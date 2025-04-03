package my.prac.core.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiUtils {

	final static String googleaiKey = PropsUtil.getProperty("keys","googleaiKey");
	final static String googleaiVer ="gemini-1.5-flash-latest";
	final static String googleaiUrl = "https://generativelanguage.googleapis.com/v1beta/models/"+googleaiVer+":generateContent?key=";

	/**
	 url 
	 ===============================================
	 https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key={googleaiKey}
	 ===============================================
	 request
	 ===============================================
	{
	    "contents": [
	        {
	            "parts": [
	                {
	                    "text": "구글 gemini-api 에 chatgpt 처럼 상위명령어를 넣으려면 어떻게 해야할까?"
	                }
	            ]
	        }
	    ]
	}
	===============================================
	response 
	===============================================

	{
	    "candidates": [
	        {
	            "content": {
	                "parts": [
	                    {
	                        "text": "Google Gemini API는 현재 ChatGPT처럼 명령어(프롬프트)에 상위 명령어를 직접적으로 지원하지 않습니다.  ChatGPT의 \"상위 명령어\" 기능은 모델이 프롬프트의 맥락과 의도를 이해하고, 그에 따라 응답을 생성하는 능력에 기반합니다.  Gemini API는 강력한 기능을 가지고 있지만, ChatGPT처럼 자연어를 이용한 세분화된 명령어 제어는 아직 완벽하게 제공하지 않습니다.\n\nGemini API를 이용하여 ChatGPT의 상위 명령어 기능과 유사한 결과를 얻으려면, 프롬프트 엔지니어링 기법을 사용해야 합니다.  핵심은 **상위 명령어를 명시적으로 프롬프트에 포함**하는 것입니다.  예를 들어:\n\n* **ChatGPT 스타일:** \"다음 텍스트를 요약하고, 긍정적, 부정적, 중립적 관점에서 분석해줘.  요약은 100자 이내로 해줘.\"\n\n* **Gemini API 스타일:** \"다음 텍스트를 100자 이내로 요약해주세요.  요약 후, 긍정적, 부정적, 중립적 관점에서 분석 결과를 각각 별도로 제시해주세요.\"\n\n\n핵심 차이점은 ChatGPT는 \"요약하고 분석해줘\" 라는 상위 명령어를 이해하여 세부적인 작업(100자 이내, 긍정/부정/중립 분석)을 스스로 추론하는 반면, Gemini API는 상위 명령어를 구체적인 지시 사항으로 분해하여 명시적으로 제공해야 합니다.\n\n즉, Gemini API를 사용하여 상위 명령어 효과를 내려면 다음과 같은 전략을 고려하세요:\n\n* **명령어 분해:** 상위 명령어를 여러 개의 작고 명확한 하위 명령어로 나눕니다.\n* **세부 지시:** 각 하위 명령어에 대해 구체적인 지침(예: 길이 제한, 형식, 출력 형태 등)을 제공합니다.\n* **예시 제공:**  원하는 출력 형태를 보여주는 예시를 프롬프트에 포함시키면 모델이 이해하기 쉬워집니다.\n* **반복 및 개선:**  원하는 결과를 얻지 못하면 프롬프트를 수정하고 실험하는 과정을 반복합니다.  다양한 프롬프트를 시도하여 모델의 반응을 관찰하는 것이 중요합니다.\n* **매개변수 조정:** Gemini API가 제공하는 다양한 매개변수(temperature, top_p 등)를 조정하여 응답의 창의성과 정확성을 제어할 수 있습니다.\n\n\n결론적으로, Gemini API는 ChatGPT처럼 자연스러운 상위 명령어 이해를 아직 완벽하게 지원하지 않습니다.  그러나 프롬프트 엔지니어링을 통해 상위 명령어와 유사한 효과를 얻을 수 있습니다.  명확하고 구체적인 지침을 제공하는 것이 성공의 열쇠입니다.  Google의 Gemini API 문서를 참고하여 가능한 매개변수와 최적의 프롬프트 작성 방법을 탐색해보세요.\n"
	                    }
	                ],
	                "role": "model"
	            },
	            "finishReason": "STOP",
	            "avgLogprobs": -0.27278621613033233
	        }
	    ],
	    "usageMetadata": {
	        "promptTokenCount": 30,
	        "candidatesTokenCount": 756,
	        "totalTokenCount": 786,
	        "promptTokensDetails": [
	            {
	                "modality": "TEXT",
	                "tokenCount": 30
	            }
	        ],
	        "candidatesTokensDetails": [
	            {
	                "modality": "TEXT",
	                "tokenCount": 756
	            }
	        ]
	    },
	    "modelVersion": "gemini-1.5-flash-latest"
	}

	===============================================
	 */
	

	public static String callGeminiApi(String prompt) throws Exception {
		String urlString = googleaiUrl + googleaiKey;
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		// JSON 형식이 올바른지 확인
		String jsonInputString = String.format("{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}",
				prompt.replace("\n", "\\n"));

		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInputString.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		int responseCode = conn.getResponseCode();
		if (responseCode == 200) { // 성공적인 응답 코드 200을 체크
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
				StringBuilder response = new StringBuilder();
				String responseLine;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}

				// JSON 응답에서 메시지만 추출
				JSONObject jsonResponse = new JSONObject(response.toString());
				if (jsonResponse.has("candidates")) {
					JSONArray candidates = jsonResponse.getJSONArray("candidates");
					StringBuilder message = new StringBuilder();
					for (int i = 0; i < candidates.length(); i++) {
						JSONObject content = candidates.getJSONObject(i).getJSONObject("content");
						JSONArray parts = content.getJSONArray("parts");
						for (int j = 0; j < parts.length(); j++) {
							message.append(parts.getJSONObject(j).getString("text")).append("\n");
						}
					}
					return message.toString().trim();
				} else {
					return "No candidates found in the response.";
				}
			}
		} else if (responseCode == 401) {
			throw new RuntimeException(
					"Failed : HTTP error code : 401 Unauthorized. Please check your API key and permissions.");
		} else if (responseCode == 400) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
				StringBuilder response = new StringBuilder();
				String responseLine;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}
				throw new RuntimeException(
						"Failed : HTTP error code : 400 Bad Request. Response: " + response.toString());
			}
		} else {
			throw new RuntimeException("Failed : HTTP error code : " + responseCode);
		}
	}
	
	
}
