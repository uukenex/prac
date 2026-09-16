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
 * 완전히 새 경로("/newboard/**")와 새 뷰(webapp/WEB-INF/view/{nonsession,session}/newboard/*.jsp)
 * + 새 스타일시트(assets/css/newboard.css)로 같은 데이터를 보여주는 병행 게시판을 추가한다.
 * 서비스 레이어(CommentService/ShareService)는 기존 컨트롤러들과 완전히 동일하게 재사용하므로
 * 데이터/DB 변경은 전혀 없다 -- 순수 뷰 레이어만 새로 만든 것.
 *
 * "톤만 바꾸고 기능/위치는 유지" 요청에 따라 상단 드롭다운 메뉴·(기존 좌측 사이드의) 숨김
 * 게임 링크·Fancybox·네이버 스마트에디터는 모두 그대로 살렸고(_chrome_top.jsp 참고), PC 전용
 * 2단 레이아웃만 제거해 폭 무관 단일 레이아웃으로 통일했다.
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
	// 자유게시판 (New)
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

	@RequestMapping(value = "/newboard/freeSign", method = RequestMethod.GET)
	public String freeSign(Model model) {
		model.addAttribute("room", "free");
		return "session/newboard/free_sign";
	}

	@RequestMapping(value = "/newboard/freeWrite", method = RequestMethod.POST)
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

	@RequestMapping(value = "/newboard/freeUpdateForm", method = RequestMethod.POST)
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

	@RequestMapping(value = "/newboard/freeSearch", method = RequestMethod.POST)
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
	// 공유게시판 (New)
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

	@RequestMapping(value = "/newboard/shareSign", method = RequestMethod.GET)
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

	@RequestMapping(value = "/newboard/shareUpdateForm", method = RequestMethod.POST)
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
	// 비밀게시판 (New) -- 로그인 필수(비로그인 시 /loginCheck)
	// ================================================================

	@RequestMapping(value = "/newboard/secret", method = RequestMethod.GET)
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

	@RequestMapping(value = "/newboard/secretView", method = RequestMethod.GET)
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

	@RequestMapping(value = "/newboard/secretSign", method = RequestMethod.GET)
	public String secretSign(Model model, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		model.addAttribute("room", "secret");
		return "session/newboard/secret_sign";
	}

	@RequestMapping(value = "/newboard/secretWrite", method = RequestMethod.POST)
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
		return "redirect:/newboard/secretView?commentNo=" + commentService.currentNo();
	}

	@RequestMapping(value = "/newboard/secretUpdateForm", method = RequestMethod.POST)
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

	@RequestMapping(value = "/newboard/secretUpdate", method = RequestMethod.POST)
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
		return "redirect:/newboard/secretView?commentNo=" + commentNo;
	}

	@RequestMapping(value = "/newboard/secretDelete", method = RequestMethod.POST)
	public String secretDelete(HttpServletRequest request, HttpSession session) {
		if (notLoggedIn(session)) return "redirect:/loginCheck";
		String commentNo = request.getParameter("commentNo");
		try {
			commentService.deleteComment(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.deleteComment DB none Connect");
		}
		return "redirect:/newboard/secret?page=1";
	}

	@RequestMapping(value = "/newboard/secretSearch", method = RequestMethod.POST)
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
	// [thjeon 전용] 자유/공유 -> 비밀 이동 (기존 SecretController와 동일 로직,
	// 뉴게시판 URL로 리다이렉트만 다름)
	// ================================================================

	@RequestMapping(value = "/newboard/moveFreeToSecret", method = RequestMethod.POST)
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
		return "redirect:/newboard/secretView?commentNo=" + commentNo;
	}

	@RequestMapping(value = "/newboard/moveShareToSecret", method = RequestMethod.POST)
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
		return "redirect:/newboard/secretView?commentNo=" + commentService.currentNo();
	}

}
