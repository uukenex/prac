package my.prac.core.dto;

import java.util.Date;

/**
 * 게시글_댓글 모델 클래스.
 * 
 * @author generated by ERMaster
 * @version $Id$
 */
public class CommentReply extends AbstractModel {

	/** serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** 댓글_번호. */
	private Integer replyNo;

	/** 댓글_내용. */
	private String replyContent;
	/** 댓글_날짜. */
	private Date replyDate;

	/** 게시글. */
	private Integer replyCommentNo;

	/** 사용자. */
	private String userId;

	private String userNick;

	public Integer getReplyNo() {
		return replyNo;
	}

	public void setReplyNo(Integer replyNo) {
		this.replyNo = replyNo;
	}

	public String getReplyContent() {
		return replyContent;
	}

	public void setReplyContent(String replyContent) {
		this.replyContent = replyContent;
	}

	public Date getReplyDate() {
		return replyDate;
	}

	public void setReplyDate(Date replyDate) {
		this.replyDate = replyDate;
	}

	public Integer getReplyCommentNo() {
		return replyCommentNo;
	}

	public void setReplyCommentNo(Integer replyCommentNo) {
		this.replyCommentNo = replyCommentNo;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserNick() {
		return userNick;
	}

	public void setUserNick(String userNick) {
		this.userNick = userNick;
	}

}
