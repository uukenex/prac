package my.prac.api.loa.controller;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotS5Service;

/**
 * [2026-11-04] 시즌 5.5 광산 웹앱(PWA) 전용 주소 /mine/ -- "s5 탑오르기와 별개로 s5.5 광산게임 전용 url, 전용 url에서만 PWA, 여기서만 카카오톡 로그인".
 *  1) 카카오 로그인(Season5KakaoLoginController의 OAuth를 함께 쓴다, 돌아올 곳 /mine/)
 *  2) 처음이면 람쥐봇 방 닉네임을 직접 입력 -- 기존 광산 아이디와 같으면 연결, 없으면 새로 만들어 연결(TBOT_S55_MINE_LINK, S55MineServiceImpl.s55Link)
 *  3) 연결돼 있으면 바로 광산(탑 화면과 같은 광산: _mine_overlay.jsp + /loa/s55/mine.js)
 *  https로만(Cloudflare 앞단이라 서버는 http로 받는다 -- CF-Visitor / X-Forwarded-Proto로 본다). 기존 탑 화면의 광산 입구는 당분간 그대로(로그인 없음).
 *  /mine/manifest.webmanifest, /mine/sw.js(범위 /mine/) = 설치용. 서비스 워커는 아무것도 저장하지 않는다(배포가 바로 보이게) -- 오프라인일 때 안내만.
 */
@Controller
public class S55MineController {

    @Resource(name = "core.prjbot.BotS5Service")
    BotS5Service s5Service;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static boolean viaHttp(HttpServletRequest req) {
        String vis = req.getHeader("CF-Visitor"), xf = req.getHeader("X-Forwarded-Proto");
        return (vis != null && vis.contains("\"http\"")) || "http".equalsIgnoreCase(xf);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> me(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object me = s == null ? null : s.getAttribute(Season5KakaoLoginController.SESSION_KEY);
        return me instanceof Map ? (Map<String, Object>) me : null;
    }

    private static String kakaoId(HttpServletRequest req) {
        Map<String, Object> me = me(req);
        return me == null || me.get("kakaoId") == null ? null : String.valueOf(me.get("kakaoId"));
    }

    @GetMapping("/mine")
    public String noSlash(HttpServletRequest req) {
        return "redirect:/mine/";
    }

    @GetMapping("/mine/")
    public String page(HttpServletRequest req, HttpServletResponse resp, HttpSession session) throws Exception {
        if (viaHttp(req) && req.getServerName().endsWith("dev-apc.com")) return "redirect:https://" + req.getServerName() + req.getContextPath() + "/mine/";
        resp.setHeader("Cache-Control", "no-store");
        req.setAttribute("pageBuild", Season5ViewController.PAGE_BUILD);
        String kid = Season5KakaoLoginController.isLoggedIn(session) ? kakaoId(req) : null;
        if (kid == null) {
            req.setAttribute("mode", "login");
            return "nonsession/loa/s55_mine";
        }
        Map<String, Object> me = me(req);
        req.setAttribute("nickname", me == null ? "" : String.valueOf(me.get("nickname")));
        req.setAttribute("kakaoId", kid); // [2026-11-07] "카톡이 두 개라 기존 계정에 못 들어온다 -- 이미 연결된 계정도 지금 카톡 로그인 ID를 띄워 줘(관리자가 DB로 직접 연결)"
        String user = s5Service.s55LinkedUser(kid);
        if (user == null) {
            req.setAttribute("mode", "link");
        } else {
            req.setAttribute("mode", "game");
            req.setAttribute("userJson", JSON.writeValueAsString(user).replace("</", "<\\/"));
            req.setAttribute("userName", user);
            if (s5Service.s55IsAdmin(user)) req.setAttribute("vapidPub", my.prac.core.util.S55AdminPush.publicKey()); // [2026-11-04] 관리자만 알림 켜기 버튼
        }
        return "nonsession/loa/s55_mine";
    }

    /** 닉네임 확인(연결 전 미리 보기) -- {ok, name, exists, lv, taken, message}. */
    @GetMapping(value = "/mine/check", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> check(HttpServletRequest req, @RequestParam(value = "name", defaultValue = "") String name) {
        String kid = kakaoId(req);
        HashMap<String, Object> out = kid == null ? new HashMap<String, Object>() : s5Service.s55LinkCheck(kid, name);
        if (kid == null) { out.put("ok", false); out.put("login", false); out.put("message", "카카오 로그인이 풀렸어요. 다시 로그인해 주세요."); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /** 연결 -- 있으면 그 캐릭터, 없으면 새로 만들어 연결. */
    @PostMapping(value = "/mine/link", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> link(HttpServletRequest req, @RequestParam(value = "name", defaultValue = "") String name) {
        String kid = kakaoId(req);
        HashMap<String, Object> out;
        if (kid == null) { out = new HashMap<>(); out.put("ok", false); out.put("login", false); out.put("message", "카카오 로그인이 풀렸어요. 다시 로그인해 주세요."); }
        else if (s5Service.s55LinkedUser(kid) != null) { out = new HashMap<>(); out.put("ok", true); out.put("message", "이미 연결돼 있어요."); }
        else {
            out = s5Service.s55Link(kid, name);
            if (Boolean.TRUE.equals(out.get("ok"))) { // [2026-11-04] 관리자 폰 알림 -- 누가 카카오로 어떤 닉네임에 연결했는지
                Map<String, Object> me = me(req);
                my.prac.core.util.S55AdminPush.notify("🔗 카카오 연결", "카카오 '" + (me == null ? "?" : me.get("nickname")) + "' 님이 '" + out.get("name") + "'" + (Boolean.TRUE.equals(out.get("created")) ? " (새 캐릭터)" : "") + "에 연결했어요");
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /** [2026-11-04] 관리자 폰 알림 구독(관리자 캐릭터에 연결된 카카오 계정만). */
    @PostMapping(value = "/mine/push-sub", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> pushSub(HttpServletRequest req, @RequestParam(value = "endpoint", defaultValue = "") String endpoint) {
        HashMap<String, Object> out = new HashMap<>();
        String kid = kakaoId(req), user = kid == null ? null : s5Service.s55LinkedUser(kid);
        boolean ok = user != null && s5Service.s55IsAdmin(user) && my.prac.core.util.S55AdminPush.subscribe(endpoint, kid, user);
        out.put("ok", ok);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out); // [2026-11-04] 들어올 때마다 자동 구독 -- 알림은 [시험 알림]으로만
    }

    private boolean adminSession(HttpServletRequest req) {
        String kid = kakaoId(req), user = kid == null ? null : s5Service.s55LinkedUser(kid);
        return user != null && s5Service.s55IsAdmin(user);
    }

    /** [2026-11-04] 화면 안 관리자 알림(10초마다) -- 관리자만. */
    @GetMapping(value = "/mine/admin-alerts", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> adminAlerts(HttpServletRequest req, @RequestParam(value = "since", defaultValue = "0") long since) {
        Object body = adminSession(req) ? my.prac.core.util.S55AdminPush.recent(since) : new java.util.ArrayList<Object>();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** [2026-11-04] 접속 중 유저 통신 상태(핑/기기/방식) -- 관리자만. */
    @GetMapping(value = "/mine/admin-net", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> adminNet(HttpServletRequest req) {
        Object body = adminSession(req) ? my.prac.core.util.S55AdminPush.nets() : new java.util.ArrayList<Object>();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** [2026-11-04] 시험 알림(폰 알림 + 화면 안 알림) -- 관리자만. */
    @PostMapping(value = "/mine/push-test", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> pushTest(HttpServletRequest req) {
        HashMap<String, Object> out = new HashMap<>();
        boolean ok = adminSession(req);
        if (ok) my.prac.core.util.S55AdminPush.notify("🔔 시험 알림", "관리자 알림이 잘 오고 있어요. (" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + ")");
        out.put("ok", ok);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /** 서비스 워커가 푸시를 받으면 그 기기 몫의 알림을 가져간다(구독 주소가 열쇠). */
    @GetMapping(value = "/mine/push-pull", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> pushPull(@RequestParam(value = "endpoint", defaultValue = "") String endpoint) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(my.prac.core.util.S55AdminPush.pull(endpoint));
    }

    @GetMapping("/mine/manifest.webmanifest")
    @ResponseBody
    public ResponseEntity<String> manifest(HttpServletRequest req) {
        String ctx = req.getContextPath();
        String m = "{\"name\":\"람쥐 광산\",\"short_name\":\"람쥐광산\",\"description\":\"람쥐봇 시즌 5.5 광산 게임\","
                + "\"start_url\":\"" + ctx + "/mine/\",\"scope\":\"" + ctx + "/mine/\",\"display\":\"fullscreen\",\"orientation\":\"portrait\","
                + "\"background_color\":\"#241a3a\",\"theme_color\":\"#241a3a\",\"lang\":\"ko\","
                + "\"icons\":[{\"src\":\"" + ctx + "/img_loa/s55/icon-192.png\",\"sizes\":\"192x192\",\"type\":\"image/png\",\"purpose\":\"any maskable\"},"
                + "{\"src\":\"" + ctx + "/img_loa/s55/icon-512.png\",\"sizes\":\"512x512\",\"type\":\"image/png\",\"purpose\":\"any maskable\"}]}";
        return ResponseEntity.ok().contentType(MediaType.valueOf("application/manifest+json;charset=UTF-8")).cacheControl(CacheControl.noCache()).body(m);
    }

    /** 서비스 워커 -- 아무것도 저장하지 않고(배포가 바로 보이게), 화면 이동이 네트워크 오류로 실패하면 안내 화면만. */
    @GetMapping("/mine/sw.js")
    @ResponseBody
    public ResponseEntity<String> serviceWorker() {
        String js = "self.addEventListener('install', function (e) { self.skipWaiting(); });\n"
                + "self.addEventListener('activate', function (e) { e.waitUntil(self.clients.claim()); });\n"
                + "self.addEventListener('fetch', function (e) {\n"
                + "  if (e.request.mode !== 'navigate') return;\n"
                + "  e.respondWith(fetch(e.request).catch(function () {\n"
                + "    return new Response('<!doctype html><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><body style=\"background:#241a3a;color:#fff;font-family:sans-serif;text-align:center;padding-top:30vh\">"
                + "<div style=\"font-size:40px\">🔌</div><p>인터넷에 연결되어 있지 않아요.<br>연결되면 다시 열어 주세요.</p></body>', { headers: { 'Content-Type': 'text/html; charset=utf-8' } });\n"
                + "  }));\n"
                + "});\n"
                // [2026-11-04] 관리자 폰 알림 -- 푸시는 내용이 없다. 깨면 이 기기 몫을 가져와 띄운다(없으면 기본 문구 -- 푸시마다 알림을 꼭 띄워야 한다)
                + "self.addEventListener('push', function (e) {\n"
                + "  e.waitUntil(self.registration.pushManager.getSubscription().then(function (sub) {\n"
                + "    return fetch('/mine/push-pull?endpoint=' + encodeURIComponent(sub ? sub.endpoint : ''), { cache: 'no-store' }).then(function (r) { return r.json(); });\n"
                + "  }).catch(function () { return []; }).then(function (list) {\n"
                + "    if (!list || !list.length) return self.registration.showNotification('람쥐 광산', { body: '새 알림이 있어요.', icon: '/img_loa/s55/icon-192.png', tag: 's55' });\n"
                + "    return Promise.all(list.slice(-5).map(function (n, i) { return self.registration.showNotification(n.title || '람쥐 광산', { body: n.body || '', icon: '/img_loa/s55/icon-192.png', tag: 's55-' + n.t + '-' + i, timestamp: n.t }); }));\n"
                + "  }));\n"
                + "});\n"
                + "self.addEventListener('notificationclick', function (e) {\n"
                + "  e.notification.close();\n"
                + "  e.waitUntil(self.clients.matchAll({ type: 'window' }).then(function (cs) { for (var i = 0; i < cs.length; i++) if (cs[i].url.indexOf('/mine/') >= 0) return cs[i].focus(); return self.clients.openWindow('/mine/'); }));\n"
                + "});\n";
        return ResponseEntity.ok().contentType(MediaType.valueOf("application/javascript;charset=UTF-8")).cacheControl(CacheControl.noCache()).body(js);
    }
}
