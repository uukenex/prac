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
	static Logger logger = LoggerFactory.getLogger(FreeController.class);

	@Resource(name = "core.prjuser.UserService")
	UserService us;
	@Resource(name = "core.prjboard.CommentService")
	CommentService cs;

	// 자유게시판 리스트 보기
	@RequestMapping(value = "/free", method = RequestMethod.GET)
	public String free(Model model, @RequestParam int page, HttpSession session) {
		List<Comments> c = cs.freeListByPage(page);
		model.addAttribute("comments", c);

		int freePageCount = cs.freePageCount();
		int totalPage = freePageCount / 10 + 1;
		if (freePageCount % 10 == 0) {
			totalPage -= 1;
		}
		if (freePageCount == 0) {
			totalPage = 0;
		}
		logger.trace("결과값 : {}", c);
		model.addAttribute("totalPage", totalPage);
		return "nonsession/freeboard/freeboard";
	}

	// 자유 -단일게시물 보기
	@RequestMapping(value = "/freeView", method = RequestMethod.GET)
	public String noticeView(Model model, @RequestParam int commentNo) {
		cs.count(commentNo);
		List<CommentReply> r = cs.selectReplyList(commentNo);
		Comments c = cs.selectComment(commentNo);
		c.setCommentContent1(c.getCommentContent1().replace("{imgUrl}", "http://13.209.8.226"));
		model.addAttribute("comment", c);
		model.addAttribute("replys", r);
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
		cs.writeFreeComment(commentName, commentContent, userId);

		return "redirect:/freeView?commentNo=" + cs.currentNo();
	}

	// 공지사항 수정창으로 넘어가기
	@RequestMapping(value = "/session/freeUpdate", method = RequestMethod.POST)
	public String noticeUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		Comments c = cs.selectComment(Integer.parseInt(commentNo));
		model.addAttribute("comment", c);
		return "session/freeboard/freeboard_change";
	}

	// 공지 수정하기
	@RequestMapping(value = "/freeUpdate", method = RequestMethod.POST)
	public String commentUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		cs.updateComment(Integer.parseInt(commentNo), commentName, commentContent);
		return "redirect:/freeView?commentNo=" + commentNo;
	}

	// 공지 삭제
	@RequestMapping(value = "/freeDelete", method = RequestMethod.POST)
	public String commentDelete(Model model, HttpServletRequest request, HttpSession session) {
		String commentNo = request.getParameter("commentNo");
		int result = cs.deleteComment(Integer.parseInt(commentNo));
		if (result == 1) {
			session.setAttribute("message", "정상 삭제 완료");
		}
		return "redirect:/free?page=1";
	}

	// 댓글달기 ajax
	@RequestMapping(value = "/session/replyRegist", method = RequestMethod.POST)
	public @ResponseBody List<CommentReply> ajaxreply(@RequestParam String userId, @RequestParam String replyContent,
			@RequestParam int commentNo, HttpSession session) {
		int result = cs.insertReply(replyContent, commentNo, userId);
		List<CommentReply> list = cs.selectReplyList(commentNo);

		return list;
	}

	// 댓글 삭제
	@RequestMapping(value = "/replyDelete", method = RequestMethod.POST)
	public String replyDelete(Model model, HttpServletRequest request, HttpSession session) {
		String replyNo = request.getParameter("replyNo");
		String commentNo = request.getParameter("commentNo");
		logger.trace("{}", replyNo);
		int result = cs.deleteReply(Integer.parseInt(replyNo));
		if (result == 1) {
			session.setAttribute("message", "정상 삭제 완료");
		}
		return "redirect:/freeView?commentNo=" + commentNo;
	}

	// 검색기능(자유게시판) ajax 카테고리와 검색키워드를 받아옴
	@RequestMapping(value = "/search", method = RequestMethod.POST)
	public @ResponseBody List<Comments> ajaxsearch(@RequestParam String category, @RequestParam String keyword,
			Model model) {
		List<Comments> result = new ArrayList<>();
		logger.trace("여기오다");
		if (category.equals("제목")) {
			result = cs.freeSearchListByPage(keyword, 1);
			model.addAttribute("comments", result);
		} else if (category.equals("내용")) {
			result = cs.freeSearchContentListByPage(keyword, 1);
		} else if (category.equals("닉네임")) {
			result = cs.freeSearchNickListByPage(keyword, 1);
		}
		return result;
	}

}
