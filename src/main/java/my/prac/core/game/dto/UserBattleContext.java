package my.prac.core.game.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public double hellNerfRate; // 헬너프 비율 (예: 0.05 = 5%)
    
	// 헬박스 영구 스탯 보너스 표시용
	public int hellBoxAtkMin;
	public int hellBoxAtkMax;
	public int hellBoxHp;
	public int hellBoxRegen;
	public int hellBoxCrit;
	public int hellBoxCritDmg;

	// 경험치판매 영구 스탯 보너스 표시용
	public int expSellHp;
	public int expSellAtkMin;
	public int expSellAtkMax;
	public double expSellCrit;
	public double expSellCritDmg;
    
	// [OPT-HUNTER] attackInfo 에서 미리 조회한 dropsRows 공유용 (applyDropBonusToContext 중복 조회 방지)
	public List<HashMap<String,Object>> preDropRows = null;

	// 보스 아이템(7001~7019) 보유 목록 (bossItemQtyMap.keySet 기반으로 채워짐)
	public Set<Integer> ownedBossItems = new HashSet<>();
	// 보스 아이템 강화 수량 (단일 소스, calcUserBattleContext에서 채워짐)
	public Map<Integer,Integer> bossItemQtyMap = new HashMap<>();
	// [999] 선물: 일반 인벤토리 기반 별도 관리
	public boolean has999Gift = false;

	// GP 잔액 (calcUserBattleContext에서 채워짐, 0이면 미보유)
	public double gpBalance = 0.0;

	// 공격/사망 통계 (calcUserBattleContext에서 채워짐, ma_cooldownAndHp에서 재사용)
	public AttackDeathStat ads = null;

	// 세트 효과 - SPECIAL_* 타입 활성 세트 보너스 목록
	public List<String> activeSetSpecials = null;

	// 세트 효과 - 최종 비율 보너스 및 쿨타임 감소
	public int setAtkFinalRate    = 0; // 최종 공격력 증가율 (%)
	public int setCritFinalRate   = 0; // 최종 크리율 증가율 (%)
	public int setCooldownReduce  = 0; // 쿨타임 감소 (%)
	public int setCooldownIncrease = 0; // 쿨타임 증가 (%)
	public int setEvasionRate     = 0; // 몬스터/보스 공격 회피율 (%)

	// 직업레벨 보너스 (전 직업 레벨 합산)
	public int totalJobLv = 0;

	// 워록 멀티킬 여부 (buildAttackMessage에서 header 생략 제어)
	public boolean warlockMultiKill = false;
	// 워록 멀티킬 실제 데미지 범위
	public long warlockDmgMin = 0;
	public long warlockDmgMax = 0;
	// SP 항목별 계산식 (=== 이후 표시용)
	public java.util.List<String> spBreakdowns = new java.util.ArrayList<>();
	// 워록 멀티킬 EXP 계산식 (=== 이후 표시용)
	public String warlockExp1Breakdown = null;
	public java.util.List<String> warlockExpBreakdowns = new java.util.ArrayList<>();
	// 워록 멀티킬 중 레벨업 발생 시 === 이후 표시용 (LevelUpResult 수치 복사)
	public int warlockLvUpBefore = 0;
	public int warlockLvUpAfter = 0;
	public int warlockLvUpHpBefore = 0;
	public int warlockLvUpHpAfter = 0;
	public int warlockLvUpHpDelta = 0;
	public int warlockLvUpAtkMinBefore = 0;
	public int warlockLvUpAtkMinAfter = 0;
	public int warlockLvUpAtkMaxBefore = 0;
	public int warlockLvUpAtkMaxAfter = 0;
	public int warlockLvUpAtkMinDelta = 0;
	public int warlockLvUpAtkMaxDelta = 0;
	public int warlockLvUpCritBefore = 0;
	public int warlockLvUpCritAfter = 0;
	public int warlockLvUpCritDelta = 0;
	public int warlockLvUpRegenBefore = 0;
	public int warlockLvUpRegenAfter = 0;
	public int warlockLvUpRegenDelta = 0;
}