package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

public interface BotService {
	public void insertBotWordSaveTx(HashMap<String, Object> hashMap)  throws Exception;

	public String selectBotWordSaveOne(HashMap<String, Object> hashMap);
	
	public List<String> selectBotWordSaveAll(HashMap<String, Object> hashMap);
	
	public void deleteBotWordSaveMasterTx(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotWordSaveTx(HashMap<String, Object> hashMap)  throws Exception;
	
	public String selectBotImgSaveOne(String param);
	public void insertBotImgSaveOneTx(HashMap<String, Object> hashMap)  throws Exception;
	
	public String selectBotImgMch(String param);
}

	
