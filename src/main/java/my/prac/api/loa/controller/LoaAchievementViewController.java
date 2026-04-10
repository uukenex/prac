package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.game.dto.AchievementConfig;
import my.prac.core.prjbot.service.BotNewService;

/**
 * 업적 현황 뷰 컨트롤러
 * - /loa/achievement-view  : JSP 페이지
 * - /loa/api/achievements  : 업적 데이터 JSON API
 */
@Controller
@RequestMapping("/loa")
public class LoaAchievementViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    @GetMapping("/achievement-view")
    public String achievementViewPage() {
        return "nonsession/loa/achievement_view";
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/api/achievements")
    @ResponseBody
    public ResponseEntity<?> getAchievements(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 1. 기본 업적 목록 (TBOT_POINT_RANK CMD LIKE 'ACHV_%')
        List<HashMap<String, Object>> achvList = new ArrayList<>();
        try {
            achvList = botNewService.selectAchievementsByUser(userName, "");
            if (achvList == null) achvList = new ArrayList<>();
        } catch (Exception ignore) {}

        // 2. 업적 파싱 및 그룹화
        HashMap<String, Object> grouped = parseAchievements(achvList);

        // 3. 기본 통계 - ACHV 업적 최대 임계값에서 도출
        int totalKills   = toInt(grouped.get("maxTotalKill"));
        int totalAttacks = toInt(grouped.get("maxAttack"));
        int totalDeaths  = toInt(grouped.get("maxDeath"));

        // 4. 드랍 아이템 수량 (실제 개수는 인벤토리에서)
        List<HashMap<String, Object>> drops = new ArrayList<>();
        try {
            drops = botNewService.selectTotalDropItems(userName);
            if (drops == null) drops = new ArrayList<>();
        } catch (Exception ignore) {}

        int totalDrops = 0, darkQty = 0, grayQty = 0, lightQty = 0;
        for (HashMap<String, Object> d : drops) {
            Object v = d.get("TOTAL_QTY");
            String type = Objects.toString(d.get("GAIN_TYPE"), "");
            if (v instanceof Number) {
                int qty = ((Number) v).intValue();
                totalDrops += qty;
                if (AchievementConfig.ITEM_TYPE_LIGHT.equals(type)) lightQty += qty;
                else if (AchievementConfig.ITEM_TYPE_DARK.equals(type)) darkQty += qty;
                else if (AchievementConfig.ITEM_TYPE_GRAY.equals(type)) grayQty += qty;
            }
        }

        // 5. 헬보스 업적
        int hellAtkCount   = 0;
        int hellClearCount = 0;
        try { hellAtkCount   = botNewService.selectHellBossAttackCount(userName); } catch (Exception ignore) {}
        try { hellClearCount = botNewService.selectHellBossClearCount(userName);  } catch (Exception ignore) {}

        // 6. 룰렛 업적
        int atkSuccessCnt = 0;
        int criSuccessCnt = 0;
        try {
            HashMap<String, Object> buff = botNewService.selectMaxDailyBuffStats(userName);
            if (buff != null) {
                atkSuccessCnt = toInt(buff.get("ATK_FULL_CNT"));
                criSuccessCnt = toInt(buff.get("CRI_FULL_CNT"));
            }
        } catch (Exception ignore) {}

        // 7. 몬스터별 킬 목록 (ACHV_KILL{threshold}_MON_{monNo} → 몬스터별 최대달성 킬수)
        List<HashMap<String, Object>> monKillList = (List<HashMap<String, Object>>) grouped.remove("_monKillList");
        if (monKillList == null) monKillList = new ArrayList<>();

        // 8. 학살자 시즌 목록 (ACHV_SLAYER_SEASON_{YYYYMMDD}_{MON_NO})
        List<HashMap<String, Object>> slayerSeasons = buildSeasonList(
                (Map<String, Integer>) grouped.remove("_slayerSeasonMap"), "monCount");

        // 9. 직업마스터 시즌 목록 (ACHV_MASTER_SEASON_{YYYYMMDD}_{JOB_NO})
        List<HashMap<String, Object>> masterSeasons = buildSeasonList(
                (Map<String, Integer>) grouped.remove("_masterSeasonMap"), "count");

        // 10. 최초 토벌 목록 (ACHV_FIRST_CLEAR_MON_{MON_NO})
        List<Integer> firstClearList = (List<Integer>) grouped.remove("_firstClearList");
        if (firstClearList == null) firstClearList = new ArrayList<>();

        // 11. 공개 처치 방송 목록 (ACHV_CLEAR_BROADCAST_MON_{MON_NO})
        List<Integer> broadcastList = (List<Integer>) grouped.remove("_broadcastList");
        if (broadcastList == null) broadcastList = new ArrayList<>();

        // 12. 특수 업적 목록 (ACHV_{0001~0090})
        List<String> specialList = (List<String>) grouped.remove("_specialList");
        if (specialList == null) specialList = new ArrayList<>();

        // 13. 전체 CMD 텍스트 목록
        List<String> allCmds = new ArrayList<>();
        for (HashMap<String, Object> row : achvList) {
            allCmds.add(Objects.toString(row.get("CMD"), ""));
        }

        // 결과 조합
        result.put("userName",       userName);
        result.put("totalAchv",      achvList.size());
        result.put("grouped",        grouped);
        result.put("stats",          mapOf("kills", totalKills, "attacks", totalAttacks, "drops", totalDrops,
                                           "deaths", totalDeaths, "lightQty", lightQty, "darkQty", darkQty, "grayQty", grayQty));
        result.put("hellBoss",       mapOf("atkCount", hellAtkCount, "clearCount", hellClearCount));
        result.put("roulette",       mapOf("atkSuccessCnt", atkSuccessCnt, "criSuccessCnt", criSuccessCnt));
        result.put("monKillList",    monKillList);
        result.put("slayerSeasons",  slayerSeasons);
        result.put("masterSeasons",  masterSeasons);
        result.put("firstClearList", firstClearList);
        result.put("broadcastList",  broadcastList);
        result.put("specialList",    specialList);
        result.put("allCmds",        allCmds);

        return ResponseEntity.ok(result);
    }

    /** 업적 목록 파싱 → 카테고리별 그룹화 */
    private HashMap<String, Object> parseAchievements(List<HashMap<String, Object>> achvList) {
        // --- 정규식 패턴 정의 ---
        Pattern P_TOTAL_KILL   = Pattern.compile("^ACHV_KILL_TOTAL_(\\d+)$");
        Pattern P_NM_KILL      = Pattern.compile("^ACHV_KILL_NIGHTMARE_TOTAL_(\\d+)$");   // fix: NM→NIGHTMARE
        Pattern P_HELL_KILL    = Pattern.compile("^ACHV_KILL_HELL_TOTAL_(\\d+)$");
        Pattern P_MON_KILL     = Pattern.compile("^ACHV_KILL(\\d+)_MON_(\\d+)$");          // fix: KILL{X}_MON_{Y}
        Pattern P_DEATH        = Pattern.compile("^ACHV_DEATH_(\\d+)$");
        Pattern P_ATTACK       = Pattern.compile("^ACHV_ATTACK_TOTAL_(\\d+)$");
        Pattern P_LIGHT        = Pattern.compile("^ACHV_LIGHT_ITEM_(\\d+)$");
        Pattern P_DARK         = Pattern.compile("^ACHV_DARK_ITEM_(\\d+)$");
        Pattern P_GRAY         = Pattern.compile("^ACHV_GRAY_ITEM_(\\d+)$");
        Pattern P_SKILL        = Pattern.compile("^ACHV_JOB_SKILL_(.+)_(\\d+)$");
        Pattern P_SELL         = Pattern.compile("^ACHV_SHOP_SELL_(\\d+)$");
        Pattern P_POTION       = Pattern.compile("^ACHV_POTION_USE_(\\d+)$");
        Pattern P_BAG          = Pattern.compile("^ACHV_BAG_(\\d+)$");
        Pattern P_HELL_ATK     = Pattern.compile("^ACHV_HELL_ATK_(\\d+)$");
        Pattern P_HELL_CLEAR   = Pattern.compile("^ACHV_HELL_CLEAR_(\\d+)$");
        Pattern P_SLAYER       = Pattern.compile("^ACHV_SLAYER_SEASON_(\\d{8})_(\\d+)$");
        Pattern P_MASTER       = Pattern.compile("^ACHV_MASTER_SEASON_(\\d{8})_(\\d+)$"); // fix: JOB_MASTER→MASTER
        Pattern P_ROULETTE_ATK = Pattern.compile("^ACHV_ROULETTE_ATK_(\\d+)$");
        Pattern P_ROULETTE_CRI = Pattern.compile("^ACHV_ROULETTE_CRI_(\\d+)$");
        Pattern P_FIRST_CLEAR  = Pattern.compile("^ACHV_FIRST_CLEAR_MON_(\\d+)$");        // new
        Pattern P_BROADCAST    = Pattern.compile("^ACHV_CLEAR_BROADCAST_MON_(\\d+)$");    // new
        Pattern P_SPECIAL      = Pattern.compile("^ACHV_(\\d{4})$");                       // new

        // --- 수집 자료구조 ---
        Map<String, Integer> totalKills  = new LinkedHashMap<>();
        Map<String, Integer> nmKills     = new LinkedHashMap<>();
        Map<String, Integer> hellKills   = new LinkedHashMap<>();
        Map<Integer, Integer> monKillMap = new TreeMap<>();  // monNo → maxThreshold
        int monKillCount = 0;
        Map<String, Integer> deaths      = new LinkedHashMap<>();
        Map<String, Integer> attacks     = new LinkedHashMap<>();
        Map<String, Integer> lightItems  = new LinkedHashMap<>();
        Map<String, Integer> darkItems   = new LinkedHashMap<>();
        Map<String, Integer> grayItems   = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> skills = new LinkedHashMap<>();
        Map<String, Integer> sells       = new LinkedHashMap<>();
        Map<String, Integer> potions     = new LinkedHashMap<>();
        Map<String, Integer> bags        = new LinkedHashMap<>();
        Map<String, Integer> hellAtk     = new LinkedHashMap<>();
        Map<String, Integer> hellClear   = new LinkedHashMap<>();
        Map<String, Integer> slayer      = new TreeMap<>();  // date → monCount
        Map<String, Integer> master      = new TreeMap<>();  // date → jobCount
        Map<String, Integer> rouletteAtk = new LinkedHashMap<>();
        Map<String, Integer> rouletteCri = new LinkedHashMap<>();
        Map<Integer, Integer> firstClear = new TreeMap<>();  // monNo → 1
        Map<Integer, Integer> broadcast  = new TreeMap<>();  // monNo → 1
        List<String> specialList         = new ArrayList<>();

        for (HashMap<String, Object> row : achvList) {
            String cmd = Objects.toString(row.get("CMD"), "");
            Matcher m;
            if ((m = P_TOTAL_KILL.matcher(cmd)).matches())   { totalKills.put(m.group(1), 1); continue; }
            if ((m = P_NM_KILL.matcher(cmd)).matches())      { nmKills.put(m.group(1), 1); continue; }
            if ((m = P_HELL_KILL.matcher(cmd)).matches())    { hellKills.put(m.group(1), 1); continue; }
            if ((m = P_MON_KILL.matcher(cmd)).matches()) {
                int thr = Integer.parseInt(m.group(1));
                int monNo = Integer.parseInt(m.group(2));
                monKillMap.merge(monNo, thr, Math::max);
                monKillCount++;
                continue;
            }
            if ((m = P_DEATH.matcher(cmd)).matches())        { deaths.put(m.group(1), 1); continue; }
            if ((m = P_ATTACK.matcher(cmd)).matches())       { attacks.put(m.group(1), 1); continue; }
            if ((m = P_LIGHT.matcher(cmd)).matches())        { lightItems.put(m.group(1), 1); continue; }
            if ((m = P_DARK.matcher(cmd)).matches())         { darkItems.put(m.group(1), 1); continue; }
            if ((m = P_GRAY.matcher(cmd)).matches())         { grayItems.put(m.group(1), 1); continue; }
            if ((m = P_SKILL.matcher(cmd)).matches())        { skills.computeIfAbsent(m.group(1), k -> new LinkedHashMap<>()).put(m.group(2), 1); continue; }
            if ((m = P_SELL.matcher(cmd)).matches())         { sells.put(m.group(1), 1); continue; }
            if ((m = P_POTION.matcher(cmd)).matches())       { potions.put(m.group(1), 1); continue; }
            if ((m = P_BAG.matcher(cmd)).matches())          { bags.put(m.group(1), 1); continue; }
            if ((m = P_HELL_ATK.matcher(cmd)).matches())     { hellAtk.put(m.group(1), 1); continue; }
            if ((m = P_HELL_CLEAR.matcher(cmd)).matches())   { hellClear.put(m.group(1), 1); continue; }
            if ((m = P_SLAYER.matcher(cmd)).matches())       { slayer.merge(m.group(1), 1, Integer::sum); continue; }
            if ((m = P_MASTER.matcher(cmd)).matches())       { master.merge(m.group(1), 1, Integer::sum); continue; }
            if ((m = P_ROULETTE_ATK.matcher(cmd)).matches()) { rouletteAtk.put(m.group(1), 1); continue; }
            if ((m = P_ROULETTE_CRI.matcher(cmd)).matches()) { rouletteCri.put(m.group(1), 1); continue; }
            if ((m = P_FIRST_CLEAR.matcher(cmd)).matches())  { firstClear.put(Integer.parseInt(m.group(1)), 1); continue; }
            if ((m = P_BROADCAST.matcher(cmd)).matches())    { broadcast.put(Integer.parseInt(m.group(1)), 1); continue; }
            if ((m = P_SPECIAL.matcher(cmd)).matches())      { specialList.add(m.group(1)); continue; }
        }

        // --- 몬스터별 킬 리스트 빌드 (monNo 순 정렬) ---
        List<HashMap<String, Object>> monKillList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : monKillMap.entrySet()) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",   e.getKey());
            item.put("maxKill", e.getValue());
            monKillList.add(item);
        }

        // --- 결과 ---
        HashMap<String, Object> g = new HashMap<>();
        // 업적 개수
        g.put("totalKills",   totalKills.size());
        g.put("nmKills",      nmKills.size());
        g.put("hellKills",    hellKills.size());
        g.put("monKills",     monKillCount);
        g.put("deaths",       deaths.size());
        g.put("attacks",      attacks.size());
        g.put("lightItems",   lightItems.size());
        g.put("darkItems",    darkItems.size());
        g.put("grayItems",    grayItems.size());
        g.put("skills",       skills.size());
        g.put("sells",        sells.size());
        g.put("potions",      potions.size());
        g.put("bags",         bags.size());
        g.put("hellAtk",      hellAtk.size());
        g.put("hellClear",    hellClear.size());
        g.put("slayer",       slayer.values().stream().mapToInt(Integer::intValue).sum());
        g.put("master",       master.values().stream().mapToInt(Integer::intValue).sum());
        g.put("rouletteAtk",  rouletteAtk.size());
        g.put("rouletteCri",  rouletteCri.size());
        g.put("firstClear",   firstClear.size());
        g.put("broadcast",    broadcast.size());
        g.put("special",      specialList.size());
        // 최대 달성 임계값
        g.put("maxTotalKill",  totalKills.isEmpty()  ? 0 : maxInt(totalKills));
        g.put("maxNmKill",     nmKills.isEmpty()     ? 0 : maxInt(nmKills));
        g.put("maxHellKill",   hellKills.isEmpty()   ? 0 : maxInt(hellKills));
        g.put("maxDeath",      deaths.isEmpty()      ? 0 : maxInt(deaths));
        g.put("maxAttack",     attacks.isEmpty()     ? 0 : maxInt(attacks));
        g.put("maxLightItem",  lightItems.isEmpty()  ? 0 : maxInt(lightItems));
        g.put("maxDarkItem",   darkItems.isEmpty()   ? 0 : maxInt(darkItems));
        g.put("maxGrayItem",   grayItems.isEmpty()   ? 0 : maxInt(grayItems));
        g.put("maxSell",       sells.isEmpty()       ? 0 : maxInt(sells));
        g.put("maxPotion",     potions.isEmpty()     ? 0 : maxInt(potions));
        g.put("maxBag",        bags.isEmpty()        ? 0 : maxInt(bags));
        g.put("maxHellAtk",    hellAtk.isEmpty()     ? 0 : maxInt(hellAtk));
        g.put("maxHellClear",  hellClear.isEmpty()   ? 0 : maxInt(hellClear));
        // 내부 전달용 (getAchievements에서 remove해서 사용)
        g.put("_monKillList",     monKillList);
        g.put("_slayerSeasonMap", slayer);
        g.put("_masterSeasonMap", master);
        g.put("_firstClearList",  new ArrayList<>(firstClear.keySet()));
        g.put("_broadcastList",   new ArrayList<>(broadcast.keySet()));
        g.put("_specialList",     specialList);
        return g;
    }

    /** {date → count} 맵을 [{season, countKey}] 리스트로 변환 (날짜 순 정렬) */
    private List<HashMap<String, Object>> buildSeasonList(Map<String, Integer> seasonMap, String countKey) {
        List<HashMap<String, Object>> list = new ArrayList<>();
        if (seasonMap == null) return list;
        for (Map.Entry<String, Integer> e : seasonMap.entrySet()) {
            HashMap<String, Object> s = new HashMap<>();
            s.put("season",  e.getKey());
            s.put(countKey,  e.getValue());
            list.add(s);
        }
        return list;
    }

    private int maxInt(Map<String, Integer> map) {
        return map.keySet().stream()
                .mapToInt(k -> { try { return Integer.parseInt(k.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } })
                .max().orElse(0);
    }

    private HashMap<String, Object> mapOf(Object... kv) {
        HashMap<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return Integer.parseInt(Objects.toString(o, "0")); } catch (Exception e) { return 0; }
    }
}
