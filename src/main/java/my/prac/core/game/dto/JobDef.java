package my.prac.core.game.dto;

import java.io.Serializable;

public class JobDef {

	// 직업 공통 정의
	public String name;
	public String listLine;
	public String attackLine;

	public JobDef(String name, String listLine, String attackLine) {
		this.name = name;
		this.listLine = listLine;
		this.attackLine = attackLine;
	}
}
