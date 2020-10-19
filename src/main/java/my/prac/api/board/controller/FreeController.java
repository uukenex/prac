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

import my.prac.core.dto.CommentReply;
import my.prac.core.dto.Comments;
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;
import my.prac.core.prjuser.service.UserService;

/**
 * 자유게시판 ,댓글 ajax
 *
 */
@Controller
public class FreeController {
	static Logger log = LoggerFactory.getLogger(FreeController.class);

	@Resource(name = "core.prjuser.UserService")
	UserService userService;
	@Resource(name = "core.prjboard.CommentService")
	CommentService commentService;

	// 자유게시판 리스트 보기
	@RequestMapping(value = "/free", method = RequestMethod.GET)
	public String free(Model model, @RequestParam int page, HttpSession session) {
		List<Comments> comment = null;
		int freePageCount = 0;
		int totalPage = 0;
		try {
			comment = commentService.freeListByPage(page);
			freePageCount = commentService.freePageCount();
			totalPage = freePageCount / 10 + 1;
			if (freePageCount % 10 == 0) {
				totalPage -= 1;
			}
			if (freePageCount == 0) {
				totalPage = 0;
			}
		} catch (Exception e) {
			log.info("commentService.freeListByPage DB none Connect");
			log.info("commentService.freePageCount DB none Connect");
		}
		model.addAttribute("comments", comment);
		model.addAttribute("totalPage", totalPage);
		return "nonsession/freeboard/freeboard";
	}

	// 자유 -단일게시물 보기
	@RequestMapping(value = "/freeView", method = RequestMethod.GET)
	public String noticeView(Model model, @RequestParam int commentNo) {
		List<CommentReply> reply = null;
		Comments comment = null;
		try {
			commentService.count(commentNo);
			reply = commentService.selectReplyList(commentNo);
			comment = commentService.selectComment(commentNo);
			comment.setCommentContent(comment.getCommentContent().replace("{imgUrl}", "http://dev-apc.com"));
		} catch (Exception e) {
			log.info("commentService.selectReplyList DB none Connect");
			log.info("commentService.selectComment DB none Connect");
		}

		model.addAttribute("comment", comment);
		model.addAttribute("replys", reply);
		return "nonsession/freeboard/freeboard_view";
	}

	// 공지 쓰기 페이지로 넘어감
	@RequestMapping(value = "/session/boardsign", method = RequestMethod.GET)
	public String noticeWrtie(Model model) {
		return "session/freeboard/freeboard_sign";
	}

	// 자유게시판 글 쓰기
	@RequestMapping(value = "/boardWrite", method = RequestMethod.POST)
	public String commentWrite(Model model, HttpServletRequest request, HttpSession session) {

		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u.getUserId();
		try {
			commentService.writeFreeComment(commentName, commentContent, userId);
		} catch (Exception e) {
			log.info("commentService.writeFreeComment DB none Connect");
		}

		return "redirect:/freeView?commentNo=" + commentService.currentNo();
	}

	// 공지사항 수정창으로 넘어가기
	@RequestMapping(value = "/session/freeUpdate", method = RequestMethod.POST)
	public String noticeUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		Comments comment = null;
		try {
			comment = commentService.selectComment(Integer.parseInt(commentNo));
		} catch (Exception e) {
			log.info("commentService.selectComment DB none Connect");
		}
		model.addAttribute("comment", comment);
		return "session/freeboard/freeboard_change";
	}

	// 공지 수정하기
	@RequestMapping(value = "/freeUpdate", method = RequestMethod.POST)
	public String commentUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		try {
			commentService.updateComment(Integer.parseInt(commentNo), commentName, commentContent);
		} catch (Exception e) {
			log.info("commentService.updateComment DB none Connect");
		}
		return "redirect:/freeView?commentNo=" + commentNo;
	}

	// 공지 삭제
	@RequestMapping(value = "/freeDelete", method = RequestMethod.POST)
	public String commentDelete(Model model, HttpServletRequest request, HttpSession session) {
		String commentNo = request.getParameter("commentNo");
		int result = 0;
		try {
			result = commentService.deleteComment(Integer.parseInt(commentNo));
			if (result == 1) {
				session.setAttribute("message", "정상 삭제 완료");
			}
		} catch (Exception e) {
			log.info("commentService.deleteComment DB none Connect");
		}
		return "redirect:/free?page=1";
	}

	// 댓글달기 ajax
	@RequestMapping(value = "/session/replyRegist", method = RequestMethod.POST)
	public @ResponseBody String ajaxreply(@RequestParam String userId, @RequestParam String replyContent,
			@RequestParam int commentNo, HttpSession session) {
		try {
			commentService.insertReply(replyContent, commentNo, userId);
		} catch (Exception e) {
			log.info("commentService.insertReply DB none Connect");
		}

		return "OK";
	}

	// 댓글 삭제
	/*
	 * @RequestMapping(value = "/replyDelete", method = RequestMethod.POST)
	 * public String replyDelete(Model model, HttpServletRequest request,
	 * HttpSession session) { String replyNo = request.getParameter("replyNo");
	 * String commentNo = request.getParameter("commentNo"); int result =
	 * commentService.deleteReply(Integer.parseInt(replyNo)); if (result == 1) {
	 * session.setAttribute("message", "정상 삭제 완료"); } return
	 * "redirect:/freeView?commentNo=" + commentNo; }
	 */
	// 검색기능(자유게시판) ajax 카테고리와 검색키워드를 받아옴
	@RequestMapping(value = "/search", method = RequestMethod.POST)
	public @ResponseBody List<Comments> ajaxsearch(@RequestParam String category, @RequestParam String keyword,
			Model model) {
		List<Comments> result = new ArrayList<>();
		try {
			if (category.equals("제목")) {
				result = commentService.freeSearchListByPage(keyword, 1);
				model.addAttribute("comments", result);
			} else if (category.equals("내용")) {
				result = commentService.freeSearchContentListByPage(keyword, 1);
			} else if (category.equals("닉네임")) {
				result = commentService.freeSearchNickListByPage(keyword, 1);
			}
		} catch (Exception e) {
			log.info("commentService.freeSearchListByPage DB none Connect");
		}
		return result;
	}

}
