package my.prac.api.loa.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import my.prac.core.game.dto.AttackDeathStat;
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

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping("/achievement-view")
    public String achievementViewPage() {
        return "nonsession/loa/achievement_view";
    }

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

        // 3. 기본 통계
        AttackDeathStat ads = null;
        try { ads = botNewService.selectAttackDeathStats(userName, ""); } catch (Exception ignore) {}

        int totalKills  = ads == null ? 0 : ads.totalAttacks;
        int totalDeaths = ads == null ? 0 : ads.totalDeaths;

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
                if (AchievementConfig.ITEM_TYPE_LIGHT.equals(type)) lightQty += qty; // DROP3
                else if (AchievementConfig.ITEM_TYPE_DARK.equals(type)) darkQty += qty;  // DROP5
                else if (AchievementConfig.ITEM_TYPE_GRAY.equals(type)) grayQty += qty;  // DROP9
            }
        }

        // 4. 헬보스 업적 (6-1)
        int hellAtkCount   = 0;
        int hellClearCount = 0;
        try { hellAtkCount   = botNewService.selectHellBossAttackCount(userName); } catch (Exception ignore) {}
        try { hellClearCount = botNewService.selectHellBossClearCount(userName);  } catch (Exception ignore) {}

        // 5. 룰렛 업적 (6-1)
        int maxAtkBonus    = 0;
        int maxCriDmgBonus = 0;
        try {
            HashMap<String, Object> buff = botNewService.selectMaxDailyBuffStats(userName);
            if (buff != null) {
                maxAtkBonus    = toInt(buff.get("MAX_ATK_BONUS"));
                maxCriDmgBonus = toInt(buff.get("MAX_CRI_DMG_BONUS"));
            }
        } catch (Exception ignore) {}

        // 6. 직업마스터 업적 시즌 목록 (6-1)
        List<String> jobMasterSeasons = new ArrayList<>();
        try {
            List<HashMap<String, Object>> masterRows = botNewService.selectJobMasterSeasons(userName);
            if (masterRows != null) {
                for (HashMap<String, Object> row : masterRows) {
                    String d = Objects.toString(row.get("SEASON_KEY"), "");
                    if (!d.isEmpty()) jobMasterSeasons.add(d + "_" + row.get("JOB"));
                }
            }
        } catch (Exception ignore) {}

        // 7. 몬스터 학살자 시즌 달성 목록 (6-1)
        List<HashMap<String, Object>> slayerSeasons = buildSlayerSeasons(userName);

        // 결과 조합
        result.put("userName",   userName);
        result.put("totalAchv",  achvList.size());
        result.put("grouped",    grouped);
        result.put("stats",      mapOf("kills", totalKills, "drops", totalDrops, "deaths", totalDeaths,
                                       "lightQty", lightQty, "darkQty", darkQty, "grayQty", grayQty));
        result.put("hellBoss",   mapOf("atkCount", hellAtkCount, "clearCount", hellClearCount));
        result.put("roulette",   mapOf("maxAtkBonus", maxAtkBonus, "maxCriDmgBonus", maxCriDmgBonus));
        result.put("jobMasterSeasons",  jobMasterSeasons);
        result.put("slayerSeasons",     slayerSeasons);

        return ResponseEntity.ok(result);
    }

    /** 업적 목록 파싱 → 카테고리별 그룹화 */
    private HashMap<String, Object> parseAchievements(List<HashMap<String, Object>> achvList) {
        Pattern P_TOTAL_KILL   = Pattern.compile("^ACHV_KILL_TOTAL_(\\d+)$");
        Pattern P_NM_KILL      = Pattern.compile("^ACHV_KILL_NM_TOTAL_(\\d+)$");
        Pattern P_HELL_KILL    = Pattern.compile("^ACHV_KILL_HELL_TOTAL_(\\d+)$");
        Pattern P_MON_KILL     = Pattern.compile("^ACHV_KILL_MON_(\\d+)_(\\d+)$");
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
        Pattern P_SLAYER       = Pattern.compile("^ACHV_SLAYER_SEASON_(\\d+)$");
        Pattern P_MASTER       = Pattern.compile("^ACHV_JOB_MASTER_SEASON_(\\d+)_(.+)$");
        Pattern P_ROULETTE_ATK = Pattern.compile("^ACHV_ROULETTE_ATK_(\\d+)$");
        Pattern P_ROULETTE_CRI = Pattern.compile("^ACHV_ROULETTE_CRI_(\\d+)$");

        Map<String, Integer> totalKills  = new LinkedHashMap<>();
        Map<String, Integer> nmKills     = new LinkedHashMap<>();
        Map<String, Integer> hellKills   = new LinkedHashMap<>();
        Map<String, Integer> monKills    = new LinkedHashMap<>();
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
        Map<String, Integer> slayer      = new LinkedHashMap<>();
        Map<String, Integer> master      = new LinkedHashMap<>();
        Map<String, Integer> rouletteAtk = new LinkedHashMap<>();
        Map<String, Integer> rouletteCri = new LinkedHashMap<>();

        for (HashMap<String, Object> row : achvList) {
            String cmd = Objects.toString(row.get("CMD"), "");
            Matcher m;
            if ((m = P_TOTAL_KILL.matcher(cmd)).matches())  { totalKills.put(m.group(1), 1); continue; }
            if ((m = P_NM_KILL.matcher(cmd)).matches())     { nmKills.put(m.group(1), 1); continue; }
            if ((m = P_HELL_KILL.matcher(cmd)).matches())   { hellKills.put(m.group(1), 1); continue; }
            if ((m = P_MON_KILL.matcher(cmd)).matches())    { monKills.put(m.group(1)+"_"+m.group(2), 1); continue; }
            if ((m = P_DEATH.matcher(cmd)).matches())       { deaths.put(m.group(1), 1); continue; }
            if ((m = P_ATTACK.matcher(cmd)).matches())      { attacks.put(m.group(1), 1); continue; }
            if ((m = P_LIGHT.matcher(cmd)).matches())       { lightItems.put(m.group(1), 1); continue; }
            if ((m = P_DARK.matcher(cmd)).matches())        { darkItems.put(m.group(1), 1); continue; }
            if ((m = P_GRAY.matcher(cmd)).matches())        { grayItems.put(m.group(1), 1); continue; }
            if ((m = P_SKILL.matcher(cmd)).matches())       { skills.computeIfAbsent(m.group(1), k->new LinkedHashMap<>()).put(m.group(2), 1); continue; }
            if ((m = P_SELL.matcher(cmd)).matches())        { sells.put(m.group(1), 1); continue; }
            if ((m = P_POTION.matcher(cmd)).matches())      { potions.put(m.group(1), 1); continue; }
            if ((m = P_BAG.matcher(cmd)).matches())         { bags.put(m.group(1), 1); continue; }
            if ((m = P_HELL_ATK.matcher(cmd)).matches())   { hellAtk.put(m.group(1), 1); continue; }
            if ((m = P_HELL_CLEAR.matcher(cmd)).matches()) { hellClear.put(m.group(1), 1); continue; }
            if ((m = P_SLAYER.matcher(cmd)).matches())      { slayer.put(m.group(1), 1); continue; }
            if ((m = P_MASTER.matcher(cmd)).matches())      { master.put(m.group(1)+"_"+m.group(2), 1); continue; }
            if ((m = P_ROULETTE_ATK.matcher(cmd)).matches()){ rouletteAtk.put(m.group(1), 1); continue; }
            if ((m = P_ROULETTE_CRI.matcher(cmd)).matches()){ rouletteCri.put(m.group(1), 1); continue; }
        }

        HashMap<String, Object> g = new HashMap<>();
        g.put("totalKills",  totalKills.size());
        g.put("nmKills",     nmKills.size());
        g.put("hellKills",   hellKills.size());
        g.put("monKills",    monKills.size());
        g.put("deaths",      deaths.size());
        g.put("attacks",     attacks.size());
        g.put("lightItems",  lightItems.size());
        g.put("darkItems",   darkItems.size());
        g.put("grayItems",   grayItems.size());
        g.put("skills",      skills.size());
        g.put("sells",       sells.size());
        g.put("potions",     potions.size());
        g.put("bags",        bags.size());
        g.put("hellAtk",     hellAtk.size());
        g.put("hellClear",   hellClear.size());
        g.put("slayer",      slayer.size());
        g.put("master",      master.size());
        g.put("rouletteAtk", rouletteAtk.size());
        g.put("rouletteCri", rouletteCri.size());
        // 최대 달성 임계값
        g.put("maxTotalKill",   totalKills.isEmpty()  ? 0 : maxInt(totalKills));
        g.put("maxNmKill",      nmKills.isEmpty()     ? 0 : maxInt(nmKills));
        g.put("maxHellKill",    hellKills.isEmpty()   ? 0 : maxInt(hellKills));
        g.put("maxDeath",       deaths.isEmpty()      ? 0 : maxInt(deaths));
        g.put("maxAttack",      attacks.isEmpty()     ? 0 : maxInt(attacks));
        g.put("maxLightItem",   lightItems.isEmpty()  ? 0 : maxInt(lightItems));
        g.put("maxDarkItem",    darkItems.isEmpty()   ? 0 : maxInt(darkItems));
        g.put("maxGrayItem",    grayItems.isEmpty()   ? 0 : maxInt(grayItems));
        g.put("maxSell",        sells.isEmpty()       ? 0 : maxInt(sells));
        g.put("maxPotion",      potions.isEmpty()     ? 0 : maxInt(potions));
        g.put("maxBag",         bags.isEmpty()        ? 0 : maxInt(bags));
        g.put("maxHellAtk",     hellAtk.isEmpty()     ? 0 : maxInt(hellAtk));
        g.put("maxHellClear",   hellClear.isEmpty()   ? 0 : maxInt(hellClear));
        return g;
    }

    /** 몬스터 학살자 시즌 달성 목록 (2026.03.01 기준 15일 단위) */
    private List<HashMap<String, Object>> buildSlayerSeasons(String userName) {
        List<HashMap<String, Object>> result = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate today = LocalDate.now();

        LocalDate seasonStart = start;
        while (seasonStart.isBefore(today)) {
            // 15일 단위 시즌 끝
            LocalDate mid = LocalDate.of(seasonStart.getYear(), seasonStart.getMonthValue(), 15);
            LocalDate endOfMonth = seasonStart.withDayOfMonth(seasonStart.lengthOfMonth());
            LocalDate seasonEnd = seasonStart.getDayOfMonth() <= 15 ? mid : endOfMonth;

            if (seasonEnd.isBefore(today)) {
                // 종료된 시즌 → 1위 조회
                String sKey = seasonStart.format(YYYYMMDD);
                String eKey = seasonEnd.format(YYYYMMDD);
                try {
                    HashMap<String, Object> q = new HashMap<>();
                    q.put("seasonStart", sKey);
                    q.put("seasonEnd",   eKey);
                    List<HashMap<String, Object>> ranks = botNewService.selectSlayerSeasonRank(q);
                    if (ranks != null && !ranks.isEmpty()) {
                        HashMap<String, Object> top = ranks.get(0);
                        String topUser = Objects.toString(top.get("USER_NAME"), "");
                        int    killCnt = toInt(top.get("KILL_CNT"));
                        int    monTypes = toInt(top.get("MON_TYPES"));
                        boolean isMine = userName.equalsIgnoreCase(topUser);
                        boolean achieved = isMine && monTypes >= 30;
                        HashMap<String, Object> row = new HashMap<>();
                        row.put("season",    sKey + "~" + eKey);
                        row.put("top",       topUser);
                        row.put("killCnt",   killCnt);
                        row.put("monTypes",  monTypes);
                        row.put("isMine",    isMine);
                        row.put("achieved",  achieved);
                        result.add(row);
                    }
                } catch (Exception ignore) {}
            }

            // 다음 시즌
            seasonStart = seasonEnd.plusDays(1);
        }
        return result;
    }

    private int maxInt(Map<String, Integer> map) {
        return map.keySet().stream().mapToInt(k -> { try { return Integer.parseInt(k.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }).max().orElse(0);
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
