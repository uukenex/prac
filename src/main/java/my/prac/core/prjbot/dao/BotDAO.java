package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotDAO")
public interface BotDAO {
	public int insertBotWordSave(HashMap<String, Object> hashMap);

	public String selectBotWordSaveOne(HashMap<String, Object> hashMap);
	
	public List<String> selectBotWordSaveAll(HashMap<String, Object> hashMap);

	public int deleteBotWordSave(HashMap<String, Object> hashMap);
	

}
