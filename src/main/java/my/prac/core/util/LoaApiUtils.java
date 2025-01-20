package my.prac.core.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LoaApiUtils {

	final static String lostArkKey_m1 = "bearer " + PropsUtil.getProperty("keys", "loaKey_m1");
	final static String lostArkKey_m2 = "bearer " + PropsUtil.getProperty("keys", "loaKey_m2");
	final static String lostArkKey_m3 = "bearer " + PropsUtil.getProperty("keys", "loaKey_m2");
	final static String lostArkKey_m4 = "bearer " + PropsUtil.getProperty("keys", "loaKey_m2");
	final static String lostArkKey_m5 = "bearer " + PropsUtil.getProperty("keys", "loaKey_m2");
	final static String lostArkKey_s1 = "bearer " + PropsUtil.getProperty("keys", "loaKey_s1");
	final static String lostArkKey_s2 = "bearer " + PropsUtil.getProperty("keys", "loaKey_s1");
	final static String lostArkKey_s3 = "bearer " + PropsUtil.getProperty("keys", "loaKey_s1");
	final static String lostArkKey_s4 = "bearer " + PropsUtil.getProperty("keys", "loaKey_s1");
	final static String lostArkKey_s5 = "bearer " + PropsUtil.getProperty("keys", "loaKey_s1");
	final static int TIMEOUT_VALUE = 10000;// 10초

	final static String[] key_list = { lostArkKey_m1, lostArkKey_m2, lostArkKey_m3, lostArkKey_m4, lostArkKey_m5,
									   lostArkKey_s1, lostArkKey_s2, lostArkKey_s3, lostArkKey_s4, lostArkKey_s5 
									 };

	public static String connect_process(String paramUrl) throws Exception {
		URL url = new URL(paramUrl);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		// POST 요청을 위해 기본값이 false인 setDoOutput을 true로

		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		
		Random rd = new Random();
		String key = key_list[rd.nextInt(key_list.length)];
		
		conn.setRequestProperty("Authorization", key);
		conn.setConnectTimeout(TIMEOUT_VALUE);
		conn.setReadTimeout(TIMEOUT_VALUE);
		
		// 결과 코드가 200이라면 성공
		//int responseCode = conn.getResponseCode();
		//System.out.println("### getAccessToken responseCode : " + responseCode);

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
	
	public static String connect_process_post(String paramUrl, String jsonBody) throws Exception { 
		
		Map<String, Object> resultMap = new HashMap<String, Object>();
		BufferedReader in = null;
		String jsonStr = "";
		String responseData = "";	
		BufferedReader br = null;
		StringBuffer sb = null;
		
		try {
			URL url = new URL(paramUrl);
			
			
			
		    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		    conn.setRequestMethod("POST");
		    conn.setRequestProperty("Content-Type", "application/json");
		    conn.setRequestProperty("Accept", "application/json");
		    
		    Random rd = new Random();
			String key = key_list[rd.nextInt(key_list.length)];
			
		    conn.setRequestProperty("Authorization", key);
		    conn.setConnectTimeout(TIMEOUT_VALUE);
			conn.setReadTimeout(TIMEOUT_VALUE);
		    conn.setDoOutput(true);

		    try (OutputStream os = conn.getOutputStream()){
				byte request_data[] = jsonBody.getBytes("utf-8");
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
	
	public static String shortClassName(String txt) {
		try {
			txt=txt.replaceAll("기상술사", "기　상");
			txt=txt.replaceAll("배틀마스터", "배　마");
			txt=txt.replaceAll("브레이커", "브　커");
			txt=txt.replaceAll("소울이터", "소　울");
			txt=txt.replaceAll("호크아이", "호　크");
			txt=txt.replaceAll("스트라이커", "스　커");
			txt=txt.replaceAll("소서리스", "소　서");
			txt=txt.replaceAll("건슬링어", "건　슬");
			txt=txt.replaceAll("블레이드", "블　레");
			txt=txt.replaceAll("블래스터", "블　래");
			txt=txt.replaceAll("인파이터", "인　파");
			txt=txt.replaceAll("슬레이어", "슬　레");
			txt=txt.replaceAll("디스트로이어", "디　트");
			txt=txt.replaceAll("홀리나이트", "홀　나");
			txt=txt.replaceAll("스카우터", "스　카");
			txt=txt.replaceAll("데빌헌터", "데　헌");
			txt=txt.replaceAll("아르카나", "알　카");
			txt=txt.replaceAll("바드", "바　드");
			txt=txt.replaceAll("리퍼", "리　퍼");
			
		}catch(Exception e) {
			txt="";
		}
		return txt;
	}
	public static String filterTextForMarket(String txt) {
		try {
			txt=txt.replaceAll("최상급 오레하 융화 재료", "최상레하");
			txt=txt.replaceAll("명예의 파편 주머니", "명파");
			txt=txt.replaceAll("정제된 ", "정");
			txt=txt.replaceAll("파괴강석", "파강");
			txt=txt.replaceAll("태양의 ", "");
			txt=txt.replaceAll("찬란한 명예의 돌파석", "찬명돌");
			txt=txt.replaceAll("10레벨 멸화의 보석", "10멸");
			txt=txt.replaceAll("10레벨 홍염의 보석", "10홍");
			txt=txt.replaceAll("9레벨 멸화의 보석", "9멸");
			txt=txt.replaceAll("9레벨 홍염의 보석", "9홍");
			
			txt=txt.replaceAll("10레벨 겁화의 보석", "10겁");
			txt=txt.replaceAll("9레벨 겁화의 보석", "9겁");
			txt=txt.replaceAll("8레벨 겁화의 보석", "8겁");
			txt=txt.replaceAll("7레벨 겁화의 보석", "7겁");
			
			txt=txt.replaceAll("10레벨 작열의 보석", "10작");
			txt=txt.replaceAll("9레벨 작열의 보석", "9작");
			txt=txt.replaceAll("8레벨 작열의 보석", "8작");
			txt=txt.replaceAll("7레벨 작열의 보석", "7작");
			
			txt=txt.replaceAll("용암", "[무기]용암");
			txt=txt.replaceAll("빙하", "[방어구]빙하");
			txt=txt.replaceAll(" 각인서", "");
			
		}catch(Exception e) {
			txt="";
		}
	
		return txt;
	}
	public static int totalGoldForEngrave(String name,String lv) {
		double gold = 0;
		if(lv.equals("0")) {
			return 0;
		}
		switch(name) {
			case "아드레날린":
				gold = 35;
				break;
			case "원한":
				gold = 27;
				break;
			case "예리한 둔기":
				gold = 19;
				break;
			case "저주받은 인형":
				gold = 16;
				break;
			case "돌격대장":
				gold = 18;
				break;
			case "타격의 대가":
				gold = 10;
				break;
			case "기습의 대가":
				gold = 10.5;
				break;
			case "질량 증가":
				gold = 10;
				break;
			case "슈퍼 차지":
				gold = 6;
				break;
			case "결투의 대가":
				gold = 6;
				break;
			case "속전속결":
				gold = 3;
				break;
			case "바리케이드":
				gold = 2.5;
				break;
			case "안정된 상태":
				gold = 3;
				break;
			case "마나 효율 증가":
				gold = 3;
				break;
			case "정밀 단도":
				gold = 1;
				break;
			case "에테르 포식자":
				gold = 0.5;
				break;
			case "마나의 흐름":
				gold = 7;
				break;
			case "구슬동자":
				gold = 4;
				break;
			case "전문의":
				gold = 4.5;
				break;
			case "각성":
				gold = 3;
				break;
			case "중갑 착용":
				gold = 1;
				break;
			case "급소 타격":
				gold = 0.5;
				break;
		}
		
		switch(lv) {
			case "4":
				gold = gold*20;
				break;
			case "3":
				gold = gold*15;
				break;
			case "2":
				gold = gold*10;
				break;
			case "1":
				gold = gold*5;
				break;
		}
		return (int)gold;
		
	}
	
	public static String switchWord(String txt) {
		if(txt.indexOf("예둔")>=0 || txt.indexOf("예리")>=0) {
			txt="예리한 둔기";
		}else if(txt.indexOf("아드")>=0) {
			txt="아드레날린";
		}else if(txt.indexOf("바리")>=0) {
			txt="바리케이드";
		}else if(txt.indexOf("돌대")>=0 || txt.indexOf("돌격")>=0) {
			txt="돌격대장";
		}else if(txt.indexOf("질증")>=0 || txt.indexOf("질량")>=0) {
			txt="질량 증가";
		}else if(txt.indexOf("슈차")>=0 || txt.indexOf("슈퍼")>=0) {
			txt="슈퍼 차지";
		}else if(txt.indexOf("마흐")>=0 || txt.indexOf("마나의")>=0) {
			txt="마나의 흐름";
		}else if(txt.indexOf("정단")>=0 || txt.indexOf("정밀")>=0) {
			txt="정밀 단도";
		}else if(txt.indexOf("저받")>=0 || txt.indexOf("저주")>=0) {
			txt="저주받은 인형";
		}else if(txt.indexOf("타대")>=0 || txt.indexOf("타격")>=0) {
			txt="타격의 대가";
		}else if(txt.indexOf("기대")>=0 || txt.indexOf("기습")>=0) {
			txt="기습의 대가";
		}else if(txt.indexOf("속속")>=0 || txt.indexOf("속전")>=0) {
			txt="속전속결";
		}else if(txt.indexOf("안상")>=0 || txt.indexOf("안정")>=0) {
			txt="안정된 상태";
		}else if(txt.indexOf("에포")>=0 || txt.indexOf("에테")>=0) {
			txt="에테르 포식자";
		}else if(txt.indexOf("결대")>=0 || txt.indexOf("결투")>=0) {
			txt="결투의 대가";
		}else if(txt.indexOf("달저")>=0 || txt.indexOf("달인")>=0) {
			txt="달인의 저력";
		}else if(txt.indexOf("구동")>=0 || txt.indexOf("구슬")>=0) {
			txt="구슬동자";
		}else if(txt.indexOf("마효")>=0 || txt.indexOf("마나효")>=0 || txt.indexOf("마나 효")>=0) {
			txt="마나 효율 증가";
		}else if(txt.indexOf("최마")>=0 || txt.indexOf("최대마나")>=0 || txt.indexOf("최대 마나")>=0) {
			txt="최대 마나 증가";
		}else if(txt.indexOf("정흡")>=0 || txt.indexOf("정기")>=0) {
			txt="정기 흡수";
		}else if(txt.indexOf("급타")>=0 || txt.indexOf("급소")>=0) {
			txt="급소 타격";
		}else if(txt.indexOf("추진")>=0) {
			txt="추진력";
		}else if(txt.indexOf("폭발")>=0 || txt.indexOf("폭전")>=0) {
			txt="폭발물 전문가";
		}else if(txt.indexOf("선수")>=0 || txt.indexOf("선필")>=0) {
			txt="선수필승";
		}else if(txt.indexOf("승부")>=0) {
			txt="승부사";
		}else if(txt.indexOf("시선")>=0 || txt.indexOf("시집")>=0) {
			txt="시선 집중";
		}else if(txt.indexOf("중갑")>=0 || txt.indexOf("중착")>=0) {
			txt="중갑 착용";
		}
		
		
		return txt;
	}
	
	public static Boolean marketConditionYn(String txt) {
		try {
			if(
				txt.indexOf("아드")>=0
			 || txt.indexOf("예리")>=0
			 || txt.indexOf("원한")>=0
			 || txt.indexOf("돌격")>=0
			 || txt.indexOf("질량")>=0
			 || txt.indexOf("슈퍼")>=0
			 || txt.indexOf("마나")>=0
			 || txt.indexOf("정밀")>=0
			 || txt.indexOf("저주")>=0
			 || txt.indexOf("타격")>=0
			 || txt.indexOf("기습")>=0
			 || txt.indexOf("속전")>=0
			 || txt.indexOf("안정")>=0
			 || txt.indexOf("에테")>=0
			 || txt.indexOf("바리")>=0
			 || txt.indexOf("결투")>=0
			 || txt.indexOf("달인")>=0
			 || txt.indexOf("구슬")>=0
			 || txt.indexOf("전문")>=0
			 || txt.indexOf("마나")>=0
			 || txt.indexOf("각성")>=0
			 || txt.indexOf("중갑")>=0
			 || txt.indexOf("최대")>=0
			 || txt.indexOf("정기")>=0
			 || txt.indexOf("급소")>=0
			 || txt.indexOf("추진")>=0
			 || txt.indexOf("폭발")>=0
			 || txt.indexOf("선수")>=0
			 || txt.indexOf("승부")>=0
			 || txt.indexOf("시선")>=0
			 || txt.indexOf("중갑")>=0
			 
			){
				return true;
			}else {
				return false;
			}
			
			
		}catch(Exception e) {
			return false;
		}
	
	}
	
	public static String filterTextForEngrave(String txt) {
		try {
			if(
				txt.indexOf("아드레날린")>=0
			 || txt.indexOf("예리한 둔기")>=0
			 || txt.indexOf("원한")>=0
			 || txt.indexOf("돌격대장")>=0
			 || txt.indexOf("질량 증가")>=0
			 || txt.indexOf("슈퍼 차지")>=0
			 || txt.indexOf("마나 효율 증가")>=0
			 || txt.indexOf("정밀 단도")>=0
			 || txt.indexOf("저주받은 인형")>=0
			 || txt.indexOf("타격의 대가")>=0
			 || txt.indexOf("기습의 대가")>=0
			 || txt.indexOf("속전속결")>=0
			 || txt.indexOf("안정된 상태")>=0
			 || txt.indexOf("에테르 포식자")>=0
			 || txt.indexOf("바리케이드")>=0
			 || txt.indexOf("결투의 대가")>=0
			 || txt.indexOf("달인의 저력")>=0
					
					
			) {
				txt ="[D]"+txt;
			}else if(
				txt.indexOf("구슬동자")>=0
			 || txt.indexOf("전문의")>=0
			 || txt.indexOf("마나의 흐름")>=0
			 || txt.indexOf("각성")>=0
			 || txt.indexOf("중갑 착용")>=0
			 || txt.indexOf("최대 마나 증가")>=0
			 || txt.indexOf("정기 흡수")>=0
			 || txt.indexOf("급소 타격")>=0
			 //|| txt.indexOf("")>=0
			 
			){
				txt ="[S]"+txt;
			}else {
				txt ="[G]"+txt;
			}
			
			txt = txt.replaceAll(" 각인서","");
			
		}catch(Exception e) {
			txt="";
		}
	
		return txt;
	}
	public static String tier4accesorry(String txt) {
		try {
			txt=txt.replaceAll("치명타 적중률","치적");
			txt=txt.replaceAll("치명타 피해","치피");
			txt=txt.replaceAll("아군 공격력 강화 효과","아공강");
			txt=txt.replaceAll("아군 피해량 강화 효과","아피강");
			txt=txt.replaceAll("무기 공격력", "무공");
			txt=txt.replaceAll("공격력", "공");
			txt=txt.replaceAll("최대 생명력","최생");
			txt=txt.replaceAll("최대 마나","마나");
			txt=txt.replaceAll("상태이상 공격 지속시간","CC");
			txt=txt.replaceAll("전투 중 생명력 회복량","생회");
			txt=txt.replaceAll("적에게 주는 피해", "피증");
			txt=txt.replaceAll("추가 피해", "추피");
			txt=txt.replaceAll("세레나데, 신앙, 조화 게이지 획득량", "폿아덴");
			txt=txt.replaceAll("파티원 회복 효과","파티회복");
			txt=txt.replaceAll("파티원 보호막 효과","파티보호");
			
		}catch(Exception e) {
			txt="";
		}
		return txt;
	}
	
	public static String filterText(String txt) {
		try {
			txt=txt.replaceAll("  ", " ");
			txt=txt.replaceAll("\\[상급 재련\\] ", "");
			txt=txt.replaceAll("\\[초월\\]", "");
			txt=txt.replaceAll("\\[공용\\]", "");
			txt=txt.replaceAll("\\[하의\\]", "");
			txt=txt.replaceAll("\\[장갑\\]", "");
			txt=txt.replaceAll("\\[어깨\\]", "");
			txt=txt.replaceAll("\\[투구\\]", "");
			txt=txt.replaceAll("\\[상의\\]", "");
			txt=txt.replaceAll(" \\(혼돈\\)", "");
			txt=txt.replaceAll(" \\(질서\\)", "");
			txt=txt.replaceAll("Lv.", "");
			txt=txt.replaceAll("회피의 달인", "회달");
			txt=txt.replaceAll("탈출의 달인", "탈달");
			txt=txt.replaceAll("폭발물 달인", "폭달");
			txt=txt.replaceAll("생명의 축복", "생축");
			txt=txt.replaceAll("자원의 축복", "자축");
			txt=txt.replaceAll("최대 생명력", "최생");
			txt=txt.replaceAll("무기 공격력", "무공");
			txt=txt.replaceAll("보스 피해" , "보피");
			txt=txt.replaceAll("치명타 피해", "치피");
			txt=txt.replaceAll("공격력", "공격");
			txt=txt.replaceAll("무력화", "무력");
			txt=txt.replaceAll("보호막 강화", "보호강화");
			txt=txt.replaceAll("아군 강화", "아군강화");
			txt=txt.replaceAll("회복 강화", "회복강화");
			txt=txt.replaceAll("추가 피해", "추피");
			txt=txt.replaceAll("아이덴티티 획득", "아덴");
			txt=txt.replaceAll("받는 피해 감소", "받피감");
			txt=txt.replaceAll("물리 방어력", "물방");
			txt=txt.replaceAll("물약 중독", "물약중독");
			txt=txt.replaceAll("슬롯 효과", "");
			
		}catch(Exception e) {
			txt="";
		}
	
		return txt;
	}
	
	public static void setSSL() throws NoSuchAlgorithmException, KeyManagementException {
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

	public static String StringToDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateTime = dateFormat.format(new Date());
        
        return dateTime;
	}
	
	public static String StringTommorowDate() {
		LocalDate now = LocalDate.now();
		now = now.plusDays(1);
		String dateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return dateTime;
	}
}
