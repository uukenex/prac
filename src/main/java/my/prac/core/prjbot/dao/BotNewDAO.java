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


    int insertBotPointNew(HashMap<String,Object> map);

    // User / Battle
    User selectUser(@Param("userName") String userName, @Param("roomName") String roomName);
    OngoingBattle selectOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);
    AttackDeathStat selectAttackDeathStats(@Param("userName") String userName, @Param("roomName") String roomName);
    List<KillStat> selectKillStats(@Param("userName") String userName, @Param("roomName") String roomName);

    List<Monster> selectAllMonsters();
    Monster selectMonsterByNo(@Param("monNo") int monNo);
    Monster selectMonsterByName(@Param("monName") String monName);

    Timestamp selectLastAttackTime(@Param("userName") String userName, @Param("roomName") String roomName);

    int updateUserAfterBattle(
        @Param("userName") String userName, @Param("roomName") String roomName,
        @Param("newLv") int newLv, @Param("newExpCur") int newExpCur, @Param("newExpNext") int newExpNext,
        @Param("newHpCur") int newHpCur, @Param("newHpMax") int newHpMax,
        @Param("newAtkMin") int newAtkMin, @Param("newAtkMax") int newAtkMax,
        @Param("critRate") int critRate, @Param("hpRegen") int hpRegen
    );

    int insertBattleLog(BattleLog log);

    int closeOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);

    int updateUserHpOnly(@Param("userName") String userName, @Param("roomName") String roomName, @Param("newHpCur") int newHpCur);

    int updateUserTargetMon(@Param("userName") String userName, @Param("roomName") String roomName, @Param("newMonNo") int newMonNo);

    int insertUserWithTarget(@Param("userName") String userName, @Param("roomName") String roomName, @Param("targetMonNo") int targetMonNo);

    Integer selectLatestLuckyYn(@Param("userName") String userName, @Param("roomName") String roomName);

    // Inventory & Items
    Integer selectItemIdByCode(@Param("itemCode") String itemCode);
    Integer selectItemIdByName(@Param("itemName") String itemName);

    int insertInventoryLog(HashMap<String,Object> p);

    List<HashMap<String,Object>> selectInventorySummary(@Param("userName") String userName, @Param("roomName") String roomName);

    Integer selectCurrentPoint(@Param("userName") String userName, @Param("roomName") String roomName);

    int insertPointRank(HashMap<String,Object> p);

    Integer selectItemSellPriceById(@Param("itemId") int itemId);

    List<HashMap<String,Object>> selectInventoryRowsForSale(
        @Param("userName") String userName, @Param("roomName") String roomName, @Param("itemId") int itemId);

    int updateInventoryDelByRowId(@Param("rid") String rid);
    int updateInventoryQtyByRowId(@Param("rid") String rid, @Param("newQty") int newQty);

    Integer selectInventoryQty(@Param("userName") String userName, @Param("roomName") String roomName, @Param("itemId") Integer itemId);

    // MARKET 합산 버프 (Number 안전)
    HashMap<String,Object> selectOwnedItemBuffTotals(
        @Param("userName") String userName, @Param("roomName") String roomName, @Param("onlyMarket") String onlyMarket);

    // 공격정보용 인벤토리 요약 (일반+MARKET+빛나는)
    List<HashMap<String,Object>> selectInventorySummaryAll(
        @Param("userName") String userName, @Param("roomName") String roomName);

    // 구매시 단가 조회 (by NAME)
    Integer selectItemPriceByName(@Param("itemName") String itemName);

    // 닉네임 보조검색
    List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map);
}
