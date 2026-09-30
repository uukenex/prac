package my.prac.core.prjbot.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * [2026-09-30] 밸런스 통계 소급 집계 도구(일회성/재실행용 오프라인 실행, 앱 배포와 무관).
 * 과거 전투 로그 텍스트(TBOT_S5_WORD_HIS + 복사 시점 이후의 TBOT_WORD_HIS)를 BattleLogParser로 전투 1회 단위로
 * 복원해 TBOT_S5_BATTLE_STAT에 SRC='LOG'로 넣는다. 재실행하면 기존 SRC='LOG' 행을 지우고 다시 만든다.
 * 앱이 기록한 SRC='LIVE' 행이 이미 있으면 그 첫 시각 이전 구간만 소급해서 중복이 없다.
 *
 * 실행(접속 정보는 인자로만 받는다, 저장소에 넣지 않음):
 *   javac -encoding UTF-8 -cp "target/classes;ojdbc8.jar;json.jar" -d out tools/S5BattleStatBackfill.java
 *   java -cp "target/classes;out;ojdbc8.jar;json.jar" my.prac.core.prjbot.service.impl.S5BattleStatBackfill \
 *        "jdbc:oracle:thin:@//host:port/SID" USER PASSWORD 7 http://rgb-tns.dev-apc.com [dry]
 *
 * 근사치 주의: 파티 전투력은 로그에서 복원한 그 시점의 HP(최대)/ATK(굴림 역산)를 쓰고, 로그에 안 나오는 방어력은
 * "지금" 그 동료의 방어력(라이브 /api/tower-party)으로 대체한다. 몬스터 종류(중간보스)는 등장 문구로 구분되지 않는
 * 경우 같은 층 일반 몬스터 공격력의 2.5배 이상이면 M으로 본다.
 */
public class S5BattleStatBackfill {

    static final Map<String, String> JOB_CODE = new HashMap<>();
    static {
        JOB_CODE.put("전사", "WARRIOR"); JOB_CODE.put("마법사", "MAGE"); JOB_CODE.put("도적", "ROGUE");
        JOB_CODE.put("궁수", "ARCHER"); JOB_CODE.put("도사", "PRIEST");
    }

    static long combatPower(double hp, double atk, double def) { return Math.round(hp * 1.0 + atk * 10.0 + def * 8.0); }

    /** "234333" / "7.56a" / "1.2b" -> 기본값(a=x10000, b=x10000^2 ...). */
    static double parseHp(String s) {
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            double v = Double.parseDouble(s.substring(0, s.length() - 1));
            return v * Math.pow(10000, last - 'a' + 1);
        }
        return Double.parseDouble(s);
    }

    public static void main(String[] a) throws Exception {
        String url = a[0], dbUser = a[1], dbPw = a[2];
        int days = Integer.parseInt(a[3]);
        String api = a[4];
        boolean dry = a.length > 5 && "dry".equals(a[5]); // dry: 전부 처리하되 마지막에 rollback(DB 변경 없음)
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPw)) {
            c.setAutoCommit(false);
            // 1) 대상 유저(테스트/관리자 제외)
            List<String> users = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT USER_NAME FROM TBOT_S5_USER_PROGRESS WHERE NVL(NO_COOLDOWN_YN,'N') <> 'Y'");
                 ResultSet rs = ps.executeQuery()) { while (rs.next()) users.add(rs.getString(1)); }
            // 2) 구간: [now-days, 앱이 기록한 첫 LIVE 행 or now)
            Timestamp to;
            try (PreparedStatement ps = c.prepareStatement("SELECT MIN(REG_DATE) FROM TBOT_S5_BATTLE_STAT WHERE SRC = 'LIVE'");
                 ResultSet rs = ps.executeQuery()) { rs.next(); Timestamp m = rs.getTimestamp(1); to = m != null ? m : new Timestamp(System.currentTimeMillis()); }
            Timestamp from = new Timestamp(to.getTime() - days * 86400000L);
            System.out.println("users=" + users.size() + " window=" + from + " ~ " + to);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM TBOT_S5_BATTLE_STAT WHERE SRC = 'LOG'")) { System.out.println("deleted old LOG rows=" + ps.executeUpdate()); }

            String sql = "SELECT INSERT_DATE, RES FROM ("
                    + " SELECT INSERT_DATE, TO_NCHAR(RES) AS RES FROM TBOT_S5_WORD_HIS WHERE USER_NAME = ? AND INSERT_DATE >= ? AND INSERT_DATE < ?"
                    + " UNION ALL"
                    + " SELECT INSERT_DATE, TO_NCHAR(RES) AS RES FROM TBOT_WORD_HIS WHERE USER_NAME = ? AND INSERT_DATE >= ? AND INSERT_DATE < ?"
                    + "   AND INSERT_DATE > TO_DATE('2026-09-30 00:20:58','YYYY-MM-DD HH24:MI:SS')"
                    + ") ORDER BY INSERT_DATE";
            String ins = "INSERT INTO TBOT_S5_BATTLE_STAT (REG_DATE, USER_NAME, FLOOR, KIND, COMBO, PARTY_SIZE, PARTY_POWER, MON_POWER, RESULT, TURNS, DEAD_CNT, SRC)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,'LOG')";
            int totalRows = 0, totalFights = 0;
            Map<String, int[]> resultCnt = new HashMap<>();
            for (String user : users) {
                List<BattleLogParser.Row> rows = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, user); ps.setTimestamp(2, from); ps.setTimestamp(3, to);
                    ps.setString(4, user); ps.setTimestamp(5, from); ps.setTimestamp(6, to);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) rows.add(new BattleLogParser.Row(user, new Date(rs.getTimestamp(1).getTime()), rs.getString(2)));
                    }
                }
                List<BattleLogParser.Fight> fights = BattleLogParser.parse(rows);
                totalRows += rows.size();
                if (fights.isEmpty()) { System.out.println(user + ": rows=" + rows.size() + " fights=0"); continue; }

                // 같은 층 일반 몬스터 공격력 기준(중간보스 구분용): 명시 종류 N인 전투의 최소 공격력
                Map<Integer, Long> baseAtk = new HashMap<>();
                for (BattleLogParser.Fight f : fights) if ("N".equals(f.kind)) baseAtk.merge(f.floor, f.monAtk, Math::min);
                Map<String, Long> defByName = fetchDef(api, user);

                int ok = 0;
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (BattleLogParser.Fight f : fights) {
                        String kind = f.kind;
                        Long b = baseAtk.get(f.floor);
                        if ("N".equals(kind) && b != null && b > 0 && f.monAtk >= b * 2.5) kind = "M";
                        double mult = "B".equals(kind) ? 1.6 : 1.0;
                        long monPower = combatPower(parseHp(f.monHp), f.monAtk * mult, f.monDef);
                        List<String> jobs = new ArrayList<>();
                        long power = 0;
                        for (BattleLogParser.Member m : f.members) {
                            jobs.add(JOB_CODE.getOrDefault(m.jobKr, m.jobKr));
                            long def = defByName.containsKey(m.name) ? defByName.get(m.name) : Math.round(m.hpMax * 0.04);
                            power += combatPower(m.hpMax, m.atk, def);
                        }
                        Collections.sort(jobs);
                        int i = 1;
                        ps.setTimestamp(i++, new Timestamp(f.endAt.getTime()));
                        ps.setString(i++, user);
                        ps.setInt(i++, f.floor);
                        ps.setString(i++, kind);
                        ps.setString(i++, String.join("-", jobs));
                        ps.setInt(i++, jobs.size());
                        ps.setLong(i++, power);
                        ps.setLong(i++, monPower);
                        ps.setString(i++, f.result);
                        ps.setInt(i++, f.turns);
                        ps.setInt(i++, f.deadCnt);
                        ps.addBatch(); ok++;
                        resultCnt.computeIfAbsent(f.result, k -> new int[1])[0]++;
                    }
                    ps.executeBatch();
                }
                totalFights += ok;
                System.out.println(user + ": rows=" + rows.size() + " fights=" + ok);
            }
            if (dry) { c.rollback(); System.out.println("[dry-run] rollback"); } else c.commit();
            System.out.println("parser diag: starts=" + BattleLogParser.cStart + " droppedOverwritten=" + BattleLogParser.cDropOverwritten + "(noAttack=" + BattleLogParser.cOvNoAttack + ",withAttack=" + BattleLogParser.cOvWithAttack + ")"
                    + " noFloor=" + BattleLogParser.cDropNoFloor + " noMonster=" + BattleLogParser.cDropNoMonster
                    + " endNoMembers=" + BattleLogParser.cEndNoMembers + " fleeNoMembers=" + BattleLogParser.cFleeNoMembers);
            System.out.println("DONE rows=" + totalRows + " fights=" + totalFights);
            for (Map.Entry<String, int[]> e : resultCnt.entrySet()) System.out.println("  " + e.getKey() + "=" + e.getValue()[0]);
        }
    }

    /** 라이브 /api/tower-party로 그 유저 동료들의 현재 방어력(이름 기준). 실패하면 빈 맵(추정치 사용). */
    static Map<String, Long> fetchDef(String api, String user) {
        Map<String, Long> out = new HashMap<>();
        try {
            URL u = new URL(api + "/loa/api/tower-party?userName=" + URLEncoder.encode(user, "UTF-8"));
            HttpURLConnection h = (HttpURLConnection) u.openConnection();
            h.setConnectTimeout(8000); h.setReadTimeout(20000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (RgbTowerBot backfill tool)"); // Cloudflare가 기본 Java UA를 403으로 막음
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) {
                String line; while ((line = br.readLine()) != null) sb.append(line);
            }
            String body = sb.toString().trim();
            JSONArray arr = body.startsWith("[") ? new JSONArray(body) : new JSONObject(body).getJSONArray("companions");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.has("NAME") && o.has("EFF_DEF")) out.put(o.getString("NAME"), o.getLong("EFF_DEF"));
            }
        } catch (Exception e) {
            System.out.println("  (방어력 조회 실패 -> 추정치 사용: " + user + " " + e.getMessage() + ")");
        }
        return out;
    }
}
