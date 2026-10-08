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

	// [2026-10-08] 회원가입/아이디 로그인은 없애고 카카오 로그인만 쓴다 -- 옛 진입 주소는 로그인 화면(/loginCheck)으로 보낸다.
	@RequestMapping("/join")
	public String join(Model model) {
		return "redirect:/loginCheck";
	}

	@RequestMapping("/login")
	public String login(Model model, HttpSession session, HttpServletRequest request) {
		return "redirect:/loginCheck";
	}

	@RequestMapping("/pop_login")
	public String popLogin(Model model, HttpSession session, HttpServletRequest request) {
		return "redirect:/loginCheck";
	}

	@RequestMapping("/mainpage")
	public String mainpage(Model model) {
		return "nonsession/freeboard/freeboard";
	}

}
