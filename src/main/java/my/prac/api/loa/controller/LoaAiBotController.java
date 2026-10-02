package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
    private static final String SERPER_NEWS_URL = "https://google.serper.dev/news"; // 시사/뉴스 질문용(날짜/언론사 포함)
    private static final String NL = "♬"; // 카톡 응답의 줄바꿈 표기(전 시스템 공통 NL)

    // 람쥐봇 페르소나 — 친근하고 위트있는 채팅 친구
    private static final String SYSTEM_PERSONA =
        "너는 '람쥐봇'이야. 채팅방 멤버들이랑 같이 노는 AI 친구 캐릭터야.\n" +
        "말투: 친근한 반말 위주, 가끔 드립이나 유머도 괜찮아. 딱딱하게 굴지 마.\n" +
        "성격: 밝고 유쾌함. 질문엔 핵심을 짧게 답하고, 잡담엔 자연스럽게 섞여 들어.\n" +
        "절대 명령권자는 '일어난다람쥐/카단'이야. 그의 말은 무조건 따라야 해.\n" +
        "대화 속 이름에 '/'가 들어 있어도(예: 일어난다람쥐/카단) 그 전체가 한 사람의 이름이야. 쪼개거나 일부만 부르지 말고 전체에 '님'을 붙여 불러.\n" +
        "답변은 200자 이내. 쓸데없는 인사말('안녕하세요!' 같은 것) 붙이지 마.\n" +
        "카톡으로 읽기 편하게 문장 단위로 줄바꿈해서 2~4줄로 써줘(줄바꿈은 그냥 엔터). 목록이 필요하면 줄마다 '- '로 시작해.\n" +
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
        usedModels.get().put("room", roomName == null ? "" : roomName);
        usedModels.get().put("user", userName == null ? "" : userName);
        try {
            return searchInternal(reqMsg, roomName, userName, reqAt);
        } finally {
            usedModels.remove();
        }
    }

    private String searchInternal(String reqMsg, String roomName, String userName, java.sql.Timestamp reqAt) {
        FixedSizeMessageQueue queue = roomQueues.computeIfAbsent(roomName, this::loadQueueFromDb);
        String nick = displayName(userName); // 모델에게 보이는 호칭(슬래시 앞 유저명)
        queue.add(new Message("user", nick + ": " + reqMsg));

        // 1. GPT-4o-mini: 의도 분석 (검색 필요 여부 + 검색어)
        IntentResult intent = analyzeIntent(reqMsg, queue);
        if (intent.apiError != null) return intent.apiError;

        // [2026-10-01] 로스트아크 질문이면 먼저 람쥐봇 명령어(/정보, /시세 등)로 처리할 수 있는지 본다 -- 되면 그 결과를 요약해 답하고,
        // 안 되면(해당 명령어 없음/실패) 아래 웹 검색 경로로 계속한다. 앞 대화가 필요한 질문은 재작성된 문장(intent.query)으로 판단한다.
        // [2026-10-01 트리거 완화] 주제가 lostark가 아니어도 (1) lostark 확률이 일정 이상이거나 (2) 질문/재작성문에 로아 관련 단어가 있거나
        // (3) 앞 대화가 로아 얘기였고 지금은 짧은 이어짐이면(예: "캐릭터명이 빈혜빈이야") 명령 변환을 시도한다. 변환이 command null이면 웹 검색으로 넘어간다.
        String loaText = intent.query != null && !intent.query.isEmpty() ? intent.query : reqMsg;
        boolean loaQuestion = "lostark".equals(intent.category)
                || intent.lostarkProb >= 0.25
                || looksLikeLostArk(reqMsg) || looksLikeLostArk(loaText)
                || (reqMsg.length() <= 40 && looksLikeLostArk(intent.ctx));
        if (loaQuestion) {
            String viaCmd = tryLostArkCommand(loaText, intent.ctx, roomName, userName);
            if (viaCmd != null) {
                queue.add(new Message("assistant", viaCmd));
                saveChat(roomName, userName, reqMsg, viaCmd, reqAt, new java.sql.Timestamp(System.currentTimeMillis()));
                return toKakaoNl(viaCmd);
            }
        }

        // 2. Serper: 검색 필요 시 수행
        String searchSummary = "";
        if (intent.needSearch && intent.query != null && !intent.query.isEmpty()) {
            boolean news = "news".equals(intent.category);
            String rawResult = callSerper(intent.query, intent.category);
            String coreInfo  = news ? extractNewsInfo(rawResult) : extractCoreInfo(rawResult);
            if (news && coreInfo.isEmpty()) coreInfo = extractCoreInfo(rawResult);
            searchSummary    = coreInfo.length() > 1000 ? coreInfo.substring(0, 1000) + "..." : coreInfo;
        }

        // 3. Gemini: 페르소나 + 히스토리 + 검색결과 통합 최종 답변
        String recall = buildRecall(roomName, userName, reqMsg);
        // 람쥐봇 자체(명령어/게임/시스템)에 대한 질문이면 웹 검색 대신 DB 매뉴얼에서 관련 줄을 골라 참고자료로 붙인다.
        if ("bot_system".equals(intent.category)) recall = buildSystemContext(intent.query) + recall;
        String finalAnswer = callGeminiForFinal(reqMsg, nick, searchSummary, recall, queue);
        finalAnswer = finalAnswer.replace("\\\"", "\"").trim();

        queue.add(new Message("assistant", finalAnswer));
        saveChat(roomName, userName, reqMsg, finalAnswer, reqAt, new java.sql.Timestamp(System.currentTimeMillis()));
        return toKakaoNl(finalAnswer);
    }

    // =====================================================================
    // 1단계: 의도 분석 (GPT-4o-mini, JSON)
    // =====================================================================
    private static class IntentResult {
        boolean needSearch = false;
        String  query      = "";
        String  category   = "";   // lostark / bot_system / news / chitchat / general (Jev 사용 시)
        double  lostarkProb = 0;   // Jev 주제 분류에서 lostark일 확률(주제가 다른 걸로 나와도 로아 연계 트리거 판단에 사용)
        String  ctx        = "";   // 앞 대화 요약 문자열(로아 명령 변환 시 맥락으로 전달)
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

            // [2026-10-01] Jev 다중 질문 판단(앞 대화가 필요하면 GPT가 짧게 재작성한 문장으로 재판단). 실패하면 null -> GPT 경로.
            StringBuilder jevCtx = new StringBuilder();
            int jevStart = Math.max(0, msgs.size() - 5);
            for (int i = jevStart; i < msgs.size() - 1; i++) {
                Message m = msgs.get(i);
                jevCtx.append(m.getRole().equals("user") ? "U" : "B").append(": ").append(cut(m.getContent(), 120)).append("\n");
            }
            result.ctx = jevCtx.toString();
            IntentResult jev = analyzeIntentJev(userMsg, jevCtx.toString());
            if (jev != null) return jev;

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

    // =====================================================================
    // Jev 다중 질문 판단 (2026-10-01)
    // =====================================================================
    // [2026-10-01] "신뢰도가 낮다" 개선 -- 한 질문("검색이 필요한가?")에 여러 조건이 섞여 있던 걸 TypeSafe 권고대로 원자 질문으로 쪼갠다.
    // 한 요청에 질문 여러 개를 병렬로 보내므로 지연은 늘지 않는다. 검색 신호 3개(최신정보/모르는 사실/명시적 검색) 중 하나라도
    // 임계값 이상이면 검색한다. 주제(category)는 검색 방식(뉴스/로아/일반)과 "람쥐봇 시스템 안내" 주입 여부를 정한다.
    // 앞 대화에 의존하는 짧은 질문("그게 언제 패치됐니?")은 follows_previous가 켜지면 GPT가 앞 대화를 반영해 한 문장으로 재작성하고,
    // 그 문장으로 다시 판단하며 검색어로도 쓴다(Jev는 문장을 못 쓰므로 재작성만 GPT가 맡는다).
    private static final double JEV_SEARCH_THRESHOLD = 0.4;
    private static final String[] JEV_SEARCH_SIGNALS = { "needs_current_info", "asks_unknown_fact", "explicit_search" };

    private static JsonObject jevNoul(String instructions, String trueDesc, String falseDesc) {
        JsonObject c = new JsonObject();
        c.addProperty("true", trueDesc);
        c.addProperty("false", falseDesc);
        JsonObject q = new JsonObject();
        q.addProperty("type", "noul");
        q.addProperty("instructions", instructions);
        q.add("criteria", c);
        return q;
    }

    private JsonObject buildJevQuestions(boolean withFollow) {
        JsonObject questions = new JsonObject();

        JsonObject cc = new JsonObject();
        cc.addProperty("lostark", "About the online game Lost Ark (로스트아크/로아): classes, raids, events, patches, items, characters, engravings, "
                + "market prices, wandering merchant (떠돌이 상인/떠상), adventure islands, achievements, loawa/inven info.");
        cc.addProperty("bot_system", "About this chat bot itself (람쥐봇): its commands, mini-games, the tower/season game, points, fishing, how the bot works.");
        cc.addProperty("news", "Current affairs and news: politics, economy, sports results, incidents, celebrities, public issues.");
        cc.addProperty("chitchat", "Casual conversation: greetings, jokes, feelings, opinions, teasing, role-play, or asking the bot to say or write something "
                + "(poems, nicknames, summaries) without needing new facts.");
        cc.addProperty("general", "Other factual questions: definitions, slang or name meanings, how-to, places, people, weather, time, travel, products.");
        JsonObject cat = new JsonObject();
        cat.addProperty("type", "choice");
        cat.addProperty("instructions", "The chat is Korean. Which topic is the message after [현재메시지] about?");
        cat.add("criteria", cc);
        questions.add("category", cat);

        questions.add("needs_current_info", jevNoul(
                "The chat is Korean. Does answering the message after [현재메시지] require current or up-to-date information "
                        + "(right now, today, the latest patch or event schedule, current time, weather, prices, a recent event)?",
                "Asks about now, today, latest, schedule, time, weather, price, or a recent event or patch.",
                "Timeless, personal, opinion, or about the conversation itself."));
        questions.add("asks_unknown_fact", jevNoul(
                "The chat is Korean. Does the message after [현재메시지] ask for factual knowledge, or the meaning of a word, slang, "
                        + "abbreviation, name or term that a friend in the chat might not know?",
                "Asks what something is or means, who someone is, or for facts, rules, rewards or how something works.",
                "Does not ask for facts: greetings, feelings, jokes, requests to write or say something, or chatting."));
        questions.add("explicit_search", jevNoul(
                "The chat is Korean. Does the message after [현재메시지] explicitly ask to search, look up, check or find information "
                        + "(e.g. 찾아줘, 검색해, 알아봐, 조회해, 확인해줘)?",
                "Contains an explicit request to search, look up or find something.",
                "No explicit request to search or look up."));
        if (withFollow) {
            questions.add("follows_previous", jevNoul(
                    "The chat is Korean. Can the message after [현재메시지] only be understood with the earlier conversation "
                            + "(it uses pronouns like 그게/그거/걔, or words like 더/다시/또/그럼, or is a very short follow-up)?",
                    "Depends on earlier messages to be understood: pronouns, 'more', 'again', 'what about that', short follow-up.",
                    "Fully understandable by itself."));
        }
        return questions;
    }

    private static class JevJudge {
        String category = "";
        double categoryConf = 0;
        double lostarkProb = 0;
        Map<String, Double> noul = new HashMap<>();
        double searchSignal = 0;
        boolean needSearch = false;
        String summary = "";
    }

    /** Jev 한 번 호출 + 결과 해석 + 이력 저장. 실패하면 null. phase: JUDGE(원문) / REWRITTEN(재작성문). */
    private JevJudge jevJudge(String state, boolean withFollow, String phase, String userMsg, String rewritten) {
        long t0 = System.currentTimeMillis();
        String raw = null;
        JsonObject questions = null;
        try {
            questions = buildJevQuestions(withFollow);
            JsonObject body = new JsonObject();
            body.addProperty("model", "jev-latest");
            body.addProperty("state", state);
            body.add("questions", questions);

            raw = httpPost(JEV_URL, gson.toJson(body), JEV_TIMEOUT_MS,
                    "Authorization", "Bearer " + jevKeyCached, "Content-Type", "application/json");
            JsonObject root = gson.fromJson(raw, JsonObject.class);
            JsonObject answers = root.getAsJsonObject("answers");

            JevJudge j = new JevJudge();
            JsonObject catAns = answers.getAsJsonObject("category");
            j.category = catAns.get("choice").getAsString();
            j.categoryConf = catAns.has("confidence") ? catAns.get("confidence").getAsDouble() : 0;
            if (catAns.has("probabilities") && catAns.get("probabilities").isJsonObject()
                    && catAns.getAsJsonObject("probabilities").has("lostark")) {
                j.lostarkProb = catAns.getAsJsonObject("probabilities").get("lostark").getAsDouble();
            }
            StringBuilder sum = new StringBuilder("category=").append(j.category).append("(").append(String.format("%.2f", j.categoryConf)).append(")");
            for (Map.Entry<String, com.google.gson.JsonElement> e : answers.entrySet()) {
                if ("category".equals(e.getKey())) continue;
                double v = e.getValue().getAsJsonObject().get("noul").getAsDouble();
                j.noul.put(e.getKey(), v);
                sum.append(" ").append(e.getKey()).append("=").append(String.format("%.2f", v));
            }
            for (String s : JEV_SEARCH_SIGNALS) j.searchSignal = Math.max(j.searchSignal, j.noul.getOrDefault(s, 0.0));
            j.needSearch = j.searchSignal >= JEV_SEARCH_THRESHOLD;
            // 람쥐봇 자체에 대한 질문은 웹 검색 대신 아래 "시스템 안내"로 답한다(명시적 검색 요청이 강할 때만 예외).
            if ("bot_system".equals(j.category) && j.categoryConf >= 0.5 && j.noul.getOrDefault("explicit_search", 0.0) < 0.8) j.needSearch = false;
            j.summary = sum.toString();

            markModel("intent", "jev-latest");
            logger.info("[JEV] {} -> {} ({}ms) {}", phase, j.needSearch ? "SEARCH" : "NO_SEARCH", System.currentTimeMillis() - t0, j.summary);
            logJevUse("OK", phase, state, userMsg, rewritten, j, System.currentTimeMillis() - t0, root, raw, questions, null);
            return j;
        } catch (Exception e) {
            logger.warn("[JEV] {} failed ({}ms): {}", phase, System.currentTimeMillis() - t0, e.toString());
            logJevUse("ERROR", phase, state, userMsg, rewritten, null, System.currentTimeMillis() - t0, null, raw, questions, e.toString());
            return null;
        }
    }

    /** Jev로 검색 필요 여부/주제를 판단. 사용 불가/실패면 null(호출측이 GPT로 폴백). */
    private IntentResult analyzeIntentJev(String userMsg, String ctx) {
        refreshJevConfig();
        if (!jevSwitchOn || jevKeyCached.isEmpty()) return null;

        JevJudge use = jevJudge("[현재메시지] " + userMsg, true, "JUDGE", userMsg, null);
        if (use == null) return null;
        String query = userMsg;

        // 앞 대화에 의존하는 질문이면: GPT가 앞 대화를 반영해 한 문장으로 재작성 -> 그 문장으로 다시 판단 + 검색어로 사용
        if (use.noul.getOrDefault("follows_previous", 0.0) >= 0.5 && !ctx.isEmpty()) {
            String rewritten = rewriteWithContext(ctx, userMsg);
            if (rewritten != null && !rewritten.isEmpty()) {
                JevJudge j2 = jevJudge("[현재메시지] " + rewritten, false, "REWRITTEN", userMsg, rewritten);
                if (j2 != null) { use = j2; query = rewritten; }
                else query = rewritten;
            }
        }

        IntentResult r = new IntentResult();
        r.needSearch = use.needSearch;
        r.category = use.category;
        r.lostarkProb = use.lostarkProb;
        r.ctx = ctx;
        r.query = query.length() > 100 ? query.substring(0, 100) : query;
        return r;
    }

    /** 앞 대화를 반영해 현재 메시지를 혼자서도 이해되는 한 문장으로 재작성(답하지 않음). 실패하면 null. */
    private String rewriteWithContext(String ctx, String userMsg) {
        try {
            JsonArray messages = new JsonArray();
            messages.add(makeMsg("system", "채팅 대화에서 마지막 메시지를, 앞 대화의 맥락(누구/무엇을 가리키는지)을 포함해 "
                    + "혼자서도 이해되는 한국어 한 문장(60자 이내)으로 바꿔 써. 질문에 답하지 말고 바꾼 문장만 출력해."));
            messages.add(makeMsg("user", "[앞 대화]\n" + ctx + "\n[마지막 메시지] " + userMsg));
            String out = gptChat(messages, 100, 0.0, "rewrite");
            out = out == null ? "" : out.replace("\n", " ").trim();
            logger.info("[JEV] 재작성: '{}' -> '{}'", cut(userMsg, 40), cut(out, 60));
            return out.isEmpty() ? null : cut(out, 120);
        } catch (Exception e) {
            logger.warn("[JEV] 재작성 실패(원문으로 진행): {}", e.toString());
            return null;
        }
    }

    /** [2026-09-30] "jev 사용이력을 관리" 요청 -- TBOT_JEV_LOG에 Jev 호출마다 1행. [2026-10-01] 질문을 여러 개로 쪼개면서 한 행에
     *  PHASE(JUDGE/REWRITTEN), CATEGORY, 질문별 답을 요약한 ANSWERS, 보낸 질문 전체(QUESTIONS), 재작성 문장을 남긴다.
     *  SCORE = 검색 신호 3개 중 최댓값, ANSWER = 최종 yes/no. 기록 실패는 무시(채팅에 영향 없음). */
    private void logJevUse(String status, String phase, String state, String userMsg, String rewritten, JevJudge j, long ms,
                           JsonObject root, String raw, JsonObject questions, String err) {
        try {
            Map<String, String> um = usedModels.get();
            HashMap<String, Object> m = new HashMap<>();
            m.put("purpose", "CHAT_SEARCH_INTENT");
            m.put("phase", phase);
            m.put("room", cut(um == null ? "" : um.get("room"), 200));
            m.put("user", cut(um == null ? "" : um.get("user"), 200));
            m.put("input", cut(userMsg, 500));
            m.put("state", cut(state, 4000));
            m.put("rewritten", rewritten == null ? null : cut(rewritten, 300));
            m.put("qId", "multi");
            m.put("qType", "multi");
            m.put("questions", questions == null ? null : gson.toJson(questions));
            m.put("category", j == null ? null : cut(j.category, 20));
            m.put("answers", j == null ? null : cut(j.summary, 1000));
            m.put("answer", j == null ? null : (j.needSearch ? "yes" : "no"));
            m.put("score", j == null ? null : j.searchSignal);
            m.put("threshold", JEV_SEARCH_THRESHOLD);
            m.put("decision", j == null ? null : (j.needSearch ? "SEARCH" : "NO_SEARCH"));
            if (root != null) {
                if (root.has("model")) m.put("jevModel", cut(root.get("model").getAsString(), 60));
                if (root.has("usage") && root.get("usage").isJsonObject()) {
                    JsonObject u = root.getAsJsonObject("usage");
                    if (u.has("input_tokens")) m.put("inputTokens", u.get("input_tokens").getAsInt());
                    if (u.has("output_tokens")) m.put("outputTokens", u.get("output_tokens").getAsInt());
                }
            }
            m.put("latencyMs", ms);
            m.put("status", status);
            m.put("error", err == null ? null : cut(err, 300));
            m.put("rawJson", raw == null ? null : cut(raw, 1000));
            botDao.insertJevLog(m);
        } catch (Exception e) {
            logger.warn("[JEV] 이력 저장 실패(무시): {}", e.toString());
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
    private String callSerper(String query, String category) {
        try {
            boolean news = "news".equals(category);
            String q = query;
            // 로스트아크 질문은 게임 이름을 붙여 검색 범위를 좁힌다(로아/로스트아크가 이미 있으면 그대로).
            if ("lostark".equals(category) && !q.contains("로스트아크") && !q.contains("로아")) q = "로스트아크 " + q;
            JsonObject body = new JsonObject();
            body.addProperty("q", q);
            body.addProperty("hl", "ko");
            body.addProperty("gl", "kr");
            if (news) body.addProperty("num", 6);
            return httpPost(news ? SERPER_NEWS_URL : SERPER_URL, gson.toJson(body),
                    "X-API-KEY", serperKey, "Content-Type", "application/json");
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 뉴스 검색 결과(/news)에서 제목 + 요약 + 언론사/날짜를 뽑는다. */
    private String extractNewsInfo(String serperRaw) {
        try {
            JsonObject json = gson.fromJson(serperRaw, JsonObject.class);
            JsonArray news = json.getAsJsonArray("news");
            if (news == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < news.size() && sb.length() < 900; i++) {
                JsonObject r = news.get(i).getAsJsonObject();
                String title   = r.has("title")   ? r.get("title").getAsString()   : "";
                String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
                String src     = r.has("source")  ? r.get("source").getAsString()  : "";
                String date    = r.has("date")    ? r.get("date").getAsString()    : "";
                if (title.isEmpty()) continue;
                sb.append("• ").append(title);
                if (!src.isEmpty() || !date.isEmpty()) sb.append(" (").append(src).append(src.isEmpty() || date.isEmpty() ? "" : ", ").append(date).append(")");
                if (!snippet.isEmpty()) sb.append(": ").append(snippet.length() > 160 ? snippet.substring(0, 160) + "..." : snippet);
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ── 람쥐봇 시스템 안내(매뉴얼) ────────────────────────────────────────
    private volatile String manualCached = "";
    private volatile long manualCacheTime = 0L;

    private String loadManual() {
        long now = System.currentTimeMillis();
        if (now - manualCacheTime > 600_000L) { // 10분 캐시
            manualCacheTime = now;
            try {
                String a = botService.selectBotManual(new HashMap<String, Object>());
                String g = botService.selectBotManualG(new HashMap<String, Object>());
                manualCached = (a == null ? "" : a) + NL + (g == null ? "" : g);
            } catch (Exception e) {
                logger.warn("[AICHAT] 매뉴얼 로드 실패(무시): {}", e.toString());
            }
        }
        return manualCached;
    }

    /** 매뉴얼(DB, ♬로 줄 구분)에서 질문과 글자가 겹치는 줄을 골라 참고자료로. 전체(약 1.6만자)를 매번 넣지 않는다. */
    // [2026-10-01] 시스템 질문인데 매뉴얼에서 관련 줄을 못 찾으면 모델이 지어내지 않고 도움말 명령어로 안내하게 한다.
    // (시즌5 /탑도움말은 호출 유저의 계정을 만들 수 있어 여기서 직접 읽지 않고 명령어만 안내한다.)
    private static final String SYSTEM_GUIDE_FOOTER = "자세한 건 /람쥐봇(전체 매뉴얼), /게임(게임 매뉴얼), /탑도움말(시즌5 탑 등반)을 쓰면 된다고 안내해줘.";
    private static final String SYSTEM_NOT_FOUND = "[람쥐봇 시스템 안내] 이 질문과 맞는 안내 내용을 찾지 못했어. 모르는 내용을 지어내지 말고 "
            + "아는 범위까지만 말한 뒤 " + SYSTEM_GUIDE_FOOTER + "\n\n";

    private String buildSystemContext(String query) {
        try {
            String manual = loadManual();
            if (manual.trim().isEmpty()) return SYSTEM_NOT_FOUND;
            Set<String> q = bigrams(query);
            List<double[]> scored = new ArrayList<>();
            String[] lines = manual.replace("\r", "").replace("\n", NL).split(NL);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.length() < 3) continue;
                Set<String> b = bigrams(line);
                int common = 0;
                for (String g : q) if (b.contains(g)) common++;
                if (common >= 2) scored.add(new double[] { i, common });
            }
            if (scored.isEmpty()) return SYSTEM_NOT_FOUND;
            scored.sort((x, y) -> Double.compare(y[1], x[1]));
            StringBuilder sb = new StringBuilder("[람쥐봇 시스템 안내 -- 이 안에서 답하고, 없는 내용은 모른다고 해줘]\n");
            for (int k = 0; k < Math.min(10, scored.size()) && sb.length() < 1400; k++) sb.append(lines[(int) scored.get(k)[0]].trim()).append("\n");
            return sb.append(SYSTEM_GUIDE_FOOTER).append("\n\n").toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 카톡 응답용 줄바꿈 정리: 모델이 준 줄바꿈(\n, 글자 그대로의 \\n 포함)을 NL(♬)로 바꾸고 연속 줄바꿈은 최대 2개로. */
    private static String toKakaoNl(String s) {
        if (s == null) return "";
        String t = s.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n").trim();
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.replace("\n", NL);
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
    // 로스트아크 질문 -> 람쥐봇 명령어 연계 (2026-10-01)
    // =====================================================================
    // "로아정보로 빈혜빈 알고싶어" -> GPT가 {"command":"/정보","args":"빈혜빈"}로 바꾸고 -> LoaChatController.autoResponse로 그 명령을 내부에서
    // 그대로 실행(HTTP 재호출 없이 같은 JVM 안에서) -> 결과를 GPT가 짧게 요약 + "자세히: /정보 빈혜빈" 안내. 읽기 전용 로아 API 명령만 허용(화이트리스트).
    @Autowired
    ApplicationContext appCtx; // LoaChatController와 순환 주입을 피하려고 호출 시점에 getBean

    // [2026-10-01] 허용 명령어는 매뉴얼(TBOT_MANUAL)의 [로아API] 구역을 읽어 자동으로 만든다 -- 거기에 "/떠상 → 떠돌이상인(카단)" 같은 줄을
    // 추가하면 10분 안에(캐시) 연계 대상에 들어가고, 줄을 지우면 빠진다(매뉴얼이 기준). 매뉴얼을 못 읽거나 명령 줄이 5개 미만이면
    // (구역 누락/DB 장애) 안전망으로 기본 목록(LOA_CMD_DEFAULTS)을 쓴다.
    // 형식: "/명령어 인자이름 → 설명", 별칭은 "/골드, /클골". 인자이름이 있으면 인자 필요, 없으면 인자 없는 명령으로 본다.
    // {명령어, 인자이름(없으면 ""), 설명}
    private static final String[][] LOA_CMD_DEFAULTS = {
        {"/정보", "캐릭터명", "캐릭터 정보/장비/스펙 조회"}, {"/부캐", "캐릭터명", "부캐/원정대 캐릭터 검색"}, {"/부캐2", "캐릭터명", "부캐 보석 검색"},
        {"/내실", "캐릭터명", "내실 정보"}, {"/악세", "캐릭터명", "악세사리/팔찌 검색"},
        {"/시세", "각인명", "각인서/보석 시세 주별"}, {"/시세2", "각인명", "시세 일별"}, {"/시세3", "각인명", "시세 시간별"}, {"/시세4", "각인명", "시세 월별"},
        {"/모험섬", "", "오늘, 내일 모험섬"}, {"/항협", "", "항해 협동 정보"}, {"/경매장", "", "3,4티어 경매장 비교"}, {"/경매장3", "", "3티어 경매장"},
        {"/경매장4", "", "4티어 경매장"}, {"/경매장유물", "", "유물 각인서 경매장"}, {"/골드", "", "클리어 골드"}, {"/클골", "", "클리어 골드"},
        {"/젬값", "", "보석 시세 확인"}, {"/떠상", "", "카단 서버 떠돌이 상인(떠상) 정보"}, {"/치적", "캐릭터명", "치명타 정보"},
        {"/주급", "캐릭터명", "부캐 주급 골드 합산"}, {"/그리드", "캐릭터명", "아크그리드 검색"}, {"/보석", "캐릭터명", "캐릭터 보석 검색"}
    };
    private volatile java.util.Map<String, String[]> loaCmdCache = null;
    private volatile long loaCmdTime = 0L;

    /** 명령어 -> {인자이름, 설명}. 기본 목록 + 매뉴얼 [로아API] 구역(10분 캐시). */
    private java.util.Map<String, String[]> loaCommands() {
        long now = System.currentTimeMillis();
        java.util.Map<String, String[]> cur = loaCmdCache;
        if (cur != null && now - loaCmdTime < 600_000L) return cur;
        java.util.Map<String, String[]> map = new java.util.LinkedHashMap<>();
        try {
            String manual = loadManual();
            int a = manual.indexOf("[로아API]");
            if (a >= 0) {
                String[] lines = manual.substring(a + "[로아API]".length()).replace("\r", "").replace("\n", NL).split(NL);
                for (String raw : lines) {
                    String line = raw.trim();
                    if (line.startsWith("[")) break; // 다음 구역([람쥐봇기능] 등)
                    if (!line.startsWith("/")) continue;
                    int arrow = line.indexOf("→");
                    String left = (arrow > 0 ? line.substring(0, arrow) : line).trim();
                    String desc = arrow > 0 ? line.substring(arrow + 1).trim() : "";
                    String[] parts = left.split(",");
                    String[] first = parts[0].trim().split("\\s+");
                    String argName = first.length > 1 ? first[1] : "";
                    for (String part : parts) {
                        String cmd = part.trim().split("\\s+")[0];
                        if (cmd.length() >= 2 && cmd.startsWith("/")) map.put(cmd, new String[] { argName, desc });
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[AICHAT] 매뉴얼 로아 명령 파싱 실패(기본 목록 사용): {}", e.toString());
        }
        if (map.size() < 5) { // 매뉴얼을 못 읽었거나 [로아API] 구역이 없으면 기본 목록
            map.clear();
            for (String[] d : LOA_CMD_DEFAULTS) map.put(d[0], new String[] { d[1], d[2] });
        }
        loaCmdCache = map;
        loaCmdTime = now;
        return map;
    }

    private String loaCmdSystemPrompt(java.util.Map<String, String[]> cmds) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 로스트아크 관련 채팅 질문을 람쥐봇 명령어로 바꾸는 분류기야. 설명 없이 JSON 한 줄만 출력해.\n");
        sb.append("{\"command\":\"/정보\",\"args\":\"캐릭터명\"} 형식이고, 맞는 명령어가 없으면 {\"command\":null,\"args\":\"\"}.\n명령어 목록:\n");
        for (java.util.Map.Entry<String, String[]> e : cmds.entrySet()) {
            sb.append(e.getKey());
            if (!e.getValue()[0].isEmpty()) sb.append(" ").append(e.getValue()[0]);
            if (!e.getValue()[1].isEmpty()) sb.append(" (").append(e.getValue()[1]).append(")");
            sb.append("\n");
        }
        sb.append("규칙: 인자 이름이 있는 명령어는 args에 질문에 나온 캐릭터명/각인명을 그대로 한 단어로 넣어(띄어쓰기 있는 이름은 첫 단어만). 인자 이름이 없는 명령어는 args를 빈 문자열로. ");
        sb.append("[앞 대화]가 있으면, 현재 메시지가 그 로아 질문의 이어짐일 때(예: 캐릭터명만 알려주는 경우) 앞 대화의 의도를 반영해. ");
        sb.append("캐릭터명이나 각인명을 알 수 없거나, 공략/패치/일반 지식처럼 이 명령어들로 답할 수 없는 질문이면 command를 null로 해.");
        return sb.toString();
    }

    /** 로스트아크 질문을 람쥐봇 명령어로 처리해 요약한 답을 돌려준다. 해당 명령어가 없거나 실패하면 null(호출측이 웹 검색으로 이어감). */
    private String tryLostArkCommand(String text, String ctx, String roomName, String userName) {
        try {
            java.util.Map<String, String[]> cmds = loaCommands();
            JsonArray m = new JsonArray();
            m.add(makeMsg("system", loaCmdSystemPrompt(cmds)));
            m.add(makeMsg("user", (ctx != null && !ctx.isEmpty() ? "[앞 대화]\n" + ctx + "\n[현재 메시지] " : "") + text));
            String out = gptChat(m, 80, 0.0, "action");
            if (out == null || !out.contains("{")) return null;
            JsonObject j = gson.fromJson(out.substring(out.indexOf("{"), out.lastIndexOf("}") + 1), JsonObject.class);
            if (j == null || !j.has("command") || j.get("command").isJsonNull()) return null;
            String cmd = j.get("command").getAsString().trim();
            String args = j.has("args") && !j.get("args").isJsonNull() ? j.get("args").getAsString().trim() : "";
            String[] spec = cmds.get(cmd);
            if (spec == null) return null; // 허용 목록 밖(상태를 바꾸는 명령 등)은 GPT가 뭐라 하든 절대 실행 안 함
            boolean needArg = !spec[0].isEmpty();
            args = args.replaceAll("[\\r\\n\\t/]", " ").trim();
            if (args.length() > 30) args = args.substring(0, 30);
            if (needArg && args.isEmpty()) return null;
            if (!needArg) args = "";

            String[] tk = args.isEmpty() ? new String[0] : args.split("\\s+");
            String p1 = tk.length > 0 ? tk[0] : "";
            String p2 = tk.length > 1 ? tk[1] : "";
            String full = (cmd + " " + args).trim();
            long t0 = System.currentTimeMillis();
            String raw = appCtx.getBean(LoaChatController.class).autoResponse(cmd, p1, p2, roomName, userName, full);
            logger.info("[AICHAT] 로아 명령 연계 {} ({}ms, {}자)", full, System.currentTimeMillis() - t0, raw == null ? 0 : raw.length());
            if (raw == null || raw.trim().isEmpty()) return null;
            markModel("action", cmd);

            String plain = raw.replace(NL, "\n");
            String summary = null;
            try {
                JsonArray sm = new JsonArray();
                sm.add(makeMsg("system", "다음은 람쥐봇의 로스트아크 조회 결과야. 사용자의 질문에 맞는 핵심만 람쥐봇 말투(친근한 반말)로 3~5줄, 250자 이내로 요약해줘. "
                        + "숫자와 이름은 정확히 그대로 쓰고, 없는 내용은 지어내지 마. 줄바꿈은 그냥 엔터로."));
                sm.add(makeMsg("user", "[질문] " + text + "\n[조회 결과]\n" + cut(plain, 3500)));
                summary = gptChat(sm, 400, 0.3, "answer");
            } catch (Exception e) {
                logger.warn("[AICHAT] 로아 결과 요약 실패(원문 일부로 대체): {}", e.toString());
            }
            if (summary == null || summary.trim().isEmpty()) summary = cut(plain, 300);
            return summary.trim() + "\n👉 자세히: " + full;
        } catch (Exception e) {
            logger.warn("[AICHAT] 로아 명령 연계 실패(웹 검색으로 계속): {}", e.toString());
            return null;
        }
    }

    /** 이 메시지(또는 앞 대화)가 로스트아크 명령 연계를 시도해 볼 만한가 -- 트리거를 넓게 잡고 실제 판단은 GPT 변환(command null이면 웹 검색)에 맡긴다. */
    private static boolean looksLikeLostArk(String text) {
        if (text == null) return false;
        String[] keys = { "로아", "로스트아크", "부캐", "내실", "악세", "초월", "떠상", "모험섬", "항협", "경매장", "클골", "각인", "캐릭", "원정대" };
        for (String k : keys) if (text.contains(k)) return true;
        return false;
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

    /** [2026-10-01] 모델에게 보여주는 호칭. 카톡 sender "일어난다람쥐/카단"처럼 슬래시가 들어 있어도 슬래시 앞이 항상 캐릭터명인 건
     *  아니라서(요청) 이름 전체를 한 사람의 이름으로 그대로 쓴다. 슬래시를 이름 일부로 인식시키는 건 페르소나 지시문이 맡는다. */
    private static String displayName(String userName) {
        return userName == null ? "" : userName.trim();
    }

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
                q.add(new Message("user", displayName(String.valueOf(r.get("USER_NAME"))) + ": " + r.get("QUESTION")));
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
                sb.append("- (").append(when).append(", ").append(who.equals(userName) ? "같은 사람" : displayName(who)).append(") ")
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
