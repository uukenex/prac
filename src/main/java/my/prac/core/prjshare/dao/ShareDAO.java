package my.prac.core.prjshare.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import my.prac.core.dto.Shareboard;

@Repository("core.prjshare.ShareDAO")
public interface ShareDAO {

	public List<Shareboard> selectShareListByPage(int page);

	public int selectSharePageCount();

	public Shareboard selectShare(int shareNo);

	public int selectMaxShareNo(Shareboard share);

	public int insertShare(Shareboard share);

	public int updateShare(Shareboard share);

	public int insertShareHist(Shareboard share);

	public Shareboard selectShareHist(HashMap<String, Object> shareMap);

}
