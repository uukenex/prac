package my.prac.core.util;

import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

public class LoaApiParser {

	static String [] setList = {"악몽","환각","지배","사멸","갈망","배신","파괴","구원","매혹"};
	static String [] elixirList = {"강맹","달인","신념","회심","선각자","선봉대","행운","진군","칼날방패"};
	static String [] braceletList = { "정밀", "멸시", "습격", "우월", "응원", "약점 노출", "비수", "냉정", "열정", "기습", "결투", "깨달음", "속공","분개","반격",
			"순환", "마나회수", "쐐기", "망치", "상처악화", "보상", "수확", "강타", "돌진", "타격", "오뚝이", "응급처치", "긴급수혈", "반전", "앵콜", "적립", "투자" };

	static String [] tier4GrindOptList = {"공격력","무기 공격력","최대 생명력","최대 마나","상태이상","전투 중","적에게","추가","세레나데","낙인력","파티원 보호막","파티원 회복",
			"아군 공격력","아군 피해량","치명타 적중","치명타 피해"}; 
	
	final static String enterStr= "♬";
	final static String spaceStr= "`";
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

		String elixirFind ="";
		
		try {
			element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_000");
			elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
			elixirFind = LoaApiUtils.filterText(elixirFind);
			totElixir += Integer.parseInt(elixirFind.replaceAll("[^1-5]", ""));
			
			for(String elixir:elixirList) {
				if(elixirFind.indexOf(elixir) >= 0) {
					equipElixirList.add(elixir);
				}
			}
			
		}catch(Exception e) {
			
		}
		
		try {
			element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_001");
			elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
			elixirFind = LoaApiUtils.filterText(elixirFind);
			totElixir += Integer.parseInt(elixirFind.replaceAll("[^1-5]", ""));
			
			for(String elixir:elixirList) {
				if(elixirFind.indexOf(elixir) >= 0) {
					equipElixirList.add(elixir);
				}
			}
		}catch(Exception e) {
			
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
		try {

			element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_000");
			elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
			elixirFind = LoaApiUtils.filterText(elixirFind);
			if(flag ==1) {//초월검색인 경우
				rtnTxt += elixirFind+" ";
			}else {
				rtnTxt += StringUtils.leftPad( elixirFind, 8, "　")+"　";
			}
		}catch(Exception e) {
			
		}
		
		try {
			element_009_value3 = (HashMap<String, Object>) element_009_value2.get("Element_001");
			elixirFind = Jsoup.parse((String) element_009_value3.get("contentStr").toString().split("<br>")[0]).text();
			elixirFind = LoaApiUtils.filterText(elixirFind);
			if(flag ==1) {//초월검색인 경우 
				rtnTxt += elixirFind+" ";
			}else {
				rtnTxt += StringUtils.leftPad( elixirFind, 8, "　")+"";
			}
		}catch(Exception e) {
			
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
		HashMap<String, Object> bracelet_element = new HashMap<>();
		HashMap<String, Object> stone_element = new HashMap<>();
		//연마
		HashMap<String, Object> grinding_element = new HashMap<>();
		//깨달음
		HashMap<String, Object> ark_passive_point_element = new HashMap<>();
		//3티어 효과
		HashMap<String, Object> tier3_stats = new HashMap<>();

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
			elixir_element = findElementDt(searchHs,"엘릭서");
			if(elixir_element.size()>0) {
				break;
			}
		}
		for(HashMap<String, Object> searchHs : elements) {
			bracelet_element = findElementDt(searchHs,"팔찌 효과");
			if(bracelet_element.size()>0) {
				break;
			}
		}
		for(HashMap<String, Object> searchHs : elements) {
			stone_element = findElementDt(searchHs,"무작위 각인 효과");
			if(stone_element.size()>0) {
				break;
			}
		}
		for (HashMap<String, Object> searchHs : elements) {
			grinding_element = findElementDt(searchHs, "연마 효과");
			if (grinding_element.size() > 0) {
				break;
			}
		}
		for (HashMap<String, Object> searchHs : elements) {
			ark_passive_point_element = findElementDt(searchHs, "아크 패시브 포인트 효과");
			if (ark_passive_point_element.size() > 0) {
				break;
			}
		}
		
		for (HashMap<String, Object> searchHs : elements) {
			tier3_stats = findElementDt(searchHs, "추가 효과");
			if (tier3_stats.size() > 0) {
				break;
			}
		}
		HashMap<String,Object> freshMap = new HashMap<>();
		freshMap.put("weapon_element", weapon_element);
		freshMap.put("quality_element", quality_element);
		freshMap.put("new_refine_element", new_refine_element);
		freshMap.put("limit_element", limit_element);
		freshMap.put("elixir_element", elixir_element);
		freshMap.put("bracelet_element", bracelet_element);
		freshMap.put("stone_element", stone_element);
		freshMap.put("grinding_element", grinding_element);
		freshMap.put("ark_passive_point_element", ark_passive_point_element);
		freshMap.put("tier3_stats", tier3_stats);
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

	public static String findBraceletOptions(int inputcase,String param) {
		String res ="";
		String[] arr = param.split("<img");
		
		if(inputcase==0) {
			for(String a : arr) {
				a = "<img"+a;
				String b = Jsoup.parse(a.replace("<BR>", spaceStr)).text();
				res += findBraceletOptionsDt( b );
			}
		}else if(inputcase==1) {
			for(String a : arr) {
				a = "<img"+a;
				String b = Jsoup.parse(a.replace("<BR>", spaceStr)).text();
				res += newFindTier4AccesorryOptionsDt( b );
			}
		}else if(inputcase==2){
			for(String a : arr) {
				a = "<img"+a;
				String b = Jsoup.parse(a.replace("<BR>", spaceStr)).text();
				res += newFindTier4AccesorryOptionsForTotal( b ,"D");
			}
		}else {
			for(String a : arr) {
				a = "<img"+a;
				String b = Jsoup.parse(a.replace("<BR>", spaceStr)).text();
				res += newFindTier4AccesorryOptionsForTotal( b ,"S");
			}
		}
		return res;
		
	}
	public static String newFindTier4AccesorryOptionsForTotal(String param,String position) {
		if(param==null || param.equals("")) {
			return "";
		}
		
		String msg = LoaApiUtils.tier4accesorry(param);
		
		String high_msg   = "상";
		String middle_msg = "중";
		String low_msg    = "하";

		if(position.equals("D")) {
			if (msg.indexOf("공") == 0) {
				if (param.indexOf("1.55%") >= 0) {
					return high_msg;
				} else if (param.indexOf("0.95%") >= 0) {
					return middle_msg;
				} else if(param.indexOf("0.40%")>= 0){
					return low_msg;
				}
			} else if (msg.indexOf("무공") == 0) {
				if (param.indexOf("3.00%") >= 0) {
					return high_msg;
				} else if (param.indexOf("1.80%") >= 0) {
					return middle_msg;
				}  else if(param.indexOf("0.80%")>= 0){
					return low_msg;
				}
			} else if (msg.indexOf("피증") == 0) {
				if (param.indexOf("2.00") >= 0) {
					return high_msg;
				} else if (param.indexOf("1.20") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			} else if (msg.indexOf("추피") == 0) {
				if (param.indexOf("2.60") >= 0) {
					return high_msg;
				} else if (param.indexOf("1.60") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			} else if (msg.indexOf("치적") == 0) {
				if (param.indexOf("1.55") >= 0) {
					return high_msg;
				} else if (param.indexOf("0.95") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			} else if (msg.indexOf("치피") == 0) {
				if (param.indexOf("4.00") >= 0) {
					return high_msg;
				} else if (param.indexOf("2.40") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			}
		}else {
			if (msg.indexOf("무공") == 0) {
				if (param.indexOf("3.00%") >= 0) {
					return high_msg;
				} else if (param.indexOf("1.80%") >= 0) {
					return middle_msg;
				} else if (param.indexOf("0.80%") >= 0) {
					return low_msg;
				}
			} else if (msg.indexOf("낙인력") == 0) {
				if (param.indexOf("8.00") >= 0) {
					return high_msg;
				} else if (param.indexOf("4.80") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			} else if (msg.indexOf("아공강") == 0) {
				if (param.indexOf("5.00") >= 0) {
					return high_msg;
				} else if (param.indexOf("3.00") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			} else if (msg.indexOf("아피강") == 0) {
				if (param.indexOf("7.50") >= 0) {
					return high_msg;
				} else if (param.indexOf("4.50") >= 0) {
					return middle_msg;
				} else {
					return low_msg;
				}
			}
		}
		
		
		
		return "";
	}
	
	public static String newFindTier4AccesorryOptionsDt(String param) {
		if(param==null || param.equals("")) {
			return "";
		}
		
		String msg = LoaApiUtils.tier4accesorry(param);
		
		String high_msg   = "∇"+"(상)"+msg + ""  + enterStr;
		String middle_msg = "∇"+"(중)"+msg + ""  + enterStr;
		String low_msg    = "∇"+"(하)"+msg + ""  + enterStr;

		if (msg.indexOf("공") == 0) {
			if (param.indexOf("390") >= 0 || param.indexOf("1.55%") >= 0) {
				return high_msg;
			} else if (param.indexOf("195") >= 0 || param.indexOf("0.95%") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("무공") == 0) {
			if (param.indexOf("960") >= 0 || param.indexOf("3.00%") >= 0) {
				return high_msg;
			} else if (param.indexOf("480") >= 0 || param.indexOf("1.80%") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("최생") == 0) {
			if (param.indexOf("6500") >= 0) {
				return high_msg;
			} else if (param.indexOf("3250") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("마나") == 0) {
			if (param.indexOf("30") >= 0) {
				return high_msg;
			} else if (param.indexOf("15") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("CC") == 0) {
			if (param.indexOf("1.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("0.50") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("생회") == 0) {
			if (param.indexOf("50") >= 0) {
				return high_msg;
			} else if (param.indexOf("25") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("피증") == 0) {
			if (param.indexOf("2.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("1.20") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("추피") == 0) {
			if (param.indexOf("2.60") >= 0) {
				return high_msg;
			} else if (param.indexOf("1.60") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("폿아덴") == 0) {
			if (param.indexOf("6.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("3.60") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("낙인력") == 0) {
			if (param.indexOf("8.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("4.80") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("파티") == 0) {
			if (param.indexOf("3.50") >= 0) {
				return high_msg;
			} else if (param.indexOf("2.10") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("아공강") == 0) {
			if (param.indexOf("5.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("3.00") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("아피강") == 0) {
			if (param.indexOf("7.50") >= 0) {
				return high_msg;
			} else if (param.indexOf("4.50") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("치적") == 0) {
			if (param.indexOf("1.55") >= 0) {
				return high_msg;
			} else if (param.indexOf("0.95") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		} else if (msg.indexOf("치피") == 0) {
			if (param.indexOf("4.00") >= 0) {
				return high_msg;
			} else if (param.indexOf("2.40") >= 0) {
				return middle_msg;
			} else {
				return low_msg;
			}
		}
		
		return "∇"+msg+enterStr;
	}
	
	public static String findTier4AccesorryOptionsDt(String param) {
		String msg = "";
		
		String high_msg   ="";
		String middle_msg ="";
		String low_msg 	  ="";

		if(param==null || param.equals("")) {
			return "";
		}
		
		for(int i=0;i<tier4GrindOptList.length;i++) {
			msg = tier4GrindOptList[i];
			
			if(param.indexOf(msg) == 0) {
				switch(msg) {
					case "공격력":
						msg = LoaApiUtils.tier4accesorry(msg);
						if(param.indexOf("%")>0) {
							high_msg   = "∇"+"(상)"+msg + "%"  + enterStr;
							middle_msg = "∇"+"(중)"+msg + "%"  + enterStr;
							low_msg    = "∇"+"(하)"+msg + "%"  + enterStr;
						}else {
							high_msg   = "∇"+"(상)"+msg + ""  + enterStr;
							middle_msg = "∇"+"(중)"+msg + ""  + enterStr;
							low_msg    = "∇"+"(하)"+msg + ""  + enterStr;
						}
						
						if( param.indexOf("390") >= 0 || param.indexOf("1.55%")>=0) {
							return high_msg;
						}else if( param.indexOf("195") >= 0 || param.indexOf("0.95%")>=0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "무기 공격력":
						msg = LoaApiUtils.tier4accesorry(msg);
						if(param.indexOf("%")>0) {
							high_msg   = "∇"+"(상)"+msg + "%"  + enterStr;
							middle_msg = "∇"+"(중)"+msg + "%"  + enterStr;
							low_msg    = "∇"+"(하)"+msg + "%"  + enterStr;
						}else {
							high_msg   = "∇"+"(상)"+msg + ""  + enterStr;
							middle_msg = "∇"+"(중)"+msg + ""  + enterStr;
							low_msg    = "∇"+"(하)"+msg + ""  + enterStr;
						}
						if( param.indexOf("960") >= 0 || param.indexOf("3.00%")>=0) {
							return high_msg;
						}else if( param.indexOf("480") >= 0 || param.indexOf("1.80%")>=0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "최대 생명력":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("6500") >= 0) {
							return high_msg;
						}else if( param.indexOf("3250") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "최대 마나":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("30") >= 0) {
							return high_msg;
						}else if( param.indexOf("15") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "상태이상":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("1.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("0.50") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "전투 중":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("50") >= 0) {
							return high_msg;
						}else if( param.indexOf("25") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "적에게":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("2.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("1.20") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "추가":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("2.60") >= 0) {
							return high_msg;
						}else if( param.indexOf("1.60") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "세레나데":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("6.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("3.60") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "낙인력":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("8.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("4.80") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "파티원 보호막":
					case "파티원 회복":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("3.50") >= 0) {
							return high_msg;
						}else if( param.indexOf("2.10") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "아군 공격력":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("5.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("3.00") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "아군 피해량":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("7.50") >= 0) {
							return high_msg;
						}else if( param.indexOf("4.50") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "치명타 적중":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("1.55") >= 0) {
							return high_msg;
						}else if( param.indexOf("0.95") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "치명타 피해":
						msg = LoaApiUtils.tier4accesorry(msg);
						high_msg   = "∇"+"(상)"+msg + enterStr;
						middle_msg = "∇"+"(중)"+msg + enterStr;
						low_msg    = "∇"+"(하)"+msg + enterStr;
						if( param.indexOf("4.00") >= 0) {
							return high_msg;
						}else if( param.indexOf("2.40") >= 0) {
							return middle_msg;
						}else {
							return low_msg;
						}
					default:
						msg = LoaApiUtils.tier4accesorry(msg);
					break;
				}
			}
		}
		
		return "∇"+param;
		
	}
	public static String findBraceletOptionsDt(String param) {
		String msg = "";
		
		String high_msg   ="";
		String middle_msg ="";
		String low_msg 	  ="";

		if(param==null || param.equals("")) {
			return "";
		}
		
		for(int i=0;i<braceletList.length;i++) {
			msg = "["+braceletList[i]+"]";
			
			high_msg   = "∇"+"(상)"+msg  + enterStr;
			middle_msg = "∇"+"(중)"+msg  + enterStr;
			low_msg    = "∇"+"(하)"+msg  + enterStr;
			
			if(param.indexOf(msg) >= 0) {
				switch(msg) {
					case "[분개]":
						if( param.indexOf("23%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("20%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
					case "[망치]":
						if( param.indexOf("12%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("10%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
					
					case "[쐐기]":
						if( param.indexOf("0.5%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("0.45%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
					
					case "[마나회수]":
						if( param.indexOf("200") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("175") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
					
					case "[속공]":
						if( param.indexOf("15%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("12%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[깨달음]":
						if( param.indexOf("6%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("5%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[냉정]":
					case "[열정]":
					case "[기습]":
					case "[결투]":
					case "[순환]":
						if( param.indexOf("4%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("3.5%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[약점 노출]":
					case "[비수]":
						if( param.indexOf("2.5%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("2.1%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[응원]":
						if( param.indexOf("1.3%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("1.1%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[우월]":
						if( param.indexOf("3%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("2.5%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[습격]":
						if( param.indexOf("10%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("8%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[정밀]":
					case "[멸시]":
						if( param.indexOf("5%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("4%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[오뚝이]":
					case "[타격]":
						if( param.indexOf("200") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("160") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[돌진]":
					case "[강타]":
						if( param.indexOf("100") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("80") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[수확]":
						if( param.indexOf("250") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("220") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[보상]":
						if( param.indexOf("7") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("6") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					
					case "[상처악화]":
						if( param.indexOf("7%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("5%") >= 0 ) {
							return middle_msg;
						}else { 
							return low_msg; 
						}
					case "[응급처치]":
						if( param.indexOf("8000") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("6500") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					case "[긴급수혈]":
						if( param.indexOf("16000") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("13000") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					case "[반격]":
					case "[투자]":
						if( param.indexOf("2200") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("1600") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}	
					case "[반전]":
					case "[앵콜]":	
						if( param.indexOf("25%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("20%") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
					
					case "[적립]":
						if( param.indexOf("50%") >= 0 ) {
							return high_msg;
						}else if( param.indexOf("40") >= 0 ) {
							return middle_msg;
						}else {
							return low_msg;
						}
						
					
					default:
					break;
				}
			}
		}
		
		return "∇"+param;
	}
	
	
	public static HashMap<String,Object> engraveSelector(String name,String grade,String lv) {
		
		String colName="";
		int realLv=0;
		
		switch(name) {
			case "아드레날린":
				colName = "ENG01";
				break;
			case "원한":
				colName = "ENG02";
				break;
			case "예리한 둔기":
				colName = "ENG03";
				break;
			case "저주받은 인형":
				colName = "ENG04";
				break;
			case "돌격대장":
				colName = "ENG05";
				break;
			case "타격의 대가":
				colName = "ENG06";
				break;
			case "기습의 대가":
				colName = "ENG07";
				break;
			case "질량 증가":
				colName = "ENG08";
				break;
			case "슈퍼 차지":
				colName = "ENG09";
				break;
			case "결투의 대가":
				colName = "ENG10";
				break;
			case "속전속결":
				colName = "ENG11";
				break;
			case "바리케이드":
				colName = "ENG12";
				break;
			case "마나 효율 증가":
				colName = "ENG13";
				break;
			case "정밀 단도":
				colName = "ENG14";
				break;
			case "에테르 포식자":
				colName = "ENG15";
				break;
			case "안정된 상태":
				colName = "ENG16";
				break;
			case "마나의 흐름":
				colName = "ENG17";
				break;
			case "구슬동자":
				colName = "ENG18";
				break;
			case "전문의":
				colName = "ENG19";
				break;
			case "각성":
				colName = "ENG20";
				break;
			case "중갑 착용":
				colName = "ENG21";
				break;
			case "급소 타격":
				colName = "ENG22";
				break;
			default:
				break;
		}
		
		/** ex) 
		 * 유물4 = 19
		 * 유물3 = 18
		 * 유물2 = 17
		 * 유물1 = 16
		 * 유물0 = 15
		 * 
		 * 전설4 = 14
		 * 전설3 = 13
		 * 전설2 = 12
		 * 전설1 = 11
		 * 전설0 = 10
		 */
		switch(grade) {
			case "유물":
				realLv=15;
				break;
			case "전설":
				realLv=10;
				break;
			case "영웅":
				realLv=5;
				break;
		}
		
		switch(lv) {
			case "0":
				break;
			case "1":
				realLv = realLv+1;
				break;
			case "2":
				realLv = realLv+2;
				break;
			case "3":
				realLv = realLv+3;
				break;
			case "4":
				realLv = realLv+4;
				break;
		}
		
		HashMap<String,Object> engMap = new HashMap<>();
		engMap.put("colName", colName);
		engMap.put("realLv", realLv);
		
		return engMap;
	}
	
}
