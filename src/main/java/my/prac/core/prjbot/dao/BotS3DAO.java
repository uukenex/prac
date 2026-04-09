package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotS3DAO")
public interface BotS3DAO {

	/** [헬보스] 활성 보스 조회 (TBOT_POINT_NEW_BOSS, 전역) */
	HashMap<String, Object> selectHellBoss();

	/** [헬보스] HP 업데이트 (낙관적 잠금) */
	int updateHellBoss(HashMap<String, Object> map);

	/** [헬보스] 공격 로그 INSERT (TBOT_POINT_NEW_BATTLE_LOG) */
	int insertHellBattleLog(HashMap<String, Object> map);

	/** [헬보스] 기여도 TOP 조회 */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

	/** [헬보스] 마지막 처치된 보스 조회 (리워드 표시용) */
	HashMap<String, Object> selectLastKilledHellBoss();

	/** [헬보스] 마지막 보상 수령자 조회 */
	HashMap<String, Object> selectLastHellRewardRecipient();

}
