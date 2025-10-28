package my.prac.core.game.dto;

import java.io.Serializable;

public class KillStat {
    public int monNo;
    public String monName;
    public int killCount;
    public KillStat() {}
    public KillStat(int monNo, String monName, int killCount) {
        this.monNo = monNo;
        this.monName = monName;
        this.killCount = killCount;
    }
}