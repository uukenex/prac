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
 *   /파티편성 [N]     — 동료 목록 조회 / N번째 동료 파티 편성·해제
 *   /탑현황           — 현재 층/PP/상태 조회
 *   /업적             — 업적 달성 현황
 *   /동료뽑기 N, /동료뽑기10 N — N번 계약서로 동료 뽑기(10연속), 번호는 SPA 상점 탭에서 확인
 *   /장비뽑기 N, /장비뽑기10 N — N번 보물상자로 장비 뽑기(10연속)
 *   /주사위구매 [N]   — 주사위 등급 확인 / 장착
 *   /스탯구매 [종류]  — 스탯 구매 현황 / 강화(공격력 | 최소공격력 | 체력)
 *   /장비목록         — 보유 장비 조회
 *   /장비장착 N [M]   — N번째 미착용 장비를 M번째(생략 시 자동) 파티원에 장착
 *   /장비합성 N       — N번째 장비 포함 동일 장비 3개를 상위 등급으로 합성
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

    public String rollDice(HashMap<String, Object> map) {
        return s5Service.rollDice(userNameOf(map));
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

    public String towerStatus(HashMap<String, Object> map) {
        return s5Service.towerStatus(userNameOf(map));
    }

    public String achievements(HashMap<String, Object> map) {
        return s5Service.achievements(userNameOf(map));
    }

    public String gachaCompanion(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /동료뽑기 N (N은 SPA 상점 탭에서 확인)";
        try {
            return s5Service.gachaCompanion(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaCompanionTen(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /동료뽑기10 N (N은 SPA 상점 탭에서 확인)";
        try {
            return s5Service.gachaCompanionTen(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaEquip(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비뽑기 N (N은 SPA 상점 탭에서 확인)";
        try {
            return s5Service.gachaEquip(userNameOf(map), Integer.parseInt(param1));
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요.";
        }
    }

    public String gachaEquipTen(HashMap<String, Object> map) {
        String param1 = param1Of(map);
        if (param1.isEmpty()) return "사용법: /장비뽑기10 N (N은 SPA 상점 탭에서 확인)";
        try {
            return s5Service.gachaEquipTen(userNameOf(map), Integer.parseInt(param1));
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
        if (param1.isEmpty()) return "사용법: /장비장착 N [M] (N=미착용 장비 번호, M=파티원 번호(생략 가능))";
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
}
