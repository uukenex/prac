package my.prac.api.board.controller;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.dto.Comments;
import my.prac.core.dto.CommentReply;
import my.prac.core.dto.Shareboard;
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;
import my.prac.core.prjshare.service.ShareService;

/**
 * [2026-09-16 신설] 비밀게시판 -- 자유게시판/공유게시판과 별도의 게시판이지만 내부적으로는
 * 자유게시판과 같은 TCOMMENT 테이블을 COMMENT_CATEGORY='비밀게시판'으로 나눠 쓴다(FreeController와
 * 동일한 CommentService 인프라 재사용, 댓글도 그대로 붙는다). 모든 URL을 "/session/**"
 * 아래에 둬서 SessionInterceptor(MvcConfig 참고)로 비로그인 접근을 자동 리다이렉트시키고,
 * 각 메서드에서도 한 번 더 로그인 여부를 직접 확인한다(인터셉터가 리다이렉트 응답을 보낸
 * 뒤에도 핸들러 실행 자체는 막지 않는 기존 구현 특성 -- SessionInterceptor.java 참고 --
 * 때문에 컨트롤러 쪽 방어가 없으면 세션이 없을 때 NPE가 날 수 있음).
 */
@Controller
public class SecretController {
	static Logger log = LoggerFactory.getLogger(SecretController.class);

	@Resource(name = "core.prjboard.CommentService")
	CommentService commentService;
	@Resource(name = "core.prjshare.ShareService")
	ShareService shareService;

	// thjeon 유저 전용 기능(자유/공유 게시판 글을 비밀게시판으로 이동) 판별.
	private boolean isThjeon(HttpSession session) {
		Users u = (Users) session.getAttribute("Users");
		return u != null && "THJEON".equals(u.getUserId());
	}

	private boolean notLoggedIn(HttpSession session) {
		return session.getAttribute("Users") == null;
	}

	// 비밀게시판 리스트 보기 -- 로그인 안 했으면 목록 자체를 보여주지 않는다.
	@RequestMapping(value = "/session/secret", method = RequestMethod.GET)
	public String secret(Model model, @RequestParam(required = false, defaultValue = "0") int page, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		List<Comments> comment = null;
		int secretPageCount = 0;
		int totalPage = 0;

		if (page == 0) {
			page = 1;
		}
		try {
			comment = commentService.secretListByPage(page);
			secretPageCount = commentService.secretPageCount();
			totalPage = secretPageCount / 10 + 1;
			if (secretPageCount % 10 == 0) {
				totalPage -= 1;
			}
			if (secretPageCount == 0) {
				totalPage = 0;
			}
		} catch (Exception e) {
			log.info("commentService.secretListByPage DB none Connect");
			log.info("commentService.secretPageCount DB none Connect");
		}
		model.addAttribute("comments", comment);
		model.addAttribute("totalPage", totalPage);
		return "session/secretboard/secretboard";
	}

	// 비밀 -단일게시물 보기
	@RequestMapping(value = "/session/secretView", method = RequestMethod.GET)
	public String secretView(Model model, @RequestParam int commentNo, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		List<CommentReply> reply = null;
		Comments comment = null;
		try {
			commentService.count(commentNo);
			comment = commentService.selectComment(commentNo);
			reply = commentService.selectReplyList(commentNo);
			comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("commentService.selectReplyList DB none Connect");
			log.info("commentService.selectComment DB none Connect");
		}

		model.addAttribute("comment", comment);
		model.addAttribute("replys", reply);
		return "session/secretboard/secretboard_view";
	}

	// 비밀게시판 글쓰기 페이지로 넘어감
	@RequestMapping(value = "/session/secretsign", method = RequestMethod.GET)
	public String secretWrtie(Model model, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}
		return "session/secretboard/secretboard_sign";
	}

	// 비밀게시판 글 쓰기 -- SECRET_YN은 writeSecretComment 내부에서 항상 'Y' 고정.
	@RequestMapping(value = "/session/secretWrite", method = RequestMethod.POST)
	public String secretWrite(Model model, HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u.getUserId();
		try {
			commentService.writeSecretComment(commentName, commentContent, userId);
		} catch (Exception e) {
			log.info("commentService.writeSecretComment DB none Connect");
		}

		return "redirect:/session/secretView?commentNo=" + commentService.currentNo();
	}

	// 비밀게시판 수정창으로 넘어가기
	@RequestMapping(value = "/session/secretUpdateForm", method = RequestMethod.POST)
	public String secretUpdateForm(Model model, HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		String commentNo = request.getParameter("commentNo");
		Comments comment = null;
		try {
			comment = commentService.selectComment(Integer.parseInt(commentNo));
			comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("comment", comment);
		return "session/secretboard/secretboard_change";
	}

	// 비밀게시판 수정하기
	@RequestMapping(value = "/session/secretUpdate", method = RequestMethod.POST)
	public String secretUpdate(Model model, HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");

		try {
			commentService.updateComment(Integer.parseInt(commentNo), commentName, commentContent, "Y");
		} catch (Exception e) {
			log.info("commentService.updateComment DB none Connect");
		}
		return "redirect:/session/secretView?commentNo=" + commentNo;
	}

	// 비밀게시판 삭제
	@RequestMapping(value = "/session/secretDelete", method = RequestMethod.POST)
	public String secretDelete(Model model, HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) {
			return "redirect:/loginCheck";
		}

		String commentNo = request.getParameter("commentNo");
		try {
			commentService.deleteComment(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.deleteComment DB none Connect");
		}
		return "redirect:/session/secret?page=1";
	}

	// 검색기능(비밀게시판) ajax
	@RequestMapping(value = "/session/secretSearch", method = RequestMethod.POST)
	public @ResponseBody List<Comments> ajaxSecretSearch(@RequestParam String category, @RequestParam String keyword,
			Model model, HttpSession session) {
		List<Comments> result = new ArrayList<>();
		if (notLoggedIn(session)) {
			return result;
		}
		try {
			if (category.equals("제목")) {
				result = commentService.secretSearchListByPage(keyword, 1);
			} else if (category.equals("내용")) {
				result = commentService.secretSearchContentListByPage(keyword, 1);
			} else if (category.equals("닉네임")) {
				result = commentService.secretSearchNickListByPage(keyword, 1);
			}
		} catch (Exception e) {
			log.info("commentService.secretSearchListByPage DB none Connect");
		}
		return result;
	}

	// [thjeon 전용] 자유게시판 글을 비밀게시판으로 이동 -- COMMENT_CATEGORY만 바꾸므로 댓글도
	// 그대로 따라간다(같은 COMMENT_NO).
	@RequestMapping(value = "/session/moveFreeToSecret", method = RequestMethod.POST)
	public String moveFreeToSecret(HttpServletRequest request, HttpSession session) {
		String commentNo = request.getParameter("commentNo");
		if (!isThjeon(session) || commentNo == null) {
			return "redirect:/free";
		}
		try {
			commentService.moveCommentToSecret(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.moveCommentToSecret DB none Connect");
			return "redirect:/freeView?commentNo=" + commentNo;
		}
		return "redirect:/session/secretView?commentNo=" + commentNo;
	}

	// [thjeon 전용] 공유게시판 글을 비밀게시판으로 이동 -- TSHAREBOARD/TSHAREBOARD_HIST와
	// TCOMMENT는 서로 다른 테이블이라 단순 카테고리 변경이 불가능하므로, 제목/내용을 비밀게시판에
	// 새 글로 복사(insert)한 뒤 원본(이력 포함)을 삭제하는 방식으로 "이동"을 구현한다. 작성자는
	// selectShare()가 내려주는 MODIFY_ID(최종 수정자, 공유게시판은 INSERT_ID를 조회하지 않음)로 기록.
	@RequestMapping(value = "/session/moveShareToSecret", method = RequestMethod.POST)
	public String moveShareToSecret(HttpServletRequest request, HttpSession session) {
		String shareNoStr = request.getParameter("shareNo");
		if (!isThjeon(session) || shareNoStr == null) {
			return "redirect:/share";
		}
		int shareNo;
		try {
			shareNo = Integer.parseInt(shareNoStr);
		} catch (NumberFormatException e) {
			return "redirect:/share";
		}
		try {
			Shareboard share = shareService.selectShare(shareNo);
			if (share == null) {
				return "redirect:/share";
			}
			String authorId = share.getModifyId();
			commentService.writeSecretComment(share.getShareName(), share.getShareContent(), authorId);
			shareService.deleteShareTx(shareNo);
		} catch (Exception e) {
			log.info("moveShareToSecret DB none Connect");
			return "redirect:/shareView?shareNo=" + shareNo;
		}
		return "redirect:/session/secretView?commentNo=" + commentService.currentNo();
	}

}
