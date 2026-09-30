package my.prac.core.prjbot.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [2026-09-30] 밸런스 통계 소급 집계용 -- 채팅/웹 응답 로그 텍스트(TBOT_S5_WORD_HIS 등)를 시간순으로 읽어
 * 전투 1회(등장 ~ 처치/전멸/도망) 단위 기록으로 복원하는 순수 파서(Spring/DB 의존 없음).
 *
 * 로그 형식(응답 문자열 안의 줄바꿈은 "♬"): 등장 줄 "👾 등장!/몬스터명/⚔️ATK 🛡️DEF ❤️HP", 층은 같은 행의
 * "🗺️ N층 ..단계" / "탐사 현황: N층" / "→ N층(" 에서, 파티는 "★4도사(이름) 💗현재/최대" 줄과 그 다음
 * "🎲굴림→원본dmg" 줄(원본dmg = 공격력 x 굴림이라 공격력을 복원), 종료는 "처치!"(승리) / "파티 전멸"(전멸) /
 * "전투에서 도망쳤습니다"(도망). 이모지에 의존하지 않도록 한글/숫자 패턴만 쓴다.
 */
final class BattleLogParser {

    static final class Row {
        final String user; final Date at; final String res;
        Row(String user, Date at, String res) { this.user = user; this.at = at; this.res = res == null ? "" : res; }
    }

    static final class Member {
        String jobKr; int grade; String name; long hpMax; long atk; int hpCur;
    }

    static final class Fight {
        String user; Date endAt; int floor; String kind; // N/E/M/B
        String monHp; long monAtk; long monDef;           // monHp는 "7.56a" 같은 원문(호출측이 PP.parse)
        List<Member> members = new ArrayList<>();
        String result; int turns; int deadCnt;
    }

    private static final Pattern P_FLOOR_MOVE = Pattern.compile("→\\s*(\\d+)층\\(");
    private static final Pattern P_FLOOR_STAGE = Pattern.compile("(\\d+)층\\s+\\d+/\\d+단계");
    private static final Pattern P_FLOOR_EXPLORE = Pattern.compile("탐사 현황:\\s*(\\d+)층");
    private static final Pattern P_FLOOR_CLIMB = Pattern.compile("계단을 올라\\s*(\\d+)층");
    private static final Pattern P_FLOOR_VILLAGE = Pattern.compile("(\\d+)층 마을로 이동");
    private static final Pattern P_NUM = Pattern.compile("(\\d+(?:\\.\\d+)?[a-z]?)");
    private static final Pattern P_MEMBER = Pattern.compile("^★(\\d)([가-힣]+)\\((.+?)\\)\\s*\\D{0,4}?(\\d+)/(\\d+)\\s*$");
    private static final Pattern P_ROLL = Pattern.compile("🎲\\s*(\\d+(?:\\+\\d+)?)\\s*→\\s*(\\d+)dmg");

    // 진단용 카운터(소급 집계 도구가 출력). droppedOverwritten(noAttack)은 정상: 자동사냥이 이동 중에 조용히 정산한
    // 조우(로그에 공격/처치 줄이 없고 다음 이동에서 곧바로 새 "등장!"이 나옴)라 실제 전투 기록이 아니므로 버린다.
    static int cOvNoAttack, cOvWithAttack, cStart, cDropOverwritten, cDropNoFloor, cDropNoMonster, cEndNoMembers, cFleeNoMembers;

    /** rows는 한 유저의 행을 시간 오름차순으로 준다. */
    static List<Fight> parse(List<Row> rows) {
        List<Fight> out = new ArrayList<>();
        Integer floor = null;
        Fight cur = null;
        Map<String, Member> members = null;
        boolean hasAttack = false;

        for (Row r : rows) {
            String res = r.res;
            if (res.isEmpty() || res.contains("쿨타임")) continue;

            Integer f = floorOf(res);
            if (f != null) floor = f;

            boolean start = res.contains("등장!") && res.contains("전투를 시작하려면");
            if (start) {
                // 이전 전투가 끝맺음 없이 남아있으면(결과 불명) 버린다.
                cStart++;
                if (cur != null) { cDropOverwritten++; if (hasAttack) cOvWithAttack++; else cOvNoAttack++; }
                cur = new Fight();
                cur.user = r.user;
                members = new LinkedHashMap<>();
                hasAttack = false;
                if (f != null) cur.floor = f; else if (floor != null) cur.floor = floor; else { cDropNoFloor++; cur = null; continue; }
                cur.kind = res.contains("보스 등장") ? "B" : res.contains("강화 등장") ? "E" : res.contains("중간보스 칸") ? "M" : "N";
                if (!parseMonster(res, cur)) { cDropNoMonster++; cur = null; continue; }
                continue;
            }
            if (cur == null) continue;

            // 도망(전투 중 층 이동)
            if (res.contains("전투에서 도망쳤습니다")) {
                if (!members.isEmpty()) out.add(finish(cur, members, "FLEE", r.at, false)); else cFleeNoMembers++;
                cur = null; members = null;
                continue;
            }

            boolean attackRow = parseAttackLines(res, members);
            if (attackRow) { cur.turns++; hasAttack = true; }

            if (res.contains("파티 전멸")) {
                if (!members.isEmpty()) out.add(finish(cur, members, "WIPE", r.at, true)); else cEndNoMembers++;
                cur = null; members = null;
            } else if (res.contains("처치!") && hasAttack) {
                if (!members.isEmpty()) out.add(finish(cur, members, "WIN", r.at, false)); else cEndNoMembers++;
                cur = null; members = null;
            }
        }
        return out;
    }

    private static Integer floorOf(String res) {
        Integer v = null;
        Matcher m;
        m = P_FLOOR_VILLAGE.matcher(res); if (m.find()) v = Integer.valueOf(m.group(1));
        m = P_FLOOR_CLIMB.matcher(res);   if (m.find()) v = Integer.valueOf(m.group(1));
        m = P_FLOOR_EXPLORE.matcher(res); if (m.find()) v = Integer.valueOf(m.group(1));
        m = P_FLOOR_STAGE.matcher(res);   if (m.find()) v = Integer.valueOf(m.group(1));
        m = P_FLOOR_MOVE.matcher(res);    if (m.find()) v = Integer.valueOf(m.group(1));
        return v;
    }

    /** "등장!" 줄 다음(몬스터명) 다음 줄의 "ATK DEF HP" 숫자 3개. */
    private static boolean parseMonster(String res, Fight f) {
        String[] lines = res.split("♬");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("등장!")) continue;
            for (int j = i + 1; j <= i + 3 && j < lines.length; j++) {
                Matcher m = P_NUM.matcher(lines[j]);
                List<String> nums = new ArrayList<>();
                while (m.find()) nums.add(m.group(1));
                if (nums.size() >= 3 && lines[j].contains("❤")) {
                    try {
                        f.monAtk = Long.parseLong(nums.get(0));
                        f.monDef = Long.parseLong(nums.get(1));
                        f.monHp = nums.get(2);
                        return true;
                    } catch (NumberFormatException e) { return false; }
                }
            }
        }
        return false;
    }

    /** 파티 상태 줄 + 굴림 줄을 읽어 members(이름 기준)를 채운다. 공격 턴 행이면 true. */
    private static boolean parseAttackLines(String res, Map<String, Member> members) {
        String[] lines = res.split("♬");
        boolean any = false;
        Member last = null;
        boolean pastSummary = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("전투결과")) { pastSummary = true; continue; }
            Matcher pm = P_MEMBER.matcher(line);
            if (pm.matches()) {
                String name = pm.group(3);
                Member m = members.get(name);
                if (m == null) {
                    m = new Member();
                    m.grade = Integer.parseInt(pm.group(1));
                    m.jobKr = pm.group(2);
                    m.name = name;
                    m.hpMax = Long.parseLong(pm.group(5));
                    members.put(name, m);
                }
                m.hpCur = Integer.parseInt(pm.group(4));
                last = pastSummary ? null : m;
                any = true;
                continue;
            }
            if (last != null) {
                Matcher rm = P_ROLL.matcher(line);
                if (rm.find() && last.atk == 0) {
                    String rollTxt = rm.group(1);
                    long roll = 0;
                    for (String p : rollTxt.split("\\+")) roll += Long.parseLong(p);
                    long rawDmg = Long.parseLong(rm.group(2));
                    if (roll > 0) last.atk = Math.round((double) rawDmg / roll);
                }
                if (rm.find(0)) last = null; // 굴림 줄을 읽었으면 다음 멤버 줄까지 대기
            }
        }
        return any;
    }

    private static Fight finish(Fight f, Map<String, Member> members, String result, Date at, boolean allDead) {
        f.members = new ArrayList<>(members.values());
        f.result = result;
        f.endAt = at;
        int dead = 0;
        for (Member m : f.members) if (m.hpCur <= 0) dead++;
        f.deadCnt = allDead ? f.members.size() : dead;
        return f;
    }
}
