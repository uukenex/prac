package my.prac.api.loa.controller;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;
import my.prac.core.util.MiniGameUtil;


@Controller
public class LoaPlayController {
	static Logger logger = LoggerFactory.getLogger(LoaPlayController.class);
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	final String[] unable_save_list = {enterStr,spaceStr,tabStr,allSeeStr,anotherMsgStr,listSeparatorStr,"\\"};

	boolean dailyCheck(HashMap<String,Object> map) {
		int check_val = botService.selectDailyCheck(map);
		boolean check = false;
		
		switch(map.get("cmd").toString()) {
			/*case "weapon_upgrade":
				if((check_val+1) <= 5 ) {
					map.put("extra_msg", (check_val+1)+"회 시도, 5회까지 가능 이벤트 ing!!");
					check = true;
				}
				break;*/
			default:
				if(check_val == 0 ) {
					check = true;
				}
		}
		
		return check;
		
	}
	
	//ㄱㄱㅈㅂ
	String BossAttackInfoForPoint(HashMap<String,Object> map) {
		
		map.put("cmd", "boss_attack");
		String checkCount = botService.selectHourCheckCountForPoint(map);
	    int checkCountInt = Integer.parseInt(checkCount);
	    int defaultCheckCount =40;
	    String msg=map.get("newUserName") + "님," + enterStr;
	    msg += "일일공격횟수: "+checkCountInt+" / "+defaultCheckCount+enterStr+enterStr;
	    
	    List<String> info = botService.selectHourCheckToday(map);
	    if(info ==null){
	    	return "";
	    }
	    for(String s : info) {
	    	msg+=s+enterStr;
	    }
	    
	    return msg;
	}
	
	boolean checkBossCooldown(HashMap<String,Object> map) {
		if("test".equals(map.get("param1"))){
			return true;
		}
		map.put("timeDelay", 15);
		map.put("timeDelayMsg", "");
		
		if ("Y".equals(map.get("item_14_1"))) {
			map.put("timeDelay", 14);
			map.put("timeDelayMsg", "[모래시계]");
	    }
		if ("Y".equals(map.get("item_14_2"))) {
			map.put("timeDelay", 13);
			map.put("timeDelayMsg", "[모래시계2]");
		}
		if ("Y".equals(map.get("item_14_3"))) {
			map.put("timeDelay", 12);
			map.put("timeDelayMsg", "[모래시계3]");
		}
		if ("Y".equals(map.get("item_14_4"))) {
			map.put("timeDelay", 11);
			map.put("timeDelayMsg", "[모래시계4]");
		}
		
		
		/*
		if ("Y".equals(map.get("night_attack_ok"))) {
			map.put("timeDelay", 15);
	        
	    }*/
	    String check_val = botService.selectHourCheck(map);
	    
	    if(check_val == null) {
	        return true; // 공격 가능
	    } else {
	        map.put("extra_msg", check_val + " 이후 재시도 가능합니다.");
	        return false; // 공격 불가
	    }
	}
	
	
	
	int weaponBonusForAttendance(HashMap<String,Object> map) {
		int grade = botService.selectWeaponLvCheck(map);
		int bonus = 0;
		if(grade > 10) {
			bonus = grade-10;
		}
		if(bonus > 0) {
			map.put("extra_msg", "강화보너스 "+bonus+"p 추가");
		}
		
	    return bonus;  
	}
	int weaponBonusForDiceRoll(HashMap<String,Object> map) {
		int grade = botService.selectWeaponLvCheck(map);
		int bonus = 0;
		if(grade > 10) {
			bonus = grade-10;
		}
		if(bonus > 0) {
			map.put("extra_msg", "강화보너스 "+bonus+"p 추가");
		}
		
	    return bonus;  
	}
	
	public HashMap<String, Object> getWeaponStatsForPoint(HashMap<String, Object> map) {
		// 무기 레벨 조회 (없으면 0)
		int weaponLv = botService.selectWeaponLvCheckForPoint(map);
		int accLv = botService.selectAccLvCheckForPoint(map);
		int accMaxLv = botService.selectAccLogMaxLvForPoint(map);
		int sumScore = botSettleService.selectBotPointSumScoreForPoint(map);
		
		HashMap<String, Object> now;
		int hitRingLevel =0;
		try {
			now = botService.selectBotPointHitRingForPoint(map);
			hitRingLevel = Integer.parseInt(now.get("LV").toString()) ;
		} catch (Exception e) {
			hitRingLevel = 0;
		}
		
		List<HashMap<String, Object>> limitList;
		String limit_stat_name="";
		int limit_stat_lv =0;
		
		try {
			limitList = botService.selectBotPointStatUserSumForPoint(map);
			for(HashMap<String,Object> hs : limitList) {
				limit_stat_name = "LIMIT_"+hs.get("STAT_NAME").toString();
				limit_stat_lv = Integer.parseInt(hs.get("LV").toString());
				//STR , DEF, CRI , LIMIT
				//LIMIT_STR , LIMIT_DEF, LIMIT_CRI , LIMIT_LIMIT
				map.put(limit_stat_name, limit_stat_lv);
			}
			
		} catch (Exception e) {
		}
		
		
		
		map.put("level", weaponLv);
	    map.put("acc_level", accLv);
		map.put("acc_max_level", accMaxLv);
		map.put("hit_ring_level", hitRingLevel);
		map.put("sum_score", sumScore);
		getStatPointProcess(map);
	    return map;
	}
	
	
	public HashMap<String, Object> getWeaponStats(HashMap<String, Object> map) {
	    // 무기 레벨 조회 (없으면 0)
	    int weaponLv = botService.selectWeaponLvCheck(map);
	    int accLv = botService.selectAccLvCheck(map);
		int accMaxLv = botService.selectAccLogMaxLv(map);
		int sumScore = botSettleService.selectBotPointSumScore(map);
		
		HashMap<String, Object> now;
		int hitRingLevel =0;
		try {
			now = botService.selectBotPointHitRingTx(map);
			hitRingLevel = Integer.parseInt(now.get("LV").toString()) ;
		} catch (Exception e) {
			hitRingLevel = 0;
		}
		
		List<HashMap<String, Object>> limitList;
		String limit_stat_name="";
		int limit_stat_lv =0;
		
		try {
			limitList = botService.selectBotPointStatUserSum(map);
			for(HashMap<String,Object> hs : limitList) {
				limit_stat_name = "LIMIT_"+hs.get("STAT_NAME").toString();
				limit_stat_lv = Integer.parseInt(hs.get("LV").toString());
				//STR , DEF, CRI , LIMIT
				//LIMIT_STR , LIMIT_DEF, LIMIT_CRI , LIMIT_LIMIT
				map.put(limit_stat_name, limit_stat_lv);
			}
			
		} catch (Exception e) {
		}
		
		
		map.put("level", weaponLv);
	    map.put("acc_level", accLv);
		map.put("acc_max_level", accMaxLv);
		map.put("hit_ring_level", hitRingLevel);
		map.put("sum_score", sumScore);
		getStatPointProcess(map);
	    return map;
	}
	
	void getStatPointProcess(HashMap<String,Object> result) {
		Random rand = new Random();
		int weaponLv = Integer.parseInt(result.get("level").toString());
		int accLv = Integer.parseInt(result.get("acc_level").toString());
		int accMaxLv = Integer.parseInt(result.get("acc_max_level").toString());
		int hitRingLevel = Integer.parseInt(result.get("hit_ring_level").toString());
		
		// 실제 적용될 레벨
	    int acc_apply_level = accMaxLv;
		
	    int default_def = 5;
	    
		 // --- 현재 accLv 기준 스탯 ---
	    int[] pow_data = MiniGameUtil.POW_MAP_ACC.getOrDefault(accMaxLv, new int[]{0, 0, 0, 0}); 
	    int plus_crit = pow_data[0];
	    int plus_min  = pow_data[1];
	    int plus_max  = pow_data[2];
	    int plus_def  = pow_data[3];
	    /*
	    // --- 최대 레벨-1 기준 스탯 ---
	    int[] pow_data_max = MiniGameUtil.POW_MAP_ACC.getOrDefault(accMaxLv - 1, new int[]{0, 0, 0, 0}); 
	    int plus_crit_max = pow_data_max[0];
	    int plus_min_max  = pow_data_max[1];
	    int plus_max_max  = pow_data_max[2];
	    int plus_def_max  = pow_data_max[3];

	    // --- accLv < accMaxLv 조건일 때, Max-1 스탯으로 덮어씌우기 ---
	    if (accLv < accMaxLv) {
	    	acc_apply_level = accMaxLv-1;
	        plus_crit = plus_crit_max;
	        plus_min  = plus_min_max;
	        plus_max  = plus_max_max;
	        plus_def  = plus_def_max;
	    }
		*/
	    int part_of_min_weapon = (1 + (weaponLv / 2)) / 2 ;
	    int part_of_max_weapon = (5 + weaponLv) * 2 ;

		int part_of_min_acc = plus_min;
		int part_of_max_acc = plus_max;
		
	    double part_of_weapon_crit = Math.min(0.20 + weaponLv * 0.01 , 1.0);
	    double part_of_acc_crit = Math.min(0 + plus_crit * 0.01 , 1.0);
	    

	    // 결과 저장
	    result.put("level", weaponLv);
	    result.put("acc_level", accLv);
		result.put("acc_max_level", accMaxLv);
		result.put("acc_apply_level", acc_apply_level);
		
		result.put("part_of_min_weapon", part_of_min_weapon);
		result.put("part_of_max_weapon", part_of_max_weapon);
		result.put("part_of_min_acc", part_of_min_acc);
		result.put("part_of_max_acc", part_of_max_acc);
		result.put("part_of_weapon_crit", part_of_weapon_crit);
		result.put("part_of_acc_crit", part_of_acc_crit);
		
		int limit_str =0;
		int limit_def =0;
		double limit_cri =0.00;
		int limit_limit =0;
		if(result.get("LIMIT_STR")!=null) {
			limit_str = Integer.parseInt(result.get("LIMIT_STR").toString());
	    }
		if(result.get("LIMIT_DEF")!=null) {
			limit_def = Integer.parseInt(result.get("LIMIT_DEF").toString());
		}
		if(result.get("LIMIT_CRI")!=null) {
			limit_cri = Double.parseDouble(result.get("LIMIT_CRI").toString())*0.01;
		}
		if(result.get("LIMIT_LIMIT")!=null) {
			limit_limit = Integer.parseInt(result.get("LIMIT_LIMIT").toString());
		}
		
		result.put("limit_str", limit_str); 
		result.put("limit_def", limit_def); 
		result.put("limit_cri", limit_cri); 
		result.put("limit_limit", limit_limit); 
		
		int min = part_of_min_weapon + part_of_min_acc;
	    int max = part_of_max_weapon + part_of_max_acc+limit_str;
	    int baseDamage = rand.nextInt(max - min + 1) + min;
	    double criticalChance = part_of_weapon_crit+part_of_acc_crit+limit_cri;
	    
	    result.put("min", min);
	    result.put("max", max);
	    result.put("baseDamage", baseDamage);
	    result.put("criticalChance", criticalChance);
	    result.put("def", default_def+plus_def+limit_def);
	    result.put("hit", hitRingLevel);
	}
	List<HashMap<String,Object>> selectPointItemUserListForPoint(HashMap<String,Object> map){
		List<String> ableItemList = new ArrayList<>();
		List<HashMap<String,Object>> PointItemOptionList= new ArrayList<>(); 
		List<HashMap<String,Object>> userItemList = new ArrayList<>();
		try {
			PointItemOptionList = botService.selectPointItemOptionList(map);
			userItemList = botService.selectPointItemUserListForPoint(map);
			map.put("totalItemListSize",PointItemOptionList.size());
			map.put("userItemListSize",userItemList.size());
			
			int totSum =0;
			int userSum = 0;
			
			for(HashMap<String,Object> hs : PointItemOptionList) {
				totSum += Integer.parseInt(hs.get("MAX_LV").toString());
			}
			
			if(userItemList.size() > 0) {
				for(HashMap<String,Object> hs : userItemList) {
					userSum += Integer.parseInt(hs.get("ITEM_LV").toString());
				}
				
			}
			
			map.put("totSum", totSum);
			map.put("userSum", userSum);
			//MiniGameUtil.itemAddtionalFunction(map);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return userItemList;
	}
	
	List<String> selectPointItemUserList(HashMap<String,Object> map){
		List<String> ableItemList = new ArrayList<>();
		try {
			List<HashMap<String,Object>> PointItemOptionList = botService.selectPointItemOptionList(map);
			List<HashMap<String,Object>> userItemList = botService.selectPointItemUserList(map);
			map.put("totalItemListSize",PointItemOptionList.size());
			map.put("userItemListSize",userItemList.size());
			//MiniGameUtil.itemAddtionalFunction(map);
			for (HashMap<String,Object> userItem : userItemList) {
				ableItemList.add(userItem.get("ITEM_NO")+"-"+userItem.get("ITEM_LV"));
			}
			
			int totSum =0;
			int userSum = 0;
			
			for(HashMap<String,Object> hs : PointItemOptionList) {
				totSum += Integer.parseInt(hs.get("MAX_LV").toString());
			}
			
			if(userItemList.size() > 0) {
				for(HashMap<String,Object> hs : userItemList) {
					userSum += Integer.parseInt(hs.get("ITEM_LV").toString());
				}
				
			}
			
			map.put("totSum", totSum);
			map.put("userSum", userSum);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ableItemList;
	}
	
	int weaponBonusForFight(HashMap<String,Object> map) {
		HashMap<String,Object> targetMap = new HashMap<>();
		targetMap.put("roomName", map.get("roomName"));
		targetMap.put("userName", map.get("param1"));
		
		int grade0 = botService.selectWeaponLvCheck(map);
		int grade1 = botService.selectWeaponLvCheck(targetMap);
		
		int bonus0 = (grade0 >= 11) ? (grade0 - 10) : 0;  // 내 보너스
		int bonus1 = (grade1 >= 11) ? (grade1 - 10) : 0;  // 상대 보너스
		
		return bonus0 - bonus1;  // 보너스 차이
	}
	
	String testMethod(HashMap<String,Object> map) {
		String str="★☆♥♠♣♦✓✔✖☑☀☁☂☃☕☎✉☘⚠☠☯⚡❄❌✅"
				+"	♥, ♠, ♣, ♦, ⚠, ☀, ☁ ♛ ✪";
		
		str = "테스트완료입니다";
		
		return str;
	}
	
	String pointSeasonMsg() {
		return "";
		//return "7월1일 람쥐포인트 시즌1이 종료됩니다./포인트상점 을 통해 사용할수있습니다.";
	}
	
	String usePoint(HashMap<String,Object> map) {
		return  "아마도 개발자에게 전달완료..";
	}
	
	String attendanceToday(HashMap<String,Object> map) {
		String msg="";
		List<HashMap<String,Object>> point_map = botService.selectBotPointRankToday(map);
		
		msg +=map.get("roomName")+" 출석명단"+enterStr;
		
		for(HashMap<String,Object> hm : point_map) {
			msg += hm.get("USER_NAME")+enterStr ;
		}
		
		
		return msg;
	}
	String attendance(HashMap<String,Object> map) {
		map.put("cmd", "attendance");
		if(!dailyCheck(map)) {
			return map.get("userName")+"님 오늘의 출석 포인트는 이미 획득 했습니다.";
		}
		
		String extraMsg="";
		int bonus = weaponBonusForAttendance(map);
		if(bonus>0) {
			extraMsg+=enterStr+map.get("extra_msg");
		}
		
		Random random = new Random(); // 랜덤객체
		int score = random.nextInt(100)+1;
		int new_score=0;
		map.put("score",score+bonus);
		
		try {
			new_score = botService.insertBotPointRankTx(map);
		} catch (Exception e) {
			return "오류발생";
		}
		
		return map.get("userName")+"님 출석포인트 "+score+"점 획득"
			  +extraMsg + enterStr+"갱신 포인트 : "+new_score;
	}
	
	
	String diceRoll(HashMap<String,Object> map) {
		map.put("cmd", "diceRoll");
		if(!dailyCheck(map)) {
			return map.get("userName")+"님 오늘의 주사위 포인트는 이미 획득 했습니다.";
		}
		
		String extraMsg="";
		int bonus = weaponBonusForAttendance(map);
		if(bonus>0) {
			extraMsg+=enterStr+map.get("extra_msg");
		}
		
		Random random = new Random(); // 랜덤객체
		int number = random.nextInt(101);
		String prefix="";
		
		int score = 0;
		int new_score=0;
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_1_1 = ableItemList.contains("1-1");
		boolean item_1_2 = ableItemList.contains("1-2");
		boolean item_1_3 = ableItemList.contains("1-3");
		boolean item_2_1 = ableItemList.contains("2-1");
		boolean item_2_2 = ableItemList.contains("2-2");
		
		
		if(number>=99) { // 500
			if(item_2_2 && number == 99) {
				prefix="아이템 [럭키세븐] 효과가 발동되었습니다"
					  +enterStr+"굴리기전부터 운명적입니다. 행운의 신이 함께합니다.";
				score =+199;
			}else {
				prefix="굴리기전부터 운명적입니다. 행운의 신이 함께합니다.";
				score =+100;
			}
			
		}else if(number>=85) { // 85~98
			prefix="행운이 쌓인채로 굴러갑니다.";
			score =+number;
		}else if(number>=50) { //50~84점 => 25~42포인트
			
			if(item_2_1 && number == 77) {
				prefix="아이템 [럭키세븐] 효과가 발동되었습니다"
					  +enterStr+"오늘은 행운의 날!";
				score =+200;
			}else {
				prefix="데굴데굴..";
				score =+(number/2);
			}
		}else if(number>=20) {//0

			if (item_2_2) {
				if(number == 33) {
					prefix = "아이템 [럭키세븐] 효과가 발동되었습니다" + enterStr ;
					score = +33*2;
				}else if(number == 33) {
					prefix = "아이템 [럭키세븐] 효과가 발동되었습니다" + enterStr ;
					score = +22*2;
				}else {
					prefix = "데굴데굴..";
					score = +(number / 2);
				}
				
				
			}else {
				prefix = "데굴데굴..";
				score = +(number / 2);
			}
			
			prefix="또르르륵..";
			score =+0;
		}else { // -50
			if(item_2_1 && number == 7) {
				prefix="아이템 [럭키세븐] 효과가 발동되었습니다"
					  +enterStr+"소소한 행운발견!";
				score =+77;
			}else if (item_1_1){
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
					      +enterStr+"아이템 [부적]이 도움을 줍니다 (+25p 보정)"	 ;
					score =-25;
			}else if (item_1_2){
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
					      +enterStr+"아이템 [부적]의 효과로 보호받았습니다(+50p 보정)"	 ;
					score =-0;
			}else if (item_1_3){
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
					      +enterStr+"아이템 [부적]의 가호가 함께합니다 (+60p 보정)"	 ;
					score =+10;
			}else {
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
				      +enterStr+"(주사위 눈의 20보다 낮으면 -50 p)"	 ;
				score =-50;
			}
		}
		
		try {
			map.put("score", score+bonus);
			new_score = botService.insertBotPointRankTx(map);
		} catch (Exception e) {
			return "오류발생";
		}
		
		
		String msg = prefix+enterStr+"『"+map.get("userName") + "』 님의 주사위: "+number+" (0~100) "
				   +enterStr+score+"점 획득"
				   +extraMsg
				   +enterStr+"갱신 포인트 : "+new_score;
		if(new_score < 0) {
			//msg += enterStr+"＊마이너스 포인트는 오늘의 주사위 한번더!";
		}
		return msg;
	}
	
	String fight_s(HashMap<String,Object> map) {
		map.put("cmd", "fight_s");
		String userName;
		String targetName;
		int score;
		//like UPPER(#{newUserName, jdbcType=VARCHAR}||'%'
		userName = map.get("userName").toString();
		try {
			targetName = map.get("param1").toString();
			score = Integer.parseInt(map.get("param2").toString());
		}catch(Exception e) {
			return "/결투 결투자명 포인트 형식으로 입력해야합니다.";
		}
		
		if(score <= 0) {
			return "포인트는 1포인트이상 입력해주세요.";
		}
		
		/*
		int cnt = botService.selectBotPointRankFightBeforeCount(map);
		if(cnt>0) {
			return "결투 신청 쿨타임(신청만..2min..)";
		}
		*/
		
		List<HashMap<String,Object>> newMap = botService.selectBotPointRankFightBeforeCheck(map);
		if(newMap.size() !=2) {
			return "결투신청 오류:대결자가 "+newMap.size()+"명!";
		}
		
		int p0 = Integer.parseInt(newMap.get(0).get("SCORE").toString());
		int p1 = Integer.parseInt(newMap.get(1).get("SCORE").toString());
		
		if( score > p0 ) {
			return newMap.get(0).get("USER_NAME")+" 님 포인트 부족!"+enterStr+
				  "*결투포인트: "+score+"p"+enterStr+
				  "*현재포인트: "+p0+"p";
			
		}
		if( score > p1 ) {
			return newMap.get(1).get("USER_NAME")+" 님 포인트 부족!"+enterStr+
				  "*결투포인트: "+score+"p"+enterStr+
				  "*현재포인트: "+p1+"p";
		}
		
		
		//HAVING SUM(A.SCORE) >= #{param2, jdbcType=NUMERIC}
		//기본적으로 newMap size =2이다
		/*if( newMap.size()==1) {
			return newMap.get(0).get("USER_NAME").toString()+" vs "+"알수없는 상대"+enterStr+"결투 실패!";
		}*/
		
		try {
			botService.insertBotPointFightSTx(map);
		}catch(Exception e) {
			return "오류발생";
		}
		
		
		List<HashMap<String,Object>> newMap2= botService.selectBotPointRankFightBeforeCheck2(map);
		String newMsg ="";
		try {
			newMsg = enterStr+enterStr+"최근 30일간 결투전적"+enterStr+newMap2.get(0).get("RESULT").toString(); 
		}catch(Exception e) {
			System.out.println(e.toString());
		}
		
		
		/*
		int diff = weaponBonusForFight(map);
		

		String extraMsg = "";

		if (diff != 0) {
	        extraMsg += enterStr + enterStr + Math.abs(diff) + "강화 차이로 인한 승률보정!";

	        int userRate = 50;
	        int targetRate = 50;

	        if (diff > 0) { // userName 강화 우세
	            userRate = 50 + diff;
	            targetRate = 50 - diff;
	        } else { // targetName 강화 우세
	            userRate = 50 + diff; // diff는 음수
	            targetRate = 50 - diff;
	        }

	        targetRate = 100 - userRate;

	        extraMsg += enterStr + userName + " " + userRate + " : " + targetRate + " " + targetName;
	    }
		*/
		return userName + " 님의 결투신청!"+enterStr +
				"**결투포인트: "+score+enterStr+enterStr+
				map.get("param1")+ " 님, 결투를 받으시려면"+enterStr+
				" /저스트가드 입력 (60sec)"
				+newMsg;
				//+extraMsg;
	}
	
	String fight_e(HashMap<String,Object> map) {
		map.put("cmd", "fight_e");
		String userName;
		String targetName;
		int score;
		
		userName = map.get("userName").toString();
		//현재 내가 결투자 인지 확인, fight_s 에서 찾아옴
		int seq =0;
		List<HashMap<String,Object>> fightMap = botService.selectBotPointFight(map);
		if(fightMap==null || fightMap.size()==0) {
			return userName+" 님, 요청 결투가 없음!";
		}
		if(fightMap.size() > 1) {
			return userName+" 님, 요청 결투가 2개이상!(오류시 최대2분 후 정상화..!)";
		}
		
		try {
			seq = Integer.parseInt(fightMap.get(0).get("SEQ").toString());
			userName = fightMap.get(0).get("USER_NAME").toString();
			targetName = fightMap.get(0).get("TARGET_NAME").toString();
			score = Integer.parseInt(fightMap.get(0).get("SCORE").toString());
		}catch(Exception e) {
			return userName+" 님, 요청 결투가 없음2!";
		}
		map.put("seq", seq);
		map.put("userName", userName);
		map.put("param1", targetName);
		map.put("param2", score);
		
		/*
		List<HashMap<String,Object>> newMap = botService.selectBotPointRankFightBeforeCheck(map);
		if(newMap==null || newMap.size()==0 || newMap.size()==1) {
			return "남아있는 포인트가 부족합니다.";
		}
		*/
		List<HashMap<String,Object>> newMap = botService.selectBotPointRankFightBeforeCheck(map);
		if(newMap.size() !=2) {
			return "결투신청 오류:대결자가 "+newMap.size()+"명!";
		}
		
		
		int p0 = Integer.parseInt(newMap.get(0).get("SCORE").toString());
		int p1 = Integer.parseInt(newMap.get(1).get("SCORE").toString());
		if( score > p0 ) {
			return newMap.get(0).get("USER_NAME")+" 님 포인트 부족!"+enterStr+
				  "*결투포인트: "+score+"p"+enterStr+
				  "*현재포인트: "+p0+"p";
			
		}
		if( score > p1 ) {
			return newMap.get(1).get("USER_NAME")+" 님 포인트 부족!"+enterStr+
				  "*결투포인트: "+score+"p"+enterStr+
				  "*현재포인트: "+p1+"p";
		}
		
		//0번객체
		HashMap<String,Object> h1 = newMap.get(0);
		//1번객체
		HashMap<String,Object> h2 = newMap.get(1);
		
		//도전자가 main 
		String main_user_name ="";
		int main_user_point =0;
		int main_user_point_org =0;
		String sub_user_name ="";
		int sub_user_point =0;
		int sub_user_point_org =0;
		String winner_name="";
		String loser_name="";
		if(h1.get("USER_NAME").equals(userName)) {
			//h1 : 도전자 , h2: 수락자
			main_user_name = h1.get("USER_NAME").toString();
			main_user_point = Integer.parseInt(h1.get("SCORE").toString());
			sub_user_name = h2.get("USER_NAME").toString();
			sub_user_point = Integer.parseInt(h2.get("SCORE").toString());
		}else {
			//h2 : 도전자 , h1: 도전자
			main_user_name = h2.get("USER_NAME").toString();
			main_user_point = Integer.parseInt(h2.get("SCORE").toString());
			sub_user_name = h1.get("USER_NAME").toString();
			sub_user_point = Integer.parseInt(h1.get("SCORE").toString());
		}
		main_user_point_org = main_user_point;
		 sub_user_point_org =  sub_user_point;
		
		//int diff = weaponBonusForFight(map);
		//int baseWinRate = 50 + diff;	
		int baseWinRate = 50 ;	
		 
		Random random = new Random(); // 랜덤객체
		int number = random.nextInt(100);
		if(number < baseWinRate) {
			//main이 이기는 로직
			winner_name = main_user_name;
			loser_name = sub_user_name;
			main_user_point += score;
			sub_user_point  -= score;
		}else {
			//sub가 이기는 로직
			loser_name = main_user_name;
			winner_name = sub_user_name;
			main_user_point -= score;
			sub_user_point  += score;
		}		
		
		map.put("winnerName", winner_name);
		map.put("loserName", loser_name);
		try {
			//도전 종료처리
			botService.updateBotPointFightETx(map);
		} catch (Exception e1) {
			
		}
		
		String extraMsg="";
		/*
		if (diff != 0) {
	        extraMsg += enterStr + enterStr + Math.abs(diff) + "강화 차이로 인한 승률보정!";

	        int userRate = 50;
	        int targetRate = 50;

	        if (diff > 0) { // userName 강화 우세
	            userRate = 50 + diff;
	            targetRate = 50 - diff;
	        } else { // targetName 강화 우세
	            userRate = 50 + diff; // diff는 음수
	            targetRate = 50 - diff;
	        }

	        targetRate = 100 - userRate;

	        extraMsg += enterStr + userName + " " + userRate + " : " + targetRate + " " + targetName;
	    }
		*/
		
		return winner_name+" 님, 승리"+enterStr
				+main_user_name +" : "+main_user_point_org+" → "+ main_user_point +" p"+enterStr
				+ sub_user_name +" : "+ sub_user_point_org+" → "+  sub_user_point +" p"+enterStr
				+ extraMsg;
	}
	
	String pointShop(HashMap<String,Object> map) {
		return "[포인트상점 목록]"
	          +enterStr+"/상자구매 : 200p(10회까지 무료)"
	          +enterStr+"/악세구매 : 200p(상자 누적50회부터 가능)"
	          +enterStr+"/악세강화 : 레벨별 상이"
	          +enterStr+"/반지강화 : 500p(보스회피시 할인)"
	          +enterStr+enterStr+"[언리밋]"
	          +enterStr+"/맥포강화 : 1000p(맥스포인트획득제한 +1)"
	          +enterStr+"/근력강화 : 1000p(최대데미지+1)"
	          +enterStr+"/방어강화 : 1000p(방어력+1)"
	          +enterStr+"/치명강화 : 1000p(치명타+1%)"
	          +enterStr
	          +enterStr;
	}
	
	String pointBoxOpenBuy(HashMap<String,Object> map) {
		map.put("cmd", "pointShop");
		String msg = map.get("userName")+" 님,"+enterStr;
		int defaultScore = 200;
		try {
			
			int count = botService.selectPointItemUserCount(map);
			if(count <= 10) {
				msg += "welcome gift 수령! 0p 소모!"+enterStr;
			}else {
				List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
				int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
				if(score < defaultScore) {
					return map.get("userName")+" 님, "+defaultScore+" p 이상만 가능합니다.";
				}
				map.put("score", -defaultScore);
				int new_score = botService.insertBotPointRankTx(map);
				
				msg += defaultScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			}
			
		}catch(Exception e) {
			return "포인트샵 조회 오류입니다.";
		}
		
		int boxCount =0;
		try {
			botService.insertPointNewBoxOpenTx(map);
			
			boxCount = botService.selectPointNewBoxCount(map);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		msg += "아이템상자 구매가 완료되었습니다."
			+ enterStr + "보유 상자 갯수 : "+boxCount
		    + enterStr + " /상자열기 입력으로 상자열기"
		    ;
		
		return msg;
	}
	
	
	//상자열기
	String pointBoxOpen(HashMap<String,Object> map) {
	    Random rand = new Random();
	    String msg = map.get("userName") + " 님," + enterStr;

	    // 0. 현재 상자 openFlag 조회 (null 안전 처리)
	    String openFlag = "";
	    HashMap<String, Object> rareItemInfo = null;
        try {
            rareItemInfo = botService.selectPointItemUserOpenFlag1(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if(rareItemInfo ==null || rareItemInfo.size()==0) {
        	try {
    	        openFlag = botService.selectPointItemUserOpenFlag(map);
    	    } catch (Exception e) {
    	    	return msg+"상자열기 오류입니다";
    	    }
        }else {
        	openFlag = rareItemInfo.get("OPEN_FLAG").toString();
        }
        
	    
        List<String> userItemList = selectPointItemUserList(map);
        
        
	    String itemNo = "";
	    String itemName = "";
	    String itemLv = "1";
        String itemDesc = "";

        if(openFlag ==null) {
        	return msg+"상자가 없습니다"
        			+enterStr+"/상자구매 : 200p";
        }
        
        int baseRate = 24;
        int defaultRate = baseRate;

        try {
            int curRate = botService.selectPointItemUserOpenRate(map);
            
            if (curRate >= baseRate) {
            	
            } else {
                // 25보다 작은 경우: 보정 강하게 (차이의 2배 이상 보정)
                defaultRate = baseRate - (curRate - baseRate) * 2;  
            }
            
        } catch (Exception e1) {
            System.out.println("에러");
            defaultRate = baseRate;
        }
        
	    switch (openFlag) {
	        case "0": // 아직 오픈하지 않은 상자
	        	// 2. 보물상자 오픈 성공 → 보물 정보 조회
	            List<HashMap<String, Object>> itemInfoList = new ArrayList<>();
	            map.put("cmd", "pointBoxOpenUp");
	            try {
	                itemInfoList = botService.selectPointItemInfoList(map);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }

	            // 3. 유저가 가지고 있는 아이템 목록 (ITEM_NO-ITEM_LV)
	            

	            // 4. MAX_LV 미도달 아이템 후보 목록 생성
	            List<HashMap<String, Object>> candidateList = new ArrayList<>();
	            for (HashMap<String, Object> itemInfo : itemInfoList) {
	                itemNo = itemInfo.get("ITEM_NO").toString();
	                int maxLevel = Integer.parseInt(itemInfo.get("MAX_LV").toString());
	                int currentLevel = 0;

	                if(itemInfo.get("ITEM_GB") !=null && itemInfo.get("ITEM_GB").toString().equals("1")) {
	                	if(userItemList.size() < 5) {
	                		continue;
	                	}
	                }
	                
	                for (String userItem : userItemList) {
	                    String[] parts = userItem.split("-");
	                    if (parts[0].equals(itemNo)) {
	                        currentLevel = Integer.parseInt(parts[1]);
	                        break;
	                    }
	                }

	                if (currentLevel < maxLevel) {
	                    itemInfo.put("CURRENT_LV", currentLevel);
	                    candidateList.add(itemInfo);
	                }
	            }

	            if (candidateList.isEmpty()) {
	                msg += "획득 가능한 보물이 없습니다!";
	                return msg;
	            }
	        	
	        	
	        	// 1. 보물상자 성공 여부 (20%)
	            boolean isSuccess = rand.nextInt(100) <= defaultRate;

	            if (!isSuccess) {
	                try {
	                    int count = botService.selectPointItemUserCount(map);
	                    msg += "보물상자 오픈 실패!";
	                    msg += enterStr+"보물상자가 모래가 되어 사라졌습니다";
	                    
	                    map.put("cmd", "pointBoxOpenDel");
	                    botService.updatePointNewBoxOpenTx(map);
	                    if (count > 10) {
	                        // 실패 시 0~200P 환급
	                        int refundPoint = rand.nextInt(151);
	                        msg += enterStr + refundPoint + "P가 환급";

	                        
	                        map.put("score", refundPoint);
	                        int new_score = botService.insertBotPointRankTx(map);
	                        msg += enterStr + "갱신포인트 : " + new_score + "p";
	                    }
	                    
	                } catch (Exception e) {
	                    msg = "보물상자 오픈 오류!!";
	                    e.printStackTrace();
	                }
	                
	                int boxCount =0;
	        		try {
	        			boxCount = botService.selectPointNewBoxCount(map);
	        			
	        		} catch (Exception e) {
	        			e.printStackTrace();
	        		}
	        		
	        		if(boxCount>0) {
	        			msg +=enterStr + "보유 상자 갯수 : "+boxCount;
	        		}
	                return msg;
	            }

	            

	            // 5. 후보 중 랜덤 선택
	            HashMap<String, Object> chosenItem = candidateList.get(rand.nextInt(candidateList.size()));
	            itemNo = chosenItem.get("ITEM_NO").toString();
	            itemDesc = chosenItem.get("ITEM_DESC").toString();
                itemName = chosenItem.get("ITEM_NAME").toString();
	            
	            
	            int maxLevel = Integer.parseInt(chosenItem.get("MAX_LV").toString());
	            int currentLevel = Integer.parseInt(chosenItem.get("CURRENT_LV").toString());
	            
	            // 6. 보상 레벨 결정
	            String nowOpenFlag = "0";
	            int rewardLevel;
	            if (currentLevel >= maxLevel || maxLevel == 1) { 
	                // 맥스레벨 도달 or 맥스레벨이 1인 경우
	                rewardLevel = 1;
	                nowOpenFlag = "2";
	            } else {
	            	// 20% 확률로 2레벨, 아니면 1레벨
	                rewardLevel = rand.nextInt(100) < 20 ? 2 : 1;

	                // ✅ 현재 레벨 + 보상 레벨이 맥스 초과하면 조정
	                if (currentLevel + rewardLevel > maxLevel) {
	                    rewardLevel = maxLevel - currentLevel;
	                }

	                nowOpenFlag = (rewardLevel == 2) ? "1" : "2";
	            }

	            if(itemNo.equals("9")||itemNo.equals("19")) {
	            	nowOpenFlag ="1";
	            }
	            
	            // 7. DB 저장 (보물 지급 처리)
	            try {
	                map.put("rdLv", rewardLevel);
	                map.put("rdItemNo", itemNo);
	                map.put("openFlag", nowOpenFlag);
	                map.put("cmd", "pointBoxOpenUp");
	                botService.updatePointNewBoxOpenTx(map);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }

	            // 8. 메시지 구성
	            switch (nowOpenFlag) {
	                case "1":
	                    msg += "상자가 황금빛으로 빛나고 있습니다!!" + enterStr;
	                    msg += "/상자열기 로 열기" + enterStr;
	                    break;
	                case "2":
	                	msg += "보물 획득!!" + enterStr;
	    	            msg += "item No : " + itemNo + enterStr;
	    	            msg += itemName + itemLv+" lv"+ enterStr;
	    	            msg += itemDesc;
	    	            userItemList = selectPointItemUserList(map);
	                    break;
	            }
	            break;

	        case "1": // 레벨2 상자, /상자열기 호출 시
	            // 재사용 가능한 조회 메서드 활용
	            if (rareItemInfo != null) {
	                itemNo = rareItemInfo.get("ITEM_NO").toString();
	                itemName = rareItemInfo.get("ITEM_NAME").toString();
	                itemLv = rareItemInfo.get("LV").toString();
	                itemDesc = rareItemInfo.get("ITEM_DESC").toString();
	            }

	            // DB 업데이트 (openFlag -> 2)
	            try {
	                map.put("openFlag", "2");
	                botService.updatePointNewBoxOpen2Tx(map);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }

	            msg += "보물 획득!!" + enterStr;
	            msg += "item No : " + itemNo + enterStr;
	            msg += itemName + itemLv+"lv"+ enterStr;
	            msg += itemDesc;
	            
	            userItemList = selectPointItemUserList(map);
	            break;
	    }
	    
	    int boxCount =0;
		try {
			boxCount = botService.selectPointNewBoxCount(map);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(boxCount>0) {
			msg +=enterStr + "보유 상자 갯수 : "+boxCount;
		}
	    try {
	    	if(map.get("totalItemListSize")!=null && !map.get("totalItemListSize").equals("")) {
				msg += enterStr+"보물수집: "+map.get("userItemListSize")+" / "+(map.get("totalItemListSize"));
				msg += "(수집점수: "+map.get("userSum")+" / "+(map.get("totSum"))+")"+enterStr;
			}
			
	    }catch(Exception e) {
	    	
	    } 
		
		
	    return msg;
	}
	
	
	String gamble(HashMap<String,Object> map) {
		map.put("cmd", "gamble_s2");
		//map.put("score", -200);
		map.put("score", 0);
		if(!dailyCheck(map)) {
			return map.get("userName")+"님 오늘의 뽑기 포인트는 이미 획득 했습니다.";
		}
		
		String userName = map.get("userName").toString();
		
		HashMap<String,Object> info = botService.selectBotPointUpdownS(map);
		if(info == null || info.size() ==0) {
			if(map.get("param1")!=null && !map.get("param1").toString().equals("") ) {
				return userName+" 님, 신규 뽑기는 /뽑기 입력.";
			}
			
			//신규대상인 경우 
			//List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			try {
				/*
				int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
				
				if(score < 200) {
					return userName+" 님, 200p 이상만 가능합니다.";
				}
				
				int new_score = botService.insertBotPointRankTx(map);
				*/
				Random random = new Random(); // 랜덤객체
				map.put("randomNumber", random.nextInt(100)+1);
				botService.insertBotPointUpdownSTx(map);
				
				return userName+" 님!"+enterStr+
						//score+"p → "+new_score+"p"+enterStr+
						"/뽑기 숫자(1~100) 입력하시면 updown게임 진행!"+enterStr+
						"최대 500p에서 점차적으로 줄어듭니다!";
			}catch(Exception e) {
				return userName+" 님, updown 오류!";
			}
		}else {
			//count가 1이상, 현재 진행분...
			int in_number;
			String res = "";
			
			int completeYn = -1;
			int seq = -1;
			int number1 = -1;
			int number2 = -1;
			int number3 = -1;
			int number4 = -1;
			int number5 = -1;
			int targetNumber = -999;
			
			try {
				in_number = Integer.parseInt(map.get("param1").toString());
				targetNumber = Integer.parseInt(info.get("TARGET_NUMBER").toString());
				completeYn = Integer.parseInt(info.get("COMPLETE_YN").toString());
				seq = Integer.parseInt(info.get("SEQ").toString());
				
				if(in_number > 100 || in_number < 0) {
					return userName+ " 님, 0~100사이 입력해주세요";
				}
				
				res += userName+" 님, 현재 입력 숫자:"+in_number+enterStr+
						"총 6회 중 "+(completeYn+1) +" 회 진행중"+enterStr;
				
				List<String> ableItemList = selectPointItemUserList(map); 
				boolean item_3_1 = ableItemList.contains("3-1");
				
				
				switch(completeYn) {
					case 5:
						number5= Integer.parseInt(info.get("NUMBER5").toString());
					case 4:
						number4= Integer.parseInt(info.get("NUMBER4").toString());
					case 3:
						number3= Integer.parseInt(info.get("NUMBER3").toString());
					case 2:
						number2= Integer.parseInt(info.get("NUMBER2").toString());
					case 1:
						number1= Integer.parseInt(info.get("NUMBER1").toString());
					case 0:
						break;
				}
				
				//1회차 시도때는 completeYn : 0
				//2회차 시도때는 completeYn : 1 , number1과 동일한지 비교하기
				//3회차 시도때는 completeYn : 2 , number1~2와 동일한지 비교하기
				//4회차 시도때는 completeYn : 3 , number1~3과 동일한지 비교하기
				//5회차 시도때는 completeYn : 4 , number1~4와 동일한지 비교하기
				//6회차 시도때는 completeYn : 5 , number1~5와 동일한지 비교하기
				
				boolean breakFlag=false;
				switch(completeYn) {
					case 5:
						if(in_number ==  number5) {
							breakFlag = true;
							break;
						}
					case 4:
						if(in_number ==  number4) {
							breakFlag = true;
							break;
						}
					case 3:
						if(in_number ==  number3) {
							breakFlag = true;
							break;
						}
					case 2:
						if(in_number ==  number2) {
							breakFlag = true;
							break;
						}
					case 1:
						if(in_number ==  number1) {
							breakFlag = true;
							break;
						}
					break;
				}
				if(breakFlag) {
					res += userName+" 님, 이전 동일숫자입력!"+enterStr+enterStr;
				}else {
					
					int score = 0;
					int preview_score=0;
					switch(completeYn+1) {
						case 1:
							score =500;
							preview_score=300;
							break;
						case 2:
							score =300;
							preview_score=240;
							break;
						case 3:
							score =240;
							preview_score=120;
							break;
						case 4:
							score =120;
							preview_score=60;
							break;
						case 5:
							score =60;
							preview_score=20;
							break;
						case 6:
							score =20;
							preview_score=0;
							break;
					}
					
				
					
					if (targetNumber == in_number) {
					    res += enterStr+(completeYn + 1) + "회차 정답!" + enterStr;
					    res += "정답: " + in_number + "!" + enterStr;

					    map.put("endYn", "1");

					    HashMap<String, Object> newMap = new HashMap<>();
					    newMap.put("userName", map.get("userName"));
					    newMap.put("roomName", map.get("roomName"));
					    newMap.put("score", score);
					    newMap.put("cmd", "gamble_s2");

					    int newScore = botService.insertBotPointRankTx(newMap);

					    res += "정답포인트: " + score + "p 획득" + enterStr;
					    res += "갱신포인트: " + newScore + "p" + enterStr;

					} else {
					    res += (completeYn + 1) + "회차 실패!" + enterStr + enterStr;
					    
					    if (completeYn + 1 < 6) {
					    	int diff = Math.abs(targetNumber - in_number);
					        String arrow = targetNumber > in_number ? " ↑" : " ↓";
					        if (item_3_1 && diff >= 30) {
					            arrow += arrow.trim(); // 화살표 두 개
					        }
					        res += in_number + arrow + (targetNumber > in_number ? " UP" : " DOWN") + enterStr;
					        res += "다음에 맞추면 " + preview_score + "p 획득 가능" + enterStr;
					    }
					}
					
					// 최대 시도 도달 시 처리
					if (completeYn + 1 == 6) {
						if (targetNumber != in_number) {
							res += "정답은 " + targetNumber + "!!" + enterStr+ enterStr;
						    map.put("endYn", "1");

						    HashMap<String, Object> newMap = new HashMap<>();
						    newMap.put("userName", map.get("userName"));
						    newMap.put("roomName", map.get("roomName"));
						    newMap.put("score", 1);
						    newMap.put("cmd", "gamble_s2");

						    int newScore = botService.insertBotPointRankTx(newMap);

						    res += "참여포인트: 1p 획득" + enterStr;
						    res += "갱신포인트: " + newScore + "p" + enterStr;
						}
					}

					// 게임 진행 업데이트
					map.put("colName", "number" + (completeYn + 1));
					map.put("inNumber", in_number);
					map.put("seq", seq);
					botService.updateBotPointUpdownSTx(map);
				}
				
				
				res += enterStr + "진행이력:::" + enterStr;

				for (int i = 1; i <= 5; i++) {
				    Object value = info.get("NUMBER" + i);
				    if (value != null) {
				        int number = Integer.parseInt(value.toString());
				        int diff = Math.abs(targetNumber - number);
				        String direction = "";
				        if (targetNumber > number) {
				            direction = " ↑";
				        } else if (targetNumber < number) {
				            direction = " ↓";
				        }
				        if (item_3_1 && diff >= 30 && !direction.isEmpty()) {
				            direction += direction.trim(); // 화살표 두 개
				        }
				        res += i + "차시도 " + number + direction + enterStr;
				    }
				}

				// 현재 시도는 이미 비교했으므로 다시 계산
				String currentDirection = "";
				int diff = Math.abs(targetNumber - in_number);
				if (targetNumber > in_number) {
				    currentDirection = " ↑";
				} else if (targetNumber < in_number) {
				    currentDirection = " ↓";
				}
				if (item_3_1 && diff >= 30 && !currentDirection.isEmpty()) {
				    currentDirection += currentDirection.trim();
				}
				res += "현재시도 " + in_number + currentDirection + enterStr;
				
				if(breakFlag) {
					return res;
				}
				
				
			}catch(Exception e) {
				return userName+" 님, /뽑기 숫자 입력필요!!!";
			}
			return res;
			
		}
		
		
		
	}
	
	String baseball(HashMap<String,Object> map) {
		int defaultScore=0;
		int SuccessScore=10;
		map.put("cmd", "baseball_s");
		map.put("score", defaultScore);
		
		String userName = map.get("userName").toString();
		
		HashMap<String,Object> info = botService.selectBotPointBaseballIngChk(map);
		if(info == null || info.size() ==0) {
			
			if(map.get("param1")!=null && !map.get("param1").toString().equals("") ) {
				return userName+" 님, 신규 야구는 /야구 입력";
			}
			
			//신규대상인 경우 
			//List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			try {
				
				//int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
				/*
				if(score < defaultScore) {
					return userName+" 님, "+defaultScore+" p 이상만 가능합니다.";
				}
				map.put("score", -defaultScore);
				int new_score = botService.insertBotPointRankTx(map);
				*/
				String target = generateRandom3Digits();
				
				map.put("targetNumber", target);
				botService.insertBotPointBaseballSTx(map);
				
				return userName+" 님!"+enterStr+
						//score+"p → "+new_score+"p"+enterStr+
						"/야구 숫자3자리 입력하시면 야구게임 진행!"+enterStr+
						"방 인원 전체가 참여 가능!";
			}catch(Exception e) {
				return userName+" 님, 야구 오류!";
			}
		}else if(info.size() > 0 ){
			try {
				int ingCount = Integer.parseInt(info.get("ING").toString());
				if(ingCount == 0) {
					return userName+" 님, 진행 중인 야구 없음!";
				}
				if(ingCount >1) {
					return userName+" 님, 오류! 개발자호출필요";
				}
				
				//진행중인 seq
				int seq  = Integer.parseInt(info.get("SEQ").toString());
				String target = info.get("TARGET_NUMBER").toString();
				String guess = map.get("param1").toString();
				if (!guess.matches("\\d{3}") || !isAllDigitsUnique(guess)) {
				    return userName+" 님, 서로 다른 숫자 3자리를 입력!";
				}
	
	            int strike = 0, ball = 0;
	            for (int i = 0; i < 3; i++) {
	                if (target.charAt(i) == guess.charAt(i)) strike++;
	                else if (target.contains(String.valueOf(guess.charAt(i)))) ball++;
	            }
	
	            
	            String retCnt = strike + "S " + ball + "B";
	            String res="";
	            
            	boolean endYn = (strike == 3);
            	
                map.put("seq", seq);
                map.put("tryNumber", guess);
                map.put("retCnt", retCnt);
                map.put("endYn", endYn);
            	
				botService.insertBotPointBaseballIng(map);
				
				if(endYn) {
					HashMap<String, Object> newMap = new HashMap<>();
				    newMap.put("userName", map.get("userName"));
				    newMap.put("roomName", map.get("roomName"));
				    newMap.put("score", SuccessScore);
				    newMap.put("cmd", "baseball_e");
	
				    int newScore = botService.insertBotPointRankTx(newMap);
	
				    res += "정답포인트: "+SuccessScore+" p 획득" + enterStr;
				    res += "갱신포인트: " + newScore + "p" + enterStr;
				}
					
	            return userName+" 님,"+enterStr+
	            		guess+" 의 결과는..."+enterStr
	            		+retCnt+" !"+enterStr+enterStr
	            		+res;
			} 
            catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "오류!!";
			}
			
			
		}else {
			return "오류";
		}
		
		
		
	}
	
	public boolean isAllDigitsUnique(String number) {
	    if (number == null || number.length() != 3) return false;
	    Set<Character> digitSet = new HashSet<>();
	    for (char ch : number.toCharArray()) {
	        digitSet.add(ch);
	    }
	    return digitSet.size() == 3;
	}
	private String generateRandom3Digits() {
		List<Integer> digits = new ArrayList<>();
		while (digits.size() < 3) {
			int n = (int) (Math.random() * 10);
			if (!digits.contains(n))
				digits.add(n);
		}
		return digits.stream().map(String::valueOf).collect(Collectors.joining());
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
	
	String weaponInfo(HashMap<String,Object> map) {
		map.put("cmd", "weapon_upgrade2");
		String msg = map.get("userName")+" 님, "+enterStr;
		
		HashMap<String, Object> weaponInfo = getWeaponStats(map);
	    
	    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
		if(weaponLv < 30) {
			return msg+"30레벨 달성시 공개됩니다!!";
		}
		msg +="Thankyou for playing"+enterStr;
		List<HashMap<String,Object>> list = botService.selectBotPointWeaponPct(map);
		
		int i=0;
		for(HashMap<String,Object> hs : list) {
			msg += hs.get("LV")+" " +hs.get("PCT")+enterStr ;
			i++;
			
			if(i == 7) {
				msg+=allSeeStr;
			}
			
		}
		
		
		return msg;
	}
	String weapon(HashMap<String,Object> map) {
		map.put("cmd", "weapon_upgrade");
		map.put("score",0);
		
		String msg = map.get("userName")+" 님,"+enterStr;
		
		if(!dailyCheck(map)) {
			return map.get("userName")+" 님, 오늘의 강화 완료!"+enterStr 
					+ "/강화2 : 30p 소모해서 강화 1회 진행가능!"+ enterStr
					+ "/강화3 : 150p 소모해서 강화 5회 진행가능!";
		}
		msg += weapon_upgrade_logic(map,1,0);
		//msg += enterStr + map.get("extra_msg");
		return msg;
	}
	//추가강화
	String weapon2(HashMap<String,Object> map) {
		map.put("cmd", "weapon_upgrade2");
		String msg = map.get("userName")+" 님,"+enterStr;
		
		HashMap<String, Object> weaponInfo = getWeaponStats(map);
	    
	    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
		if(weaponLv >= 30) {
			return "포인트 소모 강화 MAX!!";
		}
		
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_8_1 = ableItemList.contains("8-1");
		boolean item_8_2 = ableItemList.contains("8-2");
		boolean item_8_3 = ableItemList.contains("8-3");
		boolean item_8_4 = ableItemList.contains("8-4");
		boolean item_8_5 = ableItemList.contains("8-5");
		
		int defaultScore = 30;
		
		
		if(item_8_1) {
			defaultScore -=2;
		} else if(item_8_2) {
			defaultScore -=4;
		} else if(item_8_3) {
			defaultScore -=6;
		} else if(item_8_4) {
			defaultScore -=8;
		} else if(item_8_5) {
			defaultScore -=10;
		}
		int calcScore = defaultScore *1;
		try {
			List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
			if(score < calcScore) {
				return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
			}
			map.put("score", -calcScore);
			int new_score = botService.insertBotPointRankTx(map);
			
			msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			
			if(item_8_1 || item_8_2 || item_8_3 ||item_8_4 ||item_8_5) {
				msg +="([테메르의 정] 강화 할인 적용)"+enterStr;
			}
			
		}catch(Exception e) {
			return "강화2 포인트조회 오류입니다.";
		}
		
		msg += weapon_upgrade_logic(map,1 ,defaultScore );
		
		return msg;
	}
	//추가강화
	String weapon3(HashMap<String,Object> map) {
		map.put("cmd", "weapon_upgrade2");
		String msg = map.get("userName")+" 님,"+enterStr;
		
		HashMap<String, Object> weaponInfo = getWeaponStats(map);
	    
	    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
		if(weaponLv >= 30) {
			return "포인트 소모 강화 MAX!!";
		}
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_8_1 = ableItemList.contains("8-1");
		boolean item_8_2 = ableItemList.contains("8-2");
		boolean item_8_3 = ableItemList.contains("8-3");
		boolean item_8_4 = ableItemList.contains("8-4");
		boolean item_8_5 = ableItemList.contains("8-5");
		
		int defaultScore = 30;
		if(item_8_1) {
			defaultScore -=2;
		} else if(item_8_2) {
			defaultScore -=4;
		} else if(item_8_3) {
			defaultScore -=6;
		} else if(item_8_4) {
			defaultScore -=8;
		} else if(item_8_5) {
			defaultScore -=10;
		}
		int calcScore = defaultScore *5;
		
		
		try {
			List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
			if(score < calcScore) {
				return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
			}
			map.put("score", -calcScore);
			int new_score = botService.insertBotPointRankTx(map);
			
			msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			
			if(item_8_1 || item_8_2 || item_8_3 ||item_8_4 ||item_8_5) {
				msg +="([테메르의 정] 강화 할인 적용)"+enterStr;
			}
			
		}catch(Exception e) {
			return "강화3 포인트조회 오류입니다.";
		}
		
		msg += weapon_upgrade_logic(map,5,defaultScore);
		
		return msg;
	}

	//리밋강화 ㄹㅁㄱㅎ ㄻㄱㅎ
	
	public String limit_upgrade(HashMap<String,Object> map) {
		map.put("cmd", "maximum_limit_upgrade");
		String msg = map.get("userName")+" 님,"+enterStr;
		int defaultScore = 1000;
		int calcScore = defaultScore ;
		
		String statName =map.get("stat").toString();
		
		
		try {
			List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
			if(score < calcScore) {
				return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
			}
			map.put("score", -calcScore);
			int new_score = botService.insertBotPointRankTx(map);
			
			msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			
			map.put("statName", statName);
			map.put("lv", 1);
			botService.insertBotPointStatUserTx(map);
			
			int lv = 0;
			List<HashMap<String,Object>> ls2 = botService.selectBotPointStatUserSum(map);
			for(HashMap<String,Object> hs2 : ls2) {
				if(hs2.get("STAT_NAME").toString().equals(statName)) {
					lv = Integer.parseInt(hs2.get("LV").toString());
				}
			}
			
			switch(statName) {
				case "LIMIT":
					msg += enterStr+"맥포강화 완료, " + enterStr+"Maximum get Point +"+lv;
					break;
				case "STR":
					msg += enterStr+"근력강화 완료, " + enterStr+"Str Point +"+lv;
					break;
				case "DEF":
					msg += enterStr+"방어강화 완료, " + enterStr+"Def Point +"+lv;
					break;
				case "CRI":
					msg += enterStr+"치명강화 완료, " + enterStr+"Cri Point +"+lv;
					break;
			}
			
			
		}catch(Exception e) {
			return "강화 포인트조회 오류입니다.";
		}
		return msg;
	}
	//악세구매 악세구입 
	public String acc_buy(HashMap<String,Object> map) {
		map.put("cmd", "acc_buy");
		String msg = map.get("userName")+" 님,"+enterStr;
		int defaultScore = 200;
		int calcScore = defaultScore ;
		
		
		
		try {
			int count = botService.selectPointItemUserCount(map);
			
			if(count < 50) {
				return  map.get("userName")+" 님,"+enterStr+"상자획득 누적 50개이상 구매가능!";
			}
			
			
			HashMap<String, Object> now = botService.selectBotPointAcc(map);
			if(now == null) {
				List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
				int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
				if(score < calcScore) {
					return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
				}
				map.put("score", -calcScore);
				int new_score = botService.insertBotPointRankTx(map);
				
				msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
				
				botService.insertBotPointAccTx(map);
				msg += enterStr+"악세구매 완료, " + enterStr+"/악세강화 : 50p";
				
			}else {
				return map.get("userName")+" 님, "+"이미 존재! "+enterStr+"/악세강화 : 200p";
			}
		}catch(Exception e) {
			return "악세구매 포인트조회 오류입니다.";
		}
		return msg;
	}
	
	//악세강화
	String acc_upgrade(HashMap<String,Object> map) {
		map.put("cmd", "acc_upgrade");
		String msg = map.get("userName")+" 님,"+enterStr;
		
		try {
			
			HashMap<String, Object> now = botService.selectBotPointAcc(map);
			if(now == null) {
				return map.get("userName")+" 님, "+"악세가 없습니다! "+enterStr+"/악세구매 : 200p";
			}else {
				int lv = Integer.parseInt(now.get("LV").toString()) ;
				int[] gold_data = MiniGameUtil.GOLD_MAP_ACC.getOrDefault(lv, new int[]{0}); 
				
				int defaultScore = gold_data[0];
				int calcScore = defaultScore ;
				
				switch (now.get("TRY").toString()) {
					case "TRY":
						List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
						int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
						if(score < calcScore) {
							return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
						}
						map.put("score", -calcScore);
						int new_score = botService.insertBotPointRankTx(map);
						msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
						msg += acc_upgrade_logic(map);
						
						break;
					case "MENT":
						
						HashMap<String, Object> result;
					    result = getSuccessRateAcc(lv);
					    HashMap<String, Object> result1;
					    result1 = getSuccessRateAcc(lv+1);
					    
					    
						msg += "현재 악세 "+ lv+" lv" +enterStr;
						msg +="최소공격력 +" +result.get("plus_min")+ ", ";
						msg +="최대공격력 +" +result.get("plus_max")+enterStr;
						msg +="치명타 +" +result.get("plus_crit")+"%, ";
						msg +="방어력 +" +result.get("plus_def")+enterStr+enterStr;
						
					    msg += "성공시 악세 "+ (lv+1)+" lv" +enterStr;
					    msg +="최소공격력 +" +result1.get("plus_min")+ ", ";
						msg +="최대공격력 +" +result1.get("plus_max")+enterStr;
						msg +="치명타 +" +result1.get("plus_crit")+"%, ";
						msg +="방어력 +" +result1.get("plus_def")+enterStr+enterStr;
					            
						
						msg += "( 2 Min ) 내로 '/악세강화' 입력 시 강화시도!"+enterStr;
						msg += "강화비용 : "+ defaultScore + " p" +enterStr+enterStr;
						msg += "확률::성공 / 실패 / 파괴" + enterStr;
					    msg += result.get("successRate")+"% / "+result.get("failRate")+"% / "+result.get("brokenRate")+"%"+enterStr+enterStr ;
					    
						botService.updateBotPointAccTryMentTx(map);
						
						break;
				}
			}
			
			
			
		}catch(Exception e) {
			return "악세강화 포인트조회 오류입니다.";
		}
		
		
		
		
		//msg += enterStr + map.get("extra_msg");
		return msg;
	}
	
	//악세강화 
	public String acc_upgrade_logic(HashMap<String,Object> map) {
		String msg="";
		
		HashMap<String, Object> now;
		int lv;
		
		try {
			now = botService.selectBotPointAcc(map);
			lv = Integer.parseInt(now.get("WEAPON_LV").toString());
			
		} catch (Exception e1) {
			return  map.get("userName")+" 님, 악세사리가 없습니다. "+enterStr+"/악세구매 : 200p";
		}
		
		
		try {
			HashMap<String, Object> result;
		    result = getSuccessRateAcc(lv);
		    String resultCode = result.get("isSuccess").toString();
		    String resultMsg = result.get("isMsg").toString();
		    
		    
		    msg = map.get("userName")+" 님,"+enterStr+" 악세 "+(lv+1)+" lv 강화 시도 결과.."+enterStr+resultMsg;
		    
		    
		    switch(resultCode) {
		    	case "OK":
		    		map.put("successYn", "OK");
		    		msg+="";
		    		
		    		HashMap<String, Object> result1 = getSuccessRateAcc(lv+1);
		    		msg +=enterStr+enterStr ;
		    		msg +="최소공격력 +" +result1.get("plus_min")+ ", ";
					msg +="최대공격력 +" +result1.get("plus_max")+enterStr;
					msg +="치명타 +" +result1.get("plus_crit")+"%, ";
					msg +="방어력 +" +result1.get("plus_def")+enterStr+enterStr;
					msg +=enterStr;
					map.put("weaponLv", (lv+1));
		    		break;
		    	case "FAIL":
		    		map.put("successYn", "FAIL");
		    		map.put("weaponLv", (lv));
		    		break;
		    	case "BROKEN":
		    		map.put("successYn", "BROKEN");
		    		map.put("weaponLv", (lv));
		    		break;
		    }
		   
			map.put("resultCode", resultCode);
			
		    
		    
			botService.updateBotPointAccTx(map);
		} catch (Exception e) {
			msg ="강화중 에러발생";
		}
		
		return msg;
	}
	
	//반지강화
	String hit_ring_upgrade(HashMap<String,Object> map) {
		map.put("cmd", "hit_ring_upgrade");
		String msg = map.get("userName")+" 님,"+enterStr;
		
		try {
			
			HashMap<String, Object> now = botService.selectBotPointHitRingTx(map);
			
			int lv = Integer.parseInt(now.get("LV").toString()) ;
			
			String price0to1_dateStr = now.get("PRICE0TO1_DATE").toString();
			String price1to2_dateStr = now.get("PRICE1TO2_DATE").toString();
			String price2to3_dateStr = now.get("PRICE2TO3_DATE").toString();
			String price3to4_dateStr = now.get("PRICE3TO4_DATE").toString();
			String price4to5_dateStr = now.get("PRICE4TO5_DATE").toString();
			String price5to6_dateStr = now.get("PRICE5TO6_DATE").toString();
			
			int calcScore = 500;
			int evadeCnt = 0;
			switch(lv) {
				case 0:
					//0 to 1 일때는 9.15~sysdate까지의 counting하여, 횟수 가져오기 
					map.put("calc_date", price0to1_dateStr);
					map.put("priceColName", "PRICE0TO1");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE0TO1_DATE");//성공시 업데이트할 컬럼
					break;
				case 1:
					//price0to1_dateStr 부터 현재까지의 
					map.put("calc_date", price0to1_dateStr);
					map.put("priceColName", "PRICE1TO2");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE1TO2_DATE");
					break;
				case 2:
					//price0to1_dateStr 부터 현재까지의 
					map.put("calc_date", price1to2_dateStr);
					map.put("priceColName", "PRICE2TO3");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE2TO3_DATE");
					break;
				case 3:
					map.put("calc_date", price2to3_dateStr);
					map.put("priceColName", "PRICE3TO4");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE3TO4_DATE");
					break;
				case 4:
					map.put("calc_date", price3to4_dateStr);
					map.put("priceColName", "PRICE4TO5");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE4TO5_DATE");
					break;
				case 5:
					map.put("calc_date", price4to5_dateStr);
					map.put("priceColName", "PRICE5TO6");//성공시 업데이트할 컬럼
					map.put("colName", "PRICE5TO6_DATE");
					break;
				case 6:
					return map.get("userName")+" 님, 반지강화 MAX!!";
			}
			
			evadeCnt = botService.selectBotPointHitRingEvadeCnt(map);
			calcScore -= evadeCnt*50; //회피횟수 * 30 만큼 할인
			
			if(calcScore<0) {
				calcScore=0;
			}
			
			map.put("lv", lv);
			map.put("calcScore",calcScore);
			
			switch (now.get("TRY").toString()) {
				case "TRY":
					List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
					int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
					if(score < calcScore) {
						return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
					}
					map.put("score", -calcScore);
					int new_score = botService.insertBotPointRankTx(map);
					msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
					//msg += hit_ring_upgrade_logic(map);
					
					botService.updateBotPointHitRingTx(map);
					msg +=(lv+1)+"lv 강화 성공!" + enterStr;
					msg +="명중률 : +"+(lv+1)+"%";
					break;
				case "MENT":
				    
					msg += "현재 반지 "+ lv+" lv" +enterStr;
					msg +="명중률 +" + lv    + "%" +enterStr+enterStr;
					msg +="강화시 +" +(lv+1) + "%" +enterStr+enterStr;
					
					msg += "( 2 Min ) 내로 '/반지강화' 입력 시 강화시도!"+enterStr;
					msg += "강화비용 : "+ calcScore + " p" +enterStr;
					
					msg +="(최초 500p,보스 회피횟수당 50p 할인!)"+enterStr;
					
					// 1. 문자열을 LocalDateTime으로 파싱
			        DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
			        LocalDateTime dateTime = LocalDateTime.parse(map.get("calc_date").toString(), inputFormat);

			        // 2. 원하는 출력 포맷 지정
			        DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("M월 d일 HH시 mm분");

			        // 3. 변환 후 출력
			        String formatted = dateTime.format(outputFormat);
					
					msg +="이전 강화 성공 시점인 " +  formatted + enterStr;
					msg +=" 이후 부터 계산.." + enterStr;
				    
					botService.updateBotPointHitRingTryMentTx(map);
					
					break;
			}
			
		}catch(Exception e) {
			return "반지강화 포인트조회 오류입니다.";
		}
		
		return msg;
	}
	
	public String weapon_upgrade_logic(HashMap<String,Object> map,int rate,int tryPirce) {
		String msg="";
		
		HashMap<String, Object> now;
		int lv;
		double failPct;
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_11_1 = ableItemList.contains("11-1");
		boolean item_11_2 = ableItemList.contains("11-2");
		
		try {
			//now는 현재값을 가져오고, 없을땐 생성해서 가져온다 
			now = botService.selectBotPointWeapon(map);
			lv = Integer.parseInt(now.get("WEAPON_LV").toString());
			failPct = Double.parseDouble(now.get("FAIL_PCT").toString());
			
			msg +=" "+ lv + "lv → "+(lv+1)+"lv 강화 시도"+enterStr+enterStr;
			if(rate>1) {
				msg+="("+rate+"배수)"+enterStr;
			}
			
		} catch (Exception e1) {
			return "강화 검색 오류";
		}
		
		
		try {
			if(failPct>=100) {
				map.put("successYn","1");
				map.put("failPct", 0);  // 확정성공 후 누적 초기화
				
				map.put("successYn", "1");
		        map.put("failPct", 0); // 성공 시 누적 실패율 초기화
		        map.put("addPct", 0); // 실패 시 누적 증가(로그테이블)
		        map.put("tryLv", lv+1); // 현재 레벨+1 (시도레벨)
		        map.put("weaponLv", lv+1); // 현재 레벨+1 (성공레벨)
		        
		        msg += (lv+1) +" 단계 강화 ⭐성공⭐!"+enterStr;	
		        msg += "성공률 : "+"100%"+enterStr;	
		        msg += "장인의기운이 초기화 되었습니다."+enterStr;
		        
		        if(rate>1 && tryPirce>0) {
		        	msg += rate+"회차 중 "+"1회차에 성공, 환급포인트 : "+tryPirce*(rate-1)+enterStr;
		        	map.put("score", tryPirce*(rate-1));
		        	map.put("cmd", "weapon_upgrade2");
		        	botService.insertBotPointRankTx(map);
		        }
		        
			}else {
				HashMap<String, Object> result;
				boolean isSuccess =false;
				boolean isMaxedOut = false; // 100% 달성 여부
				
			    double failAdd =0.0;
			    double successRate =0.0;
			    
			    int i=1;
				for( i=1 ; i <= rate ; i++) {
					if(tryPirce == 0 && item_11_1) {
						//무료강화이면서  item_11_1 있는경우
						result = getSuccessRate(lv,1.5);
					}else if(tryPirce == 0 && item_11_2) {
						//무료강화이면서  item_11_2 있는경우
						result = getSuccessRate(lv,2);
					}else {
						result = getSuccessRate(lv,1);
					}
					isSuccess = (boolean) result.get("isSuccess");
				    failAdd = (double) result.get("failAddPct");
				    successRate = (double) result.get("successRate");     // 현재 성공 확률
				    
				    double currentPct = failPct + failAdd * i;
				    if (currentPct >= 100) {
				    	if(tryPirce > 0)//중간 시도과정만, 1회에는 문구 잘나오도록 처리
				        isMaxedOut = true; // 장인의기운 100% 달성
				        break;
				    }
				    // i 번째 시도
					if(isSuccess) {
						break;
					}
				}
				
			    int refundCount = rate-i;     
			    int refundAmount = refundCount*tryPirce;
			    
			    if(refundCount > 1) {
			    	//중간성공시 .. 로직 
			    	 msg += rate + "회차 중 " + (rate - refundCount) + "회차에 " +
			    	           (isMaxedOut ? "장인의기운 100% 도달" : "성공") +
			    	           ", 환급포인트 : " + refundAmount + enterStr;
		        	map.put("score", refundAmount);
		        	map.put("cmd", "weapon_upgrade2");
		        	botService.insertBotPointRankTx(map);
			    }
			    
			    if (rate>0 && isMaxedOut) {
			        // 100% 도달 시 로직 (성공은 안 함)
			        double sumPct = 100.0;
			        map.put("successYn", "0");
			        map.put("failPct", sumPct);
			        map.put("addPct", Math.round(failAdd * i * 100) / 100.0);
			        map.put("tryLv", lv + 1);
			        map.put("weaponLv", lv);

			        msg += "현재 장인의기운: 100%" + enterStr;
			    }else if (isSuccess) {
			        map.put("successYn", "1");
			        map.put("failPct", 0); // 성공 시 누적 실패율 초기화
			        map.put("addPct", 0); // 실패 시 누적 증가(로그테이블)
			        map.put("tryLv", lv+1); // 현재 레벨+1 (시도레벨)
			        map.put("weaponLv", lv+1); // 현재 레벨+1 (성공레벨)
			        
			        msg += (lv+1) +" 단계 강화 ⭐성공⭐"+enterStr;	
			        msg += "성공률 : "+successRate+"%"+enterStr;	
			        if(tryPirce == 0 && item_11_1) {
						//무료강화이면서  item_11_1 있는경우
			        	 msg += "([황금모루] 보너스 성공률 적용)"+enterStr;	
					}else if(tryPirce == 0 && item_11_2) {
						//무료강화이면서  item_11_2 있는경우
						 msg += "([황금모루] 보너스 성공률 적용)"+enterStr;
					}
			       
			        msg += "장인의기운이 초기화 되었습니다."+enterStr+enterStr;			        
			        msg += "누적되었던 장인의기운 "+failPct+"%"+enterStr;
			    } else {
			    	double failAddRate = Math.round(failAdd * rate * 100) / 100.0;
			    	double sum = failPct + failAddRate;
			    	double sumPct = Math.round(sum * 100.0) / 100.0; 
			    	
			        map.put("successYn", "0");
			        map.put("failPct", sumPct); // 실패 시 누적 증가
			        map.put("addPct", failAddRate); // 실패 시 누적 증가(로그테이블)
			        map.put("tryLv", lv+1); // 현재 레벨+1 (시도레벨)
			        map.put("weaponLv", lv); // 현재 레벨+1 (실패레벨)
			        
			        
				    msg += (lv+1) + " 단계 강화 ☂실패☂"+enterStr;		
				    msg += "성공률 : "+Math.round(successRate * 100) / 100.0+"%"+enterStr;
				    if(tryPirce == 0 && item_11_1) {
						//무료강화이면서  item_11_1 있는경우
			        	 msg += "([황금모루] 보너스 성공률 적용)"+enterStr;	
					}else if(tryPirce == 0 && item_11_2) {
						//무료강화이면서  item_11_2 있는경우
						 msg += "([황금모루] 보너스 성공률 적용)"+enterStr;
					}
				    msg += "장인의기운 +" + Math.round(failAdd * rate* 100) / 100.0+"%"+enterStr;	
				    msg += "현재 장인의기운: "+sumPct+"%"+enterStr;
				    
			    }
			    
				
			}
			botService.upsertDailyWeaponUpgradeTx(map);
		} catch (Exception e) {
			msg ="강화중 에러발생";
		}
		
		return msg;
	}
	
	public HashMap<String,Object> getSuccessRate(int level,double bonusRate) {
	    Double[] data = MiniGameUtil.RATE_MAP_WEAPON.getOrDefault(level, new Double[]{0.0, 0.0});
	    
	    double successRate = data[0];
	    double failAddPct = data[1];
	    boolean isSuccess = false;
	    double roll = Math.random() * 100;
        isSuccess = roll < successRate; 
	    
	    HashMap<String, Object> result = new HashMap<>();
	    result.put("isSuccess", isSuccess);         // 성공 여부
	    result.put("successRate", successRate*bonusRate);     // 현재 성공 확률
	    result.put("failAddPct", failAddPct*bonusRate);       // 실패시 누적 증가량
	    return result;
	}
	public HashMap<String,Object> getSuccessRateAcc(int level) {
		Double[] data = MiniGameUtil.RATE_MAP_ACC.getOrDefault(level, new Double[]{0.0, 0.0, 0.0});
		int[] pow_data = MiniGameUtil.POW_MAP_ACC.getOrDefault(level, new int[]{0, 0, 0, 0}); 
		double successRate = data[0];
		double failRate = data[1];
		double brokenRate = data[2];
		int plus_crit = pow_data[0];
		int plus_min = pow_data[1];
		int plus_max = pow_data[2];
		int plus_def = pow_data[3];
		
		
		
		double roll = Math.random() * 100;
		
		String resultCode;
		String resultMsg;
		if (roll < successRate) {
			resultCode = "OK"; // 성공
			resultMsg = "성공했습니다!"; // 성공
		} else if (roll < successRate + failRate) {
			resultCode = "FAIL";    // 실패
			resultMsg = "실패했습니다!"; // 성공
		} else {
			resultCode = "BROKEN";  // 파괴
			resultMsg = "파괴되었습니다!"; // 성공
		}
		
		HashMap<String, Object> result = new HashMap<>();
	    result.put("isSuccess", resultCode);         // 성공 여부
	    result.put("isMsg", resultMsg); 
	    
	    result.put("successRate", successRate);   
	    result.put("failRate", failRate);         
	    result.put("brokenRate", brokenRate);       
	    
	    result.put("plus_crit", plus_crit);   
	    result.put("plus_min", plus_min);         
	    result.put("plus_max", plus_max);         
	    result.put("plus_def", plus_def);         
	    

		return result;
	}

	public String bossInfo(HashMap<String, Object> map) {
		HashMap<String, Object> boss;
		try {
			boss = botService.selectBotPointBoss(map);
			if (boss == null || boss.get("HP") == null) {
				return "보스가 존재하지 않습니다.";
			}

			// 보스 기본 정보
			int hp = Integer.parseInt(boss.get("HP").toString());
			int reward = Integer.parseInt(boss.get("REWARD").toString());
			int orgHp = Integer.parseInt(boss.get("ORG_HP").toString());
			int seq = Integer.parseInt(boss.get("SEQ").toString());
			String startTime = boss.get("START_DATE").toString();
			String startTime변환 = startTime;
			try {
				DateTimeFormatter parseFmt = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");
		        LocalDateTime dt = LocalDateTime.parse(startTime변환, parseFmt);

		        DateTimeFormatter outFmt = DateTimeFormatter.ofPattern("(a) h시 mm분", Locale.KOREAN);
		        startTime변환 = dt.format(outFmt);
			}catch (Exception e) {
				startTime변환 = startTime;
			}
			
			
			
			// 보스 스탯 (널이면 기본값)
			int bossAtkRate = boss.get("ATK_RATE") != null ? Integer.parseInt(boss.get("ATK_RATE").toString()) : 10;
			int bossAtkPower = boss.get("ATK_POWER") != null ? Integer.parseInt(boss.get("ATK_POWER").toString()) : 100;
			int bossDefRate = boss.get("DEF_RATE") != null ? Integer.parseInt(boss.get("DEF_RATE").toString()) : 10;
			int bossDefPower = boss.get("DEF_POWER") != null ? Integer.parseInt(boss.get("DEF_POWER").toString()) : 100;

			int critDefRate = boss.get("CRIT_DEF_RATE") != null ? Integer.parseInt(boss.get("CRIT_DEF_RATE").toString())
					: 0;
			int evadeRate = boss.get("EVADE_RATE") != null ? Integer.parseInt(boss.get("EVADE_RATE").toString()) : 0;
			String hideRule = boss.get("HIDE_RULE") != null ? boss.get("HIDE_RULE").toString() : "없음";

			// 출력 메시지 구성
			StringBuilder sb = new StringBuilder();
			sb.append("출현 보스 정보"+enterStr);
			//sb.append("보스 번호 : ").append(seq).append(enterStr);
			//sb.append("체력 : ").append(hp).append(" / ").append(orgHp).append(enterStr);
			sb.append("체력 : ").append("???").append(" / ").append("???").append(enterStr);
			sb.append("보상 : ").append(reward).append(" 포인트"+enterStr);
			//sb.append("(보상포인트 = 체력 / 20 ± 500 )").append(enterStr);
			sb.append("출현 시간 : ").append(startTime변환).append(enterStr+enterStr);

			sb.append("공격력 : 1~").append(bossAtkPower);
			sb.append("( 확률 : ").append(bossAtkRate).append("%)"+enterStr);
			sb.append("방어력 : 1~").append(bossDefPower);
			sb.append("( 확률 : ").append(bossDefRate).append("%)"+enterStr);
			sb.append("치명 저항 : ").append(critDefRate).append("%"+enterStr);
			sb.append("회피율 : ").append(evadeRate).append("%"+enterStr);
			
			String hideRuleMsg ="";
			switch(hideRule) {
				case "아침":
					hideRuleMsg="06시~10시 피해 30% 감소";
					break;
				case "점심":
					hideRuleMsg="10시~15시 피해 30% 감소";
					break;
				case "저녁":
					hideRuleMsg="15시~19시 피해 30% 감소";
					break;
				default:
					hideRuleMsg="02시~06시 피해 30% 감소";
					break;
			}
			sb.append("숨김 룰 : ").append(hideRuleMsg).append(enterStr);

			return sb.toString();

		} catch (Exception e) {
			return "보스 정보를 가져오는데 실패했습니다. (" + e.getMessage() + ")";
		}
	}
	
	public String attackBoss2(HashMap<String, Object> map) {
		 map.put("cmd", "boss_attack");

		    HashMap<String, Object> boss;
		    // ----------------
		    // 0. 보스 정보 조회
		    // ----------------
		    int hp, reward, org_hp, seq;
		    int bossAtkRate = 20, bossAtkPower = 20, bossDefRate = 20, bossDefPower = 20;
		    int critDefRate = 0;  // ★ 크리티컬 저항 확률 (%)
		    int evadeRate = 10;   // ★ 보스 회피 확률 (%)
		    String hideRule = "Normal"; 
		    String startTime;

		    int appliedDefPower=0 ;
		    int appliedAtkPower=0 ;
		    int appliedAtkPowerCalc=0 ;
		    int debuff = 0;
		    int debuff1 = 0;
		    int debuff2 = 0;
		    int drainRemain = 0;
		    try {
		        boss = botService.selectBotPointBoss(map);
		        if (boss == null || boss.get("HP") == null) return "";

		        hp = Integer.parseInt(boss.get("HP").toString());
		        reward = Integer.parseInt(boss.get("REWARD").toString());
		        org_hp = Integer.parseInt(boss.get("ORG_HP").toString());
		        seq = Integer.parseInt(boss.get("SEQ").toString());

		        // 보스 공격/방어 스탯0 적용
		        bossAtkRate  = boss.get("ATK_RATE")  != null ? Integer.parseInt(boss.get("ATK_RATE").toString())  : 10;
		        bossAtkPower = boss.get("ATK_POWER") != null ? Integer.parseInt(boss.get("ATK_POWER").toString()) : 100;
		        bossDefRate  = boss.get("DEF_RATE")  != null ? Integer.parseInt(boss.get("DEF_RATE").toString())  : 10;
		        bossDefPower = boss.get("DEF_POWER") != null ? Integer.parseInt(boss.get("DEF_POWER").toString()) : 100;
		        debuff       = boss.get("DEBUFF")    != null ? Integer.parseInt(boss.get("DEBUFF").toString()) : 0;
		        debuff1      = boss.get("DEBUFF1")   != null ? Integer.parseInt(boss.get("DEBUFF1").toString()) : 0;
		        debuff2      = boss.get("DEBUFF2")   != null ? Integer.parseInt(boss.get("DEBUFF2").toString()) : 0;
		        drainRemain      = boss.get("DRAIN_REMAIN")   != null ? Integer.parseInt(boss.get("DRAIN_REMAIN").toString()) : 0;
		        // ★ 추가 : 크리티컬 저항, 회피율, 숨김룰 적용
		        if (boss.get("CRIT_DEF_RATE") != null) critDefRate = Integer.parseInt(boss.get("CRIT_DEF_RATE").toString());
		        if (boss.get("EVADE_RATE") != null) evadeRate = Integer.parseInt(boss.get("EVADE_RATE").toString());
		        if (boss.get("HIDE_RULE") != null) hideRule = boss.get("HIDE_RULE").toString();

		        startTime = boss.get("START_DATE").toString();
		    } catch (Exception e) {
		        return "보스 정보를 가져오는데 실패했습니다.";
		    }

		    // ----------------
		    // 1. 보스 시작시간 체크
		    // ----------------
		    try {
		        LocalDateTime startDate = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
		        if (LocalDateTime.now().isBefore(startDate)) {
		            return "보스가 아직 등장하지 않았습니다!"+enterStr+"/보스정보 로 정보확인!";
		        }
		    } catch (Exception e) {
		    	e.printStackTrace();
		        // startDate 파싱 실패 시 무시 (공격 가능)
		    }
		    
	    // ----------------
	    // 아이템 조회
	    // ----------------
	    List<String> ableItemList = selectPointItemUserList(map);
	    boolean item_4_1 = ableItemList.contains("4-1");
	    boolean item_4_2 = ableItemList.contains("4-2");
	    boolean item_5_1 = ableItemList.contains("5-1");
	    boolean item_5_2 = ableItemList.contains("5-2");
	    boolean item_5_3 = ableItemList.contains("5-3");
	    boolean item_5_4 = ableItemList.contains("5-4");
	    
	    boolean item_6_1 = ableItemList.contains("6-1");
	    boolean item_7_1 = ableItemList.contains("7-1");
	    boolean item_7_2 = ableItemList.contains("7-2");
	    
	    
	    boolean item_9_1 = ableItemList.contains("9-1");
	    
	    boolean item_10_1 = ableItemList.contains("10-1");
	    boolean item_10_2 = ableItemList.contains("10-2");
	    boolean item_10_3 = ableItemList.contains("10-3");
	    boolean item_10_4 = ableItemList.contains("10-4");
	    boolean item_10_5 = ableItemList.contains("10-5");
	    boolean item_10_6 = ableItemList.contains("10-6");

	    boolean item_12_1 = ableItemList.contains("12-1");
	    boolean item_12_2 = ableItemList.contains("12-2");
	    boolean item_12_3 = ableItemList.contains("12-3");
	    boolean item_12_4 = ableItemList.contains("12-4");
	    boolean item_12_5 = ableItemList.contains("12-5");
	    boolean item_12_6 = ableItemList.contains("12-6");
	    
	    boolean item_13_1 = ableItemList.contains("13-1");
	    boolean item_13_2 = ableItemList.contains("13-2");
	    boolean item_13_3 = ableItemList.contains("13-3");
	    boolean item_13_4 = ableItemList.contains("13-4");
	    
	    boolean item_14_1 = ableItemList.contains("14-1");
	    boolean item_14_2 = ableItemList.contains("14-2");
	    boolean item_14_3 = ableItemList.contains("14-3");
	    boolean item_14_4 = ableItemList.contains("14-4");
	    
	    boolean item_17_1 = ableItemList.contains("17-1");
	    boolean item_17_2 = ableItemList.contains("17-2");
	    boolean item_17_3 = ableItemList.contains("17-3");
	    
	    boolean item_18_1 = ableItemList.contains("18-1");
	    boolean item_18_2 = ableItemList.contains("18-2");
	    
	    
	    
	    boolean item_19_1 = ableItemList.contains("19-1");
	    boolean item_20_1 = ableItemList.contains("20-1");
	    
	    int item_21_1_sum = botService.selectItem21LvSum();
	    item_21_1_sum = item_21_1_sum/2;
	    
	    //공격횟수아이템
	    boolean item_22_1 = ableItemList.contains("22-1");
	    boolean item_22_2 = ableItemList.contains("22-2");
	    boolean item_22_3 = ableItemList.contains("22-3");
	    //보스 방어 무시
	    boolean item_23_1 = ableItemList.contains("23-1");
	    boolean item_23_2 = ableItemList.contains("23-2");
	    boolean item_23_3 = ableItemList.contains("23-3");
	    
	    boolean item_99_1 = ableItemList.contains("99-1");	    
	    if(item_14_1) {
	    	map.put("item_14_1", "Y");
	    }
	    if(item_14_2) {
	    	map.put("item_14_2", "Y");
	    }
	    if(item_14_3) {
	    	map.put("item_14_3", "Y");
	    }
	    if(item_14_4) {
	    	map.put("item_14_4", "Y");
	    }
	    
	    
	    boolean item_15_1 = ableItemList.contains("15-1"); 
	    boolean item_15_2 = ableItemList.contains("15-2"); 
	    boolean item_16_1 = ableItemList.contains("16-1"); //징수의총
	    
	    
	    HashMap<String, Object> weaponInfo = getWeaponStats(map);
	    
	    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
	    Double weaponCriticalChance = Double.parseDouble(weaponInfo.get("criticalChance").toString());
	    int weaponMin = Integer.parseInt(weaponInfo.get("min").toString());
	    int weaponMax = Integer.parseInt(weaponInfo.get("max").toString());
	    int def = Integer.parseInt(weaponInfo.get("def").toString());
	    int hit = Integer.parseInt(weaponInfo.get("hit").toString());

	    int player_deffence = def;
	    if (item_13_1) {
	    	player_deffence += 3;
	    }
	    if (item_13_2) {
	    	player_deffence += 6;
	    }
	    if (item_13_3) {
	    	player_deffence += 9;
	    }
	    if (item_13_4) {
	    	player_deffence += 12;
	    }
	    
	    int player_hit = hit;
	    evadeRate -= player_hit;
	    
	    double roll = Math.random();
	    boolean flag_boss_attack = Math.random() < bossAtkRate / 100.0;
	    boolean flag_boss_evade = Math.random() < evadeRate / 100.0;
	    boolean flag_boss_defence = Math.random() < bossDefRate / 100.0;
	    boolean flag_boss_debuff = debuff > 0; //천벌 적용상태 
	    boolean flag_boss_debuff1 = debuff1 > 0;
	    boolean flag_boss_debuff2 = debuff2 > 0;
	    boolean flag_boss_drain_remain = drainRemain > 0;
	    
	    
	    boolean flag_player_double_attack = Math.random() < 0.03; //3%
	    
	    //boolean flag_boss_hide_able = debuff == 0;
	    
	    boolean flag_boss_drain = Math.random() < 0.01;//1%확률
	    boolean flag_boss_special = Math.random() < 0.01;//1%확률
	    boolean flag_new_point = Math.random() < 0.01;//1%확률
	    // ----------------
	    // 보스 숨김 체크
	    // ----------------
	    
	    LocalTime now = LocalTime.now();
	    LocalTime start = LocalTime.of(2, 0);
	    LocalTime end = LocalTime.of(6, 0);
	    map.put("night_attack_ok", "N");
	    
	    if(!flag_boss_debuff) {
	    	switch (hideRule) {
			case "아침":
				start = LocalTime.of(6, 0);
				end = LocalTime.of(10, 0);
				if (!now.isBefore(start) && now.isBefore(end)) {
					if(item_6_1) {
						map.put("night_attack_ok","Y");
					}else {
						map.put("night_attack_ok","Y2");
						map.put("night_attack_y2_msg","보스가 안개에 숨었습니다...피해 30% 감소..(06시~10시)");
						//return "보스가 안개에 숨었습니다...공격불가..(06시~10시 불가시간)";
					}
				}
				break;
			case "점심":
				start = LocalTime.of(10, 0);
				end = LocalTime.of(15, 0);
				if (!now.isBefore(start) && now.isBefore(end)) {
					if(item_6_1) {
						map.put("night_attack_ok","Y");
					}else {
						map.put("night_attack_ok","Y2");
						map.put("night_attack_y2_msg","보스가 구름에 숨었습니다...피해 30% 감소..(10시~15시)");
						//return "보스가 구름에 숨었습니다...공격불가..(10시~15시 불가시간)";
					}
				}
				break;
			case "저녁":
				start = LocalTime.of(15, 0);
				end = LocalTime.of(19, 0);
				if (!now.isBefore(start) && now.isBefore(end)) {
					if(item_6_1) {
						map.put("night_attack_ok","Y");
					}else {
						map.put("night_attack_ok","Y2");
						map.put("night_attack_y2_msg","보스가 퇴근길에 숨었습니다...피해 30% 감소..(15시~19시)");
						//return "보스가 퇴근길에 숨었습니다...공격불가..(15시~19시 불가시간)";
					}
				}
				break;
			default:
				start = LocalTime.of(2, 0);
				end = LocalTime.of(6, 0);
				if (!now.isBefore(start) && now.isBefore(end)) {
					if(item_6_1) {
						map.put("night_attack_ok","Y");
					}else {
						map.put("night_attack_ok","Y2");
						map.put("night_attack_y2_msg","보스가 어둠에 숨었습니다...피해 30% 감소..(02시~06시)");
						//return "보스가 어둠에 숨었습니다...공격불가..(02시~06시 불가시간)";
					}
				}
				break;
			}
	    } else {
	    	//hideRule 미적용처리..
	    	bossAtkRate = 0;
	    	bossDefRate = 0;
	    	evadeRate = 0;
	    	
	    	flag_boss_attack = false;
	    	flag_boss_evade = false;
	    	flag_boss_defence = false;
	    	//flag_boss = false;
	    	
	    }
	    
	    
	    
	    String checkCount = botService.selectHourCheckCount(map);
	    int checkCountInt = Integer.parseInt(checkCount);
	    int defaultCheckCount =40;
	    
	    if(item_22_1) {
	    	defaultCheckCount +=2;
	    }
	    if(item_22_2) {
	    	defaultCheckCount +=4;
	    }
	    if(item_22_3) {
	    	defaultCheckCount +=6;
	    }
	    
	    if(checkCountInt >= defaultCheckCount) {
	    	return map.get("userName") + "님," + enterStr + "일일공격횟수 끝!";
	    }
	    String countingMsg = "";
	    checkCountInt +=1;
	    countingMsg += "일일공격횟수: "+checkCountInt+" / "+defaultCheckCount;
	    // ----------------
	    // 공격 쿨타임 체크
	    // ----------------
	    if (!checkBossCooldown(map)) {
	        return map.get("userName") + "님," + enterStr + map.get("extra_msg");
	    }

	    Random rand = new Random();
	    
	    boolean heavensPunishment = false;
	    String punishMsg ="";
	    String debuff1Msg ="";
	    boolean debuff1_start =false;
	    if(item_9_1) {
	    	if(debuff ==0) {
	    		int rn0 = rand.nextInt(100);
		    	if (rn0 < 5) { // 0~99 (5%)
		    		heavensPunishment = true;
		    		punishMsg = " [천벌] 효과! 숨은보스가 모습을 드러냅니다!" + enterStr + "보스회피,보스공격,보스방어를 무시하고 초강력치명타 피해를 줍니다!"+enterStr;
		        }
	    	}
	    }
	    
	    if(item_20_1) {
	    	//if(debuff1 ==0) {
	    		int rn0 = rand.nextInt(100);
		    	if (rn0 < 5) { // 0~99 중 0~10일 때 발동(12%)
		    		debuff1_start = true;
		    		debuff1Msg = " [저주받은인형]보스에게 디버프부여!" + "보스의 치명저항이 감소됩니다!"+enterStr;
		        }
	    	//}
	    }

	    
	    if(item_10_1) {
	    	weaponMax +=2;
	    }
	    if(item_10_2) {
	    	weaponMax +=4;
	    }
	    if(item_10_3) {
	    	weaponMax +=6;
	    }
	    if(item_10_4) {
	    	weaponMax +=8;
	    }
	    if(item_10_5) {
	    	weaponMax +=10;
	    }
	    if(item_10_6) {
	    	weaponMax +=12;
	    }
	    if(item_21_1_sum > 0) {
	    	weaponMax += item_21_1_sum;
		}
	    
	    
	    if(item_12_1) {
	    	weaponMin +=1;
	    }
	    if(item_12_2) {
	    	weaponMin +=2;
	    }
	    if(item_12_3) {
	    	weaponMin +=3;
	    }
	    if(item_12_4) {
	    	weaponMin +=4;
	    }
	    if(item_12_5) {
	    	weaponMin +=5;
	    }
	    if(item_12_6) {
	    	weaponMin +=5;
	    }
	    
	    int weaponBaseDmg =  rand.nextInt(weaponMax - weaponMin + 1) + weaponMin;
	    String weaponBaseDmgMsg = "("+weaponMin+"~"+weaponMax+") ";
	 // ----------------
	    // 5. 회피 계산
	    // ----------------
	    boolean isEvade = false;
	    String isEvadeMsg = "";

	    boolean chaserCrit1 = false;
	    boolean chaserCrit2 = false;
	    boolean chaserCrit3 = false;
	    if(!heavensPunishment) {
	    //천벌이 아닐때만 계산 
	    	if (flag_boss_evade) {  // 보스가 회피 성공
		    //if (roll < (evadeRate / 100.0)) {  // 보스가 회피 성공
		        isEvade = true;
		        isEvadeMsg = "보스가 회피합니다." + enterStr;
		        double effectiveEvadeRate = evadeRate;
		        
		        if (item_17_1) {
		        	if (Math.random() < 0.12) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자] 보스가 피했지만 공격합니다!" + enterStr;
		                chaserCrit1=true;
		            } 
		        }
		        if (item_17_2) {
		        	if (Math.random() < 0.20) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자2]보스가 피했지만 공격합니다!" + enterStr;
		                chaserCrit1=true;
		            } else if (Math.random() < 0.24) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자2]보스가 피했지만 강하게 공격합니다!" + enterStr;
		                chaserCrit2=true;
		            } 
		        }
		        if (item_17_3) {
		        	if (Math.random() < 0.28) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자3]보스가 피했지만 공격합니다!" + enterStr;
		                chaserCrit1=true;
		            } else if (Math.random() < 0.34) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자3]보스가 피했지만 강하게 공격합니다!" + enterStr;
		                chaserCrit2=true;
		            } else if (Math.random() < 0.36) { 
		                isEvade = false;
		                isEvadeMsg += "[화려한추격자3]보스가 피했지만 더강하게 공격합니다!" + enterStr;
		                chaserCrit3=true;
		            }
		        }
		        
		        
		        if(!(chaserCrit1||chaserCrit2||chaserCrit3)) {
		        	
		        	
		        	 // 아이템 7-1, 7-2 가 있으면 보스 회피 무효화 시도
			        if (item_7_1) {
			        	effectiveEvadeRate = Math.max(evadeRate - 6, 0); // 최소 0
			        	if (roll < (effectiveEvadeRate / 100.0)) { //회피가 10일때 -5시켜 , 0~5
			        		//isEvadeMsg += "[덫] 실패, 보스 회피!!" + enterStr;
			        	}else {//5~100
			        		isEvade = false;
			                isEvadeMsg += "[덫] 보스 회피 무효!" + enterStr;
			        	}
			           
			        } else if (item_7_2) {
			        	effectiveEvadeRate = Math.max(evadeRate - 12, 0); // 최소 0
			        	if (roll < (effectiveEvadeRate / 100.0)) { //회피가 11일때 -10시켜 , 0~1
			        		//isEvadeMsg += "[덫2]실패, 보스 회피!!" + enterStr;
			        	}else {//1~100
			        		isEvade = false;
			                isEvadeMsg += "[덫2]보스 회피 무효!" + enterStr;
			        	}
			        }
		        }
		        
		       
		    }
	    }

	    // ----------------
	    // 야간투시경 적용
	    //  ---------------
	    String nightMsg = "";
	    if ("Y".equals(map.get("night_attack_ok"))) {
	        nightMsg = "";
	        
	    }
	    if ("Y2".equals(map.get("night_attack_ok"))) {
	        nightMsg = map.get("night_attack_y2_msg") + enterStr;
	        
	    }

		// ----------------
		// 플레이어 공격 계산
		// ----------------
		int damage = 0;
		boolean isCritical = false;
		boolean isSuperCritical = false;
		String bossDefenseMsg = "";
		String dmgMsg = "";
		map.put("evadeYn", isEvade);
		String scoutMsg="";

		double criticalChance = 0.0; // ★ 위쪽에서 치명타 확률 계산 후 메시지에서 사용
		StringBuilder critLog = new StringBuilder(); // ★ 치명타 로그 추가

		if (!isEvade) {
			// 무기 레벨 기반 기본 데미지
			int baseDamage = weaponBaseDmg;

			// ----------------
			// 치명타 확률 계산 (새 방식)
			// ----------------
			criticalChance = weaponCriticalChance;

			int baseCritPercent = (int) (criticalChance * 100);
			int totalCritPercent = baseCritPercent;
			List<String> critParts = new ArrayList<>();

			if (weaponLv > 0) {
				critParts.add("플레이어강화(" + baseCritPercent + "%)");
			}

			if (item_5_1) {
				totalCritPercent += 3;
				critParts.add("+ [예리한칼날](3%)");
			}
			if (item_5_2) {
				totalCritPercent += 6;
				critParts.add("+ [예리한칼날2](6%)");
			}
			if (item_5_3) {
				totalCritPercent += 9;
				critParts.add("+ [예리한칼날3](9%)");
			}
			if (item_5_4) {
				totalCritPercent += 12;
				critParts.add("+ [예리한칼날4](12%)");
			}
			if ((hp * 100.0) / org_hp < 10) {
				if (item_4_1) {
					totalCritPercent += 5;
					critParts.add("+ [스카우터](5%)");
					//scoutMsg += "약점노출시켰습니다"+enterStr;
				}
				if (item_4_2) {
					totalCritPercent += 10;
					critParts.add("+ [스카우터2](10%)");
					//scoutMsg += "약점노출시켰습니다"+enterStr;
				}
			}

			if (critDefRate > 0) {
				totalCritPercent -= critDefRate;
				critParts.add("+ 보스저항(-" + critDefRate + "%)");
			}
			
			if( debuff1 > 0 || debuff1_start) {
				totalCritPercent += 5;
				critParts.add("+ 보스저항감소(5%)");
			}
			
			if(item_99_1) {
				totalCritPercent += 70;
				critParts.add("+ 테스트모드(70%)");
			}
			
			if(item_21_1_sum > 0) {
				totalCritPercent += item_21_1_sum;
				critParts.add("+ 원기옥(" + item_21_1_sum + "%)");
			}
			
			/*
			if (totalCritPercent < 0)
				totalCritPercent = 0;
			 */
			critLog.setLength(0);
			if (totalCritPercent > 0) {
				critLog.append(enterStr+"▶ 치명타확률 : ").append(totalCritPercent).append("%").append(enterStr);
				if (!critParts.isEmpty()) {
					critLog.append(String.join(" ", critParts)).append(enterStr);
				}
			}
			
			// 치명타 발동 여부
			isCritical = Math.random() < (totalCritPercent / 100.0);
			if (isCritical) {
				isSuperCritical = Math.random() < 0.10;
			}
			
			//야간투시경이 없을때 item_6_1
			if ("Y2".equals(map.get("night_attack_ok"))) {
				isCritical = false;
				isSuperCritical = false;
			}

			if (chaserCrit1||chaserCrit2||chaserCrit3) {
				if (chaserCrit1) {
					isCritical = false;
					isSuperCritical = false;
				}
				if (chaserCrit2) {
					isCritical = true;
					isSuperCritical = false;
				}
				if (chaserCrit3) {
					isCritical = true;
					isSuperCritical = true;
				}
			}
			
			if(heavensPunishment) {
				isCritical =true;
				isSuperCritical =true;
			}

			
			dmgMsg ="";
			
			if (isSuperCritical) {
				damage = baseDamage * 5;
				if(item_18_1) {
					damage += baseDamage/2; //5.5배
			    }
				if(item_18_2) {
					damage += baseDamage; //6배
				}
				
				dmgMsg += "[✨초강력 치명타!!] "+weaponBaseDmgMsg + baseDamage + " → " + damage;
			} else if (isCritical) {
				damage = baseDamage * 3;
				dmgMsg += "[✨치명타!] "+weaponBaseDmgMsg + baseDamage + " → " + damage;
			}  else {
				damage = baseDamage;
				dmgMsg += ""+weaponBaseDmgMsg + baseDamage + " 로 공격!";
			}
			
			if ("Y2".equals(map.get("night_attack_ok"))){
				damage = damage * 7 / 10;
				dmgMsg += " → " + damage;
			}
			
			if(flag_player_double_attack) {
		    	if(item_15_1) {
		    		damage = damage * 3 / 2;
		    		dmgMsg += " → " + damage+"[더블어택]";
		    	}
		    	if(item_15_2) {
		    		damage = damage * 2;
		    		dmgMsg += " → " + damage+"[더블어택2]";
		    	}
		    	
		    }
			
			
			if (flag_boss_debuff) {
				map.put("useDebuff", 1);
				punishMsg = "[천벌디버프](+" + damage + "),"+(debuff-1)+"회 적용가능" + enterStr;
				damage = damage * 2;
			}
			if (flag_boss_debuff1) {
				if(!debuff1_start) {
					map.put("useDebuff1", 1);
				}
				debuff1Msg = "[저주받은인형디버프](보스저항-5%)"+(debuff1-1)+"회 적용가능" + enterStr;
				//punishMsg = "[천벌디버프](+" + damage + "),"+(debuff-1)+"회 적용가능" + enterStr;
				//damage = damage * 2;
			}
			if (flag_boss_debuff2) {
				map.put("useDebuff2", 1);
				//punishMsg = "[천벌디버프](+" + damage + "),"+(debuff-1)+"회 적용가능" + enterStr;
				//damage = damage * 2;
			}

			// 보스 방어 적용 (메시지 추가)
			if (flag_boss_defence) {
				 
				appliedDefPower = ThreadLocalRandom.current().nextInt(1, bossDefPower + 1);
				int appliedDefPowerNew=appliedDefPower;
				bossDefenseMsg = "보스가 방어하였습니다! 데미지 " + appliedDefPower + " 상쇄!" + enterStr;
				
				if(item_23_1) {
					appliedDefPowerNew -= 2;
					bossDefenseMsg+="[스트립아머]방어도 무시 효과.." +appliedDefPower;
				}
				if(item_23_2) {
					appliedDefPowerNew -= 4;
					bossDefenseMsg+="[스트립아머]방어도 무시 효과.." +appliedDefPower;
				}
				if(item_23_3) {
					appliedDefPowerNew -= 6;
					bossDefenseMsg+="[스트립아머]방어도 무시 효과.." +appliedDefPower;
				}
				if(appliedDefPowerNew <0) {
					appliedDefPowerNew = 0;
				}
			
				
				
				damage -= appliedDefPowerNew;
				if (damage < 0)
					damage = 0;
				if(item_23_1 || item_23_2 || item_23_3) {
					bossDefenseMsg+="→ " +appliedDefPowerNew+enterStr;
				}
				
				
			}
			
			if (item_19_1) {

			}

		}
	    
	    // ----------------
	    // 보스 HP/스코어/리워드 처리
	    // ----------------
	    int score = damage / 4;
	    
	    int sum_score = Integer.parseInt(weaponInfo.get("sum_score").toString());
	    
	    boolean newbieYn = sum_score < 6000 ;
	    if (newbieYn) score += 10;

	    boolean isKill = false;
	    int newHp = hp - damage;
	    String rewardMsg = "";
	    if (newHp <= 0) {
	    	if (item_16_1) {
	    		isKill = true;
	            score = Math.min(damage, hp) / 4 + 100+drainRemain;
	            map.put("reward", reward);
	            map.put("org_hp", org_hp);
	            rewardMsg = calcBossReward2(map);
	            respawnBoss(map);
	            
	            appliedAtkPower=0;
	            appliedDefPower=0;
	            bossDefenseMsg="";
	            
	            if(flag_boss_drain_remain) {
	            	map.put("extra_msg", enterStr+"보스가 흡혈했던 포인트 추가획득 : "+drainRemain+enterStr);
	            }
	            
	    	}else if (isCritical) {
	            isKill = true;
	            score = Math.min(damage, hp) / 4 + 100+drainRemain;
	            map.put("reward", reward);
	            map.put("org_hp", org_hp);
	            rewardMsg = calcBossReward2(map);
	            respawnBoss(map);
	            
	            appliedAtkPower=0;
	            appliedDefPower=0;
	            bossDefenseMsg="";
	            
	            if(flag_boss_drain_remain) {
	            	map.put("extra_msg", enterStr+"보스가 흡혈했던 포인트 추가획득 : "+drainRemain+enterStr);
	            }
	        } else {
	            newHp = 1;
	            int allowedDamage = hp - 1;
	            score = Math.min(damage, allowedDamage) / 4;
	            damage = allowedDamage;
	            
	            //보스 무적이라 메시지 필요없음 
	            dmgMsg="";
	            bossDefenseMsg="";
	        }
	    }
	    
	    String bossAttackMsg ="";
		if (!isKill) {
			if (heavensPunishment) {

			} else {//천벌이 아닐때만 계산
				if (flag_boss_attack) {
					appliedAtkPower = ThreadLocalRandom.current().nextInt(1, bossAtkPower + 1);
					appliedAtkPowerCalc = appliedAtkPower;
					bossAttackMsg = "▶ 보스의 반격! " + appliedAtkPowerCalc + " 의 데미지!!";
					if (player_deffence > 0) {
						bossAttackMsg += enterStr +"(플레이어 방어)"+ "-" + appliedAtkPower + " → ";
						appliedAtkPowerCalc -= player_deffence;
						if (appliedAtkPowerCalc < 0) {
							appliedAtkPowerCalc = 0;
						}
						// item13Msg+="-"+appliedAtkPowerCalc;
					}

					score -= appliedAtkPowerCalc;
					bossAttackMsg += "-" + appliedAtkPowerCalc;

					if (newbieYn) {
						if (appliedAtkPowerCalc > 0) {
							score += appliedAtkPowerCalc;
							bossAttackMsg += enterStr+"(초보자) " + appliedAtkPowerCalc + " 회복";
						}
					}

					if (item_19_1) {
						if (Math.random() <= 0.50) { // 50%확률
							score += appliedAtkPowerCalc;
							bossAttackMsg += enterStr + "[성스러운방어막,거울의힘], " + appliedAtkPowerCalc + " 피해회복,데미지반사";
							// bossAttackMsg+=enterStr+appliedAtkPower+" 데미지반사" ;

							damage += appliedAtkPower;
							score += appliedAtkPower / 4;
							dmgMsg += enterStr + "[성스러운방어막,거울의힘] +데미지 " + appliedAtkPower;

							newHp = hp - damage;
							if (newHp <= 0) {
								newHp = 1;
								int allowedDamage = hp - 1;
								score = Math.min(damage, allowedDamage) / 4;
								damage = allowedDamage;

								// 보스 무적이라 메시지 필요없음
								dmgMsg = "";
								bossDefenseMsg = "";
							}

						}
					}
					map.put("extra_msg", bossAttackMsg);

				}else if(flag_boss_drain) {
					appliedAtkPower = ThreadLocalRandom.current().nextInt(10, 30);
					appliedAtkPowerCalc = appliedAtkPower;
					
					bossAttackMsg = "▶ {보스의 흡혈} 사용!" + appliedAtkPowerCalc + " 의 흡혈!!";
					bossAttackMsg += enterStr +"누적흡혈량: " + (drainRemain+appliedAtkPowerCalc)+"(처치시 처치자 획득)";
					
					score -= appliedAtkPowerCalc;

					if (newbieYn) {
						if (appliedAtkPowerCalc > 0) {
							score += appliedAtkPowerCalc;
							bossAttackMsg += enterStr+"(초보자) " + appliedAtkPowerCalc + " 회복";
						}
					}
					
					map.put("drainRemain", appliedAtkPowerCalc);
					map.put("useDrain", 1);
					map.put("extra_msg", bossAttackMsg);
					
				}else if(flag_boss_special) {
					appliedAtkPower = ThreadLocalRandom.current().nextInt(100, 200);
					appliedAtkPowerCalc = appliedAtkPower;
					
					bossAttackMsg = "▶ {보스의 필살기} 사용!! " + appliedAtkPowerCalc + " 의 피해..!!"
							+enterStr+"너무큰피해에..상자를 받았습니다.";
					
					score -= appliedAtkPowerCalc;
					bossAttackMsg += "-" + appliedAtkPowerCalc;

					if (newbieYn) {
						if (appliedAtkPowerCalc > 0) {
							score += appliedAtkPowerCalc;
							bossAttackMsg += enterStr+"(초보자) " + appliedAtkPowerCalc + " 회복";
						}
					}
					
					map.put("useSpecial", 1);
					map.put("extra_msg", bossAttackMsg);
					
					/*
					if(flag_new_point) {
						
					}*/
					try {
						botService.insertPointNewBoxOpenTx(map);
					} catch (Exception e) {
						System.out.println("오류");
					}
				}
			}

		}
	    
	    // DB 반영
	    int new_score;
	    try {
	        map.put("hp", hp);
	        map.put("newHp", newHp);
	        map.put("seq", seq);
	        map.put("damage", damage);
	        
	        if(!isKill) {
	        	
	        	int limit_lv = 0;
				List<HashMap<String,Object>> ls2 = botService.selectBotPointStatUserSum(map);
				for(HashMap<String,Object> hs2 : ls2) {
					if(hs2.get("STAT_NAME").toString().equals("LIMIT")) {
						limit_lv = Integer.parseInt(hs2.get("LV").toString());
					}
				}
	        	
	        	
	        	if(score > 150) {
		        	score =150 +limit_lv;
		        }
	        }
	        
	        if(flag_boss_attack || flag_boss_drain || flag_boss_special) {
	        	
	        }else {
	        	
	        	if(score < 10) {
	        		score = 10;
	        	}
	        }
	        
	        
	        map.put("score", score);
	        map.put("endYn", isKill ? "1" : "0");
	        map.put("atkPower", appliedAtkPower);
	        map.put("defPower", appliedDefPower );
	        
	        //heavensPunishment
	        if(heavensPunishment) {
	        	map.put("heavensPunishment", 1);
	        }
	        
	        if(debuff1_start) {
	        	map.put("debuff1_start", 1);
	        }
	        botService.updateBotPointBossTx(map);
	        new_score = botService.insertBotPointRankTx(map);
	    } catch (Exception e) {
	        return "오류발생";
	    }

		// ----------------
		// 8. 메시지 생성 (카테고리 정리)
		// ----------------
		StringBuilder msg = new StringBuilder();

		// 1. 공격 결과
		msg.append(map.get("userName")).append("님이 보스를 공격했습니다!").append(enterStr);
		
		if (!isEvade) {
		    // 1. 먼저 입힌 데미지 표시
		    msg.append("▶ 입힌 데미지: ").append(damage).append(enterStr);
		    // 2. 데미지 상세 로그 (치명타, 방어 등 포함)
		    msg.append(dmgMsg).append(enterStr);
		    if (!punishMsg.isEmpty())
				msg.append(punishMsg);
		    if (!debuff1Msg.isEmpty())
		    	msg.append(debuff1Msg);
		    if (!bossDefenseMsg.isEmpty())
		        msg.append(bossDefenseMsg);
		    if (!scoutMsg.isEmpty())
		        msg.append(scoutMsg);
		} else {
		    msg.append("보스가 공격을 회피! 데미지 0!").append(enterStr);
		}
		if (item_7_1 || item_7_2)
			msg.append(isEvadeMsg);
		
		msg.append(nightMsg);
		
		// 치명타 확률 & 결과 메시지
		msg.append(critLog.toString());

		// 3. 보스 반격
		if (map.get("extra_msg") != null) {
			msg.append(map.get("extra_msg")).append(enterStr);
		}
		// 4. 보스 상태
		msg.append(enterStr);
		if (newHp == 1 && !isKill) {
			msg.append("✨보스는 체력 1! 치명타로 최후의 일격 필요!").append(enterStr);
		} else if (isKill) {
			msg.append("✨보스를 처치했습니다!").append(enterStr);
		} else {
			if ((newHp * 100.0) / org_hp < 10) {
		        msg.append("보스 체력: ").append(newHp).append("/").append(org_hp).append(enterStr);
		    }
			else {
				if (item_4_1)
					msg.append("보스 체력: ").append((int) ((newHp * 100.0) / org_hp)).append("% [스카우터]").append(enterStr);
				else if (item_4_2)
					msg.append("보스 체력: ").append(newHp).append("/??? (").append((int) ((newHp * 100.0) / org_hp)).append("%) [스카우터2]").append(enterStr);
				else
					msg.append("보스 체력: ???/???").append(enterStr);
			}
			
		}
		msg.append("공격 쿨타임 : ").append(map.get("timeDelay")).append(" Min ").append(map.get("timeDelayMsg")).append(enterStr);

		// 5. 포인트 및 보상
		msg.append("▶ 획득 포인트: ").append(score);
		if(score ==150) {
			msg.append("(MAX)");
		}
		
		if (newbieYn)
			msg.append(" (초보자 +10p)");
		if (score ==10)
			msg.append(" (최소p 보정)");
		//msg.append("기본공격포인트 "+damage+"÷3");
		msg.append(enterStr).append("갱신포인트 : ").append(new_score).append(enterStr);
		
		if (!rewardMsg.isEmpty())
			msg.append(anotherMsgStr).append(rewardMsg);

		int boxCount =0;
		try {
			boxCount = botService.selectPointNewBoxCount(map);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(boxCount>0) {
			msg.append(enterStr + "보유 상자 갯수 : "+boxCount);
		}
		msg.append(enterStr +countingMsg);
		
	    return msg.toString();
	}

	//테스트
	void respawnBoss(HashMap<String, Object> map) {
		try {
			HashMap<String, Object> newBoss = new HashMap<>();
			newBoss.put("startDate",
					LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			newBoss.put("roomName", map.get("roomName"));

			// 랜덤 스탯 생성기
			Random rand = new Random();
			
			 // --- 사용자 최고 레벨 조회 ---
	        HashMap<String, Object> values = botService.selectBotMaxLv(map);
	        int userMaxWeapon = values.get("VAL1") == null ? 0 : Integer.parseInt(values.get("VAL1").toString());
	        int userMaxItem   = values.get("VAL2") == null ? 0 : Integer.parseInt(values.get("VAL2").toString());
	        int userMaxAcc    = values.get("VAL3") == null ? 0 : Integer.parseInt(values.get("VAL3").toString());

	        // --- HP 계산: 유저 최고 레벨 총합 기반 ---
	        int userTotalLv = userMaxWeapon + userMaxItem + userMaxAcc;
	        int orgHp = (userTotalLv * 300) + (5000 + rand.nextInt(5001)); // 5000 ~ 10000 랜덤 보정
	        newBoss.put("org_hp", orgHp);
	        
	        int reward = 600 + (int)(Math.pow((orgHp - 5000) / 55000.0, 0.8) * 1200);
			newBoss.put("reward", reward);
			
			if (userTotalLv <= 20) {
			    // --- 유저 합계가 20레벨 이하라면 능력치는 전부 0 ---
			    newBoss.put("evadeRate", 0);
			    newBoss.put("atkRate", 0);
			    newBoss.put("atkPower", 0);
			    newBoss.put("defRate", 0);
			    newBoss.put("defPower", 0);
			    newBoss.put("critDefRate", 0);
			} else {
				// --- 능력치 총합 예산 (유저 레벨 기반 랜덤) ---
		        int minBudget = Math.max(6, userTotalLv / 2);       // 최소 유저 합계만큼
		        int maxBudget = Math.max(12, userTotalLv * 3 / 2);  // 1.5배까지
		        int totalBudget = minBudget + rand.nextInt(maxBudget - minBudget + 1);
		        
		        int maxStat = 25; // 능력치 최대치
		        // 최소 1씩 보장
		        int[] stats = new int[6];
		        Arrays.fill(stats, 1);
		        int remaining = totalBudget - 6;

		        // 남은 포인트를 랜덤 분배 (각 스탯 maxStat 초과 방지)
		        while (remaining > 0) {
		            int idx = rand.nextInt(6);
		            if (stats[idx] < maxStat) {
		                stats[idx]++;
		                remaining--;
		            }
		        }

		        newBoss.put("evadeRate", stats[0]);
		        newBoss.put("atkRate", stats[1]);
		        newBoss.put("atkPower", stats[2]);
		        newBoss.put("defRate", stats[3]);
		        newBoss.put("defPower", stats[4]);
		        newBoss.put("critDefRate", stats[5]);
			}
	        
			// hideRule : 아침 / 저녁 / 새벽 중 랜덤
			String[] hideRules = { "아침","점심", "저녁", "새벽" };
			newBoss.put("hideRule", hideRules[rand.nextInt(hideRules.length)]);
			// TODO: 다른 스탯(ATK_RATE, DEF_RATE 등)도 초기화 값 넣기
			botService.insertBotPointBossTx(newBoss); // 신규 보스 생성 쿼리

		} catch (Exception e) {
			System.err.println("보스 재생성 실패: " + e.getMessage());
		}
	}

	
	public String calcBossReward2(HashMap<String, Object> map) {
		String roomName = (String) map.get("roomName");
		int totalReward = Integer.parseInt(map.get("reward").toString()) ; // 기본 총 보상 포인트
		int tot1=0;
		int tot2=0;
		int bossOrgMaxHp = Integer.parseInt(map.get("org_hp").toString());
		
		tot1 = totalReward/10*7;
		tot2 = totalReward/10*3;
		
		List<HashMap<String, Object>> top3List = botService.selectTop3Contributors(map);
		
		int totalTop3Damage = 0;
		for (HashMap<String, Object> row : top3List) {
			totalTop3Damage += Integer.parseInt(row.get("SCORE").toString());
		}
		
		StringBuilder msgBuilder = new StringBuilder();
		
		msgBuilder.append("보스는 2시간 뒤 재등장합니다!");
		
		msgBuilder.append(enterStr).append(enterStr).append("보스 기여도 보상 분배 결과").append(allSeeStr);
		
		msgBuilder.append(enterStr+"횟수 기여도"+enterStr);
		for (HashMap<String, Object> row : top3List) {
			String name = row.get("USER_NAME").toString();
			int cnt = Integer.parseInt(row.get("CNT").toString());
			int totCnt = Integer.parseInt(row.get("TOT_CNT").toString());
			
			double cntRatio = (double) cnt / totCnt * 100;
			
			// top3 데미지 합 대비 분배 비율
			double rewardRatio = (double) cnt / totCnt;
			int reward = (int) Math.floor(tot1 * rewardRatio); // 내림처리
			
			// 포인트 지급 처리
			HashMap<String, Object> rewardMap = new HashMap<>();
			rewardMap.put("roomName", roomName);
			rewardMap.put("userName", name);
			rewardMap.put("score", reward);
			rewardMap.put("cmd", "boss_kill_reward");
			
			try {
				botService.insertBotPointRankTx(rewardMap);
			} catch (Exception e) {
				// 오류 무시
			}
			
			// 메시지 작성
			String percentStr = String.format("%.0f", cntRatio); // 정수 퍼센트
			msgBuilder
			.append(name)
			.append(" - ")
			.append(cnt) //score가 3분의1로 나눠지기때문 
			.append(" 회(")
			.append(percentStr)
			.append("%) - ")
			.append(reward)
			.append("pt 지급")
			.append(enterStr)
			
			;
		}
		
		msgBuilder.append(enterStr+"데미지 기여도"+enterStr);
		for (HashMap<String, Object> row : top3List) {
			String name = row.get("USER_NAME").toString();
			int damage = Integer.parseInt(row.get("SCORE").toString());
			
			// 보스 전체 체력 대비 데미지 비율 (%)
			double bossRatio = (double) damage / bossOrgMaxHp * 100;
			
			// top3 데미지 합 대비 분배 비율
			double rewardRatio = (double) damage / totalTop3Damage;
			int reward = (int) Math.floor(tot2 * rewardRatio); // 내림처리
			
			// 포인트 지급 처리
			HashMap<String, Object> rewardMap = new HashMap<>();
			rewardMap.put("roomName", roomName);
			rewardMap.put("userName", name);
			rewardMap.put("score", reward);
			rewardMap.put("cmd", "boss_kill_reward");
			
			try {
				botService.insertBotPointRankTx(rewardMap);
			} catch (Exception e) {
				// 오류 무시
			}
			
			// 메시지 작성
			String percentStr = String.format("%.0f", bossRatio*4); // 정수 퍼센트
			msgBuilder
			.append(name)
			.append(" - ")
			.append(damage*4) //score가 4분의1로 나눠지기때문 
			.append(" dmg(")
			.append(percentStr)
			.append("%) - ")
			.append(reward)
			.append("pt 지급")
			.append(enterStr)
			
			;
		}
		
		
		
		
		
		return msgBuilder.toString();
	}
	
}

