package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.util.PropsUtil;

/**
 * [2026-09-30] 시즌5 전용 카카오 로그인 -- "/로그로 연결되는 페이지(로그/몬스터정보/밸런스통계)는 카톡로그인이 된
 * 사람만 볼 수 있게, 카톡로그인은 s5전용으로, 받는 정보는 키값과 닉네임만" 요청.
 *
 * - 저장: TBOT_S5_KAKAO_MEMBER(KAKAO_ID, NICKNAME, REG_DATE, LAST_LOGIN)만. S4(낚시) 회원 테이블과 무관.
 * - 세션 키 SESSION_KEY 에 {kakaoId, nickname}. 페이지/API 보호는 isLoggedIn()으로 각 컨트롤러가 확인.
 * - 카카오 개발자 콘솔에 이미 등록돼 있는 S4 리다이렉트 URI(/s4/kakao/callback)를 그대로 재사용한다(새 URI를 콘솔에
 *   등록하지 않아도 바로 동작). 구분은 OAuth state("s5-랜덤")로 하고, S4WebController.kakaoCallback이 그 state를
 *   보면 이 컨트롤러의 /loa/s5/kakao/callback 으로 forward 한다. 카카오 토큰 교환 시 redirect_uri는 인가 요청 때와
 *   같아야 하므로 여기서도 S4 콜백 URI 문자열을 그대로 쓴다.
 * - [2026-10-28] "로그 페이지도 매번 로그인 창이 뜬다" -- 로그인이 세션에만 있어서(60분, 배포마다 사라짐, 카톡 안 브라우저는 창을 닫으면
 *   세션 쿠키를 버림) 거의 매번 다시 물었다. 로그인에 성공하면 브라우저에 REMEMBER_DAYS일짜리 무작위 쿠키(REMEMBER_COOKIE)를 주고
 *   그 SHA-256 해시만 TBOT_S5_KAKAO_REMEMBER에 둔다. 세션이 없으면 isLoggedIn이 그 쿠키로 세션을 되살린다(쓰는 동안은 기간 연장).
 */
@Controller
@RequestMapping("/loa/s5")
public class Season5KakaoLoginController {

    public static final String SESSION_KEY = "s5_kakao";
    public static final String STATE_PREFIX = "s5-";
    private static final String STATE_KEY = "s5_kakao_state";
    private static final String NEXT_KEY = "s5_kakao_next";
    private static final String DEFAULT_NEXT = "/loa/tower-battle-log";

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO s5Dao;

    private static final String REMEMBER_COOKIE = "s5_keep";
    private static final int REMEMBER_DAYS = 180;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    /** isLoggedIn(static, 다른 컨트롤러가 부름)이 쿠키로 세션을 되살릴 때 쓰는 DAO. */
    private static volatile BotS5DAO rememberDao;

    @PostConstruct
    void initRemember() {
        rememberDao = s5Dao;
    }

    /** 로그인 후 돌아갈 수 있는 곳(오픈 리다이렉트 방지) -- 보호 대상 3페이지만. */
    private static boolean allowedNext(String next) {
        return "/loa/tower-battle-log".equals(next) || "/loa/tower-mine-users".equals(next); // [2026-10-25] 광산 캐릭터 현황
    }

    /** 다른 컨트롤러(Season5ViewController)가 페이지/API 보호에 쓴다. */
    public static boolean isLoggedIn(HttpSession session) {
        if (session != null && session.getAttribute(SESSION_KEY) != null) return true;
        return restoreFromCookie(session); // [2026-10-28] 세션이 없으면 오래 기억하기 쿠키로
    }

    /** 오래 기억하기 쿠키가 살아 있으면 그 카카오 회원으로 세션을 다시 채운다. 하루에 한 번 DB 기간과 쿠키 기간을 다시 늘린다. */
    private static boolean restoreFromCookie(HttpSession session) {
        BotS5DAO dao = rememberDao;
        ServletRequestAttributes attrs = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes
                ? (ServletRequestAttributes) RequestContextHolder.getRequestAttributes() : null;
        if (dao == null || attrs == null) return false;
        HttpServletRequest req = attrs.getRequest();
        String token = cookieValue(req, REMEMBER_COOKIE);
        if (token == null || token.length() < 20 || token.length() > 100) return false;
        try {
            String hash = sha256(token);
            HashMap<String, Object> row = dao.selectKakaoRemember(hash);
            if (row == null || row.get("KAKAO_ID") == null) return false;
            HashMap<String, String> me = new HashMap<>();
            me.put("kakaoId", String.valueOf(row.get("KAKAO_ID")));
            me.put("nickname", row.get("NICKNAME") == null ? "익명" : String.valueOf(row.get("NICKNAME")));
            (session != null ? session : req.getSession(true)).setAttribute(SESSION_KEY, me);
            if (row.get("STALE") instanceof Number && ((Number) row.get("STALE")).intValue() == 1) {
                dao.touchKakaoRemember(hash, REMEMBER_DAYS);
                if (attrs.getResponse() != null) setRememberCookie(attrs.getResponse(), token, REMEMBER_DAYS * 86400);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String cookieValue(HttpServletRequest req, String name) {
        Cookie[] cs = req.getCookies();
        if (cs != null) for (Cookie c : cs) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    /** Set-Cookie를 직접 쓴다(servlet-api가 옛 버전이라 HttpOnly/SameSite 설정 메서드가 없다). maxAge 0 = 지우기.
     *  Expires도 함께 붙인다(오래된 웹뷰는 Max-Age만 있으면 세션 쿠키로 취급하기도 한다). */
    private static void setRememberCookie(HttpServletResponse resp, String value, int maxAge) {
        String expires = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(maxAge));
        resp.addHeader("Set-Cookie", REMEMBER_COOKIE + "=" + value + "; Max-Age=" + maxAge + "; Expires=" + expires + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private static String sha256(String s) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "next", defaultValue = "") String next,
            @RequestParam(value = "err", defaultValue = "") String err, HttpSession session, Model model) {
        String target = allowedNext(next) ? next : DEFAULT_NEXT;
        if (isLoggedIn(session)) return "redirect:" + target;
        if (allowedNext(next)) session.setAttribute(NEXT_KEY, next);
        model.addAttribute("err", err);
        return "nonsession/loa/s5_login";
    }

    @GetMapping("/kakao/auth")
    public String kakaoAuth(HttpServletRequest request, HttpSession session) throws Exception {
        String kakaoKey = PropsUtil.getProperty("keys", "kakaoKey");
        String state = STATE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        session.setAttribute(STATE_KEY, state);
        return "redirect:" + KAKAO_AUTH_URL
                + "?client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(sharedRedirectUri(request), "UTF-8")
                + "&response_type=code"
                + "&state=" + state;
    }

    /** S4WebController.kakaoCallback 이 state가 "s5-"로 시작하면 여기로 forward(code/state 파라미터 그대로). */
    @GetMapping("/kakao/callback")
    public String kakaoCallback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request, HttpServletResponse response, HttpSession session) throws Exception {
        String expected = (String) session.getAttribute(STATE_KEY);
        session.removeAttribute(STATE_KEY);
        if (error != null || code == null) return "redirect:/loa/s5/login?err=cancel";
        if (expected == null || !expected.equals(state)) return "redirect:/loa/s5/login?err=state";

        String kakaoKey = PropsUtil.getProperty("keys", "kakaoKey");
        String tokenJson = httpPost(KAKAO_TOKEN_URL,
                "grant_type=authorization_code"
                + "&client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(sharedRedirectUri(request), "UTF-8")
                + "&code=" + URLEncoder.encode(code, "UTF-8"));
        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenJson, new TypeReference<Map<String, Object>>() {});
        String accessToken = (String) tokenMap.get("access_token");
        if (accessToken == null) return "redirect:/loa/s5/login?err=token";

        String userJson = httpGetWithBearer(KAKAO_USER_URL, accessToken);
        Map<String, Object> userMap = new ObjectMapper().readValue(userJson, new TypeReference<Map<String, Object>>() {});
        Object idObj = userMap.get("id");
        if (idObj == null) return "redirect:/loa/s5/login?err=token";
        String kakaoId = String.valueOf(idObj);

        String nickname = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) userMap.get("kakao_account");
        if (account != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) account.get("profile");
            if (profile != null) nickname = (String) profile.get("nickname");
        }
        if (nickname == null || nickname.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) userMap.get("properties");
            if (props != null) nickname = (String) props.get("nickname");
        }
        if (nickname == null || nickname.isEmpty()) nickname = "익명";
        if (nickname.length() > 50) nickname = nickname.substring(0, 50);

        HashMap<String, Object> row = new HashMap<>();
        row.put("kakaoId", kakaoId);
        row.put("nickname", nickname);
        s5Dao.upsertKakaoMember(row);

        HashMap<String, String> me = new HashMap<>();
        me.put("kakaoId", kakaoId);
        me.put("nickname", nickname);
        session.setAttribute(SESSION_KEY, me);
        // [2026-10-28] 오래 기억하기 -- 이 브라우저에 무작위 쿠키, DB엔 그 해시만(기간 지난 것은 이때 같이 지운다)
        try {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            s5Dao.deleteExpiredKakaoRemember();
            s5Dao.insertKakaoRemember(sha256(token), kakaoId, REMEMBER_DAYS);
            setRememberCookie(response, token, REMEMBER_DAYS * 86400);
        } catch (Exception e) {
            // 기억하기에 실패해도 이번 로그인(세션)은 그대로 쓴다
        }

        String next = (String) session.getAttribute(NEXT_KEY);
        session.removeAttribute(NEXT_KEY);
        return "redirect:" + (next != null && allowedNext(next) ? next : DEFAULT_NEXT);
    }

    /** 페이지 우상단 "닉네임 · 로그아웃" 표시용. */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public Map<String, Object> me(HttpSession session) {
        Map<String, Object> out = new HashMap<>();
        isLoggedIn(session); // 세션이 없으면 오래 기억하기 쿠키로 먼저 되살린다
        @SuppressWarnings("unchecked")
        HashMap<String, String> me = session == null ? null : (HashMap<String, String>) session.getAttribute(SESSION_KEY);
        out.put("loggedIn", me != null);
        if (me != null) out.put("nickname", me.get("nickname"));
        return out;
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        String token = cookieValue(request, REMEMBER_COOKIE); // [2026-10-28] 오래 기억하기도 끊는다(이 브라우저만)
        if (token != null) {
            try { s5Dao.deleteKakaoRemember(sha256(token)); } catch (Exception e) { /* 쿠키만이라도 지운다 */ }
            setRememberCookie(response, "", 0);
        }
        return "redirect:/loa/s5/login";
    }

    /** S4WebController.getRedirectUri 와 같은 계산(카카오 콘솔에 등록된 S4 콜백). */
    private String sharedRedirectUri(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort())
                + request.getContextPath() + "/s4/kakao/callback";
    }

    private String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        return readResponse(conn);
    }

    private String httpGetWithBearer(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
