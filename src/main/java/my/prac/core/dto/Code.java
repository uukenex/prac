package my.prac.core.dto;

public class Code extends AbstractModel {
	private static final long serialVersionUID = 1L;

	private String codeLgroup;
	private String codeMgroup;
	private String codeName;
	private String codeSname;
	private String codeGroup;
	private String remark;
	private String remark1;
	private String remark2;
	private String useYn;
	private String content;

	public String getCodeLgroup() {
		return this.codeLgroup;
	}

	public String getCodeMgroup() {
		return this.codeMgroup;
	}

	public String getCodeName() {
		return this.codeName;
	}

	public String getCodeSname() {
		return this.codeSname;
	}

	public String getCodeGroup() {
		return this.codeGroup;
	}

	public String getRemark() {
		return this.remark;
	}

	public String getRemark1() {
		return this.remark1;
	}

	public String getRemark2() {
		return this.remark2;
	}

	public String getUseYn() {
		return this.useYn;
	}

	public String getContent() {
		return this.content;
	}

	public void setCodeLgroup(String codeLgroup) {
		this.codeLgroup = codeLgroup;
	}

	public void setCodeMgroup(String codeMgroup) {
		this.codeMgroup = codeMgroup;
	}

	public void setCodeName(String codeName) {
		this.codeName = codeName;
	}

	public void setCodeSname(String codeSname) {
		this.codeSname = codeSname;
	}

	public void setCodeGroup(String codeGroup) {
		this.codeGroup = codeGroup;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public void setRemark1(String remark1) {
		this.remark1 = remark1;
	}

	public void setRemark2(String remark2) {
		this.remark2 = remark2;
	}

	public void setUseYn(String useYn) {
		this.useYn = useYn;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
