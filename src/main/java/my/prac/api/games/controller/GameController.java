package my.prac.api.games.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class GameController {
	static Logger logger = LoggerFactory.getLogger(GameController.class);

	@RequestMapping(value = "/game1", method = RequestMethod.GET)
	public String game1(Model model) {
		return "games/game1";
	}
}
