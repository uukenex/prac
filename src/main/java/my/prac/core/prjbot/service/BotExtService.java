package my.prac.core.prjbot.service;

public interface BotExtService {
	
	public int saveLatestMerchantReports(String json, int serverId) throws Exception;
	public String buildMerchantMessage(int serverId) throws Exception ;
} 

	
