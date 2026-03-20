package my.prac.core.game.dto;

import java.io.Serializable;

public class JobChangeReq {
	public String baseJob;   // 어떤 직업으로
	public int minCount;     // 몇 회 이상 공격해야 하는지

	public JobChangeReq(String baseJob, int minCount) {
        this.baseJob = baseJob;
        this.minCount = minCount;
    }
}
