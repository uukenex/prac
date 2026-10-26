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
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final String[] JOB_KEYS = { "WARRIOR", "MAGE", "ROGUE", "ARCHER", "PRIEST", "SHAMAN" };

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
        put("SHAMAN",  new double[]{ 1.1, 0.7, 1.4 }); // [2026-10-20] 무녀 -- 도사와 비슷한 서포터 체형(체력/방어 높고 공격 낮음)
    }};

    private static final HashMap<String, String> JOB_NAME = new HashMap<String, String>() {{
        put("WARRIOR", "전사"); put("MAGE", "마법사"); put("ROGUE", "도적");
        put("ARCHER", "궁수");  put("PRIEST", "도사"); put("SHAMAN", "무녀");
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
        put("STAFF", new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST", "SHAMAN")));
        put("BOW",   new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("PLATE", new java.util.HashSet<>(java.util.Arrays.asList("WARRIOR", "ROGUE")));
        put("ROBE",  new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST", "SHAMAN")));
        put("JACKET", new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("HELM",  new java.util.HashSet<>(java.util.Arrays.asList("WARRIOR", "ROGUE")));
        put("HEADBAND", new java.util.HashSet<>(java.util.Arrays.asList("MAGE", "PRIEST", "SHAMAN")));
        put("PLUME", new java.util.HashSet<>(java.util.Arrays.asList("ARCHER")));
        put("COMMON", new java.util.HashSet<>(java.util.Arrays.asList(JOB_KEYS))); // 악세서리 -- 전 직업 착용 가능
    }};
    private static final HashMap<String, String> JOB_TO_WEAPON_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "SWORD"); put("ROGUE", "SWORD");
        put("MAGE", "STAFF");    put("PRIEST", "STAFF"); put("SHAMAN", "STAFF");
        put("ARCHER", "BOW");
    }};
    private static final HashMap<String, String> JOB_TO_ARMOR_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "PLATE"); put("ROGUE", "PLATE");
        put("MAGE", "ROBE");     put("PRIEST", "ROBE"); put("SHAMAN", "ROBE");
        put("ARCHER", "JACKET");
    }};
    private static final HashMap<String, String> JOB_TO_HELMET_CLASS = new HashMap<String, String>() {{
        put("WARRIOR", "HELM"); put("ROGUE", "HELM");
        put("MAGE", "HEADBAND"); put("PRIEST", "HEADBAND"); put("SHAMAN", "HEADBAND");
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
            { "타이가" }, // ★7
        });
        put("MAGE", new String[][]{
            { "유키", "아오이", "이츠키" },
            { "미유", "리사", "나오" },
            { "사야", "마이", "유나" },
            { "리오", "카나" },
            { "미코토" },
            { "세라" },
            { "아리스" }, // ★7
        });
        put("ROGUE", new String[][]{
            { "카이토", "료", "츠바사" },
            { "신지", "타쿠야", "겐지" },
            { "레이", "아키라", "소마" },
            { "유이토", "카게로우" },
            { "나기" },
            { "야토" },
            { "시노" }, // ★7
        });
        put("ARCHER", new String[][]{
            { "소라", "유토", "나나" },
            { "아야", "미사키", "유즈키" },
            { "리코", "마유", "하루카" },
            { "세리나", "츠키미" },
            { "스즈네" },
            { "아마츠" },
            { "히카리" }, // ★7
        });
        put("PRIEST", new String[][]{
            { "사쿠라", "히나", "유이" },
            { "모모카", "이오리", "시온" },
            { "노조미", "아사히", "렌게" },
            { "코하루", "미레이" },
            { "스이렌" },
            { "아마네" },
            { "미코" }, // ★7
        });
        // [2026-10-20] 무녀(SHAMAN) -- 신사/제례 느낌의 이름. 마지막(★7)은 히미코(고대 무녀왕).
        put("SHAMAN", new String[][]{
            { "하나", "토키", "메이" },
            { "카구야", "유라", "사요" },
            { "아카네", "치요", "스미레" },
            { "미즈키", "하스미" },
            { "시즈쿠" },
            { "카가리" },
            { "히미코" },
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
        // [2026-09-28] 101층+ 계단 구역은 모든 층이 전투층이라 위 "블록당 8층" 번호로는 X0/X9층이
        // 앞뒤 층과 이름이 겹친다 -- 98층(=80번) 다음부터 층마다 1씩 이어서 매김.
        if (floor >= STAIR_ZONE_START) pos = 80 + (floor - STAIR_ZONE_BASE_CAMP);
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
    // 인덱싱하므로 행 하나만 늘리면 별도 분기 없이 자동 적용됨.
    // [2026-09-21 재조정] "7성장비는 6성과 능력치는 동일하도록해줘. 특수능력만 추가되는걸로
    // 하자" 요청 -- ★7은 스탯 보너스를 ★6과 완전히 동일하게 두고, 전설 고유효과(DEF_STEAL
    // 등, TBOT_S5_LEGENDARY_MASTER)만으로 차별화한다. 원래 있던 "★6 대비 약 1.8배" 수치는
    // 제거.
    private static final double[][] EQUIP_BONUS = {
        { 30, 0.05,   5, 0.05,   3, 0.05 },
        { 45, 0.07,   8, 0.07,   5, 0.07 },
        { 75, 0.10,  13, 0.10,   8, 0.10 },
        { 150, 0.15, 25, 0.15,  15, 0.15 },
        { 350, 0.22, 60, 0.22,  35, 0.22 },
        { 800, 0.35, 150, 0.35, 80, 0.35 },
        { 800, 0.35, 150, 0.35, 80, 0.35 },
    };

    // [2026-09-18] "50층 이상 보스층 처치 시 확률로 전설의조각 드랍, 조각 10개로 전설제작"
    // 요청 -- 보스층(59/69/79/89/99) 순서대로 드랍확률 5%→25%로 균등증가(5%p씩). "최대
    // 2개"였다가 같은 날 후속 메시지("일반사용자에겐 제작은 아직 오픈하지 말고, 보스처치시
    // 최대1개")로 최대 1개로 축소.
    private static final int[] LEGEND_FRAGMENT_BOSS_FLOOR = { 59, 69, 79, 89, 99 };
    // [2026-10-08] "전설의조각 확률을 기존 대비 2배" 요청 -- 보스층 5/10/15/20/25% -> 10/20/30/40/50%, 몬스터 0.5% -> 1%, 중간보스 1% -> 2%,
    // 장비상자 15% -> 30%, 행운칸 사원/유적 5% -> 10%.
    private static final int[] LEGEND_FRAGMENT_DROP_PCT   = { 10, 20, 30, 40, 50 };
    // [2026-09-29] 80층 이상 일반몬스터 0.5%(5/1000), 중간보스 1%(10/1000) 조각 드랍
    private static final int LEGEND_FRAGMENT_MOB_MIN_FLOOR = 80;
    private static final int LEGEND_FRAGMENT_MOB_DROP_PERMILLE = 10;
    private static final int LEGEND_FRAGMENT_MIDBOSS_DROP_PERMILLE = 20;
    private static final int LEGEND_CRAFT_COST = 10;      // 전설제작 소모 조각 개수
    private static final int LEGEND_CRAFT_SUCCESS_PCT = 30; // 전설제작 성공률
    // [2026-09-21] "최상급장비상자에서 조각이 너무 잘 나온다, 확률에 맞게 나오도록" 요청 --
    // 원래 100% 확정 지급이던 걸 보스층 드랍(5~25%)과 같은 방식의 확률 지급으로 전환.
    // 보스층 최고 확률(25%, 99층)보다 살짝 낮은 수준으로 잡음 -- 상자는 반복 구매/오픈이
    // 가능해 보스킬(하루 3회 제한)보다 훨씬 자주 시도할 수 있으므로.
    private static final int LEGEND_FRAGMENT_BOX_DROP_PCT = 30;
    // [2026-09-21] "전설은 한번 만들어지면 전설의조각 9개로 바꿀수있도록도 해줘" 요청 --
    // 제작 비용(10개)보다 1개 적게(9개) 돌려줘서 무손실 순환을 막는 조각 싱크.
    // [2026-09-29] "조각 15개로 주거나...만드는데도 30%확률이라 혜택을 주고싶어" 요청 -- 환급 9->15
    // (제작 1회 기대비용 10/0.3=약33개의 절반 이하라 무손실 순환은 여전히 불가), 그리고 연속 실패
    // 1회당 다음 성공률 +10%p 누적(성공하면 0으로 초기화, 100% 상한 = 연속 7회 실패 시 확정).
    private static final int LEGEND_DISENCHANT_REFUND = 15;
    // [2026-10-08] 전설장비 강화(수치 제한 없음). 레벨 n -> n+1 시도:
    //  - 조각 비용 = 3 + n/2 (n=0,1 -> 3개, n=20 -> 13개, n=40 -> 23개 ... 계속 증가)
    //  - 성공률 = max(30, 95 - 3n)% (n=10 -> 65%, n>=22 -> 30% 고정) + 그 장비의 연속 실패 1회당 +5%p(최대 +50%p, 성공하면 0으로)
    //  - 실패하면 강화 수치는 그대로(조각만 소모). 성공하면 +1, 그 장비의 보너스가 단계당 6%씩 늘어난다(예: +20 = 보너스 2.2배).
    //  - 분해하면 기본 환급(15개)에 그 장비에 쓴 정가 조각의 절반을 더 돌려준다.
    private static final int ENHANCE_STAT_PCT_PER_LV = 6;
    private static final int ENHANCE_BASE_SUCCESS_PCT = 95;
    private static final int ENHANCE_SUCCESS_DROP_PER_LV = 3;
    private static final int ENHANCE_MIN_SUCCESS_PCT = 30;
    private static final int ENHANCE_PITY_STEP_PCT = 5;
    private static final int ENHANCE_PITY_MAX = 10;

    private static int enhanceCost(int lv) { return 3 + lv / 2; }

    private static int enhanceBaseSuccessPct(int lv) { return Math.max(ENHANCE_MIN_SUCCESS_PCT, ENHANCE_BASE_SUCCESS_PCT - ENHANCE_SUCCESS_DROP_PER_LV * lv); }

    private static int enhanceSuccessPct(int lv, int pity) {
        return Math.min(100, enhanceBaseSuccessPct(lv) + Math.min(pity, ENHANCE_PITY_MAX) * ENHANCE_PITY_STEP_PCT);
    }

    /** 0단계에서 lv단계까지 올리는 데 든 정가 조각 합계(실패로 날린 조각은 제외). */
    private static int enhanceCumulativeCost(int lv) {
        int sum = 0;
        for (int i = 0; i < lv; i++) sum += enhanceCost(i);
        return sum;
    }
    private static final int LEGEND_PITY_STEP_PCT = 10;
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
        // [2026-09-28] 101층+ 계단 구역 전용 칸(STAIR_ZONE_PATTERN 참고)
        put("RANDOM_LUCKY", "🎲 행운"); put("MIDBOSS", "👹 중간보스");
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
    // [2026-10-20] "하루 400회 제한 없애줘" -- 하루 이동 한도를 폐지했다. 0 이하 = 제한 없음(기본값). 다시 걸려면 TBOT_S5_CONFIG의 DAILY_MOVE_LIMIT를 1 이상으로 넣고 /갱신.
    private static volatile int DAILY_MOVE_LIMIT = 0;

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
    // [2026-10-22] "관리자 계정은 랭킹에서 안 나오도록" 요청 -- /탑랭킹 집계에서 뺄 유저명('|' 구분, TBOT_S5_CONFIG.RANK_EXCLUDE_USERS, /갱신으로 반영).
    private static volatile String RANK_EXCLUDE_USERS = "";
    // [2026-10-23] "광산/광산상점은 관리자만 열고, config로 일반 유저 오픈 여부를 결정" 요청 -- TBOT_S5_CONFIG.MINE_OPEN_YN('Y'면 전체 오픈, 기본 N),
    // MINE_ADMIN_USERS('|' 구분, 오픈 전에도 들어갈 수 있는 유저). /갱신으로 반영.
    private static volatile boolean MINE_OPEN = false;
    private static volatile String MINE_ADMIN_USERS = "";

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
                if ("RANK_EXCLUDE_USERS".equals(key)) {
                    RANK_EXCLUDE_USERS = val;
                    continue;
                }
                if ("MINE_OPEN_YN".equals(key)) {
                    MINE_OPEN = "Y".equalsIgnoreCase(val.trim());
                    continue;
                }
                if ("MINE_ADMIN_USERS".equals(key)) {
                    MINE_ADMIN_USERS = val;
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
                    } else if ("S7_REQ_G5".equals(key)) {
                        S7_REQ_G5 = Integer.parseInt(val);
                    } else if ("STAIR_ZONE_MAX_FLOOR".equals(key)) {
                        // 200 미만으로는 내리지 않고(이미 열린 구간 보호), 준비된 최대(500)를 넘기지 않는다.
                        stairZoneOpenMax = Math.max(200, Math.min(STAIR_ZONE_READY_MAX, Integer.parseInt(val)));
                    }
                } catch (NumberFormatException ignore) {
                    // 파싱 실패한 값은 무시하고 기존(기본) 값 유지
                }
            }
        } catch (Exception ignore) {
            // 서버 기동 시점에 DB 접속이 안 되거나 테이블이 없어도 기본값으로 계속 기동
        }
    }

    /** [2026-10-23] 광산/광산상점을 쓸 수 있는지 -- 전체 오픈(MINE_OPEN_YN=Y)이거나 MINE_ADMIN_USERS에 있는 유저. */
    @Override
    public boolean mineAccess(String userName) {
        if (MINE_OPEN) return true;
        if (userName == null || MINE_ADMIN_USERS == null) return false;
        for (String u : MINE_ADMIN_USERS.split("\\|")) if (u.trim().equals(userName.trim())) return true;
        return false;
    }

    /** [2026-10-24] 광산 관리자(좌표 이동) -- MINE_ADMIN_USERS 또는 EVENT_ADMIN_USERS에 있는 유저. */
    private boolean isMineAdmin(String userName) {
        if (userName == null) return false;
        if (MINE_ADMIN_USERS != null) for (String u : MINE_ADMIN_USERS.split("\\|")) if (!u.trim().isEmpty() && u.trim().equals(userName.trim())) return true;
        return isEventAdmin(userName);
    }

    private static HashMap<String, Object> mineClosed() {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        out.put("closed", true);
        out.put("error", "⛏️ 광산은 아직 준비 중입니다.");
        out.put("message", "⛏️ 광산은 아직 준비 중입니다.");
        return out;
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
        my.prac.core.prjbot.dao.S5RequestCache.clearShared(); // [2026-10-22] 요청 간 공유 캐시(기준 데이터/장비 목록)도 즉시 비움
        loadConfig();
        loadBalanceV2();
        return "🗼 시즌5 설정 갱신 완료 (칸이동 " + MOVE_COOLDOWN_SEC + "초 / 전투중 " + COMBAT_COOLDOWN_SEC
                + "초 / 전투종료 " + COMBAT_END_COOLDOWN_SEC + "초 / 하루 이동 한도 "
                + (DAILY_MOVE_LIMIT > 0 ? "웹 " + DAILY_MOVE_LIMIT + "회·카톡 " + (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) + "회" : "없음")
                + " / 자동사냥 시간당 "
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
    /** [2026-10-08] 전설 팔찌 "학자의 토시"(SKILL_RATE_MULT) -- 착용한 동료의 캐릭터 스킬 발동 확률을 배율만큼 올린다(예: 도사 부활 10% -> 15%).
     *  resolveCombatTurn이 턴 시작에 파티 동료마다 SKILL_MULT(배율)를 붙여 두고, 확률 굴림 지점이 이 헬퍼로 최종 확률을 구한다.
     *  [2026-10-20] ★7 동료는 ★6 대비 발현율 x1.2(STAR7_SKILL_BONUS). 학자의 토시(x1.5)와는 곱하지 않고 "증가분을 더한다"(1+0.2+0.5 = x1.7)
     *  -- 곱셈(x2.25)이면 대부분 스킬이 한계에 닿아 아이템 가치가 무의미해지기 때문. 증가가 붙은 확률은 SKILL_RATE_CAP(90%)를 넘지 않아 필중이 되지 않는다. */
    private int skillRate(HashMap<String, Object> companion, int baseChance) {
        if (companion == null) return baseChance;
        double bonus = 0.0;
        Object m = companion.get("SKILL_MULT");
        if (m instanceof Number) bonus += ((Number) m).doubleValue() - 1.0;
        if (intVal(companion.get("GRADE"), 1) == STAR7_GRADE) bonus += STAR7_SKILL_BONUS;
        if (bonus <= 0) return baseChance;
        int boosted = (int) Math.round(baseChance * (1.0 + bonus));
        return Math.min(boosted, Math.max(baseChance, SKILL_RATE_CAP));
    }

    // ---- [2026-10-20] ★7 동료 ----
    private static final int STAR7_GRADE = 7;
    /** ★7 기본 스탯 = ★6 기본 x (한계돌파 최대 단계 배율) x 1.2. 한계돌파는 없다(LIMIT_BREAK는 항상 0). */
    private static final double STAR7_STAT_OVER_LB6 = 1.2;
    /** ★7 스킬 발현율 증가분(+20% = x1.2). */
    private static final double STAR7_SKILL_BONUS = 0.2;
    /** 아이템/★7 배율이 붙은 스킬 확률의 상한(%) -- 필중 방지. */
    private static final int SKILL_RATE_CAP = 90;
    /** 업적 "전설의 시작"(TBOT_S5_ACHIEVEMENT ACH_ID 599) -- 한계돌파 +6인 ★5 동료를 S7_REQ_G5체 가지면 달성되고, 달성하면 ★7 동료를 소환할 수 있다(재료/전설의조각 소모 없음). */
    private static final int ACH_LEGEND_BEGIN = 599;
    private static volatile int S7_REQ_G5 = 2;
    /** 무녀 스킬 이름 -- 전투 로그/시너지 안내에 표시. */
    private static final String SHAMAN_SKILL_WARD = "액막이";     // 몬스터 공격력을 고정 수치만큼 낮춘다
    private static final String SHAMAN_SKILL_RELEASE = "살풀이";  // 아군 전원의 공격력을 고정 수치만큼 올린다
    /** 각 스킬이 한 턴에 움직일 수 있는 최대치(몬스터 공격력 기준값 대비). */
    private static final double SHAMAN_DRAIN_CAP_PCT = 0.5;

    /** [2026-10-20] 무녀 스킬 사전 굴림 -- 파티 공격 전에 호출. 발동한 무녀(살아있고 기절 아님)마다 두 스킬을 각각 따로 굴린다.
     *  액막이 = 몬스터 공격력 -N(반격 파트에서 적용), 살풀이 = 아군 전원 공격력 +N(공격 루프에서 적용). 둘 다 고정 수치이고, 한 턴에 각각 몬스터 공격력 기준값의 SHAMAN_DRAIN_CAP_PCT까지만 움직인다.
     *  N = 무녀의 최종 공격력 x 계수(★1~4 0.5, ★5 0.6, ★6~7 0.7, 무녀 3인조 x1.5). 확률은 스킬마다 ★1~3 30%, ★4 35%, ★5 40%, ★6~7 45%(+무녀 3인조 20%p) -- skillRate 적용.
     *  @return 무녀 동료ID -> {액막이 수치, 살풀이 수치}(발동한 것만, 0이면 미발동) */
    private LinkedHashMap<Integer, int[]> rollShamanPlays(List<HashMap<String, Object>> party, HashMap<String, Object> userStat,
            String synergy, double monsterAtkBase, int bossStunCid) {
        LinkedHashMap<Integer, int[]> plays = new LinkedHashMap<>();
        int wardCap = (int) Math.floor(monsterAtkBase * SHAMAN_DRAIN_CAP_PCT);
        int releaseCap = wardCap;
        boolean mono = "SHAMAN".equals(synergy);
        for (HashMap<String, Object> c : party) {
            if (!"SHAMAN".equals(strVal(c.get("CLASS"), ""))) continue;
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            if (PP.toBaseValue(hp) <= 0) continue;
            int cid = intVal(c.get("COMPANION_ID"), 0);
            if (bossStunCid != 0 && bossStunCid == cid) continue;
            int grade = intVal(c.get("GRADE"), 1);
            int chance = grade >= 6 ? 45 : (grade >= 5 ? 40 : (grade >= 4 ? 35 : 30));
            if (mono) chance += 20;
            int pct = skillRate(c, chance);
            boolean wardHit = RND.nextInt(100) < pct;
            boolean releaseHit = RND.nextInt(100) < pct;
            if (!wardHit && !releaseHit) continue;
            int[] eff = slotPenalty(computeEffectiveStat("SHAMAN", grade, dao.selectEquipByCompanion(cid), userStat, intVal(c.get("LIMIT_BREAK"), 0)), c, userStat);
            double coef = grade >= 6 ? 0.7 : (grade >= 5 ? 0.6 : 0.5);
            if (mono) coef *= 1.5;
            int amount = Math.max(1, (int) Math.round(eff[1] * coef));
            int ward = wardHit ? Math.min(wardCap, amount) : 0;
            int release = releaseHit ? Math.min(releaseCap, amount) : 0;
            wardCap -= ward;
            releaseCap -= release;
            if (ward > 0 || release > 0) plays.put(cid, new int[]{ ward, release });
        }
        return plays;
    }

    private void attachSkillMult(String userName, List<HashMap<String, Object>> party) {
        try {
            for (HashMap<String, Object> r : dao.selectSkillRateMults(userName)) {
                int cid = intVal(r.get("CID"), 0);
                double mult = intVal(r.get("MULT_PCT"), 100) / 100.0;
                for (HashMap<String, Object> c : party) {
                    // 같은 동료가 여러 개를 껴도(쿼리가 GROUP BY 없이 행을 그대로 준다) 가장 큰 배율 하나만 적용한다
                    if (intVal(c.get("COMPANION_ID"), 0) == cid) {
                        // [2026-10-22] 성장형은 특수효과 50% -- 배율의 증가분만 절반(x1.5 -> x1.25)
                        double cm = 1 + (mult - 1) * legendScale(intVal(c.get("GRADE"), 1) == GROWTH_GRADE); // [2026-10-23] 증가분 50%(성장형 25%)
                        Object prev = c.get("SKILL_MULT");
                        if (!(prev instanceof Number) || cm > ((Number) prev).doubleValue()) c.put("SKILL_MULT", cm);
                    }
                }
            }
        } catch (Exception e) {
            // 배율 조회 실패는 전투를 막지 않는다(배율 없이 진행)
        }
    }

    private int[] calcBaseStat(String job, int grade) {
        int[][] gradeBase = GRADE_BASE_V2;
        // [2026-10-20] ★7은 ★6 행을 기본으로 쓰고, 한계돌파와 똑같이 "장비까지 더한 뒤" 최종 배율(star7StatMult = ★6 한계돌파 최대 x 1.2)을 곱한다 -- computeEffectiveStat 참고.
        // (기본 스탯에만 배율을 곱하면 장비 보너스에는 안 곱해져서, 장비를 낀 ★6 한계돌파 최대보다 약해지는 문제가 있었다.)
        boolean star7 = grade >= STAR7_GRADE;
        int g = star7 ? 6 : grade;
        int[] base = (BALANCE_V2_ENABLED && gradeBase != null && g - 1 < gradeBase.length && gradeBase[g - 1] != null)
                ? gradeBase[g - 1] : GRADE_BASE[g - 1];
        double[] mult = JOB_MULT.get(job);
        int hp  = (int) Math.round(base[0] * mult[0]);
        int atk = (int) Math.round(base[1] * mult[1]);
        int def = (int) Math.round(base[2] * mult[2]);
        return new int[]{ hp, atk, def };
    }

    /** ★7 능력치 배율(★6 기본 대비) = (1 + 한계돌파 최대 단계 증가율) x 1.2. 현재 표 기준 2.8 x 1.2 = 3.36. */
    private double star7StatMult() {
        return (1 + limitBreakPct(LIMIT_BREAK_MAX)) * STAR7_STAT_OVER_LB6;
    }

    // ---- [2026-10-20] 성장형 동료(☆1) ----
    // 유저당 1명(GRADE=8 표식, 가챠/한계돌파/승급과 무관), 직업은 상점에서 자유 변경, 이름은 유저가 변경, 장비는 직업 그룹과 상관없이 착용.
    // 강함: 1~100층 사냥층(보스/마을 제외 80개) 중 100% 탐사를 기록한 층 수 n에 비례해 배율 G가 0.25(★3 기본 수준)에서 ★7(3.36)의 2배(6.72)까지 선형 상승.
    // 101층 이상은 (계단 구역은 항상 완전탐사로 보고) n이 80이 된 시점부터, 도달한 최고층 기준 계단층(100의 배수 제외, 1000층까지) 하나당 +0.0005를 일괄 반영한다.
    // ★7처럼 장비까지 더한 최종 값에 곱한다(finalStatMult). 직업 스킬은 ★6 기준(GRADE 8은 6 이상 분기를 그대로 탐), ★7 발현율 보너스는 없다.
    private static final int GROWTH_GRADE = 8;
    private static final double GROWTH_START_MULT = 0.25;
    private static final int GROWTH_FLOORS_FULL = 80;
    private static final double GROWTH_STAIR_PER_FLOOR = 0.0005;
    private static final int GROWTH_STAIR_MAX_FLOOR = 1000;
    private static final String GROWTH_DEFAULT_NAME = "선생님";
    private static final String GROWTH_DEFAULT_JOB = "WARRIOR";

    // [2026-10-22] "성장형 스펙을 조금 낮추자 -- ★7 기준이 아니라 ★6의 2배가 최대" 요청: 풀 성장을 ★7의 2배(x6.72)에서
    // ★6 한계돌파 최대(+6, x2.8)의 2배(x5.6)로 하향. 또 "성장형은 장비의 효과를 50%만 받게(특수효과 포함)" 요청으로
    // 장비 능력치 보너스(전설 STAT_MULT/강화 포함)와 전설 특수효과(확률/수치)를 GROWTH_EQUIP_EFFECT만큼만 받는다.
    private static final double GROWTH_EQUIP_EFFECT = 0.5;

    private double growthFullMult() { return 2.0 * (1 + limitBreakPct(LIMIT_BREAK_MAX)); }

    // [2026-10-23] "전설장비 특수효과를 현재 대비 50%로" 요청 -- 모든 동료의 전설 특수효과(확률/수치/배율의 증가분)에 곱하는 배율.
    // 성장형(☆1)은 여기에 GROWTH_EQUIP_EFFECT(50%)가 한 번 더 곱해져 원래의 25%. 능력치 보너스(장비 기본/강화)에는 적용하지 않는다(STAT_MULT의 증가분만).
    private static final double LEGEND_EFFECT_SCALE = 0.5;

    /** 이 동료가 받는 전설 특수효과 배율(전원 50%, 성장형은 그 절반). */
    private static double legendScale(boolean growth) {
        return LEGEND_EFFECT_SCALE * (growth ? GROWTH_EQUIP_EFFECT : 1.0);
    }

    /** 전설 특수효과 확률 굴림 -- pct%에 legendScale을 곱한 확률(성장형이면 한 번 더 절반). */
    private static boolean legendRoll(int pct, boolean growth) {
        return pct > 0 && RND.nextDouble() * 100 < pct * legendScale(growth);
    }

    /** 정수 효과(+N)에 배율을 곱한 값 -- 소수부는 그 확률로 1이 된다(평균이 정확히 N x 배율). */
    private static int scaleInt(int n, double scale) {
        double x = n * scale;
        int b = (int) Math.floor(x);
        return b + (RND.nextDouble() < x - b ? 1 : 0);
    }

    /** 101층~maxFloor(최대 1000층) 중 100의 배수(마을)를 뺀 계단층 수. */
    private int growthStairFloors(int maxFloor) {
        int cap = Math.min(maxFloor, GROWTH_STAIR_MAX_FLOOR);
        if (cap <= 100) return 0;
        return (cap - 100) - (cap / 100 - 1);
    }

    /** userStat(selectUserStat)에 실린 GROWTH_N(1~100층 사냥층 중 100% 탐사 수), GROWTH_MAXF(도달 최고층)로 성장 배율을 계산. */
    private double growthMult(HashMap<String, Object> userStat) {
        int n = userStat == null ? 0 : Math.max(0, Math.min(GROWTH_FLOORS_FULL, intVal(userStat.get("GROWTH_N"), 0)));
        double g = GROWTH_START_MULT + (growthFullMult() - GROWTH_START_MULT) * n / GROWTH_FLOORS_FULL;
        if (n >= GROWTH_FLOORS_FULL && userStat != null) g += GROWTH_STAIR_PER_FLOOR * growthStairFloors(intVal(userStat.get("GROWTH_MAXF"), 0));
        return g;
    }

    /** 장비/스탯구매까지 더한 값에 마지막으로 곱하는 최종 배율 -- 성장형은 탐사 성장, ★7은 고정 배율, 그 외는 한계돌파. */
    private double finalStatMult(int grade, int limitBreak, HashMap<String, Object> userStat) {
        if (grade == GROWTH_GRADE) return growthMult(userStat);
        if (grade >= STAR7_GRADE) return star7StatMult();
        return limitBreak > 0 ? 1 + limitBreakPct(limitBreak) : 1.0;
    }

    private String gradeMark(int grade) { return grade == GROWTH_GRADE ? "☆1" : "★" + grade; }

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

    /** [2026-09-21] "tbot_s5_user_progress에 cur_floor 0인 계정이 자꾸 생겨. 주기적으로
     *  지워줘" 요청 -- 원인은 웹뷰(/loa/tower-view, /loa/api/tower-status)가 모르는 userName
     *  으로 호출되면(오타 URL, 만료된 링크, 미리보기 크롤러 등) resolveUserName이 실패하고
     *  apiTowerStatus가 조용히 initUser로 빈 계정을 새로 만들어버려서(Season5ViewController
     *  참고) 발생 -- 매일 새벽 4시에 "가입 후 하루가 지나도 한 번도 이동하지 않은(CUR_FLOOR=0)"
     *  계정을 정리. 실제로 플레이를 시작하면 최초 이동/전투에서 바로 CUR_FLOOR이 0을 벗어나므로
     *  이 조건에 걸리는 계정은 전부 미사용 계정이다.
     *  [버그수정] @Scheduled 메서드는 JDK 동적 프록시가 호출할 수 있게 인터페이스(BotS5Service)에도
     *  반드시 선언돼 있어야 한다 -- 누락 시 컨텍스트 부팅 자체가 실패한다(라이브에서 실제로 겪음). */
    @Override
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupJunkZeroFloorUsers() {
        try {
            int deleted = dao.deleteJunkZeroFloorUsers();
            if (deleted > 0) {
                System.out.println("[S5 정리] CUR_FLOOR=0 미사용 계정 " + deleted + "건 삭제");
            }
        } catch (Exception e) {
            System.out.println("[S5 정리] CUR_FLOOR=0 계정 정리 실패: " + e.getMessage());
        }
    }

    /** [2026-09-30] "시즌5 로그가 들어가는 테이블을 따로 만들어 관리, 삭제주기는 한 달" 요청 --
     *  TBOT_S5_WORD_HIS에서 30일이 지난 행을 매일 새벽 4시 30분에 5000행씩 지운다(한 번에 대량 삭제로
     *  언두/락이 커지지 않게 배치마다 별도 호출, 0건이 나오거나 안전 상한에 닿으면 종료).
     *  @Scheduled 메서드라 BotS5Service 인터페이스에도 선언돼 있어야 한다(위 정리 메서드와 동일 이유). */
    @Override
    @Scheduled(cron = "0 30 4 * * *")
    public void purgeOldS5WordHis() {
        try {
            int total = 0;
            for (int i = 0; i < 400; i++) { // 최대 200만 행까지만 한 번에(무한 루프 방지)
                int n = dao.deleteOldS5WordHis(S5_WORD_HIS_KEEP_DAYS, 5000);
                total += n;
                if (n < 5000) break;
            }
            if (total > 0) {
                System.out.println("[S5 정리] 시즌5 로그 " + S5_WORD_HIS_KEEP_DAYS + "일 초과 " + total + "건 삭제");
            }
        } catch (Exception e) {
            System.out.println("[S5 정리] 시즌5 로그 정리 실패: " + e.getMessage());
        }
    }

    /** [2026-09-30] "tbot_word_his도 1개월 지난 데이터 자동으로 정리" 요청 -- 기존 로그 테이블(시즌5 외 로그 + 복사 이전
     *  시즌5 로그)에서 30일이 지난 행을 매일 새벽 4시 40분에 5000행씩 지운다(purgeOldS5WordHis와 같은 방식).
     *  주의: 경매장 시세 통계(BotMapper.selectMarketCondition)는 이 테이블의 최근 60일을 읽으므로 이제 30일치만 남는다.
     *  @Scheduled 메서드라 BotS5Service 인터페이스에도 선언돼 있어야 한다. */
    @Override
    @Scheduled(cron = "0 40 4 * * *")
    public void purgeOldWordHis() {
        try {
            int total = 0;
            for (int i = 0; i < 400; i++) { // 최대 200만 행까지만 한 번에(무한 루프 방지)
                int n = dao.deleteOldWordHis(WORD_HIS_KEEP_DAYS, 5000);
                total += n;
                if (n < 5000) break;
            }
            if (total > 0) {
                System.out.println("[정리] 기존 로그(TBOT_WORD_HIS) " + WORD_HIS_KEEP_DAYS + "일 초과 " + total + "건 삭제");
            }
        } catch (Exception e) {
            System.out.println("[정리] 기존 로그(TBOT_WORD_HIS) 정리 실패: " + e.getMessage());
        }
    }

    /** [2026-09-30] "경매장 시세통계는 한 달이 지나면 일마다 최소값 row/최대값 row 2개씩만 보관" 요청 -- 매일 새벽 4시 50분에
     *  한 달(30일)이 지난 날짜의 T_MARKET_ITEM_PRICE 행을 아이템별 최소가 행/최대가 행만 남기고 지운다. 일/주/월별 최소~최대를
     *  보여주는 /시세 명령 결과는 그대로다(시간별 보기는 최근 8시간만 쓰므로 영향 없음). 2026-09-30에 기존 데이터에도 같은 규칙을
     *  한 번 적용했다(삭제분 43만 행은 T_MKT_PRICE_BAK_20260930에 백업).
     *  @Scheduled 메서드라 BotS5Service 인터페이스에도 선언돼 있어야 한다. */
    @Override
    @Scheduled(cron = "0 50 4 * * *")
    public void compactOldMarketPrice() {
        try {
            int n = dao.compactOldMarketPrice(MARKET_PRICE_FULL_KEEP_DAYS);
            if (n > 0) {
                System.out.println("[정리] 경매장 시세 " + MARKET_PRICE_FULL_KEEP_DAYS + "일 초과분 " + n + "건 압축(일 최소/최대만 유지)");
            }
        } catch (Exception e) {
            System.out.println("[정리] 경매장 시세 압축 실패: " + e.getMessage());
        }
    }

    /** 경매장 시세를 원본 그대로 보관하는 기간(일). */
    private static final int MARKET_PRICE_FULL_KEEP_DAYS = 30;

    /** 시즌5 전용 로그 테이블 보관기간(일). */
    private static final int S5_WORD_HIS_KEEP_DAYS = 30;
    /** 기존 로그 테이블(TBOT_WORD_HIS) 보관기간(일). */
    private static final int WORD_HIS_KEEP_DAYS = 30;

    /** 한계돌파 N단계의 스탯 배율(%) -- 위 LIMIT_BREAK_PCT 표 참고, 범위 밖이면 클램프. */
    private double limitBreakPct(int limitBreak) {
        int lv = Math.max(0, Math.min(limitBreak, LIMIT_BREAK_MAX));
        double[] v2 = LIMIT_BREAK_V2;
        if (BALANCE_V2_ENABLED && v2 != null && lv < v2.length) return v2[lv];
        return LIMIT_BREAK_PCT[lv];
    }

    // [2026-09-22] "근본" 처방 -- 몬스터 ATK 전체 상향(위 ATK_BOOST_*)만으로는 스탯구매
    // (체력 Lv당 +3%, 최대 45~50Lv)와 한계돌파(최대 +180%)가 복합된 고투자 캐릭터의 체력이
    // 그만큼 불어나 있어서 "맞아도 안 아프다"는 체감이 그대로 남는다(로스터리 밸런스 조사
    // 때 진단한 것과 같은 원인 -- 스탯구매 ×2.35 + 한계돌파 ×2.80 복합 투자배율이 DEF/ATK
    // 어느 쪽 설계에도 반영이 안 돼 있었음). computeEffectiveStat()의 체력 배율 공식
    // ((1+HP_PCT_PER_LV*hpLv) * 한계돌파배율)과 완전히 동일한 식으로 "이 캐릭터의 체력이
    // 투자 전 대비 몇 배 불었는지"(investMult)를 구하고, 그 초과분의 ATK_INVEST_SCALE(50%)
    // 만큼만 몬스터 공격력에 되돌려준다(100% 전부 되돌리면 투자로 얻는 이득이 전혀 없어지므로
    // 로스터리 DEF 설계 때와 동일하게 절반만 -- 투자는 여전히 유리하지만 그 유리함의 정도를
    // 완화). investMult=1(무투자)이면 배율 1(변화 없음). */
    private static final double ATK_INVEST_SCALE = 0.5;

    private double investAtkScale(HashMap<String, Object> userStat, int limitBreak) {
        int hpLv = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        double investMult = (1 + HP_PCT_PER_LV * hpLv) * (1 + limitBreakPct(limitBreak));
        return 1 + ATK_INVEST_SCALE * Math.max(0, investMult - 1);
    }

    /** [2026-10-20] 동료 개체 기준 투자배율 -- ★7은 한계돌파가 없는 대신 기본 스탯이 이미 부풀려져 있으므로 그 배율(star7StatMult)을 한계돌파 증가분과 같은 취급으로 반영한다. */
    private double investAtkScaleFor(HashMap<String, Object> userStat, HashMap<String, Object> companion) {
        if (intVal(companion.get("GRADE"), 1) >= STAR7_GRADE) {
            int hpLv = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
            double investMult = (1 + HP_PCT_PER_LV * hpLv) * finalStatMult(intVal(companion.get("GRADE"), 1), 0, userStat);
            return 1 + ATK_INVEST_SCALE * Math.max(0, investMult - 1);
        }
        return investAtkScale(userStat, intVal(companion.get("LIMIT_BREAK"), 0));
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
                // [2026-09-29] 전설 STAT_MULT 효과(예: 조던 링) -- 그 장비 한 개가 주는 보너스만 N% 배율.
                double sm = "STAT_MULT".equals(strVal(e.get("LEGENDARY_EFFECT_TYPE"), ""))
                        ? 1 + (intVal(e.get("LEGENDARY_EFFECT_PARAM1"), 100) / 100.0 - 1) * LEGEND_EFFECT_SCALE : 1.0; // [2026-10-23] 증가분 50%
                // [2026-10-08] 전설장비 강화 -- 강화 1단계마다 그 장비 한 개가 주는 보너스가 ENHANCE_STAT_PCT_PER_LV% 늘어난다(수치 제한 없음).
                int enhLv = e.get("LEGENDARY_ID") == null ? 0 : intVal(e.get("ENHANCE_LEVEL"), 0);
                if (enhLv > 0) sm *= 1 + enhLv * ENHANCE_STAT_PCT_PER_LV / 100.0;
                if (grade == GROWTH_GRADE) sm *= GROWTH_EQUIP_EFFECT; // [2026-10-22] 성장형은 장비 효과 50%
                if ("HELMET".equals(part)) hp += (b[0] + base[0] * b[1]) * sm;
                else if ("WEAPON".equals(part)) atk += (b[2] + base[1] * b[3]) * sm;
                else if ("ARMOR".equals(part)) def += (b[4] + base[2] * b[5]) * sm;
                // [2026-09-15] 악세서리(목걸이/반지/팔찌) 신설 -- 장비뽑기 말고 별도 악세뽑기로
                // 얻는 3종. 기존 부위 둘의 절반씩을 동시에 준다(목걸이=무기+갑옷, 반지=무기+
                // 투구, 팔찌=갑옷+투구) -- 무기(ATK)/갑옷(DEF)/투구(HP) 보너스 공식을 그대로
                // 절반만 적용. pullAccessoryCore() 참고.
                else if ("NECKLACE".equals(part)) { // 무기(ATK)+갑옷(DEF) 절반씩
                    atk += (b[2] + base[1] * b[3]) / 2.0 * sm;
                    def += (b[4] + base[2] * b[5]) / 2.0 * sm;
                } else if ("RING".equals(part)) { // 무기(ATK)+투구(HP) 절반씩
                    atk += (b[2] + base[1] * b[3]) / 2.0 * sm;
                    hp += (b[0] + base[0] * b[1]) / 2.0 * sm;
                } else if ("BRACELET".equals(part)) { // 갑옷(DEF)+투구(HP) 절반씩
                    def += (b[4] + base[2] * b[5]) / 2.0 * sm;
                    hp += (b[0] + base[0] * b[1]) / 2.0 * sm;
                }
            }
        }

        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int atkMinLv = userStat == null ? 0 : intVal(userStat.get("ATK_MIN_LV"), 0);
        int hpLv     = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        atk *= (1 + ATK_PCT_PER_LV * atkMaxLv);
        hp  *= (1 + HP_PCT_PER_LV * hpLv);
        // [2026-10-20] ★7은 한계돌파가 없는 대신 같은 자리(장비/스탯구매까지 반영한 최종 값)에 star7StatMult(★6 한계돌파 최대 x 1.2)를 곱한다 -- 어떤 장비를 껴도 항상 ★6 한계돌파 최대의 1.2배.
        double lbMult = finalStatMult(grade, limitBreak, userStat);
        if (lbMult != 1.0) {
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

    /** [2026-09-30] 밸런스 통계 -- 전투 1회가 끝날 때(승리/전멸/도망) 결과를 TBOT_S5_BATTLE_STAT에 남긴다.
     *  실패해도 전투 진행에 영향 없게 전부 try/catch. 테스트/관리자 계정(NO_COOLDOWN_YN)은 통계를
     *  오염시키므로 제외. 파티 전투력은 지금 편성 기준(/탑현황의 종합전투력과 같은 가중치),
     *  몬스터 전투력은 그 전투의 배율(강화 x2/중간보스 x3, 보스 공격력 x1.6)까지 반영. */
    private void logBattleResult(String userName, HashMap<String, Object> p, int floor, String result, int turns) {
        try {
            if ("Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) return;
            boolean elite = "Y".equals(strVal(p.get("CUR_MONSTER_ELITE_YN"), "N"));
            boolean mid = "Y".equals(strVal(p.get("CUR_MONSTER_MIDBOSS_YN"), "N"));
            boolean boss = !isStairZone(floor) && floor % 10 == 9;
            double mult = elite ? 2.0 : (mid ? 3.0 : 1.0);
            HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), boss ? "Y" : "N"), floor);
            long monPower = 0;
            if (mon != null) {
                double atk = ((Number) mon.get("ATK_VALUE")).doubleValue() * mult * (boss ? 1.6 : 1.0);
                monPower = combatPower(((Number) mon.get("HP_VALUE")).doubleValue() * mult, atk,
                        ((Number) mon.get("DEF_VALUE")).doubleValue() * mult);
            }
            List<String> jobs = new ArrayList<>();
            long power = 0;
            int dead = 0;
            for (HashMap<String, Object> c : companionsWithEffectiveStats(userName)) {
                if (c.get("PARTY_SLOT") == null) continue;
                jobs.add(strVal(c.get("CLASS"), "?"));
                power += combatPower(intVal(c.get("EFF_HP"), 0), intVal(c.get("EFF_ATK"), 0), intVal(c.get("EFF_DEF"), 0));
                if (PP.toBaseValue(PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""))) <= 0) dead++;
            }
            if (jobs.isEmpty()) return;
            Collections.sort(jobs);
            HashMap<String, Object> row = new HashMap<>();
            row.put("userName", userName);
            row.put("floor", floor);
            row.put("kind", boss ? "B" : mid ? "M" : elite ? "E" : "N");
            row.put("combo", String.join("-", jobs));
            row.put("partySize", jobs.size());
            row.put("partyPower", power);
            row.put("monPower", monPower);
            row.put("result", result);
            row.put("turns", turns);
            row.put("deadCnt", "WIPE".equals(result) ? jobs.size() : dead);
            dao.insertBattleStat(row);
        } catch (Exception ignore) {
            // 통계 적재 실패는 무시(전투 진행/트랜잭션에 영향 주지 않음)
        }
    }

    /** [2026-09-30] 몬스터 정보 DB 뷰 -- 층 1~STAIR_ZONE_MAX_FLOOR의 마을(X0, 100의 배수)을 뺀 모든 층에 대해
     *  실제 전투와 같은 경로(applyHardcoreFloorScale)로 스탯을 만든다. 보스층(X9, 100층 이하)은 보스 행,
     *  그 외는 일반 행. 처치 PP = 기준행 PP_PER_KILL x floorPpMultiplier(x 처치 종류 배율은 미반영).
     *  전투력/안전기준은 /탑현황과 같은 combatPower/SAFE_HUNT_RATIO. */
    @Override
    public List<HashMap<String, Object>> monsterInfoTable() {
        List<HashMap<String, Object>> out = new ArrayList<>();
        // [2026-10-01] 관리자용 미리보기라 아직 안 열린 층(~500)까지 전부 보여준다. 투자계수 = 현재 열린 상한 기준 "최대 투자"
        // (체력 스탯 만렙 + 한계돌파 최대)일 때 몬스터 공격력에 곱해지는 값(investAtkScale와 같은 식) -- 실제 곱해지는 값은 대상 동료의 투자에 따라 1~이 값.
        int refCap = statCapFor(STAIR_ZONE_BASE_CAMP) + 5 * Math.max(0, stairZoneOpenMax / 100 - 1);
        double refMult = (1 + HP_PCT_PER_LV * refCap) * (1 + limitBreakPct(LIMIT_BREAK_MAX));
        double refInvestScale = 1 + ATK_INVEST_SCALE * Math.max(0, refMult - 1);
        for (int f = 1; f <= STAIR_ZONE_READY_MAX; f++) {
            if (!isStairZone(f) && f % 10 == 0) continue; // 마을(10/20/../100/200/300/400/500)
            boolean boss = !isStairZone(f) && f % 10 == 9;
            HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(f), boss ? "Y" : "N"), f);
            if (mon == null) continue;
            double hp = ((Number) mon.get("HP_VALUE")).doubleValue();
            double atk = ((Number) mon.get("ATK_VALUE")).doubleValue();
            double def = ((Number) mon.get("DEF_VALUE")).doubleValue();
            double atkEff = boss ? atk * 1.6 : atk; // 보스 공격력 x1.6은 전투 계산에서 추가로 곱해짐
            long power = combatPower(hp, atkEff, def);
            Object ext = mon.get("PP_PER_KILL_EXT");
            PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), ext == null ? "" : ext.toString())
                    .multiply(floorPpMultiplier(f));
            HashMap<String, Object> row = new HashMap<>();
            row.put("floor", f);
            row.put("kind", boss ? "보스" : isStairZone(f) ? "계단층" : "일반");
            row.put("name", boss ? strVal(mon.get("MONSTER_NAME"), "보스") : floorMonsterName(f, mon));
            row.put("hp", Math.round(hp));
            row.put("atk", Math.round(atkEff));
            row.put("investScale", Math.round(refInvestScale * 100) / 100.0);
            row.put("atkInvest", Math.round(atkEff * refInvestScale));
            row.put("def", Math.round(def));
            row.put("pp", perKill.format());
            row.put("ppBase", PP.toBaseValue(perKill));
            row.put("power", power);
            row.put("safe", Math.round(power * SAFE_HUNT_RATIO));
            out.add(row);
        }
        return out;
    }

    private volatile List<HashMap<String, Object>> monsterCodexCache = null;
    private volatile long monsterCodexTime = 0L;

    @Override
    public List<HashMap<String, Object>> monsterCodex() {
        long now = System.currentTimeMillis();
        List<HashMap<String, Object>> cached = monsterCodexCache;
        if (cached == null || now - monsterCodexTime > 600_000L) {
            cached = monsterInfoTable(); // 층 수만큼 몬스터 조회가 나가므로 10분 캐시(밸런스/오픈 층이 바뀌면 최대 10분 뒤 반영)
            monsterCodexCache = cached;
            monsterCodexTime = now;
        }
        return cached;
    }

    @Override
    public List<HashMap<String, Object>> legendaryCodex() {
        return dao.selectLegendaryCodex();
    }

    /** 그 층 "일반" 몬스터(BOSS_YN='N')의 전투력 -- applyHardcoreFloorScale로 51층+ 구간 내
     *  선형 스케일(1번째 사냥터층 50% ~ 8번째 100%)까지 반영한 실제 그 층 기준값. */
    private long floorMonsterCombatPower(int floor) {
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), "N"), floor);
        if (mon == null) return 0;
        return combatPower(((Number) mon.get("HP_VALUE")).doubleValue(),
                ((Number) mon.get("ATK_VALUE")).doubleValue(), ((Number) mon.get("DEF_VALUE")).doubleValue());
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
        PENDING_FACE.merge(face, 1, Integer::sum); // [2026-10-21 성능] 공용 통계 행 UPDATE를 굴림마다(트랜잭션 안에서) 하지 않고 모아서 반영
        return face;
    }

    /** [2026-09-19] "51층+ 몬스터도 주사위를 굴리는데 반격은 자기만의 무작위 면수(51~70층
     *  6~12, 71층+ 8~20)를 쓰는데, 은신 기습(첫턴 선공)만 이 로직 이전에 짜여서 플레이어가
     *  낀 주사위(diceMax, 최대 20이지만 등급이 낮으면 그보다 작음)를 그대로 재사용하고
     *  있었다" 버그 -- 51층 미만은 원래도 몬스터가 플레이어 주사위를 공유하던 구간이라
     *  그대로 두고, 51층 이상만 이 헬퍼로 통일해서 기습/반격이 항상 같은 규칙을 쓰게 한다. */
    /** [2026-10-01] "200층 이후는 몬스터 주사위 굴림의 최소치가 더 높게" -- 201~225층 4, 이후 25층마다 +1(300층 7, 500층 15). 최대 눈금보다는 항상 낮게. */
    private int monsterDiceMin(int floor, int faceMax) {
        if (floor <= 200) return 1;
        int min = 4 + (floor - 201) / 25;
        return Math.max(1, Math.min(min, faceMax - 1));
    }

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
            StringBuilder sb, HashMap<String, Object> target, int dmg, boolean lethalAllowed) {
        String job = strVal(target.get("CLASS"), "WARRIOR");
        int grade = intVal(target.get("GRADE"), 1);
        String name = strVal(target.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
        List<HashMap<String, Object>> equips = dao.selectEquipByCompanion(intVal(target.get("COMPANION_ID"), 0));
        int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, intVal(target.get("LIMIT_BREAK"), 0)), target, userStat);
        // [2026-09-30] "100층이후에서는 기습데미지로 인한 즉사도 허용" -- 101층+는 사망방지 클램프 생략.
        if (!lethalAllowed) {
            int hardCap = (int) Math.floor(eff[0] * AMBUSH_DEATH_GUARD_PCT);
            if (dmg > hardCap) dmg = Math.max(1, hardCap);
        }
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
        if (PP.toBaseValue(hpAfter) <= 0 && tryLastStand(userName, p, intVal(target.get("COMPANION_ID"), 0), equips, sb)) {
            hpAfter = PP.fromPP(1); // 용왕의 투구 불굴
        }
        if (warded) {
            sb.append("🛡️✨ 피해 면역 발동! ").append(jobTag(grade, job, name)).append("이(가) 이번 피해를 완전히 막아냈다! (가호 소모)").append(NL);
        }
        sb.append(jobTag(grade, job, name)).append("에게 ").append(dmg).append("dmg (💗")
          .append(plainNum(hpAfter)).append("/").append(eff[0]).append(")")
          .append(PP.toBaseValue(hpAfter) <= 0 ? " 💀 기습에 쓰러졌다!" : "").append(NL);
        writeCompanionHp(target, hpAfter);
        // [2026-10-08] "은신 기습 한 턴 말고 다음턴부터 부활 가능하도록" -- 기습 턴에는 도사 부활이 없지만, 기습으로 쓰러진 동료는
        // 이 전투의 2턴째부터 살아있는 ★5+ 도사가 부활을 시도할 수 있다(resolveCombatTurn 참고). 누가 쓰러졌는지 전투 동안 기억해 둔다.
        if (PP.toBaseValue(hpAfter) <= 0) {
            int deadCid = intVal(target.get("COMPANION_ID"), 0);
            List<Integer> ambushDead = parseMinionCids(strVal(p.get("CUR_AMBUSH_DEAD_CIDS"), ""));
            if (deadCid > 0 && !ambushDead.contains(deadCid)) {
                ambushDead.add(deadCid);
                String joined = joinMinionCids(ambushDead);
                HashMap<String, Object> adUp = new HashMap<>();
                adUp.put("userName", userName);
                adUp.put("curAmbushDeadCids", joined);
                dao.updateUserProgress(adUp);
                p.put("CUR_AMBUSH_DEAD_CIDS", joined);
            }
        }
    }

    /** [2026-10-08] 보스 은신 급습(79/89층 은신 즉사, 하수인 급습·공격) 경로용 -- 대상이 가호(WARD_COMPANION_ID, 럭키칸 가호/수수께끼 로브 보호막)
     *  보유자이면 이번 피해를 막고 가호를 소모한다. 막았으면 true. (예전엔 "보스방 진입 시 가호가 초기화되니 불필요"라며 빠져 있었지만,
     *  로브 보호막은 보스전 시작(startCombat) 때 걸리므로 이 경로들에서도 막혀야 한다.) */
    private boolean consumeWardIfTarget(String userName, HashMap<String, Object> p, HashMap<String, Object> target, StringBuilder sb, String attackWord) {
        int wardCid = intVal(p.get("WARD_COMPANION_ID"), 0);
        if (wardCid <= 0 || wardCid != intVal(target.get("COMPANION_ID"), 0)) return false;
        HashMap<String, Object> wardClearUp = new HashMap<>();
        wardClearUp.put("userName", userName);
        wardClearUp.put("wardCompanionId", 0);
        dao.updateUserProgress(wardClearUp);
        p.put("WARD_COMPANION_ID", 0);
        String job = strVal(target.get("CLASS"), "WARRIOR");
        String name = strVal(target.get("NAME"), JOB_NAME.getOrDefault(job, "동료"));
        sb.append("🛡️✨ 피해 면역 발동! ").append(jobTag(intVal(target.get("GRADE"), 1), job, name))
          .append("이(가) ").append(attackWord).append("을(를) 완전히 막아냈다! (가호 소모)").append(NL);
        return true;
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

    // [2026-09-21] "60층 미만은 방어력 0, 60층 이후는 방어력을 선형구조로(61층≈100,
    // 99층은 지금 그대로)" 요청 -- 몬스터 DEF만 따로 떼어서 저장값(V1 블록 단위든 V2
    // 선형식이든) 대신 이 층 기반 공식으로 완전히 대체한다(HP/ATK는 기존 방식/V2 그대로
    // 유지, 이 요청 범위 밖). 99층(블록10 보스)은 51층+ 구간이라 이미 밸런스 V2가 적용
    // 중이었고, 그 V2 테이블(TBOT_S5_MONSTER_INFO_V2)의 99층 DEF 실측치가 6096이라
    // 이 값을 "지금 그대로"의 상한 앵커로 고정 -- V2 테이블이 나중에 다시 바뀌면 이
    // 상수도 같이 맞춰야 함.
    private static final int DEF_RAMP_START_FLOOR = 61;
    private static final int DEF_RAMP_END_FLOOR = 99;
    private static final double DEF_RAMP_START_VAL = 100;
    private static final double DEF_RAMP_END_VAL = 6096;

    // [2026-09-22] "전체적으로 몬스터공격력이 너무낮아서 긴장감이 없어" 신고 -- 로그 대신
    // 실측 API(라이브 유저 8명, 24~99층)로 "몬스터 반격 1회가 파티원 HP의 몇%를 깎는지"를
    // 계산해보니 평균 6~12%(약한 구성은 최대 ~19%)로, V2 설계 당시 목표(일반 15%/보스 20%,
    // S5_BALANCE_V2_LINEAR.sql 주석)에 못 미치고 있었음(분석 상세: S5_TOWER_DESIGN.md
    // "몬스터 공격력 적정선 분석"). "즉효+근본 둘 다 적용" 요청으로 두 가지를 함께:
    //   1) 즉효(이 상수들): ATK_VALUE 자체를 목표치까지 전체 상향(51층+ 일반 15%->21%
    //      즉 ×1.4, 보스 20%->29% 즉 ×1.45; 50층 이하는 애초에 스케일 대상도 아니었던
    //      레거시 V1 구간이라 "조금씩"이라는 요청대로 더 작게 ×1.2). DB 마이그레이션 없이
    //      applyHardcoreFloorScale()에서 조회 시점에 곱연산 적용(DEF 선형화와 동일 패턴 --
    //      저장값 자체는 안 건드려서 언제든 상수만 바꾸면 즉시 롤백 가능).
    //   2) 근본(investAtkScale() 참고): 스탯구매+한계돌파 복합 투자배율이 큰 캐릭터일수록
    //      몬스터 반격도 비례해서 세지도록(로스터리 DEF 건과 동일 설계 원리를 ATK에도 적용).
    // [2026-09-22 재조정] 실전 피드백("타락고냥이/바드 유저 몬스터공격시 1씩 닳아, 60~70층
    // 유저도 기습 데미지가 너무 적어") -- ×1.2로는 50층 이하 절대값이 여전히 부족해서
    // ×2.0으로 올림. 근본 원인은 그보다 `Math.max(1, ATK*roll - DEF)` 식 자체가 DEF가
    // 크면(고투자 계정) 원본 공격력과 무관하게 전부 "1"로 바닥날 수 있다는 것 -- 아래
    // monsterHitDamage()에서 "적어도 원본(ATK*roll)의 일정 비율은 방어를 뚫는다"는 최소
    // 보장선을 추가로 둠(기습도 같은 함수 재사용, investAtkScale도 함께 적용).
    private static final double ATK_BOOST_V1_LEGACY = 2.0;   // 50층 이하(레거시 V1)
    private static final double ATK_BOOST_V2_NORMAL = 1.4;   // 51층+ 일반(15%->21% 목표)
    private static final double ATK_BOOST_V2_BOSS = 1.45;    // 51층+ 보스(20%->29% 목표)
    private static final double MONSTER_DMG_MIN_RATIO = 0.5; // 방어력이 아무리 높아도 원본 공격력의 최소 이 비율은 관통

    private double monsterDefForFloor(int floor) {
        if (floor < DEF_RAMP_START_FLOOR) return 0; // 60층 미만(및 60층 자체=마을) 전부 0
        double slope = (DEF_RAMP_END_VAL - DEF_RAMP_START_VAL) / (double) (DEF_RAMP_END_FLOOR - DEF_RAMP_START_FLOOR);
        if (floor <= DEF_RAMP_END_FLOOR) return DEF_RAMP_START_VAL + slope * (floor - DEF_RAMP_START_FLOOR);
        // [2026-09-28] 101층+ 계단 구역 -- 99층 값에서 이어서 61~99층 기울기의 절반으로 계속 증가.
        return DEF_RAMP_END_VAL + slope * STAIR_ZONE_SLOPE_FACTOR * (floor - DEF_RAMP_END_FLOOR);
    }

    /** [2026-09-22] 몬스터가 파티원을 때리는 모든 경로(반격/기습)가 공유하는 최종 피해 계산.
     *  기존엔 `Math.max(1, atk*roll - def)`라 방어력이 크면(고투자 계정) 몬스터 공격력이
     *  아무리 세져도 결과가 그냥 "1"로 바닥나 버렸다("몬스터공격시 1씩 닳아" 신고) -- 방어력이
     *  깎을 수 있는 한도를 원본(atk*roll)의 (1-MONSTER_DMG_MIN_RATIO)까지로 캡핑해서,
     *  "적어도 몬스터기본공격x주사위배율의 절반은 뚫고 들어와야 한다"는 요청대로 최소
     *  보장선을 원본 공격력에 비례하게 만든다. */
    private int monsterHitDamage(int monsterAtk, int roll, int targetDef) {
        int raw = monsterAtk * roll;
        int minGuaranteed = Math.max(1, (int) Math.round(raw * MONSTER_DMG_MIN_RATIO));
        return Math.max(minGuaranteed, raw - targetDef);
    }

    /** [2026-10-01] 201~300층 몬스터 HP/ATK -- V2 표는 200층까지라, 101~200층에서 층당 오르는 폭(선형)을 200층 값에서 그대로
     *  이어 붙인다("100~200층 오르는 수치만큼"). DEF는 monsterDefForFloor가 이미 층 기반 선형식이라 따로 안 한다. */
    private int[] extrapolateMonsterV2(int floor) {
        int[] a = MONSTER_V2.get(STAIR_ZONE_START);
        int[] b = MONSTER_V2.get(200);
        if (a == null || b == null) return null;
        double span = 200 - STAIR_ZONE_START;
        int d = floor - 200;
        int[] out = new int[Math.min(a.length, b.length)];
        for (int i = 0; i < out.length; i++) out[i] = (int) Math.round(b[i] + (b[i] - a[i]) / span * d);
        return out;
    }

    private HashMap<String, Object> applyHardcoreFloorScale(HashMap<String, Object> mon, int floor) {
        if (mon == null) return null;
        // [2026-09-21] 밸런스 V2 오버레이 -- 이름/MONSTER_ID/PP_PER_KILL 등은 V1 조회 결과
        // (mon)를 그대로 두고 HP/ATK만 V2 선형식 값으로 교체한다(DEF는 아래 monsterDefForFloor로
        // 항상 다시 덮어쓰므로 V2 쪽 DEF는 더 이상 안 씀 -- 09-21 후속 요청 "60층 미만은 방어력
        // 0, 60층 이후는 선형"이 V1/V2 구분 없이 DEF 전체에 적용되는 지시라 V2가 이겼든 V1이
        // 이겼든 DEF만큼은 항상 이 새 공식이 최종적으로 이김). V2가 그 층을 다루면 HP/ATK는
        // 무조건 우선 적용되므로, 아래 V1 전용(블록≥6, 보스 제외) 스케일 로직보다 먼저 처리.
        // [2026-09-21 재수정] "몬스터스펙은 바뀌기이전 50층까지는 난이도가 괜찮았다, 50층
        // 이후로 선형구조로 해달라" 요청(실측 버그 신고 포함 -- ★2 전사가 7층 몬스터에게
        // 주사위 8이 떠도 2dmg만 들어가는 등, 1~10층 저층완화(dampen)를 넣어도 DEF가 여전히
        // 초반 파티 ATK 대비 압도적으로 높았음) -- V2 자체가 실측 캘리브레이션이 있던 11~89층
        // 기준으로 설계돼 1~50층은 애초에 검증된 적 없는 외삽 구간이었다. 저층 완화 미봉책
        // 대신 **50층 이하는 V2를 아예 적용하지 않고 원래 V1 그대로**(블록1~5는 무보정 고정값,
        // 이미 "괜찮았다"고 확인된 값) 돌아가게 하고, V2는 51층 이상에서만 적용한다.
        int[] v2 = (BALANCE_V2_ENABLED && floor > 50) ? MONSTER_V2.get(floor) : null;
        if (v2 == null && BALANCE_V2_ENABLED && floor > 200) v2 = extrapolateMonsterV2(floor);
        HashMap<String, Object> scaled = new HashMap<>(mon);
        if (v2 != null) {
            scaled.put("HP_VALUE", v2[0]);
            scaled.put("ATK_VALUE", v2[1]);
        } else if (!"Y".equals(strVal(mon.get("BOSS_YN"), "N")) && blockNo(floor) >= 6) {
            // V1 전용(51~99층, 보스 제외) 블록 내 50%→100% 선형 보간 -- 기존 그대로.
            int pos = floor % 10; // 1~8=사냥터층(이 스케일 대상), 0=마을/9=보스는 몬스터 조회 자체를 안 함
            if (pos >= 1 && pos <= 8) {
                double mult = HARDCORE_FLOOR_SCALE_MIN + (1.0 - HARDCORE_FLOOR_SCALE_MIN) * (pos - 1) / 7.0;
                scaled.put("HP_VALUE", ((Number) mon.get("HP_VALUE")).doubleValue() * mult);
                scaled.put("ATK_VALUE", ((Number) mon.get("ATK_VALUE")).doubleValue() * mult);
            }
        }
        // [2026-09-22] ATK 전체 상향(위 ATK_BOOST_* 주석 참고) -- HP/DEF와 무관하게 ATK_VALUE만
        // 한 번 더 곱연산으로 올린다. V1/V2 어느 경로를 거쳤든 이 시점의 scaled.ATK_VALUE가
        // "이번 요청 전 최종값"이므로 여기서 곱하면 두 경로 모두에 자동 적용된다.
        boolean isBossMon = "Y".equals(strVal(mon.get("BOSS_YN"), "N"));
        double atkBoost = floor > 50 ? (isBossMon ? ATK_BOOST_V2_BOSS : ATK_BOOST_V2_NORMAL) : ATK_BOOST_V1_LEGACY;
        scaled.put("ATK_VALUE", ((Number) scaled.get("ATK_VALUE")).doubleValue() * atkBoost);

        // DEF는 위 HP/ATK 경로(V1/V2)와 무관하게 층 기반 공식으로 완전히 대체(보스 포함 전부).
        scaled.put("DEF_VALUE", monsterDefForFloor(floor));
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
    // [2026-09-21] "91층 이후도 밸런스 완성되었으면 100층까지 갈수있도록 열어주자" 요청 --
    // 블록10(91~99, V2 밸런스가 이미 floor>50 전체에 적용 중이라 별도 수치 작업 불필요,
    // TBOT_S5_FLOOR_INFO.TILE_COUNT/TBOT_S5_MONSTER_INFO 이름·PP도 91~98/99보스 모두 09-18에
    // 블록9와 함께 미리 채워져 있었음, 확인 완료)까지 오픈. 실제 콘텐츠는 99층(보스)이 끝이라
    // "100층"은 열리는 게 아니라 여전히 다음 잠금선(그 이상은 콘텐츠 자체가 없음)으로 남는다.
    // [2026-09-28] 101~200층 "계단 구역" 오픈으로 잠금선을 201로 상향(STAIR_ZONE_* 참고).
    private static final int CONTENT_LOCKED_FLOOR = 201;

    // [2026-09-28] "101층부턴 선형구조로 밸런스를잡아주고, 한층마다 9개의칸으로 구성되도록
    // 해줘. 계단-전투-전투-행운-중간보스-전투-전투-행운-중간보스 이렇게 틀을 잡아서 구성하고,
    // 행운칸에는 고대의유적이나 행운/함정 이 다같이 나오는 랜덤칸으로 해줘. 그리고 맵은
    // 계단식으로 해줘" 요청 -- 101층부터는 1~100층의 "10층 블록(사냥터 8층+보스층+마을)"
    // 구조를 쓰지 않는 별도 "계단 구역"이다:
    //  - 층마다 9칸 고정 순서(STAIR_ZONE_PATTERN, 섞지 않음). 주사위로 칸을 건너뛰면 이 순서
    //    자체가 의미가 없어지므로 이동은 항상 1칸씩(주사위는 전투에서만 굴림), 9번째
    //    중간보스까지 넘으면 다음 이동에서 바로 한 층 위 1번(계단) 칸으로 올라간다.
    //  - X9층=보스층, X0층=마을 같은 층번호 규칙이 없다(모든 층이 같은 9칸 구성). 출입용
    //    마을은 100층 하나뿐(STAIR_ZONE_BASE_CAMP) -- /층변경 0 으로 돌아가 부활, 다시 올 땐
    //    /층변경 <층번호>로 이미 가본 층까지 바로 이동(매번 101층부터 다시 오르지 않게).
    //  - "중간보스"는 1~100층처럼 전투칸의 확률 조우가 아니라 전용 칸(MIDBOSS)이라 밟으면
    //    항상 등장(기존 중간보스 3배 배율 그대로). 일반 전투칸은 한 마리씩(61층+ 2마리 규칙
    //    미적용 -- 09-20 로드맵 "계단 한 칸 = 몬스터 한 마리"와 일치).
    //  - 행운칸(RANDOM_LUCKY)은 밟는 순간 고대의 유적(=기존 무너진 사원 효과, 사용자 확인)/
    //    럭키/함정 중 하나로 1/3씩 결정.
    //  - 오픈 범위는 우선 101~200층(사용자 확인) -- 몬스터 수치/칸 수는
    //    S5_STAIR_ZONE_101_200.sql로 채워둠.
    private static final int STAIR_ZONE_BASE_CAMP = 100;
    private static final int STAIR_ZONE_START = 101;
    // [2026-10-01] "200~300층 사냥터 오픈 준비" -- 데이터/로직은 300층까지 준비(STAIR_ZONE_READY_MAX)해 두고, 실제로 갈 수 있는 상한은
    // TBOT_S5_CONFIG의 STAIR_ZONE_MAX_FLOOR(기본 200, /갱신으로 반영)로 연다. 200 -> 300으로 바꾸면 그 순간 오픈.
    // [2026-10-08] "300층 이후 500층까지도 가능하도록 미리" 요청 -- 준비 상한을 300 -> 500으로(층 정보 301~500은 S5_FLOOR_301_500.sql). 몬스터 HP/ATK는
    // extrapolateMonsterV2가 200층 값에서 층당 오르는 폭 그대로 이어 붙이고, DEF/PP 보상/주사위 최솟값/스탯 상한도 층 기반 공식이라 별도 표가 필요 없다.
    private static final int STAIR_ZONE_READY_MAX = 500;
    private static volatile int stairZoneOpenMax = 200;
    private static final String[] STAIR_ZONE_PATTERN = {
        "STAIRS_UP", "COMBAT", "COMBAT", "RANDOM_LUCKY", "MIDBOSS",
        "COMBAT", "COMBAT", "RANDOM_LUCKY", "MIDBOSS"
    };
    // TBOT_S5_MONSTER_INFO(이름/PP_PER_KILL 기준행)가 있는 마지막 블록 -- 101층+는 블록 행이
    // 따로 없으므로 블록10 행을 기준으로 쓰고, 실제 HP/ATK/DEF는 V2 오버레이가 층별로 덮어쓴다.
    private static final int MONSTER_MAX_BLOCK = 10;
    // "기울기를 조금 더 완만하게"(사용자 확인) -- 101층+ HP/ATK/DEF 증가폭 = 1~99층 증가폭의 절반.
    private static final double STAIR_ZONE_SLOPE_FACTOR = 0.5;

    // [2026-09-29] "100층 이후는 100층마다 마을이 존재, 나머지는 다 계단식 일반사냥터" 요청 --
    // 마을은 100/200/300...층(100의 배수), 그 사이 층은 전부 9칸 고정 계단층(109/110층 포함).
    private boolean isStairZone(int floor) {
        return floor >= STAIR_ZONE_START && floor % 100 != 0;
    }

    /** 계단 구역에서 "돌아갈 마을" -- 지금 층 이하 가장 가까운 100의 배수 마을(101~199층은 100층, 201~299층은 200층). */
    private int stairCampOf(int floor) {
        return Math.max(STAIR_ZONE_BASE_CAMP, (floor / 100) * 100);
    }

    /** selectMonster()용 블록 번호 -- 블록 행이 없는 100층 이상은 마지막 블록(10) 행을 쓴다. */
    private int monsterBlockNo(int floor) {
        return Math.min(blockNo(floor), MONSTER_MAX_BLOCK);
    }

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
    // [2026-09-30] "몬스터 선공 기습시 최대데미지 제한없게, 몬스터의 기본데미지만큼 피해를 받게" 요청 --
    // 2만 상한 제거(MAX_AMBUSH_DMG 삭제). 즉사 방지 90% 클램프(applyAmbushHit)는 별개 안전장치라 유지.
    private static final double AMBUSH_SPLIT_THRESHOLD_PCT = 0.9;
    private static final double AMBUSH_DEATH_GUARD_PCT = 0.9;
    // [2026-09-20] "데미지가 낮을 때도 많아야 한다, 평소공격력의 50%로" 요청 -- 기습 굴림의
    // 기준 공격력을 몬스터 평소 공격력(ATK_VALUE 기반 정상 공식)의 이 비율만큼만 쓴다.
    // [2026-09-30] 위 50% 감쇠도 같은 요청으로 1.0(반격과 동일하게 몬스터 기본 공격력 100%)으로 되돌림.
    private static final double AMBUSH_ATK_PCT = 1.0;

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
        // [2026-09-28] 101층+ 계단 구역 -- 무작위 배치 대신 9칸 고정 순서 그대로(섞지 않음).
        // 순서 자체가 설계이므로 아래 무작위 생성/셔플 로직을 전부 건너뛴다.
        boolean stairZone = isStairZone(floor);
        if (stairZone) {
            types.addAll(java.util.Arrays.asList(STAIR_ZONE_PATTERN));
        }
        if (!stairZone) {
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
        } // !stairZone

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
        if (floor >= 51 && !stairZone) { // 계단 구역은 탐사/체크포인트 개념 없음(층 도착 시 항상 1번 칸부터)
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

    // [2026-09-21] "91층부턴 탐사율90%이상에만 올라갈수있도록 해줘" 요청 -- 50~90층대의 계단별
    // 0/10/20/25% 단계표 대신, 91층 이상은 어느 계단을 밟든 균일하게 90%를 요구한다(resolveTile
    // STAIRS_UP 분기 참고).
    private static final int STAIRS_UP_REQUIRE_PCT_ENDGAME = 90;
    private static final int STAIRS_UP_ENDGAME_FLOOR = 91;

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
    /** 랭킹 제외 유저 목록(RANK_EXCLUDE_USERS). */
    private List<String> rankExcludeUsers() {
        List<String> out = new ArrayList<>();
        String v = RANK_EXCLUDE_USERS;
        if (v == null) return out;
        for (String u : v.split("\\|")) if (!u.trim().isEmpty()) out.add(u.trim());
        return out;
    }

    @Override
    public String ranking(String userName) {
        List<String> ex = rankExcludeUsers(); // [2026-10-22] 관리자(테스트) 계정은 랭킹 집계에서 제외
        int maxFloor = dao.selectMaxFloorReached(ex);
        int maxKill = dao.selectMaxTotalKillCount(ex);
        int maxAch = dao.selectMaxAchievementCount(ex);
        int maxExplored = dao.selectMaxFullyExploredCount(ex);
        int maxCompanion = dao.selectMaxCompanionCount(ex);
        int maxCompanionGrade = dao.selectMaxCompanionGrade(ex);
        // [2026-09-15] "최고 동료/장비 등급을 갯수까지 포함해서" 요청 -- 처음엔 서버 전체
        // 합산치(여러 유저가 나눠 가진 걸 다 더한 값)로 잘못 구현했었는데("13명이라고
        // 나오는데 실제로 한 사람이 최고로 가진 명수를 적어달라" 신고로 확인), 최고 등급
        // 컴패니언/장비를 "한 명이 최고 몇 개 보유했는지"(개인별 집계의 MAX)로 정정.
        // "누가"는 여전히 비공개.
        int maxCompanionGradeCount = dao.selectMaxCompanionGradeCount(ex);
        int maxEquip = dao.selectMaxEquipCount(ex);
        int maxEquipGrade = dao.selectMaxEquipGrade(ex);
        int maxEquipGradeCount = dao.selectMaxEquipGradeCount(ex);
        // [2026-09-15] 악세서리(목걸이/반지/팔찌) 신설로 "장비"와 분리된 전용 랭킹 통계.
        int maxAccessory = dao.selectMaxAccessoryCount(ex);
        int maxAccessoryGrade = dao.selectMaxAccessoryGrade(ex);
        int maxAccessoryGradeCount = dao.selectMaxAccessoryGradeCount(ex);

        PP maxPp = PP.fromPP(0);
        for (HashMap<String, Object> row : dao.selectAllUserPp(ex)) {
            PP v = PP.of(((Number) row.get("PP_VALUE")).doubleValue(), strVal(row.get("PP_EXT"), ""));
            if (v.compare(maxPp) > 0) maxPp = v;
        }

        // 본인 수치 -- 미등록/조회 실패 시 전부 0/빈 값으로 둬서 (me) 표시가 안 붙게만 하고
        // 그 외엔 정상 진행(랭킹판 자체는 로그인 여부와 무관하게 항상 보여준다).
        // 랭킹에서 빠진 유저(관리자 테스트 계정)는 (me)도 붙이지 않는다 -- 남의 기록과 비교하는 의미가 없음
        HashMap<String, Object> mineP = userName == null || userName.trim().isEmpty() || ex.contains(userName.trim()) ? null : dao.selectUserProgress(userName);
        int mineFloor = 0, mineKill = 0, mineAch = 0, mineExplored = 0;
        int mineCompanion = 0, mineCompanionGrade = 0, mineEquip = 0, mineEquipGrade = 0;
        int mineAccessory = 0, mineAccessoryGrade = 0;
        int mineCompanionGradeCount = 0, mineEquipGradeCount = 0, mineAccessoryGradeCount = 0; // 서버 최고 등급을 몇 개 보유했는지
        PP minePp = PP.fromPP(0);
        if (mineP != null) {
            mineFloor = intVal(mineP.get("MAX_FLOOR_REACHED"), 0);
            mineKill = intVal(mineP.get("TOTAL_KILL_COUNT"), 0);
            mineAch = dao.selectUserAchievements(userName).size();
            mineExplored = dao.countFullyExploredFloors(userName);
            mineCompanion = dao.countUserCompanions(userName);
            for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
                int cg = intVal(c.get("GRADE"), 0);
                if (cg == GROWTH_GRADE) continue; // 성장형(☆1)은 최고 등급 집계 제외
                if (cg > mineCompanionGrade) { mineCompanionGrade = cg; mineCompanionGradeCount = 0; }
                if (cg == mineCompanionGrade) mineCompanionGradeCount++;
            }
            mineEquip = dao.countUserEquip(userName);
            mineAccessory = dao.countUserAccessory(userName);
            for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
                int eg = intVal(e.get("GRADE"), 0);
                String part = strVal(e.get("PART"), "");
                if ("NECKLACE".equals(part) || "RING".equals(part) || "BRACELET".equals(part)) {
                    if (eg > mineAccessoryGrade) { mineAccessoryGrade = eg; mineAccessoryGradeCount = 0; }
                    if (eg == mineAccessoryGrade) mineAccessoryGradeCount += qtyOf(e);
                } else {
                    if (eg > mineEquipGrade) { mineEquipGrade = eg; mineEquipGradeCount = 0; }
                    if (eg == mineEquipGrade) mineEquipGradeCount += qtyOf(e);
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
          .append(meTagGrade(mineCompanionGrade, mineCompanionGradeCount, maxCompanionGrade, maxCompanionGradeCount)).append(NL);
        sb.append("🎽 최다 장비 보유: ").append(maxEquip).append("개").append(meTag(mineEquip, maxEquip)).append(NL);
        sb.append("💎 최고 장비 등급: ★").append(maxEquipGrade).append(" (최다 보유자 ").append(maxEquipGradeCount).append("개)")
          .append(meTagGrade(mineEquipGrade, mineEquipGradeCount, maxEquipGrade, maxEquipGradeCount)).append(NL);
        sb.append("💍 최다 악세서리 보유: ").append(maxAccessory).append("개").append(meTag(mineAccessory, maxAccessory)).append(NL);
        sb.append("🔮 최고 악세서리 등급: ★").append(maxAccessoryGrade).append(" (최다 보유자 ").append(maxAccessoryGradeCount).append("개)")
          .append(meTagGrade(mineAccessoryGrade, mineAccessoryGradeCount, maxAccessoryGrade, maxAccessoryGradeCount)).append(NL);
        sb.append("💰 최다 누적 PP: ").append(maxPp.format())
          .append((PP.toBaseValue(maxPp) > 0 && minePp.compare(maxPp) >= 0) ? " (me)" : "");
        return sb.toString();
    }

    /**
     * ranking() 전용 -- "최고 등급(최다 보유자 N개)" 줄용. [2026-09-29] "6성 하나만 있어도 (me)가 나온다, 가장
     * 많이 가진(15개) 사람만 (me)" 신고 -- 등급만 최고 등급이면 붙던 걸, 그 최고 등급을 가장 많이 보유한
     * 개수(maxCount, 동타 포함)까지 채워야 붙도록 정정.
     */
    private String meTagGrade(int mineGrade, int mineCount, int maxGrade, int maxCount) {
        return (maxGrade > 0 && mineGrade >= maxGrade && mineCount >= maxCount) ? " (me)" : "";
    }

    /**
     * [2026-09-29] "전투메세지에서 1만이 넘으면 1a로 표시하는데, 체력과 데미지는 a로 안 나오게 통일" 요청 --
     * 전투 메시지의 HP/피해 표기용. PP.format()은 1만 이상을 "1a" 단위로 줄이므로, 단위 없이 기본 수치
     * (toBaseValue)를 그대로 숫자로 찍는다(공격 데미지/eff[0] 등 int 값들이 원래 그렇게 찍히던 것과 통일).
     * PP(재화) 표기는 그대로 format()을 쓴다.
     */
    private static String plainNum(PP pp) {
        return String.valueOf(PP.toBaseValue(pp));
    }

    /** ranking() 전용 -- max가 0(아직 아무도 없음)이 아니고 mine이 max 이상(동타 포함)이면 " (me)". */
    private String meTag(int mine, int max) {
        return (max > 0 && mine >= max) ? " (me)" : "";
    }

    /** 사냥터층(구간 내 1~8번째) PP 보상 배율: 1층 1.0배, 2층 1.1배 ... 8층 1.7배로 층마다 조금씩 차이. 보스/마을층은 1.0배. */
    private double floorPpMultiplier(int floor) {
        // [2026-09-28] 101층+ 계단 구역은 X0/X9 층 규칙이 없으므로 층 번호 기준 선형 증가로 --
        // 98층(블록10 마지막 사냥터층, 1.7배)에서 이어서 층당 +0.01배(완만하게).
        if (isStairZone(floor)) return 1.7 + 0.01 * (floor - STAIR_ZONE_BASE_CAMP);
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
        boolean stairZoneFloor = isStairZone(floor);
        boolean isHuntFloor = stairZoneFloor || (fpos >= 1 && fpos <= 8); // 계단 구역은 모든 층이 전투층
        sb.append("🗼 ").append(floor).append("층");
        // [2026-09-21] "88층 권장전투력(99813)보다 종합전투력(111008)이 더 큰데, 추천 사냥터는
        // 오히려 더 낮은 78층으로 나온다 -- 모순 아니냐" 신고. 실제로는 모순이 아니라, 여기
        // 라벨이 그 층 몬스터의 "원본" 전투력(1배)이고, 추천(recommendHuntFloor)은 안전마진
        // SAFE_HUNT_RATIO(1.3배) 이상을 요구해서 기준 자체가 서로 달랐다(88층: 99813 vs
        // 안전기준 129757 > 111008이라 88층은 추천에서 빠짐). "권장전투력"이라는 이름이
        // 마치 "이 값만 넘으면 여기서 사냥해도 안전하다"처럼 읽혀서 오해를 낳았으므로,
        // 라벨을 "몬스터 전투력"(원본, 안전마진 미포함)으로 바꾸고 안전기준(margin 적용값)도
        // 같이 보여줘서 두 수치가 왜 다른 결론을 내는지 한눈에 보이게 한다.
        if (isHuntFloor) {
            // [2026-10-08] "현재 전투력/추천층이 의미가 없는 것 같다" 요청 -- 추천 사냥터와 그 근거인 "안전기준"(몬스터 전투력의 1.3배)을 없앴다.
            long monPower = floorMonsterCombatPower(floor);
            sb.append(" (몬스터 전투력 ").append(monPower).append(")");
        } else sb.append(" (").append(floorKindLabel(floor)).append(")");
        sb.append(NL);
        if (stairZoneFloor) {
            // [2026-09-28] 계단 구역은 탐사율 대신 "9칸 중 몇 번째 칸인지"로 진행도를 보여준다.
            HashMap<String, Object> myUfp = dao.selectUserFloorProgress(target, floor);
            int stepNow = myUfp == null ? 1 : Math.max(1, intVal(myUfp.get("CUR_TILE"), 1));
            sb.append("🪜 계단 구역 진행 ").append(stepNow).append("/").append(STAIR_ZONE_PATTERN.length).append("단계").append(NL);
        } else if (isHuntFloor) {
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

        // ── PP + 편성 동료 ──
        PP totalEarned = PP.of(numVal(p.get("TOTAL_PP_EARNED_VALUE"), 0), strVal(p.get("TOTAL_PP_EARNED_EXT"), ""));
        sb.append("💰 보유 ").append(pp.format()).append(" PP · 누적획득 ").append(totalEarned.format()).append(" PP").append(NL);
        // [2026-09-10] "최고티어 동료 3명의 등급/직업도 표기해달라" 요청 -- 파티 편성 여부와
        // 무관하게 "보유한 동료 중" 등급이 가장 높은 3명을 뽑아서 "★등급직업" 형태로 보여준다.
        // [2026-09-17] "이름은 빼고 성급/직업만, 쉼표로만 구분해줘" 요청으로 이름 표기 제거.
        // [2026-10-23] "최고동료 대신 현재 편성 동료 및 동료별 전투력" 요청 -- 보유 최고 등급 3명 대신 파티 슬롯 순서대로 편성 동료와 각자의 전투력.
        List<HashMap<String, Object>> allCompanions = dao.selectUserCompanions(target);
        List<HashMap<String, Object>> party = new ArrayList<>();
        for (HashMap<String, Object> c : companionsWithEffectiveStats(target)) if (c.get("PARTY_SLOT") != null) party.add(c);
        party.sort((a, b) -> intVal(a.get("PARTY_SLOT"), 0) - intVal(b.get("PARTY_SLOT"), 0));
        long myPower = 0;
        if (!party.isEmpty()) {
            sb.append("🛡️ 편성 동료").append(NL);
            for (HashMap<String, Object> c : party) {
                long power = combatPower(intVal(c.get("EFF_HP"), 0), intVal(c.get("EFF_ATK"), 0), intVal(c.get("EFF_DEF"), 0));
                myPower += power;
                String job = JOB_NAME.getOrDefault(strVal(c.get("CLASS"), "WARRIOR"), "동료");
                String name = strVal(c.get("NAME"), "");
                sb.append(intVal(c.get("PARTY_SLOT"), 0)).append(". ").append(gradeMark(intVal(c.get("GRADE"), 1))).append(" ").append(job)
                  .append(name.isEmpty() || name.equals(job) ? "" : "(" + name + ")").append(" · 전투력 ").append(power).append(NL);
            }
        }
        sb.append(NL);

        // ── 전투력 + 누적 통계 ──
        // [2026-09-15 신설] "전투력을 수치화 시켜고, 그 수치를 보여주고, 층별 전투력을
        // 나타내 주고, 현재 몇층이 괜찮은 사냥터인지 추천해달라" 요청.
        // [2026-10-23] 종합전투력은 위 편성 동료 목록에서 같이 합산(편성 동료 전투력의 합)
        if (myPower <= 0) {
            sb.append("⚡ 종합전투력: 0 (파티에 동료를 편성하면 계산됩니다)").append(NL);
        } else {
            sb.append("⚡ 종합전투력: ").append(myPower);
            // [2026-10-08] 추천 사냥터(🎯추천: N층) 표시 제거 -- 전투력 수치만 보여준다.
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
            if ("NECKLACE".equals(part) || "RING".equals(part) || "BRACELET".equals(part)) accessoryCount += qtyOf(e);
            else equipCount += qtyOf(e);
        }
        sb.append("🎽 장비 보유: ").append(equipCount).append("개").append(NL);
        sb.append("💍 악세서리 보유: ").append(accessoryCount).append("개").append(NL);
        // [2026-09-21] "/ㅌㅈㅂ에 악세사리보유 아래에 전설조각 몇개인지도 표기해줘" 요청.
        sb.append("🧩 전설의 조각: ").append(intVal(p.get("LEGEND_FRAGMENT"), 0)).append("개").append(NL);

        sb.append(NL).append("🖥️ 웹으로 보기: ").append(towerViewLink(target)).append(NL);
        sb.append("👉 전체 명령어는 /탑도움말 을 입력해 확인하세요.");
        return sb.toString();
    }

    private String floorKindLabel(int floor) {
        if (isStairZone(floor)) return "계단층";
        int m = floor % 10;
        if (m == 0) return "마을";
        if (m == 9) return "보스층";
        return "사냥터";
    }

    /** 전투 로그 표시용 "★등급직업(이름)" 포맷. 예: ★3궁수(나나) */
    private String jobTag(int grade, String job, String name) {
        return gradeMark(grade) + JOB_NAME.getOrDefault(job, "동료") + "(" + name + ")";
    }

    /**
     * 파티 시너지 판정("3캐릭부터만 효과가 나도록" 요청) -- 파티가 정확히 3명이고, 그 3명이
     * 전부 같은 직업(모노 조합)이거나 전부 다른 직업(균형 조합, "레인보우")일 때만 발동한다.
     * 3명 중 2명만 같은 직업(예: 전사2+마법사1)이거나 파티가 3명 미만이면 시너지 없음(null).
     * @return "WARRIOR"/"MAGE"/"ROGUE"/"ARCHER"/"PRIEST"(해당 직업 모노 3인) 또는
     *         "RAINBOW"(3직업 전부 다름), 조건 미충족 시 null.
     */
    // [2026-10-23] 4인 파티(80층+) -- "정확히 3명" 대신 3명 이상: 전원 같은 직업이면 모노, 전원 다른 직업이면 균형(4명이면 4직업 모두 달라야 함).
    private String detectPartySynergy(List<HashMap<String, Object>> party) {
        if (party.size() < 3) return null;
        java.util.Set<String> jobs = new java.util.HashSet<>();
        for (HashMap<String, Object> c : party) jobs.add(strVal(c.get("CLASS"), "WARRIOR"));
        if (jobs.size() == 1) return jobs.iterator().next();
        if (jobs.size() == party.size()) return "RAINBOW";
        return null;
    }

    // [2026-10-23] "동료를 4명까지, 80층 이상부터 열리는 기능" 요청 -- 최고 도달층(MAX_FLOOR_REACHED)이 80층 이상이면 파티 4번 슬롯이 열린다.
    private static final int PARTY4_UNLOCK_FLOOR = 80;

    private int partyMax(HashMap<String, Object> p) {
        return p != null && intVal(p.get("MAX_FLOOR_REACHED"), 0) >= PARTY4_UNLOCK_FLOOR ? 4 : 3;
    }

    // [2026-10-23] "4번째 동료는 30% 약화된 채로 등장, 1층 등반 시마다 0.1%씩 해제(최대 0%)" 요청 -- 파티 4번 슬롯 동료의 체력/공격/방어에
    // (1 - 약화율)을 곱한다. 약화율 = 30% - 0.1% x (최고 도달층 - 80), 0% 미만이 되면 0(380층 이상은 약화 없음). 최고 도달층은 userStat.GROWTH_MAXF.
    private static final double PARTY4_PENALTY_START = 0.30;
    private static final double PARTY4_PENALTY_PER_FLOOR = 0.001;

    private double party4Penalty(HashMap<String, Object> userStat) {
        int maxF = userStat == null ? 0 : intVal(userStat.get("GROWTH_MAXF"), 0);
        return Math.max(0, PARTY4_PENALTY_START - PARTY4_PENALTY_PER_FLOOR * Math.max(0, maxF - PARTY4_UNLOCK_FLOOR));
    }

    /** computeEffectiveStat 결과에 4번 슬롯 약화를 반영(4번 슬롯이 아니거나 약화가 끝났으면 그대로). [hp, atk, def, ...] 중 앞 3개만. */
    private int[] slotPenalty(int[] eff, HashMap<String, Object> c, HashMap<String, Object> userStat) {
        if (eff == null || c == null || intVal(c.get("PARTY_SLOT"), 0) != 4) return eff;
        double k = 1 - party4Penalty(userStat);
        if (k >= 1) return eff;
        int[] out = eff.clone();
        for (int i = 0; i < 3 && i < out.length; i++) out[i] = Math.max(1, (int) Math.round(out[i] * k));
        return out;
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
            case "SHAMAN":  return "🔮 " + JOB_NAME.get("SHAMAN") + " 3인조 시너지! " + SHAMAN_SKILL_WARD + "·" + SHAMAN_SKILL_RELEASE + " 확률 +20%p, 효과 수치 x1.5" + NL;
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
        sb.append("/계단내려가기 (/탑내려가기, /계단다운)").append(NL);
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
        sb.append("/계단내려가기 · /탑올라가기 : 마을끼리 위/아래 구간 이동(올라가기는 보스 처치 후만), 101층+에선 계단내려가기가 그 구간 100층 마을로 바로 이동").append(NL);
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
        HashMap<String, Object> p = dao.selectUserProgress(userName); // [2026-09-30] 같은 조회를 두 번 하던 것을 한 번으로
        boolean brandNew = p == null;
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

        // [2026-10-22] 전투에 영향이 가는 패치 -- 패치 내용을 확인하기 전, 또는 전설장비 착용 규칙에 어긋난 장비가 남아 있으면 굴리지 않고 돌려보낸다.
        HashMap<String, Object> gate = diceGate(userName);
        if (gate != null) return strVal(gate.get("text"), "");

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
        if (!"Y".equals(strVal(p.get("NO_COOLDOWN_YN"), "N"))) rememberDiceCooldown(userName, nextCooldownSec);
        return prependAutoHunt(autoHuntMsg, result);
    }

    // ---- [2026-10-22] 연타 대응: 쿨타임을 메모리에서 먼저 판정 ----
    // 자주 누르는 유저의 주사위는 대부분 쿨타임에 막히는데, 예전엔 막히기 전에 진행 조회/전설 규칙 검사(장비 전체)/자동사냥 정산 조회를 다 하고
    // 거절했다(거절 한 번에 쿼리 여러 개). 굴림이 커밋되면 "마지막 굴림 시각(초 단위로 자름), 쿨타임"을 메모리에 남기고,
    // 컨트롤러가 서비스를 부르기 전에 이걸로 먼저 거절한다(DB/트랜잭션 없음). DB 판정과 어긋나지 않게 항상 1초 느슨하게 보고,
    // 마지막 1초와 메모리에 없는 경우(서버 재시작 직후 등)는 기존 DB 판정(checkDiceCooldown)이 그대로 처리한다.
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> DICE_COOLDOWN_MEM = new java.util.concurrent.ConcurrentHashMap<>();

    private void rememberDiceCooldown(final String userName, final long cooldownSec) {
        if (cooldownSec <= 1) return;
        final long now = System.currentTimeMillis();
        final long[] v = { now - now % 1000, cooldownSec };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            // 굴림이 롤백되면 DB엔 쿨타임이 안 남으니 메모리에도 남기지 않는다(커밋된 뒤에만 기록)
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() { DICE_COOLDOWN_MEM.put(userName, v); }
                    });
        } else {
            DICE_COOLDOWN_MEM.put(userName, v);
        }
    }

    /** 메모리로 판정한 쿨타임 거절 메시지(checkDiceCooldown과 같은 문구), 판정 못 하면 null. DB를 쓰지 않는다. */
    @Override
    public String diceCooldownFast(String userName) {
        if (userName == null) return null;
        long[] c = DICE_COOLDOWN_MEM.get(userName);
        if (c == null) return null;
        long elapsedSec = (System.currentTimeMillis() - c[0]) / 1000;
        if (elapsedSec >= c[1] - 1) { // 마지막 1초(DB 시각 반올림/시계 차이 여유)는 DB 판정에 맡긴다
            DICE_COOLDOWN_MEM.remove(userName, c);
            return null;
        }
        return "⏳" + userName + "님, 아직 쿨타임입니다! " + (c[1] - elapsedSec) + "초 후 다시 시도해주세요. (쿨타임 " + c[1] + "초)";
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
        // 한도 폐지(0 이하) 상태에서는 null을 내려서 웹이 사용량/한도 배지를 숨긴다.
        info.put("webLimit", DAILY_MOVE_LIMIT > 0 ? (Object) DAILY_MOVE_LIMIT : null);
        info.put("totalLimit", DAILY_MOVE_LIMIT > 0 ? (Object) (DAILY_MOVE_LIMIT + KAKAO_BONUS_MOVE) : null);
        return info;
    }

    private String checkAndBumpDailyDiceLimit(String userName, HashMap<String, Object> p, String channel) {
        if (DAILY_MOVE_LIMIT <= 0) return null; // [2026-10-20] 하루 이동 한도 폐지 -- 막지 않고 카운트(DB 쓰기)도 하지 않는다
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
        // [2026-09-28] 101층+ 계단 구역은 X0=마을/X9=보스층 규칙이 없으므로(모든 층이 9칸 전투층)
        // 아래 m 기반 분기(마을 안내/보스전 직행)를 전부 건너뛴다.
        boolean stairZone = isStairZone(floor);
        // [2026-09-17] "파티 전멸 후에도 다음 주사위를 쓸 수 있던데" 신고 -- 마을(m==0)에서는
        // 계속 움직일 수 있어야 하므로(회복이 마을에서만 일어남) 사냥터/보스층에서만 막는다.
        if ((stairZone || m != 0) && isPartyWiped(userName)) {
            if (stairZone) {
                return userName + "님," + NL + "💀 파티 전원이 전투불가 상태입니다. /층변경 0 으로 " + stairCampOf(floor)
                        + "층 마을로 돌아가야 부활합니다. (/계단내려가기로도 이동, 다시 올 땐 /층변경 " + floor + " 처럼 가본 층으로 바로 이동 가능)";
            }
            return userName + "님," + NL + "💀 파티 전원이 전투불가 상태입니다. 마을로 돌아가야 부활합니다. (/층변경 0 또는 /계단내려가기)";
        }
        if (!stairZone && m == 0) {
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
            if (floor >= STAIR_ZONE_BASE_CAMP) {
                return userName + "님," + NL + "🏘️ " + floor + "층 마을 -- 이 위는 한 층씩 오르는 계단 구역입니다." + NL
                        + "👉 /층변경 1 로 " + (floor + 1) + "층부터 오르거나, /층변경 <층번호> 로 가본 층까지 바로 이동하세요.";
            }
            return userName + "님," + NL + "🏘️ 여기는 마을입니다. 웹 상점(" + TOWER_VIEW_URL + ")을 이용하거나 /층변경 N 으로 사냥터에 진입하세요. (전체 명령어는 /탑도움말)";
        }
        if (!stairZone && m == 9) {
            return startCombat(userName, p, floor, true, false, false);
        }

        // ── 사냥터 보드: 끝 없이 순환하는 루프. 계단(STAIRS) 칸을 밟으면 다음 층 이동
        //    "자격"만 얻고(MAX_FLOOR_REACHED 갱신), 실제 이동은 /층변경 N 을 직접 입력해야 한다. ──
        HashMap<String, Object> fi = dao.selectFloorInfo(floor);
        int tileCount = fi == null ? 8 : intVal(fi.get("TILE_COUNT"), 8);
        HashMap<String, Object> ufp = dao.selectUserFloorProgress(userName, floor);
        int curTile = ufp == null ? 0 : intVal(ufp.get("CUR_TILE"), 0);

        // [2026-09-28] 계단 구역 -- 9번째 칸(마지막 중간보스)까지 끝냈으면 이번 이동은 칸 이동이
        // 아니라 한 층 위로 오르기. 도착 기록이 없으면(예외 상황) 1번 계단 칸에 서 있는 걸로 본다.
        if (stairZone) {
            if (curTile < 1) curTile = 1;
            if (curTile >= tileCount) return climbStairZone(userName, p, floor);
        }

        int diceMax = diceMax(strVal(p.get("DICE_GRADE"), "DICE_6"));
        // [2026-09-09] 특수칸에서 건 "다음 이동 1회, 주사위 2개" 플래그(handleSpecialTile 참고)
        // 소모 -- 소모는 여기 이동 굴림에서만 하고(전투 공격 굴림엔 관여 안 함), 결과는 두 눈을
        // 더한 값. rollLabel은 화면에 "3+5=8"처럼 두 눈을 그대로 보여주기 위한 표시용 문자열.
        // [2026-09-28] 계단 구역은 이동에 주사위를 안 쓰므로(항상 1칸) 더블주사위도 여기서
        // 소모하지 않는다 -- 다음 전투 공격 턴에서 소모됨(resolveCombatTurn 참고).
        boolean doubleDice = !stairZone && "Y".equals(strVal(p.get("DOUBLE_DICE_YN"), "N"));
        int roll;
        String rollLabel;
        if (stairZone) {
            roll = 1;
            rollLabel = "1";
        } else if (doubleDice) {
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
        // [2026-09-28] 계단 구역은 9칸을 순서대로 밟는 구조라 "탐사"가 매번 자동 100%가 됨 --
        // 탐사 체크포인트/완전탐사 업적·뽑기권/50% 악세권/구간 선택권 같은 탐사 보상 체계는
        // 적용하지 않는다(층마다 자동 지급되면 100개 층 x 보상으로 경제가 무너지고, 업적 ID
        // 대역(100+floor, 500+floor)도 101층+는 등록된 업적이 없는 번호라 부적절).
        if (!stairZone && floor >= 51 && tileCount > 0) {
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
        if (stairZone) {
            sb.append("🪜 한 계단 올랐다! ").append(curTile).append(" → ").append(newTile).append("번 칸")
              .append(NL).append("🗺️ ").append(floor).append("층 ").append(newTile).append("/").append(tileCount).append("단계");
            if (newTile < tileCount) {
                String nextType = STAIR_ZONE_PATTERN[newTile]; // 다음 칸(0-based index = newTile)
                sb.append(" (다음: ").append(TILE_LABEL.getOrDefault(nextType, nextType)).append(")");
            } else {
                sb.append(" (마지막 칸 -- 다음 이동 시 ").append(floor + 1).append("층으로)");
            }
        } else {
        sb.append(doubleDice ? "🎲🎲 주사위 " : "🎲 주사위 ").append(rollLabel).append("! ").append(curTile).append(" → ").append(newTile).append("번 칸")
          .append(NL).append("🗺️ 탐사 현황: ").append(floor).append("층 .. ");
        // "25/25 완전탐사면 그냥 탐사완료라고만 띄워달라" 요청
        if (visited >= tileCount) {
            sb.append("탐사완료");
        } else {
            sb.append(visited).append("/").append(tileCount).append("칸 발견");
        }
        } // !stairZone (메시지)
        if (!stairZone && visited >= tileCount) {
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
        String halfExploreReward = stairZone ? null : checkExploreHalfReward(userName, p, floor, visited, tileCount);
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

        // [2026-09-28] 계단 구역 행운칸 -- "고대의유적이나 행운/함정이 다같이 나오는 랜덤칸"
        // 요청. 밟는 순간 셋 중 하나(1/3씩)로 정해지고, 효과 자체는 기존 칸 처리를 그대로 탄다
        // (고대의 유적=SPECIAL(무너진 사원과 동일 효과, 사용자 확인), 럭키=PP, 함정=TRAP).
        if ("RANDOM_LUCKY".equals(effectiveType)) {
            String[] luckyPool = { "SPECIAL", "PP", "TRAP" };
            effectiveType = luckyPool[RND.nextInt(luckyPool.length)];
            String revealed = "SPECIAL".equals(effectiveType) ? "🏛️ 고대의 유적"
                    : "PP".equals(effectiveType) ? "🍀 럭키" : "🕳️ 함정";
            sb.append("...정체는 ").append(revealed).append("!").append(NL);
        }

        switch (effectiveType) {
            case "MIDBOSS": {
                // [2026-09-28] 계단 구역 전용 -- 확률 조우가 아니라 이 칸을 밟으면 항상 중간보스
                // (기존 중간보스와 동일한 3배 배율/스킬 도용 로직을 startCombat에서 그대로 사용).
                sb.append(NL).append(startCombat(userName, p, floor, false, false, true));
                break;
            }
            case "COMBAT": {
                // [2026-09-06] 51층 이후(블록6+) 전투칸은 일정 확률로 "중간보스"와 마주친다 --
                // 등장 메시지/맵 표기는 평범한 몬스터와 완전히 동일해서(startCombat 참고) 실제로
                // 붙어보기 전엔 알 수 없다.
                // [2026-09-28] 계단 구역은 중간보스가 전용 칸(MIDBOSS)으로 따로 있으므로 전투칸에서는
                // 확률 조우를 끈다(안 그러면 한 층에 중간보스가 2마리를 넘어 설계 틀이 깨짐).
                boolean midBossEncounter = !stairZone && blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT;
                // [세 구간 분리 요청] 탐사 현황과 몬스터 등장 사이에 빈 줄
                sb.append(NL).append(startCombat(userName, p, floor, false, false, midBossEncounter));
                break;
            }
            case "PP": {
                // 럭키칸(칸 유형 값은 하위호환을 위해 기존 "PP" 그대로 두고 표시만 "🍀 럭키"로 바꿈,
                // 함정칸처럼 이로운 효과 4종 중 무작위 -- PP 보너스/회복은 즉시 발동, 공격력/방어력
                // 강화는 이후 3번의 보드 이동 동안 지속되는 파티 전체 버프(위 luckyTurnLeft 참고).
                HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), "N");
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
                        wardUp.put("wardRobeYn", "N"); // 럭키칸 가호는 전투가 끝나도 유지
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
                    if (stairZone) {
                        // [2026-09-28] 계단 구역은 순환 보드가 아니라 1->9 한 방향 계단이라, 끝에서
                        // 반대쪽 끝으로 넘어가는(순환) 대신 1~9 범위 안에서 멈추게 하고, 안내 문구의
                        // 칸 수도 실제로 움직인 만큼으로 맞춘다.
                        movedTile = Math.max(1, Math.min(tileCount, newTile + moveDelta));
                        moveDelta = movedTile - newTile;
                    }
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
                    // [2026-09-28] 계단 구역은 앞으로 밀려 중간보스 칸(MIDBOSS)에 떨어져도 그 자리에서
                    // 바로 싸운다(안 그러면 다음 이동에 그 칸을 건너뛰고 지나가버림).
                    boolean landedOnMidBossTile = stairZone && "MIDBOSS".equals(movedTileType);
                    if (moveDelta < 0 || "COMBAT".equals(movedTileType) || landedOnMidBossTile) {
                        boolean movedMidBoss = stairZone ? landedOnMidBossTile
                                : (blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT);
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
                    HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), "N");
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
                int requiredPct = !exploreGateFloor ? 0
                        : (floor >= STAIRS_UP_ENDGAME_FLOOR ? STAIRS_UP_REQUIRE_PCT_ENDGAME
                                : stairsUpRequiredPct(userName, floor, newTile));
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
        // [2026-09-28] 계단 구역 행운칸에서 나오는 "고대의 유적"은 효과는 무너진 사원과 완전히
        // 동일(사용자 확인)하고 이름/문구만 다르게 보여준다(방문 누적/히든 업적도 공유).
        boolean ruins = isStairZone(floor);
        String siteName = ruins ? "고대의 유적" : "무너진 사원";
        StringBuilder sb = new StringBuilder(ruins ? "🏛️ " : "🏚️ ").append(siteName).append("을 발견했다... (누적 방문 ").append(cnt).append("회)");
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
        if (roll < 10) { // [2026-10-08] 조각 5% -> 10% (축복 30% -> 25%)
            int newFragment = intVal(p.get("LEGEND_FRAGMENT"), 0) + 1;
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("legendFragment", newFragment);
            dao.updateUserProgress(up);
            p.put("LEGEND_FRAGMENT", newFragment);
            sb.append(NL).append("🧩 ").append(floor).append("층 ").append(siteName).append("의 폐허 잔해 속에서 전설의조각을 발견했다! (보유 ").append(newFragment).append("개)");
        } else if (roll < 25) {
            HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), "N");
            PP reward = mon == null ? PP.of(10, "")
                    : PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(3 * floorPpMultiplier(floor));
            addPp(userName, p, reward);
            PP curPp = PP.of(((Number) p.get("PP_VALUE")).doubleValue(), strVal(p.get("PP_EXT"), ""));
            sb.append(NL).append("💰 폐허 속에 묻혀있던 ").append(reward.format()).append(" PP를 발견했다! (보유 ").append(curPp.format()).append(" PP)");
        } else if (roll < 75) {
            // 계단 구역은 중간보스가 전용 칸으로 따로 있어서 여기선 확률 조우를 끔(COMBAT 칸과 동일 이유).
            boolean midBossEncounter = !ruins && blockNo(floor) >= 6 && RND.nextInt(100) < MIDBOSS_CHANCE_PCT;
            sb.append(NL).append(ruins ? "😱 유적 안쪽에 숨어있던 몬스터가 튀어나왔다!" : "😱 사원 안쪽에 숨어있던 몬스터가 튀어나왔다!").append(NL)
              .append(startCombat(userName, p, floor, false, false, midBossEncounter));
        } else {
            // 축복: 기존 럭키칸 ATK_UP_30과 동일 컬럼(LUCKY_TURN_LEFT/LUCKY_EFFECT)을 그대로
            // 재사용하되, 지속시간만 "1턴"(HP_DOUBLE_1T와 동일한 1턴 한정 패턴)으로 짧게 둔다.
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("luckyTurnLeft", 1);
            up.put("luckyEffect", "ATK_UP_30");
            dao.updateUserProgress(up);
            sb.append(NL).append("✨ ").append(siteName).append("의 축복을 받았다! 다음 1번의 이동/전투 동안 파티 전원의 공격력이 30% 강화됩니다.");
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
        HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), "N");
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
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), boss ? "Y" : "N"), floor);
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
        // [2026-09-28] 101층+ 계단 구역은 전투칸 1개 = 몬스터 1마리(09-20 로드맵 "계단 한 칸 =
        // 몬스터 한 마리")라 61층+ "두 마리" 규칙을 적용하지 않는다.
        boolean dualMonster = (!boss && !elite && !midBoss && blockNo(floor) >= 7 && !isStairZone(floor)) || dualBossFloor;
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
        up.put("curAmbushDeadCids", ""); // [2026-10-08] 은신 기습 사망자 명단 초기화
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
        // [2026-09-29] ★7 전설 로브 START_BARRIER -- 착용자가 파티에 있으면 전투 시작과 함께
        // "다음 피해 1회 면역"(WARD_COMPANION_ID, 럭키칸 가호와 같은 컬럼/소모 규칙)을 두르고
        // 시작한다. 이미 다른 가호가 걸려있으면(컬럼 하나뿐) 덮어쓰지 않는다.
        String barrierNote = null;
        if (intVal(p.get("WARD_COMPANION_ID"), 0) <= 0) {
            for (HashMap<String, Object> bc : dao.selectUserCompanions(userName)) {
                if (bc.get("PARTY_SLOT") == null) continue;
                if (PP.toBaseValue(PP.of(((Number) bc.get("CUR_HP_VALUE")).doubleValue(), strVal(bc.get("CUR_HP_EXT"), ""))) <= 0) continue;
                String barrierItem = null;
                for (HashMap<String, Object> be : dao.selectEquipByCompanion(intVal(bc.get("COMPANION_ID"), 0))) {
                    if ("START_BARRIER".equals(strVal(be.get("LEGENDARY_EFFECT_TYPE"), ""))) { barrierItem = strVal(be.get("LEGENDARY_ITEM_NAME"), "전설 로브"); break; }
                }
                if (barrierItem == null) continue;
                if (!legendRoll(100, intVal(bc.get("GRADE"), 1) == GROWTH_GRADE)) continue; // [2026-10-23] 발동 확률 50%(성장형 25%)
                int bcid = intVal(bc.get("COMPANION_ID"), 0);
                HashMap<String, Object> wardUp = new HashMap<>();
                wardUp.put("userName", userName);
                wardUp.put("wardCompanionId", bcid);
                wardUp.put("wardRobeYn", "Y"); // [2026-10-08] 로브 보호막 -- 전투가 끝나면 쓰지 않았어도 사라진다
                dao.updateUserProgress(wardUp);
                p.put("WARD_COMPANION_ID", bcid);
                String bcJob = strVal(bc.get("CLASS"), "WARRIOR");
                barrierNote = "🛡️✨ " + jobTag(intVal(bc.get("GRADE"), 1), bcJob, strVal(bc.get("NAME"), JOB_NAME.getOrDefault(bcJob, "동료")))
                        + "의 " + barrierItem + " 발동! 무적 보호막(다음 피해 1회 면역)을 두르고 시작합니다.";
                break;
            }
        }
        // [형식 정리 요청] "OO 등장!"을 한 줄에 다 몰아넣지 않고 "등장!" 알림 / 몬스터 이름 /
        // 능력치를 각각 줄로 나눔("능력치" 라벨·콜론도 빼서 더 짧게). [2026-09-06] 중간보스는
        // "맵에는 일반적인 몬스터로 표시되는데" 요청대로 평범한 등장 메시지("👾 등장!")를 그대로
        // 쓰고 강화몹처럼 정체를 미리 알려주는 문구도 없다 -- 실제 스탯(3배)은 아래에 그대로
        // 노출되지만, 정체는 전투 중 스킬 훔치기가 나와야 드러난다.
        sb.append(dualBossFloor ? "👹👹 보스 두 체 등장!" : boss ? "👹 보스 등장!" : elite ? "💪 강화 등장!" : dualMonster ? "👾👾 몬스터 두 마리 등장!" : "👾 등장!").append(NL);
        sb.append(floorMonsterName(floor, mon)).append(NL);
        // [2026-10-01] "몬스터 처음 소개될 때 투자보정된 값으로" 요청 -- 실제 반격은 맞는 동료의 투자배율(investAtkScale)이 곱해지는데 소개에는
        // 기본 공격력만 나와 주사위 1에도 그 몇 배가 들어가 보였다. 편성된 동료 중 가장 큰 투자배율(최악 기준)을 곱한 값을 보여주고 배율을 표기한다.
        HashMap<String, Object> introStat = dao.selectUserStat(userName);
        double introScale = 1.0;
        for (HashMap<String, Object> ic : dao.selectUserCompanions(userName)) {
            if (ic.get("PARTY_SLOT") == null) continue;
            introScale = Math.max(introScale, investAtkScaleFor(introStat, ic));
        }
        int introAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * (boss ? 1.6 : 1.0) * introScale);
        sb.append("⚔️ ").append(introAtk)
          .append(" 🛡️ ").append((int) Math.round(intVal(mon.get("DEF_VALUE"), 0) * eliteMult))
          .append(" ❤️ ").append(plainNum(fullHp)).append(dualMonster ? " x2" : "").append(NL);
        if (introScale > 1.005) sb.append("📈 공격력에 투자보정 x").append(String.format("%.2f", introScale)).append(" 반영 (파티 최대 기준)").append(NL);
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
        if (barrierNote != null) sb.append(barrierNote).append(NL);
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
        attachSkillMult(userName, party); // [2026-10-08] 학자의 토시 착용 동료는 스킬 발동 확률 배율

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
                int[] amEff = slotPenalty(computeEffectiveStat(strVal(amTarget.get("CLASS"), "WARRIOR"), intVal(amTarget.get("GRADE"), 1),
                        amEquips, userStat, intVal(amTarget.get("LIMIT_BREAK"), 0)), amTarget, userStat);
                int amMonsterAtk = (int) Math.round(intVal(mon.get("ATK_VALUE"), 0) * eliteMult * trapAmbushDmgMult * AMBUSH_ATK_PCT);
                // [2026-09-22] "60~70층 유저도 선공(기습)당할때 데미지가 너무 적어" 신고 --
                // 반격과 동일하게 대상 본인의 투자배율만큼 기습 공격력도 같이 올린다.
                amMonsterAtk = (int) Math.round(amMonsterAtk * investAtkScaleFor(userStat, amTarget));
                // [2026-09-19] "51층+ 몬스터는 자기만의 무작위 면수를 쓴다"는 규칙(반격과 동일,
                // monsterOwnDiceMax 참고)을 기습 굴림에도 맞춤 -- 예전엔 플레이어가 낀 주사위
                // (diceMax, 최대 20)를 그대로 재사용해서 플레이어가 강한 주사위를 낄수록
                // 몬스터 기습도 덩달아 세지는 부작용이 있었다.
                int amFaceMax = monsterOwnDiceMax(floor, diceMax);
                int amRoll = rollFace(monsterDiceMin(floor, amFaceMax), amFaceMax);
                // [2026-09-22] 반격과 동일하게 "적어도 원본 공격력의 절반은 방어를 뚫는다"는
                // 최소 보장선 적용(monsterHitDamage 참고). MAX_AMBUSH_DMG/사망방지 클램프는
                // 아래에서 그대로 이어서 적용됨.
                int amDmg = monsterHitDamage(amMonsterAtk, amRoll, amEff[2]);
                // [2026-09-30 버그 수정] "럭키칸 체력 두 배(받는 피해 절반) 이후 다음 전투에서 피해 감소가 안 먹힌다" 신고 --
                // 절반 처리가 반격 피해(아래 dmgToParty)에만 있고 선공 기습에는 빠져 있어서, 축복 중에도 기습이 100%로 꽂혔다.
                int amDmgBeforeHalf = amDmg;
                if (luckyHpDouble) amDmg = Math.max(1, (int) Math.round(amDmg * 0.5));
                // [2026-09-19] "★6 체력1만인데 기습이 4만" 신고 -- 재요청("즉사할 수도 있게는
                // 해야지, 다만 100% 확정 즉사는 과함")에 맞춰 즉사 자체는 막지 않되, 실드까지
                // 감안한 체감 최대치를 MAX_AMBUSH_DMG(2만)로 못박는다.
                // [2026-09-30] 최대데미지 상한(2만) 제거 -- 몬스터 기본 공격력*굴림 그대로(방어/최소관통선만 적용).
                sb.append("🌑 은신 기습! ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 먼저 공격한다!").append(NL);
                if (luckyHpDouble) {
                    sb.append("🍀 체력 두 배 효과: 기습 피해 절반 (").append(amDmgBeforeHalf).append(" → ").append(amDmg).append(")").append(NL);
                }
                // 대상 최대체력의 90% 이상이면(사실상 빈사권) 한 명에게 몰아치지 않고 가능하면
                // 두 명에게 절반씩 나눠 때린다(다중공격 연출). [2026-09-20] 실제 즉사 방지는
                // applyAmbushHit()의 최종 하드 클램프(AMBUSH_DEATH_GUARD_PCT)가 담당하므로,
                // 여기서 분산 여부와 무관하게 기습만으로는 죽지 않는다.
                // [2026-09-30] 100층 초과(계단 구역)는 즉사 허용 -- 분산(절반씩)도 하지 않고 한 명에게 전부 꽂힘.
                boolean lethalAmbush = floor > 100;
                if (!lethalAmbush && amDmg >= Math.round(amEff[0] * AMBUSH_SPLIT_THRESHOLD_PCT) && ambushAlive.size() > 1) {
                    List<HashMap<String, Object>> remaining = new ArrayList<>(ambushAlive);
                    remaining.remove(amTarget);
                    HashMap<String, Object> amTarget2 = remaining.get(RND.nextInt(remaining.size()));
                    int half = Math.max(1, amDmg / 2);
                    sb.append("💥 위력이 너무 강해 두 곳으로 갈라져 꽂혔다!").append(NL);
                    applyAmbushHit(userName, p, userStat, sb, amTarget, half, false);
                    applyAmbushHit(userName, p, userStat, sb, amTarget2, half, false);
                } else {
                    applyAmbushHit(userName, p, userStat, sb, amTarget, amDmg, lethalAmbush);
                }
                sb.append(NL);
            }
        }

        // [2026-10-08] "은신 기습 한 턴 말고 다음턴부터 부활 가능하도록" 요청 -- 기습(1턴째)으로 쓰러진 동료는 2턴째부터 매 턴, 파티에 살아있는
        // ★5+ 도사가 반격 사망 때와 같은 확률(★5 10%/HP30%, ★6 15%/HP50%)로 한 명씩 부활을 시도한다. 부활하면 이번 턴 파티 공격에 바로 참여한다.
        // 도사가 없거나 쓰러져 있으면 명단을 유지(도사가 부활하면 이어서 시도), 파티에서 빠졌거나 이미 살아난 동료는 명단에서 지운다.
        if (curCombatTurn >= 2) {
            List<Integer> ambushDeadList = parseMinionCids(strVal(p.get("CUR_AMBUSH_DEAD_CIDS"), ""));
            if (!ambushDeadList.isEmpty()) {
                HashMap<String, Object> ambushReviver = null;
                for (HashMap<String, Object> c : party) {
                    if (!"PRIEST".equals(strVal(c.get("CLASS"), "")) || intVal(c.get("GRADE"), 1) < 5) continue;
                    if (PP.toBaseValue(PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""))) <= 0) continue;
                    ambushReviver = c;
                    break;
                }
                if (ambushReviver != null) {
                    int arGrade = intVal(ambushReviver.get("GRADE"), 1);
                    int arChance = arGrade >= 6 ? 15 : 10;
                    double arPct = arGrade >= 6 ? 0.5 : 0.3;
                    List<Integer> stillDead = new ArrayList<>();
                    for (int deadCid : ambushDeadList) {
                        HashMap<String, Object> vc = null;
                        for (HashMap<String, Object> c : party) {
                            if (intVal(c.get("COMPANION_ID"), 0) == deadCid) { vc = c; break; }
                        }
                        if (vc == null) continue; // 파티에서 빠짐
                        if (PP.toBaseValue(PP.of(((Number) vc.get("CUR_HP_VALUE")).doubleValue(), strVal(vc.get("CUR_HP_EXT"), ""))) > 0) continue; // 이미 살아남
                        if (RND.nextInt(100) < skillRate(ambushReviver, arChance)) {
                            String vJob = strVal(vc.get("CLASS"), "WARRIOR");
                            int vGrade = intVal(vc.get("GRADE"), 1);
                            int[] vEff = slotPenalty(computeEffectiveStat(vJob, vGrade, dao.selectEquipByCompanion(deadCid), userStat, intVal(vc.get("LIMIT_BREAK"), 0)), vc, userStat);
                            PP vHp = PP.fromPP(Math.max(1, (int) Math.round(vEff[0] * arPct)));
                            writeCompanionHp(vc, vHp);
                            sb.append("✨ 도사의 기적! ").append(jobTag(vGrade, vJob, strVal(vc.get("NAME"), JOB_NAME.getOrDefault(vJob, "동료"))))
                              .append(" 부활 (HP ").append(plainNum(vHp)).append("/").append(vEff[0]).append(") -- 기습에 쓰러졌던 동료").append(NL);
                        } else {
                            stillDead.add(deadCid);
                        }
                    }
                    String stillJoined = joinMinionCids(stillDead);
                    if (!stillJoined.equals(joinMinionCids(ambushDeadList))) {
                        HashMap<String, Object> stUp = new HashMap<>();
                        stUp.put("userName", userName);
                        stUp.put("curAmbushDeadCids", stillJoined);
                        dao.updateUserProgress(stUp);
                        p.put("CUR_AMBUSH_DEAD_CIDS", stillJoined);
                    }
                }
            }
        }

        // 보스 기절 스킬(반격 턴에 걸림, 아래 참고)로 지정된 동료는 이번 공격 턴만 건너뛰고 소모된다.
        int bossStunCid = intVal(p.get("BOSS_STUN_CID"), 0);
        boolean stunConsumed = false;

        // ── 파티 선공: 생존한 동료 전원이 각자 1회씩 공격 (직업별 특수효과 포함) ──
        long totalDamage = 0;
        // [2026-09-21] "방어로 인한 차감분을 확실히 표시해달라" 요청 -- 개별 줄은 방어력
        // 적용 "전" 원본 데미지(ATK*굴림)를 보여주고, 실제 방어 차감은 합산 데미지 한 줄에서
        // "총딜 - 차감 = 실제딜" 형태로 한 번에 보여준다(파티원별로 방어 무시/크리티컬/
        // 사제 감소 등 보정이 제각각이라 "원본합-실제합"의 차이를 그대로 차감량으로 써서
        // 항상 산수가 맞게 함 -- totalDamage 참고).
        long totalRawDamage = 0;
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

        // [2026-09-29] "몬스터가 회피스킬을 뺏을 때 동료 한 명한테만 회피하도록" 요청 -- 지난 턴에
        // 도적 스킬(회피)을 훔쳤으면(MONSTER_EVADE_PCT), 그 확률로 이번 턴 "공격하는 동료 중 무작위 한
        // 명"의 공격만 회피한다(예전엔 파티 공격 전체를 무효화). 대상은 이 루프에서 실제로 공격할 동료
        // (생존+기절 아님) 중에서 뽑는다.
        int monsterEvadeCid = -1;
        int monsterEvadePct = intVal(p.get("MONSTER_EVADE_PCT"), 0);
        if (monsterEvadePct > 0 && RND.nextInt(100) < monsterEvadePct) {
            List<Integer> evadeCandidates = new ArrayList<>();
            for (HashMap<String, Object> c : party) {
                PP chp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
                if (PP.toBaseValue(chp) <= 0) continue;
                int cid = intVal(c.get("COMPANION_ID"), -1);
                if (bossStunCid != 0 && bossStunCid == cid) continue;
                evadeCandidates.add(cid);
            }
            if (!evadeCandidates.isEmpty()) monsterEvadeCid = evadeCandidates.get(RND.nextInt(evadeCandidates.size()));
        }

        // [2026-10-20] 무녀 스킬 두 개를 파티 공격 전에 각각 굴린다 -- 액막이(몬스터 공격력 -N: 반격 파트에서 적용), 살풀이(아군 전원 공격력 +N: 아래 공격 루프에서 적용).
        // 둘 다 고정 수치이고 몬스터 공격력 기준값의 50%까지만 움직인다.
        LinkedHashMap<Integer, int[]> shamanPlays = skillLocked ? new LinkedHashMap<Integer, int[]>()
                : rollShamanPlays(party, userStat, synergy,
                        intVal(mon.get("ATK_VALUE"), 0) * eliteMult * ("Y".equals(strVal(mon.get("BOSS_YN"), "N")) ? 1.6 : 1.0), bossStunCid);
        int shamanDrain = 0, shamanBuff = 0;
        for (int[] v : shamanPlays.values()) { shamanDrain += v[0]; shamanBuff += v[1]; }

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
            int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0)), c, userStat);
            // [2026-10-20] 전설 머리띠 "수선도사의 머리띠"(DICE_MIN_PLUS): 전투 중 이 동료의 공격/보호막 주사위 하한 +N. 전설 깃장식 "주작의 깃장식"(CRIT_DAMAGE): 크리티컬 배율 교체.
            boolean growthHalf = grade == GROWTH_GRADE; // [2026-10-22] 성장형은 장비 특수효과 50%
            HashMap<String, Object> diceMinEq = legendEquipEffect(equips, "DICE_MIN_PLUS");
            int diceMinPlus = diceMinEq == null ? 0 : intVal(diceMinEq.get("LEGENDARY_EFFECT_PARAM1"), 0);
            if (diceMinPlus != 0) diceMinPlus = scaleInt(diceMinPlus, legendScale(growthHalf)); // [2026-10-23] 효과 50%(성장형 25%)
            int myDiceMin = diceMinFor(p) + diceMinPlus;
            HashMap<String, Object> critEq = legendEquipEffect(equips, "CRIT_DAMAGE");
            double critMult = critEq == null ? 1.5 : intVal(critEq.get("LEGENDARY_EFFECT_PARAM1"), 150) / 100.0;
            if (critEq != null) critMult = 1.5 + (critMult - 1.5) * legendScale(growthHalf); // [2026-10-23] 증가분 50%(성장형 25%)
            if ("ARCHER".equals(synergy)) eff[1] = (int) Math.round(eff[1] * 1.3); // 시너지: 궁수3인조 공격력+30%
            // 균형3인조(RAINBOW)의 방어 +10%는 이 배열이 아니라 반격 파트에서 대상(tEff[2])에
            // 직접 적용한다(여기 eff[2]는 "내가 공격할 때" 값이라 방어 보너스는 안 쓰임).
            if ("RAINBOW".equals(synergy)) eff[1] = (int) Math.round(eff[1] * 1.1); // 시너지: 균형3인조 공격 +10%
            if (trapAtkDown) eff[1] = (int) Math.round(eff[1] * 0.7); // 함정: 공격력 30% 약화
            if (luckyAtkUp) eff[1] = (int) Math.round(eff[1] * luckyMult); // 럭키: 공격력 강화
            if (shamanBuff > 0) eff[1] += shamanBuff; // 무녀 살풀이: 아군 공격력 +N (고정 수치)
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
                int critChance = skillRate(c, grade >= 6 ? 50 : (grade >= 5 ? 40 : 30));
                archerCrit = RND.nextInt(100) < critChance;
            }

            int roll;
            String rollLabel;
            if (doubleDice) {
                int atkRoll1 = rollFace(myDiceMin, diceMax);
                int atkRoll2 = rollFace(myDiceMin, diceMax);
                roll = atkRoll1 + atkRoll2;
                rollLabel = atkRoll1 + "+" + atkRoll2;
            } else {
                roll = rollFace(myDiceMin, diceMax);
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
            // [2026-09-21] 표기 개편용 -- 방어력 적용 "전" 원본 데미지(주사위 눈만 반영,
            // ATK*굴림). 방어 무시/가산인 DEF_STEAL도 "방어가 없었다면 얼마였을지" 기준을
            // 그대로 맞추기 위해 항상 이 식으로 계산해둔다(실제 dmg 계산식과는 별개).
            int rawDmg = eff[1] * roll;
            // [2026-09-21 재설계] "송곳: 방어력을 무시하고, 방어력만큼 내데미지에 더한다" --
            // 처음엔 "훔친 만큼만 가산"(부분 관통)이었는데, 사용자가 "무시 + 그만큼 가산"으로
            // 명확히 정정 -- effMonsterDef를 아예 빼지 않고(무시) PARAM1%만큼 그대로 더한다.
            // PARAM1=100이면 dmg = ATK*roll + DEF(방어력이 페널티가 아니라 순수 보너스가 됨).
            if (legWeapon != null && "DEF_STEAL".equals(strVal(legWeapon.get("EFFECT_TYPE"), ""))) {
                int bonus = (int) Math.round(effMonsterDef * (intVal(legWeapon.get("EFFECT_PARAM1"), 0) / 100.0));
                dmg = Math.max(1, eff[1] * roll) + bonus;
                // [2026-10-23] 효과 50%(성장형 25%) -- 효과 없을 때(방어 차감)와 온전한 효과 사이를 배율만큼
                int noEffectDmg = Math.max(1, eff[1] * roll - effMonsterDef);
                dmg = (int) Math.round(noEffectDmg + (dmg - noEffectDmg) * legendScale(growthHalf));
                legendaryWeaponTag = strVal(legWeapon.get("ITEM_NAME"), "") + "+" + Math.round(bonus * legendScale(growthHalf));
            } else {
                dmg = Math.max(1, eff[1] * roll - effMonsterDef);
            }
            dmg = Math.max(dmg, eff[3]); // 스탯구매 최소공격력 보정
            if (archerCrit) dmg = (int) Math.round(dmg * critMult); // 궁수 크리티컬: 최종 데미지 1.5배(주작의 깃장식이면 그 배율)
            // [2026-09-29] ★7 전설 활 "바펠세파르 활"(DOUBLE_SHOT) -- EFFECT_PARAM1(%) 확률로 같은
            // 주사위 눈금으로 한 번 더 쏜다. 추가타도 방어/최소공격력/크리티컬은 독립적으로 다시
            // 계산해서 원 공격과 동일한 규칙을 따른다(크리 확률도 따로 굴림).
            int extraDmg = 0, extraRaw = 0;
            if (legWeapon != null && "DOUBLE_SHOT".equals(strVal(legWeapon.get("EFFECT_TYPE"), ""))
                    && legendRoll(intVal(legWeapon.get("EFFECT_PARAM1"), 0), growthHalf)) {
                extraRaw = eff[1] * roll;
                extraDmg = Math.max(Math.max(1, extraRaw - effMonsterDef), eff[3]);
                if ("ARCHER".equals(job) && RND.nextInt(100) < skillRate(c, grade >= 6 ? 50 : (grade >= 5 ? 40 : 30))) {
                    extraDmg = (int) Math.round(extraDmg * critMult);
                }
                dmg += extraDmg;
            }
            // [2026-09-17] "도사는 서포터로 만들자, 현행 데미지의 6분의1수준으로 낮춰서 딜은
            // 그대로 들어가도록(완전히 0은 아님), 실드는 변경없음" 요청 -- 실드는 이 dmg와
            // 완전히 별개의 두 번째 주사위 굴림(shieldRoll, 아래 PRIEST switch case)으로
            // 계산되므로 이 줄과 무관하게 그대로 유지된다.
            if ("PRIEST".equals(job) || "SHAMAN".equals(job)) dmg = Math.max(1, (int) Math.round(dmg / 6.0));
            // [2026-10-20] 전설 지팡이 "수다쟁이 웨리"(CHATTER): PARAM1% 확률로 몬스터 스턴, PARAM2% 확률로 이 공격 데미지 두 배(두 확률은 각각 따로 굴림, 특수효과라 강화/스킬 배율과 무관).
            boolean chatterDouble = false, chatterStun = false;
            int chatterRawBonus = 0;
            if (legWeapon != null && "CHATTER".equals(strVal(legWeapon.get("EFFECT_TYPE"), ""))) {
                if (legendRoll(intVal(legWeapon.get("EFFECT_PARAM2"), 0), growthHalf)) { chatterDouble = true; dmg *= 2; chatterRawBonus = rawDmg + extraRaw; }
                if (legendRoll(intVal(legWeapon.get("EFFECT_PARAM1"), 0), growthHalf)) { chatterStun = true; stunned = true; }
            }
            boolean evadedHit = intVal(c.get("COMPANION_ID"), -1) == monsterEvadeCid;
            if (evadedHit) dmg = 0; // 몬스터가 훔친 회피 -- 이 동료의 공격만 무효(총딜/방어차감 산수에서도 제외)
            totalDamage += dmg;
            totalRawDamage += evadedHit ? 0 : rawDmg + extraRaw + chatterRawBonus;
            // [간결화] 텍스트가 너무 길다는 요청으로, 공격력/범위(전투 시작 전 "OO 등장!" 메시지에
            // 이미 표시됨)는 매 줄마다 반복하지 않고, 직업별 특수효과도 새 줄 대신 같은 줄 끝에
            // 붙여서 파티원 1명당 항상 딱 1줄만 쓰도록 함.
            // [세 구간 분리 요청] 공격 줄에도 현재/최대 HP를 같이 보여줘서, 나중에 "이번턴 남은"
            // 구간의 HP와 바로 비교되게 함.
            // [2026-09-05 멘트 개편] "이름+HP"와 "주사위/데미지"를 한 줄에 몰아넣지 말고 줄을
            // 나눠달라는 요청 -- 이름+HP 줄, 그 아래 굴림 결과 줄로 분리.
            // [2026-09-21] "4굴려서 나온 데미지 표기"를 원해서 이 줄은 방어 적용 전 원본
            // (rawDmg)을 보여주고, 방어 차감은 아래 "총 N dmg로 공격!" 다음 줄에서 한 번에.
            sb.append(jobTag(grade, job, cName)).append(" 💗").append(plainNum(hp)).append("/").append(eff[0]).append(NL)
              .append("🎲").append(rollLabel).append("→").append(rawDmg).append("dmg");
            if (archerCrit) sb.append(" 💥크리티컬!").append(critMult != 1.5 ? "(x" + critMult + ")" : "");
            if (legendaryWeaponTag != null) sb.append(" 🗡️").append(legendaryWeaponTag).append("(방어력 무시+가산)");
            if (extraRaw > 0) sb.append(" 🏹").append(strVal(legWeapon.get("ITEM_NAME"), "")).append(" 연사! +").append(extraRaw).append("dmg");
            if (chatterDouble) sb.append(" 🗣️").append(strVal(legWeapon.get("ITEM_NAME"), "")).append(": 데미지 두 배!");
            if (chatterStun) sb.append(" 💫").append(strVal(legWeapon.get("ITEM_NAME"), "")).append(": 몬스터 스턴!");
            if (evadedHit) sb.append(" 🌀").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 몸놀림으로 이 공격을 회피!(0dmg)");

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
                    if (RND.nextInt(100) < skillRate(c, stunChance)) {
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
                    if (RND.nextInt(100) < skillRate(c, stealChance)) {
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
                    int shieldRoll = doubleDice ? rollFace(myDiceMin, diceMax) + rollFace(myDiceMin, diceMax) : rollFace(myDiceMin, diceMax);
                    int shieldAmt = Math.max(0, eff[1] * shieldRoll);
                    if ("PRIEST".equals(synergy)) shieldAmt = (int) Math.round(shieldAmt * 2.0); // 시너지: 도사3인조 2배
                    shieldPool += shieldAmt;
                    // ★5/★6 특수효과(부활)는 반격으로 동료가 사망할 때 발동하므로 아래 반격
                    // 파트에서 처리한다.
                    break;
                }
                case "SHAMAN": {
                    int[] played = shamanPlays.get(intVal(c.get("COMPANION_ID"), 0));
                    if (played != null) {
                        if (played[0] > 0) sb.append(" 🔮").append(SHAMAN_SKILL_WARD).append("! 몬스터 공격력 -").append(played[0]);
                        if (played[1] > 0) sb.append(" 🔮").append(SHAMAN_SKILL_RELEASE).append("! 아군 공격력 +").append(played[1]);
                    }
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
        // [2026-09-21] "방어로인한 데미지차감분을 확실히 표시해줘" 요청 -- 위 개별 줄들이
        // 이제 방어 적용 전(raw) 값이라, 여기 합계도 raw 합계로 보여주고 바로 다음 줄에
        // "원본 - 방어차감 = 실제딜" 산수를 명시한다. 차감량은 파티원별 방어무시(궁수)/
        // 크리티컬/사제감소/전설무기 가산이 제각각이라 "raw합 - 실제합"으로 역산해서 늘
        // 산수가 맞게 한다(개별 몬스터DEF를 그대로 인원수만큼 곱하면 이 보정들과 안 맞음).
        // 차감량이 음수면(전설무기 방어무시+가산 등으로 실제딜이 raw보다 큼) "가산"으로 표기.
        if (totalRawDamage > 0) {
            sb.append("총 ").append(totalRawDamage).append("dmg로 공격!").append(NL);
            long mitigated = totalRawDamage - totalDamage;
            if (mitigated > 0) {
                sb.append(totalRawDamage).append("dmg - 🛡️방어 ").append(mitigated).append("dmg = ")
                  .append(totalDamage).append("dmg").append(NL);
            } else if (mitigated < 0) {
                sb.append(totalRawDamage).append("dmg + 방어무시 가산 ").append(-mitigated).append("dmg = ")
                  .append(totalDamage).append("dmg").append(NL);
            }
        }

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
        // [2026-09-29] 회피 판정은 위 파티 공격 루프에서 동료 한 명 단위로 처리(monsterEvadeCid).

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
            logBattleResult(userName, p, floor, "WIN", curCombatTurn); // [2026-09-30] 밸런스 통계
            // [2026-09-09] "두 마리"라 실제로 2마리분 처치 보상을 준다(dualHpMult가 그대로 배율).
            PP reward = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), "")).multiply(floorPpMultiplier(floor) * eliteMult * dualHpMult);
            // [2026-10-20] 전설 목걸이 "군트람"(KILL_PP_BONUS): 파티에 착용한 동료가 있으면 처치 PP +PARAM1%(여러 개여도 한 번만, 가장 큰 값).
            double killPpPct = 0;
            String killPpItem = null;
            for (HashMap<String, Object> pc : party) {
                HashMap<String, Object> kEq = legendEquipEffect(dao.selectEquipByCompanion(intVal(pc.get("COMPANION_ID"), 0)), "KILL_PP_BONUS");
                double kPct = kEq == null ? 0 : intVal(kEq.get("LEGENDARY_EFFECT_PARAM1"), 0) * legendScale(intVal(pc.get("GRADE"), 1) == GROWTH_GRADE); // [2026-10-23] 효과 50%(성장형 25%)
                if (kEq != null && kPct > killPpPct) {
                    killPpPct = kPct;
                    killPpItem = strVal(kEq.get("LEGENDARY_ITEM_NAME"), "목걸이");
                }
            }
            if (killPpPct > 0) reward = reward.multiply(1 + killPpPct / 100.0);
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

            // [2026-09-29] "80층 이상에서는 일반몬스터는 0.5%, 중간보스는 1%로 전설의조각을 얻게해줘" 요청
            // -- 보스층 드랍(59/69/79/89/99)과 별개로, 보스가 아닌 처치(일반/엘리트/중간보스)에서도
            // 80층 이상이면 천분율 확률로 조각 1개.
            if (!isBoss && floor >= LEGEND_FRAGMENT_MOB_MIN_FLOOR
                    && RND.nextInt(1000) < (midBoss ? LEGEND_FRAGMENT_MIDBOSS_DROP_PERMILLE : LEGEND_FRAGMENT_MOB_DROP_PERMILLE)) {
                int newFragment = intVal(p.get("LEGEND_FRAGMENT"), 0) + 1;
                HashMap<String, Object> fragUp = new HashMap<>();
                fragUp.put("userName", userName);
                fragUp.put("legendFragment", newFragment);
                dao.updateUserProgress(fragUp);
                p.put("LEGEND_FRAGMENT", newFragment);
                sb.append("🧩 ").append(floor).append("층 ").append(midBoss ? "중간보스가" : "몬스터가").append(" 전설의조각을 떨어뜨렸다! (보유 ")
                  .append(newFragment).append("개)").append(NL);
            }

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
                        sb.append("🧩 ").append(floor).append("층 보스가 전설의조각을 떨어뜨렸다! (보유 ").append(newFragment).append("개)").append(NL);
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
                sb.append(NL).append(reward.format()).append(" PP 획득!").append(killPpItem != null ? " ✨[" + killPpItem + "] +" + pct1(killPpPct) + "%" : "").append(" (보유 ").append(curPp.format()).append(" PP)");
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
        // [2026-09-21] "남은hp와 차감hp를 잘보이도록" 요청 -- 이번 턴 실제로 깎인 양을
        // monsterHp(턴 시작 시점 스냅샷)와의 차이로 역산해서 같이 보여준다(dual몬스터/보호막
        // 흡수 등 경로가 복잡해도 "시작-끝" 차이라 항상 정확함).
        PP monsterDealt = monsterHp.subtract(monsterHpAfter);
        sb.append(eliteMonsterName(floor, mon, elite)).append(" 💛")
          .append(plainNum(monsterHpAfter)).append("/").append(plainNum(monsterMaxHp));
        if (PP.toBaseValue(monsterDealt) > 0) sb.append(" (-").append(plainNum(monsterDealt)).append(")");
        sb.append(NL);

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
            logBattleResult(userName, p, floor, "WIPE", curCombatTurn); // [2026-09-30] 밸런스 통계
            int wipeStreak = intVal(p.get("WIPE_STREAK_CUR"), 0) + 1;
            HashMap<String, Object> defeatUp = new HashMap<>();
            defeatUp.put("userName", userName);
            defeatUp.put("status", "NORMAL");
            defeatUp.put("clearMonster", true);
            defeatUp.put("wipeStreakCur", wipeStreak);
            dao.updateUserProgress(defeatUp);
            // [버그 수정, 2026-09-29] 계단 구역(101층+)에서 전멸(중간보스 등)한 뒤 동료만 바꿔 다시
            // /주사위를 굴리면, 위치가 여전히 그 칸(예: 9번 칸)이라 "이 칸은 끝냈다"로 취급돼 그대로
            // 다음 칸/다음 층으로 넘어가 버렸다(보스를 못 이겼는데 102층으로 진행 -- 신고 확인).
            // 전멸했으면 위치를 한 칸 되돌려서 다음 굴림이 같은 칸의 전투로 재진입하게 한다.
            if (isStairZone(floor)) {
                HashMap<String, Object> wipeUfp = dao.selectUserFloorProgress(userName, floor);
                int wipeTile = wipeUfp == null ? 1 : intVal(wipeUfp.get("CUR_TILE"), 1);
                if (wipeTile > 1) {
                    HashMap<String, Object> backUp = new HashMap<>();
                    backUp.put("userName", userName);
                    backUp.put("floor", floor);
                    backUp.put("curTile", wipeTile - 1);
                    dao.upsertUserFloorProgress(backUp);
                }
            }
            // [변경] 예전엔 여기서 바로 풀피로 되돌렸는데, 그러면 안내 문구("마을에서 회복")가
            // 거짓말이 됨. 이제 정말로 마을에 돌아가야(changeFloor) 부활한다.
            sb.append("💀 파티 전멸... 전투에 패배했습니다. 동료들이 전투불가 상태로 남습니다 -- 마을로 돌아가야 부활합니다.");
            // "1~4층에서 여러 번 죽으면 /탑내려가기 안내도 해달라" 요청 -- 이 구간(블록 앞
            // 절반, 초반 사냥터)에서 연속으로 막히고 있으면 쉬운 아래 구간에서 파밍하고
            // 오라고 힌트를 준다. 2연속부터("여러 번") 매번 다시 보여준다.
            int fm = floor % 10;
            if (isStairZone(floor)) {
                // [2026-09-28] 계단 구역엔 층마다 마을이 없다 -- 부활은 100층 마을에서만.
                sb.append(NL).append(NL).append("🪜 /계단내려가기(또는 /층변경 0) 으로 ").append(stairCampOf(floor))
                  .append("층 마을로 돌아가 부활한 뒤, /층변경 ").append(floor).append(" 으로 이 층부터 다시 도전할 수 있어요.")
                  .append(NL).append("(동료만 바꿔 다시 /주사위를 굴리면 방금 진 칸에서 재도전합니다 -- 다음 칸/층으로 넘어가지 않아요)");
            } else if (fm >= 1 && fm <= 4 && wipeStreak >= 2 && floor >= 10) {
                sb.append(NL).append(NL)
                  .append("💡 이 구간에서 ").append(wipeStreak).append("연속으로 전멸했어요. 아직 버거우면 ")
                  .append("/계단내려가기로 10층 아래 마을로 내려가서 스탯/장비를 더 준비한 뒤 다시 도전해보세요.");
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

            boolean vWarded = consumeWardIfTarget(userName, p, victim, sb, "은신 급습");
            PP vHpAfter = vWarded
                    ? PP.of(((Number) victim.get("CUR_HP_VALUE")).doubleValue(), strVal(victim.get("CUR_HP_EXT"), ""))
                    : PP.fromPP(0);
            HashMap<String, Object> reviver79 = null;
            for (HashMap<String, Object> c : party) {
                if (vWarded) break; // 막았으면 부활 판정 불필요
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
                if (RND.nextInt(100) < skillRate(reviver79, reviveChance)) {
                    List<HashMap<String, Object>> vEquips = dao.selectEquipByCompanion(intVal(victim.get("COMPANION_ID"), 0));
                    int[] vEff = slotPenalty(computeEffectiveStat(vJob, vGrade, vEquips, userStat, intVal(victim.get("LIMIT_BREAK"), 0)), victim, userStat);
                    vHpAfter = PP.fromPP(Math.max(1, (int) Math.round(vEff[0] * revivePct)));
                    sb.append("✨ 도사의 기적! ").append(jobTag(vGrade, vJob, vName))
                      .append(" 부활(HP ").append(plainNum(vHpAfter)).append("/").append(vEff[0]).append(")").append(NL);
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

                    boolean ivWarded = consumeWardIfTarget(userName, p, instaVictim, sb, "은신 급습");
                    PP ivHpAfter = ivWarded
                            ? PP.of(((Number) instaVictim.get("CUR_HP_VALUE")).doubleValue(), strVal(instaVictim.get("CUR_HP_EXT"), ""))
                            : PP.fromPP(0);
                    HashMap<String, Object> reviverIv = null;
                    for (HashMap<String, Object> c : party) {
                        if (ivWarded) break;
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
                        if (RND.nextInt(100) < skillRate(reviverIv, reviveChance2)) {
                            List<HashMap<String, Object>> ivEquips = dao.selectEquipByCompanion(intVal(instaVictim.get("COMPANION_ID"), 0));
                            int[] ivEff = slotPenalty(computeEffectiveStat(ivJob, ivGrade, ivEquips, userStat, intVal(instaVictim.get("LIMIT_BREAK"), 0)), instaVictim, userStat);
                            ivHpAfter = PP.fromPP(Math.max(1, (int) Math.round(ivEff[0] * revivePct2)));
                            sb.append("✨ 도사의 기적! ").append(jobTag(ivGrade, ivJob, ivName))
                              .append(" 부활(HP ").append(plainNum(ivHpAfter)).append("/").append(ivEff[0]).append(")").append(NL);
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
            int[] wEff = slotPenalty(computeEffectiveStat("WARRIOR", wGrade, wEquips, userStat, intVal(c.get("LIMIT_BREAK"), 0)), c, userStat);
            PP wHp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean over50 = PP.toBaseValue(wHp) * 2 >= wEff[0];
            int guardChance = 30;
            if (warriorSynergy) guardChance += 20;      // 시너지: 전사3인조
            if (wGrade >= 6) guardChance += 20;          // ★6
            else if (wGrade >= 5) guardChance += 10;     // ★5
            if (over50 && !c.equals(target) && RND.nextInt(100) < skillRate(c, guardChance)) {
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
                    sb.append("🥷 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 기술을 흉내내 몸놀림이 가벼워졌다! (다음 턴 15% 확률로 동료 한 명의 공격을 회피)").append(NL);
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
                    sb.append("👑 ").append(eliteMonsterName(floor, mon, elite)).append("이(가) 도적의 스킬을 빼앗았다! 몸놀림이 가벼워졌다! (다음 턴 25% 확률로 동료 한 명의 공격을 회피)").append(NL);
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
        int[] tEff = slotPenalty(computeEffectiveStat(tJob, tGrade, tEquips, userStat, intVal(curTarget.get("LIMIT_BREAK"), 0)), curTarget, userStat);
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
        if (shamanDrain > 0) monsterAtk = Math.max(1, monsterAtk - shamanDrain); // 무녀 액막이: 고정 수치만큼 반격 공격력 감소(투자배율 곱하기 전)
        // [2026-09-22] "근본" 처방 -- 이 반격을 맞는 대상 본인의 투자배율(스탯구매+한계돌파)에
        // 비례해서 공격력을 추가로 올린다(investAtkScale 주석 참고). 무투자 캐릭터는 배율 1이라
        // 변화 없음.
        monsterAtk = (int) Math.round(monsterAtk * investAtkScaleFor(userStat, curTarget));
        int roll = rollFace(monsterDiceMin(floor, monsterDiceMax), monsterDiceMax); // 몬스터 자신의 반격 굴림 -- 플레이어 강화/마이너스 주사위와 무관하게 항상 1부터
        // [2026-09-22] "플레이어가 공격받을때도 방어력에 의한 차감수치 표기해줘" 요청 -- 파티
        // 공격 쪽(총 N dmg로 공격! / N - 🛡️방어 M = 실제딜)과 동일한 형식을 반격에도 적용.
        // 방어 적용 전 원본(origAtkRoll)을 따로 남겨두고, monsterHitDamage()로 방어(및 최소
        // 관통선)까지 적용한 값과의 차이를 "방어차감"으로 보여준다.
        int origAtkRoll = monsterAtk * roll;
        int rawDmgToParty = monsterHitDamage(monsterAtk, roll, tEff[2]);
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
        sb.append("🎲").append(roll).append("→ ").append(origAtkRoll).append("dmg").append(NL);
        // [2026-09-22] 방어차감 산수 -- 미드보스/보스 궁수기술 도용 증폭까지 반영된 최종
        // rawDmgToParty 기준으로 역산해서(파티 공격 쪽과 동일한 "raw - 실제 = 차감" 방식)
        // 늘 산수가 맞게 한다. 최소관통선(monsterHitDamage)에 걸리면 차감량이 원본의 50%로
        // 자동 캡핑된 상태로 보인다.
        long tMitigated = origAtkRoll - rawDmgToParty;
        if (tMitigated > 0) {
            sb.append(origAtkRoll).append("dmg - 🛡️방어 ").append(tMitigated).append("dmg = ")
              .append(rawDmgToParty).append("dmg").append(NL);
        } else if (tMitigated < 0) {
            sb.append(origAtkRoll).append("dmg + 방어무시 가산 ").append(-tMitigated).append("dmg = ")
              .append(rawDmgToParty).append("dmg").append(NL);
        }
        sb.append(dualMonster ? eliteMonsterName(floor, mon, elite, ti) : eliteMonsterName(floor, mon, elite))
          .append("의 ").append(jobTag(tGrade, tJob, tName)).append("에게 반격! ").append(NL);

        // [2026-09-05 신설] ★5/★6 도적 "회피" -- 자신이 반격 대상이 되면 일정 확률로 피해를
        // 통째로 무효화한다(실드/전사 감소보다 우선 -- 아예 안 맞은 셈이라 뒤 계산 자체를 건너뜀).
        boolean rogueEvaded = false;
        if ("ROGUE".equals(tJob) && tGrade >= 5) {
            int evadeChance = tGrade >= 6 ? 25 : 20; // [2026-09-17] 밸런스 조정: 기존 45/30 -> 25/20
            if (RND.nextInt(100) < skillRate(curTarget, evadeChance)) rogueEvaded = true;
        }

        // [2026-10-20] 전설 재킷 "랑그히리스 체이서"(EVADE_CHANCE): 맞을 때 PARAM1% 확률로 피해 무효(특수효과라 스킬 배율과 무관).
        String itemEvadeName = null;
        HashMap<String, Object> evEq = legendEquipEffect(tEquips, "EVADE_CHANCE");
        if (!rogueEvaded && evEq != null && legendRoll(intVal(evEq.get("LEGENDARY_EFFECT_PARAM1"), 0), tGrade == GROWTH_GRADE)) {
            rogueEvaded = true;
            itemEvadeName = strVal(evEq.get("LEGENDARY_ITEM_NAME"), "장비");
        }
        if (rogueEvaded) {
            dmgToParty = 0;
            sb.append(itemEvadeName != null ? "🌀 [" + itemEvadeName + "] 피해 무효!" : "🌀 회피! 피해를 완전히 피했다").append(NL);
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
                String targetAttackLinePrefix = jobTag(tGrade, tJob, tName) + " 💗" + plainNum(targetHp) + "/" + tEff[0];
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
            // [2026-10-20] 전설 갑주 "템즈 플레이트"(DAMAGE_REDUCTION): 이 동료가 받는 피해 PARAM1% 감소.
            HashMap<String, Object> drEq = legendEquipEffect(tEquips, "DAMAGE_REDUCTION");
            if (drEq != null && dmgToParty > 0) {
                int before = dmgToParty;
                double drPct = intVal(drEq.get("LEGENDARY_EFFECT_PARAM1"), 0) * legendScale(tGrade == GROWTH_GRADE); // [2026-10-23] 효과 50%(성장형 25%)
                dmgToParty = (int) Math.round(dmgToParty * (1 - drPct / 100.0));
                sb.append("🛡️ [").append(strVal(drEq.get("LEGENDARY_ITEM_NAME"), "갑주")).append("] 받는 피해 감소 (").append(before).append(" → ").append(dmgToParty).append(")").append(NL);
            }
            // [2026-09-06, 51층+ 전용 럭키] "체력 두배(3턴)" -- 실제 HP를 다시 계산하지 않고
            // 받는 피해를 절반으로 깎아 체력 두 배로 버티는 것과 동일한 효과를 낸다.
            if (luckyHpDouble) {
                int hpDoubleBefore = dmgToParty;
                dmgToParty = (int) Math.round(dmgToParty * 0.5);
                // [2026-09-30] 절반이 조용히 적용돼 위 "N dmg" 줄과 실제 피해가 달라 보이므로(적용 안 된 것처럼 오해) 명시.
                if (hpDoubleBefore > 0) {
                    sb.append("🍀 체력 두 배 효과: 받는 피해 절반 (").append(hpDoubleBefore).append(" → ").append(dmgToParty).append(")").append(NL);
                }
            }
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
        // [2026-10-20] 전설 투구 "용왕의 투구"(LAST_STAND): 전투당 1번, 쓰러질 피해를 받아도 HP 1로 생존(도사 부활 판정보다 먼저).
        if (PP.toBaseValue(targetHpAfter) <= 0 && tryLastStand(userName, p, intVal(curTarget.get("COMPANION_ID"), 0), tEquips, sb)) {
            targetHpAfter = PP.fromPP(1);
        }

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
                if (RND.nextInt(100) < skillRate(reviver, reviveChance)) {
                    targetHpAfter = PP.fromPP(Math.max(1, (int) Math.round(tEff[0] * revivePct)));
                    sb.append("✨ 도사의 기적! ").append(jobTag(tGrade, tJob, tName))
                      .append(" 부활 (HP ").append(plainNum(targetHpAfter)).append("/").append(tEff[0]).append(")").append(NL);
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
              .append(lifestealHeal).append(" 회복! 💛").append(plainNum(monsterHpAfter)).append("/").append(plainNum(monsterMaxHp));
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
              .append("dmg 추가 피해! 💛").append(plainNum(monsterHpAfter)).append("/").append(plainNum(monsterMaxHp));
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
                    int[] mEff = slotPenalty(computeEffectiveStat(mJob, mGrade, mEquips, userStat, intVal(minionC.get("LIMIT_BREAK"), 0)), minionC, userStat);

                    HashMap<String, Object> victim = stillAlive.get(RND.nextInt(stillAlive.size()));
                    String vJob = strVal(victim.get("CLASS"), "WARRIOR");
                    int vGrade = intVal(victim.get("GRADE"), 1);
                    List<HashMap<String, Object>> vEquips = dao.selectEquipByCompanion(intVal(victim.get("COMPANION_ID"), 0));
                    int[] vEff = slotPenalty(computeEffectiveStat(vJob, vGrade, vEquips, userStat, intVal(victim.get("LIMIT_BREAK"), 0)), victim, userStat);
                    PP victimHp = PP.of(((Number) victim.get("CUR_HP_VALUE")).doubleValue(), strVal(victim.get("CUR_HP_EXT"), ""));

                    int mRoll = rollFace(monsterDiceMin(floor, monsterDiceMax), monsterDiceMax);
                    int mDmg = Math.max(1, mEff[1] * mRoll - vEff[2]);
                    if (consumeWardIfTarget(userName, p, victim, sb, "하수인의 공격")) mDmg = 0;
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
            int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0)), c, userStat);
            PP hp = PP.of(((Number) c.get("CUR_HP_VALUE")).doubleValue(), strVal(c.get("CUR_HP_EXT"), ""));
            boolean dead = PP.toBaseValue(hp) <= 0;
            if (i > 0) sb.append(NL);
            sb.append(jobTag(grade, job, cName)).append(" ").append(dead ? "💀" : "💗")
              .append(plainNum(hp)).append("/").append(eff[0]);
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
        int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, intVal(c.get("LIMIT_BREAK"), 0)), c, userStat);
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
        int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, intVal(companion.get("LIMIT_BREAK"), 0)), companion, userStat);
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
        HashMap<String, Object> normal = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), "N"), floor);
        if (normal != null && intVal(normal.get("MONSTER_ID"), -1) == monsterId) return normal;
        // [2026-09-21] 이전엔 여기서 applyHardcoreFloorScale을 안 거쳐서(보스는 HP/ATK 스케일
        // 대상이 아니라 안 써도 무방했음) 새로 생긴 층 기반 DEF 재정의(monsterDefForFloor)도
        // 같이 빠졌었다. 보스도 반드시 거치게 통일.
        HashMap<String, Object> boss = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), "Y"), floor);
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
        HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), "N");
        if (mon == null) return null;

        long kills = cappedMin / minPerKill;
        if (kills <= 0) return null;

        PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), strVal(mon.get("PP_PER_KILL_EXT"), ""));
        PP reward = perKill.multiply(kills * floorPpMultiplier(floor) * autoHuntFloorBonusMultiplier(floor));
        addPp(userName, p, reward);
        // [2026-10-23] 광산상점 수익(처치당 PP 보너스) -- 이번 정산의 자동사냥 처치 수만큼 함께 지급(자동사냥 업적 누적에는 넣지 않음)
        PP mineBonus = minePpBonus(userName, kills);
        if (mineBonus != null) addPp(userName, p, mineBonus);

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
                + " PP 획득!" + (mineBonus != null ? " ⛏️ 광산 수익 +" + mineBonus.format() + " PP" : "") + " (보유 " + curPp.format() + " PP)";
    }

    // ================================================================
    // /층변경 N
    // ================================================================
    @Override
    @Transactional
    public String changeFloor(String userName, int n) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        boolean wasInCombat = "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"));
        // [2026-09-28] 101층+ 계단 구역 출입/이동은 10층 블록 규칙(X0=마을, X9=보스, 블록 안
        // 0~9 이동)과 맞지 않아 별도 처리 -- 100층 마을에서 위로 가거나(1~9 또는 가본 층 번호),
        // 계단 구역 안에서 움직이는 모든 경우.
        if (floor >= STAIR_ZONE_BASE_CAMP && (isStairZone(floor) || n != 0)) {
            return changeFloorStairZone(userName, p, floor, n, wasInCombat);
        }
        if (n < 0 || n > 9) {
            return "층변경은 0~9 범위만 가능합니다. (같은 10층 구간 내 이동)";
        }
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
            logBattleResult(userName, p, floor, "FLEE", intVal(p.get("CUR_COMBAT_TURN"), 0)); // [2026-09-30] 밸런스 통계
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
            return "🏘️ 이미 이 구간의 마을입니다. 더 아래 구간으로 가려면 /계단내려가기를 사용하세요.";
        }
        return changeFloor(userName, fm - 1);
    }

    /** [2026-09-28] 계단 구역(101층+) /층변경 처리. n: 0=100층 마을(베이스캠프, 부활),
     *  1~9=지금 층의 10층 단위 안 이동(100층 마을에선 101~109), 101 이상=그 층 번호로 바로
     *  이동(이미 가본 층까지만 -- 전멸 후 매번 101층부터 다시 오르지 않게). 계단 구역 층에
     *  도착하면 항상 그 층 1번(계단) 칸부터 새로 시작한다(enterStairFloor 참고). */
    private String changeFloorStairZone(String userName, HashMap<String, Object> p, int floor, int n, boolean wasInCombat) {
        int target;
        if (n >= STAIR_ZONE_START) target = n;
        else if (n == 0) target = stairCampOf(floor);
        else if (n >= 1 && n <= 9) target = floorBlockBase(floor) + n;
        else return "층변경은 0~9, 또는 가본 적 있는 " + STAIR_ZONE_START + "층 이상 층 번호로 입력하세요. (예: /층변경 0 = "
                + stairCampOf(floor) + "층 마을, /층변경 " + STAIR_ZONE_START + ")";
        if (target == floor) return "이미 " + floor + "층에 있습니다.";
        if (target > stairZoneOpenMax) {
            return "🌑 어둠이 득실거려 현재는 갈 수 없습니다. (" + stairZoneOpenMax + "층까지 오픈, 이후 추후 오픈 예정)";
        }
        int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        // [2026-10-01] 마을(200/300..) 바로 위 첫 층(201/301..)은 101층과 같이 항상 입장 가능(마을에서 올라가는 길목)
        if (target > STAIR_ZONE_START && target % 100 != 1 && target > maxReached) {
            return "🪜 " + target + "층은 아직 가본 적이 없습니다." + NL
                    + "계단 구역은 한 층씩 직접 올라가야 합니다. (지금까지 최고 " + Math.max(maxReached, STAIR_ZONE_BASE_CAMP) + "층)";
        }

        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", target);
        up.put("killCountCur", 0);
        int newFleeCount = -1;
        if (wasInCombat) {
            logBattleResult(userName, p, floor, "FLEE", intVal(p.get("CUR_COMBAT_TURN"), 0)); // [2026-09-30] 밸런스 통계
            up.put("status", "NORMAL");
            up.put("clearMonster", true);
            newFleeCount = intVal(p.get("FLEE_COUNT_TOTAL"), 0) + 1;
            up.put("fleeCountTotal", newFleeCount);
        }
        dao.updateUserProgress(up);
        p.put("CUR_FLOOR", target);
        if (newFleeCount >= 0) checkFleeAchievements(userName, newFleeCount);
        grantFloorAchievements(userName, target);

        StringBuilder sb = new StringBuilder(userName).append("님," + NL);
        if (wasInCombat) sb.append("💨 전투에서 도망쳤습니다!").append(NL);
        sb.append(floor).append("층 → ").append(target).append("층(").append(floorKindLabel(target)).append(")으로 이동했습니다.");
        if (!isStairZone(target)) { // 100의 배수 = 마을(부활)
            int revivedCount = revivePartyDead(userName, dao.selectUserCompanions(userName), dao.selectUserStat(userName));
            if (revivedCount > 0) {
                sb.append(NL).append("✨ 전투불가 상태였던 동료 ").append(revivedCount).append("명이 마을에서 부활했습니다!");
            }
        } else {
            enterStairFloor(userName, target);
            sb.append(NL).append("🪜 1번 계단 칸에서 시작합니다. (다음: ").append(TILE_LABEL.get(STAIR_ZONE_PATTERN[1])).append(")");
        }
        return sb.toString();
    }

    /** 계단 구역 층에 새로 도착 -- 이전 원정의 위치/방문기록/보드를 지우고 1번(계단) 칸에서 시작. */
    private void enterStairFloor(String userName, int floor) {
        dao.deleteUserFloorProgress(userName, floor);
        dao.deleteTileVisits(userName, floor);
        dao.deleteUserTileMaster(userName, floor);
        ensureUserBoard(userName, floor);
        HashMap<String, Object> ufp = new HashMap<>();
        ufp.put("userName", userName);
        ufp.put("floor", floor);
        ufp.put("curTile", 1);
        ufp.put("entryTile", 1); // 함정 RESET_TILE(처음 계단칸으로) 복귀 지점
        dao.upsertUserFloorProgress(ufp);
        dao.insertTileVisit(userName, floor, 1);
    }

    /** 계단 구역에서 9번째 칸(마지막 중간보스)까지 끝낸 뒤의 이동 -- 한 층 위 1번 칸으로 오른다. */
    private String climbStairZone(String userName, HashMap<String, Object> p, int floor) {
        int next = floor + 1;
        if (next > stairZoneOpenMax) {
            return userName + "님," + NL + "🌑 " + floor + "층 위로는 아직 어둠이 득실거립니다. (" + stairZoneOpenMax
                    + "층까지 오픈, 이후 추후 오픈 예정)" + NL + "👉 /층변경 0 으로 " + stairCampOf(floor) + "층 마을로 돌아갈 수 있어요.";
        }
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("curFloor", next); // MAX_FLOOR_REACHED는 매퍼에서 GREATEST로 함께 갱신됨
        dao.updateUserProgress(up);
        p.put("CUR_FLOOR", next);
        grantFloorAchievements(userName, next);
        if (!isStairZone(next)) {
            // [2026-09-29] 다음 층이 100의 배수면 마을 -- 보드 없이 도착, 파티 전원 부활.
            int revived = revivePartyDead(userName, dao.selectUserCompanions(userName), dao.selectUserStat(userName));
            return userName + "님," + NL + "🪜⬆️ " + floor + "층을 돌파하고 계단을 올라 " + next + "층 마을에 도착했습니다!"
                    + (revived > 0 ? NL + "✨ 전투불가 상태였던 동료 " + revived + "명이 마을에서 부활했습니다!" : "")
                    + NL + "👉 /층변경 1 로 " + (next + 1) + "층부터 이어서 오를 수 있어요. (/층변경 0 으로 이 마을 복귀)";
        }
        enterStairFloor(userName, next);
        return userName + "님," + NL + "🪜⬆️ " + floor + "층을 돌파하고 계단을 올라 " + next + "층에 도착했습니다!" + NL
                + "🗺️ " + next + "층 1/" + STAIR_ZONE_PATTERN.length + "단계 (다음: " + TILE_LABEL.get(STAIR_ZONE_PATTERN[1]) + ")";
    }

    /**
     * /계단내려가기(/탑내려가기, /계단다운, /탑다운) — 100층 이하는 마을에서만 바로 아래 10층 구간
     * 마을로 이동, 101층+는 계단 구역 어디서든 그 구간 100의 배수 마을로 이동(200층 마을에선 바로
     * 아래 100층 마을). changeFloor()와 달리
     * 같은 구간을 벗어나는 이동이라 target이 항상 이전에 실제로 밟았던 마을(구간을 순서대로
     * 올라와야만 지금 서 있을 수 있으므로)이라 별도 재진입 자격 확인이 필요 없다. 사냥터층
     * 탐사 초기화(resetBlockExploration)도 여긴 해당 없음(마을→마을 이동은 사냥터층을 아예
     * 거치지 않음).
     */
    @Override
    public String descendVillage(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        if (isStairZone(floor)) {
            // [2026-09-29] "101~199층에서는 100층 마을로 이동" 요청 -- 계단 구역(110/120...층 포함)
            // 어디서든 /계단내려가기 = 지금 구간의 마을(100의 배수)로 이동(/층변경 0과 동일, 전투 중이면 도망).
            boolean wasInCombat = "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"));
            return changeFloorStairZone(userName, p, floor, 0, wasInCombat);
        }
        if (floor % 10 != 0) {
            return "🏘️ 마을에서만 사용할 수 있습니다. (/층변경 0 으로 먼저 마을로 이동하세요)";
        }
        if (floor == 0) {
            return "🏘️ 이미 0층 마을입니다. 더 내려갈 곳이 없습니다.";
        }
        // 200/300...층 마을의 "아래 마을"은 10층 아래(계단층)가 아니라 바로 아래 100층 단위 마을.
        int target = (floor > STAIR_ZONE_BASE_CAMP && floor % 100 == 0) ? floor - 100 : floor - 10;

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
        if (floor >= STAIR_ZONE_BASE_CAMP) {
            // [2026-09-28] 100층 위는 마을이 없는 계단 구역 -- 탑올라가기 대신 층변경으로 진입.
            return "🪜 " + floor + "층 위는 100층마다만 마을이 있는 계단 구역입니다. /층변경 1 로 "
                    + (floor + 1) + "층부터 오르거나, /층변경 <층번호> 로 가본 층까지 바로 이동하세요.";
        }
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
            String line = idx + ". " + name + " (" + job + " " + gradeMark(grade)
                    + (limitBreak > 0 ? " (+" + limitBreak + ")" : "") + ")"
                    + (slot != null ? " [파티 " + slot + "번]" : " [대기]");
            order.add(new int[]{ grade, limitBreak, lines.size() });
            lines.add(line);
            idx++;
        }
        order.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]); // 성급desc, 동점이면 한계돌파desc
        for (int[] o : order) sb.append(lines.get(o[2])).append(NL);
        if (hiddenCount > 0) sb.append("(숨긴 동료 ").append(hiddenCount).append("마리는 표시 생략, 웹에서 확인)").append(NL);
        int max = partyMax(dao.selectUserProgress(userName));
        sb.append("/파티편성 N 으로 편성/해제 (최대 ").append(max).append("명").append(max < 4 ? ", " + PARTY4_UNLOCK_FLOOR + "층 도달 시 4명" : "")
          .append("), /동료가리기 N 으로 목록 숨김/해제");
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
        for (HashMap<String, Object> e : equipped) unwearEquipOne(strVal(companion.get("USER_NAME"), ""), e);
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
        int max = partyMax(p);
        if (used >= max) {
            return "파티는 최대 " + max + "명까지 편성 가능합니다. 다른 동료를 먼저 해제하세요."
                    + (max < 4 ? " (" + PARTY4_UNLOCK_FLOOR + "층에 도달하면 4명까지)" : "");
        }
        boolean[] usedSlot = new boolean[6];
        for (HashMap<String, Object> c : companions) {
            Object s = c.get("PARTY_SLOT");
            if (s != null && ((Number) s).intValue() >= 1 && ((Number) s).intValue() <= 5) usedSlot[((Number) s).intValue()] = true;
        }
        int slot = 1;
        while (slot <= max && usedSlot[slot]) slot++;

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
        if (targetSlot == 4 && partyMax(p) < 4) {
            return "🔒 파티 4번 슬롯은 " + PARTY4_UNLOCK_FLOOR + "층에 도달하면 열립니다.";
        }
        if (targetSlot < 1 || targetSlot > partyMax(p)) {
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
    /** [2026-10-08] "이벤트지급 시 최근 3일내 전투기록이 있는 사람만으로 한정" 요청 -- 이 일수 안에 TBOT_S5_BATTLE_STAT(전투 종료 기록)이 있는 유저에게만 지급. */
    private static final int EVENT_GRANT_ACTIVE_DAYS = 3;

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
        StringBuilder sb = new StringBuilder("🎉 이벤트 지급 완료! (최근 " + EVENT_GRANT_ACTIVE_DAYS + "일 내 전투 기록이 있는 유저만)");
        if (companionQty > 0) {
            int affected = dao.bulkGrantTierCompanionVoucher(tier, companionQty, EVENT_GRANT_ACTIVE_DAYS);
            sb.append(NL).append("최근 " + EVENT_GRANT_ACTIVE_DAYS + "일 내 전투한 유저 ").append(affected).append("명에게 ")
              .append(COMPANION_TIER_NAME[tier - 1]).append("(").append(tier).append("번) 동료뽑기권 ")
              .append(companionQty).append("장 지급 (해금 여부와 무관하게 바로 사용 가능)");
        }
        if (equipQty > 0) {
            int affected = dao.bulkGrantTierEquipVoucher(tier, equipQty, EVENT_GRANT_ACTIVE_DAYS);
            sb.append(NL).append("최근 " + EVENT_GRANT_ACTIVE_DAYS + "일 내 전투한 유저 ").append(affected).append("명에게 ")
              .append(COMPANION_TIER_NAME[tier - 1]).append("(").append(tier).append("번) 장비뽑기권 ")
              .append(equipQty).append("장 지급 (해금 여부와 무관하게 바로 사용 가능)");
        }
        if (accessoryQty > 0) {
            int affected = dao.bulkGrantTierAccessoryVoucher(tier, accessoryQty, EVENT_GRANT_ACTIVE_DAYS);
            sb.append(NL).append("최근 " + EVENT_GRANT_ACTIVE_DAYS + "일 내 전투한 유저 ").append(affected).append("명에게 ")
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
        HashMap<String, Object> row = cachedNoticeRow();
        HashMap<String, Object> result = new HashMap<>();
        if (row.isEmpty()) {
            result.put("APP_VERSION", "0");
            result.put("NOTICE_TEXT", "");
            result.put("DISMISSED", false);
            return result;
        }
        result.putAll(row);
        boolean dismissed = false;
        if (userName != null && !userName.trim().isEmpty()) {
            String u = userName.trim();
            String seen = NOTICE_SEEN_CACHE.get(u);
            if (seen == null) {
                seen = dao.selectNoticeSeenVersion(u);
                if (seen == null) seen = "";
                NOTICE_SEEN_CACHE.put(u, seen);
            }
            String version = String.valueOf(row.get("APP_VERSION"));
            dismissed = version.equals(seen);
        }
        result.put("DISMISSED", dismissed);
        return result;
    }

    // [2026-10-22] "이거 왜 계속 호출해? 안 호출하게" 요청 -- 웹 화면이 탭마다 3분마다 /api/tower-notice를 불러서 그때마다
    // 공지 행 + 그 유저의 NOTICE_SEEN_VERSION을 DB에서 읽었다(이미 열려 있는 탭은 새로고침 전까지 옛 화면 코드로 계속 부른다).
    // 공지 행은 10분간 메모리에 두고(/공지등록 하면 즉시 갱신), 유저별 "다시 보지 않기" 값도 처음 한 번만 읽고 메모리에 기억한다
    // (이 값은 dismissNotice로만 바뀌고, 바꿀 때 메모리도 같이 고친다). DB를 직접 고친 공지는 최대 10분 늦게 반영된다.
    private static final long NOTICE_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static volatile HashMap<String, Object> noticeCache; // 빈 맵 = 공지 행 없음
    private static volatile long noticeCacheAt;
    private static final java.util.concurrent.ConcurrentHashMap<String, String> NOTICE_SEEN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private HashMap<String, Object> cachedNoticeRow() {
        HashMap<String, Object> c = noticeCache;
        if (c != null && System.currentTimeMillis() - noticeCacheAt < NOTICE_CACHE_TTL_MS) return new HashMap<>(c);
        HashMap<String, Object> row = dao.selectNotice();
        c = row == null ? new HashMap<String, Object>() : new HashMap<>(row);
        noticeCache = c;
        noticeCacheAt = System.currentTimeMillis();
        return new HashMap<>(c);
    }

    /** 웹 SPA 전용: "다시 보지 않기" -- 현재 공지 버전을 그 유저의 NOTICE_SEEN_VERSION으로
     *  저장. 진행 기록이 없는 유저(TBOT_S5_USER_PROGRESS 행 미존재)면 0건 갱신, 조용히 무시
     *  (그런 유저는 애초에 볼 진행 데이터가 없어 이 페이지를 실제로 쓸 일이 없음). */
    @Override
    @Transactional
    public void dismissNotice(String userName) {
        if (userName == null || userName.trim().isEmpty()) return;
        HashMap<String, Object> row = cachedNoticeRow();
        String version = row.isEmpty() ? "0" : String.valueOf(row.get("APP_VERSION"));
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName.trim());
        up.put("version", version);
        dao.updateNoticeSeenVersion(up);
        NOTICE_SEEN_CACHE.put(userName.trim(), version);
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
        noticeCache = null; // 새 공지를 다음 확인부터 바로 보이게
        return "📢 공지 등록 완료! (버전 " + version + ") 웹 화면을 켜둔 접속자들에게 새로고침 안내 팝업이 순서대로 뜹니다.";
    }

    /**
     * /탑통계(관리자 전용) — /이벤트지급과 같은 EVENT_ADMIN_USERS 권한 체크를 그대로 재사용.
     * "어느 채널이 몇 명인지" 별도 컬럼으로 안 남기고 있어서, 웹 액션마다 TBOT_WORD_HIS.
     * ROOM_NAME='WEB'으로 로그를 남기는 기존 장치(Season5ViewController.apiTowerAction)를
     * 역이용해서 집계한다(selectChannelUsageStats 주석 참고) -- 정확한 실시간 집계가 아니라
     * 대략적인 채널 분포 참고용이라는 걸 명시해서 오해 없게 한다.
     */
    /** [2026-09-29] "/로그는 관리자만 사용할수 있게 해줘" 요청 -- /이벤트지급·/탑통계와 같은
     *  EVENT_ADMIN_USERS 권한 체크 재사용. 링크는 절대경로(채팅앱 링크 인식, TOWER_VIEW_URL과 동일 이유). */
    @Override
    public String battleLogLink(String userName) {
        if (!isEventAdmin(userName)) {
            return "권한이 없습니다.";
        }
        return "📜 유저별 전투/웹 로그 뷰어: http://rgb-tns.dev-apc.com/loa/tower-battle-log";
    }

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
        // [2026-10-21 성능] 예전엔 액션마다 (1) 유저 행 카운터 UPDATE (2) 시간대별 공용 행 MERGE를 각각 단독으로(커밋 2번) 실행했다.
        // 통계 화면에서만 읽는 값이라 메모리에 모았다가 flushStatBuffers()가 30초마다 한 번에 반영한다.
        PENDING_ACTIVITY.merge(userName + "\u0001" + cols[0] + "\u0001" + cols[1], 1, Integer::sum);
        // [2026-09-06] "/탑통계에 1시간/24시간/오늘 기준도 보고싶다" 요청 -- 전체 누적 컬럼과
        // 별개로, 현재 시(hour) 버킷에도 같이 적재해서 시간대별 집계를 가능하게 함.
        // statKey는 항상 "STATTYPE_CHANNEL"(예: DICE_WEB) 형태의 고정 화이트리스트 값.
        int us = statKey.lastIndexOf('_');
        if (us > 0) {
            PENDING_HOURLY.merge(statKey.substring(0, us) + "\u0001" + statKey.substring(us + 1), 1, Integer::sum);
        }
    }

    // ---- [2026-10-21 성능] 통계 카운터 쓰기 모으기 ----
    // 이 값들은 통계 화면에서만 읽고 게임 진행에는 쓰이지 않는다. 주사위 눈 분포(전 유저가 같은 행을 올림)와 시간대별 활동(같은 행)은
    // 여러 유저가 동시에 같은 행을 갱신하면 서로의 커밋을 기다리고, 눈을 서로 다른 순서로 갱신하면 교착(ORA-00060)까지 날 수 있어서
    // 메모리에 모았다가 별도 짧은 트랜잭션에서 한 번에 반영한다. 서버가 갑자기 죽으면 최대 30초치가 사라질 뿐이다.
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> PENDING_FACE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> PENDING_ACTIVITY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> PENDING_HOURLY = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void flushStatBuffers() {
        for (Integer face : new ArrayList<>(PENDING_FACE.keySet())) {
            Integer n = PENDING_FACE.remove(face);
            if (n == null || n <= 0) continue;
            try { dao.bumpDiceFaceStatBy(face, n); } catch (Exception e) { PENDING_FACE.merge(face, n, Integer::sum); }
        }
        for (String key : new ArrayList<>(PENDING_ACTIVITY.keySet())) {
            Integer n = PENDING_ACTIVITY.remove(key);
            if (n == null || n <= 0) continue;
            String[] p = key.split("\u0001");
            try { dao.bumpActivityStatBy(p[0], p[1], p[2], n); } catch (Exception e) { /* 통계 실패는 버린다(유저 삭제 등) */ }
        }
        for (String key : new ArrayList<>(PENDING_HOURLY.keySet())) {
            Integer n = PENDING_HOURLY.remove(key);
            if (n == null || n <= 0) continue;
            String[] p = key.split("\u0001");
            try { dao.bumpActivityHourlyBy(p[0], p[1], n); } catch (Exception e) { PENDING_HOURLY.merge(key, n, Integer::sum); }
        }
    }

    /** 서버 종료/재배포 때 아직 반영 안 된 통계를 남기지 않도록 마지막으로 한 번 반영. */
    @javax.annotation.PreDestroy
    public void flushStatBuffersOnShutdown() {
        try { flushStatBuffers(); } catch (Throwable ignore) { }
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
            BufferedImage original;
            if (imageUrl.startsWith("classpath:")) {
                // [2026-10-20] 앱에 포함된 이미지(예: 성장형 동료 그림 s5/growth_doodle.png) -- 외부 호출 없이 클래스패스에서 읽는다.
                try (InputStream is = BotS5ServiceImpl.class.getResourceAsStream("/" + imageUrl.substring("classpath:".length()))) {
                    if (is == null) return null;
                    original = ImageIO.read(is);
                }
            } else {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                // fetchRandomNekoImage()와 동일 이유(User-Agent 없으면 403).
                conn.setRequestProperty("User-Agent", "RgbTowerBot/1.0 (https://rgb-tns.dev-apc.com)");
                if (conn.getResponseCode() != 200) return null;
                try (InputStream is = conn.getInputStream()) {
                    original = ImageIO.read(is);
                }
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
            if (intVal(owned.get("GRADE"), 1) == GROWTH_GRADE) continue; // 성장형 동료는 이름/직업이 바뀌므로 중복 판정 대상이 아니다
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
                if (grade == 5 && newLimitBreak >= LIMIT_BREAK_MAX && checkLegendBegin(userName)) result.put("legendBegin", true);
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
                        + "한계돌파+" + lb + " 달성! (전체 스탯 +" + Math.round(limitBreakPct(lb) * 100) + "%)"
                        + (Boolean.TRUE.equals(r.get("legendBegin")) ? NL + "🏆 업적 [전설의 시작] 달성! /7성소환 으로 ★7 동료를 소환할 수 있습니다." : "");
            }
            return "🔁 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 뽑힘)" + NL
                    + "한계돌파가 이미 최대(+" + LIMIT_BREAK_MAX + ")라 계약서 대신 " + r.get("dupeBonus") + " PP로 환급되었습니다.";
        }

        int[] stat = (int[]) r.get("stat");
        int cnt = dao.selectUserCompanions(userName).size(); // 목록 번호는 성장형 포함 전체 기준(보유 수 집계는 성장형 제외)

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
        boolean legendBeginHit = false; // 이번 10연속 중에 업적 [전설의 시작]을 달성했는지
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
                    if (Boolean.TRUE.equals(r.get("legendBegin"))) legendBeginHit = true;
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
        if (legendBeginHit) sb.append(NL).append("🏆 업적 [전설의 시작] 달성! /7성소환 으로 ★7 동료를 소환할 수 있습니다.");
        if (stopReason != null) sb.append(NL).append("⚠️ ").append(stopReason).append(" (그 이상은 중단됨)");
        sb.append(NL).append("👉 /파티편성 으로 확인하세요");
        return sb.toString();
    }

    private boolean isVillage(HashMap<String, Object> p) {
        int floor = intVal(p.get("CUR_FLOOR"), 0);
        return floor % 10 == 0 && !isStairZone(floor); // 110/120...층은 계단 구역의 일반 층(마을 아님)
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

        addEquipItems(userName, equipClassToStore, part, grade, 1); // [2026-10-22] 같은 종류 스택에 수량으로 쌓는다

        if (grade == 6) grantAchievement(userName, 22);

        // [2026-09-18] "최상급장비상자에서 조각이 1~3개정도 나오게 해줘" 요청 -- GACHA_ID=8
        // ("전설의 장비 상자", 80층 해금 최상급 티어)에서 조각 1~3개 보너스 지급.
        // [2026-09-21 정정] "전설의조각이 너무잘나오는거같아, 확률에 맞게나오도록 해줘" 신고 --
        // 처음엔 매 뽑기마다 100% 확정 지급이라 상자를 열 때마다 조각이 계속 쌓여서 너무
        // 후했다. 보스층 드랍(LEGEND_FRAGMENT_DROP_PCT, 5~25%)과 같은 확률 방식으로 바꿔서
        // LEGEND_FRAGMENT_BOX_DROP_PCT(15%) 확률로만 발동하게 하고, 발동 시 수량(1~3개)은
        // 기존 그대로 유지.
        if (intVal(gacha.get("GACHA_ID"), 0) == 8 && RND.nextInt(100) < LEGEND_FRAGMENT_BOX_DROP_PCT) {
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
            result += NL + "🧩 전설의 장비 상자에서 전설의조각 " + r.get("fragmentGranted") + "개도 함께 획득!";
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
        if (fragmentTotal > 0) sb.append(NL).append("🧩 전설의 장비 상자에서 전설의조각 ").append(fragmentTotal).append("개 획득!");
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

        addEquipItems(userName, "COMMON", part, grade, 1); // [2026-10-22] 같은 종류 스택에 수량으로 쌓는다

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
    public int stairZoneMaxFloor() {
        return stairZoneOpenMax;
    }

    @Override
    public int autoHuntKillsPerHour() {
        return AUTO_HUNT_KILLS_PER_HOUR;
    }

    @Override
    public String currentFloorMonsterName(int floor) {
        boolean isBossFloor = !isStairZone(floor) && floor % 10 == 9; // 계단 구역엔 보스층이 없음
        HashMap<String, Object> mon = dao.selectMonster(monsterBlockNo(floor), isBossFloor ? "Y" : "N");
        if (mon == null) return null;
        return floorMonsterName(floor, mon);
    }

    /** [2026-09-21] 전투화면 재설계(스탯표 POWER/GUARD)용 -- 이 층 몬스터의 실제 전투 ATK/DEF
     *  (V2 오버레이/하드코어 스케일 전부 반영된 값). null이면 [atk, def] 순서. */
    @Override
    public int[] currentFloorMonsterAtkDef(int floor) {
        boolean isBossFloor = !isStairZone(floor) && floor % 10 == 9; // 계단 구역엔 보스층이 없음
        HashMap<String, Object> mon = applyHardcoreFloorScale(dao.selectMonster(monsterBlockNo(floor), isBossFloor ? "Y" : "N"), floor);
        if (mon == null) return null;
        return new int[]{ (int) Math.round(((Number) mon.get("ATK_VALUE")).doubleValue()), (int) Math.round(((Number) mon.get("DEF_VALUE")).doubleValue()) };
    }

    /** 스탯 강화 상한 계산: 구간(10층 단위) 하나 클리어(보스 처치)마다 +5. index0(unlockedBlock=0)일 때도 최소 5. */
    private int statCapFor(int unlockedBlock) {
        return 5 + 5 * (unlockedBlock / 10);
    }

    /** [2026-10-01] "200층 상점에 스탯 업그레이드 추가" -- 계단 구역엔 보스 처치로 오르는 UNLOCKED_BLOCK이 없어서, 100의 배수 마을(200, 300..)에
     *  도착(가본 최고층 MAX_FLOOR_REACHED가 그 층 이상)할 때마다 상한을 +5 더 준다. 200층 마을 도착 = +5, 300층 = +10. */
    private int statCapForUser(HashMap<String, Object> p) {
        int base = statCapFor(intVal(p.get("UNLOCKED_BLOCK"), 0));
        int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        return base + 5 * Math.max(0, Math.min(maxReached, STAIR_ZONE_READY_MAX) / 100 - 1);
    }

    /** 스탯 상한이 다음에 오르는 마을 층: 100층 미만 구간은 다음 10층 마을, 100층 이후는 아직 못 가본 다음 100의 배수 마을. */
    private int nextStatVillageFloor(HashMap<String, Object> p) {
        int unlockedBlock = intVal(p.get("UNLOCKED_BLOCK"), 0);
        if (unlockedBlock < STAIR_ZONE_BASE_CAMP) return unlockedBlock + 10;
        int maxReached = intVal(p.get("MAX_FLOOR_REACHED"), 0);
        return Math.max(200, (maxReached / 100 + 1) * 100);
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
        int cap = statCapForUser(p);
        int nextVillageFloor = nextStatVillageFloor(p); // 다음 상한이 열리는 마을(100층 미만: 그 앞 보스 처치 / 100층 이후: 다음 100의 배수 마을 도착)

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
        int cap = statCapForUser(p);

        HashMap<String, Object> result = new HashMap<>();
        result.put("atkMaxLv", atkMaxLv);
        result.put("atkMinLv", atkMinLv);
        result.put("hpLv", hpLv);
        result.put("cap", cap);
        result.put("nextCap", cap + 5);
        result.put("nextVillageFloor", nextStatVillageFloor(p));
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
        // [2026-10-22] 실제 능력치 계산(computeEffectiveStat)은 V2 표(TBOT_S5_EQUIP_BONUS_V2)를 쓰는데 이 문구만 예전 하드코딩 표(EQUIP_BONUS)를 보여줘서
        // 화면/채팅의 장비 옵션이 실제와 달랐다(예: ★6 무기 실제 ATK +139/+31.2%인데 +150/+35%로 표시). 같은 행(equipBonusRow)을 쓰도록 통일.
        double[] b = equipBonusRow(grade);
        if ("NECKLACE".equals(part)) { // 무기(ATK)+갑옷(DEF) 절반씩
            return "ATK +" + Math.round(b[2] / 2) + "/+" + pct1(b[3] * 50) + "% · DEF +" + Math.round(b[4] / 2) + "/+" + pct1(b[5] * 50) + "%";
        }
        if ("RING".equals(part)) { // 무기(ATK)+투구(HP) 절반씩
            return "ATK +" + Math.round(b[2] / 2) + "/+" + pct1(b[3] * 50) + "% · HP +" + Math.round(b[0] / 2) + "/+" + pct1(b[1] * 50) + "%";
        }
        if ("BRACELET".equals(part)) { // 갑옷(DEF)+투구(HP) 절반씩
            return "DEF +" + Math.round(b[4] / 2) + "/+" + pct1(b[5] * 50) + "% · HP +" + Math.round(b[0] / 2) + "/+" + pct1(b[1] * 50) + "%";
        }
        int fixedIdx = "HELMET".equals(part) ? 0 : "WEAPON".equals(part) ? 2 : 4;
        int pctIdx = fixedIdx + 1;
        String statName = "HELMET".equals(part) ? "HP" : "WEAPON".equals(part) ? "ATK" : "DEF";
        return statName + " +" + Math.round(b[fixedIdx]) + " / +" + pct1(b[pctIdx] * 100) + "%";
    }

    /** 실제 능력치 계산(computeEffectiveStat)과 같은 장비 보너스 행 -- V2 표가 로드돼 있으면 V2, 아니면 기본 표. */
    private double[] equipBonusRow(int grade) {
        double[][] equipV2 = EQUIP_BONUS_V2;
        return (BALANCE_V2_ENABLED && equipV2 != null && grade - 1 < equipV2.length && equipV2[grade - 1] != null)
                ? equipV2[grade - 1] : EQUIP_BONUS[grade - 1];
    }

    /** 퍼센트 표기 -- 정수면 정수, 아니면 소수 1자리(31.2). */
    private static String pct1(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    // [2026-10-22] "설정에서 8개 탭 순서를 바꾸거나 가리기, 유저별로" 요청 -- 하단 메뉴 배치(TBOT_S5_USER_PROGRESS.DOCK_LAYOUT).
    // 순서대로 쉼표로 잇고 숨긴 탭은 앞에 '!'(예: board,party,!msg,...). 탑(board)과 설정(settings)은 숨길 수 없다(되돌릴 길/게임 진행이 막히지 않게).
    // 모르는 키/중복은 버리고, 빠진 키는 뒤에 붙인다(나중에 탭이 늘어나도 기존 저장값이 깨지지 않게). 기본 배치와 같으면 NULL로 저장.
    private static final List<String> DOCK_KEYS = java.util.Arrays.asList("board", "party", "shop", "inven", "ach", "codex", "msg", "settings", "mine", "mineshop");

    @Override
    @Transactional
    public String setDockLayout(String userName, String raw) {
        getOrInitProgress(userName);
        List<String> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String t : (raw == null ? "" : raw).split(",")) {
            t = t.trim();
            boolean hidden = t.startsWith("!");
            String k = hidden ? t.substring(1) : t;
            if (!DOCK_KEYS.contains(k) || !seen.add(k)) continue;
            if ("board".equals(k) || "settings".equals(k)) hidden = false;
            out.add((hidden ? "!" : "") + k);
        }
        for (String k : DOCK_KEYS) if (seen.add(k)) out.add(k);
        String v = String.join(",", out);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("dockLayout", v.equals(String.join(",", DOCK_KEYS)) ? "" : v); // 빈 문자열 = Oracle NULL = 기본 배치
        dao.updateUserProgress(up);
        return "하단 메뉴 설정을 저장했습니다.";
    }

    /** [2026-10-22] 인벤토리 화면용 장비 옵션 문구(부위/등급 기준 능력치 보너스). */
    @Override
    public String equipOptionText(String part, int grade) {
        if (grade < 1 || grade > 7) return "";
        return equipBonusText(part, grade);
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
        int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, limitBreak), target, userStat);
        int[] effBase = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, 0), target, userStat);
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
            int[] eff = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, limitBreak), c, userStat);
            int[] effBase = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, 0), c, userStat);
            c.put("EFF_HP", eff[0]); c.put("EFF_ATK", eff[1]); c.put("EFF_DEF", eff[2]);
            if (intVal(c.get("PARTY_SLOT"), 0) == 4) c.put("WEAK_PCT", Math.round(party4Penalty(userStat) * 1000) / 10.0); // 화면 안내용(약화율 %)
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
            companionLabel.put(intVal(c.get("COMPANION_ID"), -1), job + "(" + name + ") " + gradeMark(intVal(c.get("GRADE"), 1)));
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
                  .append(" ★").append(grade).append(qtyOf(e) > 1 ? " x" + qtyOf(e) : "")
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
                  .append(" ★").append(grade).append(qtyOf(e) > 1 ? " x" + qtyOf(e) : "")
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
                sb.append(idx++).append(". ").append(job).append("(").append(name).append(") ").append(gradeMark(intVal(c.get("GRADE"), 1))).append(NL);
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
        if (intVal(targetCompanion.get("GRADE"), 1) != GROWTH_GRADE && !allowedJobs.contains(strVal(targetCompanion.get("CLASS"), ""))) return "이 장비는 " + equipClassLabel(equipClass, part) + " 전용입니다.";

        int companionId = intVal(targetCompanion.get("COMPANION_ID"), 0);
        // [2026-09-18] 장착으로 EFF_HP가 바뀌어도 비율 유지(rescaleHpForEquipChange 참고) --
        // 장착 "전" 최대체력을 먼저 구해둔다.
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int oldMaxHp = effMaxHpOf(targetCompanion, userStat);
        // [2026-10-22] 전설 착용 규칙 확인 -- 2026-10-23에 "동료당 1개", "같은 전설은 한 동료만" 둘 다 폐지되어 지금은 항상 통과(legendWearBlock)
        String legendBlock = legendWearBlock(userName, equip, companionId);
        if (legendBlock != null) return legendBlock;
        // 같은 부위에 이미 장착된 게 있으면 해제 -- [2026-10-22] 수량(QTY) 때문에 장착을 먼저 하고 기존 장비 해제는 그 뒤에(unwearEquipOne 설명 참고)
        List<HashMap<String, Object>> oldSamePart = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
            if (part.equals(strVal(e.get("PART"), ""))) oldSamePart.add(e);
        }
        wearEquipOne(userName, equip, companionId);
        for (HashMap<String, Object> e : oldSamePart) unwearEquipOne(userName, e);
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

    // ---- [2026-10-22] 장비 수량(QTY) ----
    // 미착용 일반 장비(전설 아님)는 같은 종류(CLASS/PART/GRADE)끼리 한 행에 QTY로 쌓는다(보관함이 행 수천 개로 불어나던 문제).
    // 착용 중인 행과 전설 행은 항상 1개짜리 행이다. 장착하면 스택에서 1개를 떼어 새 행으로 끼우고, 해제하면 같은 종류 스택에 다시 합친다.
    // 합성/분해는 스택에서 수량만 뺀다. 같은 종류가 아직 여러 행이어도(배포 직후 합치기 스크립트 전) 모든 경로가 행 단위로 맞게 동작한다.
    private int qtyOf(HashMap<String, Object> e) { return Math.max(1, intVal(e.get("QTY"), 1)); }

    private boolean stackable(HashMap<String, Object> e) {
        return e.get("LEGENDARY_ID") == null && intVal(e.get("GRADE"), 1) < 7;
    }

    /** 미착용 일반 장비 n개를 보관함에 넣는다(같은 종류 스택이 있으면 수량만 늘리고, 없으면 n개짜리 행 하나를 만든다). */
    private void addEquipItems(String userName, String clazz, String part, int grade, int n) {
        if (n <= 0) return;
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", userName);
        m.put("class", clazz);
        m.put("part", part);
        m.put("grade", grade);
        m.put("qty", n);
        m.put("equippedCompanionId", null);
        if (grade < 7) {
            if (dao.addEquipStack(m) > 0) return;
            dao.insertEquip(m);
            return;
        }
        m.put("qty", 1);
        for (int i = 0; i < n; i++) dao.insertEquip(m);
    }

    /** 미착용 행에서 n개를 꺼낸다(남으면 수량만 줄이고, 딱 맞으면 행 삭제). 수량이 모자라면(동시 처리 등) 예외로 트랜잭션 전체를 되돌린다. */
    private void takeEquipUnits(int equipId, int n) {
        if (n <= 0) return;
        if (dao.decrementEquipQty(equipId, n) > 0) return;
        if (dao.deleteEquipIfQty(equipId, n) > 0) return;
        throw new IllegalStateException("장비 수량이 바뀌었습니다. 다시 시도해주세요. (" + equipId + ")");
    }

    /** 미착용 행 equip(스택일 수 있음)에서 1개를 companionId에게 장착한다. */
    private void wearEquipOne(String userName, HashMap<String, Object> equip, int companionId) {
        int id = intVal(equip.get("EQUIP_ID"), 0);
        if (qtyOf(equip) > 1) {
            takeEquipUnits(id, 1);
            HashMap<String, Object> m = new HashMap<>();
            m.put("userName", userName);
            m.put("class", strVal(equip.get("CLASS"), ""));
            m.put("part", strVal(equip.get("PART"), ""));
            m.put("grade", intVal(equip.get("GRADE"), 1));
            m.put("equippedCompanionId", companionId);
            m.put("qty", 1);
            dao.insertEquip(m);
            return;
        }
        HashMap<String, Object> wear = new HashMap<>();
        wear.put("equipId", id);
        wear.put("equippedCompanionId", companionId);
        dao.updateEquipEquippedCompanion(wear);
    }

    /** 착용 해제 -- 일반 장비면 같은 종류 미착용 스택에 합친다(스택이 없으면 이 행이 그대로 새 스택). 장착과 함께 쓸 땐 반드시 장착 "뒤에" 부른다
     *  (먼저 해제하면 해제한 장비가 장착하려던 1개짜리 스택에 합쳐져 그 행 전체가 끼워질 수 있다). */
    private void unwearEquipOne(String userName, HashMap<String, Object> equip) {
        int id = intVal(equip.get("EQUIP_ID"), 0);
        HashMap<String, Object> unwear = new HashMap<>();
        unwear.put("equipId", id);
        unwear.put("equippedCompanionId", null);
        dao.updateEquipEquippedCompanion(unwear);
        if (!stackable(equip) || equip.get("CLASS") == null) return;
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", userName);
        m.put("class", strVal(equip.get("CLASS"), ""));
        m.put("part", strVal(equip.get("PART"), ""));
        m.put("grade", intVal(equip.get("GRADE"), 1));
        m.put("qty", qtyOf(equip));
        m.put("excludeId", id);
        if (dao.addEquipStack(m) > 0) dao.deleteEquip(id);
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
                if (intVal(target.get("GRADE"), 1) != GROWTH_GRADE && !weaponAllowedJobs(strVal(e.get("CLASS"), "")).contains(job)) continue; // 성장형은 직업 제한 없음
                if (best != null && intVal(e.get("GRADE"), 0) <= intVal(best.get("GRADE"), 0)) continue;
                if (legendWearBlock(userName, e, companionId) != null) continue; // [2026-10-22] 전설 규칙에 걸리는 후보는 건너뜀
                best = e;
            }
            if (best == null) continue;
            int bestGrade = intVal(best.get("GRADE"), 0);
            if (bestGrade <= curGradeByPart.getOrDefault(part, 0)) continue; // 이미 같거나 더 좋음

            List<HashMap<String, Object>> oldSamePart = new ArrayList<>();
            for (HashMap<String, Object> e : dao.selectEquipByCompanion(companionId)) {
                if (part.equals(strVal(e.get("PART"), ""))) oldSamePart.add(e);
            }
            wearEquipOne(userName, best, companionId); // [2026-10-22] 수량(QTY) -- 장착 먼저, 기존 장비 해제는 뒤에
            for (HashMap<String, Object> e : oldSamePart) unwearEquipOne(userName, e);
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

        unwearEquipOne(userName, found); // [2026-10-22] 일반 장비는 같은 종류 미착용 스택에 합쳐진다
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
                boolean legendBegin = grade == 5 && newLimitBreak >= LIMIT_BREAK_MAX && checkLegendBegin(userName);
                return "🔺 이미 보유한 " + JOB_NAME.get(job) + "(" + name + ")와 중복! (★" + grade + " 선택권 사용)" + NL
                        + "한계돌파+" + newLimitBreak + " 달성! (전체 스탯 +" + Math.round(limitBreakPct(newLimitBreak) * 100) + "%)"
                        + " (남은 ★" + grade + " 선택권 " + (have - 1) + "장)"
                        + (legendBegin ? NL + "🏆 업적 [전설의 시작] 달성! /7성소환 으로 ★7 동료를 소환할 수 있습니다." : "");
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

        addEquipItems(userName, weaponClass, "WEAPON", grade, 1); // [2026-10-22] 같은 종류 스택에 수량으로 쌓는다

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
        int have = 0;
        for (HashMap<String, Object> sm : same) have += qtyOf(sm);
        if (have < 3) return "동일 등급/부위/직업 미착용 장비가 3개 이상 필요합니다. (현재 " + have + "개)";

        // [2026-10-22] 수량(QTY) -- 재료 3개를 스택(들)에서 수량으로 뺀다
        int need = 3;
        for (HashMap<String, Object> sm : same) {
            if (need <= 0) break;
            int take = Math.min(need, qtyOf(sm));
            takeEquipUnits(intVal(sm.get("EQUIP_ID"), 0), take);
            need -= take;
        }
        addEquipItems(userName, clazz, part, grade + 1, 1);

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
    // 메시지("일반사용자에겐 아직 제작부분은 오픈하지 말고")로 한동안 NO_COOLDOWN_YN(기존
    // 관리자/테스트 계정 플래그) 보유 계정만 사용 가능하도록 막아뒀었다(조각 드랍/보유는
    // 이미 일반 유저에게도 보였음).
    // [2026-09-21 후속] "전설제작 오픈해줘(일어난다람쥐/카단은 성공했으나 도륙이냥/달소는
    // 진행불가함)" 요청 -- 일어난다람쥐/카단이 성공했던 건 NO_COOLDOWN_YN='Y'인 관리자/
    // 테스트 계정이었기 때문이고, 도륙이냥/달소는 조각을 다 모으고도 이 게이트에 막혀
    // "🔒 전설제작은 아직 준비 중인 기능입니다"만 보고 있었다. 전체 오픈으로 이 차단을 제거.
    @Override
    public List<HashMap<String, Object>> legendaryRoster() {
        return dao.selectLegendaryMasterList();
    }

    @Override
    @Transactional
    public String craftLegendary(String userName, int legendaryId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
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

        int pity = intVal(p.get("LEGEND_PITY"), 0);
        int chance = Math.min(100, LEGEND_CRAFT_SUCCESS_PCT + pity * LEGEND_PITY_STEP_PCT);
        boolean success = RND.nextInt(100) < chance;
        String itemName = strVal(target.get("ITEM_NAME"), "");
        HashMap<String, Object> pityUp = new HashMap<>();
        pityUp.put("userName", userName);
        pityUp.put("legendPity", success ? 0 : pity + 1);
        dao.updateUserProgress(pityUp);
        p.put("LEGEND_PITY", success ? 0 : pity + 1);
        if (!success) {
            int nextChance = Math.min(100, LEGEND_CRAFT_SUCCESS_PCT + (pity + 1) * LEGEND_PITY_STEP_PCT);
            return "💨 [" + itemName + "] 전설제작 실패... 전설의조각 " + LEGEND_CRAFT_COST + "개를 소모했습니다. (보유 "
                    + remaining + "개) 🍀 연속 실패 보너스로 다음 성공률 " + nextChance + "%";
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
        return disenchantRow(userName, progress, unequipped.get(equipIdx - 1));
    }

    // ---- [2026-10-23] 웹 광산(공용 맵 여러 개, 2D 도트) ----
    // 맵은 S5MineMap(주기마다 시드로 서버가 만든다)을 모든 유저가 같이 쓴다 -- 칸마다 광석 등급이 정해져 있어 좌표를 공유할 수 있다.
    // 화면에는 지형/포탈/NPC만 보내고 광석은 캐 봐야 안다. 한 칸은 유저마다 한 번만 캘 수 있다(TBOT_S5_MINE_DUG, 맵 리셋 때만 초기화).
    // [2026-10-23] "스태미너 대신 곡괭이 내구도, 처음 1000(모든 유저 현재/최대 1000 -- 같은 날 "초기 내구도 200"으로 변경), 마을 대장간 NPC에게 광석을 주면 내구도를 올린다" 요청 --
    //   한 번 캘 때 곡괭이 내구도(TBOT_S5_MINE_USER.PICK_DUR) 1 소모, 시간 회복 없음([2026-10-24] 매일 0시에 최대치까지 가득 -- minePickRefill). 대장장이(평원 마을 NPC)에게 광석을 주면
    //   MINE_PICK_UPGRADE 비율로 곡괭이 강화 -- 최대 내구도(PICK_DUR_MAX)와 현재 내구도(PICK_DUR)가 같은 양만큼 함께 오른다(상한 없음, 별도 수리 없음 --
    //   2026-10-23 "회복은 최대치를 늘리는 것" 확인). 예전 스태미너 컬럼(STAMINA/STAMINA_AT_MS)은 더 이상 읽지 않는다.
    // [2026-10-23] "전체 맵은 2일 주기로 리셋 후 셔플, 리셋 작업이 끝나기 전엔 리셋 중이라 접속 안 된다고 안내" 요청 -- mineGate 참고.
    // 캐릭터 위치(맵 + 좌표)는 광산을 나갈 때(와 이동 중 주기적으로, 캘 때, 포탈을 탈 때) 저장하고, 마지막 저장 위치에서 시간당 갈 수 있는
    // 거리(MINE_MAX_TILES_PER_SEC)를 넘으면 거절하고 서버 위치로 되돌린다(순간이동 방지). 포탈(지역 통로/동굴 입구/나가는 길/지하 문)은
    // 바로 옆 칸에서 들어가면 대상 맵으로 옮기고, 대상 맵이 아직 없으면(잠김) 안내만 한다.
    // 광산상점: 광석을 내고 자동사냥 처치당 PP 보너스(PP_BONUS)를 영구히 올린다 -- 자동사냥 정산 때 처치 수만큼 함께 지급(settleAutoHunt).
    //   [2026-10-23] 처음엔 "시간당"이었으나 "시간당 대신 처치당"으로 변경(같은 수치, 자동사냥 시간당 처치 수(기본 6)만큼 커짐).
    /** [2026-10-24] "대장장이 곡괭이 강화 최대치 제한" -- 최대 내구도 상한. 상한에 닿으면 더 강화하지 않는다(수리는 없음 -- 같은 날 "수리는 없애도 될 것 같아"). */
    static final int MINE_PICK_CAP = 1000;
    private static final int MINE_PICK_MAX = 200; // 곡괭이 기본(초기) 최대 내구도 -- 새로 들어온 유저도 200으로 시작(컬럼 기본값, 2026-10-23 1000 -> 200)
    /** 대장간 곡괭이 강화 {광석 등급, 필요 개수, 최대(+현재) 내구도 증가}. 캔 광석을 전부 강화에 써도 평균적으로 1회 채굴(내구도 1)보다 조금 덜 돌아오게(약 0.85) 잡았다. */
    private static final int[][] MINE_PICK_UPGRADE = { { 1, 50, 10 }, { 2, 20, 10 }, { 3, 10, 10 }, { 4, 5, 10 }, { 5, 1, 5 }, { 6, 1, 20 } };
    private static final double MINE_MAX_TILES_PER_SEC = 10.0; // 화면 이동 속도(약 7칸/초)보다 넉넉하게(2026-10-24 9 -> 10, 요청 지연 감안)
    private static final long MINE_MOVE_WINDOW_MS = 15_000L;
    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
    /** 광산상점 상품: {광석 등급, 필요 개수, 처치당 PP 증가}. 광석 1개 값 = 구리 0.1 / 철 0.3 / 은 1 / 금 3 / 미스릴 10 / 별빛석 40 (PP/자동사냥 처치). */
    private static final int[][] MINE_SHOP = { { 1, 100, 10 }, { 2, 50, 15 }, { 3, 25, 25 }, { 4, 10, 30 }, { 5, 5, 50 }, { 6, 1, 40 } };

    // [2026-10-23] 광산 아이템(TBOT_S5_MINE_ITEM) -- "어두운 동굴 + 광부 투구로 시야, 광산 레벨이 오르면 더 높은 투구 단계, 모자라면 못 움직임" 요청,
    // "용왕반지 등 미리 만들어 두고 노출만 안 하기, 있으면 바다 던전 입장" 요청.
    //  광부 투구(MINER_HELMET, 단계): 1단계 = 철광석 100개, 2단계 = 은광석 100개, 3단계 = 금광석 100개, 4단계 = 미스릴 100개, 5단계 = 별빛석 100개.
    //   광산 레벨 L(맵 파일 LEVEL) 맵은 투구 L-1단계 이상이어야 들어가고 움직이고 캘 수 있다. 시야 = 투구 단계 - (L-1): 0 아주 어둡게 / 1 잘 보임 / 2 이상 전체.
    //   지금은 광산 레벨 1(동굴 1층)뿐이라 상점에서 2단계까지만 판다(MINE_HELMET_OPEN_MAX) -- 아래층을 열 때 올린다.
    //  용왕반지(DRAGON_RING): 숨김(상점/화면에 안 나오고 아직 얻는 곳 없음). 맵 파일에 "REQUIRE DRAGON_RING"이 있는 맵(바다 던전)은 이게 있어야 들어간다.
    //   [2026-10-24] "물속 진입 대신 물 위를 걷는 옵션" -- 선장 퀘스트로 만들고, 가지고 있으면 물(~) 칸을 걸을 수 있다(mineWalk). 아래 광산 장비 참고.
    static final String MINE_ITEM_HELMET = "MINER_HELMET";
    static final String MINE_ITEM_DRAGON_RING = "DRAGON_RING";
    private static final Map<String, String> MINE_ITEM_NAMES = new LinkedHashMap<>();
    static {
        MINE_ITEM_NAMES.put(MINE_ITEM_HELMET, "광부 투구");
        MINE_ITEM_NAMES.put(MINE_ITEM_DRAGON_RING, "용왕의 반지");
    }
    /** 투구 단계별 업그레이드 비용 {광석 등급, 개수}, 인덱스 = 목표 단계 - 1. */
    //  [2026-10-25] 한때 2단계 은 250 / 3단계 금 250으로 올렸다가 같은 날 "이미 단계업한 사람이 있으면 그렇게 올리면 안 된다" -- 3단계까지 산 유저가 있어 원래 값으로 되돌렸다.
    private static final int[][] MINE_HELMET_COST = { { 2, 100 }, { 3, 100 }, { 4, 100 }, { 5, 100 }, { 6, 100 } };
    private static final int MINE_HELMET_OPEN_MAX = 4; // [2026-10-24] 동굴 3층(레벨 3)이 열려 3단계(금광석 100개)까지, [2026-10-25] 4단계는 왕도 대장장이(3층도 환하게)
    private static final int MINE_HELMET_FIELD_MAX = 3; // 마을 대장장이는 3단계까지
    /** [2026-10-25] 4단계 추가 재료("고급템은 칠요석"). */
    private static final String[][] MINE_HELMET_EXTRA4 = { { S5MineMap.SEVEN_STONE_CODE, "2" } };

    private Map<String, Integer> mineItems(String userName) {
        Map<String, Integer> out = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectMineItems(userName)) out.put(strVal(r.get("ITEM_CODE"), ""), intVal(r.get("ITEM_LEVEL"), 0));
        return out;
    }

    private static int mineHelmetNeed(S5MineMap m) {
        return Math.max(0, m.level() - 1);
    }

    /** [2026-10-24] 구역별 권장 레벨(광산 전투 레벨) -- 처음엔 "입장 레벨 제한"으로 막았다가 같은 날 "레벨 모자라도 갈 수 있게"로 바뀌어
     *  막지 않고 안내만 한다(통로/배로 들어갈 때 경고 문구, 전체 지도에 "권장 Lv."). 그 구역 가장 약한 몬스터의 적정 레벨(1 + 4(단계-1))보다 4 낮게:
     *  평원/요일 던전 1, 바닷가 5, 북쪽 사막/광산 동굴 9, 남쪽 섬 13, 동쪽 성곽 17, 북쪽 끝 설원 21. */
    static final Map<String, Integer> MINE_MIN_LV = new LinkedHashMap<>();
    static {
        MINE_MIN_LV.put(S5MineMap.FIELD, 1);
        MINE_MIN_LV.put(S5MineMap.DUNGEON, 1);
        MINE_MIN_LV.put(S5MineMap.BEACH, 5);
        MINE_MIN_LV.put(S5MineMap.DESERT, 9);
        MINE_MIN_LV.put(S5MineMap.CAVE1, 9);
        MINE_MIN_LV.put(S5MineMap.ISLAND, 13);
        MINE_MIN_LV.put(S5MineMap.CASTLE, 17);
        MINE_MIN_LV.put(S5MineMap.NORTH, 21);
        // [2026-10-24] 모래폭풍 너머 초원(몬스터 6~7단계) -- 왕도도 같은 길이라 17. [2026-10-27] 초원 9~11단계로 강해져 33, 왕도도 33.
        MINE_MIN_LV.put(S5MineMap.STEPPE, 33);
        MINE_MIN_LV.put(S5MineMap.CITY, 33);
        MINE_MIN_LV.put(S5MineMap.ROYALROAD, 45); // [2026-10-27] 왕도 가는 길(11~13단계) -- "레벨 제한 조금 높게"
        MINE_MIN_LV.put(S5MineMap.RUINS, 80); // [2026-10-28] 성당 옛터 -- 타락한 대주교("80레벨 이상 권장")
        MINE_MIN_LV.put(S5MineMap.CAVE2, 21); // [2026-10-24] 동굴 2층(6~7단계), 3층(8~9단계 + 보스), 바닷길(4~5단계)
        MINE_MIN_LV.put(S5MineMap.CAVE3, 25);
        MINE_MIN_LV.put(S5MineMap.SEAROAD, 13);
        MINE_MIN_LV.put(S5MineMap.SNOW1, 33); // [2026-10-26] 설산 1층(10~11단계), 2층(11~12), 3층(12~13), 설산 마을
        MINE_MIN_LV.put(S5MineMap.SNOW2, 37);
        MINE_MIN_LV.put(S5MineMap.SNOW3, 41);
        MINE_MIN_LV.put(S5MineMap.SNOWTOWN, 41);
    }

    static int mineMinLv(S5MineMap m) {
        return MINE_MIN_LV.getOrDefault(m.id, 1);
    }

    /** 그 맵에 들어가거나 움직일 수 없는 이유(없으면 null). lv = 광산 전투 레벨(권장 레벨은 막지 않아 지금은 안 쓴다). */
    private String mineMapBlock(S5MineMap m, Map<String, Integer> items, int lv) {
        int need = mineHelmetNeed(m), have = items.getOrDefault(MINE_ITEM_HELMET, 0);
        if (have < need) return "⛑️ " + m.name + "은(는) 광부 투구 " + need + "단계 이상이 있어야 움직일 수 있어요. (지금 " + have + "단계, 평원 마을 대장장이에게서 업그레이드)";
        if (m.requireItem() != null && items.getOrDefault(m.requireItem(), 0) < 1) {
            return "🔒 " + m.name + "에 들어가려면 [" + MINE_ITEM_NAMES.getOrDefault(m.requireItem(), m.requireItem()) + "]이(가) 필요해요.";
        }
        return null;
    }

    // ---- [2026-10-23] 2일 주기 맵 리셋 ----
    // 리셋 시각은 S5MineMap.RESET_ANCHOR_MS(2026-10-25 05:00 KST)부터 2일마다. 주기가 바뀌면(매일 05:00 스케줄 또는 그 뒤 첫 광산 요청)
    // 백그라운드로 리셋한다: 캔 칸 전부 삭제 -> 모든 유저 위치를 평원 마을로 -> 새 시드로 맵 생성 -> TBOT_S5_CONFIG.MINE_RESET_CYCLE 기록.
    // 그 사이 광산 요청은 "리셋 중" 안내만 받는다(작업이 끝나면 바로 다시 들어갈 수 있음). 서버가 꺼져 있다 켜져도 DB의 주기를 보고 이어서 한다.
    private static final java.util.concurrent.atomic.AtomicBoolean MINE_RESETTING = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile int mineAppliedCycle = Integer.MIN_VALUE; // 아직 DB에서 안 읽음
    private static final String MINE_CYCLE_KEY = "MINE_RESET_CYCLE";

    /** 지금 주기의 맵이 준비됐으면 null, 리셋 중이면 안내 응답. 주기가 바뀌었으면 리셋을 시작한다. */
    private HashMap<String, Object> mineGate() {
        minePickRefill(); // [2026-10-24] 그날 아직 0시 내구도 회복을 안 했으면(서버가 0시에 꺼져 있었던 경우)
        mineRescaleOnce(); // [2026-10-28] 다운스케일 경험치 1회 변환
        int want = S5MineMap.cycleAt(System.currentTimeMillis());
        if (!MINE_RESETTING.get() && mineAppliedCycle == want) return null;
        if (!MINE_RESETTING.get()) {
            if (mineAppliedCycle == Integer.MIN_VALUE) mineLoadCycle(want);
            if (mineAppliedCycle == want) {
                S5MineMap.setActiveCycle(want);
                return null;
            }
            startMineReset(want);
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        out.put("resetting", true);
        out.put("error", "🔄 광산 맵을 새로 섞는 중이에요(2일마다 05:00 리셋). 잠시 후 다시 들어와 주세요.");
        out.put("message", out.get("error"));
        return out;
    }

    /** DB에 기록된 마지막 리셋 주기. 처음(기록 없음)이고 아직 첫 리셋 전(주기 0)이면 지금 맵(예전 맵 파일과 같음)을 그대로 주기 0으로 본다. */
    private void mineLoadCycle(int want) {
        try {
            String v = dao.selectConfigValue(MINE_CYCLE_KEY);
            if (v != null && !v.trim().isEmpty()) {
                mineAppliedCycle = Integer.parseInt(v.trim());
            } else if (want == 0) {
                dao.upsertConfigValue(MINE_CYCLE_KEY, "0", "web mine map cycle applied (2-day reset, set by app)");
                mineAppliedCycle = 0;
            } else {
                mineAppliedCycle = -1; // 기록 없이 첫 리셋 시각이 지났다 -- 리셋부터
            }
        } catch (Exception e) {
            System.out.println("[광산] 리셋 주기 읽기 실패: " + e.getMessage());
        }
    }

    private void startMineReset(final int want) {
        if (!MINE_RESETTING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                int dug = dao.deleteAllMineDug();
                int users = dao.resetAllMinePos();
                S5MineMap.setActiveCycle(want);
                MINE_MOB_STATE.clear(); // [2026-10-24] 새 맵 -- 몬스터 상태도 처음부터
                dao.upsertConfigValue(MINE_CYCLE_KEY, String.valueOf(want), "web mine map cycle applied (2-day reset, set by app)");
                mineAppliedCycle = want;
                System.out.println("[광산] 맵 리셋 완료: 주기 " + want + ", 캔 칸 " + dug + "건 삭제, 유저 " + users + "명 마을로, "
                        + (System.currentTimeMillis() - t0) + "ms");
            } catch (Exception e) {
                System.out.println("[광산] 맵 리셋 실패(다음 요청/스케줄에서 다시 시도): " + e.getMessage());
            } finally {
                MINE_RESETTING.set(false);
            }
        }, "s5-mine-reset");
        t.setDaemon(true);
        t.start();
    }

    /** [2026-10-24] 매일 0시(KST) -- 곡괭이 내구도 가득. @Scheduled라 BotS5Service 인터페이스에도 선언돼 있어야 한다. */
    @Override
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void minePickRefillTick() {
        try {
            minePickRefill();
        } catch (Exception e) {
            System.out.println("[광산] 0시 곡괭이 내구도 회복 실패: " + e.getMessage());
        }
    }

    // ---- [2026-10-24] "0시에 곡괭이 내구도 풀 회복" -- 매일 0시(KST) 모든 유저 현재 내구도 = 최대 내구도.
    //  0시 스케줄(minePickRefillTick)과, 서버가 0시에 꺼져 있었을 때를 위해 그날 첫 광산 요청(mineGate)에서도 확인한다.
    //  한 날짜에 한 번만: TBOT_S5_CONFIG.MINE_PICK_REFILL_DAY = yyyyMMdd.
    private static final String MINE_PICK_REFILL_KEY = "MINE_PICK_REFILL_DAY";
    private static volatile String minePickRefilledDay = null;

    private synchronized void minePickRefill() {
        String today = java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        if (today.equals(minePickRefilledDay)) return;
        try {
            String done = dao.selectConfigValue(MINE_PICK_REFILL_KEY);
            if (done == null || !today.equals(done.trim())) {
                int n = dao.refillAllMinePick();
                dao.upsertConfigValue(MINE_PICK_REFILL_KEY, today, "web mine pickaxe durability refilled at 00:00 KST (yyyyMMdd)");
                System.out.println("[광산] 0시 곡괭이 내구도 회복: " + n + "명");
            }
            minePickRefilledDay = today;
        } catch (Exception e) {
            System.out.println("[광산] 곡괭이 내구도 회복 확인 실패: " + e.getMessage());
        }
    }

    /** 매일 05:00 -- 리셋 날이면 리셋을 시작한다(요청이 없어도 제시간에). @Scheduled라 BotS5Service 인터페이스에도 선언돼 있어야 한다. */
    @Override
    @Scheduled(cron = "0 0 5 * * *")
    public void mineResetTick() {
        try {
            mineGate();
        } catch (Exception e) {
            System.out.println("[광산] 리셋 확인 실패: " + e.getMessage());
        }
    }

    private static boolean mineStaleVersion(S5MineMap map, String ver) {
        return ver != null && !ver.isEmpty() && !ver.equals(map.version());
    }

    private static long longOf(Object o, long def) {
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    private HashMap<String, Object> mineRow(String userName, boolean forUpdate) {
        HashMap<String, Object> row = forUpdate ? dao.selectMineUserForUpdate(userName) : dao.selectMineUser(userName);
        if (row != null) return row;
        try {
            dao.insertMineUserIfAbsent(userName, 0, System.currentTimeMillis()); // 예전 스태미너 컬럼은 0, 곡괭이 내구도는 컬럼 기본값(1000)
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 동시에 처음 들어온 요청이 먼저 만들었다
        }
        return forUpdate ? dao.selectMineUserForUpdate(userName) : dao.selectMineUser(userName);
    }

    private static S5MineMap mineMapOf(HashMap<String, Object> row) {
        return S5MineMap.get(row == null ? null : (String) row.get("MAP_ID"));
    }

    /** 저장된 위치(없거나 지금 맵에서 못 서는 칸이면 그 맵 시작점). water = 용왕의 반지(물 위 걷기). */
    private int[] minePos(HashMap<String, Object> row, S5MineMap map, boolean water) {
        if (row.get("POS_X") != null && row.get("POS_Y") != null) {
            int x = intVal(row.get("POS_X"), map.spawnX), y = intVal(row.get("POS_Y"), map.spawnY);
            if (mineWalk(map, x, y, water)) return new int[] { x, y };
        }
        return new int[] { map.spawnX, map.spawnY };
    }

    /** [2026-10-24] 설 수 있는 칸 -- 용왕의 반지가 있으면 물(~) 칸도. */
    private static boolean mineWalk(S5MineMap map, int x, int y, boolean water) {
        return map.walkable(x, y) || (water && map.inside(x, y) && map.terrainAt(x, y) == '~');
    }

    /** stepMs = 화면 한 칸 이동 시간(신속의 장화로 빨라지면 허용 거리도 그만큼 늘린다). */
    private static boolean mineMoveOk(int[] from, long fromAt, int x, int y, long now, int stepMs) {
        int dist = Math.abs(x - from[0]) + Math.abs(y - from[1]);
        // 쉬는 시간은 최대 MINE_MOVE_WINDOW_MS까지만 친다 -- 오래 가만히 있다가 먼 곳으로 한 번에 옮기는 것 방지(화면은 이동 중 몇 초마다, 캘 때마다 위치를 저장)
        double perSec = MINE_MAX_TILES_PER_SEC * MINE_STEP_MS[0] / (double) stepMs;
        double allowed = 6 + Math.min(MINE_MOVE_WINDOW_MS, Math.max(0, now - fromAt)) / 1000.0 * perSec;
        return dist <= allowed;
    }

    private List<HashMap<String, Object>> mineOreList(String userName) {
        Map<String, Integer> have = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectUserOres(userName)) have.put(strVal(r.get("ORE_CODE"), ""), intVal(r.get("QTY"), 0));
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (int g = S5MineMap.ORE_CODES.length; g >= 1; g--) {
            HashMap<String, Object> o = new HashMap<>();
            o.put("grade", g);
            o.put("code", S5MineMap.ORE_CODES[g - 1]);
            o.put("name", S5MineMap.ORE_NAMES[g - 1]);
            o.put("qty", have.getOrDefault(S5MineMap.ORE_CODES[g - 1], 0));
            out.add(o);
        }
        // [2026-10-23] 요일 던전의 돌(월요석~일요석) -- 등급 값은 11~17(일반 광석 1~6과 구분, 화면 아이콘/이름용)
        for (int i = 0; i < S5MineMap.DAY_STONE_CODES.length; i++) {
            HashMap<String, Object> o = new HashMap<>();
            o.put("grade", S5MineMap.DAY_STONE_GRADE_BASE + i);
            o.put("code", S5MineMap.DAY_STONE_CODES[i]);
            o.put("name", S5MineMap.DAY_STONE_NAMES[i]);
            o.put("qty", have.getOrDefault(S5MineMap.DAY_STONE_CODES[i], 0));
            out.add(o);
        }
        HashMap<String, Object> seven = new HashMap<>(); // [2026-10-24] 칠요석(요일석 7종 합성) -- 등급 값 18
        seven.put("grade", S5MineMap.SEVEN_STONE_GRADE);
        seven.put("code", S5MineMap.SEVEN_STONE_CODE);
        seven.put("name", S5MineMap.SEVEN_STONE_NAME);
        seven.put("qty", have.getOrDefault(S5MineMap.SEVEN_STONE_CODE, 0));
        out.add(seven);
        // [2026-10-24] 보석(재스퍼~다이아몬드) -- 등급 값 21~29
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) {
            HashMap<String, Object> o = new HashMap<>();
            o.put("grade", S5MineMap.GEM_GRADE_BASE + i);
            o.put("code", S5MineMap.GEM_CODES[i]);
            o.put("name", S5MineMap.GEM_NAMES[i]);
            o.put("qty", have.getOrDefault(S5MineMap.GEM_CODES[i], 0));
            out.add(o);
        }
        // [2026-10-28] 특수 재료 -- 빙석(19), 성유물 조각(30), 대주교의 인장(31)
        String[] sc = { S5MineMap.ICE_STONE_CODE, S5MineMap.SEAL_CODE };
        for (String c : sc) {
            HashMap<String, Object> o = new HashMap<>();
            o.put("grade", mineCodeGrade(c));
            o.put("code", c);
            o.put("name", mineCodeName(c));
            o.put("qty", have.getOrDefault(c, 0));
            out.add(o);
        }
        return out;
    }

    /** [2026-10-23] 캔 칸 기록의 맵 키 -- 요일 던전은 날마다 새로 캘 수 있게 날짜를 붙인다(dungeon-yyyyMMdd), 그 외 맵은 맵 ID. */
    private static String mineDugKey(S5MineMap map, long now) {
        return S5MineMap.DUNGEON.equals(map.id) ? S5MineMap.DUNGEON + "-" + S5MineMap.dayKey(now) : map.id;
    }

    /** 캔 칸 등급 값 -> 이름(1~6 일반 광석, 11~17 요일석, 18 칠요석, 21~29 보석 -- 바닷길에서 캔 칸). */
    private static String mineGradeName(int g) {
        if (g == S5MineMap.SEVEN_STONE_GRADE) return S5MineMap.SEVEN_STONE_NAME;
        if (g >= S5MineMap.GEM_GRADE_BASE && g < S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_NAMES.length) return S5MineMap.GEM_NAMES[g - S5MineMap.GEM_GRADE_BASE];
        if (g >= 1 && g <= S5MineMap.ORE_NAMES.length) return S5MineMap.ORE_NAMES[g - 1];
        int d = g - S5MineMap.DAY_STONE_GRADE_BASE;
        return d >= 0 && d < S5MineMap.DAY_STONE_NAMES.length ? S5MineMap.DAY_STONE_NAMES[d] : "";
    }

    private int minePickMax(HashMap<String, Object> row) {
        return intVal(row.get("PICK_DUR_MAX"), MINE_PICK_MAX);
    }

    private int minePickDur(HashMap<String, Object> row) {
        return Math.max(0, intVal(row.get("PICK_DUR"), MINE_PICK_MAX));
    }

    private static void putMinePick(HashMap<String, Object> out, int dur, int max) {
        out.put("durability", dur);
        out.put("durabilityMax", max);
    }

    /** 대장간 곡괭이 강화 표(화면용). */
    private static List<HashMap<String, Object>> minePickUpgradeList() {
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (int[] r : MINE_PICK_UPGRADE) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("grade", r[0]);
            m.put("oreName", S5MineMap.ORE_NAMES[r[0] - 1]);
            m.put("need", r[1]);
            m.put("gain", r[2]);
            out.add(m);
        }
        return out;
    }

    @Override
    public HashMap<String, Object> mineMap(String mapId) {
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        S5MineMap map = S5MineMap.get(mapId);
        HashMap<String, Object> out = new HashMap<>();
        out.put("id", map.id);
        out.put("name", map.name);
        out.put("dark", map.dark);
        out.put("wind", map.wind()); // [2026-10-23] 모래바람(사막) -- 화면이 가만히 있어도 좌우로 민다
        out.put("underwater", map.underwater()); // [2026-10-24] 바닷길(화면이 푸르게 + 거품)
        List<HashMap<String, Object>> npcs = new ArrayList<>();
        for (S5MineMap.Npc n : map.npcs()) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("x", n.x);
            m.put("y", n.y);
            m.put("kind", n.kind);
            m.put("name", n.name);
            m.put("line", n.line);
            if (n.ground != 0) m.put("ground", String.valueOf(n.ground)); // [2026-10-25] NPC 칸 바닥
            npcs.add(m);
        }
        out.put("npcs", npcs);
        out.put("level", map.level());
        out.put("helmetNeed", mineHelmetNeed(map));
        out.put("w", map.w);
        out.put("h", map.h);
        out.put("spawnX", map.spawnX);
        out.put("spawnY", map.spawnY);
        out.put("version", map.version());
        out.put("rows", map.terrainRows());
        List<HashMap<String, Object>> portals = new ArrayList<>();
        for (S5MineMap.Portal p : map.portals) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("x", p.x);
            m.put("y", p.y);
            m.put("kind", String.valueOf(p.kind));
            m.put("label", p.label);
            m.put("locked", p.locked());
            portals.add(m);
        }
        out.put("portals", portals);
        // [2026-10-24] 몬스터(집 칸 + 돌아다니는 칸, 위치는 시각으로 화면이 계산) + 종류 표 + 배 항로
        List<HashMap<String, Object>> mobs = new ArrayList<>();
        for (S5MineMap.Mob mb : map.mobs()) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", mb.id);
            m.put("k", mb.type);
            m.put("c", mb.cells);
            if (S5MineMobs.TYPES.get(mb.type).minion) m.put("d", 1); // [2026-10-28] 대주교 수하 -- 불려 나오기 전엔 없음
            mobs.add(m);
        }
        out.put("mobs", mobs);
        out.put("mobTypes", mineMobTypes());
        out.put("mobSlotMs", S5MineMobs.SLOT_MS);
        if (S5MineMap.SHIP_PORTS.contains(map.id)) {
            List<HashMap<String, Object>> ship = new ArrayList<>();
            for (String port : S5MineMap.SHIP_PORTS) {
                if (port.equals(map.id)) continue;
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", port);
                m.put("name", S5MineMap.nameOf(port));
                ship.add(m);
            }
            out.put("shipTo", ship);
        }
        boolean hasTele = false;
        for (S5MineMap.Portal pt : map.portals) if (S5MineMap.TELE.equals(pt.target)) hasTele = true;
        if (hasTele) { // [2026-10-24] 왕도 텔레포트 게이트 목적지 + 비용
            List<HashMap<String, Object>> tl = new ArrayList<>();
            for (String d : S5MineMap.TELE_DESTS) {
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", d);
                m.put("name", S5MineMap.nameOf(d));
                tl.add(m);
            }
            out.put("teleTo", tl);
            out.put("teleGold", MINE_TELE_GOLD);
        }
        if (map.guide() != null) { // [2026-10-25] 이동 안내원 목적지(왕도는 dev -- 화면이 관리자에게만 보여 주고 서버도 관리자만)
            List<HashMap<String, Object>> gl = new ArrayList<>();
            for (String d : S5MineMap.GUIDE_MAPS) {
                if (d.equals(map.id)) continue;
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", d);
                m.put("name", S5MineMap.townName(d)); // [2026-10-25] 마을 이름(설원 = 설원 마을)
                if (S5MineMap.SNOWTOWN.equals(d) || S5MineMap.CITY.equals(d)) m.put("needVisit", true); // [2026-10-26] 한 번 걸어서 가 본 뒤에만(화면도 그때만), [2026-10-27] 왕도도
                gl.add(m);
            }
            out.put("guideTo", gl);
        }
        // [2026-10-26] 한기 단계와 안전지대(모닥불) {x, y, 반지름} -- 화면이 모닥불을 그리고, 한기 맵이면 1초마다 mineColdTick
        out.put("cold", map.cold());
        out.put("safe", map.safeZones());
        out.put("citizens", map.citizens()); // [2026-10-27] 왕도 주민이 오가는 길(말풍선만)
        out.put("raid", S5MineMap.RUINS.equals(map.id)); // [2026-10-28] 성당 옛터 -- 화면이 1초마다 환경 턴(대주교 마법)
        return out;
    }

    private static List<HashMap<String, Object>> mineMobTypes() {
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (S5MineMobs.Type ty : S5MineMobs.TYPES) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("name", ty.name);
            m.put("tier", ty.tier);
            m.put("kind", ty.kind);
            m.put("color", ty.color);
            m.put("hp", ty.hp());
            m.put("boss", ty.boss);
            if (ty.stay) m.put("stay", true); // [2026-10-28] 대주교 -- 제자리, 4칸 안에서 선공
            out.add(m);
        }
        return out;
    }

    // ---- [2026-10-24] 광산 전투(바람의나라식: 몬스터 바로 옆에서 공격, 몬스터 선공 없음 -- 맞으면 그 자리에 멈춰 반격) ----
    // "광산 전용 레벨, 몬스터 보상은 광석/보석 드랍"(+ 레벨을 올릴 경험치). 몬스터 체력/죽음/멈춤은 서버 메모리(MINE_MOB_STATE, 맵 리셋 때 비움),
    // 유저 레벨/경험치/체력은 TBOT_S5_MINE_USER(M_LV, M_EXP, M_HP, M_HP_AT_MS, M_KILLS -- S5_MINE_COMBAT.sql).
    //  레벨 L: 최대 체력 40 x 1.09^(L-1), 공격력 6 x 1.09^(L-1), 다음 레벨까지 경험치 15 x 1.12^(L-1). 체력은 60초에 다 찬다. 쓰러지면 평원 마을에서 깨어난다(잃는 것 없음).
    //  [2026-10-28] "체력 최대치/한 번에 얻는 경험치를 낮추고 전체적 다운스케일" -- 예전 1.18 / 1.18 / 1.32에서 줄였다(몬스터도 S5MineMobs에서 같은 비율로).
    //  저장된 레벨 안 경험치는 처음 한 번 새 필요치 비율로 바꾼다(mineRescaleOnce). 레벨 상한 60 -> 99(숫자가 작아져 M_EXP NUMBER(12) 걱정 없음).
    private static final int MINE_LV_MAX = 99;
    private static final long MINE_HP_FULL_MS = 60_000L;
    private static final long MINE_ATTACK_GAP_MS = 380L;
    private static final long MINE_MOB_FREEZE_MS = 8_000L;
    private static final long MINE_MOB_HEAL_MS = 30_000L;

    private static final class MobState {
        int hp;
        long deadUntil, frozenUntil, lastHitAt;
        int fx, fy;
        String target;   // [2026-10-25] 쫓아가는 유저(인식 중)
        long lastMoveAt; // 마지막으로 한 칸 쫓아온 시각
        MobState(int hp) { this.hp = hp; }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, MobState> MINE_MOB_STATE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_LAST_ATTACK = new java.util.concurrent.ConcurrentHashMap<>();

    private static String mineMobKey(S5MineMap map, int id) {
        return map.version() + ":" + id;
    }

    static int mineHpMax(int lv) { return (int) Math.round(40 * Math.pow(1.09, lv - 1)); }
    static int mineAtk(int lv) { return (int) Math.round(6 * Math.pow(1.09, lv - 1)); }
    static long mineExpNeed(int lv) { return Math.round(15 * Math.pow(1.12, lv - 1)); }

    // [2026-10-28] 다운스케일 1회 변환 -- 레벨 안 경험치를 (새 필요치 / 예전 필요치) 비율로(바꾸지 않으면 다음 처치 때 레벨이 몇십씩 오른다).
    //  배포 뒤 첫 광산 요청(mineGate)에서 한 번만 DB로 바꾸고 설정값 MINE_SCALE_VER = 2를 남긴다(서버를 다시 켜도 다시 안 함).
    private static final String MINE_SCALE_KEY = "MINE_SCALE_VER";
    private static volatile boolean mineScaleDone = false;

    private void mineRescaleOnce() {
        if (mineScaleDone) return;
        synchronized (BotS5ServiceImpl.class) {
            if (mineScaleDone) return;
            try {
                if (!"2".equals(dao.selectConfigValue(MINE_SCALE_KEY))) {
                    int n = dao.rescaleMineExp();
                    dao.upsertConfigValue(MINE_SCALE_KEY, "2", "web mine stat downscale (exp in level x 1.12^(L-1) / 1.32^(L-1)) done");
                    System.out.println("[광산] 다운스케일 경험치 변환 " + n + "명");
                }
                mineScaleDone = true;
            } catch (Exception e) {
                System.out.println("[광산] 다운스케일 변환 실패: " + e.getMessage());
            }
        }
    }

    private int mineLv(HashMap<String, Object> row) {
        return Math.max(1, Math.min(MINE_LV_MAX, intVal(row.get("M_LV"), 1)));
    }

    // [2026-10-25] "몬스터와 전투 시 최대 체력 10%씩 잠겨 최대 50%까지, 레벨업하거나 대도시 성당 신부에게 치료받으면 정상으로" --
    //  HP_LOCK(0~5), 최대 체력 = 원래 x (10 - 잠금) / 10. 처음엔 새 전투마다 +1이었으나 [2026-10-26] "죽으면 처리되어야 하는데 전투 때마다 깎인다" --
    //  몬스터에게 쓰러질 때만 +1.
    static final String MINE_ITEM_HP_LOCK = "HP_LOCK";
    private static final int MINE_HP_LOCK_MAX = 5;

    static int mineHpCap(int lv, int lock) { return Math.max(1, mineHpMax(lv) * (10 - Math.max(0, Math.min(MINE_HP_LOCK_MAX, lock))) / 10); }

    /** 쓰러진 뒤 붙이는 안내(잠금이 늘었을 때만). */
    private static String mineLockText(HashMap<String, Object> out) {
        Object v = out.get("hpLockUp");
        return v == null ? "" : " 🔒 최대 체력이 잠겼어요(지금 -" + v + "%). 레벨업하거나 왕도 성당 신부에게 치료받으면 풀려요.";
    }

    /** 쓰러짐 -- 최대 체력 10% 더 잠금(최대 50%). 바뀐 잠금 단계를 돌려준다. */
    private int mineLockUp(String userName, int lock, HashMap<String, Object> out) {
        if (lock >= MINE_HP_LOCK_MAX) return lock;
        lock++;
        dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, lock);
        out.put("hpLockUp", lock * 10);
        return lock;
    }

    /** [2026-10-26] "전투 중엔 체력 회복이 안 돼야 하는데 되는 것 같다" -- 몬스터를 쓰러뜨려 인식이 끝나면 곧바로 회복이 다시 시작돼서
     *  다음 몬스터로 가는 동안(과 한 방에 잡는 사냥 내내) 체력이 찼다. 체력을 저장하는 일(때림/맞음/쓰러뜨림/인식 해제)이 마지막으로 있은 뒤
     *  이만큼 지나야 회복이 시작된다. */
    private static final long MINE_REGEN_DELAY_MS = 10_000L;

    /** 지금 체력(저장된 체력 + 저장 시각 + MINE_REGEN_DELAY_MS부터 회복). M_HP가 없으면 가득. lock = 최대 체력 잠금 단계. */
    private int mineHpNow(HashMap<String, Object> row, long now, int lock) {
        int max = mineHpCap(mineLv(row), lock);
        if (row.get("M_HP") == null) return max;
        int hp = intVal(row.get("M_HP"), max);
        if (mineEngaged(strVal(row.get("USER_NAME"), null), now)) return Math.min(max, hp); // [2026-10-25] 몬스터에게 인식돼 있으면 회복 없음
        long from = longOf(row.get("M_HP_AT_MS"), now) + MINE_REGEN_DELAY_MS;
        return (int) Math.min(max, hp + Math.max(0, now - from) * max / MINE_HP_FULL_MS);
    }

    private static HashMap<String, Object> mineCombatInfo(int lv, long exp, int hp, long now, boolean fight, int lock) {
        HashMap<String, Object> c = mineCombatInfo(lv, exp, hp, now);
        c.put("regenFrom", now + MINE_REGEN_DELAY_MS); // [2026-10-26] 화면도 이때부터 체력을 올린다(지금 저장했으니 지금 + 대기)
        c.put("fight", fight); // [2026-10-25] 인식 중(화면도 체력을 안 올리고 1초마다 전투 턴을 부른다)
        c.put("hpMax", mineHpCap(lv, lock)); // [2026-10-25] 잠긴 만큼 뺀 최대 체력
        c.put("hpBase", mineHpMax(lv));
        c.put("hpLock", Math.min(MINE_HP_LOCK_MAX, lock) * 10);
        return c;
    }

    private static HashMap<String, Object> mineCombatInfo(int lv, long exp, int hp, long now) {
        HashMap<String, Object> c = new HashMap<>();
        c.put("lv", lv);
        c.put("exp", exp);
        c.put("expNeed", mineExpNeed(lv));
        c.put("hp", hp);
        c.put("hpMax", mineHpMax(lv));
        c.put("atk", mineAtk(lv));
        c.put("at", now);
        c.put("fullMs", MINE_HP_FULL_MS);
        return c;
    }

    /** 몬스터가 지금 있을 수 있는 칸들(멈춰 있으면 그 칸, 아니면 앞/지금/다음 시간 칸 -- 화면과 서버 시계 차이 허용). */
    private static int[] mineMobCells(S5MineMap.Mob mob, MobState s, long now, S5MineMap map) {
        if (s != null && s.frozenUntil > now) return new int[] { s.fy * map.w + s.fx };
        return new int[] { S5MineMobs.cellAt(mob, now - S5MineMobs.SLOT_MS), S5MineMobs.cellAt(mob, now), S5MineMobs.cellAt(mob, now + S5MineMobs.SLOT_MS) };
    }

    /** 화면이 1.5초마다 묻는 맵의 몬스터 상태(체력이 줄었거나 쓰러졌거나 멈춘 것만): [id, hp, deadUntil, fx, fy, frozenUntil]. 오래 안 맞은 몬스터는 다 회복(목록에서 뺀다). */
    @Override
    public HashMap<String, Object> mineMobs(String mapId) {
        HashMap<String, Object> out = new HashMap<>();
        long now = System.currentTimeMillis();
        out.put("now", now);
        if (mineGate() != null) { out.put("mobs", Collections.emptyList()); return out; }
        S5MineMap map = S5MineMap.get(mapId);
        String prefix = map.version() + ":";
        List<long[]> list = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, MobState> e : MINE_MOB_STATE.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            MobState s = e.getValue();
            long id = Long.parseLong(e.getKey().substring(prefix.length()));
            boolean minion = id < map.mobs().size() && S5MineMobs.TYPES.get(map.mobs().get((int) id).type).minion;
            synchronized (s) {
                if (minion && s.deadUntil <= now && s.frozenUntil <= now) s.deadUntil = Long.MAX_VALUE / 2; // [2026-10-28] 아무도 안 싸우면 수하는 물러난다
                if (!minion && s.deadUntil <= now && s.frozenUntil <= now && now - s.lastHitAt > MINE_MOB_HEAL_MS) { MINE_MOB_STATE.remove(e.getKey(), s); continue; }
                list.add(new long[] { id, s.hp, s.deadUntil, s.fx, s.fy, s.frozenUntil });
                seen.add(id);
            }
        }
        // [2026-10-28] 대주교 수하는 불러내기 전엔 없다(쓰러진 것으로 보낸다)
        for (S5MineMap.Mob mb : map.mobs()) if (S5MineMobs.TYPES.get(mb.type).minion && !seen.contains((long) mb.id)) list.add(new long[] { mb.id, 0, Long.MAX_VALUE / 2, mb.x, mb.y, 0 });
        if (!map.mobs().isEmpty() && mineBossDead(map.mobs().get(map.mobs().size() - 1).type)) { // 보스(동굴 3층/대주교)는 맵 리셋까지
            long bid = map.mobs().size() - 1;
            list.removeIf(r -> r[0] == bid);
            list.add(new long[] { bid, 0, Long.MAX_VALUE / 2, 0, 0, 0 });
        }
        out.put("mobs", list);
        return out;
    }

    // ---- [2026-10-24] 동굴 3층 보스 -- "선공하는 보스, 잡으면 미스릴 또는 별빛석, 맵 초기화 때만 나오고 잡히면 안 나오게" ----
    //  맵 리셋(2일 주기)마다 한 번만 나타난다. 잡히면 TBOT_S5_CONFIG.MINE_BOSS_KILLED = "주기|유저"(서버를 다시 켜도 그 주기엔 안 나옴).
    //  선공: 보스 2칸 안에 들어오면 보스가 먼저 인식한다([2026-10-25] 전투 턴 mineCombatTick -- 아래 몬스터 인식 참고).
    private static final String MINE_BOSS_KEY = "MINE_BOSS_KILLED";
    private static volatile int mineBossKilledCycle = Integer.MIN_VALUE;

    private boolean mineBossDead() {
        if (mineBossKilledCycle == Integer.MIN_VALUE) {
            try {
                String v = dao.selectConfigValue(MINE_BOSS_KEY);
                mineBossKilledCycle = v == null || v.trim().isEmpty() ? -1 : Integer.parseInt(v.split("\\|")[0].trim());
            } catch (Exception e) {
                mineBossKilledCycle = -1;
            }
        }
        return mineBossKilledCycle == S5MineMap.activeCycle();
    }

    // [2026-10-28] "젠은 맵 초기화 시점 1회만(보스 공통)" -- 타락한 대주교도 동굴 3층 보스처럼 주기마다 한 번(MINE_RAID_KILLED = "주기|유저").
    private static final String MINE_RAID_KEY = "MINE_RAID_KILLED";
    private static volatile int mineRaidKilledCycle = Integer.MIN_VALUE;

    /** 보스(type)가 이번 맵 주기에 이미 쓰러졌는지. 보스가 아니면 false. */
    private boolean mineBossDead(int type) {
        if (type == S5MineMobs.BOSS_TYPE) return mineBossDead();
        if (type != S5MineMobs.RAID_BOSS_TYPE) return false;
        if (mineRaidKilledCycle == Integer.MIN_VALUE) {
            try {
                String v = dao.selectConfigValue(MINE_RAID_KEY);
                mineRaidKilledCycle = v == null || v.trim().isEmpty() ? -1 : Integer.parseInt(v.split("\\|")[0].trim());
            } catch (Exception e) {
                mineRaidKilledCycle = -1;
            }
        }
        return mineRaidKilledCycle == S5MineMap.activeCycle();
    }

    private static S5MineMap.Mob mineBossOf(S5MineMap map) {
        if (map.mobs().isEmpty()) return null;
        S5MineMap.Mob m = map.mobs().get(map.mobs().size() - 1);
        return S5MineMobs.TYPES.get(m.type).boss ? m : null;
    }

    /** 쓰러졌을 때 깨어나는 곳으로 옮기고 그곳 이름을 돌려준다(성당에서 기도했으면 왕도 성당, 아니면 평원 마을). */
    private String mineRespawn(String userName, Map<String, Integer> items, long now) {
        mineDisengageAll(userName); // [2026-10-25] 쓰러지면 인식 끝
        boolean church = items.getOrDefault(MINE_ITEM_RESPAWN, 0) == 1;
        S5MineMap home = S5MineMap.get(church ? S5MineMap.CITY : S5MineMap.FIELD);
        int[] hp0 = church ? mineCityChurch(home) : new int[] { home.spawnX, home.spawnY };
        dao.updateMinePos(userName, home.id, hp0[0], hp0[1], now);
        return church ? "왕도 아스테리아 성당에서" : "중앙 평원 마을에서";
    }

    // ---- [2026-10-25] 전투 중 몬스터 인식(어그로) -- "전투 시 자동 체력 회복을 막고, 안 때리더라도 일정 턴마다 몬스터가 플레이어를 공격,
    //  5칸 이상 벌어지면 인식이 풀리고 체력이 가득 차도록" 요청.
    //  때린 몬스터(와 2칸 안에 들어온 보스)는 그 유저를 인식한다(MINE_ENGAGE). 인식 중엔 체력이 저절로 차지 않고(mineHpNow),
    //  화면이 1초마다 mineCombatTick을 부르면 서버가 몬스터를 한 칸씩 쫓아오게 하고(MINE_CHASE_STEP_MS) 바로 옆이면 MINE_MOB_STRIKE_GAP_MS마다 때린다
    //  (때릴 때마다 받는 반격도 한 턴으로 친다). 몬스터와 MINE_AGGRO_RANGE칸 이상 벌어지면 인식이 풀리고, 인식이 모두 풀리면 그때부터 체력이 서서히 찬다(10-25 "가득" -> "서서히").
    //  몬스터를 쓰러뜨려 인식이 끝나면 그때부터 다시 저절로 찬다. 화면을 닫아 10초 넘게 소식이 없으면 인식 중이 아닌 것으로 본다.
    private static final int MINE_AGGRO_RANGE = 5;
    private static final long MINE_MOB_STRIKE_GAP_MS = 1_500L;
    private static final long MINE_CHASE_STEP_MS = 600L;
    private static final long MINE_ENGAGE_TTL_MS = 10_000L;

    private static final class MineEngage {
        volatile String mapVer;
        volatile long at;
        final java.util.concurrent.ConcurrentHashMap<Integer, Long> mobs = new java.util.concurrent.ConcurrentHashMap<>(); // 몬스터 id -> 이 유저를 마지막으로 때린 시각
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, MineEngage> MINE_ENGAGE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 지금 몬스터에게 인식돼 있는지(체력 자동 회복 멈춤). */
    private static boolean mineEngaged(String userName, long now) {
        MineEngage e = userName == null ? null : MINE_ENGAGE.get(userName);
        return e != null && !e.mobs.isEmpty() && now - e.at <= MINE_ENGAGE_TTL_MS;
    }

    /** 몬스터 mobId가 이 유저를 인식(때렸을 때 / 보스에게 다가갔을 때). struckAt = 그 몬스터가 이 유저를 마지막으로 때린 시각. */
    private static void mineEngage(String userName, S5MineMap map, int mobId, long now, long struckAt) {
        MineEngage e = MINE_ENGAGE.computeIfAbsent(userName, u -> new MineEngage());
        if (!map.version().equals(e.mapVer)) { e.mobs.clear(); e.mapVer = map.version(); }
        e.at = now;
        e.mobs.put(mobId, struckAt);
    }

    private static void mineDisengageAll(String userName) {
        MINE_ENGAGE.remove(userName);
    }

    private static long[] mineMobRow(int id, MobState s) {
        return new long[] { id, s.hp, s.deadUntil, s.fx, s.fy, s.frozenUntil };
    }

    /** [2026-10-25] 전투 턴 -- 화면이 인식 중이거나 보스 2칸 안일 때 1초마다 부른다. 보스는 2칸 안에 들어오면 먼저 인식(선공).
     *  인식한 몬스터는 쫓아와서 바로 옆이면 때리고, MINE_AGGRO_RANGE칸 이상 멀어지면 인식이 풀린다(다 풀리면 그때부터 서서히 회복). */
    @Override
    @Transactional
    public HashMap<String, Object> mineCombatTick(String userName, String mapId, String ver, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName) || mineGate() != null) return out;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) { mineDisengageAll(userName); return out; }
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWaterWalk(items));
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0);
        int hp = mineHpNow(row, now, lock); // 인식 전이면 지금까지 회복한 만큼, 인식 중이면 회복 없이(mineHpNow)
        MineEngage e = MINE_ENGAGE.get(userName);
        if (e != null && !map.version().equals(e.mapVer)) { mineDisengageAll(userName); e = null; }
        // 보스 선공 -- 2칸 안에 들어오면 인식
        S5MineMap.Mob boss = mineBossOf(map);
        if (boss != null && !mineBossDead(boss.type) && (e == null || !e.mobs.containsKey(boss.id))) {
            S5MineMobs.Type bt = S5MineMobs.TYPES.get(boss.type);
            MobState bs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, boss.id), k -> new MobState(bt.hp()));
            int aggro = bt.stay ? 4 : 2; // [2026-10-28] 대주교는 4칸(풀리는 거리 5칸보다 안쪽)
            synchronized (bs) {
                if (bs.deadUntil > 0 && bs.deadUntil <= now) { bs.deadUntil = 0; bs.hp = bt.hp(); } // 다시 나타남(대주교 1시간)
                if (bs.deadUntil <= now) {
                    int near = -1;
                    for (int c : mineMobCells(boss, bs, now, map)) if (Math.abs(c % map.w - x) + Math.abs(c / map.w - y) <= aggro) near = c;
                    if (near >= 0) {
                        if (bs.frozenUntil <= now) { bs.fx = near % map.w; bs.fy = near / map.w; }
                        bs.frozenUntil = now + MINE_MOB_FREEZE_MS;
                        if (bs.target == null) bs.target = userName;
                        mineEngage(userName, map, boss.id, now, 0L);
                        out.put("bossAggro", true);
                    }
                }
            }
            e = MINE_ENGAGE.get(userName);
        }
        if (e == null || e.mobs.isEmpty()) { mineDisengageAll(userName); out.put("ok", true); out.put("fight", false); return out; }
        e.at = now;
        int lv = mineLv(row);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int dmgSum = 0, released = 0;
        List<HashMap<String, Object>> hits = new ArrayList<>();
        List<long[]> mobs = new ArrayList<>();
        String killerName = null;
        for (Integer id : new ArrayList<>(e.mobs.keySet())) {
            MobState s = id >= 0 && id < map.mobs().size() ? MINE_MOB_STATE.get(mineMobKey(map, id)) : null;
            if (s == null) { e.mobs.remove(id); continue; }
            S5MineMobs.Type type = S5MineMobs.TYPES.get(map.mobs().get(id).type);
            synchronized (s) {
                if (s.deadUntil > now) { e.mobs.remove(id); mobs.add(mineMobRow(id, s)); continue; } // 누가 쓰러뜨렸다
                if (s.frozenUntil <= now) { // 한동안 소식이 없어 돌아다니던 몬스터 -- 지금 칸에서 다시 붙잡는다
                    s.target = null;
                    int[] cs = mineMobCells(map.mobs().get(id), s, now, map);
                    s.fx = cs[cs.length / 2] % map.w; s.fy = cs[cs.length / 2] / map.w;
                }
                int dist = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                if (dist >= MINE_AGGRO_RANGE) {
                    e.mobs.remove(id);
                    released++;
                    if (userName.equals(s.target)) { s.target = null; s.frozenUntil = now; } // 다시 제자리로 걸어간다
                    mobs.add(mineMobRow(id, s));
                    continue;
                }
                if (s.target == null) s.target = userName;
                // 쫓아온다(한 칸) -- [2026-10-28] 대주교는 제자리, 수하(fast)는 플레이어보다 3배쯤 빨리(초당 약 20칸) 여러 칸
                long stepGap = type.fast ? MINE_FAST_STEP_MS : MINE_CHASE_STEP_MS;
                if (!type.stay && userName.equals(s.target) && dist > 1 && now - s.lastMoveAt >= stepGap) {
                    int steps = type.fast ? (int) Math.min(dist - 1, Math.min(25, (now - s.lastMoveAt) / stepGap)) : 1;
                    for (int st = 0; st < steps; st++) {
                        int dx = Integer.signum(x - s.fx), dy = Integer.signum(y - s.fy);
                        int[][] tries = Math.abs(x - s.fx) >= Math.abs(y - s.fy) ? new int[][] { { dx, 0 }, { 0, dy } } : new int[][] { { 0, dy }, { dx, 0 } };
                        boolean moved = false;
                        for (int[] t : tries) {
                            int nx = s.fx + t[0], ny = s.fy + t[1];
                            if ((t[0] != 0 || t[1] != 0) && map.walkable(nx, ny) && !(nx == x && ny == y)) { s.fx = nx; s.fy = ny; moved = true; break; }
                        }
                        if (!moved || Math.abs(s.fx - x) + Math.abs(s.fy - y) <= 1) break;
                    }
                    s.lastMoveAt = now;
                    dist = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                }
                s.frozenUntil = now + MINE_MOB_FREEZE_MS; // 인식 중엔 서버가 위치를 쥔다
                Long last = e.mobs.get(id);
                if (dist <= 1 && (last == null || now - last >= MINE_MOB_STRIKE_GAP_MS) && hp - dmgSum > 0) {
                    int d = Math.max(1, (int) Math.round(type.atk() * (0.8 + RND.nextDouble() * 0.4))) * (mineCoatDef(items) > 0 ? 2 : 1); // [2026-10-26] 방한 외투 = 2배
                    dmgSum += d;
                    e.mobs.put(id, now);
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("mobId", id);
                    h.put("dmg", d);
                    hits.add(h);
                    killerName = type.name;
                }
                mobs.add(mineMobRow(id, s));
            }
        }
        if (boss != null && boss.type == S5MineMobs.RAID_BOSS_TYPE && e.mobs.containsKey(boss.id)) mineRaidSummon(userName, map, boss, now, out, mobs); // [2026-10-28]
        out.put("ok", true);
        out.put("hits", hits);
        out.put("mobs", mobs);
        boolean fight = !e.mobs.isEmpty();
        if (dmgSum == 0 && fight && !out.containsKey("bossAggro")) { out.put("fight", true); out.put("combat", mineCombatInfo(lv, exp, hp, now, true, lock)); return out; } // 방금 인식됐으면 그때까지 찬 체력을 저장
        dao.selectMineUserForUpdate(userName); // 쓰기 전에 같은 유저 요청 직렬화
        if (dmgSum > 0) {
            hp -= dmgSum;
            if (hp <= 0) {
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCap(lv, lock);
                mineDisengageAll(userName);
                fight = false;
                out.put("died", true);
                out.put("message", "💫 " + killerName + "에게 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out));
            }
        }
        if (!fight && !out.containsKey("died")) {
            mineDisengageAll(userName);
            if (released > 0) { // [2026-10-25] "인식이 풀리면 서서히 회복" -- 바로 가득 채우지 않고 지금부터 다시 저절로 찬다(아래 저장 시각부터)
                out.put("released", true);
                out.put("message", "🏃 몬스터가 더는 쫓아오지 않아요. 숨을 고르니 체력이 서서히 돌아와요.");
            }
        }
        if (!out.containsKey("died") && dmgSum > 0) dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, lv, exp, hp, now, 0);
        out.put("fight", fight);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, fight, lock));
        if (out.containsKey("died")) out.put("state", mineState(userName));
        return out;
    }

    /** 예전 화면(보스 선공 주소)도 같은 전투 턴으로. */
    @Override
    @Transactional
    public HashMap<String, Object> mineBossHit(String userName, String mapId, String ver, int x, int y) {
        return mineCombatTick(userName, mapId, ver, x, y);
    }

    // ---- [2026-10-28] 성당 옛터 -- 타락한 대주교(레이드 보스) ----
    // "아주 센 보스 한 마리, 수하를 소환(플레이어보다 3배쯤 빠르고 강함), 다른 몬스터 없음, 광역 마법 -- 불벼락/어둠가스 등 전맵기와 화면 전체기,
    //  데미지가 엄청 세서 80레벨 이상 권장, 특수 재료 드랍" 요청.
    //  대주교(21단계 보스, 제단에서 움직이지 않음)는 4칸 안에 들어오면 먼저 인식. 누가 대주교와 싸우는 동안(마지막 전투 턴/공격 + 15초 = 깨어 있음):
    //   - 수하 소환: 20초마다 잠든 수하 자리(제단 둘레 6칸) 중 둘을 깨워 싸우는 유저를 쫓게 한다(초당 약 20칸).
    //   - 불벼락(화면 전체기): 12초마다, 대주교 12칸 안의 모든 유저에게 공격력 x2.5. 12칸 밖으로 피하면 안 맞는다.
    //   - 어둠가스(전맵기): 30초마다, 성당 옛터 어디에 있든 공격력 x1.2.
    //   마법 피해는 화면이 1초마다 부르는 mineColdTick(환경 턴)에서 계산한다(방한 외투면 2배). 마법으로 쓰러지면 전투 사망(최대 체력 잠금 +1).
    //  같은 날 "보스 공통 맵 초기화 때 1번만" -- 처음엔 1시간 뒤 다시였다가 주기마다 한 번(mineBossDead(type), MINE_RAID_KILLED).
    //  보상: 타락한 대주교의 인장 1 + 50% 다이아몬드 2 + 30% 별빛석(성유물 조각은 없앴다).
    private static final long MINE_FAST_STEP_MS = 50L;
    private static final long MINE_RAID_AWAKE_MS = 15_000L;
    private static final long MINE_RAID_SUMMON_MS = 20_000L;
    private static final int MINE_RAID_SUMMON_N = 2;
    private static final long MINE_RAID_FIRE_MS = 12_000L, MINE_RAID_FIRE_OFF = 6_000L;
    private static final long MINE_RAID_GAS_MS = 30_000L, MINE_RAID_GAS_OFF = 15_000L;
    private static final int MINE_RAID_FIRE_RANGE = 12;
    private static volatile long mineRaidAwakeUntil = 0;
    private static volatile long mineRaidLastSummon = 0;

    /** period마다 offset에 오는 일(마법) 중 after 다음 시각. */
    private static long mineRaidNext(long period, long offset, long after) {
        return ((after - offset) / period + 1) * period + offset;
    }

    /** 수하 소환 -- 대주교와 싸우는 유저(userName)에게 잠든 수하 자리 중 MINE_RAID_SUMMON_N개를 깨운다(20초마다). */
    private void mineRaidSummon(String userName, S5MineMap map, S5MineMap.Mob boss, long now, HashMap<String, Object> out, List<long[]> rows) {
        mineRaidAwakeUntil = now + MINE_RAID_AWAKE_MS;
        MobState bs = MINE_MOB_STATE.get(mineMobKey(map, boss.id));
        if (bs == null || bs.deadUntil > now || now - mineRaidLastSummon < MINE_RAID_SUMMON_MS) return;
        mineRaidLastSummon = now;
        int n = 0;
        for (S5MineMap.Mob mb : map.mobs()) {
            if (n >= MINE_RAID_SUMMON_N) break;
            S5MineMobs.Type ty = S5MineMobs.TYPES.get(mb.type);
            if (!ty.minion) continue;
            String key = mineMobKey(map, mb.id);
            MobState s = MINE_MOB_STATE.get(key);
            if (s != null && s.deadUntil <= now) continue; // 이미 나와 있다
            MobState ns = new MobState(ty.hp());
            ns.fx = mb.x; ns.fy = mb.y;
            ns.frozenUntil = now + MINE_MOB_FREEZE_MS;
            ns.target = userName;
            ns.lastMoveAt = now;
            MINE_MOB_STATE.put(key, ns);
            mineEngage(userName, map, mb.id, now, now - MINE_MOB_STRIKE_GAP_MS + 700); // 곧 덤벼든다
            rows.add(mineMobRow(mb.id, ns));
            n++;
        }
        if (n > 0) { out.put("summoned", n); out.put("message", "👿 타락한 대주교가 수하 " + n + "명을 불러냈어요! 엄청 빠르게 쫓아와요!"); }
    }

    /** 타락한 대주교 보상: 인장 1(반드시) + 50% 다이아몬드 2 + 30% 별빛석 1. [2026-10-28] 같은 날 성유물 조각은 없앴다("성유물 조각은 없애자"). */
    private List<HashMap<String, Object>> mineRaidDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        dao.addUserOre(userName, S5MineMap.SEAL_CODE, 1);
        drops.add(mineDrop(S5MineMap.SEAL_GRADE, S5MineMap.SEAL_NAME, 1));
        msg.append(" · 📜 ").append(S5MineMap.SEAL_NAME).append(" 1개!");
        if (RND.nextInt(100) < 50) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 2);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 2));
            msg.append(" · 💎 다이아몬드 2개");
        }
        if (RND.nextInt(100) < 30) {
            dao.addUserOre(userName, S5MineMap.ORE_CODES[5], 1);
            drops.add(mineDrop(6, S5MineMap.ORE_NAMES[5], 1));
            msg.append(" · ✨ ").append(S5MineMap.ORE_NAMES[5]).append(" 1개");
        }
        return drops;
    }

    /** 보스 보상: 25% 별빛석 1개, 아니면 미스릴 3~5개 + 30% 다이아몬드. */
    private List<HashMap<String, Object>> mineBossDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        if (RND.nextInt(100) < 25) {
            dao.addUserOre(userName, S5MineMap.ORE_CODES[5], 1);
            drops.add(mineDrop(6, S5MineMap.ORE_NAMES[5], 1));
            msg.append(" · ✨ ").append(S5MineMap.ORE_NAMES[5]).append(" 1개");
        } else {
            int q = 3 + RND.nextInt(3);
            dao.addUserOre(userName, S5MineMap.ORE_CODES[4], q);
            drops.add(mineDrop(5, S5MineMap.ORE_NAMES[4], q));
            msg.append(" · ").append(S5MineMap.ORE_NAMES[4]).append(' ').append(q).append("개");
        }
        if (RND.nextInt(100) < 30) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 1);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 1));
            msg.append(" · 💎 다이아몬드 1개");
        }
        return drops;
    }

    /** 공격 -- (x, y)에 서서 바로 옆(상하좌우) 몬스터 mobId를 때린다. 살아남으면 반격(몬스터 공격력), 쓰러뜨리면 경험치 + 광석/보석. */
    @Override
    @Transactional
    public HashMap<String, Object> mineAttack(String userName, String mapId, String ver, int x, int y, int mobId, int mc) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        Long last = MINE_LAST_ATTACK.get(userName);
        if (last != null && now - last < MINE_ATTACK_GAP_MS) { out.put("tooFast", true); return out; }
        MINE_LAST_ATTACK.put(userName, now);
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        boolean water = mineWaterWalk(items);
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        if (!mineWalk(map, x, y, water) || !mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        String block = mineMapBlock(map, items, mineLv(row));
        if (block != null) { out.put("message", block); return out; }
        if (mobId < 0 || mobId >= map.mobs().size()) { out.put("message", "그 몬스터는 없어요."); return out; }
        S5MineMap.Mob mob = map.mobs().get(mobId);
        S5MineMobs.Type type = S5MineMobs.TYPES.get(mob.type);
        if (mineBossDead(mob.type)) { out.put("message", "이번 맵에서는 이미 쓰러진 보스예요. 다음 맵 리셋 때 다시 나타나요."); out.put("mobId", mobId); out.put("mobDeadUntil", Long.MAX_VALUE / 2); return out; }
        if (type.minion && MINE_MOB_STATE.get(mineMobKey(map, mobId)) == null) { out.put("mobId", mobId); out.put("mobDeadUntil", Long.MAX_VALUE / 2); out.put("far", true); return out; } // [2026-10-28] 불려 나오지 않은 수하
        int lv = mineLv(row);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0);
        int hp = mineHpNow(row, now, lock);
        out.put("mobId", mobId);
        MobState s = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, mobId), k -> new MobState(type.hp()));
        int dmg, counter = 0, kills = 0;
        boolean killed = false, crit;
        synchronized (s) {
            if (s.deadUntil > now) { out.put("message", "이미 쓰러진 " + type.name + "이에요."); out.put("mobDeadUntil", s.deadUntil); return out; }
            if (s.deadUntil > 0) { s.deadUntil = 0; s.hp = type.hp(); } // 다시 나타남
            int hitCell = -1;
            for (int c : mineMobCells(mob, s, now, map)) if (Math.abs(c % map.w - x) + Math.abs(c / map.w - y) == 1 && (hitCell < 0 || c == mc)) hitCell = c; // 화면이 보던 칸 우선
            if (hitCell < 0) { out.put("message", type.name + "에게 닿지 않아요. 바로 옆에서 공격하세요."); out.put("far", true); return out; }
            crit = RND.nextInt(100) < 10;
            dmg = Math.max(1, (int) Math.round(mineAtk(lv) * (0.85 + RND.nextDouble() * 0.3) * (crit ? 1.6 : 1.0)));
            s.hp -= dmg;
            s.lastHitAt = now;
            if (s.hp <= 0) {
                killed = true;
                s.hp = 0;
                // 보스(동굴 3층/타락한 대주교)는 다음 맵 리셋까지, [2026-10-28] 대주교 수하는 다시 불러낼 때까지
                s.deadUntil = type.boss || type.minion ? Long.MAX_VALUE / 2 : now + type.respawnMs();
                s.frozenUntil = 0;
            } else {
                s.fx = hitCell % map.w;
                s.fy = hitCell / map.w;
                s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                counter = Math.max(1, (int) Math.round(type.atk() * (0.8 + RND.nextDouble() * 0.4)));
                if (s.target == null || !mineEngaged(s.target, now)) s.target = userName; // [2026-10-25] 이 유저를 쫓는다
                mineEngage(userName, map, mobId, now, now); // 반격도 한 턴
                if (mob.type == S5MineMobs.RAID_BOSS_TYPE) mineRaidAwakeUntil = now + MINE_RAID_AWAKE_MS; // [2026-10-28] 대주교가 깨어난다(마법)
            }
            out.put("mobHp", s.hp);
            out.put("mobDeadUntil", s.deadUntil);
            out.put("mobFx", s.fx);
            out.put("mobFy", s.fy);
            out.put("mobFrozenUntil", s.frozenUntil);
        }
        out.put("ok", true);
        out.put("dmg", dmg);
        out.put("crit", crit);
        out.put("killed", killed);
        out.put("mobHpMax", type.hp());
        StringBuilder msg = new StringBuilder();
        boolean died = false;
        int levelUp = 0;
        if (killed) {
            kills = 1;
            exp += type.exp();
            while (lv < MINE_LV_MAX && exp >= mineExpNeed(lv)) { exp -= mineExpNeed(lv); lv++; levelUp++; }
            if (levelUp > 0) { // 레벨이 오르면 체력이 다 차고 [2026-10-25] 잠긴 최대 체력도 풀린다
                hp = mineHpMax(lv);
                if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.remove("hpLockUp"); out.put("hpLockFree", true); }
            }
            msg.append("⚔️ ").append(type.name).append("을(를) 쓰러뜨렸어요! 경험치 +").append(type.exp());
            if (levelUp > 0) msg.append(" 🎉 레벨 업! Lv.").append(lv);
            out.put("expGain", type.exp());
            if (mob.type == S5MineMobs.RAID_BOSS_TYPE) { // [2026-10-28] 타락한 대주교 처치 -- 이번 주기엔 다시 안 나온다(DB 기록), 마법/소환 멈춤
                mineRaidAwakeUntil = 0;
                mineRaidKilledCycle = S5MineMap.activeCycle();
                dao.upsertConfigValue(MINE_RAID_KEY, S5MineMap.activeCycle() + "|" + userName, "web mine ruins raid boss killed (cycle|user)");
                msg.insert(0, "👑 타락한 대주교를 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (mob.type == S5MineMobs.BOSS_TYPE) { // [2026-10-24] 보스 처치 -- 이번 주기엔 다시 안 나온다(DB 기록)
                mineBossKilledCycle = S5MineMap.activeCycle();
                dao.upsertConfigValue(MINE_BOSS_KEY, S5MineMap.activeCycle() + "|" + userName, "web mine cave3 boss killed (cycle|user)");
                msg.insert(0, "👑 보스 처치! ");
                out.put("bossKilled", true);
            }
            out.put("drops", mob.type == S5MineMobs.RAID_BOSS_TYPE ? mineRaidDrops(userName, msg) : type.boss ? mineBossDrops(userName, msg) : mineMobDrops(userName, map, type, msg));
            out.put("ores", mineOreList(userName));
        } else {
            if (mineCoatDef(items) > 0) counter *= 2; // [2026-10-26] 방한 외투 -- 받는 피해 2배
            hp -= counter;
            out.put("counter", counter);
            if (hp <= 0) {
                died = true;
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCap(lv, lock);
                msg.append("💫 ").append(type.name).append("에게 쓰러졌어요... ").append(mineRespawn(userName, items, now)).append(" 깨어났어요.").append(mineLockText(out));
            }
        }
        if (killed) { MineEngage me = MINE_ENGAGE.get(userName); if (me != null) me.mobs.remove(mobId); }
        if (!died) dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, lv, exp, hp, now, kills);
        out.put("levelUp", levelUp);
        out.put("died", died);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, mineEngaged(userName, now), lock));
        if (msg.length() > 0) out.put("message", msg.toString());
        if (died) out.put("state", mineState(userName));
        return out;
    }

    /** 처치 보상 -- [2026-10-24] "낮은 게 조금 더 잘 나오는 느낌으로" 조정(처음엔 60% + 높은 등급 35%라 Lv.33 몬스터에서 미스릴이 시간당 100개 넘게 나왔다).
     *  55% 확률로 광석: 기준 등급 b = 1 + (단계-1)/2(구리~미스릴). 55% = 한 등급 아래(1~2개), 37% = 기준(금광석까지), 8% = 한 등급 위(금광석까지,
     *  미스릴은 9단계 몬스터의 8%뿐), 별빛석은 안 나옴. 보석은 캘 때와 같은 확률에 흔한 보석만 1.5배(5% -> 7.5%), 다이아 0.2%. */
    private List<HashMap<String, Object>> mineMobDrops(String userName, S5MineMap map, S5MineMobs.Type type, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        if (RND.nextInt(100) < 55) {
            int b = Math.min(5, 1 + (type.tier - 1) / 2), r = RND.nextInt(100), g, qty = 1;
            if (r < 55) { g = Math.max(1, b - 1); qty += RND.nextInt(2); }
            else if (r < 92) g = Math.min(b, 4);
            else g = type.tier >= 9 ? 5 : Math.min(b + 1, 4);
            dao.addUserOre(userName, S5MineMap.ORE_CODES[g - 1], qty);
            drops.add(mineDrop(g, S5MineMap.ORE_NAMES[g - 1], qty));
            msg.append(" · ").append(S5MineMap.ORE_NAMES[g - 1]).append(' ').append(qty).append("개");
        }
        int gi = S5MineMap.gemForMob(map.id, RND.nextInt(1000), 1.5);
        if (gi >= 0) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[gi], 1);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + gi, S5MineMap.GEM_NAMES[gi], 1));
            msg.append(" · 💎 ").append(S5MineMap.GEM_NAMES[gi]).append(" 1개");
        }
        // [2026-10-28] 설산 몬스터 빙석 -- 같은 날 "더 안 나오게(득템 느낌)" 15% -> 3%
        boolean snow = S5MineMap.SNOW1.equals(map.id) || S5MineMap.SNOW2.equals(map.id) || S5MineMap.SNOW3.equals(map.id);
        if (snow && RND.nextInt(100) < 3) {
            dao.addUserOre(userName, S5MineMap.ICE_STONE_CODE, 1);
            drops.add(mineDrop(S5MineMap.ICE_STONE_GRADE, S5MineMap.ICE_STONE_NAME, 1));
            msg.append(" · 🧊 ").append(S5MineMap.ICE_STONE_NAME).append(" 1개!");
        }
        return drops;
    }

    private static HashMap<String, Object> mineDrop(int grade, String name, int qty) {
        HashMap<String, Object> d = new HashMap<>();
        d.put("grade", grade);
        d.put("name", name);
        d.put("qty", qty);
        return d;
    }

    @Override
    public HashMap<String, Object> mineState(String userName) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = mineRow(userName, false);
        S5MineMap map = mineMapOf(row);
        HashMap<String, Object> out = new HashMap<>();
        putMinePick(out, minePickDur(row), minePickMax(row));
        out.put("pickUpgrades", minePickUpgradeList());
        out.put("pickCap", MINE_PICK_CAP); // [2026-10-24] 최대 내구도 상한
        out.put("nextResetAt", S5MineMap.nextResetAt(now));
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWaterWalk(items));
        out.put("mapId", map.id);
        out.put("mapName", map.name);
        out.put("mapVersion", map.version());
        out.put("x", pos[0]);
        out.put("y", pos[1]);
        // 이 맵에서 캔 칸(영구) -- [x, y, 등급, x, y, 등급, ...]
        List<Integer> dug = new ArrayList<>();
        if (S5MineMap.DUNGEON.equals(map.id)) { // [2026-10-23] 요일 던전 -- 오늘 나오는 돌
            int di = S5MineMap.dayIndex(now);
            out.put("dayLabel", S5MineMap.DAY_LABELS[di]);
            out.put("dayStone", S5MineMap.DAY_STONE_NAMES[di]);
        }
        for (HashMap<String, Object> d : dao.selectMineDugCells(userName, mineDugKey(map, now))) {
            int cell = intVal(d.get("CELL"), 0);
            dug.add(cell % map.w);
            dug.add(cell / map.w);
            dug.add(intVal(d.get("ORE_GRADE"), 0));
        }
        out.put("dug", dug);
        out.put("recent", mineRecent(userName));
        out.put("ores", mineOreList(userName));
        out.put("totalDig", intVal(row.get("TOTAL_DIG"), 0));
        out.put("ppBonus", numVal(row.get("PP_BONUS"), 0));
        int helmetTier = items.getOrDefault(MINE_ITEM_HELMET, 0);
        out.put("helmet", helmetTier);
        out.put("helmetInfo", mineHelmetInfo(userName, helmetTier)); // [2026-10-24] 대장장이 창의 투구 업그레이드
        out.put("helmetNeed", mineHelmetNeed(map));
        mineGearPayload(userName, items, out); // [2026-10-24] 장신구/장화/용왕의 반지/선장 퀘스트
        out.put("combat", mineCombatInfo(mineLv(row), Math.max(0, longOf(row.get("M_EXP"), 0L)), mineHpNow(row, now, items.getOrDefault(MINE_ITEM_HP_LOCK, 0)), now, mineEngaged(userName, now), items.getOrDefault(MINE_ITEM_HP_LOCK, 0))); // [2026-10-24] 광산 전투 레벨
        if (row.get("M_HP") != null) // [2026-10-26] 여기선 저장하지 않으니 회복 시작은 마지막 저장 시각 + 대기
            ((HashMap<String, Object>) out.get("combat")).put("regenFrom", longOf(row.get("M_HP_AT_MS"), now) + MINE_REGEN_DELAY_MS);
        out.put("mapMinLv", MINE_MIN_LV); // [2026-10-24] 구역별 권장 레벨(전체 지도 표시, 들어갈 때 경고 -- 막지는 않음)
        out.put("respawnCity", items.getOrDefault(MINE_ITEM_RESPAWN, 0) == 1); // [2026-10-24] 쓰러지면 왕도 성당에서
        out.put("visitedSnowtown", items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) > 0); // [2026-10-26] 이동 안내원 목적지
        out.put("visitedCity", items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) > 0 || isMineAdmin(userName)); // [2026-10-27]
        out.put("coatDef", mineCoatDef(items)); // [2026-10-26] 입은 방한 외투의 한기 방어 단계(0 = 안 입음)
        if (isMineAdmin(userName)) { // [2026-10-24] 관리자 전용 좌표 이동(화면 좌표 클릭) -- 고를 수 있는 맵 목록
            List<HashMap<String, Object>> maps = new ArrayList<>();
            for (String id : new String[] { S5MineMap.FIELD, S5MineMap.CAVE1, S5MineMap.DESERT, S5MineMap.BEACH, S5MineMap.DUNGEON, S5MineMap.CASTLE, S5MineMap.ISLAND, S5MineMap.NORTH, S5MineMap.STEPPE, S5MineMap.CITY, S5MineMap.CAVE2, S5MineMap.CAVE3, S5MineMap.SEAROAD,
                    S5MineMap.SNOW1, S5MineMap.SNOW2, S5MineMap.SNOW3, S5MineMap.SNOWTOWN, S5MineMap.ROYALROAD, S5MineMap.RUINS }) {
                S5MineMap m = S5MineMap.get(id);
                HashMap<String, Object> e = new HashMap<>();
                e.put("id", m.id);
                e.put("name", m.name);
                e.put("w", m.w);
                e.put("h", m.h);
                maps.add(e);
            }
            out.put("adminMaps", maps);
        }
        return out;
    }

    /** 최근 캔 곳(모든 맵, 최신 30개) -- 좌표 공유용 목록. */
    private List<HashMap<String, Object>> mineRecent(String userName) {
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (HashMap<String, Object> d : dao.selectMineDugRecent(userName, 30)) {
            String mid = strVal(d.get("MAP_ID"), S5MineMap.FIELD);
            S5MineMap m = S5MineMap.get(mid.startsWith(S5MineMap.DUNGEON) ? S5MineMap.DUNGEON : mid); // 요일 던전 키는 dungeon-yyyyMMdd
            int cell = intVal(d.get("CELL"), 0), g = intVal(d.get("ORE_GRADE"), 0);
            HashMap<String, Object> r = new HashMap<>();
            r.put("mapName", m.name);
            r.put("x", cell % m.w);
            r.put("y", cell / m.w);
            r.put("grade", g);
            r.put("name", mineGradeName(g));
            r.put("at", longOf(d.get("DUG_AT_MS"), 0L));
            out.add(r);
        }
        return out;
    }

    @Override
    @Transactional
    public HashMap<String, Object> mineDig(String userName, String mapId, String ver, int x, int y, String dir) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap map = mineMapOf(row);
        int st = minePickDur(row), stMax = minePickMax(row);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        putMinePick(out, st, stMax);
        Map<String, Integer> items = mineItems(userName);
        boolean water = mineWaterWalk(items);
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        if (!mineWalk(map, x, y, water)) {
            out.put("message", "갈 수 없는 곳입니다.");
            return out;
        }
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        String block = mineMapBlock(map, items, mineLv(row));
        if (block != null) {
            out.put("message", block);
            return out;
        }
        int g = map.oreAt(x, y);
        if (g == 0) {
            out.put("message", "여기서는 캘 수 없어요.");
            out.put("noOre", true); // [2026-10-24] 자동채광이 이 칸을 다시 고르지 않게
            return out;
        }
        int cell = y * map.w + x;
        String dugKey = mineDugKey(map, now);
        if (dao.countMineDug(userName, dugKey, cell) > 0) {
            out.put("message", "이미 캔 자리예요. 다른 곳을 찾아보세요.");
            out.put("noOre", true);
            return out;
        }
        if (st < 1) {
            out.put("message", "⛏️ 곡괭이 내구도가 다 닳았어요. 중앙 평원 마을의 대장장이에게 광석을 주고 곡괭이를 강화하세요(최대·현재 내구도가 함께 올라요).");
            return out;
        }
        // [2026-10-24] 바닷길은 하루 보석 MINE_SEA_DAILY개까지(광역 곡괭이로 함께 캔 칸도 센다) -- 다 캤으면 내구도도 안 쓴다
        boolean sea = S5MineMap.SEAROAD.equals(map.id);
        int today = Integer.parseInt(java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        int seaUsed = sea && items.getOrDefault(MINE_ITEM_SEA_DAY, 0) == today ? items.getOrDefault(MINE_ITEM_SEA_CNT, 0) : 0;
        if (sea && seaUsed >= MINE_SEA_DAILY) {
            out.put("message", "🌊 오늘은 바닷길에서 보석을 " + MINE_SEA_DAILY + "개 다 캤어요. 내일 다시 와 주세요.");
            out.put("seaLimit", true);
            return out;
        }
        // 칸 하나 캐기(요일석/바닷길 보석/광석 + 반지/목걸이 효과 + 보석) -- [2026-10-24] 광역 곡괭이(주변 3x3)도 같은 계산을 칸마다 한다.
        HashMap<String, Object> one = mineDigCell(userName, map, items, x, y, g, now, dugKey);
        int used = 1;
        // [2026-10-24] 광역 곡괭이 -- 끼고 있으면 주변 8칸 중 광석이 있고 안 캔 칸을 남은 내구도만큼 함께 캔다(칸마다 내구도 1).
        // [2026-10-25] 쌍날 곡괭이 -- 끼고 있으면 바라보는 방향(dir)의 앞 칸과 뒤 칸을 같은 규칙으로 함께 캔다.
        List<HashMap<String, Object>> wide = new ArrayList<>();
        int pickSlot = items.getOrDefault(MINE_ITEM_PICK_SLOT, 0);
        List<int[]> around = new ArrayList<>();
        if (pickSlot == 1 && items.getOrDefault(MINE_ITEM_WIDE_PICK, 0) > 0) {
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (dx != 0 || dy != 0) around.add(new int[] { dx, dy });
        } else if (pickSlot == 3 && items.getOrDefault(MINE_ITEM_LINE_PICK, 0) > 0) {
            int[] v = "up".equals(dir) ? new int[] { 0, -1 } : "left".equals(dir) ? new int[] { -1, 0 } : "right".equals(dir) ? new int[] { 1, 0 } : new int[] { 0, 1 };
            around.add(v);
            around.add(new int[] { -v[0], -v[1] });
        }
        {
            for (int[] off : around) {
                int dx = off[0], dy = off[1];
                if (st - used < 1 || (sea && seaUsed + used >= MINE_SEA_DAILY)) continue;
                int nx = x + dx, ny = y + dy, ng = map.oreAt(nx, ny);
                if (ng == 0 || !map.walkable(nx, ny) || dao.countMineDug(userName, dugKey, ny * map.w + nx) > 0) continue;
                HashMap<String, Object> w = mineDigCell(userName, map, items, nx, ny, ng, now, dugKey);
                w.put("x", nx);
                w.put("y", ny);
                wide.add(w);
                used++;
            }
        }
        if (sea) {
            dao.upsertMineItem(userName, MINE_ITEM_SEA_DAY, today);
            dao.upsertMineItem(userName, MINE_ITEM_SEA_CNT, seaUsed + used);
            out.put("seaLeft", MINE_SEA_DAILY - seaUsed - used);
        }
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("pickDur", st - used);
        up.put("atMs", now);
        up.put("x", x);
        up.put("y", y);
        dao.updateMineUser(up);
        String code = (String) one.get("oreCode"), name = (String) one.get("oreName");
        int gain = (Integer) one.get("gain");
        boolean upgraded = Boolean.TRUE.equals(one.get("upgraded"));
        int qty = 0;
        for (HashMap<String, Object> r : dao.selectUserOres(userName)) if (code.equals(strVal(r.get("ORE_CODE"), ""))) qty = intVal(r.get("QTY"), 0);
        out.put("ok", true);
        putMinePick(out, st - used, stMax);
        out.put("x", x);
        out.put("y", y);
        out.put("grade", one.get("grade"));
        out.put("oreCode", code);
        out.put("oreName", name);
        out.put("qty", qty);
        out.put("gain", gain);
        out.put("upgraded", upgraded);
        String msg = "⛏️ " + map.name + " (" + x + ", " + y + ") " + name + " " + gain + "개! (보유 " + qty + "개)"
                + (upgraded ? " 💍 심연의 반지: 한 등급 위!" : "") + (gain > 1 ? " 💍 풍요의 반지: 2개!" : "");
        if (one.get("gemGrade") != null) {
            out.put("gemGrade", one.get("gemGrade"));
            out.put("gemName", one.get("gemName"));
            msg += " + 💎 " + one.get("gemName") + " 1개!";
        }
        // [2026-10-24] 탐지 곡괭이로 별빛석을 캐면 빛을 잃는다(이번 맵 주기 동안 탐지 꺼짐)
        if (Integer.valueOf(6).equals(one.get("grade")) && items.getOrDefault(MINE_ITEM_PICK_SLOT, 0) == 2 && items.getOrDefault(MINE_ITEM_DETECT_PICK, 0) > 0 && !mineDetectDim(items)) {
            dao.upsertMineItem(userName, MINE_ITEM_DETECT_OFF, S5MineMap.activeCycle() + 1);
            out.put("detectDim", true);
            msg += " 🌑 탐지 곡괭이가 별빛에 눌려 빛을 잃었어요... 맵 리셋 때 다시 빛나요.";
        }
        boolean ice = one.get("iceGain") != null;
        for (HashMap<String, Object> wc : wide) ice |= wc.get("iceGain") != null;
        if (ice) { // [2026-10-28] 설산 빙석
            out.put("iceGain", true);
            out.put("ores", mineOreList(userName));
            msg += " 🧊 빙석 발견!";
        }
        if (!wide.isEmpty()) {
            out.put("wide", wide);
            out.put("ores", mineOreList(userName));
            msg += (pickSlot == 3 ? " ⛏️ 쌍날 +" : " ⛏️ 광역 +") + wide.size() + "칸";
        }
        out.put("message", msg);
        return out;
    }

    /** [2026-10-24] 탐지 곡괭이 -- (x, y) 주변 3x3칸 중 내가 안 캔 광석 칸: [dx, dy, 등급 값](요일 던전 = 오늘 요일석, 바닷길 = -1 보석 줄기).
     *  탐지 곡괭이를 끼고 있고 (x, y)가 저장 위치에서 갈 수 있는 거리일 때만. 읽기만 한다. */
    @Override
    public HashMap<String, Object> mineScan(String userName, String mapId, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName) || mineGate() != null) return out;
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        Map<String, Integer> items = mineItems(userName);
        if (items.getOrDefault(MINE_ITEM_DETECT_PICK, 0) < 1 || items.getOrDefault(MINE_ITEM_PICK_SLOT, 0) != 2) return out;
        if (mineDetectDim(items)) { out.put("dim", true); return out; } // 별빛석을 캐서 빛을 잃음 -- 맵 리셋 때 회복
        S5MineMap map = mineMapOf(row);
        if (mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) return out;
        long now = System.currentTimeMillis();
        if (!mineMoveOk(minePos(row, map, mineWaterWalk(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        String dugKey = mineDugKey(map, now);
        List<int[]> cells = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy, g = map.oreAt(nx, ny);
            if (g == 0 || dao.countMineDug(userName, dugKey, ny * map.w + nx) > 0) continue;
            if (S5MineMap.DUNGEON.equals(map.id)) g = S5MineMap.DAY_STONE_GRADE_BASE + S5MineMap.dayIndex(now);
            else if (S5MineMap.SEAROAD.equals(map.id)) g = -1;
            cells.add(new int[] { dx, dy, g });
        }
        out.put("ok", true);
        out.put("x", x);
        out.put("y", y);
        out.put("cells", cells);
        return out;
    }

    /** 칸 하나에서 얻기 -- 캔 칸 기록 + 광석/요일석/보석 지급. 결과: grade, oreCode, oreName, gain, upgraded, (gemGrade, gemName). 내구도/위치는 부른 쪽이 저장. */
    private HashMap<String, Object> mineDigCell(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, int g, long now, String dugKey) {
        String code, name;
        boolean upgraded = false;
        // [2026-10-23] 요일 던전 -- 칸의 광석 등급과 상관없이 그날(KST) 요일의 돌 1개(월요석~일요석), 캔 칸 기록 등급은 11~17
        if (S5MineMap.DUNGEON.equals(map.id)) {
            int di = S5MineMap.dayIndex(now);
            g = S5MineMap.DAY_STONE_GRADE_BASE + di;
            code = S5MineMap.DAY_STONE_CODES[di];
            name = S5MineMap.DAY_STONE_NAMES[di];
        } else if (S5MineMap.SEAROAD.equals(map.id)) { // [2026-10-24] 바닷길 -- 보석 줄기 칸에서 보석만(캔 칸 기록 등급 21~29)
            int gi2 = S5MineMap.searoadGem(RND.nextInt(100));
            g = S5MineMap.GEM_GRADE_BASE + gi2;
            code = S5MineMap.GEM_CODES[gi2];
            name = S5MineMap.GEM_NAMES[gi2];
        } else {
            // [2026-10-24] 심연의 반지 -- 확률로 한 등급 위 광석(미스릴까지, 별빛석은 맵당 1칸 희귀성 유지를 위해 제외). 캔 칸 기록도 받은 등급으로.
            int upPct = MINE_ABYSS_UP_PCT[mineRingTier(items, MINE_ITEM_ABYSS_RING)];
            if (g < 5 && upPct > 0 && RND.nextInt(100) < upPct) {
                g++;
                upgraded = true;
            }
            code = S5MineMap.ORE_CODES[g - 1];
            name = S5MineMap.ORE_NAMES[g - 1];
        }
        // [2026-10-24] 풍요의 반지 -- 확률로 2개
        int dbl = MINE_PLENTY_DOUBLE_PCT[mineRingTier(items, MINE_ITEM_PLENTY_RING)];
        int gain = dbl > 0 && RND.nextInt(100) < dbl ? 2 : 1;
        dao.insertMineDug(userName, dugKey, y * map.w + x, g, now);
        dao.addUserOre(userName, code, gain);
        HashMap<String, Object> r = new HashMap<>();
        r.put("grade", g);
        r.put("oreCode", code);
        r.put("oreName", name);
        r.put("gain", gain);
        r.put("upgraded", upgraded);
        // [2026-10-24] 보석 -- 캘 때 확률로 지역 보석(맵별 2종) 또는 다이아몬드(모든 맵)가 함께 나온다(S5MineMap.gemFor). 바닷길은 이미 보석.
        //  사막의 목걸이는 보석 확률 배수(난수를 배수로 나눠 문턱 아래로 들어올 확률을 키운다, 다이아몬드 포함).
        int gi = S5MineMap.SEAROAD.equals(map.id) ? -1 : S5MineMap.gemFor(map.id, (int) (RND.nextInt(1000) / MINE_SAND_GEM_MULT[Math.min(items.getOrDefault(MINE_ITEM_SAND_NECKLACE, 0), MINE_SAND_GEM_MULT.length - 1)]));
        if (gi >= 0) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[gi], 1);
            r.put("gemGrade", S5MineMap.GEM_GRADE_BASE + gi);
            r.put("gemName", S5MineMap.GEM_NAMES[gi]);
        }
        // [2026-10-28] 설산 빙석 -- 1/2/3층에서 캘 때 함께. 처음 8/10/12%였다가 같은 날 "더 안 나오게(득템 느낌)" 1.5/2/2.5%(천분율)
        int icePm = S5MineMap.SNOW1.equals(map.id) ? 15 : S5MineMap.SNOW2.equals(map.id) ? 20 : S5MineMap.SNOW3.equals(map.id) ? 25 : 0;
        if (icePm > 0 && RND.nextInt(1000) < icePm) {
            dao.addUserOre(userName, S5MineMap.ICE_STONE_CODE, 1);
            r.put("iceGain", 1);
        }
        return r;
    }

    @Override
    public HashMap<String, Object> mineSavePos(String userName, String mapId, String ver, int x, int y) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = mineRow(userName, false);
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        boolean water = mineWaterWalk(items);
        int[] pos = minePos(row, map, water);
        HashMap<String, Object> out = new HashMap<>();
        out.put("mapName", map.name); // [2026-10-23] 퇴장 로그용
        boolean sameMap = (mapId == null || mapId.isEmpty() || map.id.equals(mapId)) && !mineStaleVersion(map, ver);
        if (sameMap && mineWalk(map, x, y, water) && mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("ok", true);
            out.put("x", x);
            out.put("y", y);
        } else {
            out.put("ok", false);
            out.put("reload", !sameMap);
            out.put("x", pos[0]);
            out.put("y", pos[1]);
        }
        return out;
    }

    /** 포탈 타기 -- (x, y)에 서서 바로 옆 포탈 칸(px, py)으로 들어간다. 잠긴 포탈이면 안내만, 열린 포탈이면 대상 맵/좌표로 옮기고 새 상태를 돌려준다. */
    @Override
    @Transactional
    public HashMap<String, Object> mineWarp(String userName, String mapId, String ver, int x, int y, int px, int py, String dest) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap map = mineMapOf(row);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        Map<String, Integer> items = mineItems(userName);
        boolean water = mineWaterWalk(items);
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        S5MineMap.Portal p = map.portalAt(px, py);
        if (p == null || Math.abs(x - px) + Math.abs(y - py) != 1 || !mineWalk(map, x, y, water)) {
            out.put("message", "입구 바로 앞에서 들어가세요.");
            return out;
        }
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        if (p.locked()) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("locked", true);
            out.put("message", p.label.contains("눈바람") ? "🌨️ " + p.label + "." : "🔒 " + p.label + " -- 아직 갈 수 없어요. (준비 중)"); // [2026-10-25] 설원 마을 문
            return out;
        }
        // [2026-10-24] 배(판자 B) -- 항구(바닷가/남쪽 섬/북쪽 끝 설원) 중 다른 곳을 골라 그 항구의 내리는 칸으로
        boolean ship = S5MineMap.SHIP.equals(p.target);
        if (ship && (dest == null || dest.equals(map.id) || !S5MineMap.SHIP_PORTS.contains(dest))) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("message", "⛵ 어디로 갈지 골라 주세요.");
            return out;
        }
        // [2026-10-24] 왕도 텔레포트 게이트(X) -- "대도시에서 다른 지역으로는 어렵게": 금광석 MINE_TELE_GOLD개를 내고 고른 지역의 시작점으로
        boolean tele = S5MineMap.TELE.equals(p.target);
        if (tele) {
            if (dest == null || !S5MineMap.TELE_DESTS.contains(dest)) {
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("message", "🌀 어디로 갈지 골라 주세요.");
                return out;
            }
            if (MINE_TELE_GOLD > 0 && dao.takeUserOre(userName, S5MineMap.ORE_CODES[3], MINE_TELE_GOLD) == 0) {
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("message", "🌀 텔레포트 게이트를 움직이려면 금광석 " + MINE_TELE_GOLD + "개가 필요해요.");
                return out;
            }
        }
        S5MineMap target = S5MineMap.get(ship || tele ? dest : p.target);
        if (S5MineMap.SEAROAD.equals(target.id) && !water) { // [2026-10-24] 바닷길은 용왕의 반지가 있어야 -- 없으면 그냥 물(아무 말 없이)
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("silent", true);
            return out;
        }
        // [2026-10-24] 초원/왕도는 관리자만이었다가 [2026-10-27] "왕도 일반 플레이어들에게 오픈" -- 막지 않는다.
        String block = mineMapBlock(target, items, mineLv(row));
        if (block != null) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("locked", true);
            out.put("message", block);
            return out;
        }
        int ax = ship ? target.dockX() : tele ? target.spawnX : p.tx, ay = ship ? target.dockY() : tele ? target.spawnY : p.ty;
        int tx = target.walkable(ax, ay) ? ax : target.spawnX, ty = target.walkable(ax, ay) ? ay : target.spawnY;
        dao.updateMinePos(userName, target.id, tx, ty, now);
        mineDisengageAll(userName); // [2026-10-25] 다른 맵으로 가면 인식 끝
        if (S5MineMap.SNOWTOWN.equals(target.id) && items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) < 1) // [2026-10-26] 설산을 넘어 처음 도착 -- 이제 이동 안내원으로 오갈 수 있다
            dao.upsertMineItem(userName, MINE_ITEM_VISIT_SNOWTOWN, 1);
        if (S5MineMap.CITY.equals(target.id) && items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) < 1) // [2026-10-27] 왕도도 걸어서 한 번 도착해야 이동 안내원 목적지
            dao.upsertMineItem(userName, MINE_ITEM_VISIT_CITY, 1);
        out.put("ok", true);
        out.put("message", (ship ? "⛵ 배를 타고 " + target.name + "에 도착했어요." : tele ? "🌀 텔레포트! " + target.name + "(으)로 왔어요." + (MINE_TELE_GOLD > 0 ? " (금광석 " + MINE_TELE_GOLD + "개 사용)" : "") : "🚪 " + target.name + "(으)로 이동했어요.")
                + (mineLv(row) < mineMinLv(target) ? " ⚠️ 권장 광산 레벨 " + mineMinLv(target) + " (지금 Lv." + mineLv(row) + ") -- 몬스터가 강하니 조심하세요!" : ""));
        out.put("state", mineState(userName));
        return out;
    }

    @Override
    public HashMap<String, Object> mineShop(String userName) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> row = mineRow(userName, false);
        Map<Integer, Integer> have = new HashMap<>();
        for (HashMap<String, Object> o : mineOreList(userName)) have.put(intVal(o.get("grade"), 0), intVal(o.get("qty"), 0));
        List<HashMap<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < MINE_SHOP.length; i++) {
            int g = MINE_SHOP[i][0], need = MINE_SHOP[i][1], add = MINE_SHOP[i][2];
            HashMap<String, Object> it = new HashMap<>();
            it.put("no", i + 1);
            it.put("grade", g);
            it.put("oreName", S5MineMap.ORE_NAMES[g - 1]);
            it.put("need", need);
            it.put("add", add);
            it.put("have", have.getOrDefault(g, 0));
            it.put("maxTimes", have.getOrDefault(g, 0) / need);
            items.add(it);
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("ppBonus", numVal(row.get("PP_BONUS"), 0));
        out.put("ores", mineOreList(userName));
        // [2026-10-24] 광부 투구 업그레이드는 광산상점 탭에서 빼고 광산 안 대장장이 NPC 창으로 옮겼다(mineState의 helmetInfo).
        return out;
    }

    /** [2026-10-24] 광부 투구 상태(대장장이 창용) -- 단계, 지금 열린 최고 단계, 다음 단계 비용(광석/개수/보유). */
    private HashMap<String, Object> mineHelmetInfo(String userName, int tier) {
        HashMap<String, Object> helmet = new HashMap<>();
        helmet.put("tier", tier);
        helmet.put("openMax", MINE_HELMET_OPEN_MAX);
        if (tier < MINE_HELMET_OPEN_MAX && tier < MINE_HELMET_COST.length) {
            int cg = MINE_HELMET_COST[tier][0], cn = MINE_HELMET_COST[tier][1];
            int have = 0;
            for (HashMap<String, Object> r : dao.selectUserOres(userName)) if (S5MineMap.ORE_CODES[cg - 1].equals(strVal(r.get("ORE_CODE"), ""))) have = intVal(r.get("QTY"), 0);
            helmet.put("nextTier", tier + 1);
            helmet.put("oreGrade", cg);
            helmet.put("oreName", S5MineMap.ORE_NAMES[cg - 1]);
            helmet.put("need", cn);
            helmet.put("have", have);
            if (tier + 1 > MINE_HELMET_FIELD_MAX) { // [2026-10-25] 왕도 대장장이 단계 -- 칠요석도
                Map<String, Integer> hv = mineOreHave(userName);
                helmet.put("cityOnly", true);
                helmet.put("extra", mineCostList(MINE_HELMET_EXTRA4, hv));
                helmet.put("extraOk", mineCostOk(MINE_HELMET_EXTRA4, hv));
            }
        }
        return helmet;
    }

    /** [2026-10-23] 광부 투구 구매/업그레이드 -- 다음 단계 비용(광석)을 내고 단계 +1. */
    @Override
    @Transactional
    public String mineHelmetUpgrade(String userName) {
        if (!mineAccess(userName)) return "⛏️ 광산은 아직 준비 중입니다.";
        mineRow(userName, false);
        HashMap<String, Object> hrow = dao.selectMineUserForUpdate(userName); // 같은 유저 동시 구매 직렬화
        int tier = mineItems(userName).getOrDefault(MINE_ITEM_HELMET, 0);
        boolean city4 = tier + 1 > MINE_HELMET_FIELD_MAX && tier < MINE_HELMET_OPEN_MAX; // [2026-10-25] 4단계 = 왕도 대장장이 + 칠요석
        if (city4 && !S5MineMap.CITY.equals(strVal(hrow.get("MAP_ID"), ""))) return "🔨 광부 투구 " + (tier + 1) + "단계는 왕도 아스테리아의 대장장이만 만들 수 있어요.";
        if (city4 && !mineCostOk(MINE_HELMET_EXTRA4, mineOreHave(userName))) return "재료가 부족해요. (" + mineCostText(MINE_HELMET_EXTRA4) + "도 필요)";
        if (tier >= MINE_HELMET_OPEN_MAX || tier >= MINE_HELMET_COST.length) return "⛑️ 광부 투구는 지금 최고 단계(" + tier + "단계)예요. 더 깊은 광산이 열리면 업그레이드할 수 있어요.";
        int cg = MINE_HELMET_COST[tier][0], cn = MINE_HELMET_COST[tier][1];
        String name = S5MineMap.ORE_NAMES[cg - 1];
        if (dao.takeUserOre(userName, S5MineMap.ORE_CODES[cg - 1], cn) == 0) return "광석이 부족해요. (" + name + " " + cn + "개 필요)";
        if (city4) mineTakeCost(userName, MINE_HELMET_EXTRA4, 1);
        dao.upsertMineItem(userName, MINE_ITEM_HELMET, tier + 1);
        if (city4) return "⛑️ 광부 투구 " + (tier + 1) + "단계! (" + name + " " + cn + "개, " + mineCostText(MINE_HELMET_EXTRA4) + " 사용) 동굴 3층까지 환하게 보여요.";
        return "⛑️ 광부 투구 " + (tier + 1) + "단계! (" + name + " " + cn + "개 사용) "
                + (tier + 1 >= 2 ? "광산 동굴 1층 전체가 잘 보여요." : "동굴이 훨씬 잘 보여요.");
    }

    @Override
    @Transactional
    public String mineShopBuy(String userName, int no, int times) {
        if (!mineAccess(userName)) return "⛏️ 광산은 아직 준비 중입니다.";
        if (no < 1 || no > MINE_SHOP.length) return "잘못된 상품입니다.";
        if (times < 1) times = 1;
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        int g = MINE_SHOP[no - 1][0], need = MINE_SHOP[no - 1][1] * times, add = MINE_SHOP[no - 1][2] * times;
        String code = S5MineMap.ORE_CODES[g - 1], name = S5MineMap.ORE_NAMES[g - 1];
        if (dao.takeUserOre(userName, code, need) == 0) return "광석이 부족해요. (" + name + " " + need + "개 필요)";
        dao.addMinePpBonus(userName, add);
        double total = numVal(row.get("PP_BONUS"), 0) + add;
        return "⛏️ " + name + " " + need + "개로 처치당 PP +" + add + "! (광산 수익 처치당 " + fmtPpBonus(total) + " PP, 탑 자동사냥으로 처치한 수만큼 정산 때 함께 지급)";
    }

    private static String fmtPpBonus(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    /** 자동사냥 정산 처치 수만큼의 광산 수익(처치당 PP_BONUS). 광산 기록이 없거나 보너스가 0이면 null. */
    private PP minePpBonus(String userName, long kills) {
        HashMap<String, Object> row = dao.selectMineUser(userName);
        double perKill = row == null ? 0 : numVal(row.get("PP_BONUS"), 0);
        if (perKill <= 0 || kills <= 0) return null;
        return PP.of(perKill * kills, "");
    }

    /** [2026-10-24] 관리자 전용 좌표 이동(MINE_ADMIN_USERS 또는 EVENT_ADMIN_USERS) -- 화면 좌표를 누르면 맵/좌표 입력. 못 서는 칸이면 가장 가까운 설 수 있는 칸으로. */
    @Override
    public HashMap<String, Object> mineAdminWarp(String userName, String mapId, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!isMineAdmin(userName)) { out.put("message", "권한이 없습니다."); return out; }
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        S5MineMap map = S5MineMap.get(mapId);
        boolean water = mineWaterWalk(mineItems(userName));
        int[] p = null;
        for (int r = 0; r <= 30 && p == null; r++) { // 가까운 칸부터(마름모 둘레)
            for (int dx = -r; dx <= r && p == null; dx++) {
                int dy = r - Math.abs(dx);
                if (mineWalk(map, x + dx, y + dy, water)) p = new int[] { x + dx, y + dy };
                else if (dy != 0 && mineWalk(map, x + dx, y - dy, water)) p = new int[] { x + dx, y - dy };
            }
        }
        if (p == null) { out.put("message", map.name + " (" + x + ", " + y + ") 근처에 설 수 있는 칸이 없어요."); return out; }
        dao.updateMinePos(userName, map.id, p[0], p[1], System.currentTimeMillis());
        out.put("ok", true);
        out.put("message", "🛠️ " + map.name + " (" + p[0] + ", " + p[1] + ")로 이동했어요." + (p[0] != x || p[1] != y ? " (입력한 칸에 설 수 없어 가장 가까운 칸으로)" : ""));
        out.put("state", mineState(userName));
        return out;
    }

    /** [2026-10-23] "마을로 이동" 버튼 -- 어느 맵 어디에 있든 중앙 평원 마을(시작점)으로 바로 옮긴다(이동 기록 검사 없음, 스태미너 소모 없음). */
    @Override
    public HashMap<String, Object> mineGoVillage(String userName) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        long now = System.currentTimeMillis();
        HashMap<String, Object> out = new HashMap<>();
        if (mineEngaged(userName, now)) { out.put("ok", false); out.put("message", MINE_FIGHT_BLOCK); return out; } // [2026-10-25]
        // [2026-10-25] "부활 거점을 골랐는데 마을 이동을 누르면 거점으로 안 간다" -- 성당에서 기도했으면 왕도 성당으로(mineRespawn과 같은 곳)
        String where = mineRespawn(userName, mineItems(userName), now);
        out.put("ok", true);
        out.put("message", "🏠 " + where + " 다시 시작해요.");
        out.put("state", mineState(userName));
        return out;
    }

    private static final String MINE_FIGHT_BLOCK = "⚔️ 몬스터가 쫓아오고 있어요! 5칸 넘게 떨어져 인식을 풀고 나서 이동할 수 있어요.";

    /** [2026-10-25] 이동 안내원 -- "마을 간 이동이 활성화되는 느낌, NPC로": 안내원 2칸 안에서 다른 안내원 맵(마을만: 평원 마을/왕도, 왕도는 아직 관리자만)의
     *  안내원 옆으로 무료로 옮긴다. 전투 중(몬스터 인식)엔 안 된다. */
    @Override
    @Transactional
    public HashMap<String, Object> mineGuide(String userName, String mapId, String ver, int x, int y, String dest) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap map = mineMapOf(row);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWaterWalk(items));
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        S5MineMap.Npc g = map.guide();
        if (g == null || Math.abs(g.x - x) + Math.abs(g.y - y) > 2) { out.put("message", "이동 안내원 바로 앞에서 말을 걸어 주세요."); return out; }
        if (dest == null || dest.equals(map.id) || !S5MineMap.GUIDE_MAPS.contains(dest)) { out.put("message", "🧭 어디로 갈지 골라 주세요."); return out; }
        if (S5MineMap.CITY.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) < 1 && !isMineAdmin(userName)) { out.put("message", "🧭 왕도는 초원이나 왕도 가는 길을 지나 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-10-27]
        if (S5MineMap.SNOWTOWN.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) < 1) { out.put("message", "🧭 설산 마을은 설산을 넘어 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-10-26]
        if (mineEngaged(userName, now)) { out.put("message", MINE_FIGHT_BLOCK); return out; }
        S5MineMap target = S5MineMap.get(dest);
        String block = mineMapBlock(target, items, mineLv(row));
        if (block != null) { out.put("message", block); return out; }
        S5MineMap.Npc tg = target.guide();
        int tx = target.spawnX, ty = target.spawnY;
        if (tg != null) {
            for (int[] dv : new int[][] { { 0, 1 }, { -1, 0 }, { 1, 0 }, { 0, -1 } }) if (target.walkable(tg.x + dv[0], tg.y + dv[1])) { tx = tg.x + dv[0]; ty = tg.y + dv[1]; break; }
        }
        dao.updateMinePos(userName, target.id, tx, ty, now);
        mineDisengageAll(userName);
        out.put("ok", true);
        out.put("message", "🧭 이동 안내원: " + S5MineMap.townName(target.id) + "에 도착했어요! 즐거운 여행 되세요."
                + (mineLv(row) < mineMinLv(target) ? " ⚠️ 권장 광산 레벨 " + mineMinLv(target) + " (지금 Lv." + mineLv(row) + ")" : ""));
        out.put("state", mineState(userName));
        return out;
    }

    // ---- [2026-10-26] 한기 / 눈바람 / 방한 외투 ----
    // "북쪽 끝 설원은 한기 1레벨로 체력이 초당 1%씩, 설산 1층 2% / 2층 3% / 3층 4%. 눈바람이 가끔 불어 한 번에 10%, 불기 5초 전 예고 + 안전지대로 가라고 안내,
    //  안전지대에서는 한기 없음. 설원 정박장(피해 없음)에 암상인 -- 방한 외투(받는 피해 2배, 한기 방어 1/2레벨 두 종류) 엄청 비싸게, 설산 마을도 비싸지만
    //  암상인은 그 세 배, 매시 정각~15분에만, 대화 중에도 사라질 수 있게" 요청.
    //  화면이 한기 맵에서 1초마다 mineColdTick을 부르면, 지난 부름 뒤 지난 시간(최대 3초)만큼 최대 체력의 (한기 - 외투 방어)%/초를 깎고,
    //  그 사이 눈바람(S5MineMap.blizzardBetween)이 불었는데 안전지대(map.safeAt) 밖이면 최대 체력 10%를 더 깎는다. 깎을 때마다 체력을 저장하므로
    //  추위 속에선 자연 회복도 없다(저장 + 대기). 추위로 쓰러지면 부활 지점에서 깨어난다(최대 체력 잠금은 몬스터에게 쓰러질 때만).
    static final String MINE_ITEM_VISIT_SNOWTOWN = "VISIT_SNOWTOWN";
    static final String MINE_ITEM_VISIT_CITY = "VISIT_CITY"; // [2026-10-27] 왕도에 한 번 걸어서 도착(이동 안내원 목적지)
    static final String MINE_ITEM_COAT1 = "COAT1";
    static final String MINE_ITEM_COAT2 = "COAT2";
    static final String MINE_ITEM_COAT_SLOT = "COAT_SLOT"; // 0 = 안 입음, 1 = 방한 외투, 2 = 두꺼운 방한 외투
    private static final int MINE_BLIZZARD_PCT = 10;
    private static final long MINE_COLD_MAX_GAP_MS = 3_000L;
    private static final int MINE_BLACK_MULT = 3;
    private static final int MINE_BLACK_OPEN_MIN = 15; // 매시 정각 ~ 15분
    /** 설산 마을 값(암상인은 MINE_BLACK_MULT배). */
    private static final String[][] MINE_COAT1_COST = { { S5MineMap.ORE_CODES[4], "20" }, { S5MineMap.ORE_CODES[3], "80" }, mineCost(8, 3) };
    private static final String[][] MINE_COAT2_COST = { { S5MineMap.ORE_CODES[4], "50" }, { S5MineMap.SEVEN_STONE_CODE, "3" }, mineCost(8, 8) };

    private static final class ColdState {
        volatile String mapVer;
        volatile long at;
        volatile double frac; // 1 미만으로 남은 한기 피해(다음 부름에 더한다)
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, ColdState> MINE_COLD = new java.util.concurrent.ConcurrentHashMap<>();

    /** 입고 있는 방한 외투의 한기 방어 단계(0 = 안 입음). 입으면 몬스터에게 받는 피해 2배. */
    private static int mineCoatDef(Map<String, Integer> items) {
        int slot = items.getOrDefault(MINE_ITEM_COAT_SLOT, 0);
        if (slot == 2 && items.getOrDefault(MINE_ITEM_COAT2, 0) > 0) return 2;
        if (slot == 1 && items.getOrDefault(MINE_ITEM_COAT1, 0) > 0) return 1;
        return 0;
    }

    // [2026-10-28] "설산에서 빙석이 발견되도록, 빙석을 모아 오면 방한 장갑을 주는 퀘스트" -- 털옷 장인 의뢰(GLOVES_QUEST: 1 = 받음, 2 = 끝냄).
    //  빙석 MINE_GLOVES_ICE개를 내면 방한 장갑(GLOVES): 한기 방어 +1(외투와 합쳐짐), 받는 피해는 그대로(외투처럼 2배가 아님).
    static final String MINE_ITEM_GLOVES = "GLOVES";
    static final String MINE_ITEM_GLOVES_QUEST = "GLOVES_QUEST";
    private static final int MINE_GLOVES_ICE = 10; // [2026-10-28] 빙석이 귀해져서 30 -> 10

    /** 한기 방어 합계 = 외투 + 방한 장갑. */
    private static int mineColdDef(Map<String, Integer> items) {
        return mineCoatDef(items) + (items.getOrDefault(MINE_ITEM_GLOVES, 0) > 0 ? 1 : 0);
    }

    private static boolean mineBlackOpen(long now) {
        return java.time.Instant.ofEpochMilli(now).atZone(KST).getMinute() < MINE_BLACK_OPEN_MIN;
    }

    /** 이번 암상인이 사라지는 시각(열려 있을 때) 또는 다음에 나타나는 시각(닫혀 있을 때). */
    private static long mineBlackNextChange(long now) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(now).atZone(KST).withSecond(0).withNano(0);
        return mineBlackOpen(now) ? z.withMinute(MINE_BLACK_OPEN_MIN).toInstant().toEpochMilli() : z.withMinute(0).plusHours(1).toInstant().toEpochMilli();
    }

    private static String[][] mineCostTimes(String[][] cost, int mult) {
        String[][] out = new String[cost.length][];
        for (int i = 0; i < cost.length; i++) out[i] = new String[] { cost[i][0], String.valueOf(Integer.parseInt(cost[i][1]) * mult) };
        return out;
    }

    /** 한기 턴 -- 한기 맵에서 화면이 1초마다. 결과: cold(맵 한기), coldEff(방어 뺀 한기), safe, nextBlizzardAt, (coldDmg, blizzardDmg, combat, died, state). */
    @Override
    @Transactional
    public HashMap<String, Object> mineColdTick(String userName, String mapId, String ver, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName) || mineGate() != null) return out;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) return out;
        Map<String, Integer> items = mineItems(userName);
        if (!mineMoveOk(minePos(row, map, mineWaterWalk(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        int def = mineColdDef(items), eff = Math.max(0, map.cold() - def); // [2026-10-28] 외투 + 방한 장갑
        boolean safe = map.safeAt(x, y), raid = S5MineMap.RUINS.equals(map.id);
        out.put("ok", true);
        out.put("cold", map.cold());
        out.put("coldEff", eff);
        out.put("coatDef", def);
        out.put("safe", safe);
        if (map.cold() <= 0 && !raid) { MINE_COLD.remove(userName); return out; }
        if (map.cold() > 0) out.put("nextBlizzardAt", S5MineMap.nextBlizzard(map.id, now));
        ColdState cs = MINE_COLD.computeIfAbsent(userName, u -> new ColdState());
        long from = map.version().equals(cs.mapVer) ? Math.max(cs.at, now - MINE_COLD_MAX_GAP_MS) : now;
        cs.mapVer = map.version();
        cs.at = now;
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), lv = mineLv(row), cap = mineHpCap(lv, lock);
        int coldDmg = 0, bzDmg = 0, fireDmg = 0, gasDmg = 0;
        if (map.cold() > 0) {
            if (safe) cs.frac = 0;
            else {
                double drain = cap * eff / 100.0 * Math.max(0, now - from) / 1000.0 + cs.frac;
                coldDmg = (int) drain;
                cs.frac = drain - coldDmg;
            }
            if (S5MineMap.blizzardBetween(map.id, from, now) > 0) {
                if (safe) out.put("blizzardSafe", true);
                else bzDmg = Math.max(1, cap * MINE_BLIZZARD_PCT / 100);
            }
        }
        if (raid) { // [2026-10-28] 대주교 마법 -- 깨어 있을 때(누가 싸우는 중)만. 불벼락 = 대주교 12칸 안, 어둠가스 = 맵 전체
            S5MineMap.Mob boss = mineBossOf(map);
            MobState bs = boss == null ? null : MINE_MOB_STATE.get(mineMobKey(map, boss.id));
            boolean awake = boss != null && !mineBossDead(boss.type) && (bs == null || bs.deadUntil <= now) && mineRaidAwakeUntil > now;
            out.put("raidAwake", awake);
            if (awake) {
                long nf = mineRaidNext(MINE_RAID_FIRE_MS, MINE_RAID_FIRE_OFF, from), ng = mineRaidNext(MINE_RAID_GAS_MS, MINE_RAID_GAS_OFF, from);
                out.put("nextFireAt", mineRaidNext(MINE_RAID_FIRE_MS, MINE_RAID_FIRE_OFF, now));
                out.put("nextGasAt", mineRaidNext(MINE_RAID_GAS_MS, MINE_RAID_GAS_OFF, now));
                int atk = S5MineMobs.TYPES.get(S5MineMobs.RAID_BOSS_TYPE).atk() * (mineCoatDef(items) > 0 ? 2 : 1);
                int bx = bs != null && bs.frozenUntil > now ? bs.fx : boss.x, by = bs != null && bs.frozenUntil > now ? bs.fy : boss.y;
                if (nf <= now) {
                    out.put("fireAt", nf);
                    if (Math.max(Math.abs(x - bx), Math.abs(y - by)) <= MINE_RAID_FIRE_RANGE) fireDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 2.5);
                }
                if (ng <= now) { out.put("gasAt", ng); gasDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 1.2); }
            }
        }
        if (coldDmg + bzDmg + fireDmg + gasDmg <= 0) return out;
        HashMap<String, Object> lrow = dao.selectMineUserForUpdate(userName); // 같은 유저의 공격/전투 턴과 겹치지 않게 잠그고 다시 읽는다
        long exp = Math.max(0, longOf(lrow.get("M_EXP"), 0L));
        int hp = mineHpNow(lrow, now, lock) - coldDmg - bzDmg - fireDmg - gasDmg;
        if (coldDmg > 0) out.put("coldDmg", coldDmg);
        if (bzDmg > 0) out.put("blizzardDmg", bzDmg);
        if (fireDmg > 0) out.put("fireDmg", fireDmg);
        if (gasDmg > 0) out.put("gasDmg", gasDmg);
        if (hp <= 0) {
            boolean spell = fireDmg + gasDmg > 0;
            if (spell) lock = mineLockUp(userName, lock, out); // 대주교 마법 = 전투 사망(최대 체력 잠금)
            hp = mineHpCap(lv, lock);
            mineDisengageAll(userName);
            MINE_COLD.remove(userName);
            out.put("died", true);
            out.put("message", spell ? "💀 타락한 대주교의 " + (fireDmg > 0 ? "불벼락" : "어둠가스") + "에 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out)
                    : "🥶 " + (bzDmg > 0 ? "눈바람에 휩쓸려" : "추위를 견디지 못하고") + " 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요.");
        }
        dao.updateMineCombat(userName, lv, exp, hp, now, 0);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, mineEngaged(userName, now), lock));
        if (out.containsKey("died")) out.put("state", mineState(userName));
        return out;
    }

    /** 방한 외투 -- action: list(보기) / buy(code) / wear(code, 다시 누르면 벗기). 파는 곳은 저장 위치 맵으로 정한다:
     *  설산 마을 = 털옷 장인(제값), 북쪽 끝 설원 = 암상인(세 배, 매시 정각~15분에만). */
    @Override
    @Transactional
    public HashMap<String, Object> mineCoat(String userName, String action, String code) {
        if (!mineAccess(userName)) return mineClosed();
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        String mapId = strVal(row.get("MAP_ID"), "");
        String seller = S5MineMap.SNOWTOWN.equals(mapId) ? "town" : S5MineMap.NORTH.equals(mapId) ? "black" : "";
        Map<String, Integer> items = mineItems(userName);
        String msg = null;
        boolean ok = true;
        String item = MINE_ITEM_COAT1.equals(code) ? MINE_ITEM_COAT1 : MINE_ITEM_COAT2.equals(code) ? MINE_ITEM_COAT2 : null;
        int slot = MINE_ITEM_COAT1.equals(item) ? 1 : 2;
        String nm = MINE_ITEM_COAT1.equals(item) ? "방한 외투" : "두꺼운 방한 외투";
        if ("buy".equals(action) && item != null) {
            if (seller.isEmpty()) { ok = false; msg = "방한 외투는 설산 마을 털옷 장인이나 설원 정박장 암상인에게서만 살 수 있어요."; }
            else if ("black".equals(seller) && !mineBlackOpen(now)) { ok = false; msg = "🕶️ 암상인이 어느새 사라졌어요... 매시 정각부터 15분까지만 나타나요."; }
            else if (items.getOrDefault(item, 0) > 0) { ok = false; msg = nm + "은(는) 이미 가지고 있어요."; }
            else {
                String[][] cost = mineCostTimes(MINE_ITEM_COAT1.equals(item) ? MINE_COAT1_COST : MINE_COAT2_COST, "black".equals(seller) ? MINE_BLACK_MULT : 1);
                if (!mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "재료가 부족해요. (" + mineCostText(cost) + " 필요)"; }
                else {
                    mineTakeCost(userName, cost, 1);
                    dao.upsertMineItem(userName, item, 1);
                    dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, slot);
                    items.put(item, 1);
                    items.put(MINE_ITEM_COAT_SLOT, slot);
                    msg = "🧥 " + nm + "을(를) 사서 입었어요! 한기 방어 " + slot + "단계 (몬스터에게 받는 피해 2배) -- " + mineCostText(cost) + " 사용";
                }
            }
        } else if ("quest".equals(action) || "gloves".equals(action)) { // [2026-10-28] 털옷 장인 의뢰 -- 빙석을 모아 오면 방한 장갑
            int qs = items.getOrDefault(MINE_ITEM_GLOVES_QUEST, 0);
            if (!"town".equals(seller)) { ok = false; msg = "털옷 장인의 의뢰는 설산 마을에서만 받을 수 있어요."; }
            else if ("quest".equals(action)) {
                if (qs >= 1) { ok = false; msg = qs >= 2 ? "이미 방한 장갑을 받았어요." : "이미 의뢰를 받았어요. 빙석 " + MINE_GLOVES_ICE + "개를 모아 오세요."; }
                else {
                    dao.upsertMineItem(userName, MINE_ITEM_GLOVES_QUEST, 1);
                    items.put(MINE_ITEM_GLOVES_QUEST, 1);
                    msg = "🧤 털옷 장인: 설산 어딘가 얼음 속에 빙석이 박혀 있지. " + MINE_GLOVES_ICE + "개만 캐 오면 손이 얼지 않는 장갑을 떠 주겠네.";
                }
            } else if (qs != 1) { ok = false; msg = qs >= 2 ? "이미 방한 장갑을 받았어요." : "먼저 의뢰를 받아 주세요."; }
            else {
                String[][] cost = { { S5MineMap.ICE_STONE_CODE, String.valueOf(MINE_GLOVES_ICE) } };
                if (!mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "빙석이 모자라요. (" + MINE_GLOVES_ICE + "개 필요)"; }
                else {
                    mineTakeCost(userName, cost, 1);
                    dao.upsertMineItem(userName, MINE_ITEM_GLOVES, 1);
                    dao.upsertMineItem(userName, MINE_ITEM_GLOVES_QUEST, 2);
                    items.put(MINE_ITEM_GLOVES, 1);
                    items.put(MINE_ITEM_GLOVES_QUEST, 2);
                    msg = "🧤 방한 장갑을 받았어요! 한기 방어 +1 (외투와 함께 적용, 받는 피해는 그대로)";
                }
            }
        } else if ("wear".equals(action) && item != null) {
            if (items.getOrDefault(item, 0) < 1) { ok = false; msg = nm + "이(가) 없어요."; }
            else {
                boolean on = items.getOrDefault(MINE_ITEM_COAT_SLOT, 0) != slot;
                dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, on ? slot : 0);
                items.put(MINE_ITEM_COAT_SLOT, on ? slot : 0);
                msg = on ? "🧥 " + nm + "을(를) 입었어요. 한기 방어 " + slot + "단계, 받는 피해 2배." : "🧥 외투를 벗었어요.";
            }
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", ok);
        if (msg != null) out.put("message", msg);
        out.put("seller", seller);
        out.put("blackOpen", mineBlackOpen(now));
        out.put("blackNextChange", mineBlackNextChange(now));
        out.put("coatDef", mineCoatDef(items));
        Map<String, Integer> have = mineOreHave(userName);
        List<HashMap<String, Object>> coats = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            String c = i == 1 ? MINE_ITEM_COAT1 : MINE_ITEM_COAT2;
            String[][] cost = mineCostTimes(i == 1 ? MINE_COAT1_COST : MINE_COAT2_COST, "black".equals(seller) ? MINE_BLACK_MULT : 1);
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", c);
            m.put("name", i == 1 ? "방한 외투" : "두꺼운 방한 외투");
            m.put("def", i);
            m.put("effect", "한기 방어 " + i + "단계 · 몬스터에게 받는 피해 2배");
            m.put("owned", items.getOrDefault(c, 0) > 0);
            m.put("worn", mineCoatDef(items) == i);
            m.put("costs", mineCostList(cost, have));
            m.put("can", mineCostOk(cost, have));
            coats.add(m);
        }
        out.put("coats", coats);
        out.put("gloveQuest", items.getOrDefault(MINE_ITEM_GLOVES_QUEST, 0)); // [2026-10-28] 털옷 장인 의뢰
        out.put("hasGloves", items.getOrDefault(MINE_ITEM_GLOVES, 0) > 0);
        out.put("iceHave", have.getOrDefault(S5MineMap.ICE_STONE_CODE, 0));
        out.put("iceNeed", MINE_GLOVES_ICE);
        out.put("coldDef", mineColdDef(items));
        out.put("ores", mineOreList(userName));
        return out;
    }

    /** [2026-10-23] 대장간 -- 평원 마을 대장장이에게 광석(grade)을 주고 곡괭이를 강화한다(최대/현재 내구도 함께 증가). times = 몇 번(보유 광석만큼으로 줄인다). */
    @Override
    @Transactional
    public HashMap<String, Object> minePickUpgrade(String userName, int grade, int times) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        int dur = minePickDur(row), max = minePickMax(row);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        putMinePick(out, dur, max);
        int[] rep = null;
        for (int[] r : MINE_PICK_UPGRADE) if (r[0] == grade) rep = r;
        if (rep == null) {
            out.put("message", "그 광석으로는 강화할 수 없어요.");
            return out;
        }
        String code = S5MineMap.ORE_CODES[grade - 1], name = S5MineMap.ORE_NAMES[grade - 1];
        int have = 0;
        for (HashMap<String, Object> r : dao.selectUserOres(userName)) if (code.equals(strVal(r.get("ORE_CODE"), ""))) have = intVal(r.get("QTY"), 0);
        int limit = Math.min(Math.max(1, times), have / rep[1]);
        if (limit < 1) {
            out.put("message", "광석이 부족해요. (" + name + " " + rep[1] + "개 필요, 보유 " + have + "개)");
            return out;
        }
        // [2026-10-24] 최대 내구도는 MINE_PICK_CAP까지 -- 넘치는 횟수는 쓰지 않는다(광석도 안 받음)
        if (max >= MINE_PICK_CAP) {
            out.put("message", "🔨 곡괭이가 이미 최대치예요. (최대 내구도 " + MINE_PICK_CAP + ", 지금 " + dur + " / " + max + ")");
            return out;
        }
        int n = Math.min(limit, (MINE_PICK_CAP - max + rep[2] - 1) / rep[2]);
        if (dao.takeUserOre(userName, code, rep[1] * n) == 0) {
            out.put("message", "광석이 부족해요. (" + name + " " + rep[1] * n + "개 필요)");
            return out;
        }
        int gain = Math.min(rep[2] * n, MINE_PICK_CAP - max), newMax = max + gain, newDur = dur + gain;
        dao.addMinePickMax(userName, gain);
        putMinePick(out, newDur, newMax);
        out.put("ok", true);
        out.put("ores", mineOreList(userName));
        out.put("message", "🔨 대장장이가 곡괭이를 강화해 줬어요! " + name + " " + rep[1] * n + "개 → 최대 내구도 +" + gain
                + " (지금 " + newDur + " / " + newMax + (newMax >= MINE_PICK_CAP ? ", 최대치" : "") + ")");
        return out;
    }

    // ---- [2026-10-24] 광산 장비(보석 장신구, 신속의 장화, 용왕의 반지), 칠요석 합성, 선장 퀘스트 ----
    // "보석은 장신구 세공(영구 효과: 2개 캘 확률 / 보석 확률 / 고등급 광석 확률), 다이아몬드로 이동 속도 강화,
    //  용왕의 반지는 물속 진입 대신 물 위를 걷기, 요일석은 칠요석 합성만, 선장은 금광석 10개를 받고 퀘스트를 주되 안 알려주고 먹튀할 수도" 요청.
    //  장신구/장화는 대장장이 [구매]에서 만들고 올린다(단계 = TBOT_S5_MINE_ITEM.ITEM_LEVEL). 반지는 하나만 낀다(RING_SLOT: 1 풍요 / 2 심연),
    //  목걸이/장화는 가지고 있으면 바로 적용. 용왕의 반지는 착용 칸 없이 가지고만 있으면 된다(DRAGON_QUEST: 1 = 선장에게 이야기를 들음, 2 = 반지 받음).
    static final String MINE_ITEM_PLENTY_RING = "PLENTY_RING";
    static final String MINE_ITEM_ABYSS_RING = "ABYSS_RING";
    static final String MINE_ITEM_SAND_NECKLACE = "SAND_NECKLACE";
    static final String MINE_ITEM_SWIFT_BOOTS = "SWIFT_BOOTS";
    static final String MINE_ITEM_RING_SLOT = "RING_SLOT";
    static final String MINE_ITEM_DRAGON_QUEST = "DRAGON_QUEST";
    /** [2026-10-24] 칠요석 곡괭이 -- "주변 3x3칸을 한 번에 캐는 곡괭이(다이아몬드 + 칠요석 여러 개)", "주변 3x3에 어떤 광물이 있는지 미리 보여 주는 곡괭이",
     *  "구매 후엔 변경 자유롭게". 곡괭이 칸 PICK_SLOT: 0 = 기본, 1 = 광역 곡괭이, 2 = 탐지 곡괭이(대장장이 [구매]에서 언제든 바꿔 낀다). */
    static final String MINE_ITEM_WIDE_PICK = "WIDE_PICK";
    static final String MINE_ITEM_DETECT_PICK = "DETECT_PICK";
    /** [2026-10-25] 쌍날 곡괭이 -- 바라보는 방향 앞뒤 칸도 함께(PICK_SLOT 3). */
    static final String MINE_ITEM_LINE_PICK = "LINE_PICK";
    static final String MINE_ITEM_PICK_SLOT = "PICK_SLOT";
    /** 탐지 곡괭이가 빛을 잃은 주기 + 1("별빛석을 캐면 효과 꺼짐, 맵 리셋 때 회복"). 지금 주기 + 1과 같으면 꺼짐. */
    static final String MINE_ITEM_DETECT_OFF = "DETECT_OFF";

    private static boolean mineDetectDim(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_DETECT_OFF, 0) == S5MineMap.activeCycle() + 1;
    }

    private static int minePickSlotOf(String code) {
        return MINE_ITEM_WIDE_PICK.equals(code) ? 1 : MINE_ITEM_DETECT_PICK.equals(code) ? 2 : MINE_ITEM_LINE_PICK.equals(code) ? 3 : 0;
    }
    /** 단계별 효과(인덱스 = 단계, 0 = 없음). 장화 = 화면 한 칸 이동 시간(ms, 기본 140 -> 약 +11% / +25% / +40%). */
    //  [2026-10-25] 왕도 대장장이 단계 추가: 반지/목걸이 3단계, 장화 4단계(88ms = 약 +60%).
    private static final int[] MINE_PLENTY_DOUBLE_PCT = { 0, 3, 8, 15 };
    private static final int[] MINE_ABYSS_UP_PCT = { 0, 2, 5, 10 };
    private static final double[] MINE_SAND_GEM_MULT = { 1.0, 1.3, 1.6, 2.2 };
    private static final int[] MINE_STEP_MS = { 140, 126, 112, 100, 88 };
    /** 선장이 금광석만 받고 아무것도 안 알려줄 확률(%). */
    private static final int MINE_CAPTAIN_SCAM_PCT = 40;
    private static final int MINE_CAPTAIN_GOLD = 10;
    private static final String[] MINE_CAPTAIN_SCAM_LINES = {
        "허허, 고맙네. 신기한 거? ...아, 그건 다음에 보여주지.",
        "(선장이 금광석을 주머니에 넣더니 먼 바다만 바라본다.)",
        "어이쿠, 배에 물이 새는군! 신기한 건 다음에 보자고!",
        "신기하지? 금광석이 사라졌잖나. 껄껄껄.",
        "음... 내가 그런 말을 했던가? 요즘 깜빡깜빡해서 말이야."
    };
    private static final String MINE_CAPTAIN_STORY = "좋아, 보여주지. 이건 바다 밑 용왕이 끼던 반지 이야기라네. 그 반지를 낀 자는 물 위를 땅처럼 걸었다더군. "
            + "아쿠아마린 30개, 스타 사파이어 5개, 칠요석 1개, 다이아몬드 1개를 가져오면 내가 그 반지를 만들어 주지.";

    private static String[] mineCost(int gemIdx, int qty) {
        return new String[] { S5MineMap.GEM_CODES[gemIdx], String.valueOf(qty) };
    }

    private static final class MineGear {
        final String code, name, kind;
        final String[][][] cost; // [목표 단계 - 1][재료] = {코드, 개수}
        final String[] effect;   // [단계 - 1]
        final int fieldMax;      // [2026-10-25] 마을 대장장이가 만드는 마지막 단계 -- 그 위 단계는 왕도 대장장이만
        MineGear(String code, String name, String kind, String[][][] cost, String[] effect) {
            this(code, name, kind, cost, effect, cost.length);
        }
        MineGear(String code, String name, String kind, String[][][] cost, String[] effect, int fieldMax) {
            this.code = code; this.name = name; this.kind = kind; this.cost = cost; this.effect = effect; this.fieldMax = fieldMax;
        }
    }

    /** [2026-10-25] 별빛석 n개(재료). */
    private static String[] mineStar(int qty) {
        return new String[] { S5MineMap.ORE_CODES[5], String.valueOf(qty) };
    }

    /** [2026-10-25] 칠요석 n개(재료) -- "고급템은 칠요석이 들어가게". 칠요석 = 요일석 7종 + 별빛석(mineSynthSeven). */
    private static String[] mineSeven(int qty) {
        return new String[] { S5MineMap.SEVEN_STONE_CODE, String.valueOf(qty) };
    }

    // [2026-10-25] "성장이 빨라 상점 아이템을 너무 빨리 달성 -- 1단계 구매는 그대로, 2단계부터 비싸게" + "별빛석 필요처" 요청:
    //  라이브 확인(10-25) 2일 만에 상위 유저가 1단계 장신구/장화를 다 샀고 2단계(희귀 보석 5 + 다이아 1)도 하루치였다.
    //  2단계부터 희귀 보석 5 -> 25, 다이아 1 -> 4, 별빛석 1 추가(장신구), 장화 2단계 다이아 2 -> 6 + 미스릴 10, 3단계 다이아 3 -> 12 + 별빛석 2로 올렸다가 같은 날
    //  "이미 단계업한 사람이 있으면 그렇게 올리면 안 된다" -- 이미 누가 산 단계(장신구 2단계, 장화 2단계)는 원래 값으로 되돌리고 아무도 없는 장화 3단계만 올린 값 유지.
    // [2026-10-25] "지금보다 더 높은 업그레이드는 대도시 대장장이에서, 더 큰 폭의 아이템, 고급템은 칠요석" -- 반지/목걸이 3단계, 장화 4단계는 왕도 대장장이만(fieldMax),
    //  효과 폭을 크게(2개 8% -> 15%, 한 등급 위 5% -> 10%, 보석 1.6배 -> 2.2배, 이동 +40% -> +60%) 하고 재료에 칠요석.
    //  "곡괭이는 모든 대장장이 공통, 앞뒤가 같이 캐지는 곡괭이(칠요석 하나)" -- 쌍날 곡괭이(PICK_SLOT 3).
    private static final List<MineGear> MINE_GEAR = java.util.Arrays.asList(
        new MineGear(MINE_ITEM_PLENTY_RING, "풍요의 반지", "ring",
            new String[][][] { { mineCost(0, 20) }, { mineCost(7, 5), mineCost(8, 1) }, { mineCost(7, 40), mineCost(8, 5), mineSeven(2) } },
            new String[] { "캘 때 2개 나올 확률 3%", "캘 때 2개 나올 확률 8%", "캘 때 2개 나올 확률 15%" }, 2),
        new MineGear(MINE_ITEM_ABYSS_RING, "심연의 반지", "ring",
            new String[][][] { { mineCost(6, 20) }, { mineCost(1, 5), mineCost(8, 1) }, { mineCost(1, 40), mineCost(8, 5), mineSeven(2) } },
            new String[] { "한 등급 위 광석이 나올 확률 2% (미스릴까지)", "한 등급 위 광석이 나올 확률 5% (미스릴까지)", "한 등급 위 광석이 나올 확률 10% (미스릴까지)" }, 2),
        new MineGear(MINE_ITEM_SAND_NECKLACE, "사막의 목걸이", "necklace",
            new String[][][] { { mineCost(4, 20) }, { mineCost(5, 5), mineCost(8, 1) }, { mineCost(5, 40), mineCost(8, 5), mineSeven(2) } },
            new String[] { "보석이 나올 확률 1.3배", "보석이 나올 확률 1.6배", "보석이 나올 확률 2.2배" }, 2),
        new MineGear(MINE_ITEM_SWIFT_BOOTS, "신속의 장화", "boots",
            new String[][][] { { mineCost(8, 1) }, { mineCost(8, 2) }, { mineCost(8, 12), mineStar(2) }, { mineCost(8, 15), mineSeven(3) } },
            new String[] { "이동 속도 +11%", "이동 속도 +25%", "이동 속도 +40%", "이동 속도 +60%" }, 3),
        new MineGear(MINE_ITEM_WIDE_PICK, "광역 곡괭이", "tool",
            new String[][][] { { mineCost(8, 3), mineSeven(5) } },
            new String[] { "캘 때 주변 3x3칸도 함께 (칸마다 내구도 1)" }),
        new MineGear(MINE_ITEM_DETECT_PICK, "탐지 곡괭이", "tool",
            new String[][][] { { mineCost(8, 8), mineSeven(10), { S5MineMap.ORE_CODES[4], "10" } } }, // "많이 비싸게"
            new String[] { "서 있는 곳 주변 3x3칸의 광석이 미리 보여요 (별빛석을 캐면 빛을 잃고 맵 리셋 때 다시 빛나요)" }),
        new MineGear(MINE_ITEM_LINE_PICK, "쌍날 곡괭이", "tool",
            new String[][][] { { mineSeven(1) } },
            new String[] { "캘 때 바라보는 방향의 앞 칸과 뒤 칸도 함께 (칸마다 내구도 1)" })
    );

    private static final String[][] MINE_DRAGON_RING_COST = {
        mineCost(3, 30), mineCost(2, 5), { S5MineMap.SEVEN_STONE_CODE, "1" }, mineCost(8, 1)
    };

    private static MineGear mineGear(String code) {
        for (MineGear g : MINE_GEAR) if (g.code.equals(code)) return g;
        return null;
    }

    /** 끼고 있는 반지면 그 단계, 아니면 0. */
    private static int mineRingTier(Map<String, Integer> items, String ringCode) {
        int slot = items.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        boolean worn = (slot == 1 && MINE_ITEM_PLENTY_RING.equals(ringCode)) || (slot == 2 && MINE_ITEM_ABYSS_RING.equals(ringCode));
        return worn ? Math.min(items.getOrDefault(ringCode, 0), MINE_PLENTY_DOUBLE_PCT.length - 1) : 0;
    }

    private static int mineStepMs(Map<String, Integer> items) {
        return MINE_STEP_MS[Math.min(items.getOrDefault(MINE_ITEM_SWIFT_BOOTS, 0), MINE_STEP_MS.length - 1)];
    }

    private static boolean mineWaterWalk(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_DRAGON_RING, 0) > 0;
    }

    /** 재료 코드 -> 화면 등급 값(아이콘)과 이름. */
    private static int mineCodeGrade(String code) {
        for (int i = 0; i < S5MineMap.ORE_CODES.length; i++) if (S5MineMap.ORE_CODES[i].equals(code)) return i + 1;
        for (int i = 0; i < S5MineMap.DAY_STONE_CODES.length; i++) if (S5MineMap.DAY_STONE_CODES[i].equals(code)) return S5MineMap.DAY_STONE_GRADE_BASE + i;
        if (S5MineMap.SEVEN_STONE_CODE.equals(code)) return S5MineMap.SEVEN_STONE_GRADE;
        if (S5MineMap.ICE_STONE_CODE.equals(code)) return S5MineMap.ICE_STONE_GRADE; // [2026-10-28]
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) if (S5MineMap.GEM_CODES[i].equals(code)) return S5MineMap.GEM_GRADE_BASE + i;
        if (S5MineMap.SEAL_CODE.equals(code)) return S5MineMap.SEAL_GRADE;
        return 0;
    }

    private static String mineCodeName(String code) {
        int g = mineCodeGrade(code);
        if (g == S5MineMap.ICE_STONE_GRADE) return S5MineMap.ICE_STONE_NAME; // [2026-10-28]
        if (g == S5MineMap.SEAL_GRADE) return S5MineMap.SEAL_NAME;
        if (g >= S5MineMap.GEM_GRADE_BASE) return S5MineMap.GEM_NAMES[g - S5MineMap.GEM_GRADE_BASE];
        if (g == S5MineMap.SEVEN_STONE_GRADE) return S5MineMap.SEVEN_STONE_NAME;
        return mineGradeName(g);
    }

    private Map<String, Integer> mineOreHave(String userName) {
        Map<String, Integer> have = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectUserOres(userName)) have.put(strVal(r.get("ORE_CODE"), ""), intVal(r.get("QTY"), 0));
        return have;
    }

    /** 재료 목록(화면용) -- [{grade, name, need, have}]. */
    private static List<HashMap<String, Object>> mineCostList(String[][] cost, Map<String, Integer> have) {
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (String[] c : cost) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("grade", mineCodeGrade(c[0]));
            m.put("name", mineCodeName(c[0]));
            m.put("need", Integer.parseInt(c[1]));
            m.put("have", have.getOrDefault(c[0], 0));
            out.add(m);
        }
        return out;
    }

    private static boolean mineCostOk(String[][] cost, Map<String, Integer> have) {
        for (String[] c : cost) if (have.getOrDefault(c[0], 0) < Integer.parseInt(c[1])) return false;
        return true;
    }

    private static String mineCostText(String[][] cost) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : cost) sb.append(sb.length() > 0 ? ", " : "").append(mineCodeName(c[0])).append(' ').append(c[1]).append("개");
        return sb.toString();
    }

    /** 재료를 낸다 -- 호출 전에 유저 행을 잠그고(selectMineUserForUpdate) mineCostOk로 확인했으므로 실패하면 롤백(예외). */
    private void mineTakeCost(String userName, String[][] cost, int times) {
        for (String[] c : cost) {
            if (dao.takeUserOre(userName, c[0], Integer.parseInt(c[1]) * times) == 0) throw new IllegalStateException("재료가 부족해요.");
        }
    }

    /** 장비 화면 데이터(장신구/장화 목록, 낀 반지, 이동 속도, 물 위 걷기, 선장 퀘스트) + 광석 목록을 out에 넣는다. */
    private void mineGearPayload(String userName, Map<String, Integer> items, HashMap<String, Object> out) {
        Map<String, Integer> have = mineOreHave(userName);
        int slot = items.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        List<HashMap<String, Object>> gear = new ArrayList<>();
        for (MineGear g : MINE_GEAR) {
            int tier = Math.min(items.getOrDefault(g.code, 0), g.cost.length);
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", g.code);
            m.put("name", g.name);
            m.put("kind", g.kind);
            m.put("tier", tier);
            m.put("maxTier", g.cost.length);
            m.put("effect", tier > 0 ? g.effect[tier - 1] : "");
            if ("tool".equals(g.kind)) m.put("on", tier > 0 && items.getOrDefault(MINE_ITEM_PICK_SLOT, 0) == minePickSlotOf(g.code)); // 끼고 있는 곡괭이
            if (MINE_ITEM_DETECT_PICK.equals(g.code)) m.put("dim", mineDetectDim(items)); // 빛을 잃음(맵 리셋 때 회복)
            m.put("worn", "ring".equals(g.kind) ? (slot == 1 && MINE_ITEM_PLENTY_RING.equals(g.code)) || (slot == 2 && MINE_ITEM_ABYSS_RING.equals(g.code)) : tier > 0);
            m.put("fieldMax", g.fieldMax); // [2026-10-25] 이 단계까지 마을 대장장이, 그 위는 왕도 대장장이
            if (tier < g.cost.length) {
                m.put("nextTier", tier + 1);
                m.put("cityOnly", tier + 1 > g.fieldMax);
                m.put("nextEffect", g.effect[tier]);
                m.put("costs", mineCostList(g.cost[tier], have));
                m.put("can", mineCostOk(g.cost[tier], have));
            }
            gear.add(m);
        }
        out.put("gear", gear);
        out.put("stepMs", mineStepMs(items));
        out.put("waterWalk", mineWaterWalk(items));
        HashMap<String, Object> dragon = new HashMap<>();
        int quest = items.getOrDefault(MINE_ITEM_DRAGON_QUEST, 0);
        dragon.put("quest", quest);
        dragon.put("has", mineWaterWalk(items));
        dragon.put("gold", have.getOrDefault(S5MineMap.ORE_CODES[3], 0));
        dragon.put("goldNeed", MINE_CAPTAIN_GOLD);
        if (quest >= 1 && !mineWaterWalk(items)) {
            dragon.put("story", MINE_CAPTAIN_STORY);
            dragon.put("costs", mineCostList(MINE_DRAGON_RING_COST, have));
            dragon.put("can", mineCostOk(MINE_DRAGON_RING_COST, have));
        }
        out.put("dragon", dragon);
        int sevenMax = Integer.MAX_VALUE;
        for (String c : S5MineMap.DAY_STONE_CODES) sevenMax = Math.min(sevenMax, have.getOrDefault(c, 0));
        sevenMax = Math.min(sevenMax, have.getOrDefault(S5MineMap.ORE_CODES[5], 0)); // [2026-10-25] 칠요석 하나에 별빛석 1개
        out.put("sevenMax", sevenMax); // 지금 칠요석을 몇 개 합성할 수 있는지
        out.put("deliverDone", items.getOrDefault(MINE_ITEM_DELIVER_DAY, 0) == mineToday()); // [2026-10-28] 오늘 구리 납품 했는지
        mineLookPayload(items, have, out); // [2026-10-24] 명품상점(외형)
        out.put("ores", mineOreList(userName));
    }

    private HashMap<String, Object> mineGearResult(String userName, boolean ok, String message) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", ok);
        out.put("message", message);
        mineGearPayload(userName, mineItems(userName), out);
        return out;
    }

    /** 대장장이 [구매] -- 장신구/장화를 만들거나 다음 단계로 올린다. 처음 만든 반지는 반지 칸이 비어 있으면 바로 낀다. */
    @Override
    @Transactional
    public HashMap<String, Object> mineCraft(String userName, String code) {
        if (!mineAccess(userName)) return mineClosed();
        MineGear g = mineGear(code);
        if (g == null) return mineGearResult(userName, false, "만들 수 없는 물건이에요.");
        mineRow(userName, false);
        HashMap<String, Object> crow = dao.selectMineUserForUpdate(userName); // 같은 유저 동시 요청 직렬화
        Map<String, Integer> items = mineItems(userName);
        int tier = items.getOrDefault(g.code, 0);
        if (tier >= g.cost.length) return mineGearResult(userName, false, g.name + "은(는) 이미 최고 단계(" + tier + "단계)예요.");
        if (tier + 1 > g.fieldMax && !S5MineMap.CITY.equals(strVal(crow.get("MAP_ID"), ""))) // [2026-10-25] 높은 단계는 왕도 대장장이만
            return mineGearResult(userName, false, "🔨 " + g.name + " " + (tier + 1) + "단계는 왕도 아스테리아의 대장장이만 만들 수 있어요.");
        String[][] cost = g.cost[tier];
        if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "재료가 부족해요. (" + mineCostText(cost) + " 필요)");
        mineTakeCost(userName, cost, 1);
        dao.upsertMineItem(userName, g.code, tier + 1);
        String wear = "";
        if ("tool".equals(g.kind)) { dao.upsertMineItem(userName, MINE_ITEM_PICK_SLOT, minePickSlotOf(g.code)); wear = " 바로 꼈어요."; }
        if ("ring".equals(g.kind) && items.getOrDefault(MINE_ITEM_RING_SLOT, 0) == 0) {
            dao.upsertMineItem(userName, MINE_ITEM_RING_SLOT, MINE_ITEM_PLENTY_RING.equals(g.code) ? 1 : 2);
            wear = " 바로 끼었어요.";
        }
        return mineGearResult(userName, true, "💍 " + g.name + " " + (tier + 1) + "단계! " + g.effect[tier] + " (" + mineCostText(cost) + " 사용)" + wear);
    }

    /** 반지 바꿔 끼기(풍요/심연 중 하나만). */
    @Override
    @Transactional
    public HashMap<String, Object> mineEquipRing(String userName, String code) {
        if (!mineAccess(userName)) return mineClosed();
        MineGear g = mineGear(code);
        if (g != null && "tool".equals(g.kind)) { // [2026-10-24] 곡괭이 바꿔 끼기(같은 걸 다시 누르면 기본 곡괭이로)
            Map<String, Integer> it = mineItems(userName);
            if (it.getOrDefault(g.code, 0) < 1) return mineGearResult(userName, false, g.name + "이(가) 없어요. 먼저 만들어 주세요.");
            int slot = minePickSlotOf(g.code), now = it.getOrDefault(MINE_ITEM_PICK_SLOT, 0);
            boolean on = now != slot;
            dao.upsertMineItem(userName, MINE_ITEM_PICK_SLOT, on ? slot : 0);
            return mineGearResult(userName, true, "⛏️ " + (on ? g.name + "을(를) 꼈어요. " + g.effect[0] : "기본 곡괭이로 바꿨어요."));
        }
        if (g == null || !"ring".equals(g.kind)) return mineGearResult(userName, false, "낄 수 없는 물건이에요.");
        if (mineItems(userName).getOrDefault(g.code, 0) < 1) return mineGearResult(userName, false, g.name + "이(가) 없어요. 먼저 만들어 주세요.");
        dao.upsertMineItem(userName, MINE_ITEM_RING_SLOT, MINE_ITEM_PLENTY_RING.equals(g.code) ? 1 : 2);
        return mineGearResult(userName, true, "💍 " + g.name + "을(를) 끼었어요.");
    }

    // [2026-10-28] "구리 광석 사용처를 늘려줘 -- 초보자들이 바로 상위 구역으로 가서 구리 채광을 잘 안 한다":
    //  대장장이 [합성]에 구리 정련(구리 MINE_REFINE_COPPER개 -> 철광석 1개, 여러 번)과
    //  하루 한 번 구리 납품(구리 MINE_DELIVER_COPPER개 -> 오늘 요일석 MINE_DELIVER_STONES개 + 곡괭이 내구도 +MINE_DELIVER_DUR, 최대치까지).
    private static final int MINE_REFINE_COPPER = 20;
    private static final int MINE_DELIVER_COPPER = 100;
    private static final int MINE_DELIVER_STONES = 3;
    private static final int MINE_DELIVER_DUR = 50;
    static final String MINE_ITEM_DELIVER_DAY = "DELIVER_DAY";

    private static int mineToday() {
        return Integer.parseInt(java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** 구리 정련 -- 구리 MINE_REFINE_COPPER개마다 철광석 1개. times = 몇 번(가진 만큼으로 줄인다). */
    @Override
    @Transactional
    public HashMap<String, Object> mineRefine(String userName, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        int n = Math.min(Math.max(1, times), mineOreHave(userName).getOrDefault(S5MineMap.ORE_CODES[0], 0) / MINE_REFINE_COPPER);
        if (n < 1) return mineGearResult(userName, false, "구리 광석 " + MINE_REFINE_COPPER + "개가 있어야 철광석 1개로 정련할 수 있어요.");
        mineTakeCost(userName, new String[][] { { S5MineMap.ORE_CODES[0], String.valueOf(MINE_REFINE_COPPER) } }, n);
        dao.addUserOre(userName, S5MineMap.ORE_CODES[1], n);
        return mineGearResult(userName, true, "🔥 구리 광석 " + (n * MINE_REFINE_COPPER) + "개를 녹여 철광석 " + n + "개를 얻었어요!");
    }

    /** 오늘의 구리 납품 -- 하루 한 번 구리 MINE_DELIVER_COPPER개 -> 오늘 요일석 + 곡괭이 내구도. */
    @Override
    @Transactional
    public HashMap<String, Object> mineDeliver(String userName) {
        if (!mineAccess(userName)) return mineClosed();
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        int today = mineToday();
        if (mineItems(userName).getOrDefault(MINE_ITEM_DELIVER_DAY, 0) == today) return mineGearResult(userName, false, "오늘은 이미 구리를 납품했어요. 내일 다시 가져와 주게.");
        String[][] cost = { { S5MineMap.ORE_CODES[0], String.valueOf(MINE_DELIVER_COPPER) } };
        if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "구리 광석 " + MINE_DELIVER_COPPER + "개를 가져오면 받아 주겠네.");
        mineTakeCost(userName, cost, 1);
        int di = S5MineMap.dayIndex(now);
        dao.addUserOre(userName, S5MineMap.DAY_STONE_CODES[di], MINE_DELIVER_STONES);
        int max = minePickMax(row), dur = Math.min(max, minePickDur(row) + MINE_DELIVER_DUR);
        dao.setMinePickDur(userName, dur);
        dao.upsertMineItem(userName, MINE_ITEM_DELIVER_DAY, today);
        HashMap<String, Object> out = mineGearResult(userName, true, "📦 구리 " + MINE_DELIVER_COPPER + "개 납품 완료! " + S5MineMap.DAY_STONE_NAMES[di] + " "
                + MINE_DELIVER_STONES + "개와 곡괭이 손질(내구도 " + dur + "/" + max + ")을 받았어요.");
        putMinePick(out, dur, max);
        return out;
    }

    /** 대장장이 [합성] -- 요일석 7종(월~일) 1개씩 + 별빛석 1개 -> 칠요석 1개. times = 몇 개(가진 만큼으로 줄인다).
     *  [2026-10-25] "별빛석 필요처 -- 칠요석 만들 때 들어가도록" 요청으로 별빛석 1개 추가. */
    @Override
    @Transactional
    public HashMap<String, Object> mineSynthSeven(String userName, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        Map<String, Integer> have = mineOreHave(userName);
        int n = Math.max(1, times);
        for (String c : S5MineMap.DAY_STONE_CODES) n = Math.min(n, have.getOrDefault(c, 0));
        n = Math.min(n, have.getOrDefault(S5MineMap.ORE_CODES[5], 0));
        if (n < 1) return mineGearResult(userName, false, "요일석 7종(월요석~일요석) 하나씩과 별빛석 1개가 있어야 칠요석을 만들 수 있어요.");
        String[][] cost = new String[S5MineMap.DAY_STONE_CODES.length + 1][];
        for (int i = 0; i < S5MineMap.DAY_STONE_CODES.length; i++) cost[i] = new String[] { S5MineMap.DAY_STONE_CODES[i], "1" };
        cost[S5MineMap.DAY_STONE_CODES.length] = mineStar(1);
        mineTakeCost(userName, cost, n);
        dao.addUserOre(userName, S5MineMap.SEVEN_STONE_CODE, n);
        return mineGearResult(userName, true, "🌈 요일석 7종과 별빛석을 합쳐 칠요석 " + n + "개를 만들었어요! (보유 " + (have.getOrDefault(S5MineMap.SEVEN_STONE_CODE, 0) + n) + "개)");
    }

    /** 바닷가 선장 -- action "give" = 금광석 10개 주기(MINE_CAPTAIN_SCAM_PCT 확률로 먹튀, 아니면 용왕의 반지 이야기 = 퀘스트 시작),
     *  "craft" = 이야기를 들은 뒤 재료를 내고 용왕의 반지 받기(물 위를 걸을 수 있음). */
    @Override
    @Transactional
    public HashMap<String, Object> mineCaptain(String userName, String action) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        Map<String, Integer> items = mineItems(userName);
        int quest = items.getOrDefault(MINE_ITEM_DRAGON_QUEST, 0);
        if (mineWaterWalk(items)) return mineGearResult(userName, false, "늙은 선장: 허허, 물 위를 걷는 기분은 어떤가?");
        if ("craft".equals(action)) {
            if (quest < 1) return mineGearResult(userName, false, "늙은 선장: 무슨 반지 말인가? 금광석이나 가져오게.");
            if (!mineCostOk(MINE_DRAGON_RING_COST, mineOreHave(userName))) {
                return mineGearResult(userName, false, "늙은 선장: 재료가 모자라는군. (" + mineCostText(MINE_DRAGON_RING_COST) + " 필요)");
            }
            mineTakeCost(userName, MINE_DRAGON_RING_COST, 1);
            dao.upsertMineItem(userName, MINE_ITEM_DRAGON_RING, 1);
            dao.upsertMineItem(userName, MINE_ITEM_DRAGON_QUEST, 2);
            HashMap<String, Object> out = mineGearResult(userName, true, "🌊 늙은 선장: 자, 용왕의 반지일세. 이제 물 위를 땅처럼 걸을 수 있을 거야!");
            out.put("craftedRing", true);
            return out;
        }
        if (quest >= 1) return mineGearResult(userName, false, "늙은 선장: 이야기는 이미 해 줬잖나. 재료를 모아 오게.");
        String gold = S5MineMap.ORE_CODES[3];
        if (dao.takeUserOre(userName, gold, MINE_CAPTAIN_GOLD) == 0) {
            return mineGearResult(userName, false, "늙은 선장: 금광석 " + MINE_CAPTAIN_GOLD + "개를 가져오면 신기한 걸 보여주지.");
        }
        if (RND.nextInt(100) < MINE_CAPTAIN_SCAM_PCT) {
            HashMap<String, Object> out = mineGearResult(userName, true, "늙은 선장: " + MINE_CAPTAIN_SCAM_LINES[RND.nextInt(MINE_CAPTAIN_SCAM_LINES.length)]
                    + " (금광석 " + MINE_CAPTAIN_GOLD + "개를 잃었어요)");
            out.put("scammed", true);
            return out;
        }
        dao.upsertMineItem(userName, MINE_ITEM_DRAGON_QUEST, 1);
        HashMap<String, Object> out = mineGearResult(userName, true, "늙은 선장: " + MINE_CAPTAIN_STORY);
        out.put("questStarted", true);
        return out;
    }

    // ---- [2026-10-24] 왕도 아스테리아: 텔레포트 게이트 비용, 성당 신부(치료 + 부활 지점) ----
    /** 텔레포트 게이트 한 번 = 금광석 개수("대도시에서 다른 지역으로는 어렵게"). */
    static final int MINE_TELE_GOLD = 0; // [2026-10-25] "대도시에서 텔레포트 비용 무료로" (예전 5)
    /** 부활 지점: 1 = 왕도 성당(신부에게 기도), 없으면 평원 마을. */
    static final String MINE_ITEM_RESPAWN = "RESPAWN";

    /** 왕도 성당 앞(신부 바로 아래 칸). */
    private static int[] mineCityChurch(S5MineMap city) {
        for (S5MineMap.Npc n : city.npcs()) if ("priest".equals(n.kind) && city.walkable(n.x, n.y + 1)) return new int[] { n.x, n.y + 1 };
        return new int[] { city.spawnX, city.spawnY };
    }

    /** 성당 신부 -- action: heal(체력 가득, 무료) / save(쓰러지면 왕도 성당에서 깨어나기) / reset(부활 지점을 평원 마을로). */
    @Override
    @Transactional
    public HashMap<String, Object> minePriest(String userName, String action) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        long now = System.currentTimeMillis();
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", true);
        if ("save".equals(action) || "reset".equals(action)) {
            boolean save = "save".equals(action);
            dao.upsertMineItem(userName, MINE_ITEM_RESPAWN, save ? 1 : 0);
            out.put("respawnCity", save);
            out.put("message", save ? "🙏 성당 신부: 기도했습니다. 쓰러지면 이 성당에서 깨어날 거예요." : "🙏 성당 신부: 이제 쓰러지면 중앙 평원 마을에서 깨어날 거예요.");
            return out;
        }
        int lv = mineLv(row);
        boolean locked = mineItems(userName).getOrDefault(MINE_ITEM_HP_LOCK, 0) > 0;
        if (locked) dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); // [2026-10-25] 잠긴 최대 체력도 정상으로
        dao.updateMineCombat(userName, lv, Math.max(0, longOf(row.get("M_EXP"), 0L)), mineHpMax(lv), now, 0);
        out.put("combat", mineCombatInfo(lv, Math.max(0, longOf(row.get("M_EXP"), 0L)), mineHpMax(lv), now));
        out.put("message", "✨ 성당 신부: 여신의 축복을! 체력이 모두 회복됐어요." + (locked ? " 🔓 잠겼던 최대 체력도 풀렸어요." : ""));
        return out;
    }

    // ---- [2026-10-24] 광산에서 다른 유저 만나기 -- "접속한 유저끼리 만나게, 캐릭터명은 안 보이게, 근처에 오면 '안녕하세요' 버튼, 하루 1회 요일석 5개" ----
    //  서버 메모리(MINE_PRESENCE)에 유저별 맵/칸/방향/외형/말풍선을 들고, 화면이 1초마다 내 위치를 보내며 같은 맵의 다른 유저(최근 10초)를 받아 간다.
    //  다른 유저는 이름 대신 익명 번호(id)로만 내려준다. 인사 보상은 서버가 저장 위치 기준 4칸 안에 다른 유저가 실제로 있는지 확인한 뒤에만.
    private static final class MinePresence {
        final String id;
        volatile String mapId;
        volatile int x, y;
        volatile String dir = "down";
        volatile long at, lookAt, bubbleAt;
        volatile HashMap<String, Object> look;
        MinePresence(String id) { this.id = id; }
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, MinePresence> MINE_PRESENCE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MINE_PRESENCE_TTL_MS = 10_000L;
    private static final long MINE_BUBBLE_MS = 4_000L;
    private static final int MINE_GREET_RANGE = 4;
    private static final int MINE_GREET_STONES = 5;
    static final String MINE_ITEM_GREET_DAY = "GREET_DAY";
    /** [2026-10-24] 바닷길 하루 보석 한도("바닷길 안에서는 하루 보석 50개까지") -- SEA_DAY = yyyyMMdd, SEA_CNT = 그날 캔 칸 수. */
    static final int MINE_SEA_DAILY = 50;
    static final String MINE_ITEM_SEA_DAY = "SEA_DAY";
    static final String MINE_ITEM_SEA_CNT = "SEA_CNT";

    private MinePresence minePresenceOf(String userName) {
        return MINE_PRESENCE.computeIfAbsent(userName, u -> new MinePresence(Integer.toHexString((u + "|s5-mine-presence").hashCode() & 0x7fffffff)));
    }

    /** 내 위치 알리기 + 같은 맵 다른 유저 목록 [{id, x, y, d, look, bubble}] (이름 없음). 화면 표시용이라 DB를 거치지 않는다. */
    @Override
    public HashMap<String, Object> minePresence(String userName, String mapId, int x, int y, String dir) {
        HashMap<String, Object> out = new HashMap<>();
        long now = System.currentTimeMillis();
        List<HashMap<String, Object>> others = new ArrayList<>();
        out.put("others", others);
        if (!mineAccess(userName) || mapId == null || mapId.isEmpty()) return out;
        MinePresence me = minePresenceOf(userName);
        me.mapId = mapId; me.x = x; me.y = y; me.at = now;
        if (dir != null && dir.matches("up|down|left|right")) me.dir = dir;
        if (me.look == null || now - me.lookAt > 30_000L) { me.look = mineLookOf(mineItems(userName)); me.lookAt = now; } // 외형은 30초마다 다시 읽는다
        for (java.util.Map.Entry<String, MinePresence> e : MINE_PRESENCE.entrySet()) {
            MinePresence o = e.getValue();
            if (o == me || now - o.at > MINE_PRESENCE_TTL_MS || !mapId.equals(o.mapId) || o.look == null) continue;
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", o.id);
            m.put("x", o.x);
            m.put("y", o.y);
            m.put("d", o.dir);
            m.put("look", o.look);
            if (now - o.bubbleAt < MINE_BUBBLE_MS) m.put("bubble", "안녕하세요!");
            others.add(m);
        }
        return out;
    }

    /** 광산을 나갈 때 -- 다른 유저 화면에서 바로 사라지게. */
    @Override
    public void mineLeave(String userName) {
        MINE_PRESENCE.remove(userName);
        mineDisengageAll(userName);
    }

    /** [2026-10-25] 관리자 광산 캐릭터 현황 -- "캐릭터별 현재 위치, 장착 아이템들, 레벨, 보유 수량 보이는 페이지" 요청.
     *  지금 광산 화면을 띄운 유저(최근 10초 위치 알림)는 실시간 위치, 아니면 마지막 저장 위치. 읽기만 한다(행 생성/잠금 없음). */
    @Override
    public List<HashMap<String, Object>> mineUserOverview() {
        long now = System.currentTimeMillis();
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (HashMap<String, Object> row : dao.selectAllMineUsers()) {
            String u = strVal(row.get("USER_NAME"), "");
            Map<String, Integer> items = mineItems(u);
            HashMap<String, Object> m = new HashMap<>();
            m.put("userName", u);
            MinePresence p = MINE_PRESENCE.get(u);
            boolean online = p != null && p.mapId != null && now - p.at <= MINE_PRESENCE_TTL_MS;
            S5MineMap map = online ? S5MineMap.get(p.mapId) : mineMapOf(row);
            int[] pos = online ? new int[] { p.x, p.y } : minePos(row, map, mineWaterWalk(items));
            m.put("online", online);
            m.put("mapId", map.id);
            m.put("mapName", map.name);
            m.put("x", pos[0]);
            m.put("y", pos[1]);
            m.put("lastAt", online ? p.at : longOf(row.get("POS_AT_MS"), 0L));
            int lv = mineLv(row);
            m.put("lv", lv);
            m.put("exp", Math.max(0, longOf(row.get("M_EXP"), 0L)));
            m.put("expNeed", mineExpNeed(lv));
            m.put("kills", intVal(row.get("M_KILLS"), 0));
            m.put("totalDig", intVal(row.get("TOTAL_DIG"), 0));
            m.put("pickDur", minePickDur(row));
            m.put("pickMax", minePickMax(row));
            // 장착/보유 장비: 투구, 장신구/장화/곡괭이(단계, 끼고 있는지), 용왕의 반지
            List<HashMap<String, Object>> gear = new ArrayList<>();
            int helmet = items.getOrDefault(MINE_ITEM_HELMET, 0);
            if (helmet > 0) gear.add(mineOverviewGear("광부 투구", "helmet", helmet, MINE_HELMET_OPEN_MAX, true));
            int slot = items.getOrDefault(MINE_ITEM_RING_SLOT, 0), pickSlot = items.getOrDefault(MINE_ITEM_PICK_SLOT, 0);
            for (MineGear g : MINE_GEAR) {
                int tier = Math.min(items.getOrDefault(g.code, 0), g.cost.length);
                if (tier < 1) continue;
                boolean worn = "ring".equals(g.kind) ? (slot == 1 && MINE_ITEM_PLENTY_RING.equals(g.code)) || (slot == 2 && MINE_ITEM_ABYSS_RING.equals(g.code))
                        : "tool".equals(g.kind) ? pickSlot == minePickSlotOf(g.code) : true;
                gear.add(mineOverviewGear(g.name, g.kind, tier, g.cost.length, worn));
            }
            if (mineWaterWalk(items)) gear.add(mineOverviewGear("용왕의 반지", "dragon", 1, 1, true));
            if (items.getOrDefault(MINE_ITEM_COAT1, 0) > 0) gear.add(mineOverviewGear("방한 외투", "coat", 1, 1, mineCoatDef(items) == 1)); // [2026-10-26]
            if (items.getOrDefault(MINE_ITEM_COAT2, 0) > 0) gear.add(mineOverviewGear("두꺼운 방한 외투", "coat", 1, 1, mineCoatDef(items) == 2));
            if (items.getOrDefault(MINE_ITEM_GLOVES, 0) > 0) gear.add(mineOverviewGear("방한 장갑", "coat", 1, 1, true)); // [2026-10-28]
            m.put("gear", gear);
            m.put("pickName", pickSlot == 1 ? "광역 곡괭이" : pickSlot == 2 ? "탐지 곡괭이" : pickSlot == 3 ? "쌍날 곡괭이" : "기본 곡괭이");
            m.put("dragonQuest", items.getOrDefault(MINE_ITEM_DRAGON_QUEST, 0));
            // 외형(부위별 착용 치장 이름, 성별), 산 치장 아이템 수
            HashMap<String, Object> look = mineLookOf(items);
            HashMap<String, Object> wear = new HashMap<>();
            for (String s : MINE_LOOK_SLOTS) {
                MineCos c = mineCos(strVal(look.get(s + "Code"), ""));
                wear.put(s, c == null ? "" : c.name);
            }
            m.put("look", wear);
            m.put("lookRaw", look); // [2026-10-25] 화면 캐릭터 그림(MineChar)용
            m.put("gender", items.getOrDefault(MINE_ITEM_GENDER, 0));
            int cosOwned = 0;
            for (MineCos c : MINE_COS) if (items.getOrDefault("COS_" + c.code, 0) > 0) cosOwned++;
            m.put("cosOwned", cosOwned);
            List<HashMap<String, Object>> ores = new ArrayList<>();
            for (HashMap<String, Object> o : mineOreList(u)) if (intVal(o.get("qty"), 0) > 0) ores.add(o);
            m.put("ores", ores);
            out.add(m);
        }
        return out;
    }

    /** [2026-10-25] "로그의 광산 로그에 캐릭터 이미지" -- 광산 유저별 외형(mineLookOf 모양, 화면 MineChar가 그린다). 읽기만. */
    @Override
    public HashMap<String, Object> mineLooks() {
        HashMap<String, Object> out = new HashMap<>();
        for (HashMap<String, Object> row : dao.selectAllMineUsers()) {
            String u = strVal(row.get("USER_NAME"), "");
            if (!u.isEmpty()) out.put(u, mineLookOf(mineItems(u)));
        }
        return out;
    }

    private static HashMap<String, Object> mineOverviewGear(String name, String kind, int tier, int maxTier, boolean worn) {
        HashMap<String, Object> g = new HashMap<>();
        g.put("name", name);
        g.put("kind", kind);
        g.put("tier", tier);
        g.put("maxTier", maxTier);
        g.put("worn", worn);
        return g;
    }

    /** "안녕하세요" -- 저장 위치 4칸 안에 같은 맵의 다른 유저가 있으면 말풍선, 하루 1회 오늘 요일석 5개. */
    @Override
    @Transactional
    public HashMap<String, Object> mineGreet(String userName) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWaterWalk(items));
        MinePresence me = minePresenceOf(userName);
        boolean near = false;
        for (MinePresence o : MINE_PRESENCE.values()) {
            if (o == me || now - o.at > MINE_PRESENCE_TTL_MS || !map.id.equals(o.mapId)) continue;
            if (Math.abs(o.x - pos[0]) + Math.abs(o.y - pos[1]) <= MINE_GREET_RANGE + 2) near = true; // 저장 위치는 최대 2.5초 늦다 -- 2칸 여유
        }
        if (!near) { out.put("message", "근처에 인사할 사람이 없어요."); return out; }
        if (now - me.bubbleAt < 3_000L) { out.put("message", "조금 있다가 다시 인사해요."); return out; }
        me.bubbleAt = now;
        out.put("ok", true);
        int today = Integer.parseInt(java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        if (items.getOrDefault(MINE_ITEM_GREET_DAY, 0) != today) {
            int di = S5MineMap.dayIndex(now);
            dao.upsertMineItem(userName, MINE_ITEM_GREET_DAY, today);
            dao.addUserOre(userName, S5MineMap.DAY_STONE_CODES[di], MINE_GREET_STONES);
            out.put("reward", true);
            out.put("ores", mineOreList(userName));
            out.put("message", "👋 안녕하세요! 오늘의 첫 인사 -- " + S5MineMap.DAY_STONE_NAMES[di] + " " + MINE_GREET_STONES + "개를 받았어요.");
        } else out.put("message", "👋 안녕하세요! (인사 보상은 하루 한 번 -- 오늘은 이미 받았어요)");
        return out;
    }

    // ---- [2026-10-24] 명품상점(광산 캐릭터 외형) ----
    // "캐릭터 외형을 바꿀 수 있게, 대장장이 옆 건물에 명품상점 -- 부위별 치장 아이템, 성별 전환도 여기서" 요청. 능력치 없음(보이는 것만).
    //  산 아이템: TBOT_S5_MINE_ITEM "COS_" + 코드(1), 부위별 착용: "LOOK_" + 부위(카탈로그 순번 + 1, 0 = 기본), 성별: "LOOK_GENDER"(0 남 / 1 여).
    //  모양/색(look 값)은 화면(TowerMine.charSprite)이 그린다.
    static final String[] MINE_LOOK_SLOTS = { "hat", "hair", "top", "bottom", "shoes" };
    private static final String MINE_ITEM_GENDER = "LOOK_GENDER";
    /** 성별 전환 비용. */
    private static final String[][] MINE_GENDER_COST = { { S5MineMap.ORE_CODES[2], "10" } };

    private static final class MineCos {
        final String code, slot, name, look; // look = "색" 또는 "색|모양"
        final String[][] cost;
        MineCos(String code, String slot, String name, String look, String[][] cost) {
            this.code = code; this.slot = slot; this.name = name; this.look = look; this.cost = cost;
        }
    }

    private static String[] mineOre(int grade, int qty) {
        return new String[] { S5MineMap.ORE_CODES[grade - 1], String.valueOf(qty) };
    }

    private static final List<MineCos> MINE_COS = java.util.Arrays.asList(
        new MineCos("HAT_STRAW", "hat", "밀짚모자", "#e8c84a|straw", new String[][] { mineOre(1, 150) }),
        new MineCos("HAT_MINER", "hat", "광부 헬멧", "#9aa3ad|helmet", new String[][] { mineOre(2, 80) }),
        new MineCos("HAT_PIRATE", "hat", "해적 모자", "#222630|pirate", new String[][] { mineCost(3, 12) }),
        new MineCos("HAT_WIZARD", "hat", "마법사 모자", "#4b3a9a|wizard", new String[][] { mineCost(4, 15) }),
        new MineCos("HAT_CROWN", "hat", "황금 왕관", "#f2c230|crown", new String[][] { mineOre(4, 30), mineCost(8, 1) }),
        new MineCos("HAIR_BROWN", "hair", "갈색 머리", "#7a4a26", new String[][] { mineOre(1, 100) }),
        new MineCos("HAIR_BLOND", "hair", "금발", "#f0d060", new String[][] { mineOre(3, 20) }),
        new MineCos("HAIR_SILVER", "hair", "은발", "#d8dde6", new String[][] { mineOre(3, 40) }),
        new MineCos("HAIR_PINK", "hair", "분홍 머리", "#f58fb8", new String[][] { mineCost(0, 10) }),
        new MineCos("HAIR_BLUE", "hair", "하늘색 머리", "#6fc8f0", new String[][] { mineCost(3, 10) }),
        new MineCos("TOP_RED", "top", "붉은 도포", "#c0392b|robe", new String[][] { mineOre(2, 100) }),
        new MineCos("TOP_GREEN", "top", "초록 도포", "#2e8b57|robe", new String[][] { mineCost(7, 3) }),
        new MineCos("TOP_HANBOK", "top", "흰 한복", "#f4f1e8|hanbok", new String[][] { mineCost(6, 15) }),
        new MineCos("TOP_ARMOR", "top", "검은 갑옷", "#3a3f4a|armor", new String[][] { mineOre(3, 50) }),
        new MineCos("TOP_GOLD", "top", "황금 갑옷", "#e0b030|armor", new String[][] { mineOre(4, 30), mineCost(1, 3) }),
        new MineCos("BOT_BLACK", "bottom", "검은 바지", "#1f1f24", new String[][] { mineOre(1, 120) }),
        new MineCos("BOT_WHITE", "bottom", "흰 바지", "#e8e4da", new String[][] { mineOre(2, 60) }),
        new MineCos("BOT_RED", "bottom", "붉은 치마바지", "#a8323a", new String[][] { mineCost(5, 3) }),
        new MineCos("SHOE_LEATHER", "shoes", "가죽 장화", "#8a5a2b", new String[][] { mineOre(1, 100) }),
        new MineCos("SHOE_RED", "shoes", "꽃신", "#e0507a", new String[][] { mineCost(5, 2) }),
        new MineCos("SHOE_GOLD", "shoes", "황금 신", "#f2c230", new String[][] { mineOre(4, 20) })
    );

    private static MineCos mineCos(String code) {
        for (MineCos c : MINE_COS) if (c.code.equals(code)) return c;
        return null;
    }

    /** 외형(성별 + 부위별 착용 look 값) + 상점 목록을 out에 넣는다. */
    /** 외형(성별 + 부위별 look 값과 코드) -- 내 화면(look)과 다른 유저에게 보이는 모습(광산 함께 보기)에 같이 쓴다. */
    private static HashMap<String, Object> mineLookOf(Map<String, Integer> items) {
        HashMap<String, Object> look = new HashMap<>();
        look.put("gender", items.getOrDefault(MINE_ITEM_GENDER, 0));
        for (String slot : MINE_LOOK_SLOTS) {
            int idx = items.getOrDefault("LOOK_" + slot.toUpperCase(), 0) - 1;
            MineCos c = idx >= 0 && idx < MINE_COS.size() ? MINE_COS.get(idx) : null;
            look.put(slot, c != null && c.slot.equals(slot) ? c.look : "");
            look.put(slot + "Code", c != null && c.slot.equals(slot) ? c.code : "");
        }
        return look;
    }

    private static void mineLookPayload(Map<String, Integer> items, Map<String, Integer> have, HashMap<String, Object> out) {
        out.put("look", mineLookOf(items));
        List<HashMap<String, Object>> shop = new ArrayList<>();
        for (MineCos c : MINE_COS) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", c.code);
            m.put("slot", c.slot);
            m.put("name", c.name);
            m.put("look", c.look);
            boolean owned = items.getOrDefault("COS_" + c.code, 0) > 0;
            m.put("owned", owned);
            if (!owned) {
                m.put("costs", mineCostList(c.cost, have));
                m.put("can", mineCostOk(c.cost, have));
            }
            shop.add(m);
        }
        out.put("cosShop", shop);
        out.put("genderCosts", mineCostList(MINE_GENDER_COST, have));
        out.put("genderCan", mineCostOk(MINE_GENDER_COST, have));
    }

    /** 명품상점 -- action: buy(code, 사면 바로 입음) / wear(code, 빈 값 + slot = 그 부위 기본으로) / gender(성별 전환, 은광석 10개). */
    @Override
    @Transactional
    public HashMap<String, Object> mineLook(String userName, String action, String code, String slot) {
        if (!mineAccess(userName)) return mineClosed();
        MinePresence pr = MINE_PRESENCE.get(userName);
        if (pr != null) pr.lookAt = 0; // 다른 유저에게 보이는 외형도 다음 위치 알림 때 다시 읽기
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        Map<String, Integer> items = mineItems(userName);
        if ("gender".equals(action)) {
            if (!mineCostOk(MINE_GENDER_COST, mineOreHave(userName))) return mineGearResult(userName, false, "명품상점: 성별을 바꾸려면 " + mineCostText(MINE_GENDER_COST) + "가 필요해요.");
            mineTakeCost(userName, MINE_GENDER_COST, 1);
            int g = items.getOrDefault(MINE_ITEM_GENDER, 0) == 1 ? 0 : 1;
            dao.upsertMineItem(userName, MINE_ITEM_GENDER, g);
            return mineGearResult(userName, true, "✨ 명품상점: 짜잔~ " + (g == 1 ? "여성" : "남성") + " 캐릭터로 바뀌었어요! (" + mineCostText(MINE_GENDER_COST) + " 사용)");
        }
        if ("wear".equals(action) && (code == null || code.isEmpty())) {
            if (slot == null || !java.util.Arrays.asList(MINE_LOOK_SLOTS).contains(slot)) return mineGearResult(userName, false, "잘못된 부위예요.");
            dao.upsertMineItem(userName, "LOOK_" + slot.toUpperCase(), 0);
            return mineGearResult(userName, true, "기본 모습으로 돌아갔어요.");
        }
        MineCos c = mineCos(code);
        if (c == null) return mineGearResult(userName, false, "없는 상품이에요.");
        boolean owned = items.getOrDefault("COS_" + c.code, 0) > 0;
        if ("buy".equals(action) && !owned) {
            if (!mineCostOk(c.cost, mineOreHave(userName))) return mineGearResult(userName, false, "명품상점: 재료가 모자라요. (" + mineCostText(c.cost) + " 필요)");
            mineTakeCost(userName, c.cost, 1);
            dao.upsertMineItem(userName, "COS_" + c.code, 1);
            owned = true;
        } else if (!owned) {
            return mineGearResult(userName, false, c.name + "은(는) 아직 안 샀어요.");
        }
        dao.upsertMineItem(userName, "LOOK_" + c.slot.toUpperCase(), MINE_COS.indexOf(c) + 1);
        return mineGearResult(userName, true, "✨ " + c.name + ("buy".equals(action) ? " 구매! 바로 입었어요. (" + mineCostText(c.cost) + " 사용)" : " 착용!"));
    }

    // ---- [2026-10-23] 일반 장비/악세서리 분해(★1~★6) ----
    // "인벤토리에서 6성 이하 장비/악세 분해, 여러 개면 몇 개 분해할지 고르고, 분해 시 확률로 전설의조각. 6성 10%, 그 아래는 거의 안 나오게" 요청.
    // 개당 조각 확률(%), 인덱스 = 등급-1. 서버 전체 미착용 재고(★6 744/★5 2184/★4 2632 ...)를 다 분해해도 기대값 약 105개 수준.
    private static final double[] DISMANTLE_FRAGMENT_PCT = { 0.1, 0.1, 0.1, 0.3, 1.0, 10.0 };

    @Override
    @Transactional
    public String dismantleEquip(String userName, int equipId, int qty) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) return "전투 중에는 분해할 수 없습니다.";
        HashMap<String, Object> equip = null;
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (intVal(e.get("EQUIP_ID"), 0) == equipId) { equip = e; break; }
        }
        if (equip == null) return "이미 없는 장비입니다.";
        if (equip.get("EQUIPPED_COMPANION_ID") != null) return "착용 중인 장비는 분해할 수 없습니다. 먼저 해제하세요.";
        int grade = intVal(equip.get("GRADE"), 1);
        if (equip.get("LEGENDARY_ID") != null || grade >= 7) return "★7 전설장비는 상점의 [장비해체]로 해체하세요(조각 15개 + 강화권).";
        int have = qtyOf(equip);
        if (qty < 1) qty = 1;
        if (qty > have) return "보유 수량(" + have + "개)보다 많이 분해할 수 없습니다.";
        takeEquipUnits(equipId, qty);
        double pct = DISMANTLE_FRAGMENT_PCT[Math.max(1, Math.min(6, grade)) - 1];
        int frag = 0;
        for (int i = 0; i < qty; i++) if (RND.nextDouble() * 100 < pct) frag++;
        int total = intVal(progress.get("LEGEND_FRAGMENT"), 0);
        if (frag > 0) {
            total += frag;
            HashMap<String, Object> up = new HashMap<>();
            up.put("userName", userName);
            up.put("legendFragment", total);
            dao.updateUserProgress(up);
        }
        String label = equipFullLabel(strVal(equip.get("CLASS"), ""), strVal(equip.get("PART"), "")) + " ★" + grade;
        return "🔨 " + label + " " + qty + "개 분해 완료!" + (have > qty ? " (남은 " + (have - qty) + "개)" : "")
                + NL + (frag > 0 ? "🧩 전설의조각 " + frag + "개 획득! (보유 " + total + "개)" : "전설의조각은 나오지 않았습니다. (개당 확률 ★6 10% · ★5 1% · ★4 0.3% · 그 이하 0.1%)");
    }

    /** 채팅 /장비분해 N [개수] -- N = /장비목록 [미착용] 번호. */
    @Override
    @Transactional
    public String dismantleEquipByIdx(String userName, int equipIdx, int qty) {
        List<HashMap<String, Object>> unequipped = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (e.get("EQUIPPED_COMPANION_ID") == null) unequipped.add(e);
        }
        if (equipIdx < 1 || equipIdx > unequipped.size()) return "잘못된 장비 번호입니다. /장비목록을 확인하세요.";
        return dismantleEquip(userName, intVal(unequipped.get(equipIdx - 1).get("EQUIP_ID"), 0), qty);
    }

    /** [2026-10-22] 웹 해체 팝업 전용 -- "미착용 목록 N번" 대신 EQUIP_ID로 해체한다.
     *  번호 방식은 팝업이 상점 탭에 있어 해체 후 목록이 새로 안 불려서, 두 번째 해체부터 옛 번호가 다른 장비(★6 등)를 가리켜
     *  "★7 전설장비만 분해할 수 있습니다"로 거절됐다(무기 하나만 되고 나머지는 안 되는 것처럼 보이던 원인). */
    @Override
    @Transactional
    public String disenchantLegendaryById(String userName, int equipId) {
        HashMap<String, Object> progress = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(progress.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 분해할 수 없습니다.";
        }
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (intVal(e.get("EQUIP_ID"), 0) != equipId) continue;
            if (e.get("EQUIPPED_COMPANION_ID") != null) return "착용 중인 장비는 해체할 수 없습니다. 먼저 해제하세요.";
            return disenchantRow(userName, progress, e);
        }
        return "이미 해체했거나 없는 장비입니다.";
    }

    private String disenchantRow(String userName, HashMap<String, Object> progress, HashMap<String, Object> equip) {
        if (intVal(equip.get("GRADE"), 1) != 7 || equip.get("LEGENDARY_ID") == null) return "★7 전설장비만 조각으로 분해할 수 있습니다.";

        // [2026-10-22] "강화된 장비 해체 시 전설의조각과 강화권을 돌려줘서 다른 장비에 덧입힐 수 있게" 요청 -- 예전의 "강화에 쓴 조각 절반 환급" 대신
        // 그 강화 수치의 강화권(예: +8 강화권)을 준다. 강화권은 강화 수치가 더 낮은 다른 전설장비에 쓰면 그 수치로 맞춰진다(useEnhanceTicket).
        int enhLv = intVal(equip.get("ENHANCE_LEVEL"), 0);
        Object legIdObj = equip.get("LEGENDARY_ID");
        dao.deleteEquip(intVal(equip.get("EQUIP_ID"), 0));
        if (enhLv > 0) dao.insertEnhanceTicket(userName, enhLv, legIdObj == null ? null : intVal(legIdObj, 0));
        int newFragment = intVal(progress.get("LEGEND_FRAGMENT"), 0) + LEGEND_DISENCHANT_REFUND;
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("legendFragment", newFragment);
        dao.updateUserProgress(up);

        String itemName = "전설장비";
        if (legIdObj != null) {
            HashMap<String, Object> meta = dao.selectLegendaryMaster(intVal(legIdObj, 0));
            if (meta != null) itemName = strVal(meta.get("ITEM_NAME"), itemName);
        }
        return "🧩 [" + itemName + (enhLv > 0 ? " +" + enhLv : "") + "] 분해 완료! 전설의조각 " + LEGEND_DISENCHANT_REFUND + "개 획득 (보유 " + newFragment + "개)"
                + (enhLv > 0 ? NL + "🎫 +" + enhLv + " 강화권 획득! 강화 수치가 더 낮은 다른 전설장비에 쓰면 +" + enhLv + "로 맞춰집니다. (웹: 전설강화 창 / 채팅: /강화권)" : "");
    }

    // ================================================================
    // [2026-10-22] 전투 영향 패치 확인 게이트 + 전설장비 착용 규칙 + 강화권
    // ================================================================
    // 패치를 확인(PATCH_ACK_VERSION)하기 전에는 주사위를 굴리지 않고 패치 내용을 보여준다. 다음 전투 영향 패치 때는 PATCH_VERSION과 PATCH_NOTES만 바꾸면 된다.
    private static final String PATCH_VERSION = "20261023";
    private static final String PATCH_NOTES = "📢 전투 밸런스 패치 안내 (2026-10-23)" + NL
            + "1. 전설장비 착용 제한 없음: 동료 한 명이 여러 부위에 전설을 낄 수 있고(6부위 모두 가능), 같은 전설장비를 여러 동료가 함께 착용할 수도 있습니다." + NL
            + "2. 전설장비 특수효과가 50%로 조정됩니다. (예: 연사 30%→15%, 받는 피해 감소 20%→10%, 불굴·시작 보호막은 50% 확률로 발동, 능력치 배율 x2→x1.5, 크리티컬 x2→x1.75) 장비 기본 능력치와 강화 효과는 그대로입니다." + NL
            + "3. 성장형 동료(☆1)는 장비 효과 50% 규칙이 그대로라 전설 특수효과는 원래의 25%가 적용됩니다. (최대 성장 x5.6)" + NL
            + "4. 강화된 전설장비를 해체하면 전설의조각 15개와 그 수치의 강화권(예: +8 강화권)을 돌려받습니다. 강화권은 강화 수치가 더 낮은 다른 전설장비에 써서 그 수치로 맞출 수 있습니다. (강화비 절반 환급은 강화권으로 대체)" + NL
            + "5. 보관함의 같은 종류 일반 장비/악세서리는 개수(xN)로 묶여 표시됩니다. (합성 규칙은 그대로)";

    /** 주사위를 막아야 하면 {type: PATCH|LEGEND, text}, 아니면 null. 웹 컨트롤러는 굴리기 전에 이걸로 확인 팝업을 띄우고, 채팅은 rollDice가 text를 그대로 돌려준다. */
    @Override
    public HashMap<String, Object> diceGate(String userName) {
        HashMap<String, Object> p = dao.selectUserProgress(userName);
        if (p == null) return null;
        HashMap<String, Object> g = new HashMap<>();
        if (!PATCH_VERSION.equals(strVal(p.get("PATCH_ACK_VERSION"), ""))) {
            g.put("type", "PATCH");
            g.put("version", PATCH_VERSION);
            g.put("text", PATCH_NOTES + NL + NL + "👉 확인하면 다시 공격할 수 있습니다. (웹: 확인 버튼 / 채팅: /패치확인)");
            return g;
        }
        List<String> v = legendViolations(userName);
        if (!v.isEmpty()) {
            g.put("type", "LEGEND");
            g.put("text", "⚠️ 전설장비 착용 규칙에 어긋난 장비가 있어 공격할 수 없습니다." + NL + String.join(NL, v) + NL
                    + "👉 편성 탭(채팅: /장비해제 파티번호)에서 해제한 뒤 다시 시도하세요. (같은 전설장비는 한 동료만 착용)");
            return g;
        }
        return null;
    }

    @Override
    @Transactional
    public String ackPatch(String userName) {
        getOrInitProgress(userName);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("patchAckVersion", PATCH_VERSION);
        dao.updateUserProgress(up);
        List<String> v = legendViolations(userName);
        return "✅ 패치 내용을 확인했습니다. 이제 공격할 수 있습니다."
                + (v.isEmpty() ? "" : NL + "⚠️ 단, 아래 전설장비 착용을 먼저 정리해야 공격할 수 있습니다." + NL + String.join(NL, v));
    }

    /** 동료ID -> "직업(이름)" */
    private Map<Integer, String> companionLabels(String userName) {
        Map<Integer, String> m = new HashMap<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            String job = strVal(c.get("CLASS"), "WARRIOR");
            m.put(intVal(c.get("COMPANION_ID"), 0), JOB_NAME.getOrDefault(job, "동료") + "(" + strVal(c.get("NAME"), JOB_NAME.getOrDefault(job, "동료")) + ")");
        }
        return m;
    }

    /** 전설장비를 companionId에게 끼우면 규칙 위반인지 -- 위반이면 안내 문구, 아니면 null.
     *  [2026-10-23] "중복전설 착용 가능하게" 요청으로 남은 규칙(같은 전설은 한 동료만)도 폐지 -- 이제 전설 착용 제한이 없다.
     *  (예전 규칙: 10-22 동료당 1개 + 같은 전설 한 동료만 -> 10-23 오전 같은 전설 한 동료만 -> 10-23 오후 제한 없음) */
    private String legendWearBlock(String userName, HashMap<String, Object> equip, int companionId) {
        return null;
    }

    /** 지금 착용 상태의 전설 규칙 위반 목록 -- [2026-10-23] 규칙이 없어져 항상 빈 목록(주사위 전 전설 규칙 확인 팝업도 더는 안 뜬다). */
    private List<String> legendViolations(String userName) {
        return new ArrayList<>();
    }

    @Override
    public List<HashMap<String, Object>> enhanceTickets(String userName) {
        return dao.selectEnhanceTickets(userName);
    }

    /** 강화권 사용 -- 강화 수치가 권보다 낮은 내 ★7 전설장비를 그 수치로 맞춘다(연속 실패 보정은 0으로). */
    @Override
    @Transactional
    public String useEnhanceTicket(String userName, int ticketId, int equipId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) return "전투 중에는 강화권을 쓸 수 없습니다.";
        HashMap<String, Object> ticket = null;
        for (HashMap<String, Object> t : dao.selectEnhanceTickets(userName)) {
            if (intVal(t.get("TICKET_ID"), 0) == ticketId) { ticket = t; break; }
        }
        if (ticket == null) return "강화권을 찾을 수 없습니다.";
        HashMap<String, Object> equip = null;
        for (HashMap<String, Object> e : legendaryEquipsOf(userName)) {
            if (intVal(e.get("EQUIP_ID"), 0) == equipId) { equip = e; break; }
        }
        if (equip == null) return "강화권을 쓸 ★7 전설장비를 찾을 수 없습니다.";
        int lv = intVal(ticket.get("ENHANCE_LEVEL"), 0);
        int cur = intVal(equip.get("ENHANCE_LEVEL"), 0);
        String name = strVal(equip.get("LEGENDARY_ITEM_NAME"), "전설장비");
        if (cur >= lv) return "[" + name + "]은(는) 이미 +" + cur + "입니다. +" + lv + " 강화권은 강화 수치가 더 낮은 장비에만 쓸 수 있습니다.";
        if (dao.deleteEnhanceTicket(ticketId, userName) == 0) return "이미 사용한 강화권입니다.";
        dao.updateEquipEnhance(equipId, lv, 0);
        return "🎫 +" + lv + " 강화권 사용! [" + name + "] +" + cur + " → +" + lv + " (장비 성능 +" + (lv * ENHANCE_STAT_PCT_PER_LV) + "%)";
    }

    /** 채팅 /강화권 -- 보유 강화권과 전설장비 번호 목록. */
    @Override
    public String enhanceTicketText(String userName) {
        List<HashMap<String, Object>> tickets = dao.selectEnhanceTickets(userName);
        if (tickets.isEmpty()) return "보유한 강화권이 없습니다. (강화된 ★7 전설장비를 /전설분해 하면 그 수치의 강화권을 받습니다)";
        StringBuilder sb = new StringBuilder("🎫 강화권").append(NL);
        int no = 1;
        for (HashMap<String, Object> t : tickets) {
            sb.append(no++).append(". +").append(intVal(t.get("ENHANCE_LEVEL"), 0)).append(" 강화권");
            String src = strVal(t.get("SOURCE_ITEM_NAME"), "");
            if (!src.isEmpty()) sb.append(" (").append(src).append(" 해체)");
            sb.append(NL);
        }
        sb.append("[전설장비]").append(NL);
        no = 1;
        for (HashMap<String, Object> e : legendaryEquipsOf(userName)) {
            sb.append(no++).append(". ✨").append(strVal(e.get("LEGENDARY_ITEM_NAME"), "전설장비")).append(" +").append(intVal(e.get("ENHANCE_LEVEL"), 0))
              .append(e.get("EQUIPPED_COMPANION_ID") == null ? " (미착용)" : " (착용중)").append(NL);
        }
        sb.append("사용법: /강화권 권번호 장비번호 (강화 수치가 더 낮은 장비에만 쓸 수 있고, 그 수치로 맞춰집니다)");
        return sb.toString();
    }

    @Override
    @Transactional
    public String useEnhanceTicketByNo(String userName, int ticketNo, int equipNo) {
        List<HashMap<String, Object>> tickets = dao.selectEnhanceTickets(userName);
        List<HashMap<String, Object>> legends = legendaryEquipsOf(userName);
        if (ticketNo < 1 || ticketNo > tickets.size()) return "잘못된 강화권 번호입니다. /강화권 으로 목록을 확인하세요.";
        if (equipNo < 1 || equipNo > legends.size()) return "잘못된 장비 번호입니다. /강화권 으로 목록을 확인하세요.";
        return useEnhanceTicket(userName, intVal(tickets.get(ticketNo - 1).get("TICKET_ID"), 0), intVal(legends.get(equipNo - 1).get("EQUIP_ID"), 0));
    }

    // ================================================================
    // [2026-10-20] 성장형 동료(☆1) -- 위 growthMult 설명 참고. 모두에게 무료, 처음 상점/명령어로 열 때 자동 생성.
    // ================================================================
    private HashMap<String, Object> findGrowthCompanion(String userName) {
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("GRADE"), 1) == GROWTH_GRADE) return c;
        }
        return null;
    }

    private HashMap<String, Object> ensureGrowthCompanion(String userName) {
        HashMap<String, Object> g = findGrowthCompanion(userName);
        if (g != null) return g;
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int[] max = computeEffectiveStat(GROWTH_DEFAULT_JOB, GROWTH_GRADE, new ArrayList<HashMap<String, Object>>(), userStat, 0);
        HashMap<String, Object> nc = new HashMap<>();
        nc.put("userName", userName);
        nc.put("class", GROWTH_DEFAULT_JOB);
        nc.put("grade", GROWTH_GRADE);
        nc.put("name", GROWTH_DEFAULT_NAME);
        nc.put("imageUrl", null); // 이미지는 조회 때 TBOT_S5_GROWTH_PORTRAIT로 대체된다(모든 유저 공통)
        nc.put("curHpValue", (double) max[0]);
        nc.put("curHpExt", "");
        nc.put("partySlot", null);
        try {
            dao.insertCompanion(nc);
        } catch (Exception dup) { // 동시 요청으로 이미 생겼으면(유니크 인덱스) 그걸 쓴다
        }
        return findGrowthCompanion(userName);
    }

    @Override
    @Transactional
    public HashMap<String, Object> growthStatus(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        HashMap<String, Object> g = ensureGrowthCompanion(userName);
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        HashMap<String, Object> r = new HashMap<>();
        r.put("exists", g != null);
        if (g == null) return r;
        String job = strVal(g.get("CLASS"), GROWTH_DEFAULT_JOB);
        int n = userStat == null ? 0 : Math.min(GROWTH_FLOORS_FULL, intVal(userStat.get("GROWTH_N"), 0));
        int maxF = userStat == null ? 0 : intVal(userStat.get("GROWTH_MAXF"), 0);
        double mult = growthMult(userStat);
        r.put("companionId", intVal(g.get("COMPANION_ID"), 0));
        r.put("name", strVal(g.get("NAME"), GROWTH_DEFAULT_NAME));
        r.put("job", job);
        r.put("jobName", JOB_NAME.getOrDefault(job, "?"));
        r.put("partySlot", g.get("PARTY_SLOT"));
        r.put("image", g.get("IMAGE_URL"));
        r.put("exploredFloors", n);
        r.put("exploreMax", GROWTH_FLOORS_FULL);
        r.put("stairFloors", growthStairFloors(maxF));
        r.put("stairApplied", n >= GROWTH_FLOORS_FULL);
        r.put("mult", Math.round(mult * 1000) / 1000.0);
        r.put("fullMult", Math.round(growthFullMult() * 1000) / 1000.0);
        r.put("ratioVsStar7", Math.round(mult / star7StatMult() * 100) / 100.0);
        r.put("ratioVsStar6Max", Math.round(mult / (1 + limitBreakPct(LIMIT_BREAK_MAX)) * 100) / 100.0);
        r.put("equipEffectPct", (int) Math.round(GROWTH_EQUIP_EFFECT * 100));
        r.put("inCombat", "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL")));
        List<HashMap<String, Object>> jobs = new ArrayList<>();
        for (String j : JOB_KEYS) {
            int[] st = calcBaseStat(j, GROWTH_GRADE);
            HashMap<String, Object> jm = new HashMap<>();
            jm.put("key", j); jm.put("name", JOB_NAME.get(j));
            jm.put("hp", Math.round(st[0] * mult)); jm.put("atk", Math.round(st[1] * mult)); jm.put("def", Math.round(st[2] * mult));
            jobs.add(jm);
        }
        r.put("jobs", jobs);
        return r;
    }

    /** 채팅 /성장 -- 성장형 동료 현황 텍스트. */
    @Override
    public String growthInfoText(String userName) {
        HashMap<String, Object> st = growthStatus(userName);
        if (!Boolean.TRUE.equals(st.get("exists"))) return "성장형 동료를 만들지 못했습니다.";
        StringBuilder sb = new StringBuilder("🌱 성장형 동료").append(NL);
        sb.append("☆1 ").append(st.get("jobName")).append(" (").append(st.get("name")).append(")").append(NL);
        sb.append("탐사 ").append(st.get("exploredFloors")).append("/").append(st.get("exploreMax")).append("층 완료 · 성장 배율 x").append(st.get("mult"))
          .append(" (풀 성장 x").append(st.get("fullMult")).append(" = ★6 한계돌파+6의 2배, 지금 ★6+6의 ").append(st.get("ratioVsStar6Max")).append("배)").append(NL);
        sb.append("※ 성장형은 장비 효과(전설 특수효과 포함)를 ").append(st.get("equipEffectPct")).append("%만 받습니다.").append(NL);
        sb.append("101층 이상 상승분은 1~100층 탐사를 모두 마친 뒤부터 일괄 반영됩니다 (계단층 ").append(st.get("stairFloors")).append("개 도달").append(Boolean.TRUE.equals(st.get("stairApplied")) ? ", 반영 중" : ", 대기 중").append(")").append(NL);
        sb.append("직업 변경: /성장직업 직업 (전사/마법사/도적/궁수/도사/무녀, 무료, 전투 중 불가) · 이름 변경: /성장이름 새이름 · 장비는 직업과 상관없이 착용");
        return sb.toString();
    }

    @Override
    @Transactional
    public String growthRename(String userName, String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) return "사용법: /성장이름 새이름 (한글/영문/숫자/공백 1~10자)";
        if (name.length() > 10 || !name.matches("[가-힣A-Za-z0-9 ]+")) return "이름은 한글/영문/숫자/공백만 쓸 수 있고 1~10자여야 합니다.";
        HashMap<String, Object> g = ensureGrowthCompanion(userName);
        if (g == null) return "성장형 동료를 찾지 못했습니다.";
        dao.updateCompanionName(intVal(g.get("COMPANION_ID"), 0), userName, name);
        return "✏️ 성장형 동료의 이름을 [" + name + "](으)로 바꿨습니다.";
    }

    /** [2026-10-21] "동료 이미지 변경" 요청 -- 내가 가진 동료끼리 이미지를 서로 맞바꾼다. 화면에 보이는 이미지(IMAGE_URL, ★7/성장형의 고정 초상화 포함)를
     *  그대로 서로에게 개별 이미지(IMAGE_OVERRIDE)로 저장하므로 전투/스탯에는 영향이 없다. 두 번 바꾸면 원래대로 돌아온다. */
    @Override
    @Transactional
    public String swapCompanionImage(String userName, int companionId, int targetId) {
        if (companionId == targetId) return "같은 동료입니다. 이미지를 바꿀 다른 동료를 골라주세요.";
        HashMap<String, Object> a = findCompanionOf(userName, companionId);
        HashMap<String, Object> b = findCompanionOf(userName, targetId);
        if (a == null || b == null) return "동료를 찾을 수 없습니다.";
        String ia = strVal(a.get("IMAGE_URL"), "");
        String ib = strVal(b.get("IMAGE_URL"), "");
        if (ia.isEmpty() || ib.isEmpty()) return "이미지가 아직 없는 동료는 바꿀 수 없습니다. (이미지갱신 후 이용해주세요)";
        if (ia.equals(ib)) return "두 동료의 이미지가 같아 바뀌는 것이 없습니다.";
        dao.updateCompanionImageOverride(companionId, userName, ib);
        dao.updateCompanionImageOverride(targetId, userName, ia);
        return "🖼️ [" + companionLabelForImage(a) + "]와(과) [" + companionLabelForImage(b) + "]의 이미지를 서로 바꿨습니다.";
    }

    private String companionLabelForImage(HashMap<String, Object> c) {
        String nm = strVal(c.get("NAME"), "");
        return nm.isEmpty() ? JOB_NAME.get(strVal(c.get("CLASS"), "WARRIOR")) : nm;
    }

    @Override
    @Transactional
    public String growthChangeJob(String userName, String arg) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) return "전투 중에는 직업을 바꿀 수 없습니다.";
        String job = star7JobKey(arg);
        if (job == null) return "직업을 알 수 없습니다. (전사/마법사/도적/궁수/도사/무녀)";
        HashMap<String, Object> g = ensureGrowthCompanion(userName);
        if (g == null) return "성장형 동료를 찾지 못했습니다.";
        if (job.equals(strVal(g.get("CLASS"), ""))) return "이미 " + JOB_NAME.get(job) + "입니다.";
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        int oldMaxHp = effMaxHpOf(g, userStat);
        dao.updateCompanionClass(intVal(g.get("COMPANION_ID"), 0), userName, job);
        g.put("CLASS", job);
        rescaleHpForEquipChange(g, userStat, oldMaxHp); // 직업 체형이 바뀌어도 체력 비율 유지
        return "🔄 성장형 동료의 직업을 " + JOB_NAME.get(job) + "(으)로 바꿨습니다. 장비는 그대로 유지됩니다.";
    }

    // ================================================================
    // [2026-10-20] ★7 동료 소환/방출
    // 업적 "전설의 시작"(한계돌파 +6 ★5 동료 2체 보유)을 달성하면 원하는 직업의 ★7 동료를 소환할 수 있다. 재료/전설의조각은 소모하지 않는다.
    // 유저당 ★7은 1명(앱 검사 + DB 유니크 인덱스 UX_S5_COMP_STAR7_USER), 방출하면(장비는 벗겨짐) 다시 소환할 수 있다.
    // 초상화는 직업별로 고정(TBOT_S5_STAR7_PORTRAIT) -- 누가 소환하든 같은 직업이면 같은 얼굴.
    // ================================================================
    private int countStar5Lb6(List<HashMap<String, Object>> companions) {
        int n = 0;
        for (HashMap<String, Object> c : companions) {
            if (intVal(c.get("GRADE"), 1) == 5 && intVal(c.get("LIMIT_BREAK"), 0) >= LIMIT_BREAK_MAX) n++;
        }
        return n;
    }

    /** 조건을 채웠으면 업적 "전설의 시작"을 지급한다. @return 이번에 새로 달성했으면 true */
    private boolean checkLegendBegin(String userName) {
        return countStar5Lb6(dao.selectUserCompanions(userName)) >= S7_REQ_G5 && grantAchievement(userName, ACH_LEGEND_BEGIN);
    }

    private boolean hasLegendBegin(String userName) {
        for (HashMap<String, Object> a : dao.selectUserAchievements(userName)) {
            if (intVal(a.get("ACH_ID"), -1) == ACH_LEGEND_BEGIN) return true;
        }
        return false;
    }

    /** 직업별 고정 ★7 초상화(대표 이미지). 표에 없으면 첫 소환자의 랜덤 이미지를 한 번 뽑아 저장해서 이후 모두가 같은 이미지를 쓴다.
     *  ★7 동료의 IMAGE_URL은 조회 때 이 표 값으로 대체되므로(selectUserCompanions/selectCompanionImageUrl) TBOT_S5_STAR7_PORTRAIT를 고치면 기존 ★7에도 바로 반영된다. */
    private String star7PortraitUrl(String job) {
        String url = dao.selectStar7Portrait(job);
        if (url != null && !url.isEmpty()) return url;
        String fetched = fetchRandomNekoImage();
        if (fetched == null || fetched.isEmpty()) return null;
        try {
            dao.insertStar7Portrait(job, fetched);
            return fetched;
        } catch (Exception dup) { // 동시에 다른 유저가 먼저 저장했으면 그 값을 쓴다
            String again = dao.selectStar7Portrait(job);
            return again != null ? again : fetched;
        }
    }

    @Override
    public HashMap<String, Object> star7Status(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        List<HashMap<String, Object>> cs = dao.selectUserCompanions(userName);
        HashMap<String, Object> owned = null;
        for (HashMap<String, Object> c : cs) {
            if (intVal(c.get("GRADE"), 1) == STAR7_GRADE) owned = c;
        }
        int have5 = countStar5Lb6(cs);
        boolean newlyGranted = have5 >= S7_REQ_G5 && grantAchievement(userName, ACH_LEGEND_BEGIN);
        boolean hasAch = newlyGranted || hasLegendBegin(userName);
        HashMap<String, Object> r = new HashMap<>();
        r.put("owned", owned != null);
        if (owned != null) {
            r.put("ownedName", strVal(owned.get("NAME"), "?"));
            r.put("ownedJob", JOB_NAME.getOrDefault(strVal(owned.get("CLASS"), ""), "?"));
        }
        boolean inCombat = "IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"));
        r.put("need5", S7_REQ_G5);
        r.put("have5", have5);
        r.put("hasAchievement", hasAch);
        r.put("newlyGranted", newlyGranted);
        r.put("inCombat", inCombat);
        r.put("canSummon", owned == null && hasAch && !inCombat);
        r.put("canRelease", owned != null && !inCombat);
        List<HashMap<String, Object>> jobs = new ArrayList<>();
        for (String j : JOB_KEYS) {
            int[] st = calcBaseStat(j, STAR7_GRADE);
            double m7 = star7StatMult();
            HashMap<String, Object> jm = new HashMap<>();
            jm.put("key", j); jm.put("name", JOB_NAME.get(j));
            jm.put("hp", Math.round(st[0] * m7)); jm.put("atk", Math.round(st[1] * m7)); jm.put("def", Math.round(st[2] * m7));
            jm.put("portrait", dao.selectStar7Portrait(j));
            jobs.add(jm);
        }
        r.put("jobs", jobs);
        return r;
    }

    private String star7JobKey(String arg) {
        String a = arg == null ? "" : arg.trim();
        for (String j : JOB_KEYS) {
            if (j.equalsIgnoreCase(a) || JOB_NAME.get(j).equals(a)) return j;
        }
        return null;
    }

    /** 채팅 /7성소환 [직업] -- 인자가 없으면 안내, 있으면 그 직업의 ★7 동료를 소환. */
    @Override
    public String star7Command(String userName, String arg) {
        String a = arg == null ? "" : arg.trim();
        if (a.isEmpty()) {
            HashMap<String, Object> st = star7Status(userName);
            StringBuilder sb = new StringBuilder("🌟 ★7 동료 소환").append(NL);
            if (Boolean.TRUE.equals(st.get("newlyGranted"))) sb.append("🏆 업적 [전설의 시작] 달성!").append(NL);
            if (Boolean.TRUE.equals(st.get("owned"))) {
                return sb.append("이미 ★7 동료를 보유 중입니다: ").append(st.get("ownedName")).append(" (").append(st.get("ownedJob")).append(")").append(NL)
                        .append("다시 소환하려면 /7성방출 확인 으로 먼저 방출하세요 (장비는 벗겨집니다).").toString();
            }
            sb.append("조건: 업적 [전설의 시작] -- 한계돌파 +").append(LIMIT_BREAK_MAX).append(" ★5 동료 ").append(st.get("need5")).append("체 보유 (현재 ").append(st.get("have5")).append("체)")
              .append(Boolean.TRUE.equals(st.get("hasAchievement")) ? " ✅ 달성" : " ❌ 미달성").append(NL);
            sb.append("재료·전설의조각 소모 없음. 직업: ");
            for (String j : JOB_KEYS) sb.append(JOB_NAME.get(j)).append(" ");
            sb.append(NL).append("사용법: /7성소환 직업 (예: /7성소환 궁수) -- 능력치는 ★6 한계돌파 최대의 1.2배, 스킬 발현율은 ★6의 1.2배, 한계돌파 없음, 1인 1명");
            return sb.toString();
        }
        String job = star7JobKey(a);
        if (job == null) return "직업을 알 수 없습니다. (전사/마법사/도적/궁수/도사/무녀)";
        return summonStar7(userName, job);
    }

    @Override
    @Transactional
    public String summonStar7(String userName, String job) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) return "전투 중에는 소환할 수 없습니다.";
        if (job == null || !JOB_NAME.containsKey(job)) return "직업 값이 올바르지 않습니다.";
        List<HashMap<String, Object>> cs = dao.selectUserCompanions(userName);
        for (HashMap<String, Object> c : cs) {
            if (intVal(c.get("GRADE"), 1) == STAR7_GRADE) {
                return "이미 ★7 동료(" + strVal(c.get("NAME"), "?") + ")를 보유 중입니다. ★7은 1명만 가질 수 있습니다. (/7성방출 로 방출하면 다시 소환할 수 있어요)";
            }
        }
        boolean newly = checkLegendBegin(userName);
        if (!newly && !hasLegendBegin(userName)) {
            return "업적 [전설의 시작]이 필요합니다. 한계돌파 +" + LIMIT_BREAK_MAX + " ★5 동료 " + S7_REQ_G5 + "체를 모으세요. (현재 " + countStar5Lb6(cs) + "체)";
        }
        // 이 직업의 대표 초상화 -- 아직 없으면 지금 소환하는 사람의 이미지가 모두의 대표가 된다. 이미지를 못 가져오면(외부 API 장애) 대표가 비어 있게 되므로 소환을 미룬다.
        String portrait = star7PortraitUrl(job);
        if (portrait == null) return "초상화 이미지를 가져오지 못했습니다. 잠시 후 다시 시도해주세요. (아무것도 소모되지 않았습니다)";
        String name = NAME_POOL_BY_JOB_GRADE.get(job)[STAR7_GRADE - 1][0];
        int[] stat = calcBaseStat(job, STAR7_GRADE);
        double m7 = star7StatMult();
        stat = new int[]{ (int) Math.round(stat[0] * m7), (int) Math.round(stat[1] * m7), (int) Math.round(stat[2] * m7) }; // 장비 없을 때의 최종 능력치
        HashMap<String, Object> nc = new HashMap<>();
        nc.put("userName", userName);
        nc.put("class", job);
        nc.put("grade", STAR7_GRADE);
        nc.put("name", name);
        nc.put("imageUrl", portrait);
        nc.put("curHpValue", (double) stat[0]);
        nc.put("curHpExt", "");
        nc.put("partySlot", null);
        try {
            dao.insertCompanion(nc); // 유니크 인덱스 위반(동시 요청)이면 여기서 막힌다
        } catch (Exception e) {
            return "이미 ★7 동료를 보유 중이거나 소환 처리 중입니다.";
        }
        int idx = dao.selectUserCompanions(userName).size();
        return (newly ? "🏆 업적 [전설의 시작] 달성!" + NL : "")
                + "🌟✨ ★7 동료 소환! " + name + " (" + JOB_NAME.get(job) + " ★7)" + NL
                + "❤️ " + stat[0] + " ⚔️ " + stat[1] + " 🛡️ " + stat[2] + " (스킬 발현율 x1.2, 한계돌파 없음)" + NL
                + "👉 /파티편성 으로 파티에 편성하세요 (동료 목록 " + idx + "번 근처)";
    }

    /** 채팅 /7성방출 -- 실수 방지를 위해 "확인"을 붙여야 실행된다. */
    @Override
    public String star7ReleaseCommand(String userName, String arg) {
        if (arg == null || !arg.trim().equals("확인")) {
            return "⚠️ ★7 동료를 방출하면 되돌릴 수 없고, 착용한 장비는 모두 벗겨집니다(장비는 사라지지 않습니다)." + NL
                    + "정말 방출하려면 /7성방출 확인 을 입력하세요. 방출 후에는 /7성소환 으로 직업을 다시 골라 소환할 수 있습니다.";
        }
        return releaseStar7(userName);
    }

    @Override
    @Transactional
    public String releaseStar7(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) return "전투 중에는 방출할 수 없습니다.";
        HashMap<String, Object> target = null;
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("GRADE"), 1) == STAR7_GRADE) target = c;
        }
        if (target == null) return "방출할 ★7 동료가 없습니다.";
        int cid = intVal(target.get("COMPANION_ID"), 0);
        int unequipped = 0;
        for (HashMap<String, Object> e : dao.selectEquipByCompanion(cid)) {
            unwearEquipOne(userName, e); // 장비는 사라지지 않고 미착용으로 돌아온다(일반 장비는 같은 종류 스택에 합쳐짐)
            unequipped++;
        }
        dao.deleteCompanion(cid, userName);
        return "👋 ★7 " + JOB_NAME.getOrDefault(strVal(target.get("CLASS"), ""), "동료") + "(" + strVal(target.get("NAME"), "?") + ")를 방출했습니다."
                + (unequipped > 0 ? NL + "착용하던 장비 " + unequipped + "개는 벗겨져 보관함으로 돌아왔습니다." : "")
                + NL + "👉 /7성소환 으로 직업을 골라 다시 소환할 수 있습니다.";
    }

    /** [2026-10-20] 장착 장비 중 전설 특수효과(EFFECT_TYPE)가 type인 장비(없으면 null). 파라미터는 LEGENDARY_EFFECT_PARAM1(selectEquipByCompanion 제공).
     *  전설강화(+N)는 능력치 보너스에만 적용되고 특수효과 수치에는 적용되지 않는다(2026-10-20 결정). */
    private HashMap<String, Object> legendEquipEffect(List<HashMap<String, Object>> equips, String type) {
        if (equips == null) return null;
        for (HashMap<String, Object> e : equips) {
            if (type.equals(strVal(e.get("LEGENDARY_EFFECT_TYPE"), ""))) return e;
        }
        return null;
    }

    /** 전투 중 "불굴"(LAST_STAND, 용왕의 투구): 전투당 1번, 이 동료가 치명적 피해를 받아도 HP 1로 살아남는다. 사용한 동료는 LAST_STAND_USED_CIDS에 기록(전투 종료 때 초기화).
     *  @return 이번에 발동했으면 true(호출자가 HP를 1로 정함) */
    private boolean tryLastStand(String userName, HashMap<String, Object> p, int companionId, List<HashMap<String, Object>> equips, StringBuilder sb) {
        HashMap<String, Object> eq = legendEquipEffect(equips, "LAST_STAND");
        if (eq == null || companionId <= 0) return false;
        List<Integer> used = parseMinionCids(strVal(p.get("LAST_STAND_USED_CIDS"), ""));
        if (used.contains(companionId)) return false;
        HashMap<String, Object> owner = findCompanionOf(userName, companionId);
        if (!legendRoll(100, owner != null && intVal(owner.get("GRADE"), 1) == GROWTH_GRADE)) return false; // [2026-10-23] 발동 확률 50%(성장형 25%), 실패해도 기회는 남음
        used.add(companionId);
        String joined = joinMinionCids(used);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("lastStandUsedCids", joined);
        dao.updateUserProgress(up);
        p.put("LAST_STAND_USED_CIDS", joined);
        sb.append("🐉 [").append(strVal(eq.get("LEGENDARY_ITEM_NAME"), "불굴")).append("] 불굴! 치명적인 피해를 견뎌내 HP 1로 살아남았다!").append(NL);
        return true;
    }

    /** [2026-10-20] 장비 한 개가 동료 스탯에 주는 보너스 [hp, atk, def] -- computeEffectiveStat의 장비 반영식과 똑같이 유지할 것(스탯구매/한계돌파 곱하기 전 값).
     *  withEnhance=false면 전설장비 강화 배율을 뺀 값(강화로 늘어난 몫 = 차이). */
    private double[] equipItemBonus(HashMap<String, Object> e, int[] base, boolean withEnhance) {
        int eg = intVal(e.get("GRADE"), 1);
        double[][] equipV2 = EQUIP_BONUS_V2;
        double[] b = (BALANCE_V2_ENABLED && equipV2 != null && eg - 1 < equipV2.length && equipV2[eg - 1] != null)
                ? equipV2[eg - 1] : EQUIP_BONUS[eg - 1];
        String part = strVal(e.get("PART"), "");
        double sm = "STAT_MULT".equals(strVal(e.get("LEGENDARY_EFFECT_TYPE"), ""))
                ? 1 + (intVal(e.get("LEGENDARY_EFFECT_PARAM1"), 100) / 100.0 - 1) * LEGEND_EFFECT_SCALE : 1.0; // [2026-10-23] 증가분 50%
        int enhLv = e.get("LEGENDARY_ID") == null ? 0 : intVal(e.get("ENHANCE_LEVEL"), 0);
        if (withEnhance && enhLv > 0) sm *= 1 + enhLv * ENHANCE_STAT_PCT_PER_LV / 100.0;
        double hp = 0, atk = 0, def = 0;
        double helmHp = (b[0] + base[0] * b[1]) * sm, weapAtk = (b[2] + base[1] * b[3]) * sm, armorDef = (b[4] + base[2] * b[5]) * sm;
        switch (part) {
            case "HELMET":   hp = helmHp; break;
            case "WEAPON":   atk = weapAtk; break;
            case "ARMOR":    def = armorDef; break;
            case "NECKLACE": atk = weapAtk / 2.0; def = armorDef / 2.0; break;
            case "RING":     atk = weapAtk / 2.0; hp = helmHp / 2.0; break;
            case "BRACELET": def = armorDef / 2.0; hp = helmHp / 2.0; break;
            default: break;
        }
        return new double[]{ hp, atk, def };
    }

    /** [2026-10-20] 장비 기여 계산용 배율 {체력, 공격, 방어}(스탯구매 레벨 x 한계돌파/★7 배율) -- computeEffectiveStat과 같은 곱셈 순서. */
    private double[] contribMultipliers(HashMap<String, Object> comp, HashMap<String, Object> userStat) {
        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int hpLv = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        int lb = intVal(comp.get("LIMIT_BREAK"), 0);
        double lbM = finalStatMult(intVal(comp.get("GRADE"), 1), lb, userStat);
        if (intVal(comp.get("GRADE"), 1) == GROWTH_GRADE) lbM *= GROWTH_EQUIP_EFFECT; // [2026-10-22] 성장형은 장비 효과 50%(이 배율은 장비 기여 행에만 쓰인다)
        return new double[]{ (1 + HP_PCT_PER_LV * hpLv) * lbM, (1 + ATK_PCT_PER_LV * atkMaxLv) * lbM, lbM };
    }

    /** [2026-10-20] 장비 1개의 기여 행 -- HP/ATK/DEF = 동료에게 실제로 올려주는 최종 수치(스탯구매/한계돌파 반영), RAW_* = 장비 자체 수치(배율 곱하기 전),
     *  ENH_ 접두 필드와 RAW_ENH_ 접두 필드 = 그중 전설장비 강화(+N, 1강당 +6%)로 늘어난 몫. */
    private HashMap<String, Object> equipContribRow(HashMap<String, Object> e, int[] base, double[] m) {
        double[] full = equipItemBonus(e, base, true);
        double[] plain = equipItemBonus(e, base, false);
        HashMap<String, Object> r = new HashMap<>();
        r.put("EQUIP_ID", intVal(e.get("EQUIP_ID"), 0));
        r.put("PART", strVal(e.get("PART"), ""));
        r.put("ENHANCE_LEVEL", e.get("LEGENDARY_ID") == null ? 0 : intVal(e.get("ENHANCE_LEVEL"), 0));
        r.put("HP", Math.round(full[0] * m[0]));
        r.put("ATK", Math.round(full[1] * m[1]));
        r.put("DEF", Math.round(full[2] * m[2]));
        r.put("ENH_HP", Math.round((full[0] - plain[0]) * m[0]));
        r.put("ENH_ATK", Math.round((full[1] - plain[1]) * m[1]));
        r.put("ENH_DEF", Math.round((full[2] - plain[2]) * m[2]));
        r.put("RAW_HP", Math.round(full[0]));
        r.put("RAW_ATK", Math.round(full[1]));
        r.put("RAW_DEF", Math.round(full[2]));
        r.put("RAW_ENH_HP", Math.round(full[0] - plain[0]));
        r.put("RAW_ENH_ATK", Math.round(full[1] - plain[1]));
        r.put("RAW_ENH_DEF", Math.round(full[2] - plain[2]));
        return r;
    }

    /** [2026-10-20] 파티에 편성된 동료마다 장비 1개의 기여 행 목록(편성 화면 장비 카드 표시용). 키 = 동료ID 문자열. */
    @Override
    public HashMap<String, List<HashMap<String, Object>>> partyEquipContribution(String userName) {
        HashMap<String, List<HashMap<String, Object>>> out = new HashMap<>();
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") == null) continue;
            int cid = intVal(c.get("COMPANION_ID"), 0);
            int[] base = calcBaseStat(strVal(c.get("CLASS"), "WARRIOR"), intVal(c.get("GRADE"), 1));
            double[] m = contribMultipliers(c, userStat);
            List<HashMap<String, Object>> rows = new ArrayList<>();
            for (HashMap<String, Object> e : dao.selectEquipByCompanion(cid)) rows.add(equipContribRow(e, base, m));
            out.put(String.valueOf(cid), rows);
        }
        return out;
    }

    private HashMap<String, Object> findCompanionOf(String userName, int companionId) {
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (intVal(c.get("COMPANION_ID"), -1) == companionId) return c;
        }
        return null;
    }

    private HashMap<String, Object> statTriple(long hp, long atk, long def) {
        HashMap<String, Object> t = new HashMap<>();
        t.put("hp", hp); t.put("atk", atk); t.put("def", def);
        return t;
    }

    /** 기여 수치가 "그 장비를 뺀 캐릭터 능력치"의 몇 %인지(소수 1자리). 기준이 0 이하면 0. */
    private double pctOf(long contribution, long baseline) {
        if (baseline <= 0 || contribution == 0) return 0.0;
        return Math.round(contribution * 1000.0 / baseline) / 10.0;
    }

    /** 행에 PCT_HP/PCT_ATK/PCT_DEF(기여 / 기준 능력치 %)를 붙인다. */
    private void attachPct(HashMap<String, Object> row, long[] baseline) {
        row.put("PCT_HP", pctOf(((Number) row.get("HP")).longValue(), baseline[0]));
        row.put("PCT_ATK", pctOf(((Number) row.get("ATK")).longValue(), baseline[1]));
        row.put("PCT_DEF", pctOf(((Number) row.get("DEF")).longValue(), baseline[2]));
    }

    /** [2026-10-20] 동료 상세(웹) -- 최종 능력치를 "원래(등급/직업 기본) + 스탯구매 + 한계돌파(★7은 7성 보정) + 장비(강화분 포함)"로 분해하고,
     *  총 배율(스탯구매 x 한계돌파)과 장비별 기여 행(기여가 "그 장비를 뺀 능력치"의 몇 %인지 포함)을 함께 내려준다.
     *  분해는 합이 정확히 맞도록 한계돌파=캐릭터 합계-스탯구매 반영 기본, 장비=total-캐릭터 합계로 구한다(장비별 반올림 오차 때문에 행 합계와 ±1 다를 수 있음). */
    @Override
    public HashMap<String, Object> companionDetail(String userName, int companionId) {
        HashMap<String, Object> comp = findCompanionOf(userName, companionId);
        if (comp == null) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "동료를 찾을 수 없습니다.");
            return err;
        }
        return companionDetailCore(comp, dao.selectUserStat(userName), dao.selectEquipByCompanion(companionId));
    }

    /** [2026-10-21 성능] 편성 화면 API(/api/tower-party-equip-stat)용 -- 파티 동료 전원의 상세(companionDetail)와 장비 기여 행을 한 번에 계산한다.
     *  예전엔 byCompanion(partyEquipContribution)과 detail(partyDetail)이 각각 동료 목록/유저 스탯/장비를 다시 조회했고 detail은 동료마다 또 조회해서(N+1) 한 번에 0.6초가 걸렸다.
     *  이제 동료 목록과 유저 스탯을 한 번만 읽고 동료별 장비도 한 번씩만 읽는다(장비 기여 행은 detail의 equips를 그대로 byCompanion으로 재사용). */
    @Override
    public HashMap<String, Object> partyEquipAndDetail(String userName) {
        HashMap<String, List<HashMap<String, Object>>> by = new HashMap<>();
        HashMap<String, HashMap<String, Object>> detail = new HashMap<>();
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") == null) continue;
            int cid = intVal(c.get("COMPANION_ID"), 0);
            HashMap<String, Object> d = companionDetailCore(c, userStat, dao.selectEquipByCompanion(cid));
            detail.put(String.valueOf(cid), d);
            @SuppressWarnings("unchecked") List<HashMap<String, Object>> rows = (List<HashMap<String, Object>>) d.get("equips");
            by.put(String.valueOf(cid), rows);
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("byCompanion", by);
        out.put("detail", detail);
        return out;
    }

    private HashMap<String, Object> companionDetailCore(HashMap<String, Object> comp, HashMap<String, Object> userStat, List<HashMap<String, Object>> equips) {
        HashMap<String, Object> out = new HashMap<>();
        String job = strVal(comp.get("CLASS"), "WARRIOR");
        int grade = intVal(comp.get("GRADE"), 1);
        int lb = intVal(comp.get("LIMIT_BREAK"), 0);
        int[] total = slotPenalty(computeEffectiveStat(job, grade, equips, userStat, lb), comp, userStat);
        int[] charTotal = slotPenalty(computeEffectiveStat(job, grade, new ArrayList<HashMap<String, Object>>(), userStat, lb), comp, userStat);
        int[] base = calcBaseStat(job, grade);
        int atkMaxLv = userStat == null ? 0 : intVal(userStat.get("ATK_MAX_LV"), 0);
        int hpLv = userStat == null ? 0 : intVal(userStat.get("HP_LV"), 0);
        long buyHp = Math.round(base[0] * (1 + HP_PCT_PER_LV * hpLv));
        long buyAtk = Math.round(base[1] * (1 + ATK_PCT_PER_LV * atkMaxLv));
        long buyDef = base[2];
        double lbM = finalStatMult(grade, lb, userStat);
        out.put("baseRaw", statTriple(base[0], base[1], base[2]));
        out.put("statBuy", statTriple(buyHp - base[0], buyAtk - base[1], 0));
        out.put("lbBonus", statTriple(charTotal[0] - buyHp, charTotal[1] - buyAtk, charTotal[2] - buyDef));
        out.put("equipTotal", statTriple(total[0] - charTotal[0], total[1] - charTotal[1], total[2] - charTotal[2]));
        out.put("total", statTriple(total[0], total[1], total[2]));
        out.put("base", statTriple(buyHp, buyAtk, buyDef)); // 스탯구매까지 반영한 캐릭터 기본(상세 팝업용)
        out.put("lbLabel", grade == GROWTH_GRADE ? "성장 보정" : (grade >= STAR7_GRADE ? "7성 보정" : "한계돌파"));
        out.put("equipEffectPct", grade == GROWTH_GRADE ? (int) Math.round(GROWTH_EQUIP_EFFECT * 100) : 100);
        // 퍼센트 증가율(소수 1자리): 스탯구매(체력/공격), 한계돌파(또는 7성 보정), 둘을 곱한 총 증가율
        HashMap<String, Object> pct = new HashMap<>();
        pct.put("buyHp", Math.round(HP_PCT_PER_LV * hpLv * 1000) / 10.0);
        pct.put("buyAtk", Math.round(ATK_PCT_PER_LV * atkMaxLv * 1000) / 10.0);
        pct.put("lb", Math.round((lbM - 1) * 1000) / 10.0);
        pct.put("totalHp", Math.round(((1 + HP_PCT_PER_LV * hpLv) * lbM - 1) * 1000) / 10.0);
        pct.put("totalAtk", Math.round(((1 + ATK_PCT_PER_LV * atkMaxLv) * lbM - 1) * 1000) / 10.0);
        pct.put("totalDef", Math.round((lbM - 1) * 1000) / 10.0);
        out.put("pct", pct);
        double[] m = contribMultipliers(comp, userStat);
        List<HashMap<String, Object>> rows = new ArrayList<>();
        long enhHp = 0, enhAtk = 0, enhDef = 0;
        for (HashMap<String, Object> e : equips) {
            HashMap<String, Object> row = equipContribRow(e, base, m);
            attachPct(row, new long[]{ total[0] - ((Number) row.get("HP")).longValue(), total[1] - ((Number) row.get("ATK")).longValue(), total[2] - ((Number) row.get("DEF")).longValue() });
            enhHp += ((Number) row.get("ENH_HP")).longValue();
            enhAtk += ((Number) row.get("ENH_ATK")).longValue();
            enhDef += ((Number) row.get("ENH_DEF")).longValue();
            rows.add(row);
        }
        out.put("enhTotal", statTriple(enhHp, enhAtk, enhDef));
        out.put("equips", rows);
        return out;
    }

    /** [2026-10-20] 편성 화면 슬롯 카드 상단 표시용 -- 파티 동료마다 companionDetail. 키 = 동료ID 문자열. */
    @Override
    public HashMap<String, HashMap<String, Object>> partyDetail(String userName) {
        HashMap<String, HashMap<String, Object>> out = new HashMap<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            if (c.get("PARTY_SLOT") == null) continue;
            int cid = intVal(c.get("COMPANION_ID"), 0);
            out.put(String.valueOf(cid), companionDetail(userName, cid));
        }
        return out;
    }

    /** [2026-10-20] 장비변경 팝업용 -- 이 동료가 그 부위에 끼울 수 있는 장비(현재 착용 중 + 미착용)를 끼웠을 때의 기여 행.
     *  기준(baseline) = 그 부위 장비를 뺀 현재 능력치. 각 행의 PCT_*는 기여가 이 기준의 몇 %인지. */
    @Override
    public HashMap<String, Object> equipPreview(String userName, int companionId, String part) {
        HashMap<String, Object> out = new HashMap<>();
        HashMap<String, Object> comp = findCompanionOf(userName, companionId);
        if (comp == null) { out.put("error", "동료를 찾을 수 없습니다."); return out; }
        HashMap<String, Object> userStat = dao.selectUserStat(userName);
        String job = strVal(comp.get("CLASS"), "WARRIOR");
        int grade = intVal(comp.get("GRADE"), 1);
        int[] base = calcBaseStat(job, grade);
        double[] m = contribMultipliers(comp, userStat);
        int[] total = slotPenalty(computeEffectiveStat(job, grade, dao.selectEquipByCompanion(companionId), userStat, intVal(comp.get("LIMIT_BREAK"), 0)), comp, userStat);
        List<HashMap<String, Object>> rows = new ArrayList<>();
        int currentId = 0;
        long[] baseline = { total[0], total[1], total[2] };
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (!strVal(e.get("PART"), "").equals(part)) continue;
            Object wearer = e.get("EQUIPPED_COMPANION_ID");
            if (wearer != null && intVal(wearer, 0) != companionId) continue;
            HashMap<String, Object> row = equipContribRow(e, base, m);
            if (wearer != null) {
                currentId = intVal(e.get("EQUIP_ID"), 0);
                baseline[0] = total[0] - ((Number) row.get("HP")).longValue();
                baseline[1] = total[1] - ((Number) row.get("ATK")).longValue();
                baseline[2] = total[2] - ((Number) row.get("DEF")).longValue();
            }
            rows.add(row);
        }
        for (HashMap<String, Object> row : rows) attachPct(row, baseline);
        out.put("rows", rows);
        out.put("currentEquipId", currentId);
        out.put("baseline", statTriple(baseline[0], baseline[1], baseline[2]));
        return out;
    }

    /** [2026-10-08] 내 ★7 전설장비(착용/미착용 모두), 번호는 EQUIP_ID 오름차순 -- 채팅 /전설강화 번호와 같은 기준. */
    private List<HashMap<String, Object>> legendaryEquipsOf(String userName) {
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (HashMap<String, Object> e : dao.selectUserEquip(userName)) {
            if (intVal(e.get("GRADE"), 1) == 7 && e.get("LEGENDARY_ID") != null) list.add(e);
        }
        Collections.sort(list, (a, b) -> Integer.compare(intVal(a.get("EQUIP_ID"), 0), intVal(b.get("EQUIP_ID"), 0)));
        return list;
    }

    @Override
    public String enhanceLegendaryList(String userName) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        List<HashMap<String, Object>> list = legendaryEquipsOf(userName);
        if (list.isEmpty()) return "강화할 ★7 전설장비가 없습니다. (전설제작으로 먼저 만들어보세요)";
        Map<Integer, String> wearer = new HashMap<>();
        for (HashMap<String, Object> c : dao.selectUserCompanions(userName)) {
            String job = strVal(c.get("CLASS"), "WARRIOR");
            wearer.put(intVal(c.get("COMPANION_ID"), 0), strVal(c.get("NAME"), JOB_NAME.getOrDefault(job, "동료")));
        }
        StringBuilder sb = new StringBuilder("⬆️ 전설강화 (보유 🧩 ").append(intVal(p.get("LEGEND_FRAGMENT"), 0)).append("개)").append(NL);
        int no = 1;
        for (HashMap<String, Object> e : list) {
            int lv = intVal(e.get("ENHANCE_LEVEL"), 0);
            int pity = intVal(e.get("ENHANCE_PITY"), 0);
            Object cid = e.get("EQUIPPED_COMPANION_ID");
            sb.append(no++).append(". ✨").append(strVal(e.get("LEGENDARY_ITEM_NAME"), "전설장비")).append(" +").append(lv)
              .append(" (").append(cid == null ? "미착용" : wearer.getOrDefault(intVal(cid, 0), "착용중")).append(") ")
              .append("성공 ").append(enhanceSuccessPct(lv, pity)).append("% / 🧩").append(enhanceCost(lv)).append(NL);
        }
        sb.append("사용법: /전설강화 번호 (실패해도 강화 수치는 유지되고 조각만 소모됩니다)");
        return sb.toString();
    }

    @Override
    @Transactional
    public String enhanceLegendaryByNo(String userName, int no) {
        List<HashMap<String, Object>> list = legendaryEquipsOf(userName);
        if (no < 1 || no > list.size()) return "잘못된 번호입니다. /전설강화 로 목록을 확인하세요.";
        return enhanceLegendary(userName, intVal(list.get(no - 1).get("EQUIP_ID"), 0));
    }

    @Override
    @Transactional
    public String enhanceLegendary(String userName, int equipId) {
        HashMap<String, Object> p = getOrInitProgress(userName);
        if ("IN_COMBAT".equals(strVal(p.get("STATUS"), "NORMAL"))) {
            return "전투 중에는 전설강화를 할 수 없습니다.";
        }
        HashMap<String, Object> equip = null;
        for (HashMap<String, Object> e : legendaryEquipsOf(userName)) {
            if (intVal(e.get("EQUIP_ID"), 0) == equipId) { equip = e; break; }
        }
        if (equip == null) return "강화할 수 있는 ★7 전설장비를 찾을 수 없습니다.";
        int lv = intVal(equip.get("ENHANCE_LEVEL"), 0);
        int pity = intVal(equip.get("ENHANCE_PITY"), 0);
        int cost = enhanceCost(lv);
        int fragment = intVal(p.get("LEGEND_FRAGMENT"), 0);
        String itemName = strVal(equip.get("LEGENDARY_ITEM_NAME"), "전설장비");
        if (fragment < cost) {
            return "전설의조각이 부족합니다. (보유 " + fragment + "개 / [" + itemName + "] +" + lv + " -> +" + (lv + 1) + " 강화에 " + cost + "개 필요)";
        }
        int remaining = fragment - cost;
        HashMap<String, Object> fragUp = new HashMap<>();
        fragUp.put("userName", userName);
        fragUp.put("legendFragment", remaining);
        dao.updateUserProgress(fragUp);
        p.put("LEGEND_FRAGMENT", remaining);

        int chance = enhanceSuccessPct(lv, pity);
        boolean success = RND.nextInt(100) < chance;
        if (success) {
            dao.updateEquipEnhance(equipId, lv + 1, 0);
            return "✨✨ 강화 성공! [" + itemName + "] +" + lv + " → +" + (lv + 1) + " (장비 성능 +" + ((lv + 1) * ENHANCE_STAT_PCT_PER_LV) + "%)"
                    + NL + "🧩 " + cost + "개 소모 (보유 " + remaining + "개) / 다음 강화: 성공률 " + enhanceSuccessPct(lv + 1, 0) + "%, 🧩" + enhanceCost(lv + 1) + "개";
        }
        dao.updateEquipEnhance(equipId, lv, pity + 1);
        return "💨 [" + itemName + "] +" + lv + " 강화 실패... 강화 수치는 그대로 유지됩니다."
                + NL + "🧩 " + cost + "개 소모 (보유 " + remaining + "개) 🍀 연속 실패 보너스로 다음 성공률 " + enhanceSuccessPct(lv, pity + 1) + "%";
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
            if (!stackable(e)) continue; // 전설은 합성 대상 아님
            // [2026-10-22] 수량(QTY) -- 한 행이 n개면 같은 EQUIP_ID를 n번 넣어 "개수" 단위로 센다(소모도 아래에서 행별 개수로 묶어 뺀다)
            List<Integer> ids = realIdsByKey.computeIfAbsent(clazz + "|" + part + "|" + grade, k -> new ArrayList<>());
            int eid = intVal(e.get("EQUIP_ID"), 0);
            for (int q = qtyOf(e); q > 0; q--) ids.add(eid);
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
            for (int grade = 1; grade <= 5; grade++) { // [2026-10-23] "6성으로의 일괄합성도 되도록" 요청 -- ★5 3개 -> ★6까지 다시 허용(★6은 상한이라 소모 안 함)
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

        Map<Integer, Integer> takeById = new LinkedHashMap<>();
        for (Integer equipId : toDelete) takeById.merge(equipId, 1, Integer::sum);
        for (Map.Entry<Integer, Integer> t : takeById.entrySet()) takeEquipUnits(t.getKey(), t.getValue());
        // 합성으로 순생성된(더 이상 소모되지 않고 남은) 장비만 실제로 DB에 새로 넣는다.
        List<String> resultLines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : virtualCredit.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) continue;
            String[] parts = entry.getKey().split("\\|", 3);
            String clazz = parts[0], part = parts[1];
            int grade = Integer.parseInt(parts[2]);
            addEquipItems(userName, clazz, part, grade, qty); // [2026-10-22] 같은 종류 스택에 수량으로 한 번에
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
