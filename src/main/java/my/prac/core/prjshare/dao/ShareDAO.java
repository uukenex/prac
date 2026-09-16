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

	public int selectVersionCheck(HashMap<String, Object> paramMap);
	
	public List<Integer> selectShareHistList(int shareNo);

	// [2026-09-16 신설] 공유게시판 글을 비밀게시판으로 "이동"하기 위한 삭제(이동 = 비밀게시판에
	// 복사 insert 후 원본을 여기서 삭제, SecretController 참고). 기존엔 공유게시판에 삭제
	// 기능 자체가 없었음.
	public int deleteShare(int shareNo);

	public int deleteShareHist(int shareNo);
}
