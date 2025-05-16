package my.prac.api.loa.controller;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotService;


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
		List<HashMap<String,Object>> check_map = botService.selectBotPointRank(map);
		if(check_map ==null || check_map.size() ==0) {
			return true; //허용
		}else {
			/*
			try {
				if(Integer.parseInt(check_map.get(0).get("SCORE").toString()) < 0
					&& map.get("cmd").equals("diceRoll")) {
					return true;// 마이너스점수인 경우 1회 재도전 다이스만..
				}
			}catch(Exception e) {
				return false;//불가
			}
			*/
			
			return false;//불가
		}
		
	}
	
	String testMethod(HashMap<String,Object> map) {
		String str="";
		
		
		return str;
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
			return map.get("userName")+"님 이미 출석했습니다.";
		}
		Random random = new Random(); // 랜덤객체
		int score = random.nextInt(10)+1;
		int new_score=0;
		map.put("score",score);
		
		
		try {
			new_score = botService.insertBotPointRankTx(map);
		} catch (Exception e) {
			return "오류발생";
		}
		
		return map.get("userName")+"님 출석포인트 "+score+"점 획득"+enterStr+"갱신 포인트 : "+new_score;
	}
	
	String diceRoll(HashMap<String,Object> map) {
		map.put("cmd", "diceRoll");
		if(!dailyCheck(map)) {
			return map.get("userName")+"님 오늘의 주사위 완료!";
		}
		
		Random random = new Random(); // 랜덤객체
		int number = random.nextInt(101);
		String prefix="";
		
		int score = 0;
		int new_score=0;
		
		if(number>=99) {
			prefix="굴리기전부터 운명적입니다. 행운의 신이 함께합니다.";
			score =+50;
		}else if(number>=85) {
			prefix="행운이 쌓인채로 굴러갑니다.";
			score =+10;
		}else if(number>=50) {
			prefix="데굴데굴..";
			score =+5;
		}else if(number>=20) {
			prefix="또르르륵..";
			score =+1;
		}else {
			prefix="콰쾅.. 이런! 주사위가 바닥으로 떨어졌군요.";
			score =-5;
		}
		
		try {
			map.put("score", score);
			new_score = botService.insertBotPointRankTx(map);
		} catch (Exception e) {
			return "오류발생";
		}
		
		
		String msg = prefix+enterStr+"『"+map.get("userName") + "』 님의 주사위: "+number+" (0~100) "+
				   enterStr+score+"점 획득"+
				   enterStr+"갱신 포인트 : "+new_score;
		if(new_score < 0) {
			//msg += enterStr+"＊마이너스 포인트로 주사위 횟수 1부여!";
		}
		return msg;
	}
	
	String fight_s(HashMap<String,Object> map) {
		map.put("cmd", "fight_s");
		String userName;
		String targetName;
		int score;
		
		userName = map.get("userName").toString();
		try {
			targetName = map.get("param1").toString();
			targetName = targetName.replaceAll("/^@/", "");
			score = Integer.parseInt(map.get("param2").toString());
		}catch(Exception e) {
			return "/결투 결투자명 포인트 형식으로 입력해야합니다.";
		}
		
		if(score <= 0) {
			return "포인트는 1포인트이상 입력해주세요.";
		}
		
		int cnt = botService.selectBotPointRankFightBeforeCount(map);
		if(cnt>0) {
			return "결투 신청 쿨타임(신청만..2min..)";
		}
		
		List<HashMap<String,Object>> newMap = botService.selectBotPointRankFightBeforeCheck(map);
		if(newMap==null || newMap.size()==0 ) {
			return "남아있는 포인트가 부족합니다.";
		}
		if( newMap.size()==1) {
			return newMap.get(0).get("USER_NAME").toString()+" vs "+"알수없는 상대"+enterStr+"결투 실패!";
		}
		
		try {
			botService.insertBotPointFightSTx(map);
		}catch(Exception e) {
			return "오류발생";
		}
		
		return userName + " 님의 결투신청!"+enterStr +
				"**결투포인트: "+score+enterStr+enterStr+
				targetName+ " 님, 결투를 받으시려면"+enterStr+
				" /저스트가드 입력 (60sec)";
	}
	String fight_e(HashMap<String,Object> map) {
		map.put("cmd", "fight_e");
		String userName;
		String tagetName;
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
			tagetName = fightMap.get(0).get("TARGET_NAME").toString();
			score = Integer.parseInt(fightMap.get(0).get("SCORE").toString());
		}catch(Exception e) {
			return userName+" 님, 요청 결투가 없음2!";
		}
		map.put("seq", seq);
		map.put("userName", userName);
		map.put("param1", tagetName);
		map.put("param2", score);
		
		List<HashMap<String,Object>> newMap = botService.selectBotPointRankFightBeforeCheck(map);
		if(newMap==null || newMap.size()==0 || newMap.size()==1) {
			return "남아있는 포인트가 부족합니다.";
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
		
		Random random = new Random(); // 랜덤객체
		int number = random.nextInt(100)%2;
		if(number==1) {
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
		return winner_name+" 님, 승리"+enterStr
				+main_user_name +" : "+main_user_point_org+" → "+ main_user_point +" p"+enterStr
				+ sub_user_name +" : "+ sub_user_point_org+" → "+  sub_user_point +" p"+enterStr;
	}
	
	String eventApply(HashMap<String,Object> map) {
		//select 현재 진행중인 이벤트가 있는지?
		
		//
		
		return "뽑기 기능은 개발중입니다.";
	}
	
	String gamble(HashMap<String,Object> map) {
		return "뽑기 기능은 개발중입니다.";
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
	
}
