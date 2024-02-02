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

public class LoaApiParser {

	static String [] setList = {"악몽","환각","지배","사멸","갈망","배신","파괴","구원","매혹"};
	static String [] elixirList = {"강맹","달인","신념","회심","선각자","선봉대","행운","진군","칼날방패"};

	public static String[] getSetList() {
		return setList;
	}
	public static String[] getElixirList() {
		return elixirList;
	}
	
	/** 초월 파싱 
	 * element에 초월에 해당하는 element를 넣어주면 된다. 
	 * 성공시 초월합(String)
	 * 실패시 E0002
	 * */
	
	public static String parseLimitForEquip(HashMap<String, Object> element) throws Exception {
		HashMap<String, Object> element_008;
		HashMap<String, Object> element_008_value;
		HashMap<String, Object> element_008_value1;
		HashMap<String, Object> element_008_value2;
		HashMap<String, Object> element_008_value3;
		
		element_008 = element;
		if(element_008.toString().indexOf("초월") < 0) {
			//초월이 되지 않음
			throw new Exception("E0002");
		}
		element_008_value = (HashMap<String, Object>) element_008.get("value");
		element_008_value1 = (HashMap<String, Object>) element_008_value.get("Element_000");
		element_008_value2 = (HashMap<String, Object>) element_008_value1.get("contentStr");
		element_008_value3 = (HashMap<String, Object>) element_008_value2.get("Element_001");

		return Jsoup.parse((String) element_008_value3.get("contentStr")).text().replaceAll("[^0-9]", "");
		
	}

	
	public static int parseElixirForEquip(List<String> equipElixirList, HashMap<String, Object> element) throws Exception {
		int totElixir =0;
		
		HashMap<String, Object> element_009;
		HashMap<String, Object> element_009_value;
		HashMap<String, Object> element_009_value1;
		HashMap<String, Object> element_009_value2;
		HashMap<String, Object> element_009_value3;
		
		element_009 = element;
		element_009_value = (HashMap<String, Object>) element_009.get("value");
		element_009_value1 = (HashMap<String, Object>) element_009_value.get("Element_000");
		element_009_value2 = (HashMap<String, Object>) element_009_value1.get("contentStr");

		String elixerFind;
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_000");
		elixerFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixerFind = LoaApiUtils.filterTextForElixir(elixerFind);
		totElixir += Integer.parseInt(elixerFind.replaceAll("[^1-5]", ""));
		
		for(String elixir:elixirList) {
			if(elixerFind.indexOf(elixir) >= 0) {
				equipElixirList.add(elixir);
			}
		}
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_001");
		elixerFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixerFind = LoaApiUtils.filterTextForElixir(elixerFind);
		totElixir += Integer.parseInt(elixerFind.replaceAll("[^1-5]", ""));
		
		for(String elixir:elixirList) {
			if(elixerFind.indexOf(elixir) >= 0) {
				equipElixirList.add(elixir);
			}
		}
		
		return totElixir;
	}

}
