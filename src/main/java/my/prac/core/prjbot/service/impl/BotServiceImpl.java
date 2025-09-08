package my.prac.core.prjbot.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.LoaApiUtils;

@Service("core.prjbot.BotService")
public class BotServiceImpl implements BotService {
	
	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	
	public void insertBotWordSaveTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.insertBotWordSave(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public void insertBotWordReplaceTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.insertBotWordReplace(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}

	public String selectBotWordSaveOne(HashMap<String, Object> hashMap) {
		return botDAO.selectBotWordSaveOne(hashMap);
	}
	
	public String selectBotManual(HashMap<String, Object> hashMap){
		return botDAO.selectBotManual(hashMap);
	}
	public String selectBotManualG(HashMap<String, Object> hashMap){
		return botDAO.selectBotManualG(hashMap);
	}
	public List<String> selectBotLimitWordSaveAll(HashMap<String, Object> hashMap){
		return botDAO.selectBotLimitWordSaveAll(hashMap);
	}
	public List<String> selectBotWordSaveAll(HashMap<String, Object> hashMap){
		return botDAO.selectBotWordSaveAll(hashMap);
	}
	public List<String> selectBotImgSaveAll(HashMap<String, Object> hashMap){
		return botDAO.selectBotImgSaveAll(hashMap);
	}
	public List<String> selectBotWordReplaceAll(HashMap<String, Object> hashMap){
		return botDAO.selectBotWordReplaceAll(hashMap);
	}
	
	public int selectBotWordSaveMasterCnt(HashMap<String, Object> hashMap) throws Exception{
		return botDAO.selectBotWordSaveMasterCnt(hashMap);
	}
	
	public void deleteBotWordSaveMasterTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.deleteBotWordSaveMaster(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public void deleteBotWordSaveAllDeleteMasterTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.deleteBotWordSaveAllDeleteMaster(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public void deleteBotWordSaveTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.deleteBotWordSave(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public void deleteBotWordReplaceMasterTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.deleteBotWordReplaceMaster(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public void deleteBotWordReplaceTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.deleteBotWordReplace(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public String selectBotImgSaveOne(String param) {
		return botDAO.selectBotImgSaveOne(param);
	}
	
	public void insertBotImgSaveOneTx(HashMap<String, Object> hashMap)  throws Exception{
		if(botDAO.insertBotImgSaveOne(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	public String selectBotImgMch(HashMap<String, Object> hashMap) {
		return botDAO.selectBotImgMch(hashMap);
	}
	public String selectBotImgCharSave(String req) {
		return botDAO.selectBotImgCharSave(req);
	}
	public HashMap<String,String> selectBotImgCharSaveI3(String res){
		return botDAO.selectBotImgCharSaveI3(res);
	}
	
	public void insertBotImgCharSaveTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.insertBotImgCharSave(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	
	
	
	public List<String> selectBotRaidSaveAll(HashMap<String, Object> hashMap){
		return botDAO.selectBotRaidSaveAll(hashMap);
	}
	public void insertBotRaidSaveTx(HashMap<String, Object> hashMap) throws Exception{
		if(botDAO.insertBotRaidSaveOne(hashMap)< 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public int selectSupporters(String userId) {
		return botDAO.selectSupporters(userId);
	}
	
	public int insertBotWordHisTx(HashMap<String, Object> hashMap) {
		return botDAO.insertBotWordHis(hashMap);
	}
	
	public List<String> selectRoomList(HashMap<String, Object> hashMap){
		return botDAO.selectRoomList(hashMap);
	}
	
	public List<HashMap<String,Object>> selectMarketCondition(HashMap<String, Object> hashMap){
		return botDAO.selectMarketCondition(hashMap);
	}
	public HashMap<String,Object> selectIssueCase(HashMap<String, Object> hashMap){
		return botDAO.selectIssueCase(hashMap);
	}
	public String selectBotWordReplace(HashMap<String,Object> map){
		return botDAO.selectBotWordReplace(map);
	}
	
	public int selectBotLoaEngraveCnt(String userId) {
		return botDAO.selectBotLoaEngraveCnt(userId);
	}
	
	public HashMap<String,Object> selectBotLoaEngrave(String userId) {
		return botDAO.selectBotLoaEngrave(userId);
	}
	
	public void insertBotLoaEngraveBaseTx(String userId) throws Exception {
		if(botDAO.insertBotLoaEngraveBase(userId)< 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public void updateBotLoaEngraveTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.updateBotLoaEngrave(map)< 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public void upsertBotPowerRankTx(HashMap<String,Object> map) throws Exception{
		int cnt = botDAO.selectCountBotPowerRank(map);
		
		if(cnt==0) {
			if(botDAO.insertBotPowerRank(map) < 1) {
				throw new Exception("저장 실패");
			}
		}else {
			if(botDAO.updateBotPowerRank(map) < 1) {
				throw new Exception("저장 실패");
			}
		}
		
	}
	public List<HashMap<String,Object>> selectRoomBotPowerRank(HashMap<String,Object> map){
		return botDAO.selectRoomBotPowerRank(map);
	}
	public List<HashMap<String,Object>> selectBotPointRankNewScore(HashMap<String,Object> map){
		return botDAO.selectBotPointRankNewScore(map);
	}
	
	public int insertBotPointRankTx(HashMap<String,Object> map)  throws Exception{
		if(botDAO.insertBotPointRank(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		List<HashMap<String,Object>> ls = botDAO.selectBotPointRankNewScore(map);
		int new_score = -999;
		try {
			new_score = Integer.parseInt(ls.get(0).get("SCORE").toString());
		}catch(Exception e) {
			throw new Exception("저장완료 후 재조회 실패");
		}
		return new_score;
	}
	
	public List<HashMap<String,Object>> selectBotPointRankToday(HashMap<String,Object> map){
		return botDAO.selectBotPointRankToday(map);
	}
	public int selectDailyCheck(HashMap<String,Object> map){
		return botDAO.selectDailyCheck(map);
	}
	public String selectHourCheck(HashMap<String,Object> map){
		return botDAO.selectHourCheck(map);
	}
	public HashMap<String,Object> selectBotPointRankOne(HashMap<String,Object> map){
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("newUserName", newUserName.get(0));
			}else {
				return null;
			}
		}
		
		HashMap<String,Object> result = botDAO.selectBotPointRankOne(map);
		
		if(result.get("VALID_YN").toString().equals("0")) {
			return null;
		}
		if(result.get("BLOCK_YN").toString().equals("1")) {
			return null;
		}
		
		return result;
	}
	public List<HashMap<String,Object>> selectBotPointRankAll(HashMap<String,Object> map){
		return botDAO.selectBotPointRankAll(map);
	}
	
	/** fight */
	public List<HashMap<String,Object>> selectBotPointRankFightBeforeCheck(HashMap<String,Object> map){
		
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("param1", newUserName.get(0));
			}
		}
		
		return botDAO.selectBotPointRankFightBeforeCheck(map);
	}
	public List<HashMap<String,Object>> selectBotPointRankFightBeforeCheck2(HashMap<String,Object> map){
		return botDAO.selectBotPointRankFightBeforeCheck2(map);
	}
	
	public List<HashMap<String,Object>> selectBotPointFight(HashMap<String,Object> map){
		return botDAO.selectBotPointFight(map);
	}
	public int  selectBotPointRankFightBeforeCount(HashMap<String,Object> map) {
		return botDAO.selectBotPointRankFightBeforeCount(map);
	}
	
	public void updateBotPointFightETx(HashMap<String,Object> map)  throws Exception {
		if(botDAO.updateBotPointFightE(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		int score = Integer.parseInt(map.get("param2").toString());
		map.put("newUserName", map.get("winnerName"));
		map.put("score", score);
		if(botDAO.insertBotPointFightE(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		map.put("newUserName", map.get("loserName"));
		map.put("score", -score);
		if(botDAO.insertBotPointFightE(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public void insertBotPointFightSTx(HashMap<String,Object> map)  throws Exception{
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("param1", newUserName.get(0));
			}
		}
		
		if(botDAO.insertBotPointFightS(map) < 1) {
			throw new Exception("저장 실패");
		}
	}

	
	public void insertMarketItemList(List<HashMap<String, Object>> rawDataList) throws Exception {
		LocalDateTime now = LocalDateTime.now();

        // insert_d: 05월 02일
        String insertD = now.format(DateTimeFormatter.ofPattern("MM월 dd일"));

        // insert_h: 02일 14시
        String insertH = now.format(DateTimeFormatter.ofPattern("MM월 dd일 HH시"));

        // insert_w: 2025년 05월 1주차 (한글 주차 포맷)
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        int weekOfMonth = now.get(weekFields.weekOfMonth());
        int month = now.getMonthValue();
        int year = now.getYear();
        String insertW = String.format("%d년 %02d월 %d주차", year, month, weekOfMonth);

        for (Map<String, Object> data : rawDataList) {
            data.put("insert_d", insertD);
            data.put("insert_h", insertH);
            data.put("insert_w", insertW);
            data.put("item_name", LoaApiUtils.filterTextForEngrave(data.get("Name").toString()));
        }
        
        if(botDAO.insertMarketItemList(rawDataList) < 1) {
			throw new Exception("저장 실패");
		}

    }
	
	public void insertAuctionItemOne(HashMap<String, Object> rawDataOne) throws Exception{
		LocalDateTime now = LocalDateTime.now();

        // insert_d: 05월 02일
        String insertD = now.format(DateTimeFormatter.ofPattern("MM월 dd일"));

        // insert_h: 02일 14시
        String insertH = now.format(DateTimeFormatter.ofPattern("MM월 dd일 HH시"));

        // insert_w: 2025년 05월 1주차 (한글 주차 포맷)
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        int weekOfMonth = now.get(weekFields.weekOfMonth());
        int month = now.getMonthValue();
        int year = now.getYear();
        String insertW = String.format("%d년 %02d월 %d주차", year, month, weekOfMonth);

        rawDataOne.put("insert_d", insertD);
    	rawDataOne.put("insert_h", insertH);
    	rawDataOne.put("insert_w", insertW);
    	
        if(botDAO.insertAuctionItemOne(rawDataOne) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public List<HashMap<String,Object>> selectMarketItemPriceInfo(HashMap<String,Object> map){
		return botDAO.selectMarketItemPriceInfo(map);
	}
	
	

	/** updown */
	public HashMap<String,Object> selectBotPointUpdownS(HashMap<String,Object> map){
		return botDAO.selectBotPointUpdownS(map);
	}
	public void insertBotPointUpdownSTx(HashMap<String,Object> map) throws Exception{
		if(botDAO.insertBotPointUpdownS(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public void updateBotPointUpdownSTx(HashMap<String,Object> map) throws Exception{
		if(botDAO.updateBotPointUpdownS(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	
	
	
	/** baseball */
	public HashMap<String,Object> selectBotPointBaseballIngChk(HashMap<String,Object> map){
		return botDAO.selectBotPointBaseballIngChk(map);
	}
	public void insertBotPointBaseballSTx(HashMap<String,Object> map) throws Exception{
		//처음시작
		if(botDAO.insertBotPointBaseballS(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public void insertBotPointBaseballIng(HashMap<String,Object> map) throws Exception{
		//맞추기 시도
		if(botDAO.insertBotPointBaseballIng(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		if(map.get("endYn").equals(true) ) {
			//맞춘경우!
			if(botDAO.updateBotPointBaseballE(map) < 1) {
				throw new Exception("저장 실패");
			}
		}
	}
	
	/** 강화 */
	//only 출첵
	public void insertBotPointStoneTx(HashMap<String,Object> map)  throws Exception{
		if(botDAO.insertBotPointStone(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public HashMap<String,Object> selectBotPointWeapon(HashMap<String,Object> map) throws Exception{
		int firstTry = botDAO.selectCntBotPointWeapon(map);
		if(firstTry ==0) {
			if(botDAO.insertBotPointWeapon(map) < 1) {
				throw new Exception("저장 실패");
			}
		}
		
		return botDAO.selectBotPointWeapon(map);
	}
	public HashMap<String,Object> selectBotPointAcc(HashMap<String,Object> map) throws Exception{
		
		int cnt = botDAO.selectCntBotPointWeapon(map);
		if(cnt == 0) {
			return null;
		}
		
		return botDAO.selectBotPointAcc(map);
	}
	
	public int selectWeaponLvCheckForPoint(HashMap<String,Object> map){
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("newUserName", newUserName.get(0));
			}else {
				return 0;
			}
		}
		return botDAO.selectWeaponLvCheckForPoint(map);
	}
	
	
	public HashMap<String,Object> upsertDailyWeaponUpgradeTx(HashMap<String,Object> map) throws Exception{
		
		//매일출섹 무기업그레이드 시에만 0점으로 넣고
		//추가강화는 컨트롤러에서 insert함
		if(map.get("cmd").equals("weapon_upgrade")) {
			if(botDAO.insertBotPointRank(map) < 1) {
				throw new Exception("저장 실패");
			}
		}
		
		if(botDAO.insertBotPointWeaponLog(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		if(botDAO.updateBotPointWeapon(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		
		return null;
		
		
	}
	public HashMap<String,Object> updateBotPointAccTx(HashMap<String,Object> map) throws Exception{
		if(botDAO.insertBotPointAccLog(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		if(botDAO.updateBotPointAcc(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		
		return null;
		
		
	}
	
	public List<HashMap<String,Object>> selectBotPointWeaponRank(HashMap<String,Object> map){
		return botDAO.selectBotPointWeaponRank(map);
	}
	public String selectBotPointWeaponRank1st() {
		return botDAO.selectBotPointWeaponRank1st();
	}
	
	public void insertBotBlockTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.insertBotBlock(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public void deleteBotBlockTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.deleteBotBlock(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public List<HashMap<String,Object>> selectBotBlock(HashMap<String,Object> map) throws Exception {
		return botDAO.selectBotBlock(map);
	}
	public List<HashMap<String,Object>> selectGamePlayYn(HashMap<String,Object> map) throws Exception {
		return botDAO.selectGamePlayYn(map);
	}
	public void updateGamePlayYnTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.updateGamePlayYn(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public int selectWeaponLvCheck(HashMap<String,Object> map) {
		return botDAO.selectWeaponLvCheck(map);
	}
	
	public HashMap<String,Object> selectBossHit(HashMap<String,Object> map) throws Exception{
		return botDAO.selectBossHit(map);
	}
	
	public void updateBossHitTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.updateBossHit(map) < 1) {
			throw new Exception("저장 실패");
		}
		if(botDAO.insertBossHitLog(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		
	}
	public HashMap<String,Object> selectBotPointBoss(HashMap<String,Object> map) throws Exception{
		return botDAO.selectBotPointBoss(map);
	}
	
	public void insertBotPointBossTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.insertBotPointBoss(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public void updateBotPointBossTx(HashMap<String,Object> map) throws Exception {
		if(botDAO.updateBotPointBoss(map) < 1) {
			throw new Exception("저장 실패");
		}
		if(botDAO.insertBotPointBossLog(map) < 1) {
			throw new Exception("저장 실패");
		}
		
		
	}
	
	public List<HashMap<String, Object>> selectTop3Contributors(HashMap<String, Object> hashMap){
		
		return botDAO.selectTop3Contributors(hashMap);
	}
	
	
	public List<HashMap<String,Object>> selectPointItemUserList(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemUserList(map);
	}
	public List<HashMap<String,Object>> selectPointItemUserListForPoint(HashMap<String,Object> map) throws Exception{
		if (map.get("param1") != null && !map.get("param1").equals("")) {
			List<String> newUserName = botDAO.selectParam1ToNewUserSearch(map);
			if(newUserName.size()>0) {
				map.put("newUserName", newUserName.get(0));
			}else {
				return null;
			}
		}
		
		
		return botDAO.selectPointItemUserListForPoint(map);
	}
	
	public List<HashMap<String,Object>> selectPointItemInfoList(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemInfoList(map);
	}
	public HashMap<String,Object> selectPointItemUserOpenFlag1(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemUserOpenFlag1(map);
	}
	
	public void insertPointNewBoxOpenTx(HashMap<String,Object> map) throws Exception{
		if(botDAO.insertPointNewBoxOpen(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public void updatePointNewBoxOpenTx(HashMap<String,Object> map) throws Exception{
		if(botDAO.updatePointNewBoxOpen(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	public void updatePointNewBoxOpen2Tx(HashMap<String,Object> map) throws Exception{
		if(botDAO.updatePointNewBoxOpen2(map) < 1) {
			throw new Exception("저장 실패");
		}
	}
	
	public int selectPointItemUserCount(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemUserCount(map);
	}
	public String selectPointItemUserOpenFlag(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemUserOpenFlag(map);
	}
	public int selectPointItemUserOpenRate(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemUserOpenRate(map);
	}
	public int selectPointNewBoxCount(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointNewBoxCount(map);
	}
	public List<HashMap<String,Object>> selectPointItemOptionList(HashMap<String,Object> map) throws Exception{
		return botDAO.selectPointItemOptionList(map);
	}
	
	public List<HashMap<String,Object>> selectBotPointItemUserRankAll(HashMap<String,Object> map){
		return botDAO.selectBotPointItemUserRankAll(map);
	}
	
	public HashMap<String,Object> selectBotMaxLv(HashMap<String,Object> map){
		return botDAO.selectBotMaxLv(map);
	}
}
