package my.prac.core.prjbot.service.impl;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

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
    public List<HashMap<String, Object>> selectMonsterKillsForView(String userName) {
        return botNewDAO.selectMonsterKillsForView(userName);
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
    public int selectYesterdayAttackerCount() {
        return botNewDAO.selectYesterdayAttackerCount();
    }

    @Override
    public int updateUserAfterBattleTx(String userName, String roomName, int newLv, int newExpCur, int newExpNext,
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
	public HashMap<String,Object>  selectTotalEarnedSp(String userName, String roomName) {
		return botNewDAO.selectTotalEarnedSp(userName, roomName);
	}

	@Override
	public int insertPointRank(HashMap<String, Object> p) {
		return botNewDAO.insertPointRank(p);
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
    public List<HashMap<String, Object>> selectKillLeadersByMonster() {
        return botNewDAO.selectKillLeadersByMonster();
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
    }

    @Override
    public double selectGpBalance(String userName) {
        return botNewDAO.selectGpBalance(userName);
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
    public List<String> selectAltCharList() {
        return botNewDAO.selectAltCharList();
    }
}
