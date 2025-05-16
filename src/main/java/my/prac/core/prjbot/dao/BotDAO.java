package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotDAO")
public interface BotDAO {
	public int insertBotWordSave(HashMap<String, Object> hashMap);
	public int insertBotWordReplace(HashMap<String, Object> hashMap);

	public String selectBotWordSaveOne(HashMap<String, Object> hashMap);
	
	public String selectBotManual(HashMap<String, Object> hashMap);
	public List<String> selectBotLimitWordSaveAll(HashMap<String, Object> hashMap);
	public List<String> selectBotWordSaveAll(HashMap<String, Object> hashMap);
	public List<String> selectBotImgSaveAll(HashMap<String, Object> hashMap);
	public List<String> selectBotWordReplaceAll(HashMap<String, Object> hashMap);

	public int selectBotWordSaveMasterCnt(HashMap<String, Object> hashMap);
	public int deleteBotWordSaveMaster(HashMap<String, Object> hashMap);
	public int deleteBotWordSaveAllDeleteMaster(HashMap<String, Object> hashMap);
	public int deleteBotWordSave(HashMap<String, Object> hashMap);
	
	public int deleteBotWordReplaceMaster(HashMap<String, Object> hashMap);
	public int deleteBotWordReplace(HashMap<String, Object> hashMap);
	
	public String selectBotImgSaveOne(String param);
	public int insertBotImgSaveOne(HashMap<String, Object> hashMap);
	
	public String selectBotImgMch(HashMap<String, Object> hashMap);
	
	public String selectBotImgCharSave(String req);
	public HashMap<String,String> selectBotImgCharSaveI3(String res);
	public int insertBotImgCharSave(HashMap<String, Object> hashMap);
	
	public List<String> selectBotRaidSaveAll(HashMap<String, Object> hashMap);
	public int insertBotRaidSaveOne(HashMap<String, Object> hashMap);
	
	public int selectSupporters(String userId);
	
	public int insertBotWordHis(HashMap<String, Object> hashMap);
	
	public List<String> selectRoomList(HashMap<String, Object> hashMap);
	
	public List<HashMap<String,Object>> selectMarketCondition(HashMap<String, Object> hashMap);

	public HashMap<String,Object> selectIssueCase(HashMap<String, Object> hashMap);
	
	public String selectBotWordReplace(HashMap<String,Object> map);
	
	public int selectBotLoaEngraveCnt(String userId);
	
	public HashMap<String,Object> selectBotLoaEngrave(String userId);
	
	public int insertBotLoaEngraveBase(String userId);
	
	public int updateBotLoaEngrave(HashMap<String,Object> map);
	
	
	
	public int insertBotPowerRank(HashMap<String,Object> map);
	public int updateBotPowerRank(HashMap<String,Object> map);
	public int selectCountBotPowerRank(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectRoomBotPowerRank(HashMap<String,Object> map);
	public int insertBotPointRank(HashMap<String,Object> map);
	public HashMap<String,Object> selectBotPointRankOne(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankAll(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankToday(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRank(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankNewScore(HashMap<String,Object> map);
	
	/** fight */
	public List<HashMap<String,Object>> selectBotPointRankFightBeforeCheck(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointFight(HashMap<String,Object> map);
	public int selectBotPointRankFightBeforeCount(HashMap<String, Object> map);
	public int insertBotPointFightS(HashMap<String, Object> map);
	public int updateBotPointFightE(HashMap<String, Object> map);
	public int insertBotPointFightE(HashMap<String, Object> map);

	public int insertMarketItemList(@Param("list") List<HashMap<String, Object>> itemList);
	public int insertAuctionItemOne(HashMap<String, Object> itemOne);
	
	public List<HashMap<String,Object>> selectMarketItemPriceInfo(HashMap<String,Object> map);
	
	//param to userName
	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map);
}

