package my.prac.api.loa.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;

import my.prac.core.util.GridUtil;

@Controller
public class WeatherController {
	private static final String KAKAO_REST_API_KEY = "카카오_REST_API_키";
    private static final String KMA_SERVICE_KEY = "기상청_디코딩_서비스키";
    private static final String enterStr = System.lineSeparator();

    String weatherSearch(String area) throws Exception {
        String errMsg = "불러올 수 없는 지역이거나 지원되지 않는 지역입니다." + enterStr + "ex)00시00구00동 (띄어쓰기없이)";
        String retMsg;

        try {
            // 1. 주소 -> 위경도
            double[] latLon = geocodeAddress(area);
            if (latLon == null) {
                return errMsg;
            }

            // 2. 위경도 -> 격자좌표 (기 작성한 GridUtil 사용)
            int[] grid = GridUtil.toGrid(latLon[0], latLon[1]);
            int nx = grid[0];
            int ny = grid[1];

            // 3. 기상청 초단기실황 조회
            JSONObject ncstJson = new JSONObject(callUltraSrtNcst(nx, ny));
            Map<String, String> ncst = extractItems(ncstJson);

            if (ncst.isEmpty() || !ncst.containsKey("T1H")) {
                return errMsg;
            }

            // 4. 기상청 초단기예보 조회 (하늘상태 SKY, 강수형태 PTY 등 보강용)
            JSONObject fcstJson = new JSONObject(callUltraSrtFcst(nx, ny));
            Map<String, String> fcst = extractFirstFcstItems(fcstJson);

            // 5. 메시지 조립
            String temp = ncst.get("T1H");             // 기온(℃)
            String humidity = ncst.get("REH");          // 습도(%)
            String rain1h = ncst.get("RN1");             // 1시간 강수량
            String windSpeed = ncst.get("WSD");           // 풍속(m/s)
            String ptyCode = ncst.containsKey("PTY") ? ncst.get("PTY") : fcst.get("PTY");
            String skyCode = fcst.get("SKY");             // 초단기실황에는 SKY 없음, 예보에서 보강

            String skyText = skyText(skyCode, ptyCode);

            retMsg = "오늘날씨 : " + skyText;
            retMsg += enterStr + "현재온도 : " + temp + "℃";
            if (humidity != null) {
                retMsg += enterStr + "습도 : " + humidity + "%";
            }
            if (windSpeed != null) {
                retMsg += enterStr + "풍속 : " + windSpeed + "m/s";
            }
            if (rain1h != null && !rain1h.trim().equals("0") && !rain1h.trim().isEmpty()) {
                retMsg += enterStr + "1시간 강수량 : " + rain1h;
            }
            retMsg += enterStr;
            retMsg += enterStr + "현재 " + area + "의 온도는 " + temp + "℃ 입니다.";

        } catch (Exception e) {
            System.out.println(e.getMessage());
            retMsg = errMsg;
        }
        return retMsg;
    }

    // ===================== 주소 -> 위경도 (카카오 로컬 API) =====================
    private double[] geocodeAddress(String area) throws Exception {
        String urlStr = "https://dapi.kakao.com/v2/local/search/address.json?query="
                + URLEncoder.encode(area, "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "KakaoAK " + KAKAO_REST_API_KEY);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        String body = readBody(conn);
        conn.disconnect();

        JSONObject json = new JSONObject(body);
        JSONArray documents = json.getJSONArray("documents");
        if (documents.length() == 0) {
            return null; // 주소 검색 실패
        }
        JSONObject first = documents.getJSONObject(0);
        double lon = first.getDouble("x"); // 경도
        double lat = first.getDouble("y"); // 위도
        return new double[]{lat, lon};
    }

    // ===================== 기상청 초단기실황 =====================
    private String callUltraSrtNcst(int nx, int ny) throws Exception {
        String[] base = getBaseDateTimeForNcst();
        String urlStr = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
                + "?serviceKey=" + KMA_SERVICE_KEY
                + "&pageNo=1&numOfRows=10&dataType=JSON"
                + "&base_date=" + base[0]
                + "&base_time=" + base[1]
                + "&nx=" + nx + "&ny=" + ny;
        return httpGet(urlStr);
    }

    // ===================== 기상청 초단기예보 (하늘상태 보강) =====================
    private String callUltraSrtFcst(int nx, int ny) throws Exception {
        String[] base = getBaseDateTimeForFcst();
        String urlStr = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst"
                + "?serviceKey=" + KMA_SERVICE_KEY
                + "&pageNo=1&numOfRows=60&dataType=JSON"
                + "&base_date=" + base[0]
                + "&base_time=" + base[1]
                + "&nx=" + nx + "&ny=" + ny;
        return httpGet(urlStr);
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        String body = readBody(conn);
        conn.disconnect();
        return body;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // 초단기실황: 매시 40분 이후에 그 시각 데이터 제공 -> 40분 이전이면 이전 시각으로
    private String[] getBaseDateTimeForNcst() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.MINUTE) < 40) {
            cal.add(Calendar.HOUR_OF_DAY, -1);
        }
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
        String baseDate = dateFmt.format(cal.getTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String baseTime = String.format("%02d00", hour);
        return new String[]{baseDate, baseTime};
    }

    // 초단기예보: 매시 30분 이후 생산, 45분 이후 제공 권장 -> 45분 이전이면 이전 시각
    private String[] getBaseDateTimeForFcst() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.MINUTE) < 45) {
            cal.add(Calendar.HOUR_OF_DAY, -1);
        }
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
        String baseDate = dateFmt.format(cal.getTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String baseTime = String.format("%02d30", hour);
        return new String[]{baseDate, baseTime};
    }

    // 초단기실황 item -> Map<category, value>
    private Map<String, String> extractItems(JSONObject root) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONArray items = root.getJSONObject("response")
                    .getJSONObject("body").getJSONObject("items").getJSONArray("item");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                map.put(item.getString("category"), item.getString("obsrValue"));
            }
        } catch (Exception e) {
            // 응답 구조 이상 시 빈 맵 반환
        }
        return map;
    }

    // 초단기예보는 fcstValue 사용, 가장 가까운 미래 시각 1건만 사용
    private Map<String, String> extractFirstFcstItems(JSONObject root) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONArray items = root.getJSONObject("response")
                    .getJSONObject("body").getJSONObject("items").getJSONArray("item");
            String targetTime = null;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String fcstTime = item.getString("fcstTime");
                if (targetTime == null) {
                    targetTime = fcstTime;
                }
                if (fcstTime.equals(targetTime)) {
                    map.put(item.getString("category"), item.getString("fcstValue"));
                }
            }
        } catch (Exception e) {
            // 응답 구조 이상 시 빈 맵 반환
        }
        return map;
    }

    // SKY(하늘상태) + PTY(강수형태) -> 텍스트
    private String skyText(String sky, String pty) {
        if (pty != null) {
            switch (pty) {
                case "1": return "비";
                case "2": return "비/눈";
                case "3": return "눈";
                case "4": return "소나기";
                case "5": return "빗방울";
                case "6": return "빗방울눈날림";
                case "7": return "눈날림";
                default: break; // 0: 없음 -> sky로 판단
            }
        }
        if (sky == null) return "정보없음";
        switch (sky) {
            case "1": return "맑음";
            case "3": return "구름많음";
            case "4": return "흐림";
            default: return "정보없음";
        }
    }
}
