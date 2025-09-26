package my.prac.core.prjbot.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotSettleService;

@Service("core.prjbot.BotSettleService")
public class BotSettleServiceImpl implements BotSettleService {
	
	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	
	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;
	
	public int selectBotPointSumScore(HashMap<String,Object> map){
		return botSettleDAO.selectBotPointSumScore(map);
	}
	public int selectBotPointSumScoreForPoint(HashMap<String,Object> map){
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("newUserName", newUserName.get(0));
			}else {
				return 0;
			}
		}
		return botSettleDAO.selectBotPointSumScoreForPoint(map);
	}
	

	public List<HashMap<String,Object>> selectBotPointAccRank(HashMap<String,Object> map){
		return botSettleDAO.selectBotPointAccRank(map);
	}
	public String selectBotPointAccRank1st() {
		return botSettleDAO.selectBotPointAccRank1st();
	}
	

	public List<HashMap<String,Object>> selectBotPointAccLogRank(HashMap<String,Object> map){
		return botSettleDAO.selectBotPointAccLogRank(map);
	}
	public String selectBotPointAccLogRank1st() {
		return botSettleDAO.selectBotPointAccLogRank1st();
	}
	
}
