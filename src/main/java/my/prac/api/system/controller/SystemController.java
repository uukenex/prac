package my.prac.api.system.controller;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import my.prac.core.dto.Code;
import my.prac.core.dto.Config;
import my.prac.core.prjsystem.service.SystemService;

@Controller
public class SystemController {
	static Logger logger = LoggerFactory.getLogger(SystemController.class);

	@Resource(name = "core.prjsystem.SystemService")
	SystemService systemService;

	private List<Code> codeList = null;
	private List<Config> configList = null;

	@PostConstruct
	public void codeSettings() throws Exception {

		codeList = systemService.selectCodeList(null);
		configList = systemService.selectConfigList(null);

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
