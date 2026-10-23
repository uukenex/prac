package my.prac.core.prjbot.dao;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * [2026-10-21 성능] 한 요청 안에서 똑같은 읽기 쿼리(selectUserCompanions, selectEquipByCompanion, selectUserStat,
 * selectUserProgress 등)가 전투 한 턴/API 한 번에 여러 번씩 실행되던 문제를 줄이는 요청 범위 읽기 캐시.
 *
 * <ul>
 *   <li>{@link #begin()}/{@link #end()}를 {@code S5RequestCacheFilter}가 요청마다 호출한다. 범위 밖(스케줄러/@Async 스레드 등)에서는
 *       캐시 없이 그대로 DB를 조회한다(기존 동작).</li>
 *   <li>캐시하는 건 {@link #CACHED_READS}에 적은 읽기 메서드뿐이다. 키는 메서드 이름 + 인자.</li>
 *   <li>캐시에서 꺼낼 때마다 얕은 복사본(HashMap/List)을 돌려준다 -- 호출한 쪽이 맵에 값을 넣거나 목록을 고치는 코드가 많아서
 *       원본을 그대로 주면 다음 호출에 새어나간다.</li>
 *   <li><b>무효화는 테이블 단위</b>: BotS5Mapper.xml을 읽어서 (읽기 쿼리 -> 읽는 테이블들), (쓰기 쿼리 -> 쓰는 테이블들)을 자동으로 뽑는다.
 *       쓰기가 불리면 그 쓰기가 건드리는 테이블을 읽은 캐시 항목만 지우고 나머지는 남긴다. 매퍼에서 대응을 못 찾는 쓰기
 *       (select, count로 시작하지 않는 메서드)는 안전하게 캐시 전체를 지운다. 읽기 쿼리의 테이블을 못 뽑으면 그 쿼리는 캐시하지 않는다.</li>
 *   <li>트랜잭션이 <b>롤백</b>되면 캐시 전체를 비운다(롤백될 커밋 전 값을 읽어 둔 채로 남지 않게). 커밋은 읽은 값이 그대로 맞으므로 유지한다.</li>
 *   <li>공지 조회(selectNotice)는 요청과 무관하게 짧게(30초) 캐시한다 -- 웹 화면이 탭마다 3분마다 폴링하는 값이라서. updateNotice가 불리면 즉시 비운다.</li>
 *   <li>[2026-10-22] <b>공유(요청 간) 캐시</b> -- 주사위를 자주 굴려 같은 조회가 계속 도는 것 중 "거의 안 바뀌는 기준 데이터"(몬스터/층/뽑기/업적/전설 마스터, 10분)와
 *       "가끔 바뀌는 유저 데이터"(장비 목록/동료별 장비/유저 층 보드, 60초)만 요청 사이에서도 재사용한다(SHARED_TTL_MS). 진행 상태/HP처럼 매 굴림 바뀌는 건 넣지 않는다.
 *       일관성: 테이블마다 버전 번호를 두고 쓰기가 생기면 올린다(캐시 항목은 읽을 때 버전과 다르면 버린다). 트랜잭션 안에서 쓴 테이블은 그 트랜잭션이 끝날 때(커밋/롤백)
 *       한 번 더 올려서, 커밋 전에 다른 요청이 읽어 둔 옛 값도 무효가 된다. 쓰기를 한 트랜잭션은 자기가 쓴 테이블의 조회 결과를 공유 캐시에 넣지 않는다(커밋 전 값 전파 방지).
 *       이 DAO 밖(직접 SQL 등)에서 바꾼 값은 유효 시간(10분/60초) 안에 반영되고, /갱신(clearShared)으로 즉시 비울 수 있다.</li>
 * </ul>
 */
public final class S5RequestCache {
    private static final Logger logger = LoggerFactory.getLogger(S5RequestCache.class);

    private static final Object NULL = new Object();
    private static final ThreadLocal<Map<String, Entry>> SCOPE = new ThreadLocal<Map<String, Entry>>();
    private static final ThreadLocal<int[]> STAT = new ThreadLocal<int[]>(); // {hit, miss}
    private static final ThreadLocal<Boolean> TX_ARMED = new ThreadLocal<Boolean>();

    private static final class Entry {
        final Object value;
        final Set<String> tables;
        Entry(Object value, Set<String> tables) { this.value = value; this.tables = tables; }
    }

    /** 요청 범위에서 캐시하는 읽기 메서드. */
    private static final Set<String> CACHED_READS = new HashSet<String>(Arrays.asList(
            "selectUserProgress", "selectUserCompanions", "countUserCompanions",
            "selectUserEquip", "countUserEquip", "selectEquipByCompanion", "selectSkillRateMults",
            "selectUserStat", "selectUserAchievements", "selectUserFloorProgress", "selectUserFloorBest", "selectUserFloorBestRange",
            "selectUserTileMaster", "selectVisitedTileNos", "countFullyExploredFloors", "selectAutoHuntLog", "selectUserSpecialVisit",
            "selectMonster", "selectFloorInfo", "selectTileMaster", "selectGacha", "selectGachaList", "selectAchievementList",
            "selectLegendaryMaster", "selectLegendaryMasterList", "selectStar7Portrait", "selectCompanionImageUrl",
            "selectNotice", "selectNoticeSeenVersion"));

    // ---- [2026-10-22] 공유(요청 간) 캐시 ----
    private static final long MASTER_TTL_MS = 10 * 60 * 1000L;
    private static final long USER_TTL_MS = 60 * 1000L;
    private static final Map<String, Long> SHARED_TTL_MS = new HashMap<String, Long>();
    static {
        for (String m : new String[]{ "selectMonster", "selectFloorInfo", "selectTileMaster", "selectGacha", "selectGachaList",
                "selectAchievementList", "selectLegendaryMaster", "selectLegendaryMasterList", "selectStar7Portrait" }) {
            SHARED_TTL_MS.put(m, MASTER_TTL_MS);
        }
        for (String m : new String[]{ "selectUserEquip", "selectEquipByCompanion", "selectUserTileMaster", "selectUserFloorBestRange" }) {
            SHARED_TTL_MS.put(m, USER_TTL_MS);
        }
    }
    private static final int SHARED_MAX = 20000;
    private static final ConcurrentHashMap<String, SharedEntry> SHARED = new ConcurrentHashMap<String, SharedEntry>();
    private static final ConcurrentHashMap<String, AtomicLong> TABLE_VER = new ConcurrentHashMap<String, AtomicLong>();
    private static final AtomicLong GLOBAL_VER = new AtomicLong();
    /** 현재 트랜잭션에서 쓴 테이블들("*" = 대상을 모르는 쓰기). 트랜잭션이 끝나면 비운다. */
    private static final ThreadLocal<Set<String>> TX_DIRTY = new ThreadLocal<Set<String>>();

    private static final class SharedEntry {
        final Object value;
        final String[] tables;
        final long[] vers;
        final long global;
        final long expireAt;
        SharedEntry(Object value, String[] tables, long[] vers, long global, long expireAt) {
            this.value = value; this.tables = tables; this.vers = vers; this.global = global; this.expireAt = expireAt;
        }
    }

    private static final long NOTICE_TTL_MS = 30000L;
    private static volatile Object noticeValue;
    private static volatile long noticeAt;

    /** 매퍼 id -> 읽는 테이블들(읽기) / 쓰는 테이블들(쓰기). 못 뽑았으면 항목이 없다. */
    private static final Map<String, Set<String>> READ_TABLES = new HashMap<String, Set<String>>();
    private static final Map<String, Set<String>> WRITE_TABLES = new HashMap<String, Set<String>>();

    private S5RequestCache() {}

    // ---------------------------------------------------------------- mapper parsing
    private static final Pattern STMT = Pattern.compile("<(select|insert|update|delete)\\b[^>]*?\\bid=\"(\\w+)\"[^>]*>([\\s\\S]*?)</\\1>");
    private static final Pattern TABLE = Pattern.compile("\\b(TBOT_\\w+|TBOARD_\\w+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET = Pattern.compile("(?:INSERT\\s+(?:/\\*[^*]*\\*/\\s*)?INTO|\\bUPDATE|DELETE(?:\\s+FROM)?|MERGE\\s+INTO)\\s+((?:TBOT_|TBOARD_)\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLSQL = Pattern.compile("\\b(BEGIN|CALL|EXECUTE)\\b", Pattern.CASE_INSENSITIVE);

    // 주의: 위의 Pattern 상수들보다 뒤에 있어야 한다(정적 초기화는 선언 순서대로 실행되므로, 앞에 두면 Pattern이 아직 null이라 파싱이 통째로 실패한다).
    static {
        try {
            parseMapper();
        } catch (Throwable t) {
            logger.warn("S5RequestCache: mapper parse failed, falling back to no caching / full invalidation", t);
        }
    }

    static void parseMapper() throws Exception {
        InputStream in = S5RequestCache.class.getResourceAsStream("/mybatis/mapper2.0/BotS5Mapper.xml");
        if (in == null) return;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        } finally {
            in.close();
        }
        String xml = new String(bo.toByteArray(), "UTF-8").replaceAll("(?s)<!--.*?-->", "");
        Matcher m = STMT.matcher(xml);
        while (m.find()) {
            String kind = m.group(1);
            String id = m.group(2);
            String body = m.group(3);
            if ("select".equals(kind)) {
                Set<String> t = new HashSet<String>();
                Matcher tm = TABLE.matcher(body);
                while (tm.find()) t.add(tm.group(1).toUpperCase());
                if (!t.isEmpty()) READ_TABLES.put(id, t);
            } else {
                // 다른 문장을 부르는 PL/SQL 블록 등은 쓰는 곳을 정확히 알 수 없으니 등록하지 않는다(= 전체 무효화).
                if (PLSQL.matcher(body).find()) continue;
                Set<String> t = new HashSet<String>();
                Matcher gm = TARGET.matcher(body);
                while (gm.find()) t.add(gm.group(1).toUpperCase());
                if (!t.isEmpty()) WRITE_TABLES.put(id, t);
            }
        }
    }

    // ---------------------------------------------------------------- scope
    public static void begin() {
        TX_ARMED.remove();
        SCOPE.set(new HashMap<String, Entry>());
        STAT.set(new int[2]);
    }

    public static void end() {
        int[] st = STAT.get();
        SCOPE.remove();
        STAT.remove();
        TX_ARMED.remove();
        TX_DIRTY.remove();
        if (st != null && st[0] > 0 && logger.isDebugEnabled()) {
            logger.debug("S5 request cache hit={} miss={}", st[0], st[1]);
        }
    }

    /** 감싸야 할 DAO(원본 MyBatis 매퍼)를 캐시 프록시로 바꿔준다. */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(final Class<T> iface, final T target) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return S5RequestCache.call(target, method, args);
            }
        });
    }

    static Object call(Object target, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) return invoke(target, method, args);
        String name = method.getName();
        if (!CACHED_READS.contains(name)) {
            boolean write = !name.startsWith("select") && !name.startsWith("count");
            try {
                return invoke(target, method, args);
            } finally {
                if (write) {
                    invalidate(name); // 쓰기가 예외를 던져도(일부만 반영됐을 수 있어) 비운다
                    sharedWrite(name);
                }
            }
        }
        if ("selectNotice".equals(name)) return noticeCached(target, method, args);
        Map<String, Entry> scope = SCOPE.get();
        Set<String> tables = READ_TABLES.get(name);
        Long sharedTtl = tables == null ? null : SHARED_TTL_MS.get(name);
        if (tables == null || (scope == null && sharedTtl == null)) return invoke(target, method, args);
        String key = name + Arrays.deepToString(args);
        int[] st = STAT.get();
        if (scope != null) {
            Entry hit = scope.get(key);
            if (hit != null) {
                if (st != null) st[0]++;
                return hit.value == NULL ? null : copy(hit.value);
            }
        }
        if (sharedTtl != null) {
            Object sv = sharedGet(key);
            if (sv != null) {
                if (scope != null) scope.put(key, new Entry(sv, tables));
                if (st != null) st[0]++;
                return sv == NULL ? null : copy(sv);
            }
        }
        if (scope != null) armClearOnRollback();
        // 공유 캐시에 넣을 때 쓸 버전은 조회 "전"에 잡는다 -- 조회 도중 다른 쓰기가 끼면 그 항목은 바로 무효가 된다.
        long[] vers = sharedTtl == null ? null : versionsOf(tables);
        long global = GLOBAL_VER.get();
        Object r = invoke(target, method, args);
        Object stored = r == null ? NULL : copy(r);
        if (scope != null) scope.put(key, new Entry(stored, tables));
        if (sharedTtl != null && !dirtyIntersects(tables)) sharedPut(key, stored, tables, vers, global, sharedTtl);
        if (st != null) st[1]++;
        return r;
    }

    // ---------------------------------------------------------------- shared cache
    private static AtomicLong ver(String table) {
        AtomicLong v = TABLE_VER.get(table);
        if (v == null) {
            TABLE_VER.putIfAbsent(table, new AtomicLong());
            v = TABLE_VER.get(table);
        }
        return v;
    }

    private static long[] versionsOf(Set<String> tables) {
        long[] v = new long[tables.size()];
        int i = 0;
        for (String t : tables) v[i++] = ver(t).get();
        return v;
    }

    private static Object sharedGet(String key) {
        SharedEntry e = SHARED.get(key);
        if (e == null) return null;
        boolean valid = System.currentTimeMillis() < e.expireAt && e.global == GLOBAL_VER.get();
        for (int i = 0; valid && i < e.tables.length; i++) {
            if (ver(e.tables[i]).get() != e.vers[i]) valid = false;
        }
        if (!valid) {
            SHARED.remove(key, e);
            return null;
        }
        return e.value;
    }

    private static void sharedPut(String key, Object value, Set<String> tables, long[] vers, long global, long ttl) {
        if (SHARED.size() > SHARED_MAX) SHARED.clear(); // 상한 -- 넘치면 통째로 비운다(다시 채워질 뿐)
        SHARED.put(key, new SharedEntry(value, tables.toArray(new String[0]), vers, global, System.currentTimeMillis() + ttl));
    }

    private static boolean dirtyIntersects(Set<String> tables) {
        Set<String> d = TX_DIRTY.get();
        if (d == null || d.isEmpty()) return false;
        if (d.contains("*")) return true;
        for (String t : tables) if (d.contains(t)) return true;
        return false;
    }

    /** 쓰기 -- 그 테이블 버전을 올린다(대상을 모르면 전체). 트랜잭션 안이면 끝날 때 한 번 더 올리도록 기록한다. */
    private static void sharedWrite(String writer) {
        final Set<String> written = WRITE_TABLES.get(writer);
        bump(written);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        Set<String> dirty = TX_DIRTY.get();
        if (dirty == null) {
            dirty = new HashSet<String>();
            TX_DIRTY.set(dirty);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCompletion(int status) {
                    Set<String> d = TX_DIRTY.get();
                    TX_DIRTY.remove();
                    if (d == null) return;
                    if (d.contains("*")) GLOBAL_VER.incrementAndGet();
                    for (String t : d) if (!"*".equals(t)) ver(t).incrementAndGet();
                }
            });
        }
        if (written == null) dirty.add("*");
        else dirty.addAll(written);
    }

    private static void bump(Set<String> tables) {
        if (tables == null) {
            GLOBAL_VER.incrementAndGet();
            return;
        }
        for (String t : tables) ver(t).incrementAndGet();
    }

    /** /갱신 -- 공유 캐시를 모두 비운다(DB를 직접 고친 뒤 바로 반영하고 싶을 때). */
    public static void clearShared() {
        GLOBAL_VER.incrementAndGet();
        SHARED.clear();
    }

    /** 진단용: 공유 캐시 항목 수. */
    public static int sharedSize() {
        return SHARED.size();
    }

    /** 트랜잭션 안에서 읽은 값은 아직 커밋 전 상태일 수 있다. 커밋되면 그대로 맞지만 롤백되면 사라질 값이므로, 롤백 때만 캐시를 비운다. */
    private static void armClearOnRollback() {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || TX_ARMED.get() != null) return;
        TX_ARMED.set(Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                TX_ARMED.remove();
                if (status != STATUS_COMMITTED) {
                    Map<String, Entry> scope = SCOPE.get();
                    if (scope != null) scope.clear();
                }
            }
        });
    }

    private static Object noticeCached(Object target, Method method, Object[] args) throws Throwable {
        Object v = noticeValue;
        if (v != null && System.currentTimeMillis() - noticeAt < NOTICE_TTL_MS) return v == NULL ? null : copy(v);
        Object r = invoke(target, method, args);
        noticeValue = r == null ? NULL : copy(r);
        noticeAt = System.currentTimeMillis();
        return r;
    }

    /** 쓰기 메서드가 불렸을 때: 그 쓰기가 건드리는 테이블을 읽은 캐시 항목만 지운다. 쓰는 테이블을 모르면 전체를 지운다. */
    private static void invalidate(String writer) {
        if ("updateNotice".equals(writer)) noticeValue = null;
        Map<String, Entry> scope = SCOPE.get();
        if (scope == null || scope.isEmpty()) return;
        Set<String> written = WRITE_TABLES.get(writer);
        if (written == null) {
            scope.clear();
            return;
        }
        for (Iterator<Map.Entry<String, Entry>> it = scope.entrySet().iterator(); it.hasNext();) {
            Set<String> read = it.next().getValue().tables;
            for (String w : written) {
                if (read.contains(w)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** HashMap/List(안의 HashMap 포함)는 새로 복사해서, 호출한 쪽이 고쳐도 캐시 원본이 변하지 않게 한다. 값(문자열/숫자 등)은 불변이라 그대로 둔다. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object copy(Object o) {
        if (o instanceof HashMap) return new HashMap((HashMap) o);
        if (o instanceof List) {
            List src = (List) o;
            List out = new ArrayList(src.size());
            for (Object e : src) out.add(copy(e));
            return out;
        }
        return o;
    }

    /** 테스트/진단용: 현재 스레드의 요청 캐시가 열려 있는지. */
    public static boolean active() {
        return SCOPE.get() != null;
    }

    /** 테스트/진단용: 매퍼에서 뽑은 대응표 요약. */
    public static String describe(String id) {
        return id + " reads=" + READ_TABLES.get(id) + " writes=" + WRITE_TABLES.get(id);
    }

    static List<String> cachedReadNames() {
        List<String> l = new ArrayList<String>(CACHED_READS);
        Collections.sort(l);
        return l;
    }
}
