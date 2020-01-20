package my.prac.api.games.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import my.prac.core.games.Game;

@Controller
public class GameController {
	static Logger logger = LoggerFactory.getLogger(GameController.class);

	@RequestMapping(value = "/game", method = RequestMethod.GET)
	public String game1(Model model) {
		Game game = new Game();
		return "redirect:/free?page=1";
	}
}
