package my.prac.core.prjbot.service.impl;

import java.util.HashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotNewDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotNewService;

@Service("core.prjbot.BotNewService")
public class BotNewServiceImpl implements BotNewService {
	
	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	@Resource(name = "core.prjbot.BotDAO")
	BotNewDAO botNewDAO;
	
	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;
	
	public int insertBotPointNew(HashMap<String,Object> map) {
		return botNewDAO.insertBotPointNew(map);
	}
}
