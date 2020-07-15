package my.prac.api.games.controller;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.dto.Users;
import my.prac.core.prjgame1.service.Game1Service;

@Controller
public class GameController {
	static Logger logger = LoggerFactory.getLogger(GameController.class);
	@Resource(name = "core.prjgame1.Game1Service")
	Game1Service game1Service;

	@RequestMapping(value = "/session/game1", method = RequestMethod.GET)
	public String game1(Model model) {
		return "session/games/game1";
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

	@RequestMapping(value = "/session/game2", method = RequestMethod.GET)
	public String game2(Model model, @RequestParam(required = false, defaultValue = "10") int cell) {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}
		model.addAttribute("cell", cell);
		return "session/games/game2";
	}

	@RequestMapping(value = "/game2_2", method = RequestMethod.GET)
	public String game2_2(Model model, @RequestParam(required = false, defaultValue = "10") int cell) {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}
		model.addAttribute("cell", cell);
		return "session/games/game2_2";
	}

	@RequestMapping(value = "/session/game3", method = RequestMethod.GET)
	public String game3(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}

		model.addAttribute("rank", game1Service.selectGame3Cnt());
		model.addAttribute("cell", cell);
		return "session/games/game3";
	}

	// 댓글달기 ajax
	@RequestMapping(value = "/game3/saveGame3Cnt", method = RequestMethod.POST)
	public @ResponseBody String game3ajax(@RequestParam String userName, @RequestParam String mediaCode,
			@RequestParam String ip, @RequestParam int cnt) {
		String resultMsg = "0";
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("userName", userName);
		hashMap.put("mediaCode", mediaCode);
		hashMap.put("ip", ip);
		hashMap.put("cnt", cnt);
		try {
			game1Service.saveGame3CntTx(hashMap);
		} catch (Exception e) {
			resultMsg = "save ERR";
		}

		return resultMsg;
	}

	@RequestMapping(value = "/session/game4", method = RequestMethod.GET)
	public String game4(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		return "session/games/game4";
	}

	@RequestMapping(value = "/session/game4_1", method = RequestMethod.GET)
	public String game4_1(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		return "session/games/game4_1";
	}

	@RequestMapping(value = "/session/game4_2", method = RequestMethod.GET)
	public String game4_2(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		return "session/games/game4_2";
	}

	@RequestMapping(value = "/game4/saveGame4Cnt", method = RequestMethod.POST)
	public @ResponseBody HashMap<String, Object> game4ajax(@RequestParam String userName,
			@RequestParam String mediaCode, @RequestParam String ip, @RequestParam int cnt) {
		HashMap<String, Object> rtnMap = new HashMap<>();

		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("userName", userName);
		hashMap.put("mediaCode", mediaCode);
		hashMap.put("ip", ip);
		hashMap.put("cnt", cnt);
		hashMap.put("gameSeq", 4);

		try {
			game1Service.saveGameCntTx(hashMap);
			rtnMap.put("CODE", "OK");
		} catch (Exception e) {
			rtnMap.put("CODE", "ERR");
		}

		return rtnMap;
	}

	@RequestMapping(value = "/game4/selectGame4Cnt", method = RequestMethod.GET)
	public @ResponseBody HashMap<String, Object> selectGame4Cnt(@RequestParam String gameNo) {
		HashMap<String, Object> rtnMap = new HashMap<>();
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("gameSeq", 4);

		try {
			rtnMap.put("RESULT", game1Service.selectGameCnt(hashMap));
			rtnMap.put("CODE", "OK");
		} catch (Exception e) {
			rtnMap.put("CODE", "ERR");
		}

		return rtnMap;
	}

	@RequestMapping(value = "/session/game5", method = RequestMethod.GET)
	public String game5(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game5";
	}

	@RequestMapping(value = "/session/ws", method = RequestMethod.GET)
	public String ws(Model model) throws Exception {

		return "session/games/ws";
	}

	@RequestMapping(value = "/game/rank", method = RequestMethod.GET)
	public String gameRank(Model model) throws Exception {

		return "session/games/rank";
	}

	@RequestMapping(value = "/gameUtil/insertGameCnt", method = RequestMethod.POST)
	public @ResponseBody HashMap<String, Object> gameUtil_insertGameCnt(@RequestParam String msg,
			@RequestParam String userName, @RequestParam String mediaCode, @RequestParam String ip,
			@RequestParam int cnt, @RequestParam String gameNo, @RequestParam String dbDate,
			@RequestParam String flag) {
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
