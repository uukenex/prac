package my.prac.api.board.controller;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import my.prac.core.prjboard.service.CommentService;

@Controller
public class BasicController{
	static Logger logger = LoggerFactory.getLogger(BasicController.class);

	@Resource(name = "core.prjboard.CommentService")
	CommentService commentService;

	@RequestMapping("/join")
	public String join(Model model) {
		return "nonsession/join/join";
	}

	@RequestMapping("/login")
	public String login(Model model, HttpSession session, HttpServletRequest request) {
		return "nonsession/login/login";
	}

	@RequestMapping("/pop_login")
	public String popLogin(Model model, HttpSession session, HttpServletRequest request) {
		return "nonsession/popup/pop_login";
	}

	@RequestMapping("/mainpage")
	public String mainpage(Model model) {
		return "nonsession/freeboard/freeboard";
	}

}
