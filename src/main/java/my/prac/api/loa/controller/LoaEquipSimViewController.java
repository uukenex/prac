package my.prac.api.loa.controller;

import java.util.*;

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

    // 세트 보너스 정의 캐시 (서버 기동 후 최초 1회 로딩, 초기화 명령으로 갱신 가능)
    private volatile List<HashMap<String,Object>> setBonusDefsCache = null;

    private List<HashMap<String,Object>> getSetBonusDefsCached() {
        if (setBonusDefsCache != null) return setBonusDefsCache;
        try { setBonusDefsCache = botNewService.selectAllSetBonusDefs(); } catch (Exception ignore) {}
        return setBonusDefsCache;
    }

    /** JSP 뷰 페이지 */
    @GetMapping("/equip-sim-view")
    public String equipSimPage() {
        return "nonsession/loa/equip_sim_view";
    }

    /**
     * 초기 데이터: 유저 기본 스탯(레벨 기반 재계산) + 전체 아이템 + jobList
     */
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

        List<HashMap<String,Object>> allItems = new ArrayList<>();
        try { allItems = botNewService.selectAllItemsWithOwned(userName); } catch (Exception ignore) {}

        List<String> jobList = new ArrayList<>(MiniGameUtil.JOB_DEFS.keySet());

        Map<String,Object> userMap = new LinkedHashMap<>();
        userMap.put("userName",    userName);
        // 레벨 기반으로 재계산한 기본 스탯 반환 (DB 값이 오래된 경우 대비)
        userMap.put("atkMin",      MiniGameUtil.calcBaseAtkMin(u.lv));
        userMap.put("atkMax",      MiniGameUtil.calcBaseAtkMax(u.lv));
        userMap.put("hpMax",       MiniGameUtil.calcBaseHpMax(u.lv));
        userMap.put("hpRegen",     MiniGameUtil.calcBaseHpRegen(u.lv));
        userMap.put("critRate",    MiniGameUtil.calcBaseCritRate(u.lv));
        userMap.put("critDmg",     u.critDmg);
        userMap.put("lv",          u.lv);
        userMap.put("job",         u.job == null ? "" : u.job.trim());
        userMap.put("nightmareYn", u.nightmareYn);

        result.put("user",         userMap);
        result.put("hunterGrade",  hunterGrade);
        result.put("hellNerfMult", hellNerfMult);
        result.put("allItems",     allItems);
        result.put("jobList",      jobList);

        return ResponseEntity.ok(result);
    }

    /**
     * 실시간 스탯 계산 (BossAttackController.calcUserBattleContext 와 동일 흐름)
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

        // ── 1. 레벨 기반 기본 스탯 ──────────────────────────────────────
        int baseAtkMin  = MiniGameUtil.calcBaseAtkMin(u.lv);
        int baseAtkMax  = MiniGameUtil.calcBaseAtkMax(u.lv);
        int baseHpMax   = MiniGameUtil.calcBaseHpMax(u.lv);
        int baseRegen   = MiniGameUtil.calcBaseHpRegen(u.lv);
        int baseCrit    = MiniGameUtil.calcBaseCritRate(u.lv);
        int baseCritDmg = u.critDmg; // 크리 데미지는 레벨 기반 아님

        // ── 2. 장착 아이템 스탯 합산 + 세트ID 카운트 ─────────────────────
        int mktAtkMin=0, mktAtkMax=0, mktCrit=0, mktRegen=0;
        int mktHpMax=0, mktCritDmg=0, mktHpMaxRate=0, mktAtkMaxRate=0;
        Set<Integer> bossEquipped = new HashSet<>();
        Map<String,Integer> setIdCounts = new LinkedHashMap<>();

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

            // 세트 효과 계산용 SET_ID 카운트 (아이템 단위, qty 무관 1개로)
            String setId = Objects.toString(item.get("SET_ID"), "");
            if (!setId.isEmpty()) setIdCounts.merge(setId, 1, Integer::sum);
        }

        // ── 3. 세트 효과 (시뮬 장착 아이템 기준, BossAttackController 동일 방식) ──
        int setAtkFinalRate = 0, setCritFinalRate = 0, setCooldownReduce = 0, setEvasionRate = 0;
        if (!setIdCounts.isEmpty()) {
            List<HashMap<String,Object>> setBonusDefs = getSetBonusDefsCached();
            if (setBonusDefs != null) {
                for (HashMap<String,Object> b : setBonusDefs) {
                    String setId = Objects.toString(b.get("SET_ID"), "");
                    int reqCnt   = safeInt(b.get("REQUIRED_CNT"));
                    Integer owned = setIdCounts.get(setId);
                    if (owned == null || owned < reqCnt) continue;
                    String bt = Objects.toString(b.get("BONUS_TYPE"), "");
                    int    bv = safeInt(b.get("BONUS_VALUE"));
                    switch (bt) {
                        case "ATK_MIN":         mktAtkMin    += bv; break;
                        case "ATK_MAX":         mktAtkMax    += bv; break;
                        case "HP_MAX":          mktHpMax     += bv; break;
                        case "ATK_CRI":         mktCrit      += bv; break;
                        case "CRI_DMG":         mktCritDmg   += bv; break;
                        case "HP_REGEN":        mktRegen     += bv; break;
                        case "ATK_FINAL_RATE":  setAtkFinalRate  += bv; break;
                        case "CRIT_FINAL_RATE": setCritFinalRate += bv; break;
                        case "COOLDOWN_REDUCE": setCooldownReduce += bv; break;
                        case "EVASION_RATE":    setEvasionRate   += bv; break;
                        default: break;
                    }
                }
            }
        }

        // ── 4. 어둠사냥꾼: 아이템 HP/Regen ×1.25 (hunter 보너스 이전 적용) ──
        if ("어둠사냥꾼".equals(job)) {
            mktHpMax  = (int) Math.round(mktHpMax  * 1.25);
            mktRegen  = (int) Math.round(mktRegen  * 1.25);
        }

        // ── 5. 헌터 등급 + 보너스 (BossAttackController와 동일 로직) ───────
        String hunterGrade = "F";
        int hunterAtk=0, hunterHp=0, hunterRegen=0, hunterCritDmg=0;
        try {
            AttackDeathStat ads = botNewService.selectAttackDeathStats(userName, "");
            int adjAtk = ads == null ? 0 : ads.totalAttacks + (ads.hunterAttacks * 2);
            int adjDth = ads == null ? 0 : ads.totalDeaths  + (ads.hunterAttacks / 2);
            int adjDrp = 0, darkQty = 0, grayQty = 0;

            // 캐시 의존 없이 DB 직접 조회
            List<HashMap<String,Object>> dropsData = botNewService.selectTotalDropItems(userName);
            if (dropsData != null) {
                for (HashMap<String,Object> d : dropsData) {
                    Object v = d.get("TOTAL_QTY");
                    String type = Objects.toString(d.get("GAIN_TYPE"), "");
                    if (v instanceof Number) {
                        int qty = ((Number)v).intValue();
                        adjDrp += qty;
                        if ("DROP5".equals(type)) darkQty += qty;       // 어둠
                        else if ("DROP9".equals(type)) grayQty += qty;  // 음양
                    }
                }
            }

            // 다크몹/음양 보너스 (S 이하에만 적용, SS이상 제외)
            int itemBonus = darkQty * 1 + grayQty * 3;
            String gradeWithout = calcGrade(adjAtk, adjDrp, adjDth);
            boolean bonusEligible = !"SSS".equals(gradeWithout) && !gradeWithout.startsWith("SS");
            if (bonusEligible) {
                int k = adjAtk + itemBonus, dr = adjDrp + itemBonus, de = adjDth + itemBonus;
                String gWith = calcGrade(k, dr, de);
                if ("SSS".equals(gWith)) {
                    gWith = "SS"; // SSS 상한 → SS로 클램프
                } else {
                    adjAtk = k; adjDrp = dr; adjDth = de;
                }
                hunterGrade = gWith;
            } else {
                hunterGrade = gradeWithout;
            }

            if ("헌터".equals(job)) {
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
                hunterAtk     = Math.min(adjAtk  / 5,  atkCap);
                hunterHp      = Math.min(adjDrp  / 5,  hpCap);
                hunterRegen   = Math.min(adjDrp  / 50, regenCap);
                hunterCritDmg = Math.min(adjDth  / 5,  criCap);
            }
        } catch (Exception ignore) {}

        mktAtkMin  += hunterAtk;
        mktAtkMax  += hunterAtk;
        mktHpMax   += hunterHp;
        mktRegen   += hunterRegen;
        mktCritDmg += hunterCritDmg;

        // ── 6. 기본 합산 ──────────────────────────────────────────────────
        int atkMin  = baseAtkMin + mktAtkMin;
        int atkMax  = baseAtkMax + mktAtkMax;
        int hpMax   = baseHpMax  + mktHpMax;
        int regen   = baseRegen  + mktRegen;
        int crit    = baseCrit   + mktCrit;
        int critDmg = baseCritDmg + mktCritDmg;

        // ── 7. 직업 보너스 (BossAttackController와 동일 순서) ─────────────
        if ("검성".equals(job) || "용사".equals(job)) hpMax += baseHpMax * 2;
        if ("흡혈귀".equals(job)) regen = 0;

        if ("곰".equals(job)) {
            int atkSum  = atkMin + atkMax;
            int critMul = baseCritDmg + mktCritDmg;
            hpMax   = hpMax + (atkSum * critMul / 100);
            atkMin  = hpMax; atkMax = hpMax;
            crit    = 0; critDmg = 0;
        }

        // ── 8. 비율 보너스 ────────────────────────────────────────────────
        hpMax  += (int) Math.round(hpMax  * mktHpMaxRate  / 100.0);
        atkMin += (int) Math.round(atkMin * mktAtkMaxRate  / 100.0);
        atkMax += (int) Math.round(atkMax * mktAtkMaxRate  / 100.0);

        // ── 9. 헬너프 ─────────────────────────────────────────────────────
        if (hellMode) {
            double mult = MiniGameUtil.getHellNerfMult(hunterGrade);
            if (bossEquipped.contains(7007)) mult = Math.max(0.0, mult + 0.03);
            atkMin  = (int) Math.round(atkMin  * mult);
            atkMax  = (int) Math.round(atkMax  * mult);
            hpMax   = (int) Math.round(hpMax   * mult);
            crit    = (int) Math.round(crit    * mult);
            critDmg = (int) Math.round(critDmg * mult);
        }

        // ── 10. [7009] 진화형 무기: 레벨당 공격력 +150 ───────────────────
        if (bossEquipped.contains(7009)) {
            atkMin += u.lv * 150;
            atkMax += u.lv * 150;
        }

        // ── 11. 세트 효과: 최종 비율 보너스 (헬너프 이후, BossAttackController 동일) ──
        if (setAtkFinalRate > 0) {
            atkMin += (int) Math.round(atkMin * setAtkFinalRate / 100.0);
            atkMax += (int) Math.round(atkMax * setAtkFinalRate / 100.0);
        }
        if (setCritFinalRate > 0) {
            crit += (int) Math.round(crit * setCritFinalRate / 100.0);
        }

        // ── 12. 헌터: 치피전환 (치명타 100% 초과분 → 치명타 데미지 전환) ──
        int criConvert = 0;
        if ("헌터".equals(job) && crit > 100) {
            criConvert = crit - 100;
            critDmg   += criConvert;
            crit       = 100;
        }

        // ── 13. 직업 데미지 배율 ──────────────────────────────────────────
        double jobDmgMul = getJobDmgMul(job);
        int effAtkMin = (int) Math.round(atkMin * jobDmgMul);
        int effAtkMax = (int) Math.round(atkMax * jobDmgMul);

        int criCap = getCriCapForGrade(hunterGrade);

        result.put("atkMin",          atkMin);
        result.put("atkMax",          atkMax);
        result.put("effAtkMin",       effAtkMin);
        result.put("effAtkMax",       effAtkMax);
        result.put("jobDmgMul",       jobDmgMul);
        result.put("hpMax",           hpMax);
        result.put("regen",           regen);
        result.put("crit",            crit);
        result.put("critDmg",         critDmg);
        result.put("criConvert",      criConvert);
        result.put("criCap",          criCap);
        result.put("hunterGrade",     hunterGrade);
        result.put("job",             job);
        result.put("setAtkFinalRate", setAtkFinalRate);
        result.put("setCritFinalRate",setCritFinalRate);
        result.put("setCooldown",     setCooldownReduce);
        result.put("setEvasion",      setEvasionRate);

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════════════

    /** BossAttackController의 jobDmgMul 값과 동일하게 유지 */
    private double getJobDmgMul(String job) {
        if (job == null) return 1.0;
        switch (job) {
            case "궁수":    return 3.0;
            case "사냥꾼":  return 3.0;
            case "검성":    return 2.2;
            case "저격수":  return 2.0;
            case "음양사":  return 1.6;
            case "처단자":  return 1.4;
            case "용사":    return 1.4;
            case "전사":    return 1.4;
            case "어쎄신":  return 1.3;
            case "제너럴":  return 1.2;
            case "복수자":  return 0.2;
            default:        return 1.0;
        }
    }

    /** 헌터 등급별 치명타 데미지 상한 */
    private int getCriCapForGrade(String grade) {
        if (grade == null) return 5;
        switch (grade) {
            case "SSS": return 60;
            case "SS":  return 45;
            case "S":   return 30;
            case "A+":  return 27;
            case "A":   return 25;
            case "B+":  return 22;
            case "B":   return 20;
            case "C+":  return 16;
            case "C":   return 15;
            case "D+":  return 11;
            case "D":   return 10;
            default:    return 5;
        }
    }

    /**
     * 헌터 등급 계산 (BossAttackController / LoaHunterRankViewController와 동일 로직)
     * - DB 직접 조회 (캐시 불필요)
     * - 다크몹(DROP5) / 음양(DROP9) 보정 포함
     */
    private String resolveHunterGrade(String userName) {
        try {
            AttackDeathStat ads = botNewService.selectAttackDeathStats(userName, "");
            int totalAttacks = ads == null ? 0 : ads.totalAttacks + (ads.hunterAttacks * 2);
            int totalDeaths  = ads == null ? 0 : ads.totalDeaths  + (ads.hunterAttacks / 2);

            int totalDrops = 0, darkQty = 0, grayQty = 0;
            List<HashMap<String,Object>> drops = botNewService.selectTotalDropItems(userName);
            if (drops != null) {
                for (HashMap<String,Object> d : drops) {
                    Object v = d.get("TOTAL_QTY");
                    String type = Objects.toString(d.get("GAIN_TYPE"), "");
                    if (v instanceof Number) {
                        int qty = ((Number)v).intValue();
                        totalDrops += qty;
                        if ("DROP5".equals(type)) darkQty += qty;
                        else if ("DROP9".equals(type)) grayQty += qty;
                    }
                }
            }

            int itemBonus = darkQty * 1 + grayQty * 3;
            String gradeWithout = calcGrade(totalAttacks, totalDrops, totalDeaths);
            boolean bonusEligible = !"SSS".equals(gradeWithout) && !gradeWithout.startsWith("SS");
            if (bonusEligible) {
                int k = totalAttacks + itemBonus, dr = totalDrops + itemBonus, de = totalDeaths + itemBonus;
                String gWith = calcGrade(k, dr, de);
                return "SSS".equals(gWith) ? "SS" : gWith;
            }
            return gradeWithout;
        } catch (Exception e) { return "F"; }
    }

    /** LoaHunterRankViewController / BossAttackController 와 동일한 등급 계산 */
    private String calcGrade(int atk, int drop, int dth) {
        if (atk >= 50000 && drop >= 100000 && dth >= 1200) return "SSS";
        if (atk >= 40000 && drop >=  50000 && dth >=  700) return "SS";
        if (atk >= 30000 && drop >=  30000 && dth >=  500) return "S";
        String g;
        g = checkPlus(atk, drop, dth, 20000, 20000, 400, "A"); if (g != null) return g;
        g = checkPlus(atk, drop, dth, 10000, 10000, 200, "B"); if (g != null) return g;
        g = checkPlus(atk, drop, dth,  5000,  5000, 100, "C"); if (g != null) return g;
        g = checkPlus(atk, drop, dth,  1000,  1000,  50, "D"); if (g != null) return g;
        return "F";
    }

    /** 3조건 중 3개 충족 → base+, 2개 충족 → base, 1개 이하 → null */
    private String checkPlus(int atk, int drop, int dth,
                              int atkReq, int dropReq, int dthReq, String base) {
        int m = (atk >= atkReq ? 1 : 0) + (drop >= dropReq ? 1 : 0) + (dth >= dthReq ? 1 : 0);
        if (m == 3) return base + "+";
        if (m == 2) return base;
        return null;
    }

    private int safeInt(Object o) {
        if (o instanceof Number) return ((Number)o).intValue();
        if (o instanceof String) { try { return Integer.parseInt((String)o); } catch (Exception ignored) {} }
        return 0;
    }
}
