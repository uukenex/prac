package my.prac.core.dto;

import java.io.Serializable;
import java.sql.Timestamp;

public class AbstractModel implements Serializable {
	private static final long serialVersionUID = 1L;

	private String insertId;
	private Timestamp insertDate;
	private String modifyId;
	private Timestamp modifyDate;

	public String getInsertId() {
		return insertId;
	}

	public void setInsertId(String insertId) {
		this.insertId = insertId;
	}

	public Timestamp getInsertDate() {
		return insertDate;
	}

	public void setInsertDate(Timestamp insertDate) {
		this.insertDate = insertDate;
	}

	public String getModifyId() {
		return modifyId;
	}

	public void setModifyId(String modifyId) {
		this.modifyId = modifyId;
	}

	public Timestamp getModifyDate() {
		return modifyDate;
	}

	public void setModifyDate(Timestamp modifyDate) {
		this.modifyDate = modifyDate;
	}

}
