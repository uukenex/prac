package my.prac.core.prjbot.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotS3DAO;
import my.prac.core.prjbot.service.BotS3Service;

@Service("core.prjbot.BotS3Service")
public class BotS3ServiceImpl implements BotS3Service {

	@Resource(name = "core.prjbot.BotS3DAO")
	BotS3DAO botS3DAO;

	@Override
	public List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map) {
		return botS3DAO.selectHellTop3Contributors(map);
	}

}
