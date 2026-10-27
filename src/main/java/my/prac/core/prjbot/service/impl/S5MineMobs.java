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
 * 단계(tier): 체력 18 x 1.457^(t-1), 공격 4 x 1.413^(t-1), 경험치 6 x 1.40^(t-1)([2026-10-28] 다운스케일, 예전 2 / 1.94 / 2.7).
 * 광산 레벨(1 + 4(t-1))의 공격력으로 3번쯤 때리면 쓰러진다.
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
        /** [2026-10-28] 성당 옛터: minion = 대주교의 수하(평소엔 없고 보스가 불러낼 때만, 쓰러지면 다시 부를 때까지 없음), fast = 플레이어보다 3배쯤 빨리 쫓아옴,
         *  stay = 제자리(쫓아오지 않음 -- 대주교). */
        public boolean minion, fast, stay;
        /** [2026-10-28] 다도해 신궁: ninja = 싸우는 중 가끔 은신(공격을 피하고 다음 한 방이 2배 -- BotS5ServiceImpl.mineAttack/mineCombatTick),
         *  clone = 닌자 두목의 분신(평소엔 없고 분신술 때만 나타남, 한 대 맞으면 사라짐 -- minion처럼 다룬다, 쫓아오지 않음). */
        public boolean ninja, clone;
        Type(String name, int tier, String kind, String color, String... maps) {
            this.name = name; this.tier = tier; this.kind = kind; this.color = color; this.maps = maps;
        }
        Type asNinja() { ninja = true; return this; }
        Type asClone() { minion = true; clone = true; ninja = true; return this; }
        Type asBoss() { boss = true; return this; }
        Type asMinion() { minion = true; fast = true; return this; }
        Type asStay() { stay = true; return this; }
        // [2026-10-28] "전체적 다운스케일링" -- 단계마다 체력 x2 -> x1.457, 공격 x1.94 -> x1.413, 경험치 x2.7 -> x1.40.
        //  플레이어(BotS5ServiceImpl: 레벨마다 체력/공격 x1.09, 필요 경험치 x1.12)와 같은 비율로 줄여 적정 레벨에서 3번쯤 때리면 쓰러지는 것과
        //  레벨당 처치 수 흐름은 그대로, 숫자만 작아졌다(예: 21단계 보스 체력 2억 -> 40만).
        public int hp() { return (int) Math.round(18 * Math.pow(1.457, tier - 1) * (boss ? 12 : 1)); }
        public int atk() { return (int) Math.round(4 * Math.pow(1.413, tier - 1) * (boss ? 1.4 : 1)); }
        public long exp() { return Math.round(6 * Math.pow(1.40, tier - 1) * (boss ? 15 : 1)); }
        /** 쓰러진 뒤 다시 나타날 때까지(ms). 보스는 따로 -- [2026-10-28] "보스 공통 맵 초기화 때 1번만" (BotS5ServiceImpl). */
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
        new Type("고대 광석 골렘", 9, "human", "#5a4a6a").asBoss(),
        // [2026-10-26] 설산 1~3층 -- "레벨 40대가 많아져 설산부터 강한 몬스터". 10~13단계(적정 Lv.37~49). 보스 뒤에 덧붙이고 보스 번호는 찾아서 쓴다.
        new Type("설산 늑대", 10, "quad", "#dce6f2", S5MineMap.SNOW1),
        new Type("서리 설인", 10, "human", "#cfe0f5", S5MineMap.SNOW1),
        new Type("얼음 박쥐", 11, "flyer", "#9fd0ff", S5MineMap.SNOW1, S5MineMap.SNOW2),
        new Type("빙결 트롤", 11, "human", "#a8c8e0", S5MineMap.SNOW2),
        new Type("서리 뱀", 12, "snake", "#8fb8e0", S5MineMap.SNOW2, S5MineMap.SNOW3),
        new Type("눈보라 정령", 12, "flyer", "#e8f4ff", S5MineMap.SNOW2, S5MineMap.SNOW3),
        new Type("서리 거인", 13, "human", "#7fa8d0", S5MineMap.SNOW3),
        new Type("얼음 골렘", 13, "human", "#bfe6ff", S5MineMap.SNOW3),
        // [2026-10-27] "사막 횡단 후 왕도까지 이어지는 곳 더 강하게"(초원 9~11단계) + 왕도 가는 길(11~13단계, 설산 쪽일수록 강함)
        new Type("켄타우로스", 9, "quad", "#a07a4a", S5MineMap.STEPPE),
        new Type("오크 족장", 10, "human", "#4a6a2a", S5MineMap.STEPPE),
        // [2026-10-28] "왕도 북쪽 샛길은 더 강하게(성당 옛터보다 살짝 약한 정도)" -- 샛길 몬스터 11~13단계 -> 17~19단계(그리폰은 초원 11단계 그대로)
        new Type("그리폰", 11, "flyer", "#c8a060", S5MineMap.STEPPE),
        new Type("몰락한 기사", 17, "human", "#6a6a7a", S5MineMap.ROYALROAD),
        new Type("산악 오우거", 18, "human", "#8a7a5a", S5MineMap.ROYALROAD),
        new Type("와이번", 18, "flyer", "#5a8a5a", S5MineMap.ROYALROAD),
        new Type("서리 와이번", 19, "flyer", "#9fc8e8", S5MineMap.ROYALROAD),
        new Type("바위 거인", 19, "human", "#7a7268", S5MineMap.ROYALROAD),
        // [2026-10-28] 성당 옛터 -- 일반 몬스터 없이 대주교(21단계 보스, 제자리, 수하 소환 + 불벼락/어둠가스)와 그 수하(20단계, 3배 빠름)만
        new Type("타락한 대주교", 21, "human", "#3a1a4a").asBoss().asStay(),
        new Type("타락한 성기사", 20, "human", "#8a7a9a").asMinion(),
        // [2026-10-28] 다도해(운영자만) -- 본섬 16~17, 신궁 내부 닌자 18~19, 섬은 뒤로 갈수록 16 -> 20단계(S5MineMap.ISLE_IDS 순서)
        new Type("야자게", 16, "crab", "#c86a3a", S5MineMap.ARCHI),
        new Type("해적 원숭이", 16, "small", "#8a5a2a", S5MineMap.ARCHI),
        new Type("바다 도적", 17, "human", "#3a4a6a", S5MineMap.ARCHI),
        // [2026-10-28] 닌자는 은신(asNinja), 도트도 닌자 모양(kind ninja -- 복면/목도리). 상급 닌자는 신궁 2층에도.
        new Type("닌자", 18, "ninja", "#22222e", S5MineMap.SHRINE).asNinja(),
        new Type("상급 닌자", 19, "ninja", "#4a1a2a", S5MineMap.SHRINE, S5MineMap.SHRINE2).asNinja(),
        new Type("동백 정령", 16, "blob", "#d4104f", S5MineMap.ISLE1),
        new Type("들고양이", 16, "quad", "#c8a070", S5MineMap.ISLE1),
        new Type("괭이갈매기", 16, "flyer", "#e8e8f0", S5MineMap.ISLE2),
        new Type("바위게", 17, "crab", "#8a7a6a", S5MineMap.ISLE2),
        new Type("늙은 바다거북", 17, "quad", "#5a7a4a", S5MineMap.ISLE3),
        new Type("소라게", 17, "crab", "#e0a0a0", S5MineMap.ISLE3),
        new Type("안개 유령", 17, "flyer", "#c8d0e0", S5MineMap.ISLE4),
        new Type("늪 뱀", 18, "snake", "#4a6a3a", S5MineMap.ISLE4),
        new Type("산호 골렘", 18, "human", "#ff7a8a", S5MineMap.ISLE5),
        new Type("독해파리", 18, "blob", "#b07cff", S5MineMap.ISLE5),
        new Type("달토끼", 19, "small", "#f4f0e0", S5MineMap.ISLE6),
        new Type("달빛 늑대", 19, "quad", "#b8c8e8", S5MineMap.ISLE6),
        new Type("용암 도마뱀", 20, "snake", "#e84a1a", S5MineMap.ISLE7),
        new Type("화염 정령", 20, "blob", "#ff6a2a", S5MineMap.ISLE7),
        // [2026-10-28] 신궁 3층 그림자 닌자(20단계), 닌자 두목의 방 -- 닌자 두목(21단계 보스, 은신 + 분신술)과 그 분신(분신술 때만, 두목과 똑같이 그린다)
        new Type("그림자 닌자", 20, "ninja", "#14141c", S5MineMap.SHRINE3).asNinja(),
        new Type("닌자 두목", 21, "ninja", "#2a0f1a").asBoss().asNinja(),
        new Type("닌자 두목의 분신", 21, "ninja", "#2a0f1a").asClone()
    ));

    /** 보스 종류 번호(TYPES 안) -- [2026-10-26] 보스 뒤에 설산 몬스터를 덧붙여서 "맨 끝"이 아니라 찾아서. 동굴 3층 보스(맵 리셋 때만). */
    public static final int BOSS_TYPE = indexOf("고대 광석 골렘");
    /** [2026-10-28] 성당 옛터 대주교와 그 수하. */
    public static final int RAID_BOSS_TYPE = indexOf("타락한 대주교");
    public static final int MINION_TYPE = indexOf("타락한 성기사");
    /** 대주교가 한 번에 거느릴 수 있는 수하 자리(맵 몬스터 목록에 미리 두고 평소엔 없는 것으로 친다). */
    static final int MINION_SLOTS = 6;
    /** [2026-10-28] 닌자 두목과 분신(분신술 때 다섯 중 넷 -- 맵 몬스터 목록에 자리를 미리 두고 평소엔 없는 것으로 친다). */
    public static final int SHRINE_BOSS_TYPE = indexOf("닌자 두목");
    public static final int CLONE_TYPE = indexOf("닌자 두목의 분신");
    public static final int CLONE_SLOTS = 4;

    private static int indexOf(String name) {
        for (int i = 0; i < TYPES.size(); i++) if (TYPES.get(i).name.equals(name)) return i;
        throw new IllegalStateException("mob type " + name);
    }

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
            // 왕도(북쪽)에 가까울수록 강하다. 왕도 안은 몬스터 없음(0). [2026-10-27] 6~7단계 -> 9~11단계("사막 횡단 후 왕도까지 더 강하게")
            case S5MineMap.STEPPE: return y > m.h * 0.66 ? 9 : y > m.h * 0.33 ? 10 : 11;
            case S5MineMap.ROYALROAD: return x > m.w * 0.66 ? 19 : x > m.w * 0.33 ? 18 : 17; // [2026-10-27] 설산(동쪽) 쪽일수록 강하다, [2026-10-28] 11~13 -> 17~19
            case S5MineMap.SNOW1: return y > m.h * 0.5 ? 10 : 11; // [2026-10-26] 설산 -- 위로 오를수록 강하다(설산 마을은 없음)
            case S5MineMap.SNOW2: return y > m.h * 0.5 ? 11 : 12;
            case S5MineMap.SNOW3: return y > m.h * 0.5 ? 12 : 13;
            // [2026-10-28] 다도해 -- 본섬은 마을(부두)에서 멀수록, 신궁은 입구에서 멀수록, 섬은 부두(북쪽)에서 남쪽으로 갈수록
            case S5MineMap.ARCHI: return Math.hypot(x - m.spawnX, y - m.spawnY) < 110 ? 16 : 17;
            case S5MineMap.SHRINE: return Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) < 100 ? 18 : 19; // [2026-10-28] 130x100으로 넓어져 45 -> 100(대부분 18, 맨 위 구석만 19)
            case S5MineMap.SHRINE2: return 19; // [2026-10-28] 층마다 한 단계씩(1층 18 -> 2층 19 -> 3층 20)
            case S5MineMap.SHRINE3: return 20;
            case S5MineMap.ISLE1: return 16;
            case S5MineMap.ISLE2: return y < m.h * 0.5 ? 16 : 17;
            case S5MineMap.ISLE3: return 17;
            case S5MineMap.ISLE4: return y < m.h * 0.5 ? 17 : 18;
            case S5MineMap.ISLE5: return 18;
            case S5MineMap.ISLE6: return 19;
            case S5MineMap.ISLE7: return 20;
            default: return 0;
        }
    }

    /** 맵별 몬스터 밀도(이동 가능 칸 몇 칸에 한 마리). */
    // [2026-10-28] "맵 전체적으로 몹 개체수를 조금 늘려줘" -- 모든 맵 약 25% 더(칸 수 / 마리를 0.8배로).
    private static int density(String mapId) {
        switch (mapId) {
            case S5MineMap.FIELD: return 160;
            case S5MineMap.DUNGEON: return 256;
            case S5MineMap.CAVE1: return 104;
            case S5MineMap.SNOW1: case S5MineMap.SNOW2: case S5MineMap.SNOW3: return 96; // [2026-10-26] 설산은 빽빽하게
            case S5MineMap.SHRINE: case S5MineMap.SHRINE2: case S5MineMap.SHRINE3: return 60; // [2026-10-28] 신궁은 닌자가 빽빽하게(수색 임무 100마리, 1~3층)
            default: return 128;
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
        if (S5MineMap.SEAROAD.equals(m.id)) return new ArrayList<>(); // [2026-10-28] "바닷길에 몬스터 없게" (바닷길 몬스터 종류는 순번 때문에 목록에 남겨 둔다)
        char[][] t = m.terrainGrid();
        Random r = new Random(seed);
        List<int[]> cand = new ArrayList<>();
        int walk = 0;
        for (int y = 1; y < m.h - 1; y++) for (int x = 1; x < m.w - 1; x++) {
            if (!S5MineMap.walkable(t[y][x])) continue;
            walk++;
            if (!mobCell(t[y][x]) || Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) < 9) continue;
            if (m.dockX() >= 0 && Math.abs(x - m.dockX()) + Math.abs(y - m.dockY()) < 9) continue;
            boolean nearGate = m.nearSafe(x, y, 1); // [2026-10-26] 모닥불(안전지대) 근처엔 몬스터를 두지 않는다
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
        boolean raid = S5MineMap.RUINS.equals(m.id);
        if (raid && m.bossX() >= 0) { // [2026-10-28] 대주교 수하 자리 -- 제단 둘레(평소엔 없음, 보스가 불러내면 그 자리에서 나타나 쫓아온다)
            for (int i = 0; i < MINION_SLOTS; i++) {
                double a = Math.PI * 2 * i / MINION_SLOTS;
                int x = m.bossX() + (int) Math.round(Math.cos(a) * 4), y = m.bossY() + 2 + (int) Math.round(Math.sin(a) * 3);
                if (x < 1 || y < 1 || x >= m.w - 1 || y >= m.h - 1 || !mobCell(t[y][x])) { x = m.bossX(); y = m.bossY() + 3; }
                out.add(new S5MineMap.Mob(out.size(), MINION_TYPE, x, y, new int[] { y * m.w + x }));
            }
        }
        if (S5MineMap.SHRINE_BOSS.equals(m.id) && m.bossX() >= 0) { // [2026-10-28] 닌자 두목 분신 자리(평소엔 없음, 분신술 때 서버가 자리를 정해 세운다)
            int[][] spots = m.shrineSplitSpots(0);
            for (int i = 0; i < CLONE_SLOTS; i++) {
                int x = spots[i + 1][0], y = spots[i + 1][1];
                out.add(new S5MineMap.Mob(out.size(), CLONE_TYPE, x, y, new int[] { y * m.w + x }));
            }
        }
        if (m.bossX() >= 0) {
            List<Integer> cells = new ArrayList<>();
            cells.add(m.bossY() * m.w + m.bossX());
            if (!raid) for (int[] dv : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) { // 대주교는 제단에서 움직이지 않는다
                int nx = m.bossX() + dv[0], ny = m.bossY() + dv[1];
                if (nx > 0 && ny > 0 && nx < m.w - 1 && ny < m.h - 1 && mobCell(t[ny][nx])) cells.add(ny * m.w + nx);
            }
            int[] ca = new int[cells.size()];
            for (int i = 0; i < ca.length; i++) ca[i] = cells.get(i);
            int bt = raid ? RAID_BOSS_TYPE : S5MineMap.SHRINE_BOSS.equals(m.id) ? SHRINE_BOSS_TYPE : BOSS_TYPE; // [2026-10-28] 닌자 두목의 방
            if (ca.length > 0) out.add(new S5MineMap.Mob(out.size(), bt, m.bossX(), m.bossY(), ca)); // 보스는 늘 목록 맨 끝
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
