package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.service.BotNewService;

@Controller
@RequestMapping("/loa")
public class LoaMonsterViewController {

    @Resource(name = "core.prjbot.BotNewService")
    BotNewService botNewService;

    /** JSP 뷰 페이지 */
    @GetMapping("/monster-view")
    public String monsterViewPage() {
        return "nonsession/loa/monster_view";
    }

    /** REST API: 전체 몬스터 + 드랍 SP */
    @GetMapping("/api/monsters")
    @ResponseBody
    public ResponseEntity<?> getAllMonsters() {
        List<HashMap<String, Object>> monsters = botNewService.selectAllMonstersForView();
        HashMap<String, Object> result = new HashMap<>();
        result.put("monsters", monsters);
        result.put("total", monsters.size());
        return ResponseEntity.ok(result);
    }
}
