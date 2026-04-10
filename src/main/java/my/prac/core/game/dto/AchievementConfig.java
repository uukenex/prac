package my.prac.core.game.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 전체 업적 수치 중앙 관리 클래스
 *
 * ※ 임계값·보상SP 수정은 이 클래스 하나에서만 처리
 *
 * 업적 종류:
 *   1. 아이템 획득  (빛/어둠/음양)         ACHV_LIGHT_ITEM_N / ACHV_DARK_ITEM_N / ACHV_GRAY_ITEM_N
 *   2. 죽음 횟수                           ACHV_DEATH_N
 *   3. 상점 판매 횟수                       ACHV_SHOP_SELL_N
 *   4. 물약 사용 횟수                       ACHV_POTION_USE_N
 *   5. 직업 스킬 사용 횟수                  ACHV_JOB_SKILL_{직업}_{N}
 *   6. 공격 횟수                           ACHV_ATTACK_TOTAL_N
 *   7. 킬 (몬별/총합/나메/헬)              ACHV_KILL*
 */
public class AchievementConfig {

    private AchievementConfig() {}

    // ============================================================
    // 1. 아이템 획득 업적  (빛 / 어둠 / 음양)
    //    GAIN_TYPE 상수
    // ============================================================

    public static final String ITEM_TYPE_LIGHT = "DROP3";  // 빛
    public static final String ITEM_TYPE_DARK  = "DROP5";  // 어둠
    public static final String ITEM_TYPE_GRAY  = "DROP9";  // 음양

    /** 빛/어둠/음양 공통 달성 임계값 */
    public static final int[] ITEM_THRESHOLDS = {
        1, 10, 50, 100, 300, 500, 700, 1000, 1300, 1600, 2000,
        2400, 2800, 3300, 3800, 4300, 4900, 5500, 6100
    };

    /**
     * 아이템 업적 보상 SP
     * ※ 현재 전부 0. 추후 타입/임계값별로 세분화 가능.
     */
    public static int itemRewardSp(String gainType, int threshold) {
        return 0;
    }

    /** 아이템 업적 DB CMD 키 */
    public static String itemCmd(String gainType, int threshold) {
        if (ITEM_TYPE_LIGHT.equals(gainType)) return "ACHV_LIGHT_ITEM_" + threshold;
        if (ITEM_TYPE_DARK.equals(gainType))  return "ACHV_DARK_ITEM_"  + threshold;
        if (ITEM_TYPE_GRAY.equals(gainType))  return "ACHV_GRAY_ITEM_"  + threshold;
        return "ACHV_UNKNOWN_" + threshold;
    }

    static String itemName(String gainType, int th) {
        if (ITEM_TYPE_LIGHT.equals(gainType)) return "빛 아이템 "  + th + "개 획득";
        if (ITEM_TYPE_DARK.equals(gainType))  return "어둠 아이템 " + th + "개 획득";
        if (ITEM_TYPE_GRAY.equals(gainType))  return "음양 아이템 " + th + "개 획득";
        return "아이템 " + th + "개 획득";
    }

    /** 아이템 업적 단건 정의 */
    public static class ItemEntry {
        public final String gainType;
        public final String name;
        public final int    threshold;
        public final int    rewardSp;
        public final String cmd;

        ItemEntry(String gainType, int threshold) {
            this.gainType  = gainType;
            this.threshold = threshold;
            this.rewardSp  = itemRewardSp(gainType, threshold);
            this.cmd       = itemCmd(gainType, threshold);
            this.name      = itemName(gainType, threshold);
        }
    }

    /** 전체 아이템 업적 목록 */
    public static final List<ItemEntry> ITEM_ACHIEVEMENTS;
    static {
        List<ItemEntry> list = new ArrayList<>();
        for (int th : ITEM_THRESHOLDS) {
            list.add(new ItemEntry(ITEM_TYPE_LIGHT, th));
            list.add(new ItemEntry(ITEM_TYPE_DARK,  th));
            list.add(new ItemEntry(ITEM_TYPE_GRAY,  th));
        }
        ITEM_ACHIEVEMENTS = Collections.unmodifiableList(list);
    }

    // ============================================================
    // 2. 죽음 횟수 업적  ACHV_DEATH_N
    //    {임계값, 보상SP}
    // ============================================================
    public static final int[][] DEATH = {
        {1,   100},
        {10,  200},
        {50,  500},
        {100, 1000},
        {300, 3000},
        {500, 10000}
    };

    // ============================================================
    // 3. 상점 판매 횟수 업적  ACHV_SHOP_SELL_N
    //    {임계값, 보상SP}
    // ============================================================
    public static final int[][] SHOP_SELL = {
        {500,   5000},
        {1000,  5000},
        {2000,  10000},
        {3000,  10000},
        {4000,  10000},
        {5000,  20000},
        {6000,  20000},
        {7000,  20000},
        {8000,  20000},
        {9000,  20000},
        {10000, 30000}
    };

    // ============================================================
    // 4. 물약 사용 횟수 업적  ACHV_POTION_USE_N
    //    {임계값, 보상SP}
    // ============================================================
    public static final int[][] POTION_USE = {
        {10,   1000},
        {50,   3000},
        {100,  8000},
        {300,  15000},
        {500,  25000},
        {1000, 50000}
    };

    // ============================================================
    // 5. 직업 스킬 사용 횟수 업적  ACHV_JOB_SKILL_{직업}_{N}
    // ============================================================
    public static final int[] JOB_SKILL_THRESHOLDS = {
        1, 10, 30, 50, 100, 150,
        200, 250, 300, 350, 400, 450,
        500, 600, 700, 800, 900, 1000,
        1200, 1400, 1600, 1800, 2000, 2300, 2600, 3000
    };

    /** 직업 스킬 사용 보상 SP = 임계값 × 10 */
    public static int jobSkillReward(int threshold) {
        return threshold * 10;
    }

    // ============================================================
    // 6. 공격 횟수 업적  ACHV_ATTACK_TOTAL_N
    //    임계값 → buildKillThresholds(KILL_TOTAL_MAX)
    //    보상   → killTotalReward(th, false)
    // ============================================================

    // ============================================================
    // 7. 킬 횟수 업적
    //    몬별  ACHV_KILL{N}_MON_{monNo}
    //    총합  ACHV_KILL_TOTAL_N
    //    나메  ACHV_KILL_NIGHTMARE_TOTAL_N
    //    헬    ACHV_KILL_HELL_TOTAL_N
    // ============================================================

    /** 몬스터별 킬 업적 최대 임계값 */
    public static final int KILL_PER_MON_MAX = 50000;
    /** 총합 킬/공격 업적 최대 임계값 */
    public static final int KILL_TOTAL_MAX   = 100000;

    /**
     * 킬/공격 임계값 배열 생성
     * 구간: 1, 50, 100, 300, 500, 1000, 2000, ..., max
     */
    public static int[] buildKillThresholds(int max) {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(50);
        list.add(100);
        list.add(300);
        list.add(500);
        for (int th = 1000; th <= max; th += 1000) list.add(th);
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    /**
     * 총합 킬 보상 SP
     * 특별 구간: 1→10,000 / 1,000→1,000,000 / 10,000→10,000,000 / 50,000→100,000,000
     * 나머지: th × 255
     * nightmare/헬: 일반의 3배
     */
    public static int killTotalReward(int th, boolean nightmare) {
        int val;
        if      (th == 1)     val = 10_000;
        else if (th == 1000)  val = 1_000_000;
        else if (th == 10000) val = 10_000_000;
        else if (th == 50000) val = 100_000_000;
        else                  val = th * 255;
        return nightmare ? val * 3 : val;
    }

    /** 몬스터별 킬 보상 SP = 임계값 × 몬스터번호 / 2 */
    public static int killPerMonReward(int th, int monNo) {
        return th * monNo / 2;
    }
}
