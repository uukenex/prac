package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

@Controller
@RequestMapping("/loa")
public class LoaItemViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/item-view")
    public String itemViewPage() {
        return "nonsession/loa/item_view";
    }

    /** REST API: 전체 아이템 + 유저 보유여부 */
    @GetMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> getItemsWithOwned(
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
     * REST API: 포션 가격 공식 메타데이터
     * POTION_CONFIGS 에서 읽으므로 포션 추가 시 자동 갱신됨.
     */
    @GetMapping("/api/potion-formulas")
    @ResponseBody
    public ResponseEntity<?> getPotionFormulas() {
        Map<String, Object> result = new HashMap<>();
        MiniGameUtil.getPotionConfigs().forEach((id, cfg) -> {
            HashMap<String, Object> info = new HashMap<>();
            info.put("priceType",   cfg.priceType);
            info.put("formulaDesc", cfg.formulaDesc());
            result.put(String.valueOf(id), info);
        });
        return ResponseEntity.ok(result);
    }
}
