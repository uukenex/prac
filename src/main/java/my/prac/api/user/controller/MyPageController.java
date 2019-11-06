package my.prac.api.user.controller;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import my.prac.core.dto.Users;
import my.prac.core.prjuser.service.UserService;

@Controller
public class MyPageController {
	static Logger logger = LoggerFactory.getLogger(MyPageController.class);
	@Resource(name = "core.prjuser.UserService")
	UserService us;

	@RequestMapping(value = "/updateUser", method = RequestMethod.POST)
	public String updateUser(Model model, HttpServletRequest request, HttpSession session) {

		String userId = request.getParameter("changeId");
		String userPass = request.getParameter("changePass");
		MyHash ht = new MyHash();
		userPass = ht.testMD5(userPass);
		String userPhone = request.getParameter("changePhone");
		String userEmail = request.getParameter("changeEmail");
		String userNick = request.getParameter("changeNick");
		if (userPass == "") {
			userPass = request.getParameter("currentPass");
			userPass = ht.testMD5(userPass);
		}
		if (userPhone == "") {
			userPhone = request.getParameter("currentPhone");
		}
		if (userEmail == "") {
			userEmail = request.getParameter("currentEmail");
		}
		if (userNick == "") {
			userNick = request.getParameter("currentNick");
		}
		logger.trace("아이디 : {},비밀번호 : {}, 핸드폰 : {}, 이메일 : {}, 닉네임 : {}", userId, userPass, userPhone, userEmail,
				userNick);

		us.updateUser(userId, userPass, userPhone, userEmail, userNick);
		Users user = us.login(userId);
		session.setAttribute("Users", user);
		session.setAttribute("message", "정보가 변경되었습니다");

		return "redirect:/session/mypageMain";

	}

	class MyHash {
		public String testMD5(String str) {
			String md5Str = "";
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				md.update(str.getBytes());
				byte byteData[] = md.digest();
				StringBuffer sb = new StringBuffer();
				// byte code를 hex format으로 변경
				for (int i = 0; i < byteData.length; i++) {
					sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
				}
				md5Str = sb.toString();

			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				md5Str = null;
			}
			return md5Str;
		}
	}
}
