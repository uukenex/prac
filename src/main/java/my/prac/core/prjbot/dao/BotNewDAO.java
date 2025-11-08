package my.prac.core.prjbot.dao;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    /** MARKET 아이템만 능력치 합 */
    HashMap<String, Number> selectOwnedMarketBuffTotals(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );

    /** 전체 아이템 능력치 합 (필요하면) */
    HashMap<String, Number> selectOwnedAllBuffTotals(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );
    
    /** 내 계정 기준으로 구매 가능한 MARKET 아이템 목록 + 보유 여부 */
    List<HashMap<String, Object>> selectMarketItemsForSale(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );

    /** ITEM_ID로 단일 아이템 조회 (MARKET 한정) */
    HashMap<String, Object> selectMarketItemById(@Param("itemId") Integer itemId);

    /** 이름/코드로 단일 아이템 조회 (MARKET 한정) */
    HashMap<String, Object> selectMarketItemByNameOrCode(@Param("token") String token);

    /** 보유 여부(활성 재고) */
    Integer countOwnedMarketItem(
        @Param("userName") String userName,
        @Param("roomName") String roomName,
        @Param("itemId") Integer itemId
    );
    
    /** ITEM_TYPE='MARKET' 목록 (구매 리스트 노출용) */
    List<HashMap<String, Object>> selectMarketItems();

    /** 아이템 단건 상세 (구매 처리/보너스 표기용) */
    HashMap<String, Object> selectItemDetailById(@Param("itemId") int itemId);
 // BotNewDAO
    List<HashMap<String,Object>> selectMarketItemsWithOwned(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );

    // 이미 소유한 MARKET 아이템인가? (del_yn='0' 남아있는지)
    Integer selectHasOwnedMarketItem(
        @Param("userName") String userName,
        @Param("roomName") String roomName,
        @Param("itemId")   Integer itemId
    );
    
    Timestamp selectLastDamagedTime(@Param("userName") String userName,
            @Param("roomName") String roomName);
    
 // Top 3 레벨 랭킹
    List<HashMap<String,Object>> selectTopLevelUsers();

    // 몬스터별 학살자(50킬 이상, 1위/동률)
    List<HashMap<String,Object>> selectKillLeadersByMonster();

    // 최초 토벌자
    List<HashMap<String,Object>> selectFirstClearInfo();
    
    
    int selectPointRankCountByCmdGlobal(
            @Param("cmd") String cmd);

    int selectPointRankCountByCmdUserInRoom(
            @Param("roomName") String roomName,
            @Param("userName") String userName,
            @Param("cmd") String cmd);
    
    List<HashMap<String,Object>> selectAchievementsByUser(@Param("userName") String userName);
    
    void updateUserJob(@Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("job") String job);
    
    int updateUserStatsForWarrior(
            @Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("hpMax") int hpMax,
            @Param("atkMin") int atkMin,
            @Param("atkMax") int atkMax);
    
    Timestamp selectJobChangeDate(@Param("userName") String userName,
            @Param("roomName") String roomName);

    int updateUserJobAndChangeDate(@Param("userName") String userName,
             @Param("roomName") String roomName,
             @Param("job") String job);
}
