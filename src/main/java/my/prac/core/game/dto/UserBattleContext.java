package my.prac.core.game.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import my.prac.core.game.dto.AttackDeathStat;
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

	public int crit;
	public int critDmg;

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

	// 보스 아이템(7001~7010) 보유 목록 (calcUserBattleContext에서 채워짐)
	public Set<Integer> ownedBossItems = new HashSet<>();

	// GP 잔액 (calcUserBattleContext에서 채워짐, 0이면 미보유)
	public double gpBalance = 0.0;

	// 공격/사망 통계 (calcUserBattleContext에서 채워짐, ma_cooldownAndHp에서 재사용)
	public AttackDeathStat ads = null;

	// 세트 효과 - SPECIAL_* 타입 활성 세트 보너스 목록
	public List<String> activeSetSpecials = null;

	// 세트 효과 - 최종 비율 보너스 및 쿨타임 감소
	public int setAtkFinalRate   = 0; // 최종 공격력 증가율 (%)
	public int setCritFinalRate  = 0; // 최종 크리율 증가율 (%)
	public int setCooldownReduce = 0; // 쿨타임 감소 (초)
	public int setEvasionRate    = 0; // 몬스터/보스 공격 회피율 (%)
}