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

import my.prac.core.dto.Users;
import my.prac.core.prjgame1.service.Game1Service;

@Controller
public class GameHistController {
	static Logger logger = LoggerFactory.getLogger(GameHistController.class);
	@Resource(name = "core.prjgame1.Game1Service")
	Game1Service game1Service;

	@RequestMapping(value = "/game2_2", method = RequestMethod.GET)
	public String game2_2(Model model, @RequestParam(required = false, defaultValue = "10") int cell) {
		if (cell > 50) {
			cell = 50;
		}
		if (cell < 10) {
			cell = 10;
		}
		model.addAttribute("cell", cell);
		return "session/games_hist/game2_2";
	}

	@RequestMapping(value = "/game4_1", method = RequestMethod.GET)
	public String game4_1(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {
		return "session/games_hist/game4_1";
	}

	@RequestMapping(value = "/game4_2", method = RequestMethod.GET)
	public String game4_2(Model model, @RequestParam(required = false, defaultValue = "10") int cell) throws Exception {
		return "session/games_hist/game4_2";
	}

}
