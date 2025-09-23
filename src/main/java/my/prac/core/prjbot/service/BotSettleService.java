package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface BotSettleService {
	//지금까지 획득 전체포인트 
	public int selectBotPointSumScore(HashMap<String,Object> map);
	public int selectBotPointSumScoreForPoint(HashMap<String,Object> map);
	
	
	//악세랭킹
	public List<HashMap<String,Object>> selectBotPointAccRank(HashMap<String,Object> map);
	public String selectBotPointAccRank1st();
	//악세로그랭킹
	public List<HashMap<String,Object>> selectBotPointAccLogRank(HashMap<String,Object> map);
	public String selectBotPointAccLogRank1st();
} 

	
