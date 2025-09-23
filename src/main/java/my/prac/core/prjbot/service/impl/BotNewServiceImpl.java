package my.prac.core.prjbot.service.impl;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotNewService;

@Service("core.prjbot.BotNewService")
public class BotNewServiceImpl implements BotNewService {
	
	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	
	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;
	
}
