package my.prac.core.prjbot.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import my.prac.core.ext.dto.MerchantMasterVO;
import my.prac.core.ext.dto.MerchantReportVO;

@Repository("core.prjbot.BotExtDAO")
public interface BotExtDAO {
	  int insertMerchantReport(MerchantReportVO vo);
	  List<MerchantMasterVO> selectMerchantMasterAll();  // ★ 기준데이터 전체 조회
}

