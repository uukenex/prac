package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;

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
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "roomName",  defaultValue = "") String roomName) {

        List<HashMap<String, Object>> items = botNewService.selectAllItemsWithOwned(userName, roomName);

        HashMap<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("userName", userName);
        result.put("roomName", roomName);
        result.put("total", items.size());

        return ResponseEntity.ok(result);
    }
}
