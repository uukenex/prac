package my.prac.core.prjshare.service;

import java.util.HashMap;
import java.util.List;

import my.prac.core.dto.Shareboard;

public interface ShareService {

	// 페이지당 리스트를 보여줌(공유게시판)
	public List<Shareboard> selectShareListByPage(int page);

	// 분류별 총 게시물 수(공유게시판)
	public int selectSharePageCount();

	// 단일 게시글 보기
	public Shareboard selectShare(int shareNo);

	// 히스토리 보기
	public Shareboard selectShareHist(HashMap<String, Object> shareMap);

	// 게시글 쓰기(공유게시판)
	public int insertShareTx(Shareboard share);

	// 게시글 수정
	public int updateShareTx(Shareboard share);

}
