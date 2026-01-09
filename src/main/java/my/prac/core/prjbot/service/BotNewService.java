package my.prac.core.prjbot.service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BagLog;
import my.prac.core.game.dto.BagRewardLog;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;

public interface BotNewService {
	
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
                              ,int critRate,int hpRegen );

    int insertBattleLogTx(BattleLog log);

    /** 처치 시 진행중 전투 종료: NOW_YN=1 → 0 */
    int closeOngoingBattleTx(String userName, String roomName);
	
    int updateUserHpOnlyTx(String userName, String roomName, int newHpCur);
    
    int updateUserTargetMonTx(String userName, String roomName, int newMonNo);
    
    int insertUserWithTargetTx(String userName, String roomName, int targetMonNo);
    Integer selectLatestLuckyYn(String userName, String roomName);
    Integer selectItemIdByCode(String itemCode);
    Integer selectItemIdByName(String itemName);

    int insertInventoryLogTx(HashMap<String, Object> p);  // 트랜잭션 메서드 권장

    List<HashMap<String,Object>> selectInventorySummary(String userName, String roomName);
    
    Integer selectCurrentPoint(String userName, String roomName);
    Integer selectTotalEarnedSp(String userName, String roomName);
    int insertPointRank(HashMap<String, Object> p);
    Integer selectItemSellPriceById(int itemId);
    List<HashMap<String, Object>> selectInventoryRowsForSale(String u, String r, int id);
    List<HashMap<String, Object>> selectAllInventoryRowsForSale(String u, String R);
    
    
    int updateInventoryDelByRowId(String rowid) ;
    int updateInventoryQtyByRowId(String rowid, int newQty) ;
    Integer selectInventoryQty(String userName, String roomName, Integer itemId);
    Integer selectItemPriceByName(String itemName);

    /** 아이템 보너스 총합 */

    /** 인벤토리 요약(전체) */
    List<HashMap<String,Object>> selectInventorySummaryAll(String userName, String roomName);

    HashMap<String, Number> selectOwnedMarketBuffTotals(String user, String room);
    
    List<HashMap<String,Object>> selectMarketItemsForSale(String userName, String roomName);
    HashMap<String,Object> selectMarketItemById(Integer itemId);
    HashMap<String,Object> selectMarketItemByNameOrCode(String token);
    Integer countOwnedMarketItem(String userName, String roomName, Integer itemId);
    
    List<HashMap<String, Object>> selectMarketItems();
    HashMap<String, Object> selectItemDetailById(int itemId);
    
 // BotNewService
    List<HashMap<String,Object>> selectMarketItemsWithOwned(String userName, String roomName);
    Integer selectHasOwnedMarketItem(String userName, String roomName, Integer itemId);

    Timestamp selectLastDamagedTime(String userName, String roomName) ;
    
 // Top 3 레벨 랭킹
    List<HashMap<String,Object>> selectTopLevelUsers();

    // 몬스터별 학살자(50킬 이상, 1위/동률)
    List<HashMap<String,Object>> selectKillLeadersByMonster();

    // 최초 토벌자
    List<HashMap<String,Object>> selectFirstClearInfo();
    
    int selectPointRankCountByCmdGlobal(String cmd);

    int selectPointRankCountByCmdUserInRoom(String roomName, String userName, String cmd);
    
    public List<HashMap<String,Object>> selectAchievementsByUser(String userName,String roomName);
    
    
    List<HashMap<String,Object>> selectDailyAttackCounts(String userName, String roomName) throws Exception;
    HashMap<String,Integer> selectBattleCountByUser(String userName, String roomName) throws Exception;
    int updateUserJobAndChangeDate(String userName, String roomName, String job);
    
    List<HashMap<String,Object>> selectRisingStarsTop5Last6h();
    List<HashMap<String,Object>> selectOngoingChallengesForUnclearedBosses();
    List<HashMap<String,Object>> selectSpAndAtkRanking() throws Exception;
    
    int selectRoomBuffCount(String roomName);
    void clearRoomBuff(String roomName);
    
    HashMap<String,Object> selectDosaBuffInfo(String roomName);
    
    int selectItemIdByRowId(String rid);
    
    List<HashMap<String, Object>> selectThiefKingRanking();

    List<HashMap<String, Object>> selectAchievementCountRanking();
    List<AchievementCount> selectAchvCountsGlobal(String userName,String roomName);
    List<AchievementCount> selectAchvCountsGlobalAll();
    HashMap<String, Object> selectActiveMonster(String userName, String roomName);
    
    public void execSPMsgTest(HashMap<String,Object> map) throws Exception;
    public void execSPPatchNoteTest(HashMap<String,Object> map) throws Exception;
    
    
    int selectBagCount(String userName, String roomName);

    int consumeOneBagTx(String userName, String roomName);
    int consumeBagBulkTx(String userName, String roomName,int bagCount);

    List<Integer> selectBagRewardItemIds();

    String selectItemNameById(int itemId);
    
    List<Integer> selectBagRewardItemIdsUserNotOwned(String userName, String roomName);
    
    List<BagLog> selectRecentBagDrops();
    List<BagRewardLog> selectRecentBagRewards();
    int selectBagOpenSpCount(String userName, String roomName);
    int selectRecentBagSpSum(String userName, String roomName);
    
 // 상점/소비로 삭제된 인벤토리 누적 수량
    int selectInventorySoldCount(String userName, String roomName);
    
    List<HashMap<String, Object>> selectTotalGainCountByGainType(String userName, String roomName);
    
    Integer selectJobSkillUseCount(String userName, String roomName, String job);
    List<HashMap<String,Object>> selectJobSkillUseCountAllJobs(String userName, String roomName);
    
    HashMap<String, Object> selectTodayDailyBuff(String userName, String roomName) throws Exception;
    int upsertTodayDailyBuff(String userName, String roomName, int atkBonus, int criDmgBonus) throws Exception;
    
    int countTodayJobMasterAll() throws Exception;
    int createTodayJobMastersFromYesterdayAll() throws Exception;
    int selectIsTodayJobMasterAll(String userName, String job) throws Exception;
    List<HashMap<String, Object>> selectTodayJobMastersAll();
    
    boolean isReturnUser(String userName);
    int selectBagRewardCap(String userName);
    
    List<HashMap<String, Object>> selectTotalDropItems(String userName);

} 

	
