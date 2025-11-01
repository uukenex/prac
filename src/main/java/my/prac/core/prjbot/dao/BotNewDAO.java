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
	
	/**
     * 최근(now_yn=1) 배틀로그의 lucky_yn (0/1)을 반환. 없으면 null.
     * 내부적으로 메모리 캐시를 먼저 확인한 후, 캐시에 없으면 DB 조회.
     */
    Integer selectLatestLuckyYn(String userName, String roomName);

    /** (옵션) 캐시 갱신용 헬퍼: persist 직후 호출하면 캐시 최신화됨 */
    void updateLuckyCache(String userName, String roomName, Integer luckyYn);
    
    /** 아이템 ID 조회 (코드/이름) */
    Integer selectItemIdByCode(@Param("itemCode") String itemCode);
    Integer selectItemIdByName(@Param("itemName") String itemName);

    /** 인벤토리 로그 적재 (드랍/구매/이벤트 등 공용) */
    int insertInventoryLog(HashMap<String, Object> p);

    /** 유저 인벤토리 합산 요약 (아이템별 보유 수량) */
    List<HashMap<String,Object>> selectInventorySummary(@Param("userName") String userName,
                                                        @Param("roomName") String roomName);
    
    Integer selectCurrentPoint(
    	    @Param("userName") String userName,
    	    @Param("roomName") String roomName
    	);
    
    
    
 // 판매용
    List<HashMap<String,Object>> selectInventoryRowsForSale(
        @Param("userName") String userName,
        @Param("roomName") String roomName,
        @Param("itemId")   int itemId
    );
    int updateInventoryDelByRowId(@Param("rowid") String rowid);
    int updateInventoryQtyByRowId(@Param("rowid") String rowid, @Param("newQty") int newQty);
    Integer selectInventoryQty(@Param("userName") String userName,
                               @Param("roomName") String roomName,
                               @Param("itemId")   int itemId);
    Integer selectItemSellPriceById(@Param("itemId") int itemId);

    // 포인트 적립
    int insertPointRank(HashMap<String,Object> p);

    
}
