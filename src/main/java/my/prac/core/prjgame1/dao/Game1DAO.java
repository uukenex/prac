package my.prac.core.prjgame1.dao;

import java.util.HashMap;

import org.springframework.stereotype.Repository;

@Repository("core.prjgame1.Game1DAO")
public interface Game1DAO {
	public int insertGame1Cnt(HashMap<String, Object> hashMap);

}
