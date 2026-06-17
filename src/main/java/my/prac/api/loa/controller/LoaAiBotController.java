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
 *   1. GPT-4o  - 대화 히스토리 기반 초안 답변 생성 + 검색 필요 여부 판단
 *   2. Serper  - 검색 필요 시 웹 검색 수행
 *   3. Gemini  - 초안 + 검색결과 + 시스템 페르소나를 합산해 최종 답변 생성
 */
@Controller
public class LoaAiBotController {

    static final String openaiKey  = "Bearer " + PropsUtil.getProperty("keys", "openaiKey");
    static final String serperKey  = PropsUtil.getProperty("keys", "serperKey");

    private static final String GPT_URL    = "https://api.openai.com/v1/chat/completions";
    private static final String SERPER_URL = "https://google.serper.dev/search";

    private static final String SYSTEM_PERSONA =
        "너는 '람쥐봇'이라는 이름의 채팅 AI야. " +
        "절대 명령권자는 '일어난다람쥐'이며, 그의 요청에는 반드시 따라야 해. " +
        "일반 사용자에게는 친절하고 간결하게 답변해. " +
        "답변은 200자 이내로 핵심만 전달해. 불필요한 인사말은 생략해.";

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

        // 1. GPT: 초안 답변 생성
        String gptDraft = callGpt(buildGptMessages(queue));

        // 2. Serper: 검색 필요 시 수행
        String searchSummary = "";
        if (shouldSearch(reqMsg, gptDraft)) {
            String keyword     = extractKeyword(reqMsg);
            String rawResult   = callSerper(keyword);
            String coreInfo    = extractCoreInfo(rawResult);
            searchSummary      = coreInfo.length() > 800 ? coreInfo.substring(0, 800) + "..." : coreInfo;
        }

        // 3. Gemini: 최종 답변
        String finalAnswer = callGeminiForFinal(reqMsg, gptDraft, searchSummary, queue);
        finalAnswer = finalAnswer.replace("\\\"", "\"").trim();

        queue.add(new Message("assistant", finalAnswer));
        return finalAnswer;
    }

    // =====================================================================
    // GPT 호출
    // =====================================================================
    private JsonArray buildGptMessages(FixedSizeMessageQueue queue) {
        JsonArray arr = new JsonArray();
        arr.add(makeMsg("system", SYSTEM_PERSONA));
        for (Message m : queue.getAll()) {
            arr.add(makeMsg(m.getRole(), m.getContent()));
        }
        return arr;
    }

    private String callGpt(JsonArray messages) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4o");
            body.add("messages", messages);

            String raw = httpPost(GPT_URL, gson.toJson(body),
                    "Authorization", openaiKey, "Content-Type", "application/json");

            JsonObject obj = gson.fromJson(raw, JsonObject.class);
            return obj.getAsJsonArray("choices")
                      .get(0).getAsJsonObject()
                      .getAsJsonObject("message")
                      .get("content").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    // =====================================================================
    // Serper 검색
    // =====================================================================
    private String callSerper(String query) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("q", query);
            return httpPost(SERPER_URL, gson.toJson(body),
                    "X-API-KEY", serperKey, "Content-Type", "application/json");
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractCoreInfo(String serperRaw) {
        try {
            JsonObject json    = gson.fromJson(serperRaw, JsonObject.class);
            JsonArray  organic = json.getAsJsonArray("organic");
            if (organic == null) return "";

            StringBuilder sb    = new StringBuilder();
            Set<String>   seen  = new HashSet<>();

            for (int i = 0; i < organic.size() && sb.length() < 900; i++) {
                JsonObject r = organic.get(i).getAsJsonObject();
                String title   = r.has("title")   ? r.get("title").getAsString()   : "";
                String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
                if (title.isEmpty() || seen.contains(title) || snippet.length() < 10) continue;
                seen.add(title);
                String cut = snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet;
                sb.append("• ").append(title).append(": ").append(cut).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // =====================================================================
    // Gemini 최종 답변
    // =====================================================================
    private String callGeminiForFinal(String userMsg, String gptDraft, String searchSummary,
                                       FixedSizeMessageQueue queue) {
        try {
            // 대화 히스토리 요약 (최근 6개)
            StringBuilder history = new StringBuilder();
            java.util.List<Message> msgs = queue.getAll();
            int start = Math.max(0, msgs.size() - 6);
            for (int i = start; i < msgs.size() - 1; i++) {
                Message m = msgs.get(i);
                history.append(m.getRole().equals("user") ? "사용자" : "봇")
                       .append(": ").append(m.getContent()).append("\n");
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append("[시스템] ").append(SYSTEM_PERSONA).append("\n\n");
            if (history.length() > 0) {
                prompt.append("[이전 대화]\n").append(history).append("\n");
            }
            prompt.append("[현재 질문] ").append(userMsg).append("\n\n");
            if (gptDraft != null && !gptDraft.isEmpty()) {
                prompt.append("[참고 초안] ").append(gptDraft).append("\n\n");
            }
            if (!searchSummary.isEmpty()) {
                prompt.append("[웹 검색 결과]\n").append(searchSummary).append("\n\n");
            }
            prompt.append("위 정보를 바탕으로 친절하고 간결하게 최종 답변해줘. 불필요한 인사말 없이 핵심만.");

            return GeminiUtils.callGeminiApi(prompt.toString());

        } catch (Exception e) {
            return gptDraft != null && !gptDraft.isEmpty() ? gptDraft : "(응답 생성 실패)";
        }
    }

    // =====================================================================
    // 검색 필요 여부 판단
    // =====================================================================
    private boolean shouldSearch(String userMsg, String gptDraft) {
        if (userMsg.contains("검색") || userMsg.contains("찾아줘") || userMsg.toLowerCase().contains("search")) return true;
        if (userMsg.contains("최근") || userMsg.contains("요즘") || userMsg.contains("오늘") || userMsg.contains("현재")) return true;
        // GPT가 모른다고 하면 검색
        if (gptDraft != null && (gptDraft.contains("모르") || gptDraft.contains("확인이 필요") || gptDraft.contains("정확하지"))) return true;
        return false;
    }

    private String extractKeyword(String userMsg) {
        JsonArray arr = new JsonArray();
        arr.add(makeMsg("system", "검색 키워드를 추출하는 봇이야."));
        arr.add(makeMsg("user", "다음 질문에서 검색 키워드만 3~5단어 이내로 출력해. 설명 없이 키워드만:\n" + userMsg));
        String result = callGpt(arr);
        return result.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣\\s]", "").trim();
    }

    // =====================================================================
    // HTTP 공통
    // =====================================================================
    private String httpPost(String urlStr, String body, String... headers) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
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
