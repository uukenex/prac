package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.JobDef;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotS3Service;
import my.prac.core.util.MiniGameUtil;
import my.prac.core.util.SP;

/**
 * 통합 LOA 뷰 컨트롤러
 *
 * 기존 10개의 LoaXxxViewController를 이 한 컨트롤러로 통합.
 * - JSP 뷰 페이지: /loa/{view-name}
 * - REST API: /loa/api/{endpoint}
 *
 * 목표: 데이터 동기화 오류 방지 (각 컨트롤러가 독립적으로 DB 조회 → 타이밍 차이)
 */
@Controller
@RequestMapping("/loa")
public class LoaUnifiedViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    @Resource(name = "core.prjbot.BotS3Service")
    BotS3Service botS3Service;

    private static final DateTimeFormatter BOSS_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

    // ─────────────────────────────────────────────────────────────
    // JSP 뷰 페이지들 (GET /loa/{view-name})
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/user-info-view")
    public String userInfoViewPage() {
        return "nonsession/loa/user_info_view";
    }

    @GetMapping("/hunter-rank-view")
    public String hunterRankViewPage() {
        return "nonsession/loa/hunter_rank_view";
    }

    @GetMapping("/boss-status-view")
    public String bossStatusViewPage() {
        return "nonsession/loa/boss_status_view";
    }

    @GetMapping("/battle-log-view")
    public String battleLogViewPage() {
        return "nonsession/loa/battle_log_view";
    }

    @GetMapping("/ranking-view")
    public String rankingViewPage() {
        return "nonsession/loa/ranking_view";
    }

    @GetMapping("/item-view")
    public String itemViewPage() {
        return "nonsession/loa/item_view";
    }

    @GetMapping("/achievement-view")
    public String achievementViewPage() {
        return "nonsession/loa/achievement_view";
    }

    @GetMapping("/equip-sim-view")
    public String equipSimViewPage() {
        return "nonsession/loa/equip_sim_view";
    }

    @GetMapping("/job-view")
    public String jobViewPage() {
        return "nonsession/loa/job_view";
    }


    // ─────────────────────────────────────────────────────────────
    // REST API: /loa/api/{endpoint}
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /loa/api/user-info
     *
     * 유저 정보 (장비 화면용)
     * - user: 기본 스탯 (lv, job, hp, atk, crit, regen ...)
     * - inventory: 전체 인벤토리 목록 (itemId, itemName, qty, 스탯)
     * - potionUseCount: 물약 사용 횟수
     */
    @GetMapping("/api/user-info")
    @ResponseBody
    public ResponseEntity<?> getUserInfo(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();

        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 1) 인벤토리
        List<HashMap<String, Object>> inventory = new ArrayList<>();
        try {
            List<HashMap<String, Object>> raw = botNewService.selectInventorySummaryAll(userName, "");
            if (raw != null) {
                for (HashMap<String, Object> row : raw) {
                    int itemId = toInt(row.get("ITEM_ID"));
                    // 포션류 제외 (1001~1006)
                    if (itemId >= 1001 && itemId <= 1006) continue;
                    inventory.add(row);
                }
            }
        } catch (Exception ignore) {}

        // 2) 물약 사용 횟수 (캐시 우선)
        int potionUseCount = 0;
        try {
            potionUseCount = MiniGameUtil.POTION_USE_CACHE.containsKey(userName)
                    ? MiniGameUtil.POTION_USE_CACHE.get(userName)
                    : botNewService.selectPotionUseCount(userName);
            MiniGameUtil.POTION_USE_CACHE.putIfAbsent(userName, potionUseCount);
        } catch (Exception ignore) {}

        // 3) 아이템 카테고리 레이블 주입
        for (HashMap<String, Object> item : inventory) {
            int id = toInt(item.get("ITEM_ID"));
            item.put("_category", resolveCategory(id));
        }

        result.put("inventory", inventory);
        result.put("potionUseCount", potionUseCount);
        result.put("userName", userName);

        // 4) 유저 기본 정보 (lv, job, exp)
        try {
            User u = botNewService.selectUser(userName, null);
            result.put("lv",     u != null ? u.lv      : 0);
            result.put("job",    u != null && u.job != null ? u.job.trim() : "");
            result.put("expCur", u != null ? u.expCur  : 0);
            result.put("expNext",u != null ? u.expNext : 0);
        } catch (Exception ignore) {
            result.put("lv", 0); result.put("job", "");
            result.put("expCur", 0); result.put("expNext", 0);
        }

        // 5) 현재 SP (잔액)
        try {
            HashMap<String,Object> row = botNewService.selectCurrentPoint(userName, "");
            double sc = Double.parseDouble(Objects.toString(row != null ? row.get("SCORE") : "0", "0"));
            String sx = Objects.toString(row != null ? row.get("SCORE_EXT") : "", "");
            result.put("currentSp", new SP(sc, sx).toString());
        } catch (Exception ignore) { result.put("currentSp", "0"); }

        // 6) 누적 SP (lifetime)
        try {
            List<HashMap<String,Object>> comps = botNewService.selectUserTotalSpComponents(userName);
            double raw = 0;
            if (comps != null) {
                for (HashMap<String,Object> r : comps) {
                    String ext = Objects.toString(r.get("SP_EXT"), "");
                    double amt = Double.parseDouble(Objects.toString(r.get("SP_AMT"), "0"));
                    double mult = ext.isEmpty() ? 1.0 : Math.pow(10000.0, (ext.charAt(0) - 'a') + 1);
                    raw += amt * mult;
                }
            }
            result.put("lifetimeSp", SP.fromSp(raw).toString());
        } catch (Exception ignore) { result.put("lifetimeSp", "0"); }

        // 7) 현재 GP + 누적 GP
        try {
            double gp = botNewService.selectGpBalance(userName);
            result.put("gpBalance", gp > 0 ? String.format("%.2f", gp) : "0");
        } catch (Exception ignore) { result.put("gpBalance", "0"); }
        try {
            double totalGp = botNewService.selectUserTotalEarnedGp(userName);
            result.put("totalEarnedGp", totalGp > 0 ? String.format("%.2f", totalGp) : "0");
        } catch (Exception ignore) { result.put("totalEarnedGp", "0"); }

        // 8) 활성 세트 보너스
        try {
            result.put("setBonuses", botNewService.selectActiveSetBonuses(userName));
        } catch (Exception ignore) { result.put("setBonuses", new ArrayList<>()); }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/hunter-rank
     *
     * 헌터 랭크 API (데이터 동기화 개선 버전)
     * 5가지 지표:
     *   1) 원시 킬수, 드랍수, 죽음수
     *   2) 헌터직업 공격횟수 보정 → 킬+2배, 죽음+0.5배
     *   3) 어둠/음양 아이템 획득 보정 (S 이하에만) → 킬/드랍/죽음 각 +1(어둠), +3(음양)
     *   4) 최종 등급 계산
     *   5) 다음 등급 요구사항
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

    /**
     * GET /loa/api/boss-status
     *
     * 헬보스 현황
     *   status       : "ALIVE" | "WAITING" | "NONE"
     *   boss         : 현재(또는 예정) 보스 스탯
     *   startDate    : 보스 스폰 시각 (yyyyMMdd HHmmss)
     *   spawnRemainMs: 등장까지 남은 밀리초 (WAITING 때만 양수)
     *   recentLog    : 공격 로그 리스트 (ALIVE 때 채워짐)
     *   lastKillMsg  : 마지막 처치 결과 메시지
     */
    @GetMapping("/api/boss-status")
    @ResponseBody
    public ResponseEntity<?> getBossStatus() {

        HashMap<String, Object> result = new HashMap<>();

        // 1. 활성 보스 조회
        HashMap<String, Object> boss = null;
        try {
            boss = botS3Service.selectHellBoss();
        } catch (Exception ignore) {}

        if (boss == null || boss.get("CUR_HP") == null) {
            result.put("status",       "NONE");
            result.put("boss",         new HashMap<>());
            result.put("recentLog",    new ArrayList<>());
            result.put("lastKillMsg",  safeStr(botS3Service.getLastKillRewardMsg()));
            return ResponseEntity.ok(result);
        }

        // 2. 스폰 시각 파싱
        String startDateStr = safeStr(boss.get("START_DATE"));
        LocalDateTime spawnTime = null;
        try {
            spawnTime = LocalDateTime.parse(startDateStr, BOSS_DATE_FMT);
        } catch (Exception ignore) {}

        boolean isWaiting = spawnTime != null && LocalDateTime.now().isBefore(spawnTime);
        long spawnRemainMs = 0L;
        if (isWaiting && spawnTime != null) {
            spawnRemainMs = java.time.Duration
                    .between(LocalDateTime.now(), spawnTime).toMillis();
        }

        // 3. 공격 로그 (ALIVE 때만)
        List<HashMap<String, Object>> recentLog = new ArrayList<>();
        if (!isWaiting && !startDateStr.isEmpty()) {
            try {
                HashMap<String, Object> logParam = new HashMap<>();
                logParam.put("bossStartDate", startDateStr);
                List<HashMap<String, Object>> rows =
                        botS3Service.selectHellBossRecentLog(logParam);
                if (rows != null) recentLog = rows;
            } catch (Exception ignore) {}
        }

        result.put("status",       isWaiting ? "WAITING" : "ALIVE");
        result.put("boss",         boss);
        result.put("startDate",    startDateStr);
        result.put("spawnRemainMs", spawnRemainMs);
        result.put("recentLog",    recentLog);
        result.put("lastKillMsg",  isWaiting ? safeStr(botS3Service.getLastKillRewardMsg()) : "");

        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/battle-log
     *
     * 유저 배틀로그 (페이지네이션)
     */
    @GetMapping("/api/battle-log")
    @ResponseBody
    public ResponseEntity<?> getBattleLog(
            @RequestParam(value = "userName",  defaultValue = "") String userName,
            @RequestParam(value = "page",      defaultValue = "1")  int page,
            @RequestParam(value = "size",      defaultValue = "50") int size,
            @RequestParam(value = "days",      defaultValue = "0")  int days) {

        HashMap<String, Object> result = new HashMap<>();

        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 페이지네이션 범위 보정
        if (page  < 1)   page  = 1;
        if (size  < 1)   size  = 50;
        if (size  > 100) size  = 100;
        if (days  > 60)  days  = 60;  // BATTLE_LOG 2개월 보관 정책에 따른 최대 조회 제한
        int startRow = (page - 1) * size;
        int endRow   = page * size;

        HashMap<String, Object> params = new HashMap<>();
        params.put("userName", userName.trim());
        params.put("startRow", startRow);
        params.put("endRow",   endRow);
        params.put("days",     days > 0 ? days : null);

        List<HashMap<String, Object>> list = null;
        int total = 0;
        try {
            list  = botNewService.selectUserBattleLog(params);
            total = botNewService.selectUserBattleLogCount(params);
        } catch (Exception e) {
            result.put("error", "조회 중 오류가 발생했습니다.");
            return ResponseEntity.ok(result);
        }

        result.put("list",     list != null ? list : new ArrayList<>());
        result.put("total",    total);
        result.put("page",     page);
        result.put("size",     size);
        result.put("userName", userName.trim());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/ranking
     *
     * 전체 랭킹 데이터
     */
    @GetMapping("/api/ranking")
    @ResponseBody
    public ResponseEntity<?> getRanking() {
        HashMap<String, Object> result = new HashMap<>();
        try {
            // 떠오르는 샛별 (최근 6시간 공격횟수)
            List<HashMap<String, Object>> rising = safeList(() -> botNewService.selectRisingStarsTop5Last6h());
            result.put("rising", rising);

            // SP + 공격횟수
            List<HashMap<String, Object>> spAtk = safeList(() -> {
                try { return botNewService.selectSpAndAtkRanking(); } catch (Exception e) { return null; }
            });
            result.put("spAtk", spAtk);

            // 업적 갯수
            List<HashMap<String, Object>> achv = safeList(() -> botNewService.selectAchievementCountRanking());
            result.put("achv", achv);

            // GP 랭킹
            List<HashMap<String, Object>> gp = safeList(() -> botNewService.selectGpRanking());
            result.put("gp", gp);

        } catch (Exception e) {
            result.put("error", "랭킹 조회 중 오류가 발생했습니다.");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/alt-chars
     *
     * 부캐 리스트
     */
    @GetMapping("/api/alt-chars")
    @ResponseBody
    public ResponseEntity<?> getAltChars() {
        HashMap<String, Object> result = new HashMap<>();
        try {
            List<String> list = botNewService.selectAltCharList();
            result.put("list", list != null ? list : new ArrayList<>());
        } catch (Exception e) {
            result.put("list", new ArrayList<>());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/monsters
     *
     * 전체 몬스터 목록 + 드랍 SP
     */
    @GetMapping("/api/monsters")
    @ResponseBody
    public ResponseEntity<?> getAllMonsters() {

        // 캐시가 비어있으면 DB에서 로드
        if (MiniGameUtil.MONSTER_CACHE.isEmpty()) {
            List<my.prac.core.game.dto.Monster> monList = botNewService.selectAllMonsters();
            if (monList != null) {
                for (my.prac.core.game.dto.Monster m : monList) {
                    MiniGameUtil.MONSTER_CACHE.put(m.monNo, m);
                }
            }
        }

        List<HashMap<String, Object>> list = new ArrayList<>(MiniGameUtil.MONSTER_CACHE.size());
        for (my.prac.core.game.dto.Monster m : MiniGameUtil.MONSTER_CACHE.values()) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("MON_NO",     m.monNo);
            row.put("MON_NAME",   m.monName != null  ? m.monName  : "");
            row.put("MON_LV",     m.monLv);
            row.put("MON_HP",     m.monHp);
            row.put("MON_ATK",    m.monAtk);
            row.put("MON_EXP",    m.monExp);
            row.put("MON_DROP",   m.monDrop != null  ? m.monDrop  : "");
            row.put("MON_PATTEN", m.monPatten);
            row.put("MON_NOTE",   m.monNote != null  ? m.monNote  : "");

            // 드랍 가격
            int dropSp = 0;
            String dropSpExt = "";
            String dropName = m.monDrop != null ? m.monDrop.trim() : "";
            if (!dropName.isEmpty()) {
                Integer itemId = MiniGameUtil.ITEM_ID_CACHE.get(dropName);
                if (itemId != null) {
                    HashMap<String, Object> priceRow = MiniGameUtil.ITEM_PRICE_CACHE.get(itemId);
                    if (priceRow != null) {
                        Object p    = priceRow.get("ITEM_SELL_PRICE");
                        Object pExt = priceRow.get("ITEM_SELL_PRICE_EXT");
                        dropSp    = p    != null ? ((Number) p).intValue() : 0;
                        dropSpExt = pExt != null ? String.valueOf(pExt)    : "";
                    }
                }
            }
            row.put("DROP_SP",     dropSp);
            row.put("DROP_SP_EXT", dropSpExt);
            list.add(row);
        }

        list.sort((a, b) -> Integer.compare((int) a.get("MON_NO"), (int) b.get("MON_NO")));

        HashMap<String, Object> result = new HashMap<>();
        result.put("monsters", list);
        result.put("total",    list.size());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/monster-kills
     *
     * 유저별 몬스터 킬 통계 (일반/빛/어둠/음양 상세)
     */
    /**
     * GET /loa/api/jobs
     *
     * 직업 목록
     */
    @GetMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<?> getJobs() {
        List<Map<String, String>> jobs = new ArrayList<>();
        for (Map.Entry<String, JobDef> entry : MiniGameUtil.JOB_DEFS.entrySet()) {
            JobDef def = entry.getValue();
            Map<String, String> item = new HashMap<>();
            item.put("name",       def.name);
            item.put("listLine",   def.listLine   != null ? def.listLine   : "");
            item.put("attackLine", def.attackLine != null ? def.attackLine : "");
            jobs.add(item);
        }
        return ResponseEntity.ok(jobs);
    }

    /**
     * GET /loa/api/items
     *
     * 전체 아이템 + 유저 보유여부
     */
    @GetMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> getItems(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        List<HashMap<String, Object>> items = botNewService.selectAllItemsWithOwned(userName);

        HashMap<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("userName", userName);
        result.put("total", items.size());

        // 세트 보너스 전체 목록 (유저가 있으면 보유 현황 포함, 없으면 빈 리스트)
        if (userName != null && !userName.trim().isEmpty()) {
            try {
                result.put("setBonus", botNewService.selectActiveSetBonuses(userName));
            } catch (Exception ignore) {}
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /loa/api/achievements
     *
     * 업적 현황 (복합 조회)
     * - 업적 목록 조회 및 정규식 기반 파싱
     * - 카테고리별 그룹화
     * - 실제 전투/아이템/시즌 정보 통합
     */
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

        // 2-0. Java에서 정렬: SQL ORDER BY를 Java Comparator로 구현
        achvList.sort((a, b) -> {
            String cmdA = Objects.toString(a.get("CMD"), "");
            String cmdB = Objects.toString(b.get("CMD"), "");

            // 1순위: 업적 타입별 정렬
            int typeA = getAchievementTypeOrder(cmdA);
            int typeB = getAchievementTypeOrder(cmdB);
            if (typeA != typeB) return Integer.compare(typeA, typeB);

            // 2순위: 몬스터 번호 또는 조건 숫자 (내림차순)
            int numA = extractTrailingNumber(cmdA);
            int numB = extractTrailingNumber(cmdB);
            if (numA != numB) return Integer.compare(numB, numA); // 내림차순

            // 3순위: 50킬 → 100킬 순
            if (cmdA.contains("KILL50")) return -1;
            if (cmdB.contains("KILL50")) return 1;
            if (cmdA.contains("KILL100")) return -1;
            if (cmdB.contains("KILL100")) return 1;

            return 0;
        });

        // 2-1. 업적에 한글 라벨 추가
        for (HashMap<String, Object> achv : achvList) {
            String cmd = Objects.toString(achv.get("CMD"), "");
            achv.put("label", formatAchievementLabel(cmd, monMap));
        }

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
        if (allCmds        == null) {
            allCmds = new ArrayList<>();
            for (HashMap<String, Object> achv : achvList) {
                HashMap<String, Object> item = new HashMap<>();
                item.put("cmd", achv.get("CMD"));
                item.put("label", achv.get("label"));
                allCmds.add(item);
            }
        }

        // 9. 실제 전투 통계 (BATTLE_LOG 기반)
        AttackDeathStat ads = null;
        try { ads = botNewService.selectAttackDeathStats(userName, ""); } catch (Exception ignore) {}
        int realAttacks = ads != null ? ads.totalAttacks : 0;
        int realDeaths  = ads != null ? ads.totalDeaths  : 0;

        // 10. 몬스터별 킬 통계 (MON_KILL_STAT 기반)
        Map<Integer, Integer> achvKillMap = new HashMap<>();
        for (HashMap<String, Object> item : monKillList) {
            achvKillMap.put(toInt(item.get("monNo")), toInt(item.get("maxKill")));
        }
        int realKills = 0, realNmKills = 0, realHellKills = 0;
        monKillList = new ArrayList<>();
        try {
            List<my.prac.core.game.dto.KillStat> ksList = botNewService.selectKillStats(userName, "");
            if (ksList != null) {
                for (my.prac.core.game.dto.KillStat ks : ksList) {
                    realKills     += ks.killCount;
                    realNmKills   += ks.nmKillCount;
                    realHellKills += ks.hellKillCount;
                    HashMap<String, Object> item = new HashMap<>();
                    item.put("monNo",     ks.monNo);
                    item.put("monName",   ks.monName);
                    item.put("killTotal", ks.killCount);
                    item.put("nm1Total",  ks.nmKillCount);
                    item.put("nm2Total",  ks.hellKillCount);
                    item.put("maxKill",   achvKillMap.getOrDefault(ks.monNo, 0));
                    monKillList.add(item);
                }
            }
        } catch (Exception ignore) {}

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
        Pattern P_ROULETTE_ATK = Pattern.compile("^ACHV_ROULETTE_ATK_(\\d+)_(\\d+)$");
        Pattern P_ROULETTE_CRI = Pattern.compile("^ACHV_ROULETTE_CRI_(\\d+)_(\\d+)$");
        Pattern P_FIRST_CLEAR  = Pattern.compile("^ACHV_FIRST_CLEAR_MON_(\\d+)$");
        Pattern P_BROADCAST    = Pattern.compile("^ACHV_CLEAR_BROADCAST_MON_(\\d+)$");
        Pattern P_SPECIAL      = Pattern.compile("^ACHV_(\\d{4})$");

        // ─── 집계용 ───
        Map<String, Integer> totalKills  = new LinkedHashMap<>();
        Map<String, Integer> nmKills     = new LinkedHashMap<>();
        Map<String, Integer> hellKills   = new LinkedHashMap<>();
        Map<Integer, Integer> monKillMap = new TreeMap<>();
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
        Map<String, Map<Integer, Integer>> slayerDetail = new TreeMap<>();
        Map<String, Integer> master      = new TreeMap<>();
        Map<String, Integer> rouletteAtk = new LinkedHashMap<>();
        Map<String, Integer> rouletteCri = new LinkedHashMap<>();
        Map<Integer, Integer> firstClear = new TreeMap<>();
        Map<Integer, Integer> broadcast  = new TreeMap<>();
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

        // ─── 몬스터별 킬 리스트 ───
        List<HashMap<String, Object>> monKillList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : monKillMap.entrySet()) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("monNo",   e.getKey());
            item.put("monName", getMonName(e.getKey(), monMap));
            item.put("maxKill", e.getValue());
            monKillList.add(item);
        }

        // ─── 학살자 시즌 리스트 ───
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

        // ─── 최초 토벌 / 공개방송 리스트 ───
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

        // ─── grouped 결과 구성 ───
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

        int skillCount = skills.values().stream().mapToInt(Map::size).sum();
        g.put("skills", skillCount);

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
        return g;
    }

    /** 몬스터 번호 → 이름 (캐시 미스 시 "몬스터#N") */
    private String getMonName(int monNo, Map<Integer, Monster> monMap) {
        if (monMap != null) {
            Monster m = monMap.get(monNo);
            if (m != null && m.monName != null && !m.monName.isEmpty()) return m.monName;
        }
        return "몬스터#" + monNo;
    }

    private int maxInt(Map<String, Integer> map) {
        return map.keySet().stream()
                .mapToInt(k -> { try { return Integer.parseInt(k.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } })
                .max().orElse(0);
    }

    /** CMD → 한글 라벨 변환 */
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
            if (cmd.startsWith("ACHV_LEVEL_")) {
                int lv = Integer.parseInt(cmd.substring("ACHV_LEVEL_".length()));
                return "레벨 달성 Lv." + lv;
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

    /** YYYYMMDD → YYYY-MM-DD */
    private String formatDate(String d) {
        if (d == null || d.length() < 8) return d;
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
    }

    private HashMap<String, Object> mapOf(Object... kv) {
        HashMap<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /**
     * GET /loa/api/equip-sim-init
     *
     * 장비 시뮬레이터 초기화 데이터
     */
    @GetMapping("/api/equip-sim-init")
    @ResponseBody
    public ResponseEntity<?> getEquipSimInit(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();

        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 유저 현재 정보
        List<HashMap<String, Object>> inventory = new ArrayList<>();
        try {
            List<HashMap<String, Object>> raw = botNewService.selectInventorySummaryAll(userName, "");
            if (raw != null) inventory = new ArrayList<>(raw);
        } catch (Exception ignore) {}

        result.put("inventory", inventory);
        result.put("userName", userName);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────
    // 유틸 메서드
    // ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface Supplier<T> {
        T get() throws Exception;
    }

    @SuppressWarnings("unchecked")
    private List<HashMap<String, Object>> safeList(Supplier<List<?>> s) {
        try {
            List<?> r = s.get();
            return r != null ? (List<HashMap<String, Object>>) r : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String resolveCategory(int id) {
        if (id >= 100 && id < 200)  return "무기";
        if (id >= 200 && id < 300)  return "투구";
        if (id >= 300 && id < 400)  return "행운";
        if (id >= 400 && id < 500)  return "갑옷";
        if (id >= 500 && id < 600)  return "반지";
        if (id >= 600 && id < 700)  return "토템";
        if (id >= 700 && id < 800)  return "전설";
        if (id >= 800 && id < 900)  return "날개";
        if (id >= 900 && id < 1000) return "선물";
        if (id >= 8000 && id < 9000) return "업적";
        if (id >= 9000)              return "유물";
        return "기타";
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

    private String safeStr(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    // 업적 정렬용 헬퍼 메서드: 업적 타입별 순서 반환
    private int getAchievementTypeOrder(String cmd) {
        if (cmd.contains("ACHV_FIRST_CLEAR_MON_")) return 1;
        if (cmd.contains("ACHV_KILL_TOTAL_")) return 2;
        if (cmd.contains("ACHV_KILL") && cmd.contains("_MON_")) return 3;
        if (cmd.contains("ACHV_DEATH")) return 4;
        return 9;
    }

    // 업적 정렬용 헬퍼 메서드: CMD 끝에서 숫자 추출
    private int extractTrailingNumber(String cmd) {
        if (cmd == null || cmd.isEmpty()) return 0;
        // 끝에서 숫자 추출
        int lastDigitIndex = cmd.length() - 1;
        while (lastDigitIndex >= 0 && !Character.isDigit(cmd.charAt(lastDigitIndex))) {
            lastDigitIndex--;
        }
        if (lastDigitIndex < 0) return 0;

        int firstDigitIndex = lastDigitIndex;
        while (firstDigitIndex >= 0 && Character.isDigit(cmd.charAt(firstDigitIndex))) {
            firstDigitIndex--;
        }
        try {
            return Integer.parseInt(cmd.substring(firstDigitIndex + 1, lastDigitIndex + 1));
        } catch (Exception e) {
            return 0;
        }
    }
}
