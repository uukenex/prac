package my.prac.api.user.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 게시판 로그인 진입/로그아웃.
 * [2026-10-08] 아이디/비밀번호 로그인과 회원가입(loginUser, pop_loginUser, directloginUser, joinOk, 아이디/닉네임 중복확인, 아이디 찾기)을
 * 전부 없애고 카카오 로그인(BoardKakaoLoginController)만 쓴다. 이 컨트롤러는 로그인 화면(/loginCheck)과 로그아웃만 담당한다.
 * (예전 joinOk는 가입 성공 여부와 상관없이 입력한 아이디로 세션을 만들어서, THJEON 같은 기존 아이디로 POST만 보내도 그 사용자가 되는 구멍이 있었다.)
 */
@Controller
public class UserController {
	static Logger logger = LoggerFactory.getLogger(UserController.class);

	/** [2026-10-07] 로그인/로그아웃 뒤 이동 주소는 이 사이트 안의 주소만 허용한다(외부 사이트에서 링크로 넘어와 로그인하면 그 사이트로 보내던 오픈 리다이렉트 방지).
	 *  null/외부 주소/`//` 프로토콜 상대 주소는 자유게시판으로. */
	static String safeReturnUrl(HttpServletRequest request, String url) {
		final String dflt = "/newboard/free?page=1";
		if (url == null || url.trim().isEmpty()) return dflt;
		if (url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")) return url;
		try {
			java.net.URI u = new java.net.URI(url);
			String scheme = u.getScheme();
			if (u.getHost() != null && u.getHost().equalsIgnoreCase(request.getServerName())
					&& ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return url;
		} catch (Exception ignore) {
			// 잘못된 주소는 기본값
		}
		return dflt;
	}

	/** 로그인 화면 -- 카카오 로그인 버튼만 있다. 이미 로그인했으면 자유게시판으로. 로그인 뒤 돌아갈 주소(returnUrl)를 세션에 정해 둔다. */
	@RequestMapping(value = "/loginCheck", method = RequestMethod.GET)
	public String loginCheck(Model model, HttpServletRequest request, HttpSession session,
			@RequestParam(value = "err", defaultValue = "") String err) {
		if (session.getAttribute("Users") != null) return "redirect:/newboard/free?page=1";
		model.addAttribute("err", err);
		if (!err.isEmpty()) return "nonsession/login/loginCheck"; // 카카오 인증 실패로 돌아온 경우 -- 돌아갈 주소는 그대로 둔다
		// 로그인이 필요한 화면(/session/**)에서 넘어온 경우 원래 가려던 주소로 돌아간다(Referer가 없는 직접 접근도 로그인 화면을 보여줌).
		String target = (String) session.getAttribute("loginTarget");
		if (target != null) {
			session.removeAttribute("loginTarget");
			session.setAttribute("returnUrl", target);
		} else {
			session.setAttribute("returnUrl", safeReturnUrl(request, request.getHeader("Referer")));
		}
		return "nonsession/login/loginCheck";
	}

	@RequestMapping(value = "/logout", method = RequestMethod.GET)
	public String logoutUser(HttpServletRequest request, HttpSession session) {
		String referer = request.getHeader("Referer");

		session.invalidate();
		return "redirect:" + safeReturnUrl(request, referer);
	}

	@RequestMapping(value = "/autoLogout", method = RequestMethod.GET)
	public String autoLogout(Model model) {
		return "nonsession/login/logout";
	}
}
