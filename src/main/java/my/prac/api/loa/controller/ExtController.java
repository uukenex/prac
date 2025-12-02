package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class ExtController {
	static Logger logger = LoggerFactory.getLogger(ExtController.class);
	final String NL= "♬";
	
	private final ObjectMapper objectMapper = new ObjectMapper();

	// 3) URL 기반 (네가 원하는 방식)
	public String buildCritMessageFromUrl(String url) throws IOException {
		String json = getJsonFromUrl(url);
		JsonNode root = objectMapper.readTree(json);
		return buildCritMessageFromNode(root);
	}

	// 실제 메시지 생성 로직은 여기 하나만 유지
	private String buildCritMessageFromNode(JsonNode root) {
		JsonNode characterInfo = root.path("characterInfo");
		String nickname = characterInfo.path("nickname").asText("");
		String className = characterInfo.path("className").asText("");
		String itemLevel = characterInfo.path("itemLevel").asText("");
		String arkPassiveTitle = characterInfo.path("arkPassiveTitle").asText("");

		JsonNode critRateInfo = root.path("critRateInfo");
		double total = critRateInfo.path("total").asDouble(0.0);

		StringBuilder sb = new StringBuilder();

		// 1) 캐릭터 기본 정보
		sb.append("[").append(nickname).append("] ").append(className);

		if (!itemLevel.isEmpty()) {
			sb.append(" (").append(itemLevel).append(")");
		}
		if (!arkPassiveTitle.isEmpty()) {
			sb.append(NL+"- 아크패시브: ").append(arkPassiveTitle);
		}
		sb.append(NL);

		// 2) 총 치적
		sb.append("총 치명타 적중률: ").append(String.format("%.2f", total)).append("%"+NL+NL);

		// 3) 상세 치적
		sb.append("[상세]"+NL);

		JsonNode details = critRateInfo.path("details");
		if (details.isArray()) {
			for (JsonNode d : details) {
				String source = d.path("source").asText("");
				double value = d.path("value").asDouble(0.0);
				String note = d.path("note").asText("");

				sb.append("- ");

				if (!source.isEmpty()) {
					sb.append(source);
				}
				if (!note.isEmpty()) {
					sb.append(" ").append(note);
				}

				sb.append(" : ").append(String.format("%.2f", value)).append("%"+NL);
			}
		}

		// 조건부 치적도 나중에 쓸 수 있게 남겨둠
		JsonNode conditional = critRateInfo.path("conditional");
		if (conditional.isArray() && conditional.size() > 0) {
			sb.append(NL+"[조건부 치적]"+NL);
			for (JsonNode c : conditional) {
				String source = c.path("source").asText("");
				double value = c.path("value").asDouble(0.0);
				String note = c.path("note").asText("");

				sb.append("- ");
				if (!source.isEmpty()) {
					sb.append(source);
				}
				if (!note.isEmpty()) {
					sb.append(" ").append(note);
				}
				sb.append(" : ").append(String.format("%.2f", value)).append("%"+NL);
			}
		}

		sb.append(NL+"해당API는 다로아에서 제공받은 정보입니다."+NL);
		return sb.toString();
	}

	public static String getJsonFromUrl(String urlStr) throws IOException {
		HttpURLConnection conn = null;
		BufferedReader br = null;

		try {
			URL url = new URL(urlStr);
			System.out.println(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(10000); // 5초 타임아웃
			conn.setReadTimeout(10000); // 5초 타임아웃
			
			conn.setRequestProperty("x-api-key", "sk_1Qn1h12xEQD6z758szfzjlGaoleC0yiJ");
			conn.setRequestProperty("accept", "application/json");
			
			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP error code: " + responseCode);
			}

			br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;

			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

			return sb.toString();

		} finally {
			if (br != null)
				try {
					br.close();
				} catch (Exception e) {
				}
			if (conn != null)
				conn.disconnect();
		}
	}
	
	//파일생성테스트
	public String fetchMerchantServer5Cached() throws Exception {


	    // 오늘 날짜 (yyyy-MM-dd)
	    String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

	    String filePath = "C:/devkit/merchant_server5"+today+".json"; 
	    File file = new File(filePath);
	    // 파일이 존재하면 → 날짜 비교
	    if (file.exists()) {
	        String fileDate = new java.text.SimpleDateFormat("yyyy-MM-dd")
	                .format(new java.util.Date(file.lastModified()));

	        // 이미 오늘 파일이 있으면 API 호출 안 함
	        if (fileDate.equals(today)) {
	            return readFileJson(filePath);
	        }
	    }

	    // 존재하지 않거나 → 하루 지나서 갱신해야 함 → API 호출
	    String json = fetchMerchantServer5();  // 기존 API 호출 메서드

	    // 디렉토리 없으면 생성
	    file.getParentFile().mkdirs();

	    // 파일 저장
	    saveJsonToFile(json, filePath);

	    return json;
	}
	
	public String fetchMerchantServer5() throws Exception {
	    String url = "https://api.korlark.com/lostark/merchant/reports?server=5";

	    HttpClient client = HttpClientBuilder.create().build();
	    HttpGet get = new HttpGet(url);

	    get.setHeader("accept", "application/json");
	    get.setHeader("User-Agent", "Mozilla/5.0");

	    HttpResponse response = client.execute(get);
	    String json = EntityUtils.toString(response.getEntity(), "UTF-8");

	    return json;
	}
	
	public String readFileJson(String filePath) throws Exception {
	    return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)), "UTF-8");
	}
	public void saveJsonToFile(String json, String filePath) throws Exception {
	    java.nio.file.Files.write(
	            java.nio.file.Paths.get(filePath),
	            json.getBytes("UTF-8")
	    );
	}
}
