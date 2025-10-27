package my.prac.core.prjbot.service;

import java.sql.Timestamp;
import java.util.HashMap;

import my.prac.api.loa.controller.BossAttackController.BattleLog;
import my.prac.api.loa.controller.BossAttackController.Monster;
import my.prac.api.loa.controller.BossAttackController.User;
import my.prac.api.loa.controller.BossAttackController.OngoingBattle;

public interface BotNewService {
	public int insertBotPointNew(HashMap<String,Object> map);
	
	User selectUser(String userName, String roomName);

    /** 진행중 전투(NOW_YN=1 묶음). 없으면 null */
    OngoingBattle selectOngoingBattle(String userName, String roomName);

    Monster selectMonsterByNo(int monNo);

    Timestamp selectLastAttackTime(String userName, String roomName);

    int updateUserAfterBattle(String userName, String roomName,
                              int newLv, int newExpCur, int newExpNext,
                              int newHpCur, int newHpMax, int newAtkMin, int newAtkMax);

    int insertBattleLog(BattleLog log);

    /** 처치 시 진행중 전투 종료: NOW_YN=1 → 0 */
    int closeOngoingBattle(String userName, String roomName);
	
} 

	
