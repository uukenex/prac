package my.prac.core.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.json.JSONObject;

public class ChatGPTUtils {

	final static String openaiKey = "bearer "+PropsUtil.getProperty("keys","openaiKey");
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
	
	public static String chatgpt_message_post(String chatMsg) throws Exception { 
		
		Map<String, Object> resultMap = new HashMap<String, Object>();
		BufferedReader in = null;
		String jsonStr = "";
		String responseData = "";	
		BufferedReader br = null;
		StringBuffer sb = null;
		
		
		JSONObject json ;
		List<HashMap<String,String>> messageList = new ArrayList<>();
		
		HashMap<String,String> hs = new HashMap<>();
		hs.put("role", "system");
		hs.put("content", "200자 이내로 대답해줘");
		messageList.add(hs);
		
		HashMap<String,String> hs2 = new HashMap<>();
		hs2.put("role", "system");
		hs2.put("content", chatMsg);
		messageList.add(hs2);
		
		json = new JSONObject();
		json.put("model", "gpt-3.5-turbo");
		json.put("messages", messageList);
		json.put("temperature", "1");
		json.put("max_tokens", "250");
		
		try {
			URL url = new URL(openaiUrl);
			
			
			
		    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		    conn.setRequestMethod("POST");
		    conn.setRequestProperty("Content-Type", "application/json");
		    conn.setRequestProperty("Accept", "application/json");
		    conn.setRequestProperty("Authorization", openaiKey);
		    conn.setDoOutput(true);

		    try (OutputStream os = conn.getOutputStream()){
				byte request_data[] = json.toString().getBytes("utf-8");
				os.write(request_data);
				os.close();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		    
		    conn.connect();
			
			br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));	
			sb = new StringBuffer();	       
			while ((responseData = br.readLine()) != null) {
				sb.append(responseData); 
			}
	 
		} catch (Exception e) {
			throw e;
		} finally {
	    	if ( in != null ){
	        	in.close();
	        }
	    }
			
		return sb.toString(); 
	}
	
	
}
