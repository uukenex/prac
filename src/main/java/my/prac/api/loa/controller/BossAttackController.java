package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackCalc;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.DamageOutcome;
import my.prac.core.game.dto.Flags;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;

@Controller
public class BossAttackController {

	/* ===== Config / Const ===== */
	private static final int COOLDOWN_SECONDS = 120; // 2분
	private static final int REVIVE_WAIT_MINUTES = 0;//쿼리에서계산함
	private static final String NL = "♬";
	// 🍀 Lucky: 전투 시작 시 10% 확률 고정(신규 전투에서만 결정)
	private static final double LUCKY_RATE = 0.15;
	private static final double LUCKY_RATE_DOSA = 0.20;
	private static final String ALL_SEE_STR = "===";

	/* ===== DI ===== */
	@Autowired LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")        BotService botService;
	@Resource(name = "core.prjbot.BotDAO")            BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewService")     BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")  BotSettleService botSettleService;

	
	/** 
	 */
	public String getHpStatus(HashMap<String,Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String param1  = Objects.toString(map.get("param1"), "").trim();
	    User u = botNewService.selectUser(userName, roomName);
	    if (u == null) {
	        return "❌ 유저 정보를 찾을 수 없습니다.";
	    }

	    final String job = (u.job == null ? "" : u.job.trim());

	 // 1) MARKET 장비 버프 (monsterAttack과 동일)
	    HashMap<String, Number> buffs = null;
	    try {
	        buffs = botNewService.selectOwnedMarketBuffTotals(userName, roomName);
	    } catch (Exception ignore) {}

	    int bHpMax  = (buffs != null && buffs.get("HP_MAX")   != null) ? buffs.get("HP_MAX").intValue()   : 0;
	    int bRegen  = (buffs != null && buffs.get("HP_REGEN") != null) ? buffs.get("HP_REGEN").intValue() : 0;

	    // 2) 프리스트: 아이템 HP/리젠 1.5배
	    if ("프리스트".equals(job)) {
	        bHpMax = (int)Math.round(bHpMax * 1.25);
	        bRegen = (int)Math.round(bRegen * 1.25);
	    }

	    int baseHpMax = u.hpMax;
	    int baseRegen = u.hpRegen;

	    // 3) 운영자의 축복
	    boolean hasBless = (u.lv <= 15);
	    int blessRegenBonus = hasBless ? 5 : 0;

	    // 🩸 흡혈귀: monsterAttack 캐논과 동일하게 "아이템 리젠만" 무효
	    if ("흡혈귀".equals(job)) {
	        bRegen = 0;
	    }

	    // 4) 최종 Max HP
	    int finalHpMax = baseHpMax + bHpMax;
	    if ("전사".equals(job)) {
	        finalHpMax += baseHpMax;
	    }
	    if (finalHpMax <= 0) finalHpMax = 1;

	    // 5) 최종 리젠 (기본+아이템+축복)
	    int effRegen = baseRegen + bRegen;
	    effRegen += blessRegenBonus;
	    if (effRegen < 0) effRegen = 0;

	    // 6) 유효 체력 계산 (attackInfo와 동일 함수 사용)
	    int effHp = computeEffectiveHpFromLastAttack(userName, roomName, u, finalHpMax, effRegen);
	    if (effHp > finalHpMax) effHp = finalHpMax;

	    StringBuilder sb = new StringBuilder();
	    sb.append("❤️ ").append(userName).append("님의 체력 상태").append(NL)
	      .append("현재 체력: ").append(effHp).append(" / ").append(finalHpMax).append(NL)
	      .append("5분당 회복: +").append(effRegen).append(NL);

	    if (hasBless) {
	        sb.append("✨ 운영자의 축복 포함되어있음 (Lv 15 이하): 5분당 회복 +5").append(NL);
	    }

	    if (effHp <= finalHpMax * 0.2) {
	        sb.append("⚠️ 현재 공격 불가").append(NL);
	    } else if (effHp >= finalHpMax) {
	        sb.append("✅ 현재 체력은 최대 상태입니다.").append(NL);
	    }
	    
	 // ✅ 회복 예측 스케줄 (예: 60분 범위 내)
	    String regenInfo = buildRegenScheduleSnippetEnhanced2(userName, roomName, u, 30,effHp, finalHpMax, effRegen, 60);

	    if (regenInfo != null && !regenInfo.isEmpty()) {
	        sb.append(regenInfo);
	    }
	    
	 // 🔹 여기서 "공격 로직"에서 쓰는 진행중 전투 계산 재사용
	    try {
	        OngoingBattle ob = botNewService.selectOngoingBattle(userName, roomName);
	        if (ob != null) {
	            Monster m = botNewService.selectMonsterByNo(ob.monNo);
	            if (m != null) {
	                int monMaxHp       = m.monHp;
	                int monHpRemain    = Math.max(0, m.monHp - ob.totalDealtDmg);

	                sb.append(NL)
	                  .append("▶ 전투중인 몬스터").append(NL)
	                  .append(m.monName)
	                  .append(" (").append(monHpRemain).append(" / ").append(monMaxHp).append(")")
	                  .append(NL);
	            }
	        } else {
	            // 진행중 전투는 없지만 타겟몬은 있을 수 있음 (선택)
	            Monster m = botNewService.selectMonsterByNo(u.targetMon);
	            if (m != null) {
	                sb.append(NL)
	                  .append("▶ 타겟 몬스터").append(NL)
	                  .append(m.monName)
	                  .append(" (").append(m.monHp).append(" / ").append(m.monHp).append(")")
	                  .append(NL);
	            }
	        }
	    } catch (Exception ignore) {
	        sb.append(NL).append("전투중인 몬스터 정보를 불러오지 못했습니다.").append(NL);
	    }


	    return sb.toString();
	}
	
	/* ===== Public APIs ===== */
	public String changeJob(HashMap<String,Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String selRaw  = Objects.toString(map.get("param1"), "").trim();

	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    User u = botNewService.selectUser(userName, roomName);
	    if (u == null)
	        return "유저 정보를 찾을 수 없습니다.";

	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}

	    String curJob = (u.job == null ? "" : u.job.trim());
	    String sel = selRaw;

	    // 1) param1 없이 호출한 경우: 안내
	    if (sel.isEmpty()) {
	        if (curJob.isEmpty()) {
	            // 아직 직업 없음 → 직업 설명
	            return buildJobDescriptionList();
	        } else {
	            // 현재 직업 보여주고 설명
	            return "현재 직업: " + curJob + NL + buildJobDescriptionList();
	        }
	    }

	    // 2) 입력한 직업명 파싱
	    String newJob = normalizeJob(sel);
	    if (newJob == null) {
	        return "존재하지 않는 직업입니다. /직업 으로 확인해주세요.";
	    }

	    // 3) 레벨 제한 (처음/변경 모두 공통 룰)

	    // 4) 동일 직업으로 변경 시도
	    if (!curJob.isEmpty() && newJob.equals(curJob)) {
	        return "이미 [" + curJob + "] 직업입니다.";
	    }

	    
	 // 레벨 4는 직업 체험 모드: 쿨타임 체크 생략 + 날짜 미갱신(체험은 기록 안 남김)
	    /*
	    if (u.lv < 5) {
	        botNewService.updateUserJobAndChangeDate(userName, roomName, newJob); // **JOB_CHANGE_DATE 갱신 없는 버전 사용**
	        return "✨ 레벨5 미만 직업 체험: 쿨타임 없이 [" + newJob + "] 으로 변경했습니다!";
	    }
	    */
	    // 5) 24시간 쿨타임 체크
	    // - JOB_CHANGE_DATE 기본값을 SYSDATE-6/24 로 잡았으므로
	    //   초기 유저는 바로 변경 가능하게 됨.
	    
	    /*
	    Timestamp lastChange = u.jobChangeDate;
	    if (lastChange != null) {
	        long diffSec = java.time.Duration.between(lastChange.toInstant(), java.time.Instant.now()).getSeconds();
	        long limitSec = 0L * 60 * 60;

	        if (diffSec < limitSec) {
	            long remain = limitSec - diffSec;
	            long rh = remain / 3600;
	            long rm = (remain % 3600) / 60;

	            return "직업 변경은 0시간에 1회 가능합니다." + NL
	                 + "다음 변경까지 남은 시간: " + rh + "시간 " + rm + "분";
	        }
	    }
	    */

	    // 6) 직업 변경 수행 (JOB + JOB_CHANGE_DATE = SYSDATE)
	    int updated = botNewService.updateUserJobAndChangeDate(userName, roomName, newJob);
	    if (updated <= 0) {
	        return "직업 변경 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	    }
	    try {
	    	botNewService.closeOngoingBattleTx(userName, roomName);
	    }catch(Exception e){
	    	
	    }
	    

	    // 7) 완료 메시지
	    return "✨ " + userName + "님, [" + newJob + "] 으로 직업이 변경되었습니다." + NL;
	}


	private String buildJobDescriptionList() {
		StringBuilder sb = new StringBuilder();
	    sb.append("전직 가능한 직업 목록").append(ALL_SEE_STR);
	    for (JobDef def : JOB_DEFS.values()) {
	        sb.append(def.listLine).append(NL);
	        sb.append(def.attackLine).append(NL).append(NL);
	        
	    }
	    sb.append("♬ /직업 [직업명] 으로 전직 가능합니다.").append(NL);
	    return sb.toString();
	}

	
	private String normalizeJob(String raw) {
		 if (raw == null) return null;
		    String s = raw.trim();

		    // 별칭을 허용하고 싶으면 여기서 추가 매핑
		    // if ("전".equals(s) || "전사".equals(s)) s = "전사";

		    JobDef def = JOB_DEFS.get(s);
		    return (def != null ? def.name : null);
	}

	public String attackInfo(HashMap<String, Object> map) {
	    final String allSeeStr = "===";
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    // ① param1으로 다른 유저 조회 시도
	    String targetUser = userName;
	    if (map.get("param1") != null && !Objects.toString(map.get("param1"), "").isEmpty()) {
	        List<String> newUserName = botNewService.selectParam1ToNewUserSearch(map);
	        if (newUserName != null && !newUserName.isEmpty())
	            targetUser = newUserName.get(0);
	        else
	            return "해당 유저(" + map.get("param1") + ")를 찾을 수 없습니다.";
	    }

	    // ② 유저 조회
	    User u = botNewService.selectUser(targetUser, roomName);
	    if (u == null)
	        return targetUser + "님의 정보를 찾을 수 없습니다.";

	    String job = (u.job == null ? "" : u.job.trim());

	    // ③ 현재 포인트 조회
	    int currentPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(targetUser, roomName);
	        currentPoint = (p == null ? 0 : p);
	    } catch (Exception ignore) {}
	    final String pointStr = String.format("%,d sp", currentPoint);

	    int lifetimeSp = 0;
	    try {
	        Integer t = botNewService.selectTotalEarnedSp(targetUser, roomName);
	        lifetimeSp = (t == null ? 0 : t);
	    } catch (Exception ignore) {}
	    
	    
	    // ④ 무기강/보너스 조회
	    HashMap<String, Object> wm = new HashMap<>();
	    wm.put("userName", targetUser);
	    wm.put("roomName", roomName);
	    int weaponLv = 0;
	    try {
	        weaponLv = botService.selectWeaponLvCheck(wm);
	    } catch (Exception ignore) {
	        weaponLv = 0;
	    }
	    int weaponBonus = getWeaponAtkBonus(weaponLv); // 25강부터 +1

	    // ⑤ MARKET 장비 버프 합계 (raw)
	    HashMap<String, Number> buffs = null;
	    try {
	        buffs = botNewService.selectOwnedMarketBuffTotals(targetUser, roomName);
	    } catch (Exception ignore) {}

	    int bAtkMinRaw = (buffs != null && buffs.get("ATK_MIN")  != null) ? buffs.get("ATK_MIN").intValue()  : 0;
	    int bAtkMaxRaw = (buffs != null && buffs.get("ATK_MAX")  != null) ? buffs.get("ATK_MAX").intValue()  : 0;
	    int bCriRaw    = (buffs != null && buffs.get("ATK_CRI")  != null) ? buffs.get("ATK_CRI").intValue()  : 0;
	    int bRegenRaw  = (buffs != null && buffs.get("HP_REGEN") != null) ? buffs.get("HP_REGEN").intValue() : 0;
	    int bHpMaxRaw  = (buffs != null && buffs.get("HP_MAX")   != null) ? buffs.get("HP_MAX").intValue()   : 0;
	    int bCriDmgRaw = (buffs != null && buffs.get("CRI_DMG")  != null) ? buffs.get("CRI_DMG").intValue()  : 0;

	    // ===== 직업 보너스 계산용 (표시용 쪼개기) =====
	    int bAtkMin = bAtkMinRaw;
	    int bAtkMax = bAtkMaxRaw;
	    int bCri    = bCriRaw;
	    int bRegen  = bRegenRaw;
	    int bHpMax  = bHpMaxRaw;
	    int bCriDmg = bCriDmgRaw;

	    int jobHpMaxBonus   = 0;
	    int jobRegenBonus   = 0;

		// 사신: 아이템으로 인한 HP·크리티컬 증가 효과 미적용
		if ("사신".equals(job)) {
			bCri = 0; // 아이템 크리 확률 보너스 미적용
			bCriDmg = 0; // 아이템 크리 데미지 보너스 미적용
		}

		// 프리스트: 아이템 HP/리젠 효과 1.5배 (표시용 쪼개기)
		if ("프리스트".equals(job)) {
			int boostedHp = (int) Math.round(bHpMaxRaw * 1.25);
			int boostedRegen = (int) Math.round(bRegenRaw * 1.25);
			jobHpMaxBonus = boostedHp - bHpMaxRaw;
			jobRegenBonus = boostedRegen - bRegenRaw;
			bHpMax = boostedHp;
			bRegen = boostedRegen;
		}

	    // ===== 기본 스탯 =====
	    int baseMin   = u.atkMin;
	    int baseMax   = u.atkMax;
	    int baseHpMax = u.hpMax;

	    // ===== 전투 기준(직업 보너스 적용 전) =====
	    int atkMinWithItem = baseMin + bAtkMin;
	    int atkMaxWithItem = baseMax + weaponBonus + bAtkMax;

	    int shownCrit    = u.critRate + bCri;
	    int shownRegen   = u.hpRegen + bRegen;
	    int shownCritDmg = u.critDmg + bCriDmg;

	    // HP: 프리스트 직업 보너스 포함한 아이템/직업 적용 값
	    int hpMaxWithItemAndPriest = baseHpMax + bHpMax; // bHpMax는 위에서 프리스트 보정 포함 상태

	    // ===== 직업 보너스(전사) 반영 =====
	    // 전사 ATK 보너스: 기본 min 한 번 더, 기본 max 한 번 더 (아이템/강화 제외)
	    int finalAtkMin = atkMinWithItem;
	    int finalAtkMax = atkMaxWithItem;

	    if ("전사".equals(job)) {
	        //finalAtkMin += baseMin;
	        //finalAtkMax += baseMax;
	    }

	    // 전사 HP 보너스: 기본 HP 한 번 더 (아이템 제외)
	    int finalHpMax = hpMaxWithItemAndPriest;
	    if ("전사".equals(job)) {
	        finalHpMax += baseHpMax;
	    }
	    
	 // 🩸 흡혈귀: 리젠 완전 불가 (아이템/버프/운영자 축복 포함)
	    if ("흡혈귀".equals(job)) {
	    	bRegenRaw = 0;
	    }
	    
	    // 표시용 회복 적용 (전사/프리스트 포함 최종 HP 기준)
	    int effHp = computeEffectiveHpFromLastAttack(targetUser, roomName, u, finalHpMax, shownRegen);
	    if (effHp > finalHpMax) effHp = finalHpMax;

	    
	    
	    
	    
	    
	    // ⑧ 누적 통계/타겟
	    List<KillStat> kills = botNewService.selectKillStats(targetUser, roomName);
	    int totalKills = 0;
	    for (KillStat ks : kills) totalKills += ks.killCount;
	    AttackDeathStat ads = botNewService.selectAttackDeathStats(targetUser, roomName);
	    int totalAttacks = (ads == null ? 0 : ads.totalAttacks);
	    int totalDeaths  = (ads == null ? 0 : ads.totalDeaths);
	    Monster target = (u.targetMon > 0) ? botNewService.selectMonsterByNo(u.targetMon) : null;
	    String targetName = (target == null) ? "-" : target.monName;

	    // ⑨ 출력
	    StringBuilder sb = new StringBuilder();
	    sb.append("✨").append(targetUser).append(" 공격 정보").append(NL)
	      .append("Lv: ").append(u.lv);
	    if (!job.isEmpty()) {
	        sb.append(" (").append(job).append(")");
	    }
	    sb.append(", EXP ").append(u.expCur).append("/").append(u.expNext).append(NL);
	    sb.append("포인트: ").append(pointStr).append(NL);
	    sb.append("누적 획득 포인트: ").append(String.format("%,d", lifetimeSp)).append("sp").append(NL).append(NL);
	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL);
	    sb.append("⚔CRIT: ").append(shownCrit).append("%  CDMG ").append(shownCritDmg).append("%").append(NL);
	    sb.append("❤️HP: ").append(effHp).append(" / ").append(finalHpMax).append(",5분당회복+").append(shownRegen).append(NL).append(NL);

	    JobDef jobDef = JOB_DEFS.get(job);
	    if (jobDef != null && jobDef.attackLine != null && !jobDef.attackLine.isEmpty()) {
	        sb.append(jobDef.attackLine).append(NL);
	    }

	    sb.append("▶ 현재 타겟: ").append(targetName)
	      .append(" (MON_NO=").append(u.targetMon).append(")");

	    // 누적 전투
	    sb.append(allSeeStr);
	    
	    
	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL)
	      .append("   └ 기본 (").append(baseMin).append("~").append(baseMax).append(")").append(NL)
	      .append("   └ 시즌1 강화: ").append(weaponLv).append("강 (max+").append(weaponBonus).append(")").append(NL)
	      .append("   └ 아이템 (min").append(formatSigned(bAtkMinRaw))
	      .append(", max").append(formatSigned(bAtkMaxRaw)).append(")").append(NL);

	    /*
	    if ("전사".equals(job)) {
	        sb.append("   └ 직업 (min+")
	          .append(baseMin)
	          .append(", max+")
	          .append(baseMax)
	          .append(")")
	          .append(NL);
	    }
	    */
	    sb.append("⚔CRIT: ").append(shownCrit).append("%  CDMG ").append(shownCritDmg).append("%").append(NL)
	      .append("   └ 기본 (").append(u.critRate).append("%, ").append(u.critDmg).append("%)").append(NL);
	      
	      if ("사신".equals(job)) {
	    	    sb.append("   └ 아이템 (CRIT")
	    	      .append(formatSigned(bCriRaw))
	    	      .append("%, CDMG ")
	    	      .append(formatSigned(bCriDmgRaw))
	    	      .append("%) [미적용]").append(NL);
	    	} else {
	    	    sb.append("   └ 아이템 (CRIT")
	    	      .append(formatSigned(bCriRaw))
	    	      .append("%, CDMG ")
	    	      .append(formatSigned(bCriDmgRaw))
	    	      .append("%)").append(NL);
	    	}
	      

	    sb.append("❤️HP: ").append(effHp).append(" / ").append(finalHpMax)
	      .append(",5분당회복+").append(shownRegen).append(NL)
	      .append("   └ 기본 (HP+").append(baseHpMax)
	      .append(",5분당회복+").append(u.hpRegen).append(")").append(NL)
	      .append("   └ 아이템 (HP").append(formatSigned(bHpMaxRaw))
	      .append(",5분당회복").append(formatSigned(bRegenRaw)).append(")").append(NL);

	    if ("프리스트".equals(job) && (jobHpMaxBonus != 0 || jobRegenBonus != 0)) {
	        sb.append("   └ 직업 (HP")
	          .append(formatSigned(jobHpMaxBonus))
	          .append(",5분당회복")
	          .append(formatSigned(jobRegenBonus))
	          .append(")").append(NL);
	    }

	    if ("전사".equals(job)) {
	        sb.append("   └ 직업 (HP+")
	          .append(+baseHpMax)
	          .append(")").append(NL);
	    }

	 // 인벤토리
	 // 인벤토리
	    try {
	        List<HashMap<String, Object>> bag = botNewService.selectInventorySummaryAll(targetUser, roomName);

	        sb.append(NL).append("▶ 인벤토리").append(NL);
	        if (bag == null || bag.isEmpty()) {
	            sb.append("- (비어있음)").append(NL);
	        } else {

	            // 1) ITEM_NO ASC 정렬
	            bag.sort((a, b) -> {
	                int noA = parseIntSafe(Objects.toString(a.get("ITEM_ID"), "0"));
	                int noB = parseIntSafe(Objects.toString(b.get("ITEM_ID"), "0"));
	                return Integer.compare(noA, noB);
	            });

	            // 2) 장비 & 잡템 분리
	            List<String> equipList = new ArrayList<>();
	            List<String> etcList   = new ArrayList<>();

	            for (HashMap<String, Object> row : bag) {
	                String itemName = Objects.toString(row.get("ITEM_NAME"), "-");
	                String qtyStr   = Objects.toString(row.get("TOTAL_QTY"), "0");
	                String typeStr  = Objects.toString(row.get("ITEM_TYPE"), "");
	                String enhance  = Objects.toString(row.get("ENHANCE"), "0");  // 강화 값 있으면 사용

	                if ("MARKET".equals(typeStr)) {
	                    // 장비 → "이름(+강화)"
	                    try {
	                        int e = Integer.parseInt(enhance);
	                        if (e > 0) itemName = itemName + "(+" + e + ")";
	                    } catch (Exception ignore) {}
	                    equipList.add(itemName);
	                } else {
	                    // 잡템 → "이름x수량"
	                    etcList.add(itemName + "x" + qtyStr);
	                }
	            }

	            // 3) 한 줄 요약 형태로 정렬된 리스트 출력
	            sb.append("장비: ");
	            if (equipList.isEmpty()) {
	                sb.append("(없음)");
	            } else {
	                sb.append(String.join(", ", equipList));
	            }
	            sb.append(NL).append(NL);

	            sb.append("기타: ");
	            if (etcList.isEmpty()) {
	                sb.append("(없음)");
	            } else {
	                // 너무 길면 자동 축약
	            	sb.append(String.join(", ", etcList));
	            	/*
	                if (etcList.size() > 10) {
	                    List<String> head = etcList.subList(0, 10);
	                    sb.append(String.join(", ", head))
	                      .append(" 외 ").append(etcList.size() - 10).append("종");
	                } else {
	                    sb.append(String.join(", ", etcList));
	                }*/
	            }
	            sb.append(NL).append(NL);
	        }
	    } catch (Exception ignore) {}
	    
	    
	    sb.append("누적 전투 기록").append(NL)
	      .append("- 총 공격 횟수: ").append(totalAttacks).append("회").append(NL)
	      .append("- 총 사망 횟수: ").append(totalDeaths).append("회").append(NL).append(NL);

	    // 누적 처치
	    sb.append("누적 처치 기록 (총 ").append(totalKills).append("마리)").append(NL);
	    if (kills.isEmpty()) {
	        sb.append("기록 없음").append(NL);
	    } else {
	        for (KillStat ks : kills) {
	            sb.append("- ").append(ks.monName)
	              .append(" (MON_NO=").append(ks.monNo).append(") : ")
	              .append(ks.killCount).append("마리").append(NL);
	        }
	    }

	    
	    // 업적
	    try {
	        List<HashMap<String,Object>> achv = botNewService.selectAchievementsByUser(targetUser,roomName);
	        sb.append(NL).append("▶ 업적").append(NL);
	        if (achv == null || achv.isEmpty()) {
	            sb.append("- 달성된 업적이 없습니다.").append(NL);
	        } else {
	            for (HashMap<String,Object> row : achv) {
	                String cmd = Objects.toString(row.get("CMD"), "");
	                String label = formatAchievementLabelSimple(cmd);
	                if (!label.isEmpty()) {
	                    sb.append("✨ ").append(label).append(NL);
	                }
	            }
	        }
	    } catch (Exception ignore) {}

	    return sb.toString();
	}


	/** 타겟 변경 (번호/이름 허용) */
	public String changeTarget(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");
		final String input = Objects.toString(map.get("monNo"), "").trim();
		if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
		if (input.isEmpty()) return guideSetTargetMessage();

		if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}

		Monster m = input.matches("\\d+")
		        ? botNewService.selectMonsterByNo(Integer.parseInt(input))
		        : botNewService.selectMonsterByName(input);

		if (m == null) {
			 // 유저 레벨 조회 (없으면 Lv1 기준)
		    User u = botNewService.selectUser(userName, roomName);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("해당 몬스터(").append(input).append(")를 찾을 수 없습니다.").append(NL)
		      .append("아래 목록 중에서 선택해주세요:").append(NL).append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterCompactLine(mm, userLv));
		    }
		    return sb.toString();
		}
		
		User u = botNewService.selectUser(userName, roomName);
		if (u == null) {
		    botNewService.insertUserWithTargetTx(userName, roomName, m.monNo);
		    return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		         + "▶ 선택: " + renderMonsterCompactLine(m, 1);
		}
		if (u.targetMon == m.monNo) return "현재 타겟이 이미 " + m.monName + "(MON_NO=" + m.monNo + ") 입니다.";

		


		// 예: 사용자가 /공격타겟 13 입력 → newMonNo = 13
		int newMonNo = m.monNo; // 네가 사용하는 변수명에 맞게 치환

		if(newMonNo > 1  && newMonNo < 50) {
			// 1) 바로 아래 등급 몬스터 번호 계산
			int prevMonNo = Math.max(1, newMonNo - 1);

			// 2) 해당 몬스터를 내가 몇 마리 잡았는지 조회 (기존 selectKillStats 재사용)
			int killsOnPrev = 0;
			List<KillStat> killStats = botNewService.selectKillStats(userName, roomName);
			if (killStats != null) {
			    for (KillStat ks : killStats) {
			        if (ks.monNo == prevMonNo) {          // KillStat의 필드명에 맞게 조정
			            killsOnPrev = ks.killCount;      // getKillCount() 쓰는 구조면 그걸로
			            break;
			        }
			    }
			}

			// 3) 조건 미달 시 거부
			if (killsOnPrev < 5) {
			    Monster prev = botNewService.selectMonsterByNo(prevMonNo);
			    String prevName = (prev == null ? ("Lv " + prevMonNo) : prev.monName);
			    return "상위 등급으로 올리려면 [" + prevName + "]을(를) 최소 5마리 처치해야 합니다. (현재 "
			         + killsOnPrev + "마리)";
			}
		}
		
		
		botNewService.closeOngoingBattleTx(userName, roomName);
		botNewService.updateUserTargetMonTx(userName, roomName, m.monNo);
		int userLvForView = (u != null ? u.lv : 1);
		return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		     + "▶ 선택: " + NL + renderMonsterCompactLine(m, userLvForView);
	}
	public String buyItem(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String raw = Objects.toString(map.get("param1"), "").trim();


	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }
	    
	    //User u = botNewService.selectUser(userName, roomName);
	    //String job = (u == null || u.job == null) ? "" : u.job.trim();
	    //boolean isMerchant = "상인".equals(job);

	    
	    
	    boolean hiddenYn = false;
	    
	    if(raw.equals("전체")) {
	    	hiddenYn = false;
	    }
	    
	    if( raw.isEmpty()){
	    	hiddenYn = true;
	    }
	    
	    
	    
	    // 파라미터 없으면: 구매 가능 목록 노출
	    if (raw.isEmpty() || raw.equals("전체")) {
	        List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);
	        String compact = renderMarketListForBuy(list, userName,hiddenYn);
	        return compact;
	    }

	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}

	    // 입력 → itemId 해석
	    Integer itemId = null;
	    if (raw.matches("\\d+")) {
	        try { itemId = Integer.valueOf(raw); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByName(raw); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByCode(raw); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + raw + NL
	             + "(/구매 입력만으로 목록을 확인하세요)";
	    }

	    // 아이템 상세 조회
	    HashMap<String, Object> item = null;
	    try {
	        item = botNewService.selectItemDetailById(itemId);
	    } catch (Exception ignore) {}
	    if (item == null || !"MARKET".equalsIgnoreCase(Objects.toString(item.get("ITEM_TYPE"), ""))) {
	        return "구매할 수 없는 아이템입니다. (MARKET 유형만 구매 가능)";
	    }

	    String itemName = Objects.toString(item.get("ITEM_NAME"), String.valueOf(itemId));

	    // 단가
	    Integer tmpPrice = null;
	    try { tmpPrice = botNewService.selectItemSellPriceById(itemId); } catch (Exception ignore) {}
	    int price = (tmpPrice == null ? 0 : tmpPrice.intValue());
	    if (price <= 0) {
	        return "구매 가격 정보가 없습니다. 관리자에게 문의해주세요.";
	    }

	    
	    boolean usedMerchantDiscount = false;
	    /*
	    if(itemId.toString().startsWith("7") || itemId.toString().startsWith("9") ) {
	    	isMerchant =false;
	    }
	    
	    if (isMerchant) {
	        price = (int)Math.floor(price * 0.9);
	        usedMerchantDiscount = true;
	    }
	    */

	    // 이미 소유 여부
	    Integer ownedCnt = botNewService.selectHasOwnedMarketItem(userName, roomName, itemId);
	    if (ownedCnt != null && ownedCnt > 0) {
	        return "⚠ 이미 보유중인 아이템입니다. [" + itemName + "] 은(는) 중복구매가 불가합니다.";
	    }

	    // 포인트 확인
	    Integer tmpPoint = null;
	    try { tmpPoint = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int curPoint = (tmpPoint == null ? 0 : tmpPoint.intValue());
	    if (curPoint < price) {
	        return userName + "님, 포인트가 부족합니다. (가격: " + price + "sp, 보유: " + curPoint + "sp)";
	    }

	    // 결제 (포인트 차감)
	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", -price);
	    pr.put("cmd", "BUY");
	    botNewService.insertPointRank(pr);

	    // 인벤토리 적재
	    HashMap<String, Object> inv = new HashMap<>();
	    inv.put("userName", userName);
	    inv.put("roomName", roomName);
	    inv.put("itemId",  itemId);
	    inv.put("qty",     1);
	    inv.put("delYn",   "0");
	    inv.put("gainType", usedMerchantDiscount ? "BUY_MERCHANT" : "BUY");
	    botNewService.insertInventoryLogTx(inv);

	    // 구매 후 포인트
	    Integer tmpAfter = null;
	    try { tmpAfter = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int afterPoint = (tmpAfter == null ? 0 : tmpAfter.intValue());

	    // 옵션 표기
	    StringBuilder sbOpt = new StringBuilder();
	    sbOpt.append(buildOptionTokensFromMap(item));

	    // 결과 메시지
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 구매 완료").append(NL)
	      .append(userName).append("님, ").append(itemName).append("을(를) 구매했습니다.").append(NL)
	      .append("↘가격: ").append(price).append("sp");
	    /*if (isMerchant) {
	        sb.append(" (상인 할인 적용)");
	    }*/
	    sb.append(NL)
	      .append("↘옵션: ").append(sbOpt).append(NL)
	      .append("현재 포인트: ").append(afterPoint).append("sp");

	    try {
	    	botNewService.closeOngoingBattleTx(userName, roomName);
	    }catch(Exception e) {
	    	
	    }
	    return sb.toString();
	}

	/** Map에서 Number → int 변환(Java 1.8) */
	private int getInt(Object o) {
	    if (o == null) return 0;
	    if (o instanceof Number) return ((Number)o).intValue();
	    try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
	}

	private String formatSp(int v) {
	    if (v < 0) v = 0;
	    return String.format("%dsp", v);  
	}

	public String monsterAttack(HashMap<String, Object> map) {
	    map.put("cmd", "monster_attack");
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    
	    if(roomName.equals("람쥐봇 문의방")) {
			if(userName.equals("일어난다람쥐/카단")) {
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}
	    
	    final String param1 = Objects.toString(map.get("param1"), "");

	    // 1) 유저 조회
	    User u = botNewService.selectUser(userName, roomName);
	    if (u == null) return guideSetTargetMessage();

	    final String job = (u.job == null ? "" : u.job.trim());

	    if(job.isEmpty()) {
	    	return userName+" 님, /직업 을 통해 먼저 전직해주세요.";
	    }
	    
	    // 2) MARKET 버프 합산 (null-safe)
	    HashMap<String, Number> buffs = null;
	    try {
	        buffs = botNewService.selectOwnedMarketBuffTotals(userName, roomName);
	    } catch (Exception ignore) {}

	    int bAtkMin  = (buffs != null && buffs.get("ATK_MIN")  != null) ? buffs.get("ATK_MIN").intValue()  : 0;
	    int bAtkMax  = (buffs != null && buffs.get("ATK_MAX")  != null) ? buffs.get("ATK_MAX").intValue()  : 0;
	    int bCri     = (buffs != null && buffs.get("ATK_CRI")  != null) ? buffs.get("ATK_CRI").intValue()  : 0;
	    int bRegen   = (buffs != null && buffs.get("HP_REGEN") != null) ? buffs.get("HP_REGEN").intValue() : 0;
	    int bHpMax   = (buffs != null && buffs.get("HP_MAX")   != null) ? buffs.get("HP_MAX").intValue()   : 0;
	    int bCriDmg  = (buffs != null && buffs.get("CRI_DMG")  != null) ? buffs.get("CRI_DMG").intValue()  : 0;

	    // 3) 직업 패시브 반영 (표시/전투 공통 기반치)
	    // 프리스트: 아이템 HP 효과 2배
	    if ("프리스트".equals(job)) {
	        bHpMax = (int) Math.round(bHpMax * 1.25);
	        bRegen = (int) Math.round(bRegen * 1.25);
	    }

	    // 4) 무기 강화
	    int weaponLv = 0;
	    try {
	        weaponLv = botService.selectWeaponLvCheck(map);
	    } catch (Exception ignore) {
	        weaponLv = 0;
	    }
	    int weaponBonus = getWeaponAtkBonus(weaponLv);
	    
	 // === 전사 보너스 기준이 되는 "순수 기본값" (아이템/강화 제외) ===
	    int baseMin = u.atkMin;
	    int baseMax = u.atkMax;
	    int baseHpMax = u.hpMax;

	 // === 아이템/강화 포함한 일반 전투용 베이스 (전사 보너스 적용 전) ===
	    int atkMinWithItem = baseMin + bAtkMin;                        // 기본 + 아이템(min)
	    int atkMaxWithItem = baseMax + weaponBonus + bAtkMax;          // 기본 + 무기강 + 아이템(max)
	    int hpMaxWithItem  = baseHpMax + bHpMax;

	    // 🩸 흡혈귀: 기초리젠 불가 
	    if ("흡혈귀".equals(job)) {
	    	bRegen = 0;
	    }
	 // ☠ 사신: 아이템으로 인한 HP / 크리 증가량 무시
	    if ("사신".equals(job)) {
	        // 크리율/크리뎀도 아이템 증가분(bCri, bCriDmg) 제거
	    	bCri = 0;
	    	bCriDmg   = 0;
	        // 체젠(effRegen)은 말 안 하셨으니 그대로 두었음
	    }
	    
	    int effCritRate = u.critRate + bCri;
	    int effRegen    = u.hpRegen + bRegen;
	    int effCriDmg   = u.critDmg + bCriDmg;
	    
	 
	    
	    
	 // 🌟 운영자의 축복: Lv 7 이하 전투 시 전용 버프 (DB에는 저장하지 않음)
	    boolean hasBless = (u.lv <= 15);
	    int blessAtk = 0;
	    int blessRegen = 0;
	    if (hasBless) {
	        //blessAtk = 3;
	        blessRegen = 5;
	        effRegen += blessRegen; // 체젠은 여기서 바로 반영
	    }
	    
	
	    
	 // === 직업별 보너스 계산 ===
	    int jobBonusMin = 0;
	    int jobBonusMax = 0;
	    int jobBonusHp  = 0;
	    double jobDmgMul = 1.0;
	    
	    
	    // 6) 궁수 배율 (최종 공격력 1.5배) → 실제 데미지 범위에 반영
	    if ("궁수".equals(job)) {
	        jobDmgMul = 1.8;
	    }else if ("전사".equals(job)) {
	        jobBonusHp  = +(int)Math.round(baseHpMax);
	    }
	 // 3) 전사 보너스(기본값 기준)를 각각 더함
	    int effAtkMin = (int) Math.round(atkMinWithItem * jobDmgMul + jobBonusMin);
	    int effAtkMax = (int) Math.round(atkMaxWithItem * jobDmgMul + jobBonusMax);
	    
	    if (effAtkMax < effAtkMin) effAtkMax = effAtkMin;

	 // === 최종 전투용 HP_MAX ===
	    int effHpMax = hpMaxWithItem + jobBonusHp;
	    
	    // -----------------------------
	    // 7) 부활 처리만 (리젠 X)
	    // -----------------------------
	    String reviveMsg = reviveAfter1hIfDead(userName, roomName, u, effHpMax, effRegen);
	    boolean revivedThisTurn = false;
	    if (reviveMsg != null) {
	        if (!reviveMsg.isEmpty()) return reviveMsg;
	        revivedThisTurn = true;
	    }


	    // 8) 진행중 전투 / 신규 전투 + LUCKY 유지
	    OngoingBattle ob = botNewService.selectOngoingBattle(userName, roomName);
	    Monster m;
	    int monMaxHp, monHpRemainBefore;
	    boolean lucky;
	    if (ob != null) {
	        m = botNewService.selectMonsterByNo(ob.monNo);
	        if (m == null) return "진행중 몬스터 정보를 찾을 수 없습니다.";
	        monMaxHp = m.monHp;
	        monHpRemainBefore = Math.max(0, m.monHp - ob.totalDealtDmg);
	        lucky = (ob.luckyYn != null && ob.luckyYn == 1);
	    } else {
	        m = botNewService.selectMonsterByNo(u.targetMon);
	        if (m == null) return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";
	        monMaxHp = m.monHp;
	        monHpRemainBefore = m.monHp;
	        
	        if ("도사".equals(job)) {
	        	lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE_DOSA;
	        }else if("사신".equals(job)){
	        	lucky = false;
	        }else {
	        	lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE;
	        }
	        
	    }

	    // 9) 쿨타임 체크 (궁수 5분 반영)
	    CooldownCheck cd = checkCooldown(userName, roomName, param1, job);
	    if (!cd.ok) {
	        long min = cd.remainSeconds / 60;
	        long sec = cd.remainSeconds % 60;
	        return String.format("%s님, 공격 쿨타임 %d분 %d초 남았습니다.", userName, min, sec);
	    }
	    

	    int effectiveHp = revivedThisTurn
	            ? u.hpCur
	            : computeEffectiveHpFromLastAttack(userName, roomName, u, effHpMax, effRegen);
	    u.hpCur = effectiveHp;
	    
	    
	    // 🔹 글로벌(서버 전체) 기준 ACHV 카운트
	    List<AchievementCount> globalList = botNewService.selectAchvCountsGlobalAll();
	    Map<String, Integer> globalAchvMap = new HashMap<>();
	    if (globalList != null) {
	        for (AchievementCount ac : globalList) {
	            if (ac == null || ac.getCmd() == null) continue;
	            globalAchvMap.put(ac.getCmd(), ac.getCnt());
	        }
	    }

	    // 🔹 현재 유저(방 기준) ACHV 카운트
	    List<AchievementCount> userList = botNewService.selectAchvCountsGlobal(userName, roomName);
	    Map<String, Integer> userAchvMap = new HashMap<>();
	    if (userList != null) {
	        for (AchievementCount ac : userList) {
	            if (ac == null || ac.getCmd() == null) continue;
	            userAchvMap.put(ac.getCmd(), ac.getCnt());
	        }
	    }
	    
	    double berserkMul = 1.0;
	    if ("전사".equals(job) && effHpMax > 0 && m.monLv >= u.lv) {
	        double hpRatio = (double) u.hpCur / effHpMax;
	        if (hpRatio < 0.5) {
	            berserkMul = 1.0 + (0.5 - hpRatio) * 2.0; // 0% ~ +100%
	        }
	    }
	    
	    if ("사신".equals(job)) {
	        String firstCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get(firstCmd);
	            if (v != null) globalCnt = v.intValue();
	        }

	        if (globalCnt == 0) {
	            return "사신은 최초 토벌에 도전불가!";
	        }
	        
	        if(u.lv < m.monLv) {
	        	return "사신은 몬스터레벨보다 높아야 공격할 수 있음!";
	        }
	        
	    }
	    
	    Flags flags = new Flags();
		flags = rollFlags(u, m);
	 // 🖤 사신: 체력 10% 이하 → 치명타 +50%
	    if ("사신".equals(job)) {
	        int tenPercent = (int)Math.ceil(effHpMax * 0.1);
	        if (u.hpCur <= tenPercent) {
	            effCritRate += 50;
	        }
	    }

	 // 10) HP 20% 제한 체크 (사신은 무시)
	    if (!"사신".equals(job)) {
	        int origHpMax = u.hpMax;
	        int origRegen = u.hpRegen;

	        u.hpMax = effHpMax;
	        u.hpRegen = effRegen;

	        try {
	            String hpMsg = buildBelowHalfMsg(userName, roomName, u, param1);
	            if (hpMsg != null) return hpMsg;
	        } finally {
	            u.hpMax = origHpMax;
	            u.hpRegen = origRegen;
	        }
	    }


	    //11) 데미지 굴림 (도사/방 버프 적용 전: crit 계산은 아래로 이동)
	    DosaBuffEffect buffEff = loadRoomDosaBuffAndBuild(roomName);
	    String dosabuffMsg = "";

	    if ("도사".equals(job)) {
	    	DosaBuffEffect buffEff_self = buildDosaBuffEffect(u, u.lv, roomName);
	    	effAtkMin   += buffEff_self.addAtkMin;
	        effAtkMax   += buffEff_self.addAtkMax;
	        effCritRate += buffEff_self.addCritRate;
	        effCriDmg   += buffEff_self.addCritDmg;
	        u.hpCur     += buffEff_self.addHp;   // HP 상한 무시 회복

	        dosabuffMsg += buffEff_self.msg+NL;
	    }
	    
	    if (buffEff != null) {
	        effAtkMin   += buffEff.addAtkMin;
	        effAtkMax   += buffEff.addAtkMax;
	        effCritRate += buffEff.addCritRate;
	        effCriDmg   += buffEff.addCritDmg;
	        u.hpCur     += buffEff.addHp;   // HP 상한 무시 회복

	        dosabuffMsg += buffEff.msg;

	        // 1회 소모 → 방내 BUFF_YN 전부 초기화
	        botNewService.clearRoomBuff(roomName);
	    }
	    
	    /** TODO /
	     * 
	     */
	    
	    
	    // 🔥 A형 완전 분리: 데미지 전부 calculateDamage로 처리
	    DamageOutcome dmg = calculateDamage(
	            u,
	            m,
	            job,
	            flags,
	            effAtkMin,
	            effAtkMax,
	            effCritRate,
	            effCriDmg,
	            berserkMul,
	            monHpRemainBefore,
	            effHpMax
	    );

	    AttackCalc calc = dmg.calc;
	    flags = dmg.flags;          // (필요하면) 갱신된 플래그 다시 반영
	    boolean willKill = dmg.willKill;
		
		 // 13) 즉사 처리
		 int newHpPreview = Math.max(0, u.hpCur - calc.monDmg);
		 
		 if ("사신".equals(job) && newHpPreview <= 0 && flags.monPattern != 5) {
		     // HP는 1 남기고 버틴다고 가정
		     newHpPreview = 1;
		     // 실제로는 1만 남도록 몬스터 피해 조정
		     calc.monDmg = Math.max(0, u.hpCur - newHpPreview);
		     calc.jobSkillUsed = true;  
		     // 이 턴 공격은 실패 처리 (데미지 0)
		     if (flags.monPattern == 4) {
		    	 calc.atkDmg = 0;
		    	 String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		         calc.patternMsg = baseMsg + NL+"죽음을 거부하고, 필살기를 버텨냅니다";
		     } else if(flags.monPattern == 2) {
		    	 String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		         calc.patternMsg = baseMsg + NL+"죽음을 거부하고, 반격합니다";
		     } 
		 }
		 
		 String deathAchvMsg = "";
		 if (!"사신".equals(job) && newHpPreview <= 0) {
			 if (newHpPreview <= 0) {
			     botNewService.closeOngoingBattleTx(userName, roomName);
			     botNewService.updateUserHpOnlyTx(userName, roomName, 0);
			     botNewService.insertBattleLogTx(new BattleLog()
			             .setUserName(userName)
			             .setRoomName(roomName)
			             .setLv(u.lv)
			             .setTargetMonLv(m.monNo)
			             .setGainExp(0)
			             .setAtkDmg(calc.atkDmg)
			             .setMonDmg(calc.monDmg)
			             .setAtkCritYn(flags.atkCrit ? 1 : 0)
			             .setMonPatten(flags.monPattern)
			             .setKillYn(0)
			             .setNowYn(0)
			             .setDropYn(0)
			             .setDeathYn(1)
			             .setLuckyYn(0)
			             .setJobSkillYn(0)
			             .setJob(job)
			     );
			     
			     deathAchvMsg = grantDeathAchievements(userName, roomName);
			     
			     
			     return userName + "님, 이번전투에서 패배하여, 전투 불능이 되었습니다." + NL
			             + "현재 체력: 0 / " + effHpMax + NL
			             + "10분 뒤 최대 체력의 10%로 부활하며," + NL
			             + "이후 5분마다 HP_REGEN 만큼 서서히 회복됩니다."+NL+ deathAchvMsg;
			 }
		 }
		 

	    // 14) 처치/드랍 판단
	    //boolean willKill = calc.atkDmg >= monHpRemainBefore;
	    Resolve res = resolveKillAndDrop(m, calc, willKill, u, lucky);
	    
	    // 🔹 궁수: 획득 EXP +15%
	    if ("궁수".equals(u.job)) {
	        int baseExp = res.gainExp;
	        int bonus = (int) Math.floor(res.gainExp * 0.15);
	        res.gainExp = baseExp + bonus;
	    }
	    
	    String stealMsg = null;
	    if ("도적".equals(job)) {
	    	double stealRate = 0.25;
	    	int monLv  = m.monNo;
		    switch(monLv) {
		    	case 15:
		    		stealRate -=0.03;
		    	case 14:
		    		stealRate -=0.03;
		    	case 13:
		    		stealRate -=0.03;
		    	case 12: 
		    		stealRate -=0.03;
		    }
		    
	    	
	        if (ThreadLocalRandom.current().nextDouble() < stealRate) {
	            String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	            if (!dropName.isEmpty()) {
	                try {
	                    Integer itemId = botNewService.selectItemIdByName(dropName);
	                    if (itemId != null) {
	                        HashMap<String,Object> inv = new HashMap<>();
	                        inv.put("userName", userName);
	                        inv.put("roomName", roomName);
	                        inv.put("itemId", itemId);
	                        inv.put("qty", 1);
	                        inv.put("delYn", "0");
	                        inv.put("gainType", "STEAL");
	                        botNewService.insertInventoryLogTx(inv);
	                        // 메시지는 buildAttackMessage에서 드랍 파트와 함께 표현 가능 (원하면 추가)
	                        stealMsg = "✨ " + m.monName + "의 아이템을 훔쳤습니다! (" + dropName + "조각)";
	                        calc.jobSkillUsed = true;  
	                    }
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    String dosaCastMsg = null;
	    if ("도사".equals(job)) {
	        dosaCastMsg = "✨ 도사의 기원! 다음 공격자 강화!"+NL;
	    }
	    
	    // 15) DB 반영 + 로그
	    LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res, effHpMax);
	    String bonusMsg = "";
	    String blessMsg = "";
	    
	    
	    // 🔹 운영자의 축복 레벨 구간 보너스:2,3,4, 5, 6, 7레벨 달성 시 각각 200sp (1회 지급)
	    blessMsg = grantBlessLevelBonus(userName, roomName, up.beforeLv, up.afterLv);
	    
	    
	    if (res.killed) {
	        // 진행중 전투 종료
	        botNewService.closeOngoingBattleTx(userName, roomName);

	        // ✅ 최초토벌 보상 (글로벌 1회 or 룸 기준: selectPointRankCountByCmdGlobal 구현에 따름)
	        String firstClearMsg = grantFirstClearIfEligible(userName, roomName, m,globalAchvMap);

	     
	        // ✅ 킬수 업적 (몬스터별/통산)
	        String killAchvMsg = grantKillAchievements(userName, roomName);

	        if ((firstClearMsg != null && !firstClearMsg.isEmpty())
	         || (killAchvMsg != null && !killAchvMsg.isEmpty())) {
	            bonusMsg = NL + firstClearMsg + killAchvMsg;
	        }
	        
	    }

	    // 17) 메시지 구성 (표시용 ATK 범위에 직업 효과 반영)
	    int shownMin = effAtkMin;
	    int shownMax = effAtkMax;

	    String msg = buildAttackMessage(
	            userName, u, m, flags, calc, res, up,
	            monHpRemainBefore, monMaxHp,
	            shownMin, shownMax,
	            weaponLv, weaponBonus,
	            effHpMax
	    );
	    
	    if (dosabuffMsg != null) {
	        msg += NL + dosabuffMsg;
	    }
	    if (dosaCastMsg != null) {
	        msg += NL + dosaCastMsg;
	    }
	    
	    if (stealMsg != null) {
	        msg += NL + stealMsg;
	    }
	    
	    
	    
	    // ✅ 최초토벌/업적 메시지 추가
	    if (!bonusMsg.isEmpty()) {
	        msg += bonusMsg;
	    }
	    // ✅ 최초토벌/업적 메시지 추가
	    if (!blessMsg.isEmpty()) {
	    	msg += blessMsg;
	    }
	    
	    String celebrationMsg = grantCelebrationClearBonus(userName, roomName, globalAchvMap, userAchvMap);
        if(celebrationMsg !=null && !celebrationMsg.isEmpty()) {
        	msg +=NL+celebrationMsg; 
        }
        
	    // 18) 현재 포인트 조회
	    int curPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (p == null ? 0 : p.intValue());
	    } catch (Exception ignore) {}
	    String curSpStr = formatSp(curPoint);

	    msg = msg + NL + "현재 포인트: " + curSpStr + NL;

	    // 🌟 운영자의 축복 안내 (실제 반영된 수치 기준)
	    if (hasBless) {
	        msg += NL + "※ 운영자의 축복 적용 중: 5분당 회복 +" + blessRegen
	             + " (Lv 15 이하 한정 버프)";
	    }
	    
	    // 19) 전직 안내 (전직 안 했고 5레벨 이상일 때만)
	    if ((job.isEmpty()) && u.lv >= 1) {
	        msg += NL + "※ 아직 전직하지 않았습니다. /직업 으로 확인해주세요!";
	    }

	    try {
			botNewService.execSPMsgTest(map);
			
			msg+=NL+""+map.get("outMsg");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    return msg;
	}

	private boolean isSkeleton(Monster m) {
	    if (m == null) return false;
	    if (m.monNo == 10) return true;
	    if (m.monNo == 14) return true;
	    if (m.monName.equals("해골")) {
	    	return true;
	    }
	    if (m.monName.equals("리치")) {
	    	return true;
	    }
	    return false;
	}

	public String sellItem(HashMap<String, Object> map) {
	    final int SHINY_MULTIPLIER = 5; // ✨ 빛템 5배

	    final String userName = Objects.toString(map.get("userName"), "");
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    
	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}
	    
	    final String itemNameRaw = Objects.toString(map.get("param1"), "").trim();
	    final int reqQty = Math.max(1, parseIntSafe(Objects.toString(map.get("param2"), "1")));

	    if (userName.isEmpty() || roomName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
	    if (itemNameRaw.isEmpty()) {
	    	return "판매할 아이템명을 입력해주세요."+NL+" 예) /판매 도토리 5 또는 /판매 빛도토리 2"
	         +NL+"/판매 기타 ->잡템전체"+NL+"/판매 장비 ->장비전체";
	    }

	    User u = botNewService.selectUser(userName, roomName);
	    //String job = (u == null || u.job == null) ? "" : u.job.trim();
	    //boolean isMerchant = true;

	 // 🔥 여기부터 추가: param1 으로 전체판매 모드 제어
	    if ("기타".equals(itemNameRaw)) {
	        return sellAllByCategory(userName, roomName, u, false); // 잡템 전체판매
	    }
	    if ("장비".equals(itemNameRaw)) {
	        return sellAllByCategory(userName, roomName, u, true);  // 장비 전체판매
	    }
	    
	    final boolean wantShinyOnly = itemNameRaw.startsWith("빛") || itemNameRaw.startsWith("✨");
	    final boolean stealOnly = itemNameRaw.endsWith("조각");
	    
	    String baseName = itemNameRaw;
	    baseName = baseName.replace("빛", "").replace("✨", "");
	    if (stealOnly && baseName.endsWith("조각")) {
	        baseName = baseName.substring(0, baseName.length() - 2); // "조각" 두 글자 제거
	    }
	    

	    Integer itemId = null;
	    try { itemId = botNewService.selectItemIdByName(baseName); } catch (Exception ignore) {}
	    if (itemId == null) return "해당 아이템을 찾을 수 없습니다: " + itemNameRaw;

	    List<HashMap<String, Object>> rows = botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	    if (rows == null || rows.isEmpty()) return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";

	    // ★ 조각 수량 추가
	    int normalQty = 0, shinyQty = 0, fragQty = 0;
	    for (HashMap<String, Object> row : rows) {
	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        qty = Math.max(0, qty);

	        if ("STEAL".equalsIgnoreCase(gainType)) {
	            fragQty += qty;
	        } else if ("DROP3".equalsIgnoreCase(gainType)) {
	            shinyQty += qty;
	        } else {
	            normalQty += qty;
	        }
	    }

	 // ★ 판매 대상 수량 계산: 조각 모드 vs 일반 모드
	    int haveTotal;
	    if (stealOnly) {
	        haveTotal = fragQty;
	    } else {
	        haveTotal = normalQty + shinyQty;
	    }

	    if (haveTotal <= 0) {
	        return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";
	    }

	    Integer basePriceObj = null;
	    try { basePriceObj = botNewService.selectItemSellPriceById(itemId); } catch (Exception ignore) {}
	    int basePrice = (basePriceObj == null ? 0 : basePriceObj);
	    if (basePrice <= 0) return "해당 아이템은 판매가 설정이 없어 판매할 수 없습니다: " + itemNameRaw;

	 // ✅ 아이템 정보 조회 (장비 여부 확인)
	    HashMap<String, Object> itemDetail = null;
	    try {
	        itemDetail = botNewService.selectItemDetailById(itemId);
	    } catch (Exception ignore) {}
	    String itemType = (itemDetail == null) ? "" : Objects.toString(itemDetail.get("ITEM_TYPE"), "");
	    boolean isEquip = "MARKET".equalsIgnoreCase(itemType);
	    // ✅ 상인은 장비(MARKET) 아이템 판매 불가
	    //if (isMerchant && isEquip) {
	    //    return "상인 직업은 장비 아이템(MARKET)을 판매할 수 없습니다.";
	    //}
	    
	    int need = Math.min(reqQty, haveTotal);
	    int sold = 0, soldNormal = 0, soldShiny = 0, soldFrag = 0;
	    long totalSp = 0L;
	    
	    
	    boolean soldMerchantDiscount = false; // BUY_MERCHANT 물건을 실제로 판 적 있는지
	    //boolean soldMerchantBonus = false;    // 상인 보너스(드랍템 10%↑) 적용된 판매가 있었는지
	    
	    for (HashMap<String, Object> row : rows) {
	        if (need <= 0) break;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDropRow  = isShinyRow || "DROP".equalsIgnoreCase(gainType);
	        boolean isMerchantBuy  = "BUY_MERCHANT".equalsIgnoreCase(gainType);
	        boolean isStealRow   = "STEAL".equalsIgnoreCase(gainType);   // ★ 추가
	        
	     // ★ 모드에 따라 행 필터링
	        if (stealOnly && !isStealRow) continue;      // /판매 모피조각 → STEAL만
	        if (!stealOnly && isStealRow) continue;      // /판매 모피 → STEAL 제외

	        // 기존 빛/일반 필터
	        if (wantShinyOnly && !isShinyRow) continue;
	        if (!wantShinyOnly && isShinyRow) continue;
	        
	        

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (rid == null || qty <= 0) continue;

	        int take = Math.min(qty, need);
	        if (take <= 0) continue;

	        int unitPrice;

	        if (isShinyRow) {
	            // ✨빛드랍 기본 5배
	            unitPrice = basePrice * SHINY_MULTIPLIER;
	        } else {
	            // 기본은 아이템 판매가
	            unitPrice = basePrice;
	        }

	        // ✅ 상인 할인으로 산 아이템(BUY_MERCHANT)은 언제 팔든 '구매 당시 가격'으로만
	        if (isMerchantBuy) {
	            unitPrice = (int)Math.floor(basePrice * 0.9);
	        }

	        // ✅ 상인 직업 보너스는 DROP/DROP3 에만 적용 (BUY_MERCHANT에는 미적용)
	        //if (isMerchant && isDropRow) {
	        //    unitPrice = (int)Math.round(unitPrice * 1.1);
	        //}
	        
	        // ★ 조각(STEAL)은 절반 가격
	        if (isStealRow) {
	            unitPrice = (int)Math.floor(unitPrice * 0.5);
	        }

	     // 👇 실제로 해당 타입이 팔렸는지 기록
	        if (isMerchantBuy && take > 0) {
	            soldMerchantDiscount = true;
	        }
	        /*
	        if (isMerchant && isDropRow && !isMerchantBuy && take > 0) {
	            soldMerchantBonus = true;
	        }
	        */

	        if (qty == take) botNewService.updateInventoryDelByRowId(rid);
	        else botNewService.updateInventoryQtyByRowId(rid, qty - take);

	     // 판매 카운트
	        if (isStealRow) {
	            soldFrag += take;
	        } else if (isShinyRow) {
	            soldShiny += take;
	        } else {
	            soldNormal += take;
	        }
	        sold += take;
	        need -= take;
	        totalSp += (long) take * (long) unitPrice;
	    }

	    if (sold <= 0) {
	        // ★ 보유 안내도 모드별 분리
	        String preStock;
	        if (stealOnly) {
	            preStock = "보유: " + baseName + "조각 " + fragQty + "개";
	        } else {
	            preStock = "보유: " + baseName + " " + normalQty + "개"
	                    + (shinyQty > 0 ? ", ✨빛" + baseName + " " + shinyQty + "개" : "")
	                    + (fragQty  > 0 ? ", " + baseName + "조각 " + fragQty + "개" : "");
	        }
	        return "판매 가능한 재고가 없습니다." + NL + preStock;
	    }

	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", (int) totalSp);
	    if (isEquip) {
	        pr.put("cmd", "SELL_EQUIP");  // 장비 판매
	    } else {
	        pr.put("cmd", "SELL_JUNK");   // 잡템 판매
	    }
	    //pr.put("cmd", "SELL");
	    botNewService.insertPointRank(pr);

	    int curPoint = 0;
	    try {
	        Integer curP = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (curP == null ? 0 : Math.max(0, curP));
	    } catch (Exception ignore) {}
	    String curPointStr = String.format("%,d sp", curPoint);

	    int remainNormal = Math.max(0, normalQty - soldNormal);
	    int remainShiny  = Math.max(0, shinyQty  - soldShiny);
	    int remainFrag   = Math.max(0, fragQty   - soldFrag);  // ★
	    

	    StringBuilder remainSb = new StringBuilder("남은 재고: ");
	    boolean printed = false;
	    
        if (remainNormal > 0) {
            remainSb.append(baseName).append(" ").append(remainNormal).append("개");
            printed = true;
        }
        if (remainShiny > 0) {
            if (printed) remainSb.append(", ");
            remainSb.append("✨빛").append(baseName).append(" ").append(remainShiny).append("개");
            printed = true;
        }
     // ★ 여기 추가: 조각도 같이 보여주기
        if (remainFrag > 0) {
            if (printed) remainSb.append(", ");
            remainSb.append(baseName).append("조각 ").append(remainFrag).append("개");
            printed = true;
        }
	    
	    if (!printed) remainSb = new StringBuilder("남은 재고: 없음");

	 // 표시용 이름
	    String dispName;
	    if (stealOnly) {
	        dispName = baseName + "조각";                         // ★ /판매 모피조각
	    } else {
	        dispName = wantShinyOnly ? ("✨빛" + baseName) : baseName;
	    }
	    
	    StringBuilder sb = new StringBuilder();
	    sb.append("⚔ ").append(userName).append("님,").append(NL)
	      .append("▶ 판매 완료!").append(NL)
	      .append("- 아이템: ").append(dispName).append(NL)
	      .append("- 판매 수량: ").append(sold).append("개").append(NL)
	      .append("- 합계 적립: ").append(totalSp).append("sp").append(NL)
	      .append("- 현재 포인트: ").append(curPointStr).append(NL)
	      .append(remainSb.toString());


		 // 👇 여기 추가
		 if (soldMerchantDiscount) {
		     sb.append(NL)
		       .append("※ 상인 할인으로 구매한 아이템은 할인가(90%) 기준으로 판매되었습니다.");
		 }
		 /*
		 if (soldMerchantBonus) {
		     sb.append(NL)
		       .append("(상인 효과: 드랍 아이템 판매가 10% 보너스 적용)");
		 }*/
		 
	    if (sold < reqQty) {
	        sb.append(NL)
	          .append("(요청 ").append(reqQty).append("개 → 실제 ").append(sold).append("개 판매)");
	    }

	    return sb.toString();
	}

private String sellAllByCategory(String userName, String roomName, User u, boolean equipOnly) {
	    final int SHINY_MULTIPLIER = 5; // ✨ 빛템 5배
	    final String NL = BossAttackController.NL; // 클래스 상단 static final NL = "♬" 사용

	    //String job = (u == null || u.job == null) ? "" : u.job.trim();
	    //boolean isMerchant = "상인".equals(job);

	    // 상인은 장비 전체판매 불가 (기존 장비 판매 금지 룰 유지)/
	    /*
	    if (equipOnly && isMerchant) {
	        return "상인 직업은 장비 아이템(MARKET)을 일괄 판매할 수 없습니다. 직업을 변경 후 다시 시도해주세요.";
	    }
	     */
	    // 인벤토리 전체 판매 대상 조회 (ROWID, QTY, GAIN_TYPE만)
	    List<HashMap<String, Object>> rows = botNewService.selectAllInventoryRowsForSale(userName, roomName);
	    if (rows == null || rows.isEmpty()) {
	        return equipOnly ? "판매 가능한 장비가 없습니다."
	                         : "판매 가능한 잡템이 없습니다.";
	    }

	    // 캐시: ITEM_ID → 장비 여부 / 판매가
	    Map<Integer, Boolean> equipCache = new HashMap<>();
	    Map<Integer, Integer> priceCache = new HashMap<>();

	    int sold = 0, soldNormal = 0, soldShiny = 0, soldFrag = 0;
	    long totalSp = 0L;
	    boolean soldMerchantDiscount = false; // BUY_MERCHANT 판매 여부
	    //boolean soldMerchantBonus    = false; // 상인 10% 보너스 적용 여부

	    for (HashMap<String, Object> row : rows) {

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        if (rid == null) continue;

	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (qty <= 0) continue;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow    = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDropRow     = isShinyRow || "DROP".equalsIgnoreCase(gainType);
	        boolean isMerchantBuy = "BUY_MERCHANT".equalsIgnoreCase(gainType);
	        boolean isStealRow    = "STEAL".equalsIgnoreCase(gainType);

	        // 1) ROWID → ITEM_ID 조회 (ITEM_ID 기준 로직을 쓰기 위함)
	        Integer itemId = null;
	        try {
	            itemId = botNewService.selectItemIdByRowId(rid);
	        } catch (Exception ignore) {}
	        if (itemId == null || itemId <= 0) {
	            continue; // 아이템 정보 없으면 스킵
	        }

	        // 2) ITEM_ID → 장비 여부(ITEM_TYPE = MARKET) 캐시
	        Boolean isEquipObj = equipCache.get(itemId);
	        if (isEquipObj == null) {
	            HashMap<String, Object> itemDetail = null;
	            try {
	                itemDetail = botNewService.selectItemDetailById(itemId);
	            } catch (Exception ignore) {}
	            String itemType = (itemDetail == null) ? "" : Objects.toString(itemDetail.get("ITEM_TYPE"), "");
	            isEquipObj = "MARKET".equalsIgnoreCase(itemType);
	            equipCache.put(itemId, isEquipObj);
	        }
	        boolean isEquip = Boolean.TRUE.equals(isEquipObj);

	        // 3) 모드에 따라 필터링
	        if (equipOnly && !isEquip) continue;   // 장비 전체판매 → 장비(MARKET)만
	        if (!equipOnly && isEquip) continue;   // 잡템 전체판매 → 장비 제외

	        // 4) ITEM_ID → 기본 판매가 캐시
	        Integer basePriceObj = priceCache.get(itemId);
	        if (basePriceObj == null) {
	            Integer tmpPrice = null;
	            try { tmpPrice = botNewService.selectItemSellPriceById(itemId); } catch (Exception ignore) {}
	            basePriceObj = (tmpPrice == null ? 0 : tmpPrice.intValue());
	            priceCache.put(itemId, basePriceObj);
	        }
	        int basePrice = basePriceObj;
	        if (basePrice <= 0) {
	            // 가격 정보 없는 아이템은 판매 불가
	            continue;
	        }

	        // 5) gainType + 직업에 따른 실제 단가 계산
	        int unitPrice = basePrice;

	        // 빛드랍 5배
	        if (isShinyRow) {
	            unitPrice = basePrice * SHINY_MULTIPLIER;
	        }

	        // 상인 할인으로 구매한 아이템은 구매 당시 가격(90%) 기준(기존 sellItem 룰과 동일)
	        if (isMerchantBuy) {
	            unitPrice = (int) Math.floor(basePrice * 0.9);
	        }

	        // 상인 직업 보너스: DROP/DROP3 드랍템은 10% 보너스 (단, 상인할인구매는 보너스 X)/
	        /*
	        if (isMerchant && isDropRow && !isMerchantBuy) {
	            unitPrice = (int) Math.round(unitPrice * 1.1);
	        }
	         */
	        // 조각(STEAL)은 절반 가격
	        if (isStealRow) {
	            unitPrice = (int) Math.floor(unitPrice * 0.5);
	        }

	        // 통계 플래그
	        if (isMerchantBuy && qty > 0) {
	            soldMerchantDiscount = true;
	        }
	        /*
	        if (isMerchant && isDropRow && !isMerchantBuy && qty > 0) {
	            soldMerchantBonus = true;
	        }*/

	        // 6) 실제 판매: 전체판매이므로 가진 수량(qty) 전부 판매
	        int take = qty;

	        // 인벤토리에서 행 삭제 (전량 판매)
	        botNewService.updateInventoryDelByRowId(rid);

	        // 카운트/합계 누적
	        if (isStealRow) {
	            soldFrag += take;
	        } else if (isShinyRow) {
	            soldShiny += take;
	        } else {
	            soldNormal += take;
	        }

	        sold += take;
	        totalSp += (long) take * (long) unitPrice;
	    }

	    if (sold <= 0) {
	        return equipOnly ? "판매 가능한 장비가 없습니다."
	                         : "판매 가능한 잡템이 없습니다.";
	    }

	    // 포인트 적립 (기존 sellItem 과 동일 패턴)
	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", (int) totalSp);
	    //pr.put("cmd", "SELL");
	    if (equipOnly) {
	        pr.put("cmd", "SELL_EQUIP");  // 장비 판매
	    } else {
	        pr.put("cmd", "SELL_JUNK");   // 잡템 판매
	    }
	    botNewService.insertPointRank(pr);

	    int curPoint = 0;
	    try {
	        Integer curP = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (curP == null ? 0 : Math.max(0, curP));
	    } catch (Exception ignore) {}
	    String curPointStr = String.format("%,d sp", curPoint);

	    StringBuilder sb = new StringBuilder();
	    sb.append("⚔ ").append(userName).append("님,").append(NL)
	      .append("▶ 전체 판매 완료!").append(NL)
	      .append(equipOnly ? "- 대상: 장비 아이템 전체(MARKET)" + NL
	                        : "- 대상: 잡템 전체(장비 제외)" + NL)
	      .append("- 총 판매 수량: ").append(sold).append("개").append(NL)
	      .append("- 합계 적립: ").append(totalSp).append("sp").append(NL)
	      .append("- 현재 포인트: ").append(curPointStr);

	    if (soldNormal > 0) sb.append(NL).append("  · 일반 아이템: ").append(soldNormal).append("개");
	    if (soldShiny  > 0) sb.append(NL).append("  · ✨빛 아이템: ").append(soldShiny).append("개");
	    if (soldFrag   > 0) sb.append(NL).append("  · 조각: ").append(soldFrag).append("개");

	    if (soldMerchantDiscount) {
	        sb.append(NL)
	          .append("※ 상인 할인으로 구매한 아이템은 할인가(90%) 기준으로 판매되었습니다.");
	    }
	    /*
	    if (soldMerchantBonus) {
	        sb.append(NL)
	          .append("(상인 효과: 드랍 아이템 판매가 10% 보너스 적용)");
	    }*/

	    return sb.toString();
	}
	
	
	/** 공격 랭킹 출력 (떠오르는샛별 / Top3 / 몬스터 학살자 / 최초토벌 + 도전중) */
	public String showAttackRanking(HashMap<String,Object> map) {
	    final String NL = "♬";
	    final String allSeeStr = "===";

	    StringBuilder sb = new StringBuilder();

	    /* === 떠오르는샛별 (최근 6시간 공격횟수 TOP5) === */
	    List<HashMap<String,Object>> rising = botNewService.selectRisingStarsTop5Last6h();
	    sb.append("✨ 떠오르는샛별").append(NL);
	    if (rising == null || rising.isEmpty()) {
	        sb.append("- 데이터 없음").append(NL);
	    } else {
	        int rank = 1;
	        for (HashMap<String,Object> row : rising) {
	            String name = String.valueOf(row.get("USER_NAME"));
	            String job = String.valueOf(row.get("JOB"));
	            // 필요시 방 이름, 공격 횟수도 붙일 수 있음 (ex. " (12회)")
	            sb.append(rank).append("위 ").append(name);
	            
	            if(!"".equals(job)) {
	            	sb.append("(").append(job).append(")");
	            }
	            
	            sb.append(NL);
	            if (rank++ >= 7) break;
	        }
	    }
	    
	    List<HashMap<String,Object>> ongoing = botNewService.selectOngoingChallengesForUnclearedBosses();
	    if (ongoing != null && !ongoing.isEmpty()) {
	    	sb.append(NL);
	        sb.append(NL).append("⚔ 최초토벌 도전중").append(NL);
	        for (HashMap<String,Object> row : ongoing) {
	            String monName   = String.valueOf(row.get("MON_NAME"));
	            String userName2 = String.valueOf(row.get("USER_NAME"));
	            String job       = Objects.toString(row.get("JOB"), "");
	            int lv           = safeInt(row.get("LV"));
	            String startTime = String.valueOf(row.get("START_TIME"));
	            int monHp        = safeInt(row.get("MON_HP"));
	            int remainHp     = safeInt(row.get("REMAIN_HP"));
	            
	            sb.append(" ").append(monName)
	              .append(" ").append(remainHp).append(" / ").append(monHp).append(NL)
	              .append(" ▶[도전 중] ").append(userName2);
	            if (!job.isEmpty()) sb.append("/").append(job);
	            sb.append("(Lv.").append(lv).append(")")
	              .append(" (").append(startTime).append(")")
	              .append(NL);
	        }
	    }
	    
	    sb.append(allSeeStr);
	    
	    // =========================
	    // 도적왕 (스틸 아이템 수)
	    // =========================
	    try {
	        List<HashMap<String, Object>> thiefRank = botNewService.selectThiefKingRanking();
	        sb.append(NL).append("◆ 도적왕 (스틸 아이템 수 TOP5)").append(NL);
	        if (thiefRank == null || thiefRank.isEmpty()) {
	            sb.append("- 데이터가 없습니다.").append(NL);
	        } else {
	            int rank = 1;
	            for (HashMap<String, Object> row : thiefRank) {
	                String userName = Objects.toString(row.get("USER_NAME"), "-");
	                int stealQty = parseIntSafe(Objects.toString(row.get("STEAL_QTY"), "0"));
	                sb.append(rank).append("위 ").append(userName)
	                  .append(" - 스틸 ").append(stealQty).append("회").append(NL);
	                rank++;
	            }
	        }
	    } catch (Exception ignore) {}
	    
	    // =========================
	    // 업적 갯수 랭킹
	    // =========================
	    try {
	        List<HashMap<String, Object>> achvRank = botNewService.selectAchievementCountRanking();
	        sb.append(NL).append("◆ 업적 갯수 랭킹 (TOP5)").append(NL);
	        if (achvRank == null || achvRank.isEmpty()) {
	            sb.append("- 데이터가 없습니다.").append(NL);
	        } else {
	            int rank = 1;
	            for (HashMap<String, Object> row : achvRank) {
	                String userName = Objects.toString(row.get("USER_NAME"), "-");
	                int cnt = parseIntSafe(Objects.toString(row.get("ACHV_CNT"), "0"));
	                sb.append(rank).append("위 ").append(userName)
	                  .append(" - 업적 ").append(cnt).append("개").append(NL);
	                rank++;
	            }
	        }
	    } catch (Exception ignore) {}

	    
	    
	    /* === ⚔ 공격 랭킹 (기존 Top3) === */
	    sb.append(NL).append("⚔ 공격 레벨 랭킹").append(NL);
	    List<HashMap<String,Object>> top3 = botNewService.selectTopLevelUsers();
	    if (top3 == null || top3.isEmpty()) {
	        sb.append("데이터 없음").append(NL);
	    } else {
	        int rank = 1;
	        for (HashMap<String,Object> row : top3) {
	            String name    = String.valueOf(row.get("USER_NAME"));
	            int lv         = safeInt(row.get("LV"));
	            int expCur     = safeInt(row.get("EXP_CUR"));
	            int expNext    = safeInt(row.get("EXP_NEXT"));
	            String job	   = String.valueOf(row.get("JOB"));

	            sb.append(rank).append("위 ")
	              .append("▶ Lv.").append(lv)
	              .append(", EXP ").append(expCur).append("/").append(expNext).append(" ")
	              .append(name).append("(").append(job).append(")")
	              .append(NL);
	            rank++;
	            if (rank > 7) break;
	        }
	    }
	    sb.append(NL);

	    /* === ⚔ 몬스터 학살자 (기존) === */
	    sb.append("⚔ 몬스터 학살자").append(NL);
	    List<HashMap<String,Object>> killers = botNewService.selectKillLeadersByMonster();
	    if (killers == null || killers.isEmpty()) {
	        sb.append("데이터 없음").append(NL);
	    } else {
	        Integer lastMonNo = null;
	        String  lastMonName = null;
	        for (HashMap<String,Object> k : killers) {
	            int monNo       = safeInt(k.get("MON_NO"));
	            String monName  = String.valueOf(k.get("MON_NAME"));
	            String uName    = String.valueOf(k.get("USER_NAME"));
	            int kills       = safeInt(k.get("KILL_COUNT"));

	            if (!java.util.Objects.equals(lastMonNo, monNo)) {
	            	sb.append(monNo).append("No ").append(monName).append(" 학살자");
	                lastMonNo = monNo;
	                lastMonName = monName;
	            }
	            sb.append(" ▶ ").append(uName)
	              .append(" (").append(kills).append("마리)").append(NL);
	        }
	    }
	    sb.append(NL);

	    /* === ⚔ 최초토벌 === */
	    sb.append("⚔ 최초토벌").append(NL);

	 // 1) 이미 토벌된 몬스터
	    List<HashMap<String,Object>> firsts = botNewService.selectFirstClearInfo();
	    Set<Integer> clearedMonSet = new HashSet<>();

	    if (firsts != null && !firsts.isEmpty()) {
	        for (HashMap<String,Object> fc : firsts) {
	            int monNo        = safeInt(fc.get("MON_NO"));
	            int monLv        = safeInt(fc.get("MON_LV"));
	            String monName   = String.valueOf(fc.get("MON_NAME"));
	            String firstUser = String.valueOf(fc.get("FIRST_CLEAR_USER"));
	            String firstJob  = Objects.toString(fc.get("FIRST_CLEAR_JOB"), "");
	            String firstTime = Objects.toString(fc.get("FIRST_CLEAR_DATE"), "");

	            clearedMonSet.add(monNo);

	            sb.append("No ").append(monNo).append(" ").append(monName).append(monLv).append("Lv")
	              .append(" ▶ ").append(firstUser);

	            if (!firstJob.isEmpty() && !"null".equalsIgnoreCase(firstJob)) {
	                sb.append("/").append(firstJob);
	            }
	            if (!firstTime.isEmpty() && !"null".equalsIgnoreCase(firstTime)) {
	                sb.append(" (").append(firstTime).append(")");
	            }
	            sb.append(NL);
	        }
	    }

	    return sb.toString();
	}


	
	/** 공격 랭킹 보기 */
	/** 구매 리스트(한국어 직관 표기, NL='♬') 
	 *  헤더: ▶ {userName}님, 구매 가능 아이템
	 *  각 아이템: 
	 *   [ID] 이름 (구매완료)
	 *   ↘가격: {price}sp
	 *   ↘옵션: 최소뎀 ±X, 최대뎀 ±Y, 치명타 +Z%, 체력회복 +R (5분마다), 최대체력 +H
	 *  - '랜덤' 문구 없음. 부호는 값 그대로(+/-) 노출.
	 */
	private String renderMarketListForBuy(List<HashMap<String,Object>> items, String userName, boolean hiddenYn) {
	    if (items == null || items.isEmpty()) {
	        return "▶ " + userName + "님, 구매 가능 아이템" + NL + "- (없음)";
	    }
	    final String allSeeStr = "===";

	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ ").append(userName).append("님").append(NL);
	    sb.append("더보기 리스트에서 선택 후 구매해주세요").append(NL);
	    sb.append("/구매 전체 < 전체보기, /구매 < 보유템 제외보기").append(NL);
	    sb.append("예) /구매 목검  또는  /구매 102");
	    sb.append(allSeeStr);

	    for (HashMap<String,Object> it : items) {
	    	int    itemId   = safeInt(it.get("ITEM_ID"));
	        String name     = String.valueOf(it.get("ITEM_NAME"));
	        int    price    = safeInt(it.get("ITEM_SELL_PRICE"));
	        String ownedYn  = String.valueOf(it.get("OWNED_YN"));

	        if(hiddenYn && "Y".equalsIgnoreCase(ownedYn)) {
	    		continue;
	    	}
	        
	        // 1행: [ID] 이름 (구매완료)
	        sb.append("[").append(itemId).append("] ").append(name);
	        if ("Y".equalsIgnoreCase(ownedYn)) sb.append(" (구매완료)");
	        sb.append(NL);

	        // 2행: 가격
	        sb.append("↘가격: ").append(price).append("sp").append(NL);

	        // 3행: 옵션 (공통 포맷터)
	        sb.append("↘옵션: ").append(buildOptionTokensFromMap(it)).append(NL).append(NL);
	    }
	    return sb.toString();
	}


	/** 옵션 토큰 공통 포맷터 (최소뎀/최대뎀/치명타/체력회복/최대체력/치명타뎀) */
	private String buildOptionTokensFromMap(HashMap<String, Object> m) {
	    int atkMin   = getInt(m.get("ATK_MIN"));
	    int atkMax   = getInt(m.get("ATK_MAX"));
	    int atkCri   = getInt(m.get("ATK_CRI"));
	    int hpRegen  = getInt(m.get("HP_REGEN"));
	    int hpMax    = getInt(m.get("HP_MAX"));
	    int criDmg   = getInt(m.get("CRI_DMG")); // NEW: 치명타뎀

	    StringBuilder opt = new StringBuilder();
	    boolean first = true;

	    if (atkMin != 0) { appendOpt(opt, first, "최소뎀" + formatSigned(atkMin)); first = false; }
	    if (atkMax != 0) { appendOpt(opt, first, "최대뎀" + formatSigned(atkMax)); first = false; }
	    if (atkCri  != 0){ appendOpt(opt, first, "치명타" + formatSigned(atkCri) + "%"); first = false; }
	    if (hpRegen != 0){ appendOpt(opt, first, "체력회복" + formatSigned(hpRegen)); first = false; }
	    if (hpMax   != 0){ appendOpt(opt, first, "최대체력" + formatSigned(hpMax)); first = false; }
	    if (criDmg  != 0){ appendOpt(opt, first, "치명타뎀" + formatSigned(criDmg) + "%"); first = false; }

	    return first ? "없음" : opt.toString();
	}

	
	private void appendOpt(StringBuilder opt, boolean first, String token) {
	    if (!first) opt.append(", ");
	    opt.append(token);
	}


	private int toInt(Object v) {
	    try { return (v == null) ? 0 : Integer.parseInt(String.valueOf(v)); }
	    catch (Exception e) { return 0; }
	}
	/* ===== Combat helpers ===== */

	/**
	 * 쓰러진 유저 자동 부활 처리
	 * - 마지막 피격(또는 공격) 시점 기준 REVIVE_WAIT_MINUTES(10) 경과 시 최대체력 10%로 부활
	 * - 이후 경과 시간에 따라 5분마다 effRegen 만큼 추가 회복
	 */
	private String reviveAfter1hIfDead(String userName, String roomName, User u,
	                                   int effHpMax, int effRegen) {
	    // 살아있으면 관여 안 함
	    if (u.hpCur > 0) return null;

	    Timestamp baseline = getLastDamageBaseline(userName, roomName);

	    // 기준 이벤트가 전혀 없으면: 보수적으로 10%로 세팅 후 조용히 복구
	    if (baseline == null) {
	        int startHp = (int) Math.ceil(effHpMax * 0.1); // 10%
	        botNewService.updateUserHpOnlyTx(userName, roomName, startHp);
	        u.hpCur = startHp;
	        return "";
	    }

	    Instant reviveAt = baseline.toInstant().plus(Duration.ofMinutes(REVIVE_WAIT_MINUTES));
	    Instant now = Instant.now();

	    // 아직 부활 시간 전이면 대기 안내
	    if (now.isBefore(reviveAt)) {
	        long remainMin = (long) Math.ceil(Duration.between(now, reviveAt).getSeconds() / 60.0);
	        return "쓰러진 상태입니다. 약 " + remainMin + "분 후 자동 부활합니다.";
	    }

	    // 부활 시간 경과: 10%에서 시작
	    int startHp = (int) Math.ceil(effHpMax * 0.1);

	    // 부활 시점 이후 경과 시간만큼 5분마다 회복 적용
	    long afterMin = Duration.between(reviveAt, now).toMinutes();
	    long healedTicks = Math.max(0, afterMin) / 5;
	    long healed = healedTicks * Math.max(0, (long) effRegen);

	    int effective = (int) Math.min((long) effHpMax, (long) startHp + healed);

	    botNewService.updateUserHpOnlyTx(userName, roomName, effective);
	    u.hpCur = effective;

	    // 빈 문자열 반환 시 이번 턴은 안내 없이 평소처럼 진행
	    return "";
	}

	private int computeEffectiveHpFromLastAttack(String userName, String roomName, User u, int effHpMax, int effRegen) {

	    // 0) 이미 풀피이거나 리젠 수치가 0 이하면 그대로 반환
	    if (u.hpCur >= effHpMax || effRegen <= 0) {
	        return Math.min(u.hpCur, effHpMax);
	    }

	    // 1) 마지막으로 "맞은" 시각 (몬스터에게 데미지 혹은 즉사 시점)
	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) {
	        // 아직 한 번도 맞은 적이 없다면 피격 기반 리젠 없음
	        return Math.min(u.hpCur, effHpMax);
	    }

	    Instant damagedAt = damaged.toInstant();
	    Instant now = Instant.now();

	    // 2) damaged 이후 현재까지 경과 시간(분) → 지금까지 총 리젠 틱 수
	    long minutesFromDamaged = java.time.Duration.between(damagedAt, now).toMinutes();
	    if (minutesFromDamaged <= 0) {
	        return Math.min(u.hpCur, effHpMax);
	    }

	    long totalTicksNow = minutesFromDamaged / 5L;  // 5분당 1틱
	    if (totalTicksNow <= 0) {
	        return Math.min(u.hpCur, effHpMax);
	    }

	    // 3) 마지막 공격 시각을 이용해, "이미 리젠에 반영된 틱" 계산
	    long prevTicks = 0L;
	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        long minutesUntilLastAtk = java.time.Duration.between(damagedAt, lastAtk.toInstant()).toMinutes();
	        if (minutesUntilLastAtk > 0) {
	            prevTicks = minutesUntilLastAtk / 5L;
	        }
	    }

	    // 4) 이번에 새로 발생한 틱만 회복에 사용
	    long newTicks = totalTicksNow - prevTicks;
	    if (newTicks <= 0) {
	        // 아직 "이전에 공격했을 때까지"보다 더 많은 5분 구간이 지나지 않았다면 추가 리젠 없음
	        return Math.min(u.hpCur, effHpMax);
	    }

	    long heal = newTicks * (long) effRegen;
	    long effective = (long) u.hpCur + heal;

	    if (effective > effHpMax) {
	        effective = effHpMax;
	    }

	    return (int) effective;
	}

	
	public String guideSetTargetMessage() {
	    final String NL = "♬";
	    List<Monster> monsters = botNewService.selectAllMonsters();
	    StringBuilder sb = new StringBuilder();
	    sb.append("공격 타겟이 없습니다. 먼저 타겟을 설정해주세요.").append(NL)
	      .append("예) /공격타겟 1   또는   /공격타겟 토끼").append(NL).append(NL)
	      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);
	    for (Monster m : monsters) {
	        sb.append(renderMonsterCompactLine(m,1)).append(NL);
	    }
	    return sb.toString();
	}
	
	private CooldownCheck checkCooldown(String userName, String roomName, String param1, String job) {
	    if ("test".equals(param1)) return CooldownCheck.ok();

	    int baseCd = COOLDOWN_SECONDS; // 2분
	    if ("궁수".equals(job)) {
	        baseCd = 300; // 5분
	    }

	    Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
	    if (last == null) return CooldownCheck.ok();

	    long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
	    if (sec >= baseCd) return CooldownCheck.ok();

	    long remainSec = baseCd - sec;
	    return CooldownCheck.blockSeconds(remainSec);
	}

	

	private String buildBelowHalfMsg(String userName, String roomName, User u, String param1) {
	    if ("test".equals(param1)) return null; // 테스트 모드 패스

	    int regenWaitMin = minutesUntilReach30(u, userName, roomName);
	    CooldownCheck cd = checkCooldown(userName, roomName, param1, u.job);

	    long remainMin = cd.remainSeconds / 60;
	    long remainSec = cd.remainSeconds % 60;

	    int waitMin = Math.max(regenWaitMin, cd.remainMinutes);
	    if (waitMin <= 0) return null;

	    StringBuilder sb = new StringBuilder();
	    sb.append(userName).append("님, 약 ").append(waitMin).append("분 후 공격 가능").append(NL)
	      .append("(최대체력의 20%까지 회복 필요 ").append(regenWaitMin).append("분, ")
	      .append("쿨타임 ").append(remainMin).append("분 ").append(remainSec).append("초)").append(NL)
	      .append("현재 체력: ").append(u.hpCur).append(" / ").append(u.hpMax)
	      .append(", 5분당 회복 +").append(u.hpRegen).append(NL);

	    // ✅ 리젠 스케줄 출력
	    //String sched = buildRegenScheduleSnippetEnhanced2(effHp, finalHpMax, effRegen, 60);
	    String sched = buildRegenScheduleSnippetEnhanced(userName, roomName, u, waitMin);
	    if (sched != null) sb.append(sched).append(NL);

	    // ✅ 풀HP ETA 출력
	    //int toFull = minutesUntilFull(userName, roomName, u);
	    /*
	    if (toFull == Integer.MAX_VALUE) {
	        sb.append("(풀HP까지: 리젠 없음)").append(NL);
	    } else if (toFull > 0) {
	        sb.append("(풀HP까지 약 ").append(toFull).append("분)").append(NL);
	    }
*/
	    return sb.toString();
	}
	
	// ✅ 5분 단위 회복 기준, 피격/공격 기준과 일관성 유지
	private int minutesUntilFull(String userName, String roomName, User u) {
	    if (u.hpCur >= u.hpMax) return 0;
	    if (u.hpRegen <= 0) return Integer.MAX_VALUE;

	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) return Integer.MAX_VALUE;

	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

	    Timestamp from = damaged;
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        from = lastAtk;
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long offset = minutesPassed % 5;

	    // 다음 틱까지 남은 시간 (경계면 → 5분 후를 다음 틱으로 본다)
	    int toNextTick = (int)((5 - offset) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    int needHp = u.hpMax - u.hpCur;
	    int ticksNeeded = (int)Math.ceil(needHp / (double)u.hpRegen);
	    if (ticksNeeded <= 0) return 0;

	    return toNextTick + (ticksNeeded - 1) * 5;
	}


	private Flags rollFlags(User u, Monster m) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Flags f = new Flags();
		int crit = Math.min(100, Math.max(0, u.critRate));
		f.atkCrit = r.nextInt(100) < crit;
		f.monPattern = rollPatternWeighted(m, r);
		return f;
	}

	private int rollPatternWeighted(Monster m, ThreadLocalRandom r) {
	    int enabled = Math.max(1, m.monPatten);
	    int[] weights = new int[enabled];
	    for (int i = 0; i < enabled; i++) weights[i] = 1;
	    if (enabled == 2) { weights[0] = 20; weights[1] = 80; }
	    if (enabled == 3) { weights[0] = 10; weights[1] = 60; weights[2] = 30; }
	    if (enabled == 4) { weights[0] = 0; weights[1] = 60; weights[2] = 25; weights[3] = 15; }
	    if (enabled == 5) { weights[0] = 0; weights[1] = 60; weights[2] = 9; weights[3] = 30; weights[4] = 1; }
	    int sum = 0; for (int w : weights) sum += Math.max(0, w);
	    if (sum <= 0) { for (int i = 0; i < enabled; i++) weights[i] = 1; sum = enabled; }
	    int pick = r.nextInt(sum) + 1, acc = 0;
	    for (int i = 0; i < enabled; i++) { acc += weights[i]; if (pick <= acc) return i + 1; }
	    return 1;
	}

	private AttackCalc calcDamage(User u, Monster m, Flags f, int baseAtk, boolean crit, double critMultiplier) {
		AttackCalc c = new AttackCalc();
		c.baseAtk = baseAtk;
		c.critMultiplier = critMultiplier;
		c.atkDmg = crit ? (int) Math.round(baseAtk * critMultiplier) : baseAtk;

		String name = m.monName;
		switch (f.monPattern) {
		case 1: c.monDmg = 0; c.patternMsg = name + "이(가) 당신을 바라봅니다"; break;
		case 2:
			int minDmg = Math.max(1, (int) Math.floor(m.monAtk * 0.5));
			int maxDmg = m.monAtk;
			c.monDmg = ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1);
			c.patternMsg = name + "이(가) " + c.monDmg + " 의 데미지로 반격합니다!"; break;
		case 3:
		    // 기존 공격 데미지(크리티컬 반영 후)를 절반으로 줄인 뒤,
		    // 몬스터 방어력(defPower)을 적용하여 최종 피해를 계산한다.
		    int original = c.atkDmg; // 이전 단계(크리 포함) 데미지
		    int reduced = (int) Math.round(original * 0.5); // 방어 패턴으로 1차 감소

		    int minDef = Math.max(1, (int) Math.floor(m.monAtk * 0.5)); // 예: 22라면 11
		    int maxDef = m.monAtk;                                      // 예: 22
		    int defPower = ThreadLocalRandom.current().nextInt(minDef, maxDef + 1);

		    if (defPower >= reduced) {
		        // 완전 방어
		        c.atkDmg = 0;
		        c.monDmg = 0;
		        c.patternMsg = name + "이(가) 공격을 완전 방어했습니다!";
		    } else {
		        // 일부 방어: 최종 피해 = reduced - defPower
		        int finalDmg = reduced - defPower;
		        c.atkDmg = finalDmg;
		        c.monDmg = 0;
		        c.patternMsg = name + "이(가) 방어합니다!("
		                + original
		                + " → 50%↓ " + reduced
		                + " → 방어력 " + defPower
		                + " → 최종 " + finalDmg + ")";
		    }
		    break;
		case 4: c.monDmg = (int) Math.round(m.monAtk * 1.5); c.patternMsg = name + "의 필살기! (피해 " + c.monDmg + ")"; break;
		case 5:   // 🔥 NEW: 즉사 패턴
		    c.monDmg = 9_999_999;  // 사실상 무조건 즉사
		    c.patternMsg = name + "의 알수없는 공격"; 
		    break;
		default: c.monDmg = 0; c.patternMsg = name + "의 알 수 없는 행동… (피해 0)";
		}
		return c;
	}
	
	
	private String formatSigned(int v) {
	    return (v >= 0 ? "+" + v : String.valueOf(v));
	}

	private int safeInt(Object v) {
	    try { return v == null ? 0 : Integer.parseInt(String.valueOf(v)); }
	    catch (Exception e) { return 0; }
	}

	// 이름은 기존 그대로 두고, 현재는 20% 기준으로 동작
	private int minutesUntilReach30(User u, String userName, String roomName) {
	    int threshold = (int)Math.ceil(u.hpMax * 0.2); // ✅ 20% 기준
	    if (u.hpCur >= threshold) return 0;
	    if (u.hpRegen <= 0) return Integer.MAX_VALUE;

	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) return 0; // 맞은 적 없으면 막지 않음

	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

	    Timestamp from = damaged;
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        from = lastAtk;
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long offset = minutesPassed % 5;

	    int toNextTick = (int)((5 - offset) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    int hpNeeded = threshold - u.hpCur;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)u.hpRegen);
	    if (ticksNeeded <= 0) return 0;

	    return toNextTick + (ticksNeeded - 1) * 5;
	}


	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky) {
	    Resolve r = new Resolve();
	    r.killed = willKill;
	    r.lucky  = lucky;
	    int levelGap = u.lv - m.monLv;
	    double expMultiplier;
	    
	    if (levelGap >= 0) {
	        // 플레이어가 몬스터보다 높을 때
	        expMultiplier = Math.max(0.1, 1.0 - Math.min(levelGap, 5) * 0.1);
	    } else {
	        // 몬스터가 더 강할 때 (보너스)
	        expMultiplier = 1.0 + Math.min(-levelGap, 5) * 0.05; // 레벨 차이 1당 5% 보너스, 최대 25%
	    }

	    int baseKillExp = (int)Math.round(m.monExp * expMultiplier);

	    if (willKill) r.gainExp = lucky ? baseKillExp * 3 : baseKillExp;
	    else          r.gainExp = (int)Math.round(baseKillExp/100)+1;  //

	    if (lucky && willKill) {
	        r.dropCode = "3";
	        return r;
	    }
	    
	    double dropRate = getDropRateByLevel(m.monNo);  // ← 새 메서드 사용
	    
	    boolean drop = willKill && ThreadLocalRandom.current().nextDouble(0, 100) < dropRate;
	    r.dropCode = drop ? "1" : "0";
	    return r;
	}
	private double getDropRateByLevel(int monLv) {
	    switch (monLv) {
	        case 1:  return 70.0;
	        case 2:  return 65.0;
	        case 3:  return 60.0;
	        case 4:  return 60.0;
	        case 5:  return 50.0;
	        case 6:  return 50.0;
	        case 7:  return 50.0;
	        case 8:  return 50.0;
	        case 9:  return 50.0;
	        case 10: return 40.0;
	        case 11: return 40.0;
	        case 12: return 40.0;
	        case 13: return 30.0;
	        case 14: return 20.0;
	        case 15: return 20.0;
	        case 16: return 20.0;
	        case 17: return 20.0;
	        case 18: return 20.0;
	        case 19: return 20.0;
	        case 20: return 20.0;
	        default: return 10.0;
	    }
	}
	
	private int calcBaseHpMax(int lv) {
	    if (lv <= 1) return 10;
	    return 10 + (lv - 1) * 10;
	}

	private int calcBaseAtkMin(int lv) {
	    if (lv <= 1) return 1;
	    return lv;
	}

	private int calcBaseAtkMax(int lv) {
	    if (lv <= 1) return 15;
	    return 3 + (lv - 1) * 3;
	}

	private int calcBaseCritRate(int lv) {
		if (lv <= 1) return 10;
	    return 10 + (lv - 1) * 2;
	}

	private int calcBaseHpRegen(int lv) {
	    if (lv <= 1) return 2;  // Lv1 = 2부터 시작
	    return 2 + ((lv - 1) / 3); // 3레벨마다 +1
	}
	
	/** HP/EXP/LV + 로그 저장 (DB에는 '순수 레벨 기반 스탯'만 반영) */
	private LevelUpResult persist(String userName, String roomName,
	                              User u, Monster m,
	                              Flags f, AttackCalc c, Resolve res,int effHpMax) {

	    // 1) 최종 HP 계산 (전투 데미지 반영)
	    u.hpCur = Math.max(0, u.hpCur - c.monDmg);

	    // 2) EXP 적용 + 레벨업 (u.lv, u.expCur, u.expNext 변경)
	    LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);
	    
	 // 3) 레벨업이 발생했고, 죽은 게 아니라면 → 실전투 HPMax 기준으로 풀피 회복
	    if (up.levelUpCount > 0 && u.hpCur > 0 && effHpMax > 0) {
	        u.hpCur = effHpMax; // 여기서 109 같은 값으로 올려줌
	    }

	    // 3) 순수 레벨 기준 스탯 계산
	    //    ※ 여기서 사용하는 calcBaseXXX()는
	    //       "아이템/직업/강화 미포함 기준"으로 구현해야 함.
	    int baseHpMax    = calcBaseHpMax(u.lv);
	    int baseAtkMin   = calcBaseAtkMin(u.lv);
	    int baseAtkMax   = calcBaseAtkMax(u.lv);
	    int baseCritRate = calcBaseCritRate(u.lv);
	    int baseHpRegen  = calcBaseHpRegen(u.lv);
/*
	    // 4) DB에는 "현재 HP" 그대로 저장
	    botNewService.updateUserAfterBattleTx(
	        userName, roomName,
	        u.lv, u.expCur, u.expNext,
	        u.hpCur,         // 여기 이제 109 같은 값 들어갈 수 있음
	        u.hpMax,         // 이건 여전히 '기본 HP' (원하면 그대로 두는게 안정적)
	        u.atkMin, u.atkMax, u.critRate, u.hpRegen
	    );
*/
	    
	    // 4) 유저 테이블 업데이트: **항상 '순수 레벨 스탯'만 저장**
	    botNewService.updateUserAfterBattleTx(
	        userName,
	        roomName,
	        u.lv,
	        u.expCur,
	        u.expNext,
	        u.hpCur,
	        baseHpMax,
	        baseAtkMin,
	        baseAtkMax,
	        baseCritRate,
	        baseHpRegen
	    );

	    // 5) 사망 여부
	    int deathYn = (u.hpCur == 0 && c.monDmg > 0) ? 1 : 0;

	    // 6) 드랍 인벤토리 적재 (킬+드랍 있을 때)
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            try {
	                Integer itemId = botNewService.selectItemIdByName(dropName);
	                if (itemId != null) {
	                    HashMap<String, Object> inv = new HashMap<>();
	                    inv.put("userName",  userName);
	                    inv.put("roomName",  roomName);
	                    inv.put("itemId",    itemId);
	                    inv.put("qty",       1);
	                    inv.put("delYn",     "0");
	                    inv.put("gainType", "3".equals(res.dropCode) ? "DROP3" : "DROP");
	                    botNewService.insertInventoryLogTx(inv);
	                }
	            } catch (Exception ignore) {
	                // 드랍 저장 실패해도 전투 진행은 계속
	            }
	        }
	    }

	    // 7) BattleLog 저장 (전투 당시 정보 기준)
	    int dropAsInt = "3".equals(res.dropCode) ? 3
	                 : ("1".equals(res.dropCode) ? 1 : 0);

	    
	    int buffYn = 0;
	    
	    if (u.job !=null && "도사".equals(u.job.trim())) {   // job 은 u.job.trim()
	        buffYn = 1;
	    }

	    
	    BattleLog log = new BattleLog()
	        .setUserName(userName)
	        .setRoomName(roomName)
	        .setLv(up.beforeLv)                 // 공격 시점 레벨
	        .setTargetMonLv(m.monNo)
	        .setGainExp(up.gainedExp)
	        .setAtkDmg(c.atkDmg)
	        .setMonDmg(c.monDmg)
	        .setAtkCritYn(f.atkCrit ? 1 : 0)
	        .setMonPatten(f.monPattern)
	        .setKillYn(res.killed ? 1 : 0)
	        .setNowYn(1)
	        .setDeathYn(deathYn)
	        .setLuckyYn(res.lucky ? 1 : 0)
	        .setDropYn(dropAsInt)
	    	.setBuffYn(buffYn)
	    	.setJobSkillYn(c.jobSkillUsed ? 1 : 0)
	    	.setJob(u.job);

	    botNewService.insertBattleLogTx(log);

	    
	    
	    res.levelUpCount = up.levelUpCount;
	    return up;
	}


	 
    private String grantBlessLevelBonus(String userName, String roomName, int beforeLv, int afterLv) {
    	int total = 0;
        StringBuilder sb = new StringBuilder();
    	
    	if (afterLv <= beforeLv) return "";

        int[] targetLv = {2, 3, 4, 5, 6, 7};
        for (int lv : targetLv) {
            if (beforeLv < lv && afterLv >= lv) {
                String cmd = "ADMIN_BLESS_LV" + lv;

                int already = 0;
                try {
                    already = botNewService.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
                } catch (Exception ignore) {}

                if (already == 0) {
                    HashMap<String,Object> p = new HashMap<>();
                    p.put("userName", userName);
                    p.put("roomName", roomName);
                    p.put("score", 200);
                    p.put("cmd", cmd);
                    botNewService.insertPointRank(p);
                    
                    sb.append("✨ 운영자의 축복! Lv")
                    .append(lv)
                    .append(" 달성 보너스 :")
                    .append("200 sp 지급").append(NL);
                    total++;
                }
            }
        }
        
        if(total>0) {
        	return sb.toString();
        }else {
        	
        	return "";
        }
        
    }

	/** 무기강화 효과 (25강부터 +1, 상한 없음) */
	private int getWeaponAtkBonus(int weaponLv) {
	    if (weaponLv < 25) return 0;
	    return weaponLv - 24;
	}

	private String buildAttackMessage(
	        String userName, User u, Monster m, Flags flags, AttackCalc calc,
	        Resolve res, LevelUpResult up,
	        int monHpRemainBefore, int monMaxHp,
	        int shownAtkMin, int shownAtkMax,
	        int weaponLv, int weaponBonus,
	        int displayHpMax // ← 표시용 HP Max(아이템 포함)
	) {
	    StringBuilder sb = new StringBuilder();

	    // 헤더
	    sb.append("⚔ ").append(userName).append("님, ").append(NL)
	      .append("▶ ").append(m.monName).append("을(를) 공격!").append(NL).append(NL);

	    // 🍀 Lucky 배너(빛나는 정책)
	    if (res.lucky) {
	        sb.append("✨ LUCKY MONSTER! (처치시 경험치×3, 빛나는 드랍)").append(NL);
	    }

	    // 치명타
	    if (flags.atkCrit) sb.append("✨ 치명타!").append(NL);
	    
	 // 🎯 궁수 저격 히든: 데미지 수치는 비공개, 결과만 표기
	    if (flags.snipe) {
	        int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);

	        sb.append("✨ 저격[히든] 발동!").append(NL);

	        if (res.killed || monHpAfter <= 0) {
	            sb.append(m.monName)
	              .append("을(를) 단번에 처치했습니다!").append(NL)
	              .append("❤️ 몬스터 HP: 0 / ").append(monMaxHp).append(NL);
	        } else {
	            sb.append(m.monName)
	              .append("이(가) 간신히 버텼습니다.").append(NL)
	              .append("❤️ 몬스터 HP: ")
	              .append(monHpAfter).append(" / ").append(monMaxHp).append(NL);
	        }

	        // 몬스터 패턴 / 받은 피해 안내 (여긴 정상 공개)
	        if (calc.patternMsg != null && !calc.patternMsg.isEmpty()) {
	            sb.append("⚅ ").append(calc.patternMsg).append(NL);
	        }

	        if (calc.monDmg > 0) {
	            sb.append("❤️ 받은 피해: ").append(calc.monDmg)
	              .append(",  현재 체력: ").append(u.hpCur)
	              .append(" / ").append(displayHpMax).append(NL);
	        } else {
	            sb.append("❤️ 현재 체력: ").append(u.hpCur)
	              .append(" / ").append(displayHpMax).append(NL);
	        }

	        // 드랍
	        if (res.killed && !"0".equals(res.dropCode)) {
	            String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	            if (!dropName.isEmpty()) {
	                if ("3".equals(res.dropCode)) {
	                    sb.append("✨ 드랍 획득: ✨빛").append(dropName).append(NL);
	                } else {
	                    sb.append("✨ 드랍 획득: ").append(dropName).append(NL);
	                }
	            }
	        }

	        // EXP
	        sb.append("✨ EXP+").append(res.gainExp)
	          .append(" , EXP: ").append(u.expCur)
	          .append(" / ").append(u.expNext).append(NL);

	        // 레벨업 정보
	        if (up != null && up.levelUpCount > 0) {
	            sb.append(NL)
	              .append("✨ 레벨업! Lv ").append(up.beforeLv)
	              .append(" → ").append(up.afterLv);
	            if (up.levelUpCount > 1) {
	                sb.append(" ( +").append(up.levelUpCount).append(" )");
	            }
	            sb.append(NL);

	            sb.append("└:❤️HP ")
	              .append(up.beforeHpMax).append("→").append(up.afterHpMax)
	              .append(" (+").append(up.hpMaxDelta).append(")").append(NL);

	            sb.append("└:⚔ATK ")
	              .append(up.beforeAtkMin).append("~").append(up.beforeAtkMax)
	              .append("→").append(up.afterAtkMin).append("~").append(up.afterAtkMax)
	              .append(" (+").append(up.atkMinDelta).append("~+").append(up.atkMaxDelta).append(")").append(NL);

	            sb.append("└: CRIT ")
	              .append(up.beforeCrit).append("%→").append(up.afterCrit).append("%")
	              .append(" (+").append(up.critDelta).append("%)").append(NL);

	            sb.append("└: 5분당회복 ")
	              .append(up.beforeHpRegen).append("→").append(up.afterHpRegen)
	              .append(" (+").append(up.hpRegenDelta).append(")").append(NL);
	        }

	        // ✅ 여기서 끝: 저격일 땐 일반 데미지 표현 블록으로 내려가지 않음
	        return sb.toString();
	    }

	    // 데미지
	    sb.append("⚔ 데미지: (").append(shownAtkMin).append("~").append(shownAtkMax).append(" ⇒ ");
	    if (flags.atkCrit && calc.baseAtk > 0 && calc.critMultiplier >= 1.0) {
	        sb.append(calc.baseAtk).append("*").append(trimDouble(calc.critMultiplier)).append("=>").append(calc.atkDmg);
	    } else {
	        sb.append(calc.atkDmg);
	    }
	    sb.append(")").append(NL);

	    // 몬스터 HP
	    int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);
	    sb.append("❤️ 몬스터 HP: ").append(monHpAfter).append(" / ").append(monMaxHp).append(NL).append(NL);

	    // 반격
	    if (calc.patternMsg != null && !calc.patternMsg.isEmpty()) {
	        sb.append("⚅ ").append(calc.patternMsg).append(NL);
	    }

	    // 현재 체력(표시 Max 사용)
	    if (calc.monDmg > 0) {
	        sb.append("❤️ 받은 피해: ").append(calc.monDmg)
	          .append(",  현재 체력: ").append(u.hpCur).append(" / ").append(displayHpMax).append(NL);
	    } else {
	        sb.append("❤️ 현재 체력: ").append(u.hpCur).append(" / ").append(displayHpMax).append(NL);
	    }

	    // 드랍
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            if ("3".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: ✨빛").append(dropName).append(NL);
	            } else {
	                sb.append("✨ 드랍 획득: ").append(dropName).append(NL);
	            }
	        }
	    }

	    // EXP
	    sb.append("✨ EXP+").append(res.gainExp)
	      .append(" , EXP: ").append(u.expCur).append(" / ").append(u.expNext).append(NL);

	    if (up != null && up.levelUpCount > 0) {
	        sb.append(NL)
	          .append("✨ 레벨업! Lv ").append(up.beforeLv)
	          .append(" → ").append(up.afterLv);
	        if (up.levelUpCount > 1)
	            sb.append(" ( +").append(up.levelUpCount).append(" )");
	        sb.append(NL);

	        // ❤️ HP
	        sb.append("└:❤️HP ")
	          .append(up.beforeHpMax).append("→").append(up.afterHpMax)
	          .append(" (+").append(up.hpMaxDelta).append(")").append(NL);

	        // ⚔ ATK
	        sb.append("└:⚔ATK ")
	          .append(up.beforeAtkMin).append("~").append(up.beforeAtkMax)
	          .append("→").append(up.afterAtkMin).append("~").append(up.afterAtkMax)
	          .append(" (+").append(up.atkMinDelta).append("~+").append(up.atkMaxDelta).append(")").append(NL);

	        // CRIT
	        sb.append("└: CRIT ")
	          .append(up.beforeCrit).append("%→").append(up.afterCrit).append("%")
	          .append(" (+").append(up.critDelta).append("%)").append(NL);

	        // HP_REGEN
	        sb.append("└: 5분당회복 ")
	          .append(up.beforeHpRegen).append("→").append(up.afterHpRegen)
	          .append(" (+").append(up.hpRegenDelta).append(")").append(NL);
	    }
	    return sb.toString();
	}

	/* ===== utils ===== */

	private String trimDouble(double v) {
	    String s = String.valueOf(v);
	    if (s.endsWith(".0")) return s.substring(0, s.length()-2);
	    return s;
	}

	
	private static class Resolve {
		boolean killed; String dropCode; int gainExp; int levelUpCount; boolean lucky;
	}
	private static class CooldownCheck {
	    final boolean ok; final int remainMinutes; final long remainSeconds;
	    private CooldownCheck(boolean ok, int remainMinutes, long remainSeconds) {
	        this.ok = ok; this.remainMinutes = remainMinutes; this.remainSeconds = remainSeconds;
	    }
	    static CooldownCheck ok() { return new CooldownCheck(true, 0, 0); }
	    static CooldownCheck blockSeconds(long remainSec) {
	        return new CooldownCheck(false, (int)Math.ceil(remainSec/60.0), remainSec);
	    }
	}

	public static class LevelUpResult {
		public int gainedExp, beforeLv, afterLv, beforeExpCur, afterExpCur, afterExpNext, levelUpCount;
		public int hpMaxDelta, atkMinDelta, atkMaxDelta;
		public int critDelta;
		public int hpRegenDelta;
		public int beforeHpMax,   afterHpMax;
		public int beforeAtkMin,  afterAtkMin;
		public int beforeAtkMax,  afterAtkMax;
		public int beforeCrit,    afterCrit;
		public int beforeHpRegen, afterHpRegen;
	}

	private LevelUpResult applyExpAndLevelUp(User u, int gainedExp) {
	    LevelUpResult r = new LevelUpResult();
	    r.gainedExp = Math.max(0, gainedExp);

	    r.beforeLv      = u.lv;
	    r.beforeExpCur  = u.expCur;

	    r.beforeHpMax   = u.hpMax;
	    r.beforeAtkMin  = u.atkMin;
	    r.beforeAtkMax  = u.atkMax;
	    r.beforeCrit    = u.critRate;
	    r.beforeHpRegen = u.hpRegen;

	    int lv      = u.lv;
	    int expCur  = u.expCur + r.gainedExp;
	    int expNext = u.expNext;

	    int hpMax   = u.hpMax;
	    int atkMin  = u.atkMin;
	    int atkMax  = u.atkMax;
	    int crit    = u.critRate;
	    int regen   = u.hpRegen;

	    int hpDelta = 0;
	    int atkMinDelta = 0;
	    int atkMaxDelta = 0;
	    int critDelta   = 0;
	    int regenDelta  = 0;
	    int upCount     = 0;

	    while (expCur >= expNext) {
	        expCur -= expNext;
	        lv++;
	        upCount++;

	        expNext = calcNextExp(lv, expNext);

	        int incHp    = 10;
	        int incAtkMin= 1;
	        int incAtkMax= 3;

	        hpMax  += incHp;     hpDelta     += incHp;
	        atkMin += incAtkMin; atkMinDelta += incAtkMin;
	        atkMax += incAtkMax; atkMaxDelta += incAtkMax;

	        crit   += 2;         critDelta   += 2;

	        if (lv % 3 == 0) {
	            regen++;         regenDelta++;
	        }
	    }

	    u.lv        = lv;
	    u.expCur    = expCur;
	    u.expNext   = expNext;
	    u.hpMax     = hpMax;
	    u.atkMin    = atkMin;
	    u.atkMax    = atkMax;
	    u.critRate  = crit;
	    u.hpRegen   = regen;

	    r.afterLv       = lv;
	    r.afterExpCur   = expCur;
	    r.afterExpNext  = expNext;
	    r.levelUpCount  = upCount;

	    r.afterHpMax    = hpMax;
	    r.afterAtkMin   = atkMin;
	    r.afterAtkMax   = atkMax;
	    r.afterCrit     = crit;
	    r.afterHpRegen  = regen;

	    r.hpMaxDelta    = hpDelta;
	    r.atkMinDelta   = atkMinDelta;
	    r.atkMaxDelta   = atkMaxDelta;
	    r.critDelta     = critDelta;
	    r.hpRegenDelta  = regenDelta;

	    return r;
	}

	private static final int DELTA_BASE = 150;
	private static final int DELTA_LIN  = 120;
	private static final int DELTA_QUAD = 8;
	private static final int NEXT_CAP   = Integer.MAX_VALUE;

	private int calcNextExp(int newLv, int prevExpNext) {
	    long lv = Math.max(1, newLv);
	    long delta = (long)DELTA_BASE + (long)DELTA_LIN * lv + (long)DELTA_QUAD * lv * lv;
	    long next  = (long)prevExpNext + delta;
	    if (next > NEXT_CAP) return NEXT_CAP;
	    return (int) next;
	}

	private Timestamp getLastDamageBaseline(String userName, String roomName) {
		return botNewService.selectLastDamagedTime(userName, roomName);
	}
	private String buildRegenScheduleSnippetEnhanced2(String userName, String roomName, User u, int horizonMinutes, int currentHp, int hpMax, int effRegen, int minutesSpan) {

		if (horizonMinutes <= 0 || effRegen <= 0 || currentHp >= hpMax) return null;
		
	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

	    Timestamp from;
	    if (damaged != null && lastAtk != null) {
	        from = lastAtk.after(damaged) ? lastAtk : damaged;
	    } else if (damaged != null) {
	        from = damaged;
	    } else if (lastAtk != null) {
	        from = lastAtk;
	    } else {
	        // ✅ 아무 로그도 없으면 "지금" 기준으로 시작
	        from = Timestamp.from(Instant.now());
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long ticksSoFar = minutesPassed / 5;

	    int toNextTick = (int)((5 - (minutesPassed % 5)) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    StringBuilder sb = new StringBuilder();
	    final String NL = "♬";

	    int curHp = currentHp;
	    int maxHp = hpMax;
	    int regen = effRegen;

	    // 5분 단위로 예측 표시
	    
	    int msg_cnt =0;
	    for (int t = toNextTick; t <= horizonMinutes; t += 5) {
	        int ticksAdded = (int)(((minutesPassed + t) / 5) - ticksSoFar);
	        if (ticksAdded <= 0) continue;

	        int proj = Math.min(maxHp, curHp + ticksAdded * regen);
	        sb.append("- ").append(t).append("분 뒤: HP ").append(proj)
	          .append(" / ").append(maxHp).append(NL);

	        msg_cnt++;
	        if(msg_cnt > 5) break;
	        
	        if (proj >= maxHp) break; // 풀피 도달 시 중단
	    }

	    // === 풀 HP까지 남은 시간 계산 ===
	    int hpNeeded = maxHp - curHp;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)regen);
	    int minutesToFull = (toNextTick + (ticksNeeded - 1) * 5);
	    if (minutesToFull < 0) minutesToFull = 0;
	    
	    sb.append(" (풀HP까지 약 ").append(minutesToFull).append("분)").append(NL);
	    
	    String result = sb.toString().trim();

	    return result.isEmpty() ? null : result;
	
	}
	
	// ✅ 5분 단위 리젠 스케줄 + 풀HP까지 예상시간 표시
	private String buildRegenScheduleSnippetEnhanced(String userName, String roomName, User u, int horizonMinutes) {
	    if (horizonMinutes <= 0 || u.hpRegen <= 0 || u.hpCur >= u.hpMax) return null;

	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) return null;

	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);
	    Timestamp from = damaged;
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        from = lastAtk;
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long ticksSoFar = minutesPassed / 5;

	    int toNextTick = (int)((5 - (minutesPassed % 5)) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    StringBuilder sb = new StringBuilder();
	    final String NL = "♬";

	    int curHp = u.hpCur;
	    int maxHp = u.hpMax;
	    int regen = u.hpRegen;

	    // 5분 단위로 예측 표시
	    
	    int msg_cnt =0;
	    for (int t = toNextTick; t <= horizonMinutes; t += 5) {
	        int ticksAdded = (int)(((minutesPassed + t) / 5) - ticksSoFar);
	        if (ticksAdded <= 0) continue;

	        int proj = Math.min(maxHp, curHp + ticksAdded * regen);
	        sb.append("- ").append(t).append("분 뒤: HP ").append(proj)
	          .append(" / ").append(maxHp).append(NL);

	        msg_cnt++;
	        if(msg_cnt > 5) break;
	        
	        if (proj >= maxHp) break; // 풀피 도달 시 중단
	    }

	    // === 풀 HP까지 남은 시간 계산 ===
	    int hpNeeded = maxHp - curHp;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)regen);
	    int minutesToFull = (toNextTick + (ticksNeeded - 1) * 5);
	    if (minutesToFull < 0) minutesToFull = 0;
	    
	    sb.append(" (풀HP까지 약 ").append(minutesToFull).append("분)").append(NL);
	    
	    String result = sb.toString().trim();

	    return result.isEmpty() ? null : result;
	}



	
	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int parseIntSafe(String s) {
	    try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
	}
	
	/** 드랍 아이템 이름 → 판매가 조회 (없으면 0) */
	private int getDropPriceByName(String dropName) {
	    if (dropName == null || dropName.trim().isEmpty()) return 0;
	    try {
	        Integer p = botNewService.selectItemPriceByName(dropName.trim());
	        return (p == null ? 0 : Math.max(0, p));
	    } catch (Exception ignore) {
	        return 0;
	    }
	}
	/** 몬스터 요약 한 줄 UI */
	private String renderMonsterCompactLine(Monster m, int userLv) {
		// 드랍 아이템명 및 판매가격
	    String dropName = (m.monDrop != null ? m.monDrop : "-");
	    int dropPrice = getDropPriceByName(dropName);

	    // ATK 범위 계산 (50% ~ 100%)
	    int atkMin = (int) Math.floor(m.monAtk * 0.5);
	    int atkMax = m.monAtk;

	    // EXP 패널티 계산 (전투 공식 동일)
	    int baseExp = Math.max(0, m.monExp);
	    int diff = userLv - m.monNo;
	    int over = Math.max(0, diff);
	    double rate = Math.max(0.1, 1.0 - over * 0.1);
	    int effExp = (int) Math.round(baseExp * rate);
	    boolean hasPenalty = (over > 0 && rate < 1.0);

	    StringBuilder sb = new StringBuilder();

	    // 1행: 기본 정보
	    sb.append(m.monNo).append(". ").append(m.monName).append(" [").append(m.monLv).append("lv]")
	      .append(" ❤️HP ").append(m.monHp)
	      .append(" ⚔ATK ").append(atkMin).append("~").append(atkMax)
	      .append(NL);

	    // 2행: 보상 정보
	    sb.append("▶ 보상: EXP ").append(effExp);
	    if (hasPenalty) sb.append("▼");
	    sb.append(" / ").append(dropName).append(" ").append(dropPrice).append("sp")
	      .append(NL);

	    return sb.toString();
	}
	
	/** 몬스터 최초 토벌 보상 (방별 1명만)
	 *  - 이미 해당 ROOM_NAME에 ACHV_FIRST_CLEAR_MON_{monNo}가 존재하면 스킵
	 *  - 없으면: 해당 유저에게 rewardSp 지급 + CMD 기록
	 */
	private String grantFirstClearIfEligible(
	        String userName,
	        String roomName,
	        Monster m,
	        Map<String, Integer> globalAchvMap  // 🔹 추가
	) {
	    if (m == null) return "";

	    String achvCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	    // 1) 글로벌 Map에서 이미 존재하는지 확인
	    int globalCnt = 0;
	    if (globalAchvMap != null) {
	        Integer v = globalAchvMap.get(achvCmd);
	        if (v != null) globalCnt = v.intValue();
	    }
	    if (globalCnt > 0) {
	        // 이미 이 CMD로 기록된 최초 토벌이 있음 → 보상 X
	        return "";
	    }

	    int rewardSp = 0;
	    switch (m.monNo) {
	        case 1:
	        case 2:
	        case 3:
	        case 4:
	        case 5:
	            rewardSp = 100; break;
	        case 6:
	            rewardSp = 300; break;
	        case 7:
	        case 8:
	            rewardSp = 500; break;
	        case 9:
	        case 10:
	        case 11:
	        case 12:
	            rewardSp = 1000; break;
	        case 13:
	        case 14:
	            rewardSp = 1500; break;
	        case 15:
	        case 16:
	            rewardSp = 2000; break;
	        case 17:
	        case 18:
	            rewardSp = 2500; break;
	        case 19:
	        case 20:
	            rewardSp = 3000; break;
	        default:
	            break;
	    }
	    if (rewardSp <= 0) {
	        return ""; // 0이면 지급 X
	    }

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("cmd", achvCmd);
	    botNewService.insertPointRank(pr);

	    // (선택) 메모리상으로도 업데이트
	    if (globalAchvMap != null) {
	        globalAchvMap.put(achvCmd, globalCnt + 1);
	    }

	    return "✨ 업적 달성! [" + m.monName + "] 최초 토벌자 보상 +"
	            + rewardSp + "sp 지급되었습니다." + NL;
	}

	
	
	/** 특정 유저가 특정 업적 CMD를 아직 받지 않았으면 1회성 보상 지급 */
	private String grantOnceIfEligible(String userName, String roomName,
	                                   String achvCmd, int rewardSp) {
	    if (rewardSp <= 0) return "";

	    Integer cnt = botNewService.selectPointRankCountByCmdUserInRoom(roomName, userName, achvCmd);
	    if (cnt != null && cnt > 0) {
	        return ""; // 이미 이 업적 보상 받음
	    }

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("cmd", achvCmd);
	    botNewService.insertPointRank(pr);

	    return "✨ 업적 달성! [" + achvCmd + "] 보상 +" + rewardSp + "sp 지급되었습니다." + NL;
	}

	
	/** 몬스터별 50/100/300/500 킬 업적 보상 */
	private int calcPerMonsterKillReward(int monNo, int threshold) {
	    switch (monNo) {
	        case 1: // 토끼
	        case 2: // 다람쥐
	        case 3: // 쥐
	            switch (threshold) {
	                case 50:  return 50;
	                case 100: return 50;
	                case 300: return 50;
	                case 500: return 50;
	            }
	            break;

	        case 4: // 뱀
	        case 5: // 사슴
	            switch (threshold) {
	                case 50:  return 100;
	                case 100: return 100;
	                case 300: return 100;
	                case 500: return 100;
	            }
	            break;

	        case 6: // 곰
	            switch (threshold) {
	                case 50:  return 200;
	                case 100: return 200;
	                case 300: return 200;
	                case 500: return 200;
	            }
	            break;

	        case 7: // 여우
	        case 8: // 돼지
	            switch (threshold) {
	                case 50:  return 300;
	                case 100: return 300;
	                case 300: return 300;
	                case 500: return 300;
	            }
	            break;

	        case 9: // 호랑이
	        case 10: // 해골
	            switch (threshold) {
	                case 50:  return 500;
	                case 100: return 500;
	                case 300: return 500;
	                case 500: return 500;
	            }
	            break;
	        case 11: // 산적
	        case 12: // 도깨비
	        case 13: // 새끼용
	        	switch (threshold) {
	        	case 50:  return 600;
	        	case 100: return 600;
	        	case 300: return 600;
	        	case 500: return 600;
	        	}
	        	break;
	        case 14: // 리치
	        case 15: // 하급악마
	        case 16: // 
	        	switch (threshold) {
	        	case 50:  return 800;
	        	case 100: return 800;
	        	case 300: return 800;
	        	case 500: return 800;
	        	}
	        	break;
	    }
	    return 0;
	}
	
	/** 통산 킬수 업적 보상 */
	private int calcTotalKillReward(int threshold) {
	    switch (threshold) {
	        case 300:  return 100;
	        case 500:  return 300;
	        case 1000: return 500;
	        default:   return 0;
	    }
	}
	/**
	 * 몬스터별(50/100킬) + 통산 킬 업적 처리
	 * - room 단위로 동작
	 * - TBOT_POINT_RANK.CMD 기반 1회성 지급
	 */
	private String grantKillAchievements(String userName, String roomName) {
	    List<KillStat> ksList = botNewService.selectKillStats(userName, roomName);
	    if (ksList == null || ksList.isEmpty()) return "";

	    StringBuilder sb = new StringBuilder();
	    int totalKills = 0;

	 // 1) 몬스터별 업적 (각 MON_NO별)
	    int[] perMonThresholds = {50, 100, 300, 500, 1000};

	    for (KillStat ks : ksList) {
	        int monNo = ks.monNo;
	        int kills = ks.killCount;
	        totalKills += kills;

	        for (int th : perMonThresholds) {
	            if (kills >= th) {
	                String cmd = "ACHV_KILL" + th + "_MON_" + monNo;
	                int reward = calcPerMonsterKillReward(monNo, th);
	                sb.append(grantOnceIfEligible(userName, roomName, cmd, reward));
	            }
	        }
	    }

	    // 2) 통산 킬 업적
	    int[] totalThresholds = {50, 100, 300, 500, 1000};
	    for (int th : totalThresholds) {
	        if (totalKills >= th) {
	            String cmd = "ACHV_KILL_TOTAL_" + th;
	            int reward = calcTotalKillReward(th);
	            sb.append(grantOnceIfEligible(userName, roomName, cmd, reward));
	        }
	    }

	    return sb.toString();
	}

	private String grantCelebrationClearBonus(
	        String userName,
	        String roomName,
	        Map<String, Integer> globalAchvMap,
	        Map<String, Integer> userAchvMap
	) {

	    StringBuilder sb = new StringBuilder();

	    List<Monster> mons = botNewService.selectAllMonsters();

	    for (Monster m : mons) {

	        String firstCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;     // 최초토벌 기록
	        String userCmd  = "ACHV_CLEAR_BROADCAST_MON_" + m.monNo; // 유저 축하보상 기록

	        // 1) 해당 몬스터가 최초토벌된 적이 있는가? (글로벌)
	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get(firstCmd);
	            if (v != null) globalCnt = v.intValue();
	        }
	        if (globalCnt == 0) {
	            // 아직 아무도 이 몬스터를 최초토벌하지 않음 → 축하 보상 X
	            continue;
	        }

	        // 2) 나는 축하보상을 이미 받았는가? (유저 기준)
	        int mine = 0;
	        if (userAchvMap != null) {
	            Integer mineCnt = userAchvMap.get(userCmd);
	            if (mineCnt != null) mine = mineCnt.intValue();
	        }
	        if (mine > 0) {
	            // 이미 이 몬스터에 대한 축하 보상을 받은 상태
	            continue;
	        }

	        // 3) 최초토벌 보상의 1/3 계산
	        int rewardFull   = calcFirstClearReward(m.monNo);
	        int rewardShared = Math.max(1, rewardFull / 3);

	        // 4) 축하 보상 지급
	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score", rewardShared);
	        pr.put("cmd", userCmd);
	        botNewService.insertPointRank(pr);

	        // (선택) 메모리 캐시 업데이트
	        if (userAchvMap != null) {
	            userAchvMap.put(userCmd, mine + 1);
	        }

	        sb.append("✨ [")
	          .append(m.monName)
	          .append("] 최초토벌 축하 보상 +")
	          .append(rewardShared).append("sp 지급되었습니다!")
	          .append(NL);
	    }

	    return sb.toString();
	}


	private int calcFirstClearReward(int monNo) {
	    switch(monNo) {
	        case 1: case 2: case 3: case 4: case 5: return 100;
	        case 6: return 300;
	        case 7: return 500;
	        case 8: return 500;
	        case 9: return 1000;
	        case 10: return 1000;
	        case 11: return 1000;
	        case 12: return 1000;
	        case 13: return 1500;
	        case 14: return 1500;
	        case 15: return 2000;
	        case 16: return 2000;
	        case 17: return 2500;
	        case 18: return 2500;
	        case 19: return 3000;
	        case 20: return 3000;
	    }
	    return 0;
	}
	
	/** 업적 CMD → 단순 업적명 라벨 (보상/날짜 없이) */
	private String formatAchievementLabelSimple(String cmd) {
	    if (cmd == null || cmd.isEmpty()) return "";

	    // 최초토벌
	    if (cmd.startsWith("ACHV_FIRST_CLEAR_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_FIRST_CLEAR_MON_".length()));
	            Monster m = botNewService.selectMonsterByNo(monNo);
	            return "✨최초토벌: " + (m == null ? ("몬스터#" + monNo) : m.monName);
	        } catch (Exception e) {
	            return "최초토벌";
	        }
	    }
	    if (cmd.startsWith("ACHV_CLEAR_BROADCAST_MON_")) {
	    	try {
	    		int monNo = Integer.parseInt(cmd.substring("ACHV_CLEAR_BROADCAST_MON_".length()));
	    		Monster m = botNewService.selectMonsterByNo(monNo);
	    		return "✨축하보상: " + (m == null ? ("몬스터#" + monNo) : m.monName);
	    	} catch (Exception e) {
	    		return "축하보상";
	    	}
	    }

	    // 몬스터별 킬 업적
	    if (cmd.startsWith("ACHV_KILL") && cmd.contains("_MON_")) {
	        try {
	            String[] parts = cmd.substring("ACHV_KILL".length()).split("_MON_");
	            int threshold = Integer.parseInt(parts[0]);
	            int monNo = Integer.parseInt(parts[1]);
	            Monster m = botNewService.selectMonsterByNo(monNo);
	            String name = (m == null ? ("몬스터#" + monNo) : m.monName);
	            return name + " " + threshold + "킬 달성";
	        } catch (Exception e) {
	            return "킬 업적";
	        }
	    }

	    // 통산 킬 업적
	    if (cmd.startsWith("ACHV_KILL_TOTAL_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_KILL_TOTAL_".length()));
	            return "통산 처치 " + th + "회 달성";
	        } catch (Exception e) {
	            return "통산 업적";
	        }
	    }
	    // 데스 업적
	    if (cmd.startsWith("ACHV_DEATH_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_DEATH_".length()));
	    		return "죽음 극복 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "죽음 업적";
	    	}
	    }

	    return cmd;
	}
	// BossAttackController 내부에 추가 (필드/DI 그대로 사용)
	private String grantDeathAchievements(String userName, String roomName) {
	    // 규칙: {사망누적, 보상SP}
	    final int[][] rules = new int[][]{
	        {1,   100},
	        {10,  200},
	        {50,  500},
	        {100, 1000}
	    };

	    StringBuilder sb = new StringBuilder();
	    int deaths = 0;

	    try {
	        AttackDeathStat stat = botNewService.selectAttackDeathStats(userName, roomName);
	        deaths = (stat == null ? 0 : stat.getTotalDeaths());
	    } catch (Exception ignore) { /* 안전무시 */ }

	    for (int[] r : rules) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (deaths >= threshold) {
	            String cmd = "ACHV_DEATH_" + threshold;
	            int already = 0;
	            try {
	                already = botNewService.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
	            } catch (Exception ignore) {}

	            if (already == 0) {
	                try {
	                    HashMap<String, Object> p = new HashMap<>();
	                    p.put("userName", userName);
	                    p.put("roomName", roomName);
	                    p.put("score", rewardSp);
	                    p.put("cmd", cmd);
	                    botNewService.insertPointRank(p);

	                    sb.append("✨ 죽음 ").append(threshold)
	                      .append("회 달성 보상 +").append(rewardSp)
	                      .append("sp 지급!♬");
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    return sb.toString();
	}
	
	private int calcUserEffectiveAtkMax(User u, String roomName) {

	    // -------------------------------
	    // 1) 기본값
	    // -------------------------------
	    int atkMax = u.atkMax;
	    final String job = (u.job == null ? "" : u.job.trim());

	    // -------------------------------
	    // 2) MARKET 아이템 버프 
	    //    (selectOwnedMarketBuffTotals 사용)
	    // -------------------------------
	    HashMap<String, Number> buffs = null;
	    try {
	        buffs = botNewService.selectOwnedMarketBuffTotals(u.userName, roomName);
	    } catch(Exception ignore){}

	    int bAtkMax = (buffs != null && buffs.get("ATK_MAX") != null)
	                    ? buffs.get("ATK_MAX").intValue()
	                    : 0;

	    atkMax += bAtkMax;

	    // -------------------------------
	    // 3) 무기 강화 (selectWeaponLvCheck 사용)
	    // -------------------------------
	    int weaponLv = 0;
	    try {
	    	HashMap<String,Object> map =new HashMap<>();
	    	map.put("userName", u.userName);
	    	map.put("roomName", roomName);
	        int w = botService.selectWeaponLvCheck(map);
	        weaponLv = w;
	    } catch (Exception ignore) {}

	    int weaponBonus = getWeaponAtkBonus(weaponLv); // 25강부터 +1
	    // 네 구조: max ATK 는 무기레벨 만큼 +1 per level
	    atkMax += weaponBonus;

	    // -------------------------------
	    // 4) 운영자의 축복: Lv7 이하 → ATK +3
	    // -------------------------------
	    if (u.lv <= 15) {
	        //atkMax += 3;
	    }

	    // -------------------------------
	    // 5) 직업 패시브
	    // -------------------------------

	    // 전사: HP 기반 공격력 비례 (최대 2배)
	    if ("전사".equals(job)) {
	        // 체력 0%~100% → *1.0 ~ 2.0
	        double hpRate = (u.hpCur <= 0 ? 0 : (double)u.hpCur / (double)u.hpMax);
	        double mul = 1.0 + (hpRate);   // 0% =1.0 , 100% =2.0
	        atkMax = (int)Math.round(atkMax * mul);
	    }

	    // 마법사: 패턴3 무시 시(여기서는 반영 X), 기본적으로 보정 없음
	    // 도적: 스틸 / 회피 (공격력 보정 없음)
	    // 상인: 공격력 보정 없음
	    // 프리스트: 공격력 보정 없음
	    // 궁수: 저격은 dmg 보정이며 min/max에는 영향 없음

	    // -------------------------------
	    // 6) 최소 하한선
	    // -------------------------------
	    if (atkMax < 1) atkMax = 1;

	    return atkMax;
	}
	private DosaBuffEffect loadRoomDosaBuffAndBuild(String roomName) {
	    HashMap<String,Object> dosaBuff = botNewService.selectDosaBuffInfo(roomName);
	    if (dosaBuff == null) return null;

	    String dosaName = (String)dosaBuff.get("USER_NAME");
	    User dosaUser   = botNewService.selectUser(dosaName, roomName);

	    int dosaLv = 1;
	    try {
	        dosaLv = Integer.parseInt(dosaBuff.get("LV").toString());
	    } catch (Exception ignore) {}

	    return buildDosaBuffEffect(dosaUser, dosaLv, roomName);
	}
	
	private DosaBuffEffect buildDosaBuffEffect(User dosaUser, int dosaLv, String roomName) {
	    DosaBuffEffect eff = new DosaBuffEffect();

	    int dosaAtkMax = calcUserEffectiveAtkMax(dosaUser, roomName);

	    int dosaLvBonus = (int) Math.round(dosaLv * 0.5);
	    int dosaCriDmg  = (int) Math.round(dosaAtkMax * 0.2);

	    eff.addAtkMin   = dosaLvBonus;
	    eff.addAtkMax   = dosaLvBonus;
	    eff.addCritRate = dosaLvBonus;
	    eff.addCritDmg  = dosaCriDmg;
	    eff.addHp       = dosaLvBonus;
	    eff.msg = "✨ 도사의 버프 발동! (Lv " + dosaLv +
	              ") min+" + dosaLvBonus +
	              " max+" + dosaLvBonus +
	              ", cri+" + dosaLvBonus +
	              ", hp+" + dosaLvBonus +
	              ", cridmg +" + dosaCriDmg + "%";

	    return eff;
	}
	
	public static class DosaBuffEffect {
	    public int addAtkMin;
	    public int addAtkMax;
	    public int addCritRate;
	    public int addCritDmg;
	    public int addHp;
	    public String msg;
	}


	/**
	 * 데미지 전체 처리 전용 메서드 (A형: 완전 분리형)
	 * - 공격력 굴림, 크리티컬, 원턴킬 판정
	 * - calcDamage 호출
	 * - 마법사 패턴3 무력화
	 * - 전사 필살기 패링
	 * - 도적 회피
	 * - 프리스트 피해 감소
	 * - 전사 방패
	 * - 흡혈귀 흡혈
	 */
	private DamageOutcome calculateDamage(
	        User u,
	        Monster m,
	        String job,
	        Flags flags,
	        int effAtkMin,
	        int effAtkMax,
	        int effCritRate,
	        int effCriDmg,
	        double berserkMul,
	        int monHpRemainBefore,
	        int effHpMax
	) {
	    DamageOutcome out = new DamageOutcome();
	    AttackCalc calc = new AttackCalc();
	    calc.jobSkillUsed = false;

	    StringBuilder extraMsg = new StringBuilder();

	    // -----------------------------
	    // 1) 공격력 굴림 + 크리티컬
	    // -----------------------------
	    int critRoll = ThreadLocalRandom.current().nextInt(0, 101);
	    int critThreshold = Math.min(100, effAtkMin < 0 ? 0 : effAtkRateLimit(effCritRate)); // 안전빵 방어
	    boolean crit = (critRoll <= critThreshold);

	    int baseAtkRangeMin = (int) Math.round(effAtkMin * berserkMul);
	    int baseAtkRangeMax = (int) Math.round(effAtkMax * berserkMul);
	    if (baseAtkRangeMax < baseAtkRangeMin) baseAtkRangeMax = baseAtkRangeMin;

	    int baseAtk = (baseAtkRangeMax <= baseAtkRangeMin)
	            ? baseAtkRangeMin
	            : ThreadLocalRandom.current().nextInt(baseAtkRangeMin, baseAtkRangeMax + 1);

	   
	    
	    // -----------------------------
	    // 2) 궁수 저격, 프리스트 스켈레톤 추가뎀
	    // -----------------------------
	    boolean isSnipe = false;
	    if ("궁수".equals(job)) {
	        if (ThreadLocalRandom.current().nextDouble() < 0.065) {
	            isSnipe = true;
	            baseAtk = baseAtk * 20;
	            calc.jobSkillUsed = true;
	        }
	    }

	    if ("프리스트".equals(job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 1.25);
	    }
	    
	    
	    double critMultiplier = Math.max(1.0, effCriDmg / 100.0);
	    int rawAtkDmg = crit ? (int) Math.round(baseAtk * critMultiplier) : baseAtk;

	    // -----------------------------
	    // 3) 원턴킬 선판정
	    // -----------------------------
	    boolean lethal = rawAtkDmg >= monHpRemainBefore;

	    if (lethal) {
	        flags.atkCrit = crit;
	        flags.monPattern = 0;
	        flags.snipe = isSnipe;

	        calc.atkDmg = rawAtkDmg;
	        calc.monDmg = 0;
	        calc.patternMsg = null;

	        if (crit) {
	            calc.baseAtk = baseAtk;
	            calc.critMultiplier = critMultiplier;
	        }

	    } else {
	        // -----------------------------
	        // 4) 보스 패턴 포함 실제 데미지 계산
	        // -----------------------------
	        calc = calcDamage(u, m, flags, baseAtk, crit, critMultiplier);

	        flags.atkCrit = crit;
	        flags.snipe = isSnipe;
	        flags.finisher = (flags.monPattern == 4); // 패턴4=필살기

	        // 🔥 마법사: 패턴3 방어를 깨뜨리고 1.5배 피해
	        if ("마법사".equals(job) && flags.monPattern == 3) {
	        	// 패턴3 → 방어 대신 무행동 취급
	            flags.monPattern = 1;

	            // ✅ 방어 적용 전 기준( baseAtk * critMultiplier )으로 다시 계산
	            int originalDmg = (int) Math.round(calc.baseAtk * calc.critMultiplier);

	            int newDmg = (int) Math.round(originalDmg * 2.0);
	            calc.atkDmg = newDmg;
	            calc.monDmg = 0;  // 방어 패턴이었으니 몬스터 피해는 0 유지

	            // 디버그용 계수도 실제 데미지에 맞게 재계산
	            if (calc.baseAtk > 0) {
	                calc.critMultiplier = (double) newDmg / calc.baseAtk;
	            }

	            calc.patternMsg = m.monName + "의 방어가 마법사의 힘에 의해 무너졌습니다! (피해 2배)";
	        }

	        // 🛡 전사: 보스 필살기 패링 (20% 확률)
	        if ("전사".equals(job) && flags.finisher && calc.monDmg > 0 && m.monLv > u.lv) {
	            if (ThreadLocalRandom.current().nextDouble() < 0.20) {

	                int bossSkillDmg = calc.monDmg;             // 보스 필살기 데미지
	                int reflectTotal = calc.atkDmg + bossSkillDmg; // 되돌려줄 총 피해

	                calc.atkDmg += bossSkillDmg;  // 되받아친 만큼 공격에 누적
	                calc.monDmg = 0;              // 나는 피해 없음

	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	                calc.patternMsg = baseMsg
	                        + "패링! 보스의 필살기를 되받아쳐 총 "
	                        + reflectTotal + " 피해를 입히고 피해를 받지 않았습니다.";

	                calc.jobSkillUsed = true;
	            }
	        }

	        // 🌀 도적: 회피 (고레벨 보스일수록 회피율 감소, 필살기 제외)
	        if ("도적".equals(job) && calc.monDmg > 0 && !flags.finisher) {

	            int monLv = m.monNo;
	            double evadeRate = 0.40;
	            switch (monLv) {
	                case 15:
	                    evadeRate -= 0.05;
	                case 14:
	                    evadeRate -= 0.05;
	                case 13:
	                    evadeRate -= 0.05;
	                case 12:
	                    evadeRate -= 0.05;
	            }

	            if (ThreadLocalRandom.current().nextDouble() < evadeRate) {
	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	                calc.patternMsg = baseMsg + "도적의 회피! 피해를 받지 않았습니다.";
	                calc.monDmg = 0;
	            }
	        }

	        if ("프리스트".equals(job) && calc.monDmg > 0 && !flags.finisher) {
	            int reduced = (int) Math.floor(calc.monDmg * 0.8);
	            if (reduced < 1) reduced = 1;
	            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = baseMsg + "(받는 피해 20% 감소 → " + reduced + ")";
	            calc.monDmg = reduced;
	        }

	        // 🛡 전사: 일반 패턴 피해 감소
	        if ("전사".equals(job) && calc.monDmg > 0 && !flags.finisher) {
	            int reduce = (int) Math.round(u.lv * 2);
	            int after = Math.max(0, calc.monDmg - reduce); // 최소 0
	            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = baseMsg
	                    + "(전사의방패 효과로 " + reduce + " 피해 감소 → " + after + ")";
	            calc.monDmg = after;
	        }
	    }

	    // -----------------------------
	    // 5) 흡혈귀: 이번 턴 실제 입힌 피해의 20% 회복
	    // -----------------------------
	    if ("흡혈귀".equals(job) && calc.atkDmg > 0) {

	        if (m.monNo == 10 || m.monNo == 14) {
	            String base = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = base + "언데드는 흡혈 불가";
	        } else {
	            // 몬스터가 실제로 잃은 체력만큼만 흡혈 가능
	            int realDamage = Math.min(calc.atkDmg, monHpRemainBefore);
	            int heal = (int) Math.round(realDamage * 0.20);
	            if (heal < 1) heal = 1;

	            int before = u.hpCur;
	            u.hpCur = Math.min(effHpMax, u.hpCur + heal);

	            String base = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = base + "흡혈 효과! " + heal +
	                    " 회복 (HP " + before + " → " + u.hpCur + "/" + effHpMax + ")";
	            calc.jobSkillUsed = true;
	        }
	    }

	    out.calc = calc;
	    out.flags = flags;
	    out.willKill = (calc.atkDmg >= monHpRemainBefore);
	    out.extraMsg = extraMsg.toString();

	    return out;
	}

	// 크리율 방어용 헬퍼 (0~100 clamp 용)
	private int effAtkRateLimit(int rate) {
	    if (rate < 0) return 0;
	    if (rate > 100) return 100;
	    return rate;
	}

	
	// 직업 공통 정의
	private static final class JobDef {
	    final String name;       // 표기 이름 (전사, 궁수, ...)
	    final String listLine;   // /직업 안내용 한 줄
	    final String attackLine; // 공격정보용 한 줄

	    JobDef(String name, String listLine, String attackLine) {
	        this.name = name;
	        this.listLine = listLine;
	        this.attackLine = attackLine;
	    }
	}
	
	// 직업 메타데이터 맵 (등록 순서 유지 위해 LinkedHashMap)
	private static final Map<String, JobDef> JOB_DEFS = new LinkedHashMap<>();

	static {
	    // NL은 클래스에 이미 있는 상수라고 가정하고 그대로 사용
	    JOB_DEFS.put("전사", new JobDef(
	        "전사",
	        "▶ 전사 :육체능력이 변경되며, 강한적을 상대하면 강해진다",
	        "⚔ 기본 HP만큼 추가 증가, 방어 추가, 자신보다 몬스터 lv이 높을때 [버서크모드(50%이하부터,점점 강해짐 데미지 최대 2배), -hidden-] 활성화"
	    ));

	    JOB_DEFS.put("궁수", new JobDef(
	        "궁수",
	        "▶ 궁수 :사냥감을 조준하는 집요한 추적자, 강력한 한방을 선사하지만, 쿨타임이 길어진다",
	        "⚔ 최종 데미지 ×1.8, 쿨타임 5분, EXP +15%, 공격시 6.5%확률로 강력한공격"
	    ));

	    JOB_DEFS.put("마법사", new JobDef(
	        "마법사",
	        "▶ 마법사 :강력한 마법공격으로 몬스터의 방어태세를 무력화한다",
	        "⚔ 몬스터가 방어시 방어를 무시하고 피해 2배를 줌"
	    ));

	    JOB_DEFS.put("도적", new JobDef(
	        "도적",
	        "▶ 도적 :날렵한 손놀림으로 적의공격을 피하며,아이템을 강탈한다",
	        "⚔ 공격 시 25% 확률 추가 드랍(STEAL), 몬스터 기본 공격 40% 회피, [스틸,회피 no12부터 3%씩 감소] "
	    ));

	    JOB_DEFS.put("프리스트", new JobDef(
	        "프리스트",
	        "▶ 프리스트 :대사제의 축복을 받아 신성의힘으로 적을 물리친다",
	        "⚔ 아이템 HP/리젠 효과 1.25배, 몬스터에게 받는 피해 감소(20%), 언데드추가피해(+25%)"
	    ));
	    /*
	    JOB_DEFS.put("상인", new JobDef(
	        "상인",
	        "▶ 상인 :떠도는 몬스터에게 현금을 갈취하며, 상점 거래의 달인",
	        "⚔ 상점 구매 10% 할인, 드랍 판매가 10% 증가, 공격시 몬스터 드롭템의 20%에 해당하는 SP 추가 획득"
	    ));
 		*/
	    JOB_DEFS.put("도사", new JobDef(
	        "도사",
	        "▶ 도사 :도를 닦아 깨달음을 얻은 위인",
	        "⚔ 다음 공격하는 아군 강화(레벨*0.5만큼 능력강화,맥뎀*0.2만큼 치명뎀강화, 자신의 럭키몬스터 등장 확률 증가"
	    ));
	    /*
	    JOB_DEFS.put("기사", new JobDef(
            "기사",
            "▶ 기사 :방패로 몬스터의 공격을 방어하는 굳건한 기사",
            "⚔ 공격력만큼 몬스터 공격을 방어하며, 방어량만큼 자신의 공격력이 감소"
        ));
	     */
        JOB_DEFS.put("사신", new JobDef(
            "사신",
            "▶ 사신 :이름하야 죽음의 신, 죽지않는다",
            "⚔ 아이템으로 인한 치명타,치명타뎀 증감처리 미적용, 체력 0에서도 죽지 않음,10%미만 체력에서 치명타확률50%증가"
        ));
        
        JOB_DEFS.put("흡혈귀", new JobDef(
            "흡혈귀",
            "▶ 흡혈귀 :배가고프다, 나는 배가 고프다!",
            "⚔ 공격시 준피해의 20% 흡혈(공격&흡혈 선계산, 후피해), hp리젠 아이템의 증감처리 미적용"
        ));
	}
	
	
	
}



