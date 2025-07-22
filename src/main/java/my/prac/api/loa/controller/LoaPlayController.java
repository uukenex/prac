package my.prac.api.loa.controller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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
		int check_val = botService.selectDailyCheck(map);
		boolean check = false;
		
		switch(map.get("cmd").toString()) {
			case "weapon_upgrade":
				if((check_val+1) <= 5 ) {
					map.put("extra_msg", (check_val+1)+"회 시도, 5회까지 가능 이벤트 ing!!");
					check = true;
				}
				break;
			default:
				if(check_val == 0 ) {
					check = true;
				}
		}
		
		return check;
		
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
			return map.get("userName")+"님 오늘의 주사위 포인트는 이미 획득 했습니다.";
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
		
		return userName + " 님의 결투신청!"+enterStr +
				"**결투포인트: "+score+enterStr+enterStr+
				map.get("param1")+ " 님, 결투를 받으시려면"+enterStr+
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
	
	String pointShop(HashMap<String,Object> map) {
		List<HashMap<String,Object>> ls = botService.selectBotPointRankNewScore(map);
		int score = Integer.parseInt(ls.get(0).get("SCORE").toString());
		
		String str=map.get("userName")+"님 현재 포인트 "+score +"p"+enterStr;
		str += enterStr;
		str += "1.후원자마크(캐릭터(★)) - "+"2000p"+enterStr;
		str += "2.골드환전 - "+"1000p단위 1000골드"+enterStr;
		str += enterStr;
		str += "/포인트사용 할말.. 로 개발자에게 말해주세요.";
		
		
		return "";
		//return str;
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
						"최대 50p에서 점차적으로 줄어듭니다!";
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
							score =50;
							preview_score=40;
							break;
						case 2:
							score =40;
							preview_score=30;
							break;
						case 3:
							score =30;
							preview_score=25;
							break;
						case 4:
							score =25;
							preview_score=20;
							break;
						case 5:
							score =20;
							preview_score=5;
							break;
						case 6:
							score =5;
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
					        res += in_number + (targetNumber > in_number ? " ↑ UP" : " ↓ DOWN") + enterStr;
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
				        String direction = "";
				        if (targetNumber > number) {
				            direction = " ↑";
				        } else if (targetNumber < number) {
				            direction = " ↓";
				        } else {
				            direction = ""; // 정답과 같은 경우 (이론상 발생 안 함, 방어적 처리)
				        }
				        res += i + "차시도 " + number + direction + enterStr;
				    }
				}

				// 현재 시도는 이미 비교했으므로 다시 계산
				String currentDirection = "";
				if (targetNumber > in_number) {
				    currentDirection = " ↑";
				} else if (targetNumber < in_number) {
				    currentDirection = " ↓";
				} else {
				    currentDirection = "";
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
		int SuccessScore=3;
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
		if(!dailyCheck(map)) {
			return map.get("userName")+"님 오늘의 강화 완료!";
		}
		
		String msg = map.get("userName")+"님";
		
		HashMap<String, Object> now;
		int lv;
		double failPct;
		try {
			//now는 현재값을 가져오고, 없을땐 생성해서 가져온다 
			now = botService.selectBotPointWeapon(map);
			lv = Integer.parseInt(now.get("WEAPON_LV").toString());
			failPct = Double.parseDouble(now.get("FAIL_PCT").toString());
			
			msg +=" "+ lv + "lv → "+(lv+1)+"lv 강화 시도"+enterStr+enterStr;
		} catch (Exception e1) {
			return "강화정보 부족";
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
		        
		        msg += (lv+1) +" 단계 강화 성공!"+enterStr;	
		        msg += "성공률 : "+"100%"+enterStr;	
		        msg += "장인의기운이 초기화 되었습니다."+enterStr;
			}else {
				HashMap<String, Object> result = getSuccessRate(lv);
			    boolean isSuccess = (boolean) result.get("isSuccess");
			    double failAdd = (double) result.get("failAddPct");
			    double successRate = (double) result.get("successRate");     // 현재 성공 확률
			    if (isSuccess) {
			        map.put("successYn", "1");
			        map.put("failPct", 0); // 성공 시 누적 실패율 초기화
			        map.put("addPct", 0); // 실패 시 누적 증가(로그테이블)
			        map.put("tryLv", lv+1); // 현재 레벨+1 (시도레벨)
			        map.put("weaponLv", lv+1); // 현재 레벨+1 (성공레벨)
			        
			        msg += (lv+1) +" 단계 강화 성공!"+enterStr;	
			        msg += "성공률 : "+successRate+"%"+enterStr;	
			        msg += "장인의기운 "+failAdd+"%"+enterStr;			        
			        msg += "장인의기운이 초기화 되었습니다."+enterStr;			        
			    } else {
			        map.put("successYn", "0");
			        map.put("failPct", failPct + failAdd); // 실패 시 누적 증가
			        map.put("addPct", failAdd); // 실패 시 누적 증가(로그테이블)
			        map.put("tryLv", lv+1); // 현재 레벨+1 (시도레벨)
			        map.put("weaponLv", lv); // 현재 레벨+1 (실패레벨)
			        
			        
				    msg += (lv+1) + " 단계 강화 실패!"+enterStr;		
				    msg += "성공률 : "+successRate+"%"+enterStr;
				    msg += "장인의기운 +"+failAdd+"%"+enterStr;	
			        msg += "현재 장인의기운: "+(failPct + failAdd)+"%"+enterStr;	
			    }
				
			}
			botService.upsertDailyWeaponUpgradeTx(map);
			
			msg += enterStr + map.get("extra_msg");
			
		} catch (Exception e) {
			msg ="강화중 에러발생";
		}
		
		
		
		
		return msg;
	}
	
	public HashMap<String,Object> getSuccessRate(int level) {
		HashMap<Integer, Double[]> rateMap = new HashMap<>();
		//0강화에서 시도-> 성공률10%, 장기 쌓이는양 
	    rateMap.put(0,  new Double[]{15.0, 20.0});
	    rateMap.put(1,  new Double[]{14.0, 12.0});
	    rateMap.put(2,  new Double[]{13.0, 10.0});
	    rateMap.put(3,  new Double[]{12.0, 8.61});
	    rateMap.put(4,  new Double[]{11.0, 7.58});
	    rateMap.put(5,  new Double[]{10.0, 5.46});
	    rateMap.put(6,  new Double[]{9.0, 4.45});
	    rateMap.put(7,  new Double[]{8.0, 3.84});
	    rateMap.put(8,  new Double[]{7.0, 3.63});
	    rateMap.put(9,  new Double[]{6.0, 3.42});
	    rateMap.put(10, new Double[]{5.5, 3.11});
	    rateMap.put(11, new Double[]{5.0, 3.05});
	    rateMap.put(12, new Double[]{4.5, 2.98});
	    rateMap.put(13, new Double[]{4.0, 2.75});
	    rateMap.put(14, new Double[]{3.5, 2.57});
	    rateMap.put(15, new Double[]{3.0, 2.51});
	    rateMap.put(16, new Double[]{2.5, 2.44});
	    rateMap.put(17, new Double[]{2.0, 2.28});
	    rateMap.put(18, new Double[]{1.5, 2.13});
	    rateMap.put(19, new Double[]{1.0, 2.03});
	    rateMap.put(20, new Double[]{0.9, 1.99});
	    rateMap.put(21, new Double[]{0.8, 1.88});
	    rateMap.put(22, new Double[]{0.7, 1.77});
	    rateMap.put(23, new Double[]{0.6, 1.66});
	    rateMap.put(24, new Double[]{0.5, 1.55});
	    rateMap.put(25, new Double[]{0.4, 1.44});
	    rateMap.put(26, new Double[]{0.3, 1.33});
	    rateMap.put(27, new Double[]{0.2, 1.22});
	    rateMap.put(28, new Double[]{0.1, 1.11});
	    rateMap.put(29, new Double[]{0.1, 1.00});
	    rateMap.put(30, new Double[]{0.1, 0.93});
	    //30에서의 시도 -> 성공률 0.1, 장기백 0.93

	    Double[] data = rateMap.getOrDefault(level, new Double[]{0.0, 0.0});
	    double successRate = data[0];
	    double failAddPct = data[1];
	    
	    double roll = Math.random() * 100;
	    boolean isSuccess = roll < successRate;

	    HashMap<String, Object> result = new HashMap<>();
	    result.put("isSuccess", isSuccess);         // 성공 여부
	    result.put("successRate", successRate);     // 현재 성공 확률
	    result.put("failAddPct", failAddPct);       // 실패시 누적 증가량
	    return result;
	}
	
}
