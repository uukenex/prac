package my.prac.core.prjbot.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [2026-10-28] "여러 명이 동시에 하면 서버 부하 -- 게임이 끝날 때 한 번에 저장해도 될 것 같다" 요청 -- 광산 위치 저장 모아 쓰기.
 *
 * <p>광산 화면은 걷는 동안 2.5초마다 위치를 저장한다(/api/tower-mine-pos -> mineSavePos). 그 저장만 바로 UPDATE하지 않고 여기에 모아 두었다가
 * {@code S5MineSessionLog}의 스케줄(5초마다)이 쓴다: 걷는 중이면 처음 쌓인 뒤 15초마다, 멈췄거나 나갔으면(마지막 저장 5초 뒤) 바로, 서버가 내려갈 때 전부.
 * 광석/아이템/경험치/체력 같은 다른 저장은 모두 그대로 즉시 쓴다.</p>
 *
 * <ul>
 *   <li>아직 안 쓴 위치는 광산 유저 행 조회(selectMineUser/selectMineUserForUpdate/selectAllMineUsers) 결과에 덮어서 돌려준다
 *       (S5RequestCache.call). 저장된 POS_AT_MS보다 새 위치일 때만 덮으므로, 포탈/부활/캐기처럼 바로 쓰는 위치 저장이 뒤에 있으면 그쪽이 이긴다.</li>
 *   <li>쓸 때는 updateMinePosIfNewer(POS_AT_MS가 더 오래된 경우에만 UPDATE)라서 그 사이 바로 쓴 위치를 덮어쓰지 않는다.</li>
 *   <li>맵 리셋(resetAllMinePos)은 모아 둔 위치를 모두 버린다.</li>
 *   <li>서버가 갑자기 죽으면 마지막 15초 안의 걸음만 사라진다(다음 입장 때 그 전 저장 위치에서 시작).</li>
 * </ul>
 */
public final class S5MinePosBuffer {
    /** 위치를 덮어 줄 조회. */
    static final Set<String> POS_READS = new HashSet<String>(Arrays.asList("selectMineUser", "selectMineUserForUpdate", "selectAllMineUsers"));
    /** 위치를 쓰는 쓰기(atMs 인자) -- 그보다 오래된 모아 둔 위치는 지운다. */
    static final Set<String> POS_WRITES = new HashSet<String>(Arrays.asList("updateMinePos", "updateMineUser", "updateMinePosIfNewer"));

    public static final long FLUSH_EVERY_MS = 15_000L;
    public static final long FLUSH_IDLE_MS = 5_000L;

    public static final class Pos {
        public final String userName, mapId;
        public final int x, y;
        public final long atMs, since;
        Pos(String userName, String mapId, int x, int y, long atMs, long since) {
            this.userName = userName; this.mapId = mapId; this.x = x; this.y = y; this.atMs = atMs; this.since = since;
        }
    }

    private static final ConcurrentHashMap<String, Pos> PENDING = new ConcurrentHashMap<String, Pos>();

    private S5MinePosBuffer() {}

    /** 위치 저장(바로 쓰지 않고 모은다). 이미 더 새 위치가 있으면 무시. */
    public static void put(final String userName, final String mapId, final int x, final int y, final long atMs) {
        final long now = System.currentTimeMillis();
        PENDING.compute(userName, (k, old) -> {
            if (old != null && old.atMs > atMs) return old;
            return new Pos(userName, mapId, x, y, atMs, old == null ? now : old.since);
        });
    }

    /** 위치를 바로 쓴 쓰기가 성공했다 -- 그 시각까지의 모아 둔 위치는 필요 없다. */
    static void written(String userName, final long atMs) {
        PENDING.computeIfPresent(userName, (k, p) -> p.atMs <= atMs ? null : p);
    }

    static void clear() {
        PENDING.clear();
    }

    /** 쓸 차례인 위치들(all = 전부). 지우지는 않는다 -- 쓰고 나면 written()이 지운다(쓰는 동안에도 조회가 최신 위치를 보도록). */
    public static List<Pos> due(long now, boolean all) {
        List<Pos> out = new ArrayList<Pos>();
        for (Pos p : PENDING.values()) {
            if (all || now - p.since >= FLUSH_EVERY_MS || now - p.atMs >= FLUSH_IDLE_MS) out.add(p);
        }
        return out;
    }

    /** 쓰다가 실패한 위치(유저 행이 없어진 경우 등) -- 그대로면 버린다(다음에 같은 걸 계속 쓰지 않게). */
    public static void drop(Pos p) {
        PENDING.remove(p.userName, p);
    }

    public static int size() {
        return PENDING.size();
    }

    /** 광산 유저 행(들)에 아직 안 쓴 최신 위치를 덮는다(행 맵을 고친다 -- 캐시 원본이 아니라 돌려줄 복사본/새 결과). */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object overlay(Object r) {
        if (r == null || PENDING.isEmpty()) return r;
        if (r instanceof Map) {
            overlayRow((Map) r);
        } else if (r instanceof List) {
            for (Object o : (List) r) if (o instanceof Map) overlayRow((Map) o);
        }
        return r;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void overlayRow(Map row) {
        Object u = row.get("USER_NAME");
        if (!(u instanceof String)) return;
        Pos p = PENDING.get(u);
        if (p == null) return;
        Object at = row.get("POS_AT_MS");
        long rowAt = at instanceof Number ? ((Number) at).longValue() : Long.MIN_VALUE;
        if (p.atMs <= rowAt) return;
        row.put("MAP_ID", p.mapId);
        row.put("POS_X", BigDecimal.valueOf(p.x));
        row.put("POS_Y", BigDecimal.valueOf(p.y));
        row.put("POS_AT_MS", BigDecimal.valueOf(p.atMs));
    }
}
