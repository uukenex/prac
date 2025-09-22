package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotSettleDAO")
public interface BotSettleDAO {
	public int selectBotPointSumScore(HashMap<String,Object> map);
	public int selectBotPointSumScoreForPoint(HashMap<String,Object> map);
}

