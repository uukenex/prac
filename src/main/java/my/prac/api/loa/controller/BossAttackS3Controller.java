package my.prac.api.loa.controller;

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
import my.prac.core.prjbot.service.BotS3Service;
import my.prac.core.prjbot.service.BotService;

/**
 * [시즌3] 헬모드 전용 보스 컨트롤러
 *
 * 특징:
 *   - 헬모드(nightmareYn=2) 유저만 도전 가능
 *   - 유저 스탯은 S2 BossAttackController.calcUserBattleContext 기반
 *   - 보스 체력은 전역 공유 (TBOT_POINT_NEW_BOSS, ROOM_NAME 없음)
 *   - 공격 로그는 TBOT_POINT_NEW_BATTLE_LOG (S2 형식, NIGHTMARE_YN='2')
 *   - 처치 시 7000번대 아이템 중 1개를 기여도 1위에게 지급
 */
@Controller
public class BossAttackS3Controller {

    private static final String NL = "♬";

    /** 7000번대 헬보스 보상 아이템 목록 (확정 후 업데이트) */
    private static final List<Integer> HELL_REWARD_ITEMS = Arrays.asList(7001, 7002, 7003);

    /** 천벌 아이템 ID */
    private static final int ITEM_HEAVEN = 7001;

    /* ===== DI ===== */
    @Autowired BossAttackController bossAttackController;
    @Resource(name = "core.prjbot.BotNewService") BotNewService botNewService;
    @Resource(name = "core.prjbot.BotService")    BotService    botService;
    @Resource(name = "core.prjbot.BotS3Service")  BotS3Service  botS3Service;


    // =========================================================
    // 진입점: /공격 → 이 메서드 호출 (TARGET_MON == "BOSS_HELL")
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
        if (user == null) {
            return userName + "님, 게임 가입이 필요합니다.";
        }

        // 2. 헬모드 체크
        if (user.nightmareYn != 2) {
            return userName + "님," + NL + "헬모드 유저만 도전 가능한 보스입니다.";
        }

        // 3. 보스 정보 조회 (전역 공유, ROOM_NAME 없음)
        HashMap<String, Object> boss;
        int hp, orgHp, seq;
        int bossAtkRate, bossAtkPower, bossDefRate, bossDefPower, bossEvadeRate, critDefRate;
        int debuff, debuff1;
        try {
            boss = botS3Service.selectHellBoss();
            if (boss == null || boss.get("HP") == null) {
                String lastReward = botS3Service.getLastKillRewardMsg();
                if (lastReward != null && !lastReward.isEmpty()) {
                    return "현재 출현한 헬보스가 없습니다." + NL + NL + lastReward;
                }
                return "현재 출현한 헬보스가 없습니다.";
            }
            hp           = Integer.parseInt(boss.get("HP").toString());
            orgHp        = Integer.parseInt(boss.get("ORG_HP").toString());
            seq          = Integer.parseInt(boss.get("SEQ").toString());
            bossAtkRate  = boss.get("ATK_RATE")    != null ? Integer.parseInt(boss.get("ATK_RATE").toString())    : 20;
            bossAtkPower = boss.get("ATK_POWER")   != null ? Integer.parseInt(boss.get("ATK_POWER").toString())   : 100;
            bossDefRate  = boss.get("DEF_RATE")    != null ? Integer.parseInt(boss.get("DEF_RATE").toString())    : 20;
            bossDefPower = boss.get("DEF_POWER")   != null ? Integer.parseInt(boss.get("DEF_POWER").toString())   : 100;
            bossEvadeRate= boss.get("EVADE_RATE")  != null ? Integer.parseInt(boss.get("EVADE_RATE").toString())  : 10;
            critDefRate  = boss.get("CRIT_DEF_RATE")!= null? Integer.parseInt(boss.get("CRIT_DEF_RATE").toString()): 0;
            debuff       = boss.get("DEBUFF")      != null ? Integer.parseInt(boss.get("DEBUFF").toString())      : 0;
            debuff1      = boss.get("DEBUFF1")     != null ? Integer.parseInt(boss.get("DEBUFF1").toString())     : 0;
        } catch (Exception e) {
            return "보스 정보를 가져오는데 실패했습니다.";
        }

        // 4. 유저 전투 컨텍스트 (S2 스탯 기반)
        map.put("param0", "/ㄱ");
        map.put("param1", "");
        UserBattleContext ctx = bossAttackController.calcUserBattleContext(map);
        if (!ctx.success) {
            return userName + "님, " + ctx.errorMessage;
        }

        // 5. 쿨타임 체크 (15초)
        map.put("timeDelay", 15);
        map.put("timeDelayMsg", "");
        String cooldown = botService.selectHourCheck(map);
        if (cooldown != null) {
            return userName + "님," + NL + cooldown + " 이후 재시도 가능합니다.";
        }

        // 6. 천벌 아이템 보유 여부 확인
        boolean hasHeavenItem = false;
        try {
            List<Integer> owned = botNewService.selectInventoryItemsByIds(userName, roomName, Arrays.asList(ITEM_HEAVEN));
            hasHeavenItem = owned != null && owned.contains(ITEM_HEAVEN);
        } catch (Exception e) {
            // 조회 실패 시 미보유 처리
        }

        Random rand = new Random();

        // 7. 천벌 발동 체크 (5%, 디버프 상태이면 발동 불가)
        boolean heavensPunishment = false;
        String punishMsg = "";
        if (hasHeavenItem && debuff == 0) {
            if (rand.nextInt(100) < 5) {
                heavensPunishment = true;
                punishMsg = "[천벌] 효과! 보스회피/방어를 무시하고 초강력치명타!" + NL;
            }
        }

        // 8. 디버프 상태이면 보스 스탯 무력화 (S1 로직 동일)
        boolean flag_boss_debuff  = debuff  > 0;
        boolean flag_boss_debuff1 = debuff1 > 0;
        boolean debuff1_start = false;
        String debuff1Msg = "";

        if (flag_boss_debuff) {
            bossAtkRate   = 0;
            bossDefRate   = 0;
            bossEvadeRate = 0;
        }

        // debuff1 (저주받은인형 류): 치명저항 감소
        if (flag_boss_debuff1) {
            critDefRate = Math.max(0, critDefRate - 5);
            debuff1Msg = "[디버프1] 보스 치명저항 감소 적용" + NL;
        }

        // 9. 보스 회피 판정
        boolean isEvade = !heavensPunishment && (Math.random() < bossEvadeRate / 100.0);

        // 10. 데미지 계산
        int damage = 0;
        boolean isCritical = false;
        boolean isSuperCritical = false;
        String dmgMsg = "";
        String bossDefMsg = "";
        int appliedDefPower = 0;

        if (!isEvade) {
            int atkRange = Math.max(1, ctx.atkMax - ctx.atkMin + 1);
            int baseAtk  = ctx.atkMin + rand.nextInt(atkRange);
            String atkRangeStr = "(" + ctx.atkMin + "~" + ctx.atkMax + ") ";

            // 치명타 확률 계산
            int totalCritPercent = ctx.crit;
            totalCritPercent -= critDefRate;

            if (heavensPunishment) {
                isCritical      = true;
                isSuperCritical = true;
            } else {
                isCritical = totalCritPercent > 0 && Math.random() < totalCritPercent / 100.0;
                if (isCritical) {
                    isSuperCritical = Math.random() < 0.10;
                }
            }

            // 디버프 상태이면 데미지 2배 (S1 천벌 디버프 동일)
            double critMultiplier = Math.max(1.0, ctx.critDmg / 100.0);

            if (isSuperCritical) {
                damage = (int)(baseAtk * 5 * critMultiplier);
                dmgMsg = "[✨초강력 치명타!!] " + atkRangeStr + baseAtk + " → " + damage;
            } else if (isCritical) {
                damage = (int)(baseAtk * critMultiplier);
                dmgMsg = "[✨치명타!] " + atkRangeStr + baseAtk + " → " + damage;
            } else {
                damage = baseAtk;
                dmgMsg = atkRangeStr + baseAtk + " 로 공격!";
            }

            // 디버프(천벌) 상태이면 데미지 2배
            if (flag_boss_debuff) {
                punishMsg = "[천벌디버프](+" + damage + "), " + (debuff - 1) + "회 남음" + NL;
                damage = damage * 2;
            }

            // 보스 방어 적용 (천벌이면 무시)
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                appliedDefPower = ThreadLocalRandom.current().nextInt(1, bossDefPower + 1);
                damage = Math.max(0, damage - appliedDefPower);
                bossDefMsg = "보스가 방어하였습니다! 데미지 " + appliedDefPower + " 상쇄!" + NL;
            }
        }

        // 11. 보스 HP 차감 및 킬 판정
        int newHp  = Math.max(0, hp - damage);
        boolean isKill = (newHp <= 0);
        int score  = Math.max(1, damage / 4);

        // 12. DB 저장 (HP 업데이트 + 배틀 로그)
        int bossAtkApplied = 0;
        boolean flag_boss_attack = !isKill && !heavensPunishment && Math.random() < bossAtkRate / 100.0;
        if (flag_boss_attack) {
            bossAtkApplied = Math.max(1, ThreadLocalRandom.current().nextInt(1, bossAtkPower + 1));
        }

        map.put("hp",            hp);
        map.put("newHp",         newHp);
        map.put("seq",           seq);
        map.put("damage",        damage);
        map.put("score",         score);
        map.put("endYn",         isKill ? "1" : "0");
        // 배틀 로그용
        map.put("lv",            user.lv);
        map.put("atkDmg",        damage);
        map.put("monDmg",        bossAtkApplied);
        map.put("atkCritYn",     isCritical ? "1" : "0");
        map.put("killYn",        isKill ? "1" : "0");
        map.put("job",           ctx.job);
        // 디버프 처리
        if (heavensPunishment)  map.put("heavensPunishment", 1);
        if (flag_boss_debuff)   map.put("useDebuff",  1);
        if (debuff1_start)      map.put("debuff1_start", 1);
        if (flag_boss_debuff1 && !debuff1_start) map.put("useDebuff1", 1);

        try {
            botS3Service.updateHellBossTx(map);
        } catch (Exception e) {
            return "저장 중 오류가 발생했습니다.";
        }

        // 13. 보스 처치 처리
        String killMsg = "";
        if (isKill) {
            killMsg = calcHellBossReward(roomName, seq);
        }

        // 14. 보스 반격 메시지
        String bossAtkMsg = "";
        if (flag_boss_attack && bossAtkApplied > 0) {
            bossAtkMsg = "▶ 보스의 반격! " + bossAtkApplied + " 의 피해!" + NL;
        }

        // 15. 결과 메시지
        StringBuilder msg = new StringBuilder();
        msg.append(userName).append("님이 [헬보스]를 공격했습니다!").append(NL);

        if (!isEvade) {
            msg.append("▶ 입힌 데미지: ").append(damage).append(NL);
            msg.append(dmgMsg).append(NL);
            if (!punishMsg.isEmpty())  msg.append(punishMsg);
            if (!debuff1Msg.isEmpty()) msg.append(debuff1Msg);
            if (!bossDefMsg.isEmpty()) msg.append(bossDefMsg);
        } else {
            msg.append("보스가 공격을 회피했습니다! 데미지 0!").append(NL);
        }

        if (!bossAtkMsg.isEmpty()) msg.append(bossAtkMsg);

        msg.append(NL);
        if (isKill) {
            msg.append("✨헬보스를 처치했습니다!").append(NL);
            msg.append(killMsg);
        } else {
            double hpPct = orgHp > 0 ? (newHp * 100.0) / orgHp : 0;
            msg.append("보스 체력: ").append(newHp).append("/").append(orgHp)
               .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);
        }

        return msg.toString().trim();
    }


    // =========================================================
    // 헬보스 처치 보상
    // =========================================================
    /**
     * 보스 처치 시 기여도 가중 랜덤으로 1명 선정 후 7000번대 아이템 1개 지급.
     * 기여도: 공격 횟수 70% + 데미지 30%
     */
    private String calcHellBossReward(String roomName, int seq) {
        List<HashMap<String, Object>> contributors;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("seq", seq);
            contributors = botS3Service.selectHellTop3Contributors(q);
        } catch (Exception e) {
            return "";
        }
        if (contributors == null || contributors.isEmpty()) return "";

        // 총 공격 횟수 · 데미지 집계
        int totCnt = Integer.parseInt(contributors.get(0).get("TOT_CNT").toString());
        int totDmg = 0;
        for (HashMap<String, Object> row : contributors) {
            totDmg += Integer.parseInt(row.get("SCORE").toString());
        }

        // 기여도 가중치 (횟수 70% + 데미지 30%)
        double[] weights = new double[contributors.size()];
        double weightSum = 0;
        for (int i = 0; i < contributors.size(); i++) {
            HashMap<String, Object> row = contributors.get(i);
            int cnt   = Integer.parseInt(row.get("CNT").toString());
            int score = Integer.parseInt(row.get("SCORE").toString());
            double cntRatio   = totCnt > 0 ? (double) cnt   / totCnt : 0;
            double scoreRatio = totDmg > 0 ? (double) score / totDmg : 0;
            weights[i] = cntRatio * 0.7 + scoreRatio * 0.3;
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
        String winner = contributors.get(winnerIdx).get("USER_NAME").toString();

        // 미보유 7000번대 아이템 추출
        List<Integer> ownedIds;
        try {
            ownedIds = botNewService.selectInventoryItemsByIds(winner, roomName, HELL_REWARD_ITEMS);
        } catch (Exception e) {
            ownedIds = new ArrayList<>();
        }

        List<Integer> available = new ArrayList<>();
        for (int id : HELL_REWARD_ITEMS) {
            if (ownedIds == null || !ownedIds.contains(id)) available.add(id);
        }

        StringBuilder msg = new StringBuilder();

        if (!available.isEmpty()) {
            int giveItemId = available.get(new Random().nextInt(available.size()));
            try {
                HashMap<String, Object> inv = new HashMap<>();
                inv.put("userName", winner);
                inv.put("roomName", roomName);
                inv.put("itemId",   giveItemId);
                inv.put("qty",      1);
                inv.put("gainType", "BOSS_HELL");
                botNewService.insertInventoryLogTx(inv);
                msg.append("★ 보상 아이템 획득: [").append(winner)
                   .append("] item#").append(giveItemId).append(NL);
            } catch (Exception e) {
                // 지급 실패 무시
            }
        } else {
            msg.append("★ [").append(winner).append("] 이미 모든 헬보스 보상 보유 중!").append(NL);
        }

        // 기여도 TOP 표시
        msg.append(NL).append("-- 기여도 TOP --").append(NL);
        for (HashMap<String, Object> row : contributors) {
            String name  = row.get("USER_NAME").toString();
            int    cnt   = Integer.parseInt(row.get("CNT").toString());
            int    dmg   = Integer.parseInt(row.get("SCORE").toString());
            msg.append(name)
               .append(" - ").append(cnt).append("회 / 데미지 ").append(dmg)
               .append(NL);
        }

        return msg.toString();
    }


    // =========================================================
    // 보스 정보 조회
    // =========================================================
    /** /헬보스정보 — 현재 활성 헬보스 HP + 기여도 TOP 표시 */
    public String bossInfo(HashMap<String, Object> map) {
        HashMap<String, Object> boss;
        try {
            boss = botS3Service.selectHellBoss();
        } catch (Exception e) {
            return "보스 정보 조회에 실패했습니다.";
        }
        if (boss == null || boss.get("HP") == null) {
            String lastReward = botS3Service.getLastKillRewardMsg();
            if (lastReward != null && !lastReward.isEmpty()) {
                return "현재 출현한 헬보스가 없습니다." + NL + NL + lastReward;
            }
            return "현재 출현한 헬보스가 없습니다.";
        }

        int hp    = Integer.parseInt(boss.get("HP").toString());
        int orgHp = Integer.parseInt(boss.get("ORG_HP").toString());
        int seq   = Integer.parseInt(boss.get("SEQ").toString());
        double hpPct = orgHp > 0 ? (hp * 100.0) / orgHp : 0;

        List<HashMap<String, Object>> contributors;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("seq", seq);
            contributors = botS3Service.selectHellTop3Contributors(q);
        } catch (Exception e) {
            contributors = new ArrayList<>();
        }

        StringBuilder msg = new StringBuilder();
        msg.append("[ 헬보스 정보 ]").append(NL);
        msg.append("체력: ").append(hp).append("/").append(orgHp)
           .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);

        if (!contributors.isEmpty()) {
            msg.append(NL).append("-- 참여자 기여도 TOP --").append(NL);
            for (HashMap<String, Object> row : contributors) {
                String name  = row.get("USER_NAME").toString();
                int    cnt   = Integer.parseInt(row.get("CNT").toString());
                int    score = Integer.parseInt(row.get("SCORE").toString());
                msg.append(name)
                   .append(" - ").append(cnt).append("회 / 데미지 ").append(score)
                   .append(NL);
            }
        } else {
            msg.append("아직 참여자가 없습니다.").append(NL);
        }

        return msg.toString().trim();
    }

}
