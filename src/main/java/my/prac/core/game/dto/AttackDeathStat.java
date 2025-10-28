package my.prac.core.game.dto;

import java.io.Serializable;

public class AttackDeathStat {
	public int totalAttacks;
	public int totalDeaths;

	public AttackDeathStat() {
	}

	public AttackDeathStat(int totalAttacks, int totalDeaths) {
		this.totalAttacks = totalAttacks;
		this.totalDeaths = totalDeaths;
	}
}