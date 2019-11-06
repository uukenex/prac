package my.prac.core.prjtest.dao;

import java.util.HashMap;

import org.springframework.stereotype.Repository;

@Repository("core.prjtest.TestDAO")
public interface TestDAO {
	public int insertUsertracking(HashMap<String, Object> hashMap);
}
