package my.prac.core.prjbot.service.impl;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
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

	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map){
		return botNewDAO.selectParam1ToNewUserSearch(map);
	}
	
	public int insertBotPointNew(HashMap<String, Object> map) {
		return botNewDAO.insertBotPointNew(map);
	}

	@Override
    public User selectUser(String userName, String roomName) {
        return botNewDAO.selectUser(userName, roomName);
    }

    @Override
    public OngoingBattle selectOngoingBattle(String userName, String roomName) {
        return botNewDAO.selectOngoingBattle(userName, roomName);
    }
    
    @Override
    public AttackDeathStat selectAttackDeathStats(String userName, String roomName) {
        return botNewDAO.selectAttackDeathStats(userName, roomName);
    }

    @Override
    public List<KillStat> selectKillStats(String userName, String roomName){
    	return botNewDAO.selectKillStats(userName,roomName);
    }
    
    @Override
    public List<Monster> selectAllMonsters(){
    	return botNewDAO.selectAllMonsters();
    }
    @Override
    public Monster selectMonsterByNo(int monNo) {
        return botNewDAO.selectMonsterByNo(monNo);
    }
    @Override
    public Monster selectMonsterByName(String monName) {
    	return botNewDAO.selectMonsterByName(monName);
    }

    @Override
    public Timestamp selectLastAttackTime(String userName, String roomName) {
        return botNewDAO.selectLastAttackTime(userName, roomName);
    }

    @Override
    public int updateUserAfterBattleTx(String userName, String roomName, int newLv, int newExpCur, int newExpNext,
                                     int newHpCur, int newHpMax, int newAtkMin, int newAtkMax,int critRate,int hpRegen ) {
        return botNewDAO.updateUserAfterBattle(userName, roomName, newLv, newExpCur, newExpNext,
                                            newHpCur, newHpMax, newAtkMin, newAtkMax, critRate,hpRegen );
    }

    @Override
    public int insertBattleLogTx(BattleLog log) {
        return botNewDAO.insertBattleLog(log);
    }

    @Override
    public int closeOngoingBattleTx(String userName, String roomName) {
        return botNewDAO.closeOngoingBattle(userName, roomName);
    }
    @Override
    public int updateUserHpOnlyTx(String userName, String roomName, int newHpCur) {
    	return botNewDAO.updateUserHpOnly(userName, roomName,newHpCur);
    }
    @Override
    public int updateUserTargetMonTx(String userName, String roomName, int newMonNo) {
    	return botNewDAO.updateUserTargetMon(userName, roomName,newMonNo);
    }
    
    @Override
    public int insertUserWithTargetTx(String userName, String roomName, int targetMonNo) {
    	return botNewDAO.insertUserWithTarget(userName, roomName,targetMonNo);
    }
}
