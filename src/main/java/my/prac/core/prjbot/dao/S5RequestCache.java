package my.prac.core.prjbot.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <li>캐시에서 꺼낼 때마다 얕은 복사본(HashMap/List)을 돌려준다 -- 호출한 쪽이 맵에 값을 넣거나 목록을 고치는 코드가 많아서(예: SKILL_MULT)
 *       원본을 그대로 주면 다음 호출에 새어나간다.</li>
 *   <li>읽기(select, count로 시작하는 메서드)가 아닌 메서드가 한 번이라도 불리면 캐시 전체를 비운다(안전 우선) -- 로그/통계만 쓰는
 *       {@link #HARMLESS_WRITES}만 예외. 그래서 "쓰고 나서 바로 다시 읽는" 코드는 항상 새 값을 읽는다.</li>
 *   <li>공지 조회(selectNotice)는 요청과 무관하게 짧게(30초) 캐시한다 -- 웹 화면이 탭마다 3분마다 폴링하는 값이라서. updateNotice가 불리면 즉시 비운다.</li>
 * </ul>
 */
public final class S5RequestCache {
    private static final Logger logger = LoggerFactory.getLogger(S5RequestCache.class);

    private static final Object NULL = new Object();
    private static final ThreadLocal<Map<String, Object>> SCOPE = new ThreadLocal<Map<String, Object>>();
    private static final ThreadLocal<int[]> STAT = new ThreadLocal<int[]>(); // {hit, miss}
    private static final ThreadLocal<Boolean> TX_ARMED = new ThreadLocal<Boolean>();

    /** 요청 범위에서 캐시하는 읽기 메서드. */
    private static final Set<String> CACHED_READS = new HashSet<String>(Arrays.asList(
            "selectUserProgress", "selectUserCompanions", "countUserCompanions",
            "selectUserEquip", "countUserEquip", "selectEquipByCompanion", "selectSkillRateMults",
            "selectUserStat", "selectUserAchievements", "selectUserFloorProgress", "selectUserFloorBest",
            "selectUserTileMaster", "selectVisitedTileNos", "countFullyExploredFloors",
            "selectMonster", "selectFloorInfo", "selectTileMaster", "selectGacha",
            "selectLegendaryMaster", "selectLegendaryMasterList", "selectStar7Portrait", "selectCompanionImageUrl",
            "selectNotice", "selectNoticeSeenVersion"));

    /** 캐시된 조회 결과에 영향을 주지 않는 쓰기(로그/통계) -- 이 메서드들은 캐시를 비우지 않는다. */
    private static final Set<String> HARMLESS_WRITES = new HashSet<String>(Arrays.asList(
            "bumpDiceFaceStat", "bumpActivityHourly", "insertBattleStat", "upsertKakaoMember",
            "upsertAutoHuntLog"));

    private static final long NOTICE_TTL_MS = 30000L;
    private static volatile Object noticeValue;
    private static volatile long noticeAt;

    private S5RequestCache() {}

    public static void begin() {
        TX_ARMED.remove();
        SCOPE.set(new HashMap<String, Object>());
        STAT.set(new int[2]);
    }

    public static void end() {
        int[] st = STAT.get();
        SCOPE.remove();
        STAT.remove();
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
            boolean write = !name.startsWith("select") && !name.startsWith("count") && !HARMLESS_WRITES.contains(name);
            try {
                return invoke(target, method, args);
            } finally {
                if (write) clearAll(name); // 쓰기가 예외를 던져도(일부만 반영됐을 수 있어) 비운다
            }
        }
        if ("selectNotice".equals(name)) return noticeCached(target, method, args);
        Map<String, Object> scope = SCOPE.get();
        if (scope == null) return invoke(target, method, args);
        String key = name + Arrays.deepToString(args);
        Object hit = scope.get(key);
        int[] st = STAT.get();
        if (hit != null) {
            if (st != null) st[0]++;
            return hit == NULL ? null : copy(hit);
        }
        armClearOnTxEnd();
        Object r = invoke(target, method, args);
        scope.put(key, r == null ? NULL : copy(r));
        if (st != null) st[1]++;
        return r;
    }

    /** 트랜잭션 안에서 읽은 값은 아직 커밋 전 상태일 수 있다(롤백되면 사라질 값). 그래서 트랜잭션이 끝나는 순간(커밋/롤백 모두) 캐시를 비운다. */
    private static void armClearOnTxEnd() {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || TX_ARMED.get() != null) return;
        TX_ARMED.set(Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                TX_ARMED.remove();
                Map<String, Object> scope = SCOPE.get();
                if (scope != null) scope.clear();
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

    private static void clearAll(String writer) {
        Map<String, Object> scope = SCOPE.get();
        if (scope != null) scope.clear();
        if ("updateNotice".equals(writer)) noticeValue = null;
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

    static List<String> cachedReadNames() {
        List<String> l = new ArrayList<String>(CACHED_READS);
        Collections.sort(l);
        return l;
    }
}
