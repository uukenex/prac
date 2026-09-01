package my.prac.core.prjbot.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.service.BotS5Service;
import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 시스템 서비스 구현체 (MVP).
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 *
 * 미구현(TODO): 가챠(동료뽑기/장비뽑기), 상점 구매, 주사위구매, 스탯구매,
 * 자동사냥 정산(현재는 10마리 처치 시 AUTO_HUNT_YN 플래그만 세움),
 * 장비 장착/합성, 직업별 특수효과(전사 도발/마법사 스턴/도적 스틸/궁수 즉사/도사 보호막).
 */
@Service("core.prjbot.BotS5Service")
public class BotS5ServiceImpl implements BotS5Service {

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO dao;

    private static final String NL  = "♬";
    private static final Random RND = new Random();

    // 등급(성급) 베이스 스탯 [HP, ATK, DEF], index0 = ★1
    private static final int[][] GRADE_BASE = {
        { 100, 10, 5 },
        { 130, 13, 7 },
        { 182, 18, 10 },
        { 282, 28, 16 },
        { 494, 49, 28 },
        { 988, 98, 56 },
    };

    // 직업별 배율 [HP, ATK, DEF]
    private static final HashMap<String, double[]> JOB_MULT = new HashMap<String, double[]>() {{
        put("WARRIOR", new double[]{ 1.5, 1.0, 2.0 });
        put("MAGE",    new double[]{ 0.7, 2.0, 0.6 });
        put("ROGUE",   new double[]{ 0.9, 1.4, 1.0 });
        put("ARCHER",  new double[]{ 0.7, 1.8, 0.8 });
        put("PRIEST",  new double[]{ 1.2, 0.6, 1.6 });
    }};

    private static final HashMap<String, String> JOB_NAME = new HashMap<String, String>() {{
        put("WARRIOR", "전사"); put("MAGE", "마법사"); put("ROGUE", "도적");
        put("ARCHER", "궁수");  put("PRIEST", "도사");
    }};

    @Override
    public HashMap<String, Object> selectUserProgress(String userName) {
        return dao.selectUserProgress(userName);
    }

    @Override
    @Transactional
    public void initUser(String userName) {
        HashMap<String, Object> p = new HashMap<>();
        p.put("userName", userName);
        dao.insertUserProgress(p);

        // 스타터 동료: ★1 전사, 파티 1번 슬롯 자동 편성
        int[] stat = calcStat("WARRIOR", 1);
        HashMap<String, Object> c = new HashMap<>();
        c.put("userName", userName);
        c.put("class", "WARRIOR");
        c.put("grade", 1);
        c.put("curHpValue", stat[0]);
        c.put("curHpExt", "");
        c.put("partySlot", 1);
        dao.insertCompanion(c);
    }

    private HashMap<String, Object> getOrInitProgress(String userName) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) {
            initUser(userName);
            p = dao.selectUserProgress(userName);
        }
        return p;
    }

    private int[] calcStat(String job, int grade) {
        int[] base = GRADE_BASE[grade - 1];
        double[] mult = JOB_MULT.get(job);
        int hp  = (int) Math.round(base[0] * mult[0]);
        int atk = (int) Math.round(base[1] * mult[1]);
        int def = (int) Math.round(base[2] * mult[2]);
        return new int[]{ hp, atk, def };
    }

    private int diceMax(String diceGrade) {
        if (diceGrade == null) return 6;
        switch (diceGrade) {
            case "DICE_8":  return 8;
            case "DICE_10": return 10;
            case "DICE_12": return 12;
            case "DICE_20": return 20;
            default:        return 6;
        }
    }

    private int floorBlockBase(int floor) {
        return (floor / 10) * 10;
    }

    private int blockNo(int floor) {
        return (floorBlockBase(floor) / 10) + 1;
    }

    private int intVal(Object o, int def) {
        if (o == null) return def;
        return ((Number) o).intValue();
    }

    private String strVal(Object o, String def) {
        return o == null ? def : o.toString();
    }

    // ================================================================
    // /탑현황
    // ================================================================
    @Override
    public String towerStatus(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        String status = strVal(p.get("STATUS"), "NORMAL");
        PP pp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));

        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님," + NL);
        sb.append("현재 층: ").append(floor);
        sb.append(" (").append(floorKindLabel(floor)).append(")").append(NL);
        sb.append("보유 PP: ").append(pp.format()).append(NL);
        sb.append("상태: ").append(status).append(NL);

        if (floor % 10 >= 1 && floor % 10 <= 8) {
            HashMap<String, Object> fi = dao.selectFloorInfo(floor);
            HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
            int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
            int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);
            sb.append("보드 위치: ").append(curTile).append(" / ").append(tileCount).append(NL);
        }
        sb.append("사용 주사위: ").append(strVal(p.get("DICE_GRADE"), "DICE_6")).append(NL);
        sb.append("누적 처치: ").append(intVal(p.get("TOTAL_KILL_COUNT"), 0)).append("마리");
        return sb.toString();
    }

    private String floorKindLabel(int floor) {
        int m = floor % 10;
        if (m == 0) return "마을";
        if (m == 9) return "보스층";
        return "사냥터";
    }

    // ================================================================
    // /주사위, /ㅈㅅㅇ
    // ================================================================
    @Override
    @Transactional
    public String rollDice(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        String status = strVal(p.get("STATUS"), "NORMAL");

        if ("IN_COMBAT".equals(status)) {
            return resolveCombatTurn(userName, p, floor);
        }

        int m = floor % 10;
        if (m == 0) {
            return userName + "님," + NL + "여기는 마을입니다. /층변경 N 으로 사냥터에 진입하세요. (상점 기능은 준비 중입니다)";
        }
        if (m == 9) {
            // 보스층: 보드 없이 바로 전투 시작
            return startCombat(userName, p, floor, true);
        }

        // 사냥터: 보드 이동
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);
        HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
        int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);

        if (curTile >= tileCount) {
            return userName + "님," + NL + "이미 이 층 보드 끝에 도달했습니다. /층변경 N 으로 다른 층으로 이동하세요.";
        }

        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));
        int roll = RND.nextInt(diceMax) + 1;
        int newTile = Math.min(curTile + roll, tileCount);

        HashMap<String, Object> ufpSave = new HashMap<>();
        ufpSave.put("userName", userName);
        ufpSave.put("floor", floor);
        ufpSave.put("curTile", newTile);
        dao.upsertUserFloorProgress(ufpSave);

        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님," + NL);
        sb.append("🎲 주사위 ").append(roll).append("! ").append(curTile).append(" → ").append(newTile)
          .append(" / ").append(tileCount).append("칸").append(NL);

        // 함정 디버프 턴 감소
        int trapTurnLeft = intVal(p.get("TRAP_TURN_LEFT"), 0);
        if (trapTurnLeft > 0) {
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("trapTurnLeft", trapTurnLeft - 1);
            dao.updateUserProgress(up);
        }

        List<HashMap<String, Object>> tiles = dao.selectTileMaster(floor);
        String tileType = "COMBAT";
        for (HashMap<String, Object> t : tiles) {
            if (intVal(t.get("TILE_NO"), -1) == newTile) {
                tileType = strVal(t.get("TILE_TYPE"), "COMBAT");
                break;
            }
        }

        switch (tileType) {
            case "COMBAT":
                sb.append(startCombat(userName, p, floor, false));
                break;
            case "PP": {
                HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
                PP reward = mon == null ? PP.of(1, "")
                        : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
                addPp(userName, p, reward);
                sb.append("💰 PP 칸! ").append(reward.format()).append(" PP 획득!");
                break;
            }
            case "TRAP": {
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                up.put("trapTurnLeft", 3);
                dao.updateUserProgress(up);
                sb.append("🕳️ 함정에 걸렸다! 3턴간 전투력이 약화됩니다.");
                break;
            }
            case "SHOP":
                sb.append("🎁 비밀상점을 발견했다! (가챠 기능은 준비 중입니다)");
                break;
            case "SPECIAL":
                sb.append(handleSpecialTile(userName));
                break;
            default:
                sb.append("...아무 일도 일어나지 않았다.");
        }
        return sb.toString();
    }

    private String handleSpecialTile(String userName) {
        dao.upsertSpecialVisitIncrement(userName);
        HashMap<String, Object> v = dao.selectUserSpecialVisit(userName);
        int cnt = v == null ? 1 : intVal(v.get("VISIT_COUNT"), 1);
        StringBuilder sb = new StringBuilder("✨ 수상한 기운이 감돈다... (특수칸 누적 방문 ").append(cnt).append("회)");
        int[] thresholds = { 10, 50, 100 };
        int[] achIds = { 17, 18, 19 };
        for (int i = 0; i < thresholds.length; i++) {
            if (cnt == thresholds[i]) {
                grantAchievement(userName, achIds[i]);
                sb.append(NL).append("🏆 히든 업적 달성!");
            }
        }
        return sb.toString();
    }

    private void addPp(String userName, HashMap<String, Object> p, PP amount) {
        PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        PP result = cur.add(amount);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("ppValue", result.getValue());
        up.put("ppExt", result.getUnit());
        dao.updateUserProgress(up);
        p.put("PP_VALUE", result.getValue());
        p.put("PP_EXT", result.getUnit());
    }

    private String startCombat(String userName, HashMap<String, Object> p, int floor, boolean boss) {
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), boss ? "Y" : "N");
        if (mon == null) {
            return "몬스터 정보가 없습니다 (관리자 문의).";
        }
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("status", "IN_COMBAT");
        up.put("curMonsterId", intVal(mon.get("MONSTER_ID"), 0));
        up.put("curMonsterHpValue", ((Number) mon.get("HP_VALUE")).doubleValue());
        up.put("curMonsterHpExt", strVal(mon.get("HP_EXT"), ""));
        dao.updateUserProgress(up);

        return (boss ? "👹 보스 " : "⚔️ ") + strVal(mon.get("MONSTER_NAME"), "몬스터") + " 등장! (HP "
                + PP.of(((Number) mon.get("HP_VALUE")).doubleValue(), strVal(mon.get("HP_EXT"), "")).format()
                + ") " + NL + "전투를 시작하려면 다시 /주사위 를 입력하세요!";
    }

    private String resolveCombatTurn(String userName, HashMap<String, Object> p, int floor) {
        int monsterId = intVal(p.get("CUR_MONSTER_ID"), 0);
        HashMap<String, Object> mon = findMonsterById(floor, monsterId);
        if (mon == null) {
            // 상태 꼬임 방지: 전투 상태 초기화
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            dao.updateUserProgress(up);
            return "전투 정보를 찾을 수 없어 전투를 종료합니다.";
        }

        PP monsterHp = PP.of(((Number) p.get("CUR_MONSTER_HP_VALUE")).doubleValue(), strVal(p.get("CUR_MONSTER_HP_EXT"), ""));
        int monsterDef = intVal(mon.get("DEF_VALUE"), 0);
        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));

        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : companions) {
            if (c.get("PARTY_SLOT") != null) party.add(c);
        }
        if (party.isEmpty()) {
            return "파티에 편성된 동료가 없습니다. /파티편성 으로 동료를 편성하세요.";
        }

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 ──
        long totalDamage = 0;
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) <= 0) continue; // 전투불가
            int[] stat = calcStat(strVal(c.get("CLASS"), "WARRIOR"), intVal(c.get("GRADE"), 1));
            int roll = RND.nextInt(diceMax) + 1;
            int dmg = Math.max(1, stat[1] * roll - monsterDef);
            totalDamage += dmg;
            sb.append(JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "동료"))
              .append(" 공격! 🎲").append(roll).append(" → ").append(dmg).append(" 데미지").append(NL);
        }

        PP monsterHpAfter = monsterHp.subtract(PP.fromPP(totalDamage));
        boolean monsterDead = PP.toBaseValue(monsterHpAfter) <= 0;

        if (monsterDead) {
            PP reward = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
            boolean isBoss = "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
            int killCountCur = intVal(p.get("KILL_COUNT_CUR"), 0) + 1;
            int totalKill = intVal(p.get("TOTAL_KILL_COUNT"), 0) + 1;

            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            up.put("totalKillCount", totalKill);

            sb.append(strVal(mon.get("MONSTER_NAME"), "몬스터")).append(" 처치! 🎉").append(NL);

            if (isBoss) {
                int nextFloor = floorBlockBase(floor) + 10;
                up.put("curFloor", nextFloor);
                up.put("unlockedBlock", floorBlockBase(floor) + 10);
                sb.append("👑 보스 격파! ").append(nextFloor).append("층 마을로 이동합니다.").append(NL);
                grantAchievement(userName, 7);
            } else {
                up.put("killCountCur", killCountCur >= 10 ? 0 : killCountCur);
                if (killCountCur >= 10) {
                    up.put("autoHuntYn", "Y");
                    sb.append("🔥 이 층에서 10마리 처치! 자동사냥 모드 ON (정산 로직은 TODO)").append(NL);
                }
            }
            dao.updateUserProgress(up);
            addPp(userName, p, reward);
            checkKillAchievements(userName, totalKill);
            sb.append(reward.format()).append(" PP 획득!");
            healPartyFull(party); // 전투 종료 시 전원 부활 + Full HP
            return sb.toString();
        }

        // 몬스터 생존 → 반격
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curMonsterHpValue", monsterHpAfter.getValue());
        up.put("curMonsterHpExt", monsterHpAfter.getUnit());
        dao.updateUserProgress(up);
        sb.append(strVal(mon.get("MONSTER_NAME"), "몬스터")).append(" 남은 HP: ").append(monsterHpAfter.format()).append(NL);

        List<HashMap<String, Object>> alive = new ArrayList<>();
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) > 0) alive.add(c);
        }

        if (alive.isEmpty()) {
            HashMap<String, Object> defeatUp = new HashMap<>();
            defeatUp.put("userName", userName);
            defeatUp.put("status", "NORMAL");
            defeatUp.put("clearMonster", true);
            dao.updateUserProgress(defeatUp);
            healPartyFull(party);
            sb.append("💀 파티 전멸... 전투에 패배했습니다. 동료들이 마을에서 회복 후 다시 도전하세요.");
            return sb.toString();
        }

        HashMap<String, Object> target = alive.get(RND.nextInt(alive.size()));
        int[] tStat = calcStat(strVal(target.get("CLASS"), "WARRIOR"), intVal(target.get("GRADE"), 1));
        int monsterAtk = intVal(mon.get("ATK_VALUE"), 0);
        int roll = RND.nextInt(diceMax) + 1;
        int dmgToParty = Math.max(1, monsterAtk * roll - tStat[2]);

        PP targetHp = PP.of(((Number) target.get("CUR_HP_VALUE")).doubleValue(), strVal(target.get("CUR_HP_EXT"), ""));
        PP targetHpAfter = targetHp.subtract(PP.fromPP(dmgToParty));
        if (PP.toBaseValue(targetHpAfter) < 0) targetHpAfter = PP.fromPP(0);

        HashMap<String, Object> cUp = new HashMap<>();
        cUp.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        cUp.put("curHpValue", targetHpAfter.getValue());
        cUp.put("curHpExt", targetHpAfter.getUnit());
        dao.updateCompanionHp(cUp);

        sb.append(strVal(mon.get("MONSTER_NAME"), "몬스터")).append(" 반격! 🎲").append(roll).append(" → ")
          .append(JOB_NAME.getOrDefault(strVal(target.get("CLASS"), "WARRIOR"), "동료")).append("에게 ")
          .append(dmgToParty).append(" 피해 (남은 HP ").append(targetHpAfter.format()).append(")");
        if (PP.toBaseValue(targetHpAfter) <= 0) sb.append(" — 전투불가!");

        return sb.toString();
    }

    private void healPartyFull(List<HashMap<String, Object>> party) {
        for (HashMap<String, Object> c : party) {
            int[] stat = calcStat(strVal(c.get("CLASS"), "WARRIOR"), intVal(c.get("GRADE"), 1));
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", intVal(c.get("COMPANION_ID"), 0));
            up.put("curHpValue", (double) stat[0]);
            up.put("curHpExt", "");
            dao.updateCompanionHp(up);
        }
    }

    private HashMap<String, Object> findMonsterById(int floor, int monsterId) {
        // 일반/보스 둘 다 조회해서 ID로 매칭 (구조가 단순해서 blockNo 기준 재조회)
        HashMap<String, Object> normal = dao.selectMonster(blockNo(floor), "N");
        if (normal != null && intVal(normal.get("MONSTER_ID"), -1) == monsterId) return normal;
        HashMap<String, Object> boss = dao.selectMonster(blockNo(floor), "Y");
        if (boss != null && intVal(boss.get("MONSTER_ID"), -1) == monsterId) return boss;
        return null;
    }

    private void checkKillAchievements(String userName, int totalKill) {
        if (totalKill == 100) grantAchievement(userName, 8);
        if (totalKill == 1000) grantAchievement(userName, 9);
    }

    private void grantAchievement(String userName, int achId) {
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(userName);
        for (HashMap<String, Object> a : mine) {
            if (intVal(a.get("ACH_ID"), -1) == achId) return; // 이미 달성
        }
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", userName);
        m.put("achId", achId);
        dao.insertUserAch(m);
    }

    // ================================================================
    // /층변경 N
    // ================================================================
    @Override
    @Transactional
    public String changeFloor(String userName, int n) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (n < 0 || n > 9) {
            return "층변경은 0~9 범위만 가능합니다. (같은 10층 구간 내 이동)";
        }
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 층 이동을 할 수 없습니다.";
        }
        int target = floorBlockBase(floor) + n;

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        dao.updateUserProgress(up);

        return userName + "님," + NL + floor + "층 → " + target + "층(" + floorKindLabel(target) + ")으로 이동했습니다.";
    }

    // ================================================================
    // /파티편성
    // ================================================================
    @Override
    public String partyList(String userName) {
        getOrInitProgress(userName);
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (companions.isEmpty()) {
            return "보유한 동료가 없습니다.";
        }
        StringBuilder sb = new StringBuilder(userName).append("님의 동료 목록," + NL);
        int idx = 1;
        for (HashMap<String, Object> c : companions) {
            String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "?");
            int grade = intVal(c.get("GRADE"), 1);
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            Object slot = c.get("PARTY_SLOT");
            sb.append(idx++).append(". ").append(job).append(" ★").append(grade)
              .append(" HP ").append(hp.format())
              .append(slot != null ? " [파티 " + slot + "번]" : " [대기]")
              .append(NL);
        }
        sb.append("/파티편성 N 으로 편성/해제 (최대 3명)");
        return sb.toString();
    }

    @Override
    @Transactional
    public String partyToggle(String userName, int idx) {
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (idx < 1 || idx > companions.size()) {
            return "잘못된 번호입니다. /파티편성 으로 목록을 확인하세요.";
        }
        HashMap<String, Object> target = companions.get(idx - 1);
        boolean inParty = target.get("PARTY_SLOT") != null;

        if (inParty) {
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", intVal(target.get("COMPANION_ID"), 0));
            up.put("partySlot", null);
            dao.updateCompanionPartySlot(up);
            return "파티에서 해제했습니다.";
        }

        int used = 0;
        for (HashMap<String, Object> c : companions) if (c.get("PARTY_SLOT") != null) used++;
        if (used >= 3) {
            return "파티는 최대 3명까지 편성 가능합니다. 다른 동료를 먼저 해제하세요.";
        }
        // 비어있는 가장 작은 슬롯 번호 사용
        boolean[] usedSlot = new boolean[4];
        for (HashMap<String, Object> c : companions) {
            Object s = c.get("PARTY_SLOT");
            if (s != null) usedSlot[((Number) s).intValue()] = true;
        }
        int slot = 1;
        while (slot <= 3 && usedSlot[slot]) slot++;

        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        up.put("partySlot", slot);
        dao.updateCompanionPartySlot(up);

        if (used + 1 == 3) grantAchievement(userName, 15);
        return "파티 " + slot + "번 슬롯에 편성했습니다.";
    }

    // ================================================================
    // /업적
    // ================================================================
    @Override
    public String achievements(String userName) {
        List<HashMap<String, Object>> all = dao.selectAchievementList();
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(userName);
        HashMap<Integer, Boolean> cleared = new HashMap<>();
        for (HashMap<String, Object> m : mine) cleared.put(intVal(m.get("ACH_ID"), -1), true);

        StringBuilder sb = new StringBuilder(userName).append("님의 업적 (")
                .append(mine.size()).append("/").append(all.size()).append(")," + NL);
        for (HashMap<String, Object> a : all) {
            int id = intVal(a.get("ACH_ID"), -1);
            boolean hidden = "Y".equals(strVal(a.get("HIDDEN_YN"), "N"));
            boolean done = cleared.containsKey(id);
            if (hidden && !done) continue; // 히든 미달성은 숨김
            sb.append(done ? "✅ " : "⬜ ").append(strVal(a.get("ACH_NAME"), "")).append(" - ")
              .append(strVal(a.get("ACH_DESC"), "")).append(NL);
        }
        return sb.toString();
    }
}
