package my.prac.core.prjbot.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * [2026-10-24] 웹 광산 몬스터 -- "현재 맵에도 몬스터, 첫 맵은 구역을 나눠 마을 근처는 약한 몬스터부터, 바람의나라처럼 전투, 몬스터 선공 없음" 요청.
 * 종류 표(TYPES)와 맵별 배치(spawn)만 여기 둔다. 전투/체력/보상은 BotS5ServiceImpl.mineAttack.
 *
 * 위치: 몬스터는 집 칸 주변 2칸 안(cells)을 돌아다니는데, 칸은 시각으로 정해진다 -- cellAt(mob, ms) = cells[mix(id, ms / SLOT_MS) % cells.length].
 *   서버와 화면(tower_view.jsp TowerMine.mobPos)이 같은 식을 써서 위치를 주고받지 않는다. 맞은 몬스터는 그 자리에 잠깐 멈춘다(서버 메모리, 화면은 주기 조회).
 * 단계(tier) 1~9: 체력 18 x 2^(t-1), 공격 4 x 1.94^(t-1), 경험치 6 x 2.7^(t-1). 광산 레벨(1 + 4(t-1))의 공격력으로 3번쯤 때리면 쓰러진다.
 */
public final class S5MineMobs {
    private S5MineMobs() { }

    public static final long SLOT_MS = 3000L;

    public static final class Type {
        public final String name, kind, color;
        public final int tier;
        public final String[] maps;
        /** [2026-10-24] 보스: 먼저 공격(가까이 가면), 체력 x12 / 공격 x1.4 / 경험치 x15, 맵 리셋 때만 나타나고 잡히면 다음 리셋까지 없다. */
        public boolean boss;
        Type(String name, int tier, String kind, String color, String... maps) {
            this.name = name; this.tier = tier; this.kind = kind; this.color = color; this.maps = maps;
        }
        Type asBoss() { boss = true; return this; }
        public int hp() { return (int) Math.round(18 * Math.pow(2.0, tier - 1) * (boss ? 12 : 1)); }
        public int atk() { return (int) Math.round(4 * Math.pow(1.94, tier - 1) * (boss ? 1.4 : 1)); }
        public int exp() { return (int) Math.round(6 * Math.pow(2.7, tier - 1) * (boss ? 15 : 1)); }
        /** 쓰러진 뒤 다시 나타날 때까지(ms). */
        public long respawnMs() { return 20_000L + tier * 4_000L; }
    }

    /** kind(화면 도트 모양): small 작은 짐승, quad 네발짐승, human 사람형, flyer 날짐승/유령, crab 게/전갈, blob 물컹이, snake 뱀. */
    public static final List<Type> TYPES = Collections.unmodifiableList(java.util.Arrays.asList(
        new Type("다람쥐", 1, "small", "#b5651d", S5MineMap.FIELD),
        new Type("토끼", 1, "small", "#eeeeee", S5MineMap.FIELD),
        new Type("여우", 2, "quad", "#e07a2a", S5MineMap.FIELD),
        new Type("너구리", 2, "quad", "#8a8580", S5MineMap.FIELD),
        new Type("늑대", 3, "quad", "#8f969e", S5MineMap.FIELD),
        new Type("멧돼지", 3, "quad", "#6b4a30", S5MineMap.FIELD),
        new Type("곰", 4, "quad", "#3b2a1e", S5MineMap.FIELD),
        new Type("산적", 4, "human", "#7a3a2a", S5MineMap.FIELD),
        new Type("게", 3, "crab", "#d8432c", S5MineMap.BEACH),
        new Type("해파리", 3, "blob", "#7fc8ff", S5MineMap.BEACH),
        new Type("해적", 4, "human", "#2b2b3a", S5MineMap.BEACH),
        new Type("전갈", 4, "crab", "#c9962e", S5MineMap.DESERT),
        new Type("모래뱀", 5, "snake", "#b9a24a", S5MineMap.DESERT),
        new Type("사막 도적", 6, "human", "#c08a4a", S5MineMap.DESERT),
        new Type("박쥐", 2, "flyer", "#4a3a5a", S5MineMap.DUNGEON),
        new Type("동굴 박쥐", 4, "flyer", "#5a3a6a", S5MineMap.CAVE1),
        new Type("두더지", 5, "small", "#6a5040", S5MineMap.CAVE1),
        new Type("돌 골렘", 6, "human", "#8a8580", S5MineMap.CAVE1),
        new Type("원숭이", 5, "small", "#9a6a3a", S5MineMap.ISLAND),
        new Type("독두꺼비", 5, "blob", "#4aa040", S5MineMap.ISLAND),
        new Type("해적 선장", 6, "human", "#a02828", S5MineMap.ISLAND),
        new Type("해골 병사", 6, "human", "#e8e0d0", S5MineMap.CASTLE),
        new Type("유령", 7, "flyer", "#d8e8ff", S5MineMap.CASTLE),
        new Type("흑기사", 8, "human", "#2a2a3a", S5MineMap.CASTLE),
        new Type("눈늑대", 7, "quad", "#f0f4ff", S5MineMap.NORTH),
        new Type("설인", 8, "human", "#e8f0ff", S5MineMap.NORTH),
        new Type("얼음 정령", 9, "flyer", "#8fe8ff", S5MineMap.NORTH),
        // [2026-10-24] 모래폭풍 너머 초원(왕도 가는 길) -- 목록 끝에만 덧붙인다(앞 번호는 화면 그림 번호라 그대로)
        new Type("들소", 6, "quad", "#5a3a26", S5MineMap.STEPPE),
        new Type("오크 전사", 7, "human", "#5a7a3a", S5MineMap.STEPPE),
        new Type("하피", 7, "flyer", "#c08a8a", S5MineMap.STEPPE),
        // [2026-10-24] 광산 동굴 2층/3층, 바닷길(용궁), 3층 보스
        new Type("어둠 거미", 6, "crab", "#3a2a4a", S5MineMap.CAVE2),
        new Type("망령 광부", 7, "human", "#8a9a8a", S5MineMap.CAVE2),
        new Type("수정 골렘", 8, "human", "#8fd8ff", S5MineMap.CAVE3),
        new Type("심연 박쥐", 9, "flyer", "#3a1a5a", S5MineMap.CAVE3),
        new Type("꽃게", 4, "crab", "#e0603a", S5MineMap.SEAROAD),
        new Type("해마", 4, "small", "#f0b030", S5MineMap.SEAROAD),
        new Type("문어", 5, "blob", "#c0508a", S5MineMap.SEAROAD),
        new Type("바다뱀", 5, "snake", "#2f7ab0", S5MineMap.SEAROAD),
        new Type("고대 광석 골렘", 9, "human", "#5a4a6a").asBoss()
    ));

    /** 보스 종류 번호(TYPES 안). */
    public static final int BOSS_TYPE = TYPES.size() - 1;

    /** 그 칸의 몬스터 단계(0 = 몬스터 없음). 평원은 마을에서 멀수록 강하다. */
    static int tierAt(S5MineMap m, int x, int y) {
        switch (m.id) {
            case S5MineMap.FIELD: {
                double d = Math.hypot(x - m.spawnX, y - m.spawnY);
                return d < 40 ? 1 : d < 68 ? 2 : d < 98 ? 3 : 4;
            }
            case S5MineMap.BEACH: return y < m.h * 0.35 ? 3 : 4;
            case S5MineMap.DESERT: return y > m.h * 0.6 ? 4 : y > m.h * 0.3 ? 5 : 6;
            case S5MineMap.CAVE1: {
                int d = Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY); // 입구(아래 가운데)에서 멀수록
                return d < 40 ? 4 : d < 75 ? 5 : 6;
            }
            case S5MineMap.DUNGEON: return 2;
            case S5MineMap.CASTLE: return S5MineMap.inRect(S5MineMap.CASTLE_KEEP, x, y) ? 8 : S5MineMap.inRect(S5MineMap.CASTLE_WALL, x, y) ? 7 : 6;
            case S5MineMap.ISLAND: return Math.hypot(x - m.spawnX, y - m.spawnY) < 30 ? 5 : 6;
            case S5MineMap.NORTH: return y > m.h * 0.66 ? 7 : y > m.h * 0.33 ? 8 : 9;
            case S5MineMap.CAVE2: return Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) < 40 ? 6 : 7;
            case S5MineMap.CAVE3: return Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) < 40 ? 8 : 9;
            case S5MineMap.SEAROAD: return y > m.h * 0.5 ? 4 : 5; // 용궁(북쪽)에 가까울수록
            case S5MineMap.STEPPE: return y > m.h * 0.5 ? 6 : 7; // 왕도(북쪽)에 가까울수록 강하다. 왕도 안은 몬스터 없음(0)
            default: return 0;
        }
    }

    /** 맵별 몬스터 밀도(이동 가능 칸 몇 칸에 한 마리). */
    private static int density(String mapId) {
        switch (mapId) {
            case S5MineMap.FIELD: return 200;
            case S5MineMap.DUNGEON: return 320;
            case S5MineMap.CAVE1: return 130;
            default: return 160;
        }
    }

    /** 그 맵/단계에 나오는 종류들(없으면 단계를 낮춰 가며 찾는다). */
    private static List<Integer> typesFor(String mapId, int tier) {
        for (int t = tier; t >= 1; t--) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < TYPES.size(); i++) {
                Type ty = TYPES.get(i);
                if (ty.tier != t) continue;
                for (String mm : ty.maps) if (mm.equals(mapId)) out.add(i);
            }
            if (!out.isEmpty()) return out;
        }
        return Collections.emptyList();
    }

    /** 몬스터가 설 수 있는 칸 -- 이동 가능 칸 중 마을/부두/다리 제외. */
    private static boolean mobCell(char c) {
        return S5MineMap.walkable(c) && c != 'V' && c != 'P' && c != 'H';
    }

    static List<S5MineMap.Mob> spawn(S5MineMap m, long seed) {
        char[][] t = m.terrainGrid();
        Random r = new Random(seed);
        List<int[]> cand = new ArrayList<>();
        int walk = 0;
        for (int y = 1; y < m.h - 1; y++) for (int x = 1; x < m.w - 1; x++) {
            if (!S5MineMap.walkable(t[y][x])) continue;
            walk++;
            if (!mobCell(t[y][x]) || Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) < 9) continue;
            if (m.dockX() >= 0 && Math.abs(x - m.dockX()) + Math.abs(y - m.dockY()) < 9) continue;
            boolean nearGate = false;
            for (S5MineMap.Portal p : m.portals) if (Math.abs(x - p.x) + Math.abs(y - p.y) < 5) { nearGate = true; break; }
            for (S5MineMap.Npc n : m.npcs()) if (Math.abs(x - n.x) + Math.abs(y - n.y) < 5) { nearGate = true; break; }
            if (!nearGate) cand.add(new int[] { x, y });
        }
        Collections.shuffle(cand, r);
        int want = walk / density(m.id);
        boolean[][] taken = new boolean[m.h][m.w];
        List<S5MineMap.Mob> out = new ArrayList<>();
        for (int[] c : cand) {
            if (out.size() >= want) break;
            int x = c[0], y = c[1];
            boolean near = false;
            for (int dy = -4; dy <= 4 && !near; dy++) for (int dx = -4; dx <= 4; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < m.w && ny < m.h && taken[ny][nx]) { near = true; break; }
            }
            if (near) continue;
            List<Integer> types = typesFor(m.id, tierAt(m, x, y));
            if (types.isEmpty()) continue;
            int type = types.get(r.nextInt(types.size()));
            // [2026-10-24] "공격할 때 몬스터가 순간이동하는 것 같다" -- 돌아다니는 칸을 집 주변 1칸(상하좌우)으로 줄이고 집 칸을 맨 앞에(cellAt이 절반은 집에 머문다)
            List<Integer> cells = new ArrayList<>();
            cells.add(y * m.w + x);
            for (int[] dv : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
                int dx = dv[0], dy = dv[1], nx = x + dx, ny = y + dy;
                if (nx < 1 || ny < 1 || nx >= m.w - 1 || ny >= m.h - 1 || !mobCell(t[ny][nx])) continue;
                if (Math.abs(nx - m.spawnX) + Math.abs(ny - m.spawnY) < 7) continue;
                cells.add(ny * m.w + nx);
            }
            int[] ca = new int[cells.size()];
            for (int i = 0; i < ca.length; i++) ca[i] = cells.get(i);
            taken[y][x] = true;
            out.add(new S5MineMap.Mob(out.size(), type, x, y, ca));
        }
        if (m.bossX() >= 0) {
            List<Integer> cells = new ArrayList<>();
            cells.add(m.bossY() * m.w + m.bossX());
            for (int[] dv : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
                int nx = m.bossX() + dv[0], ny = m.bossY() + dv[1];
                if (nx > 0 && ny > 0 && nx < m.w - 1 && ny < m.h - 1 && mobCell(t[ny][nx])) cells.add(ny * m.w + nx);
            }
            int[] ca = new int[cells.size()];
            for (int i = 0; i < ca.length; i++) ca[i] = cells.get(i);
            if (ca.length > 0) out.add(new S5MineMap.Mob(out.size(), BOSS_TYPE, m.bossX(), m.bossY(), ca));
        }
        return out;
    }

    /** 32비트 정수 섞기 -- 화면 JS(Math.imul)와 같은 결과. 0 이상. */
    public static int mix(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** 그 시각 몬스터가 있는 칸(y * w + x). */
    public static int cellAt(S5MineMap.Mob mob, long ms) {
        int slot = (int) (ms / SLOT_MS);
        int r = mix(mob.id, slot) % (mob.cells.length * 2);
        return mob.cells[r < mob.cells.length ? r : 0]; // 절반 넘게 집 칸(0)에 머문다 -- 화면 TowerMine.mobPos와 같은 식
    }
}
