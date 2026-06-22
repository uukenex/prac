package my.prac.core.prjbot.service.impl;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BagLog;
import my.prac.core.game.dto.BagRewardLog;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotNewDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotNewService;

@Service("core.prjbot.BotNewService")
public class BotNewServiceImpl implements BotNewService {

	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewDAO")
	BotNewDAO botNewDAO;

	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;

	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map){
		return botNewDAO.selectParam1ToNewUserSearch(map);
	}
	

	@Override
    public User selectUser(String userName, String roomName) {
        return botNewDAO.selectUser(userName, roomName);
    }

    @Override
    public OngoingBattle selectOngoingBattle(String userName, String roomName) {
        return botNewDAO.selectOngoingBattle(userName, roomName);
    }
    
    @Override
    public AttackDeathStat selectAttackDeathStats(String userName, String roomName) {
        return botNewDAO.selectAttackDeathStats(userName, roomName);
    }

    @Override
    public List<KillStat> selectKillStats(String userName, String roomName){
    	return botNewDAO.selectKillStats(userName,roomName);
    }
    
    @Override
    public List<Monster> selectAllMonsters(){
    	return botNewDAO.selectAllMonsters();
    }
    @Override
    public List<HashMap<String, Object>> selectUserBattleLog(HashMap<String, Object> params) {
        return botNewDAO.selectUserBattleLog(params);
    }
    @Override
    public int selectUserBattleLogCount(HashMap<String, Object> params) {
        return botNewDAO.selectUserBattleLogCount(params);
    }
    @Override
    public Monster selectMonsterByNo(int monNo) {
        return botNewDAO.selectMonsterByNo(monNo);
    }
    @Override
    public Monster selectMonsterByName(String monName) {
    	return botNewDAO.selectMonsterByName(monName);
    }

    @Override
    public Timestamp selectLastAttackTime(String userName, String roomName) {
        return botNewDAO.selectLastAttackTime(userName, roomName);
    }

    @Override
    public int selectTodayHellKillCount(String userName) {
        return botNewDAO.selectTodayHellKillCount(userName);
    }

    @Override
    public int countHellBagsInBuffPeriod(String userName, int itemId, java.util.Date buffStart) {
        return botNewDAO.countHellBagsInBuffPeriod(userName, itemId, buffStart);
    }

    @Override
    public int selectYesterdayAttackerCount() {
        return botNewDAO.selectYesterdayAttackerCount();
    }

    @Override
    public int updateUserAfterBattleTx(String userName, String roomName, int newLv, long newExpCur, long newExpNext,
                                     int newHpCur, int newHpMax, int newAtkMin, int newAtkMax,int critRate,int hpRegen ) {
        return botNewDAO.updateUserAfterBattle(userName, roomName, newLv, newExpCur, newExpNext,
                                            newHpCur, newHpMax, newAtkMin, newAtkMax, critRate,hpRegen );
    }

    @Override
    public int insertBattleLogTx(BattleLog log) {
        return botNewDAO.insertBattleLog(log);
    }

    @Override
    public int insertBattleLogsBatch(List<BattleLog> logs) {
        if (logs == null || logs.isEmpty()) return 0;
        return botNewDAO.insertBattleLogBatch(logs);
    }

    @Override
    public List<HashMap<String, Object>> selectAllItemIdMappings() {
        return botNewDAO.selectAllItemIdMappings();
    }

    @Override
    public int closeOngoingBattleTx(String userName, String roomName) {
        return botNewDAO.closeOngoingBattle(userName, roomName);
    }
    @Override
    public int updateUserHpOnlyTx(String userName, String roomName, int newHpCur) {
    	return botNewDAO.updateUserHpOnly(userName, roomName,newHpCur);
    }
    @Override
    public int updateUserTargetMonTx(String userName, String roomName, int newMonNo) {
    	return botNewDAO.updateUserTargetMon(userName, roomName,newMonNo);
    }
    
    @Override
    public int insertUserWithTargetTx(String userName, String roomName, int targetMonNo) {
    	return botNewDAO.insertUserWithTarget(userName, roomName,targetMonNo);
    }
    
	@Override
	public Integer selectItemIdByCode(String itemCode) {
		return botNewDAO.selectItemIdByCode(itemCode);
	}

	@Override
	public Integer selectItemIdByName(String itemName) {
		return botNewDAO.selectItemIdByName(itemName);
	}

	@Override
	public int insertInventoryLogTx(HashMap<String, Object> p) {
		return botNewDAO.insertInventoryLog(p);
	}

	@Override
	public HashMap<String,Object>  selectCurrentPoint(String userName, String roomName) {
	    return botNewDAO.selectCurrentPoint(userName, roomName);
	}

	@Override
	public List<HashMap<String,Object>> selectPointLogRaw(String userName) {
	    return botNewDAO.selectPointLogRaw(userName);
	}
	@Override
	public HashMap<String,Object>  selectTotalEarnedSp(String userName, String roomName) {
		return botNewDAO.selectTotalEarnedSp(userName, roomName);
	}

	@Override
	public List<HashMap<String,Object>> selectUserTotalSpComponents(String userName) {
		return botNewDAO.selectUserTotalSpComponents(userName);
	}

	@Override
	public int insertPointRank(HashMap<String, Object> p) {
		int result = botNewDAO.insertPointRank(p);
		try {
			String uName = p.get("userName") != null ? p.get("userName").toString() : null;
			if (uName != null) botNewDAO.upsertPointNewRank(uName);
		} catch (Exception ignore) {}
		return result;
	}

	@Override
	public void upsertPointNewRank(String userName) {
		botNewDAO.upsertPointNewRank(userName);
	}

	@Override
	public HashMap<String,Object> selectItemSellPriceById(int itemId) {
		return botNewDAO.selectItemSellPriceById(itemId);
	}

	@Override
	public List<HashMap<String, Object>> selectInventoryRowsForSale(String u, String r, int id) {
		return botNewDAO.selectInventoryRowsForSale(u, r, id);
	}
	@Override
	public List<HashMap<String, Object>> selectAllInventoryRowsForSale(String u, String r) {
		return botNewDAO.selectAllInventoryRowsForSale(u, r);
	}
	
	@Override
	public List<HashMap<String, Object>> selectItemSellPriceList(Map<String, Object> param) throws Exception {
	    return botNewDAO.selectItemSellPriceList(param);
	}

	@Override
	public void updateInventoryDelBatch(Map<String, Object> param) throws Exception{
		botNewDAO.updateInventoryDelBatch(param);
	}
	@Override
	public int updateInventoryQtyByRowId(String rowid, int newQty) {
		return botNewDAO.updateInventoryQtyByRowId(rowid, newQty);
	}
	@Override
	public List<HashMap<String, Object>> selectInventorySummaryAll(String user, String room) {
	    return botNewDAO.selectInventorySummaryAll(user, room);
	}

	@Override
	public Integer selectItemPriceByName(String itemName) {
	    return botNewDAO.selectItemPriceByName(itemName);
	}
	
	@Override
	public HashMap<String, Number> selectOwnedMarketBuffTotals(String user, String room) {
	    return botNewDAO.selectOwnedMarketBuffTotals(user, room);
	}
    @Override
    public List<HashMap<String, Object>> selectMarketItems() {
        return botNewDAO.selectMarketItems();
    }

    @Override
    public HashMap<String, Object> selectItemDetailById(int itemId) {
        return botNewDAO.selectItemDetailById(itemId);
    }
    
    // BotNewServiceImpl
    @Override
    public List<HashMap<String,Object>> selectMarketItemsWithOwned(String userName, String roomName) {
        return botNewDAO.selectMarketItemsWithOwned(userName, roomName);
    }
    @Override
    public Timestamp selectLastDamagedTime(String userName, String roomName) {
        return botNewDAO.selectLastDamagedTime(userName, roomName);
    }
    @Override
    public HashMap<String,Object> selectLastBattleLog(HashMap<String,Object> map) throws Exception{
    	return botNewDAO.selectLastBattleLog(map);
    }

    

    @Override
    public List<HashMap<String, Object>> selectTopLevelUsers() {
        return botNewDAO.selectTopLevelUsers();
    }
    @Override
    public List<HashMap<String, Object>> selectFirstClearInfo() {
        return botNewDAO.selectFirstClearInfo();
    }
    
    @Override
    public int selectPointRankCountByCmdUserInRoom(String roomName, String userName, String cmd) {
    	return botNewDAO.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
    }
    @Override
    public boolean hasHellClearAchv(String userName) {
        return botNewDAO.selectHasHellClearAchv(userName) > 0;
    }

    @Override
    public List<HashMap<String,Object>> selectAchievementsByUser(String userName,String roomName) {
        return botNewDAO.selectAchievementsByUser(userName, roomName);
    }
    @Override
    public List<HashMap<String, Object>> selectDailyAttackCounts(String userName, String roomName) throws Exception {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        return botNewDAO.selectDailyAttackCounts(param);
    }
    @Override
    public HashMap<String,Integer> selectBattleCountByUser(String userName, String roomName) throws Exception {
    	HashMap<String,Object> param = new HashMap<String,Object>();
        param.put("userName", userName);
        param.put("roomName", roomName);

        List<HashMap<String,Object>> list = botNewDAO.selectBattleCountByUser(param);

        HashMap<String, Integer> map = new HashMap<String, Integer>();
        if (list != null) {
            for (HashMap<String,Object> row : list) {
                String job = Objects.toString(row.get("JOB"), "").trim();
                if (job.isEmpty()) continue;
                Number n = (Number) row.get("CNT");
                int cnt = (n == null ? 0 : n.intValue());
                map.put(job, cnt);
            }
        }
        return map;
    }

    @Override
    // [OPT-HUNTER] selectAttackDeathStats + selectBattleCountByUser 통합 (BATTLE_LOG 1회 스캔)
    public List<HashMap<String,Object>> selectBattleStatsByJob(String userName) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        return botNewDAO.selectBattleStatsByJob(param);
    }

    @Override
    public int updateUserJobAndChangeDate(String userName, String roomName, String job) {
        return botNewDAO.updateUserJobAndChangeDate(userName, roomName, job);
    }
    
    @Override
    public List<HashMap<String,Object>> selectRisingStarsTop5Last6h() {
        return botNewDAO.selectRisingStarsTop5Last6h();
    }
    @Override
    public List<HashMap<String,Object>> selectMaxDamageTop5() {
    	return botNewDAO.selectMaxDamageTop5();
    }

    @Override
    public List<HashMap<String,Object>> selectOngoingChallengesForUnclearedBosses() {
        return botNewDAO.selectOngoingChallengesForUnclearedBosses();
    }
    @Override
    public List<HashMap<String, Object>> selectSpAndAtkRanking() throws Exception {
        return botNewDAO.selectSpAndAtkRanking();
    }
    
    @Override
    public void clearRoomBuff() {
        botNewDAO.clearRoomBuff();
    }

    @Override
    public HashMap<String,Object> selectDosaBuffInfo() {
        return botNewDAO.selectDosaBuffInfo();
    }
    
    @Override
    public List<HashMap<String, Object>> selectAchievementCountRanking(){
    	return botNewDAO.selectAchievementCountRanking();
    }
    @Override
    public List<AchievementCount> selectAchvCountsGlobal(String userName,String roomName){
        return botNewDAO.selectAchvCountsGlobal(userName,roomName);
    }
    
    @Override
    public List<AchievementCount> selectAchvCountsGlobalAll() {
        return botNewDAO.selectAchvCountsGlobalAll();
    }
    @Override
    public void execSPMsgTest(HashMap<String,Object> map) throws Exception{
    	 botNewDAO.execSPMsgTest(map);
    }
    
    @Override
    public void execSPPatchNoteTest(HashMap<String,Object> map) throws Exception{
    	botNewDAO.execSPPatchNoteTest(map);
    }
    
    
    
    
    @Override
    public List<Integer> selectBagRewardItemIdsUserNotOwned(HashMap<String,Object> param ) {
        return botNewDAO.selectBagRewardItemIdsUserNotOwned(param);
    }

    @Override
    public List<BagLog> selectRecentBagDrops() {
        return botNewDAO.selectRecentBagDrops();
    }
    @Override
    public List<BagRewardLog> selectRecentBagRewards() {
    	return botNewDAO.selectRecentBagRewards();
    }
    @Override
    public int selectBagOpenSpCount(String userName, String roomName) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        return botNewDAO.selectBagOpenSpCount(param);
    }
    @Override
    public int selectInventorySoldCount(String userName, String roomName) {
        return botNewDAO.selectInventorySoldCount(userName, roomName);
    }
    
    @Override
    public List<HashMap<String, Object>> selectTotalGainCountByGainType(String userName, String roomName) {
        return botNewDAO.selectTotalGainCountByGainType(userName, roomName);
    }
    
    @Override
    public List<HashMap<String,Object>> selectJobSkillUseCountAllJobs(String userName, String roomName) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("roomName", roomName);
        return botNewDAO.selectJobSkillUseCountAllJobs(p);
    }

    @Override
    public List<HashMap<String,Object>> selectSpecialBuffAchvStats(String userName) {
        return botNewDAO.selectSpecialBuffAchvStats(userName);
    }

    @Override
    public HashMap<String, Object> selectTodayDailyBuff(String userName, String roomName) throws Exception {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("roomName", roomName);
        return botNewDAO.selectTodayDailyBuff(p);
    }

    @Override
    public int upsertTodayDailyBuff(String userName, String roomName, int atkBonus, int criDmgBonus) throws Exception {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("roomName", roomName);
        p.put("atkBonus", atkBonus);
        p.put("criDmgBonus", criDmgBonus);
        return botNewDAO.upsertTodayDailyBuff(p);
    }
    @Override
    public int countTodayJobMasterAll() throws Exception {
        return botNewDAO.countTodayJobMasterAll();
    }

    @Override
    public int createTodayJobMastersFromYesterdayAll() throws Exception {
        return botNewDAO.createTodayJobMastersFromYesterdayAll();
    }

    @Override
    public int selectIsTodayJobMasterAll(String userName, String job) throws Exception {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("job", job);
        return botNewDAO.selectIsTodayJobMasterAll(p);
    }
    @Override
    public List<HashMap<String, Object>> selectTodayJobMastersAll(){
    	return botNewDAO.selectTodayJobMastersAll();
    }
    
    @Override
    public boolean isReturnUser(String userName) {

        HashMap<String, Object> map = new HashMap<>();
        map.put("userName", userName);

        try {
            String yn = botNewDAO.selectIsReturnUser(map);
            return "Y".equalsIgnoreCase(yn);
        } catch (Exception e) {
            // 복귀자 판정 실패 시 보너스 미적용
            return false;
        }
    }
    
    @Override
    public List<HashMap<String, Object>> selectTotalDropItems(String userName){
    	 return botNewDAO.selectTotalDropItems(userName);
    }

    @Override
    public List<HashMap<String, Object>> selectHellBoxStats(String userName) {
        return botNewDAO.selectHellBoxStats(userName);
    }

    @Override
    public HashMap<String,Object> selectPendingHellBox(String userName) {
        return botNewDAO.selectPendingHellBox(userName);
    }

    @Override
    public void upgradePendingHellBox(HashMap<String,Object> map) {
        botNewDAO.upgradePendingHellBox(map);
    }

    @Override
    public void confirmPendingHellBox(String userName) {
        botNewDAO.confirmPendingHellBox(userName);
    }

    @Override
    public void decrementPendingHellBox(String userName) {
        botNewDAO.decrementPendingHellBox(userName);
    }

    @Override
    public List<HashMap<String,Object>> selectAllPendingHellBoxGrouped(String userName) {
        return botNewDAO.selectAllPendingHellBoxGrouped(userName);
    }

    @Override
    public void confirmAllPendingHellBoxes(String userName) {
        botNewDAO.confirmAllPendingHellBoxes(userName);
    }

    @Override
    public List<HashMap<String,Object>> selectActiveSetBonuses(String userName) {
        return botNewDAO.selectActiveSetBonuses(userName);
    }

    @Override
    public List<HashMap<String,Object>> selectAllSetBonusDefs() {
        return botNewDAO.selectAllSetBonusDefs();
    }

    private static final ConcurrentHashMap<String, Boolean> USER_ACTION_LOCKS = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquireUserActionLock(String userName) {
        return USER_ACTION_LOCKS.putIfAbsent(userName, Boolean.TRUE) == null;
    }

    @Override
    public void releaseUserActionLock(String userName) {
        USER_ACTION_LOCKS.remove(userName);
    }

    public int selectHellBossAttackCount(String userName) {
        return botNewDAO.selectHellBossAttackCount(userName);
    }
    public int selectHellBossClearCount(String userName) {
        return botNewDAO.selectHellBossClearCount(userName);
    }
    public List<HashMap<String, Object>> selectJobMasterSeasons(String userName) {
        return botNewDAO.selectJobMasterSeasons(userName);
    }
    public HashMap<String, Object> selectMaxDailyBuffStats(String userName) {
        return botNewDAO.selectMaxDailyBuffStats(userName);
    }
    public List<HashMap<String, Object>> selectSlayerSeasonRank(HashMap<String, Object> params) {
        return botNewDAO.selectSlayerSeasonRank(params);
    }

    public List<HashMap<String, Object>> selectKillLeadersByMonsterSeason(HashMap<String, Object> params) {
        return botNewDAO.selectKillLeadersByMonsterSeason(params);
    }

    public boolean isNightmareMode(String userName, String roomName) {
	    Integer v = botNewDAO.selectNightmareYn(userName, roomName);
	    return v != null && v >= 1;
	}

	public int getNightmareYn(String userName, String roomName) {
	    Integer v = botNewDAO.selectNightmareYn(userName, roomName);
	    return v != null ? v : 0;
	}
    
    public int setNightmareMode(String userName, String roomName, int nightmareYn) {
    	HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("roomName", roomName);
        p.put("nightmareYn", String.valueOf(nightmareYn));
        return botNewDAO.updateNightmareYn(p);
    }

    public boolean isNightmareUnlocked(String userName) {
        // 일반모드에서 15번, 25번, 30번 몬스터 각 10킬 이상
        int[] required = {15, 25, 30};
        for (int monNo : required) {
            if (botNewDAO.selectNormalKillCountByMonNo(userName, monNo) < 10) return false;
        }
        return true;
    }

    public boolean isHellUnlocked(String userName) {
        // OR 조건 통합 조회
        HashMap<String,Object> s;
        try { s = botNewDAO.selectHellUnlockStats(userName); } catch (Exception e) { return false; }
        if (s == null) return false;

        //long lifetimeSp  = ((Number) s.getOrDefault("LIFETIME_SP",  0)).longValue();
        int  mon25Kills  = ((Number) s.getOrDefault("MON25_KILLS",  0)).intValue();
        int  achvCount   = ((Number) s.getOrDefault("ACHV_COUNT",   0)).intValue();
        int  relicCount  = ((Number) s.getOrDefault("RELIC_COUNT",  0)).intValue();

        // 25번 몬스터 1마리 이상 처치
        if (mon25Kills >= 1) return true;

        // 업적 350개 아이템 보유
        if (achvCount >= 1) return true;

        // 유물 아이템 25개 이상 보유
        if (relicCount >= 25) return true;


        return false;
    }

    @Override
    public HashMap<String,Object> selectHeavenItemBuff(String userName) {
        return botNewDAO.selectHeavenItemBuff(userName);
    }

    @Override
    public List<Integer> selectItemIdsByType(String itemType) {
        return botNewDAO.selectItemIdsByType(itemType);
    }

    @Override
    public List<HashMap<String, Object>> selectHellRewardItemsWithOwnCount() {
        return botNewDAO.selectHellRewardItemsWithOwnCount();
    }

    @Override
    public List<Integer> selectBossItemIds() {
        return botNewDAO.selectItemIdsByType("BOSS_HELL");
    }

    @Override
    public void insertGpRecord(HashMap<String, Object> param) {
        botNewDAO.insertGpRecord(param);
        try {
            String uName = param.get("userName") != null ? param.get("userName").toString() : null;
            if (uName != null) botNewDAO.upsertPointNewRank(uName);
        } catch (Exception ignore) {}
    }

    @Override
    public double selectGpBalance(String userName) {
        return botNewDAO.selectGpBalance(userName);
    }

    @Override
    public double selectUserTotalEarnedGp(String userName) {
        return botNewDAO.selectUserTotalEarnedGp(userName);
    }

    @Override
    public List<HashMap<String, Object>> selectGpRanking() {
        return botNewDAO.selectGpRanking();
    }

    @Override
    public HashMap<String, Object> lockMacroUser(String userName) {
    	HashMap <String, Object> param = new HashMap<>();
        param.put("userName", userName);

        botNewDAO.lockMacroUser(param);

        return param ;//(Integer) param.get("outCode");
    }
    @Override
    public int selectTotalBagAcquireCount(String userName) {
    	return botNewDAO.selectTotalBagAcquireCount(userName);
    }

    // [OPT3] INVENTORY 3개 쿼리 통합
    @Override
    public HashMap<String,Object> selectAchievementInventoryCounts(String userName) {
        return botNewDAO.selectAchievementInventoryCounts(userName);
    }

    @Override
    public int selectPotionUseCount(String userName) {
        return botNewDAO.selectPotionUseCount(userName);
    }

    public int updateRandomBlessUser(String attacker,int count) {
    	HashMap<String,Object> param = new HashMap<>();
        param.put("attacker", attacker);
        param.put("count", count);

        List<String> targets =
                botNewDAO.selectRandomBlessTargets(param);

        if (targets == null || targets.isEmpty()) {
            return 0;
        }

        int successCount = 0;

        for (String target : targets) {

            int updated = botNewDAO.updateBlessYn(target);

            if (updated > 0) {
                successCount++;
            }
        }

        return successCount;
    }
    public void clearBlessYn(String userName) {
        botNewDAO.clearBlessYn(userName);
    }


    @Override
    public HashMap<String,Object> selectTodayBagCounts(String userName, String roomName) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        return botNewDAO.selectTodayBagCounts(param);
    }

    @Override
    public HashMap<String,Object> selectOpenBagCounts(String userName) {
        return botNewDAO.selectOpenBagCounts(userName);
    }

    @Override
    public int selectBagCountByItemId(String userName, String roomName, int itemId) {

        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        param.put("itemId", itemId);

        return botNewDAO.selectBagCountByItemId(param);
    }

    @Override
    public int consumeBagBulkByItemIdTx(String userName,
                                        String roomName,
                                        int itemId,
                                        int count) {

        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        param.put("itemId", itemId);
        param.put("count", count);

        return botNewDAO.consumeBagBulkByItemId(param);
    }



    @Override
    public int selectBagCountByItemIdAndGainType(String userName, String roomName, int itemId, String gainType) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        param.put("itemId",   itemId);
        param.put("gainType", gainType);
        return botNewDAO.selectBagCountByItemIdAndGainType(param);
    }

    @Override
    public int consumeBagBulkByItemIdAndGainTypeTx(String userName, String roomName, int itemId, String gainType, int count) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("roomName", roomName);
        param.put("itemId",   itemId);
        param.put("gainType", gainType);
        param.put("count",    count);
        return botNewDAO.consumeBagBulkByItemIdAndGainType(param);
    }
    public int selectTodayBagCount(String userName){
    	return botNewDAO.selectTodayBagCount(userName);
    }
    public HashMap<String,Object> selectActiveSpecialBuff(){
    	return botNewDAO.selectActiveSpecialBuff();
    }

    public int insertSpecialBuff(HashMap<String,Object> param) {
    	return botNewDAO.insertSpecialBuff(param);
    }
    
    @Override
    public List<java.util.HashMap<String, Object>> selectBossHellItemTotalQty(String userName) {
        return botNewDAO.selectBossHellItemTotalQty(userName);
    }

    public List<Integer> selectInventoryItemsByIds(String userName, String roomName, Collection<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyList();
        }
        return botNewDAO.selectInventoryItemsByIds(userName, roomName, itemIds);
    }

    @Override
    public List<HashMap<String, Object>> selectAllItemsWithOwned(String userName) {
        return botNewDAO.selectAllItemsWithOwned(userName);
    }

    @Override
    public int selectTodayAttendYn(String userName, String roomName) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName); p.put("roomName", roomName);
        return botNewDAO.selectTodayAttendYn(p);
    }

    @Override
    public int updateLastAttendDate(String userName, String roomName) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName); p.put("roomName", roomName);
        return botNewDAO.updateLastAttendDate(p);
    }
    public List<String> selectAltCharList() {
        return botNewDAO.selectAltCharList();
    }

    @Override
    public HashMap<String, Object> selectCurrentBossState() {
        return botNewDAO.selectCurrentBossState();
    }
    @Override
    public HashMap<String, Object> selectLastBossKillTime() {
        return botNewDAO.selectLastBossKillTime();
    }
    @Override
    public List<HashMap<String, Object>> selectCurrentBossRecentLog() {
        return botNewDAO.selectCurrentBossRecentLog();
    }
    @Override
    public int migrateInventoryToOld() throws Exception {

        // 이번달 1일 기준 (그 이전 데이터만 이관)
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        java.sql.Date cutoffDate = java.sql.Date.valueOf(firstOfMonth);

        HashMap<String, Object> param = new HashMap<>();
        param.put("cutoffDate", cutoffDate);

        // 1. 이관 대상 그룹 조회
        List<HashMap<String, Object>> targets = botNewDAO.selectInventoryOldMigrateTarget(param);
        if (targets == null || targets.isEmpty()) {
            return 0;
        }

        int migratedCount = 0;

        // 2. 각 그룹별 UPDATE or INSERT
        for (HashMap<String, Object> row : targets) {
            HashMap<String, Object> upsertParam = new HashMap<>();
            upsertParam.put("userName", Objects.toString(row.get("USER_NAME"), ""));
            upsertParam.put("itemId",   row.get("ITEM_ID"));
            upsertParam.put("delYn",    Objects.toString(row.get("DEL_YN"), "1"));
            upsertParam.put("gainType", row.get("GAIN_TYPE"));
            upsertParam.put("qty",      row.get("QTY"));

            int updated = botNewDAO.updateInventoryOldQty(upsertParam);
            if (updated == 0) {
                // 기존 레코드 없으면 INSERT
                botNewDAO.insertInventoryOld(upsertParam);
            }
            migratedCount++;
        }

        // 3. 이관 완료된 메인 테이블 데이터 삭제
        botNewDAO.deleteInventoryMigrated(param);

        return migratedCount;
    }

    @Override
    public List<HashMap<String, Object>> selectAllUsersForRank() {
        return botNewDAO.selectAllUsersForRank();
    }

    @Override
    public List<HashMap<String,Object>> selectJobLevels(String userName) {
        return botNewDAO.selectJobLevels(userName);
    }

    @Override
    public HashMap<String,Object> selectJobLevel(String userName, String jobName) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName", userName);
        param.put("jobName",  jobName);
        return botNewDAO.selectJobLevel(param);
    }

    @Override
    public int selectTotalJobLv(String userName) {
        return botNewDAO.selectTotalJobLv(userName);
    }

    @Override
    public int upsertJobLevel(String userName, String jobName, int jobLv, int jobKillCnt) {
        HashMap<String,Object> param = new HashMap<>();
        param.put("userName",   userName);
        param.put("jobName",    jobName);
        param.put("jobLv",      jobLv);
        param.put("jobKillCnt", jobKillCnt);
        return botNewDAO.upsertJobLevel(param);
    }

    // ── 경험치판매 ──────────────────────────────────────────────────────────
    @Override
    public HashMap<String,Object> selectExpSellStats(String userName) {
        return botNewDAO.selectExpSellStats(userName);
    }
    @Override
    public int upsertExpSellStats(HashMap<String,Object> param) {
        return botNewDAO.upsertExpSellStats(param);
    }
    @Override
    public int updateExpCurOnly(String userName, String roomName, long expCur) {
        return botNewDAO.updateExpCurOnly(userName, roomName, expCur);
    }
    @Override
    @Transactional
    public void expSellTx(String userName, String roomName, long newExpCur, HashMap<String,Object> statsParam) {
        botNewDAO.updateExpCurOnly(userName, roomName, newExpCur);
        botNewDAO.upsertExpSellStats(statsParam);
    }

    // ── 실시간 카운터 테이블 ──────────────────────────────────────────────────
    @Override
    public int upsertMonKillStat(HashMap<String,Object> param) {
        return botNewDAO.upsertMonKillStat(param);
    }
    @Override
    public int upsertHellBossClearForParticipants(HashMap<String,Object> param) {
        return botNewDAO.upsertHellBossClearForParticipants(param);
    }
    @Override
    public int upsertBattleJobStat(HashMap<String,Object> param) {
        return botNewDAO.upsertBattleJobStat(param);
    }
    @Override
    public int upsertBattleBuffStat(HashMap<String,Object> param) {
        return botNewDAO.upsertBattleBuffStat(param);
    }

    @Override
    public HashMap<String, Object> selectRelicTotalCounts() {
        return botNewDAO.selectRelicTotalCounts();
    }

    // ── 초기 이관 ─────────────────────────────────────────────────────────────
    @Override
    public void migrateBattleLogToStatAll() {
        botNewDAO.migrateBattleLogToKillStat();
        botNewDAO.migrateBattleLogToJobStat();
        botNewDAO.migrateBattleLogToBuffStat();
    }

    @Override
    public int migrateLastMonthToJobStat() {
        return botNewDAO.migrateLastMonthToJobStat();
    }

    /** 매월 5일 배치: 전월 BATTLE_JOB 이관 + battle_log 백업 + 삭제 */
    @Override
    public String runMonthlyBattleLogJob() {
        StringBuilder result = new StringBuilder();

        // 1. BATTLE_JOB 집계 — 실패해도 이후 단계 계속 진행
        try {
            int inserted = botNewDAO.migrateLastMonthToJobStat();
            result.append("BATTLE_JOB 이관: ").append(inserted).append("건");
        } catch (Exception e) {
            result.append("BATTLE_JOB 이관 실패: ").append(e.getMessage());
        }

        // 2. BACKUP 이관 — 실패 시 삭제 스킵
        boolean backupOk = false;
        try {
            int backed = botNewDAO.backupOldBattleLog();
            result.append(", BACKUP 이관: ").append(backed).append("건");
            backupOk = true;
        } catch (Exception e) {
            result.append(", BACKUP 이관 실패(삭제 스킵): ").append(e.getMessage());
        }

        // 3. 원본 삭제 — BACKUP 성공한 경우에만, 1만건씩 배치 분할 삭제
        if (backupOk) {
            try {
                final int BATCH = 10000;
                int totalDeleted = 0;
                int deleted;
                do {
                    deleted = botNewDAO.deleteLastMonthBattleLogBatch(BATCH);
                    totalDeleted += deleted;
                } while (deleted > 0);
                result.append(", battle_log 삭제: ").append(totalDeleted).append("건");
            } catch (Exception e) {
                result.append(", battle_log 삭제 실패: ").append(e.getMessage());
            }
        }

        return result.toString();
    }

    @Override
    public int selectHourlyRealAttackCount(String userName) {
        return botNewDAO.selectHourlyRealAttackCount(userName);
    }

    @Override
    public HashMap<String,Object> selectMacroLock(String userName) {
        return botNewDAO.selectMacroLock(userName);
    }

    @Override
    public int insertMacroLock(String userName, String code) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("code", code);
        return botNewDAO.insertMacroLock(p);
    }

    @Override
    public int releaseMacroLock(String userName, String code) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("code", code);
        return botNewDAO.releaseMacroLock(p);
    }

    @Override
    public int countMacroDetectLogLastHour(String userName) {
        return botNewDAO.countMacroDetectLogLastHour(userName);
    }

    @Override
    public void insertMacroDetectLog(String userName, String code) {
        HashMap<String,Object> p = new HashMap<>();
        p.put("userName", userName);
        p.put("code", code);
        botNewDAO.insertMacroDetectLog(p);
    }
}
