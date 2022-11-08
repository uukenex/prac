package my.prac.api.board.controller;

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

import my.prac.core.dto.Shareboard;
import my.prac.core.dto.Users;
import my.prac.core.prjshare.service.ShareService;

/**
 * 공유게시판
 *
 */
@Controller
public class ShareController {
	static Logger log = LoggerFactory.getLogger(ShareController.class);

	@Resource(name = "core.prjshare.ShareService")
	ShareService shareService;

	@RequestMapping(value = "/share", method = RequestMethod.GET)
	public String share(Model model, @RequestParam(required = false, defaultValue = "0") int page,
			HttpSession session) {
		List<Shareboard> shares = null;
		int sharePageCount = 0;
		int totalPage = 0;

		if (page == 0) {
			page = 1;
		}
		try {
			shares = shareService.selectShareListByPage(page);
			sharePageCount = shareService.selectSharePageCount();
			totalPage = sharePageCount / 10 + 1;
			if (sharePageCount % 10 == 0) {
				totalPage -= 1;
			}
			if (sharePageCount == 0) {
				totalPage = 0;
			}
		} catch (Exception e) {
			log.info("shareService.shareListByPage DB none Connect");
			log.info("shareService.sharePageCount DB none Connect");
		}
		model.addAttribute("shares", shares);
		model.addAttribute("totalPage", totalPage);
		return "nonsession/share/shareboard";
	}

	@RequestMapping(value = "/shareView", method = RequestMethod.GET)
	public String shareView(Model model, @RequestParam int shareNo,
			@RequestParam(required = false, defaultValue = "0") int version) {
		Shareboard share = null;
		List<Integer> shareHist = null;
		try {
			if (version == 0) {
				share = shareService.selectShare(shareNo);
				shareHist = shareService.selectShareHistList(shareNo);
			} else {
				HashMap<String, Object> shareMap = new HashMap<>();
				shareMap.put("shareNo", shareNo);
				shareMap.put("version", version);
				share = shareService.selectShareHist(shareMap);
				shareHist = shareService.selectShareHistList(shareNo);
			}
			// share.setShareContent(share.getShareContent().replaceAll("{imgUrl}",
			// "http://dev-apc.com"));
			share.setShareContent(share.getShareContent().replaceAll("？", ""));

		} catch (Exception e) {
			log.info("shareService.selectShare DB none Connect");
			e.printStackTrace();
		}

		model.addAttribute("share", share);
		model.addAttribute("shareHist", shareHist);
		return "nonsession/share/shareboard_view";
	}

	@RequestMapping(value = "/session/sharesign", method = RequestMethod.GET)
	public String noticeWrtie(Model model) {
		return "session/share/shareboard_sign";
	}

	@RequestMapping(value = "/shareWrite", method = RequestMethod.POST)
	public String shareWrite(Model model, HttpServletRequest request, HttpSession session) {

		String shareName = request.getParameter("title");
		String shareContent = request.getParameter("content");
		Users u = (Users) session.getAttribute("Users");
		String userId = u.getUserId();

		Shareboard share = new Shareboard();
		share.setShareName(shareName);
		shareContent = shareContent.replaceAll("？", "");
		share.setShareContent(shareContent);
		share.setInsertId(userId);
		share.setModifyId(userId);

		int newShareNo = 0;
		try {
			newShareNo = shareService.insertShareTx(share);
		} catch (Exception e) {
			log.info("shareService.writeShare DB none Connect");
		}

		return "redirect:/shareView?shareNo=" + newShareNo;
	}

	@RequestMapping(value = "/s/shareUpdate", method = RequestMethod.POST)
	public String noticeUpdate(Model model, HttpServletRequest request) {
		String shareNo = request.getParameter("shareNo");
		Shareboard share = null;
		try {
			share = shareService.selectShare(Integer.parseInt(shareNo));
			share.setShareContent(share.getShareContent().replaceAll("？", ""));
		} catch (Exception e) {
			log.info("shareService.selectShare DB none Connect");
		}
		model.addAttribute("share", share);
		return "session/share/shareboard_change";
	}

	@RequestMapping(value = "/shareUpdate", method = RequestMethod.POST)
	public String shareUpdate(Model model, HttpServletRequest request, HttpSession session) {

		String shareNo = request.getParameter("shareNo");
		String shareName = request.getParameter("title");
		String shareContent = request.getParameter("content");
		String ipAddr = request.getRemoteAddr();
		String userId;
		try {
			Users u = (Users) session.getAttribute("Users");
			userId = u.getUserId();
		} catch (Exception e) {
			userId = "999999";
		}

		Shareboard share = new Shareboard();
		share.setShareNo(Integer.parseInt(shareNo));
		share.setShareName(shareName);
		shareContent = shareContent.replaceAll("？", "");
		share.setShareContent(shareContent);
		share.setInsertId(userId);
		share.setModifyId(userId);
		share.setIpAddr(ipAddr);

		try {
			shareService.updateShareTx(share);
		} catch (Exception e) {
			log.info("shareService.updateShare DB none Connect");
		}
		return "redirect:/shareView?shareNo=" + shareNo;
	}

	// 댓글달기 ajax
	@RequestMapping(value = "/shareVersionCheck", method = RequestMethod.POST)
	public @ResponseBody int shareVersionCheck(@RequestParam int shareNo, @RequestParam int version,
			HttpSession session) {
		HashMap<String, Object> paramMap = new HashMap<>();
		paramMap.put("shareNo", shareNo);
		paramMap.put("version", version);
		int res = 0;

		try {
			// 버전정보 일치시 1로 업데이트 해야함..현재는 모두 업데이트가능으로
			res = shareService.selectVersionCheck(paramMap);
		} catch (Exception e) {
			log.info("ShareService.shareVersionCheck DB none Connect");
			res = -1;
		}

		return res;
	}

}
