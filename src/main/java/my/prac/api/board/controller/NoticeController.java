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
import my.prac.core.dto.Users;
import my.prac.core.prjboard.service.CommentService;
import my.prac.core.prjuser.service.UserService;

/**
 * 공지사항 ID찾기 비번찾기 회원가입
 *
 */
@Controller
public class NoticeController {
	static Logger logger = LoggerFactory.getLogger(NoticeController.class);

	@Resource(name = "core.prjuser.UserService")
	UserService us;
	@Resource(name = "core.prjboard.CommentService")
	CommentService cs;

	/**
	 * 닉네임 불러오기:: user Service 에서 불러 getUserId()를 해서 사용할 것
	 */
	// 단일게시물 보기
	@RequestMapping(value = "/noticeView", method = RequestMethod.GET)
	public String noticeView(Model model, @RequestParam int commentNo, HttpServletRequest request) {
		cs.count(commentNo);
		Comments c = cs.selectComment(commentNo);
		model.addAttribute("comment", c);

		return "nonsession/mainnotice/notice_view";
	}

	/**
	 * 리스트에서의 닉네임불러오기:: for each에서 comments.users.userNick 하면됩니다.
	 */
	// 공지사항 리스트 보기
	@RequestMapping(value = "/notice", method = RequestMethod.GET)
	public String notice(Model model, @RequestParam int page, HttpSession session) {
		List<Comments> c = cs.noticeListByPage(page);
		model.addAttribute("comments", c);

		int noticePageCount = cs.noticePageCount();
		int totalPage = noticePageCount / 10 + 1;
		if (noticePageCount % 10 == 0) {
			totalPage -= 1;
		}
		if (noticePageCount == 0) {
			totalPage = 0;
		}
		model.addAttribute("totalPage", totalPage);
		return "nonsession/mainnotice/notice";
	}

	// 공지사항 수정창으로 넘어가기
	@RequestMapping(value = "/session/noticeUpdate", method = RequestMethod.POST)
	public String noticeUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		Comments c = cs.selectComment(Integer.parseInt(commentNo));

		model.addAttribute("comment", c);
		model.addAttribute("userNick", us.searchNickById(c.getUserId()));
		return "session/mainnotice/notice_change";
	}

	// 공지 삭제
	@RequestMapping(value = "/commentDelete", method = RequestMethod.POST)
	public String commentDelete(Model model, HttpServletRequest request, HttpSession session) {
		String commentNo = request.getParameter("commentNo");
		int result = cs.deleteComment(Integer.parseInt(commentNo));
		if (result == 1) {
			session.setAttribute("message", "정상 삭제 완료");
		}
		return "redirect:/notice?page=1";
	}

	// 공지 수정하기
	@RequestMapping(value = "/commentUpdate", method = RequestMethod.POST)
	public String commentUpdate(Model model, HttpServletRequest request) {
		String commentNo = request.getParameter("commentNo");
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		cs.updateComment(Integer.parseInt(commentNo), commentName, commentContent);
		return "redirect:/noticeView?commentNo=" + commentNo;
	}

	// 공지 쓰기 페이지로 넘어감
	@RequestMapping(value = "/session/noticeWrite", method = RequestMethod.GET)
	public String noticeWrtie(Model model, HttpSession session) {
		logger.trace("notice write");
		if (session.getAttribute("Users") == null) {
			session.setAttribute("forPage", "session/mainnotice/notice_sign");
		}
		return "session/mainnotice/notice_sign";
	}

	// 공지 쓰기
	@RequestMapping(value = "/commentWrite", method = RequestMethod.POST)
	public String commentWrite(Model model, HttpServletRequest request, HttpSession session) {
		String commentName = request.getParameter("title");
		String commentContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u.getUserId();
		cs.writeNoticeComment(commentName, commentContent, userId);
		return "redirect:/noticeView?commentNo=" + cs.currentNo();
	}

	// 검색기능(자유게시판) ajax 카테고리와 검색키워드를 받아옴
	@RequestMapping(value = "/search2", method = RequestMethod.POST)
	public @ResponseBody List<Comments> ajaxsearch(@RequestParam String category, @RequestParam String keyword,
			Model model) {
		List<Comments> result = new ArrayList<>();
		logger.trace("여기오다");
		if (category.equals("제목")) {
			result = cs.noticeSearchListByPage(keyword, 1);
			model.addAttribute("comments", result);
		} else if (category.equals("내용")) {
			result = cs.noticeSearchContentListByPage(keyword, 1);
		} else if (category.equals("닉네임")) {
			result = cs.noticeSearchNickListByPage(keyword, 1);
		}
		return result;
	}

}
