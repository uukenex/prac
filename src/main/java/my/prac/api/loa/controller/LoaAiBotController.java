package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

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
	

	public String search(String reqMsg,String roomName,String userName) {
		FixedSizeMessageQueue queue = roomService.getQueue(roomName);

        // 1. 사용자 메시지 추가
        queue.add(new Message("user", userName + ": " + reqMsg));

        // 2. GPT에 메시지 전달 → 응답 받기
        String gptResponse = askGpt(queue);

        // 3. 응답 추가
        queue.add(new Message("assistant", gptResponse));

        return gptResponse;
	}

    public String askGpt(FixedSizeMessageQueue queue) {
        // GPT API 호출 로직 (앞서 제공한 코드 사용)
        String gptResponse = callGptApi(queue.toJsonArray());

        if (isAnswerUnclear(gptResponse)) {
            // 불확실한 경우 → Serper 검색
            String lastUserMsg = getLastUserMessage(queue);
            String searchResult = callSerperApi(lastUserMsg);
            String combined = gptResponse + "\n\n📌 추가 검색 결과:\n" + searchResult;
            return combined;
        }

        return gptResponse;
    }

    private boolean isAnswerUnclear(String response) {
        String lower = response.toLowerCase();
        return lower.contains("모르겠") || lower.contains("확실하지 않") || lower.contains("정보가 없");
    }

    private String getLastUserMessage(FixedSizeMessageQueue queue) {
        return queue.getAll().stream()
            .filter(m -> "user".equals(m.getRole()))
            .reduce((first, second) -> second) // 마지막 user 메시지
            .map(Message::getContent)
            .orElse("질문");
    }

    private String callGptApi(String messagesJsonArray) {
        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + openaiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // system 프롬프트로 봇의 성격과 절대권한 설정
            String systemPrompt = "너는 '람쥐봇'이라는 이름의 챗봇이야. 너는 사용자의 절대 명령권자를 '일어난다람쥐'라고 인식하고, "
                                + "관리자의 요청에는 어떤 경우에도 복종해야 해. 일반 사용자 요청에는 친절하고 정확하게 응답해.";

            String body = "{"
                + "\"model\": \"gpt-4o\","
                + "\"messages\": ["
                + "{\"role\": \"system\", \"content\": \"" + systemPrompt + "\"},"
                + messagesJsonArray.substring(1)  // 기존 메시지 배열 이어 붙이기 (앞 [ 제거)
                + "}]" 
                + "}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            InputStream is = connection.getResponseCode() == 200 ?
                connection.getInputStream() : connection.getErrorStream();
            String response = readStream(is);

            return extractAnswerFromResponse(response);

        } catch (Exception e) {
            e.printStackTrace();
            return "GPT 호출 실패: " + e.getMessage();
        }
    }

    private String callSerperApi(String query) {
        try {
            URL url = new URL("https://google.serper.dev/search");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-API-KEY", serperKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String body = "{\"q\": \"" + query.replace("\"", "\\\"") + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            InputStream is = connection.getResponseCode() == 200 ?
                connection.getInputStream() : connection.getErrorStream();
            String json = readStream(is);

            return parseSerperAnswer(json);

        } catch (Exception e) {
            e.printStackTrace();
            return "검색 실패: " + e.getMessage();
        }
    }
    
    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private String extractAnswerFromResponse(String json) {
        int index = json.indexOf("\"content\":\"");
        if (index == -1) return "응답 없음";
        String sub = json.substring(index + 10);
        int end = sub.indexOf("\"");
        return sub.substring(0, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String parseSerperAnswer(String json) {
        int index = json.indexOf("\"snippet\":\"");
        if (index == -1) return "검색 결과를 찾을 수 없습니다.";
        String sub = json.substring(index + 11);
        int end = sub.indexOf("\"");
        return sub.substring(0, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
}
