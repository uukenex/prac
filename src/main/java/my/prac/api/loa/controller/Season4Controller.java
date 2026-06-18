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
    private static final int[]    ROD_MAX_GRADE = { 0, 3, 5, 6, 7, 8 };

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
            return userName + "님," + NL + "오늘은 이미 낚시를 했습니다. 내일 다시 도전하세요! 🎣";
        }

        int rodGrade    = ((Number) equip.get("ROD_GRADE")).intValue();
        int bobberGrade = ((Number) equip.get("BOBBER_GRADE")).intValue();

        return userName + "님," + NL + s4Service.fishing(userName, rodGrade, bobberGrade) + NL + "/낚시가방 으로 물고기 확인가능!";
    }

    // ================================================================
    // /낚시가방
    // ================================================================
    public String fishingBag(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        String param1 = java.util.Objects.toString(map.get("param1"), "").trim();
        String targetUser = userName;
        if (!param1.isEmpty()) {
            List<String> found = s4Service.selectS4UserSearch(map);
            if (found != null && !found.isEmpty()) {
                targetUser = found.get(0);
            } else {
                return "해당 유저(" + param1 + ")를 찾을 수 없습니다.";
            }
        }

        List<HashMap<String, Object>> inv = s4Service.selectFishInv(targetUser);
        List<HashMap<String, Object>> userAch = s4Service.selectUserAchievements(targetUser);

        if (inv == null || inv.isEmpty()) {
            return targetUser + "님," + NL + "아직 잡은 물고기가 없습니다. /낚시 로 낚시를 시작하세요!";
        }

        // 등급별 그룹핑
        int curGrade = -1;
        StringBuilder sb = new StringBuilder();
        sb.append(targetUser).append("님,").append(NL).append("🐟 [낚시가방]").append(NL);

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
    // /낚시구매  (목록 조회 or 장비 구매)
    // ================================================================
    public String fishingShop(HashMap<String, Object> map) {
        String userName = map.get("userName").toString();
        String param1   = java.util.Objects.toString(map.get("param1"), "").trim();

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

        // 장비명 파라미터 있으면 구매 처리
        if (!param1.isEmpty()) {
            return buyEquip(userName, param1, rodGrade, bobberGrade, maxRodUnlock, maxBobberUnlock);
        }

        // 목록 표시
        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님,").append(NL).append("🛒 [낚시 상점]").append(NL);

        sb.append(NL).append("[ 낚시대 ]").append(NL);
        for (int g = 1; g < ROD_NAMES.length; g++) {
            if (g == rodGrade) {
                sb.append("  ").append(ROD_NAMES[g]).append(" ← 장착중").append(NL);
            } else if (g <= maxRodUnlock) {
                sb.append("  ").append(ROD_NAMES[g]).append(" (구매가능) → /낚시구매 ").append(ROD_NAMES[g]).append(NL);
            } else {
                sb.append("  ").append(ROD_NAMES[g]).append(" 🔒").append(NL);
            }
        }

        sb.append(NL).append("[ 찌 ]").append(NL);
        for (int g = 1; g < BOBBER_NAMES.length; g++) {
            if (g == bobberGrade) {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" ← 장착중").append(NL);
            } else if (g <= maxBobberUnlock) {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" (구매가능) → /낚시구매 ").append(BOBBER_NAMES[g]).append(NL);
            } else {
                sb.append("  ").append(BOBBER_NAMES[g]).append(" 🔒").append(NL);
            }
        }

        return sb.toString().replaceAll(NL + "$", "");
    }

    private String buyEquip(String userName, String itemName,
                             int curRod, int curBobber, int maxRod, int maxBobber) {
        for (int g = 2; g < ROD_NAMES.length; g++) {
            if (ROD_NAMES[g].equals(itemName)) {
                if (curRod >= g) return itemName + "은 이미 장착 중입니다.";
                if (g > maxRod)  return "🔒 " + itemName + "은 아직 해금되지 않았습니다. (업적 달성 필요)";
                s4Service.upgradeEquip(userName, "ROD", g);
                int maxGrade = g < ROD_MAX_GRADE.length ? ROD_MAX_GRADE[g] : 8;
                return "✅ " + itemName + " 장착 완료!" + NL + "이제 " + buildStar(maxGrade) + " 등급까지 낚을 수 있습니다!";
            }
        }
        for (int g = 2; g < BOBBER_NAMES.length; g++) {
            if (BOBBER_NAMES[g].equals(itemName)) {
                if (curBobber >= g) return itemName + "은 이미 장착 중입니다.";
                if (g > maxBobber)  return "🔒 " + itemName + "은 아직 해금되지 않았습니다. (업적 달성 필요)";
                s4Service.upgradeEquip(userName, "BOBBER", g);
                return "✅ " + itemName + " 장착 완료!" + NL + "고등급 물고기 확률이 올라갑니다!";
            }
        }
        return "알 수 없는 장비명입니다." + NL + "/낚시구매 로 목록을 확인하세요.";
    }

    // ----------------------------------------------------------------
    private String buildStar(int grade) {
        return BotS4ServiceImpl.buildStar(grade);
    }
}
