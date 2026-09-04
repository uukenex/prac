package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotS5Service;

/**
 * [시즌5] 탑 등반 컨트롤러
 *
 * 명령어:
 *   /주사위, /ㅈㅅㅇ  — 이동(비전투) 또는 공격(전투 중)
 *   /층변경 N         — 같은 10층 구간 내 이동 (예: 33층에서 /층변경 9 → 39층)
 *   /탑내려가기        — 마을에서만 사용, 바로 아래 10층 구간 마을로 이동 (예: 20층 마을 → 10층 마을) (별칭: /탑다운)
 *   /파티편성 [N]     — 동료 목록 조회 / N번째 동료 파티 편성·해제 (별칭: /탑편성, /탑동료, /탑파티, /ㅌㅍㅅ, /ㅌㄷㄹ, /ㅌㅍㅌ)
 *   /탑현황 [닉네임]   — 현재 층/PP/상태 조회. 닉네임을 붙이면 다른 유저 조회(부분 입력 시 앞부분 일치 검색) (별칭: /탑정보, /ㅌㅎㅎ, /ㅌㅈㅂ)
 *   /탑도움말, /탑명령어 — 웹(SPA) 탭 기능을 포함한 전체 명령어 안내
 *   /탑업적 [닉네임]   — 달성한 업적 이름만 조회. 닉네임을 붙이면 다른 유저 조회(부분 입력 시 앞부분 일치 검색) (별칭: /ㅌㅇㅂ, /ㅌㅇㅈ)
 *   /동료뽑기N [10]   — N번 계약서로 동료 뽑기(뒤에 10을 붙이면 10연속), 번호는 SPA 상점 탭에서 확인. /동료뽑기(번호 생략)는 1번
 *   /장비뽑기N [10]   — N번 보물상자로 장비 뽑기(뒤에 10을 붙이면 10연속). /장비뽑기(번호 생략)는 1번
 *   /주사위구매 [N]   — 주사위 등급 확인 / 장착
 *   /스탯구매 [종류]  — 스탯 구매 현황 / 강화(공격력 | 최소공격력 | 체력)
 *   /장비목록         — 보유 장비 조회
 *   /장비장착 N [M]   — N번째 미착용 장비를 M번째(생략 시 자동) 파티원에 장착
 *   /장비합성 N       — N번째 장비 포함 동일 장비 3개를 상위 등급으로 합성
 *   /장비해제 M       — M번째 파티원이 착용 중인 장비 전부 해제 (별칭: /탑해제, /ㅈㅂㅎㅈ, /ㅌㅎㅈ)
 *   /탑랭킹           — 서버 전체 최고기록(익명 집계, 누가 세웠는지는 비공개) (별칭: /ㅌㄹㅋ)
 *   /탑통계 (관리자 전용, 미공개) — 웹/카톡 채널별 이용자 수(대략치)
 *
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 */
@Controller
public class Season5Controller {

    @Resource(name = "core.prjbot.BotS5Service")
    BotS5Service s5Service;

    private String userNameOf(HashMap<String, Object> map) {
        return map.get("userName").toString();
    }

    private String param1Of(HashMap<String, Object> map) {
        return Objects.toString(map.get("param1"), "").trim();
    }

    private String param2Of(HashMap<String, Object> map) {
        return Objects.toString(map.get("param2"), "").trim();
    }

    /** LoaChatController가 "/동료뽑기"·"/장비뽑기"를 번호 없이(bare) 받았을 때만 세팅해두는 표식. */
    private boolean isS5GachaBare(HashMap<String, Object> map) {
        return "Y".equals(Objects.toString(map.get("s5GachaBare"), ""));
    }

    public String rollDice(HashMap<String, Object> map) {
        String userName = userNameOf(map);
        String result = s5Service.rollDice(userName);
        // /탑통계용 활동 카운터(웹 쪽은 Season5ViewController.apiTowerAction에서 동일하게 적재)
        s5Service.bumpActivityStat(userName, "DICE_CHAT");
        if (result != null && result.contains("파티 전멸")) {
            s5Service.bumpActivityStat(userName, "WIPE_CHAT");
        }
        return result;
    }

    public String changeFloor(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) {
            return "사용법: /층변경 0~9 (예: /층변경 9 → 같은 구간 보스층으로 이동)";
        }
        try {
            return s5Service.changeFloor(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "층변경 값은 0~9 사이의 숫자여야 합니다.";
        }
    }

    public String descendVillage(HashMap<String, Object> map) {
        return s5Service.descendVillage(userNameOf(map));
    }

    public String party(HashMap<String, Object> map) {
        String userName = userNameOf(map);
        String param1 = param1Of(map);
        if (param1.isEmpty()) return s5Service.partyList(userName);
        try {
            return s5Service.partyToggle(userName, Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요. 예: /파티편성 1";
        }
    }

    public String hideCompanion(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /동료가리기 N (N은 /파티편성 목록 번호)";
        try {
            return s5Service.toggleCompanionHidden(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String towerStatus(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        return param1.isEmpty() ? s5Service.towerStatus(userNameOf(map)) : s5Service.towerStatus(userNameOf(map), param1);
    }

    public String help(HashMap<String, Object> map) {
        return s5Service.help(userNameOf(map));
    }

    /** /갱신 — 다른 시즌들의 /갱신과 같이 눌리는 공용 명령어에 얹어서 호출됨(LoaChatController 참고) */
    public String refreshConfig() {
        return s5Service.refreshConfig();
    }

    /** /이미지갱신 */
    public String refreshImages() {
        return s5Service.refreshCompanionImages();
    }

    public String achievements(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        return param1.isEmpty() ? s5Service.achievements(userNameOf(map)) : s5Service.achievements(userNameOf(map), param1);
    }

    public String gachaCompanion(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /동료뽑기N (N은 SPA 상점 탭에서 확인, 생략 시 1번)";
        try {
            String userName = userNameOf(map);
            // "번호별로 뭐가 다른지 헷갈려한다" 요청 -- 번호 없이 bare로 쳤을 때만 안내를 앞에
            // 붙여주고, 기존 정책대로 1번 구매는 그대로 진행한다.
            String guide = isS5GachaBare(map) ? s5Service.gachaTierGuide(userName, "COMPANION") : "";
            String result = guide + s5Service.gachaCompanion(userName, Integer.parseInt(param1));
            s5Service.bumpActivityStat(userName, "GACHA_CHAT");
            return result;
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaCompanionTen(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /동료뽑기N 10 (N은 SPA 상점 탭에서 확인)";
        try {
            String userName = userNameOf(map);
            String result = s5Service.gachaCompanionTen(userName, Integer.parseInt(param1));
            s5Service.bumpActivityStat(userName, "GACHA_CHAT");
            return result;
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaEquip(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비뽑기N (N은 SPA 상점 탭에서 확인, 생략 시 1번)";
        try {
            String userName = userNameOf(map);
            String guide = isS5GachaBare(map) ? s5Service.gachaTierGuide(userName, "EQUIP") : "";
            String result = guide + s5Service.gachaEquip(userName, Integer.parseInt(param1));
            s5Service.bumpActivityStat(userName, "GACHA_CHAT");
            return result;
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaEquipTen(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비뽑기N 10 (N은 SPA 상점 탭에서 확인)";
        try {
            String userName = userNameOf(map);
            String result = s5Service.gachaEquipTen(userName, Integer.parseInt(param1));
            s5Service.bumpActivityStat(userName, "GACHA_CHAT");
            return result;
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String diceShop(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        String userName = userNameOf(map);
        if (param1.isEmpty()) return s5Service.diceShop(userName, null);
        try {
            return s5Service.diceShop(userName, Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String statShop(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        return s5Service.statShop(userNameOf(map), param1.isEmpty() ? null : param1);
    }

    public String equipList(HashMap<String, Object> map) {
        return s5Service.equipList(userNameOf(map));
    }

    public String equipWear(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        String param2 = param2Of(map);
        if (param1.isEmpty()) return s5Service.equipWearUsage(userNameOf(map));
        try {
            int equipIdx = Integer.parseInt(param1);
            Integer companionIdx = param2.isEmpty() ? null : Integer.parseInt(param2);
            return s5Service.equipWear(userNameOf(map), equipIdx, companionIdx);
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String equipSynthesis(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비합성 N (N=미착용 장비 번호)";
        try {
            return s5Service.equipSynthesis(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    /** /탑랭킹 — 인자/유저 구분 없이 서버 전체 익명 기록판을 그대로 보여줌 */
    public String ranking() {
        return s5Service.ranking();
    }

    // /이벤트지급 인자 3개(등급/동료권수량/장비권수량)를 담기엔 param1·param2 2칸으로 부족해서
    // fulltxt(전체 입력 원문)를 직접 토큰화해서 쓴다. 등급 키워드는 초급(=하급 계약서와 동일 취급)
    // /중급/상급/최상급 4개.
    private static final java.util.Map<String, Integer> EVENT_TIER_KEYWORDS = new java.util.HashMap<String, Integer>() {{
        put("초급", 1); put("하급", 1);
        put("중급", 2);
        put("상급", 3);
        put("최상급", 4);
    }};

    /**
     * /이벤트지급(관리자 전용, 미공개) — "/이벤트지급 [등급] [동료뽑기권수량] [장비뽑기권수량]".
     * 등급 토큰(초급/중급/상급/최상급)이 없으면 초급 취급, 뒤 두 수량은 생략하면 각각 0.
     * 예: "/이벤트지급 중급 3 2" -> 전체 유저에게 중급 동료뽑기권 3장 + 장비뽑기권 2장.
     */
    public String grantEventVouchers(HashMap<String, Object> map) {
        String fulltxt = Objects.toString(map.get("fulltxt"), "").trim();
        String[] tokens = fulltxt.isEmpty() ? new String[0] : fulltxt.split("\\s+");
        // tokens[0]은 명령어 자체("/이벤트지급") -- 그 뒤부터가 실제 인자
        java.util.List<String> args = tokens.length > 1
                ? java.util.Arrays.asList(tokens).subList(1, tokens.length)
                : java.util.Collections.<String>emptyList();
        int tier = 1; // 등급 생략 시 기본값: 초급
        int argIdx = 0;
        if (!args.isEmpty() && EVENT_TIER_KEYWORDS.containsKey(args.get(0))) {
            tier = EVENT_TIER_KEYWORDS.get(args.get(0));
            argIdx = 1;
        }
        try {
            int companionQty = args.size() > argIdx ? Integer.parseInt(args.get(argIdx)) : 0;
            int equipQty = args.size() > argIdx + 1 ? Integer.parseInt(args.get(argIdx + 1)) : 0;
            return s5Service.grantEventVouchers(userNameOf(map), tier, companionQty, equipQty);
        } catch (NumberFormatException e) {
            return "수량은 숫자로 입력해주세요.";
        }
    }

    public String equipUnwearAll(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비해제 M (M=파티원 번호, /파티편성에서 확인)";
        try {
            return s5Service.equipUnwearAll(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    /** /탑통계 (관리자 전용) — 웹/카톡 채널별 이용자 수 */
    public String towerStats(HashMap<String, Object> map) {
        return s5Service.towerStats(userNameOf(map));
    }
}
