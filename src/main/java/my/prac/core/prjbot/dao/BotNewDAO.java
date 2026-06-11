package my.prac.core.prjbot.dao;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BagLog;
import my.prac.core.game.dto.BagRewardLog;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;

@Repository("core.prjbot.BotNewDAO")
public interface BotNewDAO {

    // User / Battle
    User selectUser(@Param("userName") String userName, @Param("roomName") String roomName);
    OngoingBattle selectOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);
    AttackDeathStat selectAttackDeathStats(@Param("userName") String userName, @Param("roomName") String roomName);
    List<KillStat> selectKillStats(@Param("userName") String userName, @Param("roomName") String roomName);

    List<Monster> selectAllMonsters();
    Monster selectMonsterByNo(@Param("monNo") int monNo);
    Monster selectMonsterByName(@Param("monName") String monName);
    List<HashMap<String, Object>> selectUserBattleLog(HashMap<String, Object> params);
    int selectUserBattleLogCount(HashMap<String, Object> params);

    Timestamp selectLastAttackTime(@Param("userName") String userName, @Param("roomName") String roomName);

    int selectTodayHellKillCount(@Param("userName") String userName);
    int selectYesterdayAttackerCount();

    int updateUserAfterBattle(
        @Param("userName") String userName, @Param("roomName") String roomName,
        @Param("newLv") int newLv, @Param("newExpCur") long newExpCur, @Param("newExpNext") long newExpNext,
        @Param("newHpCur") int newHpCur, @Param("newHpMax") int newHpMax,
        @Param("newAtkMin") int newAtkMin, @Param("newAtkMax") int newAtkMax,
        @Param("critRate") int critRate, @Param("hpRegen") int hpRegen
    );

    int insertBattleLog(BattleLog log);

    int insertBattleLogBatch(List<BattleLog> logs);

    List<HashMap<String, Object>> selectAllItemIdMappings();

    int closeOngoingBattle(@Param("userName") String userName, @Param("roomName") String roomName);

    int updateUserHpOnly(@Param("userName") String userName, @Param("roomName") String roomName, @Param("newHpCur") int newHpCur);

    int updateUserTargetMon(@Param("userName") String userName, @Param("roomName") String roomName, @Param("newMonNo") int newMonNo);

    int insertUserWithTarget(@Param("userName") String userName, @Param("roomName") String roomName, @Param("targetMonNo") int targetMonNo);

    // Inventory & Items
    Integer selectItemIdByCode(@Param("itemCode") String itemCode);
    Integer selectItemIdByName(@Param("itemName") String itemName);

    int insertInventoryLog(HashMap<String,Object> p);

    HashMap<String,Object>  selectCurrentPoint(@Param("userName") String userName, @Param("roomName") String roomName);
    List<HashMap<String,Object>> selectPointLogRaw(@Param("userName") String userName);
    HashMap<String,Object>  selectTotalEarnedSp(@Param("userName") String userName, @Param("roomName") String roomName);
    List<HashMap<String,Object>> selectUserTotalSpComponents(@Param("userName") String userName);

    int insertPointRank(HashMap<String,Object> p);

    /** 유저당 1행 SP/GP 잔액 MERGE (insertPointRank/insertGpRecord 후 자동 호출) */
    int upsertPointNewRank(@Param("userName") String userName);

    HashMap<String,Object> selectItemSellPriceById(@Param("itemId") int itemId);

    List<HashMap<String,Object>> selectInventoryRowsForSale(
        @Param("userName") String userName, @Param("roomName") String roomName, @Param("itemId") int itemId);
    List<HashMap<String,Object>> selectAllInventoryRowsForSale(
    		@Param("userName") String userName, @Param("roomName") String roomName);

    
    List<HashMap<String, Object>> selectItemSellPriceList(Map<String, Object> param);
    
    
    void updateInventoryDelBatch(Map<String, Object> param);
    int updateInventoryQtyByRowId(@Param("rid") String rid, @Param("newQty") int newQty);

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


    /** ITEM_TYPE='MARKET' 목록 (구매 리스트 노출용) */
    List<HashMap<String, Object>> selectMarketItems();

    /** 아이템 단건 상세 (구매 처리/보너스 표기용) */
    HashMap<String, Object> selectItemDetailById(@Param("itemId") int itemId);
 // BotNewDAO
    List<HashMap<String,Object>> selectMarketItemsWithOwned(
        @Param("userName") String userName,
        @Param("roomName") String roomName
    );

    Timestamp selectLastDamagedTime(@Param("userName") String userName,
            @Param("roomName") String roomName);
    HashMap<String,Object> selectLastBattleLog(HashMap<String,Object> map);
    
 // Top 3 레벨 랭킹
    List<HashMap<String,Object>> selectTopLevelUsers();

    // 최초 토벌자
    List<HashMap<String,Object>> selectFirstClearInfo();
    
    
    int selectPointRankCountByCmdUserInRoom(
            @Param("roomName") String roomName,
            @Param("userName") String userName,
            @Param("cmd") String cmd);
    
    int selectHasHellClearAchv(String userName);
    List<HashMap<String,Object>> selectAchievementsByUser(@Param("userName") String userName,@Param("roomName") String roomName);
    
    List<HashMap<String,Object>> selectDailyAttackCounts(HashMap<String,Object> param) throws Exception;
    
    List<HashMap<String,Object>>  selectBattleCountByUser(HashMap<String,Object> param) throws Exception;

    // [OPT-HUNTER] selectAttackDeathStats + selectBattleCountByUser 통합
    List<HashMap<String,Object>> selectBattleStatsByJob(HashMap<String,Object> param);

    int updateUserJobAndChangeDate(@Param("userName") String userName,
             @Param("roomName") String roomName,
             @Param("job") String job);
    
 // 신규
    List<HashMap<String,Object>> selectRisingStarsTop5Last6h();
    List<HashMap<String,Object>> selectMaxDamageTop5();
    List<HashMap<String,Object>> selectOngoingChallengesForUnclearedBosses();
    public List<HashMap<String,Object>> selectSpAndAtkRanking();
    
    void clearRoomBuff();

    HashMap<String,Object> selectDosaBuffInfo();
    
    List<HashMap<String, Object>> selectAchievementCountRanking();
    List<AchievementCount> selectAchvCountsGlobal(@Param("userName")String userName,@Param("roomName") String roomName);
    List<AchievementCount> selectAchvCountsGlobalAll();

    public int execSPMsgTest(HashMap<String,Object> map) throws Exception;
    public int execSPPatchNoteTest(HashMap<String,Object> map) throws Exception;
    
    List<Integer> selectBagRewardItemIdsUserNotOwned(HashMap<String,Object> map);
    
	List<BagLog> selectRecentBagDrops();
	List<BagRewardLog> selectRecentBagRewards();
	int selectBagOpenSpCount(HashMap<String, Object> map);

	int selectInventorySoldCount(
	        @Param("userName") String userName,
	        @Param("roomName") String roomName);
	
	List<HashMap<String, Object>> selectTotalGainCountByGainType(@Param("userName") String userName,
	        @Param("roomName") String roomName);
	public List<HashMap<String,Object>> selectJobSkillUseCountAllJobs(HashMap<String,Object> param);
	public List<HashMap<String,Object>> selectSpecialBuffAchvStats(String userName);

	HashMap<String, Object> selectTodayDailyBuff(HashMap<String, Object> param) throws Exception;
	int upsertTodayDailyBuff(HashMap<String, Object> param) throws Exception;
	
	int countTodayJobMasterAll() throws Exception;
	int createTodayJobMastersFromYesterdayAll() throws Exception;
	int selectIsTodayJobMasterAll(HashMap<String,Object> param) throws Exception;
	
	List<HashMap<String, Object>> selectTodayJobMastersAll();
	
    String selectIsReturnUser(HashMap<String, Object> map);
    
    List<HashMap<String, Object>> selectTotalDropItems(String userName);
    List<HashMap<String, Object>> selectHellBoxStats(String userName);

    HashMap<String,Object> selectPendingHellBox(String userName);
    int upgradePendingHellBox(HashMap<String,Object> map);
    int confirmPendingHellBox(String userName);
    int decrementPendingHellBox(String userName);

    List<HashMap<String,Object>> selectActiveSetBonuses(@Param("userName") String userName);

    List<HashMap<String,Object>> selectAllSetBonusDefs();

    int selectHellBossAttackCount(String userName);
    int selectHellBossClearCount(String userName);
    List<HashMap<String, Object>> selectJobMasterSeasons(String userName);
    HashMap<String, Object> selectMaxDailyBuffStats(String userName);
    List<HashMap<String, Object>> selectSlayerSeasonRank(HashMap<String, Object> params);
    List<HashMap<String, Object>> selectKillLeadersByMonsterSeason(HashMap<String, Object> params);

    Integer selectNightmareYn(@Param("userName") String userName, @Param("roomName") String roomName);
    public int updateNightmareYn(HashMap<String,Object> map);
    int selectNormalKillCountByMonNo(@Param("userName") String userName, @Param("monNo") int monNo);
    int selectNmDarkKillMonCount(@Param("userName") String userName);
    HashMap<String,Object> selectHellUnlockStats(@Param("userName") String userName);
    HashMap<String,Object> selectHeavenItemBuff(@Param("userName") String userName);
    List<Integer> selectItemIdsByType(@Param("itemType") String itemType);
    List<HashMap<String, Object>> selectHellRewardItemsWithOwnCount();
    void insertGpRecord(HashMap<String, Object> param);
    double selectGpBalance(@Param("userName") String userName);
    double selectUserTotalEarnedGp(@Param("userName") String userName);
    List<HashMap<String, Object>> selectGpRanking();

    void lockMacroUser(HashMap<String, Object> map);
    
    int selectTotalBagAcquireCount(String userName);

    // [OPT3] INVENTORY 3개 쿼리 통합
    HashMap<String,Object> selectAchievementInventoryCounts(@Param("userName") String userName);

    /** 물약 사용 횟수 조회 */
    int selectPotionUseCount(@Param("userName") String userName);

    List<String> selectRandomBlessTargets(HashMap<String, Object> map);

    int updateBlessYn(String userName);

    public int clearBlessYn(String userName) ;
    
    HashMap<String,Object> selectTodayBagCounts(HashMap<String,Object> param);
    HashMap<String,Object> selectOpenBagCounts(String userName);

    int selectBagCountByItemId(HashMap<String,Object> param);

    int consumeBagBulkByItemId(HashMap<String,Object> param);

    int selectBagCountByItemIdAndGainType(HashMap<String,Object> param);
    int consumeBagBulkByItemIdAndGainType(HashMap<String,Object> param);
    
    int selectTodayBagCount(String userName);
    HashMap<String,Object> selectActiveSpecialBuff();

    int insertSpecialBuff(HashMap<String,Object> param);
    
    List<Integer> selectInventoryItemsByIds(
            @Param("userName") String userName,
            @Param("roomName") String roomName,
            @Param("itemIds") Collection<Integer> itemIds
    );

    /** [헬보스 강화] 보스아이템 총 보유수량 맵 조회 (item_id→총qty) */
    List<HashMap<String, Object>> selectBossHellItemTotalQty(@Param("userName") String userName);

    /** 전체 아이템 + 유저 보유여부 (item_view 페이지용) */
    List<HashMap<String, Object>> selectAllItemsWithOwned(
            @Param("userName") String userName
    );

    /** 부캐 리스트 조회 */

    /** 출석체크: 오늘 출석 여부 (1=출석, 0=미출석) */
    int selectTodayAttendYn(HashMap<String,Object> param);
    /** 출석체크: 마지막 출석일 갱신 */
    int updateLastAttendDate(HashMap<String,Object> param);
    List<String> selectAltCharList();

    /** 보스현황: 현재 진행중 보스 글로벌 누적 데미지 */
    HashMap<String, Object> selectCurrentBossState();
    /** 보스현황: 마지막으로 죽은 보스 시간 */
    HashMap<String, Object> selectLastBossKillTime();
    /** 보스현황: 최근 배틀로그 (글로벌 TOP20) */
    List<HashMap<String, Object>> selectCurrentBossRecentLog();
    // ── 인벤토리 OLD 이관 (매월 배치) ──────────────────────────────
    /** 이관 대상 그룹 조회 (item_id < 100, del_yn='1', insert_date < cutoff) */
    List<HashMap<String, Object>> selectInventoryOldMigrateTarget(HashMap<String, Object> param);

    /** OLD 테이블 QTY 증가 UPDATE */
    int updateInventoryOldQty(HashMap<String, Object> param);

    /** OLD 테이블 신규 INSERT */
    int insertInventoryOld(HashMap<String, Object> param);

    /** 이관 완료된 메인 테이블 데이터 삭제 */
    int deleteInventoryMigrated(HashMap<String, Object> param);

    /** 유물 아이템 총 개수 (일반/나메 구분) */
    HashMap<String, Object> selectRelicTotalCounts();

    /** 장비 랭킹용 전체 유저 기본 스탯 조회 */
    List<HashMap<String, Object>> selectAllUsersForRank();

    /** 직업레벨 단건 조회 */
    HashMap<String,Object> selectJobLevel(HashMap<String,Object> param);
    /** 유저의 전체 직업레벨 목록 조회 */
    List<HashMap<String,Object>> selectJobLevels(String userName);
    /** 전 직업 레벨 합산 조회 */
    int selectTotalJobLv(String userName);
    /** 직업레벨/킬수 UPSERT */
    int upsertJobLevel(HashMap<String,Object> param);

    // ── 경험치판매 ──────────────────────────────────────────────────────────
    HashMap<String,Object> selectExpSellStats(@Param("userName") String userName);
    int upsertExpSellStats(HashMap<String,Object> param);
    int updateExpCurOnly(@Param("userName") String userName,
                         @Param("roomName") String roomName,
                         @Param("expCur")   long expCur);
    // ── 실시간 카운터 테이블 ───────────────────────────────────────────────────
    int upsertMonKillStat(HashMap<String,Object> param);
    int upsertHellBossClearForParticipants(HashMap<String,Object> param);
    int upsertBattleJobStat(HashMap<String,Object> param);
    int upsertBattleBuffStat(HashMap<String,Object> param);

    // ── 초기 이관 ─────────────────────────────────────────────────────────────
    int migrateBattleLogToKillStat();
    int migrateBattleLogToJobStat();
    int migrateBattleLogToBuffStat();
    int migrateLastMonthToJobStat();
    int backupOldBattleLog();
    int deleteLastMonthBattleLogBatch(@Param("batchSize") int batchSize);
}
