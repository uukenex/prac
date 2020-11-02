package my.prac.core.prjsystem.service;

import java.util.HashMap;
import java.util.List;

import my.prac.core.dto.Code;
import my.prac.core.dto.Config;

public interface SystemService {
	public List<Code> selectCodeList(HashMap<String, Object> hashMap) throws Exception;

	public List<Config> selectConfigList(HashMap<String, Object> hashMap) throws Exception;
}
