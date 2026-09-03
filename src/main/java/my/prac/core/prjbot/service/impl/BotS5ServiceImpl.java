package my.prac.core.prjbot.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.service.BotS5Service;
import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 시스템 서비스 구현체.
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 */
@Service("core.prjbot.BotS5Service")
public class BotS5ServiceImpl implements BotS5Service {

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO dao;

    private static final String NL  = "♬";
    private static final Random RND = new Random();
    // 채팅(카톡 등)에 상대경로만 넣으면 링크가 도메인 없이 잘려 나가서(클릭 불가) 이 프로젝트의
    // 다른 시즌들과 동일하게 절대경로로 고정한다(LoaChatController/BossAttackController 등 참고).
    private static final String TOWER_VIEW_URL = "http://rgb-tns.dev-apc.com/loa/tower-view";

    /** tower-view SPA 링크 + userName 쿼리파라미터(URL 인코딩) -- 클릭하면 그 유저 화면이 바로 뜨도록. */
    private String towerViewLink(String userName) {
        try {
            return TOWER_VIEW_URL + "?userName=" + URLEncoder.encode(userName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return TOWER_VIEW_URL + "?userName=" + userName; // UTF-8은 항상 지원되므로 사실상 발생 안 함
        }
    }

    private static final String[] JOB_KEYS = { "WARRIOR", "MAGE", "ROGUE", "ARCHER", "PRIEST" };

    // 등급(성급) 베이스 스탯 [HP, ATK, DEF], index0 = ★1
    private static final int[][] GRADE_BASE = {
        { 100, 10, 5 },
        { 130, 13, 7 },
        { 182, 18, 10 },
        { 282, 28, 16 },
        { 494, 49, 28 },
        { 988, 98, 56 },
    };

    // 직업별 배율 [HP, ATK, DEF]
    private static final HashMap<String, double[]> JOB_MULT = new HashMap<String, double[]>() {{
        put("WARRIOR", new double[]{ 1.5, 1.0, 2.0 });
        put("MAGE",    new double[]{ 0.7, 2.0, 0.6 });
        put("ROGUE",   new double[]{ 0.9, 1.4, 1.0 });
        put("ARCHER",  new double[]{ 0.7, 1.8, 0.8 });
        put("PRIEST",  new double[]{ 1.2, 0.6, 1.6 });
    }};

    private static final HashMap<String, String> JOB_NAME = new HashMap<String, String>() {{
        put("WARRIOR", "전사"); put("MAGE", "마법사"); put("ROGUE", "도적");
        put("ARCHER", "궁수");  put("PRIEST", "도사");
    }};

    // 동료 뽑을 때 붙는 이름 -- 직업별×등급별로 관리(도감/애착 형성을 위해 의도적으로 좁힘).
    // [설계 변경] 원래는 직업당 3종을 등급(GRADE) 구분 없이 통으로 공유해서, 다른 등급끼리도 같은
    // 이름이 겹쳐 뽑힐 수 있었다(예: ★2 소라와 ★3 소라가 동시에 존재 가능) -- 이게 중복(dupe) 판정을
    // "직업+이름"만 보고 하던 로직과 만나 "이미 있는 이름이 더 높은 등급으로 다시 나와도 그냥
    // 증발한다"는 버그의 근본 원인이었다(실사례: 타락고냥이/바드의 ★2 궁수 "소라" 보유 중 ★3 궁수
    // 픽업이 증발). 이제 이름 자체가 등급을 내포하도록(★1 3종/★2 3종/★3 3종/★4 2종/★5 1종/★6
    // 1종, 직업당 총 13종·전체 5직업×13=65종) 등급별로 완전히 분리해서, 서로 다른 등급끼리는 이름이
    // 절대 겹치지 않는다 -- 이러면 "직업+이름"이 같다는 건 곧 "등급도 같다"는 뜻이 되어 애초에
    // 등급이 다른데 증발하는 상황 자체가 구조적으로 불가능해진다(중복 판정 코드의 등급 비교는
    // 방어적으로 그대로 둠). 기존 데이터는 S5_NAME_TIER_MIGRATION.sql로 등급에 맞는 새 이름으로
    // 일괄 재배정(마이그레이션) 완료. 별도 이름관리 테이블은 없고 이 상수가 유일한 소스 --
    // 뽑기 시점에 TBOT_S5_USER_COMPANION.NAME 컬럼에 그대로 저장됨. 배열 인덱스 0~5 = 등급 1~6.
    private static final HashMap<String, String[][]> NAME_POOL_BY_JOB_GRADE = new HashMap<String, String[][]>() {{
        put("WARRIOR", new String[][]{
            { "리쿠", "소우타", "슌" },
            { "켄고", "다이키", "유마" },
            { "하야토", "코타로", "진" },
            { "렌지", "아츠시" },
            { "츠요시" },
            { "고우키" },
        });
        put("MAGE", new String[][]{
            { "유키", "아오이", "이츠키" },
            { "미유", "리사", "나오" },
            { "사야", "마이", "유나" },
            { "리오", "카나" },
            { "미코토" },
            { "세라" },
        });
        put("ROGUE", new String[][]{
            { "카이토", "료", "츠바사" },
            { "신지", "타쿠야", "겐지" },
            { "레이", "아키라", "소마" },
            { "유이토", "카게로우" },
            { "나기" },
            { "야토" },
        });
        put("ARCHER", new String[][]{
            { "소라", "유토", "나나" },
            { "아야", "미사키", "유즈키" },
            { "리코", "마유", "하루카" },
            { "세리나", "츠키미" },
            { "스즈네" },
            { "아마츠" },
        });
        put("PRIEST", new String[][]{
            { "사쿠라", "히나", "유이" },
            { "모모카", "이오리", "시온" },
            { "노조미", "아사히", "렌게" },
            { "코하루", "미레이" },
            { "스이렌" },
            { "아마네" },
        });
    }};

    // 사냥터층(보스 제외) 몬스터 이름 -- 50종만 관리하고, 80개 사냥터층(블록1~10 × 8칸)에
    // 블록/칸 순서(1~8, 11~18, ...)로 순환 배정. 51번째부터는 앞에서부터 다시 돌면서
    // "강화 " 접두사를 붙여 재사용(스탯은 그대로 블록 단위 유지, 이름만 층마다 다르게 보이는 용도).
    // 보스(BOSS_YN='Y')는 이 배열을 쓰지 않고 TBOT_S5_MONSTER_INFO의 블록별 고유 보스 이름을 그대로 쓴다.
    private static final String[] FLOOR_MONSTER_NAME = {
        "슬라임", "들쥐", "야생 늑대", "독버섯 정령", "숲도둑 고블린", "박쥐 무리", "성난 멧돼지", "덤불 살모사",
        "곰팡이 골렘", "낡은 갑옷 유령", "동굴 거미", "뿔토끼", "이끼 트롤", "흙탕 지렁이", "떠돌이 산적",
        "가시덩굴 괴물", "얼어붙은 스켈레톤", "불도롱뇽", "안개 늑대인간", "썩은 나무 정령", "쇠사슬 죄수",
        "폐광 광부 좀비", "지하수로 악어", "곰팡이 박쥐", "돌개비", "탐욕의 임프", "습지 늪괴물",
        "부서진 인형병정", "칼날 까마귀", "얼음 정령", "화염 도마뱀", "바위 두더지", "저주받은 기사",
        "그림자 늑대", "폭풍 매", "독안개 요정", "뼈다귀 사냥개", "붉은눈 오크", "고대 석상 파수꾼",
        "심연의 촉수", "탐식하는 거머리", "번개 다람쥐", "달빛 여우령", "먼지 유령", "타오르는 해골병사",
        "얼음 여왕의 시종", "가시갑옷 멧돼지", "칠흑 까마귀왕", "폐허의 파수병", "심연 박쥐", "천벌의 사슬귀",
    };

    /** 사냥터층(비보스) 표시용 몬스터 이름. 보스는 mon의 DB 이름을 그대로 쓴다. */
    private String floorMonsterName(int floor, HashMap<String, Object> mon) {
        if ("Y".equals(strVal(mon.get("BOSS_YN"), "N"))) return strVal(mon.get("MONSTER_NAME"), "보스");
        int pos = (floor / 10) * 8 + (floor % 10); // 1층=1, 8층=8, 11층=9 ... 순환 일련번호
        if (pos < 1) pos = 1;
        int n = FLOOR_MONSTER_NAME.length;
        String base = FLOOR_MONSTER_NAME[(pos - 1) % n];
        return pos > n ? "강화 " + base : base;
    }

    /** 강화몬스터방(ELITE)용 이름 표시. elite=true면 "💪 강화 " 접두어를 붙인다. */
    private String eliteMonsterName(int floor, HashMap<String, Object> mon, boolean elite) {
        String base = floorMonsterName(floor, mon);
        return elite ? "💪 강화 " + base : base;
    }

    // 장비 등급별 보너스 [투구고정,투구%, 무기고정,무기%, 갑옷고정,갑옷%], index0=★1
    private static final double[][] EQUIP_BONUS = {
        { 30, 0.05,   5, 0.05,   3, 0.05 },
        { 45, 0.07,   8, 0.07,   5, 0.07 },
        { 75, 0.10,  13, 0.10,   8, 0.10 },
        { 150, 0.15, 25, 0.15,  15, 0.15 },
        { 350, 0.22, 60, 0.22,  35, 0.22 },
        { 800, 0.35, 150, 0.35, 80, 0.35 },
    };

    // 주사위 해금 계단 [코드, 해금 UNLOCKED_BLOCK]
    private static final String[] DICE_NAMES = { "DICE_6", "DICE_8", "DICE_10", "DICE_12", "DICE_20" };
    private static final int[]    DICE_UNLOCK = { 0, 10, 30, 50, 70 };

    // 칸 종류 표시(아이콘+이름)
    private static final HashMap<String, String> TILE_LABEL = new HashMap<String, String>() {{
        put("COMBAT", "⚔️ 전투");  put("PP", "🍀 럭키");     put("TREASURE", "💎 보물상자");
        put("TRAP",   "🕳️ 함정");  put("SPECIAL", "✨ 특수"); put("STAIRS", "🪜 계단");
        put("ELITE",  "💪 강화몬스터");
    }};

    // 쿨타임(초) 3종. DB(TBOT_S5_CONFIG)에서 서버 기동 시(@PostConstruct) 로드해 메모리에
    // 캐싱하고, /갱신 명령어로 재조회해서 값을 갱신한다. DB 조회 실패 시엔 아래 기본값을 그대로
    // 사용(서버가 죽지 않도록 방어). 다음 액션에 어느 쿨타임이 적용될지는 "지금 상태"가 아니라
    // "방금 무슨 일이 있었는지"로 정해지므로(칸이동 vs 전투중 vs 막 전투가 끝남) 매 액션마다
    // NEXT_COOLDOWN_SEC에 값을 직접 저장해둔다(touchDiceCooldown 참고).
    //   - MOVE_COOLDOWN_SEC   : 칸이동(비전투) 후
    //   - COMBAT_COOLDOWN_SEC : 전투 중(몬스터가 아직 살아있어 다음 턴으로 이어짐) 후
    //   - COMBAT_END_COOLDOWN_SEC : 전투가 이번 액션으로 끝났을 때(처치 성공 또는 파티 전멸) 후
    private static volatile long MOVE_COOLDOWN_SEC       = 15;
    private static volatile long COMBAT_COOLDOWN_SEC     = 15;
    private static volatile long COMBAT_END_COOLDOWN_SEC = 100;

    /** 서버 기동 시 TBOT_S5_CONFIG를 읽어 메모리(static 필드)에 반영. 실패해도 기본값으로 계속 동작. */
    @PostConstruct
    public void loadConfig() {
        try {
            for (HashMap<String, Object> row : dao.selectAllConfig()) {
                String key = strVal(row.get("CONFIG_KEY"), "");
                String val = strVal(row.get("CONFIG_VALUE"), "");
                try {
                    if ("MOVE_COOLDOWN_SEC".equals(key)) {
                        MOVE_COOLDOWN_SEC = Long.parseLong(val);
                    } else if ("COMBAT_COOLDOWN_SEC".equals(key)) {
                        COMBAT_COOLDOWN_SEC = Long.parseLong(val);
                    } else if ("COMBAT_END_COOLDOWN_SEC".equals(key)) {
                        COMBAT_END_COOLDOWN_SEC = Long.parseLong(val);
                    }
                } catch (NumberFormatException ignore) {
                    // 파싱 실패한 값은 무시하고 기존(기본) 값 유지
                }
            }
        } catch (Exception ignore) {
            // 서버 기동 시점에 DB 접속이 안 되거나 테이블이 없어도 기본값으로 계속 기동
        }
    }

    /** /갱신 — TBOT_S5_CONFIG를 다시 읽어 메모리 값을 갱신 */
    @Override
    public String refreshConfig() {
        loadConfig();
        return "🗼 시즌5 설정 갱신 완료 (칸이동 " + MOVE_COOLDOWN_SEC + "초 / 전투중 " + COMBAT_COOLDOWN_SEC
                + "초 / 전투종료 " + COMBAT_END_COOLDOWN_SEC + "초)";
    }

    @Override
    public HashMap<String, Object> selectUserProgress(String userName) {
        return dao.selectUserProgress(userName);
    }

    @Override
    @Transactional
    public void initUser(String userName) {
        // 계정(진행상태)만 생성. 동료 지급은 유저가 /동료뽑기 를 직접 눌러야 진행되는
        // 튜토리얼 흐름으로 처리한다 (rollDice 참고).
        HashMap<String, Object> p = new HashMap<>();
        p.put("userName", userName);
        dao.insertUserProgress(p);
    }

    private HashMap<String, Object> getOrInitProgress(String userName) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) {
            initUser(userName);
            p = dao.selectUserProgress(userName);
        }
        settleAutoHunt(userName, p); // 결과 메시지는 /주사위 쪽에서만 사용 -- 여기선 부수효과(PP 지급 등)만 필요
        return p;
    }

    // ================================================================
    // 스탯 계산
    // ================================================================
    private int[] calcBaseStat(String job, int grade) {
        int[] base = GRADE_BASE[grade - 1];
        double[] mult = JOB_MULT.get(job);
        int hp  = (int) Math.round(base[0] * mult[0]);
        int atk = (int) Math.round(base[1] * mult[1]);
        int def = (int) Math.round(base[2] * mult[2]);
        return new int[]{ hp, atk, def };
    }

    /** 등급+직업 베이스 스탯에 장비/스탯구매 보너스를 반영한 최종 전투 스탯. [hp, atk, def, minDmgFloor] */
    private int[] computeEffectiveStat(String job, int grade, List<HashMap<String, Object>> equips, HashMap<String, Object> userStat) {
        int[] base = calcBaseStat(job, grade);
        double hp = base[0], atk = base[1], def = base[2];

        if (equips != null) {
            for (HashMap<String, Object> e : equips) {
                int eg = intVal(e.get("GRADE"), 1);
                double[] b = EQUIP_BONUS[eg - 1];
                String part = strVal(e.get("PART"), "");
                if ("HELMET".equals(part)) hp += b[0] + base[0] * b[1];
                else if ("WEAPON".equals(part)) atk += b[2] + base[1] * b[3];
                else if ("ARMOR".equals(part)) def += b[4] + base[2] * b[5];
            }
        }

        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int atkMinLv = userStat == null ? 0 : intVal(userStat.get("ATK_MIN_LV"), 0);
        int hpLv     = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        atk *= (1 + 0.03 * atkMaxLv);
        hp  *= (1 + 0.03 * hpLv);
        int minDmgFloor = atkMinLv * 2;

        return new int[]{ (int) Math.round(hp), (int) Math.round(atk), (int) Math.round(def), minDmgFloor };
    }

    private int diceMax(String diceGrade) {
        if (diceGrade == null) return 6;
        switch (diceGrade) {
            case "DICE_8":  return 8;
            case "DICE_10": return 10;
            case "DICE_12": return 12;
            case "DICE_20": return 20;
            default:        return 6;
        }
    }

    private int floorBlockBase(int floor) {
        return (floor / 10) * 10;
    }

    private int blockNo(int floor) {
        return (floorBlockBase(floor) / 10) + 1;
    }

    /**
     * 이 (유저,층) 보드가 아직 없으면(마을 갔다온 뒤 첫 진입 등) 새로 만든다. 칸 개수는
     * TBOT_S5_FLOOR_INFO.TILE_COUNT(층별 고정, 기존과 동일)를 그대로 쓰고 칸 "종류"만 매번
     * 새로 무작위 배정한다. 고정 개수 칸을 먼저 넣고(계단1, 히든 1~2, 보물상자1, 20층대+엔
     * 강화몹1) 나머지를 전투50%/함정10%/럭키40%로 채운 뒤 위치를 섞는다.
     */
    @Override
    public List<HashMap<String, Object>> ensureUserBoard(String userName, int floor) {
        List<HashMap<String, Object>> existing = dao.selectUserTileMaster(userName, floor);
        if (!existing.isEmpty()) return existing;

        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);

        List<String> types = new ArrayList<>();
        types.add("STAIRS");
        int specialCount = tileCount >= 20 ? 2 : 1;
        for (int i = 0; i < specialCount; i++) types.add("SPECIAL");
        types.add("TREASURE");
        if (blockNo(floor) >= 3) types.add("ELITE"); // 20층대(블록3)부터만 강화몹방 등장
        while (types.size() < tileCount) {
            int r = RND.nextInt(100);
            if (r < 50) types.add("COMBAT");
            else if (r < 60) types.add("TRAP");
            else types.add("PP"); // 럭키칸
        }
        if (types.size() > tileCount) types = types.subList(0, tileCount); // 초소형 보드 방어
        Collections.shuffle(types, RND);

        List<HashMap<String, Object>> tiles = new ArrayList<>();
        List<Map<String, Object>> batch = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            int tileNo = i + 1;
            HashMap<String, Object> row = new HashMap<>();
            row.put("TILE_NO", tileNo);
            row.put("TILE_TYPE", types.get(i));
            tiles.add(row);
            HashMap<String, Object> b = new HashMap<>();
            b.put("tileNo", tileNo);
            b.put("tileType", types.get(i));
            batch.add(b);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userName", userName);
        params.put("floor", floor);
        params.put("tiles", batch);
        dao.insertUserTileMasterBatch(params);
        return tiles;
    }

    /** 사냥터층(구간 내 1~8번째) PP 보상 배율: 1층 1.0배, 2층 1.1배 ... 8층 1.7배로 층마다 조금씩 차이. 보스/마을층은 1.0배. */
    private double floorPpMultiplier(int floor) {
        int pos = floor % 10;
        if (pos < 1 || pos > 8) return 1.0;
        return 1.0 + 0.1 * (pos - 1);
    }

    private int intVal(Object o, int def) {
        if (o == null) return def;
        return ((Number) o).intValue();
    }

    private String strVal(Object o, String def) {
        return o == null ? def : o.toString();
    }

    // ================================================================
    // /탑현황
    // ================================================================
    @Override
    public String towerStatus(String userName) {
        return towerStatus(userName, null);
    }

    /**
     * /탑현황 [닉네임] — 인자가 없으면 자기 자신, 있으면 다른 유저 조회.
     * 정확히 일치하는 유저가 없으면 앞부분이 일치하는 닉네임을 LIKE로 검색(시즌2~4의
     * selectS4UserSearch류와 동일 패턴). 다른 유저 조회는 조회만 할 뿐 그 유저의
     * getOrInitProgress()를 타지 않는다(자동사냥 정산 등 부수효과가 남의 조회로 트리거되지 않게).
     */
    @Override
    public String towerStatus(String userName, String targetQuery) {
        boolean isOther = targetQuery != null && !targetQuery.trim().isEmpty();
        String target = userName;

        if (isOther) {
            String q = targetQuery.trim();
            HashMap<String, Object> exact = dao.selectUserProgress(q);
            if (exact != null) {
                target = q;
            } else {
                // 앞부분 일치 검색(selectS5UserSearch)은 USER_NAME 오름차순 정렬이라 여러 명이
                // 걸리면 그중 가장 앞선(사전순 첫) 닉네임으로 조용히 바로 조회한다(후보 안내 없음).
                List<String> matches = dao.selectS5UserSearch(q);
                if (matches.isEmpty()) {
                    return "🔍 '" + q + "' 로 시작하는 유저를 찾을 수 없습니다.";
                }
                target = matches.get(0);
            }
        }

        HashMap<String, Object> p = isOther ? dao.selectUserProgress(target) : getOrInitProgress(target);
        if (p == null) {
            return "🔍 '" + target + "' 님의 탑 진행 정보를 찾을 수 없습니다.";
        }

        int floor = intVal(p.get("CUR_FLOOR"), 0);
        String status = strVal(p.get("STATUS"), "NORMAL");
        PP pp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));

        StringBuilder sb = new StringBuilder();
        sb.append(target).append(isOther ? "님의 탑 현황" : "님").append("," + NL);
        sb.append("현재 층: ").append(floor);
        sb.append(" (").append(floorKindLabel(floor)).append(")").append(NL);
        sb.append("보유 PP: ").append(pp.format()).append(NL);
        sb.append("상태: ").append(status).append(NL);

        if (floor % 10 >= 1 && floor % 10 <= 8) {
            HashMap<String, Object> fi = dao.selectFloorInfo(floor);
            HashMap<String, Object> ufp = dao.selectUserFloorProgress(target, floor);
            int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
            int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);
            sb.append("보드 위치: ").append(curTile).append(" / ").append(tileCount).append(NL);
        }
        sb.append("사용 주사위: ").append(strVal(p.get("DICE_GRADE"), "DICE_6")).append(NL);
        boolean autoHuntOn = "Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"));
        sb.append("자동사냥: ").append(autoHuntOn ? "ON" : "OFF");
        if (autoHuntOn) {
            HashMap<String, Object> log = dao.selectAutoHuntLog(target);
            int huntFloor = log == null ? floor : intVal(log.get("FLOOR"), floor);
            HashMap<String, Object> mon = dao.selectMonster(blockNo(huntFloor), "N");
            if (mon != null) {
                PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
                PP perHour = perKill.multiply(6 * floorPpMultiplier(huntFloor));
                sb.append(" (").append(huntFloor).append("층 기준, 미접속 시 시간당 약 ").append(perHour.format()).append(" PP)");
            }
        }
        sb.append(NL);
        // "자동사냥이 지금 층 기준으로 도는지" 헷갈린다는 신고로 추가 -- 이 층에서 몇 마리째인지
        // 항상 보여줘서, 10마리를 다 채워야 그 층 기준으로 (재)적용된다는 걸 명확히 한다.
        if (floor % 10 >= 1 && floor % 10 <= 8) {
            sb.append("이 층 처치: ").append(intVal(p.get("KILL_COUNT_CUR"), 0)).append("/10 (자동사냥 적용까지)").append(NL);
        }
        sb.append("누적 처치: ").append(intVal(p.get("TOTAL_KILL_COUNT"), 0)).append("마리").append(NL);
        sb.append(NL).append("🖥️ 웹으로 보기: ").append(towerViewLink(target)).append(NL);
        sb.append("👉 전체 명령어는 /탑도움말 을 입력해 확인하세요.");
        return sb.toString();
    }

    private String floorKindLabel(int floor) {
        int m = floor % 10;
        if (m == 0) return "마을";
        if (m == 9) return "보스층";
        return "사냥터";
    }

    /** 전투 로그 표시용 "★등급직업(이름)" 포맷. 예: ★3궁수(나나) */
    private String jobTag(int grade, String job, String name) {
        return "★" + grade + JOB_NAME.getOrDefault(job, "동료") + "(" + name + ")";
    }

    // ================================================================
    // /탑도움말, /탑명령어 — 웹(SPA) 탭에 있는 기능을 포함해 전체 명령어를 텍스트로 안내
    // ================================================================
    @Override
    public String help(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);

        StringBuilder sb = new StringBuilder();
        sb.append("📖 시즌5 탑 등반 — 전체 명령어 도움말").append(NL);
        sb.append("🖥️ 웹으로 모든 기능 보기: ").append(towerViewLink(userName)).append(NL);
        sb.append(NL);

        sb.append("[명령어 목록] (설명은 아래 참고)").append(NL);
        sb.append("/주사위 (/ㅈㅅㅇ, /ㅈ)").append(NL);
        sb.append("/층변경 N (/층이동 N)").append(NL);
        sb.append("/탑현황 [닉네임]").append(NL);
        sb.append("/파티편성 [N]").append(NL);
        sb.append("/동료가리기 N").append(NL);
        sb.append("/동료뽑기N [10]").append(NL);
        sb.append("/장비뽑기N [10]").append(NL);
        sb.append("/주사위구매 [N]").append(NL);
        sb.append("/스탯구매 [공격력|최소공격력|체력]").append(NL);
        sb.append("/장비목록").append(NL);
        sb.append("/장비장착 [N] [M]").append(NL);
        sb.append("/장비합성 N").append(NL);
        sb.append("/탑업적").append(NL);
        sb.append(NL);

        sb.append("[상세 설명]").append(NL);
        sb.append(NL);
        sb.append("[이동/전투] (웹 '보드' 탭)").append(NL);
        sb.append("/주사위 (/ㅈㅅㅇ, /ㅈ) : 보드 이동(평소) 또는 몬스터 공격(전투 중)").append(NL);
        sb.append("/층변경 N (/층이동 N) : 현재 구간 내 N번째 층으로 이동. N=0(마을)~9(보스), 전투 중이면 도망 처리").append(NL);
        sb.append("  ※ 계단(STAIRS) 칸을 밟으면 다음 층으로 갈 '자격'만 생기고, 실제 이동은 이 명령어를 입력해야 합니다.").append(NL);
        sb.append("  ⚠️ 사냥터층에서 0층(마을, /층변경 0)으로 가면 방금 있던 층의 탐사맵(보드 위치+발견기록)이 초기화됩니다. 원정 중엔 끝까지 밀고 올라가세요!").append(NL);
        sb.append("  ⚠️ 보스를 처치해 다음 10층 구간으로 넘어가면 이전 구간으로는 다시 내려갈 수 없습니다(과거 구간 복귀 불가, 편도 진행).").append(NL);
        sb.append("  👹 29층 이후 보스는 전투 시작 시 파티원 1명을 무시(그 동료는 이번 전투 내내 피해 0), 반격 턴마다 30% 확률로 다른 동료를 기절(다음 공격 1회 불가)시킵니다.").append(NL);
        sb.append("/탑현황 [닉네임] : 현재 층/보드 위치/PP/상태/자동사냥 조회. 닉네임을 붙이면 다른 유저 조회(앞부분만 입력해도 검색됨)").append(NL);
        sb.append(NL);

        sb.append("[동료] (웹 '파티' 탭) — 파티 편성/해제는 전투 중이 아니면 어디서든 가능").append(NL);
        sb.append("/파티편성 : 보유 동료 목록 + 파티 편성 현황 조회").append(NL);
        sb.append("/파티편성 N : 목록 N번째 동료를 파티에 편성/해제 (전투 중이 아니면 어디서든)").append(NL);
        sb.append("/동료가리기 N : 목록 N번째 동료를 /파티편성 텍스트 목록에서 숨김/숨김해제(웹 화면엔 항상 표시)").append(NL);
        sb.append(NL);

        sb.append("[상점] (웹 '상점' 탭) — 뽑기는 마을이 아니어도 아무 층에서나 가능").append(NL);
        sb.append("/동료뽑기N [10] : 아래 번호의 계약서로 동료 뽑기(뒤에 10을 붙이면 10연속), 스탯도 함께 표시. 번호 생략 시 1번").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("COMPANION", 999), unlocked));
        sb.append("/장비뽑기N [10] : 아래 번호의 보물상자로 장비 뽑기(뒤에 10을 붙이면 10연속), 스탯 보너스도 함께 표시. 번호 생략 시 1번").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("EQUIP", 999), unlocked));
        sb.append("/주사위구매 [N] : 해금된 주사위 목록 확인 / N번 장착").append(NL);
        sb.append("/스탯구매 [공격력|최소공격력|체력] : 스탯 강화 현황 확인 / 구매").append(NL);
        sb.append(NL);

        sb.append("[장비] (웹 '장비' 탭)").append(NL);
        sb.append("/장비목록 : 보유 장비 조회 (미착용은 번호 + 스탯 보너스, 착용중인 건 누가 끼고 있는지 표시)").append(NL);
        sb.append("/장비장착 [N] [M] : 인자 없이 입력하면 미착용 장비 번호·파티원 번호를 먼저 안내. N=미착용 장비 번호, M=파티원 번호(생략 시 같은 직업 자동탐색)").append(NL);
        sb.append("/장비합성 N : N번째 장비 포함 동일 직업/부위/등급 미착용 장비 3개를 상위 등급 1개로 합성 (★6 불가, /장비목록의 [미착용] 번호 기준)").append(NL);
        sb.append(NL);

        sb.append("[업적] (웹 '업적' 탭)").append(NL);
        sb.append("/탑업적 : 달성한 업적 이름만 조회").append(NL);
        return sb.toString();
    }

    /**
     * 가챠 목록을 "번호. 이름 (해금층~, 비용 PP) [잠김]" 형태로 나열 (해금 안 된 것도 잠금 표시로 함께 노출).
     * 표시번호(1부터, UNLOCK_FLOOR 순 = /탑도움말·/장비뽑기·/동료뽑기에서 쓰는 번호)와
     * 실제 GACHA_ID(DB PK, COMPANION 1~4 / EQUIP 5~8로 서로 다름)가 다르므로 매핑해서 보여준다.
     */
    private String gachaCatalogText(List<HashMap<String, Object>> list, int unlocked) {
        StringBuilder sb = new StringBuilder();
        int displayIdx = 1;
        for (HashMap<String, Object> g : list) {
            String name = strVal(g.get("GACHA_NAME"), "?");
            int unlockFloor = intVal(g.get("UNLOCK_FLOOR"), 0);
            PP cost = PP.of(((Number) g.get("COST_VALUE")).doubleValue(), strVal(g.get("COST_EXT"), ""));
            boolean isUnlocked = unlocked >= unlockFloor;
            sb.append("  ").append(displayIdx++).append(". ").append(name)
              .append(" (").append(unlockFloor).append("층~, ").append(cost.format()).append(" PP)")
              .append(isUnlocked ? "" : " 🔒잠김").append(NL);
        }
        return sb.toString();
    }

    /** /동료뽑기·/장비뽑기 N에서 쓰는 표시번호(1부터, UNLOCK_FLOOR 순)를 실제 GACHA_ID로 변환. 없으면 null. */
    private Integer resolveGachaId(String gachaType, int displayIdx) {
        List<HashMap<String, Object>> list = dao.selectGachaList(gachaType, 999);
        if (displayIdx < 1 || displayIdx > list.size()) return null;
        return intVal(list.get(displayIdx - 1).get("GACHA_ID"), 0);
    }

    // ================================================================
    // /주사위, /ㅈㅅㅇ
    // ================================================================
    @Override
    @Transactional
    public String rollDice(String userName) {
        boolean brandNew = dao.selectUserProgress(userName) == null;
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) {
            initUser(userName);
            p = dao.selectUserProgress(userName);
        }

        if (brandNew) {
            // 계정이 없던 유저의 첫 /주사위 → 계정만 생성. 진행은 튜토리얼 순서대로 유도.
            return "┌─────────────────┐" + NL
                    + "  🗼 시즌5 탑 등반기" + NL
                    + "└─────────────────┘" + NL
                    + "계정을 생성했습니다! 현재 0층 마을이에요." + NL
                    + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기 1)";
        }

        java.util.Date lastAction = (java.util.Date) p.get("LAST_DICE_ACTION_DATE");
        String autoHuntMsg = settleAutoHunt(userName, p);
        if (autoHuntMsg == null && lastAction != null
                && !"Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"))
                && (System.currentTimeMillis() - lastAction.getTime()) / 60000L >= 30) {
            autoHuntMsg = "🌙 오랜만이에요! 자동사냥 중인 층이 없습니다.";
        }

        String status = strVal(p.get("STATUS"), "NORMAL");
        String cooldownMsg = checkDiceCooldown(p);
        if (cooldownMsg != null) return prependAutoHunt(autoHuntMsg, cooldownMsg);

        String result = rollDiceInternal(userName, p, status);
        // 다음 액션에 적용될 쿨타임은 "이번에 무슨 일이 있었는지"로 결정된다 -- 방금 전투 중이었는데
        // 이번 액션으로 몬스터가 죽었거나(처치) 파티가 전멸해서 전투가 끝났으면(=CUR_MONSTER_ID가
        // 이제 없음) 더 긴 "전투종료" 쿨타임을, 아직 몬스터가 살아있어 전투가 이어지면 "전투중"
        // 쿨타임을, 애초에 전투가 아니었으면(칸이동) "칸이동" 쿨타임을 적용한다.
        long nextCooldownSec;
        if ("IN_COMBAT".equals(status)) {
            HashMap<String, Object> pAfter = dao.selectUserProgress(userName);
            boolean stillFighting = pAfter != null && pAfter.get("CUR_MONSTER_ID") != null;
            nextCooldownSec = stillFighting ? COMBAT_COOLDOWN_SEC : COMBAT_END_COOLDOWN_SEC;
        } else {
            nextCooldownSec = MOVE_COOLDOWN_SEC;
        }
        touchDiceCooldown(userName, nextCooldownSec);
        return prependAutoHunt(autoHuntMsg, result);
    }

    private String prependAutoHunt(String autoHuntMsg, String result) {
        return autoHuntMsg == null ? result : autoHuntMsg + NL + NL + result;
    }

    /** LAST_DICE_ACTION_DATE + NEXT_COOLDOWN_SEC 기준 쿨타임 검사. 아직 남았으면 안내 메시지, 통과면 null. */
    private String checkDiceCooldown(HashMap<String, Object> p) {
        if ("Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) return null; // 특정 유저만 쿨타임 면제(관리자가 직접 부여)
        java.util.Date last = (java.util.Date) p.get("LAST_DICE_ACTION_DATE");
        if (last == null) return null;
        long cooldownSec = intVal(p.get("NEXT_COOLDOWN_SEC"), (int) MOVE_COOLDOWN_SEC);
        long elapsedSec = (System.currentTimeMillis() - last.getTime()) / 1000;
        if (elapsedSec >= cooldownSec) return null;
        long remain = cooldownSec - elapsedSec;
        return "⏳ 아직 쿨타임입니다! " + remain + "초 후 다시 시도해주세요. (쿨타임 " + cooldownSec + "초)";
    }

    private void touchDiceCooldown(String userName, long nextCooldownSec) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("touchDiceCooldown", true);
        up.put("nextCooldownSec", nextCooldownSec);
        dao.updateUserProgress(up);
    }

    private String rollDiceInternal(String userName, HashMap<String, Object> p, String status) {
        int floor = intVal(p.get("CUR_FLOOR"), 0);

        if ("IN_COMBAT".equals(status)) {
            return resolveCombatTurn(userName, p, floor);
        }

        int m = floor % 10;
        if (m == 0) {
            if (floor == 0) {
                // 튜토리얼 진행 중(0층 마을) — 다음 단계를 순서대로 안내
                int companionCount = dao.countUserCompanions(userName);
                int partyCount = countPartySize(userName);
                if (companionCount == 0) {
                    return userName + "님," + NL + "🏘️ 0층 마을 — 아직 동료가 없습니다." + NL
                            + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기 1)";
                }
                if (partyCount == 0) {
                    return userName + "님," + NL + "🏘️ 0층 마을 — 동료는 있지만 파티가 비어있습니다." + NL
                            + "👉 /파티편성 N 으로 동료를 파티에 편성하세요! (/파티편성 목록은 /파티편성 으로 확인)";
                }
                return userName + "님," + NL + "🏘️ 0층 마을 — 파티 준비 완료!" + NL
                        + "👉 층이동 명령어로 1층 가세요! (/층변경 1)";
            }
            return userName + "님," + NL + "🏘️ 여기는 마을입니다. 웹 상점(" + TOWER_VIEW_URL + ")을 이용하거나 /층변경 N 으로 사냥터에 진입하세요. (전체 명령어는 /탑도움말)";
        }
        if (m == 9) {
            return startCombat(userName, p, floor, true, false);
        }

        // ── 사냥터 보드: 끝 없이 순환하는 루프. 계단(STAIRS) 칸을 밟으면 다음 층 이동
        //    "자격"만 얻고(MAX_FLOOR_REACHED 갱신), 실제 이동은 /층변경 N 을 직접 입력해야 한다. ──
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);
        HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
        int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);

        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));
        int roll = RND.nextInt(diceMax) + 1;
        int newTile = ((curTile + roll - 1) % tileCount) + 1;

        HashMap<String, Object> ufpSave = new HashMap<>();
        ufpSave.put("userName", userName);
        ufpSave.put("floor", floor);
        ufpSave.put("curTile", newTile);
        dao.upsertUserFloorProgress(ufpSave);

        int priorVisits = dao.selectTileVisitCount(userName, floor, newTile);
        dao.insertTileVisit(userName, floor, newTile);
        int visited = dao.countTileVisits(userName, floor);

        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님," + NL);
        sb.append("🎲 주사위 ").append(roll).append("! ").append(curTile).append(" → ").append(newTile).append("번 칸")
          .append(NL).append("🗺️ 탐사 현황: ").append(visited).append("/").append(tileCount).append("칸 발견");
        if (visited >= tileCount) {
            // 완전탐사 달성 즉시 (user,floor) 역대기록에 반영 -- 마을 복귀 전이라도 영구 보존
            HashMap<String, Object> best = snapshotFloorBest(userName, floor);
            if (grantAchievement(userName, 25)) {
                sb.append(NL).append("🏆 이 층을 전부 탐험했습니다! [탐험왕] 업적 달성!");
            }
            if ("Y".equals(strVal(best.get("NEWLY_FULL"), "N"))) {
                sb.append(NL).append("🏆 [").append(floor).append("층 완전탐사] 업적 달성! 동료뽑기권 1장 지급!");
            }
        }
        sb.append(NL);

        int trapTurnLeft = intVal(p.get("TRAP_TURN_LEFT"), 0);
        int luckyTurnLeft = intVal(p.get("LUCKY_TURN_LEFT"), 0);
        if (trapTurnLeft > 0 || luckyTurnLeft > 0) {
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            if (trapTurnLeft > 0) up.put("trapTurnLeft", trapTurnLeft - 1);
            if (luckyTurnLeft > 0) up.put("luckyTurnLeft", luckyTurnLeft - 1);
            dao.updateUserProgress(up);
        }

        List<HashMap<String, Object>> tiles = ensureUserBoard(userName, floor);
        String tileType = "COMBAT";
        for (HashMap<String, Object> t : tiles) {
            if (intVal(t.get("TILE_NO"), -1) == newTile) {
                tileType = strVal(t.get("TILE_TYPE"), "COMBAT");
                break;
            }
        }
        if (!"COMBAT".equals(tileType)) {
            sb.append(TILE_LABEL.getOrDefault(tileType, tileType)).append(" 칸!").append(NL);
        }

        // 히든(특수)/아이템획득(상점) 칸은 첫 방문에만 보상을 주고, 재방문(2회차부터)은 몬스터 전투로 전환.
        // 이걸 의미있게 만드는 짝: 마을로 돌아가면 그 층의 방문기록이 초기화되므로(changeFloor 참고)
        // "한 원정 안에서 같은 칸을 우려먹기"만 막고, 다음 원정에서 다시 새로 발견하는 건 자유.
        boolean revisitOverride = priorVisits >= 1 && ("SPECIAL".equals(tileType) || "TREASURE".equals(tileType));
        if (revisitOverride) {
            sb.append("(어라, 낯익은 자리인데...? 몬스터가 튀어나왔다!)").append(NL);
        }
        String effectiveType = revisitOverride ? "COMBAT" : tileType;

        switch (effectiveType) {
            case "COMBAT":
                sb.append(startCombat(userName, p, floor, false, false));
                break;
            case "PP": {
                // 럭키칸(칸 유형 값은 하위호환을 위해 기존 "PP" 그대로 두고 표시만 "🍀 럭키"로 바꿈,
                // 함정칸처럼 이로운 효과 4종 중 무작위 -- PP 보너스/회복은 즉시 발동, 공격력/방어력
                // 강화는 이후 3번의 보드 이동 동안 지속되는 파티 전체 버프(위 luckyTurnLeft 참고).
                HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
                PP basePp = mon == null ? PP.of(1, "")
                        : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(floorPpMultiplier(floor));
                // ATK_UP_10/ATK_UP_30/DEF_UP_10/DEF_UP_30: 접두어로 종류, 끝 숫자로 세기를 구분
                // (luckyEffectPct 참고) -- 강한 버프(30%)와 약한 버프(10%)를 같이 두어 매번 같은
                // 세기만 나오지 않도록 함. CLEANSE는 함정 디버프를 즉시 해제하는 효과(신규).
                String[] luckyEffects = { "PP_BONUS", "ATK_UP_30", "DEF_UP_30", "ATK_UP_10", "DEF_UP_10", "HEAL_ALL", "CLEANSE" };
                String luckyEffect = luckyEffects[RND.nextInt(luckyEffects.length)];
                if (luckyEffect.startsWith("ATK_UP") || luckyEffect.startsWith("DEF_UP")) {
                    // [버그 수정] 이미 럭키 버프가 남아있는 상태에서 새 버프를 뽑으면 컬럼이 하나뿐이라
                    // 무조건 새 걸로 덮어써지는데(연장이 아니라 3턴으로 리셋), 예전엔 이걸 아무 안내 없이
                    // 조용히 덮어써서 "버프가 하나만 적용되는 것 같다"는 혼란을 줬다. 규칙은 "새 효과로
                    // 갱신"으로 명확히 하고, 기존 효과를 밀어냈을 땐 그 사실을 문구로 분명히 알려준다.
                    int prevLuckyTurnLeft = intVal(p.get("LUCKY_TURN_LEFT"), 0);
                    String prevLuckyEffect = strVal(p.get("LUCKY_EFFECT"), "");
                    boolean overwrote = prevLuckyTurnLeft > 0 && !prevLuckyEffect.isEmpty() && !prevLuckyEffect.equals(luckyEffect);
                    HashMap<String, Object> up = new HashMap<>();
                    up.put("userName", userName);
                    up.put("luckyTurnLeft", 3);
                    up.put("luckyEffect", luckyEffect);
                    dao.updateUserProgress(up);
                    int pct = luckyEffectPct(luckyEffect);
                    String stat = luckyEffect.startsWith("ATK_UP") ? "공격력" : "방어력";
                    sb.append("🍀 럭키 칸! 앞으로 3번 이동하는 동안 파티 전원의 ").append(stat).append("이 ").append(pct).append("% 강화됩니다.");
                    if (overwrote) {
                        int prevPct = luckyEffectPct(prevLuckyEffect);
                        String prevStat = prevLuckyEffect.startsWith("ATK_UP") ? "공격력" : "방어력";
                        sb.append(NL).append("(기존 ").append(prevStat).append(" ").append(prevPct)
                          .append("% 강화 효과는 새 효과로 갱신되어 사라졌습니다)");
                    }
                } else if ("CLEANSE".equals(luckyEffect)) {
                    // 정화 -- 함정칸으로 걸린 공격력/방어력 약화 디버프를 즉시 해제(PP 손실 효과는 즉시
                    // 발동형이라 이미 끝난 뒤라 정화할 게 없음, 이 정화 자체가 새 디버프를 남기지도 않음).
                    int curTrapTurnLeft = intVal(p.get("TRAP_TURN_LEFT"), 0);
                    if (curTrapTurnLeft > 0) {
                        HashMap<String, Object> up = new HashMap<>();
                        up.put("userName", userName);
                        up.put("trapTurnLeft", 0);
                        dao.updateUserProgress(up);
                        sb.append("🍀 럭키 칸! 몸에 걸려있던 함정 효과가 말끔히 정화되었습니다.");
                    } else {
                        sb.append("🍀 럭키 칸! 정화의 기운을 느꼈지만... 딱히 없앨 디버프가 없어 효과가 없었다.");
                    }
                } else if ("HEAL_ALL".equals(luckyEffect)) {
                    // 완전회복 -- 전투불가(HP 0) 상태인 동료까지 포함해 파티 전원을 풀피로 채운다
                    // (전투 승리 후 자동회복은 살아있는 동료만 대상이라 이거랑 다름, healPartyAliveOnly 참고).
                    List<HashMap<String, Object>> healParty = new ArrayList<>();
                    for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                        if (c.get("PARTY_SLOT") != null) healParty.add(c);
                    }
                    if (healParty.isEmpty()) {
                        sb.append("🍀 럭키 칸! 몸이 개운해지는 기운을 느꼈지만... 파티가 비어있어 효과가 없었다.");
                    } else {
                        healPartyAll(healParty, dao.selectUserStat(userName));
                        sb.append("🍀 럭키 칸! 파티 전원의 체력이 완전히 회복되었습니다! (전투불가 상태였던 동료도 부활)");
                    }
                } else { // PP_BONUS -- 기존 PP칸 보상의 3배
                    PP reward = basePp.multiply(3);
                    addPp(userName, p, reward);
                    sb.append("🍀 럭키 칸! ").append(reward.format()).append(" PP 획득!");
                }
                break;
            }
            case "TRAP": {
                // 함정 효과 3종 중 무작위 -- 공격력/방어력 약화는 이후 3번의 보드 이동(위 trapTurnLeft
                // 감소 로직 기준) 동안 지속되는 파티 전체 디버프, PP 손실은 즉시 발동하는 1회성 효과.
                // 실제 적용은 resolveCombatTurn의 파티 공격 루프(ATK_DOWN)와 몬스터 반격 대상
                // 방어력 계산(DEF_DOWN)에서 이뤄진다.
                String[] effects = { "ATK_DOWN", "DEF_DOWN", "PP_LOSS" };
                String effect = effects[RND.nextInt(effects.length)];
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                if ("PP_LOSS".equals(effect)) {
                    PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
                    PP loss = cur.multiplyRate(0.05); // 보유 PP의 5% 손실
                    PP after = cur.subtract(loss);
                    if (PP.toBaseValue(after) < 0) after = PP.fromPP(0);
                    up.put("ppValue", after.getValue());
                    up.put("ppExt", after.getUnit());
                    dao.updateUserProgress(up);
                    p.put("PP_VALUE", after.getValue());
                    p.put("PP_EXT", after.getUnit());
                    sb.append("💸 함정에 걸려 소매치기를 당했다! PP ").append(loss.format())
                      .append(" 손실 (남은 PP ").append(after.format()).append(")");
                } else {
                    // [버그 수정] 럭키 버프와 동일한 문제 -- 이미 함정 디버프가 남아있는데 새 함정을
                    // 밟으면 조용히 덮어써졌다. 동일하게 "새 효과로 갱신" + 명시적 안내로 통일.
                    int prevTrapTurnLeft = intVal(p.get("TRAP_TURN_LEFT"), 0);
                    String prevTrapEffect = strVal(p.get("TRAP_EFFECT"), "");
                    boolean overwrote = prevTrapTurnLeft > 0 && !prevTrapEffect.isEmpty() && !prevTrapEffect.equals(effect);
                    up.put("trapTurnLeft", 3);
                    up.put("trapEffect", effect);
                    dao.updateUserProgress(up);
                    if ("ATK_DOWN".equals(effect)) {
                        sb.append("🕳️ 함정에 걸렸다! 앞으로 3번 이동하는 동안 파티 전원의 공격력이 30% 약화됩니다.");
                    } else {
                        sb.append("🕳️ 함정에 걸렸다! 앞으로 3번 이동하는 동안 파티 전원의 방어력이 30% 약화되어 반격 피해를 더 받습니다.");
                    }
                    if (overwrote) {
                        String prevKr = "ATK_DOWN".equals(prevTrapEffect) ? "공격력 약화" : "방어력 약화";
                        sb.append(NL).append("(기존 ").append(prevKr).append(" 효과는 새 효과로 갱신되어 사라졌습니다)");
                    }
                }
                break;
            }
            case "TREASURE": {
                // 보물상자방 -- 첫 방문에만 보상(아이템처럼 1회성), 재방문은 위 revisitOverride로
                // 전투 전환됨. 구 SHOP칸(상점)을 대체 -- 상점 개념 자체를 없애고 보상만 남김.
                boolean pp = RND.nextBoolean();
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                if (pp) {
                    HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
                    PP reward = mon == null ? PP.of(10, "")
                            : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(5 * floorPpMultiplier(floor));
                    addPp(userName, p, reward);
                    sb.append("💎 보물상자를 발견했다! ").append(reward.format()).append(" PP 획득!");
                } else {
                    boolean companionVoucher = RND.nextBoolean();
                    if (companionVoucher) {
                        up.put("companionVoucher", intVal(p.get("COMPANION_VOUCHER"), 0) + 1);
                    } else {
                        up.put("equipVoucher", intVal(p.get("EQUIP_VOUCHER"), 0) + 1);
                    }
                    sb.append("💎 보물상자를 발견했다! ").append(companionVoucher ? "동료" : "장비")
                      .append(" 무료뽑기 1회권 획득! (다음 ").append(companionVoucher ? "/동료뽑기" : "/장비뽑기")
                      .append(" 시 자동 적용)");
                }
                dao.updateUserProgress(up);
                break;
            }
            case "SPECIAL":
                sb.append(handleSpecialTile(userName));
                break;
            case "ELITE":
                sb.append(startCombat(userName, p, floor, false, true)); // 강화몹: 보스 아님, 강화만
                break;
            case "STAIRS": {
                // floor%10 in 1..8 이므로 다음 칸은 항상 같은 구간 내(최대 9층 보스).
                // 계단을 밟아도 즉시 층이동하지 않는다 -- MAX_FLOOR_REACHED만 갱신해서
                // "이 층까지는 계단으로 실제로 밟아봤다"는 자격만 얻고, 실제 이동은
                // 유저가 /층변경 N 을 직접 입력해야 이뤄진다(도착 시점의 업적/탐사 표시는
                // changeFloor 쪽에서 그대로 처리됨).
                int nextFloor = floor + 1;
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                up.put("maxFloorReached", nextFloor);
                dao.updateUserProgress(up);
                sb.append("🪜 계단을 발견했습니다! ").append(nextFloor).append("층으로 갈 수 있어요.").append(NL)
                  .append("👉 /층변경 ").append(nextFloor % 10).append(" 으로 이동하세요.");
                break;
            }
            default:
                sb.append("...아무 일도 일어나지 않았다.");
        }
        return sb.toString();
    }

    private int countPartySize(String userName) {
        int cnt = 0;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) cnt++;
        }
        return cnt;
    }

    private String handleSpecialTile(String userName) {
        dao.upsertSpecialVisitIncrement(userName);
        HashMap<String, Object> v = dao.selectUserSpecialVisit(userName);
        int cnt = v == null ? 1 : intVal(v.get("VISIT_COUNT"), 1);
        StringBuilder sb = new StringBuilder("✨ 수상한 기운이 감돌았지만... 이번엔 별다른 일이 일어나지 않았다. (특수칸 누적 방문 ").append(cnt).append("회)");
        int[] thresholds = { 10, 50, 100 };
        int[] achIds = { 17, 18, 19 };
        for (int i = 0; i < thresholds.length; i++) {
            if (cnt == thresholds[i]) {
                grantAchievement(userName, achIds[i]);
                sb.append(NL).append("🏆 히든 업적 달성!");
            }
        }
        return sb.toString();
    }

    private void addPp(String userName, HashMap<String, Object> p, PP amount) {
        PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        PP result = cur.add(amount);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("ppValue", result.getValue());
        up.put("ppExt", result.getUnit());
        dao.updateUserProgress(up);
        p.put("PP_VALUE", result.getValue());
        p.put("PP_EXT", result.getUnit());
    }

    private boolean deductPp(String userName, HashMap<String, Object> p, PP cost) {
        PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        if (!cur.canAfford(cost)) return false;
        PP result = cur.subtract(cost);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("ppValue", result.getValue());
        up.put("ppExt", result.getUnit());
        dao.updateUserProgress(up);
        p.put("PP_VALUE", result.getValue());
        p.put("PP_EXT", result.getUnit());
        return true;
    }

    /** 보유한 동료 무료뽑기권이 있으면 1장 소비하고 true, 없으면 false(비용 정상 차감 필요). */
    private boolean consumeCompanionVoucher(String userName, HashMap<String, Object> p) {
        int cur = intVal(p.get("COMPANION_VOUCHER"), 0);
        if (cur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("companionVoucher", cur - 1);
        dao.updateUserProgress(up);
        p.put("COMPANION_VOUCHER", cur - 1);
        return true;
    }

    /** 보유한 장비 무료뽑기권이 있으면 1장 소비하고 true, 없으면 false(비용 정상 차감 필요). */
    private boolean consumeEquipVoucher(String userName, HashMap<String, Object> p) {
        int cur = intVal(p.get("EQUIP_VOUCHER"), 0);
        if (cur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("equipVoucher", cur - 1);
        dao.updateUserProgress(up);
        p.put("EQUIP_VOUCHER", cur - 1);
        return true;
    }

    private String startCombat(String userName, HashMap<String, Object> p, int floor, boolean boss, boolean elite) {
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), boss ? "Y" : "N");
        if (mon == null) {
            // TBOT_S5_MONSTER_INFO에 이 BLOCK_NO×BOSS_YN 조합 데이터가 없는 경우.
            // 원인 확인용: S5_CHECK_MONSTER_DATA.sql
            return floor + "층(BLOCK " + blockNo(floor) + ") 몬스터 정보가 없습니다 (관리자 문의).";
        }
        // 강화몹(ELITE 칸, 20층대+ 전용): 같은 층 몬스터를 그대로 쓰되 HP/ATK/DEF/PP보상 전부 2배.
        // HP는 여기서 CUR_MONSTER_HP_VALUE에 곱한 값을 바로 저장해두면 끝이지만, ATK/DEF/보상은
        // 매 턴 mon에서 새로 읽어오므로(resolveCombatTurn) CUR_MONSTER_ELITE_YN 플래그를 남겨서
        // 거기서도 계속 2배를 적용하게 한다.
        double eliteMult = elite ? 2.0 : 1.0;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("status", "IN_COMBAT");
        up.put("curMonsterId", intVal(mon.get("MONSTER_ID"), 0));
        up.put("curMonsterHpValue", ((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult);
        up.put("curMonsterHpExt", strVal(mon.get("HP_EXT"), ""));
        up.put("curMonsterEliteYn", elite ? "Y" : "N");

        // 20층 이후(블록3+, 29층 보스부터) 보스는 전투 시작 시 파티원 1명을 무작위로 "안중에 없다"며
        // 지목 -- 그 동료는 이번 전투 내내 보스의 반격 피해를 0으로 받는다(스턴 스킬과는 별개 효과).
        String immuneMsg = "";
        boolean lateBoss = boss && blockNo(floor) >= 3;
        if (lateBoss) {
            List<HashMap<String, Object>> party = new ArrayList<>();
            for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                if (c.get("PARTY_SLOT") != null) party.add(c);
            }
            if (!party.isEmpty()) {
                HashMap<String, Object> chosen = party.get(RND.nextInt(party.size()));
                up.put("bossImmuneCid", intVal(chosen.get("COMPANION_ID"), 0));
                String cName = strVal(chosen.get("NAME"), JOB_NAME.getOrDefault(strVal(chosen.get("CLASS"), ""), "동료"));
                immuneMsg = NL + "👁️ 보스가 " + cName + "은(는) 안중에도 없다는 듯 무시한다... (이번 전투 동안 피해 0)";
            }
        }
        dao.updateUserProgress(up);

        String monName = (elite ? "💪 강화 " : "") + floorMonsterName(floor, mon);
        PP fullHp = PP.of(((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult, strVal(mon.get("HP_EXT"), "")).normalize();
        StringBuilder sb = new StringBuilder();
        sb.append(boss ? "👹 보스 " : elite ? "" : "⚔️ ").append(monName).append(" 등장! (HP ")
          .append(fullHp.format()).append("/").append(fullHp.format()).append(")").append(NL);
        // 공격력 범위(주사위 곱셈 전)만 보고는 몬스터 방어력이 빠지는 걸 몰라서 "왜 범위보다
        // 적게 들어갔지?" 헷갈릴 수 있어, 전투 시작 전에 몬스터 방어력/공격력과 지금 파티에
        // 걸려있는 공격력·방어력 강화/약화 효과(함정/럭키칸)를 미리 안내한다.
        sb.append("🛡️ 몬스터 방어력: ").append((int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult))
          .append(" (공격 시 이 값만큼 피해에서 차감) / ⚔️ 몬스터 공격력: ").append((int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult)).append(NL);
        if (elite) sb.append("💪 강화몹 -- 스탯/보상 전부 평소의 2배입니다.").append(NL);
        String buffNote = currentPartyBuffDebuffNote(p);
        if (buffNote != null) sb.append(buffNote).append(NL);
        sb.append(immuneMsg.isEmpty() ? "" : immuneMsg + NL);
        sb.append("전투를 시작하려면 다시 /주사위 를 입력하세요!");
        return sb.toString();
    }

    /** "ATK_UP_10"/"DEF_UP_30" 같은 럭키칸 효과 문자열에서 끝의 퍼센트 숫자만 뽑는다. 형식이 아니면 0. */
    private int luckyEffectPct(String luckyEffect) {
        if (luckyEffect == null) return 0;
        int idx = luckyEffect.lastIndexOf('_');
        if (idx < 0) return 0;
        try {
            return Integer.parseInt(luckyEffect.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 지금 파티에 걸려있는 함정/럭키칸 공격력·방어력 강화·약화 효과를 안내 문구로 (없으면 null).
     * [버그 수정] TRAP과 LUCKY는 서로 다른 컬럼 쌍(TRAP_TURN_LEFT/EFFECT, LUCKY_TURN_LEFT/EFFECT)이라
     * 실제 전투 계산(resolveCombatTurn)에서는 둘 다 동시에 적용되는데, 이 안내문은 원래 if-return
     * 체인이라 먼저 매치되는 것 하나만 보여주고 있었다(예: 함정 디버프가 걸린 채로 럭키 버프까지
     * 걸려도 함정 문구만 보임 -- "버프가 하나만 적용되는 것 같다"는 혼란의 원인). 이제 해당되는
     * 효과를 전부 모아서 줄바꿈으로 함께 보여준다.
     */
    private String currentPartyBuffDebuffNote(HashMap<String, Object> p) {
        int trapTurnLeft = intVal(p.get("TRAP_TURN_LEFT"), 0);
        String trapEffect = strVal(p.get("TRAP_EFFECT"), "");
        int luckyTurnLeft = intVal(p.get("LUCKY_TURN_LEFT"), 0);
        String luckyEffect = strVal(p.get("LUCKY_EFFECT"), "");
        List<String> notes = new ArrayList<>();
        if (trapTurnLeft > 0 && "ATK_DOWN".equals(trapEffect)) {
            notes.add("⚠️ 함정 효과로 파티 공격력 30% 약화 중 (남은 이동 " + trapTurnLeft + "회)");
        }
        if (trapTurnLeft > 0 && "DEF_DOWN".equals(trapEffect)) {
            notes.add("⚠️ 함정 효과로 파티 방어력 30% 약화 중, 반격 피해 증가 (남은 이동 " + trapTurnLeft + "회)");
        }
        if (luckyTurnLeft > 0 && luckyEffect.startsWith("ATK_UP")) {
            notes.add("🍀 럭키 효과로 파티 공격력 " + luckyEffectPct(luckyEffect) + "% 강화 중 (남은 이동 " + luckyTurnLeft + "회)");
        }
        if (luckyTurnLeft > 0 && luckyEffect.startsWith("DEF_UP")) {
            notes.add("🍀 럭키 효과로 파티 방어력 " + luckyEffectPct(luckyEffect) + "% 강화 중, 반격 피해 감소 (남은 이동 " + luckyTurnLeft + "회)");
        }
        return notes.isEmpty() ? null : String.join(NL, notes);
    }

    private String resolveCombatTurn(String userName, HashMap<String, Object> p, int floor) {
        int monsterId = intVal(p.get("CUR_MONSTER_ID"), 0);
        HashMap<String, Object> mon = findMonsterById(floor, monsterId);
        if (mon == null) {
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            dao.updateUserProgress(up);
            return "전투 정보를 찾을 수 없어 전투를 종료합니다.";
        }

        // 강화몹(ELITE 칸) 전투면 HP/ATK/DEF/PP보상 전부 2배 -- HP는 startCombat에서 이미 곱해서
        // CUR_MONSTER_HP_VALUE에 저장해뒀지만, ATK/DEF/보상은 mon에서 매 턴 새로 읽으므로 여기서도
        // 계속 곱해줘야 한다(안 그러면 시작할 땐 강화였는데 실제 전투 계산은 평소대로 되는 불일치 발생).
        boolean elite = "Y".equals(strVal(p.get("CUR_MONSTER_ELITE_YN"), "N"));
        double eliteMult = elite ? 2.0 : 1.0;
        PP monsterHp = PP.of(((Number) p.get("CUR_MONSTER_HP_VALUE")).doubleValue(), strVal(p.get("CUR_MONSTER_HP_EXT"), ""));
        PP monsterMaxHp = PP.of(((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult, strVal(mon.get("HP_EXT"), "")).normalize();
        int monsterDef = (int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult);
        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));

        // 함정칸 디버프 / 럭키칸 버프: 남은 이동횟수가 있는 동안 파티 전체에 적용. 럭키칸은
        // ATK_UP_10/ATK_UP_30처럼 세기가 다른 여러 등급이 있어서(luckyEffects 참고) 접두어로
        // 종류를, 끝의 숫자로 퍼센트를 판단한다(luckyEffectPct 참고). 함정은 항상 고정 30%.
        boolean trapAtkDown = intVal(p.get("TRAP_TURN_LEFT"), 0) > 0 && "ATK_DOWN".equals(strVal(p.get("TRAP_EFFECT"), ""));
        boolean trapDefDown = intVal(p.get("TRAP_TURN_LEFT"), 0) > 0 && "DEF_DOWN".equals(strVal(p.get("TRAP_EFFECT"), ""));
        String luckyEffectNow = strVal(p.get("LUCKY_EFFECT"), "");
        boolean luckyAtkUp = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && luckyEffectNow.startsWith("ATK_UP");
        boolean luckyDefUp = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && luckyEffectNow.startsWith("DEF_UP");
        double luckyMult = 1.0 + luckyEffectPct(luckyEffectNow) / 100.0;

        // 20층 이후(블록3+) 보스 전용 스킬: 무시(면역, startCombat에서 지정) + 기절(아래 반격 턴에서 확률 발동)
        boolean lateBoss = "Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 3;
        int bossImmuneCid = intVal(p.get("BOSS_IMMUNE_CID"), 0);

        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : companions) {
            if (c.get("PARTY_SLOT") != null) party.add(c);
        }
        if (party.isEmpty()) {
            return "파티에 편성된 동료가 없습니다. /파티편성 으로 동료를 편성하세요.";
        }

        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        StringBuilder sb = new StringBuilder(userName).append("님," + NL);

        // 보스 기절 스킬(반격 턴에 걸림, 아래 참고)로 지정된 동료는 이번 공격 턴만 건너뛰고 소모된다.
        int bossStunCid = intVal(p.get("BOSS_STUN_CID"), 0);
        boolean stunConsumed = false;

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 (직업별 특수효과 포함) ──
        long totalDamage = 0;
        boolean stunned = false;
        boolean executeKill = false;
        int shieldPool = 0;

        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) <= 0) continue; // 전투불가
            if (bossStunCid != 0 && bossStunCid == intVal(c.get("COMPANION_ID"), -1)) {
                String stunName = strVal(c.get("NAME"), JOB_NAME.getOrDefault(strVal(c.get("CLASS"), ""), "동료"));
                sb.append("💫 ").append(stunName).append("은(는) 기절 상태라 공격하지 못했다!").append(NL);
                stunConsumed = true;
                continue;
            }

            String job = strVal(c.get("CLASS"), "WARRIOR");
            String cName = strVal(c.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
            int grade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] eff = computeEffectiveStat(job, grade, equips, userStat);
            if (trapAtkDown) eff[1] = (int) Math.round(eff[1] * 0.7); // 함정: 공격력 30% 약화
            if (luckyAtkUp) eff[1] = (int) Math.round(eff[1] * luckyMult); // 럭키: 공격력 강화

            int roll = RND.nextInt(diceMax) + 1;
            int dmg = Math.max(1, eff[1] * roll - monsterDef);
            dmg = Math.max(dmg, eff[3]); // 스탯구매 최소공격력 보정
            totalDamage += dmg;
            sb.append(jobTag(grade, job, cName)).append(" (공격력 ").append(eff[1])
              .append(", 범위 ").append(eff[1]).append("~").append(eff[1] * diceMax)
              .append(") 공격! 🎲").append(roll)
              .append(" → ").append(dmg).append(" 데미지").append(NL);

            switch (job) {
                case "MAGE":
                    if (RND.nextInt(100) < 20) {
                        stunned = true;
                        sb.append("  ✨ 마법사의 스턴 적중! 몬스터가 이번 반격을 못합니다.").append(NL);
                    }
                    break;
                case "ROGUE":
                    if (RND.nextInt(100) < 25) {
                        PP steal = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(0.1 * floorPpMultiplier(floor) * eliteMult);
                        addPp(userName, p, steal);
                        sb.append("  🗡️ 도적이 ").append(steal.format()).append(" PP를 스틸했다!").append(NL);
                    }
                    break;
                case "ARCHER":
                    if (PP.toBaseValue(monsterHp) <= PP.toBaseValue(monsterMaxHp) * 0.1 && RND.nextInt(100) < 40) {
                        executeKill = true;
                        sb.append("  🏹 궁수의 즉사 사격 적중!").append(NL);
                    }
                    break;
                case "PRIEST": {
                    int shieldRoll = RND.nextInt(diceMax) + 1;
                    int shieldAmt = Math.max(0, eff[1] * shieldRoll);
                    shieldPool += shieldAmt;
                    sb.append("  🛡️ 도사가 보호막 ").append(shieldAmt).append(" 전개! (🎲").append(shieldRoll).append(")").append(NL);
                    break;
                }
                default:
                    break;
            }
        }

        PP monsterHpAfter = executeKill ? PP.fromPP(0) : monsterHp.subtract(PP.fromPP(totalDamage));
        boolean monsterDead = executeKill || PP.toBaseValue(monsterHpAfter) <= 0;

        if (monsterDead) {
            PP reward = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(floorPpMultiplier(floor) * eliteMult);
            boolean isBoss = "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
            int killCountCur = intVal(p.get("KILL_COUNT_CUR"), 0) + 1;
            int totalKill = intVal(p.get("TOTAL_KILL_COUNT"), 0) + 1;

            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            up.put("totalKillCount", totalKill);

            sb.append(eliteMonsterName(floor, mon, elite)).append(" 처치! 🎉").append(NL);

            if (isBoss) {
                int prevBlockBase = floorBlockBase(floor);
                int nextFloor = prevBlockBase + 10;
                up.put("curFloor", nextFloor);
                up.put("unlockedBlock", prevBlockBase + 10);
                // 새 구간의 첫 사냥터층은 계단 없이도 바로 층변경 가능해야 함
                up.put("maxFloorReached", nextFloor + 1);
                up.put("killCountCur", 0); // 새 구간으로 넘어가므로 "이 층 처치수"도 초기화(changeFloor와 동일 이유)
                sb.append("👑 보스 격파! ").append(nextFloor).append("층 마을로 이동합니다.").append(NL);
                grantAchievement(userName, 7);
                // 보스 처치로 다음 구간으로 넘어가면 이전 구간(방금 클리어한 사냥터 8개층)은
                // 어차피 재진입 불가(과거 구간 복귀 불가 규칙) -- 그 구간의 보드 위치/발견기록도
                // 함께 초기화한다. (마을로만 돌아갔을 때 그 층 하나만 지우는 것과 별개 케이스)
                resetBlockExploration(userName, prevBlockBase);
                // 새 구간 마을에 도착하는 셈이므로(changeFloor의 마을 도착 부활과 동일 이유),
                // 이 보스전에서 전투불가가 된 동료가 있으면 여기서 부활시킨다.
                int revivedOnBossClear = revivePartyDead(userName, party, userStat);
                if (revivedOnBossClear > 0) {
                    sb.append("✨ 전투불가 상태였던 동료 ").append(revivedOnBossClear).append("명이 마을에서 부활했습니다!").append(NL);
                }
            } else {
                up.put("killCountCur", killCountCur >= 10 ? 0 : killCountCur);
                if (killCountCur >= 10) {
                    up.put("autoHuntYn", "Y");
                    HashMap<String, Object> log = new HashMap<>();
                    log.put("userName", userName);
                    log.put("floor", floor);
                    dao.upsertAutoHuntLog(log);
                    sb.append("🔥 이 층에서 10마리 처치! 자동사냥 모드 ON (다음 접속 시 경과시간만큼 자동 정산)").append(NL);
                } else {
                    // "자동사냥이 지금 층 기준으로 잘 돌고 있는지" 헷갈린다는 신고로 추가 -- 몇 마리째인지
                    // 매 처치마다 보여줘서 진행 상황을 항상 알 수 있게 함(/탑현황에서도 동일하게 표시).
                    sb.append("(이 층 처치: ").append(killCountCur).append("/10 -- 자동사냥 적용까지)").append(NL);
                }
            }
            dao.updateUserProgress(up);
            addPp(userName, p, reward);
            checkKillAchievements(userName, totalKill);
            sb.append(reward.format()).append(" PP 획득!");
            // 승리하면 살아있는 동료는 자동으로 풀피 회복되지만, 전투불가(HP 0)가 된 동료는
            // 그대로 둔다 -- 부활은 마을 도착이나 럭키칸의 "완전회복" 효과로만 일어난다.
            healPartyAliveOnly(party, userStat);
            return sb.toString();
        }

        // 몬스터 생존 → 반격 (스턴이면 생략)
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curMonsterHpValue", monsterHpAfter.getValue());
        up.put("curMonsterHpExt", monsterHpAfter.getUnit());
        if (stunConsumed) up.put("clearBossStun", true);
        dao.updateUserProgress(up);
        sb.append(eliteMonsterName(floor, mon, elite)).append(" 남은 HP: ")
          .append(monsterHpAfter.format()).append("/").append(monsterMaxHp.format()).append(NL);

        if (stunned) {
            sb.append("몬스터가 스턴에 걸려 반격하지 못했습니다!");
            return sb.toString();
        }

        List<HashMap<String, Object>> alive = new ArrayList<>();
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) > 0) alive.add(c);
        }

        if (alive.isEmpty()) {
            HashMap<String, Object> defeatUp = new HashMap<>();
            defeatUp.put("userName", userName);
            defeatUp.put("status", "NORMAL");
            defeatUp.put("clearMonster", true);
            dao.updateUserProgress(defeatUp);
            // [변경] 예전엔 여기서 바로 풀피로 되돌렸는데, 그러면 안내 문구("마을에서 회복")가
            // 거짓말이 됨. 이제 정말로 마을에 돌아가야(changeFloor) 부활한다.
            sb.append("💀 파티 전멸... 전투에 패배했습니다. 동료들이 전투불가 상태로 남습니다 -- 마을로 돌아가야 부활합니다.");
            return sb.toString();
        }

        HashMap<String, Object> target = alive.get(RND.nextInt(alive.size()));

        // 전사 도발: 체력 50% 이상인 전사가 있으면 확률적으로 자신이 대신 맞음
        for (HashMap<String, Object> c : alive) {
            if (!"WARRIOR".equals(strVal(c.get("CLASS"), ""))) continue;
            int wGrade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> wEquips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] wEff = computeEffectiveStat("WARRIOR", wGrade, wEquips, userStat);
            PP wHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean over50 = PP.toBaseValue(wHp) * 2 >= wEff[0];
            if (over50 && !c.equals(target) && RND.nextInt(100) < 30) {
                target = c;
                sb.append("🛡️ 전사가 대신 공격을 받아냅니다!").append(NL);
            }
            break; // 파티엔 전사가 최대 1명이라고 가정하지 않지만, 첫 전사만 판정
        }

        // 20층 이후 보스의 기절 스킬: 30% 확률로 이번 반격 턴을 통째로 써서 대상을 기절시킴(피해 없음,
        // 다음 파티 공격 턴 1회를 건너뛰게 됨 -- 위 party 루프의 bossStunCid 체크에서 소모됨).
        if (lateBoss && RND.nextInt(100) < 30) {
            HashMap<String, Object> stunUp = new HashMap<>();
            stunUp.put("userName", userName);
            stunUp.put("bossStunCid", intVal(target.get("COMPANION_ID"), 0));
            dao.updateUserProgress(stunUp);
            String stunTargetName = strVal(target.get("NAME"), JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "동료"));
            sb.append("💫 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) ").append(stunTargetName)
              .append("을(를) 기절시켰다! 다음 턴 공격 불가");
            return sb.toString();
        }

        String tJob = strVal(target.get("CLASS"), "WARRIOR");
        int tGrade = intVal(target.get("GRADE"), 1);
        List<HashMap<String, Object>> tEquips = dao.selectEquipByCompanion(intVal(target.get("COMPANION_ID"), 0));
        int[] tEff = computeEffectiveStat(tJob, tGrade, tEquips, userStat);
        if (trapDefDown) tEff[2] = (int) Math.round(tEff[2] * 0.7); // 함정: 방어력 30% 약화(반격 피해 증가)
        if (luckyDefUp) tEff[2] = (int) Math.round(tEff[2] * luckyMult); // 럭키: 방어력 강화(반격 피해 감소)
        int monsterAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult);
        int roll = RND.nextInt(diceMax) + 1;
        int dmgToParty = Math.max(1, monsterAtk * roll - tEff[2]);

        if (shieldPool > 0) {
            int absorbed = Math.min(shieldPool, dmgToParty);
            dmgToParty -= absorbed;
            sb.append("🛡️ 보호막이 ").append(absorbed).append(" 피해를 흡수했습니다!").append(NL);
        }

        boolean immune = bossImmuneCid != 0 && bossImmuneCid == intVal(target.get("COMPANION_ID"), -1);
        if (immune) {
            dmgToParty = 0;
            sb.append("👁️ 보스가 무시하던 동료라 피해가 없다!").append(NL);
        }

        PP targetHp = PP.of(((Number) target.get("CUR_HP_VALUE")).doubleValue(), strVal(target.get("CUR_HP_EXT"), ""));
        PP targetHpAfter = targetHp.subtract(PP.fromPP(dmgToParty));
        if (PP.toBaseValue(targetHpAfter) < 0) targetHpAfter = PP.fromPP(0);

        HashMap<String, Object> cUp = new HashMap<>();
        cUp.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        cUp.put("curHpValue", targetHpAfter.getValue());
        cUp.put("curHpExt", targetHpAfter.getUnit());
        dao.updateCompanionHp(cUp);

        String tName = strVal(target.get("NAME"), JOB_NAME.getOrDefault(tJob, "동료"));
        sb.append(eliteMonsterName(floor, mon, elite)).append(" 반격! 🎲").append(roll).append(" → ")
          .append(jobTag(tGrade, tJob, tName)).append("에게 ")
          .append(dmgToParty).append(" 피해 (남은 HP ").append(targetHpAfter.format()).append(")");
        if (PP.toBaseValue(targetHpAfter) <= 0) sb.append(" — 전투불가!");

        return sb.toString();
    }

    /**
     * 전투불가(HP 0)인 동료만 최대 HP로 되살린다("부활") -- 이미 살아있는 동료의 HP는 건드리지
     * 않는다. 마을 도착(changeFloor)과 보스 처치로 새 구간 마을에 자동 도착할 때 쓰인다.
     * @return 실제로 되살아난 동료 수
     */
    private int revivePartyDead(String userName, List<HashMap<String, Object>> party, HashMap<String, Object> userStat) {
        int revived = 0;
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) > 0) continue; // 이미 살아있으면 그대로 둠
            setCompanionFullHp(c, userStat);
            revived++;
        }
        return revived;
    }

    /**
     * 전투 승리 후 자동 회복 -- 살아있는(HP&gt;0) 동료만 풀피로 채우고, 전투불가(HP 0)가 된
     * 동료는 그대로 둔다(부활은 마을 도착/럭키칸에서만 일어남).
     */
    private void healPartyAliveOnly(List<HashMap<String, Object>> party, HashMap<String, Object> userStat) {
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) <= 0) continue; // 전투불가 상태는 그대로 둠(부활 아님)
            setCompanionFullHp(c, userStat);
        }
    }

    /** 럭키칸 "완전회복" 전용 -- 전투불가 상태였던 동료까지 포함해 파티 전원을 풀피로 채운다. */
    private void healPartyAll(List<HashMap<String, Object>> party, HashMap<String, Object> userStat) {
        for (HashMap<String, Object> c : party) {
            setCompanionFullHp(c, userStat);
        }
    }

    private void setCompanionFullHp(HashMap<String, Object> c, HashMap<String, Object> userStat) {
        String job = strVal(c.get("CLASS"), "WARRIOR");
        int grade = intVal(c.get("GRADE"), 1);
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
        int[] eff = computeEffectiveStat(job, grade, equips, userStat);
        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(c.get("COMPANION_ID"), 0));
        up.put("curHpValue", (double) eff[0]);
        up.put("curHpExt", "");
        dao.updateCompanionHp(up);
    }

    private HashMap<String, Object> findMonsterById(int floor, int monsterId) {
        HashMap<String, Object> normal = dao.selectMonster(blockNo(floor), "N");
        if (normal != null && intVal(normal.get("MONSTER_ID"), -1) == monsterId) return normal;
        HashMap<String, Object> boss = dao.selectMonster(blockNo(floor), "Y");
        if (boss != null && intVal(boss.get("MONSTER_ID"), -1) == monsterId) return boss;
        return null;
    }

    private void checkKillAchievements(String userName, int totalKill) {
        if (totalKill == 100) grantAchievement(userName, 8);
        if (totalKill == 1000) grantAchievement(userName, 9);
    }

    /** @return 이번에 새로 달성되었으면 true, 이미 달성된 상태였으면 false */
    private boolean grantAchievement(String userName, int achId) {
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(userName);
        for (HashMap<String, Object> a : mine) {
            if (intVal(a.get("ACH_ID"), -1) == achId) return false;
        }
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", userName);
        m.put("achId", achId);
        dao.insertUserAch(m);
        return true;
    }

    // ================================================================
    // 자동사냥 정산
    // ================================================================
    /** 자동사냥 정산. 실제로 정산된 게 있으면 안내 메시지를 반환(없으면 null) — /주사위 응답 맨 위에 붙여준다. */
    private String settleAutoHunt(String userName, HashMap<String, Object> p) {
        if (!"Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"))) return null;
        HashMap<String, Object> log = dao.selectAutoHuntLog(userName);
        if (log == null) return null;

        java.util.Date lastSettle = (java.util.Date) log.get("LAST_SETTLE_DATE");
        if (lastSettle == null) return null;
        long elapsedMin = (System.currentTimeMillis() - lastSettle.getTime()) / 60000L;
        if (elapsedMin < 10) return null; // 10분(=처치 1회 기준) 미만이면 정산할 게 없음

        long cappedMin = Math.min(elapsedMin, 8 * 60L); // 최대 8시간
        int floor = intVal(log.get("FLOOR"), intVal(p.get("CUR_FLOOR"), 1));
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
        if (mon == null) return null;

        long kills = cappedMin / 10; // 시간당 6마리 = 10분당 1마리
        if (kills <= 0) return null;

        PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
        PP reward = perKill.multiply(kills * floorPpMultiplier(floor));
        addPp(userName, p, reward);

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("totalKillCount", intVal(p.get("TOTAL_KILL_COUNT"), 0) + (int) kills);
        dao.updateUserProgress(up);
        p.put("TOTAL_KILL_COUNT", intVal(p.get("TOTAL_KILL_COUNT"), 0) + (int) kills);

        HashMap<String, Object> logUp = new HashMap<>();
        logUp.put("userName", userName);
        logUp.put("floor", floor);
        dao.upsertAutoHuntLog(logUp); // LAST_SETTLE_DATE = SYSDATE 로 갱신

        checkKillAchievements(userName, intVal(p.get("TOTAL_KILL_COUNT"), 0));
        // TODO: AUTO_HUNT_PP_TOTAL 누적치 업적(14번)은 별도 누적 컬럼이 없어 아직 미체크

        return "💤 자동사냥 정산: " + floor + "층에서 " + kills + "마리 처치, " + reward.format() + " PP 획득!";
    }

    // ================================================================
    // /층변경 N
    // ================================================================
    @Override
    @Transactional
    public String changeFloor(String userName, int n) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (n < 0 || n > 9) {
            return "층변경은 0~9 범위만 가능합니다. (같은 10층 구간 내 이동)";
        }
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        boolean wasInCombat = "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"));
        int target = floorBlockBase(floor) + n;
        int villageFloor = floorBlockBase(floor);
        boolean alwaysFree = (target == villageFloor) || (target == villageFloor + 1); // 마을↔첫 사냥터층은 항상 자유 이동
        int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        if (!alwaysFree && target > maxReached) {
            return "🪜 " + target + "층은 아직 가본 적이 없습니다." + NL
                    + "계단을 통해 한 번은 직접 올라가야 다음부턴 층변경으로 오갈 수 있어요.";
        }

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        if (wasInCombat) {
            // 전투 중 층 이동 = 도망. 진행 중이던 전투를 포기하고 상태를 되돌린다.
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
        }
        // [버그 수정] KILL_COUNT_CUR("이 층에서 몇 마리 잡았는지")가 층이 바뀌어도 초기화되지
        // 않아서, 예전 층에서 쌓인 처치수가 다음 층까지 이어져 엉뚱하게 10마리를 채우고
        // 자동사냥이 켜지는 문제가 있었다(예: 이전 층 9마리 + 새 층 1마리 = 10). 층이 실제로
        // 바뀔 때는 항상 0으로 리셋해서 "이 층 도착 이후 처치수"만 세도록 한다.
        if (target != floor) {
            up.put("killCountCur", 0);
        }
        dao.updateUserProgress(up);

        // [설계 변경] 자동사냥이 이미 켜져 있으면(AUTO_HUNT_YN='Y') 정산 기준 층도 "지금 있는 층"으로
        // 바로 맞춰준다. 예전엔 그 층에서 10마리를 다시 채워야만(killCountCur 10 도달 시점에만)
        // AUTO_HUNT_LOG.FLOOR가 갱신돼서, 새 층으로 올라가 놀기만 해도(재도전 없이는) 자동사냥이
        // 계속 예전 층 기준으로 도는 것처럼 보이는 혼란이 있었다(신고로 확인). 사냥터층으로 실제
        // 이동할 때마다 즉시 동기화해서 "자동사냥 = 지금 층 기준"을 항상 유지한다.
        int targetFm = target % 10;
        if (target != floor && targetFm >= 1 && targetFm <= 8 && "Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"))) {
            HashMap<String, Object> logUp = new HashMap<>();
            logUp.put("userName", userName);
            logUp.put("floor", target);
            dao.upsertAutoHuntLog(logUp);
        }

        if (target != floor) {
            grantFloorAchievements(userName, target);
        }

        // 사냥터층에서 마을로 돌아가면 그 층의 원정(보드 위치+발견기록)을 초기화한다.
        // 업적(탐험왕 등)을 자유롭게 파밍하지 못하게 하려는 의도 -- 한 원정 안에서 끝까지 밀어야 함.
        int fm = floor % 10;
        boolean returnedToVillage = target % 10 == 0 && fm >= 1 && fm <= 8 && floor != target;
        HashMap<String, Object> floorBest = null;
        if (returnedToVillage) {
            floorBest = snapshotFloorBest(userName, floor);
            dao.deleteUserFloorProgress(userName, floor);
            dao.deleteTileVisits(userName, floor);
            dao.deleteUserTileMaster(userName, floor); // 마을 귀환 시 보드 재생성(유저별 맵 삭제 → 다음 진입 때 ensureUserBoard가 새로 생성)
        }

        // 마을(X0층) 도착 시 전투불가(HP 0) 상태였던 파티원을 부활시킨다 -- 전투 승리/패배로는
        // 더 이상 자동으로 되살아나지 않으므로, 부활은 이 경로(또는 럭키칸)로만 일어난다.
        boolean arrivedAtVillage = target % 10 == 0 && floor != target;
        int revivedCount = 0;
        if (arrivedAtVillage) {
            List<HashMap<String, Object>> villageParty = new ArrayList<>();
            for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                if (c.get("PARTY_SLOT") != null) villageParty.add(c);
            }
            revivedCount = revivePartyDead(userName, villageParty, dao.selectUserStat(userName));
        }

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);
        if (wasInCombat) {
            sb.append("💨 전투에서 도망쳤습니다!").append(NL);
        }
        sb.append(floor).append("층 → ").append(target).append("층(").append(floorKindLabel(target)).append(")으로 이동했습니다.");
        if (returnedToVillage) {
            sb.append(NL).append("⚠️ ").append(floor).append("층의 탐사 진행도가 초기화되었습니다. (다시 가면 처음부터)");
            if (floorBest != null) {
                int bestVisited = intVal(floorBest.get("BEST_VISITED_COUNT"), 0);
                int bestTileCount = intVal(floorBest.get("TILE_COUNT"), 0);
                int pct = bestTileCount > 0 ? (bestVisited * 100 / bestTileCount) : 0;
                sb.append(NL).append("📊 이 층 역대 최고 탐사 기록: ").append(bestVisited).append("/").append(bestTileCount)
                  .append("칸 (").append(pct).append("%)");
                if ("Y".equals(strVal(floorBest.get("FULLY_EXPLORED_YN"), "N"))) {
                    sb.append(" ✅ 완전탐사 기록 보유(계속 유지됨)");
                }
                if ("Y".equals(strVal(floorBest.get("NEWLY_FULL"), "N"))) {
                    sb.append(NL).append("🏆 [").append(floor).append("층 완전탐사] 업적 달성! 동료뽑기권 1장 지급!");
                }
            }
        }
        if (revivedCount > 0) {
            sb.append(NL).append("✨ 전투불가 상태였던 동료 ").append(revivedCount).append("명이 마을에서 부활했습니다!");
        }

        int tm = target % 10;
        if (tm >= 1 && tm <= 8) {
            HashMap<String, Object> fi = dao.selectFloorInfo(target);
            int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
            int visited = dao.countTileVisits(userName, target);
            sb.append(NL).append("🗺️ 이 층 탐사 현황: ").append(visited).append("/").append(tileCount).append("칸 발견");
        }
        if (target == 1 && intVal(p.get("TOTAL_KILL_COUNT"), 0) == 0) {
            sb.append(NL).append("1층에서 주사위를 굴려 전투하세요! (/주사위)");
        }
        return sb.toString();
    }

    private void grantFloorAchievements(String userName, int floor) {
        if (floor == 1) grantAchievement(userName, 1);
        if (floor == 10) grantAchievement(userName, 2);
        if (floor == 30) grantAchievement(userName, 3);
        if (floor == 50) grantAchievement(userName, 4);
        if (floor == 70) grantAchievement(userName, 5);
        if (floor == 100) grantAchievement(userName, 6);
    }

    /** blockBase(예: 0,10,20…) 구간의 사냥터층(blockBase+1 ~ blockBase+8) 전체의 보드 위치/발견기록을 초기화. */
    private void resetBlockExploration(String userName, int blockBase) {
        for (int f = blockBase + 1; f <= blockBase + 8; f++) {
            snapshotFloorBest(userName, f);
            dao.deleteUserFloorProgress(userName, f);
            dao.deleteTileVisits(userName, f);
            dao.deleteUserTileMaster(userName, f);
        }
    }

    /**
     * 방문기록이 지워지기 전, 이번 원정의 발견 칸 수를 (user, floor) 역대 최고기록에 반영하고 갱신된 행을 반환.
     * 이번 호출로 처음 100% 완전탐사가 되었으면 "N층 완전탐사" 업적(ID 100+floor)을 주고 동료뽑기권 1장을 지급하며,
     * 반환하는 맵에 NEWLY_FULL="Y" 를 얹어 호출부가 보상 안내 문구를 붙일 수 있게 한다.
     */
    private HashMap<String, Object> snapshotFloorBest(String userName, int floor) {
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
        int visited = dao.countTileVisits(userName, floor);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("floor", floor);
        up.put("visited", visited);
        up.put("tileCount", tileCount);
        dao.upsertUserFloorBest(up);
        HashMap<String, Object> row = dao.selectUserFloorBest(userName, floor);
        row.put("NEWLY_FULL", "N");
        if ("Y".equals(strVal(row.get("FULLY_EXPLORED_YN"), "N")) && grantAchievement(userName, 100 + floor)) {
            row.put("NEWLY_FULL", "Y");
            HashMap<String, Object> prog = dao.selectUserProgress(userName);
            if (prog != null) {
                HashMap<String, Object> voucherUp = new HashMap<>();
                voucherUp.put("userName", userName);
                voucherUp.put("companionVoucher", intVal(prog.get("COMPANION_VOUCHER"), 0) + 1);
                dao.updateUserProgress(voucherUp);
            }
        }
        return row;
    }

    // ================================================================
    // /파티편성
    // ================================================================
    @Override
    public String partyList(String userName) {
        getOrInitProgress(userName);
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (companions.isEmpty()) {
            return "보유한 동료가 없습니다.";
        }
        StringBuilder sb = new StringBuilder(userName).append("님의 동료 목록," + NL);
        int idx = 1;
        int hiddenCount = 0;
        for (HashMap<String, Object> c : companions) {
            // 숨김 처리된 동료는 번호(인덱스)만 소비하고 텍스트 목록엔 표시하지 않는다.
            // (인덱스는 항상 전체 목록 기준 위치라서 /파티편성 N, /동료가리기 N 모두 같은 번호를 가리킴)
            if ("Y".equals(strVal(c.get("HIDDEN_YN"), "N"))) { idx++; hiddenCount++; continue; }
            String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "?");
            String name = strVal(c.get("NAME"), job); // 이름 없는 옛 데이터는 직업명으로 대체 표시
            int grade = intVal(c.get("GRADE"), 1);
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            Object slot = c.get("PARTY_SLOT");
            sb.append(idx++).append(". ").append(name).append(" (").append(job).append(" ★").append(grade).append(")")
              .append(" HP ").append(hp.format())
              .append(slot != null ? " [파티 " + slot + "번]" : " [대기]")
              .append(NL);
        }
        if (hiddenCount > 0) sb.append("(숨긴 동료 ").append(hiddenCount).append("마리는 표시 생략, 웹에서 확인)").append(NL);
        sb.append("/파티편성 N 으로 편성/해제 (최대 3명), /동료가리기 N 으로 목록 숨김/해제");
        return sb.toString();
    }

    @Override
    @Transactional
    public String toggleCompanionHidden(String userName, int idx) {
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (idx < 1 || idx > companions.size()) {
            return "잘못된 번호입니다. /파티편성 으로 목록을 확인하세요.";
        }
        HashMap<String, Object> target = companions.get(idx - 1);
        boolean nowHidden = !"Y".equals(strVal(target.get("HIDDEN_YN"), "N"));
        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        up.put("hiddenYn", nowHidden ? "Y" : "N");
        dao.updateCompanionHidden(up);
        String name = strVal(target.get("NAME"), JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "동료"));
        return nowHidden ? ("🙈 " + name + " 을(를) 목록에서 숨겼습니다.") : ("👀 " + name + " 을(를) 다시 표시합니다.");
    }

    @Override
    @Transactional
    public String partyToggle(String userName, int idx) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p != null && "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 파티를 변경할 수 없습니다.";
        }
        // 마을 전용 제한 폐지 -- 전투 중만 아니면 어디서든 편성 가능 (2026-09-03)
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (idx < 1 || idx > companions.size()) {
            return "잘못된 번호입니다. /파티편성 으로 목록을 확인하세요.";
        }
        HashMap<String, Object> target = companions.get(idx - 1);
        boolean inParty = target.get("PARTY_SLOT") != null;

        if (inParty) {
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", intVal(target.get("COMPANION_ID"), 0));
            up.put("partySlot", null);
            dao.updateCompanionPartySlot(up);
            return "파티에서 해제했습니다.";
        }

        int used = 0;
        for (HashMap<String, Object> c : companions) if (c.get("PARTY_SLOT") != null) used++;
        if (used >= 3) {
            return "파티는 최대 3명까지 편성 가능합니다. 다른 동료를 먼저 해제하세요.";
        }
        boolean[] usedSlot = new boolean[4];
        for (HashMap<String, Object> c : companions) {
            Object s = c.get("PARTY_SLOT");
            if (s != null) usedSlot[((Number) s).intValue()] = true;
        }
        int slot = 1;
        while (slot <= 3 && usedSlot[slot]) slot++;

        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(target.get("COMPANION_ID"), 0));
        up.put("partySlot", slot);
        dao.updateCompanionPartySlot(up);

        if (used + 1 == 3) grantAchievement(userName, 15);

        StringBuilder sb = new StringBuilder("파티 ").append(slot).append("번 슬롯에 편성했습니다!");
        if (p != null && intVal(p.get("CUR_FLOOR"), 0) == 0) {
            sb.append(NL).append("👉 층이동 명령어로 1층 가세요! (/층변경 1)");
        }
        return sb.toString();
    }

    // ================================================================
    // /탑업적
    // ================================================================
    @Override
    public String achievements(String userName) {
        List<HashMap<String, Object>> all = dao.selectAchievementList();
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(userName);

        // 이름(ACH_NAME) → 달성일(ACH_ID)로 매핑해두면 이름만 나열할 때도 ACH_ID 순서를 유지할 수 있음
        HashMap<Integer, String> nameById = new HashMap<>();
        for (HashMap<String, Object> a : all) {
            nameById.put(intVal(a.get("ACH_ID"), -1), strVal(a.get("ACH_NAME"), ""));
        }
        List<Integer> clearedIds = new ArrayList<>();
        for (HashMap<String, Object> m : mine) clearedIds.add(intVal(m.get("ACH_ID"), -1));
        Collections.sort(clearedIds);

        StringBuilder sb = new StringBuilder(userName).append("님의 업적 (")
                .append(mine.size()).append("/").append(all.size()).append(")," + NL);
        if (clearedIds.isEmpty()) {
            sb.append("(아직 달성한 업적이 없습니다)");
            return sb.toString();
        }
        for (Integer id : clearedIds) {
            sb.append("✅ ").append(nameById.getOrDefault(id, "?")).append(NL);
        }
        return sb.toString();
    }

    // ================================================================
    // 가챠
    // ================================================================
    // (구 /탑상점 명령어는 제거됨 -- SPA "상점" 탭이 그 UI 역할을 대신하고,
    //  비밀상점 칸에서 나오는 무료뽑기권으로 대체됨)

    /**
     * 동료 초상화용 랜덤 이미지 1장을 가져와 URL만 반환. nekos.best는 이미 user_info_view.jsp에서
     * 클라이언트(localStorage)로 캐싱해 쓰던 API인데, 여기선 동료별로 영구히 남아야 해서 뽑는 시점에
     * 서버가 한 번 호출해 DB(IMAGE_URL)에 박아둔다. 실패해도 동료 생성 자체는 계속 진행(null 반환).
     */
    /**
     * /이미지갱신 — IMAGE_URL이 없는 동료(전체 유저 공통, 최대 20마리씩)를 찾아 nekos.best에서
     * 이미지를 받아와 DB(IMAGE_URL)에 채워넣는다. 예전엔 뽑기 시점에 자동 호출했지만, 외부 API가
     * 느리거나(이 프로젝트 환경에서는 사내망이 외부 사이트를 차단하는 경우도 있음) 실패하면 뽑기
     * 응답 자체가 늦어지는 문제가 있어서, 뽑기와 분리해 관리자가 필요할 때 직접 실행하는 명령어로 뺐다.
     * 한 번에 20마리로 제한하는 이유: 이 요청 하나가 컨트롤러 스레드를 물고 있는 동안 마리당 최대
     * 3초(연결)+3초(응답) 대기할 수 있어서, 너무 많이 처리하면 요청이 과도하게 오래 걸림 -- 남은
     * 마리가 있으면 안내 문구에 표시하고, 다시 실행하면 이어서 처리된다.
     */
    @Override
    public String refreshCompanionImages() {
        List<HashMap<String, Object>> targets = dao.selectCompanionsMissingImage(20);
        if (targets.isEmpty()) {
            return "🖼️ 이미지가 없는 동료가 없습니다. 전부 채워져 있어요.";
        }
        int success = 0, fail = 0;
        for (HashMap<String, Object> c : targets) {
            String imgUrl = fetchRandomNekoImage();
            if (imgUrl == null) {
                fail++;
                continue;
            }
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", intVal(c.get("COMPANION_ID"), 0));
            up.put("imageUrl", imgUrl);
            dao.updateCompanionImage(up);
            success++;
        }
        int remaining = dao.countCompanionsMissingImage();
        StringBuilder sb = new StringBuilder("🖼️ 이미지 갱신: 성공 ").append(success).append("마리");
        if (fail > 0) sb.append(", 실패 ").append(fail).append("마리(외부 API 응답 없음/차단 추정)");
        if (remaining > 0) sb.append(NL).append("아직 ").append(remaining).append("마리 남음 -- /이미지갱신 다시 실행하면 이어서 처리됩니다.");
        return sb.toString();
    }

    private String fetchRandomNekoImage() {
        try {
            URL url = new URL("https://nekos.best/api/v2/neko");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            // nekos.best는 User-Agent 미지정 요청을 403으로 차단함(요구 형식: "APP_NAME (CONTACT_INFO)").
            // https://docs.nekos.best/getting-started/api-reference.html#user-agent
            conn.setRequestProperty("User-Agent", "RgbTowerBot/1.0 (https://rgb-tns.dev-apc.com)");
            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject obj = new JSONObject(sb.toString());
            JSONArray results = obj.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            return results.getJSONObject(0).optString("url", null);
        } catch (Exception e) {
            return null;
        }
    }

    private int rollGrade(HashMap<String, Object> gacha) {
        double[] w = new double[6];
        double sum = 0;
        for (int i = 0; i < 6; i++) {
            Object v = gacha.get("PROB_G" + (i + 1));
            w[i] = v == null ? 0 : ((Number) v).doubleValue();
            sum += w[i];
        }
        if (sum <= 0) return 1;
        double r = RND.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < 6; i++) {
            acc += w[i];
            if (r < acc) return i + 1;
        }
        return 6;
    }

    /** 하급 동료 계약서(GACHA_ID=1)는 튜토리얼 차원에서 첫 2회까지 무료. */
    private static final int STARTER_GACHA_ID = 1;
    private static final int STARTER_FREE_PULLS = 2;

    /** 동료 뽑기 1회의 핵심 로직(무료판정/비용차감/추첨/insert)만 수행. 실패 시 result에 error만 채워 반환. */
    private HashMap<String, Object> pullCompanionCore(String userName, HashMap<String, Object> gacha,
            HashMap<String, Object> p, int ownedSoFar) {
        HashMap<String, Object> result = new HashMap<>();
        int gachaId = intVal(gacha.get("GACHA_ID"), 0);

        // 해금 여부는 무료뽑기권/스타터 무료와 무관하게 항상 확인 (권은 비용만 면제, 해금 요건은 그대로)
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 계약서입니다.");
            return result;
        }

        boolean free = gachaId == STARTER_GACHA_ID && ownedSoFar < STARTER_FREE_PULLS;
        if (!free) free = consumeCompanionVoucher(userName, p);
        if (!free) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            if (!deductPp(userName, p, cost)) {
                result.put("error", "PP가 부족합니다. (필요 " + cost.format() + " PP)");
                return result;
            }
        }

        int grade = rollGrade(gacha);
        String job = JOB_KEYS[RND.nextInt(JOB_KEYS.length)];
        String[] namePool = NAME_POOL_BY_JOB_GRADE.get(job)[grade - 1];
        String name = namePool[RND.nextInt(namePool.length)];

        // 중복 동료(같은 직업+이름을 이미 보유) 처리: 이름 풀이 직업×등급별로 나뉘어 있어서(위
        // NAME_POOL_BY_JOB_GRADE 참고) 같은 이름은 항상 같은 등급에서만 나온다 -- 즉 "직업+이름"이
        // 같으면 등급도 항상 같다는 뜻이라, [예전 버그였던] "다른 등급인데 이름이 겹쳐서 증발" 같은
        // 상황 자체가 이제 구조적으로 발생하지 않는다. 그래서 등급 비교 없이 단순하게 직업+이름만
        // 같으면 진짜 중복으로 보고 뽑기 비용의 20%를 PP로 환급한다("중복 정산").
        boolean dupe = false;
        for (HashMap<String, Object> owned : dao.selectUserCompanions(userName)) {
            if (job.equals(strVal(owned.get("CLASS"), "")) && name.equals(strVal(owned.get("NAME"), ""))) {
                dupe = true;
                break;
            }
        }
        if (dupe) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            PP dupeBonus = cost.multiply(0.2);
            addPp(userName, p, dupeBonus);
            result.put("ok", true);
            result.put("dupe", true);
            result.put("job", job);
            result.put("grade", grade);
            result.put("name", name);
            result.put("dupeBonus", dupeBonus.format());
            return result;
        }

        int[] stat = calcBaseStat(job, grade);
        String imageUrl = fetchRandomNekoImage();

        HashMap<String, Object> c = new HashMap<>();
        c.put("userName", userName);
        c.put("class", job);
        c.put("grade", grade);
        c.put("name", name);
        c.put("imageUrl", imageUrl);
        c.put("curHpValue", (double) stat[0]);
        c.put("curHpExt", "");
        c.put("partySlot", null);
        dao.insertCompanion(c);

        int cnt = ownedSoFar + 1;
        if (cnt == 1) grantAchievement(userName, 10);
        if (cnt == 50) grantAchievement(userName, 11);
        if (grade == 6) grantAchievement(userName, 22);

        result.put("ok", true);
        result.put("dupe", false);
        result.put("job", job);
        result.put("grade", grade);
        result.put("stat", stat);
        result.put("name", name);
        return result;
    }

    private String pullCompanionInternal(String userName, int gachaId) {
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"COMPANION".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 동료 계약서입니다.";
        }
        int ownedBefore = dao.countUserCompanions(userName);
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        HashMap<String, Object> r = pullCompanionCore(userName, gacha, p, ownedBefore);
        if (r.get("error") != null) return (String) r.get("error");

        String job = (String) r.get("job");
        int grade = intVal(r.get("grade"), 1);
        String name = (String) r.get("name");

        if (Boolean.TRUE.equals(r.get("dupe"))) {
            return "🔁 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 뽑힘)" + NL
                    + "계약서 대신 " + r.get("dupeBonus") + " PP로 환급되었습니다.";
        }

        int[] stat = (int[]) r.get("stat");
        int cnt = ownedBefore + 1;

        // 새로 뽑은 동료는 (PARTY_SLOT NULLS LAST, COMPANION_ID) 정렬상 항상 목록의 맨 끝(=cnt번)에 위치
        StringBuilder sb = new StringBuilder();
        sb.append("┌─────────────┐").append(NL);
        sb.append("  🎉 새 동료 영입!").append(NL);
        sb.append("└─────────────┘").append(NL);
        sb.append(name).append(" (").append(JOB_NAME.get(job)).append(" ★").append(grade).append(")").append(NL);
        sb.append("스탯: HP ").append(stat[0]).append(" / ATK ").append(stat[1]).append(" / DEF ").append(stat[2]).append(NL);
        sb.append("👉 /파티편성 ").append(cnt).append(" 로 파티에 편성하세요 (동료 목록 ").append(cnt).append("번)");
        return sb.toString();
    }

    private String pullCompanionTenInternal(String userName, int gachaId) {
        HashMap<String, Object> gacha = dao.selectGacha(gachaId);
        if (gacha == null || !"COMPANION".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 동료 계약서입니다.";
        }
        int owned = dao.countUserCompanions(userName);
        HashMap<String, Object> p = dao.selectUserProgress(userName);

        int[] gradeCount = new int[7]; // index 1~6
        int success = 0;
        int dupeCount = 0;
        PP dupeTotal = PP.of(0, "");
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullCompanionCore(userName, gacha, p, owned);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
            if (Boolean.TRUE.equals(r.get("dupe"))) {
                dupeCount++;
                dupeTotal = dupeTotal.add(PP.parse((String) r.get("dupeBonus")));
            } else {
                owned++; // 중복이 아닌 실제 신규 동료일 때만 보유 수 증가(스타터 무료뽑기/업적 판정에 사용)
            }
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 동료뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (dupeCount > 0) {
            sb.append(NL).append("🔁 중복 ").append(dupeCount).append("마리 → ").append(dupeTotal.format()).append(" PP 환급");
        }
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 /파티편성 으로 확인하세요");
        return sb.toString();
    }

    private boolean isVillage(HashMap<String, Object> p) {
        return intVal(p.get("CUR_FLOOR"), 0) % 10 == 0;
    }

    @Override
    public int freeCompanionPullsLeft(String userName) {
        return Math.max(0, STARTER_FREE_PULLS - dao.countUserCompanions(userName));
    }

    @Override
    @Transactional
    public String gachaCompanion(String userName, int gachaId) {
        getOrInitProgress(userName);
        // 뽑기(가챠)는 어디서든 가능 -- 파티 편성(/파티편성)도 전투 중만 아니면 어디서든 가능(마을 제한 폐지)
        // gachaId는 화면/도움말에 보이는 표시번호(1부터, UNLOCK_FLOOR 순) -- 실제 GACHA_ID로 변환해서 사용
        Integer realId = resolveGachaId("COMPANION", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        return pullCompanionInternal(userName, realId);
    }

    @Override
    @Transactional
    public String gachaCompanionTen(String userName, int gachaId) {
        getOrInitProgress(userName);
        Integer realId = resolveGachaId("COMPANION", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        return pullCompanionTenInternal(userName, realId);
    }

    /** 장비 뽑기 1회의 핵심 로직만 수행. 실패 시 result에 error만 채워 반환. */
    private HashMap<String, Object> pullEquipCore(String userName, HashMap<String, Object> gacha, HashMap<String, Object> p) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 상자입니다.");
            return result;
        }
        if (!consumeEquipVoucher(userName, p)) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            if (!deductPp(userName, p, cost)) {
                result.put("error", "PP가 부족합니다. (필요 " + cost.format() + " PP)");
                return result;
            }
        }

        int grade = rollGrade(gacha);
        String job = JOB_KEYS[RND.nextInt(JOB_KEYS.length)];
        String[] parts = { "HELMET", "WEAPON", "ARMOR" };
        String part = parts[RND.nextInt(parts.length)];

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", job);
        e.put("part", part);
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        if (grade == 6) grantAchievement(userName, 22);

        result.put("ok", true);
        result.put("job", job);
        result.put("part", part);
        result.put("grade", grade);
        return result;
    }

    @Override
    @Transactional
    public String gachaEquip(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        Integer realId = resolveGachaId("EQUIP", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        HashMap<String, Object> gacha = dao.selectGacha(realId);
        if (gacha == null || !"EQUIP".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 장비 상자입니다.";
        }
        HashMap<String, Object> r = pullEquipCore(userName, gacha, p);
        if (r.get("error") != null) return (String) r.get("error");

        String job = (String) r.get("job");
        String part = (String) r.get("part");
        int grade = intVal(r.get("grade"), 1);
        return "🎁 " + JOB_NAME.get(job) + "용 " + partNameOf(part) + " ★" + grade + " 획득! (" + equipBonusText(part, grade) + ")";
    }

    @Override
    @Transactional
    public String gachaEquipTen(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        Integer realId = resolveGachaId("EQUIP", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        HashMap<String, Object> gacha = dao.selectGacha(realId);
        if (gacha == null || !"EQUIP".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 장비 상자입니다.";
        }

        int[] gradeCount = new int[7];
        int success = 0;
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullEquipCore(userName, gacha, p);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 장비뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 파티/장비 탭에서 확인하세요");
        return sb.toString();
    }

    // ================================================================
    // /주사위구매 (등급 확인/교체 — 무료 장착, 층 진행으로 자동 해금)
    // ================================================================
    @Override
    @Transactional
    public String diceShop(String userName, Integer n) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        String curDice = strVal(p.get("DICE_GRADE"), "DICE_6");

        if (n == null) {
            StringBuilder sb = new StringBuilder(userName).append("님의 주사위 목록," + NL);
            for (int i = 0; i < DICE_NAMES.length; i++) {
                boolean unlockedTier = unlocked >= DICE_UNLOCK[i];
                sb.append(i + 1).append(". ").append(DICE_NAMES[i])
                  .append(unlockedTier ? "" : " (미해금, " + DICE_UNLOCK[i] + "층부터)")
                  .append(DICE_NAMES[i].equals(curDice) ? " ← 사용중" : "")
                  .append(NL);
            }
            sb.append("/주사위구매 N 으로 해금된 주사위 장착");
            return sb.toString();
        }

        if (n < 1 || n > DICE_NAMES.length) return "잘못된 번호입니다.";
        if (unlocked < DICE_UNLOCK[n - 1]) return "아직 해금되지 않은 주사위입니다.";

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceGrade", DICE_NAMES[n - 1]);
        dao.updateUserProgress(up);
        return DICE_NAMES[n - 1] + " 을(를) 장착했습니다.";
    }

    // ================================================================
    // /스탯구매
    // ================================================================
    @Override
    @Transactional
    public String statShop(String userName, String type) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        HashMap<String, Object> stat = dao.selectUserStat(userName);
        int atkMaxLv = stat == null ? 0 : intVal(stat.get("ATK_MAX_LV"), 0);
        int atkMinLv = stat == null ? 0 : intVal(stat.get("ATK_MIN_LV"), 0);
        int hpLv     = stat == null ? 0 : intVal(stat.get("HP_LV"), 0);
        int cap = 5 + 5 * (intVal(p.get("UNLOCKED_BLOCK"), 0) / 10);

        if (type == null || type.isEmpty()) {
            StringBuilder sb = new StringBuilder(userName).append("님의 스탯 구매 현황 (구간 상한 ").append(cap).append(")," + NL);
            sb.append("공격력(최대) Lv").append(atkMaxLv).append(" — 다음 비용 ").append(50 * (atkMaxLv + 1)).append(" PP").append(NL);
            sb.append("공격력(최소) Lv").append(atkMinLv).append(" — 다음 비용 ").append(50 * (atkMinLv + 1)).append(" PP").append(NL);
            sb.append("체력 Lv").append(hpLv).append(" — 다음 비용 ").append(50 * (hpLv + 1)).append(" PP").append(NL);
            sb.append("/스탯구매 공격력 | 최소공격력 | 체력");
            return sb.toString();
        }

        int curLv;
        String field;
        switch (type) {
            case "공격력":     curLv = atkMaxLv; field = "atk"; break;
            case "최소공격력": curLv = atkMinLv; field = "min"; break;
            case "체력":       curLv = hpLv;     field = "hp";  break;
            default: return "스탯 종류는 공격력 / 최소공격력 / 체력 중 하나여야 합니다.";
        }
        if (curLv >= cap) return "현재 구간에서는 더 이상 강화할 수 없습니다. 다음 마을에 도달하면 상한이 늘어납니다.";

        PP cost = PP.fromPP(50 * (curLv + 1));
        if (!deductPp(userName, p, cost)) return "PP가 부족합니다. (필요 " + cost.format() + " PP)";

        if ("atk".equals(field)) atkMaxLv++;
        else if ("min".equals(field)) atkMinLv++;
        else hpLv++;

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("atkMaxLv", atkMaxLv);
        up.put("atkMinLv", atkMinLv);
        up.put("hpLv", hpLv);
        dao.upsertUserStat(up);
        return type + " 스탯을 강화했습니다! (Lv" + curLv + " → Lv" + (curLv + 1) + ")";
    }

    // ================================================================
    // 장비
    // ================================================================
    private String partNameOf(String part) {
        return "HELMET".equals(part) ? "투구" : "WEAPON".equals(part) ? "무기" : "갑옷";
    }

    /** 장비 등급/부위별 스탯 보너스 표기 (예: "ATK +13 / +10%") — EQUIP_BONUS[grade-1] 기준. */
    private String equipBonusText(String part, int grade) {
        double[] b = EQUIP_BONUS[grade - 1];
        int fixedIdx = "HELMET".equals(part) ? 0 : "WEAPON".equals(part) ? 2 : 4;
        int pctIdx = fixedIdx + 1;
        String statName = "HELMET".equals(part) ? "HP" : "WEAPON".equals(part) ? "ATK" : "DEF";
        return statName + " +" + (int) b[fixedIdx] + " / +" + Math.round(b[pctIdx] * 100) + "%";
    }

    /** 장비 목록 - 미착용은 /장비장착·/장비합성에 그대로 쓸 수 있는 번호를 붙이고, 착용중인 건 누가 끼고 있는지 표시. */
    @Override
    public String equipList(String userName) {
        getOrInitProgress(userName);
        List<HashMap<String, Object>> equips = dao.selectUserEquip(userName);
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        HashMap<Integer, String> companionLabel = new HashMap<>();
        for (HashMap<String, Object> c : companions) {
            String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), ""), "?");
            String name = strVal(c.get("NAME"), job);
            companionLabel.put(intVal(c.get("COMPANION_ID"), -1), job + "(" + name + ") ★" + intVal(c.get("GRADE"), 1));
        }
        if (equips.isEmpty()) return "보유한 장비가 없습니다.";

        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        List<HashMap<String, Object>> equipped = new ArrayList<>();
        for (HashMap<String, Object> e : equips) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
            else equipped.add(e);
        }

        StringBuilder sb = new StringBuilder(userName).append("님의 장비 목록," + NL);
        sb.append("[미착용 — 이 번호로 /장비장착 N, /장비합성 N]").append(NL);
        if (unequipped.isEmpty()) {
            sb.append("(없음)").append(NL);
        } else {
            int idx = 1;
            for (HashMap<String, Object> e : unequipped) {
                String part = strVal(e.get("PART"), "");
                int grade = intVal(e.get("GRADE"), 1);
                sb.append(idx++).append(". ").append(JOB_NAME.getOrDefault(strVal(e.get("CLASS"), ""), "?"))
                  .append(" ").append(partNameOf(part)).append(" ★").append(grade)
                  .append(" (").append(equipBonusText(part, grade)).append(")").append(NL);
            }
        }
        if (!equipped.isEmpty()) {
            sb.append("[장착중]").append(NL);
            for (HashMap<String, Object> e : equipped) {
                String part = strVal(e.get("PART"), "");
                int grade = intVal(e.get("GRADE"), 1);
                Object cid = e.get("EQUIPPED_COMPANION_ID");
                sb.append("- ").append(JOB_NAME.getOrDefault(strVal(e.get("CLASS"), ""), "?"))
                  .append(" ").append(partNameOf(part)).append(" ★").append(grade)
                  .append(" (").append(equipBonusText(part, grade)).append(")")
                  .append(" → ").append(companionLabel.getOrDefault(((Number) cid).intValue(), "?"))
                  .append(NL);
            }
        }
        sb.append("/장비장착 N [M], /장비합성 N (둘 다 위 [미착용] 번호 기준)");
        return sb.toString();
    }

    /** /장비장착 인자 없이 호출 시: 미착용 장비 번호 + 파티원 번호를 한 번에 안내 */
    @Override
    public String equipWearUsage(String userName) {
        getOrInitProgress(userName);
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) party.add(c);
        }

        StringBuilder sb = new StringBuilder("사용법: /장비장착 N [M]  (N=미착용 장비 번호, M=파티원 번호(생략 시 같은 직업 자동탐색))").append(NL);
        sb.append("[미착용 장비 N번]").append(NL);
        if (unequipped.isEmpty()) {
            sb.append("(없음 — /장비뽑기로 먼저 획득하세요)").append(NL);
        } else {
            int idx = 1;
            for (HashMap<String, Object> e : unequipped) {
                String part = strVal(e.get("PART"), "");
                int grade = intVal(e.get("GRADE"), 1);
                sb.append(idx++).append(". ").append(JOB_NAME.getOrDefault(strVal(e.get("CLASS"), ""), "?"))
                  .append(" ").append(partNameOf(part)).append(" ★").append(grade)
                  .append(" (").append(equipBonusText(part, grade)).append(")").append(NL);
            }
        }
        sb.append("[파티원 M번]").append(NL);
        if (party.isEmpty()) {
            sb.append("(없음 — /파티편성으로 먼저 편성하세요, 전투 중이 아니면 어디서든 가능)").append(NL);
        } else {
            int idx = 1;
            for (HashMap<String, Object> c : party) {
                String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), ""), "?");
                String name = strVal(c.get("NAME"), job);
                sb.append(idx++).append(". ").append(job).append("(").append(name).append(") ★").append(intVal(c.get("GRADE"), 1)).append(NL);
            }
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public String equipWear(String userName, int equipIdx, Integer companionIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 변경할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        HashMap<String, Object> equip = unequipped.get(equipIdx - 1);
        String equipClass = strVal(equip.get("CLASS"), "");
        String part = strVal(equip.get("PART"), "");

        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : companions) if (c.get("PARTY_SLOT") != null) party.add(c);

        HashMap<String, Object> targetCompanion = null;
        if (companionIdx != null) {
            if (companionIdx < 1 || companionIdx > party.size()) return "잘못된 동료 번호입니다. /파티편성을 확인하세요.";
            targetCompanion = party.get(companionIdx - 1);
        } else {
            for (HashMap<String, Object> c : party) {
                if (equipClass.equals(strVal(c.get("CLASS"), ""))) { targetCompanion = c; break; }
            }
        }
        if (targetCompanion == null) return "장착할 동료를 찾지 못했습니다 (같은 직업의 파티원이 필요합니다).";
        if (!equipClass.equals(strVal(targetCompanion.get("CLASS"), ""))) return "이 장비는 " + JOB_NAME.get(equipClass) + " 전용입니다.";

        int companionId = intVal(targetCompanion.get("COMPANION_ID"), 0);
        // 같은 부위에 이미 장착된 게 있으면 해제
        for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
            if (part.equals(strVal(e.get("PART"), ""))) {
                HashMap<String, Object> unwear = new HashMap<>();
                unwear.put("equipId", intVal(e.get("EQUIP_ID"), 0));
                unwear.put("equippedCompanionId", null);
                dao.updateEquipEquippedCompanion(unwear);
            }
        }

        HashMap<String, Object> wear = new HashMap<>();
        wear.put("equipId", intVal(equip.get("EQUIP_ID"), 0));
        wear.put("equippedCompanionId", companionId);
        dao.updateEquipEquippedCompanion(wear);

        int grade = intVal(equip.get("GRADE"), 1);
        String targetJob = JOB_NAME.getOrDefault(equipClass, "?");
        String targetName = strVal(targetCompanion.get("NAME"), targetJob);
        return "🎽 " + targetJob + "(" + targetName + ")에게 " + partNameOf(part) + " ★" + grade
                + " (" + equipBonusText(part, grade) + ") 장착 완료!";
    }

    @Override
    @Transactional
    public String equipSynthesis(String userName, int equipIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 합성할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        HashMap<String, Object> equip = unequipped.get(equipIdx - 1);
        int grade = intVal(equip.get("GRADE"), 1);
        if (grade >= 6) return "★6 장비는 합성할 수 없습니다.";
        String clazz = strVal(equip.get("CLASS"), "");
        String part = strVal(equip.get("PART"), "");

        List<HashMap<String, Object>> same = dao.selectSameEquipForSynthesis(userName, clazz, part, grade);
        if (same.size() < 3) return "동일 등급/부위/직업 미착용 장비가 3개 이상 필요합니다. (현재 " + same.size() + "개)";

        for (int i = 0; i < 3; i++) {
            dao.deleteEquip(intVal(same.get(i).get("EQUIP_ID"), 0));
        }
        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", clazz);
        e.put("part", part);
        e.put("grade", grade + 1);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        grantAchievement(userName, 12);
        // TODO: EQUIP_SYNTHESIS 누적 횟수 카운터가 없어 13번(30회) 업적은 아직 체크 불가
        return "✨ 합성 성공! " + JOB_NAME.get(clazz) + " " + partNameOf(part) + " ★" + (grade + 1)
                + " (" + equipBonusText(part, grade + 1) + ") 획득!";
    }
}
