package my.prac.core.prjbot.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [2026-10-23] 웹 광산 맵(모든 유저 공용). 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 * 맵마다 파일 하나(classpath s5/...)가 기준이다 -- 처음 쓸 때 읽고, 칸마다 정해진 광석 등급이 있어 "(x, y)에 별빛석이 있다" 같은 정보를
 * 유저들끼리 공유할 수 있다. 광석 등급(ORE 층)은 서버만 알고 화면에는 지형(TERRAIN 층)과 포탈만 보낸다.
 * 파일 형식: "NAME 이름", "DARK 1"(어두운 맵, 선택), "SIZE w h", "SPAWN x y",
 *   "PORTAL x y 종류 대상맵 대상x 대상y 이름"(대상맵이 - 이면 잠긴 입구), "TERRAIN" 다음 h줄(w글자), "ORE" 다음 h줄(칸마다 0~6, 0 = 광석 없음).
 * 지형 문자: . 풀밭 / , 흙길 / : 바위 지대 / V 마을 / H 다리 / c 동굴 바닥 / r 자갈 바닥 (이상 이동 가능)
 *   ~ 물 / T 나무 / ^ 바위산 / # 동굴 벽 (이동 불가) / G 지역 통로 / M 동굴 입구 / U 나가는 길 / S 지하로 가는 문 (포탈: 그 칸으로 들어가면 이동, 잠겼으면 안내).
 * 맵을 다시 만들 때: main(출력 폴더, 시드[, 미리보기 PNG 폴더]) -> 파일 교체(저장 위치가 못 서는 칸이 되면 그 맵 시작점으로 돌아간다).
 */
public final class S5MineMap {

    public static final String FIELD = "field";
    public static final String CAVE1 = "cave1";
    /** 맵 ID -> 파일. 새 맵(동서남북 지역, 지하 2층 등)은 여기에 추가하고 입구 PORTAL의 대상맵을 채우면 열린다. */
    private static final Map<String, String> RESOURCES = new LinkedHashMap<>();
    static {
        RESOURCES.put(FIELD, "s5/mine_map_255.txt");
        RESOURCES.put(CAVE1, "s5/mine_cave1.txt");
    }
    public static final String[] ORE_CODES = { "COPPER", "IRON", "SILVER", "GOLD", "MITHRIL", "STARSTONE" };
    public static final String[] ORE_NAMES = { "구리 광석", "철광석", "은광석", "금광석", "미스릴 원석", "별빛석" };
    /** 등급별 칸 비율(이동 가능 칸 중, 높은 등급부터: 별빛석, 미스릴, 금, 은, 철, 나머지 구리). 동굴이 평원보다 풍부하다. */
    private static final double[] FIELD_SHARE = { 0.007, 0.033, 0.08, 0.15, 0.28 };
    private static final double[] CAVE_SHARE = { 0.02, 0.06, 0.12, 0.20, 0.30 };

    public static final class Portal {
        public final int x, y, tx, ty;
        public final char kind;
        public final String target, label;
        Portal(int x, int y, char kind, String target, int tx, int ty, String label) {
            this.x = x; this.y = y; this.kind = kind; this.target = target; this.tx = tx; this.ty = ty; this.label = label;
        }
        public boolean locked() {
            return target == null || !RESOURCES.containsKey(target);
        }
    }

    public final String id, name;
    public final boolean dark;
    public final int w, h, spawnX, spawnY;
    private final char[][] terrain;
    private final byte[][] ore;
    public final List<Portal> portals;

    private S5MineMap(String id, String name, boolean dark, int w, int h, int spawnX, int spawnY, char[][] terrain, byte[][] ore, List<Portal> portals) {
        this.id = id; this.name = name; this.dark = dark; this.w = w; this.h = h; this.spawnX = spawnX; this.spawnY = spawnY;
        this.terrain = terrain; this.ore = ore; this.portals = Collections.unmodifiableList(portals);
    }

    public static boolean walkable(char c) {
        return ".,:VHcr".indexOf(c) >= 0;
    }

    public static boolean portalChar(char c) {
        return "GMUS".indexOf(c) >= 0;
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

    private volatile String version;

    /** 지형/포탈 내용의 해시 -- 화면이 지형을 브라우저에 저장해 두고 바뀌었을 때만 다시 받는다. */
    public String version() {
        String v = version;
        if (v == null) {
            int hsh = 17;
            for (char[] row : terrain) hsh = 31 * hsh + Arrays.hashCode(row);
            for (Portal p : portals) hsh = 31 * hsh + (p.x + "," + p.y + p.target + p.label + p.locked()).hashCode();
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
        return RESOURCES.containsKey(id) ? get(id).name : id;
    }

    // ---------------------------------------------------------------- 파일 읽기
    private static final ConcurrentHashMap<String, S5MineMap> LOADED = new ConcurrentHashMap<>();

    /** 맵 ID로 읽는다(없는 ID면 평원). */
    public static S5MineMap get(String id) {
        String key = id != null && RESOURCES.containsKey(id) ? id : FIELD;
        S5MineMap m = LOADED.get(key);
        if (m == null) {
            synchronized (S5MineMap.class) {
                m = LOADED.get(key);
                if (m == null) {
                    m = load(key, RESOURCES.get(key));
                    LOADED.put(key, m);
                }
            }
        }
        return m;
    }

    public static S5MineMap get() {
        return get(FIELD);
    }

    private static S5MineMap load(String id, String resource) {
        try (InputStream in = S5MineMap.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("광산 맵 파일이 없습니다: " + resource);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int w = 0, h = 0, sx = 0, sy = 0;
            String name = id;
            boolean dark = false;
            char[][] t = null;
            byte[][] o = null;
            List<Portal> portals = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] p = line.trim().split("\\s+");
                if ("NAME".equals(p[0])) name = line.trim().substring(4).trim();
                else if ("DARK".equals(p[0])) dark = "1".equals(p[1]);
                else if ("SIZE".equals(p[0])) { w = Integer.parseInt(p[1]); h = Integer.parseInt(p[2]); }
                else if ("SPAWN".equals(p[0])) { sx = Integer.parseInt(p[1]); sy = Integer.parseInt(p[2]); }
                else if ("PORTAL".equals(p[0])) {
                    String target = "-".equals(p[4]) ? null : p[4];
                    int tx = "-".equals(p[5]) ? 0 : Integer.parseInt(p[5]), ty = "-".equals(p[6]) ? 0 : Integer.parseInt(p[6]);
                    StringBuilder label = new StringBuilder();
                    for (int i = 7; i < p.length; i++) label.append(i > 7 ? " " : "").append(p[i]);
                    portals.add(new Portal(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3].charAt(0), target, tx, ty, label.toString()));
                } else if ("TERRAIN".equals(p[0])) {
                    t = new char[h][];
                    for (int y = 0; y < h; y++) t[y] = fixedRow(br.readLine(), w);
                } else if ("ORE".equals(p[0])) {
                    o = new byte[h][w];
                    for (int y = 0; y < h; y++) {
                        char[] r = fixedRow(br.readLine(), w);
                        for (int x = 0; x < w; x++) o[y][x] = (byte) (r[x] >= '1' && r[x] <= '6' ? r[x] - '0' : 0);
                    }
                }
            }
            if (t == null || o == null) throw new IllegalStateException("광산 맵 파일 형식 오류: " + resource);
            return new S5MineMap(id, name, dark, w, h, sx, sy, t, o, portals);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("광산 맵 파일 읽기 실패: " + e, e);
        }
    }

    private static char[] fixedRow(String line, int w) {
        char[] r = new char[w];
        Arrays.fill(r, '^');
        if (line != null) line.getChars(0, Math.min(w, line.length()), r, 0);
        return r;
    }

    // ---------------------------------------------------------------- 생성기 (맵 파일을 새로 만들 때만)
    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /** 사용법: java ... S5MineMap 출력폴더(src/main/resources/s5) [시드] [미리보기 PNG 폴더] */
    public static void main(String[] args) throws Exception {
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261023L;
        File dir = new File(args[0]);
        S5MineMap cave = generateCave(seed + 1);
        S5MineMap field = generateField(seed);
        // 평원 동굴 입구 <-> 동굴 나가는 길
        Portal mouth = null;
        for (Portal p : field.portals) if (p.kind == 'M') mouth = p;
        List<Portal> fp = new ArrayList<>();
        for (Portal p : field.portals) fp.add(p.kind == 'M' ? new Portal(p.x, p.y, 'M', CAVE1, cave.spawnX, cave.spawnY, p.label) : p);
        List<Portal> cp = new ArrayList<>();
        for (Portal p : cave.portals) cp.add(p.kind == 'U' ? new Portal(p.x, p.y, 'U', FIELD, mouth.x, mouth.y + 1, p.label) : p);
        field = new S5MineMap(field.id, field.name, field.dark, field.w, field.h, field.spawnX, field.spawnY, field.terrain, field.ore, fp);
        cave = new S5MineMap(cave.id, cave.name, cave.dark, cave.w, cave.h, cave.spawnX, cave.spawnY, cave.terrain, cave.ore, cp);
        write(field, new File(dir, "mine_map_255.txt"), seed);
        write(cave, new File(dir, "mine_cave1.txt"), seed + 1);
        if (args.length > 2) {
            writePreview(field, new File(args[2]), "field");
            writePreview(cave, new File(args[2]), "cave1");
        }
    }

    private static void write(S5MineMap m, File f, long seed) throws Exception {
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.print("# S5 web mine map '" + m.id + "' (shared by all users). Generated by S5MineMap.main seed " + seed + "\n");
            pw.print("# TERRAIN: . grass , path : rocky V village H bridge c cave floor r gravel (walkable) / ~ water T tree ^ cliff # cave wall (blocked)\n");
            pw.print("#          G region gate, M cave mouth, U way out, S door down (portals; target '-' = locked)\n");
            pw.print("# ORE: 0 none, 1 COPPER 2 IRON 3 SILVER 4 GOLD 5 MITHRIL 6 STARSTONE\n");
            pw.print("NAME " + m.name + "\n");
            if (m.dark) pw.print("DARK 1\n");
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
                case 'G': case 'M': case 'U': case 'S': c = 0xff2020; break;
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
