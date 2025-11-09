package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
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
	private static final int REVIVE_WAIT_MINUTES = 10;
	private static final String NL = "♬";
	// 🍀 Lucky: 전투 시작 시 10% 확률 고정(신규 전투에서만 결정)
	private static final double LUCKY_RATE = 0.15;

	/* ===== DI ===== */
	@Autowired LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")        BotService botService;
	@Resource(name = "core.prjbot.BotDAO")            BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewService")     BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")  BotSettleService botSettleService;

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

	    // 2) 입력한 직업명 파싱
	    String newJob = normalizeJob(sel);
	    if (newJob == null) {
	        return "존재하지 않는 직업입니다. /직업 으로 확인해주세요.";
	    }

	    // 3) 레벨 제한 (처음/변경 모두 공통 룰)
	    if (u.lv < 5) {
	        return "전직은 5레벨부터 가능합니다. 현재 레벨: " + u.lv;
	    }

	    // 4) 동일 직업으로 변경 시도
	    if (!curJob.isEmpty() && newJob.equals(curJob)) {
	        return "이미 [" + curJob + "] 직업입니다.";
	    }

	    // 5) 24시간 쿨타임 체크
	    // - JOB_CHANGE_DATE 기본값을 SYSDATE-6/24 로 잡았으므로
	    //   초기 유저는 바로 변경 가능하게 됨.
	    Timestamp lastChange = u.jobChangeDate;
	    if (lastChange != null) {
	        long diffSec = java.time.Duration.between(lastChange.toInstant(), java.time.Instant.now()).getSeconds();
	        long limitSec = 6L * 60 * 60;

	        if (diffSec < limitSec) {
	            long remain = limitSec - diffSec;
	            long rh = remain / 3600;
	            long rm = (remain % 3600) / 60;

	            return "직업 변경은 6시간에 1회 가능합니다." + NL
	                 + "다음 변경까지 남은 시간: " + rh + "시간 " + rm + "분";
	        }
	    }

	    // 6) 직업 변경 수행 (JOB + JOB_CHANGE_DATE = SYSDATE)
	    int updated = botNewService.updateUserJobAndChangeDate(userName, roomName, newJob);
	    if (updated <= 0) {
	        return "직업 변경 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	    }

	    // 7) 완료 메시지
	    return "✨ " + userName + "님, [" + newJob + "] 으로 직업이 변경되었습니다." + NL
	         + "(직업 변경은 6시간에 1회 가능합니다)";
	}


	private String buildJobDescriptionList() {
	    String NL = "♬";
	    return "전직 가능한 직업 목록" + NL +
	           "▶ 전사 : 기본 HP·공격력만큼 추가 적용, 버서크모드(체력이 낮아지면 데미지 2배)" + NL +
	           "▶ 궁수 : 최종 데미지 ×1.7, 공격 쿨타임 5분, EXP +15%, [히든]" + NL +
	           "▶ 마법사 : 몬스터 방어 패턴(패턴3) 50% 확률로 무시, 성공 시 피해 1.5배" + NL +
	           "▶ 도적 : 공격 시 20% 확률로 추가 드랍(STEAL), 몬스터 기본 공격 50% 회피" + NL +
	           "▶ 프리스트 : 아이템 HP/리젠 효과 1.5배, 특정 몬스터에게 받는 피해 50% 감소" + NL +
	           "▶ 상인 : 상점 구매 10% 할인, 드랍 판매가 10% 증가, 공격시 SP 추가 획득" + NL +
	           "♬ 6시간마다 /직업 [직업명] 으로 전직 가능합니다." + NL;
	}

	
	private String normalizeJob(String s) {
	    switch (s) {
	        case "전사": return "전사";
	        case "궁수": return "궁수";
	        case "마법사": return "마법사";
	        case "도적": return "도적";
	        case "프리스트": return "프리스트";
	        case "상인": return "상인";
	        default: return null;
	    }
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
	    final String pointStr = String.format("%d sp", currentPoint);

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

	    // 프리스트: 아이템 HP/리젠 효과 1.5배 (표시용 쪼개기)
	    if ("프리스트".equals(job)) {
	        int boostedHp    = (int)Math.round(bHpMaxRaw * 1.5);
	        int boostedRegen = (int)Math.round(bRegenRaw * 1.5);
	        jobHpMaxBonus    = boostedHp - bHpMaxRaw;
	        jobRegenBonus    = boostedRegen - bRegenRaw;
	        bHpMax           = boostedHp;
	        bRegen           = boostedRegen;
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
	        finalAtkMin += baseMin;
	        finalAtkMax += baseMax;
	    }

	    // 전사 HP 보너스: 기본 HP 한 번 더 (아이템 제외)
	    int finalHpMax = hpMaxWithItemAndPriest;
	    if ("전사".equals(job)) {
	        finalHpMax += baseHpMax;
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
	    sb.append(", EXP ").append(u.expCur).append("/").append(u.expNext).append(NL)
	      .append("포인트: ").append(pointStr).append(NL);

	    // ⚔ ATK 블럭 (monsterAttack 로직과 동일한 전사 보너스 구조)
	    sb.append("⚔ATK: ").append(finalAtkMin).append(" ~ ").append(finalAtkMax).append(NL)
	      .append("   └ 기본 (").append(baseMin).append("~").append(baseMax).append(")").append(NL)
	      .append("   └ 시즌1 강화: ").append(weaponLv).append("강 (max+").append(weaponBonus).append(")").append(NL)
	      .append("   └ 아이템 (min").append(formatSigned(bAtkMinRaw))
	      .append(", max").append(formatSigned(bAtkMaxRaw)).append(")").append(NL);

	    if ("전사".equals(job)) {
	        sb.append("   └ 직업 (min+")
	          .append(baseMin)
	          .append(", max+")
	          .append(baseMax)
	          .append(")")
	          .append(NL);
	    }

	    // ⚔ CRIT 블럭
	    sb.append("⚔CRIT: ").append(shownCrit).append("%  CDMG ").append(shownCritDmg).append("%").append(NL)
	      .append("   └ 기본 (").append(u.critRate).append("%, ").append(u.critDmg).append("%)").append(NL)
	      .append("   └ 아이템 (CRIT").append(formatSigned(bCriRaw))
	      .append("%, CDMG ").append(formatSigned(bCriDmgRaw)).append("%)").append(NL);

	    // ❤️ HP 블럭 (전사/프리스트 효과 포함)
	    sb.append("❤️ HP: ").append(effHp).append(" / ").append(finalHpMax)
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
	          .append(baseHpMax)
	          .append(")").append(NL);
	    }

	    // 직업 설명 라인
	    if ("궁수".equals(job)) {
	        sb.append("   ⚔ 직업 : 최종 데미지 ×1.7, 쿨타임 5분, EXP +15%").append(NL);
	    } else if ("전사".equals(job)) {
	        sb.append("   ⚔ 직업 : 기본 ATK(min/max)와 HP만큼 추가 적용, 버서크모드(체력이 낮아지면 데미지 최대 2배)").append(NL);
	    } else if ("마법사".equals(job)) {
	        sb.append("   ⚔ 직업 : 몬스터 방어 패턴(패턴3)을 50% 확률로 무시, 성공시 피해 1.5배").append(NL);
	    } else if ("도적".equals(job)) {
	        sb.append("   ⚔ 직업 : 공격 시 20% 확률 추가 드랍(STEAL), 몬스터 기본 공격 50% 회피").append(NL);
	    } else if ("프리스트".equals(job)) {
	        sb.append("   ⚔ 직업 : 아이템 HP/리젠 효과 1.5배, 특정몬스터에게 받는 피해 감소").append(NL);
	    } else if ("상인".equals(job)) {
	        sb.append("   ⚔ 직업 : 상점 구매 10% 할인, 드랍 판매가 10% 증가, 공격시 SP 추가 획득").append(NL);
	    }

	    sb.append("▶ 현재 타겟: ").append(targetName)
	      .append(" (MON_NO=").append(u.targetMon).append(")");

	    // 누적 전투
	    sb.append(allSeeStr);
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

	    // 인벤토리
	    try {
	        List<HashMap<String, Object>> bag = botNewService.selectInventorySummaryAll(targetUser, roomName);
	        sb.append(NL).append("▶ 인벤토리").append(NL);
	        if (bag == null || bag.isEmpty()) {
	            sb.append("- (비어있음)").append(NL);
	        } else {
	            for (HashMap<String, Object> row : bag) {
	                String itemName = Objects.toString(row.get("ITEM_NAME"), "-");
	                String qtyStr   = Objects.toString(row.get("TOTAL_QTY"), "0");
	                String typeStr  = Objects.toString(row.get("ITEM_TYPE"), "");
	                sb.append("- ").append(itemName);
	                if ("MARKET".equals(typeStr)) {
	                    sb.append(" (장비)");
	                } else {
	                    sb.append(" x").append(qtyStr);
	                }
	                sb.append(NL);
	            }
	        }
	    } catch (Exception ignore) {}

	    // 업적
	    try {
	        List<HashMap<String,Object>> achv = botNewService.selectAchievementsByUser(targetUser);
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
		      .append("▶ 선택 가능한 몬스터").append(NL);

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

	    User u = botNewService.selectUser(userName, roomName);
	    String job = (u == null || u.job == null) ? "" : u.job.trim();
	    boolean isMerchant = "상인".equals(job);

	    // 파라미터 없으면: 구매 가능 목록 노출
	    if (raw.isEmpty() || "리스트".equalsIgnoreCase(raw) || "list".equalsIgnoreCase(raw)) {
	        List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);
	        String compact = renderMarketListForBuy(list, userName);
	        return compact;
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
	    if (isMerchant) {
	        price = (int)Math.floor(price * 0.9);
	        usedMerchantDiscount = true;
	    }

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
	    if (isMerchant) {
	        sb.append(" (상인 할인 적용)");
	    }
	    sb.append(NL)
	      .append("↘옵션: ").append(sbOpt).append(NL)
	      .append("현재 포인트: ").append(afterPoint).append("sp");

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

	    final String param1 = Objects.toString(map.get("param1"), "");

	    // 1) 유저 조회
	    User u = botNewService.selectUser(userName, roomName);
	    if (u == null) return guideSetTargetMessage();

	    final String job = (u.job == null ? "" : u.job.trim());

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
	        bHpMax = (int) Math.round(bHpMax * 1.5);
	        bRegen = (int) Math.round(bRegen * 1.5);
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

	    int effCritRate = u.critRate + bCri;
	    int effRegen    = u.hpRegen + bRegen;
	    int effCriDmg   = u.critDmg + bCriDmg;
	    
	    
	 // === 직업별 보너스 계산 ===
	    int jobBonusMin = 0;
	    int jobBonusMax = 0;
	    int jobBonusHp  = 0;
	    double jobDmgMul = 1.0;
	    
	    
	    // 6) 궁수 배율 (최종 공격력 1.5배) → 실제 데미지 범위에 반영
	    if ("궁수".equals(job)) {
	        jobDmgMul = 1.7;
	    }else if ("전사".equals(job)) {
	        // ✅ 전사: "기본 min 한 번 더, 기본 max 한 번 더" (아이템/강화 제외)
	        jobBonusMin = baseMin;
	        jobBonusMax = baseMax;
	        jobBonusHp  = baseHpMax;
	    }
	 // 3) 전사 보너스(기본값 기준)를 각각 더함
	    int effAtkMin = (int) Math.round(atkMinWithItem * jobDmgMul + jobBonusMin);
	    int effAtkMax = (int) Math.round(atkMaxWithItem * jobDmgMul + jobBonusMax);
	    if (effAtkMax < effAtkMin) effAtkMax = effAtkMin;

	 // === 최종 전투용 HP_MAX ===
	    int effHpMax = hpMaxWithItem + jobBonusHp;
	    
	    // 7) 부활/자동회복 처리
	    String reviveMsg = reviveAfter1hIfDead(userName, roomName, u, effHpMax, effRegen);
	    boolean revivedThisTurn = false;
	    if (reviveMsg != null) {
	        if (!reviveMsg.isEmpty()) return reviveMsg;
	        revivedThisTurn = true;
	    }

	    int effectiveHp = revivedThisTurn
	            ? u.hpCur
	            : computeEffectiveHpFromLastAttack(userName, roomName, u, effHpMax, effRegen);
	    u.hpCur = Math.min(effectiveHp, effHpMax);
	    
	    // 🔹 전사 히든: 체력이 낮을수록 공격력 증가 (최대 +30%)
	    double berserkMul = 1.0;
	    if ("전사".equals(job) && effHpMax > 0) {
	        double hpRatio = (double) u.hpCur / effHpMax;
	        if (hpRatio < 0.5) {
	            berserkMul = 1.0 + (0.5 - hpRatio) * 2.0; // 0% ~ +30%
	        }
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
	        lucky = ThreadLocalRandom.current().nextDouble() < LUCKY_RATE;
	    }

	    // 9) 쿨타임 체크 (궁수 5분 반영)
	    CooldownCheck cd = checkCooldown(userName, roomName, param1, job);
	    if (!cd.ok) {
	        long min = cd.remainSeconds / 60;
	        long sec = cd.remainSeconds % 60;
	        return String.format("%s님, 공격 쿨타임 %d분 %d초 남았습니다.", userName, min, sec);
	    }

	    // 10) HP 30% 미만 가이드 (기존 로직, u에 effHpMax/effRegen 반영해서 호출)
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

	    // 11) 데미지 굴림
	    boolean crit = ThreadLocalRandom.current().nextDouble(0, 100) < clamp(effCritRate, 0, 100);

	    int baseAtkRangeMin = effAtkMin;
	    int baseAtkRangeMax = effAtkMax;

	 // 전사 버서커 배율 적용 (범위 전체에 곱)
	    baseAtkRangeMin = (int) Math.round(baseAtkRangeMin * berserkMul);
	    baseAtkRangeMax = (int) Math.round(baseAtkRangeMax * berserkMul);
	    if (baseAtkRangeMax < baseAtkRangeMin) baseAtkRangeMax = baseAtkRangeMin;
	    
	    int baseAtk = (baseAtkRangeMax <= baseAtkRangeMin)
	            ? baseAtkRangeMin
	            : ThreadLocalRandom.current().nextInt(baseAtkRangeMin, baseAtkRangeMax + 1);

	    double critMultiplier = Math.max(1.0, effCriDmg / 100.0);

	    int rawAtkDmg = crit ? (int)Math.round(baseAtk * critMultiplier) : baseAtk;

	    // 🎯 궁수 히든 저격: 유저Lv-2 ~ Lv+5 몬스터 대상, 5% 확률, 20배 데미지
	    boolean isSnipe = false;
	    if ("궁수".equals(job)) {
	    	int monLv = m.monNo; // int형이므로 null 비교 불필요
	        int userLv = u.lv;

	        if (monLv > 0 && monLv >= userLv - 2 && monLv <= userLv + 5) {
	            if (ThreadLocalRandom.current().nextDouble() < 0.01) {
	                isSnipe = true;
	                rawAtkDmg = rawAtkDmg * 20;
	            }
	        }
	    }
	    
	    // 12) 원턴킬 선판정
		boolean lethal = rawAtkDmg >= monHpRemainBefore;

		Flags flags = new Flags();
		AttackCalc calc = new AttackCalc();

		if (lethal) {
			flags.atkCrit = crit;
			flags.monPattern = 0;
			flags.snipe = isSnipe; // 추가
			calc.atkDmg = rawAtkDmg;
			calc.monDmg = 0;
			calc.patternMsg = null;
			if (crit) {
				calc.baseAtk = baseAtk;
				calc.critMultiplier = critMultiplier;
			}
		} else {
			flags = rollFlags(u, m);
			flags.atkCrit = crit;
			flags.snipe = isSnipe; // 저격 여부 유지

			boolean mageBreakGuard = false;

			// 🔹 마법사: 패턴3 방어 50% 확률 무시 + 무시 시 데미지 1.5배
			if ("마법사".equals(job) && flags.monPattern == 3) {
				if (ThreadLocalRandom.current().nextDouble() < 0.50) {
					mageBreakGuard = true;
					flags.monPattern = 1; // 방어 대신 무행동으로 취급
				}
			}
			
			calc = calcDamage(u, m, flags, baseAtk, crit, critMultiplier);

			if (mageBreakGuard) {
				// 방어를 깨뜨린 경우: 최종 공격 데미지 1.5배
				calc.atkDmg = (int) Math.round(calc.atkDmg * 1.5);
				calc.patternMsg = m.monName + "의 방어가 마법사의 힘에 의해 무너졌습니다! (피해 1.5배)";
			}
			// 🔹 프리스트: 해골 상대로 피격 데미지 50% 감소
			if ("프리스트".equals(job) && calc.monDmg > 0 && isSkeleton(m)) {
				int reduced = (int) Math.floor(calc.monDmg * 0.5);
				if (reduced < 1)
					reduced = 1; // 완전무효는 아님, 최소 1 유지
				String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
				calc.patternMsg = baseMsg + "(프리스트 효과로 피해 50% 감소 → " + reduced + ")";
				calc.monDmg = reduced;
			}

			// 🔹 도적: 50% 확률 회피 (몬스터 피해 무효화)
			if ("도적".equals(job) && calc.monDmg > 0) {
				if (ThreadLocalRandom.current().nextDouble() < 0.50) {
					String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
					calc.patternMsg = baseMsg + "도적의 회피! 피해를 받지 않았습니다.";
					calc.monDmg = 0;
				}
			}
			
			
		}

			

		 // 13) 즉사 처리
		 int newHpPreview = Math.max(0, u.hpCur - calc.monDmg);
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
		             .setNowYn(1)
		             .setDropYn(0)
		             .setDeathYn(1)
		             .setLuckyYn(0)
		     );
		     return userName + "님, 전투 불능 상태입니다." + NL
		             + "현재 체력: 0 / " + effHpMax + NL
		             + "10분 뒤 최대 체력의 10%로 부활하며," + NL
		             + "이후 5분마다 HP_REGEN 만큼 서서히 회복됩니다.";
		 }

	    // 14) 처치/드랍 판단
	    boolean willKill = calc.atkDmg >= monHpRemainBefore;
	    Resolve res = resolveKillAndDrop(m, calc, willKill, u, lucky);

	    // 🔹 상인: 공격 시마다, 해당 몬스터 드랍템 판매가의 10%를 SP로 추가 획득 (킬/드랍 여부 무관)
	    int merchantBonusSp = 0;
	    if ("상인".equals(job)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            int dropPrice = getDropPriceByName(dropName); // 이미 있는 헬퍼
	            if (dropPrice > 0) {
	                merchantBonusSp = (int) Math.floor(dropPrice * 0.10);
	                if (merchantBonusSp > 0) {
	                    HashMap<String,Object> pr = new HashMap<>();
	                    pr.put("userName", userName);
	                    pr.put("roomName", roomName);
	                    pr.put("score", merchantBonusSp);
	                    pr.put("cmd", "MERCHANT_ATTACK_BONUS");
	                    botNewService.insertPointRank(pr);
	                }
	            }
	        }
	    }
	    
	    // 🔹 궁수: 획득 EXP +10%
	    if ("궁수".equals(u.job)) {
	        int baseExp = res.gainExp;
	        int bonus = (int) Math.floor(res.gainExp * 0.15);
	        res.gainExp = baseExp + bonus;
	    }
	    
	    String stealMsg = null;
	 // 도적: 공격 시 20% 확률로 추가 드랍 (비처치도 가능)
	    if ("도적".equals(job)) {
	        if (ThreadLocalRandom.current().nextDouble() < 0.20) {
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
	                        stealMsg = "✨ " + m.monName + "의 아이템을 훔쳤습니다! (" + dropName + ")";
	                    }
	                } catch (Exception ignore) {}
	            }
	        }
	    }

	    // 15) DB 반영 + 로그
	    LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res);
	    String bonusMsg = "";
	    if (res.killed) {
	        // 진행중 전투 종료
	        botNewService.closeOngoingBattleTx(userName, roomName);

	        // ✅ 최초토벌 보상 (글로벌 1회 or 룸 기준: selectPointRankCountByCmdGlobal 구현에 따름)
	        String firstClearMsg = grantFirstClearIfEligible(userName, roomName, m);

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
	    
	    if (stealMsg != null) {
	        msg += NL + stealMsg;
	    }
	    
	    // ✅ 최초토벌/업적 메시지 추가
	    if (!bonusMsg.isEmpty()) {
	        msg += bonusMsg;
	    }
	    
	 // 🔹 상인 추가 보너스 안내
	    if (merchantBonusSp > 0) {
	        msg += NL + "✨ 상인 효과!" + merchantBonusSp + "sp 획득";
	    }

	    // 18) 현재 포인트 조회
	    int curPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (p == null ? 0 : p.intValue());
	    } catch (Exception ignore) {}
	    String curSpStr = formatSp(curPoint);

	    msg = msg + NL + "현재 포인트: " + curSpStr + NL + "/구매, /판매 로 상점열기!";

	    // 19) 전직 안내 (전직 안 했고 5레벨 이상일 때만)
	    if ((job.isEmpty()) && u.lv >= 5) {
	        msg += NL + "※ 아직 전직하지 않았습니다. /직업 으로 확인해주세요!";
	    }

	    return msg;
	}

	private boolean isSkeleton(Monster m) {
	    if (m == null) return false;
	    if (m.monNo == 10) return true;
	    String name = m.monName;
	    return name != null && name.contains("해골");
	}

	public String sellItem(HashMap<String, Object> map) {
	    final int SHINY_MULTIPLIER = 5; // ✨ 빛템 5배

	    final String userName = Objects.toString(map.get("userName"), "");
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String itemNameRaw = Objects.toString(map.get("param1"), "").trim();
	    final int reqQty = Math.max(1, parseIntSafe(Objects.toString(map.get("param2"), "1")));

	    if (userName.isEmpty() || roomName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
	    if (itemNameRaw.isEmpty()) return "판매할 아이템명을 입력해주세요. 예) /판매 도토리 5 또는 /판매 빛도토리 2";

	    User u = botNewService.selectUser(userName, roomName);
	    String job = (u == null || u.job == null) ? "" : u.job.trim();
	    boolean isMerchant = "상인".equals(job);

	    final boolean wantShinyOnly = itemNameRaw.startsWith("빛") || itemNameRaw.startsWith("✨");
	    final String baseName = itemNameRaw.replace("빛", "").replace("✨", "");

	    Integer itemId = null;
	    try { itemId = botNewService.selectItemIdByName(baseName); } catch (Exception ignore) {}
	    if (itemId == null) return "해당 아이템을 찾을 수 없습니다: " + itemNameRaw;

	    List<HashMap<String, Object>> rows = botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	    if (rows == null || rows.isEmpty()) return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";

	    int normalQty = 0, shinyQty = 0;
	    for (HashMap<String, Object> row : rows) {
	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if ("DROP3".equalsIgnoreCase(gainType)) shinyQty += Math.max(0, qty);
	        else normalQty += Math.max(0, qty);
	    }

	    int haveTotal = normalQty + shinyQty;
	    if (haveTotal <= 0) return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";

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
	    if (isMerchant && isEquip) {
	        return "상인 직업은 장비 아이템(MARKET)을 판매할 수 없습니다.";
	    }
	    
	    int need = Math.min(reqQty, haveTotal);
	    int sold = 0, soldNormal = 0, soldShiny = 0;
	    long totalSp = 0L;

	    boolean soldMerchantDiscount = false; // BUY_MERCHANT 물건을 실제로 판 적 있는지
	    boolean soldMerchantBonus = false;    // 상인 보너스(드랍템 10%↑) 적용된 판매가 있었는지
	    
	    for (HashMap<String, Object> row : rows) {
	        if (need <= 0) break;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        boolean isShinyRow = "DROP3".equalsIgnoreCase(gainType);
	        boolean isDropRow  = isShinyRow || "DROP".equalsIgnoreCase(gainType);
	        boolean isMerchantBuy  = "BUY_MERCHANT".equalsIgnoreCase(gainType);
	        
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
	        if (isMerchant && isDropRow) {
	            unitPrice = (int)Math.round(unitPrice * 1.1);
	        }

	     // 👇 실제로 해당 타입이 팔렸는지 기록
	        if (isMerchantBuy && take > 0) {
	            soldMerchantDiscount = true;
	        }
	        if (isMerchant && isDropRow && !isMerchantBuy && take > 0) {
	            soldMerchantBonus = true;
	        }
	        

	        if (qty == take) botNewService.updateInventoryDelByRowId(rid);
	        else botNewService.updateInventoryQtyByRowId(rid, qty - take);

	        if (isShinyRow) soldShiny += take; else soldNormal += take;
	        sold += take;
	        need -= take;
	        totalSp += (long) take * (long) unitPrice;
	    }

	    if (sold <= 0) {
	        String preStock = "보유: " + baseName + " " + normalQty + "개"
	                + (shinyQty > 0 ? ", ✨빛" + baseName + " " + shinyQty + "개" : "");
	        return "판매 가능한 재고가 없습니다." + NL + preStock;
	    }

	    HashMap<String, Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", (int) totalSp);
	    pr.put("cmd", "SELL");
	    botNewService.insertPointRank(pr);

	    int curPoint = 0;
	    try {
	        Integer curP = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (curP == null ? 0 : Math.max(0, curP));
	    } catch (Exception ignore) {}
	    String curPointStr = String.format("%,d sp", curPoint);

	    int remainNormal = Math.max(0, normalQty - soldNormal);
	    int remainShiny  = Math.max(0, shinyQty  - soldShiny);

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
	    if (!printed) remainSb = new StringBuilder("남은 재고: 없음");

	    String dispName = wantShinyOnly ? ("✨빛" + baseName) : baseName;

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
		 if (soldMerchantBonus) {
		     sb.append(NL)
		       .append("(상인 효과: 드랍 아이템 판매가 10% 보너스 적용)");
		 }
		 
	    if (sold < reqQty) {
	        sb.append(NL)
	          .append("(요청 ").append(reqQty).append("개 → 실제 ").append(sold).append("개 판매)");
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
	    sb.append("떠오르는샛별").append(NL);
	    if (rising == null || rising.isEmpty()) {
	        sb.append("- 데이터 없음").append(NL);
	    } else {
	        int rank = 1;
	        for (HashMap<String,Object> row : rising) {
	            String name = String.valueOf(row.get("USER_NAME"));
	            // 필요시 방 이름, 공격 횟수도 붙일 수 있음 (ex. " (12회)")
	            sb.append(rank).append("위: ").append(name).append(NL);
	            if (rank++ >= 5) break;
	        }
	    }
	    sb.append(allSeeStr).append(NL).append(NL);

	    /* === ⚔ 공격 랭킹 (기존 Top3) === */
	    sb.append("⚔ 공격 랭킹").append(NL);
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

	            sb.append(rank).append("위: ").append(name).append(NL)
	              .append("▶(Lv.").append(lv)
	              .append(", EXP ").append(expCur).append("/").append(expNext).append(")")
	              .append(NL);
	            rank++;
	            if (rank > 3) break;
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
	                sb.append("- ").append(monName).append(" 학살자: ").append(NL);
	                lastMonNo = monNo;
	                lastMonName = monName;
	            }
	            sb.append("▶").append(uName)
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
	            String monName   = String.valueOf(fc.get("MON_NAME"));
	            String firstUser = String.valueOf(fc.get("FIRST_CLEAR_USER"));
	            String firstJob  = Objects.toString(fc.get("FIRST_CLEAR_JOB"), "");
	            String firstTime = Objects.toString(fc.get("FIRST_CLEAR_DATE"), "");

	            clearedMonSet.add(monNo);

	            sb.append("- ").append(monName).append(NL)
	              .append("▶").append(firstUser);

	            if (!firstJob.isEmpty() && !"null".equalsIgnoreCase(firstJob)) {
	                sb.append("/").append(firstJob);
	            }
	            if (!firstTime.isEmpty() && !"null".equalsIgnoreCase(firstTime)) {
	                sb.append(" (").append(firstTime).append(")");
	            }
	            sb.append(NL);
	        }
	    }

	    List<HashMap<String,Object>> ongoing = botNewService.selectOngoingChallengesForUnclearedBosses();
	    if (ongoing != null && !ongoing.isEmpty()) {
	        sb.append(NL).append("⚔ 최초토벌 도전중").append(NL);
	        for (HashMap<String,Object> row : ongoing) {
	            String monName   = String.valueOf(row.get("MON_NAME"));
	            String userName2 = String.valueOf(row.get("USER_NAME"));
	            String job       = Objects.toString(row.get("JOB"), "");
	            int lv           = safeInt(row.get("LV"));
	            String startTime = String.valueOf(row.get("START_TIME"));
	            int monHp        = safeInt(row.get("MON_HP"));
	            int remainHp     = safeInt(row.get("REMAIN_HP"));

	            sb.append("- ").append(monName)
	              .append(" ").append(remainHp).append(" / ").append(monHp).append(NL)
	              .append("▶[도전 중] ").append(userName2);
	            if (!job.isEmpty()) sb.append("/").append(job);
	            sb.append("(Lv.").append(lv).append(")")
	              .append(" (").append(startTime).append(")")
	              .append(NL);
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
	private String renderMarketListForBuy(List<HashMap<String,Object>> items, String userName) {
	    if (items == null || items.isEmpty()) {
	        return "▶ " + userName + "님, 구매 가능 아이템" + NL + "- (없음)";
	    }
	    final String allSeeStr = "===";

	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ ").append(userName).append("님").append(NL);
	    sb.append("더보기 리스트에서 선택 후 구매해주세요").append(NL);
	    sb.append("예) /구매 목검  또는  /구매 102");
	    sb.append(allSeeStr);

	    for (HashMap<String,Object> it : items) {
	        int    itemId   = safeInt(it.get("ITEM_ID"));
	        String name     = String.valueOf(it.get("ITEM_NAME"));
	        int    price    = safeInt(it.get("ITEM_SELL_PRICE"));
	        String ownedYn  = String.valueOf(it.get("OWNED_YN"));

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
		if (u.hpCur >= effHpMax || effRegen <= 0) {
			return Math.min(u.hpCur, effHpMax);
		}

// 피격 시점 (회복 시작 기준)
		Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
		if (damaged == null) {
// 맞은 적이 없다면 피격 기반 리젠 없음
			return Math.min(u.hpCur, effHpMax);
		}

// 마지막 공격 시점 (여기까지의 리젠은 이미 HP에 반영되었다고 본다)
		Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

		Timestamp from = damaged;
		if (lastAtk != null && lastAtk.after(damaged)) {
			from = lastAtk;
		}

		long minutes = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
		long ticks = minutes / 5; // 5분당 1틱
		if (ticks <= 0) {
			return Math.min(u.hpCur, effHpMax);
		}

		long heal = ticks * (long) effRegen;
		long effective = (long) u.hpCur + heal;

		return (int) Math.min(effective, (long) effHpMax);
	}
	
	public String guideSetTargetMessage() {
	    final String NL = "♬";
	    List<Monster> monsters = botNewService.selectAllMonsters();
	    StringBuilder sb = new StringBuilder();
	    sb.append("공격 타겟이 없습니다. 먼저 타겟을 설정해주세요.").append(NL)
	      .append("예) /공격타겟 1   또는   /공격타겟 토끼").append(NL).append(NL)
	      .append("▶ 선택 가능한 몬스터").append(NL);
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
	    String sched = buildRegenScheduleSnippetEnhanced(userName, roomName, u, waitMin);
	    if (sched != null) sb.append(sched).append(NL);

	    // ✅ 풀HP ETA 출력
	    int toFull = minutesUntilFull(userName, roomName, u);
	    if (toFull == Integer.MAX_VALUE) {
	        sb.append("(풀HP까지: 리젠 없음)").append(NL);
	    } else if (toFull > 0) {
	        sb.append("(풀HP까지 약 ").append(toFull).append("분)").append(NL);
	    }

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
		case 4: c.monDmg = (int) Math.round(m.monAtk * 2.0); c.patternMsg = name + "의 필살기! (피해 " + c.monDmg + ")"; break;
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

	    int baseKillExp = (int)Math.round(
	        m.monExp * Math.max(0.1, 1.0 - Math.max(0, u.lv - m.monNo) * 0.2)
	    );

	    if (willKill) r.gainExp = lucky ? baseKillExp * 3 : baseKillExp;
	    else          r.gainExp = 2;  // ✅ 비처치 EXP 1 → 2

	    if (lucky && willKill) {
	        r.dropCode = "3";
	        return r;
	    }
	    boolean drop = willKill && ThreadLocalRandom.current().nextDouble(0, 100) < 40.0;
	    r.dropCode = drop ? "1" : "0";
	    return r;
	}

	/** HP/EXP/LV + 로그 저장 */
	private LevelUpResult persist(String userName, String roomName, User u, Monster m, Flags f, AttackCalc c, Resolve res) {
	    // 1) 유저 HP 반영
	    u.hpCur = Math.max(0, u.hpCur - c.monDmg);
	    LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);

	    // 2) 유저 스탯 DB 반영
	    botNewService.updateUserAfterBattleTx(
	        userName, roomName,
	        u.lv, u.expCur, u.expNext, u.hpCur, u.hpMax,
	        u.atkMin, u.atkMax, u.critRate, u.hpRegen
	    );

	    int deathYn = (u.hpCur == 0 && c.monDmg > 0) ? 1 : 0;

	    // 3) 🔹 드랍 인벤토리 적재 (DROP / DROP3)
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            try {
	                Integer itemId = botNewService.selectItemIdByName(dropName);
	                if (itemId != null) {
	                    HashMap<String, Object> inv = new HashMap<>();
	                    inv.put("userName", userName);
	                    inv.put("roomName", roomName);
	                    inv.put("itemId",  itemId);
	                    inv.put("qty",     1);
	                    inv.put("delYn",   "0");
	                    inv.put("gainType", "3".equals(res.dropCode) ? "DROP3" : "DROP");
	                    botNewService.insertInventoryLogTx(inv);
	                }
	            } catch (Exception ignore) {
	                // 드랍 적재 실패해도 전투 진행은 계속
	            }
	        }
	    }

	    // 4) BattleLog dropYn 세팅
	    int dropAsInt = "3".equals(res.dropCode) ? 3 : ("1".equals(res.dropCode) ? 1 : 0);

	    BattleLog log = new BattleLog()
	        .setUserName(userName)
	        .setRoomName(roomName)
	        .setLv(up.beforeLv)
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
	        .setDropYn(dropAsInt);

	    botNewService.insertBattleLogTx(log);

	    res.levelUpCount = up.levelUpCount;
	    return up;
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

	private static class Flags {
	    boolean atkCrit;
	    int monPattern;
	    boolean snipe; // 궁수 저격 여부
	}
	
	private static class AttackCalc {
		int atkDmg; int monDmg; int atkMin; int atkMax; String patternMsg;
	    int baseAtk; double critMultiplier;
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

	    String job = (u.job == null ? "" : u.job.trim());
	    boolean isWarrior = "전사".equals(job);

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

	        // 전사: 레벨업 증가량 2배
	        if (isWarrior) {
	            incHp    *= 2;
	            incAtkMin*= 2;
	            incAtkMax*= 2;
	        }

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
	    for (int t = toNextTick; t <= horizonMinutes; t += 5) {
	        int ticksAdded = (int)(((minutesPassed + t) / 5) - ticksSoFar);
	        if (ticksAdded <= 0) continue;

	        int proj = Math.min(maxHp, curHp + ticksAdded * regen);
	        sb.append("- ").append(t).append("분 뒤: HP ").append(proj)
	          .append(" / ").append(maxHp).append(NL);

	        if (proj >= maxHp) break; // 풀피 도달 시 중단
	    }

	    // === 풀 HP까지 남은 시간 계산 ===
	    int hpNeeded = maxHp - curHp;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)regen);
	    int minutesToFull = (toNextTick + (ticksNeeded - 1) * 5);
	    if (minutesToFull < 0) minutesToFull = 0;

	    String result = sb.toString().trim();
	    if (!result.isEmpty()) {
	        result += NL + "♬(풀HP까지 약 " + minutesToFull + "분)♬";
	    }

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
	    double rate = Math.max(0.1, 1.0 - over * 0.2);
	    int effExp = (int) Math.round(baseExp * rate);
	    boolean hasPenalty = (over > 0 && rate < 1.0);

	    StringBuilder sb = new StringBuilder();

	    // 1행: 기본 정보
	    sb.append(m.monNo).append(". ").append(m.monName)
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
	private String grantFirstClearIfEligible(String userName, String roomName, Monster m) {
	    if (m == null) return "";

	    String achvCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	    // 이미 이 방에서 해당 몬스터 최초토벌 보상이 지급되었는지 확인
	    Integer cnt = botNewService.selectPointRankCountByCmdGlobal(achvCmd);
	    if (cnt != null && cnt > 0) {
	        return ""; // 이미 다른 누가 받음
	    }

		int rewardSp = 0;
		switch (m.monNo) {
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
			rewardSp = 100;
			break;
		case 6:
			rewardSp = 300;
			break;
		case 7:
			rewardSp = 500;
			break;
		case 8:
			rewardSp = 500;
			break;
		case 9:
			rewardSp = 1000;
			break;
		case 10:
			rewardSp = 1000;
			break;

		default:
			break;
		}
	    if (rewardSp <= 0) {
	        return ""; // 보상 값이 0이면 지급하지 않음
	    }

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("cmd", achvCmd);
	    botNewService.insertPointRank(pr);

	    return "✨ 업적 달성! [" + m.monName + "] 최초 토벌자 보상 +" + rewardSp + "sp 지급되었습니다." + NL;
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

	
	/** 업적 CMD → 단순 업적명 라벨 (보상/날짜 없이) */
	private String formatAchievementLabelSimple(String cmd) {
	    if (cmd == null || cmd.isEmpty()) return "";

	    // 최초토벌
	    if (cmd.startsWith("ACHV_FIRST_CLEAR_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_FIRST_CLEAR_MON_".length()));
	            Monster m = botNewService.selectMonsterByNo(monNo);
	            return "최초토벌: " + (m == null ? ("몬스터#" + monNo) : m.monName);
	        } catch (Exception e) {
	            return "최초토벌";
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

	    return cmd;
	}

}
