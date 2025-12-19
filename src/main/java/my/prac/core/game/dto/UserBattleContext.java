package my.prac.core.game.dto;

public class UserBattleContext {
	 // 공통 입력/기본 정보
    public boolean success;
    public String errorMessage;

    public String roomName;
    public String userName;
    public String param1;
    public String targetUser;

    public User user;
    public String job;

    // MARKET 버프 (raw)
    public int bAtkMinRaw;
    public int bAtkMaxRaw;
    public int bCriRaw;
    public int bRegenRaw;
    public int bHpMaxRaw;
    public int bCriDmgRaw;
    public int bHpMaxRateRaw;
    public int bAtkMaxRateRaw;

    // 무기
    public int weaponLv;
    public int weaponBonus;

    // 기본 스탯
    public int baseMin;
    public int baseMax;
    public int baseHpMax;
    public int baseRegen;
    public int baseCritRate;
    public int baseCritDmg;

    // 축복/최종 스탯
    public boolean hasBless;
    public int blessRegenBonus;

    public int atkMinWithItem;
    public int atkMaxWithItem;
    public int finalHpMax;
    public int effRegen;

    public int shownCrit;     // 표시용 크리율
    public int shownRegen;    // 표시용 리젠 (축복 포함)
    public int shownCritDmg;  // 표시용 크리뎀

    // 필요하면 누적 SP, 현재 SP도 여기에 추가 가능
    public int currentPoint;
    public int lifetimeSp;
    
 // 직업 보너스(표시용)
    public int jobHpMaxBonus;   // 직업으로 인해 추가된 HP (프리스트/파이터)
    public int jobRegenBonus;   // 직업으로 인해 추가된 리젠 (프리스트용)
    
    public int dailyAtkBonus;
    public int dailyCriDmgBonus;
    public boolean isJobMaster;
    
    public boolean isReturnUser;
    
}