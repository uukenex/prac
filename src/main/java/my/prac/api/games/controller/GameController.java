package my.prac.api.games.controller;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjgame1.service.Game1Service;

@Controller
public class GameController {
	static Logger logger = LoggerFactory.getLogger(GameController.class);
	@Resource(name = "core.prjgame1.Game1Service")
	Game1Service game1Service;

	@RequestMapping(value = "/game1", method = RequestMethod.GET)
	public String game1(Model model) {
		return "games/game1";
	}

	@RequestMapping(value = "/game1Rank", method = RequestMethod.GET)
	public List<HashMap<String, Object>> game1Rank(Model model) {
		List<HashMap<String, Object>> rankList = null;
		return rankList;
	}

	// 댓글달기 ajax
	@RequestMapping(value = "/game1/saveGame1Cnt", method = RequestMethod.POST)
	public @ResponseBody String ajaxreply(@RequestParam String mediaCode, @RequestParam String ip,
			@RequestParam int cnt) {
		String resultMsg = "0";
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("mediaCode", mediaCode);
		hashMap.put("ip", ip);
		hashMap.put("cnt", cnt);
		try {
			game1Service.saveGame1CntTx(hashMap);
		} catch (Exception e) {
			resultMsg = "save ERR";
		}

		return resultMsg;
	}

	@RequestMapping(value = "/game2", method = RequestMethod.GET)
	public String game2(Model model) {
		return "games/game2";
	}

}
