package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import my.prac.core.ext.dto.MerchantMasterVO;
import my.prac.core.ext.dto.MerchantReportVO;

@Repository("core.prjbot.BotExtDAO")
public interface BotExtDAO {
	  int insertMerchantReport(MerchantReportVO vo);
	  List<MerchantMasterVO> selectMerchantMasterAll();  // ★ 기준데이터 전체 조회
	  List<HashMap<String, Object>> selectMerchantByTimeVal(HashMap<String, Object> param);
}

