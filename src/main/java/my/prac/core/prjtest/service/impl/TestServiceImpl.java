package my.prac.core.prjtest.service.impl;

import java.util.HashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjtest.dao.TestDAO;
import my.prac.core.prjtest.service.TestService;

@Service("core.prjtest.TestService")
public class TestServiceImpl implements TestService {
	@Resource(name = "core.prjtest.TestDAO")
	TestDAO testDao;

	@Override
	public String transactionTx(HashMap<String, Object> map) {
		map.put("userId", "test1");
		map.put("ipAddr", "0");
		map.put("sessionId", "0");
		map.put("command", "test1");
		map.put("programId", "login");
		testDao.insertUsertracking(map);
		System.out.println(1 / 0);
		map.put("userId", "test2");
		map.put("ipAddr", "0");
		map.put("sessionId", "0");
		map.put("command", "test2");
		map.put("programId", "login");
		testDao.insertUsertracking(map);
		return null;
	}

}
