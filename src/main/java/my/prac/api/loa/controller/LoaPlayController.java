package my.prac.api.loa.controller;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.MiniGameUtil;


@Controller
public class LoaPlayController {
	static Logger logger = LoggerFactory.getLogger(LoaPlayController.class);
	@Resource(name = "core.prjbot.BotService")
	BotService botService;

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
	
	boolean isBossHiddenNow(boolean item_6_1, HashMap<String,Object> map) {
	    LocalTime now = LocalTime.now();
	    LocalTime start = LocalTime.of(2, 0);
	    LocalTime end = LocalTime.of(6, 0);

	    map.put("night_attack_ok", "N");

	    if (!now.isBefore(start) && now.isBefore(end)) {
	        if(item_6_1) {
	            // 야간 투시경 아이템 있음
	            map.put("night_attack_ok", "Y");
	            map.put("timeDelay", 10); // 10분으로 줄임
	        } else {
	            map.put("extra_msg", "보스가 어둠에 숨었습니다...공격불가..(02시~06시 불가시간)");
	            return true; // 숨겨진 상태
	        }
	    }
	    return false; // 숨겨지지 않음
	}
	
	boolean checkBossCooldown(HashMap<String,Object> map) {
		if("test".equals(map.get("param1"))){
			return true;
		}
		map.put("timeDelay", 15);
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
	int getWeaponLv(HashMap<String,Object> map) {
		return botService.selectWeaponLvCheck(map);
	}
	
	public HashMap<String, Object> getWeaponStatsForPoint(HashMap<String, Object> map) {
		HashMap<String, Object> result = new HashMap<>();
		Random rand = new Random();
		
		// 무기 레벨 조회 (없으면 0)
		int weaponLv = botService.selectWeaponLvCheckForPoint(map);
		if (weaponLv < 0) weaponLv = 0; // 안전하게 0 처리
		
		// 최소/최대 데미지 계산
		int min = (1 + (weaponLv / 2)) / 2;
		int max = (5 + weaponLv) * 2;
		
		// baseDamage 랜덤 생성
		int baseDamage = rand.nextInt(max - min + 1) + min;
		
		// 기본 치명타 확률 계산
		double criticalChance = Math.min(0.20 + weaponLv * 0.01, 1.0);
		
		// 결과 저장
		result.put("level", weaponLv);
		result.put("min", min);
		result.put("max", max);
		result.put("baseDamage", baseDamage);
		result.put("criticalChance", criticalChance);
		
		return result;
	}
	
	
	public HashMap<String, Object> getWeaponStats(HashMap<String, Object> map) {
	    HashMap<String, Object> result = new HashMap<>();
	    Random rand = new Random();

	    // 무기 레벨 조회 (없으면 0)
	    int weaponLv = botService.selectWeaponLvCheck(map);
	    if (weaponLv < 0) weaponLv = 0; // 안전하게 0 처리

	    // 최소/최대 데미지 계산
	    int min = (1 + (weaponLv / 2)) / 2;
	    int max = (5 + weaponLv) * 2;

	    // baseDamage 랜덤 생성
	    int baseDamage = rand.nextInt(max - min + 1) + min;

	    // 기본 치명타 확률 계산
	    double criticalChance = Math.min(0.20 + weaponLv * 0.01, 1.0);

	    // 결과 저장
	    result.put("level", weaponLv);
	    result.put("min", min);
	    result.put("max", max);
	    result.put("baseDamage", baseDamage);
	    result.put("criticalChance", criticalChance);

	    return result;
	}
	
	List<String> selectPointItemUserList(HashMap<String,Object> map){
		List<String> ableItemList = new ArrayList<>();
		try {
			List<HashMap<String,Object>> userItemList = botService.selectPointItemUserList(map);
			//MiniGameUtil.itemAddtionalFunction(map);
			for (HashMap<String,Object> userItem : userItemList) {
				ableItemList.add(userItem.get("ITEM_NO")+"-"+userItem.get("ITEM_LV"));
			}
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
			  +extraMsg + enterStr+"갱신 포인트 : "+new_score
			  +enterStr + "/상자구매 :200p구매"
			  +enterStr + "/상자 : 공개된 옵션 리스트보기 추가!"
			  +enterStr + "/보스,/보스정보: 보스정보 추가";
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
				score =+999;
			}else {
				prefix="굴리기전부터 운명적입니다. 행운의 신이 함께합니다.";
				score =+500;
			}
			
		}else if(number>=85) { // 85~98
			prefix="행운이 쌓인채로 굴러갑니다.";
			score =+number;
		}else if(number>=50) { //50~84점 => 25~42포인트
			
			if(item_2_1 && number == 77) {
				prefix="아이템 [럭키세븐] 효과가 발동되었습니다"
					  +enterStr+"오늘은 행운의 날!";
				score =+777;
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
					      +enterStr+"아이템 [부적]이 도움을 줍니다 (+25p 보정효과)"	 ;
					score =-25;
			}else if (item_1_2){
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
					      +enterStr+"아이템 [부적]의 효과로 보호받았습니다(+50p 보정효과)"	 ;
					score =-0;
			}else if (item_1_3){
				prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요."
					      +enterStr+"아이템 [부적]의 가호가 함께합니다 (+60p 보정효과)"	 ;
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
		
		return userName + " 님의 결투신청!"+enterStr +
				"**결투포인트: "+score+enterStr+enterStr+
				map.get("param1")+ " 님, 결투를 받으시려면"+enterStr+
				" /저스트가드 입력 (60sec)"
				+newMsg
				+extraMsg;
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
		
		int diff = weaponBonusForFight(map);
		int baseWinRate = 50 + diff;	
		 
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
		
		
		return winner_name+" 님, 승리"+enterStr
				+main_user_name +" : "+main_user_point_org+" → "+ main_user_point +" p"+enterStr
				+ sub_user_name +" : "+ sub_user_point_org+" → "+  sub_user_point +" p"+enterStr
				+ extraMsg;
	}
	
	String pointShop(HashMap<String,Object> map) {
		return "[포인트상점 목록]"
	          +enterStr+"/상자구입 : 200p(1회차 무료)"
	          +enterStr
	          +enterStr;
	}
	
	String pointBoxOpenBuy(HashMap<String,Object> map) {
		map.put("cmd", "pointShop");
		String msg = map.get("userName")+" 님,"+enterStr;
		int defaultScore = 200;
		try {
			
			int count = botService.selectPointItemUserCount(map);
			if(count ==0) {
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
	
	
	
	String pointBoxOpen(HashMap<String,Object> map) {
	    Random rand = new Random();
	    String msg = map.get("userName") + " 님," + enterStr;

	    // 0. 현재 상자 openFlag 조회 (null 안전 처리)
	    String openFlag = "";
	    try {
	        openFlag = botService.selectPointItemUserOpenFlag(map);
	    } catch (Exception e) {
	    	return msg+"상자열기 오류입니다";
	    }
	    
	    String itemNo = "";
	    String itemName = "";
	    String itemLv = "1";
        String itemDesc = "";

        if(openFlag ==null) {
        	return msg+"상자가 없습니다"
        			+enterStr+"/상자구매 : 200p";
        }
        
	    switch (openFlag) {
	        case "0": // 아직 오픈하지 않은 상자
	            // 1. 보물상자 성공 여부 (20%)
	            boolean isSuccess = rand.nextInt(100) < 25;

	            if (!isSuccess) {
	                try {
	                    int count = botService.selectPointItemUserCount(map);
	                    msg += "보물상자 오픈 실패!";
	                    msg += enterStr+"보물상자가 모래가 되어 사라졌습니다";
	                    
	                    map.put("cmd", "pointBoxOpenDel");
	                    botService.updatePointNewBoxOpenTx(map);
	                    if (count > 1) {
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
	                return msg;
	            }

	            // 2. 보물상자 오픈 성공 → 보물 정보 조회
	            List<HashMap<String, Object>> itemInfoList = new ArrayList<>();
	            try {
	                itemInfoList = botService.selectPointItemInfoList(map);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }

	            // 3. 유저가 가지고 있는 아이템 목록 (ITEM_NO-ITEM_LV)
	            List<String> userItemList = selectPointItemUserList(map);

	            // 4. MAX_LV 미도달 아이템 후보 목록 생성
	            List<HashMap<String, Object>> candidateList = new ArrayList<>();
	            for (HashMap<String, Object> itemInfo : itemInfoList) {
	                itemNo = itemInfo.get("ITEM_NO").toString();
	                int maxLevel = Integer.parseInt(itemInfo.get("MAX_LV").toString());
	                int currentLevel = 0;

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
	                rewardLevel = rand.nextInt(100) < 20 ? 2 : 1;
	                nowOpenFlag = (rewardLevel == 2) ? "1" : "2";
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
	                    break;
	            }
	            break;

	        case "1": // 레벨2 상자, /상자열기 호출 시
	            // 재사용 가능한 조회 메서드 활용
	            HashMap<String, Object> rareItemInfo = null;
	            try {
	                rareItemInfo = botService.selectPointItemUserOpenFlag1(map);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }

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
	            break;
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
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_8_1 = ableItemList.contains("8-1");
		boolean item_8_2 = ableItemList.contains("8-2");
		boolean item_8_3 = ableItemList.contains("8-3");
		boolean item_8_4 = ableItemList.contains("8-4");
		boolean item_8_5 = ableItemList.contains("8-5");
		
		int defaultScore = 30;
		int calcScore = defaultScore *1;
		
		if(item_8_1) {
			calcScore -=2;
		} else if(item_8_2) {
			calcScore -=4;
		} else if(item_8_3) {
			calcScore -=6;
		} else if(item_8_4) {
			calcScore -=8;
		} else if(item_8_5) {
			calcScore -=10;
		}
		
		try {
			List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
			if(score < calcScore) {
				return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
			}
			map.put("score", -calcScore);
			int new_score = botService.insertBotPointRankTx(map);
			
			msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			
			if(defaultScore!=calcScore) {
				msg +="([테메르의 정] 강화 할인 적용)"+enterStr;
			}
			
		}catch(Exception e) {
			return "강화2 포인트조회 오류입니다.";
		}
		
		msg += weapon_upgrade_logic(map,1 ,calcScore );
		
		return msg;
	}
	//추가강화
	String weapon3(HashMap<String,Object> map) {
		map.put("cmd", "weapon_upgrade2");
		String msg = map.get("userName")+" 님,"+enterStr;
		
		List<String> ableItemList = selectPointItemUserList(map); 
		boolean item_8_1 = ableItemList.contains("8-1");
		boolean item_8_2 = ableItemList.contains("8-2");
		boolean item_8_3 = ableItemList.contains("8-3");
		boolean item_8_4 = ableItemList.contains("8-4");
		boolean item_8_5 = ableItemList.contains("8-5");
		
		int defaultScore = 30;
		int calcScore = defaultScore *5;
		
		if(item_8_1) {
			calcScore -=2*5;
		} else if(item_8_2) {
			calcScore -=4*5;
		} else if(item_8_3) {
			calcScore -=6*5;
		} else if(item_8_4) {
			calcScore -=8*5;
		} else if(item_8_5) {
			calcScore -=10*5;
		}
		
		try {
			List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
			int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
			if(score < calcScore) {
				return map.get("userName")+" 님, "+calcScore+" p 이상만 가능합니다.";
			}
			map.put("score", -calcScore);
			int new_score = botService.insertBotPointRankTx(map);
			
			msg += calcScore+"p 사용! .. "+score + "p → "+ new_score+"p"+enterStr;
			
			if(defaultScore*5 != calcScore) {
				msg +="([테메르의 정] 강화 할인 적용)"+enterStr;
			}
			
		}catch(Exception e) {
			return "강화3 포인트조회 오류입니다.";
		}
		
		msg += weapon_upgrade_logic(map,5,defaultScore);
		
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
			    
			    if(refundCount > 0) {
			    	//중간성공시 .. 로직 
			    	 msg += rate + "회차 중 " + (rate - refundCount) + "회차에 " +
			    	           (isMaxedOut ? "장인의기운 100% 도달" : "성공") +
			    	           ", 환급포인트 : " + refundAmount + enterStr;
		        	map.put("score", refundAmount);
		        	map.put("cmd", "weapon_upgrade2");
		        	botService.insertBotPointRankTx(map);
			    }
			    
			    if (isMaxedOut) {
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
				    msg += "성공률 : "+successRate+"%"+enterStr;
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
	    Double[] data = MiniGameUtil.RATE_MAP.getOrDefault(level, new Double[]{0.0, 0.0});
	    
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
			sb.append("(보상포인트 = 체력 / 20 ± 500 )").append(enterStr);
			sb.append("출현 시간 : ").append(startTime).append(enterStr+enterStr);

			sb.append("공격력 : 1~").append(bossAtkPower);
			sb.append("( 확률 : ").append(bossAtkRate).append("%)"+enterStr);
			sb.append("방어력 : 1~").append(bossDefPower);
			sb.append("( 확률 : ").append(bossDefRate).append("%)"+enterStr);
			sb.append("치명 저항 : ").append(critDefRate).append("%"+enterStr);
			sb.append("회피율 : ").append(evadeRate).append("%"+enterStr);
			
			String hideRuleMsg ="";
			switch(hideRule) {
				case "아침":
					hideRuleMsg="06시~10시 공격불가";
					break;
				case "점심":
					hideRuleMsg="10시~15시 공격불가";
					break;
				case "저녁":
					hideRuleMsg="15시~19시 공격불가";
					break;
				default:
					hideRuleMsg="02시~06시 공격불가";
					break;
			}
			sb.append("숨김 룰 : ").append(hideRuleMsg).append(enterStr);

			return sb.toString();

		} catch (Exception e) {
			return "보스 정보를 가져오는데 실패했습니다. (" + e.getMessage() + ")";
		}
	}
	
	public String attackBoss(HashMap<String, Object> map) {
		return "";
		
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
	    boolean item_6_1 = ableItemList.contains("6-1");
	    boolean item_7_1 = ableItemList.contains("7-1");
	    boolean item_7_2 = ableItemList.contains("7-2");

	    boolean item_12_1 = ableItemList.contains("12-1");
	    boolean item_12_2 = ableItemList.contains("12-2");
	    boolean item_12_3 = ableItemList.contains("12-3");
	    boolean item_12_4 = ableItemList.contains("12-4");
	    boolean item_12_5 = ableItemList.contains("12-5");
	    boolean item_13_1 = ableItemList.contains("13-1");
	    boolean item_13_2 = ableItemList.contains("13-2");
	    boolean item_13_3 = ableItemList.contains("13-3");
	    
	    
	    // ----------------
	    // 보스 숨김 체크
	    // ----------------
	    
	    LocalTime now = LocalTime.now();
	    LocalTime start = LocalTime.of(2, 0);
	    LocalTime end = LocalTime.of(6, 0);
	    map.put("night_attack_ok", "N");
	    
		switch (hideRule) {
		case "아침":
			start = LocalTime.of(6, 0);
			end = LocalTime.of(10, 0);
			if (!now.isBefore(start) && now.isBefore(end)) {
				if(item_6_1) {
					map.put("night_attack_ok","Y");
				}else {
					return "보스가 안개에 숨었습니다...공격불가..(06시~10시 불가시간)";
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
					return "보스가 구름에 숨었습니다...공격불가..(10시~15시 불가시간)";
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
					return "보스가 퇴근길에 숨었습니다...공격불가..(15시~19시 불가시간)";
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
				
					return "보스가 어둠에 숨었습니다...공격불가..(02시~06시 불가시간)";
				}
			}
			
			break;
			
		}

	    // ----------------
	    // 공격 쿨타임 체크
	    // ----------------
	    if (!checkBossCooldown(map)) {
	        return map.get("userName") + "님," + enterStr + map.get("extra_msg");
	    }

	    

	    HashMap<String, Object> weaponInfo = getWeaponStats(map);
	    
	    int weaponLv = Integer.parseInt(weaponInfo.get("level").toString());
	    Double weaponCriticalChance = Double.parseDouble(weaponInfo.get("criticalChance").toString());
	    int weaponMin = Integer.parseInt(weaponInfo.get("min").toString());
	    int weaponMax = Integer.parseInt(weaponInfo.get("max").toString());
	    
	    if(item_12_1) {
	    	weaponMin +=3;
	    }
	    if(item_12_2) {
	    	weaponMin +=6;
	    }
	    if(item_12_3) {
	    	weaponMin +=9;
	    }
	    if(item_12_4) {
	    	weaponMin +=12;
	    }
	    if(item_12_5) {
	    	weaponMin +=15;
	    }
	    
	    Random rand = new Random();
	    int weaponBaseDmg =  rand.nextInt(weaponMax - weaponMin + 1) + weaponMax;
	 // ----------------
	    // 5. 회피 계산
	    // ----------------
	    double roll = Math.random();
	    boolean isEvade = false;
	    String isEvadeMsg = "";

	    if (roll < (evadeRate / 100.0)) {  // 보스가 회피 성공
	        isEvade = true;
	        isEvadeMsg = "보스가 회피합니다." + enterStr;

	        // 아이템 7-1, 7-2 가 있으면 보스 회피 무효화 시도
	        if (item_7_1) {
	            if (roll < 0.05) {  
	                isEvade = false;
	                isEvadeMsg = "[덫] 보스 회피 무효!" + enterStr;
	            } else {
	                isEvadeMsg = "[덫] 실패, 보스 회피!!" + enterStr;
	            }
	        } else if (item_7_2) {
	            if (roll < 0.10) {  
	                isEvade = false;
	                isEvadeMsg = "[덫] lv2 보스 회피 무효!" + enterStr;
	            } else {
	                isEvadeMsg = "[덫] lv2 실패, 보스 회피!!" + enterStr;
	            }
	        }
	    }

	    // ----------------
	    // 야간투시경 적용
	    //  ---------------
	    String nightMsg = "";
	    if ("Y".equals(map.get("night_attack_ok"))) {
	        nightMsg = "[야간투시경] 적용, 숨은보스 타격 효과 " + enterStr;
	        
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
			int baseDamage = weaponBaseDmg ;

			 // ----------------
	        // 치명타 확률 계산 (새 방식)
	        // ----------------
	        criticalChance = weaponCriticalChance;

	        int baseCritPercent = (int)(criticalChance * 100); 
	        int totalCritPercent = baseCritPercent;
	        List<String> critParts = new ArrayList<>();

	        
	        if (weaponLv > 0) {
	            critParts.add("무기강화(" + baseCritPercent + "%)");
	        }

	        if (item_5_1) {
	            totalCritPercent += 5;
	            critParts.add("+ [예리한칼날](5%)");
	        }
	        if (item_5_2) {
	            totalCritPercent += 10;
	            critParts.add("+ [예리한칼날 Lv2](10%)");
	        }
	        if (item_5_3) {
	            totalCritPercent += 15;
	            critParts.add("+ [예리한칼날 Lv3](15%)");
	        }
	        if((hp * 100.0) / org_hp < 10) {
	        	if (item_4_1) {
		            totalCritPercent += 5;
		            critParts.add("+ [스카우터](5%)");
		            scoutMsg +="약점을 노출시켰습니다";
		        }
		        if (item_4_2) {
		            totalCritPercent += 10;
		            critParts.add("+ [스카우터 Lv2](10%)");
		            scoutMsg +="약점을 노출시켰습니다";
		        }
	        }
	        

	        if (critDefRate > 0) {
	            totalCritPercent -= critDefRate;
	            critParts.add("- 보스저항(" + critDefRate + "%)");
	        }

	        if (totalCritPercent < 0) totalCritPercent = 0;

	        critLog.setLength(0);
	        if (totalCritPercent > 0) {
	            critLog.append("▶치명타확률 : ").append(totalCritPercent).append("%").append(enterStr);
	            if (!critParts.isEmpty()) {
	                critLog.append(String.join(" ", critParts)).append(enterStr);
	            }
	        }
	        
	        
		    // 치명타 발동 여부
	        isCritical = Math.random() < (totalCritPercent / 100.0);
	        if (isCritical) {
	            isSuperCritical = Math.random() < 0.10;
	        }

			// 최종 데미지 산출
			if (isSuperCritical) {
				damage = baseDamage * 5;
				dmgMsg = "[✨초강력 치명타!!] 데미지 " + baseDamage + " → " + damage;
			} else if (isCritical) {
				damage = baseDamage * 3;
				dmgMsg = "[✨치명타!] 데미지 " + baseDamage + " → " + damage;
			} else {
				damage = baseDamage;
				dmgMsg = "데미지 " + baseDamage + " 로 공격!";
			}

			// 보스 방어 적용 (메시지 추가)
			if (Math.random() < bossDefRate / 100.0) {
				appliedDefPower = ThreadLocalRandom.current().nextInt(1, bossDefPower + 1);

				damage -= appliedDefPower;
				if (damage < 0)
					damage = 0;

				bossDefenseMsg = "보스가 방어하였습니다! 데미지 " + appliedDefPower + " 상쇄!" + enterStr;
			}
		}
	    
	    // ----------------
	    // 보스 HP/스코어/리워드 처리
	    // ----------------
	    int score = damage / 3;
	    boolean newbieYn = weaponLv < 13;
	    if (newbieYn) score += 10;

	    boolean isKill = false;
	    int newHp = hp - damage;
	    String rewardMsg = "";
	    if (newHp <= 0) {
	        if (isCritical) {
	            isKill = true;
	            score = Math.min(damage, hp) / 3 + 100;
	            map.put("reward", reward);
	            map.put("org_hp", org_hp);
	            rewardMsg = calcBossReward2(map);
	            respawnBoss(map);
	            
	            appliedAtkPower=0;
	            appliedDefPower=0;
	            bossDefenseMsg="";
	        } else {
	            newHp = 1;
	            int allowedDamage = hp - 1;
	            score = Math.min(damage, allowedDamage) / 3;
	            damage = allowedDamage;
	            
	            //보스 무적이라 메시지 필요없음 
	            dmgMsg="";
	            bossDefenseMsg="";
	        }
	    }
	    
	    if(!isKill) {
	    	if (Math.random() < bossAtkRate / 100.0) {
	    		String item13Msg="";
		    	appliedAtkPower = ThreadLocalRandom.current().nextInt(1, bossAtkPower + 1);
		    	
		    	if(item_13_1) {
		    		appliedAtkPower -= 2;
		    		item13Msg+=enterStr+"방어 유물의 효과로 일부 피해를 막았습니다.";
		    	}
		    	if(item_13_2) {
		    		appliedAtkPower -= 4;
		    		item13Msg+=enterStr+"방어 유물의 효과로 일부 피해를 막았습니다.";
		    	}
		    	if(item_13_3) {
		    		appliedAtkPower -= 6;
		    		item13Msg+=enterStr+"방어 유물의 효과로 일부 피해를 막았습니다.";
		    	}
		    	
		        score -= appliedAtkPower;
		        map.put("extra_msg", "보스가 반격합니다! 데미지를 입었습니다! -" + appliedAtkPower+"p"+item13Msg);
		    }
	    }
	    
	    // DB 반영
	    int new_score;
	    try {
	        map.put("hp", hp);
	        map.put("newHp", newHp);
	        map.put("seq", seq);
	        map.put("damage", damage);
	        map.put("score", score);
	        map.put("endYn", isKill ? "1" : "0");
	        map.put("atkPower", appliedAtkPower);
	        map.put("defPower", appliedDefPower );
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
		    if (!bossDefenseMsg.isEmpty())
		        msg.append(bossDefenseMsg);
		    if (!scoutMsg.isEmpty())
		        msg.append(scoutMsg);
		} else {
		    msg.append("보스가 공격을 회피! 데미지 0!").append(enterStr);
		}
		if (item_7_1 || item_7_2)
			msg.append(isEvadeMsg);
		
		msg.append(nightMsg).append(enterStr);
		
		// 치명타 확률 & 결과 메시지
		msg.append(critLog.toString());

		// 3. 보스 반격
		if (map.get("extra_msg") != null) {
			msg.append(map.get("extra_msg")).append(enterStr).append(enterStr);
		}
		// 4. 보스 상태
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
					msg.append("보스 체력: ").append(newHp).append("/??? [스카우터 Lv2]").append(enterStr);
				else
					msg.append("보스 체력: ???/???").append(enterStr);
			}
			
		}
		msg.append("공격 쿨타임 : ").append(map.get("timeDelay")).append(" Min").append(enterStr);

		// 5. 포인트 및 보상
		msg.append("획득 포인트: ").append(score);
		if (newbieYn)
			msg.append(" (초보자 +10p)");
		msg.append(enterStr).append("갱신포인트 : ").append(new_score).append(enterStr);
		
		if (!rewardMsg.isEmpty())
			msg.append(anotherMsgStr).append(rewardMsg);

	    return msg.toString();
	}

	private void respawnBoss(HashMap<String, Object> map) {
		try {
			HashMap<String, Object> newBoss = new HashMap<>();
			newBoss.put("startDate",
					LocalDateTime.now().plusHours(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			newBoss.put("roomName", map.get("roomName"));

			// 랜덤 스탯 생성기
			Random rand = new Random();

			// HP : 5000 ~ 20000
			int orgHp = 5000 + rand.nextInt(20000 - 5000 + 1);
			newBoss.put("org_hp", orgHp);

			int reward = orgHp / 20 + rand.nextInt(500);
			newBoss.put("reward", reward);

			// --- 6개 항목 합계가 100 이하가 되도록 랜덤 분배 ---
	        int totalLimit = 100;
	        int remaining = totalLimit - 6; // 최소 1씩 보장하기 위해 6 빼고 시작
	        int[] stats = new int[6];

	        for (int i = 0; i < 6; i++) {
	            // 남은 포인트가 있고, 아직 배분할 슬롯이 남았을 때 랜덤 분배
	            int add = (i == 5) ? remaining : rand.nextInt(remaining + 1);
	            stats[i] = 1 + add; // 최소 1 보장
	            remaining -= add;
	            if (stats[i] > 20) stats[i] = 20; // maxStat 초과 방지
	        }

	        // 랜덤 순서 섞기
	        for (int i = stats.length - 1; i > 0; i--) {
	            int j = rand.nextInt(i + 1);
	            int tmp = stats[i];
	            stats[i] = stats[j];
	            stats[j] = tmp;
	        }

	        newBoss.put("evadeRate", stats[0]);
	        newBoss.put("atkRate", stats[1]);
	        newBoss.put("atkPower", stats[2]);
	        newBoss.put("defRate", stats[3]);
	        newBoss.put("defPower", stats[4]);
	        newBoss.put("critDefRate", stats[5]);
	        
	        
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
		int bossOrgMaxHp = Integer.parseInt(map.get("org_hp").toString());
		
		List<HashMap<String, Object>> top3List = botService.selectTop3Contributors(map);
		
		int totalTop3Damage = 0;
		for (HashMap<String, Object> row : top3List) {
			totalTop3Damage += Integer.parseInt(row.get("SCORE").toString());
		}
		
		StringBuilder msgBuilder = new StringBuilder();
		msgBuilder.append("보스 기여도 보상 분배 결과").append(enterStr);
		
		for (HashMap<String, Object> row : top3List) {
			String name = row.get("USER_NAME").toString();
			int damage = Integer.parseInt(row.get("SCORE").toString());
			
			// 보스 전체 체력 대비 데미지 비율 (%)
			double bossRatio = (double) damage / bossOrgMaxHp * 100;
			
			// top3 데미지 합 대비 분배 비율
			double rewardRatio = (double) damage / totalTop3Damage;
			int reward = (int) Math.floor(totalReward * rewardRatio); // 내림처리
			
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
			String percentStr = String.format("%.0f", bossRatio); // 정수 퍼센트
			msgBuilder
			.append(name)
			.append(" - ")
			.append(damage)
			.append(" dmg(")
			.append(percentStr)
			.append("%) - ")
			.append(reward)
			.append("pt 지급")
			.append(enterStr)
			
			;
		}
		
		msgBuilder.append(enterStr).append(enterStr).append("6시간 뒤 재등장 예정!");
		return msgBuilder.toString();
	}
	public String calcBossReward(HashMap<String, Object> map) {
	    String roomName = (String) map.get("roomName");
	    int totalReward = Integer.parseInt(map.get("max_hp").toString())/10 ; // 기본 총 보상 포인트
	    int bossOrgMaxHp = Integer.parseInt(map.get("max_hp").toString());
	    
	    List<HashMap<String, Object>> top3List = botService.selectTop3Contributors(map);

	    int totalTop3Damage = 0;
	    for (HashMap<String, Object> row : top3List) {
	        totalTop3Damage += Integer.parseInt(row.get("SCORE").toString());
	    }

	    StringBuilder msgBuilder = new StringBuilder();
	    msgBuilder.append("보스 기여도 보상 분배 결과").append(enterStr);

	    for (HashMap<String, Object> row : top3List) {
	        String name = row.get("USER_NAME").toString();
	        int damage = Integer.parseInt(row.get("SCORE").toString());

	        // 보스 전체 체력 대비 데미지 비율 (%)
	        double bossRatio = (double) damage / bossOrgMaxHp * 100;

	        // top3 데미지 합 대비 분배 비율
	        double rewardRatio = (double) damage / totalTop3Damage;
	        int reward = (int) Math.floor(totalReward * rewardRatio); // 내림처리

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
	        String percentStr = String.format("%.0f", bossRatio); // 정수 퍼센트
	        msgBuilder
	            .append(name)
	            .append(" - ")
	            .append(damage)
	            .append(" dmg(")
	            .append(percentStr)
	            .append("%) - ")
	            .append(reward)
	            .append("pt 지급")
	            .append(enterStr);
	    }

	    return msgBuilder.toString();
	}
	
}
