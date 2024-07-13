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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
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
import my.prac.core.util.ChatGPTUtils;
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
	final String spaceStr= "`";
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
		case "/골드": case "/ㄱㄷ": case "/클골": case "/ㅋㄱ":
			val = checkGoldList();
			break;
		case "/모험섬": case "/ㅁㅎㅅ":
			LocalDate now = LocalDate.now();
			DayOfWeek dayOfWeek = now.getDayOfWeek();
			
			LocalTime time = LocalTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH");
			String nowTime = time.format(formatter);
			
			switch(dayOfWeek.getValue()) {
				case 2:
					//화요일엔 다음날 정보 없음
					val  = calendarSearch(0);
					break;
				case 3:
					//수요일 오전엔 정보 없음
					if( Integer.parseInt(nowTime) <= 6) {
						val  = "정보없음" ;
					}else {
						val  = calendarSearch(0);
						val += enterStr+"내일의 모험섬 더보기..▼"+allSeeStr;
						val += calendarSearch(1);
						val += tossAccount();
					}
					break;
				default:
					val  = calendarSearch(0);
					val += enterStr+"내일의 모험섬 더보기..▼"+allSeeStr;
					val += calendarSearch(1);
					val += tossAccount();
					break;
			}
			
			
			break;
			
		case "/장비":
		case "/ㅈㅂ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= equipmentSearch(param1);
					val+= tossAccount();
					
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/초월": case "/엘릭서":
		case "/ㅊㅇ": case "/ㅇㄹㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= limitSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/내실":
		case "/ㄴㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= collectionSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;
		case "/ㄱㅁㅈ":
		case "/경매장":
			val = marketSearch();
			break;
		case "/ㄱㅁㅈ3":
		case "/경매장3":
			val = marketSearch(3);
			break;
		case "/ㄱㅁㅈ4":
		case "/경매장4":
			val = marketSearch(4);
			break;
		case "/ㄱㅁㅈㅇㅁ":
		case "/ㄱㅁㅈ유물":
		case "/경매장유물":
			val = marketSearch(40000);
			break;
			
		case "/레이드":
		case "/ㄹㅇㄷ":
			if (param1 != null && !param1.equals("")) {
				try {
					//raidAdd(reqMap);
					//val = raidSearch(reqMap);
				} catch (Exception e) {
					//val = errorCodeMng(e);
				}
			}else {
				//val = raidSearch(reqMap);
			}
			break;
		case "/악세":
		case "/ㅇㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= accessorySearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;		
		case "/부캐":
		case "/ㅂㅋ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= subCharacterSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;	
		case "/보석":
		case "/ㅂㅅ":
			if (param1 != null && !param1.equals("")) {
				try {
					val = supporters(param1);
					val+= subCharacterGemSearch(param1);
				} catch (Exception e) {
					val = errorCodeMng(e);
				}
			}
			break;		
		case "/항협": case "/항해": case "/항해협동": case "/ㅎㅎ":
			val = shipSearch();
			break;
		case "/섬마": case "/가방": case "/ㄱㅂ": case "/ㅅㅁ":
			val = openBox(param1,param2);
			break;
		case "/날씨": case "/ㄴㅆ":
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
		case "/챗":
			fulltxt = fulltxt.substring(param0.length()).trim();
			val = chatGptSearch(fulltxt,sender);
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
		case "/단어목록": case "/단어조회": case "/단어": case "/ㄷㅇ":
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
			
			val += tossAccount();
			
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
		msg += "시즌3 골드획득정보..";
		msg += enterStr + "베히모스 21,500G";
		msg += enterStr + "에키드나[하 18,500G/노 14,500G]";
		msg += enterStr + "카멘 1-3[하 20,000G/노 13,000G]";
		msg += enterStr + "상아탑 [하 13,000G/노 6,500G]";
		msg += enterStr + "일리아칸 [하 8,500G/노 5,400G]";
		msg += enterStr + "카양겔 [하 4,800G/노 3,600G]";
		msg += enterStr + "아브 1-3 [하 3,600G/노 3,000G]";
		msg += enterStr + "아브 1-4 [하 5,600G/노 4,600G]";
		msg += enterStr ;
		msg += enterStr + "싱글모드..▼ "+ allSeeStr;
		msg += enterStr + "발탄 600G";
		msg += enterStr + "비아키스 800G";
		msg += enterStr + "쿠크 1,400G";
		msg += enterStr + "아브 1,700G(+900G)";
		msg += enterStr + "카양겔 1,700G";
		msg += enterStr + "일리아칸 2,800G";
		msg += enterStr + "상아탑 3,500G";
		msg += enterStr;

		return msg;
	}
	
	String calendarSearch(int type) throws Exception {
		List<String> ret1Arr = new ArrayList<>();
		List<String> ret2Arr = new ArrayList<>();
		String today = "";
		String retMsg="";
		String retMsg1="";
		String retMsg2="";
		String retMsg3="";
		
		if(type==0) {
			retMsg="오늘의 모험 섬";
			today = LoaApiUtils.StringToDate();
		}else {
			retMsg= enterStr + enterStr + "내일의 모험 섬";
			today = LoaApiUtils.StringTommorowDate();
		}
		
		retMsg += " ("+today+")";
		
				
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
				
				if(data_sub_list.get("StartTimes")==null) {
					continue;
				}
				
				String times = Jsoup.parse((String) data_sub_list.get("StartTimes").toString()).text();
				if(times.indexOf(today) < 0) {
					continue;
				}
				
				List<Map<String, Object>> rewardItemsList = (List<Map<String, Object>>)data_sub_list.get("RewardItems");
				for(Map<String, Object> rewardItem : rewardItemsList) {
					rewardItem.remove("Icon");
					rewardItem.remove("Location");
					rewardItem.remove("Grade");
					
					List<Map<String, Object>> items = (List<Map<String, Object>>) rewardItem.get("Items");
					for(Map<String, Object> item:items) {
						if(item.get("StartTimes")!=null) {

							List<String> start_time_list = (List<String>)item.get("StartTimes");
							for(String time : start_time_list) {
								if(time.equals(today+"T09:00:00")) {
									switch(item.get("Name").toString()) {
										case "전설 ~ 고급 카드 팩 IV":
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
									switch(item.get("Name").toString()) {
										case "전설 ~ 고급 카드 팩 IV":
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
				retMsg3 = enterStr+"§API정보없음 : "+ retMsg3;
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
			HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
			HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
			HashMap<String, Object> limit_element = (HashMap<String, Object>)maps.get("limit_element");
			HashMap<String, Object> elixir_element = (HashMap<String, Object>)maps.get("elixir_element");
			
			switch (equip.get("Type").toString()) {
			case "무기":
				
				String tmpWeaponLimit = LoaApiParser.parseLimitForLimit(limit_element);
				if(!tmpWeaponLimit.equals("")) {
					resEquip += enterStr +equip.get("Type").toString()+" :" + tmpWeaponLimit;
					resEquip = LoaApiUtils.filterText(resEquip);
				}
				break;
			
			case "투구":
			case "상의":
			case "하의":
			case "장갑":
			case "어깨":
				//초월합계는 장비에서가져옴 
				String tmpLimit = LoaApiParser.parseLimit(limit_element);
				if(!tmpLimit.equals("")) {
					totLimit = tmpLimit;
				}
				 
				
				//초월 정보 출력
				resEquip += enterStr +equip.get("Type").toString()+" :" + LoaApiParser.parseLimitForLimit(limit_element)+"◈";
				resEquip = LoaApiUtils.filterText(resEquip);

				//엘릭서 정보 출력 
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList,elixir_element);
				resEquip  +=LoaApiParser.parseElixirForLimit(equipElixirList,elixir_element,1);
				
				break;
				default:
				continue;
			}
		}
		
		String elixirField="";
		int cnt =0 ;
		
		if(equipElixirList.size()==2) {
			if(equipElixirList.get(0).equals(equipElixirList.get(1))) {
				for(String elixer: LoaApiParser.getElixirList()) {
					cnt += Collections.frequency(equipElixirList, elixer);
					if(cnt > 1) { // 회심2 를 회심으로 표기 
						elixirField = elixirField + elixer;
						break;
					}else {
						continue;
					}
					
				}
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
		
		String resField1 = "";
		String resField2 = "";
		String resField3 = "";
		
		
		String avgLv = armoryProfile.get("ItemMaxLevel").toString();
		String className = armoryProfile.get("CharacterClassName").toString();
		

		HashMap<String,Object> arkPassive= (HashMap<String, Object>) armoryProfile.get("ArkPassive");
		String isArkPassive = arkPassive.get("IsArkPassive").toString();
		List<HashMap<String,Object>> arkPassivePt = (List<HashMap<String, Object>>) arkPassive.get("Points");
		
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
				
				String tmpWeaponLimit = LoaApiParser.parseLimitForLimit(limit_element);
				if(!tmpWeaponLimit.equals("")) {
					resField2 += equip.get("Type").toString()+" :";//초
					resField2 += tmpWeaponLimit;
					resField2 = LoaApiUtils.filterText(resField2);
					resField2 += enterStr;
				}
				
				break;
			case "투구": case "상의": case "하의": case "장갑": case "어깨":
				resField1 += equip.get("Type").toString()+" :";//렙
				resField1 += " "+Jsoup.parse((String) weapon_element.get("value")).text().replaceAll("[^0-9]", "")+"강";
				if(new_refine_element.size()>0) {
					String newEnhanceInfo2="";
					newEnhanceInfo2 = Jsoup.parse((String) new_refine_element.get("value")).text();
					newEnhanceInfo2 = LoaApiUtils.filterText(newEnhanceInfo2);
					newEnhanceInfo2 = newEnhanceInfo2.replace("단계", "");
					newEnhanceInfo2 = StringUtils.leftPad( newEnhanceInfo2, 2, " ");
					resField1 += "[+"+newEnhanceInfo2+"]";
				}else {
					resField1 += "　 　";
				}
				resField1 += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
				resField1 += enterStr;
				
				resField2 += equip.get("Type").toString()+" :";//초
				resField2 += LoaApiParser.parseLimitForLimit(limit_element);
				resField2 = LoaApiUtils.filterText(resField2);
				resField2 += enterStr;
				
				
				resField3 += equip.get("Type").toString()+" :";//엘
				resField3 += LoaApiParser.parseElixirForLimit(null,elixir_element,0);
				resField3 += enterStr;
				
				//초월
				//초월합계는 장비에서가져옴 
				String tmpLimit = LoaApiParser.parseLimit(limit_element);
				if(!tmpLimit.equals("")) {
					totLimit = tmpLimit;
				}
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
		
		if(equipElixirList.size()==2) {
			if(equipElixirList.get(0).equals(equipElixirList.get(1))) {
				for(String elixer: LoaApiParser.getElixirList()) {
					cnt1 += Collections.frequency(equipElixirList, elixer);
					if(cnt1 > 1) { // 회심2 를 회심으로 표기 
						elixirField = elixirField + elixer;
						break;
					}else {
						continue;
					}
					
				}
			}
		}
		
		
		resMsg += " "+avgLv + "Lv " + className + enterStr;
		resMsg += "§무기 : "+enhanceLv+"강";
		if(!newEnhanceInfo.equals("")) {
			resMsg +="[+"+newEnhanceInfo+"]"; 
		}
		resMsg +=" 무품 : "+weaponQualityValue + enterStr; 
		resMsg += "§악세평균품질 : "+avgQuality/5 + enterStr;
		resMsg += "§세트 : "+setField + enterStr;
		
		if(isArkPassive.equals("true")) {
			resMsg +="♩AP-사용 : Y"+enterStr;
		}else {
			resMsg +="♩AP-사용 : N"+enterStr;
		}
		
		for(HashMap<String,Object> pt:arkPassivePt) {
			resMsg +="♩AP-"+pt.get("Name")+" : " +pt.get("Value")+enterStr;
		}
		
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
		
		int tier = 3;
		if(Double.parseDouble(avgLv.replaceAll(",", ""))>=1640) {
			tier = 4;
		}
		resMsg += gemSearch(ordUserId, tier);
		if(isArkPassive.equals("true")) {
			resMsg +="♩AP-각인 : 미구현"+enterStr;	
		}else {
			resMsg += engraveSearch(ordUserId);
		}
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
	
	String accessorySearch(String userId) throws Exception {
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
		
		String resMsg = ordUserId+ " 악세정보"+enterStr;


		for (Map<String, Object> equip : armoryEquipment) {
			HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
			HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
			HashMap<String, Object> weapon_element = (HashMap<String, Object>)maps.get("weapon_element");
			HashMap<String, Object> quality_element = (HashMap<String, Object>)maps.get("quality_element");
			HashMap<String, Object> new_refine_element = (HashMap<String, Object>)maps.get("new_refine_element");
			HashMap<String, Object> limit_element = (HashMap<String, Object>)maps.get("limit_element");
			HashMap<String, Object> elixir_element = (HashMap<String, Object>)maps.get("elixir_element");
			HashMap<String, Object> bracelet_element = (HashMap<String, Object>)maps.get("bracelet_element");
			HashMap<String, Object> stone_element = (HashMap<String, Object>)maps.get("stone_element");
			
			switch (equip.get("Type").toString()) {
			case "어빌리티 스톤":
				HashMap<String, Object> stone_val = (HashMap<String, Object>) stone_element.get("value");
				HashMap<String, Object> stone_option = (HashMap<String, Object>) stone_val.get("Element_000");
				HashMap<String, Object> stone_option0 = (HashMap<String, Object>) stone_option.get("contentStr");
				HashMap<String, Object> stone_option1 = (HashMap<String, Object>) stone_option0.get("Element_000");
				HashMap<String, Object> stone_option2 = (HashMap<String, Object>) stone_option0.get("Element_001");
				
				resMsg += equip.get("Name");
				resMsg += enterStr;
				String stone_option1_str = Jsoup.parse(stone_option1.get("contentStr").toString()).text();
				String stone_option2_str = Jsoup.parse(stone_option2.get("contentStr").toString()).text();
				
				int len = 0;
				
				stone_option1_str = stone_option1_str.replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
				len = stone_option1_str.length();
				stone_option1_str = stone_option1_str.substring(0,1)+stone_option1_str.substring(len-1,len);

				stone_option2_str = stone_option2_str.replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
				len = stone_option2_str.length();
				stone_option2_str = stone_option2_str.substring(0,1)+stone_option2_str.substring(len-1,len);
				
				resMsg += stone_option1_str + " " + stone_option2_str +enterStr;
				resMsg += enterStr;
				break;
			case "반지":case "귀걸이": case "목걸이":
				break;
			case "팔찌":
				resMsg += enterStr;
				resMsg += "팔찌 정보"+enterStr;
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				resMsg += LoaApiParser.findBraceletOptions(bracelet.get("Element_001").toString());
				
				resMsg += enterStr;
				resMsg += "상세 더보기..▼"+allSeeStr;
				String braceletDt = Jsoup.parse(bracelet.get("Element_001").toString().replace("<BR>", enterStr)).text();
				resMsg += braceletDt;
				break;
			default:
			continue;
			}
		}

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
	
	String gemSearch(String userId,int tier) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});


		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			//throw new Exception("E0003");
			
			return enterStr+"§보석 : 정보 없음";
		}
		if(gems == null) {
			return enterStr+"§보석 : 정보 없음";
		}
		if(gems.equals("null")) {
			return enterStr+"§보석 : 정보 없음";
		}
		
		String resMsg = "";
		for (Map<String, Object> gem : gems) {
			String gemName = Jsoup.parse((String) gem.get("Name")).text();
			for(String equipGem : gemList) {
				if( gemName.indexOf(equipGem)>=0 ) {
					if(equipGem.equals(gemList[0])) {
						equipGemT3DealList.add((int)gem.get("Level"));
					}else if(equipGem.equals(gemList[1])) {
						equipGemT3CoolList.add((int)gem.get("Level"));
					}else if(equipGem.equals(gemList[2])) {
						equipGemT4DealList.add((int)gem.get("Level"));
					}else if(equipGem.equals(gemList[3])) {
						equipGemT4CoolList.add((int)gem.get("Level"));
					}
				}
			}
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		resMsg = resMsg + enterStr+"§"+gemList[0]+" : "+ equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		resMsg = resMsg + enterStr+"§"+gemList[1]+" : "+ equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		if(tier ==4) {
			resMsg = resMsg + enterStr+"§"+gemList[2]+" : "+ equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg = resMsg + enterStr+"§"+gemList[3]+" : "+ equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		}
		return resMsg;
	}
	
	String gemCntSearch(String userId,int tier) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});


		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			//throw new Exception("E0003");
			
			return "";
		}
		if(gems == null) {
			return "";
		}
		if(gems.equals("null")) {
			return "";
		}
		String resMsg = "";
		int cnt = 0;
		for (Map<String, Object> gem : gems) {
			String gemName = Jsoup.parse((String) gem.get("Name")).text();
			for(String equipGem : gemList) {
				int gemLv = (int)gem.get("Level");
				if(gemLv < 7) {
					continue;
				}
				
				if( gemName.indexOf(equipGem)>=0 ) {
					cnt++;
					if(equipGem.equals(gemList[0])) {
						equipGemT3DealList.add(gemLv);
					}else if(equipGem.equals(gemList[1])) {
						equipGemT3CoolList.add(gemLv);
					}else if(equipGem.equals(gemList[2])) {
						equipGemT4DealList.add(gemLv);
					}else if(equipGem.equals(gemList[3])) {
						equipGemT4CoolList.add(gemLv);
					}
				}
			}
		}

		if(cnt==0) {
			return "";
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		resMsg = resMsg + enterStr+"§"+gemList[0]+" : "+ equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		resMsg = resMsg + enterStr+"§"+gemList[1]+" : "+ equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		if(tier ==4) {
			resMsg = resMsg + enterStr+"§"+gemList[2]+" : "+ equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg = resMsg + enterStr+"§"+gemList[3]+" : "+ equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
		}
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
		
		double totPoint=0;
		double totMaxPoint=0;

		double percent = 100.0/9.0;
		double eachPercent =0.0;
		double totEachPercent =0.0;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		for(HashMap<String,Object> rtn : rtnMap) {
			
			type = rtn.get("Type").toString();
			type = type.replaceAll(" 씨앗", "");
			point = Double.parseDouble(rtn.get("Point").toString());
			maxPoint =  Double.parseDouble(rtn.get("MaxPoint").toString()); 
			
			eachPercent = percent/maxPoint * point;
			totPoint +=point;
			totMaxPoint +=maxPoint;
			totEachPercent += eachPercent;
			resMsg +=enterStr+"["+(int)Math.floor((point/maxPoint)*100)+"%]"+type + " ("+Math.round(point)+"/"+Math.round(maxPoint)+")";
			
		}
		resMsg +=enterStr;
		resMsg +=enterStr+"로아와점수 : "+(int)totPoint+" / "+(int)totMaxPoint ;
		resMsg +=enterStr+"일로아점수 : "+ String.format("%.3f", totEachPercent);	
		
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
	String subCharacterGemSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+" 부캐 보석 정보" + enterStr;
		resMsg += "7보석↑ 캐릭터 표기" + enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", "")) >= 1415)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		resMsg += enterStr;
		
		int charCnt = 0;
		
		for(HashMap<String,Object> charList : sortedList) {
			String subCharName = charList.get("CharacterName").toString();
			Double lv = Double.parseDouble(charList.get("ItemMaxLevel").toString().replaceAll(",", ""));
			int tier =3;
			if(lv >=1640 ) {
				tier = 4;
			}
			String gemInfo = gemCntSearch(subCharName,tier);
			
			
			if(gemInfo.equals("")) {
				continue;
			}
			
			charCnt++;
			resMsg += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "] ";
			resMsg += "("+lv+") ";
			resMsg += subCharName + gemInfo + enterStr;
			
			if(charCnt ==3) {
				resMsg += enterStr + "3캐릭 이상 더보기..▼ ";
				resMsg += allSeeStr ;
			}
			
		}
		
		return resMsg;
	}
	
	
	
	String marketTier4Search() throws Exception {
		String resMsg = "";
		JSONObject json ;
		json = new JSONObject();

		json.put("CategoryCode", "50000");
		resMsg +="[4티어]"+enterStr;
		
		json.put("itemName", "용암");
		resMsg += marketDtSearch(json,2);
		
		json.put("itemName", "빙하");
		resMsg += marketDtSearch(json,2);
		
		json.put("itemName", "운명의 파편 주머니");
		resMsg += marketDtSearch(json,1);
		
		json.put("itemName", "운명의 돌파석");
		resMsg += marketDtSearch(json,2);
		
		json.put("itemName", "운명의 파괴석");
		resMsg += marketDtSearch(json,2);
		
		json.put("itemName", "아비도스");
		resMsg += marketDtSearch(json,4);
		
		//여기부턴 거래소
		resMsg += enterStr;
		
		json.put("CategoryCode", "210000");
		json.put("Sort", "BUY_PRICE");
		json.put("SortCondition", "ASC");
		
		resMsg += "10겁/작 -";
		json.put("itemName", "10레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "10레벨 작");
		resMsg += auctionSearchDt(json,false,true);
		
		resMsg += spaceStr+"9겁/작 -";
		json.put("itemName", "9레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "9레벨 작");
		resMsg += auctionSearchDt(json,false,true);

		resMsg += spaceStr+"8겁/작 -";
		json.put("itemName", "8레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "8레벨 작");
		resMsg += auctionSearchDt(json,false,true);

		resMsg += spaceStr+"7겁/작 -";
		json.put("itemName", "7레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "7레벨 작");
		resMsg += auctionSearchDt(json,false,true);
		
		return resMsg;
	}
	String marketTier3Search() throws Exception {
		String resMsg = "";
		JSONObject json ;
		json = new JSONObject();

		json.put("CategoryCode", "50000");
		resMsg +="[3티어]"+enterStr;
		
		json.put("itemName", "태양");
		resMsg += marketDtSearch(json,0);
		
		json.put("itemName", "명예의 파편");
		resMsg += marketDtSearch(json,1);
		
		json.put("itemName", "찬란");
		resMsg += marketDtSearch(json,2);
		
		json.put("itemName", "파괴강석");
		resMsg += marketDtSearch(json,3);
		
		json.put("itemName", "최상급");
		resMsg += marketDtSearch(json,4);
		
		//여기부턴 거래소
		resMsg += enterStr;
		
		json.put("CategoryCode", "210000");
		json.put("Sort", "BUY_PRICE");
		json.put("SortCondition", "ASC");
		
		resMsg += "10멸/홍 -";
		json.put("itemName", "10레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "10레벨 홍");
		resMsg += auctionSearchDt(json,false,true);
		
		
		resMsg += spaceStr+"9멸/홍 -";
		json.put("itemName", "9레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "9레벨 홍");
		resMsg += auctionSearchDt(json,false,true);
	
		
		return resMsg;
	}
	
	String marketSearch(int tier) throws Exception {
		JSONObject json ;
		String resMsg= "[아이템명]-[실시간최저가]"+enterStr;
		
		json = new JSONObject();
		
		
		if(tier==3) {
			resMsg += marketTier3Search();
		}else if(tier==4) {
			resMsg += marketTier4Search();
		}else if(tier==40000) {
			json.put("CategoryCode", "40000");
			json.put("Sort", "CURRENT_MIN_PRICE");
			json.put("SortCondition", "DESC");
			json.put("ItemGrade", "유물");
			
			resMsg +="[유물]"+enterStr;
			
			resMsg += marketDtSearch(json,2);
		}else {
			return "경매장/경매장3/경매장4/경매장유물 만 사용가능";
		}
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
		return resMsg;
	}
	
	String marketSearch() throws Exception {
		
		String resMsg= "[아이템명]-[실시간최저가]"+enterStr;
		
		resMsg +=marketTier4Search();
		resMsg +=enterStr;
		resMsg +=marketTier3Search();
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
		return resMsg;
	}
	
	String marketDtSearch(JSONObject json,int numbering) throws Exception {
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
					str += "파편주머니 - "+price.trim().replace(" ", "/")+ "G" + enterStr;
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

	String auctionSearchDt(JSONObject json,boolean nameDefaultYn,boolean enterYn) throws Exception {
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
			
			String buyPrice = auctionInfo.get("BuyPrice").toString();
			if(Integer.parseInt(buyPrice)>10000) {
				String tmpPrice1 = buyPrice.substring(0,				  buyPrice.length()-4);
				String tmpPrice2 = buyPrice.substring(buyPrice.length()-4,buyPrice.length()-3);
				
				buyPrice = tmpPrice1 + "."+tmpPrice2+"만";
			}
			
			if(nameDefaultYn) {
				str += item.get("Name") + " - " ;
			}
			
			str += buyPrice + "G";
			
			if(enterYn) {
				str +=enterStr;
			}

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
			
			
			String ondo_text="";
			try {
				ondo_text += doc.select(".weekly_forecast_area .today .lowest").text();
				ondo_text += " ";
				ondo_text += doc.select(".weekly_forecast_area .today .highest").text();
			}catch(Exception e) {
			}
			
			//m : 미세먼지 
			String mise_text="";
			try {
				for(int i=0;i<3;i++) {
					try {
						mise_text += doc.select(".weather_info:not(.type_tomorrow) .today_chart_list .item_today:eq("+i+") .title").text();
						mise_text += " : ";
						mise_text += doc.select(".weather_info:not(.type_tomorrow) .today_chart_list .item_today:eq("+i+") .txt").text();
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
					tmp_weather = "";
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+i+") .time" ).text();
					time_text += " : ";
					
					tmp_weather = doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+i+") .blind").text();
					tmp_weather = StringUtils.leftPad(tmp_weather, 2, "　");
					time_text +=tmp_weather;
					
					time_text += "　　";
					time_text += doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+(i+4)+") .time" ).text();
					time_text += " : ";
					tmp_weather = doc.select(".flicking-camera > div:first-child .weather_graph_box ._hourly_weather ._li:eq("+(i+4)+") .blind").text();
					tmp_weather = StringUtils.leftPad(tmp_weather, 2, "　");
					time_text +=tmp_weather;
					time_text +=enterStr;
					
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
			
			if(ondo_text!=null && !ondo_text.equals("")) {
				retMsg += enterStr+ondo_text;
			}
			
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
	
	
	public String openBox(String str1,String str2) throws Exception {
		String resMsg="";
		
		int i =0; 
		int j =0;
		
		try {
			i = Integer.parseInt(str1);
			j = Integer.parseInt(str2);
		}catch(Exception e) {
			return "오류"; 
		}
		
		int totJ=j; //최초 전체수량
		
		if(i==0 || i>j || i>2000 || j> 2000) {
			return "오류";
		}
		
		resMsg +=i+"개씩 개봉 전체:"+totJ+" 섬마0.3%/금화5%/은화94.7%"+enterStr;
		
		int tot_count = totJ/i; //전체회차
		int nmg = totJ%i; //나머지
		
		if(tot_count>17) {
			resMsg +=allSeeStr;
		}
		
		for(int count=0;count<tot_count;count++) {
			String findItem="";
			j = j-i;
			resMsg +=count+1+"회차 남은수량: "+j+"/"+totJ;
			for(int rd =0;rd<i;rd++) {
				findItem += openBox2();
			}
			int item3 = countChar(findItem, '3');
			int item2 = countChar(findItem, '2');
			int item1 = countChar(findItem, '1');
			
			StringBuilder sb = new StringBuilder();
			if(item3>0) {
				sb.append(" 은화 "+item3);
			}
			if(item2>0) {
				sb.append(" 금화 "+item2);
			}
			if(item1>0) {
				sb.append(" 섬마 "+item1);
			}
			resMsg +=sb+enterStr;
			if(item1>0) {
				break;
			}
		}
		return resMsg;
	}
	
	public int openBox2() {
		Random random = new Random();
		int rs = random.nextInt(1000)+1;
		if(rs>997) {
			return 1;//달성
		}else if(rs>947) {
			return 2;//금화
		}else {
			return 3;//은화
		}
		
	}

	public int countChar(String str, char ch) {
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == ch) {
				count++;
			}
		}
		return count;
	}
	
	
	
	public String chatGptSearch(String reqMsg,String userName) throws Exception {
		String content ="";
		
		int cnt = 0;
		//cnt = botService.select오늘얼마썻는지체크로직(userName);
		
		if(cnt>3) {
			content ="오늘 3회 모두 사용했습니다.";
		}
		
		try {
			String returnData = ChatGPTUtils.chatgpt_message_post2(reqMsg);
			// JSON 문자열을 JsonObject로 파싱
            JSONObject jsonObject = new JSONObject(returnData);

            // choices 배열에서 첫 번째 객체를 가져옴
            JSONArray choicesArray = jsonObject.getJSONArray("choices");
            JSONObject choiceObject = choicesArray.getJSONObject(0);

            // message 객체에서 content 값을 추출
            JSONObject messageObject = choiceObject.getJSONObject("message");
            content = messageObject.getString("content");

            content = content.replaceAll("\n", enterStr);
            
		} catch (Exception e) {
			e.printStackTrace();
			content = "오류입니다.";
		}
		
		// botService.save db에 저장 로직 (reqMsg,content)

		return content;
	}
	
	public String tossAccount() {
		String ment = "";
		ment += enterStr+"람쥐봇 후원하기(토스)";
		ment += enterStr+"https://toss.me/daramzz";
		return ment; 
	}
	
	public String supporters(String userId) {
		String ment = "";
		
		int suppertersYn = botService.selectSupporters(userId);
		if(suppertersYn > 0) {
			ment+="⭐";
		}
		return ment; 
	}
}
