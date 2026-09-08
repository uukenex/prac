package my.prac.core.prjbot.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

    /**
     * tower-view SPA 링크 + userName 쿼리파라미터 -- 클릭하면 그 유저 화면이 바로 뜨도록.
     * [2026-09-03] 한글을 %XX로 URL 인코딩하면 채팅 텍스트에서 알아보기 어렵다는 요청으로
     * 인코딩 없이 원문 그대로 붙인다. (예전엔 카톡 등 채팅앱의 링크 자동인식이 원문 한글에서
     * 끊기는 문제 때문에 인코딩을 넣었던 것으로 보임 -- 다시 끊기면 그때 재검토)
     */
    private String towerViewLink(String userName) {
        return TOWER_VIEW_URL + "?userName=" + userName;
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
    // "어둠 " 접두사를 붙여 재사용(스탯은 그대로 블록 단위 유지, 이름만 층마다 다르게 보이는
    // 용도). [2026-09-05] "강화"는 ELITE 칸 몬스터 이름(eliteMonsterName의 "💪 강화 ")과
    // 헷갈린다는 요청으로 이쪽만 "어둠"으로 변경.
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
        return pos > n ? "어둠 " + base : base;
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

    // 주사위 해금 계단 [코드, 해금 UNLOCKED_BLOCK] -- [2026-09-05] DICE_4 신설, 언제든(0층부터)
    // 쓸 수 있는 탐사용 저분산 주사위로 DICE_6과 같은 해금 단계(0)에 추가.
    private static final String[] DICE_NAMES = { "DICE_4", "DICE_6", "DICE_8", "DICE_10", "DICE_12", "DICE_20" };

    // COMPANION 가챠 티어(GACHA_ID 1~4) 이름 -- floorVoucherReward()가 지급하는 티어락 뽑기권 표시용
    private static final String[] COMPANION_TIER_NAME = { "하급", "중급", "상급", "최상급" };
    private static final int[]    DICE_UNLOCK = { 0, 0, 10, 30, 50, 70 };

    // [2026-09-08] 30/50/60/70/80/90층 마을 도착 보상 -- 주사위 강화(+, 최소치 상승) 상점,
    // 최대 6단계까지 순차 구매(이전 단계 보유 + 그 층 도달 필요). 계정 전체 공통 적용(장착
    // 중인 주사위 등급 무관), 최대치는 항상 그대로 -- diceMinFor()/rollFace() 참고. 가격은
    // 잠정치, 실측 후 조정 가능.
    private static final int[]  DICE_BONUS_UNLOCK = { 30, 50, 60, 70, 80, 90 };
    private static final long[] DICE_BONUS_COST   = { 3000, 6000, 12000, 24000, 48000, 96000 };

    // [2026-09-08 후속] 마이너스 주사위(-, 최소치 하강)는 강화(+)와 달리 "탐사 정밀 이동"용
    // 유틸리티 성격이라 여러 단계로 안 키우고 -1 딱 한 단계만("마이너스는 -1 하나만 있길
    // 바란다" 확인) -- 30층 도착 시 구매 가능, 1회 구매하면 끝.
    private static final int[]  DICE_MALUS_UNLOCK = { 30 };
    private static final long[] DICE_MALUS_COST   = { 1000 };

    // /스탯구매 레벨당 실제 증가량 -- computeEffectiveStat()과 상점 표시(statShop/statShopInfo)가
    // 이 값을 공유해서 "레벨당 얼마나 느는지" 표시가 실제 전투 계산과 어긋나지 않게 한다.
    private static final double ATK_PCT_PER_LV = 0.03;   // 공격력(최대) 레벨당 +3%(곱연산)
    private static final double HP_PCT_PER_LV  = 0.03;   // 체력 레벨당 +3%(곱연산)
    private static final int    MIN_DMG_PER_LV = 2;      // 최소공격력 레벨당 데미지 하한 +2(고정값)

    // 칸 종류 표시(아이콘+이름). 계단은 "위로 향하는 계단"/"아래로 향하는 계단" 2종으로 분리
    // (요청으로 신설) -- 층마다 고정으로 하나씩, 총 2칸. 층이동 시 어느 쪽에 도착하는지가 이동
    // 방향(위/아래)을 따라 갈리므로 changeFloor() 참고.
    private static final HashMap<String, String> TILE_LABEL = new HashMap<String, String>() {{
        put("COMBAT", "⚔️ 전투");  put("PP", "🍀 럭키");     put("TREASURE", "💎 보물상자");
        put("TRAP",   "🕳️ 함정");  put("SPECIAL", "✨ 특수");
        put("STAIRS_UP", "🪜⬆️ 계단(위)"); put("STAIRS_DOWN", "🪜⬇️ 계단(아래)");
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

    // 하루 주사위(이동+전투 통합) 굴림 횟수 제한. 위 쿨타임들과 같은 이유로 DB(TBOT_S5_CONFIG)
    // config화 -- 재배포 없이 /갱신으로 값만 바꿀 수 있게.
    private static volatile int DAILY_DICE_LIMIT = 750;

    // [2026-09-07] "웹/카톡 같이 쓰게 해달라, 카톡은 200회 더 주자" 요청 -- 채널(WEB/CHAT)
    // 무관하게 공유하는 총 굴림 카운터(DICE_ROLL_COUNT_TODAY) 기준으로, 웹은 DAILY_DICE_LIMIT
    // 까지만, 카카오톡(CHAT)은 거기에 이 보너스를 더한 값까지 계속 가능하다. 예) 기본 1000 +
    // 200 = 카톡 1200. 채널을 섞어 쓰든(웹 600+카톡 400) 한쪽만 쓰든(웹만 1000, 또는 카톡만
    // 1200) 결과는 항상 "총합이 웹 한도를 넘으면 웹 차단, 카톡 한도를 넘으면 둘 다 차단"으로
    // 동일하다 -- checkAndBumpDailyDiceLimit 참고.
    private static volatile int KAKAO_BONUS_DICE = 200;

    // 자동사냥(미접속 정산) 속도/상한. "재배포 없이 밸런스 조절하게 해달라" 요청으로 config화.
    //   - AUTO_HUNT_KILLS_PER_HOUR : 미접속 시간당 처치 수(분당 환산해 10분당 1마리처럼 사용)
    //   - AUTO_HUNT_MAX_HOURS      : 정산에 반영하는 미접속 시간 상한(그 이상 방치해도 더 안 늘어남)
    private static volatile int AUTO_HUNT_KILLS_PER_HOUR = 6;
    private static volatile int AUTO_HUNT_MAX_HOURS = 8;

    // /이벤트지급(관리자 전용) 실행 권한이 있는 유저명 목록 -- TBOT_S5_CONFIG.EVENT_ADMIN_USERS에
    // '|'로 구분해 저장(예: "일어난다람쥐/카단|다른관리자"). 이 시스템엔 별도 권한/역할 체계가
    // 없어서, 뽑기권처럼 실제 경제가치가 있는 걸 아무나 채팅으로 못 뿌리게 막는 유일한 장치다.
    // 비어있으면(기본값) 아무도 실행할 수 없다 -- 반드시 DB에 직접 세팅해야 활성화됨(의도적).
    private static volatile String EVENT_ADMIN_USERS = "";

    /** 서버 기동 시 TBOT_S5_CONFIG를 읽어 메모리(static 필드)에 반영. 실패해도 기본값으로 계속 동작. */
    @PostConstruct
    public void loadConfig() {
        try {
            for (HashMap<String, Object> row : dao.selectAllConfig()) {
                String key = strVal(row.get("CONFIG_KEY"), "");
                String val = strVal(row.get("CONFIG_VALUE"), "");
                if ("EVENT_ADMIN_USERS".equals(key)) {
                    EVENT_ADMIN_USERS = val; // 문자열 그대로라 파싱 실패 케이스 없음
                    continue;
                }
                try {
                    if ("MOVE_COOLDOWN_SEC".equals(key)) {
                        MOVE_COOLDOWN_SEC = Long.parseLong(val);
                    } else if ("COMBAT_COOLDOWN_SEC".equals(key)) {
                        COMBAT_COOLDOWN_SEC = Long.parseLong(val);
                    } else if ("COMBAT_END_COOLDOWN_SEC".equals(key)) {
                        COMBAT_END_COOLDOWN_SEC = Long.parseLong(val);
                    } else if ("DAILY_DICE_LIMIT".equals(key)) {
                        DAILY_DICE_LIMIT = Integer.parseInt(val);
                    } else if ("KAKAO_BONUS_DICE".equals(key)) {
                        KAKAO_BONUS_DICE = Integer.parseInt(val);
                    } else if ("AUTO_HUNT_KILLS_PER_HOUR".equals(key)) {
                        AUTO_HUNT_KILLS_PER_HOUR = Integer.parseInt(val);
                    } else if ("AUTO_HUNT_MAX_HOURS".equals(key)) {
                        AUTO_HUNT_MAX_HOURS = Integer.parseInt(val);
                    }
                } catch (NumberFormatException ignore) {
                    // 파싱 실패한 값은 무시하고 기존(기본) 값 유지
                }
            }
        } catch (Exception ignore) {
            // 서버 기동 시점에 DB 접속이 안 되거나 테이블이 없어도 기본값으로 계속 기동
        }
    }

    /** userName이 EVENT_ADMIN_USERS 목록에 있는지(=이벤트 지급 명령어 실행 권한이 있는지). */
    private boolean isEventAdmin(String userName) {
        if (EVENT_ADMIN_USERS == null || EVENT_ADMIN_USERS.trim().isEmpty() || userName == null) return false;
        for (String u : EVENT_ADMIN_USERS.split("\\|")) {
            if (u.trim().equals(userName)) return true;
        }
        return false;
    }

    /** /갱신 — TBOT_S5_CONFIG를 다시 읽어 메모리 값을 갱신 */
    @Override
    public String refreshConfig() {
        loadConfig();
        return "🗼 시즌5 설정 갱신 완료 (칸이동 " + MOVE_COOLDOWN_SEC + "초 / 전투중 " + COMBAT_COOLDOWN_SEC
                + "초 / 전투종료 " + COMBAT_END_COOLDOWN_SEC + "초 / 하루 주사위 한도 웹 " + DAILY_DICE_LIMIT
                + "회·카톡 " + (DAILY_DICE_LIMIT + KAKAO_BONUS_DICE) + "회 / 자동사냥 시간당 "
                + AUTO_HUNT_KILLS_PER_HOUR + "마리, 최대 " + AUTO_HUNT_MAX_HOURS + "시간)";
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
        atk *= (1 + ATK_PCT_PER_LV * atkMaxLv);
        hp  *= (1 + HP_PCT_PER_LV * hpLv);
        int minDmgFloor = atkMinLv * MIN_DMG_PER_LV;

        return new int[]{ (int) Math.round(hp), (int) Math.round(atk), (int) Math.round(def), minDmgFloor };
    }

    private int diceMax(String diceGrade) {
        if (diceGrade == null) return 6;
        switch (diceGrade) {
            case "DICE_4":  return 4;
            case "DICE_8":  return 8;
            case "DICE_10": return 10;
            case "DICE_12": return 12;
            case "DICE_20": return 20;
            default:        return 6;
        }
    }

    /**
     * 주사위를 굴리고(diceMin~diceMax) 나온 눈을 전역 통계(TBOT_S5_DICE_STATS)에 1 증가시킨다.
     * 이동/전투공격/보호막/몬스터반격 등 RND.nextInt(...)를 쓰는 모든 지점에서 이걸로 대체 --
     * "/탑통계"의 "주사위 눈 나온 횟수" 항목용(범위 밖 눈금은 그 통계 테이블에 매칭되는 행이
     * 없어 조용히 무시됨, TBOT_S5_DICE_STATS는 1~20만 미리 시딩돼 있음). 통계 적재 실패가
     * 게임 진행을 막으면 안 되므로 실패는 조용히 무시한다.
     * [2026-09-08] "30/50/60/70/80/90층 마을 도착 보상으로 주사위 강화(+최소치)/마이너스
     * 주사위(-최소치) 상점" 신설 -- diceMin이 diceMax를 넘는 극단적 경우(작은 주사위+큰
     * 강화)는 max 고정으로 방어(Math.min).
     */
    private int rollFace(int diceMin, int diceMax) {
        int lo = Math.min(diceMin, diceMax);
        int face = RND.nextInt(diceMax - lo + 1) + lo;
        try { dao.bumpDiceFaceStat(face); } catch (Exception ignore) { }
        return face;
    }

    /** 위 rollFace()의 diceMin 인자용 -- 유저의 주사위 강화(+)/마이너스 주사위(-) 단계를
     *  합산해 이번 굴림의 최소 눈금을 계산한다(계정 전체 공통 적용, 장착 주사위 등급 무관,
     *  둘 다 "최소치만" 조정하고 최대치는 항상 diceMax 그대로). 몬스터 자신의 반격 굴림
     *  (rollFace(1, monsterDiceMax))에는 적용하지 않음 -- 플레이어 강화와 무관해야 함. */
    private int diceMinFor(HashMap<String, Object> p) {
        return 1 + intVal(p.get("DICE_MIN_BONUS"), 0) - intVal(p.get("DICE_MIN_MALUS"), 0);
    }

    private int floorBlockBase(int floor) {
        return (floor / 10) * 10;
    }

    private int blockNo(int floor) {
        return (floorBlockBase(floor) / 10) + 1;
    }

    // [2026-09-07, 당분간] 블록6(51~60층) 오픈 확정, 61층(블록7 사냥터) 이후는 아직 콘텐츠
    // 미공개라 진입 자체를 막는다. (49층/59층 보스 스킬, 51층+ 중간보스·신규 함정/럭키·
    // 지그재그 큰 보드·보상 인상 전부 51~60 범위에서 실측 검증 완료 후 이 값만 올렸음.)
    private static final int CONTENT_LOCKED_FLOOR = 61;

    // [2026-09-06] 51층 이후(블록6+) 전투칸에서 중간보스와 마주칠 확률(%). 밸런스 튜닝값이라
    // 필요하면 조정. 잠긴 콘텐츠라 실사용자 영향 없이 먼저 만들어두고 51층 오픈 시 재검토.
    private static final int MIDBOSS_CHANCE_PCT = 20;

    /**
     * "N층 완전탐사" 업적(ACH_ID 100+floor) 보상 — 3개 블록(=30층)마다 동료뽑기 티어가 한 단계
     * 오르고, 그 안에서 지급 수량이 1→3→5로 늘어난다: 블록1~3(1~30층)=하급×1/3/5,
     * 블록4~6(31~60층)=중급×1/3/5, 블록7~9(61~90층)=상급×1/3/5, 블록10(91~98층)=최상급×1.
     * @return [gachaTier(1~4, COMPANION GACHA_ID와 동일), count]
     */
    private int[] floorVoucherReward(int floor) {
        int block = blockNo(floor); // 1~10
        int tier = (block - 1) / 3 + 1;      // 1,1,1, 2,2,2, 3,3,3, 4
        int count = (block - 1) % 3 * 2 + 1; // 1,3,5, 1,3,5, 1,3,5, 1
        return new int[]{ tier, count };
    }

    /**
     * 이 (유저,층) 보드가 아직 없으면(마을 갔다온 뒤 첫 진입 등) 새로 만든다. 칸 개수는
     * TBOT_S5_FLOOR_INFO.TILE_COUNT(층별 고정, 기존과 동일)를 그대로 쓰고 칸 "종류"만 매번
     * 새로 무작위 배정한다. 고정 개수 칸을 먼저 넣고(계단 위/아래 각 1개씩 총 2개, 히든 1~2,
     * 보물상자1, 20층대+엔 강화몹1) 나머지를 전투50%/함정10%/럭키40%로 채운 뒤 위치를 섞는다.
     */
    // [버그 수정] @Transactional이 없어서, 트랜잭션이 안 걸린 컨텍스트(예: Season5ViewController의
    // 읽기 전용 GET들이 이걸 호출하는 buildTilesWithFogOfWar)에서 부르면 새로 만든 보드가
    // insertUserTileMasterBatch로 저장은 시도되지만 커밋이 안 돼서(스프링+마이바티스 조합에서
    // 트랜잭션 밖 쓰기는 세션이 닫히며 조용히 롤백됨) 매번 새로 랜덤 생성만 되고 실제로는 절대
    // 저장이 안 되는 문제가 있었다(실 DB 확인: 활발히 플레이 중인 계정인데 TBOT_S5_USER_TILE_MASTER
    // 행이 0개). changeFloor()/rollDice()처럼 이미 @Transactional인 곳에서 부르면 그 트랜잭션에
    // 합류하므로 원래도 정상 동작했음 -- 이 메서드 자체에 달아서 어디서 불러도 항상 커밋되게 함.
    @Override
    @Transactional
    public List<HashMap<String, Object>> ensureUserBoard(String userName, int floor) {
        List<HashMap<String, Object>> existing = dao.selectUserTileMaster(userName, floor);
        if (!existing.isEmpty()) return existing;

        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);

        List<String> types = new ArrayList<>();
        // 계단을 위/아래 방향으로 분리(요청) -- 항상 층마다 딱 2칸(각 방향 1개씩) 고정
        types.add("STAIRS_UP");
        types.add("STAIRS_DOWN");
        // "51층부터 워프포인트(특수칸) 기믹" 요청 -- 특수칸이 체크포인트 역할을 하므로 51층부턴
        // 넉넉하게 4개(기존 1~2개보다 늘림)를 배치.
        int specialCount = floor >= 51 ? 4 : (tileCount >= 20 ? 2 : 1);
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

        // "51층부터 마을 가도 탐사율이 초기화 안 되게(워프포인트/체크포인트 개념)" 요청 -- 51층
        // 이상에서 특수칸(워프포인트)을 밟으면 그 시점 탐사 칸수를 체크포인트로 저장해두고
        // (markSpecialTileCheckpoint 참고), 마을 복귀 등으로 보드가 새로 생성될 때 그 체크포인트
        // 만큼을 "이미 발견한 칸"으로 미리 채워 넣어서 탐사율이 체크포인트 지점까지는 유지되게
        // 한다(그 이후 발견분만 사라짐). 방금 막 생성된 프레시 보드이므로 어떤 특정 칸을 발견한
        // 것으로 칠지는 의미가 없어 그냥 1번~N번을 채운다.
        if (floor >= 51) {
            HashMap<String, Object> best = dao.selectUserFloorBest(userName, floor);
            int checkpoint = best == null ? 0 : intVal(best.get("CHECKPOINT_VISITED_COUNT"), 0);
            if (checkpoint > 0) {
                int n = Math.min(checkpoint, tiles.size());
                for (int tileNo = 1; tileNo <= n; tileNo++) {
                    dao.insertTileVisit(userName, floor, tileNo);
                }
            }
        }
        return tiles;
    }

    /**
     * "특수칸을 워프포인트로" 요청 -- 51층 이상에서 특수칸을 밟은 시점의 탐사 칸수를 체크포인트로
     * 저장(최고치만 갱신, GREATEST). 이후 마을 복귀 등으로 보드가 리셋돼도 ensureUserBoard()가
     * 이 값만큼은 "이미 발견한 칸"으로 되살려준다.
     */
    private void markSpecialTileCheckpoint(String userName, int floor, int visited) {
        HashMap<String, Object> cp = new HashMap<>();
        cp.put("userName", userName);
        cp.put("floor", floor);
        cp.put("checkpoint", visited);
        dao.upsertFloorCheckpoint(cp);
    }

    /**
     * /탑랭킹 — "누가 랭커인지는 모르게" 요청대로, 유저명은 절대 조회/노출하지 않고
     * 서버 전체에서 각 항목별 최고 수치만 익명으로 보여준다(1위 목록이 아니라 "기록판"에 가까움).
     * PP는 값+단위(EXT)가 섞여있어 SQL MAX로 못 비교하므로(예: 9999 vs 1a는 1a가 더 큼)
     * 전체를 가져와 PP.compare()로 비교 -- 유저 수가 많지 않아 성능 문제 없음.
     * [2026-09-08] "누가 세운 기록인지 모르지만, 본인인 경우는 (me)라고 표기해달라" 요청 --
     * 다른 사람 기록은 여전히 완전 비공개(유저명 조회 자체를 안 함), 딱 이 요청을 보낸 본인의
     * 수치만 서버 최고기록과 같은지(동타 포함, >=) 비교해서 같으면 그 줄에만 "(me)"를 붙인다.
     */
    @Override
    public String ranking(String userName) {
        int maxFloor = dao.selectMaxFloorReached();
        int maxKill = dao.selectMaxTotalKillCount();
        int maxAch = dao.selectMaxAchievementCount();
        int maxExplored = dao.selectMaxFullyExploredCount();
        int maxCompanion = dao.selectMaxCompanionCount();
        int maxCompanionGrade = dao.selectMaxCompanionGrade();
        int maxEquip = dao.selectMaxEquipCount();
        int maxEquipGrade = dao.selectMaxEquipGrade();

        PP maxPp = PP.fromPP(0);
        for (HashMap<String, Object> row : dao.selectAllUserPp()) {
            PP v = PP.of(((Number) row.get("PP_VALUE")).doubleValue(), strVal(row.get("PP_EXT"), ""));
            if (v.compare(maxPp) > 0) maxPp = v;
        }

        // 본인 수치 -- 미등록/조회 실패 시 전부 0/빈 값으로 둬서 (me) 표시가 안 붙게만 하고
        // 그 외엔 정상 진행(랭킹판 자체는 로그인 여부와 무관하게 항상 보여준다).
        HashMap<String, Object> mineP = userName == null || userName.trim().isEmpty() ? null : dao.selectUserProgress(userName);
        int mineFloor = 0, mineKill = 0, mineAch = 0, mineExplored = 0;
        int mineCompanion = 0, mineCompanionGrade = 0, mineEquip = 0, mineEquipGrade = 0;
        PP minePp = PP.fromPP(0);
        if (mineP != null) {
            mineFloor = intVal(mineP.get("MAX_FLOOR_REACHED"), 0);
            mineKill = intVal(mineP.get("TOTAL_KILL_COUNT"), 0);
            mineAch = dao.selectUserAchievements(userName).size();
            mineExplored = dao.countFullyExploredFloors(userName);
            mineCompanion = dao.countUserCompanions(userName);
            for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                mineCompanionGrade = Math.max(mineCompanionGrade, intVal(c.get("GRADE"), 0));
            }
            mineEquip = dao.countUserEquip(userName);
            for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
                mineEquipGrade = Math.max(mineEquipGrade, intVal(e.get("GRADE"), 0));
            }
            minePp = PP.of(numVal(mineP.get("TOTAL_PP_EARNED_VALUE"), 0), strVal(mineP.get("TOTAL_PP_EARNED_EXT"), ""));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("┌────────────────┐").append(NL);
        sb.append(" 🏆 시즌5 서버 전체 기록").append(NL);
        sb.append("└────────────────┘").append(NL);
        sb.append("(누가 세운 기록인지는 비공개입니다 -- 본인 기록만 (me)로 표시)").append(NL).append(NL);
        sb.append("🪜 최고 도달 층: ").append(maxFloor).append("층").append(meTag(mineFloor, maxFloor)).append(NL);
        sb.append("⚔️ 최다 누적 처치: ").append(maxKill).append("마리").append(meTag(mineKill, maxKill)).append(NL);
        sb.append("🏅 최다 업적 보유: ").append(maxAch).append("개").append(meTag(mineAch, maxAch)).append(NL);
        sb.append("🗺️ 최다 완전탐사: ").append(maxExplored).append("개 층").append(meTag(mineExplored, maxExplored)).append(NL);
        sb.append("👥 최다 동료 보유: ").append(maxCompanion).append("명").append(meTag(mineCompanion, maxCompanion)).append(NL);
        sb.append("✨ 최고 동료 등급: ★").append(maxCompanionGrade).append(meTag(mineCompanionGrade, maxCompanionGrade)).append(NL);
        sb.append("🎽 최다 장비 보유: ").append(maxEquip).append("개").append(meTag(mineEquip, maxEquip)).append(NL);
        sb.append("💎 최고 장비 등급: ★").append(maxEquipGrade).append(meTag(mineEquipGrade, maxEquipGrade)).append(NL);
        sb.append("💰 최다 누적 PP: ").append(maxPp.format())
          .append((PP.toBaseValue(maxPp) > 0 && minePp.compare(maxPp) >= 0) ? " (me)" : "");
        return sb.toString();
    }

    /** ranking() 전용 -- max가 0(아직 아무도 없음)이 아니고 mine이 max 이상(동타 포함)이면 " (me)". */
    private String meTag(int mine, int max) {
        return (max > 0 && mine >= max) ? " (me)" : "";
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

    private double numVal(Object o, double def) {
        if (o == null) return def;
        return ((Number) o).doubleValue();
    }

    private String strVal(Object o, String def) {
        return o == null ? def : o.toString();
    }

    /**
     * 닉네임(부분 입력 가능)으로 실제 유저명을 찾는다("/탑현황 닉네임"과 동일 패턴, /탑업적
     * 닉네임 조회에도 재사용). 정확히 일치하는 유저가 있으면 그대로, 없으면 앞부분이 일치하는
     * 후보 중 사전순 첫 번째(USER_NAME 오름차순, 여러 명이면 후보 안내 없이 조용히 첫 번째로).
     * 아무도 없으면 null.
     */
    private String resolveTargetUser(String targetQuery) {
        String q = targetQuery.trim();
        HashMap<String, Object> exact = dao.selectUserProgress(q);
        if (exact != null) return q;
        List<String> matches = dao.selectS5UserSearch(q);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public String resolveUserName(String targetQuery) {
        return resolveTargetUser(targetQuery);
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
            String resolved = resolveTargetUser(targetQuery);
            if (resolved == null) {
                return "🔍 '" + targetQuery.trim() + "' 로 시작하는 유저를 찾을 수 없습니다.";
            }
            target = resolved;
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
            // "보드위치에 현재탐사율/최고탐사율도 보여달라" 요청으로 추가 -- 이번 원정에서 실제로
            // 발견(방문)한 칸 수 기준 현재탐사율과, 마을 복귀로 리셋되어도 남아있는 역대 최고기록을
            // 같이 보여준다(둘 다 %, 분모가 0이면 0%로 방어).
            int visitedNow = dao.countTileVisits(target, floor);
            int curPct = tileCount > 0 ? (visitedNow * 100 / tileCount) : 0;
            HashMap<String, Object> best = dao.selectUserFloorBest(target, floor);
            int bestVisited = best == null ? 0 : intVal(best.get("BEST_VISITED_COUNT"), 0);
            int bestTileCount = best == null ? 0 : intVal(best.get("TILE_COUNT"), 0);
            int bestPct = bestTileCount > 0 ? (bestVisited * 100 / bestTileCount) : Math.max(curPct, 0);
            boolean fullyExplored = best != null && "Y".equals(strVal(best.get("FULLY_EXPLORED_YN"), "N"));
            sb.append("탐사율: 현재 ").append(curPct).append("% / 최고 ").append(bestPct).append("%")
              .append(fullyExplored ? " ✅완전탐사" : "").append(NL);
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
                PP perHour = perKill.multiply(AUTO_HUNT_KILLS_PER_HOUR * floorPpMultiplier(huntFloor));
                sb.append(" (").append(huntFloor).append("층 기준, 미접속 시 시간당 약 ").append(perHour.format()).append(" PP)");
            }
        }
        sb.append(NL);
        // "자동사냥이 지금 층 기준으로 도는지" 헷갈린다는 신고로 추가 -- 이 층에서 몇 마리째인지
        // 보여줘서 10마리를 다 채워야 켜진다는 걸 명확히 한다. 단, 이미 켜져 있으면 이 카운터는
        // 더 이상 안 오르고(위 resolveCombatTurn 참고) 의미도 없으므로 표시 자체를 생략한다
        // (계속 표시하면 "숫자가 이상하게 안 늘어난다"는 오해를 삼).
        if (!autoHuntOn && floor % 10 >= 1 && floor % 10 <= 8) {
            sb.append("이 층 처치: ").append(intVal(p.get("KILL_COUNT_CUR"), 0)).append("/10 (자동사냥 적용까지)").append(NL);
        }
        sb.append("누적 처치: ").append(intVal(p.get("TOTAL_KILL_COUNT"), 0)).append("마리").append(NL);
        PP totalEarned = PP.of(numVal(p.get("TOTAL_PP_EARNED_VALUE"), 0), strVal(p.get("TOTAL_PP_EARNED_EXT"), ""));
        sb.append("누적 획득 PP: ").append(totalEarned.format()).append(NL);
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

    /**
     * 파티 시너지 판정("3캐릭부터만 효과가 나도록" 요청) -- 파티가 정확히 3명이고, 그 3명이
     * 전부 같은 직업(모노 조합)이거나 전부 다른 직업(균형 조합, "레인보우")일 때만 발동한다.
     * 3명 중 2명만 같은 직업(예: 전사2+마법사1)이거나 파티가 3명 미만이면 시너지 없음(null).
     * @return "WARRIOR"/"MAGE"/"ROGUE"/"ARCHER"/"PRIEST"(해당 직업 모노 3인) 또는
     *         "RAINBOW"(3직업 전부 다름), 조건 미충족 시 null.
     */
    private String detectPartySynergy(List<HashMap<String, Object>> party) {
        if (party.size() != 3) return null;
        java.util.Set<String> jobs = new java.util.HashSet<>();
        for (HashMap<String, Object> c : party) jobs.add(strVal(c.get("CLASS"), "WARRIOR"));
        if (jobs.size() == 1) return jobs.iterator().next();
        if (jobs.size() == 3) return "RAINBOW";
        return null;
    }

    /** 시너지 발동 안내 한 줄(없으면 빈 문자열). 매 전투 턴마다 붙여서 지금 켜져 있는지 알 수 있게 함. */
    private String synergyAnnounce(String synergy) {
        if (synergy == null) return "";
        switch (synergy) {
            case "WARRIOR": return "🛡️ 전사 3인조 시너지! 도발 확률 상승, 파티 전체 반격 피해 10%↓" + NL;
            case "MAGE":    return "🔮 마법사 3인조 시너지! 스턴 확률 상승, 스턴 시 피해 +20%" + NL;
            case "ROGUE":   return "🗡️ 도적 3인조 시너지! PP 훔치기 확률 상승, 훔친 PP량 2배" + NL;
            case "ARCHER":  return "🏹 궁수 3인조 시너지! 파티 전체 공격력 +30%" + NL;
            case "PRIEST":  return "✨ 도사 3인조 시너지! 보호막량 2배" + NL;
            case "RAINBOW": return ""; // 균형 파티 시너지 멘트 제거 요청(효과 자체는 유지)
            default:        return "";
        }
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
        sb.append("/층내려가기 (/층다운)").append(NL);
        sb.append("/탑내려가기 (/탑다운)").append(NL);
        sb.append("/탑올라가기 (/탑업)").append(NL);
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
        sb.append("/탑랭킹").append(NL);
        sb.append(NL);

        sb.append("[상세 설명]").append(NL);
        sb.append(NL);
        sb.append("[이동/전투] (웹 '보드' 탭)").append(NL);
        sb.append("/주사위 (/ㅈㅅㅇ, /ㅈ) : 보드 이동(평소) 또는 몬스터 공격(전투 중)").append(NL);
        sb.append("/층변경 N (/층이동 N) : 현재 구간 내 N번째 층으로 이동. N=0(마을)~9(보스), 전투 중이면 도망 처리").append(NL);
        sb.append("  ※ 계단(STAIRS) 칸을 밟으면 다음 층으로 갈 '자격'만 생기고, 실제 이동은 이 명령어를 입력해야 합니다.").append(NL);
        sb.append("  ⚠️ 사냥터층에서 0층(마을, /층변경 0)으로 가면 방금 있던 층의 탐사맵(보드 위치+발견기록)이 초기화됩니다. 원정 중엔 끝까지 밀고 올라가세요!").append(NL);
        sb.append("  ⚠️ 보스를 처치해 다음 10층 구간으로 넘어가면 그 구간 사냥터층(전투/탐사)으로는 다시 못 돌아갑니다(편도 진행, 마을은 예외 — 아래 /탑내려가기 참고).").append(NL);
        sb.append("/층내려가기 (별칭: /층다운) : 지금 있는 구간 안에서 바로 아래 한 층으로 이동(예: 28층 → 27층). 이미 그 구간 마을이면 실패(대신 /탑내려가기 사용). 전투 중이면 도망 처리(/층변경과 동일)").append(NL);
        sb.append("/탑내려가기 (별칭: /탑다운) : 마을에서만 사용 가능, 바로 아래 10층 구간의 마을로 이동(예: 20층 마을 → 10층 마을). 사냥터층은 거치지 않고 마을끼리만 이동하며, 몇 번이든 반복 가능").append(NL);
        sb.append("  💡 구간 앞부분(1~4층 위치)에서 파티가 여러 번 전멸하면, 스탯/장비를 더 준비하고 오라고 /탑내려가기를 자동으로 안내해줍니다.").append(NL);
        sb.append("/탑올라가기 (별칭: /탑업) : 마을에서만 사용 가능, 이 구간 보스를 이미 처치했으면 바로 위 10층 구간의 마을로 이동(예: 10층 마을 → 20층 마을). /탑내려가기의 대칭 기능").append(NL);
        sb.append("/탑현황 [닉네임] (별칭: /탑정보, /ㅌㅎㅎ, /ㅌㅈㅂ) : 현재 층/보드 위치/PP/상태/자동사냥 조회. 닉네임을 붙이면 다른 유저 조회(앞부분만 입력해도 검색됨)").append(NL);
        sb.append(NL);

        sb.append("[동료] (웹 '파티' 탭) — 파티 편성/해제는 전투 중이 아니면 어디서든 가능").append(NL);
        sb.append("/파티편성 (별칭: /탑편성, /탑동료, /탑파티, /ㅌㅍㅅ, /ㅌㄷㄹ, /ㅌㅍㅌ) : 보유 동료 목록 + 파티 편성 현황 조회").append(NL);
        sb.append("/파티편성 N : 목록 N번째 동료를 파티에 편성/해제 (전투 중이 아니면 어디서든)").append(NL);
        sb.append("/동료가리기 N : 목록 N번째 동료를 /파티편성 텍스트 목록에서 숨김/숨김해제(웹 화면엔 항상 표시)").append(NL);
        sb.append(NL);

        sb.append("[파티 시너지] 전투마다 자동 판정, 파티가 정확히 3명이고 아래 조건을 만족해야 발동(2명만 겹치면 발동 안 함)").append(NL);
        sb.append("전사★★★ : 도발 확률 상승, 파티 전체 반격 피해 10%↓").append(NL);
        sb.append("마법사★★★ : 스턴 확률 상승, 스턴 시 피해 +20%").append(NL);
        sb.append("도적★★★ : PP훔치기 확률 상승, 훔친 PP량 2배").append(NL);
        sb.append("궁수★★★ : 파티 전체 공격력 +30%").append(NL);
        sb.append("도사★★★ : 보호막량 2배").append(NL);
        sb.append(NL);

        sb.append("[동료 성급 특수효과] ★5/★6 동료는 그 직업에 완전히 새로운 개인 효과가 생김(시너지와 별개, 중복 적용)").append(NL);
        sb.append("전사 : ★5 도발 확률 +10%p, ★6 +20%p(도발 성공 시 받는 피해도 20%↓)").append(NL);
        sb.append("마법사 : ★5/★6 스턴 2턴 지속(다음 턴 반격까지 막음), ★6은 발동 확률도 +15%p").append(NL);
        sb.append("도적 : ★5 반격 대상이 되면 30% 확률로 회피(피해 0), ★6 45%").append(NL);
        sb.append("궁수 : ★5 몬스터 방어력 50% 무시(관통), ★6 100% 무시(완전 관통)").append(NL);
        sb.append("도사 : ★5 동료가 쓰러지면 25% 확률로 즉시 부활(HP30%), ★6 40% 확률(HP50%)").append(NL);
        sb.append(NL);

        sb.append("[상점] (웹 '상점' 탭) — 뽑기는 마을이 아니어도 아무 층에서나 가능").append(NL);
        sb.append("/동료뽑기N [10] : 아래 번호의 계약서로 동료 뽑기(뒤에 10을 붙이면 10연속), 스탯도 함께 표시. 번호는 반드시 붙여써야 함(예: /동료뽑기1) — 번호 없이 /동료뽑기만 치면 안 뽑히고 등급별 안내만 나옴").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("COMPANION", 999), unlocked));
        sb.append("/장비뽑기N [10] : 아래 번호의 보물상자로 장비 뽑기(뒤에 10을 붙이면 10연속), 스탯 보너스도 함께 표시. 번호는 반드시 붙여써야 함(예: /장비뽑기1) — 번호 없이 /장비뽑기만 치면 안 뽑히고 등급별 안내만 나옴").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("EQUIP", 999), unlocked));
        sb.append("/주사위구매 [N] : 해금된 주사위 목록 확인 / N번 장착").append(NL);
        sb.append("/스탯구매 [공격력|최소공격력|체력] : 스탯 강화 현황 확인 / 구매").append(NL);
        sb.append(NL);

        sb.append("[장비] (웹 '장비' 탭)").append(NL);
        sb.append("/장비목록 : 보유 장비 조회 (미착용은 번호 + 스탯 보너스, 착용중인 건 누가 끼고 있는지 표시)").append(NL);
        sb.append("/장비장착 [N] [M] : 인자 없이 입력하면 미착용 장비 번호·파티원 번호를 먼저 안내. N=미착용 장비 번호, M=파티원 번호(생략 시 같은 직업 자동탐색)").append(NL);
        sb.append("/장비합성 N : N번째 장비 포함 동일 직업/부위/등급 미착용 장비 3개를 상위 등급 1개로 합성 (★6 불가, /장비목록의 [미착용] 번호 기준)").append(NL);
        sb.append("/장비해제 M (별칭: /탑해제, /ㅈㅂㅎㅈ, /ㅌㅎㅈ) : M번째 파티원이 착용 중인 장비(투구/무기/갑옷) 전부를 한 번에 해제").append(NL);
        sb.append(NL);

        sb.append("[업적] (웹 '업적' 탭)").append(NL);
        sb.append("/탑업적 [닉네임] (별칭: /ㅌㅇㅂ, /ㅌㅇㅈ) : 달성한 업적 이름만 조회 (닉네임 붙이면 다른 유저도)").append(NL);
        sb.append(NL);

        sb.append("[랭킹]").append(NL);
        sb.append("/탑랭킹 (별칭: /ㅌㄹㅋ) : 서버 전체 최고기록 조회 (최고층/누적처치/업적수 등, 누가 세운 기록인지는 비공개)").append(NL);
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

    /**
     * "번호 사용법을 헷갈려한다" 요청 -- 번호 없이 bare로 /동료뽑기, /장비뽑기를 치면 이 안내를
     * 먼저 보여준 다음(아래 gachaCompanion/gachaEquip에서 이어붙임), 기존 정책대로 1번을 그대로
     * 구매까지 진행한다("설명하고, 없으면 1번 사줄지 물어본 다음 사준다" 요청 -- 이 봇은 대화
     * 상태를 유지하지 않아 실제로 되묻지는 못하므로, 안내와 함께 기본값(1번) 구매를 그 자리에서
     * 바로 진행하는 걸로 대신함).
     */
    private String gachaTierGuideText(String userName, String gachaType) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        String cmd = "COMPANION".equals(gachaType) ? "/동료뽑기" : "/장비뽑기";
        StringBuilder sb = new StringBuilder("📖 ").append(cmd).append("N 번호 안내").append(NL);
        // "실제 칠 명령어 형태 그대로 보여달라" 요청 -- "N. 이름" 대신 "/동료뽑기N : 이름"
        int displayIdx = 1;
        for (HashMap<String, Object> g : dao.selectGachaList(gachaType, 999)) {
            String name = strVal(g.get("GACHA_NAME"), "?");
            int unlockFloor = intVal(g.get("UNLOCK_FLOOR"), 0);
            PP cost = PP.of(((Number) g.get("COST_VALUE")).doubleValue(), strVal(g.get("COST_EXT"), ""));
            boolean isUnlocked = unlocked >= unlockFloor;
            sb.append(cmd).append(displayIdx++).append(" : ").append(name)
              .append(" (").append(unlockFloor).append("층~, ").append(cost.format()).append(" PP)")
              .append(isUnlocked ? "" : " 🔒잠김").append(NL);
        }
        sb.append("(뒤에 10을 붙이면 10연속, 예: ").append(cmd).append("2 10)").append(NL);
        sb.append("번호 없이 치면 1번을 바로 구매합니다. 이번 결과 👇").append(NL);
        return sb.toString();
    }

    @Override
    public String gachaTierGuide(String userName, String gachaType) {
        return gachaTierGuideText(userName, gachaType);
    }

    /** /동료뽑기·/장비뽑기 N에서 쓰는 표시번호(1부터, UNLOCK_FLOOR 순)를 실제 GACHA_ID로 변환. 없으면 null. */
    private Integer resolveGachaId(String gachaType, int displayIdx) {
        List<HashMap<String, Object>> list = dao.selectGachaList(gachaType, 999);
        if (displayIdx < 1 || displayIdx > list.size()) return null;
        return intVal(list.get(displayIdx - 1).get("GACHA_ID"), 0);
    }

    // [2026-09-06 오탐 수정] "은용" 계정이 실제로는 매크로가 아닌데 정지된 사고로 확인 --
    // 원인은 LAST_REQUEST_INTERVAL_SEC를 초 단위로 반올림(버림)해서 저장하다 보니, 실제
    // 간격이 0.3초든 0.9초든 전부 "0초"로 뭉개져서 매번 "이전과 완전히 같은 간격"으로
    // 오판된 것 -- 웹 UI가 액션 후 바로 상태를 다시 불러오는 이중 요청이나 더블탭/새로고침
    // 몇 번만 겹쳐도 쉽게 15연속을 채워버림. 사람이 못 내는 규칙성은 보통 "초 단위로 몇 초씩
    // 딱딱 맞아떨어지는" 느린 패턴(팔세쪽있음 사례: ~4초 간격)이지, 1초 미만의 순간적인
    // 겹침이 아니므로 최소 간격 하한(MACRO_MIN_INTERVAL_SEC)을 둬서 그런 경우는 아예
    // "판정 대상에서 제외"(스트릭을 올리지도, 끊지도 않음)하도록 수정. 안전 마진으로 스트릭
    // 기준치도 15->25로 올림.
    private static final long MACRO_MIN_INTERVAL_SEC = 2;
    // 이 이상 촘촘하지 않으면 애초에 "빠르게 연타"일 뿐 자동화로 보기 어려움
    private static final long MACRO_MAX_INTERVAL_SEC = 20;
    // 이전 간격과 이만큼(초) 이내로 차이나면 "같은 타이머"로 본다
    private static final long MACRO_TOLERANCE_SEC = 1;
    // 이 횟수 연속으로 "같은 타이머"가 감지되면 일시정지
    private static final int MACRO_STREAK_THRESHOLD = 25;

    /**
     * 매크로(자동화 클라이언트) 탐지 + 잠금 처리. 쿨타임 통과 여부와 무관하게 "요청이 들어온
     * 간격" 자체가 여러 번 연속으로 거의 똑같으면(사람은 이렇게 못 침) 일시정지시키고, 이미
     * 일시정지된 계정이 그래도 계속 시도하면 영구정지로 격상한다. 잠기지 않았으면 null 반환.
     */
    private String checkMacroLock(String userName, HashMap<String, Object> p) {
        if ("Y".equals(strVal(p.get("BAN_YN"), "N"))) {
            return "🚫 매크로(자동화) 사용이 확인되어 영구정지된 계정입니다.";
        }

        if ("Y".equals(strVal(p.get("SUSPEND_YN"), "N"))) {
            // 이미 매크로 의심으로 일시정지된 계정이 그래도 계속 시도 -- 영구정지로 격상.
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("banYn", "Y");
            up.put("touchLastRequestDate", true);
            dao.updateUserProgress(up);
            return "🚫 일시정지 상태에서 계속 시도하여 영구정지 처리되었습니다. (관리자 문의)";
        }

        java.util.Date lastReq = (java.util.Date) p.get("LAST_REQUEST_DATE");
        long nowMs = System.currentTimeMillis();
        long curIntervalSec = lastReq == null ? -1 : (nowMs - lastReq.getTime()) / 1000L;

        // 너무 촘촘한(순간적인) 간격은 웹 UI의 이중 요청/더블탭 같은 아티팩트일 수 있어
        // 판정에서 아예 제외한다 -- LAST_REQUEST_DATE 등도 갱신하지 않아 이 호출이 없었던
        // 것처럼 취급(다음 요청은 그 이전의 진짜 간격을 기준으로 비교됨).
        if (curIntervalSec >= 0 && curIntervalSec < MACRO_MIN_INTERVAL_SEC) {
            return null;
        }

        int prevStreak = intVal(p.get("MACRO_STREAK"), 0);
        long prevIntervalSec = (long) intVal(p.get("LAST_REQUEST_INTERVAL_SEC"), -1);
        boolean sameTimer = curIntervalSec >= 0 && curIntervalSec <= MACRO_MAX_INTERVAL_SEC
                && prevIntervalSec >= 0 && Math.abs(curIntervalSec - prevIntervalSec) <= MACRO_TOLERANCE_SEC;
        int newStreak = sameTimer ? prevStreak + 1 : 0;

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("touchLastRequestDate", true);
        up.put("lastRequestIntervalSec", (int) curIntervalSec);
        up.put("macroStreak", newStreak);

        if (newStreak >= MACRO_STREAK_THRESHOLD) {
            up.put("suspendYn", "Y");
            dao.updateUserProgress(up);
            return "🚫 매크로(자동화) 사용이 의심되어 일시정지되었습니다. (관리자 문의 필요)";
        }
        dao.updateUserProgress(up);
        return null;
    }

    // ================================================================
    // /주사위, /ㅈㅅㅇ
    // ================================================================
    @Override
    @Transactional
    public String rollDice(String userName, String channel) {
        boolean brandNew = dao.selectUserProgress(userName) == null;
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) {
            initUser(userName);
            p = dao.selectUserProgress(userName);
        }

        if (brandNew) {
            // 계정이 없던 유저의 첫 /주사위 → 계정만 생성. 진행은 튜토리얼 순서대로 유도.
            return "┌────────────┐" + NL
                    + " 🗼 시즌5 탑 등반기" + NL
                    + "└────────────┘" + NL
                    + "계정을 생성했습니다! 현재 0층 마을이에요." + NL
                    + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기1)";
        }

        // [2026-09-05 신설] 매크로(자동화 클라이언트) 탐지 -- "팔세쪽있음" 계정이 웹 DICE
        // 액션을 20분 넘게 거의 완벽하게 균일한 4초 간격으로 반복하는 걸 실 로그로 확인
        // (사람이 낼 수 없는 규칙성). 쿨타임 통과 여부와 무관하게 "요청이 들어온 간격" 자체를
        // 본다(매크로는 쿨타임 안내를 받아도 그냥 같은 타이머로 계속 찌르기 때문).
        String macroLockMsg = checkMacroLock(userName, p);
        if (macroLockMsg != null) return macroLockMsg;

        java.util.Date lastAction = (java.util.Date) p.get("LAST_DICE_ACTION_DATE");
        String autoHuntMsg = settleAutoHunt(userName, p);
        if (autoHuntMsg == null && lastAction != null
                && !"Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"))
                && (System.currentTimeMillis() - lastAction.getTime()) / 60000L >= 30) {
            autoHuntMsg = "🌙 오랜만이에요! 자동사냥 중인 층이 없습니다.";
        }

        String status = strVal(p.get("STATUS"), "NORMAL");
        String cooldownMsg = checkDiceCooldown(userName, p);
        if (cooldownMsg != null) return prependAutoHunt(autoHuntMsg, cooldownMsg);

        // 하루 굴림 횟수 제한("하루 N번까지만" 요청, 2026-09-07에 채널별 한도로 확장) -- 쿨타임
        // 통과 후, 실제로 이번 액션이 "굴림 1회"로 카운트되기 직전에 확인한다(쿨타임에 막힌
        // 시도는 카운트 안 함). 관리자 테스트 계정(NO_COOLDOWN_YN)은 쿨타임과 동일한 이유로
        // 이 제한도 면제.
        if (!"Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) {
            String limitMsg = checkAndBumpDailyDiceLimit(userName, p, channel);
            if (limitMsg != null) return prependAutoHunt(autoHuntMsg, limitMsg);
        }

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
    private String checkDiceCooldown(String userName, HashMap<String, Object> p) {
        if ("Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) return null; // 특정 유저만 쿨타임 면제(관리자가 직접 부여)
        java.util.Date last = (java.util.Date) p.get("LAST_DICE_ACTION_DATE");
        if (last == null) return null;
        long cooldownSec = intVal(p.get("NEXT_COOLDOWN_SEC"), (int) MOVE_COOLDOWN_SEC);
        long elapsedSec = (System.currentTimeMillis() - last.getTime()) / 1000;
        if (elapsedSec >= cooldownSec) return null;
        long remain = cooldownSec - elapsedSec;
        // "누구한테 온 메시지인지 알 수 있게 닉네임도 넣어달라" 요청
        return "⏳" + userName + "님, 아직 쿨타임입니다! " + remain + "초 후 다시 시도해주세요. (쿨타임 " + cooldownSec + "초)";
    }

    private void touchDiceCooldown(String userName, long nextCooldownSec) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("touchDiceCooldown", true);
        up.put("nextCooldownSec", nextCooldownSec);
        dao.updateUserProgress(up);
    }

    /**
     * 하루 주사위 굴림 횟수(DICE_ROLL_COUNT_TODAY, 채널 무관 공유 카운터)를 확인하고, 한도
     * 안이면 카운트를 올린 뒤 null을 반환한다(통과). DICE_ROLL_DATE가 오늘이 아니면(=날짜가
     * 바뀌었거나 최초 굴림) 카운트를 1로 리셋 -- 별도 배치/스케줄러 없이 "확인하는 시점에
     * 날짜만 비교"하는 방식이라 자정에 뭔가 돌려줄 필요가 없다. 한도를 넘으면 카운트는 그대로
     * 두고 안내 메시지만 반환.
     * [2026-09-07] "웹/카톡 같이 쓰게, 카톡은 200회 더" 요청으로 channel별 한도 분리 --
     * WEB은 DAILY_DICE_LIMIT까지, CHAT(카카오톡)은 거기에 KAKAO_BONUS_DICE를 더한 값까지.
     * 카운터 자체는 채널 구분 없이 하나 그대로 써서, 어느 채널로 얼마씩 섞어 쓰든 "총합이
     * 웹 한도를 넘으면 웹만 차단, 카톡 한도까지 넘으면 전부 차단"이 자연스럽게 성립한다.
     * (SimpleDateFormat은 스레드 안전하지 않아 static 캐시로 못 쓰므로 java.time으로 비교한다.)
     */
    private String checkAndBumpDailyDiceLimit(String userName, HashMap<String, Object> p, String channel) {
        java.util.Date rollDate = (java.util.Date) p.get("DICE_ROLL_DATE");
        int rollCountToday = intVal(p.get("DICE_ROLL_COUNT_TODAY"), 0);
        boolean sameDay = rollDate != null
                && new java.sql.Date(rollDate.getTime()).toLocalDate().equals(java.time.LocalDate.now());
        int curCount = sameDay ? rollCountToday : 0;
        boolean isWeb = "WEB".equals(channel);
        int channelLimit = isWeb ? DAILY_DICE_LIMIT : (DAILY_DICE_LIMIT + KAKAO_BONUS_DICE);
        if (curCount >= channelLimit) {
            if (isWeb) {
                // 웹은 막혔지만 카톡 쪽 보너스가 아직 안 찼으면 그쪽으로 안내.
                if (curCount < DAILY_DICE_LIMIT + KAKAO_BONUS_DICE) {
                    return "🎲 오늘 웹에서 주사위를 " + DAILY_DICE_LIMIT + "번 모두 굴렸습니다. "
                            + "카카오톡에서는 " + (DAILY_DICE_LIMIT + KAKAO_BONUS_DICE - curCount) + "번 더 진행할 수 있어요!";
                }
                return "🎲 오늘 주사위를 " + (DAILY_DICE_LIMIT + KAKAO_BONUS_DICE) + "번 모두 굴렸습니다. 내일 다시 시도해주세요.";
            }
            return "🎲 오늘 카카오톡 한도(" + (DAILY_DICE_LIMIT + KAKAO_BONUS_DICE) + "번)까지 모두 굴렸습니다. 내일 다시 시도해주세요.";
        }
        int newCount = curCount + 1;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceRollCountToday", newCount);
        up.put("touchDiceRollDate", true);
        dao.updateUserProgress(up);
        p.put("DICE_ROLL_COUNT_TODAY", newCount);
        p.put("DICE_ROLL_DATE", new java.util.Date());
        return null;
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
                            + "👉 하급 동료 계약서 무료뽑기를 하세요! (/동료뽑기1)";
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
            return startCombat(userName, p, floor, true, false, false);
        }

        // ── 사냥터 보드: 끝 없이 순환하는 루프. 계단(STAIRS) 칸을 밟으면 다음 층 이동
        //    "자격"만 얻고(MAX_FLOOR_REACHED 갱신), 실제 이동은 /층변경 N 을 직접 입력해야 한다. ──
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);
        HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
        int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);

        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));
        int roll = rollFace(diceMinFor(p), diceMax);
        // [2026-09-08] 마이너스 주사위로 roll이 0/음수까지 나올 수 있게 되면서(탐사 정밀 이동
        // 목적), 원래의 "(curTile+roll-1) % tileCount" 계산은 피제수가 음수일 때 Java의 %가
        // 음수를 그대로 돌려줘서 깨진다 -- +tileCount 보정 후 다시 한 번 %로 항상 [0,tileCount)
        // 범위로 정규화. roll=0이면 제자리, roll<0이면 뒤로 이동.
        int newTile = (((curTile + roll - 1) % tileCount) + tileCount) % tileCount + 1;

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
          .append(NL).append("🗺️ 탐사 현황: ").append(floor).append("층 .. ");
        // "25/25 완전탐사면 그냥 탐사완료라고만 띄워달라" 요청
        if (visited >= tileCount) {
            sb.append("탐사완료");
        } else {
            sb.append(visited).append("/").append(tileCount).append("칸 발견");
        }
        if (visited >= tileCount) {
            // 완전탐사 달성 즉시 (user,floor) 역대기록에 반영 -- 마을 복귀 전이라도 영구 보존
            HashMap<String, Object> best = snapshotFloorBest(userName, floor);
            if (grantAchievement(userName, 25)) {
                sb.append(NL).append("🏆 이 층을 전부 탐험했습니다! [탐험왕] 업적 달성!");
            }
            if ("Y".equals(strVal(best.get("NEWLY_FULL"), "N"))) {
                sb.append(NL).append("🏆 [").append(floor).append("층 완전탐사] 업적 달성! ").append(best.get("VOUCHER_MSG"));
                Object ticketMsg = best.get("BLOCK_TICKET_MSG");
                if (ticketMsg != null) sb.append(NL).append(ticketMsg);
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
            case "COMBAT": {
                // [2026-09-06] 51층 이후(블록6+) 전투칸은 일정 확률로 "중간보스"와 마주친다 --
                // 등장 메시지/맵 표기는 평범한 몬스터와 완전히 동일해서(startCombat 참고) 실제로
                // 붙어보기 전엔 알 수 없다.
                boolean midBossEncounter = blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT;
                // [세 구간 분리 요청] 탐사 현황과 몬스터 등장 사이에 빈 줄
                sb.append(NL).append(startCombat(userName, p, floor, false, false, midBossEncounter));
                break;
            }
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
                List<String> luckyEffectList = new ArrayList<>();
                luckyEffectList.add("PP_BONUS"); luckyEffectList.add("ATK_UP_30"); luckyEffectList.add("DEF_UP_30");
                luckyEffectList.add("ATK_UP_10"); luckyEffectList.add("DEF_UP_10");
                luckyEffectList.add("HEAL_ALL"); luckyEffectList.add("CLEANSE");
                // [2026-09-06, 51층+ 전용] 체력 두배(3턴) / 매턴 공격력만큼 방어막 생성(3턴)
                if (blockNo(floor) >= 6) { luckyEffectList.add("HP_DOUBLE"); luckyEffectList.add("SHIELD_ON_ATK"); }
                String luckyEffect = luckyEffectList.get(RND.nextInt(luckyEffectList.size()));
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
                } else if ("HP_DOUBLE".equals(luckyEffect) || "SHIELD_ON_ATK".equals(luckyEffect)) {
                    // [2026-09-06, 51층+ 전용] ATK_UP/DEF_UP과 같은 컬럼(LUCKY_TURN_LEFT/EFFECT)을
                    // 재사용해 3턴 지속시킨다(덮어쓰기 안내 로직도 동일하게 적용).
                    int prevLuckyTurnLeft2 = intVal(p.get("LUCKY_TURN_LEFT"), 0);
                    String prevLuckyEffect2 = strVal(p.get("LUCKY_EFFECT"), "");
                    boolean overwrote2 = prevLuckyTurnLeft2 > 0 && !prevLuckyEffect2.isEmpty() && !prevLuckyEffect2.equals(luckyEffect);
                    HashMap<String, Object> up2 = new HashMap<>();
                    up2.put("userName", userName);
                    up2.put("luckyTurnLeft", 3);
                    up2.put("luckyEffect", luckyEffect);
                    dao.updateUserProgress(up2);
                    if ("HP_DOUBLE".equals(luckyEffect)) {
                        sb.append("🍀 심상치 않은 럭키 칸! 앞으로 3번 이동하는 동안 체력이 두 배로 버팁니다. (받는 피해 절반)");
                    } else {
                        sb.append("🍀 심상치 않은 럭키 칸! 앞으로 3번 이동하는 동안 매 턴 파티 전원의 공격력만큼 방어막이 추가로 생성됩니다.");
                    }
                    if (overwrote2) {
                        sb.append(NL).append("(기존 럭키 효과는 새 효과로 갱신되어 사라졌습니다)");
                    }
                } else { // PP_BONUS -- 기존 PP칸 보상의 3배
                    PP reward = basePp.multiply(3);
                    addPp(userName, p, reward);
                    PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
                    sb.append("🍀 럭키 칸! ").append(reward.format()).append(" PP 획득! (보유 ").append(curPp.format()).append(" PP)");
                }
                break;
            }
            case "TRAP": {
                // 함정 효과 3종 중 무작위 -- 공격력/방어력 약화는 이후 3번의 보드 이동(위 trapTurnLeft
                // 감소 로직 기준) 동안 지속되는 파티 전체 디버프, PP 손실은 즉시 발동하는 1회성 효과.
                // 실제 적용은 resolveCombatTurn의 파티 공격 루프(ATK_DOWN)와 몬스터 반격 대상
                // 방어력 계산(DEF_DOWN)에서 이뤄진다.
                // [2026-09-06, 51층+ 전용] RESET_TILE(처음 계단칸으로 돌아가기)/SKILL_LOCK(스킬
                // 사용금지 1턴) 2종 추가 -- 51층 미만에서는 나오지 않는다.
                List<String> effectList = new ArrayList<>();
                effectList.add("ATK_DOWN"); effectList.add("DEF_DOWN"); effectList.add("PP_LOSS");
                if (blockNo(floor) >= 6) { effectList.add("RESET_TILE"); effectList.add("SKILL_LOCK"); }
                String effect = effectList.get(RND.nextInt(effectList.size()));
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
                    // "엔터값 넣어달라" 요청 -- 손실 문구와 남은 PP를 줄바꿈으로 분리
                    sb.append("💸 함정에 걸려 소매치기를 당했다! PP ").append(loss.format())
                      .append(" 손실 ").append(NL).append("💰 PP ").append(after.format());
                } else if ("RESET_TILE".equals(effect)) {
                    // 즉시 발동형 1회성 효과(PP_LOSS와 동일 성격) -- TRAP_TURN_LEFT는 건드리지
                    // 않는다. entryTile은 이 층에 도착했을 때 밟은 첫 계단 칸(changeFloor 참고).
                    int entryTile = ufp == null ? 0 : intVal(ufp.get("ENTRY_TILE"), 0);
                    if (entryTile > 0) {
                        HashMap<String, Object> resetUp = new HashMap<>();
                        resetUp.put("userName", userName);
                        resetUp.put("floor", floor);
                        resetUp.put("curTile", entryTile);
                        dao.upsertUserFloorProgress(resetUp);
                        sb.append("🕳️ 함정에 걸렸다! 알 수 없는 힘에 이끌려 처음 계단(").append(entryTile).append("번 칸)으로 돌아갔다!");
                    } else {
                        sb.append("🕳️ 함정에 걸렸다! 무언가 되돌리려 했지만... 아무 일도 일어나지 않았다.");
                    }
                } else if ("SKILL_LOCK".equals(effect)) {
                    // "1턴"은 이동 횟수가 아니라 다음 전투 1회(resolveCombatTurn 참고에서
                    // 즉시 소모) -- 여기서는 플래그만 걸어둔다.
                    up.put("trapTurnLeft", 1);
                    up.put("trapEffect", effect);
                    dao.updateUserProgress(up);
                    sb.append("🕳️ 함정에 걸렸다! 다음 전투 1턴 동안 파티의 직업별 특수 스킬을 쓸 수 없습니다.");
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
                    PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
                    sb.append("💎 보물상자를 발견했다! ").append(reward.format()).append(" PP 획득! (보유 ").append(curPp.format()).append(" PP)");
                } else {
                    // [정책 변경, 2026-09-02] 예전엔 등급 무관 범용 권(COMPANION_VOUCHER/
                    // EQUIP_VOUCHER)을 줬는데, 범용 권이 "해금 여부 무관하게 아무 등급에나
                    // 쓸 수 있게" 설계돼 있어서 하급 보물상자에서 나온 권으로 최상급(35000
                    // PP짜리) 가챠까지 뚫려버리는 문제가 있었다(신고로 확인). 범용 권 개념
                    // 자체를 없애고 등급락 권(T1~T4)만 지급하도록 변경.
                    // [정책 변경, 2026-09-05] 그 다음엔 항상 T1(하급)만 줬는데, "층에 맞는
                    // 뽑기권이 지급돼야 한다"(0~30층 하급, 30층~ 중급 ...)는 요청으로, 지금
                    // 해금된 최고 등급(현재 UNLOCKED_BLOCK 기준)에 맞춰 준다 -- 절대 그보다
                    // 높은 등급은 못 받으므로 위 "최상급 뚫림" 버그는 여전히 재발하지 않는다.
                    boolean companionVoucher = RND.nextBoolean();
                    String voucherGachaType = companionVoucher ? "COMPANION" : "EQUIP";
                    int unlockedBlockNow = intVal(p.get("UNLOCKED_BLOCK"), 0);
                    int tier = dao.selectGachaList(voucherGachaType, unlockedBlockNow).size();
                    if (tier < 1) tier = 1;
                    if (tier > 4) tier = 4;
                    if (companionVoucher) {
                        up.put("companionVoucherT" + tier, intVal(p.get("COMPANION_VOUCHER_T" + tier), 0) + 1);
                    } else {
                        up.put("equipVoucherT" + tier, intVal(p.get("EQUIP_VOUCHER_T" + tier), 0) + 1);
                    }
                    sb.append("💎 보물상자를 발견했다! ").append(companionVoucher ? "동료" : "장비")
                      .append(" 무료뽑기 1회권(").append(COMPANION_TIER_NAME[tier - 1]).append(") 획득! (다음 ").append(companionVoucher ? "/동료뽑기" : "/장비뽑기")
                      .append(" 시 자동 적용)");
                }
                dao.updateUserProgress(up);
                break;
            }
            case "SPECIAL":
                sb.append(handleSpecialTile(userName, floor, visited));
                break;
            case "ELITE":
                sb.append(NL).append(startCombat(userName, p, floor, false, true, false)); // 강화몹: 보스 아님, 강화만
                break;
            case "STAIRS_UP": {
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
                sb.append("🪜⬆️ 위로 향하는 계단을 발견했습니다! ").append(nextFloor).append("층으로 갈 수 있어요.").append(NL)
                  .append("👉 /층변경 ").append(nextFloor % 10).append(" 으로 이동하세요.");
                break;
            }
            case "STAIRS_DOWN":
                // 내려가는 방향은 이미 가본 층이라 별도 해금 로직 없이 안내만(이전 층/마을로는
                // 이미 자유롭게 /층변경 가능). 위/아래 계단을 구분한 건 층 이동 시 어느 칸에
                // 도착하는지 방향을 나누기 위함(changeFloor 참고).
                sb.append("🪜⬇️ 내려가는 계단을 발견했습니다. (/층변경으로 이전 층·마을로 이동 가능)");
                break;
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

    private String handleSpecialTile(String userName, int floor, int visited) {
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
        // "51층부터 특수칸이 워프포인트" 요청 -- 이 시점 탐사 칸수를 체크포인트로 저장.
        if (floor >= 51) {
            markSpecialTileCheckpoint(userName, floor, visited);
            sb.append(NL).append("🌀 워프포인트를 발견했다! 지금까지의 탐사 기록(").append(visited).append("칸)이 저장되었다.");
        }
        return sb.toString();
    }

    /**
     * PP 지급의 유일한 통로 -- 전투 처치보상/자동사냥 정산/보물상자/럭키칸/도적 스틸/중복 뽑기
     * 환급 등 모든 PP 획득이 이 함수를 거치므로, 여기서 같이 누적치(TOTAL_PP_EARNED)도 더해두면
     * 소스별로 따로 손댈 필요 없이 "역대 총 획득 PP"를 정확히 추적할 수 있다("/탑현황에 누적PP도
     * 보여달라" 요청으로 신설). 현재 보유고(PP_VALUE, 쓰면 줄어듦)와는 별개의 값.
     */
    private void addPp(String userName, HashMap<String, Object> p, PP amount) {
        PP cur = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        PP result = cur.add(amount);
        PP earnedCur = PP.of(numVal(p.get("TOTAL_PP_EARNED_VALUE"), 0), strVal(p.get("TOTAL_PP_EARNED_EXT"), ""));
        PP earnedResult = earnedCur.add(amount);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("ppValue", result.getValue());
        up.put("ppExt", result.getUnit());
        up.put("totalPpEarnedValue", earnedResult.getValue());
        up.put("totalPpEarnedExt", earnedResult.getUnit());
        dao.updateUserProgress(up);
        p.put("PP_VALUE", result.getValue());
        p.put("PP_EXT", result.getUnit());
        p.put("TOTAL_PP_EARNED_VALUE", earnedResult.getValue());
        p.put("TOTAL_PP_EARNED_EXT", earnedResult.getUnit());
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

    /**
     * 보유한 동료 무료뽑기권이 있으면 1장 소비하고 true, 없으면 false(비용 정상 차감 필요).
     * gachaId(=COMPANION 가챠 티어 1~4)에 정확히 락된 티어별 권(N층 완전탐사 보상,
     * COMPANION_VOUCHER_T1~4)만 확인한다.
     * [정책 변경] 예전엔 등급 무관 범용 권(COMPANION_VOUCHER)으로 폴백해서 아무 등급에나
     * 쓸 수 있었는데, 그러면 하급 보물상자에서 나온 권 1장으로 최상급(35000 PP) 가챠까지
     * 뚫려버리는 문제가 있었다("팔세쪽있음" 신고로 확인 -- 하급 보물상자 권이 있어서 최상급
     * 상자도 "무료뽑기권으로만 가능"으로 표시됨). 범용 권 개념 자체를 없애고, 기존에 쌓여있던
     * 범용 권 잔량은 전부 T1(하급)로 일괄 이관했다(S5_VOUCHER_TIER_MIGRATION.sql).
     */
    private boolean consumeCompanionVoucher(String userName, HashMap<String, Object> p, int gachaId) {
        if (gachaId < 1 || gachaId > 4) return false;
        String field = "companionVoucherT" + gachaId;
        String column = "COMPANION_VOUCHER_T" + gachaId;
        int tierCur = intVal(p.get(column), 0);
        if (tierCur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, tierCur - 1);
        dao.updateUserProgress(up);
        p.put(column, tierCur - 1);
        return true;
    }

    /** consumeCompanionVoucher와 완전히 동일한 정책/패턴, 장비뽑기용. */
    private boolean consumeEquipVoucher(String userName, HashMap<String, Object> p, int gachaId) {
        if (gachaId < 1 || gachaId > 4) return false;
        String field = "equipVoucherT" + gachaId;
        String column = "EQUIP_VOUCHER_T" + gachaId;
        int tierCur = intVal(p.get(column), 0);
        if (tierCur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, tierCur - 1);
        dao.updateUserProgress(up);
        p.put(column, tierCur - 1);
        return true;
    }

    /**
     * [해금 여부 우회용] 소비하지 않고 "이 gachaId를 무료로 뽑을 수 있는 권이 있는지"만 확인.
     * 원래는 권이 있어도 UNLOCK_FLOOR(그 등급 해금 여부)는 그대로 확인해서, 아직 그 층에
     * 못 간 유저는 티어락 권(N층 완전탐사 보상)이 있어도 못 썼다 -- "이벤트로 중급뽑기권을
     * 뿌려도 30층 전이면 못 쓰는 거 아니냐"는 지적으로 확인된 문제. 권을 보유했다는 것 자체가
     * 이미 그 등급을 쓸 자격을 부여받았다는 뜻이므로(관리자 지급이든 진행도 보상이든), 그
     * gachaId에 정확히 락된 권이 있으면 해금 여부와 무관하게 그 등급 가챠를 시도할 수 있게 한다.
     * (등급 무관 범용 권 폴백은 제거됨 -- 위 consumeCompanionVoucher 주석 참고)
     */
    private boolean hasUsableCompanionVoucher(HashMap<String, Object> p, int gachaId) {
        return gachaId >= 1 && gachaId <= 4 && intVal(p.get("COMPANION_VOUCHER_T" + gachaId), 0) > 0;
    }

    /** hasUsableCompanionVoucher와 완전히 동일한 목적/패턴, 장비뽑기용. */
    private boolean hasUsableEquipVoucher(HashMap<String, Object> p, int gachaId) {
        return gachaId >= 1 && gachaId <= 4 && intVal(p.get("EQUIP_VOUCHER_T" + gachaId), 0) > 0;
    }

    private String startCombat(String userName, HashMap<String, Object> p, int floor, boolean boss, boolean elite, boolean midBoss) {
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), boss ? "Y" : "N");
        if (mon == null) {
            // TBOT_S5_MONSTER_INFO에 이 BLOCK_NO×BOSS_YN 조합 데이터가 없는 경우.
            // 원인 확인용: S5_CHECK_MONSTER_DATA.sql
            return floor + "층(BLOCK " + blockNo(floor) + ") 몬스터 정보가 없습니다 (관리자 문의).";
        }
        // 강화몹(ELITE 칸, 20층대+ 전용): 같은 층 몬스터를 그대로 쓰되 HP/ATK/DEF/PP보상 전부 2배.
        // HP는 여기서 CUR_MONSTER_HP_VALUE에 곱한 값을 바로 저장해두면 끝이지만, ATK/DEF/보상은
        // 매 턴 mon에서 새로 읽어오므로(resolveCombatTurn) CUR_MONSTER_ELITE_YN 플래그를 남겨서
        // 거기서도 계속 2배를 적용하게 한다. [2026-09-06] 중간보스(51층+ 전용, midBoss)는
        // ELITE와 배타적으로 3배 -- eliteMult 변수를 그대로 재사용.
        double eliteMult = elite ? 2.0 : (midBoss ? 3.0 : 1.0);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("status", "IN_COMBAT");
        up.put("curMonsterId", intVal(mon.get("MONSTER_ID"), 0));
        up.put("curMonsterHpValue", ((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult);
        up.put("curMonsterHpExt", strVal(mon.get("HP_EXT"), ""));
        up.put("curMonsterEliteYn", elite ? "Y" : "N");
        up.put("curMonsterMidbossYn", midBoss ? "Y" : "N");

        // [정책 변경, 2026-09-05] "29층 보스가 너무 세다"는 신고로, 블록3+ 보스의 "무시"
        // 스킬(파티원 1명 지목, 그 동료는 전투 내내 보스에게 공격 불가)을 완전히 제거했다.
        // 이제 블록3+ 보스 특수 스킬은 아래 반격 파트의 "기절"(매 턴 확률로 1명 스턴) 하나뿐
        // -- 발동 확률도 30%→20%로 낮춰서 예전보다 확실히 약해졌다. BOSS_IMMUNE_CID는 더 이상
        // 세팅하지 않지만 컬럼/조회/초기화 로직은 과거 진행 중이던 값 정리를 위해 남겨둔다.
        dao.updateUserProgress(up);

        PP fullHp = PP.of(((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult, strVal(mon.get("HP_EXT"), "")).normalize();
        StringBuilder sb = new StringBuilder();
        // [형식 정리 요청] "OO 등장!"을 한 줄에 다 몰아넣지 않고 "등장!" 알림 / 몬스터 이름 /
        // 능력치를 각각 줄로 나눔("능력치" 라벨·콜론도 빼서 더 짧게). [2026-09-06] 중간보스는
        // "맵에는 일반적인 몬스터로 표시되는데" 요청대로 평범한 등장 메시지("👾 등장!")를 그대로
        // 쓰고 강화몹처럼 정체를 미리 알려주는 문구도 없다 -- 실제 스탯(3배)은 아래에 그대로
        // 노출되지만, 정체는 전투 중 스킬 훔치기가 나와야 드러난다.
        sb.append(boss ? "👹 보스 등장!" : elite ? "💪 강화 등장!" : "👾 등장!").append(NL);
        sb.append(floorMonsterName(floor, mon)).append(NL);
        sb.append("⚔️ ").append((int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult))
          .append(" 🛡️ ").append((int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult))
          .append(" ❤️ ").append(fullHp.format()).append(NL);
        if (elite) sb.append("💪 강화몹 -- 스탯/보상 전부 평소의 2배입니다.").append(NL);
        sb.append(NL);
        String buffNote = currentPartyBuffDebuffNote(p);
        if (buffNote != null) sb.append(buffNote).append(NL);
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
        // [2026-09-06] 51층 이후(블록6+) 중간보스(맵에는 평범한 몬스터로 보이지만 COMBAT
        // 칸에서 확률로 등장, startCombat 참고) -- 강화몹(2배)보다 센 3배 배율. elite와
        // midBoss는 서로 배타적(전자는 ELITE 칸, 후자는 COMBAT 칸 전용)이라 eliteMult
        // 변수 하나를 그대로 공유해 쓴다.
        boolean midBoss = "Y".equals(strVal(p.get("CUR_MONSTER_MIDBOSS_YN"), "N"));
        double eliteMult = elite ? 2.0 : (midBoss ? 3.0 : 1.0);
        PP monsterHp = PP.of(((Number) p.get("CUR_MONSTER_HP_VALUE")).doubleValue(), strVal(p.get("CUR_MONSTER_HP_EXT"), ""));
        PP monsterMaxHp = PP.of(((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult, strVal(mon.get("HP_EXT"), "")).normalize();
        // 중간보스가 지난 턴에 전사 스킬을 훔쳐 자기 방어력을 올려뒀으면(아래 미드보스 파트
        // 참고) 이번 파티 공격 턴 1회에만 반영하고 소모한다(1회성 -- 아래 up에서 0으로 정리).
        int monsterDefBuffPct = intVal(p.get("MONSTER_DEF_BUFF_PCT"), 0);
        int monsterDef = (int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult * (1 + monsterDefBuffPct / 100.0));
        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));

        // 함정칸 디버프 / 럭키칸 버프: 남은 이동횟수가 있는 동안 파티 전체에 적용. 럭키칸은
        // ATK_UP_10/ATK_UP_30처럼 세기가 다른 여러 등급이 있어서(luckyEffects 참고) 접두어로
        // 종류를, 끝의 숫자로 퍼센트를 판단한다(luckyEffectPct 참고). 함정은 항상 고정 30%.
        boolean trapAtkDown = intVal(p.get("TRAP_TURN_LEFT"), 0) > 0 && "ATK_DOWN".equals(strVal(p.get("TRAP_EFFECT"), ""));
        boolean trapDefDown = intVal(p.get("TRAP_TURN_LEFT"), 0) > 0 && "DEF_DOWN".equals(strVal(p.get("TRAP_EFFECT"), ""));
        // [2026-09-06, 51층+ 전용 함정] 스킬사용금지 -- 기존 ATK_DOWN/DEF_DOWN과 같은
        // TRAP_EFFECT/TRAP_TURN_LEFT 컬럼을 그대로 재사용하되, "1턴"은 이동 횟수가 아니라
        // 실제 전투 1회(resolveCombatTurn 1회 호출)를 의미하므로 여기서 소모 여부를 판단하고
        // 아래 up에서 즉시 TRAP_TURN_LEFT=0으로 꺼버린다(이동 기반 자동 감소를 기다리지 않음).
        boolean skillLocked = intVal(p.get("TRAP_TURN_LEFT"), 0) > 0 && "SKILL_LOCK".equals(strVal(p.get("TRAP_EFFECT"), ""));
        if (skillLocked) {
            // 이번 전투 1회로 소모 -- 이 전투가 승리/전멸/지속 중 무엇으로 끝나든 상관없이
            // 항상 정확히 한 번만 적용되도록, 아래 분기별 up과 별개로 여기서 바로 꺼버린다.
            HashMap<String, Object> skillLockUp = new HashMap<>();
            skillLockUp.put("userName", userName);
            skillLockUp.put("trapTurnLeft", 0);
            dao.updateUserProgress(skillLockUp);
        }
        String luckyEffectNow = strVal(p.get("LUCKY_EFFECT"), "");
        boolean luckyAtkUp = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && luckyEffectNow.startsWith("ATK_UP");
        boolean luckyDefUp = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && luckyEffectNow.startsWith("DEF_UP");
        // [2026-09-06, 51층+ 전용 럭키] 체력 두배(3턴) -- 실제로 CUR_HP_VALUE를 다시 계산해
        // 저장하기보다, 기존 ATK_UP/DEF_UP과 같은 구조로 "받는 피해 절반"으로 구현해 체력을
        // 두 배로 버티는 것과 같은 효과를 낸다(아래 반격 피해 계산에서 사용).
        boolean luckyHpDouble = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && "HP_DOUBLE".equals(luckyEffectNow);
        // [2026-09-06, 51층+ 전용 럭키] 매턴 공격력만큼 방어막 생성(3턴) -- 파티 공격 루프에서
        // 생존한 동료 각자의 공격력만큼 shieldPool에 추가로 쌓아준다(도사 실드와 별개로 가산).
        boolean luckyShieldOnAtk = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0 && "SHIELD_ON_ATK".equals(luckyEffectNow);
        double luckyMult = 1.0 + luckyEffectPct(luckyEffectNow) / 100.0;

        // [2026-09-05] 20층 이후(블록3+) 보스 전용 스킬: 기절(아래 반격 턴에서 20% 확률 발동)
        // -- 원래 있던 "무시" 스킬은 보스가 너무 세다는 신고로 제거, 기절 확률도 30%->20%로 완화.
        boolean lateBoss = "Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 3;

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

        // 파티 시너지(직업 3인조/균형 3인조, "3캐릭부터만" 요청) -- 매 턴 판정해서 파티 구성을
        // 바꾸면 바로바로 반영되게 한다. 발동 중이면 매 턴 안내 한 줄을 붙여서 켜져 있는지 보여줌.
        String synergy = detectPartySynergy(party);
        sb.append(synergyAnnounce(synergy));

        // 보스 기절 스킬(반격 턴에 걸림, 아래 참고)로 지정된 동료는 이번 공격 턴만 건너뛰고 소모된다.
        int bossStunCid = intVal(p.get("BOSS_STUN_CID"), 0);
        boolean stunConsumed = false;

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 (직업별 특수효과 포함) ──
        long totalDamage = 0;
        boolean stunned = false;
        boolean executeKill = false;
        int shieldPool = 0;
        // [2026-09-05] ★5/★6 마법사 "2턴 스턴" -- 지난 턴에 걸어둔 배너(MONSTER_STUNNED_YN)가
        // 있으면 이번 턴도 자동으로 스턴 처리하고 소모한다. mageBankNextTurn은 "이번 턴에 새로
        // 배너를 걸었는지" -- 아래에서 이번 턴 결과를 반영해 배너를 다시 쓰거나 지운다.
        boolean incomingBankedStun = "Y".equals(strVal(p.get("MONSTER_STUNNED_YN"), "N"));
        boolean mageBankNextTurn = false;
        if (skillLocked) sb.append("🔒 스킬 봉인 상태! 이번 턴은 파티 특수 스킬(직업별 효과)을 쓸 수 없다.").append(NL);

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
            if ("ARCHER".equals(synergy)) eff[1] = (int) Math.round(eff[1] * 1.3); // 시너지: 궁수3인조 공격력+30%
            // 균형3인조(RAINBOW)의 방어 +10%는 이 배열이 아니라 반격 파트에서 대상(tEff[2])에
            // 직접 적용한다(여기 eff[2]는 "내가 공격할 때" 값이라 방어 보너스는 안 쓰임).
            if ("RAINBOW".equals(synergy)) eff[1] = (int) Math.round(eff[1] * 1.1); // 시너지: 균형3인조 공격 +10%
            if (trapAtkDown) eff[1] = (int) Math.round(eff[1] * 0.7); // 함정: 공격력 30% 약화
            if (luckyAtkUp) eff[1] = (int) Math.round(eff[1] * luckyMult); // 럭키: 공격력 강화
            if (luckyShieldOnAtk) shieldPool += eff[1]; // 럭키: 매턴 공격력만큼 방어막 추가 생성(도사 실드와 별개로 가산)

            // [정책 변경, 2026-09-05] "무시 대상" 스킬 자체를 제거했다("29층 보스가 너무 세다"는
            // 신고 -- bossImmuneCid는 더 이상 세팅되지 않으므로 이 분기는 이제 죽은 코드다).

            // [2026-09-05] ★5/★6 궁수 "관통" 특수효과 -- 몬스터 방어력을 일부/전부 무시
            int effMonsterDef = monsterDef;
            if ("ARCHER".equals(job)) {
                if (grade >= 6) effMonsterDef = 0;                                   // ★6: 방어 완전 무시
                else if (grade >= 5) effMonsterDef = (int) Math.round(monsterDef * 0.5); // ★5: 방어 50% 무시
            }

            int roll = rollFace(diceMinFor(p), diceMax);
            int dmg = Math.max(1, eff[1] * roll - effMonsterDef);
            dmg = Math.max(dmg, eff[3]); // 스탯구매 최소공격력 보정
            totalDamage += dmg;
            // [간결화] 텍스트가 너무 길다는 요청으로, 공격력/범위(전투 시작 전 "OO 등장!" 메시지에
            // 이미 표시됨)는 매 줄마다 반복하지 않고, 직업별 특수효과도 새 줄 대신 같은 줄 끝에
            // 붙여서 파티원 1명당 항상 딱 1줄만 쓰도록 함.
            // [세 구간 분리 요청] 공격 줄에도 현재/최대 HP를 같이 보여줘서, 나중에 "이번턴 남은"
            // 구간의 HP와 바로 비교되게 함.
            // [2026-09-05 멘트 개편] "이름+HP"와 "주사위/데미지"를 한 줄에 몰아넣지 말고 줄을
            // 나눠달라는 요청 -- 이름+HP 줄, 그 아래 굴림 결과 줄로 분리.
            sb.append(jobTag(grade, job, cName)).append(" 💗").append(hp.format()).append("/").append(eff[0]).append(NL)
              .append("🎲").append(roll).append("→").append(dmg).append("dmg");

            // [2026-09-05 신설] ★5/★6 동료 성급 특수효과 -- 시너지와 별개로 "이 동료 개인"의
            // 등급이 높을수록 그 직업 고유 효과가 강해진다. 시너지가 함께 켜져 있으면 둘 다
            // 적용(스택)된다. [2026-09-06] 함정 "스킬사용금지"에 걸려있으면(skillLocked)
            // 이 switch 전체(직업별 특수효과)를 건너뛴다 -- 기본 공격 데미지(위에서 이미 계산)는
            // 그대로 들어간다.
            if (!skillLocked)
            switch (job) {
                case "MAGE": {
                    int stunChance = 20;
                    if ("MAGE".equals(synergy)) stunChance += 20; // 시너지: 마법사3인조
                    if (grade >= 6) stunChance += 15;              // ★6: 확률도 약간 상승
                    if (RND.nextInt(100) < stunChance) {
                        stunned = true;
                        sb.append(" ✨스턴!");
                        // [2026-09-05] ★5/★6 "2턴 스턴" -- 이번 턴은 물론 다음 턴 반격까지
                        // 막아버린다(MONSTER_STUNNED_YN에 배너로 저장해뒀다가 다음 턴 소모).
                        if (grade >= 5) {
                            mageBankNextTurn = true;
                            sb.append("(2턴 지속)");
                        }
                    }
                    break;
                }
                case "ROGUE": {
                    int stealChance = 25;
                    if ("ROGUE".equals(synergy)) stealChance += 10; // 시너지: 도적3인조
                    if (RND.nextInt(100) < stealChance) {
                        double stealMult = 0.1;
                        if ("ROGUE".equals(synergy)) stealMult *= 2.0; // 시너지: 훔친 PP 2배
                        PP steal = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(stealMult * floorPpMultiplier(floor) * eliteMult);
                        addPp(userName, p, steal);
                        sb.append(" 🗡️+").append(steal.format()).append("PP");
                    }
                    // ★5/★6 특수효과(회피)는 도적이 반격 "대상"이 됐을 때 발동하므로 아래
                    // resolveCombatTurn 반격 파트에서 처리한다(공격 턴인 여기와는 무관).
                    break;
                }
                case "ARCHER": {
                    // ★5/★6 특수효과(관통, 몬스터 방어 무시)는 위 dmg 계산에서 이미 처리했다.
                    if (PP.toBaseValue(monsterHp) <= PP.toBaseValue(monsterMaxHp) * 0.1 && RND.nextInt(100) < 40) {
                        executeKill = true;
                        sb.append(" 🏹즉사!");
                    }
                    break;
                }
                case "PRIEST": {
                    // [명확화 요청] "실드를 누구한테 주는지 안 보인다"는 지적으로, 도사 자신의
                    // 줄에는 더 이상 🛡️+N을 안 찍는다 -- 실제로 이번 반격을 막아준 대상이
                    // 정해진 뒤(아래 resolveCombatTurn의 반격 파트) 그 동료 자신의 줄에 붙여준다.
                    int shieldRoll = rollFace(diceMinFor(p), diceMax);
                    int shieldAmt = Math.max(0, eff[1] * shieldRoll);
                    if ("PRIEST".equals(synergy)) shieldAmt = (int) Math.round(shieldAmt * 2.0); // 시너지: 도사3인조 2배
                    shieldPool += shieldAmt;
                    // ★5/★6 특수효과(부활)는 반격으로 동료가 사망할 때 발동하므로 아래 반격
                    // 파트에서 처리한다.
                    break;
                }
                default:
                    break;
            }
            sb.append(NL);
        }
        // "파티 합공 총 데미지도 보여달라" 요청 -- 개별 줄만으로는 한 번에 얼마나 몰아쳤는지
        // 암산해야 해서, 공격 줄들 바로 아래에 합계를 한 줄 더 보여준다.
        if (totalDamage > 0) sb.append("총 ").append(totalDamage).append("dmg로 공격!").append(NL);

        // 중간보스가 지난 턴에 도사 스킬을 훔쳐 자신에게 보호막을 둘렀으면(아래 미드보스
        // 파트 참고), 이번 파티 공격에서 그만큼 먼저 흡수하고 소모한다(1회성).
        int monsterShieldValue = intVal(p.get("MONSTER_SHIELD_VALUE"), 0);
        if (monsterShieldValue > 0 && totalDamage > 0) {
            long absorbedByMonster = Math.min(monsterShieldValue, totalDamage);
            totalDamage -= absorbedByMonster;
            sb.append("🛡️ ").append(eliteMonsterName(floor, mon, elite)).append("의 보호막이 ")
              .append(absorbedByMonster).append(" 피해를 흡수했다! (이후 ").append(totalDamage).append("dmg)").append(NL);
        }

        // 지난 턴에 걸린 "2턴 스턴" 배너가 있으면 이번 턴도 반격을 못 하게 한다(이번 턴에 새
        // 마법사가 또 성공시켰는지와 무관하게 항상 적용).
        if (incomingBankedStun) {
            stunned = true;
            sb.append("💫 지난 턴 스턴이 아직 이어지고 있다!").append(NL);
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
            // 승리했으니 연속 전멸 스트릭 초기화("여러 번 죽으면 /탑내려가기 안내" 판단용)
            up.put("wipeStreakCur", 0);

            // [세 구간 분리 요청] 공격 결과 / 처치·보상 안내를 빈 줄로 나눠서 구분되게 함.
            sb.append(NL).append(eliteMonsterName(floor, mon, elite)).append(" 처치! 🎉").append(NL);

            if (isBoss) {
                int prevBlockBase = floorBlockBase(floor);
                int nextFloor = prevBlockBase + 10;

                // [2026-09-05 보정] "무시 대상" 보스 기믹이 반대로(면역) 동작하던 시기에 29층
                // 보스를 이미 깬 유저는 28층으로 되돌려 새 규칙으로 재도전하게 했다
                // (PENDING_RESTORE_* 컬럼에 원래 진행상황 저장, S5_BOSS29_ROLLBACK.sql 참고).
                // 지금이 바로 그 재도전 클리어(floor==29 && 저장된 값 있음)라면, 평소처럼
                // 다음 구간(30층)으로 보내는 대신 저장해둔 원래 위치로 복구해준다.
                boolean isRestore = floor == 29 && p.get("PENDING_RESTORE_FLOOR") != null;
                int landFloor = isRestore ? intVal(p.get("PENDING_RESTORE_FLOOR"), nextFloor) : nextFloor;
                int landUnlockedBlock = isRestore ? intVal(p.get("PENDING_RESTORE_UNLOCKED_BLOCK"), nextFloor) : nextFloor;
                int landMaxFloorReached = isRestore ? intVal(p.get("PENDING_RESTORE_MAX_FLOOR"), nextFloor + 1) : nextFloor + 1;

                up.put("curFloor", landFloor);
                up.put("unlockedBlock", landUnlockedBlock);
                // 새 구간의 첫 사냥터층은 계단 없이도 바로 층변경 가능해야 함
                up.put("maxFloorReached", landMaxFloorReached);
                up.put("killCountCur", 0); // 새 구간으로 넘어가므로 "이 층 처치수"도 초기화(changeFloor와 동일 이유)
                if (isRestore) {
                    up.put("clearPendingRestore", true);
                    sb.append("👑 보스 격파! (재도전 완료) 예전 진행 상황으로 복구되어 ").append(landFloor).append("층으로 이동합니다.").append(NL);
                    Object pendingAutoHuntFloor = p.get("PENDING_RESTORE_AUTOHUNT_FLOOR");
                    if (pendingAutoHuntFloor != null) {
                        HashMap<String, Object> restoreLog = new HashMap<>();
                        restoreLog.put("userName", userName);
                        restoreLog.put("floor", intVal(pendingAutoHuntFloor, landFloor));
                        dao.upsertAutoHuntLog(restoreLog);
                    }
                } else {
                    sb.append("👑 보스 격파! ").append(nextFloor).append("층 마을로 이동합니다.").append(NL);
                }
                // "새 마을 도착 시 스탯상한/주사위해금/뽑기권해금 등 바뀐 것들을 알려달라" 요청으로 추가
                // (재도전 복구 케이스는 여러 구간을 한 번에 건너뛸 수 있어 landUnlockedBlock까지 전부 훑는다)
                String unlockSummary = newlyUnlockedSummary(prevBlockBase, landUnlockedBlock);
                if (!unlockSummary.isEmpty()) {
                    sb.append("── 새로 해금된 것들 ──").append(NL).append(unlockSummary);
                }
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
                // [버그 수정] 자동사냥이 이미 켜져 있을 때도 KILL_COUNT_CUR가 계속 0~9로
                // 순환(리셋)해서 "이 층 처치 N/10" 수치가 계속 오르내리는 것처럼 보이고,
                // 10마리째마다 "자동사냥 모드 ON" 안내가 쓸데없이 반복 출력되는 문제가
                // 있었다(문의로 확인). 자동사냥은 한 번 켜지면 층 이동 시 자동으로 그 층
                // 기준으로 따라오므로(changeFloor의 동기화 로직 참고), 이미 켜진 뒤에는
                // 이 카운터를 더 건드리지 않는다 -- "이 층 처치 N/10"은 순수하게 "아직
                // 자동사냥이 꺼져 있고, 이 층에서 처음 켜기까지 몇 마리 남았는지"만 의미.
                boolean alreadyAutoHunt = "Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"));
                if (!alreadyAutoHunt) {
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
            }
            dao.updateUserProgress(up);
            addPp(userName, p, reward);
            checkKillAchievements(userName, totalKill - 1, totalKill);
            // "PP 획득 시 현재 보유 PP도 같이 보여달라, 보스전처럼 0PP면 아예 표시하지 말아달라"
            // 요청 -- 보스 몬스터는 PP_PER_KILL_VALUE가 0으로 설정돼 있어 처치해도 파밍 보상이
            // 없는데(업적/해금 보상만 있음), 그동안 "0 PP 획득!"이 그대로 찍혀서 어색했다.
            if (PP.toBaseValue(reward) > 0) {
                // [세 구간 분리 요청] "이번턴 남은" 자리를 처치 시에는 생존 동료 HP 대신 획득 PP로
                // 대체 -- 처치한 마당에 파티 HP를 또 보여줄 필요는 없으므로.
                PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
                sb.append(NL).append(reward.format()).append(" PP 획득! (보유 ").append(curPp.format()).append(" PP)");
            }
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
        // ★5/★6 마법사 2턴 스턴 배너 갱신 -- 이번 턴에 새로 걸렸으면 다음 턴을 위해 Y로,
        // 아니면(지난 배너를 방금 소모했든 애초에 없었든) N으로 정리한다.
        up.put("monsterStunnedYn", mageBankNextTurn ? "Y" : "N");
        // 중간보스의 1회성 방어버프/보호막은 위에서 이미 소모했으므로 일단 0으로 정리 --
        // 아래 미드보스 파트에서 이번 턴에 새로 훔쳤으면 별도 update로 다시 채워 넣는다.
        up.put("monsterDefBuffPct", 0);
        up.put("monsterShieldValue", 0);
        dao.updateUserProgress(up);
        // [세 구간 분리 요청] 파티 공격 결과(1구간)와 몬스터 상태·반격(2구간) 사이에 빈 줄을 넣는다.
        sb.append(NL);
        // [2026-09-05 멘트 개편] "HP" 텍스트 대신 이모지로, 파티(💗)와 구분되게 몬스터는
        // 노란색 하트(💛)를 쓴다.
        sb.append(eliteMonsterName(floor, mon, elite)).append(" 💛")
          .append(monsterHpAfter.format()).append("/").append(monsterMaxHp.format()).append(NL);

        if (stunned) {
            sb.append("몬스터 스턴! 반격 못함");
            sb.append(NL).append(NL).append(partyHpSummary(party, userStat));
            return sb.toString();
        }

        List<HashMap<String, Object>> alive = new ArrayList<>();
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) > 0) alive.add(c);
        }

        if (alive.isEmpty()) {
            int wipeStreak = intVal(p.get("WIPE_STREAK_CUR"), 0) + 1;
            HashMap<String, Object> defeatUp = new HashMap<>();
            defeatUp.put("userName", userName);
            defeatUp.put("status", "NORMAL");
            defeatUp.put("clearMonster", true);
            defeatUp.put("wipeStreakCur", wipeStreak);
            dao.updateUserProgress(defeatUp);
            // [변경] 예전엔 여기서 바로 풀피로 되돌렸는데, 그러면 안내 문구("마을에서 회복")가
            // 거짓말이 됨. 이제 정말로 마을에 돌아가야(changeFloor) 부활한다.
            sb.append("💀 파티 전멸... 전투에 패배했습니다. 동료들이 전투불가 상태로 남습니다 -- 마을로 돌아가야 부활합니다.");
            // "1~4층에서 여러 번 죽으면 /탑내려가기 안내도 해달라" 요청 -- 이 구간(블록 앞
            // 절반, 초반 사냥터)에서 연속으로 막히고 있으면 쉬운 아래 구간에서 파밍하고
            // 오라고 힌트를 준다. 2연속부터("여러 번") 매번 다시 보여준다.
            int fm = floor % 10;
            if (fm >= 1 && fm <= 4 && wipeStreak >= 2 && floor >= 10) {
                sb.append(NL).append(NL)
                  .append("💡 이 구간에서 ").append(wipeStreak).append("연속으로 전멸했어요. 아직 버거우면 ")
                  .append("/탑내려가기(/탑다운)로 10층 아래 마을로 내려가서 스탯/장비를 더 준비한 뒤 다시 도전해보세요.");
            }
            return sb.toString();
        }

        HashMap<String, Object> target = alive.get(RND.nextInt(alive.size()));
        // 도발로 대상이 바뀌면, 그 안내는 반격 결과 줄 "다음"에 보여준다("원래 대상이었던 X 대신"
        // 형태로 설명하는 게 자연스러움) -- 원래 대상을 미리 기억해둔다.
        HashMap<String, Object> originalTarget = target;
        boolean guarded = false;

        // 전사 도발: 체력 50% 이상인 전사가 있으면 확률적으로 자신이 대신 맞음.
        // [2026-09-05 신설] 전사3인조 시너지/★5·★6 성급 특수효과로 도발 확률이 오르고,
        // ★6 전사가 도발에 성공하면 그 반격 피해를 추가로 20% 더 깎는다. 전사3인조일 때는
        // 3명 전원이 각자 도발 판정을 받도록(기존엔 "첫 전사만" 판정하던 걸) 시너지 조건에서만
        // 풀어준다 -- 그 외엔 예전처럼 첫 전사만 판정(다수 판정으로 인한 밸런스 변화 방지).
        boolean warriorSynergy = "WARRIOR".equals(synergy);
        int warriorGuardMitigationPct = 0;
        if (!skillLocked) // [2026-09-06] 함정 "스킬사용금지" 중엔 전사 도발도 발동하지 않는다.
        for (HashMap<String, Object> c : alive) {
            if (!"WARRIOR".equals(strVal(c.get("CLASS"), ""))) continue;
            int wGrade = intVal(c.get("GRADE"), 1);
            List<HashMap<String, Object>> wEquips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] wEff = computeEffectiveStat("WARRIOR", wGrade, wEquips, userStat);
            PP wHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean over50 = PP.toBaseValue(wHp) * 2 >= wEff[0];
            int guardChance = 30;
            if (warriorSynergy) guardChance += 20;      // 시너지: 전사3인조
            if (wGrade >= 6) guardChance += 20;          // ★6
            else if (wGrade >= 5) guardChance += 10;     // ★5
            if (over50 && !c.equals(target) && RND.nextInt(100) < guardChance) {
                target = c;
                guarded = true;
                warriorGuardMitigationPct = wGrade >= 6 ? 20 : 0; // ★6: 도발 성공 시 받는 피해 추가 20%↓
            }
            if (!warriorSynergy) break; // 시너지 아니면 예전처럼 첫 전사만 판정
        }

        // [2026-09-06 신설] 51층 이후(블록6+) 중간보스 -- 맵/등장 메시지는 평범한 몬스터와
        // 똑같이 보이지만(startCombat 참고), 매 턴 지금 파티에 있는 직업 중 하나의 "기본 스킬"을
        // 하나 훔쳐서 자신이 사용한다(여러 직업이 섞여 있으면 매 턴 그 중 하나를 무작위로).
        // 전사는 도발(타겟팅) 자체가 자신에게 의미가 없으니 대신 방어력을 올리고, 도사는
        // 스스로에게 보호막을, 도적은 PP를 훔치고, 궁수는(즉사는 제외) 이번 반격 피해를
        // 늘리고, 마법사는 동료 한 명을 기절시킨다(기존 20층+ 보스 기절과 동일한 방식 재사용,
        // 반격 턴을 통째로 소모).
        boolean midBossArcherDmgUp = false;
        if (midBoss) {
            List<String> stealable = new ArrayList<>();
            for (HashMap<String, Object> c : alive) {
                String j = strVal(c.get("CLASS"), "");
                if (JOB_NAME.containsKey(j) && !stealable.contains(j)) stealable.add(j);
            }
            if (!stealable.isEmpty()) {
                String stolenJob = stealable.get(RND.nextInt(stealable.size()));
                if ("MAGE".equals(stolenJob)) {
                    HashMap<String, Object> stealStunUp = new HashMap<>();
                    stealStunUp.put("userName", userName);
                    stealStunUp.put("bossStunCid", intVal(target.get("COMPANION_ID"), 0));
                    dao.updateUserProgress(stealStunUp);
                    String stealStunName = strVal(target.get("NAME"), JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "동료"));
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 마법사의 기술을 흉내내 ")
                      .append(stealStunName).append(" 기절! 다음턴 공격불가");
                    sb.append(NL).append(NL).append(partyHpSummary(party, userStat));
                    return sb.toString();
                } else if ("WARRIOR".equals(stolenJob)) {
                    HashMap<String, Object> stealDefUp = new HashMap<>();
                    stealDefUp.put("userName", userName);
                    stealDefUp.put("monsterDefBuffPct", 30);
                    dao.updateUserProgress(stealDefUp);
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 전사의 기술을 흉내내 방어 태세를 갖췄다! (다음 파티 공격 시 방어력 +30%)").append(NL);
                } else if ("PRIEST".equals(stolenJob)) {
                    int stealShieldAmt = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * 2);
                    HashMap<String, Object> stealShUp = new HashMap<>();
                    stealShUp.put("userName", userName);
                    stealShUp.put("monsterShieldValue", stealShieldAmt);
                    dao.updateUserProgress(stealShUp);
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도사의 기술을 흉내내 스스로에게 보호막(").append(stealShieldAmt).append(")을 둘렀다!").append(NL);
                } else if ("ROGUE".equals(stolenJob)) {
                    PP curPpNow = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
                    PP stolenPp = curPpNow.multiplyRate(0.05);
                    PP afterPp = curPpNow.subtract(stolenPp);
                    if (PP.toBaseValue(afterPp) < 0) afterPp = PP.fromPP(0);
                    HashMap<String, Object> stealPpUp = new HashMap<>();
                    stealPpUp.put("userName", userName);
                    stealPpUp.put("ppValue", afterPp.getValue());
                    stealPpUp.put("ppExt", afterPp.getUnit());
                    dao.updateUserProgress(stealPpUp);
                    p.put("PP_VALUE", afterPp.getValue());
                    p.put("PP_EXT", afterPp.getUnit());
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 기술을 흉내내 PP를 훔쳐갔다! -").append(stolenPp.format()).append("PP").append(NL);
                } else if ("ARCHER".equals(stolenJob)) {
                    midBossArcherDmgUp = true;
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 궁수의 기술을 흉내내 이번 공격의 피해가 늘어난다!").append(NL);
                }
            }
        }

        // 20층 이후 보스의 기절 스킬: [2026-09-05] 30%->20%(무시 스킬 삭제와 함께 완화) ->
        // 50%(무시 삭제로 빠진 위협도를 기절 쪽으로 보충)로 재조정. 이번 반격 턴을 통째로 써서
        // 대상을 기절시킴(피해 없음, 다음 파티 공격 턴 1회를 건너뛰게 됨 -- 위 party 루프의
        // bossStunCid 체크에서 소모됨).
        if (lateBoss && RND.nextInt(100) < 50) {
            HashMap<String, Object> stunUp = new HashMap<>();
            stunUp.put("userName", userName);
            stunUp.put("bossStunCid", intVal(target.get("COMPANION_ID"), 0));
            dao.updateUserProgress(stunUp);
            String stunTargetName = strVal(target.get("NAME"), JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "동료"));
            sb.append("💫 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) ").append(stunTargetName)
              .append(" 기절! 다음턴 공격불가");
            sb.append(NL).append(NL).append(partyHpSummary(party, userStat));
            return sb.toString();
        }

        // [2026-09-05 신설] 39층 이후(블록4+) 보스는 반격 한 번에 동료 2명을 동시에 노린다
        // ("29층보다 갑자기 세졌다"는 원성과는 별개 요청 -- 위의 "무시" 스킬 삭제/기절 완화로
        // 개별 위협도는 낮췄고, 이건 블록4+ 보스만의 새 특성으로 얹은 것). 보호막은 두 대상
        // 모두에게 각자 독립적으로 100% 적용된다(shieldPool을 나눠 쓰지 않음 -- 도사의 가치가
        // 유지되도록). 첫 번째 대상만 위 전사 도발의 대상이 될 수 있고, 두 번째는 순수 랜덤.
        boolean doubleTarget = "Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 4;
        List<HashMap<String, Object>> targets = new ArrayList<>();
        targets.add(target);
        if (doubleTarget) {
            List<HashMap<String, Object>> remaining = new ArrayList<>(alive);
            remaining.remove(target);
            if (!remaining.isEmpty()) targets.add(remaining.get(RND.nextInt(remaining.size())));
        }

        // [2026-09-06 신설] 49층 이후(블록5+) 보스는 흡혈 능력 추가 -- 2명을 공격할 때 그 중
        // 두 번째 대상에게 실제로 들어간 피해(보호막으로 막힌 만큼은 제외한 값)만큼 자신의
        // 체력을 회복한다. 첫 번째 대상(전사 도발 대상이 될 수 있는 쪽)은 그대로 두고 흡혈은
        // 오직 한 명분만 적용(요청: "2명공격하니까 1명은 흡혈되도록").
        boolean vampiricBoss = "Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 5;
        long lifestealHeal = 0;

        // [2026-09-07] "몬스터도 주사위를 굴리는데 51~70층은 6~12, 71층부터는 8~20을 굴리게
        // 해달라" 요청 -- 원래 몬스터 반격은 플레이어가 낀 주사위(diceMax)를 그대로 같이
        // 썼는데(플레이어가 큰 주사위를 낄수록 몬스터 반격도 덩달아 세지는 부작용), 51층+는
        // 몬스터 자신만의 무작위 면수 주사위를 매 턴 새로 굴려서 플레이어 장비와 무관하게
        // 반격 변동폭을 키운다(한 턴 안에서 여러 대상을 때리는 다중 타겟 보스는 같은 턴 동안
        // 같은 면수를 공유, 대상별 눈금만 각자 새로 굴림).
        int monsterDiceMax = diceMax;
        if (floor >= 51) {
            monsterDiceMax = (floor <= 70) ? (6 + RND.nextInt(7)) : (8 + RND.nextInt(13));
        }

        for (int ti = 0; ti < targets.size(); ti++) {
            HashMap<String, Object> curTarget = targets.get(ti);
            boolean curGuarded = ti == 0 && guarded;
            HashMap<String, Object> curOriginalTarget = ti == 0 ? originalTarget : curTarget;
            if (ti > 0) sb.append(NL);

        String tJob = strVal(curTarget.get("CLASS"), "WARRIOR");
        int tGrade = intVal(curTarget.get("GRADE"), 1);
        List<HashMap<String, Object>> tEquips = dao.selectEquipByCompanion(intVal(curTarget.get("COMPANION_ID"), 0));
        int[] tEff = computeEffectiveStat(tJob, tGrade, tEquips, userStat);
        if ("RAINBOW".equals(synergy)) tEff[2] = (int) Math.round(tEff[2] * 1.1); // 시너지: 균형3인조 방어 +10%
        if (trapDefDown) tEff[2] = (int) Math.round(tEff[2] * 0.7); // 함정: 방어력 30% 약화(반격 피해 증가)
        if (luckyDefUp) tEff[2] = (int) Math.round(tEff[2] * luckyMult); // 럭키: 방어력 강화(반격 피해 감소)
        int monsterAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult);
        int roll = rollFace(1, monsterDiceMax); // 몬스터 자신의 반격 굴림 -- 플레이어 강화/마이너스 주사위와 무관하게 항상 1부터
        int rawDmgToParty = Math.max(1, monsterAtk * roll - tEff[2]);
        // 중간보스가 이번 턴 궁수 기술을 훔쳤으면(위 미드보스 파트) 이 반격 피해를 즉시 증폭.
        if (midBossArcherDmgUp) rawDmgToParty = (int) Math.round(rawDmgToParty * 1.3);
        int dmgToParty = rawDmgToParty;

        String tName = strVal(curTarget.get("NAME"), JOB_NAME.getOrDefault(tJob, "동료"));
        PP targetHp = PP.of(((Number) curTarget.get("CUR_HP_VALUE")).doubleValue(), strVal(curTarget.get("CUR_HP_EXT"), ""));
        // [명확화 요청] 실드가 얼마나 막아줬는지 한눈에 보이게, 반격 줄은 먼저 원본(raw) 피해량을
        // 보여주고, 실드/무시로 깎인 결과는 바로 다음 줄에서 설명한다(예전엔 이미 깎인 값만 나와서
        // "실드가 실제로 얼마를 막아줬는지" 확인이 안 됐음). [버그 수정] "누가 맞았는지 안 나온다"는
        // 신고로 대상도 표기.
        // [2026-09-05 멘트 개편] 파티 공격 줄과 형식을 맞춰서(이름+HP 줄 / 굴림 결과 줄 분리),
        // 몬스터 HP 줄 바로 다음에 굴림 결과를 붙이고, "~에게 반격!" 문구는 숫자 없이 별도 줄로.
        sb.append("🎲").append(roll).append("→ ").append(rawDmgToParty).append("dmg").append(NL);
        sb.append(eliteMonsterName(floor, mon, elite)).append("의 ").append(jobTag(tGrade, tJob, tName))
          .append("에게 반격! ").append(NL);

        // [2026-09-05 신설] ★5/★6 도적 "회피" -- 자신이 반격 대상이 되면 일정 확률로 피해를
        // 통째로 무효화한다(실드/전사 감소보다 우선 -- 아예 안 맞은 셈이라 뒤 계산 자체를 건너뜀).
        boolean rogueEvaded = false;
        if ("ROGUE".equals(tJob) && tGrade >= 5) {
            int evadeChance = tGrade >= 6 ? 45 : 30;
            if (RND.nextInt(100) < evadeChance) rogueEvaded = true;
        }

        if (rogueEvaded) {
            dmgToParty = 0;
            sb.append("🌀 회피! 피해를 완전히 피했다").append(NL);
        } else {
            if (shieldPool > 0) {
                int absorbed = Math.min(shieldPool, dmgToParty);
                dmgToParty -= absorbed;
                // [2026-09-05 멘트 개편] "N보호 후 Mdmg" 대신 잔여/총 보호막을 게이지처럼 보여줌.
                sb.append("🛡️ ").append(shieldPool - absorbed).append("/").append(shieldPool)
                  .append(" ").append(dmgToParty).append("dmg").append(NL);
                // "도사가 누구를 실드해줬는지 명확히" 요청 -- 위 파티 공격 파트에서 도사 자신에게
                // 붙던 🛡️+N 표시를, 실제로 이 실드를 받은(이번 반격의) 대상 본인의 "굴림 결과"
                // 줄로 옮겨서 붙인다(이름+HP 줄 / 굴림 결과 줄이 분리된 뒤로는 후자에 붙임).
                // 그 줄은 이 시점에 이미 sb에 적혀 있으므로 자리를 찾아 뒤에 이어붙인다.
                String targetAttackLinePrefix = jobTag(tGrade, tJob, tName) + " 💗" + targetHp.format() + "/" + tEff[0];
                int nameLineStart = sb.indexOf(targetAttackLinePrefix);
                if (nameLineStart >= 0) {
                    int nameLineEnd = sb.indexOf(NL, nameLineStart);
                    if (nameLineEnd >= 0) {
                        int rollLineEnd = sb.indexOf(NL, nameLineEnd + NL.length());
                        if (rollLineEnd < 0) rollLineEnd = sb.length();
                        sb.insert(rollLineEnd, " 🛡️+" + shieldPool);
                    }
                }
            }

            // [정책 변경] "무시 대상"은 보스 피해를 면제받지 않는다(반격은 평소처럼 그대로 받음) --
            // 대신 이 동료의 공격이 보스에게 안 먹히도록 위 파티 공격 파트에서 처리했다.

            // [2026-09-05 신설] 전사3인조 시너지: 파티 전체가 받는 반격 피해 10% 감소(항상 적용).
            // ★6 전사가 이번에 도발로 대신 맞았으면 그 몫만 추가로 20% 더 감소.
            if (warriorSynergy) dmgToParty = (int) Math.round(dmgToParty * 0.9);
            if (warriorGuardMitigationPct > 0) dmgToParty = (int) Math.round(dmgToParty * (1 - warriorGuardMitigationPct / 100.0));
            // [2026-09-06, 51층+ 전용 럭키] "체력 두배(3턴)" -- 실제 HP를 다시 계산하지 않고
            // 받는 피해를 절반으로 깎아 체력 두 배로 버티는 것과 동일한 효과를 낸다.
            if (luckyHpDouble) dmgToParty = (int) Math.round(dmgToParty * 0.5);
        }

        // 흡혈은 두 번째 대상(ti==1)에게 실제로 박힌 최종 피해(보호막 흡수분 제외, 회피 시 0)만
        // 집계 -- 이번 for문이 끝난 뒤 한꺼번에 보스 HP에 반영한다.
        if (vampiricBoss && ti == 1) lifestealHeal += dmgToParty;

        PP targetHpAfter = targetHp.subtract(PP.fromPP(dmgToParty));
        if (PP.toBaseValue(targetHpAfter) < 0) targetHpAfter = PP.fromPP(0);

        // [2026-09-05 신설] ★5/★6 도사 "부활" -- 이번 반격으로 동료가 쓰러지면, 파티 안의
        // ★5 이상 도사가(자기 자신이 쓰러진 경우 포함) 일정 확률로 그 자리에서 되살린다.
        if (PP.toBaseValue(targetHpAfter) <= 0) {
            HashMap<String, Object> reviver = null;
            for (HashMap<String, Object> c : party) {
                if ("PRIEST".equals(strVal(c.get("CLASS"), "")) && intVal(c.get("GRADE"), 1) >= 5) {
                    reviver = c;
                    break;
                }
            }
            if (reviver != null) {
                int reviverGrade = intVal(reviver.get("GRADE"), 1);
                int reviveChance = reviverGrade >= 6 ? 40 : 25;
                double revivePct = reviverGrade >= 6 ? 0.5 : 0.3;
                if (RND.nextInt(100) < reviveChance) {
                    targetHpAfter = PP.fromPP(Math.max(1, (int) Math.round(tEff[0] * revivePct)));
                    sb.append("✨ 도사의 기적! ").append(jobTag(tGrade, tJob, tName))
                      .append(" 부활 (HP ").append(targetHpAfter.format()).append("/").append(tEff[0]).append(")").append(NL);
                }
            }
        }

        HashMap<String, Object> cUp = new HashMap<>();
        cUp.put("companionId", intVal(curTarget.get("COMPANION_ID"), 0));
        cUp.put("curHpValue", targetHpAfter.getValue());
        cUp.put("curHpExt", targetHpAfter.getUnit());
        dao.updateCompanionHp(cUp);
        // [버그 수정] DB엔 반영됐지만 curTarget(=party 리스트 안의 같은 객체)의 메모리 값은 안
        // 바뀌어서, 바로 아래 partyHpSummary()가 반격 맞기 "전" HP를 그대로 보여주는 문제가
        // 있었다("이번턴 남은" 구간에 맞은 사람 HP가 그대로 풀피로 나옴 -- 신고로 확인).
        // party 리스트 원본을 직접 갱신.
        curTarget.put("CUR_HP_VALUE", targetHpAfter.getValue());
        curTarget.put("CUR_HP_EXT", targetHpAfter.getUnit());

        if (curGuarded) {
            // 도발로 실제 맞은 건 다른 동료라서, 원래 대상이 누구였는지 반격 결과 다음 줄에 설명.
            String origJob = strVal(curOriginalTarget.get("CLASS"), "WARRIOR");
            int origGrade = intVal(curOriginalTarget.get("GRADE"), 1);
            String origName = strVal(curOriginalTarget.get("NAME"), JOB_NAME.getOrDefault(origJob, "동료"));
            sb.append(jobTag(origGrade, origJob, origName)).append(" 대신 🛡️ 전사가 공격을 받아냅니다!").append(NL);
        }
        } // for (targets)

        // 흡혈 반영 -- 위 for문에서 집계한 만큼 보스 체력을 회복시키고(최대체력 초과 불가),
        // 이미 저장해둔 curMonsterHpValue를 갱신값으로 한 번 더 덮어쓴다.
        if (lifestealHeal > 0) {
            PP healedHp = monsterHpAfter.add(PP.fromPP(lifestealHeal));
            if (PP.toBaseValue(healedHp) > PP.toBaseValue(monsterMaxHp)) healedHp = monsterMaxHp;
            monsterHpAfter = healedHp;
            HashMap<String, Object> healUp = new HashMap<>();
            healUp.put("userName", userName);
            healUp.put("curMonsterHpValue", monsterHpAfter.getValue());
            healUp.put("curMonsterHpExt", monsterHpAfter.getUnit());
            dao.updateUserProgress(healUp);
            sb.append(NL).append("🩸 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 흡혈로 ")
              .append(lifestealHeal).append(" 회복! 💛").append(monsterHpAfter.format()).append("/").append(monsterMaxHp.format());
        }

        // "몬스터 반격 이후 파티 체력을 보여달라" 요청
        sb.append(NL).append(partyHpSummary(party, userStat));

        return sb.toString();
    }

    /**
     * "몬스터 반격 이후 파티 체력을 보여달라, 성급/이름도 나오게, 줄바꿈도 넣어달라" 요청으로
     * 신설 -- 파티 전원의 현재HP/최대HP를 공격 줄과 동일한 jobTag(★등급직업(이름)) 형식으로
     * 한 명당 한 줄씩 보여준다(전투불가면 💀). [세 구간 분리 요청] "이번턴 남은" 구간의 헤더.
     */
    private String partyHpSummary(List<HashMap<String, Object>> party, HashMap<String, Object> userStat) {
        // [명확화 요청] 헤더를 "전투결과"로 바꾸고, 하트는 헤더 장식이 아니라 각자의 HP 앞에
        // 붙여서 "이 사람은 살아있다/죽었다"가 줄마다 바로 보이게 함(전투불가면 💀0/최대).
        StringBuilder sb = new StringBuilder("전투결과").append(NL);
        for (int i = 0; i < party.size(); i++) {
            HashMap<String, Object> c = party.get(i);
            String job = strVal(c.get("CLASS"), "WARRIOR");
            int grade = intVal(c.get("GRADE"), 1);
            String cName = strVal(c.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
            List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
            int[] eff = computeEffectiveStat(job, grade, equips, userStat);
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean dead = PP.toBaseValue(hp) <= 0;
            if (i > 0) sb.append(NL);
            sb.append(jobTag(grade, job, cName)).append(" ").append(dead ? "💀" : "💗")
              .append(hp.format()).append("/").append(eff[0]);
        }
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

    // [2026-09-08] 자동사냥 정산(settleAutoHunt)은 kills를 한 번에 여러 마리씩(예: +6, +12)
    // 더하므로, 원래의 "totalKill == 100" 같은 정확히-일치 체크는 그 순간을 건너뛰어버릴 수
    // 있었다(수동 전투 킬은 항상 +1씩이라 문제 없었음). prevTotal/newTotal 사이에 임계값이
    // 있었는지로 바꿔서 두 호출 경로 모두 안전하게 만듦.
    private void checkKillAchievements(String userName, int prevTotal, int newTotal) {
        if (prevTotal < 100 && newTotal >= 100) grantAchievement(userName, 8);
        if (prevTotal < 1000 && newTotal >= 1000) grantAchievement(userName, 9);
    }

    /** [2026-09-08] "자동사냥으로 몇 회 처치, 관련 업적에 추가해달라" 요청 -- 자동사냥으로만
     *  처치한 누적 마리수(AUTO_HUNT_KILL_TOTAL, 수동 전투 킬과 별개) 임계값 판정. */
    private void checkAutoHuntKillAchievements(String userName, int prevTotal, int newTotal) {
        if (prevTotal < 500 && newTotal >= 500) grantAchievement(userName, 26);
        if (prevTotal < 5000 && newTotal >= 5000) grantAchievement(userName, 27);
    }

    /** [2026-09-08] ACH_ID=14(자동사냥 입문, "자동사냥으로 PP 1000 모았다")는 마스터
     *  데이터엔 있었지만 전용 누적 컬럼이 없어 한 번도 체크되지 않고 있었다 -- 자동사냥
     *  전용 누적 PP(AUTO_HUNT_PP_TOTAL_*) 신설하며 드디어 연동. */
    private void checkAutoHuntPpAchievement(String userName, PP prevTotal, PP newTotal) {
        PP threshold = PP.of(1000, "");
        if (prevTotal.compare(threshold) < 0 && newTotal.compare(threshold) >= 0) {
            grantAchievement(userName, 14);
        }
    }

    /** [2026-09-08] "몬스터 전투중 도망치다 업적도 있으면 좋겠다" 요청 -- 전투 중 /층변경(도망)
     *  누적 횟수(FLEE_COUNT_TOTAL, changeFloor에서 1씩만 증가하므로 exact == 체크로 충분). */
    private void checkFleeAchievements(String userName, int newTotal) {
        if (newTotal == 10) grantAchievement(userName, 28);
        if (newTotal == 100) grantAchievement(userName, 29);
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
        // AUTO_HUNT_KILLS_PER_HOUR(기본 6)를 "N분당 1마리"로 환산 -- config화(재배포 없이 /갱신으로 조절)
        long minPerKill = Math.max(1, 60L / Math.max(1, AUTO_HUNT_KILLS_PER_HOUR));
        if (elapsedMin < minPerKill) return null; // 아직 1마리도 정산할 만큼 안 지남

        long cappedMin = Math.min(elapsedMin, AUTO_HUNT_MAX_HOURS * 60L);
        int floor = intVal(log.get("FLOOR"), intVal(p.get("CUR_FLOOR"), 1));
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
        if (mon == null) return null;

        long kills = cappedMin / minPerKill;
        if (kills <= 0) return null;

        PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
        PP reward = perKill.multiply(kills * floorPpMultiplier(floor));
        addPp(userName, p, reward);

        // [2026-09-08] "자동사냥으로 몇 회 처치, 관련 업적에 추가해달라" 요청 -- 자동사냥으로만
        // 처치한 누적치(AUTO_HUNT_KILL_TOTAL)를 TOTAL_KILL_COUNT와 별도로 함께 쌓는다. 자동사냥
        // 전용 누적 PP(AUTO_HUNT_PP_TOTAL_*)도 신설해서 그동안 미체크였던 ACH_ID=14(자동사냥
        // 입문) 업적을 드디어 연동. 크로싱 판정을 위해 갱신 전(prev) 값을 먼저 담아둔다.
        int prevTotalKill = intVal(p.get("TOTAL_KILL_COUNT"), 0);
        int newTotalKill = prevTotalKill + (int) kills;
        int prevAutoHuntKill = intVal(p.get("AUTO_HUNT_KILL_TOTAL"), 0);
        int newAutoHuntKill = prevAutoHuntKill + (int) kills;
        PP prevAutoHuntPp = PP.of(numVal(p.get("AUTO_HUNT_PP_TOTAL_VALUE"), 0), strVal(p.get("AUTO_HUNT_PP_TOTAL_EXT"), ""));
        PP newAutoHuntPp = PP.of(numVal(p.get("AUTO_HUNT_PP_TOTAL_VALUE"), 0), strVal(p.get("AUTO_HUNT_PP_TOTAL_EXT"), "")).add(reward);

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("totalKillCount", newTotalKill);
        up.put("autoHuntKillTotal", newAutoHuntKill);
        up.put("autoHuntPpTotalValue", newAutoHuntPp.getValue());
        up.put("autoHuntPpTotalExt", newAutoHuntPp.getUnit());
        dao.updateUserProgress(up);
        p.put("TOTAL_KILL_COUNT", newTotalKill);
        p.put("AUTO_HUNT_KILL_TOTAL", newAutoHuntKill);
        p.put("AUTO_HUNT_PP_TOTAL_VALUE", newAutoHuntPp.getValue());
        p.put("AUTO_HUNT_PP_TOTAL_EXT", newAutoHuntPp.getUnit());

        HashMap<String, Object> logUp = new HashMap<>();
        logUp.put("userName", userName);
        logUp.put("floor", floor);
        dao.upsertAutoHuntLog(logUp); // LAST_SETTLE_DATE = SYSDATE 로 갱신

        checkKillAchievements(userName, prevTotalKill, newTotalKill);
        checkAutoHuntKillAchievements(userName, prevAutoHuntKill, newAutoHuntKill);
        checkAutoHuntPpAchievement(userName, prevAutoHuntPp, newAutoHuntPp);

        // "PP 획득 시 현재 보유 PP도 보여달라" 요청 -- 자동사냥은 사냥터 몬스터만 farm하므로
        // reward가 0일 일은 없어 보스전 같은 0PP 생략 케이스는 여기 해당 없음.
        PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
        return "💤 자동사냥 정산: " + floor + "층에서 " + kills + "마리 처치, " + reward.format()
                + " PP 획득! (보유 " + curPp.format() + " PP)";
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
        if (target == floor) {
            return "이미 " + floor + "층에 있습니다."; // "같은 층으로 이동은 막아달라" 요청
        }
        // [2026-09-07, 당분간] 61층 이상은 콘텐츠 준비 전이라 진입 자체를 차단(마을 접근 등
        // 다른 제약보다 우선 확인). maxReached로 이미 자격이 있어도 예외 없이 막는다.
        if (target >= CONTENT_LOCKED_FLOOR) {
            return "🌑 어둠이 득실거려 현재는 갈 수 없습니다. (61층 이상, 추후 오픈 예정)";
        }
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
        int newFleeCount = -1;
        if (wasInCombat) {
            // 전투 중 층 이동 = 도망. 진행 중이던 전투를 포기하고 상태를 되돌린다.
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            // [2026-09-08] "몬스터 전투중 도망치다 업적도 있으면 좋겠다" 요청 -- 도망 누적 횟수.
            newFleeCount = intVal(p.get("FLEE_COUNT_TOTAL"), 0) + 1;
            up.put("fleeCountTotal", newFleeCount);
        }
        // [버그 수정] KILL_COUNT_CUR("이 층에서 몇 마리 잡았는지")가 층이 바뀌어도 초기화되지
        // 않아서, 예전 층에서 쌓인 처치수가 다음 층까지 이어져 엉뚱하게 10마리를 채우고
        // 자동사냥이 켜지는 문제가 있었다(예: 이전 층 9마리 + 새 층 1마리 = 10). 층이 실제로
        // 바뀔 때는 항상 0으로 리셋해서 "이 층 도착 이후 처치수"만 세도록 한다.
        if (target != floor) {
            up.put("killCountCur", 0);
        }
        dao.updateUserProgress(up);
        if (newFleeCount >= 0) {
            checkFleeAchievements(userName, newFleeCount);
        }

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

        // 사냥터층에서 마을로 돌아가면 그 구간(blockBase+1~+8) 전체 사냥터층의 원정(보드
        // 위치+발견기록)을 초기화한다. 업적(탐험왕 등)을 자유롭게 파밍하지 못하게 하려는
        // 의도 -- 한 원정 안에서 끝까지 밀어야 함.
        // [버그 수정] "1층 17/25 채우고 2층 올라갔다가 마을 가면 1층이 초기화 안 된다"는 신고로
        // 확인 -- 예전엔 마을 오기 직전에 서 있던 층 딱 하나만 초기화돼서, 그 전에 들렀다가
        // 층변경으로 그냥 지나쳐온 다른 층들의 방문기록은 그대로 방치돼 있었다(원정을 나눠서
        // 여러 번에 걸쳐 완전탐사를 파밍할 수 있던 구멍). 이제 이 구간 사냥터층 8개 전부를
        // 완전탐사 여부와 무관하게 전부 초기화한다 -- "완전탐사했다는 기록은 남기고 보드/방문
        // 기록만 초기화해도 된다"는 요청대로, 어차피 완전탐사 여부(FULLY_EXPLORED_YN)는
        // TBOT_S5_USER_FLOOR_BEST라는 별도 테이블에 영구 보존되고 여기서 지우는 CUR_TILE/
        // TILE_VISIT/USER_TILE_MASTER와는 전혀 무관하므로, 굳이 완전탐사한 층만 봐줄 이유가
        // 없었음(snapshotFloorBest가 먼저 그 기록을 반영해두므로 초기화해도 기록은 안전).
        int fm = floor % 10;
        boolean returnedToVillage = target % 10 == 0 && fm >= 1 && fm <= 8 && floor != target;
        HashMap<String, Object> floorBest = null;
        if (returnedToVillage) {
            floorBest = snapshotFloorBest(userName, floor); // 방금 나온 층 -- 안내 메시지에 이 기록을 그대로 씀
            resetBlockExploration(userName, target); // 이 구간 사냥터층 8개 전부 초기화(완전탐사한 층도 포함, 기록 자체는 안전)
        }

        // "탑 상하이동 시 계단칸에 도착하게 해달라" 요청 -- 사냥터층(1~8)에 도착하면 이동 방향에
        // 맞는 계단 칸에 서 있는 걸로 위치를 맞춘다: 아래에서 올라왔으면(target > floor) 그 층의
        // "내려가는 계단"(다시 내려갈 때 쓸 계단)에, 위에서 내려왔으면(target < floor) "올라가는
        // 계단"에 도착. target은 항상 floor와 같은 10층 구간 안이라 단순 대소 비교로 방향이 정확히
        // 갈린다. 마을/보스층은 보드가 없어서 해당 없음.
        if (targetFm >= 1 && targetFm <= 8) {
            String landTileType = target > floor ? "STAIRS_DOWN" : "STAIRS_UP";
            List<HashMap<String, Object>> targetTiles = ensureUserBoard(userName, target);
            int landTileNo = 0;
            for (HashMap<String, Object> t : targetTiles) {
                if (landTileType.equals(strVal(t.get("TILE_TYPE"), ""))) {
                    landTileNo = intVal(t.get("TILE_NO"), 0);
                    break;
                }
            }
            if (landTileNo > 0) {
                HashMap<String, Object> ufpSave = new HashMap<>();
                ufpSave.put("userName", userName);
                ufpSave.put("floor", target);
                ufpSave.put("curTile", landTileNo);
                // [2026-09-06] 이 원정에서 처음 밟은 계단 칸 -- 51층+ "처음 계단칸으로 돌아가기"
                // 함정(RESET_TILE)이 복귀 지점으로 쓴다.
                ufpSave.put("entryTile", landTileNo);
                dao.upsertUserFloorProgress(ufpSave);
                dao.insertTileVisit(userName, target, landTileNo);
            }
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
            sb.append(NL).append("⚠️ 이 구간 사냥터층의 탐사 진행도가 전부 초기화되었습니다. (완전탐사 기록은 유지, 다시 가면 보드는 새로 생성)");
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
                    sb.append(NL).append("🏆 [").append(floor).append("층 완전탐사] 업적 달성! ").append(floorBest.get("VOUCHER_MSG"));
                    Object ticketMsg = floorBest.get("BLOCK_TICKET_MSG");
                    if (ticketMsg != null) sb.append(NL).append(ticketMsg);
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

    /**
     * /층내려가기(/층다운) — 같은 구간 안에서 바로 아래 한 층으로. changeFloor()에 그대로
     * 위임해서 도착 처리(부활/탐사 초기화/계단 착지/업적 등)를 전부 동일하게 재사용한다.
     */
    @Override
    public String descendFloor(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        int fm = floor % 10;
        if (fm == 0) {
            return "🏘️ 이미 이 구간의 마을입니다. 더 아래 구간으로 가려면 /탑내려가기(/탑다운)를 사용하세요.";
        }
        return changeFloor(userName, fm - 1);
    }

    /**
     * /탑내려가기(/탑다운) — 마을에서만 바로 아래 10층 구간 마을로 이동. changeFloor()와 달리
     * 같은 구간을 벗어나는 이동이라 target이 항상 이전에 실제로 밟았던 마을(구간을 순서대로
     * 올라와야만 지금 서 있을 수 있으므로)이라 별도 재진입 자격 확인이 필요 없다. 사냥터층
     * 탐사 초기화(resetBlockExploration)도 여긴 해당 없음(마을→마을 이동은 사냥터층을 아예
     * 거치지 않음).
     */
    @Override
    public String descendVillage(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        if (floor % 10 != 0) {
            return "🏘️ 마을에서만 사용할 수 있습니다. (/층변경 0 으로 먼저 마을로 이동하세요)";
        }
        if (floor == 0) {
            return "🏘️ 이미 0층 마을입니다. 더 내려갈 곳이 없습니다.";
        }
        int target = floor - 10;

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        dao.updateUserProgress(up);

        List<HashMap<String, Object>> villageParty = new ArrayList<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) villageParty.add(c);
        }
        int revivedCount = revivePartyDead(userName, villageParty, dao.selectUserStat(userName));

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);
        sb.append("🪜 ").append(floor).append("층 마을 → ").append(target).append("층 마을로 내려갔습니다.");
        if (revivedCount > 0) {
            sb.append(NL).append("✨ 전투불가 상태였던 동료 ").append(revivedCount).append("명이 마을에서 부활했습니다!");
        }
        return sb.toString();
    }

    /**
     * /탑올라가기(/탑업) — "보스를 처치한 구간은 위 마을로 바로 이동하게 해달라" 요청.
     * descendVillage()와 대칭인 상승 버전: 마을에서만 사용 가능, 이 구간 보스를 이미
     * 처치해서(UNLOCKED_BLOCK이 목표 층 이상) 위 구간이 열려있어야만 이동할 수 있다
     * (아직 안 열린 구간은 그 구간 보스를 실제로 밟고 넘어야 함 -- 편도 진행 규칙 유지).
     */
    @Override
    public String ascendVillage(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        if (floor % 10 != 0) {
            return "🏘️ 마을에서만 사용할 수 있습니다. (/층변경 0 으로 먼저 마을로 이동하세요)";
        }
        int target = floor + 10;
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (unlocked < target) {
            return "🔒 아직 해금되지 않은 구간입니다. 이 구간 보스를 처치해야 " + target + "층 마을이 열립니다.";
        }

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        dao.updateUserProgress(up);

        List<HashMap<String, Object>> villageParty = new ArrayList<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) villageParty.add(c);
        }
        int revivedCount = revivePartyDead(userName, villageParty, dao.selectUserStat(userName));

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);
        sb.append("🪜 ").append(floor).append("층 마을 → ").append(target).append("층 마을로 올라갔습니다.");
        if (revivedCount > 0) {
            sb.append(NL).append("✨ 전투불가 상태였던 동료 ").append(revivedCount).append("명이 마을에서 부활했습니다!");
        }
        return sb.toString();
    }

    private void grantFloorAchievements(String userName, int floor) {
        if (floor == 1) grantAchievement(userName, 1);
        if (floor == 10) grantAchievement(userName, 2);
        if (floor == 30) grantAchievement(userName, 3);
        if (floor == 60) grantAchievement(userName, 4); // [수정] 상급 동료 계약서 실제 해금층(60)에 맞춰 50→60
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
            int[] reward = floorVoucherReward(floor); // [gachaTier 1~4, count]
            int tier = reward[0], count = reward[1];
            String field = "companionVoucherT" + tier;
            String column = "COMPANION_VOUCHER_T" + tier;
            HashMap<String, Object> voucherUp = new HashMap<>();
            voucherUp.put("userName", userName);
            voucherUp.put(field, intVal(prog == null ? null : prog.get(column), 0) + count);
            dao.updateUserProgress(voucherUp);
            row.put("VOUCHER_MSG", COMPANION_TIER_NAME[tier - 1] + " 동료뽑기권 " + count + "장 지급!");
            // 이번에 새로 완전탐사된 층이 속한 10층 구간의 4층 그룹(앞 X1~X4/뒤 X5~X8)이
            // 이걸로 전부 완전탐사가 됐는지 확인 -- 됐으면 선택권 지급(웹 UI 전용, 구간별 등급 상향).
            row.put("BLOCK_TICKET_MSG", checkBlockExploreTicket(userName, floor));
        }
        return row;
    }

    /**
     * 방금 완전탐사된 floor가 속한 4층 그룹(X1~X4 또는 X5~X8)이 전부 완전탐사인지 확인하고,
     * 처음 달성이면 ★3 선택권(동료/무기)을 지급한다. 알림 문구(없으면 null)를 반환.
     */
    private String checkBlockExploreTicket(String userName, int floor) {
        int m = floor % 10;
        if (m < 1 || m > 8) return null; // 마을/보스층은 대상 아님(이 경로로 올 일도 없음)
        boolean lowGroup = m <= 4;
        int base = floorBlockBase(floor);
        int groupStart = lowGroup ? base + 1 : base + 5;

        for (int f = groupStart; f < groupStart + 4; f++) {
            HashMap<String, Object> fb = dao.selectUserFloorBest(userName, f);
            if (fb == null || !"Y".equals(strVal(fb.get("FULLY_EXPLORED_YN"), "N"))) return null; // 아직 그룹 미완성
        }

        int block = blockNo(floor);
        int achId = (lowGroup ? 300 : 400) + block;
        if (!grantAchievement(userName, achId)) return null; // 이미 지급됨

        // 선택권 등급도 구간(블록)에 따라 상향: 1~30층(블록1~3)=★3, 31~50층(블록4~5)=★4,
        // 51층 이후(블록6~10)=★5. 등급별로 컬럼을 따로 둬서(기존 _G3=원래 컬럼 재사용) 여러
        // 등급의 선택권을 동시에 들고 있어도 섞이지 않게 한다.
        int ticketGrade = base >= 50 ? 5 : (base >= 30 ? 4 : 3);
        HashMap<String, Object> prog = dao.selectUserProgress(userName);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        String field = (lowGroup ? "companionChoiceTicket" : "weaponChoiceTicket") + (ticketGrade == 3 ? "" : "G" + ticketGrade);
        String column = (lowGroup ? "COMPANION_CHOICE_TICKET" : "WEAPON_CHOICE_TICKET") + (ticketGrade == 3 ? "" : "_G" + ticketGrade);
        up.put(field, intVal(prog == null ? null : prog.get(column), 0) + 1);
        dao.updateUserProgress(up);

        int gStart = groupStart, gEnd = groupStart + 3;
        return "🎁 [" + gStart + "~" + gEnd + "층 완전탐사] 업적 달성! ★" + ticketGrade + " " + (lowGroup ? "동료" : "무기")
                + " 선택권 1장 지급! (웹 화면 파티/장비 탭에서 직업을 골라 사용하세요)";
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

    /** 파티 슬롯에서 동료를 뺄 때 항상 같이 호출 -- "해제 시 장비도 같이 풀리게 해달라" 요청
      * (2026-09-06)으로 신설. 착용 중이던 장비를 전부 미착용 상태로 되돌린다(장비 자체가
      * 사라지진 않고 미착용 목록으로 돌아갈 뿐). equipUnwearAll(/장비해제)과 partyToggle/
      * partyUnassignAll(파티 해제) 양쪽에서 공유. */
    private int unequipAllForCompanion(int companionId) {
        List<HashMap<String, Object>> equipped = dao.selectEquipByCompanion(companionId);
        for (HashMap<String, Object> e : equipped) {
            HashMap<String, Object> unwear = new HashMap<>();
            unwear.put("equipId", intVal(e.get("EQUIP_ID"), 0));
            unwear.put("equippedCompanionId", null);
            dao.updateEquipEquippedCompanion(unwear);
        }
        return equipped.size();
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
            int companionId = intVal(target.get("COMPANION_ID"), 0);
            int unequipped = unequipAllForCompanion(companionId);
            HashMap<String, Object> up = new HashMap<>();
            up.put("companionId", companionId);
            up.put("partySlot", null);
            dao.updateCompanionPartySlot(up);
            return "파티에서 해제했습니다." + (unequipped > 0 ? " (착용 중이던 장비 " + unequipped + "개도 함께 해제됨)" : "");
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

    /**
     * 웹 SPA 전용: 파티 슬롯 탭 시트에서 쓰는 통합 배치 액션. targetSlot(1~3)에 idx(=/파티편성
     * 목록 번호)의 동료를 배치한다. 두 가지 경우를 하나로 처리:
     *   1) idx가 이미 편성된 동료(파티끼리 자리 교체, "동료1,2,3끼리도 위치변경 가능하니?" 요청) --
     *      targetSlot이 비어있으면 단순 이동, 다른 동료가 있으면 서로 자리를 맞바꾼다(교환,
     *      쫓겨나는 동료 없음).
     *   2) idx가 아직 미편성인 동료(슬롯 시트에서 "빈 슬롯에 배치" 또는 "다른 동료로 교체") --
     *      targetSlot이 비어있으면 그냥 배치, 이미 다른 동료가 있으면 그 동료를 파티에서
     *      완전히 빼낸다(돌아갈 자리가 없으므로 교환이 아니라 축출 -- "해제 시 장비도 함께
     *      해제되게 해달라" 요청대로 축출되는 동료의 장비도 이때 자동으로 전부 해제한다).
     */
    @Override
    @Transactional
    public String partySwapSlot(String userName, int idx, int targetSlot) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p != null && "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 파티를 변경할 수 없습니다.";
        }
        if (targetSlot < 1 || targetSlot > 3) {
            return "잘못된 슬롯 번호입니다.";
        }
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        if (idx < 1 || idx > companions.size()) {
            return "잘못된 번호입니다. /파티편성 으로 목록을 확인하세요.";
        }
        HashMap<String, Object> dragged = companions.get(idx - 1);
        Object draggedSlotObj = dragged.get("PARTY_SLOT");
        boolean wasPartied = draggedSlotObj != null;
        int draggedSlot = wasPartied ? ((Number) draggedSlotObj).intValue() : 0;
        if (wasPartied && draggedSlot == targetSlot) {
            return "이미 그 자리입니다.";
        }

        HashMap<String, Object> occupant = null;
        for (HashMap<String, Object> c : companions) {
            Object s = c.get("PARTY_SLOT");
            if (s != null && ((Number) s).intValue() == targetSlot) {
                occupant = c;
                break;
            }
        }

        HashMap<String, Object> up1 = new HashMap<>();
        up1.put("companionId", intVal(dragged.get("COMPANION_ID"), 0));
        up1.put("partySlot", targetSlot);
        dao.updateCompanionPartySlot(up1);

        if (occupant == null) {
            return wasPartied ? ("파티 " + targetSlot + "번 자리로 이동했습니다!")
                               : ("파티 " + targetSlot + "번 슬롯에 편성했습니다!");
        }
        if (wasPartied) {
            HashMap<String, Object> up2 = new HashMap<>();
            up2.put("companionId", intVal(occupant.get("COMPANION_ID"), 0));
            up2.put("partySlot", draggedSlot);
            dao.updateCompanionPartySlot(up2);
            return "파티 " + draggedSlot + "번과 " + targetSlot + "번 자리를 맞바꿨습니다!";
        }
        // 미편성 동료로 교체 -- 기존 자리는 사라지므로 쫓겨나는 동료는 완전히 파티 밖으로,
        // 장비도 함께 해제.
        int evictedUnequipped = unequipAllForCompanion(intVal(occupant.get("COMPANION_ID"), 0));
        HashMap<String, Object> up2 = new HashMap<>();
        up2.put("companionId", intVal(occupant.get("COMPANION_ID"), 0));
        up2.put("partySlot", null);
        dao.updateCompanionPartySlot(up2);
        String occupantName = strVal(occupant.get("NAME"), JOB_NAME.getOrDefault(strVal(occupant.get("CLASS"), ""), "동료"));
        return "파티 " + targetSlot + "번 자리를 교체했습니다! (" + occupantName + " 은(는) 파티 밖으로"
                + (evictedUnequipped > 0 ? ", 장비 " + evictedUnequipped + "개도 함께 해제됨)" : ")");
    }

    /** 웹 SPA 전용: 편성된 동료 전원을 한 번에 해제("일괄해제" 요청으로 신설). 개별 해제는 기존 PARTY_TOGGLE로 충분해서 그대로 둠. */
    @Override
    @Transactional
    public String partyUnassignAll(String userName) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p != null && "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 파티를 변경할 수 없습니다.";
        }
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        int cnt = 0;
        int unequippedTotal = 0;
        for (HashMap<String, Object> c : companions) {
            if (c.get("PARTY_SLOT") != null) {
                int companionId = intVal(c.get("COMPANION_ID"), 0);
                unequippedTotal += unequipAllForCompanion(companionId);
                HashMap<String, Object> up = new HashMap<>();
                up.put("companionId", companionId);
                up.put("partySlot", null);
                dao.updateCompanionPartySlot(up);
                cnt++;
            }
        }
        if (cnt == 0) return "편성된 동료가 없습니다.";
        return "파티 " + cnt + "명을 전부 해제했습니다." + (unequippedTotal > 0 ? " (착용 중이던 장비 " + unequippedTotal + "개도 함께 해제됨)" : "");
    }

    /**
     * /이벤트지급(관리자 전용) — EVENT_ADMIN_USERS에 없는 유저는 조용히 거부(누가 관리자인지,
     * 이 명령어가 뭘 하는 건지조차 드러내지 않도록 이유를 자세히 안 붙임). 뽑기권은 실제 경제
     * 가치가 있어 아무나 채팅으로 뿌릴 수 있으면 안 되므로, 이 시스템 안에서 유일하게 존재하는
     * 권한 체크(isEventAdmin)를 반드시 통과해야 한다.
     * 동료뽑기권/장비뽑기권 둘 다 등급(tier 1~4)을 못박은 티어락 권으로 지급(그 등급
     * 계약서/상자에만 쓸 수 있고, 아직 그 층에 못 간 유저도 이 권으로는 바로 뽑을 수 있음 --
     * hasUsableCompanionVoucher/hasUsableEquipVoucher 참고). [버그 수정] 원래 장비는 등급
     * 구분 없이 범용 EQUIP_VOUCHER로만 지급했는데, "/이벤트지급 중급 1 1 했더니 장비뽑기권은
     * 초급으로 지급됐다(둘 다 중급이어야 함)"는 신고로 확인 -- 이제 EQUIP_VOUCHER_T{tier}로 지급.
     */
    @Override
    @Transactional
    public String grantEventVouchers(String userName, int tier, int companionQty, int equipQty) {
        if (!isEventAdmin(userName)) {
            return "권한이 없습니다.";
        }
        if (tier < 1 || tier > 4) {
            return "등급은 1(하급)~4(최상급) 사이여야 합니다.";
        }
        if (companionQty < 0 || equipQty < 0) {
            return "수량은 0 이상이어야 합니다.";
        }
        if (companionQty == 0 && equipQty == 0) {
            return "사용법: /이벤트지급 [등급 초급|중급|상급|최상급] [동료뽑기권수량] [장비뽑기권수량] "
                    + "(예: /이벤트지급 중급 3 2, 등급 생략 시 초급, 수량 생략 시 0)";
        }
        StringBuilder sb = new StringBuilder("🎉 이벤트 지급 완료!");
        if (companionQty > 0) {
            int affected = dao.bulkGrantTierCompanionVoucher(tier, companionQty);
            sb.append(NL).append("전체 유저 ").append(affected).append("명에게 ")
              .append(COMPANION_TIER_NAME[tier - 1]).append("(").append(tier).append("번) 동료뽑기권 ")
              .append(companionQty).append("장 지급 (해금 여부와 무관하게 바로 사용 가능)");
        }
        if (equipQty > 0) {
            int affected = dao.bulkGrantTierEquipVoucher(tier, equipQty);
            sb.append(NL).append("전체 유저 ").append(affected).append("명에게 ")
              .append(COMPANION_TIER_NAME[tier - 1]).append("(").append(tier).append("번) 장비뽑기권 ")
              .append(equipQty).append("장 지급 (해금 여부와 무관하게 바로 사용 가능)");
        }
        return sb.toString();
    }

    /** 웹 SPA 전용: 현재 등록된 공지 버전/내용. 로그인/권한 무관, 항상 공개(비어있으면 폴백값).
     *  userName이 있으면 그 유저가 이 버전을 이미 "다시 보지 않기"로 닫았는지 DISMISSED에 담는다
     *  (진행 기록이 없는 유저면 항상 false). */
    @Override
    public HashMap<String, Object> getNotice(String userName) {
        HashMap<String, Object> row = dao.selectNotice();
        HashMap<String, Object> result = new HashMap<>();
        if (row == null) {
            result.put("APP_VERSION", "0");
            result.put("NOTICE_TEXT", "");
            result.put("DISMISSED", false);
            return result;
        }
        result.putAll(row);
        boolean dismissed = false;
        if (userName != null && !userName.trim().isEmpty()) {
            String seen = dao.selectNoticeSeenVersion(userName.trim());
            String version = String.valueOf(row.get("APP_VERSION"));
            dismissed = version.equals(seen);
        }
        result.put("DISMISSED", dismissed);
        return result;
    }

    /** 웹 SPA 전용: "다시 보지 않기" -- 현재 공지 버전을 그 유저의 NOTICE_SEEN_VERSION으로
     *  저장. 진행 기록이 없는 유저(TBOT_S5_USER_PROGRESS 행 미존재)면 0건 갱신, 조용히 무시
     *  (그런 유저는 애초에 볼 진행 데이터가 없어 이 페이지를 실제로 쓸 일이 없음). */
    @Override
    @Transactional
    public void dismissNotice(String userName) {
        if (userName == null || userName.trim().isEmpty()) return;
        HashMap<String, Object> row = dao.selectNotice();
        String version = (row == null) ? "0" : String.valueOf(row.get("APP_VERSION"));
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName.trim());
        up.put("version", version);
        dao.updateNoticeSeenVersion(up);
    }

    /**
     * /공지등록(관리자 전용) — "새로고침 잘 안 하는 유저가 있다, 업데이트 시 강제로
     * 새로고침 유도하고 공지도 보여주고 싶다" 요청. 새 공지 내용을 등록하고 버전(현재
     * 시각 타임스탬프 문자열, 매번 반드시 달라짐)을 새로 발급 -- 웹 화면이 주기적으로
     * /api/tower-notice를 조회하다가 버전이 바뀐 걸 감지하면 새로고침 안내 팝업을 띄운다.
     * /이벤트지급과 같은 EVENT_ADMIN_USERS 권한 체크를 그대로 재사용.
     */
    @Override
    @Transactional
    public String setNotice(String userName, String text) {
        if (!isEventAdmin(userName)) {
            return "권한이 없습니다.";
        }
        if (text == null || text.trim().isEmpty()) {
            return "사용법: /공지등록 [공지 내용] (등록하면 웹 화면 접속자에게 새로고침 안내와 함께 표시됩니다)";
        }
        String version = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        HashMap<String, Object> up = new HashMap<>();
        up.put("version", version);
        up.put("text", text.trim());
        dao.updateNotice(up);
        return "📢 공지 등록 완료! (버전 " + version + ") 웹 화면을 켜둔 접속자들에게 새로고침 안내 팝업이 순서대로 뜹니다.";
    }

    /**
     * /탑통계(관리자 전용) — /이벤트지급과 같은 EVENT_ADMIN_USERS 권한 체크를 그대로 재사용.
     * "어느 채널이 몇 명인지" 별도 컬럼으로 안 남기고 있어서, 웹 액션마다 TBOT_WORD_HIS.
     * ROOM_NAME='WEB'으로 로그를 남기는 기존 장치(Season5ViewController.apiTowerAction)를
     * 역이용해서 집계한다(selectChannelUsageStats 주석 참고) -- 정확한 실시간 집계가 아니라
     * 대략적인 채널 분포 참고용이라는 걸 명시해서 오해 없게 한다.
     */
    @Override
    public String towerStats(String userName) {
        if (!isEventAdmin(userName)) {
            return "권한이 없습니다.";
        }
        HashMap<String, Object> stat = dao.selectChannelUsageStats();
        int total = intVal(stat.get("TOTAL_USERS"), 0);
        int web = intVal(stat.get("WEB_USERS"), 0);
        int chat = intVal(stat.get("CHAT_USERS"), 0);
        int both = intVal(stat.get("BOTH_USERS"), 0);
        int webOnly = web - both;
        int chatOnly = chat - both;
        int neither = total - web - chatOnly; // = total - (web ∪ chat), 아직 아무 활동도 없는 유저(계정만 생성)

        StringBuilder sb = new StringBuilder();
        sb.append("┌────────────────┐").append(NL);
        sb.append(" 📊 시즌5 채널 통계").append(NL);
        sb.append("└────────────────┘").append(NL);
        sb.append("전체 유저: ").append(total).append("명").append(NL);
        sb.append("🖥️ 웹 이용: ").append(web).append("명 (").append(pct(web, total)).append("%)").append(NL);
        sb.append("💬 카톡 이용: ").append(chat).append("명 (").append(pct(chat, total)).append("%)").append(NL);
        sb.append("🔀 둘 다 이용: ").append(both).append("명").append(NL);
        sb.append("  ├ 웹만: ").append(webOnly).append("명").append(NL);
        sb.append("  └ 카톡만: ").append(chatOnly).append("명").append(NL);
        if (neither > 0) {
            sb.append("⚪ 기록 없음(계정만 생성): ").append(neither).append("명").append(NL);
        }

        // [2026-09-06] "1시간/24시간/오늘 기준도 보고 싶다" 요청 -- TBOT_S5_ACTIVITY_HOURLY
        // (시간 버킷) 기준으로 세 구간을 먼저 보여주고, 마지막에 전체 누적을 보여준다.
        // 시간/24시간/오늘은 달력 기준(현재 시(hour) 버킷 / 최근 24개 버킷 / 자정 이후 버킷
        // 합)이라 "정확히 지금부터 60분 전"같은 엄밀한 롤링 윈도우는 아니다.
        try {
            List<HashMap<String, Object>> hourlyRows = dao.selectActivityHourly();
            HashMap<String, HashMap<String, Object>> byKey = new HashMap<>();
            for (HashMap<String, Object> row : hourlyRows) {
                byKey.put(strVal(row.get("STAT_TYPE"), "") + "_" + strVal(row.get("CHANNEL"), ""), row);
            }
            appendActivityWindow(sb, byKey, "⏱ 최근 1시간", "HOUR1");
            appendActivityWindow(sb, byKey, "🕐 최근 24시간", "HOUR24");
            appendActivityWindow(sb, byKey, "📅 오늘(00:00~)", "TODAY");
        } catch (Exception e) {
            // S5_ACTIVITY_HOURLY.sql 미적용 등으로 테이블이 없을 때도 나머지 통계는 정상 출력되게
            sb.append(NL).append("(시간대별 통계는 TBOT_S5_ACTIVITY_HOURLY 테이블 확인 필요)").append(NL);
        }

        long diceWeb = ((Number) stat.getOrDefault("DICE_WEB", 0)).longValue();
        long diceChat = ((Number) stat.getOrDefault("DICE_CHAT", 0)).longValue();
        long gachaWeb = ((Number) stat.getOrDefault("GACHA_WEB", 0)).longValue();
        long gachaChat = ((Number) stat.getOrDefault("GACHA_CHAT", 0)).longValue();
        long wipeWeb = ((Number) stat.getOrDefault("WIPE_WEB", 0)).longValue();
        long wipeChat = ((Number) stat.getOrDefault("WIPE_CHAT", 0)).longValue();
        sb.append(NL).append("📆 전체 누적").append(NL);
        appendActivityLine(sb, "🎲", "주사위(이동+전투)", diceWeb, diceChat);
        appendActivityLine(sb, "🎰", "뽑기 시도(10연속도 1회)", gachaWeb, gachaChat);
        appendActivityLine(sb, "💀", "파티 전멸", wipeWeb, wipeChat);

        try {
            List<HashMap<String, Object>> faceRows = dao.selectDiceFaceStats();
            long faceTotal = 0;
            int maxRolledFace = 6; // DICE_6은 항상 있으니 최소 1~6까지는 표시
            for (HashMap<String, Object> row : faceRows) {
                long cnt = ((Number) row.getOrDefault("ROLL_COUNT", 0)).longValue();
                faceTotal += cnt;
                if (cnt > 0) maxRolledFace = Math.max(maxRolledFace, intVal(row.get("FACE_VALUE"), 0));
            }
            sb.append(NL).append("🎲 주사위 눈 분포 (합계 ").append(faceTotal).append("회)").append(NL);
            for (HashMap<String, Object> row : faceRows) {
                int face = intVal(row.get("FACE_VALUE"), 0);
                if (face > maxRolledFace) continue; // DICE_8/10/12/20 미해금 구간의 빈 칸(항상 0)은 생략
                long cnt = ((Number) row.getOrDefault("ROLL_COUNT", 0)).longValue();
                sb.append("  ").append(face).append(": ").append(cnt).append("회");
                if (face < maxRolledFace) sb.append(NL);
            }
        } catch (Exception e) {
            // S5_DICE_FACE_STATS.sql 미적용 등으로 테이블이 없을 때도 나머지 통계는 정상 출력되게
            sb.append(NL).append("🎲 주사위 눈 분포: (TBOT_S5_DICE_STATS 테이블 확인 필요)");
        }

        return sb.toString();
    }

    private int pct(int part, int total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    /** /탑통계 한 줄(이모지 + 라벨 + 웹/카톡 + 합계). */
    private void appendActivityLine(StringBuilder sb, String emoji, String label, long web, long chat) {
        sb.append(emoji).append(" ").append(label).append(" — 웹 ").append(web).append(" / 카톡 ").append(chat)
          .append(" (합계 ").append(web + chat).append(")").append(NL);
    }

    /** /탑통계 시간대 구간 하나(제목 + 주사위/뽑기/전멸 3줄). windowCol은 HOUR1/HOUR24/TODAY. */
    private void appendActivityWindow(StringBuilder sb, HashMap<String, HashMap<String, Object>> byKey, String title, String windowCol) {
        sb.append(NL).append(title).append(NL);
        appendActivityLine(sb, "🎲", "주사위", hourlyVal(byKey, "DICE", "WEB", windowCol), hourlyVal(byKey, "DICE", "CHAT", windowCol));
        appendActivityLine(sb, "🎰", "뽑기", hourlyVal(byKey, "GACHA", "WEB", windowCol), hourlyVal(byKey, "GACHA", "CHAT", windowCol));
        appendActivityLine(sb, "💀", "전멸", hourlyVal(byKey, "WIPE", "WEB", windowCol), hourlyVal(byKey, "WIPE", "CHAT", windowCol));
    }

    private long hourlyVal(HashMap<String, HashMap<String, Object>> byKey, String statType, String channel, String windowCol) {
        HashMap<String, Object> row = byKey.get(statType + "_" + channel);
        if (row == null) return 0;
        return ((Number) row.getOrDefault(windowCol, 0)).longValue();
    }

    private static final HashMap<String, String[]> ACTIVITY_STAT_COLUMNS = new HashMap<String, String[]>() {{
        // { countColumn, usedFlagColumn } -- 이 화이트리스트에 없는 statKey는 bumpActivityStat에서 조용히 무시됨
        put("DICE_WEB",   new String[]{ "DICE_COUNT_WEB",  "WEB_USED_YN" });
        put("DICE_CHAT",  new String[]{ "DICE_COUNT_CHAT", "CHAT_USED_YN" });
        put("GACHA_WEB",  new String[]{ "GACHA_COUNT_WEB",  "WEB_USED_YN" });
        put("GACHA_CHAT", new String[]{ "GACHA_COUNT_CHAT", "CHAT_USED_YN" });
        put("WIPE_WEB",   new String[]{ "WIPE_COUNT_WEB",  "WEB_USED_YN" });
        put("WIPE_CHAT",  new String[]{ "WIPE_COUNT_CHAT", "CHAT_USED_YN" });
    }};

    /** /탑통계용 활동 카운터 적재. statKey가 화이트리스트에 없으면 조용히 무시(호출부 실수 방지용 방어). */
    @Override
    public void bumpActivityStat(String userName, String statKey) {
        String[] cols = ACTIVITY_STAT_COLUMNS.get(statKey);
        if (cols == null) return;
        try {
            dao.bumpActivityStat(userName, cols[0], cols[1]);
        } catch (Exception ignore) {
            // 통계 적재 실패가 실제 게임 액션 응답을 막으면 안 됨(TBOT_WORD_HIS 로깅과 동일 관례)
        }
        // [2026-09-06] "/탑통계에 1시간/24시간/오늘 기준도 보고싶다" 요청 -- 전체 누적 컬럼과
        // 별개로, 현재 시(hour) 버킷에도 같이 적재해서 시간대별 집계를 가능하게 함.
        // statKey는 항상 "STATTYPE_CHANNEL"(예: DICE_WEB) 형태의 고정 화이트리스트 값.
        try {
            int us = statKey.lastIndexOf('_');
            if (us > 0) {
                dao.bumpActivityHourly(statKey.substring(0, us), statKey.substring(us + 1));
            }
        } catch (Exception ignore) {
            // 마찬가지로 통계 실패가 게임 진행을 막으면 안 됨
        }
    }

    // ================================================================
    // /탑업적
    // ================================================================
    @Override
    public String achievements(String userName) {
        return achievements(userName, null);
    }

    /** /탑업적 닉네임 — "/탑현황 닉네임"과 동일한 패턴으로 다른 유저 업적도 조회 가능하게. */
    @Override
    public String achievements(String userName, String targetQuery) {
        boolean isOther = targetQuery != null && !targetQuery.trim().isEmpty();
        String target = userName;
        if (isOther) {
            String resolved = resolveTargetUser(targetQuery);
            if (resolved == null) {
                return "🔍 '" + targetQuery.trim() + "' 로 시작하는 유저를 찾을 수 없습니다.";
            }
            target = resolved;
        }

        List<HashMap<String, Object>> all = dao.selectAchievementList();
        List<HashMap<String, Object>> mine = dao.selectUserAchievements(target);

        // ACH_ID -> 업적 정보 전체(이름/타입/파라미터) 매핑
        HashMap<Integer, HashMap<String, Object>> achById = new HashMap<>();
        for (HashMap<String, Object> a : all) {
            achById.put(intVal(a.get("ACH_ID"), -1), a);
        }

        StringBuilder sb = new StringBuilder(target).append("님의 업적 (")
                .append(mine.size()).append("/").append(all.size()).append(")," + NL);
        if (mine.isEmpty()) {
            sb.append("(아직 달성한 업적이 없습니다)");
            return sb.toString();
        }

        // "업적이 125개나 돼서 카톡 텍스트로 조회하면 너무 길다" 요청. 세 종류가 대량으로
        // 반복되는 게 원인이라("N층 완전탐사" 블록당 최대 8개·전체 80개, "N~N층 동료/무기
        // 선택권" 각 10개씩) 이 셋을 유형별로 한 줄씩 묶어서 압축한다("최신순 15개 자르기
        // 말고 이런 식으로 묶어달라"는 후속 요청 -- 캡은 안전망으로만 남겨둠).
        // [2026-09-08 버그 근본 수정] "층 완전탐사류 업적을 보유한 유저는 /탑업적 조회가
        // 그룹화 없이 개별 나열로 나온다"는 신고 재확인 결과 원인 확정: TBOT_S5_ACHIEVEMENT.
        // ACH_PARAM은 VARCHAR2(50) 컬럼이라 JDBC가 String으로 돌려주는데, 바로 아래서
        // intVal()(내부에서 (Number) o로 강제 캐스팅)에 넘겨서 FLOOR_EXPLORE 업적을 하나라도
        // 보유한 유저는 전부 ClassCastException이 터졌다 -- 아래 try/catch(직전 세션이 원인을
        // 못 찾고 임시로 감싸둔 안전망)가 그 예외를 삼키고 그룹화 없는 원래 방식으로 조용히
        // 폴백했던 것. ACH_PARAM은 Integer.parseInt로 파싱하도록 고쳐서 근본 수정, try/catch는
        // 다른 이유로도 실패하지 않도록 안전망으로 그대로 남겨둠.
        try {
            java.util.TreeMap<Integer, List<Integer>> floorsByBlock = new java.util.TreeMap<>();
            List<String> compVoucherFloors = new ArrayList<>(); // "1~4층 동료 선택권" -> "1~4층"만
            List<String> weapVoucherFloors = new ArrayList<>();
            List<HashMap<String, Object>> others = new ArrayList<>();
            for (HashMap<String, Object> m : mine) {
                int id = intVal(m.get("ACH_ID"), -1);
                HashMap<String, Object> a = achById.get(id);
                String type = a == null ? "" : strVal(a.get("ACH_TYPE"), "");
                if ("FLOOR_EXPLORE".equals(type)) {
                    int floor;
                    try { floor = Integer.parseInt(strVal(a.get("ACH_PARAM"), "0").trim()); }
                    catch (NumberFormatException nfe) { floor = 0; }
                    List<Integer> bucket = floorsByBlock.get(blockNo(floor));
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        floorsByBlock.put(blockNo(floor), bucket);
                    }
                    bucket.add(floor);
                } else if ("BLOCK_EXPLORE_LOW".equals(type)) {
                    compVoucherFloors.add(strVal(a.get("ACH_NAME"), "").replace(" 동료 선택권", ""));
                } else if ("BLOCK_EXPLORE_HIGH".equals(type)) {
                    weapVoucherFloors.add(strVal(a.get("ACH_NAME"), "").replace(" 무기 선택권", ""));
                } else {
                    others.add(m);
                }
            }
            String[] roman = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
            for (Integer block : floorsByBlock.keySet()) {
                List<Integer> floors = floorsByBlock.get(block);
                Collections.sort(floors);
                StringBuilder floorList = new StringBuilder();
                for (int i = 0; i < floors.size(); i++) {
                    if (i > 0) floorList.append(",");
                    floorList.append(floors.get(i));
                }
                sb.append("✅ 탑 완전정복").append(block >= 1 && block <= roman.length ? roman[block - 1] : String.valueOf(block))
                  .append(" ").append(floorList).append(NL);
            }
            if (!compVoucherFloors.isEmpty()) {
                sb.append("✅ 동료 선택권 ").append(String.join(",", compVoucherFloors)).append(NL);
            }
            if (!weapVoucherFloors.isEmpty()) {
                sb.append("✅ 무기 선택권 ").append(String.join(",", weapVoucherFloors)).append(NL);
            }

            // [2026-09-08] "외 N개 더로 자르지 말고 전체 다 노출, 대신 정렬을 비슷한 것끼리
            // 묶이게 조정해달라" 요청 -- 15개 캡/최신순 정렬 제거. TBOT_S5_ACHIEVEMENT 마스터
            // 데이터(S5_MASTER_DATA.sql)는 같은 ACH_TYPE끼리 ACH_ID를 연달아 붙여서 등록해뒀으므로
            // (예: 1~6=FLOOR_REACHED, 8~9=MONSTER_KILL_TOTAL, 10~11=GACHA_PULL_COUNT,
            // 12~13=EQUIP_SYNTHESIS, 17~24=히든 ??? 계열...) ACH_ID 오름차순으로만 정렬해도
            // 같은 유형끼리 자연히 묶여서 나온다.
            others.sort((a, b) -> Integer.compare(intVal(a.get("ACH_ID"), 0), intVal(b.get("ACH_ID"), 0)));
            for (HashMap<String, Object> m : others) {
                int id = intVal(m.get("ACH_ID"), -1);
                HashMap<String, Object> a = achById.get(id);
                sb.append("✅ ").append(a != null ? strVal(a.get("ACH_NAME"), "?") : "?").append(NL);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sb.setLength(0);
            sb.append(target).append("님의 업적 (").append(mine.size()).append("/").append(all.size()).append(")," + NL);
            for (HashMap<String, Object> m : mine) {
                int id = intVal(m.get("ACH_ID"), -1);
                sb.append("✅ ").append(nameByIdSafe(achById, id)).append(NL);
            }
        }
        return sb.toString();
    }

    private String nameByIdSafe(HashMap<Integer, HashMap<String, Object>> achById, int id) {
        HashMap<String, Object> a = achById.get(id);
        return a == null ? "?" : strVal(a.get("ACH_NAME"), "?");
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

    // [2026-09-07] "중급/상급 등에서 낮은 성급 동료가 나오면 PP를 너무 많이 환급해준다" 신고 --
    // 기존엔 중복 보상이 "그 뽑기권 비용 * 20%"라서, 같은 ★3이라도 상급(12000P) 계약서에서
    // 나오면 중급(1500P)에서 나온 것보다 8배 많이 환급받는 등 뽑기권 종류에 따라 같은 성급의
    // 환급액이 들쭉날쭉했다(상급/최상급은 저성급도 자주 뽑히는데 비용만 비싸서 특히 심함).
    // 이제 뽑기권 비용이 아니라 "뽑힌 동료의 성급" 하나로만 환급액을 고정 -- 어느 계약서에서
    // 나왔든 같은 성급이면 같은 PP를 돌려받고, 성급이 높을수록 더 많이 받는다.
    private static final int[] COMPANION_DUPE_REFUND = { 20, 80, 400, 2000, 8000, 30000 }; // index = grade-1

    /** 동료 뽑기 1회의 핵심 로직(무료판정/비용차감/추첨/insert)만 수행. 실패 시 result에 error만 채워 반환. */
    private HashMap<String, Object> pullCompanionCore(String userName, HashMap<String, Object> gacha,
            HashMap<String, Object> p, int ownedSoFar) {
        HashMap<String, Object> result = new HashMap<>();
        int gachaId = intVal(gacha.get("GACHA_ID"), 0);

        // [정책 변경] 해금 여부는 원래 무료뽑기권/스타터 무료와 무관하게 항상 확인했는데(권은
        // 비용만 면제, 해금 요건은 그대로), 그러면 이벤트로 중급 이상 뽑기권을 지급해도 아직
        // 그 층에 못 간 유저는 못 쓰는 문제가 있었다. 권을 갖고 있다는 것 자체가 그 등급을 쓸
        // 자격을 이미 부여받았다는 뜻이라, 그 gachaId에 실제로 쓸 수 있는 권(티어락 또는 범용)이
        // 있으면 해금 여부를 건너뛴다.
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        boolean hasVoucher = hasUsableCompanionVoucher(p, gachaId);
        if (!hasVoucher && intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 계약서입니다.");
            return result;
        }

        boolean free = gachaId == STARTER_GACHA_ID && ownedSoFar < STARTER_FREE_PULLS;
        if (!free) free = consumeCompanionVoucher(userName, p, gachaId);
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
        // 같으면 진짜 중복으로 보고 PP를 환급한다("중복 정산") -- 환급액은 뽑힌 성급 기준
        // COMPANION_DUPE_REFUND 고정표(위 참고, 어느 계약서에서 나왔든 동일).
        boolean dupe = false;
        for (HashMap<String, Object> owned : dao.selectUserCompanions(userName)) {
            if (job.equals(strVal(owned.get("CLASS"), "")) && name.equals(strVal(owned.get("NAME"), ""))) {
                dupe = true;
                break;
            }
        }
        if (dupe) {
            PP dupeBonus = PP.fromPP(COMPANION_DUPE_REFUND[grade - 1]);
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
        // [정리 요청] 테두리가 너무 길어서 텍스트 폭에 맞게 줄이고, HP/ATK/DEF 라벨도 이모지로.
        StringBuilder sb = new StringBuilder();
        sb.append("┌───────────┐").append(NL);
        sb.append(" 🎉 새 동료 영입!").append(NL);
        sb.append("└───────────┘").append(NL);
        sb.append(name).append(" (").append(JOB_NAME.get(job)).append(" ★").append(grade).append(")").append(NL);
        sb.append("❤️ ").append(stat[0]).append(" ⚔️ ").append(stat[1]).append(" 🛡️ ").append(stat[2]).append(NL);
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
    private HashMap<String, Object> pullEquipCore(String userName, HashMap<String, Object> gacha, HashMap<String, Object> p, int tier) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        // pullCompanionCore와 동일한 이유로, 쓸 수 있는 장비뽑기권을 갖고 있으면 해금 여부를 건너뜀
        if (!hasUsableEquipVoucher(p, tier) && intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 상자입니다.");
            return result;
        }
        if (!consumeEquipVoucher(userName, p, tier)) {
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
        HashMap<String, Object> r = pullEquipCore(userName, gacha, p, gachaId);
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
            HashMap<String, Object> r = pullEquipCore(userName, gacha, p, gachaId);
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
            sb.append("/주사위구매 N 으로 해금된 주사위로 자유롭게 교체(무료, 몇 번이든 가능)");
            return sb.toString();
        }

        if (n < 1 || n > DICE_NAMES.length) return "잘못된 번호입니다.";
        if (unlocked < DICE_UNLOCK[n - 1]) return "아직 해금되지 않은 주사위입니다.";
        // [2026-09-08] "전투중엔 주사위변경도 안 되게 막아달라" 요청 -- 전투 중 유리한 면수로
        // 갈아끼우는 걸 막는다. 목록 조회(n==null)는 그대로 허용, 실제 교체(n!=null)만 차단.
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 주사위를 교체할 수 없습니다.";
        }

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceGrade", DICE_NAMES[n - 1]);
        dao.updateUserProgress(up);
        // [2026-09-06] "웹 UI에서 뭘로 바뀌었는지 멘트 있으면 좋겠다" 요청 -- 웹 오버레이도
        // 이 메시지를 그대로 토스트로 보여주므로(TW.action 공용 흐름) 여기서만 고치면 됨.
        return "🎲 " + diceMax(DICE_NAMES[n - 1]) + "면체 주사위로 교체되었습니다.";
    }

    /** 웹 SPA 상점탭 주사위 UI용 — 등급별 {name, unlockFloor, unlocked, current} 구조화 목록. */
    @Override
    public List<HashMap<String, Object>> diceListInfo(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        String curDice = strVal(p.get("DICE_GRADE"), "DICE_6");
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < DICE_NAMES.length; i++) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("idx", i + 1);
            row.put("name", DICE_NAMES[i]);
            row.put("unlockFloor", DICE_UNLOCK[i]);
            row.put("unlocked", unlocked >= DICE_UNLOCK[i]);
            row.put("current", DICE_NAMES[i].equals(curDice));
            list.add(row);
        }
        return list;
    }

    /** [2026-09-08] 웹 SPA 주사위 UI용 — 주사위 강화(+, 6단계)/마이너스 주사위(-, 1단계뿐)
     *  현황·비용 구조화 데이터. */
    @Override
    public HashMap<String, Object> diceEnhanceInfo(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int bonus = intVal(p.get("DICE_MIN_BONUS"), 0);
        int malus = intVal(p.get("DICE_MIN_MALUS"), 0);
        HashMap<String, Object> result = new HashMap<>();
        result.put("bonusLevel", bonus);
        result.put("malusLevel", malus);
        List<HashMap<String, Object>> bonusTiers = new ArrayList<>();
        for (int i = 0; i < DICE_BONUS_UNLOCK.length; i++) {
            int tier = i + 1;
            HashMap<String, Object> b = new HashMap<>();
            b.put("tier", tier);
            b.put("unlockFloor", DICE_BONUS_UNLOCK[i]);
            b.put("cost", DICE_BONUS_COST[i]);
            b.put("owned", bonus >= tier);
            b.put("buyable", bonus == tier - 1 && unlocked >= DICE_BONUS_UNLOCK[i]);
            bonusTiers.add(b);
        }
        List<HashMap<String, Object>> malusTiers = new ArrayList<>();
        for (int i = 0; i < DICE_MALUS_UNLOCK.length; i++) {
            int tier = i + 1;
            HashMap<String, Object> m = new HashMap<>();
            m.put("tier", tier);
            m.put("unlockFloor", DICE_MALUS_UNLOCK[i]);
            m.put("cost", DICE_MALUS_COST[i]);
            m.put("owned", malus >= tier);
            m.put("buyable", malus == tier - 1 && unlocked >= DICE_MALUS_UNLOCK[i]);
            malusTiers.add(m);
        }
        result.put("bonusTiers", bonusTiers);
        result.put("malusTiers", malusTiers);
        return result;
    }

    /** /주사위강화, /마이너스주사위 (인자 없이) — 현재 단계/다음 단계 비용·해금 조건 안내. */
    @Override
    public String diceEnhanceStatus(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int bonus = intVal(p.get("DICE_MIN_BONUS"), 0);
        int malus = intVal(p.get("DICE_MIN_MALUS"), 0);
        StringBuilder sb = new StringBuilder(userName).append("님의 주사위 강화 현황," + NL);
        sb.append("🎲 주사위 강화(최소 눈금 +): ").append(bonus).append("/").append(DICE_BONUS_UNLOCK.length).append("단계").append(NL);
        if (bonus < DICE_BONUS_UNLOCK.length) {
            int nf = DICE_BONUS_UNLOCK[bonus];
            sb.append("  다음 단계(+").append(bonus + 1).append("): ")
              .append(unlocked >= nf ? "구매 가능, " : nf + "층 마을 도착 필요, ")
              .append(PP.of(DICE_BONUS_COST[bonus], "").format()).append(" PP").append(NL);
        }
        sb.append("🎲 마이너스 주사위(최소 눈금 -): ").append(malus).append("/").append(DICE_MALUS_UNLOCK.length).append("단계").append(NL);
        if (malus < DICE_MALUS_UNLOCK.length) {
            int nf = DICE_MALUS_UNLOCK[malus];
            sb.append("  다음 단계(-").append(malus + 1).append("): ")
              .append(unlocked >= nf ? "구매 가능, " : nf + "층 마을 도착 필요, ")
              .append(PP.of(DICE_MALUS_COST[malus], "").format()).append(" PP").append(NL);
        }
        sb.append("/주사위강화 구매 로 강화 다음 단계, /마이너스주사위 구매 로 마이너스 다음 단계 구매 (둘 다 계정 전체 공통 적용, 최대 눈금은 그대로)");
        return sb.toString();
    }

    @Override
    @Transactional
    public String buyDiceBonus(String userName) {
        return buyDiceEnhance(userName, true);
    }

    @Override
    @Transactional
    public String buyDiceMalus(String userName) {
        return buyDiceEnhance(userName, false);
    }

    private String buyDiceEnhance(String userName, boolean isBonus) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        // [2026-09-08] "전투중엔 주사위변경도 안 되게 막아달라(최대/최소 둘 다)" 요청 --
        // 위 diceShop()의 최대치(등급) 교체 차단과 짝을 이루는, 최소치(강화/마이너스) 구매 차단.
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 주사위 강화/마이너스 주사위를 구매할 수 없습니다.";
        }
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int cur = intVal(p.get(isBonus ? "DICE_MIN_BONUS" : "DICE_MIN_MALUS"), 0);
        String label = isBonus ? "주사위 강화" : "마이너스 주사위";
        int[] unlockArr = isBonus ? DICE_BONUS_UNLOCK : DICE_MALUS_UNLOCK;
        if (cur >= unlockArr.length) {
            return "🎲 " + label + "는 이미 최대 단계(" + unlockArr.length + "단계)입니다.";
        }
        int nextTier = cur + 1;
        int needFloor = unlockArr[nextTier - 1];
        if (unlocked < needFloor) {
            return "🔒 " + needFloor + "층 마을에 도착해야 " + label + " " + nextTier + "단계를 구매할 수 있습니다. (현재 " + cur + "단계)";
        }
        long costRaw = (isBonus ? DICE_BONUS_COST : DICE_MALUS_COST)[nextTier - 1];
        PP price = PP.of(costRaw, "");
        if (!deductPp(userName, p, price)) {
            return "PP가 부족합니다. (" + price.format() + " 필요)";
        }
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(isBonus ? "diceMinBonus" : "diceMinMalus", nextTier);
        dao.updateUserProgress(up);
        return isBonus
                ? "🎲 주사위 강화 " + nextTier + "단계 적용! (모든 주사위 최소 눈금 +" + nextTier + ", 최대 눈금은 그대로)"
                : "🎲 마이너스 주사위 " + nextTier + "단계 적용! (모든 주사위 최소 눈금 -" + nextTier + ", 최대 눈금은 그대로)";
    }

    @Override
    public int autoHuntKillsPerHour() {
        return AUTO_HUNT_KILLS_PER_HOUR;
    }

    /** 스탯 강화 상한 계산: 구간(10층 단위) 하나 클리어(보스 처치)마다 +5. index0(unlockedBlock=0)일 때도 최소 5. */
    private int statCapFor(int unlockedBlock) {
        return 5 + 5 * (unlockedBlock / 10);
    }

    /**
     * 보스 처치로 UNLOCKED_BLOCK이 oldUnlocked -> newUnlocked로 오를 때 "이번에 새로 열린 것"만
     * 모아서 안내 문구로 만든다(스탯 상한 상승 / 이번 구간부터 열리는 주사위 / 동료·장비 계약서
     * 해금) -- "새 마을 도착 시 뭐가 바뀌었는지 알려달라" 요청으로 추가.
     */
    private String newlyUnlockedSummary(int oldUnlocked, int newUnlocked) {
        StringBuilder sb = new StringBuilder();
        int oldCap = statCapFor(oldUnlocked);
        int newCap = statCapFor(newUnlocked);
        if (newCap > oldCap) {
            sb.append("📈 스탯 강화 상한: ").append(oldCap).append(" → ").append(newCap).append(NL);
        }
        for (int i = 0; i < DICE_NAMES.length; i++) {
            if (DICE_UNLOCK[i] == newUnlocked) {
                sb.append("🎲 새 주사위 해금: ").append(DICE_NAMES[i])
                  .append(" (/주사위구매 ").append(i + 1).append("로 장착)").append(NL);
            }
        }
        sb.append(newlyUnlockedGacha("COMPANION", "동료 계약서", oldUnlocked, newUnlocked));
        sb.append(newlyUnlockedGacha("EQUIP", "장비 상자", oldUnlocked, newUnlocked));
        return sb.toString();
    }

    /** newlyUnlockedSummary의 가챠(동료/장비) 부분 -- 해금 컷오프 전후 목록을 비교해 새로 늘어난 것만 뽑는다. */
    private String newlyUnlockedGacha(String gachaType, String label, int oldUnlocked, int newUnlocked) {
        List<HashMap<String, Object>> oldList = dao.selectGachaList(gachaType, oldUnlocked);
        List<HashMap<String, Object>> newList = dao.selectGachaList(gachaType, newUnlocked);
        List<Integer> oldIds = new ArrayList<>();
        for (HashMap<String, Object> g : oldList) oldIds.add(intVal(g.get("GACHA_ID"), -1));
        StringBuilder sb = new StringBuilder();
        for (HashMap<String, Object> g : newList) {
            int id = intVal(g.get("GACHA_ID"), -1);
            if (!oldIds.contains(id)) {
                sb.append("🎁 ").append(label).append(" 해금: ").append(strVal(g.get("GACHA_NAME"), "")).append(NL);
            }
        }
        return sb.toString();
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
        int unlockedBlock = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int cap = statCapFor(unlockedBlock);
        int nextVillageFloor = unlockedBlock + 10; // 다음 상한이 열리는 마을(그 앞 보스를 처치하면 도착)

        if (type == null || type.isEmpty()) {
            // 레벨당 실제 증가량("스탯구매로 인해 증가량도 표기해달라" 요청) -- 퍼센트 스탯(공격력
            // 최대/체력)은 현재까지 누적된 % + 다음 레벨에 추가되는 %p, 최소공격력은 고정 데미지
            // 하한이라 현재/다음 수치를 그대로 보여준다. 값은 computeEffectiveStat()과 공유하는
            // ATK_PCT_PER_LV/HP_PCT_PER_LV/MIN_DMG_PER_LV 상수 기준이라 실제 전투 계산과 항상 일치.
            int atkPct = (int) Math.round(ATK_PCT_PER_LV * 100 * atkMaxLv);
            int atkPctPerLv = (int) Math.round(ATK_PCT_PER_LV * 100);
            int hpPct = (int) Math.round(HP_PCT_PER_LV * 100 * hpLv);
            int hpPctPerLv = (int) Math.round(HP_PCT_PER_LV * 100);
            int minDmgCur = atkMinLv * MIN_DMG_PER_LV;

            // "보기 불편하다" 요청 -- 라벨을 더 짧고 직관적으로(최대→%, 최소→+) 바꾸고, 스탯
            // 항목 사이사이에 빈 줄을 넣어 구분되게 함.
            StringBuilder sb = new StringBuilder(userName).append("님의 스탯 구매 현황 (현재 구간 상한 ").append(cap).append(")," + NL);
            sb.append(NL);
            sb.append("공격력(%) Lv").append(atkMaxLv).append(" / ").append(cap)
              .append(" (현재 +").append(atkPct).append("%, 다음 레벨 +").append(atkPctPerLv).append("%p)")
              .append(" — 다음 비용 ").append(50 * (atkMaxLv + 1)).append(" PP").append(NL);
            sb.append(NL);
            sb.append("공격력(+) Lv").append(atkMinLv).append(" / ").append(cap)
              .append(" (현재 최소데미지 +").append(minDmgCur).append(", 다음 레벨 +").append(MIN_DMG_PER_LV).append(")")
              .append(" — 다음 비용 ").append(50 * (atkMinLv + 1)).append(" PP").append(NL);
            sb.append(NL);
            sb.append("체력 Lv").append(hpLv).append(" / ").append(cap)
              .append(" (현재 +").append(hpPct).append("%, 다음 레벨 +").append(hpPctPerLv).append("%p)")
              .append(" — 다음 비용 ").append(50 * (hpLv + 1)).append(" PP").append(NL);
            sb.append(NL);
            sb.append("📈 다음 상한 ").append(cap + 5).append("은 ").append(nextVillageFloor)
              .append("층 마을 도달 시 열립니다 (그 앞 보스 처치 필요).").append(NL);
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
        if (curLv >= cap) {
            return "현재 구간에서는 더 이상 강화할 수 없습니다. " + nextVillageFloor + "층 마을에 도달하면 상한이 "
                    + (cap + 5) + "로 늘어납니다.";
        }

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
        String gain;
        if ("min".equals(field)) {
            gain = "최소데미지 +" + MIN_DMG_PER_LV;
        } else {
            int pctPerLv = (int) Math.round(("atk".equals(field) ? ATK_PCT_PER_LV : HP_PCT_PER_LV) * 100);
            gain = "+" + pctPerLv + "%p";
        }
        return type + " 스탯을 강화했습니다! (Lv" + curLv + " → Lv" + (curLv + 1) + ", " + gain + ")";
    }

    /** 웹 SPA 상점탭 스탯 UI용 — 현재 레벨/상한/다음 상한이 열리는 층·비용을 구조화해서 반환. */
    @Override
    public HashMap<String, Object> statShopInfo(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        HashMap<String, Object> stat = dao.selectUserStat(userName);
        int atkMaxLv = stat == null ? 0 : intVal(stat.get("ATK_MAX_LV"), 0);
        int atkMinLv = stat == null ? 0 : intVal(stat.get("ATK_MIN_LV"), 0);
        int hpLv     = stat == null ? 0 : intVal(stat.get("HP_LV"), 0);
        int unlockedBlock = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int cap = statCapFor(unlockedBlock);

        HashMap<String, Object> result = new HashMap<>();
        result.put("atkMaxLv", atkMaxLv);
        result.put("atkMinLv", atkMinLv);
        result.put("hpLv", hpLv);
        result.put("cap", cap);
        result.put("nextCap", cap + 5);
        result.put("nextVillageFloor", unlockedBlock + 10);
        result.put("nextCostAtkMax", 50 * (atkMaxLv + 1));
        result.put("nextCostAtkMin", 50 * (atkMinLv + 1));
        result.put("nextCostHp", 50 * (hpLv + 1));
        // "레벨당 증가량도 표기해달라" 요청 -- 현재까지 누적된 보너스 + 레벨당 증가폭(퍼센트
        // 스탯은 %, 최소공격력은 고정 데미지). computeEffectiveStat()과 같은 상수를 써서 실제
        // 전투 계산과 항상 일치.
        int atkPctPerLv = (int) Math.round(ATK_PCT_PER_LV * 100);
        int hpPctPerLv = (int) Math.round(HP_PCT_PER_LV * 100);
        result.put("atkPctPerLv", atkPctPerLv);
        result.put("hpPctPerLv", hpPctPerLv);
        result.put("minDmgPerLv", MIN_DMG_PER_LV);
        result.put("atkPctCur", atkPctPerLv * atkMaxLv);
        result.put("hpPctCur", hpPctPerLv * hpLv);
        result.put("minDmgCur", MIN_DMG_PER_LV * atkMinLv);
        return result;
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

    /** 웹 SPA 캐릭터 상세 카드용 — 장비/스탯구매 보너스까지 반영한 유효 스탯. 대상이 없으면 [0,0,0]. */
    @Override
    public int[] companionEffectiveStat(String userName, int companionId) {
        HashMap<String, Object> target = null;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("COMPANION_ID"), -1) == companionId) { target = c; break; }
        }
        if (target == null) return new int[]{ 0, 0, 0 };
        String job = strVal(target.get("CLASS"), "WARRIOR");
        int grade = intVal(target.get("GRADE"), 1);
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(companionId);
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int[] eff = computeEffectiveStat(job, grade, equips, userStat);
        return new int[]{ eff[0], eff[1], eff[2] };
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

    /** /장비해제 M — M번째 파티원(equipWear의 companionIdx와 동일 기준)이 착용 중인 장비 전부 해제 */
    @Override
    @Transactional
    public String equipUnwearAll(String userName, int companionIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 변경할 수 없습니다.";
        }
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) party.add(c);
        }
        if (companionIdx < 1 || companionIdx > party.size()) return "잘못된 동료 번호입니다. /파티편성을 확인하세요.";
        HashMap<String, Object> target = party.get(companionIdx - 1);
        int companionId = intVal(target.get("COMPANION_ID"), 0);

        String job = JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "?");
        String name = strVal(target.get("NAME"), job);
        int unequipped = unequipAllForCompanion(companionId);
        if (unequipped == 0) return job + "(" + name + ")은(는) 착용 중인 장비가 없습니다.";
        return "🧺 " + job + "(" + name + ")의 장비 " + unequipped + "개를 전부 해제했습니다. (/장비목록의 [미착용]으로 이동)";
    }

    /**
     * 웹 SPA 전용: 파티 슬롯 시트에서 장비 부위 하나만 콕 집어 해제("장비도 해제하는 기능 넣어줘"
     * 요청, 2026-09-06) -- equipUnwearAll처럼 그 동료 전체가 아니라 이 장비 하나만. 다른 웹
     * 전용 액션들과 달리 별도 "번호" 재계산 없이 화면에 이미 내려간 EQUIP_ID를 그대로 쓴다
     * (미착용 장비의 idx처럼 매번 재계산되는 번호가 아니라 DB 고유값이라 그대로 써도 안전).
     */
    @Override
    @Transactional
    public String equipUnwearOne(String userName, int equipId) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 변경할 수 없습니다.";
        }
        HashMap<String, Object> found = null;
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (intVal(e.get("EQUIP_ID"), -1) == equipId) { found = e; break; }
        }
        if (found == null || found.get("EQUIPPED_COMPANION_ID") == null) {
            return "이미 해제되었거나 존재하지 않는 장비입니다.";
        }
        HashMap<String, Object> unwear = new HashMap<>();
        unwear.put("equipId", equipId);
        unwear.put("equippedCompanionId", null);
        dao.updateEquipEquippedCompanion(unwear);
        String part = strVal(found.get("PART"), "");
        int grade = intVal(found.get("GRADE"), 1);
        return "🧺 " + partNameOf(part) + " ★" + grade + " 을(를) 해제했습니다. (미착용 목록으로 이동)";
    }

    /** 선택권 등급(3/4/5)에 대응하는 진행상태 컬럼/필드 접미사("" 또는 "G4"/"G5"). */
    private String ticketSuffix(int grade) {
        return grade == 3 ? "" : "G" + grade;
    }

    /**
     * ★N 동료 선택권 사용(웹 UI 전용). 등급(3/4/5, 구간에 따라 지급된 것 그대로)/직업이
     * 확정이라 가챠와 달리 실패가 없다.
     * [알려진 단순화] 가챠의 "중복 동료 20% PP 환급"은 여기선 적용 안 함 -- 선택권 자체가
     * 무상 보상이라 이미 원가가 0이고, 중복이어도 이름만 다시 랜덤일 뿐 손해가 아니라서 생략.
     */
    @Override
    @Transactional
    public String redeemCompanionChoiceTicket(String userName, String job, int grade) {
        if (grade != 3 && grade != 4 && grade != 5) return "선택권 등급 값이 올바르지 않습니다.";
        HashMap<String, Object> p = getOrInitProgress(userName);
        String field = "companionChoiceTicket" + ticketSuffix(grade);
        String column = "COMPANION_CHOICE_TICKET" + (grade == 3 ? "" : "_G" + grade);
        int have = intVal(p.get(column), 0);
        if (have <= 0) return "보유한 ★" + grade + " 동료 선택권이 없습니다.";
        if (!JOB_NAME.containsKey(job)) return "직업 값이 올바르지 않습니다.";

        String[] namePool = NAME_POOL_BY_JOB_GRADE.get(job)[grade - 1];
        String name = namePool[RND.nextInt(namePool.length)];
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

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, have - 1);
        dao.updateUserProgress(up);

        return "🎉 " + JOB_NAME.get(job) + "(" + name + ") ★" + grade + " 동료를 획득했습니다! (선택권 사용, 남은 ★" + grade + " 선택권 " + (have - 1) + "장)";
    }

    /** ★N 무기(WEAPON 부위) 선택권 사용(웹 UI 전용). */
    @Override
    @Transactional
    public String redeemWeaponChoiceTicket(String userName, String job, int grade) {
        if (grade != 3 && grade != 4 && grade != 5) return "선택권 등급 값이 올바르지 않습니다.";
        HashMap<String, Object> p = getOrInitProgress(userName);
        String field = "weaponChoiceTicket" + ticketSuffix(grade);
        String column = "WEAPON_CHOICE_TICKET" + (grade == 3 ? "" : "_G" + grade);
        int have = intVal(p.get(column), 0);
        if (have <= 0) return "보유한 ★" + grade + " 무기 선택권이 없습니다.";
        if (!JOB_NAME.containsKey(job)) return "직업 값이 올바르지 않습니다.";

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", job);
        e.put("part", "WEAPON");
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, have - 1);
        dao.updateUserProgress(up);

        return "🎉 " + JOB_NAME.get(job) + "용 무기 ★" + grade + " (" + equipBonusText("WEAPON", grade) + ") 획득! (선택권 사용, 남은 ★" + grade + " 선택권 " + (have - 1) + "장)";
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
