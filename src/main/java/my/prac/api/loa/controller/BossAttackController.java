package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackCalc;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BagLog;
import my.prac.core.game.dto.BagRewardLog;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.DamageOutcome;
import my.prac.core.game.dto.Flags;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
import my.prac.core.game.dto.UserBattleContext;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;
import my.prac.core.util.MiniGameUtil;

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
	private static final int BAG_ITEM_ID = 91;
	
	/* ===== DI ===== */
	@Autowired LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")        BotService botService;
	@Resource(name = "core.prjbot.BotDAO")            BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewService")     BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")  BotSettleService botSettleService;
	
	
	
	
	public String changeMode(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String selRaw  = Objects.toString(map.get("param1"), "").trim();

	    String msg = "";
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    User u = botNewService.selectUser(userName, null);
	    if (u == null)
	        return "유저 정보를 찾을 수 없습니다.";

	    if(selRaw.equals("나이트메어")||selRaw.equals("나메")) {
	    	botNewService.setNightmareMode(userName,roomName,true);
	    	msg ="나이트메어";
	    }else {
	    	botNewService.setNightmareMode(userName,roomName,false);
	    	msg="일반";
	    }
	    botNewService.closeOngoingBattleTx(userName, roomName);
		return msg+" 모드로 변경완료"+NL+"[일반/나이트메어] 선택가능";
	}
	
	public String roulette(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");

	    if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";

	    // 문의방 제한 동일 패턴
	    if ("람쥐봇 문의방".equals(roomName) && !"일어난다람쥐/카단".equals(userName)) {
	        return "문의방에서는 불가능합니다.";
	    }

	    try {
	        // 1) 오늘 이미 돌렸는지 확인
	        HashMap<String, Object> today = botNewService.selectTodayDailyBuff(userName, roomName);
	        if (today != null && !today.isEmpty()) {
	            int atk  = safeInt(today.get("ATK_BONUS"));
	            int cdmg = safeInt(today.get("CRI_DMG_BONUS"));
	            return " " + userName + "님, 오늘은 이미 룰렛을 돌렸습니다." + NL
	                 + "오늘의 버프: ATK +" + atk + ", CDMG +" + cdmg + "%" + NL
	                 + "(자정에 초기화됩니다)";
	        }

	        // 2) 새로 뽑기
	        int atkBonus = ThreadLocalRandom.current().nextInt(10, 101);    // 10~100
	        int cdmgBonus = ThreadLocalRandom.current().nextInt(30, 301);  // 30~300

	        botNewService.upsertTodayDailyBuff(userName, roomName, atkBonus, cdmgBonus);

	        return " " + userName + "님, 룰렛 결과!" + NL
	             + "오늘의 버프: ATK +" + atkBonus + ", CDMG +" + cdmgBonus + "%" + NL
	             + "(자정에 초기화됩니다)";
	    } catch (Exception e) {
	        e.printStackTrace();
	        return "룰렛 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	    }
	}

	public String bagLog(HashMap<String, Object> map) {
		List<BagLog> logs = botNewService.selectRecentBagDrops();
		List<BagRewardLog> rewards = botNewService.selectRecentBagRewards();

		if ((logs == null || logs.isEmpty()) && (rewards == null || rewards.isEmpty())) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm");

		// 1) 가방 획득 로그 (기존)
		if (logs != null && !logs.isEmpty()) {
			sb.append("최근 가방 획득 로그 ").append(NL);
			for (BagLog log : logs) {
				String when = (log.getInsertDate() != null ? fmt.format(log.getInsertDate()) : "-");
				sb.append("- ").append(when).append(" : ").append(log.getUserName()).append("님이 가방을 획득~!").append(NL);
			}
			sb.append(NL);
		}

		// 2) 가방 보상 로그 (SP/아이템)
		if (rewards != null && !rewards.isEmpty()) {
			sb.append("최근 가방 보상 로그 ").append(NL);
			for (BagRewardLog r : rewards) {
				String when = (r.getInsertDate() != null ? fmt.format(r.getInsertDate()) : "-");
				sb.append("- ").append(when).append(" : ").append(r.getUserName()).append("님이 ").append(r.getGain())
						.append(" 획득!").append(NL);
			}
		}

		return sb.toString();
	}
	
	private UserBattleContext calcUserBattleContext(HashMap<String, Object> map) {
	    UserBattleContext ctx = new UserBattleContext();

	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String param1   = Objects.toString(map.get("param1"), "").trim();

	    ctx.roomName = roomName;
	    ctx.userName = userName;
	    ctx.param1   = param1;

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        ctx.success = false;
	        ctx.errorMessage = "방/유저 정보가 누락되었습니다.";
	        return ctx;
	    }

	    // ① param1으로 다른 유저 조회 시도 (두 메서드 동일 로직)
	    String targetUser = userName;
	    if (!param1.isEmpty()) {
	        List<String> newUserName = botNewService.selectParam1ToNewUserSearch(map);
	        if (newUserName != null && !newUserName.isEmpty()) {
	            targetUser = newUserName.get(0);
	        } else {
	            ctx.success = false;
	            ctx.errorMessage = "해당 유저(" + param1 + ")를 찾을 수 없습니다.";
	            return ctx;
	        }
	    }
	    ctx.targetUser = targetUser;

	    // ② 유저 조회
	    User u = botNewService.selectUser(targetUser,null);
	    if (u == null) {
	        ctx.success = false;
	        ctx.errorMessage = "❌ 유저 정보를 찾을 수 없습니다.";
	        return ctx;
	    }

	    ctx.isReturnUser = false; //botNewService.isReturnUser(targetUser);
	    
	    ctx.user = u;
	    ctx.job  = (u.job == null ? "" : u.job.trim());

	    // (선택) 현재 포인트 / 누적 SP도 여기서 같이 조회해두고 싶으면:
	    try {
	        Integer p = botNewService.selectCurrentPoint(targetUser, roomName);
	        ctx.currentPoint = (p == null ? 0 : p);
	    } catch (Exception ignore) {
	        ctx.currentPoint = 0;
	    }

	    try {
	        Integer t = botNewService.selectTotalEarnedSp(targetUser, roomName);
	        ctx.lifetimeSp = (t == null ? 0 : t);
	    } catch (Exception ignore) {
	        ctx.lifetimeSp = 0;
	    }

	    final String job = ctx.job;

	    // 1) MARKET 장비 버프 raw
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
	    int bHpMaxRateRaw  = (buffs != null && buffs.get("HP_MAX_RATE")   != null) ? buffs.get("HP_MAX_RATE").intValue()   : 0;
	    int bAtkMaxRateRaw  = (buffs != null && buffs.get("ATK_MAX_RATE")   != null) ? buffs.get("ATK_MAX_RATE").intValue()   : 0;

	    // 🔹 직업 보너스 표시용 변수
	    int jobHpMaxBonus = 0;
	    int jobRegenBonus = 0;
	    
	    // 사신: 아이템으로 인한 크리/크리뎀 효과 미적용
	    /*
	    if ("사신".equals(job)) {
	        bCriRaw    = 0;
	        bCriDmgRaw = 0;
	        // (주석상 HP까지 막고 싶으면 bHpMaxRaw = 0; 도 여기서 처리)
	    }
	     */
	    // 프리스트: 아이템 HP/리젠 1.25배 (monsterAttack 기준으로 맞춤)
	    if ("프리스트".equals(job)) {
	    	int hpBase   = bHpMaxRaw;
	        int regenBase= bRegenRaw;

	        bHpMaxRaw  = (int) Math.round(bHpMaxRaw * 1.25);
	        bRegenRaw  = (int) Math.round(bRegenRaw * 1.25);

	        jobHpMaxBonus = bHpMaxRaw  - hpBase;
	        jobRegenBonus = bRegenRaw  - regenBase;
	    }
	    if ("어둠사냥꾼".equals(job)) {
	    	int hpBase   = bHpMaxRaw;
	        int regenBase= bRegenRaw;

	        bHpMaxRaw  = (int) Math.round(bHpMaxRaw * 1.25);
	        bRegenRaw  = (int) Math.round(bRegenRaw * 1.25);

	        jobHpMaxBonus = bHpMaxRaw  - hpBase;
	        jobRegenBonus = bRegenRaw  - regenBase;
	    }
	    
	    if ("용기사".equals(job)) {
	    	bHpMaxRaw  = (int) Math.round(bHpMaxRaw * 2);
	        bRegenRaw  = (int) Math.round(bRegenRaw * 2);
	    }

	    // 기본 스탯
	    int baseMin     = u.atkMin;
	    int baseMax     = u.atkMax;
	    int baseHpMax   = u.hpMax;
	    int baseRegen   = u.hpRegen;
	    int baseCrit    = u.critRate;
	    int baseCritDmg = u.critDmg;

	    ctx.baseMin     = baseMin;
	    ctx.baseMax     = baseMax;
	    ctx.baseHpMax   = baseHpMax;
	    ctx.baseRegen   = baseRegen;
	    ctx.baseCritRate= baseCrit;
	    ctx.baseCritDmg = baseCritDmg;

	    ctx.bAtkMinRaw  = bAtkMinRaw;
	    ctx.bAtkMaxRaw  = bAtkMaxRaw;
	    ctx.bCriRaw     = bCriRaw;
	    ctx.bRegenRaw   = bRegenRaw;
	    ctx.bHpMaxRaw   = bHpMaxRaw;
	    ctx.bCriDmgRaw  = bCriDmgRaw;
	    ctx.bHpMaxRateRaw  = bHpMaxRateRaw;
	    ctx.bAtkMaxRateRaw  = bAtkMaxRateRaw;

	    // ② 무기강/보너스 조회
	    HashMap<String, Object> wm = new HashMap<>();
	    wm.put("userName", targetUser);
	    wm.put("roomName", roomName);
	    //int weaponLv = 0;
	    
	    //int weaponBonus = getWeaponAtkBonus(weaponLv); // 25강부터 +1

	    //ctx.weaponLv     = weaponLv;
	    //ctx.weaponBonus  = weaponBonus;

	    int atkMinWithItem = baseMin + bAtkMinRaw;
	    int atkMaxWithItem = baseMax + bAtkMaxRaw;

	    // 3) 운영자의 축복
	    /*
	    boolean hasBless = (u.lv <= 15);
	    int blessRegenBonus = hasBless ? 5 : 0;
	    ctx.hasBless          = hasBless;
	    ctx.blessRegenBonus   = blessRegenBonus;
	     */
	    // 🩸 흡혈귀: monsterAttack 캐논 기준으로 "아이템 리젠만" 무효
	    if ("흡혈귀".equals(job)) {
	        bRegenRaw = 0;
	    }

	    // 4) 최종 HP
	    int finalHpMax = baseHpMax + bHpMaxRaw;
	    int finalRegen = baseRegen + bRegenRaw;
	    if ("전사".equals(job)) {
	        //finalHpMax += baseHpMax*10; // 기본 HP 추가
	    }
	    if ("검성".equals(job)) {
	        finalHpMax += baseHpMax*2; // 기본 HP 추가
	    }
	    if ("용사".equals(job)) {
	    	finalHpMax += baseHpMax*2; // 기본 HP 추가

	        jobHpMaxBonus = baseHpMax*2;
	    }
	    if ("저격수".equals(job)) {
	        finalHpMax = finalHpMax/2; // 기본 HP 추가
	    }
	    if (finalHpMax <= 0) finalHpMax = 1;

	    // 5) 최종 리젠 (기본+아이템+축복)
	    int effRegen = finalRegen + jobRegenBonus;
	    if (effRegen < 0) effRegen = 0;

	    // 6) 파이터: HP 추가 보정
	    if ("파이터".equals(job)) {
	        finalHpMax += atkMaxWithItem * 3;
	        finalHpMax += effRegen * 3;
	        finalHpMax += (baseCritDmg + bCriDmgRaw) * 3;
	    }
	    // 7) 파이터: 증가된 HP 기반 공격력 재보정 ★ 새로 추가 ★
	    if ("파이터".equals(job)) {

	        int shownCrit    = baseCrit + bCriRaw;
	        int shownCritDmg = baseCritDmg + bCriDmgRaw;

	        int fighterAtkBonus = (atkMaxWithItem + shownCrit + shownCritDmg) * 3;

	        atkMinWithItem += fighterAtkBonus;
	        atkMaxWithItem += fighterAtkBonus;

	        // 파이터는 크리 기반 능력 삭제
	        ctx.shownCrit    = 0;
	        ctx.shownCritDmg = 0;
	    }
	    

	  

	 // ✅ 오늘 룰렛 버프(개인형, 00시 초기화: TRUNC(SYSDATE) 기준)
	    int dailyAtkBonus  = 0;
	    int dailyCdmgBonus = 0;
	    try {
	        HashMap<String,Object> b = botNewService.selectTodayDailyBuff(targetUser, roomName);
	        if (b != null && !b.isEmpty()) {
	            dailyAtkBonus  = safeInt(b.get("ATK_BONUS"));
	            dailyCdmgBonus = safeInt(b.get("CRI_DMG_BONUS"));
	        }
	    } catch (Exception ignore) {}

	    
	    
	    // ctx에 저장(attackInfo 노출용)
	    ctx.dailyAtkBonus     = dailyAtkBonus;
	    ctx.dailyCriDmgBonus  = dailyCdmgBonus;

	    // 실제 스탯에 반영 (공격력 +, 크리뎀 +)
	    atkMinWithItem += dailyAtkBonus;
	    atkMaxWithItem += dailyAtkBonus;
	    bCriDmgRaw     += dailyCdmgBonus; // shownCritDmg 계산에 자연스럽게 포함
	    

	 // ✅ 직업 마스터 보너스(오늘) : ATK+100, HP+1000
	    boolean isMaster = false;
	    try {
	        if (job != null && !job.trim().isEmpty()) {
	            isMaster = botNewService.selectIsTodayJobMasterAll(targetUser, job) > 0;
	        }
	    } catch (Exception ignore) {}

	    int jobMasterAtkRate = 0;
	    int jobMasterHpRate = 0;
	    int jobEffRegen = 0;
	    if (isMaster) {
	    	jobMasterAtkRate += 10;
	    	jobMasterHpRate  += 15;
	    	jobEffRegen     += 1000;
	        ctx.isJobMaster = true;
	    } else {
	        ctx.isJobMaster = false;
	    }
	    
	    
	    int finalHpMaxBonus = (finalHpMax * (ctx.bHpMaxRateRaw+jobMasterHpRate)) /100;
	    finalHpMax += finalHpMaxBonus;
	    int atkMinWithItemBonus = (atkMinWithItem * (ctx.bAtkMaxRateRaw+jobMasterAtkRate)) /100;
	    atkMinWithItem += atkMinWithItemBonus;
	    int atkMaxWithItemBonus = (atkMaxWithItem * (ctx.bAtkMaxRateRaw+jobMasterAtkRate)) /100;
	    atkMaxWithItem += atkMaxWithItemBonus;
	    
	    effRegen += jobEffRegen;
	    
	    // HP/ATK 확정치 저장
	    ctx.atkMinWithItem = atkMinWithItem;
	    ctx.atkMaxWithItem = atkMaxWithItem;
	    ctx.finalHpMax  = finalHpMax;
	    ctx.effRegen    = effRegen;
	    
	    // 표시용 스탯 (1번 메서드에서 쓰던 값)
	    ctx.shownCrit     = baseCrit + bCriRaw;
	    ctx.shownRegen    = effRegen;                // 축복 포함 리젠을 그대로 표시하고 싶으면 이렇게
	    ctx.shownCritDmg  = baseCritDmg + bCriDmgRaw;

	    // 🔹 직업 보너스(표시용) 저장
	    ctx.jobHpMaxBonus = jobHpMaxBonus;
	    ctx.jobRegenBonus = jobRegenBonus;
	    
	    ctx.success = true;
	    
	    
	    applyDropBonusToContext(ctx, targetUser, roomName);

	    
	    return ctx;
	}
	/** 
	 */
	public String getHpStatus(HashMap<String,Object> map) {
		UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }
	    
	    // calcUserBattleContext 에서 채워준 공통 값들 재사용
	    final String targetUser = ctx.targetUser;
	    final String roomName   = ctx.roomName;
	    final User   u          = ctx.user;

	    final int finalHpMax = ctx.finalHpMax;  // 최종 HP
	    final int effRegen   = ctx.effRegen;    // 실제 적용 리젠(축복 포함/흡혈귀 처리 포함)
	    final boolean hasBless = ctx.hasBless;  // 운영자 축복 여부

	    // 6) 유효 체력 계산 (attackInfo와 동일 함수 사용)
	    int effHp = computeEffectiveHpFromLastAttack(targetUser, roomName, u, finalHpMax, effRegen);
	    if (effHp > finalHpMax) effHp = finalHpMax;

	    StringBuilder sb = new StringBuilder();
	    sb.append("❤️ ").append(targetUser).append("님의 체력 상태").append(NL)
	      .append("현재 체력: ").append(effHp).append(" / ").append(finalHpMax).append(NL)
	      .append("5분당 회복: +").append(effRegen).append(NL);

	    if (hasBless) {
	        sb.append("✨ 운영자의 축복 포함되어있음 (Lv 15 이하): 5분당 회복 +5").append(NL);
	    }

	    if (effHp <= finalHpMax * 0.05) {
	        sb.append("⚠️ 현재 공격 불가").append(NL);
	    } else if (effHp >= finalHpMax) {
	        sb.append("✅ 현재 체력은 최대 상태입니다.").append(NL);
	    }

	    // ✅ 회복 예측 스케줄 (예: 60분 범위 내)
	    //   buildRegenScheduleSnippetEnhanced2 시그니처:
	    //   (String targetUser, String roomName, User u,
	    //    int intervalMinutes, int effHp, int finalHpMax, int effRegen, int maxMinutes)
	    String regenInfo = buildRegenScheduleSnippetEnhanced2(
	            targetUser,
	            roomName,
	            u,
	            30,          // intervalMinutes
	            effHp,
	            finalHpMax,
	            effRegen,
	            60           // maxMinutes
	    );

	    if (regenInfo != null && !regenInfo.isEmpty()) {
	        sb.append(regenInfo);
	    }

	    // 🔹 여기서 "공격 로직"에서 쓰는 진행중 전투 계산 재사용
	    boolean nightmare = botNewService.isNightmareMode(targetUser, roomName);
	    try {
	        OngoingBattle ob = botNewService.selectOngoingBattle(targetUser, roomName);
	        if (ob != null) {
	            Monster m = botNewService.selectMonsterByNo(ob.monNo);
	            if (m != null) {
	                int monMaxHp    = m.monHp;
	                int monHpRemain = Math.max(0, m.monHp - ob.totalDealtDmg);

	                if(nightmare) {
	                	monMaxHp *=100;
	                }
	                
	                
	                sb.append(NL)
	                  .append("▶ 전투중인 몬스터").append(NL)
	                  .append(m.monName);
                    if(nightmare) {
                    	sb.append("[나이트메어]");
	                }
	                sb.append(" (").append(monHpRemain).append(" / ").append(monMaxHp).append(")")
	                  .append(NL);
	            }
	        } else {
	            // 진행중 전투는 없지만 타겟몬은 있을 수 있음 (선택)
	            Monster m = botNewService.selectMonsterByNo(u.targetMon);
	            if(nightmare) {
                	m.monHp *=100;
                }
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
	
	

	public String openBag(HashMap<String,Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }

	    // 1) 가방 개수 확인
	    int bagCount = botNewService.selectBagCount(userName, roomName);
	    if (bagCount <= 0) {
	        return "열 수 있는 가방이 없습니다.";
	    }
	    /*
	    // 2) 가방 1개 소비
	    int updated = botNewService.consumeOneBagTx(userName, roomName);
	    if (updated <= 0) {
	        return "가방을 사용하는 중 오류가 발생했습니다. 다시 시도해주세요.";
	    }
	     */    
	 // 🔹 한 번에 모두 소비
	    int updated = botNewService.consumeBagBulkTx(userName, roomName, bagCount);
	    if (updated <= 0) {
	        return "가방을 사용하는 중 오류가 발생했습니다.";
	    }
	    
	    
	    int totalSp = 0;
	    List<String> detail = new ArrayList<>();
	    List<String> itemSummary = new ArrayList<>();
	    
	    for (int i = 1; i <= bagCount; i++) {

	        double roll = ThreadLocalRandom.current().nextDouble();

	        if (roll < 0.90) {
	            int sp = rollBagSpWithCeiling(userName, roomName);
	            totalSp += sp;
	            detail.add("가방" + i + ": " + sp + "sp");
	        } else {

	            List<Integer> rewardItemIds =
	                    botNewService.selectBagRewardItemIdsUserNotOwned(userName, roomName);

	            if (rewardItemIds == null || rewardItemIds.isEmpty()) {
	                rewardItemIds = botNewService.selectBagRewardItemIds();
	            }

	            if (rewardItemIds == null || rewardItemIds.isEmpty()) {
	                int sp = rollBagSpWithCeiling(userName, roomName);
	                totalSp += sp;
	                detail.add("가방" + i + ": " + sp + "sp");
	                continue;
	            }

	            int itemId = rewardItemIds.get(
	                    ThreadLocalRandom.current().nextInt(rewardItemIds.size())
	            );

	            HashMap<String,Object> inv = new HashMap<>();
	            inv.put("userName", userName);
	            inv.put("roomName", roomName);
	            inv.put("itemId", itemId);
	            inv.put("qty", 1);
	            inv.put("delYn", "0");
	            inv.put("gainType", "BAG_OPEN");
	            botNewService.insertInventoryLogTx(inv);

	            HashMap<String,Object> info = botNewService.selectItemDetailById(itemId);
	            String itemName = Objects.toString(info.get("ITEM_NAME"), "");

	            String label = itemName;
	            if (itemId >= 9000 && itemId < 10000) {
	                String opt = buildEnhancedOptionLine(info, 1);
	                if (!opt.isEmpty()) label += opt;
	            }

	            itemSummary.add(label);
	            detail.add("가방" + i + ": " + label + " 획득");
	        }
	    }

	    // 🔹 SP는 합산해서 1번만 저장
	    if (totalSp > 0) {
	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score", totalSp);
	        pr.put("cmd", "BAG_OPEN_SP");
	        botNewService.insertPointRank(pr);
	    }

	    // 🔹 메시지 조립
	    StringBuilder sb = new StringBuilder();
	    sb.append("가방 ").append(bagCount).append("개를 열었습니다!").append(NL);

	    if (totalSp > 0) {
	        sb.append("✨ 총 획득: ").append(totalSp).append("sp").append(NL);
	    }

	    if (!itemSummary.isEmpty()) {
	        sb.append("✨ 아이템 획득: ").append(String.join(", ", itemSummary)).append(NL);
	    }

	    sb.append(NL).append("▶ 상세 내역").append(NL);
	    for (String d : detail) {
	        sb.append(d).append(NL);
	    }

	    return sb.toString();
	}
	
	private int rollBagSpWithCeiling(String userName, String roomName) {
		 // ① 유저의 BAG_OPEN_SP 기록 개수 조회
	    int totalCount = botNewService.selectBagOpenSpCount(userName, roomName);

	    

	    // 🔥 누적 SP 기반 상한 적용
		int cap = botNewService.selectBagRewardCap(userName);
	    cap = (int) Math.round(cap/2);
	    if(cap <50000) {
	    	cap = 100000;
	    }
	    
	    // ② 10개 미만이면 천장 적용 안 함 → 기본 200~100000 룰렛
	    if (totalCount < 10) {
	        return pickBiasedSp(5000, cap);
	    }

	    // ③ 최근 10개 SP 합계 조회
	    int recentSum = botNewService.selectRecentBagSpSum(userName, roomName);

	    // ④ 최근 10개 합계가 5만 미만일 때만 천장 발동
	    int minSp;

	    if (recentSum < 150000) {
	        minSp = 100000;   // 천장 발동: 50,000 ~ 100,000 룰렛
	    } else {
	        minSp = 5000;     // 평소 확률
	    }

	    return pickBiasedSp(minSp, cap);
	}

	/* ===== Public APIs ===== */
	public String changeJob(HashMap<String,Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String selRaw  = Objects.toString(map.get("param1"), "").trim();

	    boolean master =false;
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    User u = botNewService.selectUser(userName, null);
	    if (u == null)
	        return "유저 정보를 찾을 수 없습니다.";

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
	    
	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				master =true;
			}else {
				return "문의방에서는 불가능합니다.";
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

	    if(!master) {
	    	// 5-0) 해당 유저의 직업별 공격횟수 전체 조회 (쿼리 1번)
		    Map<String, Integer> jobCntMap = Collections.emptyMap();
		    int totalCnt = 0;

		    try {
		        jobCntMap = botNewService.selectBattleCountByUser(userName, roomName);
		    } catch (Exception e) {
		        e.printStackTrace();
		        jobCntMap = new HashMap<String, Integer>();
		    }

		    // 전체 공격횟수 = 모든 직업 CNT 합
		    for (Integer v : jobCntMap.values()) {
		        if (v != null) {
		            totalCnt += v;
		        }
		    }

		    // 5-1) 직업별 전직 조건 체크 (전사 100, 도적 100 같은 것들)
		    List<JobChangeReq> reqList = JOB_CHANGE_REQS.get(newJob);
		    if (reqList != null && !reqList.isEmpty()) {
		        StringBuilder sb = new StringBuilder();

		        for (JobChangeReq req : reqList) {
		            int curCnt = jobCntMap.getOrDefault(req.baseJob, 0);

		            if (curCnt < req.minCount) {
		                sb.append("- [")
		                  .append(req.baseJob)
		                  .append("] 직업으로 ")
		                  .append(req.minCount)
		                  .append("회 이상 공격 필요 (현재: ")
		                  .append(curCnt)
		                  .append("회)")
		                  .append(NL);
		            }
		        }

		        if (sb.length() > 0) {
		            return "[" + newJob + "] 직업은 아래 조건을 모두 만족해야 전직 가능합니다." + NL
		                 + sb.toString().trim();
		        }
		    }

		    // 5-2) 전체 공격 횟수 조건 체크
		    Integer totalReq = JOB_CHANGE_TOTAL_REQS.get(newJob);
		    if (totalReq != null) {
		        if (totalCnt < totalReq) {
		            return "[" + newJob + "] 직업은 전체 공격 횟수 "
		                 + totalReq + "회를 달성해야 전직 가능합니다. (현재: "
		                 + totalCnt + "회)";
		        }
		    }
	    }
	    
	    
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

	public String invenInfo(HashMap<String, Object> map) {

	    UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }

	    final String userName = ctx.targetUser;
	    final String roomName = ctx.roomName;

	    StringBuilder sb = new StringBuilder();
	    sb.append("✨").append(userName).append(" 인벤토리");

	    sb.append(ALL_SEE_STR);
	    List<HashMap<String, Object>> bag =
	            botNewService.selectInventorySummaryAll(userName, roomName);

	    if (bag == null || bag.isEmpty()) {
	        sb.append("- 인벤토리가 비어있습니다.");
	        return sb.toString();
	    }

	    // 카테고리 버킷
	    Map<String, List<String>> catMap = new LinkedHashMap<>();
	    catMap.put("※무기", new ArrayList<>());
	    catMap.put("※갑옷", new ArrayList<>());
	    catMap.put("※투구", new ArrayList<>());
	    catMap.put("※전설", new ArrayList<>());
	    catMap.put("※날개", new ArrayList<>());
	    catMap.put("※토템", new ArrayList<>());
	    catMap.put("※행운", new ArrayList<>());
	    catMap.put("※반지", new ArrayList<>());
	    catMap.put("※선물", new ArrayList<>());
	    catMap.put("※유물", new ArrayList<>());
	    catMap.put("※업적", new ArrayList<>());
	    catMap.put("※기타", new ArrayList<>());

	    for (HashMap<String, Object> row : bag) {

	        int itemId = safeInt(row.get("ITEM_ID"));
	        String itemName = Objects.toString(row.get("ITEM_NAME"), "");
	        String type = Objects.toString(row.get("ITEM_TYPE"), "");
	        int qty = safeInt(row.get("TOTAL_QTY"));

	        if (itemName.isEmpty()) continue;

	        String cat = resolveItemCategory(itemId);
	        String label = itemName;

	        // ─────────────────
	        // 장비 / 전설 / 날개 / 토템 /업적
	        // ─────────────────
	        if ("MARKET".equalsIgnoreCase(type)
	            || "MASTER".equalsIgnoreCase(type)
	            || "BAG_OPEN".equalsIgnoreCase(type)
	            || "ACHV".equalsIgnoreCase(type)
	        		) {
	        	
	        	
	        	/*
	        	HashMap<String, Object> info =
	                    botNewService.selectItemDetailById(itemId);
	            */
	        	//String opt = buildEnhancedOptionLine(info, 1);
	        	String opt = buildEnhancedOptionLine(row, 1);
	            if (!opt.isEmpty()) {
	                label += opt;
	            }
	        }
	        // ─────────────────
	        // 기타
	        // ─────────────────
	        else {
	            if (qty > 1) {
	                label += "x" + qty;
	            }
	        }

	        List<String> bucket = catMap.getOrDefault(cat, catMap.get("※기타"));
	        bucket.add(label);
	    }

	    // 출력
	    for (Map.Entry<String, List<String>> e : catMap.entrySet()) {
	        List<String> list = e.getValue();
	        if (list.isEmpty()) continue;

	        sb.append(e.getKey()).append(":").append(NL);
	        for (String s : list) {
	            sb.append(", ").append(s).append(NL);
	        }
	    }

	    sb.append(NL);
	    try {
	        List<HashMap<String,Object>> drops =
	                botNewService.selectTotalDropItems(userName);

	        if (drops != null && !drops.isEmpty()) {

	            sb.append(NL)
	              .append("▶ 누적 획득 드랍 아이템").append(NL)
	              .append("{ 일반 / 조각 / 빛 / 어둠 / 음양 }").append(NL);

	            Map<String, DropSummary> summaryMap = new LinkedHashMap<>();

	            for (HashMap<String,Object> row : drops) {

	                String rawName  = Objects.toString(row.get("ITEM_NAME"), "");
	                String gainType = Objects.toString(row.get("GAIN_TYPE"), "");
	                int qty         = safeInt(row.get("TOTAL_QTY"));

	                if (qty <= 0 || rawName.isEmpty()) continue;

	                // 🔹 아이템명 정규화 (접두/접미 제거)
	                String itemName = rawName
	                        .replace("조각", "")
	                        .replace("빛", "")
	                        .replace("어둠", "")
	                        .replace("음양", "");

	                DropSummary s = summaryMap.computeIfAbsent(itemName, k -> new DropSummary());

	                switch (gainType) {
	                    case "DROP":   // 일반 드랍
	                        s.normal += qty;
	                        break;
	                    case "STEAL":  // 조각
	                        s.fragment += qty;
	                        break;
	                    case "DROP3":  // 빛
	                        s.light += qty;
	                        break;
	                    case "DROP5":  // 어둠
	                        s.dark += qty;
	                        break;
	                    case "DROP9":  // 음양
	                        s.gray += qty;
	                        break;
	                }
	            }

	            // 출력
	            for (Map.Entry<String, DropSummary> e : summaryMap.entrySet()) {
	                DropSummary s = e.getValue();

	                sb.append(e.getKey())
	                  .append(" : { ")
	                  .append(s.normal).append(" / ")
	                  .append(s.fragment).append(" / ")
	                  .append(s.light).append(" / ")
	                  .append(s.dark).append(" / ")
	                  .append(s.gray)
	                  .append(" }")
	                  .append(NL);
	            }
	        }

	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }

	    
	    return sb.toString();
	}

	
	public String attackInfo(HashMap<String, Object> map) {
	    UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }

	    // 🔹 calcUserBattleContext 에서 가져오는 공통 값들
	    final String targetUser = ctx.targetUser;
	    final String roomName   = ctx.roomName;
	    final User   u          = ctx.user;
	    final String job        = ctx.job;

	    final int finalHpMax    = ctx.finalHpMax;      // 최종 HP
	    final int shownRegen    = ctx.shownRegen;      // 표시용 리젠(축복/흡혈귀 반영)
	    final int shownCrit     = ctx.shownCrit;       // 표시용 크리율
	    final int shownCritDmg  = ctx.shownCritDmg;    // 표시용 크리뎀

	    final int finalAtkMin   = ctx.atkMinWithItem;  // 아이템/무기 적용 ATK min
	    final int finalAtkMax   = ctx.atkMaxWithItem;  // 아이템/무기 적용 ATK max

	    final int baseMin       = ctx.baseMin;
	    final int baseMax       = ctx.baseMax;
	    final int baseHpMax     = ctx.baseHpMax;

	    final int bAtkMinRaw    = ctx.bAtkMinRaw;
	    final int bAtkMaxRaw    = ctx.bAtkMaxRaw;
	    final int bAtkMaxRateRaw    = ctx.bAtkMaxRateRaw;
	    final int bCriRaw       = ctx.bCriRaw;
	    final int bCriDmgRaw    = ctx.bCriDmgRaw;
	    final int bHpMaxRaw     = ctx.bHpMaxRaw;
	    final int bRegenRaw     = ctx.bRegenRaw;

	    // 직업 보너스 분리해서 보고 싶으면 calcUserBattleContext 에서 채워두었다고 가정
	    final int jobHpMaxBonus   = ctx.jobHpMaxBonus;   // 없으면 0
	    final int jobRegenBonus   = ctx.jobRegenBonus;   // 없으면 0

	    final String pointStr   = formatSpShort(ctx.currentPoint);
	    final int lifetimeSp    = ctx.lifetimeSp;
	    final String lifetimeSpStr    = formatSpShort(ctx.lifetimeSp);

	    final String allSeeStr  = NL + "===" + NL;  // 구분선

	    // ① 유효 체력 계산 (attackInfo 이전 로직과 동일, 리젠은 표시용 리젠 사용)
	    int effHp = computeEffectiveHpFromLastAttack(targetUser, roomName, u, finalHpMax, shownRegen);
	    if (effHp > finalHpMax) effHp = finalHpMax;

	    // ⑧ 누적 통계/타겟
	    List<KillStat> kills = botNewService.selectKillStats(targetUser, roomName);
	    int totalKills = 0;
	    for (KillStat ks : kills) totalKills += ks.killCount;
	    AttackDeathStat ads = botNewService.selectAttackDeathStats(targetUser, roomName);
	    int totalAttacks = (ads == null ? 0 : ads.totalAttacks);
	    int totalDeaths  = (ads == null ? 0 : ads.totalDeaths);

	    
	    
	 // === NEW: 일별 공격 통계 (어제 자정까지) ===
	    Date firstAttackDay = null;
	    Date maxAttackDay   = null;
	    int  maxAttackCnt   = 0;
	    int  avgAttackPerDay = 0;
	    int  todayAttackCnt  = 0;   // ★ 추가
	    Date today = truncateToDate(new Date()); // ★ 오늘 기준일


	    try {
	        List<HashMap<String,Object>> dailyList =
	                botNewService.selectDailyAttackCounts(targetUser, roomName);

	        if (dailyList != null && !dailyList.isEmpty()) {
	            int totalAtkBeforeToday = 0;
	            int activeDays = 0; // ★ 어제까지 실제 일수

	            for (int i = 0; i < dailyList.size(); i++) {
	                HashMap<String,Object> row = dailyList.get(i);
	                if (row == null) continue;

	                Object dayObj = row.get("ATTACK_DAY");
	                Date day = null;
	                if (dayObj instanceof Date) {
	                    day = truncateToDate((Date) dayObj);
	                } else if (dayObj instanceof java.sql.Date) {
	                    day = truncateToDate(new Date(((java.sql.Date)dayObj).getTime()));
	                }

	                int cnt = safeInt(row.get("ATK_CNT"));

	                if (day == null) continue;

	                // 최초 공격일
	                if (firstAttackDay == null) {
	                    firstAttackDay = day;
	                }

	                // 최대 공격일
	                if (cnt > maxAttackCnt) {
	                    maxAttackCnt = cnt;
	                    maxAttackDay = day;
	                }

	                // ★ 오늘 공격
	                if (day.equals(today)) {
	                    todayAttackCnt = cnt;
	                } else {
	                    // ★ 어제까지 누적/평균용
	                    totalAtkBeforeToday += cnt;
	                    activeDays++;
	                }
	            }

	            // ★ 일평균 = 어제까지 기준
	            if (activeDays > 0) {
	                avgAttackPerDay = totalAtkBeforeToday / activeDays;
	            }
	        }
	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }
	 // === NEW: 직업별 공격 횟수 ===
	    Map<String, Integer> jobAtkMap = Collections.emptyMap();
	    try {
	        jobAtkMap = botNewService.selectBattleCountByUser(targetUser, roomName);
	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	        jobAtkMap = new HashMap<>();
	    }
	    
	    // 🔹 몬스터 전체 캐시
	    List<Monster> monList = botNewService.selectAllMonsters();
	    Map<Integer, Monster> monMap = new HashMap<>();
	    if (monList != null) {
	        for (Monster mm : monList) {
	            monMap.put(mm.monNo, mm);
	        }
	    }

	    Monster target = (u.targetMon > 0) ? monMap.get(u.targetMon) : null;
	    String targetName = (target == null) ? "-" : target.monName;

	    
	    List<HashMap<String, Object>> bag = botNewService.selectInventorySummaryAll(targetUser, roomName);
	    
	    // ⑨ 출력
	    StringBuilder sb = new StringBuilder();
	    sb.append("✨").append(targetUser).append(" 공격 정보").append(NL)
	      .append("Lv: ").append(u.lv);
	    if (!job.isEmpty()) {
	        sb.append(" (").append(job).append(")");
	    }
	    sb.append(", EXP ").append(u.expCur).append("/").append(u.expNext).append(NL);
	    sb.append("포인트: ").append(pointStr).append(NL);
	    sb.append("누적 획득 포인트: ").append(lifetimeSpStr).append(NL).append(NL);

	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL);
	    sb.append("⚔CRIT: ").append(shownCrit).append("%  CDMG ").append(shownCritDmg).append("%").append(NL);
	    sb.append("❤️HP: ").append(effHp).append(" / ").append(finalHpMax)
	      .append(",5분당회복+").append(shownRegen).append(NL).append(NL);

	    if (ctx.isJobMaster) {
	        sb.append(ctx.job).append(" 마스터 보너스: ATK 10%, HP 15%, 리젠+1000").append(NL);
	    }

        sb.append("▶ 현재 타겟: ").append(targetName)
	      .append(" (MON_NO=").append(u.targetMon).append(")");

	    // 누적 전투
	    sb.append(allSeeStr);

	    JobDef jobDef = JOB_DEFS.get(job);
	    if (jobDef != null && jobDef.attackLine != null && !jobDef.attackLine.isEmpty()) {
	        sb.append(jobDef.attackLine).append(NL).append(NL);
	    }
	    // ─ ATK 상세 ─
	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL)
	      .append("   └ 기본 (").append(baseMin).append("~").append(baseMax).append(")").append(NL)
	      /*
	      .append("   └ 시즌1 강화: ").append(weaponLv).append("강 (max+").append(weaponBonus).append(")").append(NL)
	      */
	      .append("   └ 아이템 (min").append(formatSigned(bAtkMinRaw))
	      .append(", max").append(formatSigned(bAtkMaxRaw)).append(")").append(NL);
	      
	    
	    if(ctx.dailyAtkBonus > 0) {
	    	sb.append("   └ 룰렛 버프: ATK +").append(ctx.dailyAtkBonus).append(NL);
	    }
	    if(bAtkMaxRateRaw > 0) {
	    	sb.append("   └ 최종공격력 (").append(formatSigned(bAtkMaxRateRaw)).append("%)").append(NL);
	    }
	    // ─ CRIT 상세 ─
	    sb.append("⚔CRIT: ").append(shownCrit).append("%  CDMG ").append(shownCritDmg).append("%").append(NL)
	      .append("   └ 기본 (").append(u.critRate).append("%, ").append(u.critDmg).append("%)").append(NL);
	      

	    if ("파이터".equals(job)) {
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
	    
	    if(ctx.dailyCriDmgBonus > 0) {
	    	sb.append("   └ 룰렛 버프 (CRIT")
	        .append(formatSigned(0))
	        .append("%, CDMG ")
	        .append(formatSigned(ctx.dailyCriDmgBonus))
	        .append("%)").append(NL);
		    
	    }
	    
	    // ─ HP 상세 ─
	    sb.append("❤️HP: ").append(effHp).append(" / ").append(finalHpMax)
	      .append(",5분당회복+").append(shownRegen).append(NL)
	      .append("   └ 기본 (HP+").append(baseHpMax)
	      .append(",5분당회복+").append(u.hpRegen).append(")").append(NL)
	      .append("   └ 아이템 (HP").append(formatSigned(bHpMaxRaw))
	      .append(",5분당회복").append(formatSigned(bRegenRaw)).append(")").append(NL);

	    if (jobHpMaxBonus != 0 || jobRegenBonus != 0) {
	        sb.append("   └ 직업 (HP")
	          .append(formatSigned(jobHpMaxBonus))
	          .append(",5분당회복")
	          .append(formatSigned(jobRegenBonus))
	          .append(")").append(NL);
	    }
	    
	    String relicSummary = buildRelicSummaryLine(bag,9000);
        if (relicSummary != null) {
            sb.append(NL).append(relicSummary).append(NL);
        }
        String relicSummary2 = buildRelicSummaryLine(bag,8000);
        if (relicSummary2 != null) {
        	sb.append(NL).append(relicSummary2).append(NL);
        }
        
        
        if (ctx.dropMinAtkBonus +ctx.dropMaxAtkBonus +  ctx.dropHpBonus + ctx.dropRegenBonus
                + ctx.dropCritBonus + ctx.dropCritDmgBonus > 0) {

        	sb.append(NL).append("✨어둠 부가 효과: ");
            if (ctx.dropMinAtkBonus > 0) sb.append("min_ATK+").append(ctx.dropMinAtkBonus).append(" ");
            if (ctx.dropMaxAtkBonus > 0) sb.append("max_ATK+").append(ctx.dropMaxAtkBonus).append(" ");
            if (ctx.dropHpBonus > 0) sb.append("HP+").append(ctx.dropHpBonus).append(" ");
            if (ctx.dropRegenBonus > 0) sb.append("체젠+").append(ctx.dropRegenBonus).append(" ");
            if (ctx.dropCritBonus > 0) sb.append("치확+").append(ctx.dropCritBonus).append("% ");
            if (ctx.dropCritDmgBonus > 0) sb.append("치피+").append(ctx.dropCritDmgBonus).append("% ");
            sb.append(NL);
        }


	    // ─ 인벤토리 ─
	    try {
	        sb.append(NL).append("▶ 인벤토리<옵션:/인벤>").append(NL);
	        if (bag == null || bag.isEmpty()) {
	            sb.append("- (비어있음)").append(NL);
	        } else {
	            // 1) ITEM_ID ASC 정렬
	            bag.sort((a, b) -> {
	                int noA = parseIntSafe(Objects.toString(a.get("ITEM_ID"), "0"));
	                int noB = parseIntSafe(Objects.toString(b.get("ITEM_ID"), "0"));
	                return Integer.compare(noA, noB);
	            });

	            // 2) 카테고리별 버킷 생성
	            Map<String, List<String>> catMap = new LinkedHashMap<>();
	            catMap.put("※무기", new ArrayList<>());
	            catMap.put("※투구", new ArrayList<>());
	            catMap.put("※행운", new ArrayList<>());
	            catMap.put("※갑옷", new ArrayList<>());
	            catMap.put("※반지", new ArrayList<>());
	            catMap.put("※토템", new ArrayList<>());
	            catMap.put("※전설", new ArrayList<>());
	            catMap.put("※날개", new ArrayList<>());
	            catMap.put("※선물", new ArrayList<>());
	            catMap.put("※유물", new ArrayList<>());
	            catMap.put("※업적", new ArrayList<>());
	            catMap.put("※기타", new ArrayList<>());

	            // 3) 인벤토리 한 줄씩 카테고리 분류
	            for (HashMap<String, Object> row : bag) {
	                if (row == null) continue;

	                String itemName = Objects.toString(row.get("ITEM_NAME"), "-");
	                String qtyStr   = Objects.toString(row.get("TOTAL_QTY"), "0");
	                String typeStr  = Objects.toString(row.get("ITEM_TYPE"), "");
	                int itemId      = parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));

	                if (itemName == null || itemName.trim().isEmpty()) continue;

	                // 수량 파싱
	                int qtyVal = parseIntSafe(qtyStr);
	                if (qtyVal < 1) qtyVal = 1; // 최소 1

	                String label = itemName;
	                boolean isEquipType =
	                        "MARKET".equalsIgnoreCase(typeStr) ||
	                        "BAG_OPEN".equalsIgnoreCase(typeStr) ||
	                        "MASTER".equalsIgnoreCase(typeStr) || 
	                        "ACHV".equalsIgnoreCase(typeStr) 
	                        ;

	                if (isEquipType) {
	                	
	                } else {
	                    if (qtyVal > 1) {
	                        label = label + "x" + qtyVal;
	                    }
	                }

	                String cat = resolveItemCategory(itemId);

	                List<String> bucket = catMap.get(cat);
	                if (bucket == null) {
	                    bucket = catMap.get("※기타");
	                }
	                bucket.add(label);
	            }

	            // 4) 카테고리별 출력
	            for (Map.Entry<String, List<String>> e : catMap.entrySet()) {
	                List<String> list = e.getValue();
	                if (list == null || list.isEmpty()) continue;

	                int max = getMaxAllowedByCategoryLabel(e.getKey());

	                if (max != Integer.MAX_VALUE) {
	                    sb.append(e.getKey()).append("(최대").append(max).append("개)").append(": ");
	                } else {
	                    sb.append(e.getKey()).append(": ");
	                }

	                sb.append(String.join(", ", list));
	                sb.append(NL);
	            }

	            sb.append(NL);
	        }
	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }

	    sb.append("누적 전투 기록").append(NL)
	      .append("- 총 공격 횟수: ").append(totalAttacks).append("회").append(NL)
	      .append("- 총 사망 횟수: ").append(totalDeaths).append("회").append(NL).append(NL);

	    if (firstAttackDay != null) {
	        sb.append("시작일: ")
	          .append(formatDateYMD(firstAttackDay))
	          .append(NL);
	    } else {
	        sb.append("시작일: -").append(NL);
	    }

	    sb.append("- 일별 평균 공격(어제까지): ")
	      .append(avgAttackPerDay)
	      .append("회/일").append(NL);

	    if (maxAttackDay != null && maxAttackCnt > 0) {
	        sb.append("- 최고 공격: ")
	          .append(formatDateMD(maxAttackDay))
	          .append(" ")
	          .append(maxAttackCnt).append("회").append(NL);
	    } else {
	        sb.append("- 최고 공격: -").append(NL);
	    }
	    sb.append("- 오늘 공격: ")
	      .append(todayAttackCnt)
	      .append("회")
	      .append(NL);

		sb.append(NL);
		// === NEW: 직업별 공격 횟수 출력 ===
		if (jobAtkMap != null && !jobAtkMap.isEmpty()) {
			sb.append("직업별 공격 횟수").append(NL);

			List<String> rows = new ArrayList<>();
			List<String> jobNames = new ArrayList<>(jobAtkMap.keySet());
			Collections.sort(jobNames);

			for (String j : jobNames) {
				rows.add(j + ": " + String.format("%,d", jobAtkMap.get(j)) + "회");
			}

			for (int i = 0; i < rows.size(); i += 3) {
				sb.append("- ").append(String.join(" / ", rows.subList(i, Math.min(i + 3, rows.size())))).append(NL);
			}

			sb.append(NL);
		}

		// 누적 처치
		sb.append("누적 처치 기록 (총 ").append(totalKills).append("마리)").append(NL);

		if (kills == null || kills.isEmpty()) {
			sb.append("기록 없음").append(NL);
		} else {
			List<String> rows = new ArrayList<>();

			for (KillStat ks : kills) {
				String monName = ks.monName;
				if ((monName == null || monName.isEmpty()) && monMap != null) {
					Monster mm = monMap.get(ks.monNo);
					if (mm != null)
						monName = mm.monName;
				}
				rows.add(monName + ": " + String.format("%,d", ks.killCount) + "마리");
			}

			for (int i = 0; i < rows.size(); i += 3) {
				sb.append("- ").append(String.join(" / ", rows.subList(i, Math.min(i + 3, rows.size())))).append(NL);
			}
		}

	    // 업적
	    int achvCnt = 0;
	    try {
	        List<HashMap<String,Object>> achv = botNewService.selectAchievementsByUser(targetUser, roomName);
	        achvCnt = (achv == null ? 0 : achv.size());
	        
	        sb.append(NL).append("▶ 업적").append(" [").append(achvCnt).append("개]").append(NL);
	        if (achv == null || achv.isEmpty()) {
	            sb.append("- 달성된 업적이 없습니다.").append(NL);
	        } else {
	        	//renderAchievementSummary(sb, achv);
	        	//sb.append("(상세: /가방상세)").append(NL);
	            renderAchievementLinesCompact(sb, achv, monMap);
	        }
	    } catch (Exception ignore) {}

	    return sb.toString();
	}


	/** 타겟 변경 (번호/이름 허용) */
	public String changeTarget(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");
		final String input = Objects.toString(map.get("monNo"), "").trim();
		boolean nightmare = botNewService.isNightmareMode(userName, roomName);
	    
		if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
		if (input.isEmpty()) {
		    User u = botNewService.selectUser(userName, null);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("공격 타겟 목록입니다:").append(NL).append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterCompactLine(mm, userLv,nightmare)); // ★ 레벨 비례 EXP 반영됨!
		    }
		    
		    return sb.toString();
		}

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
		    User u = botNewService.selectUser(userName, null);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("해당 몬스터(").append(input).append(")를 찾을 수 없습니다.").append(NL)
		      .append("아래 목록 중에서 선택해주세요:").append(NL).append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterCompactLine(mm, userLv,nightmare));
		    }
		    return sb.toString();
		}
		
		User u = botNewService.selectUser(userName, null);
		if (u == null) {
		    botNewService.insertUserWithTargetTx(userName, roomName, m.monNo);
		    return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		         + "▶ 선택: " + renderMonsterCompactLine(m, 1,nightmare);
		}
		if (u.targetMon == m.monNo) return "현재 타겟이 이미 " + m.monName + "(MON_NO=" + m.monNo + ") 입니다.";

		


		// 예: 사용자가 /공격타겟 13 입력 → newMonNo = 13
		/*
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
			if(!master) {
				if (killsOnPrev < 5 ) {
				    Monster prev = botNewService.selectMonsterByNo(prevMonNo);
				    String prevName = (prev == null ? ("Lv " + prevMonNo) : prev.monName);
				    return "상위 등급으로 올리려면 [" + prevName + "]을(를) 최소 5마리 처치해야 합니다. (현재 "
				         + killsOnPrev + "마리)";
				}
			}
		}
		*/
		
		botNewService.closeOngoingBattleTx(userName, roomName);
		botNewService.updateUserTargetMonTx(userName, roomName, m.monNo);
		int userLvForView = (u != null ? u.lv : 1);
		return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		     + "▶ 선택: " + NL + renderMonsterCompactLine(m, userLvForView,nightmare);
	}
	// 엔트리 포인트: 기존 /구매 명령이 들어오는 곳
	public String buyItem(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String rawParam = Objects.toString(map.get("param1"), "").trim();

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }


	    // 파라미터 없으면: 구매 가능 목록 노출 (기존 로직 유지)
	    if (rawParam.isEmpty() || "전체".equals(rawParam)) {
	    	return buildCustomMarketAllMessage(userName, roomName);
	    }
	    
	    // 2) /구매 신규  (또는 /구매 000 같이 쓰고 싶으면 OR 유지)
	    if ("신규".equals(rawParam) || "000".equals(rawParam)) {
	        // 전체 목록 조회 (기존에 쓰던 쿼리)
	        List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);
	        if (list == null || list.isEmpty()) {
	            return "신규 등록 아이템이 없습니다.";
	        }

	        // INSERT_DATE 기준으로 내림차순 정렬 (최근 등록 순)
	        list.sort(new Comparator<HashMap<String,Object>>() {
	            @Override
	            public int compare(HashMap<String,Object> o1, HashMap<String,Object> o2) {
	                java.sql.Timestamp t1 = toTimestamp(o1.get("INSERT_DATE"));
	                java.sql.Timestamp t2 = toTimestamp(o2.get("INSERT_DATE"));
	                // null 안전 처리: null 은 가장 오래된 것으로 취급
	                if (t1 == null && t2 == null) return 0;
	                if (t1 == null) return 1;
	                if (t2 == null) return -1;
	                // 최근 것이 앞으로 오도록 내림차순
	                return t2.compareTo(t1);
	            }
	        });

	        // 상위 10개만 사용
	        int limit = Math.min(10, list.size());
	        List<HashMap<String,Object>> newestList = new ArrayList<>(list.subList(0, limit));

	        String compact = renderMarketListForBuy(newestList, userName, false);
	        return "▶ 신규 등록 아이템 목록" + NL + compact;
	    }
	    
	    
	 // ➊ 카테고리 목록 모드 체크
	    int[] range = resolveCategoryRange(rawParam);  // ex) "무기" → [100, 200]
	    if (range != null) {
	        int min = range[0];
	        int max = range[1];

	        // DB에서 모든 아이템 가져온 뒤 100~199 사이만 필터
	        List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);

	        List<HashMap<String,Object>> filtered = new ArrayList<>();
	        for (HashMap<String,Object> row : list) {
	            int id = parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));
	            if (id >= min && id < max) {
	                filtered.add(row);
	            }
	        }

	        return "▶ " + rawParam + " 카테고리 목록" + NL
	             + renderMarketListForBuy(filtered, userName, false);
	    }

	    // 문의방 제한 (기존 로직 유지)
	    if (roomName.equals("람쥐봇 문의방")) {
	        if (!userName.equals("일어난다람쥐/카단")) {
	            return "문의방에서는 불가능합니다.";
	        }
	    }

	    // 멀티 구매: 콤마 포함 시
	    if (rawParam.contains(",")) {
	        return buyMultiItems(roomName, userName, rawParam);
	    }

	    // 단일 구매
	    return buySingleItem(roomName, userName, rawParam);
	}

	
	// 콤마 기반 멀티 구매 + x / * 수량 지원
	// 예) "101,102,백화검*3,200x2"
	private String buyMultiItems(String roomName, String userName, String raw) {
	    String[] tokens = raw.split(",");
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 일괄 구매 결과").append(NL);

	    boolean hasAny = false;

	    for (String t : tokens) {
	        String token = (t == null ? "" : t.trim());
	        if (token.isEmpty()) {
	            continue;
	        }
	        hasAny = true;

	        // 수량 파싱: 123x2, 123*2, 백화검*3 등
	        int qty = 1;
	        String itemToken = token;

	        java.util.regex.Matcher m =
	            java.util.regex.Pattern
	                .compile("(.+?)[xX\\*](\\d+)$")
	                .matcher(token);

	        if (m.matches()) {
	            itemToken = m.group(1).trim();
	            qty = parseIntSafe(m.group(2));
	            if (qty <= 0) qty = 1;
	        }

	        for (int i = 0; i < qty; i++) {
	            String oneResult = buySingleItem(roomName, userName, itemToken);

	            String label = resolveItemLabel(itemToken);   // 🔹 여기서 아이템 이름으로 변환

	            sb.append(NL)
	              .append("[").append(label);                 // 🔹 itemToken 대신 label 사용
	            if (qty > 1) {
	                sb.append(" #").append(i + 1).append("/").append(qty);
	            }
	            sb.append("]").append(NL)
	              .append(oneResult).append(NL);
	        }
	    }

	    if (!hasAny) {
	        return "구매할 대상이 없습니다.";
	    }

	    return sb.toString();
	}
	// 실제 단일 아이템 구매 로직 (기존 buyItem의 본체 부분)
	private String buySingleItem(String roomName, String userName, String raw) {

	    // 입력 → itemId 해석
	    Integer itemId = null;
	    if (raw != null && raw.matches("\\d+")) {
	        try { itemId = Integer.valueOf(raw); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByName(raw); } catch (Exception ignore) {}
	    }
	    
	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + raw + NL
	             + "(/구매 입력만으로 목록을 확인하세요)";
	    }

	    // 이미 소유 여부 체크
	    boolean alreadyOwnedThisItem = false;
	    try {
	        List<HashMap<String,Object>> inv = botNewService.selectInventorySummaryAll(userName, roomName);
	        if (inv != null) {
	            for (HashMap<String,Object> row : inv) {
	                if (row == null) continue;

	                int rowItemId = parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));
	                if (rowItemId != itemId) continue;

	                int q = parseIntSafe(Objects.toString(row.get("TOTAL_QTY"), "0"));
	                if (q > 0) {
	                    alreadyOwnedThisItem = true;  // 이미 이 아이템은 가지고 있음 → 업그레이드 구매
	                    break;
	                }
	            }
	        }
	    } catch (Exception ignore) {}

	    // 장비 카테고리 수량 제한 체크 (새 장비일 때만)
	    if (!alreadyOwnedThisItem) {
	        String limitMsg = checkEquipCategoryLimit(userName, roomName, itemId, 1);
	        if (limitMsg != null) {
	            return limitMsg;
	        }
	    }

	    // 아이템 상세 조회
	    HashMap<String, Object> item = null;
	    try {
	        item = botNewService.selectItemDetailById(itemId);
	    } catch (Exception ignore) {}
	    String itemType = (item == null) ? "" : Objects.toString(item.get("ITEM_TYPE"), "");

	    if (item == null || !"MARKET".equalsIgnoreCase(itemType)) {
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

	    // 포인트 확인
	    Integer tmpPoint = null;
	    try { tmpPoint = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int curPoint = (tmpPoint == null ? 0 : tmpPoint.intValue());
	    
	    if (curPoint < price) {
	        return userName + "님, [" + itemName + "] 구매에 필요한 포인트가 부족합니다."
	             + " (가격: " + price + "sp, 보유: " + curPoint + "sp)";
	    }


	    // ============================
	    // 인벤토리 적재 (장비는 중복구매 시 QTY 증가)
	    // ============================
	    int buyQty = 1; // 현재 /구매는 1개씩 구매
	    int finalQty = 1; // 👉 이 값을 나중에 옵션 표시에 사용

	    int itemIdInt = itemId; // 위에서 구한 itemId 그대로 사용
	    boolean upgradeOk = false;// isUpgradableEquip(itemIdInt);

	    if ("MARKET".equalsIgnoreCase(itemType)) {
	        // 장비: 같은 ITEM_ID 가진 행이 있으면 QTY만 증가
	        List<HashMap<String, Object>> rows =
	                botNewService.selectInventoryRowsForSale(userName, roomName, itemId);

	        String targetRowId = null;
	        int currentQty = 0;

	        if (rows != null) {
	            for (HashMap<String, Object> row : rows) {
	                if (row == null) continue;

	                String delYn = Objects.toString(row.get("DEL_YN"), "0");
	                if (!"0".equals(delYn)) continue; // 삭제된 건 스킵

	                String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	                if (rid == null) continue;

	                int q = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	                if (q <= 0) continue;

	                // 같은 ITEM_ID 한 줄만 관리한다고 가정 → 첫 행 사용
	                targetRowId = rid;
	                currentQty = q;
	                break;
	            }
	        }

	        if (!upgradeOk) {
	            // ❌ 업그레이드 불가 장비 (100/200/400번대 외 MARKET)
	            // → 기존처럼 1개만 보유 가능
	            if (currentQty > 0) {
	                return "⚠ 이미 보유중인 아이템입니다. [" + itemName + "] 은(는) 1개만 보유 가능합니다.";
	            }

	            // 최초 구매만 허용 (QTY=1)
	            finalQty = buyQty;
	            HashMap<String, Object> inv = new HashMap<>();
	            inv.put("userName", userName);
	            inv.put("roomName", roomName);
	            inv.put("itemId",  itemIdInt);
	            inv.put("qty",     buyQty);
	            inv.put("delYn",   "0");
	            inv.put("gainType","BUY");
	            botNewService.insertInventoryLogTx(inv);

	        } else {
	            // ✅ 업그레이드 가능 장비(100/200/400번대)
	            int newQty = currentQty + buyQty;

	            // 최대 4단계(QTY=4)까지 허용
	            if (newQty > 1) {
	            //if (newQty > 4) {
	                int plus = Math.max(0, currentQty - 1);
	                return "⚠ [" + itemName + "] 은(는) 최대 (+3) 까지 업그레이드 가능합니다."
	                     + NL + "현재 보유 상태: " + itemName
	                     + (plus > 0 ? "(+" + plus + ")" : "")
	                     + " (현재 갯수=" + currentQty + ")";
	            }

	            if (targetRowId != null) {
	                finalQty = newQty;
	                botNewService.updateInventoryQtyByRowId(targetRowId, newQty);
	            } else {
	                finalQty = buyQty;
	                HashMap<String, Object> inv = new HashMap<>();
	                inv.put("userName", userName);
	                inv.put("roomName", roomName);
	                inv.put("itemId",  itemIdInt);
	                inv.put("qty",     buyQty);
	                inv.put("delYn",   "0");
	                inv.put("gainType","BUY");
	                botNewService.insertInventoryLogTx(inv);
	            }
	        }

	    } else {
	        finalQty = buyQty;
	        // 장비가 아닌 경우 → 기존처럼 바로 insert
	        HashMap<String, Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId",  itemId);
	        inv.put("qty",     buyQty);
	        inv.put("delYn",   "0");
	        inv.put("gainType","BUY");
	        botNewService.insertInventoryLogTx(inv);
	    }
	    

	    // 결제 (포인트 차감)
	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", -price);
	    pr.put("cmd", "BUY");
	    botNewService.insertPointRank(pr);

	    // 구매 후 포인트
	    Integer tmpAfter = null;
	    try { tmpAfter = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int afterPoint = (tmpAfter == null ? 0 : tmpAfter.intValue());

	    int upgradeLevel = 0;
	    if ("MARKET".equalsIgnoreCase(itemType)) {
	        upgradeLevel = Math.max(0, finalQty - 1); // qty=2 → +1, qty=3 → +2 ...
	    }

	    // 표시용 이름
	    String shownName = itemName;
	    if (upgradeLevel > 0) {
	        shownName = itemName + "(+" + upgradeLevel + ")";
	    }

	    // 옵션 문자열 결정
	    String optionStr;
	    
	    /*
	    if ("MARKET".equalsIgnoreCase(itemType)) {
	        // 장비: 강화 수량 기반 옵션 (공격력 1(+1)~1(+1) 형태)
	        optionStr = buildEnhancedOptionLine(item, finalQty);
	    } else {
	        // 기타: 기존 옵션 포맷 유지
	        optionStr = buildOptionTokensFromMap(item);
	    }*/
	    
	    optionStr = buildEnhancedOptionLine(item, 1); 
	    //buildOptionTokensFromMap(item);

	    // 결과 메시지
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 구매 완료").append(NL)
	      .append(userName).append("님, ").append(shownName).append("을(를) 구매했습니다.").append(NL)
	      .append("↘가격: ").append(price).append("sp").append(NL)
	      .append("↘옵션: ").append(optionStr).append(NL)
	      .append("✨포인트: ").append(afterPoint).append("sp");

	    try {
	        botNewService.closeOngoingBattleTx(userName, roomName);
	    } catch(Exception e) {
	        // 무시
	    }

	    return sb.toString();
	}

	private void applyDropBonusToContext(
	        UserBattleContext ctx,
	        String userName,
	        String roomName
	) {

	    List<HashMap<String,Object>> drops =
	            botNewService.selectTotalDropItems(userName);

	    if (drops == null || drops.isEmpty()) return;

	    int bonusMinAtk     = 0;
	    int bonusMaxAtk     = 0;
	    int bonusHp      = 0;
	    int bonusRegen   = 0;
	    int bonusCrit    = 0;
	    int bonusCritDmg = 0;

	    for (HashMap<String,Object> row : drops) {

	        String name     = Objects.toString(row.get("ITEM_NAME"), "");
	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "");
	        int itemId = safeInt(row.get("ITEM_ID"));
	        int qty         = safeInt(row.get("TOTAL_QTY"));

	        if (qty <= 0 || name.isEmpty()) continue;

	        // 👉 어둠 아이템만 적용 (원하면 조건 제거 가능)
	        if (!"DROP5".equals(gainType)) continue;
	        
	        switch(itemId) {
	        case 1: 
	        	bonusCritDmg += qty /10;
	        	break;
	        case 15: case 30:
	        	bonusCritDmg += qty /5;
	        	break;
	        case 25:  
	        	bonusCritDmg += qty ;
	        	break;
	        	
	        case 27:
	        	bonusRegen+=qty/2;
	        	break;
	        case 20:  
	        	bonusRegen+=qty/5;
	        	break;
	        case 12:  case 7: case 8:  
	        	bonusRegen+=qty/10;
	        	break;
	        	
	        case 23: case 28:
	        	bonusCrit+=qty/5;
	        	break;
	        case 17: case 9: case 11: case 19:  
	        	bonusCrit+=qty/10;
	        	break;
	        	
	        case 24: 
	        	bonusMinAtk+=qty/2;
	        	break;
	        case 2: case 3: case 5: case 16:  
	        	bonusMinAtk+=qty/10;
	        	break;
	        	
	        case 26: case 29:
	        	bonusMaxAtk+=qty/2;
	        	break;
	        case 13: case 4: case 6: case 14: 
	        	bonusMaxAtk+=qty/10;
	        	break;
	        	
	        case 10: case 18: case 21: case 22: 
	        	bonusHp += qty/2;
	        	break;
	        }
	        
	        
	        //상한초과방지
	        bonusCrit = Math.min(bonusCrit, 100);
	        bonusCritDmg = Math.min(bonusCritDmg, 200);
	        bonusMinAtk = Math.min(bonusMinAtk, 150);
	        bonusMaxAtk = Math.min(bonusMaxAtk, 200);
	    }

	    // ctx 에 바로 반영
	    ctx.atkMinWithItem += bonusMinAtk;
	    ctx.atkMaxWithItem += bonusMaxAtk;

	    ctx.finalHpMax     += bonusHp;
	    ctx.shownRegen     += bonusRegen;

	    ctx.shownCrit      += bonusCrit;
	    ctx.shownCritDmg   += bonusCritDmg;

	    // 표시용 (선택)
	    ctx.dropMinAtkBonus     = bonusMinAtk;
	    ctx.dropMaxAtkBonus     = bonusMaxAtk;
	    ctx.dropHpBonus      = bonusHp;
	    ctx.dropRegenBonus   = bonusRegen;
	    ctx.dropCritBonus    = bonusCrit;
	    ctx.dropCritDmgBonus = bonusCritDmg;
	}

	
	
	private String buildCustomMarketAllMessage(String userName, String roomName) {


	    // 기본(키워드 없음 또는 기타)
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 람쥐 상점 전체 안내").append(NL)
	      .append("- /구매 100 or /구매 무기: 무기 카테고리").append(NL)
	      .append("- /구매 200 or /구매 투구: 투구 카테고리").append(NL)
	      .append("- /구매 000 or /구매 신규: 최근 등록 아이템").append(NL)
	      .append("- 입력 가능 카테고리 ").append(NL)
	      .append("- 신규 무기 투구 행운 갑옷 반지 토템 전설 날개 선물 ").append(NL)
	      .append("- 000 100 200 300 400 500 600 700 800 900").append(NL);

	    // 필요하면 여기서 전체 상품 일부만 보여줘도 됨
	    // List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);
	    // sb.append(NL).append(renderMarketListForBuy(list, userName, true));

	    return sb.toString();
	}
	// 멀티 구매 출력용: "101" → "목검" 같은 ITEM_NAME으로 바꿔줌
	private String resolveItemLabel(String itemToken) {
	    if (itemToken == null || itemToken.trim().isEmpty()) {
	        return "";
	    }

	    String token = itemToken.trim();
	    Integer itemId = null;

	    // 1) 숫자면 ID로 시도
	    if (token.matches("\\d+")) {
	        try { itemId = Integer.valueOf(token); } catch (Exception ignore) {}
	    }

	    // 2) 이름으로 시도
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByName(token); } catch (Exception ignore) {}
	    }

	    // 3) 코드로 시도
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByCode(token); } catch (Exception ignore) {}
	    }

	    if (itemId == null) {
	        // 끝까지 못 찾으면 그냥 원래 토큰 리턴
	        return token;
	    }

	    // 4) ITEM_NAME 조회
	    try {
	        HashMap<String, Object> item = botNewService.selectItemDetailById(itemId);
	        if (item != null) {
	            String itemName = Objects.toString(item.get("ITEM_NAME"), "");
	            if (!itemName.isEmpty()) {
	                return itemName;
	            }
	        }
	    } catch (Exception ignore) {}

	    // 조회 실패 시 토큰 그대로
	    return token;
	}
	
	
	public String monsterAttack(HashMap<String, Object> map) {
	    map.put("cmd", "monster_attack");

	    // 0) 방/유저 기본 검증 (구버전 그대로)
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    
	    boolean master = false;
	    
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    // 문의방 제한 (구버전 그대로)
	    if ("람쥐봇 문의방".equals(roomName) && "일어난다람쥐/카단".equals(userName) ) {
	    	master =  true;
	    }
	    if (master) {
	    	map.put("param1","test");
	    }
	    
	    if ("람쥐봇 문의방".equals(roomName) && !master) {
            return "문의방에서는 불가능합니다.";
	    }
	    
	    int lockCode = botNewService.lockMacroUser(userName);

	    if (lockCode == 1 || lockCode == 2) {
	        // 매크로 → 공격 차단
	        return "공격불가 상태입니다 code:"+lockCode;
	    }
	    

	    // 쿨타임/HP 제한에서 쓰는 원래 param1 (구버전과 동일)
	    final String param1 = Objects.toString(map.get("param1"), "");

	    // ─────────────────────────────
	    // 1) 스탯 계산용 map 복사본 → param1 비워서 "타 유저 조회" 방지만 막음
	    //    (실제 전투 로직에서의 param1 사용은 위에서 받은 값으로 계속 진행)
	    // ─────────────────────────────
	    HashMap<String, Object> statMap = new HashMap<>(map);
	    statMap.put("param1", "");   // calcUserBattleContext 에서 다른 유저 검색 못 하게 막는 용도

	 // ✅ 크론 없이: 오늘 첫 공격이면 전일 배틀로그로 오늘 마스터 생성(전체방 기준)
	    try {
	        int todayCnt = botNewService.countTodayJobMasterAll();
	        if (todayCnt == 0) {
	            botNewService.createTodayJobMastersFromYesterdayAll();
	        }
	    } catch (Exception ignore) {}
	    
	    // 2) 공통 스탯 계산
	    UserBattleContext ctx = calcUserBattleContext(statMap);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }

	    final User u = ctx.user;
	    String job   = (u.job == null ? "" : u.job.trim());
	    if (job.isEmpty()) {
	        return userName + " 님, /직업 을 통해 먼저 전직해주세요."+NL+"12/15 업데이트 이후 가방으로 능력치 변경을 확인해주세요.";
	    }

	    // ─────────────────────────────
	    // 3) calcUserBattleContext 에서 가져오는 스탯들
	    // ─────────────────────────────
	    final int baseMin   = ctx.baseMin;
	    final int baseMax   = ctx.baseMax;
	    final int baseHpMax = ctx.baseHpMax;

	    final int bAtkMin   = ctx.bAtkMinRaw;
	    final int bAtkMax   = ctx.bAtkMaxRaw;
	    final int bHpMax    = ctx.bHpMaxRaw;
	    final int bRegen    = ctx.bRegenRaw;
	    final int bCri      = ctx.bCriRaw;
	    final int bCriDmg   = ctx.bCriDmgRaw;

	    final int weaponLv    = ctx.weaponLv;
	    final int weaponBonus = ctx.weaponBonus;

	    // 아이템/강화 포함 전투용 기본 ATK (직업 배율 적용 전)
	    final int atkMinWithItem = ctx.atkMinWithItem; // baseMin + bAtkMin
	    final int atkMaxWithItem = ctx.atkMaxWithItem; // baseMax + weaponBonus + bAtkMax


	    // 리젠/HP, 크리 (calcUserBattleContext에서 직업 패시브/축복/흡혈귀 등 반영한 값)
	    int effRegen    = ctx.effRegen;
	    int effHpMax    = ctx.finalHpMax;  // 최종 전투용 HP_MAX (전사/파이터 HP 보너스 포함이라고 가정)
	    int effCritRate = ctx.shownCrit;
	    int effCriDmg   = ctx.shownCritDmg;

	    // ─────────────────────────────
	    // 4) 직업별 데미지 배율 (궁수 / 전사) - 구버전 로직 복원
	    // ─────────────────────────────
	    double jobDmgMul = 1.0;
	    int jobBonusMin  = 0;
	    int jobBonusMax  = 0;
	    // 전사 HP 보너스는 calcUserBattleContext.finalHpMax 에서 이미 처리했다고 보고
	    // 여기서는 데미지 배율만 적용

	    if ("궁수".equals(job)) {
	        jobDmgMul = 1.6;   // 궁수: 데미지 1.6배
	    } else if ("전사".equals(job)) {
	        jobDmgMul = 1.4;   // 전사: 데미지 1.2배
	    } else if ("검성".equals(job)) {
	        jobDmgMul = 2.5;   // 
	    } else if ("어쎄신".equals(job)) {
	        jobDmgMul = 1.3;   // 
	    } else if ("제너럴".equals(job)) {
	        jobDmgMul = 1.2;   //
	    } else if ("처단자".equals(job)) {
	        jobDmgMul = 1.4;   
	    } else if ("용사".equals(job)) {
	        jobDmgMul = 1.4;   
	    } else if ("복수자".equals(job)) {
	        jobDmgMul = 1.8;   
	    } else if ("음양사".equals(job)) {
	        jobDmgMul = 1.8;   
	    }

	    // 직업 배율까지 반영된 실제 전투용 공격력 (구버전 공식과 동일)
	    int effAtkMin = (int)Math.round(atkMinWithItem * jobDmgMul + jobBonusMin);
	    int effAtkMax = (int)Math.round(atkMaxWithItem * jobDmgMul + jobBonusMax);
	    if (effAtkMax < effAtkMin) effAtkMax = effAtkMin;

	    // 추가로 HP를 덮어쓰고 싶다면 아래처럼 쓸 수도 있지만,
	    // 현재는 calcUserBattleContext.finalHpMax 를 신뢰:
	    // int effHpMax = hpMaxWithItem + jobBonusHp;

	    // 광전사/버서크 배수 (파이터 등에서 사용)
	    double berserkMul = 1.0;

	    // -----------------------------
	    // 5) 부활 처리만 (리젠 X) - 구버전 그대로
	    // -----------------------------
	    String reviveMsg = reviveAfter1hIfDead(userName, roomName, u, effHpMax, effRegen);
	    boolean revivedThisTurn = false;
	    if (reviveMsg != null) {
	        if (!reviveMsg.isEmpty()) return reviveMsg;
	        revivedThisTurn = true;
	    }

	    // 🔹 글로벌(서버 전체) 기준 ACHV 카운트
	    List<AchievementCount> globalList = botNewService.selectAchvCountsGlobalAll();
	    Map<String, Integer> globalAchvMap = new HashMap<>();
	    if (globalList != null) {
	        for (AchievementCount ac : globalList) {
	            if (ac == null || ac.getCmd() == null) continue;
	            globalAchvMap.put(ac.getCmd(), ac.getCnt());
	        }
	    }

	    // 6) 진행중 전투 / 신규 전투 + LUCKY 유지 (구버전 그대로)
	    OngoingBattle ob = botNewService.selectOngoingBattle(userName, roomName);
	    Monster m;
	    int monMaxHp = 0,monAtk =0, monHpRemainBefore;
	    
	 // ✅ 나이트메어 모드 확인
	    boolean nightmare = botNewService.isNightmareMode(userName, roomName);
	    int nightmareMul = nightmare ? 100 : 1;
	    
	    boolean lucky = false;
	    boolean dark = false; // 어둠몬스터 여부
	    boolean gray = false; 
	    
	    int beforeJobSkillYn=0;
	    int killCountForThisMon=0;
	    int nmKillCountForThisMon=0;
	    if (ob != null) {
	        m = botNewService.selectMonsterByNo(ob.monNo);
	        if (m == null) return "진행중 몬스터 정보를 찾을 수 없습니다.";
	        beforeJobSkillYn = ob.beforeJobSkillYn;
	        
	        monMaxHp = m.monHp;
	        monAtk = m.monAtk;
	     // 🔥 나이트메어 증폭
	        if (nightmare) {
	            monMaxHp *= nightmareMul;
	            monAtk *= nightmareMul;
	            m.monLv +=100;
	        }
	        
	        lucky = (ob.luckyYn != null && ob.luckyYn == 1);
	        dark  = (ob.luckyYn != null && ob.luckyYn == 2);
	        gray  = (ob.luckyYn != null && ob.luckyYn == 3);
	        if (dark) {
	        	if(m.monNo <15) {
	        		monMaxHp = monMaxHp * 5;
	        		monAtk = monAtk * 2;
	        	}else if(m.monNo>=25) {
	        		monMaxHp = monMaxHp * 2;
	        		monAtk = (int)Math.round( monAtk * 1.25);
	        	}else if(m.monNo>=15) {
	        		monMaxHp = monMaxHp * 3;
	        		monAtk = (int)Math.round( monAtk * 1.5);
	        	}else{
	        		
	        	}
	        	
	        } 
	        
	        
            monHpRemainBefore = Math.max(0, monMaxHp - ob.totalDealtDmg);
	        
         // ★ 이 유저의 해당 몬스터 누적 킬 수 조회
	        killCountForThisMon = 0;
	        nmKillCountForThisMon = 0;
	        try {
	            List<KillStat> kills = botNewService.selectKillStats(userName, roomName);
	            if (kills != null) {
	                for (KillStat ks : kills) {
	                    if (ks.monNo == m.monNo) {
	                        killCountForThisMon = ks.killCount;
	                        nmKillCountForThisMon = ks.nmKillCount;
	                        break;
	                    }
	                }
	            }
	        } catch (Exception ignore) {}

	    } else {
	        m = botNewService.selectMonsterByNo(u.targetMon);
	        if (m == null) return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";

	        beforeJobSkillYn = -1;
	        
	        monMaxHp = m.monHp;
	        monHpRemainBefore = m.monHp;
	        monAtk = m.monAtk;
	     // 🔥 나이트메어 증폭
	        if (nightmare) {
	            monMaxHp *= nightmareMul;
	            monHpRemainBefore *= nightmareMul;
	            monAtk *= nightmareMul;
	            m.monLv +=100;
	        }
	        
	        // ★ 이 유저의 해당 몬스터 누적 킬 수 조회
	        killCountForThisMon = 0;
	        nmKillCountForThisMon = 0;
	        try {
	            List<KillStat> kills = botNewService.selectKillStats(userName, roomName);
	            if (kills != null) {
	                for (KillStat ks : kills) {
	                    if (ks.monNo == m.monNo) {
	                        killCountForThisMon = ks.killCount;
	                        nmKillCountForThisMon = ks.nmKillCount;
	                        break;
	                    }
	                }
	            }
	        } catch (Exception ignore) {}

	        // ★ 300킬 이상 + 20% 확률이면 어둠몬
	        
	     // ★ 300킬 이상 + 20% 확률이면 어둠몬
	        if ((!nightmare && killCountForThisMon >= 350 && m.monNo >= 15)
	        		
	        		|| (nightmare &&nmKillCountForThisMon > 150 && m.monNo >= 15 ) 
	        		) {
	            double rnd = ThreadLocalRandom.current().nextDouble();
	            if (rnd < 0.05) {
	                dark = true;
	            }
	        }
	        
	        if ((!nightmare && killCountForThisMon >= 300 && m.monNo < 15)
	        		|| (nightmare && nmKillCountForThisMon > 150 && m.monNo < 15)
	        		) {
	            double rnd = ThreadLocalRandom.current().nextDouble();
	            if (rnd < 0.10) {
	                dark = true;
	            }
	        }

	        if ("도사".equals(job)) {
                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE_DOSA;
	        } else {
                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE ;
	        }
	        boolean able_to_lucky_yn = false;
	        if (killCountForThisMon >= 50) {
	            able_to_lucky_yn = true;
	        }
	        

	        if (!able_to_lucky_yn) {
	            lucky = false;
	        }

	       
	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get("ACHV_FIRST_CLEAR_MON_" + m.monNo);
	            if (v != null) globalCnt = v.intValue();
	        }

	        if (dark || globalCnt == 0 ||m.monNo > 50) {
	            lucky = false;
	        } 
	        
	        if (lucky || globalCnt == 0 || m.monNo > 50 ||"사신".equals(job)) {
	        	dark = false;
	        }
	       
	        if ("음양사".equals(job)) {
	        	gray = ThreadLocalRandom.current().nextDouble() < 0.05;
	        }
	        
	        if(gray) {
	        	lucky = false;
	        	dark = false;
	        }
	        
	        /*
	        if (nightmare) {
	        	dark = false;
	        	gray = false;
	        }
	        */
	        
	        if (dark) {
	        	if(m.monNo <15) {
	        		monMaxHp = monMaxHp * 5;
	        		monAtk = monAtk * 2;
	        		monHpRemainBefore = monMaxHp;
	        	}else if(m.monNo>=25) {
	        		monMaxHp = monMaxHp * 2;
	        		monAtk = (int)Math.round( monAtk * 1.25);
	        		monHpRemainBefore = monMaxHp;
	        	}else if(m.monNo>=15) {
	        		monMaxHp = monMaxHp * 3;
	        		monAtk = (int)Math.round( monAtk * 1.5);
	        		monHpRemainBefore = monMaxHp;
	        	}
	        }

	    }
	    
	    
	    

	    // 7) 쿨타임 체크 (param1 그대로 사용)
	    CooldownCheck cd = checkCooldown(userName, roomName, param1, job);
	    if (!cd.ok) {
	        long min = cd.remainSeconds / 60;
	        long sec = cd.remainSeconds % 60;
	        return String.format("%s님, 공격 쿨타임 %d분 %d초 남았습니다.", userName, min, sec);
	    }

	    // 8) 현재 체력 확정 (이전 전투 로그 기준 + 리젠)
	    int effectiveHp = revivedThisTurn
	            ? u.hpCur
	            : computeEffectiveHpFromLastAttack(userName, roomName, u, effHpMax, effRegen);
	    u.hpCur = effectiveHp;

	    // 유저별 업적 카운트
	    List<AchievementCount> userAchvList = botNewService.selectAchvCountsGlobal(userName, roomName);
	    
	    Set<String> achievedCmdSet = new HashSet<>();
	    if (userAchvList != null) {
	        for (AchievementCount ac : userAchvList) {
	            achievedCmdSet.add(ac.getCmd());
	        }
	    }
	    
	    Map<String, Integer> userAchvMap = new HashMap<>();
	    if (userAchvList != null) {
	        for (AchievementCount ac : userAchvList) {
	            if (ac == null || ac.getCmd() == null) continue;
	            userAchvMap.put(ac.getCmd(), ac.getCnt());
	        }
	    }

	    if ("파이터".equals(job) && effHpMax > 0) {
	    	double hpRatio = (double) u.hpCur / effHpMax;
	        if (hpRatio < 1) {
	            berserkMul = 1.0 + (1 - hpRatio) * 0.5;   // 최대 3배
	        }
	    }
	    
	    if ("용사".equals(job) && dark ) {
	        berserkMul = 1.5;
	    }
	    if ("처단자".equals(job) && lucky ) {
	    	berserkMul = 1.5;
	    }
	    if ("음양사".equals(job) && (lucky || dark )) {
	    	berserkMul = 1.5;
	    }
	    
	    /*
	    if ("궁사".equals(job)) {
	        String firstCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get(firstCmd);
	            if (v != null) globalCnt = v.intValue();
	        }

	        if (globalCnt == 0) {
	            return "궁사 최초 토벌에 도전불가!";
	        }
	        
	    }
	    */
	    if ("사신".equals(job)) {
	        String firstCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get(firstCmd);
	            if (v != null) globalCnt = v.intValue();
	        }

	        if (globalCnt == 0) {
	            return "최초 토벌에 도전불가 직업!";
	        }
	        
	    }

	    Flags flags = rollFlags(u, m);

	    // 9) HP 5% 제한 체크
	    int origHpMax = u.hpMax;
	    int origRegen = u.hpRegen;

	    u.hpMax   = effHpMax;
	    u.hpRegen = effRegen;

	    
	    
	    try {
	        String hpMsg = buildBelowHalfMsg(userName, roomName, u, param1);
	        if (!"사신".equals(job)) {
	        	if (hpMsg != null) {
		        	return hpMsg;
		        }
	    	}
	        
	    } finally {
	        u.hpMax   = origHpMax;
	        u.hpRegen = origRegen;
	    }

	    // 10) 도사 버프 (본인 + 방 전체)
	    DosaBuffEffect buffEff_self = null;
	    if ("도사".equals(job) || "음양사".equals(job) ) {
	        buffEff_self = buildDosaBuffEffect(u, u.lv, roomName, 1);
	        effAtkMin   += buffEff_self.addAtkMin;
	        effAtkMax   += buffEff_self.addAtkMax;
	        effCritRate += buffEff_self.addCritRate;
	        effCriDmg   += buffEff_self.addCritDmg;
	        u.hpCur     += buffEff_self.addHp;
	    }

	    DosaBuffEffect buffEff_room = loadRoomDosaBuffAndBuild(roomName);
	    if (buffEff_room != null) {
	        effAtkMin   += buffEff_room.addAtkMin;
	        effAtkMax   += buffEff_room.addAtkMax;
	        effCritRate += buffEff_room.addCritRate;
	        effCriDmg   += buffEff_room.addCritDmg;
	        u.hpCur     += buffEff_room.addHp;
	        botNewService.clearRoomBuff(roomName);
	    }
	    String dosabuffMsg = "";
	    if (buffEff_room != null || buffEff_self != null) {
	        dosabuffMsg = buildUnifiedDosaBuffMessage(buffEff_self, buffEff_room);
	    }

	    // 11) 데미지 계산 (A형 완전 분리 버전)
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
	            effHpMax,
	            beforeJobSkillYn
	    );

	    AttackCalc calc = dmg.calc;
	    flags = dmg.flags;
	    boolean willKill = dmg.willKill;
	    
	 // 🔥 전투 종료 패턴 처리 (패턴 6)
	    if (calc.endBattle) {

	        // ✅ 기존 캐논 전투 종료 로직 재사용
	        botNewService.closeOngoingBattleTx(userName, roomName);

	        // EXP / 드랍 없는 빈 Resolve
	        Resolve emptyResolve = new Resolve();
	        emptyResolve.killed   = false;
	        emptyResolve.gainExp  = 0;
	        emptyResolve.dropCode = "0";

	        return buildAttackMessage(
	            userName, u, m, flags, calc,
	            emptyResolve, null,
	            monHpRemainBefore, monMaxHp,
	            effAtkMin, effAtkMax,   // 표시용 공격력
	            weaponLv, weaponBonus,
	            effHpMax,               // 표시용 HP_MAX
	            null,
	            null,
	            ctx.isReturnUser,
	            nightmare
	        );
	    }
	    

	    // 12) 사망 처리
	    int newHpPreview = Math.max(0, u.hpCur - calc.monDmg);
	    
	 // ☠ 사신: 체력이 0이 되어도 죽지 않고, 대신 공격에 실패
 		 if ("사신".equals(job) && newHpPreview <= 0) {
		     // HP는 1 남기고 버틴다고 가정
		     newHpPreview = 1;
		     // 실제로는 1만 남도록 몬스터 피해 조정
		     calc.monDmg = Math.max(0, u.hpCur - newHpPreview);
		     calc.atkDmg = (int) Math.round(calc.atkDmg*0.5) ;
		     calc.jobSkillUsed = true;  
		     String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		     calc.patternMsg = baseMsg + "사신은 죽음을 거부하고 버텼지만, 약화된 피해를 주었습니다.(50%)";
	
		     // ★ 여기서 바로 리턴하지 않고, 아래 persist() 로직을 타면서
		     //    HP 1, atkDmg=0 상태로 저장되도록 둔다.
		 }
	 
	    String deathAchvMsg = "";
	    if (!"사신".equals(job) && newHpPreview <= 0) {
	    	
	    	 // ✅ 이번에 준 피해 / 몬스터 남은 체력 표시
	        int dealtThisTurn = Math.max(0, calc.atkDmg);
	        int monRemainAfter = Math.max(0, monHpRemainBefore - dealtThisTurn);
	    	
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
	                .setNightmareYn(nightmare?1:0)
	        );

	        deathAchvMsg = grantDeathAchievements(userName, roomName);
	        return userName + "님, 이번전투에서 패배하여, 전투 불능이 되었습니다." + NL
	                + calc.monDmg + " 피해로 사망!" + NL
	                + "▶ 이번에 준 피해: " + dealtThisTurn + NL
	                + "▶ 몬스터 남은 체력: " + monRemainAfter + " / " + monMaxHp + NL
	                + "현재 체력: 0 / " + effHpMax + NL
	                + "5분 뒤 최대 체력의 10%로 부활하며," + NL
	                + "이후 5분마다 HP_REGEN 만큼 서서히 회복됩니다." + NL
	                + deathAchvMsg;
	    }

	    // 13) 처치/드랍 판단
	    Resolve res = resolveKillAndDrop(m, calc, willKill, u, lucky, dark, gray);
	    String newPoint ="";
	    String stealPoint ="";
	 
	    if (res.killed &&nightmare) {
	        res.gainExp *= 50;
	    }
	    
	    
	    // 궁수: 획득 EXP +25%
	    if ("궁수".equals(u.job)) {
	        int baseExp = res.gainExp;
	        int bonus   = (int)Math.floor(res.gainExp * 0.25);
	        res.gainExp = baseExp + bonus;
	    }

	    // 도적: 훔치기
	    String stealMsg = "";
	    if ("도적".equals(job) && !(m.monNo > 50)) {
	        double stealRate = 0.40;
	        int monLv  = m.monNo;
	        switch (monLv) {
		        case 30: stealRate -= 0.05;
		        case 29: stealRate -= 0.05;
		        case 28: stealRate -= 0.05;
		        case 27: stealRate -= 0.05;
		        case 26: stealRate -= 0.05;
		        case 25: stealRate -= 0.05;
		        case 24: stealRate -= 0.05;
		        case 23: stealRate -= 0.05;
		        case 22: stealRate -= 0.05;
	        }

	        if (ThreadLocalRandom.current().nextDouble() < stealRate) {
	            String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	            if (!dropName.isEmpty()) {
	                try {
	                    Integer itemId = botNewService.selectItemIdByName(dropName);
	                    if (itemId != null) {
	                        HashMap<String, Object> inv = new HashMap<>();
	                        inv.put("userName", userName);
	                        inv.put("roomName", roomName);
	                        inv.put("itemId", itemId);
	                        inv.put("qty", 1);
	                        inv.put("delYn", "1");
	                        inv.put("gainType", "STEAL");
	                        botNewService.insertInventoryLogTx(inv);
	                        stealMsg = "✨ " + m.monName + "의 아이템을 훔쳤습니다! (" + dropName + "조각)";
	                        calc.jobSkillUsed = true;
	                    }
	                    stealPoint += " +"+baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,"STEAL",1,nightmare);
	                } catch (Exception ignore) {}
	                
	            }
	        }
	    }
	    
	 // 어쎄신 스틸 (신규 전투 시작 시)
	    if ("어쎄신".equals(job) && m.monNo <= 50) {

	        // 스틸 불가 몬스터

	            // killCountForThisMon ← 이미 위에서 계산됨
	    		int kc = killCountForThisMon;
	    		if(nightmare) {
	    			kc = nmKillCountForThisMon;
	    		}
	            

	            // 기본 30%, 100킬마다 +5%, 1000킬 이상 80%
	            double stealRate = 0.30 + (kc / 100) * 0.05;
	            if (kc >= 1000) {
	                stealRate = 0.8;
	            }
	            if (stealRate > 0.8) {
	                stealRate = 0.8;
	            }

	            if (ThreadLocalRandom.current().nextDouble() < stealRate) {
	                String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	                if (!dropName.isEmpty()) {
	                    try {
	                        Integer itemId = botNewService.selectItemIdByName(dropName);
	                        if (itemId != null) {
	                            HashMap<String, Object> inv = new HashMap<>();
	                            inv.put("userName", userName);
	                            inv.put("roomName", roomName);
	                            inv.put("itemId", itemId);
	                            inv.put("qty", 1);
	                            inv.put("delYn", "1");
	                            inv.put("gainType", "STEAL");
	                            botNewService.insertInventoryLogTx(inv);

	                            stealMsg =
	                                "어쎄신의 조용한 수확..!"+ dropName+
	                                 "조각 획득! ( "+kc +"킬 / "+ (int)(stealRate * 100) + "%) " ;

	                            calc.jobSkillUsed = true;
	                        }
	                        stealPoint += " +"+baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,"STEAL",1,nightmare);
	                    } catch (Exception ignore) {}
	                    
	                }
	            }else {
	            	stealMsg =
                            "어쎄신의 수확! (" +
                            kc + "킬 / " +
                            (int)(stealRate * 100) + "%) " +
                            "실패!";
	            }
	        }
	    
	    if ("처단자".equals(job) && !(m.monNo > 50) && willKill) {
	        double stealRate = 0.3;
	        if (ThreadLocalRandom.current().nextDouble() < stealRate) {
	            String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	            if (!dropName.isEmpty()) {
	                try {
	                    Integer itemId = botNewService.selectItemIdByName(dropName);
	                    if (itemId != null) {
	                        HashMap<String, Object> inv = new HashMap<>();
	                        inv.put("userName", userName);
	                        inv.put("roomName", roomName);
	                        inv.put("itemId", itemId);
	                        inv.put("qty", 2);
	                        inv.put("delYn", "1");
	                        inv.put("gainType", "STEAL");
	                        botNewService.insertInventoryLogTx(inv);
	                        stealMsg = "✨ 날카로운 처단으로 추가획득 (+" + dropName + ")";
	                        calc.jobSkillUsed = true;
	                    }
	                    stealPoint += " +" +baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,"STEAL",2,nightmare);
	                    
	                    
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    
	    if ("용사".equals(job) && !(m.monNo > 50)) {
	        double stealRate = 0.30;
	        if (ThreadLocalRandom.current().nextDouble() < stealRate) {
	            String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	            if (!dropName.isEmpty()) {
	                try {
	                    Integer itemId = botNewService.selectItemIdByName(dropName);
	                    if (itemId != null) {
	                        HashMap<String, Object> inv = new HashMap<>();
	                        inv.put("userName", userName);
	                        inv.put("roomName", roomName);
	                        inv.put("itemId", itemId);
	                        inv.put("qty", 1);
	                        inv.put("delYn", "1");
	                        inv.put("gainType", "STEAL");
	                        botNewService.insertInventoryLogTx(inv);
	                        if(ThreadLocalRandom.current().nextDouble() < 0.5) {
	                        	stealMsg += "✨ " + m.monName + "과  싸우던 마을주민에게서 약탈했다! (" + dropName + "조각)";
	                        }else {
	                        	stealMsg += "✨ 촌장 집에서 " + m.monName + "의 아이템을 발견했다! (" + dropName + "조각)";
	                        }
	                        calc.jobSkillUsed = true;
	                    }
	                    
	                    stealPoint += " +"+baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,"STEAL",1,nightmare);
	                    
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    

	    String dosaCastMsg = null;
	    if ("도사".equals(job)||"음양사".equals(job)) {
	        dosaCastMsg = "✨"+job+"의 기원! 다음 공격자 강화!";
	    }
	    
	    
	    boolean flag1 =false;
	    
	    if(ctx.lifetimeSp < 200000000) {
	    	flag1=true;
	    }/*else if(ctx.lifetimeSp < 25000000) {
	    	flag2=true;
	    }*/
	    
	 // 🔥 드랍 즉시 SP 지급
	   
	    
	    if (res.killed && !"0".equals(res.dropCode)) {

	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {

	            newPoint += " +"+baroSellItem(dropName,0,res,userName,roomName,ctx,u,"DROP",1,nightmare);
	        }
	    }
	    

	    // 14) DB 반영 + 레벨업 처리
	    LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res, effHpMax,ctx.isReturnUser,nightmare);
	    String bonusMsg = "";
	    String blessMsg = "";

	    /*
	    if (u.lv < 8) {
	        blessMsg = grantBlessLevelBonus(userName, roomName, up.beforeLv, up.afterLv);
	    }
	     */
	    String bagDropMsg = "";
	    if (res.killed) {
	        botNewService.closeOngoingBattleTx(userName, roomName);

	        String firstClearMsg = grantFirstClearIfEligible(userName, roomName, m, globalAchvMap);
	        String killAchvMsg   = grantKillAchievements(userName, roomName,achievedCmdSet);
	        String itemAchvMsg   = grantLightDarkItemAchievements(userName, roomName,achievedCmdSet);
	        String bagAchvMsg    = grantBagAcquireAchievementsFast(userName, roomName,achievedCmdSet);
	        String attackAchvMsg = grantAttackCountAchievements(userName, roomName,achievedCmdSet);
	        String jobSkillAchvMsg = grantJobSkillUseAchievementsAllJobs(userName, roomName,achievedCmdSet);
	        String shopSellAchvMsg = grantShopSellAchievementsFast(userName, roomName, achievedCmdSet);
	        
	        String achvRewardMsg = grantAchievementBasedReward(userName, roomName, userAchvList);
	        
	        // 🔹 새로 추가: 직업별 스킬 사용 업적 (이번 턴에 스킬 썼을 때만)
	        
	        if ((firstClearMsg   != null && !firstClearMsg.isEmpty())
	                || (killAchvMsg     != null && !killAchvMsg.isEmpty())
	                || (itemAchvMsg     != null && !itemAchvMsg.isEmpty())
	                || (attackAchvMsg   != null && !attackAchvMsg.isEmpty())
	                || (jobSkillAchvMsg != null && !jobSkillAchvMsg.isEmpty())
	                || (shopSellAchvMsg  != null && !shopSellAchvMsg.isEmpty())
	                || (achvRewardMsg  != null && !achvRewardMsg.isEmpty())
	                || (bagAchvMsg   != null && !bagAchvMsg .isEmpty())
	        		) {

	                   bonusMsg = NL
	                           + firstClearMsg
	                           + killAchvMsg
	                           + itemAchvMsg
	                           + attackAchvMsg
	                           + jobSkillAchvMsg
	                           + shopSellAchvMsg
	                           + achvRewardMsg
	                           + bagAchvMsg ;
	               }

	        bagDropMsg = tryDropBag(userName, roomName, m);
	    }

	    // 15) 메시지 구성
	    int shownMin = effAtkMin;
	    int shownMax = effAtkMax;

	    StringBuilder midExtra = new StringBuilder();
	    StringBuilder botExtra = new StringBuilder();
	    if (dmg.dmgCalcMsg != null && !dmg.dmgCalcMsg.isEmpty()) {
	        midExtra.append(dmg.dmgCalcMsg);
	    }
	    if (dosabuffMsg != null && !dosabuffMsg.isEmpty()) {
	        midExtra.append(NL).append(dosabuffMsg);
	    }
	    if (dosaCastMsg != null && !dosaCastMsg.isEmpty()) {
	        botExtra.append(NL).append(dosaCastMsg);
	    }
	    if (stealMsg != null && !stealMsg.isEmpty()) {
	        botExtra.append(NL).append(stealMsg);
	    }
	    
	    String msg = buildAttackMessage(
	            userName, u, m, flags, calc, res, up,
	            monHpRemainBefore, monMaxHp,
	            shownMin, shownMax,
	            weaponLv, weaponBonus,
	            effHpMax,
	            midExtra.toString(),
	            botExtra.toString(),
	            ctx.isReturnUser,
	            nightmare
	    );

	    if (!bonusMsg.isEmpty()) {
	        msg += bonusMsg;
	    }
	    if (!blessMsg.isEmpty()) {
	        msg += blessMsg;
	    }

	    String celebrationMsg = grantCelebrationClearBonus(userName, roomName, globalAchvMap, userAchvMap);
	    if (celebrationMsg != null && !celebrationMsg.isEmpty()) {
	        msg += NL + celebrationMsg;
	    }

	    // 16) 현재 포인트
	    int curPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (p == null ? 0 : p.intValue());
	    } catch (Exception ignore) {}
	    String curSpStr = formatSpShort(curPoint);
	    if (!stealPoint.isEmpty()) {
	    	msg += "✨추가획득" + stealPoint ;
    		msg +=NL;
	    }
	    
	    if (!newPoint.isEmpty()) {
	    	msg += "✨전투획득" + newPoint;
	    	if(flag1) {
	    		msg+="(누적 200m sp 이하 2배 적용)";
	    	}/*
    		if(flag2) {
    			msg+="(누적 2500만sp 이하 1.5배 적용)";
    		}*/
    		msg +=NL;
	    }
	    msg += "✨포인트: " + curSpStr;

	    if (bagDropMsg != null && !bagDropMsg.isEmpty()) {
	        msg += NL + bagDropMsg;
	    }
	    

	    try {
	        botNewService.execSPMsgTest(map);
	        msg += NL + Objects.toString(map.get("outMsg"), "");
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return msg;
	}
	
	public String baroSellItem(String dropName,Integer itemId,Resolve res,String userName,String roomName,UserBattleContext ctx,User u,String gainType,int qty,boolean nightmare) {
		String newPoint="";
		try {
			if(0 == itemId) {
				itemId = botNewService.selectItemIdByName(dropName);
			}
            Integer basePrice = botNewService.selectItemSellPriceById(itemId);

            if (basePrice != null && basePrice > 0) {

                int gainSp = basePrice;

                if("STEAL".equals(gainType)) {
                	gainSp /= 2;
                	gainSp *= qty;
                }
                
                if(!"STEAL".equals(gainType)) {
                	// 빛 / 어둠 보정
                	if ("9".equals(res.dropCode)) {
                		gainSp *=15;
                	}
                    if ("3".equals(res.dropCode) || "5".equals(res.dropCode)) {
                        gainSp *= 5;
                    }
                    if ("2".equals(res.dropCode)) {
                        gainSp *= 2;
                    }

                   

                }
                
                // 복귀자 보너스
                
                if(nightmare) {
                	gainSp *=50;
                }
                
                if (ctx.isReturnUser) {
                    gainSp *= 2;
                }
                
                if(ctx.lifetimeSp < 200000000) {
                	gainSp *= 2;
                }

    	        
                // SP 즉시 지급
                HashMap<String, Object> pr = new HashMap<>();
                pr.put("userName", userName);
                pr.put("roomName", roomName);
                pr.put("score", (int) gainSp);
                pr.put("cmd", "DROP_SP_"+gainType);

                botNewService.insertPointRank(pr);

                newPoint = formatSpShort(gainSp);
                
                
                // 메시지용
                //stealMsg += NL + "SP 즉시 획득: +" + formatSp(gainSp);

            }

        } catch (Exception ignore) {}
		
		return newPoint;
	}
	
	
	
	public String patchNote(HashMap<String,Object> map) {
		String msg ="";
		try {
			botNewService.execSPPatchNoteTest(map);
			msg += Objects.toString(map.get("outMsg"), "");
	    } catch (Exception e) {
	    	msg ="";
	        e.printStackTrace();
	    }
		return msg;
		
	}
	

	private double computeBagPityMultiplier(String userName, String roomName) {

	    // 1) 최근 가방 먹은 사람인지 확인
		
	    try {
	        List<BagLog> lastDrops = botNewService.selectRecentBagDrops();
	        if (lastDrops != null) {
	            for (BagLog b : lastDrops) {
	                if (b == null) continue;
	                String u = b.getUserName();
	                if (userName.equals(u)) {
	                    // 최근 5개 가방 로그 안에 있으면 → 이미 먹은 사람
	                    return 0.3; //3.5->1.05퍼로 강등 
	                }
	            }
	        }
	    } catch (Exception ignore) {}
		 	    
	    boolean isRising = false;

	    // 2) 최근 6시간 라이징 스타(Top7)인지 확인
	    try {
	        List<HashMap<String,Object>> rising = botNewService.selectRisingStarsTop5Last6h();
	        if (rising != null) {
	            for (HashMap<String,Object> row : rising) {
	                if (row == null) continue;

	                String rn = Objects.toString(row.get("ROOM_NAME"), "");
	                String un = Objects.toString(row.get("USER_NAME"), "");

	                if (roomName.equals(rn) && userName.equals(un)) {
	                    isRising = true;
	                    break;
	                }
	            }
	        }
	    } catch (Exception ignore) {}

	    
	    
	    if (isRising) {
	        // 열심히 때렸는데 최근 가방 기록은 없는 사람 → 드랍율 4배
	        return 5.0;
	    }
	    
	    
	    

	    // 기본값: 보정 없음
	    return 1.0;
	}
	
	private String tryDropBag(String userName, String roomName, Monster m) {

	    // 몬스터에 따른 가방 드랍 확률 (예시)
	    double baseRate = getBagDropRate(m.monNo);
	    
	    // 2) 최근 가방/라이징스타 기반 보정 배율
	    double pityMul = computeBagPityMultiplier(userName, roomName);

	    // 3) 최종 드랍율 (상한 50% 정도로 캡)
	    double finalRate = baseRate * pityMul;
	    //if (finalRate > 0.5) finalRate = 0.5;

	    if (ThreadLocalRandom.current().nextDouble() >= finalRate) {
	        return ""; // 드랍 실패 → 메시지 없음
	    }

	    // 인벤토리에 가방 1개 추가
	    try {
	        HashMap<String,Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId", BAG_ITEM_ID);
	        inv.put("qty", 1);
	        inv.put("delYn", "0");
	        inv.put("gainType", "BAG_DROP");

	        botNewService.insertInventoryLogTx(inv);

	        return "" + m.monName + "이(가) 수상한 가방을 떨어뜨렸습니다! (/가방열기 로 열 수 있습니다.)";
	    } catch (Exception e) {
	        // 실패해도 전투 진행은 깨지 않게
	        // log.error("bag drop error", e);
	        return "";
	    }
	}
	
	private double getBagDropRate(int monNo) {
		return 0.035; //3.5%
		
	    // 예시: 초반 몹은 5%, 후반 보스는 15%
		/*
	    switch (monNo) {
	    
	        case 1: case 2: case 3: case 4: case 5:
	        case 6: case 7: case 8: case 9: case 10:
	            return 0.007;  // 0.7%
	        case 11: case 12: case 13:case 14: case 15:
	            return 0.012;  // 1.2%
	        case 16: case 17: case 18: case 19: case 20:
	            return 0.015;  // 1.5
	        case 21: case 22: case 23: case 24: case 25:
	        	return 0.015;  // 1.5
	        case 26: case 27: case 28: case 29: case 30:
	        	return 0.015;  // 1.5
	        case 51: case 52: case 53: case 61: case 62: case 63:
	        	return 0.005;  // 0.5%
	        case 91:
	        	return 0.02;  // 2%
	        default:
	            return 0;
	    }
	    */
	}

	

	public String sellItem(HashMap<String, Object> map) {
	    final int SHINY_MULTIPLIER = 5; // ✨ 빛템 5배

	    final String userName = Objects.toString(map.get("userName"), "");
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    
	    
	    boolean flag1 = false;
	    boolean flag2 = false;
	    
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

	    User u = botNewService.selectUser(userName, null);
	    //String job = (u == null || u.job == null) ? "" : u.job.trim();
	    //boolean isMerchant = true;

	 // 🔥 여기부터 추가: param1 으로 전체판매 모드 제어
	    if ("기타".equals(itemNameRaw)) {
	        return sellAllByCategory(userName, roomName, u, false); // 잡템 전체판매
	    }
	    if ("장비".equals(itemNameRaw)) {
	        return sellAllByCategory(userName, roomName, u, true);  // 장비 전체판매
	    }
	    if ("무기".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※무기"); // 또는 "무기"
	    }
	    if ("투구".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※투구");   // 또는 "투구"
	    }
	    if ("갑옷".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※갑옷");  // 또는 "갑옷"
	    }
	    if ("반지".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※반지");   // 또는 "반지"
	    }
	    if ("토템".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※토템");  // 또는 "토템"
	    }
	    if ("행운".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※행운");   // 또는 "행운"
	    }
	    if ("날개".equals(itemNameRaw)) {
	    	return sellAllBySlot(userName, roomName, u, "※날개"); // 또는 "전설"
	    }
	    if ("전설".equals(itemNameRaw)) {
	        return sellAllBySlot(userName, roomName, u, "※전설"); // 또는 "전설"
	    }
	    
	    // 숫자로만 들어온 경우: ITEM_ID 로 직접 판매 (/판매 10001)
	    boolean isNumericId = itemNameRaw.matches("\\d+");

	    boolean wantShinyOnly = false;
	    boolean wantDarkOnly  = false;
	    boolean stealOnly     = false;

	    String baseName = itemNameRaw;   // 화면 표기용 기본 이름
	    Integer itemId = null;

	    if (isNumericId) {
	        // 번호로 들어온 경우 → 바로 ITEM_ID 사용
	        try {
	            itemId = Integer.valueOf(itemNameRaw);
	        } catch (Exception ignore) {}

	        // 빛/어둠/조각 모드는 번호 모드에서는 사용하지 않음
	        wantShinyOnly = false;
	        wantDarkOnly  = false;
	        stealOnly     = false;
	    } else {
	        // 🔹 이름으로 들어온 경우 → 기존 빛/어둠/조각 규칙 유지
	        wantShinyOnly = itemNameRaw.startsWith("빛");
	        wantDarkOnly  = itemNameRaw.startsWith("어둠");
	        stealOnly     = itemNameRaw.endsWith("조각");
	        
	        baseName = itemNameRaw.replace("빛", "").replace("어둠", "");
	        if (stealOnly && baseName.endsWith("조각")) {
	            baseName = baseName.substring(0, baseName.length() - 2); // "조각" 두 글자 제거
	        }

	        try {
	            itemId = botNewService.selectItemIdByName(baseName);
	        } catch (Exception ignore) {}
	    }

	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + itemNameRaw;
	    }
	    
	    List<HashMap<String, Object>> rows = botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	    if (rows == null || rows.isEmpty()) return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";

	    // ★ 조각 수량 추가
	    int normalQty = 0, shinyQty = 0, fragQty = 0, darkQty=0;
	    for (HashMap<String, Object> row : rows) {
	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        qty = Math.max(0, qty);

	        if ("STEAL".equalsIgnoreCase(gainType)) {
	            fragQty += qty;
	        } else if ("DROP3".equalsIgnoreCase(gainType)) {
	            shinyQty += qty;
	        } else if ("DROP5".equalsIgnoreCase(gainType)) {
	            darkQty += qty;
	        } else {
	            normalQty += qty;
	        }
	    }

	 // ★ 판매 대상 수량 계산: 조각 모드 vs 일반 모드
	    int haveTotal;
	    if (stealOnly) {
	        haveTotal = fragQty;
	    } else {
	        haveTotal = normalQty + shinyQty + darkQty;
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
	    
	    // 🔹 번호로 들어온 경우에도 실제 아이템명으로 baseName 보정
	    if (itemDetail != null) {
	        String realName = Objects.toString(itemDetail.get("ITEM_NAME"), baseName);
	        baseName = realName;
	    }
	    
	    String itemType = (itemDetail == null) ? "" : Objects.toString(itemDetail.get("ITEM_TYPE"), "");
	    boolean isEquip = "MARKET".equalsIgnoreCase(itemType);
	    
	    int need;
	    if (isEquip) {
	        // 🛡 장비(MARKET)인 경우: 요청 수량과 상관없이 보유분 전체 판매
	        need = haveTotal;
	    } else {
	        // 잡템 / 기타는 기존처럼 요청 수량만큼만 판매
	        need = Math.min(reqQty, haveTotal);
	    }

	    int sold = 0, soldNormal = 0, soldShiny = 0,soldDark=0, soldFrag = 0;
	    long totalSp = 0L;
	    
	    
	    boolean soldMerchantDiscount = false; // BUY_MERCHANT 물건을 실제로 판 적 있는지
	    
	    for (HashMap<String, Object> row : rows) {
	        if (need <= 0) break;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDarkRow  = "DROP5".equalsIgnoreCase(gainType);
	        boolean isStealRow = "STEAL".equalsIgnoreCase(gainType);   // ★ 추가


	        // ★ 모드에 따라 행 필터링
	        // 행 종류 분류
	        boolean isNormalRow = !isShinyRow && !isDarkRow && !isStealRow;

	        if (stealOnly) {
	            // /판매 도토리조각 → STEAL만
	            if (!isStealRow) continue;
	        } else if (wantShinyOnly) {
	            // /판매 빛도토리 → DROP3(빛도토리)만
	            if (!isShinyRow) continue;
	        } else if (wantDarkOnly) {
	            // /판매 어둠도토리 → DROP5(어둠도토리)만
	            if (!isDarkRow) continue;
	        } else {
	            // /판매 도토리 → 일반도토리만
	            if (!isNormalRow) continue;
	        }
	        

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (rid == null || qty <= 0) continue;

	        int take = Math.min(qty, need);
	        if (take <= 0) continue;

	        int unitPrice;

	        if (isShinyRow || isDarkRow) {
	            // ✨빛드랍 기본 5배
	            unitPrice = basePrice * SHINY_MULTIPLIER;
	        } else {
	            // 기본은 아이템 판매가
	            unitPrice = basePrice;
	        }

	        // ★ 조각(STEAL)은 절반 가격
	        if (isStealRow) {
	            unitPrice = (int)Math.floor(unitPrice * 0.5);
	        }
	        
	        if (!isEquip && u.totalSp < 200000000) {
	        	unitPrice *= 2;
	        	flag1 = true;
	        }/*else if (!isEquip && u.totalSp < 25000000) {
	            unitPrice *= 1.5;
	            flag2 = true;
	        }*/
	        
	        

	        if (qty == take) botNewService.updateInventoryDelByRowId(rid);
	        else botNewService.updateInventoryQtyByRowId(rid, qty - take);

	     // 판매 카운트
	        if (isStealRow) {
	            soldFrag += take;
	        } else if (isShinyRow) {
	            soldShiny += take;
	        } else if (isDarkRow) {
	            soldDark += take;
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
	                    + (shinyQty > 0 ? ", 빛" + baseName + " " + shinyQty + "개" : "")
	                    + (darkQty > 0 ? ", 어둠" + baseName + " " + darkQty + "개" : "")
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
	    int remainDark  = Math.max(0, darkQty  - soldDark);
	    int remainFrag   = Math.max(0, fragQty   - soldFrag);  // ★
	    

	    StringBuilder remainSb = new StringBuilder("남은 재고: ");
	    boolean printed = false;
	    
        if (remainNormal > 0) {
            remainSb.append(baseName).append(" ").append(remainNormal).append("개");
            printed = true;
        }
        if (remainShiny > 0) {
            if (printed) remainSb.append(", ");
            remainSb.append("빛").append(baseName).append(" ").append(remainShiny).append("개");
            printed = true;
        }
        if (remainDark > 0) {
        	if (printed) remainSb.append(", ");
        	remainSb.append("어둠").append(baseName).append(" ").append(remainDark).append("개");
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
	    } else if(wantShinyOnly){
	        dispName = "빛" + baseName;
	    } else if(wantDarkOnly){
	        dispName = "어둠" + baseName;
	    }else {
	    	dispName = baseName;
	    }
	    
	    StringBuilder sb = new StringBuilder();
	    sb.append("⚔ ").append(userName).append("님,").append(NL)
	      .append("▶ 판매 완료!").append(NL)
	      .append("- 아이템: ").append(dispName).append(NL)
	      .append("- 판매 수량: ").append(sold).append("개").append(NL)
	      .append("- 합계 적립: ").append(totalSp).append("sp").append(NL)
	      .append("- 현재 포인트: ").append(curPointStr).append(NL)
	      .append(remainSb.toString());

	    if (flag1) {
	        sb.append(NL)
	          .append("✨지원보너스 적용! (10,000,000sp 까지 기타 아이템 판매가 x2)");
	    }
	    if (flag2) {
	    	sb.append(NL)
	    	  .append("✨지원보너스 적용! (25,000,000sp 까지 기타 아이템 판매가 x1.5)");
	    }
	    
		 // 👇 여기 추가
		 if (soldMerchantDiscount) {
		     sb.append(NL)
		       .append("※ 상인 할인으로 구매한 아이템은 할인가(90%) 기준으로 판매되었습니다.");
		 }
	    if (sold < reqQty) {
	        sb.append(NL)
	          .append("(요청 ").append(reqQty).append("개 → 실제 ").append(sold).append("개 판매)");
	    }

	    /*
	    String achvMsg = grantShopSellAchievements(userName, roomName);
	    if (achvMsg != null && !achvMsg.isEmpty()) {
	        sb.append(NL).append("업적").append(NL)
	          .append(achvMsg);
	    }
	    */
	    return sb.toString();
	}
	
	private String sellAllByCategoryFiltered(String userName, String roomName, User u, boolean equipOnly, String slotKey) {
	    final int SHINY_MULTIPLIER = 5;
	    final String NL = BossAttackController.NL;
	    
	    boolean flag1 = false;
	    

	    List<HashMap<String, Object>> rows = botNewService.selectAllInventoryRowsForSale(userName, roomName);
	    if (rows == null || rows.isEmpty()) {
	        if (slotKey != null) return "판매 가능한 " + slotKey + " 아이템이 없습니다.";
	        return equipOnly ? "판매 가능한 장비가 없습니다." : "판매 가능한 잡템이 없습니다.";
	    }

	    Map<Integer, Boolean> equipCache = new HashMap<>();
	    Map<Integer, Integer> priceCache = new HashMap<>();
	    Map<Integer, String>  catCache   = new HashMap<>(); // NEW: itemId -> 카테고리(※무기 등)

	    int sold = 0, soldNormal = 0, soldShiny = 0, soldDark = 0, soldFrag = 0;
	    long totalSp = 0L;
	    boolean soldMerchantDiscount = false;

	    for (HashMap<String, Object> row : rows) {

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        if (rid == null) continue;

	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (qty <= 0) continue;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDarkRow  = "DROP5".equalsIgnoreCase(gainType);
	        boolean isStealRow = "STEAL".equalsIgnoreCase(gainType);

	        Integer itemId = null;
	        try { itemId = botNewService.selectItemIdByRowId(rid); } catch (Exception ignore) {}
	        if (itemId == null || itemId <= 0) continue;

	        // 장비 여부 캐시
	        Boolean isEquipObj = equipCache.get(itemId);
	        if (isEquipObj == null) {
	            HashMap<String, Object> itemDetail = null;
	            try { itemDetail = botNewService.selectItemDetailById(itemId); } catch (Exception ignore) {}
	            String itemType = (itemDetail == null) ? "" : Objects.toString(itemDetail.get("ITEM_TYPE"), "");
	            isEquipObj = "MARKET".equalsIgnoreCase(itemType);
	            equipCache.put(itemId, isEquipObj);
	        }
	        boolean isEquip = Boolean.TRUE.equals(isEquipObj);

	        // 기존 필터(장비 전체/잡템 전체)
	        if (equipOnly && !isEquip) continue;
	        if (!equipOnly && isEquip) continue;

	        // ✅ NEW: 슬롯(카테고리) 필터
	        if (slotKey != null) {
	            String cat = catCache.get(itemId);
	            if (cat == null) {
	                // 기존 attackInfo에서 쓰던 resolveItemCategory(itemId) 재사용 가능
	                // 여기서 "※무기" 같은 문자열을 반환한다고 가정
	                cat = resolveItemCategory(itemId);
	                catCache.put(itemId, cat);
	            }

	            // slotKey를 "※무기" 형태로 맞추는 걸 추천 (제일 안전)
	            // 예: slotKey="※무기"
	            if (!slotKey.equals(cat)) continue;
	        }

	        // 가격 캐시
	        Integer basePriceObj = priceCache.get(itemId);
	        if (basePriceObj == null) {
	            Integer tmpPrice = null;
	            try { tmpPrice = botNewService.selectItemSellPriceById(itemId); } catch (Exception ignore) {}
	            basePriceObj = (tmpPrice == null ? 0 : tmpPrice.intValue());
	            priceCache.put(itemId, basePriceObj);
	        }
	        int basePrice = basePriceObj;
	        if (basePrice <= 0) continue;

	        int unitPrice = basePrice;
	        if (isShinyRow || isDarkRow) unitPrice = basePrice * SHINY_MULTIPLIER;
	        if (isStealRow) unitPrice = (int)Math.floor(unitPrice * 0.5);

	        
	        if (!isEquip && u.totalSp < 200000000) {
	        	unitPrice *= 2;
	        	flag1 = true;
	        	
	        }/*else if (!isEquip && u.totalSp < 25000000) {
	            unitPrice *= 1.5;
	            flag2 = true;
	        }*/
	        
	        int take = qty;
	        botNewService.updateInventoryDelByRowId(rid);

	        if (isStealRow) soldFrag += take;
	        else if (isShinyRow) soldShiny += take;
	        else if (isDarkRow) soldDark += take;
	        else soldNormal += take;

	        sold += take;
	        totalSp += (long) take * (long) unitPrice;
	        
	    }

	    if (sold <= 0) {
	        if (slotKey != null) return "판매 가능한 " + slotKey + " 아이템이 없습니다.";
	        return equipOnly ? "판매 가능한 장비가 없습니다." : "판매 가능한 잡템이 없습니다.";
	    }

	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", (int) totalSp);
	    pr.put("cmd", equipOnly ? "SELL_EQUIP" : "SELL_JUNK");
	    botNewService.insertPointRank(pr);

	    int curPoint = 0;
	    try {
	        Integer curP = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (curP == null ? 0 : Math.max(0, curP));
	    } catch (Exception ignore) {}
	    String curPointStr = String.format("%,d sp", curPoint);

	    String title = (slotKey != null)
	            ? ("- 대상: " + slotKey + " 전체 판매" + NL)
	            : (equipOnly ? "- 대상: 장비 아이템 전체(MARKET)" + NL
	                        : "- 대상: 잡템 전체(장비 제외)" + NL);

	    StringBuilder sb = new StringBuilder();
	    sb.append("⚔ ").append(userName).append("님,").append(NL)
	      .append("▶ 전체 판매 완료!").append(NL)
	      .append(title)
	      .append("- 총 판매 수량: ").append(sold).append("개").append(NL)
	      .append("- 합계 적립: ").append(totalSp).append("sp").append(NL)
	      .append("- 현재 포인트: ").append(curPointStr);

	    if (flag1) {
	        sb.append(NL)
	          .append("✨지원보너스 적용! (200m sp 까지 기타 아이템 판매가 x2)");
	    }
	    /*
	    if (flag2) {
	    	sb.append(NL)
	    	  .append("✨지원보너스 적용! (25,000,000sp 까지 기타 아이템 판매가 x1.5)");
	    }
	    */
	    
	    if (soldNormal > 0) sb.append(NL).append("  · 일반 아이템: ").append(soldNormal).append("개");
	    if (soldShiny  > 0) sb.append(NL).append("  · 빛 아이템: ").append(soldShiny).append("개");
	    if (soldDark   > 0) sb.append(NL).append("  · 어둠 아이템: ").append(soldDark).append("개");
	    if (soldFrag   > 0) sb.append(NL).append("  · 조각: ").append(soldFrag).append("개");

	    if (soldMerchantDiscount) {
	        sb.append(NL).append("※ 상인 할인으로 구매한 아이템은 할인가(90%) 기준으로 판매되었습니다.");
	    }

	    /*
	    String achvMsg = grantShopSellAchievements(userName, roomName);
	    if (achvMsg != null && !achvMsg.isEmpty()) {
	        sb.append(NL).append("업적").append(NL).append(achvMsg);
	    }*/

	    return sb.toString();
	}
	
	private String sellAllBySlot(String userName, String roomName, User u, String slotKey) {
	    // equipOnly = true 로 두고, 슬롯 필터까지 적용
	    return sellAllByCategoryFiltered(userName, roomName, u, true, slotKey);
	}
	private String sellAllByCategory(String userName, String roomName, User u, boolean equipOnly) {
	    return sellAllByCategoryFiltered(userName, roomName, u, equipOnly, null);
	}

	
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
	            if (rank++ >= 9) break;
	        }
	    }
	    sb.append(NL);
	    
	    List<HashMap<String,Object>> masters = botNewService.selectTodayJobMastersAll();

	    sb.append("✨ Today 직업 마스터").append(NL);

	    if (masters == null || masters.isEmpty()) {
	        sb.append("- 데이터 없음").append(NL);
	    } else {
	        for (HashMap<String,Object> row : masters) {
	            String job  = String.valueOf(row.get("JOB"));
	            String name = String.valueOf(row.get("USER_NAME"));
	            int cnt     = Integer.parseInt(String.valueOf(row.get("ATK_CNT")));

	            sb.append("• ")
	              .append(job)
	              .append(" : ")
	              .append(name)
	              .append(" (")
	              .append(cnt)
	              .append("회)")
	              .append(NL);
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
		 // SP / 공격횟수 랭킹
		 // =========================
		 try {
		     List<HashMap<String, Object>> spAtkList = botNewService.selectSpAndAtkRanking();
		     sb.append(NL).append("◆ SP 누적 랭킹 (TOP5)").append(NL);
	
		     if (spAtkList == null || spAtkList.isEmpty()) {
		         sb.append("- 데이터가 없습니다.").append(NL);
		     } else {
		         // SP 순위 정렬 (이미 TOT_SP DESC 이지만, 방어용으로 한 번 더 정렬)
		         List<HashMap<String, Object>> bySp = new ArrayList<>(spAtkList);
		         bySp.sort((a, b) -> Integer.compare(
		                 safeInt(b.get("TOT_SP")),
		                 safeInt(a.get("TOT_SP"))
		         ));
	
		         int rank = 1;
		         for (HashMap<String, Object> row : bySp) {
		             String userName2 = Objects.toString(row.get("USER_NAME"), "-");
		             int lv          = safeInt(row.get("LV"));
		             int totSp       = safeInt(row.get("TOT_SP"));
	
		             sb.append(rank).append("위 ")
		               .append(userName2)
		               .append(" (Lv.").append(lv).append(")")
		               .append(" - ").append(formatSpShort(totSp))
		               .append(NL);
	
		             if (++rank > 5) break;
		         }
		     }
	
		     sb.append(NL).append("◆ 공격 횟수 랭킹 (TOP10)").append(NL);
	
		     if (spAtkList == null || spAtkList.isEmpty()) {
		         sb.append("- 데이터가 없습니다.").append(NL);
		     } else {
		         // 공격 횟수 순위 정렬
		         List<HashMap<String, Object>> byAtk = new ArrayList<>(spAtkList);
		         byAtk.sort((a, b) -> Integer.compare(
		                 safeInt(b.get("ATK_CNT")),
		                 safeInt(a.get("ATK_CNT"))
		         ));
	
		         int rank = 1;
		         for (HashMap<String, Object> row : byAtk) {
		             String userName2 = Objects.toString(row.get("USER_NAME"), "-");
		             int lv          = safeInt(row.get("LV"));
		             int atkCnt      = safeInt(row.get("ATK_CNT"));
	
		             sb.append(rank).append("위 ")
		               .append(userName2)
		               .append(" (Lv.").append(lv).append(")")
		               .append(" - 공격 ").append(String.format("%,d", atkCnt)).append("회")
		               .append(NL);
	
		             if (++rank > 10) break;
		         }
		     }
		     
		     /*
		     sb.append(NL).append("◆ 죽음 극복 랭킹 (TOP7)").append(NL);
		 	
		     if (spAtkList == null || spAtkList.isEmpty()) {
		         sb.append("- 데이터가 없습니다.").append(NL);
		     } else {
		         // 공격 횟수 순위 정렬
		         List<HashMap<String, Object>> byDeath = new ArrayList<>(spAtkList);
		         byDeath.sort((a, b) -> Integer.compare(
		                 safeInt(b.get("DEATH_CNT")),
		                 safeInt(a.get("DEATH_CNT"))
		         ));
	
		         int rank = 1;
		         for (HashMap<String, Object> row : byDeath) {
		             String userName2 = Objects.toString(row.get("USER_NAME"), "-");
		             int deathCnt      = safeInt(row.get("DEATH_CNT"));
	
		             sb.append(rank).append("위 ")
		               .append(userName2)
		               .append(" - 죽음 ").append(String.format("%,d", deathCnt)).append("회")
		               .append(NL);
	
		             if (++rank > 7) break;
		         }
		     }
		     */
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
	    /*
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
	     */
	    sb.append(NL);
	    /* === ⚔ 몬스터 학살자 (기존) === */
	    sb.append("⚔ 몬스터 학살자").append(NL);
	    List<HashMap<String,Object>> killers = botNewService.selectKillLeadersByMonster();
	    if (killers == null || killers.isEmpty()) {
	        sb.append("데이터 없음").append(NL);
	    } else {
	        Integer lastMonNo = null;
	        for (HashMap<String,Object> k : killers) {
	            int monNo       = safeInt(k.get("MON_NO"));
	            String monName  = String.valueOf(k.get("MON_NAME"));
	            String uName    = String.valueOf(k.get("USER_NAME"));
	            int kills       = safeInt(k.get("KILL_COUNT"));

	            if (!java.util.Objects.equals(lastMonNo, monNo)) {
	            	sb.append(monNo).append(".No ").append(monName).append(" 학살자");
	                lastMonNo = monNo;
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

	            sb.append(monNo).append(".No ").append(" ").append(monName).append(monLv).append("Lv")
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

	private String grantAchievementBasedReward(
	        String userName,
	        String roomName,
	        List<AchievementCount> achievements
	) {
	    if (achievements == null || achievements.isEmpty()) {
	        return "";
	    }

	    int achvCnt = 0;
	    for (AchievementCount ac : achievements) {
	        achvCnt += ac.getCnt();
	    }
	    StringBuilder msg = new StringBuilder();

	    try {
	        // 업적 개수 → 지급 아이템 (고정)
	        LinkedHashMap<Integer, Integer> rewardMap = new LinkedHashMap<>();
	        rewardMap.put(50 ,8001);
	        rewardMap.put(80 ,8002);
	        rewardMap.put(100,8003);
	        rewardMap.put(120,8004);
	        rewardMap.put(150,8005);
	        rewardMap.put(170,8006);
	        rewardMap.put(200,8007);
	        rewardMap.put(220,8008);
	        rewardMap.put(250,8009);
	        rewardMap.put(300,8010);
	        rewardMap.put(320,8011);
	        rewardMap.put(350,8012);
	        rewardMap.put(400,8013);
	        rewardMap.put(500,8014);

	        for (Map.Entry<Integer, Integer> e : rewardMap.entrySet()) {
	            int needCnt = e.getKey();
	            int itemId  = e.getValue();

	            if (achvCnt < needCnt) continue;

	            // 이미 지급했는지 체크 (보유 여부)
	            Integer alreadyHave =
	                    botNewService.selectInventoryQty(userName, roomName, itemId);

	            if (alreadyHave != null && alreadyHave > 0) continue;

	            // 지급
	            HashMap<String,Object> inv = new HashMap<>();
	            inv.put("userName", userName);
	            inv.put("roomName", roomName);
	            inv.put("itemId", itemId);
	            inv.put("qty", 1);
	            inv.put("delYn", "0");
	            inv.put("gainType", "ACHV");

	            botNewService.insertInventoryLogTx(inv);

	            msg.append("업적 ")
	               .append(needCnt)
	               .append("개 달성 보상 획득! (")
	               .append("아이템#").append(itemId)
	               .append(")").append(NL);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return msg.toString();
	}

	
	private String grantAttackCountAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet
	) {
	    AttackDeathStat ads = botNewService.selectAttackDeathStats(userName, roomName);
	    if (ads == null) return "";

	    int totalAttacks = ads.totalAttacks;
	    if (totalAttacks <= 0) return "";

	    int[] thresholds = {
	        1000,2000,3000,4000,5000,6000,7000,8000,9000,
	        10000,11000,12000,13000,14000,15000,16000,17000,
	        18000,19000,20000,21000,22000,23000,24000,25000,
	        26000,27000,28000,29000,30000,31000,32000,33000,
	        34000,35000,36000,37000,38000,39000,40000
	    };

	    StringBuilder sb = new StringBuilder();

	    for (int th : thresholds) {
	        if (totalAttacks < th) break;

	        String cmd = "ACHV_ATTACK_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int rewardSp = th * 10;

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, rewardSp, achievedCmdSet
	            )
	        );
	    }

	    return sb.toString();
	}



	private String grantJobSkillUseAchievementsAllJobs(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet
	) {

	    // 1️⃣ 직업별 스킬 사용 누적 수 (쿼리 1회)
	    List<HashMap<String,Object>> rows =
	            botNewService.selectJobSkillUseCountAllJobs(userName, roomName);
	    if (rows == null || rows.isEmpty()) return "";

	    // 2️⃣ 공통 임계치
	    final int[] thresholds = {
	        1, 10, 30, 50, 100, 150,
	        200, 250, 300, 350, 400, 450,
	        500, 600, 700, 800, 900, 1000,1200,1400,1600,1800,2000,2300,2600,3000
	    };

	    StringBuilder sb = new StringBuilder();

	    // 3️⃣ 직업별 처리
	    for (HashMap<String,Object> row : rows) {
	        if (row == null) continue;

	        String jobName = Objects.toString(row.get("JOB"), "").trim();
	        if (jobName.isEmpty()) continue;

	        int totalSkillUse;
	        Object v = row.get("TOTAL_SKILL_USE");
	        if (v instanceof Number) {
	            totalSkillUse = ((Number) v).intValue();
	        } else {
	            totalSkillUse = parseIntSafe(Objects.toString(v, "0"));
	        }

	        if (totalSkillUse <= 0) continue;

	        // 4️⃣ 임계치 달성 여부만 체크 (DB 조회 ❌)
	        for (int th : thresholds) {
	            if (totalSkillUse < th) break; // 정렬 가정 → 효율

	            String cmd = "ACHV_JOB_SKILL_" + jobName + "_" + th;

	            // 이미 달성한 업적이면 스킵 (메모리)
	            if (achievedCmdSet.contains(cmd)) continue;

	            int rewardSp = th * 10; // 기존 정책 유지

	            sb.append(
	                grantOnceIfEligibleFast(
	                    userName,
	                    roomName,
	                    cmd,
	                    rewardSp,
	                    achievedCmdSet
	                )
	            );
	        }
	    }

	    return sb.toString();
	}

	
	private String grantShopSellAchievementsFast(
	        String userName,
	        String roomName,
	        Set<String> achvCmdSet) {

	    final int[][] rules = {
	    	{500,   5000},
	        {1000,  5000},
	        {2000,  10000},
	        {3000,  10000},
	        {4000,  10000},
	        {5000,  20000},
	        {6000,  20000},
	        {7000,  20000},
	        {8000,  20000},
	        {9000,  20000},
	        {10000, 30000}
	    };

	    int soldCount;
	    try {
	        soldCount = botNewService.selectInventorySoldCount(userName, roomName);
	    } catch (Exception e) {
	        return "";
	    }

	    if (soldCount <= 0) return "";

	    StringBuilder sb = new StringBuilder();

	    for (int[] r : rules) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (soldCount < threshold) continue;

	        String cmd = "ACHV_SHOP_SELL_" + threshold;

	        // 🔹 Fast 체크 (쿼리 안 탐)
	        if (achvCmdSet.contains(cmd)) continue;

	        // 🔹 지급
	        HashMap<String,Object> p = new HashMap<>();
	        p.put("userName", userName);
	        p.put("roomName", roomName);
	        p.put("score", rewardSp);
	        p.put("cmd", cmd);

	        botNewService.insertPointRank(p);
	        achvCmdSet.add(cmd); // 중요 ⭐

	        sb.append("✨ 상점 판매 ")
	          .append(threshold)
	          .append("회 달성 보상 +")
	          .append(formatSpShort(rewardSp))
	          .append(" 지급!♬")
	          .append(NL);
	    }

	    return sb.toString();
	}

	/**
	 * 상점/소비로 삭제된 인벤토리 누적 수량 기준 업적 지급
	 * - 기준: TBOT_POINT_NEW_INVENTORY의 DEL_YN='1' QTY 합계
	 * - 업적 CMD: ACHV_SHOP_SELL_{threshold}
	 */
	private String renderMarketListForBuy(List<HashMap<String,Object>> items, String userName, boolean hiddenYn) {
	    if (items == null || items.isEmpty()) {
	        return "▶ " + userName + "님, 구매 가능 아이템" + NL + "- (없음)";
	    }
	    final String allSeeStr = "===";

	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ ").append(userName).append("님").append(NL);
	    sb.append("더보기 리스트에서 선택 후 구매해주세요").append(NL);
	    sb.append("/구매 전체 < 설명보기, /구매 [카테고리]< 카테고리 전체 보기").append(NL);
	    sb.append("/구매 목검  또는  /구매 102").append(NL);
	    sb.append("다중구매: /구매 101,102,401  또는 /구매 목검,도씨검");
	    sb.append(allSeeStr);

	    for (HashMap<String,Object> it : items) {
	        int    itemId   = safeInt(it.get("ITEM_ID"));
	        String name     = String.valueOf(it.get("ITEM_NAME"));
	        int    price    = safeInt(it.get("ITEM_SELL_PRICE"));
	        String ownedYn  = String.valueOf(it.get("OWNED_YN"));
	        String itemType = String.valueOf(it.get("ITEM_TYPE"));

	        // 인벤 쿼리에서 OWN_QTY, MAXED_YN 을 내려주고 있다고 가정
	        int ownQty      = safeInt(it.get("OWN_QTY"));          // 없으면 0
	        String maxedYn  = String.valueOf(it.get("MAXED_YN"));  // 없으면 "null"

	      

	        boolean isEquipType =
	                "MARKET".equalsIgnoreCase(itemType);
	        boolean upgradable = false;
	        /*        
	        (itemId >= 100 && itemId < 200) ||   // 무기
	                (itemId >= 200 && itemId < 300) ||   // 투구
	                (itemId >= 400 && itemId < 500);     // 갑옷
	                */
	        boolean isMaxed = "Y".equalsIgnoreCase(maxedYn);
	     // 🔥 보유템 제외 모드일 때 필터링
	        if (hiddenYn && "Y".equalsIgnoreCase(ownedYn)) {
	            // 👉 강화 가능한 장비이고, 아직 MAX가 아니라면 예외로 보여준다
	            boolean showForUpgrade = isEquipType && upgradable && !isMaxed;
	            if (!showForUpgrade) {
	                // 강화도 안 되고 / 이미 MAX면 숨김
	                continue;
	            }
	        }
	        
	        // 표시용 이름에 (+n) 붙이기 (업그레이드 장비만)
	        String dispName = name;
	        if (isEquipType && upgradable && ownQty > 1) {
	            int plus = ownQty - 1;      // QTY 2 → +1, QTY 3 → +2 ...
	            if (plus > 0) {
	                dispName = name + "(+" + plus + ")";
	            }
	        }

	        // 1행: [ID] 이름 (상태)
	        sb.append("[")
	          .append(itemId)
	          .append("] ")
	          .append(dispName);

	        if ("Y".equalsIgnoreCase(ownedYn)) {
	            if (isEquipType && upgradable) {
	                if ("Y".equalsIgnoreCase(maxedYn)) {
	                    //sb.append(" (최대강화)");
	                } else {
	                    sb.append(" (보유중)");
	                }
	            } else {
	                sb.append(" (구매완료)");
	            }
	        }
	        sb.append(NL);

	        // 2행: 가격
	        sb.append("↘가격: ").append(price).append("sp").append(NL);

	        // 3행 이후: 옵션
	        if (isEquipType && upgradable) {
	            // 🔹 업그레이드 가능한 장비: 현재/다음 옵션 둘 다 보여주기

	            // 현재 기준 QTY (0이면 아직 미보유 → 1개 기준으로 표시)
	            int curQty = (ownQty <= 0 ? 1 : ownQty);
	            
	            String curOpt = buildEnhancedOptionLine(it, curQty);
	            sb.append("↘옵션: ").append(curOpt).append(NL);

	            // 다음 구매시 옵션 (MAX가 아니라면)
	            if (!"Y".equalsIgnoreCase(maxedYn)) {
	                int nextQty = curQty + 1;
	                if (nextQty > 4) nextQty = 4;  // 안전 캡
	                String nextOpt = buildEnhancedOptionLine(it, nextQty);
	                sb.append("↘다음 구매시: ").append(nextOpt).append(NL);
	            } else {
	               //sb.append("↘다음 구매시: (최대 강화 상태입니다)").append(NL);
	            }

	            sb.append(NL);
	        } else {
	            // 🔹 그 외 아이템: 기존 옵션 포맷 그대로
	            sb.append("↘옵션: ")
	              .append(buildEnhancedOptionLine(it, 1))
	              .append(NL).append(NL);
	        }
	    }
	    return sb.toString();
	}


	

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
	        int startHp = (int) Math.ceil(effHpMax * 0.10); // 10%
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
	        sb.append(renderMonsterCompactLine(m,1,false)).append(NL);
	    }
	    return sb.toString();
	}
	
	private CooldownCheck checkCooldown(String userName, String roomName, String param1, String job) {
	    if ("test".equals(param1)) return CooldownCheck.ok();

	    int baseCd = COOLDOWN_SECONDS; // 2분
	    
	    Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
	    if (last == null) return CooldownCheck.ok();

	    long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
	    if (sec >= baseCd) return CooldownCheck.ok();

	    long remainSec = baseCd - sec;
	    return CooldownCheck.blockSeconds(remainSec);
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
		int[] custom = MiniGameUtil.MON_PATTERN_WEIGHTS.get(m.monNo);
		int[] weights;

		if (custom != null && custom.length >= enabled) {
			// 몬스터별로 설정된 가중치가 있고, 패턴 개수만큼 들어있으면 그대로 사용
			weights = Arrays.copyOf(custom, enabled);
		} else {
			// 2) 없으면 기존 공통 로직 사용
			weights = new int[enabled];
			for (int i = 0; i < enabled; i++)
				weights[i] = 1;

			if (enabled == 2) {
				weights[0] = 20;
				weights[1] = 80;
			} else if (enabled == 3) {
				weights[0] = 10;
				weights[1] = 60;
				weights[2] = 30;
			} else if (enabled == 4) {
				weights[0] = 0;
				weights[1] = 60;
				weights[2] = 25;
				weights[3] = 15;
			} else if (enabled == 5) {
				weights[0] = 0;
				weights[1] = 62;
				weights[2] = 7;
				weights[3] = 26;
				weights[4] = 5;
			}
		}

		// 3) 안전장치 (모든 weight가 0일 경우)
		int sum = 0;
		for (int w : weights)
			sum += Math.max(0, w);
		if (sum <= 0) {
			for (int i = 0; i < enabled; i++)
				weights[i] = 1;
			sum = enabled;
		}

		// 4) 가중치 랜덤 픽
		int pick = r.nextInt(sum) + 1;
		int acc = 0;
		for (int i = 0; i < enabled; i++) {
			acc += weights[i];
			if (pick <= acc)
				return i + 1; // 패턴 번호는 1부터
		}
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
		case 5:  

            double rnd = ThreadLocalRandom.current().nextDouble();
            if (rnd < 0.20) {
            	 // 🔥 빈사 패턴: 체력을 1 남기고 공격 연출
                int lethalDmg = Math.max(1, u.hpCur - 1); // 1HP 남기기
                c.atkDmg = 0;  
                c.monDmg = lethalDmg;
                c.patternMsg = name + "의 일격! 당신을 빈사 상태로 몰아넣었습니다!";
            } else {
            	// 🔥 보스 흡혈 패턴
                // 1) 플레이어에게 들어갈 피해 = 보스 ATK의 20%
                int lifeDmg = Math.max(1, (int)Math.round(m.monAtk * 0.2));

                // 2) 플레이어 공격은 0으로 취급 (보스에게 데미지 못 줌)
                //    내부적으로는 보스 회복량을 기록하기 위해 ATK_DMG를 음수로 넣는다.
                //    이렇게 하면 totalDealtDmg가 줄어들어서 "보스 HP 회복" 효과가 난다.
                int heal = lifeDmg * 10;  // 준 피해의 10배를 회복 (오버힐 느낌)
                c.atkDmg = -heal;         // 누적 데미지 감소 → 보스가 heal 만큼 회복

                // 3) 플레이어가 받는 피해
                c.monDmg = lifeDmg;

                // 4) 메시지
                c.patternMsg = name
                        + "의 흡혈 공격! 보스가 공격을 막고, 유저에게 "
                        + lifeDmg + " 피해를 주고, 체력을 "
                        + heal + " 만큼 회복했습니다!";
            }
		    break;
		case 6:
			c.atkDmg = 0;
		    c.monDmg = 0;
		    c.endBattle = true;
		    c.patternMsg = name + "이(가) 울부짖었습니다. 플레이어는 기절했습니다.(전투종료)";
		    break;
		case 7:
			break;
		case 8:
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

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky,boolean dark,boolean gray) {
	    Resolve r = new Resolve();
	    r.killed = willKill;
	    r.lucky  = lucky;
	    r.dark = dark;
	    r.gray = gray;
	    int levelGap = u.lv - m.monLv;
	    double expMultiplier;
	    
	    if (levelGap >= 0) {
	        // 플레이어가 몬스터보다 높을 때
	        expMultiplier = Math.max(0.1, 1.0 - levelGap * 0.1);
	    } else {
	        // 몬스터가 더 강할 때 (보너스)
	        expMultiplier = 1.0 + Math.min(-levelGap, 5) * 0.05; // 레벨 차이 1당 5% 보너스, 최대 25%
	    }

	    int baseKillExp = (int)Math.round(m.monExp * expMultiplier);

	    if (willKill) {
	    	if(gray) {
	    		baseKillExp *= 15;
	    	}else if(dark) {
	    		baseKillExp *= 5;
	    	}else if(lucky) {
	    		baseKillExp *= 3;
	    	}
	    	
	    	r.gainExp = baseKillExp;
	    }else if(c.atkDmg >0){
	    	r.gainExp = (int)Math.round(baseKillExp/20)+1;  //
	    }

	    if ( gray && willKill ) {
	    	r.dropCode = "9";
	    	return r;
	    }
	    if ( lucky && willKill ) {
	        r.dropCode = "3";
	        return r;
	    }
	    if ( dark && willKill ) {
	        r.dropCode = "5";
	        return r;
	    }
	    
	    
	  //기본드랍 100%
	    r.dropCode = "1";
	    
	    
	    
	    
	    boolean normalDrop =
	            ThreadLocalRandom.current().nextDouble(0, 100) < 70;
	    
	    // 30% 감소 
	    if("사신".equals(u.job)) {
	    	if(normalDrop) {
	    		r.dropCode = "1";
	    	}else {
	    		r.dropCode = "0";
	    	}
	    }
	    

	    if(!"사신".equals(u.job)) {
	    	 double extraDropRate = getDropRateByNo(m.monNo);  // ← 새 메서드 사용
	 	    
	 	    boolean extraDrop =
	 	            ThreadLocalRandom.current().nextDouble(0, 100) < extraDropRate;

	 	        if (extraDrop) {
	 	            r.dropCode = "2"; // 🔥 기본 + 추가 드랍
	 	        }
	 	        
	    }
	   
	    //boolean drop = willKill && ThreadLocalRandom.current().nextDouble(0, 100) < dropRate;
	    //r.dropCode = drop ? "1" : "0";
	    return r;
	}
	private double getDropRateByNo(int monNo) {
	    switch (monNo) {
	        case 1:  case 2:  case 3:  case 4:  
	        case 5:  case 6:  case 7:  case 8:  
	        case 9:  case 10: case 11: case 12:
	        	return 30;
	        case 13: case 14: case 16: case 17: 
	        case 18: case 19: case 20: case 21:
	        case 22: case 23: case 24: case 26:
	        case 27: case 28: case 29: case 30:
	        	return 20;
	        	
	        case 15: case 25:
	        	return 25;
	        	
	        case 51: case 52: case 53: 
	        	return 80;
	        case 61: case 62: case 63: 
	        	return 0;
	        case 91: 
	        	return 0;
	        default: 
	        	return 0;
	    }
	}
	
	private int calcBaseHpMax(int lv) {
		int base = lv * 20;
		int bonus = 0;
	    if (lv >= 50)  bonus += (lv - 49) * 20;   
	    if (lv >= 100) bonus += (lv - 99) * 40;  
	    if (lv >= 150) bonus += (lv - 149) * 80;
	    if (lv >= 200) bonus += (lv - 199) * 120; 
		
	    return base+bonus;
	}

	private int calcBaseAtkMin(int lv) {
		int base = lv;

		int bonus = 0;
	    if (lv >= 80)  bonus += (lv - 79) * 1;
	    if (lv >= 150) bonus += (lv - 149) * 2;
	    if (lv >= 190) bonus += (lv - 189) * 3;

	    return base + bonus;
	}

	private int calcBaseAtkMax(int lv) {
		int base = lv * 3;

	    int bonus = 0;
	    if (lv >= 60)  bonus += (lv - 59) * 1;
	    if (lv >= 80)  bonus += (lv - 79) * 2;
	    if (lv >= 120)  bonus += (lv - 119) * 3;
	    if (lv >= 150) bonus += (lv - 149) * 4;
	    if (lv >= 180) bonus += (lv - 179) * 5;
	    if (lv >= 210) bonus += (lv - 209) * 6;

	    return base + bonus;
	}

	private int calcBaseCritRate(int lv) {
	    return 10 + (lv - 1) * 2;
	}

	private int calcBaseHpRegen(int lv) {
		int base = lv * 3;
		
		int bonus = 0;
		
		if (lv >= 50)  bonus += (lv - 49) * 3;
		if (lv >= 80)  bonus += (lv - 79) * 5;
		if (lv >= 100) bonus += (lv - 99) * 8;
		if (lv >= 110) bonus += (lv - 109) * 10;
		if (lv >= 120) bonus += (lv - 119) * 15;
		if (lv >= 130) bonus += (lv - 129) * 20;
		if (lv >= 150) bonus += (lv - 149) * 30;
		if (lv >= 160) bonus += (lv - 159) * 35;
		if (lv >= 170) bonus += (lv - 169) * 40;
		if (lv >= 180) bonus += (lv - 179) * 45;
		if (lv >= 190) bonus += (lv - 189) * 50;
		if (lv >= 200) bonus += (lv - 199) * 55;
		if (lv >= 210) bonus += (lv - 209) * 60;
		if (lv >= 220) bonus += (lv - 219) * 65;
		if (lv >= 230) bonus += (lv - 229) * 70;
		if (lv >= 240) bonus += (lv - 239) * 75;
		if (lv >= 250) bonus += (lv - 249) * 80;
		if (lv >= 260) bonus += (lv - 259) * 85;
		if (lv >= 270) bonus += (lv - 269) * 90;
		if (lv >= 280) bonus += (lv - 279) * 95;
		if (lv >= 290) bonus += (lv - 289) * 100;
		if (lv >= 300) bonus += (lv - 299) * 105;

	    return base+bonus;
	}
	
	/** HP/EXP/LV + 로그 저장 (DB에는 '순수 레벨 기반 스탯'만 반영) */
	private LevelUpResult persist(String userName, String roomName,
	                              User u, Monster m,
	                              Flags f, AttackCalc c, Resolve res,int effHpMax,
	                              boolean isReturnUser,boolean nightmare ) {

	    // 1) 최종 HP 계산 (전투 데미지 반영)
	    u.hpCur = Math.max(0, u.hpCur - c.monDmg);

	    // 2) EXP 적용 + 레벨업 (u.lv, u.expCur, u.expNext 변경)
	    LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);
	    
	 // 3) 레벨업이 발생했고, 죽은 게 아니라면 → 실전투 HPMax 기준으로 풀피 회복
	    if (up.levelUpCount > 0 && u.hpCur > 0 && effHpMax > 0) {
	        u.hpCur = effHpMax; // 여기서 109 같은 값으로 올려줌
	    }

	    // 3) 순수 레벨 기준 스탯 계산
	    int baseHpMax    = calcBaseHpMax(u.lv);
	    int baseAtkMin   = calcBaseAtkMin(u.lv);
	    int baseAtkMax   = calcBaseAtkMax(u.lv);
	    int baseCritRate = calcBaseCritRate(u.lv);
	    int baseHpRegen  = calcBaseHpRegen(u.lv);
	    
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
	            	
	            	String gainType="DROP";
	            	if("3".equals(res.dropCode)) {
	            		gainType = "DROP3";
	            	}else if ("5".equals(res.dropCode)) {
	            		gainType = "DROP5";
	            	}else if ("9".equals(res.dropCode)) {
	            		gainType = "DROP9";
	            	}
	            	
	            	
	            	int qty=1;
	            	if ("2".equals(res.dropCode)) {
	            	    qty = 2; // 기본 1 + 추가 1
	            	}

	            	
	                Integer itemId = botNewService.selectItemIdByName(dropName);
	                if (itemId != null) {
	                    HashMap<String, Object> inv = new HashMap<>();
	                    inv.put("userName",  userName);
	                    inv.put("roomName",  roomName);
	                    inv.put("itemId",    itemId);
	                    if (isReturnUser) {
	                    	inv.put("qty",qty*2);
	                    }else {
	                    	inv.put("qty",qty);
	                    }
	                    inv.put("delYn",     "1");
	                    inv.put("gainType", gainType);
	                    botNewService.insertInventoryLogTx(inv);
	                }
	            } catch (Exception ignore) {
	                // 드랍 저장 실패해도 전투 진행은 계속
	            }
	        }
	    }

	    // 7) BattleLog 저장 (전투 당시 정보 기준)
	    
	    
	    int dropAsInt = 0; 
	    if( res.dropCode == "9") {
	    	dropAsInt = 9;
	    }else if( res.dropCode == "5") {
	    	dropAsInt = 5;
	    }if( res.dropCode == "3") {
	    	dropAsInt = 3;
	    }if( res.dropCode == "2") {
	    	dropAsInt = 2;
	    }if( res.dropCode == "1") {
	    	dropAsInt = 1;
	    }else {
	    	dropAsInt = 0;
	    }

	    
	    int buffYn = 0;
	    
	    if (u.job !=null && "도사".equals(u.job.trim()) || "음양사".equals(u.job.trim())) {   // job 은 u.job.trim()
	        buffYn = 1;
	    }

	    int luckyYn=0;
	    if(res.gray) {
	    	luckyYn =3;
	    }else if(res.dark) {
	    	luckyYn =2;
	    }else if(res.lucky) {
	    	luckyYn =1;
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
	        .setLuckyYn(luckyYn)
	        .setDropYn(dropAsInt)
	    	.setBuffYn(buffYn)
	    	.setJobSkillYn(c.jobSkillUsed ? 1 : 0)
	    	.setJob(u.job)
	    	.setNightmareYn(nightmare?1:0);

	    botNewService.insertBattleLogTx(log);
	    return up;
	}

	class DropSummary {
	    int normal;
	    int fragment;
	    int light;
	    int dark;
	    int gray;
	}

	private String buildAttackMessage(
	        String userName, User u, Monster m, Flags flags, AttackCalc calc,
	        Resolve res, LevelUpResult up,
	        int monHpRemainBefore, int monMaxHp,
	        int shownAtkMin, int shownAtkMax,
	        int weaponLv, int weaponBonus,
	        int displayHpMax, // ← 표시용 HP Max(아이템 포함)
	        String midExtraLines,
	        String botExtraLines,
	        boolean isReturnUser,
	        boolean nightmare
	) {
	    StringBuilder sb = new StringBuilder();

	    // 헤더
	    sb.append("⚔ ").append(userName).append("님, ").append(NL)
	      .append("▶ ").append(m.monName);
	    if(nightmare) sb.append("[나이트메어]");
	    
	    sb.append("을(를) 공격!").append(NL).append(NL);

	    if (res.gray) {
	    	sb.append("✨ LIGHT&DARK MONSTER! (처치시 경험치×15, 음양 드랍)").append(NL);
	    }
	    if (res.dark) {
	    	sb.append("✨ DARK MONSTER! (처치시 경험치×5, 어둠 드랍)").append(NL);
	    }
	    if (res.lucky) {
	        sb.append("✨ LUCKY MONSTER! (처치시 경험치×3, 빛 드랍)").append(NL);
	    }

	    // 치명타
	    if (flags.atkCrit) sb.append("✨ 치명타!").append(NL);
	    
	    // 데미지
	    sb.append("⚔ 데미지: (").append(shownAtkMin).append("~").append(shownAtkMax).append(" ⇒ ");
	    if (flags.atkCrit && calc.baseAtk > 0 && calc.critMultiplier >= 1.0) {
	        sb.append(calc.baseAtk).append("*").append(trimDouble(calc.critMultiplier)).append("=>").append(calc.atkDmg);
	    } else {
	        sb.append(calc.atkDmg);
	    }
	    sb.append(")").append(NL);

	    if (midExtraLines != null && !midExtraLines.isEmpty()) {
	        sb.append(midExtraLines).append(NL).append(NL);
	    }
	    
	    // 몬스터 HP
	    int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);
	    sb.append("❤️ 몬스터 HP: ").append(monHpAfter).append(" / ").append(monMaxHp).append(NL);

	    // 반격
	    if (calc.patternMsg != null && !calc.patternMsg.isEmpty()) {
	        sb.append(NL).append("⚅ ").append(calc.patternMsg).append(NL);
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
	        	if ("9".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: 음양").append(dropName).append(NL);
	            }else if ("5".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: 어둠").append(dropName).append(NL);
	            } else if ("3".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: 빛").append(dropName).append(NL);
	            } else if ("2".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: ").append(dropName).append(" x2");
	            } else {
	                sb.append("✨ 드랍 획득: ").append(dropName).append(NL);
	            }
	        	
	        	if(isReturnUser) {
        	    	sb.append("x2 (복귀bonus) ");
	        	}
	        	sb.append(NL);
	        	
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
	    
	    if (botExtraLines != null && !botExtraLines.isEmpty()) {
	        sb.append(botExtraLines).append(NL);
	    }
	    
	    return sb.toString();
	}

	/* ===== utils ===== */

	private String trimDouble(double v) {
		return String.format("%.2f", v);
	    
	}

	// 이름은 기존 그대로 두고, 현재는 20% 기준으로 동작
	private int minutesUntilReach30(User u, String userName, String roomName) {
	    int threshold = (int)Math.ceil(u.hpMax * 0.05); // ✅ 5% 기준
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
	      .append("(최대체력의 5%까지 회복 필요 ").append(regenWaitMin).append("분, ")
	      .append("쿨타임 ").append(remainMin).append("분 ").append(remainSec).append("초)").append(NL)
	      .append("현재 체력: ").append(u.hpCur).append(" / ").append(u.hpMax)
	      .append(", 5분당 회복 +").append(u.hpRegen).append(NL);

	    String sched = buildRegenScheduleSnippetEnhanced(userName, roomName, u, waitMin);
	    if (sched != null) sb.append(sched).append(NL);

	    return sb.toString();
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
	private static class Resolve {
		boolean killed; String dropCode; int gainExp; boolean lucky; boolean dark; boolean gray;
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

	     // 🔥 핵심: 레벨 기준 재계산
	        int newHpMax   = calcBaseHpMax(lv);
	        int newAtkMin = calcBaseAtkMin(lv);
	        int newAtkMax = calcBaseAtkMax(lv);
	        int newCrit   = calcBaseCritRate(lv);
	        int newRegen  = calcBaseHpRegen(lv);

	        hpDelta     += (newHpMax   - hpMax);
	        atkMinDelta += (newAtkMin - atkMin);
	        atkMaxDelta += (newAtkMax - atkMax);
	        critDelta   += (newCrit   - crit);
	        regenDelta  += (newRegen  - regen);
	        
	        hpMax   = newHpMax;
	        atkMin = newAtkMin;
	        atkMax = newAtkMax;
	        crit   = newCrit;
	        regen  = newRegen;
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
	private String renderMonsterCompactLine(Monster m, int userLv,boolean nightmare) {

		// 드랍 아이템명 및 판매가격
	    String dropName = (m.monDrop != null ? m.monDrop : "-");
	    int dropPrice = getDropPriceByName(dropName);

	    if(nightmare) {
	    	m.monAtk = m.monAtk*100;
	    	m.monHp = m.monHp*100;
	    	dropPrice = dropPrice*50;
	    }

	    // ATK 범위 계산 (50% ~ 100%)
	    int atkMin = (int) Math.floor(m.monAtk * 0.5);
	    int atkMax = m.monAtk;

	 // EXP 보정 계산 (resolveKillAndDrop 과 동일)
	    int baseExp = Math.max(0, m.monExp);
	    int levelGap = userLv - m.monLv;
	    double expMultiplier;

	    if (levelGap >= 0) {
	        // 플레이어가 몬스터보다 높을 때 → 패널티
	        expMultiplier = Math.max(0.1, 1.0 - levelGap * 0.1);
	    } else {
	        // 몬스터가 더 강할 때 → 보너스
	        expMultiplier = 1.0 + Math.min(-levelGap, 5) * 0.05; // 레벨 차 1당 5%, 최대 25%
	    }

	    int effExp = (int)Math.round(baseExp * expMultiplier);
	    boolean hasPenalty = (levelGap >= 0 && expMultiplier < 1.0);
	    boolean hasBonus   = (levelGap < 0  && expMultiplier > 1.0);

	    
	    
	    
	    StringBuilder sb = new StringBuilder();

	    // 1행: 기본 정보
	    sb.append(m.monNo).append(". ").append(m.monName).append(" [").append(m.monLv).append("lv]")
	      .append(" ❤️HP ").append(m.monHp)
	      .append(" ⚔ATK ").append(atkMin).append("~").append(atkMax)
	      .append(NL);

	    
	 // 🔹 3행: 몬스터 패턴 정보 (mon_patten = 최대 패턴 번호)
	    int patMax = m.monPatten; // 예: 4라면 1~4까지 사용됨
	    if (patMax > 0) {
	        sb.append("▶ 패턴(").append(patMax).append("): ");

	        boolean first = true;
	        for (int pat = 1; pat <= patMax; pat++) {
	            String desc = null;
	            switch (pat) {
	                case 1: desc = "1: 주시"; break;
	                case 2: desc = "2: 공격"; break;
	                case 3: desc = "3: 방어"; break;
	                case 4: desc = "4: 필살기(최댐*1.5)"; break;
	                case 5: desc = "5: 흡혈/즉사급피해"; break; // 필요하면
	                default: break;
	            }

	            if (desc != null) {
	                if (!first) sb.append(", ");
	                sb.append(desc);
	                first = false;
	            }
	        }
	        sb.append(NL);
	    }

	    // 2행: 보상 정보
	    sb.append("▶ 보상: EXP ").append(effExp);
	    if (hasPenalty) sb.append("▼");
	    else if (hasBonus) sb.append("▲");
	    sb.append(" / ").append(dropName).append(" ").append(dropPrice).append("sp")
	      .append(NL);


	    // 🔹 4행: 추가 설명 (mon_note)
	    String note = (m.monNote != null ? m.monNote.trim() : "");
	    if (!note.isEmpty()) {
	        sb.append("※ ").append(note).append(NL);
	    }
	    sb.append(NL);

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

	    int rewardSp = calcFirstClearReward(m.monNo);
	    

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
	            + formatSpShort(rewardSp) + " 지급되었습니다." + NL;
	}

	
	private String grantOnceIfEligibleFast(
	        String userName,
	        String roomName,
	        String achvCmd,
	        int rewardSp,
	        Set<String> achievedCmdSet
	) {
	    if (rewardSp <= 0) return "";

	    // ✅ 메모리에서만 중복 체크
	    if (achievedCmdSet.contains(achvCmd)) {
	        return "";
	    }

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("cmd", achvCmd);

	    botNewService.insertPointRank(pr);

	    // ✅ 즉시 Set 갱신 (같은 공격 내 중복 방지)
	    achievedCmdSet.add(achvCmd);

	    return "✨ 업적 달성! [" + achvCmd + "] 보상 +" + formatSpShort(rewardSp) + " 지급되었습니다." + NL;
	}
	

	private boolean isSkeleton(Monster m) {
	    if (m == null) return false;
	    if (m.monNo == 10||m.monNo ==14||m.monNo ==15||m.monNo ==25||m.monNo ==28) return true;
	    if (m.monName.equals("해골")||m.monName.equals("리치")||m.monName.equals("하급악마")
	    		||m.monName.equals("중급악마")||m.monName.equals("미이라")) {
	    	return true;
	    }
	    return false;
	}
	
	/** 통산 킬수 업적 보상 */
	private int calcTotalKillReward(int threshold,boolean nightmareYn) {
		
		int val = 0;
		
	    switch (threshold) {
	    	case 1:  val = 50; break;
	        case 300:  val = 100; break;
	        case 500:  val = 300; break;
	        case 1000: val = 500; break;
	        case 2000: val = 1000; break;
	        case 3000: val = 3000; break;
	        case 4000: val = 10000; break;
	        case 5000: val = 50000; break;
	        case 6000: val = 50000; break;
	        case 7000: val = 100000; break;
	        case 8000: val = 100000; break;
	        case 9000: val = 150000; break;
	        case 10000: val = 150000; break;
	        case 11000: val = 200000; break;
	        case 12000: val = 200000; break;
	        case 13000: val = 250000; break;
	        case 14000: val = 250000; break;
	        case 15000: val = 300000; break;
	        case 16000: val = 300000; break;
	        case 17000: val = 300000; break;
	        case 18000: val = 300000; break;
	        case 19000: val = 300000; break;
	        case 20000: val = 300000; break;
	        case 21000: val = 400000; break;
	        case 22000: val = 400000; break;
	        case 23000: val = 450000; break;
	        case 24000: val = 450000; break;
	        case 25000: val = 500000; break;
	        case 26000: val = 500000; break;
	        case 27000: val = 550000; break;
	        case 28000: val = 550000; break;
	        case 29000: val = 600000; break;
	        case 30000: val = 600000; break;
	        case 31000: val = 700000; break;
	        case 32000: val = 700000; break;
	        case 33000: val = 750000; break;
	        case 34000: val = 750000; break;
	        case 35000: val = 800000; break;
	        case 36000: val = 800000; break;
	        case 37000: val = 850000; break;
	        case 38000: val = 850000; break;
	        case 39000: val = 900000; break;
	        case 40000: val = 900000; break;
	        default:   val = 0;
	    }
	    
	    if(nightmareYn) {
	    	val *= 20;
	    }
	    
	    return val;
	}
	/**
	 * 몬스터별(50/100킬) + 통산 킬 업적 처리
	 * - room 단위로 동작
	 * - TBOT_POINT_RANK.CMD 기반 1회성 지급
	 */
	private String grantKillAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet
	) {
	    List<KillStat> ksList = botNewService.selectKillStats(userName, roomName);
	    if (ksList == null || ksList.isEmpty()) return "";

	    StringBuilder sb = new StringBuilder();
	    int totalKills = 0;
	    int totalNmKills = 0;

	    int[] perMonThresholds = {1,50,100,300,500,1000,2000,3000,4000,5000,6000,7000,8000,9000,10000};

	    for (KillStat ks : ksList) {
	        int monNo = ks.monNo;
	        int kills = ks.killCount;
	        totalKills += kills;
	        totalNmKills += ks.nmKillCount;
	        
	        for (int th : perMonThresholds) {
	            if (kills < th) break;

	            String cmd = "ACHV_KILL" + th + "_MON_" + monNo;
	            if (achievedCmdSet.contains(cmd)) continue;

	            int reward = th * monNo / 2;

	            sb.append(
	                grantOnceIfEligibleFast(
	                    userName, roomName, cmd, reward, achievedCmdSet
	                )
	            );
	        }
	    }

	    int[] totalThresholds = {
	        1,50,100,300,500,1000,2000,3000,4000,5000,
	        6000,7000,8000,9000,10000
	        ,11000,12000,13000,14000,15000,16000,17000,18000,19000,20000
	        ,21000,22000,23000,24000,25000,26000,27000,28000,29000,30000
	        ,31000,32000,33000,34000,35000,36000,37000,38000,39000,40000
	    };

	    for (int th : totalThresholds) {
	        if (totalKills < th) break;

	        String cmd = "ACHV_KILL_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int reward = calcTotalKillReward(th,false);

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, reward, achievedCmdSet
	            )
	        );
	    }
	    
	    for (int th : totalThresholds) {
	        if (totalNmKills < th) break;

	        String cmd = "ACHV_KILL_NIGHTMARE_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int reward = calcTotalKillReward(th,true);

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, reward, achievedCmdSet
	            )
	        );
	    }

	    return sb.toString();
	}

	private String grantLightDarkItemAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet
	) {
	    int lightTotal = 0;
	    int darkTotal  = 0;
	    int grayTotal  = 0;

	    List<HashMap<String, Object>> gainRows =
	            botNewService.selectTotalGainCountByGainType(userName, roomName);

	    if (gainRows != null) {
	        for (HashMap<String, Object> row : gainRows) {
	            String type = Objects.toString(row.get("GAIN_TYPE"), "");
	            int qty = parseIntSafe(Objects.toString(row.get("TOTAL_QTY"), "0"));

	            if ("DROP3".equals(type)) lightTotal = qty;
	            else if ("DROP5".equals(type)) darkTotal = qty;
	            else if ("DROP9".equals(type)) grayTotal = qty;
	        }
	    }

	    if (lightTotal <= 0 && darkTotal <= 0 && grayTotal <= 0) return "";

	    int[] thresholds = {1,10,50,100,300,500,700,1000,1300,1600,2000
	    		,2400,2800,3300,3800,4300,4900,5500,6100};
	    StringBuilder sb = new StringBuilder();

	    for (int th : thresholds) {
	        if (lightTotal >= th) {
	            String cmd = "ACHV_LIGHT_ITEM_" + th;
	            if (!achievedCmdSet.contains(cmd)) {
	                sb.append(
	                    grantOnceIfEligibleFast(
	                        userName, roomName, cmd,
	                        calcLightItemReward(th),
	                        achievedCmdSet
	                    )
	                );
	            }
	        }
	        if (darkTotal >= th) {
	            String cmd = "ACHV_DARK_ITEM_" + th;
	            if (!achievedCmdSet.contains(cmd)) {
	                sb.append(
	                    grantOnceIfEligibleFast(
	                        userName, roomName, cmd,
	                        calcDarkItemReward(th),
	                        achievedCmdSet
	                    )
	                );
	            }
	        }
	        if (grayTotal >= th) {
	            String cmd = "ACHV_GRAY_ITEM_" + th;
	            if (!achievedCmdSet.contains(cmd)) {
	                sb.append(
	                    grantOnceIfEligibleFast(
	                        userName, roomName, cmd,
	                        calcGrayItemReward(th),
	                        achievedCmdSet
	                    )
	                );
	            }
	        }
	    }

	    return sb.toString();
	}

	
	private int calcLightItemReward(int th) {
	    // 예시: 빛템은 kill 업적보다 살짝 약하게
	    // th = 1,10,50, ... 기준
	    if (th <= 1)   return 100;
	    if (th <= 10)  return 500;
	    if (th <= 50)  return 2000;
	    if (th <= 100) return 4000;
	    if (th <= 300) return 8000;
	    if (th <= 500) return 12000;
	    if (th <= 1500)return 20000;
	    if (th <= 2000)return 30000;
	    if (th <= 3000)return 40000;
	    if (th <= 4000)return 50000;
	    if (th <= 5000)return 100000;
	    if (th <= 6000)return 200000;
	    if (th <= 7000)return 300000;
	    return 0;
	}

	private int calcDarkItemReward(int th) {
	    // 예시: 어둠템은 좀 더 희귀하다고 가정해서 빛템보다 1.5배 정도
	    int base = calcLightItemReward(th);
	    return (int)Math.round(base * 1.5);
	}
	private int calcGrayItemReward(int th) {
		// 예시: 어둠템은 좀 더 희귀하다고 가정해서 빛템보다 1.5배 정도
		int base = calcLightItemReward(th);
		return (int)Math.round(base * 10);
	}
	
	private String grantCelebrationClearBonus(
	        String userName,
	        String roomName,
	        Map<String, Integer> globalAchvMap,
	        Map<String, Integer> userAchvMap
	) {

	    StringBuilder sb = new StringBuilder();

	    List<Monster> mons = botNewService.selectAllMonsters();
	    
	    // ⭐ NEW: 내 레벨 한 번만 조회
	    User u = botNewService.selectUser(userName, null);
	    int myLv = (u == null ? 0 : u.lv);

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
	        
	     // ⭐ NEW ①: 내가 이 몬스터의 최초토벌자인 경우 → 축하보상 스킵
	        int myFirstCnt = 0;
	        if (userAchvMap != null) {
	            Integer v = userAchvMap.get(firstCmd); // firstCmd = ACHV_FIRST_CLEAR_MON_X
	            if (v != null) myFirstCnt = v.intValue();
	        }
	        if (myFirstCnt > 0) {
	            // 나는 이미 이 몬스터의 '최초토벌' 업적을 가진 사람 → 축하보상 대상에서 제외
	            continue;
	        }

	        // ⭐ NEW ②: 내 레벨이 몬스터 레벨 미만이면 축하보상 스킵
	        
	        if (myLv + 30 < m.monLv) {
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
	          .append( formatSpShort(rewardShared) ).append(" 지급되었습니다!")
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
	        case 15: return 5000;
	        case 16: return 5000;
	        case 17: return 5000;
	        case 18: return 15000;
	        case 19: return 25000;
	        case 20: return 35000;
	        case 21: return 45000;
	        case 22: return 60000;
	        case 23: return 75000;
	        case 24: return 100000;
	        case 25: return 200000;
	        case 26: return 200000;
	        case 27: return 200000;
	        case 28: return 200000;
	        case 29: return 250000;
	        case 30: return 300000;
	    }
	    return 0;
	}
	
	/**
	 * 업적 리스트를:
	 * - 축하보상 숨기고
	 * - 통산 처치 / 몬스터별 킬 / 죽음 극복 은 [..] 형태로 묶어서 출력
	 */
	// 업적 문자열 패턴
	
	private void renderAchievementLinesCompact(
	        StringBuilder sb,
	        List<HashMap<String, Object>> achv,
	        Map<Integer, Monster> monMap) {

	    // ===== 패턴 =====
		Pattern P_BAG_GET =
				Pattern.compile("^가방 획득 (\\d+)회 달성$");
	    Pattern P_TOTAL_KILL =
	            Pattern.compile("^통산 처치 (\\d+)회 달성$");
	    Pattern P_TOTAL_NIGHTMARE_KILL =
	    		Pattern.compile("^나이트메어 통산 처치 (\\d+)회 달성$");
	    Pattern P_DEATH_OVERCOME =
	            Pattern.compile("^죽음 극복 (\\d+)회 달성$");
	    Pattern P_MONSTER_KILL =
	            Pattern.compile("^(.+?) (\\d+)킬 달성$");
	    Pattern P_LIGHT_ITEM_GET =
	            Pattern.compile("^빛 아이템 획득 (\\d+)회 달성$");
	    Pattern P_DARK_ITEM_GET =
	            Pattern.compile("^어둠 아이템 획득 (\\d+)회 달성$");
	    Pattern P_GRAY_ITEM_GET =
	    		Pattern.compile("^음양 아이템 획득 (\\d+)회 달성$");
	    Pattern P_ATTACK_COUNT =
	            Pattern.compile("^통산 공격 (\\d+)회 달성$");
	    Pattern P_JOB_SKILL =
	            Pattern.compile("^(.+?) 스킬 사용 (\\d+)회 달성$");

	    // ===== 집계용 =====
	    SortedSet<Integer> bagGetSteps = new TreeSet<>();
	    SortedSet<Integer> totalKillSteps = new TreeSet<>();
	    SortedSet<Integer> totalNmKillSteps = new TreeSet<>();
	    SortedSet<Integer> deathSteps     = new TreeSet<>();
	    SortedSet<Integer> attackSteps   = new TreeSet<>();
	    SortedSet<Integer> lightSteps    = new TreeSet<>();
	    SortedSet<Integer> darkSteps     = new TreeSet<>();
	    SortedSet<Integer> graySteps     = new TreeSet<>();

	    Map<String, Integer> monsterKills = new LinkedHashMap<>();
	    Map<String, SortedSet<Integer>> jobSkillSteps = new LinkedHashMap<>();

	    List<String> firstClears = new ArrayList<>();

	    // ===== 수집 =====
	    for (HashMap<String, Object> row : achv) {
	        if (row == null) continue;

	        String cmd = Objects.toString(row.get("CMD"), "");
	        String label = formatAchievementLabelSimple(cmd, monMap);
	        if (label == null || label.isEmpty()) continue;

	        label = label.replace("✨", "").trim();

	        if (label.contains("축하보상")) continue;

	        Matcher m;

	        if ((m = P_BAG_GET.matcher(label)).matches()) {
	        	bagGetSteps.add(parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_TOTAL_KILL.matcher(label)).matches()) {
	            totalKillSteps.add(parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_TOTAL_NIGHTMARE_KILL.matcher(label)).matches()) {
	        	totalNmKillSteps.add(parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_DEATH_OVERCOME.matcher(label)).matches()) {
	            deathSteps.add(parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_ATTACK_COUNT.matcher(label)).matches()) {
	            attackSteps.add(parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_LIGHT_ITEM_GET.matcher(label)).matches()) {
	            lightSteps.add(parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_DARK_ITEM_GET.matcher(label)).matches()) {
	            darkSteps.add(parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_GRAY_ITEM_GET.matcher(label)).matches()) {
	        	graySteps.add(parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_JOB_SKILL.matcher(label)).matches()) {
	            String job = m.group(1).trim();
	            int v = parseIntSafe(m.group(2));
	            jobSkillSteps
	                .computeIfAbsent(job, k -> new TreeSet<>())
	                .add(v);
	            continue;
	        }
	        if ((m = P_MONSTER_KILL.matcher(label)).matches()) {
	            String mon = m.group(1).trim();
	            int v = parseIntSafe(m.group(2));
	            monsterKills.put(mon, Math.max(monsterKills.getOrDefault(mon, 0), v));
	            continue;
	        }

	        // 최초 토벌
	        if (label.startsWith("최초토벌")) {
	            firstClears.add(label.replace("최초토벌:", "").trim());
	        }
	    }

	    // ===== 출력 =====

	    // 1️⃣ 통산 기록 (최대값만)
	    sb.append("✨통산기록").append(NL);

	    
	    if (!attackSteps.isEmpty())
	        sb.append("공격: ").append(String.format("%,d", attackSteps.last())).append("회").append(NL);
	    if (!totalKillSteps.isEmpty())
	        sb.append("처치: ").append(String.format("%,d", totalKillSteps.last())).append("마리").append(NL);
	    if (!totalNmKillSteps.isEmpty())
	    	sb.append("나이트메어 처치: ").append(String.format("%,d", totalNmKillSteps.last())).append("마리").append(NL);
	    if (!deathSteps.isEmpty())
	        sb.append("죽음 극복: ").append(String.format("%,d", deathSteps.last())).append("회").append(NL);
	    if (!lightSteps.isEmpty())
	        sb.append("빛 획득: ").append(String.format("%,d", lightSteps.last())).append("회").append(NL);
	    if (!darkSteps.isEmpty())
	        sb.append("어둠 획득: ").append(String.format("%,d", darkSteps.last())).append("회").append(NL);
	    if (!graySteps.isEmpty())
	    	sb.append("음양 획득: ").append(String.format("%,d", graySteps.last())).append("회").append(NL);
	    if (!bagGetSteps.isEmpty())
	    	sb.append("가방 획득: ").append(String.format("%,d", bagGetSteps.last())).append("회").append(NL);
	    sb.append(NL);

	    // 2️⃣ 스킬 숙련 (3개씩)
	    if (!jobSkillSteps.isEmpty()) {
	        sb.append("✨스킬 숙련").append(NL);

	        List<String> rows = new ArrayList<>();
	        for (Map.Entry<String, SortedSet<Integer>> e : jobSkillSteps.entrySet()) {
	            rows.add(e.getKey() + " " + String.format("%,d", e.getValue().last()) + "회");
	        }

	        for (int i = 0; i < rows.size(); i += 3) {
	            sb.append(String.join(" / ",
	                    rows.subList(i, Math.min(i + 3, rows.size()))))
	              .append(NL);
	        }
	        sb.append(NL);
	    }

	    // 3️⃣ 최초 토벌 (한 줄)
	    /*
	    if (!firstClears.isEmpty()) {
	        sb.append("✨최초 토벌: ").append(firstClears.size()).append("종").append(NL);
	        sb.append(String.join(", ", firstClears)).append(NL).append(NL);
	    }
	    */
	    

	    // 4️⃣ 몬스터 처치 (3개씩)
	    if (!monsterKills.isEmpty()) {
	        sb.append("✨몬스터 처치").append(NL);

	        List<String> rows = new ArrayList<>();
	        for (Map.Entry<String, Integer> e : monsterKills.entrySet()) {
	            rows.add(e.getKey() + ": " + String.format("%,d", e.getValue()) + "킬");
	        }

	        for (int i = 0; i < rows.size(); i += 3) {
	            sb.append(String.join(" / ",
	                    rows.subList(i, Math.min(i + 3, rows.size()))))
	              .append(NL);
	        }
	    }
	}

	private String formatAchievementLabelSimple(String cmd, Map<Integer, Monster> monMap) {
	    if (cmd == null || cmd.isEmpty()) return "";

	    // 작은 헬퍼: monNo → 이름 (monMap에서만 조회)
	    java.util.function.Function<Integer, String> findMonName = (Integer monNo) -> {
	        if (monNo == null) return "몬스터#" + monNo;
	        Monster m = null;
	        if (monMap != null) {
	            m = monMap.get(monNo);
	        }
	        return (m == null ? ("몬스터#" + monNo) : m.monName);
	    };

	    // 🔹 최초토벌
	    if (cmd.startsWith("ACHV_FIRST_CLEAR_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_FIRST_CLEAR_MON_".length()));
	            String name = findMonName.apply(monNo);
	            return "✨최초토벌: " + name;
	        } catch (Exception e) {
	            return "최초토벌";
	        }
	    }

	    // 🔹 최초토벌 축하보상
	    if (cmd.startsWith("ACHV_CLEAR_BROADCAST_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_CLEAR_BROADCAST_MON_".length()));
	            String name = findMonName.apply(monNo);
	            return "✨축하보상: " + name;
	        } catch (Exception e) {
	            return "축하보상";
	        }
	    }

	    // 🔹 몬스터별 킬 업적: ACHV_KILL10_MON_3 이런 형태 가정
	    if (cmd.startsWith("ACHV_KILL") && cmd.contains("_MON_")) {
	        try {
	            String[] parts = cmd.substring("ACHV_KILL".length()).split("_MON_");
	            int threshold = Integer.parseInt(parts[0]);   // 10
	            int monNo = Integer.parseInt(parts[1]);       // 3
	            String name = findMonName.apply(monNo);
	            return name + " " + threshold + "킬 달성";
	        } catch (Exception e) {
	            return "킬 업적";
	        }
	    }

	    // 🔹 통산 킬 업적
	    if (cmd.startsWith("ACHV_KILL_TOTAL_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_KILL_TOTAL_".length()));
	            return "통산 처치 " + th + "회 달성";
	        } catch (Exception e) {
	            return "통산 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_KILL_NIGHTMARE_TOTAL_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_KILL_NIGHTMARE_TOTAL_".length()));
	    		return "나이트메어 통산 처치 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "나이트메어 통산 업적";
	    	}
	    }

	    // 🔹 데스 업적
	    if (cmd.startsWith("ACHV_DEATH_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_DEATH_".length()));
	            return "죽음 극복 " + th + "회 달성";
	        } catch (Exception e) {
	            return "죽음 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_SHOP_SELL_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_SHOP_SELL_".length()));
	    		return "상점 판매 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "상점 판매 ";
	    	}
	    }
	    if (cmd.startsWith("ACHV_LIGHT_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_LIGHT_ITEM_".length()));
	    		return "빛 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "빛 아이템 획득";
	    	}
	    }
	    if (cmd.startsWith("ACHV_DARK_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_DARK_ITEM_".length()));
	    		return "어둠 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "어둠 아이템 획득 ";
	    	}
	    }
	    if (cmd.startsWith("ACHV_GRAY_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_GRAY_ITEM_".length()));
	    		return "음양 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "음양 아이템 획득 ";
	    	}
	    }
	    
	    if (cmd.startsWith("ACHV_ATTACK_TOTAL_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_ATTACK_TOTAL_".length()));
	            return "통산 공격 " + th + "회 달성";
	        } catch (Exception e) {
	            return "통산 공격 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_BAG_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_BAG_".length()));
	    		return "가방 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "가방 획득 업적";
	    	}
	    }

	    if (cmd.startsWith("ACHV_JOB_SKILL_")) {
	        try {
	            String rest = cmd.substring("ACHV_JOB_SKILL_".length()); // "궁수_10"
	            String[] parts = rest.split("_");
	            if (parts.length >= 2) {
	                String jobName = parts[0];               // 궁수, 사신, 기사...
	                int th = Integer.parseInt(parts[1]);     // 10
	                return jobName + " 스킬 사용 " + th + "회 달성";
	            } else {
	                return "직업 스킬 사용 업적";
	            }
	        } catch (Exception e) {
	            return "직업 스킬 사용 업적";
	        }
	    }
	    

	    return cmd;
	}

	
	private String grantDeathAchievements(String userName, String roomName) {
	    // 규칙: {사망누적, 보상SP}
	    final int[][] rules = new int[][]{
	        {1,   100},
	        {10,  200},
	        {50,  500},
	        {100, 1000},
	        {300, 3000},
	        {500, 10000}
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
	                      .append("회 달성 보상 +").append( formatSpShort(rewardSp))
	                      .append(" 지급!♬");
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    return sb.toString();
	}
	
	private String grantBagAcquireAchievementsFast(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet
	) {
	    // 🎒 가방 아이템 ID
	    int bagTotal =
	            botNewService.selectTotalBagAcquireCount(userName);

	    if (bagTotal <= 0) return "";

	    // 기존 업적 스타일과 동일한 threshold
	    int[] thresholds = {
	            1, 5, 10, 30, 50, 100,
	            200, 300, 500, 700,
	            1000, 1500, 2000
	    };

	    StringBuilder sb = new StringBuilder();

	    for (int th : thresholds) {
	        if (bagTotal >= th) {
	            String cmd = "ACHV_BAG_" + th;

	            if (!achievedCmdSet.contains(cmd)) {
	                sb.append(
	                    grantOnceIfEligibleFast(
	                        userName,
	                        roomName,
	                        cmd,
	                        calcBagAchvReward(th),
	                        achievedCmdSet
	                    )
	                );
	            }
	        }
	    }

	    return sb.toString();
	}

	private int calcBagAchvReward(int th) {
	    if (th >= 2000) return 20000;
	    if (th >= 1500) return 15000;
	    if (th >= 1000) return 12000;
	    if (th >= 700)  return 8000;
	    if (th >= 500)  return 6000;
	    if (th >= 300)  return 4000;
	    if (th >= 100)  return 2500;
	    if (th >= 50)   return 1500;
	    if (th >= 10)   return 800;
	    if (th >= 5)    return 400;
	    return 200;
	}

	
	private int calcUserEffectiveAtkMax(User u, String roomName) {

	    // -------------------------------
	    // 1) 기본값
	    // -------------------------------
	    int atkMax = u.atkMax;
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

	    //int weaponBonus = getWeaponAtkBonus(0); // 25강부터 +1
	    // 네 구조: max ATK 는 무기레벨 만큼 +1 per level
	    //atkMax += weaponBonus;

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

	    return buildDosaBuffEffect(dosaUser, dosaLv, roomName,0);
	}
	//도사
	private DosaBuffEffect buildDosaBuffEffect(User dosaUser, int dosaLv, String roomName, int selfYn) {
	    DosaBuffEffect eff = new DosaBuffEffect();

	    int dosaAtkMax = calcUserEffectiveAtkMax(dosaUser, roomName);

	    int dosaLvBonus = 0;
	    int dosaCriDmg  = 0;

	    if(selfYn==1) {
	    	dosaLvBonus = (int) Math.round(dosaLv);
	    	dosaCriDmg = (int) Math.round(dosaAtkMax * 0.1);
	    	//dosaCriDmg = (int) Math.round(dosaAtkMax * 0.05);
	    	//eff.addAtkMin   = dosaLvBonus;
	 	    //eff.addAtkMax   = dosaLvBonus;
	 	    //eff.addCritRate = dosaLvBonus;
	 	    //eff.addCritDmg  = dosaCriDmg;
	 	    eff.addHp       = dosaCriDmg*2;
	    }else {
	    	dosaLvBonus = (int) Math.round(dosaLv * 0.5);
	    	dosaCriDmg = (int) Math.round(dosaAtkMax * 0.1);
	    	eff.addAtkMin   = dosaLvBonus;
		    eff.addAtkMax   = dosaLvBonus*3;
		    eff.addCritRate = dosaLvBonus;
		    eff.addCritDmg  = dosaCriDmg/2;
		    eff.addHp       = dosaCriDmg*10;
	    }
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
	        int effHpMax,
	        int beforeJobSkillYn
	) {
	    DamageOutcome out = new DamageOutcome();
	    AttackCalc calc = new AttackCalc();
	    calc.jobSkillUsed = false;

	    StringBuilder extraMsg = new StringBuilder();
	    out.dmgCalcMsg="";

	    // -----------------------------
	    // 1) 공격력 굴림 + 크리티컬
	    // -----------------------------
	    int critRoll = ThreadLocalRandom.current().nextInt(0, 101);
	    int critThreshold = effAtkRateLimit(effCritRate); // 안전빵 방어
	    boolean crit = (critRoll <= critThreshold);

	    int baseAtk = (effAtkMax <= effAtkMin)
	            ? effAtkMin
	            : ThreadLocalRandom.current().nextInt(effAtkMin, effAtkMax + 1);

	    double critMultiplier = Math.max(1.0, effCriDmg / 100.0);
	    
	    // -----------------------------
	    // 2) 추가데미지 로직
	    // -----------------------------
	    
	    if ("궁사".equals(job)) {
	        int step = (int)Math.round(effAtkMax * 0.10);
	        step = Math.max(step, 280); // 최소 200 단위
	        
	        // 1) 연사 횟수 계산
	        int range    = Math.max(0, effAtkMax - effAtkMin); // 최대뎀 - 최소뎀
	        int segments = range / step;
	        int hitCount = Math.max(1, segments + 1);          // 구간+1이 실제 발사 수

	        int totalDmg = 0;
	        StringBuilder multiMsg = new StringBuilder();

	        if (hitCount > 1) {
	            multiMsg.append("궁사의 연사 발동! ")
	                    .append(hitCount).append("연사").append(NL);
	        }

	        // 2) 크리티컬 분배
	        //  - 1타는 무조건 크리
	        //  - 나머지 (2~hitCount) 샷에 대해 남은 크리율을 균등 분배
	        int remainingCritBudget = Math.max(0, effCritRate); // 100은 1타 확정크리용
	        double perHitRateRaw = (hitCount > 1)
	                ? (double) remainingCritBudget / (hitCount - 1)
	                : 0.0;

	        // 2~마지막샷까지 개별 최대 70%
	        if (perHitRateRaw > 75.0) {
	            perHitRateRaw = 75.0;
	        }
	        double perHitRate = perHitRateRaw; // 0.0 ~ 80.0

	        boolean allCrit = true; // 전탄 크리 체크용

	        for (int i = 1; i <= hitCount; i++) {
	            int shotAtk;

	            if (i < hitCount) {
	                // 1샷 ~ (hitCount-1)샷: 구간별 고정값
	                // 1샷: effAtkMin
	                // 2샷: effAtkMin + 280
	                // 3샷: effAtkMin + 560 ...
	                shotAtk = effAtkMin + 280 * (i - 1);
	                if (shotAtk > effAtkMax) {
	                    shotAtk = effAtkMax;
	                }
	            } else {
	                // 마지막 샷: [startLast ~ effAtkMax] 랜덤
	                int startLast = effAtkMin + 280 * (hitCount - 1);
	                if (startLast > effAtkMax) {
	                    startLast = effAtkMax;
	                }

	                if (effAtkMax <= startLast) {
	                    shotAtk = effAtkMax;
	                } else {
	                    shotAtk = ThreadLocalRandom.current()
	                            .nextInt(startLast, effAtkMax + 1);
	                }
	            }
	            
	            double minFactor = 0.3; // 마지막 타 최소 비율 (원하면 0.2~0.4 사이로 튜닝)

	            int maxIdx = (hitCount > 1 ? hitCount - 1 : 1);
	            double factor = 1.0;
	            if (hitCount > 1) {
	                factor = 1.0 - (1.0 - minFactor) * (i - 1) / maxIdx;
	            }
	            shotAtk = (int)Math.round(shotAtk * factor);
	            
	            
	            // 3) 크리 판정
	            boolean shotCrit;
	            if (i == 1) {
	                // 1타는 확정 크리
	                shotCrit = true;
	            } else {
	                int roll = ThreadLocalRandom.current().nextInt(0, 101);
	                shotCrit = (roll <= perHitRate);
	            }

	            int shotDmg = shotCrit
	                    ? (int) Math.round(shotAtk * critMultiplier*0.65)
	                    : shotAtk;

	            totalDmg += shotDmg;
	            if (!shotCrit) {
	                allCrit = false;
	            }

	            if (hitCount > 1) {
	                multiMsg.append(i).append("타: ").append(shotDmg);
	                if (shotCrit) multiMsg.append(" (치명!)");
	                multiMsg.append(NL);
	            }
	        }

	        // 4) 전탄 크리 보너스 (1.1배)
	        if (hitCount > 1 && allCrit) {
	            int before = totalDmg;
	            totalDmg = (int) Math.round(totalDmg * 1.3);
	            multiMsg.append("ALL 치명! ")
	                    .append(before).append(" → ").append(totalDmg)
	                    .append(" (+30%)").append(NL);
	            calc.jobSkillUsed =true;
	        } else if (hitCount > 1) {
	            // 기존 총합 안내
	            multiMsg.append("총합 데미지: ").append(totalDmg).append("!").append(NL);
	        }

	        // 이후 공통 로직에서는 "한 번의 큰 타격"처럼 처리되지만
	        // 실제로는 위에서 연사 데미지로 합산한 값이 들어간다.
	        baseAtk = totalDmg;
	        crit = false;           // 샷별로 이미 크리 반영했으므로 여기서는 의미없음

	        // 궁사 전용 계산 메시지를 out에 남김
	        out.dmgCalcMsg = multiMsg.toString();
	    }
	    
	    if ("궁사2".equals(job)) {

	        // 1) 연사 횟수 계산 (280 차이마다 1연타 증가)
	        int range    = Math.max(0, effAtkMax - effAtkMin); // 최대뎀 - 최소뎀
	        int segments = range / 280;                        // 280 차이마다 1구간
	        int hitCount = Math.max(1, segments + 1);          // 구간+1이 실제 발사 수

	        // 2) 기존 한 번 공격했을 때 데미지(크리 포함)
	        int singleDmg = crit
	                ? (int)Math.round(baseAtk * critMultiplier)
	                : baseAtk;

	        // 3) 연타 보너스: 1타 추가될 때마다 +20%
	        //    hitCount=1 → 1.0배, 2 → 1.2배, 3 → 1.4배, ...
	        double bonusRate = 1.0 + 0.2 * (hitCount - 1);
	        int totalDmg = (int)Math.round(singleDmg * bonusRate);

	        // 4) totalDmg를 hitCount개로 랜덤 분배 (합은 항상 totalDmg)
	        int[] parts = new int[hitCount];
	        int remain = totalDmg;

	        for (int i = 0; i < hitCount; i++) {
	            int slotsLeft = hitCount - i;

	            if (slotsLeft == 1) {
	                // 마지막 타는 남은 데미지 전부
	                parts[i] = remain;
	            } else {
	                // 최소 1은 남기고 랜덤 분배
	                int minVal = 1;
	                int maxVal = remain - (slotsLeft - 1); // 뒤 타들 최소 1씩은 남겨야 함
	                if (maxVal < minVal) {
	                    maxVal = minVal;
	                }
	                int val = ThreadLocalRandom.current().nextInt(minVal, maxVal + 1);
	                parts[i] = val;
	                remain  -= val;
	            }
	        }

	        // 5) 표시용 메시지 구성
	        StringBuilder multiMsg = new StringBuilder();
	        if (hitCount > 1) {
	            multiMsg.append("궁사의 연사 발동! ")
	                    .append(hitCount).append("연타").append(NL);
	        }

	        if (hitCount > 1) {
	            for (int i = 0; i < hitCount; i++) {
	                multiMsg.append(i + 1).append("타: ")
	                        .append(parts[i]).append(NL);
	            }
	            multiMsg.append("총합 데미지: ")
	                    .append(totalDmg).append(NL);
	            //calc.jobSkillUsed = true;
	        }

	        // 6) 실제 전투용 데미지는 totalDmg 한 번만 사용
	        baseAtk = totalDmg;
	        crit    = false;           // 크리티컬은 singleDmg 안에 이미 반영 끝
	        out.dmgCalcMsg = multiMsg.toString();
	    }


	    if ("저격수".equals(job)) {
	    	
	    	baseAtk = (effAtkMin + effAtkMax + 1) /2;
	    	
	    	switch(beforeJobSkillYn) {
	    		case 0:
	    			
		        	if (ThreadLocalRandom.current().nextDouble() < 0.13) {
		        		out.dmgCalcMsg += "[헤드샷] 보너스 DMG "+baseAtk+"→";
		        		baseAtk = (int)Math.round(baseAtk * 3.75);
		        		out.dmgCalcMsg += baseAtk+NL;
		        		calc.jobSkillUsed = true;
		        		
		        	}else {
		        		out.dmgCalcMsg += "조준 보너스 DMG "+baseAtk+"→";
		        		baseAtk = (int)Math.round(baseAtk * 2.25);
		        		out.dmgCalcMsg += baseAtk+NL;
						calc.jobSkillUsed = true;
		        	}
		        	flags.monPattern = 1;
		        	
	    			break;
	    		case 1:
	    			if (ThreadLocalRandom.current().nextDouble() < 0.20) {
	    				flags.monPattern = 1;
	    				out.dmgCalcMsg += "몬스터를 따돌려 숨었다.."+NL;
	    			}else {
	    				out.dmgCalcMsg += "다음 공격 준비 중.."+NL;
	    			}
	    			baseAtk=0;
	    			break;
    			default:
    				baseAtk=0;
    				crit=false;
	            	out.dmgCalcMsg += "저격 위치 확보 중.. ";
		        	baseAtk =0;
		        	flags.monPattern = 1;
	    			
    				
    				break;
	    	}
	    }
	    
	    if ("제너럴".equals(job)) {
	    	switch(beforeJobSkillYn) {
	    		case 0:
	    			baseAtk = (effAtkMin + effAtkMax + 1) /2;
		        	if (ThreadLocalRandom.current().nextDouble() < 0.15) {
		        		out.dmgCalcMsg += "[헤드샷] 보너스 DMG "+baseAtk+"→";
		        		baseAtk = (int)Math.round(baseAtk * 3.25);
		        		out.dmgCalcMsg += baseAtk+NL;
		        		
		        	}else {
		        		out.dmgCalcMsg += "조준 보너스 DMG "+baseAtk+"→";
		        		baseAtk = (int)Math.round(baseAtk * 1.85);
		        		out.dmgCalcMsg += baseAtk+NL;
		        	}
		        	calc.jobSkillUsed = true;
		        	flags.monPattern = 1;
		        	
	    			break;
	    		case 1:
	    			if (ThreadLocalRandom.current().nextDouble() < 0.15) {
		        		out.dmgCalcMsg += "[헤드샷] 보너스 DMG "+baseAtk+"→";
		        		baseAtk = (int)Math.round(baseAtk * 1.65);
		        		out.dmgCalcMsg += baseAtk+NL;
	    			}
	    			out.dmgCalcMsg += "회피기동타격..!"+NL;
	    			
	    			calc.jobSkillUsed = true;
	    			break;
    			default:
    				
    				if (ThreadLocalRandom.current().nextDouble() < 0.50) {
    					//회피기동타격 
    					if (ThreadLocalRandom.current().nextDouble() < 0.15) {
    		        		out.dmgCalcMsg += "[헤드샷] 보너스 DMG "+baseAtk+"→";
    		        		baseAtk = (int)Math.round(baseAtk * 1.65);
    		        		out.dmgCalcMsg += baseAtk+NL;
    	    			}
    	    			out.dmgCalcMsg += "회피기동타격..!"+NL;
    	    			
    	    			calc.jobSkillUsed = true;
    				}else {
    					//저격모드
    					if (ThreadLocalRandom.current().nextDouble() < 0.10) {
        					out.dmgCalcMsg += "폭격 지원 요청 중.. 몬스터의 무력화..!";
        		        	baseAtk =(int)Math.round(baseAtk * 3);
        		        	flags.monPattern = 1;
        				}else {
        					baseAtk=0;
            				crit=false;
        					out.dmgCalcMsg += "저격 위치 확보 중.. ";
        		        	baseAtk =0;
        		        	flags.monPattern = 1;
        				}
    				}
    				
    				
    				break;
	    	}
	    }
	    if ("검성".equals(job)) {
	    	if (ThreadLocalRandom.current().nextDouble() < 0.065) {
        		out.dmgCalcMsg += "바람가르기! "+baseAtk+"→";
        		baseAtk = (int)Math.round(baseAtk * 4);
        		out.dmgCalcMsg += baseAtk+NL;
        		out.dmgCalcMsg += "몬스터가 바람에 갇혀 행동불가가 됨!";
        		calc.jobSkillUsed = true;
            	flags.monPattern = 1;
			}
	    	
	    }
	    if ("어쎄신".equals(job)) {
	    	if (ThreadLocalRandom.current().nextDouble() < 0.065) {
        		out.dmgCalcMsg += "그림투스! "+baseAtk+"→";
        		baseAtk = (int)Math.round(baseAtk * 4);
        		out.dmgCalcMsg += baseAtk+NL;
        		out.dmgCalcMsg += "몬스터가 기습에 당해 행동불가가 됨!";
        		calc.jobSkillUsed = true;
            	flags.monPattern = 1;
			}
	    	
	    }
	    
	    if ("도박사".equals(job)) {

            int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1~100
            int multiplier = 1;

            if (roll <= 1)       multiplier = 100/2;
            else if (roll <= 3)  multiplier = 50/2;
            else if (roll <= 6)  multiplier = 33/2;
            else if (roll <= 10) multiplier = 25/2;
            else if (roll <= 15) multiplier = 20/2;
            else if (roll <= 21) multiplier = 16/2;
            else if (roll <= 28) multiplier = 14/2;
            else if (roll <= 36) multiplier = 12/2;
            else if (roll <= 45) multiplier = 11/2;
            else if (roll <= 55) multiplier = 10/2;
            else {
                // ❌ 실패
                //baseAtk = 0;
                crit = false;
                calc.jobSkillUsed = false;
                out.dmgCalcMsg = "도박 실패!(크리티컬해제)";
                multiplier=1;
            }

            // 🎯 성공
            int before = baseAtk;
            baseAtk = baseAtk * multiplier;

            if(roll<=10) {
            	calc.jobSkillUsed = true;
            }
            if(roll <=55 ) {
	            out.dmgCalcMsg =
	                "도박 성공! (피해량 ×" + multiplier + ") "
	                + before + " ⇒ " + baseAtk + "!";
            }
        }
	    
	    boolean isSnipe = false;
	    if ("궁수".equals(job)) {
	        if (ThreadLocalRandom.current().nextDouble() < 0.13) {
	            isSnipe = true;
	            baseAtk = baseAtk * 20;
	            calc.jobSkillUsed = true;
	            crit = false;
	        }
	    }

	    if ("프리스트".equals(job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 1.25);
	    }
	    if ("어둠사냥꾼".equals(job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 1.75);
	    }
	    if ("용사".equals(job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 1.25);
	    }
	    
	    if ("용기사".equals(job)) {
	        /*
	    	if (u.hpCur >= effHpMax) {
	        	out.dmgCalcMsg += "풀HP DMG "+baseAtk+"→";
	        	baseAtk = (int)Math.round(baseAtk * 1.5);
	        	out.dmgCalcMsg += baseAtk+NL;
	        }
	        */
	    	if(effCritRate > 500) {
	    		int bonus = (int)Math.round(effCritRate*21); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCritRate > 400) {
	    		int bonus = (int)Math.round(effCritRate*17); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCritRate > 300) {
	    		int bonus = (int)Math.round(effCritRate*13); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCritRate > 200) {
	    		int bonus = (int)Math.round(effCritRate*9); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCritRate > 100) {
	    		int bonus = (int)Math.round(effCritRate*5); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}else {
	    		int bonus = (int)Math.round(effCritRate*3); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	    	}
	    	
	    	
	    	if(effCriDmg > 1700) {
	    		int bonus = (int)Math.round(effCriDmg*21); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCriDmg > 1300) {
	    		int bonus = (int)Math.round(effCriDmg*17); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCriDmg > 1000) {
	    		int bonus = (int)Math.round(effCriDmg*13); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	    	}else if(effCriDmg > 700) {
	    		int bonus = (int)Math.round(effCriDmg*9); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	    	}else {
	    		int bonus = (int)Math.round(effCriDmg*5); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	    	}
	        
	        effCritRate = 0;
	        effCriDmg = 0;
	        crit = false;
	        if (m.monNo==13 || m.monNo==20 || m.monNo==29) {
	        	out.dmgCalcMsg += "용족 보너스 "+baseAtk+"→";
	        	baseAtk = (int)Math.round(baseAtk * 5);
	        	out.dmgCalcMsg += baseAtk;
	        }
	    }
	    /*
	    if("파이터".equals(job)) {
	    	baseAtk = (int) Math.round(berserkMul * baseAtk);
	    	effCritRate = -100;
	        effCriDmg = 0;
	        crit = false;
	    }
	    */
	    //모든직업 berserk 는 상위에서 계산하도록 
    	baseAtk = (int) Math.round(berserkMul * baseAtk);
	    
	   

	    
	    
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
	    	boolean beforeCalc = calc.jobSkillUsed;
	        calc = calcDamage(u, m, flags, baseAtk, crit, critMultiplier);
	        calc.jobSkillUsed = beforeCalc;
	        
	        flags.atkCrit = crit;
	        flags.snipe = isSnipe;
	        flags.finisher = (flags.monPattern >= 4); // 패턴4=필살기
	        
	        if ("저격수".equals(job) ) {
	        	
	        	switch(beforeJobSkillYn) {
		    		case 0:
		    			calc.patternMsg = m.monName + " (이)가 표적을 찾고 있습니다.";
		    			break;
		    		case 1:
		    			break;
	    			default:
		    			calc.patternMsg = m.monName + " (이)가 배회합니다";
	    				break;
	        	}
	        }
	        if ("제너럴".equals(job) ) {
	        	
	        	switch(beforeJobSkillYn) {
	        	case 0:
	        		calc.patternMsg = m.monName + " (이)가 표적을 찾고 있습니다.";
	        		break;
	        	case 1:
	        		if(!flags.finisher && calc.monDmg > 0) {
	        			int monLv = m.monNo;
	        			double evadeRate = 0.90;
	    	            switch (monLv) {
		    	            case 30:
		    	            	evadeRate -= 0.05;
		    	            case 29:
		    	            	evadeRate -= 0.05;
	    		            case 28:
	    		            	evadeRate -= 0.05;
	    		            case 27:
	    		            	evadeRate -= 0.05;
	    		            case 26:
	    		            	evadeRate -= 0.05;
	    		            case 25:
	    		            	evadeRate -= 0.05;
	    		            case 24:
	    		            	evadeRate -= 0.05;
	    		            case 23:
	    		            	evadeRate -= 0.05;
	    		            case 22:
	    		            	evadeRate -= 0.05;    
	    	            }

	    	            if (ThreadLocalRandom.current().nextDouble() < evadeRate) {
	    	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	    	                calc.patternMsg = baseMsg + "제너럴의 회피! 피해를 받지 않았습니다.";
	    	                calc.monDmg = 0;
	    	            }
	        		}else if(flags.finisher && calc.monDmg > 0) {
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
	        		
	        		break;
	        	default:
	        		if(!calc.jobSkillUsed) {
	        			if(baseAtk>0) {
	        				calc.patternMsg = m.monName + " (이)가 날벼락에 맞았습니다!";
	        			}else {
	        				calc.patternMsg = m.monName + " (이)가 배회합니다";
	        			}
	        		}
	        		break;
	        	}
	        }
	    	if ("파이터".equals(job) ) {
	    		if(u.hpCur < effHpMax*0.3) {
	    			if (ThreadLocalRandom.current().nextDouble() < 0.40) {
	    				flags.monPattern = 1;
		    			calc.monDmg = 0;  // 방어 패턴이었으니 몬스터 피해는 0 유지
		    			calc.patternMsg = m.monName + "의 패턴파훼! 몬스터가 모든행동을 멈춥니다";
		    			
			            calc.atkDmg = calc.baseAtk;
		    		}
	    		}
	        }
	        // 🔥 마법사: 패턴3 방어를 깨뜨리고 1.5배 피해
	        if ("마법사".equals(job) ) {
	        	if(flags.monPattern == 3) {
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
	        	}else if(flags.monPattern == 4) {
	        		int reduced = (int) Math.floor(calc.monDmg * 0.7);
		            if (reduced < 1) reduced = 1;
		            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		            calc.patternMsg = baseMsg + "(마나실드 필살피해 30% 감소 → " + reduced + ")";
		            calc.monDmg = reduced;
	        	}
	        }
	        
	        if ("처단자".equals(job) ) {
	        	if(flags.monPattern == 3) {
		        	// 패턴3 → 방어 대신 무행동 취급
		            flags.monPattern = 1;
	
		            // ✅ 방어 적용 전 기준( baseAtk * critMultiplier )으로 다시 계산
		            int originalDmg = (int) Math.round(calc.baseAtk * calc.critMultiplier);
	
		            int newDmg = (int) Math.round(originalDmg * 2.5);
		            calc.atkDmg = newDmg;
		            calc.monDmg = 0;  // 방어 패턴이었으니 몬스터 피해는 0 유지
	
		            // 디버그용 계수도 실제 데미지에 맞게 재계산
		            if (calc.baseAtk > 0) {
		                calc.critMultiplier = (double) newDmg / calc.baseAtk;
		            }
	
		            calc.patternMsg = "처단자의 방어파괴! (피해 2.5배)";
	        	}
	        }

	        // 🛡 전사: 보스 필살기 패링 (20% 확률)
	        if ("검성".equals(job)) {
	        	if (flags.finisher && calc.monDmg > 0) {
		            if (ThreadLocalRandom.current().nextDouble() < 0.15) {

		                int bossSkillDmg = calc.monDmg;             // 보스 필살기 데미지
		                int reflectTotal = calc.atkDmg + bossSkillDmg; // 되돌려줄 총 피해

		                calc.atkDmg += bossSkillDmg;  // 되받아친 만큼 공격에 누적
		                calc.monDmg = 0;              // 나는 피해 없음

		                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		                calc.patternMsg = baseMsg
		                        + "패링! 몬스터의 필살기를 되받아쳐 총 "
		                        + reflectTotal + " 피해를 입히고 피해를 받지 않았습니다.";

		                calc.jobSkillUsed = true;
		            }
		        }else if (!flags.finisher && calc.monDmg > 0) {
		        	if (ThreadLocalRandom.current().nextDouble() < 0.15) {
		        		int bossSkillDmg = calc.monDmg;             // 보스 필살기 데미지
		                int reflectTotal = calc.atkDmg + bossSkillDmg; // 되돌려줄 총 피해

		                calc.atkDmg += bossSkillDmg;  // 되받아친 만큼 공격에 누적
		                calc.monDmg = 0;              // 나는 피해 없음

		                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		                calc.patternMsg = baseMsg
		                        + "패링! 몬스터의 공격를 되받아쳐 총 "
		                        + reflectTotal + " 피해를 입히고 피해를 받지 않았습니다.";

		                calc.jobSkillUsed = true;
		        	}
		        }
	        }
	        
	        if("전사".equals(job)) {
	        	if (flags.finisher && calc.monDmg > 0) {
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
		        }else if (!flags.finisher && calc.monDmg > 0) {
		            int reduce = (int) Math.round(u.lv * 10)+m.monLv*10;
		            int after = Math.max(0, calc.monDmg - reduce); // 최소 0
		            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		            calc.patternMsg = baseMsg
		                    + "(전사의방패 효과로 " + reduce + " 피해 감소 → " + after + ")";
		            calc.monDmg = after;
		        }
	        }
	        
	        if ("어쎄신".equals(job) && calc.monDmg > 0 ) {
	        	double evadeRate = 1;
	        	if(flags.finisher) {
	        		evadeRate = 0.20;
	        	}
	        	
	        	if (ThreadLocalRandom.current().nextDouble() < evadeRate) {
	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	                calc.patternMsg = baseMsg + NL+"어쎄신의 날렵한 회피! 피해를 받지 않았습니다.";
	                calc.monDmg = 0;
	            }

	            
	        }
	        
	        
	        // 🌀 도적: 회피 (고레벨 보스일수록 회피율 감소, 필살기 제외)
	        if ("도적".equals(job) && calc.monDmg > 0 && !flags.finisher) {

	            int monLv = m.monNo;
	            double evadeRate = 0.80;
	            switch (monLv) {
		            case 30:
		            	evadeRate -= 0.05;
		            case 29:
		            	evadeRate -= 0.05;
		            case 28:
		            	evadeRate -= 0.05;
		            case 27:
		            	evadeRate -= 0.05;
		            case 26:
		            	evadeRate -= 0.05;
		            case 25:
		            	evadeRate -= 0.05;
		            case 24:
		            	evadeRate -= 0.05;
		            case 23:
		            	evadeRate -= 0.05;
		            case 22:
		            	evadeRate -= 0.05;  
	            }

	            if (ThreadLocalRandom.current().nextDouble() < evadeRate) {
	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	                calc.patternMsg = baseMsg + "도적의 회피! 피해를 받지 않았습니다.";
	                calc.monDmg = 0;
	            }
	        }
	        
	        if ("처단자".equals(job) && calc.monDmg > 0 && !flags.finisher) {

	            int monLv = m.monNo;
	            double evadeRate = 0.80;
	            switch (monLv) {
		            case 30:
		            	evadeRate -= 0.05;
		            case 29:
		            	evadeRate -= 0.05;
		            case 28:
		            	evadeRate -= 0.05;
		            case 27:
		            	evadeRate -= 0.05;
		            case 26:
		            	evadeRate -= 0.05;
		            case 25:
		            	evadeRate -= 0.05;
		            case 24:
		            	evadeRate -= 0.05;
		            case 23:
		            	evadeRate -= 0.05;
		            case 22:
		            	evadeRate -= 0.05;
	            }

	            if (ThreadLocalRandom.current().nextDouble() < evadeRate) {
	                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	                calc.patternMsg = baseMsg + "적의 공격이 처단자에게 닿지않습니다";
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
	        if ("어둠사냥꾼".equals(job) && calc.monDmg > 0 && !flags.finisher) {
	        	int reduced = (int) Math.floor(calc.monDmg * 0.7);
	        	if (reduced < 1) reduced = 1;
	        	String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	        	calc.patternMsg = baseMsg + "(받는 피해 30% 감소 → " + reduced + ")";
	        	calc.monDmg = reduced;
	        }
	        
	        if ("어둠사냥꾼".equals(job) && flags.finisher && flags.monPattern==6 ) {
	        	calc.atkDmg = rawAtkDmg*5;
			    calc.monDmg = 0;
			    calc.endBattle = false;
			    calc.patternMsg = "도망가는 적을 붙잡아 강력한 일격!" + rawAtkDmg*5 + " 피해";
	        }
	        
	        if ("복수자".equals(job)) {
		        if (calc.monDmg > 0 && flags.monPattern == 2 || flags.monPattern == 4) {
		            int revengeDmg = (int) Math.round(calc.monDmg * 1.5);
		            calc.atkDmg += revengeDmg;

		            calc.patternMsg += NL
		                + "어벤져의 분노! 받은 피해 "
		                + calc.monDmg
		                + " → 반격 데미지 +"
		                + revengeDmg;
		        }
		    }
	     // 몬스터 공격 변동 처리 (회피 / 증폭)
	        if ("도박사".equals(job)) {
		        if (calc.monDmg > 0 ) {
	
		            int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1~100
		            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	
		            if (roll <= 11) {
		                // 🌀 회피
		                calc.monDmg = 0;
		                calc.patternMsg = NL+baseMsg + "도박대성공! (회피판정 → "+0+")";
		            }
		            else if (roll <= 44) {
		            	int increased = calc.monDmg /2;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박성공! (받는 피해 50% → " + increased + ")";
		            }
		            else if (roll <= 88) {
		                // 💥 2배 피해
		                int increased = calc.monDmg * 2;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박실패! (받는 피해x2 → " + increased + ")";
		            }
		            else {
		                // ☠ 3배 피해
		                int increased = calc.monDmg * 3;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박대실패! (받는 피해x2 → " + increased + ")";
		            }
		        }
	        }

	        
	    }

	    if ("용사".equals(job)) {
	    	double rnd = ThreadLocalRandom.current().nextDouble();
            if (rnd < 0.10) {
            	int heal = (int) Math.round(effHpMax * 1);

	            int before = u.hpCur;
	            u.hpCur = Math.min(effHpMax, u.hpCur + heal);

	            String base = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = base + "정령의 가호 효과! " + 
	                    "fullHp 회복 (HP " + before + " → " + u.hpCur + "/" + effHpMax + ")";
	            calc.jobSkillUsed = true;
            }
	    }
	    // -----------------------------
	    // 5) 흡혈귀: 이번 턴 실제 입힌 피해의 20% 회복
	    // -----------------------------
	    if ("흡혈귀".equals(job) && calc.atkDmg > 0) {

	        if (m.monNo == 10 || m.monNo == 14 || m.monNo == 28) {
	            String base = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = base + "언데드는 흡혈 불가";
	        } else {
	            // 몬스터가 실제로 잃은 체력만큼만 흡혈 가능
	            int realDamage = Math.min(calc.atkDmg, monHpRemainBefore);
	            int heal = (int) Math.round(realDamage * 0.20);
	            if (heal < 1) heal = 1;
	            
	            int maxHeal = (int) Math.round(effHpMax * 0.20);
	            if (heal > maxHeal) heal = maxHeal;
	            

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
	

	private String buildJobDescriptionList() {
		StringBuilder sb = new StringBuilder();
		sb.append("♬ /직업 [직업명] 으로 전직 가능합니다.");
	    sb.append("♬♬ 전직 가능한 직업 목록").append(ALL_SEE_STR);
	    for (JobDef def : JOB_DEFS.values()) {
	    	sb.append(def.name).append(":");
	        sb.append(def.listLine).append(NL);
	        sb.append(def.attackLine).append(NL).append(NL);
	        
	    }
	    
	    return sb.toString();
	}

	
	private String normalizeJob(String raw) {
		 if (raw == null) return null;
		    String s = raw.trim();

		    JobDef def = JOB_DEFS.get(s);
		    return (def != null ? def.name : null);
	}


	private static class JobChangeReq {
	    final String baseJob;   // 어떤 직업으로
	    final int minCount;     // 몇 회 이상 공격해야 하는지

	    JobChangeReq(String baseJob, int minCount) {
	        this.baseJob = baseJob;
	        this.minCount = minCount;
	    }
	}
	// 직업 공통 정의
	private static final class JobDef {
	    final String name;       
	    final String listLine;   
	    final String attackLine; 

	    JobDef(String name, String listLine, String attackLine) {
	        this.name = name;
	        this.listLine = listLine;
	        this.attackLine = attackLine;
	    }
	}
	
	private int pickBiasedSp(int min, int max) {
	    double r = ThreadLocalRandom.current().nextDouble(); // 0~1
	    double biased = Math.pow(r, 8); // 극단적으로 0쪽으로 치우침

	    int span = max - min;
	    return min + (int)Math.round(span * biased);
	}

	private String buildUnifiedDosaBuffMessage(DosaBuffEffect self, DosaBuffEffect room) {

	    double min= 0, max = 0, crit = 0, cdmg = 0, hp = 0;

	    if (self != null) {
	    	min  += self.addAtkMin; 
	    	max  += self.addAtkMax;
	        crit += self.addCritRate;
	        cdmg += self.addCritDmg;
	        hp   += self.addHp;
	    }

	    if (room != null) {
	    	min  += room.addAtkMin; 
	    	max  += room.addAtkMax;
	        crit += room.addCritRate;
	        cdmg += room.addCritDmg;
	        hp   += room.addHp;
	    }

	    StringBuilder sb = new StringBuilder("※버프 효과: ");

	    List<String> parts = new ArrayList<>();

	    if (min != 0)  parts.add("MIN "  + (min >= 0 ? "+" : "") + (int)min);
	    if (max != 0)  parts.add("MAX "  + (max >= 0 ? "+" : "") + (int)max);
	    if (crit != 0) parts.add("CRIT " + (crit>= 0 ? "+" : "") + (int)crit + "%");
	    if (cdmg != 0) parts.add("CDMG " + (cdmg>= 0 ? "+" : "") + (int)cdmg + "%");
	    if (hp   != 0) parts.add("HP "   + (hp  >= 0 ? "+" : "") + (int)hp);

	    sb.append(String.join(", ", parts));

	    return sb.toString();
	}
	
	// ===== 장비 카테고리별 최대 소지 수량 =====
	private int getEquipCategoryMax(int itemId) {
	    // 무기 (100번대): 최대 5개
	    if (itemId >= 100 && itemId < 200) return 5;
	    // 투구 (200번대): 1개
	    if (itemId >= 200 && itemId < 300) return 1;
	    // 갑옷 (400번대): 1개
	    if (itemId >= 400 && itemId < 500) return 1;
	    // 전설 (700번대): 1개
	    if (itemId >= 700 && itemId < 800) return 1;
	    // 날개 (800번대): 1개
	    if (itemId >= 800 && itemId < 900) return 1;

	    // 나머지는 제한 없음
	    return Integer.MAX_VALUE;
	}

	private int getMaxAllowedByCategoryLabel(String label) {
	    if (label.contains("무기"))  return 5;    // 100번대
	    if (label.contains("투구"))  return 1;    // 200번대
	    if (label.contains("갑옷"))  return 1;    // 400번대
	    if (label.contains("날개"))  return 1;    // 800번대
	    if (label.contains("전설"))  return 1;    // 700번대

	    // 나머지(행운/반지/토템/선물/유물 등)
	    return Integer.MAX_VALUE;
	}
	
	/**
	 * 같은 "장비 카테고리"인지 판별
	 *  - 여기서 말하는 카테고리는 위 제한이 걸리는 4개(무기/투구/갑옷/전설)
	 */
	private boolean isSameEquipCategory(int baseItemId, int otherItemId) {
	    // 무기
	    if (baseItemId >= 100 && baseItemId < 200) {
	        return (otherItemId >= 100 && otherItemId < 200);
	    }
	    // 투구
	    if (baseItemId >= 200 && baseItemId < 300) {
	        return (otherItemId >= 200 && otherItemId < 300);
	    }
	    // 갑옷
	    if (baseItemId >= 400 && baseItemId < 500) {
	        return (otherItemId >= 400 && otherItemId < 500);
	    }
	    // 날개
	    if (baseItemId >= 800 && baseItemId < 900) {
	    	return (otherItemId >= 800 && otherItemId < 900);
	    }
	    // 전설
	    if (baseItemId >= 700 && baseItemId < 800) {
	        return (otherItemId >= 700 && otherItemId < 800);
	    }
	    return false;
	}

	private int getCurrentEquipCategoryHolding(String userName, String roomName, int baseItemId) {

		List<HashMap<String, Object>> inv = botNewService.selectInventorySummaryAll(userName, roomName);

		if (inv == null || inv.isEmpty()) {
			return 0;
		}

		int count = 0;
		for (HashMap<String, Object> row : inv) {
			if (row == null)
				continue;

			Object oItemId = row.get("ITEM_ID");
			if (!(oItemId instanceof Number))
				continue;

			int itemId = ((Number) oItemId).intValue();

// baseItemId 와 같은 장비 카테고리인지 체크
			if (!isSameEquipCategory(baseItemId, itemId))
				continue;

// 장비인지 한 번 더 필터 (ITEM_TYPE 이 MARKET 인 것만)
			String itemType = Objects.toString(row.get("ITEM_TYPE"), "");
			if (!"MARKET".equalsIgnoreCase(itemType))
				continue;

// TOTAL_QTY 가 0 이면 사실상 미보유로 간주
			Object oQty = row.get("TOTAL_QTY");
			int qty = (oQty instanceof Number) ? ((Number) oQty).intValue() : 0;
			if (qty <= 0)
				continue;

// ✅ 장비 제한은 "행 개수" 기준으로 +1
			count++;
		}
		return count;
	}
	
	/**
	 * 장비아이템 카테고리 수량 제한 체크
	 *
	 * @return null 이면 OK, 문자열이면 에러 메시지
	 */
	private String checkEquipCategoryLimit(String userName,
	                                       String roomName,
	                                       int itemId,
	                                       int gainQty) {

	    if (gainQty <= 0) {
	        return null; // 실제로 얻는 수량이 없으면 체크 안 함
	    }

	    int maxAllowed = getEquipCategoryMax(itemId);
	    if (maxAllowed == Integer.MAX_VALUE) {
	        // 제한 없는 카테고리 (행운/반지/토템/선물/유물 등)
	        return null;
	    }

	    // 현재 인벤토리 기준 해당 카테고리 총합
	    int current = getCurrentEquipCategoryHolding(userName, roomName, itemId);

	    if (current + gainQty > maxAllowed) {
	        // 메시지는 네 스타일에 맞게
	        return "❌ 장비 카테고리 수량 제한으로 인해 행동이 불가능합니다."
	             + NL
	             + "현재 카테고리 보유 수량: " + current
	             + "개 / 최대 " + maxAllowed + "개, 판매 후 구매해주세요. ";
	    }

	    return null;
	}
	
	private String resolveItemCategory(int itemId) {
	    if (itemId >= 100 && itemId < 200)  return "※무기";   // 100번대
	    if (itemId >= 200 && itemId < 300)  return "※투구";   // 200번대
	    if (itemId >= 300 && itemId < 400)  return "※행운";   // 300번대
	    if (itemId >= 400 && itemId < 500)  return "※갑옷";   // 400번대
	    if (itemId >= 500 && itemId < 600)  return "※반지";   // 500번대
	    if (itemId >= 600 && itemId < 700)  return "※토템";   // 600번대
	    if (itemId >= 700 && itemId < 800)  return "※전설";   // 700번대
	    if (itemId >= 800 && itemId < 900)  return "※날개";   // 800번대
	    if (itemId >= 900 && itemId < 1000) return "※선물";   // 900번대
	    if (itemId >= 8000 && itemId < 9000) return "※업적"; // 9000번대 
	    if (itemId >= 9000 && itemId < 10000) return "※유물"; // 9000번대 
	    return "※기타";
	}
	// 카테고리명 또는 숫자로 범위를 구하는 함수
	private int[] resolveCategoryRange(String raw) {
	    if (raw == null) return null;
	    String s = raw.trim();

	    if (s.isEmpty()) return null;

	    // 1) 문자 카테고리 먼저 처리
	    switch (s) {
	        case "무기": return new int[]{100, 200};
	        case "투구": return new int[]{200, 300};
	        case "행운": return new int[]{300, 400};
	        case "갑옷": return new int[]{400, 500};
	        case "반지": return new int[]{500, 600};
	        case "토템": return new int[]{600, 700};
	        case "전설": return new int[]{700, 800};
	        case "날개": return new int[]{800, 900};
	        case "선물": return new int[]{900, 1000};
	        //case "유물": return new int[]{9000, 10000};
	    }

	    // 2) 숫자인 경우: "100", "200", "9000" 같이 "00"으로 끝나는 것만 카테고리로 취급
	    if (s.matches("\\d+")) {
	        // 끝이 "00"이 아니면 카테고리 아님 → 단일 구매로 내려가게 null 리턴
	        if (!s.endsWith("00")) {
	            return null;
	        }

	        int num;
	        try {
	            num = Integer.parseInt(s);
	        } catch (NumberFormatException e) {
	            return null;
	        }

	        // 100 → [100,200), 200 → [200,300), 9000 → [9000,9100) (원하면 여기 커스텀 가능)
	        return new int[]{num, num + 100};
	    }

	    return null;
	}
	
	
	private String buildEnhancedOptionLine(HashMap<String,Object> item, int qty) {
	    if (item == null) return "";

	    int baseMin     = parseIntSafe(Objects.toString(item.get("ATK_MIN"), "0"));
	    int baseMax     = parseIntSafe(Objects.toString(item.get("ATK_MAX"), "0"));
	    int baseHp      = parseIntSafe(Objects.toString(item.get("HP_MAX"), "0"));
	    int baseRegen   = parseIntSafe(Objects.toString(item.get("HP_REGEN"), "0"));
	    int baseCri     = parseIntSafe(Objects.toString(item.get("ATK_CRI"), "0"));    // 치확
	    int baseCriDmg  = parseIntSafe(Objects.toString(item.get("CRI_DMG"), "0"));    // 치피
	    int baseHpRate  = parseIntSafe(Objects.toString(item.get("HP_MAX_RATE"), "0"));// 체력%
	    int baseAtkRate = parseIntSafe(Objects.toString(item.get("ATK_MAX_RATE"), "0"));// 최종공격력%

	    StringBuilder sb = new StringBuilder();

	    // 공격력
	    if (baseMin != 0 || baseMax != 0) {
	        sb.append("[공격력 ")
	          .append(baseMin)
	          .append("~")
	          .append(baseMax)
	          .append("] ");
	    }

	    // 최종 공격력 %
	    if (baseAtkRate != 0) {
	        sb.append("[최종공격력 ")
	          .append(baseAtkRate)
	          .append("%] ");
	    }

	    // HP
	    if (baseHp != 0) {
	        sb.append("[체력+ ")
	          .append(baseHp)
	          .append("] ");
	    }

	    // HP %
	    if (baseHpRate != 0) {
	        sb.append("[체력% ")
	          .append(baseHpRate)
	          .append("] ");
	    }

	    // 체젠
	    if (baseRegen != 0) {
	        sb.append("[체젠 ")
	          .append(baseRegen)
	          .append("] ");
	    }

	    // 치확
	    if (baseCri != 0) {
	        sb.append("[치확 ")
	          .append(baseCri)
	          .append("] ");
	    }

	    // 치피
	    if (baseCriDmg != 0) {
	        sb.append("[치피 ")
	          .append(baseCriDmg)
	          .append("] ");
	    }

	    return sb.toString().trim();
	}


	private Date truncateToDate(Date d) {
	    Calendar c = Calendar.getInstance();
	    c.setTime(d);
	    c.set(Calendar.HOUR_OF_DAY, 0);
	    c.set(Calendar.MINUTE, 0);
	    c.set(Calendar.SECOND, 0);
	    c.set(Calendar.MILLISECOND, 0);
	    return c.getTime();
	}
	
	private java.sql.Timestamp toTimestamp(Object obj) {
	    if (obj == null) return null;

	    if (obj instanceof java.sql.Timestamp) {
	        return (java.sql.Timestamp) obj;
	    }
	    if (obj instanceof java.util.Date) {
	        return new java.sql.Timestamp(((java.util.Date) obj).getTime());
	    }
	    if (obj instanceof String) {
	        String s = ((String) obj).trim();
	        if (s.isEmpty()) return null;

	        // 1) yyyy-MM-dd HH:mm:ss 형태 시도
	        try {
	            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	            java.util.Date d = fmt.parse(s);
	            return new java.sql.Timestamp(d.getTime());
	        } catch (Exception ignore) {}

	        // 2) yyyyMMddHHmmss 형태 시도
	        try {
	            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
	            java.util.Date d = fmt.parse(s);
	            return new java.sql.Timestamp(d.getTime());
	        } catch (Exception ignore) {}

	        // 3) 위 포맷이 아니면, 그냥 null 취급
	        return null;
	    }

	    // 예상 밖 타입이면 null
	    return null;
	}

	private String buildRelicSummaryLine(List<HashMap<String, Object>> bag,int number) {
		int sumAtkMin = 0;
		int sumAtkMax = 0;
		int sumHp = 0;
		int sumRegen = 0;
		int sumCrit = 0;
		int sumCritDmg = 0;
		int sumAtkRate = 0;
		int sumHpRate = 0;
		int relicCount = 0;
		try {
			if (bag == null)
				return null;

			for (HashMap<String, Object> row : bag) {
				int itemId = safeInt(row.get("ITEM_ID"));
				if (itemId < number || itemId >= number+1000)
					continue;

				relicCount++;

				sumAtkMin += safeInt(row.get("ATK_MIN"));
				sumAtkMax += safeInt(row.get("ATK_MAX"));
				sumHp += safeInt(row.get("HP_MAX"));
				sumRegen += safeInt(row.get("HP_REGEN"));
				sumCrit += safeInt(row.get("ATK_CRI"));
				sumCritDmg += safeInt(row.get("CRI_DMG"));
				sumAtkRate += safeInt(row.get("ATK_MAX_RATE"));
				sumHpRate += safeInt(row.get("HP_MAX_RATE"));
			}

		} catch (Exception e) {
			return null;
		}

		if (relicCount == 0)
			return null;

		StringBuilder sb = new StringBuilder();
		if(number==8000) {
			sb.append("✨ 업적 효과 (").append(relicCount).append("개): ");
		}else if(number==9000) {
			sb.append("✨ 유물 효과 (").append(relicCount).append("개): ");
		}

		boolean first = true;

		if (sumAtkMin != 0 || sumAtkMax != 0) {
			sb.append("ATK ").append(sumAtkMin).append("~").append(sumAtkMax);
			first = false;
		}
		if (sumAtkRate > 0) {
			if (!first)
				sb.append(", ");
			sb.append("최종ATK +").append(sumAtkRate).append("%");
			first = false;
		}
		if (sumHp > 0 || sumHpRate > 0) {
			if (!first)
				sb.append(", ");
			sb.append("HP +").append(sumHp);
			if (sumHpRate > 0)
				sb.append(" (+").append(sumHpRate).append("%)");
			first = false;
		}
		if (sumRegen > 0) {
			if (!first)
				sb.append(", ");
			sb.append("체젠 +").append(sumRegen);
			first = false;
		}
		if (sumCrit > 0 || sumCritDmg > 0) {
			if (!first)
				sb.append(", ");
			sb.append("치확 +").append(sumCrit).append("% / 치뎀 +").append(sumCritDmg).append("%");
		}
		


		return sb.toString();
	}
	
	public static String formatSpShort(long sp) {
	    if (sp < 1_000) {
	        return sp + "sp";
	    } else if (sp < 1_000_000) {
	        return trimDecimal(sp / 1_000.0) + "k sp";
	    } else if (sp < 1_000_000_000) {
	        return trimDecimal(sp / 1_000_000.0) + "m sp";
	    } else {
	        return trimDecimal(sp / 1_000_000_000.0) + "b sp";
	    }
	}

	private static String trimDecimal(double v) {
	    if (v == (long) v) {
	        return String.valueOf((long) v);
	    }
	    return String.format("%.2f", v).replaceAll("\\.?0+$", "");
	}
	
	private static String formatDateYMD(Date d) {
	    if (d == null) return "-";
	    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(d);
	}

	private static String formatDateMD(Date d) {
	    if (d == null) return "-";
	    return new java.text.SimpleDateFormat("MM월dd일").format(d);
	}
	
	
	// 직업 메타데이터 맵 (등록 순서 유지 위해 LinkedHashMap)
	private static final Map<String, JobDef> JOB_DEFS = new LinkedHashMap<>();

	static {
	    // NL은 클래스에 이미 있는 상수라고 가정하고 그대로 사용
	    JOB_DEFS.put("전사", new JobDef(
	        "전사",
	        "▶ 육체능력이 변경되며, 패링 스킬 추가 ",
	        "⚔ 몬스터레벨에 따라 방어도 추가, 적의 필살기를 반격(20%),모든 적에게 데미지 추가(+40%)"
	    ));

	    /*
	    JOB_DEFS.put("궁수", new JobDef(
	        "궁수",
	        "▶ 사냥감을 조준하는 집요한 추적자, 강력한 한방을 선사한다",
	        "⚔ 최종 데미지 ×1.6, EXP +25%, 공격시 13%확률로 강력한공격(dmg*20)"
	    ));
	     */
	    JOB_DEFS.put("마법사", new JobDef(
	        "마법사",
	        "▶ 강력한 마법공격으로 몬스터의 방어태세를 무력화한다",
	        "⚔ 몬스터가 방어시 방어를 무시하고 피해 2배를 줌, 보스의 필살기를 마나실드로 방어(30%데미지감소)"
	    ));

	    JOB_DEFS.put("도적", new JobDef(
	        "도적",
	        "▶ 날렵한 손놀림으로 적의공격을 피하며,아이템을 강탈한다",
	        "⚔ 공격 시 40% 확률 추가 드랍(STEAL), 몬스터 기본 공격 80% 회피, [스틸,회피 no22부터 5%씩 감소] "
	    ));
	    
	    JOB_DEFS.put("프리스트", new JobDef(
    		"프리스트",
    		"▶ 대사제의 축복을 받아 신성의힘으로 적을 물리친다",
    		"⚔ 아이템 HP/리젠 효과 1.25배, 몬스터에게 받는 일반공격 피해 감소(20%), 언데드추가피해(+25%)"
		));
	    
	    JOB_DEFS.put("도사", new JobDef(
	        "도사",
	        "▶ 도를 닦아 깨달음을 얻은 위인",
	        "⚔ 다음 공격하는 아군 강화(레벨*0.5만큼 능력강화,맥뎀*0.1만큼 치명뎀강화,"+NL+"매턴 공격시 자신 회복,자신의 럭키몬스터 등장 확률 증가"
	    ));
	    
        JOB_DEFS.put("사신", new JobDef(
            "사신",
            "▶ 이름하야 죽음의 신, 죽지않는다",
            "⚔ 드랍율-30%, 체력 0에서도 죽지 않음, 다크 몬스터 조우 불가"
        ));
        
        JOB_DEFS.put("흡혈귀", new JobDef(
            "흡혈귀",
            "▶ 배가고프다, 나는 배가 고프다!",
            "⚔ 공격시 준피해의 20% 흡혈(공격&흡혈 선계산, 후피해)[max: 최대체력의20%], hp리젠 아이템의 증감처리 미적용"
        ));
        
        JOB_DEFS.put("용기사", new JobDef(
    		"용기사",
    		"▶ 용족의 마지막 후예, 배신당한 아픔을 가지고 있다",
    		"⚔ 아이템 HP/리젠 효과 2배, 100% 초과 치명타확률, 기본 치명타 데미지 초과분을 공격력으로 전환,치명타가 발생하지않음, 용족에 5배의 피해"
        ));
        
        /*
        JOB_DEFS.put("파이터", new JobDef(
    		"파이터",
    		"▶ 강인한 체력의 소유자, 체력이 낮아지면 적의 행동을 저지시킨다",
    		"⚔ 공격력 최대치, 치명타 배율 및 치명타데미지 증가가 체력으로 전환(3배수,치명 미발생)"+NL+"본인의 체력이 낮아질수록 데미지 증가(추가 50%까지), 체력이 30%이하 일 때 적 행동저지(40%)"
        ));
        */
        /*
        JOB_DEFS.put("궁사2", new JobDef(
    		"궁사2",
    		"▶ 연속공격의 달인, 최대데미지와 최소공격력 차이가 클수록 연속공격한다(테스트모드)",
    		"⚔ 최대-최소 데미지 차이 280 마다 1연사 추가공격(추가공격데미지 고정)"
		));
        */
        JOB_DEFS.put("저격수", new JobDef(
    		"저격수",
    		"▶ 숨어서 급소를 노리는 암살자, 극강의 공격력을 선사한다",
    		"⚔ 공격력이 항상 중간값으로 고정, 최대체력-50%"+NL+
    		  "*조우 은엄폐 이후, *저격 - *이동 패턴을 반복"+NL+
    		  "*조우 은엄폐, *저격(13% headShot) 시 모든 행동 무시, *이동 시 20%확률 모든 행동 무시"
        ));
        
        JOB_DEFS.put("궁사", new JobDef(
    		"궁사",
    		"▶ 연속공격의 달인, 최대데미지와 최소공격력 차이가 클수록 연속공격한다",
    		"⚔ 최대-최소 데미지 차이 최대데미지의10%마다(최소280) 1연사 추가공격(각 구간 별 공격은 개별치명타율 최대75%)"+NL
 	         +"◎선행조건 : 공격횟수 3000회 "
        ));

        
        JOB_DEFS.put("용사", new JobDef(
	        "용사",
	        "▶ 선택 받은 자",//어둠몹에 피해두배 ,언데드추뎀25% ,스틸30%, 10%확률 완전회복
	        "⚔ 기본 HP*2 만큼 추가 증가, 어둠몬스터에 추가피해(+50%), 언데드 추가피해(+25%), 공격시 steal(30%), 정령의가호(10%), 기본데미지 * 1.4"+NL
	        +"◎선행조건 전사,도적,도사,프리스트 직업으로 각 300회 공격"
	    ));
	     
	    
	    JOB_DEFS.put("처단자", new JobDef(
	        "처단자",
	        "▶ 신을 모독하는 자는 그의 손에서 살아남을수 없다, 물론 모독을 안했어도 말이지..! ",
	        "⚔ 방어를 무시하고 피해 2.5배를 줌, 몬스터의 기본공격 80%회피 [회피 no22부터 5%씩 감소] , 처치시 추가드랍(30%), 빛몬스터에 추가피해(+50%), 기본데미지 *1.4 "+NL
	        +"◎선행조건 마법사,도적 직업으로 각 300회 공격"
	    ));
	    JOB_DEFS.put("제너럴", new JobDef(
	        "제너럴",
	        "▶ 블랙필드에서는 누구도 따라잡을자가 없다!",
	        "⚔ 조우시 (*은엄폐-저격 or *회피기동전술) 이후 *회피기동전술을 다회 반복"+NL
	        +"*조우 은엄폐(공격x or 폭격[hidden]), *저격(13% headShot) 시 모든 행동 무시, *회피기동전술 시 - hidden -,기본공격력 * 1.2"+NL
	        +"◎선행조건 저격수,전사 직업으로 각 300회 공격"
	    ));
	    
	    JOB_DEFS.put("검성", new JobDef(
	        "검성",
	        "▶ 검으로 세상 끝에 닿았다",
	        "⚔ 기본 HP*2만큼 추가 증가, 적의 공격 반격(15%),기본데미지*2.5"+NL
	        +"◎선행조건 전사 직업으로 1000회 공격"
	    ));
	    JOB_DEFS.put("어쎄신", new JobDef(
    		"어쎄신",
    		"▶ 그의 암습은 누구도 피할수없다.상대가 누구일 지라도",
    		"⚔ 공격 시 STEAL(30%,100킬 당 5%씩 증가,max 80%), 몬스터 기본 공격 회피, 필살기를 확률 회피, 기본데미지*1.3"+NL
    		+"◎선행조건 도적 직업으로 1000회 공격"
		));
	    
	    JOB_DEFS.put("어둠사냥꾼", new JobDef(
    		"어둠사냥꾼",
    		"▶ ???",
    		"⚔ 아이템 HP/리젠 효과 1.25배, 몬스터에게 받는 일반공격 피해 감소(30%), 언데드추가피해(+75%), -???- "+NL
    		+"◎선행조건 프리스트, 용기사 직업으로 각 300회 공격"
		));
	    JOB_DEFS.put("복수자", new JobDef(
    		"복수자",
    		"▶ ",
    		"⚔ 기본공격 배율 1.8, 몬스터의 일반공격/필살 시 받은피해를 돌려줌  "+NL
    		+"◎선행조건 전사, 제너럴 직업으로 각 300회 공격"
		));
	    
	    JOB_DEFS.put("도박사", new JobDef(
    		"도박사",
    		"▶ ???",
    		"⚔ -???- "+NL
    		+"◎선행조건 어둠사냥꾼, 복수자 직업으로 각 100회 공격"
		));
	    
	    JOB_DEFS.put("음양사", new JobDef(
    		"음양사",
    		"▶ ???",
    		"⚔ -???- "+NL
    		+"◎선행조건 도사 직업으로 1000회 공격"
		));
	    /*
	    JOB_DEFS.put("용투사", new JobDef(
			"용투사",
			"▶ 용족의 마지막 후예, 격투술로 상대를 제압한다",
			"⚔ 용 "+NL
			+"◎선행조건 마법사,도적 직업으로 각 300회 공격"
		));
        */
	}
	
	// 목표직업 -> 요구조건 리스트
	private static final Map<String, List<JobChangeReq>> JOB_CHANGE_REQS = new HashMap<>();
	// 목표직업 -> 전체 공격 횟수 요구
	private static final Map<String, Integer> JOB_CHANGE_TOTAL_REQS = new HashMap<>();
	
	static {
	    // 용사 = 전사 300회 + 도적 300회 공격해야 전직 가능
	    JOB_CHANGE_REQS.put("용사", Arrays.asList(
	        new JobChangeReq("전사", 300),
	        new JobChangeReq("도적", 300),
	        new JobChangeReq("도사", 300),
	        new JobChangeReq("프리스트", 300)
	    ));
	    JOB_CHANGE_REQS.put("처단자", Arrays.asList(
    		new JobChangeReq("마법사", 300),
    		new JobChangeReq("도적", 300)
		));
	    JOB_CHANGE_REQS.put("제너럴", Arrays.asList(
    		new JobChangeReq("저격수", 300),
    		new JobChangeReq("전사", 300)
		));
	    JOB_CHANGE_REQS.put("검성", Arrays.asList(
    		new JobChangeReq("전사", 1000)
		));
	    JOB_CHANGE_REQS.put("어쎄신", Arrays.asList(
	    	new JobChangeReq("도적", 1000)
		));
	    JOB_CHANGE_REQS.put("어둠사냥꾼", Arrays.asList(
	    	new JobChangeReq("프리스트", 300),
	    	new JobChangeReq("용기사", 300)
		));
	    JOB_CHANGE_REQS.put("복수자", Arrays.asList(
			new JobChangeReq("전사", 300),
			new JobChangeReq("제너럴", 300)
		));
	    JOB_CHANGE_REQS.put("도박사", Arrays.asList(
    		new JobChangeReq("어둠사냥꾼", 100),
    		new JobChangeReq("복수자", 100)
		));
	    JOB_CHANGE_REQS.put("음양사", Arrays.asList(
	    	new JobChangeReq("도사", 1000)
		));
	    
	    
	    
	    // 용사 = 전체 공격 1000회 이상
	    JOB_CHANGE_TOTAL_REQS.put("궁사", 3000);
	    
	}
}





