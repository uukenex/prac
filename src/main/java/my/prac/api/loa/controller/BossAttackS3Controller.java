package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.User;
import my.prac.core.game.dto.UserBattleContext;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.SP;
import my.prac.core.prjbot.service.BotS3Service;

/**
 * [시즌3] 헬모드 전용 보스 컨트롤러
 *
 * 진입 경로:
 *   A) BossAttackController.monsterAttack → targetMon==99 분기 → attackBossS3(map, ctx)
 *      - 쿨타임/HP확정은 monsterAttack 에서 이미 처리됨
 *   B) 직접 호출 → attackBossS3(map)
 *      - 유저 조회, 헬모드 체크, 쿨타임 포함 전체 처리
 *
 * 보스 테이블: TBOT_POINT_NEW_BOSS (전역 공유, ROOM_NAME 없음)
 * 공격 로그  : TBOT_POINT_NEW_BATTLE_LOG (NIGHTMARE_YN='2', TARGET_MON_LV=999)
 * 보상       : 7000번대 미소지자 중 기여도(횟수70%+데미지30%) 가중 랜덤 1명에게 1개 지급
 */
@Controller
public class BossAttackS3Controller {

    private static final String NL          = "♬";
    private static final String ALL_SEE_STR = "===";

    // =========================================================
    // 헬보스 능력치 랜덤 범위 설정 (필요 시 수정)
    // =========================================================
    /** 보스 공격 발동률 (%) */
    static final int BOSS_ATK_RATE_MIN   = 15,  BOSS_ATK_RATE_MAX   = 30;
    /** 보스 공격 데미지: 유저 최대HP의 X% (체력비례 퍼센트 데미지) */
    static final int BOSS_ATK_POWER_MIN  = 10,  BOSS_ATK_POWER_MAX  = 99;
    /** 보스 방어 발동률 (%) */
    static final int BOSS_DEF_RATE_MIN   = 10,  BOSS_DEF_RATE_MAX   = 25;
    /** 보스 방어: 유저 공격의 X% 감소 */
    static final int BOSS_DEF_POWER_MIN  = 10,  BOSS_DEF_POWER_MAX  = 100;
    /** 보스 회피율 (%) */
    static final int BOSS_EVADE_RATE_MIN = 5,   BOSS_EVADE_RATE_MAX = 20;
    /** 보스 치명 저항: 제거 (0 고정) */
    static final int BOSS_CRIT_DEF_MIN   = 0,   BOSS_CRIT_DEF_MAX   = 0;
    /** 보스 최대 HP (raw) */
    static final long BOSS_MAX_HP_MIN    = 50_000L;
    static final long BOSS_MAX_HP_MAX    = 200_000L;

    /** 헬보스 보상 아이템 타입 */
    private static final String HELL_ITEM_TYPE = "BOSS_HELL";

    /** 헬보스 보상 아이템 목록 (BOSS_HELL 타입 기반 동적 로드, 캐시) */
    private volatile List<Integer> hellRewardItemsCache = null;

    private List<Integer> getHellRewardItems() {
        if (hellRewardItemsCache == null || hellRewardItemsCache.isEmpty()) {
            try {
                List<Integer> ids = botNewService.selectItemIdsByType(HELL_ITEM_TYPE);
                if (ids != null && !ids.isEmpty()) hellRewardItemsCache = ids;
            } catch (Exception ignore) {}
        }
        return hellRewardItemsCache != null ? hellRewardItemsCache : Collections.emptyList();
    }

    /* ===== DI ===== */
    @Autowired BossAttackController bossAttackController;
    @Resource(name = "core.prjbot.BotNewService") BotNewService botNewService;
    @Resource(name = "core.prjbot.BotS3Service")  BotS3Service  botS3Service;


    // =========================================================
    // 진입점 A: BossAttackController.monsterAttack 에서 분기
    //   - 쿨타임/HP확정은 monsterAttack 에서 완료된 상태
    // =========================================================
    String attackBossS3(HashMap<String, Object> map, UserBattleContext ctx) {
        User user = ctx.user;
        if (user.nightmareYn != 2) {
            return user.userName + "님," + NL + "헬모드 유저만 도전 가능한 보스입니다.";
        }
        return doBossAttack(map, ctx);
    }


    // =========================================================
    // 진입점 B: 직접 호출 (유저 조회 + 쿨타임 포함)
    // =========================================================
    public String attackBossS3(HashMap<String, Object> map) {
        final String roomName = Objects.toString(map.get("roomName"), "");
        final String userName = Objects.toString(map.get("userName"), "");

        // 1. 유저 조회
        User user;
        try {
            user = botNewService.selectUser(userName, roomName);
        } catch (Exception e) {
            return userName + "님, 유저 정보 조회에 실패했습니다.";
        }
        if (user == null) return userName + "님, 게임 가입이 필요합니다.";

        // 2. 헬모드 체크
        if (user.nightmareYn != 2) {
            return userName + "님," + NL + "헬모드 유저만 도전 가능한 보스입니다.";
        }

        // 3. 유저 전투 컨텍스트 (S2 calcUserBattleContext 재사용)
        map.put("param0", "/ㄱ");
        map.put("param1", "");
        UserBattleContext ctx = bossAttackController.calcUserBattleContext(map);
        if (!ctx.success) return userName + "님, " + ctx.errorMessage;

        return doBossAttack(map, ctx);
    }


    // =========================================================
    // 핵심 보스 공격 로직 (쿨타임/유저 검증 완료 후 호출)
    // =========================================================
    private String doBossAttack(HashMap<String, Object> map, UserBattleContext ctx) {
        final String roomName = Objects.toString(map.get("roomName"), "");
        final String userName = Objects.toString(map.get("userName"), "");
        final User   user     = ctx.user;

        // 보스 정보 조회 (전역, ROOM_NAME 없음)
        HashMap<String, Object> boss;
        long hp, maxHp;
        // try 블록 밖에서 사용해야 하므로 미리 선언
        double curHpNum = 0;
        String curHpExt = "";
        SP curHpSp = null;
        int seq;
        int bossAtkRate, bossAtkPower, bossDefRate, bossDefPower, bossEvadeRate, critDefRate;
        int debuff, debuff1;
        String bossStartDate;
        String hideRule;
        try {
            boss = botS3Service.selectHellBoss();
            if (boss == null || boss.get("CUR_HP") == null) {
                String lastReward = botS3Service.getLastKillRewardMsg();
                if (lastReward != null && !lastReward.isEmpty()) {
                    return "현재 출현한 상급악마가 없습니다." + NL + NL + lastReward;
                }
                return "현재 출현한 상급악마가 없습니다.";
            }
            // HP: CUR_HP(소수 가능) + CUR_HP_EXT 조합 → SP로 raw 계산
            curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
            curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
            curHpSp  = SP.of(curHpNum, curHpExt);
            hp       = SP.toBaseValue(curHpSp);

            double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
            String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
            maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));

            seq           = Integer.parseInt(boss.get("SEQ").toString());
            bossAtkRate   = boss.get("ATK_RATE")     != null ? Integer.parseInt(boss.get("ATK_RATE").toString())     : 20;
            bossAtkPower  = boss.get("ATK_POWER")    != null ? Integer.parseInt(boss.get("ATK_POWER").toString())    : 100;
            bossDefRate   = boss.get("DEF_RATE")     != null ? Integer.parseInt(boss.get("DEF_RATE").toString())     : 20;
            bossDefPower  = boss.get("DEF_POWER")    != null ? Integer.parseInt(boss.get("DEF_POWER").toString())    : 100;
            bossEvadeRate = boss.get("EVADE_RATE")   != null ? Integer.parseInt(boss.get("EVADE_RATE").toString())   : 10;
            critDefRate   = boss.get("CRIT_DEF_RATE")!= null ? Integer.parseInt(boss.get("CRIT_DEF_RATE").toString()): 0;
            debuff        = boss.get("DEBUFF")       != null ? Integer.parseInt(boss.get("DEBUFF").toString())       : 0;
            debuff1       = boss.get("DEBUFF1")      != null ? Integer.parseInt(boss.get("DEBUFF1").toString())      : 0;
            bossStartDate = boss.get("START_DATE")   != null ? boss.get("START_DATE").toString() : "";
            hideRule      = boss.get("HIDE_RULE")    != null ? boss.get("HIDE_RULE").toString()    : "0";
        } catch (Exception e) {
            return "보스 정보를 가져오는데 실패했습니다.";
        }

        // 재생성 쿨타임 체크 (INSERT_DATE가 미래면 아직 등장 전)
        if (!bossStartDate.isEmpty()) {
            try {
                LocalDateTime spawnTime = LocalDateTime.parse(bossStartDate,
                        DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
                if (LocalDateTime.now().isBefore(spawnTime)) {
                    String dispTime = spawnTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
                    String lastReward = botS3Service.getLastKillRewardMsg();
                    String msg = userName + "님," + NL + "상급악마가 재정비 중입니다." + NL + "등장 예정: " + dispTime;
                    if (lastReward != null && !lastReward.isEmpty()) {
                        msg += NL + NL + lastReward;
                    }
                    return msg;
                }
            } catch (Exception ignored) {}
        }

        // 보스 아이템 보유 여부: calcUserBattleContext에서 이미 로드된 ctx.ownedBossItems 재사용
        Set<Integer> ownedBoss = ctx.ownedBossItems;
        boolean hasHeavenItem = ownedBoss.contains(7001); // 7001: 천벌 발동

        Random rand = new Random();

        // 천벌 발동 (5%, 디버프 활성 시 발동 불가)
        boolean heavensPunishment = false;
        String punishMsg = "";
        if (hasHeavenItem && debuff == 0) {
            if (rand.nextInt(100) < 5) {
                heavensPunishment = true;
                punishMsg = "[천벌] 효과! 보스회피/방어를 무시하고 초강력치명타!" + NL;
            }
        }

        // 디버프 상태 처리
        boolean flag_boss_debuff  = debuff  > 0;
        boolean flag_boss_debuff1 = debuff1 > 0;
        boolean debuff1_start = false;
        String debuff1Msg = "";

        if (flag_boss_debuff) {
            bossAtkRate   = 0;
            bossDefRate   = 0;
            bossEvadeRate = 0;
        }
        if (flag_boss_debuff1) {
            critDefRate = Math.max(0, critDefRate - 5);
            debuff1Msg = "[디버프1] 보스 치명저항 감소" + NL;
        }

        // 보스 회피 판정
        boolean isEvade = !heavensPunishment && (Math.random() < bossEvadeRate / 100.0);

        // 데미지 계산
        long damage = 0;
        boolean isCritical = false;
        boolean isSuperCritical = false;
        String dmgMsg = "";
        String bossDefMsg = "";
        String hideMsg = "";
        String windSlashMsg = "";

        if (!isEvade) {
            String atkRangeStr = "(" + (ctx.atkMin / 100) + "~" + (ctx.atkMax / 100) + ") ";
            // 보스 기본 크리율 10% 고정 (S2 크리율 미적용), 초강력치명타는 크리 발동 시 10% 확률
            int totalCritPercent = Math.max(0, 100 - critDefRate);

            // HIDE_RULE: 특정 시간대 치명타 불가 (천벌/디버프 중엔 무시)
            hideMsg = applyHideRule(hideRule, heavensPunishment || flag_boss_debuff);

            double critMultiplier = Math.max(1.0, ctx.critDmg / 100.0);

            if ("궁사".equals(ctx.job)) {
                // [궁사] 연사 로직 (S2와 동일 구조)
                int range = Math.max(0, ctx.atkMax - ctx.atkMin);
                double rangeRatio = ctx.atkMax > 0 ? (double) range / ctx.atkMax : 0.0;
                int hitCount;
                if      (rangeRatio >= 0.50) hitCount = 5;
                else if (rangeRatio >= 0.30) hitCount = 4;
                else if (rangeRatio >= 0.10) hitCount = 3;
                else                         hitCount = 2;
                if (ownedBoss.contains(7003)) hitCount = Math.min(hitCount + 1, 6); // [7003] 연사수 +1

                int remainCrit = Math.max(0, heavensPunishment ? 100 : totalCritPercent);
                double perHitRate = hitCount > 1 ? Math.min(80.0, (double) remainCrit / (hitCount - 1)) : 0.0;

                long totalMultiDmg = 0;
                boolean allCrit = true;
                StringBuilder multiMsg = new StringBuilder();
                multiMsg.append("궁사의 연사 발동! ").append(hitCount).append("연사").append(NL);

                for (int i = 1; i <= hitCount; i++) {
                    int shotAtk = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
                    if (shotAtk < 1) shotAtk = 1;
                    shotAtk = (int) Math.round(shotAtk * 0.7);
                    boolean shotCrit = (i == 1) || (!hideMsg.isEmpty() ? false : ThreadLocalRandom.current().nextInt(0, 101) <= perHitRate);
                    long shotDmg = shotCrit ? (long)(shotAtk * critMultiplier * 0.70) : shotAtk;
                    totalMultiDmg += shotDmg;
                    if (!shotCrit) allCrit = false;
                    multiMsg.append(i).append("타: ").append(shotDmg);
                    if (shotCrit) multiMsg.append(" (치명!)");
                    multiMsg.append(NL);
                }
                if (allCrit) {
                    long before = totalMultiDmg;
                    totalMultiDmg = (long)(totalMultiDmg * 1.3);
                    multiMsg.append("ALL 치명! ").append(before).append(" → ").append(totalMultiDmg).append(" (+30%)").append(NL);
                } else {
                    multiMsg.append("총합 데미지: ").append(totalMultiDmg).append("!").append(NL);
                }
                isCritical = allCrit;
                damage = totalMultiDmg;
                dmgMsg = atkRangeStr + multiMsg;
            } else {
                // 일반 단타 로직
                int baseAtk = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
                if (baseAtk < 1) baseAtk = 1;

                // 직업 추가 데미지: 상급악마(악마 속성)
                if ("어둠사냥꾼".equals(ctx.job))      baseAtk = (int) Math.round(baseAtk * 2.0);
                else if ("용사".equals(ctx.job)) baseAtk = (int) Math.round(baseAtk * 1.25);

                // [검성] 바람가르기 (기본 6.5% + 7006 보유 시 +15%)
                if ("검성".equals(ctx.job)) {
                    double windProb = 0.065 + (ownedBoss.contains(7006) ? 0.15 : 0.0);
                    if (Math.random() < windProb) {
                        windSlashMsg = "[바람가르기] " + baseAtk + "→";
                        baseAtk = (int) Math.round(baseAtk * 4);
                        windSlashMsg += baseAtk + " (4배 데미지!)" + NL;
                    }
                }

                if (heavensPunishment) {
                    isCritical = true; isSuperCritical = true;
                } else {
                    isCritical = totalCritPercent > 0 && Math.random() < totalCritPercent / 100.0;
                    if (isCritical) isSuperCritical = Math.random() < 0.10;
                }

                if (!hideMsg.isEmpty()) {
                    isCritical = false;
                    isSuperCritical = false;
                }

                if (isSuperCritical) {
                    damage = (long)(baseAtk * 3 * critMultiplier);
                    dmgMsg = "[✨초강력 치명타!!] " + atkRangeStr + baseAtk + " → " + damage;
                } else if (isCritical) {
                    damage = (long)(baseAtk * critMultiplier);
                    dmgMsg = "[✨치명타!] " + atkRangeStr + baseAtk + " → " + damage;
                } else {
                    damage = baseAtk;
                    dmgMsg = atkRangeStr + baseAtk + " 로 공격!";
                }
            }

            // 천벌 디버프 상태이면 데미지 2배
            if (flag_boss_debuff) {
                punishMsg = "[천벌디버프](+" + damage + "), " + (debuff - 1) + "회 남음" + NL;
                damage *= 2;
            }

            // 보스 방어 (천벌/디버프 무시, 방어력: 데미지의 X% 감소)
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                int defPct = ThreadLocalRandom.current().nextInt(BOSS_DEF_POWER_MIN, bossDefPower + 1);
                long defAmt = damage * defPct / 100;
                damage = Math.max(0, damage - defAmt);
                bossDefMsg = "보스가 방어하였습니다! 데미지 " + defPct + "% 감소 (-" + defAmt + ")!" + NL;
            }
        }

        // [도적] 2타 계산 (기본 20% / 7002 보유 시 35% 확률, 회피 시 발동 안함)
        long damage2 = 0;
        boolean thiefHit2 = false;
        String dmgMsg2 = "", bossDefMsg2 = "";
        boolean isCritical2 = false, isSuperCritical2 = false;
        double thiefProb = ownedBoss.contains(7002) ? 0.35 : 0.20;
        if (!isEvade && "도적".equals(ctx.job) && Math.random() < thiefProb) {
            thiefHit2 = true;
            int baseAtk2 = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
            if (baseAtk2 < 1) baseAtk2 = 1;
            String atkRangeStr2 = "(" + (ctx.atkMin / 100) + "~" + (ctx.atkMax / 100) + ") ";
            // 직업 추가 데미지 (2타)
            if ("어둠사냥꾼".equals(ctx.job))      baseAtk2 = (int) Math.round(baseAtk2 * 2.0);
            else if ("용사".equals(ctx.job)) baseAtk2 = (int) Math.round(baseAtk2 * 1.25);
            int totalCrit2 = Math.max(0, 10 - critDefRate); // 보스 기본 크리율 10% 고정
            isCritical2 = totalCrit2 > 0 && Math.random() < totalCrit2 / 100.0;
            if (isCritical2) isSuperCritical2 = Math.random() < 0.10;
            double critMul2 = Math.max(1.0, ctx.critDmg / 100.0);
            if (isSuperCritical2) {
                damage2 = (long)(baseAtk2 * 3 * critMul2);
                dmgMsg2 = "[✨초강력 치명타!!] " + atkRangeStr2 + baseAtk2 + " → " + damage2;
            } else if (isCritical2) {
                damage2 = (long)(baseAtk2 * critMul2);
                dmgMsg2 = "[✨치명타!] " + atkRangeStr2 + baseAtk2 + " → " + damage2;
            } else {
                damage2 = baseAtk2;
                dmgMsg2 = atkRangeStr2 + baseAtk2 + " 로 공격!";
            }
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                int defPct2 = ThreadLocalRandom.current().nextInt(BOSS_DEF_POWER_MIN, bossDefPower + 1);
                long defAmt2 = damage2 * defPct2 / 100;
                damage2 = Math.max(0, damage2 - defAmt2);
                bossDefMsg2 = "보스가 2타 방어! 데미지 " + defPct2 + "% 감소 (-" + defAmt2 + ")!" + NL;
            }
        }
        long totalDamage = damage + damage2;

        // HP 차감 (1차)
        SP newHpSp = isEvade || totalDamage <= 0
                ? SP.of(curHpNum, curHpExt)
                : SP.of(curHpNum, curHpExt).subtract(SP.fromSp(totalDamage));
        boolean isKill = SP.toBaseValue(newHpSp) <= 0;
        long newHp = isKill ? 0 : SP.toBaseValue(newHpSp);

        // 보스 반격 (유저 최대HP × bossAtkPower% 비례 데미지)
        int bossAtkApplied = 0;
        boolean flag_boss_attack = !isKill && !heavensPunishment && Math.random() < bossAtkRate / 100.0;
        if (flag_boss_attack) {
            int atkPct = ThreadLocalRandom.current().nextInt(BOSS_ATK_POWER_MIN, bossAtkPower + 1);
            bossAtkApplied = Math.max(1, (int)(ctx.hpMax * atkPct / 100.0));
        }

        // [7005] 가시갑옷: 받은 피해의 10% 반사 → totalDamage에 추가 후 HP 재계산
        int reflectDmg = 0;
        String reflectMsg = "";
        if (ownedBoss.contains(7005) && bossAtkApplied > 0) {
            reflectDmg = Math.max(1, (int)Math.round(bossAtkApplied * 0.10));
            totalDamage += reflectDmg;
            newHpSp = SP.of(curHpNum, curHpExt).subtract(SP.fromSp(totalDamage));
            isKill = SP.toBaseValue(newHpSp) <= 0;
            newHp = isKill ? 0 : SP.toBaseValue(newHpSp);
            reflectMsg = "[가시갑옷] 반사 +" + reflectDmg + "!" + NL;
        }

        // DB 저장 (HP 업데이트 + 배틀 로그)
        // hp    : 낙관적 잠금 WHERE 절용 → DB 원본값 그대로 (double)
        // newHp : SP 변환 결과 소수값 (예: 2.99832)
        // newHpExt: SP 변환 결과 단위 (예: "b"), 없으면 null
        double newHpDbVal = isKill ? 0.0 : newHpSp.getValue();
        String newHpDbExt = isKill || newHpSp.getUnit().isEmpty() ? null : newHpSp.getUnit();
        map.put("hp",       curHpNum);
        map.put("newHp",    newHpDbVal);
        map.put("newHpExt", newHpDbExt);
        map.put("seq",          seq);
        map.put("endYn",        isKill ? "1" : "0");
        map.put("lv",           user.lv);
        map.put("atkDmg",       totalDamage);
        map.put("monDmg",       bossAtkApplied);
        map.put("atkCritYn",    isCritical ? "1" : "0");
        map.put("killYn",       isKill ? "1" : "0");
        map.put("job",          ctx.job);
        if (heavensPunishment)                   map.put("heavensPunishment", 1);
        if (flag_boss_debuff)                    map.put("useDebuff", 1);
        if (debuff1_start)                       map.put("debuff1_start", 1);
        if (flag_boss_debuff1 && !debuff1_start) map.put("useDebuff1", 1);

        try {
            botS3Service.updateHellBossTx(map);
        } catch (Exception e) {
            return "저장 중 오류가 발생했습니다.";
        }

        // 공격 SP 보상: 준 데미지 × 1000 raw SP (100 데미지 → 10a)
        String spRewardMsg = "";
        if (!isEvade && damage > 0) {
            try {
                SP spReward = SP.fromSp(totalDamage * 10000L);
                HashMap<String, Object> pr = new HashMap<>();
                pr.put("userName", userName);
                pr.put("roomName", roomName);
                pr.put("score",    spReward.getValue());
                pr.put("scoreExt", spReward.getUnit());
                pr.put("cmd",      "BOSS_HELL_ATK");
                botNewService.insertPointRank(pr);
                spRewardMsg = " 획득 SP: " + spReward + NL;
            } catch (Exception e) {
                // SP 지급 실패는 무시
            }
        }

        // 헬보스 업적 체크 (공격 업적, 클리어 참여 업적)
        String hellAchvMsg = grantHellBossAchievements(userName, roomName, isKill, bossStartDate);

        // 처치 보상 + 보스 재생성
        String killMsg = "";
        if (isKill) {
            killMsg = calcHellBossReward(roomName, bossStartDate, maxHp);
            botS3Service.saveLastKillMsg(killMsg); // 대기화면 표시용 캐시
            respawnHellBoss(bossStartDate);
        }

        // 결과 메시지
        StringBuilder msg = new StringBuilder();
        msg.append(userName).append("님이 [상급악마]를 공격했습니다!").append(NL);

        if (!isEvade) {
            msg.append("▶ 입힌 데미지: ").append(damage).append(NL);
            msg.append(dmgMsg).append(NL);
            if (!windSlashMsg.isEmpty()) msg.append(windSlashMsg);
            if (thiefHit2) {
                msg.append("⚔ 2타 데미지: ").append(damage2).append(NL);
                msg.append(dmgMsg2).append(NL);
                if (!bossDefMsg2.isEmpty()) msg.append(bossDefMsg2);
            }
            if (!hideMsg.isEmpty())     msg.append(hideMsg);
            if (!reflectMsg.isEmpty())  msg.append(reflectMsg);
            if (!spRewardMsg.isEmpty()) msg.append(spRewardMsg);
            if (!hellAchvMsg.isEmpty()) msg.append(hellAchvMsg);
            if (!punishMsg.isEmpty())   msg.append(punishMsg);
            if (!debuff1Msg.isEmpty())  msg.append(debuff1Msg);
            if (!bossDefMsg.isEmpty())  msg.append(bossDefMsg);
        } else {
            msg.append("보스가 공격을 회피했습니다! 데미지 0!").append(NL);
        }

        if (flag_boss_attack && bossAtkApplied > 0) {
            msg.append("▶ 보스의 반격! 최대HP의 피해! (").append(bossAtkApplied).append(")").append(NL);
            int remainHp = Math.max(0, ctx.hpMax - bossAtkApplied);
            msg.append("  └ 남은체력: ").append(remainHp).append("/").append(ctx.hpMax).append(NL);
        }

        msg.append(NL);
        if (isKill) {
            msg.append("✨상급악마를 처치했습니다!").append(NL).append(killMsg);
            msg.append(NL).append("새로운 상급악마가 출현했습니다!").append(NL);
        } else {
            String curHpDisp = SP.fromSp(newHp).toString();
            String maxHpDisp = SP.fromSp(maxHp).toString();
            double hpPct = maxHp > 0 ? (newHp * 100.0) / maxHp : 0;
            msg.append("보스 체력: ").append(curHpDisp).append("/").append(maxHpDisp)
               .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);
        }

        return msg.toString().trim();
    }


    // =========================================================
    // 보스 재생성: 처치 후 랜덤 능력치로 새 보스 INSERT
    // =========================================================
    private static final String[] HIDE_RULES = {"아침", "점심", "저녁", "새벽"};
    private static final DateTimeFormatter SPAWN_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 보스 재생성 — 참여 인원수에 따라 쿨타임을 동적 산정
     *   6명 이하 → 18시간(1080분), 30명 이상 → 6시간(360분)
     *   수식: cooldownMin = max(360, min(1080, 1080 - (n-6) * 30))
     */
    private void respawnHellBoss(String bossStartDate) {
        try {
            Random rand = new Random();

            // 참여 인원수 조회 (쿨타임 산정)
            int participantCount = 6; // 기본값 (조회 실패 시)
            try {
                HashMap<String, Object> q = new HashMap<>();
                q.put("bossStartDate", bossStartDate);
                List<HashMap<String, Object>> participants = botS3Service.selectHellTop3Contributors(q);
                if (participants != null && !participants.isEmpty()) participantCount = participants.size();
            } catch (Exception ignored) {}
            long cooldownMin = Math.max(360, Math.min(1080, 1080 - (long)(participantCount - 6) * 30));

            // 최근 공격 평균 데미지 기반으로 120~150회 분량 HP 산정
            long rawHp;
            try {
                Long avgDmg = botS3Service.selectRecentHellAvgDmg();
                if (avgDmg != null && avgDmg > 0) {
                    int hitTarget = 120 + rand.nextInt(31); // 120~150 랜덤
                    rawHp = avgDmg * hitTarget;
                } else {
                    // 데이터 없을 때 기본값
                    rawHp = BOSS_MAX_HP_MIN + (long)(rand.nextDouble() * (BOSS_MAX_HP_MAX - BOSS_MAX_HP_MIN + 1));
                }
            } catch (Exception e) {
                rawHp = BOSS_MAX_HP_MIN + (long)(rand.nextDouble() * (BOSS_MAX_HP_MAX - BOSS_MAX_HP_MIN + 1));
            }

            HashMap<String, Object> bossMap = new HashMap<>();
            bossMap.put("atkRate",     randInt(rand, BOSS_ATK_RATE_MIN,   BOSS_ATK_RATE_MAX));
            bossMap.put("atkPower",    randInt(rand, BOSS_ATK_POWER_MIN,  BOSS_ATK_POWER_MAX));
            bossMap.put("defRate",     randInt(rand, BOSS_DEF_RATE_MIN,   BOSS_DEF_RATE_MAX));
            bossMap.put("defPower",    randInt(rand, BOSS_DEF_POWER_MIN,  BOSS_DEF_POWER_MAX));
            bossMap.put("evadeRate",   randInt(rand, BOSS_EVADE_RATE_MIN, BOSS_EVADE_RATE_MAX));
            bossMap.put("critDefRate", randInt(rand, BOSS_CRIT_DEF_MIN,   BOSS_CRIT_DEF_MAX));
            // 참여 인원수 기반 쿨타임 후 등장
            bossMap.put("startDate",   LocalDateTime.now().plusMinutes(cooldownMin).format(SPAWN_DATE_FMT));
            bossMap.put("hideRule",    HIDE_RULES[rand.nextInt(HIDE_RULES.length)]);
            SP hpSp = SP.fromSp(rawHp);
            bossMap.put("maxHp",    (long) hpSp.getValue());
            bossMap.put("maxHpExt", hpSp.getUnit().isEmpty() ? null : hpSp.getUnit());
            botS3Service.insertHellBoss(bossMap);
        } catch (Exception e) {
            // 재생성 실패는 무시 (처치 결과 메시지에 영향 없음)
        }
    }

    private int randInt(Random rand, int min, int max) {
        return min + rand.nextInt(max - min + 1);
    }

    // =====================================================
    // 헬보스 업적 체크 및 지급
    // =====================================================
    private static final int[] HELL_ATK_THRESHOLDS   = {10, 50, 100, 300, 500, 1000, 2000, 5000};
    private static final int[] HELL_CLEAR_THRESHOLDS = {1, 3, 5, 10, 20, 30, 50};
    /** 1a = 10000 raw SP */
    private static final long HELL_ACHV_REWARD_SP = 10_000L;

    private String grantHellBossAchievements(String userName, String roomName,
                                              boolean isKill, String bossStartDate) {
        StringBuilder sb = new StringBuilder();
        try {
            // 기달성 CMD 로드
            Set<String> achieved = new HashSet<>();
            List<HashMap<String, Object>> achvRows = botNewService.selectAchievementsByUser(userName, roomName);
            if (achvRows != null) {
                for (HashMap<String, Object> r : achvRows) {
                    String cmd = Objects.toString(r.get("CMD"), "");
                    if (cmd.startsWith("ACHV_HELL_ATK_") || cmd.startsWith("ACHV_HELL_CLEAR_")) {
                        achieved.add(cmd);
                    }
                }
            }

            // 공격 횟수 업적
            int atkCount = botNewService.selectHellBossAttackCount(userName);
            for (int th : HELL_ATK_THRESHOLDS) {
                if (atkCount < th) break;
                String cmd = "ACHV_HELL_ATK_" + th;
                if (achieved.contains(cmd)) continue;
                sb.append(grantHellAchv(userName, roomName, cmd, HELL_ACHV_REWARD_SP));
                achieved.add(cmd);
            }

            // 클리어 참여 횟수 업적
            int clearCount = botNewService.selectHellBossClearCount(userName);
            for (int th : HELL_CLEAR_THRESHOLDS) {
                if (clearCount < th) break;
                String cmd = "ACHV_HELL_CLEAR_" + th;
                if (achieved.contains(cmd)) continue;
                sb.append(grantHellAchv(userName, roomName, cmd, HELL_ACHV_REWARD_SP));
                achieved.add(cmd);
            }
        } catch (Exception ignore) {}
        return sb.toString();
    }

    private String grantHellAchv(String userName, String roomName, String cmd, long rewardRawSp) {
        try {
            SP sp = SP.fromSp(rewardRawSp);
            HashMap<String, Object> pr = new HashMap<>();
            pr.put("userName", userName);
            pr.put("roomName", roomName);
            pr.put("score",    sp.getValue());
            pr.put("scoreExt", sp.getUnit());
            pr.put("cmd",      cmd);
            botNewService.insertPointRank(pr);
            return "✨ 헬보스 업적! [" + cmd + "] +" + sp + " 지급" + NL;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HIDE_RULE 시간대 체크 (S1 동일 방식)
     * @param skip 천벌/디버프 활성 시 무시
     * @return 활성 시 메시지, 비활성 시 빈 문자열
     */
    private String applyHideRule(String hideRule, boolean skip) {
        if (skip) return "";
        LocalTime now = LocalTime.now();
        LocalTime s, e;
        String msg;
        switch (hideRule) {
            case "아침":
                s = LocalTime.of(6,  0); e = LocalTime.of(10, 0);
                msg = "보스가 안개에 숨었습니다 (치명타 불가, 06~10시)" + NL; break;
            case "점심":
                s = LocalTime.of(10, 0); e = LocalTime.of(15, 0);
                msg = "보스가 구름에 숨었습니다 (치명타 불가, 10~15시)" + NL; break;
            case "저녁":
                s = LocalTime.of(15, 0); e = LocalTime.of(19, 0);
                msg = "보스가 퇴근길에 숨었습니다 (치명타 불가, 15~19시)" + NL; break;
            default: // 새벽
                s = LocalTime.of(2,  0); e = LocalTime.of(6,  0);
                msg = "보스가 어둠에 숨었습니다 (치명타 불가, 02~06시)" + NL; break;
        }
        return (!now.isBefore(s) && now.isBefore(e)) ? msg : "";
    }


    // =========================================================
    // 보상: 2% 이상 데미지 기여자 중 등확률 랜덤 지급
    //   30% → 아이템 (7000번대 미소지자만)
    //   70% → GP 0.5~1 (7000번대 무관)
    // =========================================================
    private String calcHellBossReward(String roomName, String bossStartDate, long maxHp) {
        Random rand = new Random();

        // ── 전체 참여자 조회 (데미지% 계산용) ──
        List<HashMap<String, Object>> allContributors;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("bossStartDate", bossStartDate);
            allContributors = botS3Service.selectHellTop3Contributors(q);
        } catch (Exception e) { allContributors = new ArrayList<>(); }

        // 전체 데미지 합산
        long totScore = 0;
        for (HashMap<String, Object> row : allContributors)
            totScore += Long.parseLong(row.get("SCORE").toString());

        // 2% 이상 데미지 기여자 목록 (순서 유지)
        List<String> qualified = new ArrayList<>();
        for (HashMap<String, Object> row : allContributors) {
            long score = Long.parseLong(row.get("SCORE").toString());
            double dmgPct = totScore > 0 ? score * 100.0 / totScore : 0;
            if (dmgPct >= 2.0) qualified.add(row.get("USER_NAME").toString());
        }

        StringBuilder msg = new StringBuilder();

        // 보상 타입 결정 (30% 아이템 / 70% GP)
        double diceRoll = rand.nextDouble();
        boolean isItemReward = diceRoll < 0.30;
        msg.append("이번 클리어 보상은 ").append(isItemReward ? "아이템" : "GP").append(" 입니다!").append(NL);

        if (isItemReward) {
            // ────────────────────────────────────────────────────
            // 30% : 2%이상 + 7000번대 미소지자 중 등확률 랜덤 아이템 지급
            // ────────────────────────────────────────────────────
            List<HashMap<String, Object>> eligibleFromDB;
            try {
                HashMap<String, Object> q = new HashMap<>();
                q.put("bossStartDate", bossStartDate);
                eligibleFromDB = botS3Service.selectHellEligibleContributors(q);
            } catch (Exception e) { eligibleFromDB = new ArrayList<>(); }

            Set<String> noItemNames = new HashSet<>();
            for (HashMap<String, Object> row : eligibleFromDB)
                noItemNames.add(row.get("USER_NAME").toString());

            // 2%이상 기여자 중 7000번대 미소지자
            List<String> itemCandidates = new ArrayList<>();
            for (String uName : qualified) {
                if (noItemNames.contains(uName)) itemCandidates.add(uName);
            }

            if (itemCandidates.isEmpty()) {
                msg.append("★ 보상 대상 없음").append(NL);
            } else {
                String winner = itemCandidates.get(rand.nextInt(itemCandidates.size()));
                for (int i = 0; i < itemCandidates.size(); i++) {
                    String uName = itemCandidates.get(i);
                    boolean isWin = uName.equals(winner);
                    msg.append(isWin ? "★" : "  ")
                       .append(i + 1).append(". ").append(uName)
                       .append(isWin ? " ← 당첨!" : "").append(NL);
                }

                // 아이템 지급 (미발견 아이템 우선) + 아이템 정보 맵 구성
                List<Integer> givePool;
                boolean isFirstDiscovery = false;
                HashMap<Integer, String[]> itemInfoMap = new HashMap<>(); // itemId → {name, desc}
                try {
                    List<HashMap<String, Object>> rewardMeta = botNewService.selectHellRewardItemsWithOwnCount();
                    List<Integer> undiscovered = new ArrayList<>();
                    List<Integer> allItems     = new ArrayList<>();
                    for (HashMap<String, Object> row : rewardMeta) {
                        int iid      = Integer.parseInt(row.get("ITEM_ID").toString());
                        long cnt     = Long.parseLong(row.get("GLOBAL_OWN_CNT").toString());
                        String iName = Objects.toString(row.get("ITEM_NAME"), "아이템#" + iid);
                        String iDesc = Objects.toString(row.get("ITEM_DESC"), "");
                        itemInfoMap.put(iid, new String[]{iName, iDesc});
                        allItems.add(iid);
                        if (cnt == 0) undiscovered.add(iid);
                    }
                    if (!undiscovered.isEmpty()) {
                        givePool = undiscovered;
                        isFirstDiscovery = true;
                    } else {
                        givePool = allItems;
                    }
                } catch (Exception e) {
                    givePool = new ArrayList<>(getHellRewardItems());
                }
                if (givePool.isEmpty()) givePool = new ArrayList<>(getHellRewardItems());

                if (givePool.isEmpty()) {
                    // 지급할 아이템 없음 → 1 GP 지급 (아이템과 중복 불가)
                    try {
                        HashMap<String, Object> gpFallback = new HashMap<>();
                        gpFallback.put("userName", winner);
                        gpFallback.put("roomName", roomName);
                        gpFallback.put("score",    1.0);
                        gpFallback.put("cmd",      "BOSS_HELL_NO_ITEM_GP");
                        botNewService.insertGpRecord(gpFallback);
                        msg.append(NL).append("✨ [").append(winner).append("] 지급 아이템 없음 → 1 GP 지급!").append(NL);
                    } catch (Exception e) { /* 지급 실패 무시 */ }
                } else {
                    int giveItemId = givePool.get(rand.nextInt(givePool.size()));
                    String[] info  = itemInfoMap.getOrDefault(giveItemId, new String[]{"아이템#" + giveItemId, ""});
                    String iName   = info[0];
                    String iDesc   = info[1];
                    String displayName = (iDesc == null || iDesc.isEmpty()) ? iName : iName + " (" + iDesc + ")";
                    try {
                        HashMap<String, Object> inv = new HashMap<>();
                        inv.put("userName", winner);
                        inv.put("roomName", roomName);
                        inv.put("itemId",   giveItemId);
                        inv.put("qty",      1);
                        inv.put("gainType", "BOSS_HELL");
                        botNewService.insertInventoryLogTx(inv);
                        msg.append(NL).append(isFirstDiscovery ? "✨최초 발견! " : "✨ 보상: ")
                           .append("[").append(winner).append("] ").append(displayName)
                           .append(isFirstDiscovery ? " (서버 최초 획득!)" : "").append(NL);
                    } catch (Exception e) { /* 지급 실패 무시 */ }
                }
            }

        } else {
            // ────────────────────────────────────────────────────
            // 70% : GP 지급 (7000번대 무관)
            //   - 랜덤 1명: 0.5~1 GP
            //   - 미당첨자 전원: 0.2 GP
            //   - MVP(데미지 1위): +0.2 GP 추가 보너스
            // ────────────────────────────────────────────────────
            if (qualified.isEmpty()) {
                msg.append("GP 지급 대상 없음").append(NL);
            } else {
                double randomGp = 0.5 + rand.nextDouble() * 0.5; // 0.5 ~ 1.0
                String gpWinner = qualified.get(rand.nextInt(qualified.size()));
                // MVP: allContributors는 SCORE DESC 정렬 → 첫 번째가 데미지 1위
                String mvpName = allContributors.isEmpty() ? "" : allContributors.get(0).get("USER_NAME").toString();

                for (int i = 0; i < qualified.size(); i++) {
                    String uName = qualified.get(i);
                    boolean isWin = uName.equals(gpWinner);
                    boolean isMvp = uName.equals(mvpName);
                    msg.append(isWin ? "★" : "  ")
                       .append(i + 1).append(". ").append(uName)
                       .append(isMvp ? " [MVP]" : "")
                       .append(isWin ? " ← 당첨!" : "").append(NL);
                }

                msg.append(NL);
                // GP 지급 (당첨자/MVP는 이름 표시, 나머지 참여자는 일괄 표시)
                int nonWinnerCount = 0;
                for (String uName : qualified) {
                    boolean isWin = uName.equals(gpWinner);
                    boolean isMvp = uName.equals(mvpName);
                    double gp = isWin ? randomGp : 0.2;
                    if (isMvp) gp += 0.2; // MVP 추가 보너스
                    try {
                        HashMap<String, Object> gpMap = new HashMap<>();
                        gpMap.put("userName", uName);
                        gpMap.put("roomName", roomName);
                        gpMap.put("score",    gp);
                        gpMap.put("cmd",      isWin ? "BOSS_HELL_KILL_GP" : "BOSS_HELL_PART_GP");
                        botNewService.insertGpRecord(gpMap);
                    } catch (Exception ignore) {}
                    if (isWin) {
                        msg.append("[").append(uName).append("] ")
                           .append(String.format("+%.2f GP (랜덤당첨)", randomGp));
                        if (isMvp) msg.append(" +0.20 GP (MVP)");
                        msg.append(NL);
                    } else {
                        if (isMvp) {
                            // MVP이면서 비당첨자: MVP 보너스 이름 포함 표시
                            msg.append("[").append(mvpName).append("] +0.20 GP (MVP보너스)").append(NL);
                        } else {
                            nonWinnerCount++;
                        }
                    }
                }
                if (nonWinnerCount > 0) {
                    msg.append("참여자 전체 +0.20 GP").append(NL);
                }
            }
        }

        // 전체 기여도 TOP (데미지% 포함) — 더보기 구분자 이후에 표시
        if (!allContributors.isEmpty()) {
            msg.append(NL).append(ALL_SEE_STR).append(NL);
            msg.append("-- 전체 기여도 TOP --").append(NL);
            for (HashMap<String, Object> row : allContributors) {
                long score = Long.parseLong(row.get("SCORE").toString());
                double dmgPct = totScore > 0 ? score * 100.0 / totScore : 0;
                msg.append(row.get("USER_NAME"))
                   .append(" - ").append(row.get("CNT")).append("회 / ")
                   .append(row.get("SCORE")).append("dmg")
                   .append(String.format(" (%.1f%%)", dmgPct)).append(NL);
            }
        }
        return msg.toString();
    }


    // =========================================================
    // 헬보스 출현 알림 (BossAttackController.ma_buildMessage 에서 호출)
    // =========================================================
    public String getHellBossStatusMsg() {
        try {
            HashMap<String, Object> boss = botS3Service.selectHellBoss();
            if (boss == null || boss.get("CUR_HP") == null) return "";
            String startDateStr = boss.get("START_DATE") != null ? boss.get("START_DATE").toString() : "";
            if (startDateStr.isEmpty()) return "";
            LocalDateTime spawnTime = LocalDateTime.parse(startDateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(spawnTime)) {
                // 미래: 남은 분 계산
                long remainMin = java.time.Duration.between(now, spawnTime).toMinutes();
                if (remainMin >= 55 && remainMin <= 65) return "※상급악마 1시간 후 출현 예정!" + NL;
                if (remainMin >= 8  && remainMin <= 12) return "※상급악마 10분 후 출현 예정!" + NL;
                if (remainMin >= 3  && remainMin <= 7)  return "※상급악마 5분 후 출현 예정!" + NL;
                return "";
            } else {
                // 과거: 이미 출현 중 → 체력% 계산
                double curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
                String curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
                long hp = SP.toBaseValue(SP.of(curHpNum, curHpExt));
                double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
                String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
                long maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));
                int pct = maxHp > 0 ? (int)Math.round(hp * 100.0 / maxHp) : 0;
                return "현재 상급악마 출현! [체력 " + pct + "%]" + NL;
            }
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================
    // 보스 정보 조회: /헬보스정보
    // =========================================================
    public String bossInfo(HashMap<String, Object> map) {
        HashMap<String, Object> boss;
        try {
            boss = botS3Service.selectHellBoss();
        } catch (Exception e) {
            return "보스 정보 조회에 실패했습니다.";
        }
        if (boss == null || boss.get("CUR_HP") == null) {
            String lastReward = botS3Service.getLastKillRewardMsg();
            if (lastReward != null && !lastReward.isEmpty()) {
                return "현재 출현한 상급악마가 없습니다." + NL + NL + lastReward;
            }
            return "현재 출현한 상급악마가 없습니다.";
        }

        double curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
        String curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
        long hp = SP.toBaseValue(SP.of(curHpNum, curHpExt));

        double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
        String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
        long maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));

        String startDate = boss.get("START_DATE") != null ? boss.get("START_DATE").toString() : "";
        double hpPct = maxHp > 0 ? (hp * 100.0) / maxHp : 0;

        List<HashMap<String, Object>> contributors = new ArrayList<>();
        if (!startDate.isEmpty()) {
            try {
                HashMap<String, Object> q = new HashMap<>();
                q.put("bossStartDate", startDate);
                contributors = botS3Service.selectHellTop3Contributors(q);
            } catch (Exception e) {
                // 조회 실패 무시
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("[ 상급악마 정보 ]").append(NL);
        msg.append("체력: ").append(SP.fromSp(hp)).append("/").append(SP.fromSp(maxHp))
           .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);

        if (!contributors.isEmpty()) {
            msg.append(NL).append("-- 참여자 기여도 TOP --").append(NL);
            for (HashMap<String, Object> row : contributors) {
                msg.append(row.get("USER_NAME"))
                   .append(" - ").append(row.get("CNT")).append("회 / 데미지 ").append(row.get("SCORE"))
                   .append(NL);
            }
        } else {
            msg.append("아직 참여자가 없습니다.").append(NL);
        }

        return msg.toString().trim();
    }

}
