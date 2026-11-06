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
    public boolean s55IsAdmin(String userName) { return isMineAdmin(userName); }

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
        return out;
    }

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
        MINE_MIN_LV.put(S5MineMap.ARCHISEA, 73);
        int[] isleLv = { 73, 73, 77, 77, 81, 85, 89 };
        for (int i = 0; i < S5MineMap.ISLE_IDS.length; i++) MINE_MIN_LV.put(S5MineMap.ISLE_IDS[i], isleLv[i]);
    }

    static int mineMinLv(S5MineMap m) {
        return MINE_MIN_LV.getOrDefault(m.id, 1);
    }

    /** 그 맵에 들어가거나 움직일 수 없는 이유(없으면 null). lv = 광산 전투 레벨(권장 레벨은 막지 않아 지금은 안 쓴다). */
    String mineMapBlock(S5MineMap m, Map<String, Integer> items, int lv) {
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
        return map.walkable(x, y) || ((water >= 1 || map.boat()) && map.inside(x, y) && map.terrainAt(x, y) == '~'); // [2026-10-28] 다도해 뱃길(boat)은 배로 물 위를 다닌다
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
        String[] sc = { S5MineMap.ICE_STONE_CODE, S5MineMap.MOON_STONE_CODE, S5MineMap.SEAL_CODE, S5MineMap.NINJA_BOOK_CODE, S5MineMap.ICE_HEART_CODE, S5MineMap.DESERT_HEART_CODE, S5MineMap.VOLCANO_HEART_CODE, S5MineMap.THUNDER_HEART_CODE };
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.Arrays.stream(S5MineMap.WEAPON_SHARD_CODES)).toArray(String[]::new); // [2026-10-29] 무기 조각
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.Arrays.stream(S5MineMap.ARMOR_SHARD_CODES)).toArray(String[]::new); // [2026-11-06] 갑옷 조각
        sc = java.util.stream.Stream.concat(java.util.Arrays.stream(sc), java.util.stream.Stream.of(S5MineMap.GALAXY_CODE)).toArray(String[]::new); // [2026-11-06] 은하수 조각
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
                if (S5MineMap.SNOWTOWN.equals(d) || S5MineMap.CITY.equals(d) || S5MineMap.ARCHI.equals(d) || S5MineMap.SNOWROAD.equals(d)) m.put("needVisit", true); // [2026-10-26] 한 번 걸어서 가 본 뒤에만(화면도 그때만), [2026-10-27] 왕도도
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
        out.put("thunder", S5MineMap.WESTROAD.equals(map.id)); // [2026-11-05] 마물의 숲 입구 -- 화면이 1초마다 환경 턴(천둥 황소 장판/전체 감전)
        out.put("vein", map.veinMine()); // [2026-10-28] 설산 동굴 -- 앞의 얼음 광맥을 연속 채광
        out.put("noMap", map.noMap()); // [2026-10-28] 개미굴 -- 지도가 안 보이는 곳 // [2026-10-28] 캘 칸이 없는 맵 -- 화면이 캐기 버튼(옆에 몬스터가 있을 때만 공격 버튼)과 자동채광을 숨긴다
        if (map.outdoor()) { // [2026-11-05] "모든 필드에 밤과 낮" -- 바깥 맵은 화면이 같은 시각으로 밤을 어둡게
            out.put("outdoor", true);
            out.put("dayAnchor", S5MineMap.RESET_ANCHOR_MS);
            out.put("dayMs", S5MineMap.ARCHI_DAY_MS);
            out.put("dayCycleMs", S5MineMap.ARCHI_CYCLE_MS);
        }
        if (S5MineMap.isArchi(map.id)) { // [2026-10-28] 다도해 -- 낮 햇빛(초당 %), 배로 다니는 뱃길, 낮밤 계산(화면이 같은 시각으로)
            out.put("archi", true);
            out.put("sun", map.sun());
            out.put("boat", map.boat());
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
            if (ty.stay) m.put("stay", true); // [2026-10-28] 대주교 -- 제자리, 4칸 안에서 선공
            if (ty.minion) m.put("minion", true); // [2026-10-28] 레벨 차이 선공에서 뺀다(화면)
            if (ty.lvShow > 0) m.put("lv", ty.lvShow); // [2026-10-28] 얼음 여왕 -- 화면 레벨 Lv.95
            if (ty.boss) m.put("aggro", MINE_BOSS_AGGRO_RANGE); // [2026-10-28] 보스 선공 거리 12칸
            if (ty.ninja) m.put("ninja", true); // [2026-10-28] 은신(화면은 반투명)
            if (!ty.boss && ty.aggro > 0) m.put("aggro", ty.aggro); // [2026-11-05] 왕도 서쪽길 무리 -- 화면도 이 거리 안이면 전투 턴
            if (ty.firstStrike) m.put("first", true); // [2026-10-28] 설산 동굴 박쥐 -- 늘 선공(화면도 6칸 안이면 전투 턴)
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
        long regenAt;     // [2026-10-29] 보스 체력 회복을 마지막으로 반영한 시각
        long nearAt;      // [2026-10-30] 보스 MINE_BOSS_ALONE_RANGE칸 안에 누가 있던 마지막 시각
        MobState(int hp) { this.hp = hp; }
        /** 다시 나타날 때(체력 가득) 닌자 상태도 처음으로. */
        void respawn(int max) { deadUntil = 0; hp = max; stealth = false; stealthHits = 0; splitUntil = 0; splitStage = 0; hitters = null; }
    }

    static final java.util.concurrent.ConcurrentHashMap<String, MobState> MINE_MOB_STATE = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_LAST_ATTACK = new java.util.concurrent.ConcurrentHashMap<>();

    static String mineMobKey(S5MineMap map, int id) {
        return map.version() + ":" + id;
    }

    // [2026-10-28] "99레벨 경험치 꽉 차면 왕도 12시 성당 신부에게 환생, 환생하면 체력 1.5배, 필요 경험치 3배, 레벨 1로(Lv1(환생1)), 날개 이펙트" --
    //  환생 횟수 rb(0~5, TBOT_S5_MINE_ITEM의 REBIRTH)만큼 겹쳐 곱한다: 최대 체력 x 1.5^rb, 필요 경험치 x 3^rb. 공격력은 그대로.
    static final String MINE_ITEM_REBIRTH = "REBIRTH";
    static final int MINE_REBIRTH_MAX = 5;

    static int mineRebirth(Map<String, Integer> items) { return Math.max(0, Math.min(MINE_REBIRTH_MAX, items.getOrDefault(MINE_ITEM_REBIRTH, 0))); }
    // [2026-10-28 수정] "환생 1회당 기본 체력 +100%(x회차), 필요 경험치 5배(회차 제곱), 전투/채굴 광물 획득량 +100%(x회차), 방한/햇빛 피해 1%(x회차) 경감" -- [2026-10-29] 방한/햇빛/동굴 밝기 단계 +1(x회차)로 바꿈
    //  -> 최대 체력 x (1 + rb)([2026-10-30] x (1 + 0.5 rb)), 필요 경험치 x 2^rb([2026-10-28] 5^rb에서), 광물 획득 x (1 + rb)(mineRebirthGain), 한기/눈바람/방한 장비 더위 피해량 x (1 - 0.01 rb)(mineColdTick rbCut).
    //  [2026-10-29] 지금은 환생 회차만큼 방한 단계(mineColdDef), 햇빛 저항 단계(mineSunPct), 동굴 밝기(화면 sight) +1씩.
    /** [2026-10-30] "체력이 과하다 -- 환생당 +50%로" -- x (1 + rb)(5환생 x6)에서 x (1 + 0.5 rb)(5환생 x3.5). */
    static final double MINE_RB_HP_BONUS = 0.5;
    static int mineHpMax(int lv, int rb) { return (int) Math.round(40 * Math.pow(1.09, lv - 1) * (1 + MINE_RB_HP_BONUS * rb)); }
    /** 환생 광물 획득 배수(채굴/몬스터 광석) = 1 + 환생 횟수. */
    static int mineRebirthGain(Map<String, Integer> items) { return 1 + mineRebirth(items); }
    /** [2026-10-28] "환생 보너스 추가 광석은 미스릴 이하 광석만" -- 구리~미스릴(등급 1~5) 광석일 때만 x(1 + 회차), 별빛석/요일석/보석 등은 1배. */
    static int mineRebirthGain(Map<String, Integer> items, int grade, String code) {
        return grade >= 1 && grade <= 5 && S5MineMap.ORE_CODES[grade - 1].equals(code) ? mineRebirthGain(items) : 1;
    }
    static int mineAtk(int lv) { return (int) Math.round(6 * Math.pow(1.09, lv - 1)); }
    /** [2026-10-28] "환생 시 공격 데미지 보너스 +50%(x환생 수)" -- 공격력 x (1 + 0.5 rb). */
    static int mineAtk(int lv, int rb) { return (int) Math.round(mineAtk(lv) * (1 + MINE_RB_ATK_BONUS * rb)); }
    /** [2026-10-28] 공격력 배수 -- "환생 효과나 다른 공격력 증가와는 합연산": 1 + 환생 0.5 x 회차 + 광전사의 장갑 0.5(곱하지 않고 더한다). 양산은 고정 +10(mineAtkFlat). */
    static double mineAtkMul(Map<String, Integer> items) {
        return 1 + MINE_RB_ATK_BONUS * mineRebirth(items) + (MINE_ITEM_BERSERK_GLOVES.equals(mineGloveCode(items)) ? (items.getOrDefault(MINE_ITEM_BERSERK_GLOVES, 0) >= 2 ? 0.65 : MINE_BERSERK_MULT - 1) + (items.getOrDefault(MINE_LOWHP_FLAG, 0) > 0 ? 0.2 : 0) : 0) // [2026-11-04] 2단계
                + mineWeaponPct(items) / 100.0; // [2026-10-29] 드랍 무기 공격력 %(닌자의 검 영웅부터, 백화검) -- 합연산
    }
    static final double MINE_RB_ATK_BONUS = 0.5;
    /** [2026-10-28] "1환생당 경험치 필요량 5배에서 2배로" -- 필요 경험치 x MINE_RB_EXP_MUL^rb. */
    static final double MINE_RB_EXP_MUL = 2;
    static long mineExpNeed(int lv, int rb) { return Math.round(15 * Math.pow(1.12, lv - 1) * Math.pow(MINE_RB_EXP_MUL, rb)); }
    /** 환생할 수 있나 -- 99레벨 + 경험치 가득(다음 레벨 필요치 이상) + 아직 5번 안 함. */
    static boolean mineRebirthReady(int lv, long exp, int rb) { return lv >= MINE_LV_MAX && rb < MINE_REBIRTH_MAX && exp >= mineExpNeed(MINE_LV_MAX, rb); }

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
        return Math.max(1, Math.min(MINE_LV_MAX, intVal(row.get("M_LV"), 1)));
    }

    // [2026-10-25] "몬스터와 전투 시 최대 체력 10%씩 잠겨 최대 50%까지, 레벨업하거나 대도시 성당 신부에게 치료받으면 정상으로" --
    //  HP_LOCK(0~5), 최대 체력 = 원래 x (10 - 잠금) / 10. 처음엔 새 전투마다 +1이었으나 [2026-10-26] "죽으면 처리되어야 하는데 전투 때마다 깎인다" --
    //  몬스터에게 쓰러질 때만 +1.
    static final String MINE_ITEM_HP_LOCK = "HP_LOCK";
    static final int MINE_HP_LOCK_MAX = 5;

    static int mineHpCap(int lv, int lock, int rb) { return Math.max(1, mineHpMax(lv, rb) * (10 - Math.max(0, Math.min(MINE_HP_LOCK_MAX, lock))) / 10); }

    /** 쓰러진 뒤 붙이는 안내(잠금이 늘었을 때만). */
    static String mineLockText(HashMap<String, Object> out) {
        Object v = out.get("hpLockUp");
        return v == null ? "" : " 🔒 최대 체력이 잠겼어요(지금 -" + v + "%). 레벨업하거나 왕도 성당 신부에게 치료받으면 풀려요.";
    }

    /** 쓰러짐 -- 최대 체력 10% 더 잠금(최대 50%). 바뀐 잠금 단계를 돌려준다. */
    int mineLockUp(String userName, int lock, HashMap<String, Object> out) {
        if (lock >= MINE_HP_LOCK_MAX) return lock;
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
        int max = mineHpCap(mineLv(row), lock, rb);
        HpVirt hv = mineHpVirt(row);
        if (hv == null && row.get("M_HP") == null) return max;
        int hp = hv != null ? hv.hp : intVal(row.get("M_HP"), max);
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
        if (arg > 0) { v = Math.min(max, v + el * (double) max * arg / 100 / 1000); }
        if (calm > 0) {
            v += Math.max(0, calm - MINE_REGEN_DELAY_MS) * (double) max * mul / MINE_HP_FULL_MS;
            if (twin) v -= calm * (double) max * mineTwinDrain(gi) / 100 / 1000;
            v = Math.min(max, v); if (twin) v = Math.max(floor, v);
        }
        if (fightSpan > 0) {
            v += fightSpan * (double) max * mul * MINE_REGEN_FIGHT_MUL / MINE_HP_FULL_MS;
            if (twin) v -= fightSpan * (double) max * mineTwinDrain(gi) / 100 / 1000;
            v = Math.min(max, v); if (twin) v = Math.max(floor, v);
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
        c.put("hpBase", mineHpMax(lv, rb));
        c.put("hpLock", Math.min(MINE_HP_LOCK_MAX, lock) * 10);
        return c;
    }

    static HashMap<String, Object> mineCombatInfo(int lv, long exp, int hp, long now, int rb) {
        HashMap<String, Object> c = new HashMap<>();
        c.put("lv", lv);
        c.put("exp", exp);
        c.put("expNeed", mineExpNeed(lv, rb));
        c.put("hp", hp);
        c.put("hpMax", mineHpMax(lv, rb));
        c.put("atk", mineAtk(lv, rb));
        c.put("rebirth", rb); // [2026-10-28] 환생 횟수(화면 "Lv.1(환생1)" + 날개)
        c.put("rebirthReady", mineRebirthReady(lv, exp, rb));
        c.put("at", now);
        c.put("fullMs", MINE_HP_FULL_MS);
        return c;
    }

    /** 몬스터가 지금 있을 수 있는 칸들(멈춰 있으면 그 칸, 아니면 앞/지금/다음 시간 칸 -- 화면과 서버 시계 차이 허용). */
    static int[] mineMobCells(S5MineMap.Mob mob, MobState s, long now, S5MineMap map) {
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
        String prefix = map.version() + ":";
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
                if (minion && s.deadUntil <= now && s.frozenUntil <= now) s.deadUntil = Long.MAX_VALUE / 2; // [2026-10-28] 아무도 안 싸우면 수하는 물러난다
                if (!minion && s.deadUntil <= now && s.frozenUntil <= now && now - s.lastHitAt > MINE_MOB_HEAL_MS) { MINE_MOB_STATE.remove(e.getKey(), s); continue; }
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
        return System.currentTimeMillis() < mineBossKilledAt(key) + MINE_BOSS_RESPAWN_MS;
    }

    /** 보스 종류 -> 쓰러진 시각 설정 키(보스가 아니거나 따로 셈하는 보스면 null). */
    static String mineBossKey(int type) {
        return type == S5MineMobs.BOSS_TYPE ? MINE_BOSS_KEY : type == S5MineMobs.RAID_BOSS_TYPE ? MINE_RAID_KEY : type == S5MineMobs.ICE_BOSS_TYPE ? MINE_ICE_KEY
                : type == S5MineMobs.LAVA_BOSS_TYPE ? MINE_LAVA_KEY : type == S5MineMobs.PHARAOH_BOSS_TYPE ? MINE_PHARAOH_KEY : null;
    }

    boolean mineBossDead() {
        return mineBossDeadKey(MINE_BOSS_KEY);
    }

    // [2026-10-28] "젠은 맵 초기화 시점 1회만(보스 공통)" -- 타락한 대주교도 동굴 3층 보스처럼 주기마다 한 번(MINE_RAID_KILLED = "주기|유저").
    static final String MINE_RAID_KEY = "MINE_RAID_KILLED";

    // [2026-10-28] 용암섬 용암 거신 -- 보스 공통으로 주기마다 한 번(MINE_LAVA_KILLED = "주기|유저").
    static final String MINE_LAVA_KEY = "MINE_LAVA_KILLED";
    // [2026-11-04] 피라미드 지하 파라오 미라 -- 보스 공통(2시간 뒤 다시). 붕대 감기 = 원거리 공격에 맞으면 MINE_STUN_MS 동안 못 움직이고 못 때린다(화면 + 공격/캐기 서버도 막음).
    static final String MINE_PHARAOH_KEY = "MINE_PHARAOH_KILLED";
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
    static final int MINE_THUNDER_NEED = 500; // [2026-11-05] 밤 처치 누적
    static final long MINE_THUNDER_BOSS_MS = 60 * 60_000L; // [2026-11-05] 20분 -> 1시간
    static final String MINE_THUNDER_CFG = "MINE_THUNDER_KILLS";
    static final double MINE_THUNDER_AIR_PCT = 1.0; // [2026-11-06] 황소가 나와 있는 동안 맵 전체 초당 최대 체력 %
    static volatile boolean mineThunderLoaded = false;
    static final Object MINE_THUNDER_LOCK = new Object();
    static volatile long mineThunderNight = Long.MIN_VALUE, mineThunderDoneNight = Long.MIN_VALUE, mineThunderUntil = 0;
    static volatile boolean mineThunderKilled = false;
    static int mineThunderKills = 0;
    static boolean mineThunderOn(long now) { return now < mineThunderUntil && !mineThunderKilled; }
    /** 황소가 나와 있는 동안 마물의 숲 입구 마물(보스 제외)은 없는 것으로 본다. */
    static boolean mineHidden(S5MineMap map, S5MineMobs.Type t, long now) { return S5MineMap.WESTROAD.equals(map.id) && !t.boss && mineThunderOn(now); }

    void mineThunderKill(S5MineMap map, S5MineMobs.Type t, long now) {
        if (!S5MineMap.WESTROAD.equals(map.id) || t.boss || !S5MineMap.night(now)) return;
        mineThunderLoad();
        int k;
        synchronized (MINE_THUNDER_LOCK) {
            if (mineThunderOn(now)) return;
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
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_DASH_MULT * (0.8 + RND.nextDouble() * 0.4))));
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
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_SPEAR_MULT * (0.8 + RND.nextDouble() * 0.4))));
            for (int ly = y - 1; ly <= y + 1; ly++) for (int lx = x - 1; lx <= x + 1; lx++) if (map.inside(lx, ly)) MINE_SHOCK.merge(ly * map.w + lx, now + MINE_SPEAR_ZONE_MS, Math::max);
            h.put("thunder", "spear");
            h.put("dmg", d);
            hits.add(h);
            dmg += d;
        } else if (dist <= 1 && now >= nx[2]) { // 붙어 있으면 밀쳐내고 근접 장판(보스 둘레 반지름 3)
            nx[2] = now + 8_000L + RND.nextInt(3_000);
            int d = mineCoatHit(items, Math.max(1, (int) Math.round(atk * MINE_SLAM_MULT * (0.8 + RND.nextDouble() * 0.4))));
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
        if (type == S5MineMobs.THUNDER_BOSS_TYPE) return !mineThunderOn(System.currentTimeMillis()); // [2026-11-05] 밤 마물 200마리(MINE_THUNDER_NEED) 뒤에만
        String key = mineBossKey(type);
        return key != null && mineBossDeadKey(key);
    }

    static S5MineMap.Mob mineBossOf(S5MineMap map) {
        if (map.mobs().isEmpty()) return null;
        S5MineMap.Mob m = map.mobs().get(map.mobs().size() - 1);
        return S5MineMobs.TYPES.get(m.type).boss ? m : null;
    }

    /** 쓰러졌을 때 깨어나는 곳으로 옮기고 그곳 이름을 돌려준다(성당에서 기도했으면 왕도 성당, 아니면 평원 마을). */
    String mineRespawn(String userName, Map<String, Integer> items, long now) {
        mineDisengageAll(userName); // [2026-10-25] 쓰러지면 인식 끝
        MINE_ICE_FROZEN.remove(userName); // [2026-10-28] 얼음 여왕 빙결도 끝
        MINE_DEBUFF.remove(userName); // [2026-10-28] 여왕개미 독/슬로우도 끝
        MINE_PSTEALTH.remove(userName); // [2026-10-30] 자객의 목걸이 은신도 끝
        MINE_STUN.remove(userName); // [2026-11-04] 붕대 스턴도 끝
        MINE_HEAL.remove(userName); // [2026-10-28] 재생의 장갑 회복도 끝
        MINE_ICE_THAWED.remove(userName);
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
        return t.firstStrike || 1 + 4 * (t.tier - 1) - lv >= MINE_PREEMPT_LV_GAP; // [2026-10-28] 설산 동굴 박쥐는 늘 선공
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
        return new long[] { id, s.hp, s.deadUntil, s.fx, s.fy, s.frozenUntil, s.stealth ? 1 : 0 }; // [2026-10-28] 7번째 = 닌자 은신 중
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
                if (bs.deadUntil <= now && bs.splitUntil <= now) { // [2026-10-28] 분신술 중엔 다가가도 먼저 덤비지 않는다(들키지 않게)
                    int near = -1;
                    for (int c : mineMobCells(boss, bs, now, map)) if (Math.abs(c % map.w - x) + Math.abs(c / map.w - y) <= aggro) near = c;
                    if (near >= 0) {
                        if (bs.frozenUntil <= now) { bs.fx = near % map.w; bs.fy = near / map.w; }
                        bs.frozenUntil = Math.max(now + MINE_MOB_FREEZE_MS, bs.splitUntil); // [2026-10-28] 분신술 자리 유지
                        if (bs.target == null) bs.target = userName;
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
            if (mt.boss || mt.minion || !mineMobPreempts(mt, lv) || (e != null && e.mobs.containsKey(mb.id)) || mineHidden(map, mt, now)) continue;
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
        if (e == null || e.mobs.isEmpty()) {
            mineDisengageAll(userName);
            out.put("ok", true);
            out.put("fight", false);
            if (healed > 0) { // 싸움이 끝난 뒤에도 4초 동안은 회복을 받아 저장
                HashMap<String, Object> hrow = dao.selectMineUserForUpdate(userName);
                if (hrow != null) {
                    int hlv = mineLv(hrow), hhp = Math.min(mineHpCap(hlv, lock, rb), mineHpNow(hrow, now, lock, rb) + healed);
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
                int dist = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                if (type.boss) dist = Math.max(0, Math.max(s.fx - x, x - s.fx - 1)) + Math.max(0, Math.max(s.fy - y, y - s.fy - 1)); // [2026-10-29] 보스 2x2칸까지의 거리
                if (type.boss || type.minion ? dist >= MINE_BOSS_LEASH : dist >= (mineMobPreempts(type, lv) ? minePreemptRange(type) + 1 : MINE_AGGRO_RANGE)) { // [2026-10-29] 보스 = 50칸 // 선공 몬스터는 인식 범위(6칸) 밖에서 풀린다. [2026-10-28] 보스는 거리로는 안 풀린다(맵을 벗어나거나 쓰러져야)
                    e.mobs.remove(id);
                    released++;
                    if (userName.equals(s.target)) { s.target = null; s.frozenUntil = Math.max(now, s.splitUntil); } // 다시 제자리로 걸어간다([2026-10-28] 분신술 중엔 그 자리에)
                    mobs.add(mineMobRow(id, s));
                    continue;
                }
                if (s.target == null) s.target = userName;
                boolean splitting = s.splitUntil > now; // [2026-10-28] 닌자 두목 분신술 중엔 제자리에서 안 때린다(자세로 찾게)
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
                boolean thunderBoss = map.mobs().get(id).type == S5MineMobs.THUNDER_BOSS_TYPE;
                if (thunderBoss && dist <= MINE_BOSS_AGGRO_RANGE && hp - dmgSum > 0) { // [2026-11-05] 천둥 황소 -- 전광석화/전기창/밀쳐내기+장판
                    int tsd = mineThunderSkills(userName, map, s, map.mobs().get(id), x, y, dist, items, now, hits, out);
                    dmgSum += tsd;
                    if (tsd > 0) killerName = type.name; // [2026-11-06] "null에게 쓰러졌어요" -- 천둥 황소 기술/원거리에 쓰러지면 이름이 비어 있었다
                    if (out.get("knockX") != null) { x = (Integer) out.get("knockX"); y = (Integer) out.get("knockY"); }
                    dist = Math.max(0, Math.max(s.fx - x, x - s.fx - 1)) + Math.max(0, Math.max(s.fy - y, y - s.fy - 1));
                }
                if (!thunderBoss && type.ranged && (dist >= 2 || type.venom || map.mobs().get(id).type == S5MineMobs.LAVA_BOSS_TYPE || map.mobs().get(id).type == S5MineMobs.PHARAOH_BOSS_TYPE) && dist <= MINE_BOSS_AGGRO_RANGE && hp - dmgSum > 0) { // [2026-10-28] 용암 거신 -- 가끔 불덩이([2026-10-30] 붙어 있어도 던져 발밑을 용암으로)
                    Long nx = MINE_RANGED_NEXT.get(userName);
                    if (nx == null) MINE_RANGED_NEXT.put(userName, now + MINE_RANGED_MIN_MS / 2); // 막 인식 -- 3초 뒤 첫 불덩이
                    else if (now >= nx) {
                        MINE_RANGED_NEXT.put(userName, now + MINE_RANGED_MIN_MS + RND.nextInt((int) MINE_RANGED_RAND_MS));
                        int d = mineCoatHit(items, Math.max(1, (int) Math.round(S5MineMobs.atk(type, map.mobs().get(id).rank) * MINE_RANGED_MULT * (0.8 + RND.nextDouble() * 0.4))));
                        dmgSum += d;
                        killerName = type.name; // [2026-11-06] 원거리 공격(불덩이/독산 등)
                        HashMap<String, Object> h = new HashMap<>();
                        h.put("mobId", id);
                        h.put("dmg", d);
                        h.put("ranged", true);
                        h.put("fx", s.fx);
                        h.put("fy", s.fy);
                        hits.add(h);
                        if (type.venom) { // [2026-10-28] 여왕개미 독산 -- 슬로우 + 독
                            Debuff db = MINE_DEBUFF.computeIfAbsent(userName, u -> new Debuff());
                            db.slowUntil = now + MINE_SLOW_MS;
                            if (db.poisonUntil <= now) db.poisonAt = now;
                            db.poisonUntil = now + MINE_POISON_MS;
                            h.put("venom", true);
                            out.put("slowUntil", db.slowUntil);
                            out.put("poisonUntil", db.poisonUntil);
                        } else if (map.mobs().get(id).type == S5MineMobs.PHARAOH_BOSS_TYPE) { // [2026-11-04] 붕대 감기 -- 스턴
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
                if (dist <= 1 && !splitting && !type.venom && (shatterNow || last == null || now - last >= MINE_MOB_STRIKE_GAP_MS) && hp - dmgSum > 0) {
                    int d = mineCoatHit(items, Math.max(1, (int) Math.round(S5MineMobs.atk(type, map.mobs().get(id).rank) * mineNightAtk(map, type, now) * (0.8 + RND.nextDouble() * 0.4)))); // [2026-11-05] 마물의 숲 밤 2배 // [2026-10-29] 개체 단계, [2026-10-26] 방한 외투 = 2배([2026-10-28] 갑옷 1.5배)
                    boolean x2 = s.stealth && map.mobs().get(id).type != S5MineMobs.SHRINE_BOSS_TYPE; // [2026-10-28] 은신했던 닌자의 기습 -- 2배(닌자 두목은 은신을 유지한 채 보통 세기)
                    if (x2) { d *= MINE_NINJA_STEALTH_MULT; s.stealth = false; }
                    if (iceHeld && id == boss.id) { d = Math.max(d, hp - dmgSum); shattered = true; } // [2026-10-28] 언 채로 맞으면 즉사
                    boolean dodged = !(iceHeld && id == boss.id) && minePStealthDodge(userName, now); // [2026-10-30] 자객의 목걸이 은신 -- 일반 공격 회피
                    if (dodged) d = 0;
                    dmgSum += d;
                    e.mobs.put(id, now);
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("mobId", id);
                    h.put("dmg", d);
                    if (dodged) h.put("dodge", true);
                    if (x2) h.put("x2", true);
                    hits.add(h);
                    killerName = type.name;
                }
                mobs.add(mineMobRow(id, s));
            }
        }
        long[] mercR = mineMercTurn(userName, map, items, x, y, now, e, out); // [2026-11-05] 용병 -- {처치 수, 경험치}
        long mercExp = mercR[1];
        Debuff pdb = MINE_DEBUFF.get(userName); // [2026-10-28] 여왕개미 독 -- 전투 턴마다 지난 시간만큼
        if (pdb != null && pdb.poisonUntil > pdb.poisonAt && hp - dmgSum > 0) {
            long until = Math.min(now, pdb.poisonUntil), el = Math.min(2_000L, until - pdb.poisonAt);
            pdb.poisonAt = until;
            if (el > 0) {
                int pd = Math.max(1, (int) (mineHpCap(mineLv(row), lock, rb) * MINE_POISON_PCT / 100 * el / 1000.0));
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
        if (boss != null && boss.type == S5MineMobs.ICE_BOSS_TYPE && e.mobs.containsKey(boss.id)) mineIceAwakeUntil = now + MINE_ICE_AWAKE_MS; // [2026-10-28] 얼음 여왕이 깨어 있다(빙결)
        out.put("ok", true);
        out.put("hits", hits);
        out.put("mobs", mobs);
        out.put("pStealth", minePStealthOf(userName, now)); // [2026-10-30] 자객의 목걸이 -- 남은 회피/끝나는 시각(없으면 null)
        if (S5MineMap.ISLE7.equals(map.id)) out.put("lava", mineLavaCells(map, now)); // [2026-10-28] 용암 칸
        String rwMsg = MINE_SHRINE_REWARD_MSG.remove(userName); // [2026-10-28] 다른 사람이 닌자 두목을 쓰러뜨렸을 때 함께 싸운 보상 소식
        if (rwMsg != null) { out.put("rewardMsg", rwMsg); out.put("ores", mineOreList(userName)); }
        boolean fight = !e.mobs.isEmpty();
        if (dmgSum == 0 && healed == 0 && mercExp == 0 && fight && !out.containsKey("bossAggro") && !out.containsKey("preempt")) { out.put("fight", true); out.put("combat", mineCombatInfo(lv, exp, hp, now, true, lock, rb)); return out; } // 방금 인식됐으면 그때까지 찬 체력을 저장
        if (dmgSum > 0 && mineFrozen(userName, now) != null && hp + healed - dmgSum > 0) { dmgSum = hp + healed; shattered = true; } // [2026-11-04] "빙결 상태에서 맞으면 즉사" -- 얼음 여왕뿐 아니라 어떤 공격이든
        boolean save = mineHpFlushDue(row, now) || hp + healed - dmgSum <= 0 || mercExp > 0; // [2026-11-05] 용병이 잡은 경험치는 바로 저장 // [2026-11-03] 체력만 바뀌면 메모리에(MINE_HP_VIRT), 쓰러지거나 15초마다 DB
        HashMap<String, Object> lrow = save ? dao.selectMineUserForUpdate(userName) : null; // 쓰기 전에 같은 유저 요청 직렬화
        if (lrow != null) { // [2026-10-28] 잠근 뒤 다시 읽는다 -- 그 사이 들어온 공격(처치/레벨업/반격)이 바꾼 레벨/경험치/체력을 옛 값으로 덮어쓰지 않게
            lv = mineLv(lrow);
            exp = Math.max(0, longOf(lrow.get("M_EXP"), 0L));
            hp = mineHpNow(lrow, now, lock, rb);
        }
        if (mercExp > 0) { // [2026-11-05] 용병이 쓰러뜨린 몬스터 경험치 -- 내 것으로
            mineMercShare(userName, mercExp, lv, mercR[0], out); // [2026-11-06] 용병 경험치(용병 처치 -- 몬스터 레벨 대신 내 레벨로, 처치마다 최소 1)
            long[] mg = mineAuraExp(userName, lv, exp, hp, lock, rb, mercExp, false, out);
            lv = (int) mg[0]; exp = mg[1]; hp = (int) mg[2]; lock = (int) mg[3];
            if (mg[4] > 0) out.put("levelUp", (int) mg[4]);
        }
        if (healed > 0) { hp = Math.min(mineHpCap(lv, lock, rb), hp + healed); out.put("healed", healed); } // [2026-10-28] 재생의 장갑
        if (dmgSum > 0) {
            if (mineVeinBreak(userName)) out.put("veinBroken", true); // [2026-10-28] 설산 동굴 -- 맞으면 연속 채광이 끊긴다
            if (MINE_ITEM_REGEN_GLOVES.equals(mineGloveCode(items))) mineHealAdd(userName, (int) Math.round(dmgSum * mineRegenPct(items)), now); // [2026-10-28] 재생의 장갑
            if (shattered) dmgSum = Math.max(dmgSum, hp); // [2026-11-04] 다시 읽은 체력 기준으로도 즉사
            hp -= dmgSum;
            if (hp <= 0) {
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCap(lv, lock, rb);
                mineDisengageAll(userName);
                fight = false;
                out.put("died", true);
                out.put("message", (shattered ? "🧊 얼어붙은 채로 " + (killerName == null ? "" : killerName + "의 ") + "일격을 맞아 산산조각 났어요... " : killerName == null ? "💫 쓰러졌어요... " : "💫 " + killerName + "에게 쓰러졌어요... ") + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out));
                if (shattered) out.put("shattered", true);
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
            if (!out.containsKey("died") && dmgSum > 0) dao.updateMinePos(userName, map.id, x, y, now);
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
        if (mineTokenRoll(userName, S5MineMap.SEAL_CODE)) { // [2026-10-30] 증표 -- 처치마다 MINE_TOKEN_PCT%(예전: 첫 처치 1번)
            drops.add(mineDrop(S5MineMap.SEAL_GRADE, S5MineMap.SEAL_NAME, 1));
            msg.append(" · 📜 ").append(S5MineMap.SEAL_NAME).append(" 1개!!");
        }
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
    //   - 여왕이 언 사람을 때리면 즉사(남은 체력 전부 -- 몬스터에게 쓰러진 것과 같아 최대 체력 잠금 +1).
    //   - 얼지 않은 다른 플레이어가 언 사람 2칸 안(위치 알림 기준)에 오면 풀린다(얼음땡). 혼자면 15초 뒤 저절로 녹는다(갇히지 않게).
    //   판정은 봉우리에 있는 모든 화면이 1초마다 부르는 환경 턴(mineColdTick)에서 한다. 언 동안엔 한기/눈바람 피해가 없다.
    //  보스 공통: 맵 주기마다 한 번(MINE_ICE_KILLED = "주기|유저"). 보상: 얼음의 심장 1(반드시) + 50% 다이아몬드 2 + 40% 별빛석 1.
    static final String MINE_ICE_KEY = "MINE_ICE_KILLED";
    static final long MINE_ICE_AWAKE_MS = 15_000L;
    static final long MINE_ICE_FIRST_MS = 8_000L, MINE_ICE_PERIOD_MS = 18_000L, MINE_ICE_FREEZE_MS = 15_000L;
    static final int MINE_ICE_RESCUE_RANGE = 2;
    static final long MINE_ICE_FRESH_MS = 6_000L; // [2026-11-04] 3초 -> 6초(통신이 느린 사람도 빙결 대상)
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
        return mineBossDeadKey(MINE_ICE_KEY);
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
    boolean mineTokenRoll(String userName, String code) {
        if (RND.nextInt(100) >= MINE_TOKEN_PCT) return false;
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
        if (S5MineMap.archiDay(System.currentTimeMillis()) && mineTokenRoll(userName, S5MineMap.VOLCANO_HEART_CODE)) { // [2026-10-30] "낮에 죽이면 화산의 심장(확률)"
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
        if (mineStunned(userName, now)) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("stunned", true); so.put("message", "🩹 붕대에 묶여 움직일 수 없어요!"); return so; } // [2026-11-04]
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
        if (mineBossDead(mob.type)) { out.put("message", "이번 맵에서는 이미 쓰러진 보스예요. 다음 맵 리셋 때 다시 나타나요."); out.put("mobId", mobId); out.put("mobDeadUntil", Long.MAX_VALUE / 2); return out; }
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
        int[] aura = mineAuraActive(items); // [2026-11-04] 검기 -- 바로 앞(때린) 적에게도 속성 피해
        int auraRange = mineAuraRange(aura);
        double atkBase = mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items);
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
            if (type.boss) mineBossRegen(s, mobMax, now, map, mob); // [2026-10-29] 인식이 풀려 있던 동안 회복한 만큼
            int hitCell = -1;
            for (int c : mineMobCells(mob, s, now, map)) { // [2026-10-29] 보스는 2x2칸, "공격할 때 본인 위치도" -- 내 칸(0)과 둘레 8칸(1)
                int fw = type.boss ? 2 : 1;
                for (int oy = 0; oy < fw; oy++) for (int ox = 0; ox < fw; ox++)
                    if (Math.max(Math.abs(c % map.w + ox - x), Math.abs(c / map.w + oy - y)) <= 1 && (hitCell < 0 || c == mc)) hitCell = c;
            } // 화면이 보던 칸 우선. [2026-10-28] 대각선 포함 8방향
            if (hitCell < 0) { out.put("message", type.name + "에게 닿지 않아요. 바로 옆에서 공격하세요."); out.put("far", true); return out; }
            crit = RND.nextInt(100) < 10;
            dmg = Math.max(1, (int) Math.round((mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items)) * (0.85 + RND.nextDouble() * 0.3) * (crit ? 1.6 : 1.0) * mineElemMul(items, type))); if (mineElemMul(items, type) > 1) out.put("elHit", true);
            if (MINE_ITEM_TWIN_GLOVES.equals(mineGloveCode(items))) { // [2026-10-30] 쌍격의 장갑 -- 55%씩 두 번(타격마다 치명 15%), 화면엔 숫자 두 개
                double base = (mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items)) * mineElemMul(items, type) * mineTwinPct(items);
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
                counter = Math.max(1, (int) Math.round(S5MineMobs.TYPES.get(S5MineMobs.SHRINE_BOSS_TYPE).atk() * MINE_CLONE_TRAP_MULT * (0.8 + RND.nextDouble() * 0.4)));
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
                if (auraRange > 0) auraMain = mineAuraHits(aura, atkBase, type, 1);
                s.lastHitAt = now;
                out.put("dummyHit", true);
            } else {
                s.hp -= dmg;
                if (auraRange > 0) { auraMain = mineAuraHits(aura, atkBase, type, 1); for (int[] h : auraMain) s.hp -= h[1]; } // [2026-11-04] 검기 속성 피해
                s.lastHitAt = now;
                if (shrineBoss) { if (s.hitters == null) s.hitters = new java.util.HashSet<>(); s.hitters.add(userName); }
                if (s.hp <= 0) {
                    killed = true;
                    s.hp = 0;
                    // 보스(동굴 3층/타락한 대주교)는 다음 맵 리셋까지, [2026-10-28] 대주교 수하는 다시 불러낼 때까지, 닌자 두목은 10분 뒤(보상은 한 사람에 한 번)
                    s.deadUntil = shrineBoss ? now + MINE_SHRINE_BOSS_RESPAWN_MS : type.boss ? now + MINE_BOSS_RESPAWN_MS : type.minion ? Long.MAX_VALUE / 2 : now + type.respawnMs(); // [2026-10-28] 보스 2시간
                    s.frozenUntil = 0;
                    splitEnd = s.splitUntil > now;
                    s.splitUntil = 0;
                    s.stealth = false;
                    if (shrineBoss) bossHitters = s.hitters == null ? new java.util.HashSet<>() : new java.util.HashSet<>(s.hitters);
                } else {
                    s.fx = hitCell % map.w;
                    s.fy = hitCell / map.w;
                    s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                    counter = type.venom ? 0 : Math.max(1, (int) Math.round(S5MineMobs.atk(type, mob.rank) * mineNightAtk(map, type, now) * (0.8 + RND.nextDouble() * 0.4))); // [2026-10-30] "여왕개미는 원거리만" -- 근접 반격 없음(독산만)
                    if (s.stealth) { counter *= MINE_NINJA_STEALTH_MULT; s.stealth = false; stealthHit = true; } // [2026-10-28] 은신했던 닌자의 기습
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
            mineThunderKill(map, type, now); // [2026-11-05] 밤 마물 처치 수
            if (mob.type == S5MineMobs.THUNDER_BOSS_TYPE) { // [2026-11-05] 천둥 황소 처치 -- 마물이 돌아온다
                mineThunderEnd(map, now);
                out.put("shock", Collections.emptyList());
                out.put("bossKilled", true);
            }
            boolean wasReady = mineRebirthReady(lv, exp, rb);
            int mindPct = mineMindExpPct(items); // [2026-10-28] 정신의 목걸이
            long baseExp = mineLuckExp(items, mineNightExp(map, type, S5MineMobs.exp(type, mob.rank), now), now), expGain = baseExp + baseExp * mindPct / 100; // [2026-11-05] 마물의 숲 밤 1.5배 // [2026-10-29] 개체 단계
            exp += expGain;
            mineMercShare(userName, expGain, type.lv(), 1, out); // [2026-11-06] 용병 경험치 = 내 경험치의 1%(레벨 차이 보정, 처치마다 최소 1)
            while (lv < MINE_LV_MAX && exp >= mineExpNeed(lv, rb)) { exp -= mineExpNeed(lv, rb); lv++; levelUp++; }
            // [2026-10-28] 99레벨에선 경험치를 다음 레벨 필요치(= 환생 조건)에서 멈춘다(막대가 가득 찬 채로, 숫자가 끝없이 쌓이지 않게)
            if (lv >= MINE_LV_MAX) exp = Math.min(exp, mineExpNeed(MINE_LV_MAX, rb));
            if (!wasReady && mineRebirthReady(lv, exp, rb)) out.put("rebirthReadyNow", true);
            if (levelUp > 0) { // 레벨이 오르면 체력이 다 차고 [2026-10-25] 잠긴 최대 체력도 풀린다
                hp = mineHpMax(lv, rb);
                if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.remove("hpLockUp"); out.put("hpLockFree", true); }
            }
            msg.append("⚔️ ").append(type.name).append("을(를) 쓰러뜨렸어요! 경험치 +").append(expGain).append(mindPct > 0 ? " (📿+" + mindPct + "%)" : "");
            if (levelUp > 0) msg.append(" 🎉 레벨 업! Lv.").append(lv);
            if (out.containsKey("rebirthReadyNow")) msg.append(" ✨ 경험치가 가득 찼어요! 왕도 12시 방향 성당의 신부님께 가면 환생할 수 있어요.");
            out.put("expGain", expGain);
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
            mineWeaponDrop(userName, items, type, mob.rank, msg, out); // [2026-11-04] 검기로 쓰러뜨린 몬스터도 같은 규칙이라 따로 뺐다
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
            List<HashMap<String, Object>> killDrops = type.noReward ? new ArrayList<HashMap<String, Object>>() : shrineBoss ? mineShrineBossDrops(userName, map, type, bossHitters, msg)
                    : mob.type == S5MineMobs.RAID_BOSS_TYPE ? mineRaidDrops(userName, msg) : mob.type == S5MineMobs.ICE_BOSS_TYPE ? mineIceDrops(userName, msg) : mob.type == S5MineMobs.LAVA_BOSS_TYPE ? mineLavaDrops(userName, msg) : mob.type == S5MineMobs.PHARAOH_BOSS_TYPE ? mineDesertReward(userName, msg) : mob.type == S5MineMobs.THUNDER_BOSS_TYPE ? mineThunderDrops(userName, msg) : type.boss ? mineBossDrops(userName, msg) : mineMobDrops(userName, map, type, msg, mob.rank);
            if (type.boss && !type.noReward) mineBossShine(userName, killDrops, msg); // [2026-11-05] 보스 공통 -- 달빛석/별빛석 확률
            out.put("drops", killDrops);
            out.put("ores", mineOreList(userName));
            if (type.boss) { // [2026-11-05] 왕도 소문 -- 혼자서 / 일행이
                int fighters = 0;
                for (MineEngage me2 : MINE_ENGAGE.values()) if (map.version().equals(me2.mapVer) && now - me2.at <= MINE_ENGAGE_TTL_MS && me2.mobs.containsKey(mob.id)) fighters++;
                mineGossipAdd(mineNick(userName) + (fighters <= 1 ? " 님이 혼자서 " : " 님 일행이 ") + type.name + "을(를) 쓰러뜨렸대요!");
            }
        } else {
            counter = mineCoatHit(items, counter); // [2026-10-26] 방한 외투 -- 받는 피해 2배([2026-10-28] 갑옷 1.5배)
            if (counter > 0 && mineVeinBreak(userName)) out.put("veinBroken", true); // [2026-10-28] 설산 동굴 -- 반격에 맞아도 끊긴다
            if (counter > 0 && MINE_ITEM_REGEN_GLOVES.equals(mineGloveCode(items))) mineHealAdd(userName, (int) Math.round(counter * mineRegenPct(items)), now); // [2026-10-28] 재생의 장갑
            hp -= counter;
            out.put("counter", counter);
            if (hp <= 0) {
                died = true;
                lock = mineLockUp(userName, lock, out); // [2026-10-26] 쓰러지면 최대 체력 잠금 +1
                hp = mineHpCap(lv, lock, rb);
                msg.append("💫 ").append(type.name).append("에게 쓰러졌어요... ").append(mineRespawn(userName, items, now)).append(" 깨어났어요.").append(mineLockText(out));
            }
        }
        if (killed) { MineEngage me = MINE_ENGAGE.get(userName); if (me != null) me.mobs.remove(mobId); }
        if (auraMain != null) out.put("aura", auraMain);
        if (auraRange > 0 && !evade && !cloneHit) { // [2026-11-04] 검기 -- 바라보는 방향 앞 사거리 칸의 다른 몬스터
            StringBuilder am = new StringBuilder();
            long[] ar = mineAuraSweep(userName, map, items, aura, auraRange, atkBase, x, y, dir, mobId, now, am, out);
            if (ar[0] > 0) {
                mineMercShare(userName, ar[1], lv, ar[0], out); // [2026-11-06] 용병 경험치(검기 처치)
                long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, ar[1], died, out);
                lv = (int) g[0]; exp = g[1]; hp = (int) g[2]; lock = (int) g[3]; levelUp += (int) g[4];
                kills += (int) ar[0];
            }
            if (am.length() > 0) msg.append(msg.length() > 0 ? " " : "").append(am);
        }
        if (!died) { int kh = mineRegenKillHeal(items, kills, mineHpMax(lv, rb)); if (kh > 0) { hp = Math.min(mineHpCap(lv, lock, rb), hp + kh); out.put("killHeal", kh); } } // [2026-11-04] 재생의 장갑 2단계
        if (!died) dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, lv, exp, hp, now, kills);
        out.put("levelUp", levelUp);
        out.put("died", died);
        out.put("combat", mineCombatInfo(lv, exp, hp, now, mineEngaged(userName, now), lock, rb));
        out.put("healUntil", mineHealUntil(userName)); // [2026-10-28] 재생의 장갑 -- 이때까지 화면이 전투 턴을 부른다
        if (msg.length() > 0) out.put("message", msg.toString());
        if (died) out.put("state", mineState(userName));
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
        List<HashMap<String, Object>> drops = new ArrayList<>();
        if (S5MineMap.WESTROAD.equals(map.id)) { // [2026-11-05] "그 맵은 몬스터가 광석을 안 떨어뜨려도 된다" -- 처치마다 DB에 쓰던 것도 없어진다(경험치는 공격마다 한 번에)
            // [2026-11-06] "보석 드랍 1% 확률, 모든 보석" -- 땅 보석 9종 + 바다 보석 5종 중 하나(고르게), 행운의 주문서면 2%. 맞을 때만 아이템을 읽고 쓴다.
            int roll = RND.nextInt(10000);
            if (roll >= MINE_WEST_GEM_P10K * 2 || (roll >= MINE_WEST_GEM_P10K && !mineLuckOn(mineItems(userName), System.currentTimeMillis()))) return drops;
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
        int gi = S5MineMap.gemForMob(map.id, RND.nextInt(1000) / dm, 1.3 * MINE_RANK_GEM_MUL[rank]);
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
            MineWeapon hw = mineWeaponOn(mineItems(userName));
            if (hw != null && mineLv(row) < hw.lv) { dao.upsertMineItem(userName, MINE_ITEM_WEAPON_SLOT, 0); out.put("weaponOffMsg", "🗡️ " + hw.name + "은(는) Lv." + hw.lv + "부터라 칼로 바꿔 들었어요."); }
            MineWeapon ha = mineArmorOn(mineItems(userName)); // [2026-11-06] 갑옷도
            if (ha != null && mineLv(row) < ha.lv) { dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, 0); out.put("weaponOffMsg", "🛡️ " + ha.name + "은(는) Lv." + ha.lv + "부터라 벗었어요."); }
        }
        putMinePick(out, minePickDur(row), minePickMax(row));
        out.put("pickUpgrades", minePickUpgradeList());
        out.put("pickCap", MINE_PICK_CAP); // [2026-10-24] 최대 내구도 상한
        out.put("nextResetAt", S5MineMap.nextResetAt(now));
        // [2026-10-28] "보스가 안 나온다, 맵 초기화 때 다시 나와야, 전체 지도에도 표기" -- 보스는 주기마다 한 번(잡히면 다음 리셋 때 다시).
        //  전체 지도가 보스 맵에 지금 있는지 / 다음에 나오는 시각을 보여 준다.
        List<HashMap<String, Object>> bosses = new ArrayList<>();
        for (String bm : new String[] { S5MineMap.CAVE3, S5MineMap.RUINS, S5MineMap.PEAK, S5MineMap.ISLE7 }) { // [2026-10-28] 설산 봉우리(얼음 여왕), 용암섬(용암 거신)
            S5MineMap.Mob bmob = mineBossOf(S5MineMap.get(bm));
            if (bmob == null) continue;
            HashMap<String, Object> b = new HashMap<>();
            b.put("map", bm);
            b.put("name", S5MineMobs.TYPES.get(bmob.type).name);
            b.put("alive", !mineBossDead(bmob.type));
            String bk = mineBossKey(bmob.type);
            b.put("nextAt", bk == null ? S5MineMap.nextResetAt(now) : mineBossKilledAt(bk) + MINE_BOSS_RESPAWN_MS); // [2026-10-28] 쓰러지고 2시간 뒤
            bosses.add(b);
        }
        if (mineArchiOpen(userName)) { // [2026-10-28] 닌자 두목 -- 맵 리셋이 아니라 쓰러지고 10분 뒤 다시(전체 지도에 다음 시각)
            S5MineMap sbm = S5MineMap.get(S5MineMap.SHRINE_BOSS);
            S5MineMap.Mob smob = mineBossOf(sbm);
            if (smob != null) {
                MobState ss = MINE_MOB_STATE.get(mineMobKey(sbm, smob.id));
                long du = ss == null ? 0 : ss.deadUntil;
                HashMap<String, Object> b = new HashMap<>();
                b.put("map", S5MineMap.SHRINE_BOSS);
                b.put("name", S5MineMobs.TYPES.get(smob.type).name);
                b.put("alive", du <= now);
                b.put("nextAt", du);
                bosses.add(b);
            }
        }
        out.put("bosses", bosses);
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
        { List<String> pay = new ArrayList<>(); mineMercPayroll(userName, items, System.currentTimeMillis(), pay); if (!pay.isEmpty()) out.put("mercPay", pay); out.put("merc", mineMercInfo(userName, items, System.currentTimeMillis())); out.put("mercs", mineMercCards(items)); } // [2026-11-06] 미접속 동안 밀린 급여도 // [2026-11-06] 용병 관리(직업별 3칸) // [2026-11-05] 용병(시험 중 -- 관리자만)
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
        out.put("visitedArchi", items.getOrDefault(MINE_ITEM_VISIT_ARCHI, 0) > 0 || isMineAdmin(userName)); // [2026-10-28] 다도해 마을
        out.put("coatDef", mineCoatDef(items)); // [2026-10-26] 입은 방한 외투의 한기 방어 단계(0 = 안 입음)
        out.put("luckUntil", mineLuckUntil(items)); // [2026-11-05] 행운의 주문서
        Object[] nt = mineNotice(); // [2026-10-28] 아직 안 본 광산 소식(확인을 누르면 mineNoticeAck, 글은 /광산공지로)
        if (!((String) nt[1]).isEmpty() && items.getOrDefault(MINE_ITEM_NOTICE, 0) < (Integer) nt[0]) {
            out.put("noticeId", nt[0]);
            out.put("notice", nt[1]);
        }
        if (isMineAdmin(userName)) { // [2026-10-24] 관리자 전용 좌표 이동(화면 좌표 클릭) -- 고를 수 있는 맵 목록
            List<HashMap<String, Object>> maps = new ArrayList<>();
            for (String id : new String[] { S5MineMap.FIELD, S5MineMap.CAVE1, S5MineMap.DESERT, S5MineMap.BEACH, S5MineMap.DUNGEON, S5MineMap.CASTLE, S5MineMap.ISLAND, S5MineMap.NORTH, S5MineMap.STEPPE, S5MineMap.CITY, S5MineMap.CAVE2, S5MineMap.CAVE3, S5MineMap.SEAROAD,
                    S5MineMap.SNOW1, S5MineMap.SNOW2, S5MineMap.SNOW3, S5MineMap.SNOWTOWN, S5MineMap.ROYALROAD, S5MineMap.RUINS, S5MineMap.SNOWROAD, S5MineMap.PEAK,
                    S5MineMap.ANTNEST, S5MineMap.PYRAMID, S5MineMap.SNOWCAVE, S5MineMap.SNOWCAVE2, S5MineMap.CAVE4, S5MineMap.PYRAMID_UNDER, S5MineMap.WESTROAD }) { // [2026-10-28] 개미굴/피라미드/설산 동굴도(전체 지도에서 눌러 이동)
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
            out.put("adminMaps", maps);
        }
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

    @Override
    @Transactional
    public HashMap<String, Object> mineDig(String userName, String mapId, String ver, int x, int y, String dir) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        if (mineStunned(userName, now)) { HashMap<String, Object> so = new HashMap<>(); so.put("ok", false); so.put("stunned", true); so.put("message", "🩹 붕대에 묶여 움직일 수 없어요!"); return so; } // [2026-11-04]
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
        if (gi < 0 && S5MineMap.isArchiLand(map.id) && RND.nextInt(1000) < S5MineMap.SEA_GEM_PERMILLE) {
            int sg = S5MineMap.seaGemFor(RND.nextInt(100));
            dao.addUserOre(userName, S5MineMap.SEA_GEM_CODES[sg], 1);
            r.put("gemGrade", S5MineMap.SEA_GEM_GRADE_BASE + sg);
            r.put("gemName", S5MineMap.SEA_GEM_NAMES[sg]);
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
        if (S5MineMap.SEAROAD.equals(target.id) && water < 1) { // [2026-10-24] 바닷길은 용왕의 반지가 있어야 -- 없으면 그냥 물(아무 말 없이)
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("silent", true);
            return out;
        }
        // [2026-10-24] 초원/왕도는 관리자만이었다가 [2026-10-27] "왕도 일반 플레이어들에게 오픈" -- 막지 않는다.
        String block = S5MineMap.isArchi(target.id) && !mineArchiMapOpen(userName, target.id) // [2026-10-28] 다도해 -- 본섬만 모두에게, 나머지는 운영자만
                ? (S5MineMap.isShrineFloor(target.id) || S5MineMap.SHRINE_BOSS.equals(target.id) ? "🔒 신궁은 아직 준비 중이에요. 곧 열려요!" : "🌊 아직 열리지 않은 바다예요.") : mineMapBlock(target, items, mineLv(row));
        if (block != null) {
            dao.updateMinePos(userName, map.id, x, y, now);
            out.put("locked", true);
            out.put("message", block);
            return out;
        }
        int ax = ship ? target.dockX() : tele ? target.spawnX : p.tx, ay = ship ? target.dockY() : tele ? target.spawnY : p.ty;
        boolean landOk = mineWalk(target, ax, ay, water); // [2026-10-28] "섬에서 뱃길로 나오면 그 섬 입구 쪽에" -- 뱃길(배로 물 위)은 물 칸도 내리는 칸(예전엔 걸을 칸이 아니라고 시작점으로 보냈다)
        int tx = landOk ? ax : target.spawnX, ty = landOk ? ay : target.spawnY;
        dao.updateMinePos(userName, target.id, tx, ty, now);
        mineDisengageAll(userName); // [2026-10-25] 다른 맵으로 가면 인식 끝
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
        MINE_ICE_FROZEN.remove(userName); // [2026-10-28] 관리자 이동 -- 얼어 있었으면 녹인다(다른 맵에서도 막히지 않게)
        out.put("ok", true);
        out.put("message", "🛠️ " + map.name + " (" + p[0] + ", " + p[1] + ")로 이동했어요." + (p[0] != x || p[1] != y ? " (입력한 칸에 설 수 없어 가장 가까운 칸으로)" : ""));
        out.put("state", mineState(userName));
        return out;
    }

    // ---- [2026-10-28] 설산 동굴 연속 채광 -- 바라보는 얼음 광맥(I)을 캘 때마다 한 번, MINE_VEIN_NEED번 이어서 캐면 빙석 1개(다시 0부터).
    //  몬스터에게 맞으면(전투 턴 / 반격) 0으로 돌아간다(mineVeinBreak). 곡괭이 내구도는 쓰지 않는다. 숫자는 서버 메모리(맵이 바뀌면 처음부터).
    static final int MINE_VEIN_NEED = 20;
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
        if (mineLv(row) < MINE_MIN_LV.getOrDefault(S5MineMap.ANTNEST, 41)) return out; // [2026-11-04] 방벽 경비병 -- 저레벨은 빠지지 않는다(바람에 밀려 들어가도)
        Map<String, Integer> items = mineItems(userName);
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
    @Override
    @Transactional
    public HashMap<String, Object> mineArtisan(String userName, int gemIdx) {
        if (!mineAccess(userName)) return mineClosed();
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!mineAtNpc(row, "artisan")) return mineGearResult(userName, false, "왕도 장인에게서만 할 수 있어요.");
        int dur = minePickDur(row), max = minePickMax(row);
        if (max < MINE_ARTISAN_MIN) return mineGearResult(userName, false, "🔨 곡괭이 최대 내구도가 " + MINE_ARTISAN_MIN + " 이상이어야 해요. (지금 " + max + "  대장장이에게서 먼저 올려 주세요)");
        if (gemIdx < 0 || gemIdx >= MINE_CUT_KINDS || dao.takeUserOre(userName, mineCutDst(gemIdx), 1) == 0)
            return mineGearResult(userName, false, "세공된 보석이 있어야 해요. (보석 세공사에게서 만들 수 있어요)");
        dao.addMinePickMax(userName, MINE_ARTISAN_GAIN);
        HashMap<String, Object> out = mineGearResult(userName, true, "🔨 " + mineCutDstName(gemIdx) + "을(를) 곡괭이에 박아 넣었어요! 최대 내구도 +" + MINE_ARTISAN_GAIN
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
        if (now < mineHermitBackAt) return null;
        long gen = mineHermitGen;
        java.util.Random r = new java.util.Random(gen * 7919L + 20261031L);
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
        return new String[][] { { S5MineMap.SEAL_CODE, "1" }, { S5MineMap.ICE_HEART_CODE, "1" }, { S5MineMap.VOLCANO_HEART_CODE, "1" }, { S5MineMap.DESERT_HEART_CODE, "1" }, { w.shard, String.valueOf(g >= 3 ? 25 * (g - 2) : 5 * (g + 1)) } }; // [2026-10-31] 현재 등급에 비례: 일반 5 / 고급 10 / 희귀 15 / 영웅 25 / 전설 50 / 유물 75 / 고대 100
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

    /** action=list: 가진 무기와 재료 / action=do: 등급업. */
    @Override
    @Transactional
    public HashMap<String, Object> mineHermit(String userName, String action, String code) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (row == null) return out;
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
            long backAt;
            synchronized (MINE_HERMIT_LOCK) { // [2026-10-31] 먼저 올린 한 사람만 -- 그 즉시 6시간 사라진다(세대가 바뀌어 다음엔 다른 곳에)
                if (now < mineHermitBackAt) { out.put("message", "...(아무도 없다)"); return out; }
                backAt = now + MINE_HERMIT_GONE_MS;
                mineHermitBackAt = backAt;
                mineHermitGen++;
            }
            dao.upsertConfigValue(MINE_HERMIT_KEY, backAt + "|" + mineHermitGen, "web mine hermit -- back at (epoch ms)|generation");
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
        if (mineFrozen(userName, now) != null) { out.put("ok", false); out.put("message", MINE_FROZEN_BLOCK); return out; } // [2026-10-28] 얼음 여왕 빙결
        if (mineEngaged(userName, now)) { out.put("ok", false); out.put("message", MINE_FIGHT_BLOCK); return out; } // [2026-10-25]
        HashMap<String, Object> vr = dao.selectMineUser(userName);
        if (vr != null && S5MineMap.ANTNEST.equals(strVal(vr.get("MAP_ID"), ""))) { // [2026-10-28] "개미굴에선 마을 이동 안 됨" -- 나가는 굴을 찾아야 한다
            out.put("ok", false);
            out.put("message", "🐜 개미굴에서는 마을로 갈 수 없어요! 빛이 새어 드는 나가는 굴을 찾아야 해요. (어딘가 가장자리에 세 곳)");
            return out;
        }
        // [2026-10-25] "부활 거점을 골랐는데 마을 이동을 누르면 거점으로 안 간다" -- 성당에서 기도했으면 왕도 성당으로(mineRespawn과 같은 곳)
        String where = mineRespawn(userName, mineItems(userName), now);
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

    /** 외투를 입었을 때 받는 피해(2배, 3단계 갑옷은 1.5배). */
    static int mineCoatHit(Map<String, Integer> items, int v) {
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
        int atkAt(int g) { return (int) Math.round(armor ? atk * Math.pow(MINE_ARMOR_GRADE_MUL, g) : atk * (1 + step * g)); }
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
            } else {
                int p = pctAt(g), pe = pctEnh(g, e);
                if (p > 0) s += " · 공격력 +" + (p + pe) + "%" + (pe > 0 ? " (등급 " + p + "% + 강화 " + pe + "%)" : "");
            }
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
    static final MineWeapon[] MINE_ARMORS = {
        new MineWeapon("ARMOR_CHAIN", "사슬 갑옷", 1, 20, 8, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[0], true),
        new MineWeapon("ARMOR_SAMURAI", "무사의 흉갑", 2, 40, 16, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[1], true),
        new MineWeapon("ARMOR_NINJA", "닌자의 갑주", 3, 60, 34, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[2], true),
        new MineWeapon("ARMOR_BAEKHWA", "백화 갑주", 4, 80, 72, 0.2, 7, -1, 0, S5MineMap.ARMOR_SHARD_CODES[3], true),
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
        if (ms.contains(S5MineMap.ISLE1) || ms.contains(S5MineMap.ISLE2) || ms.contains(S5MineMap.ISLE3) || ms.contains(S5MineMap.ISLE4) || ms.contains(S5MineMap.ISLE5) || ms.contains(S5MineMap.ISLE6) || ms.contains(S5MineMap.ISLE7)) out.add(mineArmorOf("ARMOR_BAEKHWA"));
        if (!t.ninja) for (MineWeapon w : MINE_ARMORS) if (Math.abs(t.lv() - w.lv) <= MINE_WEAPON_DROP_LV_GAP && !out.contains(w)) out.add(w);
        return out;
    }
    static int mineArmorDef(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); if (a == null) return 0; int g = mineWeaponGrade(items, a), e = mineEnh(items, a.code); return a.atkAt(g) + a.atkEnh(g, e); }
    static double mineArmorRegen(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); if (a == null) return 0; int g = mineWeaponGrade(items, a); return a.regenAt(g) + a.regenEnh(g, mineEnh(items, a.code)); }
    static int mineArmorRes(Map<String, Integer> items) { MineWeapon a = mineArmorOn(items); return a == null ? 0 : a.resAt(mineWeaponGrade(items, a)); }
    // ---- [2026-11-06] 강화 -- "미스릴 괴를 포함한 여러 재료 소비, 1강부터 이론상 20강, 2강부터 확률 5%씩 감소, 실패해도 올려 주는 확률은 없음".
    //  무기/갑옷마다(등급과 상관없이 그 장비에) ENH_코드 = 단계. n강 확률 = 100 - 5(n-1)% (1강 100% ~ 20강 5%), 실패하면 재료만 사라지고 단계는 그대로.
    static final String MINE_ENH_PREFIX = "ENH_";
    static final int MINE_ENH_MAX = 20, MINE_ENH_BASE_PCT = 10, MINE_ENH_OPT_EVERY = 5, MINE_ENH_OPT_GRADE = 4;
    static int mineEnh(Map<String, Integer> items, String code) { return Math.max(0, Math.min(MINE_ENH_MAX, items.getOrDefault(MINE_ENH_PREFIX + code, 0))); }
    // [2026-11-06] "강화는 슬롯에 강화되는 형태로" -- 무기 칸(ENH_WEAPON)에 단계가 쌓이고, 칸에 든 드랍 무기에 그대로 적용(무기를 바꿔 들어도 유지). 갑옷 칸은 추후.
    static final String MINE_ITEM_WEAPON_ENH = MINE_ENH_PREFIX + "WEAPON";
    static int mineWeaponEnh(Map<String, Integer> items) { return Math.max(0, Math.min(MINE_ENH_MAX, items.getOrDefault(MINE_ITEM_WEAPON_ENH, 0))); }
    static int mineEnhPct(int n) { return Math.max(5, 100 - 5 * (n - 1)); }
    static final int MINE_ENH_SHARDS = 10;
    /** n강 재료: 미스릴 괴 n + 금괴 n + 그 장비 조각 ceil(n/2), 6강부터 별빛석 n-5, 11~15강 달빛석 n-10,
     *  [2026-11-06] "16강부터는 달빛석 + 칠요석을 합친 은하수 조각" -- 16강부터 달빛석 대신 은하수 조각 n-10(달빛석 개수를 이어서, 칠요석은 따로 안 듦). */
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
        if (!"weapon".equals(code)) return mineGearResult(userName, false, "갑옷 칸 강화는 아직 할 수 없어요."); // [2026-11-06] "강화는 무기만, 방어구는 추후 다른 대륙에서" -- 무기 칸만
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
    static final String MINE_ITEM_NINJA_SWORD = "NINJA_SWORD";
    static final MineWeapon[] MINE_WEAPONS = {
        new MineWeapon("BICHUL_SWORD", "비철도", 3, 20, 50, 0.2, 3, -1, 0, S5MineMap.WEAPON_SHARD_CODES[1]),
        new MineWeapon("KATANA", "카타나", 4, 40, 250, 0.2, 3, -1, 0, S5MineMap.WEAPON_SHARD_CODES[2]),
        new MineWeapon(MINE_ITEM_NINJA_SWORD, "닌자의 검", 2, 60, 500, 0.4, 7, 3, 1, S5MineMap.WEAPON_SHARD_CODES[0]),
        new MineWeapon("BAEKHWA_SWORD", "백화검", 5, 80, 1500, 0.2, 7, 0, 1, S5MineMap.WEAPON_SHARD_CODES[3]),
    };
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
        if (!t.ninja) for (MineWeapon w : MINE_WEAPONS) if (Math.abs(t.lv() - w.lv) <= MINE_WEAPON_DROP_LV_GAP && !out.contains(w)) out.add(w);
        return out;
    }
    /** 화면 그림 이름(_mine_char.jsp WEAPON_LOOK의 키). */
    static String mineWeaponLook(MineWeapon w) {
        switch (w.code) {
            case "BICHUL_SWORD": return "bichul";
            case "KATANA": return "katana";
            case MINE_ITEM_NINJA_SWORD: return "ninja";
            case "BAEKHWA_SWORD": return "baekhwa";
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
        return items.getOrDefault(MINE_ITEM_ENCH_LEFT, 0) > 0 && S5MineMobs.elemBeats(items.getOrDefault(MINE_ITEM_ENCH_EL, 0), type.elem()) ? 1.5 : 1;
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
            if (type.noReward || type.clone || type.dummy || (!admin && RND.nextInt(100) >= MINE_BOSS_GEAR_PCT)) return;
            List<MineWeapon> cand = new ArrayList<>();
            if (type.ninja) { cand.add(mineWeaponOf(MINE_ITEM_NINJA_SWORD)); cand.add(mineArmorOf("ARMOR_NINJA")); }
            else {
                int best = -1;
                for (MineWeapon[] arr : new MineWeapon[][] { MINE_WEAPONS, MINE_ARMORS }) for (MineWeapon w : arr)
                    if (best < 0 || Math.abs(type.lv() - w.lv) < Math.abs(type.lv() - best) || (Math.abs(type.lv() - w.lv) == Math.abs(type.lv() - best) && w.lv > best)) best = w.lv;
                for (MineWeapon[] arr : new MineWeapon[][] { MINE_WEAPONS, MINE_ARMORS }) for (MineWeapon w : arr) if (w.lv == best) cand.add(w);
            }
            dw = cand.get(RND.nextInt(cand.size()));
            bossG = mineBossGradeRoll(dw.maxGrade);
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
    static final String[] MINE_AURA_BOSS_ITEM = { null, S5MineMap.DESERT_HEART_CODE, S5MineMap.THUNDER_HEART_CODE /* [2026-11-05] 번개 = 천둥 황소 */, null, S5MineMap.ICE_HEART_CODE, null, S5MineMap.VOLCANO_HEART_CODE, S5MineMap.SEAL_CODE, null };
    static int mineAuraCost(int lv) { return 2 * lv - 1; }
    /** [2026-11-04] "달빛석뿐 아니라 세공된 바다의 보석도" -- 속성별 세공 바다 보석(S5MineMap.seaCutCode 번호): 땅 = 자수정, 얼음 = 진주, 불 = 산호석, 어둠 = 흑진주. 같은 개수(2n-1). */
    static final int[] MINE_AURA_SEA = { -1, 2, 3, -1, 0, -1, 1, 4, -1 }; // [2026-11-05] 번개 = 세공 바다 보석 3번
    static final int MINE_AURA_SEA_QTY = 5; // [2026-11-04] "세공 바다 보석은 2n-1 말고 고정 5개"
    static String mineAuraKey(boolean active, int e) { return (active ? "AURA_A" : "AURA_L") + e; }
    static int[] mineAuraActive(Map<String, Integer> items) {
        int[] a = new int[9];
        for (int e = 1; e <= 8; e++) a[e] = Math.max(0, Math.min(Math.min(items.getOrDefault(mineAuraKey(true, e), 0), items.getOrDefault(mineAuraKey(false, e), 0)), MINE_AURA_MAX));
        return a;
    }
    static int mineAuraRange(int[] a) { int r = 0; for (int e = 1; e <= 8; e++) r = Math.max(r, a[e]); return r; }
    static HashMap<String, Object> mineAuraInfo(Map<String, Integer> items) {
        HashMap<String, Object> m = new HashMap<>();
        int[] l = new int[9];
        for (int e = 1; e <= 8; e++) l[e] = Math.min(items.getOrDefault(mineAuraKey(false, e), 0), MINE_AURA_MAX);
        m.put("l", l);
        m.put("a", mineAuraActive(items));
        m.put("gift", items.getOrDefault(MINE_ITEM_AURA_GIFT, 0) > 0 ? 2 : mineRebirth(items) >= MINE_AURA_GIFT_RB ? 1 : 0); // [2026-11-05] 2환생 특전: 0 = 아직, 1 = 받을 수 있음, 2 = 받음
        return m;
    }
    /** 켠 속성마다 [속성, 피해, 상성 1/0] -- 피해 = 공격력(무기 포함) x 단계 x 5%, 상성 x1.5.
     *  [2026-11-04] "속성마다 자기 단계만큼만 닿는다(불5 얼음1이면 2~5칸 앞은 불만)" -- dist칸 앞이면 단계 >= dist인 속성만. */
    static List<int[]> mineAuraHits(int[] a, double atkBase, S5MineMobs.Type type, int dist) {
        List<int[]> out = new ArrayList<>();
        for (int e = 1; e <= 8; e++) {
            if (a[e] <= 0 || a[e] < dist) continue;
            boolean beat = S5MineMobs.elemBeats(e, type.elem());
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
    long[] mineAuraSweep(String userName, S5MineMap map, Map<String, Integer> items, int[] aura, int range, double atkBase, int x, int y, String dir, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out) {
        int[] dv = mineDirVec(dir);
        if (dv == null || range <= 0) return new long[] { 0, 0 };
        java.util.Map<Integer, Integer> line = new java.util.HashMap<>(); // 칸 -> 몇 칸 앞
        // [2026-11-05] "검기가 여러 마리 타격" -- 바라보는 줄(+ 양옆 MINE_AURA_SIDE줄, 지금 0)의 몬스터를 가까운 순으로 최대 MINE_AURA_MAX_TARGETS마리. 속성 사거리(몇 칸 앞)는 그대로.
        boolean adminAura = isMineAdmin(userName);
        int side = adminAura ? 1 : MINE_AURA_SIDE; // [2026-11-05] "관리자 검기 폭 3칸" -- 양옆 한 줄씩
        if (adminAura) for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) { // [2026-11-05] "관리자 검기에 8방향 둘레 회오리" -- 내 둘레 8칸도 1칸 거리로
            int lx = x + ox, ly = y + oy;
            if ((ox != 0 || oy != 0) && lx >= 0 && ly >= 0 && lx < map.w && ly < map.h) line.put(ly * map.w + lx, 1);
        }
        for (int k = 1; k <= range; k++) for (int sd = -side; sd <= side; sd++) {
            int lx = x + dv[0] * k - dv[1] * sd, ly = y + dv[1] * k + dv[0] * sd;
            if (lx < 0 || ly < 0 || lx >= map.w || ly >= map.h) continue;
            line.putIfAbsent(ly * map.w + lx, k);
        }
        return mineSweepCells(userName, map, items, line, MINE_AURA_MAX_TARGETS, skipMob, now, msg, out, (ot, dist) -> mineAuraHits(aura, atkBase, ot, dist), "auraMobs", "auraDrops", "🌀 검기로 ");
    }

    /** 칸 묶음(line: 칸 -> 거리) 위의 몬스터를 가까운 순으로 maxT마리까지 hitsFn(종류, 거리) 피해 -- 검기/관리자 전체 공격이 같이 쓴다. 보스는 체력 1에서 멈춘다. */
    long[] mineSweepCells(String userName, S5MineMap map, Map<String, Integer> items, java.util.Map<Integer, Integer> line, int maxT, int skipMob, long now, StringBuilder msg, HashMap<String, Object> out,
            java.util.function.BiFunction<S5MineMobs.Type, Integer, List<int[]>> hitsFn, String mobsKey, String dropsKey, String verb) {
        List<HashMap<String, Object>> hitList = new ArrayList<>();
        List<HashMap<String, Object>> drops = new ArrayList<>();
        int mind = mineMindExpPct(items);
        long kills = 0, expSum = 0;
        List<int[]> tg = new ArrayList<>(); // {몬스터 번호, 맞은 칸, 몇 칸 앞}
        for (S5MineMap.Mob om : map.mobs()) {
            if (om.id == skipMob) continue;
            S5MineMobs.Type ot = S5MineMobs.TYPES.get(om.type);
            if (ot.clone || mineBossDead(om.type) || mineHidden(map, ot, now)) continue; // [2026-11-05] 천둥 황소 동안 마물은 숨음
            MobState s0 = MINE_MOB_STATE.get(mineMobKey(map, om.id));
            if (ot.minion && s0 == null) continue; // 불려 나오지 않은 수하
            if (s0 != null && s0.deadUntil > now) continue; // 쓰러져 있는 몬스터는 자리를 차지하지 않는다
            int hitCell = -1, fw = ot.boss ? 2 : 1, dist = 99;
            for (int c : mineMobCells(om, s0, now, map))
                for (int oy = 0; oy < fw; oy++) for (int ox = 0; ox < fw; ox++) {
                    Integer k = line.get(c + oy * map.w + ox);
                    if (k != null && k < dist) { dist = k; hitCell = c; }
                }
            if (hitCell >= 0) tg.add(new int[] { om.id, hitCell, dist });
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
            List<int[]> hs = hitsFn.apply(ot, dist);
            int sum = 0;
            for (int[] h : hs) sum += h[1];
            boolean dead = false;
            long[] row;
            synchronized (s) {
                if (s.deadUntil > now) continue;
                if (s.deadUntil > 0) s.respawn(omMax);
                if (ot.boss) mineBossRegen(s, omMax, now, map, om);
                if (ot.ninja && s.stealth) continue; // 은신한 닌자는 안 맞는다
                s.lastHitAt = now;
                if (!ot.dummy) {
                    s.hp -= sum;
                    if (ot.boss) s.hp = Math.max(1, s.hp);
                    if (s.hp <= 0) {
                        dead = true;
                        s.hp = 0;
                        s.deadUntil = ot.minion ? Long.MAX_VALUE / 2 : now + ot.respawnMs();
                        s.frozenUntil = 0;
                        s.stealth = false;
                    } else {
                        s.fx = hitCell % map.w;
                        s.fy = hitCell / map.w;
                        s.frozenUntil = now + MINE_MOB_FREEZE_MS;
                        if (s.target == null || !mineEngaged(s.target, now)) s.target = userName;
                    }
                }
                row = mineMobRow(om.id, s);
            }
            if (!dead && !ot.dummy) mineEngage(userName, map, om.id, now, now);
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", om.id);
            m.put("hits", hs);
            m.put("row", row);
            if (dead) {
                m.put("killed", true);
                kills++;
                mineThunderKill(map, ot, now); // [2026-11-05] 밤 마물 처치 수
                long be = mineLuckExp(items, mineNightExp(map, ot, S5MineMobs.exp(ot, om.rank), now), now), g = be + be * mind / 100; // [2026-11-05] 마물의 숲 밤 1.5배
                expSum += g;
                m.put("exp", g);
                msg.append(msg.length() > 0 ? " " : "").append(verb).append(ot.name).append("을(를) 쓰러뜨렸어요! 경험치 +").append(g);
                if (!ot.noReward) {
                    drops.addAll(mineMobDrops(userName, map, ot, msg, om.rank));
                    mineWeaponDrop(userName, items, ot, om.rank, msg, out);
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
        if (gain <= 0) return new long[] { lv, exp, hp, lock, 0 };
        boolean wasReady = mineRebirthReady(lv, exp, rb);
        exp += gain;
        int up = 0;
        while (lv < MINE_LV_MAX && exp >= mineExpNeed(lv, rb)) { exp -= mineExpNeed(lv, rb); lv++; up++; }
        if (lv >= MINE_LV_MAX) exp = Math.min(exp, mineExpNeed(MINE_LV_MAX, rb));
        if (!wasReady && mineRebirthReady(lv, exp, rb)) out.put("rebirthReadyNow", true);
        if (up > 0 && !died) {
            hp = mineHpMax(lv, rb);
            if (lock > 0) { lock = 0; dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); out.remove("hpLockUp"); out.put("hpLockFree", true); }
        }
        return new long[] { lv, exp, hp, lock, up };
    }

    // [2026-11-05] "공격 버튼 위에 화면 전체를 공격하는 전체 공격기 -- 관리자용(칼로 베는 이펙트)".
    //  화면에 보이는 칸(나를 가운데로 가로 hw, 세로 hh칸까지, 최대 MINE_NOVA_HALF)의 몬스터를 가까운 순으로 MINE_NOVA_MAX마리, 일반 공격 한 방씩(치명 10%, 속성 상성).
    //  보스는 체력 1에서 멈춘다(검기와 같음 -- 마지막 한 방은 직접). 쿨타임 MINE_NOVA_GAP_MS.
    static final int MINE_NOVA_HALF = 20, MINE_NOVA_MAX = 40;
    static final long MINE_NOVA_GAP_MS = 1_500L;
    static final java.util.concurrent.ConcurrentHashMap<String, Long> MINE_NOVA_AT = new java.util.concurrent.ConcurrentHashMap<>();
    @Override
    @Transactional
    public HashMap<String, Object> mineNova(String userName, String mapId, String ver, int x, int y, int hw, int hh) {
        HashMap<String, Object> out = new HashMap<>();
        out.put("ok", false);
        if (!isMineAdmin(userName)) { out.put("message", "관리자만 쓸 수 있어요."); return out; }
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        long now = System.currentTimeMillis();
        Long last = MINE_NOVA_AT.get(userName);
        if (last != null && now - last < MINE_NOVA_GAP_MS) { out.put("tooFast", true); return out; }
        MINE_NOVA_AT.put(userName, now);
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
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb);
        double atkBase = mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items);
        int ax = Math.max(1, Math.min(MINE_NOVA_HALF, hw)), ay = Math.max(1, Math.min(MINE_NOVA_HALF, hh));
        java.util.Map<Integer, Integer> cells = new java.util.HashMap<>();
        for (int yy = Math.max(0, y - ay); yy <= Math.min(map.h - 1, y + ay); yy++)
            for (int xx = Math.max(0, x - ax); xx <= Math.min(map.w - 1, x + ax); xx++) cells.put(yy * map.w + xx, Math.abs(xx - x) + Math.abs(yy - y));
        StringBuilder msg = new StringBuilder();
        long[] r = mineSweepCells(userName, map, items, cells, MINE_NOVA_MAX, -1, now, msg, out, (ot, dist) -> {
            boolean crit = RND.nextInt(100) < 10;
            int d = Math.max(1, (int) Math.round(atkBase * (0.85 + RND.nextDouble() * 0.3) * (crit ? 1.6 : 1.0) * mineElemMul(items, ot)));
            List<int[]> l = new ArrayList<>();
            l.add(new int[] { 0, d, crit ? 1 : 0 });
            return l;
        }, "novaMobs", "novaDrops", "⚔️ 전체 베기로 ");
        mineMercShare(userName, r[1], lv, r[0], out); // [2026-11-06] 용병 경험치(전체 베기)
        long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, r[1], false, out);
        dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, (int) g[0], g[1], (int) g[2], now, (int) r[0]);
        out.put("ok", true);
        out.put("levelUp", (int) g[4]);
        out.put("combat", mineCombatInfo((int) g[0], g[1], (int) g[2], now, mineEngaged(userName, now), (int) g[3], rb));
        out.put("message", msg.length() > 0 ? msg.toString() : null);
        return out;
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
        if (range <= 0) { out.put("message", "켜 둔 검기가 없어요. (인벤토리 스킬 탭)"); return out; }
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), rb = mineRebirth(items);
        int hp = mineHpNow(row, now, lock, rb);
        mineBerserkLow(items, hp, mineHpMax(lv, rb)); // [2026-11-04] 광전사의 장갑 2단계
        StringBuilder msg = new StringBuilder();
        long[] r = mineAuraSweep(userName, map, items, aura, range, mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items), x, y, dir, -1, now, msg, out);
        mineMercShare(userName, r[1], lv, r[0], out); // [2026-11-06] 용병 경험치(검기만)
        long[] g = mineAuraExp(userName, lv, exp, hp, lock, rb, r[1], false, out);
        int kh = mineRegenKillHeal(items, (int) r[0], mineHpMax((int) g[0], rb)); // [2026-11-04] 재생의 장갑 2단계
        if (kh > 0) { g[2] = Math.min(mineHpCap((int) g[0], (int) g[3], rb), g[2] + kh); out.put("killHeal", kh); }
        dao.updateMinePos(userName, map.id, x, y, now);
        dao.updateMineCombat(userName, (int) g[0], g[1], (int) g[2], now, (int) r[0]);
        out.put("ok", true);
        out.put("auraOnly", true);
        out.put("levelUp", (int) g[4]);
        out.put("combat", mineCombatInfo((int) g[0], g[1], (int) g[2], now, mineEngaged(userName, now), (int) g[3], rb));
        if (msg.length() > 0) out.put("message", msg.toString());
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
        return cur > 0 ? Math.max(MINE_MERC_LV_MIN, cur - d) : items.getOrDefault(mineOfKey(i, "LV"), MINE_MERC_LV_MIN);
    }
    // [2026-11-06] "용병은 전부 여자 캐릭으로, S5 동료 이름 재사용" -- BotS5ServiceImpl.NAME_POOL_BY_JOB_GRADE에서 (번호 = 화면 머리색 / 초상화 img_loa/s55/merc/{직업}_{번호}.jpg).
    // [2026-11-06] "동료 이름은 S5 4성 이상 동료 이름으로(기존 것도), 이미지는 최대한 은용 유저의 이미지" -- 전사 = 전사 + 도적, 궁수 = 궁수, 마법사 = 마법사 + 사제의 ★4~★7.
    //  기존 계약/후보는 번호(NM)를 그대로 두고 floorMod로 새 이름에 붙는다. 초상화는 은용의 같은 이름 동료(없으면 다른 유저 / ★7 초상화).
    static final String[][] MINE_MERC_NAMEPOOL = {
        { "렌지", "아츠시", "츠요시", "고우키", "타이가", "유이토", "카게로우", "나기", "야토", "시노" },
        { "세리나", "츠키미", "스즈네", "아마츠", "히카리" },
        { "리오", "카나", "미코토", "세라", "아리스", "코하루", "미레이", "스이렌", "아마네", "미코" } };
    static int mineMercNi(Map<String, Integer> items, int c, String f) { return Math.floorMod(items.getOrDefault(mineMercKey(c, f), 0), MINE_MERC_NAMEPOOL[c].length); }
    static String mineMercNm(Map<String, Integer> items, int c, String f) { String[] p = MINE_MERC_NAMEPOOL[c]; return p[Math.floorMod(items.getOrDefault(mineMercKey(c, f), 0), p.length)]; }
    static boolean mineMercPaid(Map<String, Integer> items, int c, long now) { return items.getOrDefault(mineMercKey(c, "PAID"), 0) * 60_000L > now; }
    void mineMercSet(String userName, Map<String, Integer> items, int c, String f, int v) { dao.upsertMineItem(userName, mineMercKey(c, f), v); items.put(mineMercKey(c, f), v); }
    /** 급여 자동 차감(밀린 날만큼, 미접속 시간 포함) -- 안내 문구는 notes에. 모자라면 한 번만 알린다(MERC{c}_WARN = 그때의 PAID). */
    void mineMercPayroll(String userName, Map<String, Integer> items, long now, List<String> notes) {
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
        m.put("sal", mineCostText(cost));
        m.put("can", mineCostOk(cost, have));
        m.put("owned", false); // [2026-11-06] 이제 늘 계약 가능(지금 용병과 바뀐다)
        return m;
    }
    static final String[] MINE_MERC_NAMES = { "전사", "궁수", "마법사" };
    static final String[] MINE_MERC_WEAPON = { "기본 칼", "기본 활", "기본 지팡이" };
    static final int[] MINE_MERC_REACH = { 5, 7, 6 };
    static final double[] MINE_MERC_DMG = { 1.2, 1.0, 1.0 }; // [2026-11-06] 마법사 평타가 한 명(파이어볼)이라 0.8 -> 1.0
    static final int MINE_MERC_SKILL_LV = 40;
    static final long MINE_MERC_SKILL_MS = 20_000L, MINE_MERC_RAIN_MS = 5_000L;
    static final int[] MINE_MERC_ELS = { S5MineMobs.EL_FIRE, S5MineMobs.EL_WATER, S5MineMobs.EL_THUNDER };
    static final int MINE_MERC_LV_MIN = 5, MINE_MERC_LV_MAX0 = 30;
    static final double MINE_MERC_EXP_PCT = 1.0;
    static final int MINE_MERC_EXP_GAP = 10, MINE_MERC_EXP_CAP_PCT = 10;
    static final long MINE_MERC_REST_MS = 20_000L;
    static final double MINE_MERC_REGEN = 0.05;
    static String mineMercKey(int c, String f) { return "MERC" + c + "_" + f; }
    static int mineMercLv(Map<String, Integer> items, int c) { return Math.max(0, Math.min(MINE_LV_MAX, items.getOrDefault(mineMercKey(c, "LV"), 0))); }
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
    static final double MINE_MERC_ATK_MUL = 0.7; // [2026-11-06] "동료 공격 성능 30% 정도만 줄이자"
    static int mineMercAtk(Map<String, Integer> items, int c) { return (int) Math.round(mineAtk(Math.max(1, mineMercLv(items, c))) * MINE_MERC_DMG[c] * MINE_MERC_ATK_MUL); }
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
    /** 용병 c 한 칸(용병 관리 창) -- 계약 안 했으면 {t, name, owned:false}. */
    static HashMap<String, Object> mineMercCard(Map<String, Integer> items, int c) {
        HashMap<String, Object> m = new HashMap<>();
        int lv = mineMercLv(items, c);
        m.put("t", c);
        m.put("name", MINE_MERC_NAMES[c]);
        m.put("reach", MINE_MERC_REACH[c]);
        m.put("weapon", MINE_MERC_WEAPON[c]);
        m.put("owned", lv > 0);
        if (lv <= 0) return m;
        int el = mineMercEl(items, c), s = mineMercStage(lv);
        m.put("lv", lv);
        m.put("exp", items.getOrDefault(mineMercKey(c, "EXP"), 0));
        m.put("need", lv >= MINE_LV_MAX ? 0 : mineExpNeed(lv, 0));
        m.put("atk", mineMercAtk(items, c));
        m.put("max", mineMercHp(items, c));
        m.put("on", mineMercOn(items) == c);
        m.put("el", el);
        m.put("elName", S5MineMobs.ELEM_NAMES[el]);
        int sMax = s, s2Max = mineMercStage2(lv); // [2026-11-06] 열린 단계 / 고른 단계(0 = 끔) -- 화면 스킬 탭
        s = mineMercSel(items, c, "S1", sMax);
        int s2 = mineMercSel(items, c, "S2", s2Max);
        m.put("st", s);
        m.put("stMax", sMax);
        m.put("stSel", items.getOrDefault(mineMercKey(c, "S1"), 0));
        m.put("skill", mineMercSkill(c, el, Math.max(1, s)));
        if (sMax < 5) { m.put("nextLv", sMax * 20); m.put("skillNext", mineMercSkill(c, el, sMax + 1)); }
        m.put("s2", s2);
        m.put("s2Max", s2Max);
        m.put("s2Sel", items.getOrDefault(mineMercKey(c, "S2"), 0));
        m.put("skill2", mineMercSkill2(c, el, Math.max(1, s2 > 0 ? s2 : s2Max)));
        if (s2Max < 3) { m.put("next2Lv", MINE_MERC_SKILL_LV + s2Max * 20); if (s2Max > 0) m.put("skill2Next", mineMercSkill2(c, el, s2Max + 1)); }
        List<String> d1 = new ArrayList<>(), d2 = new ArrayList<>();
        for (int k = 1; k <= 5; k++) d1.add(mineMercSkill(c, el, k));
        for (int k = 1; k <= 3; k++) d2.add(mineMercSkill2(c, el, k));
        m.put("skills1", d1);
        m.put("skills2", d2);
        m.put("ni", mineMercNi(items, c, "NM")); // [2026-11-06] 치장 대신 이름마다 모습(머리색)
        m.put("nm", mineMercNm(items, c, "NM")); // [2026-11-06] 이름 / 급여
        m.put("paidUntil", items.getOrDefault(mineMercKey(c, "PAID"), 0) * 60_000L);
        m.put("unpaid", !mineMercPaid(items, c, System.currentTimeMillis()));
        m.put("sal", mineCostText(mineMercCost(items, c)));
        m.put("auto", items.getOrDefault(mineMercKey(c, "MANUAL"), 0) <= 0);
        m.put("want", items.getOrDefault(MINE_ITEM_MERC_ON, 0) - 1 == c);
        m.put("mode", items.getOrDefault(MINE_ITEM_MERC_MODE, 0) == 1 ? 1 : 0); // [2026-11-06] 선공/협공
        return m;
    }
    static List<HashMap<String, Object>> mineMercCards(Map<String, Integer> items) {
        List<HashMap<String, Object>> l = new ArrayList<>();
        for (int c = 0; c < MINE_MERC_NAMES.length; c++) l.add(mineMercCard(items, c));
        return l;
    }
    /** 데리고 다니는 용병(없으면 null) -- 칸 정보 + 지금 체력/쉬기. */
    static HashMap<String, Object> mineMercInfo(String userName, Map<String, Integer> items, long now) {
        int c = mineMercOn(items);
        if (c < 0) return null;
        HashMap<String, Object> m = mineMercCard(items, c);
        MercSt ms = MINE_MERC.computeIfAbsent(userName, u -> new MercSt());
        synchronized (ms) {
            mineMercRefresh(ms, c, mineMercHp(items, c), now);
            m.put("hp", ms.hp);
            m.put("rest", ms.restUntil);
        }
        return m;
    }
    /** [2026-11-06] "용병 경험치는 플레이어가 얻는 경험치의 1%, 1레벨 용병이 99레벨 몬스터를 같이 잡아도 렙업이 과하지 않게" -- 데리고 다니는 용병에게. out.mercXp. */
    // [2026-11-06] "용병 소환 후 같이 처치하면 최소 경험치 1씩은" -- 처치 수(kills)만큼은 꼭 받는다(1% 보정/상한보다 우선).
    void mineMercShare(String userName, long gain, int mobLv, long kills, HashMap<String, Object> out) {
        if (gain <= 0 && kills <= 0) return;
        Map<String, Integer> items = mineItems(userName);
        int c = mineMercOn(items);
        if (c < 0) return;
        int lv = mineMercLv(items, c);
        if (lv >= MINE_LV_MAX) return;
        double f = MINE_MERC_EXP_PCT / 100.0;
        int d = mobLv - lv;
        if (d > MINE_MERC_EXP_GAP) f *= Math.max(0.05, 1 - (d - MINE_MERC_EXP_GAP) * 0.03);
        long need = mineExpNeed(lv, 0), g = Math.max(Math.max(1, kills), Math.min(Math.max(1, Math.round(gain * f)), Math.max(1, need * MINE_MERC_EXP_CAP_PCT / 100)));
        long exp = items.getOrDefault(mineMercKey(c, "EXP"), 0) + g;
        int up = 0;
        while (lv < MINE_LV_MAX && exp >= mineExpNeed(lv, 0)) { exp -= mineExpNeed(lv, 0); lv++; up++; }
        if (lv >= MINE_LV_MAX) exp = 0;
        dao.upsertMineItem(userName, mineMercKey(c, "EXP"), (int) exp);
        items.put(mineMercKey(c, "EXP"), (int) exp);
        if (up > 0) { dao.upsertMineItem(userName, mineMercKey(c, "LV"), lv); items.put(mineMercKey(c, "LV"), lv); }
        HashMap<String, Object> x = mineMercCard(items, c);
        x.put("gain", g);
        if (up > 0) x.put("up", up);
        out.put("mercXp", x);
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
        int cls = mineMercOn(items);
        if (cls < 0) return new long[] { 0, 0 };
        HashMap<String, Object> mo = mineMercInfo(userName, items, now);
        out.put("merc", mo);
        if (map.boat()) return new long[] { 0, 0 };
        // [2026-11-06] "용병을 선공 모드 / 협공 모드로 고르기" -- 협공(기본) = 나와 싸우는 몬스터만, 선공 = 싸우는 몬스터가 사거리 안에 없으면
        //  사거리 안의 가장 가까운 몬스터를 골라 먼저 친다(그 몬스터는 나와의 싸움에 들어와 쫓아온다). 보스/수하/허수아비/분신/숨은 마물은 고르지 않는다.
        if (items.getOrDefault(MINE_ITEM_MERC_MODE, 0) == 1) {
            boolean any = false;
            if (e != null) for (Integer id : e.mobs.keySet()) {
                if (id < 0 || id >= map.mobs().size()) continue;
                MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
                if (mineMercCan(map, S5MineMobs.TYPES.get(map.mobs().get(id).type), s, now) && Math.abs(s.fx - x) + Math.abs(s.fy - y) <= MINE_MERC_REACH[cls]) { any = true; break; }
            }
            if (!any) {
                int bestId = -1, bestD = 99, bx = 0, by = 0;
                for (S5MineMap.Mob mb : map.mobs()) {
                    if (Math.abs(mb.x - x) + Math.abs(mb.y - y) > MINE_MERC_REACH[cls] + 6) continue;
                    S5MineMobs.Type mt = S5MineMobs.TYPES.get(mb.type);
                    if (mt.boss || mt.minion || mt.dummy || mt.clone || mineHidden(map, mt, now)) continue;
                    MobState s = MINE_MOB_STATE.get(mineMobKey(map, mb.id));
                    if (s != null && (s.deadUntil > now || (mt.ninja && s.stealth))) continue;
                    int mx, my;
                    if (s != null && s.frozenUntil > now) { mx = s.fx; my = s.fy; }
                    else { int[] cs = mineMobCells(mb, s != null ? s : new MobState(S5MineMobs.hp(mt, mb.rank)), now, map); mx = cs[cs.length / 2] % map.w; my = cs[cs.length / 2] / map.w; }
                    int d = Math.abs(mx - x) + Math.abs(my - y);
                    if (d <= MINE_MERC_REACH[cls] && d < bestD) { bestD = d; bestId = mb.id; bx = mx; by = my; }
                }
                if (bestId >= 0) {
                    S5MineMap.Mob mb = map.mobs().get(bestId);
                    MobState s = MINE_MOB_STATE.computeIfAbsent(mineMobKey(map, bestId), kk -> new MobState(S5MineMobs.hp(S5MineMobs.TYPES.get(mb.type), mb.rank)));
                    synchronized (s) { if (s.frozenUntil <= now) { s.fx = bx; s.fy = by; } s.frozenUntil = Math.max(s.frozenUntil, now + MINE_MOB_FREEZE_MS); s.target = userName; }
                    mineEngage(userName, map, bestId, now, 0L);
                    e = MINE_ENGAGE.get(userName);
                    out.put("mercPre", bestId);
                }
            }
        }
        if (e == null || e.mobs.isEmpty()) return new long[] { 0, 0 };
        MercSt ms = MINE_MERC.get(userName);
        int pick = -1, best = 99, reach = MINE_MERC_REACH[cls];
        synchronized (ms) {
            ms.fightAt = now;
            if (ms.restUntil > now) return new long[] { 0, 0 };
            for (Integer id : e.mobs.keySet()) {
                if (id < 0 || id >= map.mobs().size()) continue;
                S5MineMap.Mob om = map.mobs().get(id);
                MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
                if (!mineMercCan(map, S5MineMobs.TYPES.get(om.type), s, now)) continue;
                int d = Math.abs(s.fx - x) + Math.abs(s.fy - y);
                if (d > reach) continue;
                if (id == ms.target) { pick = id; break; }
                if (d < best) { best = d; pick = id; }
            }
            ms.target = pick;
        }
        if (pick < 0) return new long[] { 0, 0 };
        int mlv = mineMercLv(items, cls), el = mineMercEl(items, cls), st = mineMercSel(items, cls, "S1", mineMercStage(mlv)), atk = mineMercAtk(items, cls); // [2026-11-06] 고른 평타 단계(0 = 끔)
        MobState ps = MINE_MOB_STATE.get(mineMobKey(map, pick));
        // [2026-11-06] "40레벨에 2차 스킬, 쿨타임(20초)마다" -- 평타(1~99): 전사 속성 검기(대상 + 둘레 s칸) / 궁수 속성 화살(한 명 +20% x s) / 마법사 파이어볼(한 명).
        //  2차(40레벨부터, 40/60/80에 1~3단계): 전사 이기어검(가까운 3+2단계마리에 날아가는 검), 마법사 불 메테오(대상 둘레 2+단계칸) / 번개 체인 라이트닝(2+2x단계번 튕김) /
        //  물 토네이도(나 -> 대상 길 + 둘레 1+단계칸), 궁수 화살비(대상 둘레 1+단계칸 땅에 5초 -- 매초 그 안의 싸우는 몬스터).
        List<Integer> hitIds = new ArrayList<>();
        List<Double> hitMul = new ArrayList<>();
        java.util.function.BiConsumer<Integer, Double> addHit = (hid, hm) -> { int k = hitIds.indexOf(hid); if (k >= 0) hitMul.set(k, hitMul.get(k) + hm); else { hitIds.add(hid); hitMul.add(hm); } };
        addHit.accept(pick, cls == 1 ? 1 + 0.2 * st : 1.0);
        List<int[]> cands = new ArrayList<>(); // 싸우는 몬스터 {id, fx, fy}
        for (Integer id : e.mobs.keySet()) {
            if (id < 0 || id >= map.mobs().size()) continue;
            MobState s = MINE_MOB_STATE.get(mineMobKey(map, id));
            if (mineMercCan(map, S5MineMobs.TYPES.get(map.mobs().get(id).type), s, now)) cands.add(new int[] { id, s.fx, s.fy });
        }
        // [2026-11-06] "용병 검기를 플레이어 평타 검기(강화)와 같은 것으로" -- 대상 옆(나 쪽)에 선 용병이 대상 쪽으로 st칸 줄을 벤다: 그 줄(대상 뒤까지)의 싸우는 몬스터에게
        //  공격력의 st x 5% 속성 피해(플레이어 검기 mineAuraHits와 같은 비율, 무리는 auraTaken 배). 대상은 평타 그대로. 방향은 나 -> 대상의 큰 축.
        if (ps != null && cls == 0 && st > 0) {
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
            for (int[] cd : cands) {
                if (cd[0] == pick) continue;
                for (int kq = 1; kq <= st; kq++) if (cd[1] == mx0 + dvx * kq && cd[2] == my0 + dvy * kq) { addHit.accept(cd[0], st * MINE_AURA_PCT / 100.0); break; }
            }
            mo.put("adir", new int[] { dvx, dvy });
        }
        int s2 = mineMercSel(items, cls, "S2", mineMercStage2(mlv)); // [2026-11-06] 고른 2차 스킬 단계(0 = 끔)
        HashMap<String, Object> sk = null;
        boolean cast = false;
        if (s2 > 0 && ps != null) synchronized (ms) { if (now - ms.skillAt >= MINE_MERC_SKILL_MS) { ms.skillAt = now; cast = true; } }
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
                for (int k = 0; k < 3 + 2 * s2; k++) { int[] tq = nb.get(k % nb.size()); addHit.accept(tq[0], 1.5 + 0.5 * s2); pts.add(new int[] { tq[1], tq[2], tq[0] }); }
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
                for (int[] cd : cands) {
                    double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((cd[1] - x) * vx + (cd[2] - y) * vy) / l2));
                    if (Math.max(Math.abs(cd[1] - ps.fx), Math.abs(cd[2] - ps.fy)) <= rad || Math.hypot(cd[1] - (x + vx * t), cd[2] - (y + vy * t)) <= 1.0) addHit.accept(cd[0], 1.5 + 0.5 * s2);
                }
                sk.put("k", "tornado"); sk.put("r", rad); sk.put("nm", "토네이도");
            } else { // 메테오
                int rad = 2 + s2;
                for (int[] cd : cands) if (Math.max(Math.abs(cd[1] - ps.fx), Math.abs(cd[2] - ps.fy)) <= rad) addHit.accept(cd[0], 2.0 + 0.5 * s2);
                sk.put("k", "meteor"); sk.put("r", rad); sk.put("nm", "메테오");
            }
            sk.put("pts", pts);
            mo.put("sk", sk);
        }
        long rainUntil; int rx, ry, rr; double rm;
        synchronized (ms) { rainUntil = ms.rainUntil; rx = ms.rainX; ry = ms.rainY; rr = ms.rainR; rm = ms.rainMul; }
        if (rainUntil > now && !cast) for (int[] cd : cands) if (Math.max(Math.abs(cd[1] - rx), Math.abs(cd[2] - ry)) <= rr) addHit.accept(cd[0], rm); // 화살비 -- 땅에 남아 매초
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
            boolean beat = S5MineMobs.elemBeats(el, ot.elem());
            int omMax = S5MineMobs.hp(ot, om.rank), dmg = Math.max(1, (int) Math.round(atk * hitMul.get(hi) * (beat ? 1.5 : 1) * (hi > 0 && cls == 0 ? ot.auraTaken : 1) * (0.85 + RND.nextDouble() * 0.3)));
            boolean dead = false;
            synchronized (s) {
                if (s.deadUntil > now) continue;
                if (ot.boss) mineBossRegen(s, omMax, now, map, om);
                s.lastHitAt = now;
                s.hp -= dmg;
                if (ot.boss) s.hp = Math.max(1, s.hp);
                if (s.hp <= 0) {
                    dead = true;
                    s.hp = 0;
                    s.deadUntil = ot.minion ? Long.MAX_VALUE / 2 : now + ot.respawnMs();
                    s.frozenUntil = 0;
                    s.stealth = false;
                }
                rows.add(mineMobRow(id, s));
                if (hi == 0) { mo.put("tx", s.fx); mo.put("ty", s.fy); mo.put("dmg", dmg); if (beat) mo.put("beat", true); }
                else sp.add(new int[] { s.fx, s.fy, dmg, id }); // [2026-11-06] 4번째 = 몬스터 번호(화면이 그려진 자리로 조준)
            }
            if (!dead) continue;
            if (hi == 0) primaryDead = true;
            e.mobs.remove(id);
            mineThunderKill(map, ot, now);
            long be = mineLuckExp(items, mineNightExp(map, ot, S5MineMobs.exp(ot, om.rank), now), now), g = be + be * mineMindExpPct(items) / 100;
            kills++;
            expSum += g;
            if (!ot.noReward) {
                StringBuilder dm = new StringBuilder();
                allDrops.addAll(mineMobDrops(userName, map, ot, dm, om.rank));
                mineWeaponDrop(userName, items, ot, om.rank, dm, mo);
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
        mo.put("exp", expSum);
        if (!allDrops.isEmpty()) mo.put("drops", allDrops);
        mo.put("ores", mineOreList(userName));
        return new long[] { kills, expSum };
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
        Map<String, Integer> items = mineItems(userName);
        boolean ok = true;
        String msg = "";
        int c = tier;
        boolean cOk = c >= 0 && c < MINE_MERC_NAMES.length;
        long now = System.currentTimeMillis();
        List<String> pay = new ArrayList<>();
        if (!"hire".equals(action)) mineMercPayroll(userName, items, now, pay);
        if ("hire".equals(action)) { // 계약 -- tier = 후보 칸(0~4), 보여 준 후보(이름/Lv.5~30/속성) 그대로, 첫날 급여를 낸다
            int oi = tier;
            c = oi >= 0 && oi < MINE_MERC_OFFERS ? items.getOrDefault(mineOfKey(oi, "T"), 0) - 1 : -1;
            cOk = c >= 0 && c < MINE_MERC_NAMES.length;
            int ofLv = cOk ? mineOfLv(items, oi) : 0; // [2026-11-06] 지금 용병을 내보내기 전에(교체 후보는 지금 레벨 기준)
            String[][] cost = cOk ? mineMercSalCost(items.getOrDefault(mineOfKey(oi, "SAL"), 0), ofLv) : null;
            if (!mineAtNpc(row, "mercenary")) { ok = false; msg = "왕도 용병소에서만 계약할 수 있어요."; }
            else if (!cOk) { ok = false; msg = "그 후보는 이제 없어요."; }
            else if (!mineCostOk(cost, mineOreHave(userName))) { ok = false; msg = "첫날 급여(" + mineCostText(cost) + ")가 모자라요." + mineLackText(cost, userName); }
            else {
                mineTakeCost(userName, cost, 1);
                StringBuilder gone = new StringBuilder(); // [2026-11-06] "현재 용병은 사라지고 대체" -- 지금 용병(들)과의 계약을 끝낸다(레벨/경험치/급여 기록도)
                for (int oc = 0; oc < MINE_MERC_NAMES.length; oc++) if (mineMercLv(items, oc) > 0) {
                    gone.append(gone.length() > 0 ? ", " : "").append(mineMercNm(items, oc, "NM"));
                    for (String f : new String[] { "LV", "EXP", "PAID", "MANUAL" }) mineMercSet(userName, items, oc, f, 0);
                    mineMercSet(userName, items, oc, "WARN", -1);
                }
                int lv = ofLv, el = items.getOrDefault(mineOfKey(oi, "EL"), S5MineMobs.EL_FIRE);
                mineMercSet(userName, items, c, "LV", lv);
                mineMercSet(userName, items, c, "EXP", 0);
                mineMercSet(userName, items, c, "EL", el);
                mineMercSet(userName, items, c, "NM", items.getOrDefault(mineOfKey(oi, "NM"), 0));
                mineMercSet(userName, items, c, "PAID", (int) (now / 60_000L) + MINE_MERC_DAY_MIN);
                mineMercSet(userName, items, c, "SAL", mineMercRollSal(mineMercLv(items, c)));
                mineMercSet(userName, items, c, "MANUAL", 0);
                mineOfSet(userName, items, oi, "T", 0); // 이 칸(과 같은 직업 칸)은 새로 뽑는다
                dao.upsertMineItem(userName, MINE_ITEM_MERC_ON, c + 1); items.put(MINE_ITEM_MERC_ON, c + 1); // 새 용병이 바로 함께
                msg = (gone.length() > 0 ? "👋 " + gone + "은(는) 떠났어요. " : "") + "🛡️ Lv." + lv + " " + S5MineMobs.ELEM_NAMES[el] + " 속성 " + MINE_MERC_NAMES[c] + " " + mineMercNm(items, c, "NM") + "와(과) 계약했어요! 첫날 급여 " + mineCostText(cost) + "를 줬어요."
                    + " 다음 급여는 내일 " + mineCostText(mineMercCost(items, c)) + "(자동 차감)예요.";
            }
        } else if ("pay".equals(action)) { // 급여(선금) 하루치
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
        } else if ("mode".equals(action)) { // [2026-11-06] 선공 / 협공 -- tier = 0 협공, 1 선공
            int md = tier == 1 ? 1 : 0;
            dao.upsertMineItem(userName, MINE_ITEM_MERC_MODE, md);
            items.put(MINE_ITEM_MERC_MODE, md);
            msg = md == 1 ? "⚔️ 선공 모드 -- 용병이 사거리 안의 몬스터를 먼저 공격해요." : "🤝 협공 모드 -- 용병이 나와 싸우는 몬스터만 함께 공격해요.";
        } else if ("lv99".equals(action)) { // [2026-11-06] "관리자는 레벨을 99로 올리는 기능"
            if (!isMineAdmin(userName)) { ok = false; msg = "관리자만 쓸 수 있어요."; }
            else if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else { mineMercSet(userName, items, c, "LV", MINE_LV_MAX); mineMercSet(userName, items, c, "EXP", 0); msg = "🛠️ " + mineMercNm(items, c, "NM") + "을(를) Lv." + MINE_LV_MAX + "로 올렸어요."; }
        } else if ("auto".equals(action)) { // 자동 차감 켜기/끄기
            if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
            else {
                boolean manual = items.getOrDefault(mineMercKey(c, "MANUAL"), 0) <= 0;
                mineMercSet(userName, items, c, "MANUAL", manual ? 1 : 0);
                msg = manual ? "⏰ 급여 자동 차감을 껐어요. 급여 날마다 직접 줘야 해요." : "💰 급여 자동 차감을 켰어요. 급여 날마다 재료에서 알아서 빠져요.";
                if (!manual) mineMercPayroll(userName, items, now, pay);
            }
        } else if ("use".equals(action)) { // 데리고 다니기 / 쉬게 하기
            if (!cOk || mineMercLv(items, c) <= 0) { ok = false; msg = "계약한 용병이 아니에요."; }
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
        }
        out.put("merc", mineMercInfo(userName, items, System.currentTimeMillis()));
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
                    rbl.add(mineNick(u) + " 님이 곧 " + (rb + 1) + "환생을 한대요!");
                }
            } catch (Exception ignore) { }
            mineGossipRb = rbl;
        }
        out.addAll(mineGossipRb);
        out.add("환생을 하면 등 뒤에 날개가 돋아난다더라고요.");
        return out;
    }

    // [2026-11-05] "금괴 반복 소모 -- 왕도 상인, 10분간 경험치 3배, 드랍률 2배" -- 행운의 주문서. 끝나는 시각은 광산 아이템 LUCK_UNTIL(초)에, 사면 남은 시간에 10분을 더한다.
    static final String[] MINE_GRADE_KO = { "일반", "고급", "희귀", "영웅", "전설", "유물", "고대", "신화" };
    static final String MINE_ITEM_LUCK = "LUCK_UNTIL";
    static final long MINE_LUCK_MS = 10 * 60_000L;
    static final String[][] MINE_LUCK_COST = { { S5MineMap.BAR_CODES[3], "1" } }; // 금괴 1개
    static long mineLuckUntil(Map<String, Integer> items) { return items.getOrDefault(MINE_ITEM_LUCK, 0) * 1000L; }
    static boolean mineLuckOn(Map<String, Integer> items, long now) { return mineLuckUntil(items) > now; }
    static long mineLuckExp(Map<String, Integer> items, long exp, long now) { return mineLuckOn(items, now) ? exp * 3 : exp; }

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
        if ("buy".equals(action)) {
            if (!mineAtNpc(row, "citymerchant")) { ok = false; msg = "왕도의 왕도 상인에게서만 살 수 있어요."; }
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
        out.put("cost", mineCostText(MINE_LUCK_COST));
        out.put("can", mineCostOk(MINE_LUCK_COST, mineOreHave(userName)));
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
        if (map.cold() <= 0 && !raid && !hot && !thunderMap) { MINE_COLD.remove(userName); return out; }
        if (map.cold() > 0) out.put("nextBlizzardAt", S5MineMap.nextBlizzard(map.id, now));
        ColdState cs = MINE_COLD.computeIfAbsent(userName, u -> new ColdState());
        long from = map.version().equals(cs.mapVer) ? Math.max(cs.at, now - MINE_COLD_MAX_GAP_MS) : now;
        cs.mapVer = map.version();
        cs.at = now;
        int lock = items.getOrDefault(MINE_ITEM_HP_LOCK, 0), lv = mineLv(row), rb = mineRebirth(items), cap = mineHpCap(lv, lock, rb);
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
                if (shock.contains(y * map.w + x)) shockDmg = mineCoatHit(items, Math.max(1, (int) Math.round(tatk * MINE_ZONE_MULT)));
                synchronized (MINE_THUNDER_LOCK) {
                    if (mineThunderStormNext <= 0) mineThunderStormNext = now + 15_000L;
                    if (now >= mineThunderStormNext) { mineThunderStormAt = now; mineThunderStormNext = now + 18_000L + RND.nextInt(6_000); }
                }
                out.put("stormNext", mineThunderStormNext);
                Long seen = MINE_THUNDER_STORM_SEEN.get(userName);
                if (mineThunderStormAt > 0 && now - mineThunderStormAt < 3_000L && (seen == null || seen < mineThunderStormAt)) {
                    MINE_THUNDER_STORM_SEEN.put(userName, mineThunderStormAt);
                    MINE_STUN.put(userName, now + MINE_STORM_STUN_MS);
                    stormDmg = mineCoatHit(items, Math.max(1, (int) Math.round(tatk * MINE_STORM_MULT)));
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
                int atk = mineCoatHit(items, S5MineMobs.TYPES.get(S5MineMobs.RAID_BOSS_TYPE).atk());
                int bx = bs != null && bs.frozenUntil > now ? bs.fx : boss.x, by = bs != null && bs.frozenUntil > now ? bs.fy : boss.y;
                if (nf <= now) {
                    out.put("fireAt", nf);
                    if (Math.max(Math.abs(x - bx), Math.abs(y - by)) <= MINE_RAID_FIRE_RANGE) fireDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 2.5);
                }
                if (ng <= now) { out.put("gasAt", ng); gasDmg = (int) Math.min(Integer.MAX_VALUE / 4, atk * 1.2); }
            }
        }
        if (coldDmg + bzDmg + fireDmg + gasDmg + heatDmg + lavaDmg + shockDmg + stormDmg <= 0) return out;
        int envDmg = coldDmg + bzDmg + fireDmg + gasDmg + heatDmg + lavaDmg + shockDmg + stormDmg;
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
        if (hp <= 0) {
            boolean spell = fireDmg + gasDmg > 0, burnt = lavaDmg > 0 && !spell, shocked = shockDmg + stormDmg > 0 && !spell && !burnt; // [2026-11-05] 천둥 황소
            if (spell || burnt || shocked) lock = mineLockUp(userName, lock, out); // 대주교 마법 = 전투 사망(최대 체력 잠금), [2026-10-28] 용암도(보스전)
            hp = mineHpCap(lv, lock, rb);
            mineDisengageAll(userName);
            MINE_COLD.remove(userName);
            out.put("died", true);
            out.put("message", shocked ? "⚡ 천둥에 감전되어 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out) : burnt ? "🌋 용암에 데어 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out) : spell ? "💀 타락한 대주교의 " + (fireDmg > 0 ? "불벼락" : "어둠가스") + "에 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요." + mineLockText(out)
                    : heatDmg > 0 && coldDmg + bzDmg == 0 ? (map.sun() > 0 ? "☀️ 뜨거운 햇볕에 쓰러졌어요... " : "🥵 더위를 견디지 못하고 쓰러졌어요... ") + mineRespawn(userName, items, now) + " 깨어났어요."
                    : "🥶 " + (bzDmg > 0 ? "눈바람에 휩쓸려" : "추위를 견디지 못하고") + " 쓰러졌어요... " + mineRespawn(userName, items, now) + " 깨어났어요.");
        }
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
        return MINE_ITEM_WIDE_PICK.equals(code) ? 1 : MINE_ITEM_DETECT_PICK.equals(code) ? 2 : MINE_ITEM_LINE_PICK.equals(code) ? 3 : 0;
    }
    /** 단계별 효과(인덱스 = 단계, 0 = 없음). 장화 = 화면 한 칸 이동 시간(ms, 기본 140 -> 약 +11% / +25% / +40%). */
    //  [2026-10-25] 왕도 대장장이 단계 추가: 반지/목걸이 3단계, 장화 4단계(88ms = 약 +60%).
    static final int[] MINE_PLENTY_DOUBLE_PCT = { 0, 3, 8, 15 };
    static final int[] MINE_ABYSS_UP_PCT = { 0, 2, 5, 10 };
    static final double[] MINE_SAND_GEM_MULT = { 1.0, 1.3, 1.6, 2.2 };
    static final int[] MINE_STEP_MS = { 140, 126, 112, 100, 88 };
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
            new String[][][] { { mineCost(4, 20) }, { mineCost(5, 5), mineCost(8, 1) }, { mineCost(5, 40), mineCost(8, 5), mineSeven(2), mineBar(3, 2), mineBar(2, 1), mineCut(4, 1) } },
            new String[] { "보석이 나올 확률 1.3배", "보석이 나올 확률 1.6배", "보석이 나올 확률 2.2배" }, 2),
        new MineGear("MIND_NECKLACE", "정신의 목걸이", "necklace", // = MINE_ITEM_MIND_NECKLACE(아래 -- 앞에서 쓰면 forward reference)
            new String[][][] { { { S5MineMap.ORE_CODES[0], "30" }, { S5MineMap.ORE_CODES[1], "20" } }, { { S5MineMap.ORE_CODES[2], "40" }, { S5MineMap.ORE_CODES[3], "15" } },
                    { { S5MineMap.ORE_CODES[3], "60" }, { S5MineMap.ORE_CODES[4], "15" }, mineCost(0, 5) } }, // 1단계 = 구리 30 + 철 20(초보자도), 2단계 은 40 + 금 15, 3단계 금 60 + 미스릴 15 + 재스퍼 5
            new String[] { "몬스터를 쓰러뜨리면 경험치 +10%", "몬스터를 쓰러뜨리면 경험치 +20%", "몬스터를 쓰러뜨리면 경험치 +30%" }),
        new MineGear(MINE_ITEM_SWIFT_BOOTS, "신속의 장화", "boots",
            new String[][][] { { mineCost(8, 1) }, { mineCost(8, 2) }, { mineCost(8, 12), mineStar(2), mineBar(1, 1) }, { mineCost(8, 15), mineSeven(3), mineBar(4, 1), mineBar(1, 2), mineCut(3, 1) } },
            new String[] { "이동 속도 +11%", "이동 속도 +25%", "이동 속도 +40%", "이동 속도 +60%" }, 3),
        new MineGear(MINE_ITEM_WIDE_PICK, "광역 곡괭이", "tool",
            new String[][][] { { mineCost(8, 3), mineSeven(5), mineBar(0, 1), mineBar(1, 1) } },
            new String[] { "캘 때 주변 3x3칸도 함께 (칸마다 내구도 1)" }),
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
            new String[] { "공격력 +50% · 체력 회복 속도 -20%", "공격력 +65% · 체력 30% 이하일 때 공격력 추가 +20% · 체력 회복 속도 -20%" }, 0),
        // [2026-10-30] "기본 공격력의 55%로 두 번 때리는 장갑 -- 타격당 치명 +5%, 착용 중 초당 체력 -1%(자연 회복과 겹쳐 상쇄)"
        new MineGear("TWIN_GLOVES", "쌍격의 장갑", "gloves", // = MINE_ITEM_TWIN_GLOVES
            new String[][][] { { mineCost(4, 10), mineStar(1), mineBar(4, 1), mineBar(3, 2), mineCut(4, 1) },
                { mineCost(4, 20), mineCut(4, 3), mineBar(4, 3), mineBar(3, 5), mineStar(3), { S5MineMap.MOON_STONE_CODE, "2" }, { S5MineMap.seaCutCode(4), "2" } } }, // [2026-11-04] 2단계(닌자의 비급서는 안 씀)
            new String[] { "한 번에 두 번 공격(각 55%) · 타격마다 치명타 +5% · 끼고 있으면 초당 체력 -1%", "한 번에 두 번 공격(각 60%) · 타격마다 치명타 +8% · 끼고 있으면 초당 체력 -0.7%" }, 0),
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

    static int mineNeckSlotOf(String code) { return MINE_ITEM_MIND_NECKLACE.equals(code) ? 2 : MINE_ITEM_ASSASSIN_NECKLACE.equals(code) ? 3 : 1; }
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
    static int mineGloveSlotOf(String code) { return MINE_ITEM_REGEN_GLOVES.equals(code) ? 2 : MINE_ITEM_TWIN_GLOVES.equals(code) ? 4 : 3; }
    static String mineGloveCode(Map<String, Integer> items) {
        int s = items.getOrDefault(MINE_ITEM_GLOVE_SLOT, 0);
        if (s == 2 && items.getOrDefault(MINE_ITEM_REGEN_GLOVES, 0) > 0) return MINE_ITEM_REGEN_GLOVES;
        if (s == 3 && items.getOrDefault(MINE_ITEM_BERSERK_GLOVES, 0) > 0) return MINE_ITEM_BERSERK_GLOVES;
        if (s == 4 && items.getOrDefault(MINE_ITEM_TWIN_GLOVES, 0) > 0) return MINE_ITEM_TWIN_GLOVES; // [2026-10-30]
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
        if (g >= S5MineMap.NINJA_SHARD_GRADE && g < S5MineMap.NINJA_SHARD_GRADE + S5MineMap.WEAPON_SHARD_NAMES.length) return S5MineMap.WEAPON_SHARD_NAMES[g - S5MineMap.NINJA_SHARD_GRADE]; // [2026-10-29]
        if (g >= S5MineMap.ARMOR_SHARD_GRADE && g < S5MineMap.ARMOR_SHARD_GRADE + S5MineMap.ARMOR_SHARD_NAMES.length) return S5MineMap.ARMOR_SHARD_NAMES[g - S5MineMap.ARMOR_SHARD_GRADE]; // [2026-11-06]
        if (g == S5MineMap.GALAXY_GRADE) return S5MineMap.GALAXY_NAME; // [2026-11-06]
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

    /** action: quest(수색 임무 받기) / claim(닌자 100명 -> 비급서 + 달빛석) / boat(달빛석 1개 -> 1인용 배) / board(배 타고 뱃길로). 본섬에서만. */
    @Override
    @Transactional
    public HashMap<String, Object> mineArchi(String userName, String action) {
        if (!mineAccess(userName)) return mineClosed();
        HashMap<String, Object> gate = mineGate();
        if (gate != null) return gate;
        if (!mineArchiOpen(userName)) return mineGearResult(userName, false, "🚧 아직 준비 중이에요. 곧 열려요!"); // 본섬 NPC 일(신궁 임무/배/뱃길)은 아직 운영자만
        mineRow(userName, false);
        HashMap<String, Object> row = dao.selectMineUserForUpdate(userName);
        if (!S5MineMap.ARCHI.equals(strVal(row.get("MAP_ID"), ""))) return mineGearResult(userName, false, "다도해 본섬에서만 할 수 있어요.");
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
    static List<HashMap<String, Object>> mineEquipList(Map<String, Integer> items) {
        List<HashMap<String, Object>> out = new ArrayList<>();
        int ring = items.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        for (MineGear g : MINE_GEAR) if ("ring".equals(g.kind) && items.getOrDefault(g.code, 0) > 0) {
            int t = Math.min(items.getOrDefault(g.code, 0), g.cost.length);
            out.add(mineEquipRow("ring", g.code, g.name + " " + t + "단계", g.effect[t - 1], ring == (MINE_ITEM_PLENTY_RING.equals(g.code) ? 1 : 2)));
        }
        if (mineHasDragonRing(items)) out.add(mineEquipRow("ring", MINE_ITEM_DRAGON_RING, "용왕의 반지", "물 위를 걸을 수 있음", ring == MINE_RING_DRAGON));
        int coat = mineCoatDef(items);
        if (false) out.add(mineEquipRow("coat", MINE_ITEM_COAT1, "방한 외투", "한기 방어 1 · 받는 피해 2배", coat == 1));
        for (String bc : new String[] { MINE_ITEM_SAND_NECKLACE, MINE_ITEM_MIND_NECKLACE, MINE_ITEM_ASSASSIN_NECKLACE, MINE_ITEM_SWIFT_BOOTS }) { // [2026-10-28] 목걸이 / 신발 칸
            MineGear g = mineGear(bc);
            int t = g == null ? 0 : Math.min(items.getOrDefault(bc, 0), g.cost.length);
            if (t < 1) continue;
            boolean neck = !MINE_ITEM_SWIFT_BOOTS.equals(bc);
            out.add(mineEquipRow(neck ? "necklace" : "boots", bc, g.name + " " + t + "단계", g.effect[t - 1], neck ? bc.equals(mineNeckCode(items)) : mineBootsTier(items) > 0));
        }
        boolean pon = mineParasolOn(items); // [2026-10-28] 무기 칸 -- 칼(기본), 양산
        MineWeapon won = mineWeaponOn(items); // [2026-10-29] 드랍 무기(등급 = 색)
        out.add(mineEquipRow("weapon", "SWORD", "칼", "기본 무기", !pon && won == null));
        for (MineWeapon w : MINE_WEAPONS) {
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
        for (MineWeapon a : MINE_ARMORS) {
            int ag = mineWeaponGrade(items, a);
            if (ag < 0) continue;
            int ae = mineEnh(items, a.code);
            HashMap<String, Object> ar = mineEquipRow("armor", a.code, a.name + (ae > 0 ? " +" + ae : ""), a.effect(ag, ae) + " · Lv." + a.lv + "부터", a == aon);
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
            ar.put("moon", have.getOrDefault(S5MineMap.MOON_STONE_CODE, 0));
            ar.put("books", have.getOrDefault(S5MineMap.NINJA_BOOK_CODE, 0));
            ar.put("boatCosts", mineCostList(MINE_BOAT_COST, have));
            ar.put("boatCan", mineCostOk(MINE_BOAT_COST, have));
            out.put("archi", ar);
        }
        out.put("stepMs", mineStepMs(items));
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
        mineLookPayload(items, have, out, isMineAdmin(userName)); // [2026-10-24] 명품상점(외형)
        out.put("ores", mineOreList(userName));
    }

    HashMap<String, Object> mineGearResult(String userName, boolean ok, String message) {
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
        if (MINE_ITEM_REGEN_GLOVES.equals(code) || MINE_ITEM_BERSERK_GLOVES.equals(code) || MINE_ITEM_TWIN_GLOVES.equals(code)) { // [2026-10-28] 장갑 칸은 하나 -- 끼면 방한 장갑은 벗는다
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
        if (MINE_ITEM_SAND_NECKLACE.equals(code) || MINE_ITEM_MIND_NECKLACE.equals(code) || MINE_ITEM_ASSASSIN_NECKLACE.equals(code)) { // [2026-10-28] 목걸이는 하나 -- 걸린 걸 다시 누르면 풀기
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
                if (alv < ea.lv) return mineGearResult(userName, false, "🛡️ " + ea.name + "은(는) Lv." + ea.lv + "부터 입을 수 있어요. (지금 Lv." + alv + ")");
                if (pickGrade >= 0) dao.upsertMineItem(userName, ea.code, pickGrade + 1);
            }
            dao.upsertMineItem(userName, MINE_ITEM_ARMOR_SLOT, on ? ea.slot : 0);
            if (on) { dao.upsertMineItem(userName, MINE_ITEM_COAT_SLOT, 0); } // [2026-11-06] "갑옷과 옷은 한 칸" -- 외투는 벗는다, 용병이 입던 같은 갑옷은 내가
            int ag = pickGrade >= 0 ? pickGrade : mineWeaponGrade(it, ea);
            return mineGearResult(userName, true, on ? "🛡️ " + ea.name + "을(를) 장착했어요. " + ea.effect(ag, mineEnh(it, ea.code)) : "🛡️ " + ea.name + "을(를) 해제했어요.");
        }
        MineWeapon ew = mineWeaponOf(code);
        if (ew != null && pickGrade != -1) {
            if (pickGrade < 0 || pickGrade > ew.maxGrade || (mineWeaponMask(it, ew) & (1 << pickGrade)) == 0) return mineGearResult(userName, false, "그 등급의 " + ew.name + "은(는) 없어요.");
            HashMap<String, Object> wr = dao.selectMineUser(userName);
            int wlv = wr == null ? 1 : mineLv(wr);
            if (wlv < ew.lv) return mineGearResult(userName, false, "🗡️ " + ew.name + "은(는) Lv." + ew.lv + "부터 들 수 있어요. (지금 Lv." + wlv + ")");
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
                if (wlv < ew.lv) return mineGearResult(userName, false, "🗡️ " + ew.name + "은(는) Lv." + ew.lv + "부터 들 수 있어요. (지금 Lv." + wlv + ")");
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
        boolean dragon = MINE_ITEM_DRAGON_RING.equals(code);
        if (!dragon && (g == null || !"ring".equals(g.kind))) return mineGearResult(userName, false, "낄 수 없는 물건이에요.");
        String nm = dragon ? "용왕의 반지" : g.name;
        if (it.getOrDefault(code, 0) < 1) return mineGearResult(userName, false, nm + "이(가) 없어요. " + (dragon ? "바닷가 늙은 선장에게 받을 수 있어요." : "먼저 만들어 주세요."));
        int want = dragon ? MINE_RING_DRAGON : MINE_ITEM_PLENTY_RING.equals(code) ? 1 : 2, cur = it.getOrDefault(MINE_ITEM_RING_SLOT, 0);
        int next = cur == want ? 0 : want;
        if (mineWaterWalk(it) && next != MINE_RING_DRAGON) { // 물 위에 서 있으면 용왕의 반지를 빼면 빠진다
            HashMap<String, Object> row = dao.selectMineUser(userName);
            S5MineMap mp = row == null ? null : mineMapOf(row);
            int[] pos = mp == null ? null : minePos(row, mp, 1);
            if (pos != null && mp.inside(pos[0], pos[1]) && mp.terrainAt(pos[0], pos[1]) == '~')
                return mineGearResult(userName, false, "🌊 물 위에 서 있어서 용왕의 반지를 뺄 수 없어요. 땅으로 나간 뒤에 바꿔 끼세요.");
        }
        dao.upsertMineItem(userName, MINE_ITEM_RING_SLOT, next);
        return mineGearResult(userName, true, next == 0 ? "💍 " + nm + "을(를) 해제했어요." : "💍 " + nm + "을(를) 장착했어요." + (dragon ? " 물 위를 걸을 수 있어요." : "") + (cur != 0 && cur != next ? " (반지는 하나만 -- 끼던 반지는 뺐어요)" : ""));
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
        int lv = mineLv(row);
        Map<String, Integer> items = mineItems(userName);
        int rb = mineRebirth(items);
        long exp = Math.max(0, longOf(row.get("M_EXP"), 0L));
        boolean locked = items.getOrDefault(MINE_ITEM_HP_LOCK, 0) > 0;
        if ("rebirth".equals(action)) return minePriestRebirth(userName, row, items, lv, exp, rb, locked, now, out);
        if (locked) dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0); // [2026-10-25] 잠긴 최대 체력도 정상으로
        dao.updateMineCombat(userName, lv, exp, mineHpMax(lv, rb), now, 0);
        out.put("combat", mineCombatInfo(lv, exp, mineHpMax(lv, rb), now, rb));
        out.put("message", "✨ 성당 신부: 여신의 축복을! 체력이 모두 회복됐어요." + (locked ? " 🔓 잠겼던 최대 체력도 풀렸어요." : ""));
        return out;
    }

    /** [2026-10-28] 환생 -- 왕도에서, 99레벨 경험치 가득일 때만. 환생 +1(최대 5), 레벨 1 / 경험치 0, 체력은 새 최대치로 가득, 최대 체력 잠금도 풀린다. */
    HashMap<String, Object> minePriestRebirth(String userName, HashMap<String, Object> row, Map<String, Integer> items, int lv, long exp, int rb,
            boolean locked, long now, HashMap<String, Object> out) {
        out.put("ok", false);
        if (!S5MineMap.CITY.equals(mineMapOf(row).id)) { out.put("message", "🙏 성당 신부: 환생 의식은 왕도 성당에서만 올릴 수 있어요."); return out; }
        if (rb >= MINE_REBIRTH_MAX) { out.put("message", "🙏 성당 신부: 이미 " + MINE_REBIRTH_MAX + "번의 환생을 마쳤어요. 더 오를 곳이 없답니다."); return out; }
        if (!mineRebirthReady(lv, exp, rb)) {
            out.put("message", "🙏 성당 신부: 아직 때가 아니에요. Lv." + MINE_LV_MAX + "에서 경험치를 가득 채우면 다시 오세요. (지금 Lv." + lv
                    + (lv >= MINE_LV_MAX ? ", 경험치 " + exp + " / " + mineExpNeed(MINE_LV_MAX, rb) : "") + ")");
            return out;
        }
        if (mineEngaged(userName, now)) { out.put("message", "🙏 성당 신부: 싸움이 끝난 뒤에 오세요."); return out; }
        int nrb = rb + 1;
        dao.upsertMineItem(userName, MINE_ITEM_REBIRTH, nrb);
        if (locked) dao.upsertMineItem(userName, MINE_ITEM_HP_LOCK, 0);
        int hp = mineHpMax(1, nrb);
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
        out.put("message", "🕊️ 성당 신부: 여신이시여, 이 영혼에게 새 날개를! 환생 " + nrb + "단계 -- Lv.1(환생" + nrb + ")로 다시 태어났어요. "
                + "최대 체력 +" + (nrb * 50) + "% · 공격력 +" + (nrb * 50) + "% · 미스릴 이하 광물 x" + (1 + nrb) + " · 방한·햇빛 저항·동굴 밝기 +" + nrb + " · 필요 경험치 x" + mineRebirthMulText(MINE_RB_EXP_MUL, nrb) + ".");
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
        volatile HashMap<String, Object> look;
        volatile int merc = -1; // [2026-11-05] 데리고 다니는 용병([2026-11-06] 직업 0 전사 / 1 궁수 / 2 마법사, -1 = 없음 -- 지금은 관리자만)
        volatile int mercEl, mercSt, mercNi; // [2026-11-06] 용병 속성/단계/이름 번호(화면 이펙트·그림)
        MinePresence(String id) { this.id = id; }
    }
    static final java.util.concurrent.ConcurrentHashMap<String, MinePresence> MINE_PRESENCE = new java.util.concurrent.ConcurrentHashMap<>();
    static final long MINE_PRESENCE_TTL_MS = 10_000L;
    static final long MINE_BUBBLE_MS = 4_000L;
    static final int MINE_GREET_RANGE = 4;
    static final int MINE_GREET_STONES = 5;
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
        MinePresence me = minePresenceOf(userName);
        me.mapId = mapId; me.x = x; me.y = y; me.at = now;
        if (dir != null && dir.matches("up|down|left|right")) me.dir = dir;
        if (me.look == null || now - me.lookAt > 30_000L) { Map<String, Integer> lit = mineItems(userName); me.look = mineLookOf(lit); me.merc = mineMercOn(lit); if (me.merc >= 0) { me.mercEl = mineMercEl(lit, me.merc); me.mercSt = mineMercStage(mineMercLv(lit, me.merc)); me.mercNi = mineMercNi(lit, me.merc, "NM"); } me.lookAt = now; } // 외형은 30초마다 다시 읽는다([2026-11-05] 용병 단계도)
        for (java.util.Map.Entry<String, MinePresence> e : MINE_PRESENCE.entrySet()) {
            MinePresence o = e.getValue();
            if (o == me || now - o.at > MINE_PRESENCE_TTL_MS || !mapId.equals(o.mapId) || o.look == null) continue;
            HashMap<String, Object> m = new HashMap<>();
            m.put("id", o.id);
            m.put("x", o.x);
            m.put("y", o.y);
            m.put("d", o.dir);
            m.put("look", o.look);
            if (minePStealthOf(e.getKey(), now) == null) m.put("n", mineNick(e.getKey())); // [2026-11-05] "플레이어 위에 닉네임"(은신 중엔 숨김)
            if (now - o.bubbleAt < MINE_BUBBLE_MS) m.put("bubble", "안녕하세요!");
            if (o.merc >= 0 && minePStealthOf(e.getKey(), now) == null) { // [2026-11-05] 용병 -- 단계, 쉬는 중, 싸우는 몬스터 칸
                m.put("mc", o.merc);
                m.put("me", o.mercEl);
                m.put("ms", o.mercSt);
                m.put("ml", o.mercNi);
                MercSt ms = MINE_MERC.get(e.getKey());
                if (ms != null && ms.restUntil > now) m.put("mr", 1);
                else if (ms != null && now - ms.atkAt < 2_500L) m.put("mt", new int[] { ms.tx, ms.ty });
            }
            if (mineFrozen(e.getKey(), now) != null) m.put("frozen", true);
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
        long[] hm = mineHermitNow(now); // [2026-10-30] 은둔한 대장장이
        if (hm != null && mapId.equals(mineHermitMaps()[(int) hm[0]])) { HashMap<String, Object> hr = new HashMap<>(); hr.put("x", hm[1]); hr.put("y", hm[2]); hr.put("until", hm[3]); out.put("hermit", hr); }
        if (hm != null && isMineAdmin(userName)) out.put("hermitAdmin", mineHermitMaps()[(int) hm[0]] + " (" + hm[1] + ", " + hm[2] + ")");
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
        int inMine = 0;
        for (MinePresence p : MINE_PRESENCE.values()) if (now - p.at <= MINE_PRESENCE_TTL_MS) inMine++;
        return "📢 패치 공지를 띄웠어요. 지금 광산에 있는 " + inMine + "명 화면 맨 위에 바로 보이고, 새로 들어오는 사람에게도 보여요."
                + "\n배포(서버 재시작)하면 저절로 사라져요. 미리 내리려면 /패치 끄기 (다른 문구: /패치 [내용], \" / \"로 줄바꿈)\n" + minePatchText;
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
            m.put("luckUntil", mineLuckUntil(items));
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
        m.put("atk", (int) Math.round(mineAtk(lv) * mineAtkMul(items) + mineWeaponFlat(items)));
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
    static final String[] MINE_LOOK_SLOTS = { "hat", "hair", "top", "bottom", "shoes", "wing" }; // [2026-10-29] wing = 환생 날개 염색(기본 흰색)
    static final String MINE_ITEM_GENDER = "LOOK_GENDER";
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
        new MineCos("BOT_PLEATED", "bottom", "검은 주름치마", "#2a2428", new String[][] { mineOre(2, 80), mineCost(0, 2) })
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
        if (c == null || (c.admin && !isMineAdmin(userName))) return mineGearResult(userName, false, "없는 상품이에요.");
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
    boolean isMineAdmin(String userName) {
        if (userName == null) return false;
        if (MINE_ADMIN_USERS != null) for (String u : MINE_ADMIN_USERS.split("\\|")) if (!u.trim().isEmpty() && u.trim().equals(userName.trim())) return true;
        return isEventAdmin(userName);
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
        if (MINE_OPEN) return true;
        if (userName == null || MINE_ADMIN_USERS == null) return false;
        for (String u : MINE_ADMIN_USERS.split("\\|")) if (u.trim().equals(userName.trim())) return true;
        return false;
    }

    // [2026-10-23] "광산/광산상점은 관리자만 열고, config로 일반 유저 오픈 여부를 결정" 요청 -- TBOT_S5_CONFIG.MINE_OPEN_YN('Y'면 전체 오픈, 기본 N),
    // MINE_ADMIN_USERS('|' 구분, 오픈 전에도 들어갈 수 있는 유저). /갱신으로 반영.
    static volatile boolean MINE_OPEN = false;

    static volatile String MINE_ADMIN_USERS = "";

    // ---- 탑(시즌 5)과 함께 쓰는 도우미(BotS5ServiceImpl에서 옮김) ----
    // /이벤트지급(관리자 전용) 실행 권한이 있는 유저명 목록 -- TBOT_S5_CONFIG.EVENT_ADMIN_USERS에
    // '|'로 구분해 저장(예: "일어난다람쥐/카단|다른관리자"). 이 시스템엔 별도 권한/역할 체계가
    // 없어서, 뽑기권처럼 실제 경제가치가 있는 걸 아무나 채팅으로 못 뿌리게 막는 유일한 장치다.
    // 비어있으면(기본값) 아무도 실행할 수 없다 -- 반드시 DB에 직접 세팅해야 활성화됨(의도적).
    static volatile String EVENT_ADMIN_USERS = "";
}
