package my.prac.core.game.dto;

import java.io.Serializable;

public class AttackDeathStat {
	public int totalAttacks;
	public int totalDeaths;
	public int hunterAttacks;

	public int getHunterAttacks() {
		return hunterAttacks;
	}

	public void setHunterAttacks(int hunterAttacks) {
		this.hunterAttacks = hunterAttacks;
	}

	public int getTotalAttacks() {
        return totalAttacks;
    }

    public void setTotalAttacks(int totalAttacks) {
        this.totalAttacks = totalAttacks;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }
    
	public AttackDeathStat() {
	}

	public AttackDeathStat(int totalAttacks, int totalDeaths) {
		this.totalAttacks = totalAttacks;
		this.totalDeaths = totalDeaths;
	}
}