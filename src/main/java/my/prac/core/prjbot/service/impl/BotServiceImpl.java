package my.prac.core.prjbot.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotService;

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
}
