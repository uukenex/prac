package my.prac.api.board.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import my.prac.core.dto.Comments;
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;

/**
 * [2026-10-07] 게시글 조회수 집계 규칙(대부분의 게시판 방식).
 * 예전에는 글을 열 때마다 무조건 +1이라 새로고침/본인 조회/비밀글 잠금 화면도 올랐다.
 *  - 같은 브라우저(쿠키)가 같은 글을 24시간 안에 다시 열면 올리지 않는다.
 *  - 작성자 본인이 열 때는 올리지 않는다(글 쓰고 바로 이동하는 redirect 포함).
 *  - 비밀글이 잠긴 화면(내용을 못 보는 경우)은 올리지 않는다.
 *  - 봇/크롤러(User-Agent)는 올리지 않는다.
 * 쿠키 pv = "글번호_조회시각(분)" 목록을 "." 로 이은 값(톰캣 7 쿠키에 쓸 수 있는 문자만 사용), 최대 MAX_KEEP개.
 */
final class BoardViewCounter {
	private static final String COOKIE = "pv";
	private static final long WINDOW_MIN = 24 * 60L;
	private static final int MAX_KEEP = 60;

	private BoardViewCounter() {}

	/** 조건을 만족하면 조회수를 올리고, 화면에 보이는 값(comment)도 +1 해 준다. 실패해도 글 보기는 계속된다. */
	static void count(CommentService commentService, HttpServletRequest req, HttpServletResponse resp,
			HttpSession session, Comments comment, boolean locked) {
		try {
			if (comment == null || comment.getCommentNo() == null || locked) return;
			if (isBot(req)) return;
			Users u = (Users) session.getAttribute("Users");
			if (u != null && u.getUserId() != null && u.getUserId().equals(comment.getUserId())) return; // 작성자 본인
			if (!markViewed(req, resp, comment.getCommentNo())) return; // 24시간 안에 이미 본 글
			commentService.count(comment.getCommentNo());
			comment.setCommentCount((comment.getCommentCount() == null ? 0 : comment.getCommentCount()) + 1);
		} catch (Exception ignore) {
			// 조회수 집계 실패는 무시
		}
	}

	private static boolean isBot(HttpServletRequest req) {
		String ua = req.getHeader("User-Agent");
		if (ua == null || ua.trim().isEmpty()) return true;
		ua = ua.toLowerCase();
		return ua.contains("bot") || ua.contains("crawl") || ua.contains("spider") || ua.contains("slurp")
				|| ua.contains("facebookexternalhit") || ua.contains("kakaotalk-scrap") || ua.contains("preview");
	}

	/** 처음(또는 24시간이 지난 뒤) 보는 글이면 쿠키에 기록하고 true, 이미 본 글이면 false. */
	private static boolean markViewed(HttpServletRequest req, HttpServletResponse resp, int commentNo) {
		long nowMin = System.currentTimeMillis() / 60000L;
		List<long[]> kept = new ArrayList<>(); // {글번호, 조회시각(분)}
		boolean seen = false;
		Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (Cookie c : cookies) {
				if (!COOKIE.equals(c.getName()) || c.getValue() == null) continue;
				for (String part : c.getValue().split("\\.")) {
					String[] kv = part.split("_");
					if (kv.length != 2) continue;
					try {
						long no = Long.parseLong(kv[0]);
						long at = Long.parseLong(kv[1]);
						if (nowMin - at >= WINDOW_MIN || at > nowMin) continue; // 만료/이상한 값은 버림
						if (no == commentNo) { seen = true; kept.add(new long[] { no, at }); }
						else kept.add(new long[] { no, at });
					} catch (NumberFormatException ignore) {}
				}
			}
		}
		if (seen) return false;
		kept.add(new long[] { commentNo, nowMin });
		while (kept.size() > MAX_KEEP) kept.remove(0);
		StringBuilder sb = new StringBuilder();
		for (long[] k : kept) {
			if (sb.length() > 0) sb.append('.');
			sb.append(k[0]).append('_').append(k[1]);
		}
		Cookie out = new Cookie(COOKIE, sb.toString());
		out.setPath("/");
		out.setMaxAge((int) (WINDOW_MIN * 60)); // 컴파일 대상 servlet-api가 2.5라 HttpOnly 설정 메서드가 없음
		resp.addCookie(out);
		return true;
	}
}
