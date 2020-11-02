package my.prac.core.prjsystem.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.dto.Code;
import my.prac.core.dto.Config;
import my.prac.core.prjsystem.dao.SystemDAO;
import my.prac.core.prjsystem.service.SystemService;

@Service("core.prjsystem.SystemService")
public class SystemServiceImpl implements SystemService {
	@Resource(name = "core.prjsystem.SystemDAO")
	SystemDAO systemDAO;

	@Override
	public List<Code> selectCodeList(HashMap<String, Object> hashMap) throws Exception {
		return systemDAO.selectCodeList(hashMap);
	}

	@Override
	public List<Config> selectConfigList(HashMap<String, Object> hashMap) throws Exception {
		return systemDAO.selectConfigList(hashMap);
	}
}
