package my.prac.core.prjgame1.service;

import java.util.HashMap;
import java.util.List;

public interface Game1Service {

	public int saveGame1CntTx(HashMap<String, Object> hashMap) throws Exception;

	public int saveGame3CntTx(HashMap<String, Object> hashMap) throws Exception;

	public List<HashMap<String, Object>> selectGame3Cnt() throws Exception;

	public List<HashMap<String, Object>> selectGameCnt(HashMap<String, Object> hashMap) throws Exception;

	public int saveGameCntTx(HashMap<String, Object> hashMap) throws Exception;
}
