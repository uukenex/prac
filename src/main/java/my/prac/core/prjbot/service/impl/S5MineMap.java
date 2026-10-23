package my.prac.core.prjbot.service.impl;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * [2026-10-23] 웹 광산 맵(모든 유저 공용). 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 * 칸마다 정해진 광석 등급이 있어 "(x, y)에 별빛석이 있다" 같은 정보를 유저들끼리 공유할 수 있다. 광석 등급(ORE 층)은 서버만 알고 화면에는
 * 지형(TERRAIN 층), 포탈, NPC만 보낸다.
 * [2026-10-23 v3] "전체 맵은 2일 주기로 리셋 후 셔플" 요청 -- 맵을 파일에서 읽지 않고 주기(cycle)마다 시드로 서버가 직접 만든다(같은 주기면 언제
 *   만들어도 같은 맵). 주기 0(첫 리셋 전) 시드는 예전 맵 파일을 만든 시드(20261023)와 같아 평원/동굴 지형과 광석이 그대로다. 리셋 시각은
 *   RESET_ANCHOR_MS(2026-10-25 05:00 KST)부터 2일마다이고, 실제 전환(캔 칸 초기화 + 위치 마을로)은 BotS5ServiceImpl.mineGate가 한다.
 *   맵: 중앙 평원(field) / 광산 동굴 1층(cave1) / 북쪽 사막(desert, 모래바람으로 좌우로 밀림, 북쪽 끝에 잠긴 통로) /
 *   남쪽 바닷가(beach, 정박한 배 + 선장 NPC, 배는 아직 잠김 -- 추후 배로만 다른 맵 이동). 평원 마을에는 대장장이 NPC(곡괭이 강화).
 * 지형 문자: . 풀밭 / , 흙길 / : 바위 지대 / V 마을 / H 다리 / c 동굴 바닥 / r 자갈 바닥 / d 모래 / b 모래사장 / P 부두 (이상 이동 가능)
 *   ~ 물 / T 나무 / ^ 바위산 / # 동굴 벽 / h j k l 초가집(2x2: 왼위 오위 왼아래 오아래) / R 사암 / C 선인장 / q 배 / N NPC (이동 불가)
 *   G 지역 통로 / M 동굴 입구 / U 나가는 길 / S 지하로 가는 문 / B 배에 오르는 판자 (포탈: 바로 옆 칸에서 들어가면 이동, 잠겼으면 안내).
 * 미리보기: main(출력 폴더[, 주기]) -> 맵별 텍스트와 PNG.
 */
public final class S5MineMap {

    public static final String FIELD = "field";
    public static final String CAVE1 = "cave1";
    public static final String DESERT = "desert";
    public static final String BEACH = "beach";
    /** 지금 있는 맵 ID. 포탈 대상이 여기 없으면 잠긴 입구로 본다. */
    private static final List<String> IDS = Arrays.asList(FIELD, CAVE1, DESERT, BEACH);
    public static final String[] ORE_CODES = { "COPPER", "IRON", "SILVER", "GOLD", "MITHRIL", "STARSTONE" };
    public static final String[] ORE_NAMES = { "구리 광석", "철광석", "은광석", "금광석", "미스릴 원석", "별빛석" };
    /** 등급별 칸 비율(이동 가능 칸 중, 높은 등급부터: 별빛석, 미스릴, 금, 은, 철, 나머지 구리). 동굴이 평원보다 풍부하다. */
    private static final double[] FIELD_SHARE = { 0.007, 0.033, 0.08, 0.15, 0.28 };
    private static final double[] CAVE_SHARE = { 0.02, 0.06, 0.12, 0.20, 0.30 };
    /** 사막은 평원보다 조금 풍부하고 북쪽(끝)으로 갈수록 좋다. 바닷가는 평원과 같다. */
    private static final double[] DESERT_SHARE = { 0.01, 0.045, 0.10, 0.16, 0.28 };

    // ---------------------------------------------------------------- 2일 주기 리셋
    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
    /** 주기 0의 시드 = 예전 맵 파일을 만든 시드(평원 = 시드, 동굴 = 시드 + 1). */
    public static final long BASE_SEED = 20261023L;
    /** 첫 리셋 시각(이후 2일마다 같은 시각). 그 전까지는 주기 0. */
    public static final long RESET_ANCHOR_MS = java.time.ZonedDateTime.of(2026, 10, 25, 5, 0, 0, 0, KST).toInstant().toEpochMilli();
    public static final long CYCLE_MS = 2L * 86_400_000L;

    public static int cycleAt(long ms) {
        return ms < RESET_ANCHOR_MS ? 0 : 1 + (int) ((ms - RESET_ANCHOR_MS) / CYCLE_MS);
    }

    /** 다음 리셋 시각(epoch ms). */
    public static long nextResetAt(long ms) {
        return RESET_ANCHOR_MS + (long) cycleAt(ms) * CYCLE_MS;
    }

    static long seedOf(int cycle) {
        return BASE_SEED + cycle * 7919L;
    }

    private static volatile int activeCycle = 0;
    private static volatile Map<String, S5MineMap> world = null;

    /** 이 주기의 맵으로 바꾼다(리셋이 끝났을 때/서버 기동 후 DB의 주기를 확인했을 때). 같은 주기면 아무것도 안 한다. */
    public static synchronized void setActiveCycle(int cycle) {
        if (world != null && cycle == activeCycle) return;
        world = buildWorld(seedOf(cycle));
        activeCycle = cycle;
    }

    public static int activeCycle() {
        return activeCycle;
    }

    // ---------------------------------------------------------------- 맵 객체
    public static final class Portal {
        public final int x, y, tx, ty;
        public final char kind;
        public final String target, label;
        Portal(int x, int y, char kind, String target, int tx, int ty, String label) {
            this.x = x; this.y = y; this.kind = kind; this.target = target; this.tx = tx; this.ty = ty; this.label = label;
        }
        public boolean locked() {
            return target == null || !IDS.contains(target);
        }
    }

    /** NPC(그 칸은 이동 불가, 부딪히면 말을 건다). kind: smith = 대장장이(곡괭이 강화), captain = 선장(대사만). */
    public static final class Npc {
        public final int x, y;
        public final String kind, name, line;
        Npc(int x, int y, String kind, String name, String line) {
            this.x = x; this.y = y; this.kind = kind; this.name = name; this.line = line;
        }
    }

    public final String id, name;
    public final boolean dark;
    private int level;          // [2026-10-23] 광산 레벨(0 = 평원처럼 투구와 무관)
    private String requireItem; // [2026-10-23] 입장에 필요한 광산 아이템(없으면 null)
    private boolean wind;       // [2026-10-23] 모래바람(가만히 있어도 좌우로 밀림, 화면이 처리)
    private List<Npc> npcs = Collections.emptyList();

    public int level() { return level; }
    public String requireItem() { return requireItem; }
    public boolean wind() { return wind; }
    public List<Npc> npcs() { return npcs; }
    public final int w, h, spawnX, spawnY;
    private final char[][] terrain;
    private final byte[][] ore;
    public final List<Portal> portals;

    private S5MineMap(String id, String name, boolean dark, int w, int h, int spawnX, int spawnY, char[][] terrain, byte[][] ore, List<Portal> portals) {
        this.id = id; this.name = name; this.dark = dark; this.w = w; this.h = h; this.spawnX = spawnX; this.spawnY = spawnY;
        this.terrain = terrain; this.ore = ore; this.portals = Collections.unmodifiableList(portals);
    }

    public static boolean walkable(char c) {
        return ".,:VHcrdbP".indexOf(c) >= 0;
    }

    public static boolean portalChar(char c) {
        return "GMUSB".indexOf(c) >= 0;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    public boolean walkable(int x, int y) {
        return inside(x, y) && walkable(terrain[y][x]);
    }

    public char terrainAt(int x, int y) {
        return terrain[y][x];
    }

    /** 0 = 광석 없음, 1~6 = ORE_CODES 순서 + 1. */
    public int oreAt(int x, int y) {
        return inside(x, y) ? ore[y][x] : 0;
    }

    public Portal portalAt(int x, int y) {
        for (Portal p : portals) if (p.x == x && p.y == y) return p;
        return null;
    }

    public Npc npcAt(int x, int y) {
        for (Npc n : npcs) if (n.x == x && n.y == y) return n;
        return null;
    }

    private volatile String version;

    /** 지형/포탈/NPC 내용의 해시 -- 화면이 지형을 브라우저에 저장해 두고 바뀌었을 때(리셋 포함)만 다시 받는다. */
    public String version() {
        String v = version;
        if (v == null) {
            int hsh = 17;
            for (char[] row : terrain) hsh = 31 * hsh + Arrays.hashCode(row);
            for (Portal p : portals) hsh = 31 * hsh + (p.x + "," + p.y + p.target + p.label + p.locked()).hashCode();
            for (Npc n : npcs) hsh = 31 * hsh + (n.x + "," + n.y + n.kind + n.name + n.line).hashCode();
            hsh = 31 * hsh + (wind ? 1 : 0);
            v = version = id + "-" + w + "x" + h + "-" + Integer.toHexString(hsh);
        }
        return v;
    }

    public List<String> terrainRows() {
        List<String> out = new ArrayList<>(h);
        for (char[] row : terrain) out.add(new String(row));
        return out;
    }

    public static String nameOf(String id) {
        return get(id).name;
    }

    /** 맵 ID로(없는 ID면 평원). 지금 주기의 맵. */
    public static S5MineMap get(String id) {
        Map<String, S5MineMap> wv = world;
        if (wv == null) {
            synchronized (S5MineMap.class) {
                if (world == null) world = buildWorld(seedOf(activeCycle));
                wv = world;
            }
        }
        S5MineMap m = id == null ? null : wv.get(id);
        return m != null ? m : wv.get(FIELD);
    }

    public static S5MineMap get() {
        return get(FIELD);
    }

    // ---------------------------------------------------------------- 맵 묶음 만들기(주기마다)
    /** 대장장이 위치: 마을 가운데에서 (+3, -2) -- 북동쪽 초가집 바로 앞. */
    private static final int SMITH_DX = 3, SMITH_DY = -2;

    static Map<String, S5MineMap> buildWorld(long seed) {
        S5MineMap field = generateField(seed);
        S5MineMap cave = generateCave(seed + 1);
        S5MineMap desert = generateDesert(seed + 2);
        S5MineMap beach = generateBeach(seed + 3);

        Portal mouth = null, north = null, south = null;
        for (Portal p : field.portals) {
            if (p.kind == 'M') mouth = p;
            if (p.kind == 'G' && (north == null || p.y < north.y)) north = p;
            if (p.kind == 'G' && (south == null || p.y > south.y)) south = p;
        }
        // 평원: 동굴 입구 -> 동굴, 북쪽 통로 -> 사막(남쪽 입구), 남쪽 통로 -> 바닷가(북쪽 입구). 동/서 통로는 아직 잠김.
        List<Portal> fp = new ArrayList<>();
        for (Portal p : field.portals) {
            if (p == mouth) fp.add(new Portal(p.x, p.y, 'M', CAVE1, cave.spawnX, cave.spawnY, p.label));
            else if (p == north) fp.add(new Portal(p.x, p.y, 'G', DESERT, desert.spawnX, desert.spawnY, "북쪽 사막으로 가는 길"));
            else if (p == south) fp.add(new Portal(p.x, p.y, 'G', BEACH, beach.spawnX, beach.spawnY, "남쪽 바닷가로 가는 길"));
            else fp.add(p);
        }
        List<Portal> cp = new ArrayList<>();
        for (Portal p : cave.portals) cp.add(p.kind == 'U' ? new Portal(p.x, p.y, 'U', FIELD, mouth.x, mouth.y + 1, p.label) : p);
        List<Portal> dp = new ArrayList<>();
        for (Portal p : desert.portals) dp.add(FIELD.equals(p.target) ? new Portal(p.x, p.y, p.kind, FIELD, north.x, north.y + 1, p.label) : p);
        List<Portal> bp = new ArrayList<>();
        for (Portal p : beach.portals) bp.add(FIELD.equals(p.target) ? new Portal(p.x, p.y, p.kind, FIELD, south.x, south.y - 1, p.label) : p);

        // 마을 대장장이(곡괭이 강화) -- 마을 칸이라 원래 광석이 없고 다른 칸/광석 배치는 그대로다.
        int sx = field.spawnX + SMITH_DX, sy = field.spawnY + SMITH_DY;
        field.terrain[sy][sx] = 'N';
        List<Npc> fn = new ArrayList<>();
        fn.add(new Npc(sx, sy, "smith", "대장장이", "광석을 가져오면 곡괭이를 더 튼튼하게 만들어 주지. 좋은 광석일수록 크게 강화된다네."));

        Map<String, S5MineMap> out = new LinkedHashMap<>();
        out.put(FIELD, relink(field, fp, fn));
        S5MineMap c2 = relink(cave, cp, null);
        c2.level = 1; // 광산 동굴 1층 = 광산 레벨 1(투구 없이도 들어가지만 아주 어둡다)
        out.put(CAVE1, c2);
        S5MineMap d2 = relink(desert, dp, null);
        d2.wind = true;
        out.put(DESERT, d2);
        out.put(BEACH, relink(beach, bp, beach.npcs));
        return Collections.unmodifiableMap(out);
    }

    private static S5MineMap relink(S5MineMap m, List<Portal> portals, List<Npc> npcs) {
        S5MineMap n = new S5MineMap(m.id, m.name, m.dark, m.w, m.h, m.spawnX, m.spawnY, m.terrain, m.ore, portals);
        n.level = m.level;
        n.requireItem = m.requireItem;
        n.wind = m.wind;
        n.npcs = npcs == null ? Collections.<Npc>emptyList() : Collections.unmodifiableList(npcs);
        return n;
    }

    // ---------------------------------------------------------------- 미리보기
    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /** 사용법: java ... S5MineMap 출력폴더 [주기] -- 맵별 텍스트(지형/광석)와 PNG 미리보기. */
    public static void main(String[] args) throws Exception {
        int cycle = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        File dir = new File(args[0]);
        dir.mkdirs();
        for (S5MineMap m : buildWorld(seedOf(cycle)).values()) {
            write(m, new File(dir, "mine_" + m.id + ".txt"), seedOf(cycle));
            writePreview(m, dir, m.id);
        }
    }

    private static void write(S5MineMap m, File f, long seed) throws Exception {
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.print("# S5 web mine map '" + m.id + "' (shared by all users). Generated by S5MineMap.main seed " + seed + "\n");
            pw.print("# TERRAIN: . grass , path : rocky V village H bridge c cave floor r gravel (walkable) / ~ water T tree ^ cliff # cave wall h j k l thatched house 2x2 (blocked)\n");
            pw.print("#          G region gate, M cave mouth, U way out, S door down (portals; target '-' = locked)\n");
            pw.print("# ORE: 0 none, 1 COPPER 2 IRON 3 SILVER 4 GOLD 5 MITHRIL 6 STARSTONE\n");
            pw.print("NAME " + m.name + "\n");
            if (m.dark) pw.print("DARK 1\n");
            if (m.level > 0) pw.print("LEVEL " + m.level + "\n");
            if (m.requireItem != null) pw.print("REQUIRE " + m.requireItem + "\n");
            pw.print("SIZE " + m.w + " " + m.h + "\n");
            pw.print("SPAWN " + m.spawnX + " " + m.spawnY + "\n");
            for (Portal p : m.portals) {
                pw.print("PORTAL " + p.x + " " + p.y + " " + p.kind + " " + (p.target == null ? "- - -" : p.target + " " + p.tx + " " + p.ty) + " " + p.label + "\n");
            }
            pw.print("TERRAIN\n");
            for (char[] row : m.terrain) pw.print(new String(row) + "\n");
            pw.print("ORE\n");
            for (byte[] row : m.ore) {
                StringBuilder sb = new StringBuilder(m.w);
                for (byte b : row) sb.append((char) ('0' + b));
                pw.print(sb + "\n");
            }
        }
        int[] cnt = new int[7];
        int walk = 0;
        for (int y = 0; y < m.h; y++) for (int x = 0; x < m.w; x++) {
            if (m.walkable(x, y)) walk++;
            cnt[m.ore[y][x]]++;
        }
        System.out.println(m.id + ": walkable " + walk + " / " + (m.w * m.h) + ", ore counts " + Arrays.toString(cnt) + ", portals " + m.portals.size());
    }

    private static boolean[][] reachable(char[][] t, int sx, int sy) {
        int h = t.length, w = t[0].length;
        boolean[][] reach = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        reach[sy][sx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] dv : DIRS) {
                int nx = p[0] + dv[0], ny = p[1] + dv[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || reach[ny][nx] || !walkable(t[ny][nx])) continue;
                reach[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return reach;
    }

    /** 이동 가능 칸마다 점수를 매겨 순위대로 등급을 나눈다(share = 높은 등급부터 비율). skip = 광석 없는 칸(마을 등). */
    private static byte[][] assignOre(char[][] t, double[][] score, double[] share, boolean[][] skip) {
        int h = t.length, w = t[0].length;
        List<double[]> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!walkable(t[y][x]) || (skip != null && skip[y][x])) continue;
            cells.add(new double[] { score[y][x], x, y });
        }
        cells.sort((a, b) -> Double.compare(b[0], a[0]));
        byte[][] o = new byte[h][w];
        int n = cells.size(), idx = 0;
        for (int g = 6; g >= 2; g--) {
            int take = (int) Math.round(n * share[6 - g]);
            for (int k = 0; k < take && idx < n; k++, idx++) o[(int) cells.get(idx)[2]][(int) cells.get(idx)[1]] = (byte) g;
        }
        for (; idx < n; idx++) o[(int) cells.get(idx)[2]][(int) cells.get(idx)[1]] = 1;
        return o;
    }

    /** 평원(255x255): 가운데 마을, 사방 길 끝에 다른 지역으로 가는 통로(아직 잠김), 북서쪽 바위산에 광산 동굴 입구. */
    static S5MineMap generateField(long seed) {
        int w = 255, h = 255;
        Random r = new Random(seed);
        Noise elev = new Noise(seed * 31 + 1), moist = new Noise(seed * 31 + 2), rich = new Noise(seed * 31 + 3);
        int cx = w / 2, cy = h / 2;
        char[][] t = new char[h][w];
        double maxD = Math.hypot(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.hypot(x - cx, y - cy) / maxD;
                double e = elev.fbm(x / 46.0, y / 46.0) + 0.30 * d - 0.10; // 바깥으로 갈수록 높고 험하다
                double mo = moist.fbm(x / 34.0, y / 34.0);
                char c;
                if (e < 0.30) c = '~';
                else if (e > 0.74) c = (e > 0.80 && r.nextDouble() < 0.55) ? '^' : ':';
                else if (mo > 0.60) c = r.nextDouble() < 0.24 ? 'T' : '.';
                else if (mo < 0.36) c = ',';
                else c = r.nextDouble() < 0.035 ? 'T' : '.';
                t[y][x] = c;
            }
        }
        for (int y = cy - 6; y <= cy + 6; y++) for (int x = cx - 6; x <= cx + 6; x++) {
            if (Math.hypot(x - cx, y - cy) <= 6.5) t[y][x] = 'V';
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        // 마을에서 사방으로 맵 끝까지 길(물은 다리), 길 끝 테두리에 다른 지역으로 가는 통로
        List<Portal> portals = new ArrayList<>();
        String[] gateNames = { "동쪽 지역으로 가는 길", "서쪽 지역으로 가는 길", "남쪽 지역으로 가는 길", "북쪽 지역으로 가는 길" };
        for (int k = 0; k < DIRS.length; k++) {
            int[] dv = DIRS[k];
            int x = cx, y = cy, wob = 0, lastX = cx, lastY = cy;
            while (true) {
                x += dv[0]; y += dv[1];
                if (r.nextDouble() < 0.18) wob += r.nextBoolean() ? 1 : -1;
                wob = Math.max(-3, Math.min(3, wob));
                int px = dv[0] == 0 ? x + wob : x, py = dv[1] == 0 ? y + wob : y;
                if (px < 1 || py < 1 || px >= w - 1 || py >= h - 1) break;
                if (t[py][px] != 'V') t[py][px] = t[py][px] == '~' ? 'H' : ',';
                // 길이 옆으로 꺾일 때 끊기지 않게 이전 칸과 이어 준다
                if (Math.abs(px - lastX) + Math.abs(py - lastY) > 1) {
                    int jx = dv[0] == 0 ? lastX : px, jy = dv[1] == 0 ? lastY : py;
                    if (t[jy][jx] != 'V') t[jy][jx] = t[jy][jx] == '~' ? 'H' : ',';
                }
                lastX = px; lastY = py;
            }
            int gx = lastX + dv[0], gy = lastY + dv[1];
            t[gy][gx] = 'G';
            portals.add(new Portal(gx, gy, 'G', null, 0, 0, gateNames[k]));
        }
        // [2026-10-23] 마을 초가집(2x2, 이동 불가) -- 마을 안 대각선 네 곳. 원래 마을 칸(광석 없음)이고 난수를 쓰지 않아 다른 칸/광석 배치는 그대로다.
        int[][] houses = { { cx - 4, cy - 4 }, { cx + 3, cy - 4 }, { cx - 4, cy + 3 }, { cx + 3, cy + 3 } };
        for (int[] hs : houses) {
            t[hs[1]][hs[0]] = 'h'; t[hs[1]][hs[0] + 1] = 'j';
            t[hs[1] + 1][hs[0]] = 'k'; t[hs[1] + 1][hs[0] + 1] = 'l';
        }

        boolean[][] reach = reachable(t, cx, cy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';

        // 광산 동굴 입구: 북서쪽(40, 40)에서 가장 가까운, 아래(남쪽) 칸이 걸어서 갈 수 있는 바위산
        int mx = -1, my = -1;
        double best = Double.MAX_VALUE;
        for (int y = 2; y < h - 2; y++) for (int x = 2; x < w - 2; x++) {
            if (t[y][x] != '^' || !reach[y + 1][x]) continue;
            double d = Math.hypot(x - 40, y - 40);
            if (d < best) { best = d; mx = x; my = y; }
        }
        t[my][mx] = 'M';
        portals.add(new Portal(mx, my, 'M', CAVE1, 0, 0, "광산 동굴 1층"));

        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = t[y][x];
            skip[y][x] = c == 'V';
            double d = Math.hypot(x - cx, y - cy) / maxD;
            score[y][x] = 0.58 * rich.fbm(x / 18.0, y / 18.0) + 0.20 * d + (c == ':' ? 0.05 : 0) + (c == 'H' || c == ',' ? -0.05 : 0) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, FIELD_SHARE, skip);
        return new S5MineMap(FIELD, "중앙 평원", false, w, h, cx, cy, t, o, portals);
    }

    /** 광산 동굴 1층(96x96, 어두움): 셀룰러 오토마타 동굴. 아래 가운데가 나가는 길, 가장 먼 곳 벽에 지하로 가는 문(아직 잠김). 평원보다 광석이 풍부하고 안쪽일수록 좋다. */
    static S5MineMap generateCave(long seed) {
        int w = 96, h = 96;
        Random r = new Random(seed);
        Noise rich = new Noise(seed * 31 + 7);
        boolean[][] wall = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            wall[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 || r.nextDouble() < 0.45;
        }
        for (int it = 0; it < 5; it++) {
            boolean[][] nw = new boolean[h][w];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) { nw[y][x] = true; continue; }
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if ((dx != 0 || dy != 0) && wall[y + dy][x + dx]) n++;
                nw[y][x] = n >= 5 || (it < 2 && n <= 1);
            }
            wall = nw;
        }
        int ux = w / 2, uy = h - 2, sx = ux, sy = h - 3;
        // 입구에서 위로 굴을 뚫어 동굴 본체와 잇는다
        for (int y = sy; y >= 2; y--) {
            boolean hit = !wall[y][ux] && y < sy - 3;
            wall[y][ux] = false;
            if (ux + 1 < w - 2) wall[y][ux + 1] = false;
            if (hit) break;
        }
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = wall[y][x] ? '#' : 'c';
        boolean[][] reach = reachable(t, sx, sy);
        int[][] dist = new int[h][w];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        dist[sy][sx] = 0;
        int fx = sx, fy = sy;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (dist[p[1]][p[0]] > dist[fy][fx]) { fx = p[0]; fy = p[1]; }
            for (int[] dv : DIRS) {
                int nx = p[0] + dv[0], ny = p[1] + dv[1];
                if (!reach[ny][nx] || dist[ny][nx] >= 0) continue;
                dist[ny][nx] = dist[p[1]][p[0]] + 1;
                q.add(new int[] { nx, ny });
            }
        }
        int maxDist = Math.max(1, dist[fy][fx]);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (t[y][x] == 'c' && !reach[y][x]) t[y][x] = '#';
            else if (t[y][x] == 'c') {
                int walls = 0;
                for (int[] dv : DIRS) if (t[y + dv[1]][x + dv[0]] == '#') walls++;
                if (walls >= 2 && r.nextDouble() < 0.35) t[y][x] = 'r';
            }
        }
        t[uy][ux] = 'U';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(ux, uy, 'U', FIELD, 0, 0, "평원으로 나가기"));
        // 지하로 가는 문: 가장 먼 바닥 칸 옆 벽
        int dx0 = fx, dy0 = fy - 1;
        for (int[] dv : DIRS) {
            int nx = fx + dv[0], ny = fy + dv[1];
            if (t[ny][nx] == '#' && nx > 0 && ny > 0 && nx < w - 1 && ny < h - 1) { dx0 = nx; dy0 = ny; break; }
        }
        t[dy0][dx0] = 'S';
        portals.add(new Portal(dx0, dy0, 'S', null, 0, 0, "지하 2층으로 내려가는 문"));

        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            double d = dist[y][x] < 0 ? 0 : (double) dist[y][x] / maxDist;
            score[y][x] = 0.50 * rich.fbm(x / 12.0, y / 12.0) + 0.32 * d + (t[y][x] == 'r' ? 0.05 : 0) + 0.15 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, CAVE_SHARE, skip);
        return new S5MineMap(CAVE1, "광산 동굴 1층", true, w, h, sx, sy, t, o, portals);
    }


    /** [2026-10-23] 북쪽 사막(150x150): 모래(d) 위에 사암(R) 무더기, 선인장(C), 오아시스(물 + 야자수). 남쪽 가운데가 평원으로 돌아가는 통로,
     *  북쪽 끝까지 이어진 넓은 모래길 끝에 다음 지역으로 가는 통로(아직 잠김). 모래바람(wind)은 buildWorld가 켠다 -- 화면이 가만히 있어도 좌우로 민다.
     *  광석은 평원보다 조금 풍부하고 북쪽일수록 좋다. */
    static S5MineMap generateDesert(long seed) {
        int w = 150, h = 150;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 11), dune = new Noise(seed * 31 + 12), rich = new Noise(seed * 31 + 13);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double rk = rock.fbm(x / 20.0, y / 20.0), du = dune.fbm(x / 12.0, y / 28.0);
            char c = 'd';
            if (rk > 0.70) c = 'R';
            else if (rk > 0.64 && r.nextDouble() < 0.45) c = 'R';
            else if (r.nextDouble() < 0.012 + (du > 0.62 ? 0.012 : 0)) c = 'C';
            t[y][x] = c;
        }
        // 오아시스 3곳: 가운데 물, 둘레에 야자수(나무)
        for (int k = 0; k < 3; k++) {
            int ox = 20 + r.nextInt(w - 40), oy = 20 + r.nextInt(h - 50);
            double rad = 2.5 + r.nextDouble() * 2;
            for (int y = oy - 7; y <= oy + 7; y++) for (int x = ox - 7; x <= ox + 7; x++) {
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) continue;
                double d = Math.hypot(x - ox, y - oy);
                if (d < rad) t[y][x] = '~';
                else if (d < rad + 2.2) t[y][x] = r.nextDouble() < 0.28 ? 'T' : 'd';
            }
        }
        for (int x = 0; x < w; x++) { t[0][x] = 'R'; t[h - 1][x] = 'R'; }
        for (int y = 0; y < h; y++) { t[y][0] = 'R'; t[y][w - 1] = 'R'; }
        // 남쪽 입구 -> 북쪽 끝까지 3칸 너비 모래길(바람에 밀려도 막히지 않게 넓게)
        int sx = w / 2, sy = h - 2, px = sx;
        for (int y = h - 2; y >= 1; y--) {
            if (r.nextDouble() < 0.22) px += r.nextBoolean() ? 1 : -1;
            px = Math.max(4, Math.min(w - 5, px));
            for (int dx = -1; dx <= 1; dx++) t[y][px + dx] = 'd';
        }
        int nx = px;
        t[h - 1][sx] = 'G';
        t[sy][sx] = 'd';
        t[0][nx] = 'G';
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'R';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(sx, h - 1, 'G', FIELD, 0, 0, "중앙 평원으로 돌아가는 길"));
        portals.add(new Portal(nx, 0, 'G', null, 0, 0, "북쪽 끝 너머 (준비 중)"));

        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 16.0, y / 16.0) + 0.25 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, DESERT_SHARE, skip);
        return new S5MineMap(DESERT, "북쪽 사막", false, w, h, sx, sy, t, o, portals);
    }

    /** [2026-10-23] 남쪽 바닷가(120x96): 북쪽은 풀밭/숲, 가운데 모래사장(b), 남쪽은 바다. 부두(P) 끝에 배(q)가 정박해 있고 배 앞(부두 위)에
     *  선장 NPC. 배에 오르는 판자(B)는 아직 잠김 -- 추후 배를 통해서만 다른 맵으로 간다(바닷가에서 다른 새 맵으로 가는 길은 없음).
     *  북쪽 가운데가 평원으로 돌아가는 통로. */
    static S5MineMap generateBeach(long seed) {
        int w = 120, h = 96;
        Random r = new Random(seed);
        Noise shore = new Noise(seed * 31 + 21), rich = new Noise(seed * 31 + 22), green = new Noise(seed * 31 + 23);
        int pier = w / 2 + 10; // 부두 왼쪽 열
        int[] coast = new int[w];   // 이 열의 첫 바다 칸 y
        for (int x = 0; x < w; x++) coast[x] = (int) Math.round(h * 0.52 + (shore.fbm(x / 16.0, 3.7) - 0.5) * 16);
        int pierCoast = coast[pier];
        for (int x = Math.max(0, pier - 3); x <= Math.min(w - 1, pier + 14); x++) coast[x] = pierCoast; // 부두/배 근처 해안선은 반듯하게
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c;
            if (y >= coast[x]) c = '~';
            else if (y >= coast[x] - 7) c = 'b';
            else c = green.fbm(x / 14.0, y / 14.0) > 0.58 && r.nextDouble() < 0.45 ? 'T' : (r.nextDouble() < 0.05 ? 'T' : '.');
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) t[0][x] = 'T';
        for (int y = 0; y < h; y++) { if (t[y][0] != '~') t[y][0] = 'T'; if (t[y][w - 1] != '~') t[y][w - 1] = 'T'; }
        // 북쪽 입구에서 모래사장까지 흙길
        int sx = w / 2, sy = 1;
        for (int y = 1; y < coast[sx] - 7; y++) t[y][sx] = ',';
        t[0][sx] = 'G';
        // 부두(3칸 폭, 해안에서 바다로 13칸) + 그 동쪽에 배(6x8) + 부두와 배 사이 판자(B)
        int pierEnd = Math.min(h - 3, pierCoast + 12);
        for (int y = pierCoast - 1; y <= pierEnd; y++) for (int x = pier; x <= pier + 2; x++) t[y][x] = 'P';
        int shipX = pier + 4, shipY = pierCoast + 4, shipW = 6, shipH = 8;
        for (int y = shipY; y < Math.min(h - 1, shipY + shipH); y++) for (int x = shipX; x < Math.min(w - 1, shipX + shipW); x++) t[y][x] = 'q';
        int plankY = shipY + 3;
        t[plankY][pier + 3] = 'B';
        // 선장: 배 앞, 판자 바로 위 부두 칸(오른쪽 열) -- 판자 앞 칸(plankY)은 비워 둬서 배에 오를 수 있다.
        int capX = pier + 2, capY = plankY - 1;
        t[capY][capX] = 'N';
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = t[y][x] == 'P' ? '~' : 'T';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(sx, 0, 'G', FIELD, 0, 0, "중앙 평원으로 돌아가는 길"));
        portals.add(new Portal(pier + 3, plankY, 'B', null, 0, 0, "배에 오르기 (준비 중)"));

        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = t[y][x] == 'P' || Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.58 * rich.fbm(x / 15.0, y / 15.0) + (t[y][x] == 'b' ? 0.06 : 0) + 0.18 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, FIELD_SHARE, skip);
        S5MineMap m = new S5MineMap(BEACH, "남쪽 바닷가", false, w, h, sx, sy, t, o, portals);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(new Npc(capX, capY, "captain", "늙은 선장", "금광석 10개를 주면 신기한 걸 보여주지."));
        m.npcs = npcs;
        return m;
    }
    /** 격자 값 노이즈(부드럽게 보간) 4옥타브 합, 0~1. */
    private static final class Noise {
        private final long seed;
        Noise(long seed) { this.seed = seed; }
        private double lattice(int x, int y) {
            long n = x * 374761393L + y * 668265263L + seed * 2147483647L;
            n = (n ^ (n >>> 13)) * 1274126177L;
            n ^= n >>> 16;
            return (n & 0xFFFFFF) / (double) 0xFFFFFF;
        }
        private double value(double x, double y) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            fx = fx * fx * (3 - 2 * fx);
            fy = fy * fy * (3 - 2 * fy);
            double a = lattice(x0, y0), b = lattice(x0 + 1, y0), c = lattice(x0, y0 + 1), d = lattice(x0 + 1, y0 + 1);
            return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
        }
        double fbm(double x, double y) {
            double sum = 0, amp = 0.5, norm = 0, f = 1;
            for (int i = 0; i < 4; i++) { sum += amp * value(x * f, y * f); norm += amp; amp *= 0.5; f *= 2; }
            return sum / norm;
        }
    }

    private static void writePreview(S5MineMap m, File dir, String prefix) throws Exception {
        dir.mkdirs();
        int s = m.w > 150 ? 3 : 6;
        java.awt.image.BufferedImage ti = new java.awt.image.BufferedImage(m.w * s, m.h * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.image.BufferedImage oi = new java.awt.image.BufferedImage(m.w * s, m.h * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] oreCol = { 0x3a3a3a, 0xb87333, 0x8a8f98, 0xdfe6ee, 0xf2c230, 0x37d0c8, 0xff4fd8 };
        for (int y = 0; y < m.h; y++) for (int x = 0; x < m.w; x++) {
            int c;
            switch (m.terrain[y][x]) {
                case '.': c = 0x6abe4f; break;
                case ',': c = 0xc9a66b; break;
                case ':': c = 0x9c8f7d; break;
                case 'V': c = 0xe8d7a8; break;
                case 'H': c = 0x8b5a2b; break;
                case '~': c = 0x3b7dd8; break;
                case 'T': c = 0x2f7d32; break;
                case 'c': c = 0x6b5b4b; break;
                case 'r': c = 0x7d6d5c; break;
                case '#': c = 0x2a221c; break;
                case 'd': c = 0xe3c47a; break;
                case 'b': c = 0xecd9a0; break;
                case 'P': c = 0x8b5a2b; break;
                case 'R': c = 0xb8875a; break;
                case 'C': c = 0x3f8f3a; break;
                case 'q': c = 0x6b4220; break;
                case 'N': c = 0x2040ff; break;
                case 'G': case 'M': case 'U': case 'S': case 'B': c = 0xff2020; break;
                case 'h': case 'j': case 'k': case 'l': c = 0xd9b25a; break;
                default: c = 0x5a4e44;
            }
            int oc = portalChar(m.terrain[y][x]) ? 0xff2020 : m.walkable(x, y) ? oreCol[m.ore[y][x]] : (m.terrain[y][x] == '~' ? 0x1d3557 : 0x111111);
            for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) {
                ti.setRGB(x * s + dx, y * s + dy, c);
                oi.setRGB(x * s + dx, y * s + dy, oc);
            }
        }
        javax.imageio.ImageIO.write(ti, "png", new File(dir, prefix + "_terrain.png"));
        javax.imageio.ImageIO.write(oi, "png", new File(dir, prefix + "_ore.png"));
    }
}
