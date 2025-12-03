package my.prac.core.prjbot.dao;

import org.springframework.stereotype.Repository;

import my.prac.core.ext.dto.MerchantReportVO;

@Repository("core.prjbot.BotExtDAO")
public interface BotExtDAO {
	  int insertMerchantReport(MerchantReportVO vo);
}

