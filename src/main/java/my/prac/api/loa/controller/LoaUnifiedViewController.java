package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import my.prac.core.game.dto.JobDef;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotS3Service;
import my.prac.core.util.MiniGameUtil;

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

    @GetMapping("/monster-view")
    public String monsterViewPage() {
        return "nonsession/loa/monster_view";
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

            // MAX 데미지 TOP5
            List<HashMap<String, Object>> maxDmg = safeList(() -> botNewService.selectMaxDamageTop5());
            result.put("maxDmg", maxDmg);

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
    @GetMapping("/api/monster-kills")
    @ResponseBody
    public ResponseEntity<?> getMonsterKills(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();

        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        List<HashMap<String, Object>> killViewRows = new ArrayList<>();
        try {
            List<HashMap<String, Object>> rows = botNewService.selectMonsterKillsForView(userName);
            if (rows != null) killViewRows = new ArrayList<>(rows);
        } catch (Exception ignore) {}

        result.put("userName", userName);
        result.put("stats", killViewRows);
        result.put("count", killViewRows.size());
        return ResponseEntity.ok(result);
    }

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
     * 업적 현황 (성능 최적화)
     * - 병렬 조회로 DB 응답 시간 단축
     * - 필수 데이터만 선택적 조회
     */
    @GetMapping("/api/achievements")
    @ResponseBody
    public ResponseEntity<?> getAchievements(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        // 병렬 조회용 변수 (성능 최적화)
        List<HashMap<String, Object>> achvList = null;
        AttackDeathStat ads = null;
        List<HashMap<String, Object>> dropsData = null;
        HashMap<String, Object> invCounts = null;
        Integer hellAtkCount = null;
        Integer hellClearCount = null;
        HashMap<String, Object> buffStats = null;
        List<HashMap<String, Object>> killViewRows = null;

        // 병렬 조회 - 모든 조회를 동시에 실행하여 성능 개선
        try {
            // 병렬 스레드 조회
            Thread t1 = new Thread(() -> {
                try { achvList = botNewService.selectAchievementsByUser(userName, ""); } catch (Exception ignore) {}
            });
            Thread t2 = new Thread(() -> {
                try { ads = botNewService.selectAttackDeathStats(userName, ""); } catch (Exception ignore) {}
            });
            Thread t3 = new Thread(() -> {
                try { dropsData = botNewService.selectTotalDropItems(userName); } catch (Exception ignore) {}
            });
            Thread t4 = new Thread(() -> {
                try { invCounts = botNewService.selectAchievementInventoryCounts(userName); } catch (Exception ignore) {}
            });
            Thread t5 = new Thread(() -> {
                try { hellAtkCount = botNewService.selectHellBossAttackCount(userName); } catch (Exception ignore) {}
            });
            Thread t6 = new Thread(() -> {
                try { hellClearCount = botNewService.selectHellBossClearCount(userName); } catch (Exception ignore) {}
            });
            Thread t7 = new Thread(() -> {
                try { buffStats = botNewService.selectMaxDailyBuffStats(userName); } catch (Exception ignore) {}
            });
            Thread t8 = new Thread(() -> {
                try { killViewRows = botNewService.selectMonsterKillsForView(userName); } catch (Exception ignore) {}
            });

            // 모든 스레드 시작
            t1.start(); t2.start(); t3.start(); t4.start();
            t5.start(); t6.start(); t7.start(); t8.start();

            // 모든 스레드 완료 대기 (타임아웃: 5초)
            long startTime = System.currentTimeMillis();
            long timeout = 5000L;
            while (System.currentTimeMillis() - startTime < timeout) {
                if (!t1.isAlive() && !t2.isAlive() && !t3.isAlive() && !t4.isAlive() &&
                    !t5.isAlive() && !t6.isAlive() && !t7.isAlive() && !t8.isAlive()) {
                    break;
                }
                Thread.sleep(50);
            }
        } catch (Exception ignore) {}

        // NULL 체크
        if (achvList == null) achvList = new ArrayList<>();
        if (dropsData == null) dropsData = new ArrayList<>();
        if (killViewRows == null) killViewRows = new ArrayList<>();

        // 기본 통계 계산
        int totalKills = 0, totalAttacks = 0, totalDrops = 0;
        int lightQty = invCounts != null ? toInt(invCounts.get("DROP3_QTY")) : 0;
        int darkQty = invCounts != null ? toInt(invCounts.get("DROP5_QTY")) : 0;
        int grayQty = invCounts != null ? toInt(invCounts.get("DROP9_QTY")) : 0;
        int bagTotal = invCounts != null ? toInt(invCounts.get("BAG_COUNT")) : 0;

        for (HashMap<String, Object> d : dropsData) {
            Object v = d.get("TOTAL_QTY");
            if (v instanceof Number) totalDrops += ((Number) v).intValue();
        }

        int realAttacks = ads != null ? ads.totalAttacks : 0;
        int realDeaths = ads != null ? ads.totalDeaths : 0;
        int hellAtk = hellAtkCount != null ? hellAtkCount : 0;
        int hellClear = hellClearCount != null ? hellClearCount : 0;

        // 간단한 응답 구성 (성능 우선)
        result.put("userName",      userName);
        result.put("totalAchv",     achvList.size());
        result.put("stats", mapOf(
            "kills",        totalKills,
            "attacks",      totalAttacks,
            "drops",        totalDrops,
            "realAttacks",  realAttacks,
            "realDeaths",   realDeaths,
            "lightQty",     lightQty,
            "darkQty",      darkQty,
            "grayQty",      grayQty,
            "bagTotal",     bagTotal
        ));
        result.put("hellBoss", mapOf(
            "atkCount", hellAtk,
            "clearCount", hellClear
        ));
        result.put("achievements", achvList);
        result.put("monsterKills", killViewRows);

        return ResponseEntity.ok(result);
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
}
