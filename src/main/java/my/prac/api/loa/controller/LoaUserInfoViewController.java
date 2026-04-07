package my.prac.api.loa.controller;

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

import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

@Controller
@RequestMapping("/loa")
public class LoaUserInfoViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/user-info-view")
    public String userInfoViewPage() {
        return "nonsession/loa/user_info_view";
    }

    /**
     * REST API: 유저 정보 (장비 화면용)
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

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return Integer.parseInt(Objects.toString(o, "0")); }
        catch (Exception e) { return 0; }
    }
}
