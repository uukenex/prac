package my.prac.api.board.controller;

import java.util.ArrayList;
import java.util.HashMap;
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

import my.prac.core.dto.CommentReply;
import my.prac.core.dto.Comments;
import my.prac.core.dto.Shareboard;
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;
import my.prac.core.prjshare.service.ShareService;

/**
 * [2026-09-16 신설] "게시판 디자인 개편" 요청 -- 기존 자유/공유/비밀게시판(FreeController/
 * ShareController/SecretController, freeboard/share/secretboard JSP)은 손대지 않고,
 * 완전히 새 뷰(webapp/WEB-INF/view/{nonsession,session}/newboard/*.jsp)
 * + 새 스타일시트(assets/css/newboard.css)로 같은 데이터를 보여주는 병행 게시판을 추가한다.
 * 서비스 레이어(CommentService/ShareService)는 기존 컨트롤러들과 완전히 동일하게 재사용하므로
 * 데이터/DB 변경은 전혀 없다 -- 순수 뷰 레이어만 새로 만든 것.
 *
 * "톤만 바꾸고 기능/위치는 유지" 요청에 따라 상단 드롭다운 메뉴·(기존 좌측 사이드의) 숨김
 * 게임 링크·Fancybox·네이버 스마트에디터는 모두 그대로 살렸고(_chrome_top.jsp 참고), PC 전용
 * 2단 레이아웃만 제거해 폭 무관 단일 레이아웃으로 통일했다.
 *
 * [2026-09-16 재정리] "원래 게시판과 동일한 규격(session 경로 등)을 그대로 이용하도록
 * 해달라" 요청 -- URL 스킴을 FreeController/ShareController/SecretController와 동일한
 * 패턴으로 다시 맞췄다: 로그인 필요한 액션(글쓰기폼/수정폼/글쓰기/수정/삭제/검색)은 전부
 * SessionInterceptor(MvcConfig, "/session/**")가 그대로 먹도록 "/session/newboard/..."
 * 로 두고, 그 외(목록/조회/글쓰기 제출/수정 제출)는 기존과 동일하게 "/newboard/..." 그대로
 * 둔다("newboard" 세그먼트만 얹었을 뿐 나머지 이름/메서드/session 유무는 원본 그대로 미러링).
 * 자유게시판은 원본이 아예 세션 프리픽스가 없는 액션(글쓰기 제출/수정 제출/삭제/검색)이
 * 많은데, 그 원본 그대로의 비보호 패턴도 동일하게 유지한다(원본을 "고친" 게 아니라 "복제"
 * 하는 것이 이번 요청의 취지).
 */
@Controller
public class NewBoardController {
	static Logger log = LoggerFactory.getLogger(NewBoardController.class);

	@Resource(name = "core.prjboard.CommentService")
	CommentService commentService;
	@Resource(name = "core.prjshare.ShareService")
	ShareService shareService;

	private boolean isThjeon(HttpSession session) {
		Users u = (Users) session.getAttribute("Users");
		return u != null && "THJEON".equals(u.getUserId());
	}

	private boolean notLoggedIn(HttpSession session) {
		return session.getAttribute("Users") == null;
	}

	// ================================================================
	// 자유게시판 (New) -- FreeController와 동일 규격:
	// /free,/freeView,/boardWrite,/freeUpdate,/freeDelete,/search 는 세션 프리픽스 없음(원본
	// 그대로), 글쓰기폼/수정폼만 /session/boardsign,/session/freeUpdate.
	// ================================================================

	@RequestMapping(value = "/newboard/free", method = RequestMethod.GET)
	public String free(Model model, @RequestParam(required = false, defaultValue = "0") int page, HttpSession session) {
		List<Comments> comment = null;
		int freePageCount = 0;
		int totalPage = 0;
		if (page == 0) page = 1;
		try {
			comment = commentService.freeListByPage(page);
			freePageCount = commentService.freePageCount();
			totalPage = freePageCount / 10 + 1;
			if (freePageCount % 10 == 0) totalPage -= 1;
			if (freePageCount == 0) totalPage = 0;
		} catch (Exception e) {
			log.info("commentService.freeListByPage DB none Connect");
		}
		model.addAttribute("room", "free");
		model.addAttribute("comments", comment);
		model.addAttribute("totalPage", totalPage);
		return "nonsession/newboard/free";
	}

	@RequestMapping(value = "/newboard/freeView", method = RequestMethod.GET)
	public String freeView(Model model, @RequestParam int commentNo, HttpSession session) {
		List<CommentReply> reply = null;
		Comments comment = null;
		boolean secretLocked = false;
		try {
			commentService.count(commentNo);
			comment = commentService.selectComment(commentNo);
			boolean isSecret = "Y".equals(comment.getSecretYn());
			boolean loggedIn = session.getAttribute("Users") != null;
			if (isSecret && !loggedIn) {
				secretLocked = true;
				comment.setCommentContent("");
			} else {
				reply = commentService.selectReplyList(commentNo);
				comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
			}
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("room", "free");
		model.addAttribute("comment", comment);
		model.addAttribute("replys", reply);
		model.addAttribute("secretLocked", secretLocked);
		return "nonsession/newboard/free_view";
	}

	@RequestMapping(value = "/session/newboard/boardsign", method = RequestMethod.GET)
	public String freeSign(Model model) {
		model.addAttribute("room", "free");
		return "session/newboard/free_sign";
	}

	@RequestMapping(value = "/newboard/boardWrite", method = RequestMethod.POST)
	public String freeWrite(HttpServletRequest request, HttpSession session) {
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		String secretYn = "Y".equals(request.getParameter("secretYn")) ? "Y" : "N";
		Users u = (Users) session.getAttribute("Users");
		String userId = u == null ? "999999" : u.getUserId();
		try {
			commentService.writeFreeComment(commentName, commentContent, userId, secretYn);
		} catch (Exception e) {
			log.info("commentService.writeFreeComment DB none Connect");
		}
		return "redirect:/newboard/freeView?commentNo=" + commentService.currentNo();
	}

	@RequestMapping(value = "/session/newboard/freeUpdate", method = RequestMethod.POST)
	public String freeUpdateForm(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		Comments comment = null;
		try {
			comment = commentService.selectComment(Integer.parseInt(commentNo));
			comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("room", "free");
		model.addAttribute("comment", comment);
		return "session/newboard/free_change";
	}

	@RequestMapping(value = "/newboard/freeUpdate", method = RequestMethod.POST)
	public String freeUpdate(HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		String secretYn = "Y".equals(request.getParameter("secretYn")) ? "Y" : "N";
		try {
			commentService.updateComment(Integer.parseInt(commentNo), commentName, commentContent, secretYn);
		} catch (Exception e) {
			log.info("commentService.updateComment DB none Connect");
		}
		return "redirect:/newboard/freeView?commentNo=" + commentNo;
	}

	@RequestMapping(value = "/newboard/freeDelete", method = RequestMethod.POST)
	public String freeDelete(HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		try {
			commentService.deleteComment(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.deleteComment DB none Connect");
		}
		return "redirect:/newboard/free?page=1";
	}

	@RequestMapping(value = "/newboard/search", method = RequestMethod.POST)
	public @ResponseBody List<Comments> freeSearch(@RequestParam String category, @RequestParam String keyword) {
		List<Comments> result = new ArrayList<>();
		try {
			if (category.equals("제목")) result = commentService.freeSearchListByPage(keyword, 1);
			else if (category.equals("내용")) result = commentService.freeSearchContentListByPage(keyword, 1);
			else if (category.equals("닉네임")) result = commentService.freeSearchNickListByPage(keyword, 1);
		} catch (Exception e) {
			log.info("commentService.freeSearchListByPage DB none Connect");
		}
		return result;
	}

	// ================================================================
	// 공유게시판 (New) -- ShareController와 동일 규격: 전부 세션 프리픽스 없음(원본에 세션
	// 보호가 걸려있지 않았던 그대로), 글쓰기폼만 /session/sharesign.
	// ================================================================

	@RequestMapping(value = "/newboard/share", method = RequestMethod.GET)
	public String share(Model model, @RequestParam(required = false, defaultValue = "0") int page) {
		List<Shareboard> shares = null;
		int sharePageCount = 0;
		int totalPage = 0;
		if (page == 0) page = 1;
		try {
			shares = shareService.selectShareListByPage(page);
			sharePageCount = shareService.selectSharePageCount();
			totalPage = sharePageCount / 10 + 1;
			if (sharePageCount % 10 == 0) totalPage -= 1;
			if (sharePageCount == 0) totalPage = 0;
		} catch (Exception e) {
			log.info("shareService.selectShareListByPage DB none Connect");
		}
		model.addAttribute("room", "share");
		model.addAttribute("shares", shares);
		model.addAttribute("totalPage", totalPage);
		return "nonsession/newboard/share";
	}

	@RequestMapping(value = "/newboard/shareView", method = RequestMethod.GET)
	public String shareView(Model model, @RequestParam int shareNo,
			@RequestParam(required = false, defaultValue = "0") int version) {
		Shareboard share = null;
		List<Integer> shareHist = null;
		try {
			if (version == 0) {
				share = shareService.selectShare(shareNo);
			} else {
				HashMap<String, Object> shareMap = new HashMap<>();
				shareMap.put("shareNo", shareNo);
				shareMap.put("version", version);
				share = shareService.selectShareHist(shareMap);
			}
			shareHist = shareService.selectShareHistList(shareNo);
			share.setShareContent(share.getShareContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("shareService.selectShare DB none Connect");
		}
		model.addAttribute("room", "share");
		model.addAttribute("share", share);
		model.addAttribute("shareHist", shareHist);
		return "nonsession/newboard/share_view";
	}

	@RequestMapping(value = "/session/newboard/sharesign", method = RequestMethod.GET)
	public String shareSign(Model model) {
		model.addAttribute("room", "share");
		return "session/newboard/share_sign";
	}

	@RequestMapping(value = "/newboard/shareWrite", method = RequestMethod.POST)
	public String shareWrite(HttpServletRequest request, HttpSession session) {
		String shareName = request.getParameter("title");
		String shareContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u == null ? "999999" : u.getUserId();

		Shareboard share = new Shareboard();
		share.setShareName(shareName);
		share.setShareContent(shareContent.replaceAll("？", ""));
		share.setInsertId(userId);
		share.setModifyId(userId);

		int newShareNo = 0;
		try {
			newShareNo = shareService.insertShareTx(share);
		} catch (Exception e) {
			log.info("shareService.insertShareTx DB none Connect");
		}
		return "redirect:/newboard/shareView?shareNo=" + newShareNo;
	}

	@RequestMapping(value = "/newboard/s/shareUpdate", method = RequestMethod.POST)
	public String shareUpdateForm(Model model, HttpServletRequest request) {
		String shareNo = request.getParameter("shareNo");
		Shareboard share = null;
		try {
			share = shareService.selectShare(Integer.parseInt(shareNo));
			share.setShareContent(share.getShareContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("shareService.selectShare DB none Connect");
		}
		model.addAttribute("room", "share");
		model.addAttribute("share", share);
		return "session/newboard/share_change";
	}

	@RequestMapping(value = "/newboard/shareUpdate", method = RequestMethod.POST)
	public String shareUpdate(HttpServletRequest request, HttpSession session) {
		String shareNo = request.getParameter("shareNo");
		String shareName = request.getParameter("title");
		String shareContent = request.getParameter("content");
		String ipAddr = request.getRemoteAddr();
		String userId;
		Users u = (Users) session.getAttribute("Users");
		userId = u == null ? "999999" : u.getUserId();

		Shareboard share = new Shareboard();
		share.setShareNo(Integer.parseInt(shareNo));
		share.setShareName(shareName);
		share.setShareContent(shareContent.replaceAll("？", ""));
		share.setInsertId(userId);
		share.setModifyId(userId);
		share.setIpAddr(ipAddr);

		try {
			shareService.updateShareTx(share);
		} catch (Exception e) {
			log.info("shareService.updateShareTx DB none Connect");
		}
		return "redirect:/newboard/shareView?shareNo=" + shareNo;
	}

	// ================================================================
	// 비밀게시판 (New) -- SecretController와 동일 규격: 목록/조회/글쓰기/수정/삭제/검색/이동
	// 전부 "/session/newboard/..."(SessionInterceptor가 자동으로 비로그인 리다이렉트,
	// 컨트롤러에서도 한 번 더 확인하는 이중 방어까지 원본과 동일).
	// ================================================================

	@RequestMapping(value = "/session/newboard/secret", method = RequestMethod.GET)
	public String secret(Model model, @RequestParam(required = false, defaultValue = "0") int page, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";

		List<Comments> comment = null;
		int secretPageCount = 0;
		int totalPage = 0;
		if (page == 0) page = 1;
		try {
			comment = commentService.secretListByPage(page);
			secretPageCount = commentService.secretPageCount();
			totalPage = secretPageCount / 10 + 1;
			if (secretPageCount % 10 == 0) totalPage -= 1;
			if (secretPageCount == 0) totalPage = 0;
		} catch (Exception e) {
			log.info("commentService.secretListByPage DB none Connect");
		}
		model.addAttribute("room", "secret");
		model.addAttribute("comments", comment);
		model.addAttribute("totalPage", totalPage);
		return "session/newboard/secret";
	}

	@RequestMapping(value = "/session/newboard/secretView", method = RequestMethod.GET)
	public String secretView(Model model, @RequestParam int commentNo, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";

		List<CommentReply> reply = null;
		Comments comment = null;
		try {
			commentService.count(commentNo);
			comment = commentService.selectComment(commentNo);
			reply = commentService.selectReplyList(commentNo);
			comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("room", "secret");
		model.addAttribute("comment", comment);
		model.addAttribute("replys", reply);
		return "session/newboard/secret_view";
	}

	@RequestMapping(value = "/session/newboard/secretsign", method = RequestMethod.GET)
	public String secretSign(Model model, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		model.addAttribute("room", "secret");
		return "session/newboard/secret_sign";
	}

	@RequestMapping(value = "/session/newboard/secretWrite", method = RequestMethod.POST)
	public String secretWrite(HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u.getUserId();
		try {
			commentService.writeSecretComment(commentName, commentContent, userId);
		} catch (Exception e) {
			log.info("commentService.writeSecretComment DB none Connect");
		}
		return "redirect:/session/newboard/secretView?commentNo=" + commentService.currentNo();
	}

	@RequestMapping(value = "/session/newboard/secretUpdateForm", method = RequestMethod.POST)
	public String secretUpdateForm(Model model, HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		String commentNo = request.getParameter("commentNo");
		Comments comment = null;
		try {
			comment = commentService.selectComment(Integer.parseInt(commentNo));
			comment.setCommentContent(comment.getCommentContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("room", "secret");
		model.addAttribute("comment", comment);
		return "session/newboard/secret_change";
	}

	@RequestMapping(value = "/session/newboard/secretUpdate", method = RequestMethod.POST)
	public String secretUpdate(HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		try {
			commentService.updateComment(Integer.parseInt(commentNo), commentName, commentContent, "Y");
		} catch (Exception e) {
			log.info("commentService.updateComment DB none Connect");
		}
		return "redirect:/session/newboard/secretView?commentNo=" + commentNo;
	}

	@RequestMapping(value = "/session/newboard/secretDelete", method = RequestMethod.POST)
	public String secretDelete(HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		String commentNo = request.getParameter("commentNo");
		try {
			commentService.deleteComment(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.deleteComment DB none Connect");
		}
		return "redirect:/session/newboard/secret?page=1";
	}

	@RequestMapping(value = "/session/newboard/secretSearch", method = RequestMethod.POST)
	public @ResponseBody List<Comments> secretSearch(@RequestParam String category, @RequestParam String keyword,
			HttpSession session) {
		List<Comments> result = new ArrayList<>();
		if (notLoggedIn(session)) return result;
		try {
			if (category.equals("제목")) result = commentService.secretSearchListByPage(keyword, 1);
			else if (category.equals("내용")) result = commentService.secretSearchContentListByPage(keyword, 1);
			else if (category.equals("닉네임")) result = commentService.secretSearchNickListByPage(keyword, 1);
		} catch (Exception e) {
			log.info("commentService.secretSearchListByPage DB none Connect");
		}
		return result;
	}

	// ================================================================
	// [thjeon 전용] 자유/공유 -> 비밀 이동 (기존 SecretController와 동일 로직/경로 규격)
	// ================================================================

	@RequestMapping(value = "/session/newboard/moveFreeToSecret", method = RequestMethod.POST)
	public String moveFreeToSecret(HttpServletRequest request, HttpSession session) {
		String commentNo = request.getParameter("commentNo");
		if (!isThjeon(session) || commentNo == null) {
			return "redirect:/newboard/free";
		}
		try {
			commentService.moveCommentToSecret(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.moveCommentToSecret DB none Connect");
			return "redirect:/newboard/freeView?commentNo=" + commentNo;
		}
		return "redirect:/session/newboard/secretView?commentNo=" + commentNo;
	}

	@RequestMapping(value = "/session/newboard/moveShareToSecret", method = RequestMethod.POST)
	public String moveShareToSecret(HttpServletRequest request, HttpSession session) {
		String shareNoStr = request.getParameter("shareNo");
		if (!isThjeon(session) || shareNoStr == null) {
			return "redirect:/newboard/share";
		}
		int shareNo;
		try {
			shareNo = Integer.parseInt(shareNoStr);
		} catch (NumberFormatException e) {
			return "redirect:/newboard/share";
		}
		try {
			Shareboard share = shareService.selectShare(shareNo);
			if (share == null) return "redirect:/newboard/share";
			String authorId = share.getModifyId();
			commentService.writeSecretComment(share.getShareName(), share.getShareContent(), authorId);
			shareService.deleteShareTx(shareNo);
		} catch (Exception e) {
			log.info("moveShareToSecret DB none Connect");
			return "redirect:/newboard/shareView?shareNo=" + shareNo;
		}
		return "redirect:/session/newboard/secretView?commentNo=" + commentService.currentNo();
	}

}
