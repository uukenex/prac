package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
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
import my.prac.core.util.PropsUtil;
import my.prac.core.util.RoomContextService;



@Controller
public class LoaAiBotController {

	final static String openaiKey = "Bearer "+PropsUtil.getProperty("keys","openaiKey");
	final static String serperKey = PropsUtil.getProperty("keys","serperKey");

	@Autowired
	RoomContextService roomService;
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
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

        if (shouldSearch(reqMsg, gptResponse)) {
        	String keyword = extractSearchKeywordFromQuestion(reqMsg); // GPT로 요약 요청

        	String rawSerperResult = callSerperApi(keyword);
            
            // ✅ JSON 파싱 및 핵심 정보 추출
            JsonObject parsedSerper = gson.fromJson(rawSerperResult, JsonObject.class);
            String extractedSummary = extractCoreInfoFromSerper(parsedSerper);

            // ✅ 요약 요청 (파싱된 결과로)
            String summarized = summarizeSerperResult(extractedSummary, reqMsg);

            finalResponse = "(추가 검색)\n\n" + summarized;
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
	
	private String extractSearchKeywordFromQuestion(String userQuestion) {
	    JsonArray keywordPrompt = new JsonArray();

	    keywordPrompt.add(makeSystem("너는 검색 키워드를 추출하는 봇이야."));
	    keywordPrompt.add(makeUser("다음 질문에서 검색할 핵심 키워드만 3~5단어 이내로 출력해줘. 예외 설명 없이 키워드만:\n\n" + userQuestion));

	    String response = callGptApi(keywordPrompt);
	    return response.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣\\s]", "").trim(); // 불필요한 문자 제거
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
        JsonArray messages = new JsonArray();
        messages.add(makeSystem("너는 AI 응답의 신뢰도를 판단하는 검증 봇이야."));
        messages.add(makeUser(
                "다음은 사용자 질문에 대한 응답이야. 응답이 유익하거나 실질적인 정보를 담고 있으면 false, " +
                "그렇지 않고 모호하거나 모르겠다고만 답했다면 true라고만 말해줘. 예외 설명 없이 true 또는 false 하나만 출력해.\n\n" +
                "응답:\n" + gptResponse
            ));

        String response = callGptApi(messages);
        return response.trim().equalsIgnoreCase("true");
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
    
    private JsonObject makeSystem(String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "system");
        obj.addProperty("content", content);
        return obj;
    }

    private JsonObject makeUser(String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");
        obj.addProperty("content", content);
        return obj;
    }
    
    private boolean shouldSearch(String userQuestion, String gptResponse) {
        // Step 1: GPT 응답에 실질 정보가 있는지 확인
        if (hasUsefulInfo(gptResponse)) return false;

        // Step 2: 질문이 정보성 질문인지 확인
        return isInfoSeekingQuestion(userQuestion);
    }

    private boolean hasUsefulInfo(String gptResponse) {
        JsonArray messages = new JsonArray();
        messages.add(makeSystem("너는 AI 응답의 유용성을 판별하는 평가 봇이야."));
        messages.add(makeUser(
            "다음 응답이 유익하거나 실질적인 정보가 있으면 true, 없고 모르겠다는 말만 있으면 false라고만 답해:\n\n" +
            gptResponse));
        
        String result = callGptApi(messages);
        return result.trim().equalsIgnoreCase("true");
    }
    
    private boolean isInfoSeekingQuestion(String userQuestion) {
        JsonArray messages = new JsonArray();
        messages.add(makeSystem("너는 사용자의 질문이 정보 탐색형인지 판단하는 봇이야."));
        messages.add(makeUser(
            "다음 질문이 잡담이 아닌, 실제로 정보나 사실을 찾는 질문이면 true, 그냥 대화/농담/감정 표현이면 false라고만 답해:\n\n" +
            userQuestion));

        String result = callGptApi(messages);
        return result.trim().equalsIgnoreCase("true");
    }
}
