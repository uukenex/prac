package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotS5Service;

/**
 * [시즌5] 탑 등반 컨트롤러 (MVP)
 *
 * 명령어:
 *   /주사위, /ㅈㅅㅇ  — 이동(비전투) 또는 공격(전투 중)
 *   /층변경 N         — 같은 10층 구간 내 이동 (예: 33층에서 /층변경 9 → 39층)
 *   /파티편성         — 동료 목록 조회
 *   /파티편성 N       — N번째 동료 파티 편성/해제 토글
 *   /탑현황           — 현재 층/PP/상태 조회
 *   /업적             — 업적 달성 현황
 *
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 * 미구현(TODO): 가챠, 상점 구매, 주사위구매, 스탯구매, 자동사냥 정산, 장비, 직업별 특수효과
 */
@Controller
public class Season5Controller {

    @Resource(name = "core.prjbot.BotS5Service")
    BotS5Service s5Service;

    public String rollDice(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        return s5Service.rollDice(userName);
    }

    public String changeFloor(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        String param1 = Objects.toString(map.get("param1"), "").trim();
        if (param1.isEmpty()) {
            return "사용법: /층변경 0~9 (예: /층변경 9 → 같은 구간 보스층으로 이동)";
        }
        try {
            int n = Integer.parseInt(param1);
            return s5Service.changeFloor(userName, n);
        } catch (NumberFormatException e) {
            return "층변경 값은 0~9 사이의 숫자여야 합니다.";
        }
    }

    public String party(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        String param1 = Objects.toString(map.get("param1"), "").trim();
        if (param1.isEmpty()) {
            return s5Service.partyList(userName);
        }
        try {
            int idx = Integer.parseInt(param1);
            return s5Service.partyToggle(userName, idx);
        } catch (NumberFormatException e) {
            return "번호는 숫자로 입력해주세요. 예: /파티편성 1";
        }
    }

    public String towerStatus(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        return s5Service.towerStatus(userName);
    }

    public String achievements(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        return s5Service.achievements(userName);
    }
}
