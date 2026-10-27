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
 *   남쪽 바닷가(beach, 정박한 배 + 선장 NPC, 배는 아직 잠김 -- 추후 배로만 다른 맵 이동) / 서쪽 동굴 = 요일 던전(dungeon, 캐면 그날 요일의 돌).
 *   동쪽 통로는 아직 잠김(성곽 맵 구상 중). 평원 마을에는 대장장이 NPC(곡괭이 강화).
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
    /** [2026-10-23] 서쪽 동굴 = 요일 던전(평원 서쪽 통로). 캐면 그날(KST) 요일의 돌이 나온다. */
    public static final String DUNGEON = "dungeon";
    /** [2026-10-24] 새 맵: 동쪽 성곽(평원 동쪽 통로), 남쪽 섬/북쪽 끝 설원(배로만 간다 -- 사막 북쪽 통로는 계속 잠김). */
    public static final String CASTLE = "castle";
    public static final String ISLAND = "island";
    public static final String NORTH = "north";
    /** 배에 오르는 판자(B) 포탈의 대상 -- 대상 맵 대신 항로(SHIP_PORTS 중 하나)를 고른다. */
    public static final String SHIP = "ship";
    public static final List<String> SHIP_PORTS = Collections.unmodifiableList(Arrays.asList(BEACH, ISLAND, NORTH));
    /** [2026-10-24] "모래폭풍을 지나면 평원 하나, 그다음 대도시(성당, 텔레포트 게이트) -- 웅장하게, 라그나로크 프론테라 느낌, 대도시에서 다른 지역으로는 어렵게" 요청.
     *  사막 북쪽 통로 -> 모래폭풍 너머 초원(STEPPE) -> 왕도(CITY). 왕도의 동/서문은 잠김, 텔레포트 게이트(X)는 금광석을 내고 다른 지역으로. */
    public static final String STEPPE = "steppe";
    public static final String CITY = "city";
    /** 텔레포트 게이트(X) 포탈의 대상 -- 목적지(TELE_DESTS)를 고른다. */
    public static final String TELE = "tele";
    /** [2026-10-24] 광산 동굴 2층(1층 "지하 2층 문", 광산 레벨 2 = 1층보다 한 단계 어둡다), 바닷길(바닷가 부두 끝 한 칸 아래 숨은 입구, 용궁 느낌, 보석만). */
    public static final String CAVE2 = "cave2";
    public static final String SEAROAD = "searoad";
    /** [2026-10-24] 광산 동굴 3층(마지막 층, 광산 레벨 3) -- 가장 깊은 곳에 선공 보스(맵 리셋 때만 나타나고 잡히면 다음 리셋까지 없음). */
    public static final String CAVE3 = "cave3";
    public static final List<String> TELE_DESTS = Collections.unmodifiableList(Arrays.asList(FIELD, BEACH, DESERT, CASTLE));
    /** [2026-10-26] "레벨 40대가 많아져 설산부터 강한 몬스터, 북쪽 끝 설원에서 설산으로 오르는 입구, 3층 설산을 지나면 설산 마을" 요청.
     *  설원 북쪽 끝 입구 -> 설산 1층 -> 2층 -> 3층 -> 설산 마을(작은 마을, 북/동/서 문은 눈바람으로 잠김). 한기(cold): 설원 1, 설산 1~3층 = 2~4(초당 최대 체력 %). */
    public static final String SNOW1 = "snow1";
    public static final String SNOW2 = "snow2";
    public static final String SNOW3 = "snow3";
    public static final String SNOWTOWN = "snowtown";
    /** [2026-10-27] "설산에서 내려가면 왕도 아스테리아, 왕도 가는 길을 긴 길로" -- 설산 마을 서문 -> 왕도 가는 길(260칸) -> 왕도 동문. */
    public static final String ROYALROAD = "royalroad";
    /** [2026-10-28] "왕도 가는 길 북쪽에 성당 옛터, 아주 센 보스 한 마리(수하 소환, 광역 마법), 다른 몬스터 없음, 넓은 평지 + 지날 수 있는 무너진 성당 잔해" */
    public static final String RUINS = "ruins";
    /** [2026-10-28] 다도해(아직 운영자만 -- BotS5ServiceImpl.mineArchiOpen). "남쪽 섬에서만 배 타고 갈 수 있는 특별한 지역, 그중 다도해 본섬(300x300, 가운데 신궁),
     *  신궁 앞 NPC에게 신궁 내부 수색 임무(닌자 100마리 -> 비급서 + 달빛석), 달빛석 1개로 본섬 NPC가 1인용 배, 배 타기 요청 -> 7개 섬으로 가는 세로 뱃길(배 탄 모습,
     *  섬마다 내림), 낮과 밤이 있어 낮에는 지역별 햇빛 피해(초당 1~5%)".
     *  남쪽 섬 배(운영자만) -> 본섬(ARCHI) -- 신궁 문 -> 신궁 내부(SHRINE). 본섬 뱃사공 -> 뱃길(ARCHISEA, 1인용 배 필요) -> 섬 7개(ISLE_IDS). 본섬 배는 남쪽 섬으로만. */
    public static final String ARCHI = "archi";
    public static final String SHRINE = "shrine";
    public static final String ARCHISEA = "archisea";
    public static final String ISLE1 = "isle1", ISLE2 = "isle2", ISLE3 = "isle3", ISLE4 = "isle4", ISLE5 = "isle5", ISLE6 = "isle6", ISLE7 = "isle7";
    public static final String[] ISLE_IDS = { ISLE1, ISLE2, ISLE3, ISLE4, ISLE5, ISLE6, ISLE7 };
    /** [2026-10-28] "설산 마을 북쪽에 설산 봉우리 가는 길(80레벨 몬스터, 더 강하게), 그 북쪽에 설산 봉우리(가운데 큰 호수, 보스 한 마리만 -- 95레벨, 얼음의 심장,
     *  주기적으로 한 명씩 얼리고 얼어 있는 사람을 때리면 즉사, 다른 사람이 곁에 와야 풀림(얼음땡))" -- 설산 마을 북문 -> 봉우리 가는 길 -> 봉우리. */
    public static final String SNOWROAD = "snowroad";
    public static final String PEAK = "snowpeak";
    /** 얼음의 심장(등급 값 33) -- 설산 봉우리 보스 보상. */
    public static final String ICE_HEART_CODE = "ICE_HEART";
    public static final String ICE_HEART_NAME = "얼음의 심장";
    public static final int ICE_HEART_GRADE = 33;
    public static final String[] ISLE_NAMES = { "동백섬", "갈매기섬", "거북섬", "안개섬", "산호섬", "달맞이섬", "용암섬" };
    /** 섬마다 낮 햇빛 피해(초당 최대 체력 %), 크기. 뒤 섬일수록 덥고 크고 몬스터가 세다(S5MineMobs.tierAt). */
    static final int[] ISLE_SUN = { 1, 2, 2, 1, 3, 4, 5 };
    static final int[] ISLE_SIZE = { 110, 120, 130, 120, 130, 140, 150 };
    public static final List<String> ARCHI_MAPS = Collections.unmodifiableList(Arrays.asList(ARCHI, SHRINE, ARCHISEA, ISLE1, ISLE2, ISLE3, ISLE4, ISLE5, ISLE6, ISLE7));
    public static boolean isArchi(String id) { return ARCHI_MAPS.contains(id); }
    /** 1인용 배(광산 아이템 코드) -- 뱃길에 들어가려면 있어야 한다(requireItem). */
    public static final String BOAT_ITEM = "BOAT";
    /** 달빛석(등급 값 20, 1인용 배 재료), 닌자의 비급서(32) -- 신궁 수색 임무 보상. */
    public static final String MOON_STONE_CODE = "MOON_STONE";
    public static final String MOON_STONE_NAME = "달빛석";
    public static final int MOON_STONE_GRADE = 20;
    public static final String NINJA_BOOK_CODE = "NINJA_BOOK";
    public static final String NINJA_BOOK_NAME = "닌자의 비급서";
    public static final int NINJA_BOOK_GRADE = 32;
    /** 다도해 낮과 밤 -- 리셋 기준 시각부터 40분마다 낮 25분 + 밤 15분(모든 화면이 같은 시각으로 판단). */
    public static final long ARCHI_DAY_MS = 25 * 60_000L, ARCHI_CYCLE_MS = 40 * 60_000L;
    public static boolean archiDay(long ms) {
        return Math.floorMod(ms - RESET_ANCHOR_MS, ARCHI_CYCLE_MS) < ARCHI_DAY_MS;
    }
    /** 다음에 낮/밤이 바뀌는 시각. */
    public static long archiNextChange(long ms) {
        long p = Math.floorMod(ms - RESET_ANCHOR_MS, ARCHI_CYCLE_MS);
        return ms + (p < ARCHI_DAY_MS ? ARCHI_DAY_MS - p : ARCHI_CYCLE_MS - p);
    }
    /** 지금 있는 맵 ID. 포탈 대상이 여기 없으면 잠긴 입구로 본다. */
    private static final List<String> IDS = Arrays.asList(FIELD, CAVE1, DESERT, BEACH, DUNGEON, CASTLE, ISLAND, NORTH, STEPPE, CITY, CAVE2, SEAROAD, CAVE3, SNOW1, SNOW2, SNOW3, SNOWTOWN, ROYALROAD, RUINS,
            ARCHI, SHRINE, ARCHISEA, ISLE1, ISLE2, ISLE3, ISLE4, ISLE5, ISLE6, ISLE7, SNOWROAD, PEAK);
    /** [2026-10-28] 특수 재료 -- 빙석(설산에서 캘 때/설산 몬스터, 등급 값 19), 타락한 대주교 보상 인장(31). 같은 날 성유물 조각(30)은 없앴다. */
    public static final String ICE_STONE_CODE = "ICE_STONE";
    public static final String ICE_STONE_NAME = "빙석";
    public static final int ICE_STONE_GRADE = 19;
    public static final String SEAL_CODE = "ARCH_SEAL";
    public static final String SEAL_NAME = "타락한 대주교의 인장";
    public static final int SEAL_GRADE = 31;
    /** [2026-10-23] 요일 던전의 돌 -- 인덱스 0 = 월요일 ... 6 = 일요일(KST). 캔 칸 기록의 등급은 11 + 인덱스로 저장한다(일반 광석 1~6과 구분). */
    public static final String[] DAY_STONE_CODES = { "MON_STONE", "TUE_STONE", "WED_STONE", "THU_STONE", "FRI_STONE", "SAT_STONE", "SUN_STONE" };
    public static final String[] DAY_STONE_NAMES = { "월요석", "화요석", "수요석", "목요석", "금요석", "토요석", "일요석" };
    public static final String[] DAY_LABELS = { "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일" };
    public static final int DAY_STONE_GRADE_BASE = 11;
    /** [2026-10-24] 칠요석 -- 요일석 7종(월~일) 1개씩을 대장장이 [합성]으로 합친 돌(등급 값 18). 용왕의 반지 재료. */
    public static final String SEVEN_STONE_CODE = "SEVEN_STONE";
    public static final String SEVEN_STONE_NAME = "칠요석";
    public static final int SEVEN_STONE_GRADE = 18;

    /** 그 시각의 요일 인덱스(0 = 월 ... 6 = 일, KST). */
    public static int dayIndex(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Seoul")).getDayOfWeek().getValue() - 1;
    }

    /** 그 시각(KST) 날짜 yyyyMMdd -- 요일 던전의 캔 칸은 날마다 새로 시작한다(캔 칸 기록의 맵 키에 붙인다). */
    public static String dayKey(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }
    public static final String[] ORE_CODES = { "COPPER", "IRON", "SILVER", "GOLD", "MITHRIL", "STARSTONE" };
    public static final String[] ORE_NAMES = { "구리 광석", "철광석", "은광석", "금광석", "미스릴 원석", "별빛석" };
    /** 등급별 칸 수(이동 가능 칸 중, 높은 등급부터: 별빛석, 미스릴, 금, 은, 철, 나머지 구리). 1 이상이면 그 맵의 칸 개수, 1 미만이면 비율.
     *  [2026-10-24] "모든 광석은 원래처럼 나오되 확률 조정 -- 별빛석은 맵당 1개 이런 식으로" 요청: 별빛석 맵당 1칸, 미스릴은 맵마다 몇십 칸,
     *  금은 1~4%로 크게 줄였다(예전엔 별빛석/미스릴/금이 각각 0.7~2% / 3~6% / 8~12%). 2일 주기 리셋마다 다른 자리에 생긴다. */
    private static final double[] FIELD_SHARE = { 1, 20, 0.012, 0.07, 0.26 };
    private static final double[] CAVE_SHARE = { 1, 15, 0.04, 0.14, 0.30 };
    /** 사막은 평원보다 조금 풍부하고 북쪽(끝)으로 갈수록 좋다. 바닷가는 맵이 작아 미스릴 칸이 적다. */
    private static final double[] DESERT_SHARE = { 1, 15, 0.03, 0.12, 0.28 };
    private static final double[] BEACH_SHARE = { 1, 6, 0.012, 0.07, 0.26 };
    /** [2026-10-24] 새 맵: 성곽/섬은 동굴만큼, 북쪽 끝 설원(배로만, 가장 멀다)은 별빛석 2칸에 더 풍부. */
    private static final double[] CASTLE_SHARE = { 1, 20, 0.05, 0.15, 0.30 };
    private static final double[] ISLAND_SHARE = { 1, 15, 0.04, 0.14, 0.30 };
    private static final double[] NORTH_SHARE = { 2, 30, 0.06, 0.18, 0.30 };
    private static final double[] STEPPE_SHARE = { 1, 20, 0.04, 0.14, 0.30 };
    private static final double[] CAVE2_SHARE = { 2, 25, 0.06, 0.18, 0.30 };
    private static final double[] CAVE3_SHARE = { 3, 35, 0.08, 0.20, 0.30 };
    /** [2026-10-26] 설산 1~3층 -- 위층일수록 풍부하다. */
    private static final double[][] SNOW_SHARE = { { 2, 30, 0.07, 0.20, 0.30 }, { 3, 35, 0.08, 0.20, 0.30 }, { 4, 40, 0.09, 0.22, 0.30 } };
    private static final double[] ROAD_SHARE = { 3, 35, 0.08, 0.20, 0.30 }; // [2026-10-27] 왕도 가는 길
    private static final double[] SNOWROAD_SHARE = { 2, 40, 0.09, 0.22, 0.30 }; // [2026-10-28] 설산 봉우리 가는 길 / 봉우리(왕도 이후 구간 = 별빛석 2칸)
    private static final double[] PEAK_SHARE = { 2, 30, 0.10, 0.22, 0.30 };

    // ---------------------------------------------------------------- 보석(채굴 때 추가로)
    /** [2026-10-24] "보석은 일반 채집 시 추가로 같이 나오게, 지역별 보석" + "다이아몬드는 어디서든" 요청. 인덱스 순서 고정(등급 값 = GEM_GRADE_BASE + 인덱스). */
    public static final String[] GEM_CODES = { "GEM_JASPER", "GEM_RUBY", "GEM_STAR_SAPPHIRE", "GEM_AQUAMARINE", "GEM_TOPAZ", "GEM_GARNET", "GEM_SPINEL", "GEM_EMERALD", "GEM_DIAMOND" };
    public static final String[] GEM_NAMES = { "재스퍼", "루비", "스타 사파이어", "아쿠아마린", "토파즈", "가넷", "스피넬", "에메랄드", "다이아몬드" };
    public static final int GEM_GRADE_BASE = 21;
    public static final int GEM_DIAMOND = 8;
    /** 한 번 캘 때 다이아몬드 확률(천분율) -- 모든 맵(요일 던전 포함). */
    public static final int DIAMOND_PERMILLE = 2;
    /** 맵별 지역 보석 {보석 인덱스, 천분율}: 평원 = 재스퍼 5% / 에메랄드 1%, 바닷가 = 아쿠아마린 5% / 스타 사파이어 1%,
     *  사막 = 토파즈 5% / 가넷 1%, 광산 동굴 = 스피넬 5% / 루비 1%. 요일 던전은 지역 보석 없음(요일석 + 다이아몬드만). */
    private static final Map<String, int[][]> GEM_DROPS = new LinkedHashMap<>();
    static {
        GEM_DROPS.put(FIELD, new int[][] { { 0, 50 }, { 7, 10 } });
        GEM_DROPS.put(BEACH, new int[][] { { 3, 50 }, { 2, 10 } });
        GEM_DROPS.put(DESERT, new int[][] { { 4, 50 }, { 5, 10 } });
        GEM_DROPS.put(CAVE1, new int[][] { { 6, 50 }, { 1, 10 } });
        // [2026-10-24] 새 맵: 성곽 = 가넷 5% / 루비 1%, 섬 = 아쿠아마린 5% / 에메랄드 1%, 북쪽 끝 설원 = 스타 사파이어 3% / 다이아몬드 +0.5%(다이아 산지)
        GEM_DROPS.put(CASTLE, new int[][] { { 5, 50 }, { 1, 10 } });
        GEM_DROPS.put(ISLAND, new int[][] { { 3, 50 }, { 7, 10 } });
        GEM_DROPS.put(NORTH, new int[][] { { 2, 30 }, { 8, 5 } });
        GEM_DROPS.put(CAVE2, new int[][] { { 6, 60 }, { 1, 15 } }); // [2026-10-24] 동굴 2층 = 스피넬 6% / 루비 1.5%
        GEM_DROPS.put(CAVE3, new int[][] { { 1, 40 }, { 8, 5 } }); // [2026-10-24] 동굴 3층 = 루비 4% + 다이아 0.5% 추가
        GEM_DROPS.put(SEAROAD, new int[][] { { 3, 50 }, { 2, 10 } }); // 바닷길 몬스터 보상용(캐면 searoadGem)
        GEM_DROPS.put(STEPPE, new int[][] { { 0, 50 }, { 7, 10 } }); // [2026-10-24] 초원 = 재스퍼 5% / 에메랄드 1%(왕도는 못 캔다)
        // [2026-10-26] 설산 = 스타 사파이어 4% + 다이아 추가였다가 [2026-10-28] "설산에선 보석 대신 빙석" -- 지역 보석 없음(빙석은 BotS5ServiceImpl.mineDigCell,
        //  모든 맵 공통 다이아몬드 0.2%만 남음)
        GEM_DROPS.put(ROYALROAD, new int[][] { { 1, 40 }, { 8, 8 } }); // [2026-10-27] 왕도 가는 길 = 루비 4% + 다이아 0.8%
        // [2026-10-28] 다도해 -- 바다는 아쿠아마린, 섬마다 다른 보석(용암섬은 루비 + 다이아)
        GEM_DROPS.put(ARCHI, new int[][] { { 3, 35 }, { 8, 6 } });
        GEM_DROPS.put(ISLE1, new int[][] { { 1, 35 } });
        GEM_DROPS.put(ISLE2, new int[][] { { 3, 35 } });
        GEM_DROPS.put(ISLE3, new int[][] { { 7, 25 } });
        GEM_DROPS.put(ISLE4, new int[][] { { 2, 30 } });
        GEM_DROPS.put(ISLE5, new int[][] { { 6, 35 }, { 3, 15 } });
        GEM_DROPS.put(ISLE6, new int[][] { { 2, 30 }, { 8, 8 } });
        GEM_DROPS.put(ISLE7, new int[][] { { 1, 45 }, { 8, 10 } });
        GEM_DROPS.put(SNOWROAD, new int[][] { { 2, 40 }, { 8, 8 } }); // [2026-10-28] 설산 봉우리 가는 길 = 스타 사파이어 4% + 다이아 0.8%
        GEM_DROPS.put(PEAK, new int[][] { { 2, 45 }, { 8, 10 } });
    }

    /** [2026-10-28] "보석 드랍률 소폭 낮춰줘" -- 맵별 지역 보석(과 추가 다이아) 천분율에 곱하는 백분율. 모든 맵 공통 다이아(DIAMOND_PERMILLE)는 그대로. */
    static final int GEM_SCALE_PCT = 80;

    /** 한 번 캘 때 함께 나오는 보석 인덱스(없으면 -1). roll = 0~999 난수. 다이아몬드를 먼저 본다. */
    public static int gemFor(String mapId, int roll) {
        if (roll < DIAMOND_PERMILLE) return GEM_DIAMOND;
        int acc = DIAMOND_PERMILLE;
        int[][] drops = GEM_DROPS.get(mapId);
        if (drops == null) return -1;
        for (int[] d : drops) {
            acc += d[1] * GEM_SCALE_PCT / 100;
            if (roll < acc) return d[0];
        }
        return -1;
    }

    /** [2026-10-24] 몬스터 처치 보상용 -- 캘 때(gemFor)와 같지만 그 맵의 흔한 보석(첫째)만 commonMult배. */
    public static int gemForMob(String mapId, int roll, double commonMult) {
        if (roll < DIAMOND_PERMILLE) return GEM_DIAMOND;
        int acc = DIAMOND_PERMILLE;
        int[][] drops = GEM_DROPS.get(mapId);
        if (drops == null) return -1;
        for (int i = 0; i < drops.length; i++) {
            acc += (i == 0 ? (int) Math.round(drops[i][1] * commonMult) : drops[i][1]) * GEM_SCALE_PCT / 100;
            if (roll < acc) return drops[i][0];
        }
        return -1;
    }

    /** 그 맵에서 나오는 보석 이름 목록(화면 안내용, 다이아몬드 포함). */
    public static List<String> gemNamesFor(String mapId) {
        List<String> out = new ArrayList<>();
        int[][] drops = GEM_DROPS.get(mapId);
        if (drops != null) for (int[] d : drops) out.add(GEM_NAMES[d[0]]);
        out.add(GEM_NAMES[GEM_DIAMOND]);
        return out;
    }

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
            return target == null || (!IDS.contains(target) && !SHIP.equals(target) && !TELE.equals(target));
        }
    }

    /** [2026-10-24] 몬스터 -- id(맵 안 순번), type(S5MineMobs.TYPES 인덱스), 집 칸(x, y), 돌아다니는 칸들(집에서 2칸 안, y * w + x).
     *  위치는 시각으로 정해진다(S5MineMobs.cellAt) -- 서버와 화면이 같은 계산을 해서 따로 동기화하지 않는다. 체력/죽음/멈춤만 서버 메모리. */
    public static final class Mob {
        public final int id, type, x, y;
        public final int[] cells;
        Mob(int id, int type, int x, int y, int[] cells) {
            this.id = id; this.type = type; this.x = x; this.y = y; this.cells = cells;
        }
    }

    /** NPC(그 칸은 이동 불가, 부딪히면 말을 건다). kind: smith = 대장장이(곡괭이 강화), captain = 선장(대사만). */
    public static final class Npc {
        public final int x, y;
        public final String kind, name, line;
        public char ground; // [2026-10-25] NPC 칸('N') 원래 바닥(화면이 그 바닥 타일을 그린다), 0이면 화면 기본값
        Npc(int x, int y, String kind, String name, String line) {
            this.x = x; this.y = y; this.kind = kind; this.name = name; this.line = line;
        }
    }

    /** [2026-10-25] "마을 간 이동이 활성화되는 느낌, NPC로" -- 이동 안내원(guide)이 서 있는 맵. 안내원끼리 무료로 오간다(왕도는 관리자만). */
    //  같은 날 "마을로 인식된 곳에서만, 사냥터만 있는 곳은 없애줘" -- 평원 마을(V)과 왕도만(바닷가/사막/성곽은 사냥터라 뺐다).
    //  [2026-10-25] 설원 북쪽 작은 마을도 마을이라 안내원을 둔다. [2026-10-26] 그 마을은 설산 3층 너머 "설산 마을"(SNOWTOWN)로 옮겼다.
    public static final List<String> GUIDE_MAPS = Collections.unmodifiableList(Arrays.asList(FIELD, SNOWTOWN, CITY));
    /** 안내원 목적지 이름(맵 이름 대신 마을 이름). */
    public static String townName(String id) {
        return FIELD.equals(id) ? "중앙 평원 마을" : SNOWTOWN.equals(id) ? "설산 마을" : CITY.equals(id) ? "왕도 아스테리아" : nameOf(id);
    }

    // ---------------------------------------------------------------- [2026-10-26] 한기 / 눈바람 / 안전지대
    /** 눈바람: 한기 맵마다 2분 칸 안의 정해진 한 때(맵/칸마다 다름). 서버(BotS5ServiceImpl.mineColdTick)가 다음 시각을 화면에 알려 준다. */
    public static final long BLIZZARD_SLOT_MS = 120_000L;

    static long blizzardAt(String mapId, long slot) {
        return slot * BLIZZARD_SLOT_MS + 20_000L + S5MineMobs.mix(mapId.hashCode(), (int) slot) % 80_000;
    }

    /** after 다음의 눈바람 시각. */
    public static long nextBlizzard(String mapId, long after) {
        for (long s = after / BLIZZARD_SLOT_MS; ; s++) {
            long t = blizzardAt(mapId, s);
            if (t > after) return t;
        }
    }

    /** (from, to] 사이에 분 눈바람 시각(없으면 -1). */
    public static long blizzardBetween(String mapId, long from, long to) {
        long t = nextBlizzard(mapId, from);
        return t <= to ? t : -1;
    }

    /** 안전지대(모닥불) -- {x, y, 반지름}. 한기와 눈바람이 없다. */
    public List<int[]> safeZones() { return Collections.unmodifiableList(safe); }

    public int cold() { return cold; }
    public int sun() { return sun; }
    public boolean boat() { return boat; }

    public boolean safeAt(int x, int y) {
        return nearSafe(x, y, 0);
    }

    /** 안전지대 margin칸 안(몬스터를 두지 않을 때). */
    public boolean nearSafe(int x, int y, int margin) {
        for (int[] z : safe) {
            int r = z[2] + margin;
            if ((x - z[0]) * (x - z[0]) + (y - z[1]) * (y - z[1]) <= r * r) return true;
        }
        return false;
    }

    /** 모닥불 배치: 눈길(pathX, 열린 줄 y0~y1) 위 every줄마다 + 18칸 격자마다 갈 수 있는 칸 -- 서로 10칸 넘게 떨어지게.
     *  눈바람 예고(5초) 동안 걸어서 닿을 수 있게 촘촘히(어느 칸에서든 가장 가까운 모닥불까지 대체로 20칸 안팎). */
    private static void campfires(char[][] t, int[] pathX, int y0, int y1, int every, List<int[]> safe) {
        int h = t.length, w = t[0].length;
        for (int y = y1 - every; y > y0 + 4; y -= every) if (pathX[y] > 0 && walkable(t[y][pathX[y]])) safe.add(new int[] { pathX[y], y, 3 });
        for (int gy = 9; gy < h - 4; gy += 18) for (int gx = 9; gx < w - 4; gx += 18) {
            int[] best = null;
            for (int d = 0; d <= 8 && best == null; d++) for (int dy = -d; dy <= d && best == null; dy++) for (int dx = -d; dx <= d; dx++) {
                int x = gx + dx, y = gy + dy;
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1 || !walkable(t[y][x]) || t[y][x] == 'Y') continue;
                best = new int[] { x, y, 3 };
                break;
            }
            if (best == null) continue;
            boolean far = true;
            for (int[] z : safe) if (Math.abs(z[0] - best[0]) + Math.abs(z[1] - best[1]) < 10) { far = false; break; }
            if (far) safe.add(best);
        }
    }

    /** 대상(target)으로 가는 포탈 옆의 걸을 수 있는 칸 -- 반대쪽에서 오면 서는 곳(맵 가장자리 포탈은 안쪽 칸, 성벽 문은 성 안쪽 칸). */
    private static int[] besidePortal(S5MineMap m, String target) {
        for (Portal p : m.portals) {
            if (!target.equals(p.target)) continue;
            for (int[] dv : DIRS) if (m.walkable(p.x + dv[0], p.y + dv[1])) return new int[] { p.x + dv[0], p.y + dv[1] };
        }
        return new int[] { m.spawnX, m.spawnY };
    }

    /** [2026-10-27] 왕도 주민(말풍선만, 대화 없음) -- 사람마다 돌바닥/풀밭을 오가는 길(칸 목록, y * w + x). 화면이 시각으로 그 길을 왕복한다. */
    public List<int[]> citizens() { return citizens; }

    private static List<int[]> cityCitizens(S5MineMap m, long seed) {
        Random r = new Random(seed * 17 + 5);
        List<int[]> out = new ArrayList<>();
        for (int tries = 0; out.size() < 18 && tries < 4000; tries++) {
            int x = 4 + r.nextInt(m.w - 8), y = 4 + r.nextInt(m.h - 8);
            if (m.terrain[y][x] != 's') continue;
            List<Integer> route = new ArrayList<>();
            route.add(y * m.w + x);
            int dir = r.nextInt(4), cx = x, cy = y;
            for (int k = 0; k < 40 && route.size() < 16; k++) {
                if (r.nextDouble() < 0.25) dir = r.nextInt(4);
                int nx = cx + DIRS[dir][0], ny = cy + DIRS[dir][1];
                if (!m.inside(nx, ny) || (m.terrain[ny][nx] != 's' && m.terrain[ny][nx] != '.')) { dir = r.nextInt(4); continue; }
                cx = nx; cy = ny;
                route.add(cy * m.w + cx);
            }
            if (route.size() < 8) continue;
            int[] a = new int[route.size()];
            for (int i = 0; i < a.length; i++) a[i] = route.get(i);
            out.add(a);
        }
        return out;
    }

    /** 포탈 대상 좌표 바꿔 끼우기(target -> (tx, ty)). */
    private static List<Portal> retarget(S5MineMap m, String target, int[] to) {
        List<Portal> out = new ArrayList<>();
        for (Portal p : m.portals) out.add(target.equals(p.target) ? new Portal(p.x, p.y, p.kind, target, to[0], to[1], p.label) : p);
        return out;
    }

    /** 시작점 근처의 탁 트인 칸(주변 8칸이 다 걸을 수 있고 포탈/NPC에서 2칸 넘게 떨어진 곳)에 이동 안내원을 세운다 -- 길을 막지 않게. */
    private static void placeGuide(S5MineMap m) {
        int cx = m.villageX >= 0 ? m.villageX : m.spawnX, cy = m.villageX >= 0 ? m.villageY : m.spawnY; // [2026-10-25] 설원은 마을 가운데 근처
        placeNpc(m, cx, cy, "guide", "이동 안내원", "어디로 모실까요? 마을과 마을 사이는 제가 공짜로 데려다 드려요!");
    }

    /** (cx, cy) 근처 탁 트인 칸에 NPC를 세운다(위 이동 안내원과 같은 규칙). */
    private static boolean placeNpc(S5MineMap m, int cx, int cy, String kind, String name, String line) {
        for (int d = 2; d <= 12; d++) {
            for (int dy = -d; dy <= d; dy++) for (int dx = -d; dx <= d; dx++) {
                if (Math.abs(dx) + Math.abs(dy) != d) continue;
                int x = cx + dx, y = cy + dy;
                if (x < 1 || y < 1 || x >= m.w - 1 || y >= m.h - 1 || !walkable(m.terrain[y][x]) || m.terrain[y][x] == 'P') continue;
                boolean open = true;
                for (int yy = y - 1; yy <= y + 1 && open; yy++) for (int xx = x - 1; xx <= x + 1; xx++) if (!walkable(m.terrain[yy][xx])) { open = false; break; }
                for (Portal pt : m.portals) if (Math.abs(pt.x - x) + Math.abs(pt.y - y) <= 2) open = false;
                for (Npc n : m.npcs) if (Math.abs(n.x - x) + Math.abs(n.y - y) <= 2) open = false;
                if (!open) continue;
                Npc g = new Npc(x, y, kind, name, line);
                g.ground = m.terrain[y][x];
                m.terrain[y][x] = 'N';
                m.ore[y][x] = 0;
                List<Npc> l = new ArrayList<>(m.npcs);
                l.add(g);
                m.npcs = Collections.unmodifiableList(l);
                return true;
            }
        }
        return false;
    }

    /** 이동 안내원(없으면 null). */
    public Npc guide() {
        for (Npc n : npcs) if ("guide".equals(n.kind)) return n;
        return null;
    }

    public final String id, name;
    public final boolean dark;
    private int level;          // [2026-10-23] 광산 레벨(0 = 평원처럼 투구와 무관)
    private String requireItem; // [2026-10-23] 입장에 필요한 광산 아이템(없으면 null)
    private boolean wind;       // [2026-10-23] 모래바람(가만히 있어도 좌우로 밀림, 화면이 처리)
    private boolean underwater; // [2026-10-24] 바닷속(화면이 푸르게 물들이고 거품)
    private List<Npc> npcs = Collections.emptyList();
    /** [2026-10-25] 맵 안 작은 마을 가운데(설산 마을) -- 없으면 -1. */
    int villageX = -1, villageY = -1;
    private int cold;                                  // [2026-10-26] 한기 단계(초당 최대 체력 %), 0 = 없음
    private int sun;                                   // [2026-10-28] 다도해 낮 햇빛 피해(초당 최대 체력 %), 0 = 없음
    private boolean boat;                              // [2026-10-28] 다도해 뱃길 -- 배를 타고 물 위로 다닌다(물 칸이 길)
    private List<int[]> safe = new ArrayList<>();      // [2026-10-26] 안전지대(모닥불) {x, y, 반지름}
    private List<int[]> citizens = Collections.emptyList(); // [2026-10-27] 왕도 주민이 오가는 길
    private List<Mob> mobs = Collections.emptyList();
    private int dockX = -1, dockY = -1; // [2026-10-24] 배에서 내리는 칸(항구 맵만)
    private int bossX = -1, bossY = -1; // [2026-10-24] 보스 자리(동굴 3층만, S5MineMobs.spawn이 보스를 둔다)

    public int level() { return level; }
    public String requireItem() { return requireItem; }
    public boolean wind() { return wind; }
    public boolean underwater() { return underwater; }
    public List<Npc> npcs() { return npcs; }
    public List<Mob> mobs() { return mobs; }
    public int dockX() { return dockX; }
    public int dockY() { return dockY; }
    public int bossX() { return bossX; }
    public int bossY() { return bossY; }
    public final int w, h, spawnX, spawnY;
    private final char[][] terrain;
    private final byte[][] ore;
    public final List<Portal> portals;

    private S5MineMap(String id, String name, boolean dark, int w, int h, int spawnX, int spawnY, char[][] terrain, byte[][] ore, List<Portal> portals) {
        this.id = id; this.name = name; this.dark = dark; this.w = w; this.h = h; this.spawnX = spawnX; this.spawnY = spawnY;
        this.terrain = terrain; this.ore = ore; this.portals = Collections.unmodifiableList(portals);
    }

    public static boolean walkable(char c) {
        // [2026-10-24] 나무(T)도 지나갈 수 있다("나무가 너무 많아") -- 광석은 없음(assignOre). s 성 바닥 / n 눈밭 / Y 눈 덮인 소나무(T처럼 통과, 광석 없음)
        return ".,:VHcrdbPTsnYupe".indexOf(c) >= 0; // [2026-10-24] u 바다 모래 / p 용궁 길 / e 해초(통과, 광석 없음)
    }

    public static boolean portalChar(char c) {
        return "GMUSBXZ".indexOf(c) >= 0; // X = 텔레포트 게이트(왕도), Z = 숨은 입구(물처럼 보임)
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
            for (Mob mb : mobs) hsh = 31 * hsh + (mb.id * 7 + mb.type) * 131 + mb.y * 1009 + mb.x + Arrays.hashCode(mb.cells); // 몬스터 배치가 바뀌면 화면도 다시 받게
            hsh = 31 * hsh + (wind ? 1 : 0) + (underwater ? 2 : 0) + dockX * 3 + dockY * 5;
            for (int[] z : safe) hsh = 31 * hsh + z[0] * 7 + z[1] * 13 + z[2] + cold * 17; // [2026-10-26] 한기/모닥불
            for (int[] cz : citizens) hsh = 31 * hsh + Arrays.hashCode(cz); // [2026-10-27] 왕도 주민
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
    /** [2026-10-24] 명품상점 위치: 마을 가운데에서 (-4, -2) -- 북서쪽 초가집 바로 앞. */
    private static final int BOUTIQUE_DX = -4, BOUTIQUE_DY = -2;

    // [2026-10-28] "별빛석은 하위 구간(왕도 이전 레벨)은 맵당 1개씩, 왕도 이후 구간은 맵당 2개씩" + "다음 맵 변경 때부터" --
    //  주기 STAR_UNIFY_CYCLE(10/29 05:00 리셋)부터 만드는 맵은 별빛석 칸 수를 assignOre에서 맞춘다(그 전 주기 맵은 예전 그대로:
    //  북쪽 끝 설원 2, 동굴 2층 2, 3층 3, 설산 2/3/4, 샛길 3). 왕도 이후 = 왕도 북쪽 샛길과 다도해(처음부터 2개).
    static final int STAR_UNIFY_CYCLE = 3;
    private static final ThreadLocal<Boolean> STAR_UNIFIED = ThreadLocal.withInitial(() -> false);

    static Map<String, S5MineMap> buildWorld(long seed) {
        STAR_UNIFIED.set((seed - BASE_SEED) / 7919L >= STAR_UNIFY_CYCLE);
        try {
            return buildWorld0(seed);
        } finally {
            STAR_UNIFIED.remove();
        }
    }

    private static Map<String, S5MineMap> buildWorld0(long seed) {
        S5MineMap field = generateField(seed);
        S5MineMap cave = generateCave(seed + 1);
        S5MineMap desert = generateDesert(seed + 2);
        S5MineMap beach = generateBeach(seed + 3);
        S5MineMap dungeon = generateDungeon(seed + 4);
        S5MineMap castle = generateCastle(seed + 5);
        S5MineMap island = generateIsland(seed + 6);
        S5MineMap northEnd = generateNorth(seed + 7);
        S5MineMap steppe = generateSteppe(seed + 8);
        S5MineMap city = generateCity(seed + 9);
        S5MineMap cave2 = generateCave(seed + 10, CAVE2, "광산 동굴 2층", CAVE2_SHARE);
        S5MineMap searoad = generateSearoad(seed + 11);
        S5MineMap cave3 = generateCave(seed + 12, CAVE3, "광산 동굴 3층", CAVE3_SHARE);
        S5MineMap snow1 = generateSnowMountain(seed + 13, SNOW1, "설산 1층", 1); // [2026-10-26] 설산 1~3층, 설산 마을
        S5MineMap snow2 = generateSnowMountain(seed + 14, SNOW2, "설산 2층", 2);
        S5MineMap snow3 = generateSnowMountain(seed + 15, SNOW3, "설산 3층", 3);
        S5MineMap snowTown = generateSnowTown(seed + 16);
        S5MineMap road = generateRoyalRoad(seed + 17); // [2026-10-27] 왕도 가는 길
        S5MineMap ruins = generateRuins(seed + 18); // [2026-10-28] 성당 옛터

        Portal mouth = null, north = null, south = null, west = null, east = null;
        for (Portal p : field.portals) {
            if (p.kind == 'M') mouth = p;
            if (p.kind == 'G' && (north == null || p.y < north.y)) north = p;
            if (p.kind == 'G' && (south == null || p.y > south.y)) south = p;
            if (p.kind == 'G' && (west == null || p.x < west.x)) west = p;
            if (p.kind == 'G' && (east == null || p.x > east.x)) east = p;
        }
        // 평원: 동굴 입구 -> 동굴, 북쪽 통로 -> 사막(남쪽 입구), 남쪽 통로 -> 바닷가(북쪽 입구), 서쪽 -> 요일 던전, 동쪽 -> 성곽(2026-10-24).
        List<Portal> fp = new ArrayList<>();
        for (Portal p : field.portals) {
            if (p == mouth) fp.add(new Portal(p.x, p.y, 'M', CAVE1, cave.spawnX, cave.spawnY, p.label));
            else if (p == north) fp.add(new Portal(p.x, p.y, 'G', DESERT, desert.spawnX, desert.spawnY, "북쪽 사막으로 가는 길"));
            else if (p == south) fp.add(new Portal(p.x, p.y, 'G', BEACH, beach.spawnX, beach.spawnY, "남쪽 바닷가로 가는 길"));
            else if (p == west) fp.add(new Portal(p.x, p.y, 'G', DUNGEON, dungeon.spawnX, dungeon.spawnY, "서쪽 동굴 (요일 던전)"));
            else if (p == east) fp.add(new Portal(p.x, p.y, 'G', CASTLE, castle.spawnX, castle.spawnY, "동쪽 성곽으로 가는 길"));
            else fp.add(p);
        }
        List<Portal> cp = new ArrayList<>();
        Portal sDoor = null;
        for (Portal p : cave.portals) {
            if (p.kind == 'U') cp.add(new Portal(p.x, p.y, 'U', FIELD, mouth.x, mouth.y + 1, p.label));
            else { sDoor = p; cp.add(new Portal(p.x, p.y, 'S', CAVE2, cave2.spawnX, cave2.spawnY, "지하 2층으로 내려가는 문")); } // [2026-10-24] 개방
        }
        // [2026-10-24] 동굴 2층: 나가는 길 -> 1층 지하 문 옆 칸, 지하 3층 문은 잠김
        int[] doorSide = { cave.spawnX, cave.spawnY };
        for (int[] dv : DIRS) if (cave.walkable(sDoor.x + dv[0], sDoor.y + dv[1])) { doorSide = new int[] { sDoor.x + dv[0], sDoor.y + dv[1] }; break; }
        List<Portal> c2p = new ArrayList<>();
        Portal c2Door = null;
        for (Portal p : cave2.portals) {
            if (p.kind == 'U') c2p.add(new Portal(p.x, p.y, 'U', CAVE1, doorSide[0], doorSide[1], "광산 동굴 1층으로 올라가기"));
            else { c2Door = p; c2p.add(new Portal(p.x, p.y, 'S', CAVE3, cave3.spawnX, cave3.spawnY, "지하 3층으로 내려가는 문")); }
        }
        // [2026-10-24] 동굴 3층(마지막): 나가는 길 -> 2층 지하 문 옆 칸. 3층의 지하 문은 막고(벽) 그 앞 칸을 보스 자리로.
        int[] door2Side = { cave2.spawnX, cave2.spawnY };
        for (int[] dv : DIRS) if (cave2.walkable(c2Door.x + dv[0], c2Door.y + dv[1])) { door2Side = new int[] { c2Door.x + dv[0], c2Door.y + dv[1] }; break; }
        List<Portal> c3p = new ArrayList<>();
        int bossX = cave3.spawnX, bossY = cave3.spawnY;
        for (Portal p : cave3.portals) {
            if (p.kind == 'U') c3p.add(new Portal(p.x, p.y, 'U', CAVE2, door2Side[0], door2Side[1], "광산 동굴 2층으로 올라가기"));
            else {
                cave3.terrain[p.y][p.x] = '#';
                for (int[] dv : DIRS) if (cave3.walkable(p.x + dv[0], p.y + dv[1])) { bossX = p.x + dv[0]; bossY = p.y + dv[1]; break; }
            }
        }
        // 사막 북쪽 끝 통로 -> 모래폭풍 너머 초원(2026-10-24 개방). 북쪽 끝 설원은 여전히 배로만.
        List<Portal> dp = new ArrayList<>();
        Portal dNorth = null;
        for (Portal p : desert.portals) {
            if (FIELD.equals(p.target)) dp.add(new Portal(p.x, p.y, p.kind, FIELD, north.x, north.y + 1, p.label));
            else { dNorth = p; dp.add(new Portal(p.x, p.y, p.kind, STEPPE, steppe.spawnX, steppe.spawnY, "모래폭풍을 지나 왕도 남쪽 초원으로")); }
        }
        // 초원: 남쪽 통로 -> 사막 북쪽 끝, 북쪽 통로 -> 왕도 남문. 왕도: 남문 -> 초원 북쪽 끝(동/서문은 잠김).
        Portal sNorth = null;
        for (Portal p : steppe.portals) if (p.y == 0) sNorth = p;
        List<Portal> sp = new ArrayList<>();
        for (Portal p : steppe.portals) {
            if (p == sNorth) sp.add(new Portal(p.x, p.y, p.kind, CITY, city.spawnX, city.spawnY, "왕도 아스테리아 남문"));
            else sp.add(new Portal(p.x, p.y, p.kind, DESERT, dNorth.x, dNorth.y + 1, "모래폭풍 너머 사막으로"));
        }
        List<Portal> tp = new ArrayList<>();
        for (Portal p : city.portals) tp.add(STEPPE.equals(p.target) ? new Portal(p.x, p.y, p.kind, STEPPE, sNorth.x, sNorth.y + 1, p.label) : p);
        List<Portal> bp = new ArrayList<>();
        int beachDockX = beach.spawnX, beachDockY = beach.spawnY;
        for (Portal p : beach.portals) {
            if (FIELD.equals(p.target)) bp.add(new Portal(p.x, p.y, p.kind, FIELD, south.x, south.y - 1, p.label));
            else if (p.kind == 'B') { bp.add(new Portal(p.x, p.y, 'B', SHIP, 0, 0, "배에 오르기")); beachDockX = p.x - 1; beachDockY = p.y; }
            else bp.add(p);
        }
        // [2026-10-24] 부두 끝 한 칸 아래 숨은 입구(Z, 물처럼 보임) -> 바닷길. 바닷길의 올라가는 길 -> 부두 끝.
        Portal plank = null;
        for (Portal p : beach.portals) if (p.kind == 'B') plank = p;
        int pierMid = plank.x - 2, pierEndY = plank.y;
        while (pierEndY + 1 < beach.h && beach.terrain[pierEndY + 1][pierMid] == 'P') pierEndY++;
        if (pierEndY + 1 < beach.h - 1) {
            beach.terrain[pierEndY + 1][pierMid] = 'Z';
            bp.add(new Portal(pierMid, pierEndY + 1, 'Z', SEAROAD, searoad.spawnX, searoad.spawnY, "???"));
        }
        List<Portal> wp = new ArrayList<>();
        for (Portal p : searoad.portals) wp.add(BEACH.equals(p.target) ? new Portal(p.x, p.y, p.kind, BEACH, pierMid, pierEndY, p.label) : p);
        List<Portal> kp = new ArrayList<>();
        for (Portal p : castle.portals) kp.add(FIELD.equals(p.target) ? new Portal(p.x, p.y, p.kind, FIELD, east.x - 1, east.y, p.label) : p);

        // 마을 대장장이(곡괭이 강화) -- 마을 칸이라 원래 광석이 없고 다른 칸/광석 배치는 그대로다.
        int sx = field.spawnX + SMITH_DX, sy = field.spawnY + SMITH_DY;
        field.terrain[sy][sx] = 'N';
        List<Npc> fn = new ArrayList<>();
        fn.add(new Npc(sx, sy, "smith", "대장장이", "광석을 가져오면 곡괭이를 더 튼튼하게 만들어 주지. 좋은 광석일수록 크게 강화된다네."));
        // [2026-10-24] 명품상점(외형 장비/성별 전환) -- 대장장이 맞은편(북서쪽) 초가집 앞
        int bx = field.spawnX + BOUTIQUE_DX, by = field.spawnY + BOUTIQUE_DY;
        field.terrain[by][bx] = 'N';
        fn.add(new Npc(bx, by, "boutique", "명품상점", "어서 오세요~ 반짝반짝 새 옷 한 벌 어떠세요? 성별도 바꿔 드려요."));

        Map<String, S5MineMap> out = new LinkedHashMap<>();
        out.put(FIELD, relink(field, fp, fn));
        S5MineMap c2 = relink(cave, cp, null);
        c2.level = 1; // 광산 동굴 1층 = 광산 레벨 1(투구 없이도 들어가지만 아주 어둡다)
        out.put(CAVE1, c2);
        S5MineMap d2 = relink(desert, dp, null);
        d2.wind = true;
        out.put(DESERT, d2);
        S5MineMap b2 = relink(beach, bp, beach.npcs);
        b2.dockX = beachDockX;
        b2.dockY = beachDockY;
        out.put(BEACH, b2);
        List<Portal> gp = new ArrayList<>();
        for (Portal p : dungeon.portals) gp.add(FIELD.equals(p.target) ? new Portal(p.x, p.y, p.kind, FIELD, west.x + 1, west.y, p.label) : p);
        out.put(DUNGEON, relink(dungeon, gp, null));
        out.put(CASTLE, relink(castle, kp, null));
        out.put(ISLAND, island);
        // [2026-10-26] 설원 입구 -> 설산 1층 아래 -> ... -> 3층 위 -> 설산 마을(남쪽 길 끝 -> 3층 맨 위 아래 칸)
        out.put(NORTH, relink(northEnd, retarget(northEnd, SNOW1, new int[] { snow1.spawnX, snow1.spawnY }), northEnd.npcs));
        out.put(STEPPE, relink(steppe, sp, null));
        out.put(CITY, relink(city, tp, city.npcs));
        S5MineMap cv2 = relink(cave2, c2p, null);
        cv2.level = 2; // 광산 레벨 2 -- 투구 1단계 이상이어야 움직이고, 시야는 1층보다 한 단계 어둡다(투구 단계 - 1)
        out.put(CAVE2, cv2);
        out.put(SEAROAD, relink(searoad, wp, null));
        S5MineMap cv3 = relink(cave3, c3p, null);
        cv3.level = 3; // 광산 레벨 3 -- 투구 2단계 이상, 시야는 2층보다 한 단계 더 어둡다
        cv3.bossX = bossX;
        cv3.bossY = bossY;
        out.put(CAVE3, cv3);
        // 설산은 맨 뒤에 넣는다(몬스터 배치 시드가 넣은 순서를 따라서, 앞 맵들의 몬스터 자리는 그대로)
        S5MineMap[] snows = { snow1, snow2, snow3 };
        String[] belowIds = { NORTH, SNOW1, SNOW2 }, aboveIds = { SNOW2, SNOW3, SNOWTOWN };
        S5MineMap[] belowMaps = { northEnd, snow1, snow2 }, aboveMaps = { snow2, snow3, snowTown };
        for (int i = 0; i < 3; i++) {
            S5MineMap sm = snows[i];
            int[] downTo = besidePortal(belowMaps[i], sm.id); // 아래 맵에서 나를 가리키는 포탈 옆 칸
            S5MineMap tmp = relink(sm, retarget(sm, belowIds[i], downTo), null);
            out.put(sm.id, relink(tmp, retarget(tmp, aboveIds[i], new int[] { aboveMaps[i].spawnX, aboveMaps[i].spawnY }), null));
        }
        out.put(SNOWTOWN, relink(snowTown, retarget(snowTown, SNOW3, besidePortal(snow3, SNOWTOWN)), null));
        // [2026-10-27] 설산 마을 서문 <-> 왕도 가는 길 <-> 왕도 동문(맨 뒤에 넣어 앞 맵들의 몬스터 자리는 그대로)
        S5MineMap twn = out.get(SNOWTOWN), cty = out.get(CITY);
        S5MineMap rd = relink(road, retarget(road, SNOWTOWN, besidePortal(twn, ROYALROAD)), null);
        rd = relink(rd, retarget(rd, RUINS, new int[] { ruins.spawnX, ruins.spawnY }), null); // [2026-10-28] 북쪽 샛길 -> 성당 옛터
        out.put(ROYALROAD, relink(rd, retarget(rd, CITY, besidePortal(cty, ROYALROAD)), null));
        out.put(RUINS, relink(ruins, retarget(ruins, ROYALROAD, besidePortal(road, RUINS)), null));
        out.put(SNOWTOWN, relink(twn, retarget(twn, ROYALROAD, besidePortal(road, SNOWTOWN)), twn.npcs));
        out.put(CITY, relink(cty, retarget(cty, ROYALROAD, besidePortal(road, CITY)), cty.npcs));
        // [2026-10-28] 다도해(운영자만) -- 본섬/신궁 내부/뱃길/섬 7개. 맨 뒤에 넣는다(앞 맵들의 몬스터 자리는 그대로).
        S5MineMap shrine = generateShrine(seed + 40);
        S5MineMap archi = generateArchiMain(seed + 41);
        S5MineMap sea = generateArchiSea(seed + 42);
        S5MineMap[] isles = new S5MineMap[ISLE_IDS.length];
        for (int i = 0; i < isles.length; i++) isles[i] = generateIsle(seed + 43 + i, i);
        S5MineMap ar = relink(archi, retarget(archi, SHRINE, new int[] { shrine.spawnX, shrine.spawnY }), null);
        out.put(ARCHI, ar);
        out.put(SHRINE, relink(shrine, retarget(shrine, ARCHI, besidePortal(ar, SHRINE)), null));
        S5MineMap sv = relink(sea, retarget(sea, ARCHI, new int[] { ar.villageX, ar.villageY + 2 }), null);
        for (int i = 0; i < isles.length; i++) sv = relink(sv, retarget(sv, ISLE_IDS[i], new int[] { isles[i].spawnX, isles[i].spawnY }), null);
        out.put(ARCHISEA, sv);
        for (int i = 0; i < isles.length; i++) out.put(ISLE_IDS[i], relink(isles[i], retarget(isles[i], ARCHISEA, seaDockBeside(sv, ISLE_IDS[i])), null));
        // [2026-10-28] 설산 마을 북문 <-> 설산 봉우리 가는 길 <-> 설산 봉우리. 맨 뒤에 넣는다(앞 맵들의 몬스터 자리는 그대로, 설산 마을은 자리 그대로 다시 넣음).
        S5MineMap sroad = generateSnowRoad(seed + 50);
        S5MineMap peak = generatePeak(seed + 51);
        S5MineMap tw0 = out.get(SNOWTOWN);
        S5MineMap sr = relink(sroad, retarget(sroad, SNOWTOWN, besidePortal(tw0, SNOWROAD)), null);
        sr = relink(sr, retarget(sr, PEAK, new int[] { peak.spawnX, peak.spawnY }), null);
        out.put(SNOWROAD, sr);
        out.put(PEAK, relink(peak, retarget(peak, SNOWROAD, besidePortal(sr, PEAK)), null));
        out.put(SNOWTOWN, relink(tw0, retarget(tw0, SNOWROAD, new int[] { sr.spawnX, sr.spawnY }), tw0.npcs));
        S5MineMap an = out.get(ARCHI); // 본섬 NPC -- 마을 광장(배 목수, 뱃사공), 신궁 문 앞(신궁 수문장)
        placeNpc(an, an.villageX - 4, an.villageY - 1, "boatwright", "배 목수", "달빛석 하나만 가져오면 혼자 타는 배를 뚝딱 만들어 주지. 달빛을 머금은 돌이라야 파도를 타거든.");
        placeNpc(an, an.villageX + 4, an.villageY - 1, "ferry", "뱃사공", "배가 있으면 뱃길로 데려다주지. 일곱 섬이 뱃길을 따라 늘어서 있다네. 한낮의 햇볕은 조심하게!");
        for (Portal pt : an.portals) if (SHRINE.equals(pt.target))
            placeNpc(an, pt.x + 4, pt.y + 3, "ninjaquest", "신궁 수문장", "신궁 안에 닌자들이 숨어들었소. 수색을 맡아 주시겠소? 100명을 처치하면 비급서와 달빛석을 드리리다.");
        for (String gid : GUIDE_MAPS) placeGuide(out.get(gid)); // [2026-10-25] 이동 안내원(몬스터 배치 전에 -- 그 칸엔 몬스터가 안 선다)
        // [2026-10-25] 왕도 대장장이 -- "지금보다 더 높은 업그레이드는 대도시 대장장이에서, 더 큰 폭의 아이템"
        S5MineMap ct = out.get(CITY);
        // [2026-10-28] "왕도 대장장이, 치장상점은 가운데로(너무 아래쪽)" -- 남문 앞에서 가운데 광장 분수 남쪽 양옆으로(부활 칸 (c, c+5) 둘레는 비워 둔다)
        int cc = ct.w / 2;
        placeNpc(ct, cc - 5, cc + 6, "smith", "왕도 대장장이", "왕도의 화로는 꺼지지 않지. 마을 대장간에선 못 만드는 물건을 만들어 주겠네.");
        placeNpc(ct, cc + 5, cc + 6, "boutique", "왕도 명품상점", "어서 오세요~ 왕도에서만 파는 반짝반짝 새 옷도 있어요. 성별도 바꿔 드려요.");
        // [2026-10-26] 방한 외투: 설산 마을 털옷 장인(제값), 설원 정박장 암상인(세 배, 매시 정각~15분에만 -- 화면/서버가 시각으로 판단, 칸은 늘 노점)
        S5MineMap tw = out.get(SNOWTOWN);
        placeNpc(tw, tw.villageX + 3, tw.villageY + 1, "furrier", "털옷 장인", "설산 바람은 뼈까지 파고들지. 내 외투면 버틸 만할 거요. 대신 몸이 둔해져서 몬스터한테는 더 아프게 맞을 거요.");
        S5MineMap nm = out.get(NORTH);
        placeNpc(nm, nm.dockX(), nm.dockY() - 3, "blackmarket", "암상인", "쉿... 아무 데서나 못 구하는 물건이지. 살 거면 서두르게. 15분이 되면 난 사라진다네.");
        for (Npc n : nm.npcs) if ("blackmarket".equals(n.kind)) nm.safe.add(new int[] { n.x, n.y, 3 }); // 노점 앞도 안전지대
        ct.citizens = cityCitizens(ct, seed); // [2026-10-27] 왕도 주민(NPC를 다 세운 뒤 -- 'N' 칸은 피해 간다)
        // [2026-10-28] "요일 동굴 앞에 NPC -- 오늘의 요일석 20개를 내면 골라서 다른 요일 동굴에" -- 평원 서쪽 통로(요일 던전 입구) 안쪽
        S5MineMap fd = out.get(FIELD);
        int[] wg = besidePortal(fd, DUNGEON);
        for (int off = 3; off <= 33; off += 10) // 통로 둘레가 좁으면 안쪽(동쪽)으로 더 들어가 자리를 찾는다
            if (placeNpc(fd, wg[0] + off, wg[1], "daykeeper", "요일 동굴 문지기", "오늘의 요일석 20개를 내면 다른 요일의 동굴 문을 열어 주지. 한 번 열면 오늘 하루는 그대로라네.")) break;
        // [2026-10-24] 몬스터 배치(맵마다 시드로) -- 위치는 시각으로 정해지므로 여기서는 집 칸과 돌아다닐 칸만 정한다.
        int k = 0;
        for (S5MineMap m : out.values()) m.mobs = Collections.unmodifiableList(S5MineMobs.spawn(m, seed * 13 + 101 + k++));
        return Collections.unmodifiableMap(out);
    }

    private static S5MineMap relink(S5MineMap m, List<Portal> portals, List<Npc> npcs) {
        S5MineMap n = new S5MineMap(m.id, m.name, m.dark, m.w, m.h, m.spawnX, m.spawnY, m.terrain, m.ore, portals);
        n.level = m.level;
        n.requireItem = m.requireItem;
        n.wind = m.wind;
        n.underwater = m.underwater;
        n.dockX = m.dockX;
        n.dockY = m.dockY;
        n.bossX = m.bossX;
        n.bossY = m.bossY;
        n.villageX = m.villageX;
        n.villageY = m.villageY;
        n.cold = m.cold;
        n.sun = m.sun;
        n.boat = m.boat;
        n.safe = m.safe;
        n.npcs = npcs == null ? Collections.<Npc>emptyList() : Collections.unmodifiableList(npcs);
        return n;
    }

    /** [2026-10-24] 맵 안 지형 문자(몬스터 배치용). */
    char[][] terrainGrid() {
        return terrain;
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
        if (STAR_UNIFIED.get() && share[0] >= 1) { // 위 STAR_UNIFY_CYCLE 참고
            int star = share == ROAD_SHARE || share == ARCHI_SHARE || share == ISLE_SHARE || share == SNOWROAD_SHARE || share == PEAK_SHARE ? 2 : 1;
            share = share.clone();
            share[0] = star;
        }
        int h = t.length, w = t[0].length;
        List<double[]> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!walkable(t[y][x]) || t[y][x] == 'T' || t[y][x] == 'Y' || t[y][x] == 'e' || (skip != null && skip[y][x])) continue; // 나무 칸은 지나가기만(광석 없음)
            cells.add(new double[] { score[y][x], x, y });
        }
        cells.sort((a, b) -> Double.compare(b[0], a[0]));
        byte[][] o = new byte[h][w];
        int n = cells.size(), idx = 0;
        for (int g = 6; g >= 2; g--) {
            double sh = share[6 - g];
            int take = sh >= 1 ? (int) sh : (int) Math.round(n * sh); // 1 이상 = 칸 개수, 1 미만 = 비율
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
        return generateCave(seed, CAVE1, "광산 동굴 1층", CAVE_SHARE);
    }

    /** [2026-10-24] 동굴 2층도 같은 방식(시드/이름/광석 비율만 다름). */
    static S5MineMap generateCave(long seed, String id, String name, double[] share) {
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
        byte[][] o = assignOre(t, score, share, skip);
        return new S5MineMap(id, name, true, w, h, sx, sy, t, o, portals);
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

    /** [2026-10-23] 서쪽 동굴 = 요일 던전: 광산 동굴과 같은 방식의 동굴(시드만 다름), 어둡지 않게(투구 없이도 보인다), 지하로 가는 문은 없앤다.
     *  아래 가운데 나가는 길 -> 평원 서쪽 통로. 칸마다 광석 등급이 있지만(캔 칸 판정용) 캐면 그날 요일의 돌이 나온다(BotS5ServiceImpl.mineDig). */
    static S5MineMap generateDungeon(long seed) {
        S5MineMap c = generateCave(seed);
        List<Portal> portals = new ArrayList<>();
        for (Portal p : c.portals) {
            if (p.kind == 'S') { c.terrain[p.y][p.x] = '#'; continue; }
            portals.add(p.kind == 'U' ? new Portal(p.x, p.y, 'U', FIELD, 0, 0, "평원으로 나가기") : p);
        }
        return new S5MineMap(DUNGEON, "서쪽 동굴 · 요일 던전", false, c.w, c.h, c.spawnX, c.spawnY, c.terrain, c.ore, portals);
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
        byte[][] o = assignOre(t, score, BEACH_SHARE, skip);
        S5MineMap m = new S5MineMap(BEACH, "남쪽 바닷가", false, w, h, sx, sy, t, o, portals);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(new Npc(capX, capY, "captain", "늙은 선장", "금광석 10개를 주면 신기한 걸 보여주지."));
        m.npcs = npcs;
        return m;
    }
    // ---------------------------------------------------------------- [2026-10-24] 새 맵(성곽 / 섬 / 북쪽 끝 설원)
    /** 동쪽 성곽의 성벽/내성 사각형 {x0, y0, x1, y1} -- 몬스터 구역(S5MineMobs)과 광석 점수에 쓴다. */
    static final int[] CASTLE_WALL = { 28, 12, 121, 97 };
    static final int[] CASTLE_KEEP = { 78, 32, 112, 78 };

    static boolean inRect(int[] rc, int x, int y) {
        return x >= rc[0] && y >= rc[1] && x <= rc[2] && y <= rc[3];
    }

    /** 동쪽 성곽(130x110): 바깥은 풀밭/숲, 가운데 성벽(W)으로 둘러싼 성 안(돌바닥 s, 기둥), 그 안 동쪽에 내성. 서쪽 끝 통로 -> 평원 동쪽 통로. */
    static S5MineMap generateCastle(long seed) {
        int w = 130, h = 110, cy = h / 2;
        Random r = new Random(seed);
        Noise moist = new Noise(seed * 31 + 41), rich = new Noise(seed * 31 + 42);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double mo = moist.fbm(x / 20.0, y / 20.0);
            t[y][x] = mo > 0.62 ? (r.nextDouble() < 0.3 ? 'T' : '.') : (r.nextDouble() < 0.03 ? 'T' : (mo < 0.35 ? ',' : '.'));
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        castleRing(t, CASTLE_WALL, 2, r, cy);
        castleRing(t, CASTLE_KEEP, 1, r, cy);
        // 기둥(성 마당/내성) -- 가운데 큰길(cy +-2)은 비워 둔다
        for (int y = CASTLE_WALL[1] + 6; y < CASTLE_WALL[3] - 4; y += 8) for (int x = CASTLE_WALL[0] + 6; x < CASTLE_KEEP[0] - 3; x += 8) if (Math.abs(y - cy) > 2) t[y][x] = 'W';
        for (int y = CASTLE_KEEP[1] + 5; y < CASTLE_KEEP[3] - 3; y += 7) for (int x = CASTLE_KEEP[0] + 5; x < CASTLE_KEEP[2] - 3; x += 7) if (Math.abs(y - cy) > 2) t[y][x] = 'W';
        for (int x = 1; x < CASTLE_WALL[0]; x++) for (int y = cy - 1; y <= cy + 1; y++) t[y][x] = ','; // 서쪽 입구 -> 성문 큰길
        t[cy][0] = 'G';
        int sx = 1, sy = cy;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(0, cy, 'G', FIELD, 0, 0, "중앙 평원으로 돌아가는 길"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 14.0, y / 14.0) + (inRect(CASTLE_WALL, x, y) ? 0.15 : 0) + (inRect(CASTLE_KEEP, x, y) ? 0.12 : 0) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, CASTLE_SHARE, skip);
        return new S5MineMap(CASTLE, "동쪽 성곽", false, w, h, sx, sy, t, o, portals);
    }

    /** 사각형 성벽(두께 th) + 안쪽 돌바닥 + 서쪽 가운데 성문(3칸). */
    private static void castleRing(char[][] t, int[] rc, int th, Random r, int cy) {
        for (int y = rc[1]; y <= rc[3]; y++) for (int x = rc[0]; x <= rc[2]; x++) {
            boolean edge = x < rc[0] + th || x > rc[2] - th || y < rc[1] + th || y > rc[3] - th;
            t[y][x] = edge ? 'W' : (r.nextDouble() < 0.05 ? '.' : 's');
        }
        for (int y = cy - 1; y <= cy + 1; y++) for (int x = rc[0]; x < rc[0] + th; x++) t[y][x] = 's';
    }

    /** 부두(3칸 폭)를 바다 첫 줄(seaStart)에서 바다 쪽(dir -1 = 북, +1 = 남)으로 + 동쪽에 배(6x8) + 부두와 배 사이 판자(B). 반환 {판자 x, 판자 y}. */
    private static int[] pierAndShip(char[][] t, int pier, int seaStart, int dir) {
        for (int i = -1; i < 12; i++) { int y = seaStart + dir * i; for (int x = pier; x <= pier + 2; x++) t[y][x] = 'P'; }
        for (int i = 3; i < 11; i++) { int y = seaStart + dir * i; for (int x = pier + 4; x < pier + 10; x++) t[y][x] = 'q'; }
        int py = seaStart + dir * 6;
        t[py][pier + 3] = 'B';
        return new int[] { pier + 3, py };
    }

    /** 남쪽 섬(100x100, 배로만): 바다 가운데 섬 -- 둘레 모래사장, 안쪽 풀밭/숲/바위. 북쪽 해안에 부두와 배(타면 다른 항구로). */
    static S5MineMap generateIsland(long seed) {
        int w = 100, h = 100, cx = 50, cy = 56;
        Random r = new Random(seed);
        Noise shape = new Noise(seed * 31 + 51), green = new Noise(seed * 31 + 52), rich = new Noise(seed * 31 + 53);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double d = Math.hypot(x - cx, (y - cy) * 1.1) / 36.0 + (shape.fbm(x / 14.0, y / 14.0) - 0.5) * 0.45;
            double gr = green.fbm(x / 12.0, y / 12.0);
            char c;
            if (d >= 1.0) c = '~';
            else if (d >= 0.80) c = 'b';
            else if (gr > 0.58) c = r.nextDouble() < 0.45 ? 'T' : '.';
            else if (gr < 0.30) c = r.nextDouble() < 0.5 ? ':' : '.';
            else c = r.nextDouble() < 0.05 ? 'T' : '.';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '~'; t[h - 1][x] = '~'; }
        for (int y = 0; y < h; y++) { t[y][0] = '~'; t[y][w - 1] = '~'; }
        int pier = cx - 1, land = 1;
        while (land < h - 1 && t[land][pier + 1] == '~') land++; // 부두 가운데 열의 첫 육지
        for (int y = land; y < land + 3; y++) for (int x = pier; x <= pier + 2; x++) if (t[y][x] == '~') t[y][x] = 'b';
        int[] plank = pierAndShip(t, pier, land - 1, -1);
        int sx = pier + 2, sy = plank[1];
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '~';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(plank[0], plank[1], 'B', SHIP, 0, 0, "배에 오르기"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = t[y][x] == 'P' || Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 13.0, y / 13.0) + 0.2 * (1.0 - Math.min(1, Math.hypot(x - cx, y - cy) / 40.0)) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, ISLAND_SHARE, skip);
        S5MineMap m = new S5MineMap(ISLAND, "남쪽 섬", false, w, h, sx, sy, t, o, portals);
        m.dockX = sx;
        m.dockY = sy;
        return m;
    }

    // ---------------------------------------------------------------- [2026-10-28] 다도해
    static final double[] ARCHI_SHARE = { 2, 40, 0.08, 0.20, 0.30 };
    static final double[] ISLE_SHARE = { 2, 30, 0.08, 0.20, 0.30 };

    /** 다도해 본섬(300x300): 큰 섬 가운데 붉은 신궁(L, 남쪽 문 S -> 신궁 내부, 앞은 붉은 참배길 p), 남쪽 해안 부두와 배(남쪽 섬으로),
     *  부두 위 돌바닥 마을 광장(배 목수/뱃사공). 그늘(안전지대 -- 햇빛 피해 없음): 마을, 신궁 앞, 섬 곳곳 야자수 그늘. */
    static S5MineMap generateArchiMain(long seed) {
        int w = 300, h = 300, cx = 150, cy = 140;
        Random r = new Random(seed);
        Noise shape = new Noise(seed * 31 + 91), green = new Noise(seed * 31 + 92), rich = new Noise(seed * 31 + 93);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double d = Math.hypot(x - cx, (y - cy) * 1.05) / 128.0 + (shape.fbm(x / 30.0, y / 30.0) - 0.5) * 0.35;
            double gr = green.fbm(x / 16.0, y / 16.0);
            char c;
            if (d >= 1.0) c = '~';
            else if (d >= 0.9) c = 'b';
            else if (gr > 0.62) c = r.nextDouble() < 0.4 ? 'T' : '.';
            else if (gr < 0.28) c = r.nextDouble() < 0.4 ? ':' : '.';
            else c = r.nextDouble() < 0.04 ? 'T' : '.';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '~'; t[h - 1][x] = '~'; }
        for (int y = 0; y < h; y++) { t[y][0] = '~'; t[y][w - 1] = '~'; }
        // 신궁: 둘레 돌바닥, 붉은 건물, 남쪽 가운데 문, 문 앞 참배길
        int sx0 = cx - 14, sx1 = cx + 14, sy0 = cy - 12, sy1 = cy + 8;
        for (int y = sy0 - 3; y <= sy1 + 3; y++) for (int x = sx0 - 3; x <= sx1 + 3; x++) t[y][x] = 's';
        for (int y = sy0; y <= sy1; y++) for (int x = sx0; x <= sx1; x++) t[y][x] = 'L';
        int doorX = cx, doorY = sy1;
        t[doorY][doorX] = 'S';
        for (int y = doorY + 1; y <= doorY + 30; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = 'p';
        // 남쪽 해안 부두와 배
        int pier = cx - 1, land = h - 2;
        while (land > 1 && t[land][pier + 1] == '~') land--;
        for (int y = land - 2; y <= land; y++) for (int x = pier; x <= pier + 2; x++) if (t[y][x] == '~') t[y][x] = 'b';
        int[] plank = pierAndShip(t, pier, land + 1, 1);
        int sx = pier + 2, sy = plank[1];
        // 부두 위 마을 광장
        int vx = pier + 1, vy = land - 7;
        for (int y = vy - 5; y <= vy + 4; y++) for (int x = vx - 9; x <= vx + 9; x++) t[y][x] = 's';
        for (int y = vy + 5; y <= land; y++) for (int x = pier; x <= pier + 2; x++) if (t[y][x] != 'P') t[y][x] = 's'; // 부두와 광장을 잇는 길
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '~';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(plank[0], plank[1], 'B', SHIP, 0, 0, "배에 오르기(남쪽 섬으로)"));
        portals.add(new Portal(doorX, doorY, 'S', SHRINE, 0, 0, "신궁 안으로"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = t[y][x] == 'P' || t[y][x] == 's' || t[y][x] == 'p' || Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 15.0, y / 15.0) + 0.2 * Math.min(1, Math.hypot(x - vx, y - vy) / 140.0) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, ARCHI_SHARE, skip);
        S5MineMap m = new S5MineMap(ARCHI, "다도해 본섬", false, w, h, sx, sy, t, o, portals);
        m.dockX = sx;
        m.dockY = sy;
        m.villageX = vx;
        m.villageY = vy;
        m.sun = 1;
        m.safe.add(new int[] { vx, vy, 7 });
        m.safe.add(new int[] { cx, doorY + 5, 4 });
        addShadeGroves(m, r, 10);
        return m;
    }

    /** 그늘(야자수 숲) -- 풀밭/모래 칸에 서로 떨어지게 n곳, 반지름 3. 햇빛 피해가 없는 안전지대(화면이 야자수 그늘로 그린다). */
    private static void addShadeGroves(S5MineMap m, Random r, int n) {
        for (int tries = 0, made = 0; made < n && tries < 4000; tries++) {
            int x = 3 + r.nextInt(m.w - 6), y = 3 + r.nextInt(m.h - 6);
            char c = m.terrain[y][x];
            if (c != '.' && c != 'b' && c != 'u') continue;
            boolean far = Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) > 12;
            for (int[] z : m.safe) if (Math.abs(z[0] - x) + Math.abs(z[1] - y) < Math.max(18, m.w / 6)) far = false;
            if (!far) continue;
            m.safe.add(new int[] { x, y, 3 });
            made++;
        }
    }

    /** 신궁 내부(96x78): 붉은 기둥 벽(L) 사이로 방 4x3개와 3칸 너비 복도, 바닥은 붉은 길(p), 방마다 기둥. 아래 가운데 방 밑으로 나가는 길(U). 광석 없음, 닌자만. */
    static S5MineMap generateShrine(long seed) {
        int w = 96, h = 78, rw = 18, rh = 16;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, 'L');
        int[][] rc = new int[12][];
        for (int row = 0; row < 3; row++) for (int col = 0; col < 4; col++) {
            int x0 = 4 + col * 23, y0 = 4 + row * 24;
            rc[row * 4 + col] = new int[] { x0, y0 };
            for (int y = y0; y < y0 + rh; y++) for (int x = x0; x < x0 + rw; x++) t[y][x] = 'p';
            if (!(row == 2 && col == 1)) for (int[] pp : new int[][] { { 4, 4 }, { 12, 4 }, { 4, 10 }, { 12, 10 } }) // 기둥(들어오는 방은 비움)
                if (r.nextDouble() < 0.8) for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) t[y0 + pp[1] + yy][x0 + pp[0] + xx] = 'L';
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) { // 가로 복도
            int[] a = rc[row * 4 + col];
            for (int x = a[0] + rw; x < a[0] + 23; x++) for (int y = a[1] + rh / 2 - 1; y <= a[1] + rh / 2 + 1; y++) t[y][x] = 'p';
        }
        for (int row = 0; row < 2; row++) for (int col = 0; col < 4; col++) { // 세로 복도
            int[] a = rc[row * 4 + col];
            for (int y = a[1] + rh; y < a[1] + 24; y++) for (int x = a[0] + rw / 2 - 1; x <= a[0] + rw / 2 + 1; x++) t[y][x] = 'p';
        }
        int[] ent = rc[2 * 4 + 1];
        int ex = ent[0] + rw / 2;
        for (int y = ent[1] + rh; y < h - 1; y++) for (int x = ex - 1; x <= ex + 1; x++) t[y][x] = 'p';
        t[h - 2][ex] = 'U';
        for (int x = 0; x < w; x++) { t[0][x] = 'L'; t[h - 1][x] = 'L'; }
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(ex, h - 2, 'U', ARCHI, 0, 0, "신궁 밖으로"));
        return new S5MineMap(SHRINE, "신궁 내부", false, w, h, ex, h - 5, t, new byte[h][w], portals);
    }

    /** 다도해 뱃길(44x330, 세로): 온통 바다(배로만 다닌다 -- boat), 가장자리에 바위. 아래 가운데 = 본섬 부두, 위로 42칸마다 왼쪽/오른쪽 번갈아 섬 부두(판자 B) 7곳.
     *  부두 둘레는 모래 몇 칸. 광석/몬스터 없음. 1인용 배가 있어야 들어온다(requireItem). */
    static S5MineMap generateArchiSea(long seed) {
        int w = 44, h = 330;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = Math.abs(x - w / 2) > 6 && r.nextDouble() < 0.02 ? '^' : '~';
        for (int x = 0; x < w; x++) { t[0][x] = '^'; }
        List<Portal> portals = new ArrayList<>();
        for (int k = 0; k <= ISLE_IDS.length; k++) {
            int[] d = seaDock(w, h, k);
            for (int y = d[1] - 2; y <= d[1] + 2; y++) for (int x = d[0] - 2; x <= d[0] + 2; x++) if (x >= 0 && x < w && y > 0 && y < h) t[y][x] = (x == 0 || x == w - 1 || y == h - 1) ? 'b' : '~';
            for (int y = d[1] - 2; y <= d[1] + 2; y++) { int sx = d[0] <= 1 ? 0 : d[0] >= w - 2 ? w - 1 : -1; if (sx >= 0) t[y][sx] = 'b'; }
            t[d[1]][d[0]] = 'B';
            portals.add(k == 0 ? new Portal(d[0], d[1], 'B', ARCHI, 0, 0, "다도해 본섬에 내리기")
                    : new Portal(d[0], d[1], 'B', ISLE_IDS[k - 1], 0, 0, ISLE_NAMES[k - 1] + "에 내리기"));
        }
        S5MineMap m = new S5MineMap(ARCHISEA, "다도해 뱃길", false, w, h, w / 2, h - 7, t, new byte[h][w], portals);
        m.boat = true;
        m.sun = 1;
        m.requireItem = BOAT_ITEM;
        return m;
    }

    /** 뱃길 부두 판자 칸: k = 0 본섬(아래 가운데), 1~7 섬(위로 42칸마다, 홀수 왼쪽/짝수 오른쪽 가장자리). */
    private static int[] seaDock(int w, int h, int k) {
        if (k == 0) return new int[] { w / 2, h - 2 };
        return new int[] { k % 2 == 1 ? 1 : w - 2, h - 10 - k * 42 };
    }

    /** 뱃길에서 그 섬 부두 판자 바로 옆 물 칸(섬에서 배를 타면 여기로). */
    private static int[] seaDockBeside(S5MineMap sea, String isle) {
        for (Portal p : sea.portals) if (isle.equals(p.target)) return new int[] { p.x <= 1 ? p.x + 1 : p.x - 1, p.y };
        return new int[] { sea.spawnX, sea.spawnY };
    }

    /** 다도해 섬(크기 ISLE_SIZE): 바다 가운데 섬, 북쪽 해안에 부두와 배(뱃길로). 섬마다 모습이 다르다 -- 0 동백(숲), 1 갈매기(바위), 2 거북(모래톱),
     *  3 안개(짙은 숲), 4 산호(바다 모래/해초), 5 달맞이(풀밭/바위), 6 용암(자갈/바위산). 그늘(야자수) 몇 곳 + 부두 앞. */
    static S5MineMap generateIsle(long seed, int idx) {
        int size = ISLE_SIZE[idx], w = size, h = size, cx = w / 2, cy = h / 2 + 4;
        Random r = new Random(seed);
        Noise shape = new Noise(seed * 31 + 71), green = new Noise(seed * 31 + 72), rich = new Noise(seed * 31 + 73);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double d = Math.hypot(x - cx, (y - cy) * 1.1) / (size * 0.4) + (shape.fbm(x / 16.0, y / 16.0) - 0.5) * 0.4;
            double gr = green.fbm(x / 12.0, y / 12.0), q = r.nextDouble();
            char c;
            if (d >= 1.0) c = '~';
            else if (d >= 0.84) c = idx == 4 ? 'u' : 'b';
            else switch (idx) {
                case 0: c = gr > 0.5 ? (q < 0.5 ? 'T' : '.') : (q < 0.06 ? 'T' : '.'); break;
                case 1: c = gr > 0.66 ? '^' : gr < 0.4 ? (q < 0.5 ? ':' : '.') : '.'; break;
                case 2: c = gr > 0.55 ? 'b' : (q < 0.05 ? 'T' : '.'); break;
                case 3: c = gr > 0.42 ? (q < 0.55 ? 'T' : '.') : '.'; break;
                case 4: c = gr > 0.6 ? 'e' : gr < 0.3 ? 'O' : 'u'; break;
                case 5: c = gr > 0.68 ? '^' : gr < 0.35 ? (q < 0.4 ? ':' : '.') : '.'; break;
                default: c = gr > 0.62 ? '^' : gr < 0.35 ? 'r' : (q < 0.3 ? 'r' : ':'); break;
            }
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '~'; t[h - 1][x] = '~'; }
        for (int y = 0; y < h; y++) { t[y][0] = '~'; t[y][w - 1] = '~'; }
        int pier = cx - 1, land = 1;
        while (land < h - 1 && t[land][pier + 1] == '~') land++;
        for (int y = land; y < land + 3; y++) for (int x = pier; x <= pier + 2; x++) if (!walkable(t[y][x])) t[y][x] = idx == 4 ? 'u' : 'b';
        int[] plank = pierAndShip(t, pier, land - 1, -1);
        int sx = pier + 2, sy = plank[1];
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '~';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(plank[0], plank[1], 'B', ARCHISEA, 0, 0, "배를 타고 뱃길로"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = t[y][x] == 'P' || Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 13.0, y / 13.0) + 0.2 * Math.min(1, Math.hypot(x - sx, y - sy) / (size * 0.7)) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, ISLE_SHARE, skip);
        S5MineMap m = new S5MineMap(ISLE_IDS[idx], ISLE_NAMES[idx], false, w, h, sx, sy, t, o, portals);
        m.dockX = sx;
        m.dockY = sy;
        m.sun = ISLE_SUN[idx];
        m.safe.add(new int[] { pier + 1, land + 2, 4 }); // 부두 앞 그늘
        addShadeGroves(m, r, 3 + size / 40);
        return m;
    }

    /** 북쪽 끝 설원(130x130, 배로만 -- 사막 북쪽 통로는 잠김): 눈밭(n), 눈 덮인 소나무(Y), 바위산, 얼어붙은 호수(~). 남쪽 해안에 부두와 배.
     *  부두에서 북쪽 끝까지 3칸 너비 눈길. 북쪽일수록 광석이 좋다. */
    static S5MineMap generateNorth(long seed) {
        int w = 130, h = 130;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 61), pine = new Noise(seed * 31 + 62), lake = new Noise(seed * 31 + 63), rich = new Noise(seed * 31 + 64), shore = new Noise(seed * 31 + 65);
        int pier = w / 2;
        int[] coast = new int[w];
        for (int x = 0; x < w; x++) coast[x] = h - 16 + (int) Math.round((shore.fbm(x / 14.0, 1.3) - 0.5) * 6);
        for (int x = pier - 3; x <= pier + 12; x++) coast[x] = coast[pier];
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c;
            if (y >= coast[x]) c = '~';
            else if (lake.fbm(x / 22.0, y / 22.0) > 0.72) c = '~';
            else if (rock.fbm(x / 18.0, y / 18.0) > 0.70) c = '^';
            else if (pine.fbm(x / 12.0, y / 12.0) > 0.60) c = r.nextDouble() < 0.35 ? 'Y' : 'n';
            else c = r.nextDouble() < 0.04 ? 'Y' : 'n';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) t[0][x] = '^';
        for (int y = 0; y < h; y++) { if (t[y][0] != '~') t[y][0] = '^'; if (t[y][w - 1] != '~') t[y][w - 1] = '^'; }
        int[] plank = pierAndShip(t, pier, coast[pier], 1);
        int sx = pier + 2, sy = plank[1];
        // 부두에서 북쪽 끝까지 눈길(3칸)
        int px = pier + 1;
        int[] pathX = new int[h];
        for (int y = coast[pier] - 1; y >= 1; y--) {
            if (r.nextDouble() < 0.2) px += r.nextBoolean() ? 1 : -1;
            px = Math.max(3, Math.min(w - 4, px));
            for (int dx = -1; dx <= 1; dx++) t[y][px + dx] = 'n';
            pathX[y] = px;
        }
        // [2026-10-26] 눈길 북쪽 끝 = 설산으로 오르는 입구(G). 위쪽 줄은 바위산, 입구 앞은 모닥불 공터. 전날 여기 둔 작은 마을은
        //  "3층 설산을 지나면 설산 마을" 요청으로 설산 3층 너머(generateSnowTown)로 옮겼다. 난수를 더 쓰지 않아 다른 칸 배치는 그대로다.
        final int gy = 6;
        int gx = pathX[gy + 1];
        for (int y = 1; y <= gy; y++) for (int x = 1; x < w - 1; x++) t[y][x] = '^';
        for (int y = gy + 1; y <= gy + 4; y++) for (int x = Math.max(1, gx - 3); x <= Math.min(w - 2, gx + 3); x++) t[y][x] = 'n';
        t[gy][gx] = 'G';
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(plank[0], plank[1], 'B', SHIP, 0, 0, "배에 오르기"));
        portals.add(new Portal(gx, gy, 'G', SNOW1, 0, 0, "설산으로 오르는 길")); // 대상 좌표는 buildWorld가 채운다
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = t[y][x] == 'P' || Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 15.0, y / 15.0) + 0.28 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, NORTH_SHARE, skip);
        S5MineMap m = new S5MineMap(NORTH, "북쪽 끝 설원", false, w, h, sx, sy, t, o, portals);
        m.dockX = sx;
        m.dockY = sy;
        // [2026-10-26] 한기 1단계. 안전지대: 정박장("여긴 피해 없음"), 설산 입구 앞 공터, 눈길과 격자 모닥불.
        m.cold = 1;
        m.safe.add(new int[] { sx, sy, 6 });
        m.safe.add(new int[] { gx, gy + 2, 3 });
        campfires(t, pathX, gy + 4, coast[pier] - 8, 22, m.safe);
        return m;
    }

    /** [2026-10-26] 설산 floor층(90x120, 한기 floor + 1): 눈밭/눈 덮인 소나무/바위산(위층일수록 바위가 많아 길이 좁다)/얼음.
     *  아래 가운데 입구(G, 내려가는 길)에서 위로 굽이치는 3칸 눈길, 맨 위 G = 다음 층(3층은 설산 마을). 입구/출구/눈길/격자에 모닥불. */
    static S5MineMap generateSnowMountain(long seed, String id, String name, int floor) {
        int w = 90, h = 120;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 81), pine = new Noise(seed * 31 + 82), lake = new Noise(seed * 31 + 83), rich = new Noise(seed * 31 + 84);
        double rockTh = 0.56 - floor * 0.02; // 바위산이 두껍게(층마다 더) -- 눈길 따라 오르는 느낌, 3층은 좁은 능선
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c;
            if (lake.fbm(x / 20.0, y / 20.0) > 0.76) c = '~';
            else if (rock.fbm(x / 14.0, y / 14.0) > rockTh) c = '^';
            else if (pine.fbm(x / 11.0, y / 11.0) > 0.62) c = r.nextDouble() < 0.3 ? 'Y' : 'n';
            else c = r.nextDouble() < 0.03 ? 'Y' : 'n';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        int px = w / 2;
        int[] pathX = new int[h];
        for (int y = h - 2; y >= 1; y--) {
            if (r.nextDouble() < 0.3) px += r.nextBoolean() ? 1 : -1;
            px = Math.max(4, Math.min(w - 5, px));
            for (int dx = -1; dx <= 1; dx++) t[y][px + dx] = 'n';
            pathX[y] = px;
        }
        int bx = pathX[h - 2], tx = pathX[1];
        t[h - 1][bx] = 'G';
        t[0][tx] = 'G';
        int sx = bx, sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        String down = floor == 1 ? NORTH : floor == 2 ? SNOW1 : SNOW2, up = floor == 1 ? SNOW2 : floor == 2 ? SNOW3 : SNOWTOWN;
        portals.add(new Portal(bx, h - 1, 'G', down, 0, 0, floor == 1 ? "북쪽 끝 설원으로 내려가기" : "설산 " + (floor - 1) + "층으로 내려가기"));
        portals.add(new Portal(tx, 0, 'G', up, 0, 0, floor == 3 ? "설산 마을로 가는 길" : "설산 " + (floor + 1) + "층으로 오르기"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 13.0, y / 13.0) + 0.2 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, SNOW_SHARE[floor - 1], skip);
        S5MineMap m = new S5MineMap(id, name, false, w, h, sx, sy, t, o, portals);
        m.cold = floor + 1;
        m.safe.add(new int[] { bx, h - 3, 4 });
        m.safe.add(new int[] { pathX[3], 3, 4 });
        campfires(t, pathX, 3, h - 3, 22, m.safe);
        return m;
    }

    /** [2026-10-26] 설산 마을(44x42) -- 설산 3층 너머. 전날 설원 북쪽 끝에 만들었던 작은 마을(돌담 W로 두른 둥근 마을 V, 북/동/서 문은 눈바람으로 잠김)을
     *  여기로 옮기고 조금 키웠다(초가집 네 채). 바깥은 바위산, 남쪽 문에서 아래로 난 눈길 끝 G = 설산 3층으로 내려가기. 마을 전체가 안전지대(한기 없음). */
    static S5MineMap generateSnowTown(long seed) {
        int w = 44, h = 42, vx = 22, vy = 17, vr = 9;
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, '^');
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            double d = Math.hypot(x - vx, y - vy);
            if (d <= vr) t[y][x] = 'V';
            else if (d <= vr + 1.2) t[y][x] = 'W';
        }
        for (int y = vy + vr - 1; y <= h - 2; y++) for (int x = vx - 1; x <= vx + 1; x++) if (t[y][x] != 'V') t[y][x] = 'n'; // 남쪽 문 + 눈길
        for (int[] hs : new int[][] { { vx - 6, vy - 5 }, { vx + 4, vy - 5 }, { vx - 6, vy + 2 }, { vx + 4, vy + 2 } }) { // 초가집 네 채
            t[hs[1]][hs[0]] = 'h'; t[hs[1]][hs[0] + 1] = 'j'; t[hs[1] + 1][hs[0]] = 'k'; t[hs[1] + 1][hs[0] + 1] = 'l';
        }
        int[][] gates = { { vx, vy - vr - 1 }, { vx + vr + 1, vy }, { vx - vr - 1, vy } };
        for (int[] g : gates) t[g[1]][g[0]] = 'G';
        t[h - 1][vx] = 'G';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(gates[0][0], gates[0][1], 'G', SNOWROAD, 0, 0, "북문 -- 설산 봉우리 가는 길")); // [2026-10-28] 북문 개방(좌표는 buildWorld)
        portals.add(new Portal(gates[1][0], gates[1][1], 'G', null, 0, 0, "동문 -- 눈바람이 너무 심해 나갈 수 없어요"));
        portals.add(new Portal(gates[2][0], gates[2][1], 'G', ROYALROAD, 0, 0, "서문 -- 왕도 북쪽 샛길")); // [2026-10-27] 설산에서 내려가 왕도로(좌표는 buildWorld)
        portals.add(new Portal(vx, h - 1, 'G', SNOW3, 0, 0, "설산 3층으로 내려가기"));
        S5MineMap m = new S5MineMap(SNOWTOWN, "설산 마을", false, w, h, vx, vy + vr + 2, t, new byte[h][w], portals);
        m.villageX = vx;
        m.villageY = vy;
        m.safe.add(new int[] { vx, vy, 30 });
        return m;
    }

    /** [2026-10-27] 왕도 가는 길(260x44) -- "설산에서 내려가면 왕도 아스테리아, 왕도 가는 길을 긴 길로". 동쪽 끝 G = 설산 마을 서문, 서쪽 끝 G = 왕도 동문.
     *  동쪽 3할은 눈 덮인 산길(눈밭/소나무/바위), 가운데는 바위 고원과 숲(흙길), 왕도에 가까운 서쪽 3할 남짓은 들판 사이 돌 포장 대로.
     *  동에서 서로 굽이치는 3칸 길이 끝까지 이어진다. 한기 없음. 몬스터는 설산 쪽일수록 강하다(S5MineMobs.tierAt). */
    static S5MineMap generateRoyalRoad(long seed) {
        int w = 260, h = 44;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 91), wood = new Noise(seed * 31 + 92), rich = new Noise(seed * 31 + 93);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double e = (double) x / w, rk = rock.fbm(x / 14.0, y / 14.0), wd = wood.fbm(x / 11.0, y / 11.0);
            char c;
            if (e > 0.7) c = rk > 0.6 ? '^' : wd > 0.6 ? (r.nextDouble() < 0.3 ? 'Y' : 'n') : (r.nextDouble() < 0.03 ? 'Y' : 'n');
            else if (e > 0.35) c = rk > 0.64 ? '^' : rk > 0.58 ? ':' : wd > 0.6 ? (r.nextDouble() < 0.35 ? 'T' : '.') : '.';
            else c = rk > 0.7 ? ':' : wd > 0.66 ? (r.nextDouble() < 0.3 ? 'T' : '.') : '.';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        int py = h / 2;
        int[] pathY = new int[w];
        for (int x = w - 2; x >= 1; x--) {
            if (r.nextDouble() < 0.22) py += r.nextBoolean() ? 1 : -1;
            py = Math.max(4, Math.min(h - 5, py));
            double e = (double) x / w;
            char rc = e > 0.7 ? 'n' : e > 0.35 ? ',' : 's';
            for (int dy = -1; dy <= 1; dy++) t[py + dy][x] = rc;
            pathY[x] = py;
        }
        int ey = pathY[w - 2], wy = pathY[1];
        t[ey][w - 1] = 'G';
        t[wy][0] = 'G';
        // [2026-10-28] 가운데에서 북쪽으로 갈라지는 샛길 -> 맨 위 G = 성당 옛터(난수를 더 쓰지 않아 다른 칸 배치는 그대로)
        int nx = w / 2;
        for (int y = pathY[nx] - 1; y >= 1; y--) for (int dx = -1; dx <= 1; dx++) t[y][nx + dx] = ',';
        t[0][nx] = 'G';
        int sx = w - 2, sy = ey;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(w - 1, ey, 'G', SNOWTOWN, 0, 0, "설산 마을 서문으로"));
        portals.add(new Portal(0, wy, 'G', CITY, 0, 0, "왕도 아스테리아 동문"));
        portals.add(new Portal(nx, 0, 'G', RUINS, 0, 0, "북쪽 -- 성당 옛터")); // [2026-10-28]
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2 || x <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 15.0, y / 15.0) + 0.2 * ((double) x / w) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, ROAD_SHARE, skip);
        return new S5MineMap(ROYALROAD, "왕도 북쪽 샛길", false, w, h, sx, sy, t, o, portals);
    }

    /** [2026-10-28] 설산 봉우리 가는 길(84x200, 한기 4) -- 설산 마을 북문(아래 G)에서 봉우리(위 G)까지 길게 굽이치는 3칸 눈길.
     *  설산 3층보다 바위가 많아 좁고, 얼음 호수가 군데군데. 눈길과 격자에 모닥불. 몬스터는 20~21단계(Lv.77~81)를 더 세게(S5MineMobs.Type.strong). */
    static S5MineMap generateSnowRoad(long seed) {
        int w = 84, h = 200;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 111), pine = new Noise(seed * 31 + 112), lake = new Noise(seed * 31 + 113), rich = new Noise(seed * 31 + 114);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c;
            if (lake.fbm(x / 18.0, y / 18.0) > 0.74) c = '~';
            else if (rock.fbm(x / 13.0, y / 13.0) > 0.5) c = '^';
            else if (pine.fbm(x / 11.0, y / 11.0) > 0.6) c = r.nextDouble() < 0.3 ? 'Y' : 'n';
            else c = r.nextDouble() < 0.03 ? 'Y' : 'n';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        int px = w / 2;
        int[] pathX = new int[h];
        for (int y = h - 2; y >= 1; y--) {
            if (r.nextDouble() < 0.3) px += r.nextBoolean() ? 1 : -1;
            px = Math.max(5, Math.min(w - 6, px));
            for (int dx = -1; dx <= 1; dx++) t[y][px + dx] = 'n';
            pathX[y] = px;
        }
        int bx = pathX[h - 2], tx = pathX[1];
        t[h - 1][bx] = 'G';
        t[0][tx] = 'G';
        int sx = bx, sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(bx, h - 1, 'G', SNOWTOWN, 0, 0, "설산 마을 북문으로 내려가기"));
        portals.add(new Portal(tx, 0, 'G', PEAK, 0, 0, "설산 봉우리로 오르기"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 13.0, y / 13.0) + 0.2 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, SNOWROAD_SHARE, skip);
        S5MineMap m = new S5MineMap(SNOWROAD, "설산 봉우리 가는 길", false, w, h, sx, sy, t, o, portals);
        m.cold = 4;
        m.safe.add(new int[] { bx, h - 3, 4 });
        m.safe.add(new int[] { pathX[3], 3, 4 });
        campfires(t, pathX, 3, h - 3, 22, m.safe);
        return m;
    }

    /** [2026-10-28] 설산 봉우리(130x130, 한기 4) -- 가운데 큰 얼음 호수(~, 못 지나감)를 눈밭 고리가 둘러싸고 바깥은 바위 절벽.
     *  남쪽 끝 G = 봉우리 가는 길로 내려가기(입구 앞 모닥불), 호수 북쪽 고리에 보스(얼음 여왕) 한 마리만(일반 몬스터 없음). 호수 동/서쪽 고리에 모닥불 하나씩. */
    static S5MineMap generatePeak(long seed) {
        int w = 130, h = 130, cx = w / 2, cy = 60;
        Random r = new Random(seed);
        Noise edge = new Noise(seed * 31 + 121), pine = new Noise(seed * 31 + 122), rich = new Noise(seed * 31 + 123);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double dx = x - cx, dy = (y - cy) * 1.1, d = Math.hypot(dx, dy), wob = (edge.fbm(x / 14.0, y / 14.0) - 0.5) * 10;
            char c;
            if (d < 30 + wob) c = '~';                       // 가운데 얼음 호수
            else if (d > 54 + wob * 1.4) c = '^';            // 바깥 절벽
            else if (pine.fbm(x / 10.0, y / 10.0) > 0.66) c = r.nextDouble() < 0.3 ? 'Y' : 'n';
            else c = r.nextDouble() < 0.02 ? 'Y' : r.nextDouble() < 0.03 ? '^' : 'n';
            t[y][x] = c;
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        // 고리가 끊기지 않게 호수 둘레 바로 바깥(호수에서 3~5칸) 바위/나무는 눈밭으로
        char[][] t0 = new char[h][];
        for (int y = 0; y < h; y++) t0[y] = t[y].clone();
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if (t0[y][x] == '~') continue;
            int near = 99;
            for (int k = 1; k <= 5 && near == 99; k++)
                if ((x - k >= 0 && t0[y][x - k] == '~') || (x + k < w && t0[y][x + k] == '~') || (y - k >= 0 && t0[y - k][x] == '~') || (y + k < h && t0[y + k][x] == '~')) near = k;
            if (near >= 2 && near <= 5) t[y][x] = 'n';
        }
        // 남쪽 입구 -- 맨 아래 G에서 호수 둘레 고리까지 3칸 눈길
        int ringS = cy + 1;
        while (ringS < h - 2 && t[ringS][cx] == '~') ringS++;
        for (int y = ringS; y <= h - 2; y++) for (int dx = -1; dx <= 1; dx++) if (t[y][cx + dx] != '~') t[y][cx + dx] = 'n';
        t[h - 1][cx] = 'G';
        int sx = cx, sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        // 보스 자리 -- 호수 북쪽 고리(호수 가장자리에서 4칸 바깥)
        int bx = cx, by = cy - 1;
        while (by > 6 && t[by][bx] == '~') by--;
        by -= 4;
        if (!walkable(t[by][bx])) for (int d = 1; d < 20; d++) {
            if (walkable(t[by][bx + d])) { bx += d; break; }
            if (walkable(t[by][bx - d])) { bx -= d; break; }
        }
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(cx, h - 1, 'G', SNOWROAD, 0, 0, "설산 봉우리 가는 길로 내려가기"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 13.0, y / 13.0) + 0.2 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, PEAK_SHARE, skip);
        S5MineMap m = new S5MineMap(PEAK, "설산 봉우리", false, w, h, sx, sy, t, o, portals);
        m.cold = 4;
        m.bossX = bx;
        m.bossY = by;
        m.safe.add(new int[] { cx, h - 4, 4 }); // 입구 앞
        for (int side : new int[] { -1, 1 }) { // 호수 동/서쪽 고리
            int x = cx + side;
            while (x > 1 && x < w - 2 && t[cy][x] == '~') x += side;
            x += side * 3;
            if (x > 1 && x < w - 2 && walkable(t[cy][x])) m.safe.add(new int[] { x, cy, 3 });
        }
        return m;
    }

    /** [2026-10-28] 성당 옛터(120x110): 넓은 풀밭 평지 가운데 무너진 대성당 터 -- 돌바닥(s) 위로 잔해(r, 지나갈 수 있음)가 흩어지고,
     *  둘레 벽(W)은 군데군데 무너져 드나들 수 있고, 두 줄로 섰던 기둥은 몇 개만 남았다(^, 못 지나감). 북쪽 제단에 대주교(보스), 남쪽 끝 G = 왕도 가는 길.
     *  일반 몬스터와 광석은 없다(S5MineMobs: 대주교 + 수하 자리만). */
    static S5MineMap generateRuins(long seed) {
        int w = 120, h = 110, c = w / 2, x0 = c - 18, x1 = c + 18, y0 = 18, y1 = 70;
        Random r = new Random(seed);
        Noise rub = new Noise(seed * 31 + 101);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = r.nextDouble() < 0.015 ? 'T' : r.nextDouble() < 0.08 ? ',' : '.';
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            boolean edge = y == y0 || y == y1 || x == x0 || x == x1;
            t[y][x] = edge ? (r.nextDouble() < 0.55 ? 'W' : 'r') : 's';
        }
        for (int y = y0 - 8; y <= y1 + 8; y++) for (int x = x0 - 8; x <= x1 + 8; x++) { // 무너진 잔해(성당 안팎)
            if (y < 1 || x < 1 || y >= h - 1 || x >= w - 1 || t[y][x] == 'W') continue;
            if (rub.fbm(x / 6.0, y / 6.0) > 0.58 && r.nextDouble() < 0.7) t[y][x] = 'r';
        }
        for (int py = y0 + 8; py < y1 - 4; py += 9) for (int px : new int[] { c - 10, c + 10 }) t[py][px] = r.nextDouble() < 0.6 ? '^' : 'r'; // 남은 기둥
        for (int y = y0 + 4; y <= y0 + 12; y++) for (int x = c - 4; x <= c + 4; x++) t[y][x] = 's'; // 제단 앞
        for (int y = y1; y <= h - 2; y++) for (int dx = -1; dx <= 1; dx++) t[y][c + dx] = ','; // 남쪽 입구에서 성당 터까지
        t[h - 1][c] = 'G';
        int sx = c, sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(c, h - 1, 'G', ROYALROAD, 0, 0, "왕도 북쪽 샛길로"));
        S5MineMap m = new S5MineMap(RUINS, "성당 옛터", false, w, h, sx, sy, t, new byte[h][w], portals);
        m.bossX = c;
        m.bossY = y0 + 6;
        return m;
    }

    /** [2026-10-24] 모래폭풍 너머 초원(140x140): 풀밭, 드문드문 숲과 바위. 남쪽 가운데 -> 사막 북쪽 끝, 남에서 북으로 굽이치는 흙길 끝 -> 왕도 남문. */
    static S5MineMap generateSteppe(long seed) {
        int w = 140, h = 140;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 71), grove = new Noise(seed * 31 + 72), rich = new Noise(seed * 31 + 73);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double rk = rock.fbm(x / 16.0, y / 16.0), gv = grove.fbm(x / 12.0, y / 12.0);
            t[y][x] = rk > 0.72 ? '^' : rk > 0.66 ? ':' : gv > 0.62 ? (r.nextDouble() < 0.4 ? 'T' : '.') : (r.nextDouble() < 0.02 ? 'T' : (gv < 0.3 ? ',' : '.'));
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        int sx = w / 2, px = sx;
        for (int y = h - 2; y >= 1; y--) {
            if (r.nextDouble() < 0.25) px += r.nextBoolean() ? 1 : -1;
            px = Math.max(5, Math.min(w - 6, px));
            for (int dx = -1; dx <= 1; dx++) t[y][px + dx] = ',';
        }
        for (int dx = -1; dx <= 1; dx++) t[h - 2][sx + dx] = ',';
        t[h - 1][sx] = 'G';
        t[0][px] = 'G';
        int sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(sx, h - 1, 'G', DESERT, 0, 0, "모래폭풍 너머 사막으로"));
        portals.add(new Portal(px, 0, 'G', CITY, 0, 0, "왕도 아스테리아 남문"));
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2;
            score[y][x] = 0.55 * rich.fbm(x / 15.0, y / 15.0) + 0.2 * (1.0 - (double) y / h) + 0.16 * r.nextDouble();
        }
        byte[][] o = assignOre(t, score, STEPPE_SHARE, skip);
        return new S5MineMap(STEPPE, "왕도 남쪽 초원", false, w, h, sx, sy, t, o, portals);
    }

    /** [2026-10-24] 왕도 아스테리아(120x120, 라그나로크 프론테라 느낌): 두꺼운 성벽(W)으로 둘러싼 정사각 도시, 성문 셋(남문만 열림, 동/서문은 잠김),
     *  성문에서 가운데까지 넓은 십자 대로(돌바닥 s), 가운데 둥근 광장과 분수(~), 광장 남쪽 텔레포트 게이트(X), 북쪽 대로 끝에 성당(Q, 신부 NPC),
     *  네 구역에 붉은 지붕 건물(K)과 정원(풀/나무). 몬스터와 광석은 없다(안전 지대). */
    static S5MineMap generateCity(long seed) {
        int w = 120, h = 120, c = w / 2, wt = 3, road = 3;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = 's';
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (x < wt || y < wt || x >= w - wt || y >= h - wt) t[y][x] = 'W';
        // 네 구역: 정원(풀/나무) 바탕, 십자 대로와 광장 둘레는 돌바닥
        for (int y = wt + 2; y < h - wt - 2; y++) for (int x = wt + 2; x < w - wt - 2; x++) {
            if (Math.abs(x - c) <= road + 1 || Math.abs(y - c) <= road + 1) continue;
            if (Math.hypot(x - c, y - c) < 17) continue;
            t[y][x] = r.nextDouble() < 0.08 ? 'T' : '.';
        }
        // 붉은 지붕 건물 블록(대로/광장에서 떨어진 곳), 건물 앞은 돌길
        // [2026-10-27] "왕도 내 건물 높게, 대도시처럼" -- 블록을 세로로 길게(8~10칸): 화면이 위 두 줄은 지붕, 그 아래는 층마다 창이 난 벽, 맨 아래는 1층 앞면으로 그린다.
        for (int by = wt + 4; by < h - wt - 12; by += 14) for (int bx = wt + 4; bx < w - wt - 8; bx += 11) {
            int bw = 6 + r.nextInt(3), bh = 8 + r.nextInt(3);
            boolean clear = true;
            for (int y = by - 1; y <= by + bh && clear; y++) for (int x = bx - 1; x <= bx + bw; x++) {
                if (Math.abs(x - c) <= road + 2 || Math.abs(y - c) <= road + 2 || Math.hypot(x - c, y - c) < 19) { clear = false; break; }
            }
            if (!clear) continue;
            for (int y = by; y < by + bh; y++) for (int x = bx; x < bx + bw; x++) t[y][x] = 'K';
            for (int x = bx - 1; x <= bx + bw; x++) t[by + bh][x] = 's';
        }
        // 가운데 광장(돌바닥 원) + 분수
        for (int y = c - 16; y <= c + 16; y++) for (int x = c - 16; x <= c + 16; x++) if (Math.hypot(x - c, y - c) < 16) t[y][x] = 's';
        for (int y = c - 3; y <= c + 3; y++) for (int x = c - 3; x <= c + 3; x++) if (Math.hypot(x - c, y - c) < 3.2) t[y][x] = '~';
        // 성당: 북쪽 대로 끝(성벽 안쪽)에 큰 건물, 문 앞에 신부
        int qx0 = c - 9, qx1 = c + 9, qy0 = wt + 3, qy1 = wt + 16;
        for (int y = qy0; y <= qy1; y++) for (int x = qx0; x <= qx1; x++) t[y][x] = 'Q';
        for (int y = qy1 + 1; y <= qy1 + 2; y++) for (int x = qx0 - 1; x <= qx1 + 1; x++) t[y][x] = 's';
        int px = c, py = qy1 + 1;
        t[py][px] = 'N';
        // 텔레포트 게이트: 광장 남쪽
        int gx = c, gy = c + 9;
        t[gy][gx] = 'X';
        // 성문: 남문(열림), 동문/서문(잠김) -- 북쪽은 성당이 막고 있다
        for (int y = h - wt; y < h; y++) for (int dx = -1; dx <= 1; dx++) t[y][c + dx] = 's';
        t[h - 1][c] = 'G';
        for (int x = w - wt; x < w; x++) for (int dy = -1; dy <= 1; dy++) t[c + dy][x] = 's';
        t[c][w - 1] = 'G';
        for (int x = 0; x < wt; x++) for (int dy = -1; dy <= 1; dy++) t[c + dy][x] = 's';
        t[c][0] = 'G';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(c, h - 1, 'G', STEPPE, 0, 0, "남문 -- 왕도 남쪽 초원으로"));
        portals.add(new Portal(w - 1, c, 'G', ROYALROAD, 0, 0, "동문 -- 왕도 북쪽 샛길(설산 방향)")); // [2026-10-27] 왕도 가는 길로 열림(좌표는 buildWorld)
        portals.add(new Portal(0, c, 'G', null, 0, 0, "서문 (굳게 닫혀 있다)"));
        portals.add(new Portal(gx, gy, 'X', TELE, 0, 0, "텔레포트 게이트"));
        int sx = c, sy = h - wt - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'K';
        byte[][] o = new byte[h][w]; // 광석 없음(도시)
        S5MineMap m = new S5MineMap(CITY, "왕도 아스테리아", false, w, h, sx, sy, t, o, portals);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(new Npc(px, py, "priest", "성당 신부", "여신의 가호가 함께하기를. 상처를 치료하고, 쓰러지면 이곳에서 깨어나도록 기도해 드리지요."));
        m.npcs = npcs;
        return m;
    }

    /** [2026-10-24] 바닷길(90x120, 바람의나라 용궁 느낌의 바닷속): 푸른 바다 모래(u) 위에 해초(e, 지나갈 수 있음)와 산호 무더기(O),
     *  남쪽 입구에서 북쪽 용궁까지 붉은 궁궐 길(p, 양옆에 붉은 기둥 L), 북쪽 끝 용궁 성벽(L)과 굳게 닫힌 용궁 문. 바닷속(underwater) -- 화면이 푸르게 물들이고 거품을 띄운다.
     *  "바닷길에서는 보석만" -- [2026-10-24] "캐면 내구도를 쓰고 100% 보석": 바다 모래/용궁 길 칸 전부(입구 3칸 안 제외)가 보석 칸(ore = 1),
     *  캐면 광석 대신 보석이 나온다(searoadGem). 해초 칸은 못 캔다. 부두 끝 숨은 입구로만 들어온다. */
    static S5MineMap generateSearoad(long seed) {
        int w = 90, h = 120, c = w / 2;
        Random r = new Random(seed);
        Noise weed = new Noise(seed * 31 + 81), coral = new Noise(seed * 31 + 82);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double cr = coral.fbm(x / 10.0, y / 10.0), wd = weed.fbm(x / 8.0, y / 8.0);
            t[y][x] = cr > 0.70 ? 'O' : (wd > 0.62 && r.nextDouble() < 0.55) ? 'e' : 'u';
        }
        for (int x = 0; x < w; x++) { t[0][x] = 'O'; t[1][x] = 'O'; t[h - 1][x] = 'O'; }
        for (int y = 0; y < h; y++) { t[y][0] = 'O'; t[y][w - 1] = 'O'; }
        // 용궁(북쪽): 붉은 성벽 블록, 가운데 문(잠김)
        int py0 = 3, py1 = 22;
        for (int y = py0; y <= py1; y++) for (int x = c - 22; x <= c + 22; x++) t[y][x] = 'L';
        for (int y = py1 + 1; y <= py1 + 3; y++) for (int x = c - 24; x <= c + 24; x++) t[y][x] = 'p'; // 궁 앞 마당
        t[py1][c] = 'G';
        // 궁궐 길: 남쪽 입구 -> 용궁 문(5칸 폭), 양옆 6칸마다 붉은 기둥
        for (int y = py1 + 1; y < h - 1; y++) for (int dx = -2; dx <= 2; dx++) t[y][c + dx] = 'p';
        for (int y = py1 + 6; y < h - 4; y += 6) { t[y][c - 4] = 'L'; t[y][c + 4] = 'L'; t[y][c - 3] = 'p'; t[y][c + 3] = 'p'; }
        t[h - 1][c] = 'U';
        int sx = c, sy = h - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'O';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(sx, h - 1, 'U', BEACH, 0, 0, "물 위로 올라가기"));
        portals.add(new Portal(c, py1, 'G', null, 0, 0, "용궁 (굳게 닫혀 있다)"));
        byte[][] o = new byte[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((t[y][x] == 'u' || t[y][x] == 'p') && Math.abs(x - sx) + Math.abs(y - sy) > 3) o[y][x] = 1; // 보석 칸(캐면 100% 보석)
        }
        S5MineMap m = new S5MineMap(SEAROAD, "바닷길", false, w, h, sx, sy, t, o, portals);
        m.underwater = true;
        return m;
    }

    /** [2026-10-24] 바닷길 보석(캘 때마다 하나): 아쿠아마린 35 / 재스퍼 15 / 토파즈 15 / 스피넬 15 / 에메랄드 6 / 가넷 5 / 루비 4 / 스타 사파이어 4 / 다이아몬드 1 (%). 반환 = GEM 인덱스. */
    private static final int[][] SEAROAD_GEMS = { { 3, 35 }, { 0, 15 }, { 4, 15 }, { 6, 15 }, { 7, 6 }, { 5, 5 }, { 1, 4 }, { 2, 4 }, { 8, 1 } };
    public static int searoadGem(int roll100) {
        int acc = 0;
        for (int[] g : SEAROAD_GEMS) { acc += g[1]; if (roll100 < acc) return g[0]; }
        return 3;
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
                case 's': c = 0xa9a49a; break;
                case 'W': c = 0x4a4540; break;
                case 'K': c = 0xa8443a; break;
                case 'Q': c = 0xdfe6f2; break;
                case 'X': c = 0x7a5cff; break;
                case 'u': c = 0x6f9fc0; break;
                case 'p': c = 0xb8483a; break;
                case 'e': c = 0x2f7a6a; break;
                case 'O': c = 0xe07a8a; break;
                case 'L': c = 0x8a1e1e; break;
                case 'Z': c = 0x3b7dd8; break;
                case 'n': c = 0xf2f6fa; break;
                case 'Y': c = 0x2f5d4a; break;
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
