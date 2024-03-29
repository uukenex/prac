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

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

public class LoaApiParser {

	static String [] setList = {"악몽","환각","지배","사멸","갈망","배신","파괴","구원","매혹"};
	static String [] elixirList = {"강맹","달인","신념","회심","선각자","선봉대","행운","진군","칼날방패"};

	final static String enterStr= "♬";
	final static String tabStr= "◐";
	final static String allSeeStr = "===";
	
	public static String[] getSetList() {
		return setList;
	}
	public static String[] getElixirList() {
		return elixirList;
	}
	
	/** 초월 파싱 (장비검색)
	 * element에 초월에 해당하는 element를 넣어주면 된다. 
	 * 성공시 초월합(String)
	 * 실패시 E0002
	 * */
	
	public static String parseLimit(HashMap<String, Object> element) throws Exception {
		HashMap<String, Object> element_008;
		HashMap<String, Object> element_008_value;
		HashMap<String, Object> element_008_value1;
		HashMap<String, Object> element_008_value2;
		HashMap<String, Object> element_008_value3;
		
		element_008 = element;
		if(element_008.toString().indexOf("[초월]") < 0) {
			//초월이 되지 않음
			return "";
		}
		element_008_value = (HashMap<String, Object>) element_008.get("value");
		element_008_value1 = (HashMap<String, Object>) element_008_value.get("Element_000");
		element_008_value2 = (HashMap<String, Object>) element_008_value1.get("contentStr");
		element_008_value3 = (HashMap<String, Object>) element_008_value2.get("Element_001");

		return Jsoup.parse((String) element_008_value3.get("contentStr")).text().replaceAll("[^0-9]", "");
		
	}

	/** 엘릭서 파싱 (장비검색) 
	 * element에 엘릭서에 해당하는 element를 넣어주면 된다.
	 * 성공시 엘릭서합(int), equipElixirList 장비된 엘릭서 List add
	 * */
	public static int parseElixirForEquip(List<String> equipElixirList, HashMap<String, Object> element) throws Exception {
		int totElixir =0;
		
		HashMap<String, Object> element_009;
		HashMap<String, Object> element_009_value;
		HashMap<String, Object> element_009_value1;
		HashMap<String, Object> element_009_value2;
		HashMap<String, Object> element_009_value3;
		
		element_009 = element;
		if(element_009.toString().indexOf("지혜의 엘릭서") < 0) {
			//엘릭서 되지 않음
			return 0;
		}
		element_009_value = (HashMap<String, Object>) element_009.get("value");
		element_009_value1 = (HashMap<String, Object>) element_009_value.get("Element_000");
		element_009_value2 = (HashMap<String, Object>) element_009_value1.get("contentStr");

		String elixirFind;
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_000");
		elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixirFind = LoaApiUtils.filterText(elixirFind);
		totElixir += Integer.parseInt(elixirFind.replaceAll("[^1-5]", ""));
		
		for(String elixir:elixirList) {
			if(elixirFind.indexOf(elixir) >= 0) {
				equipElixirList.add(elixir);
			}
		}
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_001");
		elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixirFind = LoaApiUtils.filterText(elixirFind);
		totElixir += Integer.parseInt(elixirFind.replaceAll("[^1-5]", ""));
		
		for(String elixir:elixirList) {
			if(elixirFind.indexOf(elixir) >= 0) {
				equipElixirList.add(elixir);
			}
		}
		
		return totElixir;
	}
	
	/** 초월 파싱 (초월검색)
	 * element에 초월에 해당하는 element를 넣어주면 된다. 
	 * 성공시 초월상세텍스트(String)
	 * */
	public static String parseLimitForLimit(HashMap<String, Object> element) throws Exception {
		HashMap<String, Object> element_008;
		HashMap<String, Object> element_008_value;
		HashMap<String, Object> element_008_value1;
		
		element_008 = element;
		if(element_008.toString().indexOf("[초월]") < 0) {
			//초월이 되지 않음
			return "";
		}
		element_008_value = (HashMap<String, Object>) element_008.get("value");
		element_008_value1 = (HashMap<String, Object>) element_008_value.get("Element_000");
		return Jsoup.parse((String) element_008_value1.get("topStr")).text();
		
	}
	
	/** 엘릭서 파싱 (초월검색)
	 * element에 초월에 해당하는 element를 넣어주면 된다.
	 * 성공시 엘릭서상세텍스트(String) 
	 * */
	public static String parseElixirForLimit(List<String> equipElixirList, HashMap<String, Object> element, int flag) throws Exception {
		String rtnTxt="";
		
		HashMap<String, Object> element_009;
		HashMap<String, Object> element_009_value;
		HashMap<String, Object> element_009_value1;
		HashMap<String, Object> element_009_value2;
		HashMap<String, Object> element_009_value3;
		
		element_009 = element;
		
		if(element_009.toString().indexOf("지혜의 엘릭서") < 0) {
			//엘릭서 되지 않음
			return "";
		}
		
		element_009_value = (HashMap<String, Object>) element_009.get("value");
		element_009_value1 = (HashMap<String, Object>) element_009_value.get("Element_000");
		element_009_value2 = (HashMap<String, Object>) element_009_value1.get("contentStr");

		String elixirFind;
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_000");
		elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixirFind = LoaApiUtils.filterText(elixirFind);
		if(flag ==1) {//초월검색인 경우
			rtnTxt += elixirFind+" ";
		}else {
			rtnTxt += StringUtils.leftPad( elixirFind, 8, "　")+"　";
		}
		
		
		element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_001");
		elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
		elixirFind = LoaApiUtils.filterText(elixirFind);
		if(flag ==1) {//초월검색인 경우 
			rtnTxt += elixirFind+" ";
		}else {
			rtnTxt += StringUtils.leftPad( elixirFind, 8, "　")+"";
		}
		
		
		return rtnTxt;
	}
	
	
	public static HashMap<String, Object> findElement(HashMap<String, Object> tooltip) {
		
		HashMap<String, Object> elements[] = new HashMap[13]; 
		elements[0] = (HashMap<String, Object>) tooltip.get("Element_000"); 
		elements[1] = (HashMap<String, Object>) tooltip.get("Element_001"); 
		elements[2] = (HashMap<String, Object>) tooltip.get("Element_002"); 
		elements[3] = (HashMap<String, Object>) tooltip.get("Element_003"); 
		elements[4] = (HashMap<String, Object>) tooltip.get("Element_004"); 
		elements[5] = (HashMap<String, Object>) tooltip.get("Element_005"); 
		elements[6] = (HashMap<String, Object>) tooltip.get("Element_006"); 
		elements[7] = (HashMap<String, Object>) tooltip.get("Element_007"); 
		elements[8] = (HashMap<String, Object>) tooltip.get("Element_008"); 
		elements[9] = (HashMap<String, Object>) tooltip.get("Element_009"); 
		elements[10] = (HashMap<String, Object>) tooltip.get("Element_010"); 
		elements[11] = (HashMap<String, Object>) tooltip.get("Element_011"); 
		elements[12] = (HashMap<String, Object>) tooltip.get("Element_012"); 
		
		HashMap<String, Object> weapon_element = elements[0];
		HashMap<String, Object> quality_element = new HashMap<>();
		HashMap<String, Object> new_refine_element = new HashMap<>();
		HashMap<String, Object> limit_element = new HashMap<>();
		HashMap<String, Object> elixir_element = new HashMap<>();
		
		for(HashMap<String, Object> searchHs : elements) {
			quality_element = findElementDt(searchHs,"qualityValue");
			if(quality_element.size()>0) {
				break;
			}
		}
		for(HashMap<String, Object> searchHs : elements) {
			new_refine_element = findElementDt(searchHs,"상급 재련");
			if(new_refine_element.size()>0) {
				break;
			}
		}
		for(HashMap<String, Object> searchHs : elements) {
			limit_element = findElementDt(searchHs,"[초월]");
			if(limit_element.size()>0) {
				break;
			}
		}
		for(HashMap<String, Object> searchHs : elements) {
			elixir_element = findElementDt(searchHs,"엘릭서 효과");
			if(elixir_element.size()>0) {
				break;
			}
		}
		
		HashMap<String,Object> freshMap = new HashMap<>();
		freshMap.put("weapon_element", weapon_element);
		freshMap.put("quality_element", quality_element);
		freshMap.put("new_refine_element", new_refine_element);
		freshMap.put("limit_element", limit_element);
		freshMap.put("elixir_element", elixir_element);
		return freshMap;
	}
	
	public static HashMap<String, Object> findElementDt(HashMap<String, Object> element,String keyword) {
		HashMap<String, Object> findElement = new HashMap<>();
		try {
			if(element.toString().indexOf(keyword)>=0) {
				findElement = element;
			}
		}catch(Exception e) {
		}
		
		return findElement;
	}

}
