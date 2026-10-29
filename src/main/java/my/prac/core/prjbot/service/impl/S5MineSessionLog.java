package my.prac.core.prjbot.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.dao.S5MinePosBuffer;
import my.prac.core.prjbot.dao.S5RequestCache;

/**
 * [2026-10-28] 광산 접속 기록 -- "로그 페이지에서 행동마다 한 줄씩이라 보기 힘들다, 한 판(입장 -> 한 일 -> 퇴장)을 묶어서,
 * 광석/보석/퀘스트 구매 기록과 사용(소모) 기록도" 요청, 그리고 "여러 명이 동시에 하면 서버 부하 -- 게임이 끝날 때 한 번에 저장" 요청.
 *
 * <p>예전엔 캘 때/처치할 때마다 TBOT_S5_WORD_HIS에 한 줄씩(NCLOB) INSERT했다(하루 1만 8천 줄, 몰릴 땐 분당 140줄). 이제 광산 화면의 행동은
 * 메모리의 "접속(세션)"에 모았다가 몇 줄로 묶어 쓴다:</p>
 * <ul>
 *   <li>세션 시작 = "[웹] 광산 입장"(화면을 열 때/다른 탭에서 돌아올 때). 입장 기록 없이 행동이 오면(서버 재시작 직후 등) 그때 "이어서" 시작.</li>
 *   <li>세션 끝 = "[웹] 광산 퇴장"(나가기/창 숨김 beacon), 또는 위치 알림(1초마다)과 행동이 30초 넘게 없을 때(창을 강제로 닫음/연결 끊김),
 *       또는 서버 종료(@PreDestroy). 끝나면 5초 안에 남은 내용을 쓴다.</li>
 *   <li>진행 중에도 2분마다 그때까지 모인 것을 한 줄로 써 둔다(서버가 갑자기 죽어도 최대 2분만 잃게). 한 세션의 줄들은 같은 sid로 묶여
 *       로그 뷰어(s5_battle_log_view.jsp)가 카드 하나로 합쳐 보여 준다.</li>
 *   <li>한 줄 = REQ "[웹] 광산 기록 #sid (순번) 요약", RES = JSON {v, sid, seq, start, sm, ev[], (end, em, why)}. 로그 뷰어 조회가 RES를
 *       TO_NCHAR로 읽으므로(NVARCHAR2 4000바이트, 국가 문자셋 UTF8) 한 줄을 3800바이트 아래로 나눈다.</li>
 *   <li>캐기/처치는 이어지는 동안 한 항목으로 합친다(횟수, 얻은 광석/보석, 경험치, 레벨). 구매/제작/합성/강화/이동/쓰러짐 등은 하나씩, 결과 문구와 함께.</li>
 *   <li>광석 증감(얻음/씀)은 DAO의 addUserOre/takeUserOre에서 커밋된 것만 받는다(S5RequestCache.OreListener) -- 어떤 기능이 광석을 쓰거나 주든
 *       빠짐없이 그 요청의 행동에 붙는다(행동 기록이 없는 요청의 증감은 "광석 변동"으로).</li>
 *   <li>광산상점 구매(탑 화면에서, 광산 밖)는 세션이 없으면 예전처럼 바로 한 줄.</li>
 * </ul>
 * 위치 저장 모아 쓰기(S5MinePosBuffer)도 이 스케줄이 쓴다. 이 클래스는 인터페이스를 구현하지 않는다(JDK 프록시가 되면 @Scheduled가 안 붙는다).
 */
@Component
public class S5MineSessionLog {
    private static final Logger logger = LoggerFactory.getLogger(S5MineSessionLog.class);

    public static final String REQ_PREFIX = "[웹] 광산 기록";
    static final long IDLE_MS = 30_000L;
    static final long CHECKPOINT_MS = 120_000L;
    static final int MAX_BYTES = 3800;
    static final int MAX_EVENTS = 1500;
    static final int MSG_MAX = 160;
    static final int LABEL_MAX = 60;
    static final int RETRY_MAX = 500;

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO s5Dao;
    @Resource(name = "core.prjbot.BotDAO")
    BotDAO botDao;

    private static final ObjectMapper JSON = new ObjectMapper();

    static final class Ev {
        long t;
        String k, a, m;
        int n, lv;
        long x;
        LinkedHashMap<String, Integer> o;
    }

    static final class Session {
        final String sid, user;
        final long start;
        final String sm;
        volatile long lastSeen;
        long lastWrite;
        int seq = 1;
        final List<Ev> evs = new ArrayList<Ev>();
        Ev digRun, killRun;
        int dropped;
        long end;
        String em, why;
        Session(String user, long start, String sm) {
            this.user = user; this.start = start; this.sm = sm; this.lastSeen = start; this.lastWrite = start;
            this.sid = Long.toString(start, 36).substring(Math.max(0, Long.toString(start, 36).length() - 5))
                    + Integer.toString((int) (Math.random() * 1296), 36);
        }
    }

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();
    private static final ConcurrentLinkedQueue<Session> CLOSED = new ConcurrentLinkedQueue<Session>();
    private static final ConcurrentLinkedQueue<HashMap<String, Object>> RETRY = new ConcurrentLinkedQueue<HashMap<String, Object>>();
    /** [2026-10-28] "최근 캔 곳에 최근 획득이나 구매한 걸 노출, 사용한 건 안 나와도 돼" -- 유저마다 최근 RECENT_MAX건(메모리, 서버를 다시 켜면 비어 있다).
     *  획득 = 행동에 붙은 광석/보석 증가(처치/보스/상자/연속 채광/보상 등, 캐기는 "최근 캔 곳"에 따로 있어 뺀다),
     *  구매 = 광석을 내고 한 행동(상점/대장간/제작/합성 등 -- 결과 문구). 쓴 광석(감소) 자체는 보여 주지 않는다. */
    static final int RECENT_MAX = 30;
    private static final ConcurrentHashMap<String, java.util.LinkedList<HashMap<String, Object>>> RECENT = new ConcurrentHashMap<String, java.util.LinkedList<HashMap<String, Object>>>();

    private static void noteRecent(String user, String kind, String req, String res, LinkedHashMap<String, Integer> ore, long now) {
        if ("in".equals(kind) || "out".equals(kind) || "dig".equals(kind) || "move".equals(kind) || "died".equals(kind)) return;
        if (ore == null || ore.isEmpty()) return;
        String src = "kill".equals(kind) ? "처치" : "boss".equals(kind) ? "보스 처치" : req == null ? "획득" : label(req);
        boolean spent = false;
        for (Integer v : ore.values()) if (v != null && v < 0) spent = true;
        java.util.LinkedList<HashMap<String, Object>> l = RECENT.computeIfAbsent(user, u -> new java.util.LinkedList<HashMap<String, Object>>());
        synchronized (l) {
            if (spent && "act".equals(kind)) { // 광석을 내고 한 행동 = 구매/제작
                HashMap<String, Object> e = new HashMap<String, Object>();
                e.put("at", now);
                e.put("t", "buy");
                e.put("src", src);
                e.put("text", trim(res == null ? src : res, 70));
                l.addFirst(e);
            }
            for (Map.Entry<String, Integer> o : ore.entrySet()) {
                if (o.getValue() == null || o.getValue() <= 0) continue;
                HashMap<String, Object> e = new HashMap<String, Object>();
                e.put("at", now);
                e.put("t", "get");
                e.put("src", src);
                e.put("name", o.getKey());
                e.put("qty", o.getValue());
                l.addFirst(e);
            }
            while (l.size() > RECENT_MAX) l.removeLast();
        }
    }

    /** 최근 획득/구매(새것부터). */
    public static List<HashMap<String, Object>> recent(String user) {
        java.util.LinkedList<HashMap<String, Object>> l = user == null ? null : RECENT.get(user);
        if (l == null) return new ArrayList<HashMap<String, Object>>();
        synchronized (l) {
            return new ArrayList<HashMap<String, Object>>(l);
        }
    }

    /** 이 요청에서 커밋된 광석 증감(유저 -> 이름 -> 증감) -- 다음 행동 기록에 붙인다. */
    private static final ThreadLocal<Map<String, LinkedHashMap<String, Integer>>> REQ_ORE = new ThreadLocal<Map<String, LinkedHashMap<String, Integer>>>();

    @PostConstruct
    public void init() {
        S5RequestCache.setOreListener(new S5RequestCache.OreListener() {
            @Override
            public void ore(String userName, String oreCode, int delta) {
                onOre(userName, oreCode, delta);
            }

            @Override
            public void requestEnd() {
                onRequestEnd();
            }
        });
    }

    // ---------------------------------------------------------------- 들어오는 쪽(컨트롤러/DAO)

    /** 광산 행동 하나. true = 세션에 담았다(묶어서 나중에 씀), false = 부른 쪽이 예전처럼 바로 한 줄로 쓴다. out = 서비스 결과(처치 경험치/레벨 등, 없어도 됨). */
    public static boolean action(String user, String req, String res, Map<String, Object> out) {
        if (user == null || user.trim().isEmpty() || req == null) return false;
        long now = System.currentTimeMillis();
        String kind = kindOf(req, res, out);
        LinkedHashMap<String, Integer> ore = takeReqOre(user);
        noteRecent(user, kind, req, res, ore, now); // [2026-10-28] 최근 획득/구매
        if ("in".equals(kind)) {
            Session old = SESSIONS.remove(user);
            if (old != null) close(old, now, null, "다시 입장");
            Session s = new Session(user, now, trim(res, MSG_MAX));
            synchronized (s) {
                add(s, ev(now, "in", "입장", trim(res, MSG_MAX), ore));
            }
            SESSIONS.put(user, s);
            return true;
        }
        Session s = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Session c = SESSIONS.get(user);
            if (c == null) {
                if ("out".equals(kind) || req.startsWith("[웹] 광산상점 구매")) { putBackOre(user, ore); return false; }
                c = SESSIONS.computeIfAbsent(user, u -> new Session(u, now, "(입장 기록 없이 이어서 -- 서버 재시작 등)"));
            }
            synchronized (c) {
                if (c.why != null) continue; // 방금 오래 소식 없음으로 닫혔다 -- 새 세션으로
                c.lastSeen = now;
            }
            s = c;
            break;
        }
        if (s == null) { putBackOre(user, ore); return false; }
        synchronized (s) {
            if (s.why != null) { putBackOre(user, ore); return false; } // (거의 없음) 그 사이 닫힘 -- 예전처럼 한 줄로
            s.lastSeen = now;
            if ("dig".equals(kind)) {
                if (s.digRun == null) { s.digRun = ev(now, "dig", "채굴", null, null); add(s, s.digRun); }
                s.digRun.n++;
                merge(s.digRun, ore);
            } else if ("kill".equals(kind)) {
                if (s.killRun == null) { s.killRun = ev(now, "kill", "처치", null, null); add(s, s.killRun); }
                s.killRun.n++;
                s.killRun.x += longOf(out == null ? null : out.get("expGain"));
                int lv = levelOf(out);
                if (lv > 0) s.killRun.lv = lv;
                merge(s.killRun, ore);
                if (out != null && out.get("dropName") != null) { // [2026-10-29] "무기 얻으면 로그에 뜨지?" -- 처치 묶음과 따로 한 줄(관리자 로그엔 등급 이름도)
                    Object g = out.get("dropGrade"), sh = out.get("dropShard");
                    String gn = g instanceof Integer && (Integer) g >= 0 && (Integer) g < GRADE_NAMES.length ? GRADE_NAMES[(Integer) g] : "?";
                    add(s, ev(now, "drop", "무기 드랍", out.get("dropName") + " (" + gn + ")" + (sh != null ? " -- 이미 있어 분해: " + out.get("dropShardName") + " +" + sh : ""), null));
                }
            } else {
                Ev e = ev(now, kind, label(req), trim(res, MSG_MAX), ore);
                if ("boss".equals(kind)) { e.x = longOf(out == null ? null : out.get("expGain")); e.lv = levelOf(out); }
                add(s, e);
                s.digRun = null;
                s.killRun = null;
            }
        }
        if ("out".equals(kind)) {
            if (SESSIONS.remove(user, s)) close(s, now, trim(res, MSG_MAX), "퇴장");
        }
        return true;
    }

    /** 광산 화면이 살아 있다(위치 알림/위치 저장) -- 세션이 있을 때만 마지막 시각을 갱신. */
    public static void touch(String user) {
        if (user == null) return;
        Session s = SESSIONS.get(user);
        if (s != null) s.lastSeen = System.currentTimeMillis();
    }

    private static void onOre(String user, String code, int delta) {
        if (delta == 0) return;
        String name = oreName(code);
        if (S5RequestCache.active()) { // 요청 안 -- 이 요청의 행동 기록에 붙인다
            Map<String, LinkedHashMap<String, Integer>> m = REQ_ORE.get();
            if (m == null) { m = new HashMap<String, LinkedHashMap<String, Integer>>(); REQ_ORE.set(m); }
            LinkedHashMap<String, Integer> o = m.get(user);
            if (o == null) { o = new LinkedHashMap<String, Integer>(); m.put(user, o); }
            o.merge(name, delta, Integer::sum);
            return;
        }
        LinkedHashMap<String, Integer> o = new LinkedHashMap<String, Integer>();
        o.put(name, delta);
        orphanOre(user, o);
    }

    /** 요청이 끝났는데 행동 기록에 안 붙은 광석 증감 -- 세션이 있으면 "광석 변동"으로 남긴다. */
    private static void onRequestEnd() {
        Map<String, LinkedHashMap<String, Integer>> m = REQ_ORE.get();
        REQ_ORE.remove();
        if (m == null) return;
        for (Map.Entry<String, LinkedHashMap<String, Integer>> e : m.entrySet()) orphanOre(e.getKey(), e.getValue());
    }

    private static void orphanOre(String user, LinkedHashMap<String, Integer> o) {
        if (o == null || o.isEmpty()) return;
        Session s = SESSIONS.get(user);
        if (s == null) return; // 광산 밖(채팅 명령 등) -- 그쪽 로그가 따로 있다
        long now = System.currentTimeMillis();
        noteRecent(user, "etc", null, null, o, now); // [2026-10-28] 보상 등(행동 기록 없이 들어온 증가)
        synchronized (s) {
            add(s, ev(now, "etc", "광석 변동", null, o));
        }
    }

    private static LinkedHashMap<String, Integer> takeReqOre(String user) {
        Map<String, LinkedHashMap<String, Integer>> m = REQ_ORE.get();
        return m == null ? null : m.remove(user);
    }

    private static void putBackOre(String user, LinkedHashMap<String, Integer> o) {
        if (o == null || o.isEmpty()) return;
        Map<String, LinkedHashMap<String, Integer>> m = REQ_ORE.get();
        if (m != null) m.put(user, o);
    }

    // ---------------------------------------------------------------- 분류/모양

    static String kindOf(String req, String res, Map<String, Object> out) {
        if (req.startsWith("[웹] 광산 입장")) return "in";
        if (req.startsWith("[웹] 광산 퇴장")) return "out";
        if (req.startsWith("[웹] 광산 채굴")) return "dig";
        if (req.startsWith("[웹] 광산 전투")) {
            if (out != null) {
                if (Boolean.TRUE.equals(out.get("died"))) return "died";
                if (Boolean.TRUE.equals(out.get("bossKilled"))) return "boss";
                if (Boolean.TRUE.equals(out.get("killed"))) return "kill";
            }
            if (res != null && res.contains("쓰러졌")) return "died";
            if (res != null && res.contains("👑")) return "boss";
            return "kill";
        }
        if (req.contains("쓰러짐")) return "died";
        if (req.startsWith("[웹] 광산 배 ->") || req.startsWith("[웹] 광산 이동 안내원") || req.startsWith("[웹] 광산 관리자 좌표 이동")
                || req.startsWith("[웹] 광산 요일 동굴 문지기")) return "move";
        return "act";
    }

    private static String label(String req) {
        String l = req.startsWith("[웹] ") ? req.substring("[웹] ".length()) : req;
        if (l.startsWith("광산 ")) l = l.substring(3);
        return trim(l, LABEL_MAX);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** [2026-10-29] 장비 등급 이름(BotS5ServiceImpl 등급 0~7 -- 화면엔 색으로만, 관리자 로그엔 글자로). */
    private static final String[] GRADE_NAMES = { "일반", "고급", "희귀", "영웅", "전설", "유물", "고대", "신화" };

    private static Ev ev(long t, String k, String a, String m, LinkedHashMap<String, Integer> o) {
        Ev e = new Ev();
        e.t = t; e.k = k; e.a = a; e.m = m;
        if (o != null && !o.isEmpty()) e.o = new LinkedHashMap<String, Integer>(o);
        return e;
    }

    private static void merge(Ev e, LinkedHashMap<String, Integer> o) {
        if (o == null || o.isEmpty()) return;
        if (e.o == null) e.o = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> x : o.entrySet()) e.o.merge(x.getKey(), x.getValue(), Integer::sum);
    }

    /** 세션에 항목 추가(호출자가 s를 잠근 상태). 너무 많이 쌓이면(쓰기가 계속 실패하는 경우 등) 오래된 것부터 버리고 수만 센다. */
    private static void add(Session s, Ev e) {
        s.evs.add(e);
        while (s.evs.size() > MAX_EVENTS) {
            Ev d = s.evs.remove(0);
            if (d == s.digRun) s.digRun = null;
            if (d == s.killRun) s.killRun = null;
            s.dropped++;
        }
    }

    private static long longOf(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private static int levelOf(Map<String, Object> out) {
        if (out == null || !(out.get("levelUp") instanceof Number) || ((Number) out.get("levelUp")).intValue() <= 0) return 0;
        Object c = out.get("combat");
        return c instanceof Map ? (int) longOf(((Map<String, Object>) c).get("lv")) : 0;
    }

    private static String oreName(String code) {
        try {
            String n = BotS5ServiceImpl.mineCodeName(code);
            if (n != null && !n.trim().isEmpty() && !"?".equals(n.trim())) return n;
        } catch (RuntimeException e) {
            // 모르는 코드면 코드 그대로
        }
        return code;
    }

    // ---------------------------------------------------------------- 닫기/쓰기

    /** 세션을 닫아 쓸 차례로 넘긴다(SESSIONS에서는 부른 쪽이 이미 뺐다). */
    private static void close(Session s, long now, String em, String why) {
        synchronized (s) {
            if (s.why != null) return;
            s.end = now;
            s.em = em;
            s.why = why;
        }
        CLOSED.add(s);
    }

    /** 5초마다 -- 모아 둔 위치 쓰기, 오래 소식 없는 세션 닫기, 닫힌 세션/2분 지난 세션 쓰기. */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void flushTick() {
        flush(false);
    }

    @PreDestroy
    public void shutdown() {
        try {
            flush(true);
        } catch (Throwable t) {
            logger.warn("[광산] 종료 시 기록/위치 쓰기 실패", t);
        }
    }

    void flush(boolean all) {
        long now = System.currentTimeMillis();
        for (S5MinePosBuffer.Pos p : S5MinePosBuffer.due(now, all)) {
            try {
                s5Dao.updateMinePosIfNewer(p.userName, p.mapId, p.x, p.y, p.atMs); // 성공하면 S5RequestCache가 버퍼에서 지운다
            } catch (Exception e) {
                logger.warn("[광산] 위치 쓰기 실패 {}: {}", p.userName, e.getMessage());
                S5MinePosBuffer.drop(p);
            }
        }
        for (Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Session s = it.next().getValue();
            boolean idle = now - s.lastSeen > IDLE_MS;
            if ((all || idle) && SESSIONS.remove(s.user, s)) close(s, idle ? s.lastSeen : now, null, all && !idle ? "서버 종료/재시작" : "응답 없음(창 닫힘/연결 끊김 추정)");
        }
        List<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> r;
        while ((r = RETRY.poll()) != null) rows.add(r);
        Session c;
        while ((c = CLOSED.poll()) != null) rows.addAll(take(c, true));
        for (Session s : SESSIONS.values()) {
            if (now - s.lastWrite >= CHECKPOINT_MS) rows.addAll(take(s, false));
        }
        for (HashMap<String, Object> row : rows) {
            try {
                botDao.insertS5WordHis(row);
            } catch (Exception e) {
                logger.warn("[광산] 접속 기록 쓰기 실패(다음에 다시): {}", e.getMessage());
                if (!all && RETRY.size() < RETRY_MAX) RETRY.add(row);
            }
        }
    }

    /** 세션에서 아직 안 쓴 항목을 꺼내 줄(들)로 만든다. last = 닫힌 세션의 마지막(끝 정보 포함, 항목이 없어도 한 줄). */
    private static List<HashMap<String, Object>> take(Session s, boolean last) {
        List<Ev> evs;
        int dropped, seq;
        synchronized (s) {
            s.lastWrite = System.currentTimeMillis();
            if (s.evs.isEmpty() && !last) return new ArrayList<HashMap<String, Object>>();
            evs = new ArrayList<Ev>(s.evs);
            s.evs.clear();
            s.digRun = null; // 쓴 항목은 더 고치지 않는다(이어지는 캐기/처치는 새 항목으로)
            s.killRun = null;
            dropped = s.dropped;
            s.dropped = 0;
            seq = s.seq;
        }
        List<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
        try {
            List<String> evJson = new ArrayList<String>();
            for (Ev e : evs) evJson.add(JSON.writeValueAsString(evMap(e)));
            int i = 0;
            do {
                boolean tail;
                int j = i, size = 0;
                // 머리말 크기는 끝 정보까지 넣은 것으로 넉넉히 잡는다
                int head = cesuLen(JSON.writeValueAsString(head(s, seq, true, dropped, false))) + 16;
                while (j < evJson.size() && (j == i || head + size + cesuLen(evJson.get(j)) + 1 <= MAX_BYTES)) size += cesuLen(evJson.get(j++)) + 1;
                tail = j >= evJson.size();
                LinkedHashMap<String, Object> h = head(s, seq, last && tail, i == 0 ? dropped : 0, false);
                String hj = JSON.writeValueAsString(h);
                StringBuilder sb = new StringBuilder(hj.substring(0, hj.length() - 1)).append(",\"ev\":[");
                for (int k = i; k < j; k++) { if (k > i) sb.append(','); sb.append(evJson.get(k)); }
                sb.append("]}");
                HashMap<String, Object> row = new HashMap<String, Object>();
                row.put("userName", s.user);
                row.put("roomName", "MINE");
                row.put("req", reqText(s, seq, evs.subList(i, j), last && tail));
                row.put("res", sb.toString());
                rows.add(row);
                seq++;
                i = j;
            } while (i < evJson.size());
        } catch (Exception e) {
            logger.warn("[광산] 접속 기록 만들기 실패: {}", e.getMessage());
        }
        synchronized (s) {
            s.seq = seq;
        }
        return rows;
    }

    private static LinkedHashMap<String, Object> head(Session s, int seq, boolean last, int dropped, boolean live) {
        LinkedHashMap<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("v", 1);
        h.put("sid", s.sid);
        h.put("seq", seq);
        h.put("start", s.start);
        if (s.sm != null) h.put("sm", s.sm);
        if (dropped > 0) h.put("dropped", dropped);
        if (last) {
            h.put("end", s.end);
            if (s.em != null) h.put("em", s.em);
            h.put("why", s.why);
        }
        if (live) h.put("live", true);
        return h;
    }

    private static LinkedHashMap<String, Object> evMap(Ev e) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("t", e.t);
        m.put("k", e.k);
        if (e.a != null) m.put("a", e.a);
        if (e.m != null) m.put("m", e.m);
        if (e.n > 0) m.put("n", e.n);
        if (e.x > 0) m.put("x", e.x);
        if (e.lv > 0) m.put("lv", e.lv);
        if (e.o != null && !e.o.isEmpty()) m.put("o", e.o);
        return m;
    }

    /** REQ(검색/예전 화면용 한 줄 요약). */
    private static String reqText(Session s, int seq, List<Ev> evs, boolean last) {
        int dig = 0, kill = 0, act = 0, died = 0;
        for (Ev e : evs) {
            if ("dig".equals(e.k)) dig += e.n;
            else if ("kill".equals(e.k)) kill += e.n;
            else if ("boss".equals(e.k)) kill++;
            else if ("died".equals(e.k)) died++;
            else if ("act".equals(e.k)) act++;
        }
        StringBuilder sb = new StringBuilder(REQ_PREFIX).append(" #").append(s.sid);
        if (seq > 1) sb.append(" (").append(seq).append(')');
        if (dig > 0) sb.append(" · 채굴 ").append(dig);
        if (kill > 0) sb.append(" · 처치 ").append(kill);
        if (act > 0) sb.append(" · 구매/사용 ").append(act);
        if (died > 0) sb.append(" · 쓰러짐 ").append(died);
        if (last) sb.append(" · 끝(").append(s.why).append(')');
        return sb.length() > 900 ? sb.substring(0, 900) : sb.toString();
    }

    /** 오라클 국가 문자셋 UTF8(CESU-8) 바이트 수 -- 이모지(서로게이트 쌍)는 3 + 3바이트. */
    static int cesuLen(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            n += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        }
        return n;
    }

    // ---------------------------------------------------------------- 로그 뷰어(진행 중 세션)

    /** 지금 진행 중인 세션의 아직 안 쓴 부분 -- 로그 뷰어가 DB 줄과 같은 모양으로 받아 합친다(LIVE = true). userName이 비면 전체. */
    public static List<HashMap<String, Object>> liveRows(String userName) {
        List<HashMap<String, Object>> out = new ArrayList<HashMap<String, Object>>();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        for (Session s : SESSIONS.values()) {
            if (userName != null && !userName.isEmpty() && !userName.equals(s.user)) continue;
            try {
                List<Object> evs = new ArrayList<Object>();
                int seq;
                synchronized (s) {
                    for (Ev e : s.evs) evs.add(evMap(e));
                    seq = s.seq;
                }
                LinkedHashMap<String, Object> h = head(s, seq, false, 0, true);
                h.put("seen", s.lastSeen);
                h.put("ev", evs);
                HashMap<String, Object> row = new HashMap<String, Object>();
                row.put("USER_NAME", s.user);
                row.put("ROOM_NAME", "MINE");
                row.put("REQ", REQ_PREFIX + " #" + s.sid + " (진행 중)");
                row.put("RES", JSON.writeValueAsString(h));
                row.put("INSERT_DATE", now);
                row.put("LIVE", true);
                out.add(row);
            } catch (Exception e) {
                // 진행 중 표시는 없어도 된다
            }
        }
        return out;
    }

    /** 진단용: 진행 중 세션 수. */
    public static int openCount() {
        return SESSIONS.size();
    }
}
