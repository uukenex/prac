package my.prac.core.dto;

import java.io.Serializable;

public class Config implements Serializable {
	private static final long serialVersionUID = 1L;

	private String item;
	private String val;
	private String content;

	public String getItem() {
		return this.item;
	}

	public String getVal() {
		return this.val;
	}

	public String getContent() {
		return this.content;
	}

	public void setItem(String item) {
		this.item = item;
	}

	public void setVal(String val) {
		this.val = val;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
