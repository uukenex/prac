package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class LoaChatController {
	static Logger logger = LoggerFactory.getLogger(LoaChatController.class);

	final String lostArkKey = "bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6IktYMk40TkRDSTJ5NTA5NWpjTWk5TllqY2lyZyIsImtpZCI6IktYMk40TkRDSTJ5NTA5NWpjTWk5TllqY2lyZyJ9.eyJpc3MiOiJodHRwczovL2x1ZHkuZ2FtZS5vbnN0b3ZlLmNvbSIsImF1ZCI6Imh0dHBzOi8vbHVkeS5nYW1lLm9uc3RvdmUuY29tL3Jlc291cmNlcyIsImNsaWVudF9pZCI6IjEwMDAwMDAwMDA0NDgzODUifQ.Os0TiNgS7azA9ZoR60IXzHsNmZDt5Dqan1PvSel2BQhXtsrUe9OPZWyDoLtQqsAoIQPPsbb5tMGwL59vCF7S9YLdUdZm0OKs9RH3NeBs_Z7uMqCMejS5B0iS5MqqtEuDG-y23bxZfSvUcj1-THGgq4ACg9titnaJ_TBQYwq8g-ihruAE9kk9N80IoBtBzfz_F9htclZPphQ3TjztFi1zbMxT0URta1Y-TuGCq7ZC62h9mr2lVN4beunb8qN4CokCA4FDWmGY9DaWwL6C-mBYR9JJLQAJRgsusqErhZPKhXSzf56cSsrDisu_jn0PX-FWsmkOWM4HyohHdXDeW9tMDQ";

	// final String lostArkAPIurl =
	// "https://developer-lostark.game.onstove.com/armories/characters/일어난다람쥐/equipment";
	final String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	@RequestMapping(value = "/loa/chat", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> chatApplication(@RequestParam(required = true) String action,
			@RequestParam(required = true) String param) {
		HashMap<String, Object> rtnMap = new HashMap<>();

		try {
			System.out.println(action + " " + param);
			switch (action) {
			case "equipment":
				return equipmentSearch(param);
			case "calendar":
				return calendarSearch(param);
			case "weather":
				return weatherSearch(param);
			}
			rtnMap.put("CODE", "OK");

		} catch (Exception e) {
			e.printStackTrace();
			rtnMap.put("CODE", "ERR");
		}

		return rtnMap;
	}

	Map<String, Object> calendarSearch(String userId) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();
		String retMsg="오늘의 모험섬";
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/gamecontents/calendar";
		
		String returnData = connect_process(paramUrl);
		List<Map<String, Object>> data_list = new ObjectMapper().readValue(returnData, new TypeReference<List<Map<String, Object>>>() {
		});
		
		for(Map<String,Object> data_sub_list : data_list) {
			
			if(data_sub_list.get("CategoryName").equals("모험 섬")) {
				
				
				
				List<Map<String, Object>> rewardItemsList = (List<Map<String, Object>>)data_sub_list.get("RewardItems");
				for(Map<String, Object> rewardItem : rewardItemsList) {
					
					if(rewardItem.get("StartTimes")!=null) {
						List<String> start_time_list = (List<String>)rewardItem.get("StartTimes");
						for(String time : start_time_list) {
							if(time.equals(StringToDate()+"T11:00:00")) {
								retMsg = retMsg + "</br>";
								
								switch(rewardItem.get("Name").toString()) {
									case "전설 ~ 고급 카드 팩 III":
										retMsg = retMsg + data_sub_list.get("Location").toString()+" : ";
										retMsg+="카드";
										break;
									case "실링":
										retMsg = retMsg + data_sub_list.get("Location").toString()+" : ";
										retMsg+="실링";
										break;
									case "대양의 주화 상자":
										retMsg = retMsg + data_sub_list.get("Location").toString()+" : ";
										retMsg+="주화";
										break;
									case "골드":
										retMsg = retMsg + data_sub_list.get("Location").toString()+" : ";
										retMsg+="골드";
										break;
									default:
										continue;
								}
							}
						}
					}
					
				}
				
			}
			
		}
		System.out.println(retMsg);
		rtnMap.put("data", retMsg);
		
		// System.out.println(rtnMap);
		/*
		 * List<Map<String,Object>> armoryEquipment = (List<Map<String,Object>>)
		 * rtnMap.get("ArmoryEquipment");
		 * 
		 * String equipOne = ""; for(Map<String,Object> equip: armoryEquipment){
		 * System.out.println("==================="); System.out.println(equip);
		 * equipOne=equip.toString();
		 * 
		 * }
		 */

		return rtnMap;
	}

	Map<String, Object> equipmentSearch(String userId) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();
		String retMsg="";
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment";

		String returnData = connect_process(paramUrl);
		
		Map<String, Object> map = new ObjectMapper().readValue(returnData, new TypeReference<Map<String, Object>>() {
		});
		
		// System.out.println(rtnMap);

		//List<Map<String, Object>> armoryEquipment = (List<Map<String, Object>>) map_list.get("ArmoryEquipment");
		/*
		 * String equipOne = ""; for (Map<String, Object> equip : armoryEquipment) {
		 * System.out.println("==================="); System.out.println(equip);
		 * equipOne = equip.toString();
		 * 
		 * }
		 */
		rtnMap.put("data", map);
		return rtnMap;
	}

	
	String connect_process(String paramUrl) throws Exception {
		List<Map<String, Object>>rtnMap = new ArrayList<>();
		
		URL url = new URL(paramUrl);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		// POST 요청을 위해 기본값이 false인 setDoOutput을 true로

		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		conn.setRequestProperty("Authorization", lostArkKey);

		// 결과 코드가 200이라면 성공
		int responseCode = conn.getResponseCode();
		System.out.println("### getAccessToken responseCode : " + responseCode);

		// 서버로부터 데이터 읽어오기
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line).append("\n");
			}
		}

		return sb.toString();
	}
	
	
	
	Map<String, Object> weatherSearch(String area) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();

		String retMsg = "";
		try {
			setSSL();
			String WeatherURL = "https://m.search.naver.com/search.naver?&query=날씨+" + area;
			Document doc = Jsoup.connect(WeatherURL).get();
			String cur_temp = doc.select(".weather_info ._today .temperature_text strong").text();
			String weather = doc.select(".weather_info ._today .before_slash").text();
			String diff_temp = doc.select(".weather_info ._today .temperature_info .temperature").text();// 어제와 온도차이
			String v1 = doc.select(".weather_info ._today .summary_list .sort:eq(0) .desc").text();// 체감
			String v2 = doc.select(".weather_info ._today .summary_list .sort:eq(1) .desc").text();// 습도
			String v3 = doc.select(".weather_info ._today .summary_list .sort:eq(2) .desc").text();// 풍속
			retMsg += "오늘날씨 : " + weather;
			retMsg += "</br>현재온도 : " + cur_temp;
			retMsg += "</br>체감온도 : " + v1;
			retMsg += "</br>습도 : " + v2;
			retMsg += "</br>풍속 : " + v3;
			retMsg += "</br>현재 " + area + "의 온도는 " + cur_temp + " 이며 어제보다 " + diff_temp;
		} catch (Exception e) {
			e.printStackTrace();
			retMsg = "불러올 수 없는 지역이거나 지원되지 않는 지역입니다.";
		}
		rtnMap.put("data", retMsg);
		return rtnMap;
	}
	
	public void setSSL() throws NoSuchAlgorithmException, KeyManagementException {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}
		} };
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new SecureRandom());
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		});
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	}

	public String StringToDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateTime = dateFormat.format(new Date());
        
        return dateTime;
	}
}
