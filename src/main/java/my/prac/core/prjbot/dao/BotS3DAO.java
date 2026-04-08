package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotS3DAO")
public interface BotS3DAO {

	/** [헬보스] 기여도 TOP 조회 (TBOT_BOSS_HIT_LOG 기반) */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

}
