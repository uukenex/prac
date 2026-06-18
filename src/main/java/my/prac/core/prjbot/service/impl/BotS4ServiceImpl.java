package my.prac.core.prjbot.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.prjbot.dao.BotS4DAO;
import my.prac.core.prjbot.service.BotS4Service;

@Service("core.prjbot.BotS4Service")
public class BotS4ServiceImpl implements BotS4Service {

    @Resource(name = "core.prjbot.BotS4DAO")
    BotS4DAO botS4DAO;

    private static final String NL  = "♬";
    private static final Random RND = new Random();

    // DB charset(KO16MSWIN949)이 이모지 미지원 → 소스에서 관리
    private static final HashMap<Integer, String> FISH_EMOJI_MAP = new HashMap<Integer, String>() {{
        put(101, "🐟"); put(102, "🐠"); put(103, "🐍"); put(104, "🐟"); put(105, "🐡"); put(106, "🐟");
        put(201, "🐟"); put(202, "🐈"); put(203, "❄");  put(204, "🐊"); put(205, "🦪"); put(206, "🦪");
        put(301, "🌊"); put(302, "🐟"); put(303, "🐟"); put(304, "🦪"); put(305, "🐚"); put(306, "🦀");
        put(401, "🐙"); put(402, "🦑"); put(403, "🐚"); put(404, "🦪"); put(405, "🐟"); put(406, "🐟");
        put(501, "🐟"); put(502, "🐟"); put(503, "🐚"); put(504, "🥒"); put(505, "🦀"); put(506, "🐙");
        put(601, "🐟"); put(602, "🌊"); put(603, "🦀"); put(604, "🐍"); put(605, "🦪"); put(606, "🐟");
        put(701, "🐋"); put(702, "🐡"); put(703, "🦞"); put(704, "🐚"); put(705, "🦈"); put(706, "🐡");
        put(801, "🦑"); put(802, "🐟"); put(803, "🐟"); put(804, "🐟"); put(805, "🐟"); put(806, "🦈");
    }};

    // 낚시대 등급별 최대 접근 가능 물고기 등급
    private static final int[] ROD_MAX_GRADE = { 0, 3, 5, 6, 7, 8 }; // index = rodGrade(1~5)

    // 기본 등급 확률 (일반찌 기준, 낚시대 최대등급 내에서 누적)
    // [rodGrade][fishGrade] = weight (합산 후 랜덤)
    private static final int[][] BASE_GRADE_WEIGHT = {
        {},                              // 0 미사용
        { 0, 70, 25,  5,  0,  0,  0,  0,  0 }, // rodGrade=1: ★1~★3
        { 0, 50, 25, 15,  7,  3,  0,  0,  0 }, // rodGrade=2: ★1~★5
        { 0, 40, 20, 15,  10, 8,  7,  0,  0 }, // rodGrade=3: ★1~★6
        { 0, 35, 18, 13,  10, 8,  8,  8,  0 }, // rodGrade=4: ★1~★7
        { 0, 30, 15, 12,  10, 8,  8,  9,  8 }, // rodGrade=5: ★1~★8
    };

    // 찌 등급별 ★ 추가 보정 (고등급에서 -N, 최고등급에 +N)
    // bobberGrade=1은 보정 없음
    private static final int[] BOBBER_BONUS = { 0, 0, 5, 10, 15 }; // index = bobberGrade(1~4)

    @Override
    public HashMap<String, Object> selectUserEquip(String userName) {
        return botS4DAO.selectUserEquip(userName);
    }

    @Override
    public void initUserEquip(String userName) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("userName", userName);
        map.put("rodGrade", 1);
        map.put("bobberGrade", 1);
        botS4DAO.upsertUserEquip(map);
    }

    @Override
    public HashMap<String, Object> selectTodayFishingLog(String userName) {
        return botS4DAO.selectTodayFishingLog(userName);
    }

    @Override
    @Transactional
    public String fishing(String userName, int rodGrade, int bobberGrade) {
        int maxGrade = ROD_MAX_GRADE[Math.min(rodGrade, ROD_MAX_GRADE.length - 1)];
        int[] weights = buildGradeWeights(rodGrade, bobberGrade, maxGrade);

        // 등급 추첨
        int pickedGrade = rollGrade(weights, maxGrade);

        // 등급 내 어종 추첨 (ACTIVE_YN='1' 필터는 DB에서)
        List<HashMap<String, Object>> fishList = botS4DAO.selectFishByGrade(pickedGrade);
        if (fishList == null || fishList.isEmpty()) {
            // 활성 어종 없으면 ★1로 fallback
            pickedGrade = 1;
            fishList = botS4DAO.selectFishByGrade(1);
        }
        HashMap<String, Object> fish = rollFish(fishList);
        int fishId   = ((Number) fish.get("FISH_ID")).intValue();
        String fishName  = fish.get("FISH_NAME").toString();
        int fishGrade    = ((Number) fish.get("FISH_GRADE")).intValue();

        // 낚시 로그 INSERT
        HashMap<String, Object> logMap = new HashMap<>();
        logMap.put("userName", userName);
        logMap.put("fishId", fishId);
        botS4DAO.insertFishingLog(logMap);

        // 인벤토리 UPSERT
        HashMap<String, Object> invMap = new HashMap<>();
        invMap.put("userName", userName);
        invMap.put("fishId", fishId);
        botS4DAO.upsertFishInv(invMap);

        // 업적 체크
        String achMsg = checkAchievements(userName, fishId, fishGrade);

        String star = buildStar(fishGrade);
        String emoji = FISH_EMOJI_MAP.getOrDefault(fishId, "🐟");
        String desc  = fish.get("FISH_DESC")  != null ? fish.get("FISH_DESC").toString()  : "";

        StringBuilder sb = new StringBuilder();
        sb.append("🎣 낚시 결과!").append(NL);
        sb.append(star).append(" ").append(fishName);
        if (!desc.isEmpty()) {
            sb.append(NL).append(emoji).append(" ").append(desc);
        }
        if (!achMsg.isEmpty()) {
            sb.append(NL).append(achMsg);
        }
        return sb.toString();
    }

    @Override
    public List<HashMap<String, Object>> selectFishInv(String userName) {
        return botS4DAO.selectFishInv(userName);
    }

    @Override
    public List<HashMap<String, Object>> selectUserAchievements(String userName) {
        return botS4DAO.selectUserAchievements(userName);
    }

    @Override
    public List<HashMap<String, Object>> selectAchievementList() {
        return botS4DAO.selectAchievementList();
    }

    // ----------------------------------------------------------------
    // private helpers
    // ----------------------------------------------------------------

    private int[] buildGradeWeights(int rodGrade, int bobberGrade, int maxGrade) {
        int[] w = BASE_GRADE_WEIGHT[Math.min(rodGrade, BASE_GRADE_WEIGHT.length - 1)].clone();
        int bonus = BOBBER_BONUS[Math.min(bobberGrade, BOBBER_BONUS.length - 1)];
        if (bonus > 0 && maxGrade >= 1) {
            // 최고등급 +bonus, ★1에서 -bonus (★1이 bonus보다 작으면 최소 1 유지)
            int deduct = Math.min(bonus, w[1] - 1);
            w[1] -= deduct;
            w[maxGrade] += deduct;
        }
        return w;
    }

    private int rollGrade(int[] weights, int maxGrade) {
        int total = 0;
        for (int g = 1; g <= maxGrade; g++) total += weights[g];
        int r = RND.nextInt(total);
        int cum = 0;
        for (int g = 1; g <= maxGrade; g++) {
            cum += weights[g];
            if (r < cum) return g;
        }
        return 1;
    }

    private HashMap<String, Object> rollFish(List<HashMap<String, Object>> list) {
        double totalWeight = 0;
        for (HashMap<String, Object> f : list) {
            totalWeight += ((Number) f.get("WEIGHT")).doubleValue();
        }
        double r = RND.nextDouble() * totalWeight;
        double cum = 0;
        for (HashMap<String, Object> f : list) {
            cum += ((Number) f.get("WEIGHT")).doubleValue();
            if (r < cum) return f;
        }
        return list.get(0);
    }

    private String checkAchievements(String userName, int fishId, int fishGrade) {
        List<HashMap<String, Object>> allAch = botS4DAO.selectAchievementList();
        List<HashMap<String, Object>> userAch = botS4DAO.selectUserAchievements(userName);
        Set<Integer> cleared = new HashSet<>();
        for (HashMap<String, Object> a : userAch) {
            cleared.add(((Number) a.get("ACH_ID")).intValue());
        }

        StringBuilder msg = new StringBuilder();
        for (HashMap<String, Object> ach : allAch) {
            int achId = ((Number) ach.get("ACH_ID")).intValue();
            if (cleared.contains(achId)) continue;

            String achType  = ach.get("ACH_TYPE").toString();
            int achParam    = Integer.parseInt(ach.get("ACH_PARAM").toString());
            boolean cleared_now = false;

            if ("FIRST_CATCH_GRADE".equals(achType) && fishGrade == achParam) {
                cleared_now = true;
            } else if ("ALL_KIND_GRADE".equals(achType) && fishGrade == achParam) {
                int caught = botS4DAO.selectCaughtKindCountByGrade(userName, achParam);
                int total  = botS4DAO.selectTotalKindCountByGrade(achParam);
                if (total > 0 && caught >= total) {
                    cleared_now = true;
                }
            } else if ("TOTAL_FISHING_COUNT".equals(achType)) {
                int totalCount = botS4DAO.selectTotalFishingLogCount(userName);
                if (totalCount >= achParam) {
                    cleared_now = true;
                }
            }

            if (cleared_now) {
                HashMap<String, Object> achMap = new HashMap<>();
                achMap.put("userName", userName);
                achMap.put("achId", achId);
                botS4DAO.insertUserAch(achMap);
                if (msg.length() > 0) msg.append("♬");
                msg.append("🏆 업적 달성: [").append(ach.get("ACH_NAME")).append("]");
                String rewardType = ach.get("REWARD_TYPE") != null ? ach.get("REWARD_TYPE").toString() : "";
                int rewardGrade   = ach.get("REWARD_GRADE") != null ? ((Number) ach.get("REWARD_GRADE")).intValue() : 0;
                if ("ROD_UNLOCK".equals(rewardType) && rewardGrade > 0) {
                    msg.append(" → ").append(gradeName("낚시대", rewardGrade)).append(" 구매 가능!");
                } else if ("BOBBER_UNLOCK".equals(rewardType) && rewardGrade > 0) {
                    msg.append(" → ").append(gradeName("찌", rewardGrade)).append(" 구매 가능!");
                }
            }
        }
        return msg.toString();
    }

    private static final String[] GRADE_EMOJI = { "", "⭐", "🌟", "💫", "🔶", "💎", "💜", "🔥", "👑" };

    static String buildStar(int grade) {
        String emoji = grade < GRADE_EMOJI.length ? GRADE_EMOJI[grade] : "★";
        return emoji + "★" + grade;
    }

    static String gradeName(String type, int grade) {
        String[] names = { "", "일반", "고급", "희귀", "영웅", "전설" };
        String g = grade < names.length ? names[grade] : "Lv" + grade;
        return g + type;
    }

    @Override
    public List<String> selectS4UserSearch(HashMap<String, Object> map) {
        return botS4DAO.selectS4UserSearch(map);
    }

    @Override
    public String selectTconfigVal(String item) {
        return botS4DAO.selectTconfigVal(item);
    }

    @Override
    @Transactional
    public void upgradeEquip(String userName, String type, int grade) {
        HashMap<String, Object> equip = botS4DAO.selectUserEquip(userName);
        int rodGrade    = equip != null ? ((Number) equip.get("ROD_GRADE")).intValue()    : 1;
        int bobberGrade = equip != null ? ((Number) equip.get("BOBBER_GRADE")).intValue() : 1;
        if ("ROD".equals(type))    rodGrade    = grade;
        if ("BOBBER".equals(type)) bobberGrade = grade;
        HashMap<String, Object> map = new HashMap<>();
        map.put("userName",    userName);
        map.put("rodGrade",    rodGrade);
        map.put("bobberGrade", bobberGrade);
        botS4DAO.updateUserEquip(map);
    }

    @Override
    public HashMap<String, Object> selectMemberByKakaoId(String kakaoId) {
        return botS4DAO.selectMemberByKakaoId(kakaoId);
    }

    @Override
    public void upsertMember(String kakaoId, String userName, String nickname) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("kakaoId",  kakaoId);
        map.put("userName", userName);
        map.put("nickname", nickname);
        botS4DAO.upsertMember(map);
    }
}
