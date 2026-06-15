package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotS4Service;
import my.prac.core.prjbot.service.impl.BotS4ServiceImpl;

/**
 * [시즌4] 낚시 컨트롤러
 *
 * 명령어:
 *   /낚시       — 하루 1회 낚시 실행
 *   /낚시가방   — 보유 물고기 + 업적 현황
 *   /낚시구매   — 낚시대/찌 업그레이드 목록
 *
 * 장비 테이블:   TBOT_S4_USER_EQUIP
 * 인벤토리:     TBOT_S4_FISH_INV
 * 낚시 로그:    TBOT_S4_FISHING_LOG
 * 업적:         TBOT_S4_ACHIEVEMENT / TBOT_S4_USER_ACH
 * 물고기 마스터: TBOT_S4_FISH_INFO (★1~★8, 초기 ★3까지 ACTIVE_YN='1')
 */
@Controller
public class Season4Controller {

    private static final String NL = "♬";

    private static final String[] ROD_NAMES    = { "", "일반낚시대", "고급낚시대", "희귀낚시대", "영웅낚시대", "전설낚시대" };
    private static final String[] BOBBER_NAMES = { "", "일반찌", "고급찌", "희귀찌", "영웅찌" };

    @Resource(name = "core.prjbot.BotS4Service")
    BotS4Service s4Service;

    // ================================================================
    // /낚시
    // ================================================================
    public String fishing(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();

        // 장비 초기화 (최초 사용 시)
        HashMap<String, Object> equip = s4Service.selectUserEquip(userName);
        if (equip == null) {
            s4Service.initUserEquip(userName);
            equip = new HashMap<>();
            equip.put("ROD_GRADE",    1);
            equip.put("BOBBER_GRADE", 1);
        }

        // 오늘 이미 낚시했는지 체크
        if (s4Service.selectTodayFishingLog(userName) != null) {
            return "오늘은 이미 낚시를 했습니다. 내일 다시 도전하세요! 🎣";
        }

        int rodGrade    = ((Number) equip.get("ROD_GRADE")).intValue();
        int bobberGrade = ((Number) equip.get("BOBBER_GRADE")).intValue();

        return s4Service.fishing(userName, rodGrade, bobberGrade);
    }

    // ================================================================
    // /낚시가방
    // ================================================================
    public String fishingBag(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();

        List<HashMap<String, Object>> inv = s4Service.selectFishInv(userName);
        List<HashMap<String, Object>> userAch = s4Service.selectUserAchievements(userName);

        if (inv == null || inv.isEmpty()) {
            return "아직 잡은 물고기가 없습니다. /낚시 로 낚시를 시작하세요!";
        }

        // 등급별 그룹핑
        int curGrade = -1;
        StringBuilder sb = new StringBuilder();
        sb.append("🐟 [낚시가방]").append(NL);

        for (HashMap<String, Object> item : inv) {
            int grade = ((Number) item.get("FISH_GRADE")).intValue();
            if (grade != curGrade) {
                if (curGrade != -1) sb.append(NL);
                sb.append(buildStar(grade)).append(" 등급").append(NL);
                curGrade = grade;
            }
            sb.append("  ").append(item.get("FISH_NAME"))
              .append(" x").append(item.get("QTY"))
              .append(NL);
        }

        // 업적 현황
        if (userAch != null && !userAch.isEmpty()) {
            sb.append(NL).append("🏆 달성 업적").append(NL);
            List<HashMap<String, Object>> allAch = s4Service.selectAchievementList();
            Set<Integer> clearedIds = new HashSet<>();
            for (HashMap<String, Object> a : userAch) {
                clearedIds.add(((Number) a.get("ACH_ID")).intValue());
            }
            for (HashMap<String, Object> a : allAch) {
                int id = ((Number) a.get("ACH_ID")).intValue();
                if (clearedIds.contains(id)) {
                    sb.append("  ✅ ").append(a.get("ACH_NAME")).append(NL);
                }
            }
        }

        return sb.toString().replaceAll(NL + "$", "");
    }

    // ================================================================
    // /낚시구매
    // ================================================================
    public String fishingShop(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();

        HashMap<String, Object> equip = s4Service.selectUserEquip(userName);
        if (equip == null) {
            s4Service.initUserEquip(userName);
            equip = new HashMap<>();
            equip.put("ROD_GRADE",    1);
            equip.put("BOBBER_GRADE", 1);
        }

        int rodGrade    = ((Number) equip.get("ROD_GRADE")).intValue();
        int bobberGrade = ((Number) equip.get("BOBBER_GRADE")).intValue();

        // 달성 업적에서 해금된 장비 등급 파악
        List<HashMap<String, Object>> allAch  = s4Service.selectAchievementList();
        List<HashMap<String, Object>> userAch = s4Service.selectUserAchievements(userName);
        Set<Integer> clearedIds = new HashSet<>();
        for (HashMap<String, Object> a : userAch) {
            clearedIds.add(((Number) a.get("ACH_ID")).intValue());
        }

        int maxRodUnlock    = 1;
        int maxBobberUnlock = 1;
        for (HashMap<String, Object> a : allAch) {
            if (!clearedIds.contains(((Number) a.get("ACH_ID")).intValue())) continue;
            String rewardType  = a.get("REWARD_TYPE") != null ? a.get("REWARD_TYPE").toString() : "";
            int    rewardGrade = a.get("REWARD_GRADE") != null ? ((Number) a.get("REWARD_GRADE")).intValue() : 0;
            if ("ROD_UNLOCK".equals(rewardType))    maxRodUnlock    = Math.max(maxRodUnlock,    rewardGrade);
            if ("BOBBER_UNLOCK".equals(rewardType)) maxBobberUnlock = Math.max(maxBobberUnlock, rewardGrade);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 [낚시 상점]").append(NL);

        // 낚시대
        sb.append(NL).append("[ 낚시대 ]").append(NL);
        for (int g = 1; g < ROD_NAMES.length; g++) {
            if (g == rodGrade) {
                sb.append("  ").append(ROD_NAMES[g]).append(" ← 장착중").append(NL);
            } else if (g <= maxRodUnlock) {
                sb.append("  ").append(ROD_NAMES[g]).append(" (구매가능)").append(NL);
            } else {
                sb.append("  ").append(ROD_NAMES[g]).append(" 🔒").append(NL);
            }
        }

        // 찌
        sb.append(NL).append("[ 찌 ]").append(NL);
        for (int g = 1; g < BOBBER_NAMES.length; g++) {
            if (g == bobberGrade) {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" ← 장착중").append(NL);
            } else if (g <= maxBobberUnlock) {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" (구매가능)").append(NL);
            } else {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" 🔒").append(NL);
            }
        }

        sb.append(NL).append("* 구매 기능은 추후 오픈 예정입니다.");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    private String buildStar(int grade) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < grade; i++) sb.append("★");
        return sb.toString();
    }
}
