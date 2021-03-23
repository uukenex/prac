package my.prac.core.dto;

public class Shareboard extends AbstractModel {

	/** serialVersionUID. */
	private static final long serialVersionUID = 1L;

	int shareNo;
	String shareName;
	String shareContent;
	int version;

	/** VO */
	String userNick;

	public String getUserNick() {
		return userNick;
	}

	public void setUserNick(String userNick) {
		this.userNick = userNick;
	}

	public int getShareNo() {
		return shareNo;
	}

	public void setShareNo(int shareNo) {
		this.shareNo = shareNo;
	}

	public String getShareName() {
		return shareName;
	}

	public void setShareName(String shareName) {
		this.shareName = shareName;
	}

	public String getShareContent() {
		return shareContent;
	}

	public void setShareContent(String shareContent) {
		this.shareContent = shareContent;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}
}
