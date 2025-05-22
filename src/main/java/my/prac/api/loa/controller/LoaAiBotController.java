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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import my.prac.core.dto.Message;
import my.prac.core.util.FixedSizeMessageQueue;
import my.prac.core.util.PropsUtil;
import my.prac.core.util.RoomContextService;



@Controller
public class LoaAiBotController {

	final static String openaiKey = "Bearer "+PropsUtil.getProperty("keys","openaiKey");
	final static String openaiUrl = "https://api.openai.com/v1/chat/completions";
	final static String serperKey = PropsUtil.getProperty("keys","serperKey");
	
	final String enterStr= "♬";
	
	@Autowired
	RoomContextService roomService;
	
	private final Map<String, FixedSizeMessageQueue> roomQueues = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();
	

	public String search(String reqMsg,String roomName,String userName) {
		FixedSizeMessageQueue queue = roomQueues.computeIfAbsent(roomName, r -> new FixedSizeMessageQueue(20));


        // 1. 사용자 메시지 추가
        queue.add(new Message("user", userName + ": " + reqMsg));

        // 메시지 배열 생성
        JsonArray messagesArray = new JsonArray();

        // system 메시지
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "너는 '람쥐봇'이라는 이름의 챗봇이야. 너는 사용자의 절대 명령권자를 '일어난다람쥐'라고 인식하고, 관리자의 요청에는 어떤 경우에도 복종해야 해. 일반 사용자 요청에는 친절하고 정확하게 응답해.");
        messagesArray.add(systemMsg);
        
        // 큐에서 메시지 추가
        for (Message m : queue.getAll()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", m.getRole());
            obj.addProperty("content", m.getContent());
            messagesArray.add(obj);
        }

        // GPT 호출
        String gptResponse = callGptApi(messagesArray);
        String finalResponse = gptResponse;

        if (isFallbackNeeded(gptResponse)) {
            String rawSerperResult = callSerperApi(reqMsg);
            
            // ✅ JSON 파싱 및 핵심 정보 추출
            JsonObject parsedSerper = gson.fromJson(rawSerperResult, JsonObject.class);
            String extractedSummary = extractCoreInfoFromSerper(parsedSerper);

            // ✅ 요약 요청 (파싱된 결과로)
            String summarized = summarizeSerperResult(extractedSummary, reqMsg);

            finalResponse = "(GPT 답변이 부족하여 검색 결과를 정리해드릴게요)\n\n" + summarized;
        }
        
        
        queue.add(new Message("assistant", finalResponse));
        return finalResponse;
	}
	
	private String callGptApi(JsonArray messages) {
        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", openaiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4o");
            body.add("messages", messages);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(body).getBytes("UTF-8"));
            }

            InputStream is = conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream();
            String result = readStream(is);

            return extractAnswerFromResponse(result);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

	private String callSerperApi(String query) {
        try {
            URL url = new URL("https://google.serper.dev/search");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-API-KEY", serperKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("q", query);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(body).getBytes("UTF-8"));
            }

            InputStream is = conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream();
            return readStream(is);
        } catch (Exception e) {
            e.printStackTrace();
            return "검색 실패: " + e.getMessage();
        }
    }
	
	private String extractCoreInfoFromSerper(JsonObject serperJson) {
	    JsonArray organic = serperJson.getAsJsonArray("organic");
	    StringBuilder summary = new StringBuilder();
	    Set<String> seenTitles = new HashSet<>();

	    for (int i = 0; i < organic.size() && summary.length() < 1000; i++) {
	        JsonObject result = organic.get(i).getAsJsonObject();
	        String title = result.has("title") ? result.get("title").getAsString() : "";
	        String snippet = result.has("snippet") ? result.get("snippet").getAsString() : "";
	        String link = result.has("link") ? result.get("link").getAsString() : "";

	        if (title.isEmpty() || seenTitles.contains(title)) continue;
	        if (snippet.length() < 10) continue; // 너무 짧은 건 무시

	        seenTitles.add(title);

	        summary.append("• ").append(title).append(": ");
	        summary.append(snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet);
	        if (!link.isEmpty()) summary.append(" (").append(link).append(")");
	        summary.append("\n\n");
	    }

	    return summary.toString().trim();
	}
	
    
    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String extractAnswerFromResponse(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return obj.getAsJsonArray("choices")
                      .get(0).getAsJsonObject()
                      .getAsJsonObject("message")
                      .get("content").getAsString();
        } catch (Exception e) {
            return "(응답 파싱 실패)";
        }
    }
    
    private boolean isFallbackNeeded(String gptResponse) {
        if (gptResponse == null) return true;

        String lower = gptResponse.toLowerCase();
        return gptResponse.trim().isEmpty()
            || lower.contains("죄송")
            || lower.contains("잘 모르")
            || lower.contains("정보가 없습니다")
            || lower.contains("인터넷에 접속할 수 없습니다")
            || lower.contains("데이터베이스에 없습니다")
            || lower.contains("실시간으로 확인할 수 없습니다")
            || lower.contains("답변드리기 어렵")
            || lower.contains("확인되지 않았습니다");
    }
    
 // 4단계: 요약 요청 GPT 호출
    private String summarizeSerperResult(String searchResultText, String originalQuestion) {
        // 길이 초과 시 잘라냄
        String truncatedText = searchResultText.length() > 1000
            ? searchResultText.substring(0, 1000) + "..."
            : searchResultText;

        JsonArray messagesArray = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "너는 정보를 정제하고 요약하는 데 특화된 요약 봇이야.");
        messagesArray.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content",
            "질문: " + originalQuestion + "\n\n" +
            "다음은 이 질문에 대해 웹에서 검색한 결과의 요약이야. 핵심적인 정보만 간결하고 친절하게 알려줘 (300자 이내):\n\n" +
            truncatedText);
        messagesArray.add(userMsg);

        return callGptApi(messagesArray);
    }
}
