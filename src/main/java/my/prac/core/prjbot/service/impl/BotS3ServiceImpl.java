package my.prac.core.prjbot.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotS3DAO;
import my.prac.core.prjbot.service.BotS3Service;

@Service("core.prjbot.BotS3Service")
public class BotS3ServiceImpl implements BotS3Service {

	private static final String NL = "♬";

	/** 마지막 헬보스 처치 결과 메시지 캐시 (서버 재시작 전까지 유지) */
	private static volatile String CACHED_KILL_MSG = null;

	@Resource(name = "core.prjbot.BotS3DAO")
	BotS3DAO botS3DAO;

	@Override
	public HashMap<String, Object> selectHellBoss() throws Exception {
		return botS3DAO.selectHellBoss();
	}

	@Override
	public void updateHellBossTx(HashMap<String, Object> map) throws Exception {
		if (botS3DAO.updateHellBoss(map) < 1) {
			throw new Exception("보스 HP 저장 실패");
		}
		botS3DAO.insertHellBattleLog(map);
	}

	@Override
	public void insertHellBoss(HashMap<String, Object> map) throws Exception {
		if (botS3DAO.insertHellBoss(map) < 1) {
			throw new Exception("보스 재생성 실패");
		}
	}

	@Override
	public List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map) {
		return botS3DAO.selectHellTop3Contributors(map);
	}

	@Override
	public List<HashMap<String, Object>> selectHellEligibleContributors(HashMap<String, Object> map) {
		return botS3DAO.selectHellEligibleContributors(map);
	}

	@Override
	public List<String> selectHellLotteryPool() {
		return botS3DAO.selectHellLotteryPool();
	}

	@Override
	public List<String> selectHellItemOwners() {
		return botS3DAO.selectHellItemOwners();
	}

	@Override
	public void reduceHellBossInsertDate() {
		botS3DAO.reduceHellBossInsertDate();
	}

	@Override
	public Long selectRecentHellAvgDmg() throws Exception {
		return botS3DAO.selectRecentHellAvgDmg();
	}

	@Override
	public List<HashMap<String, Object>> selectHellBossRecentLog(HashMap<String, Object> map) {
		return botS3DAO.selectHellBossRecentLog(map);
	}

	@Override
	public int selectHellBossAttackerCount(String bossStartDate) {
		return botS3DAO.selectHellBossAttackerCount(bossStartDate);
	}

	@Override
	public void updateHellBossCountUpTx(HashMap<String, Object> map) throws Exception {
		botS3DAO.updateHellBossCountUpAdd(map);
		botS3DAO.insertHellBattleLog(map);
	}

	@Override
	public int closeHellBoss(int seq) throws Exception {
		HashMap<String, Object> p = new HashMap<>();
		p.put("seq", seq);
		return botS3DAO.closeHellBoss(p);
	}

	@Override
	public void insertHellBossCountUp(HashMap<String, Object> map) throws Exception {
		if (botS3DAO.insertHellBossCountUp(map) < 1) {
			throw new Exception("카운트업 보스 생성 실패");
		}
	}

	@Override
	public int masterRespawnBossNow() {
		return botS3DAO.masterRespawnBossNow();
	}

	@Override
	public int forceCloseCurrentBoss() {
		return botS3DAO.forceCloseCurrentBoss();
	}

	@Override
	public int masterChangeBossStat(HashMap<String, Object> map) {
		return botS3DAO.masterChangeBossStat(map);
	}

	@Override
	public void reduceCountUpCloseDate() {
		botS3DAO.reduceCountUpCloseDate();
	}

	@Override
	public HashMap<String, Object> selectCountUpBossStats(String bossStartDate) {
		HashMap<String, Object> p = new HashMap<>();
		p.put("bossStartDate", bossStartDate);
		return botS3DAO.selectCountUpBossStats(p);
	}

	@Override
	public long selectMyCountUpDmg(String userName, String bossStartDate) {
		HashMap<String, Object> p = new HashMap<>();
		p.put("userName", userName);
		p.put("bossStartDate", bossStartDate);
		return botS3DAO.selectMyCountUpDmg(p);
	}

	@Override
	public void saveLastKillMsg(String msg) {
		CACHED_KILL_MSG = (msg == null ? "" : msg.trim());
	}

	@Override
	public String getLastKillRewardMsg() {
		try {
			if (CACHED_KILL_MSG != null && !CACHED_KILL_MSG.isEmpty()) {
				return "[ 최근 처치된 헬보스 결과 ]" + NL + CACHED_KILL_MSG;
			}
			// 캐시 없으면 DB에서 재조회 (서버 재시작 후 복원)
			String fromDb = buildLastKillMsgFromDb();
			if (!fromDb.isEmpty()) {
				CACHED_KILL_MSG = fromDb;
				return "[ 최근 처치된 헬보스 결과 ]" + NL + fromDb;
			}
			return "";
		} catch (Exception e) {
			return "";
		}
	}

	@Override
	public String buildLastKillMsgFromDb() {
		try {
			List<HashMap<String, Object>> rows = botS3DAO.selectLastSuccubusKillGp();
			if (rows == null || rows.isEmpty()) return "";
			StringBuilder sb = new StringBuilder();
			sb.append("[ 서큐버스 처치 보상 ]").append(NL);
			sb.append("참여자: ").append(rows.size()).append("명").append(NL).append(NL);
			for (HashMap<String, Object> row : rows) {
				String uName  = row.get("USER_NAME") != null ? row.get("USER_NAME").toString() : "";
				double gp     = row.get("GP")        != null ? ((Number) row.get("GP")).doubleValue() : 0;
				long   cnt    = row.get("CNT")        != null ? ((Number) row.get("CNT")).longValue()  : 0;
				long   dmg    = row.get("TOTAL_DMG")  != null ? ((Number) row.get("TOTAL_DMG")).longValue() : 0;
				sb.append(uName).append(NL)
				  .append("⚔ 데미지: ").append(my.prac.core.util.SP.fromSp(dmg))
				  .append(" (").append(cnt).append("회)").append(NL)
				  .append("✨ +").append(String.format("%.2f", gp)).append(" GP").append(NL);
			}
			return sb.toString().trim();
		} catch (Exception e) {
			return "";
		}
	}

}
