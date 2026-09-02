package my.prac.core.prjbot.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.annotation.Resource;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.service.BotS5Service;
import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 시스템 서비스 구현체.
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 */
@Service("core.prjbot.BotS5Service")
public class BotS5ServiceImpl implements BotS5Service {

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO dao;

    private static final String NL  = "♬";
    private static final Random RND = new Random();

    private static final String[] JOB_KEYS = { "WARRIOR", "MAGE", "ROGUE", "ARCHER", "PRIEST" };

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

    // 동료 뽑을 때 무작위로 붙는 일본식 이름 (성급/직업과 무관, 순전히 개성 부여용)
    private static final String[] NAME_POOL = {
        "유키", "하루토", "사쿠라", "렌", "아오이", "리쿠", "나츠키", "히나",
        "소라", "유토", "카이토", "미유", "아카리", "유이", "료", "리코",
        "소우타", "나나", "유즈키", "카나데", "츠바사", "메이", "슌", "이츠키",
    };

    // 장비 등급별 보너스 [투구고정,투구%, 무기고정,무기%, 갑옷고정,갑옷%], index0=★1
    private static final double[][] EQUIP_BONUS = {
        { 30, 0.05,   5, 0.05,   3, 0.05 },
        { 45, 0.07,   8, 0.07,   5, 0.07 },
        { 75, 0.10,  13, 0.10,   8, 0.10 },
        { 150, 0.15, 25, 0.15,  15, 0.15 },
        { 350, 0.22, 60, 0.22,  35, 0.22 },
        { 800, 0.35, 150, 0.35, 80, 0.35 },
    };

    // 주사위 해금 계단 [코드, 해금 UNLOCKED_BLOCK]
    private static final String[] DICE_NAMES = { "DICE_6", "DICE_8", "DICE_10", "DICE_12", "DICE_20" };
    private static final int[]    DICE_UNLOCK = { 0, 10, 30, 50, 70 };

    // 칸 종류 표시(아이콘+이름)
    private static final HashMap<String, String> TILE_LABEL = new HashMap<String, String>() {{
        put("COMBAT", "⚔️ 전투");  put("PP", "💰 PP");     put("SHOP", "🎁 상점");
        put("TRAP",   "🕳️ 함정");  put("SPECIAL", "✨ 특수"); put("STAIRS", "🪜 계단");
    }};

    // 이동(비전투) 쿨타임 3분, 전투 쿨타임 30초
    private static final long MOVE_COOLDOWN_SEC   = 180;
    private static final long COMBAT_COOLDOWN_SEC = 30;

    @Override
    public HashMap<String, Object> selectUserProgress(String userName) {
        return dao.selectUserProgress(userName);
    }

    @Override
    @Transactional
    public void initUser(String userName) {
        // 계정(진행상태)만 생성. 동료 지급은 유저가 /동료뽑기 를 직접 눌러야 진행되는
        // 튜토리얼 흐름으로 처리한다 (rollDice 참고).
        HashMap<String, Object> p = new HashMap<>();
        p.put("userName", userName);
        dao.insertUserProgress(p);
    }

    private HashMap<String, Object> getOrInitProgress(String userName) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) {
            initUser(userName);
            p = dao.selectUserProgress(userName);
        }
        settleAutoHunt(userName, p);
        return p;
    }

    // ================================================================
    // 스탯 계산
    // ================================================================
    private int[] calcBaseStat(String job, int grade) {
        int[] base = GRADE_BASE[grade - 1];
        double[] mult = JOB_MULT.get(job);
        int hp  = (int) Math.round(base[0] * mult[0]);
        int atk = (int) Math.round(base[1] * mult[1]);
        int def = (int) Math.round(base[2] * mult[2]);
        return new int[]{ hp, atk, def };
    }

    /** 등급+직업 베이스 스탯에 장비/스탯구매 보너스를 반영한 최종 전투 스탯. [hp, atk, def, minDmgFloor] */
    private int[] computeEffectiveStat(String job, int grade, List<HashMap<String, Object>> equips, HashMap<String, Object> userStat) {
        int[] base = calcBaseStat(job, grade);
        double hp = base[0], atk = base[1], def = base[2];

        if (equips != null) {
            for (HashMap<String, Object> e : equips) {
                int eg = intVal(e.get("GRADE"), 1);
                double[] b = EQUIP_BONUS[eg - 1];
                String part = strVal(e.get("PART"), "");
                if ("HELMET".equals(part)) hp += b[0] + base[0] * b[1];
                else if ("WEAPON".equals(part)) atk += b[2] + base[1] * b[3];
                else if ("ARMOR".equals(part)) def += b[4] + base[2] * b[5];
            }
        }

        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int atkMinLv = userStat == null ? 0 : intVal(userStat.get("ATK_MIN_LV"), 0);
        int hpLv     = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        atk *= (1 + 0.03 * atkMaxLv);
        hp  *= (1 + 0.03 * hpLv);
        int minDmgFloor = atkMinLv * 2;

        return new int[]{ (int) Math.round(hp), (int) Math.round(atk), (int) Math.round(def), minDmgFloor };
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
        sb.append("자동사냥: ").append("Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N")) ? "ON" : "OFF").append(NL);
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
        boolean brandNew = dao.selectUserProgress(userName) == null;
        HashMap<String, Object> p = getOrInitProgress(userName);

        if (brandNew) {
            // 계정이 없던 유저의 첫 /주사위 → 계정만 생성. 진행은 튜토리얼 순서대로 유도.
            return "┌─────────────────┐" + NL
                    + "  🗼 시즌5 탑 등반기" + NL
                    + "└─────────────────┘" + NL
                    + "계정을 생성했습니다! 현재 0층 마을이에요." + NL
                    + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기 1)";
        }

        String status = strVal(p.get("STATUS"), "NORMAL");
        String cooldownMsg = checkDiceCooldown(p, status);
        if (cooldownMsg != null) return cooldownMsg;

        String result = rollDiceInternal(userName, p, status);
        touchDiceCooldown(userName);
        return result;
    }

    /** LAST_DICE_ACTION_DATE 기준 쿨타임 검사. 아직 남았으면 안내 메시지, 통과면 null. */
    private String checkDiceCooldown(HashMap<String, Object> p, String status) {
        java.util.Date last = (java.util.Date) p.get("LAST_DICE_ACTION_DATE");
        if (last == null) return null;
        long cooldownSec = "IN_COMBAT".equals(status) ? COMBAT_COOLDOWN_SEC : MOVE_COOLDOWN_SEC;
        long elapsedSec = (System.currentTimeMillis() - last.getTime()) / 1000;
        if (elapsedSec >= cooldownSec) return null;
        long remain = cooldownSec - elapsedSec;
        return "⏳ 아직 쿨타임입니다! " + remain + "초 후 다시 시도해주세요." + NL
                + ("IN_COMBAT".equals(status) ? "(전투 쿨타임 30초)" : "(이동 쿨타임 3분)");
    }

    private void touchDiceCooldown(String userName) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("touchDiceCooldown", true);
        dao.updateUserProgress(up);
    }

    private String rollDiceInternal(String userName, HashMap<String, Object> p, String status) {
        int floor = intVal(p.get("CUR_FLOOR"), 0);

        if ("IN_COMBAT".equals(status)) {
            return resolveCombatTurn(userName, p, floor);
        }

        int m = floor % 10;
        if (m == 0) {
            if (floor == 0) {
                // 튜토리얼 진행 중(0층 마을) — 다음 단계를 순서대로 안내
                int companionCount = dao.countUserCompanions(userName);
                int partyCount = countPartySize(userName);
                if (companionCount == 0) {
                    return userName + "님," + NL + "🏘️ 0층 마을 — 아직 동료가 없습니다." + NL
                            + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기 1)";
                }
                if (partyCount == 0) {
                    return userName + "님," + NL + "🏘️ 0층 마을 — 동료는 있지만 파티가 비어있습니다." + NL
                            + "👉 /파티편성 N 으로 동료를 파티에 편성하세요! (/파티편성 목록은 /파티편성 으로 확인)";
                }
                return userName + "님," + NL + "🏘️ 0층 마을 — 파티 준비 완료!" + NL
                        + "👉 층이동 명령어로 1층 가세요! (/층변경 1)";
            }
            return userName + "님," + NL + "🏘️ 여기는 마을입니다. /탑상점 으로 상점을 이용하거나 /층변경 N 으로 사냥터에 진입하세요.";
        }
        if (m == 9) {
            return startCombat(userName, p, floor, true);
        }

        // ── 사냥터 보드: 끝 없이 순환하는 루프. 계단(STAIRS) 칸에 도착해야 다음 층으로 이동. ──
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);
        HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
        int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);

        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));
        int roll = RND.nextInt(diceMax) + 1;
        int newTile = ((curTile + roll - 1) % tileCount) + 1;

        HashMap<String, Object> ufpSave = new HashMap<>();
        ufpSave.put("userName", userName);
        ufpSave.put("floor", floor);
        ufpSave.put("curTile", newTile);
        dao.upsertUserFloorProgress(ufpSave);

        int priorVisits = dao.selectTileVisitCount(userName, floor, newTile);
        dao.insertTileVisit(userName, floor, newTile);
        int visited = dao.countTileVisits(userName, floor);

        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님," + NL);
        sb.append("🎲 주사위 ").append(roll).append("! ").append(curTile).append(" → ").append(newTile).append("번 칸")
          .append(NL).append("🗺️ 탐사 현황: ").append(visited).append("/").append(tileCount).append("칸 발견");
        if (visited >= tileCount && grantAchievement(userName, 25)) {
            sb.append(NL).append("🏆 이 층을 전부 탐험했습니다! [탐험왕] 업적 달성!");
        }
        sb.append(NL);

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
        if (!"COMBAT".equals(tileType)) {
            sb.append(TILE_LABEL.getOrDefault(tileType, tileType)).append(" 칸!").append(NL);
        }

        // 히든(특수)/아이템획득(상점) 칸은 첫 방문에만 보상을 주고, 재방문(2회차부터)은 몬스터 전투로 전환.
        // 이걸 의미있게 만드는 짝: 마을로 돌아가면 그 층의 방문기록이 초기화되므로(changeFloor 참고)
        // "한 원정 안에서 같은 칸을 우려먹기"만 막고, 다음 원정에서 다시 새로 발견하는 건 자유.
        boolean revisitOverride = priorVisits >= 1 && ("SPECIAL".equals(tileType) || "SHOP".equals(tileType));
        if (revisitOverride) {
            sb.append("(어라, 낯익은 자리인데...? 몬스터가 튀어나왔다!)").append(NL);
        }
        String effectiveType = revisitOverride ? "COMBAT" : tileType;

        switch (effectiveType) {
            case "COMBAT":
                sb.append(startCombat(userName, p, floor, false));
                break;
            case "PP": {
                HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
                PP reward = mon == null ? PP.of(1, "")
                        : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
                addPp(userName, p, reward);
                sb.append(reward.format()).append(" PP 획득!");
                break;
            }
            case "TRAP": {
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                up.put("trapTurnLeft", 3);
                dao.updateUserProgress(up);
                sb.append("함정에 걸렸다! 3턴간 전투력이 약화됩니다.");
                break;
            }
            case "SHOP": {
                boolean companionVoucher = RND.nextBoolean();
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                if (companionVoucher) {
                    up.put("companionVoucher", intVal(p.get("COMPANION_VOUCHER"), 0) + 1);
                } else {
                    up.put("equipVoucher", intVal(p.get("EQUIP_VOUCHER"), 0) + 1);
                }
                dao.updateUserProgress(up);
                sb.append("비밀상점에서 ").append(companionVoucher ? "동료" : "장비")
                  .append(" 무료뽑기 1회권을 발견했다! (다음 ").append(companionVoucher ? "/동료뽑기" : "/장비뽑기")
                  .append(" 시 자동 적용)");
                break;
            }
            case "SPECIAL":
                sb.append(handleSpecialTile(userName));
                break;
            case "STAIRS": {
                int nextFloor = floor + 1; // floor%10 in 1..8 이므로 다음 칸은 항상 같은 구간 내(최대 9층 보스)
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                up.put("curFloor", nextFloor);
                dao.updateUserProgress(up);
                grantFloorAchievements(userName, nextFloor);
                sb.append(nextFloor).append("층으로 올라갑니다!");
                break;
            }
            default:
                sb.append("...아무 일도 일어나지 않았다.");
        }
        return sb.toString();
    }

    private int countPartySize(String userName) {
        int cnt = 0;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) cnt++;
        }
        return cnt;
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

    private boolean deductPp(String userName, HashMap<String, Object> p, PP cost) {
        PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        if (!cur.canAfford(cost)) return false;
        PP result = cur.subtract(cost);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("ppValue", result.getValue());
        up.put("ppExt", result.getUnit());
        dao.updateUserProgress(up);
        p.put("PP_VALUE", result.getValue());
        p.put("PP_EXT", result.getUnit());
        return true;
    }

    /** 보유한 동료 무료뽑기권이 있으면 1장 소비하고 true, 없으면 false(비용 정상 차감 필요). */
    private boolean consumeCompanionVoucher(String userName, HashMap<String, Object> p) {
        int cur = intVal(p.get("COMPANION_VOUCHER"), 0);
        if (cur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("companionVoucher", cur - 1);
        dao.updateUserProgress(up);
        p.put("COMPANION_VOUCHER", cur - 1);
        return true;
    }

    /** 보유한 장비 무료뽑기권이 있으면 1장 소비하고 true, 없으면 false(비용 정상 차감 필요). */
    private boolean consumeEquipVoucher(String userName, HashMap<String, Object> p) {
        int cur = intVal(p.get("EQUIP_VOUCHER"), 0);
        if (cur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("equipVoucher", cur - 1);
        dao.updateUserProgress(up);
        p.put("EQUIP_VOUCHER", cur - 1);
        return true;
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
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            dao.updateUserProgress(up);
            return "전투 정보를 찾을 수 없어 전투를 종료합니다.";
        }

        PP monsterHp = PP.of(((Number) p.get("CUR_MONSTER_HP_VALUE")).doubleValue(), strVal(p.get("CUR_MONSTER_HP_EXT"), ""));
        PP monsterMaxHp = PP.of(((Number) mon.get("HP_VALUE")).doubleValue(), strVal(mon.get("HP_EXT"), ""));
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

        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        StringBuilder sb = new StringBuilder(userName).append("님," + NL);

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 (직업별 특수효과 포함) ──
        long totalDamage = 0;
        boolean stunned = false;
        boolean executeKill = false;
        int shieldPool = 0;

        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) <= 0) continue; // 전투불가

            String job = strVal(c.get("CLASS"), "WARRIOR");
            String cName = strVal(c.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
            int grade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] eff = computeEffectiveStat(job, grade, equips, userStat);

            int roll = RND.nextInt(diceMax) + 1;
            int dmg = Math.max(1, eff[1] * roll - monsterDef);
            dmg = Math.max(dmg, eff[3]); // 스탯구매 최소공격력 보정
            totalDamage += dmg;
            sb.append(cName).append(" 공격! 🎲").append(roll)
              .append(" → ").append(dmg).append(" 데미지").append(NL);

            switch (job) {
                case "MAGE":
                    if (RND.nextInt(100) < 20) {
                        stunned = true;
                        sb.append("  ✨ 마법사의 스턴 적중! 몬스터가 이번 반격을 못합니다.").append(NL);
                    }
                    break;
                case "ROGUE":
                    if (RND.nextInt(100) < 25) {
                        PP steal = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(0.1);
                        addPp(userName, p, steal);
                        sb.append("  🗡️ 도적이 ").append(steal.format()).append(" PP를 스틸했다!").append(NL);
                    }
                    break;
                case "ARCHER":
                    if (PP.toBaseValue(monsterHp) <= PP.toBaseValue(monsterMaxHp) * 0.1 && RND.nextInt(100) < 40) {
                        executeKill = true;
                        sb.append("  🏹 궁수의 즉사 사격 적중!").append(NL);
                    }
                    break;
                case "PRIEST": {
                    int shieldRoll = RND.nextInt(diceMax) + 1;
                    int shieldAmt = Math.max(0, eff[1] * shieldRoll);
                    shieldPool += shieldAmt;
                    sb.append("  🛡️ 도사가 보호막 ").append(shieldAmt).append(" 전개! (🎲").append(shieldRoll).append(")").append(NL);
                    break;
                }
                default:
                    break;
            }
        }

        PP monsterHpAfter = executeKill ? PP.fromPP(0) : monsterHp.subtract(PP.fromPP(totalDamage));
        boolean monsterDead = executeKill || PP.toBaseValue(monsterHpAfter) <= 0;

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
                // 새 구간의 첫 사냥터층은 계단 없이도 바로 층변경 가능해야 함
                up.put("maxFloorReached", nextFloor + 1);
                sb.append("👑 보스 격파! ").append(nextFloor).append("층 마을로 이동합니다.").append(NL);
                grantAchievement(userName, 7);
            } else {
                up.put("killCountCur", killCountCur >= 10 ? 0 : killCountCur);
                if (killCountCur >= 10) {
                    up.put("autoHuntYn", "Y");
                    HashMap<String, Object> log = new HashMap<>();
                    log.put("userName", userName);
                    log.put("floor", floor);
                    dao.upsertAutoHuntLog(log);
                    sb.append("🔥 이 층에서 10마리 처치! 자동사냥 모드 ON (다음 접속 시 경과시간만큼 자동 정산)").append(NL);
                }
            }
            dao.updateUserProgress(up);
            addPp(userName, p, reward);
            checkKillAchievements(userName, totalKill);
            sb.append(reward.format()).append(" PP 획득!");
            healPartyFull(userName, party, userStat);
            return sb.toString();
        }

        // 몬스터 생존 → 반격 (스턴이면 생략)
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curMonsterHpValue", monsterHpAfter.getValue());
        up.put("curMonsterHpExt", monsterHpAfter.getUnit());
        dao.updateUserProgress(up);
        sb.append(strVal(mon.get("MONSTER_NAME"), "몬스터")).append(" 남은 HP: ").append(monsterHpAfter.format()).append(NL);

        if (stunned) {
            sb.append("몬스터가 스턴에 걸려 반격하지 못했습니다!");
            return sb.toString();
        }

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
            healPartyFull(userName, party, userStat);
            sb.append("💀 파티 전멸... 전투에 패배했습니다. 동료들이 마을에서 회복 후 다시 도전하세요.");
            return sb.toString();
        }

        HashMap<String, Object> target = alive.get(RND.nextInt(alive.size()));

        // 전사 도발: 체력 50% 이상인 전사가 있으면 확률적으로 자신이 대신 맞음
        for (HashMap<String, Object> c : alive) {
            if (!"WARRIOR".equals(strVal(c.get("CLASS"), ""))) continue;
            int wGrade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> wEquips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] wEff = computeEffectiveStat("WARRIOR", wGrade, wEquips, userStat);
            PP wHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean over50 = PP.toBaseValue(wHp) * 2 >= wEff[0];
            if (over50 && !c.equals(target) && RND.nextInt(100) < 30) {
                target = c;
                sb.append("🛡️ 전사가 대신 공격을 받아냅니다!").append(NL);
            }
            break; // 파티엔 전사가 최대 1명이라고 가정하지 않지만, 첫 전사만 판정
        }

        String tJob = strVal(target.get("CLASS"), "WARRIOR");
        int tGrade = intVal(target.get("GRADE"), 1);
        List<HashMap<String, Object>> tEquips = dao.selectEquipByCompanion(intVal(target.get("COMPANION_ID"), 0));
        int[] tEff = computeEffectiveStat(tJob, tGrade, tEquips, userStat);
        int monsterAtk = intVal(mon.get("ATK_VALUE"), 0);
        int roll = RND.nextInt(diceMax) + 1;
        int dmgToParty = Math.max(1, monsterAtk * roll - tEff[2]);

        if (shieldPool > 0) {
            int absorbed = Math.min(shieldPool, dmgToParty);
            dmgToParty -= absorbed;
            sb.append("🛡️ 보호막이 ").append(absorbed).append(" 피해를 흡수했습니다!").append(NL);
        }

        PP targetHp = PP.of(((Number) target.get("CUR_HP_VALUE")).doubleValue(), strVal(target.get("CUR_HP_EXT"), ""));
        PP targetHpAfter = targetHp.subtract(PP.fromPP(dmgToParty));
        if (PP.toBaseValue(targetHpAfter) < 0) targetHpAfter = PP.fromPP(0);

        HashMap<String, Object> cUp = new HashMap<>();
        cUp.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        cUp.put("curHpValue", targetHpAfter.getValue());
        cUp.put("curHpExt", targetHpAfter.getUnit());
        dao.updateCompanionHp(cUp);

        String tName = strVal(target.get("NAME"), JOB_NAME.getOrDefault(tJob, "동료"));
        sb.append(strVal(mon.get("MONSTER_NAME"), "몬스터")).append(" 반격! 🎲").append(roll).append(" → ")
          .append(tName).append("에게 ")
          .append(dmgToParty).append(" 피해 (남은 HP ").append(targetHpAfter.format()).append(")");
        if (PP.toBaseValue(targetHpAfter) <= 0) sb.append(" — 전투불가!");

        return sb.toString();
    }

    private void healPartyFull(String userName, List<HashMap<String, Object>> party, HashMap<String, Object> userStat) {
        for (HashMap<String, Object> c : party) {
            String job = strVal(c.get("CLASS"), "WARRIOR");
            int grade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] eff = computeEffectiveStat(job, grade, equips, userStat);
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", intVal(c.get("COMPANION_ID"), 0));
            up.put("curHpValue", (double) eff[0]);
            up.put("curHpExt", "");
            dao.updateCompanionHp(up);
        }
    }

    private HashMap<String, Object> findMonsterById(int floor, int monsterId) {
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

    /** @return 이번에 새로 달성되었으면 true, 이미 달성된 상태였으면 false */
    private boolean grantAchievement(String userName, int achId) {
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(userName);
        for (HashMap<String, Object> a : mine) {
            if (intVal(a.get("ACH_ID"), -1) == achId) return false;
        }
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", userName);
        m.put("achId", achId);
        dao.insertUserAch(m);
        return true;
    }

    // ================================================================
    // 자동사냥 정산
    // ================================================================
    private void settleAutoHunt(String userName, HashMap<String, Object> p) {
        if (!"Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"))) return;
        HashMap<String, Object> log = dao.selectAutoHuntLog(userName);
        if (log == null) return;

        java.util.Date lastSettle = (java.util.Date) log.get("LAST_SETTLE_DATE");
        if (lastSettle == null) return;
        long elapsedMin = (System.currentTimeMillis() - lastSettle.getTime()) / 60000L;
        if (elapsedMin < 10) return; // 10분(=처치 1회 기준) 미만이면 정산할 게 없음

        long cappedMin = Math.min(elapsedMin, 8 * 60L); // 최대 8시간
        int floor = intVal(log.get("FLOOR"), intVal(p.get("CUR_FLOOR"), 1));
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
        if (mon == null) return;

        long kills = cappedMin / 10; // 시간당 6마리 = 10분당 1마리
        if (kills <= 0) return;

        PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
        PP reward = perKill.multiply(kills);
        addPp(userName, p, reward);

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("totalKillCount", intVal(p.get("TOTAL_KILL_COUNT"), 0) + (int) kills);
        dao.updateUserProgress(up);
        p.put("TOTAL_KILL_COUNT", intVal(p.get("TOTAL_KILL_COUNT"), 0) + (int) kills);

        HashMap<String, Object> logUp = new HashMap<>();
        logUp.put("userName", userName);
        logUp.put("floor", floor);
        dao.upsertAutoHuntLog(logUp); // LAST_SETTLE_DATE = SYSDATE 로 갱신

        checkKillAchievements(userName, intVal(p.get("TOTAL_KILL_COUNT"), 0));
        // TODO: AUTO_HUNT_PP_TOTAL 누적치 업적(14번)은 별도 누적 컬럼이 없어 아직 미체크
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
        boolean wasInCombat = "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"));
        int target = floorBlockBase(floor) + n;
        int villageFloor = floorBlockBase(floor);
        boolean alwaysFree = (target == villageFloor) || (target == villageFloor + 1); // 마을↔첫 사냥터층은 항상 자유 이동
        int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        if (!alwaysFree && target > maxReached) {
            return "🪜 " + target + "층은 아직 가본 적이 없습니다." + NL
                    + "계단을 통해 한 번은 직접 올라가야 다음부턴 층변경으로 오갈 수 있어요.";
        }

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        if (wasInCombat) {
            // 전투 중 층 이동 = 도망. 진행 중이던 전투를 포기하고 상태를 되돌린다.
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
        }
        dao.updateUserProgress(up);

        if (target != floor) {
            grantFloorAchievements(userName, target);
        }

        // 사냥터층에서 마을로 돌아가면 그 층의 원정(보드 위치+발견기록)을 초기화한다.
        // 업적(탐험왕 등)을 자유롭게 파밍하지 못하게 하려는 의도 -- 한 원정 안에서 끝까지 밀어야 함.
        int fm = floor % 10;
        boolean returnedToVillage = target % 10 == 0 && fm >= 1 && fm <= 8 && floor != target;
        if (returnedToVillage) {
            dao.deleteUserFloorProgress(userName, floor);
            dao.deleteTileVisits(userName, floor);
        }

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);
        if (wasInCombat) {
            sb.append("💨 전투에서 도망쳤습니다!").append(NL);
        }
        sb.append(floor).append("층 → ").append(target).append("층(").append(floorKindLabel(target)).append(")으로 이동했습니다.");
        if (returnedToVillage) {
            sb.append(NL).append("⚠️ ").append(floor).append("층의 탐사 진행도가 초기화되었습니다. (다시 가면 처음부터)");
        }

        int tm = target % 10;
        if (tm >= 1 && tm <= 8) {
            HashMap<String, Object> fi = dao.selectFloorInfo(target);
            int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
            int visited = dao.countTileVisits(userName, target);
            sb.append(NL).append("🗺️ 이 층 탐사 현황: ").append(visited).append("/").append(tileCount).append("칸 발견");
        }
        if (target == 1 && intVal(p.get("TOTAL_KILL_COUNT"), 0) == 0) {
            sb.append(NL).append("1층에서 주사위를 굴려 전투하세요! (/주사위)");
        }
        return sb.toString();
    }

    private void grantFloorAchievements(String userName, int floor) {
        if (floor == 1) grantAchievement(userName, 1);
        if (floor == 10) grantAchievement(userName, 2);
        if (floor == 30) grantAchievement(userName, 3);
        if (floor == 50) grantAchievement(userName, 4);
        if (floor == 70) grantAchievement(userName, 5);
        if (floor == 100) grantAchievement(userName, 6);
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
            String name = strVal(c.get("NAME"), job); // 이름 없는 옛 데이터는 직업명으로 대체 표시
            int grade = intVal(c.get("GRADE"), 1);
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            Object slot = c.get("PARTY_SLOT");
            sb.append(idx++).append(". ").append(name).append(" (").append(job).append(" ★").append(grade).append(")")
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
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p != null && "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 파티를 변경할 수 없습니다.";
        }
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

        StringBuilder sb = new StringBuilder("파티 ").append(slot).append("번 슬롯에 편성했습니다!");
        if (p != null && intVal(p.get("CUR_FLOOR"), 0) == 0) {
            sb.append(NL).append("👉 층이동 명령어로 1층 가세요! (/층변경 1)");
        }
        return sb.toString();
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
            if (hidden && !done) continue;
            sb.append(done ? "✅ " : "⬜ ").append(strVal(a.get("ACH_NAME"), "")).append(" - ")
              .append(strVal(a.get("ACH_DESC"), "")).append(NL);
        }
        return sb.toString();
    }

    // ================================================================
    // 가챠
    // ================================================================
    // (구 /탑상점 명령어는 제거됨 -- SPA "상점" 탭이 그 UI 역할을 대신하고,
    //  비밀상점 칸에서 나오는 무료뽑기권으로 대체됨)

    /**
     * 동료 초상화용 랜덤 이미지 1장을 가져와 URL만 반환. nekos.best는 이미 user_info_view.jsp에서
     * 클라이언트(localStorage)로 캐싱해 쓰던 API인데, 여기선 동료별로 영구히 남아야 해서 뽑는 시점에
     * 서버가 한 번 호출해 DB(IMAGE_URL)에 박아둔다. 실패해도 동료 생성 자체는 계속 진행(null 반환).
     */
    private String fetchRandomNekoImage() {
        try {
            URL url = new URL("https://nekos.best/api/v2/neko");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject obj = new JSONObject(sb.toString());
            JSONArray results = obj.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            return results.getJSONObject(0).optString("url", null);
        } catch (Exception e) {
            return null;
        }
    }

    private int rollGrade(HashMap<String, Object> gacha) {
        double[] w = new double[6];
        double sum = 0;
        for (int i = 0; i < 6; i++) {
            Object v = gacha.get("PROB_G" + (i + 1));
            w[i] = v == null ? 0 : ((Number) v).doubleValue();
            sum += w[i];
        }
        if (sum <= 0) return 1;
        double r = RND.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < 6; i++) {
            acc += w[i];
            if (r < acc) return i + 1;
        }
        return 6;
    }

    /** 하급 동료 계약서(GACHA_ID=1)는 튜토리얼 차원에서 첫 2회까지 무료. */
    private static final int STARTER_GACHA_ID = 1;
    private static final int STARTER_FREE_PULLS = 2;

    /** 동료 뽑기 1회의 핵심 로직(무료판정/비용차감/추첨/insert)만 수행. 실패 시 result에 error만 채워 반환. */
    private HashMap<String, Object> pullCompanionCore(String userName, HashMap<String, Object> gacha,
            HashMap<String, Object> p, int ownedSoFar) {
        HashMap<String, Object> result = new HashMap<>();
        int gachaId = intVal(gacha.get("GACHA_ID"), 0);

        // 해금 여부는 무료뽑기권/스타터 무료와 무관하게 항상 확인 (권은 비용만 면제, 해금 요건은 그대로)
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 계약서입니다.");
            return result;
        }

        boolean free = gachaId == STARTER_GACHA_ID && ownedSoFar < STARTER_FREE_PULLS;
        if (!free) free = consumeCompanionVoucher(userName, p);
        if (!free) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            if (!deductPp(userName, p, cost)) {
                result.put("error", "PP가 부족합니다. (필요 " + cost.format() + " PP)");
                return result;
            }
        }

        int grade = rollGrade(gacha);
        String job = JOB_KEYS[RND.nextInt(JOB_KEYS.length)];
        int[] stat = calcBaseStat(job, grade);
        String name = NAME_POOL[RND.nextInt(NAME_POOL.length)];
        String imageUrl = fetchRandomNekoImage();

        HashMap<String, Object> c = new HashMap<>();
        c.put("userName", userName);
        c.put("class", job);
        c.put("grade", grade);
        c.put("name", name);
        c.put("imageUrl", imageUrl);
        c.put("curHpValue", (double) stat[0]);
        c.put("curHpExt", "");
        c.put("partySlot", null);
        dao.insertCompanion(c);

        int cnt = ownedSoFar + 1;
        if (cnt == 1) grantAchievement(userName, 10);
        if (cnt == 50) grantAchievement(userName, 11);
        if (grade == 6) grantAchievement(userName, 22);

        result.put("ok", true);
        result.put("job", job);
        result.put("grade", grade);
        result.put("stat", stat);
        result.put("name", name);
        return result;
    }

    private String pullCompanionInternal(String userName, int gachaId) {
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"COMPANION".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 동료 계약서입니다.";
        }
        int ownedBefore = dao.countUserCompanions(userName);
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        HashMap<String, Object> r = pullCompanionCore(userName, gacha, p, ownedBefore);
        if (r.get("error") != null) return (String) r.get("error");

        String job = (String) r.get("job");
        int grade = intVal(r.get("grade"), 1);
        int[] stat = (int[]) r.get("stat");
        String name = (String) r.get("name");
        int cnt = ownedBefore + 1;

        // 새로 뽑은 동료는 (PARTY_SLOT NULLS LAST, COMPANION_ID) 정렬상 항상 목록의 맨 끝(=cnt번)에 위치
        StringBuilder sb = new StringBuilder();
        sb.append("┌─────────────┐").append(NL);
        sb.append("  🎉 새 동료 영입!").append(NL);
        sb.append("└─────────────┘").append(NL);
        sb.append(name).append(" (").append(JOB_NAME.get(job)).append(" ★").append(grade).append(")").append(NL);
        sb.append("스탯: HP ").append(stat[0]).append(" / ATK ").append(stat[1]).append(" / DEF ").append(stat[2]).append(NL);
        sb.append("👉 /파티편성 ").append(cnt).append(" 로 파티에 편성하세요 (동료 목록 ").append(cnt).append("번)");
        return sb.toString();
    }

    private String pullCompanionTenInternal(String userName, int gachaId) {
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"COMPANION".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 동료 계약서입니다.";
        }
        int owned = dao.countUserCompanions(userName);
        HashMap<String, Object> p = dao.selectUserProgress(userName);

        int[] gradeCount = new int[7]; // index 1~6
        int success = 0;
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullCompanionCore(userName, gacha, p, owned);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            owned++;
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 동료뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 /파티편성 으로 확인하세요");
        return sb.toString();
    }

    private boolean isVillage(HashMap<String, Object> p) {
        return intVal(p.get("CUR_FLOOR"), 0) % 10 == 0;
    }

    @Override
    public int freeCompanionPullsLeft(String userName) {
        return Math.max(0, STARTER_FREE_PULLS - dao.countUserCompanions(userName));
    }

    @Override
    @Transactional
    public String gachaCompanion(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (!isVillage(p)) {
            return "🏘️ 동료뽑기는 마을에서만 가능합니다. /층변경 0 으로 마을로 이동하세요.";
        }
        return pullCompanionInternal(userName, gachaId);
    }

    @Override
    @Transactional
    public String gachaCompanionTen(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (!isVillage(p)) {
            return "🏘️ 동료뽑기는 마을에서만 가능합니다. /층변경 0 으로 마을로 이동하세요.";
        }
        return pullCompanionTenInternal(userName, gachaId);
    }

    /** 장비 뽑기 1회의 핵심 로직만 수행. 실패 시 result에 error만 채워 반환. */
    private HashMap<String, Object> pullEquipCore(String userName, HashMap<String, Object> gacha, HashMap<String, Object> p) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 상자입니다.");
            return result;
        }
        if (!consumeEquipVoucher(userName, p)) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            if (!deductPp(userName, p, cost)) {
                result.put("error", "PP가 부족합니다. (필요 " + cost.format() + " PP)");
                return result;
            }
        }

        int grade = rollGrade(gacha);
        String job = JOB_KEYS[RND.nextInt(JOB_KEYS.length)];
        String[] parts = { "HELMET", "WEAPON", "ARMOR" };
        String part = parts[RND.nextInt(parts.length)];

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", job);
        e.put("part", part);
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        if (grade == 6) grantAchievement(userName, 22);

        result.put("ok", true);
        result.put("job", job);
        result.put("part", part);
        result.put("grade", grade);
        return result;
    }

    @Override
    @Transactional
    public String gachaEquip(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (!isVillage(p)) {
            return "🏘️ 장비뽑기는 마을에서만 가능합니다. /층변경 0 으로 마을로 이동하세요.";
        }
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"EQUIP".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 장비 상자입니다.";
        }
        HashMap<String, Object> r = pullEquipCore(userName, gacha, p);
        if (r.get("error") != null) return (String) r.get("error");

        String job = (String) r.get("job");
        String part = (String) r.get("part");
        int grade = intVal(r.get("grade"), 1);
        String partName = "HELMET".equals(part) ? "투구" : "WEAPON".equals(part) ? "무기" : "갑옷";
        return "🎁 " + JOB_NAME.get(job) + "용 " + partName + " ★" + grade + " 획득!";
    }

    @Override
    @Transactional
    public String gachaEquipTen(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (!isVillage(p)) {
            return "🏘️ 장비뽑기는 마을에서만 가능합니다. /층변경 0 으로 마을로 이동하세요.";
        }
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"EQUIP".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 장비 상자입니다.";
        }

        int[] gradeCount = new int[7];
        int success = 0;
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullEquipCore(userName, gacha, p);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 장비뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 파티/장비 탭에서 확인하세요");
        return sb.toString();
    }

    // ================================================================
    // /주사위구매 (등급 확인/교체 — 무료 장착, 층 진행으로 자동 해금)
    // ================================================================
    @Override
    @Transactional
    public String diceShop(String userName, Integer n) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        String curDice = strVal(p.get("DICE_GRADE"), "DICE_6");

        if (n == null) {
            StringBuilder sb = new StringBuilder(userName).append("님의 주사위 목록," + NL);
            for (int i = 0; i < DICE_NAMES.length; i++) {
                boolean unlockedTier = unlocked >= DICE_UNLOCK[i];
                sb.append(i + 1).append(". ").append(DICE_NAMES[i])
                  .append(unlockedTier ? "" : " (미해금, " + DICE_UNLOCK[i] + "층부터)")
                  .append(DICE_NAMES[i].equals(curDice) ? " ← 사용중" : "")
                  .append(NL);
            }
            sb.append("/주사위구매 N 으로 해금된 주사위 장착");
            return sb.toString();
        }

        if (n < 1 || n > DICE_NAMES.length) return "잘못된 번호입니다.";
        if (unlocked < DICE_UNLOCK[n - 1]) return "아직 해금되지 않은 주사위입니다.";

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceGrade", DICE_NAMES[n - 1]);
        dao.updateUserProgress(up);
        return DICE_NAMES[n - 1] + " 을(를) 장착했습니다.";
    }

    // ================================================================
    // /스탯구매
    // ================================================================
    @Override
    @Transactional
    public String statShop(String userName, String type) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        HashMap<String, Object> stat = dao.selectUserStat(userName);
        int atkMaxLv = stat == null ? 0 : intVal(stat.get("ATK_MAX_LV"), 0);
        int atkMinLv = stat == null ? 0 : intVal(stat.get("ATK_MIN_LV"), 0);
        int hpLv     = stat == null ? 0 : intVal(stat.get("HP_LV"), 0);
        int cap = 5 + 5 * (intVal(p.get("UNLOCKED_BLOCK"), 0) / 10);

        if (type == null || type.isEmpty()) {
            StringBuilder sb = new StringBuilder(userName).append("님의 스탯 구매 현황 (구간 상한 ").append(cap).append(")," + NL);
            sb.append("공격력(최대) Lv").append(atkMaxLv).append(" — 다음 비용 ").append(50 * (atkMaxLv + 1)).append(" PP").append(NL);
            sb.append("공격력(최소) Lv").append(atkMinLv).append(" — 다음 비용 ").append(50 * (atkMinLv + 1)).append(" PP").append(NL);
            sb.append("체력 Lv").append(hpLv).append(" — 다음 비용 ").append(50 * (hpLv + 1)).append(" PP").append(NL);
            sb.append("/스탯구매 공격력 | 최소공격력 | 체력");
            return sb.toString();
        }

        int curLv;
        String field;
        switch (type) {
            case "공격력":     curLv = atkMaxLv; field = "atk"; break;
            case "최소공격력": curLv = atkMinLv; field = "min"; break;
            case "체력":       curLv = hpLv;     field = "hp";  break;
            default: return "스탯 종류는 공격력 / 최소공격력 / 체력 중 하나여야 합니다.";
        }
        if (curLv >= cap) return "현재 구간에서는 더 이상 강화할 수 없습니다. 다음 마을에 도달하면 상한이 늘어납니다.";

        PP cost = PP.fromPP(50 * (curLv + 1));
        if (!deductPp(userName, p, cost)) return "PP가 부족합니다. (필요 " + cost.format() + " PP)";

        if ("atk".equals(field)) atkMaxLv++;
        else if ("min".equals(field)) atkMinLv++;
        else hpLv++;

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("atkMaxLv", atkMaxLv);
        up.put("atkMinLv", atkMinLv);
        up.put("hpLv", hpLv);
        dao.upsertUserStat(up);
        return type + " 스탯을 강화했습니다! (Lv" + curLv + " → Lv" + (curLv + 1) + ")";
    }

    // ================================================================
    // 장비
    // ================================================================
    @Override
    public String equipList(String userName) {
        getOrInitProgress(userName);
        List<HashMap<String, Object>> equips = dao.selectUserEquip(userName);
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        HashMap<Integer, String> companionLabel = new HashMap<>();
        for (HashMap<String, Object> c : companions) {
            companionLabel.put(intVal(c.get("COMPANION_ID"), -1),
                    JOB_NAME.getOrDefault(strVal(c.get("CLASS"), ""), "?") + " ★" + intVal(c.get("GRADE"), 1));
        }
        if (equips.isEmpty()) return "보유한 장비가 없습니다.";

        StringBuilder sb = new StringBuilder(userName).append("님의 장비 목록," + NL);
        int idx = 1;
        for (HashMap<String, Object> e : equips) {
            String part = strVal(e.get("PART"), "");
            String partName = "HELMET".equals(part) ? "투구" : "WEAPON".equals(part) ? "무기" : "갑옷";
            Object cid = e.get("EQUIPPED_COMPANION_ID");
            sb.append(idx++).append(". ").append(JOB_NAME.getOrDefault(strVal(e.get("CLASS"), ""), "?"))
              .append(" ").append(partName).append(" ★").append(intVal(e.get("GRADE"), 1))
              .append(cid != null ? " [" + companionLabel.getOrDefault(((Number) cid).intValue(), "장착중") + "]" : " [미착용]")
              .append(NL);
        }
        sb.append("/장비장착 N (미착용 목록 기준), /장비합성 N");
        return sb.toString();
    }

    @Override
    @Transactional
    public String equipWear(String userName, int equipIdx, Integer companionIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 변경할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        HashMap<String, Object> equip = unequipped.get(equipIdx - 1);
        String equipClass = strVal(equip.get("CLASS"), "");
        String part = strVal(equip.get("PART"), "");

        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : companions) if (c.get("PARTY_SLOT") != null) party.add(c);

        HashMap<String, Object> targetCompanion = null;
        if (companionIdx != null) {
            if (companionIdx < 1 || companionIdx > party.size()) return "잘못된 동료 번호입니다. /파티편성을 확인하세요.";
            targetCompanion = party.get(companionIdx - 1);
        } else {
            for (HashMap<String, Object> c : party) {
                if (equipClass.equals(strVal(c.get("CLASS"), ""))) { targetCompanion = c; break; }
            }
        }
        if (targetCompanion == null) return "장착할 동료를 찾지 못했습니다 (같은 직업의 파티원이 필요합니다).";
        if (!equipClass.equals(strVal(targetCompanion.get("CLASS"), ""))) return "이 장비는 " + JOB_NAME.get(equipClass) + " 전용입니다.";

        int companionId = intVal(targetCompanion.get("COMPANION_ID"), 0);
        // 같은 부위에 이미 장착된 게 있으면 해제
        for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
            if (part.equals(strVal(e.get("PART"), ""))) {
                HashMap<String, Object> unwear = new HashMap<>();
                unwear.put("equipId", intVal(e.get("EQUIP_ID"), 0));
                unwear.put("equippedCompanionId", null);
                dao.updateEquipEquippedCompanion(unwear);
            }
        }

        HashMap<String, Object> wear = new HashMap<>();
        wear.put("equipId", intVal(equip.get("EQUIP_ID"), 0));
        wear.put("equippedCompanionId", companionId);
        dao.updateEquipEquippedCompanion(wear);

        return "장착했습니다!";
    }

    @Override
    @Transactional
    public String equipSynthesis(String userName, int equipIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 합성할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        HashMap<String, Object> equip = unequipped.get(equipIdx - 1);
        int grade = intVal(equip.get("GRADE"), 1);
        if (grade >= 6) return "★6 장비는 합성할 수 없습니다.";
        String clazz = strVal(equip.get("CLASS"), "");
        String part = strVal(equip.get("PART"), "");

        List<HashMap<String, Object>> same = dao.selectSameEquipForSynthesis(userName, clazz, part, grade);
        if (same.size() < 3) return "동일 등급/부위/직업 미착용 장비가 3개 이상 필요합니다. (현재 " + same.size() + "개)";

        for (int i = 0; i < 3; i++) {
            dao.deleteEquip(intVal(same.get(i).get("EQUIP_ID"), 0));
        }
        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", clazz);
        e.put("part", part);
        e.put("grade", grade + 1);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        grantAchievement(userName, 12);
        // TODO: EQUIP_SYNTHESIS 누적 횟수 카운터가 없어 13번(30회) 업적은 아직 체크 불가
        String partName = "HELMET".equals(part) ? "투구" : "WEAPON".equals(part) ? "무기" : "갑옷";
        return "✨ 합성 성공! " + JOB_NAME.get(clazz) + " " + partName + " ★" + (grade + 1) + " 획득!";
    }
}
