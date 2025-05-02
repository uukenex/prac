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
	String search_c1() throws Exception{
		JSONObject json ;
		String resMsg= "";
		
		json = new JSONObject();
		
		json.put("CategoryCode", "40000");
		json.put("Sort", "CURRENT_MIN_PRICE");
		json.put("SortCondition", "DESC");
		json.put("ItemGrade", "유물");
		
		json.put("PageNo", "1");
		resMsg += search_c1_save(json);
		
		json.put("PageNo", "2");
		resMsg += search_c1_save(json);
		
		json.put("PageNo", "3");
		resMsg += search_c1_save(json);
		
		resMsg = engraveBook(resMsg);
		return resMsg;
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
	
	String engraveBook(String str_list) throws Exception{
		String[] arr = str_list.split(enterStr);
		
		String res1 = enterStr;
		String res2 = enterStr;
		String res3 = enterStr;
		
		for(String str:arr) {
			str = LoaApiUtils.filterTextForEngrave(str);
			if(str.indexOf("[D]") >= 0) {
				res1 += str+enterStr;
			}else if(str.indexOf("[S]") >= 0) {
				res2 += str+enterStr;
			}else {
				res3 += str+enterStr;
			}
		}
		return res1+res2+res3;
	}
}
