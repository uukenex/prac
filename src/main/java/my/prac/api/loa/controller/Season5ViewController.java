package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.service.BotS5Service;
import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 SPA 뷰 컨트롤러
 * - JSP: GET /loa/tower-view
 * - REST API: GET /loa/api/tower-*
 *
 * 기존 LoaUnifiedViewController 패턴(JSP + /api/* JSON)을 그대로 계승.
 * 화면 조회는 BotS5DAO를 직접 사용하고(다른 view 컨트롤러들과 동일 방식),
 * 게임 진행에 영향을 주는 액션은 반드시 BotS5Service를 통해 채팅 명령어와
 * 동일한 로직으로 처리한다.
 */
@Controller
@RequestMapping("/loa")
public class Season5ViewController {

    @Resource(name = "core.prjbot.BotS5Service")
    BotS5Service s5Service;

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO s5Dao;

    // ─────────────────────────────────────────────
    // JSP 뷰 페이지
    // ─────────────────────────────────────────────
    @GetMapping("/tower-view")
    public String towerViewPage() {
        return "nonsession/loa/tower_view";
    }

    // ─────────────────────────────────────────────
    // REST API
    // ─────────────────────────────────────────────

    @GetMapping("/api/tower-status")
    @ResponseBody
    public ResponseEntity<?> apiTowerStatus(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        HashMap<String, Object> progress = s5Service.selectUserProgress(userName);
        if (progress == null) {
            s5Service.initUser(userName);
            progress = s5Service.selectUserProgress(userName);
        }
        result.put("progress", progress);

        int floor = toInt(progress.get("CUR_FLOOR"));
        if (floor % 10 >= 1 && floor % 10 <= 8) {
            result.put("floorInfo", s5Dao.selectFloorInfo(floor));
            result.put("tiles", buildTilesWithFogOfWar(userName, floor));
            result.put("myTile", s5Dao.selectUserFloorProgress(userName, floor));
        }
        if ("Y".equals(String.valueOf(progress.get("AUTO_HUNT_YN")))) {
            result.put("autoHunt", buildAutoHuntInfo(userName));
        }
        return ResponseEntity.ok(result);
    }

    /** 자동사냥 화면 표시용: 몇 층 기준으로 정산되는지 + 시간당 PP + 지금까지 누적 경과시간(분). */
    private HashMap<String, Object> buildAutoHuntInfo(String userName) {
        HashMap<String, Object> log = s5Dao.selectAutoHuntLog(userName);
        if (log == null) return null;

        HashMap<String, Object> info = new HashMap<>();
        int floor = toInt(log.get("FLOOR"));
        info.put("floor", floor);

        int blockNo = (floor / 10) + 1;
        HashMap<String, Object> mon = s5Dao.selectMonster(blockNo, "N");
        if (mon != null) {
            info.put("monsterName", mon.get("MONSTER_NAME"));
            Object extObj = mon.get("PP_PER_KILL_EXT");
            PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), extObj == null ? "" : extObj.toString());
            int pos = floor % 10;
            double floorMult = (pos < 1 || pos > 8) ? 1.0 : (1.0 + 0.1 * (pos - 1)); // BotS5ServiceImpl.floorPpMultiplier와 동일 공식
            info.put("ppPerHourFormatted", perKill.multiply(6 * floorMult).format());
        }

        // 정산 대기 중인(=아직 PP로 못 받은) 시간만 보여줘야 하므로 START_DATE(자동사냥이 최초 켜진 시점,
        // 계속 플레이해도 안 바뀜)가 아니라 LAST_SETTLE_DATE(가장 최근 정산 시점) 기준으로 계산한다.
        // 그렇지 않으면 한창 접속해서 플레이 중이어도 자동사냥을 켠 이후 누적 시간이 계속 불어나 보여
        // "지금 하고 있는데 왜 15시간 경과라고 뜨지?" 같은 혼란이 생긴다.
        Object lastSettleObj = log.get("LAST_SETTLE_DATE");
        Object startObj = log.get("START_DATE");
        Object baseObj = lastSettleObj != null ? lastSettleObj : startObj;
        if (baseObj instanceof java.util.Date) {
            long elapsedMs = System.currentTimeMillis() - ((java.util.Date) baseObj).getTime();
            info.put("elapsedMinutes", Math.max(0, elapsedMs / 60000));
        }
        return info;
    }

    @GetMapping("/api/tower-party")
    @ResponseBody
    public ResponseEntity<?> apiTowerParty(@RequestParam(value = "userName", defaultValue = "") String userName) {
        List<HashMap<String, Object>> companions = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserCompanions(userName);
        HashMap<String, Object> result = new HashMap<>();
        result.put("companions", companions);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-equip")
    @ResponseBody
    public ResponseEntity<?> apiTowerEquip(@RequestParam(value = "userName", defaultValue = "") String userName) {
        List<HashMap<String, Object>> equips = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserEquip(userName);
        HashMap<String, Object> result = new HashMap<>();
        result.put("equips", equips);
        return ResponseEntity.ok(result);
    }

    /** 캐릭터 클릭(확대) 상세 카드용 — 장비/스탯구매 보너스까지 반영한 유효 스탯. 장비 목록은
     *  이미 /api/tower-equip으로 받아둔 데이터를 화면에서 COMPANION_ID로 필터링해서 재사용한다. */
    @GetMapping("/api/tower-companion-stat")
    @ResponseBody
    public ResponseEntity<?> apiTowerCompanionStat(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "companionId", defaultValue = "0") int companionId) {
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        int[] eff = s5Service.companionEffectiveStat(userName, companionId);
        result.put("hp", eff[0]);
        result.put("atk", eff[1]);
        result.put("def", eff[2]);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-shop")
    @ResponseBody
    public ResponseEntity<?> apiTowerShop(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = 0;
        if (!userName.trim().isEmpty()) {
            HashMap<String, Object> progress = s5Service.selectUserProgress(userName);
            if (progress != null) unlocked = toInt(progress.get("UNLOCKED_BLOCK"));
            result.put("ppValue", progress == null ? 0 : progress.get("PP_VALUE"));
            result.put("ppExt", progress == null ? "" : progress.get("PP_EXT"));
            result.put("freeCompanionPullsLeft", s5Service.freeCompanionPullsLeft(userName));
            result.put("companionVoucher", progress == null ? 0 : progress.get("COMPANION_VOUCHER"));
            result.put("equipVoucher", progress == null ? 0 : progress.get("EQUIP_VOUCHER"));
            // 티어락 뽑기권(N층 완전탐사 보상) -- 인덱스 0=티어1(하급)...3=티어4(최상급), COMPANION_GACHA_ID와 매칭
            result.put("companionVoucherByTier", new int[]{
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T1")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T2")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T3")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T4")),
            });
        }
        result.put("companionGacha", s5Dao.selectGachaList("COMPANION", unlocked));
        result.put("equipGacha", s5Dao.selectGachaList("EQUIP", unlocked));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-achievements")
    @ResponseBody
    public ResponseEntity<?> apiTowerAchievements(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        List<HashMap<String, Object>> all = s5Dao.selectAchievementList();
        List<HashMap<String, Object>> mine = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserAchievements(userName);
        HashMap<Integer, Boolean> cleared = new HashMap<>();
        for (HashMap<String, Object> m : mine) cleared.put(toInt(m.get("ACH_ID")), true);

        // 달성한 업적만 노출(미달성은 숨김/히든 여부 관계없이 전부 제외) -- /탑업적 채팅 명령어와 동일한 정책
        List<HashMap<String, Object>> visible = new ArrayList<>();
        for (HashMap<String, Object> a : all) {
            boolean done = cleared.containsKey(toInt(a.get("ACH_ID")));
            if (!done) continue;
            HashMap<String, Object> row = new HashMap<>(a);
            row.put("DONE", done);
            visible.add(row);
        }
        result.put("achievements", visible);
        result.put("total", all.size());
        result.put("clearedCount", mine.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 통합 액션 엔드포인트. 채팅 명령어(/주사위 등)와 동일한 BotS5Service 로직을 그대로 호출한다.
     * GET /loa/api/tower-action?userName=..&type=DICE|CHANGE_FLOOR|PARTY_TOGGLE|PARTY_SWAP|GACHA_COMPANION|
     *     GACHA_EQUIP|DICE_BUY|STAT_BUY|EQUIP_WEAR|EQUIP_SYNTH|EQUIP_UNWEAR_ALL|
     *     REDEEM_COMPANION_TICKET|REDEEM_WEAPON_TICKET&param1=&param2=
     * PARTY_SWAP(param1=/파티편성 목록 번호, param2=옮길 파티 슬롯 1~3)는 이미 편성된 동료끼리
     * 슬롯 자리를 바꾸는 웹 UI 전용 기능(채팅 명령어 없음).
     * REDEEM_*_TICKET(param1=직업 코드, param2=선택권 등급 3/4/5)는 10층 구간 완전탐사
     * 보상인 선택권을 쓰는 기능으로, 웹 UI 전용이다(채팅 명령어로는 노출 안 함 -- 의도적).
     */
    @GetMapping("/api/tower-action")
    @ResponseBody
    public ResponseEntity<?> apiTowerAction(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "type", defaultValue = "") String type,
            @RequestParam(value = "param1", defaultValue = "") String param1,
            @RequestParam(value = "param2", defaultValue = "") String param2) {

        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        String message;
        try {
            switch (type) {
                case "DICE":
                    message = s5Service.rollDice(userName);
                    break;
                case "CHANGE_FLOOR":
                    message = s5Service.changeFloor(userName, Integer.parseInt(param1));
                    break;
                case "PARTY_TOGGLE":
                    message = s5Service.partyToggle(userName, Integer.parseInt(param1));
                    break;
                case "PARTY_SWAP":
                    message = s5Service.partySwapSlot(userName, Integer.parseInt(param1), Integer.parseInt(param2));
                    break;
                case "COMPANION_HIDE":
                    message = s5Service.toggleCompanionHidden(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_COMPANION":
                    message = s5Service.gachaCompanion(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_COMPANION_10":
                    message = s5Service.gachaCompanionTen(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_EQUIP":
                    message = s5Service.gachaEquip(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_EQUIP_10":
                    message = s5Service.gachaEquipTen(userName, Integer.parseInt(param1));
                    break;
                case "DICE_BUY":
                    message = s5Service.diceShop(userName, param1.isEmpty() ? null : Integer.parseInt(param1));
                    break;
                case "STAT_BUY":
                    message = s5Service.statShop(userName, param1.isEmpty() ? null : param1);
                    break;
                case "EQUIP_WEAR":
                    message = s5Service.equipWear(userName, Integer.parseInt(param1),
                            param2.isEmpty() ? null : Integer.parseInt(param2));
                    break;
                case "EQUIP_SYNTH":
                    message = s5Service.equipSynthesis(userName, Integer.parseInt(param1));
                    break;
                case "EQUIP_UNWEAR_ALL":
                    message = s5Service.equipUnwearAll(userName, Integer.parseInt(param1));
                    break;
                case "REDEEM_COMPANION_TICKET":
                    message = s5Service.redeemCompanionChoiceTicket(userName, param1, Integer.parseInt(param2));
                    break;
                case "REDEEM_WEAPON_TICKET":
                    message = s5Service.redeemWeaponChoiceTicket(userName, param1, Integer.parseInt(param2));
                    break;
                default:
                    message = "알 수 없는 액션입니다.";
            }
        } catch (NumberFormatException e) {
            message = "잘못된 입력값입니다.";
        }

        result.put("message", message);
        return ResponseEntity.ok(result);
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return ((Number) o).intValue(); } catch (Exception e) { return 0; }
    }

    /**
     * 방문(발견)한 칸만 실제 TILE_TYPE을 노출하고, 미발견 칸은 종류를 감춘 채(DISCOVERED=false)
     * 내려준다 -- 화면에서 안개(fog of war) 처리를 서버가 보장.
     */
    private List<HashMap<String, Object>> buildTilesWithFogOfWar(String userName, int floor) {
        Set<Integer> visited = new HashSet<>();
        for (HashMap<String, Object> v : s5Dao.selectVisitedTileNos(userName, floor)) {
            visited.add(toInt(v.get("TILE_NO")));
        }
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (HashMap<String, Object> t : s5Service.ensureUserBoard(userName, floor)) {
            int tileNo = toInt(t.get("TILE_NO"));
            HashMap<String, Object> row = new HashMap<>();
            row.put("TILE_NO", tileNo);
            boolean discovered = visited.contains(tileNo);
            row.put("DISCOVERED", discovered);
            if (discovered) row.put("TILE_TYPE", t.get("TILE_TYPE"));
            out.add(row);
        }
        return out;
    }
}
