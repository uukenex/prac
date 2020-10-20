package my.prac.api.games.controller;

import java.util.HashMap;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjgame1.service.Game1Service;

@Controller
public class GameAjaxController {
	static Logger logger = LoggerFactory.getLogger(GameAjaxController.class);
	@Resource(name = "core.prjgame1.Game1Service")
	Game1Service game1Service;

	@RequestMapping(value = "/gameUtil/insertGameCnt", method = RequestMethod.POST)
	public @ResponseBody HashMap<String, Object> gameUtil_insertGameCnt(@RequestParam String msg,
																		@RequestParam String userName, 
																		@RequestParam String mediaCode, 
																		@RequestParam String ip,
																		@RequestParam int cnt, 
																		@RequestParam String gameNo, 
																		@RequestParam String dbDate,
																		@RequestParam String flag){
		HashMap<String, Object> rtnMap = new HashMap<>();
		HashMap<String, Object> hashMap = new HashMap<>();
		
		hashMap.put("userName", userName);
		hashMap.put("msg", msg);
		hashMap.put("mediaCode", mediaCode);
		hashMap.put("ip", ip);
		hashMap.put("cnt", cnt);
		hashMap.put("gameSeq", gameNo);
		hashMap.put("dbDate", dbDate);
		hashMap.put("flag", flag);

		try {
			game1Service.saveGameCntTx(hashMap);
			rtnMap.put("CODE", "OK");
		} catch (Exception e) {
			rtnMap.put("CODE", "ERR");
		}

		return rtnMap;
	}

	@RequestMapping(value = "/gameUtil/selectGameCnt", method = RequestMethod.GET)
	public @ResponseBody HashMap<String, Object> gameUtil_selectGameCnt(@RequestParam String gameNo) {
		HashMap<String, Object> rtnMap = new HashMap<>();
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("gameSeq", gameNo);

		try {
			rtnMap.put("RESULT", game1Service.selectGameCnt(hashMap));
			rtnMap.put("CODE", "OK");
		} catch (Exception e) {
			rtnMap.put("CODE", "ERR");
		}

		return rtnMap;
	}
}
