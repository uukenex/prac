package my.prac.core.game.dto;

/**
 * [S3 헬보스] HP 단위 변환 클래스
 *
 * EXT 단위 체계 (SP 단위 개념과 동일):
 *   EXT = null / '0' → ×1          (기본)
 *   EXT = 'a'        → ×10,000     (10000 HP = 1a)
 *   EXT = 'b'        → ×100,000,000 (10000a = 1b)
 *
 * 사용 예:
 *   new HellBossHp(5, "a").getRaw()  → 50,000
 *   HellBossHp.toDisplay(50_000)     → "5a"
 *   HellBossHp.toDbNum(50_000)       → 5
 *   HellBossHp.toDbExt(50_000)       → "a"
 */
public class HellBossHp {

    public static final long UNIT = 10_000L;

    private final long raw;

    /** DB 값(hp, ext)을 받아 raw long으로 변환 */
    public HellBossHp(long hp, String ext) {
        if ("a".equalsIgnoreCase(ext)) {
            this.raw = hp * UNIT;
        } else if ("b".equalsIgnoreCase(ext)) {
            this.raw = hp * UNIT * UNIT;
        } else {
            this.raw = hp;
        }
    }

    /** raw HP 값을 바로 사용 */
    public HellBossHp(long rawHp) {
        this.raw = rawHp;
    }

    /** raw HP 반환 */
    public long getRaw() {
        return raw;
    }

    // --------------------------------------------------------
    // DB 저장용 변환 (static)
    // --------------------------------------------------------

    /**
     * raw HP → DB 저장 숫자값
     * 예: 50_000 → 5 (단위 'a')
     */
    public static long toDbNum(long rawHp) {
        if (rawHp >= UNIT * UNIT) return rawHp / (UNIT * UNIT);
        if (rawHp >= UNIT)        return rawHp / UNIT;
        return rawHp;
    }

    /**
     * raw HP → DB 저장 EXT 문자 (없으면 null)
     * 예: 50_000 → "a"
     */
    public static String toDbExt(long rawHp) {
        if (rawHp >= UNIT * UNIT) return "b";
        if (rawHp >= UNIT)        return "a";
        return null;
    }

    // --------------------------------------------------------
    // 표시용 변환 (static)
    // --------------------------------------------------------

    /**
     * raw HP → 표시 문자열
     * 예: 50_000 → "5a", 200 → "200"
     */
    public static String toDisplay(long rawHp) {
        if (rawHp >= UNIT * UNIT) return (rawHp / (UNIT * UNIT)) + "b";
        if (rawHp >= UNIT)        return (rawHp / UNIT) + "a";
        return String.valueOf(rawHp);
    }

    /** 인스턴스 표시용 */
    public String toDisplay() {
        return toDisplay(raw);
    }

    @Override
    public String toString() {
        return toDisplay();
    }
}
