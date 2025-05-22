package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
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
            String fallback = callSerperApi(reqMsg);
            finalResponse = "(GPT 답변이 불완전하여 검색 결과를 추가합니다)\n\n" + gptResponse + "\n\n🔍 추가 검색:\n" + fallback;
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
}
