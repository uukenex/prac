package my.prac.core.prjbot.service.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

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

    // [2026-09-17] "장비 분류 통합, 먼저 무기류 -- 검(전사/도적)/지팡이(도사/마법사)/활(궁수)
    // 3종으로" 요청으로 시작해서, 같은 날 "갑옷/투구도 같은 3그룹으로, 악세서리 3종은 전부
    // 공용으로" 요청까지 이어짐 -- 기존엔 TBOT_S5_USER_EQUIP.CLASS가 companion CLASS와
    // 1:1(전사무기는 전사만)이었는데, WEAPON/ARMOR/HELMET은 이 3그룹 값(무기: SWORD/STAFF/BOW,
    // 갑옷: PLATE/ROBE/JACKET, 투구: HELM/HEADBAND/PLUME)으로, NECKLACE/RING/BRACELET은
    // 5직업 전부가 쓸 수 있는 단일값 COMMON으로 통합한다. CLASS 컬럼 자체는 VARCHAR2(10)로
    // 제약(CHECK/FK) 없이 자유 문자열이라 ALTER TABLE 불필요(S5_ACCESSORY_GACHA.sql이 PART에
    // 새 값 추가할 때 썼던 것과 동일한 무스키마 확장 패턴).
    //
    // [마이그레이션 순서 중요] 기존 라이브 데이터엔 아직 예전 직업명(WARRIOR/ROGUE/MAGE/
    // PRIEST/ARCHER)이 그대로 남아있다. 이 코드가 배포된 "직후"에도(마이그레이션 SQL을 아직
    // 안 돌린 시점에도) 기존 장비가 계속 착용 가능해야 하므로, weaponAllowedJobs()가 신규값과
    // 구값(직업명, 자기 자신 1명짜리 그룹으로 취급) 둘 다 이해하도록 짰다 -- "코드 먼저
    // 배포(구/신 데이터 둘 다 호환) -> 그 다음에 마이그레이션 SQL 실행"이 안전한 순서.
    private static final HashMap<String, java.util.Set<String>> WEAPON_CLASS_JOBS = new HashMap<String, java.util.Set<String>>() {{
        put("SWORD", new java.util.HashSet<>(java.util.Arrays.asList("WARRIOR", "ROGUE")));
        put("STAFF", new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST")));
        put("BOW",   new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("PLATE", new java.util.HashSet<>(java.util.Arrays.asList("WARRIOR", "ROGUE")));
        put("ROBE",  new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST")));
        put("JACKET", new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("HELM",  new java.util.HashSet<>(java.util.Arrays.asList("WARRIOR", "ROGUE")));
        put("HEADBAND", new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST")));
        put("PLUME", new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("COMMON", new java.util.HashSet<>(java.util.Arrays.asList(JOB_KEYS))); // 악세서리 -- 전 직업 착용 가능
    }};
    private static final HashMap<String, String> JOB_TO_WEAPON_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "SWORD"); put("ROGUE", "SWORD");
        put("MAGE", "STAFF");    put("PRIEST", "STAFF");
        put("ARCHER", "BOW");
    }};
    private static final HashMap<String, String> JOB_TO_ARMOR_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "PLATE"); put("ROGUE", "PLATE");
        put("MAGE", "ROBE");     put("PRIEST", "ROBE");
        put("ARCHER", "JACKET");
    }};
    private static final HashMap<String, String> JOB_TO_HELMET_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "HELM"); put("ROGUE", "HELM");
        put("MAGE", "HEADBAND"); put("PRIEST", "HEADBAND");
        put("ARCHER", "PLUME");
    }};
    private static final HashMap<String, String> WEAPON_CLASS_NAME = new HashMap<String, String>() {{
        put("SWORD", "검"); put("STAFF", "지팡이"); put("BOW", "활");
        put("PLATE", "갑주"); put("ROBE", "로브"); put("JACKET", "재킷");
        put("HELM", "투구"); put("HEADBAND", "머리띠"); put("PLUME", "깃장식");
        // COMMON은 부위마다 라벨이 다르므로("공용목걸이" 등) 여기 넣지 않고 equipClassLabel에서 따로 처리.
    }};

    /** equip의 CLASS 원본값(신규 그룹값 또는 구 직업명 둘 다)을 받아 "착용 가능한 동료 직업
     *  집합"으로 정규화한다 -- 마이그레이션 전후 데이터가 섞여 있어도 항상 정답을 낸다. */
    private java.util.Set<String> weaponAllowedJobs(String equipClass) {
        java.util.Set<String> group = WEAPON_CLASS_JOBS.get(equipClass);
        if (group != null) return group;
        return java.util.Collections.singleton(equipClass); // 구 데이터(직업명 그대로) -- 그 직업 1명짜리 그룹
    }

    /** 장비 CLASS 표시용 라벨 -- 신규 그룹값(SWORD 등)이면 그 그룹 이름, COMMON(악세서리
     *  공용)이면 "공용"+부위명, 그 외(마이그레이션 전 구 데이터)는 기존처럼 직업명. */
    private String equipClassLabel(String equipClass, String part) {
        if ("COMMON".equals(equipClass)) return "공용" + partNameOf(part);
        if (WEAPON_CLASS_NAME.containsKey(equipClass)) return WEAPON_CLASS_NAME.get(equipClass);
        return JOB_NAME.getOrDefault(equipClass, equipClass);
    }

    /** [2026-09-17] "★6무기 라고만 되어있는데 ★6검/지팡이/활로 각각 명칭 넣어달라" 요청 -- 그룹
     *  이름(검/갑주/투구/공용목걸이 등)은 이미 그 자체로 완결된 명사라 partNameOf(부위명)를 또
     *  안 붙인다(안 그러면 "검 무기"처럼 겹쳐 보임). 아직 그룹 통합 전인 구 데이터(직업명 그대로)
     *  만 기존처럼 "직업 부위"(예: "전사 투구") 형태를 유지. */
    private String equipFullLabel(String equipClass, String part) {
        if ("COMMON".equals(equipClass) || WEAPON_CLASS_NAME.containsKey(equipClass)) return equipClassLabel(equipClass, part);
        return equipClassLabel(equipClass, part) + " " + partNameOf(part);
    }

    /** 뽑기(가챠)로 새로 생성되는 장비의 CLASS를 정한다 -- WEAPON/ARMOR/HELMET은 롤된 job이
     *  속한 그룹값으로, NECKLACE/RING/BRACELET은 무조건 COMMON(전 직업 공용)으로. */
    private String equipClassForNewItem(String job, String part) {
        switch (part) {
            case "WEAPON": return JOB_TO_WEAPON_CLASS.getOrDefault(job, job);
            case "ARMOR":  return JOB_TO_ARMOR_CLASS.getOrDefault(job, job);
            case "HELMET": return JOB_TO_HELMET_CLASS.getOrDefault(job, job);
            default:       return "COMMON"; // NECKLACE/RING/BRACELET
        }
    }

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

    /** [2026-09-11] "61층+ 두 마리 몬스터는 이름이 같아서 반격 로그에서 헷갈린다" 요청 --
     *  진짜로 두 마리인(dualMonster) 경우에 한해 몬스터 이름 뒤에 "I"/"II"를 붙여 구분한다.
     *  보스의 "1마리가 2명을 때리는" 다중타겟(doubleTarget)은 애초에 같은 개체라 헷갈릴 일이
     *  없으므로 호출하는 쪽에서 dualMonster일 때만 이 오버로드를 쓴다(그 외엔 일반 버전 사용). */
    private String eliteMonsterName(int floor, HashMap<String, Object> mon, boolean elite, int ti) {
        return eliteMonsterName(floor, mon, elite) + " " + (ti == 0 ? "I" : "II");
    }

    // 장비 등급별 보너스 [투구고정,투구%, 무기고정,무기%, 갑옷고정,갑옷%], index0=★1
    // [2026-09-18] ★7(전설) 추가 -- computeEffectiveStat()이 EQUIP_BONUS[grade-1]로 그대로
    // 인덱싱하므로 행 하나만 늘리면 별도 분기 없이 자동 적용됨. ★6 대비 약 1.8배 수준.
    private static final double[][] EQUIP_BONUS = {
        { 30, 0.05,   5, 0.05,   3, 0.05 },
        { 45, 0.07,   8, 0.07,   5, 0.07 },
        { 75, 0.10,  13, 0.10,   8, 0.10 },
        { 150, 0.15, 25, 0.15,  15, 0.15 },
        { 350, 0.22, 60, 0.22,  35, 0.22 },
        { 800, 0.35, 150, 0.35, 80, 0.35 },
        { 1450, 0.45, 270, 0.45, 145, 0.45 },
    };

    // [2026-09-18] "50층 이상 보스층 처치 시 확률로 전설의조각 드랍, 조각 10개로 전설제작"
    // 요청 -- 보스층(59/69/79/89/99) 순서대로 드랍확률 5%→25%로 균등증가(5%p씩). "최대
    // 2개"였다가 같은 날 후속 메시지("일반사용자에겐 제작은 아직 오픈하지 말고, 보스처치시
    // 최대1개")로 최대 1개로 축소.
    private static final int[] LEGEND_FRAGMENT_BOSS_FLOOR = { 59, 69, 79, 89, 99 };
    private static final int[] LEGEND_FRAGMENT_DROP_PCT   = { 5, 10, 15, 20, 25 };
    private static final int LEGEND_CRAFT_COST = 10;      // 전설제작 소모 조각 개수
    private static final int LEGEND_CRAFT_SUCCESS_PCT = 30; // 전설제작 성공률
    // [2026-09-21] "전설은 한번 만들어지면 전설의조각 9개로 바꿀수있도록도 해줘" 요청 --
    // 제작 비용(10개)보다 1개 적게(9개) 돌려줘서 무손실 순환을 막는 조각 싱크.
    private static final int LEGEND_DISENCHANT_REFUND = 9;
    private static final int BOSS_DAILY_KILL_LIMIT = 3;   // 보스 하루 처치 제한(모든 보스층 공통 카운터)

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
        put("TRAP",   "🕳️ 함정");  put("SPECIAL", "🏚️ 무너진 사원"); // [2026-09-18] 舊 "✨ 특수"(워프포인트)
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

    // [2026-09-21] "주사위 굴림수 기준 하루 1200(1000+200) 제한을, 주사위(=전투 턴)는 빼고
    // 타일 이동수 기준 400(+카톡보너스 100=500)으로 바꿔달라" 요청 -- 이름을
    // DAILY_DICE_LIMIT/KAKAO_BONUS_DICE에서 DAILY_MOVE_LIMIT/KAKAO_BONUS_MOVE로 바꾸고,
    // 카운트 대상도 "모든 굴림"에서 "전투 중이 아닌 굴림(=보드 이동)"만으로 좁힘
    // (checkAndBumpDailyDiceLimit 호출부, rollDice() 참고 -- IN_COMBAT이면 이 체크 자체를
    // 건너뛰어 전투 턴은 무제한). 컬럼(DICE_ROLL_COUNT_TODAY/DICE_ROLL_DATE)은 그대로 재사용
    // (의미만 "이동만 카운트"로 좁혀짐, 마이그레이션 불필요). 위 쿨타임들과 같은 이유로
    // DB(TBOT_S5_CONFIG) config화 -- 재배포 없이 /갱신으로 값만 바꿀 수 있게.
    private static volatile int DAILY_MOVE_LIMIT = 400;

    // 채널(WEB/CHAT) 무관하게 공유하는 총 이동 카운터(DICE_ROLL_COUNT_TODAY) 기준으로, 웹은
    // DAILY_MOVE_LIMIT까지만, 카카오톡(CHAT)은 거기에 이 보너스를 더한 값(400+100=500)까지
    // 계속 가능하다. 채널을 섞어 쓰든 한쪽만 쓰든 결과는 항상 "총합이 웹 한도를 넘으면 웹
    // 차단, 카톡 한도를 넘으면 둘 다 차단"으로 동일하다 -- checkAndBumpDailyDiceLimit 참고.
    private static volatile int KAKAO_BONUS_MOVE = 100;

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
                    } else if ("DAILY_MOVE_LIMIT".equals(key)) {
                        DAILY_MOVE_LIMIT = Integer.parseInt(val);
                    } else if ("KAKAO_BONUS_MOVE".equals(key)) {
                        KAKAO_BONUS_MOVE = Integer.parseInt(val);
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
        loadBalanceV2();
        return "🗼 시즌5 설정 갱신 완료 (칸이동 " + MOVE_COOLDOWN_SEC + "초 / 전투중 " + COMBAT_COOLDOWN_SEC
                + "초 / 전투종료 " + COMBAT_END_COOLDOWN_SEC + "초 / 하루 이동 한도 웹 " + DAILY_MOVE_LIMIT
                + "회·카톡 " + (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) + "회 / 자동사냥 시간당 "
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
        int[][] gradeBase = GRADE_BASE_V2;
        int[] base = (BALANCE_V2_ENABLED && gradeBase != null && grade - 1 < gradeBase.length && gradeBase[grade - 1] != null)
                ? gradeBase[grade - 1] : GRADE_BASE[grade - 1];
        double[] mult = JOB_MULT.get(job);
        int hp  = (int) Math.round(base[0] * mult[0]);
        int atk = (int) Math.round(base[1] * mult[1]);
        int def = (int) Math.round(base[2] * mult[2]);
        return new int[]{ hp, atk, def };
    }

    /** [2026-09-14] 한계돌파(★N 동료를 중복으로 또 뽑으면 그 동료 개체에 붙는 강화, 최대
     *  6단계) -- 등급/장비/스탯구매 전부 반영한 최종 수치에 마지막으로 곱해진다("등급 하나를
     *  통째로 올린 것" 같은 효과가 아니라 순수 배율 보너스). pullCompanionCore()의 중복 처리
     *  참고.
     *  [2026-09-14 재수정] 처음엔 단계당 균일 +10%(6단계 +60%)였는데, "1단계 10%/2단계 15%/
     *  3단계 20%/4단계 25%/5단계 30%/6단계 35%로 해달라"는 요청으로 단계마다 다른 값을 쓰는
     *  배열로 교체(인덱스 0=0단계=보너스 없음, 그대로 안 씀). limitBreakPct() 참고.
     *  [2026-09-15 재조정] "한계돌파 성능이 너무 안좋다, ★4가 6돌파하면 ★6등급(0돌파)보다
     *  5%정도 쌔지도록 비율을 올려달라" 요청으로 6단계 +267.5%까지 올렸었으나("267.5/35배"
     *  스케일업), "너무 과하다"는 피드백으로 즉시 되돌림.
     *  [2026-09-15 재재조정] "30,60,90,120,150,180으로 교체해줘 (돌파당 30%강화)" 요청 --
     *  단계별로 다른 값을 쓰던 표를 버리고 돌파당 균일 +30%(6단계 +180%)로 단순화. */
    private static final double[] LIMIT_BREAK_PCT = { 0, 0.30, 0.60, 0.90, 1.20, 1.50, 1.80 };
    private static final int LIMIT_BREAK_MAX = 6;

    // ================================================================
    // 밸런스 V2(2026-09-21, "전투가 너무 빨리 끝난다, 1~100층을 계단식 아닌 완전 선형구조로"
    // 요청) -- TBOT_S5_MONSTER_INFO_V2/GRADE_BASE_V2/EQUIP_BONUS_V2/LIMIT_BREAK_V2 4테이블을
    // 서버 기동 시(loadBalanceV2, @PostConstruct) 메모리로 읽어들여 기존 하드코딩 상수/구
    // 테이블을 "오버레이"로 대체한다. BALANCE_V2_ENABLED 하나로 전체 스위치 -- 문제가 생기면
    // false로 바꾸고 재배포하면 기존(V1) 수치로 즉시 롤백된다. 테이블 로딩 실패 시(서버 기동
    // 시점에 DB 미접속 등) 각 맵/배열이 비어있는 채로 남고, 아래 조회 지점들이 그 경우 자동으로
    // 기존 V1 값으로 폴백한다(loadConfig와 동일한 방어적 설계 원칙).
    // ================================================================
    private static final boolean BALANCE_V2_ENABLED = true;
    private static volatile Map<Integer, int[]> MONSTER_V2 = new HashMap<>(); // floor -> [hp, atk, def]
    private static volatile int[][] GRADE_BASE_V2 = null;     // [grade-1][hp, atk, def]
    private static volatile double[][] EQUIP_BONUS_V2 = null; // [grade-1][helmFlat, helmPct, wepFlat, wepPct, armFlat, armPct]
    private static volatile double[] LIMIT_BREAK_V2 = null;   // [level]

    /** 서버 기동 시(및 /갱신 시) 밸런스 V2 4테이블을 메모리로 로드. 실패해도 V1으로 계속 동작. */
    @PostConstruct
    public void loadBalanceV2() {
        try {
            Map<Integer, int[]> monsterMap = new HashMap<>();
            for (HashMap<String, Object> row : dao.selectMonsterInfoV2List()) {
                int floor = intVal(row.get("FLOOR"), -1);
                if (floor < 0) continue;
                monsterMap.put(floor, new int[]{
                        intVal(row.get("HP_VALUE"), 0), intVal(row.get("ATK_VALUE"), 0), intVal(row.get("DEF_VALUE"), 0)
                });
            }
            MONSTER_V2 = monsterMap;
        } catch (Exception ignore) {
            // 로드 실패 -- MONSTER_V2는 이전 값(또는 초기 빈 맵) 유지, 조회부에서 V1 폴백
        }
        try {
            List<HashMap<String, Object>> rows = dao.selectGradeBaseV2List();
            int[][] grade = new int[rows.size()][3];
            for (HashMap<String, Object> row : rows) {
                int idx = intVal(row.get("GRADE"), 0) - 1;
                if (idx < 0 || idx >= grade.length) continue;
                grade[idx] = new int[]{ intVal(row.get("HP_VALUE"), 0), intVal(row.get("ATK_VALUE"), 0), intVal(row.get("DEF_VALUE"), 0) };
            }
            GRADE_BASE_V2 = grade;
        } catch (Exception ignore) {
            GRADE_BASE_V2 = null;
        }
        try {
            List<HashMap<String, Object>> rows = dao.selectEquipBonusV2List();
            double[][] equip = new double[rows.size()][6];
            for (HashMap<String, Object> row : rows) {
                int idx = intVal(row.get("GRADE"), 0) - 1;
                if (idx < 0 || idx >= equip.length) continue;
                equip[idx] = new double[]{
                        ((Number) row.get("HELM_FLAT")).doubleValue(), ((Number) row.get("HELM_PCT")).doubleValue(),
                        ((Number) row.get("WEAPON_FLAT")).doubleValue(), ((Number) row.get("WEAPON_PCT")).doubleValue(),
                        ((Number) row.get("ARMOR_FLAT")).doubleValue(), ((Number) row.get("ARMOR_PCT")).doubleValue()
                };
            }
            EQUIP_BONUS_V2 = equip;
        } catch (Exception ignore) {
            EQUIP_BONUS_V2 = null;
        }
        try {
            List<HashMap<String, Object>> rows = dao.selectLimitBreakV2List();
            double[] lb = new double[rows.size()];
            for (HashMap<String, Object> row : rows) {
                int idx = intVal(row.get("LB_LEVEL"), -1);
                if (idx < 0 || idx >= lb.length) continue;
                lb[idx] = ((Number) row.get("PCT")).doubleValue();
            }
            LIMIT_BREAK_V2 = lb;
        } catch (Exception ignore) {
            LIMIT_BREAK_V2 = null;
        }
    }

    /** 한계돌파 N단계의 스탯 배율(%) -- 위 LIMIT_BREAK_PCT 표 참고, 범위 밖이면 클램프. */
    private double limitBreakPct(int limitBreak) {
        int lv = Math.max(0, Math.min(limitBreak, LIMIT_BREAK_MAX));
        double[] v2 = LIMIT_BREAK_V2;
        if (BALANCE_V2_ENABLED && v2 != null && lv < v2.length) return v2[lv];
        return LIMIT_BREAK_PCT[lv];
    }

    /** 등급+직업 베이스 스탯에 장비/스탯구매/한계돌파 보너스를 반영한 최종 전투 스탯. [hp, atk, def, minDmgFloor] */
    private int[] computeEffectiveStat(String job, int grade, List<HashMap<String, Object>> equips, HashMap<String, Object> userStat, int limitBreak) {
        int[] base = calcBaseStat(job, grade);
        double hp = base[0], atk = base[1], def = base[2];

        if (equips != null) {
            for (HashMap<String, Object> e : equips) {
                int eg = intVal(e.get("GRADE"), 1);
                double[][] equipV2 = EQUIP_BONUS_V2;
                double[] b = (BALANCE_V2_ENABLED && equipV2 != null && eg - 1 < equipV2.length && equipV2[eg - 1] != null)
                        ? equipV2[eg - 1] : EQUIP_BONUS[eg - 1];
                String part = strVal(e.get("PART"), "");
                if ("HELMET".equals(part)) hp += b[0] + base[0] * b[1];
                else if ("WEAPON".equals(part)) atk += b[2] + base[1] * b[3];
                else if ("ARMOR".equals(part)) def += b[4] + base[2] * b[5];
                // [2026-09-15] 악세서리(목걸이/반지/팔찌) 신설 -- 장비뽑기 말고 별도 악세뽑기로
                // 얻는 3종. 기존 부위 둘의 절반씩을 동시에 준다(목걸이=무기+갑옷, 반지=무기+
                // 투구, 팔찌=갑옷+투구) -- 무기(ATK)/갑옷(DEF)/투구(HP) 보너스 공식을 그대로
                // 절반만 적용. pullAccessoryCore() 참고.
                else if ("NECKLACE".equals(part)) { // 무기(ATK)+갑옷(DEF) 절반씩
                    atk += (b[2] + base[1] * b[3]) / 2.0;
                    def += (b[4] + base[2] * b[5]) / 2.0;
                } else if ("RING".equals(part)) { // 무기(ATK)+투구(HP) 절반씩
                    atk += (b[2] + base[1] * b[3]) / 2.0;
                    hp += (b[0] + base[0] * b[1]) / 2.0;
                } else if ("BRACELET".equals(part)) { // 갑옷(DEF)+투구(HP) 절반씩
                    def += (b[4] + base[2] * b[5]) / 2.0;
                    hp += (b[0] + base[0] * b[1]) / 2.0;
                }
            }
        }

        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int atkMinLv = userStat == null ? 0 : intVal(userStat.get("ATK_MIN_LV"), 0);
        int hpLv     = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        atk *= (1 + ATK_PCT_PER_LV * atkMaxLv);
        hp  *= (1 + HP_PCT_PER_LV * hpLv);
        if (limitBreak > 0) {
            double lbMult = 1 + limitBreakPct(limitBreak);
            hp *= lbMult; atk *= lbMult; def *= lbMult;
        }
        int minDmgFloor = atkMinLv * MIN_DMG_PER_LV;

        return new int[]{ (int) Math.round(hp), (int) Math.round(atk), (int) Math.round(def), minDmgFloor };
    }

    // ================================================================
    // 전투력 (Combat Power)
    // ================================================================
    // [2026-09-15 신설] "/ㅌㅈㅂ에 전투력을 수치화해서 보여주고, 층별 전투력도 보여주고,
    // 지금 몇 층이 괜찮은 사냥터인지 추천해달라" 요청.
    // 실제 전투 공식(파티 공격: ATK*굴림-몬스터DEF, 몬스터 반격: 몬스터ATK*굴림-DEF, 굴림
    // 자체는 51층+ 기준 대략 6~20 사이라 평균이 10 안팎)에 착안해, "한 방 피해량"에 직결되는
    // ATK/DEF는 굵직한 가중치를, "몇 대나 버티는지"를 나타내는 HP는 작은 가중치를 줘서 셋을
    // 하나의 스칼라로 합산한다. 정밀 전투 시뮬레이션이 아니라 상대 비교(내 파티 vs 이 층
    // 몬스터)용 잠정 지표 -- 가중치는 실측 후 조정 가능.
    private static final double CP_ATK_WEIGHT = 10.0;
    private static final double CP_DEF_WEIGHT = 8.0;
    private static final double CP_HP_WEIGHT = 1.0;
    // 파티 전투력이 그 층 몬스터 전투력의 이 배수 이상이면 "여유 있게 farming 가능한 층"으로
    // 본다(추천 사냥터 산정 기준). 낮추면 더 위험한 고층까지 추천, 높이면 더 보수적으로 추천.
    private static final double SAFE_HUNT_RATIO = 1.3;

    private long combatPower(double hp, double atk, double def) {
        return Math.round(hp * CP_HP_WEIGHT + atk * CP_ATK_WEIGHT + def * CP_DEF_WEIGHT);
    }

    /** 파티(편성된 동료, PARTY_SLOT 있는 동료만) 합산 전투력. */
    private long partyCombatPower(String userName) {
        long total = 0;
        for (HashMap<String, Object> c : companionsWithEffectiveStats(userName)) {
            if (c.get("PARTY_SLOT") == null) continue;
            total += combatPower(intVal(c.get("EFF_HP"), 0), intVal(c.get("EFF_ATK"), 0), intVal(c.get("EFF_DEF"), 0));
        }
        return total;
    }

    /** 그 층 "일반" 몬스터(BOSS_YN='N')의 전투력 -- applyHardcoreFloorScale로 51층+ 구간 내
     *  선형 스케일(1번째 사냥터층 50% ~ 8번째 100%)까지 반영한 실제 그 층 기준값. */
    private long floorMonsterCombatPower(int floor) {
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(blockNo(floor), "N"), floor);
        if (mon == null) return 0;
        return combatPower(((Number) mon.get("HP_VALUE")).doubleValue(),
                ((Number) mon.get("ATK_VALUE")).doubleValue(), ((Number) mon.get("DEF_VALUE")).doubleValue());
    }

    /** [2026-09-16] "현재 갈 수 있는(이미 밟아본) 층 말고, 진짜 전투력 기준으로 어느 층이
     *  좋은 사냥터인지 추천해달라" 요청 -- 기존엔 MAX_FLOOR_REACHED(직접 걸어서 밟아본 최고
     *  층)를 상한으로 써서, 아직 그 블록 안을 다 안 걸어봤으면(예: /탑올라가기로 방금 새
     *  블록에 도착) 실제로 바로 갈 수 있는 층인데도 추천 대상에서 빠졌다. UNLOCKED_BLOCK
     *  (보스 처치로 열린 최고 마을층, 그 블록의 1~8층은 /층변경으로 즉시 이동 가능)+9를
     *  상한으로 바꿔서 "지금 당장 이동 가능한 전체 범위"를 기준으로 추천한다.
     *  SAFE_HUNT_RATIO배 이상 여유 있는 "가장 높은" 층을 추천(같은 조건이면 보상이 더 좋은
     *  고층 우선). 만족하는 층이 하나도 없으면(1층조차 버거움) 0 반환. */
    private int recommendHuntFloor(long myPower, int unlockedBlock) {
        int best = 0;
        int cap = Math.min(unlockedBlock + 9, CONTENT_LOCKED_FLOOR - 1);
        for (int f = 1; f <= cap; f++) {
            int pos = f % 10;
            if (pos < 1 || pos > 8) continue; // 사냥터층만 대상(마을/보스 제외)
            long monPower = floorMonsterCombatPower(f);
            if (monPower > 0 && myPower >= monPower * SAFE_HUNT_RATIO) best = f;
        }
        return best;
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
     * [2026-09-09] "마이너스 주사위 쓸 때 0이 나오면 제자리걸음이라 허무하다, 0 자체가 안
     * 나오게 해달라(맵이동/전투 둘 다)" 요청 -- diceMin이 0 이하로 내려가 0이 굴림 범위에
     * 포함되는 경우([lo,-1]∪[1,hi] 두 구간을 합쳐서 그 안에서만 뽑아 0을 완전히 배제한다.
     * diceMax(hi)는 항상 4 이상이라 posCount(1..hi)는 절대 0이 되지 않음 -- 안전.
     */
    private int rollFace(int diceMin, int diceMax) {
        int lo = Math.min(diceMin, diceMax);
        int hi = diceMax;
        int face;
        if (lo <= 0 && hi >= 1) {
            int negCount = lo < 0 ? -lo : 0;   // [lo..-1] 개수
            int posCount = hi;                 // [1..hi] 개수
            int idx = RND.nextInt(negCount + posCount);
            face = idx < negCount ? lo + idx : (idx - negCount) + 1;
        } else {
            face = RND.nextInt(hi - lo + 1) + lo;
        }
        try { dao.bumpDiceFaceStat(face); } catch (Exception ignore) { }
        return face;
    }

    /** [2026-09-19] "51층+ 몬스터도 주사위를 굴리는데 반격은 자기만의 무작위 면수(51~70층
     *  6~12, 71층+ 8~20)를 쓰는데, 은신 기습(첫턴 선공)만 이 로직 이전에 짜여서 플레이어가
     *  낀 주사위(diceMax, 최대 20이지만 등급이 낮으면 그보다 작음)를 그대로 재사용하고
     *  있었다" 버그 -- 51층 미만은 원래도 몬스터가 플레이어 주사위를 공유하던 구간이라
     *  그대로 두고, 51층 이상만 이 헬퍼로 통일해서 기습/반격이 항상 같은 규칙을 쓰게 한다. */
    private int monsterOwnDiceMax(int floor, int fallbackDiceMax) {
        if (floor < 51) return fallbackDiceMax;
        return (floor <= 70) ? (6 + RND.nextInt(7)) : (8 + RND.nextInt(13));
    }

    /** [2026-09-19] 은신 기습(선공) 피해를 한 대상에게 적용 -- 럭키칸 피해면역
     *  (WARD_COMPANION_ID) 체크, HP 차감, 로그 한 줄까지 한 곳에서 처리한다(단일 타격/
     *  90%↑ 분산타격 두 경로가 공유).
     *  [2026-09-20 정정] "기습으로 사망하지않게 동료 최대체력의 90%까지만 데미지가 들어가도록"
     *  요청 -- 09-19엔 "MAX_AMBUSH_DMG(2만) 넘으면 두 명에게 분산"까지만 하고 분산된 값도
     *  재클램프하지 않아서, 분산 몫(예: 1만)이 여전히 그 동료의 최대체력을 넘어 실제로
     *  즉사하는 사례가 보고됐다("즉사 가능성 자체는 남겨둔다"던 이전 방침을 이번 요청으로
     *  뒤집음). 이제 최종 적용 데미지를 대상 본인의 유효 최대체력 90%로 하드 클램프해서
     *  단일/분산 두 경로 모두 기습만으로는 절대 죽지 않게 한다. */
    private void applyAmbushHit(String userName, HashMap<String, Object> p, HashMap<String, Object> userStat,
            StringBuilder sb, HashMap<String, Object> target, int dmg) {
        String job = strVal(target.get("CLASS"), "WARRIOR");
        int grade = intVal(target.get("GRADE"), 1);
        String name = strVal(target.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(target.get("COMPANION_ID"), 0));
        int[] eff = computeEffectiveStat(job, grade, equips, userStat, intVal(target.get("LIMIT_BREAK"), 0));
        int hardCap = (int) Math.floor(eff[0] * AMBUSH_DEATH_GUARD_PCT);
        if (dmg > hardCap) dmg = Math.max(1, hardCap);
        int wardCid = intVal(p.get("WARD_COMPANION_ID"), 0);
        boolean warded = wardCid > 0 && wardCid == intVal(target.get("COMPANION_ID"), 0);
        if (warded) {
            dmg = 0;
            HashMap<String, Object> wardClearUp = new HashMap<>();
            wardClearUp.put("userName", userName);
            wardClearUp.put("wardCompanionId", 0);
            dao.updateUserProgress(wardClearUp);
            p.put("WARD_COMPANION_ID", 0);
        }
        PP hp = PP.of(((Number) target.get("CUR_HP_VALUE")).doubleValue(), strVal(target.get("CUR_HP_EXT"), ""));
        PP hpAfter = hp.subtract(PP.fromPP(dmg));
        if (PP.toBaseValue(hpAfter) < 0) hpAfter = PP.fromPP(0);
        if (warded) {
            sb.append("🛡️✨ 피해 면역 발동! ").append(jobTag(grade, job, name)).append("이(가) 이번 피해를 완전히 막아냈다! (가호 소모)").append(NL);
        }
        sb.append(jobTag(grade, job, name)).append("에게 ").append(dmg).append("dmg (💗")
          .append(hpAfter.format()).append("/").append(eff[0]).append(")").append(NL);
        writeCompanionHp(target, hpAfter);
    }

    /** 위 rollFace()의 diceMin 인자용 -- 유저가 지금 "선택"해둔 최소 눈금 조정치
     *  (DICE_MIN_ADJUST, -1..+6)를 반영한다(계정 전체 공통 적용, 장착 주사위 등급 무관,
     *  최대치는 항상 diceMax 그대로). 몬스터 자신의 반격 굴림(rollFace(1, monsterDiceMax))
     *  에는 적용하지 않음 -- 플레이어 강화와 무관해야 함.
     *  [2026-09-09 재설계] 원래는 DICE_MIN_BONUS(+구매단계)와 DICE_MIN_MALUS(-구매단계)를
     *  "1+bonus-malus"로 더해서 둘 다 동시에 누적 적용했는데, 웹 UI에서 두 트랙이 각자
     *  "현재 적용중"으로 동시에 하이라이트돼서 "여러 개가 동시에 선택된 버그처럼 보인다"는
     *  신고가 들어왔다 -- 실제로 봐도 "-1과 +2 중 어느 쪽이 지금 적용 중인지" 알 수 없는
     *  설계였음. DICE_MIN_BONUS/DICE_MIN_MALUS는 이제 "각 방향으로 얼마나 구매(해금)해뒀는지"
     *  진행도로만 쓰고, 실제로 지금 굴림에 적용되는 값은 DICE_MIN_ADJUST 하나(단일 선택,
     *  기본값 0)로 분리했다. selectDiceMinAdjust()/buyDiceEnhance() 참고.
     *  [2026-09-11 버그 수정] "마이너스 주사위(-1)를 눌러도 실제로 -1이 안 나온다"는 신고로
     *  확인 -- 강화(+)는 "1+adjust"라 +1 사면 최소눈금 2, +6이면 7로 라벨과 정확히 맞물리는데,
     *  마이너스(adjust=-1)도 같은 식을 쓰면 "1+(-1)=0"이 되어 최소가 0에서 멈추고 절대
     *  -1까지 못 내려갔다(거기다 rollFace()의 "0은 안 나오게" 처리까지 겹치면 사실상 마이너스
     *  주사위가 최소치를 전혀 안 낮추는 것과 똑같아지는 이중 버그). 마이너스는 양수처럼
     *  "1을 기준으로 뺀 값"이 아니라 "그 값 자체가 최소치"가 되도록 분기해서, -1을 선택하면
     *  실제로 diceMin=-1이 되어 라벨과 일치하게 고쳤다(강화(+) 쪽 계산식은 그대로 유지). */
    private int diceMinFor(HashMap<String, Object> p) {
        int adjust = intVal(p.get("DICE_MIN_ADJUST"), 0);
        return adjust >= 0 ? 1 + adjust : adjust;
    }

    private int floorBlockBase(int floor) {
        return (floor / 10) * 10;
    }

    private int blockNo(int floor) {
        return (floorBlockBase(floor) / 10) + 1;
    }

    // [2026-09-09] "58층이 현재 난이도이고, 51층까지 점차적으로 스탯을 낮추어달라" 요청 --
    // 9/7 S5_BLOCK6_SPECUP.sql로 6블록(51~60층) 일반 몬스터가 소울주춤 유저 스펙 기준
    // "일반 몬스터 승률 50%" 목표로 재조정됐는데, 51층(그 구간 첫 사냥터층)부터 이미 이
    // 난이도라 진입 직후 체감 난이도가 너무 높다는 신고. 그 스탯(HP_VALUE/ATK_VALUE/
    // DEF_VALUE)을 "58층(그 구간 마지막 사냥터층)=100%(현재값 그대로)"로 두고
    // "51층(첫 사냥터층)=50%"에서 시작해 8개 사냥터층에 걸쳐 선형으로 올라가게 한다.
    // 보스(BOSS_YN='Y')는 위치 개념이 없어(구간마다 X9 하나) 대상 아님, 5블록 이하(아직
    // 이 요청 대상이 아닌 층)도 대상 아님 -- 나중에 7블록 이상이 열려도 같은 원리로 자동
    // 적용되도록 blockNo>=6 전체에 일반화해둠(요청은 6블록 한정이었지만 동일 설계 원칙).
    private static final double HARDCORE_FLOOR_SCALE_MIN = 0.5;

    private HashMap<String, Object> applyHardcoreFloorScale(HashMap<String, Object> mon, int floor) {
        if (mon == null) return null;
        // [2026-09-21] 밸런스 V2 오버레이 -- 이름/MONSTER_ID/PP_PER_KILL 등은 V1 조회 결과
        // (mon)를 그대로 두고 HP/ATK/DEF만 V2 선형식 값으로 교체한다. V2가 그 층을 다루면
        // 무조건 우선 적용되므로, 아래 V1 전용(블록≥6, 보스 제외) 스케일 로직보다 먼저 처리.
        // [2026-09-21 재수정] "몬스터스펙은 바뀌기이전 50층까지는 난이도가 괜찮았다, 50층
        // 이후로 선형구조로 해달라" 요청(실측 버그 신고 포함 -- ★2 전사가 7층 몬스터에게
        // 주사위 8이 떠도 2dmg만 들어가는 등, 1~10층 저층완화(dampen)를 넣어도 DEF가 여전히
        // 초반 파티 ATK 대비 압도적으로 높았음) -- V2 자체가 실측 캘리브레이션이 있던 11~89층
        // 기준으로 설계돼 1~50층은 애초에 검증된 적 없는 외삽 구간이었다. 저층 완화 미봉책
        // 대신 **50층 이하는 V2를 아예 적용하지 않고 원래 V1 그대로**(블록1~5는 무보정 고정값,
        // 이미 "괜찮았다"고 확인된 값) 돌아가게 하고, V2는 51층 이상에서만 적용한다.
        int[] v2 = (BALANCE_V2_ENABLED && floor > 50) ? MONSTER_V2.get(floor) : null;
        if (v2 != null) {
            HashMap<String, Object> overlaid = new HashMap<>(mon);
            overlaid.put("HP_VALUE", v2[0]);
            overlaid.put("ATK_VALUE", v2[1]);
            overlaid.put("DEF_VALUE", v2[2]);
            return overlaid;
        }
        if ("Y".equals(strVal(mon.get("BOSS_YN"), "N"))) return mon; // 보스는 스케일 대상 아님
        if (blockNo(floor) < 6) return mon;
        int pos = floor % 10; // 1~8=사냥터층(이 스케일 대상), 0=마을/9=보스는 몬스터 조회 자체를 안 함
        if (pos < 1 || pos > 8) return mon;
        double mult = HARDCORE_FLOOR_SCALE_MIN + (1.0 - HARDCORE_FLOOR_SCALE_MIN) * (pos - 1) / 7.0;
        HashMap<String, Object> scaled = new HashMap<>(mon);
        scaled.put("HP_VALUE", ((Number) mon.get("HP_VALUE")).doubleValue() * mult);
        scaled.put("ATK_VALUE", ((Number) mon.get("ATK_VALUE")).doubleValue() * mult);
        scaled.put("DEF_VALUE", ((Number) mon.get("DEF_VALUE")).doubleValue() * mult);
        return scaled;
    }

    // [2026-09-13] 블록8(71~80층) 오픈 확정, 81층(블록9 사냥터) 이후는 아직 콘텐츠 미공개라
    // 진입 자체를 막는다. 오픈 전 확인: FLOOR_INFO(71~78 TILE_COUNT 196~199, 블록7의
    // 190~210대와 동급 -- 처음엔 "SELECT COUNT(*)"로 잘못 재서 1건씩만 나와 놀랐지만
    // TBOT_S5_FLOOR_INFO는 층당 1행에 TILE_COUNT 컬럼을 갖는 구조라 정상), MONSTER_INFO
    // (108/208, 블록7보다 명확히 강하게 재조정 -- S5_BLOCK8_BALANCE.sql), FLOOR_EXPLORE
    // 업적 8개(71~78) 존재 확인 완료. 79층 보스는 첫타 은신 회피+동료 처치 기믹(1턴째/
    // 6턴마다 반복) 추가. 통계 상한/뽑기 등급(GACHA_MASTER UNLOCK_FLOOR 0/30/60/80, 80이
    // 이미 있어 블록8 신규 티어 없음)은 이미 일반화돼 있어 그대로 통과.
    // (이전엔 61~70만 검증 후 71로 올렸었음.)
    // [2026-09-18] "81층 열어줘 90층까지 가능하도록" 요청 -- 블록9(81~90)만 먼저 오픈,
    // 블록10(91~98/99보스)은 아직 잠금 유지(단계적 오픈). 블록9 밸런스(TILE_COUNT 100,
    // 몬스터 방어력 x3.5, ATK 78층과 동일 비율)는 이미 이번 세션에 사전 조정 완료.
    private static final int CONTENT_LOCKED_FLOOR = 91;

    // [2026-09-06] 51층 이후(블록6+) 전투칸에서 중간보스와 마주칠 확률(%). 밸런스 튜닝값이라
    // 필요하면 조정. 잠긴 콘텐츠라 실사용자 영향 없이 먼저 만들어두고 51층 오픈 시 재검토.
    private static final int MIDBOSS_CHANCE_PCT = 20;

    // [2026-09-19] "선공몬스터 기습이 너무 세다(★6 체력1만인데 기습이 4만)" 신고 -- 처음엔
    // "최대체력 50% 초과분은 파티 전체로 분산+각자 재클램프"로 즉사 자체를 원천 차단했는데,
    // 곧바로 "즉사할 수도 있게 해야지, 다만 지금처럼 100% 확정 즉사는 과하다" 재요청으로
    // 정책을 바꿈: (1) 실드까지 감안한 체감 최대치를 MAX_AMBUSH_DMG로 못박고, (2) 그 값이
    // 대상 최대체력의 AMBUSH_SPLIT_THRESHOLD_PCT(90%)를 넘을 만큼 크면 한 명에게 몰아치지
    // 않고 두 명에게 절반씩 나눠 때린다(다중공격).
    // [2026-09-20 정정] 위 분산 로직만으로는 분산된 몫(예: 2만의 절반=1만)이 그 동료 자신의
    // 최대체력보다 커서 실제로 죽는 사례가 나와("기습으로 사망하지않게 최대체력 90%까지만
    // 데미지가 들어가도록") 방침을 다시 바꿈: 이제 기습은 즉사 가능성을 완전히 없앤다 --
    // applyAmbushHit()에서 최종 적용치를 대상 본인 최대체력의 AMBUSH_DEATH_GUARD_PCT(90%)로
    // 하드 클램프(단일/분산 두 경로 공통 적용). MAX_AMBUSH_DMG/AMBUSH_SPLIT_THRESHOLD_PCT는
    // "몰아치기 방지"(다중공격 연출) 목적으로는 계속 쓰지만, 즉사 방지 보장은 이제 이
    // 클램프가 전담한다.
    private static final int MAX_AMBUSH_DMG = 20000;
    private static final double AMBUSH_SPLIT_THRESHOLD_PCT = 0.9;
    private static final double AMBUSH_DEATH_GUARD_PCT = 0.9;
    // [2026-09-20] "데미지가 낮을 때도 많아야 한다, 평소공격력의 50%로" 요청 -- 기습 굴림의
    // 기준 공격력을 몬스터 평소 공격력(ATK_VALUE 기반 정상 공식)의 이 비율만큼만 쓴다.
    private static final double AMBUSH_ATK_PCT = 0.5;

    // [2026-09-09] "69층 보스는 10턴내 처치 옵션(폭주 타이머)을 추가해달라" 요청 -- 보스가
    // 있는 층(X9) -> 그 보스를 몇 턴 안에 처치해야 하는지. 넘기면 BOSS_ENRAGE_ATK_MULT배로
    // 폭주(공격력만 급상승, HP/DEF는 그대로). 나중에 다른 보스에도 쉽게 추가할 수 있게 맵으로
    // 둠(현재는 69층 하나뿐). 잠긴 콘텐츠(CONTENT_LOCKED_FLOOR=61)라 실사용자 영향 없음.
    private static final HashMap<Integer, Integer> BOSS_ENRAGE_TURN_LIMIT = new HashMap<Integer, Integer>() {{
        put(69, 10);
    }};
    private static final double BOSS_ENRAGE_ATK_MULT = 5.0;

    // [2026-09-10] "S4 낚시 성공시 PP 획득, S5 유저만, 1~8성 등급/현재 탑등반 고려해서
    // 수치 설계, 매일1회 보너스 개념, 9/11부터 적용" 요청. 라이브 데이터 확인(23명 S5 유저,
    // 평균 25.6층/중앙값 23층, 대부분 21명이 S4도 같이 함; 실제 낚시 등급 분포는 ★1이
    // 59%로 압도적, ★6까지는 종종 나옴) 기준으로 설계 -- 그날 낚은 물고기 등급만큼 그
    // 유저의 "현재 층 기준 몬스터 1마리 처치 PP"를 배율로 곱해서 지급(1층 유저는 소액,
    // 58층 유저는 그만큼 큰 액수 -- 항상 그 유저 경제 규모에 비례). grantFishingBonus() 참고.
    private static final String FISHING_PP_START_DATE = "2026-09-11";
    private static final double[] FISH_GRADE_PP_MULT = { 0, 1, 1.5, 2, 3, 4, 6, 8, 12 }; // index=fishGrade(1~8)

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
     * [2026-09-16] "50층 이후 50%탐사보상으로 악세뽑기권 지급, 50대=하급/60대=중급/70대=상급/
     * 80대=최상급" 요청 -- 90대는 악세뽑기권 등급이 4단계(하급~최상급)뿐이라 상한(최상급)으로
     * 고정. 블록6(50대)~10(90대) 기준 tier=block-5, 4 초과분은 4로 클램프.
     */
    private int accessoryVoucherTierForFloor(int floor) {
        int tier = blockNo(floor) - 5;
        if (tier < 1) tier = 1;
        if (tier > 4) tier = 4;
        return tier;
    }

    /**
     * [2026-09-16 신설] 50층 이상 층에서 이번 방문 중 처음으로 탐사율 50%를 넘긴 순간 악세뽑기권
     * 1장을 지급한다. ACH_ID 500+floor(550~599, 기존 achId 대역과 안 겹침)로 층당 1회만
     * 지급되게 멱등 처리(grantAchievement).
     * [2026-09-17] "계단 해금 조건을 0/10/20/25%로 다양화(50% 없앰)" 요청 이후로는 이 50% 고정
     * 기준이 더 이상 "이 층 계단 해금 시점"과 같지 않다(stairsUpRequiredPct 참고) -- 이 보상은
     * 계단과 무관하게 독립적으로 "탐사율 50% 달성"이라는 별개의 이정표를 기준으로 계속 동작한다.
     * @return 지급 안내 문구, 조건 미충족/이미 지급됨이면 null.
     */
    private String checkExploreHalfReward(String userName, HashMap<String, Object> p, int floor, int visited, int tileCount) {
        if (floor < 50 || tileCount <= 0 || visited * 100 / tileCount < 50) return null;
        if (!grantAchievement(userName, 500 + floor)) return null;
        int tier = accessoryVoucherTierForFloor(floor);
        String field = "accessoryVoucherT" + tier;
        String column = "ACCESSORY_VOUCHER_T" + tier;
        int newCnt = intVal(p.get(column), 0) + 1;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, newCnt);
        dao.updateUserProgress(up);
        p.put(column, newCnt);
        return "🎁 [" + floor + "층 탐사율 50%] " + COMPANION_TIER_NAME[tier - 1] + " 악세뽑기권 1장 지급! (/악세뽑기로 사용)";
    }

    /**
     * 이 (유저,층) 보드가 아직 없으면(마을 갔다온 뒤 첫 진입 등) 새로 만든다. 칸 개수는
     * TBOT_S5_FLOOR_INFO.TILE_COUNT(층별 고정, 기존과 동일)를 그대로 쓰고 칸 "종류"만 매번
     * 새로 무작위 배정한다. 고정 개수 칸을 먼저 넣고(계단 위/아래 각 1개씩 총 2개, 히든 1~2,
     * 보물상자1, 20층대+엔 강화몹1) 나머지를 전투65%/함정7%/럭키28%로 채운 뒤 위치를 섞는다.
     * [2026-09-14] "함정&럭키 비율을 조금 더 줄이고 전투가 많도록 해달라" 요청으로
     * 기존 50%/10%/40%에서 조정(전투 위주로).
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
        // 계단을 위/아래 방향으로 분리(요청) -- 기본은 층마다 딱 2칸(각 방향 1개씩) 고정.
        // [2026-09-16] "50층 이상은 올라가는 계단 4개, 내려가는 계단 4개로" 요청으로 50층+는
        // 방향당 4개씩(총 8칸)으로 늘림 -- 아래 STAIRS_UP 해금 조건이 "특정 계단칸 하나"가 아니라
        // 층 전체 탐사율 기준으로 판정된다. [2026-09-17] 처음엔 4칸 전부 동일 기준(50%)이었지만,
        // "0/10/20/25%로 각각 다르게" 요청으로 지금은 칸마다 요구치가 다르다(stairsUpRequiredPct
        // -- 4칸보다 적어도 앞쪽 값부터 순서대로 쓰므로 아래 81층+ 2칸 케이스도 자동으로 0%/10%가
        // 적용되어 별도 처리 불필요). [2026-09-18] "81층부터는 계단을 위/아래 각 2개로" 요청으로
        // 81층+는 방향당 2개로 축소(칸 수 자체도 81층+는 100칸으로 줄어들어서 -- 200칸대
        // 보드에 8+8개는 상대적으로 계단 비중이 낮았지만 100칸이면 과해지는 것도 함께 고려).
        // 같은 날 후속 요청으로 블록9(81~90)는 실제로 오픈됨(CONTENT_LOCKED_FLOOR=91),
        // 블록10(91층+)은 계속 비공개.
        int stairsPerDirection = floor >= 81 ? 2 : (floor >= 50 ? 4 : 1);
        for (int i = 0; i < stairsPerDirection; i++) {
            types.add("STAIRS_UP");
            types.add("STAIRS_DOWN");
        }
        // "51층부터 특수칸(舊 워프포인트, 현 무너진 사원)" 요청 -- 51층부턴 넉넉하게 배치
        // (2026-09-08엔 4개, 2026-09-09에 6개로 증량). [2026-09-09 후속] "50층 이하는 특수칸
        // 자체가 없어도 된다" 요청으로 50층 이하는 특수칸을 아예 안 놓는다(0개) --
        // handleSpecialTile()도 floor>=51 전용 효과들이라 애초에 특수칸이 안 나오면 그 코드에
        // 도달할 일도 없다. [2026-09-18] 탐사율 체크포인트는 더 이상 이 칸에 안 묶이고
        // rollDiceInternal에서 15%p 단위로 자동 저장된다(markSpecialTileCheckpoint 참고).
        int specialCount = floor >= 51 ? 6 : 0;
        for (int i = 0; i < specialCount; i++) types.add("SPECIAL");
        types.add("TREASURE");
        if (blockNo(floor) >= 3) types.add("ELITE"); // 20층대(블록3)부터만 강화몹방 등장
        while (types.size() < tileCount) {
            int r = RND.nextInt(100);
            if (r < 65) types.add("COMBAT");
            else if (r < 72) types.add("TRAP");
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

        // "51층부터 마을 가도 탐사율이 초기화 안 되게(체크포인트 개념)" 요청 -- 51층 이상은
        // 탐사율이 15%p 단위 구간을 새로 넘을 때마다 자동으로 체크포인트를 저장해두고
        // (rollDiceInternal, markSpecialTileCheckpoint 참고 -- [2026-09-18] 예전엔 특수칸
        // "워프포인트"를 직접 밟아야만 저장됐는데, "워프포인트를 없애고 15%마다 자동저장"
        // 요청으로 자동화함), 마을 복귀 등으로 보드가 새로 생성될 때 그 체크포인트만큼을
        // "이미 발견한 칸"으로 미리 채워 넣어서 탐사율이 체크포인트 지점까지는 유지되게
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

    // [2026-09-17] "50층+ 올라가는 계단 4개가 전부 50%였는데 0/10/20/25%로 각각 다르게,
    // 50%는 없도록" 요청 -- 값 자체를 배열 하나로 관리(재배포 없이 바꾸려면 나중에 config화
    // 가능하나, 당장은 4개 고정이라 상수로 충분).
    private static final int[] STAIRS_UP_REQUIRE_PCT = { 0, 10, 20, 25 };

    /** 이 (유저,층) 보드에서 tileNo가 몇 번째 STAIRS_UP 칸인지(TILE_NO 오름차순)를 찾아 그
     *  순서에 배정된 요구 탐사율(%)을 반환한다. 같은 층 안에서도 어느 계단을 밟았느냐에 따라
     *  요구치가 다르다(0/10/20/25%). 보드가 규격과 다르게 생성돼 STAIRS_UP이 4개보다 많으면
     *  넘치는 칸은 마지막 값(25%)으로 방어. */
    private int stairsUpRequiredPct(String userName, int floor, int tileNo) {
        List<HashMap<String, Object>> board = dao.selectUserTileMaster(userName, floor);
        int rank = 0;
        for (HashMap<String, Object> t : board) {
            if (!"STAIRS_UP".equals(strVal(t.get("TILE_TYPE"), ""))) continue;
            if (intVal(t.get("TILE_NO"), -1) == tileNo) break;
            rank++;
        }
        int idx = Math.min(rank, STAIRS_UP_REQUIRE_PCT.length - 1);
        return STAIRS_UP_REQUIRE_PCT[idx];
    }

    /**
     * [2026-09-18] "워프포인트를 없애고 15%마다 탐사율 자동저장" 요청 -- 51층 이상에서 탐사율이
     * 새 15%p 구간을 넘을 때마다(rollDiceInternal에서 호출) 그 시점 탐사 칸수를 체크포인트로
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
        // [2026-09-15] "최고 동료/장비 등급을 갯수까지 포함해서" 요청 -- 처음엔 서버 전체
        // 합산치(여러 유저가 나눠 가진 걸 다 더한 값)로 잘못 구현했었는데("13명이라고
        // 나오는데 실제로 한 사람이 최고로 가진 명수를 적어달라" 신고로 확인), 최고 등급
        // 컴패니언/장비를 "한 명이 최고 몇 개 보유했는지"(개인별 집계의 MAX)로 정정.
        // "누가"는 여전히 비공개.
        int maxCompanionGradeCount = dao.selectMaxCompanionGradeCount();
        int maxEquip = dao.selectMaxEquipCount();
        int maxEquipGrade = dao.selectMaxEquipGrade();
        int maxEquipGradeCount = dao.selectMaxEquipGradeCount();
        // [2026-09-15] 악세서리(목걸이/반지/팔찌) 신설로 "장비"와 분리된 전용 랭킹 통계.
        int maxAccessory = dao.selectMaxAccessoryCount();
        int maxAccessoryGrade = dao.selectMaxAccessoryGrade();
        int maxAccessoryGradeCount = dao.selectMaxAccessoryGradeCount();

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
        int mineAccessory = 0, mineAccessoryGrade = 0;
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
            mineAccessory = dao.countUserAccessory(userName);
            for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
                int eg = intVal(e.get("GRADE"), 0);
                String part = strVal(e.get("PART"), "");
                if ("NECKLACE".equals(part) || "RING".equals(part) || "BRACELET".equals(part)) {
                    mineAccessoryGrade = Math.max(mineAccessoryGrade, eg);
                } else {
                    mineEquipGrade = Math.max(mineEquipGrade, eg);
                }
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
        // [2026-09-15 표현 수정] "13마리 -> 13명" + "그 갯수는 서버 합산이 아니라 한 사람이
        // 최고로 보유한 개수여야 한다" 요청 -- 괄호 안 숫자가 "합산치"가 아니라 "최다 보유자
        // 1명 기준"임을 문구로도 명확히("최다 보유자 N명/개").
        sb.append("✨ 최고 동료 등급: ★").append(maxCompanionGrade).append(" (최다 보유자 ").append(maxCompanionGradeCount).append("명)")
          .append(meTag(mineCompanionGrade, maxCompanionGrade)).append(NL);
        sb.append("🎽 최다 장비 보유: ").append(maxEquip).append("개").append(meTag(mineEquip, maxEquip)).append(NL);
        sb.append("💎 최고 장비 등급: ★").append(maxEquipGrade).append(" (최다 보유자 ").append(maxEquipGradeCount).append("개)")
          .append(meTag(mineEquipGrade, maxEquipGrade)).append(NL);
        sb.append("💍 최다 악세서리 보유: ").append(maxAccessory).append("개").append(meTag(mineAccessory, maxAccessory)).append(NL);
        sb.append("🔮 최고 악세서리 등급: ★").append(maxAccessoryGrade).append(" (최다 보유자 ").append(maxAccessoryGradeCount).append("개)")
          .append(meTag(mineAccessoryGrade, maxAccessoryGrade)).append(NL);
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

    /** [2026-09-17] "자동사냥 60층 아래구간에서 5배수정도 늘려줘, 70층아래는 3배수로 해줘"
     * 요청 -- 저층 자동사냥(미접속 정산) 보상만 구간별로 추가 배율을 얹는다. 수동 전투(처치
     * 보상/스틸/보물상자 등)는 전부 floorPpMultiplier만 그대로 쓰고 이 배율과 무관 -- settleAutoHunt()
     * 와 /탑현황의 "시간당 예상 PP" 미리보기(둘 다 이 클래스 안), 그리고 웹뷰 tower-status API의
     * 예상 PP 미리보기(Season5ViewController.buildAutoHuntInfo, 이 메서드와 동일 공식을 유지해야
     * 함)에서만 곱해진다. 60층 미만은 5배, 60~69층은 3배, 70층 이상은 보정 없음(1배). */
    private double autoHuntFloorBonusMultiplier(int floor) {
        if (floor < 60) return 5.0;
        if (floor < 70) return 3.0;
        return 1.0;
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

    // [2026-09-10] 69층 보스 "하수인" 명단(CUR_BOSS_MINION_CIDS) 콤마구분 문자열 <-> 목록 변환.
    private List<Integer> parseMinionCids(String csv) {
        List<Integer> list = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return list;
        for (String s : csv.split(",")) {
            try { list.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignore) { }
        }
        return list;
    }

    private String joinMinionCids(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
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
        PP pp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));

        // [2026-09-17 전면 개편] "탑정보 스타일을 바꾸고싶어" 요청 -- 실시간 진행상황 위주
        // (상태/사용주사위/보드위치/자동사냥 상세/이번층 처치진행)에서 누적 프로필 요약
        // 위주(업적/완전탐사/보유 동료·장비·악세서리 개수)로 개편. 자동사냥 상세 줄은 이
        // 개편 취지(실시간 상태 정보 축소)에 맞춰 요청 예시에서 빠져있어 함께 제거했다 --
        // 필요하면 언제든 되돌릴 수 있음. 묶음 사이는 빈 줄로 구분(위치/PP+동료/통계 3묶음).
        StringBuilder sb = new StringBuilder();
        sb.append(target).append(isOther ? "님의 탑 현황" : "님").append("," + NL);

        // ── 위치 ──
        int fpos = floor % 10;
        boolean isHuntFloor = fpos >= 1 && fpos <= 8;
        sb.append("🗼 ").append(floor).append("층");
        if (isHuntFloor) sb.append(" (권장전투력 ").append(floorMonsterCombatPower(floor)).append(")");
        else sb.append(" (").append(floorKindLabel(floor)).append(")");
        sb.append(NL);
        if (isHuntFloor) {
            HashMap<String, Object> fi = dao.selectFloorInfo(floor);
            int tileCount = fi == null ? 0 : intVal(fi.get("TILE_COUNT"), 0);
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
            sb.append("🗺️ 탐사율 현재 ").append(curPct).append("% / 최고 ").append(bestPct).append("%")
              .append(fullyExplored ? " ✅완전탐사" : "").append(NL);
        }
        sb.append(NL);

        // ── PP + 최고 동료 ──
        PP totalEarned = PP.of(numVal(p.get("TOTAL_PP_EARNED_VALUE"), 0), strVal(p.get("TOTAL_PP_EARNED_EXT"), ""));
        sb.append("💰 보유 ").append(pp.format()).append(" PP · 누적획득 ").append(totalEarned.format()).append(" PP").append(NL);
        // [2026-09-10] "최고티어 동료 3명의 등급/직업도 표기해달라" 요청 -- 파티 편성 여부와
        // 무관하게 "보유한 동료 중" 등급이 가장 높은 3명을 뽑아서 "★등급직업" 형태로 보여준다.
        // [2026-09-17] "이름은 빼고 성급/직업만, 쉼표로만 구분해줘" 요청으로 이름 표기 제거.
        List<HashMap<String, Object>> allCompanions = dao.selectUserCompanions(target);
        if (!allCompanions.isEmpty()) {
            List<HashMap<String, Object>> topThree = new ArrayList<>(allCompanions);
            topThree.sort((a, b) -> intVal(b.get("GRADE"), 1) - intVal(a.get("GRADE"), 1));
            if (topThree.size() > 3) topThree = topThree.subList(0, 3);
            List<String> tags = new ArrayList<>();
            for (HashMap<String, Object> c : topThree) {
                tags.add("★" + intVal(c.get("GRADE"), 1) + JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "동료"));
            }
            sb.append("🏆 최고 동료: ").append(String.join(",", tags)).append(NL);
        }
        sb.append(NL);

        // ── 전투력 + 누적 통계 ──
        // [2026-09-15 신설] "전투력을 수치화 시켜고, 그 수치를 보여주고, 층별 전투력을
        // 나타내 주고, 현재 몇층이 괜찮은 사냥터인지 추천해달라" 요청.
        long myPower = partyCombatPower(target);
        if (myPower <= 0) {
            sb.append("⚡ 종합전투력: 0 (파티에 동료를 편성하면 계산됩니다)").append(NL);
        } else {
            sb.append("⚡ 종합전투력: ").append(myPower);
            int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
            if (maxReached < 1) {
                sb.append("(아직 사냥터 미진입)");
            } else {
                // [2026-09-16] "이미 밟아본 층 말고 진짜 전투력 기준으로 추천, 괄호 설명은
                // 빼달라" 요청 -- 상한을 MAX_FLOOR_REACHED 대신 UNLOCKED_BLOCK(지금 당장
                // /탑올라가기+층변경으로 이동 가능한 전체 범위)로 바꿔서 계산(recommendHuntFloor
                // 주석 참고).
                int unlockedBlock = intVal(p.get("UNLOCKED_BLOCK"), 0);
                int recommended = recommendHuntFloor(myPower, unlockedBlock);
                if (recommended > 0) sb.append("(🎯추천: ").append(recommended).append("층)");
                else sb.append("(⚠️ 1층 사냥도 버거울 수 있어요)");
            }
            sb.append(NL);
        }
        sb.append("📊 누적 처치: ").append(intVal(p.get("TOTAL_KILL_COUNT"), 0)).append("마리").append(NL);
        sb.append("🏅 업적: ").append(dao.selectUserAchievements(target).size()).append("개").append(NL);
        sb.append("🗺️ 완전탐사: ").append(dao.countFullyExploredFloors(target)).append("개 층").append(NL);
        sb.append("👥 동료 보유: ").append(allCompanions.size()).append("명").append(NL);
        // 장비/악세서리 보유 개수 -- 부위(PART)로 구분(무기/투구/갑옷=장비, 목걸이/반지/팔찌=악세서리).
        int equipCount = 0, accessoryCount = 0;
        for (HashMap<String, Object> e : dao.selectUserEquip(target)) {
            String part = strVal(e.get("PART"), "");
            if ("NECKLACE".equals(part) || "RING".equals(part) || "BRACELET".equals(part)) accessoryCount++;
            else equipCount++;
        }
        sb.append("🎽 장비 보유: ").append(equipCount).append("개").append(NL);
        sb.append("💍 악세서리 보유: ").append(accessoryCount).append("개").append(NL);

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
            case "PRIEST":  return "✨ 도사 3인조 시너지! 보호막량 2배 + 보호막이 흡수한 피해의 절반을 반사" + NL;
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
        sb.append("/악세뽑기N [10]").append(NL);
        sb.append("/주사위구매 [N]").append(NL);
        sb.append("/주사위강화 [구매]").append(NL);
        sb.append("/마이너스주사위 [구매]").append(NL);
        sb.append("/스탯구매 [공격력|최소공격력|체력]").append(NL);
        sb.append("/장비목록").append(NL);
        sb.append("/장비장착 [N] [M]").append(NL);
        sb.append("/장비합성 N").append(NL);
        sb.append("/장비일괄합성").append(NL);
        sb.append("/장비해제 M").append(NL);
        sb.append("/탑업적").append(NL);
        sb.append("/탑랭킹").append(NL);
        sb.append(NL);

        sb.append("[간략 설명]").append(NL);
        sb.append("🗼 이동/전투").append(NL);
        sb.append("/주사위 : 이동 또는 공격").append(NL);
        sb.append("/층변경 N : 구간 내 N번째 층 이동(0=마을~9=보스)").append(NL);
        sb.append("/층내려가기 : 구간 내 한 층 아래로").append(NL);
        sb.append("/탑내려가기 · /탑올라가기 : 마을끼리 위/아래 구간 이동(올라가기는 보스 처치 후만)").append(NL);
        sb.append("/탑현황 [닉네임] : 현황 조회").append(NL);
        sb.append(NL);

        sb.append("👥 동료").append(NL);
        sb.append("/파티편성 [N] : 목록 조회 / N번 편성·해제").append(NL);
        sb.append("/동료가리기 N : 목록에서 숨김 토글").append(NL);
        sb.append(NL);

        sb.append("🛍️ 상점").append(NL);
        sb.append("/동료뽑기N [10] : N번 계약서로 뽑기(10연속 가능)").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("COMPANION", 999), unlocked));
        sb.append("/장비뽑기N [10] : N번 상자로 뽑기(10연속 가능)").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("EQUIP", 999), unlocked));
        sb.append("/악세뽑기N [10] : N번 상자로 목걸이/반지/팔찌 뽑기(10연속 가능)").append(NL);
        sb.append(gachaCatalogText(dao.selectGachaList("ACCESSORY", 999), unlocked));
        sb.append("/주사위구매 [N] : 주사위 등급 확인/교체").append(NL);
        sb.append("/주사위강화 [구매] · /마이너스주사위 [구매] : 최소 눈금 조정 확인/구매(둘 중 하나만 적용, 기본 +0)").append(NL);
        sb.append("/스탯구매 [공격력|최소공격력|체력] : 스탯 강화 확인/구매").append(NL);
        sb.append(NL);

        sb.append("🎒 장비").append(NL);
        sb.append("/장비목록 : 보유 장비 조회").append(NL);
        sb.append("/장비장착 [N] [M] : 장착").append(NL);
        sb.append("/장비합성 N : 상위 등급으로 합성").append(NL);
        sb.append("/장비일괄합성 : 합성 가능한 조합을 전부 한 번에 합성").append(NL);
        sb.append("/장비해제 M : 파티원 장비 전부 해제").append(NL);
        sb.append(NL);

        sb.append("🏆 업적/랭킹").append(NL);
        sb.append("/탑업적 [닉네임] : 달성 업적 조회").append(NL);
        sb.append("/탑랭킹 : 서버 최고기록 조회").append(NL);
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
        // [2026-09-15] 악세서리(목걸이/반지/팔찌) 뽑기 신설 -- /악세뽑기.
        String cmd = "COMPANION".equals(gachaType) ? "/동료뽑기" : "ACCESSORY".equals(gachaType) ? "/악세뽑기" : "/장비뽑기";
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
    // [2026-09-11 오탐 재수정] "키리레이나" 계정이 다시 정지된 사고로 재조사 -- 실제로는
    // 진짜 활발히 플레이 중인 유저(같은 시간대에 뽑기/합성/장비장착 등 정상 조작 다수)였는데,
    // "쿨타임 안내를 받고도 조급하게 몇 초 간격으로 계속 재시도"한 게 원인으로 확인됨(쿨타임이
    // 3초로 짧은 구간이라 특히 재현이 쉬움). checkMacroLock()이 쿨타임 통과 여부와 "무관하게"
    // 모든 요청 간격을 스트릭에 반영하다 보니, 쿨타임 벽에 막힌 조급한 재시도들까지 전부
    // 표본으로 잡혀서 실제 액션 간격보다 훨씬 촘촘하고 일정한 패턴이 만들어졌다 -- 정작
    // 진짜 매크로는 쿨타임 벽에 헛되이 부딪히지 않고 정확히 쿨타임만큼 기다렸다 쏘는 게
    // 보통이라, 이 신호 자체가 사람/매크로 구분에 오히려 역효과였음. 대응 두 가지:
    //   1) 패턴 추적을 rollDice()의 쿨타임 통과 "이후"로 옮김(checkMacroPattern 참고) --
    //      쿨타임에 막혀 거절된 요청은 더 이상 스트릭에 전혀 반영되지 않는다.
    //   2) 최소 간격 하한을 2->5초로, 스트릭 기준치를 25->40으로 각각 올려 안전 마진 확대
    //      (짧은 쿨타임 직후 즉시 재시도하는 정상적으로 빠른 유저도 자연스럽게 걸러지게).
    private static final long MACRO_MIN_INTERVAL_SEC = 5;
    // 이 이상 촘촘하지 않으면 애초에 "빠르게 연타"일 뿐 자동화로 보기 어려움
    private static final long MACRO_MAX_INTERVAL_SEC = 20;
    // 이전 간격과 이만큼(초) 이내로 차이나면 "같은 타이머"로 본다
    private static final long MACRO_TOLERANCE_SEC = 1;
    // 이 횟수 연속으로 "같은 타이머"가 감지되면 일시정지
    private static final int MACRO_STREAK_THRESHOLD = 40;
    // [2026-09-09] "일시정지->영구정지 텀이 너무 짧아서 일반유저가 억울하게 영구정지되는
    // 케이스가 많다, 지금 대비 3배 정도 늘려달라" 요청 -- 원래는 일시정지 상태에서 딱 1번만
    // 더 시도해도(유예 0회) 곧바로 영구정지였다. 유예를 3회로 늘려서, 일시정지 후에도 2번은
    // "아직 일시정지 상태" 안내만 받고, 3번째에 가서야 영구정지로 격상되도록 완화.
    private static final int SUSPEND_BAN_GRACE = 3;
    // [2026-09-09] "영구정지도 1시간 지나면 자동으로 풀리면 좋겠다" 요청 -- 이름은 "영구정지"로
    // 그대로 두되(관리자 안내 문구 등 기존 의미 유지), 실제로는 BAN_DATE 기준 1시간짜리 강한
    // 락으로 완화. 자동 해제 시 일시정지/스트릭/유예 카운트도 함께 초기화된다.
    private static final long BAN_AUTO_UNLOCK_HOURS = 1;

    /**
     * 매크로(자동화 클라이언트) 밴/정지 상태 확인 -- 쿨타임 통과 여부와 무관하게(쿨타임에
     * 막힌 요청이라도) 항상 먼저 확인해야 하는 부분만 담당: 이미 영구정지/일시정지된 계정을
     * 계속 차단하고, 일시정지 후에도 SUSPEND_BAN_GRACE회 넘게 계속 시도하면 영구정지로
     * 격상한다. 영구정지는 BAN_DATE 기준 BAN_AUTO_UNLOCK_HOURS시간이 지나면 자동으로 풀린다.
     * 실제로 "새로 정지시킬지" 판단하는 간격 패턴 추적은 checkMacroPattern() 참고(쿨타임을
     * 통과한 요청에만 적용, 아래 rollDice() 호출 순서 참고). 막혀있지 않으면(혹은 방금 자동
     * 해제됐으면) null 반환.
     */
    private String checkMacroBanState(String userName, HashMap<String, Object> p) {
        if ("Y".equals(strVal(p.get("BAN_YN"), "N"))) {
            java.util.Date banDate = (java.util.Date) p.get("BAN_DATE");
            if (banDate != null && (System.currentTimeMillis() - banDate.getTime()) >= BAN_AUTO_UNLOCK_HOURS * 3600_000L) {
                // 1시간 경과 -- 영구정지/일시정지/스트릭/유예 카운트 전부 초기화하고 자동 해제,
                // 이번 요청은 정상 통과시킨다(막 해제된 참이라 계속 막을 이유가 없음).
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                up.put("banYn", "N");
                up.put("suspendYn", "N");
                up.put("macroStreak", 0);
                up.put("suspendRetryCount", 0);
                up.put("clearBanDate", true);
                dao.updateUserProgress(up);
                p.put("BAN_YN", "N");
                p.put("SUSPEND_YN", "N");
                return null;
            }
            return "🚫 매크로(자동화) 사용이 확인되어 영구정지된 계정입니다. (정지 후 " + BAN_AUTO_UNLOCK_HOURS + "시간 뒤 자동 해제됩니다)";
        }

        if ("Y".equals(strVal(p.get("SUSPEND_YN"), "N"))) {
            // 이미 매크로 의심으로 일시정지된 계정이 그래도 계속 시도 -- SUSPEND_BAN_GRACE회를
            // 넘기면 영구정지로 격상, 그 전까지는 안내만 반복한다.
            int retry = intVal(p.get("SUSPEND_RETRY_COUNT"), 0) + 1;
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("touchLastRequestDate", true);
            up.put("suspendRetryCount", retry);
            if (retry >= SUSPEND_BAN_GRACE) {
                up.put("banYn", "Y");
                up.put("touchBanDate", true);
                dao.updateUserProgress(up);
                return "🚫 일시정지 상태에서 계속 시도하여 영구정지 처리되었습니다. (관리자 문의, " + BAN_AUTO_UNLOCK_HOURS + "시간 뒤 자동 해제)";
            }
            dao.updateUserProgress(up);
            return "🚫 매크로(자동화) 의심으로 일시정지된 계정입니다. (관리자 문의 필요, 계속 시도하면 영구정지될 수 있습니다)";
        }
        return null;
    }

    /**
     * 매크로(자동화 클라이언트) 의심 패턴 추적 -- "요청이 들어온 간격" 자체가 여러 번 연속으로
     * 거의 똑같으면(사람은 이렇게 못 침) 일시정지시킨다.
     * [2026-09-11] 예전엔 이 판정을 쿨타임 통과 여부와 "무관하게" 모든 요청에 적용했는데,
     * "키리레이나" 계정이 실제로는 정상적으로 활발히 플레이하면서 쿨타임 안내를 받고도
     * 조급하게 몇 초 간격으로 재시도한 게 그대로 스트릭에 잡혀 오탐 정지된 사고로 확인됨
     * (쿨타임이 3초로 짧은 구간이라 특히 쉽게 재현됨). 진짜 매크로는 오히려 쿨타임 벽에
     * 헛되이 부딪히지 않고 정확히 쿨타임만큼 기다렸다 쏘는 게 보통이라, 쿨타임에 막힌
     * 요청까지 반영하는 게 사람/매크로 구분에 역효과였다 -- 이제 이 함수는 rollDice()가
     * 쿨타임을 통과시킨 뒤(=실제로 처리될 요청)에만 호출된다.
     */
    private String checkMacroPattern(String userName, HashMap<String, Object> p) {
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
        // (사람이 낼 수 없는 규칙성). 이미 정지/영구정지된 상태인지는 쿨타임 통과 여부와
        // 무관하게 항상 먼저 확인한다(정지된 계정은 쿨타임과 무관하게 계속 차단).
        String macroBanMsg = checkMacroBanState(userName, p);
        if (macroBanMsg != null) return macroBanMsg;

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

        // [2026-09-11] 새로 정지시킬지 판단하는 간격 패턴 추적은 쿨타임을 통과한(=실제로
        // 처리되는) 요청에만 적용한다 -- checkMacroPattern() 주석 참고("키리레이나" 오탐 재발
        // 방지, 쿨타임 벽에 막힌 조급한 재시도가 스트릭에 잡히던 문제 제거).
        String macroPatternMsg = checkMacroPattern(userName, p);
        if (macroPatternMsg != null) return prependAutoHunt(autoHuntMsg, macroPatternMsg);

        // 하루 이동 횟수 제한("하루 N번까지만" 요청, 2026-09-07에 채널별 한도로 확장,
        // 2026-09-21에 "주사위 전체"에서 "보드 이동"만으로 대상 축소) -- 쿨타임 통과 후,
        // 실제로 이번 액션이 "이동 1회"로 카운트되기 직전에 확인한다(쿨타임에 막힌 시도는
        // 카운트 안 함). 전투 중(IN_COMBAT)인 굴림은 애초에 이동이 아니라 공격 턴이므로 이
        // 체크 자체를 건너뛴다 -- 전투는 하루 한도와 무관하게 계속 진행할 수 있음.
        // [2026-09-21] "오늘이동주사위 계산이 안되는거같아" 확인 -- 관리자 테스트 계정
        // (NO_COOLDOWN_YN)은 카운터 자체를 건드리지 않고 건너뛰어서, 그 계정으로는 웹 UI의
        // 이동한도 표시가 항상 0/400으로 보여 "계산이 안 된다"로 오인되기 쉬웠다. 차단(한도
        // 초과 메시지)만 면제하고 카운트 자체는 그대로 올리도록 checkAndBumpDailyDiceLimit
        // 내부에서 분리(관찰/테스트 가능하게, 쿨타임과 달리 이동횟수 카운팅 자체를 숨길
        // 이유는 없음).
        if (!"IN_COMBAT".equals(status)) {
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
     * 하루 "이동" 횟수(DICE_ROLL_COUNT_TODAY 컬럼 재사용, 채널 무관 공유 카운터)를 확인하고,
     * 한도 안이면 카운트를 올린 뒤 null을 반환한다(통과). DICE_ROLL_DATE가 오늘이 아니면(=날짜가
     * 바뀌었거나 최초 이동) 카운트를 1로 리셋 -- 별도 배치/스케줄러 없이 "확인하는 시점에
     * 날짜만 비교"하는 방식이라 자정에 뭔가 돌려줄 필요가 없다. 한도를 넘으면 카운트는 그대로
     * 두고 안내 메시지만 반환.
     * [2026-09-21] "주사위 굴림수(1200=1000+200) 기준이던 하루 한도를, 전투 턴은 빼고 보드
     * 이동만 400(+카톡보너스 100=500)회로 바꿔달라" 요청 -- 이 함수 자체는 채널별 한도
     * 분리(WEB은 DAILY_MOVE_LIMIT까지, CHAT은 거기에 KAKAO_BONUS_MOVE를 더한 값까지) 로직은
     * 그대로 두고, **호출 여부**만 바뀌었다: 이제 rollDice()가 STATUS!='IN_COMBAT'일 때만
     * 이 함수를 부른다(전투 턴은 호출 자체가 없어 무제한) -- 즉 이 함수에 들어온 시점에서
     * "이동"이라는 게 이미 확정된 상태라 함수 내부 로직은 카운팅 대상 이름만 이동으로 바뀐 것.
     * 카운터 자체는 채널 구분 없이 하나 그대로 써서, 어느 채널로 얼마씩 섞어 쓰든 "총합이
     * 웹 한도를 넘으면 웹만 차단, 카톡 한도까지 넘으면 전부 차단"이 자연스럽게 성립한다.
     * (SimpleDateFormat은 스레드 안전하지 않아 static 캐시로 못 쓰므로 java.time으로 비교한다.)
     */
    // [2026-09-21] "이동한도도 맵이동하는곳에 표기하면 좋을거같아" 요청 -- 웹 UI(보드 카드
    // 근처)에 오늘 이동 사용량/한도를 보여주기 위한 조회 전용 접근자. 실제 차감/한도체크는
    // checkAndBumpDailyDiceLimit()가 그대로 담당, 이 메서드는 그 안의 "오늘 사용량" 계산
    // 로직만 재사용해서 읽기만 한다.
    @Override
    public HashMap<String, Object> moveLimitInfo(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        java.util.Date rollDate = (java.util.Date) p.get("DICE_ROLL_DATE");
        int rawUsedToday = intVal(p.get("DICE_ROLL_COUNT_TODAY"), 0);
        boolean sameDay = rollDate != null
                && new java.sql.Date(rollDate.getTime()).toLocalDate().equals(java.time.LocalDate.now());
        int used = sameDay ? rawUsedToday : 0;
        HashMap<String, Object> info = new HashMap<>();
        info.put("used", used);
        info.put("webLimit", DAILY_MOVE_LIMIT);
        info.put("totalLimit", DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE);
        return info;
    }

    private String checkAndBumpDailyDiceLimit(String userName, HashMap<String, Object> p, String channel) {
        java.util.Date rollDate = (java.util.Date) p.get("DICE_ROLL_DATE");
        int rawUsedToday = intVal(p.get("DICE_ROLL_COUNT_TODAY"), 0);
        boolean sameDay = rollDate != null
                && new java.sql.Date(rollDate.getTime()).toLocalDate().equals(java.time.LocalDate.now());
        int storedRaw = sameDay ? rawUsedToday : 0;
        int curCount = storedRaw;
        boolean isWeb = "WEB".equals(channel);
        int channelLimit = isWeb ? DAILY_MOVE_LIMIT : (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE);
        // [2026-09-21] 관리자 테스트 계정(NO_COOLDOWN_YN)은 차단(한도 초과 메시지)만 면제 --
        // 카운트 자체는 일반 유저와 동일하게 계속 올라가야 웹 UI의 이동한도 표시(moveLimitInfo)가
        // 테스트 중에도 정상 동작하는지 관찰할 수 있다(전에는 이 함수 호출 자체를 건너뛰어서
        // 관리자 계정은 항상 0/400으로 보였음 -- "계산이 안 된다"로 오인된 원인).
        boolean noCooldown = "Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"));
        if (!noCooldown && curCount >= channelLimit) {
            if (isWeb) {
                // 웹은 막혔지만 카톡 쪽 보너스가 아직 안 찼으면 그쪽으로 안내.
                if (curCount < DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) {
                    return "🚶 오늘 웹에서 이동을 " + DAILY_MOVE_LIMIT + "번 모두 했습니다. "
                            + "카카오톡에서는 " + (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE - curCount) + "번 더 진행할 수 있어요! (전투 중엔 이 제한과 무관하게 계속 싸울 수 있습니다)";
                }
                return "🚶 오늘 이동을 " + (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) + "번 모두 했습니다. "
                        + "내일 다시 시도해주세요. (전투 중엔 이 제한과 무관하게 계속 싸울 수 있습니다)";
            }
            return "🚶 오늘 카카오톡 한도(" + (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) + "번)까지 모두 이동했습니다. "
                    + "내일 다시 시도해주세요. (전투 중엔 이 제한과 무관하게 계속 싸울 수 있습니다)";
        }
        int newRaw = storedRaw + 1;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceRollCountToday", newRaw);
        up.put("touchDiceRollDate", true);
        dao.updateUserProgress(up);
        p.put("DICE_ROLL_COUNT_TODAY", newRaw);
        p.put("DICE_ROLL_DATE", new java.util.Date());
        return null;
    }

    // [2026-09-18] "보스는 하루 3번만 처치할수있도록" 요청(모든 보스층 공통, 9층부터) --
    // DICE_ROLL_DATE/COUNT_TODAY와 같은 "조회 시점에 날짜만 비교" 패턴이지만, 게이트 위치는
    // 주사위 자체가 아니라 "몬스터가 실제로 죽는 시점"(resolveCombatTurn의 monsterDead 확정
    // 직후, 99층 보스 1회부활 로직과 동일 위치)이다 -- 보스전 진행 중 갑자기 주사위를 못
    // 굴리게 되는 어색함을 피하고, 그 대신 "오늘의 마지막 일격이 안 먹힌다"는 형태로 처리.
    /** 오늘 보스 처치 횟수(날짜 바뀌면 0으로 간주, 조회만 하고 DB는 안 건드림). */
    private int bossKillCountToday(HashMap<String, Object> p) {
        java.util.Date killDate = (java.util.Date) p.get("BOSS_KILL_DATE");
        boolean sameDay = killDate != null
                && new java.sql.Date(killDate.getTime()).toLocalDate().equals(java.time.LocalDate.now());
        return sameDay ? intVal(p.get("BOSS_KILL_COUNT_TODAY"), 0) : 0;
    }

    /** 보스 처치가 실제로 확정된 시점에 카운터 +1(날짜 바뀌었으면 1로 리셋). */
    private void bumpBossKillCountToday(String userName, HashMap<String, Object> p) {
        int newCount = bossKillCountToday(p) + 1;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("bossKillCountToday", newCount);
        up.put("touchBossKillDate", true);
        dao.updateUserProgress(up);
        p.put("BOSS_KILL_COUNT_TODAY", newCount);
        p.put("BOSS_KILL_DATE", new java.util.Date());
    }

    private String rollDiceInternal(String userName, HashMap<String, Object> p, String status) {
        int floor = intVal(p.get("CUR_FLOOR"), 0);

        if ("IN_COMBAT".equals(status)) {
            return resolveCombatTurn(userName, p, floor);
        }

        int m = floor % 10;
        // [2026-09-17] "파티 전멸 후에도 다음 주사위를 쓸 수 있던데" 신고 -- 마을(m==0)에서는
        // 계속 움직일 수 있어야 하므로(회복이 마을에서만 일어남) 사냥터/보스층에서만 막는다.
        if (m != 0 && isPartyWiped(userName)) {
            return userName + "님," + NL + "💀 파티 전원이 전투불가 상태입니다. 마을로 돌아가야 부활합니다. (/층변경 0 또는 /탑내려가기)";
        }
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
        // [2026-09-09] 특수칸에서 건 "다음 이동 1회, 주사위 2개" 플래그(handleSpecialTile 참고)
        // 소모 -- 소모는 여기 이동 굴림에서만 하고(전투 공격 굴림엔 관여 안 함), 결과는 두 눈을
        // 더한 값. rollLabel은 화면에 "3+5=8"처럼 두 눈을 그대로 보여주기 위한 표시용 문자열.
        boolean doubleDice = "Y".equals(strVal(p.get("DOUBLE_DICE_YN"), "N"));
        int roll;
        String rollLabel;
        if (doubleDice) {
            int roll1 = rollFace(diceMinFor(p), diceMax);
            int roll2 = rollFace(diceMinFor(p), diceMax);
            roll = roll1 + roll2;
            rollLabel = roll1 + "+" + roll2 + "=" + roll;
            HashMap<String, Object> clearUp = new HashMap<>();
            clearUp.put("userName", userName);
            clearUp.put("doubleDiceYn", "N");
            dao.updateUserProgress(clearUp);
            p.put("DOUBLE_DICE_YN", "N");
        } else {
            roll = rollFace(diceMinFor(p), diceMax);
            rollLabel = String.valueOf(roll);
        }
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

        // [2026-09-18] "워프포인트를 없애고, 15%마다 탐사율자동저장하게 로직화" 요청 -- 특수칸
        // (구 워프포인트)을 직접 밟아야만 저장되던 체크포인트를, 탐사율이 새 15%p 구간을 넘을
        // 때마다 자동으로 저장하도록 바꿨다. 매 이동마다 DB에 또 쓰지 않도록, 저장된 체크포인트가
        // 속한 15%p 구간(tier)보다 지금이 더 높은 구간일 때만 실제로 갱신한다.
        if (floor >= 51 && tileCount > 0) {
            int tierNow = (visited * 100 / tileCount) / 15;
            if (tierNow > 0) {
                HashMap<String, Object> floorBest = dao.selectUserFloorBest(userName, floor);
                int savedCheckpoint = floorBest == null ? 0 : intVal(floorBest.get("CHECKPOINT_VISITED_COUNT"), 0);
                int tierSaved = (savedCheckpoint * 100 / tileCount) / 15;
                if (tierNow > tierSaved) markSpecialTileCheckpoint(userName, floor, visited);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님," + NL);
        sb.append(doubleDice ? "🎲🎲 주사위 " : "🎲 주사위 ").append(rollLabel).append("! ").append(curTile).append(" → ").append(newTile).append("번 칸")
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
        String halfExploreReward = checkExploreHalfReward(userName, p, floor, visited, tileCount);
        if (halfExploreReward != null) sb.append(NL).append(halfExploreReward);
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
                if (blockNo(floor) >= 6) {
                    luckyEffectList.add("HP_DOUBLE"); luckyEffectList.add("SHIELD_ON_ATK");
                    // [2026-09-15 신설] 파티원 1명 대상 다음 즉사방어(1회) / 1턴간 체력 200%
                    // (기존 3턴짜리 HP_DOUBLE과는 별개 효과 -- 지속시간만 다름).
                    luckyEffectList.add("DEATH_WARD"); luckyEffectList.add("HP_DOUBLE_1T");
                }
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
                } else if ("HP_DOUBLE".equals(luckyEffect) || "SHIELD_ON_ATK".equals(luckyEffect) || "HP_DOUBLE_1T".equals(luckyEffect)) {
                    // [2026-09-06, 51층+ 전용] ATK_UP/DEF_UP과 같은 컬럼(LUCKY_TURN_LEFT/EFFECT)을
                    // 재사용해 지속시킨다(덮어쓰기 안내 로직도 동일하게 적용).
                    // [2026-09-15] HP_DOUBLE_1T만 1턴, 나머지는 기존대로 3턴 지속.
                    int prevLuckyTurnLeft2 = intVal(p.get("LUCKY_TURN_LEFT"), 0);
                    String prevLuckyEffect2 = strVal(p.get("LUCKY_EFFECT"), "");
                    boolean overwrote2 = prevLuckyTurnLeft2 > 0 && !prevLuckyEffect2.isEmpty() && !prevLuckyEffect2.equals(luckyEffect);
                    int luckyDurationTurns = "HP_DOUBLE_1T".equals(luckyEffect) ? 1 : 3;
                    HashMap<String, Object> up2 = new HashMap<>();
                    up2.put("userName", userName);
                    up2.put("luckyTurnLeft", luckyDurationTurns);
                    up2.put("luckyEffect", luckyEffect);
                    dao.updateUserProgress(up2);
                    if ("HP_DOUBLE".equals(luckyEffect)) {
                        sb.append("🍀 심상치 않은 럭키 칸! 앞으로 3번 이동하는 동안 체력이 두 배로 버팁니다. (받는 피해 절반)");
                    } else if ("HP_DOUBLE_1T".equals(luckyEffect)) {
                        sb.append("🍀 강렬한 럭키 칸! 다음 1번의 전투 동안 체력이 두 배로 버팁니다. (받는 피해 절반)");
                    } else {
                        sb.append("🍀 심상치 않은 럭키 칸! 앞으로 3번 이동하는 동안 매 턴 파티 전원의 공격력만큼 방어막이 추가로 생성됩니다.");
                    }
                    if (overwrote2) {
                        sb.append(NL).append("(기존 럭키 효과는 새 효과로 갱신되어 사라졌습니다)");
                    }
                } else if ("DEATH_WARD".equals(luckyEffect)) {
                    // [2026-09-15 신설, 2026-09-16 재설계] 파티원 한 명에게 "다음 피해 1회 면역".
                    // 원래는 "이번 피해로 HP가 정확히 0이 될 때만"(치명타 한정) 발동하는 즉사방어로
                    // 만들었는데, 실제로는 그 조건을 만족하는 순간이 드물어 "잘 작동 안 하는 것
                    // 같다"는 신고를 받았다 -- 일반 몬스터/중간보스는 즉사 능력이 따로 없고
                    // 보스전은 진입 시 이 효과 자체가 초기화(changeFloor)돼 걸려있을 수 없으므로,
                    // 굳이 치명타로 한정할 이유가 없었다. 그래서 "이 동료가 다음으로 맞는 피해
                    // 자체를 치명타 여부와 무관하게 무조건 0으로 막는" 것으로 단순화(resolveCombatTurn
                    // 반격 피해 적용부 참고) -- 다음 피격 즉시 눈에 보이게 발동해 체감이 훨씬 명확함.
                    // 기존 럭키 버프(LUCKY_TURN_LEFT/EFFECT)와는 별개 컬럼(WARD_COMPANION_ID)을
                    // 쓰므로 서로 덮어쓰지 않고 동시에 걸려있을 수 있다.
                    List<HashMap<String, Object>> wardParty = new ArrayList<>();
                    for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                        if (c.get("PARTY_SLOT") != null) wardParty.add(c);
                    }
                    if (wardParty.isEmpty()) {
                        sb.append("🍀 럭키 칸! 누군가를 지켜주려는 기운을 느꼈지만... 파티가 비어있어 효과가 없었다.");
                    } else {
                        HashMap<String, Object> warded = wardParty.get(RND.nextInt(wardParty.size()));
                        int wardCid = intVal(warded.get("COMPANION_ID"), 0);
                        String wardedName = strVal(warded.get("NAME"), JOB_NAME.getOrDefault(strVal(warded.get("CLASS"), "WARRIOR"), "동료"));
                        String wardedTag = jobTag(intVal(warded.get("GRADE"), 1), strVal(warded.get("CLASS"), "WARRIOR"), wardedName);
                        HashMap<String, Object> wardUp = new HashMap<>();
                        wardUp.put("userName", userName);
                        wardUp.put("wardCompanionId", wardCid);
                        dao.updateUserProgress(wardUp);
                        p.put("WARD_COMPANION_ID", wardCid);
                        sb.append("🍀 강력한 가호! ").append(wardedTag).append("에게 다음 피해 1회 면역이 걸렸습니다. (다음 반격을 맞는 순간 그 피해를 완전히 막아냅니다, 1회 소모)");
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
                // 함정 효과 종류 중 무작위 -- 공격력/방어력 약화는 이후 3번의 보드 이동(위 trapTurnLeft
                // 감소 로직 기준) 동안 지속되는 파티 전체 디버프.
                // 실제 적용은 resolveCombatTurn의 파티 공격 루프(ATK_DOWN)와 몬스터 반격 대상
                // 방어력 계산(DEF_DOWN)에서 이뤄진다.
                // [2026-09-06, 51층+ 전용] RESET_TILE(처음 계단칸으로 돌아가기)/SKILL_LOCK(스킬
                // 사용금지 1턴) 2종 추가 -- 51층 미만에서는 나오지 않는다.
                // [2026-09-15] PP_LOSS(즉시 5% PP 손실) 효과 삭제 요청으로 제거.
                // [2026-09-18] "함정칸 밟으면 -4~1칸으로 이동하게 만드는 트랩도 만들어줘" 요청 --
                // MOVE 신설(전 층 공통, RESET_TILE처럼 즉시발동형 위치이동). -4~+1(6가지, 뒤로
                // 밀리는 쪽이 더 넓은 범위)만큼 보드를 이동시키고, 그 결과 칸이 COMBAT이면 그
                // 자리에서 바로 전투가 시작된다("함정으로 전투가 발생하면 한대맞고 시작"). 그 외
                // 칸(TRAP/TREASURE/계단 등)에 떨어지면 RESET_TILE과 동일하게 위치만 옮기고 그
                // 칸 자체의 효과는 트리거하지 않는다(연쇄 재귀 방지, 의도적 단순화).
                List<String> effectList = new ArrayList<>();
                effectList.add("ATK_DOWN"); effectList.add("DEF_DOWN"); effectList.add("MOVE");
                if (blockNo(floor) >= 6) { effectList.add("RESET_TILE"); effectList.add("SKILL_LOCK"); }
                String effect = effectList.get(RND.nextInt(effectList.size()));
                HashMap<String, Object> up = new HashMap<>();
                up.put("userName", userName);
                if ("MOVE".equals(effect)) {
                    int moveDelta = -4 + RND.nextInt(6); // -4..+1
                    int movedTile = (((newTile - 1 + moveDelta) % tileCount) + tileCount) % tileCount + 1;
                    HashMap<String, Object> moveUp = new HashMap<>();
                    moveUp.put("userName", userName);
                    moveUp.put("floor", floor);
                    moveUp.put("curTile", movedTile);
                    dao.upsertUserFloorProgress(moveUp);
                    // [2026-09-19] "함정칸으로 후진되었을 때 탐사한 것으로 쳐달라" 요청 --
                    // 그동안 원래 밟았던 newTile 방문만 기록되고, 실제로 밀려나 도착한
                    // movedTile은 전혀 기록되지 않아 탐사율이 안 올라갔다.
                    dao.insertTileVisit(userName, floor, movedTile);
                    sb.append("🕳️ 함정에 걸렸다! 바닥이 무너지며 ").append(Math.abs(moveDelta)).append("칸 ")
                      .append(moveDelta <= 0 ? "뒤로" : "앞으로").append(" 밀려났다! (").append(movedTile).append("번 칸)");
                    String movedTileType = "COMBAT";
                    for (HashMap<String, Object> t : tiles) {
                        if (intVal(t.get("TILE_NO"), -1) == movedTile) {
                            movedTileType = strVal(t.get("TILE_TYPE"), "COMBAT");
                            break;
                        }
                    }
                    // [2026-09-19 정정] "지금은 전투도 안하고 탐사도안돼, 내설계는
                    // 전투(선공을 받는다, 50층이상은 몬스터가 1.1배데미지)였던거같아" 요청 --
                    // 뒤로 밀려난 경우(moveDelta<0)는 도착 칸의 실제 타입과 무관하게 항상
                    // 전투가 시작되도록 변경(원래는 우연히 COMBAT 타입 칸에 떨어졌을 때만
                    // 싸웠는데, 칸 타입 비율상 대부분은 아무 일도 안 일어나는 것처럼 보였다).
                    // 앞으로 밀려난 경우(moveDelta>=0)는 기존처럼 그 칸이 진짜 COMBAT일 때만.
                    if (moveDelta < 0 || "COMBAT".equals(movedTileType)) {
                        boolean movedMidBoss = blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT;
                        sb.append(NL).append(NL).append("😱 밀려난 자리에서 몬스터와 부딪혔다!").append(NL)
                          .append(startCombat(userName, p, floor, false, false, movedMidBoss, true));
                    }
                } else if ("RESET_TILE".equals(effect)) {
                    // 즉시 발동형 1회성 효과 -- TRAP_TURN_LEFT는 건드리지
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
                sb.append(handleSpecialTile(userName, p, floor, visited));
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
                // [2026-09-16] "50층 이상 올라가는 조건을 탐사율 50%이상으로(기존 중간보스처치)"
                // 요청 -- 81층+ 전용이던 "중간보스 1회 처치" 게이트(MIDBOSS_KILLED_YN)를 완전히
                // 대체해서, 50층 이상은 이 층 탐사율(visited/tileCount)이 일정% 이상이어야
                // 계단이 열린다. 계단을 밟아도 즉시 층이동하지 않는 기존 동작은 그대로(자격만 부여).
                // [2026-09-17 후속] "계단 4개가 전부 50%였는데 0/10/20/25%로 각각 다르게,
                // 50%는 없도록" 요청 -- 층마다 4개인 STAIRS_UP 칸을 TILE_NO 순서대로 정렬해
                // 그 순서에 0/10/20/25%를 배정한다(stairsUpRequiredPct 참고). 즉 같은 층 안에서도
                // 어느 계단을 밟았느냐에 따라 요구 탐사율이 다르다.
                boolean exploreGateFloor = floor >= 50;
                int explorePct = tileCount > 0 ? (visited * 100 / tileCount) : 0;
                int requiredPct = exploreGateFloor ? stairsUpRequiredPct(userName, floor, newTile) : 0;
                if (exploreGateFloor && explorePct < requiredPct) {
                    sb.append("🪜⬆️❓ 위로 향하는 계단을 발견했지만... 무언가 강력한 기운이 막고 있다!").append(NL)
                      .append("이 층을 ").append(requiredPct).append("% 이상 탐사해야 계단이 열립니다. (현재 ").append(explorePct).append("%)");
                    break;
                }
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

    /** [2026-09-17] "파티 전멸 후에도 다음 주사위를 계속 쓸 수 있다" 신고 -- 테스트계정 전용
     *  우회가 아니라 실제 모든 계정에 있던 빈틈이었다. 전멸 시 STATUS는 NORMAL로 돌아가는데
     *  (마을로 이동은 계속 할 수 있어야 해서), startCombat()엔 파티 생존 여부를 전혀 확인하지
     *  않는다 -- 그래서 전멸한 채로 사냥터를 계속 돌아다니면 COMBAT/ELITE/보스 칸에서 새
     *  전투가 또 시작되고, 양쪽 다 공격 가능한 인원이 0명이라 0dmg만 오가며 의미 없이
     *  반복됐다. 파티 전원(편성된 동료 기준) HP가 0이면 true. */
    private boolean isPartyWiped(String userName) {
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") != null) party.add(c);
        }
        if (party.isEmpty()) return false; // 편성 자체가 없으면 이건 다른 안내(파티편성 필요)로 처리됨
        for (HashMap<String, Object> c : party) {
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) > 0) return false;
        }
        return true;
    }

    /** [2026-09-18] "워프포인트를 없애고... 기존 워프포인트는 무너진사원이라고 바꿔주고"
     *  요청으로 전면 재설계. 탐사율 체크포인트는 더 이상 이 칸을 밟아야만 저장되지 않고
     *  rollDiceInternal에서 15%p 단위로 자동 저장되므로(markSpecialTileCheckpoint 자동 호출
     *  지점 참고), 이 칸 자체는 순수하게 4갈래 확률표 하나로 재구성:
     *  5% 전설의조각, 15% PP 획득, 50% 전투(몬스터 조우), 나머지 30% 축복(공격력 30%
     *  강화 1턴). floor<51은 애초에 ensureUserBoard()가 SPECIAL 칸을 안 놓아서 이 분기에
     *  거의 안 들어오지만, 혹시 남아있는 옛 보드 대비 방어적으로 "아무 일도 없었다"만 보여줌. */
    private String handleSpecialTile(String userName, HashMap<String, Object> p, int floor, int visited) {
        dao.upsertSpecialVisitIncrement(userName);
        HashMap<String, Object> v = dao.selectUserSpecialVisit(userName);
        int cnt = v == null ? 1 : intVal(v.get("VISIT_COUNT"), 1);
        StringBuilder sb = new StringBuilder("🏚️ 무너진 사원을 발견했다... (누적 방문 ").append(cnt).append("회)");
        int[] thresholds = { 10, 50, 100 };
        int[] achIds = { 17, 18, 19 };
        for (int i = 0; i < thresholds.length; i++) {
            if (cnt == thresholds[i]) {
                grantAchievement(userName, achIds[i]);
                sb.append(NL).append("🏆 히든 업적 달성!");
            }
        }
        if (floor < 51) {
            sb.append(NL).append("...이번엔 별다른 일이 일어나지 않았다.");
            return sb.toString();
        }
        int roll = RND.nextInt(100);
        if (roll < 5) {
            int newFragment = intVal(p.get("LEGEND_FRAGMENT"), 0) + 1;
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("legendFragment", newFragment);
            dao.updateUserProgress(up);
            p.put("LEGEND_FRAGMENT", newFragment);
            sb.append(NL).append("🧩 폐허 잔해 속에서 전설의조각을 발견했다! (보유 ").append(newFragment).append("개)");
        } else if (roll < 20) {
            HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
            PP reward = mon == null ? PP.of(10, "")
                    : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(3 * floorPpMultiplier(floor));
            addPp(userName, p, reward);
            PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
            sb.append(NL).append("💰 폐허 속에 묻혀있던 ").append(reward.format()).append(" PP를 발견했다! (보유 ").append(curPp.format()).append(" PP)");
        } else if (roll < 70) {
            boolean midBossEncounter = blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT;
            sb.append(NL).append("😱 사원 안쪽에 숨어있던 몬스터가 튀어나왔다!").append(NL)
              .append(startCombat(userName, p, floor, false, false, midBossEncounter));
        } else {
            // 축복: 기존 럭키칸 ATK_UP_30과 동일 컬럼(LUCKY_TURN_LEFT/LUCKY_EFFECT)을 그대로
            // 재사용하되, 지속시간만 "1턴"(HP_DOUBLE_1T와 동일한 1턴 한정 패턴)으로 짧게 둔다.
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("luckyTurnLeft", 1);
            up.put("luckyEffect", "ATK_UP_30");
            dao.updateUserProgress(up);
            sb.append(NL).append("✨ 무너진 사원의 축복을 받았다! 다음 1번의 이동/전투 동안 파티 전원의 공격력이 30% 강화됩니다.");
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

    /** [2026-09-10] S4 낚시 연동 -- BotS4Service.grantFishingBonus 인터페이스 문서 참고. */
    @Override
    @Transactional
    public PP grantFishingBonus(String userName, int fishGrade) {
        if (java.time.LocalDate.now().isBefore(java.time.LocalDate.parse(FISHING_PP_START_DATE))) return null;
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) return null; // S5(탑) 진행기록이 없는 유저 -- 호출부(S4)가 멘트 자체를 생략
        int floor = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), "N");
        if (mon == null) return null;
        int g = Math.max(1, Math.min(8, fishGrade));
        PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
        PP reward = perKill.multiply(floorPpMultiplier(floor) * FISH_GRADE_PP_MULT[g]);
        addPp(userName, p, reward);
        return reward;
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

    /** [2026-09-15] hasUsableCompanionVoucher와 완전히 동일한 목적/패턴, 악세서리뽑기용. */
    private boolean hasUsableAccessoryVoucher(HashMap<String, Object> p, int gachaId) {
        return gachaId >= 1 && gachaId <= 4 && intVal(p.get("ACCESSORY_VOUCHER_T" + gachaId), 0) > 0;
    }

    /** [2026-09-15] consumeCompanionVoucher와 완전히 동일한 정책/패턴, 악세서리뽑기용. */
    private boolean consumeAccessoryVoucher(String userName, HashMap<String, Object> p, int gachaId) {
        if (gachaId < 1 || gachaId > 4) return false;
        String field = "accessoryVoucherT" + gachaId;
        String column = "ACCESSORY_VOUCHER_T" + gachaId;
        int tierCur = intVal(p.get(column), 0);
        if (tierCur <= 0) return false;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, tierCur - 1);
        dao.updateUserProgress(up);
        p.put(column, tierCur - 1);
        return true;
    }

    private String startCombat(String userName, HashMap<String, Object> p, int floor, boolean boss, boolean elite, boolean midBoss) {
        return startCombat(userName, p, floor, boss, elite, midBoss, false);
    }

    /** [2026-09-18] trapAmbush -- 함정칸(MOVE 효과)으로 떠밀려 우연히 COMBAT칸에 떨어져 시작된
     *  전투. resolveCombatTurn()의 "1턴째 기습" 조건에 플로어 무관하게 걸리게 하고, 71층+에서는
     *  선공몬스터 특성과 겹쳐 그 전투 내내 몬스터 공격력 10% 증가까지 함께 적용된다. */
    private String startCombat(String userName, HashMap<String, Object> p, int floor, boolean boss, boolean elite, boolean midBoss, boolean trapAmbush) {
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(blockNo(floor), boss ? "Y" : "N"), floor);
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
        // [2026-09-09] "61층부터는 일반 몬스터가 두 마리 나오게 해달라" 요청 -- 보스/강화몹/
        // 중간보스가 아닌 평범한 COMBAT 조우가 블록7(61~70층)부터 "2마리"(PP보상 2배)로
        // 취급된다. ATK/DEF는 그대로 둬서 "강화몹처럼 개별로 세진 게" 아니라 "매 턴 파티
        // 2명을 공격하는" 결과가 되게 한다(resolveCombatTurn의 doubleTarget 다중타겟 로직을
        // 그대로 재사용). [2026-09-12] "I번부터 죽여야 II번이 나온다" 요청으로, HP는 더는
        // 미리 합쳐서(2배) 하나의 풀로 만들지 않고 각자 base HP만큼의 별도 풀 2개
        // (I=curMonsterHpValue, II=curMonster2HpValue, II는 "대기" 상태로 시작)로 둔다 --
        // I번이 죽어야 resolveCombatTurn에서 II번이 활성화된다.
        // [2026-09-14] "89층보스는 위 능력을 모두 포함한 보스 2체를 상대하도록 해줘" 요청 --
        // 61층+ "몬스터 두 마리"와 같은 순차처치(I번 죽어야 II번 등장) 인프라를 그대로
        // 재사용해서 89층 보스를 "보스 2체"로 만든다. eliteMult가 1.0(보스는 elite/midBoss와
        // 배타적)이라 perMonsterHp는 그냥 보스 기본 HP 그대로 -- 즉 "체력을 나눈 하나"가
        // 아니라 "완전한 보스 몸통 2개"가 된다. isBossRow 기준 다른 보스 능력들(2인타격/
        // 흡혈/스킬도용+반격/공격력1.6배/주사위상향)은 floor만으로 계산되므로 I번이든
        // II번이든 자동으로 그대로 적용된다.
        boolean dualBossFloor = boss && floor == 89;
        boolean dualMonster = (!boss && !elite && !midBoss && blockNo(floor) >= 7) || dualBossFloor;
        double dualHpMult = dualMonster ? 2.0 : 1.0; // 처치보상(2마리분) 계산에만 사용, HP엔 미적용
        double perMonsterHp = ((Number) mon.get("HP_VALUE")).doubleValue() * eliteMult;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("status", "IN_COMBAT");
        up.put("curMonsterId", intVal(mon.get("MONSTER_ID"), 0));
        up.put("curMonsterHpValue", perMonsterHp);
        up.put("curMonsterHpExt", strVal(mon.get("HP_EXT"), ""));
        up.put("curMonsterEliteYn", elite ? "Y" : "N");
        up.put("curMonsterMidbossYn", midBoss ? "Y" : "N");
        up.put("curMonsterDualYn", dualMonster ? "Y" : "N");
        up.put("trapAmbushYn", trapAmbush ? "Y" : "N"); // 항상 명시 세팅(직전 전투가 함정기습이었던 잔여값 방지)
        up.put("curCombatTurn", 0); // 새 전투 시작 -- 69층 보스 등 턴제한 타이머를 0부터 다시 셈
        // [2026-09-14] "99층 보스는 죽으면 200% 체력으로 한 번 부활" 요청 -- 새 전투 시작
        // 시점엔 항상 아직 부활을 안 쓴 상태로 초기화(clearMonster에서도 'N'으로 정리되지만,
        // startCombat은 clearMonster 없이 바로 새 몬스터를 채우는 경로라 여기서도 명시).
        up.put("curMonsterRevivedYn", "N");
        if (dualMonster) {
            up.put("curMonster2HpValue", perMonsterHp); // II번은 대기 -- I번이 죽어야 활성화(resolveCombatTurn 참고)
        } else {
            up.put("clearMonster2", true); // 방어적 정리(직전 전투가 dual이었을 잔여값 대비)
        }

        // [정책 변경, 2026-09-05] "29층 보스가 너무 세다"는 신고로, 블록3+ 보스의 "무시"
        // 스킬(파티원 1명 지목, 그 동료는 전투 내내 보스에게 공격 불가)을 완전히 제거했다.
        // 이제 블록3+ 보스 특수 스킬은 아래 반격 파트의 "기절"(매 턴 확률로 1명 스턴) 하나뿐
        // -- 발동 확률도 30%→20%로 낮춰서 예전보다 확실히 약해졌다. BOSS_IMMUNE_CID는 더 이상
        // 세팅하지 않지만 컬럼/조회/초기화 로직은 과거 진행 중이던 값 정리를 위해 남겨둔다.
        dao.updateUserProgress(up);

        PP fullHp = PP.of(perMonsterHp, strVal(mon.get("HP_EXT"), "")).normalize();
        StringBuilder sb = new StringBuilder();
        // [형식 정리 요청] "OO 등장!"을 한 줄에 다 몰아넣지 않고 "등장!" 알림 / 몬스터 이름 /
        // 능력치를 각각 줄로 나눔("능력치" 라벨·콜론도 빼서 더 짧게). [2026-09-06] 중간보스는
        // "맵에는 일반적인 몬스터로 표시되는데" 요청대로 평범한 등장 메시지("👾 등장!")를 그대로
        // 쓰고 강화몹처럼 정체를 미리 알려주는 문구도 없다 -- 실제 스탯(3배)은 아래에 그대로
        // 노출되지만, 정체는 전투 중 스킬 훔치기가 나와야 드러난다.
        sb.append(dualBossFloor ? "👹👹 보스 두 체 등장!" : boss ? "👹 보스 등장!" : elite ? "💪 강화 등장!" : dualMonster ? "👾👾 몬스터 두 마리 등장!" : "👾 등장!").append(NL);
        sb.append(floorMonsterName(floor, mon)).append(NL);
        sb.append("⚔️ ").append((int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult))
          .append(" 🛡️ ").append((int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult))
          .append(" ❤️ ").append(fullHp.format()).append(dualMonster ? " x2" : "").append(NL);
        if (elite) sb.append("💪 강화몹 -- 스탯/보상 전부 평소의 2배입니다.").append(NL);
        // [2026-09-18] 함정으로 떠밀려 시작된 전투 -- 다음 /주사위(공격)에서 몬스터가 먼저
        // 기습한다(71층+면 그 기습을 포함해 전투 내내 몬스터 공격력도 10% 증가).
        if (trapAmbush) sb.append("😱 불시의 조우라 다음 공격 전에 몬스터가 먼저 기습합니다!").append(NL);
        // [2026-09-12] "I번부터 죽여야 II번이 나온다" 요청으로 문구 갱신 -- 예전엔 체력을
        // 미리 합쳐서 하나처럼 보였는데, 이제 I번을 완전히 처치해야 II번이 등장한다.
        // [2026-09-14] 89층은 "보스" 문구로 갈라서 안내(몬스터 두 마리와 동일 인프라지만
        // 표현만 보스답게).
        if (dualBossFloor) sb.append("👹👹 보스 두 체 -- I번을 처치해야 II번이 등장합니다(총 처치보상 2배). 이 층의 모든 보스 능력이 둘 다에게 적용됩니다.").append(NL);
        else if (dualMonster) sb.append("👾👾 몬스터 두 마리 -- I번을 처치해야 II번이 등장합니다(총 처치보상 2배). 매 턴 파티원 2명을 공격합니다.").append(NL);
        // [2026-09-14] "99층은 즉사능력은 없으나 세 명을 동시공격" 사전 안내.
        if (floor == 99 && boss) sb.append("👥👥👥 이 보스는 매 턴 동료 3명을 동시에 공격합니다.").append(NL);
        Integer enrageLimit = boss ? BOSS_ENRAGE_TURN_LIMIT.get(floor) : null;
        if (enrageLimit != null) sb.append("⏳ ").append(enrageLimit).append("턴 안에 처치하지 못하면 폭주(공격력 급상승)합니다!").append(NL);
        // [2026-09-14] "99층 보스는 죽이면 200% 체력으로 한 번 부활" 사전 안내.
        if (floor == 99 && boss) sb.append("💥 이 보스는 한 번 쓰러뜨려도 200% 체력으로 부활합니다 -- 두 번 처치해야 완전히 끝납니다!").append(NL);
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
        // [2026-09-15 신설]
        if (luckyTurnLeft > 0 && ("HP_DOUBLE".equals(luckyEffect) || "HP_DOUBLE_1T".equals(luckyEffect))) {
            notes.add("🍀 럭키 효과로 체력이 두 배로 버티는 중 (남은 이동 " + luckyTurnLeft + "회)");
        }
        if (luckyTurnLeft > 0 && "SHIELD_ON_ATK".equals(luckyEffect)) {
            notes.add("🍀 럭키 효과로 매 턴 파티 공격력만큼 방어막 생성 중 (남은 이동 " + luckyTurnLeft + "회)");
        }
        if (intVal(p.get("WARD_COMPANION_ID"), 0) > 0) {
            notes.add("✨ 파티원 한 명에게 다음 피해 1회 면역이 걸려있습니다.");
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

        // [2026-09-09] "61층부터는 일반 몬스터가 두 마리 나오고, 69층 보스는 10턴내
        // 처치하지 못하면 폭주하게 해달라" 요청 -- 이번 전투(resolveCombatTurn 1회 호출 =
        // 1턴)가 몇 턴째인지 먼저 세어둔다. MAGE 스킬도용/lateBoss 기절처럼 턴 중간에
        // return하는 분기가 여러 곳이라, 각 분기에 따로 끼워넣는 대신 여기서 한 번에
        // 갱신해서 어느 경로로 끝나든 항상 정확히 반영되게 한다.
        int curCombatTurn = intVal(p.get("CUR_COMBAT_TURN"), 0) + 1;
        HashMap<String, Object> turnUp = new HashMap<>();
        turnUp.put("userName", userName);
        turnUp.put("curCombatTurn", curCombatTurn);
        dao.updateUserProgress(turnUp);

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
        // [2026-09-09] 61층+(블록7) 일반 몬스터 "2마리" 조우 -- 반격은 아래에서 기존 보스
        // 다중타겟 인프라를 같이 써서 매 턴 파티 2명을 공격하게 만든다(=2마리가 각자
        // 공격하는 것과 동일한 결과). [2026-09-12] "I번부터 죽여야 II번이 나온다" 요청으로
        // HP는 더 이상 합쳐서(2배) 취급하지 않고, 각자 자기 체력(base)만큼만 가진 별도
        // 풀(I=CUR_MONSTER_HP_VALUE, II=CUR_MONSTER2_HP_VALUE)로 순차 처치된다.
        // [2026-09-13] "오버킬 없이 동료별로 나눠서 데미지 적용" 요청으로, 파티 전체
        // 데미지를 한 번에 모아 빼는 대신 아래 파티 공격 루프 안에서 동료 한 명씩 "현재
        // 타겟"에 즉시 적용한다(dualTarget1Remain/dualTarget2Remain 등 참고). dualHpMult는
        // "총 처치보상 2배"에만 쓰인다(HP 계산에서는 제외).
        boolean dualMonster = "Y".equals(strVal(p.get("CUR_MONSTER_DUAL_YN"), "N"));
        double dualHpMult = dualMonster ? 2.0 : 1.0;
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
        // [2026-09-15] HP_DOUBLE_1T(1턴짜리)도 동일 로직(받는 피해 절반)을 그대로 공유.
        boolean luckyHpDouble = intVal(p.get("LUCKY_TURN_LEFT"), 0) > 0
                && ("HP_DOUBLE".equals(luckyEffectNow) || "HP_DOUBLE_1T".equals(luckyEffectNow));
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

        // [2026-09-13] "71층부터는 플레이어 선공이 아니라 몬스터 선공(첫타 은신처리)" 요청 --
        // 71층 이상(블록8)의 일반 몬스터(보스 제외 -- 79층 보스는 아래 boss79Ambush로 별도
        // 처리)는 전투 1턴째에 한해 파티가 공격하기 전에 몬스터가 은신에서 튀어나와 먼저
        // 한 대 때린다. 이 공격은 반격 인프라(개인 방어력만 반영)를 그대로 재사용하되,
        // 아직 파티가 행동하기 전이라 보호막/도발 등은 적용되지 않는다(말 그대로 기습).
        // [2026-09-18] "함정칸으로 전투가 발생하면 한대맞고 시작하게, 일정층수 이상
        // 선공몬스터한테는 그 전투에 몬스터 데미지가 10%증가" 요청 -- 함정(MOVE)으로 떠밀려
        // 시작된 전투(TRAP_AMBUSH_YN)는 층수 무관하게 이 기습을 강제로 겪는다.
        // [2026-09-19 정정] "내 설계는... 50층이상은 몬스터가 1.1배데미지를 갖는다 였던거
        // 같아" -- 처음 구현 때 "일정층수 이상"을 자연 선공몬스터 구간(71층+, naturalAmbushFloor)
        // 기준으로 잘못 재활용했었다. 원래 의도한 기준은 50층+라 별도 상수로 분리.
        // [2026-09-20 확장] "데미지가 낮을 때도 많아야 한다, 평소공격력의 50%로 때리게 해달라
        // (보스포함)" 요청 -- (1) 기습의 기준 공격력 자체를 ATK_VALUE의 절반으로 낮춰서
        // MAX_AMBUSH_DMG 상한에 거의 항상 붙던 것과 달리 주사위 결과에 따라 낮은 데미지도
        // 흔히 나오게 하고, (2) 원래 보스는 이 기습에서 통째로 제외돼 있었는데 "보스포함"
        // 요청에 맞춰 79/89층 전용 은신 즉사(boss79Ambush, 아래 별도 처리) 대상만 계속
        // 제외하고 그 외 보스(51~78/80~88/90+층 보스행)는 이 기습을 함께 겪도록 열었다.
        boolean trapAmbushYn = "Y".equals(strVal(p.get("TRAP_AMBUSH_YN"), "N"));
        boolean naturalAmbushFloor = floor >= 71;
        double trapAmbushDmgMult = (trapAmbushYn && floor >= 50) ? 1.1 : 1.0;
        boolean specialBossAmbushFloor = (floor == 79 || floor == 89) && "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        if ((naturalAmbushFloor || trapAmbushYn) && curCombatTurn == 1 && !specialBossAmbushFloor) {
            List<HashMap<String, Object>> ambushAlive = new ArrayList<>();
            for (HashMap<String, Object> c : party) {
                PP ahp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                if (PP.toBaseValue(ahp) > 0) ambushAlive.add(c);
            }
            if (!ambushAlive.isEmpty()) {
                HashMap<String, Object> amTarget = ambushAlive.get(RND.nextInt(ambushAlive.size()));
                List<HashMap<String, Object>> amEquips = dao.selectEquipByCompanion(intVal(amTarget.get("COMPANION_ID"), 0));
                int[] amEff = computeEffectiveStat(strVal(amTarget.get("CLASS"), "WARRIOR"), intVal(amTarget.get("GRADE"), 1),
                        amEquips, userStat, intVal(amTarget.get("LIMIT_BREAK"), 0));
                int amMonsterAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * trapAmbushDmgMult * AMBUSH_ATK_PCT);
                // [2026-09-19] "51층+ 몬스터는 자기만의 무작위 면수를 쓴다"는 규칙(반격과 동일,
                // monsterOwnDiceMax 참고)을 기습 굴림에도 맞춤 -- 예전엔 플레이어가 낀 주사위
                // (diceMax, 최대 20)를 그대로 재사용해서 플레이어가 강한 주사위를 낄수록
                // 몬스터 기습도 덩달아 세지는 부작용이 있었다.
                int amRoll = rollFace(1, monsterOwnDiceMax(floor, diceMax));
                int amDmg = Math.max(1, amMonsterAtk * amRoll - amEff[2]);
                // [2026-09-19] "★6 체력1만인데 기습이 4만" 신고 -- 재요청("즉사할 수도 있게는
                // 해야지, 다만 100% 확정 즉사는 과함")에 맞춰 즉사 자체는 막지 않되, 실드까지
                // 감안한 체감 최대치를 MAX_AMBUSH_DMG(2만)로 못박는다.
                amDmg = Math.min(amDmg, MAX_AMBUSH_DMG);
                sb.append("🌑 은신 기습! ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 먼저 공격한다!").append(NL);
                // 대상 최대체력의 90% 이상이면(사실상 빈사권) 한 명에게 몰아치지 않고 가능하면
                // 두 명에게 절반씩 나눠 때린다(다중공격 연출). [2026-09-20] 실제 즉사 방지는
                // applyAmbushHit()의 최종 하드 클램프(AMBUSH_DEATH_GUARD_PCT)가 담당하므로,
                // 여기서 분산 여부와 무관하게 기습만으로는 죽지 않는다.
                if (amDmg >= Math.round(amEff[0] * AMBUSH_SPLIT_THRESHOLD_PCT) && ambushAlive.size() > 1) {
                    List<HashMap<String, Object>> remaining = new ArrayList<>(ambushAlive);
                    remaining.remove(amTarget);
                    HashMap<String, Object> amTarget2 = remaining.get(RND.nextInt(remaining.size()));
                    int half = Math.max(1, amDmg / 2);
                    sb.append("💥 위력이 너무 강해 두 곳으로 갈라져 꽂혔다!").append(NL);
                    applyAmbushHit(userName, p, userStat, sb, amTarget, half);
                    applyAmbushHit(userName, p, userStat, sb, amTarget2, half);
                } else {
                    applyAmbushHit(userName, p, userStat, sb, amTarget, amDmg);
                }
                sb.append(NL);
            }
        }

        // 보스 기절 스킬(반격 턴에 걸림, 아래 참고)로 지정된 동료는 이번 공격 턴만 건너뛰고 소모된다.
        int bossStunCid = intVal(p.get("BOSS_STUN_CID"), 0);
        boolean stunConsumed = false;

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 (직업별 특수효과 포함) ──
        long totalDamage = 0;
        boolean stunned = false;
        int shieldPool = 0;
        // [2026-09-05] ★5/★6 마법사 "2턴 스턴" -- 지난 턴에 걸어둔 배너(MONSTER_STUNNED_YN)가
        // 있으면 이번 턴도 자동으로 스턴 처리하고 소모한다. mageBankNextTurn은 "이번 턴에 새로
        // 배너를 걸었는지" -- 아래에서 이번 턴 결과를 반영해 배너를 다시 쓰거나 지운다.
        boolean incomingBankedStun = "Y".equals(strVal(p.get("MONSTER_STUNNED_YN"), "N"));
        boolean mageBankNextTurn = false;
        if (skillLocked) sb.append("🔒 스킬 봉인 상태! 이번 턴은 파티 특수 스킬(직업별 효과)을 쓸 수 없다.").append(NL);

        // [2026-09-10] "더블주사위가 이동에만 적용되는 걸로 보인다, 1턴간 이동/공격 둘 다
        // 적용된다고 표시해달라" 요청 -- 원래는 이동 굴림(rollDiceInternal)에서만 소모돼서,
        // 특수칸을 밟은 직후 다음 행동이 마침 "전투 공격"이면 그 턴엔 아무 효과가 없다가
        // 나중에 엉뚱한 이동 굴림에서 소모되는 게 문제였다. 이제 전투 공격 턴에서도
        // 똑같이 "다음 행동 1회"로 소모되게 해서, 특수칸 이후 정말로 맨 처음 굴리는
        // 주사위(이동이든 전투 공격이든)에 적용된다. 파티원 전원이 이번 턴에 함께 덕을
        // 본다(한 턴 = 한 번의 더블 적용이라는 취지 유지, 인원수만큼 소모되는 게 아님).
        boolean doubleDice = "Y".equals(strVal(p.get("DOUBLE_DICE_YN"), "N"));
        if (doubleDice) {
            HashMap<String, Object> clearDoubleDiceUp = new HashMap<>();
            clearDoubleDiceUp.put("userName", userName);
            clearDoubleDiceUp.put("doubleDiceYn", "N");
            dao.updateUserProgress(clearDoubleDiceUp);
            p.put("DOUBLE_DICE_YN", "N");
            sb.append("🎲🎲 더블주사위 효과! 이번 턴 파티 전원의 공격 눈금을 두 번씩 굴려 합산합니다.").append(NL);
        }

        // [2026-09-13] "I번과 II번이 같이 있어도 우리는 I번부터 우선타격, 오버킬 없이 동료별로
        // 나눠서 데미지 적용" 요청 -- dualMonster면 아래 루프에서 동료 한 명이 공격할 때마다
        // "현재 타겟"(처음엔 I번)의 체력을 그 자리에서 즉시 깎는다(파티 전체 데미지를 한 번에
        // 모아 한 번에 빼는 기존 방식 대신). 그 공격으로 현재 타겟이 죽으면 남은 피해는
        // 버리고(오버킬 없음) 다음 동료부터 II번을 타격한다. 예: I번 체력 400, 동료1이
        // 300dmg(400→100, 생존)/동료2가 600dmg(오버킬 500 버리고 I번 사망, 이후 동료는
        // II번 타격)/동료3이 II번에게 자기 dmg 그대로(예: 400) 적용.
        long dualTarget1Remain = PP.toBaseValue(monsterHp);
        Long dualTarget2Remain = null;
        if (dualMonster) {
            Object m2 = p.get("CUR_MONSTER2_HP_VALUE");
            if (m2 != null && ((Number) m2).doubleValue() > 0) {
                dualTarget2Remain = PP.toBaseValue(PP.of(((Number) m2).doubleValue(), strVal(mon.get("HP_EXT"), "")));
            }
        }
        boolean dualOnTarget2 = false;
        boolean dualMonster1KilledInLoop = false;
        boolean dualMonster2KilledInLoop = false;

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
            int[] eff = computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0));
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

            // [2026-09-16] "즉사 옵션을 없애고 크리티컬로 바꿔달라" 요청 -- 잔여체력 10%↓시
            // 40% 확률 즉사 대신, 매 공격마다 성급별 확률로 1.5배 크리티컬이 터지도록 변경
            // (1~4성 30%, 5성 40%, 6성 50% -- 배율은 성급 무관 항상 1.5배로 고정).
            boolean archerCrit = false;
            if ("ARCHER".equals(job)) {
                int critChance = grade >= 6 ? 50 : (grade >= 5 ? 40 : 30);
                archerCrit = RND.nextInt(100) < critChance;
            }

            int roll;
            String rollLabel;
            if (doubleDice) {
                int atkRoll1 = rollFace(diceMinFor(p), diceMax);
                int atkRoll2 = rollFace(diceMinFor(p), diceMax);
                roll = atkRoll1 + atkRoll2;
                rollLabel = atkRoll1 + "+" + atkRoll2;
            } else {
                roll = rollFace(diceMinFor(p), diceMax);
                rollLabel = String.valueOf(roll);
            }
            // [2026-09-18] ★7 전설무기 효과(데이터 기반, TBOT_S5_LEGENDARY_MASTER 참고) --
            // 현재 구현된 효과는 DEF_STEAL(예시: 송곳)뿐. WEAPON 슬롯에 전설장비가 있으면
            // 기본 데미지식 자체를 바꿔치기한다(사후 가산이 아님 -- effMonsterDef가 ATK*roll보다
            // 커서 원래 식이 1로 바닥 클램프되는 경우 사후 가산은 부정확해짐).
            HashMap<String, Object> legWeapon = null;
            for (HashMap<String, Object> e : equips) {
                if (!"WEAPON".equals(strVal(e.get("PART"), ""))) continue;
                Object legId = e.get("LEGENDARY_ID");
                if (legId != null) legWeapon = dao.selectLegendaryMaster(intVal(legId, 0));
                break; // WEAPON 슬롯은 1개뿐
            }
            int dmg;
            String legendaryWeaponTag = null;
            // [2026-09-21 재설계] "송곳: 방어력을 무시하고, 방어력만큼 내데미지에 더한다" --
            // 처음엔 "훔친 만큼만 가산"(부분 관통)이었는데, 사용자가 "무시 + 그만큼 가산"으로
            // 명확히 정정 -- effMonsterDef를 아예 빼지 않고(무시) PARAM1%만큼 그대로 더한다.
            // PARAM1=100이면 dmg = ATK*roll + DEF(방어력이 페널티가 아니라 순수 보너스가 됨).
            if (legWeapon != null && "DEF_STEAL".equals(strVal(legWeapon.get("EFFECT_TYPE"), ""))) {
                int bonus = (int) Math.round(effMonsterDef * (intVal(legWeapon.get("EFFECT_PARAM1"), 0) / 100.0));
                dmg = Math.max(1, eff[1] * roll) + bonus;
                legendaryWeaponTag = strVal(legWeapon.get("ITEM_NAME"), "") + "+" + bonus;
            } else {
                dmg = Math.max(1, eff[1] * roll - effMonsterDef);
            }
            dmg = Math.max(dmg, eff[3]); // 스탯구매 최소공격력 보정
            if (archerCrit) dmg = (int) Math.round(dmg * 1.5); // 궁수 크리티컬: 최종 데미지 1.5배
            // [2026-09-17] "도사는 서포터로 만들자, 현행 데미지의 6분의1수준으로 낮춰서 딜은
            // 그대로 들어가도록(완전히 0은 아님), 실드는 변경없음" 요청 -- 실드는 이 dmg와
            // 완전히 별개의 두 번째 주사위 굴림(shieldRoll, 아래 PRIEST switch case)으로
            // 계산되므로 이 줄과 무관하게 그대로 유지된다.
            if ("PRIEST".equals(job)) dmg = Math.max(1, (int) Math.round(dmg / 6.0));
            totalDamage += dmg;
            // [간결화] 텍스트가 너무 길다는 요청으로, 공격력/범위(전투 시작 전 "OO 등장!" 메시지에
            // 이미 표시됨)는 매 줄마다 반복하지 않고, 직업별 특수효과도 새 줄 대신 같은 줄 끝에
            // 붙여서 파티원 1명당 항상 딱 1줄만 쓰도록 함.
            // [세 구간 분리 요청] 공격 줄에도 현재/최대 HP를 같이 보여줘서, 나중에 "이번턴 남은"
            // 구간의 HP와 바로 비교되게 함.
            // [2026-09-05 멘트 개편] "이름+HP"와 "주사위/데미지"를 한 줄에 몰아넣지 말고 줄을
            // 나눠달라는 요청 -- 이름+HP 줄, 그 아래 굴림 결과 줄로 분리.
            sb.append(jobTag(grade, job, cName)).append(" 💗").append(hp.format()).append("/").append(eff[0]).append(NL)
              .append("🎲").append(rollLabel).append("→").append(dmg).append("dmg");
            if (archerCrit) sb.append(" 💥크리티컬!");
            if (legendaryWeaponTag != null) sb.append(" 🗡️").append(legendaryWeaponTag).append("(방어력 무시+가산)");

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
                case "PRIEST": {
                    // [명확화 요청] "실드를 누구한테 주는지 안 보인다"는 지적으로, 도사 자신의
                    // 줄에는 더 이상 🛡️+N을 안 찍는다 -- 실제로 이번 반격을 막아준 대상이
                    // 정해진 뒤(아래 resolveCombatTurn의 반격 파트) 그 동료 자신의 줄에 붙여준다.
                    int shieldRoll = doubleDice ? rollFace(diceMinFor(p), diceMax) + rollFace(diceMinFor(p), diceMax) : rollFace(diceMinFor(p), diceMax);
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

            // [2026-09-13] dual 몬스터: 이 동료의 공격을 "현재 타겟"에 그 자리에서 적용.
            if (dualMonster) {
                if (!dualOnTarget2) {
                    sb.append(" (I번)");
                    dualTarget1Remain -= dmg;
                    if (dualTarget1Remain <= 0) {
                        dualMonster1KilledInLoop = true;
                        if (dualTarget2Remain != null) dualOnTarget2 = true;
                    }
                } else {
                    sb.append(" (II번)");
                    dualTarget2Remain -= dmg;
                    if (dualTarget2Remain <= 0) dualMonster2KilledInLoop = true;
                }
            }
            sb.append(NL);
        }
        // "파티 합공 총 데미지도 보여달라" 요청 -- 개별 줄만으로는 한 번에 얼마나 몰아쳤는지
        // 암산해야 해서, 공격 줄들 바로 아래에 합계를 한 줄 더 보여준다.
        if (totalDamage > 0) sb.append("총 ").append(totalDamage).append("dmg로 공격!").append(NL);

        // [2026-09-13] "79층 보스는 첫타 은신으로 회피 후 동료 한 명을 처치하고 시작, 이후
        // 6턴마다 반복" 요청 -- 1턴째와 6의 배수 턴엔 이번 턴 파티 공격 전체가 회피되어(피해
        // 0) 무효화된다. 실제 "동료 처치"는 아래 반격 파트(alive 목록이 준비된 뒤)에서
        // 처리한다.
        // [2026-09-14] "89층보스는 위 능력을 모두 포함"(79층 은신처치+하수인 포함) 요청으로
        // floor==79 전용이던 조건을 89도 함께 타도록 확장(변수명은 기존 코드 흐름을 최소한만
        // 건드리려 그대로 둠).
        boolean isBoss79 = (floor == 79 || floor == 89) && "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        boolean boss79Ambush = isBoss79 && (curCombatTurn == 1 || curCombatTurn % 6 == 0);
        if (boss79Ambush) {
            totalDamage = 0;
            sb.append("🌑 보스가 은신 상태로 이번 턴 파티의 공격을 전부 회피했다!").append(NL);
        }

        // [2026-09-18] "도적스킬 훔칠 때 PP 대신 회피를 훔치게 해달라" 요청 -- 지난 턴에
        // 미드보스/층구간보스가 도적 스킬을 훔쳤으면(아래 스킬도용 파트에서 MONSTER_EVADE_PCT를
        // 세팅) 이번 파티 공격 전체를 그 확률로 회피(79층 은신 회피와 동일한 "전체 무효화"
        // 방식, 확률만 다름). 79층 은신으로 이미 totalDamage=0이어도 중복 계산은 무해.
        int monsterEvadePct = intVal(p.get("MONSTER_EVADE_PCT"), 0);
        if (monsterEvadePct > 0 && totalDamage > 0 && RND.nextInt(100) < monsterEvadePct) {
            totalDamage = 0;
            sb.append("🌀 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 몸놀림을 흉내내 이번 턴 파티의 공격을 전부 회피했다!").append(NL);
        }

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

        // [2026-09-13] "I번을 우선타격, 오버킬 없이 동료별로 나눠서 데미지 적용" 요청으로
        // 09-12의 "총 데미지를 한 번에 모아 빼고 오버킬을 이월"하는 방식을 대체했다. 위 파티
        // 공격 루프 안에서 이미 동료 한 명씩 "현재 타겟"의 체력을 깎아뒀으므로(dualTarget1
        // Remain/dualTarget2Remain/dualMonster1KilledInLoop/dualMonster2KilledInLoop 참고),
        // 여기서는 그 결과를 최종 monsterHpAfter/monsterDead로 정리하기만 한다.
        PP monsterHpAfter;
        boolean monsterDead;
        if (dualMonster) {
            if (!dualMonster1KilledInLoop) {
                monsterHpAfter = PP.fromPP(Math.max(0, dualTarget1Remain));
                monsterDead = false;
            } else if (dualTarget2Remain == null) {
                // I번은 죽었는데 II번이 애초에 없었던 경우 -- 정상 케이스라면 이미
                // CUR_MONSTER_DUAL_YN='N'이어야 하므로 방어적 처리일 뿐.
                monsterHpAfter = PP.fromPP(0);
                monsterDead = true;
            } else if (dualMonster2KilledInLoop) {
                monsterHpAfter = PP.fromPP(Math.max(0, dualTarget2Remain));
                monsterDead = true;
                sb.append(NL).append("💀 ").append(eliteMonsterName(floor, mon, elite, 0)).append(" 처치! II번도 이어서 쓰러뜨렸다!").append(NL);
            } else {
                monsterHpAfter = PP.fromPP(dualTarget2Remain);
                monsterDead = false;
                sb.append(NL).append("💀 ").append(eliteMonsterName(floor, mon, elite, 0)).append(" 처치! II번 몬스터가 이어서 나타난다.").append(NL);
                HashMap<String, Object> promoteUp = new HashMap<>();
                promoteUp.put("userName", userName);
                promoteUp.put("clearMonster2", true); // II번은 이제 활성화됐으니 "대기 중" 슬롯 비움
                // [2026-09-18] CUR_MONSTER_DUAL_YN은 일부러 'Y'로 그대로 둔다 -- "I번 처치 후에도
                // 두 마리 다 반격한다" 버그를 처음엔 여기서 'N'으로 꺼서 고쳤었는데, 그러면
                // dualHpMult(처치보상 2배)도 함께 꺼져서 I번+II번을 따로따로 죽인 원정에서
                // PP가 1마리분만 지급되는 회귀 버그가 생겼다("2마리인데 1마리치만 들어온다"
                // 신고로 발견). 보상 배율은 그대로 두고, 반격 문제는 대신 아래 targets 선정에서
                // CUR_MONSTER2_HP_VALUE(실제 II번 생존 여부)를 직접 확인하는 쪽으로 옮겨 고쳤다.
                dao.updateUserProgress(promoteUp);
                p.put("CUR_MONSTER2_HP_VALUE", null);
            }
        } else {
            monsterHpAfter = monsterHp.subtract(PP.fromPP(totalDamage));
            monsterDead = PP.toBaseValue(monsterHpAfter) <= 0;
        }

        // [2026-09-14] "99층 보스는 즉사능력은 없으나 세 명을 동시공격하고, 죽이면 200%의
        // 체력으로 한 번 부활하도록(다른 능력은 제거)" 요청 -- HP가 0이 된 게 이번 전투에서
        // 아직 한 번도 부활을 안 쓴 첫 사망이면, 처치 처리(보상/층이동)로 이어지는 대신
        // 최대체력의 200%로 그 자리에서 되살아나고 전투가 계속된다. 두 번째로 0이 되면
        // (CUR_MONSTER_REVIVED_YN이 이미 'Y') 그때는 평소처럼 진짜 처치로 처리된다.
        if (monsterDead && floor == 99 && "Y".equals(strVal(mon.get("BOSS_YN"), "N"))
                && !"Y".equals(strVal(p.get("CUR_MONSTER_REVIVED_YN"), "N"))) {
            monsterDead = false;
            monsterHpAfter = PP.of(((Number) mon.get("HP_VALUE")).doubleValue() * 2.0, strVal(mon.get("HP_EXT"), "")).normalize();
            HashMap<String, Object> reviveUp = new HashMap<>();
            reviveUp.put("userName", userName);
            reviveUp.put("curMonsterRevivedYn", "Y");
            dao.updateUserProgress(reviveUp);
            sb.append(NL).append("💥 ").append(eliteMonsterName(floor, mon, elite))
              .append("이(가) 쓰러졌지만 곧바로 200% 체력으로 부활한다!").append(NL);
        }

        // [2026-09-18] "보스는 하루 3번만 처치할수있도록" 요청 -- 모든 보스층(9층부터) 공통
        // 카운터. 99층 부활 로직과 같은 위치(몬스터가 죽는 시점)에서 가로채되, HP를 되돌리지
        // 않고 딱 1로만 묶어둔다("이번엔 못 죽임"). 관리자 테스트 계정은 쿨타임/일일한도와
        // 동일한 이유로 면제.
        if (monsterDead && "Y".equals(strVal(mon.get("BOSS_YN"), "N"))
                && !"Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))
                && bossKillCountToday(p) >= BOSS_DAILY_KILL_LIMIT) {
            monsterDead = false;
            monsterHpAfter = PP.fromPP(1);
            sb.append(NL).append("⏳ 오늘 보스 처치 횟수(").append(BOSS_DAILY_KILL_LIMIT).append("/").append(BOSS_DAILY_KILL_LIMIT)
              .append(")를 모두 사용했습니다! 내일 다시 도전해주세요.").append(NL);
        }

        if (monsterDead) {
            // [2026-09-09] "두 마리"라 실제로 2마리분 처치 보상을 준다(dualHpMult가 그대로 배율).
            PP reward = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(floorPpMultiplier(floor) * eliteMult * dualHpMult);
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
            // [2026-09-10] 69층 보스에게 죽어 하수인이 됐던 동료들 -- 보스가 죽었으니
            // "사라진다"(clearMonster가 CUR_BOSS_MINION_CIDS를 자동 초기화). 있었을 때만 안내.
            // [2026-09-14] 89층도 같은 하수인 인프라를 쓰므로 함께 안내.
            if ((floor == 69 || floor == 89) && !parseMinionCids(strVal(p.get("CUR_BOSS_MINION_CIDS"), "")).isEmpty()) {
                sb.append("✨ 보스의 힘에 사로잡혔던 동료들이 원래대로 돌아왔다(전투불가 상태는 여전히 마을에서 부활 필요).").append(NL);
            }

            // [2026-09-10] "중간보스 처치보상으로 중급동료뽑기 or 중급장비뽑기를 확률적으로
            // 지급해달라" 요청 -- 중간보스(51층+ COMBAT 칸 20% 확률 조우, 3배 스탯) 처치 시
            // 40% 확률로 중급(티어2) 동료뽑기권 또는 장비뽑기권 중 하나를 1장 지급(50/50).
            // PP/경험치는 이미 eliteMult(3배)로 반영되고 있어 이건 별개의 "깜짝 보상".
            if (midBoss) {
                if (RND.nextInt(100) < 40) {
                    boolean companionTicket = RND.nextBoolean();
                    HashMap<String, Object> ticketUp = new HashMap<>();
                    ticketUp.put("userName", userName);
                    if (companionTicket) {
                        int newCnt = intVal(p.get("COMPANION_VOUCHER_T2"), 0) + 1;
                        ticketUp.put("companionVoucherT2", newCnt);
                        p.put("COMPANION_VOUCHER_T2", newCnt);
                        sb.append("🎁 중간보스가 중급 동료뽑기권 1장을 떨어뜨렸다!").append(NL);
                    } else {
                        int newCnt = intVal(p.get("EQUIP_VOUCHER_T2"), 0) + 1;
                        ticketUp.put("equipVoucherT2", newCnt);
                        p.put("EQUIP_VOUCHER_T2", newCnt);
                        sb.append("🎁 중간보스가 중급 장비뽑기권 1장을 떨어뜨렸다!").append(NL);
                    }
                    dao.updateUserProgress(ticketUp);
                }
            }
            // [2026-09-16] 81층+ 계단 게이트가 "중간보스 처치"에서 "탐사율 50%↑"로 대체되면서
            // (STAIRS_UP 케이스 참고) 이 마킹은 더 이상 어디서도 읽지 않아 제거. MIDBOSS_KILLED_YN
            // 컬럼/markFloorMidbossKilled DAO 메서드 자체는 남겨두되(과거 데이터, DDL 롤백 부담)
            // 새로 값을 쓰는 곳은 없음.

            if (isBoss) {
                bumpBossKillCountToday(userName, p);

                // [2026-09-18] "50층 이상의 보스층에서 보스처치시 확률적으로 전설의조각 획득"
                // 요청 -- 59/69/79/89/99층 순서대로 5%→25%(균등 증가), 성공 시 1개.
                for (int i = 0; i < LEGEND_FRAGMENT_BOSS_FLOOR.length; i++) {
                    if (floor != LEGEND_FRAGMENT_BOSS_FLOOR[i]) continue;
                    if (RND.nextInt(100) < LEGEND_FRAGMENT_DROP_PCT[i]) {
                        int newFragment = intVal(p.get("LEGEND_FRAGMENT"), 0) + 1;
                        HashMap<String, Object> fragUp = new HashMap<>();
                        fragUp.put("userName", userName);
                        fragUp.put("legendFragment", newFragment);
                        dao.updateUserProgress(fragUp);
                        p.put("LEGEND_FRAGMENT", newFragment);
                        sb.append("🧩 전설의조각 획득! (보유 ").append(newFragment).append("개)").append(NL);
                    }
                    break;
                }

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
                // [2026-09-16] "0층 마을 도착 시 편성된 동료만 부활하는데, 편성 안 된 동료도
                // 전부 부활시켜달라" 요청 -- 전투에 참여한 party(PARTY_SLOT만)가 아니라 보유한
                // 전체 동료 목록을 넘긴다(마을 도착 부활의 다른 3곳과 동일하게 맞춤).
                int revivedOnBossClear = revivePartyDead(userName, dao.selectUserCompanions(userName), userStat);
                if (revivedOnBossClear > 0) {
                    sb.append("✨ 전투불가 상태였던 동료 ").append(revivedOnBossClear).append("명이 마을에서 부활했습니다!").append(NL);
                }
            } else {
                // [버그 수정] 자동사냥이 이미 켜져 있을 때도 KILL_COUNT_CUR가 계속 0~9로
                // 순환(리셋)해서 "이 층 처치 N/10" 수치가 계속 오르내리는 것처럼 보이고,
                // 10마리째마다 "자동사냥 모드 ON" 안내가 쓸데없이 반복 출력되는 문제가
                // 있었다(문의로 확인). 이미 켜진 뒤에는 이 카운터를 더 건드리지 않는다 --
                // "이 층 처치 N/10"은 순수하게 "아직 자동사냥이 꺼져 있고, 이 층에서 처음
                // 켜기까지 몇 마리 남았는지"만 의미.
                // [2026-09-10] "자동사냥이 실제 사냥터가 아니라 그냥 멈춰있는 층 기준으로
                // 도는 것 같다, 최근 10마리를 잡은 곳을 기준으로 해달라" 요청 -- 예전엔
                // changeFloor()에서 "자동사냥 ON이면 층 이동할 때마다 그 층으로 정산기준을
                // 맞춘다"는 규칙이었는데, 이러면 실제로 싸운 적 없는 층(그냥 지나가거나
                // 도망친 층)도 정산 기준이 돼버리는 문제가 있었다. changeFloor의 동기화는
                // 제거하고, 대신 "실제로 몬스터를 잡을 때마다" 그 층으로 갱신하도록 옮겼다 --
                // 이러면 정산 기준이 항상 "가장 최근에 실제로 전투해 이긴 층"이 된다.
                boolean alreadyAutoHunt = "Y".equals(strVal(p.get("AUTO_HUNT_YN"), "N"));
                if (alreadyAutoHunt) {
                    HashMap<String, Object> huntFloorUp = new HashMap<>();
                    huntFloorUp.put("userName", userName);
                    huntFloorUp.put("floor", floor);
                    dao.upsertAutoHuntLog(huntFloorUp);
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
        up.put("monsterEvadePct", 0);
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

        // [2026-09-13] 79층 보스 은신 처치 -- 정상 반격(도발/보호막/도적 회피 등) 대신, 파티
        // 중 무작위 생존자 1명을 그 자리에서 처치한다. ★5/★6 도사 부활은 일반 반격 사망과
        // 동일 확률로 동작(전멸 방지 여지를 남김). 이 턴은 이 처치 하나로 끝나고 정상
        // 반격은 하지 않는다.
        if (boss79Ambush) {
            HashMap<String, Object> victim = alive.get(RND.nextInt(alive.size()));
            String vJob = strVal(victim.get("CLASS"), "WARRIOR");
            int vGrade = intVal(victim.get("GRADE"), 1);
            String vName = strVal(victim.get("NAME"), JOB_NAME.getOrDefault(vJob, "동료"));
            sb.append("😈 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 은신에서 나타나 ")
              .append(jobTag(vGrade, vJob, vName)).append("을(를) 급습했다!").append(NL);

            PP vHpAfter = PP.fromPP(0);
            HashMap<String, Object> reviver79 = null;
            for (HashMap<String, Object> c : party) {
                PP rHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                if ("PRIEST".equals(strVal(c.get("CLASS"), "")) && intVal(c.get("GRADE"), 1) >= 5 && PP.toBaseValue(rHp) > 0) {
                    reviver79 = c;
                    break;
                }
            }
            if (reviver79 != null) {
                int reviverGrade = intVal(reviver79.get("GRADE"), 1);
                int reviveChance = reviverGrade >= 6 ? 15 : 10; // [2026-09-17] 밸런스 조정: 기존 40/25 -> 15/10
                double revivePct = reviverGrade >= 6 ? 0.5 : 0.3;
                if (RND.nextInt(100) < reviveChance) {
                    List<HashMap<String, Object>> vEquips = dao.selectEquipByCompanion(intVal(victim.get("COMPANION_ID"), 0));
                    int[] vEff = computeEffectiveStat(vJob, vGrade, vEquips, userStat, intVal(victim.get("LIMIT_BREAK"), 0));
                    vHpAfter = PP.fromPP(Math.max(1, (int) Math.round(vEff[0] * revivePct)));
                    sb.append("✨ 도사의 기적! ").append(jobTag(vGrade, vJob, vName))
                      .append(" 부활(HP ").append(vHpAfter.format()).append("/").append(vEff[0]).append(")").append(NL);
                }
            }
            // [2026-09-16] 럭키칸 "피해 1회 면역"(구 즉사방어) 관련 방어 체크를 여기 뒀었는데,
            // 보스전 진입 시(changeFloor) 럭키 효과가 이미 초기화되어 79층 같은 보스 구간에서는
            // WARD_COMPANION_ID가 항상 0 -- 즉 이 분기는 절대 발동할 수 없는 죽은 코드였다.
            // "보스구간엔 럭키옵션이 없이 싸우니 문제없다"는 확인에 따라 제거.
            if (PP.toBaseValue(vHpAfter) <= 0) {
                sb.append("💀 ").append(jobTag(vGrade, vJob, vName)).append("이(가) 쓰러졌다!").append(NL);
            }
            writeCompanionHp(victim, vHpAfter);

            // [2026-09-14] "79층 1턴즉사 이후 다음턴에 부활하지 못한 동료를 하수인으로
            // 생성해서 6턴마다 은신-즉사 하도록" 요청 -- 69층 하수인과 같은 CUR_BOSS_
            // MINION_CIDS 명단을 재사용하되, 이 명단은 "매 턴 공격"이 아니라 아래처럼 이
            // 앰부시 턴(6턴마다)에만 각자 무작위 파티원 1명을 은신 즉사시킨다. 방금 등록된
            // 동료는 이번 턴엔 아직 안 움직이고 다음 앰부시 턴부터 참여(69층과 동일 원칙,
            // 한 턴에 연쇄적으로 불어나는 것 방지).
            int diedCidThisTurn = -1;
            if (PP.toBaseValue(vHpAfter) <= 0) {
                List<Integer> minionIds79 = parseMinionCids(strVal(p.get("CUR_BOSS_MINION_CIDS"), ""));
                diedCidThisTurn = intVal(victim.get("COMPANION_ID"), 0);
                if (diedCidThisTurn > 0 && !minionIds79.contains(diedCidThisTurn)) {
                    minionIds79.add(diedCidThisTurn);
                    String joinedCids79 = joinMinionCids(minionIds79);
                    HashMap<String, Object> minionUp79 = new HashMap<>();
                    minionUp79.put("userName", userName);
                    minionUp79.put("curBossMinionCids", joinedCids79);
                    dao.updateUserProgress(minionUp79);
                    p.put("CUR_BOSS_MINION_CIDS", joinedCids79);
                    sb.append("💀🥀 ").append(jobTag(vGrade, vJob, vName)).append("이(가) 쓰러져 보스의 하수인이 되었다! (다음 은신 즉사 때 함께 나타난다)").append(NL);
                }
            }

            // 기존(이전 앰부시 턴까지 등록된) 하수인들이 이번 앰부시 턴에 함께 은신 즉사를
            // 시도한다 -- 방금 죽은 동료는 diedCidThisTurn으로 걸러 이번 턴엔 제외.
            List<Integer> curMinionIds79 = parseMinionCids(strVal(p.get("CUR_BOSS_MINION_CIDS"), ""));
            if (!curMinionIds79.isEmpty()) {
                List<HashMap<String, Object>> stillAlive79 = new ArrayList<>();
                for (HashMap<String, Object> c : party) {
                    PP hp79 = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                    if (PP.toBaseValue(hp79) > 0) stillAlive79.add(c);
                }
                HashMap<Integer, HashMap<String, Object>> partyById79 = new HashMap<>();
                for (HashMap<String, Object> c : party) partyById79.put(intVal(c.get("COMPANION_ID"), -1), c);
                for (Integer minionCid : curMinionIds79) {
                    if (minionCid == diedCidThisTurn) continue; // 방금 등록된 하수인은 다음 턴부터
                    if (stillAlive79.isEmpty()) break;
                    HashMap<String, Object> minionC79 = partyById79.get(minionCid);
                    if (minionC79 == null) continue;
                    String mJob79 = strVal(minionC79.get("CLASS"), "WARRIOR");
                    int mGrade79 = intVal(minionC79.get("GRADE"), 1);
                    String mName79 = strVal(minionC79.get("NAME"), JOB_NAME.getOrDefault(mJob79, "동료"));

                    HashMap<String, Object> instaVictim = stillAlive79.get(RND.nextInt(stillAlive79.size()));
                    String ivJob = strVal(instaVictim.get("CLASS"), "WARRIOR");
                    int ivGrade = intVal(instaVictim.get("GRADE"), 1);
                    String ivName = strVal(instaVictim.get("NAME"), JOB_NAME.getOrDefault(ivJob, "동료"));
                    sb.append(NL).append("👹🌑 하수인이 된 ").append(jobTag(mGrade79, mJob79, mName79))
                      .append("이(가) 은신에서 나타나 ").append(jobTag(ivGrade, ivJob, ivName)).append("을(를) 급습했다!").append(NL);

                    PP ivHpAfter = PP.fromPP(0);
                    HashMap<String, Object> reviverIv = null;
                    for (HashMap<String, Object> c : party) {
                        PP rHp2 = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                        if ("PRIEST".equals(strVal(c.get("CLASS"), "")) && intVal(c.get("GRADE"), 1) >= 5 && PP.toBaseValue(rHp2) > 0) {
                            reviverIv = c;
                            break;
                        }
                    }
                    if (reviverIv != null) {
                        int reviverGrade2 = intVal(reviverIv.get("GRADE"), 1);
                        int reviveChance2 = reviverGrade2 >= 6 ? 15 : 10; // [2026-09-17] 밸런스 조정: 기존 40/25 -> 15/10
                        double revivePct2 = reviverGrade2 >= 6 ? 0.5 : 0.3;
                        if (RND.nextInt(100) < reviveChance2) {
                            List<HashMap<String, Object>> ivEquips = dao.selectEquipByCompanion(intVal(instaVictim.get("COMPANION_ID"), 0));
                            int[] ivEff = computeEffectiveStat(ivJob, ivGrade, ivEquips, userStat, intVal(instaVictim.get("LIMIT_BREAK"), 0));
                            ivHpAfter = PP.fromPP(Math.max(1, (int) Math.round(ivEff[0] * revivePct2)));
                            sb.append("✨ 도사의 기적! ").append(jobTag(ivGrade, ivJob, ivName))
                              .append(" 부활(HP ").append(ivHpAfter.format()).append("/").append(ivEff[0]).append(")").append(NL);
                        }
                    }
                    // [2026-09-16] 이 경로(79/89층 보스 하수인 은신즉사)도 보스전 진입 시 럭키
                    // 효과가 이미 초기화되어 방어가 걸려 있을 수 없는 죽은 코드라 제거.
                    if (PP.toBaseValue(ivHpAfter) <= 0) {
                        sb.append("💀 ").append(jobTag(ivGrade, ivJob, ivName)).append("이(가) 쓰러졌다!").append(NL);
                        stillAlive79.remove(instaVictim);
                    }
                    writeCompanionHp(instaVictim, ivHpAfter);
                }
            }

            sb.append(NL).append(partyHpSummary(party, userStat));
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
            int[] wEff = computeEffectiveStat("WARRIOR", wGrade, wEquips, userStat, intVal(c.get("LIMIT_BREAK"), 0));
            PP wHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean over50 = PP.toBaseValue(wHp) * 2 >= wEff[0];
            int guardChance = 30;
            if (warriorSynergy) guardChance += 20;      // 시너지: 전사3인조
            if (wGrade >= 6) guardChance += 20;          // ★6
            else if (wGrade >= 5) guardChance += 10;     // ★5
            if (over50 && !c.equals(target) && RND.nextInt(100) < guardChance) {
                target = c;
                guarded = true;
                warriorGuardMitigationPct = wGrade >= 5 ? 20 : 0; // [2026-09-17] ★5/★6: 도발 성공 시 받는 피해 추가 20%↓(기존 ★6 전용에서 ★5도 포함)
            }
            if (!warriorSynergy) break; // 시너지 아니면 예전처럼 첫 전사만 판정
        }

        // [2026-09-06 신설] 51층 이후(블록6+) 중간보스 -- 맵/등장 메시지는 평범한 몬스터와
        // 똑같이 보이지만(startCombat 참고), 매 턴 지금 파티에 있는 직업 중 하나의 "기본 스킬"을
        // 하나 훔쳐서 자신이 사용한다(여러 직업이 섞여 있으면 매 턴 그 중 하나를 무작위로).
        // 전사는 도발(타겟팅) 자체가 자신에게 의미가 없으니 대신 방어력을 올리고, 도사는
        // 스스로에게 보호막을, 도적은 [2026-09-18] PP 대신 다음 파티 공격 회피 확률을 훔치고
        // (MONSTER_EVADE_PCT), 궁수는(크리티컬은 제외) 이번 반격 피해를
        // 늘리고, 마법사는 동료 한 명을 기절시킨다.
        // [2026-09-14 수정] "스킬 뺏을 때 반격도 같이해줘, 지금은 스킬뺏는 액션만 해서 너무
        // 약하다" 요청 -- 원래 마법사 기절만 "반격 턴을 통째로 소모"(return으로 아래 반격
        // 코드를 건너뜀)하는 유일한 예외였다(WARRIOR/PRIEST/ROGUE/ARCHER는 원래도 메시지만
        // 남기고 아래 반격 코드로 자연스럽게 이어졌음). 이제 마법사 기절도 return을 없애고
        // 그대로 아래 반격 코드로 흘러가게 해서, 기절시킨 그 대상(target -- 아래 targets
        // 리스트도 같은 target을 씀)이 이번 턴 반격 피해까지 같이 받는다(다음 턴 공격불가는
        // 그대로 유지, "이번 턴 피해 없음"만 없앤 것).
        boolean midBossArcherDmgUp = false;
        if (midBoss) {
            List<String> stealable = new ArrayList<>();
            for (HashMap<String, Object> c : alive) {
                String j = strVal(c.get("CLASS"), "");
                if (JOB_NAME.containsKey(j) && !stealable.contains(j)) stealable.add(j);
            }
            if (!stealable.isEmpty()) {
                String stolenJob = stealable.get(RND.nextInt(stealable.size()));
                // [2026-09-09] "스킬 뺏어쓰는 것도 계수를 50%로 해달라" 요청 -- WARRIOR/PRIEST/
                // ROGUE/ARCHER는 수치 계수를 그대로 절반으로(30%->15%, x2->x1, 5%->2.5%,
                // x1.3->x1.15). MAGE 기절만 수치가 아니라 "훔치면 무조건 발동"하는 이진 효과라
                // 절반화할 수치가 없어서, 대신 발동 확률 자체를 50%로 둬서 같은 취지를 맞춤
                // (실패하면 아래 평범한 반격으로 자연스럽게 이어짐).
                if ("MAGE".equals(stolenJob)) {
                    if (RND.nextInt(100) < 50) {
                        // [2026-09-14][정정] "기절시킨 동료를 공격하지 말고, 기절 대상/공격
                        // 대상 각각 룰렛 돌려달라" 요청 -- 기존엔 기절 대상이 곧 이번 턴 공격
                        // 대상(target, 위 전사 도발 판정까지 거친 값)과 항상 같았다. 이제 기절
                        // 대상은 alive 중 공격 대상(target)을 제외하고 별도로 무작위 추첨해서,
                        // 같은 동료가 한 턴에 기절+피격을 동시에 겪지 않게 한다(생존자가
                        // target 한 명뿐이면 어쩔 수 없이 같은 사람).
                        List<HashMap<String, Object>> stunPool = new ArrayList<>(alive);
                        stunPool.remove(target);
                        HashMap<String, Object> stunTarget = stunPool.isEmpty() ? target : stunPool.get(RND.nextInt(stunPool.size()));
                        HashMap<String, Object> stealStunUp = new HashMap<>();
                        stealStunUp.put("userName", userName);
                        stealStunUp.put("bossStunCid", intVal(stunTarget.get("COMPANION_ID"), 0));
                        dao.updateUserProgress(stealStunUp);
                        String stealStunName = strVal(stunTarget.get("NAME"), JOB_NAME.getOrDefault(strVal(stunTarget.get("CLASS"), ""), "동료"));
                        sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 마법사의 기술을 흉내내 ")
                          .append(stealStunName).append(" 기절! 다음턴 공격불가 (반격은 다른 동료에게)").append(NL);
                        // [2026-09-14] 더 이상 여기서 return하지 않고 아래 평범한 반격으로 이어짐.
                    }
                    // 확률 실패 -- 도용 자체가 안 통한 것으로 보고 아래 평범한 반격으로 계속 진행.
                } else if ("WARRIOR".equals(stolenJob)) {
                    HashMap<String, Object> stealDefUp = new HashMap<>();
                    stealDefUp.put("userName", userName);
                    stealDefUp.put("monsterDefBuffPct", 15);
                    dao.updateUserProgress(stealDefUp);
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 전사의 기술을 흉내내 방어 태세를 갖췄다! (다음 파티 공격 시 방어력 +15%)").append(NL);
                } else if ("PRIEST".equals(stolenJob)) {
                    int stealShieldAmt = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult);
                    HashMap<String, Object> stealShUp = new HashMap<>();
                    stealShUp.put("userName", userName);
                    stealShUp.put("monsterShieldValue", stealShieldAmt);
                    dao.updateUserProgress(stealShUp);
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도사의 기술을 흉내내 스스로에게 보호막(").append(stealShieldAmt).append(")을 둘렀다!").append(NL);
                } else if ("ROGUE".equals(stolenJob)) {
                    // [2026-09-18] "도적스킬 뺏을때 PP뺏는걸 없애고 회피하는걸 뺏어줘" 요청 --
                    // PP 드레인 대신 다음 파티 공격을 15%(미드보스, 층구간보스는 25%) 확률로
                    // 통째로 회피하는 효과로 교체(위 monsterEvadePct 체크에서 소모).
                    HashMap<String, Object> stealEvadeUp = new HashMap<>();
                    stealEvadeUp.put("userName", userName);
                    stealEvadeUp.put("monsterEvadePct", 15);
                    dao.updateUserProgress(stealEvadeUp);
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 기술을 흉내내 몸놀림이 가벼워졌다! (다음 파티 공격 15% 확률로 회피)").append(NL);
                } else if ("ARCHER".equals(stolenJob)) {
                    midBossArcherDmgUp = true;
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 궁수의 기술을 흉내내 이번 공격의 피해가 늘어난다!").append(NL);
                }
            }
        }

        // [2026-09-14] "59층 이후(59,69,79...)는 보스가 스킬을 뺏어쓰는 행동이 추가되, 스킬을
        // 뺏으면서 반격해(마법사만이 아니라 전체 직업 모두)" 요청 -- 위 미드보스 스킬 도용과
        // 같은 컨셉이지만 층구간 보스(59층+, blockNo>=6) 전용으로 별도로 둔다. 미드보스는
        // "계수를 50%로" 요청(2026-09-09)으로 절반화됐지만, 층구간 보스는 그 절반화 이전
        // 원래 수치를 그대로 쓴다 -- "미드보스는 약하게, 층구간 보스는 강하게"라는 사용자
        // 의도에 맞춰 의도적으로 미드보스보다 세게 유지. 5개 직업 전부 도용 메시지 뒤
        // return 없이 아래 평범한 반격 코드로 자연스럽게 이어진다(요청: "스킬을 뺏으면서
        // 반격해"). 99층은 "다른 능력은 제거"(3인 동시공격+1회 부활만) 요청으로 제외.
        boolean bossArcherDmgUp = false;
        boolean isBossSkillStealFloor = "Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 6 && floor != 99;
        if (isBossSkillStealFloor) {
            List<String> bossStealable = new ArrayList<>();
            for (HashMap<String, Object> c : alive) {
                String j = strVal(c.get("CLASS"), "");
                if (JOB_NAME.containsKey(j) && !bossStealable.contains(j)) bossStealable.add(j);
            }
            if (!bossStealable.isEmpty()) {
                String bossStolenJob = bossStealable.get(RND.nextInt(bossStealable.size()));
                if ("MAGE".equals(bossStolenJob)) {
                    // 미드보스(50% 확률)와 달리 층구간 보스는 원래 수치대로 항상 발동.
                    List<HashMap<String, Object>> bStunPool = new ArrayList<>(alive);
                    bStunPool.remove(target);
                    HashMap<String, Object> bStunTarget = bStunPool.isEmpty() ? target : bStunPool.get(RND.nextInt(bStunPool.size()));
                    HashMap<String, Object> bStealStunUp = new HashMap<>();
                    bStealStunUp.put("userName", userName);
                    bStealStunUp.put("bossStunCid", intVal(bStunTarget.get("COMPANION_ID"), 0));
                    dao.updateUserProgress(bStealStunUp);
                    String bStunName = strVal(bStunTarget.get("NAME"), JOB_NAME.getOrDefault(strVal(bStunTarget.get("CLASS"), ""), "동료"));
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 마법사의 스킬을 빼앗았다! ")
                      .append(bStunName).append(" 기절! 다음턴 공격불가 (반격은 다른 동료에게)").append(NL);
                } else if ("WARRIOR".equals(bossStolenJob)) {
                    HashMap<String, Object> bStealDefUp = new HashMap<>();
                    bStealDefUp.put("userName", userName);
                    bStealDefUp.put("monsterDefBuffPct", 30);
                    dao.updateUserProgress(bStealDefUp);
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 전사의 스킬을 빼앗았다! 방어 태세를 갖췄다! (다음 파티 공격 시 방어력 +30%)").append(NL);
                } else if ("PRIEST".equals(bossStolenJob)) {
                    int bStealShieldAmt = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * 2.0);
                    HashMap<String, Object> bStealShUp = new HashMap<>();
                    bStealShUp.put("userName", userName);
                    bStealShUp.put("monsterShieldValue", bStealShieldAmt);
                    dao.updateUserProgress(bStealShUp);
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도사의 스킬을 빼앗았다! 스스로에게 보호막(").append(bStealShieldAmt).append(")을 둘렀다!").append(NL);
                } else if ("ROGUE".equals(bossStolenJob)) {
                    // [2026-09-18] 미드보스와 동일 취지, 층구간보스는 기존처럼 수치를 세게(25%).
                    HashMap<String, Object> bStealEvadeUp = new HashMap<>();
                    bStealEvadeUp.put("userName", userName);
                    bStealEvadeUp.put("monsterEvadePct", 25);
                    dao.updateUserProgress(bStealEvadeUp);
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 스킬을 빼앗았다! 몸놀림이 가벼워졌다! (다음 파티 공격 25% 확률로 회피)").append(NL);
                } else if ("ARCHER".equals(bossStolenJob)) {
                    bossArcherDmgUp = true;
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 궁수의 스킬을 빼앗았다! 이번 공격의 피해가 크게 늘어난다!").append(NL);
                }
            }
        }

        // 20층 이후 보스의 기절 스킬: [2026-09-05] 30%->20%(무시 스킬 삭제와 함께 완화) ->
        // 50%(무시 삭제로 빠진 위협도를 기절 쪽으로 보충)로 재조정. 이번 반격 턴을 통째로 써서
        // 대상을 기절시킴(피해 없음, 다음 파티 공격 턴 1회를 건너뛰게 됨 -- 위 party 루프의
        // bossStunCid 체크에서 소모됨).
        // [2026-09-14] isBossSkillStealFloor(59층+)는 위 스킬도용이 대신하므로 이 예전 방식과
        // 안 겹치게 건너뛴다 -- 안 그러면 이 50% 확률이 따로 또 터져서(둘 다 early return 성
        // 분기가 있는 별개 메커니즘) "스킬을 뺏으면서 반격"이 그 턴엔 무산될 수 있었다.
        // [2026-09-14 재수정] "99층에는 기절도 없애줘" 요청으로 99층은 이 예전 기절까지
        // 완전히 제외(트리플타격+1회부활만 남기는 "다른 능력은 제거" 취지를 완성).
        if (lateBoss && !isBossSkillStealFloor && floor != 99 && RND.nextInt(100) < 50) {
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
        boolean isBossRow = "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        // [2026-09-14] "99층은 즉사능력은 없으나 세 명을 동시공격하고... (다른 능력은 제거)"
        // 요청 -- 99층은 기존 2인타격(블록4+)을 대신해 3인타격을 쓴다.
        boolean tripleTargetFloor = isBossRow && floor == 99;
        boolean doubleTarget = isBossRow && blockNo(floor) >= 4 && !tripleTargetFloor;
        // [버그 수정, 2026-09-18] "I번 처치 후에도 두 마리 다 반격한다" 신고 -- dualMonster
        // 플래그(CUR_MONSTER_DUAL_YN)는 I번이 죽고 II번이 승격된 뒤에도 그대로 'Y'로 남는다
        // (처치보상 2배 계산이 이 플래그에 의존해서 일부러 안 끔, 위 promoteUp 주석 참고).
        // 그래서 반격 대상 선정은 이 플래그만 보지 말고 "II번이 실제로 아직 살아있는지"
        // (CUR_MONSTER2_HP_VALUE 존재 여부)까지 같이 확인해야 한다 -- 승격 후에는 이 값이
        // null이라 dualBothAlive가 false가 되어 자연스럽게 1명만 반격 대상이 된다.
        boolean dualBothAlive = dualMonster && p.get("CUR_MONSTER2_HP_VALUE") != null;
        List<HashMap<String, Object>> targets = new ArrayList<>();
        targets.add(target);
        if (doubleTarget || dualBothAlive || tripleTargetFloor) {
            List<HashMap<String, Object>> remaining = new ArrayList<>(alive);
            remaining.remove(target);
            if (!remaining.isEmpty()) targets.add(remaining.get(RND.nextInt(remaining.size())));
        }
        if (tripleTargetFloor) {
            List<HashMap<String, Object>> remaining2 = new ArrayList<>(alive);
            for (HashMap<String, Object> t : targets) remaining2.remove(t);
            if (!remaining2.isEmpty()) targets.add(remaining2.get(RND.nextInt(remaining2.size())));
        }

        // [2026-09-06 신설] 49층 이후(블록5+) 보스는 흡혈 능력 추가 -- 2명을 공격할 때 그 중
        // 두 번째 대상에게 실제로 들어간 피해(보호막으로 막힌 만큼은 제외한 값)만큼 자신의
        // 체력을 회복한다. 첫 번째 대상(전사 도발 대상이 될 수 있는 쪽)은 그대로 두고 흡혈은
        // 오직 한 명분만 적용(요청: "2명공격하니까 1명은 흡혈되도록").
        // [2026-09-14] "99층은... 다른 능력은 제거" 요청으로 99층은 흡혈 제외.
        boolean vampiricBoss = isBossRow && blockNo(floor) >= 5 && !tripleTargetFloor;
        long lifestealHeal = 0;
        // [2026-09-10] "도사3인조는 보호막만 있고 데미지가 없다" 요청 -- 보호막이 실제로 흡수한
        // 피해의 절반을 몬스터에게 반사 데미지로 돌려준다(순수 방어에서 절반은 공격으로 전환).
        // 아래 shieldPool 처리부에서 매 대상마다 흡수분을 누적했다가, 반격 루프가 끝난 뒤
        // lifestealHeal과 같은 자리에서 한 번에 몬스터 HP에 반영한다.
        boolean priestReflect = "PRIEST".equals(synergy);
        long shieldReflectDamage = 0;

        // [2026-09-07] "몬스터도 주사위를 굴리는데 51~70층은 6~12, 71층부터는 8~20을 굴리게
        // 해달라" 요청 -- 원래 몬스터 반격은 플레이어가 낀 주사위(diceMax)를 그대로 같이
        // 썼는데(플레이어가 큰 주사위를 낄수록 몬스터 반격도 덩달아 세지는 부작용), 51층+는
        // 몬스터 자신만의 무작위 면수 주사위를 매 턴 새로 굴려서 플레이어 장비와 무관하게
        // 반격 변동폭을 키운다(한 턴 안에서 여러 대상을 때리는 다중 타겟 보스는 같은 턴 동안
        // 같은 면수를 공유, 대상별 눈금만 각자 새로 굴림).
        int monsterDiceMax = monsterOwnDiceMax(floor, diceMax); // [2026-09-19] 기습과 동일 헬퍼로 통일
        // [2026-09-14][정정] "주사위 범위를 올려달라(ex 6~20이었다면 8~20으로)" 요청 --
        // 처음엔 미드보스(스킬 뺏는 몹, COMBAT칸에 몰래 섞여 나오는 평범한 몬스터 위장)에
        // 붙였는데, "미드보스 말고 층구간 보스(층 끝의 진짜 보스)가 강해져야 한다"는 재요청으로
        // isBossRow(BOSS_YN='Y', 예: 69층 아자토스) 기준으로 옮김. 일반 51+ 몬스터/미드보스의
        // 범위는 그대로 두고, 층구간 보스만 상한은 유지한 채 하한을 2 높여서(51~70층 6~12 ->
        // 8~12, 71층+ 8~20 -> 10~20) 변동폭을 좁히면서 최저치를 끌어올린다(=약한 반격이 덜 나옴).
        // [2026-09-15][밸런스 조정, 2차] "★6 한계돌파+2 기준 79층 보스가 도사 보호막 받은
        // 전제로 아슬아슬하게 20% 정도로"(79층 한정) 요청 -- 몬테카를로 시뮬레이션(2만회,
        // 아래 monsterAtk의 1.6배 관련 주석 참고)으로 재검증한 결과, 이 하한 상향까지 같이
        // 켜면 승률이 약 16%로 목표(약 20%)보다 낮아지고, 이 하한 상향만 빼면(79층은 원래
        // 51+ 공용 범위 8~20 그대로) 약 22%로 목표에 더 가까웠다 -- 그래서 79층은 계속
        // 빼둔 채로 유지. 다른 보스층(59/69/89/99)은 사용자가 79층만 지정했으므로 그대로 유지.
        if (isBossRow && floor >= 51 && floor != 79) {
            monsterDiceMax = (floor <= 70) ? (8 + RND.nextInt(5)) : (10 + RND.nextInt(11));
        }

        // [2026-09-09] 69층 보스 10턴 폭주 타이머 -- 넘긴 턴부터는 공격력만 대폭 상승(HP/DEF는
        // 그대로 -- "빨리 정리 못 하면 위험해진다"는 취지라 방어/체력까지 건드릴 필요는 없음).
        // 넘기기 전엔 마지막 몇 턴에 경고 문구를 붙여서 타이머가 다가온다는 걸 알려준다.
        Integer enrageLimit = isBossRow ? BOSS_ENRAGE_TURN_LIMIT.get(floor) : null;
        boolean enraged = enrageLimit != null && curCombatTurn > enrageLimit;
        if (enrageLimit != null) {
            if (enraged) {
                sb.append("🔥 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 폭주했다! 공격력이 크게 치솟는다!").append(NL);
            } else {
                int remain = enrageLimit - curCombatTurn;
                if (remain <= 2) {
                    sb.append("⏳ 폭주까지 ").append(remain).append("턴 남음! (").append(curCombatTurn).append("/").append(enrageLimit).append("턴)").append(NL);
                }
            }
        }

        // [2026-09-16] "즉시처치시 반격하는 문제" 수정 -- 두 마리(dualMonster) 몬스터의 경우
        // 위 파티 공격 루프에서 이번 턴에 방금 죽인 개체(I번/ti=0 또는 II번/ti=1)는 반격할
        // 기회 자체가 없어야 하는데, 이 아래 반격 루프는 dualMonster1KilledInLoop/
        // dualMonster2KilledInLoop를 전혀 보지 않고 항상 두 슬롯 다 반격시켰다(한 방에 I번을
        // 처치해도 죽은 I번 명의로 반격 로그가 그대로 찍히는 버그). ti별로 "이번 턴에 죽은
        // 슬롯"이면 통째로 건너뛴다.
        boolean anyRetaliationPrinted = false;
        for (int ti = 0; ti < targets.size(); ti++) {
            if (dualMonster && ((ti == 0 && dualMonster1KilledInLoop) || (ti == 1 && dualMonster2KilledInLoop))) {
                continue;
            }
            HashMap<String, Object> curTarget = targets.get(ti);
            boolean curGuarded = ti == 0 && guarded;
            HashMap<String, Object> curOriginalTarget = ti == 0 ? originalTarget : curTarget;
            if (anyRetaliationPrinted) sb.append(NL);
            anyRetaliationPrinted = true;

        String tJob = strVal(curTarget.get("CLASS"), "WARRIOR");
        int tGrade = intVal(curTarget.get("GRADE"), 1);
        List<HashMap<String, Object>> tEquips = dao.selectEquipByCompanion(intVal(curTarget.get("COMPANION_ID"), 0));
        int[] tEff = computeEffectiveStat(tJob, tGrade, tEquips, userStat, intVal(curTarget.get("LIMIT_BREAK"), 0));
        if ("RAINBOW".equals(synergy)) tEff[2] = (int) Math.round(tEff[2] * 1.1); // 시너지: 균형3인조 방어 +10%
        if (trapDefDown) tEff[2] = (int) Math.round(tEff[2] * 0.7); // 함정: 방어력 30% 약화(반격 피해 증가)
        if (luckyDefUp) tEff[2] = (int) Math.round(tEff[2] * luckyMult); // 럭키: 방어력 강화(반격 피해 감소)
        int monsterAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * (enraged ? BOSS_ENRAGE_ATK_MULT : 1.0) * trapAmbushDmgMult);
        // [2026-09-14][정정] "공격력을 1.6배 정도 올려달라" 요청 -- 처음엔 미드보스에 붙였는데,
        // "미드보스 말고 층구간 보스(층 끝 진짜 보스)가 강해져야 한다"는 재요청으로 isBossRow
        // 기준으로 옮김. eliteMult(HP/DEF에도 같이 쓰이는 배율)는 그대로 두고 공격력에만
        // 추가로 곱한다(요청이 "공격력만" 명시).
        // [2026-09-15][밸런스 조정, 2차] "★6 한계돌파+2 기준 79층 보스가 도사 보호막을 받은
        // 전제로 아슬아슬하게 20% 정도로 가능하도록, 1.6배 자체는 적당하다"(79층 한정) 요청 --
        // 1차 조정(손계산 기반)은 몬스터 반격 굴림이 "1~고정상한 균등분포"라고 잘못 가정해서
        // 1.6배를 79층만 아예 뺐었다. 실제로는 monsterDiceMax 자체가 매 턴 무작위 면수(예:
        // 8~20)로 먼저 정해지고 그 면수만큼(1~그 면수) 다시 굴리는 2단계 구조라 기댓값이 그
        // 절반가량(예: 8~20이면 평균 약 7.5)으로 훨씬 낮다 -- 이 부분을 반영해 실제 전투
        // 루프(도사 보호막/스킬도용 기절/부활 등 포함)를 몬테카를로 시뮬레이션(2만회)으로
        // 재검증한 결과, 1.6배를 유지한 채로도 승률 약 22%(도사 보호막 있는 ROGUE/PRIEST/
        // MAGE 파티, 한계돌파+2 기준)로 목표(약 20%)에 이미 근접했다. 그래서 1.6배는 되살리고
        // (사용자 판단대로 "적당함"), 주사위 하한 상향만 79층에서 계속 뺀 상태(아래 참고)로
        // 유지 -- 이 조합이 시뮬레이션상 목표치에 가장 가까웠다(주사위까지 올리면 약 16%로
        // 오히려 목표보다 낮아짐).
        if (isBossRow) monsterAtk = (int) Math.round(monsterAtk * 1.6);
        int roll = rollFace(1, monsterDiceMax); // 몬스터 자신의 반격 굴림 -- 플레이어 강화/마이너스 주사위와 무관하게 항상 1부터
        int rawDmgToParty = Math.max(1, monsterAtk * roll - tEff[2]);
        // 중간보스가 이번 턴 궁수 기술을 훔쳤으면(위 미드보스 파트) 이 반격 피해를 즉시 증폭.
        // [2026-09-16] "1.1배율로" 요청 -- 미드보스/구간보스 모두 +10%(x1.1)로 통일(과거 x1.15/x1.3 이력 있음).
        if (midBossArcherDmgUp) rawDmgToParty = (int) Math.round(rawDmgToParty * 1.1);
        if (bossArcherDmgUp) rawDmgToParty = (int) Math.round(rawDmgToParty * 1.1);
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
        sb.append(dualMonster ? eliteMonsterName(floor, mon, elite, ti) : eliteMonsterName(floor, mon, elite))
          .append("의 ").append(jobTag(tGrade, tJob, tName)).append("에게 반격! ").append(NL);

        // [2026-09-05 신설] ★5/★6 도적 "회피" -- 자신이 반격 대상이 되면 일정 확률로 피해를
        // 통째로 무효화한다(실드/전사 감소보다 우선 -- 아예 안 맞은 셈이라 뒤 계산 자체를 건너뜀).
        boolean rogueEvaded = false;
        if ("ROGUE".equals(tJob) && tGrade >= 5) {
            int evadeChance = tGrade >= 6 ? 25 : 20; // [2026-09-17] 밸런스 조정: 기존 45/30 -> 25/20
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
                // [2026-09-10] 도사3인조: 이번에 실제로 흡수한 만큼의 절반을 반사 데미지로 누적
                // (다중 타겟 보스면 대상마다 각자 흡수분의 절반씩 쌓임 -- shieldPool 자체가
                // 대상마다 독립 적용되는 기존 설계와 동일한 결).
                if (priestReflect && absorbed > 0) shieldReflectDamage += absorbed / 2;
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

        // [2026-09-16 재설계] 럭키칸 "피해 1회 면역"(구 즉사방어/DEATH_WARD) -- "즉사방어가
        // 잘 작동 안 되는 것 같다" 신고로, "이번 피해로 정확히 HP가 0이 될 때만 발동"(치명타
        // 한정) 방식을 버리고 "이 동료가 다음으로 맞는 피해 자체를 무조건 0으로 막는다"로
        // 단순화했다 -- 치명타 여부와 무관하게 훨씬 자주(사실상 다음 피격 즉시) 눈에 보이게
        // 발동한다. 일반 몬스터/중간보스는 즉사 능력이 따로 없고, 보스전은 진입 시(changeFloor)
        // 럭키 효과 자체가 이미 초기화돼 이 방어가 걸려 있을 수 없으므로, 이 반격 피해
        // 적용부 한 곳만 처리하면 충분하다(69/79/89층 보스의 은신즉사·하수인 관련 방어
        // 코드는 전부 죽은 코드였던 것으로 확인돼 이번에 함께 제거).
        int wardCompanionId = intVal(p.get("WARD_COMPANION_ID"), 0);
        if (wardCompanionId > 0 && wardCompanionId == intVal(curTarget.get("COMPANION_ID"), 0)) {
            dmgToParty = 0;
            HashMap<String, Object> wardClearUp = new HashMap<>();
            wardClearUp.put("userName", userName);
            wardClearUp.put("wardCompanionId", 0);
            dao.updateUserProgress(wardClearUp);
            p.put("WARD_COMPANION_ID", 0);
            sb.append("🛡️✨ 피해 면역 발동! ").append(jobTag(tGrade, tJob, tName)).append("이(가) 이번 피해를 완전히 막아냈다! (가호 소모)").append(NL);
        }

        // 흡혈은 두 번째 대상(ti==1)에게 실제로 박힌 최종 피해(보호막 흡수분 제외, 회피/면역
        // 시 0)만 집계 -- 이번 for문이 끝난 뒤 한꺼번에 보스 HP에 반영한다.
        if (vampiricBoss && ti == 1) lifestealHeal += dmgToParty;

        PP targetHpAfter = targetHp.subtract(PP.fromPP(dmgToParty));
        if (PP.toBaseValue(targetHpAfter) < 0) targetHpAfter = PP.fromPP(0);

        // [2026-09-05 신설] ★5/★6 도사 "부활" -- 이번 반격으로 동료가 쓰러지면, 파티 안의
        // ★5 이상 도사가(자기 자신이 쓰러진 경우 포함) 일정 확률로 그 자리에서 되살린다.
        // [2026-09-14] "69층 하수인화 걸리면 부활이 안 되도록 해야해" 요청 -- 69층(과 같은
        // 하수인 인프라를 쓰는 89층)은 이 일반 반격 사망 경로에서 부활 시도 자체를 건너뛴다
        // (부활이 성공하면 하수인화가 무의미해지므로 -- 쓰러지면 무조건 아래에서 하수인이 됨).
        boolean noRevivalMinionFloor = (floor == 69 || floor == 89) && "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        if (PP.toBaseValue(targetHpAfter) <= 0 && !noRevivalMinionFloor) {
            HashMap<String, Object> reviver = null;
            for (HashMap<String, Object> c : party) {
                if (!"PRIEST".equals(strVal(c.get("CLASS"), "")) || intVal(c.get("GRADE"), 1) < 5) continue;
                // [2026-09-16] "도사가 죽어있는 상태에서는 부활 스킬이 발동 안 되게 막아야해"
                // 요청 -- 도사 자신의 HP가 이미 0(전투불가)이면 기적을 못 일으킨다. c가 바로
                // curTarget(이번 반격으로 지금 막 쓰러지는 대상)인 경우엔 writeCompanionHp가
                // 아직 호출 전이라 c의 CUR_HP_VALUE가 이번 피해 반영 전(=쓰러지기 직전) 값을
                // 그대로 갖고 있으므로, "자기 자신이 쓰러진 경우 포함"(자가 부활)은 그대로 허용됨.
                PP reviverHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                if (PP.toBaseValue(reviverHp) <= 0) continue;
                reviver = c;
                break;
            }
            if (reviver != null) {
                int reviverGrade = intVal(reviver.get("GRADE"), 1);
                int reviveChance = reviverGrade >= 6 ? 15 : 10; // [2026-09-17] 밸런스 조정: 기존 40/25 -> 15/10
                double revivePct = reviverGrade >= 6 ? 0.5 : 0.3;
                if (RND.nextInt(100) < reviveChance) {
                    targetHpAfter = PP.fromPP(Math.max(1, (int) Math.round(tEff[0] * revivePct)));
                    sb.append("✨ 도사의 기적! ").append(jobTag(tGrade, tJob, tName))
                      .append(" 부활 (HP ").append(targetHpAfter.format()).append("/").append(tEff[0]).append(")").append(NL);
                }
            }
        }

        // [버그 수정] DB엔 반영됐지만 curTarget(=party 리스트 안의 같은 객체)의 메모리 값은 안
        // 바뀌어서, 바로 아래 partyHpSummary()가 반격 맞기 "전" HP를 그대로 보여주는 문제가
        // 있었다("이번턴 남은" 구간에 맞은 사람 HP가 그대로 풀피로 나옴 -- 신고로 확인).
        // writeCompanionHp가 DB 반영과 party 리스트 원본 갱신을 함께 처리(+ 0 미만 방어).
        writeCompanionHp(curTarget, targetHpAfter);

        // [2026-09-10] "69층 보스는 동료를 죽이면 보스 하수인으로 살려서 공격하게, 보스
        // 처치시 사라지게 해달라" 요청 -- 이 턴 반격으로 방금 죽은(위에서 부활 자체를 건너뛴)
        // 동료를 69층(+89층, 2026-09-14 확장) 보스 한정으로 하수인 명단에 추가한다. 이미
        // HP0이라 파티 공격에서는 자동으로 빠지고(기존 alive 필터, "공격불가"), 아래 반격
        // 파트 끝에서 매 턴 파티를 역공격하는 쪽으로만 쓰인다. 전투 종료(승리/전멸/도망
        // 전부 clearMonster를 거침) 시 자동 초기화.
        if (noRevivalMinionFloor && PP.toBaseValue(targetHpAfter) <= 0) {
            List<Integer> minionIds = parseMinionCids(strVal(p.get("CUR_BOSS_MINION_CIDS"), ""));
            int diedCid = intVal(curTarget.get("COMPANION_ID"), 0);
            if (diedCid > 0 && !minionIds.contains(diedCid)) {
                minionIds.add(diedCid);
                String joinedCids = joinMinionCids(minionIds);
                HashMap<String, Object> minionUp = new HashMap<>();
                minionUp.put("userName", userName);
                minionUp.put("curBossMinionCids", joinedCids);
                dao.updateUserProgress(minionUp);
                p.put("CUR_BOSS_MINION_CIDS", joinedCids);
                sb.append("💀🥀 ").append(jobTag(tGrade, tJob, tName)).append("이(가) 쓰러져 보스의 하수인이 되었다! (보스를 처치하면 원래대로 돌아옵니다)").append(NL);
            }
        }

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

        // [2026-09-10] 도사3인조 보호막 반사 데미지 반영 -- 위 반격 루프에서 누적한 만큼
        // 몬스터 체력을 추가로 깎는다(흡혈과 같은 방식으로 루프 종료 후 한 번에 처리, 0 밑으로는
        // 안 내려가게 방어). 이미 처치 판정이 난 경우 monsterHpAfter가 이미 0이라 더 깎을 것도
        // 없어 자연히 무해함.
        if (shieldReflectDamage > 0) {
            PP reflectedHp = monsterHpAfter.subtract(PP.fromPP(shieldReflectDamage));
            if (PP.toBaseValue(reflectedHp) < 0) reflectedHp = PP.fromPP(0);
            monsterHpAfter = reflectedHp;
            HashMap<String, Object> reflectUp = new HashMap<>();
            reflectUp.put("userName", userName);
            reflectUp.put("curMonsterHpValue", monsterHpAfter.getValue());
            reflectUp.put("curMonsterHpExt", monsterHpAfter.getUnit());
            dao.updateUserProgress(reflectUp);
            sb.append(NL).append("✨ 보호막이 흡수한 피해의 절반을 반사! ").append(shieldReflectDamage)
              .append("dmg 추가 피해! 💛").append(monsterHpAfter.format()).append("/").append(monsterMaxHp.format());
        }

        // [2026-09-10] 69층 보스 하수인 반격 -- 위(또는 이전 턴)에 죽어서 하수인이 된 동료들이
        // 매 턴 파티를 추가로 공격한다. 자기 자신의 유효 스탯(장비/스탯구매 반영)을 그대로
        // 몬스터처럼 사용("동료였던 힘이 그대로 나를 공격한다"). 같은 턴 안에서 방금 새로
        // 죽은 동료는 이번 하수인 공격엔 아직 안 낀다(그 자리에서 바로 연쇄되면 한 턴에
        // 무한정 불어날 수 있어서, 다음 턴부터 반영되게 함). 보호막/전사 도발 등 이번 턴
        // 보스 반격에 쓰인 파티 버프는 재사용하지 않는 단순한 추가 공격으로 둠.
        // [2026-09-14] 69/89층으로 제한 -- 79층 하수인은 "매 턴 공격"이 아니라 위
        // boss79Ambush 블록 안에서 앰부시 턴(6턴마다)에만 은신 즉사를 시도하는 별도 방식이라,
        // 여기서 또 매 턴 공격까지 겹치면 안 된다(noRevivalMinionFloor는 for(targets) 루프
        // 안에서 선언돼 여기선 범위 밖이라 같은 조건을 다시 계산).
        boolean minionAttackEveryTurnFloor = (floor == 69 || floor == 89) && "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        List<Integer> curMinionIds = minionAttackEveryTurnFloor ? parseMinionCids(strVal(p.get("CUR_BOSS_MINION_CIDS"), "")) : new ArrayList<>();
        if (!curMinionIds.isEmpty()) {
            List<HashMap<String, Object>> stillAlive = new ArrayList<>();
            for (HashMap<String, Object> c : party) {
                PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                if (PP.toBaseValue(hp) > 0) stillAlive.add(c);
            }
            if (!stillAlive.isEmpty()) {
                HashMap<Integer, HashMap<String, Object>> partyById = new HashMap<>();
                for (HashMap<String, Object> c : party) partyById.put(intVal(c.get("COMPANION_ID"), -1), c);
                sb.append(NL);
                for (Integer minionCid : curMinionIds) {
                    HashMap<String, Object> minionC = partyById.get(minionCid);
                    if (minionC == null) continue; // 방어(파티에서 이미 빠진 경우 등)
                    String mJob = strVal(minionC.get("CLASS"), "WARRIOR");
                    int mGrade = intVal(minionC.get("GRADE"), 1);
                    String mName = strVal(minionC.get("NAME"), JOB_NAME.getOrDefault(mJob, "동료"));
                    List<HashMap<String, Object>> mEquips = dao.selectEquipByCompanion(minionCid);
                    int[] mEff = computeEffectiveStat(mJob, mGrade, mEquips, userStat, intVal(minionC.get("LIMIT_BREAK"), 0));

                    HashMap<String, Object> victim = stillAlive.get(RND.nextInt(stillAlive.size()));
                    String vJob = strVal(victim.get("CLASS"), "WARRIOR");
                    int vGrade = intVal(victim.get("GRADE"), 1);
                    List<HashMap<String, Object>> vEquips = dao.selectEquipByCompanion(intVal(victim.get("COMPANION_ID"), 0));
                    int[] vEff = computeEffectiveStat(vJob, vGrade, vEquips, userStat, intVal(victim.get("LIMIT_BREAK"), 0));
                    PP victimHp = PP.of(((Number) victim.get("CUR_HP_VALUE")).doubleValue(), strVal(victim.get("CUR_HP_EXT"), ""));

                    int mRoll = rollFace(1, monsterDiceMax);
                    int mDmg = Math.max(1, mEff[1] * mRoll - vEff[2]);
                    PP victimHpAfter = victimHp.subtract(PP.fromPP(mDmg));
                    if (PP.toBaseValue(victimHpAfter) < 0) victimHpAfter = PP.fromPP(0);

                    // [2026-09-16] 이 경로(69/89층 보스 하수인 매턴공격)도 보스전 진입 시 럭키
                    // 효과가 이미 초기화되어 방어가 걸려 있을 수 없는 죽은 코드라 제거.
                    writeCompanionHp(victim, victimHpAfter);

                    boolean victimDied = PP.toBaseValue(victimHpAfter) <= 0;
                    sb.append("👹 하수인이 된 ").append(jobTag(mGrade, mJob, mName)).append("이(가) ")
                      .append(jobTag(vGrade, vJob, strVal(victim.get("NAME"), JOB_NAME.getOrDefault(vJob, "동료"))))
                      .append("을(를) 공격! 🎲").append(mRoll).append("→").append(mDmg).append("dmg")
                      .append(victimDied ? " 💀" : "").append(NL);
                    if (victimDied) {
                        stillAlive.remove(victim);
                        if (stillAlive.isEmpty()) break; // 더 공격할 대상 없음(다음 /주사위 때 전멸 처리됨)
                    }
                }
            }
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
            int[] eff = computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0));
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

    /** [2026-09-16] "즉사방어를 추가했는데 HP가 마이너스로 저장되는 버그가 있다"(실사례:
     *  컴파니언193 CUR_HP_VALUE=-1150) 신고로 추가한 마지막 방어선. 전투 중 companion HP를
     *  쓰는 경로(반격/기습/하수인 공격 등)가 여러 곳으로 흩어져 있고 각자 0-클램프를 따로
     *  구현하고 있었는데, 정확히 어느 경로가 원인인지 로그만으로는 특정하지 못했다 -- 이제
     *  모든 HP 기록을 이 헬퍼 하나로 통일해서, 개별 클램프가 놓치는 경로가 있더라도 여기서
     *  한 번 더 걸러져 DB엔 절대 음수가 안 들어가게 한다(메시지 문구는 호출부가 이미
     *  자기 지역변수로 먼저 만들어둔 뒤 호출하므로 영향 없음). */
    private void writeCompanionHp(HashMap<String, Object> companion, PP hpAfter) {
        if (PP.toBaseValue(hpAfter) < 0) hpAfter = PP.fromPP(0);
        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(companion.get("COMPANION_ID"), 0));
        up.put("curHpValue", hpAfter.getValue());
        up.put("curHpExt", hpAfter.getUnit());
        dao.updateCompanionHp(up);
        companion.put("CUR_HP_VALUE", hpAfter.getValue());
        companion.put("CUR_HP_EXT", hpAfter.getUnit());
    }

    private void setCompanionFullHp(HashMap<String, Object> c, HashMap<String, Object> userStat) {
        String job = strVal(c.get("CLASS"), "WARRIOR");
        int grade = intVal(c.get("GRADE"), 1);
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(c.get("COMPANION_ID"), 0));
        int[] eff = computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0));
        HashMap<String, Object> up = new HashMap<>();
        up.put("companionId", intVal(c.get("COMPANION_ID"), 0));
        up.put("curHpValue", (double) eff[0]);
        up.put("curHpExt", "");
        dao.updateCompanionHp(up);
    }

    /** 이 동료의 현재 장비 기준 유효 최대체력(EFF_HP). 장비 변경 전/후 비교용. */
    private int effMaxHpOf(HashMap<String, Object> companion, HashMap<String, Object> userStat) {
        String job = strVal(companion.get("CLASS"), "WARRIOR");
        int grade = intVal(companion.get("GRADE"), 1);
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(companion.get("COMPANION_ID"), 0));
        int[] eff = computeEffectiveStat(job, grade, equips, userStat, intVal(companion.get("LIMIT_BREAK"), 0));
        return eff[0];
    }

    /** [2026-09-18] "장비장착/해제로 HP 변동분이 생길때, 비율 그대로 유지한채로 가져가게 해줘"
     *  요청 -- 장비 변경으로 EFF_HP(최대체력)가 바뀌어도 현재 체력을 절대값 그대로 두지 않고,
     *  "직전 최대체력 대비 비율"을 새 최대체력에 그대로 적용해서 재계산한다(예: 50%로 싸우던
     *  중 헬멧을 바꿔 최대체력이 늘거나 줄어도 그대로 50%). 호출부는 장비 변경 "전"에
     *  effMaxHpOf()로 oldMaxHp를 구해뒀다가, 변경 "후" 이 메서드를 호출한다. 전투불가(0)
     *  상태는 0%×새 최대체력도 0이라 자연히 그대로 유지된다. */
    private void rescaleHpForEquipChange(HashMap<String, Object> companion, HashMap<String, Object> userStat, int oldMaxHp) {
        if (oldMaxHp <= 0) return;
        int newMaxHp = effMaxHpOf(companion, userStat);
        if (newMaxHp == oldMaxHp) return;
        PP curHp = PP.of(((Number) companion.get("CUR_HP_VALUE")).doubleValue(), strVal(companion.get("CUR_HP_EXT"), ""));
        long curHpBase = PP.toBaseValue(curHp);
        if (curHpBase <= 0) return; // 전투불가는 비율도 0 -- 그대로 둠
        long newHpBase = Math.round(curHpBase * (newMaxHp / (double) oldMaxHp));
        if (newHpBase > newMaxHp) newHpBase = newMaxHp; // 반올림 오차 방어
        if (newHpBase < 1) newHpBase = 1; // 원래 0 초과였으므로 반올림으로 0이 되는 것 방지
        writeCompanionHp(companion, PP.fromPP(newHpBase));
    }

    private HashMap<String, Object> findMonsterById(int floor, int monsterId) {
        HashMap<String, Object> normal = applyHardcoreFloorScale(dao.selectMonster(blockNo(floor), "N"), floor);
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
        PP reward = perKill.multiply(kills * floorPpMultiplier(floor) * autoHuntFloorBonusMultiplier(floor));
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
        // [2026-09-07, 당분간] CONTENT_LOCKED_FLOOR 이상은 콘텐츠 준비 전이라 진입 자체를
        // 차단(마을 접근 등 다른 제약보다 우선 확인). maxReached로 이미 자격이 있어도 예외
        // 없이 막는다. [2026-09-18] 81~90(블록9) 오픈으로 잠금선을 91로 상향.
        if (target >= CONTENT_LOCKED_FLOOR) {
            return "🌑 어둠이 득실거려 현재는 갈 수 없습니다. (" + CONTENT_LOCKED_FLOOR + "층 이상, 추후 오픈 예정)";
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
        // [2026-09-15 신설] "보스룸(각9층)에 도착하면 모든 럭키/함정 효과를 무효로 하고
        // 시작" 요청 -- 보스전은 이미 충분히 빡빡하게 설계돼 있어서, 사냥터에서 우연히
        // 걸어둔 버프/디버프(즉사방어 포함, 이것도 럭키칸 산출물이므로 함께 무효화)를 그대로
        // 들고 들어가는 게 유불리를 크게 흔드는 걸 막기 위함.
        boolean enteringBossRoom = target % 10 == 9 && target != floor;
        if (enteringBossRoom) {
            up.put("luckyTurnLeft", 0);
            up.put("luckyEffect", "");
            up.put("trapTurnLeft", 0);
            up.put("trapEffect", "");
            up.put("wardCompanionId", 0);
        }
        dao.updateUserProgress(up);
        if (newFleeCount >= 0) {
            checkFleeAchievements(userName, newFleeCount);
        }

        // [2026-09-10] "자동사냥이 실제 사냥터가 아니라 그냥 멈춰있는 층 기준으로 도는 것
        // 같다, 최근 10마리를 잡은 곳을 기준으로 해달라" 요청으로 이 동기화(층 이동만 해도
        // 정산 기준을 그 층으로 옮기던 로직)는 제거했다 -- 실제로 싸운 적 없는 층(그냥
        // 지나가거나 전투 중 도망친 층)까지 정산 기준이 돼버리는 문제가 있었음. 대신
        // resolveCombatTurn()에서 "실제로 몬스터를 잡을 때마다" AUTO_HUNT_LOG.FLOOR를
        // 갱신하도록 옮겨서, 정산 기준이 항상 "가장 최근에 실제로 이긴 전투의 층"이 되게 함.
        int targetFm = target % 10;

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
            // [2026-09-16] "편성 안 된 동료도 마을 도착 시 전부 부활시켜달라" 요청 -- 기존엔
            // PARTY_SLOT 있는 동료만 넘겨서 후보(대기) 동료는 전투불가 상태가 안 풀렸다.
            revivedCount = revivePartyDead(userName, dao.selectUserCompanions(userName), dao.selectUserStat(userName));
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

        // [2026-09-16] "편성 안 된 동료도 마을 도착 시 전부 부활시켜달라" 요청.
        int revivedCount = revivePartyDead(userName, dao.selectUserCompanions(userName), dao.selectUserStat(userName));

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

        // [2026-09-16] "편성 안 된 동료도 마을 도착 시 전부 부활시켜달라" 요청.
        int revivedCount = revivePartyDead(userName, dao.selectUserCompanions(userName), dao.selectUserStat(userName));

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
        // [2026-09-19] "정렬이 뒤죽박죽이야, 성급 desc로 목록 뿌려줘, HP는 표기 안해도 될것같아"
        // 요청 -- 번호(idx)는 /파티편성 N, /동료가리기 N 등이 selectUserCompanions()의 원래
        // 순서(PARTY_SLOT, COMPANION_ID)로 그대로 위치 참조하므로 절대 바꾸면 안 된다(바꾸면
        // 여기 화면에서 본 번호로 다른 명령을 입력했을 때 엉뚱한 동료가 걸림). 그래서 idx
        // 자체는 기존과 동일하게 매기고, 화면에 "보여주는 줄 순서"만 성급 내림차순(동점이면
        // 한계돌파 내림차순)으로 재배열한다. HP는 요청대로 표시에서 제거.
        int idx = 1;
        int hiddenCount = 0;
        List<int[]> order = new ArrayList<>(); // [grade, limitBreak, lines 안에서의 위치]
        List<String> lines = new ArrayList<>();
        for (HashMap<String, Object> c : companions) {
            // 숨김 처리된 동료는 번호(인덱스)만 소비하고 텍스트 목록엔 표시하지 않는다.
            if ("Y".equals(strVal(c.get("HIDDEN_YN"), "N"))) { idx++; hiddenCount++; continue; }
            String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "?");
            String name = strVal(c.get("NAME"), job); // 이름 없는 옛 데이터는 직업명으로 대체 표시
            int grade = intVal(c.get("GRADE"), 1);
            int limitBreak = intVal(c.get("LIMIT_BREAK"), 0);
            Object slot = c.get("PARTY_SLOT");
            // [2026-09-14] "동료편성 화면 및 텍스트에 (+3)이런식으로 표기해줘" 요청 -- 한계돌파
            // 단계가 있을 때만 등급 뒤에 붙인다.
            String line = idx + ". " + name + " (" + job + " ★" + grade
                    + (limitBreak > 0 ? " (+" + limitBreak + ")" : "") + ")"
                    + (slot != null ? " [파티 " + slot + "번]" : " [대기]");
            order.add(new int[]{ grade, limitBreak, lines.size() });
            lines.add(line);
            idx++;
        }
        order.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]); // 성급desc, 동점이면 한계돌파desc
        for (int[] o : order) sb.append(lines.get(o[2])).append(NL);
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
    private int unequipAllForCompanion(HashMap<String, Object> companion) {
        int companionId = intVal(companion.get("COMPANION_ID"), 0);
        List<HashMap<String, Object>> equipped = dao.selectEquipByCompanion(companionId);
        if (equipped.isEmpty()) return 0;
        // [2026-09-18] 장비 해제로 EFF_HP가 바뀌어도 비율 유지(rescaleHpForEquipChange 참고) --
        // 해제 "전" 최대체력을 먼저 구해둔다.
        HashMap<String, Object> userStat = dao.selectUserStat(strVal(companion.get("USER_NAME"), ""));
        int oldMaxHp = effMaxHpOf(companion, userStat);
        for (HashMap<String, Object> e : equipped) {
            HashMap<String, Object> unwear = new HashMap<>();
            unwear.put("equipId", intVal(e.get("EQUIP_ID"), 0));
            unwear.put("equippedCompanionId", null);
            dao.updateEquipEquippedCompanion(unwear);
        }
        rescaleHpForEquipChange(companion, userStat, oldMaxHp);
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
            int unequipped = unequipAllForCompanion(target);
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
        int evictedUnequipped = unequipAllForCompanion(occupant);
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
                unequippedTotal += unequipAllForCompanion(c);
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
     * 동료뽑기권/장비뽑기권/악세서리뽑기권 전부 등급(tier 1~4)을 못박은 티어락 권으로
     * 지급(그 등급 계약서/상자에만 쓸 수 있고, 아직 그 층에 못 간 유저도 이 권으로는 바로
     * 뽑을 수 있음 -- hasUsableCompanionVoucher/hasUsableEquipVoucher/
     * hasUsableAccessoryVoucher 참고). [버그 수정] 원래 장비는 등급 구분 없이 범용
     * EQUIP_VOUCHER로만 지급했는데, "/이벤트지급 중급 1 1 했더니 장비뽑기권은 초급으로
     * 지급됐다(둘 다 중급이어야 함)"는 신고로 확인 -- 이제 EQUIP_VOUCHER_T{tier}로 지급.
     * [2026-09-15] "악세뽑기권도 파라미터로 추가해서 지급할 수 있게 해달라, 예: /이벤트지급
     * 초급 0 0 1 => 악세뽑기권초급 1개" 요청으로 4번째 인자(accessoryQty) 추가.
     */
    @Override
    @Transactional
    public String grantEventVouchers(String userName, int tier, int companionQty, int equipQty, int accessoryQty) {
        if (!isEventAdmin(userName)) {
            return "권한이 없습니다.";
        }
        if (tier < 1 || tier > 4) {
            return "등급은 1(하급)~4(최상급) 사이여야 합니다.";
        }
        if (companionQty < 0 || equipQty < 0 || accessoryQty < 0) {
            return "수량은 0 이상이어야 합니다.";
        }
        if (companionQty == 0 && equipQty == 0 && accessoryQty == 0) {
            return "사용법: /이벤트지급 [등급 초급|중급|상급|최상급] [동료뽑기권수량] [장비뽑기권수량] [악세뽑기권수량] "
                    + "(예: /이벤트지급 중급 3 2 1, 등급 생략 시 초급, 수량 생략 시 0)";
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
        if (accessoryQty > 0) {
            int affected = dao.bulkGrantTierAccessoryVoucher(tier, accessoryQty);
            sb.append(NL).append("전체 유저 ").append(affected).append("명에게 ")
              .append(COMPANION_TIER_NAME[tier - 1]).append("(").append(tier).append("번) 악세뽑기권 ")
              .append(accessoryQty).append("장 지급 (해금 여부와 무관하게 바로 사용 가능)");
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
            // [2026-09-16] 50층+ 50%탐사 보상(ACH_ID 500+floor, ACH_TYPE=FLOOR_EXPLORE_HALF)도
            // 층마다 하나씩 쌓이면 FLOOR_EXPLORE와 똑같이 줄이 폭증하므로 같은 방식으로 블록별
            // 그룹핑.
            java.util.TreeMap<Integer, List<Integer>> halfFloorsByBlock = new java.util.TreeMap<>();
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
                } else if ("FLOOR_EXPLORE_HALF".equals(type)) {
                    int floor;
                    try { floor = Integer.parseInt(strVal(a.get("ACH_PARAM"), "0").trim()); }
                    catch (NumberFormatException nfe) { floor = 0; }
                    List<Integer> bucket = halfFloorsByBlock.get(blockNo(floor));
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        halfFloorsByBlock.put(blockNo(floor), bucket);
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
            for (Integer block : halfFloorsByBlock.keySet()) {
                List<Integer> floors = halfFloorsByBlock.get(block);
                Collections.sort(floors);
                StringBuilder floorList = new StringBuilder();
                for (int i = 0; i < floors.size(); i++) {
                    if (i > 0) floorList.append(",");
                    floorList.append(floors.get(i));
                }
                sb.append("✅ 탐사50%").append(block >= 1 && block <= roman.length ? roman[block - 1] : String.valueOf(block))
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

    // ================================================================
    // 동료 초상화 축소판 캐싱 (2026-09-14, "핸드폰에서 이미지가 잘 안 뜬다" 근본 대응)
    // ================================================================
    // 조사 결과: nekos.best 원본 초상화가 장당 1.7~3.2MB(가로 1000~2900px)인데 화면엔
    // 48~64px로만 쓰고 있어서, 동료 60마리 전체보기를 한 번에 열면 100MB+를 동시에 받으려는
    // 게 유력한 원인이었다(loading="lazy"로 1차 완화는 이미 적용됨). 여기서는 서버가 작은
    // 정사각형 축소판(160x160 JPEG)을 만들어 디스크에 캐싱해두고, /api/tower-avatar가 원본
    // 대신 이 축소판을 서빙한다 -- 캐시 키는 COMPANION_ID가 아니라 IMAGE_URL의 MD5(이미지갱신
    // 등으로 URL이 바뀌면 자동으로 새 캐시 항목이 되고, 옛 파일은 그냥 안 쓰이게 됨). 캐시
    // 디렉토리는 OS 임시폴더 하위라 서버 재시작으로 비워져도 다음 요청 때 다시 만들어질
    // 뿐이라 문제 없다.
    private static final File AVATAR_CACHE_DIR = new File(System.getProperty("java.io.tmpdir"), "s5_avatar_cache");
    private static final int AVATAR_THUMB_SIZE = 160; // CSS 표시 크기(48~64px)의 레티나 대비 2~3배

    /** URL 문자열의 MD5 hex -- 캐시 파일명으로 씀(URL 특수문자를 그대로 파일명에 못 쓰므로). */
    private String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    /** 원본 이미지를 정사각형으로 크롭(가로 중앙, 세로는 위쪽 15% 지점 기준 -- CSS의
     *  object-position:50% 15%와 맞춤)한 뒤 targetSize로 축소. 알파 채널(PNG 투명)은 흰
     *  배경에 얹어서 JPEG로 안전하게 변환한다. */
    private BufferedImage cropResizeSquare(BufferedImage src, int targetSize) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.min(w, h);
        int x = Math.max(0, (w - side) / 2);
        int maxY = Math.max(0, h - side);
        int y = (int) Math.round(maxY * 0.15);
        BufferedImage cropped = src.getSubimage(x, y, side, side);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetSize, targetSize);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cropped, 0, 0, targetSize, targetSize, null);
        g.dispose();
        return resized;
    }

    private byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /** 웹 SPA 동료 초상화 축소판 프록시(/api/tower-avatar)용 -- 캐시에 있으면 그대로, 없으면
     *  원본을 받아 축소/캐싱 후 반환. 실패(원본 없음/외부 API 차단/디코딩 실패 등)하면
     *  null -- 컨트롤러가 404로 응답하고, 프론트는 기존 onerror 폴백(이모지)으로 자연스럽게
     *  처리한다. 동시에 같은 캐시 파일을 처음 요청하는 여러 스레드가 겹쳐도(각자 원본을
     *  중복으로 받아와 낭비는 있을 수 있지만) 임시파일에 먼저 쓰고 원자적으로 rename하므로
     *  다른 스레드가 쓰다 만 파일을 읽는 깨진 이미지 문제는 없다. */
    @Override
    public byte[] getCompanionAvatarThumbnail(int companionId) {
        String imageUrl = dao.selectCompanionImageUrl(companionId);
        if (imageUrl == null || imageUrl.trim().isEmpty()) return null;
        try {
            File cacheFile = new File(AVATAR_CACHE_DIR, md5Hex(imageUrl) + ".jpg");
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return Files.readAllBytes(cacheFile.toPath());
            }
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            // fetchRandomNekoImage()와 동일 이유(User-Agent 없으면 403).
            conn.setRequestProperty("User-Agent", "RgbTowerBot/1.0 (https://rgb-tns.dev-apc.com)");
            if (conn.getResponseCode() != 200) return null;
            BufferedImage original;
            try (InputStream is = conn.getInputStream()) {
                original = ImageIO.read(is);
            }
            if (original == null) return null;
            BufferedImage thumb = cropResizeSquare(original, AVATAR_THUMB_SIZE);
            byte[] thumbBytes = encodeJpeg(thumb, 0.82f);
            AVATAR_CACHE_DIR.mkdirs();
            File tmpFile = new File(AVATAR_CACHE_DIR, cacheFile.getName() + "." + Thread.currentThread().getId() + ".tmp");
            Files.write(tmpFile.toPath(), thumbBytes);
            try {
                Files.move(tmpFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(tmpFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return thumbBytes;
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

    /** 이 유저가 이미 보유한 (직업,이름) 동료 개체를 반환(없으면 null). 뽑기/선택권 공용 중복판정. */
    private HashMap<String, Object> findOwnedCompanion(String userName, String job, String name) {
        for (HashMap<String, Object> owned : dao.selectUserCompanions(userName)) {
            if (job.equals(strVal(owned.get("CLASS"), "")) && name.equals(strVal(owned.get("NAME"), ""))) {
                return owned;
            }
        }
        return null;
    }

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
        // 상황 자체가 이제 구조적으로 발생하지 않는다.
        // [2026-09-14 재설계] "중복 동료를 얻으면 한계돌파(최대 6단계)가 되게 해달라" 요청 --
        // 중복이 뜨면 PP 환급 대신 그 보유 동료 개체의 LIMIT_BREAK를 1단계 올린다(단계당
        // 전체 스탯 +10%, computeEffectiveStat 참고). 이미 6단계(만렙)면 더 올릴 자리가
        // 없으니 그때만 기존처럼 PP로 환급(COMPANION_DUPE_REFUND 고정표, 성급 기준).
        HashMap<String, Object> ownedDupe = findOwnedCompanion(userName, job, name);
        if (ownedDupe != null) {
            int curLimitBreak = intVal(ownedDupe.get("LIMIT_BREAK"), 0);
            result.put("ok", true);
            result.put("dupe", true);
            result.put("job", job);
            result.put("grade", grade);
            result.put("name", name);
            if (curLimitBreak < LIMIT_BREAK_MAX) {
                int newLimitBreak = curLimitBreak + 1;
                HashMap<String, Object> lbUp = new HashMap<>();
                lbUp.put("companionId", intVal(ownedDupe.get("COMPANION_ID"), 0));
                lbUp.put("limitBreak", newLimitBreak);
                dao.updateCompanionLimitBreak(lbUp);
                result.put("limitBreakUp", true);
                result.put("limitBreak", newLimitBreak);
            } else {
                PP dupeBonus = PP.fromPP(COMPANION_DUPE_REFUND[grade - 1]);
                addPp(userName, p, dupeBonus);
                result.put("limitBreakUp", false);
                result.put("dupeBonus", dupeBonus.format());
            }
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
            if (Boolean.TRUE.equals(r.get("limitBreakUp"))) {
                int lb = intVal(r.get("limitBreak"), 0);
                return "🔺 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 뽑힘)" + NL
                        + "한계돌파+" + lb + " 달성! (전체 스탯 +" + Math.round(limitBreakPct(lb) * 100) + "%)";
            }
            return "🔁 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 뽑힘)" + NL
                    + "한계돌파가 이미 최대(+" + LIMIT_BREAK_MAX + ")라 계약서 대신 " + r.get("dupeBonus") + " PP로 환급되었습니다.";
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
        int limitBreakCount = 0; // 한계돌파로 이어진 중복 수
        int dupeCount = 0; // 한계돌파 만렙이라 PP로 환급된 중복 수
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
                if (Boolean.TRUE.equals(r.get("limitBreakUp"))) {
                    limitBreakCount++;
                } else {
                    dupeCount++;
                    dupeTotal = dupeTotal.add(PP.parse((String) r.get("dupeBonus")));
                }
            } else {
                owned++; // 중복이 아닌 실제 신규 동료일 때만 보유 수 증가(스타터 무료뽑기/업적 판정에 사용)
            }
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 동료뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (limitBreakCount > 0) {
            sb.append(NL).append("🔺 한계돌파 ").append(limitBreakCount).append("마리");
        }
        if (dupeCount > 0) {
            sb.append(NL).append("🔁 중복(한계돌파 만렙) ").append(dupeCount).append("마리 → ").append(dupeTotal.format()).append(" PP 환급");
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
        // [2026-09-17] 무기/갑옷/투구 통합 -- job 롤은 기존 그대로 5직업 균등, 그 직업이 속한
        // 그룹값(검/지팡이/활, 갑주/로브/재킷, 투구/머리띠/깃장식)으로 변환해서 저장한다. 예전엔
        // 전사용 1/5 + 도적용 1/5로 따로 나뉘던 게 이제 그룹 하나로 합쳐져 2/5가 되고(궁수 몫은
        // 그대로 1/5) -- 직업별 드랍 확률 총량 자체는 그대로 유지되고 착용 가능 범위만 넓어지는
        // 것뿐이라 경제(획득 총량)에 영향 없음.
        String equipClassToStore = equipClassForNewItem(job, part);

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", equipClassToStore);
        e.put("part", part);
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        if (grade == 6) grantAchievement(userName, 22);

        // [2026-09-18] "최상급장비상자에서 조각이 1~3개정도 나오게 해줘" 요청 -- GACHA_ID=8
        // ("전설의 장비 상자", 80층 해금 최상급 티어)에서만 매 뽑기마다 조각 1~3개 보너스 지급.
        if (intVal(gacha.get("GACHA_ID"), 0) == 8) {
            int fragmentGranted = 1 + RND.nextInt(3);
            int newFragment = intVal(p.get("LEGEND_FRAGMENT"), 0) + fragmentGranted;
            HashMap<String, Object> fragUp = new HashMap<>();
            fragUp.put("userName", userName);
            fragUp.put("legendFragment", newFragment);
            dao.updateUserProgress(fragUp);
            p.put("LEGEND_FRAGMENT", newFragment);
            result.put("fragmentGranted", fragmentGranted);
        }

        result.put("ok", true);
        result.put("job", equipClassToStore); // 그룹값(SWORD/PLATE/HELM 등)으로 변환된 값
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

        String equipClass = (String) r.get("job"); // 이미 그룹값(SWORD/PLATE/HELM 등)으로 변환된 값
        String part = (String) r.get("part");
        int grade = intVal(r.get("grade"), 1);
        // 그룹값은 "검용 무기"처럼 겹쳐 보이지 않게 부위명(partNameOf)을 생략(equipClassLabel만
        // 으로 이미 종류가 드러남), 마이그레이션 전 구 데이터는 기존 "전사용 투구" 문구 유지.
        String itemLabel = WEAPON_CLASS_NAME.containsKey(equipClass) ? (equipClassLabel(equipClass, part) + "용")
                : (equipClassLabel(equipClass, part) + "용 " + partNameOf(part));
        String result = "🎁 " + itemLabel + " ★" + grade + " 획득! (" + equipBonusText(part, grade) + ")";
        if (r.get("fragmentGranted") != null) {
            result += NL + "🧩 전설의조각 " + r.get("fragmentGranted") + "개도 함께 획득!";
        }
        return result;
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
        int fragmentTotal = 0;
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullEquipCore(userName, gacha, p, gachaId);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
            if (r.get("fragmentGranted") != null) fragmentTotal += intVal(r.get("fragmentGranted"), 0);
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 장비뽑기 (").append(success).append("/10)").append(NL);
        for (int g = 1; g <= 6; g++) {
            if (gradeCount[g] > 0) sb.append("★").append(g).append("×").append(gradeCount[g]).append("  ");
        }
        if (fragmentTotal > 0) sb.append(NL).append("🧩 전설의조각 ").append(fragmentTotal).append("개 획득!");
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 파티/장비 탭에서 확인하세요");
        return sb.toString();
    }

    /** [2026-09-15] "장비뽑기 말고 악세뽑기를 추가해서 목걸이/반지/팔찌 3종" 요청 --
     *  pullEquipCore와 거의 같은 구조(해금층 확인, 등급 굴림, 직업 무작위, 부위 무작위,
     *  INSERT). [2026-09-15 후속] "/이벤트지급으로도 지급할 수 있게" 요청으로 뽑기권
     *  (ACCESSORY_VOUCHER_T{tier}) 소모 경로도 pullEquipCore와 동일하게 추가 -- 쓸 수 있는
     *  뽑기권이 있으면 해금 여부를 건너뛰고(hasUsableAccessoryVoucher) PP 대신 그 권을
     *  소모한다(consumeAccessoryVoucher). 기존 equipWear/equipSynthesis/장비목록 등은 전부
     *  PART 문자열에 무관한 범용(generic) 로직이라 NECKLACE/RING/BRACELET도 코드 변경 없이
     *  그대로 착용/합성/목록조회가 된다. */
    private HashMap<String, Object> pullAccessoryCore(String userName, HashMap<String, Object> gacha, HashMap<String, Object> p, int tier) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (!hasUsableAccessoryVoucher(p, tier) && intVal(gacha.get("UNLOCK_FLOOR"), 0) > unlocked) {
            result.put("error", "아직 해금되지 않은 상자입니다.");
            return result;
        }
        if (!consumeAccessoryVoucher(userName, p, tier)) {
            PP cost = PP.of(((Number) gacha.get("COST_VALUE")).doubleValue(), strVal(gacha.get("COST_EXT"), ""));
            if (!deductPp(userName, p, cost)) {
                result.put("error", "PP가 부족합니다. (필요 " + cost.format() + " PP)");
                return result;
            }
        }

        int grade = rollGrade(gacha);
        // [2026-09-17] "악세사리3종은 전부 공용으로 바꿔줘" 요청 -- 예전엔 5직업 중 하나를 롤해
        // CLASS로 저장했지만(그 직업 전용), 이제 직업 무관 COMMON 하나로 고정한다(직업 롤 자체가
        // 더 이상 결과에 영향을 안 줘서 제거).
        String[] parts = { "NECKLACE", "RING", "BRACELET" };
        String part = parts[RND.nextInt(parts.length)];

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", "COMMON");
        e.put("part", part);
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        if (grade == 6) grantAchievement(userName, 22); // 기존 ★6 장비 업적(악세서리도 "장비"로 취급)

        result.put("ok", true);
        result.put("job", "COMMON");
        result.put("part", part);
        result.put("grade", grade);
        return result;
    }

    @Override
    @Transactional
    public String gachaAccessory(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        Integer realId = resolveGachaId("ACCESSORY", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        HashMap<String, Object> gacha = dao.selectGacha(realId);
        if (gacha == null || !"ACCESSORY".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 악세서리 상자입니다.";
        }
        HashMap<String, Object> r = pullAccessoryCore(userName, gacha, p, gachaId);
        if (r.get("error") != null) return (String) r.get("error");

        String equipClass = (String) r.get("job");
        String part = (String) r.get("part");
        int grade = intVal(r.get("grade"), 1);
        return "🎁 " + equipClassLabel(equipClass, part) + " ★" + grade + " 획득! (" + equipBonusText(part, grade) + ")";
    }

    @Override
    @Transactional
    public String gachaAccessoryTen(String userName, int gachaId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        Integer realId = resolveGachaId("ACCESSORY", gachaId);
        if (realId == null) return "존재하지 않는 번호입니다. /탑도움말에서 번호를 다시 확인하세요.";
        HashMap<String, Object> gacha = dao.selectGacha(realId);
        if (gacha == null || !"ACCESSORY".equals(strVal(gacha.get("GACHA_TYPE"), ""))) {
            return "존재하지 않는 악세서리 상자입니다.";
        }

        int[] gradeCount = new int[7];
        int success = 0;
        String stopReason = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Object> r = pullAccessoryCore(userName, gacha, p, gachaId);
            if (r.get("error") != null) {
                stopReason = (String) r.get("error");
                break;
            }
            success++;
            gradeCount[intVal(r.get("grade"), 1)]++;
        }

        StringBuilder sb = new StringBuilder("🎰 10연속 악세뽑기 (").append(success).append("/10)").append(NL);
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
        // [2026-09-08] "전투중엔 주사위변경도 안 되게 막아달라" 요청으로 한때 여기서 IN_COMBAT
        // 차단을 걸었었는데, [2026-09-09] "전투중에도 주사위변경 가능하게 해달라"는 후속
        // 요청으로 다시 허용(차단 제거).

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
     *  현황·비용 구조화 데이터.
     *  [2026-09-09 재설계] "adjust"가 지금 실제로 굴림에 적용되는 단일 값(-1..+6, 기본 0) --
     *  bonusLevel/malusLevel은 이제 "각 방향으로 얼마나 사뒀는지"(구매 진행도)만 의미하고,
     *  화면에서 "현재 선택됨" 강조는 반드시 이 adjust 값 하나와만 비교해서 표시해야 한다
     *  (예전엔 owned 단계 전부를 강조해서 두 트랙이 동시에 "선택된 것처럼" 보이는 문제가 있었음). */
    @Override
    public HashMap<String, Object> diceEnhanceInfo(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int unlocked = intVal(p.get("UNLOCKED_BLOCK"), 0);
        int bonus = intVal(p.get("DICE_MIN_BONUS"), 0);
        int malus = intVal(p.get("DICE_MIN_MALUS"), 0);
        int adjust = intVal(p.get("DICE_MIN_ADJUST"), 0);
        HashMap<String, Object> result = new HashMap<>();
        result.put("bonusLevel", bonus);
        result.put("malusLevel", malus);
        result.put("adjust", adjust);
        List<HashMap<String, Object>> bonusTiers = new ArrayList<>();
        for (int i = 0; i < DICE_BONUS_UNLOCK.length; i++) {
            int tier = i + 1;
            HashMap<String, Object> b = new HashMap<>();
            b.put("tier", tier);
            b.put("unlockFloor", DICE_BONUS_UNLOCK[i]);
            b.put("cost", DICE_BONUS_COST[i]);
            b.put("owned", bonus >= tier);
            b.put("buyable", bonus == tier - 1 && unlocked >= DICE_BONUS_UNLOCK[i]);
            b.put("selected", adjust == tier);
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
            m.put("selected", adjust == -tier);
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
        int adjust = intVal(p.get("DICE_MIN_ADJUST"), 0);
        StringBuilder sb = new StringBuilder(userName).append("님의 주사위 강화 현황," + NL);
        sb.append("🎲 지금 적용 중인 최소 눈금 조정: ").append(adjust > 0 ? "+" + adjust : String.valueOf(adjust)).append(NL);
        sb.append("🎲 주사위 강화(구매 진행도, 최소 눈금 +): ").append(bonus).append("/").append(DICE_BONUS_UNLOCK.length).append("단계").append(NL);
        if (bonus < DICE_BONUS_UNLOCK.length) {
            int nf = DICE_BONUS_UNLOCK[bonus];
            sb.append("  다음 단계(+").append(bonus + 1).append("): ")
              .append(unlocked >= nf ? "구매 가능, " : nf + "층 마을 도착 필요, ")
              .append(PP.of(DICE_BONUS_COST[bonus], "").format()).append(" PP").append(NL);
        }
        sb.append("🎲 마이너스 주사위(구매 진행도, 최소 눈금 -): ").append(malus).append("/").append(DICE_MALUS_UNLOCK.length).append("단계").append(NL);
        if (malus < DICE_MALUS_UNLOCK.length) {
            int nf = DICE_MALUS_UNLOCK[malus];
            sb.append("  다음 단계(-").append(malus + 1).append("): ")
              .append(unlocked >= nf ? "구매 가능, " : nf + "층 마을 도착 필요, ")
              .append(PP.of(DICE_MALUS_COST[malus], "").format()).append(" PP").append(NL);
        }
        sb.append("/주사위강화 구매 로 강화 다음 단계, /마이너스주사위 구매 로 마이너스 다음 단계 구매 -- 둘 다 사둔 단계 중 딱 하나만 골라 적용됨(구매 즉시 그 단계로 전환, 최대 눈금은 그대로)");
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
        // [2026-09-08] "전투중엔 주사위변경도 안 되게 막아달라(최대/최소 둘 다)" 요청으로 걸었던
        // IN_COMBAT 차단 -- [2026-09-09] "전투중에도 주사위변경 가능하게 해달라"는 후속 요청으로
        // diceShop()과 마찬가지로 다시 허용(차단 제거).
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
        // [2026-09-09] 구매한 단계를 즉시 "적용 중"으로 선택(DICE_MIN_ADJUST) -- 방금 산 걸
        // 바로 켜주는 게 자연스럽고, 다른 방향에 이미 선택돼 있던 값은 여기서 밀려난다(단일선택).
        int newAdjust = isBonus ? nextTier : -nextTier;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(isBonus ? "diceMinBonus" : "diceMinMalus", nextTier);
        up.put("diceMinAdjust", newAdjust);
        dao.updateUserProgress(up);
        return isBonus
                ? "🎲 주사위 강화 " + nextTier + "단계 구매 + 적용! (최소 눈금 +" + nextTier + ", 최대 눈금은 그대로)"
                : "🎲 마이너스 주사위 " + nextTier + "단계 구매 + 적용! (최소 눈금 -" + nextTier + ", 최대 눈금은 그대로)";
    }

    /** [2026-09-09] "최소 눈금 조정은 최대주사위처럼 딱 1개만 선택되게 해달라, 0은 구매 없이도
     *  항상 고를 수 있어야 한다" 요청 -- 이미 구매(해금)해둔 단계들 중 하나로 무료로 전환한다
     *  (신규 구매가 아니라 "이미 산 것들 중 지금 뭘 켤지" 선택). 0은 항상 허용(기본값, 아무
     *  조정 없음). value>0은 DICE_MIN_BONUS까지, value<0은 -DICE_MIN_MALUS까지만 허용. */
    @Override
    @Transactional
    public String selectDiceMinAdjust(String userName, int value) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int bonus = intVal(p.get("DICE_MIN_BONUS"), 0);
        int malus = intVal(p.get("DICE_MIN_MALUS"), 0);
        if (value != 0 && (value > bonus || value < -malus)) {
            return "🔒 아직 구매하지 않은 단계입니다. (강화 " + bonus + "단계, 마이너스 " + malus + "단계 보유 중)";
        }
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("diceMinAdjust", value);
        dao.updateUserProgress(up);
        return "🎲 최소 눈금 조정을 " + (value > 0 ? "+" + value : String.valueOf(value)) + "로 변경했습니다. (최대 눈금은 그대로)";
    }

    @Override
    public int autoHuntKillsPerHour() {
        return AUTO_HUNT_KILLS_PER_HOUR;
    }

    @Override
    public String currentFloorMonsterName(int floor) {
        boolean isBossFloor = floor % 10 == 9;
        HashMap<String, Object> mon = dao.selectMonster(blockNo(floor), isBossFloor ? "Y" : "N");
        if (mon == null) return null;
        return floorMonsterName(floor, mon);
    }

    /** [2026-09-21] 전투화면 재설계(스탯표 POWER/GUARD)용 -- 이 층 몬스터의 실제 전투 ATK/DEF
     *  (V2 오버레이/하드코어 스케일 전부 반영된 값). null이면 [atk, def] 순서. */
    @Override
    public int[] currentFloorMonsterAtkDef(int floor) {
        boolean isBossFloor = floor % 10 == 9;
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(blockNo(floor), isBossFloor ? "Y" : "N"), floor);
        if (mon == null) return null;
        return new int[]{ (int) Math.round(((Number) mon.get("ATK_VALUE")).doubleValue()), (int) Math.round(((Number) mon.get("DEF_VALUE")).doubleValue()) };
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
    // [2026-09-15] 악세서리(목걸이/반지/팔찌) 3종 추가 -- computeEffectiveStat()의 NECKLACE/
    // RING/BRACELET 분기 참고.
    private String partNameOf(String part) {
        switch (part) {
            case "HELMET": return "투구";
            case "WEAPON": return "무기";
            case "NECKLACE": return "목걸이";
            case "RING": return "반지";
            case "BRACELET": return "팔찌";
            default: return "갑옷"; // ARMOR
        }
    }

    /** 장비 등급/부위별 스탯 보너스 표기 (예: "ATK +13 / +10%") — EQUIP_BONUS[grade-1] 기준.
     *  [2026-09-15] 악세서리는 두 부위(무기/갑옷/투구 중 둘)의 절반씩을 동시에 주므로 표기도
     *  "스탯A +x/+y% · 스탯B +x/+y%" 형태로 두 줄 합쳐서 보여준다. */
    private String equipBonusText(String part, int grade) {
        double[] b = EQUIP_BONUS[grade - 1];
        if ("NECKLACE".equals(part)) { // 무기(ATK)+갑옷(DEF) 절반씩
            return "ATK +" + Math.round(b[2] / 2) + "/+" + Math.round(b[3] * 50) + "% · DEF +" + Math.round(b[4] / 2) + "/+" + Math.round(b[5] * 50) + "%";
        }
        if ("RING".equals(part)) { // 무기(ATK)+투구(HP) 절반씩
            return "ATK +" + Math.round(b[2] / 2) + "/+" + Math.round(b[3] * 50) + "% · HP +" + Math.round(b[0] / 2) + "/+" + Math.round(b[1] * 50) + "%";
        }
        if ("BRACELET".equals(part)) { // 갑옷(DEF)+투구(HP) 절반씩
            return "DEF +" + Math.round(b[4] / 2) + "/+" + Math.round(b[5] * 50) + "% · HP +" + Math.round(b[0] / 2) + "/+" + Math.round(b[1] * 50) + "%";
        }
        int fixedIdx = "HELMET".equals(part) ? 0 : "WEAPON".equals(part) ? 2 : 4;
        int pctIdx = fixedIdx + 1;
        String statName = "HELMET".equals(part) ? "HP" : "WEAPON".equals(part) ? "ATK" : "DEF";
        return statName + " +" + (int) b[fixedIdx] + " / +" + Math.round(b[pctIdx] * 100) + "%";
    }

    /** 웹 SPA 캐릭터 상세 카드용 — 장비/스탯구매 보너스까지 반영한 유효 스탯. 대상이 없으면
     *  전부 0. [hp, atk, def, hpBase, atkBase, defBase] -- base는 한계돌파 배율만 뺀 값(장비/
     *  스탯구매는 그대로 포함)이라, hp-hpBase가 곧 "한계돌파로 인한 증가분"이다.
     *  [2026-09-14] "동료편성 화면에 한계돌파0성스탯(+돌파로인한스탯)처럼 표기해달라" 요청으로
     *  base 3개를 추가 반환 -- 화면(tower_view.jsp)에서 hp-hpBase 형태로 보너스만 따로 계산. */
    @Override
    public int[] companionEffectiveStat(String userName, int companionId) {
        HashMap<String, Object> target = null;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("COMPANION_ID"), -1) == companionId) { target = c; break; }
        }
        if (target == null) return new int[]{ 0, 0, 0, 0, 0, 0 };
        String job = strVal(target.get("CLASS"), "WARRIOR");
        int grade = intVal(target.get("GRADE"), 1);
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(companionId);
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int limitBreak = intVal(target.get("LIMIT_BREAK"), 0);
        int[] eff = computeEffectiveStat(job, grade, equips, userStat, limitBreak);
        int[] effBase = computeEffectiveStat(job, grade, equips, userStat, 0);
        return new int[]{ eff[0], eff[1], eff[2], effBase[0], effBase[1], effBase[2] };
    }

    /** [2026-09-14] "동료편성 전체보기 카드마다 공/방/체 + 한계돌파 보너스를 다 보여달라"
     *  요청 -- companionEffectiveStat()을 동료마다 따로 부르면 N+1 조회(동료 수만큼 장비/
     *  유저스탯 재조회)가 되므로, 유저 스탯과 전체 장비 목록을 한 번씩만 조회해 동료
     *  COMPANION_ID별로 묶어둔 뒤 한 바퀴만 돈다. 반환하는 각 동료 맵은 selectUserCompanions
     *  원본 키(CLASS/GRADE/LIMIT_BREAK/...)에 EFF_HP/EFF_ATK/EFF_DEF/EFF_HP_BASE/EFF_ATK_BASE/
     *  EFF_DEF_BASE 6개 필드를 더해서 그대로 돌려준다(원본 객체를 그대로 mutate). */
    @Override
    public List<HashMap<String, Object>> companionsWithEffectiveStats(String userName) {
        List<HashMap<String, Object>> companions = dao.selectUserCompanions(userName);
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        HashMap<Integer, List<HashMap<String, Object>>> equipsByCompanion = new HashMap<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            Object cidObj = e.get("EQUIPPED_COMPANION_ID");
            if (cidObj == null) continue;
            int cid = intVal(cidObj, -1);
            equipsByCompanion.computeIfAbsent(cid, k -> new ArrayList<>()).add(e);
        }
        for (HashMap<String, Object> c : companions) {
            String job = strVal(c.get("CLASS"), "WARRIOR");
            int grade = intVal(c.get("GRADE"), 1);
            int limitBreak = intVal(c.get("LIMIT_BREAK"), 0);
            int cid = intVal(c.get("COMPANION_ID"), 0);
            List<HashMap<String, Object>> equips = equipsByCompanion.getOrDefault(cid, Collections.emptyList());
            int[] eff = computeEffectiveStat(job, grade, equips, userStat, limitBreak);
            int[] effBase = computeEffectiveStat(job, grade, equips, userStat, 0);
            c.put("EFF_HP", eff[0]); c.put("EFF_ATK", eff[1]); c.put("EFF_DEF", eff[2]);
            c.put("EFF_HP_BASE", effBase[0]); c.put("EFF_ATK_BASE", effBase[1]); c.put("EFF_DEF_BASE", effBase[2]);
        }
        return companions;
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
                sb.append(idx++).append(". ").append(equipFullLabel(strVal(e.get("CLASS"), ""), part))
                  .append(" ★").append(grade)
                  .append(" (").append(equipBonusText(part, grade)).append(")").append(NL);
            }
        }
        if (!equipped.isEmpty()) {
            sb.append("[장착중]").append(NL);
            for (HashMap<String, Object> e : equipped) {
                String part = strVal(e.get("PART"), "");
                int grade = intVal(e.get("GRADE"), 1);
                Object cid = e.get("EQUIPPED_COMPANION_ID");
                sb.append("- ").append(equipFullLabel(strVal(e.get("CLASS"), ""), part))
                  .append(" ★").append(grade)
                  .append(" (").append(equipBonusText(part, grade)).append(")")
                  .append(" → ").append(companionLabel.getOrDefault(((Number) cid).intValue(), "?"))
                  .append(NL);
            }
        }
        sb.append("/장비장착 N [M], /장비합성 N (둘 다 위 [미착용] 번호 기준), /장비일괄합성 (합성 가능한 조합 전부 한번에)");
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
                sb.append(idx++).append(". ").append(equipFullLabel(strVal(e.get("CLASS"), ""), part))
                  .append(" ★").append(grade)
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

        // [2026-09-17] 무기(PART=WEAPON)는 CLASS가 직업 1:1이 아니라 무기군(SWORD/STAFF/BOW)일
        // 수 있어서, weaponAllowedJobs()로 "이 CLASS 값을 착용 가능한 직업 집합"을 구해 그 집합에
        // 포함되는지로 판정한다(헬멧/갑옷/악세서리는 그 CLASS가 곧 직업명 그대로라 singleton
        // 집합이 되어 기존과 동일한 1:1 동작 -- weaponAllowedJobs()가 구/신 데이터 모두 처리).
        java.util.Set<String> allowedJobs = weaponAllowedJobs(equipClass);
        HashMap<String, Object> targetCompanion = null;
        if (companionIdx != null) {
            if (companionIdx < 1 || companionIdx > party.size()) return "잘못된 동료 번호입니다. /파티편성을 확인하세요.";
            targetCompanion = party.get(companionIdx - 1);
        } else {
            for (HashMap<String, Object> c : party) {
                if (allowedJobs.contains(strVal(c.get("CLASS"), ""))) { targetCompanion = c; break; }
            }
        }
        if (targetCompanion == null) return "장착할 동료를 찾지 못했습니다 (착용 가능한 직업의 파티원이 필요합니다).";
        if (!allowedJobs.contains(strVal(targetCompanion.get("CLASS"), ""))) return "이 장비는 " + equipClassLabel(equipClass, part) + " 전용입니다.";

        int companionId = intVal(targetCompanion.get("COMPANION_ID"), 0);
        // [2026-09-18] 장착으로 EFF_HP가 바뀌어도 비율 유지(rescaleHpForEquipChange 참고) --
        // 장착 "전" 최대체력을 먼저 구해둔다.
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int oldMaxHp = effMaxHpOf(targetCompanion, userStat);
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
        rescaleHpForEquipChange(targetCompanion, userStat, oldMaxHp);

        int grade = intVal(equip.get("GRADE"), 1);
        // [2026-09-17 버그수정] 여기서 표시할 "누구에게 장착했는지"는 equipClass(장비 쪽,
        // 무기 통합 후 SWORD 등일 수 있음)가 아니라 실제 대상 동료 본인의 직업이어야 한다
        // (예전엔 equipClass==동료 직업이 항상 1:1이라 안 갈렸지만, 검/지팡이가 두 직업을
        // 겸용하게 되면서 "검(아츠시)에게..." 같은 오표시가 날 수 있었다).
        String targetJob = JOB_NAME.getOrDefault(strVal(targetCompanion.get("CLASS"), ""), "?");
        String targetName = strVal(targetCompanion.get("NAME"), targetJob);
        // 그룹 통합된 장비는 "무기 ★4"보다 "검 ★4"처럼 구체적으로 -- equipClass(장비 쪽 CLASS)를 그대로 쓴다.
        boolean grouped = WEAPON_CLASS_NAME.containsKey(equipClass) || "COMMON".equals(equipClass);
        String itemLabel = grouped ? equipClassLabel(equipClass, part) : partNameOf(part);
        return "🎽 " + targetJob + "(" + targetName + ")에게 " + itemLabel + " ★" + grade
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

        String job = JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "?");
        String name = strVal(target.get("NAME"), job);
        int unequipped = unequipAllForCompanion(target);
        if (unequipped == 0) return job + "(" + name + ")은(는) 착용 중인 장비가 없습니다.";
        return "🧺 " + job + "(" + name + ")의 장비 " + unequipped + "개를 전부 해제했습니다. (/장비목록의 [미착용]으로 이동)";
    }

    private static final String[] EQUIP_ALL_PARTS = { "WEAPON", "HELMET", "ARMOR", "NECKLACE", "RING", "BRACELET" };

    /** [2026-09-18] "동료 1명에 대해 장착할수있는 장비 일괄 장착 기능" 요청 -- 6부위 각각
     *  이 동료 직업이 쓸 수 있는 미착용 장비 중 최고 등급을 찾아, 지금 착용 중인 것보다
     *  등급이 높을 때만 교체한다(같거나 낮으면 그 부위는 건드리지 않음 -- 굳이 같은 등급으로
     *  바꿔치기해서 DB만 흔들 이유가 없음). HP 비율 유지는 equipWear와 동일하게 전/후
     *  EFF_HP를 한 번만 비교(부위 여러 개가 한꺼번에 바뀌어도 재계산은 마지막에 1회). */
    @Override
    @Transactional
    public String equipBestAll(String userName, int companionIdx) {
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
        String job = strVal(target.get("CLASS"), "");
        int companionId = intVal(target.get("COMPANION_ID"), 0);
        String targetJob = JOB_NAME.getOrDefault(job, "?");
        String targetName = strVal(target.get("NAME"), targetJob);

        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int oldMaxHp = effMaxHpOf(target, userStat);

        HashMap<String, Integer> curGradeByPart = new HashMap<>();
        for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
            curGradeByPart.put(strVal(e.get("PART"), ""), intVal(e.get("GRADE"), 0));
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }

        List<String> upgraded = new ArrayList<>();
        for (String part : EQUIP_ALL_PARTS) {
            HashMap<String, Object> best = null;
            for (HashMap<String, Object> e : unequipped) {
                if (!part.equals(strVal(e.get("PART"), ""))) continue;
                if (!weaponAllowedJobs(strVal(e.get("CLASS"), "")).contains(job)) continue;
                if (best == null || intVal(e.get("GRADE"), 0) > intVal(best.get("GRADE"), 0)) best = e;
            }
            if (best == null) continue;
            int bestGrade = intVal(best.get("GRADE"), 0);
            if (bestGrade <= curGradeByPart.getOrDefault(part, 0)) continue; // 이미 같거나 더 좋음

            for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
                if (!part.equals(strVal(e.get("PART"), ""))) continue;
                HashMap<String, Object> unwear = new HashMap<>();
                unwear.put("equipId", intVal(e.get("EQUIP_ID"), 0));
                unwear.put("equippedCompanionId", null);
                dao.updateEquipEquippedCompanion(unwear);
            }
            HashMap<String, Object> wear = new HashMap<>();
            wear.put("equipId", intVal(best.get("EQUIP_ID"), 0));
            wear.put("equippedCompanionId", companionId);
            dao.updateEquipEquippedCompanion(wear);
            String bestClass = strVal(best.get("CLASS"), "");
            boolean grouped = WEAPON_CLASS_NAME.containsKey(bestClass) || "COMMON".equals(bestClass);
            String itemLabel = grouped ? equipClassLabel(bestClass, part) : partNameOf(part);
            upgraded.add(itemLabel + " ★" + bestGrade);
        }

        rescaleHpForEquipChange(target, userStat, oldMaxHp);

        if (upgraded.isEmpty()) return targetJob + "(" + targetName + ")에게 장착할 더 나은 장비가 없습니다.";
        return "🎽 " + targetJob + "(" + targetName + ") 일괄장착 완료! " + String.join(", ", upgraded);
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
        // [2026-09-18] 해제로 EFF_HP가 바뀌어도 비율 유지(rescaleHpForEquipChange 참고) --
        // 이 장비를 착용 중인 동료를 찾아 해제 "전" 최대체력을 먼저 구해둔다.
        int ownerCompanionId = intVal(found.get("EQUIPPED_COMPANION_ID"), 0);
        HashMap<String, Object> owner = null;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("COMPANION_ID"), -1) == ownerCompanionId) { owner = c; break; }
        }
        HashMap<String, Object> ownerUserStat = owner == null ? null : dao.selectUserStat(userName);
        int ownerOldMaxHp = owner == null ? 0 : effMaxHpOf(owner, ownerUserStat);

        HashMap<String, Object> unwear = new HashMap<>();
        unwear.put("equipId", equipId);
        unwear.put("equippedCompanionId", null);
        dao.updateEquipEquippedCompanion(unwear);
        if (owner != null) rescaleHpForEquipChange(owner, ownerUserStat, ownerOldMaxHp);
        String part = strVal(found.get("PART"), "");
        int grade = intVal(found.get("GRADE"), 1);
        String foundClass = strVal(found.get("CLASS"), "");
        boolean grouped = WEAPON_CLASS_NAME.containsKey(foundClass) || "COMMON".equals(foundClass);
        String itemLabel = grouped ? equipClassLabel(foundClass, part) : partNameOf(part);
        return "🧺 " + itemLabel + " ★" + grade + " 을(를) 해제했습니다. (미착용 목록으로 이동)";
    }

    /** 선택권 등급(3/4/5)에 대응하는 진행상태 컬럼/필드 접미사("" 또는 "G4"/"G5"). */
    private String ticketSuffix(int grade) {
        return grade == 3 ? "" : "G" + grade;
    }

    /**
     * ★N 동료 선택권 사용(웹 UI 전용). 등급(3/4/5, 구간에 따라 지급된 것 그대로)/직업이
     * 확정이라 가챠와 달리 실패가 없다.
     * [2026-09-18 수정] "업적보상 캐릭선택권으로 받은 캐릭터가 한계돌파가 안되고 별도
     * 캐릭터로 들어온다" 문의 -- 예전엔 "[알려진 단순화] 선택권은 무상 보상이라 중복이어도
     * 이름만 다시 랜덤일 뿐 손해가 아니라서 생략"이라며 가챠의 중복판정(findOwnedCompanion)을
     * 아예 안 태웠는데, 실제로는 이미 보유한 (직업,이름)과 겹치면 완전히 별개의 동료 개체가
     * 하나 더 생겨서(같은 이름 카드가 파티/합성 목록에 2장) 유저 입장에선 "왜 한계돌파가
     * 안 되고 복제됐지" 하는 명백한 버그로 보였다. 가챠와 동일하게 중복이면 그 개체의
     * LIMIT_BREAK를 올리고(최대치면 PP 환급)로 통일.
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

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, have - 1);

        HashMap<String, Object> ownedDupe = findOwnedCompanion(userName, job, name);
        if (ownedDupe != null) {
            int curLimitBreak = intVal(ownedDupe.get("LIMIT_BREAK"), 0);
            dao.updateUserProgress(up);
            if (curLimitBreak < LIMIT_BREAK_MAX) {
                int newLimitBreak = curLimitBreak + 1;
                HashMap<String, Object> lbUp = new HashMap<>();
                lbUp.put("companionId", intVal(ownedDupe.get("COMPANION_ID"), 0));
                lbUp.put("limitBreak", newLimitBreak);
                dao.updateCompanionLimitBreak(lbUp);
                return "🔺 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 선택권 사용)" + NL
                        + "한계돌파+" + newLimitBreak + " 달성! (전체 스탯 +" + Math.round(limitBreakPct(newLimitBreak) * 100) + "%)"
                        + " (남은 ★" + grade + " 선택권 " + (have - 1) + "장)";
            }
            PP dupeBonus = PP.fromPP(COMPANION_DUPE_REFUND[grade - 1]);
            addPp(userName, p, dupeBonus);
            return "🔁 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 선택권 사용)" + NL
                    + "한계돌파가 이미 최대(+" + LIMIT_BREAK_MAX + ")라 " + dupeBonus.format() + " PP로 환급되었습니다."
                    + " (남은 ★" + grade + " 선택권 " + (have - 1) + "장)";
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
        // [2026-09-17] 무기 통합 -- 플레이어는 그대로 직업을 고르지만(기존 UX 유지), 실제로
        // 저장되는 CLASS는 그 직업이 속한 무기군(검/지팡이/활)이다. 예를 들어 전사를 골라도
        // 도적을 골라도 결과는 똑같이 CLASS=SWORD로 저장되어 둘 다 착용 가능한 검 1자루가 된다.
        String weaponClass = JOB_TO_WEAPON_CLASS.getOrDefault(job, job);

        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", weaponClass);
        e.put("part", "WEAPON");
        e.put("grade", grade);
        e.put("equippedCompanionId", null);
        dao.insertEquip(e);

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put(field, have - 1);
        dao.updateUserProgress(up);

        return "🎉 " + equipClassLabel(weaponClass, "WEAPON") + " ★" + grade + " (" + equipBonusText("WEAPON", grade) + ") 획득! (선택권 사용, 남은 ★" + grade + " 선택권 " + (have - 1) + "장)";
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
        return "✨ 합성 성공! " + equipFullLabel(clazz, part) + " ★" + (grade + 1)
                + " (" + equipBonusText(part, grade + 1) + ") 획득!";
    }

    // [2026-09-18] "조각 10개를 모으면 전설제작 할수있고, 전설 제작 성공률은 30%.
    // 랜덤제작만 만들고싶어" 요청 -- 그때는 성공 시 로스터 전체에서 무작위 1개였다.
    // [2026-09-21 재설계] "전설제작창에서 여러아이템 중 선택하여 제작버튼을 누르면..." 요청으로
    // "결과 랜덤"에서 "제작 대상을 직접 고르고, 그 대상에 대해 성공/실패만 확률로" 방식으로
    // 변경(로스터가 늘어날수록 "원하는 종류인지도 랜덤"이면 UX가 나빠지므로). 같은 날 후속
    // 메시지("일반사용자에겐 아직 제작부분은 오픈하지 말고")로 실제 오픈 전까지는
    // NO_COOLDOWN_YN(기존 관리자/테스트 계정 플래그) 보유 계정만 사용 가능하도록 막아둔다 --
    // 조각 드랍/보유는 이미 일반 유저에게도 보이므로 제작 UI/커맨드 자체는 그대로 두되 진입
    // 시점에 여기서 차단.
    @Override
    public List<HashMap<String, Object>> legendaryRoster() {
        return dao.selectLegendaryMasterList();
    }

    @Override
    @Transactional
    public String craftLegendary(String userName, int legendaryId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if (!"Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) {
            return "🔒 전설제작은 아직 준비 중인 기능입니다.";
        }
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 전설제작을 할 수 없습니다.";
        }
        HashMap<String, Object> target = dao.selectLegendaryMaster(legendaryId);
        if (target == null) return "존재하지 않는 전설장비입니다.";
        int fragment = intVal(p.get("LEGEND_FRAGMENT"), 0);
        if (fragment < LEGEND_CRAFT_COST) {
            return "전설의조각이 부족합니다. (보유 " + fragment + "개 / 필요 " + LEGEND_CRAFT_COST + "개)";
        }

        int remaining = fragment - LEGEND_CRAFT_COST;
        HashMap<String, Object> fragUp = new HashMap<>();
        fragUp.put("userName", userName);
        fragUp.put("legendFragment", remaining);
        dao.updateUserProgress(fragUp);
        p.put("LEGEND_FRAGMENT", remaining);

        boolean success = RND.nextInt(100) < LEGEND_CRAFT_SUCCESS_PCT;
        String itemName = strVal(target.get("ITEM_NAME"), "");
        if (!success) {
            return "💨 [" + itemName + "] 전설제작 실패... 전설의조각 " + LEGEND_CRAFT_COST + "개를 소모했습니다. (보유 " + remaining + "개)";
        }

        String clazz = strVal(target.get("CLASS"), "");
        String part = strVal(target.get("PART"), "");
        HashMap<String, Object> e = new HashMap<>();
        e.put("userName", userName);
        e.put("class", clazz);
        e.put("part", part);
        e.put("grade", 7);
        e.put("equippedCompanionId", null);
        e.put("legendaryId", legendaryId);
        dao.insertEquip(e);

        return "✨✨ 전설제작 성공! [" + itemName + "] ★7 " + equipClassLabel(clazz, part)
                + " 획득! (" + strVal(target.get("FLAVOR_TEXT"), "") + ")";
    }

    // [2026-09-21] "전설은 한번 만들어지면 전설의조각 9개로 바꿀수있도록도 해줘" 요청 -- 제작
    // 비용(10개)보다 1개 적게 돌려줘서(9개) 완전한 무손실 순환(만들고 부수고 다시 만들고...)은
    // 안 되게 하는 조각 싱크. equipSynthesis()와 동일하게 "미착용 장비 목록(N번)" 인덱스로
    // 대상을 지정한다.
    @Override
    @Transactional
    public String disenchantLegendary(String userName, int equipIdx) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 분해할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        HashMap<String, Object> equip = unequipped.get(equipIdx - 1);
        if (intVal(equip.get("GRADE"), 1) != 7) return "★7 전설장비만 조각으로 분해할 수 있습니다.";

        dao.deleteEquip(intVal(equip.get("EQUIP_ID"), 0));
        int newFragment = intVal(progress.get("LEGEND_FRAGMENT"), 0) + LEGEND_DISENCHANT_REFUND;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("legendFragment", newFragment);
        dao.updateUserProgress(up);

        Object legIdObj = equip.get("LEGENDARY_ID");
        String itemName = "전설장비";
        if (legIdObj != null) {
            HashMap<String, Object> meta = dao.selectLegendaryMaster(intVal(legIdObj, 0));
            if (meta != null) itemName = strVal(meta.get("ITEM_NAME"), itemName);
        }
        return "🧩 [" + itemName + "] 분해 완료! 전설의조각 " + LEGEND_DISENCHANT_REFUND + "개 획득 (보유 " + newFragment + "개)";
    }

    // [2026-09-21] "이전버전은 v1, 지금은v2로 해서 유저가 선택한걸 띄워주도록 하자. 전투화면
    // v1,v2는 db에저장해서 선택한걸 저장하도록 해줘" 요청 -- 전투화면 UI 버전(포켓몬 스타일
    // 구버전=V1, 삼국지 대전화면 신버전=V2) 선호를 유저별로 저장(TBOT_S5_USER_PROGRESS.
    // BATTLE_SCREEN_VERSION, 기본값 V2).
    @Override
    public String setBattleScreenVersion(String userName, String version) {
        String v = "V1".equalsIgnoreCase(version) ? "V1" : "V2"; // V1이 아니면 전부 V2로 정규화
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("battleScreenVersion", v);
        dao.updateUserProgress(up);
        return "🖼️ 전투화면을 " + v + "로 변경했습니다.";
    }

    /** [2026-09-12] "장비 일괄합성 기능을 만들고 싶다" 요청 -- 미착용 장비 전체를 훑어서
     *  합성 가능한(동일 클래스/부위/등급 3개 이상, ★6 미만) 조합을 전부 반복 합성한다.
     *  등급별로 낮은 등급부터 순서대로 처리하면서, 합성으로 새로 생긴 장비를 그 다음(더 높은
     *  등급) 처리 시점에 이미 보유 중이던 것과 합쳐서 다시 검사하는 방식으로 한 번의 순회
     *  안에서 연쇄 합성(예: ★1 9개 → ★2 3개 → ★3 1개)까지 자연스럽게 처리된다.
     *  실제 DB DELETE/INSERT는 등급 1~5(각 3개 소모, 1개 생성)까지만 발생하고, ★6은 상한이라
     *  받기만 하고 더 소모되지 않는다. */
    @Override
    @Transactional
    public String equipSynthesisAll(String userName) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 장비를 합성할 수 없습니다.";
        }
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }

        // 실제 보유 중인 장비의 EQUIP_ID를 (클래스|부위|등급) 키로 모아둔다 -- 소모(삭제) 대상은
        // 여기서만 꺼내 쓰고, 합성으로 "새로 생기는" 수량은 아래 virtualCredit으로 별도 관리한다.
        Map<String, List<Integer>> realIdsByKey = new LinkedHashMap<>();
        Set<String> classPartCombos = new LinkedHashSet<>();
        for (HashMap<String, Object> e : unequipped) {
            String clazz = strVal(e.get("CLASS"), "");
            String part = strVal(e.get("PART"), "");
            int grade = intVal(e.get("GRADE"), 1);
            realIdsByKey.computeIfAbsent(clazz + "|" + part + "|" + grade, k -> new ArrayList<>())
                    .add(intVal(e.get("EQUIP_ID"), 0));
            classPartCombos.add(clazz + "|" + part);
        }

        List<Integer> toDelete = new ArrayList<>();
        Map<String, Integer> virtualCredit = new LinkedHashMap<>(); // key -> 합성으로 생겼지만 아직 미확정인 수량
        int totalSynthCount = 0;

        // [2026-09-17] "5성이상은 일괄합성이 안되도록 해줘" 요청 -- 상한을 5에서 4로 낮춰서
        // ★5 재료(3개 이상 있어도)는 이 반복문이 아예 쳐다보지 않는다. ★4까지 조합해 ★5를
        // "만드는" 것까지는 그대로 되지만(정상적인 성장 경로), 그렇게 만들어졌거나 원래 갖고
        // 있던 ★5를 또 3개씩 모아 ★6으로 자동 소모하는 것만 막는다(개별 "합성" 버튼으로
        // 직접 누르는 수동 합성은 equipSynthesis()가 따로 처리하므로 영향 없음 -- 요청이
        // "일괄합성"만 콕 집어 말했으므로 수동은 그대로 둠).
        for (String cp : classPartCombos) {
            String[] cpArr = cp.split("\\|", 2);
            String clazz = cpArr[0], part = cpArr[1];
            for (int grade = 1; grade <= 4; grade++) {
                String key = clazz + "|" + part + "|" + grade;
                List<Integer> realIds = realIdsByKey.getOrDefault(key, Collections.emptyList());
                int realPos = 0; // realIds 중 아직 소모(삭제 예약)하지 않은 다음 인덱스
                int available = realIds.size() + virtualCredit.getOrDefault(key, 0);
                while (available >= 3) {
                    int fromReal = Math.min(3, realIds.size() - realPos);
                    for (int i = 0; i < fromReal; i++) toDelete.add(realIds.get(realPos++));
                    int fromVirtual = 3 - fromReal;
                    if (fromVirtual > 0) virtualCredit.merge(key, -fromVirtual, Integer::sum);

                    String nextKey = clazz + "|" + part + "|" + (grade + 1);
                    virtualCredit.merge(nextKey, 1, Integer::sum);
                    available -= 3;
                    totalSynthCount++;
                }
            }
        }

        if (totalSynthCount == 0) {
            return "합성 가능한 조합이 없습니다. (동일 등급/부위/직업 미착용 장비가 3개 이상 있어야 합성됩니다)";
        }

        for (Integer equipId : toDelete) {
            dao.deleteEquip(equipId);
        }
        // 합성으로 순생성된(더 이상 소모되지 않고 남은) 장비만 실제로 DB에 새로 넣는다.
        List<String> resultLines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : virtualCredit.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) continue;
            String[] parts = entry.getKey().split("\\|", 3);
            String clazz = parts[0], part = parts[1];
            int grade = Integer.parseInt(parts[2]);
            for (int i = 0; i < qty; i++) {
                HashMap<String, Object> e = new HashMap<>();
                e.put("userName", userName);
                e.put("class", clazz);
                e.put("part", part);
                e.put("grade", grade);
                e.put("equippedCompanionId", null);
                dao.insertEquip(e);
            }
            resultLines.add(equipFullLabel(clazz, part) + " ★" + grade
                    + " (" + equipBonusText(part, grade) + ") x" + qty);
        }

        grantAchievement(userName, 12);
        StringBuilder sb = new StringBuilder("✨ 일괄합성 완료! 총 ").append(totalSynthCount).append("회 합성.").append(NL);
        sb.append("[획득]").append(NL);
        for (String line : resultLines) sb.append("- ").append(line).append(NL);
        return sb.toString().trim();
    }
}
