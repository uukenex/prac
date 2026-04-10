package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.User;
import my.prac.core.game.dto.UserBattleContext;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.SP;
import my.prac.core.prjbot.service.BotS3Service;
import my.prac.core.prjbot.service.BotService;

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

    private static final String NL = "♬";

    // =========================================================
    // 헬보스 능력치 랜덤 범위 설정 (필요 시 수정)
    // =========================================================
    /** 보스 공격 발동률 (%) */
    static final int BOSS_ATK_RATE_MIN   = 15,  BOSS_ATK_RATE_MAX   = 30;
    /** 보스 공격 데미지 최대값 */
    static final int BOSS_ATK_POWER_MIN  = 80,  BOSS_ATK_POWER_MAX  = 200;
    /** 보스 방어 발동률 (%) */
    static final int BOSS_DEF_RATE_MIN   = 10,  BOSS_DEF_RATE_MAX   = 25;
    /** 보스 방어 데미지 감소 최대값 */
    static final int BOSS_DEF_POWER_MIN  = 50,  BOSS_DEF_POWER_MAX  = 150;
    /** 보스 회피율 (%) */
    static final int BOSS_EVADE_RATE_MIN = 5,   BOSS_EVADE_RATE_MAX = 20;
    /** 보스 치명 저항 (%) */
    static final int BOSS_CRIT_DEF_MIN   = 0,   BOSS_CRIT_DEF_MAX   = 30;
    /** 보스 최대 HP (raw) */
    static final long BOSS_MAX_HP_MIN    = 50_000L;
    static final long BOSS_MAX_HP_MAX    = 200_000L;

    /** 헬보스 보상 아이템 목록 (7000번대, 확정 후 업데이트) */
    private static final List<Integer> HELL_REWARD_ITEMS = Arrays.asList(7001, 7002, 7003);

    /** 천벌 아이템 ID */
    private static final int ITEM_HEAVEN = 7001;

    /* ===== DI ===== */
    @Autowired BossAttackController bossAttackController;
    @Resource(name = "core.prjbot.BotNewService") BotNewService botNewService;
    @Resource(name = "core.prjbot.BotService")    BotService    botService;   // 직접 호출 시 쿨타임용
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

        // 3. 쿨타임 체크 (15초)
        map.put("timeDelay", 15);
        map.put("timeDelayMsg", "");
        String cooldown = botService.selectHourCheck(map);
        if (cooldown != null) {
            return userName + "님," + NL + cooldown + " 이후 재시도 가능합니다.";
        }

        // 4. 유저 전투 컨텍스트 (S2 스탯 기반)
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
                    return "현재 출현한 헬보스가 없습니다." + NL + NL + lastReward;
                }
                return "현재 출현한 헬보스가 없습니다.";
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
                    return userName + "님," + NL + "헬보스가 재정비 중입니다." + NL + "등장 예정: " + dispTime;
                }
            } catch (Exception ignored) {}
        }

        // 천벌 아이템 보유 여부 확인
        boolean hasHeavenItem = false;
        try {
            List<Integer> owned = botNewService.selectInventoryItemsByIds(userName, roomName, Arrays.asList(ITEM_HEAVEN));
            hasHeavenItem = owned != null && owned.contains(ITEM_HEAVEN);
        } catch (Exception e) {
            // 조회 실패 시 미보유 처리
        }

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

        if (!isEvade) {
            int baseAtk = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
            if (baseAtk < 1) baseAtk = 1;
            String atkRangeStr = "(" + (ctx.atkMin / 100) + "~" + (ctx.atkMax / 100) + ") ";

            int totalCritPercent = ctx.crit - critDefRate;

            if (heavensPunishment) {
                isCritical = true; isSuperCritical = true;
            } else {
                isCritical = totalCritPercent > 0 && Math.random() < totalCritPercent / 100.0;
                if (isCritical) isSuperCritical = Math.random() < 0.10;
            }

            // HIDE_RULE: 특정 시간대 치명타 불가 (천벌/디버프 중엔 무시)
            hideMsg = applyHideRule(hideRule, heavensPunishment || flag_boss_debuff);
            if (!hideMsg.isEmpty()) {
                isCritical = false;
                isSuperCritical = false;
            }

            double critMultiplier = Math.max(1.0, ctx.critDmg / 100.0);

            if (isSuperCritical) {
                damage = (long)(baseAtk * 5 * critMultiplier);
                dmgMsg = "[✨초강력 치명타!!] " + atkRangeStr + baseAtk + " → " + damage;
            } else if (isCritical) {
                damage = (long)(baseAtk * critMultiplier);
                dmgMsg = "[✨치명타!] " + atkRangeStr + baseAtk + " → " + damage;
            } else {
                damage = baseAtk;
                dmgMsg = atkRangeStr + baseAtk + " 로 공격!";
            }

            // 천벌 디버프 상태이면 데미지 2배
            if (flag_boss_debuff) {
                punishMsg = "[천벌디버프](+" + damage + "), " + (debuff - 1) + "회 남음" + NL;
                damage *= 2;
            }

            // 보스 방어 (천벌/디버프 무시)
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                int defAmt = ThreadLocalRandom.current().nextInt(1, bossDefPower + 1);
                damage = Math.max(0, damage - defAmt);
                bossDefMsg = "보스가 방어하였습니다! 데미지 " + defAmt + " 상쇄!" + NL;
            }
        }

        // HP 차감: SP.subtract → 단위 자동 변환 (예: 3b - 16.8a = 2.99832b)
        SP newHpSp = isEvade || damage <= 0
                ? SP.of(curHpNum, curHpExt)
                : SP.of(curHpNum, curHpExt).subtract(SP.fromSp(damage));
        boolean isKill = SP.toBaseValue(newHpSp) <= 0;
        long newHp = isKill ? 0 : SP.toBaseValue(newHpSp);

        // 보스 반격
        int bossAtkApplied = 0;
        boolean flag_boss_attack = !isKill && !heavensPunishment && Math.random() < bossAtkRate / 100.0;
        if (flag_boss_attack) {
            bossAtkApplied = Math.max(1, ThreadLocalRandom.current().nextInt(1, bossAtkPower + 1));
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
        map.put("atkDmg",       damage);
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
                SP spReward = SP.fromSp(damage * 1000L);
                HashMap<String, Object> pr = new HashMap<>();
                pr.put("userName", userName);
                pr.put("roomName", roomName);
                pr.put("score",    spReward.getValue());
                pr.put("scoreExt", spReward.getUnit());
                pr.put("cmd",      "BOSS_HELL_ATK");
                botNewService.insertPointRank(pr);
                spRewardMsg = "💰 획득 SP: " + spReward + NL;
            } catch (Exception e) {
                // SP 지급 실패는 무시
            }
        }

        // 처치 보상 + 보스 재생성
        String killMsg = "";
        if (isKill) {
            killMsg = calcHellBossReward(roomName, bossStartDate);
            respawnHellBoss();
        }

        // 결과 메시지
        StringBuilder msg = new StringBuilder();
        msg.append(userName).append("님이 [헬보스]를 공격했습니다!").append(NL);

        if (!isEvade) {
            msg.append("▶ 입힌 데미지: ").append(damage).append(NL);
            msg.append(dmgMsg).append(NL);
            if (!hideMsg.isEmpty())     msg.append(hideMsg);
            if (!spRewardMsg.isEmpty()) msg.append(spRewardMsg);
            if (!punishMsg.isEmpty())   msg.append(punishMsg);
            if (!debuff1Msg.isEmpty())  msg.append(debuff1Msg);
            if (!bossDefMsg.isEmpty())  msg.append(bossDefMsg);
        } else {
            msg.append("보스가 공격을 회피했습니다! 데미지 0!").append(NL);
        }

        if (flag_boss_attack && bossAtkApplied > 0) {
            msg.append("▶ 보스의 반격! ").append(bossAtkApplied).append(" 의 피해!").append(NL);
        }

        msg.append(NL);
        if (isKill) {
            msg.append("✨헬보스를 처치했습니다!").append(NL).append(killMsg);
            msg.append(NL).append("새로운 헬보스가 출현했습니다!").append(NL);
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

    private void respawnHellBoss() {
        try {
            Random rand = new Random();
            long rawHp = BOSS_MAX_HP_MIN + (long)(rand.nextDouble() * (BOSS_MAX_HP_MAX - BOSS_MAX_HP_MIN + 1));

            HashMap<String, Object> bossMap = new HashMap<>();
            bossMap.put("atkRate",     randInt(rand, BOSS_ATK_RATE_MIN,   BOSS_ATK_RATE_MAX));
            bossMap.put("atkPower",    randInt(rand, BOSS_ATK_POWER_MIN,  BOSS_ATK_POWER_MAX));
            bossMap.put("defRate",     randInt(rand, BOSS_DEF_RATE_MIN,   BOSS_DEF_RATE_MAX));
            bossMap.put("defPower",    randInt(rand, BOSS_DEF_POWER_MIN,  BOSS_DEF_POWER_MAX));
            bossMap.put("evadeRate",   randInt(rand, BOSS_EVADE_RATE_MIN, BOSS_EVADE_RATE_MAX));
            bossMap.put("critDefRate", randInt(rand, BOSS_CRIT_DEF_MIN,   BOSS_CRIT_DEF_MAX));
            // 24시간 후 등장
            bossMap.put("startDate",   LocalDateTime.now().plusHours(24).format(SPAWN_DATE_FMT));
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
    // 보상: 7000번대 미소지자 중 기여도 가중 랜덤 1명에게 1개 지급
    // =========================================================
    private String calcHellBossReward(String roomName, String bossStartDate) {
        // 보상 대상: 7000번대 미소지자 중 기여도 상위
        List<HashMap<String, Object>> eligible;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("bossStartDate", bossStartDate);
            eligible = botS3Service.selectHellEligibleContributors(q);
        } catch (Exception e) {
            eligible = new ArrayList<>();
        }

        // 전체 기여도 (표시용)
        List<HashMap<String, Object>> allContributors;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("bossStartDate", bossStartDate);
            allContributors = botS3Service.selectHellTop3Contributors(q);
        } catch (Exception e) {
            allContributors = new ArrayList<>();
        }

        StringBuilder msg = new StringBuilder();

        if (eligible == null || eligible.isEmpty()) {
            msg.append("★ 보상 대상 없음 (모든 참여자가 이미 7000번대 아이템 보유)").append(NL);
        } else {
            // 가중치 산정 (횟수 70% + 데미지 30%)
            int totCnt = Integer.parseInt(eligible.get(0).get("TOT_CNT").toString());
            long totDmg = 0;
            for (HashMap<String, Object> row : eligible) totDmg += Long.parseLong(row.get("SCORE").toString());

            double[] weights = new double[eligible.size()];
            double weightSum = 0;
            for (int i = 0; i < eligible.size(); i++) {
                int cnt    = Integer.parseInt(eligible.get(i).get("CNT").toString());
                long score = Long.parseLong(eligible.get(i).get("SCORE").toString());
                weights[i] = (totCnt > 0 ? (double)cnt / totCnt : 0) * 0.7
                           + (totDmg > 0 ? (double)score / totDmg : 0) * 0.3;
                weightSum += weights[i];
            }

            // 가중 랜덤 선정
            double r = Math.random() * weightSum;
            double cum = 0;
            int winnerIdx = 0;
            for (int i = 0; i < weights.length; i++) {
                cum += weights[i];
                if (r <= cum) { winnerIdx = i; break; }
            }
            String winner = eligible.get(winnerIdx).get("USER_NAME").toString();

            // 아이템 지급 (7000번대 중 랜덤 1개)
            int giveItemId = HELL_REWARD_ITEMS.get(new Random().nextInt(HELL_REWARD_ITEMS.size()));
            try {
                HashMap<String, Object> inv = new HashMap<>();
                inv.put("userName", winner);
                inv.put("roomName", roomName);
                inv.put("itemId",   giveItemId);
                inv.put("qty",      1);
                inv.put("gainType", "BOSS_HELL");
                botNewService.insertInventoryLogTx(inv);
                msg.append("★ 보상: [").append(winner).append("] item#").append(giveItemId).append(NL);
            } catch (Exception e) {
                // 지급 실패 무시
            }
        }

        // 기여도 TOP 표시
        if (!allContributors.isEmpty()) {
            msg.append(NL).append("-- 기여도 TOP --").append(NL);
            for (HashMap<String, Object> row : allContributors) {
                msg.append(row.get("USER_NAME"))
                   .append(" - ").append(row.get("CNT")).append("회 / 데미지 ").append(row.get("SCORE"))
                   .append(NL);
            }
        }
        return msg.toString();
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
                return "현재 출현한 헬보스가 없습니다." + NL + NL + lastReward;
            }
            return "현재 출현한 헬보스가 없습니다.";
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
        msg.append("[ 헬보스 정보 ]").append(NL);
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
