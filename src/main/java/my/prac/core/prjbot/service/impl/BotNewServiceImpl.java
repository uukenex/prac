package my.prac.core.prjbot.service.impl;

import java.sql.Timestamp;
import java.util.HashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.api.loa.controller.BossAttackController.BattleLog;
import my.prac.api.loa.controller.BossAttackController.Monster;
import my.prac.api.loa.controller.BossAttackController.User;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotNewDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotNewService;

@Service("core.prjbot.BotNewService")
public class BotNewServiceImpl implements BotNewService {

	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewDAO")
	BotNewDAO botNewDAO;

	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;

	public int insertBotPointNew(HashMap<String, Object> map) {
		return botNewDAO.insertBotPointNew(map);
	}

	public User selectUser(String userName, String roomName) {
		return botNewDAO.selectUser(userName,roomName);
	}

	public Monster selectMonsterByNo(String userName, String roomName) {
		return botNewDAO.selectMonsterByNo(userName,roomName);
	}

	public Timestamp selectLastAttackTime(String userName, String roomName) {
		return botNewDAO.selectLastAttackTime(userName,roomName);
	}

	// 업데이트/로그
	public void updateUserAfterBattle(String userName, String roomName, int newLv, int newExpCur, int newExpNext,
			int newHpCur, int newHpMax, int newAtkMin, int newAtkMax) {
		
		botNewDAO.updateUserAfterBattle(userName, roomName, newLv, newExpCur, newExpNext, newHpCur, newHpMax, newAtkMin,
				newAtkMax);
	}

	public void insertBattleLog(BattleLog log) {
		botNewDAO.insertBattleLog(log);
	}
}
