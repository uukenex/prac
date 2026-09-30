package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import my.prac.core.dto.Message;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotS4Service;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.FixedSizeMessageQueue;
import my.prac.core.util.GeminiUtils;
import my.prac.core.util.PropsUtil;
import my.prac.core.util.RoomContextService;

/**
 * /챗 AI 처리 흐름:
 *   1. Jev(TypeSafe System One) — 의도 분석 (검색 필요 여부 확률 판단, 수십 ms). 키 없음/장애/타임아웃이면
 *      GPT-4o-mini(검색 필요 여부 + 검색어 추출, JSON 반환)로 자동 폴백
 *   2. Serper       — 검색 필요 시 웹 검색
 *   3. GPT(TCONFIG GPT_MODEL, 기본 gpt-6-luna) 또는 Gemini(USE_GEMINI=1) — 페르소나 + 대화 히스토리 +
 *      검색결과 + 이 방의 최근 7일 비슷한 과거 대화를 통합해 최종 답변
 *   대화는 TBOT_AI_CHAT_HIS에 삭제 없이 보존되고, 읽을 때 최근 7일만 사용해 서버 재기동 후 방 큐를 복원하고 유사 대화를 찾는다(방 단위, 유저 구분은 유지).
 */
@Controller
public class LoaAiBotController {

    static final String openaiKey = "Bearer " + PropsUtil.getProperty("keys", "openaiKey");
    static final String serperKey = PropsUtil.getProperty("keys", "serperKey");

    private static final String GPT_URL    = "https://api.openai.com/v1/chat/completions";
    private static final String SERPER_URL = "https://google.serper.dev/search";

    // 람쥐봇 페르소나 — 친근하고 위트있는 채팅 친구
    private static final String SYSTEM_PERSONA =
        "너는 '람쥐봇'이야. 채팅방 멤버들이랑 같이 노는 AI 친구 캐릭터야.\n" +
        "말투: 친근한 반말 위주, 가끔 드립이나 유머도 괜찮아. 딱딱하게 굴지 마.\n" +
        "성격: 밝고 유쾌함. 질문엔 핵심을 짧게 답하고, 잡담엔 자연스럽게 섞여 들어.\n" +
        "절대 명령권자는 '일어난다람쥐'야. 그의 말은 무조건 따라야 해.\n" +
        "답변은 200자 이내. 쓸데없는 인사말('안녕하세요!' 같은 것) 붙이지 마.\n" +
        "웹 검색 결과가 있으면 '찾아봤는데~' 같은 말투로 자연스럽게 녹여서 얘기해줘.";

    // 의도 분석 프롬프트 — GPT-4o-mini 에 보낼 시스템 지시
    private static final String INTENT_SYSTEM =
        "너는 채팅 메시지가 웹 검색이 필요한지 판단하는 분류기야.\n" +
        "반드시 아래 JSON 형식만 출력해. 설명 없이 JSON만.\n" +
        "{\"search\": true/false, \"query\": \"검색어 또는 빈 문자열\"}\n\n" +
        "search=true 조건 (하나라도 해당하면 true):\n" +
        "- 사실 정보 질문 (날씨, 뉴스, 시세, 사람, 장소, 사건 등)\n" +
        "- '찾아줘', '알려줘', '뭐야', '어디야', '언제야', '얼마야', '검색', '정보' 등 포함\n" +
        "- 최신 정보가 필요한 것 (오늘, 요즘, 최근, 현재, 지금 등)\n" +
        "- 모르는 것 물어볼 때\n\n" +
        "search=false 조건:\n" +
        "- 단순 잡담, 인사, 감탄, 게임 얘기, 개인적인 이야기\n" +
        "- 의견 묻기 ('어떻게 생각해?', '넌 어때?' 등)\n" +
        "- 이미 대화 중인 주제 이어가기\n\n" +
        "query는 search=true일 때만 채워. 실제 검색할 핵심 키워드로.";

    @Autowired
    RoomContextService roomService;

    @Resource(name = "core.prjbot.BotService")
    BotService botService;

    @Resource(name = "core.prjbot.BotS4Service")
    BotS4Service botS4Service;

    @Resource(name = "core.prjbot.BotDAO")
    BotDAO botDao;

    private static final Logger logger = LoggerFactory.getLogger(LoaAiBotController.class);

    // ── Jev (TypeSafe AI, System One 모델) ─────────────────────────────────
    // [2026-09-30] "/챗에서 빠른 의미판단" 요청. 글을 쓰는 LLM이 아니라 확률(0~1)만 돌려주는 분류형 모델이라 "검색이
    // 필요한 질문인가"(Noul) 판단만 맡기고, 검색어는 사용자 메시지 그대로 쓴다(Jev는 문장을 생성하지 못함).
    // 키는 safety/keys.properties 의 jevKey(없으면 Jev 미사용 = 기존 GPT 경로). TCONFIG USE_JEV=0 이면 즉시 끔.
    private static final String JEV_URL = "https://api.typesafe.ai/v1/systemone";
    private static final double JEV_SEARCH_THRESHOLD = 0.5;
    private static final int JEV_TIMEOUT_MS = 3000; // 빠른 판단이 목적이라 느리면 바로 GPT로 폴백
    private volatile String jevKeyCached = "";
    private volatile boolean jevSwitchOn = true;
    private volatile long jevCacheTime = 0L;
    private static final long JEV_CACHE_TTL = 60_000L;

    private void refreshJevConfig() {
        long now = System.currentTimeMillis();
        if (now - jevCacheTime <= JEV_CACHE_TTL) return;
        jevCacheTime = now;
        String k = PropsUtil.getProperty("keys", "jevKey"); // 파일 재조회는 60초에 한 번만
        jevKeyCached = k == null ? "" : k.trim();
        try {
            jevSwitchOn = !"0".equals(botS4Service.selectTconfigVal("USE_JEV"));
        } catch (Exception e) {
            // DB 오류 시 이전 값 유지
        }
    }

    /** [2026-09-30] 이번 /챗 요청에서 실제로 쓴 모델 기록(DB 저장용). 요청 하나가 한 스레드에서 끝까지 처리되므로
     *  ThreadLocal로 충분하다. 키: "intent"(의도판단 모델) / "answer"(최종 답변 모델). 폴백이 일어나면 실제 쓴 모델이 남는다. */
    private final ThreadLocal<Map<String, String>> usedModels = new ThreadLocal<>();

    private void markModel(String kind, String model) {
        Map<String, String> m = usedModels.get();
        if (m != null) m.put(kind, model);
    }

    private final Map<String, FixedSizeMessageQueue> roomQueues = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();

    // USE_GEMINI: TCONFIG DB에서 60초 캐싱
    private volatile boolean geminiCached = false;
    private volatile long geminiCacheTime = 0L;
    private static final long GEMINI_CACHE_TTL = 60_000L;

    private boolean isUseGemini() {
        long now = System.currentTimeMillis();
        if (now - geminiCacheTime > GEMINI_CACHE_TTL) {
            try {
                String val = botS4Service.selectTconfigVal("USE_GEMINI");
                geminiCached = "1".equals(val);
            } catch (Exception e) {
                // DB 오류 시 이전 캐시 유지
            }
            geminiCacheTime = now;
        }
        return geminiCached;
    }

    // =====================================================================
    // 진입점
    // =====================================================================
    public String search(String reqMsg, String roomName, String userName) {
        java.sql.Timestamp reqAt = new java.sql.Timestamp(System.currentTimeMillis()); // 입력 받은 시각(DB 기록용)
        usedModels.set(new HashMap<>());
        try {
            return searchInternal(reqMsg, roomName, userName, reqAt);
        } finally {
            usedModels.remove();
        }
    }

    private String searchInternal(String reqMsg, String roomName, String userName, java.sql.Timestamp reqAt) {
        FixedSizeMessageQueue queue = roomQueues.computeIfAbsent(roomName, this::loadQueueFromDb);
        queue.add(new Message("user", userName + ": " + reqMsg));

        // 1. GPT-4o-mini: 의도 분석 (검색 필요 여부 + 검색어)
        IntentResult intent = analyzeIntent(reqMsg, queue);
        if (intent.apiError != null) return intent.apiError;

        // 2. Serper: 검색 필요 시 수행
        String searchSummary = "";
        if (intent.needSearch && intent.query != null && !intent.query.isEmpty()) {
            String rawResult = callSerper(intent.query);
            String coreInfo  = extractCoreInfo(rawResult);
            searchSummary    = coreInfo.length() > 1000 ? coreInfo.substring(0, 1000) + "..." : coreInfo;
        }

        // 3. Gemini: 페르소나 + 히스토리 + 검색결과 통합 최종 답변
        String recall = buildRecall(roomName, userName, reqMsg);
        String finalAnswer = callGeminiForFinal(reqMsg, userName, searchSummary, recall, queue);
        finalAnswer = finalAnswer.replace("\\\"", "\"").trim();

        queue.add(new Message("assistant", finalAnswer));
        saveChat(roomName, userName, reqMsg, finalAnswer, reqAt, new java.sql.Timestamp(System.currentTimeMillis()));
        return finalAnswer;
    }

    // =====================================================================
    // 1단계: 의도 분석 (GPT-4o-mini, JSON)
    // =====================================================================
    private static class IntentResult {
        boolean needSearch = false;
        String  query      = "";
        String  apiError   = null;
    }

    private IntentResult analyzeIntent(String userMsg, FixedSizeMessageQueue queue) {
        IntentResult result = new IntentResult();
        try {
            // 최근 대화 2개만 맥락으로 전달 (비용 절약)
            StringBuilder ctx = new StringBuilder();
            java.util.List<Message> msgs = queue.getAll();
            int start = Math.max(0, msgs.size() - 3);
            for (int i = start; i < msgs.size() - 1; i++) {
                Message m = msgs.get(i);
                ctx.append(m.getRole().equals("user") ? "U" : "B").append(": ").append(m.getContent()).append("\n");
            }

            String userPrompt = (ctx.length() > 0 ? "[최근대화]\n" + ctx + "\n" : "") + "[현재메시지] " + userMsg;

            IntentResult jev = analyzeIntentJev(userPrompt, userMsg);
            if (jev != null) return jev; // null = Jev 미사용/실패 -> 아래 GPT 경로

            JsonArray messages = new JsonArray();
            messages.add(makeMsg("system", INTENT_SYSTEM));
            messages.add(makeMsg("user", userPrompt));

            String content = gptChat(messages, 80, 0.0, "intent");

            // JSON 파싱
            if (content.contains("{")) {
                content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                JsonObject json = gson.fromJson(content, JsonObject.class);
                result.needSearch = json.has("search") && json.get("search").getAsBoolean();
                result.query      = json.has("query")  ? json.get("query").getAsString().trim() : "";
            }
        } catch (Exception e) {
            // HTTP 에러 시 result에 에러 메시지 보관 (3단계에서 그대로 반환)
            String apiErr = parseApiErrMsg(e);
            if (apiErr != null) { result.needSearch = false; result.query = apiErr; result.apiError = apiErr; return result; }
            // 기타 파싱 실패 시 키워드 폴백
            markModel("intent", "keyword");
            result.needSearch = fallbackNeedsSearch(userMsg);
            result.query      = userMsg;
        }
        return result;
    }

    /** Jev로 "웹 검색이 필요한 질문인가"를 판단. 사용 불가/실패/모호하지 않은 오류면 null(호출측이 GPT로 폴백). */
    private IntentResult analyzeIntentJev(String state, String userMsg) {
        refreshJevConfig();
        if (!jevSwitchOn || jevKeyCached.isEmpty()) return null;
        long t0 = System.currentTimeMillis();
        try {
            JsonObject criteria = new JsonObject();
            criteria.addProperty("true", "Needs factual or up-to-date information from the web: news, weather, prices, "
                    + "people, places, events, definitions, 'what/where/when/how much is X', or explicit requests to look something up.");
            criteria.addProperty("false", "Casual chat, greetings, reactions, jokes, opinions, personal talk, game talk, "
                    + "or continuing the current topic without needing new facts.");
            JsonObject q = new JsonObject();
            q.addProperty("type", "noul");
            q.addProperty("instructions", "The chat below is Korean. Judge only the message after [현재메시지] "
                    + "(the earlier [최근대화] is context). Does replying well require searching the web for facts?");
            q.add("criteria", criteria);
            JsonObject questions = new JsonObject();
            questions.add("search", q);
            JsonObject body = new JsonObject();
            body.addProperty("model", "jev-latest");
            body.addProperty("state", state);
            body.add("questions", questions);

            String raw = httpPost(JEV_URL, gson.toJson(body), JEV_TIMEOUT_MS,
                    "Authorization", "Bearer " + jevKeyCached, "Content-Type", "application/json");
            double p = gson.fromJson(raw, JsonObject.class)
                    .getAsJsonObject("answers").getAsJsonObject("search").get("noul").getAsDouble();

            markModel("intent", "jev-latest");
            IntentResult r = new IntentResult();
            r.needSearch = p >= JEV_SEARCH_THRESHOLD;
            r.query = userMsg.length() > 100 ? userMsg.substring(0, 100) : userMsg;
            logger.info("[JEV] search p={} -> {} ({}ms) msg={}", String.format("%.3f", p), r.needSearch,
                    System.currentTimeMillis() - t0, userMsg.length() > 40 ? userMsg.substring(0, 40) : userMsg);
            return r;
        } catch (Exception e) {
            logger.warn("[JEV] failed -> GPT fallback ({}ms): {}", System.currentTimeMillis() - t0, e.toString());
            return null;
        }
    }

    // 의도 분석 실패 시 단순 키워드 폴백
    private boolean fallbackNeedsSearch(String msg) {
        String[] triggers = {"찾아", "검색", "알려줘", "뭐야", "어디야", "언제야", "얼마야",
                             "최근", "요즘", "오늘", "현재", "지금", "뉴스", "정보", "몇시", "날씨"};
        for (String t : triggers) if (msg.contains(t)) return true;
        return false;
    }

    // =====================================================================
    // 2단계: Serper 검색
    // =====================================================================
    private String callSerper(String query) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("q", query);
            body.addProperty("hl", "ko");
            body.addProperty("gl", "kr");
            return httpPost(SERPER_URL, gson.toJson(body),
                    "X-API-KEY", serperKey, "Content-Type", "application/json");
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractCoreInfo(String serperRaw) {
        try {
            JsonObject json = gson.fromJson(serperRaw, JsonObject.class);
            StringBuilder sb = new StringBuilder();
            Set<String> seen = new HashSet<>();

            // answerBox (인스턴트 답변) 있으면 최우선
            if (json.has("answerBox")) {
                JsonObject ab = json.getAsJsonObject("answerBox");
                String abAnswer = ab.has("answer")  ? ab.get("answer").getAsString()  :
                                  ab.has("snippet") ? ab.get("snippet").getAsString() : "";
                if (!abAnswer.isEmpty()) sb.append("핵심: ").append(abAnswer).append("\n");
            }

            // organic 결과
            JsonArray organic = json.getAsJsonArray("organic");
            if (organic != null) {
                for (int i = 0; i < organic.size() && sb.length() < 900; i++) {
                    JsonObject r   = organic.get(i).getAsJsonObject();
                    String title   = r.has("title")   ? r.get("title").getAsString()   : "";
                    String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
                    if (title.isEmpty() || seen.contains(title) || snippet.length() < 10) continue;
                    seen.add(title);
                    String cut = snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet;
                    sb.append("• ").append(title).append(": ").append(cut).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // =====================================================================
    // 3단계: 최종 답변 (USE_GEMINI는 TCONFIG DB에서 실시간 조회, 60초 캐시)
    // =====================================================================
    private String callGeminiForFinal(String userMsg, String userName,
                                       String searchSummary, String recall, FixedSizeMessageQueue queue) {
        // 대화 히스토리 구성 (최대 8개)
        java.util.List<Message> msgs = queue.getAll();
        int start = Math.max(0, msgs.size() - 8);

        if (isUseGemini()) {
            // ── Gemini 경로 ──────────────────────────────────────────────
            try {
                StringBuilder history = new StringBuilder();
                for (int i = start; i < msgs.size() - 1; i++) {
                    Message m = msgs.get(i);
                    history.append(m.getRole().equals("user") ? "유저" : "람쥐봇")
                           .append(": ").append(m.getContent()).append("\n");
                }
                StringBuilder prompt = new StringBuilder();
                prompt.append("[캐릭터 설정]\n").append(SYSTEM_PERSONA).append("\n\n");
                if (!recall.isEmpty()) prompt.append(recall).append("\n");
                if (history.length() > 0) prompt.append("[대화 흐름]\n").append(history).append("\n");
                prompt.append("[").append(userName).append("의 말] ").append(userMsg).append("\n\n");
                if (!searchSummary.isEmpty()) {
                    prompt.append("[웹에서 찾은 정보]\n").append(searchSummary).append("\n\n");
                    prompt.append("위 검색 결과를 자연스럽게 녹여서 람쥐봇 말투로 답해줘.");
                } else {
                    prompt.append("람쥐봇 캐릭터로 자연스럽게 답해줘. 잡담이면 같이 놀아주고, 질문이면 핵심만 짧게.");
                }
                String gem = GeminiUtils.callGeminiApi(prompt.toString());
                markModel("answer", "gemini");
                return gem;
            } catch (Exception e) {
                String apiErr = parseApiErrMsg(e);
                return apiErr != null ? apiErr : "(Gemini 오류 발생... 나중에 다시 물어봐!)";
            }
        }

        // ── GPT 경로 (Gemini 미사용 시) ──────────────────────────────────
        try {
            JsonArray messages = new JsonArray();
            messages.add(makeMsg("system", SYSTEM_PERSONA));
            if (!recall.isEmpty()) messages.add(makeMsg("system", recall));
            // 히스토리
            for (int i = start; i < msgs.size() - 1; i++) {
                Message m = msgs.get(i);
                messages.add(makeMsg(m.getRole(), m.getContent()));
            }
            // 검색 결과가 있으면 user 메시지에 추가 컨텍스트로 주입
            String userContent = userMsg;
            if (!searchSummary.isEmpty()) {
                userContent = userMsg + "\n\n[참고 검색결과]\n" + searchSummary
                        + "\n\n위 검색 결과를 자연스럽게 녹여서 람쥐봇 말투로 답해줘.";
            }
            messages.add(makeMsg("user", userName + ": " + userContent));

            return gptChat(messages, 300, 0.8, "answer");

        } catch (Exception e) {
            String apiErr = parseApiErrMsg(e);
            return apiErr != null ? apiErr : "(지금 좀 멍청해진 것 같아... 나중에 다시 물어봐!)";
        }
    }

    // =====================================================================
    // GPT 호출 공통 (모델은 TCONFIG GPT_MODEL, 기본 gpt-6-luna)
    // =====================================================================
    // [2026-09-30] 최신 소형 모델로 교체(gpt-6-luna: 추론 없음 기준 첫 글자 0.78초/초당 139토큰, 입력 $0.10/출력 $0.50 per 1M --
    // 속도는 gpt-4o-mini와 비슷하고 더 싸다. 비교 근거는 Artificial Analysis). gpt-5.x 계열은 max_tokens 대신 max_completion_tokens를 쓰고 temperature를
    // 지원하지 않으며, reasoning_effort="none"이어야 추론 없이 gpt-4o-mini처럼 바로 답한다. 새 모델이 거절(400/404)
    // 하면 GPT_FALLBACK_MODEL(gpt-4o-mini)로 한 번 재시도해서 채팅이 끊기지 않게 한다. 모델 변경(예: gpt-5.4-nano로
    // 더 빠르게)은 배포 없이 TCONFIG GPT_MODEL 값만 바꾸면 60초 안에 반영된다.
    private static final String GPT_DEFAULT_MODEL  = "gpt-6-luna";
    private static final String GPT_FALLBACK_MODEL = "gpt-4o-mini";
    private volatile String gptModelCached = GPT_DEFAULT_MODEL;
    private volatile long gptModelTime = 0L;

    private String gptModel() {
        long now = System.currentTimeMillis();
        if (now - gptModelTime > 60_000L) {
            gptModelTime = now;
            try {
                String v = botS4Service.selectTconfigVal("GPT_MODEL");
                gptModelCached = (v == null || v.trim().isEmpty()) ? GPT_DEFAULT_MODEL : v.trim();
            } catch (Exception e) {
                // DB 오류 시 이전 값 유지
            }
        }
        return gptModelCached;
    }

    /** kind: "intent"(의도판단) / "answer"(최종 답변) -- DB 기록용으로 실제 성공한 모델명을 남긴다. */
    private String gptChat(JsonArray messages, int maxTokens, double temperature, String kind) throws Exception {
        String model = gptModel();
        try {
            String out = gptChatOnce(model, messages, maxTokens, temperature);
            markModel(kind, model);
            return out;
        } catch (HttpApiException e) {
            if ((e.code == 400 || e.code == 404) && !GPT_FALLBACK_MODEL.equals(model)) {
                logger.warn("[GPT] {} 거절({}) -> {} 로 재시도: {}", model, e.code, GPT_FALLBACK_MODEL, e.getMessage());
                String out = gptChatOnce(GPT_FALLBACK_MODEL, messages, maxTokens, temperature);
                markModel(kind, GPT_FALLBACK_MODEL);
                return out;
            }
            throw e;
        }
    }

    private String gptChatOnce(String model, JsonArray messages, int maxTokens, double temperature) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        if (model.matches("^gpt-[5-9].*")) { // gpt-5.x / gpt-6.x 계열(추론 모델 파라미터 체계)
            body.addProperty("max_completion_tokens", maxTokens);
            body.addProperty("reasoning_effort", "none");
        } else {
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("temperature", temperature);
        }
        long t0 = System.currentTimeMillis();
        String raw = httpPost(GPT_URL, gson.toJson(body), "Authorization", openaiKey, "Content-Type", "application/json");
        logger.info("[GPT] {} {}ms", model, System.currentTimeMillis() - t0);
        return gson.fromJson(raw, JsonObject.class)
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString().trim();
    }

    // =====================================================================
    // /챗 대화 영구 저장 + 재기동 복원 + 유사 과거 대화 (TBOT_AI_CHAT_HIS, 방 단위)
    // =====================================================================
    private static final int CHAT_KEEP_DAYS = 7;      // 읽는 범위(일). 데이터 자체는 지우지 않고 보존
    private static final int CHAT_QUEUE_ROWS = 10;    // 큐(20개 메시지) 복원용 = 최근 10회 왕복
    private static final int CHAT_RECALL_SCAN = 300;  // 유사도 검색 대상(최근 7일 중 최대 300회)
    private static final int CHAT_PROMPT_TURNS = 4;   // 프롬프트에 이미 들어가는 최근 왕복 수(8메시지) -- 중복 회상 제외
    private static final double CHAT_RECALL_MIN_SCORE = 0.35;

    private static String cut(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 서버 재기동 후 첫 /챗에서 그 방의 최근 대화(7일 이내)로 메모리 큐를 복원한다. */
    private FixedSizeMessageQueue loadQueueFromDb(String roomName) {
        FixedSizeMessageQueue q = new FixedSizeMessageQueue(20);
        try {
            HashMap<String, Object> p = new HashMap<>();
            p.put("room", roomName);
            p.put("days", CHAT_KEEP_DAYS);
            p.put("maxRows", CHAT_QUEUE_ROWS);
            List<HashMap<String, Object>> rows = botDao.selectAiChatRecent(p); // 최신순
            for (int i = rows.size() - 1; i >= 0; i--) {
                HashMap<String, Object> r = rows.get(i);
                q.add(new Message("user", r.get("USER_NAME") + ": " + r.get("QUESTION")));
                q.add(new Message("assistant", String.valueOf(r.get("ANSWER"))));
            }
        } catch (Exception e) {
            logger.warn("[AICHAT] 큐 복원 실패(무시): {}", e.toString());
        }
        return q;
    }

    private void saveChat(String roomName, String userName, String question, String answer,
                          java.sql.Timestamp reqAt, java.sql.Timestamp resAt) {
        try {
            // 오류 안내문("(...)")은 대화가 아니므로 저장하지 않는다.
            if (answer == null || answer.isEmpty() || (answer.startsWith("(") && answer.endsWith(")"))) return;
            HashMap<String, Object> p = new HashMap<>();
            p.put("room", cut(roomName, 200));
            p.put("user", cut(userName, 200));
            p.put("question", cut(question, 500));
            p.put("answer", cut(answer, 700));
            p.put("reqDate", reqAt);   // 입력 받은 시각
            p.put("resDate", resAt);   // 답변을 만든(출력한) 시각
            Map<String, String> um = usedModels.get();
            p.put("model", um == null ? "" : cut(um.getOrDefault("answer", ""), 60));
            p.put("intentModel", um == null ? "" : cut(um.getOrDefault("intent", ""), 60));
            botDao.insertAiChatHis(p); // 삭제하지 않고 영구 보존, 읽을 때만 최근 CHAT_KEEP_DAYS일로 제한
        } catch (Exception e) {
            logger.warn("[AICHAT] 저장 실패(무시): {}", e.toString());
        }
    }

    /** 한글/영문/숫자만 남긴 문자열의 문자 bigram 집합(공백/기호 무시). */
    private static Set<String> bigrams(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 1 < sb.length(); i++) out.add(sb.substring(i, i + 2));
        return out;
    }

    /** 이 방의 최근 7일 대화 중 지금 질문과 비슷한 것(최대 2개)을 프롬프트용 텍스트로. 방이 달라지면 절대 안 섞이고,
     *  같은 방 안에서는 다른 유저의 대화도 참조한다. 없으면 빈 문자열. */
    private String buildRecall(String roomName, String userName, String msg) {
        try {
            Set<String> cur = bigrams(msg);
            if (cur.size() < 2) return "";
            HashMap<String, Object> p = new HashMap<>();
            p.put("room", roomName);
            p.put("days", CHAT_KEEP_DAYS);
            p.put("maxRows", CHAT_RECALL_SCAN);
            List<HashMap<String, Object>> rows = botDao.selectAiChatRecent(p); // 최신순
            List<double[]> scored = new ArrayList<>(); // {index, score}
            for (int i = CHAT_PROMPT_TURNS; i < rows.size(); i++) { // 최근 몇 회는 이미 [대화 흐름]에 있으니 제외
                Set<String> past = bigrams(String.valueOf(rows.get(i).get("QUESTION")));
                if (past.size() < 2) continue;
                int common = 0;
                for (String g : cur) if (past.contains(g)) common++;
                double dice = 2.0 * common / (cur.size() + past.size());
                if (common >= 2 && dice >= CHAT_RECALL_MIN_SCORE) scored.add(new double[] { i, dice });
            }
            if (scored.isEmpty()) return "";
            scored.sort((a, b) -> a[1] != b[1] ? Double.compare(b[1], a[1]) : Double.compare(a[0], b[0])); // 점수 높은 순, 같으면 최신
            StringBuilder sb = new StringBuilder("[이 방에서 예전에 나눈 비슷한 대화 -- 참고만 하고, 이어지는 얘기면 자연스럽게 연결해서 답해줘]\n");
            long now = System.currentTimeMillis();
            for (int k = 0; k < Math.min(2, scored.size()); k++) {
                HashMap<String, Object> r = rows.get((int) scored.get(k)[0]);
                long ageH = 0;
                Object ts = r.get("REG_DATE");
                if (ts instanceof java.util.Date) ageH = (now - ((java.util.Date) ts).getTime()) / 3_600_000L;
                String when = ageH >= 24 ? (ageH / 24) + "일 전" : Math.max(ageH, 1) + "시간 전";
                String who = String.valueOf(r.get("USER_NAME"));
                sb.append("- (").append(when).append(", ").append(who.equals(userName) ? "같은 사람" : who).append(") ")
                  .append("질문: ").append(cut(String.valueOf(r.get("QUESTION")), 120))
                  .append(" / 람쥐봇 답: ").append(cut(String.valueOf(r.get("ANSWER")), 160)).append("\n");
            }
            logger.info("[AICHAT] 유사 과거 대화 {}건 참조 (room={})", Math.min(2, scored.size()), roomName);
            return sb.toString();
        } catch (Exception e) {
            logger.warn("[AICHAT] 유사 대화 검색 실패(무시): {}", e.toString());
            return "";
        }
    }

    // =====================================================================
    // HTTP 공통
    // =====================================================================
    static class HttpApiException extends Exception {
        final int code;
        HttpApiException(int code, String body) { super(code + ":" + body); this.code = code; }
    }

    private String httpPost(String urlStr, String body, String... headers) throws Exception {
        return httpPost(urlStr, body, 0, headers);
    }

    /** timeoutMs > 0 이면 연결/읽기 타임아웃을 그 값으로(Jev처럼 빨라야 의미 있는 호출용), 0이면 기본(8초/15초). */
    private String httpPost(String urlStr, String body, int timeoutMs, String... headers) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeoutMs > 0 ? timeoutMs : 8000);
        conn.setReadTimeout(timeoutMs > 0 ? timeoutMs : 15000);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            conn.setRequestProperty(headers[i], headers[i + 1]);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        int code = conn.getResponseCode();
        if (code >= 400) {
            String errBody = readStream(conn.getErrorStream());
            throw new HttpApiException(code, errBody);
        }
        return readStream(conn.getInputStream());
    }

    private String parseApiErrMsg(Exception e) {
        if (!(e instanceof HttpApiException)) return null;
        HttpApiException he = (HttpApiException) e;
        String msg = "";
        try {
            JsonObject j = gson.fromJson(he.getMessage().substring(he.getMessage().indexOf(":") + 1), JsonObject.class);
            if (j.has("error")) msg = j.getAsJsonObject("error").get("message").getAsString();
        } catch (Exception ignore) {}
        if (he.code == 429 || msg.toLowerCase().contains("quota") || msg.toLowerCase().contains("rate limit")
                || msg.toLowerCase().contains("insufficient_quota")) {
            return "(OpenAI API 사용량 한도 초과야. 잠시 후 다시 시도해줘!)";
        }
        if (he.code == 401) return "(OpenAI API 키가 유효하지 않아. 설정 확인 필요!)";
        if (!msg.isEmpty()) return "(API 오류: " + msg + ")";
        return "(API 오류 " + he.code + ")";
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private JsonObject makeMsg(String role, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.addProperty("content", content);
        return obj;
    }
}
