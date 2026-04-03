package my.prac.api.loa.controller;

import java.util.ArrayList;
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

import my.prac.core.game.dto.Monster;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.util.MiniGameUtil;

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

    /**
     * REST API: 전체 몬스터 + 드랍 SP
     * MONSTER_CACHE 우선 사용 (BossAttackController.initCache 에서 서버 기동 시 로드)
     * ITEM_ID_CACHE + ITEM_PRICE_CACHE 로 드랍가격 조회 (없으면 0)
     */
    @GetMapping("/api/monsters")
    @ResponseBody
    public ResponseEntity<?> getAllMonsters() {

        // 캐시가 비어있으면 DB에서 로드 (기동 직후 initCache 미실행 대비)
        if (MiniGameUtil.MONSTER_CACHE.isEmpty()) {
            List<Monster> monList = botNewService.selectAllMonsters();
            if (monList != null) {
                for (Monster m : monList) MiniGameUtil.MONSTER_CACHE.put(m.monNo, m);
            }
        }

        List<HashMap<String, Object>> list = new ArrayList<>(MiniGameUtil.MONSTER_CACHE.size());
        for (Monster m : MiniGameUtil.MONSTER_CACHE.values()) {
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

            // 드랍 가격: ITEM_ID_CACHE(이름→ID) + ITEM_PRICE_CACHE(ID→가격)
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
     * REST API: 유저별 몬스터 킬 통계 (일반/빛/다크/음양)
     */
    @GetMapping("/api/monster-kills")
    @ResponseBody
    public ResponseEntity<?> getMonsterKills(
            @RequestParam(value = "userName", defaultValue = "") String userName) {

        if (userName.trim().isEmpty()) {
            return ResponseEntity.ok(new HashMap<>());
        }

        List<HashMap<String, Object>> rows = botNewService.selectMonsterKillsForView(userName.trim());

        // MON_NO → 통계 맵으로 변환
        Map<String, Object> killMap = new HashMap<>();
        long totalKills = 0;
        for (HashMap<String, Object> row : rows) {
            String monNo = String.valueOf(row.get("MON_NO"));
            killMap.put(monNo, row);
            Object t = row.get("KILL_TOTAL");
            if (t != null) totalKills += ((Number) t).longValue();
        }

        HashMap<String, Object> result = new HashMap<>();
        result.put("kills",      killMap);
        result.put("totalKills", totalKills);
        result.put("userName",   userName.trim());
        return ResponseEntity.ok(result);
    }
}
