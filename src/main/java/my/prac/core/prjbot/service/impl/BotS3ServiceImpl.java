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
			// 캐시된 처치 결과가 있으면 그대로 반환
			if (CACHED_KILL_MSG != null && !CACHED_KILL_MSG.isEmpty()) {
				return "[ 최근 처치된 헬보스 결과 ]" + NL + CACHED_KILL_MSG;
			}
			// 캐시 없음 (서버 재시작 후 등) → 빈 문자열
			return "";
		} catch (Exception e) {
			return "";
		}
	}

}
