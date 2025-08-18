package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface BotService {
	public void insertBotWordSaveTx(HashMap<String, Object> hashMap)  throws Exception;
	public void insertBotWordReplaceTx(HashMap<String, Object> hashMap)  throws Exception;

	public String selectBotWordSaveOne(HashMap<String, Object> hashMap);
	
	public String selectBotManual(HashMap<String, Object> hashMap);
	public List<String> selectBotLimitWordSaveAll(HashMap<String, Object> hashMap);
	public List<String> selectBotWordSaveAll(HashMap<String, Object> hashMap);
	public List<String> selectBotImgSaveAll(HashMap<String, Object> hashMap);	
	public List<String> selectBotWordReplaceAll(HashMap<String, Object> hashMap);
	
	public int selectBotWordSaveMasterCnt(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotWordSaveMasterTx(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotWordSaveAllDeleteMasterTx(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotWordSaveTx(HashMap<String, Object> hashMap)  throws Exception;
	
	public void deleteBotWordReplaceMasterTx(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotWordReplaceTx(HashMap<String, Object> hashMap)  throws Exception;
	
	
	public String selectBotImgSaveOne(String param);
	public void insertBotImgSaveOneTx(HashMap<String, Object> hashMap)  throws Exception;
	
	public String selectBotImgMch(HashMap<String, Object> hashMap);
	
	public String selectBotImgCharSave(String req);
	public HashMap<String,String> selectBotImgCharSaveI3(String res);
	public void insertBotImgCharSaveTx(HashMap<String, Object> hashMap)  throws Exception;
	
	
	public List<String> selectBotRaidSaveAll(HashMap<String, Object> hashMap);
	public void insertBotRaidSaveTx(HashMap<String, Object> hashMap)  throws Exception;
	
	public int selectSupporters(String userId);
	
	public int insertBotWordHisTx(HashMap<String, Object> hashMap);
	public List<String> selectRoomList(HashMap<String, Object> hashMap);
	
	public List<HashMap<String,Object>> selectMarketCondition(HashMap<String, Object> hashMap);
	public HashMap<String,Object> selectIssueCase(HashMap<String, Object> hashMap);
	
	public String selectBotWordReplace(HashMap<String,Object> map);
	
	public int selectBotLoaEngraveCnt(String userId);
	public HashMap<String,Object> selectBotLoaEngrave(String userId);
	public void insertBotLoaEngraveBaseTx(String userId) throws Exception;
	public void updateBotLoaEngraveTx(HashMap<String,Object> map) throws Exception;
	
	
	
	public void upsertBotPowerRankTx(HashMap<String,Object> map)  throws Exception;
	public List<HashMap<String,Object>> selectRoomBotPowerRank(HashMap<String,Object> map);
	public int insertBotPointRankTx(HashMap<String,Object> map)  throws Exception;
	public List<HashMap<String,Object>> selectBotPointRankToday(HashMap<String,Object> map);
	public HashMap<String,Object> selectBotPointRankOne(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankAll(HashMap<String,Object> map);
	public int selectDailyCheck(HashMap<String,Object> map);
	public String selectHourCheck(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankNewScore(HashMap<String,Object> map);
	/** fight */
	public List<HashMap<String,Object>> selectBotPointRankFightBeforeCheck(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointRankFightBeforeCheck2(HashMap<String,Object> map);
	public List<HashMap<String,Object>> selectBotPointFight(HashMap<String,Object> map);
	public int  selectBotPointRankFightBeforeCount(HashMap<String,Object> map) ;
	public void updateBotPointFightETx(HashMap<String,Object> map)  throws Exception;
	public void insertBotPointFightSTx(HashMap<String,Object> map)  throws Exception;
	
	public void insertMarketItemList(List<HashMap<String, Object>> rawDataList) throws Exception;
	public void insertAuctionItemOne(HashMap<String, Object> rawDataOne) throws Exception;
	
	public List<HashMap<String,Object>> selectMarketItemPriceInfo(HashMap<String,Object> map);

	/** updown */
	public HashMap<String,Object> selectBotPointUpdownS(HashMap<String,Object> map);
	public void  insertBotPointUpdownSTx(HashMap<String,Object> map) throws Exception;
	public void  updateBotPointUpdownSTx(HashMap<String,Object> map) throws Exception;
	
	/** baseball */
	public HashMap<String,Object> selectBotPointBaseballIngChk(HashMap<String,Object> map);
	public void insertBotPointBaseballSTx(HashMap<String,Object> map) throws Exception;
	public void insertBotPointBaseballIng(HashMap<String,Object> map) throws Exception;
	
	/** 강화 */
	//only 출첵
	public void insertBotPointStoneTx(HashMap<String,Object> map)  throws Exception;
	
	public HashMap<String,Object> selectBotPointWeapon(HashMap<String,Object> map) throws Exception;
	public HashMap<String,Object> upsertDailyWeaponUpgradeTx(HashMap<String,Object> map) throws Exception;
	public List<HashMap<String,Object>> selectBotPointWeaponRank(HashMap<String,Object> map);
	public String selectBotPointWeaponRank1st();
	
	public int selectWeaponLvCheck(HashMap<String,Object> map);
	//차단기능
	public void insertBotBlockTx(HashMap<String, Object> hashMap)  throws Exception;
	public void deleteBotBlockTx(HashMap<String, Object> hashMap)  throws Exception;
	public List<HashMap<String,Object>> selectBotBlock(HashMap<String, Object> hashMap)  throws Exception;
	//게임 플레이여부
	public List<HashMap<String,Object>> selectGamePlayYn(HashMap<String,Object> map) throws Exception;
	public void updateGamePlayYnTx(HashMap<String, Object> hashMap)  throws Exception;
	
	//보스몬스터 기능
	public HashMap<String,Object> selectBossHit(HashMap<String,Object> map) throws Exception;
	public void updateBossHitTx(HashMap<String, Object> hashMap)  throws Exception;
	public List<HashMap<String, Object>> selectTop3Contributors(HashMap<String, Object> hashMap);

	//신보스
	public HashMap<String,Object> selectBotPointBoss(HashMap<String,Object> map) throws Exception;
	public void insertBotPointBossTx(HashMap<String, Object> hashMap)  throws Exception;
	public void updateBotPointBossTx(HashMap<String, Object> hashMap)  throws Exception;
	
	//아이템 뽑기 기능
	public List<HashMap<String,Object>> selectPointItemUserList(HashMap<String,Object> map) throws Exception;
	public List<HashMap<String,Object>> selectPointItemUserListForPoint(HashMap<String,Object> map) throws Exception;
	public List<HashMap<String,Object>> selectPointItemInfoList(HashMap<String,Object> map) throws Exception;
	public HashMap<String,Object> selectPointItemUserOpenFlag1(HashMap<String,Object> map) throws Exception;
	public void insertPointNewBoxOpenTx(HashMap<String,Object> map) throws Exception;
	public void updatePointNewBoxOpenTx(HashMap<String,Object> map) throws Exception;
	public void updatePointNewBoxOpen2Tx(HashMap<String,Object> map) throws Exception;
	public int selectPointItemUserCount(HashMap<String,Object> map) throws Exception;
	public String selectPointItemUserOpenFlag(HashMap<String,Object> map) throws Exception;
	public int selectPointNewBoxCount(HashMap<String,Object> map) throws Exception;
} 

	
