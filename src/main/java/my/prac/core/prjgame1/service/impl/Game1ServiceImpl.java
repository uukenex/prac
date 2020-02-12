package my.prac.core.prjgame1.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.prjgame1.dao.Game1DAO;
import my.prac.core.prjgame1.service.Game1Service;

@Service("core.prjgame1.Game1Service")
public class Game1ServiceImpl implements Game1Service {
	@Resource(name = "core.prjgame1.Game1DAO")
	Game1DAO game1DAO;

	@Override
	public int saveGame1CntTx(HashMap<String, Object> hashMap) throws Exception {
		return game1DAO.insertGame1Cnt(hashMap);
	}

	@Override
	public int saveGame3CntTx(HashMap<String, Object> hashMap) throws Exception {
		return game1DAO.insertGame3Cnt(hashMap);
	}

	@Override
	public List<HashMap<String, Object>> selectGame3Cnt() throws Exception {
		return game1DAO.selectGame3Cnt();
	}

}
