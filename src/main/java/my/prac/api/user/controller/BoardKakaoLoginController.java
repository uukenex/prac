package my.prac.api.user.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.dto.Users;
import my.prac.core.prjuser.service.UserService;
import my.prac.core.util.PropsUtil;

/**
 * [2026-10-08] 게시판 카카오 로그인 -- 아이디/비밀번호 로그인과 회원가입을 없애고 카카오 로그인만 쓴다.
 * - 저장: TBOARD_KAKAO_MEMBER(KAKAO_ID, USER_ID, NICKNAME). 시즌5 회원 테이블(TBOT_S5_KAKAO_MEMBER)과는 별도로 운영한다.
 *   일반 회원은 USER_ID='K'+카카오ID, 닉네임은 카카오 닉네임(로그인할 때마다 갱신). 관리자는 이 테이블에 THJEON으로 매핑된 행이 있어
 *   그 카카오 계정으로 로그인하면 기존 TUSER의 THJEON(게시판 표기 "관리자")으로 로그인된다 -- 닉네임이 아니라 카카오 고유번호로만 매핑하므로
 *   닉네임을 "전태환"으로 바꿔도 관리자가 될 수 없다.
 * - 세션 키는 기존 게시판과 같은 "Users"(my.prac.core.dto.Users) -- 글쓰기/인터셉터/JSP가 그대로 동작한다. 로그인 성공 시 세션을 새로 발급한다(세션 고정 방지).
 *
 * [2026-10-08 수정] 카카오 개발자 콘솔에는 시즌5 로그인이 쓰는 콜백(http://rgb-tns.dev-apc.com/s4/kakao/callback) 하나만 등록돼 있다.
 * 예전엔 redirect_uri를 "지금 접속한 호스트" 기준으로 만들어서, 게시판을 rgb-tns가 아닌 호스트(prd-web 등)로 열면 등록 안 된 주소로 요청돼
 * 로그인이 실패했다. 이제 시즌5와 똑같이 등록된 콜백 주소 하나만 쓰고(S4WebController.kakaoCallback이 state가 "bd-"면 이쪽으로 forward),
 * 게시판 호스트가 다르면 아래 흐름으로 세션을 넘긴다.
 *   1) 게시판 호스트 /board/kakao/auth: 일회용 nonce를 그 호스트 세션에 저장, state = "bd-" + nonce + "." + base64url(출발 origin).
 *   2) 카카오 -> 등록된 콜백(어느 호스트든): state에서 출발 origin을 꺼내 허용 도메인인지 검사, 토큰/회원 처리 후 일회용 티켓(60초, 메모리)을
 *      만들어 "출발 origin/board/kakao/finish?ticket=" 으로 보낸다.
 *   3) 출발 호스트 /board/kakao/finish: 티켓을 소비하고 티켓의 nonce가 이 브라우저 세션의 nonce와 같을 때만 로그인(남이 만든 링크로 로그인되는 것 방지).
 * 티켓 저장소는 메모리라 앱이 한 대(한 JVM)일 때만 동작한다(모든 호스트가 같은 Tomcat이므로 현재 구성에선 문제없음).
 */
@Controller
@RequestMapping("/board/kakao")
public class BoardKakaoLoginController {

    public static final String STATE_PREFIX = "bd-";
    private static final String NONCE_KEY = "board_kakao_nonce";

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";

    /** 카카오 콘솔에 등록된 콜백(시즌5 로그인과 동일). 운영 도메인(*.dev-apc.com)에서 시작한 로그인은 모두 이 주소로 돌아온다. */
    static final String REGISTERED_REDIRECT_URI = "http://rgb-tns.dev-apc.com/s4/kakao/callback";

    private static final long TICKET_TTL_MS = 60_000L;
    private static final int TICKET_MAX = 1000;
    private static final SecureRandom RND = new SecureRandom();
    private static final ConcurrentHashMap<String, Ticket> TICKETS = new ConcurrentHashMap<>();

    private static class Ticket {
        final String userId;
        final String nickname;
        final String nonce;
        final long expiresAt;
        Ticket(String userId, String nickname, String nonce) {
            this.userId = userId;
            this.nickname = nickname;
            this.nonce = nonce;
            this.expiresAt = System.currentTimeMillis() + TICKET_TTL_MS;
        }
    }

    @Resource(name = "core.prjuser.UserService")
    UserService uService;

    @GetMapping("/auth")
    public String kakaoAuth(HttpServletRequest request, HttpSession session) throws Exception {
        String kakaoKey = PropsUtil.getProperty("keys", "kakaoKey");
        String nonce = randomHex(16);
        session.setAttribute(NONCE_KEY, nonce);
        String origin = originOf(request);
        String state = buildState(nonce, origin);
        return "redirect:" + KAKAO_AUTH_URL
                + "?client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUriFor(origin, request), "UTF-8")
                + "&response_type=code"
                + "&state=" + state;
    }

    /** S4WebController.kakaoCallback 이 state가 "bd-"로 시작하면 여기로 forward(code/state/error 파라미터 그대로). 보통 rgb-tns 호스트에서 실행된다. */
    @GetMapping("/callback")
    public String kakaoCallback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request) throws Exception {
        String[] parsed = parseState(state); // {nonce, origin}, 형식/출발지가 잘못되면 null
        if (parsed == null) return "redirect:" + request.getContextPath() + "/loginCheck?err=state";
        String nonce = parsed[0];
        String origin = parsed[1];
        String ctx = request.getContextPath();
        if (error != null || code == null) return "redirect:" + origin + ctx + "/loginCheck?err=cancel";

        String kakaoKey = PropsUtil.getProperty("keys", "kakaoKey");
        String tokenJson = httpPost(KAKAO_TOKEN_URL,
                "grant_type=authorization_code"
                + "&client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUriFor(origin, request), "UTF-8")
                + "&code=" + URLEncoder.encode(code, "UTF-8"));
        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenJson, new TypeReference<Map<String, Object>>() {});
        String accessToken = (String) tokenMap.get("access_token");
        if (accessToken == null) return "redirect:" + origin + ctx + "/loginCheck?err=token";

        String userJson = httpGetWithBearer(KAKAO_USER_URL, accessToken);
        Map<String, Object> userMap = new ObjectMapper().readValue(userJson, new TypeReference<Map<String, Object>>() {});
        Object idObj = userMap.get("id");
        if (idObj == null) return "redirect:" + origin + ctx + "/loginCheck?err=token";
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
        if (nickname.length() > 30) nickname = nickname.substring(0, 30);

        // 회원 등록/갱신 후 게시판 사용자 ID(일반 회원 K+카카오ID, 관리자 THJEON)
        String userId = uService.boardKakaoLogin(kakaoId, nickname);
        if (userId == null) return "redirect:" + origin + ctx + "/loginCheck?err=token";

        // 세션은 출발 호스트에서 만들어야 하므로 일회용 티켓만 넘긴다.
        String ticket = issueTicket(userId, nickname, nonce);
        return "redirect:" + origin + ctx + "/board/kakao/finish?ticket=" + ticket;
    }

    /** 출발 호스트에서 티켓을 소비해 로그인 세션을 만든다. */
    @GetMapping("/finish")
    public String kakaoFinish(@RequestParam(value = "ticket", required = false) String ticketId,
            HttpServletRequest request, HttpSession session) throws Exception {
        Ticket t = consumeTicket(ticketId);
        if (t == null) return "redirect:/loginCheck?err=token";
        String expectedNonce = (String) session.getAttribute(NONCE_KEY);
        session.removeAttribute(NONCE_KEY);
        if (expectedNonce == null || !expectedNonce.equals(t.nonce)) return "redirect:/loginCheck?err=state";

        // 기존 TUSER에 있는 ID(관리자)면 그 계정의 닉네임("관리자")으로, 아니면 카카오 닉네임으로. 비밀번호 해시는 세션에 넣지 않는다.
        Users u = uService.login(t.userId);
        if (u != null) {
            u.setUserPass(null);
        } else {
            u = new Users();
            u.setUserId(t.userId);
            u.setUserName(t.nickname);
            u.setUserNick(t.nickname);
        }

        String returnUrl = (String) session.getAttribute("returnUrl");
        session.invalidate(); // 세션 고정 방지 -- 로그인 성공 시 새 세션
        HttpSession fresh = request.getSession(true);
        fresh.setAttribute("Users", u);
        return "redirect:" + UserController.safeReturnUrl(request, returnUrl);
    }

    // ── state / origin / 티켓 도우미 ─────────────────────────────────────

    /** 지금 요청의 출발지(scheme://host[:port]). */
    static String originOf(HttpServletRequest request) {
        int port = request.getServerPort();
        String scheme = request.getScheme();
        boolean defPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defPort ? "" : ":" + port);
    }

    static String buildState(String nonce, String origin) throws Exception {
        String o = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(origin.getBytes("UTF-8"));
        return STATE_PREFIX + nonce + "." + o;
    }

    /** {nonce, origin} 또는 null. 출발 origin은 허용 도메인일 때만 통과(오픈 리다이렉트 방지). */
    static String[] parseState(String state) {
        try {
            if (state == null || !state.startsWith(STATE_PREFIX)) return null;
            String rest = state.substring(STATE_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0) return null;
            String nonce = rest.substring(0, dot);
            if (!nonce.matches("[0-9a-f]{16,64}")) return null;
            String origin = new String(java.util.Base64.getUrlDecoder().decode(rest.substring(dot + 1)), "UTF-8");
            return isAllowedOrigin(origin) ? new String[] { nonce, origin } : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 운영 도메인(dev-apc.com과 그 하위) 또는 로컬 개발(localhost/127.0.0.1)만 허용. */
    static boolean isAllowedOrigin(String origin) {
        try {
            java.net.URI u = new java.net.URI(origin);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return false;
            if (u.getPath() != null && !u.getPath().isEmpty()) return false;
            if (u.getQuery() != null || u.getUserInfo() != null || u.getFragment() != null) return false;
            host = host.toLowerCase();
            return host.matches("([a-z0-9-]+\\.)*dev-apc\\.com") || host.equals("localhost") || host.equals("127.0.0.1");
        } catch (Exception e) {
            return false;
        }
    }

    /** 운영 도메인은 카카오 콘솔에 등록된 콜백 하나로, 로컬 개발은 그 호스트의 콜백으로(콘솔에 등록해 둔 경우에만 카카오가 허용). */
    private String redirectUriFor(String origin, HttpServletRequest request) {
        if (origin.contains("localhost") || origin.contains("127.0.0.1")) {
            return origin + request.getContextPath() + "/s4/kakao/callback";
        }
        return REGISTERED_REDIRECT_URI;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String issueTicket(String userId, String nickname, String nonce) {
        purgeExpired();
        if (TICKETS.size() >= TICKET_MAX) TICKETS.clear(); // 비정상 폭주 시 안전장치
        String id = randomHex(16);
        TICKETS.put(id, new Ticket(userId, nickname, nonce));
        return id;
    }

    private static Ticket consumeTicket(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}")) return null;
        Ticket t = TICKETS.remove(id); // 일회용
        if (t == null || t.expiresAt < System.currentTimeMillis()) return null;
        return t;
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Ticket>> it = TICKETS.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue().expiresAt < now) it.remove();
        }
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
