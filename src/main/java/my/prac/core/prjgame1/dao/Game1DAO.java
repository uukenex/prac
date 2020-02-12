package my.prac.core.prjgame1.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository("core.prjgame1.Game1DAO")
public interface Game1DAO {
	public int insertGame1Cnt(HashMap<String, Object> hashMap);

	public int insertGame3Cnt(HashMap<String, Object> hashMap);

	public List<HashMap<String, Object>> selectGame3Cnt();

}
