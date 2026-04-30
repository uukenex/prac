package my.prac.core.prjbot.service;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    List<HashMap<String, Object>> selectMonsterKillsForView(String userName);

    List<HashMap<String, Object>> selectUserBattleLog(HashMap<String, Object> params);
    int selectUserBattleLogCount(HashMap<String, Object> params);

    HashMap<String,Object> selectLastBattleLog(HashMap<String,Object> map) throws Exception;
    Timestamp selectLastAttackTime(String userName, String roomName);
    int selectYesterdayAttackerCount();

    int updateUserAfterBattleTx(String userName, String roomName,
                              int newLv, int newExpCur, int newExpNext,
                              int newHpCur, int newHpMax, int newAtkMin, int newAtkMax
                              ,int critRate,int hpRegen );

    int insertBattleLogTx(BattleLog log);

    int insertBattleLogsBatch(List<BattleLog> logs);

    List<HashMap<String, Object>> selectAllItemIdMappings();

    /** 처치 시 진행중 전투 종료: NOW_YN=1 → 0 */
    int closeOngoingBattleTx(String userName, String roomName);
	
    int updateUserHpOnlyTx(String userName, String roomName, int newHpCur);
    
    int updateUserTargetMonTx(String userName, String roomName, int newMonNo);
    
    int insertUserWithTargetTx(String userName, String roomName, int targetMonNo);
    Integer selectItemIdByCode(String itemCode);
    Integer selectItemIdByName(String itemName);

    int insertInventoryLogTx(HashMap<String, Object> p);  // 트랜잭션 메서드 권장

    HashMap<String,Object>  selectCurrentPoint(String userName, String roomName);
    HashMap<String,Object>  selectTotalEarnedSp(String userName, String roomName);
    int insertPointRank(HashMap<String, Object> p);
    HashMap<String,Object> selectItemSellPriceById(int itemId);
    List<HashMap<String, Object>> selectInventoryRowsForSale(String u, String r, int id);
    List<HashMap<String, Object>> selectAllInventoryRowsForSale(String u, String R);
    
    List<HashMap<String, Object>> selectItemSellPriceList(Map<String, Object> param) throws Exception;
    
    void updateInventoryDelBatch(Map<String, Object> param) throws Exception;
    int updateInventoryQtyByRowId(String rowid, int newQty) ;
    Integer selectItemPriceByName(String itemName);

    /** 아이템 보너스 총합 */

    /** 인벤토리 요약(전체) */
    List<HashMap<String,Object>> selectInventorySummaryAll(String userName, String roomName);

    HashMap<String, Number> selectOwnedMarketBuffTotals(String user, String room);
    
    List<HashMap<String, Object>> selectMarketItems();
    HashMap<String, Object> selectItemDetailById(int itemId);
    
 // BotNewService
    List<HashMap<String,Object>> selectMarketItemsWithOwned(String userName, String roomName);
    Timestamp selectLastDamagedTime(String userName, String roomName) ;
    
 // Top 3 레벨 랭킹
    List<HashMap<String,Object>> selectTopLevelUsers();

    // 몬스터별 학살자(50킬 이상, 1위/동률)
    List<HashMap<String,Object>> selectKillLeadersByMonster();

    // 최초 토벌자
    List<HashMap<String,Object>> selectFirstClearInfo();
    
    int selectPointRankCountByCmdUserInRoom(String roomName, String userName, String cmd);
    
    public List<HashMap<String,Object>> selectAchievementsByUser(String userName,String roomName);
    
    
    List<HashMap<String,Object>> selectDailyAttackCounts(String userName, String roomName) throws Exception;
    HashMap<String,Integer> selectBattleCountByUser(String userName, String roomName) throws Exception;
    // [OPT-HUNTER] selectAttackDeathStats + selectBattleCountByUser 통합
    List<HashMap<String,Object>> selectBattleStatsByJob(String userName);
    int updateUserJobAndChangeDate(String userName, String roomName, String job);
    
    List<HashMap<String,Object>> selectRisingStarsTop5Last6h();
    List<HashMap<String,Object>> selectMaxDamageTop5();
    List<HashMap<String,Object>> selectOngoingChallengesForUnclearedBosses();
    List<HashMap<String,Object>> selectSpAndAtkRanking() throws Exception;
    
    void clearRoomBuff();

    HashMap<String,Object> selectDosaBuffInfo();
    
    List<HashMap<String, Object>> selectAchievementCountRanking();
    List<AchievementCount> selectAchvCountsGlobal(String userName,String roomName);
    List<AchievementCount> selectAchvCountsGlobalAll();
    public void execSPMsgTest(HashMap<String,Object> map) throws Exception;
    public void execSPPatchNoteTest(HashMap<String,Object> map) throws Exception;
    
    
    List<Integer> selectBagRewardItemIdsUserNotOwned( HashMap<String,Object> param );
    
    List<BagLog> selectRecentBagDrops();
    List<BagRewardLog> selectRecentBagRewards();
    int selectBagOpenSpCount(String userName, String roomName);
    // 상점/소비로 삭제된 인벤토리 누적 수량
    int selectInventorySoldCount(String userName, String roomName);
    
    List<HashMap<String, Object>> selectTotalGainCountByGainType(String userName, String roomName);
    
    List<HashMap<String,Object>> selectJobSkillUseCountAllJobs(String userName, String roomName);
    List<HashMap<String,Object>> selectSpecialBuffAchvStats(String userName);

    HashMap<String, Object> selectTodayDailyBuff(String userName, String roomName) throws Exception;
    int upsertTodayDailyBuff(String userName, String roomName, int atkBonus, int criDmgBonus) throws Exception;
    
    int countTodayJobMasterAll() throws Exception;
    int createTodayJobMastersFromYesterdayAll() throws Exception;
    int selectIsTodayJobMasterAll(String userName, String job) throws Exception;
    List<HashMap<String, Object>> selectTodayJobMastersAll();
    
    boolean isReturnUser(String userName);

    List<HashMap<String, Object>> selectTotalDropItems(String userName);

    List<HashMap<String,Object>> selectActiveSetBonuses(String userName);

    List<HashMap<String,Object>> selectAllSetBonusDefs();

    /** 유저별 중복 액션(구매/판매) 방지용 락 */
    boolean tryAcquireUserActionLock(String userName);
    void releaseUserActionLock(String userName);

    // [6-1] 헬보스/직업마스터/룰렛/학살자 업적 관련
    int selectHellBossAttackCount(String userName);
    int selectHellBossClearCount(String userName);
    List<HashMap<String, Object>> selectJobMasterSeasons(String userName);
    HashMap<String, Object> selectMaxDailyBuffStats(String userName);
    List<HashMap<String, Object>> selectSlayerSeasonRank(HashMap<String, Object> params);
    List<HashMap<String, Object>> selectKillLeadersByMonsterSeason(HashMap<String, Object> params);

    public boolean isNightmareMode(String userName, String roomName);
    public int getNightmareYn(String userName, String roomName);
    public int setNightmareMode(String userName, String roomName, int nightmareYn);
    public boolean isNightmareUnlocked(String userName);
    public boolean isHellUnlocked(String userName);
    HashMap<String,Object> selectHeavenItemBuff(String userName);
    List<Integer> selectItemIdsByType(String itemType);
    List<HashMap<String, Object>> selectHellRewardItemsWithOwnCount();
    void insertGpRecord(HashMap<String, Object> param);
    double selectGpBalance(String userName);
    List<HashMap<String, Object>> selectGpRanking();
    List<Integer> selectBossItemIds();
    
    HashMap<String,Object> lockMacroUser(String userName);
    
    int selectTotalBagAcquireCount(String userName);

    // [OPT3] INVENTORY 3개 쿼리 통합
    HashMap<String,Object> selectAchievementInventoryCounts(String userName);

    /** 물약 사용 횟수 조회 */
    int selectPotionUseCount(String userName);
    
    public int updateRandomBlessUser(String attacker,int count) ;
    public void clearBlessYn(String userName) ;
    
    int selectBagCountByItemId(String userName, String roomName, int itemId);
    int consumeBagBulkByItemIdTx(String userName, String roomName, int itemId, int count);
    
    int selectTodayBagCount(String userName);
    HashMap<String,Object> selectActiveSpecialBuff();

    int insertSpecialBuff(HashMap<String,Object> param);
    
    List<Integer> selectInventoryItemsByIds(String userName, String roomName, Collection<Integer> itemIds);

    /** 전체 아이템 + 유저 보유여부 (item_view 페이지용) */
    List<HashMap<String, Object>> selectAllItemsWithOwned(String userName);

    /** 부캐 리스트 조회 */
    List<String> selectAltCharList();
}

	
