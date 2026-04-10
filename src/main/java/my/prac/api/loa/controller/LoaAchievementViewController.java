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

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

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

        // 1. 몬스터 캐시 (DB 추가 조회 없음)
        Map<Integer, Monster> monMap = MiniGameUtil.MONSTER_CACHE;

        // 2. 기본 업적 목록 (TBOT_POINT_RANK CMD LIKE 'ACHV_%')
        List<HashMap<String, Object>> achvList = new ArrayList<>();
        try {
            achvList = botNewService.selectAchievementsByUser(userName, "");
            if (achvList == null) achvList = new ArrayList<>();
        } catch (Exception ignore) {}

        // 3. 업적 파싱 및 그룹화 (monMap 전달 → 몬스터명 포함)
        HashMap<String, Object> grouped = parseAchievements(achvList, monMap);

        // 4. 기본 통계 - ACHV 업적 최대 임계값에서 도출
        int totalKills   = toInt(grouped.get("maxTotalKill"));
        int totalAttacks = toInt(grouped.get("maxAttack"));

        // 5. 인벤토리 통합 조회 (드랍 수량 + 가방 수량) - 기존 메서드 활용
        HashMap<String, Object> invCounts = null;
        try { invCounts = botNewService.selectAchievementInventoryCounts(userName); } catch (Exception ignore) {}

        int lightQty  = invCounts != null ? toInt(invCounts.get("DROP3_QTY")) : 0;
        int darkQty   = invCounts != null ? toInt(invCounts.get("DROP5_QTY")) : 0;
        int grayQty   = invCounts != null ? toInt(invCounts.get("DROP9_QTY")) : 0;
        int bagTotal  = invCounts != null ? toInt(invCounts.get("BAG_COUNT"))  : 0;
        int totalDrops = lightQty + darkQty + grayQty;

        // 6. 헬보스 업적
        int hellAtkCount   = 0;
        int hellClearCount = 0;
        try { hellAtkCount   = botNewService.selectHellBossAttackCount(userName); } catch (Exception ignore) {}
        try { hellClearCount = botNewService.selectHellBossClearCount(userName);  } catch (Exception ignore) {}

        // 7. 룰렛 업적 (DAILY_BUFF 실수치)
        int atkSuccessCnt = 0;
        int criSuccessCnt = 0;
        try {
            HashMap<String, Object> buff = botNewService.selectMaxDailyBuffStats(userName);
            if (buff != null) {
                atkSuccessCnt = toInt(buff.get("ATK_FULL_CNT"));
                criSuccessCnt = toInt(buff.get("CRI_FULL_CNT"));
            }
        } catch (Exception ignore) {}

        // 8. 파싱 내부 데이터 추출
        List<HashMap<String, Object>> monKillList    = (List<HashMap<String, Object>>) grouped.remove("_monKillList");
        List<HashMap<String, Object>> slayerSeasons  = (List<HashMap<String, Object>>) grouped.remove("_slayerSeasons");
        List<HashMap<String, Object>> masterSeasons  = (List<HashMap<String, Object>>) grouped.remove("_masterSeasons");
        List<HashMap<String, Object>> firstClearList = (List<HashMap<String, Object>>) grouped.remove("_firstClearList");
        List<HashMap<String, Object>> broadcastList  = (List<HashMap<String, Object>>) grouped.remove("_broadcastList");
        List<String> specialList                     = (List<String>) grouped.remove("_specialList");
        List<HashMap<String, Object>> allCmds        = (List<HashMap<String, Object>>) grouped.remove("_allCmds");

        if (monKillList    == null) monKillList    = new ArrayList<>();
        if (slayerSeasons  == null) slayerSeasons  = new ArrayList<>();
        if (masterSeasons  == null) masterSeasons  = new ArrayList<>();
        if (firstClearList == null) firstClearList = new ArrayList<>();
        if (broadcastList  == null) broadcastList  = new ArrayList<>();
        if (specialList    == null) specialList    = new ArrayList<>();
        if (allCmds        == null) allCmds        = new ArrayList<>();

        // 9. 실제 전투 통계 (BATTLE_LOG 기반)
        AttackDeathStat ads = null;
        try { ads = botNewService.selectAttackDeathStats(userName, ""); } catch (Exception ignore) {}
        int realAttacks = ads != null ? ads.totalAttacks : 0;
        int realDeaths  = ads != null ? ads.totalDeaths  : 0;

        // 10. 몬스터별 실제 킬 (일반/빛/어둠 상세) - 기존 selectMonsterKillsForView
        List<HashMap<String, Object>> killViewRows = new ArrayList<>();
        try {
            List<HashMap<String, Object>> tmp = botNewService.selectMonsterKillsForView(userName);
            if (tmp != null) killViewRows = tmp;
        } catch (Exception ignore) {}

        // achievement lookup (monNo → maxKill threshold)
        Map<Integer, Integer> achvKillMap = new HashMap<>();
        for (HashMap<String, Object> item : monKillList) {
            achvKillMap.put(toInt(item.get("monNo")), toInt(item.get("maxKill")));
        }

        // monKillList를 실제 킬 데이터로 재구성 (업적 없는 몬스터도 포함)
        int realKills = 0, realNmKills = 0, realHellKills = 0;
        monKillList = new ArrayList<>();
        for (HashMap<String, Object> row : killViewRows) {
            int monNo     = toInt(row.get("MON_NO"));
            int killTotal = toInt(row.get("KILL_TOTAL"));
            int nm1Total  = toInt(row.get("NM1_TOTAL"));
            int nm2Total  = toInt(row.get("NM2_TOTAL"));
            realKills     += killTotal;
            realNmKills   += nm1Total;
            realHellKills += nm2Total;
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",      monNo);
            item.put("monName",    getMonName(monNo, monMap));
            item.put("killTotal",  killTotal);
            item.put("nm0Normal",  toInt(row.get("NM0_NORMAL")));
            item.put("nm0Light",   toInt(row.get("NM0_LIGHT")));
            item.put("nm0Dark",    toInt(row.get("NM0_DARK")));
            item.put("nm0Yinyang", toInt(row.get("NM0_YINYANG")));
            item.put("nm1Total",   nm1Total);
            item.put("nm2Total",   nm2Total);
            item.put("maxKill",    achvKillMap.getOrDefault(monNo, 0));
            monKillList.add(item);
        }

        // 11. 결과 조합
        result.put("userName",       userName);
        result.put("totalAchv",      achvList.size());
        result.put("grouped",        grouped);
        result.put("stats",          mapOf(
                "kills",        totalKills,   "attacks",       totalAttacks,
                "drops",        totalDrops,   "bagTotal",      bagTotal,
                "lightQty",     lightQty,     "darkQty",       darkQty,      "grayQty",       grayQty,
                "realAttacks",  realAttacks,  "realDeaths",    realDeaths,
                "realKills",    realKills,    "realNmKills",   realNmKills,  "realHellKills", realHellKills));
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

    /** 업적 목록 파싱 → 카테고리별 그룹화 + 내부 리스트 빌드 */
    private HashMap<String, Object> parseAchievements(
            List<HashMap<String, Object>> achvList,
            Map<Integer, Monster> monMap) {

        // ─── 정규식 패턴 ───
        Pattern P_TOTAL_KILL   = Pattern.compile("^ACHV_KILL_TOTAL_(\\d+)$");
        Pattern P_NM_KILL      = Pattern.compile("^ACHV_KILL_NIGHTMARE_TOTAL_(\\d+)$");
        Pattern P_HELL_KILL    = Pattern.compile("^ACHV_KILL_HELL_TOTAL_(\\d+)$");
        Pattern P_MON_KILL     = Pattern.compile("^ACHV_KILL(\\d+)_MON_(\\d+)$");
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
        Pattern P_MASTER       = Pattern.compile("^ACHV_MASTER_SEASON_(\\d{8})_(\\d+)$");
        Pattern P_ROULETTE_ATK = Pattern.compile("^ACHV_ROULETTE_ATK_(\\d+)_(\\d+)$");   // fix: ATK_100_1 형식
        Pattern P_ROULETTE_CRI = Pattern.compile("^ACHV_ROULETTE_CRI_(\\d+)_(\\d+)$");   // fix: CRI_300_1 형식
        Pattern P_FIRST_CLEAR  = Pattern.compile("^ACHV_FIRST_CLEAR_MON_(\\d+)$");
        Pattern P_BROADCAST    = Pattern.compile("^ACHV_CLEAR_BROADCAST_MON_(\\d+)$");
        Pattern P_SPECIAL      = Pattern.compile("^ACHV_(\\d{4})$");

        // ─── 집계용 ───
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
        // 학살자: date → {monNo → 1} (팝업용 몬스터 목록 보존)
        Map<String, Map<Integer, Integer>> slayerDetail = new TreeMap<>();
        Map<String, Integer> master      = new TreeMap<>();  // date → maxCnt
        Map<String, Integer> rouletteAtk = new LinkedHashMap<>();  // key=ATK_{pct}_{nth}
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
            if ((m = P_SLAYER.matcher(cmd)).matches()) {
                String date = m.group(1);
                int monNo = Integer.parseInt(m.group(2));
                slayerDetail.computeIfAbsent(date, k -> new TreeMap<>()).put(monNo, 1);
                continue;
            }
            if ((m = P_MASTER.matcher(cmd)).matches()) {
                String date = m.group(1);
                int cnt = Integer.parseInt(m.group(2));
                master.merge(date, cnt, Math::max);
                continue;
            }
            if ((m = P_ROULETTE_ATK.matcher(cmd)).matches()) { rouletteAtk.put(m.group(1)+"_"+m.group(2), 1); continue; }
            if ((m = P_ROULETTE_CRI.matcher(cmd)).matches()) { rouletteCri.put(m.group(1)+"_"+m.group(2), 1); continue; }
            if ((m = P_FIRST_CLEAR.matcher(cmd)).matches())  { firstClear.put(Integer.parseInt(m.group(1)), 1); continue; }
            if ((m = P_BROADCAST.matcher(cmd)).matches())    { broadcast.put(Integer.parseInt(m.group(1)), 1); continue; }
            if ((m = P_SPECIAL.matcher(cmd)).matches())      { specialList.add(m.group(1)); continue; }
        }

        // ─── 몬스터별 킬 리스트 (monNo 순, 이름 포함) ───
        List<HashMap<String, Object>> monKillList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : monKillMap.entrySet()) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",   e.getKey());
            item.put("monName", getMonName(e.getKey(), monMap));
            item.put("maxKill", e.getValue());
            monKillList.add(item);
        }

        // ─── 학살자 시즌 리스트 (팝업용 몬스터 목록 포함) ───
        List<HashMap<String, Object>> slayerSeasons = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Integer>> e : slayerDetail.entrySet()) {
            List<HashMap<String, Object>> mons = new ArrayList<>();
            for (Integer monNo : e.getValue().keySet()) {
                HashMap<String, Object> mon = new HashMap<>();
                mon.put("monNo",   monNo);
                mon.put("monName", getMonName(monNo, monMap));
                mons.add(mon);
            }
            HashMap<String, Object> s = new HashMap<>();
            s.put("season",   e.getKey());
            s.put("monCount", e.getValue().size());
            s.put("mons",     mons);
            slayerSeasons.add(s);
        }

        // ─── 마스터 시즌 리스트 ───
        List<HashMap<String, Object>> masterSeasons = new ArrayList<>();
        for (Map.Entry<String, Integer> e : master.entrySet()) {
            HashMap<String, Object> s = new HashMap<>();
            s.put("season", e.getKey());
            s.put("count",  e.getValue());
            masterSeasons.add(s);
        }

        // ─── 최초 토벌 / 공개방송 리스트 (이름 포함) ───
        List<HashMap<String, Object>> firstClearList = new ArrayList<>();
        for (Integer monNo : firstClear.keySet()) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",   monNo);
            item.put("monName", getMonName(monNo, monMap));
            firstClearList.add(item);
        }
        List<HashMap<String, Object>> broadcastList = new ArrayList<>();
        for (Integer monNo : broadcast.keySet()) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",   monNo);
            item.put("monName", getMonName(monNo, monMap));
            broadcastList.add(item);
        }

        // ─── 전체 CMD → 한글 라벨 목록 ───
        List<HashMap<String, Object>> allCmds = new ArrayList<>();
        for (HashMap<String, Object> row : achvList) {
            String cmd = Objects.toString(row.get("CMD"), "");
            HashMap<String, Object> entry = new HashMap<>();
            entry.put("cmd",   cmd);
            entry.put("label", formatAchievementLabel(cmd, monMap));
            allCmds.add(entry);
        }

        // ─── 결과 맵 ───
        HashMap<String, Object> g = new HashMap<>();
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
        g.put("slayer",       slayerDetail.values().stream().mapToInt(Map::size).sum());
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
        // 내부 전달용 (getAchievements에서 remove)
        g.put("_monKillList",    monKillList);
        g.put("_slayerSeasons",  slayerSeasons);
        g.put("_masterSeasons",  masterSeasons);
        g.put("_firstClearList", firstClearList);
        g.put("_broadcastList",  broadcastList);
        g.put("_specialList",    specialList);
        g.put("_allCmds",        allCmds);
        return g;
    }

    /**
     * CMD → 한글 라벨 변환 (BossAttackController.formatAchievementLabelSimple 기반 + 확장)
     */
    private String formatAchievementLabel(String cmd, Map<Integer, Monster> monMap) {
        if (cmd == null || cmd.isEmpty()) return "";
        try {
            if (cmd.startsWith("ACHV_FIRST_CLEAR_MON_")) {
                int monNo = Integer.parseInt(cmd.substring("ACHV_FIRST_CLEAR_MON_".length()));
                return "최초토벌: " + getMonName(monNo, monMap);
            }
            if (cmd.startsWith("ACHV_CLEAR_BROADCAST_MON_")) {
                int monNo = Integer.parseInt(cmd.substring("ACHV_CLEAR_BROADCAST_MON_".length()));
                return "축하방송: " + getMonName(monNo, monMap);
            }
            if (cmd.startsWith("ACHV_KILL") && cmd.contains("_MON_")) {
                String[] parts = cmd.substring("ACHV_KILL".length()).split("_MON_");
                int threshold = Integer.parseInt(parts[0]);
                int monNo     = Integer.parseInt(parts[1]);
                return getMonName(monNo, monMap) + " " + threshold + "킬 달성";
            }
            if (cmd.startsWith("ACHV_KILL_TOTAL_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_KILL_TOTAL_".length()));
                return "통산 처치 " + th + "마리 달성";
            }
            if (cmd.startsWith("ACHV_KILL_NIGHTMARE_TOTAL_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_KILL_NIGHTMARE_TOTAL_".length()));
                return "나이트메어 처치 " + th + "마리 달성";
            }
            if (cmd.startsWith("ACHV_KILL_HELL_TOTAL_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_KILL_HELL_TOTAL_".length()));
                return "헬 처치 " + th + "마리 달성";
            }
            if (cmd.startsWith("ACHV_DEATH_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_DEATH_".length()));
                return "죽음 극복 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_ATTACK_TOTAL_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_ATTACK_TOTAL_".length()));
                return "통산 공격 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_SHOP_SELL_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_SHOP_SELL_".length()));
                return "상점 판매 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_LIGHT_ITEM_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_LIGHT_ITEM_".length()));
                return "빛 아이템 획득 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_DARK_ITEM_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_DARK_ITEM_".length()));
                return "어둠 아이템 획득 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_GRAY_ITEM_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_GRAY_ITEM_".length()));
                return "음양 아이템 획득 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_BAG_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_BAG_".length()));
                return "가방 획득 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_JOB_SKILL_")) {
                String rest   = cmd.substring("ACHV_JOB_SKILL_".length());
                String[] parts = rest.split("_");
                if (parts.length >= 2) {
                    String jobName = parts[0];
                    int th = Integer.parseInt(parts[1]);
                    return jobName + " 스킬 사용 " + th + "회 달성";
                }
            }
            if (cmd.startsWith("ACHV_HELL_ATK_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_HELL_ATK_".length()));
                return "헬보스 공격 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_HELL_CLEAR_")) {
                int th = Integer.parseInt(cmd.substring("ACHV_HELL_CLEAR_".length()));
                return "헬보스 처치 " + th + "회 달성";
            }
            if (cmd.startsWith("ACHV_ROULETTE_ATK_")) {
                String rest   = cmd.substring("ACHV_ROULETTE_ATK_".length());
                String[] parts = rest.split("_");
                return "공격력 " + parts[0] + "% 달성 " + parts[1] + "번째";
            }
            if (cmd.startsWith("ACHV_ROULETTE_CRI_")) {
                String rest   = cmd.substring("ACHV_ROULETTE_CRI_".length());
                String[] parts = rest.split("_");
                return "치명타 " + parts[0] + "% 달성 " + parts[1] + "번째";
            }
            if (cmd.startsWith("ACHV_SLAYER_SEASON_")) {
                String rest = cmd.substring("ACHV_SLAYER_SEASON_".length());
                String[] parts = rest.split("_");
                String date  = parts[0];
                int monNo    = Integer.parseInt(parts[1]);
                return "학살자 " + formatDate(date) + " 시즌: " + getMonName(monNo, monMap);
            }
            if (cmd.startsWith("ACHV_MASTER_SEASON_")) {
                String rest = cmd.substring("ACHV_MASTER_SEASON_".length());
                String[] parts = rest.split("_");
                String date = parts[0];
                int cnt     = Integer.parseInt(parts[1]);
                return "직업마스터 " + formatDate(date) + " 시즌 " + cnt + "회 달성";
            }
            if (cmd.matches("^ACHV_\\d{4}$")) {
                return "특수업적 #" + cmd.substring(5);
            }
        } catch (Exception ignore) {}
        return cmd;
    }

    /** 몬스터 번호 → 이름 (캐시 미스 시 "몬스터#N") */
    private String getMonName(int monNo, Map<Integer, Monster> monMap) {
        if (monMap != null) {
            Monster m = monMap.get(monNo);
            if (m != null && m.monName != null && !m.monName.isEmpty()) return m.monName;
        }
        return "몬스터#" + monNo;
    }

    /** YYYYMMDD → YYYY-MM-DD */
    private String formatDate(String d) {
        if (d == null || d.length() < 8) return d;
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
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
