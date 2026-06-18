package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
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

import my.prac.core.dto.Message;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.FixedSizeMessageQueue;
import my.prac.core.util.GeminiUtils;
import my.prac.core.util.PropsUtil;
import my.prac.core.util.RoomContextService;

/**
 * /챗 AI 처리 흐름:
 *   1. GPT-4o-mini  — 의도 분석 (검색 필요 여부 + 검색어 추출, JSON 반환)
 *   2. Serper       — 검색 필요 시 웹 검색
 *   3. Gemini       — 페르소나 + 대화 히스토리 + 검색결과 통합해 최종 답변
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

    private final Map<String, FixedSizeMessageQueue> roomQueues = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();

    // =====================================================================
    // 진입점
    // =====================================================================
    public String search(String reqMsg, String roomName, String userName) {
        FixedSizeMessageQueue queue = roomQueues.computeIfAbsent(roomName, r -> new FixedSizeMessageQueue(20));
        queue.add(new Message("user", userName + ": " + reqMsg));

        // 1. GPT-4o-mini: 의도 분석 (검색 필요 여부 + 검색어)
        IntentResult intent = analyzeIntent(reqMsg, queue);

        // 2. Serper: 검색 필요 시 수행
        String searchSummary = "";
        if (intent.needSearch && intent.query != null && !intent.query.isEmpty()) {
            String rawResult = callSerper(intent.query);
            String coreInfo  = extractCoreInfo(rawResult);
            searchSummary    = coreInfo.length() > 1000 ? coreInfo.substring(0, 1000) + "..." : coreInfo;
        }

        // 3. Gemini: 페르소나 + 히스토리 + 검색결과 통합 최종 답변
        String finalAnswer = callGeminiForFinal(reqMsg, userName, searchSummary, queue);
        finalAnswer = finalAnswer.replace("\\\"", "\"").trim();

        queue.add(new Message("assistant", finalAnswer));
        return finalAnswer;
    }

    // =====================================================================
    // 1단계: 의도 분석 (GPT-4o-mini, JSON)
    // =====================================================================
    private static class IntentResult {
        boolean needSearch = false;
        String  query      = "";
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

            JsonArray messages = new JsonArray();
            messages.add(makeMsg("system", INTENT_SYSTEM));
            messages.add(makeMsg("user", userPrompt));

            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4o-mini");
            body.add("messages", messages);
            body.addProperty("max_tokens", 80);
            body.addProperty("temperature", 0);

            String raw = httpPost(GPT_URL, gson.toJson(body),
                    "Authorization", openaiKey, "Content-Type", "application/json");

            String content = gson.fromJson(raw, JsonObject.class)
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString().trim();

            // JSON 파싱
            if (content.contains("{")) {
                content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                JsonObject json = gson.fromJson(content, JsonObject.class);
                result.needSearch = json.has("search") && json.get("search").getAsBoolean();
                result.query      = json.has("query")  ? json.get("query").getAsString().trim() : "";
            }
        } catch (Exception e) {
            // 파싱 실패 시 키워드 폴백
            result.needSearch = fallbackNeedsSearch(userMsg);
            result.query      = userMsg;
        }
        return result;
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
    // 3단계: 최종 답변 (GPT 사용 중 — Gemini 복구 시 USE_GEMINI = true 로 변경)
    // =====================================================================
    private static final boolean USE_GEMINI = true;

    private String callGeminiForFinal(String userMsg, String userName,
                                       String searchSummary, FixedSizeMessageQueue queue) {
        // 대화 히스토리 구성 (최대 8개)
        java.util.List<Message> msgs = queue.getAll();
        int start = Math.max(0, msgs.size() - 8);

        if (USE_GEMINI) {
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
                if (history.length() > 0) prompt.append("[대화 흐름]\n").append(history).append("\n");
                prompt.append("[").append(userName).append("의 말] ").append(userMsg).append("\n\n");
                if (!searchSummary.isEmpty()) {
                    prompt.append("[웹에서 찾은 정보]\n").append(searchSummary).append("\n\n");
                    prompt.append("위 검색 결과를 자연스럽게 녹여서 람쥐봇 말투로 답해줘.");
                } else {
                    prompt.append("람쥐봇 캐릭터로 자연스럽게 답해줘. 잡담이면 같이 놀아주고, 질문이면 핵심만 짧게.");
                }
                return GeminiUtils.callGeminiApi(prompt.toString());
            } catch (Exception e) {
                return "(지금 좀 멍청해진 것 같아... 나중에 다시 물어봐!)";
            }
        }

        // ── GPT 경로 (Gemini 미사용 시) ──────────────────────────────────
        try {
            JsonArray messages = new JsonArray();
            messages.add(makeMsg("system", SYSTEM_PERSONA));
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

            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4o-mini");
            body.add("messages", messages);
            body.addProperty("max_tokens", 300);
            body.addProperty("temperature", 0.8);

            String raw = httpPost(GPT_URL, gson.toJson(body),
                    "Authorization", openaiKey, "Content-Type", "application/json");

            return gson.fromJson(raw, JsonObject.class)
                       .getAsJsonArray("choices").get(0).getAsJsonObject()
                       .getAsJsonObject("message").get("content").getAsString().trim();

        } catch (Exception e) {
            return "(지금 좀 멍청해진 것 같아... 나중에 다시 물어봐!)";
        }
    }

    // =====================================================================
    // HTTP 공통
    // =====================================================================
    private String httpPost(String urlStr, String body, String... headers) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            conn.setRequestProperty(headers[i], headers[i + 1]);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        return readStream(is);
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
