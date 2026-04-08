package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

public interface BotS3Service {

	/** [헬보스] 기여도 TOP 조회 (TBOT_BOSS_HIT_LOG 기반) */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

}
