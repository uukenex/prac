package my.prac.api.system.controller;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import javax.annotation.Resource;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.dto.Code;
import my.prac.core.dto.Config;
import my.prac.core.prjsystem.service.SystemService;
import my.prac.core.util.CaptchaValidation;
import my.prac.core.util.PropsUtil;

@Controller
public class SystemController {
	static Logger logger = LoggerFactory.getLogger(SystemController.class);

	final static String reCaptchaServerKey = PropsUtil.getProperty("keys","reCaptchaServerKey");
	final static String reCaptchaSiteKey = PropsUtil.getProperty("keys","reCaptchaSiteKey");
	final static String SITE_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

	@Resource(name = "core.prjsystem.SystemService")
	SystemService systemService;

	private List<Code> codeList = null;
	private List<Config> configList = null;
	
	
	
	@RequestMapping(value = "/validation", method = RequestMethod.POST)
	public @ResponseBody int VerifyRecaptcha(HttpServletRequest request) {
		CaptchaValidation.setSecretKey(reCaptchaServerKey);
	    String gRecaptchaResponse = request.getParameter("recaptcha");
	    
	    System.out.println("validation..");
	    try {
	       if(CaptchaValidation.verify(gRecaptchaResponse)) {
	    	   System.out.println("ok");
	    	   return 0; // 성공 
	       }else {
	    	   System.out.println("fail");
	    	   return 1; // 실패
	       }
	    } catch (Exception e) {
	    	System.out.println("err");
	        e.printStackTrace();
	        return -1; //에러
	    }
	}
	
	@RequestMapping(value = "/test", method = RequestMethod.GET)
	public String test(HttpSession session, Model model) throws Exception {
		model.addAttribute("reCaptchaSiteKey",reCaptchaSiteKey);
		return "test";
	}

	
	@RequestMapping(value = "/code_refresh", method = RequestMethod.GET)
	public String refresh(HttpSession session, Model model) throws Exception {
		codeList = systemService.selectCodeList(null);
		configList = systemService.selectConfigList(null);
		return "index";
	}

	public List<Code> getCodeList() throws Exception {
		return codeList;
	}

	public List<Config> getConfigList() throws Exception {
		return configList;
	}
}
