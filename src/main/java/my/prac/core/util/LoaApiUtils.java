package my.prac.core.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LoaApiUtils {

	final static String lostArkKey = "bearer "+PropsUtil.getProperty("keys","loaKey");
	
	public static String connect_process(String paramUrl) throws Exception {
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
	
	public static String filterTextForElixir(String txt) {
		txt=txt.replaceAll(" ", "");
		txt=txt.replaceAll("\\[공용\\]", "");
		txt=txt.replaceAll("\\[하의\\]", "");
		txt=txt.replaceAll("\\[장갑\\]", "");
		txt=txt.replaceAll("\\[어깨\\]", "");
		txt=txt.replaceAll("\\[투구\\]", "");
		txt=txt.replaceAll("\\[상의\\]", "");
		txt=txt.replaceAll("\\(혼돈\\)", "");
		txt=txt.replaceAll("\\(질서\\)", "");
		txt=txt.replaceAll("Lv.", "");
		txt=txt.replaceAll("회피의달인", "회달");
		txt=txt.replaceAll("탈출의달인", "탈달");
		txt=txt.replaceAll("폭발물달인", "폭달");
		txt=txt.replaceAll("생명의축복", "생축");
		txt=txt.replaceAll("자원의축복", "자축");
		txt=txt.replaceAll("최대생명력", "최생");
		txt=txt.replaceAll("무기공격력", "무공");
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
}
