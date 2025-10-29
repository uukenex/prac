package my.prac.core.prjbot.service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;

public interface BotNewService {
	public int insertBotPointNew(HashMap<String,Object> map);
	
	User selectUser(String userName, String roomName);

	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map);
    /** 진행중 전투(NOW_YN=1 묶음). 없으면 null */
    OngoingBattle selectOngoingBattle(String userName, String roomName);

    List<KillStat> selectKillStats(String userName, String roomName);
    AttackDeathStat selectAttackDeathStats(String userName, String roomName);
    
    List<Monster> selectAllMonsters();
    Monster selectMonsterByNo(int monNo);
    Monster selectMonsterByName(String monName);

    Timestamp selectLastAttackTime(String userName, String roomName);

    int updateUserAfterBattleTx(String userName, String roomName,
                              int newLv, int newExpCur, int newExpNext,
                              int newHpCur, int newHpMax, int newAtkMin, int newAtkMax
                              ,int critRate);

    int insertBattleLogTx(BattleLog log);

    /** 처치 시 진행중 전투 종료: NOW_YN=1 → 0 */
    int closeOngoingBattleTx(String userName, String roomName);
	
    int updateUserHpOnlyTx(String userName, String roomName, int newHpCur);
    
    int updateUserTargetMonTx(String userName, String roomName, int newMonNo);
    
    int insertUserWithTargetTx(String userName, String roomName, int targetMonNo);
} 

	
