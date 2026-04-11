package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.game.dto.AchievementConfig;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.prjbot.service.BotNewService;

@Controller
@RequestMapping("/loa")
public class LoaHunterRankViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    @GetMapping("/hunter-rank-view")
    public String hunterRankViewPage() {
        return "nonsession/loa/hunter_rank_view";
    }

    /**
     * 헌터 랭크 API
     * 5가지 지표:
     *   1) 원시 킬수 (totalKills → selectAttackDeathStats.totalAttacks)
     *   2) 원시 드랍갯수 (totalDrops)
     *   3) 원시 죽음수 (totalDeaths)
     *   4) 헌터직업 공격횟수 보정 → 킬+2배, 죽음+0.5배
     *   5) 어둠/음양 아이템 획득 보정 (S 이하에만) → 킬/드랍/죽음 각 +1(어둠), +3(음양)
     */
    @GetMapping("/api/hunter-rank")
    @ResponseBody
    public ResponseEntity<?> getHunterRank(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();

        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 1. 공격/죽음/헌터공격 통계
        AttackDeathStat ads = null;
        try {
            ads = botNewService.selectAttackDeathStats(userName, "");
        } catch (Exception ignore) {}

        int rawKills      = ads == null ? 0 : ads.totalAttacks;
        int hunterAttacks = ads == null ? 0 : ads.hunterAttacks;
        int rawDeaths     = ads == null ? 0 : ads.totalDeaths;

        int hunterKillBonus  = hunterAttacks * 2;
        int hunterDeathBonus = hunterAttacks / 2;

        // 2. 드랍 통계 (빛/어둠/음양 포함)
        int rawDrops = 0;
        int darkQty  = 0;
        int grayQty  = 0;
        try {
            List<HashMap<String, Object>> drops = botNewService.selectTotalDropItems(userName);
            if (drops != null) {
                for (HashMap<String, Object> d : drops) {
                    Object v = d.get("TOTAL_QTY");
                    String type = Objects.toString(d.get("GAIN_TYPE"), "");
                    if (v instanceof Number) {
                        int qty = ((Number) v).intValue();
                        rawDrops += qty;
                        if (AchievementConfig.ITEM_TYPE_DARK.equals(type)) darkQty += qty;
                        else if (AchievementConfig.ITEM_TYPE_GRAY.equals(type)) grayQty += qty;
                    }
                }
            }
        } catch (Exception ignore) {}

        int itemBonusVal = darkQty * 1 + grayQty * 3;

        // 3. 보정 전 등급 계산 (SS이상이면 아이템 보너스 미적용)
        int adjKills  = rawKills  + hunterKillBonus;
        int adjDrops  = rawDrops;
        int adjDeaths = rawDeaths + hunterDeathBonus;

        String gradeWithout = calcGrade(adjKills, adjDrops, adjDeaths);
        boolean bonusEligible = !"SSS".equals(gradeWithout) && !gradeWithout.startsWith("SS");

        if (bonusEligible) {
            int k  = adjKills  + itemBonusVal;
            int dr = adjDrops  + itemBonusVal;
            int de = adjDeaths + itemBonusVal;

            String gradeWith = calcGrade(k, dr, de);

            if ("SSS".equals(gradeWith)) {
                gradeWith = "SS";
            } else {
                adjKills  = k;
                adjDrops  = dr;
                adjDeaths = de;
            }

            gradeWithout = gradeWith;
        }

        String grade = gradeWithout;

        // 4. 다음 등급 요건 계산
        int[][] gradeReqs = {
            // {killReq, dropReq, deathReq}
            {50000, 100000, 1200},  // SSS
            {40000,  50000,  700},  // SS
            {30000,  30000,  500},  // S
            {20000,  20000,  400},  // A
            {10000,  10000,  200},  // B
            { 5000,   5000,  100},  // C
            { 1000,   1000,   50},  // D
        };
        String[] gradeNames = {"SSS","SS","S","A","B","C","D"};

        String nextGrade  = "MAX";
        int[] nextReqs    = null;
        int[] curReqs     = null;
        for (int i = 0; i < gradeNames.length; i++) {
            if (grade.startsWith(gradeNames[i])) {
                curReqs = gradeReqs[i];
                nextGrade = (i > 0) ? gradeNames[i - 1] : "MAX";
                nextReqs  = (i > 0) ? gradeReqs[i - 1] : null;
                break;
            }
        }
        if (curReqs == null) { // F grade
            curReqs  = new int[]{0, 0, 0};
            nextGrade = "D";
            nextReqs  = gradeReqs[6];
        }

        // 5. JSON 응답
        result.put("userName",       userName);
        result.put("grade",          grade);
        result.put("bonusEligible",  bonusEligible);

        HashMap<String, Object> rawMap = new HashMap<>();
        rawMap.put("kills",  rawKills);
        rawMap.put("drops",  rawDrops);
        rawMap.put("deaths", rawDeaths);
        result.put("raw", rawMap);

        HashMap<String, Object> hunterBonusMap = new HashMap<>();
        hunterBonusMap.put("hunterAttacks",  hunterAttacks);
        hunterBonusMap.put("killBonus",   hunterKillBonus);
        hunterBonusMap.put("deathBonus",  hunterDeathBonus);
        result.put("hunterBonus", hunterBonusMap);

        HashMap<String, Object> itemBonusMap = new HashMap<>();
        itemBonusMap.put("darkQty",   darkQty);
        itemBonusMap.put("grayQty",   grayQty);
        itemBonusMap.put("bonusVal",  itemBonusVal);
        itemBonusMap.put("applied",   bonusEligible);
        result.put("itemBonus", itemBonusMap);

        HashMap<String, Object> adjMap = new HashMap<>();
        adjMap.put("kills",  adjKills);
        adjMap.put("drops",  adjDrops);
        adjMap.put("deaths", adjDeaths);
        result.put("adjusted", adjMap);

        result.put("nextGrade", nextGrade);
        if (nextReqs != null) {
            HashMap<String, Object> nr = new HashMap<>();
            nr.put("kills",  nextReqs[0]);
            nr.put("drops",  nextReqs[1]);
            nr.put("deaths", nextReqs[2]);
            result.put("nextReqs", nr);
        } else {
            result.put("nextReqs", null);
        }

        return ResponseEntity.ok(result);
    }

    private String calcGrade(int kills, int drops, int deaths) {
        if (kills >= 50000 && drops >= 100000 && deaths >= 1200) return "SSS";
        if (kills >= 40000 && drops >=  50000 && deaths >=  700) return "SS";
        if (kills >= 30000 && drops >=  30000 && deaths >=  500) return "S";
        String g = checkPlus(kills, drops, deaths, 20000, 20000, 400, "A");
        if (g != null) return g;
        g = checkPlus(kills, drops, deaths, 10000, 10000, 200, "B");
        if (g != null) return g;
        g = checkPlus(kills, drops, deaths,  5000,  5000, 100, "C");
        if (g != null) return g;
        g = checkPlus(kills, drops, deaths,  1000,  1000,  50, "D");
        if (g != null) return g;
        return "F";
    }

    private String checkPlus(int k, int d, int de, int kr, int dr, int der, String base) {
        int m = 0;
        if (k >= kr)  m++;
        if (d >= dr)  m++;
        if (de >= der) m++;
        if (m == 3) return base + "+";
        if (m == 2) return base;
        return null;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return Integer.parseInt(Objects.toString(o, "0")); }
        catch (Exception e) { return 0; }
    }
}
