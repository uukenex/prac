package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.game.dto.Monster;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

@Controller
@RequestMapping("/loa")
public class LoaBossStatusViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/boss-status-view")
    public String bossStatusViewPage() {
        return "nonsession/loa/boss_status_view";
    }

    /**
     * REST API: 보스현황 데이터
     * - 현재 보스 기본정보(MONSTER_CACHE)
     * - 현재 보스 누적 데미지 (NOW_YN=1)
     * - 마지막 보스 사망 시각 (KILL_YN=1)
     * - 최근 배틀로그 TOP20
     */
    @GetMapping("/api/boss-status")
    @ResponseBody
    public ResponseEntity<?> getBossStatus() {

        HashMap<String, Object> result = new HashMap<>();

        // ── 1. 현재 진행중 보스 DB 집계 ──────────────────────────────
        HashMap<String, Object> bossState = null;
        try {
            bossState = botNewService.selectCurrentBossState();
        } catch (Exception ignore) {}

        // ── 2. 마지막 보스 사망 시각 ──────────────────────────────────
        HashMap<String, Object> lastKill = null;
        try {
            lastKill = botNewService.selectLastBossKillTime();
        } catch (Exception ignore) {}

        // ── 3. 최근 배틀로그 TOP20 ────────────────────────────────────
        List<HashMap<String, Object>> recentLog = new ArrayList<>();
        try {
            recentLog = botNewService.selectCurrentBossRecentLog();
            if (recentLog == null) recentLog = new ArrayList<>();
        } catch (Exception ignore) {}

        // ── 4. 현재 보스 기본정보 (MONSTER_CACHE) ────────────────────
        HashMap<String, Object> bossInfo = new HashMap<>();
        if (bossState != null && bossState.get("MON_NO") != null) {
            int monNo = ((Number) bossState.get("MON_NO")).intValue();
            Monster m = MiniGameUtil.MONSTER_CACHE.get(monNo);
            if (m == null) {
                // 캐시 미스 시 DB 재조회
                try {
                    List<Monster> monList = botNewService.selectAllMonsters();
                    if (monList != null) {
                        for (Monster mx : monList) MiniGameUtil.MONSTER_CACHE.put(mx.monNo, mx);
                    }
                    m = MiniGameUtil.MONSTER_CACHE.get(monNo);
                } catch (Exception ignore) {}
            }
            if (m != null) {
                bossInfo.put("MON_NO",     m.monNo);
                bossInfo.put("MON_NAME",   m.monName  != null ? m.monName  : "");
                bossInfo.put("MON_LV",     m.monLv);
                bossInfo.put("MON_HP",     m.monHp);
                bossInfo.put("MON_ATK",    m.monAtk);
                bossInfo.put("MON_NOTE",   m.monNote  != null ? m.monNote  : "");
                bossInfo.put("MON_PATTEN", m.monPatten);
            }
        }

        // ── 5. 보스 생존/사망 여부 판단 ──────────────────────────────
        // NOW_YN=1 로그가 있으면 보스 생존, 없으면 마지막 사망시각 기준으로 다음 등장 시간 계산
        boolean isAlive = (bossState != null && !bossState.isEmpty());

        result.put("isAlive",    isAlive);
        result.put("bossInfo",   bossInfo);
        result.put("bossState",  bossState  != null ? bossState  : new HashMap<>());
        result.put("lastKill",   lastKill   != null ? lastKill   : new HashMap<>());
        result.put("recentLog",  recentLog);

        return ResponseEntity.ok(result);
    }
}
