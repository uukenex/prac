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
			return false;//불가
		}
		
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
		
		return prefix+enterStr+"『"+map.get("userName") + "』 님의 주사위: "+number+" (0~100) "+score+"점 획득"+enterStr+"갱신 포인트 : "+new_score;
		
		
	}
}
