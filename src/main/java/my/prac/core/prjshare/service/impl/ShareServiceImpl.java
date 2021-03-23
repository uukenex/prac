package my.prac.core.prjshare.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.dto.Shareboard;
import my.prac.core.prjshare.dao.ShareDAO;
import my.prac.core.prjshare.service.ShareService;

@Service("core.prjshare.ShareService")
public class ShareServiceImpl implements ShareService {
	@Resource(name = "core.prjshare.ShareDAO")
	ShareDAO shareDAO;

	@Override
	public List<Shareboard> selectShareListByPage(int page) {
		return shareDAO.selectShareListByPage(page);
	}

	@Override
	public int selectSharePageCount() {
		return shareDAO.selectSharePageCount();
	}

	@Override
	public Shareboard selectShareHist(HashMap<String, Object> shareMap) {
		return shareDAO.selectShareHist(shareMap);
	}

	@Override
	public Shareboard selectShare(int shareNo) {
		return shareDAO.selectShare(shareNo);
	}

	@Override
	public int insertShareTx(Shareboard share) {
		int rtn = shareDAO.insertShare(share);
		if (rtn < 1) {
			System.out.println("insertShare rtn 0");
		}
		int newRtn = shareDAO.selectMaxShareNo(share);
		return newRtn;
	}

	@Override
	public int updateShareTx(Shareboard share) {
		int rtn = 0;
		rtn = shareDAO.insertShareHist(shareDAO.selectShare(share.getShareNo()));
		if (rtn < 1) {
			System.out.println("insertShareHist rtn 0");
		}

		rtn = shareDAO.updateShare(share);
		if (rtn < 1) {
			System.out.println("updateShare rtn 0");
		}

		return rtn;
	}

}
