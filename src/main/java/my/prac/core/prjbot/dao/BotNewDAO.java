package my.prac.core.prjbot.dao;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import my.prac.api.loa.controller.BossAttackController.BattleLog;
import my.prac.api.loa.controller.BossAttackController.Monster;
import my.prac.api.loa.controller.BossAttackController.User;

@Repository("core.prjbot.BotNewDAO")
public interface BotNewDAO {
	public int insertBotPointNew(HashMap<String,Object> map);
	
	 // TBOT_POINT_NEW_USER
    User selectUser(@Param("userName") String userName,
                    @Param("roomName") String roomName);

    // tbot_point_new_mon_info
    Monster selectMonsterByNo(@Param("userName") String userName,
            				  @Param("roomName") String roomName);

    // tbot_point_new_battle_log
    Timestamp selectLastAttackTime(@Param("userName") String userName,
                                   @Param("roomName") String roomName);

    // 업데이트/로그
    int updateUserAfterBattle(@Param("userName") String userName,
                              @Param("roomName") String roomName,
                              @Param("newLv") int newLv,
                              @Param("newExpCur") int newExpCur,
                              @Param("newExpNext") int newExpNext,
                              @Param("newHpCur") int newHpCur,
                              @Param("newHpMax") int newHpMax,
                              @Param("newAtkMin") int newAtkMin,
                              @Param("newAtkMax") int newAtkMax);

    int insertBattleLog(BattleLog log);
}

