package my.prac.api.test.controller;

import java.util.HashMap;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import my.prac.core.prjtest.service.TestService;

@Controller
public class TestController {
	static Logger logger = LoggerFactory.getLogger(TestController.class);

	@Resource(name = "core.prjtest.TestService")
	TestService testService;

	@RequestMapping(value = "/transactionTest", method = RequestMethod.GET)
	public String loginCheck(HttpServletRequest request, HttpSession session) {
		HashMap<String, Object> hashMap = new HashMap<>();
		testService.transactionTx(hashMap);

		return "";
	}
}
