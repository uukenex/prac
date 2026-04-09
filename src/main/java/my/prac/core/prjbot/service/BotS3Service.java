package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

public interface BotS3Service {

	/** [헬보스] 활성 보스 조회 (TBOT_POINT_NEW_BOSS, 전역) */
	HashMap<String, Object> selectHellBoss() throws Exception;

	/** [헬보스] HP 업데이트 + 공격 로그 INSERT (트랜잭션) */
	void updateHellBossTx(HashMap<String, Object> map) throws Exception;

	/** [헬보스] 마지막 처치 보스 + 보상 정보 메시지 (보스 없을 때 표시용) */
	String getLastKillRewardMsg();

	/** [헬보스] 기여도 TOP 조회 */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

}
