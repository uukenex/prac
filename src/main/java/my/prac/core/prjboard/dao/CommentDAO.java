package my.prac.core.prjboard.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import my.prac.core.dto.CommentReply;
import my.prac.core.dto.Comments;

@Repository("core.prjboard.CommentDAO")
public interface CommentDAO{
	// 단일 게시글 보기
	public Comments selectComment(int commentNo);

	// 게시글 쓰기(공지사항은 관리자만 작성가능)
	public int insertComment(HashMap<String, Object> hashMap);

	// 게시글 수정(작성자만 가능)
	public int updateComment(HashMap<String, Object> hashMap);

	// 게시글 삭제(작성자만 가능)
	public int deleteComment(int commentNo);

	// 페이지당 리스트를 보여줌 분류별로 있어야함
	public List<Comments> getCommentByPage(HashMap<String, Object> hashMap);

	// 이름으로 검색 페이지당 리스트를 보여줌
	public List<Comments> searchCommentByNameOfPage(HashMap<String, Object> hashMap);

	// 내용으로 검색 페이지당 리스트를 보여줌
	public List<Comments> searchCommentByContentOfPage(HashMap<String, Object> hashMap);

	// 닉네임으로 검색 페이지당 리스트를 보여줌
	public List<Comments> searchCommentByNickOfPage(HashMap<String, Object> hashMap);

	// 조회수 올리기 기능
	public int updateCommentCount(int commentNo);

	// 총 게시글이 몇개인지 카운트
	public int pageCount(String commentCategory);

	public int selectNo();

	public List<Comments> selectNoticeTop5();

	public List<Comments> selectFreeTop5();

	// 특정 게시글번호로 되어있는 댓글목록 조회 list
	public List<CommentReply> selectReplyList(int replyCommentNo);

	// 댓글 달기 기능 map으로 데이터 받음.댓글내용,게시글번호,작성자
	public int insertReply(HashMap<String, Object> hashMap);

	// 댓글 삭제 기능 번호로
	public int deleteReply(int replyNo);

	// 특정 게시글 번호로 되어있는 댓글삭제-이건 글삭제할때만 들어감
	public int deleteReplyByCommentNo(int replyCommentNo);

}
