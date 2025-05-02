package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.json.JSONObject;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.LoaApiUtils;

@Controller
public class LoaMarketController {
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
	final String lostArkAPIurl = "https://developer-lostark.game.onstove.com";

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	//"[유물각인서 시세조회]"
	void search_c1() throws Exception{
		JSONObject json = new JSONObject();
		
		json.put("CategoryCode", "40000");
		json.put("Sort", "CURRENT_MIN_PRICE");
		json.put("SortCondition", "DESC");
		json.put("ItemGrade", "유물");
		
		json.put("PageNo", "1");
		search_c1_save(json);
		
		json.put("PageNo", "2");
		search_c1_save(json);
		
		json.put("PageNo", "3");
		search_c1_save(json);
		
	}
	
	//보석조회
	void search_c2() throws Exception{
		JSONObject json ;
		json = new JSONObject();
		
		json.put("CategoryCode", "210000");
		json.put("Sort", "BUY_PRICE");
		json.put("SortCondition", "ASC");
		
		json.put("itemName", "10레벨 겁");
		search_c2_save(json);
		json.put("itemName", "10레벨 작");
		search_c2_save(json);
		json.put("itemName", "9레벨 겁");
		search_c2_save(json);
		json.put("itemName", "9레벨 작");
		search_c2_save(json);
		json.put("itemName", "8레벨 겁");
		search_c2_save(json);
		json.put("itemName", "8레벨 작");
		search_c2_save(json);
		json.put("itemName", "7레벨 겁");
		search_c2_save(json);
		json.put("itemName", "7레벨 작");
		search_c2_save(json);
	}
	
	String search_c1_save(JSONObject json) throws Exception {
		String str = "";

		String paramUrl = lostArkAPIurl + "/markets/items";
		String returnData;
		try {
			returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
		}catch(Exception e){
			throw new Exception("E0004");
		}
		
		try {
			HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,
					new TypeReference<Map<String, Object>>() {
					});
			List<HashMap<String, Object>> itemMap = (List<HashMap<String, Object>>) rtnMap.get("Items");
			
			botService.insertMarketItemList(itemMap);
			
			/*
			for (HashMap<String, Object> item : itemMap) {
				str += item.get("Name") + " - " ;
				str += item.get("CurrentMinPrice");
				
				
			}*/
			
		}catch(Exception e){
		}

		

		return str;
	}
	
	
	String search_c2_save(JSONObject json) throws Exception {
		String str = "";

		try {

			String paramUrl = lostArkAPIurl + "/auctions/items";
			String returnData;
			try {
				returnData = LoaApiUtils.connect_process_post(paramUrl, json.toString());
			}catch(Exception e){
				throw new Exception("E0004");
			}
			
			HashMap<String, Object> rtnMap = new ObjectMapper().readValue(returnData,
					new TypeReference<Map<String, Object>>() {
					});
			List<HashMap<String, Object>> itemMap = (List<HashMap<String, Object>>) rtnMap.get("Items");
			HashMap<String, Object> item = itemMap.get(0);
			HashMap<String, Object> auctionInfo = (HashMap<String, Object>) item.get("AuctionInfo");
			auctionInfo.put("item_name", item.get("Name"));
			//Name, BuyPrice
			botService.insertAuctionItemOne(auctionInfo);
			
		} catch (Exception e) {
			e.printStackTrace();
			str = "";
		}

		return str;
	}

	
}
