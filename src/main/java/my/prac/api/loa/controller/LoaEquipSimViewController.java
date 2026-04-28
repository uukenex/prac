package my.prac.api.loa.controller;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

@Controller
@RequestMapping("/loa")
public class LoaEquipSimViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    // ── 직업 목록 (드롭다운용) ─────────────────────────────────
    private static final List<String> JOB_LIST = Arrays.asList(
        "전사","검성","용사","파이터","도적","저격수","궁수","궁사",
        "사냥꾼","어둠사냥꾼","헌터","곰","도사","음양사","제너럴",
        "어쓰신","복수자","사신","처단자","축복술사","은둔자","흡혈귀"
    );

    /** JSP 뷰 페이지 */
    @GetMapping("/equip-sim-view")
    public String equipSimPage() {
        return "nonsession/loa/equip_sim_view";
    }

    /** 초기 데이터: 유저 기본 스탯 + 보유 아이템 목록 */
    @GetMapping("/api/equip-sim-init")
    @ResponseBody
    public ResponseEntity<?> getEquipSimInit(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        Map<String,Object> result = new LinkedHashMap<>();
        userName = userName.trim();
        if (userName.isEmpty()) {
            result.put("error", "userName이 필요합니다.");
            return ResponseEntity.ok(result);
        }

        User u = botNewService.selectUser(userName, null);
        if (u == null) {
            result.put("error", "유저(" + userName + ")를 찾을 수 없습니다.");
            return ResponseEntity.ok(result);
        }

        String hunterGrade  = resolveHunterGrade(userName);
        double hellNerfMult = MiniGameUtil.getHellNerfMult(hunterGrade);

        // 보유 아이템만 필터
        List<HashMap<String,Object>> ownedItems = new ArrayList<>();
        try {
            for (HashMap<String,Object> item : botNewService.selectAllItemsWithOwned(userName)) {
                Object qty = item.get("OWN_QTY");
                if (qty instanceof Number && ((Number)qty).intValue() > 0) {
                    ownedItems.add(item);
                }
            }
        } catch (Exception ignore) {}

        Map<String,Object> userMap = new LinkedHashMap<>();
        userMap.put("userName",    userName);
        userMap.put("atkMin",      u.atkMin);
        userMap.put("atkMax",      u.atkMax);
        userMap.put("hpMax",       u.hpMax);
        userMap.put("hpRegen",     u.hpRegen);
        userMap.put("critRate",    u.critRate);
        userMap.put("critDmg",     u.critDmg);
        userMap.put("lv",          u.lv);
        userMap.put("job",         u.job == null ? "" : u.job.trim());
        userMap.put("nightmareYn", u.nightmareYn);

        result.put("user",        userMap);
        result.put("hunterGrade", hunterGrade);
        result.put("hellNerfMult", hellNerfMult);
        result.put("ownedItems",  ownedItems);
        result.put("jobList",     JOB_LIST);

        return ResponseEntity.ok(result);
    }

    /**
     * 실시간 스탯 계산
     * Body: { userName, simJob, hellMode, equippedItems: [{itemId, qty}] }
     */
    @PostMapping("/api/equip-sim-calc")
    @ResponseBody
    public ResponseEntity<?> calcEquipSim(@RequestBody Map<String,Object> body) {

        String  userName = Objects.toString(body.get("userName"), "").trim();
        String  simJob   = Objects.toString(body.get("simJob"),   "").trim();
        boolean hellMode = Boolean.TRUE.equals(body.get("hellMode"));

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> equippedList =
            body.get("equippedItems") instanceof List
                ? (List<Map<String,Object>>) body.get("equippedItems")
                : Collections.emptyList();

        Map<String,Object> result = new LinkedHashMap<>();

        User u = botNewService.selectUser(userName, null);
        if (u == null) {
            result.put("error", "유저 없음");
            return ResponseEntity.ok(result);
        }

        String job = simJob.isEmpty() ? (u.job == null ? "" : u.job.trim()) : simJob;

        // ── 아이템 스탯 합산 ─────────────────────────────────────────
        int mktAtkMin=0, mktAtkMax=0, mktCrit=0, mktRegen=0;
        int mktHpMax=0, mktCritDmg=0, mktHpMaxRate=0, mktAtkMaxRate=0;
        Set<Integer> bossEquipped = new HashSet<>();

        for (Map<String,Object> ei : equippedList) {
            int itemId = ei.get("itemId") instanceof Number ? ((Number)ei.get("itemId")).intValue() : 0;
            int qty    = ei.get("qty")    instanceof Number ? ((Number)ei.get("qty")).intValue()    : 1;
            if (qty <= 0) continue;

            HashMap<String,Object> item = MiniGameUtil.ITEM_DETAIL_CACHE.get(itemId);
            if (item == null) continue;

            mktAtkMin    += safeInt(item.get("ATK_MIN"))      * qty;
            mktAtkMax    += safeInt(item.get("ATK_MAX"))      * qty;
            mktCrit      += safeInt(item.get("ATK_CRI"))      * qty;
            mktRegen     += safeInt(item.get("HP_REGEN"))     * qty;
            mktHpMax     += safeInt(item.get("HP_MAX"))       * qty;
            mktCritDmg   += safeInt(item.get("CRI_DMG"))      * qty;
            mktHpMaxRate += safeInt(item.get("HP_MAX_RATE"))  * qty;
            mktAtkMaxRate+= safeInt(item.get("ATK_MAX_RATE")) * qty;
            if (itemId >= 7000 && itemId < 8000) bossEquipped.add(itemId);
        }

        // ── 헌터 등급 + 보너스 ──────────────────────────────────────
        String hunterGrade = resolveHunterGrade(userName);
        int hunterAtk=0, hunterHp=0, hunterRegen=0, hunterCritDmg=0;

        if ("헌터".equals(job)) {
            try {
                AttackDeathStat ads = botNewService.selectAttackDeathStats(userName, "");
                int totalAttacks = ads == null ? 0 : ads.totalAttacks + (ads.hunterAttacks * 2);
                int totalDeaths  = ads == null ? 0 : ads.totalDeaths  + (ads.hunterAttacks / 2);
                int totalDrops   = getDropsFromCache(userName);

                int atkCap, hpCap, regenCap, criCap;
                switch (hunterGrade) {
                    case "SSS": atkCap=8000; hpCap=80000; regenCap=8000; criCap=60; break;
                    case "SS":  atkCap=6000; hpCap=60000; regenCap=6000; criCap=45; break;
                    case "S":   atkCap=4000; hpCap=30000; regenCap=3000; criCap=30; break;
                    case "A+":  atkCap=3300; hpCap=22000; regenCap=2200; criCap=27; break;
                    case "A":   atkCap=3000; hpCap=20000; regenCap=2000; criCap=25; break;
                    case "B+":  atkCap=2200; hpCap=16000; regenCap=1700; criCap=22; break;
                    case "B":   atkCap=2000; hpCap=15000; regenCap=1500; criCap=20; break;
                    case "C+":  atkCap=1300; hpCap=9000;  regenCap=900;  criCap=16; break;
                    case "C":   atkCap=1200; hpCap=8000;  regenCap=800;  criCap=15; break;
                    case "D+":  atkCap=700;  hpCap=5000;  regenCap=500;  criCap=11; break;
                    case "D":   atkCap=600;  hpCap=4000;  regenCap=400;  criCap=10; break;
                    default:    atkCap=200;  hpCap=1000;  regenCap=100;  criCap=5;
                }
                hunterAtk     = Math.min(totalAttacks/5,  atkCap);
                hunterHp      = Math.min(totalDrops  /5,  hpCap);
                hunterRegen   = Math.min(totalDrops  /50, regenCap);
                hunterCritDmg = Math.min(totalDeaths /5,  criCap);
            } catch (Exception ignore) {}
        }

        // 어둠사냥꾼: item HP/Regen 25% 증가
        if ("어둠사냥꾼".equals(job)) {
            mktHpMax  = (int) Math.round(mktHpMax  * 1.25);
            mktRegen  = (int) Math.round(mktRegen  * 1.25);
        }

        mktAtkMin  += hunterAtk;
        mktAtkMax  += hunterAtk;
        mktHpMax   += hunterHp;
        mktRegen   += hunterRegen;
        mktCritDmg += hunterCritDmg;

        // ── 기본 합산 ────────────────────────────────────────────────
        int atkMin  = u.atkMin   + mktAtkMin;
        int atkMax  = u.atkMax   + mktAtkMax;
        int hpMax   = u.hpMax    + mktHpMax;
        int regen   = u.hpRegen  + mktRegen;
        int crit    = u.critRate + mktCrit;
        int critDmg = u.critDmg  + mktCritDmg;

        // ── 직업 보너스 ──────────────────────────────────────────────
        if ("검성".equals(job) || "용사".equals(job)) hpMax += u.hpMax * 2;
        if ("흡혈귀".equals(job)) regen = 0;

        // 곰: HP = ATK
        if ("곰".equals(job)) {
            int atkSum  = atkMin + atkMax;
            int critMul = u.critDmg + mktCritDmg;
            hpMax  = hpMax + (atkSum * critMul / 100);
            atkMin = hpMax; atkMax = hpMax;
            crit   = 0; critDmg = 0;
        }

        // ── 비율 보너스 ──────────────────────────────────────────────
        hpMax  += (int) Math.round(hpMax  * mktHpMaxRate  / 100.0);
        atkMin += (int) Math.round(atkMin * mktAtkMaxRate  / 100.0);
        atkMax += (int) Math.round(atkMax * mktAtkMaxRate  / 100.0);

        // ── 헬너프 ───────────────────────────────────────────────────
        if (hellMode) {
            double mult = MiniGameUtil.getHellNerfMult(hunterGrade);
            if (bossEquipped.contains(7007)) mult = Math.max(0.0, mult + 0.03);
            atkMin  = (int) Math.round(atkMin  * mult);
            atkMax  = (int) Math.round(atkMax  * mult);
            hpMax   = (int) Math.round(hpMax   * mult);
            crit    = (int) Math.round(crit    * mult);
            critDmg = (int) Math.round(critDmg * mult);
        }

        // ── [7009] 진화형 무기 ───────────────────────────────────────
        if (bossEquipped.contains(7009)) {
            atkMin += u.lv * 150;
            atkMax += u.lv * 150;
        }

        result.put("atkMin",      atkMin);
        result.put("atkMax",      atkMax);
        result.put("hpMax",       hpMax);
        result.put("regen",       regen);
        result.put("crit",        crit);
        result.put("critDmg",     critDmg);
        result.put("hunterGrade", hunterGrade);
        result.put("job",         job);

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════════════

    private String resolveHunterGrade(String userName) {
        try {
            AttackDeathStat ads = botNewService.selectAttackDeathStats(userName, "");
            int totalAttacks = ads == null ? 0 : ads.totalAttacks + (ads.hunterAttacks * 2);
            int totalDeaths  = ads == null ? 0 : ads.totalDeaths  + (ads.hunterAttacks / 2);
            int totalDrops   = getDropsFromCache(userName);
            return calcGrade(totalAttacks, totalDrops, totalDeaths);
        } catch (Exception e) { return "F"; }
    }

    private int getDropsFromCache(String userName) {
        HashMap<String,Object> cached = MiniGameUtil.INV_BUFF_CACHE.get(userName);
        if (cached == null) return 0;
        @SuppressWarnings("unchecked")
        List<HashMap<String,Object>> drops = (List<HashMap<String,Object>>) cached.get("drops");
        if (drops == null) return 0;
        int total = 0;
        for (HashMap<String,Object> d : drops) {
            Object v = d.get("TOTAL_QTY");
            if (v instanceof Number) total += ((Number)v).intValue();
        }
        return total;
    }

    private String calcGrade(int atk, int drop, int dth) {
        if (atk >= 50000 && drop >= 100000 && dth >= 1200) return "SSS";
        if (atk >= 40000 && drop >= 50000  && dth >= 700)  return "SS";
        if (atk >= 30000 && drop >= 30000  && dth >= 500)  return "S";
        String g;
        g = checkPlus(atk, drop, dth, 20000, 20000, 400, "A"); if (g != null) return g;
        g = checkPlus(atk, drop, dth, 10000, 10000, 200, "B"); if (g != null) return g;
        g = checkPlus(atk, drop, dth,  5000,  5000, 100, "C"); if (g != null) return g;
        g = checkPlus(atk, drop, dth,  1000,  1000,  50, "D"); if (g != null) return g;
        return "F";
    }

    private String checkPlus(int atk, int drop, int dth,
                              int atkReq, int dropReq, int dthReq, String base) {
        if (atk < atkReq || drop < dropReq || dth < dthReq) return null;
        int above = (atk  >= atkReq  * 2 ? 1 : 0)
                  + (drop >= dropReq * 2 ? 1 : 0)
                  + (dth  >= dthReq  * 2 ? 1 : 0);
        return above >= 2 ? base + "+" : base;
    }

    private int safeInt(Object o) {
        if (o instanceof Number) return ((Number)o).intValue();
        if (o instanceof String) { try { return Integer.parseInt((String)o); } catch (Exception ignored) {} }
        return 0;
    }
}
