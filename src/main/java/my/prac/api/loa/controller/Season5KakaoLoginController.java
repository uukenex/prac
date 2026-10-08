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

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    /** 로그인 후 돌아갈 수 있는 곳(오픈 리다이렉트 방지) -- 보호 대상 3페이지만. */
    private static boolean allowedNext(String next) {
        return "/loa/tower-battle-log".equals(next) || "/loa/tower-balance-stats".equals(next);
    }

    /** 다른 컨트롤러(Season5ViewController)가 페이지/API 보호에 쓴다. */
    public static boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute(SESSION_KEY) != null;
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
            HttpServletRequest request, HttpSession session) throws Exception {
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

        String next = (String) session.getAttribute(NEXT_KEY);
        session.removeAttribute(NEXT_KEY);
        return "redirect:" + (next != null && allowedNext(next) ? next : DEFAULT_NEXT);
    }

    /** 페이지 우상단 "닉네임 · 로그아웃" 표시용. */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @ResponseBody
    public Map<String, Object> me(HttpSession session) {
        Map<String, Object> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        HashMap<String, String> me = session == null ? null : (HashMap<String, String>) session.getAttribute(SESSION_KEY);
        out.put("loggedIn", me != null);
        if (me != null) out.put("nickname", me.get("nickname"));
        return out;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
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
