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
	public Long selectRecentHellAvgDmg() throws Exception {
		return botS3DAO.selectRecentHellAvgDmg();
	}

	@Override
	public String getLastKillRewardMsg() {
		try {
			HashMap<String, Object> lastBoss = botS3DAO.selectLastKilledHellBoss();
			if (lastBoss == null) return "";

			String startDate = lastBoss.get("START_DATE").toString();
			HashMap<String, Object> q = new HashMap<>();
			q.put("bossStartDate", startDate);

			List<HashMap<String, Object>> contributors = botS3DAO.selectHellTop3Contributors(q);
			HashMap<String, Object> itemRow = botS3DAO.selectLastHellRewardRecipient();
			HashMap<String, Object> gpRow   = botS3DAO.selectLastHellGpRecipient();

			StringBuilder sb = new StringBuilder();
			sb.append("[ 최근 처치된 헬보스 결과 ]").append(NL);

			// 보상 수령자 표시 + 시각화에 사용할 당첨자 확정
			String rewardWinner = null;
			if (itemRow != null) {
				sb.append("★ 보상(아이템): [").append(itemRow.get("USER_NAME"))
				  .append("] item#").append(itemRow.get("ITEM_ID"))
				  .append(" (").append(itemRow.get("REWARD_DATE")).append(")").append(NL);
				rewardWinner = itemRow.get("USER_NAME").toString();
			}
			if (gpRow != null) {
				double gpScore = Double.parseDouble(gpRow.get("SCORE").toString());
				sb.append("★ 보상(GP): [").append(gpRow.get("USER_NAME"))
				  .append("] +").append(String.format("%.2f", gpScore)).append(" GP")
				  .append(" (").append(gpRow.get("REWARD_DATE")).append(")").append(NL);
				if (rewardWinner == null) rewardWinner = gpRow.get("USER_NAME").toString();
			}

			if (contributors == null || contributors.isEmpty()) return sb.toString().trim();

			// 전체 데미지 합산
			long totScore = 0;
			for (HashMap<String, Object> row : contributors)
				totScore += Long.parseLong(row.get("SCORE").toString());
			if (totScore == 0) return sb.toString().trim();

			// 2% 이상 기여자 필터 + 데미지% 계산
			List<String> names  = new ArrayList<>();
			List<Object> cnts   = new ArrayList<>();
			List<Long>   scores = new ArrayList<>();
			List<Double> pcts   = new ArrayList<>();
			for (HashMap<String, Object> row : contributors) {
				long score = Long.parseLong(row.get("SCORE").toString());
				double pct = score * 100.0 / totScore;
				if (pct >= 2.0) {
					names.add(row.get("USER_NAME").toString());
					cnts.add(row.get("CNT"));
					scores.add(score);
					pcts.add(pct);
				}
			}

			// 추첨 대상 목록 (데미지% 표시)
			sb.append(NL).append("-- 추첨 대상 (").append(names.size()).append("명) --").append(NL);
			for (int i = 0; i < names.size(); i++) {
				sb.append(" ").append(names.get(i))
				  .append(" ").append(cnts.get(i)).append("회/")
				  .append(String.format("%,d", scores.get(i))).append("dmg")
				  .append(" (").append(String.format("%.1f%%", pcts.get(i))).append(")")
				  .append(NL);
			}

			// 다이스 구간 시각화 (당첨자 밴드 중앙값을 주사위값으로 표시)
			if (rewardWinner != null && !names.isEmpty()) {
				double cumulative = 0;
				double winnerMid = -1;
				for (int i = 0; i < names.size(); i++) {
					double pct = pcts.get(i);
					if (names.get(i).equals(rewardWinner)) winnerMid = cumulative + pct / 2.0;
					cumulative += pct;
				}
				if (winnerMid >= 0) {
					sb.append(NL).append("⚅ 주사위: ").append(String.format("%.2f%%", winnerMid)).append(" →").append(NL);
					cumulative = 0;
					for (int i = 0; i < names.size(); i++) {
						String uName = names.get(i);
						double pct   = pcts.get(i);
						double bandEnd = cumulative + pct;
						boolean isWin = uName.equals(rewardWinner);
						sb.append(isWin ? "★" : " ")
						  .append(String.format("[%5.1f~%5.1f%%]", cumulative, bandEnd))
						  .append(" ").append(uName)
						  .append(isWin ? " ← 당첨!" : "")
						  .append(NL);
						cumulative = bandEnd;
					}
				}
			}

			return sb.toString().trim();
		} catch (Exception e) {
			return "";
		}
	}

}
