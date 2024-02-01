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

import my.prac.core.util.PropsUtil;

@Controller
public class LoaChatController {
	static Logger logger = LoggerFactory.getLogger(LoaChatController.class);

	final String lostArkKey = "bearer "+PropsUtil.getProperty("keys","loaKey");

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
			case "ship":
				return shipSearch(param);
			case "weather":
				return weatherSearch(param);
			case "default":
				String val = autoResponse(param);
				if(val!=null&&!val.equals("")) {
					rtnMap.put("data", val);
					return rtnMap;
				}
								
			}

		} catch (Exception e) {
			e.printStackTrace();
			rtnMap.put("data", "ERR");
		}

		return null;
	}

	
	String autoResponse(String param) {
		String val="";

		switch (param) {
			case "/비실":
			case "/비실이":
				val="아니거든요";
				break;
			case "/돔돔":
			case "/돔돔쨩":
				val="비실이 아냐";
				break;
			case "/두목":
				val="용인피주먹";
				break;
			case "/우고":
				val="도굴단장";
				break;
			case "/도굴단장":
				val="우고";
				break;
			case "/퇴근":
				val="쥰내 신나는 텍스트";
				break;
			case "/나랑꽃":
				val="네 수령님";
				break;
			case "/수령님":
				val="네 나랑꽃님";
				break;
			default:
				System.out.println(param);
				break;
			
		}
		
		return val;
	}
	
	Map<String, Object> shipSearch(String userId) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();
		String retMsg="오늘의 항협";
		String retMsg1="";
		String retMsg2="";
		String retMsg3="";
		
		int cnt = 0; 
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/gamecontents/calendar";
		
		String returnData = connect_process(paramUrl);
		List<Map<String, Object>> data_list = new ObjectMapper().readValue(returnData, new TypeReference<List<Map<String, Object>>>() {
		});
		
		for(Map<String,Object> data_sub_list : data_list) {
			if(data_sub_list.get("CategoryName").equals("항해")) {
				if(data_sub_list.get("StartTimes")!=null) {
					List<String> start_time_list = (List<String>)data_sub_list.get("StartTimes");
					for(String time : start_time_list) {
						if(time.equals(StringToDate()+"T19:30:00")) {
							retMsg1 = retMsg1 +data_sub_list.get("ContentsName").toString()+", ";
						}
						if(time.equals(StringToDate()+"T21:30:00")) {
							retMsg2 = retMsg2 +data_sub_list.get("ContentsName").toString()+", ";
						}
						if(time.equals(StringToDate()+"T23:30:00")) {
							retMsg3 = retMsg3 +data_sub_list.get("ContentsName").toString()+", ";
						}
					}
				}
			}
		}
		
		retMsg1 = "<br>(오후 7:30)"+ retMsg1;
		retMsg2 = "<br>(오후 9:30)"+ retMsg2;
		retMsg3 = "<br>(오후11:30)"+ retMsg3;
		
		retMsg = retMsg+retMsg1+retMsg2+retMsg3;
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
	Map<String, Object> calendarSearch(String userId) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();
		String retMsg="오늘의 모험 섬";
		String retMsg1="";
		String retMsg2="";
		
		int cnt = 0; 
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
							//if(time.equals("2024-01-28"+"T09:00:00")) {
							if(time.equals(StringToDate()+"T09:00:00")) {
								switch(rewardItem.get("Name").toString()) {
									case "전설 ~ 고급 카드 팩 III":
										retMsg1 = retMsg1 + "</br>";
										retMsg1 = retMsg1 + data_sub_list.get("Location").toString()+" : ";
										retMsg1+="카드";
										cnt++;
										break;
									case "실링":
										retMsg1 = retMsg1 + "</br>";
										retMsg1 = retMsg1 + data_sub_list.get("Location").toString()+" : ";
										retMsg1+="실링";
										cnt++;
										break;
									case "대양의 주화 상자":
										retMsg1 = retMsg1 + "</br>";
										retMsg1 = retMsg1 + data_sub_list.get("Location").toString()+" : ";
										retMsg1+="주화";
										cnt++;
										break;
									case "골드":
										retMsg1 = retMsg1 + "</br>";
										retMsg1 = retMsg1 + data_sub_list.get("Location").toString()+" : ";
										retMsg1+="골드";
										cnt++;
										break;
									default:
										continue;
								}
							}
							//if(time.equals("2024-01-28"+"T19:00:00")) {
							if(time.equals(StringToDate()+"T19:00:00")) {
								switch(rewardItem.get("Name").toString()) {
									case "전설 ~ 고급 카드 팩 III":
										retMsg2 = retMsg2 + "</br>";
										retMsg2 = retMsg2 + data_sub_list.get("Location").toString()+" : ";
										retMsg2+="카드";
										cnt++;
										break;
									case "실링":
										retMsg2 = retMsg2 + "</br>";
										retMsg2 = retMsg2 + data_sub_list.get("Location").toString()+" : ";
										retMsg2+="실링";
										cnt++;
										break;
									case "대양의 주화 상자":
										retMsg2 = retMsg2 + "</br>";
										retMsg2 = retMsg2 + data_sub_list.get("Location").toString()+" : ";
										retMsg2+="주화";
										cnt++;
										break;
									case "골드":
										retMsg2 = retMsg2 + "</br>";
										retMsg2 = retMsg2 + data_sub_list.get("Location").toString()+" : ";
										retMsg2+="골드";
										cnt++;
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
		
		if(cnt>=6) {
			retMsg1 = "(오전)<br>"+ retMsg1;
			retMsg2 = "(오후)<br>"+ retMsg2;
		}
		
		retMsg = retMsg+retMsg1+retMsg2;
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
