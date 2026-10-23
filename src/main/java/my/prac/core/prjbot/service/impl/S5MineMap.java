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
import java.util.List;
import java.util.Random;

/**
 * [2026-10-23] 웹 광산 맵(255x255, 모든 유저 공용). 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 * 맵은 파일(classpath s5/mine_map_255.txt) 하나가 기준이다 -- 서버가 시작할 때 읽고, 칸마다 정해진 광석 등급이 있어
 * "(x, y)에 별빛석이 있다" 같은 정보를 유저들끼리 공유할 수 있다. 광석 등급(ORE 층)은 서버만 알고 화면에는 지형(TERRAIN 층)만 보낸다.
 * 파일 형식: "SIZE w h", "SPAWN x y", "TERRAIN" 다음 h줄(w글자), "ORE" 다음 h줄(칸마다 0~6, 0 = 광석 없음).
 * 지형 문자: . 풀밭 / , 흙길 / : 바위 지대 / V 마을 / H 다리 (이상 이동 가능) / ~ 물 / T 나무 / ^ 바위산 (이동 불가).
 * 맵을 다시 만들 때: main(출력 경로, 시드) 실행 -> 파일 교체(유저 위치는 이동 가능 칸이 아니면 마을로 돌아간다).
 */
public final class S5MineMap {

    public static final String RESOURCE = "s5/mine_map_255.txt";
    public static final String[] ORE_CODES = { "COPPER", "IRON", "SILVER", "GOLD", "MITHRIL", "STARSTONE" };
    public static final String[] ORE_NAMES = { "구리 광석", "철광석", "은광석", "금광석", "미스릴 원석", "별빛석" };
    /** 등급별 칸 비율(이동 가능 칸 중, 높은 등급부터): 별빛석 0.7%, 미스릴 3.3%, 금 8%, 은 15%, 철 28%, 나머지 구리. */
    private static final double[] TOP_SHARE = { 0.007, 0.033, 0.08, 0.15, 0.28 };

    public final int w, h, spawnX, spawnY;
    private final char[][] terrain;
    private final byte[][] ore;

    private S5MineMap(int w, int h, int spawnX, int spawnY, char[][] terrain, byte[][] ore) {
        this.w = w; this.h = h; this.spawnX = spawnX; this.spawnY = spawnY; this.terrain = terrain; this.ore = ore;
    }

    public static boolean walkable(char c) {
        return c == '.' || c == ',' || c == ':' || c == 'V' || c == 'H';
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

    private volatile String version;

    /** 지형 내용의 해시 -- 화면이 지형을 브라우저에 저장해 두고 바뀌었을 때만 다시 받는다. */
    public String version() {
        String v = version;
        if (v == null) {
            int hsh = 17;
            for (char[] row : terrain) hsh = 31 * hsh + Arrays.hashCode(row);
            v = version = w + "x" + h + "-" + Integer.toHexString(hsh);
        }
        return v;
    }

    public List<String> terrainRows() {
        List<String> out = new ArrayList<>(h);
        for (char[] row : terrain) out.add(new String(row));
        return out;
    }

    // ---------------------------------------------------------------- 파일 읽기
    private static volatile S5MineMap loaded;

    public static S5MineMap get() {
        S5MineMap m = loaded;
        if (m == null) {
            synchronized (S5MineMap.class) {
                if (loaded == null) loaded = load();
                m = loaded;
            }
        }
        return m;
    }

    private static S5MineMap load() {
        try (InputStream in = S5MineMap.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("광산 맵 파일이 없습니다: " + RESOURCE);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int w = 0, h = 0, sx = 0, sy = 0;
            char[][] t = null;
            byte[][] o = null;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] p = line.trim().split("\\s+");
                if ("SIZE".equals(p[0])) { w = Integer.parseInt(p[1]); h = Integer.parseInt(p[2]); }
                else if ("SPAWN".equals(p[0])) { sx = Integer.parseInt(p[1]); sy = Integer.parseInt(p[2]); }
                else if ("TERRAIN".equals(p[0])) {
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
            if (t == null || o == null) throw new IllegalStateException("광산 맵 파일 형식 오류");
            return new S5MineMap(w, h, sx, sy, t, o);
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
    /** 사용법: java ... S5MineMap 출력파일 [시드] [미리보기 PNG 폴더] */
    public static void main(String[] args) throws Exception {
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261023L;
        S5MineMap m = generate(255, 255, seed);
        try (PrintWriter pw = new PrintWriter(new File(args[0]), "UTF-8")) {
            pw.print("# S5 web mine map (255x255, shared by all users). Generated by S5MineMap.main seed " + seed + "\n");
            pw.print("# TERRAIN: . grass , path : rocky V village H bridge (walkable) / ~ water T tree ^ cliff (blocked)\n");
            pw.print("# ORE: 0 none, 1 COPPER 2 IRON 3 SILVER 4 GOLD 5 MITHRIL 6 STARSTONE\n");
            pw.print("SIZE " + m.w + " " + m.h + "\n");
            pw.print("SPAWN " + m.spawnX + " " + m.spawnY + "\n");
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
        System.out.println("walkable " + walk + " / " + (m.w * m.h) + ", ore counts " + Arrays.toString(cnt));
        if (args.length > 2) writePreview(m, new File(args[2]));
    }

    static S5MineMap generate(int w, int h, long seed) {
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
        // 마을(중앙)과 사방으로 뻗은 길(물은 다리)
        for (int y = cy - 6; y <= cy + 6; y++) for (int x = cx - 6; x <= cx + 6; x++) {
            if (Math.hypot(x - cx, y - cy) <= 6.5) t[y][x] = 'V';
        }
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] dv : dirs) {
            int x = cx, y = cy, wob = 0;
            for (int s = 0; s < 105; s++) {
                x += dv[0]; y += dv[1];
                if (r.nextDouble() < 0.18) wob += r.nextBoolean() ? 1 : -1;
                wob = Math.max(-3, Math.min(3, wob));
                int px = dv[0] == 0 ? x + wob : x, py = dv[1] == 0 ? y + wob : y;
                if (px < 1 || py < 1 || px >= w - 1 || py >= h - 1) break;
                if (t[py][px] == 'V') continue;
                t[py][px] = t[py][px] == '~' ? 'H' : ',';
            }
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }

        // 마을에서 걸어서 못 가는 칸은 바위산으로 막는다(갈 수 없는 곳에 광석이 있으면 안 되므로)
        boolean[][] reach = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { cx, cy });
        reach[cy][cx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] dv : dirs) {
                int nx = p[0] + dv[0], ny = p[1] + dv[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || reach[ny][nx] || !walkable(t[ny][nx])) continue;
                reach[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';

        // 광석: 마을 밖 이동 가능 칸마다 점수(광맥 노이즈 + 마을에서 먼 정도 + 바위 지대 + 약간의 운)를 매겨 순위대로 등급을 나눈다.
        List<double[]> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = t[y][x];
            if (!walkable(c) || c == 'V') continue;
            double d = Math.hypot(x - cx, y - cy) / maxD;
            double vein = rich.fbm(x / 18.0, y / 18.0);
            double score = 0.58 * vein + 0.20 * d + (c == ':' ? 0.05 : 0) + (c == 'H' || c == ',' ? -0.05 : 0) + 0.16 * r.nextDouble();
            cells.add(new double[] { score, x, y });
        }
        cells.sort((a, b) -> Double.compare(b[0], a[0]));
        byte[][] o = new byte[h][w];
        int n = cells.size(), idx = 0;
        for (int g = 6; g >= 2; g--) {
            int take = (int) Math.round(n * TOP_SHARE[6 - g]);
            for (int k = 0; k < take && idx < n; k++, idx++) o[(int) cells.get(idx)[2]][(int) cells.get(idx)[1]] = (byte) g;
        }
        for (; idx < n; idx++) o[(int) cells.get(idx)[2]][(int) cells.get(idx)[1]] = 1;
        return new S5MineMap(w, h, cx, cy, t, o);
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

    private static void writePreview(S5MineMap m, File dir) throws Exception {
        dir.mkdirs();
        int s = 3;
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
                default: c = 0x5a4e44;
            }
            int oc = m.walkable(x, y) ? oreCol[m.ore[y][x]] : (m.terrain[y][x] == '~' ? 0x1d3557 : 0x111111);
            for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) {
                ti.setRGB(x * s + dx, y * s + dy, c);
                oi.setRGB(x * s + dx, y * s + dy, oc);
            }
        }
        javax.imageio.ImageIO.write(ti, "png", new File(dir, "mine_terrain.png"));
        javax.imageio.ImageIO.write(oi, "png", new File(dir, "mine_ore.png"));
    }
}
