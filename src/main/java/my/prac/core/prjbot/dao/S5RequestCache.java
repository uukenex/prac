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
            "selectNotice", "selectNoticeSeenVersion",
            // [2026-10-28] 광산 -- 유저 단위 공유 캐시(아래 USER_READS)
            "selectMineUser", "selectMineItems", "selectUserOres"));

    // ---- [2026-10-28] "여러 명이 동시에 하면 서버가 부하 걸리는 것 같다, 빠르게(캐시 등)" -- 광산 유저 단위 공유 캐시 ----
    //  광산 요청은 거의 매번 selectMineItems(아이템 전부)/selectMineUser/selectUserOres를 다시 읽는다(위치 알림 1초, 전투/한기 턴 1초, 위치 저장 2.5초, 캐기마다).
    //  이 세 조회를 요청 사이에서도 재사용하되, 무효화는 테이블 전체가 아니라 "테이블|유저" 단위로 한다(다른 유저가 캐도 내 캐시는 그대로).
    //  - 쓰기 메서드에 userName 인자(@Param("userName") 또는 맵의 userName)가 있으면 그 유저 키만, 없으면(전체 리셋/0시 회복 등) 테이블 키를 올린다.
    //    유저 단위 항목은 테이블 키와 유저 키 버전을 둘 다 들고 있어서 어느 쪽이 올라가도 버린다.
    //  - 커밋 전 쓰기가 진행 중인 유저 키(PENDING > 0)에는 새 항목을 넣지 않는다 -- 쓰기 트랜잭션이 커밋된 직후 끝날 때 버전을 올리기 전의
    //    아주 짧은 틈에 다른 요청이 옛 값을 넣어 두는 일이 없게(잠금을 잡고 다시 읽는 흐름이 옛 값을 받으면 덮어쓰기가 사라질 수 있다).
    //  - 이 세 테이블을 읽는 다른 조회는 공유 캐시에 넣지 않는다(유저 키로 무효화되지 않으므로).
    private static final Set<String> USER_TABLES = new HashSet<String>(Arrays.asList("TBOT_S5_MINE_USER", "TBOT_S5_MINE_ITEM", "TBOT_S5_USER_ORE"));
    private static final Set<String> USER_READS = new HashSet<String>(Arrays.asList("selectMineUser", "selectMineItems", "selectUserOres"));
    private static final long MINE_USER_TTL_MS = 60 * 1000L;
    /** 유저 키("테이블|유저") -> 커밋 전인 쓰기 수(트랜잭션 안이면 끝날 때, 밖이면 쓰기 직후 줄인다). */
    private static final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> PENDING = new ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>();
    private static final ConcurrentHashMap<Method, String[]> PARAM_NAMES = new ConcurrentHashMap<Method, String[]>();

    /** [2026-10-28] 광석 증감 알림(광산 접속 기록 S5MineSessionLog) -- 커밋된 것만 알린다(롤백된 지급/차감은 알리지 않음). */
    public interface OreListener {
        void ore(String userName, String oreCode, int delta);
        /** 요청이 끝날 때(필터) -- 그 요청에서 쓰다 남은 스레드 값 정리. */
        void requestEnd();
    }
    private static volatile OreListener oreListener;
    private static final ThreadLocal<List<Object[]>> TX_ORE = new ThreadLocal<List<Object[]>>();

    public static void setOreListener(OreListener l) {
        oreListener = l;
    }

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
        for (String m : USER_READS) SHARED_TTL_MS.put(m, MINE_USER_TTL_MS); // [2026-10-28]
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
        TX_ORE.remove();
        OreListener ol = oreListener;
        if (ol != null) {
            try { ol.requestEnd(); } catch (Throwable t) { logger.warn("S5RequestCache: ore listener requestEnd failed", t); }
        }
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
        Object r = callCached(target, method, args, name);
        // [2026-10-28] 광산 위치 저장을 모아 쓰는 동안(S5MinePosBuffer) 광산 유저 행 조회에 아직 안 쓴 최신 위치를 덮어 준다.
        if (S5MinePosBuffer.POS_READS.contains(name)) r = S5MinePosBuffer.overlay(r);
        return r;
    }

    private static Object callCached(Object target, Method method, Object[] args, String name) throws Throwable {
        if (!CACHED_READS.contains(name)) {
            boolean write = !name.startsWith("select") && !name.startsWith("count");
            String user = write ? userArg(method, args) : null;
            List<String> held = write ? preWrite(name, user) : null;
            if ("resetAllMinePos".equals(name)) S5MinePosBuffer.clear(); // 맵 리셋 -- 모아 둔 위치는 버린다(쓰기 전/후 모두)
            try {
                Object res = invoke(target, method, args);
                if (write) afterWriteOk(name, method, args, res, user);
                return res;
            } finally {
                if (write) {
                    invalidate(name); // 쓰기가 예외를 던져도(일부만 반영됐을 수 있어) 비운다
                    sharedWrite(name, user);
                    for (String k : held) pending(k).decrementAndGet(); // 트랜잭션 밖 쓰기 -- 버전을 올린 뒤 풀어 준다
                    if ("resetAllMinePos".equals(name)) S5MinePosBuffer.clear();
                } else if ("selectMineUserForUpdate".equals(name)) {
                    dropScopeTables(USER_TABLES); // 잠근 뒤엔 같은 요청에서 잠그기 전에 읽어 둔 광산 값을 쓰지 않는다
                }
            }
        }
        if ("selectNotice".equals(name)) return noticeCached(target, method, args);
        Map<String, Entry> scope = SCOPE.get();
        Set<String> tables = READ_TABLES.get(name);
        Long sharedTtl = tables == null ? null : SHARED_TTL_MS.get(name);
        // [2026-10-28] 광산 유저 단위 조회는 유저 키를 붙여 둔다(유저를 모르면 공유하지 않음). 다른 조회가 그 테이블을 읽으면 공유하지 않는다.
        String user = null;
        if (sharedTtl != null) {
            if (USER_READS.contains(name)) {
                user = userArg(method, args);
                if (user == null) sharedTtl = null;
            } else if (intersects(tables, USER_TABLES)) {
                sharedTtl = null;
            }
        }
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
        Set<String> vkeys = sharedTtl == null ? null : versionKeys(tables, user);
        long[] vers = vkeys == null ? null : versionsOf(vkeys);
        long global = GLOBAL_VER.get();
        Object r = invoke(target, method, args);
        Object stored = r == null ? NULL : copy(r);
        if (scope != null) scope.put(key, new Entry(stored, tables));
        if (sharedTtl != null && !dirtyIntersects(vkeys) && !anyPending(vkeys)) sharedPut(key, stored, vkeys, vers, global, sharedTtl);
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
    private static void sharedWrite(String writer, String user) {
        final Set<String> written = writeKeys(WRITE_TABLES.get(writer), user);
        bump(written);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        Set<String> dirty = txDirty();
        if (written == null) dirty.add("*");
        else dirty.addAll(written);
    }

    /** 현재 트랜잭션에서 쓴 키 모음(처음 쓸 때 끝날 때의 정리를 등록한다). 트랜잭션 동기화가 켜져 있을 때만 부른다. */
    private static Set<String> txDirty() {
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
                    for (String t : d) if (t.indexOf('|') > 0) pending(t).decrementAndGet(); // [2026-10-28] 버전을 올린 뒤에 풀어 준다
                }
            });
        }
        return dirty;
    }

    // ---------------------------------------------------------------- [2026-10-28] 광산 유저 단위 키
    /** 쓰는 테이블 -> 버전 키(광산 유저 테이블이고 유저를 알면 "테이블|유저", 아니면 테이블). null이면 대상을 모르는 쓰기. */
    private static Set<String> writeKeys(Set<String> written, String user) {
        if (written == null || user == null || !intersects(written, USER_TABLES)) return written;
        Set<String> out = new HashSet<String>();
        for (String t : written) out.add(USER_TABLES.contains(t) ? t + "|" + user : t);
        return out;
    }

    /** 읽는 테이블 -> 확인할 버전 키(광산 유저 테이블은 테이블 키와 유저 키 둘 다). */
    private static Set<String> versionKeys(Set<String> tables, String user) {
        if (user == null || !intersects(tables, USER_TABLES)) return tables;
        Set<String> out = new HashSet<String>(tables);
        for (String t : tables) if (USER_TABLES.contains(t)) out.add(t + "|" + user);
        return out;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        if (a == null) return false;
        for (String t : a) if (b.contains(t)) return true;
        return false;
    }

    private static java.util.concurrent.atomic.AtomicInteger pending(String key) {
        java.util.concurrent.atomic.AtomicInteger p = PENDING.get(key);
        if (p == null) {
            PENDING.putIfAbsent(key, new java.util.concurrent.atomic.AtomicInteger());
            p = PENDING.get(key);
        }
        return p;
    }

    private static boolean anyPending(Set<String> keys) {
        for (String k : keys) {
            if (k.indexOf('|') < 0) continue;
            java.util.concurrent.atomic.AtomicInteger p = PENDING.get(k);
            if (p != null && p.get() > 0) return true;
        }
        return false;
    }

    /** 쓰기 직전 -- 광산 유저 키를 "쓰는 중"으로 표시한다(버전 올리기보다 먼저). 트랜잭션 안이면 끝날 때 풀고(같은 키는 한 번만),
     *  밖이면 돌려준 키를 부른 쪽이 쓰기 직후 푼다. */
    private static List<String> preWrite(String writer, String user) {
        List<String> held = new ArrayList<String>();
        Set<String> keys = writeKeys(WRITE_TABLES.get(writer), user);
        if (keys == null) return held;
        boolean tx = TransactionSynchronizationManager.isSynchronizationActive();
        for (String k : keys) {
            if (k.indexOf('|') < 0) continue;
            if (tx) {
                if (txDirty().add(k)) pending(k).incrementAndGet();
            } else {
                pending(k).incrementAndGet();
                held.add(k);
            }
        }
        return held;
    }

    /** 이 요청 범위 캐시에서 tables를 읽은 항목을 지운다. */
    private static void dropScopeTables(Set<String> tables) {
        Map<String, Entry> scope = SCOPE.get();
        if (scope == null || scope.isEmpty()) return;
        for (Iterator<Map.Entry<String, Entry>> it = scope.entrySet().iterator(); it.hasNext();) {
            if (intersects(it.next().getValue().tables, tables)) it.remove();
        }
    }

    /** 매퍼 메서드의 인자 이름(@Param). 못 찾으면 null 칸. */
    private static String[] paramNames(Method m) {
        String[] names = PARAM_NAMES.get(m);
        if (names != null) return names;
        java.lang.annotation.Annotation[][] pa = m.getParameterAnnotations();
        names = new String[pa.length];
        for (int i = 0; i < pa.length; i++) {
            for (java.lang.annotation.Annotation a : pa[i]) {
                if (a instanceof org.apache.ibatis.annotations.Param) names[i] = ((org.apache.ibatis.annotations.Param) a).value();
            }
        }
        PARAM_NAMES.put(m, names);
        return names;
    }

    /** 인자 하나 -- @Param(name) 또는 맵 하나를 받는 메서드면 그 맵의 name 값. */
    @SuppressWarnings("rawtypes")
    static Object argOf(Method m, Object[] args, String name) {
        if (args == null) return null;
        String[] names = paramNames(m);
        for (int i = 0; i < names.length && i < args.length; i++) if (name.equals(names[i])) return args[i];
        if (args.length == 1 && args[0] instanceof Map) return ((Map) args[0]).get(name);
        return null;
    }

    static String userArg(Method m, Object[] args) {
        Object u = argOf(m, args, "userName");
        return u instanceof String ? (String) u : null;
    }

    /** 쓰기가 성공한 뒤 -- 광석 증감 알림(커밋 후), 위치 버퍼 정리. */
    private static void afterWriteOk(String name, Method method, Object[] args, Object res, String user) {
        if ("addUserOre".equals(name) || "takeUserOre".equals(name)) {
            if (user == null || oreListener == null) return;
            Object code = argOf(method, args, "oreCode"), qty = argOf(method, args, "qty");
            if (!(code instanceof String) || !(qty instanceof Number)) return;
            if ("takeUserOre".equals(name) && !(res instanceof Number && ((Number) res).intValue() > 0)) return; // 모자라서 안 빠졌다
            int q = ((Number) qty).intValue();
            oreDelta(user, (String) code, "addUserOre".equals(name) ? q : -q);
        } else if (S5MinePosBuffer.POS_WRITES.contains(name)) {
            Object at = argOf(method, args, "atMs");
            if (user != null && at instanceof Number) S5MinePosBuffer.written(user, ((Number) at).longValue());
        }
    }

    private static void oreDelta(String user, String code, int delta) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fireOre(user, code, delta);
            return;
        }
        List<Object[]> l = TX_ORE.get();
        if (l == null) {
            l = new ArrayList<Object[]>();
            TX_ORE.set(l);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCompletion(int status) {
                    List<Object[]> d = TX_ORE.get();
                    TX_ORE.remove();
                    if (d == null || status != STATUS_COMMITTED) return; // 롤백된 증감은 없던 일
                    for (Object[] o : d) fireOre((String) o[0], (String) o[1], (Integer) o[2]);
                }
            });
        }
        l.add(new Object[] { user, code, delta });
    }

    private static void fireOre(String user, String code, int delta) {
        OreListener ol = oreListener;
        if (ol == null) return;
        try {
            ol.ore(user, code, delta);
        } catch (Throwable t) {
            logger.warn("S5RequestCache: ore listener failed", t);
        }
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
