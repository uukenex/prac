package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

	public String bagLog(HashMap<String, Object> map) {
	    List<BagLog> logs = botNewService.selectRecentBagDrops();

	    if (logs == null || logs.isEmpty()) {
	        return "";
	    }

	    StringBuilder sb = new StringBuilder();
	    sb.append("최근 가방 획득 로그 (최대 5건)").append(NL);

	    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm");

	    for (BagLog log : logs) {
	        String when = (log.getInsertDate() != null ? fmt.format(log.getInsertDate()) : "-");
	        sb.append("- ")
	          .append(when)
	          .append(" : ")
	          .append(log.getUserName())
	          .append("님이 가방을 획득했습니다.")
	          .append(NL);
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
	    User u = botNewService.selectUser(targetUser, roomName);
	    if (u == null) {
	        ctx.success = false;
	        ctx.errorMessage = "❌ 유저 정보를 찾을 수 없습니다.";
	        return ctx;
	    }

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

	    // 🔹 직업 보너스 표시용 변수
	    int jobHpMaxBonus = 0;
	    int jobRegenBonus = 0;
	    
	    // 사신: 아이템으로 인한 크리/크리뎀 효과 미적용
	    if ("사신".equals(job)) {
	        bCriRaw    = 0;
	        bCriDmgRaw = 0;
	        // (주석상 HP까지 막고 싶으면 bHpMaxRaw = 0; 도 여기서 처리)
	    }

	    // 프리스트: 아이템 HP/리젠 1.25배 (monsterAttack 기준으로 맞춤)
	    if ("프리스트".equals(job)) {
	    	int hpBase   = bHpMaxRaw;
	        int regenBase= bRegenRaw;

	        bHpMaxRaw  = (int) Math.round(bHpMaxRaw * 1.25);
	        bRegenRaw  = (int) Math.round(bRegenRaw * 1.25);

	        jobHpMaxBonus = bHpMaxRaw  - hpBase;
	        jobRegenBonus = bRegenRaw  - regenBase;
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

	    // ② 무기강/보너스 조회
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

	    ctx.weaponLv     = weaponLv;
	    ctx.weaponBonus  = weaponBonus;

	    int atkMinWithItem = baseMin + bAtkMinRaw;
	    int atkMaxWithItem = baseMax + weaponBonus + bAtkMaxRaw;

	    ctx.atkMinWithItem = atkMinWithItem;
	    ctx.atkMaxWithItem = atkMaxWithItem;

	    // 3) 운영자의 축복
	    boolean hasBless = (u.lv <= 15);
	    int blessRegenBonus = hasBless ? 5 : 0;
	    ctx.hasBless          = hasBless;
	    ctx.blessRegenBonus   = blessRegenBonus;

	    // 🩸 흡혈귀: monsterAttack 캐논 기준으로 "아이템 리젠만" 무효
	    if ("흡혈귀".equals(job)) {
	        bRegenRaw = 0;
	    }

	    // 4) 최종 HP
	    int finalHpMax = baseHpMax + bHpMaxRaw;
	    if ("전사".equals(job)) {
	        finalHpMax += baseHpMax; // 기본 HP 추가
	    }
	    if (finalHpMax <= 0) finalHpMax = 1;

	    // 5) 최종 리젠 (기본+아이템+축복)
	    int effRegen = baseRegen + bRegenRaw + blessRegenBonus;
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
	    

	    
	    ctx.finalHpMax  = finalHpMax;
	    ctx.effRegen    = effRegen;

	    // HP/ATK 확정치 저장
	    ctx.atkMinWithItem = atkMinWithItem;
	    ctx.atkMaxWithItem = atkMaxWithItem;
	    
	    // 표시용 스탯 (1번 메서드에서 쓰던 값)
	    ctx.shownCrit     = baseCrit + bCriRaw;
	    ctx.shownRegen    = effRegen;                // 축복 포함 리젠을 그대로 표시하고 싶으면 이렇게
	    ctx.shownCritDmg  = baseCritDmg + bCriDmgRaw;

	    // 🔹 직업 보너스(표시용) 저장
	    ctx.jobHpMaxBonus = jobHpMaxBonus;
	    ctx.jobRegenBonus = jobRegenBonus;
	    
	    ctx.success = true;
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
	    try {
	        OngoingBattle ob = botNewService.selectOngoingBattle(targetUser, roomName);
	        if (ob != null) {
	            Monster m = botNewService.selectMonsterByNo(ob.monNo);
	            if (m != null) {
	                int monMaxHp    = m.monHp;
	                int monHpRemain = Math.max(0, m.monHp - ob.totalDealtDmg);

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

	    // 2) 가방 1개 소비
	    int updated = botNewService.consumeOneBagTx(userName, roomName);
	    if (updated <= 0) {
	        return "가방을 사용하는 중 오류가 발생했습니다. 다시 시도해주세요.";
	    }

	    // 3) 보상 결정 (컨트롤러에서 확률/로직 모두 처리)
	    double roll = ThreadLocalRandom.current().nextDouble();

	    if (roll < 0.40) { //40퍼확률로 골드 
	    	// 🔥 작은 쪽이 더 잘 나오는 SP 보상 (200 ~ 50000)
	        int sp = pickBiasedSp(200, 100000);

	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score", sp);
	        pr.put("cmd", "BAG_OPEN_SP");

	        botNewService.insertPointRank(pr);

	        return "가방을 열어보니 반짝이는 포인트가 나옵니다! +" + sp + "sp";
	    } else {
	    	
	    	// 아이템 보상

	        // 1순위: 가지고 있지 않은 보상 아이템
	        List<Integer> rewardItemIds = botNewService
	                .selectBagRewardItemIdsUserNotOwned(userName, roomName);

	        // 하나도 없으면: 전체 보상 풀에서 뽑거나, SP로 대체
	        if (rewardItemIds == null || rewardItemIds.isEmpty()) {
	            // 전체 보상 아이템 목록
	            rewardItemIds = botNewService.selectBagRewardItemIds();
	        }

	        // 그래도 없으면 최종적으로 SP 보상
	        if (rewardItemIds == null || rewardItemIds.isEmpty()) {
	            int sp = pickBiasedSp(200, 100000);

	            HashMap<String,Object> pr = new HashMap<>();
	            pr.put("userName", userName);
	            pr.put("roomName", roomName);
	            pr.put("score", sp);
	            pr.put("cmd", "BAG_OPEN_SP");

	            botNewService.insertPointRank(pr);

	            return "가방을 열어보니 반짝이는 포인트가 나옵니다! +" + sp + "sp";
	        }

	        int idx = ThreadLocalRandom.current().nextInt(rewardItemIds.size());
	        int itemId = rewardItemIds.get(idx);

	        HashMap<String,Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId", itemId);
	        inv.put("qty", 1);
	        inv.put("delYn", "0");
	        inv.put("gainType", "BAG_OPEN");

	        botNewService.insertInventoryLogTx(inv);

	        String itemName = botNewService.selectItemNameById(itemId);

	     // 아이템 전체 정보 조회 (권장: ITEM_CODE / ATK_MIN 등 얻기 위해)
	        HashMap<String,Object> info = botNewService.selectItemDetailById(itemId);  
	        // Map 형태라는 가정: ITEM_CODE, ATK_MIN, ATK_MAX, HP_REGEN, HP_MAX, CRI_DMG...

	        String label = itemName;

	        // 9000번대 = 유물
	        if (itemId >= 9000 && itemId < 10000) {
	            // buildRelicStatSuffix(HashMap row) 그대로 사용 가능!
	            String suffix = buildRelicStatSuffix(info);
	            if (!suffix.isEmpty()) {
	                label += suffix;    // 예: 고대돌조각(ATK+30~30)
	            }
	        }
	        return "가방을 열어보니 [" + label + "] 아이템을 획득했습니다!";
	    }
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

	    final int weaponLv      = ctx.weaponLv;
	    final int weaponBonus   = ctx.weaponBonus;

	    final int bAtkMinRaw    = ctx.bAtkMinRaw;
	    final int bAtkMaxRaw    = ctx.bAtkMaxRaw;
	    final int bCriRaw       = ctx.bCriRaw;
	    final int bCriDmgRaw    = ctx.bCriDmgRaw;
	    final int bHpMaxRaw     = ctx.bHpMaxRaw;
	    final int bRegenRaw     = ctx.bRegenRaw;

	    // 직업 보너스 분리해서 보고 싶으면 calcUserBattleContext 에서 채워두었다고 가정
	    final int jobHpMaxBonus   = ctx.jobHpMaxBonus;   // 없으면 0
	    final int jobRegenBonus   = ctx.jobRegenBonus;   // 없으면 0

	    final String pointStr   = String.format("%,d sp", ctx.currentPoint);
	    final int lifetimeSp    = ctx.lifetimeSp;

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
	    sb.append("❤️HP: ").append(effHp).append(" / ").append(finalHpMax)
	      .append(",5분당회복+").append(shownRegen).append(NL).append(NL);

	    JobDef jobDef = JOB_DEFS.get(job);
	    if (jobDef != null && jobDef.attackLine != null && !jobDef.attackLine.isEmpty()) {
	        sb.append(jobDef.attackLine).append(NL);
	    }

	    sb.append("▶ 현재 타겟: ").append(targetName)
	      .append(" (MON_NO=").append(u.targetMon).append(")");

	    // 누적 전투
	    sb.append(allSeeStr);

	    // ─ ATK 상세 ─
	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL)
	      .append("   └ 기본 (").append(baseMin).append("~").append(baseMax).append(")").append(NL)
	      .append("   └ 시즌1 강화: ").append(weaponLv).append("강 (max+").append(weaponBonus).append(")").append(NL)
	      .append("   └ 아이템 (min").append(formatSigned(bAtkMinRaw))
	      .append(", max").append(formatSigned(bAtkMaxRaw)).append(")").append(NL);

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

	    // ─ HP 상세 ─
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
	    if ("파이터".equals(job) && (jobHpMaxBonus != 0)) {
	        sb.append("   └ 직업 (HP")
	          .append(formatSigned(jobHpMaxBonus))
	          .append(",5분당회복")
	          .append(formatSigned(0))
	          .append(")").append(NL);
	    }

	    if ("전사".equals(job)) {
	        sb.append("   └ 직업 (HP+")
	          .append(baseHpMax)
	          .append(")").append(NL);
	    }

	    // ─ 인벤토리 ─
	    try {
	        List<HashMap<String, Object>> bag = botNewService.selectInventorySummaryAll(targetUser, roomName);

	        sb.append(NL).append("▶ 인벤토리").append(NL);
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
	            catMap.put("※선물", new ArrayList<>());
	            catMap.put("※유물", new ArrayList<>());
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
	                        "MASTER".equalsIgnoreCase(typeStr);

	                if (isEquipType) {
	                    // 이름에 업그레이드 단계 "(+n)" 표시 (QTY-1)
	                    int plusLv = Math.max(0, qtyVal - 1);
	                    if (plusLv > 0) {
	                        label = label + "(+" + plusLv + ")";
	                    }

	                    double factor = calcEquipUpgradeFactor(qtyVal);

	                    int atkMin0 = parseIntSafe(Objects.toString(row.get("ATK_MIN"), "0"));
	                    int atkMax0 = parseIntSafe(Objects.toString(row.get("ATK_MAX"), "0"));
	                    int hpMax0  = parseIntSafe(Objects.toString(row.get("HP_MAX"), "0"));
	                    int regen0  = parseIntSafe(Objects.toString(row.get("HP_REGEN"), "0"));

	                    int atkMinUp = (int)Math.round(atkMin0 * factor);
	                    int atkMaxUp = (int)Math.round(atkMax0 * factor);
	                    int hpMaxUp  = (int)Math.round(hpMax0  * factor);
	                    int regenUp  = (int)Math.round(regen0  * factor);

	                    String atkMinStr = formatStatWithPlus(atkMin0, atkMinUp);
	                    String atkMaxStr = formatStatWithPlus(atkMax0, atkMaxUp);

	                    String hpMaxStr  = (hpMax0  != 0 ? formatStatWithPlus(hpMax0,  hpMaxUp)  : null);
	                    String regenStr  = (regen0  != 0 ? formatStatWithPlus(regen0,  regenUp)  : null);

	                    StringBuilder optSb = new StringBuilder();
	                    if (atkMin0 != 0 || atkMax0 != 0) {
	                        optSb.append(" 공격력 ")
	                             .append(atkMinStr)
	                             .append("~")
	                             .append(atkMaxStr);
	                    }
	                    if (hpMaxStr != null) {
	                        optSb.append(" 체력 ").append(hpMaxStr);
	                    }
	                    if (regenStr != null) {
	                        optSb.append(" 체젠 ").append(regenStr);
	                    }

	                    label = label + optSb.toString();

	                } else {
	                    if (qtyVal > 1) {
	                        label = label + "x" + qtyVal;
	                    }
	                }

	                String cat = resolveItemCategory(itemId);

	                // 유물(9000번대)에만 짧은 능력치 꼬리표 추가
	                if ("※유물".equals(cat)) {
	                    HashMap<String,Object> info = botNewService.selectItemDetailById(itemId);
	                    String relicStat = buildRelicStatSuffix(info);
	                    if (!relicStat.isEmpty()) {
	                        label += relicStat + NL;
	                    }
	                }

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

	    // 누적 처치
	    sb.append("누적 처치 기록 (총 ").append(totalKills).append("마리)").append(NL);
	    if (kills.isEmpty()) {
	        sb.append("기록 없음").append(NL);
	    } else {
	        for (KillStat ks : kills) {
	            String monName = ks.monName;

	            if (monName == null || monName.isEmpty()) {
	                Monster mm = monMap.get(ks.monNo);
	                if (mm != null)
	                    monName = mm.monName;
	            }

	            sb.append("- ").append(monName)
	              .append(" (MON_NO=").append(ks.monNo).append(") : ")
	              .append(ks.killCount).append("마리").append(NL);
	        }
	    }

	    // 업적
	    try {
	        List<HashMap<String,Object>> achv = botNewService.selectAchievementsByUser(targetUser, roomName);
	        sb.append(NL).append("▶ 업적").append(NL);
	        if (achv == null || achv.isEmpty()) {
	            sb.append("- 달성된 업적이 없습니다.").append(NL);
	        } else {
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
		if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
		if (input.isEmpty()) {
		    User u = botNewService.selectUser(userName, roomName);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("공격 타겟 목록입니다:").append(NL).append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterCompactLine(mm, userLv)); // ★ 레벨 비례 EXP 반영됨!
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
	        try { itemId = botNewService.selectItemIdByCode(raw); } catch (Exception ignore) {}
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

	    // 결제 (포인트 차감)
	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", -price);
	    pr.put("cmd", "BUY");
	    botNewService.insertPointRank(pr);

	    // ============================
	    // 인벤토리 적재 (장비는 중복구매 시 QTY 증가)
	    // ============================
	    int buyQty = 1; // 현재 /구매는 1개씩 구매
	    int finalQty = 1; // 👉 이 값을 나중에 옵션 표시에 사용

	    int itemIdInt = itemId; // 위에서 구한 itemId 그대로 사용
	    boolean upgradeOk = isUpgradableEquip(itemIdInt);

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
	            if (newQty > 4) {
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
	    if ("MARKET".equalsIgnoreCase(itemType)) {
	        // 장비: 강화 수량 기반 옵션 (공격력 1(+1)~1(+1) 형태)
	        optionStr = buildEnhancedOptionLine(item, finalQty);
	    } else {
	        // 기타: 기존 옵션 포맷 유지
	        optionStr = buildOptionTokensFromMap(item);
	    }

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


	private String buildCustomMarketAllMessage(String userName, String roomName) {


	    // 기본(키워드 없음 또는 기타)
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 람쥐 상점 전체 안내").append(NL)
	      .append("- /구매 100 or /구매 무기: 무기 카테고리").append(NL)
	      .append("- /구매 200 or /구매 투구: 투구 카테고리").append(NL)
	      .append("- /구매 000 or /구매 신규: 최근 등록 아이템").append(NL)
	      .append("- /구매 아이템명 : 개별 구매").append(NL);

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
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    // 문의방 제한 (구버전 그대로)
	    if ("람쥐봇 문의방".equals(roomName)) {
	        if (!"일어난다람쥐/카단".equals(userName)) {
	            return "문의방에서는 불가능합니다.";
	        }
	    }

	    // 쿨타임/HP 제한에서 쓰는 원래 param1 (구버전과 동일)
	    final String param1 = Objects.toString(map.get("param1"), "");

	    // ─────────────────────────────
	    // 1) 스탯 계산용 map 복사본 → param1 비워서 "타 유저 조회" 방지만 막음
	    //    (실제 전투 로직에서의 param1 사용은 위에서 받은 값으로 계속 진행)
	    // ─────────────────────────────
	    HashMap<String, Object> statMap = new HashMap<>(map);
	    statMap.put("param1", "");   // calcUserBattleContext 에서 다른 유저 검색 못 하게 막는 용도

	    // 2) 공통 스탯 계산
	    UserBattleContext ctx = calcUserBattleContext(statMap);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }

	    final User u = ctx.user;
	    String job   = (u.job == null ? "" : u.job.trim());
	    if (job.isEmpty()) {
	        return userName + " 님, /직업 을 통해 먼저 전직해주세요.";
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

	    // HP (아이템까지 포함, 직업 HP보너스 적용 전 베이스)
	    final int hpMaxWithItem  = baseHpMax + bHpMax;

	    // 리젠/HP, 크리 (calcUserBattleContext에서 직업 패시브/축복/흡혈귀 등 반영한 값)
	    int effRegen    = ctx.effRegen;
	    int effHpMax    = ctx.finalHpMax;  // 최종 전투용 HP_MAX (전사/파이터 HP 보너스 포함이라고 가정)
	    int effCritRate = ctx.shownCrit;
	    int effCriDmg   = ctx.shownCritDmg;

	    final boolean hasBless = ctx.hasBless;
	    final int blessRegen   = ctx.blessRegenBonus;

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
	        jobDmgMul = 1.2;   // 전사: 데미지 1.2배
	        // HP 보너스는 finalHpMax에 포함되어 있음 (이전 설계 기준)
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
	    int monMaxHp, monHpRemainBefore;
	    boolean lucky;
	    boolean dark = false; // 어둠몬스터 여부

	    if (ob != null) {
	        m = botNewService.selectMonsterByNo(ob.monNo);
	        if (m == null) return "진행중 몬스터 정보를 찾을 수 없습니다.";
	        lucky = (ob.luckyYn != null && ob.luckyYn == 1);
	        dark  = (ob.luckyYn != null && ob.luckyYn == 2);
	        if (dark) {
	            monMaxHp = m.monHp * 5;
	            m.monAtk = m.monAtk * 2;
	        } else {
	            monMaxHp = m.monHp;
	        }
	        monHpRemainBefore = Math.max(0, monMaxHp - ob.totalDealtDmg);

	    } else {
	        m = botNewService.selectMonsterByNo(u.targetMon);
	        if (m == null) return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";

	        monMaxHp = m.monHp;
	        monHpRemainBefore = m.monHp;

	        // ★ 이 유저의 해당 몬스터 누적 킬 수 조회
	        int killCountForThisMon = 0;
	        try {
	            List<KillStat> kills = botNewService.selectKillStats(userName, roomName);
	            if (kills != null) {
	                for (KillStat ks : kills) {
	                    if (ks.monNo == m.monNo) {
	                        killCountForThisMon = ks.killCount;
	                        break;
	                    }
	                }
	            }
	        } catch (Exception ignore) {}

	        // ★ 300킬 이상 + 20% 확률이면 어둠몬
	        if (killCountForThisMon >= 300) {
	            double rnd = ThreadLocalRandom.current().nextDouble();
	            if (rnd < 0.20) {
	                dark = true;
	            }
	        }

	        boolean able_to_lucky_yn = false;
	        if (killCountForThisMon >= 50) {
	            able_to_lucky_yn = true;
	        }

	        if (!able_to_lucky_yn) {
	            lucky = false;
	        }

	        if (dark) {
	            monMaxHp = monMaxHp * 5;
	            m.monAtk = m.monAtk * 2;
	            monHpRemainBefore = monMaxHp;
	        }

	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get("ACHV_FIRST_CLEAR_MON_" + m.monNo);
	            if (v != null) globalCnt = v.intValue();
	        }

	        if (dark) {
	            lucky = false;
	        } else if (m.monNo > 50) {
	            lucky = false;
	            dark = false;
	        } else if (globalCnt == 0) {
	            lucky = false;
	            dark = false;
	        } else if ("사신".equals(job)) {
	            lucky = false;
	        } else if ("도사".equals(job)) {
	            if (m.monNo > 11) {
	                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE_DOSA / 2;
	            } else {
	                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE_DOSA;
	            }
	        } else {
	            if (m.monNo > 11) {
	                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE / 2;
	            } else {
	                lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE;
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
	    List<AchievementCount> userList = botNewService.selectAchvCountsGlobal(userName, roomName);
	    Map<String, Integer> userAchvMap = new HashMap<>();
	    if (userList != null) {
	        for (AchievementCount ac : userList) {
	            if (ac == null || ac.getCmd() == null) continue;
	            userAchvMap.put(ac.getCmd(), ac.getCnt());
	        }
	    }

	    // 파이터: 체력 비례 버서크 배율 (네가 추가한 신규 로직 유지)
	    if ("파이터".equals(job) && effHpMax > 0) {
	        double hpRatio = (double) u.hpCur / effHpMax;
	        if (hpRatio < 1) {
	            berserkMul = 1.0 + (1 - hpRatio) * 3.0;
	        }
	    }

	    Flags flags = rollFlags(u, m);

	    // 9) HP 20% 제한 체크
	    int origHpMax = u.hpMax;
	    int origRegen = u.hpRegen;

	    u.hpMax   = effHpMax;
	    u.hpRegen = effRegen;

	    try {
	        String hpMsg = buildBelowHalfMsg(userName, roomName, u, param1);
	        if (hpMsg != null) return hpMsg;
	    } finally {
	        u.hpMax   = origHpMax;
	        u.hpRegen = origRegen;
	    }

	    // 10) 도사 버프 (본인 + 방 전체)
	    DosaBuffEffect buffEff_self = null;
	    if ("도사".equals(job)) {
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
	            effHpMax
	    );

	    AttackCalc calc = dmg.calc;
	    flags = dmg.flags;
	    boolean willKill = dmg.willKill;

	    // 12) 사망 처리
	    int newHpPreview = Math.max(0, u.hpCur - calc.monDmg);
	    String deathAchvMsg = "";
	    if (!"사신".equals(job) && newHpPreview <= 0) {
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
	                + "5분 뒤 최대 체력의 10%로 부활하며," + NL
	                + "이후 5분마다 HP_REGEN 만큼 서서히 회복됩니다." + NL
	                + deathAchvMsg;
	    }

	    // 13) 처치/드랍 판단
	    Resolve res = resolveKillAndDrop(m, calc, willKill, u, lucky, dark);

	    // 궁수: 획득 EXP +25%
	    if ("궁수".equals(u.job)) {
	        int baseExp = res.gainExp;
	        int bonus   = (int)Math.floor(res.gainExp * 0.25);
	        res.gainExp = baseExp + bonus;
	    }

	    // 도적: 훔치기
	    String stealMsg = null;
	    if ("도적".equals(job) && !(m.monNo > 50)) {
	        double stealRate = 0.40;
	        int monLv  = m.monNo;
	        switch (monLv) {
	            case 17: stealRate -= 0.05;
	            case 16: stealRate -= 0.05;
	            case 15: stealRate -= 0.03;
	            case 14: stealRate -= 0.03;
	            case 13: stealRate -= 0.03;
	            case 12: stealRate -= 0.03;
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
	                        inv.put("delYn", "0");
	                        inv.put("gainType", "STEAL");
	                        botNewService.insertInventoryLogTx(inv);
	                        stealMsg = "✨ " + m.monName + "의 아이템을 훔쳤습니다! (" + dropName + "조각)";
	                        calc.jobSkillUsed = true;
	                    }
	                } catch (Exception ignore) {}
	            }
	        }
	    }

	    String dosaCastMsg = null;
	    if ("도사".equals(job)) {
	        dosaCastMsg = "✨ 도사의 기원! 다음 공격자 강화!";
	    }

	    // 14) DB 반영 + 레벨업 처리
	    LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res, effHpMax);
	    String bonusMsg = "";
	    String blessMsg = "";

	    if (u.lv < 8) {
	        blessMsg = grantBlessLevelBonus(userName, roomName, up.beforeLv, up.afterLv);
	    }

	    String bagDropMsg = "";
	    if (res.killed) {
	        botNewService.closeOngoingBattleTx(userName, roomName);

	        String firstClearMsg = grantFirstClearIfEligible(userName, roomName, m, globalAchvMap);
	        String killAchvMsg   = grantKillAchievements(userName, roomName);
	        String itemAchvMsg   = grantLightDarkItemAchievements(userName, roomName);

	        if ((firstClearMsg != null && !firstClearMsg.isEmpty())
	                || (killAchvMsg != null && !killAchvMsg.isEmpty())
	                || (itemAchvMsg != null && !itemAchvMsg.isEmpty())) {

	            bonusMsg = NL
	                    + firstClearMsg
	                    + killAchvMsg
	                    + itemAchvMsg;
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
	            botExtra.toString()
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
	    String curSpStr = formatSp(curPoint);

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
	                	try {
	            	    	if(userName.equals("은용/아르카나/1720")||userName.equals("나는야덩어리")) {
	            	    		return 2;
	            	    	}
	            	    	
	            	    }catch(Exception e) {}
	                    return 1; // 기본 확률
	                }
	            }
	        }
	    } catch (Exception ignore) {}
		 
	    
	    try {
	    	if(userName.equals("은용/아르카나/1720")||userName.equals("나는야덩어리")) {
	    		return 10.0;
	    	}
	    	
	    }catch(Exception e) {}
	    
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
	        return 4.0;
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
	    // 예시: 초반 몹은 5%, 후반 보스는 15%
	    switch (monNo) {
	        case 1: case 2: case 3: case 4: case 5:
	        case 6: case 7: case 8: case 9: case 10:
	            return 0.007;  // 0.7%
	        case 11: case 12: case 13:case 14: case 15:
	            return 0.012;  // 1.2%
	        case 16: case 17: case 18: case 19: case 20:
	            return 0.015;  // 1.5
	        case 51: case 52: case 53: case 61: case 62: case 63:
	        	return 0.005;  // 0.5%
	        case 91:
	        	return 0.02;  // 2%
	        default:
	            return 0;
	    }
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


		 // 👇 여기 추가
		 if (soldMerchantDiscount) {
		     sb.append(NL)
		       .append("※ 상인 할인으로 구매한 아이템은 할인가(90%) 기준으로 판매되었습니다.");
		 }
	    if (sold < reqQty) {
	        sb.append(NL)
	          .append("(요청 ").append(reqQty).append("개 → 실제 ").append(sold).append("개 판매)");
	    }

	    String achvMsg = grantShopSellAchievements(userName, roomName);
	    if (achvMsg != null && !achvMsg.isEmpty()) {
	        sb.append(NL).append("업적").append(NL)
	          .append(achvMsg);
	    }
	    
	    return sb.toString();
	}

	private String sellAllByCategory(String userName, String roomName, User u, boolean equipOnly) {
	    final int SHINY_MULTIPLIER = 5; //  빛템 5배
	    final String NL = BossAttackController.NL; // 클래스 상단 static final NL = "♬" 사용

	    List<HashMap<String, Object>> rows = botNewService.selectAllInventoryRowsForSale(userName, roomName);
	    if (rows == null || rows.isEmpty()) {
	        return equipOnly ? "판매 가능한 장비가 없습니다."
	                         : "판매 가능한 잡템이 없습니다.";
	    }

	    // 캐시: ITEM_ID → 장비 여부 / 판매가
	    Map<Integer, Boolean> equipCache = new HashMap<>();
	    Map<Integer, Integer> priceCache = new HashMap<>();

	    int sold = 0, soldNormal = 0, soldShiny = 0,soldDark=0, soldFrag = 0;
	    long totalSp = 0L;
	    boolean soldMerchantDiscount = false; // BUY_MERCHANT 판매 여부

	    for (HashMap<String, Object> row : rows) {

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        if (rid == null) continue;

	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (qty <= 0) continue;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow    = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDarkRow    = "DROP5".equalsIgnoreCase(gainType);
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
	        if (isShinyRow||isDarkRow) {
	            unitPrice = basePrice * SHINY_MULTIPLIER;
	        }

	        // 조각(STEAL)은 절반 가격
	        if (isStealRow) {
	            unitPrice = (int) Math.floor(unitPrice * 0.5);
	        }

	        // 6) 실제 판매: 전체판매이므로 가진 수량(qty) 전부 판매
	        int take = qty;

	        // 인벤토리에서 행 삭제 (전량 판매)
	        botNewService.updateInventoryDelByRowId(rid);

	        // 카운트/합계 누적
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
	    if (soldShiny  > 0) sb.append(NL).append("  · 빛 아이템: ").append(soldShiny).append("개");
	    if (soldDark  > 0) sb.append(NL).append("  · 어둠 아이템: ").append(soldDark).append("개");
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

	    String achvMsg = grantShopSellAchievements(userName, roomName);
	    if (achvMsg != null && !achvMsg.isEmpty()) {
	        sb.append(NL).append("업적").append(NL)
	          .append(achvMsg);
	    }
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
	    /*
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
	    */
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


	/**
	 * 상점/소비로 삭제된 인벤토리 누적 수량 기준 업적 지급
	 * - 기준: TBOT_POINT_NEW_INVENTORY의 DEL_YN='1' QTY 합계
	 * - 업적 CMD: ACHV_SHOP_SELL_{threshold}
	 */
	private String grantShopSellAchievements(String userName, String roomName) {
	    // {기준 수량, 보상 SP}
	    final int[][] rules = new int[][]{
	        {1000,  1000},
	        {5000,  3000},
	        {10000, 7000}
	    };

	    StringBuilder sb = new StringBuilder();
	    int soldCount = 0;

	    try {
	        soldCount = botNewService.selectInventorySoldCount(userName, roomName);
	    } catch (Exception ignore) { /* 안전 무시 */ }

	    for (int[] r : rules) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (soldCount >= threshold) {
	            String cmd = "ACHV_SHOP_SELL_" + threshold;

	            int already = 0;
	            try {
	                already = botNewService.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
	            } catch (Exception ignore) {}

	            if (already == 0) {
	                try {
	                    HashMap<String,Object> p = new HashMap<>();
	                    p.put("userName", userName);
	                    p.put("roomName", roomName);
	                    p.put("score", rewardSp);
	                    p.put("cmd", cmd);

	                    botNewService.insertPointRank(p);

	                    sb.append("✨ 상점 판매 ")
	                      .append(threshold)
	                      .append("회 달성 보상 +")
	                      .append(rewardSp)
	                      .append("sp 지급!♬")
	                      .append(NL);
	                } catch (Exception ignore) {}
	            }
	        }
	    }

	    return sb.toString();
	}
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
	    sb.append("다중구매: /구매 101,102,401  또는 /구매 목검x3,도씨검*3");
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
	        boolean upgradable =
	                (itemId >= 100 && itemId < 200) ||   // 무기
	                (itemId >= 200 && itemId < 300) ||   // 투구
	                (itemId >= 400 && itemId < 500);     // 갑옷
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
	                    sb.append(" (최대강화)");
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
	                sb.append("↘다음 구매시: (최대 강화 상태입니다)").append(NL);
	            }

	            sb.append(NL);
	        } else {
	            // 🔹 그 외 아이템: 기존 옵션 포맷 그대로
	            sb.append("↘옵션: ")
	              .append(buildOptionTokensFromMap(it))
	              .append(NL).append(NL);
	        }
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
	        sb.append(renderMonsterCompactLine(m,1)).append(NL);
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
			int maxHpBase = Math.max(1, u.hpMax); // 0 방지
            double hpRatio = (double) u.hpCur / maxHpBase;

            // 기본체력의 5배 아래일때 쓴다
            if (hpRatio < 2) {
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

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky,boolean dark) {
	    Resolve r = new Resolve();
	    r.killed = willKill;
	    r.lucky  = lucky;
	    r.dark = dark;
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
	    	if(dark) {
	    		baseKillExp *= 5;
	    	}else if(lucky) {
	    		baseKillExp *= 3;
	    	}
	    	
	    	r.gainExp = baseKillExp;
	    }
	    else {
	    	r.gainExp = (int)Math.round(baseKillExp/20)+1;  //
	    }

	    if ( lucky && willKill ) {
	        r.dropCode = "3";
	        return r;
	    }
	    if ( dark && willKill ) {
	        r.dropCode = "5";
	        return r;
	    }
	    
	    double dropRate = getDropRateByNo(m.monNo);  // ← 새 메서드 사용
	    
	    boolean drop = willKill && ThreadLocalRandom.current().nextDouble(0, 100) < dropRate;
	    r.dropCode = drop ? "1" : "0";
	    return r;
	}
	private double getDropRateByNo(int monNo) {
	    switch (monNo) {
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
	        case 51: return 80;
	        case 52: return 80;
	        case 53: return 80;
	        case 61: return 0;
	        case 62: return 0;
	        case 63: return 0;
	        case 91: return 1.0;
	        default: return 40.0;
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
	            	}
	            	
	                Integer itemId = botNewService.selectItemIdByName(dropName);
	                if (itemId != null) {
	                    HashMap<String, Object> inv = new HashMap<>();
	                    inv.put("userName",  userName);
	                    inv.put("roomName",  roomName);
	                    inv.put("itemId",    itemId);
	                    inv.put("qty",       1);
	                    inv.put("delYn",     "0");
	                    inv.put("gainType", gainType);
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

	    int luckyYn=0;
	    if(res.dark) {
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
	        int displayHpMax, // ← 표시용 HP Max(아이템 포함)
	        String midExtraLines,
	        String botExtraLines
	) {
	    StringBuilder sb = new StringBuilder();

	    // 헤더
	    sb.append("⚔ ").append(userName).append("님, ").append(NL)
	      .append("▶ ").append(m.monName).append("을(를) 공격!").append(NL).append(NL);

	    if (res.dark) {
	    	sb.append("✨ DARK MONSTER! (처치시 경험치×5, 어둠 드랍)").append(NL);
	    }
	    if (res.lucky) {
	        sb.append("✨ LUCKY MONSTER! (처치시 경험치×3, 빛 드랍)").append(NL);
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
	            	if ("5".equals(res.dropCode)) {
	                    sb.append("✨ 드랍 획득: 어둠").append(dropName).append(NL);
	                } else if ("3".equals(res.dropCode)) {
	                    sb.append("✨ 드랍 획득: 빛").append(dropName).append(NL);
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
	        	if ("5".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: 어둠").append(dropName).append(NL);
	            } else if ("3".equals(res.dropCode)) {
	                sb.append("✨ 드랍 획득: 빛").append(dropName).append(NL);
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
		boolean killed; String dropCode; int gainExp; int levelUpCount; boolean lucky; boolean dark;
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
	                case 5: desc = "5: hidden"; break; // 필요하면
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

	private boolean isSkeleton(Monster m) {
	    if (m == null) return false;
	    if (m.monNo == 10||m.monNo ==14||m.monNo ==15) return true;
	    if (m.monName.equals("해골")||m.monName.equals("리치")||m.monName.equals("하급악마")) {
	    	return true;
	    }
	    return false;
	}
	
	/** 통산 킬수 업적 보상 */
	private int calcTotalKillReward(int threshold) {
	    switch (threshold) {
	        case 300:  return 100;
	        case 500:  return 300;
	        case 1000: return 500;
	        case 2000: return 1000;
	        case 3000: return 3000;
	        case 4000: return 10000;
	        case 5000: return 50000;
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
	    int[] perMonThresholds = {50, 100, 300, 500, 1000,2000,3000,4000,5000};

	    for (KillStat ks : ksList) {
	        int monNo = ks.monNo;
	        int kills = ks.killCount;
	        totalKills += kills;

	        for (int th : perMonThresholds) {
	            if (kills >= th) {
	                String cmd = "ACHV_KILL" + th + "_MON_" + monNo;
	                int reward =  th*monNo/2;
	                sb.append(grantOnceIfEligible(userName, roomName, cmd, reward));
	            }
	        }
	    }

	    // 2) 통산 킬 업적
	    int[] totalThresholds = {50, 100, 300, 500, 1000,2000,3000,4000,5000};
	    for (int th : totalThresholds) {
	        if (totalKills >= th) {
	            String cmd = "ACHV_KILL_TOTAL_" + th;
	            int reward = calcTotalKillReward(th);
	            sb.append(grantOnceIfEligible(userName, roomName, cmd, reward));
	        }
	    }

	    return sb.toString();
	}

	private String grantLightDarkItemAchievements(String userName, String roomName) {

	    // 🔹 1) 누적 획득 개수 조회 (GAIN_TYPE 기준)
	    //    → 이 부분은 TBOT_INVENTORY_LOG (또는 네 로그 테이블)에서
	    //      GAIN_TYPE별 SUM(QTY)를 가져오는 Service/DAO 를 하나 만들어서 사용하면 됨.
	    int lightTotal = 0; // DROP3 누적
	    int darkTotal  = 0; // DROP5 누적
	    List<HashMap<String, Object>> gainRows = botNewService.selectTotalGainCountByGainType(userName, roomName);

	    if (gainRows != null) {
	        for (HashMap<String, Object> row : gainRows) {
	            String type = Objects.toString(row.get("GAIN_TYPE"), "");
	            int qty     = parseIntSafe(Objects.toString(row.get("TOTAL_QTY"), "0"));

	            if ("DROP3".equals(type)) {
	                lightTotal = qty;
	            } else if ("DROP5".equals(type)) {
	                darkTotal = qty;
	            }
	        }
	    }
	    if (lightTotal <= 0 && darkTotal <= 0) {
	        return "";
	    }

	    StringBuilder sb = new StringBuilder();

	    // 🔹 2) 공통 threshold 정의 (원하는 대로 조절)
	    int[] thresholds = {1, 10, 50, 100, 300, 500, 1000, 2000};

	    // 🔹 3) 빛 아이템 누적 업적
	    for (int th : thresholds) {
	        if (lightTotal >= th) {
	            String cmd   = "ACHV_LIGHT_ITEM_" + th;     // 예: ACHV_LIGHT_ITEM_50
	            int rewardSp = calcLightItemReward(th);     // 아래에서 정의
	            sb.append(grantOnceIfEligible(userName, roomName, cmd, rewardSp));
	        }
	    }

	    // 🔹 4) 어둠 아이템 누적 업적
	    for (int th : thresholds) {
	        if (darkTotal >= th) {
	            String cmd   = "ACHV_DARK_ITEM_" + th;      // 예: ACHV_DARK_ITEM_50
	            int rewardSp = calcDarkItemReward(th);
	            sb.append(grantOnceIfEligible(userName, roomName, cmd, rewardSp));
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
	    if (th <= 1000)return 20000;
	    return 0;
	}

	private int calcDarkItemReward(int th) {
	    // 예시: 어둠템은 좀 더 희귀하다고 가정해서 빛템보다 1.5배 정도
	    int base = calcLightItemReward(th);
	    return (int)Math.round(base * 1.5);
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
	        case 15: return 5000;
	        case 16: return 5000;
	        case 17: return 5000;
	        case 18: return 5000;
	        case 19: return 5000;
	        case 20: return 5000;
	    }
	    return 0;
	}
	
	/**
	 * 업적 리스트를:
	 * - 축하보상 숨기고
	 * - 통산 처치 / 몬스터별 킬 / 죽음 극복 은 [..] 형태로 묶어서 출력
	 */
	// 업적 문자열 패턴
	private static final Pattern P_TOTAL_KILL =
	        Pattern.compile("^통산 처치 (\\d+)회 달성$");
	private static final Pattern P_DEATH_OVERCOME =
	        Pattern.compile("^죽음 극복 (\\d+)회 달성$");
	private static final Pattern P_MONSTER_KILL =
	        Pattern.compile("^(.+?) (\\d+)킬 달성$");
	private static final Pattern P_LIGHT_ITEM_GET=
			Pattern.compile("^빛 아이템 획득 (\\d+)회 달성$");
	private static final Pattern P_DARK_ITEM_GET =
			Pattern.compile("^어둠 아이템 획득 (\\d+)회 달성$");

	private void renderAchievementLinesCompact(
	        StringBuilder sb,
	        List<HashMap<String, Object>> achv,
	        Map<Integer, Monster> monMap) {

	    // 1) 카테고리별 버킷
	    //    - 최초토벌/기타: 그대로 출력
	    //    - 통산 처치: 숫자 모아 [a/b/c]
	    //    - 죽음 극복: 숫자 모아 [a/b/c]
	    //    - 몬스터별 킬: 몬스터 이름별로 숫자 모아 [a/b/c]
	    List<String> others = new ArrayList<>();                 // 최초토벌 등
	    java.util.SortedSet<Integer> totalKillSteps = new java.util.TreeSet<>();
	    java.util.SortedSet<Integer> deathSteps = new java.util.TreeSet<>();
	    Map<String, java.util.SortedSet<Integer>> monKillSteps = new LinkedHashMap<>();
	    java.util.SortedSet<Integer> lightItemSteps = new java.util.TreeSet<>();
	    java.util.SortedSet<Integer> darkItemSteps = new java.util.TreeSet<>();

	    for (HashMap<String, Object> row : achv) {
	        if (row == null) continue;

	        String cmd = Objects.toString(row.get("CMD"), "");
	        String label = formatAchievementLabelSimple(cmd, monMap);
	        if (label == null) continue;
	        label = label.trim();
	        if (label.isEmpty()) continue;

	        // 1-1) 축하보상은 공격정보에서 노출하지 않음
	        if (label.contains("축하보상")) {
	            continue;
	        }

	        // 1-2) 패턴 매칭
	        Matcher mTotal = P_TOTAL_KILL.matcher(label);
	        Matcher mDeath = P_DEATH_OVERCOME.matcher(label);
	        Matcher mMon = P_MONSTER_KILL.matcher(label);
	        Matcher mLightItem = P_LIGHT_ITEM_GET.matcher(label);
	        Matcher mDarkItem = P_DARK_ITEM_GET.matcher(label);

	        if (mTotal.matches()) {
	            int v = parseIntSafe(mTotal.group(1));
	            if (v > 0) totalKillSteps.add(v);
	            continue;
	        }

	        if (mDeath.matches()) {
	            int v = parseIntSafe(mDeath.group(1));
	            if (v > 0) deathSteps.add(v);
	            continue;
	        }

	        if (mMon.matches()) {
	            String monName = mMon.group(1).trim();  // 예: 산적, 사과나무, 새끼용 ...
	            int v = parseIntSafe(mMon.group(2));
	            if (monName.isEmpty() || v <= 0) {
	                others.add(label);
	                continue;
	            }
	            java.util.SortedSet<Integer> set = monKillSteps.get(monName);
	            if (set == null) {
	                set = new java.util.TreeSet<>();
	                monKillSteps.put(monName, set);
	            }
	            set.add(v);
	            continue;
	        }
	        if (mLightItem.matches()) {
	            int v = parseIntSafe(mLightItem.group(1));
	            if (v > 0) lightItemSteps.add(v);
	            continue;
	        }
	        if (mDarkItem.matches()) {
	            int v = parseIntSafe(mDarkItem.group(1));
	            if (v > 0) darkItemSteps.add(v);
	            continue;
	        }

	        // 위 어느 패턴에도 안 걸리면 (예: 최초토벌 등) 그대로 보존
	        others.add(label);
	    }

	    // 2) 출력 순서:
	    //    1) others (최초토벌 등)
	    //    2) 통산 처치
	    //    3) 몬스터별 킬
	    //    4) 죽음 극복
	    for (String line : others) {
	        sb.append("✨ ").append(line).append(NL);
	    }

	    if (!totalKillSteps.isEmpty()) {
	        sb.append("✨ 통산 처치 [")
	          .append(joinStepNumbers(totalKillSteps))
	          .append("]회 달성").append(NL);
	    }

	    for (Map.Entry<String, java.util.SortedSet<Integer>> e : monKillSteps.entrySet()) {
	        String monName = e.getKey();
	        java.util.SortedSet<Integer> steps = e.getValue();
	        if (steps == null || steps.isEmpty()) continue;

	        sb.append("✨ ")
	          .append(monName)
	          .append(" [")
	          .append(joinStepNumbers(steps))
	          .append("]킬 달성").append(NL);
	    }

	    if (!deathSteps.isEmpty()) {
	        sb.append("✨ 죽음 극복 [")
	          .append(joinStepNumbers(deathSteps))
	          .append("]회 달성").append(NL);
	    }
	    if (!lightItemSteps.isEmpty()) {
	    	sb.append("✨ 빛 획득 [")
	    	.append(joinStepNumbers(lightItemSteps))
	    	.append("]회 달성").append(NL);
	    }
	    if (!darkItemSteps.isEmpty()) {
	    	sb.append("✨ 어둠 획득 [")
	    	.append(joinStepNumbers(darkItemSteps))
	    	.append("]회 달성").append(NL);
	    }
	}

	/** TreeSet<Integer> → "300/500/1000" 형식으로 이어 붙이기 */
	private static String joinStepNumbers(java.util.SortedSet<Integer> steps) {
	    StringBuilder tmp = new StringBuilder();
	    boolean first = true;
	    for (Integer v : steps) {
	        if (v == null) continue;
	        if (!first) tmp.append(",");
	        tmp.append(v);
	        first = false;
	    }
	    return tmp.toString();
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
	 	    eff.addHp       = dosaCriDmg;
	    }else {
	    	dosaLvBonus = (int) Math.round(dosaLv * 0.5);
	    	dosaCriDmg = (int) Math.round(dosaAtkMax * 0.1);
	    	eff.addAtkMin   = dosaLvBonus;
		    eff.addAtkMax   = dosaLvBonus;
		    eff.addCritRate = dosaLvBonus;
		    eff.addCritDmg  = dosaCriDmg;
		    eff.addHp       = dosaCriDmg*3;
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
	        int effHpMax
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

	   
	    
	    // -----------------------------
	    // 2) 궁수 저격, 프리스트 스켈레톤 추가뎀
	    // -----------------------------
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
	    
	    if ("용기사".equals(job)) {
	        if (u.hpCur >= effHpMax) {
	        	out.dmgCalcMsg += "풀HP DMG "+baseAtk+"→";
	        	baseAtk = (int)Math.round(baseAtk * 1.5);
	        	out.dmgCalcMsg += baseAtk+NL;
	        }
	        
	        int overCrit = Math.max(0, effCritRate-100);
	        if (overCrit > 0) {
	            int bonus = (int)Math.round(effCritRate*3); 
	            out.dmgCalcMsg += "크리율 보너스 ("+bonus+") "+baseAtk+"→";
	            baseAtk += bonus;
	            out.dmgCalcMsg += baseAtk+NL;
	        }
	        int overCriDmg = Math.max(0, effCriDmg-150); 
	        if (overCriDmg > 0) {
	        	int bonus = (int)Math.round(effCriDmg*3); 
	        	out.dmgCalcMsg += "크리뎀 보너스 ("+bonus+") "+baseAtk+"→";
	        	baseAtk += bonus;
	        	out.dmgCalcMsg += baseAtk+NL;
	        }
	        
	        effCritRate = 0;
	        effCriDmg = 0;
	        crit = false;
	        if (m.monNo==13) {
	        	out.dmgCalcMsg += "용족 보너스 "+baseAtk+"→";
	        	baseAtk = (int)Math.round(baseAtk * 2);
	        	out.dmgCalcMsg += baseAtk;
	        }
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
	        
	    	if ("파이터".equals(job) ) {
	    		if(u.hpCur < effHpMax*0.3) {
	    			flags.monPattern = 1;
	    			calc.monDmg = 0;  // 방어 패턴이었으니 몬스터 피해는 0 유지
	    			calc.patternMsg = m.monName + "의 패턴파훼! 몬스터가 모든행동을 멈춥니다";
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
	        		int reduced = (int) Math.floor(calc.monDmg * 0.8);
		            if (reduced < 1) reduced = 1;
		            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		            calc.patternMsg = baseMsg + "(마나실드 필살피해 20% 감소 → " + reduced + ")";
		            calc.monDmg = reduced;
	        	}
	        }

	        // 🛡 전사: 보스 필살기 패링 (20% 확률)
	        if ("전사".equals(job) && flags.finisher && calc.monDmg > 0) {
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
	            double evadeRate = 0.50;
	            switch (monLv) {
		            case 17:
		            	evadeRate -= 0.05;
		            case 16:
		            	evadeRate -= 0.05;
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
	            int reduce = (int) Math.round(u.lv * 2)+m.monLv*2;
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
	    sb.append("전직 가능한 직업 목록").append(ALL_SEE_STR);
	    for (JobDef def : JOB_DEFS.values()) {
	    	sb.append(def.name).append(":");
	        sb.append(def.listLine).append(NL);
	        sb.append(def.attackLine).append(NL).append(NL);
	        
	    }
	    sb.append("♬ /직업 [직업명] 으로 전직 가능합니다.").append(NL);
	    return sb.toString();
	}

	
	private String normalizeJob(String raw) {
		 if (raw == null) return null;
		    String s = raw.trim();

		    JobDef def = JOB_DEFS.get(s);
		    return (def != null ? def.name : null);
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

	    StringBuilder sb = new StringBuilder("※도사의 버프 효과: ");

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

	    // 나머지는 제한 없음
	    return Integer.MAX_VALUE;
	}

	private int getMaxAllowedByCategoryLabel(String label) {
	    if (label.contains("무기"))  return 5;    // 100번대
	    if (label.contains("투구"))  return 1;    // 200번대
	    if (label.contains("갑옷"))  return 1;    // 400번대
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
	    if (itemId >= 900 && itemId < 1000) return "※선물";   // 900번대
	    if (itemId >= 9000 && itemId < 10000) return "※유물"; // 9000번대 
	    return "※기타";
	}
	// 카테고리명 또는 숫자로 범위를 구하는 함수
	private int[] resolveCategoryRange(String raw) {
	    if (raw == null) return null;
	    String s = raw.trim();

	    // ➊ 숫자일 경우 ex) "100", "200"
	    if (s.matches("\\d+")) {
	        int num = Integer.parseInt(s);
	        int base = (num / 100) * 100;   // 123 → 100
	        return new int[]{ base, base + 100 };   // [100, 200)
	    }

	    // ➋ 문자 카테고리
	    switch (s) {
	    	case "신규": return new int[]{000, 1000};
	        case "무기": return new int[]{100, 200};
	        case "투구": return new int[]{200, 300};
	        case "행운": return new int[]{300, 400};
	        case "갑옷": return new int[]{400, 500};
	        case "반지": return new int[]{500, 600};
	        case "토템": return new int[]{600, 700};
	        case "전설": return new int[]{700, 800};
	        case "선물": return new int[]{900, 1000};
	        //case "유물": return new int[]{9000, 10000};
	    }

	    return null;
	}
	
	
	private String buildRelicStatSuffix(HashMap<String, Object> row) {
	    int atkMin = parseIntSafe(Objects.toString(row.get("ATK_MIN"), "0"));
	    int atkMax = parseIntSafe(Objects.toString(row.get("ATK_MAX"), "0"));
	    int atkCri = parseIntSafe(Objects.toString(row.get("ATK_CRI"), "0"));
	    int hpRegen = parseIntSafe(Objects.toString(row.get("HP_REGEN"), "0"));
	    int hpMax   = parseIntSafe(Objects.toString(row.get("HP_MAX"), "0"));
	    int criDmg  = parseIntSafe(Objects.toString(row.get("CRI_DMG"), "0"));

	    StringBuilder sb = new StringBuilder();
	    boolean first = true;

	    // 1) ATK_MIN
	    if (atkMin != 0) {
	        sb.append("ATK_MIN+").append(atkMin);
	        first = false;
	    }

	    // 2) ATK_MAX
	    if (atkMax != 0) {
	        if (!first) sb.append(", ");
	        sb.append("ATK_MAX+").append(atkMax);
	        first = false;
	    }

	    if (hpRegen != 0) {
	        if (!first) sb.append(", ");
	        sb.append("REGEN+").append(hpRegen);
	        first = false;
	    }
	    if (hpMax != 0) {
	        if (!first) sb.append(", ");
	        sb.append("HP+").append(hpMax);
	        first = false;
	    }
	    if (atkCri != 0) {
	    	if (!first) sb.append(", ");
	    	sb.append("CRI+").append(atkCri);
	    	first = false;
	    }
	    if (criDmg != 0) {
	        if (!first) sb.append(", ");
	        sb.append("CRI_DMG+").append(criDmg);
	        first = false;
	    }

	    if (first) return ""; // 전부 0이면

	    return "(" + sb.toString() + ")";
	}
	
	private String buildEnhancedOptionLine(HashMap<String,Object> item, int qty) {
	    if (item == null) return "";

	    int baseMin    = parseIntSafe(Objects.toString(item.get("ATK_MIN"), "0"));
	    int baseMax    = parseIntSafe(Objects.toString(item.get("ATK_MAX"), "0"));
	    int baseHp     = parseIntSafe(Objects.toString(item.get("HP_MAX"), "0"));
	    int baseRegen  = parseIntSafe(Objects.toString(item.get("HP_REGEN"), "0"));
	    int baseCri    = parseIntSafe(Objects.toString(item.get("ATK_CRI"), "0"));   // 치확
	    int baseCriDmg = parseIntSafe(Objects.toString(item.get("CRI_DMG"), "0"));   // 치피

	    // qty 1 → level 0, qty 2 → level 1 ...
	    int level = Math.max(0, qty - 1);
	    if (level > 3) level = 3; // 최대 3단계까지

	    // 레벨별 누적 강화율 (%)
	    int percent;
	    switch (level) {
	        case 1:  percent = 30; break; // +1
	        case 2:  percent = 50; break; // +1 +2 = 30 + 20
	        case 3:  percent = 60; break; // +1 +2 +3 = 30 + 20 + 10
	        default: percent = 0;  break; // level 0
	    }

	    int bonusMin    = (int)Math.floor(baseMin    * percent / 100.0);
	    int bonusMax    = (int)Math.floor(baseMax    * percent / 100.0);
	    int bonusHp     = (int)Math.floor(baseHp     * percent / 100.0);
	    int bonusRegen  = (int)Math.floor(baseRegen  * percent / 100.0);
	    int bonusCri    = (int)Math.floor(baseCri    * percent / 100.0);
	    int bonusCriDmg = (int)Math.floor(baseCriDmg * percent / 100.0);

	    StringBuilder sb = new StringBuilder();

	    // 공격력
	    if (baseMin != 0 || baseMax != 0) {
	        sb.append("[공격력 ")
	          .append(baseMin);
	        if (bonusMin != 0) {
	            sb.append("(").append(formatSigned(bonusMin)).append(")");
	        }
	        sb.append("~")
	          .append(baseMax);
	        if (bonusMax != 0) {
	            sb.append("(").append(formatSigned(bonusMax)).append(")");
	        }
	        sb.append("] ");
	    }

	    // HP
	    if (baseHp != 0) {
	        sb.append("[체력 ").append(baseHp);
	        if (bonusHp != 0) {
	            sb.append("(").append(formatSigned(bonusHp)).append(")");
	        }
	        sb.append("] ");
	    }

	    // 체젠
	    if (baseRegen != 0) {
	        sb.append("[체젠 ").append(baseRegen);
	        if (bonusRegen != 0) {
	            sb.append("(").append(formatSigned(bonusRegen)).append(")");
	        }
	        sb.append("] ");
	    }

	    // 치확
	    if (baseCri != 0) {
	        sb.append("[치확 ").append(baseCri);
	        if (bonusCri != 0) {
	            sb.append("(").append(formatSigned(bonusCri)).append(")");
	        }
	        sb.append("] ");
	    }

	    // 치피
	    if (baseCriDmg != 0) {
	        sb.append("[치피 ").append(baseCriDmg);
	        if (bonusCriDmg != 0) {
	            sb.append("(").append(formatSigned(bonusCriDmg)).append(")");
	        }
	        sb.append("] ");
	    }

	    return sb.toString().trim();
	}

	
	/** 장비 업그레이드 계수: QTY 1~4 → 1.0 / 1.3 / 1.5 / 1.6 */
	private double calcEquipUpgradeFactor(int qty) {
	    if (qty <= 1) return 1.0;
	    if (qty == 2) return 1.3;
	    if (qty == 3) return 1.5;
	    return 1.6; // QTY 4 이상도 1.6으로 캡
	}

	/** "100(+30)" 형식으로 포맷 */
	private String formatStatWithPlus(int base, int upgraded) {
	    int inc = upgraded - base;
	    if (inc <= 0) {
	        return String.valueOf(base);
	    }
	    return base + "(+" + inc + ")";
	}
	
	private boolean isUpgradableEquip(int itemId) {
	    return (itemId >= 100 && itemId < 200)   // 무기
	        || (itemId >= 200 && itemId < 300)   // 투구
	        || (itemId >= 400 && itemId < 500);  // 갑옷
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
	// 직업 메타데이터 맵 (등록 순서 유지 위해 LinkedHashMap)
	private static final Map<String, JobDef> JOB_DEFS = new LinkedHashMap<>();

	static {
	    // NL은 클래스에 이미 있는 상수라고 가정하고 그대로 사용
	    JOB_DEFS.put("전사", new JobDef(
	        "전사",
	        "▶ 육체능력이 변경되며, 패링 스킬 추가 ",
	        "⚔ 기본 HP만큼 추가 증가, 몬스터레벨에 따라 방어도 추가, 적의 필살기를 반격(20%),모든 적에게 데미지 추가(+20%)"
	    ));

	    JOB_DEFS.put("궁수", new JobDef(
	        "궁수",
	        "▶ 사냥감을 조준하는 집요한 추적자, 강력한 한방을 선사하지만, 쿨타임이 길어진다",
	        "⚔ 최종 데미지 ×1.6, EXP +25%, 공격시 13%확률로 강력한공격"
	    ));

	    JOB_DEFS.put("마법사", new JobDef(
	        "마법사",
	        "▶ 강력한 마법공격으로 몬스터의 방어태세를 무력화한다",
	        "⚔ 몬스터가 방어시 방어를 무시하고 피해 2배를 줌, 보스의 필살기를 마나실드로 방어(20%데미지감소)"
	    ));

	    JOB_DEFS.put("도적", new JobDef(
	        "도적",
	        "▶ 날렵한 손놀림으로 적의공격을 피하며,아이템을 강탈한다",
	        "⚔ 공격 시 40% 확률 추가 드랍(STEAL), 몬스터 기본 공격 50% 회피, [스틸,회피 no12부터 3%씩 감소] "
	    ));

	    JOB_DEFS.put("프리스트", new JobDef(
	        "프리스트",
	        "▶ 대사제의 축복을 받아 신성의힘으로 적을 물리친다",
	        "⚔ 아이템 HP/리젠 효과 1.25배, 몬스터에게 받는 피해 감소(20%), 언데드추가피해(+25%)"
	    ));
	    JOB_DEFS.put("도사", new JobDef(
	        "도사",
	        "▶ 도를 닦아 깨달음을 얻은 위인",
	        "⚔ 다음 공격하는 아군 강화(레벨*0.5만큼 능력강화,맥뎀*0.1만큼 치명뎀강화,"+NL+"매턴 공격시 자신 회복,자신의 럭키몬스터 등장 확률 증가"
	    ));
	    /*
        JOB_DEFS.put("사신", new JobDef(
            "사신",
            "▶ 이름하야 죽음의 신, 죽지않는다",
            "⚔ 아이템으로 인한 치명타,치명타뎀 증감처리 미적용, 체력 0에서도 죽지 않음,10%미만 체력에서 치명타확률50%증가"
        ));
        */
        JOB_DEFS.put("흡혈귀", new JobDef(
            "흡혈귀",
            "▶ 배가고프다, 나는 배가 고프다!",
            "⚔ 공격시 준피해의 20% 흡혈(공격&흡혈 선계산, 후피해)[max: 최대체력의20%], hp리젠 아이템의 증감처리 미적용"
        ));
        
        JOB_DEFS.put("용기사", new JobDef(
    		"용기사",
    		"▶ 용족의 마지막 후예, 배신당한 아픔을 가지고 있다",
    		"⚔ 풀HP일때 데미지1.5배, 100% 초과 치명타확률, 기본 치명타 데미지 초과분을 공격력으로 전환,치명타가 발생하지않음, 용족에 2배의 피해"
        ));
        
        JOB_DEFS.put("파이터", new JobDef(
    		"파이터",
    		"▶ 강인한 체력의 소유자, 체력이 낮아지면 적의 행동을 저지시킨다",
    		"⚔ 공격력 최대치, 치명타 배율 및 치명타데미지 증가가 체력으로 전환(3배수,치명 미발생)"+NL+"본인의 체력%에 따라 데미지 증가"
        ));
        
	}
	
	
	
}



