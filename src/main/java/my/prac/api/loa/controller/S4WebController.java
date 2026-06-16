package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.prjbot.service.BotS4Service;
import my.prac.core.util.PropsUtil;

@Controller
@RequestMapping("/s4")
public class S4WebController {

    private static final String KAKAO_AUTH_URL  = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL  = "https://kapi.kakao.com/v2/user/me";
    private static final String SESSION_KEY      = "s4_user";

    @Resource(name = "core.prjbot.BotS4Service")
    BotS4Service s4Service;

    // =====================================================
    // 로그인 화면 (카카오 로그인 버튼)
    // =====================================================
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage(HttpSession session) {
        if (session.getAttribute(SESSION_KEY) != null) {
            return "redirect:/s4/main";
        }
        return "nonsession/s4/login";
    }

    // =====================================================
    // 카카오 OAuth 인가 URL 리다이렉트
    // =====================================================
    @RequestMapping(value = "/kakao/auth", method = RequestMethod.GET)
    public String kakaoAuth(HttpServletRequest request) throws Exception {
        String kakaoKey   = PropsUtil.getProperty("keys", "kakaoKey");
        String redirectUri = getRedirectUri(request);
        String url = KAKAO_AUTH_URL
                + "?client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&response_type=code";
        return "redirect:" + url;
    }

    // =====================================================
    // 카카오 OAuth 콜백
    // =====================================================
    @RequestMapping(value = "/kakao/callback", method = RequestMethod.GET)
    public String kakaoCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request, HttpSession session) throws Exception {

        if (error != null || code == null) {
            return "redirect:/s4/login?err=cancel";
        }

        String kakaoKey    = PropsUtil.getProperty("keys", "kakaoKey");
        String redirectUri = getRedirectUri(request);

        // 1. 토큰 발급
        String tokenJson = httpPost(KAKAO_TOKEN_URL,
                "grant_type=authorization_code"
                + "&client_id=" + kakaoKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&code=" + code);

        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenJson, new TypeReference<Map<String, Object>>() {});
        String accessToken = (String) tokenMap.get("access_token");
        if (accessToken == null) {
            return "redirect:/s4/login?err=token";
        }

        // 2. 사용자 정보 조회
        String userJson = httpGetWithBearer(KAKAO_USER_URL, accessToken);
        Map<String, Object> userMap = new ObjectMapper().readValue(userJson, new TypeReference<Map<String, Object>>() {});
        String kakaoId = String.valueOf(userMap.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) userMap.get("kakao_account");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        String nickname = profile != null ? (String) profile.get("nickname") : "익명";
        if (nickname == null || nickname.isEmpty()) nickname = "익명";

        // 3. DB upsert (닉네임 = userName)
        HashMap<String, Object> member = s4Service.selectMemberByKakaoId(kakaoId);
        String userName = member != null ? member.get("USER_NAME").toString() : nickname;
        s4Service.upsertMember(kakaoId, userName, nickname);

        // 4. 낚시 장비 초기화 (최초 가입 시)
        if (s4Service.selectUserEquip(userName) == null) {
            s4Service.initUserEquip(userName);
        }

        // 5. 세션 저장
        HashMap<String, String> sessionUser = new HashMap<>();
        sessionUser.put("kakaoId",  kakaoId);
        sessionUser.put("userName", userName);
        sessionUser.put("nickname", nickname);
        session.setAttribute(SESSION_KEY, sessionUser);

        return "redirect:/s4/main";
    }

    // =====================================================
    // 낚시 메인 페이지
    // =====================================================
    @RequestMapping(value = "/main", method = RequestMethod.GET)
    public String mainPage(HttpSession session, Model model) {
        HashMap<?, ?> user = (HashMap<?, ?>) session.getAttribute(SESSION_KEY);
        if (user == null) return "redirect:/s4/login";

        String userName = (String) user.get("userName");
        String nickname = (String) user.get("nickname");
        model.addAttribute("userName", userName);
        model.addAttribute("nickname", nickname);

        boolean todayFished = s4Service.selectTodayFishingLog(userName) != null;
        model.addAttribute("todayFished", todayFished);

        HashMap<String, Object> equip = s4Service.selectUserEquip(userName);
        if (equip == null) equip = new HashMap<>();
        model.addAttribute("equip", equip);

        return "nonsession/s4/main";
    }

    // =====================================================
    // Ajax - 낚시하기
    // =====================================================
    @RequestMapping(value = "/api/fishing", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> apiFishing(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        HashMap<?, ?> user = (HashMap<?, ?>) session.getAttribute(SESSION_KEY);
        if (user == null) { result.put("error", "로그인이 필요합니다."); return result; }

        String userName = (String) user.get("userName");

        if (s4Service.selectTodayFishingLog(userName) != null) {
            result.put("error", "오늘은 이미 낚시를 했습니다. 내일 다시 도전하세요!");
            return result;
        }

        HashMap<String, Object> equip = s4Service.selectUserEquip(userName);
        if (equip == null) equip = new HashMap<>();
        int rodGrade    = equip.get("ROD_GRADE")    != null ? ((Number) equip.get("ROD_GRADE")).intValue()    : 1;
        int bobberGrade = equip.get("BOBBER_GRADE") != null ? ((Number) equip.get("BOBBER_GRADE")).intValue() : 1;

        String msg = s4Service.fishing(userName, rodGrade, bobberGrade);
        result.put("message", msg);
        result.put("success", true);
        return result;
    }

    // =====================================================
    // Ajax - 낚시가방
    // =====================================================
    @RequestMapping(value = "/api/bag", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> apiBag(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        HashMap<?, ?> user = (HashMap<?, ?>) session.getAttribute(SESSION_KEY);
        if (user == null) { result.put("error", "로그인이 필요합니다."); return result; }

        String userName = (String) user.get("userName");
        List<HashMap<String, Object>> inv  = s4Service.selectFishInv(userName);
        List<HashMap<String, Object>> achs = s4Service.selectUserAchievements(userName);
        result.put("inv",  inv);
        result.put("achs", achs);
        return result;
    }

    // =====================================================
    // 로그아웃
    // =====================================================
    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/s4/login";
    }

    // =====================================================
    // Helper
    // =====================================================
    private String getRedirectUri(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort())
                + request.getContextPath() + "/s4/kakao/callback";
    }

    private String httpPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        return readResponse(conn);
    }

    private String httpGetWithBearer(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
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
        br.close();
        return sb.toString();
    }
}
