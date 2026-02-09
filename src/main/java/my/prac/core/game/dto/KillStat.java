package my.prac.core.game.dto;

import java.io.Serializable;

public class KillStat {
    public int monNo;
    public String monName;
    public int killCount;
    public int nmKillCount;
    
    public KillStat() {}
    public KillStat(int monNo, String monName, int killCount, int nmKillCount) {
        this.monNo = monNo;
        this.monName = monName;
        this.killCount = killCount;
        this.nmKillCount = nmKillCount;
    }
}