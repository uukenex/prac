package my.prac.core.util;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ChatGPTUtils {

	final static String openaiKey = "Bearer "+PropsUtil.getProperty("keys","openaiKey");
	final static String openaiUrl = "https://api.openai.com/v1/chat/completions";

	/**
	 url 
	 ===============================================
	 https://api.openai.com/v1/chat/completions
	 ===============================================
	 request
	 ===============================================
	{
	    "model": "gpt-3.5-turbo",
	    "messages": [
	        {
		        "role":"system",
		        "content":"200자 이내로 대답해줘"
	        },
	        {
	            "role": "user",
	            "content": "국내여행 추천"
	        }
	    ],
	    "temperature": 1,
	    "max_tokens":250
	}
	===============================================
	response 
	===============================================

	{
	    "id": "chatcmpl-9NEhjsaoBd5lje8yBlL8pM3KVuzVM",
	    "object": "chat.completion",
	    "created": 1715325095,
	    "model": "gpt-3.5-turbo-0125",
	    "choices": [
	        {
	            "index": 0,
	            "message": {
	                "role": "assistant",
	                "content": "전라남도 순천이나 강원도 속초를 추천해요. 자연 경치와 맛집 볼거리가 풍부하기 때문이에요."
	            },
	            "logprobs": null,
	            "finish_reason": "stop"
	        }
	    ],
	    "usage": {
	        "prompt_tokens": 34,
	        "completion_tokens": 52,
	        "total_tokens": 86
	    },
	    "system_fingerprint": null
	}
	===============================================
	 */
	/*
	public static String chatgpt_message_post(String chatMsg) throws Exception { 
		
		BufferedReader in = null;
		List<HashMap<String,String>> messageList = new ArrayList<>();
		
		HashMap<String,String> hs = new HashMap<>();
		hs.put("role", "system");
		hs.put("content", "200자 이내로 대답해줘");
		messageList.add(hs);
		
		HashMap<String,String> hs2 = new HashMap<>();
		hs2.put("role", "system");
		hs2.put("content", chatMsg);
		messageList.add(hs2);
		
		Map<String, Object> requestBody = new HashMap<>();
	    requestBody.put("messages", messageList);
	    requestBody.put("model","gpt-3.5-turbo");
	    requestBody.put("temperature", 1);
	    requestBody.put("max_tokens", 250);
		
		
		try {
			HttpHeaders headers = new HttpHeaders();
		    headers.setContentType(MediaType.APPLICATION_JSON);
		    headers.set("Authorization", openaiKey );
			
			HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

		    RestTemplate restTemplate = new RestTemplate();
		    ResponseEntity<Map> response = restTemplate.postForEntity(openaiUrl, requestEntity, Map.class);
		    Map<String, Object> responseBody = response.getBody();

		    return responseBody.toString();
			
		} catch (Exception e) {
			System.out.println(e);
			throw e;
		} finally {
	    	if ( in != null ){
	        	in.close();
	        }
	    }
			
	}
	 */
	public static String chatgpt_message_post2(String chatMsg) throws Exception {

		// GPT API 엔드포인트
		String endpoint = "https://api.openai.com/v1/chat/completions";

		// POST 요청을 생성하고 데이터를 설정합니다.
		HttpPost httpPost = new HttpPost(endpoint);
		httpPost.setHeader("Content-Type", "application/json");
		httpPost.setHeader("Authorization", openaiKey);

		// GPT에 전달할 JSON 데이터 생성
		

		String json = "{\n" +
		        "    \"model\": \"gpt-3.5-turbo\",\n" +
		        "    \"messages\": [\n" +
		        "        {\n" +
		        "            \"role\": \"system\",\n" +
		        "            \"content\": \"너의이름은 '람쥐봇' 이야. 주인은 '일어난다람쥐' 이고, 항상 존댓말로 답을 줘."
		        + 	"앞으로의 명령을 저장하지말고, 질문에 대해 400자 내로 10초이내에 대답해줘.\"\n" +
		        "        },\n" +
		        "        {\n" +
		        "            \"role\": \"user\",\n" +
		        "            \"content\": \"" + chatMsg + "\"\n" +
		        "        }\n" +
		        "    ],\n" +
		        "    \"temperature\": 1,\n" +
		        "    \"max_tokens\": 450\n" +
		        "}";
		
		StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
		httpPost.setEntity(entity);

		// HttpClient 생성 및 요청 보내기
		try (CloseableHttpClient httpClient = HttpClients.createDefault();
				CloseableHttpResponse response = httpClient.execute(httpPost)) {

			// 응답을 문자열로 변환
			HttpEntity responseEntity = (HttpEntity) response.getEntity();
			String responseString = EntityUtils.toString(responseEntity);

			// 결과 출력
			//System.out.println("Response: " + responseString);
			return responseString;
		}
		
	}
	
	
}
