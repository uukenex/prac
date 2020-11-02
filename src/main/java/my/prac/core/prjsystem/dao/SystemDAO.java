package my.prac.core.prjsystem.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import my.prac.core.dto.Code;
import my.prac.core.dto.Config;

@Repository("core.prjsystem.SystemDAO")
public interface SystemDAO {
	public List<Code> selectCodeList(HashMap<String, Object> hashMap);

	public List<Config> selectConfigList(HashMap<String, Object> hashMap);

}
