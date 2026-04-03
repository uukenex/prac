package my.prac.api.loa.controller;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotExtService;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;
import my.prac.core.util.ChatGPTUtils;
import my.prac.core.util.GeminiUtils;
import my.prac.core.util.ImageUtils;
import my.prac.core.util.LoaApiParser;
import my.prac.core.util.LoaApiUtils;


@Controller
public class LoaChatController {
	static Logger logger = LoggerFactory.getLogger(LoaChatController.class);

	@Autowired
	LoaChatSubController sub;
	@Autowired
	LoaMarketController market;
	@Autowired
	LoaPlayController play;
	@Autowired
	LoaAiBotController ai;
	
	@Autowired
	ExtController ext;
	
	@Autowired
	BossAttackController boss;

	@Autowired
	BossAttackS3Controller bossS3; // [시즌3] 보스 공격 전담 컨트롤러

	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;
	@Resource(name = "core.prjbot.BotNewService")
	BotNewService botNewService;

	@Resource(name = "core.prjbot.BotExtService")
	BotExtService botExtService;
	
	// final String lostArkAPIurl =
	// "https://developer-lostark.game.onstove.com/armories/characters/일어난다람쥐/equipment";
	final String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	final String rank_1st = "👑"; 
	final String rank_2nd = "🥈"; 
	final String rank_3rd = "🥉"; 
	final String rank_etc = "　";
	
	final String[] unable_save_list = {enterStr,spaceStr,tabStr,allSeeStr,anotherMsgStr,listSeparatorStr,"\\"};
	
	@RequestMapping(value = "/loa/apiCombatPower/{param0}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> apiCombatPower(@PathVariable String param0) {
		
		Map<String,Object> map =new HashMap<String,Object>();
		try {
			map = apiCallCombatPower(param0);
		} catch (Exception e) {
		}
		return map;
	}
	
	
	public Map<String,Object> apiCallCombatPower(String userId) throws Exception {

	    Map<String,Object> result = new HashMap<>();
	    List<Map<String,Object>> characters = new ArrayList<>();

	    String ordUserId = userId;

	    // 캐릭터명 URL 인코딩
	    userId = URLEncoder.encode(userId, "UTF-8");

	    String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";

	    String returnData = "";

	    try {
	        returnData = LoaApiUtils.connect_process(paramUrl);
	    } catch(Exception e) {
	        e.printStackTrace();
	        throw new Exception("E0004");
	    }

	    ObjectMapper mapper = new ObjectMapper();

	    List<HashMap<String, Object>> rtnMap =
	            mapper.readValue(returnData,
	                    new TypeReference<List<Map<String, Object>>>() {});

	    if(rtnMap == null || rtnMap.isEmpty()) {
	        result.put("server", "");
	        result.put("characters", characters);
	        return result;
		}

		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x -> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1640)
				.sorted(Comparator
						.comparingDouble(x -> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());

	    if(sortedList.isEmpty()) {
	        result.put("server", "");
	        result.put("characters", characters);
	        return result;
	    }

	    // 메인 서버
	    String mainServer = sortedList.get(0).get("ServerName").toString();

	    int charCnt = 0;

	    for(HashMap<String,Object> charInfo : sortedList) {

	        if(charCnt >= 6) break;

	        if(!mainServer.equals(charInfo.get("ServerName").toString())) continue;

	        String characterName = charInfo.get("CharacterName").toString();
	        String className = charInfo.get("CharacterClassName").toString();

	        double level = Double.parseDouble(
	                charInfo.get("ItemAvgLevel").toString().replaceAll(",", "")
	        );

	        Map<String,Object> resMap = new HashMap<>();

	        try {
	            resMap = sub.sumTotalPowerSearch2(characterName);
	        } catch(Exception e) {
	            System.out.println(characterName + " API error");
	            continue;
	        }

	        Map<String,Object> armoryProfile = new HashMap<>();

	        try {
	            armoryProfile = (Map<String, Object>) resMap.get("ArmoryProfile");
	        } catch(Exception e){
	            System.out.println(characterName + " ArmoryProfile parse error");
	        }

	        String combatPower = "0";

	        if(armoryProfile != null && armoryProfile.get("CombatPower") != null) {
	            combatPower = armoryProfile.get("CombatPower").toString();
	            combatPower = combatPower.replaceAll(",", "");
	        }

	        Map<String,Object> charMap = new HashMap<>();

	        charMap.put("name", characterName);
	        charMap.put("class", className);
	        charMap.put("level", level);
	        charMap.put("combatPower", combatPower);

	        characters.add(charMap);
	        
	        HashMap<String,Object> dbMap = new HashMap<>();

	        dbMap.put("characterName", characterName);
	        dbMap.put("serverName", mainServer);
	        dbMap.put("className", className);
	        dbMap.put("itemLevel", level);
	        dbMap.put("combatPower", Double.parseDouble(combatPower));

	        botService.mergeCharacterPower(dbMap);

	        charCnt++;
	    }

	    //result.put("server", mainServer);
	    result.put("characters", characters);

	    return result;
	}
	
	@RequestMapping(value = "/loa/manual", method = RequestMethod.GET)
	public @ResponseBody String manualPage() {
		
		HashMap<String,Object> map =new HashMap<String,Object>();
		String val = botService.selectBotManual(map);
		return val;
	}
	
	@RequestMapping(value = "/loa/cron/{param0}", method = RequestMethod.GET)
	public void cronManager(@PathVariable String param0) {
		String val = "";
		HashMap<String, Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("roomName", "cron");
		reqMap.put("userName", "cron");
		reqMap.put("fulltxt", param0);
		String org_fulltxt = param0;
		
		//패치날엔 스킵 
		if (shouldSkip()) {
            return;
        }
		
		switch(param0) {
		case "c1":
			org_fulltxt = "/ㄱㅁㅈㅇㅁ";
			try {
				market.search_c1();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			try {
				market.search_c2();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			
			try {
				//val ="[유물각인서 시세조회]";
				//val += marketEngrave();
				
				
			}catch(Exception e) {
				//val = errorCodeMng(e,reqMap);
			}
			break;
		case "c2":
			org_fulltxt = "/ㄸㅅㅋㄹㄹ";
			try {
				// 1) 캐시된 JSON 가져오기 (하루 1번만 API 호출하도록 만들어둔 메서드)
				String json = ext.fetchMerchantServer5();

				// 2) DB 저장 (server=5: 카단)
				botExtService.saveLatestMerchantReports(json, 5);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			break;
		case "test":
			try {
				market.search_c2();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			break;
		}
		
		
		
		try {
			if(val!="") {
				reqMap.put("req", org_fulltxt);
				reqMap.put("res", val);
				botService.insertBotWordHisTx(reqMap);
			}
		}catch(Exception e) {
			
		}
		
		
		
	}
	
	public static boolean shouldSkip2() {
	    LocalTime now = LocalTime.now();
	    return !now.isBefore(LocalTime.of(21, 45)) && now.isBefore(LocalTime.of(21, 50));
	}
	
	public static boolean shouldSkip() {
        // 현재 날짜와 시간 가져오기
        LocalDateTime now = LocalDateTime.now();
        
        // 현재 요일과 시간
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 스킵할 요일과 시간대 설정
        DayOfWeek skipDay = DayOfWeek.WEDNESDAY; // 수요일
        LocalTime skipStart = LocalTime.of(3, 0); // 03:00
        LocalTime skipEnd = LocalTime.of(10, 0);  // 10:00

        // 요일이 수요일이면서 시간대가 03:00~10:00 사이인지 체크
        if (dayOfWeek == skipDay) {
            if (!time.isBefore(skipStart) && time.isBefore(skipEnd)) {
                return true; // 스킵!
            }
        }

        return false; // 스킵 안 함
    }
	
	
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
			sender = removeEmojis(sender);
			
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
		
		try {
			star+= sp_icon(Integer.parseInt(starYn));
		}catch (Exception e){
			star+="　";
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
		
		if(param2!=null && param2.equals("undefined")) {
			param2 = "";
		}
		
		if(param0.startsWith("[")) {
			return emotionMsg(param0,roomName,sender);
		}else if(param0.startsWith("/")) {
			return commandMsg(param0,param1,param2,roomName,sender,fulltxt);
		}
		return val;
	}
	
	public static String removeEmojis(String input) {
	    if (input == null) return null;

	    StringBuilder sb = new StringBuilder();
	    input.codePoints().forEach(cp -> {
	        // BMP(0x0000 ~ 0xFFFF)까지만 허용
	        if (cp <= 0xFFFF) {
	            sb.appendCodePoint(cp);
	        }
	    });
	    return sb.toString();
	}
	
	String commandMsg(String param0, String param1, String param2, String roomName, String sender, String fulltxt)
			throws Exception {
		String val = "";
		HashMap<String, Object> reqMap = new HashMap<>();
		reqMap.put("param0", param0);
		reqMap.put("param1", param1);
		reqMap.put("param2", param2);
		reqMap.put("roomName", roomName);
		reqMap.put("userName", sender);
		reqMap.put("fulltxt", fulltxt);
		String org_fulltxt = fulltxt;
		String org_userName = sender;
		
		int masterYn =0;
		boolean passYn=false;
		String replace_param="";
		
		List<HashMap<String,Object>> gameYnList;
		String playYn ; 
		
		HashMap<String,Object> saveMap = new HashMap<>();
		List<HashMap<String,Object>> hs;
		try {
			if(fulltxt.length()>300) {
				val = "너무길어요!";
				return val;
			}
			
			switch (param0) {
			case "/테스트":
				val = play.testMethod(reqMap);
				break;
			case "/ㄹㅇ":
			case "/보상":
			case "/비싼전각":
			case "/비싼유각":
			case "/전각":
			case "/유각":
			case "/상단일":
			case "/중단일":
			case "/상상":
			case "/중중":
				passYn = true;
				break;
			case "/ㅌㅅㅌ":
				val = botService.execSPProcTest(reqMap);
				break;
			case "/큐브":
				break;
				/*
			case "/시즌이력":
			case "/ㅅㅈㅇㄹ":
				reqMap.put("param0", "/ㅅㅈㅇㄹ");
				val = botService.execSPProcTest(reqMap);
				break;
			case "/시즌정보":
				break;
				*/
				/*
			case "/강화": case "/ㄱㅎ": 
				List<HashMap<String,Object>> gameYnList = botService.selectGamePlayYn(reqMap);
				String playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.weapon(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/반지강화": case "/ㅂㅈㄱㅎ": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.hit_ring_upgrade(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/강화정보": case "/ㄱㅎㅈㅂ": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.weaponInfo(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/강화2": case "/ㄱㅎ2": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.weapon2(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/강화3": case "/ㄱㅎ3": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.weapon3(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/강화랭킹": case "/ㄱㅎㄹㅋ": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = "전체 방 강화랭킹 1등: "+botService.selectBotPointWeaponRank1st()+enterStr+enterStr;
					
					
					List<HashMap<String,Object>> weapon_map = botService.selectBotPointWeaponRank(reqMap);
					val +="현재방 강화랭킹"+enterStr;
					for(int i =0;i<weapon_map.size();i++) {
						
						default:
							val += rank_etc;
							break;
						}
						val += weapon_map.get(i).get("USER_NAME")+ " : "+weapon_map.get(i).get("GRADE")+enterStr ;
						if(i==3) {
							val += allSeeStr;
						}
					}
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				
				break;
			case "/악세랭킹": case "/ㅇㅅㄹㅋ": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = "전체 방 악세랭킹 1등: "+botSettleService.selectBotPointAccRank1st()+enterStr+enterStr;
					
					
					List<HashMap<String,Object>> weapon_map = botSettleService.selectBotPointAccRank(reqMap);
					val +=roomName+" 악세랭킹"+enterStr;
					for(int i =0;i<weapon_map.size();i++) {
						switch(i) {
						
						default:
							val += rank_etc;
							break;
						}
						val += weapon_map.get(i).get("USER_NAME")+ " : "+weapon_map.get(i).get("GRADE")+enterStr ;
						if(i==3) {
							val += allSeeStr;
						}
					}
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				
				break;
			case "/악세랭킹2": case "/ㅇㅅㄹㅋ2": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = "전체 방 달성 악세랭킹 1등: "+botSettleService.selectBotPointAccLogRank1st()+enterStr+enterStr;
					
					
					List<HashMap<String,Object>> weapon_map = botSettleService.selectBotPointAccLogRank(reqMap);
					val +=roomName+" 달성악세랭킹"+enterStr;
					for(int i =0;i<weapon_map.size();i++) {
						switch(i) {
						
						default:
							val += rank_etc;
							break;
						}
						val += weapon_map.get(i).get("USER_NAME")+ " : "+weapon_map.get(i).get("GRADE")+enterStr ;
						if(i==3) {
							val += allSeeStr;
						}
					}
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				
				break;	
				*/
				/*
			case "/ㅊㅅㅂ": case "/출석부": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("출석")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.attendanceToday(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				
				break;
				*/
				/*
			case "/ㅊㅊ": case "/cc": case "/CC": case "/출첵":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("출석")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.attendance(reqMap);
					val+= enterStr+enterStr+play.pointSeasonMsg()+enterStr;
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				
				break;
				*/
				/*
			case "/주사위": case "/ㅈㅅㅇ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("주사위")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.diceRoll(reqMap);
					val+= enterStr+enterStr+play.pointSeasonMsg()+enterStr;
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				
			case "/결투": case "/ㄱㅌ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("결투")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.fight_s(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				
				/*
			case "/보스": case "/보스정보":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.bossInfo(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
			case "/구매": case "/ㄱㅁ":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.buyItem(reqMap);
				break;
			case "/판매": case "/ㅍㅁ":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				val = boss.sellItem(reqMap);
				break;
			case "/직업": case "/전직":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.changeJob(reqMap);
				break;
			case "/모드": case "/ㅁㄷ":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				val = boss.changeMode(reqMap);
				break;
			case "/체력": case "/ㅊㄹ":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				val = boss.getHpStatus(reqMap);
				break;
			case "/ㄱㅂㄹㄱ": case "/ㄱㅂㄺ":
			case "/가방로그":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.bagLog(reqMap);
				break;
			case "/ㄱㅂㅇㅍ": case "/ㄱㅂㅇㄱ":
			case "/가방오픈": case "/가방열기":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				val = boss.openBag(reqMap);
				break;
			case "/공격랭킹": case "/ㄱㄱㄹㅋ":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.showAttackRanking(reqMap);
				break;
			case "/공격정보": case "/ㄱㄱㅈㅂ": case "/가방": case "/ㄱㅂ":
				/*
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
		        */
				val = boss.attackInfo(reqMap);
				break;
			case "/가방상세": case "/ㄱㅂㅅㅅ": case "/ㄱㅄㅅ": case "/ㅇㅂ":case "/인벤":case "/인벤토리":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.invenInfo(reqMap);
				break;
			case "/갱신":
				passYn = true;
				val = boss.refreshCache();
				break;

			case "/ㄱㄱㄱㅈ": case "/공격공지": case "/공지":
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				val = boss.patchNote(reqMap);
				break;
				/*
			case "/ㄹㄹ": case "/룰렛": 
				val = boss.roulette(reqMap);
				break;*/
				
			case "/ㄱㄱㄱ": case "/ㄱ": case "/R": case "/r": 
				
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				
				val = boss.monsterAttack(reqMap);
				break;
			case "/ㄱㄱㅌㄱ":case "/공격타겟":
			case "/ㄱㄱㄷㅅ":case "/공격대상": 
				if (shouldSkip2()) {
					val = "매일9시45분~50분은 일시중단입니다.";
					passYn = true;
		            return val;
		        }
				passYn = true;
				reqMap.put("monNo", param1);
				val = boss.changeTarget(reqMap);
				break;
				/*
			case "/공격": case "/ㄱㄱ": case "/rr": case "/RR": 
				
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.attackBoss2(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/

				// =====================================================
				// [시즌3] 보스 공격 라우팅 안내
				// =====================================================
				// TODO: 기존 /공격 case 내부(monsterAttack 호출 전) 아래 분기 추가 예정
				//   User u = botNewService.selectUser(sender, null);
				//   if ("BOSS".equals(String.valueOf(u.targetMon))) {
				//       val = bossS3.attackBoss(reqMap);   // [시즌3] 보스 전투
				//   } else {
				//       val = boss.monsterAttack(reqMap);  // 기존 시즌2 몬스터 전투
				//   }
				//
				// TODO: 보스 정보 조회 명령어 추가 예정
				//   case "/보스정보": case "/ㅂㅅ":
				//       val = bossS3.bossInfo(reqMap);
				//       break;
				/*
			case "/포인트사용": 
				val = play.usePoint(reqMap);
				break;	
				*/
			case "/포인트뽑기": case "/ㅃㄱ": case "/뽑기":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("뽑기")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.gamble(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				/*
			case "/포인트상점": case "/상점": case "/ㅍㅇㅌㅅㅈ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("뽑기")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.pointShop(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
				/*
			case "/악세구매": case "/악세구입": case "/ㅇㅅㄱㅇ": case "/ㅇㅅㄱㅁ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					
					val = play.acc_buy(reqMap);
					
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
			case "/악세강화": case "/ㅇㅅㄱㅎ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					
					val = play.acc_upgrade(reqMap);
					
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
			case "/근력강화": case "/ㄱㄺㅎ": case "/ㄱㄹㄱㅎ": 
			case "/방어강화": case "/ㅂㅇㄱㅎ": 
			case "/치명강화": case "/ㅊㅁㄱㅎ": 
			case "/맥포강화": case "/ㅁㅍㄱㅎ": 
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					switch(param0) {
						case "/근력강화": case "/ㄱㄺㅎ": case "/ㄱㄹㄱㅎ":
							reqMap.put("stat", "STR");
						break;
						case "/방어강화": case "/ㅂㅇㄱㅎ": 
							reqMap.put("stat", "DEF");
							break;
						case "/치명강화": case "/ㅊㅁㄱㅎ": 
							reqMap.put("stat", "CRI");
							break;
						case "/맥포강화": case "/ㅁㅍㄱㅎ":
							reqMap.put("stat", "LIMIT");
							break;
					}
					val = play.limit_upgrade(reqMap);
					
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
			case "/상자":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = "상자 아이템 리스트..";
					val+= allSeeStr;
					List<HashMap<String,Object>> PointItemOptionList = botService.selectPointItemOptionList(reqMap);
					for (HashMap<String,Object> item : PointItemOptionList) {
						val += enterStr+"Item No"+item.get("ITEM_NO")+" "+item.get("ITEM_NAME");
						val += enterStr+item.get("ITEM_DESC")+enterStr;
					}
					
					
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
			case "/상자랭킹": case "/ㅅㅈㄹㅋ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					
					List<HashMap<String,Object>> box_ranking_map = botService.selectBotPointItemUserRankAll(reqMap);
					val +=roomName+" 보물 상자 랭킹"+enterStr;
					val +="총/오픈(확률)/미개봉"+enterStr;
					for(int i =0;i<box_ranking_map.size();i++) {
						switch(i) {
							default:
								break;
						}
						val += box_ranking_map.get(i).get("USER_NAME")+ ":"+box_ranking_map.get(i).get("TOT_CNT")+"/"+box_ranking_map.get(i).get("OPEN_SUM")+"("+box_ranking_map.get(i).get("RATE")+")"+"/"+box_ranking_map.get(i).get("YET_SUM")+enterStr ;
					}
					
					
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;		
			case "/상자구입": case "/상자구매": case "/ㅅㅈㄱㅁ": case "/ㅅㅈㄱㅇ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.pointBoxOpenBuy(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
			case "/상자열기": case "/상자오픈": case "/ㅅㅈㅇㄱ": case "/ㅅㅈㅇㅍ":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("강화")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.pointBoxOpen(reqMap);
					//val ="점검중입니다";
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				*/
			case "/포인트야구": case "/야구":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("야구")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.baseball(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				
			case "/저가": case "/저스트가드":
				gameYnList = botService.selectGamePlayYn(reqMap);
				playYn ="1"; 
				for(HashMap<String,Object> gameYn : gameYnList) {
					if(gameYn.get("NAME").equals("결투")) {
						playYn = gameYn.get("PLAY_YN").toString(); 
					}
				}
				
				if(playYn.equals("1")) {
					val = play.fight_e(reqMap);
				}else {
					val = "별도 게임방에서 진행해주세요.";
				}
				break;
				
			/*
			case "/이벤트참여":
				val = play.eventApply(reqMap);
				break;*/
			case "/로또": case "/ㄹㄸ":
				val = lotto();
				break;
			case "/궁합":
				try {
					val = loveTest(param1,param2);
				}catch(Exception e) {
					val = "/궁합 이름1 이름2 형태로 입력해주세요.(한글만)";
				}
				
				break;
			case "/시세": case "/ㅅㅅ":
			case "/시세1": case "/ㅅㅅ1":
			case "/시세2": case "/ㅅㅅ2":
			case "/시세3": case "/ㅅㅅ3":
			case "/시세4": case "/ㅅㅅ4":
				fulltxt = fulltxt.replace(param1, LoaApiUtils.switchWord(param1));
				param1 =LoaApiUtils.switchWord(param1);
				
				try {
					switch(param0) {
					case "/시세":
					case "/ㅅㅅ":
					case "/시세1":
					case "/ㅅㅅ1":
						param2 ="WEEK";
						break;
					case "/시세2":
					case "/ㅅㅅ2":
						param2 ="DAY";
						break;
					case "/시세3":
					case "/ㅅㅅ3":
						param2 ="HOUR";
						break;
					case "/시세4":
					case "/ㅅㅅ4":
						param2 ="MONTH";
						break;	
					}
						
				} catch (Exception e) {
					param2 ="WEEK";
				}
				reqMap.put("param1",param1);
				reqMap.put("param2",param2);
				
				if(param1 == null || param1.length()==0) {
					val +="각인서 또는 보석(7~10) 입력가능";
				}
				
				List<HashMap<String,Object>> list2 = botService.selectMarketItemPriceInfo(reqMap);
				val = param1+enterStr;
				if(list2 == null || list2.size()==0) {
					val +="결과가 없습니다";
				}
				
				int count=0;
				for(HashMap<String,Object> h:list2) {
					val += h.get("BASE")+" : "+h.get("MIN_PRICE")+" ~ "+h.get("MAX_PRICE") +enterStr;
					count++;
					if(count > 12) {
						break;
					}
				}
				val+=enterStr;
				val+="/시세1 이름 : 주차별"+enterStr;
				val+="/시세2 이름 : 일별"+enterStr;
				val+="/시세3 이름 : 시간별"+enterStr;
				val+="/시세4 이름 : 월별"+enterStr;
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
				passYn=true;
				break;
			case "/골드": case "/ㄱㄷ": case "/클골": case "/ㅋㄱ":
				val  = checkGoldList();
				//val += enterStr;
				//val += enterStr;
				//val += "http://rgb-tns.dev-apc.com/in/202409";
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
						val += enterStr+enterStr;
						val += shipSearch();
						break;
					case 3:
						//수요일 오전엔 정보 없음
						if( Integer.parseInt(nowTime) <= 6) {
							val  = "정보없음" ;
						}else {
							val  = calendarSearch(0);
							val += enterStr+enterStr;
							val += shipSearch();
							val += enterStr + enterStr+"내일의 모험섬 더보기..▼"+allSeeStr;
							val += calendarSearch(1);
						}
						break;
					default:
						val  = calendarSearch(0);
						val += enterStr + enterStr;
						val += shipSearch();
						val += enterStr + enterStr +"내일의 모험섬 더보기..▼"+allSeeStr;
						val += calendarSearch(1);
						break;
				}
				
				break;
				
			case "/각인":
			case "/ㄱㅇ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㄱㅇ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						//val = supporters(param1);
						val = engraveSearch(param1);
						//val+= tossAccount2();
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= enterStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㄱㅇ 캐릭명 으로 입력해주세요";
				}
				
				break;
			case "/파티":
			case "/ㅍㅌ":
				if (param1 != null && !param1.equals("")) {
					try {
						val = raidSearch(reqMap);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}else {
					val = raidListSearch(reqMap);
				}
				break;	
			
			case "/파티생성":
			case "/ㅍㅌㅅㅅ":
				if (param1 != null && !param1.equals("")) {
					
					if (param2 == null || param2.equals("")) {
						return "파티설명 등록 실패!"+
								enterStr+"/파티생성 파티명 파티설명 으로 입력해주세요";
								
					}
					
					try {
						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "파티설명 등록 실패!, 특수문자 안되요!";
								return val;
							}
						}

						fulltxt = fulltxt.substring(param0.length()+1+param1.length()).trim();
						reqMap.put("param2", fulltxt);
						
						val = raidAdd(reqMap);
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}else {
					val = "/파티생성 파티명 파티설명 으로 입력해주세요"
						 +enterStr +"/파티 로 파티명조회";
				}
				break;
			case "/파티수정":
			case "/ㅍㅌㅅㅈ":
				if (param1 != null && !param1.equals("")) {
					try {
						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "파티설명 등록 실패!, 특수문자 안되요!";
								return val;
							}
						}

						fulltxt = fulltxt.substring(param0.length()+1+param1.length()).trim();
						reqMap.put("param2", fulltxt);
						
						val = raidMod(reqMap);
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}else {
					val = "/파티생성 파티명 파티설명 으로 입력해주세요"
						 +enterStr +"/파티 로 파티명조회";
				}
				break;
			case "/파티가입":
			case "/ㅍㅌㄱㅇ":
				if (param1 != null && !param1.equals("")) {
					try {
						val = raidJoin(reqMap);
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}else {
					val = "/파티가입 파티명 캐릭명 으로 입력해주세요"
						+enterStr +"/파티 로 파티명조회";
				}
				break;
			case "/파티탈퇴":
			case "/ㅍㅌㅌㅌ":
				if (param1 != null && !param1.equals("")) {
					try {
						raidDel(reqMap);
						val = raidSearch(reqMap);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
					}
				}else {
					val = "/파티탈퇴 파티명 닉네임으로 입력해주세요";
				}
				break;
			case "/ㄱㄹㄷ": case "/그리드":
				if (param1 != null && !param1.equals("")) {
					param0="/ㄱㄹㄷ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						//val = supporters(param1);
						val = arkGridSearch(param1);
						//val+= tossAccount2();
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= enterStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㄱㄹㄷ 캐릭명 으로 입력해주세요";
				}
				break;	
			case "/장비":
			case "/정보":
			case "/ㅈㅂ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅈㅂ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						//val = supporters(param1);
						val = newnewEquipSearch(param1);
						//val+= tossAccount2();
						
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= enterStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㅈㅂ 캐릭명 으로 입력해주세요";
				}
				break;
			case "/떠상": case "/ㄸㅅ":
				int serverId = 5; // 카단
				try {
					val= botExtService.buildMerchantMessage(serverId);
				}catch(Exception e) {
					e.printStackTrace();
				}
				break;
			case "/치적": case "/ㅊㅈ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅊㅈ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						String ordUserId=param1;
						String userId = URLEncoder.encode(ordUserId, "UTF-8");
						// +는 %2B로 치환한다
						String paramUrl = "https://www.daloa.xyz/api/critical/" + userId ;
						
						val = ext.buildCritMessageFromUrl(paramUrl);
						val+="https://www.daloa.xyz"+allSeeStr ;
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						/*
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= enterStr;
							val+= hs.get("RES");
						}
						*/
					}
				}else {
					return "/치적 캐릭명 으로 입력해주세요";
				}
				
				break;
				/*
			case "/초월": case "/엘릭서":
			case "/ㅊㅇ": case "/ㅇㄹㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅊㅇ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supportersIcon(param1);
						val+= limitSearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> hs = botService.selectIssueCase(reqMap);
						if(hs !=null && hs.size()>0) {
							val+= enterStr+hs.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= hs.get("RES");
						}
					}
				}else {
					return "/ㅊㅇ 캐릭명 또는 /ㅇㄹㅅ 캐릭명 으로 입력해주세요";
				}
				break;
				*/
			case "/내실":
			case "/ㄴㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㄴㅅ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supportersIcon(param1);
						val+= collectionSearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㄴㅅ 캐릭명 으로 입력해주세요";
				}
				break;
			
			case "/악세":
			case "/ㅇㅅ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅇㅅ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supportersIcon(param1);
						val+= accessorySearch(param1);
					} catch (Exception e) {
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㅇㅅ 캐릭명 으로 입력해주세요";
				}
				break;		
			case "/부캐":
			case "/ㅂㅋ":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅂㅋ";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1+" "+param2;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					
					int limitLv =50;
					try {
						limitLv = Integer.parseInt(param2);
					}catch(Exception e) {
						limitLv = 50;
					}
					try {
						val = supportersIcon(param1);
						val+= subCharacterInfoSearch1(param1,limitLv);
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㅂㅋ 캐릭명 으로 입력해주세요";
				}
				break;
			case "/부캐2":
			case "/ㅂㅋ2":
				if (param1 != null && !param1.equals("")) {
					param0="/ㅂㅋ2";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supportersIcon(param1);
						val+= subCharacterInfoSearch2(param1);
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/ㅂㅋ2 캐릭명 으로 입력해주세요";
				}
				break;
			case "/주급":
				if (param1 != null && !param1.equals("")) {
					param0="/주급";
					param1 = param1.trim();
					
					replace_param = botService.selectBotWordReplace(reqMap);
					if(replace_param!=null && !replace_param.equals("")) {
						param1 = replace_param;
					}
					
					fulltxt = param0+" "+param1;
					org_fulltxt = fulltxt;
					reqMap.put("fulltxt", fulltxt);
					try {
						val = supportersIcon(param1);
						val+= subCharacterGoldSum(param1);
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
						
						HashMap<String,Object> h = botService.selectIssueCase(reqMap);
						if(h !=null && h.size()>0) {
							val+= enterStr+h.get("INSERT_DATE")+ "에 최종조회된 내용 불러오기입니다.";
							val+= anotherMsgStr;
							val+= h.get("RES");
						}
					}
				}else {
					return "/주급 캐릭명 으로 입력해주세요";
				}
				break;
			
				/*
			case "/전투력":
			case "/ㅈㅌㄹ":
				param0="/ㅈㅌㄹ";
				param1 = param1.trim();
				
				replace_param = botService.selectBotWordReplace(reqMap);
				if(replace_param!=null && !replace_param.equals("")) {
					param1 = replace_param;
				}
				
				fulltxt = param0+" "+param1;
				org_fulltxt = fulltxt;
				reqMap.put("fulltxt", fulltxt);
				if (param1 != null && !param1.equals("")) {
					try {
						//val = "v0.3으로 패치중입니다.";
						
						val = supportersIcon(param1);
						val+= sub.sumTotalPowerSearch(param1,saveMap);
						if(val!=null && !val.equals("")) {
							//val+= tossAccount2();
						}
						
						
						
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
					}
				}else {
					return "/ㅈㅌㄹ 캐릭명 으로 입력해주세요";
				}
				
				try {
					
					if(!saveMap.get("score").toString().equals("0")) {
						saveMap.put("targetGb", "1");
						botService.upsertBotPowerRankTx(saveMap);
					}
				}catch(Exception e) {
					System.out.println("전투력 저장만안됨");
				}
				break;
			case "/전투력2":
			case "/ㅈㅌㄹ2":
				param0="/ㅈㅌㄹ2";
				param1 = param1.trim();
				
				replace_param = botService.selectBotWordReplace(reqMap);
				if(replace_param!=null && !replace_param.equals("")) {
					param1 = replace_param;
				}
				
				fulltxt = param0+" "+param1;
				org_fulltxt = fulltxt;
				reqMap.put("fulltxt", fulltxt);
				if (param1 != null && !param1.equals("")) {
					try {
						val = supportersIcon(param1);
						val += param1+" 캐릭터 전투력 상세"+enterStr;
						HashMap<String,Object> charMap = sub.sumTotalPowerSearch2(param1);
						sub.sumTotalPowerSearchByMainChar(charMap,saveMap);
						val+= saveMap.get("resMsg");
						
						val+= enterStr;
						val+= enterStr;
						val+= "/정보 와 동기화 되어있습니다. 해당 명령어는 상세내역만을 보여줍니다.";
						if(val!=null && !val.equals("")) {
							//val+= tossAccount2();
						}
					} catch (Exception e) {
						e.printStackTrace();
						val = errorCodeMng(e,reqMap);
						val+=enterStr+param1+" 으로 조회됨";
					}
				}else {
					return "/ㅈㅌㄹ2 캐릭명 으로 입력해주세요";
				}
				

				try {
					if(!saveMap.get("score").toString().equals("0")) {
						saveMap.put("targetGb", "2");
						botService.upsertBotPowerRankTx(saveMap);
					}
				}catch(Exception e) {
					System.out.println("전투력 저장만안됨");
				}
				break;
				*/
				/*
			case "/랭킹": case "/ㄹㅋ":
				
				String guildName ="";
				switch (roomName) {
					case "로아냥떼":
					case "로아냥떼 도박장":
						guildName = "냥떼목장";
						break;
					case "카단 포핑":
						guildName = "포핑";
						break;
					case "미움받을용기 수다방":
						guildName = "미움받을용기";
						break;
					case "test123":
					case "test":
						guildName = "미움받을용기";
						break;
					default:
						guildName = "";
						break;
				}
				
				if(guildName.equals("")) {
					break;
				}
				
				val +="인게임과 무관한 람쥐봇 전투력입니다!"+enterStr+enterStr;
				
				reqMap.put("guildName", guildName);
				reqMap.put("targetGb", "1");
				hs = botService.selectRoomBotPowerRank(reqMap);
				val +=roomName+" 원정대 TOP10 (v1.2)"+enterStr;
				
				
				for(HashMap<String,Object> hm : hs) {
					String starYn="";
					String star="";
					
					try {
						starYn = hm.get("STAR_YN").toString();	
						star+= sp_icon(Integer.parseInt(starYn));
					}catch (Exception e){
						starYn = "";
						star+="　";
					}
					
					val += star +hm.get("CHAR_NAME")+ " : "+hm.get("SCORE")+enterStr ;
				}
				
				val +=enterStr;
				reqMap.put("targetGb", "2");
				hs = botService.selectRoomBotPowerRank(reqMap);
				val +=roomName+" 캐릭터 TOP10 (v1.2)"+enterStr;
				
				for(HashMap<String,Object> hm : hs) {
					String starYn="";
					String star="";
					try {
						starYn = hm.get("STAR_YN").toString();	
						star+= sp_icon(Integer.parseInt(starYn));
					}catch (Exception e){
						starYn = "";
						star+="　";
					}
					val += star +hm.get("CHAR_NAME")+ " : "+hm.get("SCORE")+enterStr ;
				}
				
				val +=enterStr;
				//val +="원정대 점수 갱신: /전투력"+enterStr;
				//val +="캐릭터 점수 갱신: /정보"+enterStr;
				break;
				*/
				/*
			case "/랭킹2": case "/ㄹㅋ2":
				List<HashMap<String,Object>> hs2;

				String guildName2 ="";
				switch (roomName) {
					case "로아냥떼":
					case "로아냥떼 도박장":
						guildName = "냥떼목장";
						break;
					case "카단 포핑":
						guildName = "포핑";
						break;
					case "미움받을용기 수다방":
						guildName = "미움받을용기";
						break;
					case "test123":
					case "test":
						guildName = "냥떼목장";
						break;
					default:
						guildName = "";
						break;
				}
				
				if(guildName.equals("")) {
					break;
				}
				reqMap.put("guildName", guildName);
				reqMap.put("targetGb", "2");
				reqMap.put("classGb", "D");
				hs = botService.selectRoomBotPowerRank(reqMap);
				val +=roomName+" 캐릭터(딜러) TOP10"+enterStr;
				
				
				for(HashMap<String,Object> hm : hs) {
					String starYn="";
					String star="";
					try {
						starYn = hm.get("STAR_YN").toString();	
						star+= sp_icon(Integer.parseInt(starYn));
					}catch (Exception e){
						starYn = "";
						star+="　";
					}
					val += star +hm.get("CHAR_NAME")+ " : "+hm.get("SCORE")+enterStr ;
				}
				
				val +=enterStr;
				reqMap.put("classGb", "S");
				hs = botService.selectRoomBotPowerRank(reqMap);
				val +=roomName+" 캐릭터(서폿) TOP10"+enterStr;
				
				for(HashMap<String,Object> hm : hs) {
					String starYn="";
					String star="";
					try {
						starYn = hm.get("STAR_YN").toString();	
						star+= sp_icon(Integer.parseInt(starYn));
					}catch (Exception e){
						starYn = "";
						star+="　";
					}
					val += star +hm.get("CHAR_NAME")+ " : "+hm.get("SCORE")+enterStr ;
				}
				
				val +=enterStr;
				break;
				/*
			case "/포인트": case "/ㅍㅇㅌ":
				reqMap.put("newUserName", sender);
				
				HashMap<String,Object> point_map_one = botService.selectBotPointRankOne(reqMap);
				if(point_map_one == null) {
					return "";
				}
				
				int lv = Integer.parseInt(point_map_one.get("WEAPON").toString());
				
				HashMap<String, Object> weaponInfo = play.getWeaponStatsForPoint(reqMap);
			    
			    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
			    int accLv = Integer.parseInt(weaponInfo.get("acc_level").toString());
			    int accMaxLv = Integer.parseInt(weaponInfo.get("acc_max_level").toString());
			    int acc_apply_level = Integer.parseInt(weaponInfo.get("acc_apply_level").toString());
			    double part_of_weapon_crit = Double.parseDouble(weaponInfo.get("part_of_weapon_crit").toString());
			    double part_of_acc_crit = Double.parseDouble(weaponInfo.get("part_of_acc_crit").toString());
			    int weaponMin = Integer.parseInt(weaponInfo.get("part_of_min_weapon").toString());
			    int weaponMax = Integer.parseInt(weaponInfo.get("part_of_max_weapon").toString());
			    int accMin = Integer.parseInt(weaponInfo.get("part_of_min_acc").toString());
			    int accMax = Integer.parseInt(weaponInfo.get("part_of_max_acc").toString());
			    int hit = Integer.parseInt(weaponInfo.get("hit").toString());
				
				val += "❤️"+point_map_one.get("TOT")+"누적획득 포인트: "+weaponInfo.get("sum_score")+" p"+ enterStr+ 
					   "⚔"+"무기: +"+weaponLv+" lv"+point_map_one.get("WEAPON_USE")+enterStr+
					   "⚔"+"악세: +"+accLv+" lv"+point_map_one.get("ACC_USE")+enterStr+
					   "✨"+"공격력: "+weaponMin+"~"+weaponMax+" (치확: "+(int)(part_of_weapon_crit*100)+"%)"+enterStr+
					   "✨"+"+악세: "+accMin+"~"+accMax+" (치확: "+(int)(part_of_acc_crit*100)+"%)"+enterStr+
					   "　 ‡　악세달성 최고레벨 "+accMaxLv+" lv 적용"+enterStr+
					   "✨"+"+반지: "+hit+ " lv (명중: "+(int)(hit)+"%)"+enterStr+enterStr;
				val += "⚅언리밋⚅"+ enterStr+
					   "⚔근력강화: +"+weaponInfo.get("limit_str")+ enterStr+
					   "⚔방어강화: +"+weaponInfo.get("limit_def")+ enterStr+
					   
					   
					   "⚔치명강화: +"+Math.round( (double) Double.parseDouble(weaponInfo.get("limit_cri").toString())*100) +"%"+ enterStr+
					   "⚔맥포강화: +"+weaponInfo.get("limit_limit")+ enterStr+enterStr;
					   //"⏰"+point_map_one.get("ATTENDANCE")+ enterStr+
					   //"⚅"+point_map_one.get("DICE")+enterStr+enterStr ;
					   //"✨"+point_map_one.get("GAMBLE_WIN")+enterStr +
					   //"⚾"+point_map_one.get("BASEBALL_WIN")+enterStr +
					   //"⚔️"+point_map_one.get("FIGHT_SUM")+point_map_one.get("FIGHT_WIN")+point_map_one.get("FIGHT_LOSE")+enterStr+
					   
				
				
				val += "✨"+"보물상자";
				
				List<HashMap<String,Object>> userItemList = play.selectPointItemUserListForPoint(reqMap);
				try {
					if(reqMap.get("totalItemListSize")!=null && !reqMap.get("totalItemListSize").equals("")) {
						val += "(보물수집: "+reqMap.get("userItemListSize")+" / "+(reqMap.get("totalItemListSize"))+")"+enterStr;
						val += "　　　　(수집점수: "+reqMap.get("userSum")+" / "+(reqMap.get("totSum"))+")"+enterStr;
					}
					
				}catch(Exception e) {
					val += enterStr;
				}
				
				val +="→(전체/오픈/실패) : "+point_map_one.get("BOX_TOT")+enterStr+
				      "→(소모/환급) : "+point_map_one.get("BOX_TOT_POINT")+enterStr
				   ;
				
				if(userItemList !=null && userItemList.size()>0) {
					val += "보물 더보기"+allSeeStr;
				}
				for (HashMap<String,Object> userItem : userItemList) {
					val += enterStr+"Item No"+userItem.get("ITEM_NO")+" "+userItem.get("ITEM_NAME")+" " +userItem.get("ITEM_LV")+" lv";
					val += enterStr+userItem.get("ITEM_DESC")+enterStr;
				}
				
				break;
				*/
				/*
			case "/포인트랭킹": case "/ㅍㅇㅌㄹㅋ":
				List<HashMap<String,Object>> point_map = botService.selectBotPointRankAll(reqMap);
				val +=roomName+" 람쥐포인트"+enterStr;
				for(int i =0;i<point_map.size();i++) {
					switch(i) {
							
						default:
							val += rank_etc;
							break;
					}
					val += point_map.get(i).get("USER_NAME")+ " : "+point_map.get(i).get("SCORE")+enterStr ;
				}
				break;
				*/
			case "/항협": case "/항해": case "/항해협동": case "/ㅎㅎ":
				val = shipSearch();
				break;
				/*
			case "/가방": case "/ㄱㅂ":
				val = openBox(param1,param2);
				break;
				*/
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
				passYn=true;
				break;
			case "/저메추":
				String[] menu_list = { "피자", "탕수육", "치킨", "샐러드", "마라탕", "양꼬치", "삼겹살", "설렁탕", "김치찌개", "된장찌개", "삼치튀김", "참치마요",
						"회", "육회비빔밥", "냉면", "카레", "돈까스", "제육볶음", "오징어볶음", "떡볶이", "굶기", "초밥", "햄버거", "짜장면", "빵", "파스타", "닭발",
						"쭈꾸미", "낙지덮밥", "라면", "짜계치", "스팸과 흰밥", "간장계란밥", "간장게장", "참치회", "죽", "흰밥", "감자탕", "육전", "짬뽕", "순두부찌개",
						"지코바 양념치킨 모짜치즈 추가","닭가슴살", "단백질 음료", "바나나", "포케", "요아정", "홍콩반점 짜장밥", "호두과자", "라멘", "곱창.막창.대창", "소주", 
						"텐동", "유린기", "물회", "이베리코", "핫초코", "핫식스", "귤", "생강차", "부추전/파전"
					,"과메기","골뱅이무침","조개찜","해물찜","백고동","삼합","굴보쌈","1++한우","수박"	
				
				};
				Random random3 = new Random();
				val = menu_list[random3.nextInt(menu_list.length)];
				passYn=true;
				break;
			/*
			case "/챗":
				fulltxt = fulltxt.substring(param0.length()).trim();
				val = chatGptSearch(fulltxt,sender);
				break;
				*/
			case "/챗2":
				fulltxt = fulltxt.substring(param0.length()).trim();
				val = geminiSearch(fulltxt,sender);
				break;
			case "/챗": 
			case "/ㅊ": 
			case "/대화": 
			case "/ㅊㅌ": 
				fulltxt = fulltxt.substring(param0.length()).trim();
				if(fulltxt.length()!=0) {
					val = ai.search(fulltxt,roomName,sender);
					val = val.replaceAll("\\\"", "\"");;
					val = val.replaceAll("\n", enterStr);
			        //val = cutByBytesAndInsertMarker(val, 600, allSeeStr);
				}
				break;
				
			case "/ㅈㄱ":
			case "/젬값":
				param0="/ㅈㄱ";
				org_fulltxt = param0;
				try {
					val = marketSearch("gem");
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
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
						/*
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
						*/
						/*
					case "악세":
					case "ㅇㅅ":
						param0="/ㄱㅁㅈㅇㅅ";
						org_fulltxt = param0;
						try {
							val = "악세검색 오류가 있어 당분간 조회불가합니다.";//newSearchAcce();
						}catch(Exception e) {
							val = errorCodeMng(e,reqMap);
						}
						break;
						*/
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
				/*
			case "/ㄱㅁㅈㅇㅅ":
			case "/경매장악세":
				param0="/ㄱㅁㅈㅇㅅ";
				org_fulltxt = param0;
				try{
					val = "악세검색 오류가 있어 당분간 조회불가합니다.";
					//val = newSearchAcce();
				}catch(Exception e) {
					val = errorCodeMng(e,reqMap);
				}
				break;
			*/
			
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
				/*
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
				*/
			case "/게임공지":
				
				if(!sender.equals("전태환")) {
					return "";
				}
				List<String> list =botService.selectRoomList2(reqMap);
				
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
			case "/전체공지":
				if(!sender.equals("전태환")) {
					return "";
				}
				list =botService.selectRoomList1(reqMap);
				
				tmp_val ="";
				
				for(int i=0;i<list.size();i++) {
					if(i!=0) {
						tmp_val += listSeparatorStr;
					}
					tmp_val += list.get(i);
				}
				
				if(tmp_val.equals("")) {
					return "";
				}
				
				
				val = tmp_val;
				break;
			case "/별명":
				
				List<String> replaceList2 = botService.selectBotWordReplaceAll(reqMap);
				
				val += enterStr + "별명목록:" + enterStr;
				for (String word : replaceList2) {
					val += word + enterStr;
				}
				
				break;
			case "/별명등록": case "/별명추가":

				try {
					if (fulltxt.indexOf("=") < 0) {
						val = "별명등록 실패!, =을 포함해주세요";
					} else {

						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "별명등록 실패!, 특수문자 안되요!";
								return val;
							}
						}

						String[] txtList;
						fulltxt = fulltxt.substring(param0.length()).trim();
						txtList = fulltxt.split("=");
						reqMap.put("req", txtList[0].trim());
						reqMap.put("res", txtList[1].trim());

						botService.insertBotWordReplaceTx(reqMap);
						val = "별명등록 완료!";
					}
				} catch (Exception e) {
					val = "별명등록 실패!";
				}
				break;
			case "/별명제거": case "/별명삭제":
				try {
					if (fulltxt.indexOf("=") < 0) {
						val = "별명삭제 실패!, =을 포함해주세요";
					} else {
						for (String str : unable_save_list) {
							if (fulltxt.indexOf(str) >= 0) {
								val = "별명등록 실패!, 특수문자 안되요!";
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
							botService.deleteBotWordReplaceMasterTx(reqMap);
						} else {
							botService.deleteBotWordReplaceTx(reqMap);
						}

						val = "별명삭제 완료!";
					}
				} catch (Exception e) {
					val = "별명삭제 실패!";
				}
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
			case "/차단목록":
				masterYn = botService.selectBotWordSaveMasterCnt(reqMap);
				if (masterYn > 0) {
					hs = botService.selectBotBlock(reqMap);
					val+="유저 차단 목록"+enterStr;
					for(HashMap<String,Object> hm : hs) {
						val += hm.get("NAME")+enterStr ;
					}
					
					hs = botService.selectGamePlayYn(reqMap);
					val+="게임기능 차단 목록"+enterStr;
					for(HashMap<String,Object> hm : hs) {
						val += hm.get("NAME")+enterStr ;
					}
				} 
				
				
			case "/차단":
				masterYn = botService.selectBotWordSaveMasterCnt(reqMap);
				if (masterYn > 0) {
					if (param1 != null && !param1.equals("")) {
						switch(param1) {
							case "야구" :
							case "출석" :
							case "주사위":
							case "뽑기" :
							case "강화" :
							case "결투" :
								reqMap.put("playYn", "0");
								botService.updateGamePlayYnTx(reqMap);
								val = "게임 차단 완료!";
								break;
							default:
								botService.insertBotBlockTx(reqMap);
								val = "유저 차단 완료!";
								break;
						}
					}else {
						val = "차단 가능 목록 : 야구/출석/주사위/뽑기/강화/결투/카톡프로필명";
					}
				} 
				break;	
			case "/차단해제":
				masterYn = botService.selectBotWordSaveMasterCnt(reqMap);
				if (masterYn > 0) {
					if (param1 != null && !param1.equals("")) {
						switch(param1) {
							case "야구" :
							case "출석" :
							case "주사위":
							case "뽑기" :
							case "강화" :
							case "결투" :
								reqMap.put("playYn", "1");
								botService.updateGamePlayYnTx(reqMap);
								val = "게임 차단 해제 완료!";
								break;
							default:
								botService.deleteBotBlockTx(reqMap);
								val = "유저 차단 해제 완료!";
								break;
						}
					}else {
						val = "차단해제 가능 목록 : 야구/출석/주사위/뽑기/강화/결투/카톡프로필명";
					}
					
					
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
			case "/명령어": case "/람쥐봇": case "/ㄹㅈㅂ":
				val = botService.selectBotManual(reqMap);
				passYn=true;
				break;
			case "/게임":
				val = botService.selectBotManualG(reqMap);
				passYn=true;
				break;
			case "/테스트2":
				passYn=true;
				break;
			case "/단어목록": case "/단어조회": case "/단어": case "/ㄷㅇ":
			
				List<String> wordList = botService.selectBotWordSaveAll(reqMap);
				val += enterStr + "단어목록:" + enterStr;
				for (String word : wordList) {
					val += word + enterStr;
				}
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
				
				String db_input_val =val;
				if(param0.equals("/ㅈㅂ")) {
					try{
						db_input_val = val.split(allSeeStr)[0];
					}catch(Exception e) {
						db_input_val = val;
					}
				}
				
				reqMap.put("res", db_input_val);
				reqMap.put("userName", org_userName);
				botService.insertBotWordHisTx(reqMap);
			}
			
				
		}
		
		

		return val;
	}
	String raidListSearch(HashMap<String,Object> reqMap) throws Exception {
		List<HashMap<String,Object>> raidList = botService.selectBotRaidSaveListAll(reqMap);
		String count = "";
		String raidName="";
		String remark="";
		String resMsg = "";
		
		
		for(HashMap<String,Object> hs:raidList) {
			raidName = hs.get("RAID_NAME").toString();
			remark   = hs.get("REMARK").toString();
			count    = hs.get("CNT").toString();
			
			resMsg += "파티명:" + raidName+ "　(현재 "+count+" 명)"+enterStr;
			resMsg += "설명: "+remark;
			resMsg += enterStr;
			resMsg += enterStr;
		}
		
		return resMsg;
	}
	
	String raidSearch(HashMap<String,Object> reqMap) throws Exception {
		List<HashMap<String,Object>> charList = botService.selectBotRaidSaveAll(reqMap);
		
		if(charList ==null) {
			return "해당 파티명이 없습니다.";
		}
		List<HashMap<String,Object>> raidOne = botService.selectBotRaidSaveListAll(reqMap);
		HashMap<String,Object> raidInfo = raidOne.get(0);
		List<HashMap<String,Object>> attackerList = new ArrayList<>();
		List<HashMap<String,Object>> supporterList = new ArrayList<>();
		
		HashMap<String,Object> charInfo;
		String remark ="";
		
		int count = 0;
		for(HashMap<String,Object> hs:charList) {
			String ordUserId=hs.get("CHAR_NAME").toString();
			String userId = URLEncoder.encode(ordUserId, "UTF-8");
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
			String avgLv = armoryProfile.get("ItemAvgLevel").toString();
			String className = armoryProfile.get("CharacterClassName").toString();
			String combatPower ="";
			if(armoryProfile.get("CombatPower") != null) {
				combatPower = armoryProfile.get("CombatPower").toString();
			}
			
			charInfo.put("id",ordUserId);
			charInfo.put("lv", avgLv);
			charInfo.put("class", className);
			charInfo.put("combatPower", combatPower);
			
			Double lv = Double.parseDouble(avgLv.replaceAll(",", ""));
			if(className.equals("바드")
				  || className.equals("도화가")
				  || className.equals("홀리나이트")) {
				supporterList.add(charInfo);
				count++;
			}else {
				attackerList.add(charInfo);
				count++;
			}
			
			//remark = hs.get("REMARK").toString();
		}
		
		
		List<HashMap<String, Object>> sortedList1 = attackerList.stream()
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("lv").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		List<HashMap<String, Object>> sortedList2 = supporterList.stream()
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("lv").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		
		
		String resMsg ="파티명: "+raidInfo.get("RAID_NAME")+" 　"+raidInfo.get("CNT")+"/8"+enterStr;
		resMsg += "설명: "+raidInfo.get("REMARK")+enterStr;
		resMsg += "※딜러※"+enterStr;
		for(HashMap<String,Object> hs :sortedList1) {
			resMsg += "[" + LoaApiUtils.shortClassName(hs.get("class").toString()) + "] ";
			resMsg += "("+hs.get("lv")+")";
			resMsg += "("+hs.get("combatPower")+")";
			resMsg += hs.get("id");
			resMsg += enterStr;
		}
		resMsg += "※서포터※"+enterStr;
		for(HashMap<String,Object> hs :sortedList2) {
			resMsg += "[" + LoaApiUtils.shortClassName(hs.get("class").toString()) + "] ";
			resMsg += "("+hs.get("lv")+")";
			resMsg += "("+hs.get("combatPower")+")";
			resMsg += hs.get("id");
			resMsg += enterStr;
		}
		
		return resMsg;
	}
	String raidAdd(HashMap<String,Object> reqMap) throws Exception {
		String resMsg ="";
		try {
			botService.insertBotRaidSaveTx(reqMap);
		}catch(Exception e) {
			return "이미 동명의 파티가 있거나 오류입니다"
					+enterStr+e.getMessage();
		}
		
		resMsg = raidSearch(reqMap);
		
		
		return resMsg;
	}
	String raidMod(HashMap<String,Object> reqMap) throws Exception {
		String resMsg ="";
		try {
			botService.updateBotRaidSaveTx(reqMap);
		}catch(Exception e) {
			return "이미 동명의 파티가 있거나 오류입니다"
					+enterStr+e.getMessage();
		}
		
		resMsg = raidSearch(reqMap);
		
		
		return resMsg;
	}
	String raidJoin(HashMap<String,Object> reqMap) throws Exception {
		String resMsg ="";
		try {
			botService.insertBotRaidJoinSaveTx(reqMap);
		}catch(Exception e) {
			return "/파티가입 파티명 캐릭명 으로 입력해주세요"
					+enterStr +"/파티 로 파티명조회"
					+enterStr+e.getMessage();
		}
		
		resMsg = raidSearch(reqMap);
		
		
		return resMsg;
	}
	void raidDel(HashMap<String,Object> reqMap) throws Exception {
		botService.insertBotRaidDelTx(reqMap);
	}
	
	

	String lotto() throws Exception {
		String val ="";
		
		Random random = new Random(); // 랜덤객체
		int[] lottoNumber = new int[6];
		
		for(int i = 0; i < 6; i++) {
			int number = random.nextInt(45) + 1;
			for(int j = 0; j < i; j++) {
				if(lottoNumber[j] == number) {
					number = random.nextInt(45) + 1;
					j = -1;
				}
			}
			lottoNumber[i] = number;
		}
		
		
		for(int number : lottoNumber) {
			val += number + " ";
		}
		
		return val;
	}
	
	String emotionMsg(String param0,String roomName,String sender) throws Exception {
		String val = "";
		String randKey = "";
		boolean imgcp=false;
		
		//selectBotImgMch param0 , roomName selectBotImgSaveAll
		HashMap<String,Object> reqMap = new HashMap<>();
		HashMap<String,Object> pointMap = new HashMap<>();
		
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
/*
		pointMap.put("roomName", roomName);
		pointMap.put("userName", sender);
		pointMap.put("cmd", param0);
		pointMap.put("score", 1);
		botService.insertBotPointRankTx(pointMap);
	*/	
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
		String retMsg4="";
		
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
			
			//로웬 등도 추가 예정
			//if(data_sub_list.get("CategoryName").equals("")) {
				
			//}
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
		//msg += enterStr + "종막[ "+enterStr+"[하 0G/노 0G]";
		msg += enterStr + "⭐그림자 레이드⭐";
		msg += enterStr + "✓고통-세르카"+enterStr+" ↳ [노 35,000G/하 44,000G]";
		msg += enterStr+" ↳ [나메 54,000G]";
		/*
		msg += enterStr + "✓절망-****"+enterStr+" ↳ [노 **,000G/하 **,000G]";
		msg += enterStr+" ↳ [나메 **,000G]";
		*/
		msg += enterStr ;
		msg += enterStr + "⭐어비스 던전⭐";
		msg += enterStr + "✓지평의 성당-아르세너스";
		msg += enterStr + " ↳ [1단계 30,000G(귀속)]";
		msg += enterStr + " ↳ [2단계 40,000G(귀속)]";
		msg += enterStr + " ↳ [3단계 50,000G(귀속)]";
		
		msg += enterStr ;
		msg += enterStr + "⭐카제로스 레이드⭐";
		msg += enterStr + "✓종막-카제로스"+enterStr+" ↳ [노 40,000G/하 52,000G]";
		msg += enterStr + "✓4막-아르모체"+enterStr+" ↳ [노 33,000G/하 42,000G]";
		msg += enterStr + "✓3막-모르둠"+enterStr+" ↳ [노 21,000G/하 27,000G]";
		//msg += enterStr + " ↳ (구)[노 28,000G/하 38,000G]";
		msg += enterStr + "✓2막-아브렐슈드"+enterStr+" ↳ [노 16,500G/하 23,000G]";
		//msg += enterStr + " ↳ (구)[노 21,500G/하 30,500G]";
		msg += enterStr + "✓1막-에기르"+enterStr+" ↳ [노 11,500G/하 18,000G]";
		//msg += enterStr + " ↳ (구) [노 15,500G/하 24,500G]";
		msg += enterStr + "✓서막-에키드나"+enterStr+" ↳ [노 6,100(*귀속)/하 7,200G]";
		//msg += enterStr + " ↳ (구) [노 3,650G (+3,650G)] [하 8,800G]";
		
		/*
		msg += enterStr ;
		msg += enterStr + "⭐강습 레이드⭐";
		msg += enterStr + "✓림레이크" + enterStr + " ↳ [노 10,000G/하 18,000G]";
		*/
		msg += enterStr ;
		msg += enterStr + "⭐에픽 레이드⭐";
		msg += enterStr + "✓베히모스 (신)[ 7,200G ]";
		//msg += enterStr + " ↳(구) [ 8,800G ]";
		
		
		msg += allSeeStr ;
		msg += enterStr + "⭐군단장 레이드⭐";
		msg += enterStr + "✓어둠-카멘";
		msg += enterStr + " ↳ 1-3[노 6,400G(*귀속)]";
		msg += enterStr + " ↳ 1-3[하 8,000G(*귀속)]";
		msg += enterStr + " ↳ 4[하 5,000G(*귀속)]";
		msg += enterStr + "✓질병-일리아칸";
		msg += enterStr + " ↳ [노 4,700G(*귀속)]";
		msg += enterStr + " ↳ [하 6,000G(*귀속)]";
		/*
		msg += enterStr + "✓몽환-아브렐슈드";
		msg += enterStr + " ↳ 1-3[노 3,000G/하 3,600G]";
		msg += enterStr + " ↳ 4 [노 1,600G/하 2,000G]";
		msg += enterStr + "✓광기-쿠크세이튼";
		msg += enterStr + " ↳ 3,000G";
		msg += enterStr + "✓욕망-비아키스";
		msg += enterStr + " ↳ [노 1,600G/하 2,400G]";
		msg += enterStr + "✓마수-발탄";
		msg += enterStr + " ↳ [노 1,200G/하 1,800G]";
		*/
		msg += enterStr;
		msg += enterStr + "⭐어비스 던전⭐";
		msg += enterStr + "✓혼돈의 상아탑";
		msg += enterStr + " ↳ [노 5,200G(*귀속)]";
		msg += enterStr + "   [하 7,200G(*귀속)]";
		msg += enterStr + "✓카양겔";
		msg += enterStr + " ↳ [노 3,300G(*귀속)]";
		msg += enterStr + " ↳ [하 4,300G(*귀속)]";
		
		
		msg += enterStr;
		msg += enterStr + "⭐싱글 모드⭐(싱글골드:미확정)";
		msg += enterStr + "✓모르둠 [10,500G (+10,500G)]";
		msg += enterStr + "✓아브렐슈드 [8,250G (+8,250G)]";
		msg += enterStr + "✓에기르 [5,750G (+5,750G)]";
		msg += enterStr + "✓에키드나 [6,100G(귀속)]";
		msg += enterStr + "✓카멘 [6,400G(귀속)]";
		msg += enterStr + "✓상아탑 [5,200G(귀속)]";
		msg += enterStr + "✓일리아칸 [4,700G(귀속)]";
		msg += enterStr + "✓카양겔 [3,300G(귀속)]";
		msg += enterStr + "✓아브렐슈드 [4,600G(귀속)]";
		msg += enterStr + "✓쿠크세이튼 [3,000G(귀속)]";
		msg += enterStr + "✓비아키스 [1600G(귀속)]";
		msg += enterStr + "✓발탄 [1200G(귀속)]";
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
/*
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
			HashMap<String, Object> new_refine_element = (HashMap<String, Object>)maps.get("new_refine_element");
			
			switch (equip.get("Type").toString()) {
			case "무기":
				
				String tmpWeaponLimit = LoaApiParser.parseLimitForLimit(limit_element);
				if(!tmpWeaponLimit.equals("")) {
					
					resEquip += enterStr +equip.get("Type").toString();
					
					if(new_refine_element.size()>0) {
						String newEnhanceInfo2="";
						newEnhanceInfo2 = Jsoup.parse((String) new_refine_element.get("value")).text();
						newEnhanceInfo2 = LoaApiUtils.filterText(newEnhanceInfo2);
						newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 30단계 - 기본 효과 \\+2%", "");
						newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 40단계 - 기본 효과 \\+3%", "");
						newEnhanceInfo2 = newEnhanceInfo2.replace("단계", "");
						newEnhanceInfo2 = StringUtils.leftPad( newEnhanceInfo2, 2, " ");
						resEquip += "[+"+newEnhanceInfo2+"]";
					}else {
						resEquip += "[+ 0]";
					}
					
					resEquip += " :" + tmpWeaponLimit;
					
					
					
					
					
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
				 
				resEquip += enterStr +equip.get("Type").toString();
				
				if(new_refine_element.size()>0) {
					String newEnhanceInfo2="";
					newEnhanceInfo2 = Jsoup.parse((String) new_refine_element.get("value")).text();
					newEnhanceInfo2 = LoaApiUtils.filterText(newEnhanceInfo2);
					newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 30단계 - 기본 효과 \\+2%", "");
					newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 40단계 - 기본 효과 \\+3%", "");
					newEnhanceInfo2 = newEnhanceInfo2.replace("단계", "");
					newEnhanceInfo2 = StringUtils.leftPad( newEnhanceInfo2, 2, " ");
					resEquip += "[+"+newEnhanceInfo2+"]";
				}else {
					resEquip += "[+ 0]";
				}
				//초월 정보 출력
				resEquip += " :" + LoaApiParser.parseLimitForLimit(limit_element)+enterStr;
				resEquip = LoaApiUtils.filterText(resEquip);

				//엘릭서 정보 출력 
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList,elixir_element);
				resEquip  +="　　　　◈"+LoaApiParser.parseElixirForLimit(equipElixirList,elixir_element,1);
				
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
	*/
	String engraveSearch(String userId) throws Exception{
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+enterStr+"원정대 각인" + enterStr+enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1540)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String mainCharName = sortedList.get(0).get("CharacterName").toString();
		
		HashMap<String,Object> resMap =new HashMap<>();
		Map<String, Object> armoryEngraving = new HashMap<>();
		List<Map<String,Object>> armoryEngraves = new ArrayList<>();
		List<HashMap<String,Object>> refreshEngraveList = new ArrayList<>();
		
		try {
			armoryEngraving = (Map<String, Object>) resMap.get("ArmoryEngraving");
		}catch(Exception e){
		}
		
		try {
			armoryEngraves = (List<Map<String, Object>>) armoryEngraving.get("ArkPassiveEffects");
			
			for (Map<String, Object> engrave : armoryEngraves) {
				HashMap<String,Object> refreshDataMap = LoaApiParser.engraveSelector(engrave.get("Name").toString(), engrave.get("Grade").toString(), engrave.get("Level").toString());
				if(!refreshEngraveList.contains(refreshDataMap)) {
					refreshEngraveList.add(refreshDataMap);
				}
			}
			
		}catch(Exception e){
		}
		
		List<HashMap<String, Object>> engraveUpd = sub.updOfTotEngrave(refreshEngraveList,mainCharName);
		resMsg += sub.msgOfTotEngrave(engraveUpd);
		
		return resMsg;
	}
	
	String arkGridSearch(String userId) throws Exception{
		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/armories/characters/" + userId;// + "?filters=equipment%2Bprofiles";
		String returnData;
		try {
			returnData = LoaApiUtils.connect_process(paramUrl);
		}catch(Exception e){
			System.out.println(ordUserId+" arkGridSearch "+e.getMessage());
			throw new Exception("E0004");
		}
		
		HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String, Object>>() {});

		Map<String, Object> arkGrid;
		
		
		String resMsg=ordUserId+ " 그리드 정보" + enterStr;
		
		try {
	        arkGrid = (Map<String, Object>) rtnMap.get("ArkGrid");
	    } catch(Exception e) {
	        System.out.println(userId + " ArkGrid");
	        throw new Exception("E0003");
	    }

	    String allSeeMsg = "";
	    String arkGridFullMsg = "";

	    try {

	        List<HashMap<String, Object>> slots = (List<HashMap<String, Object>>) arkGrid.get("Slots");

	        // 코어 + 젬 정보를 한 번에 담을 리스트
	        List<Map<String, Object>> coreList = new ArrayList<>();

	        // 젬 옵션 파싱용 패턴들
	        java.util.regex.Pattern pEff   = java.util.regex.Pattern.compile("의지력 효율\\s*:\\s*(\\d+)");
	        java.util.regex.Pattern pPoint = java.util.regex.Pattern.compile("([가-힣]+) 포인트\\s*:\\s*(\\d+)");
	        java.util.regex.Pattern pStat  = java.util.regex.Pattern.compile("\\[(.+?)]\\s*Lv\\.(\\d+)");

	        for (HashMap<String, Object> slot : slots) {
	            HashMap<String, Object> tooltip = new ObjectMapper().readValue(
	                    (String) slot.get("Tooltip"),
	                    new TypeReference<Map<String, Object>>() {}
	            );

	            HashMap<String, Object> maps = LoaApiParser.findElementForArkGrid(tooltip);

	            // 코어 타입 : "질서 - 해", "혼돈 - 달" 등
	            HashMap<String, Object> grid_core_type = (HashMap<String, Object>) maps.get("코어 타입");
	            HashMap<String, Object> grid_core_type_v = (HashMap<String, Object>) grid_core_type.get("value");
	            String coreTypeText = Jsoup.parse((String) grid_core_type_v.get("Element_001")).text(); // ex) "질서 - 해"

	            // 활성 포인트
	            int activePoint = Integer.parseInt(slot.get("Point").toString());

	            // 코어 옵션 (필요하면 사용)
	            HashMap<String, Object> grid_core_option = (HashMap<String, Object>) maps.get("코어 옵션");
	            HashMap<String, Object> grid_core_option_v = (HashMap<String, Object>) grid_core_option.get("value");
	            String coreOptionHtml = (String) grid_core_option_v.get("Element_001");
	            String coreOptionText = Jsoup.parse(coreOptionHtml).text();

	            String[] optionParts = coreOptionText.split("(?=\\[\\d+P\\])");
	            StringBuilder optionMsg = new StringBuilder();
	            for (String line : optionParts) {
	                if (line.trim().isEmpty()) continue;
	                int reqPoint = Integer.parseInt(line.substring(line.indexOf("[") + 1, line.indexOf("P")));
	                if (activePoint >= reqPoint) {
	                    optionMsg.append("(O)").append(line).append(enterStr);
	                } else {
	                    optionMsg.append("(X)").append(line).append(enterStr);
	                }
	            }

	            String grade = Objects.toString(slot.get("Grade"), "");          // 전설 / 유물 / 고대
	            String name  = Objects.toString(slot.get("Name"), "");           // "질서의 해 코어 : 비연참"

	            // 스킬명 추출 ("코어 : " 뒤쪽)
	            String skillName = "";
	            int colonIdx = name.indexOf(" : ");
	            if (colonIdx > -1 && colonIdx + 3 < name.length()) {
	                skillName = name.substring(colonIdx + 3);                    // ex) "비연참"
	            }

	            // 헤더용 한 줄 (위쪽, allSee쪽 모두에서 공통 사용)
	            String headerLine = "[" + grade + "] " + coreTypeText + " : " + skillName + " : " + activePoint;

	            // 정렬 키 계산 (질서/혼돈, 해/달/별)
	            int major;   // 질서(0) → 혼돈(1) → 기타(2)
	            if (coreTypeText.startsWith("질서"))      major = 0;
	            else if (coreTypeText.startsWith("혼돈")) major = 1;
	            else                                      major = 2;

	            int minor;   // 해(0) → 달(1) → 별(2) → 기타(3)
	            if (coreTypeText.contains("해"))      minor = 0;
	            else if (coreTypeText.contains("달")) minor = 1;
	            else if (coreTypeText.contains("별")) minor = 2;
	            else                                  minor = 3;

	            int sortKey = major * 10 + minor;

	            // ─────────────────────────────
	            // 이 코어에 속한 젬들 문자열 만들기
	            // ─────────────────────────────
	            StringBuilder coreGemMsg = new StringBuilder();

	            List<HashMap<String, Object>> gems = (List<HashMap<String, Object>>) slot.get("Gems");
	            for (HashMap<String, Object> gem : gems) {

	                HashMap<String, Object> gem_tooltip1 = new ObjectMapper().readValue(
	                        (String) gem.get("Tooltip"),
	                        new TypeReference<Map<String, Object>>() {}
	                );
	                HashMap<String, Object> gem_tooltip2 = LoaApiParser.findElementForArkGrid(gem_tooltip1);

	                // 젬 이름
	                HashMap<String, Object> 젬이름 = (HashMap<String, Object>) gem_tooltip2.get("젬 이름");
	                String fullGemName = Jsoup.parse((String) 젬이름.get("value")).text(); // ex) "질서의 젬 : 안정"

	                // "질서의 젬 : 안정" → 타입/이름 분리
	                String gemType = "";
	                String gemLabel = fullGemName;
	                java.util.regex.Matcher mName =
	                        java.util.regex.Pattern.compile("(.*)의 젬\\s*:\\s*(.*)").matcher(fullGemName);
	                if (mName.find()) {
	                    gemType = mName.group(1).trim();   // 질서 / 혼돈 등
	                    gemLabel = mName.group(2).trim();  // 안정 / 붕괴 등
	                }

	                // 젬 등급 (전설/유물/고대)
	                String gemGrade = Objects.toString(gem.get("Grade"), "");

	                // 젬 옵션 텍스트
	                HashMap<String, Object> 젬옵션 = (HashMap<String, Object>) gem_tooltip2.get("젬 옵션");
	                HashMap<String, Object> 젬옵션_v = (HashMap<String, Object>) 젬옵션.get("value");
	                
	                try {
	                	String rawOptText = Jsoup.parse((String) 젬옵션_v.get("Element_001")).text();

		                // 의지력 효율 / X 포인트
		                Integer effVal = null;
		                Integer pointVal = null;

		                java.util.regex.Matcher mEff = pEff.matcher(rawOptText);
		                if (mEff.find()) {
		                    effVal = Integer.parseInt(mEff.group(1));
		                }

		                java.util.regex.Matcher mPoint = pPoint.matcher(rawOptText);
		                String pointType = "";
		                if (mPoint.find()) {
		                    pointType = mPoint.group(1).trim();              // 질서 / 혼돈
		                    pointVal = Integer.parseInt(mPoint.group(2));
		                }

		                // [낙인력] Lv.3 / [추가 피해] Lv.4 / ...
		                List<Integer> lvList = new ArrayList<>();
		                java.util.regex.Matcher mStat = pStat.matcher(rawOptText);
		                while (mStat.find()) {
		                    String lvlStr = mStat.group(2).trim();
		                    try {
		                        lvList.add(Integer.parseInt(lvlStr));
		                    } catch (NumberFormatException ignore) {}
		                }

		                // 요약코드 [5514] = eff(5) + point(5) + 첫옵션Lv(1) + 두번째옵션Lv(4)
		                String summaryCode = "";
		                if (effVal != null && pointVal != null && lvList.size() >= 2) {
		                    int lv1 = lvList.get(0);
		                    int lv2 = lvList.get(1);
		                    summaryCode = "[" + effVal + "" + pointVal + "" + lv1 + "" + lv2 + "]";
		                }

		                // ─ 출력 구성 ─
		                // 한 줄: +[5514]질서 : 견고 (전설)
		                coreGemMsg.append("+");
		                if (!summaryCode.isEmpty()) {
		                    coreGemMsg.append(summaryCode);
		                }
		                if (!gemType.isEmpty()) {
		                    coreGemMsg.append(gemType).append(" : ").append(gemLabel);
		                } else {
		                    coreGemMsg.append(fullGemName);
		                }
		                if (!gemGrade.isEmpty()) {
		                    coreGemMsg.append(" (").append(gemGrade).append(")");
		                }
		                coreGemMsg.append(enterStr);
	                }catch(Exception e) {
	                	
	                }
	                
	            }

	            Map<String, Object> coreInfo = new HashMap<>();
	            coreInfo.put("header", headerLine);
	            coreInfo.put("sortKey", sortKey);
	            coreInfo.put("gemMsg", coreGemMsg.toString());

	            coreList.add(coreInfo);
	        }

	        // ─────────────────────────────
	        // 코어 정렬 후 상단 출력
	        // ─────────────────────────────
	        arkGridFullMsg += "§아크그리드" + enterStr;

	        coreList.sort(Comparator.comparingInt(o -> (Integer) o.get("sortKey")));

	        for (Map<String, Object> c : coreList) {
	            String headerLine = Objects.toString(c.get("header"), "");
	            arkGridFullMsg += headerLine + enterStr;
	        }

	        // 젬 전체 효과
	        List<HashMap<String, Object>> effects= (List<HashMap<String, Object>>) arkGrid.get("Effects");
	        arkGridFullMsg += enterStr + "§젬 전체 효과" + enterStr;
	        for (HashMap<String, Object> effect : effects) {
	            arkGridFullMsg += Jsoup.parse((String) effect.get("Tooltip")).text() + enterStr;
	        }
	        arkGridFullMsg += enterStr;

	        // ─────────────────────────────
	        // allSeeStr(===) 이후: 코어별 젬 상세
	        // ─────────────────────────────
	        StringBuilder allSeeSb = new StringBuilder();
	        for (Map<String, Object> c : coreList) {
	            String headerLine = Objects.toString(c.get("header"), "");
	            String gemMsg     = Objects.toString(c.get("gemMsg"), "");

	            allSeeSb.append(headerLine).append(enterStr);
	            allSeeSb.append(gemMsg);
	            allSeeSb.append(enterStr); // 코어 사이 공백 줄
	        }
	        allSeeMsg = allSeeSb.toString();

	    } catch(Exception e) {
	        arkGridFullMsg = "";
	        e.printStackTrace();
	        System.out.println("아크그리드없음");
	    }

	    // allSeeStr은 기존처럼 "===\n" 같은 값을 갖고 있다고 가정
	    resMsg += arkGridFullMsg + allSeeStr + allSeeMsg;
		return resMsg;
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
		Map<String, Object> arkGrid;
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
			arkGrid = (Map<String, Object>) rtnMap.get("ArkGrid");
		}catch(Exception e){
			System.out.println(userId+" ArkGrid");
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
		String itemAvgLevel = armoryProfile.get("ItemAvgLevel").toString();
		String characterLevel = armoryProfile.get("CharacterLevel").toString();
		String expeditionLevel = armoryProfile.get("ExpeditionLevel").toString();
		String className = armoryProfile.get("CharacterClassName").toString();

		String title = "";
		if(armoryProfile.get("Title")!=null) {
			title=armoryProfile.get("Title").toString();
		}
		
		String combatPower ="";
		if(armoryProfile.get("CombatPower") != null) {
			combatPower = armoryProfile.get("CombatPower").toString();
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
		//String totLimit ="";
		//int totElixir =0;
		
		String abillityStoneMsg = "";
		String accessoryMsg = "";
		String braceletMsg = "";
		
		String nakwonMsg ="";
		
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
			HashMap<String, Object> nakwon = (HashMap<String, Object>)maps.get("nakwon");
			
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
					newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 30단계 - 기본 효과 \\+2%", "");
					newEnhanceInfo2 = newEnhanceInfo2.replaceAll(" 40단계 - 기본 효과 \\+3%", "");
					newEnhanceInfo2 = newEnhanceInfo2.replace("단계", "");
					newEnhanceInfo2 = StringUtils.leftPad( newEnhanceInfo2, 2, " ");
					resField1 += "[+"+newEnhanceInfo2+"]";
				}else {
					resField1 += "[+ 0]";
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
				/*
				resField2 += equip.get("Type").toString()+" :";//초
				resField2 += LoaApiParser.parseLimitForLimit(limit_element);
				resField2 = LoaApiUtils.filterText(resField2);
				resField2 += enterStr;
				
				if(!equip.get("Type").toString().equals("무기")) {
					resField3 += equip.get("Type").toString()+" :";//엘
					resField3 += LoaApiParser.parseElixirForLimit(null,elixir_element,0);
					resField3 += enterStr;
				}*/
				/*
				//초월
				//초월합계는 장비에서가져옴 
				String tmpLimit = LoaApiParser.parseLimit(limit_element);
				if(!tmpLimit.equals("")) {
					totLimit = tmpLimit;
				}
				//엘릭서
				totElixir +=LoaApiParser.parseElixirForEquip(equipElixirList, elixir_element);
				*/
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
						break;
					case "아이템 티어 4":
						accessoryMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						accessoryMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						accessoryMsg += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						accessoryMsg += enterStr;
						
						switch (className) {
							case "바드":
							case "도화가":
							case "홀리나이트":
								accessoryMsg += LoaApiParser.findBraceletOptions("S",1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
								break;
							default:
								accessoryMsg += LoaApiParser.findBraceletOptions("D",1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
						}
						
						accessoryMsg += enterStr;
						break;
				}
				break;
			case "팔찌":
				braceletMsg += "팔찌 정보 ";
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
					case "아이템 티어 3":
						braceletMsg += LoaApiParser.findBraceletOptions("",0,bracelet.get("Element_001").toString());
						break;
					case "아이템 티어 4":
						if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text().indexOf("고대")>=0 ) {
							//고대팔찌 우선적용 
							switch (className) {
								case "바드":
								case "도화가":
								case "홀리나이트":
									braceletMsg += LoaApiParser.findBraceletOptions("S",5,bracelet.get("Element_001").toString());
									break;
								default:
									braceletMsg += LoaApiParser.findBraceletOptions("D",4,bracelet.get("Element_001").toString());
							}
							
						}else {
							braceletMsg += LoaApiParser.findBraceletOptions("",0,bracelet.get("Element_001").toString());
						}
						
						break;
				}
				braceletMsg += enterStr;
				break;
			case "보주":
				try {
					nakwonMsg += "";
					HashMap<String, Object> nakwonTooltip = (HashMap<String, Object>) nakwon.get("value");
					String nakwonElelment001 = Jsoup.parse((String)nakwonTooltip.get("Element_001")).text();
					
					Pattern p = Pattern.compile("낙원력\\s*:\\s*(\\d+)");
					Matcher m = p.matcher(nakwonElelment001);
	
					if (m.find()) {
					    String value = m.group(1);  // 숫자만 추출됨 (83597)
					    nakwonMsg += "⭐낙원력 : " + value;
					}
				}catch(Exception e) {
					
				}
				break;	
			default:
			continue;
			}
		}
		
		String arkGridMsg ="아크그리드"+enterStr;
		int ark_질서_해=-1;
		int ark_질서_달=-1;
		int ark_질서_별=-1;
		int ark_혼돈_해=-1;
		int ark_혼돈_달=-1;
		int ark_혼돈_별=-1;
		
		String ark_질서_해_msg="";
		String ark_질서_달_msg="";
		String ark_질서_별_msg="";
		String ark_혼돈_해_msg="";
		String ark_혼돈_달_msg="";
		String ark_혼돈_별_msg="";
		String arkGridFullMsg="§아크그리드"+enterStr;
		try {
			List<HashMap<String, Object>> slots= (List<HashMap<String, Object>>) arkGrid.get("Slots");
			for (HashMap<String, Object> slot : slots) {
				HashMap<String, Object> tooltip = new ObjectMapper().readValue((String) slot.get("Tooltip"),
						new TypeReference<Map<String, Object>>() {
						});
				
				//System.out.println(slot);
				HashMap<String, Object> maps = LoaApiParser.findElementForArkGrid(tooltip);
				
				// 코어 타입
				HashMap<String, Object> grid_core_type = (HashMap<String, Object>) maps.get("코어 타입");
				HashMap<String, Object> grid_core_type_v = (HashMap<String, Object>) grid_core_type.get("value");
				String grid_core_type_v_e1 = Jsoup.parse((String) grid_core_type_v.get("Element_001")).text();

				// 활성 포인트
				int activePoint = Integer.parseInt(slot.get("Point").toString());

				// 코어 옵션
				HashMap<String, Object> grid_core_option = (HashMap<String, Object>) maps.get("코어 옵션");
				HashMap<String, Object> grid_core_option_v = (HashMap<String, Object>) grid_core_option.get("value");
				String coreOptionHtml = (String) grid_core_option_v.get("Element_001");

				// HTML 태그 제거 후 옵션 추출
				String coreOptionText = Jsoup.parse(coreOptionHtml).text();
				
				String[] optionParts = coreOptionText.split("(?=\\[\\d+P\\])");

				StringBuilder optionMsg = new StringBuilder();
				for (String line : optionParts) {
				    // [10P], [14P] 형태의 포인트 값 추출
				    int reqPoint = Integer.parseInt(line.substring(line.indexOf("[") + 1, line.indexOf("P")));
				    if (activePoint >= reqPoint) {
				        optionMsg.append("(O)").append(line).append(enterStr);
				    } else {
				        optionMsg.append("(X)").append(line).append(enterStr);
				    }
				}
				
				//grid_core_type_v_e1 => 질서 - 해/ 혼돈 - 달
				
				switch(grid_core_type_v_e1) {
					case "질서 - 해":
						ark_질서_해 = activePoint;
						break;
					case "질서 - 달":
						ark_질서_달 = activePoint;
						break;
					case "질서 - 별":
						ark_질서_별 = activePoint;
						break;
					case "혼돈 - 해":
						ark_혼돈_해 = activePoint;
						break;
					case "혼돈 - 달":
						ark_혼돈_달 = activePoint;
						break;
					case "혼돈 - 별":
						ark_혼돈_별 = activePoint;
						break;
				}
				
				//arkGridMsg += slot.get("Grade") + " " + grid_core_type_v_e1+ ", 활성포인트: " + activePoint + enterStr ;
				arkGridFullMsg += slot.get("Grade") + " " + slot.get("Name")+ " : " + activePoint + enterStr + optionMsg.toString()+enterStr;
				

				List<HashMap<String, Object>> gems = (List<HashMap<String, Object>>)slot.get("Gems");
				for(HashMap<String, Object> gem: gems) {
					/*
					HashMap<String, Object> gem_tooltip1 = new ObjectMapper().readValue((String)gem.get("Tooltip"), new TypeReference<Map<String, Object>>(){});
					HashMap<String, Object> gem_tooltip2 = LoaApiParser.findElementForArkGrid(gem_tooltip1);
					HashMap<String, Object> 젬옵션 = (HashMap<String, Object>)gem_tooltip2.get("젬 옵션");
					HashMap<String, Object> 젬옵션_v =(HashMap<String, Object>) 젬옵션.get("value");
					String raw = Jsoup.parse((String) 젬옵션_v.get("Element_001")).text();
					String formatted = raw.replaceAll("(?=의지력 효율|혼돈 포인트|\\[아군 피해 강화]|\\[보스 피해]|\\[낙인력]|\\[아군 공격 강화]|\\[공격력]|\\[추가 피해])", "\n");
					
					HashMap<String, Object> 젬이름 = (HashMap<String, Object>)gem_tooltip2.get("젬 이름");
					String gemName = Jsoup.parse((String) 젬이름.get("value")).text();
					gemName = gemName.replaceAll("-$", ""); // 끝에 오는 - 제거
					
					arkGridFullMsg += "+"+gemName + enterStr;
					
					
					String[] lines = formatted.split("\n");
					for (String line : lines) {
						if (!line.trim().isEmpty()) { // 공백줄 무시
							arkGridFullMsg += (" " + line)+enterStr;
						}
					}
					*/
					
				}
				
				
			}
			List<HashMap<String, Object>> effects= (List<HashMap<String, Object>>) arkGrid.get("Effects");
			arkGridFullMsg += enterStr+"§아크그리드 젬 전체 효과"+enterStr;
			for (HashMap<String, Object> effect : effects) {
				arkGridFullMsg += ""+/*effect.get("Name") + ""+ */Jsoup.parse((String) effect.get("Tooltip")).text()+enterStr;
			}
			
			arkGridFullMsg += enterStr;
			
		}catch(Exception e) {
			arkGridMsg ="";
			arkGridFullMsg="";
			System.out.println("아크그리드없음");
		}
		
		try {
		
		if(ark_질서_해==-1) {
			ark_질서_해_msg="X";
		}else {
			ark_질서_해_msg = String.valueOf(ark_질서_해);
		}
		if(ark_질서_달==-1) {
			ark_질서_달_msg="X";
		}else {
			ark_질서_달_msg = String.valueOf(ark_질서_달);
		}
		if(ark_질서_별==-1) {
			ark_질서_별_msg="X";
		}else {
			ark_질서_별_msg = String.valueOf(ark_질서_별);
		}
		if(ark_혼돈_해==-1) {
			ark_혼돈_해_msg="X";
		}else {
			ark_혼돈_해_msg = String.valueOf(ark_혼돈_해);
		}
		if(ark_혼돈_달==-1) {
			ark_혼돈_달_msg="X";
		}else {
			ark_혼돈_달_msg = String.valueOf(ark_혼돈_달);
		}
		if(ark_혼돈_별==-1) {
			ark_혼돈_별_msg="X";
		}else {
			ark_혼돈_별_msg = String.valueOf(ark_혼돈_별);
		}
		
		arkGridMsg +="질서(해/달/별) 　　 "+ark_질서_해_msg+" / "+ark_질서_달_msg+" / "+ark_질서_별_msg+enterStr;
		arkGridMsg +="혼돈(해/달/별) 　　 "+ark_혼돈_해_msg+" / "+ark_혼돈_달_msg+" / "+ark_혼돈_별_msg+enterStr;
		
		} catch(Exception e) {
			System.out.println("아크그리드파싱실패");
		}
		List<String> acceossory;
		List<String> accessoryList = new ArrayList<>();
		try {
			acceossory = totalAccessorySearch(rtnMap,userId,className,3);
			if(acceossory !=null) {
				accessoryList.addAll(acceossory);
			}
			
			
		} catch(Exception e) {
			accessoryList = null;
		}
		
		int g1ss = 0;
		int g1sj = 0;
		int g1sh = 0;
		int g1s = 0;
		int g1jj =0;
		int g1jh =0;
		int g1j =0;
		String acceMainMsg="";
		String acceMsg ="";
		String acceStr =""; 
		if(accessoryList !=null) {
			for (String g1 : accessoryList) {
				switch (g1) {
				case "상상":
					g1ss++;
					break;
				case "상중":
				case "중상":
					g1sj++;
					break;
				case "상하":
				case "하상":
					g1sh++;
					break;
				case "상":
					g1s++;
					break;
				case "중중":
					g1jj++;
					break;
				case "중하":
				case "하중":
					g1jh++;
					break;
				case "중":
					g1j++;
					break;
				}
			}			
			
			if(g1ss>0) {
				acceMsg += "상상:"+g1ss+" ";
				acceStr+=String.valueOf(g1ss);
			}
			if(g1sj>0) {
				acceMsg += "상중:"+g1sj+" ";
				acceStr+=String.valueOf(g1sj);
			}
			if(g1sh>0) {
				acceMsg += "상하:"+g1sh+" ";
				acceStr+=String.valueOf(g1sh);
			}
			if(g1s>0) {
				acceMsg += "상단:"+g1s+" ";
				acceStr+=String.valueOf(g1s);
			}
			
			if(g1jj>0) {
				acceMsg += "중중:"+g1jj+" ";
				acceStr+=String.valueOf(g1jj);
			}
			if(g1jh>0) {
				acceMsg += "중하:"+g1jh+" ";
				acceStr+=String.valueOf(g1jh);
			}
			if(g1j>0) {
				acceMsg += "중단:"+g1j+" ";
				acceStr+=String.valueOf(g1j);
			}
			
			switch(acceStr.length()) {
				case 1:
					acceMainMsg="악세 　　　　　";
					break;
				case 2:
					acceMainMsg="악세 　　　　";
					break;
				case 3:
					acceMainMsg="악세 　　　";
					break;
				case 4:
					acceMainMsg="악세 　　";
					break;
				case 5:
					acceMainMsg="악세 　";
					break;
			}
			acceMainMsg += acceMsg;
			
		}
		
		
		
		//String setField="";
		String elixirField="";
		
		/*for(String set:LoaApiParser.getSetList()) {
			int cnt0=0;
			cnt0 += Collections.frequency(equipSetList, set);
			if(cnt0 > 0) {
				setField = setField+cnt0+set;
			}
			
		}*/
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
		String arkpoint1Lv="0";
		String arkpoint2Lv="0";
		String arkpoint3Lv="0";
		for(HashMap<String,Object> pt:arkPassivePt) {
			switch(pt.get("Name").toString()) {
			case "진화":
				arkpoint1=pt.get("Value").toString();
				arkpoint1Lv=pt.get("Description").toString();
				break;
			case "깨달음":
				arkpoint2=pt.get("Value").toString();
				arkpoint2Lv=pt.get("Description").toString();
				break;
			case "도약":
				arkpoint3=pt.get("Value").toString();
				arkpoint3Lv=pt.get("Description").toString();
				break;
			}
		}
		
		int tier = 3;
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", ""))>=1640) {
			tier = 4;
		}
		if(!characterImage.equals("")) {
			resMsg += charImgSearch(ordUserId,title,className,characterImage) + anotherMsgStr;
		}
		resMsg += "레벨"    +"　　　 　"+itemAvgLevel+enterStr;
		resMsg += "전투/원대"+"　　"+characterLevel+"　/　"+expeditionLevel+enterStr;
		//resMsg += "엘릭/초월"+"　　"+totElixir+"(" + elixirField+")"+" / "+totLimit+enterStr;
		resMsg += "공격/최생"+"　　"+atk+" / "+life+enterStr;
		//resMsg += "진/깨/도"+"　 　"+arkpoint1+" / "+arkpoint2+" / "+arkpoint3+enterStr;
		
		if(tier ==4) {
			resMsg += "아크패시브"+"　 "+"진:"+arkpoint1+"/"+"깨:"+arkpoint2+"/"+"도:"+arkpoint3+enterStr;
		}
		
		resMsg += newGemSearch(armoryGem,ordUserId, tier);
		if(isArkPassive.equals("true")) {
			resMsg += newEngraveSearch(armoryEngraving,ordUserId,true,true);	
		}else {
			//id,arkPassive,simpleMode
			resMsg += newEngraveSearch(armoryEngraving,ordUserId,false,true);
		}
		
		
		resMsg += acceMainMsg;
		resMsg += enterStr;
		
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", "")) >= 1700){
			resMsg += arkGridMsg + enterStr;
		}
		
		
		
		/*전투력 들어갈곳*/
		HashMap<String,Object> saveMap = new HashMap<>();
		
		/*
		try {
			sub.sumTotalPowerSearchByMainChar(rtnMap,saveMap);
			saveMap.put("charName", ordUserId);
			resMsg += "골드환산가치 : "+ saveMap.get("score");
			if(!saveMap.get("score").toString().equals("0")) {
				saveMap.put("lv", Double.parseDouble(itemAvgLevel.replaceAll(",", "")));
				saveMap.put("targetGb", "2");
				switch (className) {
					case "바드":
					case "도화가":
					case "홀리나이트":
						saveMap.put("classGb","S");
						break;
					default:
						saveMap.put("classGb","D");
						break;
				}
				
				
				botService.upsertBotPowerRankTx(saveMap);
			}
		}catch(Exception e) {
			System.out.println("전투력 저장만안됨");
		}
		*/
		
		if(!combatPower.equals("")) {
			resMsg += enterStr;
			resMsg += "⭐전투력 : "+ combatPower+enterStr;
		}
		
		if(!nakwonMsg.equals("")) {
			resMsg +=nakwonMsg+enterStr;
		}
		
		
		
		resMsg += "상세 더보기..▼"+allSeeStr;
		//resMsg += "방어구 / 초월 / 엘릭서"+enterStr;
		
		//resMsg += "§세트 : "+setField + enterStr;
		resMsg += resField1 + enterStr;
		
		resMsg +=abillityStoneMsg+ enterStr;
		resMsg +=accessoryMsg + enterStr;
		resMsg +=braceletMsg + enterStr;
		/*
		if(totLimit.equals("")) {
			resMsg += "§초월 : 없음" + enterStr;
		}else {
			resMsg += "§초월합 : " + totLimit + enterStr;
			resMsg += resField2 + enterStr;
		}
		*/
		/*
		if(totElixir==0) {
			resMsg += "§엘릭서 : 없음" + enterStr;
		}else {
			resMsg += "§엘릭서합 : " + totElixir + "(" + elixirField+")" + enterStr;;
			resMsg += resField3 + enterStr;
		}
		*/
		//아바타 정보 
		resMsg +=avatarsText;
		
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", "")) >= 1600) {
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
						resMsg +=" / 120" +" 　 "+pt.get("Description").toString();
						break;
					case "깨달음":
						resMsg +=" / 101" +" 　 "+ pt.get("Description").toString();
						break;
					case "도약":
						resMsg +=" / 70" +" 　 "+ pt.get("Description").toString();
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
		
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", "")) >= 1700){
			resMsg += enterStr + arkGridFullMsg + enterStr;
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
		
		
		
		String className = armoryProfile.get("CharacterClassName").toString();
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
			
			case "반지":case "귀걸이": case "목걸이":
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
				
					case "아이템 티어 4":
						resMsg += Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text();
						resMsg += " 품:"+(int)((HashMap<String, Object>) quality_element.get("value")).get("qualityValue");
						resMsg += " ("+((HashMap<String, Object>) ark_passive_point_element.get("value")).get("Element_001")+")";
						resMsg += enterStr;
						
						switch (className) {
							case "바드":
							case "도화가":
							case "홀리나이트":
								resMsg += LoaApiParser.findBraceletOptions("S",1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
								break;
							default:
								resMsg += LoaApiParser.findBraceletOptions("D",1,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString());
						}
						
						resMsg += enterStr;
						resMsg2Ok = true;
						break;
				}
				break;
			case "팔찌":
				resMsg += "팔찌 정보 ";
				HashMap<String, Object> bracelet =  (HashMap<String, Object>) bracelet_element.get("value");
				switch(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr2")).text()) {
					case "아이템 티어 3":
						resMsg += LoaApiParser.findBraceletOptions("",0,bracelet.get("Element_001").toString());
						break;
					case "아이템 티어 4":
						if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text().indexOf("고대")>=0 ) {
							//고대팔찌 우선적용 
							switch (className) {
								case "바드":
								case "도화가":
								case "홀리나이트":
									resMsg += LoaApiParser.findBraceletOptions("S",5,bracelet.get("Element_001").toString());
									break;
								default:
									resMsg += LoaApiParser.findBraceletOptions("D",4,bracelet.get("Element_001").toString());
							}
							
						}else {
							resMsg += LoaApiParser.findBraceletOptions("",0,bracelet.get("Element_001").toString());
						}
						
						break;
				}
				resMsg += enterStr;
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
				//String gradeLv = engrave.get("Grade").toString().substring(0,1)+engrave.get("Level");
				//engraveList.add(gradeLv+" "+tmpEng);
				engraveList.add(tmpEng+engrave.get("Level"));
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
		String[] gemList = {"멸화","홍염","겁화","작열","광휘"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		List<Integer> equipGemT4DualList = new ArrayList<>();
		
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
						}else if(equipGem.equals(gemList[4])) {
							equipGemT4DualList.add((int)gem.get("Level"));
						}
					}
				}
			}
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DualList,Collections.reverseOrder());
		if(tier ==3) {
			resMsg += "멸 / 홍　"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+" / "+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}else if(tier ==4) {
			resMsg += "겁/작/광"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg5 = equipGemT4DualList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+"/"+tmpMsg4+"/"+tmpMsg5,29,spaceStr);
			resMsg += enterStr;
		}
		return resMsg;
	}
	
	String miniGemSearch(String userId,int tier) throws Exception {
		
		
		String[] gemList = {"멸화","홍염","겁화","작열","광휘"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		List<Integer> equipGemT4DualList = new ArrayList<>();
		
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
						}else if(equipGem.equals(gemList[4])) {
							equipGemT4DualList.add((int)gem.get("Level"));
						}
					}
				}
			}
		}
		
		Collections.sort(equipGemT3DealList,Collections.reverseOrder());
		Collections.sort(equipGemT3CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DealList,Collections.reverseOrder());
		Collections.sort(equipGemT4CoolList,Collections.reverseOrder());
		Collections.sort(equipGemT4DualList,Collections.reverseOrder());
		if(tier ==3) {
			resMsg += "멸/홍"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+"/"+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}else if(tier ==4) {
			resMsg += "겁/작/광"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg5 = equipGemT4DualList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+"/"+tmpMsg4+"/"+tmpMsg5,29,spaceStr);
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

		String[] gemList = {"멸화","홍염","겁화","작열","광휘"};
		List<Integer> equipGemT3DealList = new ArrayList<>();
		List<Integer> equipGemT3CoolList = new ArrayList<>();
		List<Integer> equipGemT4DealList = new ArrayList<>();
		List<Integer> equipGemT4CoolList = new ArrayList<>();
		List<Integer> equipGemT4DualList = new ArrayList<>();
		
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
				if(gemLv < 7) {
					continue;
				}
				
				if( gemName.indexOf(equipGem)>=0 ) {
					if(equipGem.equals(gemList[0])) {//멸화
						if(gemLv < 10) {
							continue;
						}
						cnt++;
						equipGemT3DealList.add(gemLv);
					}else if(equipGem.equals(gemList[1])) {//홍염
						if(gemLv < 10) {
							continue;
						}
						cnt++;
						equipGemT3CoolList.add(gemLv);
					}else if(equipGem.equals(gemList[2])) {//겁화
						cnt++;
						equipGemT4DealList.add(gemLv);
					}else if(equipGem.equals(gemList[3])) {//작열
						cnt++;
						equipGemT4CoolList.add(gemLv);
					}else if(equipGem.equals(gemList[4])) {//광휘
						cnt++;
						equipGemT4DualList.add(gemLv);
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
		Collections.sort(equipGemT4DualList,Collections.reverseOrder());
		
		if(equipGemT3DealList.size()+equipGemT3CoolList.size() > 0) {
			resMsg += "멸/홍"+"";
			String tmpMsg1 = equipGemT3DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg2 = equipGemT3CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg1+"/"+tmpMsg2,29,spaceStr); 
			resMsg += enterStr;
		}
		if(equipGemT4DealList.size()+equipGemT4CoolList.size()+equipGemT4DualList.size() > 0) {
			resMsg += "겁/작/광"+"";
			String tmpMsg3 = equipGemT4DealList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg4 = equipGemT4CoolList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			String tmpMsg5 = equipGemT4DualList.toString().replaceAll("\\[","").replaceAll("\\]","").replaceAll(" ","");
			resMsg += StringUtils.center(tmpMsg3+"/"+tmpMsg4+"/"+tmpMsg5,29,spaceStr);
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
	
	List<String> totalAccessorySearch(Map<String,Object> rtnMap,String userId,String className,int grade) throws Exception {
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
							
							//고대 유물 둘다 되도록수정 
							//고대
							if(grade == 1) {
								if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text().indexOf("고대")>=0 ) {
									switch (className) {
										case "바드":
										case "도화가":
										case "홀리나이트":
											g1.add(LoaApiParser.findBraceletOptions("S",3,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
											break;
										default:
											g1.add(LoaApiParser.findBraceletOptions("D",2,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
											break;
									}
								}
							}else if(grade ==2){//유물 (grade 2)
								if(Jsoup.parse((String) ((HashMap<String, Object>) quality_element.get("value")).get("leftStr0")).text().indexOf("유물")>=0 ) {
									switch (className) {
										case "바드":
										case "도화가":
										case "홀리나이트":
											g1.add(LoaApiParser.findBraceletOptions("S",3,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
											break;
										default:
											g1.add(LoaApiParser.findBraceletOptions("D",2,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
											break;
									}
								}
							}else {
								//둘다 grade 3
								switch (className) {
								case "바드":
								case "도화가":
								case "홀리나이트":
									g1.add(LoaApiParser.findBraceletOptions("S",3,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
									break;
								default:
									g1.add(LoaApiParser.findBraceletOptions("D",2,((HashMap<String, Object>) grinding_element.get("value")).get("Element_001").toString()));
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
			return null;
		}
		
		
		return g1;
	}
	
	List<Map<String, Object>> totalEngraveSearchByMainChar(Map<String,Object> rtnMap,String mainCharName) throws Exception {

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
	
	List<Map<String, Object>> totalEngraveSearch(List<Map<String,Object>> rtnList,String mainCharName) throws Exception {
		List<Map<String, Object>> engraves;
		List<Map<String, Object>> refreshDataList = new ArrayList<>();;
		
		try {
			engraves = rtnList;
		
			for (Map<String, Object> engrave : engraves) {
				HashMap<String,Object> refreshDataMap = LoaApiParser.engraveSelector(engrave.get("Name").toString(), engrave.get("Grade").toString(), engrave.get("Level").toString());
				if(!refreshDataList.contains(refreshDataMap)) {
					refreshDataList.add(refreshDataMap);
				}
			}
			
		}catch(Exception e){
			System.out.println(e.getMessage());
			return null;
		}
		
		
		return refreshDataList;
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
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		
		double eachPercent =0.0;
		double totEachPercent =0.0;
		double percent = 100.0/rtnMap.size();
		
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
	
	String subCharacterInfoSearch1(String userId,int limitLv) throws Exception {
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
		
		
		String resMsg="";
		if(limitLv > 50) {
			resMsg=ordUserId+" 부캐 정보 "+limitLv+"↑" + enterStr;
		}else {
			resMsg=ordUserId+" 부캐 정보" + enterStr;
		}
		
		resMsg += "§부캐 보석&엘초: /부캐2 로 변경됨!"+enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= limitLv)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String mainServer = sortedList.get(0).get("ServerName").toString();
		
		resMsg += mainServer;
		resMsg += enterStr;
		
		String resMsg2="";
		HashMap<String,Object> resMap =new HashMap<>();
		
		int charCnt = 0;
		for(HashMap<String,Object> charList : sortedList) {
			if(mainServer.equals(charList.get("ServerName").toString())) {
				charCnt++;
				resMsg += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "]";
				resMsg += "("+charList.get("ItemAvgLevel").toString().replaceAll(",", "")+")";
				resMsg += charList.get("CharacterName").toString();
				resMsg += enterStr;
				
			}else {
				charCnt++;
				resMsg2 += charList.get("ServerName").toString() + enterStr;
				resMsg2 += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "]";
				resMsg2 += "("+charList.get("ItemAvgLevel").toString().replaceAll(",", "")+")";
				resMsg2 += charList.get("CharacterName").toString();
				resMsg2 += enterStr;
				
			}
			
		}
		if(charCnt>6) {
			resMsg = resMsg + allSeeStr + resMsg2;
		}else {
			resMsg = resMsg + resMsg2;
		}
		
		
		return resMsg;
	}
	
	String subCharacterGoldSum(String userId) throws Exception {
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
		
		
		
		
		String resMsg=ordUserId;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1640)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String mainServer = sortedList.get(0).get("ServerName").toString();
		
		//resMsg += mainServer;
		//resMsg += enterStr;
		
		
		HashMap<String,Object> resMap =new HashMap<>();
		
		int charCnt = 0;
		int partial_gold = 0;
		int total_gold   = 0;
		String resMsg1 ="";
		
		for(HashMap<String,Object> charList : sortedList) {
			if(mainServer.equals(charList.get("ServerName").toString())) {
				charCnt++;
				resMsg1 += "[" + LoaApiUtils.shortClassName(charList.get("CharacterClassName").toString()) + "] ";
				resMsg1 += charList.get("CharacterName").toString()+" - ";
				Map<String, Object> armoryProfile = new HashMap<>();
				
				
				try {
					resMap = sub.sumTotalPowerSearch2(charList.get("CharacterName").toString());
					armoryProfile = (Map<String, Object>) resMap.get("ArmoryProfile");
				}catch(Exception e){
					System.out.println(userId+" ArmoryProfile");
				}
				
				double lv = Double.parseDouble(charList.get("ItemAvgLevel").toString().replaceAll(",", ""));
				partial_gold = 0;
				if(lv >= 1750 ) {
					partial_gold = 156000;
				}else if(lv >= 1740 ) {
					partial_gold = 148000;
				}else if(lv >= 1730) {
					partial_gold = 138000;
				}else if(lv >= 1720) {
					partial_gold = 117000;
				}else if(lv >= 1710) {
					partial_gold = 108000;
				}else if(lv >= 1700) {
					partial_gold = 83000;
				}else if(lv >= 1690) {
					partial_gold = 62000;
				}else if(lv >= 1680) {
					partial_gold = 55500;
				}else if(lv >= 1670) {
					partial_gold = 16500+11500+7200;
				}else if(lv >= 1660) {
					partial_gold = 25900;
				}else if(lv >= 1640) {
					partial_gold = 7200+7200;
				}
				total_gold +=partial_gold;
				
				if(Double.parseDouble(charList.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1600) {
					String combatPower ="";
					if(armoryProfile.get("CombatPower") != null) {
						
						combatPower = armoryProfile.get("CombatPower").toString();
					}
					resMsg1 += partial_gold + " G " + enterStr;
					resMsg1 += charList.get("ItemAvgLevel").toString().replaceAll(",", "")+"Lv ";
					resMsg1 += ",전투력("+combatPower+")";
					resMsg1 += enterStr ;
				}
				
				if(charCnt ==6) {
					break;
				}
			}
			
		}
		resMsg += " 총 골드합계 "+total_gold+" G"+enterStr+enterStr;
		resMsg += resMsg1;
		
		return resMsg;
	}
	String subCharacterInfoSearch2(String userId) throws Exception {
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
		
		
		
		
		String resMsg=ordUserId+" 1640이상 부캐 정보" + enterStr;
		resMsg += "4T 7보석↑,3T 10보석↑ 표기"+enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1640)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
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
				resMsg += charList.get("CharacterName").toString();
				resMsg += enterStr;
				System.out.println(ordUserId+" : "+charCnt + " / "+ sortedList.size());
				resMap = sub.sumTotalPowerSearch2(charList.get("CharacterName").toString());
				
				Map<String, Object> armoryGem = new HashMap<>();
				Map<String, Object> armoryProfile = new HashMap<>();
				
				try {
					armoryGem = (Map<String, Object>) resMap.get("ArmoryGem");
				}catch(Exception e){
				}
				
				try {
					armoryProfile = (Map<String, Object>) resMap.get("ArmoryProfile");
				}catch(Exception e){
					System.out.println(userId+" ArmoryProfile");
				}
				
				
				
				if(Double.parseDouble(charList.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1640) {
					//resMsg += miniLimitSearch(resMap,charList.get("CharacterName").toString());
					String combatPower ="";
					if(armoryProfile.get("CombatPower") != null) {
						
						combatPower = armoryProfile.get("CombatPower").toString();
					}
					resMsg += ""+charList.get("ItemAvgLevel").toString().replaceAll(",", "")+"Lv ";
					resMsg +="전투력: "+combatPower;
					resMsg += enterStr;
				}
				
				resMsg += miniGemCntSearch(armoryGem,charList.get("CharacterName").toString());//얘는 엔터포함됨
				if(charCnt ==4) {
					resMsg += enterStr + "4캐릭 이상 더보기..▼ ";
					resMsg += allSeeStr ;
				}
			}
			
		}
		
		return resMsg;
	}
	
	
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
			
			//resMsg += searchAuctionParse("고대3연마");
			//resMsg += enterStr;
	
	/*
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
		*/
		/*
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
		*/
	
	String newSearchAcce() throws Exception {
	    StringBuilder resMsg = new StringBuilder("4티어 악세 최저가").append(enterStr);
	    resMsg.append("(연마단계)").append(enterStr);
	    
	    resMsg.append("§목걸이").append(enterStr)
	    	  .append("(3)").append(getAccessoryDetails("고대3", "낙인상", "목걸이")).append(enterStr)
	    	  .append("(1)").append(getAccessoryDetails("고대1", "낙인상", "목걸이")).append(enterStr)
	    	  .append(enterStr);
	    
	    resMsg.append("§귀걸이").append(enterStr)	  
	    	  .append("(3)").append(getAccessoryDetails("고대3", "공퍼상", "귀걸이")).append(enterStr)
	    	  .append(enterStr);
	    
	    resMsg.append("§반지").append(enterStr)	  
		      .append("(3)").append(getAccessoryDetails("고대3", "치적상", "반지")).append(enterStr)
		      .append("(3)").append(getAccessoryDetails("고대3", "치피상", "반지")).append(enterStr)
		      .append("(3)").append(getAccessoryDetails("고대3", "치적중치피중", "반지")).append(enterStr)
		      .append("(3)").append(getAccessoryDetails("고대3", "치피상공플상", "반지")).append(enterStr)
		      //.append("(3)").append(getAccessoryDetails("고대3", "아공상아피상최생중", "반지")).append(enterStr)
		      .append("(1)").append(getAccessoryDetails("고대1", "치적상", "반지")).append(enterStr)
		      .append("(1)").append(getAccessoryDetails("고대1", "치피상", "반지")).append(enterStr)
		      .append(enterStr);



	    return resMsg.toString();
	}

	String getAccessoryDetails(String itemGrade, String itemType, String category) throws Exception {
	    JSONObject json = new JSONObject();
	    JSONArray options = new JSONArray();
	    json.put("Sort", "BUY_PRICE")
	        .put("SortCondition", "ASC")
	        .put("ItemTier", 4)
	        .put("ItemGrade", "고대");

	    // 카테고리 코드 설정
	    String categoryCode;
	    if (category.equals("목걸이")) categoryCode = "200010";
	    else if (category.equals("귀걸이")) categoryCode = "200020";
	    else if (category.equals("반지")) categoryCode = "200030";
	    else categoryCode = "";

	    json.put("CategoryCode", categoryCode);

	    //7: 1~3 하중상 => 10하 11중 12상 

	    // 옵션 설정
	    switch(itemGrade) {
	    	case "고대3":
	    		if (itemType.equals("낙인상")) {
		            options.put(createOption(8, 1, 13))
		            	   .put(createOption(7, 44, 12));
		        } else if (itemType.equals("공퍼상")) {
		            options.put(createOption(8, 1, 12))
		            	   .put(createOption(7, 45, 12));
		        } else if (itemType.equals("치적상")) {
		            options.put(createOption(8, 1, 12))
		                   .put(createOption(7, 49, 12));
		        } else if (itemType.equals("치피상")) {
		            options.put(createOption(8, 1, 12))
	                   	   .put(createOption(7, 50, 12));
		        } else if (itemType.equals("치적중치피중")) {
		            options.put(createOption(8, 1, 12))
                	   	   .put(createOption(7, 49, 11))
                	   	   .put(createOption(7, 50, 11));
		        } else if (itemType.equals("아공상아피상최생중")) {
		            options.put(createOption(8, 1, 12))
		                   .put(createOption(7, 51, 12))
		                   .put(createOption(7, 52, 12))
		                   .put(createOption(7, 55, 12));
		        } else if (itemType.equals("치피상공플상")) {
		            options.put(createOption(8, 1, 12))
	                   .put(createOption(7, 50, 12))
	                   .put(createOption(7, 53, 12));
		        }
	    		break;
	    	case "고대1":
	    		 if (itemType.equals("낙인상")) {
	 	            options.put(createOption(8, 1, 6))
	 	                   .put(createOption(7, 44, 12));
	 	        } else if (itemType.equals("치적상")) {
	 	            options.put(createOption(8, 1, 5))
	 	                   .put(createOption(7, 49, 12));
	 	        } else if (itemType.equals("치피상")) {
	 	            options.put(createOption(8, 1, 5))
	 	                   .put(createOption(7, 50, 12));
	 	        }
	    		break;
	    }

	    return itemType + " "+ auctionSearchDt(json.put("EtcOptions", options), false, false);
	}


	// 옵션 생성 메서드
	JSONObject createOption(int firstOption, int secondOption, int value) throws JSONException {
	    return new JSONObject().put("FirstOption", firstOption)
	                           .put("SecondOption", secondOption)
	                           .put("MinValue", value)
	                           .put("MaxValue", value);
	}

	
	
	/*
	String marketTier4accessorySearch() throws Exception {
		String resMsg = "4티어 악세 최저가"+enterStr;
		JSONObject json = new JSONObject();
		JSONArray options = new JSONArray();
		JSONObject json2 = new JSONObject();
		JSONObject json3 = new JSONObject();
		
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
	*/
	/*
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
		
		case "고대3낙인력":
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
		}
		
		
		return resMsg;
	}
	*/
	
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
		resMsg += "티어별조회:/경매장4 /경매장3"+enterStr;
		resMsg += "각인서조회:/경매장유물"+enterStr;
		resMsg += "시세 조회:/시세 각인명"+enterStr;
		//resMsg += "연마악세 조회: 경매장악세"+enterStr;
		
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
	
	String marketSearch(String item) throws Exception {
		JSONObject json ;
		String resMsg= "[아이템명]-[실시간최저가]"+enterStr;
		
		json = new JSONObject();
		

		json.put("CategoryCode", "230000");
		json.put("Sort", "CURRENT_MIN_PRICE");
		json.put("SortCondition", "ASC");
		json.put("ItemName", "질서");
		json.put("PageNo", "1");
		
		
		resMsg +="[젬이름]-[ 고급 / 희귀 / 영웅 ]"+enterStr;
		
		resMsg += marketDtSearch(json,5,true,true);
		
		resMsg +=enterStr;
		
		json.put("ItemName", "혼돈");
		resMsg += marketDtSearch(json,5,true,true);
		
		resMsg = LoaApiUtils.filterTextForMarket(resMsg);
	
		
		
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
				case 5:
					// 옵션 점수 매핑 테이블
					Map<String, String> optionPointMap = new HashMap<>();
					optionPointMap.put("불변", "10p");
					optionPointMap.put("견고", "9p");
					optionPointMap.put("안정", "8p");
					optionPointMap.put("붕괴", "10p");
					optionPointMap.put("왜곡", "9p");
					optionPointMap.put("침식", "8p");

					Map<String, Map<String, List<String>>> groupedMap = new LinkedHashMap<>();

					for (HashMap<String, Object> item : itemMap) {
					    String name = item.get("Name").toString(); // ex) "질서의 젬 : 안정"
					    String[] parts = name.split(" : ");
					    if (parts.length < 2) continue;

					    String mainName = parts[0].replace("의 젬", "").trim(); // ex) "질서"
					    String option = parts[1].trim();                      // ex) "안정"

					    price = item.get("CurrentMinPrice").toString();

					    groupedMap
					        .computeIfAbsent(mainName, k -> new LinkedHashMap<>())
					        .computeIfAbsent(option, k -> new ArrayList<>())
					        .add(price);
					}

					// 최종 문자열 조립
					StringBuilder sb = new StringBuilder();
					for (Map.Entry<String, Map<String, List<String>>> mainEntry : groupedMap.entrySet()) {
					    sb.append(mainEntry.getKey()).append(enterStr); // ex) "질서"

					    for (Map.Entry<String, List<String>> optionEntry : mainEntry.getValue().entrySet()) {
					        String option = optionEntry.getKey(); // ex) "안정"
					        String point = optionPointMap.getOrDefault(option, "0p"); // 점수
					        List<String> prices = optionEntry.getValue();

					        sb.append(option)
					          .append("(").append(point).append(") - ").append(enterStr)
					          .append("　[ ")
					          .append(String.join("/", prices))
					          .append(" ] G")
					          .append(enterStr);
					    }
					}

					str+= sb;
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
			return "/ㄱㅂ 10 100 형태로 입력해주세요"; 
		}
		
		int totJ=j; //최초 전체수량
		
		if(i==0 || i>j || i>2000 || j> 2000) {
			return "/ㄱㅂ 10 100 형태로 입력해주세요";
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
            content = cutByBytesAndInsertMarker(content, 150, allSeeStr);
            
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println(e.getMessage());
			content = "오류입니다.";
		}
		
		// botService.save db에 저장 로직 (reqMsg,content)

		return content;
	}
	public String geminiSearch(String reqMsg,String userName) throws Exception {
		String content ="";
		
		int cnt = 0;
		//cnt = botService.select오늘얼마썻는지체크로직(userName);
		
		if(cnt>3) {
			content ="오늘 3회 모두 사용했습니다.";
		}
		
		try {
			content = GeminiUtils.callGeminiApi(reqMsg);
			content = content.replaceAll("\n", enterStr);
			content = cutByBytesAndInsertMarker(content, 150, allSeeStr);
			
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println(e.getMessage());
			content = "오류입니다.";
		}
		
		// botService.save db에 저장 로직 (reqMsg,content)
		
		return content;
	}
	
	public static String cutByBytesAndInsertMarker(String text, int maxBytes, String marker) throws Exception {
	    byte[] bytes = text.getBytes("UTF-8");
	    if (bytes.length <= maxBytes) {
	        return text;
	    }

	    int bytesCount = 0;
	    int cutIndex = 0;

	    for (int i = 0; i < text.length(); i++) {
	        char c = text.charAt(i);
	        int charBytes = String.valueOf(c).getBytes("UTF-8").length;
	        
	        if (bytesCount + charBytes > maxBytes) {
	            break;
	        }
	        bytesCount += charBytes;
	        cutIndex = i + 1;
	    }

	    // 앞쪽은 잘라내고, marker 추가하고, 뒷내용도 이어붙임
	    String front = text.substring(0, cutIndex);
	    String back = text.substring(cutIndex);

	    return front + marker + back;
	}
	
	public String tossAccount() {
		String ment = "";
		ment += enterStr+"람쥐봇 건의방 및 공통방 참여url(오픈채팅)";
		ment += enterStr+"https://open.kakao.com/o/gVPFm5Lh";
		return ment; 
	}
	public String tossAccount2() {
		String ment = "";
		ment += enterStr;
		ment += enterStr+"[람쥐봇 운영에 도움주신분들]";
		ment += enterStr+"챙석봉 리퍼고냥이 블루미안 친칠라솜꼬리토끼 꼰강선 동탄미시김토끼 루아요 호데레 키데레 NutBox 키리레이나 rogo극특바드";
		ment += enterStr;
		ment += enterStr+"[개발자 후원하기]";
		ment += enterStr+"후원금은 서버비 및 개발자 콜라비용에 보탬이 됩니다.";
		ment += enterStr+"토스뱅크 1000-4571-3008 ㅈㅌㅎ";
		ment += enterStr;
		/*
		ment += enterStr+"[개발자 오픈채팅]";
		ment += enterStr+"아이디어나 개선점 피드백 감사합니다.";
		ment += enterStr+"https://open.kakao.com/o/sC6s7lkb";
		ment += enterStr;
		ment += enterStr+"[람쥐봇 운영에 도움을 주신분들]";
		ment += enterStr+"챙석봉, 리퍼고냥이, 블루미안, 친칠라솜꼬리토끼";
		ment += enterStr;
		*/
		return ment; 
	}
	
	
	public String sp_icon(int supportersCase) {
		String ment ="";
		switch (supportersCase) {
			case 1:
				ment+="⭐";
				break;
			case 2:
				ment+="✪";
				break;
			case 3:
				ment+="♛";
				break;
			case 4:
				ment+="☁";
				break;
			case 5:
				ment+="☀";
				break;
			case 6:
				ment+="♦";
				break;
			case 7:
				ment+="☂";
				break;
			case 8:
				ment+="☃";
				break;
			case 9:
				ment+="❄";
				break;
			case 10:
				ment+="☠";
				break;
		}
		return ment;
		
		
	}
	
	public String supportersIcon(String userId) {
		int supportersYn = botService.selectSupporters(userId);
		return sp_icon(supportersYn);
		
	}
	public int supporters(String userId) {
		int suppertersYn = botService.selectSupporters(userId);
		
		return suppertersYn; 
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
				
				title = title.replaceAll("<[^>]*>", "").trim();
				
				hs.put("title", title);
				hs.put("class_name", className);
				
				int icon = supporters(ordUserId);
				if(icon > 0) {
					hs.put("star_yn", icon);	
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
