package my.prac.api.board.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import my.prac.core.dto.Comments;
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;

/**
 * [2026-10-07] 게시판 글 수정/삭제 권한과 검색 결과 비밀글 처리. 예전에는 글 번호만 보내면 로그인/작성자 확인 없이
 * 남의 글을 고치고 지울 수 있었고, 검색 API가 비밀글 본문을 비로그인에게도 내려줬다.
 * 규칙: 로그인한 사용자가 글 작성자이거나 관리자(THJEON)일 때만 수정/삭제.
 */
final class BoardAuth {
	private BoardAuth() {}

	static Users user(HttpSession session) {
		return session == null ? null : (Users) session.getAttribute("Users");
	}

	static boolean isAdmin(Users u) {
		return u != null && "THJEON".equals(u.getUserId());
	}

	static boolean mayModify(CommentService commentService, HttpSession session, String commentNo) {
		try {
			Users u = user(session);
			if (u == null) return false;
			if (isAdmin(u)) return true;
			Comments c = commentService.selectComment(Integer.parseInt(commentNo));
			return c != null && u.getUserId() != null && u.getUserId().equals(c.getUserId());
		} catch (Exception e) {
			return false;
		}
	}

	/** 권한이 없을 때 이동할 주소: 비로그인이면 로그인 화면, 로그인했으면 그 글 보기로. */
	static String denied(HttpSession session, String viewUrl) {
		return user(session) == null ? "redirect:/loginCheck" : "redirect:" + viewUrl;
	}

	/** 비로그인에게는 비밀글 본문을 내려주지 않는다(내용 검색은 비밀글 자체를 결과에서 뺀다 -- 일치 여부로 내용을 추측하지 못하게). */
	static List<Comments> hideSecrets(List<Comments> list, HttpSession session, String category) {
		if (list == null || user(session) != null) return list;
		List<Comments> out = new ArrayList<>();
		for (Comments c : list) {
			if ("Y".equals(c.getSecretYn())) {
				if ("내용".equals(category)) continue;
				c.setCommentContent("");
			}
			out.add(c);
		}
		return out;
	}
}
