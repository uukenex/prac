package my.prac.core.game.dto;

import java.util.HashMap;
import java.util.List;
import my.prac.core.util.SP;

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
	public int mktAtkMin;
	public int mktAtkMax;
	public int mktCrit;
	public int mktRegen;
	public int mktHpMax;
	public int mktCritDmg;
	public int mktHpMaxRate;
	public int mktAtkMaxRate;

	// 기본 스탯
	public int baseAtkMin;
	public int baseAtkMax;
	public int baseHpMax;
	public int baseRegen;
	public int baseCrit;
	public int baseCritDmg;

	// 축복/최종 스탯
	public boolean hasBless;
	public int blessRegenBonus;

	public int atkMin;
	public int atkMax;
	public int hpMax;
	public int regen;
	public int hpCur;   // computeEffectiveHpFromLastAttack 결과 (리젠 반영 현재 체력)

	public int dispCrit; // 표시용 크리율
	public int dispRegen; // 표시용 리젠 (축복 포함)
	public int dispCritDmg; // 표시용 크리뎀

	// 필요하면 누적 SP, 현재 SP도 여기에 추가 가능
	public SP currentPoint;
	public String currentPointStr;
	public SP lifetimeSp;
	public String lifetimeSpStr;

	// 직업 보너스(표시용)
	public int jobHp; // 직업으로 인해 추가된 HP (프리스트/파이터)
	public int jobRegen; // 직업으로 인해 추가된 리젠 (프리스트용)

	public int dropAtkMin;
	public int dropAtkMax;
	public int dropHp;
	public int dropRegen;
	public int dropCrit;
	public int dropCritDmg;

	public String hunterGrade;

	public int hellNerfAtkMin; 
    public int hellNerfAtkMax; 
    public int hellNerfHp ;
    public int hellNerfCrit; 
    public int hellNerfCritDmg; 
    
	// [OPT-HUNTER] attackInfo 에서 미리 조회한 dropsRows 공유용 (applyDropBonusToContext 중복 조회 방지)
	public List<HashMap<String,Object>> preDropRows = null;
}