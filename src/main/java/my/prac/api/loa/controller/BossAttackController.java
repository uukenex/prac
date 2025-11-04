package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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
	private static final int COOLDOWN_SECONDS = 30; // 30초 
	private static final int REVIVE_WAIT_MINUTES = 60;
	private static final String NL = "♬";
	// 🍀 Lucky: 전투 시작 시 10% 확률 고정(신규 전투에서만 결정)
	private static final double LUCKY_RATE = 0.10;

	/* ===== DI ===== */
	@Autowired LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")        BotService botService;
	@Resource(name = "core.prjbot.BotDAO")            BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewService")     BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")  BotSettleService botSettleService;

	/* ===== Public APIs ===== */

	/** 유저 기본정보 + 누적 처치/공격/사망 + 포인트 + 인벤토리 요약 */
	public String attackInfo(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";

	    // ① param1으로 다른 유저 조회 시도(별도 수정 없이 유지)
	    String targetUser = userName;
	    if (map.get("param1") != null && !Objects.toString(map.get("param1"), "").isEmpty()) {
	        List<String> newUserName = botNewService.selectParam1ToNewUserSearch(map);
	        if (newUserName != null && !newUserName.isEmpty()) targetUser = newUserName.get(0);
	        else return "해당 유저(" + map.get("param1") + ")를 찾을 수 없습니다.";
	    }

	    // ② 유저 조회
	    User u = botNewService.selectUser(targetUser, roomName);
	    if (u == null) return targetUser + "님의 정보를 찾을 수 없습니다.";

	    // ③ 현재 포인트 조회
	    int currentPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(targetUser, roomName);
	        currentPoint = (p == null ? 0 : p);
	    } catch (Exception ignore) {}
	    final String pointStr = String.format("%d sp", currentPoint);

	    // ④ 무기강/보너스 조회 (표시/전투용)
	    HashMap<String, Object> wm = new HashMap<String, Object>();
	    wm.put("userName", targetUser);
	    wm.put("roomName", roomName);
	    int weaponLv = 0;
	    try { weaponLv = botService.selectWeaponLvCheck(wm); } catch (Exception ignore) { weaponLv = 0; }
	    int weaponBonus = getWeaponAtkBonus(weaponLv); // 25강부터 +1 (상한 없음)

	    // ⑤ MARKET 장비 버프 합계(표시/전투용)
	    HashMap<String, Number> buffs = botNewService.selectOwnedMarketBuffTotals(userName, roomName);
		
		int bAtkMin = buffs.get("ATK_MIN").intValue();
		int bAtkMax = buffs.get("ATK_MAX").intValue();
		int bCri    = buffs.get("ATK_CRI").intValue();
		int bRegen  = buffs.get("HP_REGEN").intValue();
		int bHpMax  = buffs.get("HP_MAX").intValue();
		
		
	    // ⑥ 표시 수치 (DB 스탯은 불변)
	    int shownAtkMin = u.atkMin + bAtkMin;
	    int shownAtkMax = u.atkMax + weaponBonus + bAtkMax;
	    int shownCrit   = u.critRate + bCri;
	    int shownHpMax  = u.hpMax + bHpMax;
	    int shownRegen  = u.hpRegen + bRegen;

	    // ⑦ 읽기 회복(표시용, Max 클램프)
	    int effHp = computeEffectiveHpFromLastAttack(targetUser, roomName, u, shownHpMax, shownRegen);
	    if (effHp > shownHpMax) effHp = shownHpMax;

	    // ⑧ 누적 통계/타겟
	    List<KillStat> kills = botNewService.selectKillStats(targetUser, roomName);
	    int totalKills = 0; for (KillStat ks : kills) totalKills += ks.killCount;
	    AttackDeathStat ads = botNewService.selectAttackDeathStats(targetUser, roomName);
	    int totalAttacks = (ads == null ? 0 : ads.totalAttacks);
	    int totalDeaths  = (ads == null ? 0 : ads.totalDeaths);
	    Monster target = (u.targetMon > 0) ? botNewService.selectMonsterByNo(u.targetMon) : null;
	    String targetName = (target == null) ? "-" : target.monName;

	    // ⑨ 출력
	    StringBuilder sb = new StringBuilder();
	    sb.append("✨").append(targetUser).append(" 공격 정보").append(NL)
	      .append("Lv: ").append(u.lv)
	      .append(", EXP ").append(u.expCur).append("/").append(u.expNext).append(NL)
	      .append("포인트: ").append(pointStr).append(NL)
	      .append("⚔ ATK: ").append(shownAtkMin).append(" ~ ").append(shownAtkMax)
	        .append("  |  CRIT: ").append(shownCrit).append("%").append(NL)
	      .append("   └ 무기강: ").append(weaponLv).append("강 (Max +").append(weaponBonus).append(")")
	        .append("  + 아이템(+").append(bAtkMin).append("~").append(bAtkMax)
	        .append(", CRIT+").append(bCri).append("%)").append(NL)
	      .append("❤️ HP: ").append(effHp).append(" / ").append(shownHpMax)
	        .append("  | 10분당 회복 +").append(shownRegen).append(NL)
	      .append("▶ 현재 타겟: ").append(targetName)
	        .append(" (MON_NO=").append(u.targetMon).append(")").append(NL)
	      .append(NL);

	    sb.append("누적 전투 기록").append(NL)
	      .append("- 총 공격 횟수: ").append(totalAttacks).append("회").append(NL)
	      .append("- 총 사망 횟수: ").append(totalDeaths).append("회").append(NL)
	      .append(NL);

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

	    // ⑩ 인벤토리 요약(일반 + MARKET + 빛나는)
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
	                sb.append("- ").append(itemName).append(" x").append(qtyStr);
	                if ("MARKET".equals(typeStr)) sb.append(" (장비)");
	                if (itemName.startsWith("빛나는 ")) sb.append(" (판매불가)");
	                sb.append(NL);
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
			List<Monster> monsters = botNewService.selectAllMonsters();
			StringBuilder sb = new StringBuilder();
			sb.append("해당 몬스터(").append(input).append(")를 찾을 수 없습니다.").append(NL)
			  .append("아래 목록 중에서 선택해주세요:").append(NL).append(NL);
			for (Monster mm : monsters) sb.append(mm.monNo).append(" : ").append(mm.monName).append(NL);
			return sb.toString();
		}

		User u = botNewService.selectUser(userName, roomName);
		if (u == null) {
			botNewService.insertUserWithTargetTx(userName, roomName, m.monNo);
			return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다.";
		}
		if (u.targetMon == m.monNo) return "현재 타겟이 이미 " + m.monName + "(MON_NO=" + m.monNo + ") 입니다.";

		botNewService.closeOngoingBattleTx(userName, roomName);
		botNewService.updateUserTargetMonTx(userName, roomName, m.monNo);
		return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다.";
	}

	/** /구매 [아이템명|아이템번호]
	 *   - 파라미터 없으면: 구매 가능한 MARKET 아이템 리스트(+능력치) 출력
	 *   - 파라미터 있으면: 단일 구매 진행 (같은 아이템은 1회만 구매 가능)
	 *   - 결제: TBOT_POINT_RANK(new_yn='1', cmd='BUY')에 음수 점수 적립
	 *   - 인벤토리: TBOT_POINT_NEW_INVENTORY에 GAIN_TYPE='BUY', QTY=1, DEL_YN='0' 적재
	 */
	public String buyItem(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String raw = Objects.toString(map.get("param1"), "").trim();

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }

	    // 파라미터 없으면: 구매 가능 목록 노출
	    if (raw.isEmpty() || "리스트".equalsIgnoreCase(raw) || "list".equalsIgnoreCase(raw)) {
	    	
	    	List<HashMap<String,Object>> items = botNewService.selectMarketItemsWithOwned(userName, roomName);

	    	StringBuilder sb = new StringBuilder();
	    	sb.append("▶ ").append(userName).append("님, 구매 가능 아이템").append(NL);

	    	for (HashMap<String,Object> item : items) {
	    	    String name = (String)item.get("ITEM_NAME");
	    	    int price   = ((Number)item.get("ITEM_SELL_PRICE")).intValue();
	    	    String owned = "Y".equals(item.get("OWNED_YN")) ? "[구매완료]" : "";
	    	    int atkMin  = ((Number)item.get("ATK_MIN")).intValue();
	    	    int atkMax  = ((Number)item.get("ATK_MAX")).intValue();
	    	    int hpMax   = ((Number)item.get("HP_MAX")).intValue();
	    	    int regen   = ((Number)item.get("HP_REGEN")).intValue();
	    	    int cri     = ((Number)item.get("ATK_CRI")).intValue();

	    	    // 첫 줄: 아이템 + 상태
	    	    sb.append(item.get("ITEM_ID")).append(" ").append(name);
	    	    if (!owned.isEmpty()) sb.append(" ").append(owned);
	    	    sb.append(NL);

	    	    // 두 번째 줄: 옵션 설명
	    	    sb.append("(")
	    	      .append(price).append("sp");

	    	    if (atkMin != 0 || atkMax != 0) {
	    	        sb.append(", +ATK ").append(atkMin).append("~").append(atkMax);
	    	    }
	    	    if (cri > 0) {
	    	        sb.append(", +CRI ").append(cri).append("%");
	    	    }
	    	    if (hpMax > 0) {
	    	        sb.append(", +HP ").append(hpMax);
	    	    }
	    	    if (regen > 0) {
	    	        sb.append(", +HP_REGEN ").append(regen).append("/10m");
	    	    }

	    	    sb.append(")").append(NL);
	    	}
	    	return sb.toString();
	    	
	    }

	    // ====== 구매 진행 ======
	    // 1) 입력 → itemId 해석 (숫자면 바로, 아니면 이름→코드 순으로 시도)
	    Integer itemId = null;
	    if (raw.matches("\\d+")) {
	        try {
	            itemId = Integer.valueOf(raw);
	        } catch (Exception ignore) { /* no-op */ }
	    }
	    if (itemId == null) {
	        try {
	            itemId = botNewService.selectItemIdByName(raw);
	        } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try {
	            itemId = botNewService.selectItemIdByCode(raw);
	        } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + raw + NL + "(/구매 입력만으로 목록을 확인하세요)";
	    }

	    // 2) 아이템 상세 조회 (MARKET 인지, 가격/능력치)
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

	    
	    // 구매 직전
	    Integer ownedCnt = botNewService.selectHasOwnedMarketItem(userName, roomName, itemId);
	    if (ownedCnt != null && ownedCnt > 0) {
	        return "⚠ 이미 보유중인 아이템입니다. [" + itemName + "] 은(는) 중복구매가 불가합니다.";
	    }
	    

	    // 4) 포인트 확인
	    Integer tmpPoint = null;
	    try { tmpPoint = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int curPoint = (tmpPoint == null ? 0 : tmpPoint.intValue());
	    if (curPoint < price) {
	        return userName + "님, 포인트가 부족합니다. (가격: " + price + "sp, 보유: " + curPoint + "sp)";
	    }

	    // 5) 결제(포인트 차감: 음수 점수 적립) + 인벤토리 적재
	    //    - 포인트: TBOT_POINT_RANK(new_yn='1', cmd='BUY', score = -price)
	    HashMap<String, Object> pr = new HashMap<String, Object>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", Integer.valueOf(-price));
	    pr.put("cmd", "BUY");
	    botNewService.insertPointRank(pr);

	    //    - 인벤토리: QTY=1, DEL_YN='0', GAIN_TYPE='BUY'
	    HashMap<String, Object> inv = new HashMap<String, Object>();
	    inv.put("userName", userName);
	    inv.put("roomName", roomName);
	    inv.put("itemId",  Integer.valueOf(itemId));
	    inv.put("qty",     Integer.valueOf(1));
	    inv.put("delYn",   "0");
	    inv.put("gainType","BUY");
	    botNewService.insertInventoryLogTx(inv);

	    // 6) 구매 후 포인트
	    Integer tmpAfter = null;
	    try { tmpAfter = botNewService.selectCurrentPoint(userName, roomName); } catch (Exception ignore) {}
	    int afterPoint = (tmpAfter == null ? 0 : tmpAfter.intValue());
	    String afterPointStr = String.format("%dsp", afterPoint);

	    // 7) 능력치 표기(보여주기 용)
	    int atkMin   = getInt(item.get("ATK_MIN"));
	    int atkMax   = getInt(item.get("ATK_MAX"));
	    int atkCri   = getInt(item.get("ATK_CRI"));
	    int hpRegen  = getInt(item.get("HP_REGEN"));
	    int hpMax    = getInt(item.get("HP_MAX"));

	    StringBuilder bonus = new StringBuilder();
	    boolean printed = false;
	    if (atkMin != 0 || atkMax != 0) { bonus.append(String.format("+ATK %d~%d  ", atkMin, atkMax)); printed = true; }
	    if (atkCri != 0) { bonus.append(String.format("+CRI %d%%  ", atkCri)); printed = true; }
	    if (hpRegen != 0) { bonus.append(String.format("+HP_REGEN %d  ", hpRegen)); printed = true; }
	    if (hpMax != 0) { bonus.append(String.format("+HP_MAX %d  ", hpMax)); printed = true; }
	    if (!printed) bonus.append("(능력치 없음)");

	    // 8) 결과 메시지
	    StringBuilder sb = new StringBuilder();
	    sb.append("▶ 구매 완료").append(NL)
	      .append(userName).append("님, ").append(itemName).append("을(를) 구매했습니다.").append(NL)
	      .append("가격: ").append(price).append("sp").append(NL)
	      .append("능력치: ").append(bonus.toString()).append(NL)
	      .append("현재 포인트: ").append(afterPointStr);
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

	/** 몬스터 공격 (MARKET 장비 버프 + 무기보너스 적용, 럭키 유지, 드랍→인벤 반영) */
	public String monsterAttack(HashMap<String, Object> map) {
	    map.put("cmd", "monster_attack");

	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    final String param1 = Objects.toString(map.get("param1"), "");

	    // 1) 유저
	    User u = botNewService.selectUser(userName, roomName);
	    if (u == null) return guideSetTargetMessage();

	    // 2) MARKET 버프 합산
	    HashMap<String, Number> buffs = botNewService.selectOwnedMarketBuffTotals(userName, roomName);

	    int bAtkMin = buffs.get("ATK_MIN").intValue();
	    int bAtkMax = buffs.get("ATK_MAX").intValue();
	    int bCri    = buffs.get("ATK_CRI").intValue();
	    int bRegen  = buffs.get("HP_REGEN").intValue();
	    int bHpMax  = buffs.get("HP_MAX").intValue();
	    
	    
	    // 3) 무기 강화
	    int weaponLv = 0;
	    try { weaponLv = botService.selectWeaponLvCheck(map); } catch (Exception ignore) { weaponLv = 0; }
	    int weaponBonus = getWeaponAtkBonus(weaponLv);

	    // 4) 전투용 유효치
	    final int effAtkMin   = u.atkMin + bAtkMin;
	    final int effAtkMax   = u.atkMax + weaponBonus + bAtkMax;
	    final int effCritRate = u.critRate + bCri;
	    final int effHpMax    = u.hpMax + bHpMax;
	    final int effRegen    = u.hpRegen + bRegen;

	    // 5) 부활 체크
	    String reviveMsg = reviveAfter1hIfDead(userName, roomName, u, effHpMax, effRegen);
	    boolean revivedThisTurn = false;
	    if (reviveMsg != null) {
	        if (!reviveMsg.isEmpty()) return reviveMsg;
	        revivedThisTurn = true;
	    }

	    // 6) 읽기 회복 + HP 클램프
	    
	    int effectiveHp = revivedThisTurn ? u.hpCur : computeEffectiveHpFromLastAttack(userName, roomName, u, effHpMax, effRegen);
	    u.hpCur = Math.min(effectiveHp, effHpMax);

	    // 7) 진행중 전투/신규 전투 + 럭키 유지/결정 (OngoingBattle의 luckyYn 사용)
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

	    // 8) 쿨타임/50% 미만 안내
	    CooldownCheck cd = checkCooldown(userName, roomName, param1);
	    if (!cd.ok) {
	        long min = cd.remainSeconds / 60, sec = cd.remainSeconds % 60;
	        return String.format("%s님, 공격 쿨타임 %d분 %d초 남았습니다.", userName, min, sec);
	    }
	    int origHpMax = u.hpMax, origRegen = u.hpRegen;
	    u.hpMax = effHpMax; u.hpRegen = effRegen;
	    try {
	        String hpMsg = buildBelowHalfMsg(userName, roomName, u, param1);
	        if (hpMsg != null) return hpMsg;
	    } finally {
	        u.hpMax = origHpMax; u.hpRegen = origRegen;
	    }

	    // 9) 굴림
	    boolean crit = ThreadLocalRandom.current().nextDouble(0, 100) < clamp(effCritRate, 0, 100);
	    int baseAtk = (effAtkMax <= effAtkMin) ? effAtkMin
	                   : ThreadLocalRandom.current().nextInt(effAtkMin, effAtkMax + 1);
	    double critMultiplier = Math.max(1.0, u.critDmg / 100.0);
	    int rawAtkDmg = crit ? (int)Math.round(baseAtk * critMultiplier) : baseAtk;

	    // 10) 원턴킬 선판정
	    boolean lethal = rawAtkDmg >= monHpRemainBefore;

	    Flags flags = new Flags();
	    AttackCalc calc = new AttackCalc();
	    if (lethal) {
	        flags.atkCrit = crit;
	        flags.monPattern = 0;
	        calc.atkDmg = rawAtkDmg;
	        calc.monDmg = 0;
	        calc.patternMsg = null;
	        if (crit) { calc.baseAtk = baseAtk; calc.critMultiplier = critMultiplier; }
	    } else {
	        flags = rollFlags(u, m);
	        flags.atkCrit = crit;
	        calc = calcDamage(u, m, flags, baseAtk, crit, critMultiplier);
	    }

	    // 11) 즉사 처리
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
	        return userName + "님, 큰 피해로 쓰러졌습니다." + NL
	             + "현재 체력: 0 / " + u.hpMax + NL
	             + REVIVE_WAIT_MINUTES + "분 뒤 부활하여 50% 체력을 가집니다.";
	    }

	    // 12) 처치/드랍 판단
	    boolean willKill = calc.atkDmg >= monHpRemainBefore;
	    Resolve res = resolveKillAndDrop(m, calc, willKill, u, lucky);

	    // 13) DB 반영 + 로그
	    LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res);
	    if (res.killed) botNewService.closeOngoingBattleTx(userName, roomName);

	    // 14) [DROP → INVENTORY]
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String baseDrop = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!baseDrop.isEmpty()) {
	            Integer itemId = null;
	            try { itemId = botNewService.selectItemIdByName(baseDrop); } catch (Exception ignore) {}
	            if (itemId != null) {
	                HashMap<String,Object> inv = new HashMap<String,Object>();
	                inv.put("userName", userName);
	                inv.put("roomName", roomName);
	                inv.put("itemId",   itemId);
	                inv.put("qty",      1);
	                inv.put("delYn",    "0");
	                inv.put("gainType", res.lucky ? "DROP3" : "DROP"); // 빛나는은 판매불가
	                botNewService.insertInventoryLogTx(inv);
	            }
	        }
	    }

	    // 15) 메시지 (HP Max는 표시 Max 사용)
	    int shownMin = effAtkMin;
	    int shownMax = effAtkMax;
	    String msg = buildAttackMessage(
	    	    userName, u, m, flags, calc, res, up,
	    	    monHpRemainBefore, monMaxHp,
	    	    shownMin, shownMax,
	    	    weaponLv, weaponBonus,effHpMax
	    	);
	    
	 // ✅ 기존 쿼리(selectCurrentPoint) 재사용만 함 — 새 SQL 추가 없음
	    int curPoint = 0;
	    try {
	        Integer p = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (p == null ? 0 : p.intValue());
	    } catch (Exception ignore) {}
	    String curSpStr = formatSp(curPoint);

	    // ✔ 문구 붙이기
	    msg = msg + NL + "현재 포인트: " + curSpStr +NL+ "/구매, /판매 로 상점열기!";

	    return msg;
	}

	/** /판매 {아이템명} {수량} — DROP3(빛나는) 판매 금지 + FIFO */
	public String sellItem(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String itemName = Objects.toString(map.get("param1"), "").trim();
	    final int reqQty = Math.max(1, parseIntSafe(Objects.toString(map.get("param2"), "1")));

	    if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
	    if (itemName.isEmpty()) return "판매할 아이템명을 입력해주세요. 예) /판매 도토리 30";

	    // 아이템 ID
	    Integer itemId = null;
	    try { itemId = botNewService.selectItemIdByName(itemName); } catch (Exception ignore) {}
	    if (itemId == null) return "해당 아이템을 찾을 수 없습니다: " + itemName;

	    // 판매 단가
	    Integer priceObj = null;
	    try { priceObj = botNewService.selectItemSellPriceById(itemId); } catch (Exception ignore) {}
	    int price = (priceObj == null ? 0 : priceObj.intValue());
	    if (price <= 0) return "해당 아이템은 판매가 설정이 없어 판매할 수 없습니다: " + itemName;

	    // 보유 수량(집계) — 쿼리에서 DROP3 제외(아래 ‘매퍼 요구사항’ 참고)
	    Integer haveObj = null;
	    try { haveObj = botNewService.selectInventoryQty(userName, roomName, itemId); } catch (Exception ignore) {}
	    int have = (haveObj == null ? 0 : Math.max(0, haveObj.intValue()));
	    if (have <= 0) return "인벤토리에 보유 중인 [" + itemName + "]이(가) 없습니다.";

	    int need = Math.min(reqQty, have);
	    if (need <= 0) return "판매 수량이 올바르지 않습니다.";

	    // FIFO 차감 (DROP3 행은 스킵)
	    List<HashMap<String, Object>> rows = botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	    int sold = 0;
	    for (HashMap<String, Object> row : rows) {
	        if (need <= 0) break;

	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "DROP");
	        if ("DROP3".equalsIgnoreCase(gainType)) continue; // 빛나는: 판매 불가

	        String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	        int qty = parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	        if (rid == null || qty <= 0) continue;

	        if (qty <= need) {
	            botNewService.updateInventoryDelByRowId(rid);
	            sold += qty; need -= qty;
	        } else {
	            botNewService.updateInventoryQtyByRowId(rid, qty - need);
	            sold += need; need = 0;
	        }
	    }

	    long totalSp = (long)sold * (long)price;

	    // 포인트 적립
	    if (totalSp > 0) {
	        HashMap<String,Object> pr = new HashMap<String,Object>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score",   (int)totalSp);
	        pr.put("cmd",     "SELL");
	        botNewService.insertPointRank(pr);
	    }

	    // 현재 포인트
	    int curPoint = 0;
	    try {
	        Integer curP = botNewService.selectCurrentPoint(userName, roomName);
	        curPoint = (curP == null ? 0 : Math.max(0, curP.intValue()));
	    } catch (Exception ignore) {}
	    String curPointStr = String.format("%d sp", curPoint);

	    StringBuilder sb = new StringBuilder();
	    sb.append("⚔ ").append(userName).append("님,").append(NL)
	      .append("▶ 판매 결과").append(NL)
	      .append("- 아이템: ").append(itemName).append(NL)
	      .append("- 판매 수량: ").append(sold).append("개").append(NL)
	      .append("- 단가: ").append(price).append("sp").append(NL)
	      .append("- 합계 적립: ").append(totalSp).append("sp").append(NL)
	      .append("- 현재 포인트: ").append(curPointStr);
	    if (sold < reqQty) {
	        sb.append(NL).append("(보유 수량 부족으로 요청한 ").append(reqQty).append("개 중 ").append(sold).append("개만 판매)");
	    }
	    return sb.toString();
	}

	/* ===== Combat helpers ===== */

	// 변경 후  ✅ 효과치 & 10분당 1틱 규칙
	private String reviveAfter1hIfDead(String userName, String roomName, User u, int effHpMax, int effRegen) {
		if (u.hpCur > 0) return null;

	    // “마지막으로 공격받은 시각” 이후 60분
	    Timestamp baseline = getLastDamageBaseline(userName, roomName);
	    if (baseline == null) {
	        int half = (int) Math.ceil(effHpMax * 0.5);
	        botNewService.updateUserHpOnlyTx(userName, roomName, half);
	        u.hpCur = half;
	        return "";
	    }

	    Instant reviveAt = baseline.toInstant().plus(Duration.ofMinutes(REVIVE_WAIT_MINUTES));
	    Instant now = Instant.now();

	    if (now.isBefore(reviveAt)) {
	        long remainMin = (long) Math.ceil(Duration.between(now, reviveAt).getSeconds() / 60.0);
	        return "쓰러진 상태입니다. 약 " + remainMin + "분 후 자동 부활합니다.";
	    }

	    int half = (int) Math.ceil(effHpMax * 0.5);
	    long afterMin = Duration.between(reviveAt, now).toMinutes();
	    long healedTicks = Math.max(0, afterMin) / 10;      // ✅ 10분당 1틱
	    long healed = healedTicks * Math.max(0, (long) effRegen);
	    int effective = (int) Math.min((long) effHpMax, (long) half + healed);

	    botNewService.updateUserHpOnlyTx(userName, roomName, effective);
	    u.hpCur = effective;
	    return "";
	}

	private int computeEffectiveHpFromLastAttack(String userName, String roomName, User u,
            int effHpMax, int effRegen) {
	if (u.hpCur >= effHpMax || effRegen <= 0) return Math.min(u.hpCur, effHpMax);
	
	Timestamp baseline = getLastDamageBaseline(userName, roomName);
	if (baseline == null) return Math.min(u.hpCur, effHpMax);
	
	long minutes = Math.max(0, Duration.between(baseline.toInstant(), Instant.now()).toMinutes());
	long ticks   = minutes / 10; // ✅ 10분당 1틱
	if (ticks <= 0) return Math.min(u.hpCur, effHpMax);
	
	long heal = ticks * (long) effRegen;
	long effective = (long) u.hpCur + heal;
	return (int) Math.min(effective, (long) effHpMax);
	}

	public String guideSetTargetMessage() {
		List<Monster> monsters = botNewService.selectAllMonsters();
		StringBuilder sb = new StringBuilder();
		sb.append("공격 타겟이 없습니다. 먼저 타겟을 설정해주세요.").append(NL)
		  .append("예) /공격타겟 1   또는   /공격타겟 토끼").append(NL).append(NL)
		  .append("선택 가능한 몬스터 목록").append(NL);
		for (Monster m : monsters) sb.append(m.monNo).append(" : ").append(m.monName).append(NL);
		return sb.toString();
	}

	private CooldownCheck checkCooldown(String userName, String roomName, String param1) {
		if ("test".equals(param1)) return CooldownCheck.ok();
	    Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
	    if (last == null) return CooldownCheck.ok();
	    long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
	    if (sec >= COOLDOWN_SECONDS) return CooldownCheck.ok();
	    long remainSec = COOLDOWN_SECONDS - sec;
	    return CooldownCheck.blockSeconds(remainSec);
	}

	private String buildBelowHalfMsg(String userName, String roomName, User u, String param1) {
	    // 테스트 모드는 무시
	    if ("test".equals(param1)) return null;

	    int regenWaitMin = minutesUntilReach30(u, userName, roomName);

	    CooldownCheck cd = checkCooldown(userName, roomName, param1);

	    long remainMin = cd.remainSeconds / 60;
	    long remainSec = cd.remainSeconds % 60;

	    int waitMin = Math.max(regenWaitMin, cd.remainMinutes);

	    // 체력 충분 + 쿨타임 OK → 통과
	    if (waitMin <= 0) return null;

	    return userName + "님, 약 " + waitMin + "분 후 공격 가능" + NL
	         + "(최대체력의 30%까지 회복 필요 " + regenWaitMin + "분, "
	         + "쿨타임 " + remainMin + "분 " + remainSec + "초)" + NL
	         + "현재 체력: " + u.hpCur + " / " + u.hpMax + "  |  10분당 회복 +" + u.hpRegen;
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
			int reducedAtk = (int) Math.round(c.atkDmg * 0.5);
			int minDef = Math.max(1, (int) Math.floor(m.monAtk * 0.5));
			int maxDef = m.monAtk;
			int defPower = ThreadLocalRandom.current().nextInt(minDef, maxDef + 1);
			if (defPower >= reducedAtk) {
				c.atkDmg = 0; c.monDmg = 0; c.patternMsg = name + "이(가) 공격을 완전 방어했습니다!";
			} else {
				c.atkDmg = reducedAtk; c.monDmg = 0;
				int blocked = reducedAtk - defPower;
				c.patternMsg = name + "이(가) 방어합니다!(" + defPower + " 방어, " + blocked + " 피해)";
			}
			break;
		case 4: c.monDmg = (int) Math.round(m.monAtk * 2.0); c.patternMsg = name + "의 필살기! (피해 " + c.monDmg + ")"; break;
		default: c.monDmg = 0; c.patternMsg = name + "의 알 수 없는 행동… (피해 0)";
		}
		return c;
	}
	
	
	private String renderMarketListCompactWithOwned(List<HashMap<String,Object>> items) {
	    if (items == null || items.isEmpty()) return "판매중인 아이템이 없습니다.";
	    StringBuilder sb = new StringBuilder();
	    for (HashMap<String,Object> it : items) {
	        int itemId   = safeInt(it.get("ITEM_ID"));
	        String name  = String.valueOf(it.get("ITEM_NAME"));
	        int price    = safeInt(it.get("ITEM_SELL_PRICE"));
	        int atkMin   = safeInt(it.get("ATK_MIN"));
	        int atkMax   = safeInt(it.get("ATK_MAX"));
	        int atkCri   = safeInt(it.get("ATK_CRI"));
	        int hpRegen  = safeInt(it.get("HP_REGEN"));
	        int hpMax    = safeInt(it.get("HP_MAX"));
	        String owned = String.valueOf(it.get("OWNED_YN"));

	        sb.append(itemId).append(" ").append(name).append(" (")
	          .append(price).append("sp");

	        boolean first = true;
	        if (atkMin != 0 || atkMax != 0) { sb.append(", +ATK ").append(atkMin).append("~").append(atkMax); first=false; }
	        if (atkCri != 0)  { sb.append(first?", ":" , ").append("+CRI ").append(atkCri).append("%"); first=false; }
	        if (hpRegen != 0) { sb.append(first?", ":" , ").append("+REGEN ").append(hpRegen).append(" /10m"); first=false; }
	        if (hpMax != 0)   { sb.append(first?", ":" , ").append("+HP ").append(hpMax); }

	        sb.append(")");
	        if ("Y".equalsIgnoreCase(owned)) sb.append("[보유중]");
	        sb.append(NL);
	    }
	    return sb.toString();
	}

	private int safeInt(Object v) {
	    try { return v == null ? 0 : Integer.parseInt(String.valueOf(v)); }
	    catch (Exception e) { return 0; }
	}

	private int minutesUntilReach30(User u, String userName, String roomName) {
		int threshold = (int) Math.ceil(u.hpMax * 0.3);
		if (u.hpCur >= threshold)
			return 0;

		Timestamp baseline = getLastDamageBaseline(userName, roomName);
		if (baseline == null)
			return 0;

		long minutesPassed = Math.max(0, Duration.between(baseline.toInstant(), Instant.now()).toMinutes());
		long minutesToNextTick = (10 - (minutesPassed % 10)) % 10;
		int regenPerTick = Math.max(0, u.hpRegen);
		if (regenPerTick <= 0)
			return Integer.MAX_VALUE;

		int hpNeeded = Math.max(0, threshold - u.hpCur);
		int ticksNeeded = (int) Math.ceil(hpNeeded / (double) regenPerTick);
		return (int) (minutesToNextTick + Math.max(0, ticksNeeded - 1) * 10);
	}

	/** 🍀 Lucky: 처치시에만 EXP×3, 드랍코드 '3' = 빛나는(판매불가) */
	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky) {
	    Resolve r = new Resolve();
	    r.killed = willKill;
	    r.lucky  = lucky;

	    int baseKillExp = (int)Math.round(
	        m.monExp * Math.max(0.1, 1.0 - Math.max(0, u.lv - m.monNo) * 0.2)
	    );

	    if (willKill) r.gainExp = lucky ? baseKillExp * 3 : baseKillExp;
	    else          r.gainExp = 1;

	    if (lucky && willKill) {
	        r.dropCode = "3";         // 빛나는(판매불가)
	        return r;
	    }
	    boolean drop = willKill && ThreadLocalRandom.current().nextDouble(0, 100) < 30.0;
	    r.dropCode = drop ? "1" : "0"; // '1' = 일반드랍(판매가능)
	    return r;
	}

	/** HP/EXP/LV + 로그 저장 */
	private LevelUpResult persist(String userName, String roomName, User u, Monster m, Flags f, AttackCalc c, Resolve res) {
		u.hpCur = Math.max(0, u.hpCur - c.monDmg);
		LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);

		botNewService.updateUserAfterBattleTx(
		   userName, roomName,
		   u.lv, u.expCur, u.expNext, u.hpCur, u.hpMax,
		   u.atkMin, u.atkMax, u.critRate, u.hpRegen
		);
	    int deathYn = (u.hpCur == 0 && c.monDmg > 0) ? 1 : 0;

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
				.setLuckyYn(res.lucky ? 1 : 0);

	    int dropAsInt = "3".equals(res.dropCode) ? 3 : ("1".equals(res.dropCode) ? 1 : 0);
	    log.setDropYn(dropAsInt);
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
	                sb.append("✨ 드랍 획득: 빛나는 ").append(dropName).append(NL);
	            } else {
	                sb.append("✨ 드랍 획득: ").append(dropName).append(NL);
	            }
	        }
	    }

	    // EXP
	    sb.append("✨ EXP+").append(res.gainExp)
	      .append(" , EXP: ").append(u.expCur).append(" / ").append(u.expNext).append(NL);

	    return sb.toString();
	}

	/* ===== utils ===== */

	private String trimDouble(double v) {
	    String s = String.valueOf(v);
	    if (s.endsWith(".0")) return s.substring(0, s.length()-2);
	    return s;
	}

	private int minutesToHalf(User u) { return minutesToHalf(u, u.hpCur); }
	private int minutesToHalf(User u, int currentHp) {
	    int threshold = (int) Math.ceil(u.hpMax * 0.5);
	    if (currentHp >= threshold) return 0;
	    if (u.hpRegen <= 0) return Integer.MAX_VALUE;

	    int need = threshold - currentHp;                 // 필요한 HP
	    int ticksNeeded = (need + u.hpRegen - 1) / u.hpRegen; // ⌈need / regen⌉
	    return ticksNeeded * 10;                          // ✅ 틱(10분) → 분
	}

	private static class Flags { boolean atkCrit; int monPattern; }
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
		public int critDelta; public int hpRegenDelta;
	}

	private LevelUpResult applyExpAndLevelUp(User u, int gainedExp) {
		LevelUpResult r = new LevelUpResult();
		r.gainedExp = Math.max(0, gainedExp);
		r.beforeLv = u.lv; r.beforeExpCur = u.expCur;

		int lv = u.lv, expCur = u.expCur + r.gainedExp, expNext = u.expNext;
		int hpMax = u.hpMax, atkMin = u.atkMin, atkMax = u.atkMax, crit = u.critRate, regen = u.hpRegen;

		int hpDelta = 0, atkMinDelta = 0, atkMaxDelta = 0, critDelta = 0, regenDelta = 0, upCount = 0;

		while (expCur >= expNext) {
			expCur -= expNext; lv++; upCount++;
			expNext = calcNextExp(lv, expNext);
			hpMax += 10;  hpDelta     += 10;
			atkMin += 1;  atkMinDelta += 1;
			atkMax += 3;  atkMaxDelta += 3;
			crit   += 2;  critDelta   += 2;
			if (lv % 3 == 0) { regen++; regenDelta++; }
		}

		u.lv = lv; u.expCur = expCur; u.expNext = expNext;
		u.hpMax = hpMax; u.atkMin = atkMin; u.atkMax = atkMax;
		u.critRate = crit; u.hpRegen  = regen;

		r.afterLv = lv; r.afterExpCur = expCur; r.afterExpNext = expNext; r.levelUpCount = upCount;
		r.hpMaxDelta = hpDelta; r.atkMinDelta = atkMinDelta; r.atkMaxDelta = atkMaxDelta;
		r.critDelta = critDelta; r.hpRegenDelta = regenDelta;
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
	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged != null) return damaged;
	    // 폴백: 과거 데이터/초기 유저를 위해 기존 공격시각도 고려
	    Timestamp any = botNewService.selectLastAttackTime(userName, roomName);
	    return any;
	}
	
	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int parseIntSafe(String s) {
	    try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
	}
}
