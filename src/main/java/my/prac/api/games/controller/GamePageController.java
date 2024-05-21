package my.prac.api.games.controller;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import my.prac.api.system.controller.SystemController;
import my.prac.core.dto.Users;
import my.prac.core.prjgame1.service.Game1Service;

@Controller
public class GamePageController {
	static Logger logger = LoggerFactory.getLogger(GamePageController.class);
	@Resource(name = "core.prjgame1.Game1Service")
	Game1Service game1Service;

	@Autowired
	SystemController systemController;

	/******************** Main games *********************/

	@RequestMapping(value = "/game11", method = RequestMethod.GET)
	public String game11(HttpSession session, Model model) {
		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game11";
	}
	
	@RequestMapping(value = "/game1", method = RequestMethod.GET)
	public String game1(HttpSession session, Model model) {
		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game1";
	}

	@RequestMapping(value = "/game2", method = RequestMethod.GET)
	public String game2(HttpSession session, Model model,
			@RequestParam(required = false, defaultValue = "10") int cell) {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}
		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("cell", cell);
		return "session/games/game2";
	}

	@RequestMapping(value = "/game3", method = RequestMethod.GET)
	public String game3(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}
		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("cell", cell);
		return "session/games/game3";
	}

	@RequestMapping(value = "/game4", method = RequestMethod.GET)
	public String game4(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {
		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game4t";
	}

	@RequestMapping(value = "/game5", method = RequestMethod.GET)
	public String game5(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game5";
	}

	@RequestMapping(value = "/game5_1", method = RequestMethod.GET)
	public String game5_1(HttpSession session, Model model,
			@RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game5_1";
	}

	@RequestMapping(value = "/game6", method = RequestMethod.GET)
	public String game6(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		return "session/games/game6";
	}

	@RequestMapping(value = "/game6_1", method = RequestMethod.GET)
	public String game6_1noSession(HttpSession session, Model model,
			@RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game6_1";
	}

	@RequestMapping(value = "/game6_2", method = RequestMethod.GET)
	public String game6_2noSession(HttpSession session, Model model,
			@RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game6_2";
	}

	@RequestMapping(value = "/game7", method = RequestMethod.GET)
	public String game7noSession(HttpSession session, Model model,
			@RequestParam(required = false, defaultValue = "10") int cell) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game7";
	}

	@RequestMapping(value = "/game8", method = RequestMethod.GET)
	public String game8noSession(HttpSession session, Model model) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game8";
	}

	@RequestMapping(value = "/game9", method = RequestMethod.GET)
	public String game9noSession(HttpSession session, Model model) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game9";
	}

	@RequestMapping(value = "/game10", method = RequestMethod.GET)
	public String game10noSession(HttpSession session, Model model) throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game10";
	}

	/******************** Main games end *********************/

	@RequestMapping(value = "/session/ws", method = RequestMethod.GET)
	public String ws(Model model) throws Exception {
		return "session/games/ws";
	}

	@RequestMapping(value = "/rank", method = RequestMethod.GET)
	public String gameRank(Model model) throws Exception {
		model.addAttribute("code", systemController.getCodeList());
		model.addAttribute("config", systemController.getConfigList());
		return "session/games/rank";
	}

	@RequestMapping(value = "/ws5", method = RequestMethod.GET)
	public String ws5(HttpSession session, Model model, @RequestParam(required = false, defaultValue = "10") int cell)
			throws Exception {

		Users user = (Users) session.getAttribute("Users");
		if (user != null) {
			model.addAttribute("userId", user.getUserId());
			model.addAttribute("userNick", user.getUserNick());
		}
		model.addAttribute("testMode", "true");
		return "session/games/game6_1";
	}

}
