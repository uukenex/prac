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
 * [2026-11-03] 시즌 5.5(광산) -- "기능별로 쪼개도 좋아" 요청으로 BotS5ServiceImpl(1만 5천 줄)에서 광산 부분을 이 부모 클래스로 옮겼다.
 * 빈은 그대로 BotS5ServiceImpl(@Service "core.prjbot.BotS5Service") 하나이고, BotS5ServiceImpl extends S55MineServiceImpl.
 * 탑(시즌 5) 쪽 기능이 필요하면 아래 abstract 메서드로 부른다. 둘이 같이 쓰는 작은 도우미(strVal/intVal 등)는 이 클래스에 있다.
 */
public abstract class S55MineServiceImpl implements BotS5Service {

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO dao;

    // ---- [2026-11-04] 시즌 5.5 웹앱(PWA, /mine/) -- "카카오 로그인은 신규 테이블로 유저 정보 매칭, 최초 접속 때 카카오톡 닉네임을 직접 입력해서
    //  기존 광산 게임 아이디와 맞으면 연결, 없으면 생성해서 연결". 연결은 TBOT_S55_MINE_LINK(카카오 계정 하나 = 캐릭터 하나).
    //  이미 다른 카카오 계정에 연결된 이름은 연결하지 않는다(남의 캐릭터를 가져가지 않게).
    static final int S55_NAME_MAX = 40;

    @Override
    public String s55LinkedUser(String kakaoId) {
        if (kakaoId == null || kakaoId.isEmpty()) return null;
        return dao.selectS55Link(kakaoId);
    }

    @Override
    public boolean s55IsAdmin(String userName) { return isMineAdminName(userName); } // [2026-11-23] 알림 빼기 등은 신발과 상관없이 이름으로

    /** 입력한 이름 다듬기(앞뒤 공백, 줄바꿈/제어문자/꺾쇠 없음, 1~S55_NAME_MAX자) -- 쓸 수 없으면 null. */
    static String s55CleanName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > S55_NAME_MAX || n.matches(".*[\\p{Cntrl}<>\"'].*")) return null;
        return n;
    }

    @Override
    public HashMap<String, Object> s55LinkCheck(String kakaoId, String name) {
        HashMap<String, Object> out = new HashMap<>();
        String n = s55CleanName(name);
        out.put("ok", n != null);
        if (n == null) { out.put("message", "닉네임을 1~" + S55_NAME_MAX + "자로 입력해 주세요. (꺾쇠·따옴표·줄바꿈은 안 돼요)"); return out; }
        out.put("name", n);
        String owner = dao.selectS55LinkByUser(n);
        out.put("taken", owner != null && !owner.equals(kakaoId));
        HashMap<String, Object> row = dao.selectMineUser(n);
        out.put("exists", row != null);
        if (row != null) out.put("lv", mineLv(row));
        else out.put("similar", s55Similar(kakaoId, n)); // [2026-11-04] "비슷한 닉네임 추천" -- 오타로 새 캐릭터를 만들지 않게
        return out;
    }

    /** [2026-11-04] 입력한 이름과 비슷한 기존 광산 캐릭터(최대 3개, 다른 카카오 계정에 연결된 것은 뺀다) -- [{name, lv}].
     *  같은 기준: 대소문자·띄어쓰기 무시하고 같음 / "닉네임/캐릭터"의 앞쪽이나 뒤쪽이 같음 / 한쪽이 다른 쪽을 품음 / 글자 2개 이하 차이. */
    List<HashMap<String, Object>> s55Similar(String kakaoId, String name) {
        String q = name.toLowerCase().replace(" ", "");
        String[] qp = q.split("/", 2);
        List<Object[]> scored = new ArrayList<>();
        for (HashMap<String, Object> u : dao.selectAllMineUsers()) {
            String un = strVal(u.get("USER_NAME"), "");
            if (un.isEmpty()) continue;
            String c = un.toLowerCase().replace(" ", "");
            String[] cp = c.split("/", 2);
            int score = c.equals(q) ? 100
                    : (cp[0].equals(qp[0]) || (cp.length > 1 && qp.length > 1 && cp[1].equals(qp[1])) || (cp.length > 1 && cp[1].equals(q)) || cp[0].equals(q)) ? 80
                    : s55Lev(c, q) <= 2 ? 70 - s55Lev(c, q)
                    : (q.length() >= 2 && (c.contains(q) || q.contains(c))) ? 60 : 0;
            if (score == 0) continue;
            String owner = dao.selectS55LinkByUser(un);
            if (owner != null && !owner.equals(kakaoId)) continue;
            scored.add(new Object[] { score, un, intVal(u.get("M_LV"), 1) });
        }
        scored.sort((a, b) -> (Integer) b[0] - (Integer) a[0]);
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("name", scored.get(i)[1]);
            m.put("lv", scored.get(i)[2]);
            out.add(m);
        }
        return out;
    }

    /** 글자 차이(편집 거리). */
    static int s55Lev(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2) return 99;
        int[] p = new int[b.length() + 1], c = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) p[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            c[0] = i;
            for (int j = 1; j <= b.length(); j++) c[j] = Math.min(Math.min(c[j - 1] + 1, p[j] + 1), p[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = p; p = c; c = t;
        }
        return p[b.length()];
    }

    @Override
    @Transactional
    public HashMap<String, Object> s55Link(String kakaoId, String name) {
        HashMap<String, Object> out = s55LinkCheck(kakaoId, name);
        if (!Boolean.TRUE.equals(out.get("ok"))) return out;
        String n = (String) out.get("name");
        if (Boolean.TRUE.equals(out.get("taken"))) { out.put("ok", false); out.put("message", "이미 다른 카카오 계정에 연결된 닉네임이에요. 본인 캐릭터라면 관리자에게 문의해 주세요."); return out; }
        if (!mineAccess(n)) { out.put("ok", false); out.put("message", "광산은 아직 준비 중이에요."); return out; }
        boolean created = !Boolean.TRUE.equals(out.get("exists"));
        mineRow(n, false); // 없으면 이 이름으로 광산 캐릭터를 만든다(/광산 링크와 같다)
        dao.upsertS55Link(kakaoId, n);
        out.put("created", created);
        out.put("message", created ? "새 광산 캐릭터 '" + n + "'(으)로 시작해요!" : "'" + n + "' 캐릭터와 연결했어요!");
        return out;
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
    static final int MINE_BROKEN_PICK_PCT = 20;
    static final int MINE_PICK_MAX = 200; // 곡괭이 기본(초기) 최대 내구도 -- 새로 들어온 유저도 200으로 시작(컬럼 기본값, 2026-10-23 1000 -> 200)
    /** 대장간 곡괭이 강화 {광석 등급, 필요 개수, 최대(+현재) 내구도 증가}. 캔 광석을 전부 강화에 써도 평균적으로 1회 채굴(내구도 1)보다 조금 덜 돌아오게(약 0.85) 잡았다. */
    static final int[][] MINE_PICK_UPGRADE = { { 1, 50, 10 }, { 2, 20, 10 }, { 3, 10, 10 }, { 4, 5, 10 }, { 5, 1, 5 }, { 6, 1, 20 } };
    static final double MINE_MAX_TILES_PER_SEC = 10.0; // 화면 이동 속도(약 7칸/초)보다 넉넉하게(2026-10-24 9 -> 10, 요청 지연 감안)
    static final long MINE_MOVE_WINDOW_MS = 15_000L;
    static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
    /** 광산상점 상품: {광석 등급, 필요 개수, 처치당 PP 증가}. 광석 1개 값 = 구리 0.1 / 철 0.3 / 은 1 / 금 3 / 미스릴 10 / 별빛석 40 (PP/자동사냥 처치). */
    static final int[][] MINE_SHOP = { { 1, 100, 10 }, { 2, 50, 15 }, { 3, 25, 25 }, { 4, 10, 30 }, { 5, 5, 50 }, { 6, 1, 40 } };

    // [2026-10-23] 광산 아이템(TBOT_S5_MINE_ITEM) -- "어두운 동굴 + 광부 투구로 시야, 광산 레벨이 오르면 더 높은 투구 단계, 모자라면 못 움직임" 요청,
    // "용왕반지 등 미리 만들어 두고 노출만 안 하기, 있으면 바다 던전 입장" 요청.
    //  광부 투구(MINER_HELMET, 단계): 1단계 = 철광석 100개, 2단계 = 은광석 100개, 3단계 = 금광석 100개, 4단계 = 미스릴 100개, 5단계 = 별빛석 100개.
    //   광산 레벨 L(맵 파일 LEVEL) 맵은 투구 L-1단계 이상이어야 들어가고 움직이고 캘 수 있다. 시야 = 투구 단계 - (L-1): 0 아주 어둡게 / 1 잘 보임 / 2 이상 전체.
    //   지금은 광산 레벨 1(동굴 1층)뿐이라 상점에서 2단계까지만 판다(MINE_HELMET_OPEN_MAX) -- 아래층을 열 때 올린다.
    //  용왕반지(DRAGON_RING): 숨김(상점/화면에 안 나오고 아직 얻는 곳 없음). 맵 파일에 "REQUIRE DRAGON_RING"이 있는 맵(바다 던전)은 이게 있어야 들어간다.
    //   [2026-10-24] "물속 진입 대신 물 위를 걷는 옵션" -- 선장 퀘스트로 만들고, 가지고 있으면 물(~) 칸을 걸을 수 있다(mineWalk). 아래 광산 장비 참고.
    static final String MINE_ITEM_HELMET = "MINER_HELMET";
    static final String MINE_ITEM_DRAGON_RING = "DRAGON_RING";
    static final Map<String, String> MINE_ITEM_NAMES = new LinkedHashMap<>();
    static {
        MINE_ITEM_NAMES.put(MINE_ITEM_HELMET, "광부 투구");
        MINE_ITEM_NAMES.put(MINE_ITEM_DRAGON_RING, "용왕의 반지");
        MINE_ITEM_NAMES.put("WYRM_RING", "용의 반지"); // [2026-11-15] 월드보스 드랍
        MINE_ITEM_NAMES.put(S5MineMap.BOAT_ITEM, "1인용 배"); // [2026-10-28] 다도해 뱃길
        MINE_ITEM_NAMES.put(S5MineMap.SHRINE_KEY_ITEM, "신궁 열쇠"); // [2026-10-28] 닌자 두목의 방(신궁 수문장 수색 임무 보상)
    }
    /** 투구 단계별 업그레이드 비용 {광석 등급, 개수}, 인덱스 = 목표 단계 - 1. */
    //  [2026-10-25] 한때 2단계 은 250 / 3단계 금 250으로 올렸다가 같은 날 "이미 단계업한 사람이 있으면 그렇게 올리면 안 된다" -- 3단계까지 산 유저가 있어 원래 값으로 되돌렸다.
    static final int[][] MINE_HELMET_COST = { { 2, 100 }, { 3, 100 }, { 4, 100 }, { 5, 100 }, { 6, 100 } };
    static final int MINE_HELMET_OPEN_MAX = 4; // [2026-10-24] 동굴 3층(레벨 3)이 열려 3단계(금광석 100개)까지, [2026-10-25] 4단계는 왕도 대장장이(3층도 환하게)
    static final int MINE_HELMET_FIELD_MAX = 3; // 마을 대장장이는 3단계까지
    /** [2026-10-25] 4단계 추가 재료("고급템은 칠요석"). */
    static final String[][] MINE_HELMET_EXTRA4 = { { S5MineMap.SEVEN_STONE_CODE, "2" } };

    Map<String, Integer> mineItems(String userName) {
        Map<String, Integer> out = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectMineItems(userName)) out.put(strVal(r.get("ITEM_CODE"), ""), intVal(r.get("ITEM_LEVEL"), 0));
        for (int k = 0; k <= MINE_MERC_K3; k++) { MercXp mx = MINE_MERC_XP.get(mineMercXpKey(userName, k)); if (mx != null && mx.p > 0) out.put(mineMercKey(k, "XPP"), (int) mx.p); } // [2026-11-18] 아직 DB에 안 쓴 용병 경험치(화면용)
        if (isMineAdminName(userName) && mineShoesGm(out)) mineAdminSkills(out); // [2026-11-23] 고침: isMineAdmin(신발 확인 -> mineItems)을 여기서 부르면 서로 끝없이 불러 관리자 접속이 안 됐다 -- 이름 + 방금 읽은 아이템의 신발로 // [2026-11-07] "관리자는 전부 5레벨" -- 배운 단계만 덮어 읽는다(DB는 그대로, 켜는 단계는 스킬 탭에서)
        try { MineWeapon ar = mineArmorOn(out); MINE_ARMOR_HP_OF.put(userName, ar == null ? 0 : mineArmorHpAt(ar.code, mineWeaponGrade(out, ar)) + mineArmorHpEnh(ar.code, mineWeaponGrade(out, ar), mineArmorEnh(out))); } catch (Exception ignore) { } // [2026-11-17] 갑옷 최대 체력(mineHpCap), [2026-11-18] + 갑옷 칸 강화
        return out;
    }
    // [2026-11-17] "갑옷류에 최대 체력 추가 -- 닌자의 갑주 5,000~10,000, 백화 갑주 10만~20만(등급별 상이)" -- 일반(0)에서 신화(7)까지 고르게.
    //  사슬 갑옷 500~1,000 / 무사의 흉갑 1,500~3,000 / 닌자의 갑주 5,000~10,000 / 백화 갑주 10만~20만 / 심판의 갑옷 25만~50만.
    //  사람마다 낀 갑옷의 체력을 아이템을 읽을 때마다 적어 두고(MINE_ARMOR_HP_OF), 최대 체력(mineHpCap)에 더한다(체력 잠금은 더한 값 기준).
    static final java.util.Map<String, int[]> MINE_ARMOR_HP = new java.util.HashMap<>();
    static {
        MINE_ARMOR_HP.put("ARMOR_CHAIN", new int[] { 500, 1_000 }); MINE_ARMOR_HP.put("ARMOR_SAMURAI", new int[] { 1_500, 3_000 }); MINE_ARMOR_HP.put("ARMOR_NINJA", new int[] { 5_000, 10_000 });
        // [2026-11-19] "백화 갑주 이후 갑옷들 최대 체력 증가량 소폭 낮춰 줘, 너무 차이 나" -- 백화 10만~20만 -> 8만~16만, 심판 25만~50만 -> 20만~40만(모두 -20%)
        MINE_ARMOR_HP.put("ARMOR_BAEKHWA", new int[] { 80_000, 160_000 }); MINE_ARMOR_HP.put("ARMOR_IMMORTAL", new int[] { 200_000, 400_000 });
        // [2026-11-21] 고침: "바다 갑옷 유물인데 최대 체력이 없다" -- 바다의 갑옷(110)이 이 표에서 빠져 모든 등급이 0이었다. 백화(80)와 심판(125) 사이, 받는 피해 감소(3,600)와 같은 비율로 14만~28만.
        MINE_ARMOR_HP.put("ARMOR_SEA", new int[] { 140_000, 280_000 });
        MINE_ARMOR_HP.put("ARMOR_WYRM_SKIN", new int[] { 200_000, 400_000 }); // [2026-11-23] 거룡의 피부조각(신화 단일 = 40만)
    }
    static final java.util.concurrent.ConcurrentHashMap<String, Integer> MINE_ARMOR_HP_OF = new java.util.concurrent.ConcurrentHashMap<>();
    static int mineArmorHpAt(String code, int g) { int[] r = MINE_ARMOR_HP.get(code); return r == null || g < 0 ? 0 : r[0] + (r[1] - r[0]) * Math.min(7, g) / 7; }

    static int mineHelmetNeed(S5MineMap m) {
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
        MINE_MIN_LV.put(S5MineMap.ROYALROAD, 70); // [2026-10-27] 왕도 가는 길(11~13단계, 45), [2026-10-28] 왕도 북쪽 샛길로 이름을 바꾸고 17~19단계(성당 옛터보다 살짝 약하게) 70
        MINE_MIN_LV.put(S5MineMap.RUINS, 80); // [2026-10-28] 성당 옛터 -- 타락한 대주교("80레벨 이상 권장")
        MINE_MIN_LV.put(S5MineMap.SNOWROAD, 80); // [2026-10-28] 설산 봉우리 가는 길(20~21단계를 더 세게), 봉우리(얼음 여왕 Lv.95)
        MINE_MIN_LV.put(S5MineMap.PEAK, 90);
        MINE_MIN_LV.put(S5MineMap.WESTROAD, 53); // [2026-11-05] 왕도 서쪽길(매머드 무리 14~23단계, 왕도에서 멀수록)
        MINE_MIN_LV.put(S5MineMap.CAVE2, 21); // [2026-10-24] 동굴 2층(6~7단계), 3층(8~9단계 + 보스), 바닷길(4~5단계)
        MINE_MIN_LV.put(S5MineMap.CAVE3, 25);
        MINE_MIN_LV.put(S5MineMap.CAVE4, 53); // [2026-10-28] 광산 동굴 4층(14~15단계)
        MINE_MIN_LV.put(S5MineMap.ANTNEST, 41);
        MINE_MIN_LV.put(S5MineMap.SNOWCAVE, 41); // [2026-10-28] 설산 동굴(박쥐 11~12단계)
        MINE_MIN_LV.put(S5MineMap.SNOWCAVE2, 49); // [2026-10-28] 설산 동굴 2층(얼음 골렘 13단계) // [2026-10-28] 개미굴(11~13단계 + 여왕개미 14단계, 사막 서쪽에서 빠짐)
        MINE_MIN_LV.put(S5MineMap.SEAROAD, 13);
        MINE_MIN_LV.put(S5MineMap.SNOW1, 33); // [2026-10-26] 설산 1층(10~11단계), 2층(11~12), 3층(12~13), 설산 마을
        MINE_MIN_LV.put(S5MineMap.SNOW2, 37);
        MINE_MIN_LV.put(S5MineMap.SNOW3, 41);
        MINE_MIN_LV.put(S5MineMap.SNOWTOWN, 41);
        // [2026-10-28] 다도해(본섬 16~17단계, 신궁 18~19, 섬은 뒤로 갈수록 16 -> 20)
        // [2026-10-29] "다도해 본섬~다도해 쪽 권장 레벨 표시가 잘못됐다" -- 몬스터를 +4단계 올릴 때(S5MineMobs.tierAt) 권장 레벨을 안 따라 올렸다.
        //  지금 단계(본섬 20~21, 신궁 1~3층 22/23/24, 닌자 두목 25, 섬 20/20/21/21/22/23/24)에 같은 식(가장 약한 몬스터 Lv. - 4 = 4 x 단계 - 7)으로.
        MINE_MIN_LV.put(S5MineMap.ARCHI, 73);
        MINE_MIN_LV.put(S5MineMap.SHRINE, 81);
        MINE_MIN_LV.put(S5MineMap.SHRINE2, 85);
        MINE_MIN_LV.put(S5MineMap.SHRINE3, 89);
        MINE_MIN_LV.put(S5MineMap.SHRINE_BOSS, 93);
        MINE_MIN_LV.put(S5MineMap.FOREST, 90); // [2026-11-08] 마물의 숲 미궁
        MINE_MIN_LV.put(S5MineMap.HARPY_NEST, 95);
        for (String fr : S5MineMap.FOREST_ROOM_IDS) MINE_MIN_LV.put(fr, 90); // [2026-11-11] 두고개식 미궁 방
        MINE_MIN_LV.put(S5MineMap.FOREST_END, 90);
        MINE_MIN_LV.put(S5MineMap.HARPY_FERRY, 95);
        MINE_MIN_LV.put(S5MineMap.DEMON_EAST, 95); // [2026-11-16] 악마의 들판
        MINE_MIN_LV.put(S5MineMap.DEMON_WEST, 95);
        MINE_MIN_LV.put(S5MineMap.DEMON_EAST2, 105); MINE_MIN_LV.put(S5MineMap.DEMON_WEST2, 105); // [2026-11-17] 로드맵
        MINE_MIN_LV.put(S5MineMap.DEMON_EAST3, 115); MINE_MIN_LV.put(S5MineMap.DEMON_WEST3, 115);
        MINE_MIN_LV.put(S5MineMap.LAST_LINE, 125); MINE_MIN_LV.put(S5MineMap.ABYSS_GATE, 125); MINE_MIN_LV.put(S5MineMap.CHAOS, 135); MINE_MIN_LV.put(S5MineMap.DEMON_VILLAGE, 135);
        MINE_MIN_LV.put(S5MineMap.ARCHISEA, 73);
        int[] isleLv = { 73, 73, 77, 77, 81, 85, 89 };
        for (int i = 0; i < S5MineMap.ISLE_IDS.length; i++) MINE_MIN_LV.put(S5MineMap.ISLE_IDS[i], isleLv[i]);
    }

    static int mineMinLv(S5MineMap m) {
        return MINE_MIN_LV.getOrDefault(m.id, 1);
    }

    /** 그 맵에 들어가거나 움직일 수 없는 이유(없으면 null). lv = 광산 전투 레벨(권장 레벨은 막지 않아 지금은 안 쓴다). */
    String mineMapBlock(S5MineMap m, Map<String, Integer> items, int lv) {
        // [2026-11-18] 같은 날 "두 번째 용병과 해양도시, 물보스까지 일반 유저 오픈" -- 바다 위/윤슬항/망망대해 관리자 제한을 풀었다(배는 그대로 필요)/망망대해 -- 지금은 관리자만
        if (m.boat() && items.getOrDefault(S5MineMap.BOAT_ITEM, 0) > 0 && mineBoatHp(items)[0] <= 0) return "⛵ 배가 부서져 있어요. 다도해 본섬 배 목수에게 고쳐 달라고 하세요."; // [2026-11-18]
        if (S5MineMap.CASTLE.equals(m.id) && mineWbOn(System.currentTimeMillis()) && mineWbOk(MINE_CTX.get())) return null; // [2026-11-15] 월드보스 시간엔 누구나 동쪽 성곽에서 싸울 수 있다
        if (S5MineMap.demonTier(m.id) >= 0 || S5MineMap.ABYSS_GATE.equals(m.id) || S5MineMap.CHAOS.equals(m.id) || S5MineMap.DEMON_VILLAGE.equals(m.id)) { // [2026-11-16] "탑 29층 클리어 증표를 가져오라고" -- 29층 수문장을 쓰러뜨린 사람만 성문 밖으로 // [2026-11-17] 들판 2·3, 방어선, 마계도
            String cu = MINE_CTX.get();
            boolean adm = cu != null && isMineAdmin(cu);
            if (S5MineMap.demonTier(m.id) == 3 || S5MineMap.ABYSS_GATE.equals(m.id) || S5MineMap.CHAOS.equals(m.id) || S5MineMap.DEMON_VILLAGE.equals(m.id)) { // [2026-11-17] "서쪽/동쪽 맵의 특수 아이템 두 개를 합쳐 최후의 방어선에 들어갈 수 있게"
                if (!adm && items.getOrDefault(MINE_ITEM_LL_PASS, 0) < 1) {
                    Map<String, Integer> hv = cu == null ? new HashMap<String, Integer>() : mineOreHave(cu);
                    int dk = hv.getOrDefault(S5MineMap.DUSK_SEAL_CODE, 0), dw = hv.getOrDefault(S5MineMap.DAWN_SEAL_CODE, 0);
                    if (dk < 1 || dw < 1) return "🛡️ 방어선 경비병: 이 너머는 최후의 방어선이오. 서쪽 들판의 " + S5MineMap.DUSK_SEAL_NAME + "과 동쪽 들판의 " + S5MineMap.DAWN_SEAL_NAME + "을 하나씩 가져와 맞대면 길이 열리오. (지금 황혼 " + dk + " · 여명 " + dw + ")";
                }
            }
            if (mineTwBlock(items) < MINE_DEMON_NEED_FLOOR && !adm)
                return "🛡️ 성문 수비대장: 멈추시오! 성문 밖은 악마들의 땅이오. 탑 " + MINE_DEMON_NEED_FLOOR + "층 수문장을 쓰러뜨린 증표를 가져오면 문을 열어 주겠소." + (mineTwBlock(items) > 0 ? " (지금 " + mineTwBlock(items) + "층 수문장까지)" : "");
            return null;
        }
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
    static final java.util.concurrent.atomic.AtomicBoolean MINE_RESETTING = new java.util.concurrent.atomic.AtomicBoolean(false);
    static volatile int mineAppliedCycle = Integer.MIN_VALUE; // 아직 DB에서 안 읽음
    static final String MINE_CYCLE_KEY = "MINE_RESET_CYCLE";

    /** 지금 주기의 맵이 준비됐으면 null, 리셋 중이면 안내 응답. 주기가 바뀌었으면 리셋을 시작한다. */
    HashMap<String, Object> mineGate() {
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
    void mineLoadCycle(int want) {
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

    void startMineReset(final int want) {
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
    static final String MINE_PICK_REFILL_KEY = "MINE_PICK_REFILL_DAY";
    static volatile String minePickRefilledDay = null;

    void minePickRefill() {
        String today = java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        if (today.equals(minePickRefilledDay)) return; // [2026-10-28] 성능 -- 광산 요청마다(모든 유저) 잠금을 잡던 것을, 오늘 이미 했으면 잠금 없이 바로 돌아간다
        synchronized (this) {
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

    static boolean mineStaleVersion(S5MineMap map, String ver) {
        return ver != null && !ver.isEmpty() && !ver.equals(map.version());
    }

    static long longOf(Object o, long def) {
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    HashMap<String, Object> mineRow(String userName, boolean forUpdate) {
        HashMap<String, Object> row = forUpdate ? dao.selectMineUserForUpdate(userName) : dao.selectMineUser(userName);
        if (row != null) return row;
        try {
            dao.insertMineUserIfAbsent(userName, 0, System.currentTimeMillis()); // 예전 스태미너 컬럼은 0, 곡괭이 내구도는 컬럼 기본값(1000)
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 동시에 처음 들어온 요청이 먼저 만들었다
        }
        return forUpdate ? dao.selectMineUserForUpdate(userName) : dao.selectMineUser(userName);
    }

    /** [2026-10-28] 잠금 조회 먼저(행이 있으면 한 번으로 끝), 없을 때만 만들고 다시 잠금 조회. */
    HashMap<String, Object> mineRowLocked(String userName) {
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        return row != null ? row : mineRow(userName, true);
    }

    static S5MineMap mineMapOf(HashMap<String, Object> row) {
        return S5MineMap.get(row == null ? null : (String) row.get("MAP_ID"));
    }

    /** 저장된 위치(없거나 지금 맵에서 못 서는 칸이면 그 맵 시작점). water = 용왕의 반지(물 위 걷기). */
    int[] minePos(HashMap<String, Object> row, S5MineMap map, int water) {
        if (row.get("POS_X") != null && row.get("POS_Y") != null) {
            int x = intVal(row.get("POS_X"), map.spawnX), y = intVal(row.get("POS_Y"), map.spawnY);
            if (mineWalk(map, x, y, water)) return new int[] { x, y };
        }
        return new int[] { map.spawnX, map.spawnY };
    }

    /** [2026-10-24] 설 수 있는 칸 -- 용왕의 반지가 있으면 물(~) 칸도. */
    /** [2026-10-28] 설 수 있는 칸 판정 모드 -- 0 보통, 1 물 위(용왕의 반지), 2 모두 통과("운영자 신발 바람의 신 -- 모든 장애물/몬스터 통과"). */
    static int mineWalkMode(Map<String, Integer> items) {
        MineCos shoes = mineWornCos(items, "shoes");
        if (shoes != null && MINE_COS_SPEED.equals(shoes.code)) return 2;
        return mineWaterWalk(items) ? 1 : 0;
    }

    static boolean mineWalk(S5MineMap map, int x, int y, int water) {
        if (water >= 2) return map.inside(x, y); // 바람의 신 -- 맵 안이면 어디든
        return map.walkable(x, y) || ((water >= 1 || map.boat() || map.sail()) && map.inside(x, y) && map.terrainAt(x, y) == '~'); // [2026-10-28] 다도해 뱃길(boat)은 배로 물 위를 다닌다, [2026-11-18] 남쪽 바닷가(sail)는 배가 있으면(화면이 막는다)
    }

    /** stepMs = 화면 한 칸 이동 시간(신속의 장화로 빨라지면 허용 거리도 그만큼 늘린다). */
    static boolean mineMoveOk(int[] from, long fromAt, int x, int y, long now, int stepMs) {
        int dist = Math.abs(x - from[0]) + Math.abs(y - from[1]);
        // 쉬는 시간은 최대 MINE_MOVE_WINDOW_MS까지만 친다 -- 오래 가만히 있다가 먼 곳으로 한 번에 옮기는 것 방지(화면은 이동 중 몇 초마다, 캘 때마다 위치를 저장)
        double perSec = MINE_MAX_TILES_PER_SEC * MINE_STEP_MS[0] / (double) stepMs;
        double allowed = 6 + Math.min(MINE_MOVE_WINDOW_MS, Math.max(0, now - fromAt)) / 1000.0 * perSec;
        return dist <= allowed;
    }

    List<HashMap<String, Object>> mineOreList(String userName) {
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
        String[] sc = { S5MineMap.ICE_STONE_CODE, S5MineMap.MOON_STONE_CODE, S5MineMap.SEAL_CODE, S5MineMap.NINJA_BOOK_CODE, S5MineMap.ICE_HEART_CODE, S5MineMap.DESERT_HEART_CODE, S5MineMap.VOLCANO_HEART_CODE, S5MineMap.THUNDER_HEART_CODE, S5MineMap.WIND_HEART_CODE };
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.Arrays.stream(S5MineMap.WEAPON_SHARD_CODES)).toArray(String[]::new); // [2026-10-29] 무기 조각
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.Arrays.stream(S5MineMap.ARMOR_SHARD_CODES)).toArray(String[]::new); // [2026-11-06] 갑옷 조각
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.stream.Stream.of(S5MineMap.GALAXY_CODE, S5MineMap.TOWER_TOKEN_CODE, S5MineMap.DUSK_SEAL_CODE, S5MineMap.DAWN_SEAL_CODE, S5MineMap.DEMON_ORE_CODE, S5MineMap.DOOM_SHARD_CODE, S5MineMap.LIGHT_HEART_CODE, S5MineMap.DARK_HEART_CODE, S5MineMap.JUDGE_TOKEN_CODE, S5MineMap.MANA_STONE_CODE, S5MineMap.WATER_HEART_CODE, S5MineMap.WP_KIT_CODE, S5MineMap.WOOD_CODE, S5MineMap.PRIMAL_GEM_CODE, S5MineMap.SUN_STONE_CODE, S5MineMap.SQ_MEDAL_CODE)).toArray(String[]::new); // [2026-11-23] 햇빛석 // [2026-11-18] 어둠의 심장, 심판의 증표 // [2026-11-06] 은하수 조각, [2026-11-15] 탑의 증표, [2026-11-17] 인장 둘 / 악마의 광석 조각 / 종말의 파편
        { // [2026-10-28] 괴 / 세공된 보석
            List<String> scl = new ArrayList<>(java.util.Arrays.asList(sc));
            scl.addAll(java.util.Arrays.asList(S5MineMap.BAR_CODES));
            for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) scl.add(S5MineMap.cutCode(i));
            scl.add(S5MineMap.CUT_ICE_CODE); // [2026-10-28] 세공된 빙석 / 세공된 바다 보석
            for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) scl.add(S5MineMap.seaCutCode(i));
            sc = scl.toArray(new String[0]);
        }
        sc = mineWithSeaGems(sc); // [2026-10-28] 다도해 바다 보석(34~38) // [2026-10-28] 달빛석(20), 비급서(32), 얼음의 심장(33)
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
    static String mineDugKey(S5MineMap map, long now, Map<String, Integer> items) {
        if (!S5MineMap.DUNGEON.equals(map.id)) return map.id;
        int dd = mineDungeonDay(items, now); // [2026-10-28] 문지기에게 다른 요일 동굴을 열었으면 그 요일 칸 기록을 따로
        return S5MineMap.DUNGEON + "-" + S5MineMap.dayKey(now) + (dd != S5MineMap.dayIndex(now) ? "-d" + dd : "");
    }

    // [2026-10-28] "요일 동굴 앞에 NPC, 오늘의 요일석 20개를 내면 골라서 다른 요일 동굴에" -- 요일 동굴 문지기(평원 서쪽 통로 안쪽).
    //  고른 요일은 오늘 하루만(DUNGEON_DAY = yyyyMMdd * 10 + 요일 0~6) -- 그동안 요일 던전에서 그 요일의 돌이 나오고 캔 칸 기록도 그 요일로 따로.
    //  같은 요일로 다시 들어가기와 오늘 요일로 되돌리기는 무료.
    static final String MINE_ITEM_DUNGEON_DAY = "DUNGEON_DAY";
    static final int MINE_DAYGATE_COST = 20;

    /** 요일 동굴 문지기 -- day(0 = 월 ... 6 = 일)의 요일 동굴로 들어간다. 오늘이 아닌 요일을 처음 열 때만 오늘의 요일석 MINE_DAYGATE_COST개.
     *  오늘 요일을 고르면 무료로 되돌린다. 평원에서만(문지기가 평원 서쪽 통로 안쪽에 있다), 전투 중엔 안 됨. 성공하면 요일 던전 시작점으로 옮긴다. */
    @Override
    @Transactional
    public HashMap<String, Object> mineDayGate(String userName, int day) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (day < 0 || day > 6) { out.put("message", "어느 요일 동굴로 갈지 골라 주게."); return out; }
        if (!S5MineMap.FIELD.equals(strVal(row.get("MAP_ID"), S5MineMap.FIELD))) { out.put("message", "요일 동굴 문지기는 중앙 평원 서쪽 통로 앞에 있어요."); return out; }
        if (mineEngaged(userName, now)) { out.put("message", MINE_FIGHT_BLOCK); return out; }
        Map<String, Integer> items = mineItems(userName);
        int today = S5MineMap.dayIndex(now), cur = mineDungeonDay(items, now);
        String paid = "";
        if (day == today) dao.upsertMineItem(userName, MINE_ITEM_DUNGEON_DAY, 0); // 오늘 요일로 되돌리기(무료)
        else if (day != cur) {
            String[][] cost = { { S5MineMap.DAY_STONE_CODES[today], String.valueOf(MINE_DAYGATE_COST) } };
            if (!mineCostOk(cost, mineOreHave(userName))) { out.put("message", "🕳️ " + S5MineMap.DAY_STONE_NAMES[today] + " " + MINE_DAYGATE_COST + "개가 있어야 다른 요일 동굴 문을 열어 줄 수 있네."); return out; }
            mineTakeCost(userName, cost, 1);
            dao.upsertMineItem(userName, MINE_ITEM_DUNGEON_DAY, mineToday() * 10 + day);
            paid = " (" + S5MineMap.DAY_STONE_NAMES[today] + " " + MINE_DAYGATE_COST + "개)";
        }
        S5MineMap dg = S5MineMap.get(S5MineMap.DUNGEON);
        dao.updateMinePos(userName, dg.id, dg.spawnX, dg.spawnY, now);
        mineDisengageAll(userName);
        out.put("ok", true);
        out.put("message", "🕳️ " + S5MineMap.DAY_LABELS[day] + " 동굴로 들어왔어요! 여기선 " + S5MineMap.DAY_STONE_NAMES[day] + "이 나와요." + paid);
        out.put("state", mineState(userName));
        return out;
    }

    /** 이 유저의 오늘 요일 던전 요일(문지기로 바꿨으면 그 요일, 아니면 오늘). */
    static int mineDungeonDay(Map<String, Integer> items, long now) {
        int v = items == null ? 0 : items.getOrDefault(MINE_ITEM_DUNGEON_DAY, 0);
        return v / 10 == mineToday() ? v % 10 : S5MineMap.dayIndex(now);
    }

    /** 캔 칸 등급 값 -> 이름(1~6 일반 광석, 11~17 요일석, 18 칠요석, 21~29 보석 -- 바닷길에서 캔 칸). */
    static String mineGradeName(int g) {
        if (g == S5MineMap.SEVEN_STONE_GRADE) return S5MineMap.SEVEN_STONE_NAME;
        if (g >= S5MineMap.GEM_GRADE_BASE && g < S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_NAMES.length) return S5MineMap.GEM_NAMES[g - S5MineMap.GEM_GRADE_BASE];
        if (g >= 1 && g <= S5MineMap.ORE_NAMES.length) return S5MineMap.ORE_NAMES[g - 1];
        int d = g - S5MineMap.DAY_STONE_GRADE_BASE;
        return d >= 0 && d < S5MineMap.DAY_STONE_NAMES.length ? S5MineMap.DAY_STONE_NAMES[d] : "";
    }

    int minePickMax(HashMap<String, Object> row) {
        return intVal(row.get("PICK_DUR_MAX"), MINE_PICK_MAX);
    }

    int minePickDur(HashMap<String, Object> row) {
        return Math.max(0, intVal(row.get("PICK_DUR"), MINE_PICK_MAX));
    }

    static void putMinePick(HashMap<String, Object> out, int dur, int max) {
        out.put("durability", dur);
        out.put("durabilityMax", max);
    }

    /** 대장간 곡괭이 강화 표(화면용). */
    static List<HashMap<String, Object>> minePickUpgradeList() {
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
        if (!map.board().isEmpty()) { List<int[]> bd = new ArrayList<>(); for (int[] b : map.board()) bd.add(new int[] { b[0], b[1], b[2] }); out.put("board", bd); } // [2026-11-15] 탑 보드 칸 {x, y, 종류}
        if (S5MineMap.towerFloor(map.id) > 0) out.put("tower", S5MineMap.towerFloor(map.id));
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
        if (map.abyssPortal() != null) out.put("abyss", map.abyssPortal()); // [2026-11-17] 마계 입구 -- 화면이 소용돌이를 그린다
        // [2026-10-24] 몬스터(집 칸 + 돌아다니는 칸, 위치는 시각으로 화면이 계산) + 종류 표 + 배 항로
        List<HashMap<String, Object>> mobs = new ArrayList<>();
        for (S5MineMap.Mob mb : map.mobs()) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", mb.id);
            m.put("k", mb.type);
            m.put("c", mb.cells);
            if (S5MineMobs.TYPES.get(mb.type).minion) m.put("d", 1); // [2026-10-28] 대주교 수하 -- 불려 나오기 전엔 없음
            if (mb.rank != 1) m.put("r", mb.rank); // [2026-10-29] 개체 단계(없으면 보통)
            mobs.add(m);
        }
        out.put("mobs", mobs);
        out.put("mobTypes", mineMobTypes());
        out.put("mobSlotMs", S5MineMobs.SLOT_MS);
        if (S5MineMap.SHIP_PORTS.contains(map.id) || S5MineMap.ARCHI.equals(map.id)) {
            List<HashMap<String, Object>> ship = new ArrayList<>();
            for (String port : S5MineMap.ARCHI.equals(map.id) ? java.util.Collections.singletonList(S5MineMap.ISLAND) : S5MineMap.SHIP_PORTS) { // [2026-10-28] 본섬 배는 남쪽 섬으로만
                if (port.equals(map.id)) continue;
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", port);
                m.put("name", S5MineMap.nameOf(port));
                ship.add(m);
            }
            if (S5MineMap.ISLAND.equals(map.id)) { // [2026-10-28] 남쪽 섬 -> 다도해 본섬(운영자만 -- 화면은 운영자에게만 보여 주고 서버도 막는다)
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", S5MineMap.ARCHI);
                m.put("name", S5MineMap.nameOf(S5MineMap.ARCHI));
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
                if (S5MineMap.SNOWTOWN.equals(d) || S5MineMap.CITY.equals(d) || S5MineMap.ARCHI.equals(d) || S5MineMap.SNOWROAD.equals(d) || S5MineMap.TOWER_CITY.equals(d)) m.put("needVisit", true); // [2026-10-26] 한 번 걸어서 가 본 뒤에만(화면도 그때만), [2026-10-27] 왕도도
                gl.add(m);
            }
            out.put("guideTo", gl);
        }
        // [2026-10-26] 한기 단계와 안전지대(모닥불) {x, y, 반지름} -- 화면이 모닥불을 그리고, 한기 맵이면 1초마다 mineColdTick
        out.put("cold", map.cold());
        out.put("safe", map.safeZones());
        out.put("citizens", map.citizens()); // [2026-10-27] 왕도 주민이 오가는 길(말풍선만)
        out.put("raid", S5MineMap.RUINS.equals(map.id)); // [2026-10-28] 성당 옛터 -- 화면이 1초마다 환경 턴(대주교 마법)
        out.put("ice", S5MineMap.PEAK.equals(map.id)); // [2026-10-28] 설산 봉우리 -- 얼음 여왕 빙결(환경 턴), 얼음땡
        out.put("hot", mineHotMap(map)); // [2026-10-28] 더운 곳(방한 장비를 끼면 더위 피해) -- 화면이 1초마다 환경 턴
        out.put("noDig", !map.hasOre() && !map.veinMine());
        if (map.hasOre()) out.put("noOre", map.noOreRuns()); // [2026-11-11] 못 캐는 칸 -- 자동채광이 건너뛴다
        if (!map.trees().isEmpty()) out.put("trees", map.trees()); // [2026-11-08] 하피의 둥지 대형 나무(왼위 x, y -- 3x2칸)
        out.put("thunder", S5MineMap.WESTROAD.equals(map.id)); // [2026-11-05] 마물의 숲 입구 -- 화면이 1초마다 환경 턴(천둥 황소 장판/전체 감전)
        out.put("vein", map.veinMine()); // [2026-10-28] 설산 동굴 -- 앞의 얼음 광맥을 연속 채광
        if (map.mazeLeft() >= 0) out.put("mazeLeft", map.mazeLeft()); // [2026-11-11] 미궁 힌트 -- 끝방까지 몇 걸음
        out.put("noMap", map.noMap()); // [2026-10-28] 개미굴 -- 지도가 안 보이는 곳 // [2026-10-28] 캘 칸이 없는 맵 -- 화면이 캐기 버튼(옆에 몬스터가 있을 때만 공격 버튼)과 자동채광을 숨긴다
        if (map.outdoor()) { // [2026-11-05] "모든 필드에 밤과 낮" -- 바깥 맵은 화면이 같은 시각으로 밤을 어둡게
            out.put("outdoor", true);
            out.put("dayAnchor", S5MineMap.RESET_ANCHOR_MS);
            out.put("dayMs", S5MineMap.ARCHI_DAY_MS);
            out.put("dayCycleMs", S5MineMap.ARCHI_CYCLE_MS);
            if (S5MineMap.TOWER_CITY.equals(map.id)) out.put("alwaysNight", true); // [2026-11-18] "요새 도시는 항상 밤"
        }
        if (map.sail()) out.put("sail", true); // [2026-11-18] 남쪽 바닷가 -- 1인용 배가 있으면 물 위로(지금은 관리자만, 화면)
        if (map.boat()) out.put("boat", true); // [2026-11-18] "배 이미지가 없다" -- 다도해 맵에만 실려서 바다 위/망망대해는 화면이 배 맵인 줄 몰랐다(배 그림/낚시 버튼/물 위 이동)
        if (map.seaBarrels() != null) out.put("barrels", map.seaBarrels()); // [2026-11-18] 크라켄 해역 화약통(같은 이유로 밖으로)
        if (S5MineMap.isArchi(map.id)) { // [2026-10-28] 다도해 -- 낮 햇빛(초당 %), 배로 다니는 뱃길, 낮밤 계산(화면이 같은 시각으로)
            out.put("archi", true);
            out.put("sun", map.sun());
            out.put("dayAnchor", S5MineMap.RESET_ANCHOR_MS);
            out.put("dayMs", S5MineMap.ARCHI_DAY_MS);
            out.put("dayCycleMs", S5MineMap.ARCHI_CYCLE_MS);
        }
        return out;
    }

    static List<HashMap<String, Object>> mineMobTypes() {
        List<HashMap<String, Object>> out = new ArrayList<>();
        for (S5MineMobs.Type ty : S5MineMobs.TYPES) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("name", ty.name);
            m.put("tier", ty.tier);
            m.put("kind", ty.kind);
            m.put("color", ty.color);
            m.put("hp", ty.hp());
            m.put("boss", ty.boss);
            m.put("el", ty.elem()); // [2026-10-29] 속성
            if (ty.dummy) m.put("dummy", true);
            if (ty.sim) { m.put("sim", ty.simIndex()); m.put("lv", ty.simOf().lvShow > 0 ? ty.simOf().lvShow : 1 + 4 * (ty.simOf().tier - 1)); } // [2026-11-07] 보스 허수아비(그림 = 그 보스)
            if (ty.stay) m.put("stay", true); // [2026-10-28] 대주교 -- 제자리, 4칸 안에서 선공
            if (ty.champ) m.put("champ", true); // [2026-11-16] 챔피언 이름표
            if (ty.chaos) { m.put("chaos", true); m.put("src", ty.src); if (ty.chaosBoss) m.put("cboss", true); } // [2026-11-17] 악마화된 몬스터(원래 그림)
            if (ty.demon) m.put("demon", true); // [2026-11-16] 악마(크게 그리고, 순간이동은 걷지 않고 번쩍)
            if (ty.minion) m.put("minion", true); // [2026-10-28] 레벨 차이 선공에서 뺀다(화면)
            if (ty.lvShow > 0) m.put("lv", ty.lvShow); // [2026-10-28] 얼음 여왕 -- 화면 레벨 Lv.95
            if (ty.foot() > 2) m.put("foot", ty.foot()); // [2026-11-15] 거룡 3x3(보스 기본 2x2)
            if (ty.boss) m.put("aggro", MINE_BOSS_AGGRO_RANGE); // [2026-10-28] 보스 선공 거리 12칸
            if (ty.ninja) m.put("ninja", true); // [2026-10-28] 은신(화면은 반투명)
            if (!ty.boss && ty.aggro > 0) m.put("aggro", ty.aggro); // [2026-11-05] 왕도 서쪽길 무리 -- 화면도 이 거리 안이면 전투 턴
            if (ty.firstStrike) m.put("first", true); // [2026-10-28] 설산 동굴 박쥐 -- 늘 선공(화면도 6칸 안이면 전투 턴)
            if (ty.sea) m.put("sea", true); // [2026-11-18] 바다 몬스터(선인식이면 눈/이름이 빨갛게)
            if (ty.dive) m.put("dive", true); // [2026-11-18] 바다 속으로 숨었다 나왔다(화면 seaDiving = 서버 S5MineMobs.diving)
            if (ty.clone) m.put("clone", true); // [2026-10-28] 닌자 두목의 분신 -- 화면은 두목(맵 마지막 몬스터)과 똑같이 그린다
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
    static final int MINE_LV_MAX = 99;
    // [2026-11-08] "진환생은 99레벨 이후도 쭉 100레벨 이상으로 레벨업(능력치는 99레벨에서 멈춤)", "최고 레벨 제한 없이" -- 진환생은 끝없이 오른다(MINE_LV_TRUE_MAX = M_LV NUMBER(4) 끝).
    //  능력치(체력 mineHpMax / 공격력 mineAtk / 선공 레벨 차이 mineMobPreempts)는 99레벨 값에서 멈춘다.
    static final int MINE_LV_TRUE_MAX = 9999;
    static int mineLvCap(int rb) { return rb >= MINE_REBIRTH_BASE ? MINE_LV_TRUE_MAX : MINE_LV_MAX; } // [2026-11-18] "5환생 이전은 100레벨 못 찍게" -- 0~4환생은 99에서 멈추고, 5환생·진환생만 99를 넘는다
    /** [2026-11-08] "진환생은 무기/방어구 장착 제한 해제" -- 장비 레벨(20/40/60/80...)이 모자라도 든다. */
    static boolean mineGearLvOk(int lv, Map<String, Integer> items, int need) { return lv >= need || mineTrueRebirth(items); }
    static final long MINE_HP_FULL_MS = 60_000L;
    static final long MINE_ATTACK_GAP_MS = 380L;
    static final long MINE_MOB_FREEZE_MS = 8_000L;
    static final long MINE_MOB_HEAL_MS = 30_000L;

    static final class MobState {
        int hp;
        long deadUntil, frozenUntil, lastHitAt;
        int fx, fy;
        String target;   // [2026-10-25] 쫓아가는 유저(인식 중)
        long lastMoveAt; // 마지막으로 한 칸 쫓아온 시각
        boolean stealth;  // [2026-10-28] 닌자 은신 중(공격을 피했고 다음 한 방이 2배)
        int stealthHits;  // [2026-10-28] 닌자 두목 -- 은신 중 받은 공격 수(MINE_BOSS_STEALTH_HITS번째에 풀림)
        long splitUntil;  // [2026-10-28] 닌자 두목 분신술이 끝나는 시각(그 전엔 제자리, 안 때림)
        int splitStage;   // 지금까지 쓴 분신술 횟수(체력 75/50/25% 아래로 내려갈 때마다 한 번)
        java.util.Set<String> hitters; // [2026-10-28] 닌자 두목을 때린 유저(처치 때 첫 보상)
        List<Integer> fxG; // [2026-11-17] 보스가 실제로 떨어뜨린 것(한 사람 몫, 등급 x 개수) -- 쓰러진 연출(bossFx)이 이만큼만 쏟아진다
        long regenAt;     // [2026-10-29] 보스 체력 회복을 마지막으로 반영한 시각
        long stunUntil;   // [2026-11-07] 일섬참에 맞아 기절(이때까지 움직이지도 때리지도 기술을 쓰지도 않는다)
        long vulnUntil; int vulnPct; // [2026-11-08] 만검진 -- 받는 피해 +vulnPct%(이때까지)
        long nearAt;      // [2026-10-30] 보스 MINE_BOSS_ALONE_RANGE칸 안에 누가 있던 마지막 시각
        int strikeN;      // [2026-11-15] 탑 수문장 -- 때린 횟수(강타 차례)
        long fxAt;        // [2026-11-16] 쓰러진 것을 위치 알림이 처음 본 시각(전리품 연출을 한 번만, -1 = 연출 없음)
        long[] pat;       // [2026-11-16] 탑 수문장 패턴마다 다음에 쓸 시각
        boolean kraken;   // [2026-11-18] 심해의 크라켄 -- 받는 피해 x MINE_KRK_ARMOR(기절 중엔 x MINE_KRK_STUN_MUL)
        MobState(int hp) { this.hp = hp; }
        /** 다시 나타날 때(체력 가득) 닌자 상태도 처음으로. */
        void respawn(int max) { deadUntil = 0; hp = max; stealth = false; stealthHits = 0; splitUntil = 0; splitStage = 0; hitters = null; fxAt = 0; fxG = null; pat = null; }
    }

    static final java.util.concurrent.ConcurrentHashMap<String, MobState> MINE_MOB_STATE = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_LAST_ATTACK = new java.util.concurrent.ConcurrentHashMap<>();

    // [2026-11-15] "탑은 1인 인스턴스 던전 형태" -- 1인 층(S5MineMap.solo)의 몬스터 상태는 유저마다 따로 둔다: 키 = 맵 버전 + "@유저:" + 번호.
    //  누구의 요청인지는 MINE_CTX(요청 스레드마다 -- mineAccess / mineCtx가 적는다)로 안다. 층을 옮기거나 쓰러지면 내 인스턴스를 비운다(mineSoloReset -- 다시 들어가면 처음부터).
    //  그래서 탑에서는 남의 몬스터를 때리거나 수문장을 함께 잡을 수 없고(보상도 혼자), 다른 사람도 안 보인다(minePresence).
    static final ThreadLocal<String> MINE_CTX = new ThreadLocal<>();
    static String mineMobPrefix(S5MineMap map) {
        if (map.solo()) { String u = MINE_CTX.get(); if (u != null) return map.version() + "@" + u + ":"; }
        return map.version() + ":";
    }
    static String mineMobKey(S5MineMap map, int id) {
        return mineMobPrefix(map) + id;
    }
    /** 이 요청이 누구 것인지 적어 둔다(1인 층의 몬스터 상태를 가리려고) -- 유저 이름 없이 몬스터 목록만 묻는 주소에서 부른다. */
    @Override
    public void mineCtx(String userName) { MINE_CTX.set(userName == null || userName.trim().isEmpty() ? null : userName); }
    /** 내 탑 인스턴스 비우기 -- 층을 옮기거나 쓰러지면. */
    static void mineSoloReset(String userName) {
        if (userName == null) return;
        String tag = "@" + userName + ":";
        MINE_MOB_STATE.keySet().removeIf(k -> k.contains(tag));
        MINE_ZONES.keySet().removeIf(k -> k.contains(tag)); // [2026-11-16] 내 장판도
    }

    // [2026-10-28] "99레벨 경험치 꽉 차면 왕도 12시 성당 신부에게 환생, 환생하면 체력 1.5배, 필요 경험치 3배, 레벨 1로(Lv1(환생1)), 날개 이펙트" --
    //  환생 횟수 rb(0~5, TBOT_S5_MINE_ITEM의 REBIRTH)만큼 겹쳐 곱한다: 최대 체력 x 1.5^rb, 필요 경험치 x 3^rb. 공격력은 그대로.
    static final String MINE_ITEM_REBIRTH = "REBIRTH";
    // [2026-11-08] "5환생 이후 진환생 -- 진환생1로 시작하고 6환생과 같은 개념" -- 환생 횟수 rb를 그대로 이어서 센다(6 = 진환생1 ... 10 = 진환생5).
    //  효과(체력/공격력/광물/방한·햇빛·동굴 밝기 +회차, 필요 경험치 x2^회차)도 그대로 이어진다. 진환생부터는 왕도 상인 행운의 주문서(금괴, 경험치 3배)를 못 쓴다(mineLuckUntil).
    static final int MINE_REBIRTH_BASE = 5, MINE_REBIRTH_MAX = 6; // [2026-11-08] "진환생은 1만 있어"

    static int mineRebirth(Map<String, Integer> items) { return Math.max(0, Math.min(MINE_REBIRTH_MAX, items.getOrDefault(MINE_ITEM_REBIRTH, 0))); }
    static boolean mineTrueRebirth(Map<String, Integer> items) { return mineRebirth(items) > MINE_REBIRTH_BASE; }
    /** "환생3" / "진환생1"(6환생). */
    static String mineRbName(int rb) { return rb > MINE_REBIRTH_BASE ? "진환생" + (rb - MINE_REBIRTH_BASE) : "환생" + rb; }
    // [2026-10-28 수정] "환생 1회당 기본 체력 +100%(x회차), 필요 경험치 5배(회차 제곱), 전투/채굴 광물 획득량 +100%(x회차), 방한/햇빛 피해 1%(x회차) 경감" -- [2026-10-29] 방한/햇빛/동굴 밝기 단계 +1(x회차)로 바꿈
    //  -> 최대 체력 x (1 + rb)([2026-10-30] x (1 + 0.5 rb)), 필요 경험치 x 2^rb([2026-10-28] 5^rb에서), 광물 획득 x (1 + rb)(mineRebirthGain), 한기/눈바람/방한 장비 더위 피해량 x (1 - 0.01 rb)(mineColdTick rbCut).
    //  [2026-10-29] 지금은 환생 회차만큼 방한 단계(mineColdDef), 햇빛 저항 단계(mineSunPct), 동굴 밝기(화면 sight) +1씩.
    /** [2026-10-30] "체력이 과하다 -- 환생당 +50%로" -- x (1 + rb)(5환생 x6)에서 x (1 + 0.5 rb)(5환생 x3.5). */
    static final double MINE_RB_HP_BONUS = 0.5;
    // [2026-11-17] "99레벨 이후도 조금씩 더 강해지게, 진환생 전에도 99레벨 이후 레벨업 가능, 진환생은 선택(강제하는 버튼/안내 없게)" --
    //  5환생까지도 99에서 멈추지 않고 계속 오른다(mineExpAdd).
    //  같은 날 "진환생 전후가 왜 이렇게 차이 나? 똑같아야 -- 지금 기준에서 변경 없게, 99 이후는 레벨이 오를수록 공격력은 더 오르고 체력은 덜" --
    //  처음엔 99레벨 값의 1%씩(환생 체력 배수까지 곱해져 진환생이 4배로 올랐다) -> 99 넘은 만큼 모두에게 같은 양을 더한다(99까지는 그대로):
    //  k번째 레벨(100레벨 = 1) 공격력 +(MINE_OVER_ATK0 + MINE_OVER_ATK_UP x (k-1)), 체력 +max(MINE_OVER_HP_MIN, MINE_OVER_HP0 - MINE_OVER_HP_DOWN x (k-1)).
    static final int MINE_OVER_ATK0 = 300, MINE_OVER_ATK_UP = 10, MINE_OVER_HP0 = 5_800, MINE_OVER_HP_DOWN = 150, MINE_OVER_HP_MIN = 500; // 99레벨 직전 한 레벨치(공격 +298, 체력 +5,789 -- 환생 전 기준)에서 이어진다
    static long mineOverAtk(int lv) { long n = Math.max(0, lv - MINE_LV_MAX); return n * MINE_OVER_ATK0 + MINE_OVER_ATK_UP * n * (n - 1) / 2; }
    static long mineOverHp(int lv) { long n = Math.max(0, lv - MINE_LV_MAX), s = 0; for (long k = 1; k <= n; k++) s += Math.max(MINE_OVER_HP_MIN, MINE_OVER_HP0 - MINE_OVER_HP_DOWN * (k - 1)); return s; }
    // [2026-11-17] "환생 시 능력치 보존 -- 0환생 99에서 1환생하면 0환생 5레벨 기준 스탯으로 시작, 2환생 10, 3환생 20, 4환생 30, 5환생 40, 진환생 50.
    //  4환생이면 1~30레벨 기준 스탯은 같다. 환생 보너스는 별도" -- 기본 체력/공격력을 정할 때 레벨을 환생 바닥(MINE_RB_FLOOR_LV[환생 횟수]) 아래로 내리지 않는다.
    //  환생 보너스(체력 +50%, 공격력 +50% / 회)는 그 위에 그대로 곱한다. 용병(환생 0)은 그대로.
    static final int[] MINE_RB_FLOOR_LV = { 1, 5, 10, 20, 30, 40, 50 };
    static int mineStatLv(int lv, int rb) { return Math.max(lv, MINE_RB_FLOOR_LV[Math.max(0, Math.min(rb, MINE_RB_FLOOR_LV.length - 1))]); }
    static int mineAtkP(int lv, Map<String, Integer> items) { return mineAtk(mineStatLv(lv, mineRebirth(items))); }
    static int mineHpMax(int lv, int rb) { return (int) Math.round(mineHpBase(Math.min(MINE_LV_MAX, mineStatLv(lv, rb))) * (1 + MINE_RB_HP_BONUS * rb) + mineOverHp(lv)); } // [2026-11-08] 100레벨 이상(진환생)도 99레벨 체력
    // [2026-11-11] "80레벨 체력은 너무 낮고 99레벨은 높다 -- 80~90, 90~99 순차적으로" -> 같은 날 "99레벨은 16만, 그 아래는 그에 맞게 더 올리고, 80레벨부터 1만 정도씩"
    //  -- 80레벨 50,000 -> 99레벨 160,000 직선(레벨마다 약 +5,789 -- 99레벨 16만에서 1만씩이면 80레벨이 음수라 가능한 만큼 크게).
    //  79 -> 80에서 갑자기 뛰지 않게 71~79레벨은 70레벨 값(15,293)에서 80레벨 값까지 같은 배율(레벨마다 약 x1.126)로 잇는다. 70레벨까지는 그대로(레벨마다 9%).
    //  예전 9% 곡선: 80 36,204 / 90 85,707 / 99 186,147.
    static final double MINE_HP_LV80 = 50_000, MINE_HP_LV99 = 160_000;
    static double mineHpBase(int lv) {
        if (lv <= 70) return 40 * Math.pow(1.09, lv - 1);
        if (lv < 80) { double h70 = 40 * Math.pow(1.09, 69); return h70 * Math.pow(MINE_HP_LV80 / h70, (lv - 70) / 10.0); }
        return MINE_HP_LV80 + (MINE_HP_LV99 - MINE_HP_LV80) * (lv - 80) / 19.0;
    }
    /** 환생 광물 획득 배수(채굴/몬스터 광석) = 1 + 환생 횟수. */
    static int mineRebirthGain(Map<String, Integer> items) { return 1 + mineRebirth(items); }
    /** [2026-10-28] "환생 보너스 추가 광석은 미스릴 이하 광석만" -- 구리~미스릴(등급 1~5) 광석일 때만 x(1 + 회차), 별빛석/요일석/보석 등은 1배. */
    static int mineRebirthGain(Map<String, Integer> items, int grade, String code) {
        return grade >= 1 && grade <= 5 && S5MineMap.ORE_CODES[grade - 1].equals(code) ? mineRebirthGain(items) : 1;
    }
    // [2026-11-07] "고레벨에서 갑자기 너무 세진다 -- 99레벨은 플레이어 기준 20% 너프, 그 아래 레벨도 계단식" -- 20레벨마다 5%씩(1~19 그대로, 20~39 -5%, 40~59 -10%, 60~79 -15%, 80~99 -20%).
    static double mineAtkStep(int lv) { return 1.0 - 0.05 * Math.min(4, Math.max(0, lv) / 20); }
    // [2026-11-07] "플레이어 공격력도 용병과 비슷한 곡선(노환생 기준)" -- 80레벨까지는 그대로(레벨마다 9% + 계단식 너프), 80 -> 99는 직선으로 완만하게(80레벨 값 -> 99레벨 10,000).
    static final double MINE_ATK_LV99 = 10_000;
    static double mineAtkExp(int lv) { return 6 * Math.pow(1.09, lv - 1) * mineAtkStep(lv); }
    static int mineAtk(int lv) { if (lv <= 80) return (int) Math.round(mineAtkExp(lv)); double a80 = mineAtkExp(80); return (int) Math.round(a80 + (MINE_ATK_LV99 - a80) * (Math.min(MINE_LV_MAX, lv) - 80) / 19.0 + mineOverAtk(lv)); } // [2026-11-17] 99 넘으면 레벨마다 같은 양(오를수록 더)
    /** [2026-10-28] "환생 시 공격 데미지 보너스 +50%(x환생 수)" -- 공격력 x (1 + 0.5 rb). */
    static int mineAtk(int lv, int rb) { return (int) Math.round(mineAtk(mineStatLv(lv, rb)) * (1 + MINE_RB_ATK_BONUS * rb)); } // [2026-11-17] 환생 바닥 레벨
    /** [2026-10-28] 공격력 배수 -- "환생 효과나 다른 공격력 증가와는 합연산": 1 + 환생 0.5 x 회차 + 광전사의 장갑 0.5(곱하지 않고 더한다). 양산은 고정 +10(mineAtkFlat). */
    static double mineAtkMul(Map<String, Integer> items) {
        return 1 + MINE_RB_ATK_BONUS * mineRebirth(items) + (MINE_ITEM_BERSERK_GLOVES.equals(mineGloveCode(items)) ? (items.getOrDefault(MINE_ITEM_BERSERK_GLOVES, 0) >= 2 ? 0.65 : MINE_BERSERK_MULT - 1) + (items.getOrDefault(MINE_LOWHP_FLAG, 0) > 0 ? 0.2 : 0) : 0) // [2026-11-04] 2단계
                + mineWeaponPct(items) / 100.0; // [2026-10-29] 드랍 무기 공격력 %(닌자의 검 영웅부터, 백화검) -- 합연산
    }
    static final double MINE_RB_ATK_BONUS = 0.5;
    /** [2026-10-28] "1환생당 경험치 필요량 5배에서 2배로" -- 필요 경험치 x MINE_RB_EXP_MUL^rb. */
    static final double MINE_RB_EXP_MUL = 2;
    // [2026-11-08] 진환생 -- "80레벨부터 필요 경험치 5배(비공개)", "필요량은 컬럼을 늘리기보다 들어오는 경험치 감소로, 플레이어한테는 그대로 들어오는 것처럼".
    //  화면/DB의 필요 경험치(mineExpNeed)는 99레벨 값에서 더 커지지 않고, 대신 들어오는 경험치를 mineTrueRbDiv(80레벨부터 5배, 99레벨 뒤로 레벨마다 1.12배)로 나눠 쌓는다(mineExpAdd).
    //  처치 문구의 "경험치 +N"은 나누기 전 값 그대로. 결과는 예전 식(필요량 x 5 x 1.12^레벨)과 같다. 비공개 -- 화면/신부 설명에 쓰지 않는다.
    static final int MINE_TRUE_RB_EXP80 = 5;
    static long mineExpNeed(int lv, int rb) { return Math.round(15 * Math.pow(1.12, (rb > MINE_REBIRTH_BASE ? Math.min(MINE_LV_MAX, lv) : lv) - 1) * Math.pow(MINE_RB_EXP_MUL, rb)); }
    static double mineTrueRbDiv(int lv, int rb) { return rb <= MINE_REBIRTH_BASE ? 1 : (lv >= 80 ? MINE_TRUE_RB_EXP80 : 1) * Math.pow(1.12, Math.max(0, lv - MINE_LV_MAX)); }
    /** 경험치 더하기 + 레벨 업 -- {레벨, 레벨 안 경험치, 오른 레벨 수, 화면에 알릴 오른 경험치}. 5환생까지는 99레벨에서 가득 찬 채로 멈춘다(넘친 건 안 오름). */
    // [2026-11-18] "플레이어 레벨 80부터 너무 안 오른다 -- 지금 대비 경험치 3배쯤" -- 80레벨부터 얻는 경험치 x MINE_EXP80_MUL(처치/검기/필살기/용병 처치 모두 여기로 들어온다).
    //  용병 경험치(mineMercShare)는 곱하기 전 값으로 그대로.
    static final int MINE_EXP80_LV = 80, MINE_EXP80_MUL = 3;
    static long[] mineExpAdd(int lv, long exp, long gain, int rb) {
        if (lv >= MINE_EXP80_LV && gain > 0) gain *= MINE_EXP80_MUL;
        int lv0 = lv, up = 0;
        long exp0 = exp;
        if (rb <= MINE_REBIRTH_BASE) {
            exp += gain;
            int cap = mineLvCap(rb); // [2026-11-18] 0~4환생은 99에서 경험치가 가득 찬 채로 멈춘다(환생할 수 있는 상태), 5환생은 [2026-11-17]처럼 99를 넘어 레벨마다 1.12배
            while (lv < cap && exp >= mineExpNeed(lv, rb)) { exp -= mineExpNeed(lv, rb); lv++; up++; }
            if (lv >= cap) exp = Math.min(exp, cap == MINE_LV_MAX ? mineExpNeed(lv, rb) : mineExpNeed(lv, rb) - 1);
            return new long[] { lv, exp, up, mineExpReal(lv0, exp0, lv, exp, rb) };
        }
        double raw = gain; // 진환생 -- 나누기 전 경험치로 다음 레벨까지 남은 만큼씩 쓴다
        while (lv < MINE_LV_TRUE_MAX) {
            long need = mineExpNeed(lv, rb);
            double d = mineTrueRbDiv(lv, rb), toNext = Math.max(0, need - exp) * d;
            if (raw < toNext) {
                double add = raw / d;
                long a = (long) Math.floor(add);
                if (RND.nextDouble() < add - a) a++; // 작은 값도 평균으로는 들어가게
                exp = Math.min(need - 1, exp + a);
                break;
            }
            raw -= toNext; exp = 0; lv++; up++;
        }
        return new long[] { lv, exp, up, gain };
    }
    /** 환생할 수 있나 -- 99레벨 + 경험치 가득(다음 레벨 필요치 이상) + 아직 5번 안 함. */
    static boolean mineRebirthReady(int lv, long exp, int rb) { return lv >= MINE_LV_MAX && rb < MINE_REBIRTH_MAX && (lv > MINE_LV_MAX || exp >= mineExpNeed(MINE_LV_MAX, rb)); } // [2026-11-08] 진환생은 99를 넘어 오르므로 100레벨 이상이면(= 99레벨을 가득 채움)

    // [2026-10-28] 다운스케일 1회 변환 -- 레벨 안 경험치를 (새 필요치 / 예전 필요치) 비율로(바꾸지 않으면 다음 처치 때 레벨이 몇십씩 오른다).
    //  배포 뒤 첫 광산 요청(mineGate)에서 한 번만 DB로 바꾸고 설정값 MINE_SCALE_VER = 2를 남긴다(서버를 다시 켜도 다시 안 함).
    static final String MINE_SCALE_KEY = "MINE_SCALE_VER";
    static volatile boolean mineScaleDone = false;

    void mineRescaleOnce() {
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

    int mineLv(HashMap<String, Object> row) {
        return Math.max(1, Math.min(MINE_LV_TRUE_MAX, intVal(row.get("M_LV"), 1))); // [2026-11-08] 진환생은 100레벨 이상(능력치 함수들이 99에서 멈춘다)
    }

    // [2026-10-25] "몬스터와 전투 시 최대 체력 10%씩 잠겨 최대 50%까지, 레벨업하거나 대도시 성당 신부에게 치료받으면 정상으로" --
    //  HP_LOCK(0~5), 최대 체력 = 원래 x (10 - 잠금) / 10. 처음엔 새 전투마다 +1이었으나 [2026-10-26] "죽으면 처리되어야 하는데 전투 때마다 깎인다" --
    //  몬스터에게 쓰러질 때만 +1.
    static final String MINE_ITEM_HP_LOCK = "HP_LOCK";
    static final int MINE_HP_LOCK_MAX = 5;

    /** [2026-11-17] 갑옷 체력까지 더한 최대 체력(잠금 전) -- 체력바 100%, 거룡의 최대 체력 % 공격("늘어난 체력만큼 거룡 상향"). */
    static int mineHpFull(int lv, int rb) { String hu = MINE_CTX.get(); return mineHpMax(lv, rb) + (hu == null ? 0 : MINE_ARMOR_HP_OF.getOrDefault(hu, 0)); }
    static int mineHpCapN(int lv, int lock, int rb) { String hu = MINE_CTX.get(); return Math.max(1, (mineHpMax(lv, rb) + (hu == null ? 0 : MINE_ARMOR_HP_OF.getOrDefault(hu, 0))) * (10 - Math.max(0, Math.min(MINE_HP_LOCK_MAX, lock))) / 10); }
    /** [2026-11-21] 각성(필살기) 중엔 2배 -- "현재 체력 2배(최대 체력을 잠시 넘는다)": 넘친 체력이 곳곳의 최대 체력 자르기에 잘리지 않게. 회복은 mineHpCapN(원래 최대)까지만. */
    static int mineHpCap(int lv, int lock, int rb) { int c = mineHpCapN(lv, lock, rb); return mineKakOn(MINE_CTX.get()) ? c + c / 2 : c; } // [2026-11-22] 각성 최대 체력 2배 -> 1.5배
    /** [2026-11-21] 회복 -- 원래 최대 체력까지만(각성으로 넘친 체력은 그대로 두고 더 채우지 않는다). */
    static int mineHealTo(int hp, long add, int lv, int lock, int rb) { int n = mineHpCapN(lv, lock, rb); return hp >= n ? hp : (int) Math.min(n, hp + add); }

    /** 쓰러진 뒤 붙이는 안내(잠금이 늘었을 때만). */
    static String mineLockText(HashMap<String, Object> out) {
        Object v = out.get("hpLockUp");
        return v == null ? "" : " 🔒 최대 체력이 잠겼어요(지금 -" + v + "%). 레벨업하거나 왕도 성당 신부에게 치료받으면 풀려요.";
    }

    /** 쓰러짐 -- 최대 체력 10% 더 잠금(최대 50%). 바뀐 잠금 단계를 돌려준다. */
    int mineLockUp(String userName, int lock, HashMap<String, Object> out) {
        if (lock >= MINE_HP_LOCK_MAX) return lock;
        if (mineWbZone(userName, System.currentTimeMillis())) return lock; // [2026-11-15] 월드보스 싸움터(동쪽 성곽 내성)는 잠금 없음
        lock++;
        dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, lock);
        out.put("hpLockUp", lock * 10);
        return lock;
    }

    /** [2026-10-26] "전투 중엔 체력 회복이 안 돼야 하는데 되는 것 같다" -- 몬스터를 쓰러뜨려 인식이 끝나면 곧바로 회복이 다시 시작돼서
     *  다음 몬스터로 가는 동안(과 한 방에 잡는 사냥 내내) 체력이 찼다. 체력을 저장하는 일(때림/맞음/쓰러뜨림/인식 해제)이 마지막으로 있은 뒤
     *  이만큼 지나야 회복이 시작된다. */
    static final long MINE_REGEN_DELAY_MS = 10_000L;

    /** [2026-10-28] "체력 회복은 전투 중이어도 서서히, 그늘이나 모닥불 근처에서는 조금씩 더" -- 전투 중(인식됨)엔 평소의 1/4 속도로(대기 없이),
     *  안전지대(그늘/모닥불/마을 등 map.safeAt)에 서 있으면 x1.5. 화면(hpNow)도 같은 식. */
    static final double MINE_REGEN_FIGHT_MUL = 0.25, MINE_REGEN_SAFE_MUL = 1.5;

    /** 지금 체력(저장된 체력 + 저장 시각 + MINE_REGEN_DELAY_MS부터 회복). M_HP가 없으면 가득. lock = 최대 체력 잠금 단계, rb = 환생 횟수. */
    // ---- [2026-11-03] "전투 시 매초 체력을 DB에 저장할 필요 없다" -- 전투 턴/환경 턴(한기·더위·용암 등)에서 체력만 바뀌면 메모리(MINE_HP_VIRT)에 두고
    //  MINE_HP_FLUSH_MS마다(또는 쓰러질 때) DB에 쓴다. 메모리 값은 "어느 DB 저장 시각(M_HP_AT_MS) 다음 값인지"를 들고 있어서, 그 사이 다른 요청(처치/레벨업/회복 등)이
    //  DB에 쓰면 저장 시각이 달라져 저절로 버려진다(따로 지울 곳이 없다). 창을 닫으면 마지막 몇 초의 체력 변화만 사라진다.
    static final class HpVirt { final int hp; final long at, dbAt; HpVirt(int hp, long at, long dbAt) { this.hp = hp; this.at = at; this.dbAt = dbAt; } }
    static final java.util.concurrent.ConcurrentHashMap<String, HpVirt> MINE_HP_VIRT = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_HP_FLUSH_MS = 15_000L;

    /** 이 DB 행 다음의 메모리 체력(없거나 DB가 그 뒤에 바뀌었으면 null). */
    HpVirt mineHpVirt(HashMap<String, Object> row) {
        String un = strVal(row.get("USER_NAME"), null);
        HpVirt v = un == null ? null : MINE_HP_VIRT.get(un);
        return v != null && row.get("M_HP_AT_MS") != null && v.dbAt == longOf(row.get("M_HP_AT_MS"), -1L) ? v : null;
    }

    /** 체력 저장 시각(메모리 값이 있으면 그 시각) -- 회복 계산의 기준. */
    long mineHpAt(HashMap<String, Object> row, long def) {
        HpVirt v = mineHpVirt(row);
        return v != null ? v.at : longOf(row.get("M_HP_AT_MS"), def);
    }

    /** 체력만 바뀐 결과를 메모리에 둔다(dbAt = 이 요청이 읽은 DB 행의 저장 시각). DB에 쓸 때가 됐는지는 부른 쪽이 mineHpFlushDue로 본다. */
    static void mineHpKeep(String userName, HashMap<String, Object> row, int hp, long now) {
        MINE_HP_VIRT.put(userName, new HpVirt(hp, now, longOf(row.get("M_HP_AT_MS"), 0L)));
    }

    static boolean mineHpFlushDue(HashMap<String, Object> row, long now) {
        return now - longOf(row.get("M_HP_AT_MS"), 0L) >= MINE_HP_FLUSH_MS;
    }

    int mineHpNow(HashMap<String, Object> row, long now, int lock, int rb) {
        int max = mineHpCap(mineLv(row), lock, rb), nmax = mineHpCapN(mineLv(row), lock, rb); // [2026-11-21] 각성 -- max = 2배(넘친 체력 유지), 회복은 nmax까지
        HpVirt hv = mineHpVirt(row);
        if (hv == null && row.get("M_HP") == null) return nmax;
        int hp = Math.min(max, hv != null ? hv.hp : intVal(row.get("M_HP"), max)); // [2026-11-11] 고레벨 최대 체력이 줄어도(80레벨부터 새 곡선) 넘치지 않게
        long hpAt = hv != null ? hv.at : longOf(row.get("M_HP_AT_MS"), now);
        String un = strVal(row.get("USER_NAME"), null);
        boolean fight = mineEngaged(un, now);
        double mul = 1;
        S5MineMap m = mineMapOf(row);
        if (m != null && row.get("POS_X") != null && m.safeAt(intVal(row.get("POS_X"), -1), intVal(row.get("POS_Y"), -1))) mul *= MINE_REGEN_SAFE_MUL;
        Map<String, Integer> gi = un == null ? null : mineItems(un);
        if (gi != null && MINE_ITEM_BERSERK_GLOVES.equals(mineGloveCode(gi))) mul *= MINE_BERSERK_REGEN_MUL; // [2026-10-29] 광전사의 장갑 -- 회복 없음 -> 20% 느리게
        boolean twin = gi != null && MINE_ITEM_TWIN_GLOVES.equals(mineGloveCode(gi)); // [2026-10-30] 쌍격의 장갑 -- 저장 시각부터 초당 -1%(1 아래로는 안 떨어진다)
        // [2026-11-03] "쌍격의 장갑 끼고 오래 쉬다 싸우면 체력이 1이 됨" -- 예전엔 지금 싸우는 중이면 저장 뒤 전체 시간을 전투 회복(x0.25)으로 쳐서
        //  초당 -1%가 회복을 넘어 체력이 바닥났다. 이제 마지막 MINE_ENGAGE_TTL_MS(10초)만 전투로 보고, 그 앞은 평소처럼(10초 대기 뒤 회복) 계산한다.
        long el = Math.max(0, now - hpAt), fightSpan = fight ? Math.min(el, MINE_ENGAGE_TTL_MS) : 0, calm = el - fightSpan;
        double floor = Math.min(hp, 1), v = hp;
        double arg = gi == null ? 0 : mineArmorRegen(gi); // [2026-11-06] 전설 이상 갑옷 -- 초당 최대 체력 %(싸울 때도, 대기 없이)
        if (arg > 0) { double p0 = v; v = Math.min(Math.max(nmax, p0), v + el * (double) nmax * arg / 100 / 1000); }
        if (calm > 0) {
            double p0 = v;
            v += Math.max(0, calm - MINE_REGEN_DELAY_MS) * (double) nmax * mul / MINE_HP_FULL_MS;
            if (twin) v -= calm * (double) nmax * mineTwinDrain(gi) / 100 / 1000;
            v = Math.min(Math.max(nmax, p0), v); if (twin) v = Math.max(floor, v);
        }
        if (fightSpan > 0) {
            double p0 = v;
            v += fightSpan * (double) nmax * mul * MINE_REGEN_FIGHT_MUL / MINE_HP_FULL_MS;
            if (twin) v -= fightSpan * (double) nmax * mineTwinDrain(gi) / 100 / 1000;
            v = Math.min(Math.max(nmax, p0), v); if (twin) v = Math.max(floor, v);
        }
        return (int) v;
    }

    static HashMap<String, Object> mineCombatInfo(int lv, long exp, int hp, long now, boolean fight, int lock, int rb) {
        HashMap<String, Object> c = mineCombatInfo(lv, exp, hp, now, rb);
        c.put("regenFrom", now + MINE_REGEN_DELAY_MS); // [2026-10-26] 화면도 이때부터 체력을 올린다(지금 저장했으니 지금 + 대기)
        c.put("fight", fight); // [2026-10-25] 인식 중(화면은 1초마다 전투 턴을 부른다), [2026-10-28] 체력은 1/4 속도로 찬다
        c.put("regenFight", MINE_REGEN_FIGHT_MUL);
        c.put("regenSafe", MINE_REGEN_SAFE_MUL);
        c.put("hpMax", mineHpCap(lv, lock, rb)); // [2026-10-25] 잠긴 만큼 뺀 최대 체력
        c.put("hpBase", mineHpFull(lv, rb)); // [2026-11-17] 갑옷 체력까지(넘치던 체력바)
        c.put("hpLock", Math.min(MINE_HP_LOCK_MAX, lock) * 10);
        long ku = mineKakUntil(MINE_CTX.get()); // [2026-11-21] 각성 -- 끝나는 시각과 원래 최대 체력(화면: 넘친 몫을 금색 줄로)
        if (ku > now) c.put("hpMaxN", mineHpCapN(lv, lock, rb));
        return c;
    }

    static HashMap<String, Object> mineCombatInfo(int lv, long exp, int hp, long now, int rb) {
        HashMap<String, Object> c = new HashMap<>();
        c.put("lv", lv);
        c.put("exp", exp);
        c.put("expNeed", mineExpNeed(lv, rb));
        c.put("hp", hp);
        c.put("hpMax", mineHpFull(lv, rb)); // [2026-11-17] 갑옷 체력까지
        c.put("atk", mineAtk(lv, rb));
        c.put("rebirth", rb); // [2026-10-28] 환생 횟수(화면 "Lv.1(환생1)" + 날개)
        c.put("rebirthReady", mineRebirthReady(lv, exp, rb));
        c.put("at", now);
        c.put("fullMs", MINE_HP_FULL_MS);
        long kku = mineKakUntil(MINE_CTX.get()); if (kku > now) c.put("kakUntil", kku); // [2026-11-21] 각성 -- 어느 전투 정보에나(화면 불꽃/체력바)
        return c;
    }

    // [2026-11-15] 차지하는 칸(S5MineMobs.Type.foot: 보스 2x2, 거룡 3x3)과 "여러 칸을 때리는 기술은 걸친 칸마다" 도우미.
    /** (x, y)에서 몬스터가 차지한 칸들까지의 거리(맨해튼, 붙어 있으면 1, 겹치면 0). */
    static int mineFootDist(S5MineMobs.Type t, MobState s, int x, int y) {
        int fo = t.foot() - 1;
        return Math.max(0, Math.max(s.fx - x, x - s.fx - fo)) + Math.max(0, Math.max(s.fy - y, y - s.fy - fo));
    }
    /** 왼쪽 위 칸이 cell인 몬스터가 차지한 칸 가운데 cells에 든 칸 수(적어도 1). */
    static int mineFootHits(S5MineMap map, S5MineMobs.Type t, int cell, java.util.Set<Integer> cells) {
        int f = t.foot(), n = 0;
        if (f <= 1 || cell < 0) return 1;
        for (int oy = 0; oy < f; oy++) for (int ox = 0; ox < f; ox++) if (cells.contains(cell + oy * map.w + ox)) n++;
        return Math.max(1, n);
    }
    /** 왼쪽 위가 (fx, fy)인 몬스터가 차지한 칸 가운데 (cx, cy) 둘레 r칸(체비셰프) 안에 든 칸 수(0 = 안 걸침). */
    static int mineFootIn(S5MineMobs.Type t, int fx, int fy, int cx, int cy, int r) {
        int f = t.foot(), n = 0;
        for (int oy = 0; oy < f; oy++) for (int ox = 0; ox < f; ox++) if (Math.max(Math.abs(fx + ox - cx), Math.abs(fy + oy - cy)) <= r) n++;
        return n;
    }
    /** 바로 때린 몬스터에게 들어가는 검기 타격 -- 보스는 검기 줄에 걸친 칸마다 따로 굴린다({속성, 피해, 상성, 칸 번호, 칸 안 순번}), 보스가 아니면 예전 그대로. */
    List<int[]> mineAuraMainHits(String userName, S5MineMap map, S5MineMobs.Type type, int hitCell, int x, int y, String dir, int range, int[] aura, double atkBase, double vul) {
        int n = mineAuraFoot(userName, map, type, hitCell, x, y, dir, range);
        if (n <= 1) return mineVulnApply(mineAuraHits(aura, atkBase, type, 1), vul);
        List<int[]> out = new ArrayList<>();
        for (int c = 0; c < n; c++) { int q = 0; for (int[] h : mineVulnApply(mineAuraHits(aura, atkBase, type, 1), vul)) out.add(new int[] { h[0], h[1], h[2], c, q++ }); }
        return out;
    }
    /** 내가 바로 때린 보스에게 들어가는 검기 -- 검기 줄에 걸친 보스 칸 수(보스가 아니면 1). */
    int mineAuraFoot(String userName, S5MineMap map, S5MineMobs.Type t, int cell, int x, int y, String dir, int range) {
        return t.foot() <= 1 ? 1 : mineFootHits(map, t, cell, mineAuraLine(userName, map, x, y, mineDirVec(dir), range).keySet());
    }

    /** 몬스터가 지금 있을 수 있는 칸들(멈춰 있으면 그 칸, 아니면 앞/지금/다음 시간 칸 -- 화면과 서버 시계 차이 허용). */
    static int[] mineMobCells(S5MineMap.Mob mob, MobState s, long now, S5MineMap map) {
        if (mob.type == S5MineMobs.WB_BOSS_TYPE && MINE_WB.px >= 0) return new int[] { MINE_WB.py * map.w + MINE_WB.px }; // [2026-11-16] 거룡은 옮겨 다닌다
        if (mob.type == S5MineMobs.KRAKEN_TYPE && MINE_KRK_POS[0] >= 0) return new int[] { MINE_KRK_POS[1] * map.w + MINE_KRK_POS[0] }; // [2026-11-18] 크라켄은 길을 따라 돈다
        if (s != null && s.frozenUntil > now) return new int[] { s.fy * map.w + s.fx };
        return new int[] { S5MineMobs.cellAt(mob, now - S5MineMobs.SLOT_MS), S5MineMobs.cellAt(mob, now), S5MineMobs.cellAt(mob, now + S5MineMobs.SLOT_MS) };
    }

    // [2026-11-06] "왕도 서쪽(마물의 숲, 1,400마리)에서 몹이 뭉치면 렉" -- (1) 같은 맵의 몬스터 상태는 MINE_MOBS_SNAP_MS 동안 한 번만 만들어 함께 쓰고
    //  (예전엔 사람마다 1초마다 서버 전체 몬스터 상태표를 처음부터 훑었다), (2) 몬스터가 MINE_MOBS_BIG마리 넘는 맵은 내 둘레 MINE_MOBS_NEAR칸 안 줄만 보낸다
    //  (보스/수하 줄은 늘). 화면은 near 밖의 예전 상태를 그대로 둔다.
    static final long MINE_MOBS_SNAP_MS = 250L;
    static final int MINE_MOBS_BIG = 300, MINE_MOBS_NEAR = 40;
    static final java.util.concurrent.ConcurrentHashMap<String, Object[]> MINE_MOBS_SNAP = new java.util.concurrent.ConcurrentHashMap<>();
    @Override
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> mineMobs(String mapId, int x, int y) {
        HashMap<String, Object> full = mineMobsShared(mapId);
        HashMap<String, Object> out = new HashMap<>(full);
        out.put("now", System.currentTimeMillis());
        S5MineMap map = mapId == null ? null : S5MineMap.get(mapId);
        Object lo = full.get("mobs");
        if (map == null || map.mobs().size() <= MINE_MOBS_BIG || !(lo instanceof List) || x < 0 || y < 0) return out;
        List<long[]> near = new ArrayList<>();
        for (long[] r : (List<long[]>) lo) {
            int id = (int) r[0];
            S5MineMobs.Type t = id >= 0 && id < map.mobs().size() ? S5MineMobs.TYPES.get(map.mobs().get(id).type) : null;
            S5MineMap.Mob mb = t == null ? null : map.mobs().get(id);
            if (t == null || t.boss || t.minion || Math.max(Math.abs(r[3] - x), Math.abs(r[4] - y)) <= MINE_MOBS_NEAR || Math.max(Math.abs(mb.x - x), Math.abs(mb.y - y)) <= MINE_MOBS_NEAR) near.add(r);
        }
        out.put("mobs", near);
        out.put("near", new int[] { x, y, MINE_MOBS_NEAR });
        return out;
    }
    HashMap<String, Object> mineMobsShared(String mapId) {
        long now = System.currentTimeMillis();
        S5MineMap map = mapId == null ? null : S5MineMap.get(mapId);
        if (map != null && map.solo()) return mineMobs(mapId); // [2026-11-15] 1인 층(탑)은 사람마다 달라 공유 스냅샷을 안 쓴다
        String key = map == null ? "" : map.version();
        Object[] sn = MINE_MOBS_SNAP.get(key);
        if (sn != null && now - (Long) sn[0] < MINE_MOBS_SNAP_MS) { @SuppressWarnings("unchecked") HashMap<String, Object> c = (HashMap<String, Object>) sn[1]; return c; }
        HashMap<String, Object> out = mineMobs(mapId);
        if (map != null) MINE_MOBS_SNAP.put(key, new Object[] { now, out });
        return out;
    }
    /** 화면이 1.5초마다 묻는 맵의 몬스터 상태(체력이 줄었거나 쓰러졌거나 멈춘 것만): [id, hp, deadUntil, fx, fy, frozenUntil]. 오래 안 맞은 몬스터는 다 회복(목록에서 뺀다). */
    @Override
    public HashMap<String, Object> mineMobs(String mapId) {
        HashMap<String, Object> out = new HashMap<>();
        long now = System.currentTimeMillis();
        out.put("now", now);
        if (mineGate() != null) { out.put("mobs", Collections.emptyList()); return out; }
        S5MineMap map = S5MineMap.get(mapId);
        String prefix = mineMobPrefix(map); // [2026-11-15] 1인 층(탑)은 내 것만
        if (S5MineMap.HARPY_NEST.equals(map.id)) mineHarpySync(map, now, out); // [2026-11-08] 하피 자리(나무 사이를 옮겨 다님) + 나무 상태
        List<long[]> list = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, MobState> e : MINE_MOB_STATE.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            MobState s = e.getValue();
            long id = Long.parseLong(e.getKey().substring(prefix.length()));
            boolean minion = id < map.mobs().size() && S5MineMobs.TYPES.get(map.mobs().get((int) id).type).minion;
            S5MineMobs.Type bt = id < map.mobs().size() ? S5MineMobs.TYPES.get(map.mobs().get((int) id).type) : null;
            if (bt != null && mineHidden(map, bt, now)) continue; // [2026-11-05] 숨은 마물은 화면이 hideUntil로 지운다(1,400마리 줄을 매초 보내지 않게)
            synchronized (s) {
                if (bt != null && bt.boss && s.deadUntil <= now) { // [2026-10-29] 보스 -- 인식이 풀리면 서서히 회복
                    int bmax = S5MineMobs.hp(bt, map.mobs().get((int) id).rank);
                    mineBossRegen(s, bmax, now, map, map.mobs().get((int) id));
                    if (s.hp < bmax || s.target != null) { list.add(mineMobRow((int) id, s)); seen.add(id); continue; }
                }
                if (bt != null && bt.sim && s.deadUntil <= now && (s.hp <= 0 || now - s.lastHitAt >= MINE_SIM_HEAL_MS)) { MINE_MOB_STATE.remove(e.getKey(), s); continue; } // [2026-11-07] 보스 허수아비 -- 다 참
                if (minion && s.deadUntil <= now && s.frozenUntil <= now) s.deadUntil = Long.MAX_VALUE / 2; // [2026-10-28] 아무도 안 싸우면 수하는 물러난다
                if (!minion && s.deadUntil <= now && s.frozenUntil <= now && now - s.lastHitAt > MINE_MOB_HEAL_MS && !(map.solo() && s.hp > 0)) { MINE_MOB_STATE.remove(e.getKey(), s); continue; } // [2026-11-16] 탑(1인 층)의 살아 있는 몬스터는 그대로 둔다 -- 방을 지키는 몬스터가 사라져 문이 열리지 않게
                list.add(mineMobRow((int) id, s)); // [2026-10-28] 7번째 = 은신 중(1)
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
        if (S5MineMap.WESTROAD.equals(map.id) && mineThunderOn(now)) out.put("hideUntil", mineThunderUntil); // [2026-11-05] 천둥 황소 -- 이때까지 마물(보스 제외)은 없음
        if (S5MineMap.WESTROAD.equals(map.id)) { mineThunderLoad(); out.put("thunderKills", mineThunderKills); out.put("thunderNeed", MINE_THUNDER_NEED); } // [2026-11-06] 절반부터 화면에 전기(점점 세게)
        return out;
    }

    // ---- [2026-10-24] 동굴 3층 보스 -- "선공하는 보스, 잡으면 미스릴 또는 별빛석, 맵 초기화 때만 나오고 잡히면 안 나오게" ----
    //  맵 리셋(2일 주기)마다 한 번만 나타난다. 잡히면 TBOT_S5_CONFIG.MINE_BOSS_KILLED = "주기|유저"(서버를 다시 켜도 그 주기엔 안 나옴).
    //  선공: 보스 2칸 안에 들어오면 보스가 먼저 인식한다([2026-10-25] 전투 턴 mineCombatTick -- 아래 몬스터 인식 참고).
    static final String MINE_BOSS_KEY = "MINE_BOSS_KILLED";
    // [2026-10-28] "처치된 보스는 2시간 뒤 재생성" -- 보스 공통(동굴 3층/대주교/얼음 여왕/용암 거신)으로 쓰러진 시각 + MINE_BOSS_RESPAWN_MS까지만 없다.
    //  쓰러진 시각은 설정값(키마다 "T시각|유저")에 남겨 서버를 다시 켜도 이어진다(예전 "주기|유저" 값은 이미 지난 것으로 본다).
    static final long MINE_BOSS_RESPAWN_MS = 2 * 3_600_000L;
    static final long MINE_CHAMP_RESPAWN_MS = 30 * 60_000L; // [2026-11-16] 동굴 3층 챔피언(고대 광석 골렘) -- 쓰러진 뒤 30분
    static final long MINE_BOSS_FX_MS = 8_000L; // [2026-11-16] 보스 전리품 연출을 알리는 시간
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_BOSS_KILLED_AT = new java.util.concurrent.ConcurrentHashMap<>();

    long mineBossKilledAt(String key) {
        Long v = MINE_BOSS_KILLED_AT.get(key);
        if (v == null) {
            long at = 0;
            try {
                String c = dao.selectConfigValue(key);
                if (c != null && c.startsWith("T")) at = Long.parseLong(c.substring(1).split("\\|")[0].trim());
            } catch (Exception e) {
                at = 0;
            }
            MINE_BOSS_KILLED_AT.put(key, at);
            v = at;
        }
        return v;
    }

    void mineBossKilledNow(String key, String userName, long now) {
        MINE_BOSS_KILLED_AT.put(key, now);
        dao.upsertConfigValue(key, "T" + now + "|" + userName, "web mine boss killed (T epoch ms|user) -- respawns 2h later");
    }

    boolean mineBossDeadKey(String key) {
        return System.currentTimeMillis() < mineBossKilledAt(key) + (MINE_BOSS_KEY.equals(key) ? MINE_CHAMP_RESPAWN_MS : MINE_BOSS_RESPAWN_MS); // [2026-11-16] 동굴 3층 챔피언은 30분
    }

    /** 보스 종류 -> 쓰러진 시각 설정 키(보스가 아니거나 따로 셈하는 보스면 null). */
    static String mineBossKey(int type) {
        return type == S5MineMobs.BOSS_TYPE ? MINE_BOSS_KEY : type == S5MineMobs.RAID_BOSS_TYPE ? MINE_RAID_KEY : type == S5MineMobs.ICE_BOSS_TYPE ? MINE_ICE_KEY
                : type == S5MineMobs.LAVA_BOSS_TYPE ? MINE_LAVA_KEY : type == S5MineMobs.PHARAOH_BOSS_TYPE ? MINE_PHARAOH_KEY : type == S5MineMobs.HARPY_BOSS_TYPE ? MINE_HARPY_KEY : null;
    }

    boolean mineBossDead() {
        return mineBossDead(S5MineMobs.BOSS_TYPE); // [2026-11-13] 소환
    }

    // [2026-10-28] "젠은 맵 초기화 시점 1회만(보스 공통)" -- 타락한 대주교도 동굴 3층 보스처럼 주기마다 한 번(MINE_RAID_KILLED = "주기|유저").
    static final String MINE_RAID_KEY = "MINE_RAID_KILLED";

    // [2026-10-28] 용암섬 용암 거신 -- 보스 공통으로 주기마다 한 번(MINE_LAVA_KILLED = "주기|유저").
    static final String MINE_LAVA_KEY = "MINE_LAVA_KILLED";
    // [2026-11-04] 피라미드 지하 파라오 미라 -- 보스 공통(2시간 뒤 다시). 붕대 감기 = 원거리 공격에 맞으면 MINE_STUN_MS 동안 못 움직이고 못 때린다(화면 + 공격/캐기 서버도 막음).
    static final String MINE_PHARAOH_KEY = "MINE_PHARAOH_KILLED";
    static final boolean MINE_FOREST_OPEN = true; // [2026-11-11] "일반 유저에게 오픈" -- 마물의 숲 미궁/끝방/하피의 둥지/나루터(false면 관리자만)
    static final String MINE_HARPY_KEY = "MINE_HARPY_KILLED"; // [2026-11-08] 하피 -- 쓰러지고 2시간 뒤 다시
    static final long MINE_STUN_MS = 2_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_STUN = new java.util.concurrent.ConcurrentHashMap<>();
    static boolean mineStunned(String userName, long now) { Long u = userName == null ? null : MINE_STUN.get(userName); return u != null && u > now; }
    /** 원거리 공격(불덩이) -- 인식 중인 유저마다 다음에 던질 시각. 6~10초마다, 2칸 이상 떨어져 있을 때(붙어 있으면 그냥 때린다). */
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_RANGED_NEXT = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_RANGED_MIN_MS = 6_000L, MINE_RANGED_RAND_MS = 4_000L;
    static final double MINE_RANGED_MULT = 1.3;
    // [2026-10-28] 여왕개미 독산 -- 맞으면 MINE_SLOW_MS 동안 느려지고(화면이 걸음을 2배로 늦춘다) MINE_POISON_MS 동안 초당 최대 체력 MINE_POISON_PCT% 독(전투 턴마다).
    static final long MINE_SLOW_MS = 4_000L, MINE_POISON_MS = 6_000L;
    static final double MINE_POISON_PCT = 2;
    static final class Debuff { volatile long slowUntil, poisonUntil, poisonAt; }
    static final java.util.concurrent.ConcurrentHashMap<String, Debuff> MINE_DEBUFF = new java.util.concurrent.ConcurrentHashMap<>();
    // [2026-10-28] "용암 거신은 플레이어가 밟고 있던 땅을 용암화시키는 원거리 공격, 용암화된 땅은 전투가 완전 종료되면 정상화" --
    //  불덩이가 떨어진 칸(맞은 플레이어가 서 있던 칸)이 용암이 된다(최대 MINE_LAVA_MAX칸). 용암 위에 서 있으면 환경 턴(1초)마다 최대 체력의 MINE_LAVA_PCT%.
    //  용암 거신과 싸우는(인식된) 사람이 아무도 없거나 거신이 쓰러지면 모두 식는다. 용암섬은 한 곳뿐이라 서버 메모리 하나로 둔다.
    static final java.util.concurrent.ConcurrentHashMap<Integer, Long> MINE_LAVA = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile String mineLavaVer = "";
    static final int MINE_LAVA_MAX = 180; // [2026-10-29] 3x3씩 깔리니 60 -> 180
    static final double MINE_LAVA_PCT = 6;
    /** [2026-11-05] "화산섬 보스 물속에도 마그마 장판(다만 짧게)" -- 용왕의 반지로 물 위에 서서 피하던 것. 물 칸(~)의 용암은 이만큼만 남는다. */
    static final long MINE_LAVA_WATER_MS = 4_000L;

    /** 지금 용암 칸(y * w + x) -- 용암섬이 아니면 빈 목록. 전투가 완전히 끝났으면 여기서 식힌다. */
    List<Integer> mineLavaCells(S5MineMap map, long now) {
        if (!S5MineMap.ISLE7.equals(map.id)) return Collections.emptyList();
        if (!map.version().equals(mineLavaVer)) { MINE_LAVA.clear(); mineLavaVer = map.version(); }
        if (!MINE_LAVA.isEmpty() && !mineLavaFightOn(map, now)) MINE_LAVA.clear();
        MINE_LAVA.entrySet().removeIf(en -> now - en.getValue() > MINE_LAVA_WATER_MS && mineLavaWater(map, en.getKey())); // [2026-11-05] 물 위 용암은 금방 식는다
        return new ArrayList<>(MINE_LAVA.keySet());
    }

    static boolean mineLavaWater(S5MineMap map, int cell) {
        int cx = cell % map.w, cy = cell / map.w;
        return map.inside(cx, cy) && map.terrainAt(cx, cy) == '~';
    }

    /** 용암 거신이 살아 있고 누군가와 싸우는 중인지(인식된 사람이 있음). */
    boolean mineLavaFightOn(S5MineMap map, long now) {
        S5MineMap.Mob boss = mineBossOf(map);
        if (boss == null || boss.type != S5MineMobs.LAVA_BOSS_TYPE || mineBossDead(boss.type)) return false;
        MobState bs = MINE_MOB_STATE.get(mineMobKey(map, boss.id));
        if (bs != null && bs.deadUntil > now) return false;
        for (MineEngage e : MINE_ENGAGE.values()) if (map.version().equals(e.mapVer) && now - e.at <= MINE_ENGAGE_TTL_MS && e.mobs.containsKey(boss.id)) return true;
        return false;
    }

    // ---- [2026-11-05] 마물의 숲 입구 천둥 황소 -- ("200마리로 완화" -- 처음엔 500) "이번 밤 동안 500마리를 잡으면 모든 몬스터가 사라지고 맵 가운데 번개의 힘을 받은 소 보스, 번개의 심장".
    //  밤마다 마물 처치 수를 모두 함께 센다(서버 메모리). MINE_THUNDER_NEED에 닿으면 마물은 모두 숨고(mineHidden) 황소가 MINE_THUNDER_BOSS_MS 동안 나타난다.
    //  쓰러뜨리거나 시간이 지나면 마물이 돌아온다. 한 밤에 한 번. 스킬(mineThunderSkills): 전광석화(멀면 딱 붙고 가까우면 밀쳐냄), 멀면 전기창(맞은 자리 3x3 폭발),
    //  붙어 있으면 밀쳐내고 근접 장판, 그리고 전체 맵 낙뢰(감전 스턴 -- 환경 턴).
    // [2026-11-05] "밤에만 세고, 낮이 돼도 사라지지 않게. 누적 500이 되면 0으로 돌리며 보스가 나타나고 1시간 뒤 사라진다. 사라지면 일반 몹이 다시.
    //  1시간 카운트는 맵 안 모두에게" -- 밤마다 0으로 돌리던 것/한 밤에 한 번 제한을 없앴다. 누적 수는 TBOT_S5_CONFIG(MINE_THUNDER_CFG)에 10마리마다 적어
    //  배포/재시작에도 이어진다(최대 9마리 손실). 남은 시간은 mineMobs의 hideUntil로 화면에 보인다.
    static final int MINE_THUNDER_NEED = 1250; // [2026-11-19] 2000 -> 1250(사용자)
    // // [2026-11-14] 500 -> 2000("천둥 보스가 복사된다" -- 밤/쉼 조건을 없애자 1분에 90마리씩 잡혀 5~6분마다 다시 나옴) // [2026-11-05] 밤 처치 누적
    static final long MINE_THUNDER_BOSS_MS = 60 * 60_000L; // [2026-11-05] 20분 -> 1시간
    static final String MINE_THUNDER_CFG = "MINE_THUNDER_KILLS";
    static final double MINE_THUNDER_AIR_PCT = 1.0; // [2026-11-06] 황소가 나와 있는 동안 맵 전체 초당 최대 체력 %
    static volatile boolean mineThunderLoaded = false;
    static final Object MINE_THUNDER_LOCK = new Object();
    static volatile long mineThunderNight = Long.MIN_VALUE, mineThunderDoneNight = Long.MIN_VALUE, mineThunderUntil = 0;
    static volatile boolean mineThunderKilled = false;
    // [2026-11-08] "천둥 보스가 처치되면 최소 2시간 있다가부터 몬스터 카운팅" -- 쓰러진 시각 + MINE_THUNDER_REST_MS까지는 밤 처치를 세지 않는다(설정에 적어 재시작에도 이어진다).
    static final long MINE_THUNDER_REST_MS = 2 * 3_600_000L;
    static final String MINE_THUNDER_REST_CFG = "MINE_THUNDER_REST_AT";
    static volatile long mineThunderRestAt = 0;
    static int mineThunderKills = 0;
    static boolean mineThunderOn(long now) { return now < mineThunderUntil && !mineThunderKilled; }
    /** 황소가 나와 있는 동안 마물의 숲 입구 마물(보스 제외)은 없는 것으로 본다. */
    static boolean mineHidden(S5MineMap map, S5MineMobs.Type t, long now) { return S5MineMap.WESTROAD.equals(map.id) && !t.boss && mineThunderOn(now); }

    void mineThunderKill(S5MineMap map, S5MineMobs.Type t, long now) {
        if (!S5MineMap.WESTROAD.equals(map.id) || t.boss) return; // [2026-11-13] "정해진 숫자만큼 처치하면 그냥 보스가 출현" -- 밤에만 세던 것 없앰
        mineThunderLoad();
        int k;
        synchronized (MINE_THUNDER_LOCK) {
            if (mineThunderOn(now)) return; // [2026-11-13] 쓰러진 뒤 2시간 쉼(mineThunderRestAt)도 없앰 -- 나와 있는 동안만 안 센다
            k = ++mineThunderKills;
            if (k >= MINE_THUNDER_NEED) {
                mineThunderKills = k = 0;
                mineThunderKilled = false;
                mineThunderUntil = now + MINE_THUNDER_BOSS_MS;
            }
        }
        mineThunderAfter(map, now, k);
    }

    void mineThunderLoad() {
        if (!mineThunderLoaded) {
            synchronized (MINE_THUNDER_LOCK) {
                if (!mineThunderLoaded) {
                    try { String v = dao.selectConfigValue(MINE_THUNDER_CFG); if (v != null && !v.trim().isEmpty()) mineThunderKills = Math.max(0, Integer.parseInt(v.trim())); } catch (Exception ignore) { }
                    try { String v = dao.selectConfigValue(MINE_THUNDER_REST_CFG); if (v != null && !v.trim().isEmpty()) mineThunderRestAt = Long.parseLong(v.trim()); } catch (Exception ignore) { } // [2026-11-08]
                    mineThunderLoaded = true;
                }
            }
        }
    }

    void mineThunderAfter(S5MineMap map, long now, int k) {
        if (k % 10 == 0) { try { dao.upsertConfigValue(MINE_THUNDER_CFG, String.valueOf(k), "west forest night kills toward thunder bull (set by app)"); } catch (Exception ignore) { } }
        if (k > 0) return;
        S5MineMap.Mob boss = mineBossOf(map);
        if (boss != null) {
            S5MineMobs.Type bt = S5MineMobs.TYPES.get(boss.type);
            int bmax = S5MineMobs.hp(bt, boss.rank);
            MobState bs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, boss.id), kk -> new MobState(bmax));
            synchronized (bs) { bs.respawn(bmax); bs.target = null; bs.frozenUntil = 0; bs.fx = boss.x; bs.fy = boss.y; bs.regenAt = now; bs.nearAt = now; bs.lastHitAt = 0; }
        }
        MINE_SHOCK.clear();
        MINE_THUNDER_NEXT.clear();
        mineThunderStormAt = 0;
        mineThunderStormNext = now + 15_000L;
        int bid = boss == null ? -1 : boss.id;
        for (MineEngage e : MINE_ENGAGE.values()) if (map.version().equals(e.mapVer)) e.mobs.keySet().removeIf(id -> id != bid); // 싸우던 마물도 사라진다
    }

    /** 황소를 쓰러뜨렸다 -- 마물이 곧(쓰러져 있던 것도 3초 안에) 돌아온다. */
    void mineThunderEnd(S5MineMap map, long now) {
        mineThunderKilled = true;
        mineThunderLoad();
        if (mineThunderRestAt > 0) { mineThunderRestAt = 0; try { dao.upsertConfigValue(MINE_THUNDER_REST_CFG, "0", "thunder bull rest (unused since 2026-11-13)"); } catch (Exception ignore) { } } // [2026-11-13] 쉼 없이 바로 다시 센다
        MINE_SHOCK.clear();
        MINE_THUNDER_NEXT.clear();
        String prefix = map.version() + ":";
        for (java.util.Map.Entry<String, MobState> en : MINE_MOB_STATE.entrySet()) {
            if (!en.getKey().startsWith(prefix)) continue;
            MobState ms = en.getValue();
            synchronized (ms) { if (ms.deadUntil > now + 3_000L && ms.deadUntil < Long.MAX_VALUE / 4) ms.deadUntil = now + 3_000L; }
        }
    }

    /** 천둥 황소 스킬 타이머(유저마다 {전광석화, 전기창, 밀쳐내기+장판}), 전기 장판 칸(y * w + x -> 사라지는 시각), 전체 낙뢰 시각. */
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_THUNDER_NEXT = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<Integer, Long> MINE_SHOCK = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_THUNDER_STORM_SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile long mineThunderStormAt = 0, mineThunderStormNext = 0;
    static final long MINE_SPEAR_ZONE_MS = 1_500L, MINE_FIELD_ZONE_MS = 3_000L, MINE_STORM_STUN_MS = 1_500L;
    static final double MINE_DASH_MULT = 0.8, MINE_SPEAR_MULT = 1.3, MINE_SLAM_MULT = 1.0, MINE_ZONE_MULT = 0.5, MINE_STORM_MULT = 0.6;

    static List<Integer> mineShockCells(long now) {
        MINE_SHOCK.entrySet().removeIf(en -> en.getValue() <= now);
        return new ArrayList<>(MINE_SHOCK.keySet());
    }

    /** 보스(2x2, 왼위 bx,by)에서 멀어지는 쪽으로 플레이어를 최대 n칸 민다(막히면 그 앞까지). */
    static int[] mineThunderKnock(S5MineMap map, int bx, int by, int x, int y, int n, int walk) {
        double cx = bx + 0.5, cy = by + 0.5;
        int dx = (int) Math.signum(x - cx), dy = (int) Math.signum(y - cy);
        if (dx == 0 && dy == 0) dx = RND.nextBoolean() ? 1 : -1;
        int px = x, py = y;
        for (int i = 0; i < n; i++) {
            int[][] tries = { { dx, dy }, { dx, 0 }, { 0, dy } };
            boolean moved = false;
            for (int[] t : tries) {
                if (t[0] == 0 && t[1] == 0) continue;
                int nx = px + t[0], ny = py + t[1];
                if (mineWalk(map, nx, ny, walk) && !(nx >= bx && nx <= bx + 1 && ny >= by && ny <= by + 1)) { px = nx; py = ny; moved = true; break; }
            }
            if (!moved) break;
        }
        return new int[] { px, py };
    }

    /** 전광석화로 딱 붙을 자리(보스 2x2의 왼위 칸) -- 플레이어 바로 옆. */
    static int[] mineThunderBeside(S5MineMap map, int x, int y, int bx, int by) {
        int[][] c = { { x + 1, y }, { x - 2, y }, { x, y + 1 }, { x, y - 2 }, { x + 1, y - 1 }, { x - 2, y - 1 }, { x - 1, y + 1 }, { x - 1, y - 2 } };
        int[] best = null;
        int bd = Integer.MAX_VALUE;
        for (int[] q : c) {
            if (!map.walkable(q[0], q[1]) || !map.walkable(q[0] + 1, q[1] + 1)) continue;
            int d = Math.abs(q[0] - bx) + Math.abs(q[1] - by);
            if (d < bd) { bd = d; best = q; }
        }
        return best;
    }

    int mineThunderSkills(String userName, S5MineMap map, MobState s, S5MineMap.Mob mb, int x, int y, int dist, Map<String, Integer> items, long now,
            List<HashMap<String, Object>> hits, HashMap<String, Object> out) {
        S5MineMobs.Type type = S5MineMobs.TYPES.get(mb.type);
        long[] nx = MINE_THUNDER_NEXT.computeIfAbsent(userName, u -> new long[] { now + 3_000L, now + 4_500L, now + 2_000L });
        int atk = S5MineMobs.atk(type, mb.rank), dmg = 0;
        HashMap<String, Object> h = new HashMap<>();
        h.put("mobId", mb.id);
        h.put("fx", s.fx);
        h.put("fy", s.fy);
        if (now >= nx[0]) { // 전광석화
            nx[0] = now + 9_000L + RND.nextInt(4_000);
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_DASH_MULT * (0.8 + RND.nextDouble() * 0.4))), type.elem()); // [2026-11-22] 특수기 = 몬스터 속성
            h.put("thunder", "dash");
            if (dist >= 3) { // 멀면 번개처럼 달려와 딱 붙는다
                int[] c = mineThunderBeside(map, x, y, s.fx, s.fy);
                if (c != null) { s.fx = c[0]; s.fy = c[1]; s.lastMoveAt = now; }
                h.put("pull", true);
            } else { // 가까우면 들이받아 밀쳐낸다
                int[] k = mineThunderKnock(map, s.fx, s.fy, x, y, 4, mineWalkMode(items));
                out.put("knockX", k[0]);
                out.put("knockY", k[1]);
                h.put("push", true);
            }
            h.put("tx", s.fx);
            h.put("ty", s.fy);
            h.put("dmg", d);
            hits.add(h);
            dmg += d;
        } else if (dist >= 2 && now >= nx[1]) { // 전기창 -- 맞은 자리 3x3이 잠깐 폭발 장판
            nx[1] = now + 6_000L + RND.nextInt(3_000);
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_SPEAR_MULT * (0.8 + RND.nextDouble() * 0.4))), type.elem());
            for (int ly = y - 1; ly <= y + 1; ly++) for (int lx = x - 1; lx <= x + 1; lx++) if (map.inside(lx, ly)) MINE_SHOCK.merge(ly * map.w + lx, now + MINE_SPEAR_ZONE_MS, Math::max);
            h.put("thunder", "spear");
            h.put("dmg", d);
            hits.add(h);
            dmg += d;
        } else if (dist <= 1 && now >= nx[2]) { // 붙어 있으면 밀쳐내고 근접 장판(보스 둘레 반지름 3)
            nx[2] = now + 8_000L + RND.nextInt(3_000);
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_SLAM_MULT * (0.8 + RND.nextDouble() * 0.4))), type.elem());
            int[] k = mineThunderKnock(map, s.fx, s.fy, x, y, 3, mineWalkMode(items));
            out.put("knockX", k[0]);
            out.put("knockY", k[1]);
            for (int ly = s.fy - 3; ly <= s.fy + 4; ly++) for (int lx = s.fx - 3; lx <= s.fx + 4; lx++)
                if (map.inside(lx, ly) && Math.abs(lx - s.fx - 0.5) + Math.abs(ly - s.fy - 0.5) <= 4.5) MINE_SHOCK.merge(ly * map.w + lx, now + MINE_FIELD_ZONE_MS, Math::max);
            h.put("thunder", "slam");
            h.put("dmg", d);
            hits.add(h);
            dmg += d;
        }
        if (out.containsKey("knockX")) dao.updateMinePos(userName, map.id, (Integer) out.get("knockX"), (Integer) out.get("knockY"), now);
        if (!MINE_SHOCK.isEmpty()) out.put("shock", mineShockCells(now));
        return dmg;
    }

    /** 보스(type)가 쓰러진 지 2시간이 안 됐는지. 보스가 아니면 false. */
    boolean mineBossDead(int type) {
        if (type == S5MineMobs.WB_BOSS_TYPE) { long nw = System.currentTimeMillis(); mineWbSync(nw); return !mineWbOn(nw) || !mineWbOk(MINE_CTX.get()); } // [2026-11-15] 일반 공개 전엔 관리자에게만 있다 // [2026-11-15] 월드보스 -- 매일 21시 30분 동안
        if (type == S5MineMobs.THUNDER_BOSS_TYPE) return !mineThunderOn(System.currentTimeMillis()); // [2026-11-05] 밤 마물 200마리(MINE_THUNDER_NEED) 뒤에만
        if (mineCallMap(type) != null) return !mineBossUp(type, System.currentTimeMillis()); // [2026-11-13] 보스룸 보스는 소환했을 때만
        String key = mineBossKey(type);
        return key != null && mineBossDeadKey(key);
    }

    // ---- [2026-11-15] 탑의 도시 + 탑 -- "하피 처치 후 나루터에서만 가는 신규 맵, 왕도보다 큰 마을, 그 지역의 탑은 시즌5 탑 등반과 같은 규칙 --
    //  시즌5.5와 시즌5 탑이 잘 섞이게"(방식 A: 광산 캐릭터로 오르는 시즌5식 탑).
    //  - 하피 여왕을 쓰러뜨린(함께 싸운 보상 포함) 사람만 나루터 뱃사공이 강 건너 탑의 도시로 태워 준다(MINE_ITEM_HARPY_CLEAR, mineHarpyDrops가 남긴다).
    //  - 탑 1~8층(구역마다)은 시즌5처럼 끝없이 도는 칸 고리: 걸어서 못 움직이고 🎲 주사위(1~6)로만 칸을 옮긴다. 칸: 전투(칸 안쪽 몬스터가 깨어나 덤빈다 --
    //    쓰러뜨려야 다시 굴린다) / 보물 / PP(시즌5 PP -- 그 사람의 시즌5 층 처치 PP 기준) / 함정(최대 체력 15%) / 행운(체력 가득 + 가끔 세공 보석) / 계단(위: 다음 층 열림, 아래).
    //  - 9층마다 수문장(보스) -- 쓰러뜨리면(함께 싸운 사람도) 그 구역은 끝(아래 층으로 못 돌아간다), 10층마다 마을(쉼터, 탑 관리인/사제/대장장이).
    //  - 기록은 광산 아이템 TW_MAX(올라갈 수 있는 가장 높은 층), TW_BLOCK(쓰러뜨린 마지막 수문장 층 -- 그 아래로는 못 감).
    //  - 탑 몬스터를 쓰러뜨리면 경험치/광석/보석에 더해 탑의 증표(1개, 수문장은 함께 싸운 사람 모두 20개) -- [2026-11-15] 같은 날 "시즌5 PP는 안 줘도 된다,
    //    시즌5.5의 탑의 증표로 대체 -- 최종 장비를 사는 재화로"(탑의 도시 증표 상인 mineTokenShop).
    static final String MINE_ITEM_HARPY_CLEAR = "HARPY_CLEAR", MINE_ITEM_TW_MAX = "TW_MAX", MINE_ITEM_TW_BLOCK = "TW_BLOCK";
    static final long MINE_TW_ROLL_MS = 1_200L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_TW_ROLL_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final int MINE_TW_SEA_PCT = 40, MINE_TW_SEA_BOSS = 2; // [2026-11-16] 탑 보물방 바다 보석 확률(%), 수문장 처치 바다 보석 개수
    static final int MINE_TW_TOKEN_KILL = 1, MINE_TW_TOKEN_BOSS = 20, MINE_TW_TOKEN_CHEST = 2, MINE_TW_TOKEN_TILE = 3;
    // [2026-11-15] 같은 날 "보스는 s5 패턴·이름을, 하루 3회 잡을 수 있는 룰은 동일하게" -- 수문장 = 시즌5 구역 보스.
    //  하루 3번(시즌5 BOSS_DAILY_KILL_LIMIT처럼 모든 수문장 공통, TWB_DAY = 날짜 yyyyMMdd / TWB_CNT = 그날 처치 수): 다 쓰면 수문장 층에 못 들어가고, 함께 싸워도 보상·진행이 없다.
    //  패턴: 강타(시즌5 보스 반격 x1.6 -- 네 번째 공격마다, 29층부터 세 번째마다), 29층부터 기절(시즌5 구역 3+ 보스: 공격마다 20%, MINE_TW_STUN_GAP_MS에 한 번까지).
    static final int MINE_TW_BOSS_DAILY = 3, MINE_TW_STUN_PCT = 20;
    static final double MINE_TW_HEAVY_MULT = 1.6;
    static final long MINE_TW_STUN_MS = 1_500L, MINE_TW_STUN_GAP_MS = 6_000L;
    static final String MINE_ITEM_TWB_DAY = "TWB_DAY", MINE_ITEM_TWB_CNT = "TWB_CNT";
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_TW_STUN_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static int mineToday(long now) { java.time.LocalDate d = java.time.Instant.ofEpochMilli(now).atZone(KST).toLocalDate(); return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth(); }
    /** 탑 수문장의 한 방(전투 턴의 공격 / 내 공격에 대한 반격)에 시즌5 패턴을 입힌다 -- {피해, 강타 1/0, 기절 1/0}. 기절은 부르는 쪽이 건다(피하면 안 걸림 -- mineTwStun). */
    int[] mineTwStrike(String userName, int floor, MobState s, int d, long now) {
        boolean heavy = ++s.strikeN % (floor >= 59 ? 2 : floor >= 29 ? 3 : 4) == 0; // [2026-11-23] 59층부터 두 번째마다 강타, 기절 30%
        boolean stun = floor >= 29 && RND.nextInt(100) < (floor >= 59 ? 30 : MINE_TW_STUN_PCT) && now - MINE_TW_STUN_AT.getOrDefault(userName, 0L) >= MINE_TW_STUN_GAP_MS;
        return new int[] { heavy ? (int) Math.round(d * MINE_TW_HEAVY_MULT) : d, heavy ? 1 : 0, stun ? 1 : 0 };
    }
    void mineTwStun(String userName, long now, HashMap<String, Object> out) {
        MINE_TW_STUN_AT.put(userName, now);
        MINE_STUN.put(userName, now + MINE_TW_STUN_MS);
        out.put("stunUntil", now + MINE_TW_STUN_MS);
    }
    /** 오늘 쓰러뜨린 수문장 수(날짜가 바뀌면 0). */
    static int mineTwBossToday(Map<String, Integer> items, long now) { return items.getOrDefault(MINE_ITEM_TWB_DAY, 0) == mineToday(now) ? items.getOrDefault(MINE_ITEM_TWB_CNT, 0) : 0; }
    /** [2026-11-19] "탑 안에선 수문장 반복 처치 보상 3번 중 몇 번 남았는지 항상" -- 사람마다 {날짜, 그날 횟수}를 메모리에(상태를 읽을 때 / 수문장 보상 때) 두고 위치 알림(1초)에 남은 수를 싣는다(DB 조회 없이). */
    static final java.util.concurrent.ConcurrentHashMap<String, int[]> MINE_TW_LEFT = new java.util.concurrent.ConcurrentHashMap<>();
    static int mineTwLeftOf(String userName, long now) { int[] v = MINE_TW_LEFT.get(userName); return v == null ? -1 : v[0] == mineToday(now) ? Math.max(0, MINE_TW_BOSS_DAILY - v[1]) : MINE_TW_BOSS_DAILY; }
    /** 탑의 증표 n개 -- 안내 글자. */
    /** [2026-11-17] "증표 보상 구간을 고층엔 더 많이" -- 1~10층 x1, 11~20층 x2, 21~30층 x3, 31~40층 x4, 41~50층 x5. */
    static int mineTwTokenMul(int f) { return 1 + Math.max(0, f - 1) / 10; }
    // [2026-11-23] "7성은 50층 이상 보스 드랍, 하루 3회 제한에 경험치도 못 얻게(그것도 보상), 대신 경험치 획득량 3배" --
    //  탑 수문장을 쓰러뜨린 사람이 이번에 보상을 받았는지(첫 처치 / 하루 MINE_TW_BOSS_DAILY번 안 / 관리자)를 남겨 두고, 바로 뒤 경험치(mineTwExp)와 장비 드랍이 그걸 본다.
    //  받으면 경험치 x MINE_TW_BOSS_EXP_MUL, 못 받으면 경험치 0 + 장비 드랍 없음. 50층 위 수문장(59~99층)은 보상을 받을 때 ★7 동료 장비 MINE_TW_MG7_PCT(층) 확률.
    static final int MINE_TW_BOSS_EXP_MUL = 3;
    static final java.util.concurrent.ConcurrentHashMap<String, Boolean> MINE_TW_BOSS_RW = new java.util.concurrent.ConcurrentHashMap<>();
    static int mineTwMg7Pct(int f) { return f < 50 ? 0 : 10 + (f - 59) / 10 * 5; } // 59층 10% · 69층 15% · 79층 20% · 89층 25% · 99층 30%
    /** 탑 수문장 처치 경험치 -- 보상을 받았으면 x3, 하루 횟수를 다 써서 못 받았으면 0. 탑 수문장이 아니면 그대로. */
    static long mineTwExp(String userName, S5MineMap map, S5MineMobs.Type t, long e) {
        if (!t.boss || S5MineMap.towerFloor(map.id) <= 0) return e;
        Boolean rw = MINE_TW_BOSS_RW.get(userName);
        return rw == null ? e : rw ? e * MINE_TW_BOSS_EXP_MUL : 0;
    }
    /** 탑 수문장을 쓰러뜨렸는데 하루 횟수를 다 써서 보상(장비 드랍 포함)이 없다. */
    static boolean mineTwNoRw(String userName, S5MineMap map, S5MineMobs.Type t) { return t.boss && S5MineMap.towerFloor(map.id) > 0 && Boolean.FALSE.equals(MINE_TW_BOSS_RW.get(userName)); }
    String mineTowerToken(String userName, int n) {
        dao.addUserOre(userName, S5MineMap.TOWER_TOKEN_CODE, n);
        return "🗼 " + S5MineMap.TOWER_TOKEN_NAME + " +" + n;
    }
    static int mineTwMax(Map<String, Integer> items) { return Math.max(1, items.getOrDefault(MINE_ITEM_TW_MAX, 1)); }
    static int mineTwBlock(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_TW_BLOCK, 0); }
    /** 보드에서 지금 서 있는 칸 번호(없으면 -1). */
    static int mineTwIndex(S5MineMap map, int x, int y) {
        List<int[]> bd = map.board();
        for (int i = 0; i < bd.size(); i++) if (bd.get(i)[0] == x && bd.get(i)[1] == y) return i;
        return -1;
    }
    /** 보드 칸 i(전투 칸)의 몬스터 -- 전투 칸 순서대로 놓였다(S5MineMobs.spawn). */
    static S5MineMap.Mob mineTwMob(S5MineMap map, int i) {
        int k = 0;
        for (int j = 0; j < i; j++) if (map.board().get(j)[2] == 'C') k++;
        return k < map.mobs().size() ? map.mobs().get(k) : null;
    }
    /** 탑 몬스터를 쓰러뜨렸다(처치 경로 셋에서) -- 탑의 증표, 수문장이면 함께 싸운 사람 모두 그 구역 끝(하루 MINE_TW_BOSS_DAILY번까지). 안내 글자(없으면 ""). */
    String mineTowerKill(String userName, S5MineMap map, S5MineMap.Mob mob, S5MineMobs.Type t) {
        if (t.chaos && S5MineMap.CHAOS.equals(map.id)) { MINE_CHAOS_RUN.remove(userName); return " 🌌 마계의 기운이 걷혔어요! 남쪽 귀환의 문이 열렸어요."; } // [2026-11-17]
        int f = S5MineMap.towerFloor(map.id);
        if (f <= 0 || !(t.tower || t.boss)) return "";
        StringBuilder m = new StringBuilder();
        try {
            if (!t.boss) {
                TwRun run = MINE_TW_RUN.get(userName); // [2026-11-16] 방을 지키던 몬스터 -- 이제 오른쪽 문이 열린다
                if (run != null && run.mobId == mob.id) run.mobId = -1;
                return " · " + mineTowerToken(userName, MINE_TW_TOKEN_KILL * mineTwTokenMul(f)) + " 🚪 오른쪽 문이 열렸어요.";
            }
            long now = System.currentTimeMillis();
            int day = mineToday(now);
            java.util.Set<String> us = new java.util.LinkedHashSet<>();
            us.add(userName);
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, mob.id));
            if (s != null) synchronized (s) { if (s.hitters != null) us.addAll(s.hitters); s.strikeN = 0; }
            for (String u : us) {
                Map<String, Integer> it = mineItems(u);
                // [2026-11-17] "수문장 반복 처치는 되게, 처음 올라가는 층은 가능, 이미 처치한 보스 보상만 하루 3회" --
                //  처음 쓰러뜨린 수문장(TW_BLOCK < 층)은 언제나 보상 + 다음 층, 하루 횟수에도 안 센다. 이미 쓰러뜨린 수문장은 몇 번이고 잡을 수 있고 보상만 하루 MINE_TW_BOSS_DAILY번.
                int cnt = mineTwBossToday(it, now);
                boolean first = mineTwBlock(it) < f;
                String txt;
                boolean rwOk = first || cnt < MINE_TW_BOSS_DAILY || isMineAdmin(u);
                MINE_TW_BOSS_RW.put(u, rwOk); // [2026-11-23] 바로 뒤 경험치/장비 드랍이 본다
                if (!rwOk) txt = " ⏳ 이미 쓰러뜨린 수문장 보상(경험치·장비 포함)은 하루 " + MINE_TW_BOSS_DAILY + "번까지예요(오늘 " + MINE_TW_BOSS_DAILY + "/" + MINE_TW_BOSS_DAILY + "). 내일 다시 받을 수 있어요.";
                else {
                    if (!first) { dao.upsertMineItem(u, MINE_ITEM_TWB_DAY, day); dao.upsertMineItem(u, MINE_ITEM_TWB_CNT, cnt + 1); MINE_TW_LEFT.put(u, new int[] { day, cnt + 1 }); }
                    if (first) dao.upsertMineItem(u, MINE_ITEM_TW_BLOCK, f);
                    if (mineTwMax(it) < f + 2) dao.upsertMineItem(u, MINE_ITEM_TW_MAX, Math.min(S5MineMap.TOWER_FLOORS, f + 2));
                    StringBuilder sea = new StringBuilder(); // [2026-11-16] 수문장 처치 -- 바다 보석 MINE_TW_SEA_BOSS개
                    for (int q = 0; q < MINE_TW_SEA_BOSS; q++) { int sg = RND.nextInt(S5MineMap.SEA_GEM_CODES.length); dao.addUserOre(u, S5MineMap.SEA_GEM_CODES[sg], 1); sea.append(" · 🌊 ").append(S5MineMap.SEA_GEM_NAMES[sg]); }
                    if (RND.nextInt(100) < mineTwMg7Pct(f)) { // [2026-11-23] 50층 위 수문장 -- ★7 동료 장비
                        String mp = MG_PARTS[RND.nextInt(MG_PARTS.length)], mc = mgCode(mp, mgAcc(mp) ? 9 : RND.nextInt(3), 7);
                        dao.upsertMineItem(u, mc, it.getOrDefault(mc, 0) + 1);
                        sea.append(" · 🎁 ").append(mgName(mp, 9, 7)).append("을(를) 얻었어요! (").append(mgEffText(mp, 7)).append(")");
                        mineGossipAdd(mineNick(u) + " 님이 탑 " + f + "층 수문장에게서 7성 용병 장비를 얻었대요!");
                    }
                    txt = " · " + mineTowerToken(u, MINE_TW_TOKEN_BOSS * mineTwTokenMul(f)) + sea + (first ? " (첫 처치!)" : " (오늘 반복 보상 " + Math.min(MINE_TW_BOSS_DAILY, cnt + 1) + "/" + MINE_TW_BOSS_DAILY + ")")
                            + (first ? " 🗼 " + (f + 1) + "층 마을이 열렸어요! 위쪽 계단으로 올라가요." : "");
                }
                if (u.equals(userName)) m.append(txt);
                else MINE_SHRINE_REWARD_MSG.merge(u, "🗼 탑 " + f + "층 수문장 " + t.name + "이(가) 쓰러졌어요!" + txt, (p, q) -> p + " / " + q);
            }
        } catch (Exception e) { /* 기록 실패해도 처치는 그대로 */ }
        return m.toString();
    }
    // ---- [2026-11-15] 동료(용병) 장비 -- "탑의 증표로 동료 장비 뽑기, 동료 장비는 시즌5와 동일하게(장비 뽑기 / 악세 뽑기, 초급·중급·고급·최상급),
    //  동료는 무적이라 방어력·방어막·부활은 무의미". 시즌5처럼 부위 여섯(무기/투구/갑옷 = 직업군 전용, 목걸이/반지/팔찌 = 공용), 등급 ★1~★6.
    //  시즌5의 체력/공격/방어 자리를 공격 쪽으로 옮겼다: 무기 = 공격력 %, 투구 = 치명타 확률(2배 피해), 갑옷 = 상성 피해 +%(속성 상성 x1.5에 더해),
    //  악세는 시즌5처럼 두 부위의 절반씩(목걸이 = 무기+갑옷, 반지 = 무기+투구, 팔찌 = 갑옷+투구).
    //  가진 장비 = 아이템 MG_{부위}{직업 0 전사/1 궁수/2 마법사, 악세 9}_{★}(개수), 끼운 장비 = MERC{직업}_G{부위}(★, 0 = 없음 -- 끼우면 가진 개수에서 하나 빠지고 빼면 돌아온다).
    //  뽑기는 탑의 도시·탑 마을의 증표 상인: 단계마다 증표 가격, 위 단계는 탑 수문장을 쓰러뜨려 연다(중급 9층 / 고급 19층 / 최상급 29층).
    static final String[] MG_PARTS = { "W", "H", "A", "N", "R", "B" }, MG_PART_NAMES = { "무기", "투구", "갑옷", "목걸이", "반지", "팔찌" };
    static final String[][] MG_ITEM_NAMES = { { "검", "투구", "갑주" }, { "활", "깃장식", "재킷" }, { "지팡이", "머리띠", "로브" } }; // 시즌5 직업군 이름(전사/궁수/마법사)
    static final int[] MG_ATK = { 0, 5, 10, 16, 24, 34, 48, 66 }, MG_CRIT = { 0, 2, 4, 6, 9, 12, 16, 22 }, MG_BEAT = { 0, 10, 20, 32, 48, 66, 90, 124 }; // [2026-11-17] ★7(혼돈스러운 마계 보스)
    static final String[] MG_TIER_NAMES = { "초급", "중급", "고급", "최상급" };
    // [2026-11-15] "탑 증표로 원하는 보석을 구매할 수 있게" -- 증표 상인 보석 탭: 땅 보석 여덟 가지 MINE_TK_GEM / 다이아몬드 MINE_TK_DIAMOND / 세공 보석 MINE_TK_CUT 증표(고르는 대로, 1개·10개).
    static final int MINE_TK_GEM = 3, MINE_TK_DIAMOND = 12, MINE_TK_CUT = 10;
    static final int[] MINE_TK_SEA = { 5, 5, 8, 8, 15 }, MINE_TK_SEA_CUT = { 15, 15, 24, 24, 45 }; // [2026-11-16] 세공된 바다 보석(세공 보석 탭) // [2026-11-16] 바다 보석 -- 진주 / 산호석 / 자수정 / 오팔 / 흑진주
    /** 증표로 사는 보석 {코드, 이름, 가격} -- 땅 보석, 다이아몬드, 세공 보석 순. */
    // [2026-11-17] "탑 증표 상점의 보석류(바다 보석, 세공류 포함), 심장도 한정된 개수만 구매 -- 이미 산 건 어쩔 수 없고" -- 물건마다 하루(KST) 한도.
    //  같은 날 "하루 한도 말고 전체 한도로, 수량도 넉넉히" -- 물건마다 계정 전체 한도(이번 변경부터 센다):
    //  땅 보석 100 / 다이아몬드 30 / 세공 보석 50 / 바다 보석 50 / 세공 바다 보석 30 / 보스 심장 10. 기록: TKT_<코드> = 지금까지 산 개수.
    static int mineTkLimit(String code) {
        if (code.endsWith("_HEART")) return 10;
        if (code.startsWith("CUT_")) { for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) if (S5MineMap.seaCutCode(i).equals(code)) return 30; return 50; }
        for (String sg : S5MineMap.SEA_GEM_CODES) if (sg.equals(code)) return 50;
        return S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND].equals(code) ? 30 : 100;
    }
    static int mineTkBought(Map<String, Integer> items, String code, long now) { return items.getOrDefault("TKT_" + code, 0); }
    static List<Object[]> mineTokenGems() {
        List<Object[]> l = new ArrayList<>();
        for (int i = 0; i < S5MineMap.GEM_DIAMOND; i++) l.add(new Object[] { S5MineMap.GEM_CODES[i], S5MineMap.GEM_NAMES[i], MINE_TK_GEM });
        l.add(new Object[] { S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], MINE_TK_DIAMOND });
        for (int i = 0; i < S5MineMap.GEM_DIAMOND; i++) l.add(new Object[] { S5MineMap.cutCode(i), S5MineMap.cutName(i), MINE_TK_CUT });
        for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) l.add(new Object[] { S5MineMap.SEA_GEM_CODES[i], S5MineMap.SEA_GEM_NAMES[i], MINE_TK_SEA[i] });
        for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) l.add(new Object[] { S5MineMap.seaCutCode(i), S5MineMap.seaCutName(i), MINE_TK_SEA_CUT[i] });
        // [2026-11-17] "탑 보상 교환에 보스 리워드인 심장도 -- 아직 획득 못 하는 건 빼고": 지금 보스에게서 실제로 나오는(라이브에서 얻은 사람이 있는) 심장 다섯(심장 탭)
        l.add(new Object[] { S5MineMap.THUNDER_HEART_CODE, S5MineMap.THUNDER_HEART_NAME, 30 });
        l.add(new Object[] { S5MineMap.DESERT_HEART_CODE, S5MineMap.DESERT_HEART_NAME, 40 });
        l.add(new Object[] { S5MineMap.ICE_HEART_CODE, S5MineMap.ICE_HEART_NAME, 60 });
        l.add(new Object[] { S5MineMap.VOLCANO_HEART_CODE, S5MineMap.VOLCANO_HEART_NAME, 60 });
        l.add(new Object[] { S5MineMap.WIND_HEART_CODE, S5MineMap.WIND_HEART_NAME, 80 }); // [2026-11-16] "탑의 증표로 바다 보석도 교환"
        return l;
    }
    static final int[] MG_TIER_PRICE = { 5, 20, 60, 150 }, MG_TIER_UNLOCK = { 0, 9, 19, 29 }; // 증표, 열리는 수문장 층(TW_BLOCK)
    static final int[][] MG_TIER_RATE = { { 0, 600, 300, 90, 10, 0, 0 }, { 0, 0, 650, 250, 80, 18, 2 }, { 0, 0, 0, 600, 300, 90, 10 }, { 0, 0, 0, 0, 600, 320, 80 } }; // ★0~★6 천분율(중급·최상급은 시즌5 동료 계약서와 같은 비율)
    static String mgCode(String part, int cls, int g) { return "MG_" + part + cls + "_" + g; }
    // [2026-11-23] "용병 장비 6성을 7성으로 올리는 걸로(햇빛석 사용처)" -- ★6 하나(가진 것이든 끼운 것이든)를 햇빛석 MINE_MG7_SUN개로 ★7로.
    //  같은 날 "성급 올리는 건 NPC를 통해서" -- 왕도 용병소 마당의 햇빛 대장장이(mgsmith)에게서만.
    static final int MINE_MG7_SUN = 5;
    static boolean mgAcc(String part) { return "N".equals(part) || "R".equals(part) || "B".equals(part); }
    static String mgName(String part, int cls, int g) {
        int pi = java.util.Arrays.asList(MG_PARTS).indexOf(part);
        // [2026-11-23] "용병의 검(6성) 말고 용병의 무기(6성)처럼 -- 직업·등급에 따라 이름이 바뀌지 않게": 부위 이름 하나로(직업 cls는 쓰지 않는다)
        return "용병의 " + MG_PART_NAMES[Math.max(0, pi)] + "(" + g + "성)";
    }
    /** 부위 하나의 효과 {공격력 %, 치명 %, 상성 %}. */
    static int[] mgEff(String part, int g) {
        g = Math.max(0, Math.min(7, g));
        switch (part) {
            case "W": return new int[] { MG_ATK[g], 0, 0 };
            case "H": return new int[] { 0, MG_CRIT[g], 0 };
            case "A": return new int[] { 0, 0, MG_BEAT[g] };
            case "N": return new int[] { MG_ATK[g] / 2, 0, MG_BEAT[g] / 2 };
            case "R": return new int[] { MG_ATK[g] / 2, MG_CRIT[g] / 2, 0 };
            default: return new int[] { 0, MG_CRIT[g] / 2, MG_BEAT[g] / 2 };
        }
    }
    static String mgEffText(String part, int g) {
        int[] e = mgEff(part, g);
        List<String> l = new ArrayList<>();
        if (e[0] > 0) l.add("공격력 +" + e[0] + "%");
        if (e[1] > 0) l.add("치명타 " + e[1] + "%");
        if (e[2] > 0) l.add("상성 피해 +" + e[2] + "%");
        return String.join(" · ", l);
    }
    /** 용병 c가 끼운 장비를 합친 효과 {공격력 %, 치명 %, 상성 %}. */
    static int[] mineMgBonus(Map<String, Integer> items, int c) {
        int[] s = new int[3];
        for (String p : MG_PARTS) { int[] e = mgEff(p, items.getOrDefault(mineMercKey(c, "G" + p), 0)); for (int i = 0; i < 3; i++) s[i] += e[i]; }
        return s;
    }
    static int mgRoll(int tier) {
        int r = RND.nextInt(1000), acc = 0;
        for (int g = 1; g <= 6; g++) { acc += MG_TIER_RATE[tier][g]; if (r < acc) return g; }
        return 6;
    }
    /** [2026-11-15] 증표 상인(동료 장비 뽑기) + 동료 장비 끼우기 -- action=list / inv / pull·pull10(code = equip|acc, grade = 단계) / equip(code = 아이템, grade = 직업) / unequip(code = 부위, grade = 직업). */
    public HashMap<String, Object> mineTokenShop(String userName, String action, String code, int grade) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        Map<String, Integer> items = mineItems(userName);
        if ("mail".equals(action) || "mailGet".equals(action)) { // [2026-11-17] 우편함
            if ("mailGet".equals(action)) {
                if (!mineAtNpc(row, "mailbox")) { out.put("message", "마을 우체부에게서만 받을 수 있어요."); return out; }
                Object[] ml = null;
                for (Object[] q : mineMails()) if (q[0].equals(code) && mineMailFor(userName, q)) ml = q; // [2026-11-20] DB 우편도
                if (ml == null) { out.put("message", "그런 우편은 없어요."); return out; }
                if (items.getOrDefault("MAIL_" + ml[0], 0) > 0) { out.put("message", "이미 받은 우편이에요."); return out; }
                dao.upsertMineItem(userName, "MAIL_" + ml[0], 1); items.put("MAIL_" + ml[0], 1);
                StringBuilder got = new StringBuilder();
                for (String[] it : (String[][]) ml[3]) { int q = Integer.parseInt(it[1]); dao.addUserOre(userName, it[0], q); got.append(got.length() > 0 ? " · " : "").append(mineCodeName(it[0])).append(' ').append(q); }
                out.put("ok", true);
                out.put("message", "📮 " + ml[1] + " -- " + got + " 받았어요!");
                out.put("ores", mineOreList(userName));
            }
            out.put("mails", mineMailList(userName, items));
            return out;
        }
        if ("mana".equals(action)) { // [2026-11-18] "정제된 마석은 미스릴 원석으로 1:1 교환 -- 최후의 방어선 NPC(정제사)"
            int mana = mineOreHave(userName).getOrDefault(S5MineMap.MANA_STONE_CODE, 0);
            if (!mineAtNpc(row, "refiner")) { out.put("message", "정제사에게서만 바꿀 수 있어요."); return out; }
            int n = grade <= 0 ? mana : Math.min(mana, grade);
            if (n < 1 || dao.takeUserOre(userName, S5MineMap.MANA_STONE_CODE, n) == 0) { out.put("message", "💠 " + S5MineMap.MANA_STONE_NAME + "이(가) 없어요. 마계 마을 땅을 캐면 나온대요."); return out; }
            dao.addUserOre(userName, S5MineMap.ORE_CODES[4], n);
            out.put("ok", true);
            out.put("got", n);
            out.put("message", "💠 " + S5MineMap.MANA_STONE_NAME + " " + n + "개를 " + S5MineMap.ORE_NAMES[4] + " " + n + "개로 바꿨어요!");
            out.put("ores", mineOreList(userName));
            action = "refineInfo";
        }
        if ("alchemyInfo".equals(action) || "alchemy".equals(action) || "alchemyBack".equals(action)) { // [2026-11-20] "큰 마을 연금술사 -- 구리 -> 철 10:1(상위로도)" -- 왕도/요새 도시, 구리->철, 철->은, 은->금(금 위로는 없음 -- 미스릴은 병목이라)
            if ("alchemy".equals(action)) {
                if (!mineAtNpc(row, "alchemist")) { out.put("message", "연금술사에게서만 바꿀 수 있어요."); return out; }
                int from = -1;
                try { from = Integer.parseInt(code); } catch (NumberFormatException ex) { }
                if (from < 0 || from > 3) { out.put("message", "그건 바꿀 수 없어요."); return out; }
                // [2026-11-20] "연금술사가 금광석 10개 -> 악마의 광석 조각 1개로" -- from 3 = 금광석 -> 악마의 광석 조각
                String toCode = from == 3 ? S5MineMap.DEMON_ORE_CODE : S5MineMap.ORE_CODES[from + 1], toName = from == 3 ? S5MineMap.DEMON_ORE_NAME : S5MineMap.ORE_NAMES[from + 1];
                int haveF = mineOreHave(userName).getOrDefault(S5MineMap.ORE_CODES[from], 0), maxN = haveF / MINE_ALCHEMY_RATE;
                int n = grade <= 0 ? maxN : Math.min(maxN, grade);
                if (n < 1) { out.put("message", "⚗️ " + S5MineMap.ORE_NAMES[from] + "이(가) " + MINE_ALCHEMY_RATE + "개 이상 있어야 해요. (보유 " + haveF + ")"); }
                else if (dao.takeUserOre(userName, S5MineMap.ORE_CODES[from], n * MINE_ALCHEMY_RATE) == 0) { out.put("message", "⚗️ " + S5MineMap.ORE_NAMES[from] + "이(가) 모자라요."); }
                else {
                    dao.addUserOre(userName, toCode, n);
                    out.put("ok", true);
                    out.put("got", n);
                    out.put("message", "⚗️ " + S5MineMap.ORE_NAMES[from] + " " + String.format("%,d", n * MINE_ALCHEMY_RATE) + "개를 녹여 " + toName + " " + String.format("%,d", n) + "개로 바꿨어요!");
                }
            }
            if ("alchemyBack".equals(action)) { // [2026-11-20] "연금술사 복원 -- 악마의 광석 조각 1개 -> 금광석 3~8개 ... 철광석까지 다시 원복"
                if (!mineAtNpc(row, "alchemist")) { out.put("message", "연금술사에게서만 되돌릴 수 있어요."); return out; }
                int from = -1;
                try { from = Integer.parseInt(code); } catch (NumberFormatException ex) { }
                if (from < 2 || from > 4) { out.put("message", "그건 되돌릴 수 없어요."); return out; }
                String fCode = from == 4 ? S5MineMap.DEMON_ORE_CODE : S5MineMap.ORE_CODES[from - 1], fName = from == 4 ? S5MineMap.DEMON_ORE_NAME : S5MineMap.ORE_NAMES[from - 1];
                String tCode = S5MineMap.ORE_CODES[from - 2], tName = S5MineMap.ORE_NAMES[from - 2];
                int haveF = mineOreHave(userName).getOrDefault(fCode, 0), n = grade <= 0 ? haveF : Math.min(haveF, grade);
                if (n < 1) out.put("message", "⚗️ " + fName + "이(가) 없어요.");
                else if (dao.takeUserOre(userName, fCode, n) == 0) out.put("message", "⚗️ " + fName + "이(가) 모자라요.");
                else {
                    long got = 0;
                    for (int k = 0; k < n; k++) got += MINE_ALCHEMY_BACK_MIN + RND.nextInt(MINE_ALCHEMY_BACK_MAX - MINE_ALCHEMY_BACK_MIN + 1);
                    int gi = (int) Math.min(Integer.MAX_VALUE, got);
                    dao.addUserOre(userName, tCode, gi);
                    out.put("ok", true);
                    out.put("got", gi);
                    out.put("message", "⚗️ " + fName + " " + String.format("%,d", n) + "개를 풀어 " + tName + " " + String.format("%,d", gi) + "개로 되돌렸어요!");
                }
            }
            Map<String, Integer> hv = mineOreHave(userName);
            int[] q = new int[5];
            for (int i = 0; i < 4; i++) q[i] = hv.getOrDefault(S5MineMap.ORE_CODES[i], 0);
            q[4] = hv.getOrDefault(S5MineMap.DEMON_ORE_CODE, 0); // [2026-11-20] 악마의 광석 조각
            out.put("have", q);
            out.put("rate", MINE_ALCHEMY_RATE);
            out.put("backMin", MINE_ALCHEMY_BACK_MIN); out.put("backMax", MINE_ALCHEMY_BACK_MAX);
            out.put("ores", mineOreList(userName));
            return out;
        }
        if ("primalInfo".equals(action) || "primalMake".equals(action) || "primalUse".equals(action)) { // [2026-11-20] 최후의 방어선 태초의 보석 세공사
            boolean wide = items.getOrDefault(MINE_ITEM_AURA_WIDE, 0) > 0;
            int gem = mineOreHave(userName).getOrDefault(S5MineMap.PRIMAL_GEM_CODE, 0);
            if ("primalMake".equals(action)) {
                if (!mineAtNpc(row, "primalsmith")) { out.put("message", "태초의 보석 세공사에게서만 만들 수 있어요."); return out; }
                if (wide || gem > 0) { out.put("message", "💎 태초의 보석은 한 사람에게 하나뿐이라네. " + (wide ? "이미 그대 검에 깃들어 있군." : "가진 걸 먼저 써 보게.")); }
                else {
                    String[][] pc = minePrimalCost();
                    if (!mineCostOk(pc, mineOreHave(userName))) { out.put("message", "💎 재료가 모자라요." + mineLackText(pc, userName)); }
                    else { mineTakeCost(userName, pc, 1); dao.addUserOre(userName, S5MineMap.PRIMAL_GEM_CODE, 1); gem = 1; out.put("ok", true); out.put("message", "💎 모든 것이 하나로 녹아... " + S5MineMap.PRIMAL_GEM_NAME + "이(가) 태어났어요!"); mineGossipAdd(mineNick(userName) + " 님이 " + S5MineMap.PRIMAL_GEM_NAME + "을(를) 만들었대요!"); }
                }
            } else if ("primalUse".equals(action)) {
                if (!mineAtNpc(row, "primalsmith")) { out.put("message", "태초의 보석 세공사에게서만 쓸 수 있어요."); return out; }
                if (wide) out.put("message", "💎 이미 태초의 빛이 검에 깃들어 있어요. (한 번만)");
                else if (gem < 1 || dao.takeUserOre(userName, S5MineMap.PRIMAL_GEM_CODE, 1) == 0) out.put("message", "💎 " + S5MineMap.PRIMAL_GEM_NAME + "이(가) 없어요.");
                else { dao.upsertMineItem(userName, MINE_ITEM_AURA_WIDE, 1); items.put(MINE_ITEM_AURA_WIDE, 1); MINE_AURA_WIDE_U.put(userName, true); wide = true; gem--; out.put("ok", true); out.put("aura", mineAuraInfo(items)); out.put("message", "✨ 태초의 보석이 검에 스며들었어요! 이제 검기가 3칸 폭으로 뻗어 나가요."); }
            }
            out.put("cost", mineCostList(minePrimalCost(), mineOreHave(userName)));
            out.put("gem", gem);
            out.put("wide", wide);
            out.put("ores", mineOreList(userName));
            return out;
        }
        if ("refine".equals(action) || "refineInfo".equals(action)) { // [2026-11-17] 최후의 방어선 정제사 -- 악마의 광석 조각 하나마다 MINE_REFINE_PCT%로 미스릴 원석
            int shard = mineOreHave(userName).getOrDefault(S5MineMap.DEMON_ORE_CODE, 0);
            out.put("mana", mineOreHave(userName).getOrDefault(S5MineMap.MANA_STONE_CODE, 0)); // [2026-11-18] 정제된 마석
            if ("refine".equals(action)) {
                if (!mineAtNpc(row, "refiner")) { out.put("message", "정제사에게서만 정제할 수 있어요."); return out; }
                int n = grade <= 0 ? shard : Math.min(shard, grade);
                if (n < 1 || dao.takeUserOre(userName, S5MineMap.DEMON_ORE_CODE, n) == 0) { out.put("message", "⚒️ " + S5MineMap.DEMON_ORE_NAME + "이(가) 없어요. 악마의 땅에서 곡괭이로 캐 오게."); out.put("shard", shard); return out; }
                int got = 0;
                for (int i = 0; i < n; i++) if (RND.nextInt(100) < MINE_REFINE_PCT) got++;
                if (got > 0) dao.addUserOre(userName, S5MineMap.ORE_CODES[4], got);
                shard -= n;
                out.put("ok", true);
                out.put("got", got);
                out.put("message", "🔥 " + S5MineMap.DEMON_ORE_NAME + " " + n + "개를 정제했어요. " + (got > 0 ? "💎 " + S5MineMap.ORE_NAMES[4] + " " + got + "개가 남았어요!" : "...모두 재가 되어 버렸어요.") + " (남은 조각 " + shard + ")");
                out.put("ores", mineOreList(userName));
            }
            out.put("shard", shard);
            out.put("pct", MINE_REFINE_PCT);
            return out;
        }
        int have = mineOreHave(userName).getOrDefault(S5MineMap.TOWER_TOKEN_CODE, 0), block = mineTwBlock(items);
        if ("pull".equals(action) || "pull10".equals(action)) {
            boolean acc = "acc".equals(code);
            int tier = grade, n = "pull10".equals(action) ? 10 : 1;
            if (!mineAtNpc(row, "tokenshop")) { out.put("message", "증표 상인에게서만 뽑을 수 있어요."); return out; }
            if (tier < 0 || tier > 3) { out.put("message", "그런 상자는 없어요."); return out; }
            if (block < MG_TIER_UNLOCK[tier] && !isMineAdmin(userName)) { out.put("message", "🔒 " + MG_TIER_NAMES[tier] + " 상자는 탑 " + MG_TIER_UNLOCK[tier] + "층 수문장을 쓰러뜨리면 열려요."); return out; }
            int price = MG_TIER_PRICE[tier] * n;
            if (have < price || dao.takeUserOre(userName, S5MineMap.TOWER_TOKEN_CODE, price) == 0) { out.put("message", "🗼 " + S5MineMap.TOWER_TOKEN_NAME + "이(가) " + price + "개 있어야 해요. (보유 " + have + ")"); return out; }
            have -= price;
            List<String> got = new ArrayList<>();
            int best = 0;
            for (int i = 0; i < n; i++) {
                int[] mact = mineMercActive(items); // [2026-11-18] 데리고 다니는 용병(둘이면 둘 중 하나) 직업의 것으로
                int g = mgRoll(tier), cls = acc ? 9 : mact.length > 0 ? mineMercCls(items, mact[RND.nextInt(mact.length)]) : items.getOrDefault(MINE_ITEM_MERC_ON, 0) > 0 ? Math.min(2, items.get(MINE_ITEM_MERC_ON) - 1) : RND.nextInt(3); // [2026-11-17] 지금 용병 직업의 것으로
                String part = acc ? MG_PARTS[3 + RND.nextInt(3)] : MG_PARTS[RND.nextInt(3)], ic = mgCode(part, cls, g);
                int q = items.getOrDefault(ic, 0) + 1;
                dao.upsertMineItem(userName, ic, q);
                items.put(ic, q);
                got.add(mgName(part, cls, g));
                best = Math.max(best, g);
            }
            if (best >= 6) mineGossipAdd(mineNick(userName) + " 님이 " + MG_TIER_NAMES[tier] + " " + (acc ? "악세" : "장비") + " 상자에서 ★6 동료 장비를 뽑았대요!");
            out.put("ok", true);
            out.put("got", got);
            out.put("best", best);
            out.put("message", "🎁 " + MG_TIER_NAMES[tier] + " " + (acc ? "악세" : "장비") + " 상자 " + n + "번 -- " + String.join(", ", got));
        } else if ("equip".equals(action)) {
            int c = grade;
            if (c < 0 || c > MINE_MERC_K3 || mineMercLv(items, c) <= 0) { out.put("message", "계약한 용병에게만 끼울 수 있어요."); return out; } // [2026-11-18] c = 기록 번호(3 = 두 번째 용병)
            int mcl = mineMercCls(items, c);
            String[] p = code == null ? new String[0] : code.split("_");
            if (p.length != 3 || !"MG".equals(p[0]) || p[1].length() != 2) { out.put("message", "그런 장비는 없어요."); return out; }
            String part = p[1].substring(0, 1);
            int cls = p[1].charAt(1) - '0', g;
            try { g = Integer.parseInt(p[2]); } catch (NumberFormatException e) { out.put("message", "그런 장비는 없어요."); return out; }
            // [2026-11-17] "용병 장비가 직업별로 나뉘어 있는 걸 무기/갑옷/투구/악세로 -- 지금 장착 직업군에 따라 자동 변환(6성 검 + 5성 활 -> 검사면 전부 검)" --
            //  무기/투구/갑옷은 직업과 상관없이 같은 부위·같은 ★이면 같은 장비로 친다. 끼우면 그 용병 직업의 것으로(가진 것 중 아무 직업 것이나 하나 쓴다).
            if (java.util.Arrays.asList(MG_PARTS).contains(part) && !mgAcc(part) && items.getOrDefault(code, 0) < 1) for (int oc = 0; oc < 3; oc++) if (items.getOrDefault(mgCode(part, oc, g), 0) > 0) { code = mgCode(part, oc, g); cls = oc; break; }
            if (!java.util.Arrays.asList(MG_PARTS).contains(part) || g < 1 || g > 7 || items.getOrDefault(code, 0) < 1) { out.put("message", "가진 장비가 아니에요."); return out; }
            if (!mgAcc(part)) cls = mcl;
            String slot = mineMercKey(c, "G" + part);
            int old = items.getOrDefault(slot, 0);
            dao.upsertMineItem(userName, code, items.get(code) - 1);
            items.put(code, items.get(code) - 1);
            if (old > 0) { String oc = mgCode(part, mgAcc(part) ? 9 : mcl, old); int oq = items.getOrDefault(oc, 0) + 1; dao.upsertMineItem(userName, oc, oq); items.put(oc, oq); }
            dao.upsertMineItem(userName, slot, g);
            items.put(slot, g);
            out.put("ok", true);
            out.put("message", "🛡️ " + mineMercNm(items, c, "NM") + "에게 " + mgName(part, cls, g) + "을(를) 끼웠어요. (" + mgEffText(part, g) + ")");
        } else if ("unequip".equals(action)) {
            int c = grade;
            if (c < 0 || c > MINE_MERC_K3 || !java.util.Arrays.asList(MG_PARTS).contains(code)) { out.put("message", "그런 칸은 없어요."); return out; }
            String slot = mineMercKey(c, "G" + code);
            int old = items.getOrDefault(slot, 0);
            if (old <= 0) { out.put("message", "끼운 장비가 없어요."); return out; }
            String oc = mgCode(code, mgAcc(code) ? 9 : mineMercCls(items, c), old);
            int oq = items.getOrDefault(oc, 0) + 1;
            dao.upsertMineItem(userName, oc, oq);
            items.put(oc, oq);
            dao.upsertMineItem(userName, slot, 0);
            items.put(slot, 0);
            out.put("ok", true);
            out.put("message", mineMercNm(items, c, "NM") + "의 " + MG_PART_NAMES[java.util.Arrays.asList(MG_PARTS).indexOf(code)] + "을(를) 뺐어요.");
        } else if ("mg7".equals(action)) { // [2026-11-23] ★6 -> ★7(햇빛석) -- 가진 것: code = 장비 코드 / 끼운 것: code = 부위(한 글자), grade = 용병 기록 번호
            if (!mineAtNpc(row, "mgsmith")) { out.put("message", "왕도 용병소의 햇빛 대장장이에게서만 올릴 수 있어요."); return out; }
            int sun = mineOreHave(userName).getOrDefault(S5MineMap.SUN_STONE_CODE, 0);
            boolean worn = code != null && code.length() == 1;
            String part, src = null, slot = null;
            int cls;
            if (worn) {
                int c = grade;
                if (c < 0 || c > MINE_MERC_K3 || mineMercLv(items, c) <= 0 || !java.util.Arrays.asList(MG_PARTS).contains(code)) { out.put("message", "그런 칸은 없어요."); return out; }
                slot = mineMercKey(c, "G" + code);
                if (items.getOrDefault(slot, 0) != 6) { out.put("message", "★6 장비만 7성으로 올릴 수 있어요."); return out; }
                part = code;
                cls = mgAcc(part) ? 9 : mineMercCls(items, c);
            } else {
                String[] p = code == null ? new String[0] : code.split("_");
                if (p.length != 3 || !"MG".equals(p[0]) || p[1].length() != 2 || !"6".equals(p[2])) { out.put("message", "★6 장비만 7성으로 올릴 수 있어요."); return out; }
                part = p[1].substring(0, 1);
                cls = p[1].charAt(1) - '0';
                if (!java.util.Arrays.asList(MG_PARTS).contains(part)) { out.put("message", "그런 장비는 없어요."); return out; }
                src = code;
                if (!mgAcc(part) && items.getOrDefault(src, 0) < 1) for (int oc = 0; oc < 3; oc++) if (items.getOrDefault(mgCode(part, oc, 6), 0) > 0) { src = mgCode(part, oc, 6); cls = oc; break; } // 무기/투구/갑옷은 직업 구분 없이
                if (items.getOrDefault(src, 0) < 1) { out.put("message", "가진 장비가 아니에요."); return out; }
            }
            if (sun < MINE_MG7_SUN || dao.takeUserOre(userName, S5MineMap.SUN_STONE_CODE, MINE_MG7_SUN) == 0) { out.put("message", "☀️ " + S5MineMap.SUN_STONE_NAME + "이(가) " + MINE_MG7_SUN + "개 있어야 해요. (보유 " + sun + ")"); return out; }
            if (worn) { dao.upsertMineItem(userName, slot, 7); items.put(slot, 7); }
            else {
                dao.upsertMineItem(userName, src, items.get(src) - 1); items.put(src, items.get(src) - 1);
                String dc = mgCode(part, cls, 7);
                int dq = items.getOrDefault(dc, 0) + 1;
                dao.upsertMineItem(userName, dc, dq); items.put(dc, dq);
            }
            out.put("ok", true);
            out.put("best", 7);
            out.put("message", "☀️ 햇빛이 스며들어 " + mgName(part, cls, 7) + "(으)로 올랐어요! (" + mgEffText(part, 7) + " · " + S5MineMap.SUN_STONE_NAME + " -" + MINE_MG7_SUN + ")");
        } else if ("gem".equals(action)) { // [2026-11-15] 보석 사기 -- code = 보석 코드, grade = 개수
            if (!mineAtNpc(row, "tokenshop")) { out.put("message", "증표 상인에게서만 살 수 있어요."); return out; }
            Object[] gm = null;
            for (Object[] q : mineTokenGems()) if (q[0].equals(code)) gm = q;
            if (gm == null) { out.put("message", "그런 보석은 팔지 않아요."); return out; }
            int n = Math.max(1, Math.min(100, grade)), bought = mineTkBought(items, code, System.currentTimeMillis()), lim = mineTkLimit(code);
            if (bought + n > lim) { out.put("message", "🛒 " + gm[1] + "은(는) 모두 " + lim + "개까지만 살 수 있어요. (지금까지 " + bought + "/" + lim + (bought < lim ? " -- " + (lim - bought) + "개 더" : "") + ")"); return out; }
            int price = (Integer) gm[2] * n;
            if (have < price || dao.takeUserOre(userName, S5MineMap.TOWER_TOKEN_CODE, price) == 0) { out.put("message", "🗼 " + S5MineMap.TOWER_TOKEN_NAME + "이(가) " + price + "개 있어야 해요. (보유 " + have + ")"); return out; }
            have -= price;
            dao.addUserOre(userName, code, n);
            dao.upsertMineItem(userName, "TKT_" + code, bought + n); items.put("TKT_" + code, bought + n);
            out.put("ok", true);
            out.put("message", "💎 " + gm[1] + " " + n + "개를 샀어요. (증표 -" + price + " · 구매 " + (bought + n) + "/" + lim + ")");
        }
        List<HashMap<String, Object>> inv = new ArrayList<>(); // 가진 동료 장비(★ 높은 것부터) -- [2026-11-17] 무기/투구/갑옷은 직업 구분 없이 합쳐서 지금 용병 직업 이름으로
        int curC = Math.max(0, Math.min(2, items.getOrDefault(MINE_ITEM_MERC_ON, 1) - 1));
        for (int g = 7; g >= 1; g--) for (String part : MG_PARTS) {
            int q = 0;
            if (mgAcc(part)) q = items.getOrDefault(mgCode(part, 9, g), 0);
            else for (int oc = 0; oc < 3; oc++) q += items.getOrDefault(mgCode(part, oc, g), 0);
            if (q <= 0) continue;
            int cls = mgAcc(part) ? 9 : curC;
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", mgCode(part, cls, g)); m.put("part", part); m.put("cls", cls); m.put("g", g); m.put("q", q);
            m.put("name", mgName(part, cls, g)); m.put("eff", mgEffText(part, g));
            inv.add(m);
        }
        out.put("inv", inv);
        List<HashMap<String, Object>> tiers = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("name", MG_TIER_NAMES[t]); m.put("price", MG_TIER_PRICE[t]); m.put("unlock", MG_TIER_UNLOCK[t]);
            m.put("open", block >= MG_TIER_UNLOCK[t] || isMineAdmin(userName));
            List<String> rl = new ArrayList<>();
            for (int g = 1; g <= 6; g++) if (MG_TIER_RATE[t][g] > 0) rl.add("★" + g + " " + (MG_TIER_RATE[t][g] % 10 == 0 ? String.valueOf(MG_TIER_RATE[t][g] / 10) : String.valueOf(MG_TIER_RATE[t][g] / 10.0)) + "%");
            m.put("rate", String.join(" / ", rl));
            tiers.add(m);
        }
        out.put("tiers", tiers);
        List<HashMap<String, Object>> gems = new ArrayList<>(); // [2026-11-15] 증표로 사는 보석
        for (Object[] q : mineTokenGems()) { HashMap<String, Object> m = new HashMap<>(); m.put("code", q[0]); m.put("name", q[1]); m.put("price", q[2]); m.put("lim", mineTkLimit((String) q[0])); m.put("bought", mineTkBought(items, (String) q[0], System.currentTimeMillis())); gems.add(m); } // [2026-11-17] 하루 한도
        out.put("gems", gems);
        out.put("have", have);
        out.put("mercs", mineMercCards(items));
        out.put("sun", mineOreHave(userName).getOrDefault(S5MineMap.SUN_STONE_CODE, 0)); out.put("sunCost", MINE_MG7_SUN); // [2026-11-23] ★6 -> ★7
        if (!out.containsKey("message")) out.put("ok", true);
        out.put("ores", mineOreList(userName));
        return out;
    }

    // ---- [2026-11-15] 탑 방 방식 -- "실제로 주사위를 굴리는 건 안 해도 된다. 마물의 숲처럼 한 개 방을 반복 표현하는 방식으로, 오른쪽으로만 이동
    //  (계단방은 위아래, 왼쪽은 탑 나가기). 오른쪽으로 가는 순간 주사위 굴리는 판정. 주사위 게임에 집착 안 해도 된다".
    //  층마다 방 하나(tw{층})를 되풀이해 지난다: 오른쪽 문을 지나는 순간 다음 방이 무엇인지 정해지고(MINE_TW_ROOM_PCT -- 전투/보물/증표/함정/행운, 나머지 빈 방)
    //  같은 방의 왼쪽 문 앞에 다시 선다. 전투방은 방을 지키는 몬스터를 쓰러뜨려야 오른쪽 문이 열린다. S5MineMap.towerRooms(층)번 지나면 계단방(tw{층}s):
    //  위 계단 = 다음 층(올라갈 수 있는 층 TW_MAX가 는다), 아래 계단 = 아래층, 왼쪽 문은 언제나 탑 밖(탑 입구 맵). 수문장 층은 수문장을 쓰러뜨려야 위 계단이 열린다.
    //  지금 층에서 몇 번째 방인지/무슨 방인지는 메모리(MINE_TW_RUN -- 배포하면 그 층 첫 방부터).
    static final class TwRun { final int floor; int step, mobId = -1, spike; char type = 'E'; TwRun(int f) { floor = f; } }
    // [2026-11-16] "보물방/몬스터방/함정방 구분이 잘 안 된다 -- 함정방엔 바닥에 가시가 있어서 체력을 잃으면서 간다" -- 함정방은 들어설 때 한 번에 깎지 않고,
    //  바닥의 가시 띠(mineTwSpikeCell: 세로 두 줄씩 세 띠)를 밟을 때마다 최대 체력의 MINE_TW_SPIKE_PCT씩(1 아래로는 안 내려간다). 화면이 밟은 수를 알리고(mineTower spike),
    //  띠는 방을 가로질러 있어 적어도 MINE_TW_SPIKE_NEED번은 밟아야 하므로 덜 알린 채 오른쪽 문을 지나면 모자란 만큼 그때 깎는다. 한 방에서 MINE_TW_SPIKE_MAX번까지만.
    static final double MINE_TW_SPIKE_PCT = 0.025;
    static final int MINE_TW_SPIKE_NEED = 6, MINE_TW_SPIKE_MAX = 14;
    static boolean mineTwSpikeCell(int x, int y) { return y >= 2 && y <= S5MineMap.TROOM_H - 3 && (x == 6 || x == 7 || x == 10 || x == 11 || x == 14 || x == 15); }
    /** 가시를 k번 밟았다 -- 체력을 깎고 out(있으면)에 combat / spikeDmg. */
    void mineTwSpikeHit(String userName, HashMap<String, Object> row, Map<String, Integer> items, TwRun run, int k, long now, HashMap<String, Object> out) {
        k = Math.min(k, MINE_TW_SPIKE_MAX - run.spike);
        if (k <= 0) return;
        int lv = mineLv(row), lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int hpNow = mineHpNow(row, now, lock, rb), cut = Math.max(1, (int) Math.round(mineHpMax(lv, rb) * MINE_TW_SPIKE_PCT)) * k, nh = Math.max(1, hpNow - cut);
        dao.updateMineCombat(userName, lv, exp, nh, now, 0);
        MINE_HP_VIRT.remove(userName);
        row.put("M_HP", nh); row.put("M_HP_AT_MS", now);
        run.spike += k;
        if (out != null) { out.put("combat", mineCombatInfo(lv, exp, nh, now, false, lock, rb)); out.put("spikeDmg", hpNow - nh); }
    }
    static final java.util.concurrent.ConcurrentHashMap<String, TwRun> MINE_TW_RUN = new java.util.concurrent.ConcurrentHashMap<>();
    static final int[] MINE_TW_ROOM_PCT = { 50, 12, 10, 12, 8 }; // 전투 C / 보물 T / 증표 P / 함정 X / 행운 L (나머지 8% = 빈 방 E)
    static final String MINE_TW_ROOM_KEYS = "CTPXL";
    static TwRun mineTwRun(String userName, int f) {
        TwRun r = MINE_TW_RUN.get(userName);
        if (r == null || r.floor != f) { r = new TwRun(f); MINE_TW_RUN.put(userName, r); }
        return r;
    }
    /** 탑 밖으로(탑 입구 맵의 큰 문 앞) -- 내 인스턴스와 진행을 비운다. */
    void mineTowerLeave(String userName, long now) {
        S5MineMap g = S5MineMap.get(S5MineMap.TOWER_GATE);
        mineDisengageAll(userName);
        mineSoloReset(userName);
        MINE_TW_RUN.remove(userName);
        int[] to = mineNearWalk(g, g.w / 2, S5MineMap.GATE_DOOR_Y + 1);
        dao.updateMinePos(userName, g.id, to == null ? g.spawnX : to[0], to == null ? g.spawnY : to[1], now);
    }
    /** n층으로 들어간다(그 층 첫 방/수문장의 방/마을). 못 가면 지금 자리(stay, sx, sy)에 그대로 두고 까닭을 message로. */
    HashMap<String, Object> mineTowerEnter(String userName, Map<String, Integer> items, int n, S5MineMap stay, int sx, int sy, long now, HashMap<String, Object> out) {
        return mineTowerEnter(userName, items, n, stay, sx, sy, now, out, false);
    }
    /** down = 아래 계단으로 내려왔다 -- [2026-11-16] "29층에서 28층으로 내려가면 바로 다시 올라갈 수 있게 계단층으로, 위쪽에서 리스폰": 방 층은 그 층의 계단방(위 계단 바로 아래)에, 수문장 층은 위 계단 앞에. */
    HashMap<String, Object> mineTowerEnter(String userName, Map<String, Integer> items, int n, S5MineMap stay, int sx, int sy, long now, HashMap<String, Object> out, boolean down) {
        boolean admin = isMineAdmin(userName);
        int max = mineTwMax(items), block = mineTwBlock(items);
        String err = null;
        if (n > S5MineMap.TOWER_FLOORS) err = "🚧 " + n + "층부터는 아직 준비 중이에요.";
        else if (n < 1) err = "그런 층은 없어요.";
        else if (!admin && n > max) err = "🪜 아직 " + n + "층으로 가는 계단을 못 찾았어요. (지금 " + max + "층까지)";
        // [2026-11-16] "지난 층 다시 갈 수 있게" -- 수문장을 쓰러뜨린 구역 아래로도 올라가 본 층(max)까지는 언제든 간다(예전엔 끝난 구역은 잠겼다)
        else if (false && S5MineMap.towerBossFloor(n) && mineTwBossToday(items, now) >= MINE_TW_BOSS_DAILY) err = /* [2026-11-17] 수문장 층은 늘 들어간다(반복 보상만 하루 3번 -- mineTowerKill) */ "⏳ 오늘 수문장 처치 횟수(" + MINE_TW_BOSS_DAILY + "/" + MINE_TW_BOSS_DAILY + ")를 모두 썼어요. 내일 다시 도전해 주세요.";
        if (err != null) {
            dao.updateMinePos(userName, stay.id, sx, sy, now);
            out.put("message", err);
            return out;
        }
        boolean toStair = down && S5MineMap.towerRoomFloor(n);
        S5MineMap tm = S5MineMap.get(toStair ? S5MineMap.towerStairId(n) : S5MineMap.towerId(n));
        mineDisengageAll(userName);
        mineSoloReset(userName); // 1인 인스턴스 -- 층에 들어갈 때마다 처음부터
        TwRun nr = new TwRun(n);
        int ex = tm.spawnX, ey = tm.spawnY;
        if (toStair) { nr.step = S5MineMap.towerRooms(n); nr.type = 'U'; ex = tm.w / 2; ey = 2; } // 위 계단 바로 아래
        else if (down) { ex = tm.w / 2; ey = 2; } // 수문장 층 -- 위 계단 앞
        MINE_TW_RUN.put(userName, nr);
        dao.updateMinePos(userName, tm.id, ex, ey, now);
        out.put("ok", true);
        if (toStair) out.put("message", "🪜 " + n + "층 계단방으로 내려왔어요. 위쪽 계단으로 바로 다시 올라갈 수 있어요.");
        else out.put("message", "🗼 " + tm.name + (S5MineMap.towerRoomFloor(n) ? " -- 오른쪽 문으로 나아가세요. 방을 " + S5MineMap.towerRooms(n) + "개 지나면 계단방이에요. (왼쪽 문 = 탑 밖)"
                : S5MineMap.towerBossFloor(n) ? " -- 수문장을 쓰러뜨리면 위쪽 계단이 열려요." : " -- 쉬었다 가세요. 위쪽 계단이 다음 구역이에요."));
        out.put("state", mineState(userName));
        return out;
    }
    /** 탑 안팎의 문/계단(mineWarp에서) -- 탑과 상관없는 포탈이면 null(보통 포탈로). */
    HashMap<String, Object> mineTowerPortal(String userName, HashMap<String, Object> row, S5MineMap map, S5MineMap.Portal p, Map<String, Integer> items, int x, int y, long now) {
        int f = S5MineMap.towerFloor(map.id), tf = S5MineMap.towerFloor(p.target);
        if (f <= 0 && tf <= 0) return null; // 탑 입구 맵의 남쪽 문(도시로) 같은 보통 포탈
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        boolean admin = isMineAdmin(userName), ok = mineTowerOk(userName);
        if (f > 0 && S5MineMap.TOWER_GATE.equals(p.target)) { // 왼쪽 문 -- 탑 밖으로(언제나)
            mineTowerLeave(userName, now);
            out.put("ok", true);
            out.put("message", "🚪 탑에서 나왔어요.");
            out.put("state", mineState(userName));
            return out;
        }
        if (!ok) { // 일반 공개 전
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("locked", true);
            out.put("message", f > 0 ? "🚧 탑은 아직 준비 중이에요. 왼쪽 문으로 나갈 수 있어요." : "🚧 탑의 문은 아직 굳게 닫혀 있어요. (준비 중)");
            return out;
        }
        MineEngage e = MINE_ENGAGE.get(userName);
        if (f <= 0) { // 탑 입구의 큰 문 -- 가장 높이 올라간 층으로(수문장 층에서 막히면 그 까닭을 알려 준다)
            int n = Math.max(mineTwBlock(items) + 1, Math.min(mineTwMax(items), S5MineMap.TOWER_FLOORS));
            return mineTowerEnter(userName, items, admin ? Math.max(1, Math.min(mineTwMax(items), S5MineMap.TOWER_FLOORS)) : n, map, x, y, now, out);
        }
        if (map.id.equals(p.target)) return mineTowerNext(userName, row, map, items, f, x, y, now, out); // 오른쪽 문 -- 다음 방
        if (tf > f) { // 위 계단
            if (S5MineMap.towerBossFloor(f) && mineTwBlock(items) < f && !admin) {
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("locked", true);
                out.put("message", "🔒 수문장을 쓰러뜨려야 위쪽 계단이 열려요.");
                return out;
            }
            if (mineTwMax(items) < tf) { dao.upsertMineItem(userName, MINE_ITEM_TW_MAX, Math.min(S5MineMap.TOWER_FLOORS, tf)); items.put(MINE_ITEM_TW_MAX, Math.min(S5MineMap.TOWER_FLOORS, tf)); }
        }
        if (e != null && !e.mobs.isEmpty() && mineEngaged(userName, now) && S5MineMap.towerBossFloor(f)) { // 수문장과 싸우는 중엔 계단으로 못 빠진다
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("message", "⚔️ 수문장과 싸우는 중엔 계단을 쓸 수 없어요. (왼쪽 문으로는 나갈 수 있어요)");
            return out;
        }
        return mineTowerEnter(userName, items, tf, map, x, y, now, out, tf < f);
    }
    /** 방의 오른쪽 문 -- 다음 방으로(이 순간 무슨 방인지 정해진다). 전투방이면 방을 지키는 몬스터를 쓰러뜨린 뒤에만. */
    HashMap<String, Object> mineTowerNext(String userName, HashMap<String, Object> row, S5MineMap map, Map<String, Integer> items, int f, int x, int y, long now, HashMap<String, Object> out) {
        TwRun run = mineTwRun(userName, f);
        if (run.type == 'C' && run.mobId >= 0) { // [2026-11-16] 쓰러뜨리기 전엔 안 열린다(쓰러뜨리면 mineTowerKill이 mobId를 비운다). 화면도 문을 쇠창살로 닫아 둔다
            final int gid = run.mobId;
            S5MineMap.Mob gm = gid < map.mobs().size() ? map.mobs().get(gid) : null;
            MobState gs = MINE_MOB_STATE.get(mineMobKey(map, gid));
            if (gm == null || (gs != null && gs.deadUntil > now)) run.mobId = -1; // 이미 쓰러져 있다
            else {
                if (gs == null) { // 상태가 사라졌으면 다시 세운다
                    final int gmax = S5MineMobs.hp(S5MineMobs.TYPES.get(gm.type), gm.rank);
                    MobState ns = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, gid), k -> new MobState(gmax));
                    synchronized (ns) { ns.fx = gm.x; ns.fy = gm.y; ns.frozenUntil = now + MINE_MOB_FREEZE_MS; }
                }
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("locked", true);
                out.put("message", "⚔️ 방을 지키는 몬스터를 쓰러뜨려야 문이 열려요.");
                return out;
            }
        }
        if (run.type == 'X' && run.spike < MINE_TW_SPIKE_NEED) mineTwSpikeHit(userName, row, items, run, MINE_TW_SPIKE_NEED - run.spike, now, null); // [2026-11-16] 가시 띠를 안 밟고는 못 지난다
        int total = S5MineMap.towerRooms(f), lv = mineLv(row), lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        run.step++;
        run.mobId = -1;
        run.spike = 0;
        List<HashMap<String, Object>> fxDrops = new ArrayList<>(); // [2026-11-16] 방 한가운데 상자/제단에서 쏟아져 나와 빨려 들어오는 것들(화면 연출)
        mineDisengageAll(userName);
        mineSoloReset(userName);
        StringBuilder msg = new StringBuilder();
        if (run.step >= total) { // 계단방
            S5MineMap sm = S5MineMap.get(S5MineMap.towerStairId(f));
            run.type = 'U';
            if (mineTwMax(items) < f + 1 && f + 1 <= S5MineMap.TOWER_FLOORS) dao.upsertMineItem(userName, MINE_ITEM_TW_MAX, f + 1);
            dao.updateMinePos(userName, sm.id, sm.spawnX, sm.spawnY, now);
            msg.append("🪜 계단방이에요! 위쪽 계단은 ").append(f + 1).append("층").append(f > 1 ? ", 아래쪽 계단은 " + (f - 1) + "층" : "").append(", 왼쪽 문은 탑 밖이에요.");
        } else {
            int r = RND.nextInt(100), acc = 0;
            char ty = 'E';
            for (int i = 0; i < MINE_TW_ROOM_PCT.length; i++) { acc += MINE_TW_ROOM_PCT[i]; if (r < acc) { ty = MINE_TW_ROOM_KEYS.charAt(i); break; } }
            run.type = ty;
            dao.updateMinePos(userName, map.id, map.spawnX, map.spawnY, now);
            msg.append("🚪 ").append(run.step).append("/").append(total).append("번째 방 -- ");
            if (ty == 'C' && !map.mobs().isEmpty()) {
                S5MineMap.Mob mb = map.mobs().get(RND.nextInt(map.mobs().size()));
                S5MineMobs.Type mt = S5MineMobs.TYPES.get(mb.type);
                int mmax = S5MineMobs.hp(mt, mb.rank);
                MobState gs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, mb.id), k -> new MobState(mmax));
                synchronized (gs) { gs.respawn(mmax); gs.fx = mb.x; gs.fy = mb.y; gs.target = userName; gs.frozenUntil = now + MINE_MOB_FREEZE_MS; gs.lastHitAt = 0; gs.stunUntil = 0; }
                mineEngage(userName, map, mb.id, now, 0L);
                run.mobId = mb.id;
                msg.append("⚔️ ").append(mt.name).append("이(가) 방을 지키고 있어요! 쓰러뜨려야 오른쪽 문이 열려요.");
            } else if (ty == 'T') {
                int g1 = RND.nextInt(S5MineMap.GEM_DIAMOND), g2 = RND.nextInt(S5MineMap.GEM_DIAMOND);
                if (RND.nextInt(100) < MINE_TW_SEA_PCT) { // [2026-11-16] "탑 내 보상도 바다 보석이 나오게" -- 보물방은 바다 보석이 하나 더
                    int sg = RND.nextInt(S5MineMap.SEA_GEM_CODES.length);
                    dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[sg], 1);
                    fxDrops.add(mineDrop(S5MineMap.SEA_GEM_GRADE_BASE + sg, S5MineMap.SEA_GEM_NAMES[sg], 1));
                    msg.append("🌊 ").append(S5MineMap.SEA_GEM_NAMES[sg]).append(" · ");
                }
                dao.addUserOre(userName, S5MineMap.GEM_CODES[g1], 1);
                dao.addUserOre(userName, S5MineMap.GEM_CODES[g2], 1);
                fxDrops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + g1, S5MineMap.GEM_NAMES[g1], 1));
                fxDrops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + g2, S5MineMap.GEM_NAMES[g2], 1));
                msg.append("📦 보물 상자! 💎 ").append(S5MineMap.GEM_NAMES[g1]).append(" · 💎 ").append(S5MineMap.GEM_NAMES[g2]);
                if (RND.nextInt(100) < 25) { dao.addUserOre(userName, S5MineMap.MOON_STONE_CODE, 1); msg.append(" · 🌙 달빛석"); fxDrops.add(mineDrop(S5MineMap.MOON_STONE_GRADE, S5MineMap.MOON_STONE_NAME, 1)); }
                msg.append(" · ").append(mineTowerToken(userName, MINE_TW_TOKEN_CHEST * mineTwTokenMul(f)));
                fxDrops.add(mineDrop(S5MineMap.TOWER_TOKEN_GRADE, S5MineMap.TOWER_TOKEN_NAME, MINE_TW_TOKEN_CHEST * mineTwTokenMul(f)));
            } else if (ty == 'P') {
                msg.append("🗼 증표가 놓인 방! ").append(mineTowerToken(userName, MINE_TW_TOKEN_TILE * mineTwTokenMul(f)));
                fxDrops.add(mineDrop(S5MineMap.TOWER_TOKEN_GRADE, S5MineMap.TOWER_TOKEN_NAME, MINE_TW_TOKEN_TILE * mineTwTokenMul(f)));
            } else if (ty == 'X') { // [2026-11-16] 들어설 때 한 번에 깎지 않고 가시를 밟을 때마다(mineTwSpikeHit)
                msg.append("⚠️ 함정방! 바닥에 가시가 솟아 있어요. 밟을 때마다 체력이 깎여요.");
            } else if (ty == 'L') {
                dao.updateMineCombat(userName, lv, exp, mineHpCap(lv, lock, rb), now, 0);
                MINE_HP_VIRT.remove(userName);
                msg.append("🍀 행운의 샘! 체력이 가득 찼어요");
                if (RND.nextInt(100) < 30) { int c = RND.nextInt(S5MineMap.GEM_DIAMOND); dao.addUserOre(userName, S5MineMap.cutCode(c), 1); msg.append(" · ✨ ").append(S5MineMap.cutName(c)); fxDrops.add(mineDrop(S5MineMap.CUT_GRADE_BASE + c, S5MineMap.cutName(c), 1)); }
            } else { run.type = 'E'; msg.append("조용한 빈 방이에요. 숨을 고르고 나아가세요."); }
        }
        out.put("ok", true);
        out.put("message", msg.toString());
        out.put("state", mineState(userName));
        if (!fxDrops.isEmpty()) out.put("twDrops", fxDrops);
        return out;
    }
    /** [2026-11-15] 탑 -- action=info / go&n(층 이동) / exit(탑 입구로) / ferry(나루터 <-> 탑의 도시). 방 사이 이동은 문(mineWarp -> mineTowerPortal). */
    public HashMap<String, Object> mineTower(String userName, String action, int n) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int max = mineTwMax(items), block = mineTwBlock(items), floor = S5MineMap.towerFloor(map.id);
        boolean admin = isMineAdmin(userName);
        if (admin) { max = S5MineMap.TOWER_FLOORS; block = Math.max(block, S5MineMap.TOWER_FLOORS - 1); } // [2026-11-16] "관리자는 전층 다 갈 수 있게" -- 층 목록/계획에 전층 클리어로
        out.put("max", max);
        out.put("block", block);
        out.put("floor", floor);
        out.put("top", S5MineMap.TOWER_TOP); // [2026-11-15] 계획한 꼭대기(100층) -- 열린 층(open)보다 위는 준비 중
        out.put("open", S5MineMap.TOWER_FLOORS);
        out.put("bossNames", S5MineMobs.TOWER_BOSS_NAMES);
        out.put("bossDone", mineTwBossToday(items, now));
        out.put("bossDaily", MINE_TW_BOSS_DAILY);
        if ("spike".equals(action)) { // [2026-11-16] 함정방 가시를 밟았다(n번 -- 화면이 모아서 알린다)
            TwRun sr = MINE_TW_RUN.get(userName);
            out.put("ok", true);
            if (sr != null && sr.type == 'X' && floor > 0 && sr.floor == floor && n > 0) mineTwSpikeHit(userName, row, items, sr, Math.min(n, 4), now, out);
            return out;
        }
        boolean towerOk = mineTowerOk(userName); // [2026-11-15] 일반 공개 전엔 관리자만
        if ("info".equals(action)) { out.put("ok", true); out.put("ferryOk", towerOk); return out; } // [2026-11-16] "하피 처치 기록이 없어도 이용할 수 있게"
        if (!towerOk && !"exit".equals(action) && !("ferry".equals(action) && S5MineMap.TOWER_CITY.equals(map.id))) { // 이미 안에 있던 사람은 나갈 수만 있다
            out.put("message", S5MineMap.HARPY_FERRY.equals(map.id) ? "⛵ 뱃사공: 강 건너편은 아직 길을 닦는 중이라네. 조금만 기다려 주게. (준비 중)" : "🚧 탑은 아직 준비 중이에요.");
            return out;
        }
        if (mineFrozen(userName, now) != null || mineStunned(userName, now)) { out.put("message", "지금은 움직일 수 없어요."); return out; }
        if ("ferry".equals(action)) {
            if (S5MineMap.HARPY_FERRY.equals(map.id)) {
                S5MineMap c = S5MineMap.get(S5MineMap.TOWER_CITY); // [2026-11-16] 하피 처치 기록 없이도 건넌다
                if (items.getOrDefault(MINE_ITEM_VISIT_TCITY, 0) < 1) { dao.upsertMineItem(userName, MINE_ITEM_VISIT_TCITY, 1); items.put(MINE_ITEM_VISIT_TCITY, 1); }
                mineDisengageAll(userName);
                dao.updateMinePos(userName, c.id, c.spawnX, c.spawnY, now);
                out.put("message", "⛵ 강을 건너 요새 도시 엘리시온에 도착했어요!");
            } else if (S5MineMap.TOWER_CITY.equals(map.id)) {
                S5MineMap fr = S5MineMap.get(S5MineMap.HARPY_FERRY);
                mineDisengageAll(userName);
                dao.updateMinePos(userName, fr.id, fr.spawnX, fr.spawnY - 6, now);
                out.put("message", "⛵ 마물의 숲 나루터로 돌아왔어요.");
            } else { out.put("message", "뱃사공에게서만 배를 탈 수 있어요."); return out; }
            out.put("ok", true);
            out.put("state", mineState(userName));
            return out;
        }
        if ("exit".equals(action)) {
            if (floor <= 0) { out.put("message", "탑 안에서만 나갈 수 있어요."); return out; }
            mineTowerLeave(userName, now);
            out.put("ok", true);
            out.put("message", "🗼 탑에서 나왔어요.");
            out.put("state", mineState(userName));
            return out;
        }
        if ("go".equals(action)) { // 탑 관리인(탑 입구/마을) 또는 탑 안의 [층 이동] -- 올라 본 층으로 바로
            if (floor <= 0 && !(S5MineMap.TOWER_GATE.equals(map.id) && mineAtNpc(row, "towergate"))) { out.put("message", "탑 입구의 탑 관리인에게 말하거나 탑 안에서 옮길 수 있어요."); return out; }
            MineEngage e = MINE_ENGAGE.get(userName);
            if (e != null && !e.mobs.isEmpty() && mineEngaged(userName, now)) { out.put("message", "⚔️ 싸우는 중엔 층을 옮길 수 없어요."); return out; }
            int[] pos = minePos(row, map, 0);
            return mineTowerEnter(userName, items, n, map, pos[0], pos[1], now, out);
        }
        out.put("message", "이제 탑은 방을 지나며 올라가요. 오른쪽 문으로 나아가세요."); // [2026-11-15] 주사위(roll)는 없앴다
        return out;
    }

    // ---- [2026-11-15] 월드보스 -- "모두가 참여하고 보상을 순차 지급, 한 번에 모든 유저가 오면 좋겠다".
    //  같은 날 다시: "맵 초기화마다 1번만 보상, 동쪽 성곽 맵에 드래곤 형태로, 패턴은 알아서, 체력이 다한 플레이어는 회색 화면으로 두다가 작은 카운트 뒤 10초 뒤 부활,
    //  언제 처치될지 / 모두가 참여하려면 어떻게 할지 고민 -- 알아서 만들고 보고, 보상은 전체 인원 동일(기본 별빛석·칠요석, 확률로 은하수 조각), 용의 반지 Lv1 확률 드랍(개별 확률)".
    //  - 때와 곳: (처음엔 매일 21시부터 30분 -- 아래 상수 위 설명대로 매시 정각 10분으로 바뀜) 정해진 시각부터 MINE_WB_LEN_MS 동안 동쪽 성곽 내성 한가운데에 심연의 거룡. MINE_WB_NOTICE_MS 전부터 화면 위 띠(위치 알림 wb)와
    //    채팅 응답 끝(방마다 한 번, mineWbChatTail)으로 알리고, 어디서든 [참여](mineWorldBoss join)로 내성 서문 앞에 간다. 광부 투구가 모자라도 그 시간엔 성곽에서 싸울 수 있다(mineMapBlock).
    //  - 언제 쓰러지나(mineWbApply): 거룡 체력은 "몫"으로 깎인다 -- 피해 / 내 기본 공격력(레벨·환생)이 한 사람의 몫이고, 지금 싸우는 사람(최근 MINE_WB_ACTIVE_MS 안에 때린 사람) 수 x
    //    MINE_WB_UNITS 몫이 모이면 쓰러진다. 그래서 몇 명이 오든, 레벨이 높든 낮든 다 같이 치면 8~10분쯤. 너무 빨리 끝나 늦게 온 사람이 놓치지 않게 시작 뒤 MINE_WB_MIN_KILL_MS(5분)까지는
    //    체력이 (1 - 지난 시간 / 5분) 아래로 안 내려가고, MINE_WB_SOFT_MS(15분)이 지나면 받는 피해가 점점 커져(5분마다 +100%) 30분 안에는 쓰러진다.
    //  - 보상(mineWbFinish): 쓰러뜨리면 MINE_WB_MIN_SHARE 몫 이상 싸운 모두에게 같은 보상 -- 별빛석 MINE_WB_STAR + 칠요석 MINE_WB_SEVEN, MINE_WB_GALAXY_PCT% 은하수 조각 1,
    //    MINE_WB_RING_PCT% 용의 반지(사람마다 따로 굴림, 이미 있으면 은하수 조각 1). 맵 초기화 주기(2일)마다 한 번만(WB_CYCLE = 받은 주기 + 1) -- 주기 안에 두 번 열리니 하루 놓쳐도 된다.
    //    순위/구간 보상은 없앴다(전원 같게). 시간 안에 못 잡으면 보상 없음(기회는 그대로).
    //  - 패턴(처음 것 -- 지금 수치와 종류는 아래 상수 위 설명): 어둠 숨결(원거리), 반격, 화염 장판(싸우는 사람 몇 명 발밑 5x5에 예고 ->
    //    예고 뒤 그 안에 있으면 피해), 포효(체력 70%/40%에서 한 번씩, 둘레 14칸 기절 MINE_WB_ROAR_MS).
    //  - 쓰러지면(내성 안에서): 그 자리에 MINE_WB_DOWN_MS(10초) 쓰러져 있다가 체력 가득 차서 일어난다(화면은 회색 + 작은 카운트, 최대 체력 잠금 없음). 그동안 맞지도 때리지도 않는다.
    //  상태는 메모리(배포하면 그 회차는 끝).
    // [2026-11-15] 같은 날 "월드보스 체력을 더 올리고 더 강하게, 매시 정각부터 10분간, 더 많은 패턴과 전체 공격기, 스턴도" --
    //  - 매시 정각부터 MINE_WB_LEN_MS(10분). 예고는 MINE_WB_NOTICE_MS(3분) 전부터, 채팅 알림은 방마다 MINE_WB_CHAT_GAP_MS(3시간)에 한 번.
    //  - 쓰러뜨리려면 싸우는 사람마다 MINE_WB_UNITS(2,600) 몫(예전 1,800). 시작 뒤 3분까지는 체력이 (1 - 지난 시간 / 3분) 아래로 안 내려가고, 8분이 지나면 받는 피해가 30초마다 +50%.
    //  - 공격: 어둠 숨결 6/8/10%(예전 4/5/6%), 반격 0.8%, 붙어서 안 때릴 때 8%.
    //  - 패턴(종류마다 따로 돈다, 체력이 줄수록 잦아짐 -- mineWbZoneTick / mineWbPattern):
    //      0 화염 장판 -- 싸우는 사람 몇 명 발밑 5x5, 3초 예고, 30%
    //      1 심연의 비 -- 거룡 둘레 곳곳 3x3 여러 군데, 2.5초 예고, 20%
    //      2 꼬리 휩쓸기 -- 거룡 둘레 2칸, 1.8초 예고, 25% + 기절 MINE_WB_TAIL_STUN_MS
    //      3 멸망의 숨결(전체 공격기) -- 거룡 둘레 MINE_WB_PAT_R[3]칸 모두, 5초 예고, 55% -- 내성 벽 쪽으로 멀리 피해야 한다
    //    포효 -- MINE_WB_ROAR_GAP_MS마다 + 체력 70%/40%에서, 둘레 14칸 기절 MINE_WB_ROAR_MS.
    static final long MINE_WB_PERIOD_MS = 3_600_000L, MINE_WB_LEN_MS = 5 * 60_000L, /* [2026-11-17] "매시 정각부터 5분 동안만" (10분 -> 5분) */ MINE_WB_NOTICE_MS = 3 * 60_000L, MINE_WB_CHAT_GAP_MS = 3 * 3_600_000L;
    static final double[] MINE_WB_HIT_PCT = { 0.06, 0.08, 0.10 }; // 원거리 한 발 = 최대 체력 %, 체력 70% / 40% 아래로 세진다
    static final long[] MINE_WB_SHOT_MS = { 3_000L, 2_500L, 2_000L };
    static final double MINE_WB_MELEE_PCT = 0.08, MINE_WB_COUNTER_PCT = 0.008; // 붙어 있는데 안 때릴 때의 한 방 8%, 내가 때릴 때마다 돌아오는 반격 0.8%(초당 두 번쯤)
    static final long MINE_WB_ACTIVE_MS = 20_000L, MINE_WB_DOWN_MS = 10_000L; // 체력/몫 규칙은 아래 WbPool 설명([2026-11-16] 주기 동안 이어지는 체력으로 바뀜 -- 3분 바닥선/8분 뒤 증폭/사람 수로 나누기는 없앴다)
    static final int MINE_WB_STAR = 3, MINE_WB_SEVEN = 3, MINE_WB_GALAXY_PCT = 30, MINE_WB_RING_PCT = 5;
    // [2026-11-22] "월드보스 안 쳐도 접속만 하면 보상 -- 쳤을 때 보상, 그냥 처치되면 공통 보상으로 나눠" -- 거룡이 쓰러진 맵 주기 동안 광산에 들어온 모두에게
    //  공통 보상(별빛석 MINE_WB_COM_STAR + 칠요석 MINE_WB_COM_SEVEN, 주기마다 한 번 -- 아이템 WB_COM = 받은 주기 + 1). 싸운 사람(MINE_WB_MIN_SHARE 몫 이상)은 여기에 더해
    //  참여 보상(위 MINE_WB_STAR/SEVEN + 은하수 조각/용의 반지 확률)과 토벌 순위 보상을 그대로 받는다. 쓰러질 때 접속해 있던 사람은 그 자리에서, 아니면 다음에 들어올 때.
    static final int MINE_WB_COM_STAR = 1, MINE_WB_COM_SEVEN = 1;
    static final String MINE_ITEM_WB_COM = "WB_COM", MINE_ITEM_WBC = "WBC_"; // [2026-11-23] WBC_<주기> = 그 주기 공통 보상 받음(게시판)
    static final java.util.Set<String> MINE_WB_COM_GIVEN = java.util.concurrent.ConcurrentHashMap.newKeySet(); // "이름|주기" -- 같은 순간 두 번 주지 않게
    // [2026-11-23] 4~8 = 하늘고래 베히모스(더 넓고 더 아프게): 4 돌풍(싸우는 사람 발밑 7x7) / 5 회오리(둘레 12칸 곳곳 5x5) / 6 물보라 분출(둘레 5칸 -- 맞으면 성벽 너머 중앙 평원으로 날아간다)
    //  / 7 폭풍 낙하(하늘을 나는 동안 싸우는 사람 발밑으로 연달아 내리꽂힘 -- 주기는 MINE_BH_FLY_*) / 8 폭풍 숨결(둘레 15칸 모두, 전체 공격기). 화면 종류 번호는 MINE_WB_ZK(19~23).
    static final long[] MINE_WB_PAT_GAP_MS = { 10_000L, 13_000L, 17_000L, 50_000L, 9_000L, 12_000L, 60_000L, 0L, 46_000L }, MINE_WB_PAT_WARN_MS = { 3_000L, 2_500L, 1_800L, 5_000L, 2_500L, 2_200L, 2_600L, 1_300L, 5_000L },
        MINE_WB_PAT_FIRST_MS = { 8_000L, 14_000L, 20_000L, 35_000L, 7_000L, 12_000L, 40_000L, Long.MAX_VALUE / 8, 32_000L }; // [2026-11-23] 물보라 분출(6) = 등장 40초 뒤부터 1분마다(40초·1분40초·2분40초·3분40초·4분40초)
    static final int[] MINE_WB_PAT_R = { 2, 1, 3, 13, 3, 2, 5, 2, 15 }; // 장판 반경(체비셰프) -- 꼬리 휩쓸기 3 = 거룡(3x3) 가운데에서 둘레 2칸
    static final double[] MINE_WB_PAT_PCT = { 0.30, 0.20, 0.25, 0.55, 0.32, 0.22, 0.18, 0.24, 0.60 };
    static final String[] MINE_WB_PAT_NAME = { "화염", "심연의 비", "꼬리 휩쓸기", "멸망의 숨결", "돌풍", "회오리", "물보라 분출", "폭풍 낙하", "폭풍 숨결" };
    static final int[] MINE_WB_ZK = { 0, 1, 2, 3, 19, 20, 21, 22, 23 }; // 화면 장판 종류(글자/색) 번호
    static final int MINE_WB_PAT_N = MINE_WB_PAT_R.length;
    static final long MINE_WB_ROAR_MS = 2_000L, MINE_WB_ROAR_GAP_MS = 40_000L, MINE_WB_TAIL_STUN_MS = 1_500L;
    static final int MINE_WB_ROAR_RANGE = 14;
    static final String MINE_ITEM_WB_CYCLE = "WB_CYCLE", MINE_WB_NAME = "심연의 거룡";
    // ---- [2026-11-23] "월드보스 처치하면 다음 타임엔 두 번째 월드보스 하늘고래 베히모스 -- 번갈아 등장 말고 1회씩만 처치하면 끝, 베히모스는 비공개로 다음 타임에 갑자기" --
    //  맵 주기(2일) 안에서 거룡이 쓰러지면 풀이 2단계(WbPool.stage = 1)로 넘어가 다음 정각 회차부터 베히모스가 나온다. 예고(띠/채팅 예고/게시판의 다음 출현)는 없고 나타날 때만 알린다.
    //  베히모스도 쓰러지면 그 주기엔 끝, 맵이 초기화되면 다시 거룡부터. 몬스터 칸은 거룡 것을 그대로 쓰고 화면이 wb.bh를 보고 그림/이름을 바꾼다.
    //  체력 = 거룡 몫 x MINE_BH_HP_MUL, 원거리/근접/반격 x MINE_BH_DMG_MUL, 바람 속성(무기 상성). 하늘을 날며(MINE_BH_FLY_*) 폭풍 낙하를 내리꽂고, 물보라 분출에 맞으면 중앙 평원으로 날아간다.
    //  보상(햇빛석만): 체력 10%가 깎일 때마다 그 10% 구간(100->90%, 90->80% ...)에 한 대라도 때린 사람에게 0~1개(MINE_BH_SEG -- 구간을 넘긴 한 방은 두 구간 모두),
    //  쓰러지면 광산에 들어온 적이 있는 모두에게 1개. 구간 기여자는 메모리에만 있어 서버를 다시 켜면 그 구간은 다시 켠 뒤 때린 사람부터.
    static final String MINE_BH_NAME = "하늘고래 베히모스";
    static final double MINE_BH_HP_MUL = 1.2, MINE_BH_DMG_MUL = 1.25;
    static final long MINE_BH_PERIOD_MS = 2 * 3_600_000L; // [2026-11-23] 두 시간에 한 번(KST 짝수 정각)
    static final long MINE_BH_FLY_FIRST_MS = 25_000L, MINE_BH_FLY_GAP_MS = 40_000L, MINE_BH_FLY_MS = 9_000L, MINE_BH_DIVE_MS = 1_400L, MINE_BH_FLY_MOVE_MS = 1_800L, MINE_BH_KNOCK_MS = 6_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, Double> MINE_BH_SHARE = new java.util.concurrent.ConcurrentHashMap<>(); // 베히모스 싸운 몫(화면 표시만 -- 보상은 10%마다 싸우던 사람)
    static final java.util.Set<String> MINE_BH_SEG = java.util.concurrent.ConcurrentHashMap.newKeySet(); // [2026-11-23] 지금 10% 구간에 때린 사람
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_BH_KNOCK = new java.util.concurrent.ConcurrentHashMap<>(); // 물보라 분출에 맞은 사람 -> 맞은 시각(다음 위치 알림에서 중앙 평원으로)
    /** 지금 주기의 월드보스가 베히모스(2단계)인지. */
    static boolean mineWbBh() { return MINE_WB_POOL.stage >= 1; }
    static String mineWbName() { return mineWbBh() ? MINE_BH_NAME : MINE_WB_NAME; }
    /** 이번 주기에 거룡이 쓰러졌는지(베히모스로 넘어갔으면 쓰러진 것). */
    static boolean mineWbDragonDead() { WbPool p = MINE_WB_POOL; return p.stage >= 1 || p.killed; }
    static boolean mineWbFlying(WbState st, long now) { return mineWbBh() && st.flyUntil > now; }
    /** 무기 속성 상성 -- 베히모스일 땐 월드보스 칸을 바람 속성으로 본다. */
    static boolean mineWeakTo(int e, S5MineMobs.Type t) {
        if (mineWbBh() && t == S5MineMobs.TYPES.get(S5MineMobs.WB_BOSS_TYPE)) return S5MineMobs.elemBeats(e, S5MineMobs.EL_WIND);
        return S5MineMobs.weakTo(e, t);
    }
    static final class WbState {
        final long start;
        volatile boolean killed, done;
        volatile int phase, roarSeq, zoneSeq;
        volatile long roarAt, roarNext;
        final long[] patNext = new long[MINE_WB_PAT_N]; // 패턴 종류마다 다음에 쓸 시각
        volatile long flyNext, flyAt, flyUntil, diveNext; // [2026-11-23] 베히모스 -- 하늘을 나는 때
        final java.util.concurrent.ConcurrentHashMap<String, Double> score = new java.util.concurrent.ConcurrentHashMap<>(); // 싸운 몫(피해 / 내 기본 공격력)
        final java.util.concurrent.ConcurrentHashMap<String, Long> lastHit = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Set<String> rooms = java.util.concurrent.ConcurrentHashMap.newKeySet(); // 채팅 알림을 붙인 방
        final List<long[]> zones = new ArrayList<>(); // 장판 {x, y, 터지는 시각, 번호, 반경, 종류}
        volatile int px = -1, py; // [2026-11-16] 거룡이 지금 선 자리(왼쪽 위 칸, -1 = 아직 집 자리)
        volatile long moveNext;
        WbState(long start) { this.start = start; for (int k = 0; k < MINE_WB_PAT_N; k++) patNext[k] = start + MINE_WB_PAT_FIRST_MS[k]; roarNext = start + MINE_WB_ROAR_GAP_MS; flyNext = start + MINE_BH_FLY_FIRST_MS; }
    }
    static volatile WbState MINE_WB = new WbState(0);
    // [2026-11-16] "거룡이 움직이면 좋겠어" -- 내성 안에서 MINE_WB_MOVE_GAP_MS마다(체력이 줄수록 잦게) 날아올라 MINE_WB_MOVE_MIN~MAX칸 떨어진 곳에 내려앉는다.
    //  꼬리 휩쓸기/멸망의 숨결을 예고하는 동안과 기절해 있는 동안은 그 자리에 있다. 자리는 WbState.px/py(몬스터 상태 fx/fy에도 매초 적는다 -- mineWbMove).
    static final long MINE_WB_MOVE_GAP_MS = 30_000L, MINE_WB_MOVE_FIRST_MS = 18_000L;
    static final int MINE_WB_MOVE_MIN = 5, MINE_WB_MOVE_MAX = 10;
    /** 거룡이 지금 선 자리(3x3의 왼쪽 위 칸). */
    static int[] mineWbPos() {
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
        WbState st = MINE_WB;
        if (bm == null) return new int[] { 0, 0 };
        return st.px < 0 ? new int[] { bm.x, bm.y } : new int[] { st.px, st.py };
    }
    /** 때가 되면 자리를 옮기고, 지금 자리를 몬스터 상태에 적는다(매초 위치 알림에서 -- mineWbSync). */
    void mineWbMove(WbState st, long now) {
        if (st.start <= 0 || st.killed || st.done || now < st.start) return;
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
        MobState bs = bm == null ? null : MINE_MOB_STATE.get(mineMobKey(a, bm.id));
        if (bs == null) return;
        if (st.px < 0) { st.px = bm.x; st.py = bm.y; st.moveNext = st.start + MINE_WB_MOVE_FIRST_MS; }
        if (now >= st.moveNext) synchronized (st.zones) {
            if (now >= st.moveNext) {
                boolean hold = bs.stunUntil > now;
                for (long[] z : st.zones) if ((z[5] == 2 || z[5] == 3 || z[5] == 6 || z[5] == 8) && z[2] > now - 500L) hold = true; // [2026-11-23] 베히모스 물보라 분출/폭풍 숨결도
                if (hold) st.moveNext = now + 3_000L;
                else {
                    int[] k = S5MineMap.CASTLE_KEEP;
                    for (int t = 0; t < 60; t++) {
                        int nx = st.px - MINE_WB_MOVE_MAX + RND.nextInt(MINE_WB_MOVE_MAX * 2 + 1), ny = st.py - MINE_WB_MOVE_MAX + RND.nextInt(MINE_WB_MOVE_MAX * 2 + 1);
                        int d = Math.max(Math.abs(nx - st.px), Math.abs(ny - st.py));
                        if (d < MINE_WB_MOVE_MIN || nx < k[0] + 3 || ny < k[1] + 3 || nx + 2 > k[2] - 3 || ny + 2 > k[3] - 3) continue;
                        if (Math.max(Math.abs(nx - bm.x), Math.abs(ny - bm.y)) > 14) continue; // 집 자리에서 너무 멀리는 안 간다
                        boolean ok = true;
                        for (int yy = ny; yy < ny + 3 && ok; yy++) for (int xx = nx; xx < nx + 3; xx++) if (!a.walkable(xx, yy)) { ok = false; break; }
                        if (!ok) continue;
                        st.px = nx; st.py = ny;
                        break;
                    }
                    st.moveNext = mineWbFlying(st, now) ? now + MINE_BH_FLY_MOVE_MS : now + MINE_WB_MOVE_GAP_MS - mineWbStage() * 5_000L; // [2026-11-23] 베히모스는 하늘을 나는 동안 자주 옮겨 다닌다
                }
            }
        }
        synchronized (bs) { if (bs.deadUntil <= now) { bs.fx = st.px; bs.fy = st.py; bs.frozenUntil = Math.max(bs.frozenUntil, st.start + MINE_WB_LEN_MS + 5_000L); } }
    }
    static volatile long mineWbForce = 0; // 관리자 시험 -- 이 시각에 바로 시작
    static final java.util.concurrent.ConcurrentHashMap<String, Integer> MINE_WB_ATK = new java.util.concurrent.ConcurrentHashMap<>(); // 몫 기준 공격력(레벨·환생)
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_WB_DOWN = new java.util.concurrent.ConcurrentHashMap<>(); // 쓰러져 있는 사람 -> 일어나는 시각
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_WB_SEEN = new java.util.concurrent.ConcurrentHashMap<>(); // {본 장판 번호, 본 포효 번호}
    /** 월드보스에 쓰러져 있으면 일어나는 시각, 아니면 0. */
    static long mineWbDownUntil(String userName, long now) { Long u = userName == null ? null : MINE_WB_DOWN.get(userName); return u != null && u > now ? u : 0; }
    /** 지금 월드보스 싸움터(진행 중 + 동쪽 성곽 내성 안)에 있는지 -- 여기서 쓰러지면 그 자리에서 10초 뒤 일어나고 최대 체력 잠금이 없다. */
    boolean mineWbZone(String userName, long now) {
        MinePresence p = userName == null ? null : MINE_PRESENCE.get(userName);
        return p != null && mineWbOk(userName) && S5MineMap.CASTLE.equals(p.mapId) && mineWbOn(now) && S5MineMap.inRect(S5MineMap.CASTLE_KEEP, p.x, p.y);
    }
    /** 지금(또는 곧) 회차의 시작 시각 -- 관리자 시험 회차가 살아 있으면 그것, 아니면 오늘 21시(21시 30분이 지났으면 내일 21시). */
    static long mineWbStart(long now) {
        if (mineWbForce > 0 && now < mineWbForce + MINE_WB_LEN_MS) return mineWbForce;
        if (!MINE_WB_OPEN) return Long.MAX_VALUE / 4; // [2026-11-15] 일반 공개 전 -- 정해진 시각엔 안 나온다(관리자 시험 회차만)
        if (mineWbBh()) { // [2026-11-23] "베히모스는 두 시간에 한 번씩" -- KST 짝수 정각(0·2·…·22시)
            long kst = 9 * 3_600_000L, b = (now + kst) / MINE_BH_PERIOD_MS * MINE_BH_PERIOD_MS - kst;
            return now >= b + MINE_WB_LEN_MS ? b + MINE_BH_PERIOD_MS : b;
        }
        long s = now / MINE_WB_PERIOD_MS * MINE_WB_PERIOD_MS; // [2026-11-15] 매시 정각(KST는 UTC와 시간 단위로만 달라 정각이 같다)
        return now >= s + MINE_WB_LEN_MS ? s + MINE_WB_PERIOD_MS : s;
    }
    // ---- [2026-11-16] "체력은 맵 초기화마다 초기화되는 거 맞지?" -- 거룡의 체력은 회차(매시 정각 10분)마다 다시 차지 않고 맵 초기화 주기(2일) 동안 이어진다.
    //  회차가 끝나면 날아가고(깎인 체력 그대로), 다음 정각에 그 체력으로 다시 온다. 쓰러지면 그 주기엔 다시 안 나온다(다음 맵 초기화에 가득 차서).
    //  체력 = "몫"의 합: 피해 / 내 기본 공격력(레벨·환생)이 한 몫이고 MINE_WB_TOTAL몫이 모이면 쓰러진다(사람 수로 나누지 않는다 -- 여럿이 치면 그만큼 빨리, 다른 시간대에 친 것도 다 쌓인다).
    //  보상은 그 주기에 MINE_WB_MIN_SHARE몫 이상 싸운 모두(쓰러질 때 접속해 있지 않아도 -- 다음에 광산에 들어올 때 받는다, mineWbClaim), 주기마다 한 번.
    //  남은 체력은 설정(MINE_WB_POOL = "주기|체력|쓰러짐")에, 사람마다 싸운 몫은 아이템(WB_SHARE / WB_SHARE_CYC = 주기 + 1)에 가끔 적어 서버를 다시 켜도 이어진다.
    //  일반 공개 전(MINE_WB_OPEN = false) 관리자 시험 회차는 따로: 시작할 때마다 가득 찬 작은 체력(MINE_WB_TEST_TOTAL몫)으로, 저장하지 않는다.
    static final class WbPool { int cycle = Integer.MIN_VALUE, hp = 1; boolean killed, test; long savedAt, killedAt; int stage, mile; long dKilledAt; } // [2026-11-17] killedAt -- 게시판, [2026-11-23] stage 1 = 베히모스(mile = 보상 준 10% 수, dKilledAt = 거룡이 쓰러진 시각)
    static final WbPool MINE_WB_POOL = new WbPool();
    static final String MINE_WB_POOL_KEY = "MINE_WB_POOL", MINE_ITEM_WB_SHARE = "WB_SHARE", MINE_ITEM_WB_SHARE_CYC = "WB_SHARE_CYC";
    // [2026-11-23] "막타를 쳤는데 몫이 0은 말이 안 돼 -- 기여도가 있으면 보상" -- 보상 기준 몫 100 -> 조금이라도 때렸으면(0.001), 저장할 때 0보다 크면 최소 1(반올림으로 0이 되던 것)
    static final double MINE_WB_TOTAL = 60_000, MINE_WB_TEST_TOTAL = 2_600, MINE_WB_MIN_SHARE = 0.001, MINE_WB_TEST_MIN_SHARE = 0.001;
    static int mineWbShareInt(double share) { return share <= 0 ? 0 : (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(share))); }
    static final java.util.concurrent.ConcurrentHashMap<String, Double> MINE_WB_SHARE = new java.util.concurrent.ConcurrentHashMap<>(); // 이번 주기에 싸운 몫
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_WB_SHARE_SAVED = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile long mineWbGoneAt = 0; // 거룡이 (못 잡은 채) 날아간 시각 -- 화면의 퇴장 연출
    // ---- [2026-11-17] "마을에 월드보스 게시판 NPC -- 이전 보스, 현재 보스(현재 맵 주기), 다음 출현 시간, 누가 얼마씩 데미지 기여도, 언제 처치, 누가 무슨 템 나왔는지" ----
    //  주기별 기록: 사람마다 WBS_<주기>(몫) / WBR_<주기>(받은 보상), 주기 결과는 설정 MINE_WB_C<주기>(체력|최대|처치|처치 시각). 게시판은 20초 모아 보여 준다.
    static final String MINE_ITEM_WBS = "WBS_", MINE_ITEM_WBR = "WBR_", MINE_WB_CYC_KEY = "MINE_WB_C";
    static volatile long MINE_WB_BOARD_AT = 0;
    static volatile HashMap<String, Object> MINE_WB_BOARD = null;
    HashMap<String, Object> mineWbBoard(long now) {
        if (MINE_WB_BOARD != null && now - MINE_WB_BOARD_AT < 20_000L) { HashMap<String, Object> o = new HashMap<>(MINE_WB_BOARD); o.put("live", mineWbBoardLive(now)); return o; }
        mineWbPool(now);
        int cur = S5MineMap.cycleAt(now), max = mineWbMax();
        HashMap<String, Object> out = new HashMap<>();
        List<String> codes = java.util.Arrays.asList(MINE_ITEM_WBS + cur, MINE_ITEM_WBR + cur, MINE_ITEM_WBS + (cur - 1), MINE_ITEM_WBR + (cur - 1), MINE_ITEM_WB_SHARE, MINE_ITEM_WB_SHARE_CYC, MINE_ITEM_WB_CYCLE,
            MINE_ITEM_WBC + cur, MINE_ITEM_WBC + (cur - 1), MINE_ITEM_WB_COM); // [2026-11-23] 공통 보상
        List<HashMap<String, Object>> rows;
        try { rows = dao.selectMineItemsByCodes(codes); } catch (Exception e) { rows = new ArrayList<>(); }
        for (int k = 0; k < 2; k++) {
            int c = cur - k;
            Map<String, Double> sh = new HashMap<>();
            Map<String, Integer> rw = new HashMap<>();
            Map<String, Integer> oldSh = new HashMap<>(), oldCyc = new HashMap<>(), oldRw = new HashMap<>();
            java.util.Set<String> com = new java.util.TreeSet<>(); // [2026-11-23] 공통 보상 받은 사람
            for (HashMap<String, Object> r : rows) {
                String u = strVal(r.get("USER_NAME"), ""), code = strVal(r.get("ITEM_CODE"), "");
                int v = intVal(r.get("ITEM_LEVEL"), 0);
                if ((MINE_ITEM_WBS + c).equals(code)) sh.put(u, (double) v);
                else if ((MINE_ITEM_WBR + c).equals(code)) rw.put(u, v);
                else if (MINE_ITEM_WB_SHARE.equals(code)) oldSh.put(u, v);
                else if (MINE_ITEM_WB_SHARE_CYC.equals(code)) oldCyc.put(u, v);
                else if (MINE_ITEM_WB_CYCLE.equals(code)) oldRw.put(u, v);
                else if ((MINE_ITEM_WBC + c).equals(code) || (MINE_ITEM_WB_COM.equals(code) && v == c + 1)) com.add(mineNick(u)); // WB_COM은 이 기록 전에 받은 사람(마지막 주기 하나)
            }
            for (Map.Entry<String, Integer> e : oldCyc.entrySet()) if (e.getValue() == c + 1 && !sh.containsKey(e.getKey())) sh.put(e.getKey(), (double) oldSh.getOrDefault(e.getKey(), 0)); // 게시판 전 기록(사람마다 마지막 주기 몫 하나)
            for (Map.Entry<String, Integer> e : oldRw.entrySet()) if (e.getValue() == c + 1 && !rw.containsKey(e.getKey())) rw.put(e.getKey(), -1); // 보상은 받았는데 무엇이 나왔는지는 기록 전
            HashMap<String, Object> b = new HashMap<>();
            b.put("cycle", c);
            b.put("from", S5MineMap.RESET_ANCHOR_MS + (long) (c - 1) * S5MineMap.CYCLE_MS);
            if (k == 0) {
                for (Map.Entry<String, Double> e : MINE_WB_SHARE.entrySet()) if (!MINE_WB_POOL.test) sh.merge(e.getKey(), e.getValue(), Math::max); // 메모리(지금 싸우는 몫)가 더 새것
                if (MINE_WB_POOL.stage >= 1) { b.put("hp", 0); b.put("max", max); b.put("killed", true); b.put("killedAt", MINE_WB_POOL.dKilledAt); } // [2026-11-23] 베히모스는 비공개 -- 게시판엔 거룡이 쓰러진 것만
                else { b.put("hp", Math.max(0, MINE_WB_POOL.hp)); b.put("max", max); b.put("killed", MINE_WB_POOL.killed); b.put("killedAt", MINE_WB_POOL.killedAt); }
            } else {
                String v = null;
                try { v = dao.selectConfigValue(MINE_WB_CYC_KEY + c); } catch (Exception ignore) { }
                String[] a = v == null ? new String[0] : v.trim().split("\\|");
                if (a.length >= 4) { b.put("hp", Integer.parseInt(a[0])); b.put("max", Integer.parseInt(a[1])); b.put("killed", "1".equals(a[2])); b.put("killedAt", Long.parseLong(a[3])); }
                else { b.put("hp", -1); b.put("max", max); b.put("killed", false); b.put("killedAt", 0L); }
            }
            double tot = 0;
            for (double d : sh.values()) tot += d;
            int bmax = intVal(b.get("max"), max);
            List<HashMap<String, Object>> ps = new ArrayList<>();
            for (Map.Entry<String, Double> e : sh.entrySet()) {
                if (e.getValue() <= 0) continue;
                HashMap<String, Object> p = new HashMap<>();
                p.put("nick", mineNick(e.getKey()));
                p.put("share", (long) mineWbShareInt(e.getValue()));
                p.put("pct", tot > 0 ? Math.round(e.getValue() * 1000 / tot) / 10.0 : 0);
                p.put("dmg", Math.round(e.getValue() * bmax / MINE_WB_TOTAL)); // 몫 하나 = 최대 체력 / MINE_WB_TOTAL
                p.put("ok", e.getValue() >= MINE_WB_MIN_SHARE);
                Integer r = rw.get(e.getKey());
                if (r != null && r != 0) p.put("rw", r);
                ps.add(p);
            }
            ps.sort((p1, p2) -> Long.compare((Long) p2.get("share"), (Long) p1.get("share")));
            b.put("players", ps);
            b.put("com", new ArrayList<>(com));
            b.put("minShare", MINE_WB_MIN_SHARE);
            out.put(k == 0 ? "cur" : "prev", b);
        }
        out.put("star", MINE_WB_STAR); out.put("seven", MINE_WB_SEVEN); out.put("open", MINE_WB_OPEN);
        out.put("comStar", MINE_WB_COM_STAR); out.put("comSeven", MINE_WB_COM_SEVEN); // [2026-11-23]
        MINE_WB_BOARD = out; MINE_WB_BOARD_AT = now;
        HashMap<String, Object> o = new HashMap<>(out);
        o.put("live", mineWbBoardLive(now));
        return o;
    }
    /** 지금 상태(게시판 맨 위) -- on: 진행 중(끝나는 시각), next: 다음 출현 시각(이번 주기에 쓰러졌으면 다음 맵 주기 첫 회차). */
    HashMap<String, Object> mineWbBoardLive(long now) {
        HashMap<String, Object> l = new HashMap<>();
        if (!MINE_WB_OPEN) { l.put("closed", true); return l; }
        if (mineWbOn(now)) { l.put("on", true); l.put("end", mineWbStart(now) + MINE_WB_LEN_MS); l.put("name", mineWbName()); if (mineWbBh()) l.put("bh", true); return l; } // [2026-11-23] 베히모스는 나와 있을 때만 보인다
        int cur = S5MineMap.cycleAt(now);
        long next = mineWbStart(now);
        if (mineWbDragonDead() && MINE_WB_POOL.cycle == cur) { long ns = S5MineMap.RESET_ANCHOR_MS + (long) cur * S5MineMap.CYCLE_MS; next = (ns + MINE_WB_PERIOD_MS - 1) / MINE_WB_PERIOD_MS * MINE_WB_PERIOD_MS; l.put("nextCycle", true); }
        l.put("next", next);
        return l;
    }
    static double mineWbMinShare() { return MINE_WB_POOL.test ? MINE_WB_TEST_MIN_SHARE : MINE_WB_MIN_SHARE; }
    /** 이번 주기의 거룡 체력을 준비한다 -- 주기가 바뀌었으면 가득, 서버가 막 켜졌으면 저장된 값에서. */
    void mineWbPool(long now) {
        int cyc = S5MineMap.cycleAt(now);
        WbPool p = MINE_WB_POOL;
        if (p.cycle == cyc && p.test == !MINE_WB_OPEN) return;
        synchronized (p) {
            if (p.cycle == cyc && p.test == !MINE_WB_OPEN) return;
            boolean fresh = p.cycle == Integer.MIN_VALUE || p.test != !MINE_WB_OPEN; // 서버가 막 켜졌거나 공개 여부가 바뀜
            int max = mineWbMax();
            p.test = !MINE_WB_OPEN;
            p.hp = max;
            p.killed = false;
            p.killedAt = 0;
            p.stage = 0; p.mile = 0; p.dKilledAt = 0; // [2026-11-23]
            MINE_BH_SHARE.clear();
            MINE_BH_SEG.clear();
            MINE_WB_SHARE.clear();
            MINE_WB_SHARE_SAVED.clear();
            if (!p.test && fresh) {
                try {
                    String v = dao.selectConfigValue(MINE_WB_POOL_KEY);
                    String[] a = v == null ? new String[0] : v.trim().split("\\|");
                    if (a.length >= 3 && Integer.parseInt(a[0]) == cyc) { p.hp = Math.max(0, Math.min(max, Integer.parseInt(a[1]))); p.killed = "1".equals(a[2]); if (a.length >= 4) p.killedAt = Long.parseLong(a[3]); if (a.length >= 7) { p.stage = Integer.parseInt(a[4]); p.mile = Integer.parseInt(a[5]); p.dKilledAt = Long.parseLong(a[6]); } }
                } catch (Exception e) { System.out.println("[광산] 월드보스 체력 읽기 실패: " + e.getMessage()); }
            }
            p.cycle = cyc;
            // [2026-11-23] "이번 주기도 베히모스 단계로" -- 베히모스 전 코드가 저장한 값(4칸)인데 이번 주기 거룡이 이미 쓰러졌으면, 다음 회차부터 베히모스(가득 찬 체력)
            if (!p.test && fresh && p.killed && p.stage == 0 && p.dKilledAt == 0) {
                try {
                    String v0 = dao.selectConfigValue(MINE_WB_POOL_KEY);
                    if (v0 != null && v0.trim().split("\\|").length < 7) { p.stage = 1; p.dKilledAt = p.killedAt; p.killed = false; p.killedAt = 0; p.hp = max; p.mile = 0; mineWbPoolSave(now); System.out.println("[광산] 이번 주기 거룡은 이미 쓰러짐 -- 베히모스 단계로"); }
                } catch (Exception e) { System.out.println("[광산] 베히모스 단계 넘기기 실패: " + e.getMessage()); }
            }
            if (!p.test && !fresh) mineWbPoolSave(now); // 맵 초기화 -- 가득 찬 체력으로
        }
    }
    void mineWbPoolSave(long now) {
        WbPool p = MINE_WB_POOL;
        if (p.test || p.cycle == Integer.MIN_VALUE) return;
        p.savedAt = now;
        try {
            dao.upsertConfigValue(MINE_WB_POOL_KEY, p.cycle + "|" + Math.max(0, p.hp) + "|" + (p.killed ? 1 : 0) + "|" + p.killedAt + "|" + p.stage + "|" + p.mile + "|" + p.dKilledAt, "world boss hp for this map cycle: cycle|hp|killed|killedAt|stage|mile|dragonKilledAt (set by app)");
            if (p.stage >= 1) dao.upsertConfigValue(MINE_WB_CYC_KEY + p.cycle, "0|" + mineWbMax() + "|1|" + p.dKilledAt, "world boss result of one map cycle: hp|max|killed|killedAt (board)"); // [2026-11-23] 게시판은 거룡 기록만(베히모스는 비공개)
            else dao.upsertConfigValue(MINE_WB_CYC_KEY + p.cycle, Math.max(0, p.hp) + "|" + mineWbMax() + "|" + (p.killed ? 1 : 0) + "|" + p.killedAt, "world boss result of one map cycle: hp|max|killed|killedAt (board)"); // [2026-11-17] 게시판(지난 주기)
        }
        catch (Exception e) { System.out.println("[광산] 월드보스 체력 저장 실패: " + e.getMessage()); }
    }
    void mineWbShareSave(String userName, double share, long now) {
        WbPool p = MINE_WB_POOL;
        if (p.test || p.stage >= 1) return; // [2026-11-23] 베히모스 몫은 저장하지 않는다(거룡 몫 기록을 덮지 않게)
        MINE_WB_SHARE_SAVED.put(userName, now);
        try {
            dao.upsertMineItem(userName, MINE_ITEM_WB_SHARE, mineWbShareInt(share));
            dao.upsertMineItem(userName, MINE_ITEM_WB_SHARE_CYC, p.cycle + 1);
            dao.upsertMineItem(userName, MINE_ITEM_WBS + p.cycle, mineWbShareInt(share)); // [2026-11-17] 게시판 -- 주기별 몫(지난 주기도 남는다)
        } catch (Exception e) { /* 다음 저장 때 */ }
    }
    static boolean mineWbOn(long now) {
        long s = mineWbStart(now);
        WbState st = MINE_WB;
        WbPool p = MINE_WB_POOL;
        if (p.killed && p.cycle == S5MineMap.cycleAt(now)) return false; // 이번 주기엔 이미 쓰러졌다
        return now >= s && now < s + MINE_WB_LEN_MS && !(st.start == s && (st.killed || st.done));
    }
    /** 체력 70% / 40% 아래로 세진다(주기 동안 이어지는 체력 기준). */
    static int mineWbStage() { int max = mineWbMax(), p = (int) ((max - (long) Math.min(max, MINE_WB_POOL.hp)) * 10L / Math.max(1, max)); return p >= 6 ? 2 : p >= 3 ? 1 : 0; }
    /** 회차가 바뀌면 새 상태 + 거룡을 지금까지 깎인 체력으로 세운다, 시간이 다 된 회차는 끝(날아감). */
    void mineWbSync(long now) {
        mineWbPool(now);
        WbState st = MINE_WB;
        if (st.start > 0 && !st.done && !st.killed && now >= st.start + MINE_WB_LEN_MS) mineWbTimeout(st, now);
        long s = mineWbStart(now);
        if (st.start == s && !st.done && !st.killed) mineWbMove(st, now); // [2026-11-16] 거룡 자리 옮기기
        if (st.start == s && !MINE_WB_POOL.test) { // [2026-11-18] "거룡 피가 왜 100%야" -- 몬스터 상태가 가득 찬 체력으로 새로 생기면(풀 94%인데) 띠/체력이 100%로 보였다 -- 풀보다 높으면 풀로
            S5MineMap ca = S5MineMap.get(S5MineMap.CASTLE);
            S5MineMap.Mob cbm = ca == null ? null : mineBossOf(ca);
            MobState cbs = cbm == null ? null : MINE_MOB_STATE.get(mineMobKey(ca, cbm.id));
            if (cbs != null) synchronized (cbs) { if (cbs.deadUntil <= now && cbs.hp > MINE_WB_POOL.hp) cbs.hp = Math.max(1, MINE_WB_POOL.hp); }
        }
        if (st.start == s || now < s || MINE_WB_POOL.killed) return;
        synchronized (MINE_WB_ATK) {
            if (MINE_WB.start == s) return;
            MINE_WB = new WbState(s);
            MINE_WB_ATK.clear();
            MINE_WB_DOWN.clear();
            MINE_WB_SEEN.clear();
            S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
            S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
            if (bm != null) {
                MobState bs = new MobState(S5MineMobs.hp(S5MineMobs.TYPES.get(bm.type), bm.rank));
                bs.hp = Math.max(1, MINE_WB_POOL.hp); // 지난 회차까지 깎인 체력 그대로
                bs.fx = bm.x; bs.fy = bm.y; bs.regenAt = now; bs.nearAt = now;
                MINE_MOB_STATE.put(mineMobKey(a, bm.id), bs);
            }
        }
    }
    static int mineWbMax() {
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
        return bm == null ? 1 : S5MineMobs.hp(S5MineMobs.TYPES.get(bm.type), bm.rank);
    }
    /** 거룡이 맞았다 -- 맞기 전 체력 hpBefore, 원래 피해 dealt. 몫으로 바꿔 깎은 뒤의 체력을 돌려준다(몬스터 잠금 안에서 부른다). canKill = 이 공격으로 쓰러뜨려도 되는지(검기는 1에서 멈춤). */
    // [2026-11-17] "참여 인원이 많을 때 플레이어들에게 공격력 버프 -- 2명 +20%, 3명 +50%, 4명 +100%, 5명 이상 +150%" --
    //  지금 싸우는 사람(최근 MINE_WB_ACTIVE_MS 안에 때린 사람) 수로, 거룡에게 들어가는 피해(= 몫)에 곱한다. 화면 띠에 표시(mineWbInfo buff).
    static double mineWbPartyMul(int n) { return n >= 5 ? 2.5 : n == 4 ? 2.0 : n == 3 ? 1.5 : n == 2 ? 1.2 : 1.0; }
    static int mineWbActive(WbState st, long now) { int act = 0; for (Long t : st.lastHit.values()) if (now - t <= MINE_WB_ACTIVE_MS) act++; return act; }
    int mineWbApply(String userName, int hpBefore, int dealt, long now, boolean canKill) {
        WbState st = MINE_WB;
        WbPool p = MINE_WB_POOL;
        if (userName == null || st.killed || st.done || dealt <= 0 || st.start <= 0 || p.killed || !mineWbOk(userName)) return hpBefore;
        int atk = Math.max(1, MINE_WB_ATK.computeIfAbsent(userName, u -> { // 참여 버튼 없이 걸어온 사람 -- 처음 때릴 때 한 번
            try { HashMap<String, Object> r = dao.selectMineUser(u); return Math.max(1, mineAtk(mineLv(r), mineRebirth(mineItems(u)))); } catch (Exception e) { return 10_000; }
        }));
        st.lastHit.put(userName, now);
        if (!p.test) hpBefore = Math.min(hpBefore, Math.max(1, p.hp)); // [2026-11-18] 몬스터 상태가 풀보다 높아도 풀에서 깎는다(가득 찬 상태로 새로 생겨 풀이 되돌아가지 않게)
        double units = dealt / (double) atk * mineWbPartyMul(mineWbActive(st, now)); // [2026-11-17] 인원 버프
        if (!p.test && p.stage < 1 && !MINE_WB_SHARE.containsKey(userName)) { // 이번 주기에 앞서 싸운 몫(서버를 다시 켠 뒤 처음) -- 저장된 값에서 이어서
            try { Map<String, Integer> it = mineItems(userName); if (it.getOrDefault(MINE_ITEM_WB_SHARE_CYC, 0) == p.cycle + 1) MINE_WB_SHARE.putIfAbsent(userName, (double) it.getOrDefault(MINE_ITEM_WB_SHARE, 0)); } catch (Exception e) { /* 0부터 */ }
        }
        boolean bh = p.stage >= 1; // [2026-11-23] 베히모스 -- 몫은 화면 표시만(따로), 체력은 x MINE_BH_HP_MUL
        double mine = (bh ? MINE_BH_SHARE : MINE_WB_SHARE).merge(userName, units, Double::sum);
        st.lastHit.put(userName, now);
        int max = mineWbMax();
        long after = Math.round(hpBefore - max * units / ((p.test ? MINE_WB_TEST_TOTAL : MINE_WB_TOTAL) * (bh ? MINE_BH_HP_MUL : 1)));
        if (!canKill) after = Math.max(1, after);
        after = Math.min(hpBefore, Math.max(0, after));
        int ph = (int) Math.min(10, (max - after) * 10L / Math.max(1, max));
        synchronized (p) {
            p.hp = (int) after;
            if (ph > st.phase) {
                if ((st.phase < 3 && ph >= 3) || (st.phase < 6 && ph >= 6)) { st.roarAt = now; st.roarSeq++; } // 체력 70% / 40% -- 포효
                st.phase = ph;
            }
            if (bh) MINE_BH_SEG.add(userName); // [2026-11-23] 이 10% 구간에 때렸다
            if (bh && after > 0) { // [2026-11-23] 베히모스 -- 체력 10%가 깎일 때마다 그 구간에 때린 사람에게 햇빛석 0~1개
                int ml = (int) Math.min(9, (max - after) * 10L / Math.max(1, max));
                if (ml > p.mile) {
                    int nm = ml - p.mile; p.mile = ml; mineWbPoolSave(now);
                    List<String> seg = new ArrayList<>(MINE_BH_SEG); MINE_BH_SEG.clear(); MINE_BH_SEG.add(userName); // 구간을 넘긴 한 방은 다음 구간에도
                    mineBhMile(seg, nm, ml, now);
                }
            }
            if (after <= 0 && !p.killed) {
                if (bh) { int nm = Math.max(0, 10 - p.mile); p.killed = true; p.killedAt = now; p.mile = 10; st.killed = true; mineWbPoolSave(now); List<String> seg = new ArrayList<>(MINE_BH_SEG); MINE_BH_SEG.clear(); if (nm > 0) mineBhMile(seg, nm, 10, now); mineBhFinish(now); }
                else {
                    p.killed = true; p.killedAt = now; st.killed = true; mineWbPoolSave(now); mineWbFinish(now);
                    // [2026-11-23] 거룡이 쓰러졌다 -- 이번 주기 다음 회차부터 베히모스(가득 찬 체력, 예고 없이)
                    p.stage = 1; p.dKilledAt = now; p.killed = false; p.killedAt = 0; p.hp = max; p.mile = 0;
                    MINE_BH_SHARE.clear(); MINE_BH_SEG.clear();
                    mineWbPoolSave(now);
                }
            }
            else if (now - p.savedAt > 10_000L) mineWbPoolSave(now);
        }
        Long sv = MINE_WB_SHARE_SAVED.get(userName);
        if (!p.test && !bh && (sv == null || now - sv > 15_000L)) mineWbShareSave(userName, mine, now);
        return (int) after;
    }
    /** 한 사람 보상 -- 몫이 모자라거나 이번 주기에 이미 받았으면 그 안내만. 은하수 조각/용의 반지는 사람마다 따로 굴린다. */
    void mineWbReward(String u, double share, long now) {
        int cyc = S5MineMap.cycleAt(now) + 1;
        try {
            Map<String, Integer> it = mineItems(u);
            if (share < mineWbMinShare()) { MINE_SHRINE_REWARD_MSG.merge(u, "🐉 " + MINE_WB_NAME + "이(가) 쓰러졌어요! (조금 더 오래 함께 싸워야 보상을 받아요)", (p, q) -> p + " / " + q); return; }
            if (it.getOrDefault(MINE_ITEM_WB_CYCLE, 0) >= cyc && !isMineAdmin(u)) { MINE_SHRINE_REWARD_MSG.merge(u, "🐉 " + MINE_WB_NAME + "을(를) 함께 쓰러뜨렸어요! (이번 맵 주기 보상은 이미 받았어요 -- 맵 초기화마다 한 번)", (p, q) -> p + " / " + q); return; }
            dao.upsertMineItem(u, MINE_ITEM_WB_CYCLE, cyc);
            StringBuilder m = new StringBuilder("⚔️ ").append(MINE_WB_NAME).append(" 토벌 참여 보상! ⭐ 별빛석 ").append(MINE_WB_STAR).append(" · 칠요석 ").append(MINE_WB_SEVEN); // [2026-11-22] 공통 보상과 나눔
            dao.addUserOre(u, S5MineMap.ORE_CODES[5], MINE_WB_STAR);
            dao.addUserOre(u, S5MineMap.SEVEN_STONE_CODE, MINE_WB_SEVEN);
            int gal = RND.nextInt(100) < MINE_WB_GALAXY_PCT ? 1 : 0;
            if (RND.nextInt(100) < MINE_WB_RING_PCT) {
                if (it.getOrDefault(MINE_ITEM_WYRM_RING, 0) < 1) {
                    dao.upsertMineItem(u, MINE_ITEM_WYRM_RING, 1);
                    m.append(" · 💍 용의 반지 Lv.1 획득!! (인벤토리에서 끼면 전체 지도에서 가고 싶은 곳으로 한 시간에 한 번)");
                    mineGossipAdd(mineNick(u) + " 님이 " + MINE_WB_NAME + "에게서 용의 반지를 얻었대요!");
                } else gal++; // 이미 가진 사람은 은하수 조각으로
            }
            if (gal > 0) { dao.addUserOre(u, S5MineMap.GALAXY_CODE, gal); m.append(" · 🌌 ").append(S5MineMap.GALAXY_NAME).append(' ').append(gal); }
            try { dao.upsertMineItem(u, MINE_ITEM_WBR + (cyc - 1), 1 + 10 * gal + (m.indexOf("용의 반지") >= 0 ? 100 : 0)); } catch (Exception ignore) { } // [2026-11-17] 게시판 -- 받은 보상(1 = 별빛석·칠요석, +10 x 은하수 조각, +100 = 용의 반지)
            MINE_WB_BOARD_AT = 0;
            MINE_SHRINE_REWARD_MSG.merge(u, m.toString(), (p, q) -> p + " / " + q);
        } catch (Exception e) { /* 한 사람 실패해도 나머지는 */ }
    }
    /** [2026-11-22] 공통 보상 -- 거룡이 쓰러진 이번 맵 주기에 광산에 들어온 사람이면 싸우지 않았어도 한 번. */
    void mineWbCommon(String u, long now) {
        WbPool p = MINE_WB_POOL;
        if (u == null || !mineWbDragonDead() || p.cycle != S5MineMap.cycleAt(now)) return; // [2026-11-23] 베히모스로 넘어가도 거룡 공통 보상은 그대로
        if (p.test && !isMineAdmin(u)) return; // 관리자 시험 회차는 관리자만
        int cyc = p.cycle + 1;
        if (!MINE_WB_COM_GIVEN.add(u + "|" + cyc)) return;
        try {
            if (mineItems(u).getOrDefault(MINE_ITEM_WB_COM, 0) >= cyc && !p.test) return;
            dao.upsertMineItem(u, MINE_ITEM_WB_COM, cyc);
            try { dao.upsertMineItem(u, MINE_ITEM_WBC + p.cycle, 1); } catch (Exception ignore) { } // [2026-11-23] 게시판 -- 이 주기에 공통 보상을 받았다
            dao.addUserOre(u, S5MineMap.ORE_CODES[5], MINE_WB_COM_STAR);
            dao.addUserOre(u, S5MineMap.SEVEN_STONE_CODE, MINE_WB_COM_SEVEN);
            MINE_SHRINE_REWARD_MSG.merge(u, "🎁 " + MINE_WB_NAME + "이(가) 쓰러졌어요! 공통 보상 -- ⭐ 별빛석 " + MINE_WB_COM_STAR + " · 칠요석 " + MINE_WB_COM_SEVEN, (a, b) -> b + " / " + a);
        } catch (Exception e) { MINE_WB_COM_GIVEN.remove(u + "|" + cyc); }
    }
    /** 처치 -- 이번 주기에 싸운 모두(지금 메모리에 있는 사람)에게 보상. 서버를 다시 켜기 전에 싸우고 지금 없는 사람은 다음에 들어올 때(mineWbClaim). */
    void mineWbFinish(long now) {
        int n = 0;
        // [2026-11-20] "거룡의 심장 조각, 로스터리는 왜 없지?" -- 몫이 1위(41,650)였는데 순위 보상에서 빠졌다. 거룡은 여러 회차(매시 정각)에 걸쳐 잡는데, 그 사이 서버를 다시 켜면
        //  메모리의 몫(MINE_WB_SHARE)이 비고 다시 때린 사람만 저장값에서 이어 붙었다 -- 마지막 회차에 안 때린 사람은 순위/보상에서 빠졌다(기본 보상은 나중에 들어올 때 받음).
        //  이제 쓰러뜨리는 순간 이번 주기에 저장된 몫(WBS_주기)을 모두 읽어 메모리에 없는 사람도 넣는다(보상은 사람마다 주기당 한 번이라 겹치지 않는다).
        if (!MINE_WB_POOL.test) try {
            for (HashMap<String, Object> r : dao.selectMineItemsByCodes(java.util.Collections.singletonList(MINE_ITEM_WBS + MINE_WB_POOL.cycle))) {
                String u = strVal(r.get("USER_NAME"), null);
                if (u != null) MINE_WB_SHARE.merge(u, (double) intVal(r.get("ITEM_LEVEL"), 0), Math::max);
            }
        } catch (Exception e) { System.out.println("[광산] 월드보스 저장된 몫 읽기 실패: " + e.getMessage()); }
        for (java.util.Map.Entry<String, Double> en : MINE_WB_SHARE.entrySet()) {
            if (en.getValue() >= mineWbMinShare()) n++;
            mineWbShareSave(en.getKey(), en.getValue(), now);
            mineWbReward(en.getKey(), en.getValue(), now);
        }
        { // [2026-11-18] 토벌 순위(쌓은 몫 순) -- 거룡의 심장 조각([2026-11-20] 1~10위 0~5개 무작위)
            List<java.util.Map.Entry<String, Double>> rk = new ArrayList<>(MINE_WB_SHARE.entrySet());
            rk.removeIf(en -> en.getValue() < mineWbMinShare());
            rk.sort((p1, q1) -> Double.compare(q1.getValue(), p1.getValue()));
            for (int i = 0; i < Math.min(MINE_WP_KIT_RANKS, rk.size()); i++) {
                try {
                    String ru = rk.get(i).getKey();
                    int kq = RND.nextInt(MINE_WP_KIT_MAX + 1);
                    if (kq > 0) dao.addUserOre(ru, S5MineMap.WP_KIT_CODE, kq);
                    MINE_SHRINE_REWARD_MSG.merge(ru, "🏅 " + MINE_WB_NAME + " 토벌 " + (i + 1) + "위! " + (kq > 0 ? "🐉 " + S5MineMap.WP_KIT_NAME + " " + kq + "개 (왕도 워프 포인트 관리인에게 내면 워프 포인트의 힘을 채워요)" : "이번엔 " + S5MineMap.WP_KIT_NAME + "이 나오지 않았어요."), (p, q) -> p + " / " + q);
                } catch (Exception ignore) { }
            }
        }
        { // [2026-11-22] 공통 보상 -- 싸운 사람 + 지금 광산에 접속해 있는 모두(나머지는 다음에 들어올 때 mineWbCommon)
            java.util.Set<String> who = new java.util.HashSet<>(MINE_WB_SHARE.keySet());
            for (java.util.Map.Entry<String, MinePresence> en : MINE_PRESENCE.entrySet()) if (en.getValue() != null && now - en.getValue().at < MINE_PRESENCE_TTL_MS) who.add(en.getKey());
            for (String u : who) mineWbCommon(u, now);
        }
        MINE_WB_DOWN.clear();
        if (n > 0) mineGossipAdd("용사 " + n + "명이 동쪽 성곽에서 " + MINE_WB_NAME + "을(를) 쓰러뜨렸대요!");
        if (!MINE_WB_POOL.test) for (java.util.Map.Entry<String, Double> en : MINE_WB_SHARE.entrySet()) if (en.getValue() >= mineWbMinShare()) mineWbGearRoll(en.getKey(), false); // [2026-11-23] 거룡 장비 1%
    }
    /** 쓰러진 뒤 처음 들어온 사람 -- 이번 주기에 싸운 몫이 저장돼 있고 아직 보상을 안 받았으면 지금 준다(상태를 읽을 때). */
    void mineWbClaim(String userName, Map<String, Integer> items, long now) {
        WbPool p = MINE_WB_POOL;
        if (!mineWbDragonDead() || p.test || p.cycle != S5MineMap.cycleAt(now) || MINE_WB_SHARE.containsKey(userName)) return; // [2026-11-23] 거룡 기준
        if (items.getOrDefault(MINE_ITEM_WB_SHARE_CYC, 0) != p.cycle + 1 || items.getOrDefault(MINE_ITEM_WB_CYCLE, 0) >= p.cycle + 1) return;
        double share = items.getOrDefault(MINE_ITEM_WB_SHARE, 0);
        MINE_WB_SHARE.put(userName, share); // 한 번만
        if (share >= mineWbMinShare()) mineWbReward(userName, share, now);
    }
    /** 회차 시간이 다 됐다(못 잡음) -- 거룡이 날아간다. 깎인 체력은 그대로 남아 다음 정각에 이어진다. */
    void mineWbTimeout(WbState st, long now) {
        synchronized (st) {
            if (st.done || st.killed) return;
            st.done = true;
        }
        MINE_WB_DOWN.clear();
        mineWbGoneAt = now;
        mineWbPoolSave(now);
        mineWbVanish(now); // [2026-11-17] "10분에 딱 안 사라지고 플레이어를 공격하는 버그, 용병도 계속 공격" -- 날아가면 몬스터 상태도 쓰러진 것처럼(다음 회차에 새로 세운다), 싸움도 모두 풀기
        int left = (int) (Math.max(0, MINE_WB_POOL.hp) * 100L / Math.max(1, mineWbMax()));
        for (String u : st.lastHit.keySet()) {
            Double sh = MINE_WB_SHARE.get(u);
            if (sh != null) mineWbShareSave(u, sh, now);
            MINE_SHRINE_REWARD_MSG.merge(u, "🌫️ " + mineWbName() + "이(가) 하늘로 날아올랐어요. 남은 체력 " + left + "% -- " + (MINE_WB_POOL.test ? "(시험 회차)" : "깎인 체력은 그대로, 다음 정각에 이어서 싸워요!"), (p, q) -> p + " / " + q);
        }
    }
    /** 거룡을 맵에서 치운다 -- 쓰러진 것처럼(다음 회차 mineWbSync가 새 상태로 세운다), 붙잡은 싸움(내 것·용병 것)도 모두 푼다. */
    void mineWbVanish(long now) {
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
        if (bm == null) return;
        MobState bs = MINE_MOB_STATE.get(mineMobKey(a, bm.id));
        if (bs != null) synchronized (bs) { if (bs.deadUntil <= now) bs.deadUntil = Long.MAX_VALUE / 4; bs.target = null; bs.frozenUntil = 0; }
        int bid = bm.id;
        for (MineEngage e : MINE_ENGAGE.values()) if (a.version().equals(e.mapVer)) e.mobs.keySet().removeIf(id -> id == bid);
        MINE_RANGED_NEXT.clear();
        synchronized (MINE_WB.zones) { MINE_WB.zones.clear(); }
    }
    /** [2026-11-23] 물보라 분출 다음 시각 -- 정해진 1분 간격 그대로(아무도 안 싸워 늦었으면 지난 차례는 건너뛴다). */
    static long mineBhSpoutNext(long prev, long now) { long n = prev + MINE_WB_PAT_GAP_MS[6]; while (n <= now) n += MINE_WB_PAT_GAP_MS[6]; return n; }
    /** [2026-11-23] 베히모스 -- MINE_BH_FLY_GAP_MS마다 MINE_BH_FLY_MS 동안 하늘로 올라 자주 옮겨 다니며(mineWbMove), MINE_BH_DIVE_MS마다 싸우는 사람 발밑에 폭풍 낙하(7).
     *  물보라 분출/폭풍 숨결을 예고하는 동안엔 날아오르지 않는다. st.zones 잠금 안에서 부른다. */
    void mineBhFly(WbState st, long now, int stage) {
        if (st.flyUntil <= now && now >= st.flyNext) {
            boolean busy = false;
            for (long[] z : st.zones) if ((z[5] == 6 || z[5] == 8) && z[2] > now - 500L) busy = true;
            if (busy) st.flyNext = now + 3_000L;
            else { st.flyAt = now; st.flyUntil = now + MINE_BH_FLY_MS + stage * 1_000L; st.flyNext = st.flyUntil + MINE_BH_FLY_GAP_MS - stage * 5_000L; st.diveNext = now + 1_200L; st.moveNext = now + 600L; }
        }
        if (st.flyUntil > now && now >= st.diveNext) {
            st.diveNext = now + MINE_BH_DIVE_MS - stage * 200L;
            List<String> us = new ArrayList<>();
            for (java.util.Map.Entry<String, Long> en : st.lastHit.entrySet()) if (now - en.getValue() <= MINE_WB_ACTIVE_MS && mineWbDownUntil(en.getKey(), now) == 0) us.add(en.getKey());
            java.util.Collections.shuffle(us, RND);
            long at = now + MINE_WB_PAT_WARN_MS[7];
            for (int i = 0; i < Math.min(us.size(), 2 + stage); i++) {
                MinePresence p = MINE_PRESENCE.get(us.get(i));
                if (p != null && S5MineMap.CASTLE.equals(p.mapId)) st.zones.add(new long[] { p.x, p.y, at, ++st.zoneSeq, MINE_WB_PAT_R[7], 7 });
            }
        }
    }
    /** [2026-11-23] 베히모스 체력이 10%씩(nm칸) 깎였다 -- 그 구간에 때린 사람(seg)마다 칸마다 햇빛석 0~1개. ml = 지금까지 깎인 10% 수.
     *  같은 날 "10% 깎일 때 싸우고 있는 사람 말고, 90%가 되는 순간엔 100~90% 동안 기여한 사람들" -- 예전엔 최근 20초 안에 때린 사람이었다. */
    void mineBhMile(List<String> seg, int nm, int ml, long now) {
        for (String u : seg) {
            if (MINE_WB_POOL.test && !isMineAdmin(u)) continue;
            int q = 0;
            for (int i = 0; i < nm; i++) q += RND.nextInt(2);
            try { if (q > 0) dao.addUserOre(u, S5MineMap.SUN_STONE_CODE, q); } catch (Exception e) { continue; }
            MINE_SHRINE_REWARD_MSG.merge(u, "🐋 " + MINE_BH_NAME + (ml >= 10 ? " 마지막 일격! " : " 체력 " + (100 - ml * 10) + "%! ") + (q > 0 ? "☀️ " + S5MineMap.SUN_STONE_NAME + " " + q + "개를 얻었어요!" : "이번엔 햇빛석이 나오지 않았어요."), (a, b) -> a + " / " + b);
        }
    }
    /** [2026-11-23] "거룡과 베히모스를 잡으면 각각 1% 확률로 장비" -- 함께 싸운 사람마다 장비마다 MINE_WB_GEAR_PCT%(관리자는 x10).
     *  거룡: 거룡의 이빨(검) / 거룡의 피부조각(갑옷). 베히모스: 베히모스의 전율(검) / 진주목걸이 / 수정구슬(장갑). 무기·갑옷은 신화 단일 등급(이미 있으면 조각으로). */
    void mineWbGearRoll(String u, boolean bh) {
        String[] codes = bh ? new String[] { MINE_ITEM_BH_TREMOR, MINE_ITEM_BH_PEARL, MINE_ITEM_BH_ORB } : new String[] { MINE_ITEM_WYRM_TOOTH, MINE_ITEM_WYRM_SKIN };
        int pct = MINE_WB_GEAR_PCT * (isMineAdmin(u) ? 10 : 1);
        for (String code : codes) {
            if (RND.nextInt(100) >= pct) continue;
            try { mineWbGearGive(u, code, bh ? MINE_BH_NAME : MINE_WB_NAME); } catch (Exception e) { System.out.println("[광산] 월드보스 장비 지급 실패 " + code + ": " + e.getMessage()); }
        }
    }
    void mineWbGearGive(String u, String code, String boss) {
        Map<String, Integer> it = mineItems(u);
        MineWeapon w = mineWeaponOf(code);
        if (w == null) w = mineArmorOf(code);
        String msg;
        if (w != null) {
            int g = 7, mask = mineWeaponMask(it, w), cur = mineWeaponGrade(it, w);
            if ((mask & (1 << g)) != 0) { dao.addUserOre(u, w.shard, g + 1); msg = "🧩 " + boss + "에게서 " + w.name + "이(가) 또 나와 분해했어요. " + mineCodeName(w.shard) + " +" + (g + 1); }
            else {
                dao.upsertMineItem(u, w.code + "_M", mask | (1 << g));
                if (cur < 0) dao.upsertMineItem(u, w.code, g + 1);
                msg = (w.armor ? "🛡️ " : "🗡️ ") + boss + "에게서 신화 " + w.name + "을(를) 얻었어요!! " + w.effect(g) + (w.armor ? " (인벤토리 갑옷 칸에서 장착)" : " (인벤토리 무기 칸에서 장착)");
                mineGossipAdd(mineNick(u) + " 님이 " + boss + "에게서 신화 " + w.name + "을(를) 손에 넣었대요!");
            }
        } else {
            String nm = MINE_ITEM_BH_PEARL.equals(code) ? "베히모스의 진주목걸이" : "베히모스의 수정구슬";
            if (it.getOrDefault(code, 0) > 0) msg = "✨ " + boss + "에게서 " + nm + "이(가) 또 나왔지만 이미 가지고 있어요.";
            else {
                dao.upsertMineItem(u, code, 1);
                msg = "✨ " + boss + "에게서 " + nm + "을(를) 얻었어요!! (인벤토리 장비 칸에서 장착)";
                mineGossipAdd(mineNick(u) + " 님이 " + boss + "에게서 " + nm + "을(를) 손에 넣었대요!");
            }
        }
        MINE_SHRINE_REWARD_MSG.merge(u, msg, (a, b) -> a + " / " + b);
    }
    /** [2026-11-23] 베히모스 처치 -- 광산에 들어온 적이 있는 모두(광산 유저 행이 있는 사람)에게 햇빛석 1개(접속해 있지 않아도 바로 넣는다). */
    void mineBhFinish(long now) {
        MINE_WB_DOWN.clear();
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        if (MINE_WB_POOL.test) { for (String u : MINE_BH_SHARE.keySet()) if (isMineAdmin(u)) all.add(u); }
        else try { for (HashMap<String, Object> r : dao.selectAllMineUsers()) { String u = strVal(r.get("USER_NAME"), "").trim(); if (u.length() > 1) all.add(u); } }
        catch (Exception e) { System.out.println("[광산] 베히모스 보상 대상 읽기 실패: " + e.getMessage()); }
        int n = 0;
        for (String u : all) {
            try { dao.addUserOre(u, S5MineMap.SUN_STONE_CODE, 1); n++; } catch (Exception e) { continue; }
            MINE_SHRINE_REWARD_MSG.merge(u, "☀️ " + MINE_BH_NAME + "이(가) 쓰러졌어요! 하늘에서 햇빛이 쏟아져 모두에게 " + S5MineMap.SUN_STONE_NAME + " 1개", (a, b) -> b + " / " + a);
        }
        System.out.println("[광산] 베히모스 처치 -- 햇빛석 1개씩 " + n + "명");
        if (!MINE_WB_POOL.test) for (String u : MINE_BH_SHARE.keySet()) mineWbGearRoll(u, true); // [2026-11-23] 베히모스 장비 1% -- 이번 주기에 베히모스를 때린 사람
        mineGossipAdd("용사들이 동쪽 성곽 하늘에서 " + MINE_BH_NAME + "을(를) 쓰러뜨렸대요! 햇빛석이 비처럼 쏟아졌다나요?");
    }
    /** 패턴 예고 -- 종류마다 제 주기로(체력이 줄수록 잦게): 화염 장판 / 심연의 비 / 꼬리 휩쓸기 / 멸망의 숨결, 그리고 포효. 전투 턴마다 불린다. */
    void mineWbZoneTick(WbState st, long now) {
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = a == null ? null : mineBossOf(a);
        if (bm == null) return;
        int[] wpos = mineWbPos(); // [2026-11-16] 지금 선 자리 기준
        int cx = wpos[0] + 1, cy = wpos[1] + 1, stage = mineWbStage();
        boolean bh = mineWbBh();
        synchronized (st.zones) {
            st.zones.removeIf(z -> now > z[2] + 3_000L);
            if (bh) mineBhFly(st, now, stage);
            for (int k = bh ? 4 : 0; k < (bh ? MINE_WB_PAT_N : 4); k++) {
                if (k == 7) continue; // 폭풍 낙하는 나는 동안만(mineBhFly)
                if (bh && mineWbFlying(st, now) && k == 8) continue; // 하늘에 있는 동안은 폭풍 숨결을 쉰다(물보라 분출은 정해진 시각에 -- [2026-11-23])
                if (now < st.patNext[k]) continue;
                st.patNext[k] = k == 6 ? mineBhSpoutNext(st.patNext[k], now) : now + MINE_WB_PAT_GAP_MS[k] - stage * MINE_WB_PAT_GAP_MS[k] / 6; // [2026-11-23] 물보라 분출은 체력과 상관없이 정확히 1분마다
                long at = now + MINE_WB_PAT_WARN_MS[k];
                if (k == 0 || k == 4) { // 화염 장판 / [2026-11-23] 돌풍 -- 지금 싸우는 사람 몇 명(셋에 하나 + 단계, 많아야 8 -- 돌풍은 둘에 하나)의 발밑
                    List<String> us = new ArrayList<>();
                    for (java.util.Map.Entry<String, Long> en : st.lastHit.entrySet()) if (now - en.getValue() <= MINE_WB_ACTIVE_MS && mineWbDownUntil(en.getKey(), now) == 0) us.add(en.getKey());
                    java.util.Collections.shuffle(us, RND);
                    int n = Math.min(us.size(), Math.min(8, k == 4 ? (us.size() + 1) / 2 + stage : (us.size() + 2) / 3 + stage));
                    for (int i = 0; i < n; i++) {
                        MinePresence p = MINE_PRESENCE.get(us.get(i));
                        if (p != null && S5MineMap.CASTLE.equals(p.mapId)) st.zones.add(new long[] { p.x, p.y, at, ++st.zoneSeq, MINE_WB_PAT_R[k], k });
                    }
                } else if (k == 1) { // 심연의 비 -- 거룡 둘레 10칸 안 곳곳
                    for (int i = 0; i < 6 + 3 * stage; i++) st.zones.add(new long[] { cx - 10 + RND.nextInt(21), cy - 10 + RND.nextInt(21), at, ++st.zoneSeq, MINE_WB_PAT_R[1], 1 });
                } else if (k == 5) { // [2026-11-23] 회오리 -- 베히모스 둘레 12칸 안 곳곳(더 많이, 더 넓게)
                    for (int i = 0; i < 9 + 4 * stage; i++) st.zones.add(new long[] { cx - 12 + RND.nextInt(25), cy - 12 + RND.nextInt(25), at, ++st.zoneSeq, MINE_WB_PAT_R[5], 5 });
                } else st.zones.add(new long[] { cx, cy, at, ++st.zoneSeq, MINE_WB_PAT_R[k], k }); // 꼬리 휩쓸기 / 멸망의 숨결 -- 거룡 가운데에서
            }
            if (now >= st.roarNext) { st.roarNext = now + MINE_WB_ROAR_GAP_MS - stage * 5_000L; st.roarAt = now; st.roarSeq++; }
        }
    }
    // ---- [2026-11-16] 악마(악마의 들판) 기술 -- 나와 싸우는 동안(전투 턴마다): 2칸 넘게 떨어지면 MINE_DEMON_BLINK_MS마다 내 곁으로 순간이동,
    //  MINE_DEMON_FIRE_MS마다 내 발밑에 지옥불(예고 1.8초, 3x3 -- 마도사는 둘레 세 곳 더, 발록은 5x5), 발록은 지면 강타(둘레 3칸 + 기절)도.
    static final int MINE_DEMON_NEED_FLOOR = 29;
    // ---- [2026-11-17] "우편함 NPC를 만들어서 각 유저들에게 바다 보석 주자 -- 사과 보상" -- 마을 우체부(중앙 평원 마을·왕도 광장·요새 도시).
    //  우편은 여기 적어 둔다: {번호, 제목, 내용, {{재료 코드, 개수}...}}. 사람마다 한 번씩 받는다(MAIL_<번호> = 1). 안 받은 우편이 있으면 들어올 때 알린다(mineState mailN).
    static final Object[][] MINE_MAILS = {
        { "SORRY1117", "🙇 사과 보상", "그동안 사막의 목걸이 효과가 바다 보석에는 적용되지 않았어요. 불편을 드려 죄송합니다 -- 바다 보석 꾸러미를 보내 드려요.",
            new String[][] { { "PEARL", "5" }, { "CORAL_GEM", "5" }, { "AMETHYST", "5" }, { "OPAL", "5" }, { "BLACK_PEARL", "5" } } },
        // [2026-11-18] 받는 사람을 정한 우편(5번째 = 받는 사람들) -- 보로네오를 함께 쓰러뜨렸는데 필살기/용병/함께 싸운 보상 경로에 보로네오 보상이 빠져 있었다
        { "BORO1118", "⚖️ 보로네오 미지급 보상", "심판자 보로네오를 함께 쓰러뜨렸을 때 빠졌던 보상을 보내 드려요. 불편을 드려 죄송합니다.",
            new String[][] { { "LIGHT_HEART", "1" }, { "JUDGE_TOKEN", "1" } }, new String[] { "도륙이냥/달소", "로스터리", "일어난다람쥐/카단" } },
        // [2026-11-18] 같은 날 17시쯤 한 번 더(로스터리·도륙이냥이 함께 -- 액션 로그 16:49~17:04 보로네오 전투) -- 보상 고침 배포 전이라 또 빠졌다
        { "BORO1118B", "⚖️ 보로네오 미지급 보상 (17시)", "17시쯤 심판자 보로네오를 다시 쓰러뜨렸을 때 빠졌던 보상을 보내 드려요.",
            new String[][] { { "LIGHT_HEART", "1" }, { "JUDGE_TOKEN", "1" } }, new String[] { "도륙이냥/달소", "로스터리" } },
        // [2026-11-19] "망망대해 체력 감소율에 따라(10초 50% -> 24%) 지금까지 배 수리 이력을 찾아 수리비 50%를 각자의 우편으로" --
        //  액션 로그(TBOT_S5_WORD_HIS 세션 묶음의 boatfix, 11-18 ~ 11-19 14:54)에서 사람마다 낸 재료를 더해 절반(올림). 관리자(테스트 계정)는 뺐다.
        { "BOAT1119A", "⛵ 배 수리비 절반 돌려드려요", "망망대해에서 배가 너무 빨리 부서졌어요(10초 50% -> 24%로 고침). 그동안 배를 고치느라 낸 재료의 절반을 돌려드려요.",
            new String[][] { { "PEARL", "3" } }, new String[] { "도륙이냥/달소" } },
        { "BOAT1119B", "⛵ 배 수리비 절반 돌려드려요", "망망대해에서 배가 너무 빨리 부서졌어요(10초 50% -> 24%로 고침). 그동안 배를 고치느라 낸 재료의 절반을 돌려드려요.",
            new String[][] { { "PEARL", "4" }, { "CORAL_GEM", "1" }, { "AMETHYST", "4" }, { "WOOD", "9" } }, new String[] { "은용" } },
        { "BOAT1119C", "⛵ 배 수리비 절반 돌려드려요", "망망대해에서 배가 너무 빨리 부서졌어요(10초 50% -> 24%로 고침). 그동안 배를 고치느라 낸 재료의 절반을 돌려드려요.",
            new String[][] { { "PEARL", "1" }, { "CORAL_GEM", "2" } }, new String[] { "로스터리" } },
        { "BOAT1119D", "⛵ 배 수리비 절반 돌려드려요", "망망대해에서 배가 너무 빨리 부서졌어요(10초 50% -> 24%로 고침). 그동안 배를 고치느라 낸 재료의 절반을 돌려드려요.",
            new String[][] { { "PEARL", "1" }, { "CORAL_GEM", "1" } }, new String[] { "내일도승리/창술사" } },
        // [2026-11-20] 마계 마을이 이제 들어갈 때 정제된 마석을 준다(캐기는 막음) -- 예전 방문은 기록이 없어(포탈 이동은 로그에 안 남음) 마계 입구에 갈 수 있는 사람(방어선 통행증)에게 1개씩(사용자)
        { "MANA1120", "💠 정제된 마석 보충", "마계 마을에서 이제 땅을 캐는 대신, 들어가기만 하면 정제된 마석을 받아요. 그동안 다녀간 몫으로 1개를 보내 드려요.",
            new String[][] { { "MANA_STONE", "1" } }, new String[] { "도륙이냥/달소", "로스터리", "castle/냉동홀붕", "은용" } } };
    static boolean mineMailFor(String userName, Object[] q) { return q.length < 5 || q[4] == null || java.util.Arrays.asList((String[]) q[4]).contains(userName); }
    /** [2026-11-20] "우편 보내는 게 배포가 필요하니? 앞으로 DB로도" -- 코드 우편(MINE_MAILS) + DB 우편(TBOT_S55_MAIL, 60초 공유 캐시 -- /갱신으로 바로).
     *  DB 줄: ITEMS = "코드:개수,코드:개수", RECIPIENTS = "이름|이름"(비우면 모두). 같은 MAIL_ID가 코드에도 있으면 코드 것만. 읽기 실패해도 코드 우편은 그대로. */
    List<Object[]> mineMails() {
        List<Object[]> l = new ArrayList<>(java.util.Arrays.asList(MINE_MAILS));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Object[] q : MINE_MAILS) ids.add((String) q[0]);
        try {
            for (HashMap<String, Object> r : dao.selectS55Mails()) {
                String id = strVal(r.get("MAIL_ID"), "").trim(), items = strVal(r.get("ITEMS"), "").trim(), rc = strVal(r.get("RECIPIENTS"), "").trim();
                if (id.isEmpty() || items.isEmpty() || !ids.add(id)) continue;
                List<String[]> its = new ArrayList<>();
                for (String p : items.split(",")) { String[] kv = p.trim().split(":"); if (kv.length == 2 && !kv[0].trim().isEmpty()) try { its.add(new String[] { kv[0].trim(), String.valueOf(Integer.parseInt(kv[1].trim())) }); } catch (NumberFormatException ignore) { } }
                if (its.isEmpty()) continue;
                String[] who = null;
                if (!rc.isEmpty()) { List<String> w = new ArrayList<>(); for (String p : rc.split("\\|")) if (!p.trim().isEmpty()) w.add(p.trim()); who = w.toArray(new String[0]); }
                l.add(new Object[] { id, strVal(r.get("TITLE"), "📮 우편"), strVal(r.get("BODY"), ""), its.toArray(new String[0][]), who });
            }
        } catch (Exception e) { System.out.println("[광산] DB 우편 읽기 실패: " + e.getMessage()); }
        return l;
    }
    List<HashMap<String, Object>> mineMailList(String userName, Map<String, Integer> items) {
        List<HashMap<String, Object>> l = new ArrayList<>();
        for (Object[] q : mineMails()) {
            if (!mineMailFor(userName, q)) continue;
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", q[0]); m.put("title", q[1]); m.put("body", q[2]); m.put("got", items.getOrDefault("MAIL_" + q[0], 0) > 0);
            List<HashMap<String, Object>> its = new ArrayList<>();
            for (String[] it : (String[][]) q[3]) { HashMap<String, Object> x = new HashMap<>(); x.put("grade", mineCodeGrade(it[0])); x.put("name", mineCodeName(it[0])); x.put("qty", Integer.parseInt(it[1])); its.add(x); }
            m.put("items", its);
            l.add(m);
        }
        return l;
    }
    int mineMailUnread(String userName, Map<String, Integer> items) { int n = 0; for (Object[] q : mineMails()) if (mineMailFor(userName, q) && items.getOrDefault("MAIL_" + q[0], 0) < 1) n++; return n; }
    // ---- [2026-11-17] "보스전이 있는 장소에서 보스전이 시작되면, 그 맵에 몇 명 있는지에 따라 '우리는 하나' 버프를 보여 주고 얼마나 강해졌는지 띄우기,
    //  체력도 배수로(초보일 때는 더 많이)" -- 보스 맵(보스가 깨어 싸우는 중: 누군가를 노리거나 30초 안에 맞음)에 2명 이상 있으면 모두에게:
    //  공격력 +20 / 50 / 100 / 150%(2 / 3 / 4 / 5명 이상 -- 월드보스 인원 버프와 같은 표, 월드보스 싸움터는 그쪽 것만), 체력 x(1 + 0.2 x (인원 - 1), 최대 +80%),
    //  체력 몫은 Lv.50 이하 x2, Lv.51~80 x1.5. 체력은 받는 피해를 그만큼 나눠서(mineCoatHit / 장판) -- 최대 체력 숫자는 그대로. 위치 알림(1초)이 다시 잰다(MINE_UNITY_MS 동안 유효).
    static final long MINE_UNITY_MS = 3_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, double[]> MINE_UNITY = new java.util.concurrent.ConcurrentHashMap<>(); // {공격 배수, 체력 배수, 끝나는 시각, 인원}
    static double mineUnityAtk(String u) { double[] v = u == null ? null : MINE_UNITY.get(u); return (v != null && v[2] > System.currentTimeMillis() ? v[0] : 1.0) * (mineKakOn(u) ? MINE_KAK_DMG : 1.0); } // [2026-11-21] 각성 -- 주는 피해 2배(내 공격/검기/돌개베기/필살기, 용병 제외)
    static double mineUnityHp(String u) { double[] v = u == null ? null : MINE_UNITY.get(u); return v != null && v[2] > System.currentTimeMillis() ? v[1] : 1.0; }
    HashMap<String, Object> mineUnityCalc(String userName, String mapId, long now) {
        S5MineMap map = S5MineMap.get(mapId);
        if (map == null || map.solo() || S5MineMap.CASTLE.equals(mapId)) { MINE_UNITY.remove(userName); return null; }
        S5MineMap.Mob bm = mineBossOf(map);
        MobState bs = bm == null ? null : MINE_MOB_STATE.get(mineMobKey(map, bm.id));
        boolean fight = bs != null && bs.deadUntil <= now && (bs.target != null || now - bs.lastHitAt < 30_000L);
        int n = 0;
        if (fight) for (MinePresence p : MINE_PRESENCE.values()) if (mapId.equals(p.mapId) && now - p.at <= MINE_PRESENCE_TTL_MS) n++;
        if (!fight || n < 2) { MINE_UNITY.remove(userName); return null; }
        int lv = 99;
        try { HashMap<String, Object> r = dao.selectMineUser(userName); if (r != null) lv = mineLv(r); } catch (Exception ignore) { }
        double atk = mineWbPartyMul(n), hpB = Math.min(0.8, 0.2 * (n - 1)) * (lv <= 50 ? 2.0 : lv <= 80 ? 1.5 : 1.0), hp = 1 + hpB;
        MINE_UNITY.put(userName, new double[] { atk, hp, now + MINE_UNITY_MS, n });
        HashMap<String, Object> u = new HashMap<>();
        u.put("n", n); u.put("atk", (int) Math.round((atk - 1) * 100)); u.put("hp", Math.round(hp * 10) / 10.0); u.put("boss", S5MineMobs.TYPES.get(bm.type).name);
        return u;
    }
    // ---- [2026-11-17] 심판자 보로네오 -- 검과 망치를 MINE_BORO_SWAP_MS마다 번갈아 든다(서버 시각으로 정해져 화면과 같다).
    //  검: 성광 베기(내 쪽으로 이어지는 빛의 칼날 세 칸, 1.3초 예고, 16%), 망치: 대지 분쇄(보스 둘레 2칸, 1.6초 예고, 22% + 기절 1.2초) + 반격·근접 x1.4.
    //  언제나: 심판의 빛(내 발밑과 둘레 곳곳 3x3 빛기둥, 1.8초 예고, 15%), 천사 강림(천사 셋~다섯이 내 둘레에 내려와 창을 내리꽂고 사라진다, 2초 예고, 12%).
    //  체력 절반 아래면 모두 30% 더 자주, 천사가 더 많이.
    static final long MINE_BORO_SWAP_MS = 12_000L;
    // [2026-11-17] 같은 날 "악마도 소환(2마리씩 일정 주기마다), 대사도" -- MINE_BORO_SUMMON_MS마다 잠든 악마 병사 자리 둘을 깨우고, MINE_BORO_SAY_MS마다 한마디(화면 말풍선 bossSay).
    static final long MINE_BORO_SUMMON_MS = 25_000L, MINE_BORO_SAY_MS = 11_000L;
    static volatile long mineBoroLastSummon = 0, mineBoroLastSay = 0;
    static final String[] MINE_BORO_LINES = { "인간이여, 심판의 날이 왔다.", "이 길 너머는 마계. 네게 그 문을 지날 자격이 있는지 보겠다.", "빛은 나를 버렸으나, 심판은 나를 버리지 않았다!",
        "검으로 베고, 망치로 부순다. 그것이 나의 심판이다.", "천사들이여, 죄인을 꿰뚫어라!", "무릎 꿇어라. 그러면 고통 없이 끝내 주마.", "네 죄의 무게를 이 망치로 달아 보겠다!" };
    void mineBoroTurn(String userName, S5MineMap map, S5MineMap.Mob boss, long now, HashMap<String, Object> out, List<long[]> rows) {
        MobState bs = MINE_MOB_STATE.get(mineMobKey(map, boss.id));
        if (bs == null || bs.deadUntil > now) return;
        int bmax = S5MineMobs.hp(S5MineMobs.TYPES.get(boss.type), boss.rank);
        String say = null;
        if (now - mineBoroLastSummon >= MINE_BORO_SUMMON_MS) {
            mineBoroLastSummon = now;
            int n = 0;
            for (S5MineMap.Mob mb : map.mobs()) {
                if (n >= 2 || mb.type != S5MineMobs.BORO_MINION_TYPE) continue;
                String key = mineMobKey(map, mb.id);
                MobState s = MINE_MOB_STATE.get(key);
                if (s != null && s.deadUntil <= now) continue; // 이미 나와 있다
                S5MineMobs.Type ty = S5MineMobs.TYPES.get(mb.type);
                MobState ns = new MobState(ty.hp());
                ns.fx = mb.x; ns.fy = mb.y; ns.frozenUntil = now + MINE_MOB_FREEZE_MS; ns.target = userName; ns.lastMoveAt = now;
                MINE_MOB_STATE.put(key, ns);
                mineEngage(userName, map, mb.id, now, now - MINE_MOB_STRIKE_GAP_MS + 700);
                rows.add(mineMobRow(mb.id, ns));
                n++;
            }
            if (n > 0) { out.put("summoned", n); say = "나와라, 지옥의 병사들아! 이 죄인을 끌어내려라!"; }
        }
        if (say == null && now - mineBoroLastSay >= MINE_BORO_SAY_MS) say = bs.hp * 2 < bmax && RND.nextInt(3) == 0 ? "불멸이라 불리던 이 몸을... 감히...!" : MINE_BORO_LINES[RND.nextInt(MINE_BORO_LINES.length)];
        if (say != null) { mineBoroLastSay = now; out.put("bossSay", say); out.put("bossSayName", "심판자 보로네오"); }
    }
    static final double MINE_BORO_HAMMER_MUL = 1.4;
    static boolean mineBoroHammer(long now) { return (now / MINE_BORO_SWAP_MS) % 2 == 1; }
    static double mineBoroMul(int mobType, long now) { return mobType == S5MineMobs.BORO_BOSS_TYPE && mineBoroHammer(now) ? MINE_BORO_HAMMER_MUL : 1.0; }
    void mineBoroPattern(S5MineMap map, MobState s, int x, int y, int bmax, long now) {
        if (s.pat == null || s.pat.length != 3) s.pat = new long[] { now + 4_000L, now + 7_000L, now + 11_000L }; // {무기 기술, 심판의 빛, 천사 강림}
        boolean rage = s.hp * 2 < bmax;
        long k = rage ? 7 : 10;
        int bx = s.fx, by = s.fy;
        if (now >= s.pat[0]) {
            if (mineBoroHammer(now)) { s.pat[0] = now + 7_000L * k / 10; mineZoneAdd(map, bx, by, now + 1_600L, 3, 6, 1_600L, 220, 1_200L); } // 대지 분쇄(2x2 둘레 2칸 = 반경 3)
            else {
                s.pat[0] = now + 6_000L * k / 10;
                int dx = Integer.signum(x - bx), dy = Integer.signum(y - by);
                for (int i = 0; i < 3; i++) mineZoneAdd(map, x + dx * i, y + dy * i, now + 1_300L + i * 150L, 1, 9, 1_300L, 160, 0); // 성광 베기
            }
        }
        if (now >= s.pat[1]) {
            s.pat[1] = now + 9_000L * k / 10;
            mineZoneAdd(map, x, y, now + 1_800L, 1, 9, 1_800L, 150, 0);
            for (int i = 0; i < (rage ? 5 : 3); i++) mineZoneAdd(map, x - 4 + RND.nextInt(9), y - 4 + RND.nextInt(9), now + 1_800L, 1, 9, 1_800L, 150, 0);
        }
        if (now >= s.pat[2]) {
            s.pat[2] = now + 15_000L * k / 10;
            int n = rage ? 5 : 3;
            for (int i = 0; i < n; i++) { double a = Math.PI * 2 * i / n + RND.nextDouble() * 0.6; mineZoneAdd(map, x + (int) Math.round(Math.cos(a) * 2), y + (int) Math.round(Math.sin(a) * 2), now + 2_000L + i * 120L, 1, 10, 2_000L, 120, 0); }
        }
    }
    // ---- [2026-11-17] 로드맵 -- 인장(들판 처치), 방어선 통행(LL_PASS = 두 인장을 맞댐), 마계 입구(무작위 이동 / MINE_CHAOS_PCT% 혼돈스러운 마계), 혼돈 1인 전투 ----
    static final String MINE_ITEM_LL_PASS = "LL_PASS";
    static final int[] MINE_DSEAL_PCT = { 3, 5, 8 }; // 들판 1/2/3 일반 악마(발록은 반드시)
    static final int MINE_CHAOS_PCT = 25, MINE_REFINE_PCT = 10; // [2026-11-18] 혼돈스러운 마계 5 -> 25%
    // [2026-11-17] "마계 마을도 웅장하게, 포털로 가끔 가도록 -- 가지면 3초 뒤 안내 없이 마을로 이동된 뒤 '마계 마을을 잠깐이나마 탐험했습니다'" --
    //  포털: MINE_CHAOS_PCT% 혼돈스러운 마계, 그다음 MINE_VILLAGE_PCT% 마계 마을, 나머지 무작위 지역. 마을에 온 시각(MINE_VILLAGE_AT)부터 MINE_VILLAGE_MS 뒤
    //  1초 턴(minePresence)이 요새 도시로 옮기고 villageOut을 보낸다(서버를 다시 켜서 시각이 없어도 바로 옮긴다).
    static final int MINE_VILLAGE_PCT = 25; // [2026-11-18] 10 -> 25%
    static final long MINE_VILLAGE_MS = 10_000L;
    static final String MINE_ITEM_VILLAGE_CNT = "VILLAGE_CNT"; // [2026-11-20] 마계 마을 다녀간 횟수 // [2026-11-18] 3초 -> 10초(그동안 캐면 정제된 마석)
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_VILLAGE_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final String MINE_VILLAGE_MSG = "🏯 마계 마을을 잠깐이나마 탐험했습니다.";
    static final java.util.concurrent.ConcurrentHashMap<String, Integer> MINE_CHAOS_RUN = new java.util.concurrent.ConcurrentHashMap<>();
    /** 마계 입구 포털이 떨어뜨리는 곳(보통 지역 -- 탑/미궁 방/혼돈/잠긴 바다는 빼고). */
    static final String[] MINE_ABYSS_DESTS = { S5MineMap.FIELD, S5MineMap.CAVE1, S5MineMap.CAVE2, S5MineMap.CAVE3, S5MineMap.DESERT, S5MineMap.BEACH, S5MineMap.NORTH, S5MineMap.ISLAND, S5MineMap.SNOW1, S5MineMap.SNOW2, S5MineMap.SNOW3,
            S5MineMap.SNOWTOWN, S5MineMap.CITY, S5MineMap.CASTLE, S5MineMap.ROYALROAD, S5MineMap.WESTROAD, S5MineMap.FOREST, S5MineMap.TOWER_CITY, S5MineMap.TOWER_GATE,
            S5MineMap.DEMON_WEST, S5MineMap.DEMON_EAST, S5MineMap.DEMON_WEST2, S5MineMap.DEMON_EAST2, S5MineMap.DEMON_WEST3, S5MineMap.DEMON_EAST3, S5MineMap.LAST_LINE };
    /** 무작위 목적지 {맵, x, y} -- 들어갈 수 있는 맵의 걸을 수 있는 아무 칸(포털 칸 빼고). */
    Object[] mineAbyssPick(Map<String, Integer> items, int lv, int water) {
        for (int k = 0; k < 30; k++) {
            S5MineMap m = S5MineMap.get(MINE_ABYSS_DESTS[RND.nextInt(MINE_ABYSS_DESTS.length)]);
            if (m == null || mineMapBlock(m, items, lv) != null) continue;
            for (int i = 0; i < 300; i++) {
                int x = 1 + RND.nextInt(Math.max(1, m.w - 2)), y = 1 + RND.nextInt(Math.max(1, m.h - 2));
                if (mineWalk(m, x, y, water) && m.portalAt(x, y) == null) return new Object[] { m, x, y };
            }
        }
        S5MineMap tc = S5MineMap.get(S5MineMap.TOWER_CITY);
        return new Object[] { tc, tc.spawnX, tc.spawnY };
    }
    /** 혼돈스러운 마계에 들어왔다 -- 내 인스턴스를 비우고 악마화된 몬스터 하나를 깨운다(바로 나를 노린다). 몬스터 이름. */
    String mineChaosStart(String userName, S5MineMap m, long now) {
        MINE_CTX.set(userName); // 1인 몬스터 상태 키(관리자 이동처럼 요청 주인이 안 적힌 길로 와도)
        mineSoloReset(userName);
        if (m.mobs().isEmpty()) return "";
        S5MineMap.Mob mb = m.mobs().get(RND.nextInt(m.mobs().size()));
        S5MineMobs.Type mt = S5MineMobs.TYPES.get(mb.type);
        int mmax = S5MineMobs.hp(mt, mb.rank);
        MobState gs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(m, mb.id), k -> new MobState(mmax));
        synchronized (gs) { gs.respawn(mmax); gs.fx = mb.x; gs.fy = mb.y; gs.target = userName; gs.frozenUntil = now + MINE_MOB_FREEZE_MS; gs.lastHitAt = 0; gs.stunUntil = 0; }
        mineEngage(userName, m, mb.id, now, 0L);
        MINE_CHAOS_RUN.put(userName, mb.id);
        return mt.name;
    }
    /** 혼돈 -- 아직 살아 있는 내 몬스터 이름(없으면 null). */
    String mineChaosAlive(String userName, long now) {
        Integer id = MINE_CHAOS_RUN.get(userName);
        S5MineMap m = S5MineMap.get(S5MineMap.CHAOS);
        if (id == null || m == null || id < 0 || id >= m.mobs().size()) return null;
        MobState s = MINE_MOB_STATE.get(m.version() + "@" + userName + ":" + id);
        if (s == null || s.hp <= 0 || s.deadUntil > now) { MINE_CHAOS_RUN.remove(userName); return null; }
        return S5MineMobs.TYPES.get(m.mobs().get(id).type).name;
    }
    /** 악마화된 몬스터 처치 보상 -- 종말의 파편(보스였으면 3개) + 보스였으면 고대~신화 장비 / ★7 동료 장비 / 거룡 보상 중 하나. */
    List<HashMap<String, Object>> mineChaosDrops(String u, S5MineMobs.Type type, StringBuilder msg) {
        List<HashMap<String, Object>> d = new ArrayList<>();
        int n = type.chaosBoss ? 3 : 1;
        dao.addUserOre(u, S5MineMap.DOOM_SHARD_CODE, n);
        d.add(mineDrop(S5MineMap.DOOM_SHARD_GRADE, S5MineMap.DOOM_SHARD_NAME, n));
        msg.append(" 🌑 ").append(S5MineMap.DOOM_SHARD_NAME).append(" +").append(n);
        if (!type.chaosBoss) return d;
        Map<String, Integer> it = mineItems(u);
        int r = RND.nextInt(100);
        if (r < 40) { // 고대~신화 장비(무기/갑옷 -- 신화 15%)
            List<MineWeapon> cand = new ArrayList<>();
            for (MineWeapon[] arr : new MineWeapon[][] { MINE_WEAPONS, MINE_ARMORS }) for (MineWeapon w : arr) if (w.maxGrade >= 6 && !mineNoRandomDrop(w.code)) cand.add(w);
            if (!cand.isEmpty()) {
                MineWeapon dw = cand.get(RND.nextInt(cand.size()));
                int g = Math.min(dw.maxGrade, RND.nextInt(100) < 15 ? 7 : 6), mask = mineWeaponMask(it, dw), cur = mineWeaponGrade(it, dw);
                if ((mask & (1 << g)) != 0) { dao.addUserOre(u, dw.shard, g + 1); msg.append(" 🧩 이미 있는 ").append(MINE_GRADE_KO[g]).append(" ").append(dw.name).append("이라 분해했어요. ").append(mineCodeName(dw.shard)).append(" +").append(g + 1); }
                else {
                    dao.upsertMineItem(u, dw.code + "_M", mask | (1 << g));
                    if (cur < 0) dao.upsertMineItem(u, dw.code, g + 1);
                    msg.append(dw.armor ? " 🛡️ " : " 🗡️ ").append(MINE_GRADE_KO[g]).append(" ").append(dw.name).append("을(를) 얻었어요! ").append(dw.effect(g));
                    mineGossipAdd(mineNick(u) + " 님이 " + MINE_GRADE_KO[g] + " " + dw.name + "을(를) 손에 넣었대요!"); // [2026-11-17] 혼돈스러운 마계 소문은 정제사만 -- 어디서 얻었는지는 말하지 않는다
                }
                return d;
            }
        }
        if (r < 70) { // ★7 동료 장비
            String part = MG_PARTS[RND.nextInt(MG_PARTS.length)];
            int cls = mgAcc(part) ? 9 : RND.nextInt(3);
            String ic = mgCode(part, cls, 7);
            dao.upsertMineItem(u, ic, it.getOrDefault(ic, 0) + 1);
            msg.append(" 🎁 ").append(mgName(part, cls, 7)).append("을(를) 얻었어요! (").append(mgEffText(part, 7)).append(")");
            mineGossipAdd(mineNick(u) + " 님이 ★7 동료 장비를 얻었대요!");
            return d;
        }
        // 거룡 보상(별빛석 / 칠요석 / 은하수 조각, 낮은 확률로 용의 반지)
        dao.addUserOre(u, S5MineMap.ORE_CODES[5], MINE_WB_STAR);
        dao.addUserOre(u, S5MineMap.SEVEN_STONE_CODE, MINE_WB_SEVEN);
        dao.addUserOre(u, S5MineMap.GALAXY_CODE, 1);
        d.add(mineDrop(6, S5MineMap.ORE_NAMES[5], MINE_WB_STAR));
        d.add(mineDrop(S5MineMap.SEVEN_STONE_GRADE, S5MineMap.SEVEN_STONE_NAME, MINE_WB_SEVEN));
        d.add(mineDrop(S5MineMap.GALAXY_GRADE, S5MineMap.GALAXY_NAME, 1));
        msg.append(" 🐉 거룡의 보물! ⭐ 별빛석 ").append(MINE_WB_STAR).append(" · 칠요석 ").append(MINE_WB_SEVEN).append(" · 🌌 ").append(S5MineMap.GALAXY_NAME).append(" 1");
        if (RND.nextInt(100) < MINE_WB_RING_PCT && it.getOrDefault(MINE_ITEM_WYRM_RING, 0) < 1) { dao.upsertMineItem(u, MINE_ITEM_WYRM_RING, 1); msg.append(" · 💍 용의 반지 Lv.1 획득!!"); }
        return d;
    }
    /** 악마의 들판 처치 -- 확률로 인장(서쪽 = 황혼, 동쪽 = 여명; 발록은 반드시). */
    void mineSealDrop(String u, S5MineMap map, S5MineMobs.Type type, StringBuilder msg, List<HashMap<String, Object>> drops) {
        int tier = S5MineMap.demonTier(map.id);
        if (!type.demon || type.chaos || tier < 0 || tier > 2) return;
        if (!type.name.endsWith("발록") && RND.nextInt(100) >= MINE_DSEAL_PCT[tier]) return;
        boolean west = S5MineMap.isDemonWest(map.id);
        dao.addUserOre(u, west ? S5MineMap.DUSK_SEAL_CODE : S5MineMap.DAWN_SEAL_CODE, 1);
        drops.add(mineDrop(west ? S5MineMap.DUSK_SEAL_GRADE : S5MineMap.DAWN_SEAL_GRADE, west ? S5MineMap.DUSK_SEAL_NAME : S5MineMap.DAWN_SEAL_NAME, 1));
        msg.append(west ? " 🌇 " : " 🌅 ").append(west ? S5MineMap.DUSK_SEAL_NAME : S5MineMap.DAWN_SEAL_NAME).append("을(를) 얻었어요!");
    }
    static final long MINE_DEMON_BLINK_MS = 3_500L, MINE_DEMON_FIRE_MS = 4_500L, MINE_DEMON_SLAM_MS = 9_000L, // [2026-11-17] 더 세게(5s/6.5s/12s -> 3.5s/4.5s/9s, 피해 150/220/240 -> 200/280/300)
                       MINE_DEMON_WARN_MS = 1_800L;
    /** 순간이동했으면 true(거리를 다시 잰다). */
    boolean mineDemonSkills(S5MineMap map, MobState s, S5MineMobs.Type type, int x, int y, int dist, long now) {
        if (s.pat == null || s.pat.length != 3) s.pat = new long[] { now + 2_500L, now + 3_000L, now + 8_000L }; // {지옥불, 순간이동, 지면 강타}
        boolean moved = false, balrog = type.name.endsWith("발록"), mage = type.name.endsWith("심연의 마도사"); // [2026-11-17] 단계 악마도
        if (dist >= 3 && now >= s.pat[1]) {
            int[][] around = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } };
            int st = RND.nextInt(around.length);
            for (int i = 0; i < around.length; i++) {
                int nx = x + around[(st + i) % around.length][0], ny = y + around[(st + i) % around.length][1];
                if (map.walkable(nx, ny)) { s.fx = nx; s.fy = ny; s.lastMoveAt = now; moved = true; break; }
            }
            s.pat[1] = now + MINE_DEMON_BLINK_MS;
        }
        if (now >= s.pat[0]) {
            s.pat[0] = now + MINE_DEMON_FIRE_MS + RND.nextInt(1_500);
            int pm = (balrog ? 280 : 200) + 40 * type.demonTier; // [2026-11-17] 들판 2·3·방어선은 단계마다 +40
            mineZoneAdd(map, x, y, now + MINE_DEMON_WARN_MS, balrog ? 2 : 1, 8, MINE_DEMON_WARN_MS, pm, 0);
            if (mage) for (int k = 0; k < 3; k++) mineZoneAdd(map, x - 3 + RND.nextInt(7), y - 3 + RND.nextInt(7), now + MINE_DEMON_WARN_MS, 1, 8, MINE_DEMON_WARN_MS, pm, 0);
        }
        if (balrog && now >= s.pat[2]) { s.pat[2] = now + MINE_DEMON_SLAM_MS; mineZoneAdd(map, s.fx, s.fy, now + MINE_DEMON_WARN_MS, 3, 6, MINE_DEMON_WARN_MS, 300, 1_200L); }
        return moved;
    }
    // ---- [2026-11-16] 장판 공통 -- "탑 9·19·29층 수문장 패턴 다양화(거룡처럼)", 악마 들판의 지옥불. 거룡 장판과 같은 모양(예고 -> 터짐)·같은 판정을
    //  맵마다(1인 층은 사람마다 -- mineMobPrefix) 둔다. 장판 = {x, y, 터지는 시각, 반경, 종류, 예고 길이, 피해(최대 체력 천분율), 기절 ms}.
    //  종류(화면 색/이름): 4 독 웅덩이 / 5 낙석 / 6 지면 강타(기절) / 7 맹독 숨결(큰 것 -- 테두리 밖으로) / 8 지옥불. 위치 알림(zones)으로 화면에 보내고, 전투 턴(mineZoneHit)에서 맞는다.
    static final java.util.concurrent.ConcurrentHashMap<String, List<long[]>> MINE_ZONES = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_ZONE_SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    static final String[] MINE_ZONE_NAME = { "화염", "심연의 비", "꼬리 휩쓸기", "멸망의 숨결", "독 웅덩이", "낙석", "지면 강타", "맹독 숨결", "지옥불", "심판의 빛", "천사의 일격", "촉수 내려찍기", "물보라", "서리 폭발", "눈보라", "빙하 숨결", "어둠 웅덩이", "해일", "화염 숨결", "돌풍", "회오리", "물보라 분출", "폭풍 낙하", "폭풍 숨결", "원거리 일격", "벼락 몰아치기", "전기충격", "낙뢰 폭풍" }; // [2026-11-23] 25·26·27 최강다람쥐 // [2026-11-23] 13~18 = 탑 수문장 속성 장판, 19~23 베히모스(화면 번호), 24 원거리 일격 // [2026-11-17] 9/10 = 보로네오, [2026-11-18] 11/12 = 크라켄
    static void mineZoneAdd(S5MineMap map, int x, int y, long at, int r, int kind, long warn, int permil, long stunMs) {
        List<long[]> l = MINE_ZONES.computeIfAbsent(mineMobPrefix(map), k -> new ArrayList<>());
        synchronized (l) { l.removeIf(z -> at > z[2] + 4_000L); if (l.size() < 100) /* [2026-11-23] 60 -> 100(최강다람쥐 벼락 몰아치기 35 + 낙뢰 폭풍 30) */ l.add(new long[] { x, y, at, r, kind, warn, permil, stunMs }); }
    }
    // ---- [2026-11-23] "보스가 공격에 성공하지 못할 때(계속 피해 다니거나 안전 자리에서 때릴 때) -- 끌어당기기와 원거리 일격, 우선 체력 50% 이하일 때(분노 게이지 없이),
    //  물 보스와 빛 보스" + "확정 원거리 공격은 베히모스에도". 대상: 물(3)/빛(8) 속성 보스(배 위의 크라켄·월드보스 제외) 체력 50% 이하, 베히모스는 체력과 상관없이 원거리 일격만.
    //  🪝 끌어당기기: 2칸 넘게 떨어져 있으면 MINE_PULL_GAP_MS마다 사슬 예고(MINE_PULL_WARN_MS, 화면 pullWarn) -> 보스 옆 칸으로 끌려오고 잠깐 기절(pull).
    //  🎯 원거리 일격: MINE_SNIPE_GAP_MS마다 내 발밑 3x3 예고(MINE_SNIPE_WARN_MS) -> 최대 체력 MINE_SNIPE_PERMIL천분율(베히모스 x1.25). 둘 다 사람마다 따로 돈다.
    static final long MINE_PULL_GAP_MS = 14_000L, MINE_PULL_WARN_MS = 1_500L, MINE_PULL_STUN_MS = 600L, MINE_SNIPE_GAP_MS = 9_000L, MINE_SNIPE_WARN_MS = 1_600L;
    static final int MINE_SNIPE_PERMIL = 200, MINE_ZK_SNIPE = 24, MINE_PULL_RANGE = 14;
    static final java.util.concurrent.ConcurrentHashMap<String, Object[]> MINE_PULL = new java.util.concurrent.ConcurrentHashMap<>(); // 이름 -> {맵, 보스 번호, 끌려가는 시각}
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_PULL_NEXT = new java.util.concurrent.ConcurrentHashMap<>(), MINE_SNIPE_NEXT = new java.util.concurrent.ConcurrentHashMap<>();
    void mineBossReach(String userName, S5MineMap map, MineEngage e, int x, int y, long now, HashMap<String, Object> out) {
        if (map == null || map.boat()) return;
        Object[] pp = MINE_PULL.get(userName);
        if (pp != null && now >= (Long) pp[2]) { // 예고가 끝났다 -- 보스 옆 칸으로
            MINE_PULL.remove(userName);
            int bid = (Integer) pp[1];
            if (map.id.equals(pp[0]) && bid >= 0 && bid < map.mobs().size()) {
                MobState s = MINE_MOB_STATE.get(mineMobKey(map, bid));
                S5MineMobs.Type t = S5MineMobs.TYPES.get(map.mobs().get(bid).type);
                int fo = Math.max(1, t.foot());
                if (s != null && s.deadUntil <= now) {
                    int dist = Math.max(Math.max(s.fx - x, x - (s.fx + fo - 1)), Math.max(s.fy - y, y - (s.fy + fo - 1)));
                    if (dist >= 2 && dist <= MINE_PULL_RANGE + 2) {
                        int bx = -1, by = -1, bd = Integer.MAX_VALUE;
                        for (int yy = s.fy - 1; yy <= s.fy + fo; yy++) for (int xx = s.fx - 1; xx <= s.fx + fo; xx++) {
                            if (xx >= s.fx && xx < s.fx + fo && yy >= s.fy && yy < s.fy + fo) continue; // 보스 칸
                            if (!map.walkable(xx, yy)) continue;
                            int d = Math.abs(xx - x) + Math.abs(yy - y);
                            if (d < bd) { bd = d; bx = xx; by = yy; }
                        }
                        if (bx >= 0) {
                            out.put("pull", new int[] { bx, by });
                            MINE_STUN.put(userName, Math.max(MINE_STUN.getOrDefault(userName, 0L), now + MINE_PULL_STUN_MS));
                            out.put("stunUntil", MINE_STUN.get(userName));
                        }
                    }
                }
            }
        }
        if (e == null || e.mobs.isEmpty()) return;
        for (Integer id : e.mobs.keySet()) {
            if (id < 0 || id >= map.mobs().size()) continue;
            S5MineMap.Mob mb = map.mobs().get(id);
            S5MineMobs.Type t = S5MineMobs.TYPES.get(mb.type);
            if (!t.boss || t.dummy || t.sim) continue;
            boolean wb = mb.type == S5MineMobs.WB_BOSS_TYPE, bh = wb && mineWbBh();
            if (wb && !bh) continue;
            if (!bh && (mb.type == S5MineMobs.KRAKEN_TYPE || (t.elem() != S5MineMobs.EL_WATER && t.elem() != S5MineMobs.EL_LIGHT))) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
            if (s == null || s.deadUntil > now) continue;
            if (!bh && (long) s.hp * 2 >= S5MineMobs.hp(t, mb.rank)) continue; // 체력 50% 이하부터
            if (now >= MINE_SNIPE_NEXT.getOrDefault(userName, 0L)) {
                MINE_SNIPE_NEXT.put(userName, now + MINE_SNIPE_GAP_MS);
                mineZoneAdd(map, x, y, now + MINE_SNIPE_WARN_MS, 1, MINE_ZK_SNIPE, MINE_SNIPE_WARN_MS, (int) Math.round(MINE_SNIPE_PERMIL * (bh ? MINE_BH_DMG_MUL : 1)), 0);
            }
            if (!bh && !MINE_PULL.containsKey(userName) && now >= MINE_PULL_NEXT.getOrDefault(userName, 0L)) {
                int fo = Math.max(1, t.foot());
                int dist = Math.max(Math.max(s.fx - x, x - (s.fx + fo - 1)), Math.max(s.fy - y, y - (s.fy + fo - 1)));
                if (dist >= 2 && dist <= MINE_PULL_RANGE) {
                    MINE_PULL_NEXT.put(userName, now + MINE_PULL_GAP_MS);
                    MINE_PULL.put(userName, new Object[] { map.id, id, now + MINE_PULL_WARN_MS });
                    out.put("pullWarn", new long[] { s.fx, s.fy, fo, now + MINE_PULL_WARN_MS });
                }
            }
            break;
        }
    }
    /** 화면에 보낼 장판 {x, y, 터지는 시각, 반경, 종류, 예고 길이}(없으면 null). */
    static List<long[]> mineZoneList(S5MineMap map, long now) {
        List<long[]> l = MINE_ZONES.get(mineMobPrefix(map));
        if (l == null) return null;
        List<long[]> o = new ArrayList<>();
        synchronized (l) { for (long[] z : l) if (now <= z[2] + 600L) o.add(new long[] { z[0], z[1], z[2], z[3], z[4], z[5] }); }
        return o.isEmpty() ? null : o;
    }
    /** 전투 턴에서 -- 방금 터진 장판 안에 서 있으면 종류마다 한 번씩 최대 체력 %(기절이 달린 것은 기절도). 받은 피해를 돌려준다. */
    int mineZoneHit(String userName, S5MineMap map, int x, int y, int lv, int rb, long now, List<HashMap<String, Object>> hits, HashMap<String, Object> out) {
        List<long[]> l = MINE_ZONES.get(mineMobPrefix(map));
        if (l == null) return 0;
        Long seenAt = MINE_ZONE_SEEN.put(userName, now);
        long seen = seenAt == null ? now - 1_500L : Math.max(seenAt, now - 5_000L);
        long[] best = new long[MINE_ZONE_NAME.length], stun = new long[MINE_ZONE_NAME.length];
        synchronized (l) {
            for (long[] z : l) {
                if (z[2] <= seen || z[2] > now) continue;
                int k = (int) Math.max(0, Math.min(MINE_ZONE_NAME.length - 1, z[4]));
                if (Math.max(Math.abs(z[0] - x), Math.abs(z[1] - y)) <= z[3]) { best[k] = Math.max(best[k], z[6]); stun[k] = Math.max(stun[k], z[7]); }
            }
        }
        int sum = 0;
        S5MineMap.Mob bm = mineBossOf(map);
        for (int k = 0; k < best.length; k++) {
            if (best[k] <= 0) continue;
            int d = Math.max(1, (int) Math.round(mineHpMax(lv, rb) * best[k] / 1000.0 / mineUnityHp(userName))); // [2026-11-17] 우리는 하나
            d = mineElHit(userName, d, bm == null ? 0 : S5MineMobs.TYPES.get(bm.type).elem()); // [2026-11-22] 보스 패턴 = 보스 속성
            sum += d;
            HashMap<String, Object> h = new HashMap<>();
            h.put("mobId", bm == null ? -1 : bm.id);
            h.put("dmg", d);
            h.put("zone", true);
            h.put("zk", k);
            hits.add(h);
            if (stun[k] > 0) { MINE_STUN.put(userName, Math.max(MINE_STUN.getOrDefault(userName, 0L), now + stun[k])); out.put("stunUntil", MINE_STUN.get(userName)); }
        }
        return sum;
    }
    // 탑 수문장 패턴(층마다 다른 조합, 체력이 절반 아래면 잦아진다): {종류, 첫 시각, 간격, 예고, 반경, 피해 천분율, 기절 ms, 모양(0 내 발밑 / 1 곳곳 n개 + 내 발밑 / 2 수문장 둘레)}
    // [2026-11-23] "탑에서 수문장들이 속성에 맞게 공격하게 -- 얼음 보스인데 독 웅덩이·낙석은 이상해": 수문장마다 자기 속성의 장판(피해·간격·예고는 그대로).
    //  13 서리 폭발 / 14 눈보라 / 15 빙하 숨결(큰 것) / 16 어둠 웅덩이 / 17 해일(큰 것) / 18 화염 숨결(큰 것)을 새로 두었다.
    static final long[][][] MINE_TW_PAT = {
        { { 16, 6_000, 8_000, 2_200, 1, 120, 0, 0 }, { 6, 13_000, 15_000, 2_000, 3, 170, 1_200, 2 } }, // 9층 라텔(어둠) -- 어둠 웅덩이, 지면 강타
        { { 5, 5_000, 10_000, 2_200, 1, 130, 0, 1 }, { 6, 12_000, 14_000, 2_000, 4, 180, 1_500, 2 }, { 5, 9_000, 9_000, 2_000, 1, 120, 0, 0 } }, // 19층 갱도왕(땅) -- 낙석, 지면 강타, 발밑 낙석
        { { 12, 5_000, 7_000, 2_000, 1, 130, 0, 0 }, { 12, 8_000, 11_000, 2_200, 1, 140, 0, 1 }, { 2, 12_000, 13_000, 1_800, 4, 190, 1_500, 2 }, { 17, 25_000, 32_000, 4_500, 8, 350, 0, 2 } }, // 29층 히드라(물) -- 물보라, 물보라 곳곳, 꼬리 휩쓸기, 해일
        { { 0, 5_000, 7_000, 2_000, 1, 130, 0, 0 }, { 8, 8_000, 11_000, 2_200, 1, 140, 0, 1 }, { 6, 12_000, 13_000, 1_800, 4, 190, 1_500, 2 }, { 18, 25_000, 32_000, 4_500, 8, 350, 0, 2 } }, // 39층 이프리트(불) -- 화염, 지옥불 곳곳, 지면 강타, 화염 숨결
        { { 13, 5_000, 7_000, 2_000, 1, 130, 0, 0 }, { 14, 8_000, 11_000, 2_200, 1, 140, 0, 1 }, { 6, 12_000, 13_000, 1_800, 4, 190, 1_500, 2 }, { 15, 25_000, 32_000, 4_500, 8, 350, 0, 2 } }, // 49층 프로스트자이언트(얼음) -- 서리 폭발, 눈보라, 얼음 몽둥이 강타, 빙하 숨결
        // [2026-11-23] 59~99층(더 잦고, 더 아프고, 예고는 짧게)
        { { 12, 4_000, 6_000, 1_600, 1, 160, 0, 0 }, { 12, 7_000, 9_000, 1_800, 1, 170, 0, 1 }, { 11, 10_000, 11_000, 1_600, 4, 230, 1_500, 2 }, { 12, 18_000, 20_000, 2_000, 2, 260, 1_000, 0 } }, // 59층 모르드레드(물) -- 물보라, 물보라 곳곳, 삼지창 내려찍기, 큰 물보라
        { { 1, 4_000, 6_500, 1_700, 1, 170, 0, 1 }, { 11, 8_000, 10_000, 1_600, 4, 240, 1_800, 2 }, { 16, 6_000, 7_000, 1_600, 1, 170, 0, 0 }, { 3, 20_000, 28_000, 4_000, 9, 420, 0, 2 } }, // 69층 아자토스(무) -- 균열 비, 촉수, 공허 웅덩이, 멸망의 숨결
        { { 9, 4_000, 5_500, 1_500, 1, 180, 0, 0 }, { 10, 7_000, 8_000, 1_600, 1, 180, 0, 1 }, { 10, 10_000, 11_000, 1_500, 4, 250, 1_800, 2 }, { 9, 16_000, 18_000, 1_800, 2, 300, 1_500, 0 } }, // 79층 세라핌(빛) -- 심판의 빛, 천사의 일격 곳곳, 날개 일격, 큰 빛기둥
        { { 0, 4_000, 5_500, 1_500, 2, 200, 0, 0 }, { 8, 6_000, 7_500, 1_600, 1, 200, 0, 1 }, { 2, 9_000, 10_000, 1_500, 3, 260, 1_500, 2 }, { 18, 18_000, 24_000, 3_800, 9, 460, 0, 2 } }, // 89층 바하무트(불) -- 화염, 지옥불, 꼬리 휩쓸기, 화염 숨결
        { { 16, 3_500, 5_000, 1_400, 1, 210, 0, 0 }, { 1, 6_000, 7_000, 1_500, 1, 210, 0, 1 }, { 2, 8_000, 9_000, 1_400, 4, 280, 2_000, 2 }, { 16, 12_000, 14_000, 1_600, 2, 280, 0, 1 }, { 3, 16_000, 22_000, 3_600, 9, 520, 0, 2 } } // 99층 니드호그(어둠) -- 어둠 웅덩이, 어둠의 비, 꼬리, 어둠 웅덩이 곳곳, 멸망의 숨결
    };
    /** 수문장이 나와 싸우는 동안(전투 턴마다) -- 때가 된 패턴을 예고한다. */
    static volatile boolean MINE_TW_PAT_ON = true; // 시험용(패턴 없이 수치만 볼 때 끈다)
    void mineTwPattern(S5MineMap map, MobState s, int floor, int x, int y, int bmax, long now) {
        if (!MINE_TW_PAT_ON) return;
        int pi = Math.max(0, Math.min(MINE_TW_PAT.length - 1, floor / 10)); // [2026-11-23] 59~99층 전용(39/49층은 29층 묶음)
        long[][] ps = MINE_TW_PAT[pi];
        if (s.pat == null || s.pat.length != ps.length) { s.pat = new long[ps.length]; for (int i = 0; i < ps.length; i++) s.pat[i] = now + ps[i][1]; }
        boolean rage = (long) s.hp * 2 < bmax; // [2026-11-23] 체력 10억이 넘는 수문장(89/99층)에서 넘치지 않게
        int cx = s.fx, cy = s.fy; // 수문장 2x2의 왼쪽 위 -- 둘레 장판은 여기서 반경 +1
        for (int i = 0; i < ps.length; i++) {
            if (now < s.pat[i]) continue;
            long[] p = ps[i];
            s.pat[i] = now + (rage ? p[2] * 7 / 10 : p[2]);
            long at = now + p[3];
            int kind = (int) p[0], r = (int) p[4], pm = (int) p[5];
            if (p[7] == 0) mineZoneAdd(map, x, y, at, r, kind, p[3], pm, p[6]);
            else if (p[7] == 1) {
                mineZoneAdd(map, x, y, at, r, kind, p[3], pm, p[6]);
                for (int k = 0; k < (rage ? 8 : 5); k++) mineZoneAdd(map, Math.max(3, Math.min(map.w - 4, x - 6 + RND.nextInt(13))), Math.max(3, Math.min(map.h - 4, y - 6 + RND.nextInt(13))), at, r, kind, p[3], pm, p[6]);
            } else mineZoneAdd(map, cx, cy, at, r, kind, p[3], pm, p[6]);
        }
    }
    /** 전투 턴에서 -- 방금 터진 장판 안에 서 있으면 종류마다 한 번씩 최대 체력 %(꼬리 휩쓸기는 기절도), 새 포효면 기절. 받은 피해를 돌려준다. */
    int mineWbPattern(String userName, int x, int y, int lv, int rb, long now, List<HashMap<String, Object>> hits, HashMap<String, Object> out) {
        WbState st = MINE_WB;
        if (st.killed || st.done || st.start <= 0) return 0;
        mineWbZoneTick(st, now);
        long[] seen = MINE_WB_SEEN.computeIfAbsent(userName, u -> new long[] { now, st.roarSeq }); // {여기까지 터진 장판은 봤다(시각), 본 포효 번호}
        boolean[] hit = new boolean[MINE_WB_PAT_N];
        synchronized (st.zones) {
            for (long[] z : st.zones) {
                if (z[2] <= seen[0] || z[2] > now) continue; // 이미 봤거나 아직 안 터짐
                if (Math.max(Math.abs(z[0] - x), Math.abs(z[1] - y)) <= z[4]) hit[(int) z[5]] = true;
            }
        }
        seen[0] = now;
        int sum = 0;
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        S5MineMap.Mob bm = mineBossOf(a);
        for (int k = 0; k < MINE_WB_PAT_N; k++) {
            if (!hit[k]) continue;
            int d = Math.max(1, (int) Math.round(mineHpFull(lv, rb) * MINE_WB_PAT_PCT[k])); // [2026-11-17] 갑옷 체력까지
            d = mineElHit(userName, d, bm == null ? 0 : mineWbBh() ? S5MineMobs.EL_WIND : S5MineMobs.TYPES.get(bm.type).elem()); // [2026-11-22] 거룡 패턴 = 거룡 속성([2026-11-23] 베히모스 = 바람)
            sum += d;
            HashMap<String, Object> h = new HashMap<>();
            h.put("mobId", bm == null ? -1 : bm.id);
            h.put("dmg", d);
            h.put("zone", true);
            h.put("zk", MINE_WB_ZK[k]); // [2026-11-23] 화면 종류 번호
            hits.add(h);
            if (k == 2) { MINE_STUN.put(userName, Math.max(MINE_STUN.getOrDefault(userName, 0L), now + MINE_WB_TAIL_STUN_MS)); out.put("stunUntil", MINE_STUN.get(userName)); }
            if (k == 6) { // [2026-11-23] 물보라 분출 -- "베히모스 기준 왼쪽이면 왼쪽 맵으로, 오른쪽이면 오른쪽 벽으로, 아래면 아래쪽 벽 끝으로"(위도 위쪽 벽으로)
                int[] bp = mineWbPos();
                int dx = x - (bp[0] + 1), dy = y - (bp[1] + 1);
                if (Math.abs(dx) >= Math.abs(dy) && dx < 0) { MINE_BH_KNOCK.put(userName, now); out.put("bhKnock", true); } // 다음 위치 알림에서 중앙 평원으로(minePresence)
                else {
                    int sx = Math.abs(dx) >= Math.abs(dy) ? 1 : 0, sy = sx == 1 ? 0 : dy > 0 ? 1 : -1, tx = x, ty = y;
                    S5MineMap ca = S5MineMap.get(S5MineMap.CASTLE);
                    while (ca != null && ca.walkable(tx + sx, ty + sy) && Math.abs(tx - x) + Math.abs(ty - y) < 80) { tx += sx; ty += sy; } // 벽(못 지나가는 칸) 앞까지
                    if (tx != x || ty != y) { out.put("push", new int[] { tx, ty }); out.put("bhKnock", true); }
                }
            }
        }
        if (st.roarSeq > seen[1]) {
            seen[1] = st.roarSeq;
            int[] rp = mineWbPos();
            if (bm != null && now - st.roarAt <= 3_000L && Math.abs(rp[0] + 1 - x) + Math.abs(rp[1] + 1 - y) <= MINE_WB_ROAR_RANGE) {
                MINE_STUN.put(userName, Math.max(MINE_STUN.getOrDefault(userName, 0L), now + MINE_WB_ROAR_MS));
                out.put("stunUntil", MINE_STUN.get(userName));
                out.put("roar", true);
            }
        }
        return sum;
    }
    /** 위치 알림에 싣는 월드보스 소식(예고 10분 ~ 끝날 때까지, 없으면 null). */
    HashMap<String, Object> mineWbInfo(String userName, long now) {
        if (!mineWbOk(userName)) return null; // [2026-11-15] 일반 공개 전엔 관리자만
        mineWbSync(now);
        long s = mineWbStart(now);
        if (now < s - MINE_WB_NOTICE_MS || now >= s + MINE_WB_LEN_MS) return null;
        WbState st = MINE_WB;
        if (st.start == s && (st.killed || st.done)) return null;
        if (MINE_WB_POOL.killed) return null; // 이번 주기엔 이미 쓰러졌다
        boolean bh = mineWbBh();
        if (bh && now < s) return null; // [2026-11-23] 베히모스는 비공개 -- 예고 없이 나타난다
        HashMap<String, Object> w = new HashMap<>();
        w.put("hp", (int) (Math.max(0, MINE_WB_POOL.hp) * 1000L / mineWbMax())); // 예고 때도 남은 체력(주기 동안 이어진다)
        w.put("carry", !MINE_WB_POOL.test);
        w.put("at", s);
        w.put("end", s + MINE_WB_LEN_MS);
        w.put("on", now >= s);
        w.put("name", mineWbName());
        if (bh) w.put("bh", 1); // [2026-11-23] 화면이 그림/이름/연출을 베히모스로
        w.put("map", S5MineMap.CASTLE);
        if (now >= s && st.start == s) {
            S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
            S5MineMap.Mob bm = mineBossOf(a);
            MobState bs = bm == null ? null : MINE_MOB_STATE.get(mineMobKey(a, bm.id));
            w.put("hp", (int) (Math.max(0, Math.min(bs == null ? MINE_WB_POOL.hp : bs.hp, MINE_WB_POOL.test ? Integer.MAX_VALUE : MINE_WB_POOL.hp)) * 1000L / mineWbMax())); // [2026-11-18] 풀(주기 동안 이어지는 체력)을 넘지 않게
            Double my = (bh ? MINE_BH_SHARE : MINE_WB_SHARE).get(userName);
            if (my != null) w.put("share", (int) Math.min(100, Math.round(my * 100 / mineWbMinShare()))); // 보상 조건을 채운 정도(%)
            int act = mineWbActive(st, now);
            if (mineWbPartyMul(act) > 1) w.put("buff", (int) Math.round((mineWbPartyMul(act) - 1) * 100)); // [2026-11-17] 인원 버프 %
            w.put("n", (bh ? MINE_BH_SHARE : MINE_WB_SHARE).size());
            if (mineWbFlying(st, now)) w.put("fly", new long[] { st.flyAt, st.flyUntil }); // [2026-11-23] 하늘을 나는 중
            w.put("act", act);
            long dn = mineWbDownUntil(userName, now);
            if (dn > 0) w.put("down", dn);
            List<long[]> zs = new ArrayList<>();
            synchronized (st.zones) { for (long[] z : st.zones) if (now <= z[2] + 600L) zs.add(new long[] { z[0], z[1], z[2], z[4], MINE_WB_ZK[(int) z[5]], MINE_WB_PAT_WARN_MS[(int) z[5]] }); } // {x, y, 터지는 시각, 반경, 종류, 예고 길이}
            if (!zs.isEmpty()) w.put("zones", zs);
        }
        return w;
    }
    /** [2026-11-15] 채팅 응답 끝에 붙이는 월드보스 알림(예고 ~ 진행 중, 방마다 회차당 한 번). 없으면 null. */
    public static String mineWbChatTail(String room) {
        if (room == null || room.isEmpty() || !MINE_WB_OPEN) return null; // [2026-11-15] 일반 공개 전엔 채팅 알림 없음(관리자 시험 회차도)
        long now = System.currentTimeMillis(), s = mineWbStart(now);
        if (now < s - MINE_WB_NOTICE_MS || now >= s + MINE_WB_LEN_MS) return null;
        WbState st = MINE_WB;
        if (st.start == s && (st.killed || st.done)) return null;
        if (st.start != s && now >= s) return null; // 시작했는데 아직 상태가 없음(아무도 광산에 없음) -- 다음 응답에
        boolean bh = mineWbBh();
        if (bh && now < s) return null; // [2026-11-23] 베히모스는 예고 없이
        WbState key = st.start == s ? st : null;
        java.util.Set<String> rooms = key != null ? key.rooms : MINE_WB_PRE_ROOMS.computeIfAbsent(s, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (!rooms.add(room)) return null;
        String rk = bh ? room + "|bh" : room; // [2026-11-23] 베히모스 첫 출현은 거룡 알림 간격과 따로
        Long lastRoom = MINE_WB_ROOM_AT.get(rk); // [2026-11-15] 매시 열리니 방마다 MINE_WB_CHAT_GAP_MS에 한 번만
        if (lastRoom != null && now - lastRoom < MINE_WB_CHAT_GAP_MS) return null;
        MINE_WB_ROOM_AT.put(rk, now);
        if (bh) return "♬♬🐋 동쪽 성곽 하늘이 갈라지며 「" + MINE_BH_NAME + "」이(가) 갑자기 나타났어요! 광산 화면 위 [참여] 버튼으로 다 같이 싸워요.";
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(s).atZone(KST);
        return "♬♬🐉 월드보스 「" + MINE_WB_NAME + "」" + (now < s ? String.format(" %02d:%02d 동쪽 성곽에 등장!", z.getHour(), z.getMinute()) : " 동쪽 성곽에 출현 중!") + " 광산 화면 위 [참여] 버튼으로 다 같이 싸워요. (쓰러뜨리면 함께 싸운 모두에게 같은 보상)";
    }
    static final java.util.concurrent.ConcurrentHashMap<Long, java.util.Set<String>> MINE_WB_PRE_ROOMS = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_WB_ROOM_AT = new java.util.concurrent.ConcurrentHashMap<>();
    /** 가장 가까운 설 수 있는 칸(마름모 둘레로 넓혀 가며), 없으면 null. */
    static int[] mineNearWalk(S5MineMap map, int x, int y) {
        for (int r = 0; r <= 30; r++) for (int dx = -r; dx <= r; dx++) {
            int dy = r - Math.abs(dx);
            if (map.walkable(x + dx, y + dy)) return new int[] { x + dx, y + dy };
            if (dy != 0 && map.walkable(x + dx, y - dy)) return new int[] { x + dx, y - dy };
        }
        return null;
    }
    /** [2026-11-15] 월드보스 -- action=info / join(동쪽 성곽 내성 서문 앞으로) / start·stop(관리자 시험). */
    public HashMap<String, Object> mineWorldBoss(String userName, String action) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        if ("board".equals(action)) { HashMap<String, Object> bo = mineWbBoard(now); bo.put("ok", true); return bo; } // [2026-11-17] 월드보스 게시판
        if ("bosses".equals(action)) { HashMap<String, Object> bo = new HashMap<>(); bo.put("ok", true); bo.put("bosses", mineBossList(userName, now)); bo.put("bossRw", mineBossRwList(userName)); return bo; } // [2026-11-18] 보스 목록(천둥 황소 처치 수 등)을 열 때 새로
        if (("start".equals(action) || "stop".equals(action)) && isMineAdmin(userName)) {
            if ("start".equals(action)) {
                mineWbPool(now);
                if (MINE_WB_POOL.test) synchronized (MINE_WB_POOL) { MINE_WB_POOL.hp = mineWbMax(); MINE_WB_POOL.killed = false; MINE_WB_SHARE.clear(); } // 일반 공개 전 시험 회차 -- 매번 가득 찬 작은 체력으로
                if (MINE_WB_POOL.killed) { out.put("message", "🛠️ 이번 맵 주기엔 이미 쓰러뜨렸어요. 맵이 초기화되면 다시 나타나요."); return out; }
                mineWbForce = Math.max(now, MINE_WB.start + 1); mineWbSync(mineWbForce); // 바로 앞 회차와 같은 밀리초여도 새 회차로
                out.put("message", "🛠️ 월드보스 시험 회차를 지금 시작했어요(5분, 동쪽 성곽 내성)." + (MINE_WB_POOL.test ? "" : " 체력은 이번 주기에 깎인 그대로예요."));
            } else { mineWbForce = 0; WbState st = MINE_WB; if (!st.done && !st.killed) mineWbTimeout(st, now); out.put("message", "🛠️ 월드보스 회차를 끝냈어요."); }
            out.put("ok", true);
            return out;
        }
        HashMap<String, Object> info = mineWbInfo(userName, now);
        out.put("wb", info);
        if (!"join".equals(action)) { out.put("ok", true); return out; }
        if (info == null) { out.put("message", MINE_WB_OPEN ? "지금은 월드보스가 없어요. 매시 정각에 동쪽 성곽에 나타나요(5분 동안)." : "지금은 월드보스가 없어요."); return out; }
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        if (mineFrozen(userName, now) != null || mineStunned(userName, now) || mineWbDownUntil(userName, now) > 0) { out.put("message", "지금은 움직일 수 없어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        MINE_WB_ATK.put(userName, Math.max(1, mineAtk(mineLv(row), mineRebirth(items)))); // 몫 기준(내 기본 공격력)
        S5MineMap a = S5MineMap.get(S5MineMap.CASTLE);
        int[] to = mineNearWalk(a, S5MineMap.CASTLE_KEEP[0] - 5, a.h / 2); // 내성 서문 앞(거룡 인식 범위 밖)
        if (to == null) to = new int[] { a.spawnX, a.spawnY };
        mineDisengageAll(userName);
        dao.updateMinePos(userName, a.id, to[0], to[1], now);
        boolean got = items.getOrDefault(MINE_ITEM_WB_CYCLE, 0) >= S5MineMap.cycleAt(now) + 1 && !isMineAdmin(userName);
        out.put("ok", true);
        out.put("message", "🐉 동쪽 성곽 내성 앞에 왔어요! " + (now < (Long) info.get("at") ? "곧 내성 한가운데에 " + MINE_WB_NAME + "이(가) 나타나요." : "내성 안의 " + MINE_WB_NAME + "을(를) 다 같이 쓰러뜨려요!")
                + (got ? " (이번 맵 주기 보상은 이미 받았어요 -- 도와주기만 돼요)" : ""));
        out.put("state", mineState(userName));
        return out;
    }

    // ---- [2026-11-13] 보스 소환 -- "기본적으로 보스룸에 보스가 없도록, 보스가 없을 때 NPC를 한 명 두어 개인별로 보스 소환(2시간 쿨타임).
    //  2인 플레이 땐 바로 두 번 도전할 수도 있게(쿨타임이 사람마다), 소환하면 보스 관련 오브젝트 초기화(하피는 나무가 다시 자람)".
    //  예전: 쓰러지고 2시간 뒤 저절로 다시(젠타임마다 같은 사람이 잡아 다른 사람은 늘 "보스 없음").
    //  보스룸(동굴 3층/폐허/설산 봉우리/용암섬/피라미드 지하/하피의 둥지/닌자 두목의 방)의 보스는 소환된 동안만 있다(mineBossUp).
    //  같은 날 "소환사 NPC는 보스가 처음 생성되는 곳에, 소환 시 3초 카운트를 크게 하고 사라지면서 보스 생성, 소환된 뒤 맵에 아무도 없이 10분 지나면 보스는 사라지게".
    //  소환하면 MINE_CALL_DELAY_MS 뒤(MINE_CALL_AT = 나타나는 시각)에 나온다 -- 그동안 소환사 머리 위 카운트(맵 안 모두에게, 위치 알림 bossCall.at).
    //  나온 뒤 쓰러지거나, 맵에 아무도 없는 채로(위치 알림 MINE_CALL_SEEN) MINE_CALL_LIFE_MS가 지나면 사라진다. 소환 기록은 메모리(배포하면 보스가 사라짐 -- 쿨타임은 DB라 남는다).
    //  쿨타임은 유저 아이템 BCALL_<맵>(마지막 소환 시각, 분 단위). 소환사 NPC는 보스 집 칸에 서 있고(보스가 없을 때만, 화면 위치 알림 bossCall), 보스가 나오면 사라진다.
    static final long MINE_CALL_CD_MS = 2 * 3_600_000L, MINE_CALL_LIFE_MS = 10 * 60_000L, MINE_CALL_DELAY_MS = 3_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_CALL_SEEN = new java.util.concurrent.ConcurrentHashMap<>(); // 맵 -> 누가 마지막으로 있던 시각
    static final String MINE_ITEM_CALL = "BCALL_";
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_CALL_AT = new java.util.concurrent.ConcurrentHashMap<>(); // 맵 -> 소환 시각
    static final String[] MINE_CALL_MAPS = { /* [2026-11-16] 동굴 3층은 소환 없이 30분마다 나타나는 챔피언으로 */ S5MineMap.RUINS, S5MineMap.PEAK, S5MineMap.ISLE7, S5MineMap.PYRAMID_UNDER, S5MineMap.HARPY_NEST, S5MineMap.SHRINE_BOSS };
    /** 소환으로 나오는 보스의 맵(아니면 null). */
    static String mineCallMap(int type) {
        if (S5MineMobs.sqIndex(type) >= 0) return S5MineMap.SQ_DENS[S5MineMobs.sqIndex(type)]; // [2026-11-23] 최강다람쥐
        return type == S5MineMobs.RAID_BOSS_TYPE ? S5MineMap.RUINS : type == S5MineMobs.ICE_BOSS_TYPE ? S5MineMap.PEAK
                : type == S5MineMobs.LAVA_BOSS_TYPE ? S5MineMap.ISLE7 : type == S5MineMobs.PHARAOH_BOSS_TYPE ? S5MineMap.PYRAMID_UNDER : type == S5MineMobs.HARPY_BOSS_TYPE ? S5MineMap.HARPY_NEST
                : type == S5MineMobs.SHRINE_BOSS_TYPE ? S5MineMap.SHRINE_BOSS : null;
    }
    static boolean mineCallRoom(String mapId) { if (S5MineMap.isSqDen(mapId)) return true; for (String m : MINE_CALL_MAPS) if (m.equals(mapId)) return true; return false; } // [2026-11-23] 다람쥐 굴(보스 목록엔 안 나온다 -- 숨은 방)
    /** 소환된 보스가 지금 나와 있는지. */
    boolean mineBossUp(int type, long now) {
        String mid = mineCallMap(type);
        Long at = mid == null ? null : MINE_CALL_AT.get(mid);
        if (at == null || now < at) return false; // 소환 카운트 중
        String bk = mineBossKey(type);
        if (bk != null && mineBossKilledAt(bk) >= at) return false; // 소환한 뒤 쓰러졌다
        S5MineMap map = S5MineMap.get(mid);
        S5MineMap.Mob bm = map == null ? null : mineBossOf(map);
        MobState s = bm == null ? null : MINE_MOB_STATE.get(mineMobKey(map, bm.id));
        if (s != null && s.deadUntil > now) return false; // 닌자 두목(쓰러진 시각 기록 없음)
        long last = Math.max(at, MINE_CALL_SEEN.getOrDefault(mid, 0L));
        if (now - last >= (S5MineMap.isSqDen(mid) ? MINE_SQ_EMPTY_MS : MINE_CALL_LIFE_MS)) { MINE_CALL_AT.remove(mid, at); return false; } // 맵에 아무도 없이 10분([2026-11-23] 다람쥐 굴은 5초 -- 모두 쓰러지면 마을로 가니 같은 셈)
        if (S5MineMap.isSqDen(mid) && mineSqGone(now)) return false; // [2026-11-23] 이번 맵 주기엔 이미 쓰러졌다
        return true;
    }
    /** 소환 카운트 중이면 보스가 나타나는 시각(아니면 0). */
    static long mineCallPending(String mapId, long now) {
        Long at = MINE_CALL_AT.get(mapId);
        return at != null && now < at ? at : 0;
    }
    /** 내가 다음에 소환할 수 있는 시각(ms, 지금 되면 0). */
    static long mineCallReadyAt(Map<String, Integer> items, String mapId) {
        int m = items == null ? 0 : items.getOrDefault(MINE_ITEM_CALL + mapId, 0);
        return m <= 0 ? 0 : m * 60_000L + MINE_CALL_CD_MS;
    }
    /** 소환사 NPC 자리(보스 집 칸). */
    static int[] mineCallSpot(S5MineMap map) {
        S5MineMap.Mob bm = mineBossOf(map);
        if (bm == null) return null;
        if (S5MineMap.CAVE3.equals(map.id)) { // [2026-11-13] "지하 광산 3층 소환사가 길을 막는다 -- 맵 가운데로" -- 가운데에서 가까운, 둘레 8칸이 모두 트인 칸
            int[] c = MINE_CALL_CENTER.get(map.version());
            if (c == null) {
                int cx = map.w / 2, cy = map.h / 2;
                for (int d = 0; d < Math.max(map.w, map.h) && c == null; d++)
                    for (int dy = -d; dy <= d && c == null; dy++) for (int dx = -d; dx <= d; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != d) continue;
                        int x = cx + dx, y = cy + dy;
                        boolean open = map.walkable(x, y) && map.portalAt(x, y) == null;
                        for (int yy = y - 1; yy <= y + 1 && open; yy++) for (int xx = x - 1; xx <= x + 1; xx++) if (!map.walkable(xx, yy)) { open = false; break; }
                        if (open) { c = new int[] { x, y }; break; }
                    }
                if (c == null) c = new int[] { bm.x, bm.y };
                MINE_CALL_CENTER.put(map.version(), c);
            }
            return c;
        }
        return new int[] { bm.x, bm.y };
    }
    static final java.util.concurrent.ConcurrentHashMap<String, int[]> MINE_CALL_CENTER = new java.util.concurrent.ConcurrentHashMap<>();
    /** 보스와 그 둘레 오브젝트를 처음으로 -- 보스 체력, 수하/분신, 빙결/용암/원거리 차례, 하피 나무. */
    void mineCallReset(S5MineMap map, long now) {
        S5MineMap.Mob boss = mineBossOf(map);
        if (boss == null) return;
        for (S5MineMap.Mob mb : map.mobs()) {
            S5MineMobs.Type t = S5MineMobs.TYPES.get(mb.type);
            if (t.boss || t.minion || t.clone) MINE_MOB_STATE.remove(mineMobKey(map, mb.id));
        }
        S5MineMobs.Type bt = S5MineMobs.TYPES.get(boss.type);
        int bmax = S5MineMobs.hp(bt, boss.rank);
        MobState bs = new MobState(bmax);
        bs.fx = boss.x; bs.fy = boss.y; bs.regenAt = now; bs.nearAt = now;
        MINE_MOB_STATE.put(mineMobKey(map, boss.id), bs);
        int bid = boss.id;
        for (MineEngage e : MINE_ENGAGE.values()) if (map.version().equals(e.mapVer)) e.mobs.keySet().removeIf(id -> id == bid);
        MINE_RANGED_NEXT.clear();
        if (boss.type == S5MineMobs.RAID_BOSS_TYPE) { mineRaidAwakeUntil = 0; mineRaidLastSummon = 0; }
        if (boss.type == S5MineMobs.ICE_BOSS_TYPE) { mineIceAwakeUntil = 0; mineIceNextFreeze = 0; mineIceThawAll(); }
        if (boss.type == S5MineMobs.LAVA_BOSS_TYPE) MINE_LAVA.clear();
        if (boss.type == S5MineMobs.PHARAOH_BOSS_TYPE) MINE_PHARAOH_STUN_AT.clear();
        if (boss.type == S5MineMobs.HARPY_BOSS_TYPE) { MINE_TREES.clear(); mineHarpyReset(); } // 나무가 모두 다시 자란다
    }
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_CALL_TRY = new java.util.concurrent.ConcurrentHashMap<>();
    /** [2026-11-13] 보스 소환사 -- action=info(지금 상태) / call(소환). */
    public HashMap<String, Object> mineBossCall(String userName, String action) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        S5MineMap.Mob boss = map == null || !mineCallRoom(map.id) ? null : mineBossOf(map);
        if (boss == null) { out.put("message", "여기엔 소환할 보스가 없어요."); return out; }
        S5MineMobs.Type bt = S5MineMobs.TYPES.get(boss.type);
        Map<String, Integer> items = mineItems(userName);
        long ready = mineCallReadyAt(items, map.id);
        boolean up = mineBossUp(boss.type, now);
        out.put("boss", bt.name);
        out.put("readyAt", ready);
        out.put("up", up);
        out.put("cdMin", MINE_CALL_CD_MS / 60_000L);
        boolean sqDen = S5MineMap.isSqDen(map.id);
        if (sqDen) { out.put("sq", true); out.put("giftCost", mineCostText(MINE_SQ_GIFT_COST)); }
        if (sqDen && mineSqGone(now)) { out.put("message", "🍂 최강다람쥐는 어디론가 사라졌어요... (맵이 초기화되면 돌아온대요)"); return out; } // [2026-11-23]
        if ("sqgift".equals(action) && sqDen) { // [2026-11-23] 최강다람쥐 -- 도토리(금괴)를 바친다. 같은 날 "금괴 주는 건 보상 없도록" -- 보상도 쿨타임도 없이 고마워만 한다
            if (up || mineCallPending(map.id, now) > 0) { out.put("message", "⚡ 지금은 싸우는 중이에요!"); return out; }
            if (!mineCostOk(MINE_SQ_GIFT_COST, mineOreHave(userName))) { out.put("message", "🐿️ 최강다람쥐: 도토리(" + mineCostText(MINE_SQ_GIFT_COST) + ")가 없잖아!"); return out; }
            mineTakeCost(userName, MINE_SQ_GIFT_COST, 1);
            out.put("ok", true);
            out.put("message", "🐿️ 최강다람쥐: 냠냠! 이 도토리 꽤 실한데? 고마워~");
            return out;
        }
        if (!"call".equals(action)) { out.put("ok", true); return out; }
        Long last = MINE_CALL_TRY.get(userName);
        if (last != null && now - last < 1_000L) { out.put("tooFast", true); return out; }
        MINE_CALL_TRY.put(userName, now);
        if (up) { out.put("message", "👑 " + bt.name + "이(가) 이미 나와 있어요!"); return out; }
        if (mineCallPending(map.id, now) > 0) { out.put("message", "👑 " + bt.name + "이(가) 곧 나타나요!"); return out; }
        if (ready > now) { out.put("message", "아직 다시 부를 수 없어요. " + mineCallLeft(ready - now) + " 뒤에 다시 와 주세요."); return out; }
        int[] sp = mineCallSpot(map), pos = minePos(row, map, mineWalkMode(items));
        if (sp == null || pos == null || Math.max(Math.abs(pos[0] - sp[0]), Math.abs(pos[1] - sp[1])) > 2) { out.put("message", "소환사 바로 곁에서 불러 주세요."); return out; }
        synchronized (MINE_CALL_AT) {
            if (mineBossUp(boss.type, now) || mineCallPending(map.id, now) > 0) { out.put("message", "👑 " + bt.name + "이(가) 이미 나와 있어요!"); return out; }
            dao.upsertMineItem(userName, MINE_ITEM_CALL + map.id, (int) (now / 60_000L));
            mineCallReset(map, now);
            MINE_CALL_AT.put(map.id, now + MINE_CALL_DELAY_MS);
            MINE_CALL_SEEN.put(map.id, now);
        }
        out.put("ok", true);
        out.put("at", now + MINE_CALL_DELAY_MS);
        out.put("readyAt", now / 60_000L * 60_000L + MINE_CALL_CD_MS);
        out.put("message", "👑 " + bt.name + "을(를) 불러냈어요!");
        return out;
    }
    // ---- [2026-11-23] 최강다람쥐(다람쥐 굴) -- "번개의 힘으로 벼락 몰아치기 등, 하피 체력 x3 이벤트 보스, 전광석화 위주 + 전기충격 스턴, 공격 속도 다른 보스의 7배, 보상 다람쥐메달(아직 사용처 없음)".
    //  싸우는 동안(전투 턴마다 mineSqTurn): ⚡ 전광석화 MINE_SQ_DASH_MS마다 내 곁으로 순간 이동, 🌩️ 벼락 몰아치기 MINE_SQ_BOLT_MS마다 내 둘레 곳곳 3x3 낙뢰,
    //  ⚡ 전기충격 MINE_SQ_SHOCK_MS마다 내 발밑 5x5(맞으면 기절). 근접은 MINE_MOB_STRIKE_GAP_MS / 7마다 한 번꼴(전투 턴 사이에 친 만큼 한꺼번에), 원거리 MINE_SQ_SHOT_MS.
    //  처치하면 다람쥐메달 MINE_SQ_MEDALS개. 선물(도토리 = 금괴 1개)은 메달 1개 + 30% 별빛석 1개. 싸우기와 선물은 같은 쿨타임(굴마다 2시간).
    static final long MINE_SQ_HIT_MS = 1_000L / 7; // [2026-11-23] "격투가 -- 공격 속도 1초에 7번" (예전 1.5초 / 7은 1초에 4.7번꼴이었다)
    static final long MINE_SQ_DASH_MS = 2_500L, MINE_SQ_BOLT_MS = 9_000L /* [2026-11-23] "벼락 몰아치기는 3.5초 동안 초당 10줄기" -- 9초마다 35줄기를 0.1초 간격으로(내 둘레 4칸 안) */, MINE_SQ_SHOCK_MS = 10_000L, MINE_SQ_SHOT_MS = 1_000L, MINE_SQ_SHOCK_STUN_MS = 1_500L;
    static final int MINE_SQ_MEDALS = 1 /* [2026-11-23] "다람쥐메달 보상은 1개만" */, MINE_ZK_SQ_BOLT = 25, MINE_ZK_SQ_SHOCK = 26, MINE_ZK_SQ_STORM = 27;
    // [2026-11-23] 같은 날 "맵 전체에 번개가 초당 10번, 3초 동안" -- ⛈️ 낙뢰 폭풍: MINE_SQ_STORM_MS마다 굴 전체 곳곳에 3x3 낙뢰 30줄기(0.1초 간격), 한 줄기 최대 체력 6%
    static final long MINE_SQ_STORM_MS = 15_000L, MINE_SQ_STORM_FIRST_MS = 10_000L;
    static final String[][] MINE_SQ_GIFT_COST = { { S5MineMap.BAR_CODES[3], "1" } };
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_SQ_T = new java.util.concurrent.ConcurrentHashMap<>(); // 이름 -> {다음 전광석화, 다음 벼락, 다음 전기충격}
    void mineSqTurn(String userName, S5MineMap map, MineEngage e, int x, int y, long now, HashMap<String, Object> out) {
        if (e == null || e.mobs.isEmpty() || !S5MineMap.isSqDen(map.id)) return;
        for (Integer id : e.mobs.keySet()) {
            if (id < 0 || id >= map.mobs().size() || S5MineMobs.sqIndex(map.mobs().get(id).type) < 0) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
            if (s == null || s.deadUntil > now) return;
            long[] t = MINE_SQ_T.computeIfAbsent(userName, k -> new long[] { now + 1_500L, now + 4_000L, now + 6_000L, now + MINE_SQ_STORM_FIRST_MS });
            if (now - t[0] > 60_000L) { t[0] = now + 1_500L; t[1] = now + 4_000L; t[2] = now + 6_000L; t[3] = now + MINE_SQ_STORM_FIRST_MS; } // 오랜만의 싸움
            if (now >= t[3]) { // ⛈️ 낙뢰 폭풍 -- 굴 전체에 0.1초마다 한 줄기씩 3초
                t[3] = now + MINE_SQ_STORM_MS;
                for (int k = 0; k < 30; k++) mineZoneAdd(map, 1 + RND.nextInt(Math.max(1, map.w - 2)), 1 + RND.nextInt(Math.max(1, map.h - 2)), now + 700L + k * 100L, 1, MINE_ZK_SQ_STORM, 700L + k * 100L, 60, 0);
                out.put("sqStorm", true);
            }
            int dist = Math.max(Math.max(s.fx - x, x - (s.fx + 1)), Math.max(s.fy - y, y - (s.fy + 1)));
            if (now >= t[0]) { // ⚡ 전광석화 -- 내 곁(2x2가 설 수 있는 칸)으로
                t[0] = now + MINE_SQ_DASH_MS;
                if (dist >= 2) {
                    int[][] c = { { x + 1, y - 1 }, { x + 1, y }, { x - 2, y - 1 }, { x - 2, y }, { x - 1, y + 1 }, { x, y + 1 }, { x - 1, y - 2 }, { x, y - 2 } };
                    for (int k = 0; k < 8; k++) {
                        int[] q = c[(k + RND.nextInt(8)) % 8];
                        if (map.walkable(q[0], q[1]) && map.walkable(q[0] + 1, q[1]) && map.walkable(q[0], q[1] + 1) && map.walkable(q[0] + 1, q[1] + 1)) {
                            out.put("sqDash", new int[] { s.fx, s.fy, q[0], q[1] });
                            synchronized (s) { s.fx = q[0]; s.fy = q[1]; }
                            break;
                        }
                    }
                }
            }
            if (now >= t[1]) { // 🌩️ 벼락 몰아치기 -- 3.5초 동안 초당 10줄기(첫 줄기는 내 발밑, 나머지는 내 둘레 4칸 안 곳곳)
                t[1] = now + MINE_SQ_BOLT_MS;
                long at = now + 700L;
                mineZoneAdd(map, x, y, at, 1, MINE_ZK_SQ_BOLT, 700L, 100, 0);
                for (int k = 1; k < 35; k++) mineZoneAdd(map, Math.max(1, Math.min(map.w - 2, x - 4 + RND.nextInt(9))), Math.max(1, Math.min(map.h - 2, y - 4 + RND.nextInt(9))), at + k * 100L, 1, MINE_ZK_SQ_BOLT, 700L + k * 100L, 100, 0);
            }
            if (now >= t[2]) { // ⚡ 전기충격 -- 내 발밑 5x5, 맞으면 기절
                t[2] = now + MINE_SQ_SHOCK_MS;
                mineZoneAdd(map, x, y, now + 700L, 2, MINE_ZK_SQ_SHOCK, 700L, 90, MINE_SQ_SHOCK_STUN_MS);
            }
            return;
        }
    }
    // [2026-11-23] "보스와 싸우다 모두 죽으면 다시 NPC로, 맵에 아무도 없어도 NPC로, 처치하면 다음 맵 초기화 때 돌아와" --
    //  굴에 아무도 없는 채로 MINE_SQ_EMPTY_MS가 지나면 소환이 풀려 NPC로(쓰러진 사람은 마을로 옮겨지니 모두 쓰러진 것도 같다, mineBossUp).
    //  처치하면 그 맵 주기 번호를 설정 MINE_SQ_KILL에 적고, 그 주기 동안은 NPC도 보스도 없다(소환사 자리 비움, 선물/싸우기 막음).
    static final long MINE_SQ_EMPTY_MS = 5_000L;
    static final String MINE_SQ_KILL_KEY = "MINE_SQ_KILL";
    static volatile int MINE_SQ_KILL_CYC = Integer.MIN_VALUE; // 읽기 전
    boolean mineSqGone(long now) {
        if (MINE_SQ_KILL_CYC == Integer.MIN_VALUE) { int c = -1; try { String v = dao.selectConfigValue(MINE_SQ_KILL_KEY); if (v != null) c = Integer.parseInt(v.trim()); } catch (Exception ignore) { } MINE_SQ_KILL_CYC = c; }
        return MINE_SQ_KILL_CYC == S5MineMap.cycleAt(now);
    }
    /** 최강다람쥐 처치 보상(보스 공통 보상과 별개). */
    void mineSqReward(String userName, StringBuilder msg) {
        int cyc = S5MineMap.cycleAt(System.currentTimeMillis());
        MINE_SQ_KILL_CYC = cyc;
        try { dao.upsertConfigValue(MINE_SQ_KILL_KEY, String.valueOf(cyc), "strongest squirrel killed in this map cycle (set by app)"); } catch (Exception ignore) { }
        for (String d : S5MineMap.SQ_DENS) MINE_CALL_AT.remove(d);
        msg.append(" 🍂 최강다람쥐가 \"다음엔 안 진다!\" 하며 숲 속으로 사라졌어요(맵이 초기화되면 돌아온대요).");
        try { dao.addUserOre(userName, S5MineMap.SQ_MEDAL_CODE, MINE_SQ_MEDALS); } catch (Exception e) { return; }
        msg.append(" 🏅 다람쥐메달 ").append(MINE_SQ_MEDALS).append("개! (아직 쓸 곳은 없대요)");
        mineGossipAdd(mineNick(userName) + " 님이 숲속 어딘가에서 최강다람쥐를 이겼대요!");
    }
    static String mineCallLeft(long ms) {
        long m = (ms + 59_999L) / 60_000L;
        return m >= 60 ? (m / 60) + "시간" + (m % 60 > 0 ? " " + m % 60 + "분" : "") : m + "분";
    }

    static S5MineMap.Mob mineBossOf(S5MineMap map) {
        if (map.mobs().isEmpty()) return null;
        S5MineMap.Mob m = map.mobs().get(map.mobs().size() - 1);
        return S5MineMobs.TYPES.get(m.type).boss ? m : null;
    }

    /** 쓰러짐 안내의 뒷부분 -- "<곳> 깨어났어요." 또는 월드보스 싸움터면 "10초 뒤 그 자리에서 다시 일어나요.". */
    String mineRespawnMsg(String userName, Map<String, Integer> items, long now) {
        String w = mineRespawn(userName, items, now);
        return mineWbDownUntil(userName, now) > 0 ? "10초 뒤 그 자리에서 다시 일어나요." : w + " 깨어났어요.";
    }
    /** 쓰러졌을 때 깨어나는 곳으로 옮기고 그곳 이름을 돌려준다(성당에서 기도했으면 왕도 성당, 아니면 평원 마을). */
    // [2026-11-17] "보스와 전투 중 죽었는데 보스 앞으로 소환되는 버그, 꽤 자주", "혼돈스러운 마계에서 죽으면 마을로 간다는 문구만 뜨고 몬스터만 없어진다" --
    //  쓰러질 때 이미 출발해 있던 공격/검기/전투 턴 요청이 끝나면서 원래 자리(보스 앞, 혼돈)를 위치로 다시 써 버렸다(부활 이동보다 늦게).
    //  쓰러진 시각을 적어 두고 MINE_DIED_GUARD_MS 동안은 그 요청들이 위치를 쓰지 않는다(mineJustDied).
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_DIED_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_DIED_GUARD_MS = 3_000L;
    static boolean mineJustDied(String userName) { Long d = MINE_DIED_AT.get(userName); return d != null && System.currentTimeMillis() - d < MINE_DIED_GUARD_MS; }
    String mineRespawn(String userName, Map<String, Integer> items, long now) {
        mineDisengageAll(userName); // [2026-10-25] 쓰러지면 인식 끝
        mineSoloReset(userName); // [2026-11-15] 탑 1인 인스턴스 -- 쓰러지면 그 층은 처음부터
        MINE_TW_RUN.remove(userName);
        MINE_ICE_FROZEN.remove(userName); // [2026-10-28] 얼음 여왕 빙결도 끝
        MINE_DEBUFF.remove(userName); // [2026-10-28] 여왕개미 독/슬로우도 끝
        MINE_PSTEALTH.remove(userName); // [2026-10-30] 자객의 목걸이 은신도 끝
        MINE_STUN.remove(userName); // [2026-11-04] 붕대 스턴도 끝
        MINE_HEAL.remove(userName); // [2026-10-28] 재생의 장갑 회복도 끝
        MINE_KAK.remove(userName); // [2026-11-21] 각성도 끝
        MINE_ICE_THAWED.remove(userName);
        MINE_CHAOS_RUN.remove(userName); // [2026-11-17] 혼돈스러운 마계도 끝(몬스터는 위 mineSoloReset으로 사라진다 -- 귀환의 문이 열린다)
        if (mineWbZone(userName, now)) { // [2026-11-15] "체력이 다한 플레이어는 회색 화면으로 두다가 10초 뒤 부활" -- 월드보스 싸움터에선 그 자리에 쓰러져 있다가 일어난다
            MINE_WB_DOWN.put(userName, now + MINE_WB_DOWN_MS);
            return "10초 뒤 그 자리에서 다시";
        }
        // [2026-11-17] "죽으면 10초 뒤 부활을 전체 필드맵에서, 탑에선 각 10층마다 있는 거점 부활(혼돈스러운 마계에서는 부활하며 몬스터가 사라짐)" --
        //  탑 층에서 쓰러지면 아래쪽 10층 거점(10/20/30/40층 마을, 1~9층은 탑 입구)에서, 그 밖 모든 맵은 그 자리에 쓰러져 있다가 MINE_WB_DOWN_MS(10초) 뒤 일어난다.
        HashMap<String, Object> drow = dao.selectMineUser(userName);
        String dmap = drow == null ? "" : strVal(drow.get("MAP_ID"), "");
        int df = S5MineMap.towerFloor(dmap);
        if (df > 0 || S5MineMap.TOWER_GATE.equals(dmap)) {
            MINE_DIED_AT.put(userName, System.currentTimeMillis());
            int base = df > 0 ? (df - 1) / 10 * 10 : 0;
            S5MineMap bm = S5MineMap.get(base > 0 ? S5MineMap.towerId(base) : S5MineMap.TOWER_GATE);
            dao.updateMinePos(userName, bm.id, bm.spawnX, bm.spawnY, now);
            return base > 0 ? "탑 " + base + "층 거점에서" : "거대한 탑 입구에서";
        }
        if (drow != null && (items == null || items.getOrDefault(MINE_ITEM_HP_LOCK, 0) < MINE_HP_LOCK_MAX)) { MINE_WB_DOWN.put(userName, now + MINE_WB_DOWN_MS); return "10초 뒤 그 자리에서 다시"; }
        if (drow != null) return "🔒 최대 체력이 50%까지 잠긴 채 또 쓰러져 " + mineGoHome(userName, items, now); // [2026-11-19] "체력 50% 잠금 상태에서 또 죽으면 부활 대기 없이 마을로" (items = 이번에 쓰러지기 전 잠금)
        return mineGoHome(userName, items, now);
    }
    /** [2026-11-17] 마을(부활 거점)로 옮기기만 -- "마을로 가기" 버튼(언제나), 쓰러짐 처리가 위치를 못 읽었을 때. */
    String mineGoHome(String userName, Map<String, Integer> items, long now) {
        MINE_DIED_AT.put(userName, System.currentTimeMillis()); // [2026-11-17] 늦게 끝난 요청이 원래 자리로 되돌리지 않게
        MINE_WB_DOWN.remove(userName);
        boolean church = items.getOrDefault(MINE_ITEM_RESPAWN, 0) == 1;
        S5MineMap home = S5MineMap.get(church ? S5MineMap.CITY : S5MineMap.FIELD);
        int[] hp0 = church ? mineCityPlaza(home) : new int[] { home.spawnX, home.spawnY };
        dao.updateMinePos(userName, home.id, hp0[0], hp0[1], now);
        return church ? "왕도 아스테리아 광장에서" : "중앙 평원 마을에서";
    }

    // ---- [2026-10-25] 전투 중 몬스터 인식(어그로) -- "전투 시 자동 체력 회복을 막고, 안 때리더라도 일정 턴마다 몬스터가 플레이어를 공격,
    //  5칸 이상 벌어지면 인식이 풀리고 체력이 가득 차도록" 요청.
    //  때린 몬스터(와 2칸 안에 들어온 보스)는 그 유저를 인식한다(MINE_ENGAGE). 인식 중엔 체력이 저절로 차지 않고(mineHpNow),
    //  화면이 1초마다 mineCombatTick을 부르면 서버가 몬스터를 한 칸씩 쫓아오게 하고(MINE_CHASE_STEP_MS) 바로 옆이면 MINE_MOB_STRIKE_GAP_MS마다 때린다
    //  (때릴 때마다 받는 반격도 한 턴으로 친다). 몬스터와 MINE_AGGRO_RANGE칸 이상 벌어지면 인식이 풀리고, 인식이 모두 풀리면 그때부터 체력이 서서히 찬다(10-25 "가득" -> "서서히").
    //  몬스터를 쓰러뜨려 인식이 끝나면 그때부터 다시 저절로 찬다. 화면을 닫아 10초 넘게 소식이 없으면 인식 중이 아닌 것으로 본다.
    static final int MINE_AGGRO_RANGE = 5;
    /** [2026-10-29] "보스 공통으로 50칸 이상 멀어지면 어그로가 풀리고 체력이 서서히 회복" -- 인식이 풀린 보스는 마지막으로 맞은 지 5초 뒤부터 2분에 걸쳐 가득. */
    static final int MINE_BOSS_LEASH = 50;
    static final long MINE_BOSS_REGEN_DELAY_MS = 3_000L, MINE_BOSS_REGEN_FULL_MS = 20_000L; // [2026-11-04] "보스에게서 떨어지면 서서히 -> 빠르게 회복" (5초 뒤 120초 -> 3초 뒤 20초에 가득)
    // [2026-10-30] "맵에 아무도 없거나 200칸 안에 아무도 없으면 체력이 최대치로" -- 위치 알림(MINE_PRESENCE) 기준. 한동안(MINE_BOSS_ALONE_MS) 아무도 안 보다가 돌아와도 가득.
    static final int MINE_BOSS_ALONE_RANGE = 200;
    static final long MINE_BOSS_ALONE_MS = 20_000L;
    static void mineBossRegen(MobState s, int max, long now, S5MineMap map, S5MineMap.Mob mb) {
        if (mb.type == S5MineMobs.WB_BOSS_TYPE) { s.regenAt = now; s.nearAt = now; return; } // [2026-11-15] "월드보스는 체력 회복 없게" -- 모두 쓰러져 있거나 자리를 비워도 깎인 체력 그대로
        if (map.solo()) { s.regenAt = now; s.nearAt = now; return; } // [2026-11-15] 탑 수문장(1인 인스턴스) -- 층을 나가거나 쓰러지면 통째로 처음부터(mineSoloReset), 그 전엔 회복 없음
        if (s.deadUntil <= now) {
            boolean near = false;
            for (MinePresence p : MINE_PRESENCE.values()) {
                if (!map.id.equals(p.mapId) || now - p.at > MINE_PRESENCE_TTL_MS) continue;
                if (Math.abs(p.x - mb.x) + Math.abs(p.y - mb.y) <= MINE_BOSS_ALONE_RANGE || Math.abs(p.x - s.fx) + Math.abs(p.y - s.fy) <= MINE_BOSS_ALONE_RANGE) { near = true; break; }
            }
            if (!near && s.target != null && mineEngaged(s.target, now)) near = true; // 싸우는 중이면(위치 알림이 늦어도) 그대로
            boolean gone = s.nearAt > 0 && now - s.nearAt > MINE_BOSS_ALONE_MS;
            if (near) s.nearAt = now;
            if (!near || (gone && (s.target == null || !mineEngaged(s.target, now)))) { s.hp = max; s.target = null; s.regenAt = now; return; }
        }
        if (s.target != null || s.deadUntil > now || s.hp >= max) { s.regenAt = now; return; }
        long from = Math.max(s.regenAt, s.lastHitAt + MINE_BOSS_REGEN_DELAY_MS);
        if (now > from) { s.hp = (int) Math.min(max, s.hp + (now - from) * max / MINE_BOSS_REGEN_FULL_MS); s.regenAt = now; }
    }
    static final int MINE_BOSS_AGGRO_RANGE = 12;
    static final long MINE_MOB_STRIKE_GAP_MS = 1_500L;
    static final long MINE_CHASE_STEP_MS = 600L;
    static final long MINE_CHASE_STEP_MS_FOREST = 333L, MINE_CHASE_CATCH_MS = 2_000L; // [2026-11-05] 마물의 숲 입구 3배(1초 턴에 3칸), 늦은 턴은 2초어치까지 따라잡기
    static final long MINE_ENGAGE_TTL_MS = 10_000L;
    // [2026-10-28] 레벨 차이 선공 -- 몬스터 레벨이 내 레벨보다 MINE_PREEMPT_LV_GAP 이상 높으면 MINE_PREEMPT_RANGE칸 안에서 먼저 인식(7칸부터 풀림)
    static final int MINE_PREEMPT_LV_GAP = 20;
    static final int MINE_PREEMPT_RANGE = 6;

    /** [2026-11-05] 선공 인식 거리 -- 왕도 서쪽길 무리(aggro)는 더 넓게, 그 밖엔 MINE_PREEMPT_RANGE. */
    static int minePreemptRange(S5MineMobs.Type t) {
        return !t.boss && t.aggro > 0 ? t.aggro : MINE_PREEMPT_RANGE;
    }

    /** [2026-11-05] 밤 -- 한기 맵은 한기 +MINE_NIGHT_COLD단계, 마물의 숲 입구 몬스터(보스 제외)는 공격 MINE_NIGHT_ATK배 / 경험치 MINE_NIGHT_EXP배. */
    static final int MINE_NIGHT_COLD = 1;
    static final double MINE_NIGHT_ATK = 2.0, MINE_NIGHT_EXP = 1.5;
    static boolean mineNightWild(S5MineMap map, S5MineMobs.Type t, long now) {
        return S5MineMap.WESTROAD.equals(map.id) && !t.boss && S5MineMap.night(now);
    }
    static double mineNightAtk(S5MineMap map, S5MineMobs.Type t, long now) { return mineNightWild(map, t, now) ? MINE_NIGHT_ATK : 1.0; }
    static long mineNightExp(S5MineMap map, S5MineMobs.Type t, long exp, long now) {
        if (S5MineMap.WESTROAD.equals(map.id) && !t.boss) exp *= MINE_WEST_EXP; // [2026-11-06] "왕도 서쪽 전체적으로 경험치 2배(방금 한 저레벨 구간 포함)" -- 마물 전부(천둥 황소 제외), 밤 1.5배와 곱
        return mineNightWild(map, t, now) ? Math.round(exp * MINE_NIGHT_EXP) : exp;
    }
    static final int MINE_WEST_EXP = 2;

    static boolean mineMobPreempts(S5MineMobs.Type t, int lv) {
        return t.firstStrike || 1 + 4 * (t.tier - 1) - Math.min(MINE_LV_MAX, lv) >= MINE_PREEMPT_LV_GAP; // [2026-10-28] 설산 동굴 박쥐는 늘 선공
    }

    static final class MineEngage {
        volatile String mapVer;
        volatile long at;
        final java.util.concurrent.ConcurrentHashMap<Integer, Long> mobs = new java.util.concurrent.ConcurrentHashMap<>(); // 몬스터 id -> 이 유저를 마지막으로 때린 시각
    }
    static final java.util.concurrent.ConcurrentHashMap<String, MineEngage> MINE_ENGAGE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 지금 몬스터에게 인식돼 있는지(체력 자동 회복 멈춤). */
    static boolean mineEngaged(String userName, long now) {
        MineEngage e = userName == null ? null : MINE_ENGAGE.get(userName);
        return e != null && !e.mobs.isEmpty() && now - e.at <= MINE_ENGAGE_TTL_MS;
    }

    /** 몬스터 mobId가 이 유저를 인식(때렸을 때 / 보스에게 다가갔을 때). struckAt = 그 몬스터가 이 유저를 마지막으로 때린 시각. */
    static void mineEngage(String userName, S5MineMap map, int mobId, long now, long struckAt) {
        MineEngage e = MINE_ENGAGE.computeIfAbsent(userName, u -> new MineEngage());
        if (!map.version().equals(e.mapVer)) { e.mobs.clear(); e.mapVer = map.version(); }
        e.at = now;
        e.mobs.put(mobId, struckAt);
    }

    static void mineDisengageAll(String userName) {
        MINE_ENGAGE.remove(userName);
    }

    static long[] mineMobRow(int id, MobState s) {
        return new long[] { id, s.hp, s.deadUntil, s.fx, s.fy, s.frozenUntil, s.stealth ? 1 : 0, s.stunUntil, s.vulnUntil, s.vulnPct }; // [2026-10-28] 7번째 = 닌자 은신 중, [2026-11-07] 8번째 = 기절 끝 시각, [2026-11-08] 9/10번째 = 받는 피해 증가(끝 시각, %)
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
        int[] pos = minePos(row, map, mineWalkMode(items));
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb); // 인식 전이면 지금까지 회복한 만큼, 인식 중이면 회복 없이(mineHpNow)
        long wbDownT = mineWbDownUntil(userName, now); // [2026-11-15] 월드보스에 쓰러져 있는 10초 -- 맞지도 때리지도(용병도) 않는다
        if (wbDownT > 0) { out.put("ok", true); out.put("wbDown", wbDownT); out.put("fight", false); out.put("combat", mineCombatInfo(mineLv(row), Math.max(0, longOf(row.get("M_EXP"), 0L)), hp, now, false, lock, rb)); return out; }
        MineEngage e = MINE_ENGAGE.get(userName);
        if (e != null && !map.version().equals(e.mapVer)) { mineDisengageAll(userName); e = null; }
        // 보스 선공 -- 2칸 안에 들어오면 인식
        S5MineMap.Mob boss = mineBossOf(map);
        if (boss != null && !mineBossDead(boss.type) && (e == null || !e.mobs.containsKey(boss.id))) {
            S5MineMobs.Type bt = S5MineMobs.TYPES.get(boss.type);
            MobState bs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, boss.id), k -> new MobState(S5MineMobs.hp(bt, boss.rank)));
            int aggro = MINE_BOSS_AGGRO_RANGE; // [2026-10-28] "보스맵 구간은 인식범위 12칸, 한번 어그로가 잡히면 맵을 벗어나야만 풀리게" (예전 2칸, 대주교/얼음 여왕 4칸)
            synchronized (bs) {
                if (bs.deadUntil > 0 && bs.deadUntil <= now) bs.respawn(S5MineMobs.hp(bt, boss.rank)); // 다시 나타남(대주교 1시간, [2026-10-28] 닌자 두목 10분)
                if (bs.deadUntil <= now && bs.splitUntil <= now && !(boss.type == S5MineMobs.KRAKEN_TYPE && mineKrkLurking(bs, now))) { // [2026-10-28] 분신술 중엔 다가가도 먼저 덤비지 않는다(들키지 않게) // [2026-11-19] 숨은 크라켄은 지나가는 배를 알아보지 않는다(랜덤 조우 mineKrkMeet)
                    int near = -1;
                    for (int c : mineMobCells(boss, bs, now, map)) if (Math.abs(c % map.w - x) + Math.abs(c / map.w - y) <= aggro) near = c;
                    if (near >= 0) {
                        if (bs.frozenUntil <= now) { bs.fx = near % map.w; bs.fy = near / map.w; }
                        bs.frozenUntil = Math.max(now + MINE_MOB_FREEZE_MS, bs.splitUntil); // [2026-10-28] 분신술 자리 유지
                        if (bs.target == null) bs.target = userName;
                        if (boss.type == S5MineMobs.KRAKEN_TYPE) bs.kraken = true; // [2026-11-18]
                        mineEngage(userName, map, boss.id, now, 0L);
                        out.put("bossAggro", true);
                    }
                }
            }
            e = MINE_ENGAGE.get(userName);
        }
        int lv = mineLv(row);
        // [2026-10-28] "레벨 차이가 20 이상 나면 선공, 인식 범위 6칸" -- 몬스터 레벨(1+4(단계-1))이 내 레벨보다 20 이상 높으면 6칸 안에서 먼저 덤빈다(보스/수하 제외)
        for (S5MineMap.Mob mb : map.mobs()) {
            S5MineMobs.Type mt = S5MineMobs.TYPES.get(mb.type);
            if (mt.boss || mt.minion || !mineMobPreempts(mt, lv) || (e != null && e.mobs.containsKey(mb.id)) || mineHidden(map, mt, now) || S5MineMobs.diving(mt, mb.id, now)) continue; // [2026-11-18] 물속 몬스터는 먼저 덤비지 않는다
            MobState cur = MINE_MOB_STATE.get(mineMobKey(map, mb.id));
            int pr = minePreemptRange(mt); // [2026-11-05] 왕도 서쪽길 무리는 더 넓게
            if (cur == null && Math.abs(mb.x - x) + Math.abs(mb.y - y) > pr + 1) continue; // 상태가 없으면 집 칸 주변 1칸만 돌아다닌다(빠른 거르기)
            int near = -1;
            for (int c : mineMobCells(mb, cur, now, map)) if (Math.abs(c % map.w - x) + Math.abs(c / map.w - y) <= pr) near = c;
            if (near < 0) continue;
            MobState ms = cur != null ? cur : MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, mb.id), k -> new MobState(S5MineMobs.hp(mt, mb.rank)));
            synchronized (ms) {
                if (ms.deadUntil > now) continue;
                if (ms.frozenUntil <= now) { ms.fx = near % map.w; ms.fy = near / map.w; }
                ms.frozenUntil = now + MINE_MOB_FREEZE_MS;
                if (ms.target == null) ms.target = userName;
                mineEngage(userName, map, mb.id, now, 0L);
                if (!out.containsKey("preempt")) out.put("preempt", mt.name);
            }
            e = MINE_ENGAGE.get(userName);
        }
        int healed = mineHealDue(userName, now); // [2026-10-28] 재생의 장갑 -- 지난 1초만큼 회복
        boolean mercPreTick = mineMercPreMode(items); // [2026-11-07] 용병 선공 모드
        if ((e == null || e.mobs.isEmpty()) && mercPreTick && mineMercPreempt(userName, map, items, x, y, now, out)) e = MINE_ENGAGE.get(userName); // [2026-11-07] "선공 모드인데 공격을 안 해" -- 싸우는 몬스터가 없으면 전투 턴이 여기서 끝나 용병 차례가 오지 않았다
        if (e == null || e.mobs.isEmpty()) {
            mineDisengageAll(userName);
            out.put("ok", true);
            out.put("fight", false);
            if (healed > 0) { // 싸움이 끝난 뒤에도 4초 동안은 회복을 받아 저장
                HashMap<String, Object> hrow = dao.selectMineUserForUpdate(userName);
                if (hrow != null) {
                    int hlv = mineLv(hrow), hhp = mineHealTo(mineHpNow(hrow, now, lock, rb), healed, hlv, lock, rb);
                    long hexp = Math.max(0, longOf(hrow.get("M_EXP"), 0L));
                    dao.updateMineCombat(userName, hlv, hexp, hhp, now, 0);
                    out.put("combat", mineCombatInfo(hlv, hexp, hhp, now, false, lock, rb));
                    out.put("healed", healed);
                }
            }
            out.put("healUntil", mineHealUntil(userName));
            return out;
        }
        e.at = now;
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int dmgSum = 0, released = 0;
        // [2026-10-28] 얼음 여왕에게 얼어 있으면 -- 여왕의 인식이 거리와 상관없이 안 풀리고(쫓아온다), 여왕에게 맞으면 즉사
        boolean iceHeld = boss != null && boss.type == S5MineMobs.ICE_BOSS_TYPE && mineFrozen(userName, now) != null, shattered = false;
        if (iceHeld && !mineBossDead(boss.type)) { // [2026-10-30] "빙결 상태에서 닿으면 즉사가 안 된다" -- 언 사람은 거리와 상관없이 여왕이 노리고(인식 유지) 쫓아온다
            mineEngage(userName, map, boss.id, now, 0L);
            e = MINE_ENGAGE.get(userName);
            MobState qs = MINE_MOB_STATE.get(mineMobKey(map, boss.id));
            if (qs != null) synchronized (qs) { if (qs.deadUntil <= now) { qs.target = userName; qs.frozenUntil = Math.max(qs.frozenUntil, now + MINE_MOB_FREEZE_MS); } }
        }
        List<HashMap<String, Object>> hits = new ArrayList<>();
        List<long[]> mobs = new ArrayList<>();
        String killerName = null;
        int boatPct = 0; // [2026-11-18] 바다 위 -- 이번 턴에 배가 깎인 %
        // [2026-11-05] "검기를 제대로 쓰려면 몬스터 겹침이 필요" -- 쫓아오는 몬스터끼리 같은 칸에 겹쳐 설 수 있다(한 줄 검기에 여럿이 함께 맞게). 같은 날 잠깐 막았다가 되돌림.
        for (Integer id : new ArrayList<>(e.mobs.keySet())) {
            MobState s = id >= 0 && id < map.mobs().size() ? MINE_MOB_STATE.get(mineMobKey(map, id)) : null;
            if (s == null) { e.mobs.remove(id); continue; }
            S5MineMobs.Type type = S5MineMobs.TYPES.get(map.mobs().get(id).type);
            synchronized (s) {
                if (mineHidden(map, type, now)) { e.mobs.remove(id); continue; } // [2026-11-05] 천둥 황소가 나와 마물이 숨었다
                if (s.deadUntil > now) { e.mobs.remove(id); mobs.add(mineMobRow(id, s)); continue; } // 누가 쓰러뜨렸다
                if (s.frozenUntil <= now) { // 한동안 소식이 없어 돌아다니던 몬스터 -- 지금 칸에서 다시 붙잡는다
                    s.target = null;
                    int[] cs = mineMobCells(map.mobs().get(id), s, now, map);
                    s.fx = cs[cs.length / 2] % map.w; s.fy = cs[cs.length / 2] / map.w;
                }
                boolean harpyMoving = false, harpyBoss = map.mobs().get(id).type == S5MineMobs.HARPY_BOSS_TYPE; // [2026-11-11] 체력 절반부터 나무 패턴(숨음/옮김) -- 깃털을 더 자주
                if (map.mobs().get(id).type == S5MineMobs.WB_BOSS_TYPE) { int[] wp = mineWbPos(); s.fx = wp[0]; s.fy = wp[1]; } // [2026-11-16] 거룡 -- 옮겨 다니는 자리
                if (map.mobs().get(id).type == S5MineMobs.KRAKEN_TYPE) mineKrakenMove(map, s, id, now); // [2026-11-18] 크라켄 -- 길을 따라 돌고, 싸우는 중엔 잠수할 때 옮긴다
                if (harpyBoss) { int[] hz = mineHarpyNow(map, now); s.fx = hz[0]; s.fy = hz[1]; harpyMoving = hz[2] == 1 || hz[4] == 1; } // [2026-11-08] 하피 -- 나무 사이를 옮겨 다닌다
                int dist = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                if (type.boss) dist = mineFootDist(type, s, x, y); // [2026-10-29] 보스 2x2칸까지의 거리
                if ((type.boss || type.minion ? dist >= MINE_BOSS_LEASH : dist >= (mineMobPreempts(type, lv) ? minePreemptRange(type) + 1 : MINE_AGGRO_RANGE)) && !(!type.boss && !type.minion && mercPreTick && mineMercInView(userName, s.fx - x, s.fy - y))) { // [2026-11-07] 용병 선공으로 끌어들인 몬스터는 화면 안이면 안 풀린다 // [2026-10-29] 보스 = 50칸 // 선공 몬스터는 인식 범위(6칸) 밖에서 풀린다. [2026-10-28] 보스는 거리로는 안 풀린다(맵을 벗어나거나 쓰러져야)
                    e.mobs.remove(id);
                    released++;
                    if (userName.equals(s.target)) { s.target = null; s.frozenUntil = Math.max(now, s.splitUntil); } // 다시 제자리로 걸어간다([2026-10-28] 분신술 중엔 그 자리에)
                    mobs.add(mineMobRow(id, s));
                    continue;
                }
                if (s.target == null) s.target = userName;
                boolean stunned = s.stunUntil > now; // [2026-11-07] 일섬참 기절 -- 분신술처럼 제자리에서 안 때리고 기술도 안 쓴다
                boolean splitting = s.splitUntil > now || stunned; // [2026-10-28] 닌자 두목 분신술 중엔 제자리에서 안 때린다(자세로 찾게)
                // 쫓아온다(한 칸) -- [2026-10-28] 대주교는 제자리, 수하(fast)는 플레이어보다 3배쯤 빨리(초당 약 20칸) 여러 칸
                // [2026-11-05] "마물의 숲 입구 몬스터 이속 3배" -- 그 맵(보스 제외)은 칸 간격 1/3. "다가올 때 끊겨 보인다" -- 1초 턴이 조금 늦거나 한 번 건너뛰면
                //  예전엔 그동안 한 칸만 와서 멈칫했다. 이제 지난 시간만큼(최대 MINE_CHASE_CATCH_MS어치) 칸 수를 정한다.
                long stepGap = type.fast ? MINE_FAST_STEP_MS : S5MineMap.WESTROAD.equals(map.id) && !type.boss ? MINE_CHASE_STEP_MS_FOREST : MINE_CHASE_STEP_MS;
                if (!type.stay && !splitting && userName.equals(s.target) && dist > 1 && now - s.lastMoveAt >= stepGap) {
                    int steps = type.fast ? (int) Math.min(dist - 1, Math.min(25, (now - s.lastMoveAt) / stepGap))
                            : now - s.lastMoveAt > MINE_CHASE_CATCH_MS ? 1 // [2026-11-05] "다가올 때 순간이동" -- 막 쫓기 시작(오래 안 움직임)이면 한 칸만
                            : (int) Math.max(1, Math.min(dist - 1, (now - s.lastMoveAt) / stepGap));
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
                s.frozenUntil = Math.max(now + MINE_MOB_FREEZE_MS, s.splitUntil); // 인식 중엔 서버가 위치를 쥔다([2026-10-28] 분신술 자리는 끝날 때까지)
                if (!stunned && map.mobs().get(id).type == S5MineMobs.BORO_BOSS_TYPE) mineBoroPattern(map, s, x, y, S5MineMobs.hp(type, map.mobs().get(id).rank), now); // [2026-11-17] 보로네오
                if (map.mobs().get(id).type == S5MineMobs.KRAKEN_TYPE) mineKrakenPattern(map, s, type, id, x, y, S5MineMobs.hp(type, map.mobs().get(id).rank), now); // [2026-11-18] 크라켄(기절 중에도 화약통/지원 함선은 처리)
                if (type.boss && !stunned && S5MineMap.towerFloor(map.id) > 0 && S5MineMap.towerBossFloor(S5MineMap.towerFloor(map.id))) mineTwPattern(map, s, S5MineMap.towerFloor(map.id), x, y, S5MineMobs.hp(type, map.mobs().get(id).rank), now); // [2026-11-16] 탑 수문장 패턴
                if (type.demon && !stunned && mineDemonSkills(map, s, type, x, y, dist, now)) dist = Math.abs(s.fx - x) + Math.abs(s.fy - y); // [2026-11-16] 악마 -- 순간이동 / 지옥불
                boolean thunderBoss = map.mobs().get(id).type == S5MineMobs.THUNDER_BOSS_TYPE;
                if (thunderBoss && !stunned && dist <= MINE_BOSS_AGGRO_RANGE && hp - dmgSum > 0) { // [2026-11-05] 천둥 황소 -- 전광석화/전기창/밀쳐내기+장판
                    int tsd = mineThunderSkills(userName, map, s, map.mobs().get(id), x, y, dist, items, now, hits, out);
                    dmgSum += tsd;
                    if (tsd > 0) killerName = type.name; // [2026-11-06] "null에게 쓰러졌어요" -- 천둥 황소 기술/원거리에 쓰러지면 이름이 비어 있었다
                    if (out.get("knockX") != null) { x = (Integer) out.get("knockX"); y = (Integer) out.get("knockY"); }
                    dist = mineFootDist(type, s, x, y);
                }
                if (!thunderBoss && !stunned && type.ranged && (dist >= 2 || type.venom || harpyMoving || map.mobs().get(id).type == S5MineMobs.LAVA_BOSS_TYPE || map.mobs().get(id).type == S5MineMobs.PHARAOH_BOSS_TYPE || map.mobs().get(id).type == S5MineMobs.WB_BOSS_TYPE) && dist <= (harpyBoss ? MINE_HARPY_SHOT_RANGE : MINE_BOSS_AGGRO_RANGE) && hp - dmgSum > 0) { // [2026-11-11] "하피가 나무에 숨어서도 공격" -- 붙어 있어도, 더 멀리서도(MINE_HARPY_SHOT_RANGE) // [2026-10-28] 용암 거신 -- 가끔 불덩이([2026-10-30] 붙어 있어도 던져 발밑을 용암으로)
                    Long nx = MINE_RANGED_NEXT.get(userName);
                    if (nx == null) MINE_RANGED_NEXT.put(userName, now + MINE_RANGED_MIN_MS / 2); // 막 인식 -- 3초 뒤 첫 불덩이
                    else if (now >= nx) {
                        boolean pharaoh = map.mobs().get(id).type == S5MineMobs.PHARAOH_BOSS_TYPE;
                        // [2026-11-11] "하피나 미라가 공격 안 한다 -- 둘 다 원거리 공격을 해야" -- 예전엔 6~10초에 한 번(인식 3초 뒤 첫 발)이라 떨어져 싸우면 거의 안 때리는 것처럼 보였다.
                        //  하피 = 2.5초마다(옮기는 중 1.5초), 파라오 미라 = 3초마다 어둠 화살(붕대 스턴은 MINE_PHARAOH_STUN_GAP_MS에 한 번).
                        boolean wbBoss = map.mobs().get(id).type == S5MineMobs.WB_BOSS_TYPE; // [2026-11-15] 월드보스 -- 최대 체력 %
                        MINE_RANGED_NEXT.put(userName, now + (S5MineMobs.sqIndex(map.mobs().get(id).type) >= 0 ? MINE_SQ_SHOT_MS : wbBoss ? MINE_WB_SHOT_MS[mineWbStage()] : harpyBoss ? (harpyMoving && mineHarpyNow(map, now)[2] != 1 ? MINE_HARPY_SHOT_MS : MINE_HARPY_HIDE_SHOT_MS)
                                : pharaoh ? MINE_PHARAOH_SHOT_MS : MINE_RANGED_MIN_MS + RND.nextInt((int) MINE_RANGED_RAND_MS))); // [2026-11-08] 하피는 옮기는 중에 깃털을 연달아
                        int d = mineCoatHit(items, Math.max(1, (int) Math.round(S5MineMobs.atk(type, map.mobs().get(id).rank) * MINE_RANGED_MULT * (0.8 + RND.nextDouble() * 0.4))), type.elem()); // [2026-11-22] 원거리 = 몬스터 속성
                        if (wbBoss) d = d == 0 ? 0 : mineElHit(userName, Math.max(1, (int) Math.round(mineHpFull(lv, rb) * MINE_WB_HIT_PCT[mineWbStage()] * (mineWbBh() ? MINE_BH_DMG_MUL : 1) * (0.9 + RND.nextDouble() * 0.2))), type.elem(), false);
                        dmgSum += d;
                        killerName = type.name; // [2026-11-06] 원거리 공격(불덩이/독산 등)
                        HashMap<String, Object> h = new HashMap<>();
                        h.put("mobId", id);
                        h.put("dmg", d);
                        h.put("ranged", true);
                        h.put("fx", s.fx);
                        h.put("fy", s.fy);
                        if (wbBoss) h.put("dark", true); // [2026-11-15] 거룡의 어둠 숨결(보라 화살 그림)
                        hits.add(h);
                        if (type.venom) { // [2026-10-28] 여왕개미 독산 -- 슬로우 + 독
                            Debuff db = MINE_DEBUFF.computeIfAbsent(userName, u -> new Debuff());
                            db.slowUntil = now + MINE_SLOW_MS;
                            if (db.poisonUntil <= now) db.poisonAt = now;
                            db.poisonUntil = now + MINE_POISON_MS;
                            h.put("venom", true);
                            out.put("slowUntil", db.slowUntil);
                            out.put("poisonUntil", db.poisonUntil);
                        } else if (pharaoh && now - MINE_PHARAOH_STUN_AT.getOrDefault(userName, 0L) < MINE_PHARAOH_STUN_GAP_MS) { // [2026-11-11] 붕대 사이엔 어둠 화살(스턴 없음)
                            h.put("dark", true);
                        } else if (pharaoh) { // [2026-11-04] 붕대 감기 -- 스턴
                            MINE_PHARAOH_STUN_AT.put(userName, now);
                            MINE_STUN.put(userName, now + MINE_STUN_MS);
                            h.put("stun", true);
                            out.put("stunUntil", now + MINE_STUN_MS);
                        } else if (S5MineMap.ISLE7.equals(map.id)) {
                            mineLavaCells(map, now); // 맵 버전 맞추기
                            for (int ly = y - 1; ly <= y + 1; ly++) for (int lx = x - 1; lx <= x + 1; lx++) // [2026-10-29] "용암 범위 3x3" (예전 서 있던 한 칸)
                                if ((MINE_LAVA.size() < MINE_LAVA_MAX || MINE_LAVA.containsKey(ly * map.w + lx)) && (map.walkable(lx, ly) || (map.inside(lx, ly) && map.terrainAt(lx, ly) == '~'))) MINE_LAVA.put(ly * map.w + lx, now); // [2026-11-05] 물 위에도(MINE_LAVA_WATER_MS 뒤 식음) // [2026-10-30] 야자수 그늘(안전지대)에도 깔린다
                            h.put("lava", true);
                        }
                        killerName = type.name;
                    }
                }
                Long last = e.mobs.get(id);
                boolean shatterNow = iceHeld && id == boss.id; // [2026-10-30] 언 사람에게 닿는 순간(공격 간격 대기 없이) 즉사
                if (dist <= 1 && !splitting && !type.venom && !S5MineMobs.diving(type, id, now) && (shatterNow || last == null || now - last >= (S5MineMobs.sqIndex(map.mobs().get(id).type) >= 0 ? MINE_SQ_HIT_MS : MINE_MOB_STRIKE_GAP_MS)) && hp - dmgSum > 0) { // [2026-11-23] 최강다람쥐 = 1초에 7번 // [2026-11-18] 물속에선 안 때린다
                    int d = mineCoatHit(items, Math.max(1, (int) Math.round(S5MineMobs.atk(type, map.mobs().get(id).rank) * mineNightAtk(map, type, now) * mineBoroMul(map.mobs().get(id).type, now) * (0.8 + RND.nextDouble() * 0.4)))); // [2026-11-05] 마물의 숲 밤 2배 // [2026-10-29] 개체 단계, [2026-10-26] 방한 외투 = 2배([2026-10-28] 갑옷 1.5배)
                    if (map.mobs().get(id).type == S5MineMobs.WB_BOSS_TYPE) d = Math.max(1, (int) Math.round(mineHpFull(lv, rb) * MINE_WB_MELEE_PCT * (mineWbBh() ? MINE_BH_DMG_MUL : 1))); // [2026-11-15] 월드보스 근접 = 최대 체력 %
                    int sqHitsN = 1; // [2026-11-23] 최강다람쥐 -- 격투가: 1초에 7번 "실제로 7번 타격"(전투 턴 사이에 친 만큼 따로따로 -- 아래에서 타격을 더 붙인다, 화면은 주먹질 + 숫자가 위로 쫙)
                    if (S5MineMobs.sqIndex(map.mobs().get(id).type) >= 0) { sqHitsN = last == null ? 1 : (int) Math.max(1, Math.min(7, (now - last) / MINE_SQ_HIT_MS)); out.put("sqPunch", sqHitsN); }
                    boolean krkSwing = map.mobs().get(id).type == S5MineMobs.KRAKEN_TYPE; // [2026-11-19] 크라켄 다리 휘두르기 -- 반격이 없어진 몫까지 x MINE_KRK_SWING_MULT(사용자: 3배), 화면이 촉수를 그린다
                    if (krkSwing) d *= MINE_KRK_SWING_MULT;
                    boolean twHeavy = false, twStun = false;
                    int twF = type.boss ? S5MineMap.towerFloor(map.id) : 0;
                    if (twF > 0) { // [2026-11-15] 탑 수문장 = 시즌5 보스 패턴 -- 강타(시즌5 보스 반격 x1.6: 네 번째마다, 29층부터 세 번째마다), 29층부터 기절(시즌5 구역 3+ 보스의 기절 20%)
                        int[] ts = mineTwStrike(userName, twF, s, d, now);
                        d = ts[0]; twHeavy = ts[1] > 0; twStun = ts[2] > 0;
                    }
                    boolean x2 = s.stealth && map.mobs().get(id).type != S5MineMobs.SHRINE_BOSS_TYPE; // [2026-10-28] 은신했던 닌자의 기습 -- 2배(닌자 두목은 은신을 유지한 채 보통 세기)
                    if (x2) { d *= MINE_NINJA_STEALTH_MULT; s.stealth = false; }
                    if (iceHeld && id == boss.id) shattered = true; // [2026-11-18] 언 채로 맞으면 최대 체력 50%(아래에서 한 번에)
                    boolean dodged = !(iceHeld && id == boss.id) && minePStealthDodge(userName, now); // [2026-10-30] 자객의 목걸이 은신 -- 일반 공격 회피
                    if (dodged) d = 0;
                    dmgSum += d;
                    e.mobs.put(id, now);
                    if (map.boat() && type.sea && d > 0) boatPct += type.boss ? MINE_BOAT_KRK_PCT : map.mobs().get(id).type == S5MineMobs.SEA_WHALE ? MINE_BOAT_WHALE_PCT : MINE_BOAT_HIT_PCT; // [2026-11-18] 배도 깎인다
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("mobId", id);
                    h.put("dmg", d);
                    if (dodged) h.put("dodge", true);
                    if (x2) h.put("x2", true);
                    if (twHeavy && !dodged) h.put("heavy", true);
                    if (twStun && !dodged) { mineTwStun(userName, now, out); h.put("stun", true); }
                    if (krkSwing) { h.put("krk", true); h.put("kx", s.fx + 2); h.put("ky", s.fy + 2); }
                    hits.add(h);
                    if (sqHitsN > 1) { // [2026-11-23] 최강다람쥐 -- 나머지 주먹(한 대마다 피해 따로, ±15%)
                        h.put("sq", 0);
                        for (int q = 1; q < sqHitsN; q++) {
                            int dq = dodged ? 0 : Math.max(1, (int) Math.round(d * (0.85 + RND.nextDouble() * 0.3)));
                            dmgSum += dq;
                            HashMap<String, Object> hq = new HashMap<>();
                            hq.put("mobId", id); hq.put("dmg", dq); hq.put("sq", q);
                            if (dodged) hq.put("dodge", true);
                            hits.add(hq);
                        }
                    }
                    killerName = type.name;
                }
                mobs.add(mineMobRow(id, s));
            }
        }
        if (hp - dmgSum > 0) mineBossReach(userName, map, e, x, y, now, out);
        if (hp - dmgSum > 0) mineSqTurn(userName, map, e, x, y, now, out); // [2026-11-23] 최강다람쥐 -- 전광석화 / 벼락 몰아치기 / 전기충격 // [2026-11-23] 물·빛 보스 체력 50% 이하 -- 끌어당기기 / 원거리 일격(베히모스는 원거리 일격 늘)
        if (S5MineMap.CASTLE.equals(map.id) && mineWbOn(now) && hp - dmgSum > 0) { // [2026-11-15] 거룡 화염 장판 / 포효
            int wbz = mineWbPattern(userName, x, y, lv, rb, now, hits, out);
            if (wbz > 0) { dmgSum += wbz; killerName = mineWbName(); }
        }
        if (hp - dmgSum > 0 && !MINE_ZONES.isEmpty()) { // [2026-11-16] 탑 수문장 / 악마의 장판
            int zd = mineZoneHit(userName, map, x, y, lv, rb, now, hits, out);
            if (zd > 0) { dmgSum += zd; if (killerName == null) killerName = "장판"; }
        }
        if (map.boat() && map.terrainAt(x, y) == '~') { // [2026-11-18] 바다 위 -- 몬스터 한 대 1%(고래 2%, 크라켄 3%), 촉수 내려찍기 25%, 물보라 5%. 0이 되면 배가 부서져 마을로(mineBoatHit). 등대 섬 위(배에서 내림)는 배가 안 맞는다
            for (HashMap<String, Object> h : hits) if (Boolean.TRUE.equals(h.get("zone"))) { int zk = intVal(h.get("zk"), -1); if (zk == 11) boatPct += MINE_BOAT_SMASH_PCT; else if (zk == 12) boatPct += MINE_BOAT_SPLASH_PCT; }
            if (boatPct > 0 && items.getOrDefault(S5MineMap.BOAT_ITEM, 0) > 0) {
                long left = mineBoatHit(userName, items, mineBoatHpMax(items) * boatPct / 100, out);
                out.put("boatHp", Math.max(0, left));
                out.put("boatHit", boatPct);
            }
        }
        HashMap<String, Object> mercLockRow = null; // [2026-11-18] DB 점검 4번 -- 여기서 잠근 행을 아래 저장 때 그대로 쓴다(같은 행 FOR UPDATE 두 번)
        if (mineMercActive(items).length > 0) {
            // [2026-11-18] DB 점검 -- 용병이 있으면 용병 차례(처치 드랍/장비/경험치를 쓴다) 전에 유저 행을 먼저 잠근다. 공격 요청과 잠금 순서를 맞추고(교착 방지),
            //  잠근 뒤 용병 기록만 다시 읽어(턴 처음에 읽은 값은 그 사이 공격이 올린 용병 경험치보다 옛 값일 수 있다) 덮어쓰기를 막는다.
            mercLockRow = dao.selectMineUserForUpdate(userName);
            for (HashMap<String, Object> mr : dao.selectMineMercRows(userName)) items.put(strVal(mr.get("ITEM_CODE"), ""), intVal(mr.get("ITEM_LEVEL"), 0));
        }
        long[] mercR = mineMercTurn(userName, map, items, x, y, now, e, out); // [2026-11-05] 용병 -- {처치 수, 경험치}
        long mercExp = mercR[1];
        Debuff pdb = MINE_DEBUFF.get(userName); // [2026-10-28] 여왕개미 독 -- 전투 턴마다 지난 시간만큼
        if (pdb != null && pdb.poisonUntil > pdb.poisonAt && hp - dmgSum > 0) {
            long until = Math.min(now, pdb.poisonUntil), el = Math.min(2_000L, until - pdb.poisonAt);
            pdb.poisonAt = until;
            if (el > 0) {
                int pd = Math.max(1, (int) (mineHpCapN(mineLv(row), lock, rb) * MINE_POISON_PCT / 100 * el / 1000.0));
                dmgSum += pd;
                HashMap<String, Object> h = new HashMap<>();
                h.put("dmg", pd);
                h.put("poison", true);
                hits.add(h);
                killerName = "여왕개미의 독";
            }
            out.put("poisonUntil", pdb.poisonUntil);
        }
        if (boss != null && boss.type == S5MineMobs.RAID_BOSS_TYPE && e.mobs.containsKey(boss.id)) mineRaidSummon(userName, map, boss, now, out, mobs); // [2026-10-28]
        if (boss != null && boss.type == S5MineMobs.BORO_BOSS_TYPE && e.mobs.containsKey(boss.id)) mineBoroTurn(userName, map, boss, now, out, mobs); // [2026-11-17] 보로네오 -- 악마 소환 + 대사
        if (boss != null && boss.type == S5MineMobs.ICE_BOSS_TYPE && e.mobs.containsKey(boss.id)) mineIceAwakeUntil = now + MINE_ICE_AWAKE_MS; // [2026-10-28] 얼음 여왕이 깨어 있다(빙결)
        out.put("ok", true);
        out.put("hits", hits);
        out.put("mobs", mobs);
        out.put("pStealth", minePStealthOf(userName, now)); // [2026-10-30] 자객의 목걸이 -- 남은 회피/끝나는 시각(없으면 null)
        if (S5MineMap.ISLE7.equals(map.id)) out.put("lava", mineLavaCells(map, now)); // [2026-10-28] 용암 칸
        String rwMsg = MINE_SHRINE_REWARD_MSG.remove(userName); // [2026-10-28] 다른 사람이 닌자 두목을 쓰러뜨렸을 때 함께 싸운 보상 소식
        if (rwMsg != null) { out.put("rewardMsg", rwMsg); out.put("ores", mineOreList(userName)); }
        boolean fight = !e.mobs.isEmpty();
        if (out.containsKey("boatWreck")) fight = false; // [2026-11-18] 배가 부서져 마을로 떠밀려 갔다
        if (dmgSum == 0 && healed == 0 && mercExp == 0 && fight && !out.containsKey("bossAggro") && !out.containsKey("preempt")) { out.put("fight", true); out.put("combat", mineCombatInfo(lv, exp, hp, now, true, lock, rb)); return out; } // 방금 인식됐으면 그때까지 찬 체력을 저장
        int iceHit = 0; // [2026-11-18] 빙결 상태에서 맞으면(어떤 공격이든) 즉사 대신 최대 체력 50% + 얼음이 깨진다
        if (dmgSum > 0 && (shattered || mineFrozen(userName, now) != null)) {
            iceHit = (int) Math.round(mineHpCapN(lv, lock, rb) * MINE_ICE_HIT_PCT / 100.0);
            dmgSum = Math.max(dmgSum, iceHit);
            shattered = true;
            if (MINE_ICE_FROZEN.remove(userName) != null) MINE_ICE_THAWED.remove(userName);
        }
        boolean save = mineHpFlushDue(row, now) || hp + healed - dmgSum <= 0 || mercExp > 0; // [2026-11-05] 용병이 잡은 경험치는 바로 저장 // [2026-11-03] 체력만 바뀌면 메모리에(MINE_HP_VIRT), 쓰러지거나 15초마다 DB
        HashMap<String, Object> lrow = save ? (mercLockRow != null ? mercLockRow : dao.selectMineUserForUpdate(userName)) : null; // 쓰기 전에 같은 유저 요청 직렬화(용병 턴에서 이미 잠갔으면 그 행)
        if (lrow != null) { // [2026-10-28] 잠근 뒤 다시 읽는다 -- 그 사이 들어온 공격(처치/레벨업/반격)이 바꾼 레벨/경험치/체력을 옛 값으로 덮어쓰지 않게
            lv = mineLv(lrow);
            exp = Math.max(0, longOf(lrow.get("M_EXP"), 0L));
            hp = mineHpNow(lrow, now, lock, rb);
        }
        if (mercExp > 0) { // [2026-11-05] 용병이 쓰러뜨린 몬스터 경험치 -- 내 것으로
            mineMercShare(userName, items, mercExp, lv, mercR[0], out); // [2026-11-06] 용병 경험치(용병 처치 -- 몬스터 레벨 대신 내 레벨로, 처치마다 최소 1)
            long[] mg = mineAuraExp(userName, lv, exp, hp, lock, rb, mercExp, false, out);
            lv = (int) mg[0]; exp = mg[1]; hp = (int) mg[2]; lock = (int) mg[3];
            String mkx = out.get("merc") instanceof Map ? "merc" : out.get("merc2") instanceof Map ? "merc2" : "merc3"; // [2026-11-18] 두 용병이 같이 잡아도 "내 경험치 +"는 한 번(첫 칸에), [2026-11-23] 세 칸
            for (String mk : new String[] { "merc2", "merc3" }) if (!mk.equals(mkx) && out.get(mk) instanceof Map) ((Map<?, ?>) out.get(mk)).remove("kexp");
            Object mo2 = out.get(mkx);
            if (mo2 instanceof Map) { @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) mo2; mm.put("kexp", mg[5]); } // [2026-11-07] 화면의 "내 경험치 +"는 실제로 오른 만큼(가득 차 있으면 0 -- 안 띄움)
            if (mg[4] > 0) out.put("levelUp", (int) mg[4]);
        }
        if (healed > 0) { hp = mineHealTo(hp, healed, lv, lock, rb); out.put("healed", healed); } // [2026-10-28] 재생의 장갑
        if (dmgSum > 0 && mineBerserkInvul(userName, now)) { dmgSum = 0; out.put("berserkInv", true); } // [2026-11-20] 광전사 불굴 -- 무적 중
        if (dmgSum > 0) {
            if (mineVeinBreak(userName)) out.put("veinBroken", true); // [2026-10-28] 설산 동굴 -- 맞으면 연속 채광이 끊긴다
            boolean wyrm = mineWyrmSkinOn(items); int hp0 = hp; // [2026-11-23] 거룡의 피부조각 -- 받는 피해 -20%, 즉사 방지
            if (wyrm) dmgSum = Math.max(1, (int) Math.round(dmgSum * MINE_WYRM_SKIN_TAKE));
            if (MINE_ITEM_REGEN_GLOVES.equals(mineGloveCode(items))) mineHealAdd(userName, (int) Math.round(dmgSum * mineRegenPct(items)), now); // [2026-10-28] 재생의 장갑
            hp -= dmgSum;
            if (hp <= 0 && wyrm && hp0 > 1 && mineWyrmSave(userName, now, out)) hp = 1; // 광전사 불굴보다 먼저
            if (hp <= 0 && mineBerserkSave(userName, items, now, out)) hp = 1; // [2026-11-20] 광전사 불굴
            if (MINE_WB_NAME.equals(killerName) && mineWbBh()) killerName = MINE_BH_NAME; // [2026-11-23] "베히모스 이름이 심연의 거룡으로 나와" -- 몬스터 칸은 거룡 것을 같이 쓴다
            if (shattered && hp > 0) { out.put("iceBroke", dmgSum); out.put("message", "🧊 얼어붙은 채로 " + (killerName == null ? "" : killerName + "의 ") + "일격을 맞아 얼음이 깨졌어요! (-" + String.format("%,d", dmgSum) + ")"); }
            if (hp <= 0) {
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCapN(lv, lock, rb);
                mineDisengageAll(userName);
                fight = false;
                out.put("died", true);
                out.put("message", (shattered ? "🧊 얼어붙은 채로 " + (killerName == null ? "" : killerName + "의 ") + "일격을 맞아 산산조각 났어요... " : killerName == null ? "💫 쓰러졌어요... " : "💫 " + killerName + "에게 쓰러졌어요... ") + mineRespawnMsg(userName, items, now) + mineLockText(out));
                if (shattered) out.put("shattered", true);
                long wbd = mineWbDownUntil(userName, now); // [2026-11-15] 월드보스 -- 화면 회색 + 카운트
                if (wbd > 0) out.put("wbDown", wbd);
            }
        }
        if (!fight && !out.containsKey("died")) {
            mineDisengageAll(userName);
            if (released > 0) { // [2026-10-25] "인식이 풀리면 서서히 회복" -- 바로 가득 채우지 않고 지금부터 다시 저절로 찬다(아래 저장 시각부터)
                out.put("released", true);
                out.put("message", "🏃 몬스터가 더는 쫓아오지 않아요. 숨을 고르니 체력이 서서히 돌아와요.");
            }
        }
        if (save || out.containsKey("died")) {
            if (!out.containsKey("died") && dmgSum > 0 && !mineJustDied(userName)) dao.updateMinePos(userName, map.id, x, y, now); // [2026-11-17] 방금 쓰러졌으면 안 쓴다
            dao.updateMineCombat(userName, lv, exp, hp, now, (int) mercR[0]); // [2026-11-05] 용병 처치 수도
            MINE_HP_VIRT.remove(userName);
        } else mineHpKeep(userName, row, hp, now);
        out.put("fight", fight);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, fight, lock, rb));
        out.put("healUntil", mineHealUntil(userName)); // [2026-10-28] 재생의 장갑
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
    //  보상: 15% 타락한 대주교의 인장 1 + 50% 다이아몬드 2 + 30% 별빛석(성유물 조각은 없앴다).
    static final long MINE_FAST_STEP_MS = 50L;
    static final long MINE_RAID_AWAKE_MS = 15_000L;
    static final long MINE_RAID_SUMMON_MS = 20_000L;
    static final int MINE_RAID_SUMMON_N = 2;
    static final long MINE_RAID_FIRE_MS = 12_000L, MINE_RAID_FIRE_OFF = 6_000L;
    static final long MINE_RAID_GAS_MS = 30_000L, MINE_RAID_GAS_OFF = 15_000L;
    static final int MINE_RAID_FIRE_RANGE = 12;
    static volatile long mineRaidAwakeUntil = 0;
    static volatile long mineRaidLastSummon = 0;

    /** period마다 offset에 오는 일(마법) 중 after 다음 시각. */
    static long mineRaidNext(long period, long offset, long after) {
        return ((after - offset) / period + 1) * period + offset;
    }

    /** 수하 소환 -- 대주교와 싸우는 유저(userName)에게 잠든 수하 자리 중 MINE_RAID_SUMMON_N개를 깨운다(20초마다). */
    void mineRaidSummon(String userName, S5MineMap map, S5MineMap.Mob boss, long now, HashMap<String, Object> out, List<long[]> rows) {
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
            // [2026-10-30] "소환되자마자 사라진다 -- 하수인은 보스와 같은 인식 범위" -- 대주교 둘레(보스 인식 12칸) 유저 모두를 인식(아무도 안 싸우면 물러나던 것)
            for (java.util.Map.Entry<String, MinePresence> pe : MINE_PRESENCE.entrySet()) {
                MinePresence o = pe.getValue();
                if (pe.getKey().equals(userName) || !map.id.equals(o.mapId) || now - o.at > MINE_PRESENCE_TTL_MS) continue;
                if (Math.abs(o.x - boss.x) + Math.abs(o.y - boss.y) <= MINE_BOSS_AGGRO_RANGE) mineEngage(pe.getKey(), map, mb.id, now, now - MINE_MOB_STRIKE_GAP_MS + 700);
            }
            rows.add(mineMobRow(mb.id, ns));
            n++;
        }
        if (n > 0) { out.put("summoned", n); out.put("message", "👿 타락한 대주교가 수하 " + n + "명을 불러냈어요! 엄청 빠르게 쫓아와요!"); }
    }

    /** 타락한 대주교 보상: 15% 인장 1 + 50% 다이아몬드 2 + 30% 별빛석 1. [2026-10-28] 성유물 조각은 없앴고("성유물 조각은 없애자"),
     *  인장은 처음 반드시였다가 같은 날 "확률 드랍 15% 정도". */
    static final int MINE_SEAL_PCT = 15;

    List<HashMap<String, Object>> mineRaidDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        // [2026-11-18] "대주교가 주는 증표를 어둠의 심장으로, 드랍률 100%" -- 인장(이제 어둠의 증표)은 더 안 나온다
        dao.addUserOre(userName, S5MineMap.DARK_HEART_CODE, 1);
        drops.add(mineDrop(S5MineMap.DARK_HEART_GRADE, S5MineMap.DARK_HEART_NAME, 1));
        msg.append(" · 🖤 ").append(S5MineMap.DARK_HEART_NAME).append(" 1개!!");
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

    // ---- [2026-10-28] 설산 봉우리 -- 얼음 여왕(보스) ----
    // "보스는 95레벨, 잡으면 얼음의 심장. 강하고 주기적으로 플레이어 한 명씩 얼린다. 얼어 있는 플레이어를 보스가 공격하면 즉사.
    //  얼면 다른 플레이어가 근처로 와야만 풀린다(얼음땡)" 요청.
    //  얼음 여왕(24단계 보스, 화면 Lv.95, 체력 x1.5 / 공격 x1.1)은 4칸 안에 들어오면 먼저 인식하고 쫓아온다. 누가 여왕과 싸우는 동안(마지막 전투 턴/공격 + 15초 = 깨어 있음):
    //   - 빙결: 깨어난 뒤 8초, 그 뒤 18초마다 봉우리에 있는(최근 3초 위치 알림, 모닥불 밖, 아직 안 언) 플레이어 중 한 명을 그 자리에 얼린다(최대 15초).
    //     언 동안은 움직이기/캐기/공격/포탈/마을로를 서버가 막고, 여왕은 언 사람을 노려 쫓아간다(멀어도 인식이 안 풀림).
    //   - 여왕이 언 사람을 때리면 즉사(남은 체력 전부 -- 몬스터에게 쓰러진 것과 같아 최대 체력 잠금 +1). [2026-11-18] -> 최대 체력 50% + 얼음이 깨진다(MINE_ICE_HIT_PCT).
    //   - 얼지 않은 다른 플레이어가 언 사람 2칸 안(위치 알림 기준)에 오면 풀린다(얼음땡). 혼자면 15초 뒤 저절로 녹는다(갇히지 않게).
    //   판정은 봉우리에 있는 모든 화면이 1초마다 부르는 환경 턴(mineColdTick)에서 한다. 언 동안엔 한기/눈바람 피해가 없다.
    //  보스 공통: 맵 주기마다 한 번(MINE_ICE_KILLED = "주기|유저"). 보상: 얼음의 심장 1(반드시) + 50% 다이아몬드 2 + 40% 별빛석 1.
    static final String MINE_ICE_KEY = "MINE_ICE_KILLED";
    static final long MINE_ICE_AWAKE_MS = 15_000L;
    static final long MINE_ICE_FIRST_MS = 8_000L, MINE_ICE_PERIOD_MS = 18_000L, MINE_ICE_FREEZE_MS = 15_000L;
    static final int MINE_ICE_RESCUE_RANGE = 2;
    static final long MINE_ICE_FRESH_MS = 6_000L; // [2026-11-04] 3초 -> 6초(통신이 느린 사람도 빙결 대상)
    // [2026-11-18] "설산 보스 얼음 패턴 맞고 보스가 붙으면 50퍼센트 달게" -- 언 채로 맞으면 즉사 대신 최대 체력의 MINE_ICE_HIT_PCT%(맞은 피해가 더 크면 그만큼), 얼음은 그 일격에 깨진다.
    static final int MINE_ICE_HIT_PCT = 50;
    static final String MINE_FROZEN_BLOCK = "🧊 얼어붙어서 꼼짝할 수 없어요! 동료가 곁(2칸)에 와야만 풀려요.";
    static volatile long mineIceAwakeUntil = 0;
    static volatile long mineIceNextFreeze = 0;
    static final Object MINE_ICE_LOCK = new Object();

    static final class IceFreeze {
        final String mapVer;
        final int x, y;
        final long until;
        IceFreeze(String mapVer, int x, int y, long until) { this.mapVer = mapVer; this.x = x; this.y = y; this.until = until; }
    }
    /** 언 유저 -> 얼음(맵 버전, 언 칸, 녹는 시각). */
    static final java.util.concurrent.ConcurrentHashMap<String, IceFreeze> MINE_ICE_FROZEN = new java.util.concurrent.ConcurrentHashMap<>();
    /** 다음 환경 턴에 화면에 알릴 일: rescued(동료가 풀어 줌) / melted(저절로 녹음) / saved(내가 동료를 풀어 줌). */
    static final java.util.concurrent.ConcurrentHashMap<String, String> MINE_ICE_THAWED = new java.util.concurrent.ConcurrentHashMap<>();

    boolean mineIceBossDead() {
        return mineBossDead(S5MineMobs.ICE_BOSS_TYPE); // [2026-11-13] 소환
    }

    /** 지금 얼어 있으면 그 얼음(없으면 null). 다 녹았거나 맵이 바뀌었으면 지운다. */
    static IceFreeze mineFrozen(String userName, long now) {
        IceFreeze f = userName == null ? null : MINE_ICE_FROZEN.get(userName);
        if (f == null) return null;
        if ((f.until <= now && mineIceAwakeUntil <= now) || !f.mapVer.equals(S5MineMap.get(S5MineMap.PEAK).version())) { // [2026-10-28] "빙결이 안 풀리게" -- 여왕이 깨어 있는 동안은 시간이 지나도 안 녹는다
            if (MINE_ICE_FROZEN.remove(userName, f)) MINE_ICE_THAWED.put(userName, "melted");
            return null;
        }
        return f;
    }

    /** [2026-11-04] "빙결에 걸렸을 때 움직이거나 공격해서 일정 수치를 채우면 풀리게, 적어도 20번" -- 화면이 몸부림 n번(0.12초에 한 번까지)을 세어
     *  다 차면 부른다. 얼고 나서 n x 0.1초 넘게 지났을 때만 풀린다(너무 빠르면 거절 -- 화면이 조금 더 센다). */
    static final int MINE_ICE_STRUGGLE = 20;
    @Override
    public HashMap<String, Object> mineIceStruggle(String userName, int n) {
        HashMap<String, Object> out = new HashMap<>();
        long now = System.currentTimeMillis();
        IceFreeze f = mineFrozen(userName, now);
        if (f == null) { out.put("ok", true); return out; } // 이미 풀렸다
        long since = now - (f.until - MINE_ICE_FREEZE_MS);
        if (n < MINE_ICE_STRUGGLE || since < MINE_ICE_STRUGGLE * 100L) { out.put("ok", false); return out; }
        MINE_ICE_FROZEN.remove(userName, f);
        out.put("ok", true);
        return out;
    }

    /** 얼음을 모두 녹인다(여왕이 쓰러졌을 때). */
    static void mineIceThawAll() {
        for (String fu : new ArrayList<>(MINE_ICE_FROZEN.keySet())) if (MINE_ICE_FROZEN.remove(fu) != null) MINE_ICE_THAWED.put(fu, "melted");
    }

    /** 설산 봉우리 환경 턴(mineColdTick 안) -- 얼음땡 판정, 빙결 시각이 되면 한 명 얼리기, 내 얼음 상태. 내가 얼어 있으면 true.
     *  결과: iceAwake, nextFreezeAt(깨어 있을 때), frozen {x, y, until}, thawed(rescued/melted/saved), froze(이번에 누가 얼었음). */
    boolean mineIceTick(String userName, S5MineMap map, long now, HashMap<String, Object> out) {
        S5MineMap.Mob boss = mineBossOf(map);
        MobState bs = boss == null ? null : MINE_MOB_STATE.get(mineMobKey(map, boss.id));
        boolean awake = boss != null && !mineBossDead(boss.type) && (bs == null || bs.deadUntil <= now) && mineIceAwakeUntil > now;
        out.put("iceAwake", awake);
        // 얼음땡 -- 언 사람 2칸 안에 얼지 않은 다른 사람이 있으면 풀린다
        for (String fu : new ArrayList<>(MINE_ICE_FROZEN.keySet())) {
            IceFreeze f = mineFrozen(fu, now);
            if (f == null) continue;
            for (java.util.Map.Entry<String, MinePresence> pe : MINE_PRESENCE.entrySet()) {
                MinePresence o = pe.getValue();
                if (pe.getKey().equals(fu) || !map.id.equals(o.mapId) || now - o.at > MINE_ICE_FRESH_MS) continue;
                if (Math.abs(o.x - f.x) + Math.abs(o.y - f.y) > MINE_ICE_RESCUE_RANGE || mineFrozen(pe.getKey(), now) != null) continue;
                if (MINE_ICE_FROZEN.remove(fu, f)) {
                    MINE_ICE_THAWED.put(fu, "rescued");
                    MINE_ICE_THAWED.put(pe.getKey(), "saved");
                }
                break;
            }
        }
        // 빙결 -- 깨어 있는 동안 18초마다 한 명(처음 깨어나면 8초 뒤부터)
        if (awake) {
            String pick = null;
            synchronized (MINE_ICE_LOCK) {
                if (mineIceNextFreeze == 0 || now - mineIceNextFreeze > MINE_ICE_PERIOD_MS) mineIceNextFreeze = now + MINE_ICE_FIRST_MS; // 막 깨어남(또는 오래 잠들었다 깨어남)
                else if (now >= mineIceNextFreeze) {
                    mineIceNextFreeze = now + MINE_ICE_PERIOD_MS;
                    List<String> cand = new ArrayList<>();
                    for (java.util.Map.Entry<String, MinePresence> pe : MINE_PRESENCE.entrySet()) {
                        MinePresence o = pe.getValue();
                        if (!map.id.equals(o.mapId) || now - o.at > MINE_ICE_FRESH_MS || map.safeAt(o.x, o.y) || mineFrozen(pe.getKey(), now) != null) continue; // [2026-11-04] "한 명이 안 얼려진다" -- 걸을 수 없는 칸(물 위 등)도 대상
                        cand.add(pe.getKey());
                    }
                    if (!cand.isEmpty()) {
                        pick = cand.get(RND.nextInt(cand.size()));
                        MinePresence o = MINE_PRESENCE.get(pick);
                        if (o != null) {
                            MINE_ICE_FROZEN.put(pick, new IceFreeze(map.version(), o.x, o.y, now + MINE_ICE_FREEZE_MS));
                            MINE_ICE_THAWED.remove(pick);
                        } else pick = null;
                    }
                }
            }
            if (pick != null && bs != null) { // 여왕이 언 사람을 노린다(멀어도 인식이 안 풀림 -- mineCombatTick)
                synchronized (bs) {
                    if (bs.frozenUntil <= now) {
                        int[] cs = mineMobCells(boss, bs, now, map);
                        bs.fx = cs[cs.length / 2] % map.w; bs.fy = cs[cs.length / 2] / map.w;
                    }
                    bs.target = pick;
                    bs.frozenUntil = now + MINE_MOB_FREEZE_MS;
                }
                mineEngage(pick, map, boss.id, now, 0L);
            }
            if (pick != null) out.put("froze", true);
            out.put("nextFreezeAt", mineIceNextFreeze);
        }
        IceFreeze me = mineFrozen(userName, now);
        if (me != null) {
            HashMap<String, Object> fz = new HashMap<>();
            fz.put("x", me.x);
            fz.put("y", me.y);
            fz.put("until", Math.max(me.until, now + 3000)); // [2026-10-28] 여왕이 깨어 있는 동안은 계속 언 채로(화면이 저절로 풀린 걸로 보지 않게)
            fz.put("k", me.until); // [2026-11-07] "빙결 게이지가 초기화된다" -- 15초가 지나면 until이 매초 바뀌어 화면이 새 얼음으로 보고 몸부림 수를 0으로 돌렸다. 얼음마다 안 바뀌는 번호
            out.put("frozen", fz);
        }
        String th = MINE_ICE_THAWED.remove(userName);
        if (th != null && (me == null || "saved".equals(th))) out.put("thawed", th);
        return me != null;
    }

    /** 얼음 여왕 보상: 얼음의 심장 1(반드시) + 50% 다이아몬드 2 + 40% 별빛석 1. */
    // [2026-10-28] "보스에게 뜨는 상징적인 아이템은 고유 증표 개념으로 1회만" -- 얼음의 심장(얼음 여왕), 대주교의 인장(대주교)은 처음 잡을 때 반드시 1개,
    //  그 뒤로는 안 나온다(받았는지는 광산 아이템 TOKEN_*). 닌자의 비급서(SHRINE_BOSS_REWARD), 사막의 심장(DESERT_CHEST)은 이미 한 번만.
    static final String MINE_ITEM_TOKEN_ICE = "TOKEN_ICE_HEART", MINE_ITEM_TOKEN_SEAL = "TOKEN_ARCH_SEAL";

    /** [2026-10-30] "보스 드랍템 다회 획득, 대신 확률 30%" -- 처치마다 굴려서 나오면 주고 true. */
    static final int MINE_TOKEN_PCT = 30;
    // [2026-11-17] "보스 심장 확률 5퍼센트 올려줘" -- 얼음/화산(여기)과 바람(하피 여왕)의 심장은 30% -> 35%(번개/사막은 원래 반드시). 대주교의 인장·하피의 검은 그대로 30%.
    static final int MINE_HEART_PCT = 100; // [2026-11-17] 같은 날 "다른 보스 심장도 100%로" -- 35 -> 100(화산의 심장도 낮/밤 상관없이)
    boolean mineTokenRoll(String userName, String code) {
        if (RND.nextInt(100) >= (code != null && code.endsWith("_HEART") ? MINE_HEART_PCT : MINE_TOKEN_PCT)) return false;
        dao.addUserOre(userName, code, 1);
        return true;
    }

    List<HashMap<String, Object>> mineIceDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        if (mineTokenRoll(userName, S5MineMap.ICE_HEART_CODE)) {
            drops.add(mineDrop(S5MineMap.ICE_HEART_GRADE, S5MineMap.ICE_HEART_NAME, 1));
            msg.append(" · 💠 ").append(S5MineMap.ICE_HEART_NAME).append(" 1개!!");
        }
        if (RND.nextInt(100) < 50) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 2);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 2));
            msg.append(" · 💎 다이아몬드 2개");
        }
        if (RND.nextInt(100) < 40) {
            dao.addUserOre(userName, S5MineMap.ORE_CODES[5], 1);
            drops.add(mineDrop(6, S5MineMap.ORE_NAMES[5], 1));
            msg.append(" · ✨ ").append(S5MineMap.ORE_NAMES[5]).append(" 1개");
        }
        return drops;
    }

    // ---- [2026-10-28] 다도해 신궁 -- 닌자 은신, 닌자 두목(분신술) ----
    // "닌자가 전투 중 가끔 은신 스킬 -- 회피 후 2배 강한 공격": 닌자(Type.ninja)를 때릴 때 MINE_NINJA_STEALTH_PCT% 확률로 은신해 피한다(피해 0, 그 턴 반격 없음).
    //  은신한 닌자의 다음 한 방(반격이든 전투 턴 공격이든)은 2배, 그러면 은신이 풀린다. 화면은 은신 중인 닌자를 반투명하게 그린다(몬스터 상태 7번째 칸).
    // "보스는 분신술 -- 다섯으로 나뉘고 하나만 자세가 다름, 은신도 함, 비급서/달빛석 한 사람에 한 번 확정":
    //  닌자 두목(21단계 보스, 닌자 두목의 방)은 체력이 75/50/25% 아래로 내려갈 때마다 분신술 -- 홀 가운데 둘레 다섯 칸 중 하나로 옮겨 가고 나머지 넷에
    //  분신(맵 몬스터 목록의 분신 자리, 분신술 때만 있음)이 선다. 분신술 동안(MINE_SPLIT_MS) 두목은 제자리에서 안 때린다. 진짜를 때리면 분신이 다 사라지고,
    //  분신을 때리면 펑 사라지며 함정 반격(두목 공격력 x MINE_CLONE_TRAP_MULT) + 두목이 최대 체력의 MINE_CLONE_HEAL_PCT%를 되찾는다. 시간이 지나면 분신은 저절로 사라진다.
    //  분신과 진짜는 화면에 똑같이 그려지고 진짜만 손을 모은 자세(인을 맺음)다.
    //  보상: 두목을 때린 유저마다(처치한 사람 + 함께 때린 사람) 처음 한 번만 닌자의 비급서 1 + 달빛석 1 확정(광산 아이템 SHRINE_BOSS_REWARD = 1).
    //  이미 받은 사람은 일반 몬스터 보상(광석/보석). 보상이 한 사람에 한 번이라 다른 보스처럼 맵 리셋마다 한 번이 아니라 10분 뒤 다시 나타난다.
    static final int MINE_NINJA_STEALTH_PCT = 15;
    static final int MINE_NINJA_STEALTH_MULT = 2;
    /** [2026-10-28] "자객 보스는 은신이 3회 공격 시까지 안 풀리게" -- 닌자 두목은 은신 중 공격 1, 2번째도 피하고 3번째에 풀리며 맞는다(그때 2배 반격).
     *  은신 중에도 보통 세기로 때리지만 은신은 안 풀린다(mineCombatTick). */
    static final int MINE_BOSS_STEALTH_HITS = 3;
    static final long MINE_SPLIT_MS = 30_000L;
    static final double MINE_CLONE_TRAP_MULT = 1.0;
    static final int MINE_CLONE_HEAL_PCT = 5;
    static final long MINE_SHRINE_BOSS_RESPAWN_MS = 2 * 3_600_000L; // [2026-10-28] 10분 -> 2시간(보스 공통)
    static final String MINE_ITEM_SHRINE_BOSS_REWARD = "SHRINE_BOSS_REWARD";
    /** 함께 싸운 유저에게 다음 전투 턴에 보여 줄 보상 소식. */
    static final java.util.concurrent.ConcurrentHashMap<String, String> MINE_SHRINE_REWARD_MSG = new java.util.concurrent.ConcurrentHashMap<>();

    List<HashMap<String, Object>> mineShrineBossDrops(String userName, S5MineMap map, S5MineMobs.Type type, java.util.Set<String> hitters, StringBuilder msg) {
        List<HashMap<String, Object>> drops = null;
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        all.add(userName);
        if (hitters != null) all.addAll(hitters);
        for (String u : all) {
            if (!u.equals(userName) && mineBossRwLeft(u, S5MineMobs.SHRINE_BOSS_TYPE) == 0 && !isMineAdmin(u)) continue; // [2026-11-20] 보상 하루 5번(쓰러뜨린 사람은 이미 확인)
            if (RND.nextInt(100) >= MINE_TOKEN_PCT) continue; // [2026-10-30] 처치마다 함께 싸운 사람별로 30%(예전: 첫 처치 1번)
            dao.addUserOre(u, S5MineMap.NINJA_BOOK_CODE, 1);
            dao.addUserOre(u, S5MineMap.MOON_STONE_CODE, 1);
            if (u.equals(userName)) {
                drops = new ArrayList<>();
                drops.add(mineDrop(S5MineMap.NINJA_BOOK_GRADE, S5MineMap.NINJA_BOOK_NAME, 1));
                drops.add(mineDrop(S5MineMap.MOON_STONE_GRADE, S5MineMap.MOON_STONE_NAME, 1));
                msg.append(" · 📜 ").append(S5MineMap.NINJA_BOOK_NAME).append(" 1권 · 🌙 ").append(S5MineMap.MOON_STONE_NAME).append(" 1개!!");
            } else {
                MINE_SHRINE_REWARD_MSG.put(u, "👑 닌자 두목이 쓰러졌어요! 함께 싸운 보상으로 📜 " + S5MineMap.NINJA_BOOK_NAME + " 1권과 🌙 " + S5MineMap.MOON_STONE_NAME + " 1개를 받았어요!");
            }
        }
        return drops != null ? drops : mineMobDrops(userName, map, type, msg, 1);
    }

    /** 보스 보상: 25% 별빛석 1개, 아니면 미스릴 3~5개 + 30% 다이아몬드. */
    /** [2026-10-28] 용암 거신 보상: 달빛석 2(반드시) + 다도해 바다 보석 2개(무작위) + 50% 다이아몬드 1. */
    /** [2026-11-05] 천둥 황소 보상 -- 번개의 심장 1개(꼭), 달빛석 3, 바다 보석 3, 다이아몬드 1. */
    List<HashMap<String, Object>> mineThunderDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        msg.insert(0, "👑 천둥 황소를 쓰러뜨렸어요! ");
        dao.addUserOre(userName, S5MineMap.THUNDER_HEART_CODE, 1);
        drops.add(mineDrop(S5MineMap.THUNDER_HEART_GRADE, S5MineMap.THUNDER_HEART_NAME, 1));
        msg.append(" · ⚡ ").append(S5MineMap.THUNDER_HEART_NAME).append(" 1개!!");
        dao.addUserOre(userName, S5MineMap.MOON_STONE_CODE, 3);
        drops.add(mineDrop(S5MineMap.MOON_STONE_GRADE, S5MineMap.MOON_STONE_NAME, 3));
        msg.append(" · 🌙 ").append(S5MineMap.MOON_STONE_NAME).append(" 3개");
        for (int i = 0; i < 3; i++) {
            int gi = RND.nextInt(S5MineMap.SEA_GEM_CODES.length);
            dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[gi], 1);
            drops.add(mineDrop(S5MineMap.SEA_GEM_GRADE_BASE + gi, S5MineMap.SEA_GEM_NAMES[gi], 1));
            msg.append(" · 💎 ").append(S5MineMap.SEA_GEM_NAMES[gi]).append(" 1개");
        }
        dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 1);
        drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 1));
        msg.append(" · 💎 다이아몬드 1개");
        return drops;
    }

    List<HashMap<String, Object>> mineLavaDrops(String userName, StringBuilder msg) {
        List<HashMap<String, Object>> drops = new ArrayList<>();
        if (mineTokenRoll(userName, S5MineMap.VOLCANO_HEART_CODE)) { // [2026-10-30] "낮에 죽이면 화산의 심장(확률)" -> [2026-11-17] 언제든 반드시
            drops.add(mineDrop(S5MineMap.VOLCANO_HEART_GRADE, S5MineMap.VOLCANO_HEART_NAME, 1));
            msg.append(" · ❤️‍🔥 ").append(S5MineMap.VOLCANO_HEART_NAME).append(" 1개!!");
        }
        dao.addUserOre(userName, S5MineMap.MOON_STONE_CODE, 2);
        drops.add(mineDrop(S5MineMap.MOON_STONE_GRADE, S5MineMap.MOON_STONE_NAME, 2));
        msg.append(" · 🌙 ").append(S5MineMap.MOON_STONE_NAME).append(" 2개");
        for (int i = 0; i < 2; i++) {
            int gi = RND.nextInt(S5MineMap.SEA_GEM_CODES.length);
            dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[gi], 1);
            drops.add(mineDrop(S5MineMap.SEA_GEM_GRADE_BASE + gi, S5MineMap.SEA_GEM_NAMES[gi], 1));
            msg.append(" · 💎 ").append(S5MineMap.SEA_GEM_NAMES[gi]).append(" 1개");
        }
        if (RND.nextInt(100) < 50) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 1);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 1));
            msg.append(" · 💎 다이아몬드 1개");
        }
        return drops;
    }

    // [2026-11-05] "보스들이 달빛석이나 별빛석을 확률 드랍" -- 모든 보스 공통 덤(각 보스 원래 보상과 별개): MINE_BOSS_SHINE_PCT%로 둘 중 하나 1개(반반).
    static final int MINE_BOSS_SHINE_PCT = 30;
    void mineBossShine(String userName, List<HashMap<String, Object>> drops, StringBuilder msg) {
        if (RND.nextInt(100) >= MINE_BOSS_SHINE_PCT) return;
        if (RND.nextBoolean()) {
            dao.addUserOre(userName, S5MineMap.MOON_STONE_CODE, 1);
            drops.add(mineDrop(S5MineMap.MOON_STONE_GRADE, S5MineMap.MOON_STONE_NAME, 1));
            msg.append(" · 🌙 ").append(S5MineMap.MOON_STONE_NAME).append(" 1개!");
        } else {
            dao.addUserOre(userName, S5MineMap.ORE_CODES[5], 1);
            drops.add(mineDrop(6, S5MineMap.ORE_NAMES[5], 1));
            msg.append(" · ✨ ").append(S5MineMap.ORE_NAMES[5]).append(" 1개!");
        }
    }

    // [2026-11-11] "하피의 검과 바람의 심장 드랍률 30% 정도" -- 따로따로 MINE_HARPY_DROP_PCT%(하피의 검은 mineWeaponDrop, 등급은 일반 몬스터 무기와 같은 비율). 그 밖은 보스 공통 보상.
    static final int MINE_HARPY_DROP_PCT = 30;
    /** [2026-11-17] 심판자 보로네오 -- 빛의 심장 1개(반드시) + 보스 공통 보상. 심판의 검/갑옷은 mineWeaponDrop(MINE_BORO_GEAR_PCT%). */
    // ---- [2026-11-18] 심해의 크라켄(바다 위, Lv.115) -- "크라켄류, 5x5, 기믹 위주: 어떤 행동을 하면 스턴 / 지원군 / 보스에게 큰 피해, 평타로도 잡히지만 어렵게,
    //  지원군은 배를 탄 NPC들이 온다, 보상은 물의 심장 + 바다 보석(세공된 것도 가끔)" --
    //   - 두꺼운 몸: 받는 피해 x MINE_KRK_ARMOR(30%), 기절 중엔 x MINE_KRK_STUN_MUL(2배) -- mineVulnMul.
    //   - 촉수 내려찍기: MINE_KRK_SMASH_GAP마다(체력 30% 아래 MINE_KRK_SMASH_GAP_RAGE) 싸우는 사람 발밑 3x3에 3초 예고 -> 최대 체력 20% + 배 25%.
    //     그 3x3에 바위(^)나 등대 섬이 있으면 촉수가 박혀 MINE_KRK_STUN_MS 기절, 화약통이 있으면 터져 크라켄 최대 체력 MINE_KRK_BARREL_PCT(화약통은 1분 뒤 다시 떠오름).
    //   - 45초마다 5초 잠수(S5MineMobs.diving -- 못 때리고 안 때림), 떠오를 때 둘레 반지름 4 물보라(최대 체력 10% + 배 5%).
    //  같은 날 다시 -- "물보스는 가운데 고정형인데 전 맵을 움직이도록, 맵에 전체적으로 등대, 등대 기믹(시안 승인), 싸우는 중에도 숨었다가 이동" --
    //   - 이동(mineKrakenMove): 아무도 안 싸우면 길(S5MineMap.seaRoute)을 따라 MINE_KRK_STEP_MS마다 한 칸, 싸우는 동안(MINE_KRK_FIGHT_MS 안에 맞음/구조선)은
    //     그 자리에서 버티다가 잠수할 때마다 길 앞쪽 MINE_KRK_JUMP_MIN~MAX칸으로 옮겨 떠오른다. 기절 중엔 안 움직인다.
    //   - 등대(바다 위 여섯): 배를 대면 섬에 내리고(배는 안 맞음), 등대 곁(2칸)에서 종을 치면(mineBell, 등대마다 MINE_KRK_BELL_CD) MINE_KRK_RES_IN 뒤
    //     해상도시 구조선이 크라켄 옆에 붙어 작살 MINE_KRK_SHOTS발(발마다 최대 체력 MINE_KRK_SHOT_PCT, 기절 중 2배). 붙어 있는 동안 크라켄은 구조선만 내려치고
    //     (사람은 안 노린다 -- 대신 맞는다) 두 번 맞으면 부서져 가라앉는다(수병이 쓰러진다 -- 화면).
    //  화약통/등대/구조선/크라켄 자리는 위치 알림(sea)으로 모두에게. 바다 위는 하나뿐이라 상태도 하나(서버 메모리).
    static final long MINE_KRK_SMASH_WARN = 3_000L, MINE_KRK_SMASH_GAP = 8_000L, MINE_KRK_SMASH_GAP_RAGE = 5_000L, MINE_KRK_STUN_MS = 8_000L, MINE_KRK_BARREL_BACK = 60_000L;
    static final double MINE_KRK_BARREL_PCT = 0.05, MINE_KRK_ARMOR = 0.3, MINE_KRK_STUN_MUL = 2.0;
    static final int MINE_KRK_SMASH_PERMIL = 200, MINE_KRK_SPLASH_PERMIL = 100;
    static final int MINE_BOAT_HIT_PCT = 1, MINE_BOAT_WHALE_PCT = 2, MINE_BOAT_KRK_PCT = 3, MINE_BOAT_SMASH_PCT = 25, MINE_BOAT_SPLASH_PCT = 5;
    static final long MINE_KRK_STEP_MS = 1_200L, MINE_KRK_FIGHT_MS = 10_000L;
    static final int MINE_KRK_JUMP_MIN = 14, MINE_KRK_JUMP_MAX = 24;
    static final long MINE_KRK_BELL_CD = 90_000L, MINE_KRK_RES_IN = 7_000L, MINE_KRK_SHOT_GAP = 1_300L, MINE_KRK_RES_MAX = 40_000L, MINE_KRK_RES_SMASH1 = 2_600L, MINE_KRK_RES_SMASH_GAP = 4_000L, MINE_KRK_RES_WARN = 2_500L;
    static final int MINE_KRK_SHOTS = 5, MINE_KRK_RES_DX = 7, MINE_KRK_RES_DY = -1, MINE_KRK_RES_HIT = 50;
    static final double MINE_KRK_SHOT_PCT = 0.012;
    static final Object MINE_KRK_LOCK = new Object();
    static final List<long[]> MINE_KRK_SMASH = new ArrayList<>(); // {x, y, 터지는 시각, 구조선을 노렸으면 1}
    static final long[] MINE_KRK_BARREL = new long[10], MINE_KRK_BELL = new long[8]; // 화약통이 터진 시각 / 등대 종을 친 시각
    // 구조선 {0 x, 1 y, 2 체력(0~100), 3 가라앉은 시각, 4 부른 시각(0 = 없음), 5 붙는 시각, 6 쏜 작살 수, 7 다음 작살 시각, 8 다음 내려찍기 예고 시각, 9 마지막 작살 시각, 10 오는 쪽(-1/1)}
    static final long[] MINE_KRK_RES = new long[11];
    static final int[] MINE_KRK_POS = { -1, -1 }; // 크라켄 지금 자리(5x5 왼쪽 위)
    static double mineKrkProg = 0;
    static long mineKrkMoveAt = 0, mineKrkJumpCyc = Long.MIN_VALUE;
    static volatile long mineKrkNextSmash = 0, mineKrkSplashCyc = -1, mineKrkMsgAt = 0;
    static volatile String mineKrkMsg = null;
    // [2026-11-19] "크라켄이 전투 시에만 맵에 보이게(그냥 지나갈 땐 모르게)" -- 누군가와 싸우는 동안(전투 턴 mineKrakenPattern, MINE_KRK_SHOW_MS 안)·내려찍기 예고·구조선이 붙어 있는 동안만 화면에 보인다(sea.kf).
    //  같은 날 "수병 소환 등대는 특정 기믹 때만" -- 촉수가 바위/등대 섬에 박혀 크라켄이 기절하면 등대에 불이 켜지고(기절 + MINE_KRK_LIGHT_MS), 불이 켜진 동안만 종을 칠 수 있다(sea.lit).
    static final long MINE_KRK_SHOW_MS = 8_000L, MINE_KRK_LIGHT_MS = 12_000L;
    static final int MINE_KRK_SWING_MULT = 3; // [2026-11-19] 크라켄 근접 = 다리 휘두르기(반격 없음) 한 번에 공격 x3
    static volatile long mineKrkFightAt = 0, mineKrkLightUntil = 0;
    static void mineKrkSay(String m, long now) { mineKrkMsg = m; mineKrkMsgAt = now; }
    // [2026-11-19] "유저랑 조우할 때도 일정 장소가 아닌 맵 전체 랜덤 조우" -- 아무와도 안 싸우는 크라켄은 물속에 숨어(길을 따라 돌기만, 지나가는 배를 알아보지 않는다 -- 보스 인식 안 함)
    //  같은 날 "유저마다 정해진 시간 크라켄은 아니고, 지금은 죽고 난 뒤 1시간" -- 쓰러지면 1시간(타입 respawn) 뒤 다시 살아나고, 살아 숨어 있으면 MINE_KRK_MEET_WAIT 사이 랜덤으로 기다렸다가
    //  그때 바다 위에 있는 배(위치 알림이 먼저 온 배 -- 사람 순서는 그때그때 섞인다) 둘레 MEET_MIN~MAX칸 넓은 물(5x5)에서 솟아오른다. 싸우다 모두 달아나 다시 숨으면 또 기다렸다가.
    //  떠오른 뒤 MINE_KRK_SURFACE_MS 동안은 보스 인식이 되고(그 사이 둘레 12칸 배와 싸움이 붙는다), 싸우는 동안 잠수하면 싸우던 배 둘레로 옮겨 떠오른다. 싸움이 끝나면 다시 숨는다.
    static final long MINE_KRK_SURFACE_MS = 8_000L, MINE_KRK_MEET_WAIT_MIN = 20_000L, MINE_KRK_MEET_WAIT_MAX = 60_000L;
    static final int MINE_KRK_MEET_MIN = 5, MINE_KRK_MEET_MAX = 8, MINE_KRK_HOP_MIN = 5, MINE_KRK_HOP_MAX = 9;
    static volatile long mineKrkMeetDue = 0; // 숨은 크라켄이 솟아오를 때(0 = 아직 안 정함)
    // [2026-11-19] "기믹 중 한 개라도 이용하지 않으면 50% 이하로 체력이 안 내려가도록" -- 바위에 박힘(기절) / 화약통 / 구조선 작살 중 하나라도 일어나면 풀린다.
    //  크라켄이 쓰러지면(mineKrakenDrops) 다시 잠긴다. 막힌 첫 순간 해역 소식 한 줄(규칙은 말하지 않는다).
    static volatile boolean mineKrkGimmick = false, mineKrkFloorSaid = false;
    static volatile int mineKrkMax = 0;
    static void mineKrkGimmickUsed() { mineKrkGimmick = true; }
    /** 크라켄 체력 바닥(기믹 전 = 최대 체력 50%). 모든 피해 뒤 쓰러짐 판정 전에. */
    static void mineKrkFloor(MobState s, long now) {
        if (!s.kraken || mineKrkGimmick || mineKrkMax <= 0) return;
        int fl = mineKrkMax / 2;
        if (s.hp < fl) { s.hp = fl; if (!mineKrkFloorSaid) { mineKrkFloorSaid = true; mineKrkSay("🐙 크라켄의 몸이 단단히 굳어 꿈쩍도 하지 않아요...", now); } }
    }
    static final int[] MINE_KRK_FIX = { -1, -1 }; // 떠올라 싸우는 자리(5x5 왼쪽 위)
    static volatile long mineKrkSurfaceUntil = 0;
    static volatile int mineKrkFocX = -1, mineKrkFocY = -1; // 마지막으로 싸운 배
    static long mineKrkRnd(long a, long b) { return a + (long) (RND.nextDouble() * (b - a)); }
    /** 물속에 숨어 있는지(아무와도 안 싸우고, 막 떠오른 것도 아니다). */
    static boolean mineKrkLurking(MobState s, long now) {
        return now >= mineKrkSurfaceUntil && now - mineKrkFightAt >= MINE_KRK_SHOW_MS && now - s.lastHitAt >= MINE_KRK_FIGHT_MS && MINE_KRK_SMASH.isEmpty() && !mineKrkResOn(now);
    }
    /** (x, y) 둘레 dmin~dmax칸에서 5x5가 모두 물(~)인 가운데 칸(부두/오른쪽 끝 출구 근처는 뺀다), 못 찾으면 null. */
    static int[] mineKrkSpot(S5MineMap map, int x, int y, int dmin, int dmax) {
        if (x < 0 || y < 0) return null;
        for (int k = 0; k < 60; k++) {
            double an = RND.nextDouble() * Math.PI * 2, d = dmin + RND.nextDouble() * (dmax - dmin);
            int cx = x + (int) Math.round(Math.cos(an) * d), cy = y + (int) Math.round(Math.sin(an) * d);
            if (cx < 12 || cy < 3 || cx > map.w - 12 || cy > map.h - 4) continue;
            if (Math.abs(cx - 2 - x) + Math.abs(cy - 2 - y) > MINE_BOSS_AGGRO_RANGE - 2) continue; // 보스 인식은 5x5 왼쪽 위 칸까지의 거리라 -- 그 안에 들어오게
            boolean ok = true;
            for (int dy = -2; dy <= 2 && ok; dy++) for (int dx = -2; dx <= 2 && ok; dx++) if (map.terrainAt(cx + dx, cy + dy) != '~') ok = false;
            if (ok) return new int[] { cx, cy };
        }
        return null;
    }
    /** [2026-11-19] 관리자 -- 쓰러진 크라켄을 1시간 기다리지 않고 바로 다시 살린다(숨은 채, 곧 바다 위 배 둘레에서 솟아오른다). */
    HashMap<String, Object> mineKrkRespawnNow() {
        HashMap<String, Object> out = new HashMap<>();
        S5MineMap sm = S5MineMap.get(S5MineMap.OPEN_SEA);
        S5MineMap.Mob km = sm == null ? null : mineBossOf(sm);
        if (km == null || km.type != S5MineMobs.KRAKEN_TYPE) { out.put("ok", false); out.put("message", "크라켄이 없어요."); return out; }
        int kmax = S5MineMobs.hp(S5MineMobs.TYPES.get(km.type), km.rank);
        MobState ks = MINE_MOB_STATE.computeIfAbsent(mineMobKey(sm, km.id), k -> new MobState(kmax));
        synchronized (ks) { ks.respawn(kmax); ks.kraken = true; ks.lastHitAt = 0; ks.stunUntil = 0; }
        synchronized (MINE_KRK_LOCK) { MINE_KRK_SMASH.clear(); MINE_KRK_RES[4] = 0; MINE_KRK_FIX[0] = -1; }
        mineKrkFightAt = 0; mineKrkSurfaceUntil = 0; mineKrkMeetDue = 0; mineKrkGimmick = false; mineKrkFloorSaid = false;
        out.put("ok", true);
        out.put("message", "🐙 크라켄을 다시 살렸어요. (체력 " + String.format("%,d", kmax) + ", 20~60초 뒤 바다 위 배 둘레에서 솟아올라요)");
        return out;
    }
    /** 위치 알림(바다 위) -- 크라켄이 살아 숨어 있고 솟아오를 때가 됐으면 이 배 둘레에서 솟아오른다. */
    void mineKrkMeet(S5MineMap map, String userName, int x, int y, long now) {
        S5MineMap.Mob km = mineBossOf(map);
        if (km == null || km.type != S5MineMobs.KRAKEN_TYPE || mineBossDead(km.type)) return;
        MobState ks = MINE_MOB_STATE.get(mineMobKey(map, km.id));
        if (ks == null) return;
        synchronized (ks) {
            if (ks.deadUntil > now) { mineKrkMeetDue = 0; return; } // 쓰러져 있다(1시간)
            if (ks.deadUntil > 0) ks.respawn(S5MineMobs.hp(S5MineMobs.TYPES.get(km.type), km.rank)); // 1시간이 지나 다시 살아났다
            if (!mineKrkLurking(ks, now)) { mineKrkMeetDue = 0; return; } // 누군가와 싸우는 중
            if (mineKrkMeetDue == 0) { mineKrkMeetDue = now + mineKrkRnd(MINE_KRK_MEET_WAIT_MIN, MINE_KRK_MEET_WAIT_MAX); return; }
            if (now < mineKrkMeetDue || x < 10) return; // 부두 앞은 조용히
            int[] c = mineKrkSpot(map, x, y, MINE_KRK_MEET_MIN, MINE_KRK_MEET_MAX);
            if (c == null) return; // 둘레에 넓은 물이 없다(바위섬 사이) -- 다음 알림에
            synchronized (MINE_KRK_LOCK) {
                MINE_KRK_FIX[0] = c[0] - 2; MINE_KRK_FIX[1] = c[1] - 2;
                MINE_KRK_POS[0] = MINE_KRK_FIX[0]; MINE_KRK_POS[1] = MINE_KRK_FIX[1];
                mineKrkSurfaceUntil = now + MINE_KRK_SURFACE_MS; mineKrkFocX = x; mineKrkFocY = y;
            }
            ks.fx = MINE_KRK_POS[0]; ks.fy = MINE_KRK_POS[1];
            ks.frozenUntil = Math.max(ks.frozenUntil, now + MINE_MOB_FREEZE_MS);
            mineKrkMeetDue = 0;
        }
    }
    /** 길(고리) 한 바퀴 칸 수. */
    static double mineKrkLoop(int[][] r) { double l = 0; for (int i = 0; i < r.length; i++) l += Math.hypot(r[(i + 1) % r.length][0] - r[i][0], r[(i + 1) % r.length][1] - r[i][1]); return l; }
    /** 길 위 prog칸 자리(가운데 칸). */
    static int[] mineKrkAt(int[][] r, double prog) {
        for (int i = 0; i < r.length; i++) {
            int[] a = r[i], b = r[(i + 1) % r.length];
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (prog <= l || i == r.length - 1) { double q = l <= 0 ? 0 : Math.min(1, prog / l); return new int[] { (int) Math.round(a[0] + (b[0] - a[0]) * q), (int) Math.round(a[1] + (b[1] - a[1]) * q) }; }
            prog -= l;
        }
        return new int[] { r[0][0], r[0][1] };
    }
    static boolean mineKrkResOn(long now) { return MINE_KRK_RES[4] > 0 && MINE_KRK_RES[3] == 0 && MINE_KRK_RES[2] > 0; }
    static boolean mineKrkResArrived(long now) { return mineKrkResOn(now) && now >= MINE_KRK_RES[5]; }
    /** 크라켄 자리 옮기기(MobState 잠금 안) -- 지금 자리를 s.fx/fy(5x5 왼쪽 위)와 MINE_KRK_POS에. */
    void mineKrakenMove(S5MineMap map, MobState s, int id, long now) {
        int[][] route = map.seaRoute();
        if (route == null) return;
        if (s.deadUntil > now) return;
        S5MineMobs.Type t = S5MineMobs.TYPES.get(S5MineMobs.KRAKEN_TYPE);
        synchronized (MINE_KRK_LOCK) {
            double loop = mineKrkLoop(route);
            if (mineKrkMoveAt == 0) mineKrkMoveAt = now;
            long el = Math.max(0, Math.min(now - mineKrkMoveAt, 10 * 60_000L));
            mineKrkMoveAt = now;
            boolean stun = s.stunUntil > now, under = S5MineMobs.diving(t, id, now);
            boolean fight = now - s.lastHitAt < MINE_KRK_FIGHT_MS || !MINE_KRK_SMASH.isEmpty() || mineKrkResOn(now);
            long cyc = Math.floorDiv(now + id * 7919L, S5MineMobs.KRAKEN_DIVE_MS);
            if (fight) {
                if (under && !stun && cyc != mineKrkJumpCyc) { // 잠수 -- 싸우던 배 둘레로 옮겨 떠오른다([2026-11-19] 길 앞쪽 대신)
                    mineKrkJumpCyc = cyc;
                    int[] hop = mineKrkSpot(map, mineKrkFocX, mineKrkFocY, MINE_KRK_HOP_MIN, MINE_KRK_HOP_MAX);
                    if (hop != null) { MINE_KRK_FIX[0] = hop[0] - 2; MINE_KRK_FIX[1] = hop[1] - 2; }
                }
            } else if (!stun) mineKrkProg += el / (double) MINE_KRK_STEP_MS;
            mineKrkProg = ((mineKrkProg % loop) + loop) % loop;
            if (!mineKrkLurking(s, now) && MINE_KRK_FIX[0] >= 0) { MINE_KRK_POS[0] = MINE_KRK_FIX[0]; MINE_KRK_POS[1] = MINE_KRK_FIX[1]; } // [2026-11-19] 떠올라 있는 동안은 만난 자리
            else { int[] c = mineKrkAt(route, mineKrkProg); MINE_KRK_POS[0] = c[0] - 2; MINE_KRK_POS[1] = c[1] - 2; MINE_KRK_FIX[0] = -1; } // 숨어서 길을 따라 돈다
        }
        s.fx = MINE_KRK_POS[0]; s.fy = MINE_KRK_POS[1];
        s.frozenUntil = Math.max(s.frozenUntil, now + MINE_MOB_FREEZE_MS);
    }
    /** 위치 알림(바다 위 사람마다 1초마다) -- 아무도 안 싸워도 크라켄이 돌고, 구조선이 오고 쏘고 맞는다. */
    void mineKrakenTick(long now) {
        S5MineMap sm = S5MineMap.get(S5MineMap.OPEN_SEA);
        S5MineMap.Mob km = sm == null ? null : mineBossOf(sm);
        if (km == null || km.type != S5MineMobs.KRAKEN_TYPE) return;
        int kmax = S5MineMobs.hp(S5MineMobs.TYPES.get(km.type), km.rank);
        MobState ks = MINE_MOB_STATE.computeIfAbsent(mineMobKey(sm, km.id), k -> new MobState(kmax));
        mineKrkMax = kmax; // [2026-11-19] 체력 바닥(50%) 계산용
        synchronized (ks) {
            ks.kraken = true;
            if (ks.deadUntil > now) { synchronized (MINE_KRK_LOCK) { if (MINE_KRK_RES[4] > 0 && MINE_KRK_RES[3] == 0) MINE_KRK_RES[4] = 0; MINE_KRK_SMASH.clear(); } return; }
            mineKrakenMove(sm, ks, km.id, now);
            synchronized (MINE_KRK_LOCK) {
                mineKrkResolve(sm, ks, kmax, now);
                mineKrkRescue(sm, ks, km.id, kmax, now);
            }
        }
    }
    /** 예고가 끝난 내려찍기 -- 구조선/바위·등대(기절)/화약통. MINE_KRK_LOCK 안. */
    void mineKrkResolve(S5MineMap map, MobState s, int bmax, long now) {
        int[][] barrels = map.seaBarrels();
        for (java.util.Iterator<long[]> it = MINE_KRK_SMASH.iterator(); it.hasNext();) {
            long[] z = it.next();
            if (z[2] > now) continue;
            it.remove();
            boolean rock = false;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int zx = (int) z[0] + dx, zy = (int) z[1] + dy;
                if (!map.inside(zx, zy)) continue;
                char zc = map.terrainAt(zx, zy);
                if (zc == '^' || zc == 'r' || zc == 'N') rock = true; // 등대 섬도 바위섬
            }
            long[] r = MINE_KRK_RES;
            if (mineKrkResOn(now) && Math.max(Math.abs(r[0] - z[0]), Math.abs(r[1] - z[1])) <= 1) { // 구조선이 대신 맞았다
                r[2] = Math.max(0, r[2] - MINE_KRK_RES_HIT);
                if (r[2] == 0) { r[3] = now; mineKrkSay("⚓ 크라켄의 촉수에 구조선이 부서져 가라앉았어요... 수병들이 끝까지 싸웠어요.", now); }
                else mineKrkSay("🛡️ 구조선이 크라켄의 촉수를 대신 맞았어요! 한 번 더 맞으면 가라앉아요.", now);
            }
            if (rock) { mineKrkGimmickUsed(); s.stunUntil = Math.max(s.stunUntil, now + MINE_KRK_STUN_MS); mineKrkLightUntil = now + MINE_KRK_STUN_MS + MINE_KRK_LIGHT_MS; mineKrkSay("🪨 촉수가 바위에 박혔어요! 크라켄이 " + MINE_KRK_STUN_MS / 1000 + "초 동안 꼼짝 못 해요 -- 🗼 등대에 불이 켜졌어요!", now); } // [2026-11-19] 등대 불
            if (barrels != null) for (int i = 0; i < barrels.length && i < MINE_KRK_BARREL.length; i++) {
                if (MINE_KRK_BARREL[i] > 0 && now - MINE_KRK_BARREL[i] < MINE_KRK_BARREL_BACK) continue;
                if (Math.max(Math.abs(barrels[i][0] - z[0]), Math.abs(barrels[i][1] - z[1])) > 1) continue;
                MINE_KRK_BARREL[i] = now;
                mineKrkGimmickUsed(); // [2026-11-19]
                int bd = (int) Math.round(bmax * MINE_KRK_BARREL_PCT);
                s.hp = Math.max(1, s.hp - bd);
                mineKrkSay("💥 촉수가 화약통을 내려쳤어요! 크라켄 -" + String.format("%,d", bd), now);
            }
        }
    }
    /** 구조선 -- 붙으러 오고(크라켄 옆으로), 작살을 쏘고, 크라켄이 구조선을 내려친다. MINE_KRK_LOCK 안. */
    void mineKrkRescue(S5MineMap map, MobState s, int id, int bmax, long now) {
        long[] r = MINE_KRK_RES;
        if (r[4] <= 0) return;
        if (r[3] > 0) { if (now - r[3] > 6_000L) r[4] = 0; return; } // 가라앉고 조금 뒤 정리
        if (now > r[5] + MINE_KRK_RES_MAX) { r[4] = 0; mineKrkSay("⛵ 구조선이 크라켄을 피해 바다마을 윤슬항으로 돌아갔어요.", now); return; }
        int kcx = MINE_KRK_POS[0] + 2, kcy = MINE_KRK_POS[1] + 2, side = (int) r[10];
        boolean aimed = false;
        for (long[] z : MINE_KRK_SMASH) if (z.length > 3 && z[3] == 1) aimed = true;
        if (!aimed) { // 크라켄 옆으로(오는 동안은 멀리서 저어 온다), 노림을 받는 동안은 그 자리
            long left = Math.max(0, r[5] - now);
            int tx = kcx + side * (MINE_KRK_RES_DX + (int) (14 * left / MINE_KRK_RES_IN)), ty = kcy + MINE_KRK_RES_DY;
            r[0] = Math.max(2, Math.min(map.w - 4, tx)); r[1] = Math.max(2, Math.min(map.h - 3, ty));
        }
        if (now < r[5]) return;
        boolean under = S5MineMobs.diving(S5MineMobs.TYPES.get(S5MineMobs.KRAKEN_TYPE), id, now), stun = s.stunUntil > now;
        if (r[6] < MINE_KRK_SHOTS && now >= r[7]) { // 작살
            if (under) r[7] = now + 500L;
            else {
                mineKrkGimmickUsed(); // [2026-11-19] 구조선 작살
                int sd = (int) Math.round(bmax * MINE_KRK_SHOT_PCT * (stun ? MINE_KRK_STUN_MUL : 1.0));
                s.hp = Math.max(1, s.hp - sd);
                s.lastHitAt = now;
                r[6]++; r[7] = now + MINE_KRK_SHOT_GAP; r[9] = now;
            }
        }
        if (!aimed && !under && !stun && now >= r[8]) { // 크라켄이 구조선만 노린다(대신 맞는다)
            mineZoneAdd(map, (int) r[0], (int) r[1], now + MINE_KRK_RES_WARN, 1, 11, MINE_KRK_RES_WARN, MINE_KRK_SMASH_PERMIL, 0);
            MINE_KRK_SMASH.add(new long[] { r[0], r[1], now + MINE_KRK_RES_WARN, 1 });
            r[8] = now + MINE_KRK_RES_SMASH_GAP;
        }
    }
    /** 전투 턴(크라켄과 싸우는 사람마다, MobState 잠금 안) -- 내려찍기 예고/결과, 떠오를 때 물보라. 구조선이 붙어 있으면 사람은 안 노린다. */
    void mineKrakenPattern(S5MineMap map, MobState s, S5MineMobs.Type t, int id, int x, int y, int bmax, long now) {
        s.kraken = true;
        if (s.deadUntil > now) return;
        mineKrkFightAt = now; // [2026-11-19] 싸우는 중 -- 화면에 보인다
        if (MINE_KRK_FIX[0] < 0) { MINE_KRK_FIX[0] = s.fx; MINE_KRK_FIX[1] = s.fy; } // 숨어 있다 붙은 싸움(관리자 등)도 그 자리에서
        mineKrkFocX = x; mineKrkFocY = y; // [2026-11-19] 잠수하면 이 배 둘레로
        synchronized (MINE_KRK_LOCK) {
            mineKrkResolve(map, s, bmax, now);
            boolean under = S5MineMobs.diving(t, id, now);
            long cyc = Math.floorDiv(now + id * 7919L, S5MineMobs.KRAKEN_DIVE_MS);
            if (!under && mineKrkSplashCyc != cyc && Math.floorMod(now + id * 7919L, S5MineMobs.KRAKEN_DIVE_MS) < 2_000L) { // 막 솟아올랐다 -- 둘레 물보라
                mineKrkSplashCyc = cyc;
                if (mineKrkSplashCyc >= 0) mineZoneAdd(map, s.fx + 2, s.fy + 2, now + 900L, 4, 12, 900L, MINE_KRK_SPLASH_PERMIL, 0);
            }
            if (s.stunUntil <= now && !under && now >= mineKrkNextSmash && !mineKrkResArrived(now)) {
                mineKrkNextSmash = now + (s.hp * 10L < bmax * 3L ? MINE_KRK_SMASH_GAP_RAGE : MINE_KRK_SMASH_GAP);
                long at = now + MINE_KRK_SMASH_WARN;
                mineZoneAdd(map, x, y, at, 1, 11, MINE_KRK_SMASH_WARN, MINE_KRK_SMASH_PERMIL, 0);
                MINE_KRK_SMASH.add(new long[] { x, y, at, 0 });
            }
        }
    }
    /** 위치 알림의 sea -- 화약통 터진 시각, 등대 종 친 시각, 구조선, 크라켄 가운데 칸, 해역 소식. */
    static HashMap<String, Object> mineSeaState(long now) {
        HashMap<String, Object> o = new HashMap<>();
        synchronized (MINE_KRK_LOCK) {
            o.put("barrel", MINE_KRK_BARREL.clone());
            o.put("bell", MINE_KRK_BELL.clone());
            o.put("bellCd", MINE_KRK_BELL_CD);
            if (MINE_KRK_RES[4] > 0) o.put("res", MINE_KRK_RES.clone());
            if (MINE_KRK_POS[0] >= 0) { o.put("kx", MINE_KRK_POS[0] + 2); o.put("ky", MINE_KRK_POS[1] + 2); }
            o.put("kf", now - mineKrkFightAt < MINE_KRK_SHOW_MS || !MINE_KRK_SMASH.isEmpty() || mineKrkResOn(now)); // [2026-11-19] 싸우는 중에만 보인다
            o.put("lit", mineKrkLightUntil); // [2026-11-19] 등대 불(이 시각까지)
        }
        if (mineKrkMsg != null && now - mineKrkMsgAt < 6_000L) { o.put("msg", mineKrkMsg); o.put("msgAt", mineKrkMsgAt); }
        return o;
    }
    /** 등대 종 치기 -- idx = 바다 위 등대 순서. 등대 곁(2칸, 섬 위)에서만. */
    HashMap<String, Object> mineBell(String userName, int idx) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        S5MineMap sm = S5MineMap.get(S5MineMap.OPEN_SEA);
        if (row == null || sm == null || !S5MineMap.OPEN_SEA.equals(strVal(row.get("MAP_ID"), ""))) { out.put("message", "바다 위에서만 쓸 수 있어요."); return out; }
        int[][] lh = sm.lighthouses();
        if (lh == null || idx < 0 || idx >= lh.length || idx >= MINE_KRK_BELL.length) { out.put("message", "그런 등대는 없어요."); return out; }
        int[] pos = minePos(row, sm, 1);
        if (Math.max(Math.abs(pos[0] - lh[idx][0]), Math.abs(pos[1] - lh[idx][1])) > 2) { out.put("message", "🗼 등대 섬에 배를 대고 내려야 종을 칠 수 있어요."); return out; }
        S5MineMap.Mob km = mineBossOf(sm);
        MobState ks = km == null ? null : MINE_MOB_STATE.get(mineMobKey(sm, km.id));
        long now = System.currentTimeMillis();
        if (ks != null && ks.deadUntil > now) { out.put("message", "🌊 지금은 바다가 잠잠해요. 크라켄이 없어요."); return out; }
        synchronized (MINE_KRK_LOCK) {
            if (mineKrkResOn(now)) { out.put("message", "⛵ 구조선이 이미 " + (now < MINE_KRK_RES[5] ? "오고 있어요!" : "싸우고 있어요!")); return out; }
            if (now > mineKrkLightUntil && !isMineAdmin(userName)) { out.put("message", "🗼 등대 불이 꺼져 있어 종이 울리지 않아요. 불이 켜졌을 때 쳐 보세요."); return out; } // [2026-11-19] 특정 기믹(등대 불) 때만
            long left = MINE_KRK_BELL_CD - (now - MINE_KRK_BELL[idx]);
            if (left > 0) { out.put("message", "🔔 이 등대 종은 아직 울림이 남아 있어요. (" + (left + 999) / 1000 + "초)"); return out; }
            MINE_KRK_BELL[idx] = now;
            int kcx = MINE_KRK_POS[0] >= 0 ? MINE_KRK_POS[0] + 2 : sm.bossX() + 2;
            long[] r = MINE_KRK_RES;
            r[2] = 100; r[3] = 0; r[4] = now; r[5] = now + MINE_KRK_RES_IN; r[6] = 0; r[7] = r[5] + MINE_KRK_SHOT_GAP; r[8] = r[5] + MINE_KRK_RES_SMASH1; r[9] = 0;
            r[10] = lh[idx][0] < kcx ? -1 : 1; // 등대 쪽에서 온다
            r[0] = Math.max(2, Math.min(sm.w - 4, kcx + r[10] * (MINE_KRK_RES_DX + 14))); r[1] = MINE_KRK_POS[1] >= 0 ? MINE_KRK_POS[1] + 2 + MINE_KRK_RES_DY : sm.bossY() + 1;
        }
        mineKrkSay("🔔 " + mineNick(userName) + " 님이 등대 종을 쳤어요! " + MINE_KRK_RES_IN / 1000 + "초 뒤 바다마을 윤슬항 구조선이 달려와요.", now);
        out.put("ok", true);
        out.put("message", "🔔 땡- 땡- 등대 종을 쳤어요! " + MINE_KRK_RES_IN / 1000 + "초 뒤 바다마을 윤슬항 구조선이 작살을 들고 달려와요.");
        out.put("sea", mineSeaState(now));
        return out;
    }
    /** 심해의 크라켄 보상: 물의 심장 1(반드시) + 보스 공통 보상 + 바다 보석 3~5개 + 25% 세공된 바다 보석 1개. */
    static final int MINE_KRK_CUT_PCT = 25;
    List<HashMap<String, Object>> mineKrakenDrops(String userName, StringBuilder msg) {
        msg.insert(0, "👑 심해의 크라켄을 쓰러뜨렸어요! ");
        List<HashMap<String, Object>> drops = mineBossDrops(userName, msg);
        dao.addUserOre(userName, S5MineMap.WATER_HEART_CODE, 1);
        drops.add(mineDrop(S5MineMap.WATER_HEART_GRADE, S5MineMap.WATER_HEART_NAME, 1));
        msg.append(" · 💧 ").append(S5MineMap.WATER_HEART_NAME).append(" 1개!!");
        int[] cnt = new int[S5MineMap.SEA_GEM_CODES.length];
        for (int i = 3 + RND.nextInt(3); i > 0; i--) cnt[S5MineMap.seaGemFor(RND.nextInt(100))]++;
        for (int g = 0; g < cnt.length; g++) {
            if (cnt[g] == 0) continue;
            dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[g], cnt[g]);
            drops.add(mineDrop(S5MineMap.SEA_GEM_GRADE_BASE + g, S5MineMap.SEA_GEM_NAMES[g], cnt[g]));
            msg.append(" · 💎 ").append(S5MineMap.SEA_GEM_NAMES[g]).append(" ").append(cnt[g]).append("개");
        }
        if (RND.nextInt(100) < MINE_KRK_CUT_PCT || isMineAdmin(userName)) {
            int cg = RND.nextInt(S5MineMap.SEA_GEM_CODES.length);
            dao.addUserOre(userName, S5MineMap.seaCutCode(cg), 1);
            drops.add(mineDrop(S5MineMap.SEA_CUT_GRADE_BASE + cg, S5MineMap.seaCutName(cg), 1));
            msg.append(" · ✨ ").append(S5MineMap.seaCutName(cg)).append(" 1개!");
        }
        mineGossipAdd(mineNick(userName) + " 님이 바다 위에서 심해의 크라켄을 쓰러뜨렸대요!");
        mineKrkGimmick = false; mineKrkFloorSaid = false; // [2026-11-19] 다음 크라켄은 다시 기믹이 있어야 반 아래로
        return drops;
    }

    // ---- [2026-11-19] "바다 위 맵에서 체력이 다하면 흑백 화면 상태에서도 배가 공격받도록" -- 쓰러져 있는 10초(MINE_WB_DOWN) 동안은 전투 턴이 없어(인식도 끝)
    //  아무도 배를 안 때렸다. 위치 알림(1초마다)에서 MINE_SEA_DOWN_HIT_MS마다 배 바로 옆(1칸) 바다 몬스터 한 마리당 평소처럼 1%(고래 2%),
    //  떠올라 있는 크라켄이 2칸 안이면 3%씩 배를 깎는다(물속에 잠긴 몬스터는 못 때린다). 등대 섬 위(배에서 내림)는 안 맞는다. 0이 되면 배가 부서져 마을로(mineBoatHit).
    static final long MINE_SEA_DOWN_HIT_MS = 1_500L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_SEA_DOWN_AT = new java.util.concurrent.ConcurrentHashMap<>();
    HashMap<String, Object> mineSeaDownTick(S5MineMap map, String userName, int x, int y, long now, HashMap<String, Object> out) {
        if (mineWbDownUntil(userName, now) <= 0) { MINE_SEA_DOWN_AT.remove(userName); return null; }
        if (!map.boat() || map.terrainAt(x, y) != '~') return null;
        Long last = MINE_SEA_DOWN_AT.get(userName);
        if (last != null && now - last < MINE_SEA_DOWN_HIT_MS) return null;
        MINE_SEA_DOWN_AT.put(userName, now);
        int pct = 0;
        for (S5MineMap.Mob mb : map.mobs()) {
            S5MineMobs.Type t = S5MineMobs.TYPES.get(mb.type);
            if (!t.sea) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, mb.id));
            if (s != null && s.deadUntil > now) continue;
            if (S5MineMobs.diving(t, mb.id, now)) continue;
            if (mb.type == S5MineMobs.KRAKEN_TYPE) {
                if (s == null || mineKrkLurking(s, now) || mineFootDist(t, s, x, y) > 2) continue;
                pct += MINE_BOAT_KRK_PCT;
                continue;
            }
            int mx = s != null && s.frozenUntil > now ? s.fx : mb.x, my = s != null && s.frozenUntil > now ? s.fy : mb.y;
            if (Math.abs(mx - x) + Math.abs(my - y) > 1) continue;
            pct += mb.type == S5MineMobs.SEA_WHALE ? MINE_BOAT_WHALE_PCT : MINE_BOAT_HIT_PCT;
        }
        if (pct <= 0) return null;
        Map<String, Integer> items = mineItems(userName);
        if (items.getOrDefault(S5MineMap.BOAT_ITEM, 0) <= 0) return null;
        HashMap<String, Object> bo = new HashMap<>();
        long left = mineBoatHit(userName, items, mineBoatHpMax(items) * pct / 100, bo);
        if (bo.containsKey("boatWreck")) { MINE_SEA_DOWN_AT.remove(userName); out.put("villageOut", bo.get("message")); return out; }
        out.put("boatHp", Math.max(0, left));
        return null;
    }

    // ---- [2026-11-18] 망망대해 -- "출구가 없고, 계속 돌아다니면 초당 5%씩 배가 부서진다, 10초 이상 버티면 남쪽 바닷가/다도해/바다 도시 셋 중 하나로 떨어진다" --
    //  위치 알림(1초마다)에서 배를 깎고(0이 되면 mineBoatHit이 마을로), MINE_VAST_MS가 지나면 셋 중 하나로 옮긴다(villageOut -- 화면이 다시 불러온다).
    //  MINE_VAST = {들어온 시각, 배를 마지막으로 깎은 시각, 마지막 위치 알림} -- 5초 넘게 소식이 없었으면 새로 들어온 것으로 본다.
    static final int MINE_VAST_PERMIL = 24; // [2026-11-19] "망망대해 체력 24% 닳게(지금 50%)" -- 초당 5% -> 2.4%(천분율), 10초에 24%
    static final long MINE_VAST_MS = 10_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_VAST = new java.util.concurrent.ConcurrentHashMap<>();
    HashMap<String, Object> mineVastTick(String userName, long now, HashMap<String, Object> out) {
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null || !S5MineMap.VAST_SEA.equals(strVal(row.get("MAP_ID"), ""))) { MINE_VAST.remove(userName); return null; }
        long[] v = MINE_VAST.computeIfAbsent(userName, k -> new long[] { now, now, now });
        if (now - v[2] > 5_000L) { v[0] = now; v[1] = now; }
        v[2] = now;
        Map<String, Integer> items = mineItems(userName);
        long secs = (now - v[1]) / 1_000L;
        if (secs > 0) {
            v[1] += secs * 1_000L;
            HashMap<String, Object> bo = new HashMap<>();
            long left = mineBoatHit(userName, items, mineBoatHpMax(items) * MINE_VAST_PERMIL * secs / 1000, bo);
            if (bo.containsKey("boatWreck")) { MINE_VAST.remove(userName); out.put("villageOut", bo.get("message")); return out; }
            out.put("boatHp", Math.max(0, left));
        }
        if (now - v[0] >= MINE_VAST_MS) {
            MINE_VAST.remove(userName);
            String[] dests = { S5MineMap.BEACH, S5MineMap.ARCHI, S5MineMap.SEA_CITY };
            S5MineMap t = S5MineMap.get(dests[RND.nextInt(dests.length)]);
            int tx = t.dockX() >= 0 ? t.dockX() : t.spawnX, ty = t.dockX() >= 0 ? t.dockY() : t.spawnY;
            if (!t.walkable(tx, ty)) { tx = t.spawnX; ty = t.spawnY; }
            dao.updateMinePos(userName, t.id, tx, ty, now);
            mineDisengageAll(userName);
            out.put("villageOut", "🌊 거센 파도에 떠밀려 한참을 표류하다... " + t.name + "에 닿았어요!");
            return out;
        }
        return null;
    }

    // ---- [2026-11-18] 워프 포인트 내구도 -- "모든 유저가 함께 쓰는 내구도, 출발지와 도착지가 닳는다, 다 되면 그 지역 이동 불가, 마을은 무한, 처음 200,
    //  거룡 토벌 1·2위에게 회복 키트 20개를 나누어 주고, 회복 NPC에게 키트 하나당 내구도 20" --
    //  TBOT_S55_WP_DUR(줄이 없으면 가득)을 처음 쓸 때 메모리로 읽고, 바뀔 때마다 그 줄만 MERGE. 워프 한 번 = 출발지 -1, 도착지 -1(마을 제외).
    static final int MINE_WP_DUR_MAX = 200, MINE_WP_KIT_FIX = 20;
    // [2026-11-20] "1~10위 거룡 보상으로 심장 조각을 무작위로, 개수도 0~5개" -- 토벌 몫 순 MINE_WP_KIT_RANKS위까지 사람마다 0~MINE_WP_KIT_MAX개(예전: 1·2위 10개씩)
    static final int MINE_WP_KIT_RANKS = 10, MINE_WP_KIT_MAX = 5;
    static final java.util.Set<String> MINE_WP_TOWNS = new java.util.HashSet<>(java.util.Arrays.asList("field", "city", "snowtown", "archi", "tcity", "seacity"));
    static final java.util.concurrent.ConcurrentHashMap<String, Integer> MINE_WP_DUR = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile boolean mineWpDurLoaded = false;
    static boolean mineWpTown(S5MineMap.Waypoint w) { return w == null || MINE_WP_TOWNS.contains(w.id); }
    /** [2026-11-20] "워프 포인트 횟수는 미공개, 퍼센트로만" -- 화면엔 남은 힘 %만(1번이라도 남으면 1% 이상, 0이면 0%). */
    int mineWpPct(S5MineMap.Waypoint w) { int d = mineWpDur(w); return d <= 0 ? 0 : Math.max(1, (int) Math.ceil(d * 100.0 / MINE_WP_DUR_MAX)); }
    int mineWpDur(S5MineMap.Waypoint w) {
        if (mineWpTown(w)) return MINE_WP_DUR_MAX;
        if (!mineWpDurLoaded) synchronized (MINE_WP_DUR) { if (!mineWpDurLoaded) { for (HashMap<String, Object> r : dao.selectS55WpDur()) MINE_WP_DUR.put(strVal(r.get("WP_ID"), ""), intVal(r.get("DUR"), MINE_WP_DUR_MAX)); mineWpDurLoaded = true; } }
        return Math.max(0, MINE_WP_DUR.getOrDefault(w.id, MINE_WP_DUR_MAX));
    }
    // [2026-11-18] DB 점검 3번 -- 예전엔 synchronized 안에서 워프 트랜잭션에 MERGE를 넣어 모두가 함께 쓰는 줄 잠금을 커밋까지 쥐었다(다른 워프가 줄을 섰다).
    //  이제 메모리만 바꾸고(MINE_WP_DIRTY) 정리 스레드(mineMercXpSweep -- 30초마다, 서버 끌 때)가 요청 밖에서 쓴다.
    static final java.util.Set<String> MINE_WP_DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();
    void mineWpDurSet(S5MineMap.Waypoint w, int v) { MINE_WP_DUR.put(w.id, v); MINE_WP_DIRTY.add(w.id); }
    void mineWpDurFlush() {
        for (String id : new ArrayList<>(MINE_WP_DIRTY)) {
            MINE_WP_DIRTY.remove(id);
            Integer v = MINE_WP_DUR.get(id);
            if (v == null) continue;
            try { dao.mergeS55WpDur(id, v); } catch (Exception e) { MINE_WP_DIRTY.add(id); }
        }
    }
    /** 워프 전 -- 출발지/도착지 내구도가 다했으면 안내(아니면 null). */
    String mineWpDurBlock(S5MineMap.Waypoint from, S5MineMap.Waypoint to) {
        if (to != null && mineWpDur(to) <= 0) return "🌀 " + to.name + " 워프 포인트의 힘이 다했어요. 왕도 워프 포인트 관리인에게 거룡의 심장 조각으로 채워 달라고 하세요.";
        if (from != null && mineWpDur(from) <= 0) return "🌀 이 워프 포인트의 힘이 다했어요. 왕도 워프 포인트 관리인에게 거룡의 심장 조각으로 채워 달라고 하세요.";
        return null;
    }
    /** 워프 하는 순간 -- 출발지/도착지 하나씩(마을은 무한). */
    void mineWpDurUse(S5MineMap.Waypoint from, S5MineMap.Waypoint to) {
        synchronized (MINE_WP_DUR) {
            for (S5MineMap.Waypoint w : new S5MineMap.Waypoint[] { from, to }) if (!mineWpTown(w)) mineWpDurSet(w, Math.max(0, mineWpDur(w) - 1));
        }
    }
    /** 워프 포인트 관리인 창 -- 마을이 아닌 워프 포인트의 내구도 + 내 키트 수. */
    HashMap<String, Object> mineWpDurInfo(String userName, HashMap<String, Object> out) {
        List<HashMap<String, Object>> l = new ArrayList<>();
        for (S5MineMap.Waypoint w : S5MineMap.waypoints()) {
            if (mineWpTown(w)) continue; // [2026-11-18] 5장 바다도 모두에게
            HashMap<String, Object> m = new HashMap<>();
            int dur = mineWpDur(w);
            m.put("id", w.id); m.put("name", w.name); m.put("act", w.act); m.put("pct", mineWpPct(w)); m.put("need", (MINE_WP_DUR_MAX - dur + MINE_WP_KIT_FIX - 1) / MINE_WP_KIT_FIX); // [2026-11-20] 횟수 대신 %, 가득 채우는 데 드는 조각 수
            l.add(m);
        }
        out.put("ok", true);
        out.put("wps", l);
        out.put("fixPct", MINE_WP_KIT_FIX * 100 / MINE_WP_DUR_MAX); // [2026-11-20] 조각 하나에 +%
        out.put("kits", mineOreHave(userName).getOrDefault(S5MineMap.WP_KIT_CODE, 0));
        return out;
    }
    /** 키트 n개로 워프 포인트 하나 채우기(가득 차면 남는 키트는 안 쓴다). */
    HashMap<String, Object> mineWpFix(String userName, String wpId, int n) {
        HashMap<String, Object> out = new HashMap<>();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        S5MineMap.Waypoint w = S5MineMap.waypoint(wpId);
        if (row == null || !mineAtNpc(row, "wpkeeper")) out.put("message", "왕도 워프 포인트 관리인에게서만 채울 수 있어요.");
        else if (w == null || mineWpTown(w)) out.put("message", "마을 워프 포인트는 힘이 다하지 않아요.");
        else {
            int dur = mineWpDur(w), need = (MINE_WP_DUR_MAX - dur + MINE_WP_KIT_FIX - 1) / MINE_WP_KIT_FIX, use = Math.min(Math.max(1, n), need);
            if (need <= 0) out.put("message", "🌀 " + w.name + " 워프 포인트는 이미 가득 차 있어요.");
            else if (dao.takeUserOre(userName, S5MineMap.WP_KIT_CODE, use) == 0) out.put("message", "🔧 " + S5MineMap.WP_KIT_NAME + "이 모자라요. (" + use + "개 필요) 월드보스 거룡 토벌 1~10위 보상이에요.");
            else {
                int nd;
                synchronized (MINE_WP_DUR) { nd = Math.min(MINE_WP_DUR_MAX, mineWpDur(w) + use * MINE_WP_KIT_FIX); mineWpDurSet(w, nd); }
                out.put("message", "✨ " + w.name + " 워프 포인트에 거룡의 기운을 채웠어요! (조각 " + use + "개 · 힘 " + mineWpPct(w) + "%)");
                out.put("fixed", true);
                out.put("ores", mineOreList(userName));
            }
        }
        return mineWpDurInfo(userName, out);
    }

    static final int MINE_JUDGE_TOKEN_PCT = 50; // [2026-11-18] "보로네오 뒤쪽은 50% 확률로 나오는 증표가 있어야"
    List<HashMap<String, Object>> mineBoroDrops(String userName, StringBuilder msg) {
        msg.insert(0, "👑 심판자 보로네오를 쓰러뜨렸어요! ");
        List<HashMap<String, Object>> drops = mineBossDrops(userName, msg);
        dao.addUserOre(userName, S5MineMap.LIGHT_HEART_CODE, 1);
        drops.add(mineDrop(S5MineMap.LIGHT_HEART_GRADE, S5MineMap.LIGHT_HEART_NAME, 1));
        msg.append(" · ☀️ ").append(S5MineMap.LIGHT_HEART_NAME).append(" 1개!!");
        if (RND.nextInt(100) < MINE_JUDGE_TOKEN_PCT || isMineAdmin(userName)) { // [2026-11-18] 심판의 증표 -- 있어야 보로네오 뒤 마계 입구로
            dao.addUserOre(userName, S5MineMap.JUDGE_TOKEN_CODE, 1);
            drops.add(mineDrop(S5MineMap.JUDGE_TOKEN_GRADE, S5MineMap.JUDGE_TOKEN_NAME, 1));
            msg.append(" · ⚖️ ").append(S5MineMap.JUDGE_TOKEN_NAME).append(" 1개!! 이제 마계 입구로 갈 수 있어요.");
        }
        mineGossipAdd(mineNick(userName) + " 님이 최후의 방어선에서 심판자 보로네오를 쓰러뜨렸대요!");
        return drops;
    }
    List<HashMap<String, Object>> mineHarpyDrops(String userName, StringBuilder msg) {
        try { dao.upsertMineItem(userName, MINE_ITEM_HARPY_CLEAR, 1); } catch (Exception ignore) { } // [2026-11-15] 나루터에서 강 건너 탑의 도시로
        List<HashMap<String, Object>> drops = mineBossDrops(userName, msg);
        dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[4], 1); // [2026-11-11] "하피 보상에 흑진주 100%"
        drops.add(mineDrop(S5MineMap.SEA_GEM_GRADE_BASE + 4, S5MineMap.SEA_GEM_NAMES[4], 1));
        msg.append(" · 💎 ").append(S5MineMap.SEA_GEM_NAMES[4]).append(" 1개");
        if (RND.nextInt(100) < MINE_HEART_PCT || isMineAdmin(userName)) { // [2026-11-17] 30 -> 35%
            dao.addUserOre(userName, S5MineMap.WIND_HEART_CODE, 1);
            drops.add(mineDrop(S5MineMap.WIND_HEART_GRADE, S5MineMap.WIND_HEART_NAME, 1));
            msg.append(" · 🌪️ ").append(S5MineMap.WIND_HEART_NAME).append(" 1개!!");
        }
        return drops;
    }
    List<HashMap<String, Object>> mineBossDrops(String userName, StringBuilder msg) {
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
    public HashMap<String, Object> mineAttack(String userName, String mapId, String ver, int x, int y, int mobId, int mc, String dir) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        if (mineWbDownUntil(userName, now) > 0) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("wbDown", mineWbDownUntil(userName, now)); so.put("message", "💫 쓰러져 있어요. 곧 다시 일어나요."); return so; } // [2026-11-15] 월드보스
        if (mineStunned(userName, now)) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("stunned", true); so.put("message", "💫 기절해서 움직일 수 없어요!"); return so; } // [2026-11-04] 붕대 / [2026-11-15] 히드라·거룡의 포효
        if (mobId == -1) return mineAuraAttack(userName, mapId, ver, x, y, dir); // [2026-11-04] 옆에 몬스터 없이 검기만
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        Long last = MINE_LAST_ATTACK.get(userName);
        if (last != null && now - last < MINE_ATTACK_GAP_MS) { out.put("tooFast", true); return out; }
        MINE_LAST_ATTACK.put(userName, now);
        if (mineFrozen(userName, now) != null) { out.put("frozen", true); out.put("message", MINE_FROZEN_BLOCK); return out; } // [2026-10-28] 얼음 여왕 빙결
        HashMap<String, Object> row = mineRowLocked(userName); // [2026-10-28] 성능 -- 행 확인(mineRow) + 잠금 조회 두 번을 잠금 조회 한 번으로
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int water = mineWalkMode(items); // [2026-10-28] 0 보통 / 1 물 위 / 2 바람의 신(모두 통과)
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
        if (mineBossDead(mob.type)) { out.put("message", "지금은 보스가 없어요. 보스 소환사에게 말을 걸면 불러낼 수 있어요."); out.put("mobId", mobId); out.put("mobDeadUntil", Long.MAX_VALUE / 2); return out; }
        if (type.minion && MINE_MOB_STATE.get(mineMobKey(map, mobId)) == null) { out.put("mobId", mobId); out.put("mobDeadUntil", Long.MAX_VALUE / 2); out.put("far", true); return out; } // [2026-10-28] 불려 나오지 않은 수하
        int lv = mineLv(row);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb);
        mineBerserkLow(items, hp, mineHpMax(lv, rb)); // [2026-11-04] 광전사의 장갑 2단계
        out.put("mobId", mobId);
        int mobMax = S5MineMobs.hp(type, mob.rank); // [2026-10-29] 개체 단계
        MobState s = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, mobId), k -> new MobState(mobMax));
        int dmg, counter = 0, kills = 0;
        boolean killed = false, crit;
        final boolean[] twStunHit = { false }; // [2026-11-15] 탑 수문장 반격의 기절(회피 판정 뒤에 건다)
        int[] aura = mineAuraActive(items); // [2026-11-04] 검기 -- 바로 앞(때린) 적에게도 속성 피해
        int auraRange = mineAuraRange(aura);
        double atkBase = mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items);
        List<int[]> auraMain = null;
        boolean assassin = MINE_ITEM_ASSASSIN_NECKLACE.equals(mineNeckCode(items)), assassinHit = false; // [2026-10-30] 자객의 목걸이
        // [2026-10-28] 닌자 -- evade = 은신으로 피함(피해 0, 반격 없음, 다음 한 방 2배), stealthHit = 은신했던 닌자의 2배 반격,
        //  cloneHit = 닌자 두목의 분신을 때림(펑 사라지고 두목 회복 + 함정 반격), splitEnd = 분신술 중 진짜를 때림, split = 이번에 분신술을 씀(자리 다섯)
        boolean evade = false, stealthHit = false, cloneHit = false, splitEnd = false, shrineBoss = mob.type == S5MineMobs.SHRINE_BOSS_TYPE;
        int[][] split = null;
        java.util.Set<String> bossHitters = null;
        synchronized (s) {
            if (s.deadUntil > now) { out.put("message", "이미 쓰러진 " + type.name + "이에요."); out.put("mobDeadUntil", s.deadUntil); return out; }
            if (mineHidden(map, type, now) || (mob.type == S5MineMobs.THUNDER_BOSS_TYPE && mineBossDead(mob.type))) { out.put("message", "⚡ 마물이 숲 속으로 사라졌어요."); out.put("mobDeadUntil", Math.max(now + 3_000L, mineThunderUntil)); return out; } // [2026-11-05] 숨은 마물 / 나오지 않은 천둥 황소
            if (s.deadUntil > 0) s.respawn(mobMax); // 다시 나타남
            int[] harpyAt = mob.type == S5MineMobs.HARPY_BOSS_TYPE ? mineHarpyNow(map, now) : null; // [2026-11-08] 하피 -- 지금 자리로
            if (harpyAt != null) { s.fx = harpyAt[0]; s.fy = harpyAt[1]; s.frozenUntil = Math.max(s.frozenUntil, now + MINE_MOB_FREEZE_MS); }
            if (S5MineMobs.diving(type, mob.id, now)) { out.put("dived", true); return out; } // [2026-11-18], [2026-11-19] "바다 몬스터와 전투 시 숨는다는 메시지 안 나오게" -- 안내 없이 안 맞기만
            if (harpyAt != null && harpyAt[2] == 1) { out.put("harpyHidden", true); out.put("message", "🌳 하피 여왕이 나무 뒤에 숨었어요 -- 공격이 닿지 않아요."); return out; } // [2026-11-08] 나무 뒤 = 필살기까지 모두 무효
            if (type.sim && now - s.lastHitAt >= MINE_SIM_HEAL_MS) s.hp = mobMax; // [2026-11-07] 보스 허수아비 -- 5초 안 맞으면 다 참
            if (type.boss) mineBossRegen(s, mobMax, now, map, mob); // [2026-10-29] 인식이 풀려 있던 동안 회복한 만큼
            int hitCell = -1;
            for (int c : mineMobCells(mob, s, now, map)) { // [2026-10-29] 보스는 2x2칸, "공격할 때 본인 위치도" -- 내 칸(0)과 둘레 8칸(1)
                int fw = type.foot(); // [2026-11-15] 거룡 3x3
                for (int oy = 0; oy < fw; oy++) for (int ox = 0; ox < fw; ox++)
                    if (Math.max(Math.abs(c % map.w + ox - x), Math.abs(c / map.w + oy - y)) <= mineMeleeReach(items) && (hitCell < 0 || c == mc)) hitCell = c; // [2026-11-20] 평타 사거리 = 배운 검기 사거리
            } // 화면이 보던 칸 우선. [2026-10-28] 대각선 포함 8방향
            // [2026-11-20] "악마/순간이동 몬스터가 순간이동하며 거리를 벌리면 데미지가 안 들어간다" -- 서버는 순간이동한 자리인데 화면은 다음 1초 턴까지 예전 자리를 보여 줘
            //  그 자리를 때리면 '닿지 않음'(안내도 없이) -- 화면은 때릴 때마다 몬스터를 제가 보던 칸에 다시 세워 계속 엇갈렸다.
            //  (1) 막 순간이동한 악마(1.5초 안)는 화면이 보던 칸(mc)이 사거리 안이고 실제 자리와 4칸 안이면 실제 자리로 맞힌다. (2) 그래도 빗나가면 실제 자리를 돌려줘 화면을 바로 맞춘다.
            if (hitCell < 0 && type.demon && mc >= 0 && now - s.lastMoveAt < 1_500L) {
                int mcx = mc % map.w, mcy = mc / map.w;
                if (Math.max(Math.abs(mcx - x), Math.abs(mcy - y)) <= mineMeleeReach(items) && Math.abs(mcx - s.fx) + Math.abs(mcy - s.fy) <= 4) hitCell = s.fy * map.w + s.fx;
            }
            if (hitCell < 0) {
                out.put("message", type.name + "에게 닿지 않아요. 바로 옆에서 공격하세요."); out.put("far", true);
                out.put("mobHp", s.hp); out.put("mobDeadUntil", s.deadUntil); out.put("mobFx", s.fx); out.put("mobFy", s.fy); out.put("mobFrozenUntil", s.frozenUntil); // [2026-11-20] 실제 자리로 화면을 맞춘다
                return out;
            }
            // [2026-11-20] "평타 사거리가 늘어서 멀리서 때려도 몬스터가 바로 반격한다 -- 반격은 1칸 앞까지 와서" -- 2칸 이상 떨어져서 때리면 반격 없음(쫓아와 붙은 뒤 전투 턴에 친다)
            int nearD = 99;
            for (int oy = 0; oy < type.foot(); oy++) for (int ox = 0; ox < type.foot(); ox++) nearD = Math.min(nearD, Math.max(Math.abs(hitCell % map.w + ox - x), Math.abs(hitCell / map.w + oy - y)));
            boolean farHit = nearD > 1;
            crit = RND.nextInt(100) < 10;
            dmg = Math.max(1, (int) Math.round((mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items)) * (0.85 + RND.nextDouble() * 0.3) * (crit ? 1.6 : 1.0) * mineElemMul(items, type))); if (mineElemMul(items, type) > 1) out.put("elHit", true);
            if (MINE_ITEM_TWIN_GLOVES.equals(mineGloveCode(items))) { // [2026-10-30] 쌍격의 장갑 -- 55%씩 두 번(타격마다 치명 15%), 화면엔 숫자 두 개
                double base = (mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items)) * mineElemMul(items, type) * mineTwinPct(items);
                int[] hs = new int[2]; boolean[] cs = new boolean[2];
                for (int hi = 0; hi < 2; hi++) { cs[hi] = RND.nextInt(100) < 10 + mineTwinCrit(items); hs[hi] = Math.max(1, (int) Math.round(base * (0.85 + RND.nextDouble() * 0.3) * (cs[hi] ? 1.6 : 1.0))); }
                dmg = hs[0] + hs[1];
                crit = cs[0] || cs[1];
                out.put("hits", hs);
                out.put("hitCrits", cs);
            } // [2026-10-29] 닌자의 검, 속성 상성 x1.5 // [2026-10-28] 환생/광전사/양산 보너스는 더해서(mineAtkMul)
            if (assassin && !type.dummy && minePStealthOf(userName, now) != null) { // [2026-10-30] 자객의 목걸이 -- 은신 중 공격: 풀리며 2배
                MINE_PSTEALTH.remove(userName);
                dmg *= 2;
                Object hv = out.get("hits");
                if (hv instanceof int[]) for (int hi = 0; hi < ((int[]) hv).length; hi++) ((int[]) hv)[hi] *= 2;
                assassinHit = true;
            }
            // [2026-11-11] 일반 공격도 하피에게 들어간다(나무 뒤에 숨었을 때만 모두 무효)
            double vul = mineVulnMul(s, now) * type.armor; // [2026-11-08] 만검진 받는 피해 증가, [2026-11-23] 탑 수문장 갑피
            if (vul != 1) { dmg = (int) Math.round(dmg * vul); Object hv2 = out.get("hits"); if (hv2 instanceof int[]) for (int hi = 0; hi < ((int[]) hv2).length; hi++) ((int[]) hv2)[hi] = (int) Math.round(((int[]) hv2)[hi] * vul); }
            if (type.clone) { // [2026-10-28] 분신 -- 한 대에 펑 사라지고, 두목이 기운을 되찾으며 함정처럼 반격(두목 공격력)
                S5MineMap.Mob bm = mineBossOf(map);
                MobState bst = bm == null ? null : MINE_MOB_STATE.get(mineMobKey(map, bm.id));
                if (bst == null || bst.splitUntil <= now) { // 분신술이 이미 끝났다(화면이 아직 몰랐을 뿐) -- 함정/회복 없이 사라진 것으로
                    s.hp = 0;
                    s.deadUntil = Long.MAX_VALUE / 2;
                    s.frozenUntil = 0;
                    out.put("message", "💨 분신이 이미 사라졌어요.");
                    out.put("mobHp", 0);
                    out.put("mobDeadUntil", s.deadUntil);
                    out.put("mobFx", s.fx);
                    out.put("mobFy", s.fy);
                    out.put("mobFrozenUntil", 0);
                    return out;
                }
                cloneHit = true;
                dmg = 0;
                crit = false;
                s.hp = 0;
                s.deadUntil = Long.MAX_VALUE / 2;
                s.frozenUntil = 0;
                counter = farHit ? 0 : Math.max(1, (int) Math.round(S5MineMobs.TYPES.get(S5MineMobs.SHRINE_BOSS_TYPE).atk() * MINE_CLONE_TRAP_MULT * (0.8 + RND.nextDouble() * 0.4))); // [2026-11-20] 멀리서 때리면 함정 반격 없음
            } else if (shrineBoss && s.stealth && ++s.stealthHits < MINE_BOSS_STEALTH_HITS) { // [2026-10-28] 닌자 두목 은신 -- 3번째 공격까지 계속 피한다
                evade = true;
                dmg = 0;
                crit = false;
                s.lastHitAt = now;
                s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                if (s.target == null || !mineEngaged(s.target, now)) s.target = userName;
                mineEngage(userName, map, mobId, now, now - MINE_MOB_STRIKE_GAP_MS / 2);
                out.put("stealthLeft", MINE_BOSS_STEALTH_HITS - s.stealthHits);
            } else if (type.ninja && !s.stealth && s.splitUntil <= now && RND.nextInt(100) < MINE_NINJA_STEALTH_PCT) { // [2026-10-28] 은신 -- 공격을 피한다
                if (shrineBoss) { s.stealthHits = 0; out.put("stealthLeft", MINE_BOSS_STEALTH_HITS); }
                evade = true;
                dmg = 0;
                crit = false;
                s.stealth = true;
                s.lastHitAt = now;
                s.fx = hitCell % map.w;
                s.fy = hitCell / map.w;
                s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                if (s.target == null || !mineEngaged(s.target, now)) s.target = userName;
                mineEngage(userName, map, mobId, now, now - MINE_MOB_STRIKE_GAP_MS / 2); // 곧 2배로 기습한다
            } else if (type.dummy) { // [2026-10-29] 허수아비 -- 맞아도 줄지 않고 반격/인식도 없다(데미지 시험)
                s.fx = hitCell % map.w; s.fy = hitCell / map.w; // [2026-11-08] 허수아비 자리(다른 사람 화면의 피해 숫자 위치)
                if (auraRange > 0) auraMain = mineAuraMainHits(userName, map, type, hitCell, x, y, dir, auraRange, aura, atkBase, vul);
                s.lastHitAt = now;
                out.put("dummyHit", true);
                if (type.sim) { // [2026-11-07] 보스 허수아비 -- 체력이 줄고, 0이 되면 MINE_SIM_DOWN_MS 동안 못 때린다(그 뒤 다 찬 채로)
                    s.hp -= dmg;
                    if (auraMain != null) for (int[] h : auraMain) s.hp -= h[1];
                    if (s.hp <= 0) { s.hp = 0; s.deadUntil = now + MINE_SIM_DOWN_MS; out.put("simDown", true); }
                }
            } else {
                int wbB = s.hp; // [2026-11-15] 월드보스 기여도
                s.hp -= dmg;
                if (auraRange > 0) { auraMain = mineAuraMainHits(userName, map, type, hitCell, x, y, dir, auraRange, aura, atkBase, vul); for (int[] h : auraMain) s.hp -= h[1]; } // [2026-11-04] 검기 속성 피해
                if (MINE_ITEM_BH_ORB.equals(mineGloveCode(items)) && s.hp > 0 && RND.nextInt(100) < MINE_BH_ORB_PCT) { s.hp -= MINE_BH_ORB_DMG; out.put("waterCannon", MINE_BH_ORB_DMG); } // [2026-11-23] 베히모스의 수정구슬 -- 공중 물대포(물 속성 고정 20만)
                if (mob.type == S5MineMobs.WB_BOSS_TYPE) s.hp = mineWbApply(userName, wbB, wbB - Math.max(0, s.hp), now, true); // [2026-11-15] 몫으로 바꿔 깎는다
                mineKrkFloor(s, now); // [2026-11-19] 기믹 전엔 크라켄 50% 아래로 안 내려간다
                if (s.hp > 0 && MINE_ITEM_TWIN_GLOVES.equals(mineGloveCode(items)) && !type.dummy && !type.sim && mob.type != S5MineMobs.WB_BOSS_TYPE && mob.type != S5MineMobs.KRAKEN_TYPE && RND.nextInt(100) < mineTwinStunPct(items)) { // [2026-11-20] 쌍격의 장갑 -- 2초 기절
                    s.stunUntil = Math.max(s.stunUntil, now + MINE_TWIN_STUN_MS); s.frozenUntil = Math.max(s.frozenUntil, s.stunUntil);
                    out.put("twinStun", true);
                }
                s.lastHitAt = now;
                mineHitBy(s, type, userName); // [2026-11-13] 보스를 때린 사람(함께 싸운 보상) -- 예전엔 닌자 두목만
                if (s.hp <= 0) {
                    killed = true;
                    s.hp = 0;
                    // 보스(동굴 3층/타락한 대주교)는 다음 맵 리셋까지, [2026-10-28] 대주교 수하는 다시 불러낼 때까지, 닌자 두목은 10분 뒤(보상은 한 사람에 한 번)
                    s.deadUntil = shrineBoss ? now + MINE_SHRINE_BOSS_RESPAWN_MS : type.boss && type.respawnOverride > 0 ? now + type.respawnOverride : type.boss ? now + MINE_BOSS_RESPAWN_MS : type.minion ? Long.MAX_VALUE / 2 : now + type.respawnMs(); // [2026-10-28] 보스 2시간
                    s.frozenUntil = 0;
                    splitEnd = s.splitUntil > now;
                    s.splitUntil = 0;
                    s.stealth = false;
                    if (type.boss) bossHitters = s.hitters == null ? new java.util.HashSet<>() : new java.util.HashSet<>(s.hitters); // [2026-11-13] 보스 공통
                } else {
                    s.fx = hitCell % map.w;
                    s.fy = hitCell / map.w;
                    s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                    counter = type.venom ? 0 : Math.max(1, (int) Math.round(S5MineMobs.atk(type, mob.rank) * mineNightAtk(map, type, now) * mineBoroMul(mob.type, now) * (0.8 + RND.nextDouble() * 0.4))); // [2026-10-30] "여왕개미는 원거리만" -- 근접 반격 없음(독산만)
                    if (mob.type == S5MineMobs.WB_BOSS_TYPE) counter = Math.max(1, (int) Math.round(mineHpFull(lv, rb) * MINE_WB_COUNTER_PCT * (mineWbBh() ? MINE_BH_DMG_MUL : 1) * (0.8 + RND.nextDouble() * 0.4))); // [2026-11-15] 거룡의 반격 = 최대 체력 %(공격 수치 3만을 그대로 쓰면 누구든 두 대에 쓰러진다)
                    if (farHit) counter = 0; // [2026-11-20] 2칸 이상 떨어져 때리면 반격 없음(은신 기습/수문장 강타도 붙은 뒤에)
                    if (s.stealth && counter > 0) { counter *= MINE_NINJA_STEALTH_MULT; s.stealth = false; stealthHit = true; } // [2026-10-28] 은신했던 닌자의 기습
                    if (type.boss && S5MineMap.towerFloor(map.id) > 0 && counter > 0) { // [2026-11-15] 탑 수문장 -- 반격에도 시즌5 패턴(강타 / 29층부터 기절)
                        int[] ts = mineTwStrike(userName, S5MineMap.towerFloor(map.id), s, counter, now);
                        counter = ts[0];
                        if (ts[1] > 0) out.put("heavy", true);
                        twStunHit[0] = ts[2] > 0;
                    }
                    if (s.stunUntil > now) { counter = 0; twStunHit[0] = false; }
                    if (mob.type == S5MineMobs.KRAKEN_TYPE) counter = 0; // [2026-11-19] "모션이 나올 때만 공격받게" -- 크라켄은 반격 없이 휘두르기(전투 턴)로만 // [2026-11-16] "일섬참으로 기절한 몬스터가 반격하지 않게" -- 기절해 있는 동안은 때려도 반격(강타/기절 포함) 없음
                    if (s.target == null || !mineEngaged(s.target, now)) s.target = userName; // [2026-10-25] 이 유저를 쫓는다
                    mineEngage(userName, map, mobId, now, now); // 반격도 한 턴
                    if (mob.type == S5MineMobs.RAID_BOSS_TYPE) mineRaidAwakeUntil = now + MINE_RAID_AWAKE_MS; // [2026-10-28] 대주교가 깨어난다(마법)
                    if (mob.type == S5MineMobs.ICE_BOSS_TYPE) mineIceAwakeUntil = now + MINE_ICE_AWAKE_MS; // [2026-10-28] 얼음 여왕이 깨어난다(빙결)
                    if (shrineBoss) {
                        if (s.splitUntil > now) { splitEnd = true; s.splitUntil = 0; } // 진짜를 찾았다 -- 분신이 사라진다
                        else {
                            long max = mobMax;
                            int stage = s.hp * 4L <= max ? 3 : s.hp * 4L <= max * 2 ? 2 : s.hp * 4L <= max * 3 ? 1 : 0;
                            if (stage > s.splitStage) { // [2026-10-28] 분신술 -- 다섯 자리 중 하나로 두목이 옮겨 가고 나머지 넷에 분신
                                s.splitStage = stage;
                                s.splitUntil = now + MINE_SPLIT_MS;
                                int[][] spots = map.shrineSplitSpots(RND.nextInt(5));
                                int real = RND.nextInt(spots.length);
                                for (int k = 0; k < spots.length; k++) if (spots[k][0] == x && spots[k][1] == y) { // 때린 플레이어가 선 칸은 비킨다
                                    int nx = spots[k][0] + (spots[k][0] < map.bossX() ? 1 : -1);
                                    if (map.walkable(nx, spots[k][1])) spots[k] = new int[] { nx, spots[k][1] };
                                }
                                s.fx = spots[real][0];
                                s.fy = spots[real][1];
                                s.frozenUntil = s.splitUntil;
                                s.stealth = false;
                                counter = 0; // 연막을 터뜨리며 빠진다(반격 없음)
                                split = new int[spots.length - 1][];
                                for (int k = 0, j = 0; k < spots.length; k++) if (k != real) split[j++] = spots[k];
                            }
                        }
                    }
                }
            }
            out.put("mobHp", s.hp);
            out.put("mobDeadUntil", s.deadUntil);
            out.put("mobFx", s.fx);
            out.put("mobFy", s.fy);
            out.put("mobFrozenUntil", s.frozenUntil);
            out.put("mobStealth", s.stealth);
        }
        int enchLeft = items.getOrDefault(MINE_ITEM_ENCH_LEFT, 0); // [2026-10-29] 속성 부여 -- 공격마다 1회
        if (enchLeft > 0) { dao.upsertMineItem(userName, MINE_ITEM_ENCH_LEFT, enchLeft - 1); out.put("enchLeft", enchLeft - 1); }
        // [2026-10-28] 분신술 시작/끝(분신 상태는 두목 잠금 밖에서 -- 잠금을 겹쳐 잡지 않는다)
        if (split != null || splitEnd || cloneHit) {
            List<long[]> rows = new ArrayList<>();
            S5MineMap.Mob boss = mineBossOf(map);
            int ci = 0;
            for (S5MineMap.Mob mb : map.mobs()) {
                if (!S5MineMobs.TYPES.get(mb.type).clone) continue;
                String key = mineMobKey(map, mb.id);
                if (split != null && ci < split.length) {
                    MobState cs = new MobState(1);
                    cs.fx = split[ci][0]; cs.fy = split[ci][1];
                    cs.frozenUntil = now + MINE_SPLIT_MS;
                    cs.lastHitAt = now;
                    MINE_MOB_STATE.put(key, cs);
                    rows.add(mineMobRow(mb.id, cs));
                    ci++;
                } else if (splitEnd) {
                    MobState cs = MINE_MOB_STATE.get(key);
                    if (cs == null) continue;
                    synchronized (cs) { if (cs.deadUntil <= now) { cs.hp = 0; cs.deadUntil = Long.MAX_VALUE / 2; cs.frozenUntil = 0; } rows.add(mineMobRow(mb.id, cs)); }
                }
            }
            if (cloneHit && boss != null) { // 분신을 때리면 두목이 최대 체력의 5%를 되찾는다
                MobState bs = MINE_MOB_STATE.get(mineMobKey(map, boss.id));
                if (bs != null) synchronized (bs) {
                    if (bs.deadUntil <= now) { int bm = S5MineMobs.TYPES.get(boss.type).hp(); bs.hp = Math.min(bm, bs.hp + bm * MINE_CLONE_HEAL_PCT / 100); }
                    rows.add(mineMobRow(boss.id, bs));
                }
            }
            out.put("mobRows", rows);
            if (split != null) { out.put("split", true); out.put("message", "🥷 닌자 두목이 분신술을 썼어요! 다섯 중 자세가 다른 하나가 진짜예요. 분신을 때리면 두목이 기운을 되찾아요!"); }
            else if (splitEnd && !killed) { out.put("splitEnd", true); out.put("message", "🎯 진짜를 찾았어요! 분신들이 연기처럼 사라졌어요."); }
            else if (cloneHit) { out.put("clone", true); out.put("message", "💨 분신이었어요! 펑 터지며 반격당했고, 닌자 두목이 기운을 조금 되찾았어요."); }
        }
        if (evade) { out.put("evade", true); out.put("message", "💨 " + type.name + "이(가) 은신해 공격을 피했어요! 다음 공격이 아플 거예요."); }
        if (stealthHit) out.put("stealthHit", true);
        if (assassin) { // [2026-10-30] 자객의 목걸이 -- 은신(이번 반격부터 피한다) / 반격 회피
            if (assassinHit) out.put("assassinHit", true);
            else if (!type.dummy && RND.nextInt(100) < MINE_ASSASSIN_PCT) { MINE_PSTEALTH.put(userName, new long[] { MINE_ASSASSIN_DODGES, now + MINE_ASSASSIN_MS }); out.put("assassinOn", true); }
            if (!killed && counter > 0 && minePStealthDodge(userName, now)) { counter = 0; out.put("dodged", true); }
        }
        if (!killed && counter > 0 && twStunHit[0]) { mineTwStun(userName, now, out); out.put("stunHit", true); } // [2026-11-15] 히드라의 기절(회피했으면 안 걸림)
        long[] pst = minePStealthOf(userName, now);
        out.put("pStealth", pst == null ? null : pst);
        out.put("ok", true);
        out.put("dmg", dmg);
        out.put("crit", crit);
        out.put("killed", killed);
        out.put("mobHpMax", mobMax);
        StringBuilder msg = new StringBuilder();
        boolean died = false;
        int levelUp = 0;
        if (killed) {
            kills = 1;
            boolean bossRw = !type.boss || S5MineMap.towerFloor(map.id) > 0 || mineBossRwTake(userName, mob.type); // [2026-11-20] 보스 보상 하루 5번
            mineThunderKill(map, type, now); // [2026-11-05] 밤 마물 처치 수
            msg.append(mineTowerKill(userName, map, mob, type)); // [2026-11-15] 탑 -- 시즌5 PP, 수문장
            if (mob.type == S5MineMobs.THUNDER_BOSS_TYPE) { // [2026-11-05] 천둥 황소 처치 -- 마물이 돌아온다
                mineThunderEnd(map, now);
                out.put("shock", Collections.emptyList());
                out.put("bossKilled", true);
            }
            boolean wasReady = mineRebirthReady(lv, exp, rb);
            int mindPct = mineMindExpPct(items); // [2026-10-28] 정신의 목걸이
            long baseExp = mineLuckExp(items, mineNightExp(map, type, mineTwExp(userName, map, type, S5MineMobs.exp(type, mob.rank)), now), now), expGain = baseExp + baseExp * mindPct / 100; // [2026-11-05] 마물의 숲 밤 1.5배 // [2026-10-29] 개체 단계
            long[] xa = mineExpAdd(lv, exp, expGain, rb); // [2026-11-07] 실제로 오른 경험치(99레벨에서 가득 차면 안 오른다), [2026-11-08] 진환생은 99를 넘어
            lv = (int) xa[0]; exp = xa[1]; levelUp += (int) xa[2];
            mineMercShare(userName, items, expGain, type.lv(), 1, out); // [2026-11-06] 용병 경험치 = 내 경험치의 1%(레벨 차이 보정, 처치마다 최소 1)
            if (!wasReady && mineRebirthReady(lv, exp, rb)) out.put("rebirthReadyNow", true);
            if (levelUp > 0) { // 레벨이 오르면 체력이 다 차고 [2026-10-25] 잠긴 최대 체력도 풀린다
                hp = mineHpFull(lv, rb); // [2026-11-18] 갑옷 체력까지
                if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.remove("hpLockUp"); out.put("hpLockFree", true); }
            }
            long expReal = xa[3]; // [2026-11-07] "경험치가 실제로 안 오르는 양이 표기" -- 가득 찬 99레벨은 처치해도 경험치가 안 오른다. 오른 만큼만 알린다
            if (expReal < expGain) out.put("expFull", true);
            out.merge("expReal", expReal, (p, q) -> (Long) p + (Long) q);
            msg.append("⚔️ ").append(type.name).append("을(를) 쓰러뜨렸어요!").append(expReal > 0 ? " 경험치 +" + expReal + (mindPct > 0 ? " (📿+" + mindPct + "%)" : "") : "");
            if (levelUp > 0) msg.append(" 🎉 레벨 업! Lv.").append(lv);
            out.put("expGain", expReal);
            if (mob.type == S5MineMobs.RAID_BOSS_TYPE) { // [2026-10-28] 타락한 대주교 처치 -- 이번 주기엔 다시 안 나온다(DB 기록), 마법/소환 멈춤
                mineRaidAwakeUntil = 0;
                mineBossKilledNow(MINE_RAID_KEY, userName, now); // [2026-10-28] 2시간 뒤 다시
                msg.insert(0, "👑 타락한 대주교를 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (mob.type == S5MineMobs.ICE_BOSS_TYPE) { // [2026-10-28] 얼음 여왕 처치 -- 이번 주기엔 다시 안 나온다(DB 기록), 빙결 멈추고 얼음을 모두 녹인다
                mineIceAwakeUntil = 0;
                mineIceNextFreeze = 0;
                mineIceThawAll();
                mineBossKilledNow(MINE_ICE_KEY, userName, now); // [2026-10-28] 2시간 뒤 다시
                msg.insert(0, "👑 얼음 여왕을 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (type.noReward) msg.append(" (여왕개미는 따로 주는 게 없어요 -- 10분 뒤 다시 나타나요)"); // [2026-10-28]
            boolean twNo = mineTwNoRw(userName, map, type); // [2026-11-23] 탑 수문장 하루 횟수를 다 썼으면 장비도 없다(안내는 mineTowerKill이)
            if (bossRw && !twNo) mineWeaponDrop(userName, items, type, mob.rank, msg, out); // [2026-11-04] 검기로 쓰러뜨린 몬스터도 같은 규칙이라 따로 뺐다
            else if (!twNo) msg.append(mineBossRwNoMsg(type.name)); // [2026-11-20]
            if (mob.type == S5MineMobs.HARPY_BOSS_TYPE) { // [2026-11-08] 하피 처치 -- 2시간 뒤 다시
                mineBossKilledNow(MINE_HARPY_KEY, userName, now);
                mineHarpyReset();
                msg.insert(0, "👑 하피 여왕을 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (mob.type == S5MineMobs.PHARAOH_BOSS_TYPE) { // [2026-11-04] 파라오 미라 처치 -- 2시간 뒤 다시
                mineBossKilledNow(MINE_PHARAOH_KEY, userName, now);
                MINE_RANGED_NEXT.clear();
                msg.insert(0, "👑 파라오 미라를 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (mob.type == S5MineMobs.LAVA_BOSS_TYPE) { // [2026-10-28] 용암 거신 처치 -- 이번 주기엔 다시 안 나온다(DB 기록)
                mineBossKilledNow(MINE_LAVA_KEY, userName, now); // [2026-10-28] 2시간 뒤 다시
                MINE_RANGED_NEXT.clear();
                MINE_LAVA.clear(); // 전투 끝 -- 용암이 식는다
                out.put("lava", Collections.emptyList());
                msg.insert(0, "👑 용암 거신을 쓰러뜨렸어요! ");
                out.put("bossKilled", true);
            }
            if (mob.type == S5MineMobs.BOSS_TYPE) { // [2026-10-24] 보스 처치 -- 이번 주기엔 다시 안 나온다(DB 기록)
                mineBossKilledNow(MINE_BOSS_KEY, userName, now); // [2026-10-28] 2시간 뒤 다시
                msg.insert(0, "👑 보스 처치! ");
                out.put("bossKilled", true);
            }
            if (shrineBoss) { // [2026-10-28] 닌자 두목 처치 -- 10분 뒤 다시 나타난다, 처음 잡는 사람(함께 때린 사람 포함)에게 비급서 + 달빛석
                msg.insert(0, "👑 보스 처치! ");
                out.put("bossKilled", true);
            }
            // [2026-10-28] 신궁 수색 임무 -- 닌자 처치 수(1~3층 어디서든, 열쇠 보상)
            if (S5MineMap.isShrineFloor(map.id) && type.ninja && items.getOrDefault(MINE_ITEM_NINJA_QUEST, 0) == 1) {
                int nk = items.getOrDefault(MINE_ITEM_NINJA_KILLS, 0) + 1;
                dao.upsertMineItem(userName, MINE_ITEM_NINJA_KILLS, nk);
                out.put("ninjaKills", nk);
                if (nk < MINE_NINJA_NEED && items.getOrDefault(S5MineMap.SHRINE_KEY_ITEM, 0) < 1 && RND.nextInt(100) < MINE_NINJA_KEY_PCT) {
                    // [2026-10-28] "닌자 100마리 퀘스트 진행 중 확률적으로 보스방 열쇠" -- 수색 중 닌자를 쓰러뜨릴 때마다 MINE_NINJA_KEY_PCT%로 열쇠가 떨어진다(임무 끝)
                    dao.upsertMineItem(userName, S5MineMap.SHRINE_KEY_ITEM, 1);
                    dao.upsertMineItem(userName, MINE_ITEM_NINJA_QUEST, 0);
                    dao.upsertMineItem(userName, MINE_ITEM_NINJA_KILLS, 0);
                    msg.append(" 🗝️ 닌자가 신궁 열쇠를 떨어뜨렸어요! 수색은 여기까지 -- 동쪽 문(닌자 두목의 방)으로 들어갈 수 있어요.");
                    out.put("shrineKey", true);
                }
                else if (nk == MINE_NINJA_NEED) msg.append(" 🥷 닌자 ").append(MINE_NINJA_NEED).append("명 처치! 신궁 수문장에게 돌아가 열쇠를 받으세요.");
                else if (nk < MINE_NINJA_NEED && nk % 10 == 0) msg.append(" 🥷 수색 ").append(nk).append("/").append(MINE_NINJA_NEED);
            }
            List<HashMap<String, Object>> killDrops = !bossRw || type.noReward || mob.type == S5MineMobs.WB_BOSS_TYPE ? new ArrayList<HashMap<String, Object>>() : shrineBoss ? mineShrineBossDrops(userName, map, type, bossHitters, msg)
                    : mob.type == S5MineMobs.RAID_BOSS_TYPE ? mineRaidDrops(userName, msg) : mob.type == S5MineMobs.ICE_BOSS_TYPE ? mineIceDrops(userName, msg) : mob.type == S5MineMobs.LAVA_BOSS_TYPE ? mineLavaDrops(userName, msg) : mob.type == S5MineMobs.PHARAOH_BOSS_TYPE ? mineDesertReward(userName, msg) : mob.type == S5MineMobs.THUNDER_BOSS_TYPE ? mineThunderDrops(userName, msg) : mob.type == S5MineMobs.HARPY_BOSS_TYPE ? mineHarpyDrops(userName, msg) : mob.type == S5MineMobs.BORO_BOSS_TYPE ? mineBoroDrops(userName, msg) : mob.type == S5MineMobs.KRAKEN_TYPE ? mineKrakenDrops(userName, msg) : type.boss ? mineBossDrops(userName, msg) : mineMobDrops(userName, map, type, msg, mob.rank);
            if (bossRw && type.boss && !type.noReward && mob.type != S5MineMobs.WB_BOSS_TYPE) mineBossShine(userName, killDrops, msg); // [2026-11-05] 보스 공통 -- 달빛석/별빛석 확률
            if (S5MineMobs.sqIndex(mob.type) >= 0) mineSqReward(userName, msg); // [2026-11-23] 최강다람쥐 -- 다람쥐메달
            if (type.boss && mineBossRwCounted(mob.type) && S5MineMap.towerFloor(map.id) <= 0) { int rl = mineBossRwLeft(userName, mob.type); out.put("bossRwLeft", rl); if (bossRw) msg.append(" 🎁 오늘 보상 ").append(rl).append("/").append(MINE_BOSS_RW_MAX).append("번 남음"); } // [2026-11-20]
            if (type.boss) { int sh = mineBossShare(userName, map, mob, bossHitters); if (sh > 0) msg.append(" 🤝 함께 싸운 ").append(sh).append("명도 같은 보상을 받았어요!"); } // [2026-11-13]
            if (type.boss) out.put("bossName", type.name); // [2026-11-07] 관리자 알림(보스 전용 아이템이 나왔는지만)
            if (type.boss) { // [2026-11-17] "보스/상자 이펙트가 실제 드랍보다 너무 크다 -- 실제 개수만큼" -- 한 사람 몫(함께 싸운 사람도 같은 보상)을 적어 둔다
                List<Integer> fg = new ArrayList<>();
                for (HashMap<String, Object> w : killDrops) { Object g = w.get("grade"); if (g == null) continue; int q = Math.max(1, intVal(w.get("qty"), 1)); for (int k = 0; k < q && fg.size() < 40; k++) fg.add(intVal(g, 1)); }
                MobState ks2 = MINE_MOB_STATE.get(mineMobKey(map, mob.id));
                if (ks2 != null) synchronized (ks2) { ks2.fxG = fg.isEmpty() ? null : fg; } // 따로 주는 보상(월드보스 등)은 비워 두면 화면이 사람당 6개로
            }
            out.put("drops", killDrops);
            out.put("ores", mineOreList(userName));
            if (type.boss) { // [2026-11-05] 왕도 소문 -- 혼자서 / 일행이
                int fighters = 0;
                for (MineEngage me2 : MINE_ENGAGE.values()) if (map.version().equals(me2.mapVer) && now - me2.at <= MINE_ENGAGE_TTL_MS && me2.mobs.containsKey(mob.id)) fighters++;
                mineGossipAdd(mineNick(userName) + (fighters <= 1 ? " 님이 혼자서 " : " 님 일행이 ") + type.name + "을(를) 쓰러뜨렸대요!");
            }
        } else {
            counter = mineCoatHit(items, counter); // [2026-10-26] 방한 외투 -- 받는 피해 2배([2026-10-28] 갑옷 1.5배)
            if (counter > 0 && mineWyrmSkinOn(items)) counter = Math.max(1, (int) Math.round(counter * MINE_WYRM_SKIN_TAKE)); // [2026-11-23] 거룡의 피부조각
            if (counter > 0 && mineBerserkInvul(userName, now)) { counter = 0; out.put("berserkInv", true); } // [2026-11-20] 광전사 불굴 -- 무적 중
            if (counter > 0 && mineVeinBreak(userName)) out.put("veinBroken", true); // [2026-10-28] 설산 동굴 -- 반격에 맞아도 끊긴다
            if (counter > 0 && MINE_ITEM_REGEN_GLOVES.equals(mineGloveCode(items))) mineHealAdd(userName, (int) Math.round(counter * mineRegenPct(items)), now); // [2026-10-28] 재생의 장갑
            int hpc0 = hp;
            hp -= counter;
            out.put("counter", counter);
            if (hp <= 0 && hpc0 > 1 && mineWyrmSkinOn(items) && mineWyrmSave(userName, now, out)) hp = 1; // [2026-11-23] 거룡의 피부조각 -- 즉사 방지(광전사 불굴보다 먼저)
            if (hp <= 0 && mineBerserkSave(userName, items, now, out)) hp = 1; // [2026-11-20] 광전사 불굴
            if (hp <= 0) {
                died = true;
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCapN(lv, lock, rb);
                msg.append("💫 ").append(type.name).append("에게 쓰러졌어요... ").append(mineRespawnMsg(userName, items, now)).append(mineLockText(out));
                long wbd = mineWbDownUntil(userName, now); // [2026-11-15] 월드보스 -- 화면 회색 + 카운트
                if (wbd > 0) out.put("wbDown", wbd);
            }
        }
        if (killed) { MineEngage me = MINE_ENGAGE.get(userName); if (me != null) me.mobs.remove(mobId); }
        if (auraMain != null) out.put("aura", auraMain);
        if ((auraRange > 0 || mineWhirlOn(mineWhirlActive(items))) && !evade && !cloneHit) { // [2026-11-04] 검기 -- 바라보는 방향 앞 사거리 칸의 다른 몬스터 // [2026-11-07] + 돌개베기(둘레 8칸)
            StringBuilder am = new StringBuilder();
            java.util.Set<Integer> skA = new java.util.HashSet<>(), skW = new java.util.HashSet<>(); // [2026-11-22] 검기/돌개 겹치면 센 쪽만
            mineAuraWhirlPick(userName, map, items, aura, auraRange, x, y, dir, mobId, now, skA, skW);
            long[] ar, wr;
            try {
                MINE_SWEEP_SKIP.set(skA);
                ar = auraRange > 0 ? mineAuraSweep(userName, map, items, aura, auraRange, atkBase, x, y, dir, mobId, now, am, out) : new long[] { 0, 0 };
                MINE_SWEEP_SKIP.set(skW);
                wr = mineWhirlSweep(userName, map, items, atkBase, x, y, now, am, out);
            } finally { MINE_SWEEP_SKIP.remove(); }
            ar[0] += wr[0]; ar[1] += wr[1];
            if (ar[0] > 0) {
                mineMercShare(userName, items, ar[1], lv, ar[0], out); // [2026-11-06] 용병 경험치(검기 처치)
                long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, ar[1], died, out);
                lv = (int) g[0]; exp = g[1]; hp = (int) g[2]; lock = (int) g[3]; levelUp += (int) g[4];
                kills += (int) ar[0];
            }
            if (am.length() > 0) msg.append(msg.length() > 0 ? " " : "").append(am);
        }
        if (!died) { int kh = mineRegenKillHeal(items, kills, mineHpMax(lv, rb)); if (kh > 0) { hp = mineHealTo(hp, kh, lv, lock, rb); out.put("killHeal", kh); } } // [2026-11-04] 재생의 장갑 2단계
        if (!died && !mineJustDied(userName)) dao.updateMinePos(userName, map.id, x, y, now); // [2026-11-17] 다른 요청(전투 턴)에서 방금 쓰러졌으면 위치를 되돌리지 않는다
        else if (!died) hp = mineHpCapN(lv, lock, rb); // 체력도 부활 체력 그대로
        dao.updateMineCombat(userName, lv, exp, hp, now, kills);
        out.put("levelUp", levelUp);
        out.put("died", died);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, mineEngaged(userName, now), lock, rb));
        out.put("healUntil", mineHealUntil(userName)); // [2026-10-28] 재생의 장갑 -- 이때까지 화면이 전투 턴을 부른다
        if (msg.length() > 0) out.put("message", msg.toString());
        if (died) out.put("state", mineState(userName));
        minePresAtk(userName, dir, aura, out); // [2026-11-07] 다른 사람 화면에 공격 모션/피해 숫자
        return out;
    }

    /** 처치 보상 -- [2026-10-24] "낮은 게 조금 더 잘 나오는 느낌으로" 조정(처음엔 60% + 높은 등급 35%라 Lv.33 몬스터에서 미스릴이 시간당 100개 넘게 나왔다).
     *  55% 확률로 광석: 기준 등급 b = 1 + (단계-1)/2(구리~미스릴). 55% = 한 등급 아래(1~2개), 37% = 기준(금광석까지), 8% = 한 등급 위(금광석까지,
     *  미스릴은 9단계 몬스터의 8%뿐), 별빛석은 안 나옴. 보석은 캘 때와 같은 확률에 흔한 보석만 1.5배(5% -> 7.5%), 다이아 0.2%. */
    /** [2026-10-29] rank = 개체 단계 -- 하급: 광석 40%·보석 x0.7 / 보통: 55%·x1 / 상급: 85%·개수 x2·보석 x2(무기 확률·등급은 mineWeaponPermil/mineGradeRoll). */
    static final int[] MINE_RANK_ORE_PCT = { 40, 55, 85 };
    static final int MINE_WEST_GEM_P10K = 100; // [2026-11-06] 마물의 숲 보석 1%
    static final double[] MINE_RANK_GEM_MUL = { 0.7, 1.0, 2.0 };
    List<HashMap<String, Object>> mineMobDrops(String userName, S5MineMap map, S5MineMobs.Type type, StringBuilder msg, int rank) {
        if (type.chaos) return mineChaosDrops(userName, type, msg); // [2026-11-17] 혼돈스러운 마계
        List<HashMap<String, Object>> drops = new ArrayList<>();
        mineSealDrop(userName, map, type, msg, drops); // [2026-11-17] 들판 인장
        if (S5MineMap.WESTROAD.equals(map.id)) { // [2026-11-05] "그 맵은 몬스터가 광석을 안 떨어뜨려도 된다" -- 처치마다 DB에 쓰던 것도 없어진다(경험치는 공격마다 한 번에)
            // [2026-11-06] "보석 드랍 1% 확률, 모든 보석" -- 땅 보석 9종 + 바다 보석 5종 중 하나(고르게), 행운의 주문서면 2%. 맞을 때만 아이템을 읽고 쓴다.
            Map<String, Integer> wit = mineItems(userName);
            int roll = (int) (RND.nextInt(10000) / mineHuntGemMul(wit)); // [2026-11-13] 사냥꾼의 목걸이
            if (roll >= MINE_WEST_GEM_P10K * 2 || (roll >= MINE_WEST_GEM_P10K && !mineLuckOn(wit, System.currentTimeMillis()))) return drops;
            int k = RND.nextInt(S5MineMap.GEM_CODES.length + S5MineMap.SEA_GEM_CODES.length);
            boolean sea = k >= S5MineMap.GEM_CODES.length;
            int i = sea ? k - S5MineMap.GEM_CODES.length : k;
            String code = sea ? S5MineMap.SEA_GEM_CODES[i] : S5MineMap.GEM_CODES[i], name = sea ? S5MineMap.SEA_GEM_NAMES[i] : S5MineMap.GEM_NAMES[i];
            dao.addUserOre(userName, code, 1);
            drops.add(mineDrop((sea ? S5MineMap.SEA_GEM_GRADE_BASE : S5MineMap.GEM_GRADE_BASE) + i, name, 1));
            msg.append(" · 💎 ").append(name).append(" 1개!");
            return drops;
        }
        Map<String, Integer> dit = mineItems(userName);
        int dm = mineLuckOn(dit, System.currentTimeMillis()) ? 2 : 1; // [2026-11-05] 행운의 주문서 -- 드랍률 2배(광석/보석/빙석 확률)
        if (RND.nextInt(100) < MINE_RANK_ORE_PCT[rank] * dm) {
            int b = Math.min(5, 1 + (type.tier - 1) / 2), r = RND.nextInt(100), g, qty = 1;
            if (r < 55) { g = Math.max(1, b - 1); qty += RND.nextInt(2); }
            else if (r < 92) g = Math.min(b, 4);
            else g = type.tier >= 9 ? 5 : Math.min(b + 1, 4);
            if (rank == 2) qty *= 2; // [2026-10-29] 상급 개체
            qty *= mineRebirthGain(dit); // [2026-10-28] 환생 x(1 + 회차) -- 여기 g는 늘 구리~미스릴
            dao.addUserOre(userName, S5MineMap.ORE_CODES[g - 1], qty);
            drops.add(mineDrop(g, S5MineMap.ORE_NAMES[g - 1], qty));
            msg.append(" · ").append(S5MineMap.ORE_NAMES[g - 1]).append(' ').append(qty).append("개");
        }
        int gi = S5MineMap.gemForMob(map.id, (int) (RND.nextInt(1000) / dm / mineHuntGemMul(dit)), 1.3 * MINE_RANK_GEM_MUL[rank]); // [2026-11-13] 사냥꾼의 목걸이
        if (gi >= 0) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[gi], 1);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + gi, S5MineMap.GEM_NAMES[gi], 1));
            msg.append(" · 💎 ").append(S5MineMap.GEM_NAMES[gi]).append(" 1개");
        }
        // [2026-10-28] 설산 몬스터 빙석 -- 같은 날 "더 안 나오게(득템 느낌)" 15% -> 3%
        boolean snow = S5MineMap.SNOW1.equals(map.id) || S5MineMap.SNOW2.equals(map.id) || S5MineMap.SNOW3.equals(map.id) || S5MineMap.SNOWROAD.equals(map.id); // [2026-10-28] 봉우리 가는 길도
        if (snow && RND.nextInt(100) < 3 * dm) {
            dao.addUserOre(userName, S5MineMap.ICE_STONE_CODE, 1);
            drops.add(mineDrop(S5MineMap.ICE_STONE_GRADE, S5MineMap.ICE_STONE_NAME, 1));
            msg.append(" · 🧊 ").append(S5MineMap.ICE_STONE_NAME).append(" 1개!");
        }
        return drops;
    }

    static HashMap<String, Object> mineDrop(int grade, String name, int qty) {
        HashMap<String, Object> d = new HashMap<>();
        d.put("grade", grade);
        d.put("name", name);
        d.put("qty", qty);
        return d;
    }
    // [2026-11-18] "천둥 보스 소환 처치 수가 볼 때 안 바뀐다" -- 목록이 mineState 때만 실려서 처치 수가 들어올 때 값 그대로였다.
    //  전체 지도의 보스 목록을 열 때 /api/tower-mine-wb?action=bosses로 이것만 새로 받는다.
    List<HashMap<String, Object>> mineBossList(String userName, long now) {
        List<HashMap<String, Object>> bosses = new ArrayList<>();
        // [2026-11-13] 보스 소환 -- 전체 지도 칸 위 젠타임 대신 지도 창 "👑 보스" 버튼 목록에 보스마다 내가 다시 소환할 수 있는 시각(callAt, 0 = 지금)
        Map<String, Integer> callItems = mineItems(userName);
        for (String bm : MINE_CALL_MAPS) {
            S5MineMap.Mob bmob = mineBossOf(S5MineMap.get(bm));
            if (bmob == null) continue;
            if (S5MineMap.SHRINE_BOSS.equals(bm) && !mineArchiOpen(userName)) continue; // 닌자 두목은 다도해가 열린 사람만
            HashMap<String, Object> b = new HashMap<>();
            b.put("map", bm);
            b.put("name", S5MineMobs.TYPES.get(bmob.type).name);
            b.put("mapName", S5MineMap.nameOf(bm));
            b.put("alive", !mineBossDead(bmob.type));
            b.put("callAt", Math.max(0, mineCallReadyAt(callItems, bm)));
            bosses.add(b);
        }
        if (mineWbOk(userName)) { // [2026-11-15] 월드보스 -- 매일 21시(일반 공개 전엔 관리자에게만, 시험 회차 버튼)
            HashMap<String, Object> b = new HashMap<>();
            b.put("map", S5MineMap.CASTLE);
            b.put("name", MINE_WB_NAME);
            b.put("mapName", "동쪽 성곽 내성");
            mineWbPool(now);
            b.put("alive", mineWbOn(now));
            b.put("wbHp", (int) (Math.max(0, MINE_WB_POOL.hp) * 1000L / mineWbMax())); // [2026-11-16] 남은 체력(맵 초기화까지 이어짐)
            b.put("wbKilled", MINE_WB_POOL.killed);
            if (MINE_WB_OPEN) b.put("wbAt", mineWbStart(now));
            else b.put("wbTest", true);
            bosses.add(b);
        }
        { // 천둥 황소 -- 소환 없이 마물의 숲 입구 마물 처치 수가 차면
            S5MineMap wr = S5MineMap.get(S5MineMap.WESTROAD);
            S5MineMap.Mob tmob = wr == null ? null : mineBossOf(wr);
            if (tmob != null && tmob.type == S5MineMobs.THUNDER_BOSS_TYPE) {
                mineThunderLoad();
                HashMap<String, Object> b = new HashMap<>();
                b.put("map", S5MineMap.WESTROAD);
                b.put("name", S5MineMobs.TYPES.get(tmob.type).name);
                b.put("mapName", wr.name);
                b.put("alive", mineThunderOn(now));
                b.put("kills", mineThunderKills);
                b.put("need", MINE_THUNDER_NEED);
                bosses.add(b);
            }
        }
        return bosses;
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
        { // [2026-10-29] 무기 장착 레벨 -- 들고 있는 무기보다 레벨이 낮으면(환생 직후 등) 칼로 바꿔 든다
            Map<String, Integer> gi = mineItems(userName);
            MineWeapon hw = mineWeaponOn(gi);
            if (hw != null && !mineGearLvOk(mineLv(row), gi, hw.lv)) { dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, 0); out.put("weaponOffMsg", "🗡️ " + hw.name + "은(는) Lv." + hw.lv + "부터라 칼로 바꿔 들었어요."); }
            MineWeapon ha = mineArmorOn(mineItems(userName)); // [2026-11-06] 갑옷도
            if (ha != null && !mineGearLvOk(mineLv(row), gi, ha.lv)) { dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); out.put("weaponOffMsg", "🛡️ " + ha.name + "은(는) Lv." + ha.lv + "부터라 벗었어요."); }
        }
        putMinePick(out, minePickDur(row), minePickMax(row));
        putMineFishDur(out, mineFishDurLeft(mineItems(userName))); // [2026-11-20] 낚시 내구도(따로)
        out.put("fishGear", mineFishGear(userName)); // [2026-11-20] 장비창 낚시용품
        out.put("pickUpgrades", minePickUpgradeList());
        out.put("pickCap", MINE_PICK_CAP); // [2026-10-24] 최대 내구도 상한
        out.put("nextResetAt", S5MineMap.nextResetAt(now));
        // [2026-10-28] "보스가 안 나온다, 맵 초기화 때 다시 나와야, 전체 지도에도 표기" -- 보스는 주기마다 한 번(잡히면 다음 리셋 때 다시).
        //  전체 지도가 보스 맵에 지금 있는지 / 다음에 나오는 시각을 보여 준다.
        out.put("bosses", mineBossList(userName, now)); // [2026-11-18] 목록 만들기는 mineBossList로(보스 창을 열 때 따로 다시 받는다)
        out.put("bossRw", mineBossRwList(userName)); // [2026-11-20] 보스별 오늘 받은 보상 횟수
        { S5MineMap.Mob hb = map == null || S5MineMap.towerFloor(map.id) > 0 ? null : mineBossOf(map); // [2026-11-20] 보스룸에 들어오면 이 보스 오늘 남은 보상
          if (hb != null && mineBossRwCounted(hb.type)) { HashMap<String, Object> h = new HashMap<>(); h.put("name", S5MineMobs.TYPES.get(hb.type).name); h.put("left", mineBossRwLeft(userName, hb.type)); h.put("max", MINE_BOSS_RW_MAX); out.put("bossRwHere", h); } }
        HashMap<String, int[]> dims = new HashMap<>(); // [2026-11-11] 전체 지도 -- 맵 크기만큼 칸 크기를 다르게
        for (String mid : S5MineMap.ids()) { S5MineMap dm = S5MineMap.get(mid); dims.put(mid, new int[] { dm.w, dm.h }); }
        out.put("mapDims", dims);
        if (isMineAdmin(userName)) { // [2026-11-11] "관리자는 보스방이나 미궁 안 전체 지도" -- 미궁 방마다 몬스터 / 동·서·북·남 통로가 가는 곳 / 끝방까지 걸음
            List<HashMap<String, Object>> mg = new ArrayList<>();
            for (String rid : S5MineMap.FOREST_ROOM_IDS) {
                S5MineMap rm = S5MineMap.get(rid);
                if (rm == null) continue;
                HashMap<String, Object> r = new HashMap<>();
                r.put("id", rid);
                r.put("mob", rm.mobs().isEmpty() ? "" : S5MineMobs.TYPES.get(rm.mobs().get(0).type).name);
                r.put("left", rm.mazeLeft());
                for (S5MineMap.Portal p : rm.portals) { char d = p.label.charAt(0); r.put(d == '동' ? "e" : d == '서' ? "w" : d == '북' ? "n" : "s", p.target); }
                mg.add(r);
            }
            out.put("mazeGraph", mg);
        }
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWalkMode(items));
        out.put("mapId", map.id);
        out.put("mapName", map.name);
        out.put("mapVersion", map.version());
        { // [2026-11-03] "게임 시작 때 모든 리소스를 먼저" -- 화면이 모든 맵을 미리 받아 두도록 맵별 버전
            HashMap<String, String> mv = new HashMap<>();
            for (String id : S5MineMap.ids()) mv.put(id, S5MineMap.get(id).version());
            out.put("mapVers", mv);
        }
        out.put("x", pos[0]);
        out.put("y", pos[1]);
        // 이 맵에서 캔 칸(영구) -- [x, y, 등급, x, y, 등급, ...]
        List<Integer> dug = new ArrayList<>();
        if (S5MineMap.DUNGEON.equals(map.id)) { // [2026-10-23] 요일 던전 -- 오늘 나오는 돌([2026-10-28] 문지기로 바꾼 요일이면 그 요일)
            int di = mineDungeonDay(items, now);
            out.put("dayLabel", S5MineMap.DAY_LABELS[di]);
            out.put("dayStone", S5MineMap.DAY_STONE_NAMES[di]);
        }
        out.put("todayDay", S5MineMap.dayIndex(now)); // [2026-10-28] 요일 동굴 문지기 창
        out.put("dungeonDay", mineDungeonDay(items, now));
        for (HashMap<String, Object> d : dao.selectMineDugCells(userName, mineDugKey(map, now, items))) {
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
        out.put("helmetInfo", mineHelmetInfo(userName, helmetTier));
        out.put("aura", mineAuraInfo(items)); // [2026-11-04] 검기(배운 단계/켠 단계)
        { List<String> pay = new ArrayList<>(); mineMercPayroll(userName, items, System.currentTimeMillis(), pay); if (!pay.isEmpty()) out.put("mercPay", pay); out.put("merc", mineMercInfo(userName, items, System.currentTimeMillis())); out.put("merc2", mineMercInfo2(userName, items, System.currentTimeMillis())); out.put("merc3", mineMercInfo3(userName, items, System.currentTimeMillis())); out.put("mercs", mineMercCards(items)); } // [2026-11-06] 미접속 동안 밀린 급여도 // [2026-11-06] 용병 관리(직업별 3칸) // [2026-11-05] 용병(시험 중 -- 관리자만)
        out.put("ench", mineEnchInfo(items)); // [2026-10-29] 무기 속성 부여 // [2026-10-24] 대장장이 창의 투구 업그레이드
        out.put("helmetNeed", mineHelmetNeed(map));
        mineGearPayload(userName, items, out); // [2026-10-24] 장신구/장화/용왕의 반지/선장 퀘스트
        out.put("combat", mineCombatInfo(mineLv(row), Math.max(0, longOf(row.get("M_EXP"), 0L)), mineHpNow(row, now, items.getOrDefault(MINE_ITEM_HP_LOCK, 0), mineRebirth(items)), now, mineEngaged(userName, now), items.getOrDefault(MINE_ITEM_HP_LOCK, 0), mineRebirth(items))); // [2026-10-24] 광산 전투 레벨
        if (row.get("M_HP") != null) // [2026-10-26] 여기선 저장하지 않으니 회복 시작은 마지막 저장 시각 + 대기
            ((HashMap<String, Object>) out.get("combat")).put("regenFrom", mineHpAt(row, now) + MINE_REGEN_DELAY_MS);
        out.put("mapMinLv", MINE_MIN_LV); // [2026-10-24] 구역별 권장 레벨(전체 지도 표시, 들어갈 때 경고 -- 막지는 않음)
        out.put("respawnCity", items.getOrDefault(MINE_ITEM_RESPAWN, 0) == 1); // [2026-10-24] 쓰러지면 왕도 성당에서
        out.put("visitedSnowtown", items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) > 0); // [2026-10-26] 이동 안내원 목적지
        out.put("visitedCity", items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) > 0 || isMineAdmin(userName)); // [2026-10-27]
        if (items.getOrDefault(MINE_ITEM_VISIT_TCITY, 0) < 1 && (S5MineMap.TOWER_CITY.equals(map.id) || S5MineMap.TOWER_GATE.equals(map.id) || S5MineMap.towerFloor(map.id) > 0)) { // [2026-11-16] 이미 건너와 있는 사람도
            try { dao.upsertMineItem(userName, MINE_ITEM_VISIT_TCITY, 1); items.put(MINE_ITEM_VISIT_TCITY, 1); } catch (Exception ignore) { }
        }
        { String ca = mineChaosAlive(userName, System.currentTimeMillis()); if (ca != null) out.put("chaosLock", ca); } // [2026-11-17] 혼돈스러운 마계 -- 귀환의 문이 닫혀 있다
        out.put("llPass", items.getOrDefault(MINE_ITEM_LL_PASS, 0) > 0); // [2026-11-17] 최후의 방어선 통행
        out.put("mailN", mineMailUnread(userName, items)); // [2026-11-17] 안 받은 우편
        out.put("visitedTcity", mineTowerOk(userName) && (items.getOrDefault(MINE_ITEM_VISIT_TCITY, 0) > 0 || isMineAdmin(userName)));
        out.put("visitedArchi", items.getOrDefault(MINE_ITEM_VISIT_ARCHI, 0) > 0 || isMineAdmin(userName)); // [2026-10-28] 다도해 마을
        out.put("coatDef", mineCoatDef(items)); // [2026-10-26] 입은 방한 외투의 한기 방어 단계(0 = 안 입음)
        out.put("luckUntil", mineLuckUntil(items)); // [2026-11-05] 행운의 주문서
        out.put("antMapUntil", mineAntMapUntil(items)); // [2026-11-23] 개미굴 지도
        Object[] nt = mineNotice(); // [2026-10-28] 아직 안 본 광산 소식(확인을 누르면 mineNoticeAck, 글은 /광산공지로)
        if (!((String) nt[1]).isEmpty() && items.getOrDefault(MINE_ITEM_NOTICE, 0) < (Integer) nt[0]) {
            out.put("noticeId", nt[0]);
            out.put("notice", nt[1]);
        }
        mineWbClaim(userName, items, now); // [2026-11-16] 내가 없는 사이 거룡이 쓰러졌으면 이번 주기에 싸운 몫의 보상을 지금
        if (items.getOrDefault(MINE_ITEM_WB_COM, 0) < MINE_WB_POOL.cycle + 1) mineWbCommon(userName, now); // [2026-11-22] 거룡이 쓰러진 주기에 들어오면 공통 보상(안 싸웠어도)
        int twF = S5MineMap.towerFloor(map.id); // [2026-11-15] 탑 -- 지금 층에서 몇 번째 방 / 계단방까지 몇 개 / 무슨 방(C 전투 T 보물 P 증표 X 함정 L 행운 E 빈 방 U 계단방)
        if (twF > 0) {
            HashMap<String, Object> tr = new HashMap<>();
            TwRun run = MINE_TW_RUN.get(userName);
            boolean mine = run != null && run.floor == twF, stairRoom = S5MineMap.towerStairRoom(map.id);
            tr.put("floor", twF);
            MINE_TW_LEFT.put(userName, new int[] { items.getOrDefault(MINE_ITEM_TWB_DAY, 0), items.getOrDefault(MINE_ITEM_TWB_CNT, 0) }); // [2026-11-19] 수문장 반복 보상 남은 수
            tr.put("bossLeft", mineTwLeftOf(userName, now));
            tr.put("bossMax", MINE_TW_BOSS_DAILY);
            tr.put("total", S5MineMap.towerRoomFloor(twF) ? S5MineMap.towerRooms(twF) : 0);
            tr.put("step", stairRoom ? S5MineMap.towerRooms(twF) : mine ? run.step : 0);
            tr.put("type", stairRoom ? "U" : mine ? String.valueOf(run.type) : "E");
            if (mine && !stairRoom && run.type == 'C' && run.mobId >= 0) tr.put("mob", run.mobId); // [2026-11-16] 전투방 -- 이 몬스터가 살아 있는 동안 화면이 오른쪽 문을 닫아 둔다
            out.put("twRun", tr);
        }
        final boolean wyrmOn = mineWyrmOn(items); // [2026-11-15] 용의 반지 -- 관리자 전체 지도 이동과 같은 목록(들어갈 자격이 있는 곳만)
        if (isMineAdmin(userName) || wyrmOn) { // [2026-10-24] 관리자 전용 좌표 이동(화면 좌표 클릭) -- 고를 수 있는 맵 목록
            List<HashMap<String, Object>> maps = new ArrayList<>();
            for (String id : new String[] { S5MineMap.FIELD, S5MineMap.CAVE1, S5MineMap.DESERT, S5MineMap.BEACH, S5MineMap.DUNGEON, S5MineMap.CASTLE, S5MineMap.ISLAND, S5MineMap.NORTH, S5MineMap.STEPPE, S5MineMap.CITY, S5MineMap.CAVE2, S5MineMap.CAVE3, S5MineMap.SEAROAD,
                    S5MineMap.SNOW1, S5MineMap.SNOW2, S5MineMap.SNOW3, S5MineMap.SNOWTOWN, S5MineMap.ROYALROAD, S5MineMap.RUINS, S5MineMap.SNOWROAD, S5MineMap.PEAK,
                    S5MineMap.ANTNEST, S5MineMap.PYRAMID, S5MineMap.SNOWCAVE, S5MineMap.SNOWCAVE2, S5MineMap.CAVE4, S5MineMap.PYRAMID_UNDER, S5MineMap.WESTROAD, S5MineMap.FOREST, S5MineMap.HARPY_NEST, S5MineMap.HARPY_FERRY, S5MineMap.TOWER_CITY, S5MineMap.TOWER_GATE, S5MineMap.DEMON_WEST, S5MineMap.DEMON_EAST, S5MineMap.DEMON_WEST2, S5MineMap.DEMON_EAST2, S5MineMap.DEMON_WEST3, S5MineMap.DEMON_EAST3, S5MineMap.LAST_LINE, S5MineMap.ABYSS_GATE, S5MineMap.CHAOS, S5MineMap.DEMON_VILLAGE }) { // [2026-11-08] 마물의 숲 미궁, 하피의 둥지 // [2026-10-28] 개미굴/피라미드/설산 동굴도(전체 지도에서 눌러 이동)
                S5MineMap m = S5MineMap.get(id);
                HashMap<String, Object> e = new HashMap<>();
                e.put("id", m.id);
                e.put("name", m.name);
                e.put("w", m.w);
                e.put("h", m.h);
                e.put("sx", m.spawnX); // [2026-10-28] 전체 지도에서 지역을 누르면 그 맵 시작점으로
                e.put("sy", m.spawnY);
                maps.add(e);
            }
            for (String id : S5MineMap.ARCHI_MAPS) { // [2026-10-28] 다도해(본섬/신궁 1~3층/닌자 두목의 방/뱃길/섬)도 관리자 이동 목록에
                S5MineMap m = S5MineMap.get(id);
                HashMap<String, Object> e = new HashMap<>();
                e.put("id", m.id);
                e.put("name", m.name);
                e.put("w", m.w);
                e.put("h", m.h);
                e.put("sx", m.spawnX);
                e.put("sy", m.spawnY);
                maps.add(e);
            }
            if (isMineAdmin(userName)) for (String id : new String[] { S5MineMap.OPEN_SEA, S5MineMap.SEA_CITY, S5MineMap.VAST_SEA }) { // [2026-11-18] 바다 위, 해상도시, 망망대해(관리자만)
                S5MineMap m = S5MineMap.get(id);
                HashMap<String, Object> e = new HashMap<>();
                e.put("id", m.id); e.put("name", m.name); e.put("w", m.w); e.put("h", m.h); e.put("sx", m.spawnX); e.put("sy", m.spawnY);
                maps.add(e);
            }
            if (isMineAdmin(userName)) out.put("adminMaps", maps);
            else {
                final Map<String, Integer> rit = items;
                maps.removeIf(mm -> { String mid = (String) mm.get("id"); return S5MineMap.ABYSS_GATE.equals(mid) || !mineWarpListed(mid) || mineMapBlock(S5MineMap.get(mid), rit, 0) != null; }); // [2026-11-20] 마계 입구 / 혼돈·마계 마을은 반지로 못 간다
                out.put("ringMaps", maps);
                out.put("ringReadyAt", items.getOrDefault(MINE_ITEM_WYRM_WARP, 0) * 60_000L + MINE_WYRM_CD_MS);
            }
        }
        HashMap<String, Object> wi = mineWhirlInfo(items); // [2026-11-07] 3환생 스킬 -- 돌개베기(배운 게 있거나 배울 수 있으면), 필살기
        if (mineWhirlOk(userName, items) || mineWhirlOn((int[]) wi.get("l")) || mineWhirlOn((int[]) wi.get("a"))) out.put("whirl", wi); // [2026-11-23] 진주목걸이만 있어도
        if (mineUltOk(userName, items)) out.put("ult", mineUltInfo(userName, items));
        return out;
    }

    /** 최근 캔 곳(모든 맵, 최신 30개) -- 좌표 공유용 목록. */
    List<HashMap<String, Object>> mineRecent(String userName) {
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

    // ---- [2026-11-18] 바다 낚시 -- "바다에선 채광 대신 낚시, 시즌4 성급별 물고기를 낚싯대 등급에 따라, 시즌4 장비를 유저 이름 그대로, 채광 내구도 10을 써서" ----
    //  배를 탄 맵(다도해 뱃길/바다 위)에서 채광 버튼 = 낚시. 한 번에 곡괭이 내구도 MINE_FISH_DUR, MINE_FISH_GAP_MS마다.
    //  같은 날 "기록을 남기려면 s5.5로 복사해 와서 마이그레이션, 이후 별도로" -- 시즌4 낚시 테이블을 TBOT_S55_FISH_*로 복사(ddl/S55_FISH.sql)해
    //  낚싯대/찌(EQUIP, 없으면 일반/일반), 물고기(INFO), 가방(INV), 기록(LOG -- 던질 때마다 한 줄)을 광산 쪽 테이블로 쓴다. 같은 날 "업적은 없어도 돼" -- 낚시 업적은 세지 않는다.
    //  확률 표(낚싯대 등급별 최고 별 / 별 가중치 / 찌 보정)도 시즌4(BotS4ServiceImpl) 값을 복사해 여기 둔다.
    static final int[] MINE_ROD_MAX_STAR = { 0, 3, 5, 6, 7, 8 };
    static final int[][] MINE_ROD_STAR_W = { {}, { 0, 70, 25, 5, 0, 0, 0, 0, 0 }, { 0, 50, 25, 15, 7, 3, 0, 0, 0 }, { 0, 40, 20, 15, 10, 8, 7, 0, 0 }, { 0, 35, 18, 13, 10, 8, 8, 8, 0 }, { 0, 30, 15, 12, 10, 8, 8, 9, 8 } };
    static final int[] MINE_BOBBER_BONUS = { 0, 0, 5, 10, 15 };
    static final String[] MINE_FISH_STAR_EMOJI = { "", "⭐", "🌟", "💫", "🔶", "💎", "💜", "🔥", "👑" };
    static String mineFishStar(int g) { return (g >= 0 && g < MINE_FISH_STAR_EMOJI.length ? MINE_FISH_STAR_EMOJI[g] : "") + "★" + g; }
    /** 별(등급) 고르기 -- 낚싯대가 닿는 최고 별까지, 찌 등급만큼 ★1에서 떼어 최고 별에 얹는다(시즌4와 같게). */
    static int mineFishRollStar(int rod, int bob) {
        rod = Math.max(1, Math.min(rod, MINE_ROD_STAR_W.length - 1));
        int max = MINE_ROD_MAX_STAR[Math.min(rod, MINE_ROD_MAX_STAR.length - 1)];
        int[] w = MINE_ROD_STAR_W[rod].clone();
        int bonus = MINE_BOBBER_BONUS[Math.max(0, Math.min(bob, MINE_BOBBER_BONUS.length - 1))], deduct = Math.min(bonus, w[1] - 1);
        if (deduct > 0) { w[1] -= deduct; w[max] += deduct; }
        int total = 0;
        for (int g = 1; g <= max; g++) total += w[g];
        int r = RND.nextInt(Math.max(1, total)), cum = 0;
        for (int g = 1; g <= max; g++) { cum += w[g]; if (r < cum) return g; }
        return 1;
    }
    // [2026-11-20] "낚시 내구도를 따로 -- 처음 모두 100, 1씩 닳게, 패치되는 순간 모두 100" -- 곡괭이 내구도는 더 안 쓴다.
    //  쓴 양(FISH_USED)과 그 날짜(FISH_DAY, yyyyMMdd)를 아이템 칸에 둔다: 기록이 없거나 날짜가 오늘이 아니면 가득(곡괭이처럼 매일 0시에 다시 100).
    //  그래서 배포 순간 모두 100이고 DB 작업이 필요 없다.
    static final int MINE_FISH_DUR = 1, MINE_FISH_DUR_MAX = 100;
    static final String MINE_ITEM_FISH_USED = "FISH_USED", MINE_ITEM_FISH_DAY = "FISH_DAY";
    static int mineTodayNum() { return Integer.parseInt(java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)); }
    static int mineFishDurLeft(Map<String, Integer> items) {
        if (items.getOrDefault(MINE_ITEM_FISH_DAY, 0) != mineTodayNum()) return MINE_FISH_DUR_MAX;
        return Math.max(0, MINE_FISH_DUR_MAX - items.getOrDefault(MINE_ITEM_FISH_USED, 0));
    }
    static void putMineFishDur(HashMap<String, Object> out, int left) { out.put("fishDur", left); out.put("fishDurMax", MINE_FISH_DUR_MAX); }
    /** 한 번 던졌다 -- 낚시 내구도 1. 남은 양. */
    int mineFishDurUse(String userName, Map<String, Integer> items) {
        int left = mineFishDurLeft(items), used = MINE_FISH_DUR_MAX - left + MINE_FISH_DUR, today = mineTodayNum();
        dao.upsertMineItem(userName, MINE_ITEM_FISH_DAY, today);
        dao.upsertMineItem(userName, MINE_ITEM_FISH_USED, used);
        items.put(MINE_ITEM_FISH_DAY, today);
        items.put(MINE_ITEM_FISH_USED, used);
        return Math.max(0, MINE_FISH_DUR_MAX - used);
    }
    // [2026-11-20] "채광이나 낚시 때도 경험치를 소량, 특히 채광은 과하지 않게" -- 지금 레벨 필요 경험치의 1/n(최소 1).
    //  채광은 캐기 한 번(광역 곡괭이로 여러 칸이어도 한 번)에 1/2000, 낚시는 한 마리에 1/500(보석물고기는 3배). 80레벨부터 처치 경험치 3배(MINE_EXP80_MUL)는 여기엔 안 붙게 미리 나눈다.
    static final int MINE_DIG_EXP_DIV = 2000, MINE_FISH_EXP_DIV = 500, MINE_GEM_FISH_EXP_MUL = 3;
    long mineSmallExp(String userName, HashMap<String, Object> row, Map<String, Integer> items, int div, int mul, long now, HashMap<String, Object> out) {
        int lv = mineLv(row), rb = mineRebirth(items), lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        long gain = Math.max(1, mineExpNeed(lv, rb) * mul / div / (lv >= MINE_EXP80_LV ? MINE_EXP80_MUL : 1));
        boolean wasReady = mineRebirthReady(lv, exp, rb);
        long[] xa = mineExpAdd(lv, exp, gain, rb);
        if (xa[3] <= 0) return 0; // 99레벨에서 가득 참
        int nlv = (int) xa[0], hp;
        long nexp = xa[1];
        if (xa[2] > 0) { // 레벨 업 -- 처치 때처럼 체력 가득 + 잠긴 최대 체력 풀림
            hp = mineHpFull(nlv, rb);
            if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.put("hpLockFree", true); }
            dao.updateMineCombat(userName, nlv, nexp, hp, now, 0);
            MINE_HP_VIRT.remove(userName);
            row.put("M_HP", hp); row.put("M_HP_AT_MS", now);
            out.put("levelUp", (int) xa[2]);
        } else {
            dao.updateMineLvExp(userName, nlv, nexp);
            hp = mineHpNow(row, now, lock, rb);
        }
        row.put("M_LV", nlv); row.put("M_EXP", nexp);
        if (!wasReady && mineRebirthReady(nlv, nexp, rb)) out.put("rebirthReadyNow", true);
        out.put("combat", mineCombatInfo(nlv, nexp, hp, now, mineEngaged(userName, now), lock, rb));
        out.put("smallExp", xa[3]);
        return xa[3];
    }
    static final long MINE_FISH_GAP_MS = 2_500L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_FISH_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final String[] MINE_ROD_NAMES = { "", "일반", "고급", "희귀", "영웅", "전설" };
    /** [2026-11-20] "장비창/접속 전 화면에 낚싯대/찌(있으면) 등 낚시용품도" -- 낚시 장비 줄이 있으면 {rod, bob, rodG, bobG}, 없으면 null. */
    HashMap<String, Object> mineFishGear(String userName) {
        HashMap<String, Object> eq;
        try { eq = dao.selectS55FishEquip(userName); } catch (Exception e) { return null; }
        if (eq == null) return null;
        int rod = Math.max(1, Math.min(MINE_ROD_NAMES.length - 1, intVal(eq.get("ROD_GRADE"), 1))), bob = Math.max(1, Math.min(MINE_ROD_NAMES.length - 1, intVal(eq.get("BOBBER_GRADE"), 1)));
        HashMap<String, Object> g = new HashMap<>();
        g.put("rod", MINE_ROD_NAMES[rod] + " 낚싯대"); g.put("bob", MINE_ROD_NAMES[bob] + " 찌"); g.put("rodG", rod); g.put("bobG", bob);
        return g;
    }
    /** [2026-11-19] "인벤토리 카테고리에 낚시(성급별)" -- 성급(8 -> 1)마다 도감 물고기 + 내가 낚은 수(안 낚은 건 0 -- 화면이 ???로), 낚싯대/찌 등급. */
    HashMap<String, Object> mineFishInv(String userName) {
        HashMap<String, Object> out = new HashMap<>();
        Map<Integer, Integer> have = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectS55FishInvOf(userName)) have.put(intVal(r.get("FISH_ID"), 0), intVal(r.get("QTY"), 0));
        java.util.TreeMap<Integer, List<HashMap<String, Object>>> byG = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        int kinds = 0, total = 0, all = 0;
        for (HashMap<String, Object> f : dao.selectS55FishAll()) {
            int id = intVal(f.get("FISH_ID"), 0), g = intVal(f.get("FISH_GRADE"), 1), q = have.getOrDefault(id, 0);
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", id);
            m.put("name", strVal(f.get("FISH_NAME"), "물고기"));
            m.put("emoji", strVal(f.get("FISH_EMOJI"), g >= 7 ? "🐋" : g >= 5 ? "🐠" : "🐟"));
            m.put("desc", strVal(f.get("FISH_DESC"), ""));
            m.put("qty", q);
            byG.computeIfAbsent(g, k -> new ArrayList<>()).add(m);
            all++;
            if (q > 0) { kinds++; total += q; }
        }
        List<HashMap<String, Object>> grades = new ArrayList<>();
        { // [2026-11-19] 지역 특산 보석물고기(맨 위)
            List<HashMap<String, Object>> gl = new ArrayList<>();
            for (int r = 0; r < MINE_GEM_FISH.length; r++) {
                int gid = MINE_GEM_FISH_ID0 + r, q = have.getOrDefault(gid, 0);
                HashMap<String, Object> m = new HashMap<>();
                m.put("id", gid); m.put("name", MINE_GEM_FISH[r][0]); m.put("emoji", "💠"); m.put("desc", MINE_GEM_FISH[r][1]); m.put("qty", q); m.put("region", MINE_FISH_REGION_NAMES[r]);
                gl.add(m);
                all++;
                if (q > 0) { kinds++; total += q; }
            }
            HashMap<String, Object> gm = new HashMap<>();
            gm.put("grade", 99);
            gm.put("star", "💠 지역 특산 보석물고기");
            gm.put("fish", gl);
            grades.add(gm);
        }
        for (Map.Entry<Integer, List<HashMap<String, Object>>> e : byG.entrySet()) {
            HashMap<String, Object> gm = new HashMap<>();
            gm.put("grade", e.getKey());
            gm.put("star", mineFishStar(e.getKey()));
            gm.put("fish", e.getValue());
            grades.add(gm);
        }
        HashMap<String, Object> eq = dao.selectS55FishEquip(userName);
        int rod = eq == null ? 1 : Math.max(1, intVal(eq.get("ROD_GRADE"), 1)), bob = eq == null ? 1 : Math.max(1, intVal(eq.get("BOBBER_GRADE"), 1));
        out.put("ok", true);
        out.put("fishInv", grades);
        out.put("rod", MINE_ROD_NAMES[Math.min(rod, MINE_ROD_NAMES.length - 1)] + " 낚싯대");
        out.put("bobber", MINE_ROD_NAMES[Math.min(bob, MINE_ROD_NAMES.length - 1)] + " 찌");
        out.put("kinds", kinds);
        out.put("allKinds", all);
        out.put("total", total);
        return out;
    }
    // [2026-11-19] "물 필드라면 전체 바다낚시 가능하도록. 다만 지역별 보석물고기를 만들어서 특산품이면(맵마다가 아니고 지역 영역별)" --
    //  낚시 자리 = 배를 탄 물 위(지금처럼), 또는 물가 땅에서 바라보는 칸이 물(~)일 때. 물이 있는 맵을 지역으로 묶어(용암인 마계, 얼음인 설산/설원 위쪽, 분수뿐인 왕도/탑 마을은 빼고)
    //  지역마다 그곳에서만 낚이는 보석물고기 한 종(FISH_ID 9001~, 도감은 이 표 -- DB 물고기 표와 따로). 던질 때마다 MINE_GEM_FISH_PERMIL 확률로 보통 물고기 대신.
    static final String[] MINE_FISH_REGION_NAMES = { "중앙 평원 호수", "사막 오아시스", "남쪽 바다", "다도해", "북쪽 바다", "엘리시온 강" };
    static final String[][] MINE_GEM_FISH = {
        { "에메랄드 송어", "중앙 평원 호수 특산 -- 비늘이 에메랄드처럼 초록빛으로 반짝이는 송어." },
        { "황옥 메기", "사막 오아시스 특산 -- 수염 끝이 황옥처럼 빛나는, 깊은 샘에 사는 메기." },
        { "진주 복어", "남쪽 바다 특산 -- 배를 부풀리면 커다란 진주알처럼 동그래지는 복어." },
        { "산호 비늘돔", "다도해 특산 -- 산호초 사이를 헤엄치는 붉은 산호빛 비늘돔." },
        { "사파이어 대구", "북쪽 바다 특산 -- 차가운 물에서 푸른 보석빛을 내는 대구." },
        { "자수정 잉어", "엘리시온 강 특산 -- 보랏빛 비늘이 자수정처럼 겹겹이 빛나는 잉어." } };
    static final int MINE_GEM_FISH_ID0 = 9001, MINE_GEM_FISH_PERMIL = 30;
    static int mineFishRegion(S5MineMap map, int x, int y) {
        String id = map.id;
        if (S5MineMap.FIELD.equals(id)) return 0;
        if (S5MineMap.DESERT.equals(id)) return 1;
        if (S5MineMap.BEACH.equals(id) || S5MineMap.ISLAND.equals(id) || S5MineMap.OPEN_SEA.equals(id) || S5MineMap.VAST_SEA.equals(id) || S5MineMap.SEA_CITY.equals(id)) return 2;
        if (S5MineMap.ARCHI.equals(id) || S5MineMap.ARCHISEA.equals(id) || java.util.Arrays.asList(S5MineMap.ISLE_IDS).contains(id)) return 3;
        if (S5MineMap.NORTH.equals(id)) return y >= map.h - 20 ? 4 : -1; // 위쪽은 얼음(화면도 얼음으로 그린다)
        if (S5MineMap.TOWER_CITY.equals(id) || S5MineMap.TOWER_GATE.equals(id) || S5MineMap.HARPY_FERRY.equals(id)) return 5;
        return -1;
    }
    /** 낚시할 물 칸 -- 배를 탄 물 위면 그 칸, 아니면 바라보는 칸이 물(~)이고 낚시 지역이면 그 칸. 없으면 null. */
    static int[] mineFishSpot(S5MineMap map, int x, int y, String dir) {
        if (map.inside(x, y) && map.terrainAt(x, y) == '~' && (map.boat() || map.sail()) && mineFishRegion(map, x, y) >= 0) return new int[] { x, y };
        int dx = "left".equals(dir) ? -1 : "right".equals(dir) ? 1 : 0, dy = "up".equals(dir) ? -1 : "down".equals(dir) ? 1 : 0;
        if (dx == 0 && dy == 0) return null;
        int fx = x + dx, fy = y + dy;
        if (!map.inside(fx, fy) || map.terrainAt(fx, fy) != '~' || mineFishRegion(map, fx, fy) < 0) return null;
        return new int[] { fx, fy };
    }
    HashMap<String, Object> mineFish(String userName, HashMap<String, Object> row, Map<String, Integer> items, String mapId, int x, int y, int st, long now, HashMap<String, Object> out, int region) {
        Long last = MINE_FISH_AT.get(userName);
        int fd = mineFishDurLeft(items);
        putMineFishDur(out, fd);
        if (last != null && now - last < MINE_FISH_GAP_MS) { out.put("fishWait", true); out.put("message", "🎣 찌가 아직 물 위에 떠 있어요."); return out; }
        if (fd < MINE_FISH_DUR) { out.put("message", "🎣 낚시 내구도를 다 썼어요. 매일 0시에 다시 " + MINE_FISH_DUR_MAX + "으로 채워져요."); return out; } // [2026-11-20] 곡괭이 내구도 대신
        MINE_FISH_AT.put(userName, now);
        HashMap<String, Object> eq = dao.selectS55FishEquip(userName);
        int rod = eq == null ? 1 : Math.max(1, intVal(eq.get("ROD_GRADE"), 1)), bob = eq == null ? 1 : Math.max(1, intVal(eq.get("BOBBER_GRADE"), 1));
        if (region >= 0 && region < MINE_GEM_FISH.length && RND.nextInt(1000) < MINE_GEM_FISH_PERMIL) { // [2026-11-19] 지역 특산 보석물고기
            int gid = MINE_GEM_FISH_ID0 + region;
            dao.upsertS55FishInv(userName, gid);
            dao.insertS55FishLog(userName, gid, mapId);
            HashMap<String, Object> gup = new HashMap<>();
            gup.put("userName", userName); gup.put("pickDur", st); gup.put("atMs", now); gup.put("x", x); gup.put("y", y); // [2026-11-20] 곡괭이 내구도는 그대로
            dao.updateMineUser(gup);
            putMineFishDur(out, mineFishDurUse(userName, items));
            long gx = mineSmallExp(userName, row, items, MINE_FISH_EXP_DIV, MINE_GEM_FISH_EXP_MUL, now, out); // [2026-11-20] 낚시 경험치(보석물고기 3배)
            HashMap<String, Object> gf = new HashMap<>();
            gf.put("id", gid); gf.put("name", MINE_GEM_FISH[region][0]); gf.put("grade", 8); gf.put("star", "💠 " + MINE_FISH_REGION_NAMES[region] + " 특산"); gf.put("emoji", "💠"); gf.put("desc", MINE_GEM_FISH[region][1]); gf.put("gem", true);
            out.put("ok", true);
            out.put("fish", gf);
            out.put("rod", MINE_ROD_NAMES[Math.min(rod, MINE_ROD_NAMES.length - 1)] + " 낚싯대");
            out.put("message", "🎣 💠 " + MINE_FISH_REGION_NAMES[region] + " 특산 보석물고기 " + MINE_GEM_FISH[region][0] + "을(를) 낚았어요!!" + (gx > 0 ? " 경험치 +" + gx : ""));
            mineGossipAdd(mineNick(userName) + " 님이 " + MINE_FISH_REGION_NAMES[region] + "에서 " + MINE_GEM_FISH[region][0] + "을(를) 낚았대요!");
            return out;
        }
        int star = mineFishRollStar(rod, bob);
        List<HashMap<String, Object>> fl = dao.selectS55FishByGrade(star);
        if (fl == null || fl.isEmpty()) { star = 1; fl = dao.selectS55FishByGrade(1); }
        if (fl == null || fl.isEmpty()) { out.put("message", "🎣 아무것도 물지 않았어요."); return out; }
        double tw = 0;
        for (HashMap<String, Object> f0 : fl) tw += Math.max(0, ((Number) f0.get("WEIGHT")).doubleValue());
        double rr = RND.nextDouble() * tw, cw = 0;
        HashMap<String, Object> fish = fl.get(0);
        for (HashMap<String, Object> f0 : fl) { cw += Math.max(0, ((Number) f0.get("WEIGHT")).doubleValue()); if (rr < cw) { fish = f0; break; } }
        int fishId = intVal(fish.get("FISH_ID"), 0), grade = intVal(fish.get("FISH_GRADE"), 1);
        dao.upsertS55FishInv(userName, fishId);
        dao.insertS55FishLog(userName, fishId, mapId);
        HashMap<String, Object> up = new HashMap<>();
        up.put("userName", userName);
        up.put("pickDur", st); // [2026-11-20] 곡괭이 내구도는 그대로(낚시 내구도를 따로)
        up.put("atMs", now);
        up.put("x", x);
        up.put("y", y);
        dao.updateMineUser(up);
        putMineFishDur(out, mineFishDurUse(userName, items));
        long fx = mineSmallExp(userName, row, items, MINE_FISH_EXP_DIV, 1, now, out); // [2026-11-20] 낚시 경험치
        HashMap<String, Object> f = new HashMap<>();
        f.put("id", fishId);
        f.put("name", strVal(fish.get("FISH_NAME"), "물고기"));
        f.put("grade", grade);
        f.put("star", mineFishStar(grade));
        f.put("emoji", grade >= 7 ? "🐋" : grade >= 5 ? "🐠" : "🐟");
        f.put("desc", strVal(fish.get("FISH_DESC"), ""));
        out.put("ok", true);
        out.put("fish", f);
        out.put("rod", MINE_ROD_NAMES[Math.min(rod, MINE_ROD_NAMES.length - 1)] + " 낚싯대");
        out.put("message", "🎣 " + mineFishStar(grade) + " " + f.get("name") + "을(를) 낚았어요!" + (fx > 0 ? " 경험치 +" + fx : ""));
        return out;
    }

    @Override
    @Transactional
    public HashMap<String, Object> mineDig(String userName, String mapId, String ver, int x, int y, String dir) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        if (mineWbDownUntil(userName, now) > 0) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("wbDown", mineWbDownUntil(userName, now)); so.put("message", "💫 쓰러져 있어요. 곧 다시 일어나요."); return so; } // [2026-11-15] 월드보스
        if (mineStunned(userName, now)) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("stunned", true); so.put("message", "💫 기절해서 움직일 수 없어요!"); return so; } // [2026-11-04] 붕대 / [2026-11-15] 히드라·거룡의 포효
        HashMap<String, Object> row = mineRowLocked(userName); // [2026-10-28] 성능 -- 행 확인(mineRow) + 잠금 조회 두 번을 잠금 조회 한 번으로
        S5MineMap map = mineMapOf(row);
        int st = minePickDur(row), stMax = minePickMax(row);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        putMinePick(out, st, stMax);
        Map<String, Integer> items = mineItems(userName);
        int water = mineWalkMode(items); // [2026-10-28] 0 보통 / 1 물 위 / 2 바람의 신(모두 통과)
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        if (mineFrozen(userName, now) != null) { out.put("frozen", true); out.put("message", MINE_FROZEN_BLOCK); return out; } // [2026-10-28] 얼음 여왕 빙결
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
        boolean noFish = dir != null && dir.endsWith("!"); // [2026-11-20] 자동채광 -- 물을 바라봐도 낚시하지 않고 발밑을 캔다(배 맵은 원래 채광이 없다)
        if (noFish) dir = dir.substring(0, dir.length() - 1);
        if (S5MineMap.DEMON_VILLAGE.equals(map.id)) { out.put("message", "😈 마계 마을의 땅은 파낼 수 없어요."); out.put("noOre", true); return out; } // [2026-11-20] "마계 마을 채광 금지"
        int[] fsp = noFish ? null : mineFishSpot(map, x, y, dir); // [2026-11-19] "물 필드라면 전체 바다낚시" -- 배 위, 또는 물가에서 물(~)을 바라보면
        if ((map.boat() && !noFish) || fsp != null) { // [2026-11-18] "바다에선 채광 대신 낚시" -- 같은 날 "아직은 관리자만", [2026-11-19] 배로 다니는 바닷가/섬 물 위에서도
            // [2026-11-19] "바다 낚시도 일반 플레이어 오픈" -- 관리자만 막던 것 없앰
            return mineFish(userName, row, items, map.id, x, y, st, now, out, fsp == null ? mineFishRegion(map, x, y) : mineFishRegion(map, fsp[0], fsp[1]));
        }
        int g = map.oreAt(x, y);
        if (g == 0) {
            out.put("message", "여기서는 캘 수 없어요.");
            out.put("noOre", true); // [2026-10-24] 자동채광이 이 칸을 다시 고르지 않게
            return out;
        }
        int cell = y * map.w + x;
        String dugKey = mineDugKey(map, now, items);
        if (dao.countMineDug(userName, dugKey, cell) > 0) {
            out.put("message", "이미 캔 자리예요. 다른 곳을 찾아보세요.");
            out.put("noOre", true);
            return out;
        }
        if (st < 1) { // [2026-10-30] "내구도가 없어도 채집 가능, 성공률 20%" -- 실패하면 칸은 그대로(다시 시도 가능), 성공하면 내구도는 0 그대로(광역/쌍날은 한 칸만)
            if (RND.nextInt(100) >= MINE_BROKEN_PICK_PCT) {
                out.put("message", "⛏️ 곡괭이가 다 닳아 캐지 못했어요. (내구도 0 · 성공률 " + MINE_BROKEN_PICK_PCT + "%)\n대장장이에게 광석을 주면 강화할 수 있어요.");
                out.put("digFail", true);
                return out;
            }
            st = 1;
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
        } else if (pickSlot == 4 && items.getOrDefault(MINE_ITEM_WIDE_PICK5, 0) > 0) { // [2026-11-13] 대광역 곡괭이 5x5(가까운 칸부터)
            for (int r2 = 1; r2 <= 2; r2++) for (int dy = -r2; dy <= r2; dy++) for (int dx = -r2; dx <= r2; dx++) if (Math.max(Math.abs(dx), Math.abs(dy)) == r2) around.add(new int[] { dx, dy });
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
        mineSmallExp(userName, row, items, MINE_DIG_EXP_DIV, 1, now, out); // [2026-11-20] 채광 경험치(소량, 캐기 한 번에 한 번)
        String msg = "⛏️ " + map.name + " " + name + " " + gain + "개! (보유 " + qty + "개)" // [2026-10-28] "보석 캘 때 좌표 나오는 거 지워줘" -- 좌표 빼기
                + (upgraded ? " 💍 심연의 반지: 한 등급 위!" : "") + (gain > 1 ? " 💍 풍요의 반지: 2개!" : "");
        if (one.get("gemGrade") != null) {
            out.put("gemGrade", one.get("gemGrade"));
            out.put("gemName", one.get("gemName"));
            msg += " + 💎 " + one.get("gemName") + " 1개!";
        }
        // [2026-10-24] 탐지 곡괭이로 별빛석을 캐면 빛을 잃는다(이번 맵 주기 동안 탐지 꺼짐)
        if ((Integer.valueOf(6).equals(one.get("grade")) || Integer.valueOf(S5MineMap.MOON_STONE_GRADE).equals(one.get("grade"))) && items.getOrDefault(MINE_ITEM_PICK_SLOT, 0) == 2 && items.getOrDefault(MINE_ITEM_DETECT_PICK, 0) > 0 && !mineDetectDim(items)) {
            dao.upsertMineItem(userName, MINE_ITEM_DETECT_OFF, mineDetectOffValue());
            out.put("detectDim", true);
            msg += " 🌑 탐지 곡괭이가 별빛에 눌려 빛을 잃었어요... 12시간 뒤 다시 빛나요.";
        }
        // [2026-11-19] "마계 입구를 통해 마계 마을에 가면 정제된 마석이 나와야 하는데 안 나와" -- 칸마다 넣어 주긴 했지만(mineDigCell) 결과(manaStone/demonOre)를
        //  응답에 옮기지 않아 화면에 "+1 정제된 마석"도, 보유 수도 안 바뀌었다(다시 들어와야 보였다). 광역 칸까지 더해 알리고 보유 목록도 새로.
        int manaN = one.get("manaStone") != null ? 1 : 0, demonN = one.get("demonOre") != null ? 1 : 0;
        for (HashMap<String, Object> wc : wide) { if (wc.get("manaStone") != null) manaN++; if (wc.get("demonOre") != null) demonN++; }
        if (manaN > 0) { out.put("manaStone", manaN); msg += " 💠 " + S5MineMap.MANA_STONE_NAME + " +" + manaN; }
        if (demonN > 0) { out.put("demonOre", demonN); msg += " 😈 " + S5MineMap.DEMON_ORE_NAME + " +" + demonN; }
        if (manaN > 0 || demonN > 0) out.put("ores", mineOreList(userName));
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
            msg += (pickSlot == 3 ? " ⛏️ 쌍날 +" : pickSlot == 4 ? " ⛏️ 대광역 +" : " ⛏️ 광역 +") + wide.size() + "칸";
            int wg = 0; // [2026-11-13] "광역 곡괭이로 캘 때 보석이 잘 안 나온다" -- 함께 캔 칸의 보석은 이미 칸마다 나왔지만(사막의 목걸이 배수도) 안내/화면에 안 보였다
            for (HashMap<String, Object> wc : wide) if (wc.get("gemGrade") != null) wg++;
            if (wg > 0) msg += " 💎 주변 칸 보석 " + wg + "개!";
        }
        out.put("message", msg);
        return out;
    }

    /** [2026-10-24] 탐지 곡괭이 -- (x, y) 주변 칸 중 내가 안 캔 광석 칸: [dx, dy, 등급 값](요일 던전 = 오늘 요일석, 바닷길 = -1 보석 줄기).
     *  탐지 곡괭이를 끼고 있고 (x, y)가 저장 위치에서 갈 수 있는 거리일 때만.
     *  [2026-10-28] 범위 3x3 -> 사방 MINE_DETECT_R칸("주변 8x8", 내 칸이 가운데라 9x9). 캔 칸은 한 번에 읽는다(칸마다 조회 X).
     *  범위 안에 빛나는 돌(별빛석/달빛석)이 있으면 이번 결과까지 보여 주고 기능이 멈춘다(DETECT_OFF, [2026-10-29] 12시간 뒤 회복). */
    static final int MINE_DETECT_R = 4;
    @Override
    public HashMap<String, Object> mineScan(String userName, String mapId, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName) || mineGate() != null) return out;
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        Map<String, Integer> items = mineItems(userName);
        if (items.getOrDefault(MINE_ITEM_DETECT_PICK, 0) < 1 || items.getOrDefault(MINE_ITEM_PICK_SLOT, 0) != 2) return out;
        if (mineDetectDim(items)) { out.put("dim", true); return out; } // 별빛석을 캐서 빛을 잃음 -- 12시간 뒤 회복
        S5MineMap map = mineMapOf(row);
        if (mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) return out;
        long now = System.currentTimeMillis();
        if (!mineMoveOk(minePos(row, map, mineWalkMode(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        String dugKey = mineDugKey(map, now, items);
        Set<Integer> dug = new LinkedHashSet<>();
        for (HashMap<String, Object> d : dao.selectMineDugCells(userName, dugKey)) dug.add(intVal(d.get("CELL"), -1));
        List<int[]> cells = new ArrayList<>();
        boolean glow = false;
        for (int dy = -MINE_DETECT_R; dy <= MINE_DETECT_R; dy++) for (int dx = -MINE_DETECT_R; dx <= MINE_DETECT_R; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= map.w || ny >= map.h) continue;
            int g = map.oreAt(nx, ny);
            if (g == 0 || dug.contains(ny * map.w + nx)) continue;
            if (S5MineMap.DUNGEON.equals(map.id)) g = S5MineMap.DAY_STONE_GRADE_BASE + mineDungeonDay(items, now);
            else if (S5MineMap.SEAROAD.equals(map.id)) g = -1;
            else if (g == 6 && S5MineMap.isArchi(map.id)) g = S5MineMap.MOON_STONE_GRADE; // [2026-10-28] 다도해 = 달빛석
            if (g == 6 || g == S5MineMap.MOON_STONE_GRADE) glow = true;
            cells.add(new int[] { dx, dy, g });
        }
        if (glow) { // 빛나는 돌을 탐지 -- 이번 결과까지 보여 주고 멈춤
            dao.upsertMineItem(userName, MINE_ITEM_DETECT_OFF, mineDetectOffValue());
            out.put("dim", true);
            out.put("message", "주변에 빛나는 돌이 있어 기능이 멈추었습니다.");
        }
        out.put("r", MINE_DETECT_R);
        out.put("ok", true);
        out.put("x", x);
        out.put("y", y);
        out.put("cells", cells);
        return out;
    }

    /** 칸 하나에서 얻기 -- 캔 칸 기록 + 광석/요일석/보석 지급. 결과: grade, oreCode, oreName, gain, upgraded, (gemGrade, gemName). 내구도/위치는 부른 쪽이 저장. */
    HashMap<String, Object> mineDigCell(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, int g, long now, String dugKey) {
        String code, name;
        boolean upgraded = false;
        // [2026-10-23] 요일 던전 -- 칸의 광석 등급과 상관없이 그날(KST) 요일의 돌 1개(월요석~일요석), 캔 칸 기록 등급은 11~17
        if (S5MineMap.DUNGEON.equals(map.id)) {
            int di = mineDungeonDay(items, now); // [2026-10-28] 문지기로 바꾼 요일이면 그 요일의 돌
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
            if (g == 6 && S5MineMap.isArchi(map.id)) { // [2026-10-28] "다도해 지역에선 별빛석 없이 달빛석만" -- 별빛석 칸은 달빛석(캔 칸 기록도 20)
                mineGossipAdd(mineNick(userName) + " 님이 " + map.name + " (" + x + ", " + y + ") 근처에서 달빛석을 캤대요!"); // [2026-11-05] 왕도 소문
                g = S5MineMap.MOON_STONE_GRADE;
                code = S5MineMap.MOON_STONE_CODE;
                name = S5MineMap.MOON_STONE_NAME;
            }
        }
        // [2026-10-24] 풍요의 반지 -- 확률로 2개
        int dbl = MINE_PLENTY_DOUBLE_PCT[mineRingTier(items, MINE_ITEM_PLENTY_RING)];
        int gain = (dbl > 0 && RND.nextInt(100) < dbl ? 2 : 1) * mineRebirthGain(items, g, code); // [2026-10-28] 환생 x(1 + 회차) -- 미스릴 이하 광석만
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
        int gi = S5MineMap.SEAROAD.equals(map.id) ? -1 : S5MineMap.gemFor(map.id, (int) (RND.nextInt(1000) / MINE_SAND_GEM_MULT[Math.min(mineNeckTier(items), MINE_SAND_GEM_MULT.length - 1)]));
        if (gi >= 0) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[gi], 1);
            r.put("gemGrade", S5MineMap.GEM_GRADE_BASE + gi);
            r.put("gemName", S5MineMap.GEM_NAMES[gi]);
        }
        // [2026-11-05] "채광 시 가끔 세공된 보석(확률 낮게)" -- 천분의 MINE_DIG_CUT_PERMILLE(0.3%), 그 맵 지역 보석의 세공품
        if (!S5MineMap.SEAROAD.equals(map.id) && RND.nextInt(1000) < MINE_DIG_CUT_PERMILLE) {
            int cg = S5MineMap.cutGemFor(map.id, RND.nextInt(1 << 20));
            dao.addUserOre(userName, S5MineMap.cutCode(cg), 1);
            r.put("cutGrade", S5MineMap.CUT_GRADE_BASE + cg);
            r.put("cutName", S5MineMap.cutName(cg));
        }
        // [2026-10-28] 다도해 바다 보석(진주/산호석/자수정/오팔/흑진주) -- 지역 보석이 안 나왔을 때 본섬/일곱 섬에서 4%
        // [2026-11-17] "목걸이 효과 바다 보석에도 적용" -- 사막의 목걸이 배수를 바다 보석 확률에도 곱하고, 땅 보석이 나와도 따로 굴린다(둘 다 나오면 seaGrade로)
        if (S5MineMap.isArchiLand(map.id) && RND.nextInt(1000) < S5MineMap.SEA_GEM_PERMILLE * MINE_SAND_GEM_MULT[Math.min(mineNeckTier(items), MINE_SAND_GEM_MULT.length - 1)]) {
            int sg = S5MineMap.seaGemFor(RND.nextInt(100));
            dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[sg], 1);
            r.put(gi < 0 ? "gemGrade" : "seaGrade", S5MineMap.SEA_GEM_GRADE_BASE + sg);
            r.put(gi < 0 ? "gemName" : "seaName", S5MineMap.SEA_GEM_NAMES[sg]);
        }
        // [2026-11-20] 마계 마을은 캘 수 없다(mineDig에서 막음) -- 정제된 마석은 들어갈 때 1개 고정(mineWarp)
        if (S5MineMap.isDemonLand(map.id)) { // [2026-11-17] "악마 들판·최후의 방어선에서 곡괭이로 땅을 파면 악마의 광석 조각" -- 캘 때마다 하나
            dao.addUserOre(userName, S5MineMap.DEMON_ORE_CODE, 1);
            r.put("demonOre", 1);
        }
        // [2026-10-28] 설산 빙석 -- 1/2/3층에서 캘 때 함께. 처음 8/10/12%였다가 같은 날 "더 안 나오게(득템 느낌)" 1.5/2/2.5%(천분율)
        int icePm = S5MineMap.SNOW1.equals(map.id) ? 15 : S5MineMap.SNOW2.equals(map.id) ? 20 : S5MineMap.SNOW3.equals(map.id) ? 25 : S5MineMap.SNOWROAD.equals(map.id) || S5MineMap.PEAK.equals(map.id) ? 25 : 0; // [2026-10-28] 봉우리 가는 길/봉우리 2.5%
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
        int water = mineWalkMode(items); // [2026-10-28] 0 보통 / 1 물 위 / 2 바람의 신(모두 통과)
        int[] pos = minePos(row, map, water);
        HashMap<String, Object> out = new HashMap<>();
        out.put("mapName", map.name); // [2026-10-23] 퇴장 로그용
        boolean sameMap = (mapId == null || mapId.isEmpty() || map.id.equals(mapId)) && !mineStaleVersion(map, ver);
        IceFreeze fz = sameMap ? mineFrozen(userName, now) : null; // [2026-10-28] 얼어 있으면 언 칸에서 못 움직인다
        if (fz != null && (x != fz.x || y != fz.y)) {
            out.put("ok", false);
            out.put("frozen", true);
            out.put("x", fz.x);
            out.put("y", fz.y);
            return out;
        }
        if (sameMap && mineWalk(map, x, y, water) && mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            // [2026-10-28] 성능 -- 걷는 중 2.5초마다 오던 UPDATE를 바로 쓰지 않고 모아서 쓴다(15초마다/멈추면 5초 뒤/나가면/서버 종료 때).
            //  모아 둔 위치는 광산 유저 행 조회에 덮여 나오므로 이동 검사(mineMoveOk)와 다른 요청은 그대로 최신 위치를 본다(S5MinePosBuffer).
            my.prac.core.prjbot.dao.S5MinePosBuffer.put(userName, map.id, x, y, now);
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
        int water = mineWalkMode(items); // [2026-10-28] 0 보통 / 1 물 위 / 2 바람의 신(모두 통과)
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) {
            out.put("reload", true);
            out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요.");
            return out;
        }
        if (mineFrozen(userName, now) != null) { out.put("frozen", true); out.put("message", MINE_FROZEN_BLOCK); return out; } // [2026-10-28] 얼음 여왕 빙결
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
        if (S5MineMap.CHAOS.equals(map.id)) { // [2026-11-17] 혼돈스러운 마계 -- 쓰러뜨려야 귀환의 문이 열린다
            String alive = mineChaosAlive(userName, now);
            if (alive != null) { dao.updateMinePos(userName, map.id, x, y, now); out.put("locked", true); out.put("message", "🌌 귀환의 문이 닫혀 있어요. " + alive + "을(를) 쓰러뜨려야 열려요!"); return out; }
        }
        if (S5MineMap.towerFloor(map.id) > 0 || S5MineMap.TOWER_GATE.equals(map.id)) { // [2026-11-15] 탑 -- 방의 문/계단, 탑 입구의 큰 문
            HashMap<String, Object> tw = mineTowerPortal(userName, row, map, p, items, x, y, now);
            if (tw != null) return tw;
        }
        // [2026-10-24] 배(판자 B) -- 항구(바닷가/남쪽 섬/북쪽 끝 설원) 중 다른 곳을 골라 그 항구의 내리는 칸으로
        boolean ship = S5MineMap.SHIP.equals(p.target);
        // [2026-10-28] 다도해 -- 남쪽 섬 배에서만 본섬으로(운영자만), 본섬 배는 남쪽 섬으로만
        boolean archiShip = ship && ((S5MineMap.ISLAND.equals(map.id) && S5MineMap.ARCHI.equals(dest) && mineArchiMapOpen(userName, S5MineMap.ARCHI))
                || (S5MineMap.ARCHI.equals(map.id) && S5MineMap.ISLAND.equals(dest)));
        if (ship && !archiShip && (S5MineMap.ARCHI.equals(map.id) || dest == null || dest.equals(map.id) || !S5MineMap.SHIP_PORTS.contains(dest))) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("message", "⛵ 어디로 갈지 골라 주세요.");
            return out;
        }
        // [2026-10-24] 왕도 텔레포트 게이트(X) -- "대도시에서 다른 지역으로는 어렵게": 금광석 MINE_TELE_GOLD개를 내고 고른 지역의 시작점으로
        boolean tele = S5MineMap.TELE.equals(p.target);
        if (tele) { // [2026-11-18] 목적지 확인은 위(워프 포인트)에서
            if (MINE_TELE_GOLD > 0 && dao.takeUserOre(userName, S5MineMap.ORE_CODES[3], MINE_TELE_GOLD) == 0) {
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("message", "🌀 텔레포트 게이트를 움직이려면 금광석 " + MINE_TELE_GOLD + "개가 필요해요.");
                return out;
            }
        }
        // [2026-11-17] 마계 입구 포털 -- MINE_CHAOS_PCT%는 혼돈스러운 마계, 나머지는 전체 지도의 아무 곳
        boolean abyss = S5MineMap.ABYSS_GATE.equals(map.id) && S5MineMap.CHAOS.equals(p.target);
        int abyssRoll = abyss ? RND.nextInt(100) : -1;
        S5MineMap villageMap = abyssRoll >= MINE_CHAOS_PCT && abyssRoll < MINE_CHAOS_PCT + MINE_VILLAGE_PCT ? S5MineMap.get(S5MineMap.DEMON_VILLAGE) : null; // [2026-11-17] 마계 마을
        Object[] abyssTo = villageMap != null ? new Object[] { villageMap, villageMap.spawnX, villageMap.spawnY } : abyss && abyssRoll >= MINE_CHAOS_PCT ? mineAbyssPick(items, mineLv(row), water) : null;
        // [2026-11-18] 워프 포인트 -- 목적지 wp:아이디(켜 둔 곳만)
        S5MineMap.Waypoint wpTo = S5MineMap.TELE.equals(p.target) && dest != null && dest.startsWith("wp:") ? S5MineMap.waypoint(dest.substring(3)) : null;
        if (S5MineMap.TELE.equals(p.target)) {
            String wpMsg = wpTo == null ? "🌀 어디로 갈지 골라 주세요." : !mineWpOn(userName, items, wpTo) ? "🌀 아직 켜지 않은 워프 포인트예요. 직접 가서 만지면 켜져요."
                    : wpTo == S5MineMap.waypointAt(map.id, px, py) ? "🌀 지금 서 있는 워프 포인트예요." : null;
            if (wpMsg == null) wpMsg = mineWpDurBlock(S5MineMap.waypointAt(map.id, px, py), wpTo); // [2026-11-18] 내구도
            if (wpMsg != null) { dao.updateMinePos(userName, map.id, x, y, now); out.put("message", wpMsg); return out; }
        }
        // [2026-11-18] "윤슬항 워프가 있다면 망망대해 없이 바다 위 맵 오른쪽에서 나오면 돼" -- 남쪽 바닷가 7시 끝(망망대해로) -> 윤슬항 워프 포인트를 켠 사람은 바다 위로
        boolean vastSkip = S5MineMap.BEACH.equals(map.id) && S5MineMap.VAST_SEA.equals(p.target) && mineWpOn(userName, items, S5MineMap.waypoint(S5MineMap.SEA_CITY));
        S5MineMap target = abyssTo != null ? (S5MineMap) abyssTo[0] : S5MineMap.get(vastSkip ? S5MineMap.OPEN_SEA : ship ? dest : wpTo != null ? wpTo.map : p.target);
        if (S5MineMap.SEAROAD.equals(target.id) && water < 1) { // [2026-10-24] 바닷길은 용왕의 반지가 있어야 -- 없으면 그냥 물(아무 말 없이)
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("silent", true);
            return out;
        }
        // [2026-10-24] 초원/왕도는 관리자만이었다가 [2026-10-27] "왕도 일반 플레이어들에게 오픈" -- 막지 않는다.
        String block = S5MineMap.isForestZone(target.id) && !MINE_FOREST_OPEN && !isMineAdmin(userName) /* [2026-11-11] 미궁 방/끝방/둥지/나루터 -- 같은 날 일반 유저에게 엶(MINE_FOREST_OPEN) */ ? "🌿 가시덩굴이 얽혀 아직 들어갈 수 없어요. (준비 중)" // [2026-11-08] 마물의 숲 미궁 -- 지금은 관리자만
                : S5MineMap.isArchi(target.id) && !mineArchiMapOpen(userName, target.id) // [2026-10-28] 다도해 -- 본섬만 모두에게, 나머지는 운영자만
                ? (S5MineMap.isShrineFloor(target.id) || S5MineMap.SHRINE_BOSS.equals(target.id) ? "🔒 신궁은 아직 준비 중이에요. 곧 열려요!" : "🌊 아직 열리지 않은 바다예요.") : mineMapBlock(target, items, mineLv(row));
        if (block != null) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("locked", true);
            out.put("message", block);
            return out;
        }
        String llMsg = "";
        if (S5MineMap.LAST_LINE.equals(target.id) && items.getOrDefault(MINE_ITEM_LL_PASS, 0) < 1 && !isMineAdmin(userName)) { // [2026-11-17] 두 인장을 맞대 방어선 통행(한 번)
            if (dao.takeUserOre(userName, S5MineMap.DUSK_SEAL_CODE, 1) == 0) { dao.updateMinePos(userName, map.id, x, y, now); out.put("locked", true); out.put("message", "🛡️ " + S5MineMap.DUSK_SEAL_NAME + "이 있어야 해요."); return out; }
            if (dao.takeUserOre(userName, S5MineMap.DAWN_SEAL_CODE, 1) == 0) { dao.addUserOre(userName, S5MineMap.DUSK_SEAL_CODE, 1); dao.updateMinePos(userName, map.id, x, y, now); out.put("locked", true); out.put("message", "🛡️ " + S5MineMap.DAWN_SEAL_NAME + "이 있어야 해요."); return out; }
            dao.upsertMineItem(userName, MINE_ITEM_LL_PASS, 1);
            llMsg = "🔥 " + S5MineMap.DUSK_SEAL_NAME + "과 " + S5MineMap.DAWN_SEAL_NAME + "을 맞대자 하나의 문장이 되어 빛났어요! 이제 언제든 최후의 방어선을 드나들 수 있어요. ";
            mineGossipAdd(mineNick(userName) + " 님이 두 인장을 맞대 최후의 방어선에 들어섰대요!");
        }
        if (S5MineMap.ABYSS_GATE.equals(target.id) && S5MineMap.LAST_LINE.equals(map.id) && !isMineAdmin(userName)) { // [2026-11-18] "보로네오 뒤 마계로 가는 길 -- 50% 증표가 있어야, 갈 때 그 증표는 쓰인다"
            if (dao.takeUserOre(userName, S5MineMap.JUDGE_TOKEN_CODE, 1) == 0) {
                dao.updateMinePos(userName, map.id, x, y, now);
                out.put("locked", true);
                out.put("message", "⚖️ 마계로 가는 길은 " + S5MineMap.JUDGE_TOKEN_NAME + "가 있어야 지날 수 있어요. 심판자 보로네오를 쓰러뜨리면 가끔 얻어요. (지날 때마다 하나씩 쓰여요)");
                return out;
            }
            llMsg += "⚖️ " + S5MineMap.JUDGE_TOKEN_NAME + "를 내밀자 마계로 가는 길이 열렸어요. (증표 1개 사용) ";
        }
        if (wpTo != null) mineWpDurUse(S5MineMap.waypointAt(map.id, px, py), wpTo); // [2026-11-18] 워프 포인트 내구도 -- 출발지/도착지 -1
        int ax = abyssTo != null ? (Integer) abyssTo[1] : ship ? target.dockX() : tele ? wpTo.x : p.tx, ay = abyssTo != null ? (Integer) abyssTo[2] : ship ? target.dockY() : tele ? wpTo.y + 2 : p.ty;
        if (vastSkip) { ax = target.w - 4; ay = Math.max(2, Math.min(target.h - 3, target.h / 2)); } // [2026-11-18] 바다 위 오른쪽(남쪽 바닷가 쪽 끝 조금 안) // [2026-11-18] 워프 포인트 바로 아래
        boolean landOk = mineWalk(target, ax, ay, water); // [2026-10-28] "섬에서 뱃길로 나오면 그 섬 입구 쪽에" -- 뱃길(배로 물 위)은 물 칸도 내리는 칸(예전엔 걸을 칸이 아니라고 시작점으로 보냈다)
        int tx = landOk ? ax : target.spawnX, ty = landOk ? ay : target.spawnY;
        String sailMsg = "";
        if (target.sail() && !target.boat() && water < 1 && !target.walkable(tx, ty) && !mineCanSail(userName, items)) {
            // [2026-11-21] 고침: "다도해 뱃길에서 배 타고 본섬으로 오면 못 움직인다" -- 뱃길 끝을 넘으면 본섬/섬의 물 칸에 내리는데, 배가 부서졌거나(체력 0) 없으면
            //  화면은 물 위로 못 가게 막아(seaBoatOk) 물 한가운데 갇혔다. 그럴 땐 가장 가까운 뭍에 내린다.
            int[] ld = mineNearLand(target, tx, ty);
            if (ld != null) { tx = ld[0]; ty = ld[1]; sailMsg = "⛵ 배가 부서져 있어 가까운 뭍에 내렸어요. (배 목수에게 고쳐 달라고 하세요) "; }
        }
        dao.updateMinePos(userName, target.id, tx, ty, now);
        mineDisengageAll(userName); // [2026-10-25] 다른 맵으로 가면 인식 끝
        String chaosMob = S5MineMap.CHAOS.equals(target.id) ? mineChaosStart(userName, target, now) : null; // [2026-11-17] 혼돈스러운 마계 -- 악마화된 몬스터 하나
        if (S5MineMap.DEMON_VILLAGE.equals(target.id)) { // [2026-11-17] 마계 마을 -- 안내 없이(3초 뒤 요새 도시로)
            MINE_VILLAGE_AT.put(userName, now);
            dao.addUserOre(userName, S5MineMap.MANA_STONE_CODE, 1); // [2026-11-20] "마계 마을 가면 정제된 마석 1개" (캐기는 막음)
            dao.upsertMineItem(userName, MINE_ITEM_VILLAGE_CNT, items.getOrDefault(MINE_ITEM_VILLAGE_CNT, 0) + 1); // 다녀간 횟수(지금부터 센다)
            out.put("ok", true);
            out.put("village", true);
            out.put("manaStone", 1);
            out.put("message", "💠 마계의 기운이 손안에 엉겨 " + S5MineMap.MANA_STONE_NAME + " 1개가 되었어요.");
            out.put("state", mineState(userName));
            return out;
        }
        // [2026-10-28] "설산 1,2,3층 순서대로 밟아야 설산에 간 걸로 인정" -- 북쪽 끝 설원 -> 1층 -> 2층 -> 3층으로 올라온 만큼(SNOW_PATH 1~3)을 적고,
        //  3층까지 올라온 뒤 3층에서 마을로 들어가야 설산 마을 방문(이동 안내원 목적지). 마을에서 내려간 층은 안 친다.
        //  같은 날 "왕도 북쪽 샛길로 와도 인정" -- 샛길에서 서문으로 들어와도 방문.
        int snowPath = items.getOrDefault(MINE_ITEM_SNOW_PATH, 0), snowNext = snowPath;
        if (S5MineMap.SNOW1.equals(target.id) && S5MineMap.NORTH.equals(map.id)) snowNext = Math.max(snowPath, 1);
        else if (S5MineMap.SNOW2.equals(target.id) && S5MineMap.SNOW1.equals(map.id) && snowPath >= 1) snowNext = Math.max(snowPath, 2);
        else if (S5MineMap.SNOW3.equals(target.id) && S5MineMap.SNOW2.equals(map.id) && snowPath >= 2) snowNext = Math.max(snowPath, 3);
        if (snowNext != snowPath) dao.upsertMineItem(userName, MINE_ITEM_SNOW_PATH, snowNext);
        if (S5MineMap.SNOWTOWN.equals(target.id) && ((S5MineMap.SNOW3.equals(map.id) && snowNext >= 3) || S5MineMap.ROYALROAD.equals(map.id)) && items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) < 1) // [2026-10-26] 설산을 넘어 처음 도착 -- 이제 이동 안내원으로 오갈 수 있다
            dao.upsertMineItem(userName, MINE_ITEM_VISIT_SNOWTOWN, 1);
        if (S5MineMap.CITY.equals(target.id) && items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) < 1) // [2026-10-27] 왕도도 걸어서 한 번 도착해야 이동 안내원 목적지
            dao.upsertMineItem(userName, MINE_ITEM_VISIT_CITY, 1);
        if (S5MineMap.ARCHI.equals(target.id) && items.getOrDefault(MINE_ITEM_VISIT_ARCHI, 0) < 1) // [2026-10-28] 다도해 마을도 한 번 배로 와 봐야 이동 안내원 목적지
            dao.upsertMineItem(userName, MINE_ITEM_VISIT_ARCHI, 1);
        out.put("ok", true);
        out.put("message", sailMsg + llMsg + (chaosMob != null ? "🌌 마계의 문이 당신을 집어삼켰어요... 혼돈스러운 마계! " + chaosMob + "이(가) 깨어났어요 -- 쓰러뜨려야 귀환의 문이 열려요. " : abyss ? "🌀 마계의 문이 당신을 집어삼켰다가... " + target.name + "에 내던졌어요! " : "") + (ship ? "⛵ 배를 타고 " + target.name + "에 도착했어요." : tele ? "🌀 텔레포트! " + target.name + "(으)로 왔어요." + (MINE_TELE_GOLD > 0 ? " (금광석 " + MINE_TELE_GOLD + "개 사용)" : "") : p.kind == 'o' ? "⛵ 배를 띄워 " + target.name + "(으)로 나왔어요." /* [2026-11-18] 물가에 닿으면 */ : "🚪 " + target.name + "(으)로 이동했어요.")
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
    HashMap<String, Object> mineHelmetInfo(String userName, int tier) {
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
        if (city4 && !mineCostOk(MINE_HELMET_EXTRA4, mineOreHave(userName))) return "재료가 부족해요. (" + mineCostText(MINE_HELMET_EXTRA4) + "도 필요" + mineLackText(MINE_HELMET_EXTRA4, userName) + ")";
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

    static String fmtPpBonus(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    /** 자동사냥 정산 처치 수만큼의 광산 수익(처치당 PP_BONUS). 광산 기록이 없거나 보너스가 0이면 null. */
    PP minePpBonus(String userName, long kills) {
        HashMap<String, Object> row = dao.selectMineUser(userName);
        double perKill = row == null ? 0 : numVal(row.get("PP_BONUS"), 0);
        if (perKill <= 0 || kills <= 0) return null;
        return PP.of(perKill * kills, "");
    }

    static final String MINE_RING_ABYSS_MSG = "💍 마계 입구는 마계의 기운이 너무 거세 용의 반지의 힘이 닿지 않아요. 최후의 방어선을 지나 걸어서 가 주세요.";
    /** [2026-11-15] 전체 지도 이동 목록(관리자 화면 목록과 같다) -- 용의 반지가 갈 수 있는 곳을 가린다. */
    static boolean mineWarpListed(String mapId) {
        if (S5MineMap.CHAOS.equals(mapId) || S5MineMap.DEMON_VILLAGE.equals(mapId)) return false; // [2026-11-17] 혼돈스러운 마계 / 마계 마을은 마계 입구 포털로만(관리자 이동은 따로)
        for (String id : new String[] { S5MineMap.FIELD, S5MineMap.CAVE1, S5MineMap.DESERT, S5MineMap.BEACH, S5MineMap.DUNGEON, S5MineMap.CASTLE, S5MineMap.ISLAND, S5MineMap.NORTH, S5MineMap.STEPPE, S5MineMap.CITY, S5MineMap.CAVE2, S5MineMap.CAVE3, S5MineMap.SEAROAD,
                    S5MineMap.SNOW1, S5MineMap.SNOW2, S5MineMap.SNOW3, S5MineMap.SNOWTOWN, S5MineMap.ROYALROAD, S5MineMap.RUINS, S5MineMap.SNOWROAD, S5MineMap.PEAK,
                    S5MineMap.ANTNEST, S5MineMap.PYRAMID, S5MineMap.SNOWCAVE, S5MineMap.SNOWCAVE2, S5MineMap.CAVE4, S5MineMap.PYRAMID_UNDER, S5MineMap.WESTROAD, S5MineMap.FOREST, S5MineMap.HARPY_NEST, S5MineMap.HARPY_FERRY, S5MineMap.TOWER_CITY, S5MineMap.TOWER_GATE, S5MineMap.DEMON_WEST, S5MineMap.DEMON_EAST, S5MineMap.DEMON_WEST2, S5MineMap.DEMON_EAST2, S5MineMap.DEMON_WEST3, S5MineMap.DEMON_EAST3, S5MineMap.LAST_LINE, S5MineMap.ABYSS_GATE, S5MineMap.CHAOS, S5MineMap.DEMON_VILLAGE }) if (id.equals(mapId)) return true;
        for (String id : S5MineMap.ARCHI_MAPS) if (id.equals(mapId)) return true;
        return false;
    }

    /** [2026-10-24] 관리자 전용 좌표 이동(MINE_ADMIN_USERS 또는 EVENT_ADMIN_USERS) -- 화면 좌표를 누르면 맵/좌표 입력. 못 서는 칸이면 가장 가까운 설 수 있는 칸으로. */
    @Override
    public HashMap<String, Object> mineAdminWarp(String userName, String mapId, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        boolean ringWarp = false;
        if (!isMineAdmin(userName)) { // [2026-11-15] 용의 반지 -- 끼고 있으면 전체 지도 이동(지역 시작점으로)을 한 시간에 한 번
            Map<String, Integer> rit = mineItems(userName);
            if (!mineWyrmOn(rit)) { out.put("message", "권한이 없습니다."); return out; }
            long nowR = System.currentTimeMillis(), ready = rit.getOrDefault(MINE_ITEM_WYRM_WARP, 0) * 60_000L + MINE_WYRM_CD_MS;
            if (nowR < ready) { out.put("message", "💍 용의 반지의 힘이 아직 돌아오지 않았어요. (" + mineCallLeft(ready - nowR) + " 뒤)"); return out; }
            if (S5MineMap.ABYSS_GATE.equals(mapId)) { out.put("message", MINE_RING_ABYSS_MSG); return out; } // [2026-11-20] "마계 입구는 안 된다고 띄우든지"
            if (!mineWarpListed(mapId)) { out.put("message", "💍 용의 반지로는 갈 수 없는 곳이에요."); return out; }
            { HashMap<String, Object> rr = dao.selectMineUser(userName); if (rr != null && S5MineMap.CHAOS.equals(strVal(rr.get("MAP_ID"), ""))) { out.put("message", "💍 차원이 뒤틀린 곳이라 용의 반지의 힘이 닿지 않아요."); return out; } } // [2026-11-17] "혼돈스러운 마계는 차원 이동이라 장비 텔레포트를 못 써"
            S5MineMap dm = S5MineMap.get(mapId);
            String blk = mineMapBlock(dm, rit, 0);
            if (blk != null) { out.put("message", blk); return out; }
            if (mineFrozen(userName, nowR) != null || mineStunned(userName, nowR) || mineWbDownUntil(userName, nowR) > 0) { out.put("message", "지금은 움직일 수 없어요."); return out; }
            MineEngage re = MINE_ENGAGE.get(userName);
            if (re != null && !re.mobs.isEmpty() && mineEngaged(userName, nowR)) { out.put("message", "⚔️ 싸우는 중엔 용의 반지를 쓸 수 없어요."); return out; }
            x = dm.spawnX; y = dm.spawnY;
            ringWarp = true;
        }
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        S5MineMap map = S5MineMap.get(mapId);
        int water = mineWalkMode(mineItems(userName));
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
        if (S5MineMap.CHAOS.equals(map.id)) { mineDisengageAll(userName); String cm = mineChaosStart(userName, map, System.currentTimeMillis()); out.put("chaosMob", cm); } // [2026-11-17] 관리자 시험 -- 혼돈 몬스터도 깨운다
        if (S5MineMap.DEMON_VILLAGE.equals(map.id)) MINE_VILLAGE_AT.put(userName, System.currentTimeMillis()); // [2026-11-17] 마계 마을 -- 3초
        MINE_ICE_FROZEN.remove(userName); // [2026-10-28] 관리자 이동 -- 얼어 있었으면 녹인다(다른 맵에서도 막히지 않게)
        out.put("ok", true);
        out.put("message", "🛠️ " + map.name + " (" + p[0] + ", " + p[1] + ")로 이동했어요." + (p[0] != x || p[1] != y ? " (입력한 칸에 설 수 없어 가장 가까운 칸으로)" : ""));
        if (ringWarp) {
            mineDisengageAll(userName);
            dao.upsertMineItem(userName, MINE_ITEM_WYRM_WARP, (int) (System.currentTimeMillis() / 60_000L));
            out.put("message", "💍 용의 반지의 힘으로 " + map.name + "에 왔어요. (다음은 한 시간 뒤)");
        }
        out.put("state", mineState(userName));
        return out;
    }

    // ---- [2026-10-28] 설산 동굴 연속 채광 -- 바라보는 얼음 광맥(I)을 캘 때마다 한 번, MINE_VEIN_NEED번 이어서 캐면 빙석 1개(다시 0부터).
    //  몬스터에게 맞으면(전투 턴 / 반격) 0으로 돌아간다(mineVeinBreak). 곡괭이 내구도는 쓰지 않는다. 숫자는 서버 메모리(맵이 바뀌면 처음부터).
    static final int MINE_VEIN_NEED = 5; // [2026-11-19] 20 -> 5(사용자 "빙석 20번 때리면 사라지는데 5번으로")
    static final int MINE_VEIN_CUT_PCT = 4; // [2026-10-28] 설산 동굴 2층 -- 캘 때마다 세공된 빙석이 나올 확률(%)
    static final long MINE_VEIN_GAP_MS = 300L;
    static final class VeinState { String mapVer; int count; long at; }
    static final java.util.concurrent.ConcurrentHashMap<String, VeinState> MINE_VEIN = new java.util.concurrent.ConcurrentHashMap<>();

    /** 연속 채광이 이어지던 중이었으면 끊고 true. */
    static boolean mineVeinBreak(String userName) {
        VeinState v = MINE_VEIN.get(userName);
        if (v == null) return false;
        synchronized (v) {
            if (v.count <= 0) return false;
            v.count = 0;
            return true;
        }
    }

    @Override
    @Transactional
    public HashMap<String, Object> mineVein(String userName, String mapId, String ver, int x, int y, int tx, int ty) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        if (!map.veinMine() || !map.id.equals(mapId) || mineStaleVersion(map, ver)) return out;
        Map<String, Integer> items = mineItems(userName);
        if (!mineMoveOk(minePos(row, map, mineWalkMode(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        if (Math.abs(tx - x) + Math.abs(ty - y) != 1 || map.terrainAt(tx, ty) != 'I') { out.put("message", "💠 얼음 광맥 바로 앞에서 그쪽을 보고 캐요."); return out; }
        if (mineFrozen(userName, now) != null) { out.put("message", MINE_FROZEN_BLOCK); return out; }
        // [2026-10-29] "이미 캔 빙석은 못 캐게" -- 빙석을 얻은 광맥(이어진 I 칸 전체)은 이 유저에게 맵 리셋 때까지 다 캔 광맥(캔 칸 기록에 남긴다)
        String dugKey = mineDugKey(map, now, items);
        if (dao.countMineDug(userName, dugKey, ty * map.w + tx) > 0) { out.put("veinDone", true); out.put("message", "💠 이미 빙석을 캔 광맥이에요. 맵 리셋 때 다시 차올라요."); return out; }
        VeinState v = MINE_VEIN.computeIfAbsent(userName, u -> new VeinState());
        int count;
        boolean gain = false;
        synchronized (v) {
            if (!map.version().equals(v.mapVer)) { v.mapVer = map.version(); v.count = 0; }
            if (now - v.at < MINE_VEIN_GAP_MS) { out.put("tooFast", true); return out; }
            v.at = now;
            v.count++;
            if (v.count >= MINE_VEIN_NEED) { v.count = 0; gain = true; }
            count = gain ? MINE_VEIN_NEED : v.count;
        }
        if (gain) {
            dao.addUserOre(userName, S5MineMap.ICE_STONE_CODE, 1);
            List<Integer> vc = mineVeinCells(map, tx, ty);
            for (int c : vc) dao.insertMineDug(userName, dugKey, c, S5MineMap.ICE_STONE_GRADE, now);
            List<int[]> cells = new ArrayList<>();
            for (int c : vc) cells.add(new int[] { c % map.w, c / map.w });
            out.put("veinCells", cells); // 화면이 이 칸들을 다 캔 광맥으로 그린다
        }
        boolean cutIce = S5MineMap.SNOWCAVE2.equals(map.id) && RND.nextInt(100) < MINE_VEIN_CUT_PCT; // [2026-10-28] 2층 -- 가끔 세공된 빙석
        if (cutIce) { dao.addUserOre(userName, S5MineMap.CUT_ICE_CODE, 1); out.put("cutIce", true); out.put("ores", mineOreList(userName)); }
        out.put("ok", true);
        out.put("count", count);
        out.put("need", MINE_VEIN_NEED);
        if (gain) {
            out.put("gain", true);
            out.put("grade", S5MineMap.ICE_STONE_GRADE);
            out.put("ores", mineOreList(userName));
            out.put("message", "💠 " + MINE_VEIN_NEED + "번 연속 채광 성공! 🧊 " + S5MineMap.ICE_STONE_NAME + " 1개를 얻었어요.");
        }
        return out;
    }

    /** [2026-10-29] (tx, ty)와 이어진 얼음 광맥(I) 칸 전체(상하좌우, 최대 64칸). */
    static List<Integer> mineVeinCells(S5MineMap map, int tx, int ty) {
        List<Integer> out = new ArrayList<>();
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int s0 = ty * map.w + tx;
        q.add(s0); seen.add(s0);
        while (!q.isEmpty() && out.size() < 64) {
            int c = q.poll(), x = c % map.w, y = c / map.w;
            out.add(c);
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int nx = x + d[0], ny = y + d[1], nc = ny * map.w + nx;
                if (nx < 0 || ny < 0 || nx >= map.w || ny >= map.h || seen.contains(nc) || map.terrainAt(nx, ny) != 'I') continue;
                seen.add(nc); q.add(nc);
            }
        }
        return out;
    }

    /** [2026-10-28] 사막 서쪽(S5MineMap.DESERT_FALL_X 왼쪽)을 걷다가 화면이 확률로 부른다 -- 개미굴 한가운데로 떨어진다.
     *  사막의 그 칸에 있는지(이동 기록 검사)만 확인하고, 확률은 화면이 정한다(빠지고 싶어서 일부러 부르는 건 막을 이유가 없다). */
    @Override
    @Transactional
    public HashMap<String, Object> mineAntFall(String userName, String mapId, int x, int y) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        if (!S5MineMap.DESERT.equals(map.id) || !S5MineMap.DESERT.equals(mapId) || x >= map.w * S5MineMap.DESERT_FALL_X || !map.walkable(x, y)) return out;
        Map<String, Integer> items = mineItems(userName);
        if (mineLv(row) < MINE_MIN_LV.getOrDefault(S5MineMap.ANTNEST, 41) && mineRebirth(items) < 1) return out; // [2026-11-04] 방벽 경비병 -- 저레벨은 빠지지 않는다(바람에 밀려 들어가도), [2026-11-18] "환생자는 제한 없이"
        if (!mineMoveOk(minePos(row, map, mineWalkMode(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        if (mineFrozen(userName, now) != null) return out;
        mineDisengageAll(userName);
        S5MineMap nest = S5MineMap.get(S5MineMap.ANTNEST);
        dao.updateMinePos(userName, nest.id, nest.spawnX, nest.spawnY, now);
        out.put("ok", true);
        out.put("message", "🕳️ 모래가 꺼지며 아래로 쑥 빨려 들어갔어요... 여기는 개미굴! 지도도 보이지 않아요. 빛이 새어 드는 나가는 굴을 찾아야 해요.");
        out.put("state", mineState(userName));
        return out;
    }

    // ---- [2026-10-28] 광석 전문가 / 보석 세공사(마을마다) / 장인(왕도) ----
    static final int MINE_SMELT_NEED = 100, MINE_SMELT_PCT = 45, MINE_CUT_NEED = 5, MINE_CUT_PCT = 25, MINE_ARTISAN_MIN = 1000, MINE_ARTISAN_GAIN = 30;
    /** [2026-10-28] 세공 목록 번호 -- 0~8 보석(GEM_CODES), 9 빙석, 10~14 다도해 바다 보석(SEA_GEM_CODES). */
    static final int MINE_CUT_KINDS = 15;
    static String mineCutSrc(int i) { return i < 9 ? S5MineMap.GEM_CODES[i] : i == 9 ? S5MineMap.ICE_STONE_CODE : S5MineMap.SEA_GEM_CODES[i - 10]; }
    static String mineCutSrcName(int i) { return i < 9 ? S5MineMap.GEM_NAMES[i] : i == 9 ? S5MineMap.ICE_STONE_NAME : S5MineMap.SEA_GEM_NAMES[i - 10]; }
    static String mineCutDst(int i) { return i < 9 ? S5MineMap.cutCode(i) : i == 9 ? S5MineMap.CUT_ICE_CODE : S5MineMap.seaCutCode(i - 10); }
    static String mineCutDstName(int i) { return i < 9 ? S5MineMap.cutName(i) : i == 9 ? S5MineMap.CUT_ICE_NAME : S5MineMap.seaCutName(i - 10); }

    boolean mineAtNpc(HashMap<String, Object> row, String kind) {
        for (S5MineMap.Npc n : mineMapOf(row).npcs()) if (kind.equals(n.kind)) return true;
        return false;
    }

    /** 광석 전문가 -- 광석(등급 1~5) MINE_SMELT_NEED개마다 한 번, MINE_SMELT_PCT% 확률로 괴 하나(실패해도 광석은 녹아 없어진다). times = 시도 횟수. */
    @Override
    @Transactional
    public HashMap<String, Object> mineSmelt(String userName, int grade, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!mineAtNpc(row, "smelter")) return mineGearResult(userName, false, "광석 전문가가 있는 마을에서만 할 수 있어요.");
        if (grade < 1 || grade > S5MineMap.BAR_CODES.length) return mineGearResult(userName, false, "녹일 수 없는 광석이에요.");
        String code = S5MineMap.ORE_CODES[grade - 1];
        int have = mineOreHave(userName).getOrDefault(code, 0), n = Math.min(Math.max(1, times), have / MINE_SMELT_NEED);
        if (n < 1) return mineGearResult(userName, false, "🔥 " + S5MineMap.ORE_NAMES[grade - 1] + "이(가) " + MINE_SMELT_NEED + "개 있어야 해요. (보유 " + have + ")");
        if (dao.takeUserOre(userName, code, MINE_SMELT_NEED * n) == 0) return mineGearResult(userName, false, "광석이 모자라요.");
        int ok = 0;
        for (int i = 0; i < n; i++) if (RND.nextInt(100) < MINE_SMELT_PCT) ok++;
        if (ok > 0) dao.addUserOre(userName, S5MineMap.BAR_CODES[grade - 1], ok);
        HashMap<String, Object> out = mineGearResult(userName, true, "🔥 " + S5MineMap.ORE_NAMES[grade - 1] + " " + (MINE_SMELT_NEED * n) + "개를 녹여 " + n + "번 시도 -> "
                + (ok > 0 ? S5MineMap.BAR_NAMES[grade - 1] + " " + ok + "개 성공!" : "모두 실패했어요...") + " (성공 " + MINE_SMELT_PCT + "%)");
        if (ok > 0) out.put("craftedRing", true); // 화면 번쩍
        return out;
    }

    /** 보석 세공사 -- 보석(GEM_CODES 순서) MINE_CUT_NEED개마다 한 번, MINE_CUT_PCT% 확률로 세공된 보석 하나(실패하면 보석은 부서진다). */
    @Override
    @Transactional
    public HashMap<String, Object> mineGemCut(String userName, int gemIdx, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!mineAtNpc(row, "gemcutter")) return mineGearResult(userName, false, "보석 세공사가 있는 마을에서만 할 수 있어요.");
        if (gemIdx < 0 || gemIdx >= MINE_CUT_KINDS) return mineGearResult(userName, false, "세공할 수 없는 보석이에요.");
        String code = mineCutSrc(gemIdx);
        int have = mineOreHave(userName).getOrDefault(code, 0), n = Math.min(Math.max(1, times), have / MINE_CUT_NEED);
        if (n < 1) return mineGearResult(userName, false, "💎 " + mineCutSrcName(gemIdx) + "이(가) " + MINE_CUT_NEED + "개 있어야 해요. (보유 " + have + ")");
        if (dao.takeUserOre(userName, code, MINE_CUT_NEED * n) == 0) return mineGearResult(userName, false, "보석이 모자라요.");
        int ok = 0;
        for (int i = 0; i < n; i++) if (RND.nextInt(100) < MINE_CUT_PCT) ok++;
        if (ok > 0) dao.addUserOre(userName, mineCutDst(gemIdx), ok);
        HashMap<String, Object> out = mineGearResult(userName, true, "💎 " + mineCutSrcName(gemIdx) + " " + n + "번 세공 -> "
                + (ok > 0 ? mineCutDstName(gemIdx) + " " + ok + "개 성공!" : " 깨졌어요...") + " (성공 " + MINE_CUT_PCT + "%)");
        if (ok > 0) out.put("craftedRing", true);
        return out;
    }

    /** 왕도 장인 -- 곡괭이 최대 내구도가 MINE_ARTISAN_MIN 이상이면 세공된 보석 하나에 최대(와 지금) 내구도 +MINE_ARTISAN_GAIN(대장장이 상한 위로). */
    // [2026-11-13] "10회차까지는 1개로 30, 20회차까지는 2개로 30 ..." -- 회차 = (최대 내구도 - 1000) / 30 + 1, 보석 = (회차 - 1) / 10 + 1개(같은 종류).
    //  같은 날 예전에 1개씩 올린 유저는 쓴 보석 수로 새 규칙을 다시 계산해 맞췄다(남는 보석이 있으면 한 회차 더 -- S5_PICK_ARTISAN_MIGRATE.sql).
    static int mineArtisanRound(int max) { return Math.max(0, (max - MINE_ARTISAN_MIN) / MINE_ARTISAN_GAIN) + 1; }
    static int mineArtisanCost(int max) { return (mineArtisanRound(max) - 1) / 10 + 1; }
    @Override
    @Transactional
    public HashMap<String, Object> mineArtisan(String userName, int gemIdx) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!mineAtNpc(row, "artisan")) return mineGearResult(userName, false, "왕도 장인에게서만 할 수 있어요.");
        int dur = minePickDur(row), max = minePickMax(row);
        if (max < MINE_ARTISAN_MIN) return mineGearResult(userName, false, "🔨 곡괭이 최대 내구도가 " + MINE_ARTISAN_MIN + " 이상이어야 해요. (지금 " + max + "  대장장이에게서 먼저 올려 주세요)");
        int cost = mineArtisanCost(max);
        if (gemIdx < 0 || gemIdx >= MINE_CUT_KINDS || dao.takeUserOre(userName, mineCutDst(gemIdx), cost) == 0)
            return mineGearResult(userName, false, "세공된 보석이 " + cost + "개 있어야 해요. (" + mineArtisanRound(max) + "회차 -- 보석 세공사에게서 만들 수 있어요)");
        dao.addMinePickMax(userName, MINE_ARTISAN_GAIN);
        HashMap<String, Object> out = mineGearResult(userName, true, "🔨 " + mineCutDstName(gemIdx) + " " + cost + "개를 곡괭이에 박아 넣었어요! (" + mineArtisanRound(max) + "회차) 최대 내구도 +" + MINE_ARTISAN_GAIN
                + " (지금 " + (dur + MINE_ARTISAN_GAIN) + " / " + (max + MINE_ARTISAN_GAIN) + ")");
        out.put("durability", dur + MINE_ARTISAN_GAIN);
        out.put("durabilityMax", max + MINE_ARTISAN_GAIN);
        out.put("craftedRing", true);
        return out;
    }

    /** [2026-10-28] 개미굴 보물 상자(구석진 방) -- "인당 3개씩만, 보상이 아주 적은 것(1개)부터 지금 보상까지 골고루":
     *  한 사람이 맵 주기마다 MINE_ANT_CHEST_LIMIT개까지. 상자마다 담긴 양 q(0~1000, S5MineMap.chestRich) -- 보석 1 + q*3/1000번(1~4번),
     *  한 번에 1 ~ 1 + q*2/1000개(가장 적은 상자 = 보석 1개), 다이아몬드 q*25/1000 %(가장 많은 상자 = 예전 보상: 네 번 1~3개 + 25% 다이아).
     *  연 상자는 ANT_CHEST_CYC(주기 + 1) / ANT_CHEST_MASK(상자 순서 비트)로 기억한다. */
    static final String MINE_ITEM_ANT_CHEST_CYC = "ANT_CHEST_CYC", MINE_ITEM_ANT_CHEST_MASK = "ANT_CHEST_MASK";
    static final int MINE_ANT_CHEST_LIMIT = 10; // [2026-10-28] 3 -> "10개 다 열 수 있게"

    @Override
    @Transactional
    public HashMap<String, Object> mineAntChest(String userName) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        int[] pos = minePos(row, map, 0);
        int idx = -1, k = 0;
        S5MineMap.Npc chest = null;
        for (S5MineMap.Npc n : map.npcs()) {
            if (!"antchest".equals(n.kind)) continue;
            if (Math.abs(pos[0] - n.x) + Math.abs(pos[1] - n.y) <= 2) { idx = k; chest = n; }
            k++;
        }
        if (idx < 0) { out.put("message", "상자 바로 앞에서 열어 주세요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        int cyc = S5MineMap.activeCycle() + 1, mask = items.getOrDefault(MINE_ITEM_ANT_CHEST_CYC, 0) == cyc ? items.getOrDefault(MINE_ITEM_ANT_CHEST_MASK, 0) : 0;
        if ((mask & (1 << idx)) != 0) { out.put("message", "📦 이미 열어 본 상자예요. (맵 리셋 " + mineResetText() + " 뒤 다시 채워져요)"); return out; }
        if (Integer.bitCount(mask) >= MINE_ANT_CHEST_LIMIT) { out.put("message", "📦 이번 맵에서는 보물 상자를 " + MINE_ANT_CHEST_LIMIT + "개까지만 열 수 있어요. (맵 리셋 " + mineResetText() + " 뒤 다시)"); return out; }
        int q = map.chestRich(chest.x, chest.y), picks = 1 + q * 3 / 1000, qmax = 1 + q * 2 / 1000;
        dao.upsertMineItem(userName, MINE_ITEM_ANT_CHEST_CYC, cyc);
        dao.upsertMineItem(userName, MINE_ITEM_ANT_CHEST_MASK, mask | (1 << idx));
        List<HashMap<String, Object>> drops = new ArrayList<>();
        StringBuilder gm = new StringBuilder();
        for (int i = 0; i < picks; i++) {
            int gi = RND.nextInt(S5MineMap.GEM_CODES.length - 1);
            if (gi >= S5MineMap.GEM_DIAMOND) gi++;
            int gq = 1 + RND.nextInt(qmax);
            dao.addUserOre(userName, S5MineMap.GEM_CODES[gi], gq);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + gi, S5MineMap.GEM_NAMES[gi], gq));
            gm.append(gm.length() > 0 ? ", " : "").append(S5MineMap.GEM_NAMES[gi]).append(' ').append(gq);
        }
        if (RND.nextInt(1000) < q * 25 / 100) {
            dao.addUserOre(userName, S5MineMap.GEM_CODES[S5MineMap.GEM_DIAMOND], 1);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + S5MineMap.GEM_DIAMOND, S5MineMap.GEM_NAMES[S5MineMap.GEM_DIAMOND], 1));
            gm.append(", 다이아몬드 1");
        }
        // [2026-10-28] "달빛석이 나오게 하지 말아 줘" -- 예전 20% 달빛석 1개는 뺐다(달빛석은 다도해 배 재료라 다도해 밖에서 나오면 안 된다)
        int opened = Integer.bitCount(mask | (1 << idx));
        out.put("ok", true);
        out.put("drops", drops);
        out.put("message", "📦 보물 상자를 열었어요! 💎 " + gm + " (이번 맵에서 " + opened + "/" + MINE_ANT_CHEST_LIMIT + "개 열었어요)");
        out.put("ores", mineOreList(userName));
        return out;
    }

    /** [2026-10-28] 피라미드 보상 상자 -- 상자 2칸 안에서 열면 사막의 심장 1개 + "보석 많이, 골고루": 보석 아홉 가지 모두 2~4개씩(다이아몬드는 1~2개).
     *  맵 주기마다 한 번(DESERT_CHEST = 주기 + 1). */
    static final String MINE_ITEM_DESERT_CHEST = "DESERT_CHEST";

    /** [2026-11-04] 피라미드 상자 보상(사막의 심장 1개 + 보석 아홉 가지 2~4개, 다이아 1~2개) -- 상자와 파라오 미라 처치(100%)가 함께 쓴다. gm = 보석 목록 글. */
    private List<HashMap<String, Object>> mineDesertGems(String userName, StringBuilder gm) {
        dao.addUserOre(userName, S5MineMap.DESERT_HEART_CODE, 1);
        List<HashMap<String, Object>> drops = new ArrayList<>();
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) {
            int q = i == S5MineMap.GEM_DIAMOND ? 1 + RND.nextInt(2) : 2 + RND.nextInt(3);
            dao.addUserOre(userName, S5MineMap.GEM_CODES[i], q);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + i, S5MineMap.GEM_NAMES[i], q));
            gm.append(gm.length() > 0 ? ", " : "").append(S5MineMap.GEM_NAMES[i]).append(' ').append(q);
        }
        return drops;
    }

    /** [2026-11-04] 피라미드 상자 꽝일 때 -- 보석 세 가지를 1~2개씩(다이아는 1개), 가진 개수가 적은 보석부터(같으면 무작위). */
    private List<HashMap<String, Object>> mineDesertFewGems(String userName, StringBuilder gm) {
        Map<String, Integer> have = mineOreHave(userName);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) idx.add(i);
        Collections.shuffle(idx, RND);
        idx.sort((a, b) -> have.getOrDefault(S5MineMap.GEM_CODES[a], 0) - have.getOrDefault(S5MineMap.GEM_CODES[b], 0));
        List<HashMap<String, Object>> drops = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            int i = idx.get(k), q = i == S5MineMap.GEM_DIAMOND ? 1 : 1 + RND.nextInt(2);
            dao.addUserOre(userName, S5MineMap.GEM_CODES[i], q);
            drops.add(mineDrop(S5MineMap.GEM_GRADE_BASE + i, S5MineMap.GEM_NAMES[i], q));
            gm.append(gm.length() > 0 ? ", " : "").append(S5MineMap.GEM_NAMES[i]).append(' ').append(q);
        }
        return drops;
    }

    private List<HashMap<String, Object>> mineDesertReward(String userName, StringBuilder msg) {
        StringBuilder gm = new StringBuilder();
        List<HashMap<String, Object>> drops = mineDesertGems(userName, gm);
        drops.add(0, mineDrop(S5MineMap.DESERT_HEART_GRADE, S5MineMap.DESERT_HEART_NAME, 1));
        msg.append(" · 📦 피라미드 상자 보상! 🧡 ").append(S5MineMap.DESERT_HEART_NAME).append(" + ").append(gm);
        return drops;
    }
    static final int MINE_DESERT_CHEST_PCT = 40;

    static String mineResetText() {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(S5MineMap.nextResetAt(System.currentTimeMillis())).atZone(KST);
        return z.getMonthValue() + "/" + z.getDayOfMonth() + " " + String.format("%02d:%02d", z.getHour(), z.getMinute());
    }

    @Override
    @Transactional
    public HashMap<String, Object> mineDesertChest(String userName) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        S5MineMap.Npc chest = null;
        for (S5MineMap.Npc n : map.npcs()) if ("chest".equals(n.kind)) chest = n;
        int[] pos = minePos(row, map, 0);
        if (chest == null || Math.abs(pos[0] - chest.x) + Math.abs(pos[1] - chest.y) > 2) { out.put("message", "상자 바로 앞에서 열어 주세요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        int cyc = S5MineMap.activeCycle() + 1;
        if (items.getOrDefault(MINE_ITEM_DESERT_CHEST, 0) == cyc) { // [2026-10-30] "피라미드 보상도 다회, 확률 40%" -- 맵 주기마다 한 번 열고, 40%로 보상
            out.put("message", "📦 이미 열어 본 상자예요. (맵 리셋 " + mineResetText() + " 뒤 다시 열 수 있어요)");
            return out;
        }
        dao.upsertMineItem(userName, MINE_ITEM_DESERT_CHEST, cyc);
        if (RND.nextInt(100) >= MINE_DESERT_CHEST_PCT) { // [2026-11-04] "꽝이어도 보석은 항상 조금, 내가 없는 보석 위주로"
            StringBuilder fg = new StringBuilder();
            out.put("ok", true);
            out.put("drops", mineDesertFewGems(userName, fg));
            out.put("message", "📦 사막의 심장은 없었지만 보석이 조금 들어 있었어요! (" + fg + ") -- 맵 리셋 " + mineResetText() + " 뒤 다시 열 수 있어요");
            out.put("ores", mineOreList(userName));
            return out;
        }
        StringBuilder gm = new StringBuilder();
        List<HashMap<String, Object>> drops = mineDesertGems(userName, gm);
        out.put("ok", true);
        out.put("grade", S5MineMap.DESERT_HEART_GRADE);
        out.put("drops", drops);
        out.put("message", "📦 상자가 열렸어요! 모래빛으로 고동치는 🧡 " + S5MineMap.DESERT_HEART_NAME + " + 💎 보석이 와르르! (" + gm + ")");
        out.put("ores", mineOreList(userName));
        return out;
    }

    // ---- [2026-10-30] 은둔한 대장장이 -- "보스 증표 넷 + 무기 조각으로 무기 등급업(1~3단계), 다도해(본섬 + 7개 섬) 어딘가에 랜덤한 시간에 나와 30분".
    //  [2026-10-31] "항상 어딘가(일곱 섬)에 있고, 누군가 업그레이드하면 즉시 6시간 사라짐" -- 자리는 세대 번호(gen)로 정한다(맵 리셋 때는 새 맵 안에서 다시).
    //  사라지는 시각과 세대는 설정값 MINE_HERMIT = "돌아오는 시각(ms)|세대"에 남겨 서버를 다시 켜도 이어진다.
    static final String MINE_HERMIT_KEY = "MINE_HERMIT";
    static final long MINE_HERMIT_GONE_MS = 6 * 3_600_000L;
    static final Object MINE_HERMIT_LOCK = new Object();
    static volatile long mineHermitBackAt = -1, mineHermitGen = 0;
    static volatile String mineHermitSpotKey = "";
    static volatile int[] mineHermitSpot;
    static final int MINE_HERMIT_RANGE = 3;
    static final String[] MINE_GRADE_NAMES = { "일반", "고급", "희귀", "영웅", "전설", "유물", "고대", "신화" };
    static String[] mineHermitMaps() { return S5MineMap.ISLE_IDS; }

    /** 지금 나와 있으면 {맵 번호, x, y, 표시 유효 시각}, 사라져 있으면 null. */
    long[] mineHermitNow(long now) {
        if (mineHermitBackAt < 0) {
            synchronized (MINE_HERMIT_LOCK) {
                if (mineHermitBackAt < 0) {
                    long back = 0, gen = 0;
                    try {
                        String c = dao.selectConfigValue(MINE_HERMIT_KEY);
                        if (c != null) { String[] p = c.split("\\|"); back = Long.parseLong(p[0].trim()); gen = Long.parseLong(p[1].trim()); }
                    } catch (Exception e) {
                        back = 0;
                    }
                    mineHermitGen = gen;
                    mineHermitBackAt = back;
                }
            }
        }
        // [2026-11-21] "누군가 기능을 사용해도 남아 있도록" -- 이제 사라지지 않는다(돌아오는 시각은 안 본다). 자리는 맵 리셋(2일)마다 바뀐다(주기 번호를 시드에).
        long gen = mineHermitGen;
        java.util.Random r = new java.util.Random(gen * 7919L + 20261031L + S5MineMap.activeCycle() * 104729L);
        int mi = r.nextInt(mineHermitMaps().length);
        S5MineMap map = S5MineMap.get(mineHermitMaps()[mi]);
        String key = gen + ":" + map.version();
        int[] spot = mineHermitSpot;
        if (spot == null || !key.equals(mineHermitSpotKey)) {
            List<S5MineMap.Mob> mobs = new ArrayList<>();
            for (S5MineMap.Mob mb : map.mobs()) if (!S5MineMobs.TYPES.get(mb.type).boss) mobs.add(mb);
            int x = map.spawnX, y = map.spawnY;
            for (int t = 0; t < 40 && !mobs.isEmpty(); t++) {
                S5MineMap.Mob mb = mobs.get(r.nextInt(mobs.size()));
                int nx = mb.x + r.nextInt(5) - 2, ny = mb.y + r.nextInt(5) - 2;
                if (map.walkable(nx, ny) && !map.safeAt(nx, ny)) { x = nx; y = ny; break; }
            }
            spot = new int[] { x, y };
            mineHermitSpot = spot;
            mineHermitSpotKey = key;
        }
        return new long[] { mi, spot[0], spot[1], now + 60_000L };
    }

    static String[][] mineHermitCost(MineWeapon w, int g) {
        return new String[][] { { S5MineMap.DARK_HEART_CODE, "1" } /* [2026-11-18] 대주교의 인장 -> 어둠의 심장 */, { S5MineMap.ICE_HEART_CODE, "1" }, { S5MineMap.VOLCANO_HEART_CODE, "1" }, { S5MineMap.DESERT_HEART_CODE, "1" }, { w.shard, String.valueOf(g >= 3 ? 25 * (g - 2) : 5 * (g + 1)) } }; // [2026-10-31] 현재 등급에 비례: 일반 5 / 고급 10 / 희귀 15 / 영웅 25 / 전설 50 / 유물 75 / 고대 100
    }

    /** [2026-10-30] 가진 등급 중 가장 높은 등급(없으면 -1) -- 은둔한 대장장이는 이 등급을 기준으로 올린다. */
    static int mineWeaponTop(Map<String, Integer> items, MineWeapon w) {
        return mineWeaponGrade(items, w) < 0 ? -1 : 31 - Integer.numberOfLeadingZeros(mineWeaponMask(items, w));
    }

    /** 가장 높은 등급 g에서 오를 수 있는 등급(1~3단계 위, 무기 최고 등급까지) -- 낮은 순. */
    static List<Integer> mineHermitTargets(MineWeapon w, int g) {
        List<Integer> t = new ArrayList<>();
        for (int n = g + 1; n <= Math.min(g + 3, w.maxGrade); n++) t.add(n);
        return t;
    }

    // ---- [2026-11-20] 떠돌이 갑옷장이 -- "무기 등급업 떠돌이 상인과 비슷하게, 악마의 사냥터 동·서 1~3 맵과 방어선 맵까지, 방어구 등급업, 재료는 비슷하지만 더 고급".
    //  은둔한 대장장이와 같은 방식: 늘 일곱 맵 중 한 곳(세대 번호로 자리)에 있고, 누가 올리면 즉시 6시간 사라진다(MINE_ARMORER = "돌아오는 시각|세대").
    //  재료: 빛·번개·바람·물의 심장 1개씩 + 갑옷 조각(무기의 2배) + 악마의 광석 조각 100 x (등급 + 1). 오르는 폭은 같다(1단계 60% / 2단계 30% / 3단계 10%).
    static final String MINE_ARMORER_KEY = "MINE_ARMORER";
    static final Object MINE_ARMORER_LOCK = new Object();
    static volatile long mineArmorerBackAt = -1, mineArmorerGen = 0;
    static volatile String mineArmorerSpotKey = "";
    static volatile int[] mineArmorerSpot;
    static String[] mineArmorerMaps() { return new String[] { S5MineMap.DEMON_WEST, S5MineMap.DEMON_EAST, S5MineMap.DEMON_WEST2, S5MineMap.DEMON_EAST2, S5MineMap.DEMON_WEST3, S5MineMap.DEMON_EAST3, S5MineMap.LAST_LINE }; }
    long[] mineArmorerNow(long now) {
        if (mineArmorerBackAt < 0) {
            synchronized (MINE_ARMORER_LOCK) {
                if (mineArmorerBackAt < 0) {
                    long back = 0, gen = 0;
                    try { String c = dao.selectConfigValue(MINE_ARMORER_KEY); if (c != null) { String[] p = c.split("\\|"); back = Long.parseLong(p[0].trim()); gen = Long.parseLong(p[1].trim()); } } catch (Exception e) { back = 0; }
                    mineArmorerGen = gen; mineArmorerBackAt = back;
                }
            }
        }
        // [2026-11-21] 은둔한 대장장이와 같이 사라지지 않는다 -- 자리는 맵 리셋마다 바뀐다.
        long gen = mineArmorerGen;
        java.util.Random r = new java.util.Random(gen * 6007L + 20261120L + S5MineMap.activeCycle() * 104729L);
        int mi = r.nextInt(mineArmorerMaps().length);
        S5MineMap map = S5MineMap.get(mineArmorerMaps()[mi]);
        if (map == null) return null;
        String key = gen + ":" + map.version();
        int[] spot = mineArmorerSpot;
        if (spot == null || !key.equals(mineArmorerSpotKey)) {
            List<S5MineMap.Mob> mobs = new ArrayList<>();
            for (S5MineMap.Mob mb : map.mobs()) if (!S5MineMobs.TYPES.get(mb.type).boss) mobs.add(mb);
            int x = map.spawnX, y = map.spawnY;
            for (int t = 0; t < 40 && !mobs.isEmpty(); t++) {
                S5MineMap.Mob mb = mobs.get(r.nextInt(mobs.size()));
                int nx = mb.x + r.nextInt(5) - 2, ny = mb.y + r.nextInt(5) - 2;
                if (map.walkable(nx, ny) && !map.safeAt(nx, ny)) { x = nx; y = ny; break; }
            }
            spot = new int[] { x, y };
            mineArmorerSpot = spot; mineArmorerSpotKey = key;
        }
        return new long[] { mi, spot[0], spot[1], now + 60_000L };
    }
    static String[][] mineArmorerCost(MineWeapon w, int g) {
        int shard = (g >= 3 ? 25 * (g - 2) : 5 * (g + 1)) * 2;
        return new String[][] { { S5MineMap.LIGHT_HEART_CODE, "1" }, { S5MineMap.THUNDER_HEART_CODE, "1" }, { S5MineMap.WIND_HEART_CODE, "1" }, { S5MineMap.WATER_HEART_CODE, "1" },
                { w.shard, String.valueOf(shard) }, { S5MineMap.DEMON_ORE_CODE, String.valueOf(100 * (g + 1)) } };
    }
    /** 떠돌이 갑옷장이 -- action=alist: 가진 갑옷과 재료 / ado: 등급업(mineHermit에서 넘어온다). */
    HashMap<String, Object> mineArmorer(String userName, String action, String code, HashMap<String, Object> row, HashMap<String, Object> out) {
        long now = System.currentTimeMillis();
        S5MineMap map = mineMapOf(row);
        long[] hm = mineArmorerNow(now);
        int[] pos = minePos(row, map, 0);
        if (hm == null || !map.id.equals(mineArmorerMaps()[(int) hm[0]]) || Math.abs(pos[0] - hm[1]) + Math.abs(pos[1] - hm[2]) > MINE_HERMIT_RANGE) { out.put("message", "...(아무도 없다)"); return out; }
        Map<String, Integer> items = mineItems(userName);
        Map<String, Integer> have = mineOreHave(userName);
        if ("ado".equals(action)) {
            MineWeapon w = mineArmorOf(code);
            int g = w == null ? -1 : mineWeaponTop(items, w);
            if (g < 0) { out.put("message", "...그 갑옷은 없군."); return out; }
            List<Integer> t = mineHermitTargets(w, g);
            if (t.isEmpty()) { out.put("message", "...더 두드릴 데가 없군."); return out; }
            String[][] cost = mineArmorerCost(w, g);
            if (!mineCostOk(cost, have)) { out.put("message", "...재료가 모자라. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")"); return out; }
            // [2026-11-21] 올려도 사라지지 않는다(예전: 6시간 사라지고 다른 자리로)
            mineTakeCost(userName, cost, 1);
            int r = RND.nextInt(100), step = r < 60 ? 0 : r < 90 ? 1 : 2;
            int ng = t.get(Math.min(step, t.size() - 1));
            int mask = (mineWeaponMask(items, w) & ~(1 << g)) | (1 << ng);
            dao.upsertMineItem(userName, w.code + "_M", mask);
            dao.upsertMineItem(userName, w.code, ng + 1);
            HashMap<String, Object> res = mineGearResult(userName, true, "🛡️ ...쾅. 쾅. " + w.name + "이(가) " + MINE_GRADE_NAMES[ng] + " 등급이 되었다.");
            res.put("ores", mineOreList(userName));
            res.put("grade", ng);
            mineGossipAdd("악마의 들판 어딘가에서 쇠 두드리는 소리가 그쳤대요. 누군가 갑옷을 새로 받아 갔다나요?");
            return res;
        }
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (MineWeapon w : MINE_ARMORS) {
            int g = mineWeaponTop(items, w);
            if (g < 0) continue;
            List<Integer> t = mineHermitTargets(w, g);
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", w.code); m.put("name", w.name); m.put("grade", g); m.put("gradeName", MINE_GRADE_NAMES[g]);
            if (!t.isEmpty()) {
                int mx = t.get(t.size() - 1);
                m.put("maxGrade", mx >= 6 ? 0 : mx);
                m.put("maxName", mx >= 6 ? "???" : MINE_GRADE_NAMES[mx]);
                List<HashMap<String, Object>> cl = new ArrayList<>();
                for (String[] c : mineArmorerCost(w, g)) { HashMap<String, Object> cm = new HashMap<>(); cm.put("name", mineCodeName(c[0])); cm.put("need", Integer.parseInt(c[1])); cm.put("have", have.getOrDefault(c[0], 0)); cl.add(cm); }
                m.put("cost", cl);
            }
            list.add(m);
        }
        out.put("ok", true);
        out.put("weapons", list);
        return out;
    }

    /** action=list: 가진 무기와 재료 / action=do: 등급업. [2026-11-20] alist/ado = 떠돌이 갑옷장이. */
    @Override
    @Transactional
    public HashMap<String, Object> mineHermit(String userName, String action, String code) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (row == null) return out;
        if ("alist".equals(action) || "ado".equals(action)) return mineArmorer(userName, action, code, row, out); // [2026-11-20] 떠돌이 갑옷장이
        long now = System.currentTimeMillis();
        S5MineMap map = mineMapOf(row);
        long[] hm = mineHermitNow(now);
        int[] pos = minePos(row, map, 0);
        if (hm == null || !map.id.equals(mineHermitMaps()[(int) hm[0]]) || Math.abs(pos[0] - hm[1]) + Math.abs(pos[1] - hm[2]) > MINE_HERMIT_RANGE) { out.put("message", "...(아무도 없다)"); return out; }
        Map<String, Integer> items = mineItems(userName);
        Map<String, Integer> have = mineOreHave(userName);
        if ("do".equals(action)) {
            MineWeapon w = null;
            for (MineWeapon c : MINE_WEAPONS) if (c.code.equals(code)) w = c;
            int g = w == null ? -1 : mineWeaponTop(items, w);
            if (g < 0) { out.put("message", "...그 무기는 없군."); return out; }
            List<Integer> t = mineHermitTargets(w, g);
            if (t.isEmpty()) { out.put("message", "...더 올릴 수 없군."); return out; }
            String[][] cost = mineHermitCost(w, g);
            if (!mineCostOk(cost, have)) { out.put("message", "...재료가 모자라. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")"); return out; }
            // [2026-11-21] "누군가 기능을 사용해도 남아 있도록" -- 올려도 사라지지 않는다(예전: 먼저 올린 한 사람 뒤 6시간 사라지고 다른 자리로)
            mineTakeCost(userName, cost, 1);
            int r = RND.nextInt(100), step = r < 60 ? 0 : r < 90 ? 1 : 2; // 1단계 60%, 2단계 30%, 3단계 10%
            int ng = t.get(Math.min(step, t.size() - 1));
            int mask = (mineWeaponMask(items, w) & ~(1 << g)) | (1 << ng); // 올리기 전 등급은 새 등급이 된다
            dao.upsertMineItem(userName, w.code + "_M", mask);
            dao.upsertMineItem(userName, w.code, ng + 1);
            HashMap<String, Object> res = mineGearResult(userName, true, "🔨 ...됐군. " + w.name + "이(가) " + MINE_GRADE_NAMES[ng] + " 등급이 되었다.");
            res.put("ores", mineOreList(userName));
            res.put("grade", ng);
            return res;
        }
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (MineWeapon w : MINE_WEAPONS) {
            int g = mineWeaponTop(items, w);
            if (g < 0) continue;
            List<Integer> t = mineHermitTargets(w, g);
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", w.code);
            m.put("name", w.name);
            m.put("grade", g);
            m.put("gradeName", MINE_GRADE_NAMES[g]);
            if (!t.isEmpty()) {
                int mx = t.get(t.size() - 1);
                m.put("maxGrade", mx >= 6 ? 0 : mx); // [2026-10-30] 고대·신화는 색도 숨긴다
                m.put("maxName", mx >= 6 ? "???" : MINE_GRADE_NAMES[mx]); // "최대 등급이 고대·신화면 ???"
                List<HashMap<String, Object>> cl = new ArrayList<>();
                for (String[] c : mineHermitCost(w, g)) {
                    HashMap<String, Object> cm = new HashMap<>();
                    cm.put("name", mineCodeName(c[0]));
                    cm.put("need", Integer.parseInt(c[1]));
                    cm.put("have", have.getOrDefault(c[0], 0));
                    cl.add(cm);
                }
                m.put("cost", cl);
            }
            list.add(m);
        }
        out.put("ok", true);
        out.put("weapons", list);
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
        // [2026-11-17] "마을로 가기 기능을 언제나 사용할 수 있게" -- 빙결/전투 중/쓰러져 있어도 막지 않는다(싸움·상태이상은 mineRespawn과 같이 정리)
        HashMap<String, Object> vr = dao.selectMineUser(userName);
        // [2026-10-28] "개미굴에선 마을 이동 안 됨"(나가는 굴을 찾아야) -> [2026-11-16] "개미굴에서 마을로 이동 버튼 가능하게" -- 막지 않는다.
        // [2026-10-25] "부활 거점을 골랐는데 마을 이동을 누르면 거점으로 안 간다" -- 성당에서 기도했으면 왕도 성당으로(mineRespawn과 같은 곳)
        mineDisengageAll(userName); mineSoloReset(userName); MINE_TW_RUN.remove(userName); MINE_CHAOS_RUN.remove(userName);
        MINE_ICE_FROZEN.remove(userName); MINE_DEBUFF.remove(userName); MINE_STUN.remove(userName); MINE_ICE_THAWED.remove(userName);
        String where = mineGoHome(userName, mineItems(userName), now);
        out.put("ok", true);
        out.put("message", "🏠 " + where + " 다시 시작해요.");
        out.put("state", mineState(userName));
        return out;
    }

    static final String MINE_FIGHT_BLOCK = "⚔️ 몬스터가 쫓아오고 있어요! 5칸 넘게 떨어져 인식을 풀고 나서 이동할 수 있어요. (보스는 포탈로 맵을 벗어나야 풀려요)";

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
        int[] pos = minePos(row, map, mineWalkMode(items));
        if (!mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        S5MineMap.Npc g = map.guide();
        if (g == null || Math.abs(g.x - x) + Math.abs(g.y - y) > 2) { out.put("message", "이동 안내원 바로 앞에서 말을 걸어 주세요."); return out; }
        if (dest == null || dest.equals(map.id) || !S5MineMap.GUIDE_MAPS.contains(dest)) { out.put("message", "🧭 어디로 갈지 골라 주세요."); return out; }
        if (S5MineMap.TOWER_CITY.equals(dest) && (!mineTowerOk(userName) || (items.getOrDefault(MINE_ITEM_VISIT_TCITY, 0) < 1 && !isMineAdmin(userName)))) { out.put("message", "🧭 요새 도시는 마물의 숲 나루터에서 배를 타고 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-11-16]
        if (S5MineMap.CITY.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) < 1 && !isMineAdmin(userName)) { out.put("message", "🧭 왕도는 왕도 남쪽 초원이나 왕도 북쪽 샛길을 지나 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-10-27]
        if (S5MineMap.ARCHI.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_ARCHI, 0) < 1 && !isMineAdmin(userName)) { out.put("message", "🧭 다도해 마을은 배를 타고 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-10-28]
        if (S5MineMap.SNOWROAD.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) < 1 && !isMineAdmin(userName)) { out.put("message", "🧭 설산 베이스캠프는 설산 마을에 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-11-04]
        if (S5MineMap.SNOWTOWN.equals(dest) && items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) < 1) { out.put("message", "🧭 설산 마을은 설산 1, 2, 3층을 차례로 오르거나 왕도 북쪽 샛길을 지나 한 번 다녀오셔야 모셔다 드릴 수 있어요."); return out; } // [2026-10-26]
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
    static final String MINE_ITEM_SNOW_PATH = "SNOW_PATH"; // [2026-10-28] 설산을 아래층부터 순서대로 올라온 층(1~3)
    static final String MINE_ITEM_VISIT_ARCHI = "VISIT_ARCHI"; // [2026-10-28] 다도해 마을(본섬)에 한 번 도착(이동 안내원 목적지)
    static final String MINE_ITEM_VISIT_TCITY = "VISIT_TCITY"; // [2026-11-16] 탑의 도시에 한 번 도착(이동 안내원 목적지) -- "한 번 이상 왕래가 있었다면 마을 간 이동(NPC)이 가능하게"
    // [2026-11-18] 워프 포인트 -- 켜짐 = WP{액트} 비트. 이동 안내원으로 이미 갈 수 있던 마을(평원 마을, 한 번 가 본 왕도/설산 마을/다도해/요새 도시)은 처음부터 켜진 것으로.
    static String mineWpKey(int act) { return "WP" + act; }
    boolean mineWpOn(String userName, Map<String, Integer> items, S5MineMap.Waypoint w) {
        if (w == null) return false;
        if (isMineAdmin(userName) || ((items.getOrDefault(mineWpKey(w.act), 0) >> w.bit) & 1) == 1) return true;
        switch (w.id) {
            case "field": return true;
            case "city": return items.getOrDefault(MINE_ITEM_VISIT_CITY, 0) > 0;
            case "snowtown": case "snowroad": return items.getOrDefault(MINE_ITEM_VISIT_SNOWTOWN, 0) > 0;
            case "archi": return items.getOrDefault(MINE_ITEM_VISIT_ARCHI, 0) > 0;
            case "tcity": return items.getOrDefault(MINE_ITEM_VISIT_TCITY, 0) > 0;
            default: return false;
        }
    }
    /** [2026-11-18] 워프 포인트 창 -- 바로 앞(x, y)의 워프 포인트(px, py)를 켜고(처음이면 newly), 액트별 목록 {n, name, list:[{id, name, on, here}]}. */
    @Override
    public HashMap<String, Object> mineWaypoint(String userName, int x, int y, int px, int py) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int[] pos = minePos(row, map, mineWalkMode(items));
        S5MineMap.Waypoint here = S5MineMap.waypointAt(map.id, px, py);
        if (here == null || Math.abs(x - px) + Math.abs(y - py) != 1 || Math.abs(pos[0] - x) + Math.abs(pos[1] - y) > 10) { out.put("message", "워프 포인트 바로 앞에서 만져 주세요."); return out; }
        if (!mineWpOn(userName, items, here)) {
            int v = items.getOrDefault(mineWpKey(here.act), 0) | (1 << here.bit);
            dao.upsertMineItem(userName, mineWpKey(here.act), v);
            items.put(mineWpKey(here.act), v);
            out.put("newly", here.name);
        }
        List<HashMap<String, Object>> acts = new ArrayList<>();
        for (int a = 1; a < S5MineMap.WP_ACT_NAMES.length; a++) {
            // [2026-11-18] 5장 바다(윤슬항)도 모두에게(예전: 관리자만)
            HashMap<String, Object> am = new HashMap<>();
            List<HashMap<String, Object>> l = new ArrayList<>();
            int on = 0;
            for (S5MineMap.Waypoint w : S5MineMap.waypoints()) {
                if (w.act != a) continue;
                HashMap<String, Object> wm = new HashMap<>();
                boolean o = mineWpOn(userName, items, w);
                wm.put("id", w.id); wm.put("name", w.name); wm.put("on", o); wm.put("here", w == here);
                if (mineWpTown(w)) wm.put("inf", true); else wm.put("pct", mineWpPct(w)); // [2026-11-18] 내구도 -- [2026-11-20] %만
                if (o) on++;
                l.add(wm);
            }
            am.put("n", a); am.put("name", S5MineMap.WP_ACT_NAMES[a]); am.put("list", l); am.put("on", on);
            acts.add(am);
        }
        out.put("ok", true);
        if (here != null && !mineWpTown(here)) out.put("herePct", mineWpPct(here)); // [2026-11-20] %만
        out.put("acts", acts);
        out.put("here", here.id);
        out.put("act", here.act);
        return out;
    }
    static final String MINE_ITEM_VISIT_CITY = "VISIT_CITY"; // [2026-10-27] 왕도에 한 번 걸어서 도착(이동 안내원 목적지)
    static final String MINE_ITEM_COAT1 = "COAT1";
    static final String MINE_ITEM_COAT2 = "COAT2";
    static final String MINE_ITEM_COAT_SLOT = "COAT_SLOT"; // 0 = 안 입음, 1 = 방한 외투, 2 = 두꺼운 방한 외투
    static final int MINE_BLIZZARD_PCT = 10;
    static final long MINE_COLD_MAX_GAP_MS = 3_000L;
    static final int MINE_BLACK_OPEN_MIN = 15; // 매시 정각 ~ 15분
    /** 설산 마을 값(암상인은 MINE_BLACK_PCT%). */
    static final String[][] MINE_COAT1_COST = { { S5MineMap.ORE_CODES[4], "20" }, { S5MineMap.ORE_CODES[3], "80" }, mineCost(8, 3) };
    static final String[][] MINE_COAT2_COST = { { S5MineMap.ORE_CODES[4], "50" }, { S5MineMap.SEVEN_STONE_CODE, "3" }, mineCost(8, 8) };

    static final class ColdState {
        volatile String mapVer;
        volatile long at;
        volatile double frac; // 1 미만으로 남은 한기 피해(다음 부름에 더한다)
    }
    static final java.util.concurrent.ConcurrentHashMap<String, ColdState> MINE_COLD = new java.util.concurrent.ConcurrentHashMap<>();

    /** 입고 있는 방한 외투의 한기 방어 단계(0 = 안 입음). 입으면 몬스터에게 받는 피해 2배. */
    // [2026-10-28] "방한 외투/장갑을 전부 퀘스트 형태로 순차 획득, 이전 것은 못 쓰는 업그레이드 형태로, 세공된 빙석 10개 + 다도해 세공 보석으로 3단계" --
    //  외투 하나(COAT_TIER 1~3: 방한 외투 -> 두꺼운 방한 외투 -> 방한 갑옷), 장갑 하나(GLOVES 1~3: 방한 장갑 -> 두꺼운 방한 장갑 -> 방한 건틀릿).
    //  설산 마을 털옷 장인 의뢰로 한 단계씩(재료를 건네면 바로 다음 단계로 바뀌고 입음). 예전 COAT1/COAT2 보유는 1/2단계로 친다. 암상인은 더 안 판다.
    //  한기 방어 = 외투 단계(입었을 때) + 장갑 단계(꼈을 때). 외투를 입으면 받는 피해 2배(3단계 갑옷은 1.5배).
    static final String MINE_ITEM_COAT_TIER = "COAT_TIER";
    static final String[] MINE_COAT_NAMES = { "방한 외투", "두꺼운 방한 외투", "방한 갑옷" };
    static final String[] MINE_GLOVE_NAMES = { "방한 장갑", "두꺼운 방한 장갑", "방한 건틀릿" };

    static int mineCoatTier(Map<String, Integer> items) {
        int t = items.getOrDefault(MINE_ITEM_COAT_TIER, 0);
        if (items.getOrDefault(MINE_ITEM_COAT2, 0) > 0) t = Math.max(t, 2);
        if (items.getOrDefault(MINE_ITEM_COAT1, 0) > 0) t = Math.max(t, 1);
        return Math.min(3, t);
    }

    static int mineGloveTier(Map<String, Integer> items) {
        return Math.max(0, Math.min(3, items.getOrDefault(MINE_ITEM_GLOVES, 0)));
    }

    static String[][] mineCoatStepCost(int tier) {
        return tier == 1 ? MINE_COAT1_COST : tier == 2 ? MINE_COAT2_COST
                : new String[][] { { S5MineMap.CUT_ICE_CODE, "10" }, { S5MineMap.seaCutCode(0), "1" }, { S5MineMap.seaCutCode(4), "1" } }; // 세공 빙석 10 + 세공 진주 + 세공 흑진주
    }

    static String[][] mineGloveStepCost(int tier) {
        return tier == 1 ? new String[][] { { S5MineMap.ICE_STONE_CODE, String.valueOf(MINE_GLOVES_ICE) } }
                : tier == 2 ? new String[][] { { S5MineMap.ICE_STONE_CODE, "30" }, { S5MineMap.ORE_CODES[4], "20" } }
                : new String[][] { { S5MineMap.CUT_ICE_CODE, "10" }, { S5MineMap.seaCutCode(1), "1" }, { S5MineMap.seaCutCode(2), "1" } }; // 세공 빙석 10 + 세공 산호석 + 세공 자수정
    }

    static String mineCoatEffect(int tier) { return "한기 방어 " + tier + " · 받는 피해 " + (tier >= 3 ? "1.5배" : "2배"); }
    static String mineGloveEffect(int tier) { return "한기 방어 +" + tier; }

    static int mineCoatDef(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_COAT_SLOT, 0) > 0 && mineArmorOn(items) == null ? mineCoatTier(items) : 0;
    }

    // [2026-11-22] "갑옷에 속성 부여 -- 같은 속성은 안 맞게, 반대 속성은 1.5배. 속성은 주는 데미지와 같은 표로. 몬스터는 속성 공격 / 무속성 공격 / 둘 다"(사용자 선택: 무기처럼 NPC 부여, 기술별로 나눔).
    //  왕도 원소술사(물/번개/불/땅, 세공 보석 1개, 500번 맞는 동안) / 성당 신부(빛, 미스릴괴 1개, 1000번) -- 갑옷을 입고 있을 때만, 새로 부여하면 이전 속성은 사라진다.
    //  몬스터 공격: 일반 근접(반격, 전투 턴 근접)은 무속성, 특수기(원거리/돌진/찌르기/내려찍기/장판/낙뢰/돌풍/보스 패턴)는 그 몬스터의 속성.
    //  공격 속성 == 갑옷 속성 -> 0, elemBeats(공격 속성, 갑옷 속성) -> x1.5(무기 상성과 같은 표: 어둠<>빛, 물·얼음<>불, 땅·바람<>번개).
    //  남은 횟수는 맞을 때마다 메모리에서 세고(MINE_ARMOR_EL_OF[2]) 1초 위치 알림(minePresence) 때 DB에 쓴다. 무효/상성 횟수도 그때 화면에(ael).
    static final String MINE_ITEM_AEL = "AEL", MINE_ITEM_AEL_LEFT = "AEL_LEFT";
    /** 유저 -> {갑옷 속성, DB 남은 횟수, 아직 안 쓴 횟수, 무효 횟수, 상성 횟수, 갑옷 입음 1/0}. */
    static final java.util.concurrent.ConcurrentHashMap<String, int[]> MINE_ARMOR_EL_OF = new java.util.concurrent.ConcurrentHashMap<>();
    static int mineElHit(String u, int v, int el) { return mineElHit(u, v, el, true); }
    // [2026-11-23] "얼마 전에 진행했던 갑옷 속성 부여는 없애줘" -- 효과를 끈다(받는 피해는 속성과 상관없이 그대로). 부르는 곳(mineCoatHit 등)은 그대로 둔다.
    static final boolean MINE_AEL_ON = false;
    static int mineElHit(String u, int v, int el, boolean count) {
        if (!MINE_AEL_ON) return v;
        int[] a = u == null ? null : MINE_ARMOR_EL_OF.get(u);
        if (a == null || v <= 0) return v;
        synchronized (a) {
            if (a[0] <= 0 || a[5] == 0 || a[1] - a[2] <= 0) return v;
            if (count) a[2]++;
            if (el > 0 && el == a[0]) { if (count) a[3]++; return 0; }
            if (el > 0 && S5MineMobs.elemBeats(el, a[0])) { if (count) a[4]++; return (int) Math.min(Integer.MAX_VALUE / 4, Math.round(v * 1.5)); }
        }
        return v;
    }
    static void mineArmorElSet(String u, int el, int left) { int[] a = MINE_ARMOR_EL_OF.computeIfAbsent(u, k -> new int[6]); synchronized (a) { a[0] = el; a[1] = left; a[2] = 0; } }
    /** 갑옷 속성 e가 1.5배로 아픈 공격 속성 이름들(땅·바람 등). */
    static String mineAelWeak(int e) { StringBuilder b = new StringBuilder(); for (int k = 1; k <= 8; k++) if (S5MineMobs.elemBeats(k, e)) b.append(b.length() > 0 ? "·" : "").append(S5MineMobs.ELEM_NAMES[k]); return b.toString(); }
    /** el = 몬스터 공격 속성(0 = 무속성). */
    static int mineCoatHit(Map<String, Integer> items, int v, int el) {
        int v2 = mineElHit(MINE_CTX.get(), v, el);
        if (v2 == 0 && v > 0) return 0; // 같은 속성 -- 막았다
        return mineCoatHit(items, v2);
    }
    /** 외투를 입었을 때 받는 피해(2배, 3단계 갑옷은 1.5배). */
    static int mineCoatHit(Map<String, Integer> items, int v) {
        v = (int) Math.max(1, Math.round(v / mineUnityHp(MINE_CTX.get()))); // [2026-11-17] 우리는 하나 -- 체력 배수만큼 덜 아프다
        int d = mineCoatDef(items);
        int r = d <= 0 ? v : (int) Math.round(v * (d >= 3 ? 1.5 : 2.0));
        int ad = r > 0 ? mineArmorDef(items) : 0; // [2026-11-06] 갑옷 -- 받는 피해 고정 감소(최소 1)
        return ad > 0 ? Math.max(1, r - ad) : r;
    }

    // [2026-10-28] "설산에서 빙석이 발견되도록, 빙석을 모아 오면 방한 장갑을 주는 퀘스트" -- 털옷 장인 의뢰(GLOVES_QUEST: 1 = 받음, 2 = 끝냄).
    //  빙석 MINE_GLOVES_ICE개를 내면 방한 장갑(GLOVES): 한기 방어 +1(외투와 합쳐짐), 받는 피해는 그대로(외투처럼 2배가 아님).
    static final String MINE_ITEM_GLOVES = "GLOVES";
    static final String MINE_ITEM_GLOVES_QUEST = "GLOVES_QUEST";
    static final int MINE_GLOVES_ICE = 10; // [2026-10-28] 빙석이 귀해져서 30 -> 10

    /** [2026-10-28] "방한 장갑을 낀 채로 사막이나 남쪽 섬은 뜨거워서 햇빛 피해(초당 1%, 2개 끼면 2%)" -- 더운 맵. */
    static boolean mineHotMap(S5MineMap map) {
        return S5MineMap.DESERT.equals(map.id) || S5MineMap.ISLAND.equals(map.id) || map.sun() > 0; // [2026-10-28] 다도해(햇빛)도
    }

    /** 더운 맵에서 초당 깎이는 최대 체력 %: 끼고 있는 방한 장비(외투, 장갑) 하나에 1%. */
    static int mineHeatPct(S5MineMap map, Map<String, Integer> items, long now) {
        return mineSunPct(map, items, now) + mineGearHeatPct(items);
    }

    /** [2026-10-28] 다도해 낮 햇빛(지역별 초당 %), 밤엔 없음. */
    static int mineSunPct(S5MineMap map, long now) {
        return map.sun() > 0 && S5MineMap.archiDay(now) ? map.sun() : 0;
    }

    /** [2026-10-28] "양산을 장착하면 햇빛 피해 레벨 -2" -- 양산을 들고 있으면 햇빛 단계에서 MINE_PARASOL_CUT을 뺀다. */
    static int mineSunPct(S5MineMap map, Map<String, Integer> items, long now) {
        int s = mineSunPct(map, now);
        // [2026-10-29] "환생으로 햇빛 저항 +1씩" -- 양산(-2단계)처럼 환생 회차만큼 단계를 뺀다(예전: 회차당 20% 경감)
        return s > 0 ? Math.max(0, s - (mineParasolOn(items) ? MINE_PARASOL_CUT : 0) - mineRebirth(items) - mineArmorRes(items)) : s; // [2026-11-06] 유물 이상 갑옷
    }

    // ---- [2026-10-28] 무기 칸 -- "무기 아이템(기본은 칼) 장착을 보여주고, 양산을 다도해 상점에서" -- WEAPON_SLOT 0 = 칼(기본), 1 = 양산(PARASOL)
    static final String MINE_ITEM_PARASOL = "PARASOL", MINE_ITEM_WEAPON_SLOT = "WEAPON_SLOT";
    // ---- [2026-10-29] 레벨대 무기(몬스터 드랍) + 장비 등급(일반~신화, 화면엔 색으로만)
    //  "20/40/60/80레벨 무기": 비철도(20, 공 50), 카타나(40, 공 250) -- 영웅까지, 등급마다 +20% / 닌자의 검(60, 공 500, 등급마다 +40%, 영웅부터 공격력 +1%씩)
    //  / 백화검(80, 공 1500 + 공격력 1%, 등급마다 +20%와 +1%). 그 레벨 ±4 몬스터(닌자의 검은 닌자도)가 0.5%로 떨어뜨린다.
    //  아이템 값 = 가진 가장 좋은 등급 + 1(0 = 없음), WEAPON_SLOT = slot. 같은 등급 이하가 또 나오면 그 무기 조각(등급 + 1개)으로 자동 분해.
    static final class MineWeapon {
        final String code, name, shard; final int slot, lv, atk, maxGrade, pctFrom, pct0; final double step;
        final boolean armor; // [2026-11-06] 갑옷(atk = 받는 피해 고정 감소)
        MineWeapon(String code, String name, int slot, int lv, int atk, double step, int maxGrade, int pctFrom, int pct0, String shard) {
            this(code, name, slot, lv, atk, step, maxGrade, pctFrom, pct0, shard, false);
        }
        MineWeapon(String code, String name, int slot, int lv, int atk, double step, int maxGrade, int pctFrom, int pct0, String shard, boolean armor) {
            this.code = code; this.name = name; this.slot = slot; this.lv = lv; this.atk = atk; this.step = step; this.maxGrade = maxGrade; this.pctFrom = pctFrom; this.pct0 = pct0; this.shard = shard; this.armor = armor;
        }
        // [2026-11-06] 갑옷은 "이전 레벨 신화가 다음 레벨 전설보다 좋게" -- 등급마다 x1.3(곱, 신화 = x6.27 / 전설 = x2.86). 무기는 그대로(합, 등급마다 +step).
        int atkAt(int g) { return (int) Math.round(armor ? atk / Math.pow(MINE_ARMOR_GRADE_MUL, 7 - g) : atk * (1 + step * g)); } // [2026-11-18] 갑옷 atk = 신화 값
        /** 공격력 %(합연산) -- pctFrom 등급부터 pct0, 등급마다 +1(pctFrom < 0 = 없음). */
        int pctAt(int g) { return pctFrom < 0 || g < pctFrom ? 0 : pct0 + (g - pctFrom); }
        String effect(int g) { return effect(g, 0); }
        // [2026-11-06] 강화 -- 1강마다 기본 성능(공격력/받는 피해 감소) +10%, 5강마다 전설 이상의 추가 옵션(무기 공격력 %, 갑옷 초당 회복)만큼 더.
        int atkEnh(int g, int e) { return (int) Math.round(atkAt(g) * MINE_ENH_BASE_PCT / 100.0 * e); }
        // [2026-11-06] "5강에 8%+1%, 10강에 8%+2%" -- 전설 이상(공격력 % 옵션이 있는 무기)은 5강마다 공격력 % +1(예전: 옵션 값만큼)
        int pctEnh(int g, int e) { return g >= MINE_ENH_OPT_GRADE && pctAt(g) > 0 ? e / MINE_ENH_OPT_EVERY : 0; }
        /** 갑옷 초당 체력 회복 %(전설부터 0.5) / 강화 몫. */
        double regenAt(int g) { return armor && g >= MINE_ENH_OPT_GRADE ? MINE_ARMOR_REGEN : 0; }
        double regenEnh(int g, int e) { return regenAt(g) * (e / MINE_ENH_OPT_EVERY); }
        /** 갑옷 한기·햇빛 방어 단계 -- [2026-11-06] "전설~고대 1단, 신화 2단"(예전 유물 1 / 고대 2 / 신화 3). */
        int resAt(int g) { return !armor ? 0 : g >= 7 ? 2 : g >= 4 ? 1 : 0; }
        /** "강화로 인한 건지 등급으로 인한 건지 따로 표기" -- 합계 (등급 a + 강화 b). */
        String effect(int g, int e) {
            int b = atkAt(g), be = atkEnh(g, e);
            String s = (armor ? "받는 피해 -" : "공격력 +") + (b + be) + (be > 0 ? " (등급 " + b + " + 강화 " + be + ")" : "");
            if (armor) {
                double r = regenAt(g), re = regenEnh(g, e);
                if (r > 0) s += " · 초당 체력 회복 " + mineFmtPct(r + re) + "%" + (re > 0 ? " (등급 " + mineFmtPct(r) + "% + 강화 " + mineFmtPct(re) + "%)" : "");
                if (resAt(g) > 0) s += " · 한기·햇빛 방어 +" + resAt(g);
                int hb = mineArmorHpAt(code, g), he = mineArmorHpEnh(code, g, e); // [2026-11-18] 강화 몫
                if (hb > 0) s += " · 최대 체력 +" + String.format("%,d", hb + he) + (he > 0 ? " (등급 " + String.format("%,d", hb) + " + 강화 " + String.format("%,d", he) + ")" : ""); // [2026-11-17]
            } else {
                int p = pctAt(g), pe = pctEnh(g, e);
                if (p > 0) s += " · 공격력 +" + (p + pe) + "%" + (pe > 0 ? " (등급 " + p + "% + 강화 " + pe + "%)" : "");
            }
            if (MINE_ITEM_WYRM_TOOTH.equals(code)) s += " · 용병 공격력 +5%"; // [2026-11-23] 월드보스 장비 옵션
            if (MINE_ITEM_BH_TREMOR.equals(code)) s += " · 용병 공격력 +10%";
            if (MINE_ITEM_WYRM_SKIN.equals(code)) s += " · 받는 피해 -20% · 즉사 방지(체력이 남았는데 한 번에 쓰러질 피해면 체력 1로 버팀, 1분에 한 번)";
            return s;
        }
    }
    static String mineFmtPct(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0); }
    // ---- [2026-11-06] 갑옷(몬스터 드랍) -- "갑옷류 장비를 무기처럼 드랍/등급. 받는 피해 감소(고정 수치), 전설 이상은 초당 체력 회복 0.5%,
    //  20/40/60/80 장비, 무기와 달리 20/40도 신화까지, 유물부터 한기·햇빛 방어(동굴 시야는 x)". 갑옷 칸은 하나(ARMOR_SLOT = slot).
    //  [2026-11-06] "이전 레벨 신화 > 다음 레벨 전설, 고레벨 피해 감소는 줄이기" -- 기본 5/25/120/500(등급마다 +20%) -> 8/16/34/72(등급마다 x1.3):
    //  일반/전설/신화 = 20: 8/23/50, 40: 16/46/100, 60: 34/97/213, 80: 72/206/452.
    //  드랍은 무기와 같은 몬스터/확률로 따로 굴린다(무기가 나오지 않았을 때만 -- 한 번에 하나). 같은 등급이 또 나오면 그 갑옷 조각으로.
    static final String MINE_ITEM_ARMOR_SLOT = "ARMOR_SLOT";
    static final double MINE_ARMOR_REGEN = 0.5, MINE_ARMOR_GRADE_MUL = 1.3;
    //  [2026-11-18] "갑옷류 피해 감소량 대폭, 신화 백화 갑주는 2500" -- 값을 신화 기준으로 적고(아래 등급은 1.3씩 나눔), 신화 기준 50/100/213/452/941 -> 150/300/800/2500/5000.
    static final MineWeapon[] MINE_ARMORS = {
        new MineWeapon("ARMOR_CHAIN", "사슬 갑옷", 1, 20, 150, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[0], true),
        new MineWeapon("ARMOR_SAMURAI", "무사의 흉갑", 2, 40, 300, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[1], true),
        new MineWeapon("ARMOR_NINJA", "닌자의 갑주", 3, 60, 800, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[2], true),
        new MineWeapon("ARMOR_BAEKHWA", "백화 갑주", 4, 80, 2500, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[3], true),
        new MineWeapon("ARMOR_IMMORTAL", "심판의 갑옷", 5, 125, 5000, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[3], true),
        new MineWeapon("ARMOR_WYRM_SKIN", "거룡의 피부조각", 7, 125, 5000, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[3], true), // [2026-11-23] 거룡 처치 1% -- 심판의 갑옷 신화와 같은 감소/체력 + 받는 피해 -20% + 즉사 방지(신화 단일)
        new MineWeapon("ARMOR_SEA", "바다의 갑옷", 6, 110, 3600, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[3], true), // [2026-11-18] 바다 위 몬스터/크라켄 전용(신화 받는 피해 -3600) // [2026-11-17] 보로네오 전용(받는 피해 고정 감소 -- 백화 갑주의 2배)
    };
    static MineWeapon mineArmorOf(String code) { for (MineWeapon w : MINE_ARMORS) if (w.code.equals(code)) return w; return null; }
    static MineWeapon mineArmorOn(Map<String, Integer> items) {
        int s = items.getOrDefault(MINE_ITEM_ARMOR_SLOT, 0);
        if (s <= 0) return null;
        for (MineWeapon w : MINE_ARMORS) if (w.slot == s && mineWeaponGrade(items, w) >= 0) return w;
        return null;
    }
    static List<MineWeapon> mineArmorDrops(S5MineMobs.Type t) {
        List<MineWeapon> out = new ArrayList<>();
        if (t.dummy || t.clone || t.noReward) return out;
        java.util.List<String> ms = java.util.Arrays.asList(t.maps);
        if (t.ninja || ms.contains(S5MineMap.ARCHI)) out.add(mineArmorOf("ARMOR_NINJA"));
        if (t.sea) { out.add(mineArmorOf(MINE_ITEM_SEA_ARMOR)); return out; } // [2026-11-18] 바다 몬스터 = 바다의 갑옷만
        if (ms.contains(S5MineMap.ISLE1) || ms.contains(S5MineMap.ISLE2) || ms.contains(S5MineMap.ISLE3) || ms.contains(S5MineMap.ISLE4) || ms.contains(S5MineMap.ISLE5) || ms.contains(S5MineMap.ISLE6) || ms.contains(S5MineMap.ISLE7)) out.add(mineArmorOf("ARMOR_BAEKHWA"));
        if (!t.ninja) for (MineWeapon w : MINE_ARMORS) if (Math.abs(t.lv() - w.lv) <= MINE_WEAPON_DROP_LV_GAP && !out.contains(w) && !mineNoRandomDrop(w.code)) out.add(w); // [2026-11-15] 탑의 갑주는 상인만
        return out;
    }
    static int mineArmorDef(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); if (a == null) return 0; int g = mineWeaponGrade(items, a), e = mineArmorEnh(items); return a.atkAt(g) + a.atkEnh(g, e); }
    static double mineArmorRegen(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); if (a == null) return 0; int g = mineWeaponGrade(items, a); return a.regenAt(g) + a.regenEnh(g, mineArmorEnh(items)); }
    static int mineArmorRes(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); return a == null ? 0 : a.resAt(mineWeaponGrade(items, a)); }
    // ---- [2026-11-06] 강화 -- "미스릴 괴를 포함한 여러 재료 소비, 1강부터 이론상 20강, 2강부터 확률 5%씩 감소, 실패해도 올려 주는 확률은 없음".
    //  무기/갑옷마다(등급과 상관없이 그 장비에) ENH_코드 = 단계. n강 확률 = 100 - 5(n-1)% (1강 100% ~ 20강 5%), 실패하면 재료만 사라지고 단계는 그대로.
    static final String MINE_ENH_PREFIX = "ENH_";
    static final int MINE_ENH_MAX = 20, MINE_ENH_BASE_PCT = 10, MINE_ENH_OPT_EVERY = 5, MINE_ENH_OPT_GRADE = 4;
    static int mineEnh(Map<String, Integer> items, String code) { return Math.max(0, Math.min(MINE_ENH_MAX, items.getOrDefault(MINE_ENH_PREFIX + code, 0))); }
    // [2026-11-06] "강화는 슬롯에 강화되는 형태로" -- 무기 칸(ENH_WEAPON)에 단계가 쌓이고, 칸에 든 드랍 무기에 그대로 적용(무기를 바꿔 들어도 유지). 갑옷 칸은 추후.
    static final String MINE_ITEM_WEAPON_ENH = MINE_ENH_PREFIX + "WEAPON";
    static int mineWeaponEnh(Map<String, Integer> items) { return Math.max(0, Math.min(MINE_ENH_MAX, items.getOrDefault(MINE_ITEM_WEAPON_ENH, 0))); }
    // [2026-11-18] "방어구 강화 신설 -- 체력, 받는 피해 강화 형태로" -- 갑옷 칸(ENH_ARMOR)에 단계가 쌓이고 어떤 갑옷을 입어도 그대로. 1강마다 받는 피해 감소 +10%,
    //  갑옷 최대 체력 +10%(그 갑옷 등급 값 기준), 5강마다 전설 이상의 초당 회복만큼 더. 재료/확률은 무기 칸과 같고 조각은 입은 갑옷의 조각. 요새 도시 방어구 강화사.
    static final String MINE_ITEM_ARMOR_ENH = MINE_ENH_PREFIX + "ARMOR";
    static int mineArmorEnh(Map<String, Integer> items) { return Math.max(0, Math.min(MINE_ENH_MAX, items.getOrDefault(MINE_ITEM_ARMOR_ENH, 0))); }
    // [2026-11-19] "갑옷 강화 시 체력 %를 기존 대비 20% 낮춰 줘" -- 1강마다 갑옷 최대 체력 +10% -> +8%(받는 피해 감소 +10%는 그대로)
    static final int MINE_ENH_ARMOR_HP_PCT = 8;
    static int mineArmorHpEnh(String code, int g, int e) { return (int) Math.round(mineArmorHpAt(code, g) * MINE_ENH_ARMOR_HP_PCT / 100.0 * e); }
    static int mineEnhPct(int n) { return Math.max(5, 100 - 5 * (n - 1)); }
    static final int MINE_ENH_SHARDS = 10;
    /** n강 재료: 미스릴 괴 n + 금괴 n + 그 장비 조각 ceil(n/2), 6강부터 별빛석 n-5, 11~15강 달빛석 n-10,
     *  [2026-11-06] "16강부터는 달빛석 + 칠요석을 합친 은하수 조각" -- 16강부터 달빛석 대신 은하수 조각 n-10(달빛석 개수를 이어서, 칠요석은 따로 안 듦). */
    /** [2026-11-19] "방어구 강화 비용은 악마의 광석 조각 + 미스릴 괴로" -- n강: 미스릴 괴 n + 악마의 광석 조각 MINE_AENH_DEMON x n(갑옷 조각/금괴/별빛석/달빛석/은하수 조각은 안 든다). */
    static final int MINE_AENH_DEMON = 100; // [2026-11-19] 같은 날 "악마의 광석 조각 개수 20배로" -- 5 -> 100
    static String[][] mineArmorEnhCost(int n) {
        return new String[][] { mineBar(4, n), { S5MineMap.DEMON_ORE_CODE, String.valueOf(MINE_AENH_DEMON * n) } };
    }
    static String[][] mineEnhCost(MineWeapon w, int n) {
        List<String[]> l = new ArrayList<>();
        l.add(mineBar(4, n));
        l.add(mineBar(3, n));
        l.add(new String[] { w.shard, String.valueOf(MINE_ENH_SHARDS) }); // [2026-11-06] "강화할 때 조각 10개씩" -- 예전 ceil(n/2)
        if (n >= 6) l.add(mineStar(n - 5));
        if (n >= 11 && n < 16) l.add(new String[] { S5MineMap.MOON_STONE_CODE, String.valueOf(n - 10) });
        if (n >= 16) l.add(new String[] { S5MineMap.GALAXY_CODE, String.valueOf(n - 10) });
        return l.toArray(new String[0][]);
    }
    @Override
    @Transactional
    public HashMap<String, Object> mineEnhance(String userName, String code) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName); // 같은 유저의 동시 요청은 여기서 줄 선다
        Map<String, Integer> items = mineItems(userName);
        if ("armor".equals(code)) return mineArmorEnhance(userName, items); // [2026-11-18] 방어구 강화(요새 도시)
        if (!"weapon".equals(code)) return mineGearResult(userName, false, "그런 칸은 강화할 수 없어요."); // [2026-11-06] "강화는 무기만, 방어구는 추후 다른 대륙에서" -- 무기 칸만
        HashMap<String, Object> erow = dao.selectMineUser(userName); // [2026-11-06] "무기 강화 전용 NPC" -- 왕도 5시 방향 무기 강화사 앞에서만
        if (erow == null || !S5MineMap.CITY.equals(strVal(erow.get("MAP_ID"), "")) || !mineAtNpc(erow, "enhancer")) return mineGearResult(userName, false, "왕도 5시 방향 무기 강화사 앞에서만 강화할 수 있어요.");
        MineWeapon w = mineWeaponOn(items); // [2026-11-06] 칸 강화 -- 칸에 든 무기의 조각을 쓴다
        if (w == null) return mineGearResult(userName, false, "무기 칸에 드랍 무기를 장착해야 강화할 수 있어요.");
        int cur = mineWeaponEnh(items), g = mineWeaponGrade(items, w);
        if (cur >= MINE_ENH_MAX) return mineGearResult(userName, false, "무기 칸은 이미 +" + MINE_ENH_MAX + "강이에요.");
        int n = cur + 1, pct = mineEnhPct(n);
        String[][] cost = mineEnhCost(w, n);
        if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "재료가 모자라요. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")");
        mineTakeCost(userName, cost, 1);
        boolean ok = RND.nextInt(100) < pct;
        if (ok) { dao.upsertMineItem(userName, MINE_ITEM_WEAPON_ENH, n); }
        HashMap<String, Object> res = mineGearResult(userName, true, ok ? "🔨 강화 성공! 무기 칸 +" + n + " -- " + w.name + ": " + w.effect(g, n) : "💥 강화 실패... (" + pct + "%) 무기 칸은 +" + cur + " 그대로예요.");
        res.put("enhOk", ok);
        res.put("ores", mineOreList(userName));
        return res;
    }
    /** [2026-11-18] 갑옷 칸 강화 -- 요새 도시 방어구 강화사 앞에서만(부르는 쪽이 유저 행을 잠갔다). */
    HashMap<String, Object> mineArmorEnhance(String userName, Map<String, Integer> items) {
        HashMap<String, Object> erow = dao.selectMineUser(userName);
        if (erow == null || !S5MineMap.CITY.equals(strVal(erow.get("MAP_ID"), "")) || !mineAtNpc(erow, "armorer")) return mineGearResult(userName, false, "왕도 무기 강화사 옆 방어구 강화사 앞에서만 강화할 수 있어요."); // [2026-11-19] 왕도로 옮김
        MineWeapon a = mineArmorOn(items);
        if (a == null) return mineGearResult(userName, false, "갑옷을 입어야 강화할 수 있어요.");
        int cur = mineArmorEnh(items), g = mineWeaponGrade(items, a);
        if (cur >= MINE_ENH_MAX) return mineGearResult(userName, false, "갑옷 칸은 이미 +" + MINE_ENH_MAX + "강이에요.");
        int n = cur + 1, pct = mineEnhPct(n);
        String[][] cost = mineArmorEnhCost(n); // [2026-11-19] 미스릴 괴 + 악마의 광석 조각
        if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "재료가 모자라요. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")");
        mineTakeCost(userName, cost, 1);
        boolean ok = RND.nextInt(100) < pct;
        if (ok) { dao.upsertMineItem(userName, MINE_ITEM_ARMOR_ENH, n); items.put(MINE_ITEM_ARMOR_ENH, n); mineItems(userName); } // 갑옷 최대 체력(MINE_ARMOR_HP_OF)도 새로
        HashMap<String, Object> res = mineGearResult(userName, true, ok ? "🛡️ 강화 성공! 갑옷 칸 +" + n + " -- " + a.name + ": " + a.effect(g, n) : "💥 강화 실패... (" + pct + "%) 갑옷 칸은 +" + cur + " 그대로예요.");
        res.put("enhOk", ok);
        res.put("ores", mineOreList(userName));
        return res;
    }
    static final String MINE_ITEM_NINJA_SWORD = "NINJA_SWORD";
    static final String MINE_ITEM_HARPY_SWORD = "HARPY_SWORD";
    /** 무작위 드랍에서 빠지는 장비(하피의 검 = 하피 여왕 전용). */
    static final String MINE_ITEM_IMMORTAL_SWORD = "IMMORTAL_SWORD", MINE_ITEM_IMMORTAL_ARMOR = "ARMOR_IMMORTAL"; // [2026-11-17] 보로네오 전용
    static final int MINE_BORO_GEAR_PCT = 25;
    // [2026-11-18] "바다에서 사냥 시 바다의 검/바다의 갑옷 110레벨제(사실상 진환생하면 그냥 장착)" -- 바다 위 몬스터(Type.sea)만 떨어뜨린다(같은 레벨대 악마의 들판은 아님),
    //  크라켄은 MINE_KRK_GEAR_PCT%로 둘 중 하나(일반~신화). 바다의 검 = 하피의 검(99)과 심판의 검(125) 사이, 바다의 갑옷 = 백화 갑주(80)와 심판의 갑옷(125) 사이.
    static final String MINE_ITEM_SEA_SWORD = "SEA_SWORD", MINE_ITEM_SEA_ARMOR = "ARMOR_SEA";
    static final int MINE_KRK_GEAR_PCT = 30;
    static boolean mineNoRandomDrop(String code) { return MINE_ITEM_HARPY_SWORD.equals(code) || MINE_ITEM_IMMORTAL_SWORD.equals(code) || MINE_ITEM_IMMORTAL_ARMOR.equals(code) || MINE_ITEM_SEA_SWORD.equals(code) || MINE_ITEM_SEA_ARMOR.equals(code) || MINE_ITEM_WYRM_TOOTH.equals(code) || MINE_ITEM_BH_TREMOR.equals(code) || MINE_ITEM_WYRM_SKIN.equals(code); } // [2026-11-23] 월드보스 장비
    static final MineWeapon[] MINE_WEAPONS = {
        new MineWeapon("BICHUL_SWORD", "비철도", 3, 20, 50, 0.2, 3, -1, 0, S5MineMap.WEAPON_SHARD_CODES[1]),
        new MineWeapon("KATANA", "카타나", 4, 40, 250, 0.2, 3, -1, 0, S5MineMap.WEAPON_SHARD_CODES[2]),
        new MineWeapon(MINE_ITEM_NINJA_SWORD, "닌자의 검", 2, 60, 500, 0.4, 7, 3, 1, S5MineMap.WEAPON_SHARD_CODES[0]),
        new MineWeapon("BAEKHWA_SWORD", "백화검", 5, 80, 1500, 0.2, 7, 0, 1, S5MineMap.WEAPON_SHARD_CODES[3]),
        // [2026-11-11] 하피의 검 -- 하피 여왕만 떨어뜨린다(처치하면 반드시, 일반~신화). 99레벨부터. 백화검의 약 1.5배(공 2250 + 공격력 2%, 등급마다 +20%와 +1%). 조각은 백화검 조각.
        new MineWeapon(MINE_ITEM_HARPY_SWORD, "하피의 검", 6, 99, 2250, 0.2, 7, 0, 2, S5MineMap.WEAPON_SHARD_CODES[3]),
        // [2026-11-17] 심판의 검 -- 심판자 보로네오만 떨어뜨린다(처치마다 MINE_BORO_GEAR_PCT%로 검이나 갑옷 하나, 일반~신화). 125레벨부터. 하피의 검의 약 1.5배(공 3400 + 공격력 3%).
        new MineWeapon(MINE_ITEM_IMMORTAL_SWORD, "심판의 검", 7, 125, 3400, 0.2, 7, 0, 3, S5MineMap.WEAPON_SHARD_CODES[3]),
        new MineWeapon(MINE_ITEM_SEA_SWORD, "바다의 검", 8, 110, 2800, 0.2, 7, 0, 2, S5MineMap.WEAPON_SHARD_CODES[3]), // [2026-11-18] 바다 위 몬스터/크라켄 전용(공 2800 + 공격력 2%)
        // [2026-11-23] 월드보스 장비(신화 단일 등급) -- 거룡의 이빨 = 심판의 검 x1.5(신화 공 12,240 + 공격력 15%) + 용병 공격력 +5%,
        //  베히모스의 전율 = 심판의 검 x2(신화 공 16,320 + 공격력 15% -- 같은 날 "두 검 모두 공격력 +15%") + 용병 공격력 +10%. 거룡/베히모스 처치 때 함께 싸운 사람마다 1%(mineWbGearRoll).
        new MineWeapon("WYRM_TOOTH", "거룡의 이빨", 9, 125, 5100, 0.2, 7, 0, 8, S5MineMap.WEAPON_SHARD_CODES[3]),
        new MineWeapon("BH_TREMOR", "베히모스의 전율", 10, 125, 6800, 0.2, 7, 0, 8, /* [2026-11-23] 공격력 % 20 -> 15 */ S5MineMap.WEAPON_SHARD_CODES[3]),
    };
    static final String MINE_ITEM_WYRM_TOOTH = "WYRM_TOOTH", MINE_ITEM_BH_TREMOR = "BH_TREMOR", MINE_ITEM_WYRM_SKIN = "ARMOR_WYRM_SKIN";
    static final String MINE_ITEM_BH_PEARL = "BH_PEARL_NECKLACE", MINE_ITEM_BH_ORB = "BH_ORB_GLOVES";
    static final int MINE_WB_GEAR_PCT = 1; // [2026-11-23] 월드보스 장비 -- 장비마다 1%
    static final int MINE_BH_ORB_PCT = 20, MINE_BH_ORB_DMG = 200_000; // 베히모스의 수정구슬 -- 적중 시 20%로 물대포(물 속성 고정 20만)
    static final double MINE_WYRM_SKIN_TAKE = 0.8; // 거룡의 피부조각 -- 받는 피해 20% 감소
    /** 든 무기의 용병 공격력 %(거룡의 이빨 5 / 베히모스의 전율 10). */
    static int mineWeaponMercPct(Map<String, Integer> items) { MineWeapon w = items == null ? null : mineWeaponOn(items); return w == null ? 0 : MINE_ITEM_BH_TREMOR.equals(w.code) ? 10 : MINE_ITEM_WYRM_TOOTH.equals(w.code) ? 5 : 0; }
    // [2026-11-23] 같은 날 "즉사 방지는 쿨타임 1분, 광전사 장갑보다 먼저" -- 마지막으로 버틴 시각(유저별 메모리)
    static final long MINE_WYRM_SAVE_CD_MS = 60_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_WYRM_SAVE_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static boolean mineWyrmSave(String userName, long now, HashMap<String, Object> out) {
        Long last = MINE_WYRM_SAVE_AT.get(userName);
        if (last != null && now - last < MINE_WYRM_SAVE_CD_MS) return false;
        MINE_WYRM_SAVE_AT.put(userName, now);
        if (out != null) { out.put("wyrmSave", true); out.put("wyrmCd", MINE_WYRM_SAVE_CD_MS); }
        return true;
    }
    static boolean mineWyrmSkinOn(Map<String, Integer> items) { MineWeapon a = items == null ? null : mineArmorOn(items); return a != null && MINE_ITEM_WYRM_SKIN.equals(a.code); }
    static final int MINE_WEAPON_DROP_LV_GAP = 4;
    /** 등급 뽑기 가중치(1만분율, 일반 -> 신화): 41.22% / 30% / 16% / 8% / 3.5% / 1% / 0.25% / 0.03%. maxGrade까지만(영웅까지인 무기는 그 안에서 비율대로).
     *  [2026-10-31] "고등급 확률 두 배 이상" -- 예전 56 / 30 / 8 / 4 / 1.5 / 0.4 / 0.09 / 0.01%. */
    static final int[] MINE_GRADE_W = { 4122, 3000, 1600, 800, 350, 100, 25, 3 };
    /** [2026-10-29] 개체 단계별 무기 확률(MINE_WEAPON_RANK_P10K)과 희귀 이상 등급 가중치 배수(하급 x0.8 / 보통 x1 / 상급 x1.5). */
    //  [2026-10-29] "무기 드랍률 30% 정도 더 낮춰" -- 1만분율로 x0.7: 하급 0.21% / 보통 0.35% / 상급 0.56%(예전 0.3 / 0.5 / 0.8%)
    //  [2026-10-31] "무기 드랍률 두 배" -- 하급 0.42% / 보통 0.7% / 상급 1.12%
    static final int[] MINE_WEAPON_RANK_P10K = { 42, 70, 112 };
    static final double[] MINE_RANK_GRADE_MUL = { 0.8, 1.0, 1.5 };
    static int mineGradeRoll(int maxGrade) { return mineGradeRoll(maxGrade, 1); }
    static int mineGradeRoll(int maxGrade, int rank) {
        double[] w = new double[maxGrade + 1];
        double tot = 0;
        for (int g = 0; g <= maxGrade; g++) { w[g] = MINE_GRADE_W[g] * (g >= 2 ? MINE_RANK_GRADE_MUL[rank] : 1); tot += w[g]; }
        double r = RND.nextDouble() * tot;
        for (int g = 0; g <= maxGrade; g++) { r -= w[g]; if (r < 0) return g; }
        return 0;
    }
    // [2026-11-06] 보스 장비 -- 처치 시 MINE_BOSS_GEAR_PCT%, 등급은 영웅부터: 영웅 70% / 전설 22% / 유물 6% / 고대 1.8% / 신화 0.2%(그 장비 최고 등급까지만, 비율대로).
    static final int MINE_BOSS_GEAR_PCT = 50;
    static final int[] MINE_BOSS_GRADE_W = { 0, 0, 0, 700, 220, 60, 18, 2 };
    static int mineBossGradeRoll(int maxGrade) {
        int tot = 0;
        for (int g = 3; g <= maxGrade; g++) tot += MINE_BOSS_GRADE_W[g];
        int r = RND.nextInt(Math.max(1, tot));
        for (int g = 3; g <= maxGrade; g++) { r -= MINE_BOSS_GRADE_W[g]; if (r < 0) return g; }
        return Math.min(3, maxGrade);
    }
    static MineWeapon mineWeaponOf(String code) { for (MineWeapon w : MINE_WEAPONS) if (w.code.equals(code)) return w; return null; }
    static int mineWeaponGrade(Map<String, Integer> items, MineWeapon w) { return items.getOrDefault(w.code, 0) - 1; }
    /** [2026-10-30] "무기를 등급별로 다 가지고 고르게" -- 가진 등급 비트(아이템 코드 + "_M"), 고른(든) 등급은 그대로 코드 값(등급 + 1).
     *  예전엔 가장 좋은 등급 하나만 저장했으니 비트가 없으면 그 등급 하나만 가진 것으로 본다. */
    static int mineWeaponMask(Map<String, Integer> items, MineWeapon w) {
        int m = items.getOrDefault(w.code + "_M", 0), g = mineWeaponGrade(items, w);
        return m | (g >= 0 ? 1 << g : 0);
    }
    /** 지금 든 드랍 무기(없으면 null -- 칼/양산). */
    static MineWeapon mineWeaponOn(Map<String, Integer> items) {
        int s = items.getOrDefault(MINE_ITEM_WEAPON_SLOT, 0);
        for (MineWeapon w : MINE_WEAPONS) if (w.slot == s && mineWeaponGrade(items, w) >= 0) return w;
        return null;
    }
    /** 이 몬스터가 떨어뜨리는 무기들(레벨 ±4 / 닌자 = 닌자의 검 / [2026-10-29] 다도해 본섬 몬스터도 닌자의 검). 허수아비/분신/보상 없는 몬스터는 없음. */
    static List<MineWeapon> mineWeaponDrops(S5MineMobs.Type t) {
        List<MineWeapon> out = new ArrayList<>();
        if (t.dummy || t.clone || t.noReward) return out;
        if (t.ninja || java.util.Arrays.asList(t.maps).contains(S5MineMap.ARCHI)) out.add(mineWeaponOf(MINE_ITEM_NINJA_SWORD));
        if (t.sea) { out.add(mineWeaponOf(MINE_ITEM_SEA_SWORD)); return out; } // [2026-11-18] 바다 몬스터 = 바다의 검만
        if (java.util.Arrays.asList(t.maps).stream()
                .anyMatch(m -> m.equals(S5MineMap.ISLE1)
                            || m.equals(S5MineMap.ISLE2)
                            || m.equals(S5MineMap.ISLE3)
                            || m.equals(S5MineMap.ISLE4)
                            || m.equals(S5MineMap.ISLE5)
                            || m.equals(S5MineMap.ISLE6)
                            || m.equals(S5MineMap.ISLE7))) {
            out.add(mineWeaponOf("BAEKHWA_SWORD"));
        }
        if (!t.ninja) for (MineWeapon w : MINE_WEAPONS) if (Math.abs(t.lv() - w.lv) <= MINE_WEAPON_DROP_LV_GAP && !out.contains(w) && !mineNoRandomDrop(w.code)) out.add(w); // [2026-11-11] 하피의 검은 하피만
        return out;
    }
    /** 화면 그림 이름(_mine_char.jsp WEAPON_LOOK의 키). */
    static String mineWeaponLook(MineWeapon w) {
        switch (w.code) {
            case "BICHUL_SWORD": return "bichul";
            case "KATANA": return "katana";
            case MINE_ITEM_NINJA_SWORD: return "ninja";
            case "BAEKHWA_SWORD": return "baekhwa";
            case MINE_ITEM_HARPY_SWORD: return "harpy";
            case MINE_ITEM_SEA_SWORD: return "sea";
            case MINE_ITEM_WYRM_TOOTH: return "immortal"; // [2026-11-23] 전용 그림 전까지 심판의 검 그림
            case MINE_ITEM_BH_TREMOR: return "sea"; // [2026-11-23] 전용 그림 전까지 바다의 검 그림 // [2026-11-18] 바다의 검(_mine_char.jsp W32.sea)
            case MINE_ITEM_IMMORTAL_SWORD: return "immortal"; // [2026-11-17] 심판의 검(_mine_char.jsp W32.immortal) // [2026-11-11] 하피의 검(시안 승인 -- _mine_char.jsp W32.harpy)
            default: return "sword";
        }
    }
    static int mineWeaponPct(Map<String, Integer> items) { MineWeapon w = mineWeaponOn(items); if (w == null) return 0; int g = mineWeaponGrade(items, w); return w.pctAt(g) + w.pctEnh(g, mineWeaponEnh(items)); } // [2026-11-06] + 무기 칸 강화
    /** 무기 고정 공격력(양산 +10, 드랍 무기는 등급대로). */
    static int mineWeaponFlat(Map<String, Integer> items) { MineWeapon w = mineWeaponOn(items); if (w != null) { int g = mineWeaponGrade(items, w); return w.atkAt(g) + w.atkEnh(g, mineWeaponEnh(items)); } return mineParasolOn(items) ? MINE_PARASOL_ATK_FLAT : 0; } // [2026-11-06] + 강화
    // [2026-10-29] 무기 속성 부여 -- ENCH_EL(S5MineMobs.EL_*) / ENCH_LEFT(남은 공격 횟수). 상성 몬스터에게 피해 x1.5(곱연산).
    static final String MINE_ITEM_ENCH_EL = "ENCH_EL", MINE_ITEM_ENCH_LEFT = "ENCH_LEFT";
    /** 성당 신부 성속성 1000번, [2026-10-29] "원소술사 속성 부여는 500회" -- 물/번개/불/땅 500번. */
    static final int MINE_ENCH_HITS = 1000, MINE_ENCH_HITS_ELEM = 500;
    static double mineElemMul(Map<String, Integer> items, S5MineMobs.Type type) {
        return items.getOrDefault(MINE_ITEM_ENCH_LEFT, 0) > 0 && mineWeakTo(items.getOrDefault(MINE_ITEM_ENCH_EL, 0), type) ? 1.5 : 1;
    }
    /** [2026-10-29] 레벨대 무기 드랍(무기마다 확률, 한 번에 하나) -- 이미 가진 등급이면 조각으로 분해. [2026-11-04] 검기 처치와 함께 쓰려고 mineAttack에서 뺐다. */
    void mineWeaponDrop(String userName, Map<String, Integer> items, S5MineMobs.Type type, int rank, StringBuilder msg, HashMap<String, Object> out) {
        MineWeapon dw = null;
        int wp = MINE_WEAPON_RANK_P10K[rank];
        if (java.util.Arrays.asList(type.maps).contains(S5MineMap.WESTROAD)) wp /= 2;
        if (mineLuckOn(items, System.currentTimeMillis())) wp *= 2; // [2026-11-05] 행운의 주문서 -- 드랍률 2배 // [2026-11-05] "마물의 숲 장비 드랍은 다른 맵의 절반(등급 비율은 같게)"
        boolean admin = isMineAdmin(userName);
        if (admin) wp = Math.min(10000, wp * 10); // [2026-11-05] "관리자 드랍률 자체도 올려줘"(이펙트 시험용) -- 10배
        int bossG = -1;
        if (type.boss) { // [2026-11-06] "보스들도 장비를 -- 최소 영웅부터(신화는 엄청 낮게)": MINE_BOSS_GEAR_PCT%로 보스 레벨에 가장 가까운 무기/갑옷 하나(닌자 두목 = 닌자 장비)
            boolean harpy = type == S5MineMobs.TYPES.get(S5MineMobs.HARPY_BOSS_TYPE), boro = type == S5MineMobs.TYPES.get(S5MineMobs.BORO_BOSS_TYPE), krk = type == S5MineMobs.TYPES.get(S5MineMobs.KRAKEN_TYPE); // [2026-11-18] 크라켄 = 바다의 검/갑옷 // [2026-11-17] 보로네오 = 심판의 검/갑옷 // [2026-11-11] 하피 여왕 = 하피의 검(반드시, 일반~신화)
            if (type.noReward || type.clone || type.dummy || (!admin && RND.nextInt(100) >= (harpy ? MINE_HARPY_DROP_PCT : boro ? MINE_BORO_GEAR_PCT : krk ? MINE_KRK_GEAR_PCT : MINE_BOSS_GEAR_PCT))) return; // [2026-11-11] 하피의 검 30%
            List<MineWeapon> cand = new ArrayList<>();
            if (harpy) cand.add(mineWeaponOf(MINE_ITEM_HARPY_SWORD));
            else if (boro) { cand.add(mineWeaponOf(MINE_ITEM_IMMORTAL_SWORD)); cand.add(mineArmorOf(MINE_ITEM_IMMORTAL_ARMOR)); }
            else if (krk) { cand.add(mineWeaponOf(MINE_ITEM_SEA_SWORD)); cand.add(mineArmorOf(MINE_ITEM_SEA_ARMOR)); }
            else if (type.ninja) { cand.add(mineWeaponOf(MINE_ITEM_NINJA_SWORD)); cand.add(mineArmorOf("ARMOR_NINJA")); }
            else {
                int best = -1;
                for (MineWeapon[] arr : new MineWeapon[][] { MINE_WEAPONS, MINE_ARMORS }) for (MineWeapon w : arr)
                    if (!mineNoRandomDrop(w.code)) if (best < 0 || Math.abs(type.lv() - w.lv) < Math.abs(type.lv() - best) || (Math.abs(type.lv() - w.lv) == Math.abs(type.lv() - best) && w.lv > best)) best = w.lv;
                for (MineWeapon[] arr : new MineWeapon[][] { MINE_WEAPONS, MINE_ARMORS }) for (MineWeapon w : arr) if (w.lv == best && !mineNoRandomDrop(w.code)) cand.add(w);
            }
            dw = cand.get(RND.nextInt(cand.size()));
            bossG = harpy || boro || krk ? mineGradeRoll(dw.maxGrade, rank) : mineBossGradeRoll(dw.maxGrade); // [2026-11-17] 심판 장비도 일반~신화 // [2026-11-11] 하피의 검은 일반 몬스터에게 무기가 떴을 때와 같은 등급 비율(일반~신화)
        } else {
            for (MineWeapon w : mineWeaponDrops(type)) if (RND.nextInt(10000) < wp) { dw = w; break; }
            if (dw == null) for (MineWeapon a : mineArmorDrops(type)) if (RND.nextInt(10000) < wp) { dw = a; break; } // [2026-11-06] 갑옷 -- 무기와 같은 확률로 따로
        }
        if (dw == null) return;
        int g = bossG >= 0 ? bossG : mineGradeRoll(dw.maxGrade, rank), cur = mineWeaponGrade(items, dw), mask = mineWeaponMask(items, dw);
        if (admin && dw.maxGrade >= 4 && g < 4 && RND.nextInt(100) < 70) g = 4 + RND.nextInt(dw.maxGrade - 3); // [2026-11-05] "관리자 전설 이상 잘 나오게"(이펙트 시험용) -- 70%는 전설~최고 등급
        if ((mask & (1 << g)) != 0) { // [2026-10-30] "분해는 같은 등급이 또 나왔을 때만" -- 이미 가진 등급
            dao.addUserOre(userName, dw.shard, g + 1);
            msg.append(" 🧩 이미 있는 ").append(dw.name).append("이라 분해했어요. ").append(mineCodeName(dw.shard)).append(" +").append(g + 1);
            out.put("dropShard", g + 1);
            out.put("dropShardName", mineCodeName(dw.shard));
        } else {
            dao.upsertMineItem(userName, dw.code + "_M", mask | (1 << g)); // [2026-10-30] 처음 나온 등급은 보관(등급별로 골라 든다)
            items.put(dw.code + "_M", mask | (1 << g));
            if (cur < 0) { dao.upsertMineItem(userName, dw.code, g + 1); items.put(dw.code, g + 1); } // 처음 얻은 무기 -- 그 등급을 고른 등급으로(장착은 직접). [2026-10-30] "자동 교체 없애줘"
            if (g >= 4) mineGossipAdd(mineNick(userName) + " 님이 " + MINE_GRADE_KO[Math.min(g, MINE_GRADE_KO.length - 1)] + " " + dw.name + "을(를) 손에 넣었대요!"); // [2026-11-05] 왕도 소문
            msg.append(dw.armor ? " 🛡️ " : " 🗡️ ").append(dw.name).append("을(를) 얻었어요! ").append(dw.effect(g)).append(dw.armor ? " (인벤토리 갑옷 칸에서 등급을 골라 장착할 수 있어요)" : " (인벤토리 무기 칸에서 등급을 골라 장착할 수 있어요)");
        }
        if (dw.armor) out.put("dropArmor", true);
        out.put("dropGrade", g);
        out.put("dropName", dw.name);
    }

    // ---- [2026-11-04] 검기 ----
    // "공격 시 바라보는 방향으로 앞 최대 5칸 검기, 속성별 색 이펙트. 공격력 100% 기준 얼음2 불2면 바로 앞 적은 일반 피해 + 얼음 20% + 불 20%(숫자 3개),
    //  사거리는 가장 높은 속성 단계(얼음2 불2 = 2칸), 그 너머 적은 속성 피해만, 상성 x1.5, 8속성 모두면 8가지가 더해진다"
    //  속성별 배운 단계 AURA_L{e}(0~5, 새 NPC가 가르친다 -- 재료 미정), 켠 단계 AURA_A{e}(0~배운 단계, 인벤토리 스킬 탭에서 고른다).
    static final int MINE_AURA_MAX = 5, MINE_AURA_PCT = 5; // [2026-11-04] "10%씩 말고 5%씩(속성별 최대 25%)"
    /** [2026-11-04] "달빛석 + 보스 처치 재화로 검기 강화" -- 속성별 보스 드랍템(땅 = 사막의 심장, 얼음 = 얼음의 심장, 불 = 화산의 심장,
     *  어둠 = 타락한 대주교의 인장, 나머지는 보스가 생기면). n단계를 배우려면 그 드랍템 2n-1개 + 달빛석 2n-1개(1/3/5/7/9). */
    static final String[] MINE_AURA_BOSS_ITEM = { null, S5MineMap.DESERT_HEART_CODE, S5MineMap.THUNDER_HEART_CODE /* [2026-11-05] 번개 = 천둥 황소 */, S5MineMap.WATER_HEART_CODE /* [2026-11-18] 물 = 물의 심장(심해의 크라켄), [2026-11-19] 바다 위를 모두에게 열어 넣음(물 돌개베기도 이제 물의 심장 + 세공 진주) */, S5MineMap.ICE_HEART_CODE, S5MineMap.WIND_HEART_CODE /* [2026-11-11] 바람 = 하피 여왕 */, S5MineMap.VOLCANO_HEART_CODE, S5MineMap.DARK_HEART_CODE /* [2026-11-18] 어둠 = 어둠의 심장(예전 대주교의 인장) */, S5MineMap.LIGHT_HEART_CODE /* [2026-11-17] 빛 = 심판자 보로네오 */ };
    static int mineAuraCost(int lv) { return Math.max(1, 2 * lv - 2); } // [2026-11-17] "검기나 돌개바람 가격을 조금씩 낮추고" -- 1/3/5/7/9 -> 1/2/4/6/8
    /** [2026-11-04] "달빛석뿐 아니라 세공된 바다의 보석도" -- 속성별 세공 바다 보석(S5MineMap.seaCutCode 번호): 땅 = 자수정, 얼음 = 진주, 불 = 산호석, 어둠 = 흑진주. 같은 개수(2n-1). */
    static final int[] MINE_AURA_SEA = { -1, 2, 3, 0, 0, 0, 1, 4, 0 }; // [2026-11-19] 물 = 세공 진주(0) // [2026-11-17] 빛 = 세공 진주(0) // [2026-11-11] 바람 = 세공 진주(0) // [2026-11-05] 번개 = 세공 바다 보석 3번
    static final int MINE_AURA_SEA_QTY = 4; // [2026-11-17] 5 -> 4 // [2026-11-04] "세공 바다 보석은 2n-1 말고 고정 5개"
    static String mineAuraKey(boolean active, int e) { return (active ? "AURA_A" : "AURA_L") + e; }
    static int[] mineAuraActive(Map<String, Integer> items) {
        int[] a = new int[9];
        for (int e = 1; e <= 8; e++) a[e] = Math.max(0, Math.min(Math.min(items.getOrDefault(mineAuraKey(true, e), 0), items.getOrDefault(mineAuraKey(false, e), 0)), MINE_AURA_MAX));
        return a;
    }
    static int mineAuraRange(int[] a) { int r = 0; for (int e = 1; e <= 8; e++) r = Math.max(r, a[e]); return r; }
    /** [2026-11-20] "검기를 배우면 기본 공격 사거리도 같이(무속성), 검기 사거리 중 가장 높은 것만큼" -- 배운 단계(AURA_L, 켜지 않아도) 중 가장 높은 것 = 평타가 닿는 칸 수(최소 1). */
    static int mineMeleeReach(Map<String, Integer> items) { int r = 1; for (int e = 1; e <= 8; e++) r = Math.max(r, Math.min(items.getOrDefault(mineAuraKey(false, e), 0), MINE_AURA_MAX)); return r; }
    // [2026-11-20] 태초의 보석을 쓴 사람 -- 검기 폭 3칸(관리자처럼). AURA_WIDE = 1(한 번만). 검기 칸 계산(mineAuraLine)이 자주 불리니 메모리에 둔다.
    static final String MINE_ITEM_AURA_WIDE = "AURA_WIDE";
    static final int MINE_ALCHEMY_RATE = 10; // [2026-11-20] 연금술사 -- 아래 광석 10개 = 위 광석 1개
    static final int MINE_ALCHEMY_BACK_MIN = 3, MINE_ALCHEMY_BACK_MAX = 8; // [2026-11-20] 복원 -- 위 광석 1개 = 아래 광석 3~8개(하나마다 굴림)
    /** [2026-11-20] 태초의 보석 재료 -- 마계의 정수(정제된 마석) 10, 거룡의 심장 조각(내구도 회복 키트) 50, 악마의 광석 조각 1000, 세공된 보석(땅·바다) 각 10,
     *  같은 날 "남아도는 것 + @를 소모하도록, 구리 만 개 기준" -- 유저 전체 보유 비율(구리 33.8 : 철 23.9 : 은 16.6 : 금 25.5)대로 구리 10000 · 철 7000 · 은 5000 · 금 7500,
     *  + 병목 재료는 조금(미스릴 300, 별빛석 30 -- 예전 미스릴 10000은 유저 전체 보유량의 2.6배라 아무도 못 만들었다). */
    static final int[] MINE_PRIMAL_ORE = { 100000, 100000, 100000, 100000, 10000 }; // 구리, 철, 은, 금, 미스릴 -- [2026-11-20] "각 광석 10만 개, 미스릴 1만 개", 같은 날 "별빛석은 빼줘"
    static String[][] minePrimalCost() {
        List<String[]> l = new ArrayList<>();
        l.add(new String[] { S5MineMap.MANA_STONE_CODE, "10" });
        l.add(new String[] { S5MineMap.WP_KIT_CODE, "50" });
        l.add(new String[] { S5MineMap.DEMON_ORE_CODE, "1000" });
        for (int i = 0; i < MINE_PRIMAL_ORE.length; i++) l.add(new String[] { S5MineMap.ORE_CODES[i], String.valueOf(MINE_PRIMAL_ORE[i]) });
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) l.add(mineCut(i, 10));
        for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) l.add(new String[] { S5MineMap.seaCutCode(i), "10" });
        return l.toArray(new String[0][]);
    }
    static final java.util.concurrent.ConcurrentHashMap<String, Boolean> MINE_AURA_WIDE_U = new java.util.concurrent.ConcurrentHashMap<>();
    boolean mineAuraWideOn(String userName) { return userName != null && MINE_AURA_WIDE_U.computeIfAbsent(userName, u -> mineItems(u).getOrDefault(MINE_ITEM_AURA_WIDE, 0) > 0); }
    static HashMap<String, Object> mineAuraInfo(Map<String, Integer> items) {
        HashMap<String, Object> m = new HashMap<>();
        int[] l = new int[9];
        for (int e = 1; e <= 8; e++) l[e] = Math.min(items.getOrDefault(mineAuraKey(false, e), 0), MINE_AURA_MAX);
        m.put("l", l);
        m.put("a", mineAuraActive(items));
        m.put("reach", mineMeleeReach(items)); // [2026-11-20] 평타 사거리
        m.put("wide", items.getOrDefault(MINE_ITEM_AURA_WIDE, 0) > 0); // [2026-11-20] 태초의 보석 -- 검기 폭 3칸
        m.put("gift", items.getOrDefault(MINE_ITEM_AURA_GIFT, 0) > 0 ? 2 : mineRebirth(items) >= MINE_AURA_GIFT_RB ? 1 : 0); // [2026-11-05] 2환생 특전: 0 = 아직, 1 = 받을 수 있음, 2 = 받음
        return m;
    }
    /** 켠 속성마다 [속성, 피해, 상성 1/0] -- 피해 = 공격력(무기 포함) x 단계 x 5%, 상성 x1.5.
     *  [2026-11-04] "속성마다 자기 단계만큼만 닿는다(불5 얼음1이면 2~5칸 앞은 불만)" -- dist칸 앞이면 단계 >= dist인 속성만. */
    static List<int[]> mineAuraHits(int[] a, double atkBase, S5MineMobs.Type type, int dist) {
        List<int[]> out = new ArrayList<>();
        for (int e = 1; e <= 8; e++) {
            if (a[e] <= 0 || a[e] < dist) continue;
            boolean beat = mineWeakTo(e, type); // [2026-11-17] 보로네오는 빛·어둠 둘 다
            out.add(new int[] { e, Math.max(1, (int) Math.round(atkBase * a[e] * MINE_AURA_PCT / 100.0 * (0.85 + RND.nextDouble() * 0.3) * (beat ? 1.5 : 1) * type.auraTaken)), beat ? 1 : 0 }); // [2026-11-05] auraTaken = 마물의 숲 무리 3배
        }
        return out;
    }
    static int[] mineDirVec(String dir) {
        if ("up".equals(dir)) return new int[] { 0, -1 };
        if ("down".equals(dir)) return new int[] { 0, 1 };
        if ("left".equals(dir)) return new int[] { -1, 0 };
        if ("right".equals(dir)) return new int[] { 1, 0 };
        return null;
    }

    /** 검기 -- (x, y)에서 dir 방향 1~사거리 칸 위의 몬스터(skipMob = 일반 공격으로 이미 때린 몬스터는 뺀다)에게 속성 피해.
     *  보스는 검기만으로는 쓰러지지 않는다(체력 1). 쓰러뜨리면 일반 처치처럼 드랍/무기(경험치는 돌려주는 값으로 -- 부르는 쪽이 더한다).
     *  out: auraMobs [{id, hits, row, killed}], auraDrops. 반환 {처치 수, 경험치}. */
    /** [2026-11-05] 검기 폭(바라보는 줄 양옆 몇 줄)과 한 번에 맞는 최대 마리 -- 처치마다 경험치/드랍을 DB에 쓰므로 느려지지 않을 만큼(12). */
    // [2026-11-05] "옆 폭은 나중에 업그레이드로 -- 지금은 1칸", "검기 12마리로" -- 폭은 바라보는 줄 하나(MINE_AURA_SIDE = 0, 늘리면 양옆 줄), 모든 맵 최대 12마리.
    static final int MINE_AURA_SIDE = 0, MINE_AURA_MAX_TARGETS = 12;
    /** [2026-11-15] 플레이어 검기가 지나는 칸(칸 -> 몇 칸 앞) -- 바라보는 쪽 range칸(관리자는 폭 3칸 + 둘레 8칸). mineAuraSweep에서 떼어 냄(바로 때린 보스의 걸친 칸 수를 셀 때도 쓴다). */
    java.util.Map<Integer, Integer> mineAuraLine(String userName, S5MineMap map, int x, int y, int[] dv, int range) {
        java.util.Map<Integer, Integer> line = new java.util.HashMap<>(); // 칸 -> 몇 칸 앞
        if (dv == null || range <= 0) return line;
        // [2026-11-05] "검기가 여러 마리 타격" -- 바라보는 줄(+ 양옆 MINE_AURA_SIDE줄, 지금 0)의 몬스터를 가까운 순으로 최대 MINE_AURA_MAX_TARGETS마리. 속성 사거리(몇 칸 앞)는 그대로.
        boolean adminAura = isMineAdmin(userName);
        int side = adminAura || mineAuraWideOn(userName) ? 1 : MINE_AURA_SIDE; // [2026-11-05] "관리자 검기 폭 3칸" -- 양옆 한 줄씩, [2026-11-20] 태초의 보석을 쓴 사람도
        if (adminAura) for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) { // [2026-11-05] "관리자 검기에 8방향 둘레 회오리" -- 내 둘레 8칸도 1칸 거리로
            int lx = x + ox, ly = y + oy;
            if ((ox != 0 || oy != 0) && lx >= 0 && ly >= 0 && lx < map.w && ly < map.h) line.put(ly * map.w + lx, 1);
        }
        for (int k = 1; k <= range; k++) for (int sd = -side; sd <= side; sd++) {
            int lx = x + dv[0] * k - dv[1] * sd, ly = y + dv[1] * k + dv[0] * sd;
            if (lx < 0 || ly < 0 || lx >= map.w || ly >= map.h) continue;
            line.putIfAbsent(ly * map.w + lx, k);
        }
        return line;
    }
    long[] mineAuraSweep(String userName, S5MineMap map, Map<String, Integer> items, int[] aura, int range, double atkBase, int x, int y, String dir, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out) {
        int[] dv = mineDirVec(dir);
        if (dv == null || range <= 0) return new long[] { 0, 0 };
        java.util.Map<Integer, Integer> line = mineAuraLine(userName, map, x, y, dv, range);
        if (S5MineMap.HARPY_NEST.equals(map.id) && (aura[6] > 0 || aura[2] > 0)) mineTreeFire(map, line.keySet(), aura[6], aura[2], now, out); // [2026-11-08] 불 검기 -- 나무에 불, [2026-11-11] 번개도
        return mineSweepCells(userName, map, items, line, MINE_AURA_MAX_TARGETS, skipMob, now, msg, out, (ot, dist) -> mineAuraHits(aura, atkBase, ot, dist), "auraMobs", "auraDrops", "🌀 검기로 ");
    }

    // [2026-11-22] "검기와 돌개베기가 겹치면 센 쪽만" -- 한 번 공격에서 두 범위에 모두 걸친 몬스터는 기대 피해(켠 단계 x 상성 x 걸친 칸 수)가 큰 쪽에서만 맞는다(같으면 검기).
    //  mineSweepCells가 이 동안 MINE_SWEEP_SKIP에 든 몬스터를 건너뛴다.
    static final ThreadLocal<java.util.Set<Integer>> MINE_SWEEP_SKIP = new ThreadLocal<>();
    /** 몬스터 om이 칸 묶음 line에 걸치면 {가장 가까운 거리, 걸친 칸 수}, 아니면 null. */
    static int[] mineSweepCover(S5MineMap map, S5MineMap.Mob om, MobState s0, long now, java.util.Map<Integer, Integer> line) {
        int fw = S5MineMobs.TYPES.get(om.type).foot(), dist = 99, cnt = 0;
        for (int c : mineMobCells(om, s0, now, map)) {
            int n = 0, dmin = 99;
            for (int oy = 0; oy < fw; oy++) for (int ox = 0; ox < fw; ox++) { Integer k = line.get(c + oy * map.w + ox); if (k != null) { n++; if (k < dmin) dmin = k; } }
            if (n > 0 && (dmin < dist || (dmin == dist && n > cnt))) { dist = dmin; cnt = n; }
        }
        return cnt > 0 ? new int[] { dist, cnt } : null;
    }
    static java.util.Map<Integer, Integer> mineWhirlCells(S5MineMap map, int x, int y) {
        java.util.Map<Integer, Integer> cells = new java.util.HashMap<>();
        for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
            int lx = x + ox, ly = y + oy;
            if ((ox != 0 || oy != 0) && lx >= 0 && ly >= 0 && lx < map.w && ly < map.h) cells.put(ly * map.w + lx, 1);
        }
        return cells;
    }
    /** 두 범위에 모두 걸친 몬스터를 나눈다 -- skipA = 검기에서 뺄 몬스터(돌개가 더 셈), skipW = 돌개에서 뺄 몬스터. */
    void mineAuraWhirlPick(String userName, S5MineMap map, Map<String, Integer> items, int[] aura, int range, int x, int y, String dir, int skipMob, long now, java.util.Set<Integer> skipA, java.util.Set<Integer> skipW) {
        int[] w = mineWhirlActive(items), dv = mineDirVec(dir);
        if (!mineWhirlOn(w) || range <= 0 || dv == null) return;
        java.util.Map<Integer, Integer> line = mineAuraLine(userName, map, x, y, dv, range), wc = mineWhirlCells(map, x, y);
        for (S5MineMap.Mob om : map.mobs()) {
            if (om.id == skipMob) continue; // 바로 때린 몬스터는 검기가 원래 건너뛴다
            MobState s0 = MINE_MOB_STATE.get(mineMobKey(map, om.id));
            if (s0 != null && s0.deadUntil > now) continue;
            int[] ca = mineSweepCover(map, om, s0, now, line);
            if (ca == null) continue;
            int[] cw = mineSweepCover(map, om, s0, now, wc);
            if (cw == null) continue;
            S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
            double sa = 0, sw = 0;
            for (int e = 1; e <= 8; e++) {
                double m = mineWeakTo(e, ot) ? 1.5 : 1;
                if (aura[e] > 0 && aura[e] >= ca[0]) sa += aura[e] * m;
                if (w[e] > 0) sw += w[e] * m;
            }
            if (sw * cw[1] > sa * ca[1]) skipA.add(om.id); else skipW.add(om.id);
        }
    }

    /** 칸 묶음(line: 칸 -> 거리) 위의 몬스터를 가까운 순으로 maxT마리까지 hitsFn(종류, 거리) 피해 -- 검기/관리자 전체 공격이 같이 쓴다. 보스는 체력 1에서 멈춘다. */
    long[] mineSweepCells(String userName, S5MineMap map, Map<String, Integer> items, java.util.Map<Integer, Integer> line, int maxT, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out,
            java.util.function.BiFunction<S5MineMobs.Type, Integer, List<int[]>> hitsFn, String mobsKey, String dropsKey, String verb) {
        return mineSweepCells(userName, map, items, line, maxT, skipMob, now, msg, out, hitsFn, mobsKey, dropsKey, verb, false, 0L);
    }
    /** [2026-11-07] ult = 필살기("마지막 한 방은 직접" 없이 보스도 쓰러뜨린다 -- 분신술 닌자 두목만 예외), stunMs = 맞고 살아남은 몬스터 기절(일섬참 5초). */
    long[] mineSweepCells(String userName, S5MineMap map, Map<String, Integer> items, java.util.Map<Integer, Integer> line, int maxT, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out,
            java.util.function.BiFunction<S5MineMobs.Type, Integer, List<int[]>> hitsFn, String mobsKey, String dropsKey, String verb, boolean ult, long stunMs) {
        return mineSweepCells(userName, map, items, line, maxT, skipMob, now, msg, out, hitsFn, mobsKey, dropsKey, verb, ult, stunMs, 0);
    }
    /** [2026-11-08] vulnAdd = 맞은 몬스터의 받는 피해 증가를 이만큼(%) 쌓는다(만검진). */
    long[] mineSweepCells(String userName, S5MineMap map, Map<String, Integer> items, java.util.Map<Integer, Integer> line, int maxT, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out,
            java.util.function.BiFunction<S5MineMobs.Type, Integer, List<int[]>> hitsFn, String mobsKey, String dropsKey, String verb, boolean ult, long stunMs, int vulnAdd) {
        List<HashMap<String, Object>> hitList = new ArrayList<>();
        List<HashMap<String, Object>> drops = new ArrayList<>();
        int mind = mineMindExpPct(items);
        long kills = 0, expSum = 0;
        List<int[]> tg = new ArrayList<>(); // {몬스터 번호, 맞은 칸, 몇 칸 앞}
        java.util.Set<Integer> skipSet = MINE_SWEEP_SKIP.get(); // [2026-11-22] 검기/돌개 겹침 -- 다른 쪽이 더 센 몬스터
        for (S5MineMap.Mob om : map.mobs()) {
            if (om.id == skipMob) continue;
            if (skipSet != null && skipSet.contains(om.id)) continue;
            S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
            if (ot.clone || mineBossDead(om.type) || mineHidden(map, ot, now)) continue; // [2026-11-05] 천둥 황소 동안 마물은 숨음
            if (om.type == S5MineMobs.HARPY_BOSS_TYPE && mineHarpyNow(map, now)[2] == 1) continue; // [2026-11-08] 나무 뒤 하피 -- 필살기도 안 맞는다
            if (S5MineMobs.diving(ot, om.id, now)) continue; // [2026-11-18] 물속 바다 몬스터
            MobState s0 = MINE_MOB_STATE.get(mineMobKey(map, om.id));
            if (ot.minion && s0 == null) continue; // 불려 나오지 않은 수하
            if (s0 != null && s0.deadUntil > now) continue; // 쓰러져 있는 몬스터는 자리를 차지하지 않는다
            int hitCell = -1, fw = ot.foot(), dist = 99, cnt = 0;
            for (int c : mineMobCells(om, s0, now, map)) { // [2026-11-15] 걸친 칸 수(cnt)도 센다 -- 보스(2x2, 거룡 3x3)는 걸친 칸마다 한 번씩 맞는다
                int n = 0, dmin = 99;
                for (int oy = 0; oy < fw; oy++) for (int ox = 0; ox < fw; ox++) {
                    Integer k = line.get(c + oy * map.w + ox);
                    if (k != null) { n++; if (k < dmin) dmin = k; }
                }
                if (n > 0 && (dmin < dist || (dmin == dist && n > cnt))) { dist = dmin; hitCell = c; cnt = n; }
            }
            if (hitCell >= 0) tg.add(new int[] { om.id, hitCell, dist, cnt });
        }
        tg.sort((p, q) -> Integer.compare(p[2], q[2]));
        if (tg.size() > maxT) tg = tg.subList(0, maxT);
        for (int[] tq : tg) {
            S5MineMap.Mob om = map.mobs().get(tq[0]);
            S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
            String key = mineMobKey(map, om.id);
            int hitCell = tq[1], dist = tq[2];
            int omMax = S5MineMobs.hp(ot, om.rank);
            MobState s = MINE_MOB_STATE.computeIfAbsent(key, k -> new MobState(omMax));
            // [2026-11-15] "검기나 화살비, 검비 등 여러 칸 때리는 스킬은 한 칸당 데미지를 받게(이전 보스들 공통)" -- 검기/돌개베기/만검진은 보스가 걸친 칸마다 한 번씩.
            //  같은 날 "맞은 만큼 숫자가 뜨게, 합산되지 않게" -- 칸마다 따로 굴린 피해를 따로 싣는다: 타격 = {속성, 피해, 상성/치명, 칸 번호, 칸 안 순번}, cells = 칸 번호 -> {dx, dy}.
            //  일섬참(stunMs > 0)은 화면의 몬스터를 한 번씩 베는 기술이라 칸 수와 무관.
            List<int[]> hs;
            List<int[]> hitCells = null;
            if (stunMs <= 0 && tq[3] > 1) {
                hs = new ArrayList<>();
                hitCells = new ArrayList<>();
                int fo = ot.foot();
                for (int oy = 0; oy < fo; oy++) for (int ox = 0; ox < fo; ox++) if (line.containsKey(hitCell + oy * map.w + ox)) {
                    int ci = hitCells.size(), q = 0;
                    for (int[] h : hitsFn.apply(ot, dist)) hs.add(new int[] { h[0], h[1], h[2], ci, q++ });
                    hitCells.add(new int[] { ox, oy });
                }
            } else hs = hitsFn.apply(ot, dist);
            int sum = 0;
            for (int[] h : hs) sum += h[1];
            boolean dead = false;
            long[] row;
            synchronized (s) {
                if (s.deadUntil > now) continue;
                if (s.deadUntil > 0) s.respawn(omMax);
                if (ot.sim && now - s.lastHitAt >= MINE_SIM_HEAL_MS) s.hp = omMax; // [2026-11-07] 보스 허수아비 -- 5초 안 맞으면 다 참
                if (ot.boss) mineBossRegen(s, omMax, now, map, om);
                double vul = mineVulnMul(s, now) * ot.armor; // [2026-11-08] 만검진 받는 피해 증가(이번 타격부터 쌓인 만큼), [2026-11-23] 탑 수문장 갑피
                if (vul != 1) { mineVulnApply(hs, vul); sum = 0; for (int[] h : hs) sum += h[1]; }
                if (vulnAdd > 0) mineVulnAdd(s, vulnAdd, now);
                if (ot.ninja && s.stealth) continue; // 은신한 닌자는 안 맞는다
                s.lastHitAt = now;
                mineHitBy(s, ot, userName); // [2026-11-13] 함께 싸운 보상
                if (ot.dummy) { s.fx = hitCell % map.w; s.fy = hitCell / map.w; } // [2026-11-08] 허수아비 자리(다른 사람 화면의 피해 숫자 위치)
                if (ot.sim) { s.hp -= sum; if (s.hp <= 0) { s.hp = 0; s.deadUntil = now + MINE_SIM_DOWN_MS; } } // [2026-11-07] 보스 허수아비 -- 줄기만 하고 처치/보상 없음
                if (!ot.dummy) {
                    int wbB = s.hp; // [2026-11-15]
                    s.hp -= sum;
                    if (ot.boss && (!ult || om.type == S5MineMobs.SHRINE_BOSS_TYPE)) s.hp = Math.max(1, s.hp); // [2026-11-07] 필살기는 보스도 쓰러뜨린다
                    if (om.type == S5MineMobs.WB_BOSS_TYPE) s.hp = mineWbApply(userName, wbB, wbB - Math.max(0, s.hp), now, ult); // [2026-11-15] 월드보스 -- 몫으로(검기는 1에서 멈춤)
                    mineKrkFloor(s, now); // [2026-11-19]
                    if (s.hp <= 0) {
                        dead = true;
                        s.hp = 0;
                        s.deadUntil = ult && ot.boss ? now + (ot.respawnOverride > 0 ? ot.respawnOverride : MINE_BOSS_RESPAWN_MS) : ot.minion ? Long.MAX_VALUE / 2 : now + ot.respawnMs();
                        s.stunUntil = 0;
                        s.frozenUntil = 0;
                        s.stealth = false;
                    } else {
                        s.fx = hitCell % map.w;
                        s.fy = hitCell / map.w;
                        s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                        if (stunMs > 0) { s.stunUntil = now + stunMs; s.frozenUntil = Math.max(s.frozenUntil, s.stunUntil); } // [2026-11-07] 일섬참 -- 기절
                        if (s.target == null || !mineEngaged(s.target, now)) s.target = userName;
                    }
                }
                row = mineMobRow(om.id, s);
            }
            if (!dead && !ot.dummy) mineEngage(userName, map, om.id, now, now);
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", om.id);
            m.put("hits", hs);
            if (hitCells != null) m.put("cells", hitCells); // [2026-11-15] 보스가 걸친 칸들(숫자를 칸마다)
            m.put("row", row);
            m.put("cell", hitCell); // [2026-11-10] 맞은 칸(쓰러진 몬스터는 row의 자리가 비어 있다 -- 일섬참 붙기)
            if (dead) {
                m.put("killed", true);
                kills++;
                mineThunderKill(map, ot, now); // [2026-11-05] 밤 마물 처치 수
                msg.append(mineTowerKill(userName, map, om, ot)); // [2026-11-15] 탑
                long be = mineLuckExp(items, mineNightExp(map, ot, mineTwExp(userName, map, ot, S5MineMobs.exp(ot, om.rank)), now), now), g = be + be * mind / 100; // [2026-11-23] 탑 수문장 x3 / 0 // [2026-11-05] 마물의 숲 밤 1.5배
                expSum += g;
                m.put("exp", g);
                msg.append(msg.length() > 0 ? " " : "").append(verb).append(ot.name).append("을(를) 쓰러뜨렸어요! 경험치 +").append(g);
                if (ult && ot.boss) mineMercBossKilled(userName, map, om, ot, now, out, drops, "필살기로 "); // [2026-11-07] 필살기로 쓰러뜨린 보스 -- 기록/효과/보상은 직접 잡은 것과 같게
                if (!ot.noReward) {
                    if (!(ult && ot.boss)) drops.addAll(mineMobDrops(userName, map, ot, msg, om.rank));
                    if (!(ot.boss && Boolean.TRUE.equals(out.get("bossRwNo"))) && !mineTwNoRw(userName, map, ot)) mineWeaponDrop(userName, items, ot, om.rank, msg, out); // [2026-11-20] 보스 보상 하루 5번, [2026-11-23] 탑 수문장 하루 3번
                }
                MineEngage me = MINE_ENGAGE.get(userName);
                if (me != null) me.mobs.remove(om.id);
            }
            hitList.add(m);
        }
        out.put(mobsKey, hitList);
        if (!drops.isEmpty()) out.put(dropsKey, drops);
        if (kills > 0) out.put("ores", mineOreList(userName));
        return new long[] { kills, expSum };
    }

    /** [2026-11-04] 검기 경험치 -- 더하고 레벨 업. {lv, exp, hp, lock, 오른 레벨 수}. */
    long[] mineAuraExp(String userName, int lv, long exp, int hp, int lock, int rb, long gain, boolean died, HashMap<String, Object> out) {
        if (gain <= 0) return new long[] { lv, exp, hp, lock, 0, 0 };
        boolean wasReady = mineRebirthReady(lv, exp, rb);
        long[] xa = mineExpAdd(lv, exp, gain, rb); // [2026-11-08] 진환생은 99를 넘어
        lv = (int) xa[0]; exp = xa[1];
        int up = (int) xa[2];
        if (!wasReady && mineRebirthReady(lv, exp, rb)) out.put("rebirthReadyNow", true);
        if (up > 0 && !died) {
            hp = mineHpFull(lv, rb); // [2026-11-18] 갑옷 체력까지
            if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.remove("hpLockUp"); out.put("hpLockFree", true); }
        }
        long real = xa[3]; // [2026-11-07] 실제로 오른 경험치
        if (real < gain) out.put("expFull", true);
        out.merge("expReal", real, (p, q) -> (Long) p + (Long) q);
        return new long[] { lv, exp, hp, lock, up, real };
    }
    /** [2026-11-07] (lv0, exp0) -> (lv1, exp1) 사이에 실제로 오른 경험치(지나간 레벨들의 필요치 + 남은 차이). 99레벨 상한에서 잘린 만큼은 빠진다. */
    /** [2026-11-07] 보스 전용 아이템(드랍 등급) -- 대주교의 인장, 닌자의 비급서, 얼음의 심장, 사막의 심장, 화산의 심장, 번개의 심장. 관리자 보스 처치 알림에 이것만 알린다. */
    public static boolean mineBossItemGrade(int g) {
        return g == S5MineMap.SEAL_GRADE || g == S5MineMap.NINJA_BOOK_GRADE || g == S5MineMap.ICE_HEART_GRADE || g == S5MineMap.DESERT_HEART_GRADE
                || g == S5MineMap.VOLCANO_HEART_GRADE || g == S5MineMap.THUNDER_HEART_GRADE || g == S5MineMap.WIND_HEART_GRADE || g == S5MineMap.LIGHT_HEART_GRADE || g == S5MineMap.DARK_HEART_GRADE || g == S5MineMap.JUDGE_TOKEN_GRADE || g == S5MineMap.WATER_HEART_GRADE; // [2026-11-18]
    }
    static long mineExpReal(int lv0, long exp0, int lv1, long exp1, int rb) {
        long s = exp1 - exp0;
        for (int l = lv0; l < lv1; l++) s += mineExpNeed(l, rb);
        return Math.max(0, s);
    }

    // ---- [2026-11-08] 하피의 둥지(보스룸) -- 관리자만(mineWarp) ----
    // "못 가는 대형 나무들, 보스가 움직이다 나무 뒤로 가면 플레이어 공격에 면역(필살기도), 나무 사이 이동 중엔 원거리 공격, 나무를 불로 태우는 기믹(불 검기로 많이 때려야),
    //  숲 전체를 태우면 보스 그로기(숲이 다시 자랄 때까지 약해짐), 숲 재생 2시간, 하피 체력은 천둥 황소의 2배".
    //  하피(asStay)는 살아 있는 나무 위쪽(수관 뒤)에 MINE_HARPY_PERCH_MS 앉아 있고(숨음 -- 모든 공격 무효), MINE_HARPY_MOVE_MS 동안 다음 나무로 옮겨 가며
    //  MINE_HARPY_SHOT_MS마다 깃털(원거리)을 쏜다. 앉은 나무에 불이 붙으면 바로 떠난다. 숲이 남아 있는 동안은 날고 있어 일반 공격(과 용병)은 무효, 검기/돌개베기/필살기만.
    //  나무: 불 검기/불 돌개베기가 닿으면 그 단계만큼 쌓여 MINE_TREE_FIRE_NEED에 닿으면 불붙고(맞은 나무만, 번지지 않음) MINE_TREE_BURN_MS 뒤 다 탄다.
    //  다 탄 나무는 MINE_TREE_REGROW_MS 뒤 다시 자란다. 나무가 모두 다 타 있으면 하피는 둥지 가운데 내려앉아 약해진다(숨지 못하고 일반 공격도 들어감).
    static final int MINE_TREE_FIRE_NEED = 90; // [2026-11-11] "불타는 나무 쉽게 안 부서지게" 60 -> 90(5단계 불 검기 18번, 불+번개 같이면 9번)
    static final int MINE_TREE_CHOP_NEED = 10; // [2026-11-11] "곡괭이로 채광할 수도 -- 10회 정도면 쓰러져 밑동만" -- 여럿이 함께 찍은 횟수
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_CHOP_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_TREE_BURN_MS = 8_000L, MINE_TREE_REGROW_MS = 2 * 3_600_000L, MINE_HARPY_PERCH_MS = 6_000L, MINE_HARPY_MOVE_MS = 4_000L, MINE_HARPY_SHOT_MS = 1_500L;
    static final long MINE_PHARAOH_SHOT_MS = 3_000L, MINE_PHARAOH_STUN_GAP_MS = 9_000L; // [2026-11-11] 파라오 미라 원거리 3초마다, 붕대(스턴)는 9초에 한 번
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_PHARAOH_STUN_AT = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_HARPY_HIDE_SHOT_MS = 2_500L; // 체력 절반 전(둥지 가운데)에도 같은 간격 // [2026-11-11] "하피가 나무에 숨어서도 공격" -- 숨어 있는 동안(6초)에도 깃털 2.5초마다(예전엔 6~10초라 거의 못 쏨)
    static final int MINE_HARPY_SHOT_RANGE = 18; // 숨은 나무가 멀어도 닿게(다른 보스 12칸)
    static final java.util.concurrent.ConcurrentHashMap<Integer, long[]> MINE_TREES = new java.util.concurrent.ConcurrentHashMap<>(); // 나무 번호 -> {불 쌓인 양, 불붙은 시각}
    static final Object MINE_HARPY_LOCK = new Object();
    static int mineHarpyTree = -1, mineHarpyFx, mineHarpyFy, mineHarpyTx, mineHarpyTy;
    static long mineHarpyAt = 0;
    static boolean mineHarpyMoving = false;
    /** 0 살아 있음 / 1 불타는 중 / 2 다 탐 / 3 곡괭이에 쓰러져 밑동만(2, 3은 다시 자랄 때까지). */
    static int mineTreeState(int i, long now) {
        long[] t = MINE_TREES.get(i);
        if (t != null && t.length > 5 && t[5] > 0) { if (now - t[5] < MINE_TREE_REGROW_MS) return 3; MINE_TREES.remove(i, t); return 0; } // [2026-11-11] 쓰러진 나무
        if (t == null || t[1] == 0) return 0;
        long e = now - t[1];
        if (e < MINE_TREE_BURN_MS) return 1;
        if (e < MINE_TREE_BURN_MS + MINE_TREE_REGROW_MS) return 2;
        MINE_TREES.remove(i, t);
        return 0;
    }
    static boolean mineForestBurnt(S5MineMap map, long now) {
        if (map.trees().isEmpty()) return false;
        for (int i = 0; i < map.trees().size(); i++) if (mineTreeState(i, now) < 2) return false; // [2026-11-11] 다 탔거나 쓰러졌거나
        return true;
    }
    // [2026-11-11] "나무는 불 속성에 타지만 번개 속성도 공격되고, 동시 공격 받으면 더 잘 부서진다" -- 불/번개 단계를 더해 쌓고, 한 번에 둘 다이거나
    //  MINE_TREE_COMBO_MS 안에 다른 속성이 또 닿으면 MINE_TREE_COMBO_MUL배. 나무 = {쌓인 양, 불붙은 시각, 마지막 불, 마지막 번개}.
    static final long MINE_TREE_COMBO_MS = 2_000L;
    static final int MINE_TREE_COMBO_MUL = 2;
    /** 불/번개 검기·돌개베기가 닿은 칸(cells) 위의 살아 있는 나무에 쌓는다. 무너지기 시작한 나무 번호를 out.treeFire로. */
    static void mineTreeFire(S5MineMap map, java.util.Set<Integer> cells, int fire, int thunder, long now, HashMap<String, Object> out) {
        int lv = fire + thunder;
        List<int[]> tr = map.trees();
        for (int i = 0; i < tr.size(); i++) {
            int[] tp = tr.get(i);
            boolean hit = false;
            for (int y = tp[1] - 2; y < tp[1] + 2 && !hit; y++) for (int x = tp[0]; x < tp[0] + 3; x++) if (cells.contains(y * map.w + x)) { hit = true; break; } // [2026-11-17] "나무 잎 부분을 공격해도 나무가 공격받은 걸로" -- 밑동 + 수관(위 두 줄)
            if (!hit || mineTreeState(i, now) != 0) continue;
            long[] t = MINE_TREES.computeIfAbsent(i, k -> new long[6]);
            synchronized (t) {
                int add = lv;
                if ((fire > 0 && thunder > 0) || (fire > 0 && now - t[3] < MINE_TREE_COMBO_MS) || (thunder > 0 && now - t[2] < MINE_TREE_COMBO_MS)) { add *= MINE_TREE_COMBO_MUL; out.put("treeCombo", true); }
                if (fire > 0) t[2] = now;
                if (thunder > 0) t[3] = now;
                t[0] += add;
                if (t[0] >= MINE_TREE_FIRE_NEED && t[1] == 0) { t[1] = now; out.put("treeFire", i); }
            }
        }
    }
    /** [2026-11-11] 곡괭이로 나무 찍기 -- 내 자리(x, y) 둘레 8칸 안의 나무 칸(tx, ty). 찍은 횟수가 MINE_TREE_CHOP_NEED에 닿으면 쓰러져 밑동만 남는다(다시 자랄 때까지). */
    public HashMap<String, Object> mineChopTree(String userName, String mapId, String ver, int x, int y, int tx, int ty) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        Long last = MINE_CHOP_AT.get(userName);
        if (last != null && now - last < 400) { out.put("tooFast", true); return out; }
        MINE_CHOP_AT.put(userName, now);
        HashMap<String, Object> row = dao.selectMineUser(userName);
        if (row == null) return out;
        S5MineMap map = mineMapOf(row);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) { out.put("reload", true); return out; }
        if (map.trees().isEmpty()) { out.put("message", "여기서는 캘 수 없어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        if (!mineMoveOk(minePos(row, map, mineWalkMode(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        if (Math.max(Math.abs(tx - x), Math.abs(ty - y)) > 1) { out.put("message", "나무 바로 옆에서 나무 쪽을 바라보고 캐세요."); return out; }
        int ti = -1;
        for (int i = 0; i < map.trees().size(); i++) { int[] tp = map.trees().get(i); if (tx >= tp[0] && tx < tp[0] + 3 && ty >= tp[1] - 2 && ty < tp[1] + 2) { ti = i; break; } } // [2026-11-17] 수관(위 두 줄)도 나무
        if (ti < 0) { out.put("message", "나무 바로 옆에서 나무 쪽을 바라보고 캐세요."); return out; }
        int ts = mineTreeState(ti, now);
        if (ts != 0) { out.put("message", ts == 3 ? "이미 쓰러진 나무예요." : "타 버린 나무예요."); return out; }
        long[] t = MINE_TREES.computeIfAbsent(ti, k -> new long[6]);
        int n;
        boolean felled = false;
        synchronized (t) {
            t[4]++;
            n = (int) Math.min(MINE_TREE_CHOP_NEED, t[4]);
            if (t[4] >= MINE_TREE_CHOP_NEED && t[5] == 0 && t[1] == 0) { t[5] = now; felled = true; }
        }
        out.put("ok", true);
        out.put("tree", ti);
        out.put("chop", n);
        out.put("need", MINE_TREE_CHOP_NEED);
        if (felled) { // [2026-11-19] "나무(하피 보스 곡괭이로)" -- 같은 날 "찍을 때마다 말고 쓰러뜨리면 1~2개" -- 마지막으로 찍어 쓰러뜨린 사람에게
            int wood = 1 + RND.nextInt(2);
            dao.addUserOre(userName, S5MineMap.WOOD_CODE, wood);
            out.put("wood", wood);
            out.put("felled", true); out.put("message", "🪓 나무가 쓰러졌어요! 🪵 나무 +" + wood + "개");
        }
        return out;
    }
    static int[] mineHarpyPerch(S5MineMap map, int i) { int[] t = map.trees().get(i); return new int[] { t[0] + 1, t[1] - 2 }; } // 나무 위쪽(뒤) -- 화면은 수관에 가려 그린다
    static void mineHarpyReset() { synchronized (MINE_HARPY_LOCK) { mineHarpyTree = -1; mineHarpyAt = 0; mineHarpyMoving = false; } }
    /** 하피 지금 자리 {x, y, 숨음 1/0, 약해짐 1/0, 옮기는 중 1/0}. 부를 때마다 시간에 맞춰 다음 단계로 넘어간다. */
    // [2026-11-11] "체력이 절반이 되면 그때부터 나무에 숨어서 원거리 공격" -- 절반 전엔 둥지 가운데(bossX, bossY)에서 깃털 + 돌풍(mineHarpyGust)만.
    static boolean mineHarpyLow(S5MineMap map, long now) {
        for (S5MineMap.Mob mb : map.mobs()) if (mb.type == S5MineMobs.HARPY_BOSS_TYPE) {
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, mb.id));
            return s != null && s.deadUntil <= now && (long) s.hp * 2 < S5MineMobs.hp(S5MineMobs.TYPES.get(mb.type), mb.rank);
        }
        return false;
    }
    static int[] mineHarpyNow(S5MineMap map, long now) {
        synchronized (MINE_HARPY_LOCK) {
            if (!mineHarpyLow(map, now)) { mineHarpyTree = -1; mineHarpyAt = 0; mineHarpyMoving = false; return new int[] { map.bossX(), map.bossY(), 0, 0, 0 }; }
            if (mineForestBurnt(map, now)) { mineHarpyTree = -1; mineHarpyMoving = false; return new int[] { map.bossX(), map.bossY(), 0, 1, 0 }; }
            List<int[]> tr = map.trees();
            List<Integer> live = new ArrayList<>();
            for (int i = 0; i < tr.size(); i++) if (mineTreeState(i, now) == 0) live.add(i);
            if (mineHarpyAt == 0) { // 처음 -- 살아 있는 나무 하나에 앉아 있다
                mineHarpyTree = live.isEmpty() ? -1 : live.get(RND.nextInt(live.size()));
                int[] p = mineHarpyTree < 0 ? new int[] { map.bossX(), map.bossY() } : mineHarpyPerch(map, mineHarpyTree);
                mineHarpyFx = mineHarpyTx = p[0]; mineHarpyFy = mineHarpyTy = p[1];
                mineHarpyAt = now; mineHarpyMoving = false;
            }
            if (!mineHarpyMoving && (now - mineHarpyAt >= MINE_HARPY_PERCH_MS || mineHarpyTree < 0 || mineTreeState(mineHarpyTree, now) != 0)) { // 떠난다
                live.remove(Integer.valueOf(mineHarpyTree));
                int nt = live.isEmpty() ? -1 : live.get(RND.nextInt(live.size()));
                int[] p = nt < 0 ? new int[] { map.bossX(), map.bossY() } : mineHarpyPerch(map, nt);
                mineHarpyFx = mineHarpyTx; mineHarpyFy = mineHarpyTy; mineHarpyTx = p[0]; mineHarpyTy = p[1];
                mineHarpyTree = nt; mineHarpyAt = now; mineHarpyMoving = true;
            } else if (mineHarpyMoving && now - mineHarpyAt >= MINE_HARPY_MOVE_MS) { mineHarpyMoving = false; mineHarpyAt = now; }
            int x = mineHarpyTx, y = mineHarpyTy;
            if (mineHarpyMoving) { double k = Math.min(1, (now - mineHarpyAt) / (double) MINE_HARPY_MOVE_MS); x = (int) Math.round(mineHarpyFx + (mineHarpyTx - mineHarpyFx) * k); y = (int) Math.round(mineHarpyFy + (mineHarpyTy - mineHarpyFy) * k); }
            boolean hidden = !mineHarpyMoving && mineHarpyTree >= 0 && mineTreeState(mineHarpyTree, now) == 0;
            return new int[] { x, y, hidden ? 1 : 0, 0, mineHarpyMoving ? 1 : 0 };
        }
    }
    // [2026-11-11] 하피 돌풍 -- "전체 유저를 날려 버리면서 피해": 싸움이 시작되면(하피가 누군가를 노리는 중) MINE_HARPY_GUST_MS마다 둥지 안 모두를
    //  하피 반대쪽으로 최대 MINE_HARPY_GUST_PUSH칸 날려 보내고 하피 공격력 x MINE_HARPY_GUST_MULT 피해(환경 턴). 처음 들어온 사람은 다음 돌풍부터.
    static final long MINE_HARPY_GUST_MS = 15_000L;
    static final int MINE_HARPY_GUST_PUSH = 5;
    static final double MINE_HARPY_GUST_MULT = 2.0;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_HARPY_GUST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    int mineHarpyGust(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, long now, HashMap<String, Object> out) {
        S5MineMap.Mob bm = mineBossOf(map);
        if (bm == null || mineBossDead(bm.type)) return 0;
        MobState bs = MINE_MOB_STATE.get(mineMobKey(map, bm.id));
        if (bs == null || bs.deadUntil > now || bs.target == null) { MINE_HARPY_GUST_SEEN.remove(userName); return 0; }
        long id = now / MINE_HARPY_GUST_MS;
        out.put("gustNext", (id + 1) * MINE_HARPY_GUST_MS);
        Long seen = MINE_HARPY_GUST_SEEN.put(userName, id);
        if (seen == null || seen >= id) return 0;
        int[] hz = mineHarpyNow(map, now);
        int dx = Integer.signum(x - hz[0]), dy = Integer.signum(y - hz[1]);
        if (dx == 0 && dy == 0) dy = 1;
        int kx = x, ky = y;
        for (int st = 0; st < MINE_HARPY_GUST_PUSH; st++) {
            int[][] tries = { { kx + dx, ky + dy }, { kx + dx, ky }, { kx, ky + dy } };
            boolean moved = false;
            for (int[] q : tries) {
                if ((q[0] == kx && q[1] == ky) || !map.walkable(q[0], q[1]) || S5MineMap.portalChar(map.terrainAt(q[0], q[1]))) continue;
                kx = q[0]; ky = q[1]; moved = true; break;
            }
            if (!moved) break;
        }
        if (kx != x || ky != y) { dao.updateMinePos(userName, map.id, kx, ky, now); out.put("knockX", kx); out.put("knockY", ky); }
        out.put("gust", true);
        S5MineMobs.Type ht = S5MineMobs.TYPES.get(bm.type);
        return mineCoatHit(items, (int) Math.min(Integer.MAX_VALUE / 4, S5MineMobs.atk(ht, bm.rank) * MINE_HARPY_GUST_MULT), ht.elem()); // [2026-11-22] 돌풍 = 하피 속성
    }
    /** 몬스터 상태(화면 1초 턴) -- 하피 자리를 맞추고 {harpy: {hide, weak, move}, trees: [[번호, 상태, 불 쌓인 양]]}. */
    void mineHarpySync(S5MineMap map, long now, HashMap<String, Object> out) {
        S5MineMap.Mob bm = mineBossOf(map);
        if (bm != null && !mineBossDead(bm.type)) {
            int[] hz = mineHarpyNow(map, now);
            int bmax = S5MineMobs.hp(S5MineMobs.TYPES.get(bm.type), bm.rank);
            MobState bs = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, bm.id), k -> new MobState(bmax));
            synchronized (bs) { if (bs.deadUntil <= now) { bs.fx = hz[0]; bs.fy = hz[1]; bs.frozenUntil = now + 2_000L; } }
            HashMap<String, Object> hm = new HashMap<>();
            hm.put("hide", hz[2] == 1); hm.put("weak", hz[3] == 1); hm.put("move", hz[4] == 1); hm.put("low", mineHarpyLow(map, now));
            out.put("harpy", hm);
        }
        List<long[]> ts = new ArrayList<>();
        for (int i = 0; i < map.trees().size(); i++) { long[] t = MINE_TREES.get(i); ts.add(new long[] { i, mineTreeState(i, now), t == null ? 0 : Math.min(MINE_TREE_FIRE_NEED, t[0]) }); }
        out.put("trees", ts);
        out.put("treeNeed", MINE_TREE_FIRE_NEED);
    }

    // ---- [2026-11-07] 3환생 스킬 -- 왕도 검기 사범 옆 두 선생 ----
    // "3번째 환생부터는 검기 선생 옆에 다른 선생 두 명(주변 공격, 필살 공격)". 기존 관리자용 전체 공격은 없앴다(필살기로 대신).
    //  돌개베기는 MINE_WHIRL_OPEN, 필살기는 MINE_ULT_OPEN -- 열면 환생 MINE_SKILL3_RB번 이상(닫혀 있으면 관리자만).
    //  돌개베기(돌개 사범): 검기와 같은 재료/피해 -- 속성별 1~5단계(WHIRL_L{e} 배운 단계, WHIRL_A{e} 켠 단계), 공격할 때 내 둘레 8칸 몬스터에게 켠 속성마다
    //   공격력 x 단계 x 5% 속성 피해(상성 x1.5). 그림은 관리자 돌개바람(whirl.png). 결과는 검기 결과(auraMobs/auraDrops)에 합쳐 보낸다.
    //  필살기(필살 사범): 만검진(광역, ULT_L0)과 일섬참(단일, ULT_L1) 둘 다 배워 쓴다. 배울 때 은하수 조각 2/5/8/11/14개(레벨마다 따로), 쿨타임은 둘이 공유(2분).
    //   만검진: 쓴 자리(둘레 MINE_ULT_ZONE_R칸 = 13x13, [2026-11-08] 7x7에서 +3칸)에 5초 동안 검이 떨어지는 장판 -- 1초마다 공격력의 125%(+레벨당 20%) x 5번(화면이 1초마다 다음 타격을 부른다).
    //   일섬참: 화면에 보이는 가장 가까운 몬스터 하나에게 공격력의 700%(+레벨당 150%, [2026-11-08] 800 -> 700). 보이는 몬스터가 없으면 쿨타임을 쓰지 않는다.
    //   [2026-11-07] "체력 1에서 멈추는 것 없이 필살기로도 잡을 수 있게" -- 보스도 쓰러뜨린다(분신술 닌자 두목만 예외). 일섬참에 맞고 살아남은 몬스터는 5초 기절.
    //   관리자는 쿨타임 초기화 버튼(mineUltReset).
    // [2026-11-08] "필살 사범한테 스킬 배울 수 있게, 3환생부터는 무료로 1개 선택" -- 필살기만 연다(돌개베기는 아직 관리자만). 3환생 특전: 필살기 하나를 1레벨로 무료(한 번, ULT_RB_GIFT).
    static final boolean MINE_WHIRL_OPEN = true, MINE_ULT_OPEN = true; // [2026-11-08] "돌개도 열어 주고"
    static final int MINE_SKILL3_RB = 3;
    static final String MINE_ITEM_ULT_GIFT = "ULT_RB_GIFT";
    static final String MINE_ITEM_OPT_HD = "OPT_HD", MINE_ITEM_OPT_DMG = "OPT_DMG"; // [2026-11-21] 화면 옵션(계정에 저장)
    boolean mineWhirlOk(String userName, Map<String, Integer> items) { return isMineAdmin(userName) || (MINE_WHIRL_OPEN && mineRebirth(items) >= MINE_SKILL3_RB); }
    boolean mineUltOk(String userName, Map<String, Integer> items) { return isMineAdmin(userName) || (MINE_ULT_OPEN && mineRebirth(items) >= MINE_SKILL3_RB); }
    static final String MINE_SKILL3_NO = "이 기술은 " + MINE_SKILL3_RB + "번째 환생을 마친 사람에게만 가르치네.";

    /** [2026-11-07] 관리자 -- 검기/돌개베기(보스가 있는 속성)와 필살기 둘 다 배운 단계 5로. */
    static void mineAdminSkills(Map<String, Integer> items) {
        for (int e = 1; e <= 8; e++) {
            items.put(mineWhirlKey(false, e), MINE_AURA_MAX); // [2026-11-08] 돌개베기는 8속성 모두
            if (MINE_AURA_BOSS_ITEM[e] == null) continue;
            items.put(mineAuraKey(false, e), MINE_AURA_MAX);
        }
        items.put(mineUltKey(0), MINE_ULT_MAX);
        items.put(mineUltKey(1), MINE_ULT_MAX);
        items.put(mineUltKey(2), MINE_ULT_MAX); // [2026-11-21] 각성
    }
    static String mineWhirlKey(boolean active, int e) { return (active ? "WHIRL_A" : "WHIRL_L") + e; }
    static int[] mineWhirlActive(Map<String, Integer> items) {
        int[] a = new int[9];
        for (int e = 1; e <= 8; e++) a[e] = Math.max(0, Math.min(Math.min(items.getOrDefault(mineWhirlKey(true, e), 0), items.getOrDefault(mineWhirlKey(false, e), 0)), MINE_AURA_MAX));
        if (MINE_ITEM_BH_PEARL.equals(mineNeckCode(items))) a[S5MineMobs.EL_WATER] = MINE_AURA_MAX; // [2026-11-23] 베히모스의 진주목걸이 -- 공격할 때 둘레를 휘도는 물 회오리(물 돌개베기 5단계)
        return a;
    }
    static boolean mineWhirlOn(int[] w) { for (int e = 1; e <= 8; e++) if (w[e] > 0) return true; return false; }
    static HashMap<String, Object> mineWhirlInfo(Map<String, Integer> items) {
        HashMap<String, Object> m = new HashMap<>();
        int[] l = new int[9];
        for (int e = 1; e <= 8; e++) l[e] = Math.min(items.getOrDefault(mineWhirlKey(false, e), 0), MINE_AURA_MAX);
        m.put("l", l);
        m.put("a", mineWhirlActive(items));
        return m;
    }
    /** 돌개베기 -- 내 둘레 8칸 몬스터에게 켠 속성 피해. 결과를 검기 결과(auraMobs/auraDrops)에 합친다. 반환 {처치 수, 경험치}. */
    @SuppressWarnings("unchecked")
    long[] mineWhirlSweep(String userName, S5MineMap map, Map<String, Integer> items, double atkBase, int x, int y, long now, StringBuilder msg, HashMap<String, Object> out) {
        int[] w = mineWhirlActive(items);
        if (!mineWhirlOn(w)) return new long[] { 0, 0 };
        java.util.Map<Integer, Integer> cells = new java.util.HashMap<>();
        for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
            int lx = x + ox, ly = y + oy;
            if ((ox != 0 || oy != 0) && lx >= 0 && ly >= 0 && lx < map.w && ly < map.h) cells.put(ly * map.w + lx, 1);
        }
        if (S5MineMap.HARPY_NEST.equals(map.id) && (w[6] > 0 || w[2] > 0)) mineTreeFire(map, cells.keySet(), w[6], w[2], now, out); // [2026-11-08] 불 돌개베기 -- 나무에 불, [2026-11-11] 번개도
        long[] r = mineSweepCells(userName, map, items, cells, 8, -1, now, msg, out, (ot, dist) -> mineAuraHits(w, atkBase, ot, 1), "whirlMobs", "whirlDrops", "🌪️ 돌개베기로 ");
        Object wm = out.remove("whirlMobs"), wd = out.remove("whirlDrops");
        if (wm instanceof List && !((List<Object>) wm).isEmpty()) { List<Object> l = out.get("auraMobs") instanceof List ? new ArrayList<>((List<Object>) out.get("auraMobs")) : new ArrayList<>(); l.addAll((List<Object>) wm); out.put("auraMobs", l); }
        if (wd instanceof List && !((List<Object>) wd).isEmpty()) { List<Object> l = out.get("auraDrops") instanceof List ? new ArrayList<>((List<Object>) out.get("auraDrops")) : new ArrayList<>(); l.addAll((List<Object>) wd); out.put("auraDrops", l); }
        List<Integer> els = new ArrayList<>();
        for (int e = 1; e <= 8; e++) if (w[e] > 0) els.add(e);
        out.put("whirl", els); // 다른 사람 화면(돌개바람 색)
        return r;
    }
    @Override
    @Transactional
    public HashMap<String, Object> mineWhirlLearn(String userName, int el) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, "whirlmaster")) { out.put("message", "왕도 돌개 사범 앞에서만 배울 수 있어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        if (!mineWhirlOk(userName, items)) { out.put("message", MINE_SKILL3_NO); return out; }
        if (el < 1 || el > 8) return out; // [2026-11-08] "돌개는 모든 속성 열어 줘" -- 8속성 모두
        int cur = Math.min(items.getOrDefault(mineWhirlKey(false, el), 0), MINE_AURA_MAX), act = items.getOrDefault(mineWhirlKey(true, el), 0);
        if (cur >= MINE_AURA_MAX) { out.put("message", "이미 " + S5MineMobs.ELEM_NAMES[el] + " 돌개베기를 끝까지 익혔네."); return out; }
        int nx = cur + 1, n = mineAuraCost(nx);
        boolean bossEl = MINE_AURA_BOSS_ITEM[el] != null; // 보스가 없는 속성(물/바람/빛)은 보스 드랍템 대신 별빛석(같은 개수), 세공 바다 보석 없음
        String[] need = bossEl ? new String[] { MINE_AURA_BOSS_ITEM[el], S5MineMap.MOON_STONE_CODE, S5MineMap.seaCutCode(MINE_AURA_SEA[el]) } : new String[] { S5MineMap.ORE_CODES[5], S5MineMap.MOON_STONE_CODE };
        int[] qty = bossEl ? new int[] { n, n, MINE_AURA_SEA_QTY } : new int[] { n, n }; // 검기와 같은 재료
        for (int i = 0; i < need.length; i++) {
            if (dao.takeUserOre(userName, need[i], qty[i]) > 0) continue;
            for (int j = 0; j < i; j++) dao.addUserOre(userName, need[j], qty[j]);
            out.put("message", mineCodeName(need[0]) + " " + n + "개, " + S5MineMap.MOON_STONE_NAME + " " + n + "개" + (bossEl ? ", " + mineCodeName(need[2]) + " " + MINE_AURA_SEA_QTY + "개" : "") + "가 있어야 해요.");
            return out;
        }
        dao.upsertMineItem(userName, mineWhirlKey(false, el), nx);
        items.put(mineWhirlKey(false, el), nx);
        if (act == cur) { dao.upsertMineItem(userName, mineWhirlKey(true, el), nx); items.put(mineWhirlKey(true, el), nx); }
        out.put("ok", true);
        out.put("message", "🌪️ 돌개 사범: " + S5MineMobs.ELEM_NAMES[el] + " 돌개베기 " + nx + "단계를 익혔네! (둘레 8칸에 공격력의 " + nx * MINE_AURA_PCT + "% 속성 피해)");
        out.put("whirl", mineWhirlInfo(items));
        out.put("ores", mineOreList(userName));
        return out;
    }
    @Override
    @Transactional
    public HashMap<String, Object> mineWhirlSet(String userName, int el, int lv) {
        HashMap<String, Object> out = new HashMap<>();
        if (!mineAccess(userName)) return mineClosed();
        Map<String, Integer> items = mineItems(userName);
        int have = Math.min(items.getOrDefault(mineWhirlKey(false, el), 0), MINE_AURA_MAX);
        if (el < 1 || el > 8 || lv < 0 || lv > have) { out.put("ok", false); out.put("message", "아직 배우지 않은 단계예요."); return out; }
        dao.upsertMineItem(userName, mineWhirlKey(true, el), lv);
        items.put(mineWhirlKey(true, el), lv);
        out.put("ok", true);
        out.put("whirl", mineWhirlInfo(items));
        return out;
    }

    // [2026-11-08] "만검진에 4히트마다 방어 감소(받는 피해 증가) 10%씩" -- 만검진 한 번(1초, 4타)마다 맞은 몬스터의 받는 피해 +10%(최대 50%),
    //  마지막으로 쌓인 뒤 MINE_VULN_MS 동안. 내 공격/검기/돌개베기/필살기/용병 피해 모두에 곱한다(쌓이기 전 피해부터 -- 만검진 첫 타격엔 없음).
    static final int MINE_ULT_VULN_PCT = 10, MINE_VULN_MAX = 50;
    static final long MINE_VULN_MS = 5_000L;
    static double mineVulnMul(MobState s, long now) {
        double v = s != null && s.vulnUntil > now && s.vulnPct > 0 ? 1 + s.vulnPct / 100.0 : 1.0;
        if (s != null && s.kraken) v *= s.stunUntil > now ? MINE_KRK_STUN_MUL : MINE_KRK_ARMOR; // [2026-11-18] 두꺼운 크라켄 -- 기절하면 2배
        return v;
    }
    static List<int[]> mineVulnApply(List<int[]> hits, double vul) { if (hits != null && vul != 1) for (int[] h : hits) h[1] = Math.max(h[1] > 0 ? 1 : 0, (int) Math.round(h[1] * vul)); return hits; } // [2026-11-23] 1보다 작은 배율(크라켄·탑 수문장 갑피)도 -- 예전엔 늘리는 것만
    static void mineVulnAdd(MobState s, int pct, long now) { s.vulnPct = Math.min(MINE_VULN_MAX, (s.vulnUntil > now ? s.vulnPct : 0) + pct); s.vulnUntil = now + MINE_VULN_MS; }
    static final String[] MINE_ULT_NAME = { "만검진", "일섬참", "각성" };
    // [2026-11-21] "필살기 1종 추가 -- 7초간 모든 데미지 2배(적에게 주는 피해), 현재 체력 2배(최대 체력을 잠시 넘는다), 레벨당 지속 시간 1초씩, 키는 V"(시안 승인: 삼태극 위 노란 불꽃).
    //  ULT_L2. 지속 = MINE_KAK_MS + 레벨당 MINE_KAK_LV_MS(1레벨 7초 ~ 5레벨 11초). 쿨타임은 다른 필살기와 함께(2분), 배우는 비용도 같다(은하수 조각 2/5/8/11/14).
    //  피해 2배 = mineUnityAtk에 곱한다. 체력 2배 = 쓸 때 지금 체력 x2, 그동안 최대 체력(mineHpCap)도 2배라 잘리지 않고, 회복은 원래 최대(mineHpCapN)까지만.
    //  끝나거나 쓰러지면 넘친 체력은 원래 최대로(읽을 때 잘린다). 상태는 메모리(MINE_KAK = 끝나는 시각) -- 배포하면 끝난다.
    static final long MINE_KAK_MS = 7_000L, MINE_KAK_LV_MS = 1_000L;
    static final double MINE_KAK_DMG = 1.5; // [2026-11-22] "체력/공격력 2배 -> 1.5배"
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_KAK = new java.util.concurrent.ConcurrentHashMap<>();
    static long mineKakUntil(String u) { Long v = u == null ? null : MINE_KAK.get(u); return v == null ? 0L : v; }
    static boolean mineKakOn(String u) { return mineKakUntil(u) > System.currentTimeMillis(); }
    HashMap<String, Object> mineKakCast(String userName, HashMap<String, Object> row, Map<String, Integer> items, int lv, int ulv, long now, HashMap<String, Object> out) {
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        MINE_KAK.remove(userName);
        int hp0 = mineHpNow(row, now, lock, rb);
        long until = now + MINE_KAK_MS + MINE_KAK_LV_MS * (Math.max(1, ulv) - 1);
        MINE_KAK.put(userName, until);
        int hp1 = (int) Math.min((long) mineHpCap(lv, lock, rb), (long) hp0 * 3 / 2); // [2026-11-22] 지금 체력 2배 -> 1.5배
        dao.updateMineCombat(userName, lv, exp, hp1, now, 0);
        out.put("ok", true);
        out.put("k", 2);
        out.put("kakUntil", until);
        out.put("combat", mineCombatInfo(lv, exp, hp1, now, mineEngaged(userName, now), lock, rb));
        out.put("ult", mineUltInfo(userName, items));
        out.put("message", "🔥 각성! " + ((until - now) / 1000) + "초 동안 주는 피해 1.5배 · 체력 1.5배");
        minePresUlt(userName, 2, 0, out);
        return out;
    }
    static final int MINE_ULT_MAX = 5, MINE_ULT_ZONE_R = 6, MINE_ULT_TICKS = 5, MINE_ULT_ZONE_MAXT = 40, /* [2026-11-08] "X 스킬 범위 +3칸" -- 반경 3(7x7) -> 6(13x13) */ MINE_ULT_ZONE_SPLIT = 4;
    static final int[] MINE_ULT_COST = { 0, 2, 5, 8, 11, 14 }; // n레벨 배우기 = 은하수 조각
    // [2026-11-07] 보스 허수아비 -- 5초 안 맞으면 다 차고, 0이 되면 3초 동안 못 때린다(쓰러진 것처럼 사라졌다 다 찬 채로).
    static final long MINE_SIM_HEAL_MS = 5_000L, MINE_SIM_DOWN_MS = 3_000L;
    static final long MINE_ULT_GAP_MS = 120_000L, MINE_ULT_TICK_MS = 1_000L, MINE_ULT_STUN_MS = 2_000L, MINE_ULT_STUN_LV_MS = 1_000L; // [2026-11-07] "일섬참 맞은 대상은 기절 5초" -> [2026-11-08] "2초(레벨당 1초씩 증가)" -- 1레벨 2초 ~ 5레벨 6초
    static String mineUltKey(int k) { return "ULT_L" + k; }
    static int mineUltLv(Map<String, Integer> items, int k) { return Math.max(0, Math.min(MINE_ULT_MAX, items.getOrDefault(mineUltKey(k), 0))); }
    static double mineUltPct(int k, int lv) { return k == 0 ? 1.00 + 0.16 * (lv - 1) : 7.0 + 1.5 * (lv - 1); } // [2026-11-16] "만검진 데미지 20% 감소" -- 125%(+레벨당 20%) -> 100%(+레벨당 16%) // [2026-11-08] "일섬참 배율 100% 줄이자" -- 800% -> 700%(+레벨당 150%) // 만검진 = 한 번(1초)마다, 일섬참 = 한 방
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_ULT_AT = new java.util.concurrent.ConcurrentHashMap<>(); // 마지막으로 쓴 시각(쿨타임 공유)
    static final class UltZone { String mapId; int cx, cy, lv, n; long last; }
    static final java.util.concurrent.ConcurrentHashMap<String, UltZone> MINE_ULT_ZONE = new java.util.concurrent.ConcurrentHashMap<>();
    HashMap<String, Object> mineUltInfo(String userName, Map<String, Integer> items) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("l", new int[] { mineUltLv(items, 0), mineUltLv(items, 1), mineUltLv(items, 2) }); // [2026-11-21] 각성
        Long at = MINE_ULT_AT.get(userName);
        m.put("cd", at == null ? 0 : Math.max(0, mineUltGap(items) - (System.currentTimeMillis() - at))); // [2026-11-20] 신속의 장갑
        m.put("gap", mineUltGap(items));
        m.put("gift", items.getOrDefault(MINE_ITEM_ULT_GIFT, 0) > 0 ? 2 : 1); // [2026-11-08] 3환생 특전 -- 1 = 받을 수 있음, 2 = 받음
        return m;
    }
    /** [2026-11-08] 3환생 특전 -- 필살 사범 앞에서 아직 안 배운 필살기 하나를 1레벨로 무료(한 번). */
    @Override
    @Transactional
    public HashMap<String, Object> mineUltGift(String userName, int k) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, "ultmaster")) { out.put("message", "왕도 필살 사범 앞에서만 받을 수 있어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        if (!mineUltOk(userName, items)) { out.put("message", MINE_SKILL3_NO); return out; }
        if (items.getOrDefault(MINE_ITEM_ULT_GIFT, 0) > 0) { out.put("message", "3환생 특전은 이미 받았어요."); return out; }
        if (k < 0 || k > 2) return out; // [2026-11-21] 각성도
        if (mineUltLv(items, k) > 0) { out.put("message", "이미 배운 필살기예요. 아직 안 배운 쪽을 골라 주세요."); return out; }
        dao.upsertMineItem(userName, mineUltKey(k), 1);
        dao.upsertMineItem(userName, MINE_ITEM_ULT_GIFT, 1);
        items.put(mineUltKey(k), 1);
        items.put(MINE_ITEM_ULT_GIFT, 1);
        out.put("ok", true);
        out.put("message", "🎁 필살 사범: 세 번째 환생을 축하하네! " + MINE_ULT_NAME[k] + " 1레벨을 거저 가르쳐 주지.");
        out.put("ult", mineUltInfo(userName, items));
        return out;
    }
    @Override
    @Transactional
    public HashMap<String, Object> mineUltLearn(String userName, int k) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, "ultmaster")) { out.put("message", "왕도 필살 사범 앞에서만 배울 수 있어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        if (!mineUltOk(userName, items)) { out.put("message", MINE_SKILL3_NO); return out; }
        if (k < 0 || k > 2) return out; // [2026-11-21] 각성도
        int cur = mineUltLv(items, k);
        if (cur >= MINE_ULT_MAX) { out.put("message", "이미 " + MINE_ULT_NAME[k] + "을(를) 끝까지 익혔네."); return out; }
        int nx = cur + 1, n = MINE_ULT_COST[nx];
        if (dao.takeUserOre(userName, S5MineMap.GALAXY_CODE, n) <= 0) { out.put("message", S5MineMap.GALAXY_NAME + " " + n + "개가 있어야 해요."); return out; }
        dao.upsertMineItem(userName, mineUltKey(k), nx);
        items.put(mineUltKey(k), nx);
        out.put("ok", true);
        out.put("message", "⚔️ 필살 사범: " + MINE_ULT_NAME[k] + " " + nx + "레벨을 익혔네! (" + (k == 2 ? ((MINE_KAK_MS + MINE_KAK_LV_MS * (nx - 1)) / 1000) + "초 동안 주는 피해 1.5배 · 체력 1.5배" : k == 0 ? "5초 동안 1초마다 공격력의 " + Math.round(mineUltPct(0, nx) * 100) + "%" : "공격력의 " + Math.round(mineUltPct(1, nx) * 100) + "% 한 방") + ")");
        out.put("ult", mineUltInfo(userName, items));
        out.put("ores", mineOreList(userName));
        return out;
    }
    @Override
    public HashMap<String, Object> mineUltReset(String userName) {
        HashMap<String, Object> out = new HashMap<>();
        if (!isMineAdmin(userName)) { out.put("ok", false); out.put("message", "관리자만 쓸 수 있어요."); return out; }
        MINE_ULT_AT.remove(userName);
        out.put("ok", true);
        out.put("message", "⟳ 필살기 쿨타임을 초기화했어요.");
        out.put("ult", mineUltInfo(userName, mineItems(userName)));
        return out;
    }
    /** 필살기 -- k 0 만검진(t 0 = 쓰기, 1~4 = 장판의 다음 타격), 1 일섬참. */
    @Override
    @Transactional
    public HashMap<String, Object> mineUlt(String userName, String mapId, String ver, int x, int y, int k, int t) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (k < 0 || k > 2) return out; // [2026-11-21] 2 = 각성
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        if (mineFrozen(userName, now) != null) { out.put("frozen", true); out.put("message", MINE_FROZEN_BLOCK); return out; }
        HashMap<String, Object> row = mineRowLocked(userName);
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int water = mineWalkMode(items);
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) { out.put("reload", true); out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요."); return out; }
        if (!mineWalk(map, x, y, water) || !mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        int lv = mineLv(row);
        String block = mineMapBlock(map, items, lv);
        if (block != null) { out.put("message", block); return out; }
        int ulv;
        UltZone z = null;
        Long prevAt = MINE_ULT_AT.get(userName);
        if (k == 0 && t > 0) { // 장판의 다음 타격
            z = MINE_ULT_ZONE.get(userName);
            if (z == null || !map.id.equals(z.mapId) || z.n >= MINE_ULT_TICKS) { out.put("ended", true); return out; }
            if (now - z.last < MINE_ULT_TICK_MS - 150) { out.put("tooFast", true); return out; }
            ulv = z.lv;
        } else {
            if (!mineUltOk(userName, items)) { out.put("message", "필살기는 " + MINE_SKILL3_RB + "번째 환생부터 쓸 수 있어요."); return out; }
            ulv = mineUltLv(items, k);
            if (ulv <= 0) { out.put("message", "아직 " + MINE_ULT_NAME[k] + "을(를) 배우지 않았어요. (왕도 필살 사범)"); return out; }
            if (prevAt != null && now - prevAt < mineUltGap(items)) { out.put("message", "⏳ 필살기는 " + ((mineUltGap(items) - (now - prevAt)) / 1000 + 1) + "초 뒤에 다시 쓸 수 있어요."); /* [2026-11-20] 신속의 장갑 */ out.put("ult", mineUltInfo(userName, items)); return out; }
            MINE_ULT_AT.put(userName, now);
            if (k == 2) return mineKakCast(userName, row, items, lv, ulv, now, out); // [2026-11-21] 각성
            if (k == 0) { z = new UltZone(); z.mapId = map.id; z.cx = x; z.cy = y; z.lv = ulv; MINE_ULT_ZONE.put(userName, z); }
        }
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb);
        final double atkBase = mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items), pct = mineUltPct(k, ulv) * mineUltGloveMul(items); // [2026-11-20] 필살의 장갑
        java.util.Map<Integer, Integer> cells = new java.util.HashMap<>();
        int maxT;
        if (k == 0) {
            for (int yy = Math.max(0, z.cy - MINE_ULT_ZONE_R); yy <= Math.min(map.h - 1, z.cy + MINE_ULT_ZONE_R); yy++)
                for (int xx = Math.max(0, z.cx - MINE_ULT_ZONE_R); xx <= Math.min(map.w - 1, z.cx + MINE_ULT_ZONE_R); xx++) cells.put(yy * map.w + xx, Math.max(Math.abs(xx - z.cx), Math.abs(yy - z.cy)));
            maxT = MINE_ULT_ZONE_MAXT;
            z.n++; z.last = now;
            if (z.n >= MINE_ULT_TICKS) MINE_ULT_ZONE.remove(userName, z);
            HashMap<String, Object> zo = new HashMap<>();
            zo.put("x", z.cx); zo.put("y", z.cy); zo.put("n", z.n); zo.put("r", MINE_ULT_ZONE_R);
            out.put("zone", zo);
        } else {
            int[] v = MINE_MERC_VIEW.get(userName);
            int hw = v == null ? MINE_MERC_VIEW_W : v[0], hh = v == null ? MINE_MERC_VIEW_H : v[1];
            for (int yy = Math.max(0, y - hh); yy <= Math.min(map.h - 1, y + hh); yy++)
                for (int xx = Math.max(0, x - hw); xx <= Math.min(map.w - 1, x + hw); xx++) cells.put(yy * map.w + xx, Math.abs(xx - x) + Math.abs(yy - y));
            maxT = 1;
        }
        StringBuilder msg = new StringBuilder();
        long[] r = mineSweepCells(userName, map, items, cells, maxT, -1, now, msg, out, (ot, dist) -> {
            List<int[]> l = new ArrayList<>();
            int nh = k == 0 ? MINE_ULT_ZONE_SPLIT : 1; // [2026-11-07] "만검진은 초당 1회 말고 4번 정도로 나눠서" -- 화면은 0.25초 간격으로 띄운다
            for (int q = 0; q < nh; q++) {
                boolean crit = RND.nextInt(100) < 10;
                l.add(new int[] { 0, Math.max(1, (int) Math.round(atkBase * pct / nh * (0.9 + RND.nextDouble() * 0.2) * (crit ? 1.6 : 1.0) * mineElemMul(items, ot))), crit ? 1 : 0 });
            }
            return l;
        }, "ultMobs", "ultDrops", k == 0 ? "⚔️ 만검진으로 " : "⚔️ 일섬참으로 ", true, k == 1 ? MINE_ULT_STUN_MS + MINE_ULT_STUN_LV_MS * (ulv - 1) : 0L, k == 0 ? MINE_ULT_VULN_PCT : 0);
        if (k == 1) out.put("stunMs", MINE_ULT_STUN_MS + MINE_ULT_STUN_LV_MS * (ulv - 1));
        Object um = out.get("ultMobs");
        if (k == 1 && (!(um instanceof List) || ((List<?>) um).isEmpty())) { // 보이는 몬스터가 없다 -- 쿨타임을 돌려준다
            if (prevAt == null) MINE_ULT_AT.remove(userName); else MINE_ULT_AT.put(userName, prevAt);
            out.put("message", "화면에 벨 몬스터가 없어요.");
            out.put("ult", mineUltInfo(userName, items));
            return out;
        }
        mineMercShare(userName, items, r[1], lv, r[0], out);
        long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, r[1], false, out);
        int px = x, py = y;
        if (k == 1) { // [2026-11-08] "일섬 사용 시 맞춘 대상에게 바싹 붙게" -- 대상(보스는 2x2) 둘레 8칸 중 걸을 수 있고 지금 자리에서 가장 가까운 칸으로
            Object m0 = ((List<?>) um).get(0);
            long[] rw = m0 instanceof Map && ((Map<?, ?>) m0).get("row") instanceof long[] ? (long[]) ((Map<?, ?>) m0).get("row") : null;
            Object mid = m0 instanceof Map ? ((Map<?, ?>) m0).get("id") : null;
            if (rw != null && mid instanceof Integer) {
                S5MineMobs.Type tt = S5MineMobs.TYPES.get(map.mobs().get((Integer) mid).type);
                Object hc = ((Map<?, ?>) m0).get("cell"); // [2026-11-10] "일반 몬스터에 일섬참 쓰면 0,0으로 이동" -- 한 방에 쓰러진 몬스터는 row 자리(fx, fy)가 0,0일 수 있어 맞은 칸으로
                int tx = hc instanceof Integer ? (Integer) hc % map.w : (int) rw[3], ty = hc instanceof Integer ? (Integer) hc / map.w : (int) rw[4], fw = tt.foot(), best = 1 << 30;
                for (int yy = ty - 1; yy <= ty + fw; yy++) for (int xx = tx - 1; xx <= tx + fw; xx++) {
                    if (xx >= tx && xx < tx + fw && yy >= ty && yy < ty + fw) continue; // 몬스터 칸
                    if (!mineWalk(map, xx, yy, water)) continue;
                    int d = Math.abs(xx - x) + Math.abs(yy - y);
                    if (d < best) { best = d; px = xx; py = yy; }
                }
                if (px != x || py != y) { out.put("dashX", px); out.put("dashY", py); }
            }
        }
        dao.updateMinePos(userName, map.id, px, py, now);
        dao.updateMineCombat(userName, (int) g[0], g[1], (int) g[2], now, (int) r[0]);
        out.put("ok", true);
        out.put("k", k);
        out.put("levelUp", (int) g[4]);
        out.put("combat", mineCombatInfo((int) g[0], g[1], (int) g[2], now, mineEngaged(userName, now), (int) g[3], rb));
        out.put("ult", mineUltInfo(userName, items));
        out.put("message", msg.length() > 0 ? msg.toString() : null);
        minePresUlt(userName, k, t, out); // [2026-11-08] 다른 사람 화면에도 필살기
        return out;
    }
    /** [2026-11-08] "필살 스킬이 다른 사람한테 보이게" -- 일반 공격처럼 위치 알림에 실어 보낸다. u = 0 만검진(z = [x, y, 반경], 쓸 때만) / 1 일섬참, h = 맞은 몬스터 피해 숫자. */
    @SuppressWarnings("unchecked")
    void minePresUlt(String userName, int k, int t, HashMap<String, Object> out) {
        try {
            MinePresence me = MINE_PRESENCE.get(userName);
            if (me == null) return;
            List<int[]> h = new ArrayList<>();
            Object um = out.get("ultMobs");
            if (um instanceof List) for (Object o : (List<Object>) um) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                Object row = m.get("row"), hl = m.get("hits");
                if (!(row instanceof long[]) || !(hl instanceof List)) continue;
                long[] rw = (long[]) row;
                for (Object q : (List<Object>) hl) if (q instanceof int[]) { int[] a = (int[]) q; h.add(new int[] { (int) rw[3], (int) rw[4], a[1], a[2] == 1 ? 1 : 0 }); }
            }
            if (h.size() > 24) h = h.subList(0, 24);
            HashMap<String, Object> a = new HashMap<>();
            long now = System.currentTimeMillis();
            a.put("s", MINE_PRES_ATK_SEQ.incrementAndGet());
            a.put("at", now);
            a.put("d", me.dir);
            a.put("h", h);
            if (t == 0) a.put("u", k); // 쓸 때만 그림(장판/斬), 만검진의 다음 타격은 숫자만
            Object zo = out.get("zone");
            if (k == 0 && t == 0 && zo instanceof Map) { Map<String, Object> z = (Map<String, Object>) zo; a.put("z", new int[] { (Integer) z.get("x"), (Integer) z.get("y"), (Integer) z.get("r") }); }
            synchronized (me.atks) {
                me.atks.addLast(a);
                while (me.atks.size() > 6 || (!me.atks.isEmpty() && now - (Long) me.atks.peekFirst().get("at") > MINE_PRES_ATK_MS)) me.atks.removeFirst();
            }
        } catch (Exception ignore) { } // 화면 표시용
    }

    /** [2026-11-04] 옆에 몬스터가 없을 때 검기만 날리기(공격 버튼) -- mineAttack(mobId = -1). */
    HashMap<String, Object> mineAuraAttack(String userName, String mapId, String ver, int x, int y, String dir) {
        long now = System.currentTimeMillis();
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        Long last = MINE_LAST_ATTACK.get(userName);
        if (last != null && now - last < MINE_ATTACK_GAP_MS) { out.put("tooFast", true); return out; }
        MINE_LAST_ATTACK.put(userName, now);
        if (mineFrozen(userName, now) != null) { out.put("frozen", true); out.put("message", MINE_FROZEN_BLOCK); return out; }
        HashMap<String, Object> row = mineRowLocked(userName);
        S5MineMap map = mineMapOf(row);
        Map<String, Integer> items = mineItems(userName);
        int water = mineWalkMode(items);
        int[] pos = minePos(row, map, water);
        if ((mapId != null && !mapId.isEmpty() && !map.id.equals(mapId)) || mineStaleVersion(map, ver)) { out.put("reload", true); out.put("message", "다른 곳에서 위치가 바뀌었어요. 다시 불러올게요."); return out; }
        if (!mineWalk(map, x, y, water) || !mineMoveOk(pos, longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) {
            out.put("resyncX", pos[0]);
            out.put("resyncY", pos[1]);
            out.put("message", "이동 기록이 맞지 않아 마지막 저장 위치(" + pos[0] + ", " + pos[1] + ")로 돌아갑니다.");
            return out;
        }
        int lv = mineLv(row);
        String block = mineMapBlock(map, items, lv);
        if (block != null) { out.put("message", block); return out; }
        int[] aura = mineAuraActive(items);
        int range = mineAuraRange(aura);
        boolean whirlOn = mineWhirlOn(mineWhirlActive(items)); // [2026-11-07] 돌개베기
        if (range <= 0 && !whirlOn) { out.put("message", "켜 둔 검기가 없어요. (인벤토리 스킬 탭)"); return out; }
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb);
        mineBerserkLow(items, hp, mineHpMax(lv, rb)); // [2026-11-04] 광전사의 장갑 2단계
        StringBuilder msg = new StringBuilder();
        java.util.Set<Integer> skA = new java.util.HashSet<>(), skW = new java.util.HashSet<>(); // [2026-11-22] 검기/돌개 겹치면 센 쪽만
        mineAuraWhirlPick(userName, map, items, aura, range, x, y, dir, -1, now, skA, skW);
        long[] r, wr;
        try {
            MINE_SWEEP_SKIP.set(skA);
            r = mineAuraSweep(userName, map, items, aura, range, mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items), x, y, dir, -1, now, msg, out);
            MINE_SWEEP_SKIP.set(skW);
            wr = mineWhirlSweep(userName, map, items, mineAtkP(lv, items) * mineAtkMul(items) * mineUnityAtk(userName) + mineWeaponFlat(items), x, y, now, msg, out); // [2026-11-07] 돌개베기
        } finally { MINE_SWEEP_SKIP.remove(); }
        r = new long[] { r[0] + wr[0], r[1] + wr[1] };
        mineMercShare(userName, items, r[1], lv, r[0], out); // [2026-11-06] 용병 경험치(검기만)
        long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, r[1], false, out);
        int kh = mineRegenKillHeal(items, (int) r[0], mineHpMax((int) g[0], rb)); // [2026-11-04] 재생의 장갑 2단계
        if (kh > 0) { g[2] = mineHealTo((int) g[2], kh, (int) g[0], (int) g[3], rb); out.put("killHeal", kh); }
        if (mineJustDied(userName)) g[2] = mineHpCapN((int) g[0], (int) g[3], rb); // [2026-11-17] 다른 요청에서 방금 쓰러졌으면 -- 위치는 그대로(부활한 곳), 체력은 부활 체력
        else dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, (int) g[0], g[1], (int) g[2], now, (int) r[0]);
        out.put("ok", true);
        out.put("auraOnly", true);
        out.put("levelUp", (int) g[4]);
        out.put("combat", mineCombatInfo((int) g[0], g[1], (int) g[2], now, mineEngaged(userName, now), (int) g[3], rb));
        if (msg.length() > 0) out.put("message", msg.toString());
        minePresAtk(userName, dir, aura, out); // [2026-11-07] 다른 사람 화면에 검기
        return out;
    }

    /** [2026-11-04] 왕도 검기 사범 -- 속성 el의 검기 다음 단계 배우기(보스 드랍템 + 달빛석). 켜 둔 단계가 배운 단계와 같았으면 새 단계로 함께 올린다. */
    @Override
    @Transactional
    public HashMap<String, Object> mineAuraLearn(String userName, int el) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, "swordmaster")) { out.put("message", "왕도 검기 사범 앞에서만 배울 수 있어요."); return out; }
        if (el < 1 || el > 8 || MINE_AURA_BOSS_ITEM[el] == null) { out.put("message", "그 속성의 검기는 아직 가르칠 수 없네. (그 속성의 보스가 나타나면)"); return out; }
        Map<String, Integer> items = mineItems(userName);
        int cur = Math.min(items.getOrDefault(mineAuraKey(false, el), 0), MINE_AURA_MAX), act = items.getOrDefault(mineAuraKey(true, el), 0);
        if (cur >= MINE_AURA_MAX) { out.put("message", "이미 " + S5MineMobs.ELEM_NAMES[el] + " 검기를 끝까지 익혔네."); return out; }
        int nx = cur + 1, n = mineAuraCost(nx);
        String[] need = { MINE_AURA_BOSS_ITEM[el], S5MineMap.MOON_STONE_CODE, S5MineMap.seaCutCode(MINE_AURA_SEA[el]) };
        int[] qty = { n, n, MINE_AURA_SEA_QTY }; // [2026-11-04] 세공 바다 보석은 단계와 상관없이 5개
        for (int i = 0; i < need.length; i++) {
            if (dao.takeUserOre(userName, need[i], qty[i]) > 0) continue;
            for (int j = 0; j < i; j++) dao.addUserOre(userName, need[j], qty[j]); // 앞에서 가져간 것은 돌려준다
            out.put("message", mineCodeName(need[0]) + " " + n + "개, " + S5MineMap.MOON_STONE_NAME + " " + n + "개, " + mineCodeName(need[2]) + " " + MINE_AURA_SEA_QTY + "개가 있어야 해요.");
            return out;
        }
        dao.upsertMineItem(userName, mineAuraKey(false, el), nx);
        items.put(mineAuraKey(false, el), nx);
        if (act == cur) { dao.upsertMineItem(userName, mineAuraKey(true, el), nx); items.put(mineAuraKey(true, el), nx); }
        out.put("ok", true);
        out.put("message", "🌀 검기 사범: " + S5MineMobs.ELEM_NAMES[el] + " 검기 " + nx + "단계를 익혔네! (공격력의 " + nx * MINE_AURA_PCT + "% 속성 피해, 사거리 " + nx + "칸)");
        out.put("aura", mineAuraInfo(items));
        out.put("ores", mineOreList(userName));
        return out;
    }

    // ---- [2026-11-05] 용병(동료) -- 왕도 용병소 ----
    // "플레이어를 따라다니는 동료, 전투 땐 흩어져 같이 공격" + "왕도 7시 방향 용병소, 피해는 동료 고유의 공격력 수치, 처음 한 명 데리고 시작,
    //  동료는 체력이 있고 다 되면 플레이어 뒤에서 잠시 쉰다". 모두 견습 용병(0단계)을 데리고 시작하고, 용병소에서 더 센 용병으로 바꿔 고용한다(한 명).
    //  단계마다 고유 공격력/체력 = 그 레벨 플레이어의 40% / 100% (MINE_MERC_LV). 따라다니기/흩어지기는 화면이 그리고, 싸움은 전투 턴(1초)에 서버가:
    //  플레이어와 싸우는 몬스터(인식) 중 플레이어 MINE_MERC_REACH칸 안에서 하나를 골라(전에 치던 것 우선) 공격 1번, 그 몬스터의 반격(1.5초마다)은 용병이 맞는다.
    //  처치하면 경험치/드랍은 플레이어 것(보스는 검기처럼 마지막 1은 못 깎음). 체력이 0이면 MINE_MERC_REST_MS 동안 뒤에서 쉬고 가득 차서 돌아온다.
    //  싸우지 않을 땐 초당 최대 체력의 5%씩 찬다. 체력은 메모리(MINE_MERC, 서버 재시작 = 가득).
    // [2026-11-06] 용병 -- "궁수/마법사/전사 3개로, 직업별 외형, 원거리 공격, 공격 이펙트" + "레벨과 경험치는 따로, 스탯은 플레이어 레벨에 따른 스탯과 동일, 환생 없음,
    //  처음 계약 때 레벨 5~30, 메뉴에 용병 관리(최대 3칸, 직업별)" + "경험치는 플레이어가 얻는 경험치의 1%(레벨 차이 보정), 장비는 없음(기본 칼/활/지팡이, 기본 갑옷),
    //  치장은 직업별로, 스킬은 불/물/번개 -- 계약 때 특화 속성을 무작위로, 항상 그 속성으로 공격, 20레벨마다 강화(전사 검기 +1단계 / 마법사 범위 / 궁수 한 명 피해)".
    //  직업마다 한 명씩 계약(왕도 용병소) -- MERC{c}_LV / _EXP / _EL(속성) / _NM(이름 번호 = 모습), 데리고 다니는 건 한 명(MERC_ON = 직업 + 1, 0 = 없음).
    //  공격력 = mineAtk(용병 레벨) x 직업 배수(전사 1.2 / 궁수 1.0 / 마법사 0.8), 체력 = mineHpMax(용병 레벨, 0). 상성 몬스터에겐 x1.5(무기 속성 부여와 같은 표).
    //  단계 s = 1 + 레벨/20(최대 5): 전사 = 속성 검기 s단계(대상 + 대상 둘레 s칸의 싸우는 몬스터에게 공격력 s x 5%), 궁수 = 한 명 피해 +20% x s,
    //  마법사 = 불: 대상 둘레 s칸 폭발(s >= 3 메테오) / 번개: s+1마리에게 튕기는 체인(3칸 안, 85%씩) / 물: 나 -> 대상 사이 길(1칸) + 대상 둘레 (s+1)/2칸 토네이도. 둘레는 70%.
    //  반격은 전사 1.5초 / 원거리 3초마다. 경험치 = 내가 얻은 경험치 x 1%(몬스터가 용병보다 10레벨 넘게 높으면 1레벨마다 -3%, 최소 5%), 한 번에 그 레벨 필요치의 10%까지.
    static final String MINE_ITEM_MERC_ON = "MERC_ON";
    static final String MINE_ITEM_MERC_MODE = "MERC_MODE"; // [2026-11-06] 0 협공(기본) / 1 선공
    // [2026-11-06] "급여(동괴 2 / 은괴 2 / 금괴 2 / 미스릴괴 1 중 하나, 매번 무작위)를 1일마다 줘야 계약 유지 -- 선금 또는 자동 차감, 차감될 때 안내, 처음 계약부터,
    //  미접속 시에도 시간이 흐른다" -- MERC{c}_PAID = 급여가 치러진 끝 시각(분, epoch), MERC{c}_SAL = 다음에 낼 급여(MINE_MERC_SALARY 번호), MERC{c}_MANUAL = 1이면 자동 차감 끔.
    //  끝 시각이 지나면(접속 안 한 동안 포함) 다음 요청 때 하루치씩 자동으로 낸다(한 번에 최대 MINE_MERC_PAY_LOOP일). 모자라면 그 용병은 쉬고(따라오지 않음), 용병 관리에서 주면 그때부터 하루.
    //  선금은 MINE_MERC_PREPAY_DAYS일 앞까지. 계약 때 첫날 급여를 낸다. 계약 후보(직업마다 하나 -- 이름/레벨/속성/첫 급여)는 MERC{c}_O* 에 두고 계약하면 새로 뽑는다.
    static final String[][][] MINE_MERC_SALARY = { { mineBar(0, 2) }, { mineBar(2, 2) }, { mineBar(3, 2) }, { mineBar(4, 1) } };
    static final int MINE_MERC_DAY_MIN = 1440, MINE_MERC_PREPAY_DAYS = 7, MINE_MERC_PAY_LOOP = 30;
    // [2026-11-06] "레벨 20 구간마다 요구 급여가 높아지게, 최대 5개의 괴까지, 가끔 세공 보석" -- SAL 0~3 = 괴(동 2 / 은 2 / 금 2 / 미스릴 1개 + 레벨/20개, 최대 5개),
    //  SAL 10~ = 세공된 보석 1개(GEM_CODES 번호 + 10, 레벨이 높을수록 귀한 보석까지). 뽑을 때 MINE_MERC_SAL_CUT_PCT%가 세공된 보석.
    static final int MINE_MERC_SAL_CUT = 10, MINE_MERC_SAL_CUT_PCT = 10, MINE_MERC_BAR_MAX = 5;
    static int mineMercRollSal(int lv) {
        if (RND.nextInt(100) < MINE_MERC_SAL_CUT_PCT) return MINE_MERC_SAL_CUT + RND.nextInt(Math.min(S5MineMap.GEM_CODES.length, 3 + 2 * Math.min(3, Math.max(0, lv) / 20)));
        int k = RND.nextInt(MINE_MERC_SALARY.length);
        return k == 3 && lv >= MINE_MERC_MITHRIL2_LV && RND.nextBoolean() ? MINE_MERC_SAL_MITHRIL2 : k;
    }
    // [2026-11-06] "미스릴괴는 40레벨 이상~99레벨일 때 1~2개" -- 미스릴괴는 레벨 구간으로 늘지 않고 1개, 40레벨부터는 뽑을 때 반반으로 2개(SAL 4).
    static final int MINE_MERC_MITHRIL2_LV = 40, MINE_MERC_SAL_MITHRIL2 = 4;
    static String[][] mineMercSalCost(int sal, int lv) {
        if (sal >= MINE_MERC_SAL_CUT) return new String[][] { mineCut(Math.floorMod(sal - MINE_MERC_SAL_CUT, S5MineMap.GEM_CODES.length), 1) };
        if (sal == MINE_MERC_SAL_MITHRIL2 || sal == 3) return new String[][] { mineBar(4, sal == MINE_MERC_SAL_MITHRIL2 && lv >= MINE_MERC_MITHRIL2_LV ? 2 : 1) };
        String[] b = MINE_MERC_SALARY[Math.floorMod(sal, MINE_MERC_SALARY.length)][0];
        return new String[][] { { b[0], String.valueOf(Math.min(MINE_MERC_BAR_MAX, Integer.parseInt(b[1]) + Math.min(4, Math.max(0, lv) / 20))) } };
    }
    static String[][] mineMercCost(Map<String, Integer> items, int c) { return mineMercSalCost(items.getOrDefault(mineMercKey(c, "SAL"), 0), mineMercLv(items, c)); }
    // [2026-11-06] "용병 교체 때는 지금보다 -2~15 동료들로 추천" -- 지금 용병이 있으면 후보 레벨 = 지금 레벨 - MERCOF{i}_D(2~15, 최소 5), 없으면 MERCOF{i}_LV(5~30).
    static final int MINE_MERC_SWAP_MIN = 2, MINE_MERC_SWAP_MAX = 15;
    static int mineMercCurLv(Map<String, Integer> items) { int m = 0; for (int c = 0; c < MINE_MERC_NAMES.length; c++) m = Math.max(m, mineMercLv(items, c)); return m; }
    static int mineOfLv(Map<String, Integer> items, int i) {
        int cur = mineMercCurLv(items), d = items.getOrDefault(mineOfKey(i, "D"), 0);
        if (d < MINE_MERC_SWAP_MIN || d > MINE_MERC_SWAP_MAX) d = MINE_MERC_SWAP_MIN + Math.floorMod(i * 5 + 3, MINE_MERC_SWAP_MAX - MINE_MERC_SWAP_MIN + 1);
        return cur > 0 ? cur : items.getOrDefault(mineOfKey(i, "LV"), MINE_MERC_LV_MIN); // [2026-11-17] "용병 바꿀 때 레벨 감소 없도록" -- 교체 후보는 지금 용병과 같은 레벨(예전: 2~15 낮게)
    }
    // [2026-11-06] "용병은 전부 여자 캐릭으로, S5 동료 이름 재사용" -- BotS5ServiceImpl.NAME_POOL_BY_JOB_GRADE에서 (번호 = 화면 머리색 / 초상화 img_loa/s55/merc/{직업}_{번호}.jpg).
    // [2026-11-06] "동료 이름은 S5 4성 이상 동료 이름으로(기존 것도), 이미지는 최대한 은용 유저의 이미지" -- 전사 = 전사 + 도적, 궁수 = 궁수, 마법사 = 마법사 + 사제의 ★4~★7.
    //  기존 계약/후보는 번호(NM)를 그대로 두고 floorMod로 새 이름에 붙는다. 초상화는 은용의 같은 이름 동료(없으면 다른 유저 / ★7 초상화).
    static final String[][] MINE_MERC_NAMEPOOL = {
        { "렌지", "아츠시", "츠요시", "고우키", "타이가", "유이토", "카게로우", "나기", "야토", "시노" },
        { "세리나", "츠키미", "스즈네", "아마츠", "히카리" },
        { "리오", "카나", "미코토", "세라", "아리스", "코하루", "미레이", "스이렌", "아마네", "미코" } };
    static int mineMercNi(Map<String, Integer> items, int c, String f) { return Math.floorMod(items.getOrDefault(mineMercKey(c, f), 0), MINE_MERC_NAMEPOOL[mineMercCls(items, c)].length); }
    static String mineMercNm(Map<String, Integer> items, int c, String f) { String[] p = MINE_MERC_NAMEPOOL[mineMercCls(items, c)]; return p[Math.floorMod(items.getOrDefault(mineMercKey(c, f), 0), p.length)]; }
    // [2026-11-11] "용병 급여는 최초 1번만, 교체 때도 안 받게" -- 날마다 내던 급여(자동 차감/선금/밀리면 쉼)를 없앴다. 계정에서 처음 계약할 때 한 번만 낸다(MERC_HIRED).
    //  이미 용병이 있는 사람(예전에 계약)은 처음 계약을 마친 것으로 본다. MERC{c}_PAID/SAL/MANUAL/WARN 기록은 남아 있어도 쓰지 않는다.
    static final String MINE_ITEM_MERC_HIRED = "MERC_HIRED";
    static boolean mineMercHiredBefore(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_MERC_HIRED, 0) > 0 || mineMercCount(items) > 0; }
    static boolean mineMercPaid(Map<String, Integer> items, int c, long now) { return true; }
    void mineMercSet(String userName, Map<String, Integer> items, int c, String f, int v) { dao.upsertMineItem(userName, mineMercKey(c, f), v); items.put(mineMercKey(c, f), v); }
    /** 급여 자동 차감(밀린 날만큼, 미접속 시간 포함) -- 안내 문구는 notes에. 모자라면 한 번만 알린다(MERC{c}_WARN = 그때의 PAID). */
    void mineMercPayroll(String userName, Map<String, Integer> items, long now, List<String> notes) {
        if (now > 0) return; // [2026-11-11] 날마다 내던 급여 없앰(처음 계약 때 한 번만) -- 아래는 예전 차감 코드(쓰지 않음)
        int nowMin = (int) (now / 60_000L);
        Map<String, Integer> have = null;
        for (int c = 0; c < MINE_MERC_NAMES.length; c++) {
            if (mineMercLv(items, c) <= 0) continue;
            int paid = items.getOrDefault(mineMercKey(c, "PAID"), 0);
            if (paid > nowMin) continue;
            if (items.getOrDefault(mineMercKey(c, "MANUAL"), 0) > 0) {
                if (items.getOrDefault(mineMercKey(c, "WARN"), -1) != paid) { mineMercSet(userName, items, c, "WARN", paid); notes.add("⏰ 용병 " + mineMercNm(items, c, "NM") + "(" + MINE_MERC_NAMES[c] + ")의 급여 날이에요. 용병 관리에서 급여를 주면 다시 일해요."); }
                continue;
            }
            if (have == null) have = mineOreHave(userName);
            int base = paid <= 0 ? nowMin : paid, days = 0;
            StringBuilder spent = new StringBuilder();
            while (base <= nowMin && days < MINE_MERC_PAY_LOOP) {
                String[][] cost = mineMercCost(items, c);
                if (!mineCostOk(cost, have)) break;
                mineTakeCost(userName, cost, 1);
                for (String[] cc : cost) have.merge(cc[0], -Integer.parseInt(cc[1]), Integer::sum);
                spent.append(spent.length() > 0 ? ", " : "").append(mineCostText(cost));
                base += MINE_MERC_DAY_MIN;
                days++;
                mineMercSet(userName, items, c, "SAL", mineMercRollSal(mineMercLv(items, c)));
            }
            String nm = mineMercNm(items, c, "NM") + "(" + MINE_MERC_NAMES[c] + ")";
            if (days > 0) {
                mineMercSet(userName, items, c, "PAID", base);
                notes.add("💰 용병 " + nm + " 급여 " + days + "일치를 자동으로 줬어요: " + spent + ". 다음 급여는 " + mineCostText(mineMercCost(items, c)) + "예요.");
            }
            if (base <= nowMin && items.getOrDefault(mineMercKey(c, "WARN"), -1) != base) {
                if (days == 0 && paid <= 0) mineMercSet(userName, items, c, "PAID", nowMin); // 예전 계약(급여 기록 없음) -- 지금부터 밀린 것으로
                mineMercSet(userName, items, c, "WARN", items.getOrDefault(mineMercKey(c, "PAID"), 0));
                notes.add("⚠️ 용병 " + nm + " 급여(" + mineCostText(mineMercCost(items, c)) + ")가 모자라 일을 쉬어요. 재료를 모아 용병 관리에서 주세요.");
            }
        }
    }
    // [2026-11-06] "한 5명 중 선택" -- 계약 후보 MINE_MERC_OFFERS칸(MERCOF{i}_T = 직업 + 1, _LV, _EL, _NM, _SAL). 아직 계약 안 한 직업에서만 뽑고(칸 수가 적은 직업 먼저),
    //  같은 직업끼리는 이름이 겹치지 않게. 계약하거나 그 직업을 이미 계약했으면 그 칸은 새로 뽑는다(모든 직업을 계약했으면 비운다).
    static final int MINE_MERC_OFFERS_PER = 5, MINE_MERC_OFFERS = MINE_MERC_OFFERS_PER * 3; // [2026-11-06] "직업별 5명씩, 3줄로" -- 칸 i의 직업 = i / 5
    static final int MINE_MERC_MAX = 1; // [2026-11-06] "용병 관리 창엔 1개만(지금은 1개만)" -- 계약은 한 명까지
    static int mineMercCount(Map<String, Integer> items) { int n = 0; for (int c = 0; c < MINE_MERC_NAMES.length; c++) if (mineMercLv(items, c) > 0) n++; return n; }
    static final boolean MINE_MERC_HITTABLE = false; // [2026-11-06] 용병은 몬스터에게 맞지 않는다(쓰러져 쉬는 일 없음)
    static String mineOfKey(int i, String f) { return "MERCOF" + i + "_" + f; }
    void mineOfSet(String userName, Map<String, Integer> items, int i, String f, int v) { dao.upsertMineItem(userName, mineOfKey(i, f), v); items.put(mineOfKey(i, f), v); }
    void mineMercOffers(String userName, Map<String, Integer> items) {
        // [2026-11-06] "직업별로 5명씩 3줄" -- 칸 0~4 전사 / 5~9 궁수 / 10~14 마법사. 같은 직업 줄에서는 이름이 겹치지 않게(남은 이름 중에서).
        //  계약한 칸(T = 0)이나 예전 배치(다른 직업)는 새로 뽑는다.
        for (int i = 0; i < MINE_MERC_OFFERS; i++) {
            int c = i / MINE_MERC_OFFERS_PER, t = items.getOrDefault(mineOfKey(i, "T"), 0) - 1;
            if (t == c) { if (items.getOrDefault(mineOfKey(i, "D"), 0) < MINE_MERC_SWAP_MIN) mineOfSet(userName, items, i, "D", MINE_MERC_SWAP_MIN + RND.nextInt(MINE_MERC_SWAP_MAX - MINE_MERC_SWAP_MIN + 1)); continue; }
            int len = MINE_MERC_NAMEPOOL[c].length;
            List<Integer> left = new ArrayList<>();
            for (int n = 0; n < len; n++) {
                boolean used = false;
                for (int j = c * MINE_MERC_OFFERS_PER; j < (c + 1) * MINE_MERC_OFFERS_PER; j++)
                    if (j != i && items.getOrDefault(mineOfKey(j, "T"), 0) - 1 == c && Math.floorMod(items.getOrDefault(mineOfKey(j, "NM"), -1), len) == n) used = true;
                if (!used) left.add(n);
            }
            int nm = left.isEmpty() ? RND.nextInt(len) : left.get(RND.nextInt(left.size()));
            mineOfSet(userName, items, i, "T", c + 1);
            mineOfSet(userName, items, i, "LV", MINE_MERC_LV_MIN + RND.nextInt(MINE_MERC_LV_MAX0 - MINE_MERC_LV_MIN + 1));
            mineOfSet(userName, items, i, "EL", MINE_MERC_ELS[RND.nextInt(MINE_MERC_ELS.length)]);
            mineOfSet(userName, items, i, "NM", nm);
            mineOfSet(userName, items, i, "D", MINE_MERC_SWAP_MIN + RND.nextInt(MINE_MERC_SWAP_MAX - MINE_MERC_SWAP_MIN + 1));
            mineOfSet(userName, items, i, "SAL", mineMercRollSal(mineOfLv(items, i)));
        }
    }
    static HashMap<String, Object> mineMercOfferRow(Map<String, Integer> items, int i, Map<String, Integer> have) {
        int c = items.getOrDefault(mineOfKey(i, "T"), 0) - 1;
        if (c < 0 || c >= MINE_MERC_NAMES.length) return null;
        HashMap<String, Object> m = new HashMap<>();
        int lv = mineOfLv(items, i), el = items.getOrDefault(mineOfKey(i, "EL"), S5MineMobs.EL_FIRE), ni = Math.floorMod(items.getOrDefault(mineOfKey(i, "NM"), 0), MINE_MERC_NAMEPOOL[c].length);
        String[][] cost = mineMercSalCost(items.getOrDefault(mineOfKey(i, "SAL"), 0), lv); // [2026-11-06] 레벨 구간 급여
        m.put("i", i);
        m.put("t", c);
        m.put("job", MINE_MERC_NAMES[c]);
        m.put("nm", MINE_MERC_NAMEPOOL[c][ni]);
        m.put("ni", ni);
        m.put("lv", lv);
        m.put("el", el);
        m.put("elName", S5MineMobs.ELEM_NAMES[el]);
        m.put("skill", mineMercSkill(c, el, mineMercStage(lv)));
        boolean free = mineMercHiredBefore(items); // [2026-11-11] 처음 계약 뒤엔(교체 포함) 무료
        m.put("sal", free ? "무료" : mineCostText(cost));
        m.put("free", free);
        m.put("can", free || mineCostOk(cost, have));
        m.put("owned", false); // [2026-11-06] 이제 늘 계약 가능(지금 용병과 바뀐다)
        return m;
    }
    static final String[] MINE_MERC_NAMES = { "전사", "궁수", "마법사" };
    static final String[] MINE_MERC_WEAPON = { "기본 칼", "기본 활", "기본 지팡이" };
    static final int[] MINE_MERC_REACH = { 5, 7, 6 };
    static final double[] MINE_MERC_DMG = { 1.2, 1.0, 1.0 }; // [2026-11-06] 마법사 평타가 한 명(파이어볼)이라 0.8 -> 1.0
    static final int MINE_MERC_SKILL_LV = 40;
    static final long MINE_MERC_SKILL_MS = 20_000L, MINE_MERC_RAIN_MS = 5_000L;
    // [2026-11-18] 마법사 용병 -- "물 토네이도 4초 지속·12초 쿨타임, 불 메테오는 땅이 타는 피해가 오래 남게, 번개는 쿨타임 5초, 기본 공격이 여러 명을" --
    //  토네이도/메테오는 처음 한 방 뒤 그 자리에 남아(화살비와 같은 칸 -- rain*) 매 턴 둘레를 친다. 평타(파이어볼)는 대상 둘레 2칸 안 1+단계마리에도 60%.
    static final long MINE_MERC_TORNADO_MS = 4_000L, MINE_MERC_BURN_MS = 6_000L;
    static long mineMercSkillCd(int cls, int el) { return cls == 2 && el == S5MineMobs.EL_WATER ? 12_000L : cls == 2 && el == S5MineMobs.EL_THUNDER ? 5_000L : MINE_MERC_SKILL_MS; }
    static final int[] MINE_MERC_ELS = { S5MineMobs.EL_FIRE, S5MineMobs.EL_WATER, S5MineMobs.EL_THUNDER };
    static final int MINE_MERC_LV_MIN = 5, MINE_MERC_LV_MAX0 = 30;
    static final double MINE_MERC_EXP_PCT = 1.0;
    static final int MINE_MERC_EXP_GAP = 10, MINE_MERC_EXP_CAP_PCT = 10;
    static final int MINE_MERC_EXP_DIV100 = 20; // [2026-11-23] 용병 100레벨부터 받는 경험치 1/20
    static final long MINE_MERC_REST_MS = 20_000L;
    static final double MINE_MERC_REGEN = 0.05;
    static String mineMercKey(int c, String f) { return "MERC" + c + "_" + f; }
    // [2026-11-18] "두 번째 용병 -- 같은 직업 허용, 5환생 이상, 경험치는 첫 용병과 같게, 선공 모드는 저마다, 계약 Lv.70(진환생 99레벨 이상은 Lv.90), 용병 99 이후도 계속 레벨업" --
    //  용병 기록 번호 k: 0~2 = 첫 칸(직업마다 한 칸, 예전 그대로), 3 = 두 번째 칸(MERC3_*, 직업은 MERC3_C). 데리고 다니기 MERC_ON2(1/0), 선공 MERC_MODE2(1/0).
    //  싸움 상태(체력/쉬기/대상)는 칸마다 따로(MINE_MERC / MINE_MERC2). 동료 장비는 칸(k)마다 끼운다.
    static final int MINE_MERC_K2 = 3;
    static final String MINE_ITEM_MERC_ON2 = "MERC_ON2", MINE_ITEM_MERC_MODE2 = "MERC_MODE2";
    static final int MINE_MERC2_LV = 70, MINE_MERC2_LV_TRUE = 90;
    // [2026-11-23] "진환생하면 특전 없지? 용병 세 마리까지 -- 세 번째 용병은 Lv.90으로 시작" + "진환생 이상만, 피해는 그대로, 장비 칸도" --
    //  기록 번호 4(MERC4_*, 직업은 MERC4_C), 데리고 다니기 MERC_ON3, 선공 MERC_MODE3, 싸움 상태 MINE_MERC3. 진환생(6환생)부터 계약/데리고 다님. 처음 Lv.90(내 레벨 상한 안에서).
    static final int MINE_MERC_K3 = 4, MINE_MERC3_LV = 90;
    static final String MINE_ITEM_MERC_ON3 = "MERC_ON3", MINE_ITEM_MERC_MODE3 = "MERC_MODE3";
    static final java.util.concurrent.ConcurrentHashMap<String, MercSt> MINE_MERC3 = new java.util.concurrent.ConcurrentHashMap<>();
    static boolean mineMercOn3(Map<String, Integer> items) { return mineMercLv(items, MINE_MERC_K3) > 0 && items.getOrDefault(MINE_ITEM_MERC_ON3, 0) > 0 && mineTrueRebirth(items); }
    /** 화면/결과 키 -- 첫 칸 "merc", 두 번째 "merc2", 세 번째 "merc3". */
    static String mineMercOutKey(int k) { return k == MINE_MERC_K3 ? "merc3" : k == MINE_MERC_K2 ? "merc2" : "merc"; }
    static final int MINE_MERC_LV_TOP = 9999; // [2026-11-18] 99 이후도 계속(필요 경험치는 99레벨 값 그대로, 공격력은 플레이어 99 이후와 같은 양씩 -- mineOverAtk)
    static int mineMercCls(Map<String, Integer> items, int k) { return k < MINE_MERC_K2 ? Math.max(0, Math.min(2, k)) : Math.max(0, Math.min(2, items.getOrDefault(mineMercKey(k, "C"), 0))); } // [2026-11-23] 세 번째 칸도(MERC4_C)
    static boolean mineMercOn2(Map<String, Integer> items) { return mineMercLv(items, MINE_MERC_K2) > 0 && items.getOrDefault(MINE_ITEM_MERC_ON2, 0) > 0; }
    /** 데리고 다니는 용병 기록 번호들(첫 칸 먼저). */
    static int[] mineMercActive(Map<String, Integer> items) {
        int a = mineMercOn(items);
        boolean b = mineMercOn2(items), b3 = mineMercOn3(items); // [2026-11-23] 세 번째 칸
        int[] r = new int[(a >= 0 ? 1 : 0) + (b ? 1 : 0) + (b3 ? 1 : 0)];
        int i = 0;
        if (a >= 0) r[i++] = a;
        if (b) r[i++] = MINE_MERC_K2;
        if (b3) r[i++] = MINE_MERC_K3;
        return r;
    }
    static boolean mineMercPreModeK(Map<String, Integer> items, int k) { return items.getOrDefault(k == MINE_MERC_K3 ? MINE_ITEM_MERC_MODE3 : k == MINE_MERC_K2 ? MINE_ITEM_MERC_MODE2 : MINE_ITEM_MERC_MODE, 0) == 1; }
    static long mineMercNeed(int lv) { return mineExpNeed(Math.min(MINE_LV_MAX, Math.max(1, lv)), 0); }
    static final java.util.concurrent.ConcurrentHashMap<String, MercSt> MINE_MERC2 = new java.util.concurrent.ConcurrentHashMap<>();
    static java.util.concurrent.ConcurrentHashMap<String, MercSt> mineMercMap(int k) { return k == MINE_MERC_K3 ? MINE_MERC3 : k == MINE_MERC_K2 ? MINE_MERC2 : MINE_MERC; }
    /** 두 번째 칸 계약 레벨 -- 이미 있으면 그 레벨 그대로(교체), 처음이면 Lv.70, 진환생이고 99레벨 이상이면 Lv.90. */
    int mineMerc2HireLv(HashMap<String, Object> row, Map<String, Integer> items) {
        int had = mineMercLv(items, MINE_MERC_K2);
        return had > 0 ? had : mineTrueRebirth(items) && row != null && mineLv(row) >= MINE_LV_MAX ? MINE_MERC2_LV_TRUE : MINE_MERC2_LV;
    }
    static int mineMercLv(Map<String, Integer> items, int c) { return Math.max(0, Math.min(MINE_MERC_LV_TOP, items.getOrDefault(mineMercKey(c, "LV"), 0))); }
    static int mineMercEl(Map<String, Integer> items, int c) { int e = items.getOrDefault(mineMercKey(c, "EL"), 0); for (int x : MINE_MERC_ELS) if (x == e) return e; return S5MineMobs.EL_FIRE; }
    static int mineMercStage(int lv) { return Math.min(5, 1 + Math.max(0, lv) / 20); }
    static int mineMercStage2(int lv) { return lv >= MINE_MERC_SKILL_LV ? Math.min(3, 1 + (lv - MINE_MERC_SKILL_LV) / 20) : 0; }
    // [2026-11-06] "스킬 탭에서 끔/1/2/3 골라 안 쓸 수 있게" -- MERC{c}_S1(평타 단계) / MERC{c}_S2(2차 스킬 단계): 0 = 가장 높은 단계(기본), 9 = 끔, 1~ = 그 단계(열린 데까지).
    static final int MINE_MERC_SEL_OFF = 9;
    static int mineMercSel(Map<String, Integer> items, int c, String f, int max) {
        int v = items.getOrDefault(mineMercKey(c, f), 0);
        return v == MINE_MERC_SEL_OFF ? 0 : v >= 1 && v <= max ? v : max;
    }
    /** 데리고 다니는 용병 직업(-1 = 없음). */
    static int mineMercOn(Map<String, Integer> items) { int c = items.getOrDefault(MINE_ITEM_MERC_ON, 0) - 1; return c >= 0 && c < MINE_MERC_NAMES.length && mineMercLv(items, c) > 0 && mineMercPaid(items, c, System.currentTimeMillis()) ? c : -1; } // [2026-11-06] 급여가 밀리면 쉰다
    // [2026-11-07] "80레벨과 99레벨 차이가 너무 극명 -- 80레벨 3천이면 99레벨은 1만이 적당, 대신 80 아래에선 고정으로 올라가는 수치를 올려" --
    //  용병 공격력(궁수/마법사 기준, 전사는 x1.2)을 플레이어 곡선(mineAtk, 레벨마다 9%씩 불어남) 대신 두 구간 직선으로: Lv.1 40 -> Lv.80 3,000(레벨마다 약 37.5씩),
    //  Lv.80 3,000 -> Lv.99 10,000(레벨마다 약 368씩). (같은 날의 2배~1.8배 / 1.1배는 이것으로 바뀜)
    static final double MINE_MERC_ATK_LV1 = 40, MINE_MERC_ATK_LV80 = 3_000, MINE_MERC_ATK_LV99 = 10_000;
    static double mineMercBaseAtk(int lv) {
        int l = Math.max(1, Math.min(MINE_LV_MAX, lv));
        return (l <= 80 ? MINE_MERC_ATK_LV1 + (MINE_MERC_ATK_LV80 - MINE_MERC_ATK_LV1) * (l - 1) / 79.0 : MINE_MERC_ATK_LV80 + (MINE_MERC_ATK_LV99 - MINE_MERC_ATK_LV80) * (l - 80) / 19.0)
                + mineOverAtk(lv); // [2026-11-18] 99 이후 -- 플레이어와 같은 양(100레벨 +300, 그 뒤 +10씩 더)
    }
    static int mineMercAtk(Map<String, Integer> items, int c) { return (int) Math.round(mineMercBaseAtk(mineMercLv(items, c)) * MINE_MERC_DMG[mineMercCls(items, c)] * (1 + mineMgBonus(items, c)[0] / 100.0) * (1 + mineMercNeckPct(items, MINE_ITEM_MERC_DMG_NECK) / 100.0) * (1 + mineWeaponMercPct(items) / 100.0)); } // [2026-11-23] 투지의 목걸이, 거룡의 이빨/베히모스의 전율 // [2026-11-15] 동료 장비 공격력 %
    static int mineMercHp(Map<String, Integer> items, int c) { return mineHpMax(Math.max(1, mineMercLv(items, c)), 0); }
    /** 스킬 설명(단계 s). */
    /** 평타 설명(단계 s = 1 + 레벨/20). */
    static String mineMercSkill(int c, int el, int s) {
        String en = S5MineMobs.ELEM_NAMES[el];
        if (c == 0) return en + " 검기 " + s + "단계 · 베는 쪽 " + s + "칸 줄에 공격력의 " + s * MINE_AURA_PCT + "% " + en + " 피해 (플레이어 검기와 같음)";
        if (c == 1) return (el == S5MineMobs.EL_FIRE ? "불화살" : el == S5MineMobs.EL_THUNDER ? "번개 화살" : "물화살") + " · 한 명에게 피해 +" + 20 * s + "%";
        return (el == S5MineMobs.EL_FIRE ? "파이어볼" : el == S5MineMobs.EL_THUNDER ? "라이트닝볼" : "아이스볼") + " · 한 명"; // [2026-11-06] 속성별 구슬
    }
    /** [2026-11-06] 2차 스킬 설명(단계 s2 = 1~3, 40/60/80레벨). */
    static String mineMercSkill2(int c, int el, int s2) {
        if (c == 0) return "이기어검 " + s2 + "단계 · 검 " + (3 + 2 * s2) + "자루가 가까운 몬스터들에게 나눠 꽂힘(한 마리면 전부, 자루마다 공격력 x" + mineFmtPct(1.5 + 0.5 * s2) + ")";
        if (c == 1) return "화살비 " + s2 + "단계 · 둘레 " + (1 + s2) + "칸 땅에 5초(매초 공격력 x" + mineFmtPct(0.6 + 0.2 * s2) + ")";
        if (el == S5MineMobs.EL_THUNDER) return "체인 라이트닝 " + s2 + "단계 · " + (2 + 2 * s2) + "번 튕김(공격력 x2부터 85%씩)";
        if (el == S5MineMobs.EL_WATER) return "토네이도 " + s2 + "단계 · 지나가는 길 + 둘레 " + (1 + s2) + "칸(공격력 x" + mineFmtPct(1.5 + 0.5 * s2) + ")";
        return "메테오 " + s2 + "단계 · 둘레 " + (2 + s2) + "칸 폭발(공격력 x" + mineFmtPct(2.0 + 0.5 * s2) + ")";
    }
    static final class MercSt {
        int cls = -1, max, hp, target = -1, tx, ty;
        long restUntil, at, fightAt, atkAt, struckAt, skillAt, rainUntil; // [2026-11-06] 2차 스킬 쿨타임, 화살비
        int rainX, rainY, rainR;
        double rainMul;
    }
    static final java.util.concurrent.ConcurrentHashMap<String, MercSt> MINE_MERC = new java.util.concurrent.ConcurrentHashMap<>();
    /** 지금 체력(쉬기 끝/싸우지 않는 동안 회복 반영, 용병이 바뀌면 가득, 레벨이 올라 최대 체력이 늘면 그만큼 더). 부를 때 ms에 synchronized. */
    static void mineMercRefresh(MercSt ms, int cls, int max, long now) {
        if (ms.cls != cls) { ms.cls = cls; ms.max = max; ms.hp = max; ms.restUntil = 0; ms.target = -1; }
        if (ms.max != max) { ms.hp = Math.max(1, Math.min(max, ms.hp + Math.max(0, max - ms.max))); ms.max = max; }
        if (ms.restUntil > 0 && ms.restUntil <= now) { ms.restUntil = 0; ms.hp = max; }
        if (ms.restUntil == 0 && ms.hp < max && now - ms.fightAt > 3_000L && ms.at > 0) ms.hp = (int) Math.min(max, ms.hp + max * MINE_MERC_REGEN * (now - ms.at) / 1000.0);
        ms.at = now;
    }
    /** 용병 한 칸(용병 관리 창) -- 계약 안 했으면 {k, t, name, owned:false}. [2026-11-18] k = 기록 번호(0~2 첫 칸 직업별, 3 두 번째 칸), t = 직업. */
    static HashMap<String, Object> mineMercCard(Map<String, Integer> items, int k) {
        HashMap<String, Object> m = new HashMap<>();
        int lv = mineMercLv(items, k), c = mineMercCls(items, k);
        boolean two = k == MINE_MERC_K2, three = k == MINE_MERC_K3;
        m.put("k", k);
        m.put("slot", three ? 3 : two ? 2 : 1);
        m.put("t", c);
        m.put("name", MINE_MERC_NAMES[c]);
        m.put("reach", MINE_MERC_REACH[c]);
        m.put("weapon", MINE_MERC_WEAPON[c]);
        m.put("owned", lv > 0);
        { // [2026-11-15] 동료 장비(칸마다 ★등급, 0 = 없음) + 합친 효과
            HashMap<String, Integer> gs = new HashMap<>();
            for (String p : MG_PARTS) gs.put(p, Math.max(0, Math.min(7, items.getOrDefault(mineMercKey(k, "G" + p), 0)))); // [2026-11-23] "7성을 끼우면 6성이 된다" -- 화면에 보내는 값만 6에서 잘렸다(효과는 7 그대로였다)
            m.put("mg", gs);
            m.put("mgb", mineMgBonus(items, k));
            HashMap<String, Object> gi = new HashMap<>(); // [2026-11-16] 칸마다 끼운 장비 이름/효과
            for (String p : MG_PARTS) { int g = gs.get(p); if (g > 0) gi.put(p, new String[] { mgName(p, mgAcc(p) ? 9 : c, g), mgEffText(p, g) }); }
            m.put("mgi", gi);
        }
        if (lv <= 0) return m;
        int el = mineMercEl(items, k), s = mineMercStage(lv);
        m.put("lv", lv);
        m.put("exp", items.getOrDefault(mineMercKey(k, "EXP"), 0) + items.getOrDefault(mineMercKey(k, "XPP"), 0)); // [2026-11-18] + 아직 DB에 안 쓴 것
        m.put("need", lv >= MINE_MERC_LV_TOP ? 0 : mineMercNeed(lv));
        m.put("atk", mineMercAtk(items, k));
        m.put("max", mineMercHp(items, k));
        m.put("on", three ? mineMercOn3(items) : two ? mineMercOn2(items) : mineMercOn(items) == k);
        m.put("el", el);
        m.put("elName", S5MineMobs.ELEM_NAMES[el]);
        int sMax = s, s2Max = mineMercStage2(lv); // [2026-11-06] 열린 단계 / 고른 단계(0 = 끔) -- 화면 스킬 탭
        s = mineMercSel(items, k, "S1", sMax);
        int s2 = mineMercSel(items, k, "S2", s2Max);
        m.put("st", s);
        m.put("stMax", sMax);
        m.put("stSel", items.getOrDefault(mineMercKey(k, "S1"), 0));
        m.put("skill", mineMercSkill(c, el, Math.max(1, s)));
        if (sMax < 5) { m.put("nextLv", sMax * 20); m.put("skillNext", mineMercSkill(c, el, sMax + 1)); }
        m.put("s2", s2);
        m.put("s2Max", s2Max);
        m.put("s2Sel", items.getOrDefault(mineMercKey(k, "S2"), 0));
        m.put("skill2", mineMercSkill2(c, el, Math.max(1, s2 > 0 ? s2 : s2Max)));
        if (s2Max < 3) { m.put("next2Lv", MINE_MERC_SKILL_LV + s2Max * 20); if (s2Max > 0) m.put("skill2Next", mineMercSkill2(c, el, s2Max + 1)); }
        List<String> d1 = new ArrayList<>(), d2 = new ArrayList<>();
        for (int q = 1; q <= 5; q++) d1.add(mineMercSkill(c, el, q));
        for (int q = 1; q <= 3; q++) d2.add(mineMercSkill2(c, el, q));
        m.put("skills1", d1);
        m.put("skills2", d2);
        m.put("ni", mineMercNi(items, k, "NM")); // [2026-11-06] 치장 대신 이름마다 모습(머리색)
        m.put("nm", mineMercNm(items, k, "NM")); // [2026-11-06] 이름 / 급여
        m.put("paidUntil", items.getOrDefault(mineMercKey(k, "PAID"), 0) * 60_000L);
        m.put("unpaid", !mineMercPaid(items, k, System.currentTimeMillis()));
        m.put("sal", mineCostText(mineMercCost(items, k)));
        m.put("auto", items.getOrDefault(mineMercKey(k, "MANUAL"), 0) <= 0);
        m.put("want", three ? items.getOrDefault(MINE_ITEM_MERC_ON3, 0) > 0 : two ? items.getOrDefault(MINE_ITEM_MERC_ON2, 0) > 0 : items.getOrDefault(MINE_ITEM_MERC_ON, 0) - 1 == k);
        m.put("mode", mineMercPreModeK(items, k) ? 1 : 0); // [2026-11-06] 선공/협공, [2026-11-18] 용병마다
        return m;
    }
    static List<HashMap<String, Object>> mineMercCards(Map<String, Integer> items) {
        List<HashMap<String, Object>> l = new ArrayList<>();
        for (int k = 0; k <= MINE_MERC_K3; k++) l.add(mineMercCard(items, k)); // [2026-11-18] 목록 번호 = 기록 번호(3 = 두 번째 칸, [2026-11-23] 4 = 세 번째 칸)
        return l;
    }
    /** 데리고 다니는 첫 칸 용병(없으면 null) -- 칸 정보 + 지금 체력/쉬기. */
    static HashMap<String, Object> mineMercInfo(String userName, Map<String, Integer> items, long now) { int c = mineMercOn(items); return c < 0 ? null : mineMercInfoK(userName, items, now, c); }
    /** [2026-11-18] 데리고 다니는 두 번째 칸 용병(없으면 null). */
    static HashMap<String, Object> mineMercInfo2(String userName, Map<String, Integer> items, long now) { return mineMercOn2(items) ? mineMercInfoK(userName, items, now, MINE_MERC_K2) : null; }
    static HashMap<String, Object> mineMercInfo3(String userName, Map<String, Integer> items, long now) { return mineMercOn3(items) ? mineMercInfoK(userName, items, now, MINE_MERC_K3) : null; } // [2026-11-23]
    static HashMap<String, Object> mineMercInfoK(String userName, Map<String, Integer> items, long now, int k) {
        HashMap<String, Object> m = mineMercCard(items, k);
        MercSt ms = mineMercMap(k).computeIfAbsent(userName, u -> new MercSt());
        synchronized (ms) {
            mineMercRefresh(ms, mineMercCls(items, k), mineMercHp(items, k), now);
            m.put("hp", ms.hp);
            m.put("rest", ms.restUntil);
        }
        return m;
    }
    /** [2026-11-06] "용병 경험치는 플레이어가 얻는 경험치의 1%, 1레벨 용병이 99레벨 몬스터를 같이 잡아도 렙업이 과하지 않게" -- 데리고 다니는 용병에게. out.mercXp. */
    // [2026-11-06] "용병 소환 후 같이 처치하면 최소 경험치 1씩은" -- 처치 수(kills)만큼은 꼭 받는다(1% 보정/상한보다 우선).
    void mineMercShare(String userName, long gain, int mobLv, long kills, HashMap<String, Object> out) { mineMercShare(userName, null, gain, mobLv, kills, out); }
    // [2026-11-18] 두 번째 용병도 첫 용병과 같은 경험치(저마다 1%). 아이템은 부르는 쪽이 이미 읽은 것을 쓴다(처치마다 아이템 전체를 다시 읽지 않게 -- null이면 읽는다).
    //  99 이후도 계속 오른다(필요 경험치는 99레벨 값 그대로). 결과: out.mercXp(첫 칸) / out.mercXp2(두 번째 칸).
    // [2026-11-18] "용병 경험치 저장은 세션에 물고 있다가" -- 경험치는 메모리(MINE_MERC_XP)에 모으고, 레벨이 오를 때 / 처음 모은 지 1분 / 맵 이동 / 용병소 /
    //  30초 정리(1분 넘게 쌓인 것 -- 게임을 끈 사람 몫) / 서버 종료 때 DB에 "더한다"(addMineItemLevel -- 정리 스레드와 겹쳐도 덮어쓰지 않게).
    //  레벨은 오를 때 바로 쓴다(공격력/스킬/화면이 DB 레벨을 읽는다). 화면 경험치 = DB + 메모리(mineItems가 MERC{k}_XPP로 얹는다).
    static final class MercXp { volatile long p, at; }
    static final java.util.concurrent.ConcurrentHashMap<String, MercXp> MINE_MERC_XP = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_MERC_XP_FLUSH_MS = 60_000L;
    static String mineMercXpKey(String userName, int k) { return userName + "|" + k; }
    /** 한 사람 용병들의 모아 둔 경험치를 DB에 더한다(맵 이동, 용병소). */
    void mineMercXpFlushUser(String userName) {
        if (userName == null) return;
        for (int k = 0; k <= MINE_MERC_K3; k++) mineMercXpFlushOne(userName, k, MINE_MERC_XP.get(mineMercXpKey(userName, k)));
    }
    void mineMercXpFlushOne(String userName, int k, MercXp mx) {
        if (mx == null) return;
        long p;
        synchronized (mx) { p = mx.p; mx.p = 0; mx.at = 0; }
        if (p <= 0) return;
        try { dao.addMineItemLevel(userName, mineMercKey(k, "EXP"), (int) p); }
        catch (Exception ex) { synchronized (mx) { mx.p += p; if (mx.at == 0) mx.at = System.currentTimeMillis(); } } // 못 쓰면 다음에
    }
    /** 30초 정리(BotS5ServiceImpl.flushStatBuffers) -- 모은 지 1분 넘은 것. all = 서버 종료 때 전부. */
    public void mineMercXpSweep(boolean all) {
        try { mineBoatSweep(all); } catch (Exception e) { /* 배 피해는 다음에 */ } // [2026-11-18]
        try { mineWpDurFlush(); } catch (Exception e) { /* 워프 내구도는 다음에 */ } // [2026-11-18] DB 점검 3번
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, MercXp> en : MINE_MERC_XP.entrySet()) {
            MercXp mx = en.getValue();
            if (mx.p <= 0 || (!all && now - mx.at < MINE_MERC_XP_FLUSH_MS)) continue;
            String key = en.getKey();
            int bar = key.lastIndexOf('|');
            if (bar <= 0) continue;
            try { mineMercXpFlushOne(key.substring(0, bar), Integer.parseInt(key.substring(bar + 1)), mx); } catch (Exception ignore) { }
        }
    }
    // [2026-11-23] "용병은 플레이어 레벨을 넘을 수 없도록" -- 경험치를 받아도 내 레벨(M_LV)에서 멈춘다(계약할 때도 내 레벨까지). 내 레벨은 30초 기억.
    //  같은 날 "환생 유저는 용병 레벨 99까지는 보전" -- 한 번이라도 환생했으면 상한은 max(내 레벨, 99).
    static int mineMercCapLv(int lv, int rb) { return rb > 0 ? Math.max(lv, MINE_LV_MAX) : lv; }
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_MERC_CAP = new java.util.concurrent.ConcurrentHashMap<>();
    int mineMercCap(String userName) {
        long now = System.currentTimeMillis();
        long[] c = MINE_MERC_CAP.get(userName);
        if (c != null && now - c[1] < 30_000L) return (int) c[0];
        int lv = MINE_LV_TRUE_MAX;
        try { HashMap<String, Object> r = dao.selectMineUser(userName); if (r != null) lv = mineMercCapLv(mineLv(r), mineRebirth(mineItems(userName))); } catch (Exception ignore) { }
        MINE_MERC_CAP.put(userName, new long[] { lv, now });
        return lv;
    }
    void mineMercShare(String userName, Map<String, Integer> items, long gain, int mobLv, long kills, HashMap<String, Object> out) {
        if (gain <= 0 && kills <= 0) return;
        if (items == null) items = mineItems(userName);
        long now = System.currentTimeMillis();
        int cap = Math.min(MINE_MERC_LV_TOP, mineMercCap(userName)); // [2026-11-23] 내 레벨까지
        for (int k : mineMercActive(items)) {
            int lv = mineMercLv(items, k);
            if (lv >= cap) continue;
            double f = MINE_MERC_EXP_PCT / 100.0;
            int d = mobLv - lv;
            if (d > MINE_MERC_EXP_GAP) f *= Math.max(0.05, 1 - (d - MINE_MERC_EXP_GAP) * 0.03);
            long need = mineMercNeed(lv), g = Math.max(Math.max(1, kills), Math.min(Math.max(1, Math.round(gain * f)), Math.max(1, need * MINE_MERC_EXP_CAP_PCT / 100)));
            if (lv > MINE_LV_MAX) g = Math.max(Math.max(1, kills), g / MINE_MERC_EXP_DIV100); // [2026-11-23] "용병 100레벨 이후엔 경험치를 20분의 1쯤" -- 한 번에 받는 상한(필요량 10%)까지 함께 줄인다
            long e0 = items.getOrDefault(mineMercKey(k, "EXP"), 0); // DB 값
            MercXp mx = MINE_MERC_XP.computeIfAbsent(mineMercXpKey(userName, k), q -> new MercXp());
            int up = 0, nl = lv;
            long write = 0, left;
            synchronized (mx) {
                long exp = e0 + mx.p + g, used = 0;
                while (nl < cap && exp >= mineMercNeed(nl)) { used += mineMercNeed(nl); exp -= mineMercNeed(nl); nl++; up++; }
                if (up > 0 || (mx.at > 0 && now - mx.at >= MINE_MERC_XP_FLUSH_MS)) { write = mx.p + g - used; mx.p = 0; mx.at = 0; } // 레벨이 오르면 / 모은 지 1분이면 DB에(더하기)
                else { mx.p += g; if (mx.at == 0) mx.at = now; }
                left = mx.p;
            }
            if (nl >= MINE_MERC_LV_TOP) { dao.upsertMineItem(userName, mineMercKey(k, "EXP"), 0); items.put(mineMercKey(k, "EXP"), 0); }
            else if (write != 0) { dao.addMineItemLevel(userName, mineMercKey(k, "EXP"), (int) write); items.put(mineMercKey(k, "EXP"), (int) (e0 + write)); }
            if (up > 0) { dao.upsertMineItem(userName, mineMercKey(k, "LV"), nl); items.put(mineMercKey(k, "LV"), nl); }
            items.put(mineMercKey(k, "XPP"), (int) left);
            HashMap<String, Object> x = mineMercCard(items, k);
            x.put("gain", g);
            if (up > 0) x.put("up", up);
            out.put(k == MINE_MERC_K3 ? "mercXp3" : k == MINE_MERC_K2 ? "mercXp2" : "mercXp", x);
        }
    }
    // [2026-11-07] 용병 선공 -- 화면이 1초 턴마다 보내는 보이는 칸 수(가로/세로 절반, vw/vh). 이 안에 보이는 몬스터를 선공 모드 용병이 먼저 친다.
    static final java.util.concurrent.ConcurrentHashMap<String, int[]> MINE_MERC_VIEW = new java.util.concurrent.ConcurrentHashMap<>();
    static final int MINE_MERC_VIEW_W = 9, MINE_MERC_VIEW_H = 6, MINE_MERC_VIEW_MAX = 16;
    @Override
    public void mineView(String userName, int vw, int vh) {
        if (userName == null || vw <= 0 || vh <= 0) return;
        MINE_MERC_VIEW.put(userName, new int[] { Math.min(MINE_MERC_VIEW_MAX, vw), Math.min(MINE_MERC_VIEW_MAX, vh) });
    }
    // [2026-11-17] "용병 탐지 범위는 2칸으로가 아니라 기존 대비 2칸 줄여": 선공 모드 용병이 먼저 끌어들이는/붙잡아 두는 범위 = 화면 범위 - MINE_MERC_DETECT칸(최소 1칸)
    static final int MINE_MERC_DETECT = 2;
    static boolean mineMercInView(String userName, int dx, int dy) {
        int[] v = MINE_MERC_VIEW.get(userName);
        int w = Math.max(1, (v == null ? MINE_MERC_VIEW_W : v[0]) - MINE_MERC_DETECT), h = Math.max(1, (v == null ? MINE_MERC_VIEW_H : v[1]) - MINE_MERC_DETECT);
        return Math.abs(dx) <= w && Math.abs(dy) <= h;
    }
    /** 선공 모드로 데리고 다니는 용병이 있나. */
    static boolean mineMercPreMode(Map<String, Integer> items) { return (mineMercPreModeK(items, 0) && mineMercOn(items) >= 0) || (mineMercPreModeK(items, MINE_MERC_K2) && mineMercOn2(items)) || (mineMercPreModeK(items, MINE_MERC_K3) && mineMercOn3(items)); } // [2026-11-23] 세 번째 칸 // [2026-11-18] 둘 중 하나라도
    /** [2026-11-07] 선공 -- 화면에 보이는 가장 가까운 몬스터(보스/수하/허수아비/분신/숨은 마물 빼고)를 싸움에 끌어들인다(그 몬스터는 나를 쫓아온다). 끌어들였으면 true. */
    boolean mineMercPreempt(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, long now, HashMap<String, Object> out) {
        if (map.boat() || !mineMercPreMode(items)) return false;
        int bestId = -1, bestD = 999, bx = 0, by = 0;
        for (S5MineMap.Mob mb : map.mobs()) {
            if (Math.abs(mb.x - x) > MINE_MERC_VIEW_MAX + 8 || Math.abs(mb.y - y) > MINE_MERC_VIEW_MAX + 8) continue;
            S5MineMobs.Type mt = S5MineMobs.TYPES.get(mb.type);
            if (mt.boss || mt.minion || mt.dummy || mt.clone || mineHidden(map, mt, now)) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, mb.id));
            if (s != null && (s.deadUntil > now || (mt.ninja && s.stealth))) continue;
            if (s != null && s.target != null && !userName.equals(s.target) && mineEngaged(s.target, now)) continue; // 다른 사람이 싸우는 몬스터는 두고
            int mx, my;
            if (s != null && s.frozenUntil > now) { mx = s.fx; my = s.fy; }
            else { int[] cs = mineMobCells(mb, s != null ? s : new MobState(S5MineMobs.hp(mt, mb.rank)), now, map); mx = cs[cs.length / 2] % map.w; my = cs[cs.length / 2] / map.w; }
            if (!mineMercInView(userName, mx - x, my - y)) continue;
            int d = Math.abs(mx - x) + Math.abs(my - y);
            if (d < bestD) { bestD = d; bestId = mb.id; bx = mx; by = my; }
        }
        if (bestId < 0) return false;
        S5MineMap.Mob mb = map.mobs().get(bestId);
        S5MineMobs.Type bt = S5MineMobs.TYPES.get(mb.type);
        MobState s = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, bestId), kk -> new MobState(S5MineMobs.hp(bt, mb.rank)));
        synchronized (s) {
            if (s.deadUntil > 0 && s.deadUntil <= now) s.respawn(S5MineMobs.hp(bt, mb.rank));
            if (s.frozenUntil <= now) { s.fx = bx; s.fy = by; }
            s.frozenUntil = Math.max(s.frozenUntil, now + MINE_MOB_FREEZE_MS);
            s.target = userName;
        }
        mineEngage(userName, map, bestId, now, 0L);
        out.put("mercPre", bestId);
        return true;
    }
    static boolean mineMercCan(S5MineMap map, S5MineMobs.Type ot, MobState s, long now) {
        return s != null && s.deadUntil <= now && !ot.dummy && !ot.clone && !mineHidden(map, ot, now) && !(ot.ninja && s.stealth);
    }
    /** 전투 턴의 용병 차례 -- {처치 수, 경험치}. 결과는 out.merc(+ 드랍/무기). 둘레/체인으로 맞은 몬스터는 sp = [[칸x, 칸y, 피해], ...](체인은 튕긴 순서). */
    long[] mineMercTurn(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, long now, MineEngage e, HashMap<String, Object> out) {
        // [2026-11-06] "용병 열어줘" -- 모든 유저(예전: 관리자만)
        List<String> pay = new ArrayList<>(); // [2026-11-06] 급여 날이 지났으면 자동 차감(안내)
        mineMercPayroll(userName, items, now, pay);
        if (!pay.isEmpty()) { out.put("mercPay", pay); out.put("mercs", mineMercCards(items)); }
        long[] r = { 0, 0 };
        for (int k : mineMercActive(items)) { // [2026-11-18] 두 번째 용병 -- 첫 칸, 두 번째 칸 차례로(같은 싸움, 선공 모드는 저마다)
            long[] a = mineMercTurnK(userName, map, items, x, y, now, k >= MINE_MERC_K2 ? MINE_ENGAGE.getOrDefault(userName, e) : e, out, k);
            r[0] += a[0];
            r[1] += a[1];
        }
        { // [2026-11-18] 광석 목록은 한 번만(뒤의 것이 최신) -- [2026-11-23] 세 칸
            String last = null;
            for (String mk : new String[] { "merc", "merc2", "merc3" }) if (out.get(mk) instanceof Map && ((Map<?, ?>) out.get(mk)).containsKey("ores")) last = mk;
            if (last != null) for (String mk : new String[] { "merc", "merc2", "merc3" }) if (!mk.equals(last) && out.get(mk) instanceof Map) ((Map<?, ?>) out.get(mk)).remove("ores");
        }
        return r;
    }
    // [2026-11-23] "50층 이상 수문장이나 월드보스들은 용병이 스턴 걸리도록" -- 그런 보스(탑 51층~ 수문장, 월드보스 거룡/베히모스)와 싸우는 동안
    //  전투 턴마다 MINE_MERC_STUN_PCT%로 용병이 MINE_MERC_STUN_MS 기절(그동안 공격 없음). 풀린 뒤 MINE_MERC_STUN_GAP_MS 동안은 다시 걸리지 않는다. 키 = "이름|기록 번호".
    static final int MINE_MERC_STUN_PCT = 10;
    static final int MINE_MERC_TOWER_MUL = 3; // [2026-11-23] "탑에서는 용병이 3배 강해지게" -- 탑 층(마을 포함) 안에서 용병 공격력(평타·스킬 모두 이 값에서) x3
    static final long MINE_MERC_STUN_MS = 2_500L, MINE_MERC_STUN_GAP_MS = 6_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_MERC_STUN = new java.util.concurrent.ConcurrentHashMap<>();
    /** 용병 한 명(기록 번호 k)의 차례 -- 결과는 out.merc(첫 칸) / out.merc2(두 번째 칸). */
    long[] mineMercTurnK(String userName, S5MineMap map, Map<String, Integer> items, int x, int y, long now, MineEngage e, HashMap<String, Object> out, int k) {
        int cls = mineMercCls(items, k);
        HashMap<String, Object> mo = mineMercInfoK(userName, items, now, k);
        out.put(mineMercOutKey(k), mo);
        // [2026-11-19] "용병이 바다 위 맵에선 공격 안 하니? 원거리 공격 계열은 했으면(배 뒤에 타 있는 것처럼)" + "전사도 레벨 높은 전사는 이기어검이 원거리" --
        //  배 맵(boat)에서 궁수/마법사는 평소대로, 전사는 근접 평타/검기 없이 이기어검(2차 스킬, 쿨타임마다)만. 이기어검을 못 쓰면(40레벨 전/끔) 타고만 있다.
        boolean boatSw = map.boat() && cls == 0;
        if (map.boat() && map.terrainAt(x, y) != '~') return new long[] { 0, 0 }; // 배에서 내려 등대 섬 위에 있으면 용병은 배에 남아(화면에도 안 보인다) 쉬고 있다
        if (boatSw && mineMercSel(items, k, "S2", mineMercStage2(mineMercLv(items, k))) <= 0) return new long[] { 0, 0 };
        // [2026-11-06] "용병을 선공 모드 / 협공 모드로 고르기" -- 협공(기본) = 나와 싸우는 몬스터만, 선공 = 싸우는 몬스터가 칠 수 있는 데 없으면
        //  [2026-11-07] "선공 모드인데 공격을 안 해 -- 플레이어 기준 맵에 보이면 선공" -- 화면에 보이는(MINE_MERC_VIEW) 가장 가까운 몬스터를 골라 싸움에 끌어들인다(mineMercPreempt).
        if (mineMercPreModeK(items, k)) {
            boolean any = false;
            if (e != null) for (Integer id : e.mobs.keySet()) {
                if (id < 0 || id >= map.mobs().size()) continue;
                MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
                if (mineMercCan(map, S5MineMobs.TYPES.get(map.mobs().get(id).type), s, now) && mineMercInView(userName, s.fx - x, s.fy - y)) { any = true; break; }
            }
            if (!any && mineMercPreempt(userName, map, items, x, y, now, out)) e = MINE_ENGAGE.get(userName);
        }
        if (e == null || e.mobs.isEmpty()) return new long[] { 0, 0 };
        { // [2026-11-23] 큰 보스와 싸우면 용병이 가끔 기절
            String sk = userName + "|" + k;
            Long su = MINE_MERC_STUN.get(sk);
            if (su != null && su > now) { mo.put("stun", su); return new long[] { 0, 0 }; }
            if (su == null || now - su >= MINE_MERC_STUN_GAP_MS) {
                boolean big = false;
                for (Integer id : e.mobs.keySet()) {
                    if (id < 0 || id >= map.mobs().size()) continue;
                    int ti = map.mobs().get(id).type;
                    MobState bs = MINE_MOB_STATE.get(mineMobKey(map, id));
                    if (bs == null || bs.deadUntil > now) continue;
                    if (ti == S5MineMobs.WB_BOSS_TYPE || (S5MineMobs.TYPES.get(ti).boss && S5MineMap.towerFloor(map.id) > S5MineMobs.TW_GEN_FLOORS)) { big = true; break; }
                }
                if (big && RND.nextInt(100) < MINE_MERC_STUN_PCT) { long until = now + MINE_MERC_STUN_MS; MINE_MERC_STUN.put(sk, until); mo.put("stun", until); mo.put("stunNew", true); return new long[] { 0, 0 }; }
            }
        }
        MercSt ms = mineMercMap(k).get(userName);
        int pick = -1, best = 99, reach = MINE_MERC_REACH[cls];
        boolean preMode = mineMercPreModeK(items, k); // [2026-11-07] 선공 모드 -- 화면에 보이면 친다(전사는 그 옆으로 달려간다)
        synchronized (ms) {
            ms.fightAt = now;
            if (ms.restUntil > now) return new long[] { 0, 0 };
            for (Integer id : e.mobs.keySet()) {
                if (id < 0 || id >= map.mobs().size()) continue;
                S5MineMap.Mob om = map.mobs().get(id);
                MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
                if (!mineMercCan(map, S5MineMobs.TYPES.get(om.type), s, now)) continue;
                int d = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                if (d > reach && !(preMode && mineMercInView(userName, s.fx - x, s.fy - y))) continue;
                if (id == ms.target) { pick = id; break; }
                if (d < best) { best = d; pick = id; }
            }
            ms.target = pick;
        }
        if (pick < 0) return new long[] { 0, 0 };
        int mlv = mineMercLv(items, k), el = mineMercEl(items, k), st = mineMercSel(items, k, "S1", mineMercStage(mlv)), atk = mineMercAtk(items, k) * (S5MineMap.towerFloor(map.id) > 0 ? MINE_MERC_TOWER_MUL : 1); // [2026-11-06] 고른 평타 단계(0 = 끔), [2026-11-23] 탑 안에서는 x3
        MobState ps = MINE_MOB_STATE.get(mineMobKey(map, pick));
        // [2026-11-06] "40레벨에 2차 스킬, 쿨타임(20초)마다" -- 평타(1~99): 전사 속성 검기(대상 + 둘레 s칸) / 궁수 속성 화살(한 명 +20% x s) / 마법사 파이어볼(한 명).
        //  2차(40레벨부터, 40/60/80에 1~3단계): 전사 이기어검(가까운 3+2단계마리에 날아가는 검), 마법사 불 메테오(대상 둘레 2+단계칸) / 번개 체인 라이트닝(2+2x단계번 튕김) /
        //  물 토네이도(나 -> 대상 길 + 둘레 1+단계칸), 궁수 화살비(대상 둘레 1+단계칸 땅에 5초 -- 매초 그 안의 싸우는 몬스터).
        List<Integer> hitIds = new ArrayList<>();
        List<Double> hitMul = new ArrayList<>();
        List<int[]> hitAt = new ArrayList<>(); // [2026-11-15] 칸마다 따로 맞는 타격의 칸(없으면 null = 몬스터 자리)
        java.util.function.BiConsumer<Integer, Double> addHit = (hid, hm) -> { int hk = hitIds.indexOf(hid); if (hk >= 0) hitMul.set(hk, hitMul.get(hk) + hm); else { hitIds.add(hid); hitMul.add(hm); hitAt.add(null); } };
        // [2026-11-15] "여러 칸 때리는 스킬은 한 칸당 데미지, 숫자도 맞은 만큼(합치지 않게)" -- 보스가 걸친 칸마다 따로 한 번: {몬스터 번호, 칸 x, 칸 y}
        java.util.function.BiConsumer<int[], Double> addCell = (c, hm) -> { hitIds.add(c[0]); hitMul.add(hm); hitAt.add(new int[] { c[1], c[2] }); };
        addHit.accept(pick, cls == 1 ? 1 + 0.2 * st : 1.0);
        List<int[]> cands = new ArrayList<>(); // 싸우는 몬스터 {id, fx, fy}
        for (Integer id : e.mobs.keySet()) {
            if (id < 0 || id >= map.mobs().size()) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
            if (mineMercCan(map, S5MineMobs.TYPES.get(map.mobs().get(id).type), s, now)) cands.add(new int[] { id, s.fx, s.fy });
        }
        if (cls == 2 && ps != null) { // [2026-11-18] 마법사 평타가 여러 명 -- 대상 둘레 2칸 안 1+단계마리에 60%
            int extra = 0;
            for (int[] cd : cands) { if (cd[0] == pick || extra >= 1 + Math.max(0, st)) continue; if (Math.max(Math.abs(cd[1] - ps.fx), Math.abs(cd[2] - ps.fy)) <= 2) { addHit.accept(cd[0], 0.6); extra++; } }
        }
        // [2026-11-06] "용병 검기를 플레이어 평타 검기(강화)와 같은 것으로" -- 대상 옆(나 쪽)에 선 용병이 대상 쪽으로 st칸 줄을 벤다: 그 줄(대상 뒤까지)의 싸우는 몬스터에게
        //  공격력의 st x 5% 속성 피해(플레이어 검기 mineAuraHits와 같은 비율, 무리는 auraTaken 배). 대상은 평타 그대로. 방향은 나 -> 대상의 큰 축.
        if (ps != null && cls == 0 && st > 0 && !boatSw) {
            int ddx = ps.fx - x, ddy = ps.fy - y, dvx = Math.abs(ddx) >= Math.abs(ddy) ? Integer.signum(ddx) : 0, dvy = dvx == 0 ? Integer.signum(ddy) : 0;
            if (dvx == 0 && dvy == 0) dvy = 1;
            int mx0 = ps.fx - dvx, my0 = ps.fy - dvy; // 용병 자리(대상 바로 앞)
            // [2026-11-06] "검사 용병 검기 나가는 위치가 이상해" -- 화면의 용병은 대상 옆 빈칸(내 칸 말고)에 서는데, 검기는 늘 나 -> 대상 쪽 칸에서 나갔다(내 칸이거나 벽일 때 어긋남).
            //  서는 칸을 여기서 정해(나 쪽 칸, 못 서면 대상 네 옆 중 나와 가장 가까운 칸) 화면에 ms로 주고, 검기는 그 칸에서 대상 쪽으로.
            if (!map.walkable(mx0, my0) || (mx0 == x && my0 == y)) {
                int bd = 999;
                for (int[] v : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                    int sx = ps.fx + v[0], sy = ps.fy + v[1];
                    if (!map.walkable(sx, sy) || (sx == x && sy == y)) continue;
                    int dd = Math.abs(sx - x) + Math.abs(sy - y);
                    if (dd < bd) { bd = dd; mx0 = sx; my0 = sy; }
                }
                dvx = ps.fx - mx0; dvy = ps.fy - my0;
            }
            mo.put("ms", new int[] { mx0, my0 });
            for (int[] cd : cands) { // [2026-11-15] 보스(2x2, 거룡 3x3)는 검기 줄에 걸친 칸마다 한 번씩 -- 대상(평타를 맞는 몬스터)은 첫 칸 말고 더 걸친 칸만큼
                int fo = S5MineMobs.TYPES.get(map.mobs().get(cd[0]).type).foot(), n = 0;
                for (int kq = 1; kq <= st; kq++) {
                    int lx = mx0 + dvx * kq, ly = my0 + dvy * kq;
                    if (lx < cd[1] || lx >= cd[1] + fo || ly < cd[2] || ly >= cd[2] + fo) continue;
                    if (n++ == 0 && cd[0] == pick) continue; // 대상의 첫 칸은 평타
                    if (fo > 1) addCell.accept(new int[] { cd[0], lx, ly }, st * MINE_AURA_PCT / 100.0);
                    else addHit.accept(cd[0], st * MINE_AURA_PCT / 100.0);
                }
            }
            mo.put("adir", new int[] { dvx, dvy });
        }
        int s2 = mineMercSel(items, k, "S2", mineMercStage2(mlv)); // [2026-11-06] 고른 2차 스킬 단계(0 = 끔)
        HashMap<String, Object> sk = null;
        boolean cast = false;
        if (s2 > 0 && ps != null) synchronized (ms) { if (now - ms.skillAt >= mineMercSkillCd(cls, el) * (100 - mineMercNeckPct(items, MINE_ITEM_MERC_CD_NECK)) / 100) { ms.skillAt = now; cast = true; } } // [2026-11-23] 지휘관의 목걸이 // [2026-11-18] 마법사 물 12초 / 번개 5초
        if (cast) {
            sk = new HashMap<>();
            sk.put("x", ps.fx); sk.put("y", ps.fy); sk.put("s", s2);
            List<int[]> pts = new ArrayList<>();
            if (cls == 0) { // 이기어검
                List<int[]> nb = new ArrayList<>();
                for (int[] cd : cands) if (Math.abs(cd[1] - x) + Math.abs(cd[2] - y) <= 6) nb.add(cd);
                nb.sort((p, q) -> Math.abs(p[1] - x) + Math.abs(p[2] - y) - Math.abs(q[1] - x) - Math.abs(q[2] - y));
                // [2026-11-06] "한 마리에게 다 꽂히도록, 여러 마리면 나누어서" -- 검 3+2x단계 자루를 가까운 순으로 돌아가며(한 마리면 전부 그 몬스터에게)
                if (nb.isEmpty()) nb.add(new int[] { pick, ps.fx, ps.fy });
                for (int sw = 0; sw < 3 + 2 * s2; sw++) { int[] tq = nb.get(sw % nb.size()); addHit.accept(tq[0], 1.5 + 0.5 * s2); pts.add(new int[] { tq[1], tq[2], tq[0] }); }
                sk.put("k", "sword"); sk.put("nm", "이기어검");
            } else if (cls == 1) { // 화살비
                synchronized (ms) { ms.rainX = ps.fx; ms.rainY = ps.fy; ms.rainR = 1 + s2; ms.rainUntil = now + MINE_MERC_RAIN_MS; ms.rainMul = 0.6 + 0.2 * s2; }
                sk.put("k", "rain"); sk.put("r", 1 + s2); sk.put("until", now + MINE_MERC_RAIN_MS); sk.put("nm", "화살비");
            } else if (el == S5MineMobs.EL_THUNDER) { // 체인 라이트닝
                addHit.accept(pick, 2.0);
                int cx = ps.fx, cy = ps.fy;
                double mul = 2.0;
                List<Integer> used = new ArrayList<>();
                used.add(pick);
                for (int hop = 0; hop < 2 + 2 * s2; hop++) {
                    int[] nb = null; int nd = 99;
                    for (int[] cd : cands) { if (used.contains(cd[0])) continue; int d = Math.abs(cd[1] - cx) + Math.abs(cd[2] - cy); if (d <= 3 && d < nd) { nd = d; nb = cd; } }
                    if (nb == null) break;
                    mul *= 0.85;
                    used.add(nb[0]); addHit.accept(nb[0], mul); pts.add(new int[] { nb[1], nb[2], nb[0] });
                    cx = nb[1]; cy = nb[2];
                }
                sk.put("k", "chain"); sk.put("nm", "체인 라이트닝");
            } else if (el == S5MineMobs.EL_WATER) { // 토네이도
                int rad = 1 + s2;
                double vx = ps.fx - x, vy = ps.fy - y, l2 = vx * vx + vy * vy;
                for (int[] cd : cands) { // [2026-11-15] 걸친 칸마다
                    int fo = S5MineMobs.TYPES.get(map.mobs().get(cd[0]).type).foot();
                    for (int oy = 0; oy < fo; oy++) for (int ox = 0; ox < fo; ox++) {
                        int qx = cd[1] + ox, qy = cd[2] + oy;
                        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((qx - x) * vx + (qy - y) * vy) / l2));
                        if (!(Math.max(Math.abs(qx - ps.fx), Math.abs(qy - ps.fy)) <= rad || Math.hypot(qx - (x + vx * t), qy - (y + vy * t)) <= 1.0)) continue;
                        if (fo > 1) addCell.accept(new int[] { cd[0], qx, qy }, 1.5 + 0.5 * s2);
                        else addHit.accept(cd[0], 1.5 + 0.5 * s2);
                    }
                }
                synchronized (ms) { ms.rainX = ps.fx; ms.rainY = ps.fy; ms.rainR = rad; ms.rainUntil = now + MINE_MERC_TORNADO_MS; ms.rainMul = 0.5 + 0.25 * s2; } // [2026-11-18] 4초 동안 남는다
                sk.put("k", "tornado"); sk.put("r", rad); sk.put("nm", "토네이도"); sk.put("until", now + MINE_MERC_TORNADO_MS);
            } else { // 메테오
                int rad = 2 + s2;
                for (int[] cd : cands) { // [2026-11-15] 걸친 칸마다 따로
                    int fo = S5MineMobs.TYPES.get(map.mobs().get(cd[0]).type).foot();
                    for (int oy = 0; oy < fo; oy++) for (int ox = 0; ox < fo; ox++) {
                        if (Math.max(Math.abs(cd[1] + ox - ps.fx), Math.abs(cd[2] + oy - ps.fy)) > rad) continue;
                        if (fo > 1) addCell.accept(new int[] { cd[0], cd[1] + ox, cd[2] + oy }, 2.0 + 0.5 * s2);
                        else addHit.accept(cd[0], 2.0 + 0.5 * s2);
                    }
                }
                synchronized (ms) { ms.rainX = ps.fx; ms.rainY = ps.fy; ms.rainR = rad; ms.rainUntil = now + MINE_MERC_BURN_MS; ms.rainMul = 0.4 + 0.2 * s2; } // [2026-11-18] 타는 땅이 6초 남는다
                sk.put("k", "meteor"); sk.put("r", rad); sk.put("nm", "메테오"); sk.put("until", now + MINE_MERC_BURN_MS);
            }
            sk.put("pts", pts);
            mo.put("sk", sk);
        }
        if (boatSw) { // [2026-11-19] 배 위 전사 -- 이기어검 차례가 아니면 쉬고, 이기어검 때는 평타(대상 1.0) 몫을 뺀다
            if (!cast) return new long[] { 0, 0 };
            int b0 = hitIds.indexOf(pick);
            if (b0 >= 0) { double r0 = hitMul.get(b0) - 1.0; if (r0 <= 0.001) { hitIds.remove(b0); hitMul.remove(b0); hitAt.remove(b0); } else hitMul.set(b0, r0); }
            mo.remove("tx"); mo.remove("ty");
        }
        long rainUntil; int rx, ry, rr; double rm;
        synchronized (ms) { rainUntil = ms.rainUntil; rx = ms.rainX; ry = ms.rainY; rr = ms.rainR; rm = ms.rainMul; }
        if (rainUntil > now && !cast) for (int[] cd : cands) { // 화살비 -- 땅에 남아 매초, [2026-11-15] 걸친 칸마다 따로
            int fo = S5MineMobs.TYPES.get(map.mobs().get(cd[0]).type).foot();
            for (int oy = 0; oy < fo; oy++) for (int ox = 0; ox < fo; ox++) {
                if (Math.max(Math.abs(cd[1] + ox - rx), Math.abs(cd[2] + oy - ry)) > rr) continue;
                if (fo > 1) addCell.accept(new int[] { cd[0], cd[1] + ox, cd[2] + oy }, rm);
                else addHit.accept(cd[0], rm);
            }
        }
        long kills = 0, expSum = 0;
        List<HashMap<String, Object>> allDrops = new ArrayList<>();
        List<long[]> rows = new ArrayList<>();
        List<int[]> sp = new ArrayList<>();
        boolean primaryDead = false;
        for (int hi = 0; hi < hitIds.size(); hi++) {
            int id = hitIds.get(hi);
            S5MineMap.Mob om = map.mobs().get(id);
            S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
            if (s == null) continue;
            if (om.type == S5MineMobs.HARPY_BOSS_TYPE && mineHarpyNow(map, now)[2] == 1) continue; // [2026-11-11] 나무 뒤에 숨은 하피만 용병도 못 때린다
            if (S5MineMobs.diving(S5MineMobs.TYPES.get(om.type), om.id, now)) continue; // [2026-11-18] 물속 바다 몬스터
            boolean beat = mineWeakTo(el, ot);
            int[] mgb = mineMgBonus(items, k); // [2026-11-15] 동료 장비 -- 치명타(2배), 상성 피해 +%
            boolean mcrit = mgb[1] > 0 && RND.nextInt(100) < mgb[1];
            int omMax = S5MineMobs.hp(ot, om.rank), dmg = Math.max(1, (int) Math.round(atk * hitMul.get(hi) * (beat ? 1.5 + mgb[2] / 100.0 : 1) * (mcrit ? 2 : 1) * (hi > 0 && cls == 0 ? ot.auraTaken : 1) * (0.85 + RND.nextDouble() * 0.3)));
            boolean dead = false;
            synchronized (s) {
                if (s.deadUntil > now) continue;
                if (s.deadUntil > 0) s.respawn(omMax); // [2026-11-07] 다시 나타난 몬스터는 체력을 채우고(검기/공격과 같게)
                dmg = (int) Math.round(dmg * mineVulnMul(s, now) * ot.armor); // [2026-11-08] 만검진 받는 피해 증가, [2026-11-23] 탑 수문장 갑피
                if (ot.boss) mineBossRegen(s, omMax, now, map, om);
                s.lastHitAt = now;
                mineHitBy(s, ot, userName); // [2026-11-13] 용병이 때려도 주인이 함께 싸운 것
                int wbB = s.hp; // [2026-11-15]
                s.hp -= dmg;
                if (om.type == S5MineMobs.WB_BOSS_TYPE) s.hp = mineWbApply(userName, wbB, wbB - Math.max(0, s.hp), now, true); // [2026-11-15] 월드보스 -- 몫으로(용병 피해는 주인 몫)
                // [2026-11-07] "용병으로 적 타격 시 안 죽는 문제" -- 보스는 용병 공격으로 체력 1에서 멈췄다(내가 얼어 있거나 못 때리면 영영). 이제 용병도 쓰러뜨린다
                //  (보상/기록은 mineMercBossKilled -- 내가 쓰러뜨린 것과 같게). 분신술·은신이 있는 닌자 두목만 예전처럼 마지막 한 방은 내가.
                if (om.type == S5MineMobs.SHRINE_BOSS_TYPE) s.hp = Math.max(1, s.hp);
                mineKrkFloor(s, now); // [2026-11-19]
                if (s.hp <= 0) {
                    dead = true;
                    s.hp = 0;
                    s.deadUntil = ot.boss ? now + (ot.respawnOverride > 0 ? ot.respawnOverride : MINE_BOSS_RESPAWN_MS) : ot.minion ? Long.MAX_VALUE / 2 : now + ot.respawnMs();
                    s.frozenUntil = 0;
                    s.stealth = false;
                }
                rows.add(mineMobRow(id, s));
                if (hi == 0) { mo.put("tx", s.fx); mo.put("ty", s.fy); mo.put("dmg", dmg); if (beat) mo.put("beat", true); }
                else { int[] hat = hitAt.get(hi); sp.add(new int[] { hat != null ? hat[0] : s.fx, hat != null ? hat[1] : s.fy, dmg, id }); } // [2026-11-06] 4번째 = 몬스터 번호(화면이 그려진 자리로 조준), [2026-11-15] 칸마다 맞은 타격은 그 칸에 숫자
            }
            if (!dead) continue;
            if (hi == 0) primaryDead = true;
            e.mobs.remove(id);
            mineThunderKill(map, ot, now);
            String twm = mineTowerKill(userName, map, om, ot); // [2026-11-15] 탑 -- 용병이 쓰러뜨려도 PP/수문장
            if (!twm.isEmpty()) mo.put("twMsg", twm);
            long be = mineLuckExp(items, mineNightExp(map, ot, mineTwExp(userName, map, ot, S5MineMobs.exp(ot, om.rank)), now), now), g = be + be * mineMindExpPct(items) / 100; // [2026-11-23] 탑 수문장 x3 / 0
            kills++;
            expSum += g;
            if (ot.boss) mineMercBossKilled(userName, map, om, ot, now, mo, allDrops, "용병과 함께 "); // [2026-11-07] 용병이 쓰러뜨린 보스
            if (!ot.noReward) {
                StringBuilder dm = new StringBuilder();
                if (!ot.boss) allDrops.addAll(mineMobDrops(userName, map, ot, dm, om.rank));
                if (!(ot.boss && Boolean.TRUE.equals(mo.get("bossRwNo"))) && !mineTwNoRw(userName, map, ot)) mineWeaponDrop(userName, items, ot, om.rank, dm, mo); // [2026-11-20] 보스 보상 하루 5번, [2026-11-23] 탑 수문장 하루 3번
            }
        }
        if (rows.isEmpty()) return new long[] { 0, 0 };
        mo.put("tg", pick);
        mo.put("row", rows.get(0));
        if (rows.size() > 1) mo.put("rows", rows.subList(1, rows.size()));
        if (!sp.isEmpty()) mo.put("sp", sp);
        int got = 0;
        synchronized (ms) {
            ms.tx = (Integer) mo.get("tx"); ms.ty = (Integer) mo.get("ty"); ms.atkAt = now;
            if (MINE_MERC_HITTABLE && !primaryDead && now - ms.struckAt >= MINE_MOB_STRIKE_GAP_MS * (cls == 0 ? 1 : 2)) { // 몬스터의 반격은 용병이 맞는다(원거리는 덜) -- [2026-11-06] "용병은 쓰러지지 않게, 피격받지 않도록" 꺼 둠
                S5MineMap.Mob om = map.mobs().get(pick);
                S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
                ms.struckAt = now;
                got = Math.max(1, (int) Math.round(S5MineMobs.atk(ot, om.rank) * mineNightAtk(map, ot, now) * (0.8 + RND.nextDouble() * 0.4)));
                ms.hp -= got;
                if (ms.hp <= 0) { ms.hp = 0; ms.restUntil = now + MINE_MERC_REST_MS; ms.target = -1; mo.put("down", true); }
            }
            mo.put("hp", ms.hp);
            mo.put("rest", ms.restUntil);
        }
        if (got > 0) mo.put("got", got);
        if (kills == 0) return new long[] { 0, 0 };
        mo.put("killed", true);
        mo.put("kexp", expSum); // [2026-11-08] 처치 경험치 -- "exp"는 용병 카드(모은 경험치)와 이름이 겹쳐 매 턴 "내 경험치 +"가 떴다
        if (!allDrops.isEmpty()) mo.put("drops", allDrops);
        mo.put("ores", mineOreList(userName));
        return new long[] { kills, expSum };
    }

    /** [2026-11-07] 용병이 보스를 쓰러뜨렸을 때 -- 내가 쓰러뜨린 것과 같은 기록(다시 나타나는 시각)/효과(빙결·용암·낙뢰 끝)/보상. 안내는 mo.bossMsg. */
    /** [2026-11-13] 보스를 때린 사람 기록(허수아비 제외). 소환 때 상태를 새로 만들어 비워진다(mineCallReset). */
    static void mineHitBy(MobState s, S5MineMobs.Type t, String userName) {
        if (!t.boss || t.sim || t.dummy || t.noReward || userName == null) return;
        if (s.hitters == null) s.hitters = new java.util.HashSet<>();
        s.hitters.add(userName);
    }
    // [2026-11-20] "동일 보스는 하루 5회까지만 보상, 보스룸 진입 때나 지도에서 보스를 볼 때 몇 중 몇 남았는지 보스별/유저별로" --
    //  보스 종류마다 사람마다 하루(0시 기준) MINE_BOSS_RW_MAX번까지 처치 보상(보스 보상 + 달빛석/별빛석 확률 + 장비 드랍)을 받는다. 직접 처치/함께 싸운 몫/필살기·용병 처치 모두 같은 횟수.
    //  다 받은 뒤에도 잡을 수는 있다(경험치와 처치 기록은 그대로). 월드보스(순위 보상), 여왕개미(보상 없음), 탑 수문장, 허수아비는 세지 않는다.
    //  아이템 칸 BRW<보스 종류> = yyyyMMdd * 10 + 받은 횟수(날짜가 바뀌면 0부터). 관리자는 세기만 하고 막지 않는다(시험용).
    static final int MINE_BOSS_RW_MAX = 5;
    static final int[] MINE_BOSS_RW_TYPES = { S5MineMobs.BOSS_TYPE, S5MineMobs.RAID_BOSS_TYPE, S5MineMobs.ICE_BOSS_TYPE, S5MineMobs.SHRINE_BOSS_TYPE, S5MineMobs.LAVA_BOSS_TYPE,
            S5MineMobs.PHARAOH_BOSS_TYPE, S5MineMobs.THUNDER_BOSS_TYPE, S5MineMobs.HARPY_BOSS_TYPE, S5MineMobs.BORO_BOSS_TYPE, S5MineMobs.KRAKEN_TYPE };
    static boolean mineBossRwCounted(int t) { for (int x : MINE_BOSS_RW_TYPES) if (x == t) return true; return false; }
    static String mineBossRwKey(int t) { return "BRW" + t; }
    static int mineBossRwUsed(Map<String, Integer> items, int t) {
        int v = items.getOrDefault(mineBossRwKey(t), 0);
        return v / 10 == mineTodayNum() ? v % 10 : 0;
    }
    /** 오늘 남은 보상 횟수(세지 않는 보스면 -1). */
    int mineBossRwLeft(String u, int t) { return mineBossRwCounted(t) ? Math.max(0, MINE_BOSS_RW_MAX - mineBossRwUsed(mineItems(u), t)) : -1; }
    /** 이번 처치 보상을 받을 수 있으면 한 번 쓰고 true. 세지 않는 보스면 늘 true. */
    boolean mineBossRwTake(String u, int t) {
        if (!mineBossRwCounted(t) || u == null) return true;
        int used = mineBossRwUsed(mineItems(u), t);
        boolean admin = isMineAdmin(u);
        if (used >= MINE_BOSS_RW_MAX && !admin) return false;
        dao.upsertMineItem(u, mineBossRwKey(t), mineTodayNum() * 10 + Math.min(9, used + 1));
        return true;
    }
    static String mineBossRwNoMsg(String name) { return " 🎁 오늘 " + name + " 보상 " + MINE_BOSS_RW_MAX + "번을 다 받았어요. 내일 0시에 다시 받을 수 있어요."; }
    /** 화면용 -- 보스마다 오늘 받은 횟수. */
    List<HashMap<String, Object>> mineBossRwList(String u) {
        Map<String, Integer> it = mineItems(u);
        List<HashMap<String, Object>> l = new ArrayList<>();
        for (int t : MINE_BOSS_RW_TYPES) {
            if (t < 0) continue;
            HashMap<String, Object> m = new HashMap<>();
            m.put("name", S5MineMobs.TYPES.get(t).name);
            m.put("used", Math.min(MINE_BOSS_RW_MAX, mineBossRwUsed(it, t)));
            m.put("max", MINE_BOSS_RW_MAX);
            l.add(m);
        }
        return l;
    }
    /** [2026-11-13] 보스 종류별 처치 보상(쓰러뜨린 사람과 같은 것). */
    List<HashMap<String, Object>> mineBossKillDrops(String u, S5MineMap map, S5MineMap.Mob om, StringBuilder msg) {
        int t = om.type;
        return t == S5MineMobs.RAID_BOSS_TYPE ? mineRaidDrops(u, msg) : t == S5MineMobs.ICE_BOSS_TYPE ? mineIceDrops(u, msg)
                : t == S5MineMobs.LAVA_BOSS_TYPE ? mineLavaDrops(u, msg) : t == S5MineMobs.PHARAOH_BOSS_TYPE ? mineDesertReward(u, msg)
                : t == S5MineMobs.THUNDER_BOSS_TYPE ? mineThunderDrops(u, msg) : t == S5MineMobs.HARPY_BOSS_TYPE ? mineHarpyDrops(u, msg)
                : t == S5MineMobs.SHRINE_BOSS_TYPE ? mineMobDrops(u, map, S5MineMobs.TYPES.get(t), msg, om.rank)
                : t == S5MineMobs.BORO_BOSS_TYPE ? mineBoroDrops(u, msg) : t == S5MineMobs.KRAKEN_TYPE ? mineKrakenDrops(u, msg) /* [2026-11-18] "보로네오 클리어한 사람들 빛의 심장이 안 나왔다" -- 함께 싸운 보상도 그 보스 보상으로 */ : mineBossDrops(u, msg);
    }
    /** [2026-11-13] "같이 싸운 사람 전부가 보상받게" -- 보스를 때린 사람(용병 포함) 중 쓰러뜨린 사람 말고 모두에게 같은 보상(보스 보상 + 달빛석/별빛석 확률 + 장비 드랍).
     *  경험치는 쓰러뜨린 사람만. 소식은 1초 위치 알림(rewardMsg)으로. 돌려주는 값 = 받은 사람 수. */
    @javax.annotation.Resource(name = "transactionManager")
    org.springframework.transaction.PlatformTransactionManager mineTxm;
    // [2026-11-18] DB 점검 2번 -- 예전엔 쓰러뜨린 사람의 트랜잭션(그 사람 행을 잠근 채) 안에서 함께 싸운 사람들의 광석/아이템을 썼다 -- 그 사람들의 공격 요청과
    //  잠금 순서가 엇갈려 교착 위험. 이제 처치가 커밋된 뒤(afterCommit) 유저 이름 순으로, 사람마다 새 트랜잭션에서 그 사람 행부터 잠그고 준다. 공동 처치 기록도 커밋 뒤에.
    int mineBossShare(String killer, S5MineMap map, S5MineMap.Mob om, java.util.Set<String> hitters) {
        S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
        if (!ot.boss || ot.noReward || ot.sim || ot.dummy || om.type == S5MineMobs.WB_BOSS_TYPE) return 0; // [2026-11-15] 월드보스는 순위 보상
        java.util.TreeSet<String> mates = new java.util.TreeSet<>();
        if (hitters != null) for (String u : hitters) if (u != null && !u.equals(killer)) mates.add(u);
        StringBuilder nm = new StringBuilder();
        for (String u : mates) nm.append(nm.length() > 0 ? ", " : "").append(mineNick(u));
        final String line = "👑 " + ot.name + (mates.isEmpty() ? " 처치" : " 공동 처치") + " -- 마지막 한 방: " + mineNick(killer) + (nm.length() > 0 ? " · 함께: " + nm : ""); // [2026-11-18] "킬로그도 공동 처치로(용병이어도 그 주인)"
        final List<String> who = new ArrayList<>(mates);
        Runnable after = () -> {
            try { S5MineSessionLog.action(killer, "[웹] 광산 전투 보스 처치", line, null); } catch (Exception ignore) { }
            for (String u : who) {
                try { S5MineSessionLog.action(u, "[웹] 광산 전투 보스 처치", line, null); } catch (Exception ignore) { }
                org.springframework.transaction.support.TransactionTemplate tt = new org.springframework.transaction.support.TransactionTemplate(mineTxm);
                tt.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                try { tt.execute(stx -> { dao.selectMineUserForUpdate(u); mineBossShareOne(u, killer, map, om, ot); return null; }); } catch (Exception e) { /* 한 사람 실패해도 나머지는 */ }
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronizationAdapter() { @Override public void afterCommit() { after.run(); } });
        else after.run();
        return who.size();
    }
    void mineBossShareOne(String u, String killer, S5MineMap map, S5MineMap.Mob om, S5MineMobs.Type ot) {
        if (S5MineMap.towerFloor(map.id) <= 0 && !mineBossRwTake(u, om.type)) { // [2026-11-20] 보스 보상 하루 5번
            MINE_SHRINE_REWARD_MSG.merge(u, "👑 " + ot.name + "이(가) 쓰러졌어요!" + mineBossRwNoMsg(ot.name), (p, q) -> p + " / " + q);
            return;
        }
        {
            {
                StringBuilder m = new StringBuilder();
                List<HashMap<String, Object>> kd = mineBossKillDrops(u, map, om, m);
                mineBossShine(u, kd, m);
                mineWeaponDrop(u, mineItems(u), ot, om.rank, m, new HashMap<String, Object>());
                String got = m.toString().replace("👑 " + ot.name + "를 쓰러뜨렸어요!", "").replace("👑 " + ot.name + "을(를) 쓰러뜨렸어요!", "").trim(); // 천둥 황소 보상 문구의 처치 안내는 빼고
                String line = "👑 " + ot.name + "이(가) 쓰러졌어요! 🤝 함께 싸운 보상" + (got.isEmpty() ? "을 받았어요!" : ": " + got);
                MINE_SHRINE_REWARD_MSG.merge(u, line, (p, q) -> p + " / " + q);
            }
        }
    }

    void mineMercBossKilled(String userName, S5MineMap map, S5MineMap.Mob om, S5MineMobs.Type ot, long now, HashMap<String, Object> mo, List<HashMap<String, Object>> drops, String how) {
        StringBuilder msg = new StringBuilder();
        int t = om.type;
        if (t == S5MineMobs.THUNDER_BOSS_TYPE) { mineThunderEnd(map, now); mo.put("shock", Collections.emptyList()); }
        if (t == S5MineMobs.RAID_BOSS_TYPE) { mineRaidAwakeUntil = 0; mineBossKilledNow(MINE_RAID_KEY, userName, now); }
        if (t == S5MineMobs.ICE_BOSS_TYPE) { mineIceAwakeUntil = 0; mineIceNextFreeze = 0; mineIceThawAll(); mineBossKilledNow(MINE_ICE_KEY, userName, now); }
        if (t == S5MineMobs.PHARAOH_BOSS_TYPE) { mineBossKilledNow(MINE_PHARAOH_KEY, userName, now); MINE_RANGED_NEXT.clear(); }
        if (t == S5MineMobs.HARPY_BOSS_TYPE) { mineBossKilledNow(MINE_HARPY_KEY, userName, now); mineHarpyReset(); } // [2026-11-08]
        if (t == S5MineMobs.LAVA_BOSS_TYPE) { mineBossKilledNow(MINE_LAVA_KEY, userName, now); MINE_RANGED_NEXT.clear(); MINE_LAVA.clear(); mo.put("lava", Collections.emptyList()); }
        if (t == S5MineMobs.BOSS_TYPE) mineBossKilledNow(MINE_BOSS_KEY, userName, now);
        boolean rw = S5MineMap.towerFloor(map.id) > 0 || mineBossRwTake(userName, t); // [2026-11-20] 보스 보상 하루 5번
        if (!rw) { mo.put("bossRwNo", true); msg.append(mineBossRwNoMsg(ot.name)); }
        if (rw && !ot.noReward && t != S5MineMobs.WB_BOSS_TYPE) { // [2026-11-15] 월드보스는 순위 보상(mineWbFinish)
            List<HashMap<String, Object>> kd = t == S5MineMobs.RAID_BOSS_TYPE ? mineRaidDrops(userName, msg) : t == S5MineMobs.ICE_BOSS_TYPE ? mineIceDrops(userName, msg)
                    : t == S5MineMobs.LAVA_BOSS_TYPE ? mineLavaDrops(userName, msg) : t == S5MineMobs.PHARAOH_BOSS_TYPE ? mineDesertReward(userName, msg)
                    : t == S5MineMobs.THUNDER_BOSS_TYPE ? mineThunderDrops(userName, msg) : t == S5MineMobs.HARPY_BOSS_TYPE ? mineHarpyDrops(userName, msg) : t == S5MineMobs.KRAKEN_TYPE ? mineKrakenDrops(userName, msg) : t == S5MineMobs.BORO_BOSS_TYPE ? mineBoroDrops(userName, msg) /* [2026-11-18] 필살기/용병으로 쓰러뜨려도 빛의 심장 */ : mineBossDrops(userName, msg);
            mineBossShine(userName, kd, msg);
            drops.addAll(kd);
            MobState ks = MINE_MOB_STATE.get(mineMobKey(map, om.id)); // [2026-11-13] 함께 싸운 사람
            java.util.Set<String> hs;
            if (ks == null) hs = null; else synchronized (ks) { hs = ks.hitters == null ? null : new java.util.HashSet<>(ks.hitters); }
            int sh = mineBossShare(userName, map, om, hs);
            if (sh > 0) msg.append(" 🤝 함께 싸운 ").append(sh).append("명도 같은 보상을 받았어요!");
        }
        if (!rw && !ot.noReward && t != S5MineMobs.WB_BOSS_TYPE) { // [2026-11-20] 내가 다 받았어도 함께 싸운 사람은 각자 횟수대로
            MobState ks = MINE_MOB_STATE.get(mineMobKey(map, om.id));
            java.util.Set<String> hs;
            if (ks == null) hs = null; else synchronized (ks) { hs = ks.hitters == null ? null : new java.util.HashSet<>(ks.hitters); }
            mineBossShare(userName, map, om, hs);
        }
        if (mineBossRwCounted(t) && S5MineMap.towerFloor(map.id) <= 0) { int rl = mineBossRwLeft(userName, t); mo.put("bossRwLeft", rl); if (rw) msg.append(" 🎁 오늘 보상 ").append(rl).append("/").append(MINE_BOSS_RW_MAX).append("번 남음"); }
        mo.put("bossKilled", true);
        mo.put("bossName", ot.name); // [2026-11-07] 관리자 알림
        mo.put("bossMsg", "👑 " + how + ot.name + "을(를) 쓰러뜨렸어요!" + msg);
        mineGossipAdd(mineNick(userName) + " 님이 " + how + ot.name + "을(를) 쓰러뜨렸대요!");
    }

    @Override
    @Transactional
    public HashMap<String, Object> mineMercShop(String userName, String action, int tier, String code) {
        if (!mineAccess(userName)) return mineClosed();
        if (false) { // [2026-11-06] "용병 열어줘" -- 예전: 시험 중 관리자만
            HashMap<String, Object> closed = new HashMap<>();
            closed.put("ok", false);
            closed.put("message", "용병소는 아직 문을 열지 않았네.\n쓸 만한 놈들을 추리는 중이라... 조금만 기다려 주게.");
            return closed;
        }
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        mineMercXpFlushUser(userName); // [2026-11-18] 메모리에 모아 둔 용병 경험치부터 DB에
        Map<String, Integer> items = mineItems(userName);
        boolean ok = true;
        String msg = "";
        int c = tier;
        boolean cOk = c >= 0 && c <= MINE_MERC_K3; // [2026-11-18] 3 = 두 번째 용병, [2026-11-23] 4 = 세 번째
        long now = System.currentTimeMillis();
        List<String> pay = new ArrayList<>();
        if (!"hire".equals(action) && !"hire2".equals(action) && !"hire3".equals(action)) mineMercPayroll(userName, items, now, pay);
        if ("hire".equals(action)) { // 계약 -- tier = 후보 칸(0~4), 보여 준 후보(이름/Lv.5~30/속성) 그대로, 첫날 급여를 낸다
            int oi = tier;
            c = oi >= 0 && oi < MINE_MERC_OFFERS ? items.getOrDefault(mineOfKey(oi, "T"), 0) - 1 : -1;
            cOk = c >= 0 && c < MINE_MERC_NAMES.length;
            int ofLv = cOk ? mineOfLv(items, oi) : 0; // [2026-11-06] 지금 용병을 내보내기 전에(교체 후보는 지금 레벨 기준)
            boolean free = mineMercHiredBefore(items); // [2026-11-11] 급여는 계정에서 처음 계약할 때 한 번만(교체는 무료)
            String[][] cost = cOk && !free ? mineMercSalCost(items.getOrDefault(mineOfKey(oi, "SAL"), 0), ofLv) : null;
            if (!mineAtNpc(row, "mercenary")) { ok = false; msg = "왕도 용병소에서만 계약할 수 있어요."; }
            else if (!cOk) { ok = false; msg = "그 후보는 이제 없어요."; }
            else if (cost != null && !mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "첫 계약 급여(" + mineCostText(cost) + ")가 모자라요." + mineLackText(cost, userName); }
            else {
                if (cost != null) mineTakeCost(userName, cost, 1);
                dao.upsertMineItem(userName, MINE_ITEM_MERC_HIRED, 1); items.put(MINE_ITEM_MERC_HIRED, 1);
                int keepExp = 0; // [2026-11-17] 교체 -- 지금 용병의 경험치도 이어받는다(레벨은 mineOfLv가 같게)
                for (int oc = 0; oc < MINE_MERC_NAMES.length; oc++) if (mineMercLv(items, oc) == ofLv) keepExp = Math.max(keepExp, items.getOrDefault(mineMercKey(oc, "EXP"), 0));
                for (int oc = 0; oc < MINE_MERC_NAMES.length; oc++) if (oc != c) for (String gp : MG_PARTS) { // [2026-11-17] 끼운 동료 장비는 새 용병에게 그대로(직업에 맞게 바뀐다), 새 용병 칸에 이미 있으면 가방으로
                    String os = mineMercKey(oc, "G" + gp), ns = mineMercKey(c, "G" + gp);
                    int og = items.getOrDefault(os, 0);
                    if (og <= 0) continue;
                    if (items.getOrDefault(ns, 0) <= 0) { dao.upsertMineItem(userName, ns, og); items.put(ns, og); }
                    else { String bc = mgCode(gp, mgAcc(gp) ? 9 : c, og); int bq = items.getOrDefault(bc, 0) + 1; dao.upsertMineItem(userName, bc, bq); items.put(bc, bq); }
                    dao.upsertMineItem(userName, os, 0); items.put(os, 0);
                }
                StringBuilder gone = new StringBuilder(); // [2026-11-06] "현재 용병은 사라지고 대체" -- 지금 용병(들)과의 계약을 끝낸다(레벨/경험치/급여 기록도)
                for (int oc = 0; oc < MINE_MERC_NAMES.length; oc++) if (mineMercLv(items, oc) > 0) {
                    gone.append(gone.length() > 0 ? ", " : "").append(mineMercNm(items, oc, "NM"));
                    for (String f : new String[] { "LV", "EXP", "PAID", "MANUAL" }) mineMercSet(userName, items, oc, f, 0);
                    mineMercSet(userName, items, oc, "WARN", -1);
                }
                int lv = Math.min(ofLv, mineMercCapLv(mineLv(row), mineRebirth(items))), el = items.getOrDefault(mineOfKey(oi, "EL"), S5MineMobs.EL_FIRE); // [2026-11-23] 내 레벨까지
                mineMercSet(userName, items, c, "LV", lv);
                mineMercSet(userName, items, c, "EXP", keepExp);
                mineMercSet(userName, items, c, "EL", el);
                mineMercSet(userName, items, c, "NM", items.getOrDefault(mineOfKey(oi, "NM"), 0));
                mineMercSet(userName, items, c, "PAID", (int) (now / 60_000L) + MINE_MERC_DAY_MIN);
                mineMercSet(userName, items, c, "SAL", mineMercRollSal(mineMercLv(items, c)));
                mineMercSet(userName, items, c, "MANUAL", 0);
                mineOfSet(userName, items, oi, "T", 0); // 이 칸(과 같은 직업 칸)은 새로 뽑는다
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON, c + 1); items.put(MINE_ITEM_MERC_ON, c + 1); // 새 용병이 바로 함께
                msg = (gone.length() > 0 ? "👋 " + gone + "은(는) 떠났어요. " : "") + "🛡️ Lv." + lv + " " + S5MineMobs.ELEM_NAMES[el] + " 속성 " + MINE_MERC_NAMES[c] + " " + mineMercNm(items, c, "NM") + "와(과) 계약했어요!" + (cost != null ? " 첫 계약 급여 " + mineCostText(cost) + "를 줬어요. 앞으로 급여는 없어요." : "");
            }
        } else if ("hire2".equals(action)) { // [2026-11-18] 두 번째 용병 -- tier = 후보 칸. 5환생부터, 같은 직업도 된다, 급여 없음.
            //  처음이면 Lv.70(진환생 99레벨 이상이면 Lv.90), 이미 있으면 그 레벨·경험치를 이어받아 바뀐다(끼운 동료 장비도 그대로). 첫 칸 용병은 그대로.
            int oi = tier, k2 = MINE_MERC_K2;
            c = oi >= 0 && oi < MINE_MERC_OFFERS ? items.getOrDefault(mineOfKey(oi, "T"), 0) - 1 : -1;
            cOk = c >= 0 && c < MINE_MERC_NAMES.length;
            int had = mineMercLv(items, k2), lv2 = Math.min(mineMerc2HireLv(row, items), mineMercCapLv(mineLv(row), mineRebirth(items))); // [2026-11-23] 내 레벨까지
            if (!mineAtNpc(row, "mercenary")) { ok = false; msg = "왕도 용병소에서만 계약할 수 있어요."; }
            else if (mineRebirth(items) < MINE_REBIRTH_BASE) { ok = false; msg = "두 번째 용병은 " + MINE_REBIRTH_BASE + "환생부터 계약할 수 있어요."; }
            else if (!cOk) { ok = false; msg = "그 후보는 이제 없어요."; }
            else {
                String gone = had > 0 ? mineMercNm(items, k2, "NM") : null;
                int keepExp = had > 0 ? items.getOrDefault(mineMercKey(k2, "EXP"), 0) : 0, el = items.getOrDefault(mineOfKey(oi, "EL"), S5MineMobs.EL_FIRE);
                mineMercSet(userName, items, k2, "C", c);
                mineMercSet(userName, items, k2, "LV", lv2);
                mineMercSet(userName, items, k2, "EXP", keepExp);
                mineMercSet(userName, items, k2, "EL", el);
                mineMercSet(userName, items, k2, "NM", items.getOrDefault(mineOfKey(oi, "NM"), 0));
                mineOfSet(userName, items, oi, "T", 0); // 이 칸은 새로 뽑는다
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON2, 1); items.put(MINE_ITEM_MERC_ON2, 1); // 바로 함께
                MINE_MERC2.remove(userName); // 체력/대상은 새로
                msg = (gone != null ? "👋 " + gone + "은(는) 떠났어요. " : "") + "🛡️ 두 번째 용병 -- Lv." + lv2 + " " + S5MineMobs.ELEM_NAMES[el] + " 속성 " + MINE_MERC_NAMES[c] + " " + mineMercNm(items, k2, "NM") + "와(과) 계약했어요!";
            }
        } else if ("hire3".equals(action)) { // [2026-11-23] 세 번째 용병 -- tier = 후보 칸. 진환생부터, 같은 직업도 된다, 급여 없음. 처음 Lv.90(내 레벨 상한 안), 이미 있으면 그 레벨·경험치를 이어받아 바뀐다.
            int oi = tier, k3 = MINE_MERC_K3;
            c = oi >= 0 && oi < MINE_MERC_OFFERS ? items.getOrDefault(mineOfKey(oi, "T"), 0) - 1 : -1;
            cOk = c >= 0 && c < MINE_MERC_NAMES.length;
            int had = mineMercLv(items, k3), lv3 = Math.min(had > 0 ? had : MINE_MERC3_LV, mineMercCapLv(mineLv(row), mineRebirth(items)));
            if (!mineAtNpc(row, "mercenary")) { ok = false; msg = "왕도 용병소에서만 계약할 수 있어요."; }
            else if (!mineTrueRebirth(items)) { ok = false; msg = "세 번째 용병은 진환생부터 계약할 수 있어요."; }
            else if (!cOk) { ok = false; msg = "그 후보는 이제 없어요."; }
            else {
                String gone = had > 0 ? mineMercNm(items, k3, "NM") : null;
                int keepExp = had > 0 ? items.getOrDefault(mineMercKey(k3, "EXP"), 0) : 0, el = items.getOrDefault(mineOfKey(oi, "EL"), S5MineMobs.EL_FIRE);
                mineMercSet(userName, items, k3, "C", c);
                mineMercSet(userName, items, k3, "LV", lv3);
                mineMercSet(userName, items, k3, "EXP", keepExp);
                mineMercSet(userName, items, k3, "EL", el);
                mineMercSet(userName, items, k3, "NM", items.getOrDefault(mineOfKey(oi, "NM"), 0));
                mineOfSet(userName, items, oi, "T", 0);
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON3, 1); items.put(MINE_ITEM_MERC_ON3, 1);
                MINE_MERC3.remove(userName);
                msg = (gone != null ? "👋 " + gone + "은(는) 떠났어요. " : "") + "🛡️ 세 번째 용병 -- Lv." + lv3 + " " + S5MineMobs.ELEM_NAMES[el] + " 속성 " + MINE_MERC_NAMES[c] + " " + mineMercNm(items, k3, "NM") + "와(과) 계약했어요!";
            }
        } else if ("pay".equals(action) || "auto".equals(action)) { // [2026-11-11] 날마다 내던 급여 없앰
            ok = false; msg = "이제 용병 급여는 처음 계약할 때 한 번만 내요. 따로 줄 급여가 없어요.";
        } else if ("pay-old".equals(action)) { // (예전) 급여(선금) 하루치
            int nowMin = (int) (now / 60_000L), paid = cOk ? items.getOrDefault(mineMercKey(c, "PAID"), 0) : 0;
            String[][] cost = cOk ? mineMercCost(items, c) : null;
            if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else if (paid - nowMin >= MINE_MERC_PREPAY_DAYS * MINE_MERC_DAY_MIN) { ok = false; msg = "선금은 " + MINE_MERC_PREPAY_DAYS + "일치까지만 줄 수 있어요."; }
            else if (!mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "급여(" + mineCostText(cost) + ")가 모자라요." + mineLackText(cost, userName); }
            else {
                mineTakeCost(userName, cost, 1);
                mineMercSet(userName, items, c, "PAID", Math.max(paid, nowMin) + MINE_MERC_DAY_MIN);
                mineMercSet(userName, items, c, "SAL", mineMercRollSal(mineMercLv(items, c)));
                mineMercSet(userName, items, c, "WARN", -1);
                msg = "💰 " + mineMercNm(items, c, "NM") + "에게 급여 " + mineCostText(cost) + "를 줬어요. 다음 급여는 " + mineCostText(mineMercCost(items, c)) + "예요.";
            }
        } else if ("skill".equals(action)) { // [2026-11-06] 스킬 단계 고르기 -- tier = 직업, code = "1:n"(평타) / "2:n"(2차), n = 0 끔 / 1~ 단계
            String cd = code == null ? "" : code.trim();
            int which = cd.startsWith("2:") ? 2 : cd.startsWith("1:") ? 1 : 0, n = -1;
            try { n = Integer.parseInt(cd.substring(2)); } catch (Exception ex) { n = -1; }
            int mlv = cOk ? mineMercLv(items, c) : 0, max = which == 2 ? mineMercStage2(mlv) : mineMercStage(mlv);
            if (!cOk || mlv <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else if (which == 0 || n < 0 || n > (which == 2 ? 3 : 5)) { ok = false; msg = "그런 단계는 없어요."; }
            else if (n > max) { ok = false; msg = (which == 2 ? "2차 스킬 " : "평타 ") + n + "단계는 용병 Lv." + (which == 2 ? MINE_MERC_SKILL_LV + (n - 1) * 20 : (n - 1) * 20) + "부터 열려요."; }
            else {
                mineMercSet(userName, items, c, which == 2 ? "S2" : "S1", n == 0 ? MINE_MERC_SEL_OFF : n);
                msg = (which == 2 ? "✨ 2차 스킬을 " : "🗡️ 평타를 ") + (n == 0 ? "껐어요." : n + "단계로 맞췄어요.");
            }
        } else if ("mode".equals(action)) { // [2026-11-06] 선공 / 협공 -- tier = 0 협공, 1 선공, [2026-11-18] code = 3이면 두 번째 용병(용병마다 따로)
            int md = tier == 1 ? 1 : 0;
            String mk = String.valueOf(MINE_MERC_K3).equals(code) ? MINE_ITEM_MERC_MODE3 : String.valueOf(MINE_MERC_K2).equals(code) ? MINE_ITEM_MERC_MODE2 : MINE_ITEM_MERC_MODE; // [2026-11-23] 4 = 세 번째
            dao.upsertMineItem(userName, mk, md);
            items.put(mk, md);
            msg = md == 1 ? "⚔️ 선공 모드 -- 용병이 사거리 안의 몬스터를 먼저 공격해요." : "🤝 협공 모드 -- 용병이 나와 싸우는 몬스터만 함께 공격해요.";
        } else if ("lv99".equals(action)) { // [2026-11-06] "관리자는 레벨을 99로 올리는 기능"
            if (!isMineAdmin(userName)) { ok = false; msg = "관리자만 쓸 수 있어요."; }
            else if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else { mineMercSet(userName, items, c, "LV", MINE_LV_MAX); mineMercSet(userName, items, c, "EXP", 0); msg = "🛠️ " + mineMercNm(items, c, "NM") + "을(를) Lv." + MINE_LV_MAX + "로 올렸어요."; }
        } else if ("auto-old".equals(action)) { // (예전) 자동 차감 켜기/끄기
            if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else {
                boolean manual = items.getOrDefault(mineMercKey(c, "MANUAL"), 0) <= 0;
                mineMercSet(userName, items, c, "MANUAL", manual ? 1 : 0);
                msg = manual ? "⏰ 급여 자동 차감을 껐어요. 급여 날마다 직접 줘야 해요." : "💰 급여 자동 차감을 켰어요. 급여 날마다 재료에서 알아서 빠져요.";
                if (!manual) mineMercPayroll(userName, items, now, pay);
            }
        } else if ("use".equals(action)) { // 데리고 다니기 / 쉬게 하기
            if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else if (c == MINE_MERC_K3) { // [2026-11-23] 세 번째 용병
                boolean on = items.getOrDefault(MINE_ITEM_MERC_ON3, 0) <= 0;
                if (on && !mineTrueRebirth(items)) { ok = false; msg = "세 번째 용병은 진환생부터 데리고 다닐 수 있어요."; }
                else {
                    dao.upsertMineItem(userName, MINE_ITEM_MERC_ON3, on ? 1 : 0);
                    items.put(MINE_ITEM_MERC_ON3, on ? 1 : 0);
                    msg = on ? "🛡️ " + mineMercNm(items, c, "NM") + "와(과) 함께 다녀요." : "🛡️ " + mineMercNm(items, c, "NM") + "은(는) 용병소에서 쉬어요.";
                }
            }
            else if (c == MINE_MERC_K2) { // [2026-11-18] 두 번째 용병
                boolean on = !mineMercOn2(items);
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON2, on ? 1 : 0);
                items.put(MINE_ITEM_MERC_ON2, on ? 1 : 0);
                msg = on ? "🛡️ " + mineMercNm(items, c, "NM") + "와(과) 함께 다녀요." : "🛡️ " + mineMercNm(items, c, "NM") + "은(는) 용병소에서 쉬어요.";
            }
            else {
                boolean on = mineMercOn(items) != c;
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON, on ? c + 1 : 0);
                items.put(MINE_ITEM_MERC_ON, on ? c + 1 : 0);
                msg = on ? "🛡️ " + mineMercNm(items, c, "NM") + "와(과) 함께 다녀요." : "🛡️ " + mineMercNm(items, c, "NM") + "은(는) 용병소에서 쉬어요."; // [2026-11-06] "용병 마법사가 쉬어요 하지 말고 이름을"
            }
        }
        MinePresence pr = MINE_PRESENCE.get(userName);
        if (pr != null && ok && !"list".equals(action)) pr.lookAt = 0;
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", ok);
        out.put("message", msg);
        out.put("list", mineMercCards(items));
        if (!pay.isEmpty()) out.put("mercPay", pay);
        if (mineAtNpc(row, "mercenary")) { // [2026-11-06] 계약 후보(캐러셀) -- 직업마다 하나
            Map<String, Integer> have = mineOreHave(userName);
            List<HashMap<String, Object>> offers = new ArrayList<>();
            mineMercOffers(userName, items);
            for (int oi = 0; oi < MINE_MERC_OFFERS; oi++) { HashMap<String, Object> or = mineMercOfferRow(items, oi, have); if (or != null) offers.add(or); }
            out.put("offers", offers);
            out.put("full", mineMercCount(items) >= MINE_MERC_MAX); // [2026-11-06] 지금은 한 명만
            out.put("curLv", mineMercCurLv(items)); // [2026-11-06] 교체 후보는 지금보다 2~15레벨 낮게
            out.put("slot2Open", mineRebirth(items) >= MINE_REBIRTH_BASE); // [2026-11-18] 두 번째 용병(5환생부터) -- 같은 날 일반 유저 공개
            out.put("slot2Hide", false);
            out.put("slot2Rb", MINE_REBIRTH_BASE);
            out.put("slot2Lv", mineMerc2HireLv(row, items));
            out.put("slot3Open", mineTrueRebirth(items)); // [2026-11-23] 세 번째 용병(진환생부터)
            out.put("slot3Lv", Math.min(mineMercLv(items, MINE_MERC_K3) > 0 ? mineMercLv(items, MINE_MERC_K3) : MINE_MERC3_LV, mineMercCapLv(mineLv(row), mineRebirth(items))));
        }
        out.put("merc", mineMercInfo(userName, items, System.currentTimeMillis()));
        out.put("merc2", mineMercInfo2(userName, items, System.currentTimeMillis()));
        out.put("merc3", mineMercInfo3(userName, items, System.currentTimeMillis())); // [2026-11-23]
        out.put("ores", mineOreList(userName));
        return out;
    }

    // [2026-11-05] "2환생 특전으로 검기 1레벨(0에서 1만) 하나 무료 -- 검기 사범에게 환생 특전 받기 버튼 따로" -- 계정당 한 번(AURA_RB_GIFT = 1).
    //  아직 0단계인 속성(보스가 있어 가르칠 수 있는 속성)만 고를 수 있고, 2단계부터는 원래대로 재료로 배운다.
    static final String MINE_ITEM_AURA_GIFT = "AURA_RB_GIFT";
    static final int MINE_AURA_GIFT_RB = 2;
    @Override
    @Transactional
    public HashMap<String, Object> mineAuraGift(String userName, int el) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName); // 같은 유저의 동시 요청은 여기서 줄 선다(두 번 받기 방지)
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, "swordmaster")) { out.put("message", "왕도 검기 사범 앞에서만 받을 수 있어요."); return out; }
        Map<String, Integer> items = mineItems(userName);
        if (mineRebirth(items) < MINE_AURA_GIFT_RB) { out.put("message", MINE_AURA_GIFT_RB + "환생을 하면 받을 수 있는 특전이네."); return out; }
        if (items.getOrDefault(MINE_ITEM_AURA_GIFT, 0) > 0) { out.put("message", "환생 특전은 이미 받았네."); return out; }
        if (el < 1 || el > 8 || MINE_AURA_BOSS_ITEM[el] == null) { out.put("message", "그 속성의 검기는 아직 가르칠 수 없네."); return out; }
        int cur = items.getOrDefault(mineAuraKey(false, el), 0), act = items.getOrDefault(mineAuraKey(true, el), 0);
        if (cur > 0) { out.put("message", "이미 배운 속성이네. 아직 안 배운 속성을 골라 보게."); return out; }
        dao.upsertMineItem(userName, MINE_ITEM_AURA_GIFT, 1);
        items.put(MINE_ITEM_AURA_GIFT, 1);
        dao.upsertMineItem(userName, mineAuraKey(false, el), 1);
        items.put(mineAuraKey(false, el), 1);
        if (act == 0) { dao.upsertMineItem(userName, mineAuraKey(true, el), 1); items.put(mineAuraKey(true, el), 1); }
        out.put("ok", true);
        out.put("message", "🎁 검기 사범: 환생한 기념이네! " + S5MineMobs.ELEM_NAMES[el] + " 검기 1단계를 공짜로 가르쳐 주지. (공격력의 " + MINE_AURA_PCT + "% 속성 피해, 사거리 1칸)");
        out.put("aura", mineAuraInfo(items));
        return out;
    }

    /** [2026-11-04] 인벤토리 스킬 탭 -- 속성 el(1~8)의 검기를 lv단계로 켠다(0 = 끔, 배운 단계까지). */
    @Override
    @Transactional
    public HashMap<String, Object> mineAuraSet(String userName, int el, int lv) {
        HashMap<String, Object> out = new HashMap<>();
        if (!mineAccess(userName)) return mineClosed();
        Map<String, Integer> items = mineItems(userName);
        int have = Math.min(items.getOrDefault(mineAuraKey(false, el), 0), MINE_AURA_MAX);
        if (el < 1 || el > 8 || lv < 0 || lv > have) { out.put("ok", false); out.put("message", "아직 배우지 않은 단계예요."); return out; }
        dao.upsertMineItem(userName, mineAuraKey(true, el), lv);
        items.put(mineAuraKey(true, el), lv);
        out.put("ok", true);
        out.put("aura", mineAuraInfo(items));
        return out;
    }

    static HashMap<String, Object> mineEnchInfo(Map<String, Integer> items) {
        HashMap<String, Object> m = new HashMap<>();
        int left = items.getOrDefault(MINE_ITEM_ENCH_LEFT, 0);
        m.put("el", left > 0 ? items.getOrDefault(MINE_ITEM_ENCH_EL, 0) : 0);
        m.put("left", left);
        int aleft = items.getOrDefault(MINE_ITEM_AEL_LEFT, 0); // [2026-11-22] 갑옷 속성
        m.put("ael", MINE_AEL_ON && aleft > 0 ? items.getOrDefault(MINE_ITEM_AEL, 0) : 0); // [2026-11-23] 갑옷 속성 없앰
        m.put("aleft", MINE_AEL_ON ? aleft : 0);
        return m;
    }
    static final int MINE_PARASOL_CUT = 2;
    // [2026-10-28] "조금 더 저렴하게, 다도해 보석이 아니어도" -- 진주 10 + 산호석 10 + 금 50 -> 금광석 30 + 은광석 20
    static final String[][] MINE_PARASOL_COST = { { S5MineMap.ORE_CODES[3], "30" }, { S5MineMap.ORE_CODES[2], "20" } };
    /** [2026-10-28] "무기와 채집 도구를 별도로 관리" -- 무기(칼/양산)와 곡괭이(채집 도구)는 칸이 따로라 양산을 들어도 캘 수 있다. "양산은 공격력 20% 대신 +10" -- 고정 +10. */
    static final int MINE_PARASOL_ATK_FLAT = 10;

    static boolean mineParasolOn(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_WEAPON_SLOT, 0) == 1 && items.getOrDefault(MINE_ITEM_PARASOL, 0) > 0;
    }

    // [2026-11-05] "왕도 NPC가 유저 이야기를 -- 곧 5환생, 환생하면 날개, 혼자서 보스, 달빛석 발견 좌표" -- 왕도 주민 말풍선(화면이 1분마다 받아 간다).
    //  사건(보스 처치/달빛석/전설 이상 무기)은 서버 메모리에 최근 30개(6시간), 환생 임박은 유저 표에서 2분마다 다시 센다.
    static final java.util.concurrent.ConcurrentLinkedDeque<Object[]> MINE_GOSSIP = new java.util.concurrent.ConcurrentLinkedDeque<>();
    static final long MINE_GOSSIP_TTL_MS = 6 * 3_600_000L;
    static volatile List<String> mineGossipRb = new ArrayList<>();
    static volatile long mineGossipRbAt = 0;
    static String mineNick(String u) { int i = u == null ? -1 : u.indexOf('/'); return i > 0 ? u.substring(0, i) : u; }
    static void mineGossipAdd(String text) {
        MINE_GOSSIP.addFirst(new Object[] { System.currentTimeMillis(), text });
        while (MINE_GOSSIP.size() > 30) MINE_GOSSIP.pollLast();
    }
    // [2026-11-05] "NPC 대화 등록 사전 -- 업데이트 내역을 고지하는 대신 사전에 등록해 NPC가 가끔 말하게(자동화)" -- 클래스패스 s5/npc_talk.txt
    //  ('@yyyy-MM-dd' 줄 아래에 대사만, # 주석). 서버가 처음 부를 때 한 번 읽고(배포 = 새 파일), 날짜로부터 MINE_NPC_TALK_DAYS일 안의 줄만 내준다.
    static final int MINE_NPC_TALK_DAYS = 14;
    static volatile List<String[]> mineNpcTalkAll = null;
    @Override
    public List<String> mineNpcTalk() {
        List<String[]> all = mineNpcTalkAll;
        if (all == null) {
            all = new ArrayList<>();
            try (java.io.InputStream in = S55MineServiceImpl.class.getResourceAsStream("/s5/npc_talk.txt")) {
                if (in != null) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    String day = null; // [2026-11-05] "대사에 날짜는 빼줘" -- '@yyyy-MM-dd' 줄이 아래 대사들의 날짜
                    for (String ln; (ln = br.readLine()) != null; ) {
                        ln = ln.trim();
                        if (ln.isEmpty() || ln.startsWith("#")) continue;
                        if (ln.startsWith("@")) { day = ln.substring(1).trim(); continue; }
                        if (day != null) all.add(new String[] { day, ln });
                    }
                }
            } catch (Exception ignore) { }
            mineNpcTalkAll = all;
        }
        java.time.LocalDate today = java.time.LocalDate.now(KST);
        List<String> out = new ArrayList<>();
        for (String[] e : all) {
            try { if (!java.time.LocalDate.parse(e[0]).plusDays(MINE_NPC_TALK_DAYS).isBefore(today) && !e[1].isEmpty()) out.add(e[1]); } catch (Exception ignore) { }
        }
        return out;
    }

    @Override
    public List<String> mineGossip() {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        for (Object[] g : MINE_GOSSIP) if (now - (Long) g[0] <= MINE_GOSSIP_TTL_MS && out.size() < 10) out.add((String) g[1]);
        if (now - mineGossipRbAt > 120_000L) {
            mineGossipRbAt = now;
            List<String> rbl = new ArrayList<>();
            try {
                for (HashMap<String, Object> row : dao.selectAllMineUsers()) {
                    String u = strVal(row.get("USER_NAME"), "");
                    if (u == null || u.isEmpty() || isMineAdmin(u)) continue;
                    int lv = mineLv(row);
                    if (lv < 95) continue;
                    int rb = mineRebirth(mineItems(u));
                    if (rb >= MINE_REBIRTH_MAX) continue; // [2026-11-07] "5환생이 끝인데 NPC가 곧 6환생한다고 한다" -- 마지막 환생까지 한 사람은 빼기
                    rbl.add(mineNick(u) + " 님이 곧 " + mineRbName(rb + 1) + "을 한대요!");
                }
            } catch (Exception ignore) { }
            mineGossipRb = rbl;
        }
        out.addAll(mineGossipRb);
        out.add("환생을 하면 등 뒤에 날개가 돋아난다더라고요.");
        return out;
    }

    // [2026-11-05] "금괴 반복 소모 -- 왕도 상인, 10분간 경험치 3배, 드랍률 2배" -- 행운의 주문서. 끝나는 시각은 광산 아이템 LUCK_UNTIL(초)에, 사면 남은 시간에 10분을 더한다.
    public static final String[] MINE_GRADE_KO = { "일반", "고급", "희귀", "영웅", "전설", "유물", "고대", "신화" };
    static final String MINE_ITEM_LUCK = "LUCK_UNTIL";
    static final long MINE_LUCK_MS = 10 * 60_000L;
    static final String[][] MINE_LUCK_COST = { { S5MineMap.BAR_CODES[3], "1" } }; // 금괴 1개
    static long mineLuckUntil(Map<String, Integer> items) { return mineTrueRebirth(items) ? 0L : items.getOrDefault(MINE_ITEM_LUCK, 0) * 1000L; } // [2026-11-08] 진환생은 행운의 주문서가 듣지 않는다
    static boolean mineLuckOn(Map<String, Integer> items, long now) { return mineLuckUntil(items) > now; }
    static long mineLuckExp(Map<String, Integer> items, long exp, long now) { return mineLuckOn(items, now) ? exp * 3 : exp; }
    // [2026-11-23] "금괴 NPC가 개미굴 지도를 팔게 -- 금괴 1개면 10분 동안 개미굴 전체 지도의 4분의 1쯤(내 캐릭터 주변)을 밝혀 주게" --
    //  끝나는 시각은 ANTMAP_UNTIL(초), 사면 남은 시간에 10분을 더한다. 화면이 개미굴 미니맵에서 내 둘레 가로·세로 절반 창만 보여 준다(진환생도 살 수 있다).
    static final String MINE_ITEM_ANTMAP = "ANTMAP_UNTIL";
    static final long MINE_ANTMAP_MS = 10 * 60_000L;
    static long mineAntMapUntil(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_ANTMAP, 0) * 1000L; }

    @Override
    @Transactional
    public HashMap<String, Object> mineCityShop(String userName, String action) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        Map<String, Integer> items = mineItems(userName);
        long now = System.currentTimeMillis();
        boolean ok = true;
        String msg = "";
        if ("antmap".equals(action)) { // [2026-11-23] 개미굴 횃불(금괴 1개, 10분 -- 같은 날 "개미굴 횃불, 미니맵에 둥근 형태")
            if (!mineAtNpc(row, "citymerchant")) { ok = false; msg = "왕도의 왕도 상인에게서만 살 수 있어요."; }
            else if (!mineCostOk(MINE_LUCK_COST, mineOreHave(userName))) { ok = false; msg = "재료가 부족해요. (" + mineCostText(MINE_LUCK_COST) + " 필요" + mineLackText(MINE_LUCK_COST, userName) + ")"; }
            else {
                mineTakeCost(userName, MINE_LUCK_COST, 1);
                long until = Math.max(now, mineAntMapUntil(items)) + MINE_ANTMAP_MS;
                dao.upsertMineItem(userName, MINE_ITEM_ANTMAP, (int) (until / 1000L));
                items.put(MINE_ITEM_ANTMAP, (int) (until / 1000L));
                msg = "🔥 개미굴 횃불을 밝혔어요! 10분 동안 개미굴 미니맵에 내 주변이 둥글게 보여요(전체의 4분의 1쯤). (" + mineCostText(MINE_LUCK_COST) + " 사용)";
            }
        }
        if ("buy".equals(action)) {
            if (!mineAtNpc(row, "citymerchant")) { ok = false; msg = "왕도의 왕도 상인에게서만 살 수 있어요."; }
            else if (mineTrueRebirth(items)) { ok = false; msg = "🍀 왕도 상인: 진환생하신 분께는 행운의 주문서가 듣지 않아요. 스스로의 힘으로 오르셔야 해요."; } // [2026-11-08]
            else if (!mineCostOk(MINE_LUCK_COST, mineOreHave(userName))) { ok = false; msg = "재료가 부족해요. (" + mineCostText(MINE_LUCK_COST) + " 필요" + mineLackText(MINE_LUCK_COST, userName) + ")"; }
            else {
                mineTakeCost(userName, MINE_LUCK_COST, 1);
                long until = Math.max(now, mineLuckUntil(items)) + MINE_LUCK_MS;
                dao.upsertMineItem(userName, MINE_ITEM_LUCK, (int) (until / 1000L));
                items.put(MINE_ITEM_LUCK, (int) (until / 1000L));
                msg = "🍀 행운의 주문서를 읽었어요! 10분 동안 경험치 3배 · 드랍률 2배 (" + mineCostText(MINE_LUCK_COST) + " 사용)";
            }
        }
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", ok);
        out.put("message", msg);
        out.put("luckUntil", mineLuckUntil(items));
        out.put("antMapUntil", mineAntMapUntil(items)); // [2026-11-23]
        out.put("cost", mineCostText(MINE_LUCK_COST));
        out.put("can", mineCostOk(MINE_LUCK_COST, mineOreHave(userName)) && !mineTrueRebirth(items));
        if (mineTrueRebirth(items)) out.put("trueRb", true); // [2026-11-08] 진환생 -- 주문서 못 씀
        out.put("ores", mineOreList(userName));
        return out;
    }

    /** 다도해 상인 -- action list(보기) / buy(code). 지금은 양산 하나. */
    @Override
    @Transactional
    public HashMap<String, Object> mineArchiShop(String userName, String action, String code) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        Map<String, Integer> items = mineItems(userName);
        String msg = null;
        boolean ok = true;
        if ("buy".equals(action)) {
            if (!mineAtNpc(row, "archishop")) { ok = false; msg = "다도해 마을의 다도해 상인에게서만 살 수 있어요."; }
            else if (!MINE_ITEM_PARASOL.equals(code)) { ok = false; msg = "그런 물건은 없어요."; }
            else if (items.getOrDefault(MINE_ITEM_PARASOL, 0) > 0) { ok = false; msg = "양산은 이미 가지고 있어요. 🎒 장비 창의 무기 칸에서 들 수 있어요."; }
            else if (!mineCostOk(MINE_PARASOL_COST, mineOreHave(userName))) { ok = false; msg = "재료가 부족해요. (" + mineCostText(MINE_PARASOL_COST) + " 필요" + mineLackText(MINE_PARASOL_COST, userName) + ")"; }
            else {
                mineTakeCost(userName, MINE_PARASOL_COST, 1);
                dao.upsertMineItem(userName, MINE_ITEM_PARASOL, 1);
                dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, 1);
                msg = "⛱️ 양산을 사서 들었어요! 공격력 +" + MINE_PARASOL_ATK_FLAT + ", 다도해 햇빛 피해 -" + MINE_PARASOL_CUT + "단계 (" + mineCostText(MINE_PARASOL_COST) + " 사용)";
            }
        }
        HashMap<String, Object> out = mineGearResult(userName, ok, msg == null ? "" : msg);
        if (ok && "buy".equals(action)) out.put("craftedRing", true);
        return out;
    }

    static int mineGearHeatPct(Map<String, Integer> items) {
        return (mineCoatDef(items) > 0 ? 1 : 0) + (mineGlovesOn(items) ? 1 : 0);
    }

    /** 한기 방어 합계 = 외투 + 방한 장갑. */
    static int mineColdDef(Map<String, Integer> items) {
        return mineCoatDef(items) + (mineGlovesOn(items) ? mineGloveTier(items) : 0) // [2026-10-28] 장갑도 단계만큼
                + mineRebirth(items) // [2026-10-29] "환생으로 방한 +1씩"
                + mineArmorRes(items); // [2026-11-06] 유물 이상 갑옷 -- 예전엔 피해 1%씩 경감이라 2환생도 한기 1단계를 못 막았다
    }

    static boolean mineBlackOpen(long now) {
        return java.time.Instant.ofEpochMilli(now).atZone(KST).getMinute() < MINE_BLACK_OPEN_MIN;
    }

    /** 이번 암상인이 사라지는 시각(열려 있을 때) 또는 다음에 나타나는 시각(닫혀 있을 때). */
    static long mineBlackNextChange(long now) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(now).atZone(KST).withSecond(0).withNano(0);
        return mineBlackOpen(now) ? z.withMinute(MINE_BLACK_OPEN_MIN).toInstant().toEpochMilli() : z.withMinute(0).plusHours(1).toInstant().toEpochMilli();
    }

    static String[][] mineCostTimes(String[][] cost, int pct) {
        String[][] out = new String[cost.length][];
        for (int i = 0; i < cost.length; i++) out[i] = new String[] { cost[i][0], String.valueOf((Integer.parseInt(cost[i][1]) * pct + 99) / 100) };
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
        if (!mineMoveOk(minePos(row, map, mineWalkMode(items)), longOf(row.get("POS_AT_MS"), 0L), x, y, now, mineStepMs(items))) return out;
        if (mineWbDownUntil(userName, now) > 0) { out.put("wbDown", mineWbDownUntil(userName, now)); return out; } // [2026-11-17] 쓰러져 있는 10초 -- 환경 피해 없음
        double rbCut = 1; // [2026-10-29] 환생은 % 경감 대신 방한/햇빛 단계 +1(mineColdDef / mineSunPct) -- 피해량 배수는 1
        int def = mineColdDef(items), eff = Math.max(0, map.cold() + (map.cold() > 0 && map.outdoor() && S5MineMap.night(now) ? MINE_NIGHT_COLD : 0) - def); // [2026-10-28] 외투 + 방한 장갑, [2026-11-05] 밤엔 한기 +1단계
        boolean safe = map.safeAt(x, y), raid = S5MineMap.RUINS.equals(map.id), hot = mineHotMap(map);
        int heat = hot ? mineHeatPct(map, items, now) : 0; // [2026-10-28] 더위(초당 최대 체력 %)
        // [2026-10-28] 환생 경감을 뺀 실제 초당 % -- 화면 표시용(햇빛 = 회차당 20%, 한기/장비 더위 = 회차당 1%)
        double heatRate = hot ? mineSunPct(map, items, now) + mineGearHeatPct(items) * rbCut : 0;
        out.put("heat", heat);
        out.put("heatRate", Math.round(heatRate * 100) / 100.0);
        if (map.sun() > 0) { out.put("day", S5MineMap.archiDay(now)); out.put("dayChangeAt", S5MineMap.archiNextChange(now)); out.put("sun", map.sun()); }
        out.put("ok", true);
        out.put("cold", map.cold());
        out.put("coldEff", eff);
        out.put("coldRate", Math.round(eff * rbCut * 100) / 100.0);
        out.put("rebirth", mineRebirth(items));
        out.put("coatDef", def);
        out.put("safe", safe);
        boolean iceFrozen = S5MineMap.PEAK.equals(map.id) && mineIceTick(userName, map, now, out); // [2026-10-28] 설산 봉우리 -- 얼음 여왕 빙결/얼음땡(언 동안 한기 없음)
        boolean thunderMap = S5MineMap.WESTROAD.equals(map.id); // [2026-11-05] 마물의 숲 입구 -- 천둥 황소 장판/전체 낙뢰
        boolean harpyMap = S5MineMap.HARPY_NEST.equals(map.id); // [2026-11-11] 하피 돌풍
        if (map.cold() <= 0 && !raid && !hot && !thunderMap && !harpyMap) { MINE_COLD.remove(userName); return out; }
        if (map.cold() > 0) out.put("nextBlizzardAt", S5MineMap.nextBlizzard(map.id, now));
        ColdState cs = MINE_COLD.computeIfAbsent(userName, u -> new ColdState());
        long from = map.version().equals(cs.mapVer) ? Math.max(cs.at, now - MINE_COLD_MAX_GAP_MS) : now;
        cs.mapVer = map.version();
        cs.at = now;
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), lv = mineLv(row), rb = mineRebirth(items), cap = mineHpCapN(lv, lock, rb); // [2026-11-21] 최대 체력 % 피해는 원래 최대 기준(각성 2배 아님)
        int coldDmg = 0, bzDmg = 0, fireDmg = 0, gasDmg = 0, heatDmg = 0;
        if (heat > 0) { // [2026-10-28] 더위 -- 한기처럼 초당 깎는다(그늘/안전지대에선 없음)
            if (safe) cs.frac = 0;
            else {
                double drain = cap * heatRate / 100.0 * Math.max(0, now - from) / 1000.0 + cs.frac; // 환생 경감 적용된 비율
                heatDmg = (int) drain;
                cs.frac = drain - heatDmg;
            }
        }
        int shockDmg = 0, stormDmg = 0;
        if (thunderMap) { // [2026-11-05] 천둥 황소 -- 전기 장판 위면 초당 피해, 전체 낙뢰(감전 스턴)
            List<Integer> shock = mineShockCells(now);
            out.put("shock", shock);
            if (mineThunderOn(now)) {
                out.put("thunderOn", true);
                if (!safe) { // [2026-11-06] "보스가 나오면 초당 1% 전기 피해"(맵 전체, 입구 안전지대 빼고)
                    double drain = cap * MINE_THUNDER_AIR_PCT / 100.0 * Math.min(2_000L, Math.max(0, now - from)) / 1000.0 + cs.frac;
                    shockDmg += (int) drain;
                    cs.frac = drain - (int) drain;
                    out.put("airPct", MINE_THUNDER_AIR_PCT);
                } else cs.frac = 0;
                int tatk = S5MineMobs.TYPES.get(S5MineMobs.THUNDER_BOSS_TYPE).atk();
                if (shock.contains(y * map.w + x)) shockDmg = mineCoatHit(items, Math.max(1, (int) Math.round(tatk * MINE_ZONE_MULT)), S5MineMobs.TYPES.get(S5MineMobs.THUNDER_BOSS_TYPE).elem()); // [2026-11-22] 장판 = 황소 속성
                synchronized (MINE_THUNDER_LOCK) {
                    if (mineThunderStormNext <= 0) mineThunderStormNext = now + 15_000L;
                    if (now >= mineThunderStormNext) { mineThunderStormAt = now; mineThunderStormNext = now + 18_000L + RND.nextInt(6_000); }
                }
                out.put("stormNext", mineThunderStormNext);
                Long seen = MINE_THUNDER_STORM_SEEN.get(userName);
                if (mineThunderStormAt > 0 && now - mineThunderStormAt < 3_000L && (seen == null || seen < mineThunderStormAt)) {
                    MINE_THUNDER_STORM_SEEN.put(userName, mineThunderStormAt);
                    MINE_STUN.put(userName, now + MINE_STORM_STUN_MS);
                    stormDmg = mineCoatHit(items, Math.max(1, (int) Math.round(tatk * MINE_STORM_MULT)), S5MineMobs.TYPES.get(S5MineMobs.THUNDER_BOSS_TYPE).elem());
                    out.put("storm", mineThunderStormAt);
                    out.put("stunUntil", now + MINE_STORM_STUN_MS);
                }
            }
        }
        int lavaDmg = 0;
        if (S5MineMap.ISLE7.equals(map.id)) { // [2026-10-28] 용암 거신 -- 용암 칸 위면 초당 최대 체력 MINE_LAVA_PCT%
            List<Integer> lava = mineLavaCells(map, now);
            out.put("lava", lava);
            long el = Math.min(2_000L, Math.max(0, now - from));
            if (el > 0 && lava.contains(y * map.w + x)) lavaDmg = Math.max(1, (int) (cap * MINE_LAVA_PCT / 100 * el / 1000.0));
        }
        if (map.cold() > 0) {
            if (safe || iceFrozen) cs.frac = 0;
            else {
                double drain = cap * eff / 100.0 * rbCut * Math.max(0, now - from) / 1000.0 + cs.frac;
                coldDmg = (int) drain;
                cs.frac = drain - coldDmg;
            }
            if (S5MineMap.blizzardBetween(map.id, from, now) > 0) {
                if (safe) out.put("blizzardSafe", true);
                else if (!iceFrozen) bzDmg = Math.max(1, (int) (cap * MINE_BLIZZARD_PCT / 100 * rbCut)); // [2026-10-28] 환생 회차당 1% 경감
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
                int atk = mineCoatHit(items, S5MineMobs.TYPES.get(S5MineMobs.RAID_BOSS_TYPE).atk(), S5MineMobs.TYPES.get(S5MineMobs.RAID_BOSS_TYPE).elem()); // [2026-11-22] 패턴 = 대주교 속성
                int bx = bs != null && bs.frozenUntil > now ? bs.fx : boss.x, by = bs != null && bs.frozenUntil > now ? bs.fy : boss.y;
                if (nf <= now) {
                    out.put("fireAt", nf);
                    if (Math.max(Math.abs(x - bx), Math.abs(y - by)) <= MINE_RAID_FIRE_RANGE) fireDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 2.5);
                }
                if (ng <= now) { out.put("gasAt", ng); gasDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 1.2); }
            }
        }
        int gustDmg = harpyMap ? mineHarpyGust(userName, map, items, x, y, now, out) : 0; // [2026-11-11]
        if (gustDmg > 0) out.put("gustDmg", gustDmg);
        if (coldDmg + bzDmg + fireDmg + gasDmg + heatDmg + lavaDmg + shockDmg + stormDmg + gustDmg <= 0) return out;
        int envDmg = coldDmg + bzDmg + fireDmg + gasDmg + heatDmg + lavaDmg + shockDmg + stormDmg + gustDmg;
        if (mineBerserkInvul(userName, now)) { out.put("berserkInv", true); return out; } // [2026-11-20] "광전사 불굴을 모든 피해에" -- 무적 중엔 환경 피해(한기/눈보라/더위/용암/전기/불벼락/가스/돌풍)도 없음
        if (shockDmg > 0) out.put("shockDmg", shockDmg);
        if (stormDmg > 0) out.put("stormDmg", stormDmg);
        boolean save = mineHpFlushDue(row, now) || mineHpNow(row, now, lock, rb) - envDmg <= 0; // [2026-11-03] 체력만 바뀌면 메모리에, 쓰러지거나 15초마다 DB
        HashMap<String, Object> lrow = save ? dao.selectMineUserForUpdate(userName) : row; // 같은 유저의 공격/전투 턴과 겹치지 않게 잠그고 다시 읽는다
        if (lrow == null) lrow = row;
        lv = mineLv(lrow); // [2026-10-28] 잠근 행의 레벨로(그 사이 레벨업했으면 옛 레벨로 덮어쓰지 않게)
        long exp = Math.max(0, longOf(lrow.get("M_EXP"), 0L));
        int hp = mineHpNow(lrow, now, lock, rb) - envDmg;
        if (lavaDmg > 0) out.put("lavaDmg", lavaDmg);
        if (coldDmg > 0) out.put("coldDmg", coldDmg);
        if (heatDmg > 0) out.put("heatDmg", heatDmg);
        if (bzDmg > 0) out.put("blizzardDmg", bzDmg);
        if (fireDmg > 0) out.put("fireDmg", fireDmg);
        if (gasDmg > 0) out.put("gasDmg", gasDmg);
        if (hp <= 0 && mineBerserkSave(userName, items, now, out)) hp = 1; // [2026-11-20] 광전사 불굴 -- 환경 피해로 쓰러질 참이어도
        if (hp <= 0) {
            boolean spell = fireDmg + gasDmg + gustDmg > 0, burnt = lavaDmg > 0 && !spell, shocked = shockDmg + stormDmg > 0 && !spell && !burnt; // [2026-11-05] 천둥 황소
            if (spell || burnt || shocked) lock = mineLockUp(userName, lock, out); // 대주교 마법 = 전투 사망(최대 체력 잠금), [2026-10-28] 용암도(보스전)
            hp = mineHpCapN(lv, lock, rb);
            mineDisengageAll(userName);
            MINE_COLD.remove(userName);
            out.put("died", true);
            out.put("message", gustDmg > 0 ? "🌪️ 하피 여왕의 돌풍에 휩쓸려 쓰러졌어요... " + mineRespawnMsg(userName, items, now) + mineLockText(out) : shocked ? "⚡ 천둥에 감전되어 쓰러졌어요... " + mineRespawnMsg(userName, items, now) + mineLockText(out) : burnt ? "🌋 용암에 데어 쓰러졌어요... " + mineRespawnMsg(userName, items, now) + mineLockText(out) : spell ? "💀 타락한 대주교의 " + (fireDmg > 0 ? "불벼락" : "어둠가스") + "에 쓰러졌어요... " + mineRespawnMsg(userName, items, now) + mineLockText(out)
                    : heatDmg > 0 && coldDmg + bzDmg == 0 ? (map.sun() > 0 ? "☀️ 뜨거운 햇볕에 쓰러졌어요... " : "🥵 더위를 견디지 못하고 쓰러졌어요... ") + mineRespawnMsg(userName, items, now)
                    : "🥶 " + (bzDmg > 0 ? "눈바람에 휩쓸려" : "추위를 견디지 못하고") + " 쓰러졌어요... " + mineRespawnMsg(userName, items, now));
        }
        if (out.containsKey("died") && mineWbDownUntil(userName, now) > 0) out.put("wbDown", mineWbDownUntil(userName, now)); // [2026-11-17] 그 자리에서 10초
        if (save || out.containsKey("died")) { dao.updateMineCombat(userName, lv, exp, hp, now, 0); MINE_HP_VIRT.remove(userName); }
        else mineHpKeep(userName, row, hp, now);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, mineEngaged(userName, now), lock, rb));
        if (out.containsKey("died")) out.put("state", mineState(userName));
        return out;
    }

    /** 방한 외투 -- action: list(보기) / buy(code) / wear(code, 다시 누르면 벗기). 파는 곳은 저장 위치 맵으로 정한다:
     *  설산 마을 = 털옷 장인(제값), 북쪽 끝 설원 = 암상인(1.5배, 매시 정각~15분에만). */
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
        // [2026-10-28] 의뢰 사슬 -- action: up(code coat/gloves = 다음 단계 재료를 건넨다), wear(code coat/gloves). 예전 buy/quest/gloves도 받아 준다.
        boolean gl = "gloves".equals(code) || MINE_ITEM_GLOVES.equals(code) || "quest".equals(action) || "gloves".equals(action);
        if ("buy".equals(action) || "quest".equals(action) || "gloves".equals(action)) action = "up";
        if ("up".equals(action)) {
            int cur = gl ? mineGloveTier(items) : mineCoatTier(items), next = cur + 1;
            String nm = next <= 3 ? (gl ? MINE_GLOVE_NAMES : MINE_COAT_NAMES)[next - 1] : "";
            if (!"town".equals(seller)) { ok = false; msg = "black".equals(seller) ? "🕶️ 이제 털옷은 안 판다네... 설산 마을 털옷 장인의 의뢰로만 만들 수 있지." : "털옷 장인의 의뢰는 설산 마을에서만 받을 수 있어요."; }
            else if (cur >= 3) { ok = false; msg = (gl ? "🧤 장갑" : "🧥 외투") + "은(는) 이미 마지막 단계예요."; }
            else {
                String[][] cost = gl ? mineGloveStepCost(next) : mineCoatStepCost(next);
                if (!mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "재료가 모자라요. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")"; }
                else {
                    mineTakeCost(userName, cost, 1);
                    if (gl) {
                        dao.upsertMineItem(userName, MINE_ITEM_GLOVES, next);
                        dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, 0);
                        dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, 0); // 끼면 재생/광전사 장갑은 벗는다
                        dao.upsertMineItem(userName, MINE_ITEM_GLOVES_QUEST, 2);
                        items.put(MINE_ITEM_GLOVES, next); items.put(MINE_ITEM_GLOVES_OFF, 0); items.put(MINE_ITEM_GLOVE_SLOT, 0);
                    } else {
                        dao.upsertMineItem(userName, MINE_ITEM_COAT_TIER, next);
                        dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, 1);
                        dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); // [2026-11-06] 갑옷과 옷은 한 칸
                        items.put(MINE_ITEM_COAT_TIER, next); items.put(MINE_ITEM_COAT_SLOT, 1); items.put(MINE_ITEM_ARMOR_SLOT, 0);
                    }
                    msg = (gl ? "🧤 " : "🧥 ") + "털옷 장인: 다 됐네! " + nm + " " + next + "단계 -- " + (gl ? mineGloveEffect(next) : mineCoatEffect(next))
                            + (cur > 0 ? " (예전 것은 이걸로 바뀌었어요)" : "") + " (" + mineCostText(cost) + " 사용)";
                }
            }
        } else if ("wear".equals(action)) {
            if (gl) {
                int t = mineGloveTier(items);
                if (t < 1) { ok = false; msg = "방한 장갑이 없어요."; }
                else {
                    boolean on = !mineGlovesOn(items);
                    dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, on ? 0 : 1);
                    if (on) dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, 0);
                    items.put(MINE_ITEM_GLOVES_OFF, on ? 0 : 1);
                    if (on) items.put(MINE_ITEM_GLOVE_SLOT, 0);
                    msg = "🧤 " + MINE_GLOVE_NAMES[t - 1] + (on ? "을(를) 꼈어요." : "을(를) 벗었어요.");
                }
            } else {
                int t = mineCoatTier(items);
                if (t < 1) { ok = false; msg = "방한 외투가 없어요."; }
                else {
                    boolean on = mineCoatDef(items) <= 0;
                    dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, on ? 1 : 0);
                    items.put(MINE_ITEM_COAT_SLOT, on ? 1 : 0);
                    if (on) { dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); items.put(MINE_ITEM_ARMOR_SLOT, 0); } // [2026-11-06] 갑옷과 옷은 한 칸
                    msg = "🧥 " + MINE_COAT_NAMES[t - 1] + (on ? "을(를) 입었어요. " + mineCoatEffect(t) : "을(를) 벗었어요.");
                }
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
        // [2026-10-28] 의뢰 사슬(외투 3단계, 장갑 3단계)
        int coatTier = mineCoatTier(items), gloveTier = mineGloveTier(items);
        out.put("coatTier", coatTier);
        out.put("gloveTier", gloveTier);
        out.put("coatWorn", coatTier > 0 && mineCoatDef(items) > 0);
        out.put("glovesOn", mineGlovesOn(items));
        out.put("gloveCode", mineGloveCode(items));
        for (int k = 0; k < 2; k++) {
            boolean g2 = k == 1;
            int cur = g2 ? gloveTier : coatTier;
            List<HashMap<String, Object>> steps = new ArrayList<>();
            for (int t = 1; t <= 3; t++) {
                HashMap<String, Object> m = new HashMap<>();
                m.put("tier", t);
                m.put("name", (g2 ? MINE_GLOVE_NAMES : MINE_COAT_NAMES)[t - 1]);
                m.put("effect", g2 ? mineGloveEffect(t) : mineCoatEffect(t));
                m.put("done", t <= cur);
                m.put("next", t == cur + 1);
                if (t == cur + 1) {
                    String[][] cost = g2 ? mineGloveStepCost(t) : mineCoatStepCost(t);
                    m.put("costs", mineCostList(cost, have));
                    m.put("can", mineCostOk(cost, have));
                }
                steps.add(m);
            }
            out.put(g2 ? "gloveSteps" : "coatSteps", steps);
        }
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
    /** [2026-10-28] "정신의 목걸이 -- 추가 경험치 획득, 초보자도 살 수 있을 만큼 싸게" -- 처치 경험치 +10/20/30%. 목걸이는 하나만(NECK_SLOT 1 = 사막, 2 = 정신). */
    static final String MINE_ITEM_MIND_NECKLACE = "MIND_NECKLACE", MINE_ITEM_NECK_SLOT = "NECK_SLOT";
    static final int[] MINE_MIND_EXP_PCT = { 0, 10, 20, 30 };
    static final String MINE_ITEM_SWIFT_BOOTS = "SWIFT_BOOTS";
    static final String MINE_ITEM_RING_SLOT = "RING_SLOT";
    static final String MINE_ITEM_DRAGON_QUEST = "DRAGON_QUEST";
    /** [2026-10-24] 칠요석 곡괭이 -- "주변 3x3칸을 한 번에 캐는 곡괭이(다이아몬드 + 칠요석 여러 개)", "주변 3x3에 어떤 광물이 있는지 미리 보여 주는 곡괭이",
     *  "구매 후엔 변경 자유롭게". 곡괭이 칸 PICK_SLOT: 0 = 기본, 1 = 광역 곡괭이, 2 = 탐지 곡괭이(대장장이 [구매]에서 언제든 바꿔 낀다). */
    static final String MINE_ITEM_WIDE_PICK = "WIDE_PICK";
    static final String MINE_ITEM_DETECT_PICK = "DETECT_PICK";
    /** [2026-10-25] 쌍날 곡괭이 -- 바라보는 방향 앞뒤 칸도 함께(PICK_SLOT 3). */
    static final String MINE_ITEM_LINE_PICK = "LINE_PICK";
    /** [2026-11-13] 대광역 곡괭이 -- "3x3 말고 5x5도" 주변 5x5칸을 함께(PICK_SLOT 4, 칸마다 내구도 1). 왕도 대장장이만. */
    static final String MINE_ITEM_WIDE_PICK5 = "WIDE_PICK5";
    static final String MINE_ITEM_PICK_SLOT = "PICK_SLOT";
    /** 탐지 곡괭이가 빛을 잃음 -- [2026-10-29] "탐지 후 12시간마다 한 번씩 회복(지금은 2일 주기 = 맵 리셋)": DETECT_OFF = 다시 빛나는 시각(epoch 분).
     *  (예전 값은 주기 번호 + 1이라 아주 작은 수 -- 지금 시각보다 작아 바로 회복된 것으로 본다.) */
    static final String MINE_ITEM_DETECT_OFF = "DETECT_OFF";
    static final long MINE_DETECT_RECOVER_MS = 12 * 3_600_000L;

    static int mineDetectOffValue() { return (int) ((System.currentTimeMillis() + MINE_DETECT_RECOVER_MS) / 60_000L); }
    static boolean mineDetectDim(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_DETECT_OFF, 0) > System.currentTimeMillis() / 60_000L;
    }
    /** 다시 빛날 때까지 남은 시간("5시간 12분"). */
    static String mineDetectLeft(Map<String, Integer> items) {
        long m = Math.max(1, items.getOrDefault(MINE_ITEM_DETECT_OFF, 0) - System.currentTimeMillis() / 60_000L);
        return (m >= 60 ? m / 60 + "시간 " : "") + m % 60 + "분";
    }

    static int minePickSlotOf(String code) {
        return MINE_ITEM_WIDE_PICK.equals(code) ? 1 : MINE_ITEM_DETECT_PICK.equals(code) ? 2 : MINE_ITEM_LINE_PICK.equals(code) ? 3 : MINE_ITEM_WIDE_PICK5.equals(code) ? 4 : 0;
    }
    /** 단계별 효과(인덱스 = 단계, 0 = 없음). 장화 = 화면 한 칸 이동 시간(ms, 기본 140 -> 약 +11% / +25% / +40%). */
    //  [2026-10-25] 왕도 대장장이 단계 추가: 반지/목걸이 3단계, 장화 4단계(88ms = 약 +60%).
    static final int[] MINE_PLENTY_DOUBLE_PCT = { 0, 3, 8, 15 };
    static final int[] MINE_ABYSS_UP_PCT = { 0, 2, 5, 10 };
    static final double[] MINE_SAND_GEM_MULT = { 1.0, 1.3, 1.6, 2.2, 3.0, 4.0 }; // [2026-11-13] "업그레이드 지원, 상승 폭 많게" -- 4단계 3배, 5단계 4배(왕도 대장장이). 사냥꾼의 목걸이도 같은 표
    static final int[] MINE_STEP_MS = { 140, 126, 112, 100, 88, 74, 62 }; // [2026-11-13] 장화 5단계 +89%, 6단계 +126%(왕도 대장장이)
    /** 선장이 금광석만 받고 아무것도 안 알려줄 확률(%). */
    static final int MINE_CAPTAIN_SCAM_PCT = 40;
    static final int MINE_CAPTAIN_GOLD = 10;
    static final String[] MINE_CAPTAIN_SCAM_LINES = {
        "허허, 고맙네. 신기한 거? ...아, 그건 다음에 보여주지.",
        "(선장이 금광석을 주머니에 넣더니 먼 바다만 바라본다.)",
        "어이쿠, 배에 물이 새는군! 신기한 건 다음에 보자고!",
        "신기하지? 금광석이 사라졌잖나. 껄껄껄.",
        "음... 내가 그런 말을 했던가? 요즘 깜빡깜빡해서 말이야."
    };
    static final String MINE_CAPTAIN_STORY = "좋아, 보여주지. 이건 바다 밑 용왕이 끼던 반지 이야기라네. 그 반지를 낀 자는 물 위를 땅처럼 걸었다더군. "
            + "아쿠아마린 30개, 스타 사파이어 5개, 칠요석 1개, 다이아몬드 1개를 가져오면 내가 그 반지를 만들어 주지.";

    static String[] mineCost(int gemIdx, int qty) {
        return new String[] { S5MineMap.GEM_CODES[gemIdx], String.valueOf(qty) };
    }

    static final class MineGear {
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
    /** [2026-10-28] 괴 n개(0 = 동괴 ~ 4 = 미스릴괴). */
    static String[] mineBar(int oreIdx, int qty) {
        return new String[] { S5MineMap.BAR_CODES[oreIdx], String.valueOf(qty) };
    }

    /** [2026-10-28] 세공된 보석 n개(GEM_CODES 순서). */
    static String[] mineCut(int gemIdx, int qty) {
        return new String[] { S5MineMap.cutCode(gemIdx), String.valueOf(qty) };
    }

    static String[] mineStar(int qty) {
        return new String[] { S5MineMap.ORE_CODES[5], String.valueOf(qty) };
    }

    /** [2026-10-25] 칠요석 n개(재료) -- "고급템은 칠요석이 들어가게". 칠요석 = 요일석 7종 + 별빛석(mineSynthSeven). */
    static String[] mineSeven(int qty) {
        return new String[] { S5MineMap.SEVEN_STONE_CODE, String.valueOf(qty) };
    }

    // [2026-10-25] "성장이 빨라 상점 아이템을 너무 빨리 달성 -- 1단계 구매는 그대로, 2단계부터 비싸게" + "별빛석 필요처" 요청:
    //  라이브 확인(10-25) 2일 만에 상위 유저가 1단계 장신구/장화를 다 샀고 2단계(희귀 보석 5 + 다이아 1)도 하루치였다.
    //  2단계부터 희귀 보석 5 -> 25, 다이아 1 -> 4, 별빛석 1 추가(장신구), 장화 2단계 다이아 2 -> 6 + 미스릴 10, 3단계 다이아 3 -> 12 + 별빛석 2로 올렸다가 같은 날
    //  "이미 단계업한 사람이 있으면 그렇게 올리면 안 된다" -- 이미 누가 산 단계(장신구 2단계, 장화 2단계)는 원래 값으로 되돌리고 아무도 없는 장화 3단계만 올린 값 유지.
    // [2026-10-25] "지금보다 더 높은 업그레이드는 대도시 대장장이에서, 더 큰 폭의 아이템, 고급템은 칠요석" -- 반지/목걸이 3단계, 장화 4단계는 왕도 대장장이만(fieldMax),
    //  효과 폭을 크게(2개 8% -> 15%, 한 등급 위 5% -> 10%, 보석 1.6배 -> 2.2배, 이동 +40% -> +60%) 하고 재료에 칠요석.
    //  "곡괭이는 모든 대장장이 공통, 앞뒤가 같이 캐지는 곡괭이(칠요석 하나)" -- 쌍날 곡괭이(PICK_SLOT 3).
    static final List<MineGear> MINE_GEAR = java.util.Arrays.asList(
        new MineGear(MINE_ITEM_PLENTY_RING, "풍요의 반지", "ring",
            new String[][][] { { mineCost(0, 20) }, { mineCost(7, 5), mineCost(8, 1) }, { mineCost(7, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(7, 1) } }, // [2026-10-28] 3단계 = 괴 + 세공 보석
            new String[] { "캘 때 2개 나올 확률 3%", "캘 때 2개 나올 확률 8%", "캘 때 2개 나올 확률 15%" }, 2),
        new MineGear(MINE_ITEM_ABYSS_RING, "심연의 반지", "ring",
            new String[][][] { { mineCost(6, 20) }, { mineCost(1, 5), mineCost(8, 1) }, { mineCost(1, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(1, 1) } },
            new String[] { "한 등급 위 광석이 나올 확률 2% (미스릴까지)", "한 등급 위 광석이 나올 확률 5% (미스릴까지)", "한 등급 위 광석이 나올 확률 10% (미스릴까지)" }, 2),
        new MineGear(MINE_ITEM_SAND_NECKLACE, "사막의 목걸이", "necklace",
            new String[][][] { { mineCost(4, 20) }, { mineCost(5, 5), mineCost(8, 1) }, { mineCost(5, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(4, 1) },
                { mineCost(5, 80), mineCost(8, 10), mineSeven(5), mineBar(4, 2), mineBar(3, 3), mineCut(4, 3), mineStar(2) }, // [2026-11-13] 4·5단계
                { mineCost(5, 150), mineCost(8, 20), mineSeven(10), mineBar(4, 5), mineCut(4, 6), mineStar(4), { S5MineMap.MOON_STONE_CODE, "3" } } },
            new String[] { "캘 때 보석이 나올 확률 1.3배", "캘 때 보석이 나올 확률 1.6배", "캘 때 보석이 나올 확률 2.2배", "캘 때 보석이 나올 확률 3배", "캘 때 보석이 나올 확률 4배" }, 2),
        // [2026-11-13] "몬스터 처치 시 보석 드랍율 목걸이(채광용과 같은 레벨로)" -- 사냥꾼의 목걸이(NECK_SLOT 4), 배수는 사막의 목걸이와 같은 표(MINE_SAND_GEM_MULT)
        new MineGear("HUNT_NECKLACE", "사냥꾼의 목걸이", "necklace", // = MINE_ITEM_HUNT_NECKLACE
            new String[][][] { { mineCost(2, 20) }, { mineCost(3, 5), mineCost(8, 1) }, { mineCost(3, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(3, 1) },
                { mineCost(3, 80), mineCost(8, 10), mineSeven(5), mineBar(4, 2), mineBar(3, 3), mineCut(3, 3), mineStar(2) },
                { mineCost(3, 150), mineCost(8, 20), mineSeven(10), mineBar(4, 5), mineCut(3, 6), mineStar(4), { S5MineMap.MOON_STONE_CODE, "3" } } },
            new String[] { "몬스터를 쓰러뜨릴 때 보석이 나올 확률 1.3배", "몬스터를 쓰러뜨릴 때 보석이 나올 확률 1.6배", "몬스터를 쓰러뜨릴 때 보석이 나올 확률 2.2배", "몬스터를 쓰러뜨릴 때 보석이 나올 확률 3배", "몬스터를 쓰러뜨릴 때 보석이 나올 확률 4배" }, 2),
        new MineGear("MIND_NECKLACE", "정신의 목걸이", "necklace", // = MINE_ITEM_MIND_NECKLACE(아래 -- 앞에서 쓰면 forward reference)
            new String[][][] { { { S5MineMap.ORE_CODES[0], "30" }, { S5MineMap.ORE_CODES[1], "20" } }, { { S5MineMap.ORE_CODES[2], "40" }, { S5MineMap.ORE_CODES[3], "15" } },
                    { { S5MineMap.ORE_CODES[3], "60" }, { S5MineMap.ORE_CODES[4], "15" }, mineCost(0, 5) } }, // 1단계 = 구리 30 + 철 20(초보자도), 2단계 은 40 + 금 15, 3단계 금 60 + 미스릴 15 + 재스퍼 5
            new String[] { "몬스터를 쓰러뜨리면 경험치 +10%", "몬스터를 쓰러뜨리면 경험치 +20%", "몬스터를 쓰러뜨리면 경험치 +30%" }),
        new MineGear(MINE_ITEM_SWIFT_BOOTS, "신속의 장화", "boots",
            new String[][][] { { mineCost(8, 1) }, { mineCost(8, 2) }, { mineCost(8, 12), mineStar(2), mineBar(1, 1) }, { mineCost(8, 15), mineSeven(3), mineBar(4, 1), mineBar(1, 2), mineCut(3, 1) },
                { mineCost(8, 20), mineSeven(6), mineBar(4, 3), mineBar(1, 4), mineCut(3, 3), mineStar(2) }, // [2026-11-13] 5·6단계
                { mineCost(8, 30), mineSeven(12), mineBar(4, 6), mineCut(3, 6), mineStar(4), { S5MineMap.MOON_STONE_CODE, "3" } } },
            new String[] { "이동 속도 +11%", "이동 속도 +25%", "이동 속도 +40%", "이동 속도 +60%", "이동 속도 +89%", "이동 속도 +126%" }, 3),
        new MineGear(MINE_ITEM_WIDE_PICK, "광역 곡괭이", "tool",
            new String[][][] { { mineCost(8, 3), mineSeven(5), mineBar(0, 1), mineBar(1, 1) } },
            new String[] { "캘 때 주변 3x3칸도 함께 (칸마다 내구도 1)" }),
        new MineGear(MINE_ITEM_WIDE_PICK5, "대광역 곡괭이", "tool", // [2026-11-13] 5x5 -- 광역 곡괭이(3x3)보다 훨씬 비싸게, 왕도 대장장이만
            new String[][][] { { mineCost(8, 12), mineSeven(20), mineBar(1, 3), mineBar(4, 2), mineCut(8, 2) } },
            new String[] { "캘 때 주변 5x5칸도 함께 (칸마다 내구도 1)" }, 0),
        new MineGear(MINE_ITEM_DETECT_PICK, "탐지 곡괭이", "tool",
            new String[][][] { { mineCost(8, 8), mineSeven(10), { S5MineMap.ORE_CODES[4], "10" }, mineBar(4, 1), mineBar(3, 1), mineCut(2, 1) } }, // "많이 비싸게"
            new String[] { "빛나는 돌(별빛석·달빛석)을 탐지할 때까지 주변 광석(사방 4칸)을 알아챌 수 있다 (멈추면 12시간 뒤 다시 빛나요)" }),
        new MineGear(MINE_ITEM_LINE_PICK, "쌍날 곡괭이", "tool",
            new String[][][] { { mineSeven(1) } },
            new String[] { "캘 때 바라보는 방향의 앞 칸과 뒤 칸도 함께 (칸마다 내구도 1)" }),
        // [2026-10-28] 장갑 둘(왕도 대장장이만, 장갑 칸 하나 -- 방한 장갑과 바꿔 낀다)
        //  재생의 장갑 = "회복 속도가 빨라지는 컨셉, 전투 중에도 꽤 빠르게 -- 전투에서 받은 피해의 80%를 4초에 걸쳐 회복",
        //  광전사의 장갑 = "자연 회복이 멈추는 대신 데미지 50% 추가"
        new MineGear("REGEN_GLOVES", "재생의 장갑", "gloves", // = MINE_ITEM_REGEN_GLOVES(아래 -- 앞에서 쓰면 forward reference)
            new String[][][] { { mineCost(1, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(1, 1) }, // [2026-10-28] "유저 최고가 구매할 만큼 비싸게"
                { mineCost(1, 20), mineCut(1, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(0), "2" }, { S5MineMap.ICE_HEART_CODE, "1" } } }, // [2026-11-04] 2단계
            new String[] { "전투에서 받은 피해의 50%를 5초에 걸쳐 회복", "전투에서 받은 피해의 65%를 5초에 걸쳐 회복 · 몬스터를 쓰러뜨리면 최대 체력 3% 회복" }, 0), // [2026-11-04] 80%/4초 -> 50%/5초
        new MineGear("BERSERK_GLOVES", "광전사의 장갑", "gloves", // = MINE_ITEM_BERSERK_GLOVES
            new String[][][] { { mineCost(5, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(5, 1) },
                { mineCost(5, 20), mineCut(5, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(1), "2" }, { S5MineMap.VOLCANO_HEART_CODE, "1" } } }, // [2026-11-04] 2단계
            new String[] { "공격력 +50% · 체력 회복 속도 -20% · 불굴: 쓰러질 일격을 체력 1로 버티고 5초 무적(1분에 한 번)", "공격력 +65% · 체력 30% 이하일 때 공격력 추가 +20% · 체력 회복 속도 -20% · 불굴: 쓰러질 일격을 체력 1로 버티고 5초 무적(1분에 한 번)" }, 0), // [2026-11-20] 불굴
        // [2026-10-30] "기본 공격력의 55%로 두 번 때리는 장갑 -- 타격당 치명 +5%, 착용 중 초당 체력 -1%(자연 회복과 겹쳐 상쇄)"
        new MineGear("TWIN_GLOVES", "쌍격의 장갑", "gloves", // = MINE_ITEM_TWIN_GLOVES
            new String[][][] { { mineCost(4, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(4, 1) },
                { mineCost(4, 20), mineCut(4, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(4), "2" } } }, // [2026-11-04] 2단계(닌자의 비급서는 안 씀)
            new String[] { "한 번에 두 번 공격(각 55%) · 타격마다 치명타 +5% · 공격할 때 12% 확률로 2초 기절 · 끼고 있으면 초당 체력 -1%", "한 번에 두 번 공격(각 60%) · 타격마다 치명타 +8% · 공격할 때 18% 확률로 2초 기절 · 끼고 있으면 초당 체력 -0.7%" }, 0), // [2026-11-20] 기절
        // [2026-11-20] "장비 장착이 너무 획일화 -- 추가 장갑으로 필살기 데미지 증가나 쿨타임 감소(각각)" -- 왕도 대장장이, 장갑 칸 5/6
        new MineGear("ULT_DMG_GLOVES", "필살의 장갑", "gloves", // = MINE_ITEM_ULT_DMG_GLOVES
            new String[][][] { { mineCost(2, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(2, 1), { S5MineMap.GALAXY_CODE, "2" } },
                { mineCost(2, 20), mineCut(2, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(2), "2" }, { S5MineMap.GALAXY_CODE, "5" } } },
            new String[] { "필살기(만검진·일섬참) 피해 +30%", "필살기(만검진·일섬참) 피해 +45%" }, 0),
        new MineGear("ULT_CD_GLOVES", "신속의 장갑", "gloves", // = MINE_ITEM_ULT_CD_GLOVES
            new String[][][] { { mineCost(3, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(3, 1), { S5MineMap.GALAXY_CODE, "2" } },
                { mineCost(3, 20), mineCut(3, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(3), "2" }, { S5MineMap.GALAXY_CODE, "5" } } },
            new String[] { "필살기 쿨타임 -25% (120초 -> 90초)", "필살기 쿨타임 -35% (120초 -> 78초)" }, 0),
        // [2026-11-23] "신규 목걸이 -- 용병 스킬 쿨타임 감소 3%, 용병 데미지 증가 3%, 레벨업시키면 더 강력" -- 둘 다 5단계(3/6/10/15/20%), 1~2단계는 마을 대장장이, 3단계부터 왕도 대장장이
        new MineGear("MERC_DMG_NECKLACE", "투지의 목걸이", "necklace", // = MINE_ITEM_MERC_DMG_NECK
            new String[][][] { { mineCost(6, 20), mineBar(2, 1) }, { mineCost(7, 5), mineCost(8, 1), mineBar(3, 1) }, { mineCost(7, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(6, 1) },
                { mineCost(7, 80), mineCost(8, 10), mineSeven(5), mineBar(4, 2), mineBar(3, 3), mineCut(6, 3), mineStar(2) },
                { mineCost(7, 150), mineCost(8, 20), mineSeven(10), mineBar(4, 5), mineCut(6, 6), mineStar(4), { S5MineMap.MOON_STONE_CODE, "3" } } },
            new String[] { "용병 피해 +3%", "용병 피해 +6%", "용병 피해 +10%", "용병 피해 +15%", "용병 피해 +20%" }, 2),
        new MineGear("MERC_CD_NECKLACE", "지휘관의 목걸이", "necklace", // = MINE_ITEM_MERC_CD_NECK
            new String[][][] { { mineCost(1, 20), mineBar(2, 1) }, { mineCost(0, 5), mineCost(8, 1), mineBar(3, 1) }, { mineCost(0, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(1, 1) },
                { mineCost(0, 80), mineCost(8, 10), mineSeven(5), mineBar(4, 2), mineBar(3, 3), mineCut(1, 3), mineStar(2) },
                { mineCost(0, 150), mineCost(8, 20), mineSeven(10), mineBar(4, 5), mineCut(1, 6), mineStar(4), { S5MineMap.MOON_STONE_CODE, "3" } } },
            new String[] { "용병 스킬 쿨타임 -3%", "용병 스킬 쿨타임 -6%", "용병 스킬 쿨타임 -10%", "용병 스킬 쿨타임 -15%", "용병 스킬 쿨타임 -20%" }, 2),
        // [2026-10-30] "자객의 목걸이 -- 공격 시 확률적으로 은신(일반 공격 회피 2회), 회피가 남았을 때 공격하면 풀리며 2배 피해, 상위급" -- 왕도 대장장이만
        new MineGear("ASSASSIN_NECKLACE", "자객의 목걸이", "necklace", // = MINE_ITEM_ASSASSIN_NECKLACE
            new String[][][] { { mineCost(8, 10), mineSeven(3), { S5MineMap.MOON_STONE_CODE, "3" }, mineBar(4, 2), mineBar(3, 2), mineCut(6, 1) } },
            new String[] { "공격할 때 15%로 은신 -- 몬스터의 일반 공격을 2번 피함(15초) · 은신 중 공격하면 은신이 풀리며 2배 피해" }, 0)
    );
    /** [2026-10-30] 자객의 목걸이(NECK_SLOT 3) -- 은신 상태는 유저별 메모리 MINE_PSTEALTH {남은 회피, 끝나는 시각}. 원거리(불덩이/독산)·환경 피해는 못 피한다. */
    static final String MINE_ITEM_ASSASSIN_NECKLACE = "ASSASSIN_NECKLACE";
    static final int MINE_ASSASSIN_PCT = 15, MINE_ASSASSIN_DODGES = 2;
    static final long MINE_ASSASSIN_MS = 15_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_PSTEALTH = new java.util.concurrent.ConcurrentHashMap<>();

    /** 은신 중이면 {남은 회피, 끝나는 시각}, 아니면 null. */
    static long[] minePStealthOf(String userName, long now) {
        long[] st = MINE_PSTEALTH.get(userName);
        if (st == null) return null;
        synchronized (st) { if (st[0] > 0 && st[1] > now) return new long[] { st[0], st[1] }; }
        MINE_PSTEALTH.remove(userName, st);
        return null;
    }

    /** 몬스터의 일반 공격을 은신으로 피했는지(피했으면 회피 1회를 쓴다). */
    static boolean minePStealthDodge(String userName, long now) {
        long[] st = MINE_PSTEALTH.get(userName);
        if (st == null) return false;
        synchronized (st) {
            if (st[0] <= 0 || st[1] <= now) { MINE_PSTEALTH.remove(userName, st); return false; }
            if (--st[0] <= 0) MINE_PSTEALTH.remove(userName, st);
            return true;
        }
    }

    static int mineNeckSlotOf(String code) { return MINE_ITEM_MIND_NECKLACE.equals(code) ? 2 : MINE_ITEM_ASSASSIN_NECKLACE.equals(code) ? 3 : MINE_ITEM_HUNT_NECKLACE.equals(code) ? 4 : MINE_ITEM_MERC_DMG_NECK.equals(code) ? 5 : MINE_ITEM_MERC_CD_NECK.equals(code) ? 6 : MINE_ITEM_BH_PEARL.equals(code) ? 7 : 1; }
    // [2026-11-23] 용병 목걸이 -- NECK_SLOT 5 = 투지의 목걸이(용병 피해 +%), 6 = 지휘관의 목걸이(용병 스킬 쿨타임 -%), 걸고 있을 때만
    static final String MINE_ITEM_MERC_DMG_NECK = "MERC_DMG_NECKLACE", MINE_ITEM_MERC_CD_NECK = "MERC_CD_NECKLACE";
    static final int[] MINE_MERC_NECK_PCT = { 0, 3, 6, 10, 15, 20 };
    static int mineMercNeckPct(Map<String, Integer> items, String code) { return items != null && code.equals(mineNeckCode(items)) ? MINE_MERC_NECK_PCT[Math.max(0, Math.min(MINE_MERC_NECK_PCT.length - 1, items.getOrDefault(code, 0)))] : 0; }
    static final String MINE_ITEM_HUNT_NECKLACE = "HUNT_NECKLACE";
    /** [2026-11-13] 사냥꾼의 목걸이 -- 몬스터 보석 확률 배수(걸고 있을 때만). */
    static double mineHuntGemMul(Map<String, Integer> items) {
        return MINE_ITEM_HUNT_NECKLACE.equals(mineNeckCode(items)) ? MINE_SAND_GEM_MULT[Math.min(items.getOrDefault(MINE_ITEM_HUNT_NECKLACE, 0), MINE_SAND_GEM_MULT.length - 1)] : 1.0;
    }
    static final String MINE_ITEM_REGEN_GLOVES = "REGEN_GLOVES", MINE_ITEM_BERSERK_GLOVES = "BERSERK_GLOVES";
    /** [2026-10-30] 쌍격의 장갑(GLOVE_SLOT 4) -- 두 번(각 MINE_TWIN_PCT%), 타격마다 치명 +MINE_TWIN_CRIT, 초당 최대 체력 -MINE_TWIN_DRAIN_PCT%(mineHpNow). */
    static final String MINE_ITEM_TWIN_GLOVES = "TWIN_GLOVES";
    static final double MINE_TWIN_PCT = 0.55, MINE_TWIN_DRAIN_PCT = 1.0;
    static final int MINE_TWIN_CRIT = 5;
    /** [2026-10-28] 장갑 칸: 2 = 재생의 장갑, 3 = 광전사의 장갑, 그 밖(0/없음) = 방한 장갑(GLOVES_OFF로 벗음). */
    static final String MINE_ITEM_GLOVE_SLOT = "GLOVE_SLOT";
    static final double MINE_REGEN_GLOVE_PCT = 0.5, MINE_BERSERK_MULT = 1.5; // [2026-11-04] 재생의 장갑 80% -> 50%
    /** [2026-10-29] "광전사의 장갑 -- 체력이 회복되지 않음 대신 회복 속도 -20%" */
    static final double MINE_BERSERK_REGEN_MUL = 0.8;
    static final long MINE_REGEN_GLOVE_MS = 5_000L; // [2026-11-04] 4초 -> 5초
    // [2026-11-04] 장갑 2단계(이름은 그대로) -- 재생 65% + 처치 시 최대 체력 3%, 광전사 +65% + 체력 30% 이하 +20%, 쌍격 각 60% · 치명 +8% · 초당 -0.7%
    static final String MINE_LOWHP_FLAG = "_BERSERK_LOW"; // 저장하지 않는 표시(mineAttack이 items에 잠깐 넣는다)
    static int mineGloveLv(Map<String, Integer> items, String code) { return code.equals(mineGloveCode(items)) ? items.getOrDefault(code, 0) : 0; }
    static double mineRegenPct(Map<String, Integer> items) { return mineGloveLv(items, MINE_ITEM_REGEN_GLOVES) >= 2 ? 0.65 : MINE_REGEN_GLOVE_PCT; }
    static double mineTwinPct(Map<String, Integer> items) { return mineGloveLv(items, MINE_ITEM_TWIN_GLOVES) >= 2 ? 0.60 : MINE_TWIN_PCT; }
    static int mineTwinCrit(Map<String, Integer> items) { return mineGloveLv(items, MINE_ITEM_TWIN_GLOVES) >= 2 ? 8 : MINE_TWIN_CRIT; }
    static double mineTwinDrain(Map<String, Integer> items) { return mineGloveLv(items, MINE_ITEM_TWIN_GLOVES) >= 2 ? 0.7 : MINE_TWIN_DRAIN_PCT; }
    /** 광전사의 장갑 2단계 -- 체력이 최대의 30% 이하면 공격력 추가 +20%(items에 표시만, 저장 안 함). */
    static void mineBerserkLow(Map<String, Integer> items, int hp, int hpMax) {
        if (mineGloveLv(items, MINE_ITEM_BERSERK_GLOVES) >= 2 && hp * 10L <= hpMax * 3L) items.put(MINE_LOWHP_FLAG, 1); else items.remove(MINE_LOWHP_FLAG);
    }
    /** 재생의 장갑 2단계 -- 처치(kills마리)하면 최대 체력 3%씩 회복. 회복한 양. */
    static int mineRegenKillHeal(Map<String, Integer> items, int kills, int hpMax) {
        return kills > 0 && mineGloveLv(items, MINE_ITEM_REGEN_GLOVES) >= 2 ? (int) Math.round(hpMax * 0.03 * kills) : 0;
    }

    /** 끼고 있는 장갑 코드(없으면 null). */
    static int mineGloveSlotOf(String code) { return MINE_ITEM_REGEN_GLOVES.equals(code) ? 2 : MINE_ITEM_TWIN_GLOVES.equals(code) ? 4 : MINE_ITEM_ULT_DMG_GLOVES.equals(code) ? 5 : MINE_ITEM_ULT_CD_GLOVES.equals(code) ? 6 : MINE_ITEM_BH_ORB.equals(code) ? 7 : 3; }
    // ---- [2026-11-20] "장비 장착이 너무 획일화 -- 쌍격/광전사 상향, 필살기 장갑 둘" ----
    //  쌍격의 장갑: 공격할 때(평타) MINE_TWIN_STUN_PCT(2단계 _2)% 확률로 대상 2초 기절(월드보스/크라켄/허수아비 빼고).
    //  광전사의 장갑 -- 불굴: 체력이 0 이하로 떨어질 일격을 체력 1로 버티고 MINE_BERSERK_INV_MS 무적(그동안 받는 피해 0), MINE_BERSERK_CD_MS마다 한 번(유저별 메모리).
    //  필살의 장갑: 필살기 피해 x1.30(2단계 1.45). 신속의 장갑: 필살기 쿨타임 x0.75(2단계 0.65).
    static final String MINE_ITEM_ULT_DMG_GLOVES = "ULT_DMG_GLOVES", MINE_ITEM_ULT_CD_GLOVES = "ULT_CD_GLOVES";
    static final int MINE_TWIN_STUN_PCT = 12, MINE_TWIN_STUN_PCT2 = 18;
    static final long MINE_TWIN_STUN_MS = 2_000L, MINE_BERSERK_INV_MS = 5_000L, MINE_BERSERK_CD_MS = 60_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_BERSERK_INV = new java.util.concurrent.ConcurrentHashMap<>(); // {무적 끝, 마지막 불굴}
    static int mineTwinStunPct(Map<String, Integer> items) { return mineGloveLv(items, MINE_ITEM_TWIN_GLOVES) >= 2 ? MINE_TWIN_STUN_PCT2 : MINE_TWIN_STUN_PCT; }
    static double mineUltGloveMul(Map<String, Integer> items) { int l = mineGloveLv(items, MINE_ITEM_ULT_DMG_GLOVES); return l >= 2 ? 1.45 : l >= 1 ? 1.30 : 1.0; }
    static long mineUltGap(Map<String, Integer> items) { int l = mineGloveLv(items, MINE_ITEM_ULT_CD_GLOVES); return l >= 2 ? MINE_ULT_GAP_MS * 65 / 100 : l >= 1 ? MINE_ULT_GAP_MS * 75 / 100 : MINE_ULT_GAP_MS; }
    /** 광전사 불굴 무적 중인지. */
    static boolean mineBerserkInvul(String userName, long now) { long[] b = MINE_BERSERK_INV.get(userName); return b != null && b[0] > now; }
    /** 쓰러질 참 -- 광전사의 장갑이면 불굴(체력 1, 5초 무적)을 걸고 true. 무적 중이었어도 true. out에 berserkSave(무적 끝 시각). */
    static boolean mineBerserkSave(String userName, Map<String, Integer> items, long now, HashMap<String, Object> out) {
        if (items == null || !MINE_ITEM_BERSERK_GLOVES.equals(mineGloveCode(items))) return false;
        long[] b = MINE_BERSERK_INV.computeIfAbsent(userName, k -> new long[] { 0, -MINE_BERSERK_CD_MS });
        synchronized (b) {
            if (b[0] > now) return true;
            if (now - b[1] < MINE_BERSERK_CD_MS) return false;
            b[0] = now + MINE_BERSERK_INV_MS; b[1] = now;
        }
        if (out != null) { out.put("berserkSave", now + MINE_BERSERK_INV_MS); out.put("berserkCd", MINE_BERSERK_CD_MS); }
        return true;
    }
    static String mineGloveCode(Map<String, Integer> items) {
        int s = items.getOrDefault(MINE_ITEM_GLOVE_SLOT, 0);
        if (s == 2 && items.getOrDefault(MINE_ITEM_REGEN_GLOVES, 0) > 0) return MINE_ITEM_REGEN_GLOVES;
        if (s == 3 && items.getOrDefault(MINE_ITEM_BERSERK_GLOVES, 0) > 0) return MINE_ITEM_BERSERK_GLOVES;
        if (s == 4 && items.getOrDefault(MINE_ITEM_TWIN_GLOVES, 0) > 0) return MINE_ITEM_TWIN_GLOVES; // [2026-10-30]
        if (s == 5 && items.getOrDefault(MINE_ITEM_ULT_DMG_GLOVES, 0) > 0) return MINE_ITEM_ULT_DMG_GLOVES; // [2026-11-20]
        if (s == 6 && items.getOrDefault(MINE_ITEM_ULT_CD_GLOVES, 0) > 0) return MINE_ITEM_ULT_CD_GLOVES;
        if (s == 7 && items.getOrDefault(MINE_ITEM_BH_ORB, 0) > 0) return MINE_ITEM_BH_ORB; // [2026-11-23]
        return items.getOrDefault(MINE_ITEM_GLOVES, 0) > 0 && items.getOrDefault(MINE_ITEM_GLOVES_OFF, 0) != 1 ? MINE_ITEM_GLOVES : null;
    }

    // [2026-10-28] 재생의 장갑 -- 전투에서 받은 피해마다 80%를 4초에 걸쳐 나눠 회복(유저별 메모리, 전투 턴이 1초마다 지난 만큼 체력에 더해 저장).
    static final class HealChunk { final int amt; final long start; int given; HealChunk(int amt, long start) { this.amt = amt; this.start = start; } }
    static final java.util.concurrent.ConcurrentHashMap<String, List<HealChunk>> MINE_HEAL = new java.util.concurrent.ConcurrentHashMap<>();

    static void mineHealAdd(String userName, int amt, long now) {
        if (amt <= 0) return;
        List<HealChunk> l = MINE_HEAL.computeIfAbsent(userName, u -> new ArrayList<>());
        synchronized (l) { l.add(new HealChunk(amt, now)); }
    }

    /** 지금까지 회복할 몫(이미 준 만큼 빼고) -- 부르면 준 것으로 친다. */
    static int mineHealDue(String userName, long now) {
        List<HealChunk> l = MINE_HEAL.get(userName);
        if (l == null) return 0;
        int sum = 0;
        synchronized (l) {
            for (java.util.Iterator<HealChunk> it = l.iterator(); it.hasNext();) {
                HealChunk c = it.next();
                double p = Math.min(1.0, (now - c.start) / (double) MINE_REGEN_GLOVE_MS);
                int due = (int) Math.round(c.amt * p) - c.given;
                if (due > 0) { c.given += due; sum += due; }
                if (p >= 1.0) it.remove();
            }
        }
        return sum;
    }

    static long mineHealUntil(String userName) {
        List<HealChunk> l = MINE_HEAL.get(userName);
        if (l == null) return 0;
        long u = 0;
        synchronized (l) { for (HealChunk c : l) u = Math.max(u, c.start + MINE_REGEN_GLOVE_MS); }
        return u;
    }

    static final String[][] MINE_DRAGON_RING_COST = {
        mineCost(3, 30), mineCost(2, 5), { S5MineMap.SEVEN_STONE_CODE, "1" }, mineCost(8, 1)
    };

    static MineGear mineGear(String code) {
        for (MineGear g : MINE_GEAR) if (g.code.equals(code)) return g;
        return null;
    }

    /** 끼고 있는 반지면 그 단계, 아니면 0. */
    static int mineRingTier(Map<String, Integer> items, String ringCode) {
        int slot = items.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        boolean worn = (slot == 1 && MINE_ITEM_PLENTY_RING.equals(ringCode)) || (slot == 2 && MINE_ITEM_ABYSS_RING.equals(ringCode));
        return worn ? Math.min(items.getOrDefault(ringCode, 0), MINE_PLENTY_DOUBLE_PCT.length - 1) : 0;
    }

    static int mineStepMs(Map<String, Integer> items) {
        MineCos shoes = mineWornCos(items, "shoes");
        if (shoes != null && MINE_COS_SPEED.equals(shoes.code)) return MINE_STEP_MS[0] / MINE_COS_SPEED_DIV; // [2026-10-28] 바람의 신(운영자) -- 5배
        return MINE_STEP_MS[Math.min(mineBootsTier(items), MINE_STEP_MS.length - 1)];
    }

    // [2026-10-28] "인벤토리에 부위별 장착 장비, 누르면 장착/해제 + 신발이 빠져서 추가" -- 신속의 장화(신발)와 사막의 목걸이도 벗을 수 있게(BOOTS_OFF / NECK_OFF = 1이면 벗음).
    static final String MINE_ITEM_BOOTS_OFF = "BOOTS_OFF", MINE_ITEM_NECK_OFF = "NECK_OFF";
    static int mineBootsTier(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_BOOTS_OFF, 0) == 1 ? 0 : items.getOrDefault(MINE_ITEM_SWIFT_BOOTS, 0); }
    /** 걸고 있는 목걸이 코드(없으면 null) -- NECK_SLOT 2 = 정신, 그 밖 = 사막(예전엔 사막 목걸이만 있었다). NECK_OFF = 1이면 풀었음. */
    static String mineNeckCode(Map<String, Integer> items) {
        if (items.getOrDefault(MINE_ITEM_NECK_OFF, 0) == 1) return null;
        boolean sand = items.getOrDefault(MINE_ITEM_SAND_NECKLACE, 0) > 0, mind = items.getOrDefault(MINE_ITEM_MIND_NECKLACE, 0) > 0;
        int s = items.getOrDefault(MINE_ITEM_NECK_SLOT, 0);
        if (s == 2 && mind) return MINE_ITEM_MIND_NECKLACE;
        if (s == 3 && items.getOrDefault(MINE_ITEM_ASSASSIN_NECKLACE, 0) > 0) return MINE_ITEM_ASSASSIN_NECKLACE; // [2026-10-30]
        if (s == 4 && items.getOrDefault(MINE_ITEM_HUNT_NECKLACE, 0) > 0) return MINE_ITEM_HUNT_NECKLACE; // [2026-11-13]
        if (s == 5 && items.getOrDefault(MINE_ITEM_MERC_DMG_NECK, 0) > 0) return MINE_ITEM_MERC_DMG_NECK; // [2026-11-23]
        if (s == 6 && items.getOrDefault(MINE_ITEM_MERC_CD_NECK, 0) > 0) return MINE_ITEM_MERC_CD_NECK;
        if (s == 7 && items.getOrDefault(MINE_ITEM_BH_PEARL, 0) > 0) return MINE_ITEM_BH_PEARL; // [2026-11-23]
        if (sand) return MINE_ITEM_SAND_NECKLACE;
        return mind ? MINE_ITEM_MIND_NECKLACE : null;
    }
    /** 사막의 목걸이 단계(걸고 있을 때만). */
    static int mineNeckTier(Map<String, Integer> items) { return MINE_ITEM_SAND_NECKLACE.equals(mineNeckCode(items)) ? items.getOrDefault(MINE_ITEM_SAND_NECKLACE, 0) : 0; }
    /** 정신의 목걸이 경험치 보너스 %(걸고 있을 때만). */
    static int mineMindExpPct(Map<String, Integer> items) {
        return MINE_ITEM_MIND_NECKLACE.equals(mineNeckCode(items)) ? MINE_MIND_EXP_PCT[Math.min(MINE_MIND_EXP_PCT.length - 1, items.getOrDefault(MINE_ITEM_MIND_NECKLACE, 0))] : 0;
    }

    // [2026-10-28] "방한 외투, 방한 장갑, 용왕의 반지도 다른 장비와 중첩 적용은 안 되게 -- 반지류 하나, 옷류 하나, 장갑류 하나" --
    //  반지 칸(RING_SLOT): 1 풍요 / 2 심연 / 3 용왕의 반지(물 위 걷기는 이걸 낄 때만). 옷 칸(COAT_SLOT): 방한 외투 1/2.
    //  장갑 칸: 방한 장갑을 가지고 있으면 끼고 있는 것이 기본, GLOVES_OFF = 1이면 벗음. 화면 🎒 장비 창에서 바꿔 낀다(mineEquipRing).
    static final int MINE_RING_DRAGON = 3;
    // [2026-11-15] "용의 반지 Lv1을 (월드보스가) 확률 드랍, 착용 시 운영자 맵 이동을 한 시간에 한 번" -- 반지 칸 4. 끼고 있으면 전체 지도에서 누른 지역의 시작점으로 간다
    //  (관리자 전체 지도 이동 mineAdminWarp와 같은 목록, 단 들어갈 자격이 있는 곳만 -- mineMapBlock). 마지막으로 쓴 시각 = WYRM_WARP_AT(분).
    static final int MINE_RING_WYRM = 4;
    static final String MINE_ITEM_WYRM_RING = "WYRM_RING", MINE_ITEM_WYRM_WARP = "WYRM_WARP_AT";
    static final long MINE_WYRM_CD_MS = 3_600_000L;
    static boolean mineWyrmOn(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_WYRM_RING, 0) > 0 && items.getOrDefault(MINE_ITEM_RING_SLOT, 0) == MINE_RING_WYRM; }
    static final String MINE_ITEM_GLOVES_OFF = "GLOVES_OFF";

    static boolean mineWaterWalk(Map<String, Integer> items) {
        return mineHasDragonRing(items) && items.getOrDefault(MINE_ITEM_RING_SLOT, 0) == MINE_RING_DRAGON;
    }

    static boolean mineHasDragonRing(Map<String, Integer> items) {
        return items.getOrDefault(MINE_ITEM_DRAGON_RING, 0) > 0;
    }

    static boolean mineGlovesOn(Map<String, Integer> items) {
        return MINE_ITEM_GLOVES.equals(mineGloveCode(items)); // [2026-10-28] 재생/광전사 장갑을 끼면 방한 장갑은 벗은 것
    }

    /** 재료 코드 -> 화면 등급 값(아이콘)과 이름. */
    static String[] mineWithSeaGems(String[] sc) {
        String[] out = java.util.Arrays.copyOf(sc, sc.length + S5MineMap.SEA_GEM_CODES.length);
        System.arraycopy(S5MineMap.SEA_GEM_CODES, 0, out, sc.length, S5MineMap.SEA_GEM_CODES.length);
        return out;
    }

    static int mineCodeGrade(String code) {
        for (int i = 0; i < S5MineMap.ORE_CODES.length; i++) if (S5MineMap.ORE_CODES[i].equals(code)) return i + 1;
        for (int i = 0; i < S5MineMap.DAY_STONE_CODES.length; i++) if (S5MineMap.DAY_STONE_CODES[i].equals(code)) return S5MineMap.DAY_STONE_GRADE_BASE + i;
        if (S5MineMap.SEVEN_STONE_CODE.equals(code)) return S5MineMap.SEVEN_STONE_GRADE;
        if (S5MineMap.ICE_STONE_CODE.equals(code)) return S5MineMap.ICE_STONE_GRADE; // [2026-10-28]
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) if (S5MineMap.GEM_CODES[i].equals(code)) return S5MineMap.GEM_GRADE_BASE + i;
        if (S5MineMap.SEAL_CODE.equals(code)) return S5MineMap.SEAL_GRADE;
        for (int i = 0; i < S5MineMap.WEAPON_SHARD_CODES.length; i++) if (S5MineMap.WEAPON_SHARD_CODES[i].equals(code)) return S5MineMap.NINJA_SHARD_GRADE + i; // [2026-10-29] 무기 조각 60~63
        for (int i = 0; i < S5MineMap.ARMOR_SHARD_CODES.length; i++) if (S5MineMap.ARMOR_SHARD_CODES[i].equals(code)) return S5MineMap.ARMOR_SHARD_GRADE + i; // [2026-11-06] 갑옷 조각 66~69
        if (S5MineMap.GALAXY_CODE.equals(code)) return S5MineMap.GALAXY_GRADE; // [2026-11-06] 은하수 조각
        if (S5MineMap.MOON_STONE_CODE.equals(code)) return S5MineMap.MOON_STONE_GRADE; // [2026-10-28]
        for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) if (S5MineMap.SEA_GEM_CODES[i].equals(code)) return S5MineMap.SEA_GEM_GRADE_BASE + i; // [2026-10-28] 바다 보석
        if (S5MineMap.NINJA_BOOK_CODE.equals(code)) return S5MineMap.NINJA_BOOK_GRADE;
        if (S5MineMap.ICE_HEART_CODE.equals(code)) return S5MineMap.ICE_HEART_GRADE; // [2026-10-28] 설산 봉우리
        if (S5MineMap.DESERT_HEART_CODE.equals(code)) return S5MineMap.DESERT_HEART_GRADE; // [2026-10-28] 피라미드 보상 상자
        if (S5MineMap.VOLCANO_HEART_CODE.equals(code)) return S5MineMap.VOLCANO_HEART_GRADE; // [2026-10-30]
        if (S5MineMap.THUNDER_HEART_CODE.equals(code)) return S5MineMap.THUNDER_HEART_GRADE; // [2026-11-05]
        if (S5MineMap.WIND_HEART_CODE.equals(code)) return S5MineMap.WIND_HEART_GRADE; // [2026-11-11]
        if (S5MineMap.TOWER_TOKEN_CODE.equals(code)) return S5MineMap.TOWER_TOKEN_GRADE; // [2026-11-15]
        if (S5MineMap.DUSK_SEAL_CODE.equals(code)) return S5MineMap.DUSK_SEAL_GRADE; // [2026-11-17]
        if (S5MineMap.DAWN_SEAL_CODE.equals(code)) return S5MineMap.DAWN_SEAL_GRADE;
        if (S5MineMap.DEMON_ORE_CODE.equals(code)) return S5MineMap.DEMON_ORE_GRADE;
        if (S5MineMap.DOOM_SHARD_CODE.equals(code)) return S5MineMap.DOOM_SHARD_GRADE;
        if (S5MineMap.LIGHT_HEART_CODE.equals(code)) return S5MineMap.LIGHT_HEART_GRADE; // [2026-11-17]
        if (S5MineMap.DARK_HEART_CODE.equals(code)) return S5MineMap.DARK_HEART_GRADE; // [2026-11-18]
        if (S5MineMap.JUDGE_TOKEN_CODE.equals(code)) return S5MineMap.JUDGE_TOKEN_GRADE;
        if (S5MineMap.MANA_STONE_CODE.equals(code)) return S5MineMap.MANA_STONE_GRADE;
        if (S5MineMap.WATER_HEART_CODE.equals(code)) return S5MineMap.WATER_HEART_GRADE;
        if (S5MineMap.WP_KIT_CODE.equals(code)) return S5MineMap.WP_KIT_GRADE;
        if (S5MineMap.WOOD_CODE.equals(code)) return S5MineMap.WOOD_GRADE; // [2026-11-19]
        if (S5MineMap.PRIMAL_GEM_CODE.equals(code)) return S5MineMap.PRIMAL_GEM_GRADE; // [2026-11-20]
        if (S5MineMap.SUN_STONE_CODE.equals(code)) return S5MineMap.SUN_STONE_GRADE; // [2026-11-23]
        if (S5MineMap.SQ_MEDAL_CODE.equals(code)) return S5MineMap.SQ_MEDAL_GRADE; // [2026-11-23]
        for (int i = 0; i < S5MineMap.BAR_CODES.length; i++) if (S5MineMap.BAR_CODES[i].equals(code)) return S5MineMap.BAR_GRADE_BASE + i; // [2026-10-28] 괴
        for (int i = 0; i < S5MineMap.GEM_CODES.length; i++) if (S5MineMap.cutCode(i).equals(code)) return S5MineMap.CUT_GRADE_BASE + i; // [2026-10-28] 세공된 보석
        if (S5MineMap.CUT_ICE_CODE.equals(code)) return S5MineMap.CUT_ICE_GRADE; // [2026-10-28] 세공된 빙석
        for (int i = 0; i < S5MineMap.SEA_GEM_CODES.length; i++) if (S5MineMap.seaCutCode(i).equals(code)) return S5MineMap.SEA_CUT_GRADE_BASE + i; // 세공된 바다 보석
        return 0;
    }

    static String mineCodeName(String code) { // [2026-10-28] 광산 접속 기록(S5MineSessionLog)도 쓴다
        int g = mineCodeGrade(code);
        if (g == S5MineMap.ICE_STONE_GRADE) return S5MineMap.ICE_STONE_NAME; // [2026-10-28]
        if (g == S5MineMap.SEAL_GRADE) return S5MineMap.SEAL_NAME;
        if (g == S5MineMap.MOON_STONE_GRADE) return S5MineMap.MOON_STONE_NAME; // [2026-10-28]
        if (g == S5MineMap.NINJA_BOOK_GRADE) return S5MineMap.NINJA_BOOK_NAME;
        if (g >= S5MineMap.SEA_GEM_GRADE_BASE && g < S5MineMap.SEA_GEM_GRADE_BASE + S5MineMap.SEA_GEM_CODES.length) return S5MineMap.SEA_GEM_NAMES[g - S5MineMap.SEA_GEM_GRADE_BASE]; // [2026-10-28] 바다 보석(보석 줄보다 먼저)
        if (g == S5MineMap.ICE_HEART_GRADE) return S5MineMap.ICE_HEART_NAME; // [2026-10-28] (보석 줄보다 먼저 -- 33은 보석 번호 밖)
        if (g == S5MineMap.DESERT_HEART_GRADE) return S5MineMap.DESERT_HEART_NAME; // [2026-10-28]
        if (g == S5MineMap.VOLCANO_HEART_GRADE) return S5MineMap.VOLCANO_HEART_NAME; // [2026-10-30]
        if (g == S5MineMap.THUNDER_HEART_GRADE) return S5MineMap.THUNDER_HEART_NAME; // [2026-11-05]
        if (g == S5MineMap.WIND_HEART_GRADE) return S5MineMap.WIND_HEART_NAME; // [2026-11-11]
        if (g >= S5MineMap.NINJA_SHARD_GRADE && g < S5MineMap.NINJA_SHARD_GRADE + S5MineMap.WEAPON_SHARD_NAMES.length) return S5MineMap.WEAPON_SHARD_NAMES[g - S5MineMap.NINJA_SHARD_GRADE]; // [2026-10-29]
        if (g >= S5MineMap.ARMOR_SHARD_GRADE && g < S5MineMap.ARMOR_SHARD_GRADE + S5MineMap.ARMOR_SHARD_NAMES.length) return S5MineMap.ARMOR_SHARD_NAMES[g - S5MineMap.ARMOR_SHARD_GRADE]; // [2026-11-06]
        if (g == S5MineMap.GALAXY_GRADE) return S5MineMap.GALAXY_NAME; // [2026-11-06]
        if (g == S5MineMap.TOWER_TOKEN_GRADE) return S5MineMap.TOWER_TOKEN_NAME; // [2026-11-15]
        if (g == S5MineMap.DUSK_SEAL_GRADE) return S5MineMap.DUSK_SEAL_NAME; // [2026-11-17]
        if (g == S5MineMap.DAWN_SEAL_GRADE) return S5MineMap.DAWN_SEAL_NAME;
        if (g == S5MineMap.DEMON_ORE_GRADE) return S5MineMap.DEMON_ORE_NAME;
        if (g == S5MineMap.DOOM_SHARD_GRADE) return S5MineMap.DOOM_SHARD_NAME;
        if (g == S5MineMap.LIGHT_HEART_GRADE) return S5MineMap.LIGHT_HEART_NAME; // [2026-11-17]
        if (g == S5MineMap.DARK_HEART_GRADE) return S5MineMap.DARK_HEART_NAME; // [2026-11-18]
        if (g == S5MineMap.JUDGE_TOKEN_GRADE) return S5MineMap.JUDGE_TOKEN_NAME;
        if (g == S5MineMap.MANA_STONE_GRADE) return S5MineMap.MANA_STONE_NAME;
        if (g == S5MineMap.WATER_HEART_GRADE) return S5MineMap.WATER_HEART_NAME;
        if (g == S5MineMap.WP_KIT_GRADE) return S5MineMap.WP_KIT_NAME;
        if (g == S5MineMap.WOOD_GRADE) return S5MineMap.WOOD_NAME;
        if (g == S5MineMap.PRIMAL_GEM_GRADE) return S5MineMap.PRIMAL_GEM_NAME;
        if (g == S5MineMap.SUN_STONE_GRADE) return S5MineMap.SUN_STONE_NAME; // [2026-11-23]
        if (g == S5MineMap.SQ_MEDAL_GRADE) return S5MineMap.SQ_MEDAL_NAME;
        if (g >= S5MineMap.BAR_GRADE_BASE && g < S5MineMap.BAR_GRADE_BASE + S5MineMap.BAR_CODES.length) return S5MineMap.BAR_NAMES[g - S5MineMap.BAR_GRADE_BASE]; // [2026-10-28] 괴(보석 줄보다 먼저)
        if (g >= S5MineMap.CUT_GRADE_BASE && g < S5MineMap.CUT_GRADE_BASE + S5MineMap.GEM_CODES.length) return S5MineMap.cutName(g - S5MineMap.CUT_GRADE_BASE); // 세공된 보석
        if (g == S5MineMap.CUT_ICE_GRADE) return S5MineMap.CUT_ICE_NAME;
        if (g >= S5MineMap.SEA_CUT_GRADE_BASE && g < S5MineMap.SEA_CUT_GRADE_BASE + S5MineMap.SEA_GEM_CODES.length) return S5MineMap.seaCutName(g - S5MineMap.SEA_CUT_GRADE_BASE);
        if (g >= S5MineMap.GEM_GRADE_BASE) return S5MineMap.GEM_NAMES[g - S5MineMap.GEM_GRADE_BASE];
        if (g == S5MineMap.SEVEN_STONE_GRADE) return S5MineMap.SEVEN_STONE_NAME;
        return mineGradeName(g);
    }

    Map<String, Integer> mineOreHave(String userName) {
        Map<String, Integer> have = new HashMap<>();
        for (HashMap<String, Object> r : dao.selectUserOres(userName)) have.put(strVal(r.get("ORE_CODE"), ""), intVal(r.get("QTY"), 0));
        return have;
    }

    /** 재료 목록(화면용) -- [{grade, name, need, have}]. */
    static List<HashMap<String, Object>> mineCostList(String[][] cost, Map<String, Integer> have) {
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

    /** [2026-10-29] 모자란 재료(" · 모자란 것: 칠요석 1개, 금괴 2개", 다 있으면 ""). 버튼을 막지 않고 서버가 안내한다. */
    String mineLackText(String[][] cost, String userName) {
        Map<String, Integer> have = mineOreHave(userName);
        StringBuilder sb = new StringBuilder();
        for (String[] c : cost) {
            int lack = Integer.parseInt(c[1]) - have.getOrDefault(c[0], 0);
            if (lack > 0) sb.append(sb.length() > 0 ? ", " : "").append(mineCodeName(c[0])).append(' ').append(lack).append("개");
        }
        return sb.length() > 0 ? " · 모자란 것: " + sb : "";
    }

    static boolean mineCostOk(String[][] cost, Map<String, Integer> have) {
        for (String[] c : cost) if (have.getOrDefault(c[0], 0) < Integer.parseInt(c[1])) return false;
        return true;
    }

    static String mineCostText(String[][] cost) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : cost) sb.append(sb.length() > 0 ? ", " : "").append(mineCodeName(c[0])).append(' ').append(c[1]).append("개");
        return sb.toString();
    }

    /** 재료를 낸다 -- 호출 전에 유저 행을 잠그고(selectMineUserForUpdate) mineCostOk로 확인했으므로 실패하면 롤백(예외). */
    void mineTakeCost(String userName, String[][] cost, int times) {
        for (String[] c : cost) {
            if (dao.takeUserOre(userName, c[0], Integer.parseInt(c[1]) * times) == 0) throw new IllegalStateException("재료가 부족해요.");
        }
    }

    /** 장비 화면 데이터(장신구/장화 목록, 낀 반지, 이동 속도, 물 위 걷기, 선장 퀘스트) + 광석 목록을 out에 넣는다. */
    // ---- [2026-10-28] 다도해(운영자만) -- 신궁 수문장(수색 임무), 배 목수(달빛석 -> 1인용 배), 뱃사공(배 타기 -> 뱃길) ----
    static final String MINE_ITEM_NINJA_QUEST = "NINJA_QUEST"; // 0 없음, 1 수색 중(보상을 받으면 다시 0 -- 되풀이 가능)
    static final String MINE_ITEM_NINJA_KILLS = "NINJA_KILLS";
    static final int MINE_NINJA_NEED = 100;
    static final int MINE_NINJA_KEY_PCT = 2; // [2026-10-28] 수색 중 닌자 처치마다 열쇠가 떨어질 확률(%) -- 평균 50마리쯤
    /** 1인용 배 = 달빛석 1개 + "보석류가 골고루 많이 사용되도록" 보석 9종(재스퍼~다이아몬드). */
    static final String[][] MINE_BOAT_COST = { { S5MineMap.MOON_STONE_CODE, "1" }, mineCost(0, 20), mineCost(1, 15), mineCost(2, 8), mineCost(3, 15),
            mineCost(4, 15), mineCost(5, 15), mineCost(6, 15), mineCost(7, 8), mineCost(8, 3) };

    /** 다도해를 쓸 수 있는지 -- 아직 운영자(광산 관리자)만. 열 때는 여기만 바꾸면 된다. */
    /** [2026-10-28] "본섬만 열어줘" -- 다도해 본섬(ARCHI)은 모두에게, 신궁/뱃길/섬은 아직 운영자만(mineArchiOpen). */
    boolean mineArchiMapOpen(String userName, String mapId) {
        return S5MineMap.ARCHI.equals(mapId) || mineArchiOpen(userName);
    }

    boolean mineArchiOpen(String userName) {
        return true; // [2026-10-28] "다도해 전부 오픈" -- 신궁/뱃길/섬/본섬 NPC 일(수색 임무, 배)까지 모두에게(예전: 운영자만)
    }

    // [2026-11-18] 배 체력(해상전투 준비) -- 1인용 배 최대 체력 = MINE_BOAT_HP(+ 배 강화 BOAT_LV 1단계마다 +10%, 최대 +10 -- 강화는 다음에).
    //  받은 피해는 BOAT_DMG에 쌓는다(0 = 멀쩡, 새 아이템 없이 기존 사용자도 가득 찬 배). 바다 몬스터에게 맞으면 캐릭터 대신 배가 깎이고(mineBoatHit),
    //  0이 되면 배가 부서져 남쪽 바닷가로 떠밀려 오며, 고치기 전에는 배를 띄울 수 없다(mineSail). 수리는 바닷가 뱃사람/해상도시 부두지기(해상전투와 함께).
    static final String MINE_ITEM_BOAT_DMG = "BOAT_DMG", MINE_ITEM_BOAT_LV = "BOAT_LV";
    static final long MINE_BOAT_HP = 200_000L;
    static long mineBoatHpMax(Map<String, Integer> items) { return MINE_BOAT_HP + MINE_BOAT_HP * Math.max(0, Math.min(10, items.getOrDefault(MINE_ITEM_BOAT_LV, 0))) / 10; }
    /** {지금 체력, 최대 체력} */
    static long[] mineBoatHp(Map<String, Integer> items) { long mx = mineBoatHpMax(items); return new long[] { Math.max(0, mx - Math.max(0, items.getOrDefault(MINE_ITEM_BOAT_DMG, 0))), mx }; }
    /** 배가 dmg만큼 맞는다 -- 남은 체력. [2026-11-18] "배가 부서지면 마을로 보내 버리자" -- 0이 되면 마을(부활 거점)로 떠밀려 가고 out에 안내/상태를 담는다. */
    // [2026-11-18] DB 점검 + "수병 소환 때 렉" -- 맞을 때마다(망망대해는 매초) BOAT_DMG를 쓰고 그때마다 아이템 캐시가 깨졌다.
    //  피해는 MINE_BOAT_BUF(유저 -> {아직 안 쓴 피해, 마지막 저장 시각})에 모으고, MINE_BOAT_FLUSH_MS마다·부서질 때·맵을 옮길 때·정리 스레드(mineMercXpSweep)에서 증분으로 쓴다.
    static final long MINE_BOAT_FLUSH_MS = 15_000L;
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_BOAT_BUF = new java.util.concurrent.ConcurrentHashMap<>();
    static long mineBoatPending(String userName) { long[] b = userName == null ? null : MINE_BOAT_BUF.get(userName); if (b == null) return 0; synchronized (b) { return b[0]; } }
    /** 아직 안 쓴 피해까지 친 {지금 체력, 최대 체력}. */
    /** [2026-11-21] 배를 띄울 수 있는지(1인용 배가 있고 체력이 남음) -- 화면 seaBoatOk와 같은 규칙. */
    static boolean mineCanSail(String userName, Map<String, Integer> items) { return items.getOrDefault(S5MineMap.BOAT_ITEM, 0) > 0 && mineBoatHpU(userName, items)[0] > 0; }
    /** [2026-11-21] (x, y)에서 가장 가까운, 시작점에서 걸어서 닿는 뭍 칸(넘어가는 칸 제외), 없으면 null -- 떨어진 작은 섬에 내려 다시 갇히지 않게. */
    static int[] mineNearLand(S5MineMap m, int x, int y) {
        boolean[] ok = new boolean[m.w * m.h];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        if (m.walkable(m.spawnX, m.spawnY)) { ok[m.spawnY * m.w + m.spawnX] = true; q.add(new int[] { m.spawnX, m.spawnY }); }
        int[][] dv = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dv) { int nx = c[0] + d[0], ny = c[1] + d[1]; if (!m.inside(nx, ny) || ok[ny * m.w + nx] || !m.walkable(nx, ny)) continue; ok[ny * m.w + nx] = true; q.add(new int[] { nx, ny }); }
        }
        for (int r = 1; r <= Math.max(m.w, m.h); r++) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
            int xx = x + dx, yy = y + dy;
            if (m.inside(xx, yy) && ok[yy * m.w + xx] && m.portalAt(xx, yy) == null) return new int[] { xx, yy };
        }
        return null;
    }
    static long[] mineBoatHpU(String userName, Map<String, Integer> items) { long[] h = mineBoatHp(items); h[0] = Math.max(0, h[0] - mineBoatPending(userName)); return h; }
    void mineBoatFlush(String userName) {
        long[] b = userName == null ? null : MINE_BOAT_BUF.get(userName);
        if (b == null) return;
        long p;
        synchronized (b) { p = b[0]; b[0] = 0; b[1] = System.currentTimeMillis(); }
        if (p <= 0) { MINE_BOAT_BUF.remove(userName, b); return; }
        try { dao.addMineItemLevel(userName, MINE_ITEM_BOAT_DMG, (int) Math.min(Integer.MAX_VALUE, p)); }
        catch (Exception e) { synchronized (b) { b[0] += p; } } // 다음에 다시
    }
    void mineBoatSweep(boolean all) {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, long[]> en : MINE_BOAT_BUF.entrySet()) { long last; synchronized (en.getValue()) { last = en.getValue()[1]; } if (all || now - last >= MINE_BOAT_FLUSH_MS) mineBoatFlush(en.getKey()); }
    }
    long mineBoatHit(String userName, Map<String, Integer> items, long dmg, HashMap<String, Object> out) {
        long mx = mineBoatHpMax(items), base = Math.max(0, items.getOrDefault(MINE_ITEM_BOAT_DMG, 0)), d;
        long now = System.currentTimeMillis();
        long[] b = MINE_BOAT_BUF.computeIfAbsent(userName, k -> new long[] { 0, now });
        boolean flush;
        synchronized (b) {
            d = Math.min(mx, base + b[0] + Math.max(0, dmg));
            b[0] = d - base;
            flush = d >= mx || now - b[1] >= MINE_BOAT_FLUSH_MS;
        }
        if (flush) { mineBoatFlush(userName); items.put(MINE_ITEM_BOAT_DMG, (int) d); }
        if (d >= mx) {
            mineDisengageAll(userName);
            String where = mineGoHome(userName, items, System.currentTimeMillis());
            if (out != null) {
                out.put("boatWreck", true);
                out.put("message", "💥 배가 부서졌어요! 부서진 배 조각에 매달려 떠돌다 " + where + " 정신을 차렸어요. 배는 남쪽 바닷가 뱃사람에게 고쳐야 해요.");
                out.put("state", mineState(userName));
            }
        }
        return mx - d;
    }
    /** 배 수리(다도해 본섬 배 목수) -- 깎인 만큼 진주(20%마다 1개, 최대 5개). */
    static final int MINE_BOAT_FIX_STEP_PCT = 20;
    // [2026-11-19] "배 수리할 때 재료를 선택해서 납부 -- 바다 보석이나 나무, 나무 1개당 내구 5, 보석은 20" -- 배 체력(최대)의 %. 바다 보석 다섯 가지 아무거나 1개당 20%, 나무 1개당 5%.
    //  고를 때 개수를 함께(가득 채우는 데 필요한 만큼까지만 받는다 -- 넘치게 내지 않게).
    static int mineBoatMatPct(String code) {
        if (S5MineMap.WOOD_CODE.equals(code)) return 5;
        for (String c : S5MineMap.SEA_GEM_CODES) if (c.equals(code)) return 100; // [2026-11-21] "배 수리 시 보석 1개로 100% 수리" (예전 20%)
        return 0;
    }
    List<HashMap<String, Object>> mineBoatMats(Map<String, Integer> have, long[] bh) {
        List<HashMap<String, Object>> l = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        codes.add(S5MineMap.WOOD_CODE);
        codes.addAll(java.util.Arrays.asList(S5MineMap.SEA_GEM_CODES));
        for (String c : codes) {
            int pct = mineBoatMatPct(c);
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", c);
            m.put("name", mineCodeName(c));
            m.put("grade", mineCodeGrade(c));
            m.put("have", have.getOrDefault(c, 0));
            m.put("pct", pct);
            m.put("need", bh[0] >= bh[1] ? 0 : mineBoatMatNeed(bh, pct));
            l.add(m);
        }
        return l;
    }
    static int mineBoatMatNeed(long[] bh, int pct) { long miss = bh[1] - bh[0], per = Math.max(1, bh[1] * pct / 100); return (int) Math.max(1, (miss + per - 1) / per); }
    HashMap<String, Object> mineBoatFix(String userName, String code, int n) {
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        String at = strVal(row.get("MAP_ID"), "");
        if (!S5MineMap.ARCHI.equals(at) && !S5MineMap.BEACH.equals(at)) return mineGearResult(userName, false, "⛵ 배는 배 목수(다도해 본섬·남쪽 바닷가)가 고쳐 줘요."); // [2026-11-18] 뱃사람/부두지기를 없애서 배 목수로
        Map<String, Integer> items = mineItems(userName);
        if (items.getOrDefault(S5MineMap.BOAT_ITEM, 0) < 1) return mineGearResult(userName, false, "⛵ 고칠 배가 없어요.");
        long[] bh = mineBoatHpU(userName, items);
        if (bh[0] >= bh[1]) return mineGearResult(userName, false, "⛵ 배는 멀쩡해요.");
        int pct = mineBoatMatPct(code);
        if (pct <= 0) return mineGearResult(userName, false, "⛵ 그 재료로는 배를 고칠 수 없어요. (바다 보석 또는 나무)");
        int need = mineBoatMatNeed(bh, pct), use = Math.max(1, Math.min(n, need)), haveN = mineOreHave(userName).getOrDefault(code, 0);
        String nm = mineCodeName(code);
        if (haveN <= 0) return mineGearResult(userName, false, "⛵ " + nm + "이(가) 없어요." + (S5MineMap.WOOD_CODE.equals(code) ? " 하피의 둥지 나무를 곡괭이로 쓰러뜨리면 얻을 수 있어요." : ""));
        use = Math.min(use, haveN);
        if (dao.takeUserOre(userName, code, use) == 0) return mineGearResult(userName, false, "⛵ " + nm + "이(가) 모자라요.");
        long fix = bh[1] * pct / 100 * use, left = Math.max(0, (bh[1] - bh[0]) - fix);
        MINE_BOAT_BUF.remove(userName); // [2026-11-18] 모아 둔 피해도 함께(지금 체력 bh는 그것까지 넣어 셌다)
        dao.upsertMineItem(userName, MINE_ITEM_BOAT_DMG, (int) left);
        HashMap<String, Object> out = mineGearResult(userName, true, left == 0 ? "🔨 배를 말끔히 고쳤어요! (" + nm + " " + use + "개)" : "🔨 배를 고쳤어요! (" + nm + " " + use + "개 · +" + (pct * use) + "%) 배 체력 " + String.format("%,d", bh[1] - left) + " / " + String.format("%,d", bh[1]));
        out.put("ores", mineOreList(userName));
        return out;
    }
    static int mineBoatFixPearls(long[] bh) { long miss = bh[1] - bh[0]; return (int) Math.max(1, Math.min(100 / MINE_BOAT_FIX_STEP_PCT, (miss * 100 + bh[1] * MINE_BOAT_FIX_STEP_PCT - 1) / (bh[1] * MINE_BOAT_FIX_STEP_PCT))); }
    /** [2026-11-18] "남쪽 바닷가에서 NPC를 누르면 배를 타고 바다 위로" -- 남쪽 바닷가 뱃사람: 다도해 뱃길 / 바다 위, 해상도시 부두지기: 바다 위. 1인용 배가 있어야 하고 배 체력이 남아야 한다. */
    HashMap<String, Object> mineSail(String userName, String to) {
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        String from = strVal(row.get("MAP_ID"), "");
        boolean route = (S5MineMap.BEACH.equals(from) && (S5MineMap.ARCHISEA.equals(to) || S5MineMap.OPEN_SEA.equals(to))) || (S5MineMap.SEA_CITY.equals(from) && S5MineMap.OPEN_SEA.equals(to));
        if (!route) return mineGearResult(userName, false, "⛵ 여기서는 그쪽으로 배를 띄울 수 없어요.");
        Map<String, Integer> items = mineItems(userName);
        if (items.getOrDefault(S5MineMap.BOAT_ITEM, 0) < 1) return mineGearResult(userName, false, "⛵ 뱃사람: 자네 배가 없구먼. 다도해 본섬 배 목수에게 1인용 배부터 만들어 오게. 본섬은 남쪽 섬에서 큰 배로 갈 수 있다네.");
        if (mineBoatHpU(userName, items)[0] <= 0) return mineGearResult(userName, false, "⛵ 배가 부서져 있어요. 먼저 고쳐야 바다로 나갈 수 있어요.");
        S5MineMap sea = S5MineMap.get(to);
        String block = mineMapBlock(sea, items, mineLv(row));
        if (block != null) return mineGearResult(userName, false, block);
        long now = System.currentTimeMillis();
        if (mineEngaged(userName, now)) return mineGearResult(userName, false, MINE_FIGHT_BLOCK);
        int[] at = S5MineMap.sailLanding(from, to);
        dao.updateMinePos(userName, sea.id, at[0], at[1], now);
        mineDisengageAll(userName);
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("message", "⛵ 1인용 배를 띄워 " + sea.name + "(으)로 나왔어요! " + (S5MineMap.ARCHISEA.equals(to) ? "위로 저어 가며 섬 부두(판자)에 닿으면 내릴 수 있어요." : S5MineMap.BEACH.equals(from) ? "남쪽 끝 해상도시 부두(판자)에 닿으면 내릴 수 있어요." : "북쪽 끝 부두로 가면 남쪽 바닷가예요."));
        out.put("state", mineState(userName));
        return out;
    }

    /** action: quest(수색 임무 받기) / claim(닌자 100명 -> 비급서 + 달빛석) / boat(달빛석 1개 -> 1인용 배) / board(배 타고 뱃길로) / sail:맵(바닷가·해상도시에서 배 띄우기). 본섬에서만(sail 빼고). */
    @Override
    @Transactional
    public HashMap<String, Object> mineArchi(String userName, String action) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        if (!mineArchiOpen(userName)) return mineGearResult(userName, false, "🚧 아직 준비 중이에요. 곧 열려요!"); // 본섬 NPC 일(신궁 임무/배/뱃길)은 아직 운영자만
        mineRow(userName, false);
        if (action != null && action.startsWith("sail:")) return mineSail(userName, action.substring(5)); // [2026-11-18] 바닷가 뱃사람 / 해상도시 부두지기
        if (action != null && (action.equals("boatfix") || action.startsWith("boatfix:"))) { String[] ba = action.split(":"); int bn; try { bn = ba.length > 2 ? Integer.parseInt(ba[2]) : 999; } catch (NumberFormatException ex) { bn = 1; } return mineBoatFix(userName, ba.length > 1 ? ba[1] : S5MineMap.SEA_GEM_CODES[0], bn); } // [2026-11-18] 배 수리, [2026-11-19] boatfix:재료:개수
        if ("fishinv".equals(action)) return mineFishInv(userName); // [2026-11-19] 인벤토리 낚시 탭
        if ("krkrespawn".equals(action) && isMineAdmin(userName)) return mineKrkRespawnNow(); // [2026-11-19] 관리자 -- 크라켄 다시 살리기
        if ("wpdur".equals(action)) return mineWpDurInfo(userName, new HashMap<String, Object>()); // [2026-11-18] 워프 포인트 관리인
        if (action != null && action.startsWith("wpfix:")) { String[] wa = action.split(":"); int wn; try { wn = wa.length > 2 ? Integer.parseInt(wa[2]) : 1; } catch (NumberFormatException ex) { wn = 1; } return mineWpFix(userName, wa.length > 1 ? wa[1] : "", wn); }
        if (action != null && (action.startsWith("bell:") || action.startsWith("buoy:"))) { int bix; try { bix = Integer.parseInt(action.substring(5)); } catch (NumberFormatException ex) { bix = -1; } return mineBell(userName, bix); } // [2026-11-18] 신호 부표
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.ARCHI.equals(strVal(row.get("MAP_ID"), "")) && !("boat".equals(action) && S5MineMap.BEACH.equals(strVal(row.get("MAP_ID"), "")))) return mineGearResult(userName, false, "다도해 본섬에서만 할 수 있어요."); // [2026-11-18] 배 만들기는 남쪽 바닷가 배 목수도
        Map<String, Integer> items = mineItems(userName);
        int q = items.getOrDefault(MINE_ITEM_NINJA_QUEST, 0), kills = items.getOrDefault(MINE_ITEM_NINJA_KILLS, 0);
        long now = System.currentTimeMillis();
        // [2026-10-28] "100마리 잡으면 보상 대신 보스 방 열쇠" -- 수색 임무 보상은 신궁 열쇠(한 번 받으면 계속, 임무도 더는 없음).
        //  비급서/달빛석은 닌자 두목 첫 처치 보상(mineShrineBossDrops)으로 옮겼다.
        boolean hasKey = items.getOrDefault(S5MineMap.SHRINE_KEY_ITEM, 0) > 0;
        if ("quest".equals(action)) {
            if (hasKey) return mineGearResult(userName, false, "🥷 신궁 수문장: 이미 열쇠를 드렸소. 동쪽 문으로 들어가 닌자 두목을 쓰러뜨려 주시오.");
            if (q == 1) return mineGearResult(userName, false, "🥷 신궁 수문장: 이미 수색 중이오. (" + kills + "/" + MINE_NINJA_NEED + ")");
            dao.upsertMineItem(userName, MINE_ITEM_NINJA_QUEST, 1);
            dao.upsertMineItem(userName, MINE_ITEM_NINJA_KILLS, 0);
            return mineGearResult(userName, true, "🥷 신궁 수문장: 고맙소! 신궁 1~3층의 닌자 " + MINE_NINJA_NEED + "명을 처치하고 돌아오시오. 닌자 두목의 방 열쇠를 드리리다.");
        }
        if ("claim".equals(action)) {
            if (hasKey) return mineGearResult(userName, false, "🥷 신궁 수문장: 이미 열쇠를 드렸소.");
            if (q != 1) return mineGearResult(userName, false, "🥷 신궁 수문장: 먼저 수색 임무를 받아 주시오.");
            if (kills < MINE_NINJA_NEED) return mineGearResult(userName, false, "🥷 신궁 수문장: 아직 닌자가 남아 있소. (" + kills + "/" + MINE_NINJA_NEED + ")");
            dao.upsertMineItem(userName, S5MineMap.SHRINE_KEY_ITEM, 1);
            dao.upsertMineItem(userName, MINE_ITEM_NINJA_QUEST, 0);
            dao.upsertMineItem(userName, MINE_ITEM_NINJA_KILLS, 0);
            HashMap<String, Object> out = mineGearResult(userName, true, "🗝️ 신궁 열쇠를 받았어요! 신궁 동쪽 문(닌자 두목의 방)으로 들어갈 수 있어요. 두목을 처음 쓰러뜨리면 📜 비급서와 🌙 달빛석!");
            out.put("rewardFlash", true);
            return out;
        }
        if ("boat".equals(action)) {
            if (items.getOrDefault(S5MineMap.BOAT_ITEM, 0) > 0) return mineGearResult(userName, false, "⛵ 배 목수: 이미 배가 있잖나. 뱃사공에게 가 보게.");
            String[][] cost = MINE_BOAT_COST;
            if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "⛵ 배 목수: 재료가 모자라네. (" + mineCostText(cost) + mineLackText(cost, userName) + ") 달빛석은 신궁 깊은 곳 닌자 두목이 지니고 있다더군."); // [2026-10-28]
            mineTakeCost(userName, cost, 1);
            dao.upsertMineItem(userName, S5MineMap.BOAT_ITEM, 1);
            HashMap<String, Object> out = mineGearResult(userName, true, "⛵ 1인용 배가 완성됐어요! 뱃사공에게 배 타기를 부탁하면 뱃길로 나갈 수 있어요.");
            out.put("ores", mineOreList(userName));
            out.put("rewardFlash", true);
            return out;
        }
        if ("board".equals(action)) {
            if (items.getOrDefault(S5MineMap.BOAT_ITEM, 0) < 1) return mineGearResult(userName, false, "⛵ 뱃사공: 배가 없으면 못 나간다네. 배 목수에게 달빛석으로 배를 만들어 달라고 하게.");
            if (mineEngaged(userName, now)) return mineGearResult(userName, false, MINE_FIGHT_BLOCK);
            S5MineMap sea = S5MineMap.get(S5MineMap.ARCHISEA);
            dao.updateMinePos(userName, sea.id, sea.spawnX, sea.spawnY, now);
            mineDisengageAll(userName);
            HashMap<String, Object> out = new HashMap<>();
            out.put("ok", true);
            out.put("message", "⛵ 배를 타고 다도해 뱃길로 나왔어요! 위로 저어 가며 섬 부두(판자)에 닿으면 내릴 수 있어요.");
            out.put("state", mineState(userName));
            return out;
        }
        return mineGearResult(userName, false, "알 수 없는 요청이에요.");
    }

    /** [2026-10-28] 🎒 장비 창 -- 칸(반지/옷/장갑)마다 가진 장비와 끼고 있는지. 없으면 그 칸은 빈 목록. */
    /** [2026-11-20] 무기/갑옷을 레벨 높은 순으로(같으면 원래 순서). */
    static List<MineWeapon> mineByLvDesc(MineWeapon[] l) { List<MineWeapon> c = new ArrayList<>(java.util.Arrays.asList(l)); c.sort((p, q) -> Integer.compare(q.lv, p.lv)); return c; }
    static List<HashMap<String, Object>> mineEquipList(Map<String, Integer> items) {
        List<HashMap<String, Object>> out = new ArrayList<>();
        int ring = items.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        for (MineGear g : MINE_GEAR) if ("ring".equals(g.kind) && items.getOrDefault(g.code, 0) > 0) {
            int t = Math.min(items.getOrDefault(g.code, 0), g.cost.length);
            out.add(mineEquipRow("ring", g.code, g.name + " " + t + "단계", g.effect[t - 1], ring == (MINE_ITEM_PLENTY_RING.equals(g.code) ? 1 : 2)));
        }
        if (mineHasDragonRing(items)) out.add(mineEquipRow("ring", MINE_ITEM_DRAGON_RING, "용왕의 반지", "물 위를 걸을 수 있음", ring == MINE_RING_DRAGON));
        if (items.getOrDefault(MINE_ITEM_WYRM_RING, 0) > 0) out.add(mineEquipRow("ring", MINE_ITEM_WYRM_RING, "용의 반지 Lv." + items.get(MINE_ITEM_WYRM_RING), "전체 지도에서 누른 곳으로 이동 (한 시간에 한 번)", ring == MINE_RING_WYRM)); // [2026-11-15]
        int coat = mineCoatDef(items);
        if (false) out.add(mineEquipRow("coat", MINE_ITEM_COAT1, "방한 외투", "한기 방어 1 · 받는 피해 2배", coat == 1));
        for (String bc : new String[] { MINE_ITEM_SAND_NECKLACE, MINE_ITEM_MIND_NECKLACE, MINE_ITEM_ASSASSIN_NECKLACE, MINE_ITEM_HUNT_NECKLACE, MINE_ITEM_MERC_DMG_NECK, MINE_ITEM_MERC_CD_NECK, MINE_ITEM_SWIFT_BOOTS }) { // [2026-11-23] 용병 목걸이 둘 // [2026-10-28] 목걸이 / 신발 칸
            MineGear g = mineGear(bc);
            int t = g == null ? 0 : Math.min(items.getOrDefault(bc, 0), g.cost.length);
            if (t < 1) continue;
            boolean neck = !MINE_ITEM_SWIFT_BOOTS.equals(bc);
            out.add(mineEquipRow(neck ? "necklace" : "boots", bc, g.name + " " + t + "단계", g.effect[t - 1], neck ? bc.equals(mineNeckCode(items)) : mineBootsTier(items) > 0));
        }
        if (items.getOrDefault(MINE_ITEM_BH_PEARL, 0) > 0) out.add(mineEquipRow("necklace", MINE_ITEM_BH_PEARL, "베히모스의 진주목걸이", "공격할 때 둘레를 휘도는 물 회오리(물 돌개베기 5단계)", MINE_ITEM_BH_PEARL.equals(mineNeckCode(items)))); // [2026-11-23]
        boolean pon = mineParasolOn(items); // [2026-10-28] 무기 칸 -- 칼(기본), 양산
        MineWeapon won = mineWeaponOn(items); // [2026-10-29] 드랍 무기(등급 = 색)
        out.add(mineEquipRow("weapon", "SWORD", "칼", "기본 무기", !pon && won == null));
        for (MineWeapon w : mineByLvDesc(MINE_WEAPONS)) { // [2026-11-20] "장비 레벨순 정렬 -- 고레벨 장비가 맨 위"(예전: 만든 순서라 심판자 다음 바다 장비)
            int wg = mineWeaponGrade(items, w);
            if (wg < 0) continue;
            int we = mineWeaponEnh(items); // [2026-11-06] 무기 칸 강화 -- 어느 무기를 들어도 그대로
            HashMap<String, Object> nr = mineEquipRow("weapon", w.code, w.name, w.effect(wg, we) + " · Lv." + w.lv + "부터", w == won);
            nr.put("grade", wg);
            List<Integer> gl = new ArrayList<>(); // [2026-10-30] 가진 등급(색 칩)
            int wm = mineWeaponMask(items, w);
            for (int gi = 0; gi <= w.maxGrade; gi++) if ((wm & (1 << gi)) != 0) gl.add(gi);
            nr.put("grades", gl);
            out.add(nr);
        }
        MineWeapon aon = mineArmorOn(items); // [2026-11-06] 갑옷 칸
        for (MineWeapon a : mineByLvDesc(MINE_ARMORS)) { // [2026-11-20] 레벨 높은 갑옷부터
            int ag = mineWeaponGrade(items, a);
            if (ag < 0) continue;
            int ae = mineArmorEnh(items); // [2026-11-18] 갑옷 칸 강화 -- 어느 갑옷을 입어도 그대로
            HashMap<String, Object> ar = mineEquipRow("armor", a.code, a.name, a.effect(ag, ae) + " · Lv." + a.lv + "부터", a == aon);
            ar.put("grade", ag);
            List<Integer> agl = new ArrayList<>();
            int am = mineWeaponMask(items, a);
            for (int gi = 0; gi <= a.maxGrade; gi++) if ((am & (1 << gi)) != 0) agl.add(gi);
            ar.put("grades", agl);
            out.add(ar);
        }
        if (items.getOrDefault(MINE_ITEM_PARASOL, 0) > 0) out.add(mineEquipRow("weapon", MINE_ITEM_PARASOL, "양산", "공격력 +" + MINE_PARASOL_ATK_FLAT + " · 다도해 햇빛 -" + MINE_PARASOL_CUT + "단계", pon));
        int ct = mineCoatTier(items); // [2026-10-28] 외투는 하나(단계)
        if (ct > 0) out.add(mineEquipRow("armor", "COAT", MINE_COAT_NAMES[ct - 1] + " " + ct + "단계", mineCoatEffect(ct), mineCoatDef(items) > 0)); // [2026-11-06] 갑옷을 입고 있으면 외투는 안 입은 것으로(한 칸)
        if (false) out.add(mineEquipRow("coat", MINE_ITEM_COAT2, "두꺼운 방한 외투", "한기 방어 2 · 받는 피해 2배", coat == 2));
        int gt = mineGloveTier(items);
        if (gt > 0) out.add(mineEquipRow("gloves", MINE_ITEM_GLOVES, MINE_GLOVE_NAMES[gt - 1] + " " + gt + "단계", mineGloveEffect(gt), mineGlovesOn(items)));
        String gw = mineGloveCode(items); // [2026-10-28] 재생/광전사의 장갑
        if (items.getOrDefault(MINE_ITEM_BH_ORB, 0) > 0) out.add(mineEquipRow("gloves", MINE_ITEM_BH_ORB, "베히모스의 수정구슬", "공격이 맞으면 " + MINE_BH_ORB_PCT + "% 확률로 공중 물대포(물 속성 고정 " + String.format("%,d", MINE_BH_ORB_DMG) + ")", MINE_ITEM_BH_ORB.equals(gw))); // [2026-11-23]
        for (MineGear g : MINE_GEAR) if ("gloves".equals(g.kind) && items.getOrDefault(g.code, 0) > 0) out.add(mineEquipRow("gloves", g.code, g.name + (items.getOrDefault(g.code, 0) >= 2 ? " (" + items.get(g.code) + "단계)" : ""), g.effect[Math.min(g.effect.length, items.getOrDefault(g.code, 1)) - 1], g.code.equals(gw))); // [2026-11-04] 단계 효과
        // [2026-10-28] "장비창에서 곡괭이도 선택" -- 가진 특수 곡괭이(광역/탐지/쌍날), 끼기/빼기는 반지와 같은 /api/tower-mine-ring
        int pick = items.getOrDefault(MINE_ITEM_PICK_SLOT, 0);
        boolean special = false; // [2026-10-28] 채집 도구 칸 -- 기본 곡괭이도 한 줄(특수 곡괭이를 안 쓰면 기본)
        for (MineGear g : MINE_GEAR) if ("tool".equals(g.kind) && items.getOrDefault(g.code, 0) > 0 && pick == minePickSlotOf(g.code)) special = true;
        out.add(mineEquipRow("tool", "BASIC_PICK", "기본 곡괭이", "한 칸씩 캔다", !special));
        for (MineGear g : MINE_GEAR) if ("tool".equals(g.kind) && items.getOrDefault(g.code, 0) > 0) {
            int t = Math.min(items.getOrDefault(g.code, 0), g.cost.length);
            boolean dim = MINE_ITEM_DETECT_PICK.equals(g.code) && mineDetectDim(items);
            out.add(mineEquipRow("tool", g.code, g.name, (dim ? "🌑 빛을 잃음(" + mineDetectLeft(items) + " 뒤 회복) · " : "") + g.effect[t - 1], pick == minePickSlotOf(g.code)));
        }
        return out;
    }

    static HashMap<String, Object> mineEquipRow(String slot, String code, String name, String effect, boolean worn) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("slot", slot);
        m.put("code", code);
        m.put("name", name);
        m.put("effect", effect);
        m.put("worn", worn);
        return m;
    }

    void mineGearPayload(String userName, Map<String, Integer> items, HashMap<String, Object> out) {
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
            if (MINE_ITEM_DETECT_PICK.equals(g.code)) { m.put("dim", mineDetectDim(items)); if (mineDetectDim(items)) m.put("dimLeft", mineDetectLeft(items)); } // 빛을 잃음([2026-10-29] 12시간 뒤 회복)
            m.put("worn", "ring".equals(g.kind) ? (slot == 1 && MINE_ITEM_PLENTY_RING.equals(g.code)) || (slot == 2 && MINE_ITEM_ABYSS_RING.equals(g.code))
                    : "gloves".equals(g.kind) ? g.code.equals(mineGloveCode(items)) : "necklace".equals(g.kind) ? g.code.equals(mineNeckCode(items)) : tier > 0); // [2026-10-28] 장갑, 목걸이
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
        out.put("equip", mineEquipList(items)); // [2026-10-28] 🎒 장비 창(반지 하나, 옷 하나, 장갑 하나)
        out.put("armorRegen", mineArmorRegen(items)); // [2026-11-06] 화면 체력도 같이 오르게
        HashMap<String, Object> wenh = new HashMap<>(); // [2026-11-06] "강화는 슬롯에" -- 무기 칸 강화(장비 칸 팝업)
        int wl = mineWeaponEnh(items);
        wenh.put("lv", wl);
        MineWeapon wo = mineWeaponOn(items);
        if (wo != null) {
            int g = mineWeaponGrade(items, wo);
            wenh.put("name", wo.name);
            wenh.put("grade", g);
            wenh.put("effect", wo.effect(g, wl));
            if (wl < MINE_ENH_MAX) {
                String[][] ec = mineEnhCost(wo, wl + 1);
                wenh.put("pct", mineEnhPct(wl + 1));
                wenh.put("nextEffect", wo.effect(g, wl + 1));
                wenh.put("costs", mineCostList(ec, have));
                wenh.put("can", mineCostOk(ec, have));
            }
        }
        out.put("wenh", wenh);
        HashMap<String, Object> aenh = new HashMap<>(); // [2026-11-18] 갑옷 칸 강화(요새 도시 방어구 강화사 / 장비 칸 팝업)
        int al = mineArmorEnh(items);
        aenh.put("lv", al);
        MineWeapon ao = mineArmorOn(items);
        if (ao != null) {
            int g = mineWeaponGrade(items, ao);
            aenh.put("name", ao.name);
            aenh.put("grade", g);
            aenh.put("effect", ao.effect(g, al));
            if (al < MINE_ENH_MAX) {
                String[][] ec = mineArmorEnhCost(al + 1); // [2026-11-19]
                aenh.put("pct", mineEnhPct(al + 1));
                aenh.put("nextEffect", ao.effect(g, al + 1));
                aenh.put("costs", mineCostList(ec, have));
                aenh.put("can", mineCostOk(ec, have));
            }
        }
        out.put("aenh", aenh);
        out.put("gloveCode", mineGloveCode(items)); // [2026-10-28] 광전사의 장갑이면 화면도 체력을 안 올린다
        out.put("look", mineLookOf(items)); // [2026-10-28] 무기를 바꿔 들면 그림도
        List<HashMap<String, Object>> shop = new ArrayList<>(); // [2026-10-28] 다도해 상인
        HashMap<String, Object> ps = new HashMap<>();
        ps.put("code", MINE_ITEM_PARASOL);
        ps.put("name", "양산");
        ps.put("effect", "들고 있으면 공격력 +" + MINE_PARASOL_ATK_FLAT + " · 다도해 햇빛 피해 -" + MINE_PARASOL_CUT + "단계");
        ps.put("owned", items.getOrDefault(MINE_ITEM_PARASOL, 0) > 0);
        Map<String, Integer> shave = mineOreHave(userName);
        ps.put("costs", mineCostList(MINE_PARASOL_COST, shave));
        ps.put("can", mineCostOk(MINE_PARASOL_COST, shave));
        shop.add(ps);
        out.put("archiShop", shop);
        if (mineArchiOpen(userName)) { // [2026-10-28] 다도해 -- 신궁 수색 임무 / 1인용 배 / 달빛석·비급서
            HashMap<String, Object> ar = new HashMap<>();
            ar.put("quest", items.getOrDefault(MINE_ITEM_NINJA_QUEST, 0));
            ar.put("kills", items.getOrDefault(MINE_ITEM_NINJA_KILLS, 0));
            ar.put("need", MINE_NINJA_NEED);
            ar.put("key", items.getOrDefault(S5MineMap.SHRINE_KEY_ITEM, 0) > 0); // [2026-10-28] 신궁 열쇠(수색 임무 보상)
            ar.put("bossReward", items.getOrDefault(MINE_ITEM_SHRINE_BOSS_REWARD, 0) > 0); // 닌자 두목 첫 처치 보상을 받았는지
            ar.put("boat", items.getOrDefault(S5MineMap.BOAT_ITEM, 0) > 0);
            long[] bhp = mineBoatHpU(userName, items); // [2026-11-18] 배 체력(아직 안 쓴 피해까지)
            ar.put("boatHp", bhp[0]);
            ar.put("boatHpMax", bhp[1]);
            ar.put("boatFix", bhp[0] < bhp[1] ? mineBoatFixPearls(bhp) : 0); // 수리에 드는 진주(예전 화면 -- 지금은 고칠 게 있는지만)
            ar.put("boatMats", mineBoatMats(have, bhp)); // [2026-11-19] 재료 골라 수리(나무 5% / 바다 보석 20%)
            ar.put("moon", have.getOrDefault(S5MineMap.MOON_STONE_CODE, 0));
            ar.put("books", have.getOrDefault(S5MineMap.NINJA_BOOK_CODE, 0));
            ar.put("boatCosts", mineCostList(MINE_BOAT_COST, have));
            ar.put("boatCan", mineCostOk(MINE_BOAT_COST, have));
            out.put("archi", ar);
        }
        out.put("stepMs", mineStepMs(items));
        out.put("opt", new int[] { items.getOrDefault(MINE_ITEM_OPT_HD, 0), items.getOrDefault(MINE_ITEM_OPT_DMG, 0) }); // [2026-11-21] 화면 옵션(0 = 안 정함, 1 = 켬, 2 = 끔): 64x64 / 데미지 합산
        out.put("waterWalk", mineWaterWalk(items));
        HashMap<String, Object> dragon = new HashMap<>();
        int quest = items.getOrDefault(MINE_ITEM_DRAGON_QUEST, 0);
        dragon.put("quest", quest);
        dragon.put("has", mineHasDragonRing(items));
        dragon.put("gold", have.getOrDefault(S5MineMap.ORE_CODES[3], 0));
        dragon.put("goldNeed", MINE_CAPTAIN_GOLD);
        if (quest >= 1 && !mineHasDragonRing(items)) {
            dragon.put("story", MINE_CAPTAIN_STORY);
            dragon.put("costs", mineCostList(MINE_DRAGON_RING_COST, have));
            dragon.put("can", mineCostOk(MINE_DRAGON_RING_COST, have));
        }
        out.put("dragon", dragon);
        int sevenMax = Integer.MAX_VALUE;
        for (String c : S5MineMap.DAY_STONE_CODES) sevenMax = Math.min(sevenMax, have.getOrDefault(c, 0) / MINE_SEVEN_DAY_STONES);
        sevenMax = Math.min(sevenMax, have.getOrDefault(S5MineMap.ORE_CODES[5], 0)); // [2026-10-25] 칠요석 하나에 별빛석 1개
        out.put("sevenMax", sevenMax); // 지금 칠요석을 몇 개 합성할 수 있는지
        out.put("galaxyMax", Math.min(have.getOrDefault(S5MineMap.MOON_STONE_CODE, 0), have.getOrDefault(S5MineMap.SEVEN_STONE_CODE, 0))); // [2026-11-06] 은하수 조각
        out.put("sevenDayNeed", MINE_SEVEN_DAY_STONES); // [2026-10-28] 칠요석 하나에 요일석 종류마다 몇 개
        out.put("deliverDone", items.getOrDefault(MINE_ITEM_DELIVER_DAY, 0) == mineToday()); // [2026-10-28] 오늘 구리 납품 했는지
        mineLookPayload(items, have, out, isMineAdminName(userName)); // [2026-10-24] 명품상점(외형) -- [2026-11-23] 운영자 신발을 신으려면 이름만으로
        out.put("ores", mineOreList(userName));
    }

    HashMap<String, Object> mineGearResult(String userName, boolean ok, String message) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", ok);
        out.put("message", message);
        Map<String, Integer> gi = mineItems(userName);
        mineGearPayload(userName, gi, out); // ores도 여기서(mineGearPayload)
        out.put("whirl", mineWhirlInfo(gi)); // [2026-11-23] 진주목걸이를 끼고 빼면 회오리도 바로
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
        if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "재료가 부족해요. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")");
        mineTakeCost(userName, cost, 1);
        dao.upsertMineItem(userName, g.code, tier + 1);
        String wear = "";
        if ("tool".equals(g.kind)) { dao.upsertMineItem(userName, MINE_ITEM_PICK_SLOT, minePickSlotOf(g.code)); wear = " 바로 장착했어요."; }
        if ("necklace".equals(g.kind) && tier == 0) { // [2026-10-28] 목걸이는 하나 -- 처음 만들면 그걸 건다
            dao.upsertMineItem(userName, MINE_ITEM_NECK_SLOT, mineNeckSlotOf(g.code));
            dao.upsertMineItem(userName, MINE_ITEM_NECK_OFF, 0);
            wear = " 바로 장착했어요.";
        }
        if ("gloves".equals(g.kind)) { // [2026-10-28] 재생/광전사의 장갑 -- 만들면 바로 낀다(방한 장갑은 벗음)
            dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, mineGloveSlotOf(g.code));
            if (items.getOrDefault(MINE_ITEM_GLOVES, 0) > 0) dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, 1);
            wear = " 바로 장착했어요.";
        }
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
            return mineGearResult(userName, true, "⛏️ " + (on ? g.name + "을(를) 장착했어요. " + g.effect[0] : "기본 곡괭이로 바꿨어요."));
        }
        // [2026-10-28] 장비 칸(반지 하나, 옷 하나, 장갑 하나) -- 🎒 장비 창. 같은 걸 다시 누르면 뺀다.
        Map<String, Integer> it = mineItems(userName);
        if (MINE_ITEM_BH_ORB.equals(code) || MINE_ITEM_BH_PEARL.equals(code)) { // [2026-11-23] 베히모스 장비(장갑/목걸이) -- 같은 걸 다시 누르면 뺀다
            boolean glove = MINE_ITEM_BH_ORB.equals(code);
            String nm = glove ? "베히모스의 수정구슬" : "베히모스의 진주목걸이";
            if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, nm + "이(가) 없어요. 하늘고래 베히모스에게서 아주 가끔 나와요.");
            boolean on = !code.equals(glove ? mineGloveCode(it) : mineNeckCode(it));
            if (glove) {
                dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, on ? 7 : 0);
                if (on && it.getOrDefault(MINE_ITEM_GLOVES, 0) > 0) dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, 1);
            } else {
                if (on) dao.upsertMineItem(userName, MINE_ITEM_NECK_SLOT, 7);
                dao.upsertMineItem(userName, MINE_ITEM_NECK_OFF, on ? 0 : 1);
            }
            return mineGearResult(userName, true, (glove ? "🧤 " : "📿 ") + nm + (on ? "을(를) 장착했어요." : "을(를) 해제했어요."));
        }
        if (MINE_ITEM_REGEN_GLOVES.equals(code) || MINE_ITEM_BERSERK_GLOVES.equals(code) || MINE_ITEM_TWIN_GLOVES.equals(code) || MINE_ITEM_ULT_DMG_GLOVES.equals(code) || MINE_ITEM_ULT_CD_GLOVES.equals(code)) { // [2026-10-28] 장갑 칸은 하나 -- 끼면 방한 장갑은 벗는다
            MineGear gg = mineGear(code);
            if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, gg.name + "이(가) 없어요. 왕도 대장장이에게서 만들 수 있어요.");
            boolean on = !code.equals(mineGloveCode(it));
            dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, on ? mineGloveSlotOf(code) : 0);
            if (on && it.getOrDefault(MINE_ITEM_GLOVES, 0) > 0) dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, 1);
            if (!on) MINE_HEAL.remove(userName);
            return mineGearResult(userName, true, "🧤 " + gg.name + (on ? "을(를) 장착했어요. " + gg.effect[Math.min(gg.effect.length, it.getOrDefault(code, 1)) - 1] : "을(를) 해제했어요."));
        }
        if (MINE_ITEM_GLOVES.equals(code)) {
            if (it.getOrDefault(MINE_ITEM_GLOVES, 0) < 1) return mineGearResult(userName, false, "방한 장갑이 없어요. 설산 마을 털옷 장인의 의뢰로 받을 수 있어요.");
            boolean on = !mineGlovesOn(it);
            dao.upsertMineItem(userName, MINE_ITEM_GLOVES_OFF, on ? 0 : 1);
            if (on) dao.upsertMineItem(userName, MINE_ITEM_GLOVE_SLOT, 0); // [2026-10-28] 다른 장갑은 벗는다
            int gtn = mineGloveTier(it);
            return mineGearResult(userName, true, on ? "🧤 " + MINE_GLOVE_NAMES[gtn - 1] + "을(를) 장착했어요. " + mineGloveEffect(gtn) + " (사막·남쪽 섬에선 더워요!)" : "🧤 " + MINE_GLOVE_NAMES[gtn - 1] + "을(를) 해제했어요.");
        }
        if (MINE_ITEM_SAND_NECKLACE.equals(code) || MINE_ITEM_MIND_NECKLACE.equals(code) || MINE_ITEM_ASSASSIN_NECKLACE.equals(code) || MINE_ITEM_HUNT_NECKLACE.equals(code) || MINE_ITEM_MERC_DMG_NECK.equals(code) || MINE_ITEM_MERC_CD_NECK.equals(code)) { // [2026-11-23] 용병 목걸이 둘 // [2026-10-28] 목걸이는 하나 -- 걸린 걸 다시 누르면 풀기
            MineGear ng = mineGear(code);
            if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, ng.name + "이(가) 없어요. 대장장이에게서 만들 수 있어요.");
            boolean on = !code.equals(mineNeckCode(it));
            if (on) dao.upsertMineItem(userName, MINE_ITEM_NECK_SLOT, mineNeckSlotOf(code));
            dao.upsertMineItem(userName, MINE_ITEM_NECK_OFF, on ? 0 : 1);
            return mineGearResult(userName, true, "📿 " + ng.name + (on ? "을(를) 장착했어요." : "을(를) 해제했어요."));
        }
        if (MINE_ITEM_SWIFT_BOOTS.equals(code)) { // [2026-10-28] 신발 -- 신기/벗기
            boolean neck = false;
            if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, "신속의 장화가 없어요. 대장장이에게서 만들 수 있어요.");
            String flag = neck ? MINE_ITEM_NECK_OFF : MINE_ITEM_BOOTS_OFF;
            boolean on = it.getOrDefault(flag, 0) == 1;
            dao.upsertMineItem(userName, flag, on ? 0 : 1);
            return mineGearResult(userName, true, (neck ? "📿 사막의 목걸이를 " : "👢 신속의 장화를 ") + (on ? "장착했어요." : "해제했어요."));
        }
        if ("BASIC_PICK".equals(code)) { // [2026-10-28] 채집 도구 칸 -- 기본 곡괭이로
            dao.upsertMineItem(userName, MINE_ITEM_PICK_SLOT, 0);
            return mineGearResult(userName, true, "⛏️ 기본 곡괭이로 바꿨어요.");
        }
        int pickGrade = -1; // [2026-10-30] 등급 칩 -- "NINJA_SWORD#3" = 그 무기의 4번째 등급을 골라 든다
        if (code != null && code.indexOf('#') > 0) {
            try { pickGrade = Integer.parseInt(code.substring(code.indexOf('#') + 1)); } catch (NumberFormatException ex) { pickGrade = -2; }
            code = code.substring(0, code.indexOf('#'));
        }
        MineWeapon ea = mineArmorOf(code); // [2026-11-06] 갑옷 -- 등급 칩(#g)이면 그 등급으로 입기, 아니면 입기/벗기
        if (ea != null) {
            if (mineWeaponGrade(it, ea) < 0) return mineGearResult(userName, false, ea.name + "이(가) 없어요. " + ea.lv + "레벨 근처 몬스터가 아주 드물게 떨어뜨려요.");
            if (pickGrade != -1 && (pickGrade < 0 || pickGrade > ea.maxGrade || (mineWeaponMask(it, ea) & (1 << pickGrade)) == 0)) return mineGearResult(userName, false, "그 등급의 " + ea.name + "은(는) 없어요.");
            boolean on = pickGrade != -1 || mineArmorOn(it) != ea;
            if (on) {
                HashMap<String, Object> ar = dao.selectMineUser(userName);
                int alv = ar == null ? 1 : mineLv(ar);
                if (!mineGearLvOk(alv, it, ea.lv)) return mineGearResult(userName, false, "🛡️ " + ea.name + "은(는) Lv." + ea.lv + "부터 입을 수 있어요. (지금 Lv." + alv + ")");
                if (pickGrade >= 0) dao.upsertMineItem(userName, ea.code, pickGrade + 1);
            }
            dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, on ? ea.slot : 0);
            if (on) { dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, 0); } // [2026-11-06] "갑옷과 옷은 한 칸" -- 외투는 벗는다, 용병이 입던 같은 갑옷은 내가
            int ag = pickGrade >= 0 ? pickGrade : mineWeaponGrade(it, ea);
            return mineGearResult(userName, true, on ? "🛡️ " + ea.name + "을(를) 장착했어요. " + ea.effect(ag, mineArmorEnh(it)) : "🛡️ " + ea.name + "을(를) 해제했어요.");
        }
        MineWeapon ew = mineWeaponOf(code);
        if (ew != null && pickGrade != -1) {
            if (pickGrade < 0 || pickGrade > ew.maxGrade || (mineWeaponMask(it, ew) & (1 << pickGrade)) == 0) return mineGearResult(userName, false, "그 등급의 " + ew.name + "은(는) 없어요.");
            HashMap<String, Object> wr = dao.selectMineUser(userName);
            int wlv = wr == null ? 1 : mineLv(wr);
            if (!mineGearLvOk(wlv, it, ew.lv)) return mineGearResult(userName, false, "🗡️ " + ew.name + "은(는) Lv." + ew.lv + "부터 들 수 있어요. (지금 Lv." + wlv + ")");
            dao.upsertMineItem(userName, ew.code, pickGrade + 1);
            dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, ew.slot);
            return mineGearResult(userName, true, "🗡️ " + ew.name + "을(를) 장착했어요. " + ew.effect(pickGrade));
        }
        if (ew != null) { // [2026-10-29] 드랍 무기 -- 든 채로 다시 누르면 칼로
            if (mineWeaponGrade(it, ew) < 0) return mineGearResult(userName, false, ew.name + "이(가) 없어요. " + ew.lv + "레벨 근처 몬스터가 아주 드물게 떨어뜨려요.");
            boolean on = mineWeaponOn(it) != ew;
            if (on) { // [2026-10-29] "무기 장착 레벨 제한" -- 무기 레벨(20/40/60/80) 이상이어야 든다
                HashMap<String, Object> wr = dao.selectMineUser(userName);
                int wlv = wr == null ? 1 : mineLv(wr);
                if (!mineGearLvOk(wlv, it, ew.lv)) return mineGearResult(userName, false, "🗡️ " + ew.name + "은(는) Lv." + ew.lv + "부터 들 수 있어요. (지금 Lv." + wlv + ")");
            }
            dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, on ? ew.slot : 0);
            return mineGearResult(userName, true, on ? "🗡️ " + ew.name + "을(를) 장착했어요. " + ew.effect(mineWeaponGrade(it, ew)) : "🗡️ 칼을 장착했어요.");
        }
        if ("SWORD".equals(code) || MINE_ITEM_PARASOL.equals(code)) { // [2026-10-28] 무기 칸
            boolean pa = MINE_ITEM_PARASOL.equals(code);
            if (pa && it.getOrDefault(MINE_ITEM_PARASOL, 0) < 1) return mineGearResult(userName, false, "양산이 없어요. 다도해 마을 다도해 상인에게서 살 수 있어요.");
            pa = pa && !mineParasolOn(it); // 양산을 든 채로 다시 누르면 칼로
            dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, pa ? 1 : 0);
            return mineGearResult(userName, true, pa ? "⛱️ 양산을 장착했어요. 공격력 +" + MINE_PARASOL_ATK_FLAT + ", 다도해 햇빛 피해 -" + MINE_PARASOL_CUT + "단계" : "🗡️ 칼을 장착했어요.");
        }
        if ("COAT".equals(code) || MINE_ITEM_COAT1.equals(code) || MINE_ITEM_COAT2.equals(code)) { // [2026-10-28] 외투는 하나(단계)
            int cs = mineCoatTier(it);
            if (cs < 1) return mineGearResult(userName, false, "방한 외투가 없어요. 설산 마을 털옷 장인의 의뢰로 받을 수 있어요.");
            boolean on = mineCoatDef(it) <= 0;
            dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, on ? 1 : 0);
            if (on) dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); // [2026-11-06] "갑옷과 옷은 한 칸" -- 갑옷은 벗는다
            return mineGearResult(userName, true, on ? "🧥 " + MINE_COAT_NAMES[cs - 1] + "을(를) 장착했어요. " + mineCoatEffect(cs) : "🧥 " + MINE_COAT_NAMES[cs - 1] + "을(를) 해제했어요.");
        }
        boolean dragon = MINE_ITEM_DRAGON_RING.equals(code), wyrm = MINE_ITEM_WYRM_RING.equals(code); // [2026-11-15] 용의 반지
        if (!dragon && !wyrm && (g == null || !"ring".equals(g.kind))) return mineGearResult(userName, false, "낄 수 없는 물건이에요.");
        String nm = dragon ? "용왕의 반지" : wyrm ? "용의 반지" : g.name;
        if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, nm + "이(가) 없어요. " + (dragon ? "바닷가 늙은 선장에게 받을 수 있어요." : wyrm ? "월드보스 " + MINE_WB_NAME + "이(가) 드물게 떨어뜨려요." : "먼저 만들어 주세요."));
        int want = dragon ? MINE_RING_DRAGON : wyrm ? MINE_RING_WYRM : MINE_ITEM_PLENTY_RING.equals(code) ? 1 : 2, cur = it.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        int next = cur == want ? 0 : want;
        if (mineWaterWalk(it) && next != MINE_RING_DRAGON) { // 물 위에 서 있으면 용왕의 반지를 빼면 빠진다
            HashMap<String, Object> row = dao.selectMineUser(userName);
            S5MineMap mp = row == null ? null : mineMapOf(row);
            int[] pos = mp == null ? null : minePos(row, mp, 1);
            if (pos != null && mp.inside(pos[0], pos[1]) && mp.terrainAt(pos[0], pos[1]) == '~')
                return mineGearResult(userName, false, "🌊 물 위에 서 있어서 용왕의 반지를 뺄 수 없어요. 땅으로 나간 뒤에 바꿔 끼세요.");
        }
        dao.upsertMineItem(userName, MINE_ITEM_RING_SLOT, next);
        if (!isMineAdmin(userName) && (wyrm || cur == MINE_RING_WYRM)) { // [2026-11-20] "용의 반지 사용자가 안 된다" -- 끼는 순간엔 이동 목록(ringMaps)이 화면에 안 가서 다시 들어오기 전까지 지도를 눌러도 아무 일이 없었다
            HashMap<String, Object> ro = mineGearResult(userName, true, next == 0 ? "💍 " + nm + "을(를) 해제했어요." : "💍 " + nm + "을(를) 장착했어요. 전체 지도에서 가고 싶은 지역을 누르면 그곳으로 가요(한 시간에 한 번).");
            if (next == MINE_RING_WYRM) { HashMap<String, Object> s2 = mineState(userName); ro.put("ringMaps", s2.get("ringMaps")); ro.put("ringReadyAt", s2.get("ringReadyAt")); }
            else ro.put("ringOff", true);
            return ro;
        }
        return mineGearResult(userName, true, next == 0 ? "💍 " + nm + "을(를) 해제했어요." : "💍 " + nm + "을(를) 장착했어요." + (dragon ? " 물 위를 걸을 수 있어요." : wyrm ? " 전체 지도(🗺️)에서 가고 싶은 곳을 누르면 그곳으로 가요. (한 시간에 한 번)" : "") + (cur != 0 && cur != next ? " (반지는 하나만 -- 끼던 반지는 뺐어요)" : ""));
    }

    // [2026-10-28] "구리 광석 사용처를 늘려줘 -- 초보자들이 바로 상위 구역으로 가서 구리 채광을 잘 안 한다":
    //  대장장이 [합성]에 구리 정련(구리 MINE_REFINE_COPPER개 -> 철광석 1개, 여러 번)과
    //  하루 한 번 구리 납품(구리 MINE_DELIVER_COPPER개 -> 오늘 요일석 MINE_DELIVER_STONES개 + 곡괭이 내구도 +MINE_DELIVER_DUR, 최대치까지).
    static final int MINE_REFINE_COPPER = 20;
    static final int MINE_DELIVER_COPPER = 100;
    static final int MINE_DELIVER_STONES = 3;
    static final int MINE_DELIVER_DUR = 50;
    static final String MINE_ITEM_DELIVER_DAY = "DELIVER_DAY";

    static int mineToday() {
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

    // [2026-10-28] "광산 진입 시 다운스케일링과 신규 맵이 추가되었다고 간략한 공지 한 번" -- 유저마다 한 번(NOTICE_SEEN < 소식 번호면 mineState가 내려 준다).
    //  같은 날 "공지가 너무 길다, 수정하려면 소스를 건드려야 하나" -- 글과 번호를 TBOT_S5_NOTICE의 NOTICE_ID 2 행(글 NCLOB -- 이모지/한글 안전,
    //  APP_VERSION = 번호)에 두고 관리자 채팅 /광산공지 [내용]으로 바꾼다(번호가 올라가 모두에게 한 번씩 다시 뜸, " / "는 줄바꿈, "끄기" = 안 띄움).
    //  행이 없으면 아래 기본 글(1번).
    static final String MINE_ITEM_NOTICE = "NOTICE_SEEN";
    static final String MINE_NOTICE_DEFAULT = "⚖️ 체력·공격력·경험치 숫자가 작아졌어요(레벨·밸런스는 그대로)\n🏔️ 새 지역: 설산 → 설산 마을 → 왕도 북쪽 샛길 → 왕도, 성당 옛터";
    static volatile Object[] mineNoticeCache = null; // {번호, 글, 읽은 시각}

    /** 지금 광산 소식 {번호, 글}(1분 기억). 번호 0 또는 글이 비면 안 띄운다. */
    Object[] mineNotice() {
        Object[] c = mineNoticeCache;
        if (c != null && System.currentTimeMillis() - (Long) c[2] < 60_000L) return c;
        int id = 1;
        String text = MINE_NOTICE_DEFAULT;
        try {
            HashMap<String, Object> r = dao.selectMineNotice();
            if (r != null) {
                id = Integer.parseInt(strVal(r.get("APP_VERSION"), "1").trim());
                text = r.get("NOTICE_TEXT") == null ? "" : String.valueOf(r.get("NOTICE_TEXT"));
            }
        } catch (Exception e) {
            System.out.println("[광산] 소식 읽기 실패: " + e.getMessage());
        }
        c = new Object[] { id, text, System.currentTimeMillis() };
        mineNoticeCache = c;
        return c;
    }

    @Override
    @Transactional
    public String setMineNotice(String userName, String text) {
        if (!isEventAdmin(userName) && !isMineAdmin(userName)) return "권한이 없습니다.";
        Object[] cur = mineNotice();
        if (text == null || text.trim().isEmpty())
            return "사용법: /광산공지 [내용] (\" / \"로 줄바꿈, \"끄기\"면 안 띄움)\n지금 광산 소식(" + cur[0] + "번):\n" + (((String) cur[1]).isEmpty() ? "(없음)" : cur[1]);
        boolean off = "끄기".equals(text.trim());
        int id = (Integer) cur[0] + 1;
        String body = off ? "" : text.trim().replace(" / ", "\n");
        HashMap<String, Object> up = new HashMap<>();
        up.put("version", String.valueOf(id));
        up.put("text", body);
        dao.upsertMineNotice(up);
        mineNoticeCache = null;
        return off ? "📢 광산 소식을 껐어요." : "📢 광산 소식(" + id + "번) 등록 완료! 광산에 들어오는 사람마다 한 번씩 떠요.\n" + body;
    }

    /** 광산 소식 확인 -- 다시 안 띄운다. */
    @Override
    public HashMap<String, Object> mineNoticeAck(String userName, int id) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", mineAccess(userName) && id > 0 && id <= (Integer) mineNotice()[0]);
        if (Boolean.TRUE.equals(out.get("ok"))) dao.upsertMineItem(userName, MINE_ITEM_NOTICE, id);
        return out;
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

    /** [2026-10-28] "칠요석 재료비를 올리고 싶어, 요일석이 30개씩은 있어야" -- 칠요석 하나에 요일석 7종 각각 이만큼(예전 1개). */
    static final int MINE_SEVEN_DAY_STONES = 30;

    /** 대장장이 [합성] -- 요일석 7종(월~일) MINE_SEVEN_DAY_STONES개씩 + 별빛석 1개 -> 칠요석 1개. times = 몇 개(가진 만큼으로 줄인다).
     *  [2026-10-25] "별빛석 필요처 -- 칠요석 만들 때 들어가도록" 요청으로 별빛석 1개 추가. */
    @Override
    @Transactional
    public HashMap<String, Object> mineSynthSeven(String userName, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        Map<String, Integer> have = mineOreHave(userName);
        int n = Math.max(1, times);
        for (String c : S5MineMap.DAY_STONE_CODES) n = Math.min(n, have.getOrDefault(c, 0) / MINE_SEVEN_DAY_STONES);
        n = Math.min(n, have.getOrDefault(S5MineMap.ORE_CODES[5], 0));
        if (n < 1) return mineGearResult(userName, false, "요일석 7종(월요석~일요석) " + MINE_SEVEN_DAY_STONES + "개씩과 별빛석 1개가 있어야 칠요석을 만들 수 있어요.");
        String[][] cost = new String[S5MineMap.DAY_STONE_CODES.length + 1][];
        for (int i = 0; i < S5MineMap.DAY_STONE_CODES.length; i++) cost[i] = new String[] { S5MineMap.DAY_STONE_CODES[i], String.valueOf(MINE_SEVEN_DAY_STONES) };
        cost[S5MineMap.DAY_STONE_CODES.length] = mineStar(1);
        mineTakeCost(userName, cost, n);
        dao.addUserOre(userName, S5MineMap.SEVEN_STONE_CODE, n);
        return mineGearResult(userName, true, "🌈 요일석 7종과 별빛석을 합쳐 칠요석 " + n + "개를 만들었어요! (보유 " + (have.getOrDefault(S5MineMap.SEVEN_STONE_CODE, 0) + n) + "개)");
    }

    /** [2026-11-06] 대장장이 [합성] -- 달빛석 1 + 칠요석 1 -> 은하수 조각 1. times = 몇 개(가진 만큼으로 줄인다). */
    static final String[][] MINE_GALAXY_COST = { { S5MineMap.MOON_STONE_CODE, "1" }, { S5MineMap.SEVEN_STONE_CODE, "1" } };
    @Override
    @Transactional
    public HashMap<String, Object> mineSynthGalaxy(String userName, int times) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        dao.selectMineUserForUpdate(userName);
        Map<String, Integer> have = mineOreHave(userName);
        int n = Math.min(Math.max(1, times), Math.min(have.getOrDefault(S5MineMap.MOON_STONE_CODE, 0), have.getOrDefault(S5MineMap.SEVEN_STONE_CODE, 0)));
        if (n < 1) return mineGearResult(userName, false, "달빛석 1개와 칠요석 1개가 있어야 은하수 조각을 만들 수 있어요.");
        mineTakeCost(userName, MINE_GALAXY_COST, n);
        dao.addUserOre(userName, S5MineMap.GALAXY_CODE, n);
        return mineGearResult(userName, true, "🌌 달빛과 일곱 요일의 빛을 한데 가두어 은하수 조각 " + n + "개를 만들었어요! (보유 " + (have.getOrDefault(S5MineMap.GALAXY_CODE, 0) + n) + "개)");
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
        if (mineHasDragonRing(items)) return mineGearResult(userName, false, "늙은 선장: 허허, 물 위를 걷는 기분은 어떤가? (반지는 🎒 장비에서 바꿔 낄 수 있다네)");
        if ("craft".equals(action)) {
            if (quest < 1) return mineGearResult(userName, false, "늙은 선장: 무슨 반지 말인가? 금광석이나 가져오게.");
            if (!mineCostOk(MINE_DRAGON_RING_COST, mineOreHave(userName))) {
                return mineGearResult(userName, false, "늙은 선장: 재료가 모자라는군. (" + mineCostText(MINE_DRAGON_RING_COST) + " 필요" + mineLackText(MINE_DRAGON_RING_COST, userName) + ")");
            }
            mineTakeCost(userName, MINE_DRAGON_RING_COST, 1);
            dao.upsertMineItem(userName, MINE_ITEM_DRAGON_RING, 1);
            dao.upsertMineItem(userName, MINE_ITEM_DRAGON_QUEST, 2);
            dao.upsertMineItem(userName, MINE_ITEM_RING_SLOT, MINE_RING_DRAGON); // [2026-10-28] 반지 칸에 바로 낀다(반지는 하나만)
            HashMap<String, Object> out = mineGearResult(userName, true, "🌊 늙은 선장: 자, 용왕의 반지일세. 끼고 있으면 물 위를 땅처럼 걸을 수 있을 거야! (반지는 하나만 -- 🎒 장비에서 바꿔 끼기)");
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

    /** [2026-10-28] "왕도에서 마을 저장하면 맵 가운데서 부활" -- 왕도 한가운데 광장(분수 바로 남쪽 칸, 게이트보다 위). 막혀 있으면 가운데에서 가까운 빈 칸. */
    static int[] mineCityPlaza(S5MineMap city) {
        int cx = city.w / 2, cy = city.h / 2;
        for (int d = 5; d < 20; d++) for (int[] t : new int[][] { { cx, cy + d }, { cx + d, cy }, { cx - d, cy }, { cx, cy - d } })
            if (city.walkable(t[0], t[1]) && city.npcAt(t[0], t[1]) == null) return t;
        return new int[] { city.spawnX, city.spawnY };
    }

    /** [2026-11-22] 갑옷 속성 부여 -- 무기와 같은 NPC/재료, 횟수는 맞는 횟수(원소술사 MINE_ENCH_HITS_ELEM번, 성당 신부 MINE_ENCH_HITS번). 갑옷을 입고 있어야 한다. */
    HashMap<String, Object> mineArmorEnchant(String userName, HashMap<String, Object> row, String el, HashMap<String, Object> out) {
        int e;
        String cost, npc = "elementalist";
        if ("none".equals(el)) {
            if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, npc)) { out.put("ok", false); out.put("message", "왕도 원소술사 앞에서만 할 수 있어요."); return out; }
            dao.upsertMineItem(userName, MINE_ITEM_AEL, 0);
            dao.upsertMineItem(userName, MINE_ITEM_AEL_LEFT, 0);
            mineArmorElSet(userName, 0, 0);
            out.put("message", "🔮 원소술사: 갑옷에 깃든 속성을 거두었네.");
            out.put("ench", mineEnchInfo(mineItems(userName)));
            return out;
        }
        switch (el) {
            case "light": e = S5MineMobs.EL_LIGHT; cost = S5MineMap.BAR_CODES[4]; npc = "priest"; break;
            case "water": e = S5MineMobs.EL_WATER; cost = S5MineMap.cutCode(3); break;
            case "thunder": e = S5MineMobs.EL_THUNDER; cost = S5MineMap.cutCode(4); break;
            case "fire": e = S5MineMobs.EL_FIRE; cost = S5MineMap.cutCode(1); break;
            case "earth": e = S5MineMobs.EL_EARTH; cost = S5MineMap.cutCode(0); break;
            default: out.put("ok", false); out.put("message", "그런 속성은 없어요."); return out;
        }
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, npc)) {
            out.put("ok", false);
            out.put("message", ("priest".equals(npc) ? "왕도 성당 신부" : "왕도 원소술사") + " 앞에서만 할 수 있어요.");
            return out;
        }
        if (mineArmorOn(mineItems(userName)) == null) { out.put("ok", false); out.put("message", "갑옷을 입고 와야 속성을 깃들일 수 있어요."); return out; }
        if (dao.takeUserOre(userName, cost, 1) == 0) { out.put("ok", false); out.put("message", mineCodeName(cost) + " 1개가 있어야 해요."); return out; }
        int hits = "priest".equals(npc) ? MINE_ENCH_HITS : MINE_ENCH_HITS_ELEM;
        dao.upsertMineItem(userName, MINE_ITEM_AEL, e);
        dao.upsertMineItem(userName, MINE_ITEM_AEL_LEFT, hits);
        mineArmorElSet(userName, e, hits);
        String nm = S5MineMobs.ELEM_NAMES[e];
        out.put("message", "🛡️ 갑옷에 " + nm + " 속성을 깃들였어요! " + hits + "번 맞는 동안 " + nm + " 속성 공격은 막고, " + mineAelWeak(e) + " 속성 공격엔 1.5배로 아파요. (" + mineCodeName(cost) + " 1개 사용)");
        out.put("ench", mineEnchInfo(mineItems(userName)));
        return out;
    }

    /** [2026-10-29] 무기 속성 부여(MINE_ENCH_HITS번 공격 동안) -- 왕도 성당 신부: 빛(미스릴괴 1, 어둠 x1.5), 원소술사: 물/번개/불/땅
     *  (세공된 아쿠아마린/토파즈/루비/재스퍼 1, 상성 x1.5). 새로 부여하면 이전 속성은 사라진다. */
    HashMap<String, Object> mineEnchant(String userName, HashMap<String, Object> row, String el, HashMap<String, Object> out) {
        int e;
        String cost, npc = "elementalist";
        if ("none".equals(el)) { // [2026-10-29] "무속성으로 되돌리는 기능도 원소술사에" -- 무료
            if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, npc)) { out.put("ok", false); out.put("message", "왕도 원소술사 앞에서만 할 수 있어요."); return out; }
            dao.upsertMineItem(userName, MINE_ITEM_ENCH_EL, 0);
            dao.upsertMineItem(userName, MINE_ITEM_ENCH_LEFT, 0);
            out.put("message", "🔮 원소술사: 무기에 깃든 속성을 거두었네. 이제 무속성이야.");
            out.put("ench", mineEnchInfo(mineItems(userName)));
            return out;
        }
        switch (el) {
            case "light": e = S5MineMobs.EL_LIGHT; cost = S5MineMap.BAR_CODES[4]; npc = "priest"; break;
            case "water": e = S5MineMobs.EL_WATER; cost = S5MineMap.cutCode(3); break;
            case "thunder": e = S5MineMobs.EL_THUNDER; cost = S5MineMap.cutCode(4); break;
            case "fire": e = S5MineMobs.EL_FIRE; cost = S5MineMap.cutCode(1); break;
            case "earth": e = S5MineMobs.EL_EARTH; cost = S5MineMap.cutCode(0); break;
            default: out.put("ok", false); out.put("message", "그런 속성은 없어요."); return out;
        }
        if (!S5MineMap.CITY.equals(strVal(row.get("MAP_ID"), "")) || !mineAtNpc(row, npc)) {
            out.put("ok", false);
            out.put("message", ("priest".equals(npc) ? "왕도 성당 신부" : "왕도 원소술사") + " 앞에서만 할 수 있어요.");
            return out;
        }
        if (dao.takeUserOre(userName, cost, 1) == 0) { out.put("ok", false); out.put("message", mineCodeName(cost) + " 1개가 있어야 해요."); return out; }
        dao.upsertMineItem(userName, MINE_ITEM_ENCH_EL, e);
        int hits = "priest".equals(npc) ? MINE_ENCH_HITS : MINE_ENCH_HITS_ELEM;
        dao.upsertMineItem(userName, MINE_ITEM_ENCH_LEFT, hits);
        out.put("message", "✨ 무기에 " + S5MineMobs.ELEM_NAMES[e] + " 속성을 깃들였어요! " + hits + "번 공격하는 동안 상성 몬스터에게 피해 x1.5 (" + mineCodeName(cost) + " 1개 사용)");
        out.put("ench", mineEnchInfo(mineItems(userName)));
        return out;
    }

    /** 성당 신부 -- action: heal(체력 가득, 무료) / save(쓰러지면 왕도 성당에서 깨어나기) / reset(부활 지점을 평원 마을로) / [2026-10-28] rebirth(환생). */
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
            out.put("message", save ? "🙏 성당 신부: 기도했습니다. 쓰러지면 왕도 한가운데 광장에서 깨어날 거예요." : "🙏 성당 신부: 이제 쓰러지면 중앙 평원 마을에서 깨어날 거예요.");
            return out;
        }
        if (action.startsWith("ench_")) return mineEnchant(userName, row, action.substring(5), out); // [2026-10-29]
        if (action.startsWith("aench_")) { out.put("ok", false); out.put("message", "갑옷 속성 부여는 이제 하지 않아요."); return out; } // [2026-11-22] 갑옷 속성 -- [2026-11-23] 없앰
        int lv = mineLv(row);
        Map<String, Integer> items = mineItems(userName);
        int rb = mineRebirth(items);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        boolean locked = items.getOrDefault(MINE_ITEM_HP_LOCK, 0) > 0;
        if ("rebirth".equals(action)) return minePriestRebirth(userName, row, items, lv, exp, rb, locked, now, out);
        if (locked) dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); // [2026-10-25] 잠긴 최대 체력도 정상으로
        // [2026-11-18] "사제에게 회복 시 장비로 늘어난 최대 체력은 회복이 안 된다" -- 기본 최대 체력(mineHpMax)까지만 채우던 것을 갑옷 체력까지(mineHpFull)
        dao.updateMineCombat(userName, lv, exp, mineHpFull(lv, rb), now, 0);
        out.put("combat", mineCombatInfo(lv, exp, mineHpFull(lv, rb), now, rb));
        out.put("message", "✨ 성당 신부: 여신의 축복을! 체력이 모두 회복됐어요." + (locked ? " 🔓 잠겼던 최대 체력도 풀렸어요." : ""));
        return out;
    }

    /** [2026-10-28] 환생 -- 왕도에서, 99레벨 경험치 가득일 때만. 환생 +1(최대 5), 레벨 1 / 경험치 0, 체력은 새 최대치로 가득, 최대 체력 잠금도 풀린다. */
    HashMap<String, Object> minePriestRebirth(String userName, HashMap<String, Object> row, Map<String, Integer> items, int lv, long exp, int rb,
            boolean locked, long now, HashMap<String, Object> out) {
        out.put("ok", false);
        if (!S5MineMap.CITY.equals(mineMapOf(row).id)) { out.put("message", "🙏 성당 신부: 환생 의식은 왕도 성당에서만 올릴 수 있어요."); return out; }
        if (rb >= MINE_REBIRTH_MAX) { out.put("message", "🙏 성당 신부: 이미 " + mineRbName(MINE_REBIRTH_MAX) + "까지 마쳤어요. 더 오를 곳이 없답니다."); return out; }
        if (!mineRebirthReady(lv, exp, rb)) {
            out.put("message", "🙏 성당 신부: 아직 때가 아니에요. " + (mineLvCap(rb) == MINE_LV_MAX ? "Lv." + MINE_LV_MAX + " 경험치를 가득 채우면" : "Lv." + (MINE_LV_MAX + 1) + " 이상이 되면") + " 원할 때 다시 오세요. (지금 Lv." + lv // [2026-11-18] 5환생 전은 99에서 멈춘다
                    + (lv >= MINE_LV_MAX ? ", 경험치 " + exp + " / " + mineExpNeed(MINE_LV_MAX, rb) : "") + ")");
            return out;
        }
        if (mineEngaged(userName, now)) { out.put("message", "🙏 성당 신부: 싸움이 끝난 뒤에 오세요."); return out; }
        int nrb = rb + 1;
        dao.upsertMineItem(userName, MINE_ITEM_REBIRTH, nrb);
        if (locked) dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0);
        int hp = mineHpFull(1, nrb); // [2026-11-18] 갑옷 체력까지
        dao.updateMineCombat(userName, 1, 0L, hp, now, 0);
        if (mineWeaponOn(items) != null) { dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, 0); items.put(MINE_ITEM_WEAPON_SLOT, 0); } // [2026-10-29] 무기 장착 레벨 -- 환생하면 칼로
        if (mineArmorOn(items) != null) { dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); items.put(MINE_ITEM_ARMOR_SLOT, 0); } // [2026-11-06] 갑옷도 벗는다
        MinePresence p = MINE_PRESENCE.get(userName);
        if (p != null) p.look = null; // 다른 유저 화면에도 바로 날개가 보이게(다음 위치 알림 때 외형을 다시 읽는다)
        items.put(MINE_ITEM_REBIRTH, nrb);
        out.put("ok", true);
        out.put("rebirth", nrb);
        out.put("rebirthDone", true);
        out.put("look", mineLookOf(items));
        out.put("combat", mineCombatInfo(1, 0L, hp, now, false, 0, nrb));
        // [2026-10-28] "환생 시 멘트 조정" -- 지금 효과대로(예전 글은 체력 x1.5^n / 경험치 x3^n이었다)
        out.put("message", "🕊️ 성당 신부: 여신이시여, 이 영혼에게 새 날개를! " + (nrb > MINE_REBIRTH_BASE ? "진환생 " + (nrb - MINE_REBIRTH_BASE) : "환생 " + nrb) + "단계 -- Lv.1(" + mineRbName(nrb) + ")로 다시 태어났어요. "
                + "최대 체력 +" + (nrb * 50) + "% · 공격력 +" + (nrb * 50) + "% · 미스릴 이하 광물 x" + (1 + nrb) + " · 방한·햇빛 저항·동굴 밝기 +" + nrb + " · 필요 경험치 x" + mineRebirthMulText(MINE_RB_EXP_MUL, nrb) + "."
                + (nrb > MINE_REBIRTH_BASE ? " 이제 행운의 주문서는 쓸 수 없어요." : ""));
        return out;
    }

    static String mineRebirthMulText(double base, int rb) {
        double v = Math.pow(base, rb);
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v).replaceAll("0+$", "");
    }

    // ---- [2026-10-24] 광산에서 다른 유저 만나기 -- "접속한 유저끼리 만나게, 캐릭터명은 안 보이게, 근처에 오면 '안녕하세요' 버튼, 하루 1회 요일석 5개" ----
    //  서버 메모리(MINE_PRESENCE)에 유저별 맵/칸/방향/외형/말풍선을 들고, 화면이 1초마다 내 위치를 보내며 같은 맵의 다른 유저(최근 10초)를 받아 간다.
    //  다른 유저는 이름 대신 익명 번호(id)로만 내려준다. 인사 보상은 서버가 저장 위치 기준 4칸 안에 다른 유저가 실제로 있는지 확인한 뒤에만.
    static final class MinePresence {
        final String id;
        volatile String mapId;
        volatile int x, y;
        volatile String dir = "down";
        volatile long at, lookAt, bubbleAt;
        volatile int bubbleT; // [2026-11-22] 고른 인사말(MINE_GREET_LINES 번호)
        volatile HashMap<String, Object> look;
        volatile int merc = -1; // [2026-11-05] 데리고 다니는 용병([2026-11-06] 직업 0 전사 / 1 궁수 / 2 마법사, -1 = 없음 -- 지금은 관리자만)
        volatile int mercEl, mercSt, mercNi; // [2026-11-06] 용병 속성/단계/이름 번호(화면 이펙트·그림)
        volatile int merc2 = -1, mercEl2, mercSt2, mercNi2; // [2026-11-18] 두 번째 용병(직업, -1 = 없음)
        volatile int merc3 = -1, mercEl3, mercSt3, mercNi3; // [2026-11-23] 세 번째 용병
        final java.util.ArrayDeque<HashMap<String, Object>> atks = new java.util.ArrayDeque<>(); // [2026-11-07] 최근 공격(다른 사람 화면용)
        MinePresence(String id) { this.id = id; }
    }
    // [2026-11-07] "다른 사람 공격 모션 보이게, 피해 숫자도 다른 색으로" -- 공격(일반 공격/검기만 쓰기)마다 {s: 번호, d: 방향, h: [[x, y, 피해, 종류]], au: [[속성, 단계]], w: 관리자 폭}을
    //  남겨 두고(MINE_PRES_ATK_MS 동안, 최대 6개), 같은 맵 사람들의 1초 위치 알림에 실어 보낸다. 화면은 번호로 처음 본 것만 재생한다(휘두르기 + 검기 + 피해 숫자).
    //  종류: 0 보통, 1 치명, 10 + 속성 = 검기 속성 피해.
    static final long MINE_PRES_ATK_MS = 1_800L;
    static final java.util.concurrent.atomic.AtomicLong MINE_PRES_ATK_SEQ = new java.util.concurrent.atomic.AtomicLong();
    @SuppressWarnings("unchecked")
    void minePresAtk(String userName, String dir, int[] aura, HashMap<String, Object> out) {
        try {
            if (out == null || !Boolean.TRUE.equals(out.get("ok"))) return;
            MinePresence me = MINE_PRESENCE.get(userName);
            if (me == null) return;
            List<int[]> h = new ArrayList<>();
            Object fx = out.get("mobFx"), fy = out.get("mobFy");
            if (fx instanceof Number && fy instanceof Number) {
                int mx = ((Number) fx).intValue(), my = ((Number) fy).intValue();
                Object hs = out.get("hits");
                if (hs instanceof int[]) { int[] hv = (int[]) hs; boolean[] cs = (boolean[]) out.get("hitCrits"); for (int i = 0; i < hv.length; i++) h.add(new int[] { mx, my, hv[i], cs != null && cs[i] ? 1 : 0 }); }
                else if (out.get("dmg") instanceof Number && ((Number) out.get("dmg")).intValue() > 0) h.add(new int[] { mx, my, ((Number) out.get("dmg")).intValue(), Boolean.TRUE.equals(out.get("crit")) ? 1 : 0 });
                Object am = out.get("aura");
                if (am instanceof List) for (Object o : (List<Object>) am) if (o instanceof int[]) { int[] a = (int[]) o; h.add(new int[] { mx, my, a[1], 10 + a[0] }); }
            }
            Object ams = out.get("auraMobs");
            if (ams instanceof List) for (Object o : (List<Object>) ams) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                Object row = m.get("row"), hl = m.get("hits");
                if (!(row instanceof long[]) || !(hl instanceof List)) continue;
                long[] rw = (long[]) row;
                for (Object q : (List<Object>) hl) if (q instanceof int[]) { int[] a = (int[]) q; h.add(new int[] { (int) rw[3], (int) rw[4], a[1], 10 + a[0] }); }
            }
            List<int[]> au = new ArrayList<>();
            boolean auraFired = out.containsKey("aura") || out.containsKey("auraMobs") || Boolean.TRUE.equals(out.get("auraOnly"));
            if (auraFired && aura != null) for (int e = 1; e < aura.length && e <= 8; e++) if (aura[e] > 0) au.add(new int[] { e, aura[e] });
            if (h.isEmpty() && au.isEmpty() && !(out.get("whirl") instanceof List)) return;
            if (h.size() > 24) h = h.subList(0, 24);
            HashMap<String, Object> a = new HashMap<>();
            long now = System.currentTimeMillis();
            a.put("s", MINE_PRES_ATK_SEQ.incrementAndGet());
            a.put("at", now);
            a.put("d", dir != null && dir.matches("up|down|left|right") ? dir : me.dir);
            a.put("h", h);
            if (!au.isEmpty()) a.put("au", au);
            if (isMineAdmin(userName)) a.put("w", 1);
            if (out.get("whirl") instanceof List) a.put("wl", out.get("whirl")); // [2026-11-07] 돌개베기(켠 속성)
            synchronized (me.atks) {
                me.atks.addLast(a);
                while (me.atks.size() > 6 || (!me.atks.isEmpty() && now - (Long) me.atks.peekFirst().get("at") > MINE_PRES_ATK_MS)) me.atks.removeFirst();
            }
        } catch (Exception ignore) { } // 화면 표시용 -- 공격 결과에 영향 없게
    }
    static final java.util.concurrent.ConcurrentHashMap<String, MinePresence> MINE_PRESENCE = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_PRESENCE_TTL_MS = 10_000L;
    static final long MINE_BUBBLE_MS = 4_000L;
    static final int MINE_GREET_RANGE = 8; // [2026-11-11] "너무 가까워야 인사가 된다" 4 -> 8칸
    static final int MINE_GREET_STONES = 5;
    // [2026-11-22] "안녕하세요 말고 다른 멘트도 -- (...) 누르면 고르게" -- 화면은 번호(t)만 보내고, 말은 서버가 이 목록에서 고른다(아무 글이나 띄우지 못하게)
    static final String[] MINE_GREET_LINES = { "안녕하세요!", "같이 공격해요!", "수고하셨습니다.", "감사합니다." };
    static final String MINE_ITEM_GREET_DAY = "GREET_DAY";
    /** [2026-10-24] 바닷길 하루 보석 한도("바닷길 안에서는 하루 보석 50개까지") -- SEA_DAY = yyyyMMdd, SEA_CNT = 그날 캔 칸 수. */
    static final int MINE_SEA_DAILY = 50;
    static final String MINE_ITEM_SEA_DAY = "SEA_DAY";
    static final String MINE_ITEM_SEA_CNT = "SEA_CNT";

    MinePresence minePresenceOf(String userName) {
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
        if (S5MineMap.DEMON_VILLAGE.equals(mapId)) { // [2026-11-17] 마계 마을 -- 3초가 지나면 요새 도시로
            Long va = MINE_VILLAGE_AT.get(userName);
            if (va == null || now - va >= MINE_VILLAGE_MS) {
                HashMap<String, Object> vr = dao.selectMineUser(userName);
                if (vr != null && S5MineMap.DEMON_VILLAGE.equals(strVal(vr.get("MAP_ID"), ""))) { S5MineMap tc = S5MineMap.get(S5MineMap.TOWER_CITY); dao.updateMinePos(userName, tc.id, tc.spawnX, tc.spawnY, now); mineDisengageAll(userName); }
                MINE_VILLAGE_AT.remove(userName);
                out.put("villageOut", MINE_VILLAGE_MSG);
                return out;
            }
        }
        if (S5MineMap.VAST_SEA.equals(mapId)) { HashMap<String, Object> vo = mineVastTick(userName, now, out); if (vo != null) return vo; } // [2026-11-18] 망망대해
        if (!MINE_BH_KNOCK.isEmpty()) { // [2026-11-23] "물보라를 내뿜어 벽 쪽으로 넉백시켜 왼쪽 맵으로 날려 버리면" -- 베히모스 물보라 분출에 맞으면 성벽 너머 중앙 평원(성곽 서쪽 입구 앞)으로
            Long kn = MINE_BH_KNOCK.remove(userName);
            if (kn != null && now - kn <= MINE_BH_KNOCK_MS && S5MineMap.CASTLE.equals(mapId) && mineWbDownUntil(userName, now) == 0) {
                S5MineMap cm = S5MineMap.get(S5MineMap.CASTLE), fm = S5MineMap.get(S5MineMap.FIELD);
                if (fm != null) {
                    int tx = fm.spawnX, ty = fm.spawnY;
                    if (cm != null) for (S5MineMap.Portal pt : cm.portals) if (S5MineMap.FIELD.equals(pt.target)) { tx = pt.tx; ty = pt.ty; }
                    dao.updateMinePos(userName, fm.id, tx, ty, now);
                    mineDisengageAll(userName);
                    out.put("villageOut", "💦 " + MINE_BH_NAME + "의 물보라에 휩쓸려 성벽 너머 중앙 평원까지 날아갔어요! 동쪽 성곽으로 다시 달려가요.");
                    return out;
                }
            }
        }
        MinePresence me = minePresenceOf(userName);
        if (me.mapId != null && mapId != null && !mapId.equals(me.mapId)) { mineMercXpFlushUser(userName); mineBoatFlush(userName); } // [2026-11-18] 맵을 옮기면 모아 둔 용병 경험치/배 피해를 DB에
        me.mapId = mapId; me.x = x; me.y = y; me.at = now;
        { HashMap<String, Object> un = mineUnityCalc(userName, mapId, now); if (un != null) out.put("unity", un); } // [2026-11-17] 우리는 하나
        { int[] ae = MINE_ARMOR_EL_OF.get(userName); // [2026-11-22] 갑옷 속성 -- 맞은 횟수를 DB에, 무효/상성 횟수를 화면에
          if (ae != null) {
              int pend, imm, weak, ael, left;
              synchronized (ae) { pend = ae[2]; imm = ae[3]; weak = ae[4]; ae[3] = 0; ae[4] = 0; if (pend > 0) { ae[1] = Math.max(0, ae[1] - pend); ae[2] = 0; } ael = ae[0]; left = ae[1]; }
              if (pend > 0) dao.upsertMineItem(userName, MINE_ITEM_AEL_LEFT, left);
              if (ael > 0 || imm > 0 || weak > 0) out.put("ael", new int[] { left > 0 ? ael : 0, left, imm, weak });
          } }
        if (dir != null && dir.matches("up|down|left|right")) me.dir = dir;
        if (me.look == null || now - me.lookAt > 30_000L) { Map<String, Integer> lit = mineItems(userName); me.look = mineLookOf(lit); me.merc = mineMercOn(lit); if (me.merc >= 0) { me.mercEl = mineMercEl(lit, me.merc); me.mercSt = mineMercStage(mineMercLv(lit, me.merc)); me.mercNi = mineMercNi(lit, me.merc, "NM"); } me.merc2 = mineMercOn2(lit) ? mineMercCls(lit, MINE_MERC_K2) : -1; if (me.merc2 >= 0) { me.mercEl2 = mineMercEl(lit, MINE_MERC_K2); me.mercSt2 = mineMercStage(mineMercLv(lit, MINE_MERC_K2)); me.mercNi2 = mineMercNi(lit, MINE_MERC_K2, "NM"); } me.merc3 = mineMercOn3(lit) ? mineMercCls(lit, MINE_MERC_K3) : -1; if (me.merc3 >= 0) { me.mercEl3 = mineMercEl(lit, MINE_MERC_K3); me.mercSt3 = mineMercStage(mineMercLv(lit, MINE_MERC_K3)); me.mercNi3 = mineMercNi(lit, MINE_MERC_K3, "NM"); } me.lookAt = now; } // 외형은 30초마다 다시 읽는다([2026-11-05] 용병 단계도)
        int soloF = S5MineMap.towerFloor(mapId);
        boolean soloMap = soloF > 0 && !S5MineMap.towerTownFloor(soloF); // [2026-11-15] 탑 1인 층 -- 다른 사람은 안 보인다
        for (java.util.Map.Entry<String, MinePresence> e : MINE_PRESENCE.entrySet()) {
            MinePresence o = e.getValue();
            if (soloMap || o == me || now - o.at > MINE_PRESENCE_TTL_MS || !mapId.equals(o.mapId) || o.look == null) continue;
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", o.id);
            m.put("x", o.x);
            m.put("y", o.y);
            m.put("d", o.dir);
            m.put("look", o.look);
            if (minePStealthOf(e.getKey(), now) == null) m.put("n", mineNick(e.getKey())); // [2026-11-05] "플레이어 위에 닉네임"(은신 중엔 숨김)
            if (now - o.bubbleAt < MINE_BUBBLE_MS) m.put("bubble", MINE_GREET_LINES[Math.max(0, Math.min(MINE_GREET_LINES.length - 1, o.bubbleT))]); // [2026-11-22] 고른 인사말
            if (o.merc >= 0 && minePStealthOf(e.getKey(), now) == null) { // [2026-11-05] 용병 -- 단계, 쉬는 중, 싸우는 몬스터 칸
                m.put("mc", o.merc);
                m.put("me", o.mercEl);
                m.put("ms", o.mercSt);
                m.put("ml", o.mercNi);
                MercSt ms = MINE_MERC.get(e.getKey());
                if (ms != null && ms.restUntil > now) m.put("mr", 1);
                else if (ms != null && now - ms.atkAt < 2_500L) m.put("mt", new int[] { ms.tx, ms.ty });
            }
            if (o.merc2 >= 0 && minePStealthOf(e.getKey(), now) == null) { // [2026-11-18] 두 번째 용병 -- m2 = {mc, me, ms, ml, mr, mt}
                HashMap<String, Object> m2 = new HashMap<>();
                m2.put("mc", o.merc2);
                m2.put("me", o.mercEl2);
                m2.put("ms", o.mercSt2);
                m2.put("ml", o.mercNi2);
                MercSt ms2 = MINE_MERC2.get(e.getKey());
                if (ms2 != null && ms2.restUntil > now) m2.put("mr", 1);
                else if (ms2 != null && now - ms2.atkAt < 2_500L) m2.put("mt", new int[] { ms2.tx, ms2.ty });
                m.put("m2", m2);
            }
            if (o.merc3 >= 0 && minePStealthOf(e.getKey(), now) == null) { // [2026-11-23] 세 번째 용병 -- m3 = {mc, me, ms, ml, mr, mt}
                HashMap<String, Object> m3 = new HashMap<>();
                m3.put("mc", o.merc3);
                m3.put("me", o.mercEl3);
                m3.put("ms", o.mercSt3);
                m3.put("ml", o.mercNi3);
                MercSt ms3 = MINE_MERC3.get(e.getKey());
                if (ms3 != null && ms3.restUntil > now) m3.put("mr", 1);
                else if (ms3 != null && now - ms3.atkAt < 2_500L) m3.put("mt", new int[] { ms3.tx, ms3.ty });
                m.put("m3", m3);
            }
            synchronized (o.atks) { // [2026-11-07] 최근 공격(휘두르기/검기/피해 숫자)
                List<HashMap<String, Object>> al = new ArrayList<>();
                for (HashMap<String, Object> a : o.atks) if (now - (Long) a.get("at") <= MINE_PRES_ATK_MS) al.add(a);
                if (!al.isEmpty()) m.put("a", al);
            }
            if (mineFrozen(e.getKey(), now) != null) m.put("frozen", true);
            if (mineKakOn(e.getKey())) m.put("kk", 1); // [2026-11-21] 각성 중(금빛 불꽃)
            if (minePStealthOf(e.getKey(), now) != null) m.put("stealth", true); // [2026-10-30] 자객의 목걸이 은신 -- 반투명 // [2026-10-28] 얼음 여왕에게 얼어 있음(화면이 얼음을 그린다)
            others.add(m);
        }
        // [2026-10-30] "지금 접속 중인 사람 수 항상, 전체 지도를 가장 크게 확대하면 맵별 인원" -- 이름 없이 숫자만
        HashMap<String, Integer> mapN = new HashMap<>();
        int online = 0;
        for (MinePresence o : MINE_PRESENCE.values()) {
            if (now - o.at > MINE_PRESENCE_TTL_MS || o.mapId == null) continue;
            online++;
            mapN.merge(o.mapId, 1, Integer::sum);
        }
        out.put("online", online);
        out.put("mapN", mapN);
        if (S5MineMap.towerFloor(mapId) > 0) { int twl = mineTwLeftOf(userName, now); if (twl >= 0) out.put("twLeft", twl); } // [2026-11-19] 수문장 반복 보상 남은 수
        if (S5MineMap.OPEN_SEA.equals(mapId)) { S5MineMap osm = S5MineMap.get(mapId); if (osm != null) { mineKrkMeet(osm, userName, x, y, now); HashMap<String, Object> sdo = mineSeaDownTick(osm, userName, x, y, now, out); if (sdo != null) return sdo; } /* [2026-11-19] 쓰러져 있는 동안에도 배가 맞는다 */ mineKrakenTick(now); out.put("sea", mineSeaState(now)); } /* [2026-11-18] 화약통/등대/구조선 + 크라켄 이동 */ if (!MINE_ZONES.isEmpty()) { S5MineMap zm = S5MineMap.get(mapId); List<long[]> zl = zm == null ? null : mineZoneList(zm, now); if (zl != null) out.put("zones", zl); } // [2026-11-16] 탑 수문장 / 악마의 장판
        HashMap<String, Object> wbi = mineWbInfo(userName, now); // [2026-11-15] 월드보스 예고/진행
        if (wbi != null) out.put("wb", wbi);
        else if (now - mineWbGoneAt < 6_000L && mineWbOk(userName)) out.put("wbGone", mineWbGoneAt); // [2026-11-16] 거룡이 날아감(퇴장 연출)
        try { // [2026-11-16] "보스 처치 시에도 우수수 떨어지게(탑 보스 포함 모든 보스), 여러 사람이 같이 처치하면 그만큼 더 많이 떨어져 해당 플레이어에게 흡수되는 이펙트" --
            //  이 맵의 보스가 막 쓰러졌으면(마지막으로 맞은 지 5초 안) MINE_BOSS_FX_MS 동안 알린다: 쓰러진 자리, 함께 싸운 사람(위치 알림 번호 -- 화면이 그 사람들에게 나눠 날린다), 내가 그중 하나인지.
            S5MineMap pm = S5MineMap.get(mapId);
            S5MineMap.Mob pb = pm == null ? null : mineBossOf(pm);
            MobState ps = pb == null ? null : MINE_MOB_STATE.get(mineMobKey(pm, pb.id));
            if (ps != null) synchronized (ps) {
                if (ps.deadUntil > now && ps.hp <= 0) {
                    if (ps.fxAt == 0) ps.fxAt = now - ps.lastHitAt < 5_000L ? now : -1;
                    if (ps.fxAt > 0 && now - ps.fxAt < MINE_BOSS_FX_MS) {
                        HashMap<String, Object> fx = new HashMap<>();
                        List<String> ids = new ArrayList<>();
                        boolean mine = false;
                        java.util.Set<String> hs = ps.hitters == null ? new java.util.HashSet<>() : ps.hitters;
                        for (String u : hs) { if (u.equals(userName)) { mine = true; continue; } MinePresence hp2 = MINE_PRESENCE.get(u); if (hp2 != null && mapId.equals(hp2.mapId) && now - hp2.at <= MINE_PRESENCE_TTL_MS && ids.size() < 16) ids.add(hp2.id); }
                        fx.put("at", ps.fxAt); fx.put("x", ps.fx); fx.put("y", ps.fy); fx.put("f", S5MineMobs.TYPES.get(pb.type).foot());
                        fx.put("n", Math.max(1, hs.size())); fx.put("me", mine || hs.isEmpty()); fx.put("us", ids); if (ps.fxG != null) fx.put("g", ps.fxG);
                        out.put("bossFx", fx);
                    }
                } else ps.fxAt = 0;
            }
        } catch (Exception ignore) { }
        if (mineCallRoom(mapId)) { // [2026-11-13] 보스 소환사 -- 보스가 없을 때만 보스 집 칸에(소환 카운트 중이면 나타나는 시각 at)
            MINE_CALL_SEEN.put(mapId, now); // 맵에 누가 있다(소환된 보스가 사라지지 않게)
            S5MineMap cm = S5MineMap.get(mapId);
            S5MineMap.Mob cb = cm == null ? null : mineBossOf(cm);
            int[] sp = cm == null ? null : mineCallSpot(cm);
            if (cb != null && sp != null && !mineBossUp(cb.type, now) && !(S5MineMap.isSqDen(mapId) && mineSqGone(now))) { // [2026-11-23] 처치한 맵 주기엔 다람쥐 자리도 비운다
                HashMap<String, Object> bc = new HashMap<>(); bc.put("x", sp[0]); bc.put("y", sp[1]);
                long pa = mineCallPending(mapId, now);
                if (pa > 0) bc.put("at", pa);
                out.put("bossCall", bc);
            }
        }
        long[] hm = mineHermitNow(now); // [2026-10-30] 은둔한 대장장이
        if (hm != null && mapId.equals(mineHermitMaps()[(int) hm[0]])) { HashMap<String, Object> hr = new HashMap<>(); hr.put("x", hm[1]); hr.put("y", hm[2]); hr.put("until", hm[3]); out.put("hermit", hr); }
        if (hm != null && isMineAdmin(userName)) out.put("hermitAdmin", mineHermitMaps()[(int) hm[0]] + " (" + hm[1] + ", " + hm[2] + ")");
        long[] am = mineArmorerNow(now); // [2026-11-20] 떠돌이 갑옷장이
        if (am != null && mapId.equals(mineArmorerMaps()[(int) am[0]])) { HashMap<String, Object> ar = new HashMap<>(); ar.put("x", am[1]); ar.put("y", am[2]); ar.put("until", am[3]); out.put("armorer", ar); }
        if (am != null && isMineAdmin(userName)) out.put("armorerAdmin", mineArmorerMaps()[(int) am[0]] + " (" + am[1] + ", " + am[2] + ")");
        { // [2026-11-21] "다도해 떠상을 전체 지도에 현재 어디 있는지 표시, 악마 들판도" -- 누구에게나 맵만(칸은 안 알림). 사라져 있으면 돌아오는 시각
            HashMap<String, Object> wn = new HashMap<>();
            if (hm != null) wn.put("h", mineHermitMaps()[(int) hm[0]]); else if (mineHermitBackAt > now) wn.put("hb", mineHermitBackAt);
            if (am != null) wn.put("a", mineArmorerMaps()[(int) am[0]]); else if (mineArmorerBackAt > now) wn.put("ab", mineArmorerBackAt);
            out.put("wn", wn);
        }
        if (minePatchId > 0) { out.put("patchId", minePatchId); out.put("patchText", minePatchText); } // [2026-10-28] 패치 전 공지
        String rw = MINE_SHRINE_REWARD_MSG.remove(userName); // [2026-10-28] 닌자 두목을 함께 쓰러뜨린 보상 소식 -- 전투 중이 아니어도 1초 위치 알림으로 받는다
        if (rw != null) out.put("rewardMsg", rw);
        return out;
    }

    // ---- [2026-10-28] 패치 전 공지 -- "패치 전 공지 문구 띄우는 기능, 인게임에만" 요청.
    //  관리자가 /패치 [내용]을 치면 지금 광산에 있는 사람 화면 맨 위에 띠로 뜬다(1초마다 오가는 위치 알림 minePresence에 실어 보낸다).
    //  [2026-10-28] 명령어 /패치공지 -> /패치, 내용 없이 /패치만 치면 "곧 패치가 시작됩니다."
    //  메모리에만 두므로 배포(서버 재시작)하면 저절로 사라진다. "끄기"로 미리 내릴 수도 있다.
    static volatile String minePatchText = "";
    static volatile long minePatchId = 0;
    static final String MINE_PATCH_DEFAULT = "곧 패치가 시작됩니다.";

    @Override
    public String setMinePatchNotice(String userName, String text) {
        if (!isEventAdmin(userName) && !isMineAdmin(userName)) return "권한이 없습니다.";
        if (text == null || text.trim().isEmpty()) text = MINE_PATCH_DEFAULT;
        if ("끄기".equals(text.trim())) {
            minePatchId = 0;
            minePatchText = "";
            return "📢 패치 공지를 내렸어요.";
        }
        String body = text.trim().replace(" / ", "\n");
        minePatchText = body.length() > 300 ? body.substring(0, 300) : body;
        minePatchId = System.currentTimeMillis();
        long now = minePatchId;
        // [2026-11-19] "리턴 메시지가 너무 길어 -- 누구 접속자가 있는지만 말해 주고 패치공지완료로" -- 지금 광산에 있는 사람 닉네임만
        List<String> on = new ArrayList<>();
        for (Map.Entry<String, MinePresence> pe : MINE_PRESENCE.entrySet()) if (now - pe.getValue().at <= MINE_PRESENCE_TTL_MS) on.add(mineNick(pe.getKey()));
        return (on.isEmpty() ? "접속자 없음" : "접속자 " + on.size() + "명: " + String.join(", ", on)) + "\n패치공지완료";
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
        for (HashMap<String, Object> row : dao.selectAllMineUsers()) out.add(mineOverviewOf(row, now));
        return out;
    }

    /** [2026-10-30] "광산 접속 화면에 관리자 페이지처럼 캐릭터 정보" -- 내 현황 하나(광산 기록이 없으면 null). */
    @Override
    public HashMap<String, Object> mineMyOverview(String userName) {
        if (!mineAccess(userName)) return null;
        HashMap<String, Object> row = dao.selectMineUser(userName);
        return row == null ? null : mineOverviewOf(row, System.currentTimeMillis());
    }

    /** 한 사람 현황(광산 캐릭터 현황 페이지 / 광산 접속 화면). */
    HashMap<String, Object> mineOverviewOf(HashMap<String, Object> row, long now) {
        String u = strVal(row.get("USER_NAME"), "");
        Map<String, Integer> items = mineItems(u);
        HashMap<String, Object> m = new HashMap<>();
        m.put("userName", u);
        m.put("fishGear", mineFishGear(u)); // [2026-11-20] 접속 전 화면 낚시용품
        MinePresence p = MINE_PRESENCE.get(u);
        boolean online = p != null && p.mapId != null && now - p.at <= MINE_PRESENCE_TTL_MS;
        S5MineMap map = online ? S5MineMap.get(p.mapId) : mineMapOf(row);
        int[] pos = online ? new int[] { p.x, p.y } : minePos(row, map, mineWalkMode(items));
        m.put("online", online);
        m.put("mapId", map.id);
        m.put("mapName", map.name);
        m.put("x", pos[0]);
        m.put("y", pos[1]);
        m.put("lastAt", online ? p.at : longOf(row.get("POS_AT_MS"), 0L));
        int lv = mineLv(row), rb = mineRebirth(items);
        m.put("lv", lv);
        m.put("rebirth", rb); // [2026-10-28] 환생 횟수("Lv.1(환생1)")
        { // [2026-11-05] 관리자 캐릭터 현황 -- 속성별 검기 배운 단계 / 켠 단계, 행운의 주문서
            int[] al = new int[9], aa = mineAuraActive(items);
            for (int e = 1; e <= 8; e++) al[e] = Math.max(0, Math.min(items.getOrDefault(mineAuraKey(false, e), 0), MINE_AURA_MAX));
            m.put("auraL", al);
            m.put("auraA", aa);
            HashMap<String, Object> wi = mineWhirlInfo(items); // [2026-11-08] "관리자 뷰에 주변 공격(돌개베기) 레벨, 필살 레벨"
            m.put("whirlL", wi.get("l"));
            m.put("whirlA", wi.get("a"));
            m.put("ultL", new int[] { mineUltLv(items, 0), mineUltLv(items, 1) });
            m.put("luckUntil", mineLuckUntil(items));
        }
        for (int mc = 0; mc < MINE_MERC_NAMES.length; mc++) { // [2026-11-07] "관리자 화면에 용병 레벨도" -- 계약한 용병(직업/이름/레벨/속성, 함께 다니는지, 급여 밀림)
            int mlv = mineMercLv(items, mc);
            if (mlv <= 0) continue;
            HashMap<String, Object> mm = new HashMap<>();
            mm.put("job", MINE_MERC_NAMES[mc]);
            mm.put("nm", mineMercNm(items, mc, "NM"));
            mm.put("lv", mlv);
            mm.put("el", mineMercEl(items, mc));
            mm.put("on", mineMercOn(items) == mc);
            mm.put("unpaid", !mineMercPaid(items, mc, System.currentTimeMillis()));
            m.put("merc", mm);
        }
        if (mineMercLv(items, MINE_MERC_K2) > 0) { // [2026-11-18] 두 번째 용병
            HashMap<String, Object> mm = new HashMap<>();
            mm.put("job", MINE_MERC_NAMES[mineMercCls(items, MINE_MERC_K2)]);
            mm.put("nm", mineMercNm(items, MINE_MERC_K2, "NM"));
            mm.put("lv", mineMercLv(items, MINE_MERC_K2));
            mm.put("el", mineMercEl(items, MINE_MERC_K2));
            mm.put("on", mineMercOn2(items));
            m.put("merc2", mm);
        }
        if (mineMercLv(items, MINE_MERC_K3) > 0) { // [2026-11-23] 세 번째 용병
            HashMap<String, Object> mm = new HashMap<>();
            mm.put("job", MINE_MERC_NAMES[mineMercCls(items, MINE_MERC_K3)]);
            mm.put("nm", mineMercNm(items, MINE_MERC_K3, "NM"));
            mm.put("lv", mineMercLv(items, MINE_MERC_K3));
            mm.put("el", mineMercEl(items, MINE_MERC_K3));
            mm.put("on", mineMercOn3(items));
            m.put("merc3", mm);
        }
        m.put("exp", Math.max(0, longOf(row.get("M_EXP"), 0L)));
        m.put("expNeed", mineExpNeed(lv, rb));
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
        if (mineHasDragonRing(items)) gear.add(mineOverviewGear("용왕의 반지", "dragon", 1, 1, mineWaterWalk(items)));
        int oct = mineCoatTier(items), ogt = mineGloveTier(items); // [2026-10-28] 외투/장갑은 단계 하나씩
        if (oct > 0) gear.add(mineOverviewGear(MINE_COAT_NAMES[oct - 1], "coat", oct, 3, mineCoatDef(items) > 0));
        if (ogt > 0) gear.add(mineOverviewGear(MINE_GLOVE_NAMES[ogt - 1], "coat", ogt, 3, mineGlovesOn(items)));
        m.put("gear", gear);
        // [2026-10-28] "광산 캐릭터 현황을 인게임과 비슷하게" -- 인벤토리 장비 칸(무기/반지/장갑/옷/목걸이/신발/채집 도구)과 같은 목록, 전투 수치
        m.put("equip", mineEquipList(items));
        m.put("helmet", helmet);
        m.put("helmetMax", MINE_HELMET_OPEN_MAX);
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0);
        m.put("hpMax", mineHpCap(lv, lock, rb));
        m.put("hp", mineHpNow(row, now, lock, rb));
        m.put("atk", (int) Math.round(mineAtkP(lv, items) * mineAtkMul(items) + mineWeaponFlat(items)));
        m.put("coldDef", mineColdDef(items));
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
        return m;
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

    static HashMap<String, Object> mineOverviewGear(String name, String kind, int tier, int maxTier, boolean worn) {
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
    public HashMap<String, Object> mineGreet(String userName, int t) {
        t = Math.max(0, Math.min(MINE_GREET_LINES.length - 1, t)); // [2026-11-22] 인사말 번호
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
        int[] pos = minePos(row, map, mineWalkMode(items));
        MinePresence me = minePresenceOf(userName);
        // [2026-10-28] "3명 모이면 인사가 잘 안 된다" -- 저장 위치(DB, 움직이는 중 2.5초마다)로 재면 화면과 어긋나서 근처 판정이 자주 빗나갔다.
        //  1초마다 오는 위치 알림(화면이 보는 것과 같은 위치)이 있으면 그걸로 잰다.
        int gx = pos[0], gy = pos[1];
        if (map.id.equals(me.mapId) && now - me.at <= 3_000L) { gx = me.x; gy = me.y; }
        boolean near = false;
        for (MinePresence o : MINE_PRESENCE.values()) {
            if (o == me || now - o.at > MINE_PRESENCE_TTL_MS || !map.id.equals(o.mapId)) continue;
            if (Math.abs(o.x - gx) + Math.abs(o.y - gy) <= MINE_GREET_RANGE + 2) near = true; // 위치 알림도 최대 1초 늦다 -- 2칸 여유
        }
        if (!near) { out.put("message", "근처에 인사할 사람이 없어요."); return out; }
        if (now - me.bubbleAt < 3_000L) { out.put("message", "조금 있다가 다시 인사해요."); return out; }
        me.bubbleT = t;
        me.bubbleAt = now;
        out.put("ok", true);
        out.put("t", t);
        int today = Integer.parseInt(java.time.LocalDate.now(KST).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        if (items.getOrDefault(MINE_ITEM_GREET_DAY, 0) != today) {
            int di = S5MineMap.dayIndex(now);
            dao.upsertMineItem(userName, MINE_ITEM_GREET_DAY, today);
            dao.addUserOre(userName, S5MineMap.DAY_STONE_CODES[di], MINE_GREET_STONES);
            out.put("reward", true);
            out.put("ores", mineOreList(userName));
            out.put("message", "👋 " + MINE_GREET_LINES[t] + " 오늘의 첫 인사 -- " + S5MineMap.DAY_STONE_NAMES[di] + " " + MINE_GREET_STONES + "개를 받았어요.");
        } else out.put("message", "👋 " + MINE_GREET_LINES[t] + " (인사 보상은 하루 한 번 -- 오늘은 이미 받았어요)");
        return out;
    }

    // ---- [2026-10-24] 명품상점(광산 캐릭터 외형) ----
    // "캐릭터 외형을 바꿀 수 있게, 대장장이 옆 건물에 명품상점 -- 부위별 치장 아이템, 성별 전환도 여기서" 요청. 능력치 없음(보이는 것만).
    //  산 아이템: TBOT_S5_MINE_ITEM "COS_" + 코드(1), 부위별 착용: "LOOK_" + 부위(카탈로그 순번 + 1, 0 = 기본), 성별: "LOOK_GENDER"(0 남 / 1 여).
    //  모양/색(look 값)은 화면(TowerMine.charSprite)이 그린다.
    static final String[] MINE_LOOK_SLOTS = { "hat", "hair", "top", "bottom", "shoes", "wing" }; // [2026-10-29] wing = 환생 날개 염색(기본 흰색)
    static final String MINE_ITEM_GENDER = "LOOK_GENDER";
    // [2026-11-20] "머리 스타일 골고루 추가, 머리 변경 및 염색 기능(상인)" -- 왕도 미용실(barber): 머리 모양(LOOK_HSTYLE = 순번, 0 = 성별 기본), 염색(LOOK_HDYE = 팔레트 순번 + 1, 0 = 안 함).
    //  염색은 명품상점 머리 색보다 먼저 보인다(명품상점에서 머리 색을 입으면 염색은 지워진다). 비용은 금광석(남아도는 광석).
    static final String MINE_ITEM_HSTYLE = "LOOK_HSTYLE", MINE_ITEM_HDYE = "LOOK_HDYE";
    static final String[][] MINE_HAIR_STYLES = { { "", "기본(성별에 따라)" }, { "boy", "짧은 머리" }, { "spiky", "삐죽 머리" }, { "short", "단정한 단발" }, { "bob", "보브컷" }, { "long", "긴 생머리" },
            { "wavy", "웨이브" }, { "twin", "트윈테일" }, { "ponytail", "포니테일" }, { "bun", "올림머리" }, { "side", "옆으로 땋은 머리" } };
    static final String[][] MINE_HAIR_DYES = { { "칠흑", "#1a1418" }, { "흑갈색", "#2b1d16" }, { "밤색", "#5a3220" }, { "갈색", "#7a4a26" }, { "구릿빛", "#b0602a" }, { "주황", "#f08a30" }, { "금발", "#f0d060" },
            { "백금", "#f4ecc8" }, { "은발", "#d8dde6" }, { "새하양", "#f8f8f8" }, { "잿빛", "#8a8a96" }, { "벚꽃", "#f8b8d0" }, { "분홍", "#f58fb8" }, { "다홍", "#e0405a" }, { "포도", "#7a3aa8" },
            { "라벤더", "#b89ae8" }, { "남색", "#2a3a8a" }, { "하늘", "#6fc8f0" }, { "민트", "#7fe0c0" }, { "초록", "#3f9a5a" } };
    static final String[][] MINE_HAIR_STYLE_COST = { { S5MineMap.ORE_CODES[3], "10" } }, MINE_HAIR_DYE_COST = { { S5MineMap.ORE_CODES[3], "5" } }; // 금광석 10 / 5
    /** 성별 전환 비용. */
    static final String[][] MINE_GENDER_COST = { { S5MineMap.ORE_CODES[2], "10" } };

    static final class MineCos {
        final String code, slot, name, look; // look = "색" 또는 "색|모양"
        final String[][] cost;
        /** [2026-10-28] 운영자 전용(살 수 없음, 관리자는 처음부터 가짐, 입으면 화면에 "운영자" 표시 + 반짝이) / 상점에 덧붙이는 설명. */
        boolean admin;
        String note;
        MineCos(String code, String slot, String name, String look, String[][] cost) {
            this.code = code; this.slot = slot; this.name = name; this.look = look; this.cost = cost;
        }
        MineCos asAdmin(String note) { admin = true; this.note = note; return this; }
    }

    static String[] mineOre(int grade, int qty) {
        return new String[] { S5MineMap.ORE_CODES[grade - 1], String.valueOf(qty) };
    }

    static final List<MineCos> MINE_COS = java.util.Arrays.asList(
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
        new MineCos("SHOE_GOLD", "shoes", "황금 신", "#f2c230", new String[][] { mineOre(4, 20) }),
        // [2026-10-28] "치장 아이템 추가" -- 착용 칸(LOOK_부위)이 카탈로그 순번이라 새 아이템은 항상 맨 뒤에 붙인다.
        new MineCos("HAT_CAT", "hat", "고양이 귀", "#3b3b44|cat", new String[][] { mineOre(2, 120) }),
        new MineCos("HAT_FLOWER", "hat", "꽃 화관", "#f58fb8|flower", new String[][] { mineCost(5, 5) }),
        new MineCos("HAT_SANTA", "hat", "산타 모자", "#d42a2a|santa", new String[][] { mineOre(3, 60) }),
        new MineCos("HAT_HORN", "hat", "도깨비 뿔", "#efe6d2|horn", new String[][] { mineCost(1, 5) }),
        new MineCos("HAIR_RED", "hair", "붉은 머리", "#c8402a", new String[][] { mineCost(1, 3) }),
        new MineCos("HAIR_PURPLE", "hair", "보라 머리", "#9a5ad8", new String[][] { mineCost(6, 5) }),
        new MineCos("HAIR_MINT", "hair", "민트 머리", "#7fe0c0", new String[][] { mineCost(7, 2) }),
        new MineCos("TOP_CAPE", "top", "기사 망토", "#2c4a8a|cape", new String[][] { mineOre(4, 40), mineOre(5, 10) }),
        new MineCos("TOP_FUR", "top", "눈꽃 털옷", "#f4f6fa|fur", new String[][] { { S5MineMap.ICE_STONE_CODE, "5" } }),
        new MineCos("TOP_STAR", "top", "별빛 로브", "#2a2a6a|star", new String[][] { mineOre(6, 2), mineCost(2, 5) }),
        new MineCos("BOT_BLUE", "bottom", "청바지", "#3a5a9a", new String[][] { mineOre(2, 80) }),
        new MineCos("BOT_PURPLE", "bottom", "보라 치마바지", "#6a3a9a", new String[][] { mineCost(6, 3) }),
        new MineCos("SHOE_ICE", "shoes", "얼음 장화", "#9fd8f0", new String[][] { { S5MineMap.ICE_STONE_CODE, "3" } }),
        new MineCos("SHOE_WHITE", "shoes", "흰 운동화", "#f4f4f4", new String[][] { mineOre(3, 30) }),
        // [2026-10-28] "운영자 전용 장비 -- 누가 봐도 운영진" + "이속 5배 신발, 화려하게 반짝이는 이펙트". 상점에서 못 사고 관리자(isMineAdmin)만 가진다.
        new MineCos("HAT_GM", "hat", "운영자 왕관", "#ffd84a|gmcrown", new String[0][]).asAdmin("운영자 전용"),
        new MineCos("TOP_GM", "top", "운영자 제복", "#5b1fa8|gm", new String[0][]).asAdmin("운영자 전용"),
        new MineCos("SHOE_GM", "shoes", "바람의 신", "#7ff0ff|wing", new String[0][]).asAdmin("운영자 전용 · 이동 속도 5배"),
        // [2026-10-28] "치장상점에 황금바지 추가 + 캐시 아이템 같은 치장을 많이" -- 보석/다이아/칠요석/별빛석으로 사는 고급 치장(능력치 없음). 맨 뒤에만 붙인다.
        new MineCos("BOT_GOLD", "bottom", "황금 바지", "#f2c230", new String[][] { mineOre(4, 25), mineCost(8, 1) }),
        new MineCos("HAT_RABBIT", "hat", "토끼 귀 머리띠", "#f4eef4|rabbit", new String[][] { mineCost(0, 8), mineCost(5, 3) }),
        new MineCos("HAT_HALO", "hat", "천사의 고리", "#ffe97a|halo", new String[][] { mineCost(8, 2), mineOre(6, 1) }),
        new MineCos("HAT_DEVIL", "hat", "악마 뿔", "#c0283a|horn", new String[][] { mineCost(1, 8), mineCost(6, 4) }),
        new MineCos("HAT_BERET", "hat", "화가 베레모", "#c0392b|beret", new String[][] { mineCost(1, 4), mineCost(3, 4) }),
        new MineCos("HAT_RIBBON", "hat", "왕리본", "#ff6fa8|ribbon", new String[][] { mineCost(5, 6) }),
        new MineCos("HAT_TIARA", "hat", "별빛 티아라", "#bfe8ff|tiara", new String[][] { mineCost(8, 3), mineOre(6, 2) }),
        new MineCos("HAT_MAGE_RED", "hat", "불꽃 마법사 모자", "#a8202a|wizard", new String[][] { mineCost(1, 10), { S5MineMap.SEVEN_STONE_CODE, "1" } }),
        new MineCos("HAT_GOLD_HELM", "hat", "황금 투구", "#f2c230|helmet", new String[][] { mineOre(4, 40), mineCost(8, 2) }),
        new MineCos("HAIR_WHITE", "hair", "새하얀 머리", "#f4f4f4", new String[][] { mineCost(3, 6) }),
        new MineCos("HAIR_GREEN", "hair", "초록 머리", "#4fbf6a", new String[][] { mineCost(7, 3) }),
        new MineCos("HAIR_GOLD", "hair", "황금빛 머리", "#ffcf30", new String[][] { mineOre(4, 20), mineCost(8, 1) }),
        new MineCos("HAIR_NAVY", "hair", "남색 머리", "#2a3a8a", new String[][] { mineCost(2, 4) }),
        new MineCos("HAIR_ORANGE", "hair", "주황 머리", "#f08a30", new String[][] { mineCost(4, 6) }),
        new MineCos("TOP_TUX", "top", "연미복", "#1a1a22|tux", new String[][] { mineCost(8, 2), mineCost(0, 10) }),
        new MineCos("TOP_HOODIE", "top", "후드티", "#7a8aa0|hoodie", new String[][] { mineOre(3, 40), mineCost(0, 5) }),
        new MineCos("TOP_SAILOR", "top", "세일러복", "#f4f4f4|sailor", new String[][] { mineCost(3, 8), mineCost(2, 3) }),
        new MineCos("TOP_ANGEL", "top", "천사의 날개옷", "#f8f4ff|wings", new String[][] { mineCost(8, 3), mineOre(6, 2), { S5MineMap.SEVEN_STONE_CODE, "1" } }),
        new MineCos("TOP_DEVIL", "top", "악마의 날개옷", "#3a1020|bat", new String[][] { mineCost(1, 12), mineCost(6, 8), { S5MineMap.SEVEN_STONE_CODE, "1" } }),
        new MineCos("TOP_PIRATE_COAT", "top", "선장 코트", "#6a2a1a|cape", new String[][] { mineCost(3, 10), mineOre(4, 30) }),
        new MineCos("TOP_SAMURAI", "top", "무사 갑옷", "#8a1a1a|armor", new String[][] { mineCost(1, 6), mineOre(5, 15) }),
        new MineCos("TOP_RAINBOW", "top", "무지개 로브", "#ffffff|rainbow", new String[][] { mineCost(8, 3), { S5MineMap.SEVEN_STONE_CODE, "2" } }),
        new MineCos("BOT_PINK", "bottom", "분홍 치마", "#f58fb8", new String[][] { mineCost(5, 4) }),
        new MineCos("BOT_CAMO", "bottom", "군복 바지", "#5a6a3a", new String[][] { mineOre(2, 100), mineCost(7, 1) }),
        new MineCos("BOT_NAVY", "bottom", "남색 정장 바지", "#2a3a6a", new String[][] { mineCost(2, 4) }),
        new MineCos("BOT_SILVER", "bottom", "은빛 바지", "#c8ccd4", new String[][] { mineOre(3, 60), mineCost(8, 1) }),
        new MineCos("SHOE_BLACK", "shoes", "검은 구두", "#141418", new String[][] { mineCost(2, 3) }),
        new MineCos("SHOE_PURPLE", "shoes", "보라 부츠", "#8a4ad8", new String[][] { mineCost(6, 4) }),
        new MineCos("SHOE_GREEN", "shoes", "초록 운동화", "#4fbf6a", new String[][] { mineCost(7, 2) }),
        new MineCos("SHOE_FLAME", "shoes", "불꽃 신", "#ff6a2a|flame", new String[][] { mineCost(1, 10), mineOre(6, 1) }),
        // [2026-10-29] "날개 전부 흰색, 치장상점에서 염색" -- 환생 날개 색(입으면 날개가 그 색, 기본 = 흰색). 목록 뒤에만 붙인다(착용 번호 = 목록 순서).
        new MineCos("WING_SKY", "wing", "하늘빛 날개 염색", "#bfe4ff", new String[][] { mineCost(3, 5) }),
        new MineCos("WING_PINK", "wing", "벚꽃 날개 염색", "#ffc0dc", new String[][] { mineCost(6, 5) }),
        new MineCos("WING_MINT", "wing", "민트 날개 염색", "#9fecd0", new String[][] { mineCost(7, 5) }),
        new MineCos("WING_GOLD", "wing", "황금 날개 염색", "#ffd75a", new String[][] { mineCost(4, 5), mineBar(3, 1) }),
        new MineCos("WING_PURPLE", "wing", "별빛 보라 날개 염색", "#c8a8ff", new String[][] { mineCost(2, 5) }),
        new MineCos("WING_RED", "wing", "불사조 날개 염색", "#ff7a5a", new String[][] { mineCost(1, 8) }),
        new MineCos("WING_BLACK", "wing", "흑요 날개 염색", "#4a4458", new String[][] { mineCost(5, 5), mineCost(8, 1) }),
        new MineCos("WING_RAINBOW", "wing", "무지개 날개 염색", "rainbow", new String[][] { mineCost(8, 3), mineSeven(3) }),
        // [2026-11-06] "치장상점에 용병들이 입는 옷도" -- 용병 옷차림(메이드/기모노/무녀/교복/고딕)과 머리 장식. 맨 뒤에만 붙인다.
        new MineCos("TOP_MAID", "top", "메이드복", "#2a2230|maid", new String[][] { mineCost(0, 8), mineCost(5, 3) }),
        new MineCos("TOP_MAID_PINK", "top", "분홍 메이드복", "#f8b4c6|maid", new String[][] { mineCost(5, 6), mineCost(6, 3) }),
        new MineCos("TOP_KIMONO", "top", "동백 기모노", "#5a1a2a|kimono", new String[][] { mineCost(1, 6), mineBar(3, 1) }),
        new MineCos("TOP_KIMONO_PINK", "top", "벚꽃 기모노", "#e86090|kimono", new String[][] { mineCost(5, 6), mineBar(3, 1) }),
        new MineCos("TOP_MIKO", "top", "무녀복", "#f2f4fc|miko", new String[][] { mineCost(1, 4), mineCost(3, 4) }),
        new MineCos("TOP_BLAZER", "top", "학원 블레이저", "#2a3a6a|blazer", new String[][] { mineCost(2, 5), mineOre(3, 40) }),
        new MineCos("TOP_GOTHIC", "top", "고딕 드레스", "#1e1a20|gothic", new String[][] { mineCost(1, 8), mineCost(6, 4), mineCost(8, 1) }),
        new MineCos("HAT_MAIDBAND", "hat", "메이드 머리띠", "#ffffff|maidband", new String[][] { mineCost(0, 6) }),
        new MineCos("HAT_SUNHAT", "hat", "하얀 챙모자", "#f8f8fb|sunhat", new String[][] { mineCost(3, 5), mineOre(3, 30) }),
        new MineCos("HAT_KANZASHI", "hat", "꽃 비녀", "#e04050|kanzashi", new String[][] { mineCost(5, 4) }),
        new MineCos("BOT_HAKAMA", "bottom", "붉은 하카마", "#c8303c", new String[][] { mineCost(1, 3) }),
        new MineCos("BOT_PLEATED", "bottom", "검은 주름치마", "#2a2428", new String[][] { mineOre(2, 80), mineCost(0, 2) }),
        // [2026-11-19] "NPC 룩템 -- 기사단장 코스튬, 기사단원 코스튬"(시안 승인) -- 탑 가는 길 기사단 차림(판금 + 투구, 단장은 붉은 망토). 입으면 모자 대신 투구(_mine_char.jsp). 맨 뒤에만 붙인다.
        new MineCos("TOP_KNIGHT", "top", "기사단원 코스튬", "#9aa3b6|knight", new String[][] { mineOre(4, 30), mineCost(1, 3) }),
        new MineCos("TOP_KNIGHT_LORD", "top", "기사단장 코스튬", "#d9b44a|knightlord", new String[][] { mineOre(4, 50), mineCost(8, 2), mineBar(3, 1) })
    );
    /** [2026-10-28] 바람의 신 -- 화면 한 칸 이동 시간을 기본(140ms)의 1/5로. */
    static final String MINE_COS_SPEED = "SHOE_GM";
    static final int MINE_COS_SPEED_DIV = 5;

    static MineCos mineCos(String code) {
        for (MineCos c : MINE_COS) if (c.code.equals(code)) return c;
        return null;
    }

    /** 그 부위에 입고 있는 치장 아이템(기본이면 null). */
    static MineCos mineWornCos(Map<String, Integer> items, String slot) {
        int idx = items.getOrDefault("LOOK_" + slot.toUpperCase(), 0) - 1;
        MineCos c = idx >= 0 && idx < MINE_COS.size() ? MINE_COS.get(idx) : null;
        return c != null && c.slot.equals(slot) ? c : null;
    }

    /** 외형(성별 + 부위별 착용 look 값) + 상점 목록을 out에 넣는다. */
    /** 외형(성별 + 부위별 look 값과 코드) -- 내 화면(look)과 다른 유저에게 보이는 모습(광산 함께 보기)에 같이 쓴다. */
    static HashMap<String, Object> mineLookOf(Map<String, Integer> items) {
        HashMap<String, Object> look = new HashMap<>();
        look.put("gender", items.getOrDefault(MINE_ITEM_GENDER, 0));
        boolean gm = false;
        for (String slot : MINE_LOOK_SLOTS) {
            MineCos c = mineWornCos(items, slot);
            look.put(slot, c != null ? c.look : "");
            look.put(slot + "Code", c != null ? c.code : "");
            if (c != null && c.admin) gm = true;
        }
        if (gm) look.put("gm", true); // [2026-10-28] 운영자 장비를 입었으면 화면이 "운영자" 표시 + 반짝이
        int hst = items.getOrDefault(MINE_ITEM_HSTYLE, 0), hdy = items.getOrDefault(MINE_ITEM_HDYE, 0); // [2026-11-20] 미용실
        if (hst > 0 && hst < MINE_HAIR_STYLES.length) look.put("hairStyle", MINE_HAIR_STYLES[hst][0]);
        if (hdy > 0 && hdy <= MINE_HAIR_DYES.length) look.put("hair", MINE_HAIR_DYES[hdy - 1][1]);
        MineWeapon lw = mineWeaponOn(items); // [2026-10-29] "무기별로 외형 조금씩 다르게" -- 드랍 무기는 무기마다 그림이 다르다(_mine_char.jsp WEAPON_LOOK)
        look.put("weapon", lw != null ? mineWeaponLook(lw) : mineParasolOn(items) ? "parasol" : "sword"); // [2026-10-28] 손에 든 무기(화면 그림)
        int rb = mineRebirth(items);
        if (rb > 0) look.put("rebirth", rb); // [2026-10-28] 환생 날개(단계별로 커진다) -- 다른 유저 화면/관리자 페이지 그림에도
        return look;
    }

    static void mineLookPayload(Map<String, Integer> items, Map<String, Integer> have, HashMap<String, Object> out, boolean admin) {
        out.put("look", mineLookOf(items));
        List<HashMap<String, Object>> shop = new ArrayList<>();
        for (MineCos c : MINE_COS) {
            if (c.admin && !admin) continue; // [2026-10-28] 운영자 전용은 관리자에게만 보인다
            HashMap<String, Object> m = new HashMap<>();
            m.put("code", c.code);
            m.put("slot", c.slot);
            m.put("name", c.name);
            m.put("look", c.look);
            if (c.note != null) m.put("note", c.note);
            boolean owned = items.getOrDefault("COS_" + c.code, 0) > 0 || c.admin;
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
        // [2026-11-20] 미용실 -- 머리 모양/염색 목록, 지금 고른 것, 비용
        List<HashMap<String, Object>> hs = new ArrayList<>(), hd = new ArrayList<>();
        for (int i = 0; i < MINE_HAIR_STYLES.length; i++) { HashMap<String, Object> m = new HashMap<>(); m.put("code", MINE_HAIR_STYLES[i][0]); m.put("name", MINE_HAIR_STYLES[i][1]); hs.add(m); }
        for (String[] dy : MINE_HAIR_DYES) { HashMap<String, Object> m = new HashMap<>(); m.put("name", dy[0]); m.put("color", dy[1]); hd.add(m); }
        out.put("hairStyles", hs); out.put("hairDyes", hd);
        out.put("hairStyleCur", items.getOrDefault(MINE_ITEM_HSTYLE, 0)); out.put("hairDyeCur", items.getOrDefault(MINE_ITEM_HDYE, 0));
        HashMap<String, Object> bc = new HashMap<>();
        bc.put("style", mineCostList(MINE_HAIR_STYLE_COST, have)); bc.put("dye", mineCostList(MINE_HAIR_DYE_COST, have));
        bc.put("styleCan", mineCostOk(MINE_HAIR_STYLE_COST, have)); bc.put("dyeCan", mineCostOk(MINE_HAIR_DYE_COST, have));
        out.put("barber", bc);
    }

    /** 명품상점 -- action: buy(code, 사면 바로 입음) / wear(code, 빈 값 + slot = 그 부위 기본으로) / gender(성별 전환, 은광석 10개). */
    @Override
    @Transactional
    public HashMap<String, Object> mineLook(String userName, String action, String code, String slot) {
        if (!mineAccess(userName)) return mineClosed();
        if ("opt".equals(action)) { // [2026-11-21] "32x32나 데미지 하나로 보는 옵션은 저장되어야 해" -- 화면 옵션을 계정에(기기 저장소가 비워져도 남게). code = hd / dmg, slot = 1 켬 / 0 끔
            String key = "hd".equals(code) ? MINE_ITEM_OPT_HD : "dmg".equals(code) ? MINE_ITEM_OPT_DMG : null;
            HashMap<String, Object> oo = new HashMap<>();
            oo.put("ok", key != null);
            if (key != null) dao.upsertMineItem(userName, key, "1".equals(slot) ? 1 : 2);
            return oo;
        }
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
        if ("hairStyle".equals(action) || "hairDye".equals(action)) { // [2026-11-20] 미용실 -- code = 순번(모양 0 = 기본, 염색 0 = 안 함)
            if (!mineAtNpc(dao.selectMineUser(userName), "barber")) return mineGearResult(userName, false, "미용실에서만 바꿀 수 있어요.");
            boolean style = "hairStyle".equals(action);
            int idx = -1;
            try { idx = Integer.parseInt(code); } catch (Exception e) { }
            if (idx < 0 || idx > (style ? MINE_HAIR_STYLES.length - 1 : MINE_HAIR_DYES.length)) return mineGearResult(userName, false, "그런 머리는 없어요.");
            String key = style ? MINE_ITEM_HSTYLE : MINE_ITEM_HDYE;
            if (items.getOrDefault(key, 0) == idx) return mineGearResult(userName, false, "💇 지금 그 머리예요.");
            String[][] cost = style ? MINE_HAIR_STYLE_COST : MINE_HAIR_DYE_COST;
            if (idx > 0) { // 기본으로 되돌리기는 무료
                if (!mineCostOk(cost, mineOreHave(userName))) return mineGearResult(userName, false, "💇 미용실: 재료가 모자라요. (" + mineCostText(cost) + " 필요" + mineLackText(cost, userName) + ")");
                mineTakeCost(userName, cost, 1);
            }
            dao.upsertMineItem(userName, key, idx);
            String nm = style ? MINE_HAIR_STYLES[idx][1] : idx == 0 ? "원래 색" : MINE_HAIR_DYES[idx - 1][0];
            return mineGearResult(userName, true, "💇 짜잔~ " + (style ? "머리 모양을 「" + nm + "」(으)로 바꿨어요!" : idx == 0 ? "염색을 지웠어요." : "머리를 「" + nm + "」 색으로 물들였어요!") + (idx > 0 ? " (" + mineCostText(cost) + " 사용)" : ""));
        }
        if ("wear".equals(action) && (code == null || code.isEmpty())) {
            if (slot == null || !java.util.Arrays.asList(MINE_LOOK_SLOTS).contains(slot)) return mineGearResult(userName, false, "잘못된 부위예요.");
            dao.upsertMineItem(userName, "LOOK_" + slot.toUpperCase(), 0);
            if ("hair".equals(slot)) dao.upsertMineItem(userName, MINE_ITEM_HDYE, 0); // [2026-11-20] 머리 색을 바꾸면 염색은 지운다
            return mineGearResult(userName, true, "기본 모습으로 돌아갔어요.");
        }
        MineCos c = mineCos(code);
        if (c == null || (c.admin && !isMineAdminName(userName))) return mineGearResult(userName, false, "없는 상품이에요."); // [2026-11-23] 이름만(신발 신기 전에도)
        boolean owned = items.getOrDefault("COS_" + c.code, 0) > 0 || c.admin; // [2026-10-28] 운영자 전용은 관리자가 처음부터 가진다
        if ("buy".equals(action) && !owned) {
            if (!mineCostOk(c.cost, mineOreHave(userName))) return mineGearResult(userName, false, "명품상점: 재료가 모자라요. (" + mineCostText(c.cost) + " 필요" + mineLackText(c.cost, userName) + ")");
            mineTakeCost(userName, c.cost, 1);
            dao.upsertMineItem(userName, "COS_" + c.code, 1);
            owned = true;
        } else if (!owned) {
            return mineGearResult(userName, false, c.name + "은(는) 아직 안 샀어요.");
        }
        dao.upsertMineItem(userName, "LOOK_" + c.slot.toUpperCase(), MINE_COS.indexOf(c) + 1);
        if ("hair".equals(c.slot)) dao.upsertMineItem(userName, MINE_ITEM_HDYE, 0); // [2026-11-20] 명품상점 머리 색을 입으면 미용실 염색은 지운다
        return mineGearResult(userName, true, "✨ " + c.name + ("buy".equals(action) ? " 구매! 바로 입었어요. (" + mineCostText(c.cost) + " 사용)" : " 착용!"));
    }


    // ---- 탑(시즌 5)과 함께 쓰는 도우미(BotS5ServiceImpl에서 옮김) ----
    static final Random RND = new Random();
    static final int MINE_DIG_CUT_PERMILLE = 3; // [2026-11-05] 채광 중 세공된 보석 0.3%

    int intVal(Object o, int def) {
        if (o == null) return def;
        return ((Number) o).intValue();
    }

    String strVal(Object o, String def) {
        return o == null ? def : o.toString();
    }

    double numVal(Object o, double def) {
        if (o == null) return def;
        return ((Number) o).doubleValue();
    }

    static HashMap<String, Object> mineClosed() {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        out.put("closed", true);
        out.put("error", "⛏️ 광산은 아직 준비 중입니다.");
        out.put("message", "⛏️ 광산은 아직 준비 중입니다.");
        return out;
    }

    /** [2026-10-24] 광산 관리자(좌표 이동) -- MINE_ADMIN_USERS 또는 EVENT_ADMIN_USERS에 있는 유저. */
    // [2026-11-23] "관리자도 신발 안 신으면 관리자 권한 없도록" -- 관리자 권한(관리자 지도/이동/시험 기능/드랍 배수 등)은 관리자 이름 + 운영자 신발(바람의 신)을 신고 있을 때만.
    //  신발 상점/착용(운영자 전용 상품)과 관리자 알림 빼기(s55IsAdmin)는 이름만 본다(안 그러면 신발을 신을 수도 없다). 신발은 3초 기억.
    boolean isMineAdmin(String userName) { return isMineAdminName(userName) && mineAdminShoesOn(userName); }
    boolean isMineAdminName(String userName) {
        if (userName == null) return false;
        if (MINE_ADMIN_USERS != null) for (String u : MINE_ADMIN_USERS.split("\\|")) if (!u.trim().isEmpty() && u.trim().equals(userName.trim())) return true;
        return isEventAdmin(userName);
    }
    static final java.util.concurrent.ConcurrentHashMap<String, long[]> MINE_ADMIN_SHOES = new java.util.concurrent.ConcurrentHashMap<>(); // 이름 -> {신었으면 1, 본 시각}
    static boolean mineShoesGm(Map<String, Integer> items) { MineCos sh = items == null ? null : mineWornCos(items, "shoes"); return sh != null && MINE_COS_SPEED.equals(sh.code); }
    boolean mineAdminShoesOn(String userName) {
        long now = System.currentTimeMillis();
        long[] c = MINE_ADMIN_SHOES.get(userName);
        if (c != null && now - c[1] < 3_000L) return c[0] == 1;
        boolean on = false;
        try { on = mineShoesGm(mineItems(userName)); } catch (Exception ignore) { }
        MINE_ADMIN_SHOES.put(userName, new long[] { on ? 1 : 0, now });
        return on;
    }

    /** userName이 EVENT_ADMIN_USERS 목록에 있는지(=이벤트 지급 명령어 실행 권한이 있는지). */
    boolean isEventAdmin(String userName) {
        if (EVENT_ADMIN_USERS == null || EVENT_ADMIN_USERS.trim().isEmpty() || userName == null) return false;
        for (String u : EVENT_ADMIN_USERS.split("\\|")) {
            if (u.trim().equals(userName)) return true;
        }
        return false;
    }

    /** [2026-10-23] 광산/광산상점을 쓸 수 있는지 -- 전체 오픈(MINE_OPEN_YN=Y)이거나 MINE_ADMIN_USERS에 있는 유저. */
    @Override
    public boolean mineAccess(String userName) {
        if (userName != null) MINE_CTX.set(userName); // [2026-11-15] 1인 층(탑) 몬스터 상태를 유저별로 가리려고 -- 광산 요청은 모두 여기를 먼저 지난다
        if (MINE_OPEN) return true;
        if (userName == null || MINE_ADMIN_USERS == null) return false;
        for (String u : MINE_ADMIN_USERS.split("\\|")) if (u.trim().equals(userName.trim())) return true;
        return false;
    }

    // [2026-10-23] "광산/광산상점은 관리자만 열고, config로 일반 유저 오픈 여부를 결정" 요청 -- TBOT_S5_CONFIG.MINE_OPEN_YN('Y'면 전체 오픈, 기본 N),
    // MINE_ADMIN_USERS('|' 구분, 오픈 전에도 들어갈 수 있는 유저). /갱신으로 반영.
    static volatile boolean MINE_OPEN = false;
    // [2026-11-15] "하피 나루터 이후와 월드보스는 관리자만 먼저 오픈 -- 내가 해 볼게" -- TBOT_S5_CONFIG.MINE_TOWER_OPEN_YN(나루터 건너 탑의 도시·탑) /
    //  MINE_WB_OPEN_YN(월드보스: 매일 21시 회차, 화면 띠, 채팅 알림). 'Y'면 모두에게, 없거나 N이면 관리자만(기본). /갱신으로 반영.
    //  닫혀 있는 동안 월드보스는 정해진 시각에 나오지 않고 관리자가 시험 회차(mineWorldBoss start)를 열었을 때만, 관리자에게만 보이고 맞는다.
    //  [2026-11-16] "월드보스와 탑 마을, 탑 오픈해줘" -- 기본값을 공개로(배포하면 모두에게). 다시 닫으려면 설정값을 'N'으로.
    static volatile boolean MINE_TOWER_OPEN = true, MINE_WB_OPEN = true;
    boolean mineTowerOk(String userName) { return MINE_TOWER_OPEN || isMineAdmin(userName); }
    boolean mineWbOk(String userName) { return MINE_WB_OPEN || isMineAdmin(userName); }

    static volatile String MINE_ADMIN_USERS = "";

    // ---- 탑(시즌 5)과 함께 쓰는 도우미(BotS5ServiceImpl에서 옮김) ----
    // /이벤트지급(관리자 전용) 실행 권한이 있는 유저명 목록 -- TBOT_S5_CONFIG.EVENT_ADMIN_USERS에
    // '|'로 구분해 저장(예: "일어난다람쥐/카단|다른관리자"). 이 시스템엔 별도 권한/역할 체계가
    // 없어서, 뽑기권처럼 실제 경제가치가 있는 걸 아무나 채팅으로 못 뿌리게 막는 유일한 장치다.
    // 비어있으면(기본값) 아무도 실행할 수 없다 -- 반드시 DB에 직접 세팅해야 활성화됨(의도적).
    static volatile String EVENT_ADMIN_USERS = "";
}
