package my.prac.core.prjboard.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.dto.CommentReply;
import my.prac.core.dto.Comments;
import my.prac.core.prjboard.dao.CommentDAO;
import my.prac.core.prjboard.service.CommentService;

@Service("core.prjboard.CommentService")
public class CommentServiceImpl implements CommentService {
	@Resource(name = "core.prjboard.CommentDAO")
	CommentDAO crepo;

	public int count(int commentNo) {
		return crepo.updateCommentCount(commentNo);
	}

	// 단일 게시글 보기
	@Override
	public Comments selectComment(int commentNo) {

		return crepo.selectComment(commentNo);
	}

	// 게시글 쓰기(공지사항)
	@Override
	public int writeNoticeComment(String commentName, String commentContent, String userId) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "공지사항");
		map.put("commentName", commentName);
		map.put("commentContent", commentContent);
		map.put("userId", userId);

		return crepo.insertComment(map);
	}

	// 게시글 쓰기(자유게시판)
	@Override
	public int writeFreeComment(String commentName, String commentContent, String userId) {
		return writeFreeComment(commentName, commentContent, userId, "N");
	}

	// 게시글 쓰기(자유게시판) - 비밀글 여부 포함
	@Override
	public int writeFreeComment(String commentName, String commentContent, String userId, String secretYn) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "자유게시판");
		map.put("commentName", commentName);
		map.put("commentContent", commentContent);
		map.put("userId", userId);
		map.put("secretYn", "Y".equals(secretYn) ? "Y" : "N");

		return crepo.insertComment(map);
	}

	// [2026-09-16 신설] 게시글 쓰기(비밀게시판) -- 로그인 유저만 접근 가능한 별도 게시판이라
	// SECRET_YN은 항상 'Y'로 고정.
	@Override
	public int writeSecretComment(String commentName, String commentContent, String userId) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "비밀게시판");
		map.put("commentName", commentName);
		map.put("commentContent", commentContent);
		map.put("userId", userId);
		map.put("secretYn", "Y");

		return crepo.insertComment(map);
	}

	// 게시글 수정
	@Override
	public int updateComment(int commentNo, String commentName, String commentContent) {
		return updateComment(commentNo, commentName, commentContent, "N");
	}

	// 게시글 수정 - 비밀글 여부 포함
	@Override
	public int updateComment(int commentNo, String commentName, String commentContent, String secretYn) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentName", commentName);
		map.put("commentContent", commentContent);
		map.put("commentNo", commentNo);
		map.put("secretYn", "Y".equals(secretYn) ? "Y" : "N");

		return crepo.updateComment(map);
	}

	// 게시글 삭제 --댓글까지 지워야함

	@Override
	@Transactional
	public int deleteComment(int commentNo) {
		int result = crepo.deleteReplyByCommentNo(commentNo);
		result = crepo.deleteComment(commentNo);
		return result;
	}

	// [2026-09-16 신설] 자유게시판 글을 비밀게시판으로 이동(카테고리만 변경, 댓글은 COMMENT_NO로
	// 연결돼 있어 그대로 따라감) -- thjeon 유저 전용 이동 버튼용(권한 체크는 컨트롤러에서).
	@Override
	public int moveCommentToSecret(int commentNo) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentNo", commentNo);
		map.put("commentCategory", "비밀게시판");
		return crepo.updateCommentCategory(map);
	}

	// 페이지당 리스트를 보여줌(공지사항)
	@Override
	public List<Comments> noticeListByPage(int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "공지사항");
		map.put("page", page);
		return crepo.getCommentByPage(map);
	}

	// 페이지당 리스트를 보여줌(자유게시판)
	@Override
	public List<Comments> freeListByPage(int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "자유게시판");
		map.put("page", page);
		return crepo.getCommentByPage(map);
	}

	// [2026-09-16 신설] 페이지당 리스트를 보여줌(비밀게시판)
	@Override
	public List<Comments> secretListByPage(int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "비밀게시판");
		map.put("page", page);
		return crepo.getCommentByPage(map);
	}

	// 이름검색 리스트를 페이지별로 보여줌(공지사항)
	@Override
	public List<Comments> noticeSearchListByPage(String commentName, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "공지사항");
		map.put("page", page);
		map.put("commentName", commentName);
		return crepo.searchCommentByNameOfPage(map);
	}

	// 이름검색 리스트를 페이지별로 보여줌(자유게시판)
	@Override
	public List<Comments> freeSearchListByPage(String commentName, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "자유게시판");
		map.put("page", page);
		map.put("commentName", commentName);
		return crepo.searchCommentByNameOfPage(map);
	}

	// [2026-09-16 신설] 이름검색 리스트를 페이지별로 보여줌(비밀게시판)
	@Override
	public List<Comments> secretSearchListByPage(String commentName, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "비밀게시판");
		map.put("page", page);
		map.put("commentName", commentName);
		return crepo.searchCommentByNameOfPage(map);
	}

	// 내용검색으로 리스트를 페이지별로 보여줌(공지)
	@Override
	public List<Comments> noticeSearchContentListByPage(String commentContent, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "공지사항");
		map.put("page", page);
		map.put("commentContent", commentContent);
		return crepo.searchCommentByContentOfPage(map);
	}

	// 내용검색으로 리스트를 페이지별로 보여줌(자유게시판)
	@Override
	public List<Comments> freeSearchContentListByPage(String commentContent, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "자유게시판");
		map.put("page", page);
		map.put("commentContent", commentContent);
		return crepo.searchCommentByContentOfPage(map);
	}

	// [2026-09-16 신설] 내용검색으로 리스트를 페이지별로 보여줌(비밀게시판)
	@Override
	public List<Comments> secretSearchContentListByPage(String commentContent, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "비밀게시판");
		map.put("page", page);
		map.put("commentContent", commentContent);
		return crepo.searchCommentByContentOfPage(map);
	}

	// 닉네임검색으로 리스트를 페이지별로 보여줌(공지)
	@Override
	public List<Comments> noticeSearchNickListByPage(String userNick, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "공지사항");
		map.put("page", page);
		map.put("userNick", userNick);
		return crepo.searchCommentByNickOfPage(map);
	}

	// 닉네임검색으로 리스트를 페이지별로 보여줌(자유게시판)
	@Override
	public List<Comments> freeSearchNickListByPage(String userNick, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "자유게시판");
		map.put("page", page);
		map.put("userNick", userNick);
		return crepo.searchCommentByNickOfPage(map);
	}

	// [2026-09-16 신설] 닉네임검색으로 리스트를 페이지별로 보여줌(비밀게시판)
	@Override
	public List<Comments> secretSearchNickListByPage(String userNick, int page) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("commentCategory", "비밀게시판");
		map.put("page", page);
		map.put("userNick", userNick);
		return crepo.searchCommentByNickOfPage(map);
	}

	@Override
	public int noticePageCount() {
		return crepo.pageCount("공지사항");
	}

	@Override
	public int freePageCount() {
		return crepo.pageCount("자유게시판");
	}

	// [2026-09-16 신설]
	@Override
	public int secretPageCount() {
		return crepo.pageCount("비밀게시판");
	}

	@Override
	public int currentNo() {
		return crepo.selectNo();
	}

	@Override
	public List<Comments> selectNoticeTop5() {
		return crepo.selectNoticeTop5();
	}

	@Override
	public List<Comments> selectFreeTop5() {
		return crepo.selectFreeTop5();
	}

	// 특정 게시글번호로 되어있는 댓글목록 조회 list
	@Override
	public List<CommentReply> selectReplyList(int replyCommentNo) {
		return crepo.selectReplyList(replyCommentNo);
	}

	// 댓글 달기 기능 map으로 데이터 받음.댓글내용,게시글번호,작성자
	@Override
	public int insertReply(String replyContent, int replyCommentNo, String userId) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("replyContent", replyContent);
		map.put("replyCommentNo", replyCommentNo);
		map.put("userId", userId);
		return crepo.insertReply(map);
	}

	// 댓글 삭제 기능 번호로
	@Override
	public int deleteReply(int replyNo) {
		return crepo.deleteReply(replyNo);
	}
}
