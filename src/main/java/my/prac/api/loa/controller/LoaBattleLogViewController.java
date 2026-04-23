package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.service.BotNewService;

@Controller
@RequestMapping("/loa")
public class LoaBattleLogViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/battle-log-view")
    public String battleLogViewPage() {
        return "nonsession/loa/battle_log_view";
    }

    /**
     * REST API: 유저 배틀로그 (페이지네이션)
     * @param userName  조회 유저명
     * @param page      페이지 번호 (1부터)
     * @param size      페이지 당 건수 (기본 50)
     * @param days      기간 필터 (0=전체, 1=오늘, 3=3일, 7=7일 등)
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

        result.put("list",     list != null ? list : new java.util.ArrayList<>());
        result.put("total",    total);
        result.put("page",     page);
        result.put("size",     size);
        result.put("userName", userName.trim());

        return ResponseEntity.ok(result);
    }
}
