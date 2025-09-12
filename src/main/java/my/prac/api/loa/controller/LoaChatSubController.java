package my.prac.api.loa.controller;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.LoaApiParser;
import my.prac.core.util.LoaApiUtils;

@Controller
public class LoaChatSubController {
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
	final String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	void sumTotalPowerSearchByMainChar(HashMap<String, Object> rtnMap,HashMap<String,Object> saveMap) throws Exception {
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
		List<String> accessoryList1 = new ArrayList<>();
		List<String> accessoryList2 = new ArrayList<>();
		
		String charName = "";
		String charClassName ="";
		String guildName="";
		
		HashMap<String,Object> resMap =new HashMap<>();
		
		
		
		int charCnt =0;
		
		resMap = rtnMap;
		Map<String, Object> armoryProfile = new HashMap<>();
		Map<String, Object> armoryEngraving = new HashMap<>();
		Map<String, Object> armoryGem = new HashMap<>();
		List<Map<String,Object>> armoryEngraves = new ArrayList<>();
		
		try {
			armoryProfile = (Map<String, Object>) resMap.get("ArmoryProfile");
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
		
		charClassName = armoryProfile.get("CharacterClassName").toString();
		try {
			guildName = armoryProfile.get("GuildName").toString();
		}catch(Exception e){
			
		}
		weaponList.add(totalEquipmentSearch(resMap,charName));
		
		List<Integer> charGem = totalGemCntSearch(armoryGem,charName);
		if(charGem !=null) {
			gemList.addAll(charGem);
		}
		List<Map<String,Object>> charEngrave =(List<Map<String, Object>>) armoryEngraving.get("ArkPassiveEffects");
		if(charEngrave !=null) {
			engraveList.addAll(charEngrave);
		}
		
		//고대
		List<String> acceossory1 = totalAccessorySearch(resMap,charName,charClassName,1);
		if(acceossory1 !=null) {
			accessoryList1.addAll(acceossory1);
		}
		//유물
		List<String> acceossory2 = totalAccessorySearch(resMap,charName,charClassName,2);
		if(acceossory2 !=null) {
			accessoryList2.addAll(acceossory2);
		}
		
		String maxCharLv = armoryProfile.get("ItemAvgLevel").toString().replaceAll(",", "");
		/*
		resMsg += maxCharLv+"Lv" + enterStr;
		resMsg += msgOfWeapon(weaponList);
		resMsg += msgOfGem(gemList);
		resMsg += msgOfAccessory(accessoryList1, 1);
		resMsg += msgOfAccessory(accessoryList2, 2);
		*/
		gradeCnt_lv += calcOfLv(maxCharLv);
		gradeCnt_weapon += calcOfWeapon(weaponList);
		gradeCnt_gem += calcOfGem(gemList);
		gradeCnt_accessory += calcOfAccessory(accessoryList1, 1);//고대
		gradeCnt_accessory += calcOfAccessory(accessoryList2, 2);//유물
		gradeCnt_engrave += calcOfEngrave(charEngrave);
		
		gradeCnt=gradeCnt_lv
				+gradeCnt_subChar
				+gradeCnt_weapon
				+gradeCnt_gem
				+gradeCnt_engrave
				+gradeCnt_accessory; 
		
		String resMsg="";
		resMsg +="환산 비용: "+gradeCnt +"만 골드";
		
		if(gradeCnt>0) {
			grade="모코코";
		}
		if(gradeCnt>400) {
			grade="중급자";
		}
		if(gradeCnt>900) {
			grade="고인물";
		}
		if(gradeCnt>1800) {
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
		resMsg += "각인상세"+enterStr + msgOfEngrave(charEngrave)+enterStr;
		
		resMsg += "가격표:"+enterStr;
		resMsg += "http://rgb-tns.dev-apc.com/in/totalGold4";
		/*
		resMsg += miniGemCntSearch(charList.get("CharacterName").toString());//얘는 엔터포함됨
		if(gradeCnt_gem ==0) {
			gradeCnt=0;
		}
		*/
		saveMap.put("score", gradeCnt);
		saveMap.put("charName", charName);
		saveMap.put("guildName", guildName);
		
		saveMap.put("resMsg", resMsg);
		
	}
	
	String sumTotalPowerSearch(String userId,HashMap<String,Object> saveMap) throws Exception {

		String ordUserId=userId;
		userId = URLEncoder.encode(userId, "UTF-8");
		// +는 %2B로 치환한다
		String paramUrl = lostArkAPIurl + "/characters/" + userId + "/siblings";
		String returnData = LoaApiUtils.connect_process(paramUrl);
		
		String resMsg=ordUserId+enterStr+"원정대 전투력 정보 v1.3" + enterStr;
		
		List<HashMap<String, Object>> rtnMap = new ObjectMapper().readValue(returnData,new TypeReference<List<Map<String, Object>>>() {});
		if(rtnMap.isEmpty()) return "";
		List<HashMap<String, Object>> sortedList = rtnMap.stream()
				.filter(x->  Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", "")) >= 1540)
				.sorted(Comparator.comparingDouble(x-> Double.parseDouble(x.get("ItemAvgLevel").toString().replaceAll(",", ""))))
				.collect(toReversedList());
		
		String maxCharLv =sortedList.get(0).get("ItemAvgLevel").toString().replaceAll(",", "");
		int gradeCnt =0;
		int gradeCnt_lv=0;
		int gradeCnt_weapon=0;
		int gradeCnt_gem=0;
		int gradeCnt_engrave=0;
		int gradeCnt_accessory=0;
		
		String grade ="모코코";
		
		List<Integer> lvList = new ArrayList<>();
		List<Integer> weaponList = new ArrayList<>();
		ArrayList<Integer> gemList = new ArrayList<>();
		List<Map<String,Object>> engraveList = new ArrayList<>();
		List<String> accessoryList1 = new ArrayList<>();
		List<String> accessoryList2 = new ArrayList<>();
		
		String mainCharName = sortedList.get(0).get("CharacterName").toString();
		String charName = "";
		String charClassName ="";
		Double charLv=0.0;
		String guildName ="";
		HashMap<String,Object> resMap =new HashMap<>();
		
		List<HashMap<String,Object>> refreshEngraveList = new ArrayList<>();
		
		int charCnt =0;
		for(HashMap<String,Object> charList : sortedList) {
			
			if(charCnt>8) {
				break;
			}
			
			charName = charList.get("CharacterName").toString();
			charLv =Double.parseDouble(charList.get("ItemAvgLevel").toString().replaceAll(",", "")); 
			charClassName = charList.get("CharacterClassName").toString();
			
			if(charLv < 1640) {
				continue;
			}
			
			if(charLv >= 1680){
				lvList.add((int) Math.floor(charLv));
			}
			
			
			
			resMap = sumTotalPowerSearch2(charName);
			Map<String, Object> armoryEngraving = new HashMap<>();
			Map<String, Object> armoryGem = new HashMap<>();
			List<Map<String,Object>> armoryEngraves = new ArrayList<>();
			Map<String, Object> armoryProfile = new HashMap<>();
			
			try {
				armoryProfile = (Map<String, Object>) resMap.get("ArmoryProfile");
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
			
			
			
			if(charLv >= 1680){
				weaponList.add(totalEquipmentSearch(resMap,charName));
			}
			
			List<Integer> charGem = totalGemCntSearch(armoryGem,charName);
			if(charGem !=null) {
				gemList.addAll(charGem);
			}
			
			List<Map<String,Object>> charEngrave = totalEngraveSearch(armoryEngraves,mainCharName);
			if(charEngrave !=null) {
				engraveList.addAll(charEngrave);
			}
			
			//고대
			List<String> acceossory1 = totalAccessorySearch(resMap,charName,charClassName,1);
			if(acceossory1 !=null) {
				accessoryList1.addAll(acceossory1);
			}
			//유물
			List<String> acceossory2 = totalAccessorySearch(resMap,charName,charClassName,2);
			if(acceossory2 !=null) {
				accessoryList2.addAll(acceossory2);
			}
			
			if(charCnt ==0) {
				try {
					guildName = armoryProfile.get("GuildName").toString();
					System.out.println(guildName+" 길드 "+mainCharName);
				}catch(Exception e){
					System.out.println("길드네임 조회불가~!!");
				}
				
			}
			charCnt++;
		}
		
		
		List<HashMap<String, Object>> engraveUpd = updOfTotEngrave(refreshEngraveList,mainCharName);
		
		gradeCnt_engrave += calcOfTotEngrave(engraveUpd);
		gradeCnt_lv += calcOfLv(lvList);
		gradeCnt_weapon += calcOfWeapon(weaponList);
		gradeCnt_gem += calcOfGem(gemList);		
		//1고대 2유물
		gradeCnt_accessory += calcOfAccessory(accessoryList1, 1);
		gradeCnt_accessory += calcOfAccessory(accessoryList2, 2);
		
		resMsg += msgOfLv(lvList)+enterStr;
		resMsg += msgOfWeapon(weaponList)+enterStr;
		resMsg += msgOfGem(gemList)+enterStr;
		resMsg += msgOfAccessory(accessoryList1, 1)+enterStr;
		resMsg += msgOfAccessory(accessoryList2, 2)+enterStr;
		resMsg += enterStr;
		
		gradeCnt=gradeCnt_lv
				+gradeCnt_weapon
				+gradeCnt_gem
				+gradeCnt_engrave
				+gradeCnt_accessory; 
		resMsg +="환산 비용: "+gradeCnt +"만 골드";
		
		
		if(gradeCnt>0) {
			grade="모코코";
		}
		if(gradeCnt>500) {
			grade="모코코";
		}
		if(gradeCnt>1000) {
			grade="모코코";
		}
		if(gradeCnt>2000) {
			grade="중급자";
		}
		if(gradeCnt>4000) {
			grade="고인물";
		}
		if(gradeCnt>6000) {
			grade="슈퍼고인물";
		}
		if(gradeCnt>8000) {
			grade="일반인이 아님";
		}
		if(gradeCnt>10000) {
			grade="로악귀";
		}
		
		resMsg += enterStr;
		resMsg += "당신은 "+grade+" !!"+enterStr;
		resMsg += enterStr;
		resMsg += "약간의 상세 더보기"+allSeeStr;
		
		resMsg += "레벨 "+gradeCnt_lv+enterStr;
		resMsg += "무기 "+gradeCnt_weapon+enterStr;
		resMsg += "보석 "+gradeCnt_gem+enterStr;
		resMsg += "악세 "+gradeCnt_accessory+enterStr;
		resMsg += "각인합 "+gradeCnt_engrave+enterStr;
		resMsg += "각인상세"+enterStr + msgOfTotEngrave(engraveUpd)+enterStr;
		
		//카드
		//질서17p
		
		resMsg += "가격표:"+enterStr;
		resMsg += "http://rgb-tns.dev-apc.com/in/totalGold6";
		
		if(gradeCnt_gem ==0) {
			gradeCnt=0;
		}
		saveMap.put("score", gradeCnt);
		saveMap.put("charName", mainCharName);
		saveMap.put("guildName", guildName);
		
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
	
	int totalEquipmentSearch(Map<String,Object> rtnMap,String userId) throws Exception {
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
	
	List<Integer> totalGemCntSearch(Map<String,Object> rtnMap,String userId) throws Exception {
		String[] gemList = {"멸화","홍염","겁화","작열","광휘"};
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
					}else if(equipGem.equals(gemList[4])) {
						cnt++;
						equipGemT4List.add(gemLv);
					}
					
				}
			}
		}

		if(cnt==0) {
			return null;
		}
		
		return equipGemT4List;
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
	
	List<String> totalAccessorySearch(Map<String,Object> rtnMap,String userId,String className,int grade) throws Exception {
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
	
	
	private static <T> Collector<T, ?, List<T>> toReversedList() {
	    return Collectors.collectingAndThen(Collectors.toList(), list -> {
	        Collections.reverse(list);
	        return list;
	    });
	}
	
	//caseNo 1:고대 2:유물
	int calcOfAccessory(List<String> accessoryList,int caseNo) {
		
		int gradeCnt_accessory = 0;

		if(caseNo==1) {
			for (String g : accessoryList) {
				switch (g) {
				case "상상":
					gradeCnt_accessory += 105;
					break;
				case "상중":
				case "중상":
					gradeCnt_accessory += 42;
					break;
				case "상하":
				case "하상":
					gradeCnt_accessory += 11;
					break;
				case "상":
					gradeCnt_accessory += 4.6;
					break;
				case "중중":
					gradeCnt_accessory += 6.6;
					break;
				case "중하":
				case "하중":
					gradeCnt_accessory += 2.2;
					break;
				case "중":
					gradeCnt_accessory += 1;
					break;
				}
			}
		}else {
			for (String g : accessoryList) {
				switch (g) {
				case "상상":
					gradeCnt_accessory += 6;
					break;
				case "상중":
				case "중상":
					gradeCnt_accessory += 5;
					break;
				case "상하":
				case "하상":
					gradeCnt_accessory += 4;
					break;
				case "상":
					gradeCnt_accessory += 3;
					break;
				case "중중":
					gradeCnt_accessory += 2;
					break;
				case "중하":
				case "하중":
					gradeCnt_accessory += 1;
					break;
				case "중":
					gradeCnt_accessory += 0;
					break;
				}
			}
		}
		return gradeCnt_accessory;
	}
	
	//caseNo 1:고대 2:유물
	String msgOfAccessory(List<String> accessoryList,int caseNo) {
		String resMsg="";
		
		int ss = 0;
		int sj = 0;
		int sh = 0;
		int sd = 0;
		int jj =0;
		int jh =0;
		int jd =0;

		for (String g : accessoryList) {
			switch (g) {
			case "상상":
				ss++;
				break;
			case "상중":
			case "중상":
				sj++;
				break;
			case "상하":
			case "하상":
				sh++;
				break;
			case "상":
				sd++;
				break;
			case "중중":
				jj++;
				break;
			case "중하":
			case "하중":
				jh++;
				break;
			case "중":
				jd++;
				break;
			}
		}
		
		if(caseNo==1) {
			resMsg += "악세(고대) : " ;
		}else {
			resMsg += "악세(유물) : " ;
		}
		
		if(ss>0) {
			resMsg += "상상:"+ss+" ";
		}
		if(sj>0) {
			resMsg += "상중:"+sj+" ";
		}
		if(sh>0) {
			resMsg += "상하:"+sh+" ";
		}
		if(sd>0) {
			resMsg += "상단:"+sd+" ";
		}
		if(jj>0) {
			resMsg += "중중:"+jj+" ";
		}
		if(jh>0) {
			resMsg += "중하:"+jh+" ";
		}
		if(jd>0) {
			resMsg += "중단:"+jd+" ";
		}
		
		return resMsg;
	}
	

	int calcOfWeapon(List<Integer> weaponList) {
		int gradeCnt_weapon = 0;
		
		for(int weapon: weaponList) {
			switch(weapon) {
			case 25:
				gradeCnt_weapon += 200+190+180+170+160+150;
				break;
			case 24:
				gradeCnt_weapon += 190+180+170+160+150;
				break;
			case 23:
				gradeCnt_weapon += 180+170+160+150;
				break;
			case 22:
				gradeCnt_weapon += 170+160+150;
				break;
			case 21:
				gradeCnt_weapon += 160+150;
				break;
			case 20:
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
				break;
			}
			
		}
		
		return gradeCnt_weapon;
	}
    
	String msgOfLv(List<Integer> lvList) {
		String resMsg="레벨: ";
		lvList = lvList.stream().sorted().collect(toReversedList());
		for(int lv:lvList) {
			resMsg +=lv+" ";
    	}
		return resMsg;
	}
	
	String msgOfWeapon(List<Integer> weaponList) {
		String resMsg="";
		
		int cntWeaponLv25 =0;
		int cntWeaponLv24 =0;
		int cntWeaponLv23 =0;
		int cntWeaponLv22 =0;
		int cntWeaponLv21 =0;
		int cntWeaponLv20 =0;
		int cntEsther = 0;
		
		for (int weapon : weaponList) {
			switch (weapon) {
			case 25:
				cntWeaponLv25++;
				break;
			case 24:
				cntWeaponLv24++;
				break;
			case 23:
				cntWeaponLv23++;
				break;
			case 22:
				cntWeaponLv22++;
				break;
			case 21:
				cntWeaponLv21++;
				break;
			case 20:
				cntWeaponLv20++;
				break;
			case 9:
			case 8:
			case 7:
			case 6:
				cntEsther++;
				break;
			}

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
		
		return resMsg;
	}

	
	String msgOfGem(List<Integer> gemList) {
		String resMsg="";
		int cntGem10= Collections.frequency(gemList, 10);
		int cntGem9 = Collections.frequency(gemList, 9);
		int cntGem8 = Collections.frequency(gemList, 8);
		int cntGem7 = Collections.frequency(gemList, 7);
		//int cntGem6 = Collections.frequency(gemList, 6);
		
		
		resMsg += "보석(겁작광): ";
		if(cntGem10>0) {
			resMsg += "10:"+cntGem10+" ";
		}
		if(cntGem9>0) {
			resMsg += "9:"+cntGem9+" ";
		}
		if(cntGem8>0) {
			resMsg += "8:"+cntGem8+" ";
		}
		if(cntGem7>0) {
			resMsg += "7:"+cntGem7+" ";
		}/*
		if(cntGem6>0) {
			resMsg += "6:"+cntGem6+" ";
		}*/
		
		if(cntGem10 == 0 && cntGem9 ==0 && cntGem8 ==0 && cntGem7 ==0  ) {
			resMsg += "장착 보석 없음!";
		}
		
		return resMsg;
	}
	
	int calcOfGem(List<Integer> gemList) {
		int gradeCnt_gem =0;
		
		int cntGem10= Collections.frequency(gemList, 10);
		int cntGem9 = Collections.frequency(gemList, 9);
		int cntGem8 = Collections.frequency(gemList, 8);
		int cntGem7 = Collections.frequency(gemList, 7);
		//int cntGem6 = Collections.frequency(gemList, 6);
		
		if(cntGem10>0) {
			gradeCnt_gem += 195*cntGem10;
		}
		if(cntGem9>0) {
			gradeCnt_gem += 67*cntGem9;
		}
		if(cntGem8>0) {
			gradeCnt_gem += 23*cntGem8;
		}
		if(cntGem7>0) {
			gradeCnt_gem += 7.7*cntGem7;
		}
		/*if(cntGem6>0) {
			gradeCnt_gem += 3*cntGem6;
		}*/
		
		return gradeCnt_gem;
	}
	int calcOfLv(List<Integer> lvList) {
    	int gradeCnt_lv=0;
    	
    	for(int lv:lvList) {
    		if(lv>=1680) {
    			gradeCnt_lv += 40;
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
    		if(lv>=1720) {
    			gradeCnt_lv += 160;
    		}
    		if(lv>=1730) {
    			gradeCnt_lv += 190;
    		}
    		if(lv>=1740) {
    			gradeCnt_lv += 220;
    		}
    		if(lv>=1750) {
    			gradeCnt_lv += 250;
    		}
    		if(lv>=1760) {
    			gradeCnt_lv += 280;
    		}
    		if(lv>=1770) {
    			gradeCnt_lv += 310;
    		}
    	}
    	
		
		return gradeCnt_lv;
    }
	int calcOfLv(String maxCharLv) {
		int gradeCnt_lv=0;
		Double lv = Double.parseDouble(maxCharLv);
		if(lv>=1680) {
			gradeCnt_lv += 40;
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
		if(lv>=1720) {
			gradeCnt_lv += 160;
		}
		if(lv>=1730) {
			gradeCnt_lv += 190;
		}
		if(lv>=1740) {
			gradeCnt_lv += 220;
		}
		if(lv>=1750) {
			gradeCnt_lv += 250;
		}
		if(lv>=1760) {
			gradeCnt_lv += 280;
		}
		if(lv>=1770) {
			gradeCnt_lv += 310;
		}
		
		return gradeCnt_lv;
	}
	
	
	List<HashMap<String, Object>> updOfTotEngrave(List<HashMap<String, Object>> refreshEngraveList,String mainCharName) throws Exception  {
		// 각인 전체조회 로직start
		int charEngraveCnt = botService.selectBotLoaEngraveCnt(mainCharName);

		if (charEngraveCnt == 0) {
			// insert Logic
			botService.insertBotLoaEngraveBaseTx(mainCharName);
		}
		HashMap<String, Object> DBcharEngrave = botService.selectBotLoaEngrave(mainCharName);

		boolean updateYn = false;
		// 각인 새로운 정보를 담고있는 list: refreshEngraveList-refreshDataMap 들이 담김
		// refreshDataMap = [{ colName:ENG01, realLv:16 }, { colName:ENG02, realLv:16 }
		// ... ]
		for (HashMap<String, Object> refreshDataMap : refreshEngraveList) {

			try {
				String colName = refreshDataMap.get("colName").toString();
				int realLv = Integer.parseInt(refreshDataMap.get("realLv").toString());
				int dbLv = Integer.parseInt(DBcharEngrave.get(colName).toString());

				if (realLv == dbLv) {
					// System.out.println("검색해온값이 DB와 동일함");
				} else if (realLv > dbLv) {
					// System.out.println("DB값보다 실시간이 큼");
					refreshDataMap.put("userId", mainCharName);
					botService.updateBotLoaEngraveTx(refreshDataMap);
					updateYn = true;
				}
			} catch (Exception e) {
				continue;
			}
		}

		if (updateYn) {
			DBcharEngrave = botService.selectBotLoaEngrave(mainCharName);
		}

		List<HashMap<String, Object>> dbList = new ArrayList<>();

		for (String key : DBcharEngrave.keySet()) {
			Object value = DBcharEngrave.get(key);
			try {
				// userId, modify_date는 여기서 걸러져서 catch됨
				if (Integer.parseInt(value.toString()) == 0) {
					continue;
				}
				HashMap<String, Object> engReverseOldMap = new HashMap<>();
				engReverseOldMap.put("key", key);
				engReverseOldMap.put("value", value);

				dbList.add(engReverseOldMap);

			} catch (Exception e) {
				continue;
			}

		}
		
		return dbList;
	}
	
	String msgOfTotEngrave(List<HashMap<String, Object>> dbList){
		String resMsg="";

		List<HashMap<String, Object>> newList = dbList.stream().sorted(Comparator.comparingInt(x -> Integer.parseInt(x.get("value").toString())))
				.collect(toReversedList());

		for (HashMap<String, Object> hs : newList) {
			HashMap<String, Object> engReverseMap = LoaApiParser.engraveSelectorReverse(hs.get("key").toString(),
					hs.get("value").toString());
			// hs key/value : ENG01/19
			// engReverseMap key/value : 아드레날린/유물 4레벨

			// 16 = 1레벨 17 2레벨 18 3레벨 19 4레벨
			if (Integer.parseInt(hs.get("value").toString()) < 16) {
				continue;
			}

			int tmpgold = LoaApiUtils.totalGoldForEngrave(engReverseMap.get("key").toString(), hs.get("value").toString());
			resMsg += "-" + engReverseMap.get("key") + " - " + engReverseMap.get("value") + "  [ " + tmpgold + " 만 Gold ]" + enterStr;
		}

		return resMsg;
	}
	
	int calcOfTotEngrave(List<HashMap<String, Object>> dbList) {
		int gradeCnt_engrave = 0;
		
		List<HashMap<String, Object>> newList = dbList.stream().sorted(Comparator.comparingInt(x -> Integer.parseInt(x.get("value").toString())))
				.collect(toReversedList());

		for (HashMap<String, Object> hs : newList) {
			HashMap<String, Object> engReverseMap = LoaApiParser.engraveSelectorReverse(hs.get("key").toString(),
					hs.get("value").toString());
			// hs key/value : ENG01/19
			// engReverseMap key/value : 아드레날린/유물 4레벨

			// 16 = 1레벨 17 2레벨 18 3레벨 19 4레벨
			if (Integer.parseInt(hs.get("value").toString()) < 16) {
				continue;
			}

			int tmpgold = LoaApiUtils.totalGoldForEngrave(engReverseMap.get("key").toString(),hs.get("value").toString());
			gradeCnt_engrave += tmpgold;
		}
		
		return gradeCnt_engrave;
	}
	
	String msgOfEngrave(List<Map<String, Object>> engraveList) {
		String resMsg = "";

		List<String> engrave_hash_chk = new ArrayList<>();
		for (Map<String, Object> engrave : engraveList) {
			if (engrave.get("Grade").equals("유물")) {

				if (engrave_hash_chk.contains(engrave.get("Name").toString())) {
					continue;
				}
				engrave_hash_chk.add(engrave.get("Name").toString());

				int tmpgold = LoaApiUtils.totalGoldForEngrave(engrave.get("Name").toString(),
						engrave.get("Level").toString());

				switch (engrave.get("Level").toString()) {
				case "4":
					resMsg += " :" + engrave.get("Name").toString() + " " + engrave.get("Level").toString() + ":"
							+ tmpgold + enterStr;
					break;
				case "3":
					resMsg += " :" + engrave.get("Name").toString() + " " + engrave.get("Level").toString() + ":"
							+ tmpgold + enterStr;
					break;
				case "2":
					resMsg += " :" + engrave.get("Name").toString() + " " + engrave.get("Level").toString() + ":"
							+ tmpgold + enterStr;
					break;
				case "1":
					resMsg += " :" + engrave.get("Name").toString() + " " + engrave.get("Level").toString() + ":"
							+ tmpgold + enterStr;
					break;
				}
			}
		}

		return resMsg;
	}

	int calcOfEngrave(List<Map<String, Object>> engraveList) {
		int gradeCnt_engrave = 0;
		List<String> engrave_hash_chk = new ArrayList<>();
		for (Map<String, Object> engrave : engraveList) {
			if (engrave.get("Grade").equals("유물")) {

				if (engrave_hash_chk.contains(engrave.get("Name").toString())) {
					continue;
				}
				engrave_hash_chk.add(engrave.get("Name").toString());

				int tmpgold = LoaApiUtils.totalGoldForEngrave(engrave.get("Name").toString(),
						engrave.get("Level").toString());
				gradeCnt_engrave += tmpgold;

			}
		}

		return gradeCnt_engrave;
	}
	
}
