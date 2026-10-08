package my.prac.api.user.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;

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
 * - 저장: TBOARD_KAKAO_MEMBER(KAKAO_ID, USER_ID, NICKNAME). 기존 TUSER/게시판 테이블은 건드리지 않는다.
 *   일반 회원은 USER_ID='K'+카카오ID, 닉네임은 카카오 닉네임(로그인할 때마다 갱신). 관리자는 이 테이블에 THJEON으로 매핑된 행이 있어
 *   그 카카오 계정으로 로그인하면 기존 TUSER의 THJEON(게시판 표기 "관리자")으로 로그인된다 -- 닉네임이 아니라 카카오 고유번호로만 매핑하므로
 *   닉네임을 "전태환"으로 바꿔도 관리자가 될 수 없다.
 * - 세션 키는 기존 게시판과 같은 "Users"(my.prac.core.dto.Users) -- 글쓰기/인터셉터/JSP가 그대로 동작한다. 로그인 성공 시 세션을 새로 발급한다(세션 고정 방지).
 * - 카카오 개발자 콘솔에 등록된 S4 리다이렉트 URI(/s4/kakao/callback)를 그대로 재사용하고(시즌5 로그인과 같은 방식), OAuth state("bd-랜덤")로 구분해
 *   S4WebController.kakaoCallback 이 이 컨트롤러의 /board/kakao/callback 으로 forward 한다.
 */
@Controller
@RequestMapping("/board/kakao")
public class BoardKakaoLoginController {

    public static final String STATE_PREFIX = "bd-";
    private static final String STATE_KEY = "board_kakao_state";

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";

    @Resource(name = "core.prjuser.UserService")
    UserService uService;

    @GetMapping("/auth")
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

    /** S4WebController.kakaoCallback 이 state가 "bd-"로 시작하면 여기로 forward(code/state/error 파라미터 그대로). */
    @GetMapping("/callback")
    public String kakaoCallback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request, HttpSession session) throws Exception {
        String expected = (String) session.getAttribute(STATE_KEY);
        session.removeAttribute(STATE_KEY);
        if (error != null || code == null) return "redirect:/loginCheck?err=cancel";
        if (expected == null || !expected.equals(state)) return "redirect:/loginCheck?err=state";

        String kakaoKey = PropsUtil.getProperty("keys", "kakaoKey");
        String tokenJson = httpPost(KAKAO_TOKEN_URL,
                "grant_type=authorization_code"
                + "&client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(sharedRedirectUri(request), "UTF-8")
                + "&code=" + URLEncoder.encode(code, "UTF-8"));
        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenJson, new TypeReference<Map<String, Object>>() {});
        String accessToken = (String) tokenMap.get("access_token");
        if (accessToken == null) return "redirect:/loginCheck?err=token";

        String userJson = httpGetWithBearer(KAKAO_USER_URL, accessToken);
        Map<String, Object> userMap = new ObjectMapper().readValue(userJson, new TypeReference<Map<String, Object>>() {});
        Object idObj = userMap.get("id");
        if (idObj == null) return "redirect:/loginCheck?err=token";
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
        if (userId == null) return "redirect:/loginCheck?err=token";

        // 기존 TUSER에 있는 ID(관리자)면 그 계정의 닉네임("관리자")으로, 아니면 카카오 닉네임으로. 비밀번호 해시는 세션에 넣지 않는다.
        Users u = uService.login(userId);
        if (u != null) {
            u.setUserPass(null);
        } else {
            u = new Users();
            u.setUserId(userId);
            u.setUserName(nickname);
            u.setUserNick(nickname);
        }

        String returnUrl = (String) session.getAttribute("returnUrl");
        session.invalidate(); // 세션 고정 방지 -- 로그인 성공 시 새 세션
        HttpSession fresh = request.getSession(true);
        fresh.setAttribute("Users", u);
        return "redirect:" + UserController.safeReturnUrl(request, returnUrl);
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
