package my.prac.api.loa.controller;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;
import my.prac.core.util.LoaApiParser;
import my.prac.core.util.LoaApiUtils;


@Controller
public class LoaChatTestController {
	static Logger logger = LoggerFactory.getLogger(LoaChatTestController.class);

	
	
	@Autowired
	LoaMarketController market;
	@Autowired
	LoaPlayController play;
	@Autowired
	LoaAiBotController ai;
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;
	@Resource(name = "core.prjbot.BotNewService")
	BotNewService botNewService;
	
	// final String lostArkAPIurl =
	// "https://developer-lostark.game.onstove.com/armories/characters/일어난다람쥐/equipment";
	final static String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	final static String enterStr= "♬";
	final static String spaceStr= "`";
	final static String tabStr= "◐";
	final static String allSeeStr = "===";
	final static String anotherMsgStr = "®";
	final static String listSeparatorStr = "㈜";
	
	final static String rank_1st = "👑"; 
	final static String rank_2nd = "🥈"; 
	final static String rank_3rd = "🥉"; 
	final static String rank_etc = "　";
	
	final static String[] unable_save_list = {enterStr,spaceStr,tabStr,allSeeStr,anotherMsgStr,listSeparatorStr,"\\"};
	
	
	public static void main(String[] args) {
		ParamMap pm = new ParamMap();
		try {
			File f = new File("");
		    String path = f.getAbsolutePath(); // 현재 클래스의 절대 경로를 가져온다.
		    System.out.println(path); //--> 절대 경로가 출력됨
			File file = new File(path + "/src/main/java/my/prac/api/loa/controller/response_critical.json");
			String a =buildCritMessageFromFile(file);
			System.out.println(a);
		//t2(pm);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	 private final static ObjectMapper objectMapper = new ObjectMapper();

	public static String buildCritMessageFromFile(File file) throws IOException {
		JsonNode root = objectMapper.readTree(file);

		// characterInfo
		JsonNode characterInfo = root.path("characterInfo");
		String nickname = characterInfo.path("nickname").asText("");
		String className = characterInfo.path("className").asText("");
		String itemLevel = characterInfo.path("itemLevel").asText("");
		String arkPassiveTitle = characterInfo.path("arkPassiveTitle").asText("");

		// critRateInfo
		JsonNode critRateInfo = root.path("critRateInfo");
		double total = critRateInfo.path("total").asDouble(0.0);

		StringBuilder sb = new StringBuilder();

		// 1) 캐릭터 기본 정보
		sb.append("[").append(nickname).append("] ").append(className);

		if (!itemLevel.isEmpty()) {
			sb.append(" (").append(itemLevel).append(")");
		}
		if (!arkPassiveTitle.isEmpty()) {
			sb.append(" - 아크패시브: ").append(arkPassiveTitle);
		}
		sb.append("\n");

		// 2) 총 치적
		sb.append("총 치명타 적중률: ").append(String.format("%.2f", total)).append("%\n\n");

		// 3) 상세 치적 구성
		sb.append("[상세]\n");

		JsonNode details = critRateInfo.path("details");
		if (details.isArray()) {
			for (JsonNode d : details) {
				String source = d.path("source").asText("");
				double value = d.path("value").asDouble(0.0);
				String note = d.path("note").asText("");

				sb.append("- ");

				// source
				if (!source.isEmpty()) {
					sb.append(source);
				}

				// note (있으면 괄호나 공백으로 붙여주기)
				if (!note.isEmpty()) {
					sb.append(" ").append(note);
				}

				sb.append(" : ").append(String.format("%.2f", value)).append("%\n");
			}
		}

		// 4) 조건부 치적 (conditional) 처리 (지금은 빈 배열이지만 대비용)
		JsonNode conditional = critRateInfo.path("conditional");
		if (conditional.isArray() && conditional.size() > 0) {
			sb.append("\n[조건부 치적]\n");
			for (JsonNode c : conditional) {
				String source = c.path("source").asText("");
				double value = c.path("value").asDouble(0.0);
				String note = c.path("note").asText("");

				sb.append("- ");
				if (!source.isEmpty()) {
					sb.append(source);
				}
				if (!note.isEmpty()) {
					sb.append(" ").append(note);
				}
				sb.append(" : ").append(String.format("%.2f", value)).append("%\n");
			}
		}

		return sb.toString();
	}
	
	static void t2(ParamMap param) throws Exception {
		
		LoaChatController main = new LoaChatController();
		LoaChatSubController sub = new LoaChatSubController();
		File f = new File("");
	    String path = f.getAbsolutePath(); // 현재 클래스의 절대 경로를 가져온다.
	    System.out.println(path); //--> 절대 경로가 출력됨
	    File file = new File(path + "/src/main/java/my/prac/api/loa/controller/response_1758777822563.json");
		InputStream is = new FileInputStream(file);
		String returnData = StreamUtils.copyToString(is, Charset.forName("UTF-8"));
		HashMap<String,Object> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<Map<String,Object>>() {});
		
		List<Map<String, Object>> armoryEquipment;
		Map<String, Object> armoryProfile;
		Map<String, Object> armoryEngraving;
		Map<String, Object> armoryGem;
		Map<String, Object> arkGrid;
		List<Map<String, Object>> armoryAvatars;
		
		String userId ="";
		String ordUserId ="";
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
		String totLimit ="";
		int totElixir =0;
		
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
				
				nakwonMsg += "";
				HashMap<String, Object> nakwonTooltip = (HashMap<String, Object>) nakwon.get("value");
				String nakwonElelment001 = Jsoup.parse((String)nakwonTooltip.get("Element_001")).text();
				
				Pattern p = Pattern.compile("낙원력\\s*:\\s*(\\d+)");
				Matcher m = p.matcher(nakwonElelment001);

				if (m.find()) {
				    String value = m.group(1);  // 숫자만 추출됨 (83597)
				    nakwonMsg += "낙원력 : " + value;
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
				arkGridFullMsg += slot.get("Grade") + " " + slot.get("Name")+ ", 활성포인트: " + activePoint + enterStr + optionMsg.toString()+enterStr;
				

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
			acceossory = main.totalAccessorySearch(rtnMap,userId,className,3);
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
		
		if(totLimit.equals("")) {
			totLimit="0";
		}
		
		
		int tier = 3;
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", ""))>=1640) {
			tier = 4;
		}
		if(!characterImage.equals("")) {
			//resMsg += main.charImgSearch(ordUserId,title,className,characterImage) + anotherMsgStr;
		}
		resMsg += "레벨"    +"　　　 　"+itemAvgLevel+enterStr;
		resMsg += "전투/원대"+"　　"+characterLevel+"　/　"+expeditionLevel+enterStr;
		resMsg += "엘릭/초월"+"　　"+totElixir+"(" + elixirField+")"+" / "+totLimit+enterStr;
		resMsg += "공격/최생"+"　　"+atk+" / "+life+enterStr;
		//resMsg += "진/깨/도"+"　 　"+arkpoint1+" / "+arkpoint2+" / "+arkpoint3+enterStr;
		
		if(tier ==4) {
			resMsg += "아크패시브"+"　 "+"진:"+arkpoint1+"/"+"깨:"+arkpoint2+"/"+"도:"+arkpoint3+enterStr;
		}
		
		resMsg += main.newGemSearch(armoryGem,ordUserId, tier);
		if(isArkPassive.equals("true")) {
			resMsg += main.newEngraveSearch(armoryEngraving,ordUserId,true,true);	
		}else {
			//id,arkPassive,simpleMode
			resMsg += main.newEngraveSearch(armoryEngraving,ordUserId,false,true);
		}
		
		
		resMsg += acceMainMsg;
		resMsg += enterStr;
		
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", "")) >= 1700){
			resMsg += arkGridMsg + enterStr;
		}
		
		
		
		/*전투력 들어갈곳*/
		HashMap<String,Object> saveMap = new HashMap<>();
		
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
				
				
				//botService.upsertBotPowerRankTx(saveMap);
			}
		}catch(Exception e) {
			System.out.println("전투력 저장만안됨");
		}
		
		if(!combatPower.equals("")) {
			resMsg += enterStr;
			resMsg += "⭐인게임전투력 : "+ combatPower+enterStr;
		}
		
		
		
		
		
		resMsg += "상세 더보기..▼"+allSeeStr;
		//resMsg += "방어구 / 초월 / 엘릭서"+enterStr;
		
		//resMsg += "§세트 : "+setField + enterStr;
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
		
		if(Double.parseDouble(itemAvgLevel.replaceAll(",", "")) >= 1600) {
			if(isArkPassive.equals("true")) {
				resMsg +="§아크패시브 : 사용"+enterStr;
			}else {
				resMsg +="§아크패시브 : 미사용"+enterStr;
			}
			
			if(isArkPassive.equals("true")) {
				resMsg += main.newEngraveSearch(armoryEngraving,ordUserId,true,false);
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
		
		
		//System.out.println(rtnMap);
	}
	
	
}
