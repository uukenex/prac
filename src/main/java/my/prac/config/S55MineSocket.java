package my.prac.config;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.api.loa.controller.Season5ViewController;

/**
 * [2026-11-03] 시즌 5.5(광산) -- "네트워크 지연을 안 느끼게" 요청. 광산 화면이 HTTP 요청 대신 연결 하나(WebSocket)로 1초 턴/공격/캐기를 주고받는다.
 * 메시지 = {"i": 번호, "p": "/api/tower-mine-tick?mapId=...&x=..."}(HTTP 때와 같은 주소 문자열), 답 = {"i": 번호, "d": HTTP 때와 같은 응답}.
 * 실제 처리는 Season5ViewController의 같은 메서드를 그대로 부른다(규칙/로그가 HTTP와 똑같다). 유저는 연결할 때 주소의 u(HTTP 때의 userName과 같은 방식).
 * 받을 수 있는 주소는 ROUTES뿐이고, 화면은 연결이 안 되면 예전처럼 HTTP로 보낸다.
 */
@ServerEndpoint("/s55ws")
public class S55MineSocket {

    private static volatile Season5ViewController ctl;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Season5ViewController가 뜰 때 자기 자신을 넘긴다(이 클래스는 스프링이 아니라 톰캣이 만든다). */
    public static void bind(Season5ViewController c) { ctl = c; }

    /** [2026-11-04] 메시지를 줄 세우지 않고 따로 처리(공격/캐기가 1초 턴 뒤에서 기다리지 않게). */
    private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(16, r -> { Thread t = new Thread(r, "s55ws"); t.setDaemon(true); return t; });

    @OnMessage
    public void onMessage(String message, Session session) {
        POOL.execute(() -> handle(message, session));
    }

    private void handle(String message, Session session) {
        // [2026-11-04] HTTP 요청은 필터(S5RequestCacheFilter)가 요청 단위 캐시와 광산 기록 마무리를 해 주는데 WebSocket은 필터를 안 거친다 -- 여기서 같게
        my.prac.core.prjbot.dao.S5RequestCache.begin();
        try {
            handleIn(message, session);
        } finally {
            my.prac.core.prjbot.dao.S5RequestCache.end();
        }
    }

    private void handleIn(String message, Session session) {
        Object id = null;
        Object data;
        try {
            Map<?, ?> m = JSON.readValue(message, Map.class);
            id = m.get("i");
            data = route(String.valueOf(m.get("p")), user(session));
        } catch (Exception e) {
            data = new HashMap<String, Object>();
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("i", id);
        out.put("d", data);
        try {
            String text = JSON.writeValueAsString(out);
            synchronized (session) { session.getBasicRemote().sendText(text); }
        } catch (Exception ignore) {
        }
    }

    @OnError
    public void onError(Session session, Throwable t) {
    }

    private static String user(Session s) {
        List<String> u = s.getRequestParameterMap().get("u");
        return u == null || u.isEmpty() ? "" : u.get(0);
    }

    private static Object route(String p, String userName) throws Exception {
        Season5ViewController c = ctl;
        if (c == null || userName.trim().isEmpty()) return new HashMap<String, Object>();
        int q = p.indexOf('?');
        String path = q < 0 ? p : p.substring(0, q);
        Map<String, String> a = new HashMap<>();
        if (q >= 0) for (String kv : p.substring(q + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e > 0) a.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
        }
        switch (path) {
            case "/api/tower-mine-tick":
                return c.apiTowerMineTick(userName, a.getOrDefault("mapId", ""), num(a, "x"), num(a, "y"), a.getOrDefault("d", "down"), a.getOrDefault("v", ""),
                        a.getOrDefault("c", ""), a.getOrDefault("e", ""), a.getOrDefault("m", ""),
                        a.containsKey("pg") ? num(a, "pg") : -1, num(a, "dv"), num(a, "nw"), num(a, "fg"), num(a, "bg"), num(a, "wv")).getBody();
            case "/api/tower-mine-attack":
                return c.apiTowerMineAttack(userName, a.getOrDefault("mapId", ""), num(a, "x"), num(a, "y"), num(a, "mob"), a.getOrDefault("v", ""),
                        a.containsKey("mc") ? num(a, "mc") : -1, a.getOrDefault("d", "")).getBody();
            case "/api/tower-mine-nova": // [2026-11-05] 관리자 전체 베기(빠진 길이라 빈 답 -> 효과 없음)
                return c.apiTowerMineNova(userName, a.getOrDefault("mapId", ""), num(a, "x"), num(a, "y"), a.containsKey("hw") ? num(a, "hw") : 8, a.containsKey("hh") ? num(a, "hh") : 8, a.getOrDefault("v", "")).getBody();
            case "/api/tower-mine-dig":
                return c.apiTowerMineDig(userName, a.getOrDefault("mapId", ""), num(a, "x"), num(a, "y"), a.getOrDefault("v", ""), a.getOrDefault("dir", "down")).getBody();
            default:
                return generic(c, path, a, userName);
        }
    }

    // [2026-11-06] "접속했는데 왜 ws로 안 하고 http지? 전부 ws로 하기로 한 거 아니었나?" -- 1초 턴/공격/캐기/전체 베기만 소켓이었다. 이제 광산 화면이 보내는
    //  나머지 GET 요청(/api/tower-mine*)도 소켓으로: Season5ViewController에서 그 주소의 @GetMapping 메서드를 찾아 @RequestParam 이름대로 값을 넣어 부른다
    //  (userName은 연결한 유저). 인자가 @RequestParam 단순 값(String/int/long/double/boolean)뿐인 메서드만 -- 아니면 {"_http": true}로 답해 화면이 HTTP로 보낸다.
    private static volatile Map<String, java.lang.reflect.Method> GET_ROUTES;
    private static Map<String, java.lang.reflect.Method> getRoutes() {
        Map<String, java.lang.reflect.Method> m = GET_ROUTES;
        if (m != null) return m;
        m = new HashMap<>();
        for (java.lang.reflect.Method md : Season5ViewController.class.getMethods()) {
            org.springframework.web.bind.annotation.GetMapping gm = md.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            org.springframework.web.bind.annotation.RequestMapping rm = md.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class); // [2026-11-06] GET도 받는 @RequestMapping(위치 저장 등)
            String[] paths = gm != null ? (gm.value().length > 0 ? gm.value() : gm.path())
                    : rm != null && (rm.method().length == 0 || java.util.Arrays.asList(rm.method()).contains(org.springframework.web.bind.annotation.RequestMethod.GET)) ? (rm.value().length > 0 ? rm.value() : rm.path()) : null;
            if (paths == null) continue;
            boolean ok = true;
            for (java.lang.reflect.Parameter p : md.getParameters()) {
                org.springframework.web.bind.annotation.RequestParam rp = p.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
                Class<?> t = p.getType();
                if (rp == null || (rp.value().isEmpty() && rp.name().isEmpty())
                        || !(t == String.class || t == int.class || t == long.class || t == double.class || t == boolean.class || t == Integer.class || t == Long.class)) { ok = false; break; }
            }
            if (!ok) continue;
            for (String ps : paths) if (ps.startsWith("/api/tower-mine")) m.put(ps, md);
        }
        GET_ROUTES = m;
        return m;
    }
    private static Object generic(Season5ViewController c, String path, Map<String, String> a, String userName) throws Exception {
        java.lang.reflect.Method md = getRoutes().get(path);
        HashMap<String, Object> http = new HashMap<>();
        http.put("_http", true);
        if (md == null) return http;
        java.lang.reflect.Parameter[] ps = md.getParameters();
        Object[] args = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            org.springframework.web.bind.annotation.RequestParam rp = ps[i].getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            String nm = rp.value().isEmpty() ? rp.name() : rp.value();
            String v = "userName".equals(nm) ? userName : a.get(nm);
            if (v == null && !org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(rp.defaultValue())) v = rp.defaultValue();
            Class<?> t = ps[i].getType();
            if (v == null) {
                if (rp.required() || t.isPrimitive()) return http; // HTTP라면 400 -- 그쪽에 맡긴다
                args[i] = null;
                continue;
            }
            try {
                args[i] = t == String.class ? v : t == int.class || t == Integer.class ? Integer.valueOf(v.trim()) : t == long.class || t == Long.class ? Long.valueOf(v.trim())
                        : t == double.class ? Double.valueOf(v.trim()) : Boolean.valueOf(v.trim());
            } catch (NumberFormatException e) {
                return http;
            }
        }
        try {
            Object r = md.invoke(c, args);
            return r instanceof org.springframework.http.ResponseEntity ? ((org.springframework.http.ResponseEntity<?>) r).getBody() : r;
        } catch (java.lang.reflect.InvocationTargetException e) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("message", "요청을 처리하지 못했어요. 다시 시도해 주세요.");
            return err;
        }
    }

    private static int num(Map<String, String> a, String k) {
        try { return Integer.parseInt(a.get(k)); } catch (Exception e) { return 0; }
    }
}
