package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.service.BotS3Service;

@Controller
@RequestMapping("/loa")
public class LoaBossStatusViewController {

    private static final DateTimeFormatter BOSS_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

    @Resource(name = "core.prjbot.BotS3Service")
    BotS3Service botS3Service;

    /** JSP 뷰 페이지 */
    @GetMapping("/boss-status-view")
    public String bossStatusViewPage() {
        return "nonsession/loa/boss_status_view";
    }

    /**
     * REST API: 헬보스 현황
     *
     * 반환 구조:
     *   status       : "ALIVE" | "WAITING" | "NONE"
     *   boss         : 현재(또는 예정) 보스 스탯 (MAX_HP, CUR_HP, ATK_POWER, EVADE_RATE, CRIT_DEF_RATE, ...)
     *   startDate    : 보스 스폰 시각 (ISO 문자열)
     *   spawnRemainMs: 등장까지 남은 밀리초 (WAITING 때만 양수)
     *   recentLog    : 공격 로그 리스트 (ALIVE 때 채워짐, 시간 역순)
     *   lastKillMsg  : 마지막 처치 결과 메시지 (WAITING/NONE 때 표시용)
     */
    @GetMapping("/api/boss-status")
    @ResponseBody
    public ResponseEntity<?> getBossStatus() {

        HashMap<String, Object> result = new HashMap<>();

        // ── 1. 활성 보스 조회 ─────────────────────────────────────────
        HashMap<String, Object> boss = null;
        try { boss = botS3Service.selectHellBoss(); } catch (Exception ignore) {}

        if (boss == null || boss.get("CUR_HP") == null) {
            // 보스 레코드 자체가 없는 경우
            result.put("status",       "NONE");
            result.put("boss",         new HashMap<>());
            result.put("recentLog",    new ArrayList<>());
            result.put("lastKillMsg",  safeStr(botS3Service.getLastKillRewardMsg()));
            return ResponseEntity.ok(result);
        }

        // ── 2. 스폰 시각 파싱 ─────────────────────────────────────────
        String startDateStr = safeStr(boss.get("START_DATE")); // "yyyyMMdd HHmmss"
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

        // ── 3. 공격 로그 (ALIVE 때만) ────────────────────────────────
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

    private String safeStr(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
