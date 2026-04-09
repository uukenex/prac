package my.prac.core.prjbot.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotS3DAO;
import my.prac.core.prjbot.service.BotS3Service;

@Service("core.prjbot.BotS3Service")
public class BotS3ServiceImpl implements BotS3Service {

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
	public List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map) {
		return botS3DAO.selectHellTop3Contributors(map);
	}

	@Override
	public String getLastKillRewardMsg() {
		try {
			HashMap<String, Object> lastBoss = botS3DAO.selectLastKilledHellBoss();
			if (lastBoss == null) return "";

			int seq = Integer.parseInt(lastBoss.get("SEQ").toString());

			HashMap<String, Object> q = new HashMap<>();
			q.put("seq", seq);
			List<HashMap<String, Object>> contributors = botS3DAO.selectHellTop3Contributors(q);
			HashMap<String, Object> rewardRow = botS3DAO.selectLastHellRewardRecipient();

			StringBuilder sb = new StringBuilder();
			sb.append("[ 최근 처치된 헬보스 결과 ]").append("\n");
			if (rewardRow != null) {
				sb.append("★ 보상: [").append(rewardRow.get("USER_NAME"))
				  .append("] item#").append(rewardRow.get("ITEM_ID"))
				  .append(" (").append(rewardRow.get("REWARD_DATE")).append(")").append("\n");
			}
			if (contributors != null && !contributors.isEmpty()) {
				sb.append("\n-- 기여도 TOP --\n");
				for (HashMap<String, Object> row : contributors) {
					sb.append(row.get("USER_NAME"))
					  .append(" - ").append(row.get("CNT")).append("회 / 데미지 ").append(row.get("SCORE"))
					  .append("\n");
				}
			}
			return sb.toString().trim();
		} catch (Exception e) {
			return "";
		}
	}

}
