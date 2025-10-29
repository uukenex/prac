package my.prac.core.prjbot.dao;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;

@Repository("core.prjbot.BotNewDAO")
public interface BotNewDAO {
	public int insertBotPointNew(HashMap<String, Object> map);
	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map);
	
	User selectUser(@Param("userName") String userName, @Param("roomName") String roomName);

	AttackDeathStat selectAttackDeathStats(@Param("userName") String userName,
            @Param("roomName") String roomName);
	
	/** 유저의 몬스터별 누적 처치 수 (많은 순) */
    List<KillStat> selectKillStats(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );
    
	List<Monster> selectAllMonsters();
	Monster selectMonsterByNo(@Param("monNo") int monNo);
	Monster selectMonsterByName(@Param("monName") String monName);
	
	OngoingBattle selectOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);

	Timestamp selectLastAttackTime(@Param("userName") String userName, @Param("roomName") String roomName);

	int updateUserAfterBattle(@Param("userName") String userName, @Param("roomName") String roomName,
			@Param("newLv") int newLv, @Param("newExpCur") int newExpCur, @Param("newExpNext") int newExpNext,
			@Param("newHpCur") int newHpCur, @Param("newHpMax") int newHpMax, @Param("newAtkMin") int newAtkMin,
			@Param("newAtkMax") int newAtkMax, @Param("critRate") int critRate,@Param("hpRegen") int hpRegen );

	int insertBattleLog(BattleLog log);

	int closeOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);
	
	int updateUserHpOnly(@Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("newHpCur") int newHpCur);
	
	int updateUserTargetMon(@Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("newMonNo") int newMonNo);
	
	int insertUserWithTarget(@Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("targetMonNo") int targetMonNo);
}
