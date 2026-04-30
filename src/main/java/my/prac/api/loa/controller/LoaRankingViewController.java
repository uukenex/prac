package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.service.BotNewService;

@Controller
@RequestMapping("/loa")
public class LoaRankingViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/ranking-view")
    public String rankingViewPage() {
        return "nonsession/loa/ranking_view";
    }

    /**
     * REST API: 전체 랭킹 데이터
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
     * REST API: 부캐 리스트
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

    // ── helper ──────────────────────────────────────────────────────────────
    @FunctionalInterface
    interface Supplier<T> { T get(); }

    @SuppressWarnings("unchecked")
    private List<HashMap<String, Object>> safeList(Supplier<List<?>> s) {
        try {
            List<?> r = s.get();
            return r != null ? (List<HashMap<String, Object>>) r : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
