package my.prac.api.loa.controller;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.ImageUtils;
import my.prac.core.util.LoaApiParser;
import my.prac.core.util.LoaApiUtils;


@Controller
public class LoaChatController {
	static Logger logger = LoggerFactory.getLogger(LoaChatController.class);

	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
	// final String lostArkAPIurl =
	// "https://developer-lostark.game.onstove.com/armories/characters/일어난다람쥐/equipment";
	final String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	final String enterStr= "♬";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	
	@RequestMapping(value = "/loa/chat", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> chatApplication(
			@RequestParam(required = true)  String param0,
			@RequestParam(required = false) String param1,
			@RequestParam(required = false) String param2,
			@RequestParam(required = false) String room,
			@RequestParam(required = false) String sender,
			@RequestParam(required = false) String fulltxt
			) {
		

		try {
			System.out.println(param0 + " " + param1+ " " + param2+ " " + room+ " " + sender);
			System.out.println("fulltxt: "+fulltxt);
			String val = autoResponse(param0,param1,param2,room,sender,fulltxt);
			
			if(val!=null&&!val.equals("")) {
				HashMap<String, Object> rtnMap = new HashMap<>();
				rtnMap.put("data", val);
				return rtnMap;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	@RequestMapping(value = "/i/{imgvalues}", method = RequestMethod.GET)
	public String wimgReturn(@PathVariable String imgvalues, Model model) {
		model.addAttribute("imgval",imgvalues);
		return "rtnimgs2";
	}
	
	//roomName은 https://cafe.naver.com/msgbot/2067 수정본 참조
	String autoResponse(String param0,String param1,String param2,String roomName,String sender,String fulltxt) throws Exception {
		String val="";
		HashMap<String,Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("roomName", roomName);
		reqMap.put("userName", sender);
		
		if(param0.startsWith("[")) {
			return emotionMsg(param0,roomName);
		}else if(param0.startsWith("/")) {
			return commandMsg(param0,param1,param2,roomName,sender,fulltxt);
		}
		return val;
	}

	String commandMsg(String param0, String param1, String param2, String roomName, String sender, String fulltxt)
			throws Exception {
		String val = "";
		String list;
		HashMap<String, Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("param1", param1);
		reqMap.put("roomName", roomName);
		reqMap.put("userName", sender);

		switch (param0) {
		case "/골드":
		case "/ㄱㄷ":
			val = checkGoldList();
			break;
		case "/모험섬":
		case "/ㅁㅎㅅ":	
			val = calendarSearch();
			break;
			
		case "/장비":
		case "/ㅈㅂ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = equipmentSearch(param1);
					
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/초월": case "/엘릭서":
		case "/ㅊㅇ": case "/ㅇㄹㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = limitSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/내실":
		case "/ㄴㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = collectionSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/ㄱㅁㅈ":
		case "/경매장":
			val = marketSearch();
			break;
		case "/레이드":
		case "/ㄹㅇㄷ":
			if (param1 != null && !param1.equals("")) {
				try {
					raidAdd(reqMap);
					val = raidSearch(reqMap);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}else {
				val = raidSearch(reqMap);
			}
			break;	
		case "/부캐":
		case "/ㅂㅋ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = subCharacterSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;	
		case "/항협": case "/항해": case "/항해협동":
			val = shipSearch();
			break;
		case "/날씨":
			if (param1 != null && !param1.equals("")) {
				val = weatherSearch(param1);
			}
			break;
		case "/저메추":
			String[] menu_list = { "피자", "탕수육", "치킨", "샐러드", "마라탕", "양꼬치", "삼겹살", "설렁탕", "김치찌개", "된장찌개", "삼치튀김", "참치마요",
					"회", "육회비빔밥", "냉면", "카레", "돈까스", "제육볶음", "오징어볶음", "떡볶이", "굶기", "초밥", "햄버거", "짜장면", "빵", "파스타", "닭발",
					"쭈꾸미", "낙지덮밥", "라면", "짜계치", "스팸과 흰밥", "간장계란밥", "간장게장", "참치회", "죽", "흰밥", "감자탕" };
			Random random = new Random();
			val = menu_list[random.nextInt(menu_list.length)];

			break;
		case "/단어등록": case "/단어추가":

			try {
				if (fulltxt.indexOf("=") < 0) {
					val = "단어등록 실패!, =을 포함해주세요";
				} else if (fulltxt.indexOf(">") >= 0 || fulltxt.indexOf("<") >= 0) {
					val = "단어등록 실패!, 특수문자 안되요!";
				} else {
					String[] txtList;
					fulltxt = fulltxt.substring(param0.length()).trim();
					txtList = fulltxt.split("=");
					reqMap.put("req", txtList[0]);
					reqMap.put("res", txtList[1]);

					botService.insertBotWordSaveTx(reqMap);
					val = "단어등록 완료!";
				}
			} catch (Exception e) {
				val = "단어등록 실패!";
			}
			break;
		case "/단어제거": case "/단어삭제":
			try {
				if (fulltxt.indexOf("=") < 0) {
					val = "단어삭제 실패!, =을 포함해주세요";
				} else if (fulltxt.indexOf(">") >= 0 || fulltxt.indexOf("<") >= 0) {
					val = "단어삭제 실패!, 특수문자 안되요!";
				} else {
					String[] txtList;
					fulltxt = fulltxt.substring(param0.length()).trim();
					txtList = fulltxt.split("=");
					reqMap.put("req", txtList[0]);
					reqMap.put("res", txtList[1]);

					int masterYn = botService.selectBotWordSaveMasterCnt(reqMap);

					if (masterYn > 0) {
						botService.deleteBotWordSaveMasterTx(reqMap);
					} else {
						botService.deleteBotWordSaveTx(reqMap);
					}

					val = "단어삭제 완료!";
				}
			} catch (Exception e) {
				val = "단어삭제 실패!";
			}
			break;
		case "/단어목록": case "/단어조회": case "/단어":
			List<String> wordList = botService.selectBotWordSaveAll(reqMap);
			List<String> imgList = botService.selectBotImgSaveAll(reqMap);
			
			reqMap.put("limitYn", "1");
			List<String> limitWordList = botService.selectBotLimitWordSaveAll(reqMap);
			reqMap.put("limitYn", "2");
			List<String> limitWordList2 = botService.selectBotLimitWordSaveAll(reqMap);
			
			
			val = "주요명령(초성가능): "+enterStr;
			
			for (String word : limitWordList2) {
				val += enterStr + word;
			}
			
			val += enterStr + enterStr + "명령 더보기..▼ "+ allSeeStr;
			for (String word : limitWordList) {
				val += word + enterStr;
			}
			val += enterStr + "단어목록:" + enterStr;
			for (String word : wordList) {
				val += word + enterStr;
			}
			val += enterStr + "이모티콘목록:" + enterStr;
			for (String word : imgList) {
				val += word + enterStr;
			}
			
			
			break;
		default:
			val = botService.selectBotWordSaveOne(reqMap);
			break;
		}

		return val;
	}

	String emotionMsg(String param0,String roomName) throws Exception {
		String val = "";
		String randKey = "";
		boolean imgcp=false;
		
		//selectBotImgMch param0 , roomName selectBotImgSaveAll
		HashMap<String,Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("roomName", roomName);
		
		param0=botService.selectBotImgMch(reqMap);
		if(param0 ==null || param0.equals("")) {
			return val;
		}
		
		randKey = botService.selectBotImgSaveOne(param0);
		if (randKey == null || randKey.equals("")) {
			randKey = ImageUtils.RandomAlphaNum();
			String orgFilePath = "/img/img_loa/"    + param0  + ".png";
			String outFilePath = "/img/img_loa_cp/" + randKey + ".png";
			imgcp = ImageUtils.nioCopy(orgFilePath, outFilePath);
			if(imgcp) {
				HashMap<String,Object> hs = new HashMap<>();
				hs.put("asis", param0);
				hs.put("tobe", randKey);
				botService.insertBotImgSaveOneTx(hs);
			}else {
				System.out.println("이미지 저장 실패로 인한 리턴값없음");
				return val;
			}
		}

		val = "imgwww.dev-apc.com/i/" + randKey;

		return val;
	}

	String errorCodeMng(Exception e) {
		String val="";
		if(e != null && e.getMessage()!=null) {
			switch(e.getMessage()) {
			case "E0001":
				val = "레벨이 1630 보다 낮습니다";
				break;
			case "E0002":
				val = "초월 검색 오류";
				break;
			case "E0003":
				val = "캐릭터명 검색 오류";
				break;
			default:
				val = "ID오류이거나 엘릭서/초월이 모두있어야 검색가능합니다";
				e.printStackTrace();
				break;
			}
		}else {
			val = "ID오류이거나 엘릭서/초월이 모두있어야 검색가능합니다";
			e.printStackTrace();
		}
		return val;
	}
	
	String shipSearch() throws Exception {
		String retMsg="오늘의 항협";
		String retMsg1="";
		String retMsg2="";
		String retMsg3="";
		
		String paramUrl = lostArkAPIurl + "/gamecontents/calendar";
		
		String returnData = LoaApiUtils.connect_process(paramUrl);
		List<Map<String, Object>> data_list = new ObjectMapper().readValue(returnData, new TypeReference<List<Map<String, Object>>>() {});
		
		for(Map<String,Object> data_sub_list : data_list) {
			if(data_sub_list.get("CategoryName").equals("항해")) {
				if(data_sub_list.get("StartTimes")!=null) {
					List<String> start_time_list = (List<String>)data_sub_list.get("StartTimes");
					for(String time : start_time_list) {
						if(time.equals(LoaApiUtils.StringToDate()+"T19:30:00")) {
							retMsg1 = retMsg1 +data_sub_list.get("ContentsName").toString()+" ";
						}
						if(time.equals(LoaApiUtils.StringToDate()+"T21:30:00")) {
							retMsg2 = retMsg2 +data_sub_list.get("ContentsName").toString()+" ";
						}
						if(time.equals(LoaApiUtils.StringToDate()+"T23:30:00")) {
							retMsg3 = retMsg3 +data_sub_list.get("ContentsName").toString()+" ";
						}
					}
				}
			}
		}
		
		retMsg1 = enterStr+"[19:30] "+ retMsg1;
		retMsg2 = enterStr+"[21:30] "+ retMsg2;
		retMsg3 = enterStr+"[23:30] "+ retMsg3;
		
		retMsg = retMsg+retMsg1+retMsg2+retMsg3;
		retMsg = retMsg.replaceAll("항해 협동 : ", "");
		
		return retMsg;
	}
	
	String checkGoldList() throws Exception {
		String msg = "";
		msg += "골드획득정보..";
		msg += enterStr;
		msg += enterStr + "◆카제로스 레이드◆";
		msg += enterStr + "에키드나[하 18,500G/노 14,500G]";
		msg += enterStr;
		msg += enterStr + "◆군단장 레이드◆";
		msg += enterStr + "카멘 1-3[하 20,000G/노 13,000G]";
		msg += enterStr + "카멘 1-2[하 11,000G/노 7,500G]";
		msg += enterStr + "일리아칸 [하 10,000G/노 7,500G]";
		msg += enterStr + "아브렐슈드 1-4 [하 9,000G/노 7,000G]";
		msg += enterStr + "아브렐슈드 1-3 [하 6,000G/노 4,500G]";
		msg += enterStr + "아브렐슈드 하12노3 [5,500G]";
		msg += enterStr;
		msg += enterStr + "◆어비스 던전◆";
		msg += enterStr + "상아탑 [하 14,500G/노 9,000G]";
		msg += enterStr + "카양겔 [하 6,500G/노 4,500G]";
		msg += enterStr;
		msg += enterStr + "관문별 더보기..▼ ";
		msg += enterStr + allSeeStr;
		msg += enterStr;
		msg += enterStr + "하키드나 1관문  6,000G";
		msg += enterStr + "하키드나 2관문 12,500G";
		msg += enterStr;
		msg += enterStr + "노키드나 1관문  5,000G ";
		msg += enterStr + "노키드나 2관문  9,500G";
		msg += enterStr;
		msg += enterStr + "하멘 1관문  5,000G";
		msg += enterStr + "하멘 2관문  6,000G";
		msg += enterStr + "하멘 3관문  9,000G";
		msg += enterStr + "하멘 4관문 21,000G";
		msg += enterStr;
		msg += enterStr + "노멘 1관문  3,500G";
		msg += enterStr + "노멘 2관문  4,000G";
		msg += enterStr + "노멘 3관문  5,500G";
		msg += enterStr;

		return msg;
	}
	
	String calendarSearch() throws Exception {
		String retMsg="오늘의 모험 섬";
		String retMsg1="";
		String retMsg2="";
		String retMsg3="";
		
		List<String> ret1Arr = new ArrayList<>();
		List<String> ret2Arr = new ArrayList<>();
		String today = LoaApiUtils.StringToDate();
		//String today = "2024-02-16";
				
		int cnt = 0; 
		String paramUrl = lostArkAPIurl + "/gamecontents/calendar";
		
		String returnData = LoaApiUtils.connect_process(paramUrl);
		List<Map<String, Object>> data_list = new ObjectMapper().readValue(returnData, new TypeReference<List<Map<String, Object>>>() {
		});
		
		for(Map<String,Object> data_sub_list : data_list) {
			if(!data_sub_list.get("CategoryName").equals("모험 섬")) {
				continue;
			}
			
			if(data_sub_list.get("CategoryName").equals("모험 섬")) {
				data_sub_list.remove("CategoryName");
				data_sub_list.remove("ContentsIcon");
				data_sub_list.remove("MinItemLevel");
				data_sub_list.remove("Location");
				
				String times = Jsoup.parse((String) data_sub_list.get("StartTimes").toString()).text();
				if(times.indexOf(today) < 0) {
					continue;
				}
				
				List<Map<String, Object>> rewardItemsList = (List<Map<String, Object>>)data_sub_list.get("RewardItems");
				for(Map<String, Object> rewardItem : rewardItemsList) {
					rewardItem.remove("Icon");
					rewardItem.remove("Location");
					rewardItem.remove("Grade");
					
					
					if(rewardItem.get("StartTimes")!=null) {
						List<String> start_time_list = (List<String>)rewardItem.get("StartTimes");
						for(String time : start_time_list) {
							if(time.equals(today+"T09:00:00")) {
								switch(rewardItem.get("Name").toString()) {
									case "전설 ~ 고급 카드 팩 III":
										retMsg1 = retMsg1 + enterStr;
										retMsg1 = retMsg1 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg1+="카드";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "실링":
										retMsg1 = retMsg1 + enterStr;
										retMsg1 = retMsg1 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg1+="실링";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "대양의 주화 상자":
										retMsg1 = retMsg1 + enterStr;
										retMsg1 = retMsg1 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg1+="주화";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "골드":
										retMsg1 = retMsg1 + enterStr;
										retMsg1 = retMsg1 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg1+="♣골드♣";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									default:
										continue;
								}
							}
							else if(time.equals(today+"T19:00:00")) {
								switch(rewardItem.get("Name").toString()) {
									case "전설 ~ 고급 카드 팩 III":
										retMsg2 = retMsg2 + enterStr;
										retMsg2 = retMsg2 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg2+="카드";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "실링":
										retMsg2 = retMsg2 + enterStr;
										retMsg2 = retMsg2 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg2+="실링";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "대양의 주화 상자":
										retMsg2 = retMsg2 + enterStr;
										retMsg2 = retMsg2 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg2+="주화";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									case "골드":
										retMsg2 = retMsg2 + enterStr;
										retMsg2 = retMsg2 + data_sub_list.get("ContentsName").toString()+" : ";
										retMsg2+="♣골드♣";
										ret1Arr.add(data_sub_list.get("ContentsName").toString());
										cnt++;
										break;
									default:
										continue;
								}
							}
							
						}
					}
					
				}
				
				ret2Arr.add(data_sub_list.get("ContentsName").toString());
				
			}
		}
		
		for(String not_found : ret1Arr) {
			ret2Arr.remove(not_found);
		}
		
		
		if(cnt>=4) {
			retMsg1 = enterStr+"☆(오전)"+ retMsg1;
			retMsg2 = enterStr+"★(오후)"+ retMsg2;
			if(cnt < 6 ) {
				
				for(String not_found : ret2Arr) {
					retMsg3 += not_found+" ";
				}
				/*
				for(String not_found : retArr) {
					System.out.println(not_found);
					retMsg3 += not_found;
				}
				*/
				retMsg3 = enterStr+enterStr+"§API정보없음 : "+ retMsg3;
			}
			
		}
		
		retMsg = retMsg+retMsg1+retMsg2+retMsg3;

		return retMsg;
	}

	String limitSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		List<Map<String, Object>> armoryEquipment;
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
			throw new Exception("E0003");
		}
		
		String [] elixirList = LoaApiParser.getElixirList();
		List<String> equipElixirList = new ArrayList<>();
		
		String resMsg = ordUserId+" 초월정보";

		String resEquip = "";
		String totLimit ="";
		int totElixir =0;
		
		for (Map<String, Object> equip : armoryEquipment) {
			switch (equip.get("Type").toString()) {
			case "투구":
			case "상의":
			case "하의":
			case "장갑":
			case "어깨":
				HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
				HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
				HashMap<String, Object> limit_element = (HashMap<String, Object>)maps.get("limit_element");
				HashMap<String, Object> elixir_element = (HashMap<String, Object>)maps.get("elixir_element");
				
				//초월 정보 출력
				totLimit = LoaApiParser.parseLimit(limit_element);
				resEquip = resEquip + enterStr +equip.get("Type").toString()+"→" + LoaApiParser.parseLimitForLimit(limit_element)+"◈";
				resEquip = LoaApiUtils.filterText(resEquip);

				//엘릭서 정보 출력 
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList,elixir_element);
				resEquip  +=LoaApiParser.parseElixirForLimit(equipElixirList,elixir_element);
				
				break;
				default:
				continue;
			}
		}
		
		String elixirField="";
		int cnt =0 ;
		for(String elixir:elixirList) {
			cnt += Collections.frequency(equipElixirList, elixir);
			if(cnt > 1) { // 회심2 를 회심으로 표기 
				elixirField = elixirField + elixir;
				break;
			}else {
				continue;
			}
			
		}
		resEquip=resEquip.replaceAll("  ", " ");
		
		
		
		resMsg = resMsg + enterStr;
		
		if(totLimit.equals("")) {
			resMsg = resMsg + "§초월 : 없음";
		}else {
			resMsg = resMsg + "§초월합 : " + totLimit;
		}
		if(totElixir==0) {
			resMsg = resMsg + " 엘릭서 : 없음";
		}else {
			resMsg = resMsg +" 엘릭서합 : " + totElixir + "(" + elixirField+")";
		}
		//resMsg = resMsg +"초월합 : " + totLimit + " 엘릭서합 : " + totElixir + "(" + elixirField+")";
		resMsg = resMsg + enterStr;
		resMsg = resMsg + resEquip;
		
		return resMsg;
	}
	
	
	String equipmentSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment%2Bprofiles";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		List<Map<String, Object>> armoryEquipment;
		Map<String, Object> armoryProfile;
		
		try {
			armoryProfile = (Map<String, Object>) rtnMap.get("ArmoryProfile");
		}catch(Exception e){
			throw new Exception("E0003");
		}
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
			throw new Exception("E0003");
		}
		
		List<String> equipSetList = new ArrayList<>();
		List<String> equipElixirList = new ArrayList<>();
		
		int weaponQualityValue=0;
		double armorQualityValue=0;
		
		double tmpQuality=0;
		double avgQuality=0;
		
		String resMsg = ordUserId;

		String enhanceLv="";
		String newEnhanceInfo="";
		String totLimit ="";
		int totElixir =0;
		
		/*
		StringBuilder sb1 = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		StringBuilder sb3 = new StringBuilder();
		StringBuilder sb4 = new StringBuilder();
		StringBuilder sb5 = new StringBuilder();
		*/
		
		String resField1 = "";
		String resField2 = "";
		String resField3 = "";
		
		
		String avgLv = armoryProfile.get("ItemMaxLevel").toString();
		String className = armoryProfile.get("CharacterClassName").toString();

		for (Map<String, Object> equip : armoryEquipment) {
			HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
			HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
			HashMap<String, Object> weapon_element = (HashMap<String, Object>)maps.get("weapon_element");
			HashMap<String, Object> quality_element = (HashMap<String, Object>)maps.get("quality_element");
			HashMap<String, Object> new_refine_element = (HashMap<String, Object>)maps.get("new_refine_element");
			HashMap<String, Object> limit_element = (HashMap<String, Object>)maps.get("limit_element");
			HashMap<String, Object> elixir_element = (HashMap<String, Object>)maps.get("elixir_element");
			
			
			//악세들은 레벨파싱에서 에러가남 
			switch (equip.get("Type").toString()) {
			case "무기":case "투구": case "상의": case "하의": case "장갑": case "어깨":
				String setFind = Jsoup.parse((String) weapon_element.get("value")).text();
				if(equip.get("Type").toString().equals("무기")) {
					enhanceLv = setFind.replaceAll("[^0-9]", "");
				}
				
				for(String set:LoaApiParser.getSetList()) {
					if(setFind.indexOf(set) >= 0) {
						equipSetList.add(set);
					}
				}
				
				/* 아이템레벨 */
				//tmpLv = Integer.parseInt(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text().replaceAll("[^0-9]|[0-9]\\)$", ""));
				//avgLv = avgLv+tmpLv;
				
				/* 아이템레벨 */
				//if(tmpLv < 1610) {
				//	throw new Exception("E0001");
				//}
			}
			
			
			
			switch (equip.get("Type").toString()) {
			case "무기":
				/* 무기품질 */
				weaponQualityValue = (int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
				if(new_refine_element.size()>0) {
					newEnhanceInfo = Jsoup.parse((String) new_refine_element.get("value")).text();
					newEnhanceInfo = LoaApiUtils.filterText(newEnhanceInfo);
					newEnhanceInfo = newEnhanceInfo.replace("단계", "");
				}
				
				break;
			case "투구": case "상의": case "하의": case "장갑": case "어깨":
				resField1 += equip.get("Type").toString();//렙
				resField1 += " "+Jsoup.parse((String) weapon_element.get("value")).text().replaceAll("[^0-9]", "")+"강";
				if(new_refine_element.size()>0) {
					String newEnhanceInfo2="";
					newEnhanceInfo2 = Jsoup.parse((String) new_refine_element.get("value")).text();
					newEnhanceInfo2 = LoaApiUtils.filterText(newEnhanceInfo2);
					newEnhanceInfo2 = newEnhanceInfo2.replace("단계", "");
					resField1 += "[+"+newEnhanceInfo2+"]";
				}
				resField1 += tabStr+"품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
				resField1 += enterStr;
				
				resField2 += equip.get("Type").toString();//초
				resField2 += LoaApiParser.parseLimitForLimit(limit_element);
				resField2 = LoaApiUtils.filterText(resField2);
				resField2 += enterStr;
				
				
				resField3 += equip.get("Type").toString();//엘
				resField3 += LoaApiParser.parseElixirForLimit(null,elixir_element);
				resField3 += enterStr;
				
				/*
				switch(equip.get("Type").toString()) {
					case "투구":
						sb1.append(resField1);
						sb1.append("/");
						sb1.append(resField2);
						sb1.append("/");
						sb1.append(resField3);
						break;
					case "어깨":
						sb2.append(resField1);
						sb2.append("/");
						sb2.append(resField2);
						sb2.append("/");
						sb2.append(resField3);
						break;
					case "상의":
						sb3.append(resField1);
						sb3.append("/");
						sb3.append(resField2);
						sb3.append("/");
						sb3.append(resField3);
						break;
					case "하의":
						sb4.append(resField1);
						sb4.append("/");
						sb4.append(resField2);
						sb4.append("/");
						sb4.append(resField3);
						break;
					case "장갑":
						sb5.append(resField1);
						sb5.append("/");
						sb5.append(resField2);
						sb5.append("/");
						sb5.append(resField3);
						break;
				}
				*/
				
				//초월
				totLimit = LoaApiParser.parseLimit(limit_element);
				//엘릭서
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList, elixir_element);
				break;
			case "반지":case "귀걸이": case "목걸이":
				tmpQuality =(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
				avgQuality += tmpQuality;
				break;
			default:
			continue;
			}
		}
		
		String setField="";
		String elixirField="";
		
		for(String set:LoaApiParser.getSetList()) {
			int cnt0=0;
			cnt0 += Collections.frequency(equipSetList, set);
			if(cnt0 > 0) {
				setField = setField+cnt0+set;
			}
			
		}
		int cnt1=0;
		for(String elixer: LoaApiParser.getElixirList()) {
			cnt1 += Collections.frequency(equipElixirList, elixer);
			if(cnt1 > 1) { // 회심2 를 회심으로 표기 
				elixirField = elixirField + elixer;
				break;
			}else {
				continue;
			}
			
		}
		
		resMsg += avgLv + "Lv " + className + enterStr;
		resMsg += "§무기 : "+enhanceLv+"강";
		if(!newEnhanceInfo.equals("")) {
			resMsg +="[+"+newEnhanceInfo+"]"; 
		}
		resMsg +=" 무품 : "+weaponQualityValue + enterStr; 
		resMsg += "§악세평균품질 : "+avgQuality/5 + enterStr;
		resMsg += "§세트 : "+setField + enterStr;
		
		if(totLimit.equals("")) {
			resMsg += "§초월 : 없음";
		}else {
			resMsg += "§초월합 : " + totLimit;
		}
		if(totElixir==0) {
			resMsg += " 엘릭서 : 없음";
		}else {
			resMsg += " 엘릭서합 : " + totElixir + "(" + elixirField+")";
		}
		
		resMsg += gemSearch(ordUserId);
		resMsg += engraveSearch(ordUserId);
		
		resMsg += enterStr+enterStr;
		resMsg += "방어구 상세정보 더보기..▼"+allSeeStr;
		//resMsg += "방어구 / 초월 / 엘릭서"+enterStr;
		
		resMsg += "§세트 : "+setField + enterStr;
		resMsg += resField1 + enterStr;
		
		
		if(totLimit.equals("")) {
			resMsg += "§초월 : 없음" + enterStr;
		}else {
			resMsg += "§초월합 : " + totLimit + enterStr;
			resMsg += resField2 + enterStr;
		}
		
		if(totElixir==0) {
			resMsg += "§엘릭서 : 없음" + enterStr;;
		}else {
			resMsg += "§엘릭서합 : " + totElixir + "(" + elixirField+")" + enterStr;;
			resMsg += resField3 + enterStr;
		}
		
		
		
		/*
		resMsg += sb1+enterStr;
		resMsg += sb2+enterStr;
		resMsg += sb3+enterStr;
		resMsg += sb4+enterStr;
		resMsg += sb5+enterStr;
		*/
		return resMsg;
	}
	
	String raidSearch(HashMap<String,Object> reqMap) throws Exception {
		
		List<String> charList = botService.selectBotRaidSaveAll(reqMap);
		
		List<HashMap<String,Object>> attackerList = new ArrayList<>();
		List<HashMap<String,Object>> supporterList = new ArrayList<>();
		List<HashMap<String,Object>> notEnoughList = new ArrayList<>();
		
		HashMap<String,Object> charInfo; 
		
		int count = 0;
		for(String userId:charList) {
			String ordUserId=userId;
			userId = URLEncoder.encode(userId, "UTF-8");
			// +는 %2B로 치환한다
			String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=profiles";
			String returnData = LoaApiUtils.connect_process(paramUrl);
			HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

			Map<String, Object> armoryProfile;
			try {
				armoryProfile = (Map<String, Object>) rtnMap.get("ArmoryProfile");
			}catch(Exception e){
				continue;
			}
			
			charInfo = new HashMap<>();
			String avgLv = armoryProfile.get("ItemMaxLevel").toString();
			String className = armoryProfile.get("CharacterClassName").toString();
			
			charInfo.put("id",ordUserId);
			charInfo.put("lv", avgLv);
			charInfo.put("class", className);
			
			Double lv = Double.parseDouble(avgLv.replaceAll(",", ""));
			if(lv < 1635) {
				notEnoughList.add(charInfo);
			}else if(className.equals("바드")
				  || className.equals("도화가")
				  || className.equals("홀리나이트")) {
				supporterList.add(charInfo);
				count++;
			}else {
				attackerList.add(charInfo);
				count++;
			}
		}
		
		
		List<HashMap<String, Object>> sortedList1 = attackerList.stream()
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("lv").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		List<HashMap<String, Object>> sortedList2 = supporterList.stream()
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("lv").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		
		
		String resMsg =reqMap.get("roomName")+" 베히모스 레이드접수판 "+count+"/16"+enterStr;
		
		resMsg += "※딜러※"+enterStr;
		for(HashMap<String,Object> hs :sortedList1) {
			resMsg += "[" + LoaApiUtils.shortClassName(hs.get("class").toString()) + "] ";
			resMsg += "("+hs.get("lv")+")";
			resMsg += hs.get("id");
			resMsg += enterStr;
		}
		resMsg += "※서포터※"+enterStr;
		for(HashMap<String,Object> hs :sortedList2) {
			resMsg += "[" + LoaApiUtils.shortClassName(hs.get("class").toString()) + "] ";
			resMsg += "("+hs.get("lv")+")";
			resMsg += hs.get("id");
			resMsg += enterStr;
		}
		resMsg += "※참여불가:1635↓※"+enterStr;
		for(HashMap<String,Object> hs :notEnoughList) {
			resMsg += hs.get("id");
			resMsg += enterStr;
		}
		
		return resMsg;
	}
	void raidAdd(HashMap<String,Object> reqMap) throws Exception {
		botService.insertBotRaidSaveTx(reqMap);
	}
	
	
	
	
	String engraveSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/engravings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});


		String resMsg="";
		List<Map<String, Object>> engraves;
		
		try {
			engraves = (List<Map<String, Object>>) rtnMap.get("Effects");
		}catch(Exception e){
			//throw new Exception("E0003");
			return enterStr+"§각인 : 정보 없음";
		}
		
		List<String> engraveList = new ArrayList<>();
		
		for (Map<String, Object> engrave : engraves) {
			int len = engrave.get("Name").toString().length();
			String tmpEng = engrave.get("Name").toString().substring(0,1)+engrave.get("Name").toString().substring(len-1,len);
			engraveList.add(tmpEng);
		}
		resMsg = resMsg + enterStr+"§각인 : "+ engraveList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		
		return resMsg;
	}
	
	String gemSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});


		String[] gemList = {"멸화","홍염"};
		List<Integer> equipGemDealList = new ArrayList<>();
		List<Integer> equipGemCoolList = new ArrayList<>();
		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			//throw new Exception("E0003");
			
			return enterStr+"§보석 : 정보 없음";
		}
		String resMsg = "";
		for (Map<String, Object> gem : gems) {
			String gemName = Jsoup.parse((String) gem.get("Name")).text();
			for(String equipGem : gemList) {
				if( gemName.indexOf(equipGem)>=0 ) {
					if(equipGem.equals(gemList[0])) {
						equipGemDealList.add((int)gem.get("Level"));
					}else if(equipGem.equals(gemList[1])) {
						equipGemCoolList.add((int)gem.get("Level"));
					}
				}
			}
		}
		
		Collections.sort(equipGemDealList,Collections.reverseOrder());
		Collections.sort(equipGemCoolList,Collections.reverseOrder());
		resMsg = resMsg + enterStr+"§"+gemList[0]+" : "+ equipGemDealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		resMsg = resMsg + enterStr+"§"+gemList[1]+" : "+ equipGemCoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		
		return resMsg;
	}

	String collectionSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/collectibles";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+" 내실 정보";
		double point=0;
		double maxPoint=0;
		String type="";
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		for(HashMap<String,Object> rtn : rtnMap) {
			
			type = rtn.get("Type").toString();
			type = type.replaceAll(" 씨앗", "");
			point = Double.parseDouble(rtn.get("Point").toString());
			maxPoint =  Double.parseDouble(rtn.get("MaxPoint").toString()); 
			
			resMsg +=enterStr+"§["+Math.round((point/maxPoint)*100)+"%]"+type + " ("+Math.round(point)+"/"+Math.round(maxPoint)+")";
			
		}
		
		return resMsg;
	}
	
	String subCharacterSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+" 부캐 정보" + enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", "")) >= 1415)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String mainServer = sortedList.get(0).get("ServerName").toString();
		
		resMsg += mainServer;
		resMsg += enterStr;
		
		int charCnt = 0;
		for(HashMap<String,Object> charList : sortedList) {
			if(mainServer.equals(charList.get("ServerName").toString())) {
				charCnt++;
				resMsg += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "] ";
				resMsg += "("+charList.get("ItemMaxLevel").toString().replaceAll(",", "")+") ";
				resMsg += charList.get("CharacterName").toString();
				resMsg += enterStr;
				
				if(charCnt ==6) {
					resMsg += enterStr + "6캐릭 이상 더보기..▼ ";
					resMsg += allSeeStr ;
				}
			}
		}
		
		return resMsg;
	}
	
	String marketSearch() throws Exception {
		
		
		JSONObject json ;
		String resMsg= "[아이템명]-[실시간최저가]"+enterStr;
		
		json = new JSONObject();
		json.put("CategoryCode", "50000");
		
		json.put("itemName", "태양");
		resMsg += marketSearchDt(json,0);
		
		json.put("itemName", "명예의 파편");
		resMsg += marketSearchDt(json,1);
		
		json.put("itemName", "찬란");
		resMsg += marketSearchDt(json,2);
		
		json.put("itemName", "파괴강석");
		resMsg += marketSearchDt(json,3);
		
		json.put("itemName", "최상급");
		resMsg += marketSearchDt(json,4);
		
		
		//여기부턴 거래소
		resMsg += enterStr;
		
		json.put("CategoryCode", "210000");
		json.put("Sort", "BUY_PRICE");
		json.put("SortCondition", "ASC");
		
		json.put("itemName", "10레벨 멸");
		resMsg += auctionSearchDt(json);
		
		json.put("itemName", "10레벨 홍");
		resMsg += auctionSearchDt(json);
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
		return resMsg;
	}
	
	String marketSearchDt(JSONObject json,int numbering) throws Exception {
		String str = "";

		try {

			String paramUrl = lostArkAPIurl + "/markets/items";

			String returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
			HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,
					new TypeReference<Map<String, Object>>() {
					});
			List<HashMap<String, Object>> itemMap = (List<HashMap<String, Object>>) rtnMap.get("Items");

			String price ="";
			switch(numbering){
				case 0:
					for (HashMap<String, Object> item : itemMap) {
						price += item.get("CurrentMinPrice")+" ";
					}
					str += "숨결 - "+price.trim().replace(" ", "/")+ "G" + enterStr;
					break;
				case 1:
					for (HashMap<String, Object> item : itemMap) {
						price += item.get("CurrentMinPrice")+" ";
					}
					str += "명파 - "+price.trim().replace(" ", "/")+ "G" + enterStr;
					break;
				case 3:
					String i1 = itemMap.get(0).get("Name").toString();
					String i2 = itemMap.get(1).get("Name").toString();
					int p1 = Integer.valueOf(itemMap.get(0).get("CurrentMinPrice").toString());
					int p2 = Integer.valueOf(itemMap.get(1).get("CurrentMinPrice").toString());
					int flag=0;
					
					if(p1*5 > p2) {
						flag=1;//p2가 더 효율
					}else if(p1*5 <p2) {
						flag=2;//p1이 더 효율 
					}
					
					switch(flag) {
						case 0://동일
							str += i1+"/"+i2 +" - "+p1+"/"+p2 + "G" + enterStr; 
							break;
						case 1://p2 더 효율
							str += i1+"/★"+i2 +" - "+p1+"/★"+p2 + "G" + enterStr;
							break;
						case 2://p1 더 효율
							str += "★"+i1+"/"+i2 +" - ★"+p1+"/"+p2 + "G" + enterStr;
							break;
					}
					
					break;
				case 2:
				case 4:
					for (HashMap<String, Object> item : itemMap) {
						str += item.get("Name") + " - " + item.get("CurrentMinPrice") + "G" + enterStr;
					}
					break;
				
			}
			
			

		} catch (Exception e) {
			str = "";
		}

		return str;
	}

	String auctionSearchDt(JSONObject json) throws Exception {
		String str = "";

		try {

			String paramUrl = lostArkAPIurl + "/auctions/items";

			String returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
			HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,
					new TypeReference<Map<String, Object>>() {
					});
			List<HashMap<String, Object>> itemMap = (List<HashMap<String, Object>>) rtnMap.get("Items");
			HashMap<String, Object> item = itemMap.get(0);
			HashMap<String, Object> auctionInfo = (HashMap<String, Object>) item.get("AuctionInfo");
			
			str += item.get("Name") + " - " + auctionInfo.get("BuyPrice") + "G" + enterStr;

		} catch (Exception e) {
			e.printStackTrace();
			str = "";
		}

		return str;
	}

	private static <T> Collector<T, ?, List<T>> toReversedList() {
	    return Collectors.collectingAndThen(Collectors.toList(), list -> {
	        Collections.reverse(list);
	        return list;
	    });
	}
	
	
	String weatherSearch(String area) throws Exception {
		HashMap<String, Object> rtnMap = new HashMap<>();

		String retMsg = "";
		String errMsg = "불러올 수 없는 지역이거나 지원되지 않는 지역입니다."+enterStr+"ex)00시00구00동 (띄어쓰기없이)";
		try {
			LoaApiUtils.setSSL();
			String WeatherURL = "https://m.search.naver.com/search.naver?&query=날씨+" + area;
			Document doc = Jsoup.connect(WeatherURL).get();
			String cur_temp = doc.select(".weather_info ._today .temperature_text strong").text();
			String weather = doc.select(".weather_info ._today .before_slash").text();
			String diff_temp = doc.select(".weather_info ._today .temperature_info .temperature").text();// 어제와 온도차이
			
			String v1_text = doc.select(".weather_info ._today .summary_list .sort:eq(0) .term").text();
			String v2_text = doc.select(".weather_info ._today .summary_list .sort:eq(1) .term").text();
			String v3_text = doc.select(".weather_info ._today .summary_list .sort:eq(2) .term").text();
			String v4_text = doc.select(".weather_info ._today .summary_list .sort:eq(3) .term").text();
			
			String v1 = doc.select(".weather_info ._today .summary_list .sort:eq(0) .desc").text();// 체감
			String v2 = doc.select(".weather_info ._today .summary_list .sort:eq(1) .desc").text();// 습도
			String v3 = doc.select(".weather_info ._today .summary_list .sort:eq(2) .desc").text();// 풍속
			String v4 = doc.select(".weather_info ._today .summary_list .sort:eq(3) .desc").text();// 
			
			//v 체감 강수 습도 북동풍 
			
			
			//m : 미세먼지 
			String mise_text="";
			try {
				for(int i=0;i<3;i++) {
					try {
						mise_text += doc.select(".weather_info:not(.type_tomorrow) .today_chart_list .item_today:eq("+i+") .title").text();
						mise_text += " : ";
						mise_text += doc.select(".weather_info:not(.type_tomorrow) .today_chart_list .item_today:eq("+i+") .txt").text();
						mise_text += ".";
						mise_text += enterStr;
					}catch(Exception e) {
						continue;
					}
					
				}
				
				
			}catch(Exception e) {
				
			}
			
			
			
			//t : 시간별 날씨 
			String time_text="";
			String tmp_weather="";
			try {
				for(int i=0;i<4;i++) {
					time_text += enterStr;	
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+i+") .time" ).text();
					time_text += " : ";
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+i+") .blind").text();
					time_text += " ";
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+(i+4)+") .time" ).text();
					time_text += " : ";
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+(i+4)+") .blind").text();
					//tmp_weather = StringUtils.rightPad("맑음", 4, " ");
					
				}
				time_text = time_text.replaceAll("내일", "00시");
				time_text = time_text.replaceAll("많음", "");
			}catch(Exception e) {
			}

			if(cur_temp.equals("")) {
				return errMsg;
			}
			
			retMsg += "오늘날씨 : " + weather;
			retMsg += enterStr+"현재온도 : " + cur_temp;
			retMsg += enterStr+""+v1_text+" : " + v1;
			retMsg += enterStr+v2_text+" : " + v2;
			retMsg += enterStr+v3_text+" : " + v3;
			if(v4!=null && !v4.equals("")) {
				retMsg += enterStr+v4_text+" : " + v4;
			}
			retMsg += enterStr+"현재 " + area + "의 온도는 " + cur_temp + " 이며 어제보다 " + diff_temp;
			retMsg += enterStr;
			
			if(mise_text!=null && !mise_text.equals("")) {
				retMsg += enterStr+mise_text;
			}
			
			if(time_text!=null && !time_text.equals("")) {
				retMsg += time_text;
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
			retMsg = errMsg;
		}
		rtnMap.put("data", retMsg);
		return retMsg;
	}
	
	
}
