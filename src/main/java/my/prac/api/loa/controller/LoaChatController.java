package my.prac.api.loa.controller;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
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
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	final String[] unable_save_list = {enterStr,spaceStr,tabStr,allSeeStr,anotherMsgStr,listSeparatorStr,"\\"};
	
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
			//e.printStackTrace();
			System.out.println(e.getMessage());
		}

		return null;
	}
	@RequestMapping(value = "/in/{imgvalues}", method = RequestMethod.GET)
	public String innerimgReturn(@PathVariable String imgvalues, Model model) {
		model.addAttribute("imgval",imgvalues);
		return "rtnimgs_innerfile";
	}
	
	@RequestMapping(value = "/im/{imgvalues}", method = RequestMethod.GET)
	public String wimgReturn(@PathVariable String imgvalues, Model model) {
		model.addAttribute("imgval",imgvalues);
		return "rtnimgs_emotion";
	}
	@RequestMapping(value = "/ic/{imgvalues}", method = RequestMethod.GET)
	public String cimgReturn(@PathVariable String imgvalues, Model model) {
		HashMap<String,String> info = botService.selectBotImgCharSaveI3(imgvalues);
		
		model.addAttribute("class_name",info.get("CLASS_NAME"));
		//info = 직업+이름 
		String title = info.get("TITLE");
		String char_name = info.get("CHAR_NAME");
		String starYn = info.get("STAR_YN");
		String star="";
		if(starYn!=null && starYn.equals("1")) {
			star+="⭐";
		}
		if(title == null || title.equals("null")) {
			title = "";
		}else {
			title += " ";
		}
		String char_info = star + title + char_name; 
		model.addAttribute("char_info",char_info);
		model.addAttribute("imgval",imgvalues);
		return "rtnimgs_charinfo";
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
		HashMap<String, Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("param1", param1);
		reqMap.put("roomName", roomName);
		reqMap.put("userName", sender);
		reqMap.put("fulltxt", fulltxt);
		String org_fulltxt = fulltxt;
		
		int masterYn =0;
		boolean passYn=false;
		
		try {
			if(fulltxt.length()>90) {
				val = "너무길어요!";
				return val;
			}
			
			switch (param0) {
			case "/ㄹㅇ":
			case "/주급":
			case "/ㅈㄱ":
			case "/보상":
			case "/비싼전각":
			case "/비싼유각":
				passYn = true;
				break;
			case "/궁합": case "/ㄱㅎ":
				try {
					val = loveTest(param1,param2);
				}catch(Exception e) {
					val = "/궁합 이름1 이름2 형태로 입력해주세요.(한글만)";
				}
				
				break;
			case "/시세":
				if (param1 != null && !param1.equals("")) {
					fulltxt = fulltxt.replace(param1, LoaApiUtils.switchWord(param1));
					param1 =LoaApiUtils.switchWord(param1);
					if(!LoaApiUtils.marketConditionYn(param1)) {
						return "각인서만 검색 가능";
					}
					fulltxt = fulltxt.substring(param0.length()).trim();
					reqMap.put("param1", fulltxt);
					String tmpVal ="";
					try {
						//val = supporters(param1);
						List<HashMap<String,Object>> list = botService.selectMarketCondition(reqMap);
						for(HashMap<String,Object> hs:list) {
							tmpVal += hs.get("DT")+" : "+hs.get("SISE")+enterStr;
						}
						if(tmpVal.equals("")) {
							return "시세 검색결과가 없습니다.";
						}
						
						val += tmpVal;
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}
				break;
			case "/분배금": case "/ㅂㅂㄱ":
				if (param1 != null && !param1.equals("")) {
					int int_parama1=0;
					try {
						int_parama1 = Integer.parseInt(param1);
						
						try {
							val  = checkDividend(int_parama1,8);
							val += checkDividend(int_parama1,16);
							val += checkDividend(int_parama1,4);
						} catch (Exception e) {
							val = errorCodeMng(e,reqMap);
						}
					} catch (Exception e) {
						val = "숫자를 넣어주세요!";
					}
					
				}
				val += enterStr;
				break;
			case "/골드": case "/ㄱㄷ": case "/클골": case "/ㅋㄱ":
				val  = checkGoldList();
				val += enterStr;
				val += enterStr;
				val += "http://rgb-tns.dev-apc.com/in/202409";
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
							val += enterStr + enterStr+"내일의 모험섬 더보기..▼"+allSeeStr;
							val += calendarSearch(1);
							val += enterStr;
							val += tossAccount();
						}
						break;
					default:
						val  = calendarSearch(0);
						val += enterStr + enterStr+"내일의 모험섬 더보기..▼"+allSeeStr;
						val += calendarSearch(1);
						val += enterStr;
						val += tossAccount();
						break;
				}
				
				
				break;
				
			case "/장비":
			case "/정보":
			case "/ㅈㅂ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅈㅂ";
					param1 = param1.trim();
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						//val = supporters(param1);
						val = newnewEquipSearch(param1);
						val+= tossAccount2();
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= enterStr;
							val+= hs.get("RES");
						}
					}
				}
				break;
			
			case "/초월": case "/엘릭서":
			case "/ㅊㅇ": case "/ㅇㄹㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅊㅇ";
					param1 = param1.trim();
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supporters(param1);
						val+= limitSearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= hs.get("RES");
						}
					}
				}
				break;
			case "/내실":
			case "/ㄴㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㄴㅅ";
					param1 = param1.trim();
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supporters(param1);
						val+= collectionSearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= hs.get("RES");
						}
					}
				}
				break;
			case "/ㄱㅁㅈ":
			case "/경매장":
				if(param1==null || param1.equals("")) {
					param0="/ㄱㅁㅈ";
					org_fulltxt = param0;
					val  = newMarketSearch();
					return val;
				}
				switch(param1) {
					case "유물":
					case "ㅇㅁ":
						param0="/ㄱㅁㅈㅇㅁ";
						org_fulltxt = param0;
						try {
							val ="[유물각인서 시세조회]";
							val += marketEngrave();
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
					case "전설":
					case "ㅈㅅ":
						param0="/ㄱㅁㅈㅈㅅ";
						org_fulltxt = param0;
						try {
							val = marketSearch(400002);
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						
						break;
					case "악세":
					case "ㅇㅅ":
						param0="/ㄱㅁㅈㅇㅅ";
						org_fulltxt = param0;
						try {
							val = marketTier4accessorySearch();
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
					case "4":
						param0="/ㄱㅁㅈ4";
						org_fulltxt = param0;
						try {
							val = marketSearch(4);
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
					case "3":
						param0="/ㄱㅁㅈ3";
						org_fulltxt = param0;
						try {
							val = marketSearch(3);
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
					default:
						param0="/ㄱㅁㅈ";
						org_fulltxt = param0;
						try {
							val  = newMarketSearch();
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
				}
				
				break;
			case "/ㄱㅁㅈ3":
			case "/경매장3":
				param0="/ㄱㅁㅈ3";
				org_fulltxt = param0;
				try {
					val = marketSearch(3);
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;
			case "/ㄱㅁㅈ4":
			case "/경매장4":
				param0="/ㄱㅁㅈ4";
				org_fulltxt = param0;
				try {
					val = marketSearch(4);
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;
			case "/ㄱㅁㅈㅇㅅ":
			case "/경매장악세":
				param0="/ㄱㅁㅈㅇㅅ";
				org_fulltxt = param0;
				try{
					val = marketTier4accessorySearch();
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;
			case "/ㄱㅁㅈㅇㅁ":
			case "/ㄱㅁㅈ유물":
			case "/경매장유물":
				param0="/ㄱㅁㅈㅇㅁ";
				org_fulltxt = param0;
				try{
					val ="[유물각인서 시세조회]";
					val += marketEngrave();
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;
			case "/경매장전설":
			case "/ㄱㅁㅈㅈㅅ":
				param0="/ㄱㅁㅈㅈㅅ";
				org_fulltxt = param0;
				try{
					val = marketSearch(400002);
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;	
			case "/악세":
			case "/ㅇㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅇㅅ";
					param1 = param1.trim();
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supporters(param1);
						val+= accessorySearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= hs.get("RES");
						}
					}
				}
				break;		
			case "/부캐":
			case "/ㅂㅋ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅂㅋ";
					param1 = param1.trim();
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supporters(param1);
						val+= subCharacterInfoSearch(param1);
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= hs.get("RES");
						}
					}
				}
				break;
			case "/전투력":
			case "/ㅈㅌㄹ":
				param0="/ㅈㅌㄹ";
				param1 = param1.trim();
				fulltxt = param0+" "+param1;
				org_fulltxt = fulltxt;
				reqMap.put("fulltxt", fulltxt);
				if (param1 != null && !param1.equals("")) {
					try {
						//val = "v0.3으로 패치중입니다.";
						
						val = supporters(param1);
						val+= sumTotalPowerSearch(param1);
						val+= tossAccount2();
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
					}
				}
				break;
			case "/항협": case "/항해": case "/항해협동": case "/ㅎㅎ":
				val = shipSearch();
				break;
			case "/가방": case "/ㄱㅂ":
				val = openBox(param1,param2);
				break;
			case "/날씨": case "/ㄴㅆ":
				if (param1 != null && !param1.equals("")) {
					val = weatherSearch(param1);
				}
				break;
			case "/점메추":
				String[] menu_list2 = {"칼국수","샐러드","고구마","굶기","점심회식-부장님은 짜장면드신데","콩국수","된장찌개","순대국","스테이크덮밥",
						"오징어덮밥","떡볶이","편의점도시락","콩나물불고기","라면","햄버거","부대찌개","돈까스","제육덮밥","닭갈비","닭도리탕","김치돈까스나베",
						"쌀국수", "김치", "물", "수제비", "카레", "떡국", "라멘","텐동","유린기"};
				Random random2 = new Random();
				val = menu_list2[random2.nextInt(menu_list2.length)];
				
				break;
			case "/저메추":
				String[] menu_list = { "피자", "탕수육", "치킨", "샐러드", "마라탕", "양꼬치", "삼겹살", "설렁탕", "김치찌개", "된장찌개", "삼치튀김", "참치마요",
						"회", "육회비빔밥", "냉면", "카레", "돈까스", "제육볶음", "오징어볶음", "떡볶이", "굶기", "초밥", "햄버거", "짜장면", "빵", "파스타", "닭발",
						"쭈꾸미", "낙지덮밥", "라면", "짜계치", "스팸과 흰밥", "간장계란밥", "간장게장", "참치회", "죽", "흰밥", "감자탕", "육전", "짬뽕", "순두부찌개",
						"지코바 양념치킨 모짜치즈 추가","닭가슴살", "단백질 음료", "바나나", "포케", "요아정", "홍콩반점 짜장밥", "호두과자", "라멘", "곱창.막창.대창", "소주", 
						"텐동", "유린기", "물회", "이베리코"};
				Random random = new Random();
				val = menu_list[random.nextInt(menu_list.length)];

				break;
			case "/챗":
				fulltxt = fulltxt.substring(param0.length()).trim();
				val = chatGptSearch(fulltxt,sender);
				break;
			case "/공지":
				List<String> list =botService.selectRoomList(reqMap);
				
				String tmp_val ="";
				
				for(int i=0;i<list.size();i++) {
					if(i!=0) {
						tmp_val += listSeparatorStr;
					}
					tmp_val += list.get(i);
				}
				
				if(tmp_val.equals("")) {
					return "";
				}
				//tmp_val  = param1+tmp_val;
				
				/**  
				  res = room1㉾room2
				  
				  if(res.indexOf(listSeparatorStr)){
				  	var room_list = res.split("㉾")
				    //room_list[0] =room1
				    //room_list[1] =room2
				    
				    for (room:room_list){ 
					  Api.replyRoom(room, 공지내용);
					}
				    
				  }else{
				   원래로직
				  }
				  =>
				  
				  
				 **/
				
				val = tmp_val;
				break;
			case "/단어등록": case "/단어추가":

				try {
					if (fulltxt.indexOf("=") < 0) {
						val = "단어등록 실패!, =을 포함해주세요";
					} else {

						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "단어등록 실패!, 특수문자 안되요!";
								return val;
							}
						}

						String[] txtList;
						fulltxt = fulltxt.substring(param0.length()).trim();
						txtList = fulltxt.split("=");
						reqMap.put("req", txtList[0].trim());
						reqMap.put("res", txtList[1].trim());

						botService.insertBotWordSaveTx(reqMap);
						val = "단어등록 완료!";
					}
				} catch (Exception e) {
					val = "단어등록 실패!";
				}
				break;
			case "/단어초기화":
				masterYn = botService.selectBotWordSaveMasterCnt(reqMap);
				if (masterYn > 0) {
					botService.deleteBotWordSaveAllDeleteMasterTx(reqMap);
					val = "단어초기화 완료!";
				} 
				break;
			case "/단어제거": case "/단어삭제":
				try {
					if (fulltxt.indexOf("=") < 0) {
						val = "단어삭제 실패!, =을 포함해주세요";
					} else {
						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "단어등록 실패!, 특수문자 안되요!";
								return val;
							}
						}
						String[] txtList;
						fulltxt = fulltxt.substring(param0.length()).trim();
						txtList = fulltxt.split("=");
						reqMap.put("req", txtList[0]);
						reqMap.put("res", txtList[1]);

						masterYn = botService.selectBotWordSaveMasterCnt(reqMap);

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
			case "/명령어": case "/람쥐봇": case "/ㄹㅈㅂ":
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
				
				val += tossAccount2();
				passYn=true;
				break;
			default:
				val = botService.selectBotWordSaveOne(reqMap);
				break;
			}
			
		}catch(Exception e){
			e.printStackTrace();
			System.out.println("로그저장실패 테스트");
		}finally {
			if(!passYn) {
				reqMap.put("req", org_fulltxt);
				reqMap.put("res", val);
				botService.insertBotWordHisTx(reqMap);
			}
				
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

		val = "rgb-tns.dev-apc.com/im/" + randKey;

		return val;
	}

	String errorCodeMng(Exception e,HashMap<String, Object> map) {
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
			case "E0004":
				val = "API사이트 연결 오류(로아사이트오류)";
				break;
			default:
				val = "확인되지않은 에러발생..지속시 개발자 개인톡문의부탁드려요!";
				e.printStackTrace();
				break;
			}
		}else {
			val = "확인되지않은 에러발생...지속시 개발자 개인톡문의부탁드려요!";
			e.printStackTrace();
		}
		map.put("issue_yn", "1");
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
	
	String checkDividend(int gold,int person) throws Exception {
		String msg = "";
		int susuro = (int) Math.floor(gold*0.05);
		int dividend = (int) Math.floor(gold/(person-1));
		int dividend1 = (int)Math.floor( (gold-susuro)*((double)(person-1)/(double)person) );
		int dividend2 = (int)Math.floor(dividend1/1.1);
		//int dividend3 = (int)Math.floor(dividend2/1.1);
		
		msg += gold +" 분배금 ("+ person +"인)"+enterStr;
		msg += "수수료: -"  +susuro+"G"+enterStr;
		msg += "분배금: "  +dividend+"G"+enterStr;
		//msg += "손익분기점: -"+dividend1+"G"+enterStr;
		msg += "입찰적정가: "+dividend2+"G"+enterStr;
		//msg += "그냥줘: -"   +dividend3+"G"+enterStr;
		msg += enterStr;
		
		return msg;
	}
	
	String checkGoldList() throws Exception {
		String msg = "";
		msg += "시즌3 골드획득정보..";
		msg += enterStr + "2막아브 [하 30,500G/노 25,000G]";
		msg += enterStr + "에기르 [하 27,500G/노 23,000G]";
		msg += enterStr + "베히모스 21,500G";
		msg += enterStr + "에키드나[하 18,500G/노 14,500G]";
		msg += enterStr + "카멘 1-3[하 20,000G/노 13,000G]";
		msg += enterStr + "카멘 4[하 21,000G]";
		msg += enterStr + "상아탑 [하 13,000G/노 6,500G]";
		msg += enterStr + "일리아칸 [하 8,500G/노 5,400G]";
		msg += enterStr ;
		/*
		msg += enterStr + "싱글모드..▼ "+ allSeeStr;
		msg += enterStr + "발탄 600G";
		msg += enterStr + "비아키스 800G";
		msg += enterStr + "쿠크 1,400G";
		msg += enterStr + "아브 1,700G(+900G)";
		msg += enterStr + "카양겔 1,700G";
		msg += enterStr + "일리아칸 2,800G";
		msg += enterStr + "상아탑 3,500G";
		msg += enterStr;
		 */
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
			retMsg= "오늘의 모험 섬";
			today = LoaApiUtils.StringToDate();
		}else {
			retMsg= "내일의 모험 섬";
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
	String miniLimitSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		/*String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment";
		String returnData ="";
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);	
		}catch(Exception e) {
			throw new Exception("E0004");
		}*/
		
		List<Map<String, Object>> armoryEquipment = null;
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
		}
		
		
		//HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		
		if(rtnMap ==null) return "초월합 : 엘릭서합 : 0" ;
		/*List<Map<String, Object>> armoryEquipment;
		try {
			
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
			e.printStackTrace();
			throw new Exception("E0003");
			
		}*/
		
		List<String> equipElixirList = new ArrayList<>();
		
		String totLimit ="";
		int totElixir =0;
		
		for (Map<String, Object> equip : armoryEquipment) {
			HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
			HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
			HashMap<String, Object> limit_element = (HashMap<String, Object>)maps.get("limit_element");
			HashMap<String, Object> elixir_element = (HashMap<String, Object>)maps.get("elixir_element");
			
			switch (equip.get("Type").toString()) {
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
				//엘릭서 정보 출력 
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList,elixir_element);
				break;
				default:
				continue;
			}
		}
		
		
		return "초월합 : " + totLimit + " 엘릭서합 : " + totElixir;
	}
	
	String newnewEquipSearch(String userId) throws Exception{
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId;// + "?filters=equipment%2Bprofiles";
		String returnData;
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);
		}catch(Exception e){
			System.out.println(ordUserId+" newnewEquipSearch "+e.getMessage());
			throw new Exception("E0004");
		}
		
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		List<Map<String, Object>> armoryEquipment;
		Map<String, Object> armoryProfile;
		Map<String, Object> armoryEngraving;
		Map<String, Object> armoryGem;
		List<Map<String, Object>> armoryAvatars;
		
		
		String avatarsText="";
		
		try {
			armoryProfile = (Map<String, Object>) rtnMap.get("ArmoryProfile");
		}catch(Exception e){
			System.out.println(userId+" ArmoryProfile");
			throw new Exception("E0003");
		}
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
			System.out.println(userId+" armoryEquipment");
			throw new Exception("E0003");
		}
		try {
			armoryEngraving = (Map<String, Object>) rtnMap.get("ArmoryEngraving");
		}catch(Exception e){
			System.out.println(userId+" ArmoryEngraving");
			throw new Exception("E0003");
		}
		try {
			armoryGem = (Map<String, Object>) rtnMap.get("ArmoryGem");
		}catch(Exception e){
			System.out.println(userId+" ArmoryGem");
			throw new Exception("E0003");
		}
		
		try {
			armoryAvatars = (List<Map<String, Object>>) rtnMap.get("ArmoryAvatars");
			avatarsText +="아바타 정보"+enterStr;
			for(Map<String,Object> avatar:armoryAvatars) {
				avatarsText += avatar.get("Type")+" : "+avatar.get("Name")+enterStr;
			}
			avatarsText += enterStr;
		}catch(Exception e){
			avatarsText ="";
		}
		
		List<String> equipSetList = new ArrayList<>();
		List<String> equipElixirList = new ArrayList<>();
		
		
		
		String resMsg = "";

		String resField1 = "";
		String resField2 = "";
		String resField3 = "";
		
		
		String characterImage ="";
		if(armoryProfile.get("CharacterImage") != null) {
			characterImage = armoryProfile.get("CharacterImage").toString();
		}
		
		//템/전/원
		String itemMaxLevel = armoryProfile.get("ItemMaxLevel").toString();
		String characterLevel = armoryProfile.get("CharacterLevel").toString();
		String expeditionLevel = armoryProfile.get("ExpeditionLevel").toString();
		String className = armoryProfile.get("CharacterClassName").toString();

		String title = "";
		if(armoryProfile.get("Title")!=null) {
			title=armoryProfile.get("Title").toString();
		}

		//공/생
		String atk ="";
		String life="";
		List<HashMap<String,Object>> stats = (List<HashMap<String, Object>>) armoryProfile.get("Stats");
		for(HashMap<String,Object> stat :stats) {
			switch(stat.get("Type").toString()) {
				case "최대 생명력":
					life = stat.get("Value").toString();
					break;
				case "공격력":
					atk = stat.get("Value").toString();
					break;
			}
			 
		}
		
		//엘/초
		String totLimit ="";
		int totElixir =0;
		
		String abillityStoneMsg = "";
		String accessoryMsg = "";
		String braceletMsg = "";
		
		HashMap<String,Object> arkPassive= (HashMap<String, Object>) rtnMap.get("ArkPassive");
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
			HashMap<String, Object> ark_passive_point_element = (HashMap<String, Object>)maps.get("ark_passive_point_element");
			HashMap<String, Object> bracelet_element = (HashMap<String, Object>)maps.get("bracelet_element");
			HashMap<String, Object> stone_element = (HashMap<String, Object>)maps.get("stone_element");
			HashMap<String, Object> grinding_element = (HashMap<String, Object>)maps.get("grinding_element");
			HashMap<String, Object> tier3_stats = (HashMap<String, Object>)maps.get("tier3_stats");
			
			switch (equip.get("Type").toString()) {
			case "무기":
			case "투구": case "상의": case "하의": case "장갑": case "어깨":
				
				String setFind = Jsoup.parse((String) weapon_element.get("value")).text();
				for(String set:LoaApiParser.getSetList()) {
					if(setFind.indexOf(set) >= 0) {
						equipSetList.add(set);
					}
				}
				
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
				
				if(!equip.get("Type").toString().equals("무기")) {
					if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text().indexOf("티어 4")>0) {
						try {
							resField1 += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						}catch (Exception e) {
							System.out.println("ark_passive_point_element:: "+ark_passive_point_element);
						}
						
					}
				}
				resField1 += enterStr;
				
				resField2 += equip.get("Type").toString()+" :";//초
				resField2 += LoaApiParser.parseLimitForLimit(limit_element);
				resField2 = LoaApiUtils.filterText(resField2);
				resField2 += enterStr;
				
				if(!equip.get("Type").toString().equals("무기")) {
					resField3 += equip.get("Type").toString()+" :";//엘
					resField3 += LoaApiParser.parseElixirForLimit(null,elixir_element,0);
					resField3 += enterStr;
				}
				
				//초월
				//초월합계는 장비에서가져옴 
				String tmpLimit = LoaApiParser.parseLimit(limit_element);
				if(!tmpLimit.equals("")) {
					totLimit = tmpLimit;
				}
				//엘릭서
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList, elixir_element);
				break;
			case "어빌리티 스톤":
				HashMap<String, Object> stone_val = (HashMap<String, Object>) stone_element.get("value");
				if(stone_val == null || stone_val.size() ==0 ) {
					continue;
				}
				HashMap<String, Object> stone_option = (HashMap<String, Object>) stone_val.get("Element_000");
				HashMap<String, Object> stone_option0 = (HashMap<String, Object>) stone_option.get("contentStr");
				HashMap<String, Object> stone_option1 = (HashMap<String, Object>) stone_option0.get("Element_000");
				HashMap<String, Object> stone_option2 = (HashMap<String, Object>) stone_option0.get("Element_001");
				
				abillityStoneMsg += equip.get("Name");
				abillityStoneMsg += enterStr;
				String stone_option1_str = Jsoup.parse(stone_option1.get("contentStr").toString()).text();
				String stone_option2_str = Jsoup.parse(stone_option2.get("contentStr").toString()).text();
				
				int len = 0;
				
				stone_option1_str = stone_option1_str.replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
				len = stone_option1_str.length();
				stone_option1_str = stone_option1_str.substring(0,1)+stone_option1_str.substring(len-1,len);

				stone_option2_str = stone_option2_str.replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
				len = stone_option2_str.length();
				stone_option2_str = stone_option2_str.substring(0,1)+stone_option2_str.substring(len-1,len);
				
				abillityStoneMsg += stone_option1_str + " " + stone_option2_str +enterStr;
				break;
			case "반지":case "귀걸이": case "목걸이":
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
					case "아이템 티어 3":
						accessoryMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						accessoryMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						accessoryMsg += enterStr;
						accessoryMsg += " "+Jsoup.parse((String)((HashMap<String, Object>) tier3_stats.get("value")).get("Element_001")).text();
						accessoryMsg += enterStr;
						break;
					case "아이템 티어 4":
						accessoryMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						accessoryMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						accessoryMsg += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						accessoryMsg += enterStr;
						accessoryMsg += LoaApiParser.findBraceletOptions(1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
						accessoryMsg += enterStr;
						break;
				}
				break;
			case "팔찌":
				braceletMsg += "팔찌 정보"+enterStr;
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				braceletMsg += LoaApiParser.findBraceletOptions(0,bracelet.get("Element_001").toString());
				
				braceletMsg += enterStr;
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
		
		String arkpoint1="0";
		String arkpoint2="0";
		String arkpoint3="0";
		for(HashMap<String,Object> pt:arkPassivePt) {
			switch(pt.get("Name").toString()) {
			case "진화":
				arkpoint1=pt.get("Value").toString();
				break;
			case "깨달음":
				arkpoint2=pt.get("Value").toString();
				break;
			case "도약":
				arkpoint3=pt.get("Value").toString();
				break;
			}
		}
		
		if(totLimit.equals("")) {
			totLimit="0";
		}
		
		
		int tier = 3;
		if(Double.parseDouble(itemMaxLevel.replaceAll(",", ""))>=1640) {
			tier = 4;
		}
		if(!characterImage.equals("")) {
			resMsg += charImgSearch(ordUserId,title,className,characterImage) + anotherMsgStr;
		}
		resMsg += "레벨"    +"　　　 　"+itemMaxLevel+enterStr;
		resMsg += "전투/원대"+"　　"+characterLevel+"　/　"+expeditionLevel+enterStr;
		resMsg += "엘릭/초월"+"　　"+totElixir+"(" + elixirField+")"+" / "+totLimit+enterStr;
		resMsg += "공격/최생"+"　　"+atk+" / "+life+enterStr;
		//resMsg += "진/깨/도"+"　 　"+arkpoint1+" / "+arkpoint2+" / "+arkpoint3+enterStr;
		
		if(tier ==4) {
			resMsg += "아크패시브"+"　 "+"진:"+arkpoint1+" /"+"깨:"+arkpoint2+" /"+"도:"+arkpoint3+enterStr;
		}
		
		resMsg += newGemSearch(armoryGem,ordUserId, tier);
		if(isArkPassive.equals("true")) {
			resMsg += newEngraveSearch(armoryEngraving,ordUserId,true,true);	
		}else {
			//id,arkPassive,simpleMode
			resMsg += newEngraveSearch(armoryEngraving,ordUserId,false,true);
		}
		
		resMsg += enterStr;
		resMsg += "상세 더보기..▼"+allSeeStr;
		//resMsg += "방어구 / 초월 / 엘릭서"+enterStr;
		
		resMsg += "§세트 : "+setField + enterStr;
		resMsg += resField1 + enterStr;
		
		resMsg +=abillityStoneMsg+ enterStr;
		resMsg +=accessoryMsg + enterStr;
		resMsg +=braceletMsg + enterStr;
		
		if(totLimit.equals("")) {
			resMsg += "§초월 : 없음" + enterStr;
		}else {
			resMsg += "§초월합 : " + totLimit + enterStr;
			resMsg += resField2 + enterStr;
		}
		
		if(totElixir==0) {
			resMsg += "§엘릭서 : 없음" + enterStr;
		}else {
			resMsg += "§엘릭서합 : " + totElixir + "(" + elixirField+")" + enterStr;;
			resMsg += resField3 + enterStr;
		}
		
		//아바타 정보 
		resMsg +=avatarsText;
		
		if(Double.parseDouble(itemMaxLevel.replaceAll(",", "")) >= 1600) {
			if(isArkPassive.equals("true")) {
				resMsg +="§아크패시브 : 사용"+enterStr;
			}else {
				resMsg +="§아크패시브 : 미사용"+enterStr;
			}
			
			if(isArkPassive.equals("true")) {
				resMsg += newEngraveSearch(armoryEngraving,ordUserId,true,false);
				resMsg +=enterStr;
			}
			//
			resMsg +="§아크패시브 : 포인트"+enterStr;
			for(HashMap<String,Object> pt:arkPassivePt) {
				resMsg +=pt.get("Name")+" : " +pt.get("Value"); 
				
				switch(pt.get("Name").toString()) {
					case "진화":
						resMsg +=" / 120";
						break;
					case "깨달음":
						resMsg +=" / 101";
						break;
					case "도약":
						resMsg +=" / 70";
						break;
				}
				resMsg +=enterStr;
			}
			if(isArkPassive.equals("true")) {
				resMsg +=enterStr;
				List<HashMap<String,Object>> arkPassiveEffects = (List<HashMap<String, Object>>) arkPassive.get("Effects");
				for(HashMap<String, Object> effect : arkPassiveEffects) {
					resMsg += Jsoup.parse((String) effect.get("Description")).text() + enterStr;
				}
			}
		}
		
		return resMsg;
	}
	
	String newAccessorySearch(Map<String,Object> rtnMap,String userId) throws Exception {
		String ordUserId=userId;
		
		List<Map<String, Object>> armoryEquipment;
		
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
			throw new Exception("E0003");
		}
		
		String resMsg = ordUserId+ " 악세정보"+enterStr;
		String resMsg2="";
		boolean resMsg2Ok=false;

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
			HashMap<String, Object> grinding_element = (HashMap<String, Object>)maps.get("grinding_element");
			HashMap<String, Object> ark_passive_point_element = (HashMap<String, Object>)maps.get("ark_passive_point_element");
			HashMap<String, Object> tier3_stats = (HashMap<String, Object>)maps.get("tier3_stats");
			
			switch (equip.get("Type").toString()) {
			case "어빌리티 스톤":
				HashMap<String, Object> stone_val = (HashMap<String, Object>) stone_element.get("value");
				if(stone_val == null || stone_val.size() ==0 ) {
					continue;
				}
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
				break;
			case "반지":case "귀걸이": case "목걸이":
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
					case "아이템 티어 3":
						resMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						resMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						resMsg += enterStr;
						resMsg += " "+Jsoup.parse((String)((HashMap<String, Object>) tier3_stats.get("value")).get("Element_001")).text();
						resMsg += enterStr;
						break;
					case "아이템 티어 4":
						resMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						resMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						resMsg += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						resMsg += enterStr;
						resMsg += LoaApiParser.findBraceletOptions(1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
						//resMsg += " "+Jsoup.parse((String) ((HashMap<String, Object>) grinding_element.get("value")).get("Element_001")).text();
						resMsg += enterStr;
						resMsg2Ok = true;
						break;
				}
				break;
			case "팔찌":
				resMsg += "팔찌 정보"+enterStr;
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				resMsg += LoaApiParser.findBraceletOptions(0,bracelet.get("Element_001").toString());
				
				resMsg += enterStr;
				//resMsg += "상세 더보기..▼"+allSeeStr;
				//String braceletDt = Jsoup.parse(bracelet.get("Element_001").toString().replace("<BR>", enterStr)).text();
				//resMsg += braceletDt;
				break;
			default:
			continue;
			}
		}

		return resMsg;
	}
	
	
	String accessorySearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment%2Bprofiles";
		
		String returnData ="";
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);	
		}catch(Exception e) {
			e.printStackTrace();
			throw new Exception("E0004");
		}
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
		String resMsg2="";
		boolean resMsg2Ok=false;

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
			HashMap<String, Object> grinding_element = (HashMap<String, Object>)maps.get("grinding_element");
			HashMap<String, Object> ark_passive_point_element = (HashMap<String, Object>)maps.get("ark_passive_point_element");
			HashMap<String, Object> tier3_stats = (HashMap<String, Object>)maps.get("tier3_stats");
			
			switch (equip.get("Type").toString()) {
			/*case "어빌리티 스톤":
				
				HashMap<String, Object> stone_val = (HashMap<String, Object>) stone_element.get("value");
				if(stone_val == null || stone_val.size() ==0 ) {
					continue;
				}
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
				break;
				*/
			case "반지":case "귀걸이": case "목걸이":
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
					case "아이템 티어 3":
						resMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						resMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						resMsg += enterStr;
						resMsg += " "+Jsoup.parse((String)((HashMap<String, Object>) tier3_stats.get("value")).get("Element_001")).text();
						resMsg += enterStr;
						break;
					case "아이템 티어 4":
						resMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						resMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						resMsg += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						resMsg += enterStr;
						resMsg += LoaApiParser.findBraceletOptions(1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
						//resMsg += " "+Jsoup.parse((String) ((HashMap<String, Object>) grinding_element.get("value")).get("Element_001")).text();
						resMsg += enterStr;
						resMsg2Ok = true;
						break;
				}
				break;
			case "팔찌":
				resMsg += "팔찌 정보"+enterStr;
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				resMsg += LoaApiParser.findBraceletOptions(0,bracelet.get("Element_001").toString());
				
				resMsg += enterStr;
				//resMsg += "상세 더보기..▼"+allSeeStr;
				//String braceletDt = Jsoup.parse(bracelet.get("Element_001").toString().replace("<BR>", enterStr)).text();
				//resMsg += braceletDt;
				break;
			default:
			continue;
			}
		}

		return resMsg;
	}
	
	
	
	String newEngraveSearch(Map<String,Object> rtnMap,String userId,boolean arkPassiveYn,boolean simpleYn) throws Exception {
		String resMsg="";
		List<Map<String, Object>> engraves;
		
		try {
			if(arkPassiveYn) {
				engraves = (List<Map<String, Object>>) rtnMap.get("ArkPassiveEffects");
			}else {
				engraves = (List<Map<String, Object>>) rtnMap.get("Effects");
			}
			
		}catch(Exception e){
			//throw new Exception("E0003");
			return enterStr+"각인 : 정보 없음";
		}
		
		List<String> engraveList = new ArrayList<>();
		
		if(simpleYn) {
			for (Map<String, Object> engrave : engraves) {
				int len = engrave.get("Name").toString().length();
				String tmpEng = engrave.get("Name").toString().substring(0,1);
				engraveList.add(tmpEng);
			}
			resMsg = resMsg + "각인"+"　　　　"+ engraveList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","")+enterStr;
		}else {
			String passiveEffect="";
			for (Map<String, Object> engrave : engraves) {
				passiveEffect +=engrave.get("Grade")+" Lv"+engrave.get("Level")+" "+engrave.get("Name");
				if(engrave.get("AbilityStoneLevel")!=null) {
					passiveEffect +="(돌 +"+engrave.get("AbilityStoneLevel")+")" ;
				}
				passiveEffect +=enterStr;
			}
			resMsg = resMsg + enterStr+"§아크패시브-각인"+enterStr +passiveEffect;
		}
		return resMsg;
	}
	
	
	String newGemSearch(Map<String,Object> rtnMap,String userId,int tier) throws Exception {
		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			return "멸/홍"+" "+enterStr;
		}
		if(gems == null) {
			return "멸/홍"+" "+enterStr;
		}
		if(gems.equals("null")) {
			return "멸/홍"+" "+enterStr;
		}
		
		String resMsg = "";
		for (Map<String, Object> gem : gems) {
			String gemName = Jsoup.parse((String) gem.get("Name")).text();
			for(String equipGem : gemList) {
				if( gemName.indexOf(equipGem)>=0 ) {
					if(tier ==3) {
						if(equipGem.equals(gemList[0])) {
							equipGemT3DealList.add((int)gem.get("Level"));
						}else if(equipGem.equals(gemList[1])) {
							equipGemT3CoolList.add((int)gem.get("Level"));
						}
					}else if(tier==4){
						if(equipGem.equals(gemList[0])) {
							equipGemT4DealList.add((int)gem.get("Level")-2);
						}else if(equipGem.equals(gemList[1])) {
							equipGemT4CoolList.add((int)gem.get("Level")-2);
						}else if(equipGem.equals(gemList[2])) {
							equipGemT4DealList.add((int)gem.get("Level"));
						}else if(equipGem.equals(gemList[3])) {
							equipGemT4CoolList.add((int)gem.get("Level"));
						}
					}
				}
			}
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		if(tier ==3) {
			resMsg += "멸/홍(3T)"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+" / "+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}else if(tier ==4) {
			resMsg += "겁/작(4T)"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+" / "+tmpMsg4,29,spaceStr);
			resMsg += enterStr;
		}
		return resMsg;
	}
	
	String miniGemSearch(String userId,int tier) throws Exception {
		
		
		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			return enterStr;
		}
		if(gems == null) {
			return enterStr;
		}
		if(gems.equals("null")) {
			return enterStr;
		}
		
		String resMsg = "";
		for (Map<String, Object> gem : gems) {
			String gemName = Jsoup.parse((String) gem.get("Name")).text();
			for(String equipGem : gemList) {
				if( gemName.indexOf(equipGem)>=0 ) {
					if(tier ==3) {
						if(equipGem.equals(gemList[0])) {
							equipGemT3DealList.add((int)gem.get("Level"));
						}else if(equipGem.equals(gemList[1])) {
							equipGemT3CoolList.add((int)gem.get("Level"));
						}
					}else if(tier==4){
						if(equipGem.equals(gemList[0])) {
							equipGemT4DealList.add((int)gem.get("Level")-2);
						}else if(equipGem.equals(gemList[1])) {
							equipGemT4CoolList.add((int)gem.get("Level")-2);
						}else if(equipGem.equals(gemList[2])) {
							equipGemT4DealList.add((int)gem.get("Level"));
						}else if(equipGem.equals(gemList[3])) {
							equipGemT4CoolList.add((int)gem.get("Level"));
						}
					}
				}
			}
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		if(tier ==3) {
			resMsg += "멸/홍"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+" / "+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}else if(tier ==4) {
			resMsg += "겁/작"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+" / "+tmpMsg4,29,spaceStr);
			resMsg += enterStr;
		}
		return resMsg;
	}
	
	String miniGemCntSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		/*String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		String returnData ="";
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);	
		}catch(Exception e) {
			throw new Exception("E0004");
		}
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});
		 */
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			//throw new Exception("E0003");
			
			return null;
		}

		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		
		//List<Map<String, Object>> gems;
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
				if(gemLv < 5) {
					continue;
				}
				
				if( gemName.indexOf(equipGem)>=0 ) {
					if(equipGem.equals(gemList[0])) {
						if(gemLv < 8) {
							continue;
						}
						cnt++;
						equipGemT3DealList.add(gemLv);
					}else if(equipGem.equals(gemList[1])) {
						if(gemLv < 8) {
							continue;
						}
						cnt++;
						equipGemT3CoolList.add(gemLv);
					}else if(equipGem.equals(gemList[2])) {
						cnt++;
						equipGemT4DealList.add(gemLv);
					}else if(equipGem.equals(gemList[3])) {
						cnt++;
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
		
		if(equipGemT3DealList.size()+equipGemT3CoolList.size() > 0) {
			resMsg += "멸/홍"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+" / "+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}
		if(equipGemT4DealList.size()+equipGemT4CoolList.size() > 0) {
			resMsg += "겁/작"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+" / "+tmpMsg4,29,spaceStr);
			resMsg += enterStr;
		}
		return resMsg;
	}
	
	int totalEquipmentSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		//String ordUserId=userId;
		//userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		//String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment";
		//String returnData = LoaApiUtils.connect_process(paramUrl);
		//HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		List<Map<String, Object>> armoryEquipment = null;
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
		}
		
		List<String> equipElixirList = new ArrayList<>();

		int weaponLv = 0;
		
		for (Map<String, Object> equip : armoryEquipment) {
			HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
			HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
			HashMap<String, Object> weapon_element = (HashMap<String, Object>)maps.get("weapon_element");
			
			switch (equip.get("Type").toString()) {
			case "무기":
				weaponLv = Integer.parseInt(Jsoup.parse((String) weapon_element.get("value")).text().replaceAll("[^0-9]", ""));
				break;
			}
		}
		
		return weaponLv;
	}
	
	List<String> totalAccessorySearch(Map<String,Object> rtnMap,String userId,String className) throws Exception {
		//String ordUserId=userId;
		//userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		//String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "?filters=equipment";
		//String returnData = LoaApiUtils.connect_process(paramUrl);
		//HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		List<Map<String, Object>> armoryEquipment=null;
		
		try {
			armoryEquipment = (List<Map<String, Object>>) rtnMap.get("ArmoryEquipment");
		}catch(Exception e){
		}
		
		String resMsg ="";

		List<String> g1 = new ArrayList<>();
		try {
			for (Map<String, Object> equip : armoryEquipment) {
				HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) equip.get("Tooltip"),new TypeReference<Map<String, Object>>() {});
				HashMap<String, Object> maps = LoaApiParser.findElement(tooltip);
				HashMap<String, Object> quality_element = (HashMap<String, Object>)maps.get("quality_element");
				HashMap<String, Object> grinding_element = (HashMap<String, Object>)maps.get("grinding_element");
				
				switch (equip.get("Type").toString()) {
				
				case "반지":case "귀걸이": case "목걸이":
					switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
						case "아이템 티어 4":
							if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text().indexOf("고대")>=0 ) {
								
								switch (className) {
								case "바드":
								case "도화가":
								case "홀리나이트":
									g1.add(LoaApiParser.findBraceletOptions(3,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
									break;
								default:
									g1.add(LoaApiParser.findBraceletOptions(2,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
									break;
								}

							}
							break;
					}
					break;
				
				default:
				continue;
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
			return null;
		}
		
		
		return g1;
	}
	
	List<Map<String, Object>> totalEngraveSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		//String ordUserId=userId;
		//userId = URLEncoder.encode(userId, "UTF-8");
		//String resMsg="";
		//String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/engravings";
		//String returnData = LoaApiUtils.connect_process(paramUrl);
		//HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});
		
		List<Map<String, Object>> engraves;
		
		try {
			engraves = (List<Map<String, Object>>) rtnMap.get("ArkPassiveEffects");
		}catch(Exception e){
			return null;
		}
		
		List<String> engraveList = new ArrayList<>();
		//for (Map<String, Object> engrave : engraves) {
		//	passiveEffect +=engrave.get("Grade")+" Lv"+engrave.get("Level")+" "+engrave.get("Name");
		//}
		
		return engraves;
	}
	
	
	List<Integer> totalGemCntSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		//String ordUserId=userId;
		//userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		//String paramUrl = lostArkAPIurl + "/armories/characters/" + userId + "/gems";
		//String returnData = LoaApiUtils.connect_process(paramUrl);
		//HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});


		String[] gemList = {"멸화","홍염","겁화","작열"};
		List<Integer> equipGemT4List = new ArrayList<>();
		
		List<Map<String, Object>> gems;
		try {
			gems = (List<Map<String, Object>>) rtnMap.get("Gems");
		}catch(Exception e){
			//throw new Exception("E0003");
			
			return null;
		}
		if(gems == null) {
			return null;
		}
		if(gems.equals("null")) {
			return null;
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
					if(equipGem.equals(gemList[0])) {
						cnt++;
						equipGemT4List.add(gemLv-2);
					}else if(equipGem.equals(gemList[1])) {
						cnt++;
						equipGemT4List.add(gemLv-2);
					}else if(equipGem.equals(gemList[2])) {
						cnt++;
						equipGemT4List.add(gemLv);
					}else if(equipGem.equals(gemList[3])) {
						cnt++;
						equipGemT4List.add(gemLv);
					}
					
				}
			}
		}

		if(cnt==0) {
			return null;
		}
		
		//Collections.sort(equipGemT4List,Collections.reverseOrder());
		
		return equipGemT4List;
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

		double percent = 100.0/10.0;
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
	
	HashMap<String, Object> sumTotalPowerSearch2(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId;// + "?filters=equipment%2Bprofiles";
		String returnData;
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);
		}catch(Exception e){
			System.out.println(ordUserId+" sumTotalPowerSearch2 "+e.getMessage());
			throw new Exception("E0004");
		}
		
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		return rtnMap;
	}
	
	String sumTotalPowerSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+" 계정 전투력 정보 v0.6" + enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", "")) >= 1540)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String maxCharLv =sortedList.get(0).get("ItemMaxLevel").toString().replaceAll(",", "");
		int cntCharLv1680 =0;
		int cntWeaponLv25 =0;
		int cntWeaponLv24 =0;
		int cntWeaponLv23 =0;
		int cntWeaponLv22 =0;
		int cntWeaponLv21 =0;
		int cntWeaponLv20 =0;
		int cntEsther = 0;
		int cntGem7 =0;
		int cntGem8 =0;
		int cntGem9 =0;
		int cntGem10 =0;
		int cntEngrave4 =0;
		int cntEngrave3 =0;
		int cntEngrave2 =0;
		int cntEngrave1 =0;
		
		int gradeCnt =0;
		
		int gradeCnt_lv=0;
		int gradeCnt_subChar=0;
		int gradeCnt_weapon=0;
		int gradeCnt_gem=0;
		int gradeCnt_engrave=0;
		int gradeCnt_accessory=0;
		
		String grade ="모코코";
		
		List<Integer> weaponList = new ArrayList<>();
		ArrayList<Integer> gemList = new ArrayList<>();
		List<Map<String,Object>> engraveList = new ArrayList<>();
		List<String> accessoryList = new ArrayList<>();
		
		String charName = "";
		String charClassName ="";
		Double charLv=0.0;
		HashMap<String,Object> resMap =new HashMap<>();
		for(HashMap<String,Object> charList : sortedList) {
			charName = charList.get("CharacterName").toString();
			charLv =Double.parseDouble(charList.get("ItemMaxLevel").toString().replaceAll(",", "")); 
			charClassName = charList.get("CharacterClassName").toString();
			
			if(charLv < 1540) {
				continue;
			}
			
			if(charLv >= 1680){
				cntCharLv1680++;
			}
			
			
			
			resMap = sumTotalPowerSearch2(charName);
			List<Map<String, Object>> armoryEquipment;
			Map<String, Object> armoryProfile = new HashMap<>();
			Map<String, Object> armoryEngraving = new HashMap<>();
			Map<String, Object> armoryGem = new HashMap<>();
			
			try {
				armoryEquipment = (List<Map<String, Object>>) resMap.get("ArmoryEquipment");
			}catch(Exception e){
			}
			try {
				armoryEngraving = (Map<String, Object>) resMap.get("ArmoryEngraving");
			}catch(Exception e){
			}
			try {
				armoryGem = (Map<String, Object>) resMap.get("ArmoryGem");
			}catch(Exception e){
			}
			
			
			
			if(charLv >= 1680){
				weaponList.add(totalEquipmentSearch(resMap,charName));
			}
			
			List<Integer> charGem = totalGemCntSearch(armoryGem,charName);
			if(charGem !=null) {
				gemList.addAll(charGem);
			}
			
			
			List<Map<String,Object>> charEngrave = totalEngraveSearch(armoryEngraving,charName);
			if(charEngrave !=null) {
				engraveList.addAll(charEngrave);
			}
			
			List<String> acceossory = totalAccessorySearch(resMap,charName,charClassName);
			if(acceossory !=null) {
				accessoryList.addAll(acceossory);
			}
		}
		
		for(int weapon: weaponList) {
			switch(weapon) {
			case 25:
				cntWeaponLv25++;
				gradeCnt_weapon += 200+190+180+170+160+150;
				break;
			case 24:
				cntWeaponLv24++;
				gradeCnt_weapon += 190+180+170+160+150;
				break;
			case 23:
				cntWeaponLv23++;
				gradeCnt_weapon += 180+170+160+150;
				break;
			case 22:
				cntWeaponLv22++;
				gradeCnt_weapon += 170+160+150;
				break;
			case 21:
				cntWeaponLv21++;
				gradeCnt_weapon += 160+150;
				break;
			case 20:
				cntWeaponLv20++;
				gradeCnt_weapon += 150;
				break;
			case 9:
				gradeCnt_weapon += 3225;
			case 8:
				gradeCnt_weapon += 2320;
			case 7:
				gradeCnt_weapon += 1576;
			case 6:
				gradeCnt_weapon += 1327;
				cntEsther++;
				break;
			}
			
		}
		
		//Collections.sort(gemList,Collections.reverseOrder());
		
		cntGem10= Collections.frequency(gemList, 10);
		cntGem9 = Collections.frequency(gemList, 9);
		cntGem8 = Collections.frequency(gemList, 8);
		cntGem7 = Collections.frequency(gemList, 7);
		
		List<String> engrave_hash_chk = new ArrayList<>();
		String engrave_ment="";
		for (Map<String, Object> engrave : engraveList) {
			if(engrave.get("Grade").equals("유물")) {
				
				if(engrave_hash_chk.contains(engrave.get("Name").toString())) {
					continue;
				}
				engrave_hash_chk.add(engrave.get("Name").toString());
				
				int tmpgold = LoaApiUtils.totalGoldForEngrave(engrave.get("Name").toString(),engrave.get("Level").toString());
				gradeCnt_engrave +=tmpgold;
				
				switch(engrave.get("Level").toString()) {
					case "4": cntEngrave4++;
					engrave_ment+=" :"+engrave.get("Name").toString()+" "+engrave.get("Level").toString()+":"+tmpgold+enterStr;
					break;
					case "3": cntEngrave3++;
					engrave_ment+=" :"+engrave.get("Name").toString()+" "+engrave.get("Level").toString()+":"+tmpgold+enterStr;
					break;
					case "2": cntEngrave2++;
					engrave_ment+=" :"+engrave.get("Name").toString()+" "+engrave.get("Level").toString()+":"+tmpgold+enterStr;
					break;
					case "1": cntEngrave1++;
					engrave_ment+=" :"+engrave.get("Name").toString()+" "+engrave.get("Level").toString()+":"+tmpgold+enterStr;
					break;
				}
			}
			
			
		}
		
		
		
		
		resMsg += "최고레벨: " + maxCharLv + enterStr;
		Double lv = Double.parseDouble(maxCharLv);
		if(lv>=1680) {
			gradeCnt_lv += 200;
		}
		if(lv>=1690) {
			gradeCnt_lv += 70;
		}
		if(lv>=1700) {
			gradeCnt_lv += 100;
		}
		if(lv>=1710) {
			gradeCnt_lv += 130;
		}
		
		resMsg += "1680이상캐릭터수 : " +cntCharLv1680+ enterStr;
		if(cntCharLv1680>0) {
			gradeCnt_subChar += 200*(cntCharLv1680-1);
		}
		
		resMsg += "무기 : " ;
		if(cntWeaponLv25>0) {
			resMsg += "25:"+cntWeaponLv25+" ";
		}
		if(cntWeaponLv24>0) {
			resMsg += "24:"+cntWeaponLv24+" ";
		}
		if(cntWeaponLv23>0) {
			resMsg += "23:"+cntWeaponLv23+" ";
		}
		if(cntWeaponLv22>0) {
			resMsg += "22:"+cntWeaponLv22+" ";
		}
		if(cntWeaponLv21>0) {
			resMsg += "21:"+cntWeaponLv21+" ";
		}
		if(cntWeaponLv20>0) {
			resMsg += "20:"+cntWeaponLv20+" ";
		}
		if(cntEsther>0) {
			resMsg += "E:"+cntEsther+" ";
		}
		
		if(cntWeaponLv25 == 0 && cntWeaponLv24 ==0 && cntWeaponLv23 ==0 
		&& cntWeaponLv22 == 0 && cntWeaponLv21 ==0 && cntWeaponLv20 ==0
		&& cntEsther ==0) {
			resMsg += "강화 낮음";
		}
		
		resMsg += enterStr;
		resMsg += "보석: ";
		if(cntGem10>0) {
			resMsg += "10겁:"+cntGem10+" ";
			gradeCnt_gem += 230;//270 ~ 170 사이 적정가 6멸5홍기준 
		}
		if(cntGem9>0) {
			resMsg += "9겁:"+cntGem9+" ";
			gradeCnt_gem += 90;
		}
		if(cntGem8>0) {
			resMsg += "8겁:"+cntGem8+" ";
			gradeCnt_gem += 30*cntGem8;
		}
		if(cntGem7>0) {
			resMsg += "7겁:"+cntGem7+" ";
			gradeCnt_gem += 10*cntGem7;
		}
		
		if(cntGem10 == 0 && cntGem9 ==0 && cntGem8 ==0 && cntGem7 ==0 ) {
			resMsg += "장착 보석 없음!";
		}
		
		resMsg += enterStr;
		resMsg += "각인 : " ;
		
		if(cntEngrave4>0) {
			resMsg += "유각4:"+cntEngrave4+" ";
		}
		if(cntEngrave3>0) {
			resMsg += "유각3:"+cntEngrave3+" ";
		}
		if(cntEngrave2>0) {
			resMsg += "유각2:"+cntEngrave2+" ";
		}
		if(cntEngrave1>0) {
			resMsg += "유각1:"+cntEngrave1+" ";
		}
		
		if(cntEngrave4 == 0 && cntEngrave3 ==0 && cntEngrave2 ==0 && cntEngrave1 ==0 ) {
			resMsg += "장착 유각 없음!";
		}
		
		resMsg += enterStr;
		
		int g1ss = 0;
		int g1sj = 0;
		int g1sh = 0;
		int g1s = 0;
		int g1jj =0;
		int g1jh =0;
		int g1j =0;

		for (String g1 : accessoryList) {
			switch (g1) {
			case "상상":
				g1ss++;
				gradeCnt_accessory += 150;
				break;
			case "상중":
			case "중상":
				g1sj++;
				gradeCnt_accessory += 80;
				break;
			case "상하":
			case "하상":
				g1sh++;
				gradeCnt_accessory += 40;
				break;
			case "상":
				g1s++;
				gradeCnt_accessory += 25;
				break;
			case "중중":
				g1jj++;
				gradeCnt_accessory += 20;
				break;
			case "중하":
			case "하중":
				g1jh++;
				gradeCnt_accessory += 15;
				break;
			case "중":
				g1j++;
				gradeCnt_accessory += 10;
				break;
			}
		}

		resMsg += "악세 : " ;
		if(g1ss>0) {
			resMsg += "상상:"+g1ss+" ";
		}
		if(g1sj>0) {
			resMsg += "상중:"+g1sj+" ";
		}
		if(g1sh>0) {
			resMsg += "상하:"+g1sh+" ";
		}
		if(g1s>0) {
			resMsg += "상단일:"+g1s+" ";
		}
		
		if(g1jj>0) {
			resMsg += "중중:"+g1jj+" ";
		}
		if(g1jh>0) {
			resMsg += "중하:"+g1jh+" ";
		}
		if(g1j>0) {
			resMsg += "중단일:"+g1j+" ";
		}
		
		if(g1ss == 0 && g1sj ==0 && g1sh ==0 && g1s ==0 
				     &&g1jj == 0 && g1jh ==0 && g1j ==0) {
			resMsg += "특옵 없음!";
		}
		
		resMsg += enterStr;
		resMsg += enterStr;
		
		
		
		
		
		
		
		gradeCnt=gradeCnt_lv
				+gradeCnt_subChar
				+gradeCnt_weapon
				+gradeCnt_gem
				+gradeCnt_engrave
				+gradeCnt_accessory; 
		resMsg +="환산 비용: "+gradeCnt +"만 골드";
		
		
		if(gradeCnt>0) {
			grade="모코코";
		}
		if(gradeCnt>200) {
			grade="모코코와 중급자 사이 어딘가";
		}
		if(gradeCnt>500) {
			grade="중급자";
		}
		if(gradeCnt>1000) {
			grade="고인물";
		}
		if(gradeCnt>2000) {
			grade="슈퍼고인물";
		}
		if(gradeCnt>3000) {
			grade="일반인이 아님";
		}
		
		resMsg += enterStr;
		resMsg += "당신은 "+grade+" !!"+enterStr;
		resMsg += enterStr;
		resMsg += "약간의 상세 더보기"+allSeeStr;
		
		resMsg += "레벨 "+gradeCnt_lv+enterStr;
		resMsg += "부캐 "+gradeCnt_subChar+enterStr;
		resMsg += "무기 "+gradeCnt_weapon+enterStr;
		resMsg += "보석 "+gradeCnt_gem+enterStr;
		resMsg += "악세 "+gradeCnt_accessory+enterStr;
		resMsg += "각인합 "+gradeCnt_engrave+enterStr;
		resMsg += "각인상세"+enterStr + engrave_ment+enterStr;
		
		resMsg += "가격표:"+enterStr;
		resMsg += "v0.6 악세 상하 중하 미포함 버그 수정"+enterStr;
		resMsg += "http://rgb-tns.dev-apc.com/in/totalGold2";
		
		
		
		
		
		//resMsg += miniGemCntSearch(charList.get("CharacterName").toString());//얘는 엔터포함됨
		
		return resMsg;
	}
	
	String subCharacterInfoSearch(String userId) throws Exception {
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData ="";
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);	
		}catch(Exception e) {
			e.printStackTrace();
			throw new Exception("E0004");
		}
		
		
		
		
		String resMsg=ordUserId+" 1540이상 부캐 정보" + enterStr;
		resMsg += "4T 5보석↑,3T 8보석↑ 표기"+enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", "")) >= 1540)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemMaxLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String mainServer = sortedList.get(0).get("ServerName").toString();
		
		resMsg += mainServer;
		resMsg += enterStr;
		
		
		HashMap<String,Object> resMap =new HashMap<>();
		
		int charCnt = 0;
		for(HashMap<String,Object> charList : sortedList) {
			if(mainServer.equals(charList.get("ServerName").toString())) {
				charCnt++;
				resMsg += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "] ";
				resMsg += "("+charList.get("ItemMaxLevel").toString().replaceAll(",", "")+") ";
				resMsg += charList.get("CharacterName").toString();
				resMsg += enterStr;
				System.out.println(userId+" : "+charCnt);
				resMap = sumTotalPowerSearch2(charList.get("CharacterName").toString());
				List<Map<String, Object>> armoryEquipment;
				Map<String, Object> armoryProfile = new HashMap<>();
				Map<String, Object> armoryEngraving = new HashMap<>();
				Map<String, Object> armoryGem = new HashMap<>();
				
				try {
					armoryGem = (Map<String, Object>) resMap.get("ArmoryGem");
				}catch(Exception e){
				}
				
				
				if(Double.parseDouble(charList.get("ItemMaxLevel").toString().replaceAll(",", "")) >= 1600) {
					resMsg += miniLimitSearch(resMap,charList.get("CharacterName").toString());
				}
				
				resMsg += enterStr;
				resMsg += miniGemCntSearch(armoryGem,charList.get("CharacterName").toString());//얘는 엔터포함됨
				if(charCnt ==4) {
					resMsg += enterStr + "4캐릭 이상 더보기..▼ ";
					resMsg += allSeeStr ;
				}
			}
			
		}
		
		return resMsg;
	}
	String marketTier4accessorySearch() throws Exception {
		String resMsg = "4티어 악세 최저가";
		JSONObject json = new JSONObject();
		JSONArray options = new JSONArray();
		JSONObject json2 = new JSONObject();
		JSONObject json3 = new JSONObject();
		//200000 : 장신구전체
		//200010 : 목걸이
		//200020 : 귀걸이
		//200030 : 반지
		//200040 : 팔찌 
		//FirstOption 8:아크패시브
		//SecondOption 1 깨달음, 2 도약
		//깨포 고대3 목:13 반:12 귀:12
		//깨포 고대2 목:9 반:8 귀:8
		//깨포 고대1 목:6 반:5 귀:5
		//깨포 고대0 목:4 반:3 귀:3
		//깨포 유물3 목:8~10 반:7~9 귀:7~9
		//FirstOption 7:연마효과
		//SecondOption 44 낙인력 51아공강 52아피강 41추피 49치적 50치피 
		resMsg += searchAuctionParse("고대3연마");
		resMsg += enterStr;
		
		resMsg += "[고대 3연마]"+enterStr;
		resMsg +=searchAuctionParse("고대3낙인력");
		resMsg +=searchAuctionParse("고대3공퍼");
		resMsg +=searchAuctionParse("고대3공퍼중");
		resMsg +=searchAuctionParse("고대3치적");
		resMsg +=searchAuctionParse("고대3치적중");
		resMsg += enterStr;
		
		resMsg += "[고대 1연마]"+enterStr;
		resMsg +=searchAuctionParse("고대1낙인력");
		resMsg +=searchAuctionParse("고대1치적");
		resMsg +=searchAuctionParse("고대1치피");
		resMsg += enterStr;
		
		resMsg += "[유물 3연마](깨달음7~9)"+enterStr;
		resMsg +=searchAuctionParse("유물3아공강");
		resMsg +=searchAuctionParse("유물3치적");
		resMsg += enterStr;
		
		resMsg += "[고대 3연마](요청분)"+enterStr;
		resMsg +=searchAuctionParse("고대3아공아피");
		resMsg +=searchAuctionParse("고대3아공아피최생");
		resMsg +=searchAuctionParse("고대3치피공플");
		
		return resMsg;
	}
	
	String searchAuctionParse(String ment) throws Exception{
		String resMsg = "";
		JSONObject json = new JSONObject();
		JSONArray options = new JSONArray();
		JSONObject json2 = new JSONObject();
		JSONObject json3 = new JSONObject();
		JSONObject json4 = new JSONObject();
		JSONObject json5 = new JSONObject();
		
		switch(ment) {
		case "고대3치피공플":
			json.put("CategoryCode", "200030");//30반지
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",50);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json4.put("FirstOption",7);
			json4.put("SecondOption",53);
			json4.put("MinValue",3);
			json4.put("MaxValue",3);
			options.put(json4);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치피상공플상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3아공아피":
			json.put("CategoryCode", "200030");//30반지
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",51);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json4.put("FirstOption",7);
			json4.put("SecondOption",52);
			json4.put("MinValue",3);
			json4.put("MaxValue",3);
			options.put(json4);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(아공상아피상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3아공아피최생":
			json.put("CategoryCode", "200030");//30반지
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",51);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json4.put("FirstOption",7);
			json4.put("SecondOption",52);
			json4.put("MinValue",3);
			json4.put("MaxValue",3);
			options.put(json4);
			
			json5.put("FirstOption",7);
			json5.put("SecondOption",55);
			json5.put("MinValue",2);
			json5.put("MaxValue",2);
			options.put(json5);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(아공상아피상최생중) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3연마":
			resMsg += "[고대 3연마 잡옵]"+enterStr;
			
			json.put("CategoryCode", "200010");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",13);
			json2.put("MaxValue",13);
			options.put(json2);
			
			json.put("EtcOptions",options);
			resMsg +="목걸이 ";
			String first_value = auctionSearchDt(json,false,false);
			resMsg += first_value;
			resMsg += enterStr;
			if(first_value.equals("")) {
				return "경매장 오류";
			}
			
			
			options = new JSONArray();
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			json.put("EtcOptions",options);
			
			json.put("CategoryCode", "200020");
			resMsg +="귀걸이 ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			json.put("CategoryCode", "200030");
			resMsg +="반지　 ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "유물3연마":
			resMsg += "[유물 3연마](60->24)"+enterStr;
			
			json.put("CategoryCode", "200010");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "유물");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",8);
			json2.put("MaxValue",10);
			options.put(json2);
			
			json.put("EtcOptions",options);
			resMsg +="목걸이 ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			
			options = new JSONArray();
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",7);
			json2.put("MaxValue",9);
			options.put(json2);
			json.put("EtcOptions",options);
			
			json.put("CategoryCode", "200020");
			resMsg +="귀걸이 ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			
			json.put("CategoryCode", "200030");
			resMsg +="반지　 ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3낙인력":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200010");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",13);
			json2.put("MaxValue",13);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",44);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="목걸이(낙인력 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3공퍼":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200020");//20귀걸이
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",45);//45 공퍼
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="귀걸이(공% 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대3공퍼중":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200020");//20귀걸이
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",45);//45 공퍼
			json3.put("MinValue",2);
			json3.put("MaxValue",2);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="귀걸이(공% 중) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
			
		case "고대3치적":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200030");//30반지
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",49);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치적 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		
		case "고대3치적중":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200030");//30반지
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",12);
			json2.put("MaxValue",12);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",49);
			json3.put("MinValue",2);
			json3.put("MaxValue",2);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치적 중) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대1낙인력":
			//resMsg += "[고대 1연마]낙인력 목걸이"+enterStr;
			json.put("CategoryCode", "200010");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",6);
			json2.put("MaxValue",6);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",44);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="목걸이(낙인력 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대1치적":
			//resMsg += "[고대 1연마](75)"+enterStr;
			json.put("CategoryCode", "200030");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",5);
			json2.put("MaxValue",5);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",49);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치적 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "고대1치피":
			//resMsg += "[고대 1연마](75)"+enterStr;
			json.put("CategoryCode", "200030");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "고대");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",5);
			json2.put("MaxValue",5);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",50);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치피 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "유물3아공강":
			json.put("CategoryCode", "200030");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "유물");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",7);
			json2.put("MaxValue",9);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",51);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(아공강 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "유물3치적":
			json.put("CategoryCode", "200030");
			json.put("Sort", "BUY_PRICE");
			json.put("SortCondition", "ASC");
			json.put("ItemGrade", "유물");
			
			json2.put("FirstOption",8);
			json2.put("SecondOption",1);
			json2.put("MinValue",7);
			json2.put("MaxValue",9);
			options.put(json2);
			
			json3.put("FirstOption",7);
			json3.put("SecondOption",49);
			json3.put("MinValue",3);
			json3.put("MaxValue",3);
			options.put(json3);
			
			json.put("EtcOptions",options);
			
			resMsg +="반지(치적 상) ";
			resMsg += auctionSearchDt(json,false,false);
			resMsg += enterStr;
			break;
		case "유물3":
			break;
		}
		
		
		return resMsg;
	}
	
	String newMarketSearch() throws Exception {
		String resMsg = "";
		JSONObject json ;
		String first_value ="";
		json = new JSONObject();

		json.put("CategoryCode", "50000");
		resMsg += "[4T/3T]최저가비교"+enterStr;
		resMsg += "§ 돌파석"+enterStr;
		resMsg += "운돌/찬명돌 - ";
		json.put("itemName", "운명의 돌파석");
		first_value = marketDtSearch(json,2,false,false);
		resMsg += first_value;
		if(first_value.equals("")) {
			return "경매장 오류";
		}
		
		resMsg += "/";
		json.put("itemName", "찬란");
		resMsg += marketDtSearch(json,2,false,true);
		
		resMsg += "§ 파괴석 & 수호석"+enterStr;
		resMsg += "운파/정파강 - ";
		json.put("itemName", "운명의 파괴석");
		resMsg += marketDtSearch(json,2,false,false);
		resMsg += "/";
		json.put("itemName", "정제된 파괴강석");
		resMsg += marketDtSearch(json,2,false,true);
		
		resMsg += "운수/정수강 - ";
		json.put("itemName", "운명의 수호석");
		resMsg += marketDtSearch(json,2,false,false);
		resMsg += "/";
		json.put("itemName", "정제된 수호강석");
		resMsg += marketDtSearch(json,2,false,true);
		
		resMsg += enterStr;
		
		resMsg += "§ 융화재료"+enterStr;
		resMsg += "아비도스/최상레하 - ";
		json.put("itemName", "아비도스");
		resMsg += marketDtSearch(json,4,false,false);
		resMsg += "/";
		json.put("itemName", "최상급");
		resMsg += marketDtSearch(json,4,false,true);
		
		//여기부턴 거래소
		resMsg += enterStr;
		json = new JSONObject();
		json.put("CategoryCode", "210000");
		json.put("Sort", "BUY_PRICE");
		json.put("SortCondition", "ASC");
		resMsg += "§ 보석"+enterStr;
		resMsg += "10멸/홍 - ";
		json.put("itemName", "10레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "10레벨 홍");
		resMsg += auctionSearchDt(json,false,true);
		resMsg += spaceStr+"8겁/작 - ";
		json.put("itemName", "8레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "8레벨 작");
		resMsg += auctionSearchDt(json,false,true);
		
		resMsg += enterStr;
		
		resMsg += spaceStr+"9멸/홍 - ";
		json.put("itemName", "9레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "9레벨 홍");
		resMsg += auctionSearchDt(json,false,true);
		resMsg += spaceStr+"7겁/작 - ";
		json.put("itemName", "7레벨 겁");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "7레벨 작");
		resMsg += auctionSearchDt(json,false,true);
		
		resMsg += enterStr;
		
		
		resMsg += "§ 추가 명령어(초성가능)"+enterStr;
		resMsg += "티어별 조회: 경매장4/경매장3"+enterStr;
		resMsg += "각인서 조회: 경매장전설/경매장유물"+enterStr;
		resMsg += "연마악세 조회: 경매장악세"+enterStr;
		
		return resMsg;
	}
	
	String marketTier4Search() throws Exception {
		String resMsg = "";
		JSONObject json ;
		String first_value ="";
		json = new JSONObject();

		json.put("CategoryCode", "50000");
		resMsg +="[4티어]"+enterStr;
		
		json.put("itemName", "용암");
		first_value = marketDtSearch(json,2,true,true);
		resMsg += first_value;
		
		if(first_value.equals("")) {
			return "경매장 오류";
		}
		
		json.put("itemName", "빙하");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("itemName", "운명의 파편 주머니");
		resMsg += marketDtSearch(json,1,true,true);
		
		json.put("itemName", "운명의 돌파석");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("itemName", "운명의 파괴석");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("itemName", "아비도스");
		resMsg += marketDtSearch(json,4,true,true);
		
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
		String first_value = marketDtSearch(json,0,true,true);
		resMsg += first_value;
		
		if(first_value.equals("")) {
			return "경매장 오류";
		}
		
		json.put("itemName", "명예의 파편");
		resMsg += marketDtSearch(json,1,true,true);
		
		json.put("itemName", "찬란");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("itemName", "파괴강석");
		resMsg += marketDtSearch(json,3,true,true);
		
		json.put("itemName", "최상급");
		resMsg += marketDtSearch(json,4,true,true);
		
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
	
		resMsg += spaceStr+"8멸/홍 -";
		json.put("itemName", "8레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "8레벨 홍");
		resMsg += auctionSearchDt(json,false,true);
		
		resMsg += spaceStr+"7멸/홍 -";
		json.put("itemName", "7레벨 멸");
		resMsg += auctionSearchDt(json,false,false);
		resMsg += "/";
		json.put("itemName", "7레벨 홍");
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
			json.put("PageNo", "1");
			
			resMsg +="[유물]"+enterStr;
			
			resMsg += marketDtSearch(json,2,true,true);
			
			json.put("PageNo", "2");
			resMsg += marketDtSearch(json,2,true,true);
			
		}else if(tier==400002) {
			json.put("CategoryCode", "40000");
			json.put("Sort", "CURRENT_MIN_PRICE");
			json.put("SortCondition", "DESC");
			json.put("ItemGrade", "전설");
			json.put("PageNo", "1");
			
			resMsg +="[전설]"+enterStr;
			
			resMsg += marketDtSearch(json,2,true,true);
			
			json.put("PageNo", "2");
			resMsg += marketDtSearch(json,2,true,true);
			
		}else {
			return "";
		}
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
	
		
		
		return resMsg;
	}
	//"[유물각인서 시세조회]"
	String marketEngrave() throws Exception{
		JSONObject json ;
		String resMsg= "";
		
		json = new JSONObject();
		
		json.put("CategoryCode", "40000");
		json.put("Sort", "CURRENT_MIN_PRICE");
		json.put("SortCondition", "DESC");
		json.put("ItemGrade", "유물");
		
		json.put("PageNo", "1");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("PageNo", "2");
		resMsg += marketDtSearch(json,2,true,true);
		
		json.put("PageNo", "3");
		resMsg += marketDtSearch(json,2,true,true);
		
		resMsg = engraveBook(resMsg);
		return resMsg;
	}
	
	String engraveBook(String str_list) throws Exception{
		String[] arr = str_list.split(enterStr);
		
		String res1 = enterStr;
		String res2 = enterStr;
		String res3 = enterStr;
		
		for(String str:arr) {
			str = LoaApiUtils.filterTextForEngrave(str);
			if(str.indexOf("[D]") >= 0) {
				res1 += str+enterStr;
			}else if(str.indexOf("[S]") >= 0) {
				res2 += str+enterStr;
			}else {
				res3 += str+enterStr;
			}
		}
		return res1+res2+res3;
	}
	
	String marketSearch() throws Exception {
		
		String resMsg= "[아이템명]-[실시간최저가]"+enterStr;
		
		resMsg +=marketTier4Search();
		resMsg +=enterStr;
		resMsg +=marketTier3Search();
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
		return resMsg;
	}
	
	String marketDtSearch(JSONObject json,int numbering,boolean nameDefaultYn,boolean enterYn) throws Exception {
		String str = "";

		String paramUrl = lostArkAPIurl + "/markets/items";

		//String returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
		
		String returnData;
		try {
			returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
		}catch(Exception e){
			throw new Exception("E0004");
		}
		
		try {
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
	
						if(nameDefaultYn) {
							str += item.get("Name") + " - " ;
						}
						
						str += item.get("CurrentMinPrice") + "G";
						
						if(enterYn) {
							str +=enterStr;
						}
						
					}
					
					
					break;
				
			}
			
		}catch(Exception e){
			if(enterYn) {
				str +=enterStr;
			}
		}

		

		return str;
	}

	String auctionSearchDt(JSONObject json,boolean nameDefaultYn,boolean enterYn) throws Exception {
		String str = "";

		try {

			String paramUrl = lostArkAPIurl + "/auctions/items";

			//String returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
			String returnData;
			try {
				returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
			}catch(Exception e){
				throw new Exception("E0004");
			}
			
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
			//e.printStackTrace();
			System.out.println(e.getMessage());
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
			//e.printStackTrace();
			System.out.println(e.getMessage());
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
			//e.printStackTrace();
			System.out.println(e.getMessage());
			content = "오류입니다.";
		}
		
		// botService.save db에 저장 로직 (reqMsg,content)

		return content;
	}
	
	public String tossAccount() {
		String ment = "";
		ment += enterStr+"람쥐봇 개발자 대화하기(오픈채팅)";
		ment += enterStr+"https://open.kakao.com/o/sC6s7lkb";
		return ment; 
	}
	public String tossAccount2() {
		String ment = "";
		ment += enterStr;
		ment += enterStr+"[개발자 후원하기]";
		ment += enterStr+"후원금은 서버비 및 개발자 콜라비용에 보탬이 됩니다.";
		ment += enterStr+"토스뱅크 1000-4571-3008 ㅈㅌㅎ";
		//ment += enterStr+"[감사하신 분들]";
		//ment += enterStr+"챙석봉, 리퍼고냥이, 블루미안, 친칠라솜꼬리토끼";
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
	
	public String charImgSearch(String ordUserId,String title,String className,String imgUrl) {
		String val = "";
		String randKey = "";
		
		randKey = botService.selectBotImgCharSave(imgUrl);
		
		if (randKey == null || randKey.equals("")) {
			randKey = ImageUtils.RandomAlphaNum();
			String outFilePath = "/img/img_loa_cr/" + randKey + ".png";
			
			try {
				URL url = new URL(imgUrl);
				
				BufferedImage image = ImageIO.read(url);
				File file = new File(outFilePath);

				ImageIO.write(image, "png", file);
				
				HashMap<String,Object> hs = new HashMap<>();
				hs.put("char_name", ordUserId);
				hs.put("req", imgUrl);
				hs.put("res", randKey);
				hs.put("title", title);
				hs.put("class_name", className);
				
				String star = supporters(ordUserId);
				if(star != null && star.length()>0) {
					hs.put("star_yn", "1");	
				}
				
				botService.insertBotImgCharSaveTx(hs);
			}catch(Exception e) {
				System.out.println("이미지 다운로드 실패.. "+imgUrl);
				return val;
			}
		}

		val = "rgb-tns.dev-apc.com/ic/" + randKey;
		return val;
	}
	
	static final String[] CHO = {"2","4","2","3","6","5","4"
			,"4","8","2","4","1","3","6","4","3","4","4","3"};
	
	static final String[] JOONG = {"2","3","3","4","2","3","3","4","2","4"
			,"5","3","3","2","4","5","3","3","1","2","1"};
	
	static final String[] JONG = {"0","2","4","4","2","5","5","3","5","7","9","9"
			,"7","9","9","8","4","4","6","2","4","1","3","4","3","4","4","3"};
	
	String loveTest(String str1,String str2) throws Exception {
		
		String rtnMsg ="";
		
		String nam1 = str1;
		String nam2 = str2;
		
		char[] nameArray1 = nam1.toCharArray();
		char[] nameArray2 = nam2.toCharArray();
		
		if(nameArray2.length>nameArray1.length) {
			char[] tmpArray = nameArray1;
			nameArray1 = nameArray2;
			nameArray2 = tmpArray;
		}
		int maxLength = Math.max(nameArray1.length, nameArray2.length);
		
		StringBuilder s = new StringBuilder();
		for(int i=0;i<maxLength;i++) {
			if(i<nameArray1.length) {
				char c = nameArray1[i];
				
				int choIndex = (c-44032)/588;
				int joongIndex = ((c-44032)%588) /28;
				int jongIndex = (c-44032)%28;
				
				int choStroke = Integer.parseInt(CHO[choIndex]);
				int joongStroke = Integer.parseInt(JOONG[joongIndex]);
				int jongStroke = Integer.parseInt(JONG[jongIndex]);
				
				int totalStroke = choStroke+joongStroke+jongStroke;
				s.append(totalStroke).append(" ");
				
				rtnMsg += (c+" ");
			}
			
			if(i<nameArray2.length) {
				char c = nameArray2[i];
				
				int choIndex = (c-44032)/588;
				int joongIndex = ((c-44032)%588) /28;
				int jongIndex = (c-44032)%28;
				
				int choStroke = Integer.parseInt(CHO[choIndex]);
				int joongStroke = Integer.parseInt(JOONG[joongIndex]);
				int jongStroke = Integer.parseInt(JONG[jongIndex]);
				
				int totalStroke = choStroke+joongStroke+jongStroke;
				s.append(totalStroke).append(" ");
				
				rtnMsg += (c+" ");
			}
		}
		
		rtnMsg += enterStr;
		
		rtnMsg += s.toString().trim()+enterStr;
		String[] strokes = s.toString().trim().split(" ");
		
		int[] numbers = new int[strokes.length];
		for(int i=0;i<strokes.length;i++) {
			numbers[i] = Integer.parseInt(strokes[i]);
		}
		
		int newCnt = 0;
		
		while (numbers.length>2) {
			
			int[] newNumbers = new int[numbers.length-1];
			newCnt++;
			for (int i=1;i<numbers.length;i++) {
				int mark = (numbers[i-1]+numbers[i]);
				int remain = mark%10;
				newNumbers[i-1] = remain;
				if(i==1) {
					for(int j=0 ; j<newCnt ; j++) {
						rtnMsg += spaceStr;	
					}
				}
				rtnMsg += remain + " ";
			}
			rtnMsg += enterStr;
			numbers = newNumbers;
		}
	
		rtnMsg += ("최종점수 : " + numbers[0]+numbers[1] + "점")+enterStr;
		
	
		return rtnMsg;
	}
}
