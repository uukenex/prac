package my.prac.core.game.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 아이템 획득 업적 정의 클래스
 *
 * 관리 항목:
 *   - name      : 업적 표시 이름
 *   - threshold : 아이템 몇 개 획득 시 달성
 *   - rewardSp  : 달성 보상 SP (현재 0, 추후 설정)
 *   - gainType  : 아이템 분류 (DROP3=빛, DROP5=어둠, DROP9=음양)
 *   - cmd       : DB 업적 키 (예: ACHV_LIGHT_ITEM_100)
 *
 * 사용:
 *   ItemAchievementDef.ALL            → 전체 업적 목록
 *   ItemAchievementDef.byGainType(..) → 특정 타입 업적만
 */
public class ItemAchievementDef {

    // ── 공통 임계값 (빛/어둠/음양 동일 적용) ──
    public static final int[] THRESHOLDS = {
        1, 10, 50, 100, 300, 500, 700, 1000, 1300, 1600, 2000,
        2400, 2800, 3300, 3800, 4300, 4900, 5500, 6100
    };

    // ── GAIN_TYPE 상수 ──
    public static final String TYPE_LIGHT = "DROP3";  // 빛
    public static final String TYPE_DARK  = "DROP5";  // 어둠
    public static final String TYPE_GRAY  = "DROP9";  // 음양

    // ── 필드 ──
    /** 아이템 GAIN_TYPE */
    public final String gainType;
    /** 업적 표시 이름 */
    public final String name;
    /** 달성 임계값 (n개 획득 시 달성) */
    public final int threshold;
    /**
     * 달성 보상 SP
     * ※ 현재 0으로 설정. 추후 값 조정 시 이 클래스에서만 수정.
     */
    public final int rewardSp;
    /** DB/캐시 업적 커맨드 키 (예: ACHV_LIGHT_ITEM_100) */
    public final String cmd;

    public ItemAchievementDef(String gainType, String name, int threshold, int rewardSp) {
        this.gainType  = gainType;
        this.name      = name;
        this.threshold = threshold;
        this.rewardSp  = rewardSp;
        this.cmd       = buildCmd(gainType, threshold);
    }

    private static String buildCmd(String gainType, int threshold) {
        switch (gainType) {
            case TYPE_LIGHT: return "ACHV_LIGHT_ITEM_" + threshold;
            case TYPE_DARK:  return "ACHV_DARK_ITEM_"  + threshold;
            case TYPE_GRAY:  return "ACHV_GRAY_ITEM_"  + threshold;
            default:         return "ACHV_UNKNOWN_"     + threshold;
        }
    }

    // ── 전체 업적 목록 (정적 초기화) ──
    public static final List<ItemAchievementDef> ALL;

    static {
        List<ItemAchievementDef> list = new ArrayList<>();
        for (int th : THRESHOLDS) {
            list.add(new ItemAchievementDef(TYPE_LIGHT, "빛 아이템 "  + th + "개 획득", th, 0));
            list.add(new ItemAchievementDef(TYPE_DARK,  "어둠 아이템 " + th + "개 획득", th, 0));
            list.add(new ItemAchievementDef(TYPE_GRAY,  "음양 아이템 " + th + "개 획득", th, 0));
        }
        ALL = Collections.unmodifiableList(list);
    }

    /**
     * 특정 GAIN_TYPE 업적만 반환 (threshold 오름차순)
     */
    public static List<ItemAchievementDef> byGainType(String gainType) {
        List<ItemAchievementDef> result = new ArrayList<>();
        for (ItemAchievementDef def : ALL) {
            if (gainType.equals(def.gainType)) result.add(def);
        }
        return result;
    }

    @Override
    public String toString() {
        return cmd + " | " + name + " | 보상SP:" + rewardSp;
    }
}
