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
    /** [2026-10-28] "광산 동굴 4층에 50~60레벨 구간" -- 3층 지하 문(보스가 지키는 자리 옆)을 열어 4층으로. 14~15단계(Lv.53~57), 광산 레벨 4(투구 3단계부터). */
    public static final String CAVE4 = "cave4";
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
    /** [2026-10-28] "개미굴(지도가 안 보이는 지역), 레벨 30~50대" + "개미굴 입구는 따로 없고 사막 서쪽을 걷다가 랜덤하게 빠진다, 마을 이동 안 됨,
     *  나가는 통로 3개를 어렵게, 탈출하면 피라미드 -- 가운데 보상 상자에서 사막의 심장" -- 사막 서쪽(DESERT_FALL_X 왼쪽)에서 걸을 때마다 확률로 개미굴 한가운데로 떨어진다
     *  (BotS5ServiceImpl.mineAntFall). 개미굴(굴 미로, 미니맵 없음) 가장자리 세 곳의 나가는 굴(U) -> 피라미드. 피라미드 동쪽 문 -> 사막 서쪽. */
    public static final String ANTNEST = "antnest", PYRAMID = "pyramid";
    /** [2026-11-04] 피라미드 지하(파라오 미라의 방, 50x50) -- 개미굴 나가는 굴 셋 중 하나로만 들어간다. 전체 지도엔 없다. */
    public static final String PYRAMID_UNDER = "pyramidb";
    /** [2026-10-28] "설산 동쪽에 설산 동굴 -- 땅파기가 안 되고, 앞에 있는 광석(얼음 광맥 I)을 직접 앞에서 채광, 20번 연속 채광하면 빙석,
     *  근처 40레벨대 강하지만 체력이 적은 박쥐류가 선공, 공격받으면 연속 채광이 풀린다, 내구도 소모 없음" -- 설산 마을 동문 -> 설산 동굴(veinMine).
     *  연속 채광은 BotS5ServiceImpl.mineVein. */
    public static final String SNOWCAVE = "snowcave";
    /** [2026-10-28] "설산 동굴 2층 -- 빙석 주변에 얼음 골렘 많이(쉽게 못 캐게), 2층에서 채집 시 가끔 세공 빙석, 어두운 효과 2층 5레벨 / 1층 4레벨" --
     *  1층 가장 먼 방 가운데 계단(S) -> 2층(130x100, 광맥 더 많이). 어두움 = 광산 레벨(투구 단계 - (레벨 - 1)만큼 보인다). */
    public static final String SNOWCAVE2 = "snowcave2";
    /** [2026-11-05] "한 마리 전투가 너무 쉽게 끝난다" -- 왕도 서문 너머 왕도 서쪽길(240x120): 체력 많은 매머드류가 무리 지어(디아블로2 소 레벨처럼) 넓게 인식하고 몰려온다.
     *  50~90레벨(14~23단계)이 왕도에서 멀어질수록 강해진다(S5MineMobs.tierAt). 다른 곳으로 가는 길은 없고, 맵 중간중간 동굴 셋(아직 막힘). */
    public static final String WESTROAD = "westroad";
    public static final String FOREST = "forestmaze"; // [2026-11-08] 마물의 숲 미궁(지금은 관리자만 -- S55MineServiceImpl.mineWarp)
    // [2026-11-15] 하피를 잡은 사람만 나루터에서 강을 건너 가는 탑의 도시 + 시즌5 규칙의 탑(tw1~tw30 -- 1~8 보드, 9 보스, 10 마을이 10층마다)
    public static final String TOWER_CITY = "towercity";
    public static final int TOWER_FLOORS = 50; // [2026-11-17] 30 -> 50(31층부터는 buildWorld 맨 뒤에서 만든다)
    static final int TOWER_FLOORS_V1 = 30;
    /** [2026-11-15] "100층까지 만들고 ... 아직 작업은 30층 아래 보스까지만" -- 계획한 꼭대기(층 목록에 준비 중으로 보인다). 열린 층은 TOWER_FLOORS. */
    public static final int TOWER_TOP = 100;
    public static String towerId(int f) { return "tw" + f; }
    /** 탑 층(탑이 아니면 0). */
    public static int towerFloor(String id) {
        if (id == null || !id.startsWith("tw") || id.length() < 3) return 0;
        try { int f = Integer.parseInt(id.endsWith("s") ? id.substring(2, id.length() - 1) : id.substring(2)); return f >= 1 && f <= TOWER_FLOORS ? f : 0; } catch (NumberFormatException e) { return 0; }
    }
    // [2026-11-15] 탑 방 방식 -- 층마다 방(tw{층})과 계단방(tw{층}s), 탑 입구 맵(TOWER_GATE: 탑의 도시 북문 밖, 거대한 탑의 정면).
    public static final String TOWER_GATE = "towergate";
    public static final String DEMON_EAST = "demoneast", DEMON_WEST = "demonwest"; // [2026-11-16] 악마의 들판(요새 도시 동문/서문 밖)
    // [2026-11-17] 로드맵 -- 들판 2·3(Lv.105/115), 최후의 방어선(Lv.125, 서쪽3·동쪽3 사이), 마계 입구(방어선 북쪽, 포털), 혼돈스러운 마계(Lv.135, 1인).
    public static final String DEMON_EAST2 = "demoneast2", DEMON_EAST3 = "demoneast3", DEMON_WEST2 = "demonwest2", DEMON_WEST3 = "demonwest3";
    public static final String LAST_LINE = "lastline", ABYSS_GATE = "abyssgate", CHAOS = "chaos";
    /** [2026-11-18] 해상도시 -- 바다 위에 지은 부두 도시, 다도해 뱃길 북쪽 끝 부두에서 배로만 간다. 지금은 관리자만(S55MineServiceImpl.mineMapBlock). */
    public static final String SEA_CITY = "seacity";
    /** [2026-11-18] 바다 위(이름 미정) -- 남쪽 바닷가 북쪽 부두 <-> 남쪽 끝 해상도시 부두, 1인용 배로만(boat). 해상전투/바다 보스 자리. 지금은 관리자만. */
    public static final String OPEN_SEA = "opensea";
    /** [2026-11-18] 망망대해 -- 남쪽 바닷가 아래 부두 끝에서 배로. 나가는 문이 없고 머무는 동안 배가 초당 2.4%씩(10초에 24%, [2026-11-19] 예전 5%) 부서지며, 10초를 버티면 남쪽 바닷가/다도해 본섬/해상도시 중 하나로 떠밀려 간다(S55MineServiceImpl.mineVastTick). 지금은 관리자만. */
    public static final String VAST_SEA = "vastsea";
    public static final String DEMON_VILLAGE = "demonvillage"; // [2026-11-17] 마계 마을 -- 마계 입구 포털로 가끔, 3초만 머문다
    /** 악마의 들판(동/서, 1~3) 단계 0~2, 최후의 방어선 3, 그 밖 -1. */
    public static int demonTier(String id) {
        if (id == null) return -1;
        if (LAST_LINE.equals(id)) return 3;
        if (!id.startsWith(DEMON_EAST) && !id.startsWith(DEMON_WEST)) return -1;
        return id.endsWith("3") ? 2 : id.endsWith("2") ? 1 : 0;
    }
    public static boolean isDemonField(String id) { int t = demonTier(id); return t >= 0 && t <= 2; }
    public static boolean isDemonWest(String id) { return id != null && id.startsWith(DEMON_WEST); }
    /** 곡괭이로 캐면 악마의 광석 조각이 함께 나오는 땅(들판 1~3 + 최후의 방어선). */
    public static boolean isDemonLand(String id) { return demonTier(id) >= 0; }
    // [2026-11-17] 새 재료 -- 황혼의 인장(서쪽 들판) / 여명의 인장(동쪽 들판): 둘을 합쳐 최후의 방어선 통행, 악마의 광석 조각(악마 땅 채광, 정제 10% 미스릴), 종말의 파편(혼돈스러운 마계)
    public static final String DUSK_SEAL_CODE = "DUSK_SEAL", DUSK_SEAL_NAME = "황혼의 인장";
    public static final String DAWN_SEAL_CODE = "DAWN_SEAL", DAWN_SEAL_NAME = "여명의 인장";
    public static final String DEMON_ORE_CODE = "DEMON_ORE_SHARD", DEMON_ORE_NAME = "악마의 광석 조각";
    public static final String DOOM_SHARD_CODE = "DOOM_SHARD", DOOM_SHARD_NAME = "종말의 파편";
    public static final int DUSK_SEAL_GRADE = 73, DAWN_SEAL_GRADE = 74, DEMON_ORE_GRADE = 75, DOOM_SHARD_GRADE = 76;
    /** [2026-11-17] 빛의 심장(77) -- 심판자 보로네오 처치 보상(반드시). */
    public static final String LIGHT_HEART_CODE = "LIGHT_HEART", LIGHT_HEART_NAME = "빛의 심장";
    public static final int LIGHT_HEART_GRADE = 77;
    /** [2026-11-18] 어둠의 심장(78) -- 타락한 대주교 처치 보상(반드시, 예전 인장 = 어둠의 증표 자리). 심판의 증표(79) -- 심판자 보로네오 50%, 있어야 마계 입구로. */
    public static final String DARK_HEART_CODE = "DARK_HEART", DARK_HEART_NAME = "어둠의 심장";
    public static final int DARK_HEART_GRADE = 78;
    public static final String JUDGE_TOKEN_CODE = "JUDGE_TOKEN", JUDGE_TOKEN_NAME = "심판의 증표";
    public static final int JUDGE_TOKEN_GRADE = 79;
    /** [2026-11-18] 정제된 마석(80) -- 마계 마을(10초 머무는 동안)에서 캘 때마다 하나. */
    public static final String MANA_STONE_CODE = "MANA_STONE", MANA_STONE_NAME = "정제된 마석";
    public static final int MANA_STONE_GRADE = 80;
    /** [2026-11-18] 물의 심장(81) -- 심해의 크라켄 처치 보상(반드시). 검기 물 속성 재료. */
    public static final String WATER_HEART_CODE = "WATER_HEART", WATER_HEART_NAME = "물의 심장";
    public static final int WATER_HEART_GRADE = 81;
    /** [2026-11-18] 내구도 회복 키트(82) -- 거룡 토벌 1·2위 보상, 왕도 워프 포인트 관리인에게 내면 워프 포인트 하나의 내구도 +20. */
    public static final String WP_KIT_CODE = "WP_KIT", WP_KIT_NAME = "거룡의 심장 조각"; // [2026-11-20] "내구도 회복 키트가 뭐야? 거룡의 심장 조각으로 이름 바꿔줘"(코드는 그대로)
    public static final int WP_KIT_GRADE = 82;
    /** [2026-11-19] 나무(83) -- 하피의 둥지 나무를 곡괭이로 찍어 쓰러뜨리면 1~2개(mineChopTree). 배 수리 재료(1개당 배 체력 5%). */
    public static final String WOOD_CODE = "WOOD", WOOD_NAME = "나무";
    public static final int WOOD_GRADE = 83;
    /** [2026-11-20] 태초의 보석(84) -- 최후의 방어선 태초의 보석 세공사가 만든다. 한 번 쓰면 검기 폭이 3칸(관리자처럼, 사람마다 한 번). */
    public static final String PRIMAL_GEM_CODE = "PRIMAL_GEM", PRIMAL_GEM_NAME = "태초의 보석";
    public static final int PRIMAL_GEM_GRADE = 84;
    public static String towerStairId(int f) { return "tw" + f + "s"; }
    public static boolean towerStairRoom(String id) { return id != null && id.endsWith("s") && towerFloor(id) > 0; }
    /** 방이 있는 층(수문장/마을 층이 아닌 층). */
    public static boolean towerRoomFloor(int f) { return f >= 1 && f <= TOWER_FLOORS && !towerBossFloor(f) && !towerTownFloor(f); }
    /** 그 층에서 계단방까지 지나야 하는 방 수(구역 안에서 위로 갈수록 길다: 5, 5, 6, 6, 7, 7, 8, 8). */
    public static int towerRooms(int f) { return 5 + ((f - 1) % 10) / 2; }
    public static boolean towerBossFloor(int f) { return f % 10 == 9; }
    public static boolean towerTownFloor(int f) { return f % 10 == 0; }
    public static final String WB_ARENA = "wbarena"; // [2026-11-15] 월드보스 전장(매일 21시, 어디서든 참여 버튼으로) -- [2026-11-22] 비어 있던 자리를 왕도 수련장(보스 허수아비)으로
    public static final String HARPY_NEST = "harpynest"; // [2026-11-08] 하피의 둥지(보스룸, 지금은 관리자만)
    // [2026-11-11] 두고개식 미궁 -- 방 FOREST_ROOM_N개(0번 = FOREST = 입구 방), 미로의 끝방, 둥지 뒤 나루터. 모두 관리자만(isForestZone).
    public static final String FOREST_END = "forestend", HARPY_FERRY = "harpyferry";
    static final int FOREST_ROOM_N = 15;
    public static final String[] FOREST_ROOM_IDS = forestRoomIds();
    private static String[] forestRoomIds() { String[] a = new String[FOREST_ROOM_N]; a[0] = FOREST; for (int i = 1; i < a.length; i++) a[i] = FOREST + i; return a; }
    /** 미궁 방(입구 방 포함). */
    public static boolean isForestRoom(String id) { return id != null && id.startsWith(FOREST); }
    /** 미궁 방 + 끝방 + 하피의 둥지 + 나루터(지금은 관리자만 -- S55MineServiceImpl.mineWarp). */
    public static boolean isForestZone(String id) { return isForestRoom(id) || FOREST_END.equals(id) || HARPY_NEST.equals(id) || HARPY_FERRY.equals(id); }
    private static List<String> withForest(List<String> base) { List<String> l = new ArrayList<>(base); for (int f = 1; f <= TOWER_FLOORS; f++) { l.add(towerId(f)); if (towerRoomFloor(f)) l.add(towerStairId(f)); } /* [2026-11-15] 탑 층 + 계단방 */ for (int i = 1; i < FOREST_ROOM_N; i++) l.add(FOREST_ROOM_IDS[i]); l.add(FOREST_END); l.add(HARPY_FERRY); return Collections.unmodifiableList(l); }
    /** 사막에서 개미굴에 빠지는 구역(이 x보다 서쪽). */
    public static final double DESERT_FALL_X = 0.3;
    /** [2026-10-28] 피라미드 보상 상자 -- 사막의 심장(기타 재료, 등급 값 39). */
    public static final String DESERT_HEART_CODE = "DESERT_HEART";
    /** [2026-10-28] "광석 전문가 NPC를 마을마다 -- 구리 광석 100개 = 동괴 1개처럼(성공 45%), 보석 세공사 NPC도 마을마다 -- 세공된 루비 같은 것(성공 25%),
     *  옵션이 좋은 장비엔 괴와 세공된 보석이 들어가게" -- 괴(등급 값 40~44 = 구리~미스릴), 세공된 보석(45~53 = GEM_CODES 순서). */
    public static final String[] BAR_CODES = { "BAR_COPPER", "BAR_IRON", "BAR_SILVER", "BAR_GOLD", "BAR_MITHRIL" };
    public static final String[] BAR_NAMES = { "동괴", "철괴", "은괴", "금괴", "미스릴괴" };
    public static final int BAR_GRADE_BASE = 40, CUT_GRADE_BASE = 45;
    // [2026-10-29] TBOT_S5_USER_ORE.ORE_CODE는 VARCHAR2(20)인데 "CUT_GEM_STAR_SAPPHIRE"가 21자라 세공 성공 때 저장이 ORA-12899로 실패(거래째 되돌려져
    //  세공된 스타 사파이어를 아무도 못 만들었고, 그걸 쓰는 탐지 곡괭이도 못 만들었다). 20자 넘는 코드만 밑줄을 빼서 줄인다(다른 세공 코드는 그대로).
    public static String cutCode(int gemIdx) { String c = "CUT_" + GEM_CODES[gemIdx]; return c.length() <= 20 ? c : "CUT_GEM_" + GEM_CODES[gemIdx].substring(4).replace("_", ""); }
    public static String cutName(int gemIdx) { return "세공된 " + GEM_NAMES[gemIdx]; }
    /** [2026-10-28] "빙석을 세공 목록에, 세공된 빙석 10개 + 다도해의 세공 보석으로 방한 갑옷/장갑 3단계" -- 세공된 빙석(54), 세공된 바다 보석(55~59). */
    public static final String CUT_ICE_CODE = "CUT_ICE_STONE", CUT_ICE_NAME = "세공된 빙석";
    public static final int CUT_ICE_GRADE = 54, SEA_CUT_GRADE_BASE = 55;
    public static String seaCutCode(int i) { return "CUT_" + SEA_GEM_CODES[i]; }
    public static String seaCutName(int i) { return "세공된 " + SEA_GEM_NAMES[i]; }
    public static final String DESERT_HEART_NAME = "사막의 심장";
    public static final int DESERT_HEART_GRADE = 39;
    /** [2026-10-29] 닌자의 검 조각(60) -- 이미 가진 닌자의 검(같은 등급 이하)이 또 나오면 자동 분해(일반 1개, 등급마다 +1 -- 신화 8개). */
    public static final String NINJA_SHARD_CODE = "NINJA_SHARD", NINJA_SHARD_NAME = "닌자의 검 조각";
    public static final int NINJA_SHARD_GRADE = 60;
    /** [2026-10-29] 무기 조각(60~63, 등급 = NINJA_SHARD_GRADE + 번호): 닌자의 검 / 비철도 / 카타나 / 백화검. */
    public static final String[] WEAPON_SHARD_CODES = { NINJA_SHARD_CODE, "BICHUL_SHARD", "KATANA_SHARD", "BAEKHWA_SHARD" };
    public static final String[] WEAPON_SHARD_NAMES = { NINJA_SHARD_NAME, "비철도 조각", "카타나 조각", "백화검 조각" };
    /** [2026-11-06] 갑옷 조각(66~69, 등급 = ARMOR_SHARD_GRADE + 번호): 사슬 갑옷 / 무사의 흉갑 / 닌자의 갑주 / 백화 갑주 -- 같은 등급이 또 나오면 분해, 강화 재료. */
    public static final String[] ARMOR_SHARD_CODES = { "ARMOR_SHARD_1", "ARMOR_SHARD_2", "ARMOR_SHARD_3", "ARMOR_SHARD_4" };
    public static final String[] ARMOR_SHARD_NAMES = { "사슬 갑옷 조각", "무사의 흉갑 조각", "닌자의 갑주 조각", "백화 갑주 조각" };
    public static final int ARMOR_SHARD_GRADE = 66;
    /** [2026-11-06] 은하수 조각(등급 값 70) -- 달빛석 1 + 칠요석 1(대장장이 합성). 음의 기운을 머금은 재료(나중에 음양석 재료), 16강부터 강화 재료. */
    public static final String GALAXY_CODE = "GALAXY_SHARD", GALAXY_NAME = "은하수 조각";
    public static final int GALAXY_GRADE = 70;
    public static final String PEAK = "snowpeak";
    /** 얼음의 심장(등급 값 33) -- 설산 봉우리 보스 보상. */
    /** [2026-10-30] 화산의 심장 -- 용암 거신을 낮에 쓰러뜨리면 확률로. */
    public static final String VOLCANO_HEART_CODE = "VOLCANO_HEART", VOLCANO_HEART_NAME = "화산의 심장";
    public static final int VOLCANO_HEART_GRADE = 64;
    /** [2026-11-05] 번개의 심장(등급 값 65) -- 마물의 숲 입구 천둥 황소 처치 보상(검기 번개 속성 재료). */
    public static final String THUNDER_HEART_CODE = "THUNDER_HEART", THUNDER_HEART_NAME = "번개의 심장";
    public static final int THUNDER_HEART_GRADE = 65;
    /** [2026-11-11] 하피 여왕 고유 증표 -- 바람의 심장(처치 시 MINE_HARPY_DROP_PCT%). */
    public static final String WIND_HEART_CODE = "WIND_HEART", WIND_HEART_NAME = "바람의 심장";
    /** [2026-11-15] 탑의 증표 -- 탑 몬스터/수문장/보물·증표 칸에서. 탑의 도시 증표 상인에게서 최종 장비(탑의 검/탑의 갑주)를 산다. */
    public static final String TOWER_TOKEN_CODE = "TOWER_TOKEN", TOWER_TOKEN_NAME = "탑의 증표";
    public static final int TOWER_TOKEN_GRADE = 72;
    public static final int WIND_HEART_GRADE = 71;
    public static final String ICE_HEART_CODE = "ICE_HEART";
    public static final String ICE_HEART_NAME = "얼음의 심장";
    public static final int ICE_HEART_GRADE = 33;
    /** [2026-10-28] "다도해 지역 바닥을 캐면 다른 보석, 마비노기 참고" -- 다도해 본섬/일곱 섬에서 캘 때만 나오는 바다 보석(등급 값 34~38).
     *  지역 보석이 안 나왔을 때 SEA_GEM_PERMILLE(천분율) 확률로 하나: 진주 40 / 산호석 25 / 자수정 20 / 오팔 10 / 흑진주 5(%). */
    public static final String[] SEA_GEM_CODES = { "PEARL", "CORAL_GEM", "AMETHYST", "OPAL", "BLACK_PEARL" };
    public static final String[] SEA_GEM_NAMES = { "진주", "산호석", "자수정", "오팔", "흑진주" };
    public static final int SEA_GEM_GRADE_BASE = 34;
    public static final int SEA_GEM_PERMILLE = 50; // [2026-11-05] 다도해 바다 보석 4% -> 5%
    private static final int[] SEA_GEM_WEIGHT = { 40, 25, 20, 10, 5 };
    /** roll = 0~99 -> 바다 보석 인덱스. */
    public static int seaGemFor(int roll) {
        for (int i = 0, acc = 0; i < SEA_GEM_WEIGHT.length; i++) { acc += SEA_GEM_WEIGHT[i]; if (roll < acc) return i; }
        return 0;
    }
    /** 바다 보석이 나오는 다도해 땅(본섬 + 일곱 섬, 신궁/뱃길 제외). */
    public static boolean isArchiLand(String id) {
        if (ARCHI.equals(id)) return true;
        for (String s : ISLE_IDS) if (s.equals(id)) return true;
        return false;
    }
    public static final String[] ISLE_NAMES = { "동백섬", "갈매기섬", "거북섬", "안개섬", "산호섬", "달맞이섬", "용암섬" };
    /** 섬마다 낮 햇빛 피해(초당 최대 체력 %), 크기. 뒤 섬일수록 덥고 크고 몬스터가 세다(S5MineMobs.tierAt). */
    static final int[] ISLE_SUN = { 3, 4, 4, 3, 5, 6, 8 }; // [2026-10-28] "기본 햇빛 피해 올려줘" -- 예전 1, 2, 2, 1, 3, 4, 5 (환생으로 경감: BotS5ServiceImpl.mineSunCut)
    static final int[] ISLE_SIZE = { 110, 120, 130, 120, 130, 140, 150 };
    /** [2026-10-28] "신궁 건물 크게, 입구 두 개(일반 방 / 보스 방), 일반은 3층까지 더 넓게" -- 신궁 1층(SHRINE 그대로) -> 2층 -> 3층(계단),
     *  닌자 두목의 방(SHRINE_BOSS, 신궁 열쇠 SHRINE_KEY가 있어야 들어감 -- 수문장 수색 임무 보상). */
    public static final String SHRINE2 = "shrine2", SHRINE3 = "shrine3", SHRINE_BOSS = "shrineboss";
    public static final List<String> SHRINE_FLOORS = Collections.unmodifiableList(Arrays.asList(SHRINE, SHRINE2, SHRINE3));
    public static final String SHRINE_KEY_ITEM = "SHRINE_KEY";
    public static boolean isShrineFloor(String id) { return SHRINE_FLOORS.contains(id); }
    public static final List<String> ARCHI_MAPS = Collections.unmodifiableList(Arrays.asList(ARCHI, SHRINE, ARCHISEA, ISLE1, ISLE2, ISLE3, ISLE4, ISLE5, ISLE6, ISLE7,
            SHRINE2, SHRINE3, SHRINE_BOSS));
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
    public static final long ARCHI_DAY_MS = 30 * 60_000L, ARCHI_CYCLE_MS = 60 * 60_000L; // [2026-10-28] 낮/밤 30분씩(예전 25/15분)
    /** [2026-11-05] "모든 필드에 밤과 낮" -- 다도해와 같은 시각(리셋 기준 60분마다 낮 30분 + 밤 30분)을 모든 바깥 맵이 쓴다. */
    public static boolean night(long ms) { return !archiDay(ms); }
    /** 지금(또는 마지막) 밤 번호 -- 낮/밤 한 바퀴마다 하나씩 오른다. */
    public static long nightIndex(long ms) { return Math.floorDiv(ms - RESET_ANCHOR_MS, ARCHI_CYCLE_MS); }
    private static final List<String> INDOOR = Arrays.asList(DUNGEON, SEAROAD, ANTNEST, PYRAMID, PYRAMID_UNDER, SNOWCAVE, SNOWCAVE2);
    /** [2026-11-05] 하늘이 보이는 바깥 맵(밤에 어두워지고, 한기 맵은 밤에 더 춥다) -- 동굴/던전/신궁/피라미드/개미굴/바닷길 제외. */
    public boolean outdoor() { return !dark && !INDOOR.contains(id) && !id.startsWith("cave") && !id.startsWith("shrine"); }
    public static boolean archiDay(long ms) {
        return Math.floorMod(ms - RESET_ANCHOR_MS, ARCHI_CYCLE_MS) < ARCHI_DAY_MS;
    }
    /** 다음에 낮/밤이 바뀌는 시각. */
    public static long archiNextChange(long ms) {
        long p = Math.floorMod(ms - RESET_ANCHOR_MS, ARCHI_CYCLE_MS);
        return ms + (p < ARCHI_DAY_MS ? ARCHI_DAY_MS - p : ARCHI_CYCLE_MS - p);
    }
    /** 지금 있는 맵 ID. 포탈 대상이 여기 없으면 잠긴 입구로 본다. */
    private static final List<String> IDS = withForest(Arrays.asList(FIELD, CAVE1, DESERT, BEACH, DUNGEON, CASTLE, ISLAND, NORTH, STEPPE, CITY, CAVE2, SEAROAD, CAVE3, SNOW1, SNOW2, SNOW3, SNOWTOWN, ROYALROAD, RUINS,
            ARCHI, SHRINE, ARCHISEA, ISLE1, ISLE2, ISLE3, ISLE4, ISLE5, ISLE6, ISLE7, SNOWROAD, PEAK, SHRINE2, SHRINE3, SHRINE_BOSS,
            ANTNEST, PYRAMID, SNOWCAVE, SNOWCAVE2, CAVE4, PYRAMID_UNDER, WESTROAD, FOREST, HARPY_NEST, WB_ARENA, TOWER_CITY, TOWER_GATE, DEMON_EAST, DEMON_WEST, DEMON_EAST2, DEMON_EAST3, DEMON_WEST2, DEMON_WEST3, LAST_LINE, ABYSS_GATE, CHAOS, DEMON_VILLAGE, SEA_CITY, OPEN_SEA, VAST_SEA)); // [2026-11-18] + 해상도시, 바다 위, 망망대해 // [2026-11-17] + 로드맵 // [2026-11-16] + 악마의 들판(동/서) // [2026-11-15] + 월드보스 전장, 탑의 도시(탑 층은 withForest에서) // [2026-11-11] + 미궁 방/끝방/나루터(withForest) // [2026-10-28] 여기 없으면 그 맵으로 가는 포탈이 잠긴 것으로 나온다(Portal.locked)
    /** [2026-10-28] 특수 재료 -- 빙석(설산에서 캘 때/설산 몬스터, 등급 값 19), 타락한 대주교 보상 인장(31). 같은 날 성유물 조각(30)은 없앴다. */
    public static final String ICE_STONE_CODE = "ICE_STONE";
    public static final String ICE_STONE_NAME = "빙석";
    public static final int ICE_STONE_GRADE = 19;
    public static final String SEAL_CODE = "ARCH_SEAL";
    public static final String SEAL_NAME = "어둠의 증표"; // [2026-11-18] "기존 증표 아이템은 어둠의 증표로" (예전 이름: 타락한 대주교의 인장) -- 대주교는 이제 어둠의 심장을 준다
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
    private static final double[] ANTNEST_SHARE = { 1, 25, 0.06, 0.18, 0.30 }; // [2026-10-28] 개미굴
    private static final double[] CAVE3_SHARE = { 3, 35, 0.08, 0.20, 0.30 };
    private static final double[] CAVE4_SHARE = { 3, 40, 0.09, 0.22, 0.30 }; // [2026-10-28] 광산 동굴 4층
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
    static final int GEM_SCALE_PCT = 100; // [2026-11-05] "전체 지역 보석 채광 확률 조금씩 올려" -- 80 -> 100(지역 보석 +25%)

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

    /** [2026-11-05] 채광 중 가끔 나오는 세공된 보석의 종류 -- 그 맵 지역 보석을 확률 비율대로(지역 보석이 없는 맵은 다이아 뺀 여덟 가지 중 하나). roll = 0 이상 아무 정수. */
    public static int cutGemFor(String mapId, int roll) {
        int[][] d = GEM_DROPS.get(mapId);
        if (d == null || d.length == 0) return roll % GEM_DIAMOND;
        int tot = 0;
        for (int[] x : d) tot += x[1];
        int k = roll % Math.max(1, tot);
        for (int[] x : d) { k -= x[1]; if (k < 0) return x[0]; }
        return d[0][0];
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
        /** [2026-10-29] 개체 단계(S5MineMobs.RANK_*): 0 하급 / 1 보통 / 2 상급 -- 같은 이름이라도 레벨·체력·공격·보상이 다르다. */
        public final int rank;
        Mob(int id, int type, int x, int y, int[] cells) { this(id, type, x, y, cells, 1); }
        Mob(int id, int type, int x, int y, int[] cells, int rank) {
            this.id = id; this.type = type; this.x = x; this.y = y; this.cells = cells; this.rank = rank;
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
    public static final List<String> GUIDE_MAPS = Collections.unmodifiableList(Arrays.asList(FIELD, SNOWTOWN, CITY, ARCHI, SNOWROAD, TOWER_CITY)); // [2026-11-16] 탑의 도시(안내원은 generateTowerCity가 부두에 세운다) // [2026-10-28] 다도해 마을(본섬)도, [2026-11-04] 설산 베이스캠프(봉우리 가는 길 맨 위)
    /** 안내원 목적지 이름(맵 이름 대신 마을 이름). */
    public static String townName(String id) {
        return FIELD.equals(id) ? "중앙 평원 마을" : SNOWTOWN.equals(id) ? "설산 마을" : CITY.equals(id) ? "왕도 아스테리아" : TOWER_CITY.equals(id) ? "요새 도시 엘리시온" : ARCHI.equals(id) ? "다도해 마을" : SNOWROAD.equals(id) ? "설산 베이스캠프" : nameOf(id);
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
    public boolean noMap() { return noMap; }
    public int mazeLeft() { return mazeLeft; } // [2026-11-11] 미궁 방 -- 미로의 끝방까지 가장 적은 걸음(통로 수), 미궁 밖은 -1
    public boolean veinMine() { return veinMine; }

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
     *  눈바람 예고(5초) 동안 걸어서 닿을 수 있게 촘촘히(어느 칸에서든 가장 가까운 모닥불까지 대체로 20칸 안팎).
     *  [2026-10-28] "설산 지역 모닥불을 절반 정도 없애줘" -- 눈길 간격 x2, 격자 18 -> 32칸(입구/정박장 모닥불 포함 개수로 약 절반). */
    static final int CAMPFIRE_GRID = 32;
    private static void campfires(char[][] t, int[] pathX, int y0, int y1, int every, List<int[]> safe) {
        int h = t.length, w = t[0].length;
        every *= 2;
        for (int y = y1 - every; y > y0 + 4; y -= every) if (pathX[y] > 0 && walkable(t[y][pathX[y]])) safe.add(new int[] { pathX[y], y, 3 });
        for (int gy = 9; gy < h - 4; gy += CAMPFIRE_GRID) for (int gx = 9; gx < w - 4; gx += CAMPFIRE_GRID) {
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
    private static boolean placeNpc(S5MineMap m, int cx, int cy, String kind, String name, String line) { return placeNpc(m, cx, cy, kind, name, line, 2); }
    /** [2026-11-22] 왕도 구역 정리 -- 그 칸부터 찾는다(자리가 막혔을 때만 둘레로). */
    private static boolean placeNpcAt(S5MineMap m, int cx, int cy, String kind, String name, String line) { return placeNpc(m, cx, cy, kind, name, line, 0); }
    private static boolean placeNpc(S5MineMap m, int cx, int cy, String kind, String name, String line, int d0) {
        for (int d = d0; d <= 12; d++) {
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

    // ---- [2026-11-18] 워프 포인트 -- "공간이동 NPC를 디아블로식 워프 포인트로, 고르는 창은 지역(액트)별 1 2 3 4 탭, 못 간 곳은 꺼져 있고 직접 가서 누른 곳만 켜진다,
    //  각 지역에 워프 포인트(설산 보스방 입구 같은 느낌, 한 맵에 여러 개도)". 3x3(아홉 칸 모두 X, 대상 TELE). 이동 안내원(guide)은 없애고 그 자리 근처에 놓는다.
    //  켜짐 = 아이템 WP{액트}의 비트(WP_DEFS 안 그 액트 순서 = 비트 -- 순서를 바꾸거나 지우지 말고 끝에 붙인다). S55MineServiceImpl.mineWaypoint / mineWarp.
    public static final class Waypoint {
        public final String id, map, name;
        public final int act, bit, x, y; // x, y = 3x3 가운데
        Waypoint(String id, String map, String name, int act, int bit, int x, int y) { this.id = id; this.map = map; this.name = name; this.act = act; this.bit = bit; this.x = x; this.y = y; }
    }
    public static final String[] WP_ACT_NAMES = { "", "시작의 땅", "설산", "사막과 다도해", "요새 도시", "바다" }; // [2026-11-18] 5 = 바다(해상도시, 지금은 관리자만)
    /** {id, 액트, 맵, 자리(g 이동 안내원 자리 / v 마을(없으면 시작점) / s 시작점 / p:맵 그 맵으로 가는 문 근처 / x 이미 있는 3x3), 이름}. */
    private static final String[][] WP_DEFS = {
        { "field", "1", FIELD, "g", "중앙 평원 마을" }, { "cave1", "1", FIELD, "p:" + CAVE1, "광산 동굴 입구" },
        { "cave3", "1", CAVE3, "s", "광산 동굴 3층" }, { "dungeon", "1", FIELD, "p:" + DUNGEON, "서쪽 동굴 입구" },
        { "beach", "1", BEACH, "s", "남쪽 바닷가" }, { "castle", "1", CASTLE, "s", "동쪽 성곽" }, { "steppe", "1", STEPPE, "s", "왕도 남쪽 초원" },
        { "city", "1", CITY, "x", "왕도 아스테리아" }, { "westroad", "1", WESTROAD, "s", "마물의 숲 입구" },
        { "north", "2", NORTH, "v", "북쪽 끝 설원" }, { "snow1", "2", SNOW1, "s", "설산 1층" }, { "snow2", "2", SNOW2, "s", "설산 2층" }, { "snow3", "2", SNOW3, "s", "설산 3층" },
        { "snowtown", "2", SNOWTOWN, "g", "설산 마을" }, { "snowcave", "2", SNOWCAVE, "s", "설산 동굴" }, { "snowroad", "2", SNOWROAD, "g", "설산 봉우리 가는 길" },
        { "peak", "2", SNOWROAD, "-", "설산 봉우리 입구" } /* [2026-11-18] "봉우리 가는 길에 워프 포인트가 둘" -- 이 자리는 놓지 않는다("-") */, { "ruins", "2", ROYALROAD, "p:" + RUINS, "성당 옛터 입구" },
        { "desert", "3", DESERT, "s", "북쪽 사막" }, { "archi", "3", ARCHI, "g", "다도해 본섬" },
        { "shrine", "3", ARCHI, "p:" + SHRINE, "신궁 입구" }, { "ninja", "3", ARCHI, "p:" + SHRINE_BOSS, "닌자 두목의 방 입구" },
        { "tcity", "4", TOWER_CITY, "g", "요새 도시 엘리시온" }, { "tgate", "4", TOWER_GATE, "s", "거대한 탑 입구" }, { "deast", "4", DEMON_EAST, "s", "동쪽 악마의 들판" },
        { "dwest", "4", DEMON_WEST, "s", "서쪽 악마의 들판" }, { "lastline", "4", LAST_LINE, "s", "최후의 방어선" },
        { "seacity", "5", SEA_CITY, "v", "바다마을 윤슬항" } };
    private static volatile List<Waypoint> waypoints = Collections.emptyList();
    public static List<Waypoint> waypoints() { if (world == null) get(FIELD); return waypoints; } // 맵을 아직 안 만들었으면 만든다
    public static Waypoint waypoint(String id) { for (Waypoint w : waypoints) if (w.id.equals(id)) return w; return null; }
    /** (x, y)가 그 맵 워프 포인트 3x3 안이면 그 워프 포인트. */
    public static Waypoint waypointAt(String mapId, int x, int y) { for (Waypoint w : waypoints) if (w.map.equals(mapId) && Math.abs(w.x - x) <= 1 && Math.abs(w.y - y) <= 1) return w; return null; }
    /** [2026-11-18] 같은 날 "워프 포인트 위치도 맵이 바뀔 때마다(주기마다) 바뀌게" -- 기준 자리에서 주기 시드로 정한 만큼(가로세로 -8~8칸) 비켜서 찾는다(왕도 게이트는 그대로). */
    private static void placeWaypoints(Map<String, S5MineMap> out, long seed) {
        List<Waypoint> l = new ArrayList<>();
        int[] bits = new int[WP_ACT_NAMES.length];
        for (String[] d : WP_DEFS) {
            int act = Integer.parseInt(d[1]), bit = bits[act]++; // 놓지 못해도 비트는 그 자리를 지킨다
            S5MineMap m = out.get(d[2]);
            if (m == null || "-".equals(d[3])) continue; // [2026-11-18] "-" = 놓지 않는 자리(비트만 지킨다)
            int[] at = null;
            if ("x".equals(d[3])) {
                Npc g0 = m.guide(); // 이동 안내원은 없앤다(왕도는 이미 있는 3x3 게이트)
                if (g0 != null) { m.terrain[g0.y][g0.x] = g0.ground != 0 ? g0.ground : '.'; List<Npc> nl0 = new ArrayList<>(m.npcs); nl0.remove(g0); m.npcs = Collections.unmodifiableList(nl0); }
                for (Portal p : m.portals) if (p.kind == 'X' && m.portalAt(p.x - 1, p.y) == null && m.portalAt(p.x, p.y - 1) == null) { at = new int[] { p.x + 1, p.y + 1 }; break; }
                if (at != null) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) m.ore[at[1] + dy][at[0] + dx] = 0; // [2026-11-18] 걸을 수 있게 된 게이트 칸 -- 캐는 칸이 아니게
            } else {
                int cx = m.spawnX, cy = m.spawnY;
                if ("g".equals(d[3]) || "v".equals(d[3])) {
                    if (m.villageX >= 0) { cx = m.villageX; cy = m.villageY; }
                    Npc g = "g".equals(d[3]) ? m.guide() : null;
                    if (g != null) { // 이동 안내원은 없애고 그 자리에
                        cx = g.x; cy = g.y;
                        m.terrain[g.y][g.x] = g.ground != 0 ? g.ground : '.';
                        List<Npc> nl = new ArrayList<>(m.npcs);
                        nl.remove(g);
                        m.npcs = Collections.unmodifiableList(nl);
                    }
                } else if (d[3].startsWith("p:")) {
                    for (Portal p : m.portals) if (d[3].substring(2).equals(p.target)) { cx = p.x; cy = p.y; break; }
                }
                Random wr = new Random(seed * 131L + act * 1009L + bit * 7919L);
                int ocx = Math.max(3, Math.min(m.w - 4, cx + wr.nextInt(17) - 8)), ocy = Math.max(3, Math.min(m.h - 5, cy + wr.nextInt(17) - 8));
                at = wpSpot(m, ocx, ocy, true);
                if (at == null) at = wpSpot(m, cx, cy, true);
                if (at == null) at = wpSpot(m, cx, cy, false);
                if (at != null) {
                    List<Portal> pl = new ArrayList<>(m.portals);
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        m.terrain[at[1] + dy][at[0] + dx] = 'X';
                        m.ore[at[1] + dy][at[0] + dx] = 0;
                        pl.add(new Portal(at[0] + dx, at[1] + dy, 'X', TELE, 0, 0, "워프 포인트 · " + d[4]));
                    }
                    m.portals = Collections.unmodifiableList(pl);
                }
            }
            if (at != null) l.add(new Waypoint(d[0], d[2], d[4], act, bit, at[0], at[1]));
        }
        waypoints = Collections.unmodifiableList(l);
    }
    /** (cx, cy)에서 가까운 3x3 자리(가운데). strict = 둘레 한 칸까지(5x5) 걸을 수 있는 땅, 문/NPC에서 떨어지게. 아니면 3x3만 땅이고 바로 아래 칸이 걸을 수 있으면. */
    private static int[] wpSpot(S5MineMap m, int cx, int cy, boolean strict) {
        int r0 = strict ? 2 : 1;
        for (int d = 0; d <= 40; d++) {
            for (int dy = -d; dy <= d; dy++) for (int dx = -d; dx <= d; dx++) {
                if (Math.abs(dx) + Math.abs(dy) != d) continue;
                int x = cx + dx, y = cy + dy;
                if (x - 2 < 1 || y - 2 < 1 || x + 2 >= m.w - 1 || y + 3 >= m.h - 1) continue;
                boolean ok = true;
                for (int yy = y - r0; yy <= y + r0 && ok; yy++) for (int xx = x - r0; xx <= x + r0; xx++) { char c = m.terrain[yy][xx]; if (!walkable(c) || c == 'P') { ok = false; break; } }
                if (ok && !walkable(m.terrain[y + 2][x])) ok = false;
                if (ok && Math.max(Math.abs(m.spawnX - x), Math.abs(m.spawnY - y)) < 4) ok = false; // 들어오는 칸(시작점)은 막지 않게
                if (ok) for (Portal pt : m.portals) if (Math.abs(pt.x - x) <= 3 && Math.abs(pt.y - y) <= 3) { ok = false; break; }
                if (ok) for (Npc n : m.npcs) if (Math.abs(n.x - x) <= 2 && Math.abs(n.y - y) <= 2) { ok = false; break; }
                if (ok) return new int[] { x, y };
            }
        }
        return null;
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
    private int mazeLeft = -1;
    private boolean noMap;                             // [2026-10-28] 개미굴 -- 지도가 안 보이는 곳(화면이 미니맵을 가린다)
    private boolean veinMine;                          // [2026-10-28] 설산 동굴 -- 땅은 못 파고 얼음 광맥(I)을 앞에서 연속 채광
    private List<int[]> chestSpots = Collections.emptyList(); // [2026-10-28] 개미굴 보물 상자 자리 {x, y, 담긴 양 0~1000}(buildWorld가 NPC로 세운다)
    private List<int[]> queenSpots = Collections.emptyList(); // [2026-10-28] 개미굴 여왕개미 자리 셋(S5MineMobs.spawn)
    private final Map<Integer, Integer> chestRich = new java.util.HashMap<>(); // [2026-10-28] 개미굴 보물 상자 칸(y * w + x) -> 담긴 양(0~1000)
    public List<int[]> queenSpots() { return queenSpots; }
    private List<int[]> dummySpots = Collections.emptyList(); // [2026-10-29] 왕도 훈련장 허수아비 자리(S5MineMobs.spawn)
    private List<int[]> trees = Collections.emptyList(); // [2026-11-08] 하피의 둥지 대형 나무(왼위 x, y -- 3x2칸 'E', 지나갈 수 없음)
    public List<int[]> trees() { return trees; }
    public List<int[]> dummySpots() { return dummySpots; }
    /** 개미굴 보물 상자에 담긴 양(0 = 보석 1개 ~ 1000 = 가장 많이). 모르는 칸이면 500. */
    public int chestRich(int x, int y) { return chestRich.getOrDefault(y * w + x, 500); }
    private List<int[]> safe = new ArrayList<>();      // [2026-10-26] 안전지대(모닥불) {x, y, 반지름}
    private List<int[]> citizens = Collections.emptyList(); // [2026-10-27] 왕도 주민이 오가는 길
    private List<Mob> mobs = Collections.emptyList();
    private int dockX = -1, dockY = -1; // [2026-10-24] 배에서 내리는 칸(항구 맵만)
    private List<int[]> board = Collections.emptyList(); // [2026-11-15] 탑 보드 칸(순서대로) {x, y, 종류 문자, 안쪽 x, 안쪽 y}
    public List<int[]> board() { return board; }
    int[][] twSpots; // [2026-11-15] 탑 방 -- 몬스터가 깨어나는 자리 셋(계단방/보스/마을은 null)
    public int[][] twSpots() { return twSpots; }
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
    private int[][] seaBarrels; // [2026-11-18] 바다 위 화약통 자리 {x, y}
    public int[][] seaBarrels() { return seaBarrels; }
    private int[][] seaRoute, lighthouses; // [2026-11-18] 바다 위 크라켄이 도는 길(가운데 칸) / 등대 탑 칸
    public int[][] seaRoute() { return seaRoute; }
    public int[][] lighthouses() { return lighthouses; }
    private boolean sail; // [2026-11-18] 남쪽 바닷가 -- 맵은 걸어 다니고, 1인용 배가 있으면 물 위로도(배 맵 boat와 달리 들어올 때 배가 필요 없다)
    public boolean sail() { return sail; }
    /** [2026-11-15] "탑은 1인 인스턴스 던전 형태" -- 탑의 보드 층과 수문장 층은 사람마다 따로(몬스터 상태가 유저별, 다른 사람이 안 보인다). 마을 층(10층마다)은 함께. */
    private int soloFlag = -1;
    public boolean solo() {
        if (soloFlag < 0) { int f = towerFloor(id); soloFlag = (f > 0 && !towerTownFloor(f)) || CHAOS.equals(id) ? 1 : 0; } // [2026-11-17] 혼돈스러운 마계도 1인
        return soloFlag == 1;
    }
    public int bossY() { return bossY; }
    public final int w, h, spawnX, spawnY;
    private final char[][] terrain;
    private final byte[][] ore;
    public List<Portal> portals; // [2026-11-18] 워프 포인트를 맵을 만든 뒤에 얹는다(placeWaypoints) -- 그래서 final이 아니다

    private S5MineMap(String id, String name, boolean dark, int w, int h, int spawnX, int spawnY, char[][] terrain, byte[][] ore, List<Portal> portals) {
        this.id = id; this.name = name; this.dark = dark; this.w = w; this.h = h; this.spawnX = spawnX; this.spawnY = spawnY;
        this.terrain = terrain; this.ore = ore; this.portals = Collections.unmodifiableList(portals);
    }

    public static boolean walkable(char c) {
        // [2026-10-24] 나무(T)도 지나갈 수 있다("나무가 너무 많아") -- 광석은 없음(assignOre). s 성 바닥 / n 눈밭 / Y 눈 덮인 소나무(T처럼 통과, 광석 없음)
        return ".,:VHcrdbPTsnYupeX".indexOf(c) >= 0; // [2026-10-24] u 바다 모래 / p 용궁 길 / e 해초(통과, 광석 없음), [2026-11-18] X 워프 포인트("지나가다 자꾸 멈춘다" -- 위를 걸어 다닌다)
    }

    public static boolean portalChar(char c) {
        return "GMUSBXZo".indexOf(c) >= 0; // X = 텔레포트 게이트(왕도), Z = 숨은 입구(물처럼 보임), [2026-11-18] o = 바다로 나가는 물가(물처럼 보임, 닿으면 배를 타고 다음 맵)
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

    private Boolean hasOre;
    /** [2026-10-28] 캘 수 있는 칸이 하나라도 있는지(없으면 화면이 캐기 버튼/자동채광을 숨긴다). */
    public boolean hasOre() {
        if (hasOre == null) {
            boolean any = false;
            for (byte[] row : ore) { for (byte b : row) if (b != 0) { any = true; break; } if (any) break; }
            hasOre = any;
        }
        return hasOre;
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
    /** [2026-10-28] 화면은 맵 정보(/api/tower-mine-map)를 버전으로 브라우저에 저장해 둔다. 지형/포탈이 그대로여도 맵 정보에 항목이 늘면
     *  (배 목적지 shipTo, 다도해, noDig/vein/noMap 등) 이 숫자를 올려야 옛 정보를 버린다 -- "운영자로 남쪽 섬에서 다도해 못 감"(배 목록에 다도해가 없는 옛 정보). */
    static final int PAYLOAD_REV = 45; // [2026-11-20] 45: 왕도 미용실, 44: 워프 포인트 관리인 대사(거룡의 심장 조각), 43: 윤슬항 -> 바다마을 윤슬항(맵 이름), 42: 왕도·요새 도시 연금술사, 41: 최후의 방어선 태초의 보석 세공사, [2026-11-19] 40: 방어구 강화사 왕도로, 39: 등대 대사, [2026-11-18] 38: 다도해 본섬/일곱 섬 내 배로, 37: // [2026-11-18] 37: 남쪽 섬/북쪽 끝 설원/다도해 본섬 배로(sail) + 맵 끝 길, 36: // [2026-11-18] 36: 연안 뱃길 없앰, 35: 연안 뱃길, 34: // [2026-11-18] 34: 윤슬항 이름, 바다 위 몬스터 2.7배, 33: 바닷가 7시/5시 끝, 좌우로 긴 바다 위 + 등대, 32: // [2026-11-18] 32: 배 맵 boat를 모든 맵에, 바다 위 맨 아래 = 망망대해, 31: // [2026-11-18] 31: 남쪽 바닷가 물가 칸(o), 30: // [2026-11-18] 30: 바다 길(물가 판자/망망대해), 봉우리 길 워프 포인트 하나, 29: // [2026-11-18] 29: 바다 위 몬스터/크라켄/화약통/신호 부표, 낚시 // // [2026-11-18] 28: 다도해 뱃길 왼쪽 아래 남쪽 바닷가 부두, 바다 위, 해상도시 워프 포인트, 27: 다도해 뱃길 북쪽 끝 해상도시 부두, 26: 요새 도시 늘 밤(alwaysNight), 워프 포인트 // // [2026-11-17] 25: 보로네오(몬스터 번호가 뒤로 하나씩) // // [2026-11-17] 24: 왕도 하피 여왕 허수아비(몬스터 번호가 뒤로 하나씩) // // [2026-11-17] 23: 마계 입구(abyss), 혼돈 몬스터(chaos/src), 악마의 땅 광석 // // [2026-11-17] 탑 층마다 몬스터 종류가 바뀌어(층별 생성) 저장해 둔 맵 정보를 다시 받게 // [2026-11-16] 21: 탑 계단방에서 아래 계단 없앰
    //  20: // [2026-11-16] 20: 탑 마을에 아래 계단
    //  19: // [2026-11-16] 19: 요새 도시 + 악마의 들판(몬스터 종류 demon)
    //  18: // [2026-11-16] 18: 탑의 도시 이동 안내원 + 동굴 3층 챔피언(몬스터 종류 champ)
    //   [2026-11-15] 17: 탑 방 방식 + 탑의 도시 재건, 16: 거룡 3x3(몬스터 종류 foot), 15: 동쪽 성곽 거룡(월드보스) + 탑 수문장 이름, 14: 탑 보드(board/tower), // [2026-11-11] 13: 광석 없는 칸(noOre -- 자동채광이 건너뛰게), [2026-11-11] 12: 미궁 남은 걸음(mazeLeft), 11: 두고개식 미궁 방/끝방/나루터, [2026-11-08] 10: 미궁 위 출구가 하피의 둥지로 열림(+ 대형 나무 trees), 9: 마물의 숲 입구 통로 셋이 미궁으로 열림(화면에 저장된 옛 "막힘" 포탈을 버리게), [2026-11-05] 8: 바깥 맵 낮밤(outdoor/dayAnchor), [2026-10-30] 보스 체력 2배 -- 화면이 저장해 둔 옛 최대 체력을 버리게, [2026-11-05] 7: 설산 마을 이동 안내원에 베이스캠프가 안 보이던 것

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
            if (GUIDE_MAPS.contains(id)) hsh = 31 * hsh + GUIDE_MAPS.hashCode(); // [2026-11-05] 이동 안내원 목적지가 늘면 안내원 있는 맵도 다시 받게(베이스캠프 추가 때 설산 마을 화면이 옛 목록을 들고 있었음)
            hsh = 31 * hsh + PAYLOAD_REV; // [2026-10-28] 맵 정보에 새 항목이 생기면 올린다(화면이 저장해 둔 옛 맵 정보를 다시 받게)
            v = version = id + "-" + w + "x" + h + "-" + Integer.toHexString(hsh);
        }
        return v;
    }

    private volatile List<int[]> noOreRuns;
    /** [2026-11-11] "땅파기 불가능한 곳은 그냥 지나치게" -- 지나갈 수 있는데 광석이 없는 칸(마을/입구 둘레/NPC 둘레 등, 화면이 지형만으로 모르는 칸)을
     *  줄마다 이어진 구간 {y, x, 길이}로. 화면 자동채광/캐기가 이 칸은 고르지 않는다. 화면이 지형으로 아는 칸(V T Y P H e)은 뺀다. */
    public List<int[]> noOreRuns() {
        List<int[]> v = noOreRuns;
        if (v == null) {
            v = new ArrayList<>();
            for (int y = 0; y < h; y++) {
                int x0 = -1;
                for (int x = 0; x <= w; x++) {
                    boolean nd = x < w && ore[y][x] == 0 && walkable(terrain[y][x]) && "VTYPHe".indexOf(terrain[y][x]) < 0;
                    if (nd && x0 < 0) x0 = x;
                    if (!nd && x0 >= 0) { v.add(new int[] { y, x0, x - x0 }); x0 = -1; }
                }
            }
            noOreRuns = v;
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
    /** 모든 맵 id -- [2026-11-03] 화면이 게임 시작 때 모든 맵을 미리 받도록(mapVers). */
    public static List<String> ids() { return IDS; }

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
            else { // [2026-10-28] 지하 문을 막지 않고 4층으로(좌표는 맨 뒤에서). 보스는 그대로 문 앞을 지킨다
                c3p.add(new Portal(p.x, p.y, 'S', CAVE4, 0, 0, "지하 4층으로 내려가는 문"));
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
        { Npc wb = wbBoardNear(field.terrain, field.spawnX + 3, field.spawnY + 2, "V"); if (wb != null) fn.add(wb); } // [2026-11-17] 월드보스 게시판
        { Npc mb = npcNear(field.terrain, field.spawnX - 3, field.spawnY + 2, "V", "mailbox", MAIL_NAME, MAIL_LINE); if (mb != null) fn.add(mb); } // [2026-11-17] 우체부

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
        S5MineMap shrine = generateShrine(seed + 40, 1);
        S5MineMap archi = generateArchiMain(seed + 41);
        S5MineMap sea = generateArchiSea(seed + 42);
        S5MineMap[] isles = new S5MineMap[ISLE_IDS.length];
        for (int i = 0; i < isles.length; i++) isles[i] = generateIsle(seed + 43 + i, i);
        // [2026-10-28] 신궁 2/3층, 닌자 두목의 방 -- 섬들 뒤에 넣는다(앞 맵들의 몬스터 자리는 그대로)
        S5MineMap shrine2 = generateShrine(seed + 50, 2), shrine3 = generateShrine(seed + 51, 3), shrineBoss = generateShrineBoss(seed + 52);
        S5MineMap ar = relink(archi, retarget(archi, SHRINE, new int[] { shrine.spawnX, shrine.spawnY }), null);
        ar = relink(ar, retarget(ar, SHRINE_BOSS, new int[] { shrineBoss.spawnX, shrineBoss.spawnY }), null);
        out.put(ARCHI, ar);
        S5MineMap sh1 = relink(shrine, retarget(shrine, ARCHI, besidePortal(ar, SHRINE)), null);
        sh1 = relink(sh1, retarget(sh1, SHRINE2, new int[] { shrine2.spawnX, shrine2.spawnY }), null);
        out.put(SHRINE, sh1);
        S5MineMap sv = relink(sea, retarget(sea, ARCHI, new int[] { ar.villageX, ar.villageY + 2 }), null);
        for (int i = 0; i < isles.length; i++) sv = relink(sv, retarget(sv, ISLE_IDS[i], new int[] { isles[i].spawnX, isles[i].spawnY }), null);
        out.put(ARCHISEA, sv);
        for (int i = 0; i < isles.length; i++) out.put(ISLE_IDS[i], relink(isles[i], retarget(isles[i], ARCHISEA, seaDockBeside(sv, ISLE_IDS[i])), null));
        // [2026-10-28] 설산 마을 북문 <-> 설산 봉우리 가는 길 <-> 설산 봉우리. 맨 뒤에 넣는다(앞 맵들의 몬스터 자리는 그대로, 설산 마을은 자리 그대로 다시 넣음).
        S5MineMap sroad = generateSnowRoad(seed + 60);
        S5MineMap peak = generatePeak(seed + 61);
        S5MineMap tw0 = out.get(SNOWTOWN);
        S5MineMap sr = relink(sroad, retarget(sroad, SNOWTOWN, besidePortal(tw0, SNOWROAD)), null);
        sr = relink(sr, retarget(sr, PEAK, new int[] { peak.spawnX, peak.spawnY }), null);
        out.put(SNOWROAD, sr);
        sr.villageX = sroad.villageX; sr.villageY = sroad.villageY;
        placeNpc(sr, sr.villageX + 4, sr.villageY + 1, "basecamp", "베이스캠프 대장", "소문 들었나? 얼음 여왕의 얼음을 깨는 방법을 누가 알아냈다더군. 꽁꽁 얼어붙어도 포기하지 말고 버둥거려 보라던데... 자네도 한번 도전해 보게나."); // [2026-11-04]
        out.put(PEAK, relink(peak, retarget(peak, SNOWROAD, besidePortal(sr, PEAK)), null));
        out.put(SNOWTOWN, relink(tw0, retarget(tw0, SNOWROAD, new int[] { sr.spawnX, sr.spawnY }), tw0.npcs));
        S5MineMap sh2 = relink(shrine2, retarget(shrine2, SHRINE, besidePortal(sh1, SHRINE2)), null);
        sh2 = relink(sh2, retarget(sh2, SHRINE3, new int[] { shrine3.spawnX, shrine3.spawnY }), null);
        out.put(SHRINE2, sh2);
        out.put(SHRINE3, relink(shrine3, retarget(shrine3, SHRINE2, besidePortal(sh2, SHRINE3)), null));
        out.put(SHRINE_BOSS, relink(shrineBoss, retarget(shrineBoss, ARCHI, besidePortal(ar, SHRINE_BOSS)), null));
        S5MineMap an = out.get(ARCHI); // 본섬 NPC -- 마을 광장(배 목수, 뱃사공), 신궁 문 앞(신궁 수문장)
        // [2026-10-28] 배 목수 대사 -- 배 값이 달빛석 + 보석 아홉 가지(BotS5ServiceImpl.MINE_BOAT_COST)로 바뀌어서
        placeNpc(an, an.villageX - 4, an.villageY - 1, "boatwright", "배 목수", "달빛석 하나에 보석 아홉 가지를 골고루 가져오면 혼자 타는 배를 뚝딱 만들어 주지. 달빛을 머금은 돌이라야 파도를 타거든.");
        placeNpc(an, an.villageX + 4, an.villageY - 1, "ferry", "뱃사공", "배가 있으면 뱃길로 데려다주지. 일곱 섬이 뱃길을 따라 늘어서 있다네. 한낮의 햇볕은 조심하게!");
        // [2026-10-28] 수문장은 두 문(신궁 1층 / 닌자 두목의 방) 사이 광장에. 보상은 비급서/달빛석 대신 닌자 두목의 방 열쇠.
        int qx = 0, qy = 0, qn = 0;
        for (Portal pt : an.portals) if (SHRINE.equals(pt.target) || SHRINE_BOSS.equals(pt.target)) { qx += pt.x; qy = pt.y; qn++; }
        if (qn > 0) placeNpc(an, qx / qn, qy + 4, "ninjaquest", "신궁 수문장", "신궁 안에 닌자들이 숨어들었소. 수색을 맡아 주시겠소? 100명을 처치하면 닌자 두목의 방 열쇠를 드리리다.");
        for (String gid : GUIDE_MAPS) if (!TOWER_CITY.equals(gid)) placeGuide(out.get(gid)); // [2026-10-25] 이동 안내원(몬스터 배치 전에 -- 그 칸엔 몬스터가 안 선다)
        // [2026-10-25] 왕도 대장장이 -- "지금보다 더 높은 업그레이드는 대도시 대장장이에서, 더 큰 폭의 아이템"
        S5MineMap ct = out.get(CITY);
        // [2026-11-22] "왕도에 NPC들이 너무 난잡하다" -- 광장에 몰려 있던 NPC를 하는 일별 구역으로 나눴다:
        //  광장 = 게시판·우체부·워프 관리인·왕도 상인 / 성당 = 신부·원소술사 / 1시 수련장 = 사범 셋 / 11시 공방 = 광석 전문가·보석 세공사·연금술사
        //  5시 장비 공방 = 대장장이·장인·무기·방어구 강화사 / 남쪽 대로 = 명품상점·미용실 / 7시 용병소. 모두 정한 칸에 바로 세운다(placeNpcAt).
        int cc = ct.w / 2;
        placeNpcAt(ct, cc + ENH_ROW[0], cc + ENH_NPC_DY, "smith", "왕도 대장장이", "왕도의 화로는 꺼지지 않지. 마을 대장간에선 못 만드는 물건을 만들어 주겠네.");
        placeNpcAt(ct, cc - 3, cc + 22, "boutique", "왕도 명품상점", "어서 오세요~ 왕도에서만 파는 반짝반짝 새 옷도 있어요. 성별도 바꿔 드려요.");
        placeNpcAt(ct, cc + 3, cc + 22, "barber", "왕도 미용실", "어서 와요~ 머리 모양도 바꾸고 원하는 색으로 물들여 드려요. 앉아 봐요!"); // [2026-11-20] 머리 모양/염색
        // [2026-10-26] 방한 외투: 설산 마을 털옷 장인(제값), 설원 정박장 암상인(세 배, 매시 정각~15분에만 -- 화면/서버가 시각으로 판단, 칸은 늘 노점)
        S5MineMap tw = out.get(SNOWTOWN);
        placeNpc(tw, tw.villageX + 3, tw.villageY + 1, "furrier", "털옷 장인", "설산 바람은 뼈까지 파고들지. 내 외투면 버틸 만할 거요. 대신 몸이 둔해져서 몬스터한테는 더 아프게 맞을 거요.");
        S5MineMap nm = out.get(NORTH);
        placeNpc(nm, nm.dockX(), nm.dockY() - 3, "blackmarket", "암상인", "쉿... 아무 데서나 못 구하는 물건이지. 살 거면 서두르게. 15분이 되면 난 사라진다네.");
        for (Npc n : nm.npcs) if ("blackmarket".equals(n.kind)) nm.safe.add(new int[] { n.x, n.y, 3 }); // 노점 앞도 안전지대
        // [2026-10-28] 광석 전문가(광석 100개 -> 괴, 45%) / 보석 세공사(보석 5개 -> 세공된 보석, 25%) -- 마을마다(평원 마을, 설산 마을, 왕도, 다도해 마을).
        //  장인(최대 내구도 1000 이상에서 세공된 보석 하나 -> 최대 내구도 +30) -- 왕도.
        String smLine = "광석 백 개를 녹이면 괴 하나가 나오지. 다만 불이 까다로워서 반은 날아가네.";
        String gcLine = "보석 다섯 알을 깎아 하나를 빛나게 만들지. 네 번에 한 번 꼴로만 성공한다네.";
        S5MineMap fv = out.get(FIELD);
        placeNpc(fv, fv.spawnX + 5, fv.spawnY + 3, "smelter", "광석 전문가", smLine);
        placeNpc(fv, fv.spawnX - 5, fv.spawnY + 3, "gemcutter", "보석 세공사", gcLine);
        S5MineMap tw3 = out.get(SNOWTOWN);
        placeNpc(tw3, tw3.villageX - 3, tw3.villageY + 4, "smelter", "광석 전문가", smLine);
        placeNpc(tw3, tw3.villageX + 3, tw3.villageY + 5, "gemcutter", "보석 세공사", gcLine);
        placeNpcAt(ct, cc + CRAFT_ROW[0], cc + CRAFT_NPC_DY, "smelter", "광석 전문가", smLine); // [2026-11-22] 11시 공방
        placeNpcAt(ct, cc + CRAFT_ROW[1], cc + CRAFT_NPC_DY, "gemcutter", "보석 세공사", gcLine);
        placeNpcAt(ct, cc + CRAFT_ROW[2], cc + CRAFT_NPC_DY, "alchemist", "연금술사", ALCHEMIST_LINE); // [2026-11-20]
        placeNpcAt(ct, cc + 5, cc + 10, "citymerchant", "왕도 상인", "금괴 한 덩이면 행운의 주문서를 써 드리지. 10분 동안은 뭘 잡아도 배로 남는 장사라네!"); // [2026-11-05] 금괴 반복 소모처
        placeNpcAt(ct, cc + ENH_ROW[1], cc + ENH_NPC_DY, "artisan", "왕도 장인", /* [2026-11-22] 5시 장비 공방 */ "곡괭이가 천 번을 버틴다고? 거기에 세공한 보석을 박아 넣으면 서른 번은 더 버티게 해 주지.");
        placeNpcAt(ct, cc + ENH_ROW[2], cc + ENH_NPC_DY, "enhancer", "무기 강화사", "무기 칸을 맡기게. 빗나가도 원망은 말고.");
        placeNpcAt(ct, cc + ENH_ROW[3], cc + ENH_NPC_DY, "armorer", "방어구 강화사", "갑옷 칸을 맡기게. 두들길수록 단단해지고 몸도 버텨 내지. 다만 망치가 빗나가면 재료는 돌려주지 않네."); // [2026-11-19] "방어구 강화 NPC를 무기 강화 옆에 두고 일반 유저 오픈" -- 요새 도시(하피 처치 후에만 갈 수 있음)에서 옮김 // [2026-11-06] "말이 너무 많다" -- 짧게 // [2026-11-06] 왕도 5시 방향
        placeNpcAt(ct, cc + MERC_NPC_DX, cc + MERC_NPC_DY, "mercenary", "용병소 대장", "혼자 다니기엔 광산이 험하지. 쓸 만한 놈을 붙여 주겠네. 지치면 뒤에서 숨 좀 돌리게 해 주게나."); // [2026-11-05] 왕도 용병소
        S5MineMap av = out.get(ARCHI);
        placeNpc(av, av.villageX - 10, av.villageY - 5, "smelter", "광석 전문가", smLine);
        placeNpc(av, av.villageX + 10, av.villageY - 5, "gemcutter", "보석 세공사", gcLine);
        placeNpc(av, av.villageX, av.villageY - 8, "archishop", "다도해 상인", "햇볕이 뜨겁지? 이 양산만 있으면 한낮도 끄떡없다네. 바다 보석으로 값을 치르게."); // [2026-10-28]
        { // [2026-11-04] 검기 사범 -- 보스 드랍템 + 달빛석으로 검기 배우기(S55MineServiceImpl.mineAuraLearn). [2026-11-22] 성당 옆 -> 1시 수련관 앞마당(문 왼쪽 한 줄)
            placeNpcAt(ct, cc + TRAIN_ROW[0], cc + TRAIN_NPC_DY, "swordmaster", "검기 사범", "칼끝에 원소를 실어 앞으로 날리는 검기를 가르치지. 그 원소를 다스리는 보스의 정수, 달빛석, 세공한 바다 보석을 가져오면 한 단계씩 -- 단계가 오를수록 멀리, 세게 나가네.");
            // [2026-11-07] "3번째 환생부터는 검기 선생 옆에 다른 선생 두 명(주변 공격, 필살 공격)" -- 돌개베기 / 만검진·일섬참(S55MineServiceImpl.mineWhirlLearn, mineUltLearn)
            placeNpcAt(ct, cc + TRAIN_ROW[1], cc + TRAIN_NPC_DY, "whirlmaster", "돌개 사범", "몸을 한 바퀴 돌려 둘레를 한꺼번에 베는 돌개베기를 가르치지. 세 번째 환생을 마친 자에게만, 검기와 같은 재료로.");
            placeNpcAt(ct, cc + TRAIN_ROW[2], cc + TRAIN_NPC_DY, "ultmaster", "필살 사범", "검을 비처럼 내리는 만검진, 한 번에 끝내는 일섬참. 세 번째 환생을 마친 자라면 은하수 조각으로 가르쳐 주지. 둘은 한 호흡을 나눠 쓰네.");
        }
        ct.citizens = cityCitizens(ct, seed); // [2026-10-27] 왕도 주민(NPC를 다 세운 뒤 -- 'N' 칸은 피해 간다)
        // [2026-10-28] "요일 동굴 앞에 NPC -- 오늘의 요일석 20개를 내면 골라서 다른 요일 동굴에" -- 평원 서쪽 통로(요일 던전 입구) 안쪽
        S5MineMap fd = out.get(FIELD);
        int[] wg = besidePortal(fd, DUNGEON);
        for (int off = 3; off <= 33; off += 10) // 통로 둘레가 좁으면 안쪽(동쪽)으로 더 들어가 자리를 찾는다
            if (placeNpc(fd, wg[0] + off, wg[1], "daykeeper", "요일 동굴 문지기", "오늘의 요일석 20개를 내면 다른 요일의 동굴 문을 열어 주지. 한 번 열면 오늘 하루는 그대로라네.")) break;
        // [2026-10-28] 개미굴(사막 서쪽에서 빠짐) -> 나가는 굴 셋 -> 피라미드 -> 동쪽 문 -> 사막 서쪽. 맨 뒤에 넣는다(앞 맵들의 몬스터 자리는 그대로).
        boolean antV2 = (seed - BASE_SEED) / 7919L >= ANT_V2_CYCLE;
        S5MineMap antnest = generateAntNest(seed + 71, (seed - BASE_SEED) / 7919L >= ANT_BIG_CYCLE, antV2), pyramid = generatePyramid(seed + 72);
        S5MineMap pyb = generatePyramidUnder(seed + 79); // [2026-11-04] 피라미드 지하 -- 만들기만 여기서, 목록엔 맨 뒤에(앞 맵들의 몬스터 자리 그대로)
        S5MineMap ds0 = out.get(DESERT);
        { // [2026-11-04] 사막 서쪽 방벽 문 경비병(문 동쪽 -- "너무 저렙은 위험하니 가지 말라고 안내")
            int awx = antWallX(ds0.w), agy = -1;
            for (int y = 1; y < ds0.h - 1 && agy < 0; y++) if (ds0.terrain[y][awx] != 'W' && ds0.terrain[y - 1][awx] == 'W') agy = y + 1;
            if (agy > 0) placeNpc(ds0, awx + 3, agy, "wallguard", "사막 경비병", "이전에 챙석봉이라는 유저가 사막에 빠져서 사라졌다네.. 그는 초보 모험가였지... 그 이후 방벽이 세워졌다네..");
        }
        int[] dw = { 2, ds0.h / 2 };
        for (int x = 2; x < ds0.w / 3; x++) if (ds0.walkable(x, ds0.h / 2)) { dw = new int[] { x, ds0.h / 2 }; break; } // 사막 서쪽 가운데 걸을 수 있는 칸
        S5MineMap an1 = relink(antnest, retarget(antnest, PYRAMID, new int[] { pyramid.spawnX, pyramid.spawnY }), null);
        S5MineMap an2 = relink(an1, retarget(an1, PYRAMID_UNDER, new int[] { pyb.spawnX, pyb.spawnY }), null);
        S5MineMap pybL = relink(pyb, retarget(pyb, ANTNEST, antV2 ? besidePortal(an2, PYRAMID_UNDER) : new int[] { an2.spawnX, an2.spawnY }), null);
        for (int[] c : an2.chestSpots) // [2026-10-28] 상자마다 담긴 양(c[2] = 0~1000) -- 세운 칸으로 찾는다(자리가 막혀 옆에 서면 칸이 달라서)
            if (placeNpc(an2, c[0], c[1], "antchest", "보물 상자", "흙더미 속에 반쯤 묻힌 보물 상자. 상자마다 담긴 양이 다르다. 개미굴이 바뀌기 전까지 열 개까지 열 수 있다." /* [2026-10-29] 설명이 예전 규칙(세 개)이었다 */)) {
                Npc pn = an2.npcs.get(an2.npcs.size() - 1);
                an2.chestRich.put(pn.y * an2.w + pn.x, c[2]);
            }
        out.put(ANTNEST, an2);
        S5MineMap py = relink(pyramid, retarget(pyramid, DESERT, dw), null);
        placeNpc(py, py.w / 2, py.h / 2 - 4, "chest", "보상 상자", "모래에 반쯤 묻힌 낡은 상자. 사람마다 평생 단 한 번만 열린다는 전설이 있다." /* [2026-10-29] 개미굴을 안 거쳐도 오게 바뀐 뒤 설명이 남아 있었다 */);
        out.put(PYRAMID, py);
        // [2026-10-28] 설산 마을 동문 <-> 설산 동굴(맨 뒤 -- 앞 맵들의 몬스터 자리는 그대로, 설산 마을은 NPC 그대로 다시 넣음)
        S5MineMap snowCave = generateSnowCave(seed + 73, 1), stw = out.get(SNOWTOWN);
        out.put(SNOWCAVE, relink(snowCave, retarget(snowCave, SNOWTOWN, besidePortal(stw, SNOWCAVE)), null));
        out.put(SNOWTOWN, relink(stw, retarget(stw, SNOWCAVE, new int[] { snowCave.spawnX, snowCave.spawnY }), stw.npcs));
        // [2026-10-28] 광산 동굴 3층 지하 문 <-> 4층(맨 뒤). 4층의 지하 문은 막는다(마지막 층). 3층으로 올라오면 문 옆(보스 자리가 아닌 칸)에.
        S5MineMap cave4 = generateCave(seed + 75, CAVE4, "광산 동굴 4층", CAVE4_SHARE), cv3o = out.get(CAVE3);
        int[] up3 = { cv3o.spawnX, cv3o.spawnY };
        for (Portal p : cv3o.portals) if (CAVE4.equals(p.target))
            for (int d = 1, found = 0; d <= 4 && found == 0; d++) for (int dy = -d; dy <= d && found == 0; dy++) for (int dx = -d; dx <= d; dx++) { // 문 둘레(문 앞은 보스 자리라 그 옆으로)
                int nx = p.x + dx, ny = p.y + dy;
                if (Math.abs(dx) + Math.abs(dy) != d || !cv3o.walkable(nx, ny) || (nx == cv3o.bossX && ny == cv3o.bossY)) continue;
                up3 = new int[] { nx, ny }; found = 1; break;
            }
        List<Portal> c4p = new ArrayList<>();
        for (Portal p : cave4.portals) {
            if (p.kind == 'U') c4p.add(new Portal(p.x, p.y, 'U', CAVE3, up3[0], up3[1], "광산 동굴 3층으로 올라가기"));
            else cave4.terrain[p.y][p.x] = '#';
        }
        S5MineMap cv4 = relink(cave4, c4p, null);
        cv4.level = 4; // 광산 레벨 4 -- 투구 3단계 이상, 3층보다 한 단계 더 어둡다
        out.put(CAVE4, cv4);
        out.put(CAVE3, relink(cv3o, retarget(cv3o, CAVE4, new int[] { cave4.spawnX, cave4.spawnY }), null));
        // [2026-10-28] 설산 동굴 1층 계단 <-> 2층(맨 뒤 -- 앞 맵들의 몬스터 자리는 그대로)
        S5MineMap snowCave2 = generateSnowCave(seed + 74, 2), sc1 = out.get(SNOWCAVE);
        out.put(SNOWCAVE2, relink(snowCave2, retarget(snowCave2, SNOWCAVE, besidePortal(sc1, SNOWCAVE2)), null));
        out.put(SNOWCAVE, relink(sc1, retarget(sc1, SNOWCAVE2, new int[] { snowCave2.spawnX, snowCave2.spawnY }), null));
        out.put(PYRAMID_UNDER, pybL); // [2026-11-04] 맨 뒤
        { // [2026-11-05] 왕도 서문 <-> 왕도 서쪽길(맨 뒤 -- 앞 맵들의 몬스터 자리는 그대로). 왕도는 NPC/주민 그대로 다시 넣는다.
            S5MineMap wr0 = generateWestRoad(seed + 80), ctw = out.get(CITY);
            S5MineMap wr = relink(wr0, retarget(wr0, CITY, besidePortal(ctw, WESTROAD)), null);
            // [2026-11-08] "마물의 숲 입구의 통로 3곳이 미궁으로" -- [2026-11-11] 셋 다 같은 미궁의 입구 방(FOREST) 아래쪽으로. 입구 방 남쪽 통로는 가운데 동굴 앞으로.
            List<Portal> caves = new ArrayList<>();
            for (Portal p : wr.portals) if (p.target == null && p.kind == 'M') caves.add(p);
            caves.sort((p, q) -> Integer.compare(p.x, q.x));
            Portal midCave = caves.get(caves.size() / 2);
            List<Portal> fwp = new ArrayList<>();
            for (Portal p : wr.portals) fwp.add(caves.contains(p) ? new Portal(p.x, p.y, p.kind, FOREST, ROOM_W / 2, ROOM_W - 2, "숲 던전 입구 -- 마물의 숲 미궁") : p);
            wr = relink(wr, fwp, null);
            List<S5MineMap> fws = forestWorld(midCave.x, midCave.y + 1);
            placeNpc(wr, wr.spawnX - 5, wr.spawnY - 2, "forestguard", "숲 입구 파수꾼", "멈추게! 요즘 숲에서 마물이 미친 듯이 불어나고 있네. 하나를 베면 둘이 달려들고, 쓰러뜨려도 금세 다시 우르르 몰려나오지... 안쪽으로 갈수록 산만 한 놈들뿐일세. 각오가 없다면 돌아가게."); // [2026-11-05]
            S5MineMap ctn = relink(ctw, retarget(ctw, WESTROAD, new int[] { wr.spawnX, wr.spawnY }), ctw.npcs);
            ctn.citizens = ctw.citizens;
            out.put(CITY, ctn);
            out.put(WESTROAD, wr);
            for (S5MineMap fm : fws) out.put(fm.id, fm); // [2026-11-08] 맨 뒤(앞 맵들의 몬스터 배치 시드가 안 바뀌게) -- [2026-11-11] 미궁 방들, 끝방, 둥지, 나루터
        }
        out.put(WB_ARENA, generateWbArena(out.get(CITY))); // [2026-11-15] 월드보스 전장(맨 뒤) -- [2026-11-22] 왕도 수련장(보스 허수아비)으로 바꿔 씀
        { // [2026-11-15] 나루터 뱃사공(강 건너 탑의 도시) + 탑의 도시 + 탑 층(맨 뒤)
            S5MineMap fr = out.get(HARPY_FERRY);
            List<Npc> frn = new ArrayList<>();
            for (Npc n : fr.npcs) frn.add("forestguard".equals(n.kind) ? withKind(n, "riverferry", "나루터 뱃사공", "하피 여왕을 쓰러뜨렸다고? 그렇다면 바람도 잦아들었겠군. 강 건너 요새 도시까지 태워 주지!") : n);
            fr.npcs = Collections.unmodifiableList(frn);
            out.put(TOWER_CITY, generateTowerCity());
            // [2026-11-19] 방어구 강화사는 요새 도시에서 왕도 무기 강화사 옆으로 옮김(일반 유저 오픈) // [2026-11-18] 방어구 강화
            for (int f = 1; f <= TOWER_FLOORS_V1; f++) out.put(towerId(f), generateTowerFloor(f));
            out.put(TOWER_GATE, generateTowerGate()); // [2026-11-15] 탑 입구(맨 뒤)
            for (int f = 1; f <= TOWER_FLOORS_V1; f++) if (towerRoomFloor(f)) out.put(towerStairId(f), towerRoom(f, true)); // 계단방(맨 뒤)
            out.put(DEMON_EAST, generateDemonField(true, 0)); // [2026-11-16] 악마의 들판(맨 뒤)
            out.put(DEMON_WEST, generateDemonField(false, 0));
            for (int f = TOWER_FLOORS_V1 + 1; f <= TOWER_FLOORS; f++) { out.put(towerId(f), generateTowerFloor(f)); if (towerRoomFloor(f)) out.put(towerStairId(f), towerRoom(f, true)); } // [2026-11-17] 31~50층(맨 뒤)
            out.put(DEMON_WEST2, generateDemonField(false, 1)); // [2026-11-17] 로드맵(맨 뒤)
            out.put(DEMON_WEST3, generateDemonField(false, 2));
            out.put(DEMON_EAST2, generateDemonField(true, 1));
            out.put(DEMON_EAST3, generateDemonField(true, 2));
            out.put(LAST_LINE, generateLastLine());
            out.put(ABYSS_GATE, generateAbyssGate());
            out.put(CHAOS, generateChaos());
            out.put(DEMON_VILLAGE, generateDemonVillage()); // [2026-11-17] 마계 마을(맨 뒤)
            { // [2026-11-18] 바다 길(맨 뒤) -- "배 타는 NPC는 없애고 바다로 들어가면 자동으로 배를 탄다(1인용 배가 있어야)".
                //  남쪽 바닷가: 왼쪽 물가 판자 = 바다 위, 오른쪽 물가 판자 = 다도해 뱃길, 아래로 뻗은 부두 끝 = 망망대해.
                //  해상도시 동쪽 부두 -> 바다 위 서쪽 부두, 바다 위 동쪽 부두 -> 남쪽 바닷가(왼쪽 물가). 바다 위/해상도시/망망대해는 지금 관리자만(mineMapBlock).
                S5MineMap sc = generateSeaCity(), os = generateOpenSea(), vs = generateVastSea(), sv0 = out.get(ARCHISEA), bch = out.get(BEACH);
                // [2026-11-18] 같은 날 다시 -- "해변가 바다 접점이 다른 맵으로 이동되고 있어. 7시 방향 끝부분만 이동되어야 하고 망망대해로"(시안 승인), "5시 방향이 다도해" --
                //  물가 이동 칸(o)을 모두 없애고 바닷가 바다를 배로 다니는 바다(sail -- 1인용 배가 있으면 물 위로, 지금은 관리자만 -- 화면)로.
                //  7시 끝(왼쪽 끝 아래 BEACH_GATE칸 + 맨 아래 끝 왼쪽) = 망망대해, 5시 끝(오른쪽 끝 아래 + 맨 아래 끝 오른쪽) = 다도해 뱃길.
                //  바다 위 오른쪽 끝 -> 7시 끝 조금 안(beachIn7, 배를 타고 나온다), 다도해 뱃길 -> 5시 끝 조금 안(beachIn5).
                List<Portal> bps = new ArrayList<>(bch.portals);
                for (int k = 0; k < BEACH_GATE; k++) {
                    int[][] cs = { { 0, bch.h - 2 - k }, { 1 + k, bch.h - 1 }, { bch.w - 1, bch.h - 2 - k }, { bch.w - 2 - k, bch.h - 1 } };
                    for (int q = 0; q < cs.length; q++) {
                        int gx = cs[q][0], gy = cs[q][1];
                        if (bch.terrain[gy][gx] != '~') continue;
                        bch.terrain[gy][gx] = 'o';
                        bps.add(q < 2 ? new Portal(gx, gy, 'o', VAST_SEA, 40, 40, "망망대해로") : new Portal(gx, gy, 'o', ARCHISEA, 0, 0, "다도해 뱃길로"));
                    }
                }
                { int gx = 0, gy = bch.h - 1; if (bch.terrain[gy][gx] == '~') { bch.terrain[gy][gx] = 'o'; bps.add(new Portal(gx, gy, 'o', VAST_SEA, 40, 40, "망망대해로")); } } // 맨 왼쪽 아래 모서리
                { int gx = bch.w - 1, gy = bch.h - 1; if (bch.terrain[gy][gx] == '~') { bch.terrain[gy][gx] = 'o'; bps.add(new Portal(gx, gy, 'o', ARCHISEA, 0, 0, "다도해 뱃길로")); } }
                int[] beachIn7 = { 9, bch.h - 9 }, beachIn5 = { bch.w - 10, bch.h - 9 };
                S5MineMap b3 = relink(bch, bps, bch.npcs);
                b3.sail = true;
                b3 = relink(b3, retarget(b3, ARCHISEA, landBeside(sv0, BEACH)), b3.npcs);
                placeNpc(b3, b3.dockX - 5, b3.dockY - 3, "boatwright", "배 목수", "달빛석 하나에 보석 아홉 가지면 혼자 타는 배를 뚝딱 만들어 주지. 부서진 배도 고쳐 준다네."); // [2026-11-18] "남쪽 바닷가 목수 추가"
                out.put(BEACH, b3);
                { // [2026-11-18] 왕도 워프 포인트 관리인 -- 내구도 회복 키트로 워프 포인트 내구도를 채운다
                    S5MineMap cty2 = out.get(CITY);
                    for (Portal p : cty2.portals) if (p.kind == 'X') { placeNpcAt(cty2, cty2.w / 2 + 5, cty2.w / 2 + 6, /* [2026-11-22] 광장 워프 오른쪽 위 */ "wpkeeper", "워프 포인트 관리인", "워프 포인트는 월드보스 거룡의 심장으로 만들었다네. 드래곤의 심장은 무한에 가깝지만 충전이 필요하지. 거룡을 쓰러뜨려 얻은 심장 조각을 가져오게."); break; }
                }
                out.put(ARCHISEA, relink(sv0, retarget(sv0, BEACH, beachIn5), null));
                os = relink(os, retarget(os, BEACH, beachIn7), null);
                os = relink(os, retarget(os, SEA_CITY, new int[] { 109, 34 }), null);
                sc = relink(sc, retarget(sc, OPEN_SEA, landBeside(os, SEA_CITY)), null);
                out.put(VAST_SEA, vs);
                { // [2026-11-18] "기존에 있던 길을 내 배로도 갈 수 있게"(시안 승인) -- 큰 배(판자 B)로 오가던 세 길을 내 배로 저어 맵 끝에 닿으면 넘어간다:
                    //  남쪽 바닷가 왼쪽 끝 <-> 남쪽 섬 위쪽 끝, 바닷가 오른쪽 끝 <-> 북쪽 끝 설원 아래쪽 끝, 남쪽 섬 오른쪽 끝 <-> 다도해 본섬 왼쪽 끝.
                    //  세 맵도 바닷가처럼 물 위를 배로(sail). 넘어가면 그 맵 끝 조금 안쪽 물 위에서 배를 탄 채 나온다. 큰 배는 그대로.
                    S5MineMap bc = out.get(BEACH), il = out.get(ISLAND), no = out.get(NORTH), rA = out.get(ARCHI);
                    int bL = firstWater(bc, 0), bR = firstWater(bc, bc.w - 1);
                    int[] inBL = waterNear(bc, 3, bL + 8), inBR = waterNear(bc, bc.w - 4, bR + 8), inIT = waterNear(il, il.w / 2, 3), inIR = waterNear(il, il.w - 4, il.h / 2),
                            inNB = waterNear(no, no.w / 3, no.h - 4), inAL = waterNear(rA, 4, rA.h / 2);
                    List<Portal> rBp = new ArrayList<>(bc.portals), rIp = new ArrayList<>(il.portals), rNp = new ArrayList<>(no.portals), rAp = new ArrayList<>(rA.portals);
                    edgeGate(bc, rBp, true, 0, bL, bc.h - BEACH_GATE - 2, ISLAND, inIT, "남쪽 섬으로");
                    edgeGate(bc, rBp, true, bc.w - 1, bR, bc.h - BEACH_GATE - 2, NORTH, inNB, "북쪽 끝 설원으로");
                    edgeGate(il, rIp, false, 0, 1, il.w - 2, BEACH, inBL, "남쪽 바닷가로");
                    edgeGate(il, rIp, true, il.w - 1, 1, il.h - 2, ARCHI, inAL, "다도해 본섬으로");
                    edgeGate(no, rNp, false, no.h - 1, 1, no.w - 2, BEACH, inBR, "남쪽 바닷가로");
                    edgeGate(rA, rAp, true, 0, 1, rA.h - 2, ISLAND, inIR, "남쪽 섬으로");
                    S5MineMap il2 = relink(il, rIp, il.npcs), no2 = relink(no, rNp, no.npcs), ar2 = relink(rA, rAp, rA.npcs);
                    il2.sail = true; no2.sail = true; ar2.sail = true;
                    out.put(BEACH, relink(bc, rBp, bc.npcs)); out.put(ISLAND, il2); out.put(NORTH, no2); out.put(ARCHI, ar2);
                }
                { // [2026-11-18] 다도해도 내 배로(시안 승인) -- 본섬 위쪽 끝 <-> 다도해 뱃길 아래쪽 끝(뱃사공 없이도), 일곱 섬은 뱃길을 바라보는 섬 끝 <-> 그 섬 부두 옆 뱃길 가장자리(부두 위아래 7칸).
                    //  일곱 섬도 물 위를 배로(sail). 부두 판자(B)/뱃사공은 그대로, 섬끼리 바로 잇는 길은 없다(뱃길을 따라).
                    S5MineMap sV = out.get(ARCHISEA), aN = out.get(ARCHI);
                    List<Portal> sVp = new ArrayList<>(sV.portals), aNp = new ArrayList<>(aN.portals);
                    int[] inVB = waterNear(sV, 22, sV.h - 6), inAT = waterNear(aN, aN.w / 2, 3);
                    edgeGate(aN, aNp, false, 0, 1, aN.w - 2, ARCHISEA, inVB, "다도해 뱃길로");
                    edgeGate(sV, sVp, false, sV.h - 1, 1, sV.w - 2, ARCHI, inAT, "다도해 본섬으로");
                    for (String iid : ISLE_IDS) {
                        Portal dK = null;
                        for (Portal p : sV.portals) if (p.kind == 'B' && iid.equals(p.target)) dK = p;
                        if (dK == null) continue;
                        S5MineMap iM = out.get(iid);
                        boolean wS = dK.x < sV.w / 2; // 뱃길 서쪽 부두 = 섬이 서쪽(섬의 오른쪽 끝이 뱃길 쪽)
                        List<Portal> iSp = new ArrayList<>(iM.portals);
                        int[] inV = waterNear(sV, wS ? 3 : sV.w - 4, dK.y), inI = waterNear(iM, wS ? iM.w - 4 : 3, iM.h / 2);
                        edgeGate(iM, iSp, true, wS ? iM.w - 1 : 0, 1, iM.h - 2, ARCHISEA, inV, "다도해 뱃길로");
                        edgeGate(sV, sVp, true, wS ? 0 : sV.w - 1, Math.max(1, dK.y - 7), Math.min(sV.h - 2, dK.y + 7), iid, inI, iM.name + "(으)로");
                        S5MineMap iM2 = relink(iM, iSp, iM.npcs);
                        iM2.sail = true;
                        out.put(iid, iM2);
                    }
                    out.put(ARCHISEA, relink(sV, sVp, sV.npcs));
                    out.put(ARCHI, relink(aN, aNp, aN.npcs));
                }
                placeNpc(sc, 55, 20, "seamayor", "바다마을 윤슬항 시장", "바다마을 윤슬항에 온 걸 환영하오! 윤슬은 햇살에 반짝이는 잔물결이라오. 배 없이는 못 오는 곳이라 손님이 귀하다오.");
                placeNpc(sc, 46, 40, "smelter", "광석 전문가", "바닷바람에 식힌 쇠가 제일 단단하다네.");
                placeNpc(sc, 66, 40, "gemcutter", "보석 세공사", "파도 소리를 들으며 깎으면 손이 덜 떨려요.");
                out.put(SEA_CITY, sc);
                { // [2026-11-18] 등대 여섯(크라켄이 도는 길 옆 바위섬 -- 배를 대면 내리고, 종을 치면 해상도시 구조선이 온다). 탑 칸은 NPC(N), 섬 둘레는 자갈(r)
                    List<Npc> bn = new ArrayList<>();
                    for (int[] l : os.lighthouses) {
                        Npc g = new Npc(l[0], l[1], "lighthouse", "등대", "등대에 불이 켜졌을 때 종을 치면 바다마을 윤슬항 구조선이 달려와 크라켄과 싸워 준다네."); // [2026-11-19] 불이 켜졌을 때만
                        g.ground = 'r';
                        os.terrain[l[1]][l[0]] = 'N';
                        bn.add(g);
                    }
                    os.npcs = Collections.unmodifiableList(bn);
                }
                out.put(OPEN_SEA, os);
            }
        }
        placeWaypoints(out, seed); // [2026-11-18] 워프 포인트(몬스터 배치 전에 -- 그 칸엔 몬스터가 안 선다), 이동 안내원은 워프 포인트로 바뀐다
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
        n.seaBarrels = m.seaBarrels; // [2026-11-18]
        n.seaRoute = m.seaRoute; n.lighthouses = m.lighthouses; n.sail = m.sail; // [2026-11-18]
        n.villageX = m.villageX;
        n.villageY = m.villageY;
        n.cold = m.cold;
        n.sun = m.sun;
        n.boat = m.boat;
        n.noMap = m.noMap;
        n.mazeLeft = m.mazeLeft;
        n.veinMine = m.veinMine;
        n.chestSpots = m.chestSpots;
        n.queenSpots = m.queenSpots;
        n.dummySpots = m.dummySpots;
        n.trees = m.trees;
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
        // [2026-11-04] "사막 서쪽(개미굴에 빠지는 곳)은 방벽을 쳐서 NPC가 막도록" -- 빠지는 구역 경계(x = DESERT_FALL_X)에 세로 성벽(W),
        //  가운데에 가까운 3칸 문 하나. 문은 경비병(buildWorld)이 지키고, Lv.41 미만은 서쪽으로 못 지나간다(화면 walk/antGate, 서버 mineAntFall).
        //  모든 무작위 값을 다 쓴 뒤에 덮어써서 나머지 칸/광석은 그대로.
        int wx = antWallX(w), gy = antGateY(t, wx);
        for (int y = 1; y < h - 1; y++) if (Math.abs(y - gy) > 1) { t[y][wx] = 'W'; o[y][wx] = 0; }
        for (int y = gy - 1; y <= gy + 1; y++) for (int x = wx - 1; x <= wx + 1; x++) { t[y][x] = 'd'; o[y][x] = 0; }
        return new S5MineMap(DESERT, "북쪽 사막", false, w, h, sx, sy, t, o, portals);
    }

    /** [2026-11-04] 사막 서쪽 방벽 x(이 칸보다 서쪽 = 개미굴에 빠지는 구역). */
    public static int antWallX(int w) { return (int) (w * DESERT_FALL_X); }

    /** [2026-11-04] 사막 서쪽 방벽 문 가운데 y -- 방벽 양옆이 걸을 수 있는, 가운데에 가장 가까운 줄. */
    static int antGateY(char[][] t, int wx) {
        int h = t.length;
        for (int d = 0; d < h / 2 - 3; d++) for (int y : new int[] { h / 2 - d, h / 2 + d })
            if (walkable(t[y][wx - 2]) && walkable(t[y][wx + 2]) && t[y][wx] != 'G') return y;
        return h / 2;
    }

    /** [2026-10-28] "다음 맵 변경에서는 개미굴을 더 넓게 헤매게, 500x500" -- 이 주기(10/29 05:00 리셋)부터 만드는 맵은 500x500(방 200개). 그 전 주기는 200x200(방 36개). */
    static final int ANT_BIG_CYCLE = 3;
    /** [2026-11-04] "다음 맵 리셋부터 개미굴 375x375, 나가는 굴 셋 중 하나는 피라미드 지하로" -- 11/06 05:00 리셋(주기 7)부터. */
    static final int ANT_V2_CYCLE = 7;

    /** [2026-10-28] 개미굴 -- 흙벽(#) 속에 크고 작은 방(c, 반지름 4~7)을 2칸 굴로 이어 붙인 미로. 사막에서 빠지면 한가운데 방에 떨어진다.
     *  지도가 안 보인다(noMap -- 화면이 미니맵을 가린다). 굴은 가장 가까운 방 하나에만 이어서(고리 적게) 막다른 길이 많다.
     *  나가는 굴 셋: 떨어진 곳에서 먼 방 가운데 서로(여왕의 방과도) 멀리 떨어진 세 곳에서 가장 가까운 가장자리까지 구불구불 판 굴 끝(U) = 피라미드.
     *  [2026-10-28] "여왕개미를 세 군데" -- 떨어진 곳에서 가장 먼 방(반지름 9) + 나가는 굴이 아닌 먼 방 둘(반지름 7)에 여왕개미(queenSpots, 보스 아님 -- 보상 없음).
     *  "보물 상자를 구석진 곳에 많이" + "인당 3개만, 보상이 아주 적은 것(1개)부터 지금 보상까지 골고루" -- 남은 방 가운데 맵 가운데에서 가장 먼(구석) 곳에
     *  보물 상자(200칸: 10개, 500칸: 30개), 상자마다 담긴 양을 0~1000으로 고르게 섞어 둔다(chestSpots {x, y, 양}).
     *  멀수록 광석이 좋고 몬스터가 강하다(11~13단계). big = 500x500. */
    static S5MineMap generateAntNest(long seed, boolean big, boolean v2) {
        int w = v2 ? 375 : big ? 500 : 200, h = w; // [2026-11-04] v2 = 375x375
        Random r = new Random(seed);
        Noise rich = new Noise(seed * 31 + 131);
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, '#');
        int n = v2 ? 112 : big ? 200 : 36, apart = v2 ? 90 : big ? 120 : 50, chestN = v2 ? 17 : big ? 30 : 10; // [2026-11-04] 375x375 = 500x500의 넓이 비율로
        int[][] rooms = new int[n][];
        rooms[0] = new int[] { w / 2, h / 2, 5 }; // 떨어지는 방(한가운데)
        for (int i = 1; i < n; i++) {
            int[] best = null;
            for (int tries = 0; tries < 80; tries++) { // 다른 방과 떨어진 자리
                int rx = 12 + r.nextInt(w - 24), ry = 12 + r.nextInt(h - 24), rr = 4 + r.nextInt(4);
                boolean ok = true;
                for (int j = 0; j < i && ok; j++) if (Math.hypot(rx - rooms[j][0], ry - rooms[j][1]) < rr + rooms[j][2] + 7) ok = false;
                if (ok) { best = new int[] { rx, ry, rr }; break; }
            }
            rooms[i] = best != null ? best : new int[] { 12 + r.nextInt(w - 24), 12 + r.nextInt(h - 24), 4 };
        }
        for (int[] rm : rooms) carveRoom(t, rm[0], rm[1], rm[2]);
        for (int i = 1; i < n; i++) { // 가장 가까운 앞 방과 굴로 잇기(모두 이어진다) + 드물게 하나 더(고리)
            int near = 0;
            for (int j = 1; j < i; j++) if (Math.hypot(rooms[i][0] - rooms[j][0], rooms[i][1] - rooms[j][1]) < Math.hypot(rooms[i][0] - rooms[near][0], rooms[i][1] - rooms[near][1])) near = j;
            antTunnel(t, r, rooms[i][0], rooms[i][1], rooms[near][0], rooms[near][1]);
            if (i > 3 && r.nextDouble() < 0.12) { int j = 1 + r.nextInt(i - 1); antTunnel(t, r, rooms[i][0], rooms[i][1], rooms[j][0], rooms[j][1]); }
        }
        int sx = rooms[0][0], sy = rooms[0][1];
        // 첫 여왕의 방 = 떨어진 칸에서 걸어서 가장 먼 방(반지름 9로 넓힌다)
        int[][] dist = bfsDist(t, sx, sy);
        int qi = 1;
        for (int i = 1; i < n; i++) if (dist[rooms[i][1]][rooms[i][0]] > dist[rooms[qi][1]][rooms[qi][0]]) qi = i;
        int qx = rooms[qi][0], qy = rooms[qi][1];
        carveRoom(t, qx, qy, 9);
        // 나가는 굴 셋 -- 먼 방부터, 이미 고른 방/여왕의 방과 apart칸 넘게 떨어진 방에서 가장 가까운 가장자리로
        List<Portal> portals = new ArrayList<>();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final int[][] dd = dist;
        final int[][] rm = rooms;
        Arrays.sort(order, (a, b) -> Integer.compare(dd[rm[b][1]][rm[b][0]], dd[rm[a][1]][rm[a][0]]));
        List<int[]> picked = new ArrayList<>();
        java.util.Set<Integer> used = new java.util.HashSet<>();
        used.add(0);
        used.add(qi);
        picked.add(rooms[qi]);
        for (int oi = 0; oi < n && portals.size() < 3; oi++) {
            int[] ro = rooms[order[oi]];
            if (used.contains(order[oi])) continue;
            boolean far = true;
            for (int[] p : picked) if (Math.hypot(ro[0] - p[0], ro[1] - p[1]) < apart) far = false;
            if (!far && oi < n - 3) continue;
            picked.add(ro);
            used.add(order[oi]);
            int ex, ey; // 가장 가까운 가장자리
            int dl = ro[0], dr = w - 1 - ro[0], du = ro[1], dn = h - 1 - ro[1], m = Math.min(Math.min(dl, dr), Math.min(du, dn));
            if (m == dl) { ex = 0; ey = ro[1]; } else if (m == dr) { ex = w - 1; ey = ro[1]; } else if (m == du) { ex = ro[0]; ey = 0; } else { ex = ro[0]; ey = h - 1; }
            antTunnel(t, r, ro[0], ro[1], Math.max(1, Math.min(w - 3, ex)), Math.max(1, Math.min(h - 3, ey)));
            t[ey][ex] = 'U';
            boolean under = v2 && portals.isEmpty(); // [2026-11-04] 첫 굴은 피라미드 지하로
            portals.add(new Portal(ex, ey, 'U', under ? PYRAMID_UNDER : PYRAMID, 0, 0, under ? "무너져 내린 굴 -- 아래로" : "빛이 새어 드는 굴 -- 밖으로"));
        }
        // 여왕개미 셋 -- 첫 여왕의 방 + 나가는 굴이 아닌 먼 방 가운데 여왕의 방끼리 apart/2칸 넘게 떨어진 둘(반지름 7)
        List<int[]> queens = new ArrayList<>();
        queens.add(new int[] { qx, qy });
        for (int oi = 0; oi < n && queens.size() < 3; oi++) {
            if (used.contains(order[oi])) continue;
            int[] ro = rooms[order[oi]];
            boolean far = true;
            for (int[] q : queens) if (Math.hypot(ro[0] - q[0], ro[1] - q[1]) < apart / 2.0) far = false;
            if (!far) continue;
            used.add(order[oi]);
            carveRoom(t, ro[0], ro[1], 7);
            queens.add(new int[] { ro[0], ro[1] });
        }
        // 보물 상자 -- 남은 방 가운데 맵 가운데에서 가장 먼(구석) chestN곳
        List<int[]> rest = new ArrayList<>();
        for (int i = 1; i < n; i++) if (!used.contains(i)) rest.add(rooms[i]);
        rest.sort((a, b) -> Double.compare(Math.hypot(b[0] - w / 2, b[1] - h / 2), Math.hypot(a[0] - w / 2, a[1] - h / 2)));
        List<int[]> chests = new ArrayList<>();
        for (int i = 0; i < Math.min(chestN, rest.size()); i++) chests.add(new int[] { rest.get(i)[0], rest.get(i)[1], 0 });
        dist = bfsDist(t, sx, sy);
        int far = 1;
        for (int[] row : dist) for (int d : row) far = Math.max(far, d);
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 2 || Math.hypot(x - qx, y - qy) <= 3;
            score[y][x] = 0.5 * rich.fbm(x / 12.0, y / 12.0) + 0.3 * Math.max(0, dist[y][x]) / far + 0.16 * r.nextDouble();
        }
        for (int[] c : chests) for (int y = c[1] - 1; y <= c[1] + 1; y++) for (int x = c[0] - 1; x <= c[0] + 1; x++) skip[y][x] = true;
        byte[][] o = assignOre(t, score, ANTNEST_SHARE, skip);
        // 상자마다 담긴 양 -- 0(보석 1개)부터 1000(가장 많이)까지 고르게, 자리와는 섞어서(난수는 광석 배치 뒤에 써서 지형/광석은 그대로)
        List<Integer> amounts = new ArrayList<>();
        for (int i = 0; i < chests.size(); i++) amounts.add(chests.size() == 1 ? 1000 : i * 1000 / (chests.size() - 1));
        Collections.shuffle(amounts, r);
        for (int i = 0; i < chests.size(); i++) chests.get(i)[2] = amounts.get(i);
        S5MineMap m = new S5MineMap(ANTNEST, "개미굴", false, w, h, sx, sy, t, o, portals);
        m.noMap = true;
        m.chestSpots = chests;
        m.queenSpots = queens;
        return m;
    }

    /** [2026-10-28] 피라미드(90x90) -- 개미굴을 빠져나오면 닿는 모래 언덕. 가운데 큰 돌 피라미드(R, 31x31), 남쪽 입구에서 곧은 돌길(s)로
     *  한가운데 방(9x9)까지, 방 가운데 보상 상자(NPC chest -- 사막의 심장, 맵 주기마다 한 번). 남쪽 아래에서 시작, 동쪽 문 G = 사막 서쪽. 몬스터/광석 없음. */
    /** [2026-11-04] 피라미드 지하(50x50) -- 사암 벽(R) 안 돌바닥 방, 양옆 기둥, 남쪽 무너진 굴(개미굴로), 북쪽 제단 앞에 파라오 미라(보스, 2x2). */
    static S5MineMap generatePyramidUnder(long seed) {
        int w = 50, h = 50, cx = w / 2;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'R' : r.nextDouble() < 0.06 ? 'd' : 's';
        for (int py = 8; py < h - 8; py += 8) for (int px : new int[] { 9, w - 11 }) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) t[py + dy][px + dx] = 'R'; // 기둥
        for (int x = cx - 4; x <= cx + 5; x++) t[5][x] = 'R'; // 제단 뒷벽
        t[h - 2][cx] = 'U';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(cx, h - 2, 'U', ANTNEST, 0, 0, "무너진 굴 -- 개미굴로"));
        S5MineMap m = new S5MineMap(PYRAMID_UNDER, "피라미드 지하", false, w, h, cx, h - 4, t, new byte[h][w], portals);
        m.bossX = cx;
        m.bossY = 11;
        return m;
    }

    static S5MineMap generatePyramid(long seed) {
        int w = 90, h = 90, cx = w / 2, cy = h / 2 - 4;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = r.nextDouble() < 0.02 ? 'C' : 'd';
        for (int x = 0; x < w; x++) { t[0][x] = 'R'; t[h - 1][x] = 'R'; }
        for (int y = 0; y < h; y++) { t[y][0] = 'R'; t[y][w - 1] = 'R'; }
        int half = 15;
        for (int y = cy - half; y <= cy + half; y++) for (int x = cx - half; x <= cx + half; x++) t[y][x] = 'R';
        for (int y = cy - 4; y <= cy + 4; y++) for (int x = cx - 4; x <= cx + 4; x++) t[y][x] = 's'; // 보물 방
        for (int y = cy + 5; y <= cy + half + 1; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = 's'; // 남쪽 입구 돌길
        for (int y = cy + half + 2; y <= h - 8; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = ','; // 시작점에서 입구까지 흙길
        int sx = cx, sy = h - 8;
        int gy = h / 2;
        for (int x = cx + half + 1; x < w - 1; x++) t[gy][x] = ','; // 동쪽 문까지
        t[gy][w - 1] = 'G';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(w - 1, gy, 'G', DESERT, 0, 0, "동쪽 -- 사막으로"));
        S5MineMap m = new S5MineMap(PYRAMID, "피라미드", false, w, h, sx, sy, t, new byte[h][w], portals);
        m.safe.add(new int[] { cx, cy, 5 });
        return m;
    }

    /** [2026-10-28] 설산 동굴(110x80) -- 바위산(^) 속 눈 바닥(n) 동굴 방 16개(반지름 5~8)를 굴로 이었다. 서쪽 끝 나가는 길(U) = 설산 마을 동문.
     *  벽 가운데 바닥에 붙은 칸 일부와 방 안 군데군데가 얼음 광맥(I -- 지나갈 수 없음, 앞에서 연속 채광). 땅에는 광석이 없다(veinMine). */
    static S5MineMap generateSnowCave(long seed, int floor) {
        boolean f2 = floor >= 2; // [2026-10-28] 2층 -- 더 넓고 방이 많고 광맥이 많다
        int w = f2 ? 130 : 110, h = f2 ? 100 : 80;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, '#');
        int n = f2 ? 22 : 16;
        int[][] rooms = new int[n][];
        rooms[0] = new int[] { 7, h / 2, 4 };
        for (int i = 1; i < n; i++) {
            int[] best = null;
            for (int tries = 0; tries < 60; tries++) {
                int rx = 18 + r.nextInt(w - 28), ry = 9 + r.nextInt(h - 18), rr = 5 + r.nextInt(4);
                boolean ok = true;
                for (int j = 0; j < i && ok; j++) if (Math.hypot(rx - rooms[j][0], ry - rooms[j][1]) < rr + rooms[j][2] + 5) ok = false;
                if (ok) { best = new int[] { rx, ry, rr }; break; }
            }
            rooms[i] = best != null ? best : new int[] { 18 + r.nextInt(w - 28), 9 + r.nextInt(h - 18), 4 };
        }
        for (int[] rm : rooms) carveRoom(t, rm[0], rm[1], rm[2]);
        for (int i = 1; i < n; i++) {
            int near = 0;
            for (int j = 1; j < i; j++) if (Math.hypot(rooms[i][0] - rooms[j][0], rooms[i][1] - rooms[j][1]) < Math.hypot(rooms[i][0] - rooms[near][0], rooms[i][1] - rooms[near][1])) near = j;
            antTunnel(t, r, rooms[i][0], rooms[i][1], rooms[near][0], rooms[near][1]);
            if (i > 2 && r.nextDouble() < 0.35) { int j = 1 + r.nextInt(i - 1); antTunnel(t, r, rooms[i][0], rooms[i][1], rooms[j][0], rooms[j][1]); }
        }
        int ey = h / 2;
        for (int x = 1; x <= rooms[0][0]; x++) { t[ey][x] = 'c'; t[ey + 1][x] = 'c'; }
        t[ey][0] = 'U';
        // 바닥 -> 눈, 바닥에 붙은 벽 일부 -> 얼음 광맥, 방 안 기둥 광맥, 나머지 벽 -> 바위산
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (t[y][x] == 'c') t[y][x] = 'n';
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if (t[y][x] != '#' || x < 14) continue; // 입구 둘레는 비운다
            boolean touch = t[y - 1][x] == 'n' || t[y + 1][x] == 'n' || t[y][x - 1] == 'n' || t[y][x + 1] == 'n';
            if (touch && r.nextDouble() < (f2 ? 0.2 : 0.12)) t[y][x] = 'I';
        }
        for (int i = 1; i < n; i++) for (int k = 0; k < (f2 ? 3 : 2); k++) {
            int x = rooms[i][0] + r.nextInt(5) - 2, y = rooms[i][1] + r.nextInt(5) - 2;
            boolean open = true;
            for (int yy = y - 1; yy <= y + 1 && open; yy++) for (int xx = x - 1; xx <= x + 1; xx++) if (t[yy][xx] != 'n') { open = false; break; }
            if (open) t[y][x] = 'I';
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (t[y][x] == '#') t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(0, ey, 'U', f2 ? SNOWCAVE : SNOWTOWN, 0, 0, f2 ? "설산 동굴 1층으로 올라가기" : "설산 마을로 나가기"));
        if (!f2) { // [2026-10-28] 1층 -- 입구에서 걸어서 가장 먼 방 가운데 = 2층 계단(난수를 더 쓰지 않아 다른 칸은 그대로)
            int[][] dist = bfsDist(t, 2, ey);
            int fi = 1;
            for (int i = 1; i < n; i++) if (dist[rooms[i][1]][rooms[i][0]] > dist[rooms[fi][1]][rooms[fi][0]]) fi = i;
            t[rooms[fi][1]][rooms[fi][0]] = 'S';
            portals.add(new Portal(rooms[fi][0], rooms[fi][1], 'S', SNOWCAVE2, 0, 0, "설산 동굴 2층으로 내려가기"));
        }
        S5MineMap m = new S5MineMap(f2 ? SNOWCAVE2 : SNOWCAVE, f2 ? "설산 동굴 2층" : "설산 동굴", true, w, h, 2, ey, t, new byte[h][w], portals);
        m.veinMine = true;
        m.level = f2 ? 5 : 4; // [2026-10-28] 어두움 1층 4레벨(투구 3단계부터), 2층 5레벨(투구 4단계부터)
        return m;
    }

    private static void carveRoom(char[][] t, int cx, int cy, int rr) {
        for (int y = cy - rr; y <= cy + rr; y++) for (int x = cx - rr; x <= cx + rr; x++)
            if (y > 0 && x > 0 && y < t.length - 1 && x < t[0].length - 1 && Math.hypot(x - cx, (y - cy) * 1.15) <= rr + 0.3) t[y][x] = 'c';
    }

    /** 2칸 너비로 구불구불 파 들어가는 굴(목표 쪽으로 70%, 옆으로 30%). */
    private static void antTunnel(char[][] t, Random r, int ax, int ay, int bx, int by) {
        int x = ax, y = ay, h = t.length, w = t[0].length;
        for (int guard = 0; guard < 2000 && (x != bx || y != by); guard++) {
            boolean horiz = Math.abs(bx - x) > Math.abs(by - y);
            if (r.nextDouble() < 0.3) horiz = !horiz;
            if (horiz && x != bx) x += Integer.signum(bx - x);
            else if (!horiz && y != by) y += Integer.signum(by - y);
            else if (x != bx) x += Integer.signum(bx - x);
            else y += Integer.signum(by - y);
            for (int yy = y; yy <= y + 1; yy++) for (int xx = x; xx <= x + 1; xx++) if (yy > 0 && xx > 0 && yy < h - 1 && xx < w - 1) t[yy][xx] = 'c';
        }
    }

    /** 걸어서 간 칸 수(못 가면 -1). */
    private static int[][] bfsDist(char[][] t, int sx, int sy) {
        int h = t.length, w = t[0].length;
        int[][] d = new int[h][w];
        for (int[] row : d) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        d[sy][sx] = 0;
        q.add(new int[] { sx, sy });
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] dv : DIRS) {
                int nx = p[0] + dv[0], ny = p[1] + dv[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || d[ny][nx] >= 0 || !walkable(t[ny][nx])) continue;
                d[ny][nx] = d[p[1]][p[0]] + 1;
                q.add(new int[] { nx, ny });
            }
        }
        return d;
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
        S5MineMap cm = new S5MineMap(CASTLE, "동쪽 성곽", false, w, h, sx, sy, t, o, portals);
        // [2026-11-15] "월드보스는 동쪽 성곽 맵에 드래곤 형태로" -- 내성 한가운데(큰길 위, 기둥 없음). 평소엔 없고 월드보스 시간에만(S55MineServiceImpl.mineBossDead).
        cm.bossX = (CASTLE_KEEP[0] + CASTLE_KEEP[2]) / 2 - 1; cm.bossY = cy - 1;
        for (int y = cm.bossY - 1; y <= cm.bossY + 2; y++) for (int x = cm.bossX - 1; x <= cm.bossX + 2; x++) if (t[y][x] == 'W') t[y][x] = 's';
        return cm;
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
        // [2026-10-28] "신궁 건물 더 크게, 입구 두 개" -- 29x21 -> 51x37, 남쪽 벽에 문 둘(서쪽 = 신궁 1층, 동쪽 = 닌자 두목의 방).
        //  두 문에서 내려온 참배길이 문 앞 광장에서 만나 남쪽으로 이어진다.
        int sx0 = cx - 25, sx1 = cx + 25, sy0 = cy - 24, sy1 = cy + 12;
        for (int y = sy0 - 3; y <= sy1 + 3; y++) for (int x = sx0 - 3; x <= sx1 + 3; x++) t[y][x] = 's';
        for (int y = sy0; y <= sy1; y++) for (int x = sx0; x <= sx1; x++) t[y][x] = 'L';
        int doorX = cx - 11, doorY = sy1, bossDoorX = cx + 11;
        t[doorY][doorX] = 'S';
        t[doorY][bossDoorX] = 'S';
        for (int dxx : new int[] { doorX, bossDoorX }) for (int y = doorY + 1; y <= doorY + 7; y++) for (int x = dxx - 1; x <= dxx + 1; x++) t[y][x] = 'p';
        for (int y = doorY + 6; y <= doorY + 8; y++) for (int x = doorX - 1; x <= bossDoorX + 1; x++) t[y][x] = 'p';
        for (int y = doorY + 8; y <= doorY + 30; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = 'p';
        // 남쪽 해안 부두와 배
        int pier = cx - 1, land = h - 2;
        while (land > 1 && t[land][pier + 1] == '~') land--;
        for (int y = land - 2; y <= land; y++) for (int x = pier; x <= pier + 2; x++) if (t[y][x] == '~') t[y][x] = 'b';
        int[] plank = pierAndShip(t, pier, land + 1, 1);
        int sx = pier + 2, sy = plank[1];
        // 부두 위 마을 광장
        // [2026-10-28] "다도해 본섬에 마을 추가" -- 광장(19x10)을 다도해 마을(35x16, 돌바닥)로 넓히고 초가집 여섯 채, 이동 안내원(GUIDE_MAPS).
        //  바다 칸은 그대로 둔다(해안선을 따라 마을이 끝난다). 마을 전체가 그늘(안전지대).
        int vx = pier + 1, vy = land - 9;
        for (int y = vy - 10; y <= vy + 5; y++) for (int x = vx - 17; x <= vx + 17; x++) if (t[y][x] != '~') t[y][x] = 's';
        for (int y = vy + 6; y <= land; y++) for (int x = pier; x <= pier + 2; x++) if (t[y][x] != 'P') t[y][x] = 's'; // 부두와 광장을 잇는 길
        for (int[] hs : new int[][] { { vx - 15, vy - 9 }, { vx - 8, vy - 9 }, { vx + 7, vy - 9 }, { vx + 14, vy - 9 }, { vx - 15, vy - 3 }, { vx + 14, vy - 3 } }) { // 초가집 여섯 채
            if (t[hs[1]][hs[0]] != 's' || t[hs[1]][hs[0] + 1] != 's' || t[hs[1] + 1][hs[0]] != 's' || t[hs[1] + 1][hs[0] + 1] != 's') continue; // 해안에 걸리면 빼고
            t[hs[1]][hs[0]] = 'h'; t[hs[1]][hs[0] + 1] = 'j'; t[hs[1] + 1][hs[0]] = 'k'; t[hs[1] + 1][hs[0] + 1] = 'l';
        }
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '~';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(plank[0], plank[1], 'B', SHIP, 0, 0, "배에 오르기(남쪽 섬으로)"));
        portals.add(new Portal(doorX, doorY, 'S', SHRINE, 0, 0, "신궁 안으로 (1층)"));
        portals.add(new Portal(bossDoorX, doorY, 'S', SHRINE_BOSS, 0, 0, "닌자 두목의 방 (신궁 열쇠 필요)"));
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
        m.sun = 3; // [2026-10-28] 1 -> 3
        m.safe.add(new int[] { vx, vy - 2, 13 }); // [2026-10-28] 다도해 마을 전체(예전 광장 반지름 7)
        m.safe.add(new int[] { cx, doorY + 7, 6 }); // [2026-10-28] 두 문 앞 광장
        addShadeGroves(m, r);
        return m;
    }

    /** 그늘(야자수 숲) -- 풀밭/모래 칸에 서로 떨어지게, 반지름 3. 햇빛 피해가 없는 안전지대(화면이 야자수 그늘로 그린다).
     *  [2026-10-28] "다도해도 (모닥불을 절반으로 줄인 설산과) 비슷한 수준으로 그늘" -- 걸을 수 있는 칸 SHADE_CELLS칸마다 한 곳, 서로 SHADE_GAP칸 넘게
     *  (예전: 본섬 10곳, 섬 5~6곳 -- 설산의 1/8 정도로 드물었다). */
    static final int SHADE_CELLS = 450, SHADE_GAP = 14;
    private static void addShadeGroves(S5MineMap m, Random r) {
        int walk = 0;
        for (int y = 0; y < m.h; y++) for (int x = 0; x < m.w; x++) if (walkable(m.terrain[y][x])) walk++;
        int n = Math.max(3, walk / SHADE_CELLS);
        for (int tries = 0, made = 0; made < n && tries < 40000; tries++) {
            int x = 3 + r.nextInt(m.w - 6), y = 3 + r.nextInt(m.h - 6);
            char c = m.terrain[y][x];
            if (c != '.' && c != 'b' && c != 'u') continue;
            boolean far = Math.abs(x - m.spawnX) + Math.abs(y - m.spawnY) > 12;
            for (int[] z : m.safe) if (Math.abs(z[0] - x) + Math.abs(z[1] - y) < SHADE_GAP) far = false;
            if (!far) continue;
            m.safe.add(new int[] { x, y, 3 });
            made++;
        }
    }

    /** 신궁 층(130x100): 붉은 기둥 벽(L) 사이로 방 5x4개(20x18)와 3칸 너비 복도, 바닥은 붉은 길(p), 방마다 기둥. 아래 가운데 방 밑으로 나가는 길(U --
     *  1층은 본섬, 2/3층은 아래층 계단 옆). 1/2층은 맨 위 구석 방(1층 오른쪽, 2층 왼쪽) 위 벽에 올라가는 계단(S). 광석 없음, 닌자만.
     *  [2026-10-28] 예전 신궁 내부(96x78, 방 4x3, 한 층)를 "더 넓게, 3층까지"로 바꿨다(1층 id는 SHRINE 그대로). floor = 1~3. */
    private static void greyStone(char[][] t) {
        for (char[] row : t) for (int x = 0; x < row.length; x++) { if (row[x] == 'p') row[x] = 's'; else if (row[x] == 'L') row[x] = 'W'; }
    }

    static S5MineMap generateShrine(long seed, int floor) {
        int cols = 5, rows = 4, rw = 20, rh = 18, gx = 25, gy = 24, w = 4 + cols * gx + 1, h = 4 + rows * gy;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, 'L');
        int entRow = rows - 1, entCol = cols / 2;
        int[][] rc = new int[rows * cols][];
        for (int row = 0; row < rows; row++) for (int col = 0; col < cols; col++) {
            int x0 = 4 + col * gx, y0 = 4 + row * gy;
            rc[row * cols + col] = new int[] { x0, y0 };
            for (int y = y0; y < y0 + rh; y++) for (int x = x0; x < x0 + rw; x++) t[y][x] = 'p';
            if (!(row == entRow && col == entCol)) for (int[] pp : new int[][] { { 4, 4 }, { 14, 4 }, { 4, 12 }, { 14, 12 }, { 9, 8 } }) // 기둥(들어오는 방은 비움)
                if (r.nextDouble() < 0.7) for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) t[y0 + pp[1] + yy][x0 + pp[0] + xx] = 'L';
        }
        for (int row = 0; row < rows; row++) for (int col = 0; col < cols - 1; col++) { // 가로 복도(층마다 몇 개는 막혀 있다 -- 아래에서 이어지는지 확인)
            if (row != entRow && r.nextDouble() < 0.2) continue;
            int[] a = rc[row * cols + col];
            for (int x = a[0] + rw; x < a[0] + gx; x++) for (int y = a[1] + rh / 2 - 1; y <= a[1] + rh / 2 + 1; y++) t[y][x] = 'p';
        }
        for (int row = 0; row < rows - 1; row++) for (int col = 0; col < cols; col++) { // 세로 복도
            int[] a = rc[row * cols + col];
            for (int y = a[1] + rh; y < a[1] + gy; y++) for (int x = a[0] + rw / 2 - 1; x <= a[0] + rw / 2 + 1; x++) t[y][x] = 'p';
        }
        int[] ent = rc[entRow * cols + entCol];
        int ex = ent[0] + rw / 2;
        for (int y = ent[1] + rh; y < h - 1; y++) for (int x = ex - 1; x <= ex + 1; x++) t[y][x] = 'p';
        t[h - 2][ex] = 'U';
        for (int x = 0; x < w; x++) { t[0][x] = 'L'; t[h - 1][x] = 'L'; }
        int sx = ex, sy = h - 5;
        // 막힌 가로 복도 때문에 못 가는 방이 있으면 그 방 위 세로 복도는 늘 있으니 실제로는 다 이어진다 -- 그래도 혹시 모를 고립 칸은 벽으로
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'L';
        String id = floor == 1 ? SHRINE : floor == 2 ? SHRINE2 : SHRINE3;
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(ex, h - 2, 'U', floor == 1 ? ARCHI : floor == 2 ? SHRINE : SHRINE2, 0, 0, floor == 1 ? "신궁 밖으로" : "아래층으로 내려가는 계단"));
        if (floor < 3) {
            int[] up = rc[floor == 1 ? cols - 1 : 0];
            int ux = up[0] + rw / 2, uy = up[1] - 1;
            t[uy][ux] = 'S';
            portals.add(new Portal(ux, uy, 'S', floor == 1 ? SHRINE2 : SHRINE3, 0, 0, "신궁 " + (floor + 1) + "층으로 올라가는 계단"));
        }
        greyStone(t); // [2026-10-29] "신궁 내부가 용궁이라고 뜬다, 회색 배경" -- 붉은 용궁 길/기둥(p/L) 대신 회색 성 바닥/벽(s/W)
        return new S5MineMap(id, "신궁 " + floor + "층", false, w, h, sx, sy, t, new byte[h][w], portals);
    }

    /** [2026-10-28] 닌자 두목의 방(64x50): 붉은 벽(L)으로 둘러싼 넓은 홀(p), 가장자리에 기둥 두 줄, 북쪽 단상에 닌자 두목(보스). 아래 가운데 U = 본섬 신궁 동쪽 문.
     *  신궁 열쇠(SHRINE_KEY)가 있어야 들어온다(requireItem). 일반 몬스터/광석 없음 -- S5MineMobs: 분신 자리 + 두목만. */
    static S5MineMap generateShrineBoss(long seed) {
        int w = 64, h = 50, c = w / 2;
        Random r = new Random(seed);
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, 'L');
        for (int y = 3; y < h - 4; y++) for (int x = 3; x < w - 3; x++) t[y][x] = 'p';
        for (int y = 8; y < h - 8; y += 6) for (int x : new int[] { 8, w - 10 }) if (r.nextDouble() < 0.85) for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) t[y + yy][x + xx] = 'L'; // 기둥 두 줄
        for (int y = h - 4; y < h - 1; y++) for (int x = c - 1; x <= c + 1; x++) t[y][x] = 'p';
        t[h - 2][c] = 'U';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(c, h - 2, 'U', ARCHI, 0, 0, "신궁 밖으로"));
        greyStone(t); // [2026-10-29]
        S5MineMap m = new S5MineMap(SHRINE_BOSS, "닌자 두목의 방", false, w, h, c, h - 5, t, new byte[h][w], portals);
        m.bossX = c;
        m.bossY = 10;
        m.requireItem = SHRINE_KEY_ITEM;
        return m;
    }

    /** [2026-10-28] 닌자 두목 분신술 자리 -- 홀 가운데 둘레(반지름 8) 다섯 칸, 첫 칸 각도는 rot(0~4)마다 돈다. 못 서는 칸이면 가까운 칸으로. */
    public int[][] shrineSplitSpots(int rot) {
        int cx = bossX, cy = bossY + 10;
        int[][] out = new int[5][];
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * (i + rot / 5.0) / 5 - Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(a) * 9), y = cy + (int) Math.round(Math.sin(a) * 7);
            for (int d = 0; d < 4 && !walkable(x, y); d++) { x += Integer.signum(cx - x); y += Integer.signum(cy - y); }
            out[i] = new int[] { x, y };
        }
        return out;
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
        { // [2026-11-18] 왼쪽 아래 = 남쪽 바닷가 부두(바닷가 뱃사람이 띄워 준 배가 여기서 나간다)
            int dy = h - 24;
            for (int y = dy - 2; y <= dy + 2; y++) for (int x = 0; x <= 3; x++) t[y][x] = x == 0 ? 'b' : '~';
            t[dy][1] = 'B';
            portals.add(new Portal(1, dy, 'B', BEACH, 0, 0, "남쪽 바닷가에 내리기"));
        }
        S5MineMap m = new S5MineMap(ARCHISEA, "다도해 뱃길", false, w, h, w / 2, h - 7, t, new byte[h][w], portals);
        m.boat = true;
        m.sun = 3; // [2026-10-28] 1 -> 3
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
        // [2026-10-28] "마지막 섬엔 보스" -- 용암섬 남쪽 끝(부두 반대편) 탁 트인 칸에서 4칸 안쪽 = 용암 거신 자리, 둘레(반지름 4)는 자갈 화구(걸을 수 있는 칸만)
        int bossX = -1, bossY = -1;
        if (idx == ISLE_IDS.length - 1) {
            for (int y = h - 3; y > h / 2 && bossX < 0; y--) for (int dx = 0; dx <= w / 3 && bossX < 0; dx++) for (int sg = -1; sg <= 1; sg += 2) {
                int x = cx + sg * dx;
                if (x < 2 || x >= w - 2) continue;
                boolean open = true;
                for (int yy = y - 1; yy <= y + 1 && open; yy++) for (int xx = x - 1; xx <= x + 1; xx++) if (!walkable(t[yy][xx])) { open = false; break; }
                if (open) { bossX = x; bossY = y; break; }
            }
            if (bossX >= 0) {
                for (int k = 0; k < 4 && walkable(t[bossY - 1][bossX]); k++) bossY--;
                for (int y = bossY - 4; y <= bossY + 4; y++) for (int x = bossX - 4; x <= bossX + 4; x++)
                    if (y > 0 && x > 0 && y < h - 1 && x < w - 1 && walkable(t[y][x]) && Math.hypot(x - bossX, y - bossY) <= 4.2) t[y][x] = 'r';
            }
        }
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
        if (bossX >= 0) { m.bossX = bossX; m.bossY = bossY; m.safe.add(new int[] { bossX, bossY, -100 }); } // 보스 자리(그늘 고르기에서 멀리 -- 반지름 음수라 안전지대는 아님)
        addShadeGroves(m, r);
        m.safe.removeIf(z -> z[2] < 0);
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
        portals.add(new Portal(gates[1][0], gates[1][1], 'G', SNOWCAVE, 0, 0, "동문 -- 설산 동굴")); // [2026-10-28] 개방(좌표는 buildWorld)
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

    /** [2026-11-05] 왕도 서쪽길(240x120) -- 탁 트인 툰드라 들판(풀밭/자갈/드문 나무, 바위 언덕). 동쪽 끝 G = 왕도 서문, 서쪽으로 굽이치는 흙길이 끝까지(막다른 길).
     *  맵 중간중간 바위 언덕 셋에 동굴 입구(M, 아직 막힘 -- 대상 없음). 몬스터는 무리 지어(S5MineMobs.spawn), 왕도에서 멀수록 강하다.
     *  [2026-11-05] 이름 "왕도 서쪽 마물의 숲 입구", 채광 불가(광석 없음), 입구 파수꾼, 가운데 보스 자리. */
    static S5MineMap generateWestRoad(long seed) {
        int w = 240, h = 120;
        Random r = new Random(seed);
        Noise rock = new Noise(seed * 31 + 81), wood = new Noise(seed * 31 + 82), rich = new Noise(seed * 31 + 83), grav = new Noise(seed * 31 + 84);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double rk = rock.fbm(x / 13.0, y / 13.0), wd = wood.fbm(x / 10.0, y / 10.0), gv = grav.fbm(x / 9.0, y / 9.0);
            t[y][x] = rk > 0.7 ? '^' : wd > 0.68 ? (r.nextDouble() < 0.25 ? 'T' : '.') : gv > 0.66 ? ':' : '.';
        }
        for (int x = 0; x < w; x++) { t[0][x] = '^'; t[h - 1][x] = '^'; }
        for (int y = 0; y < h; y++) { t[y][0] = '^'; t[y][w - 1] = '^'; }
        // 동굴 셋 -- 가운데를 피해 맵 중간중간(북동 / 남쪽 가운데 / 북서). 바위 언덕(타원) 아래쪽 가운데가 입구, 입구 앞 3칸은 비운다.
        int[][] caves = { { (int) (w * 0.70), (int) (h * 0.24) }, { (int) (w * 0.44), (int) (h * 0.74) }, { (int) (w * 0.18), (int) (h * 0.30) } };
        // [2026-11-05] "동굴 셋을 숲을 배경으로, 숲 던전 입구로" -- 바위 언덕 대신 지나갈 수 없는 울창한 숲(F) 덩어리, 둘레엔 나무(T)가 듬성듬성, 아래 가운데가 숲 던전 입구(M, 아직 막힘)
        String[] caveNames = { "숲 던전 입구", "숲 던전 입구", "숲 던전 입구" };
        for (int[] cv : caves) {
            for (int y = cv[1] - 6; y <= cv[1] + 6; y++) for (int x = cv[0] - 11; x <= cv[0] + 11; x++) {
                double dd = Math.pow((x - cv[0]) / 9.0, 2) + Math.pow((y - cv[1]) / 5.0, 2);
                int hh = (x * 73856093) ^ (y * 19349663);
                if (dd <= 1.0 || (dd <= 1.2 && (hh & 3) != 0)) t[y][x] = 'F';
                else if (dd <= 1.8 && (hh & 7) < 3 && t[y][x] == '.') t[y][x] = 'T';
            }
            for (int y = cv[1] + 6; y <= cv[1] + 8; y++) for (int x = cv[0] - 1; x <= cv[0] + 1; x++) t[y][x] = '.';
            t[cv[1] + 5][cv[0]] = 'M';
        }
        // 동쪽 문에서 서쪽으로 굽이치는 3칸 흙길(막다른 길)
        int py = h / 2;
        for (int x = w - 2; x >= 3; x--) {
            if (r.nextDouble() < 0.2) py += r.nextBoolean() ? 1 : -1;
            py = Math.max(6, Math.min(h - 7, py));
            for (int dy = -1; dy <= 1; dy++) if (t[py + dy][x] != 'M') t[py + dy][x] = ',';
        }
        int bx = w / 2, by = h / 2; // [2026-11-05] 맵 가운데 보스 자리 -- 둘레를 탁 트인 풀밭으로(동굴 언덕/흙길은 그대로)
        for (int y = by - 6; y <= by + 6; y++) for (int x = bx - 8; x <= bx + 8; x++)
            if (Math.pow((x - bx) / 8.0, 2) + Math.pow((y - by) / 6.0, 2) <= 1.0 && (t[y][x] == '^' || t[y][x] == 'T' || t[y][x] == ':')) t[y][x] = '.';
        int gy = h / 2;
        for (int x = w - 6; x < w - 1; x++) for (int dy = -1; dy <= 1; dy++) t[gy + dy][x] = ',';
        t[gy][w - 1] = 'G';
        int sx = w - 2, sy = gy;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '^';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(w - 1, gy, 'G', CITY, 0, 0, "왕도 아스테리아 서문으로"));
        for (int i = 0; i < caves.length; i++) portals.add(new Portal(caves[i][0], caves[i][1] + 5, 'M', null, 0, 0, caveNames[i] + " (가시덩굴이 얽혀 막혀 있다)"));
        byte[][] o = new byte[h][w]; // [2026-11-05] "이 맵은 채광 불가" -- 광석 없음(화면도 캐기 버튼/자동채광을 숨긴다)
        S5MineMap m = new S5MineMap(WESTROAD, "왕도 서쪽 마물의 숲 입구", false, w, h, sx, sy, t, o, portals); // [2026-11-05] 이름 바꿈(왕도 서쪽길 -> 마물의 숲 입구)
        m.bossX = bx; m.bossY = by; // [2026-11-05] 밤에 마물 200마리를 쓰러뜨리면 맵 가운데에 천둥 황소
        return m;
    }

    // [2026-11-11] 마물의 숲 미궁(두고개 모티브) -- "10x10칸 방, 동서남북 통로, 남쪽으로 가면 들어왔던 입구로, 지금 어디인지 모르게(계속 같은 맵을 도는 것처럼), 방마다 다른 몬스터".
    //  방은 모두 같은 이름/같은 모양/지도 없음. 동/서/북 통로는 고정 시드(FOREST_SEED -- 맵 초기화 때 안 바뀜)로 섞어 잇는다:
    //  맞는 통로를 차례로(FOREST_PATH_LEN개 방) 고르면 끝방, 틀린 통로는 길에서 벗어난 방이나 처음 두 방으로. 남쪽 통로는 어느 방이든 입구 방으로(입구 방 남쪽은 마물의 숲 입구).
    //  끝방(FOREST_END, 몬스터 없음 -- 쉼터) 북쪽 -> 하피의 둥지 -> 둥지 위쪽 -> 강이 보이는 나루터(HARPY_FERRY).
    static final long FOREST_SEED = 20261108L;
    static final int FOREST_PATH_LEN = 7, ROOM_W = 10;
    /** 미궁 방 FOREST_ROOM_N개 + 끝방 + 둥지 + 나루터(포탈을 다 이어서). wrX, wrY = 입구 방 남쪽 통로가 내려 주는 마물의 숲 입구 칸. */
    static List<S5MineMap> forestWorld(int wrX, int wrY) {
        int n = FOREST_ROOM_N, c = ROOM_W / 2, L = ROOM_W - 1;
        int[][] ex = forestExits();
        List<S5MineMap> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Portal> ps = new ArrayList<>();
            String[] tg = new String[3];
            for (int d = 0; d < 3; d++) tg[d] = ex[i][d] < 0 ? FOREST_END : FOREST_ROOM_IDS[ex[i][d]];
            ps.add(new Portal(L, c, 'G', tg[0], 1, c, "동쪽 숲길"));
            ps.add(new Portal(0, c, 'G', tg[1], L - 1, c, "서쪽 숲길"));
            ps.add(new Portal(c, 0, 'G', tg[2], c, L - 1, "북쪽 숲길"));
            ps.add(i == 0 ? new Portal(c, L, 'G', WESTROAD, wrX, wrY, "남쪽 숲길") : new Portal(c, L, 'G', FOREST, c, L - 1, "남쪽 숲길"));
            out.add(forestRoom(FOREST_ROOM_IDS[i], "마물의 숲 미궁", ps, false));
        }
        List<Portal> ep = new ArrayList<>();
        ep.add(new Portal(c, 0, 'G', HARPY_NEST, HARPY_NEST_W / 2, HARPY_NEST_H - 3, "짙은 바람이 불어오는 숲길"));
        ep.add(new Portal(c, L, 'G', FOREST, c, L - 1, "남쪽 숲길"));
        out.add(forestRoom(FOREST_END, "미로의 끝방", ep, true));
        // [2026-11-11] "힌트로 미궁의 끝까지 몇 걸음" -- 끝방에서 거꾸로(들어오는 통로를 따라) 센 가장 적은 통로 수
        java.util.Map<String, Integer> dist = new java.util.HashMap<>();
        java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
        dist.put(FOREST_END, 0); q.add(FOREST_END);
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (S5MineMap fm : out) if (!dist.containsKey(fm.id)) for (Portal p : fm.portals) if (cur.equals(p.target)) { dist.put(fm.id, dist.get(cur) + 1); q.add(fm.id); break; }
        }
        for (S5MineMap fm : out) if (isForestRoom(fm.id)) fm.mazeLeft = dist.getOrDefault(fm.id, -1);
        out.add(generateHarpyNest());
        out.add(generateHarpyFerry());
        return out;
    }
    /** 방마다 {동, 서, 북} 통로가 가는 방 번호(-1 = 끝방). 고정 시드. */
    static int[][] forestExits() {
        int n = FOREST_ROOM_N;
        Random r = new Random(FOREST_SEED);
        List<Integer> rest = new ArrayList<>();
        for (int i = 1; i < n; i++) rest.add(i);
        Collections.shuffle(rest, r);
        int[] path = new int[FOREST_PATH_LEN];
        for (int i = 1; i < path.length; i++) path[i] = rest.get(i - 1);
        List<Integer> pool = new ArrayList<>(rest.subList(path.length - 1, rest.size())); // 길에서 벗어난 방들 + 처음 두 방
        pool.add(path[0]); pool.add(path[1]);
        int[][] ex = new int[n][3];
        for (int[] e : ex) java.util.Arrays.fill(e, -2);
        for (int i = 0; i < path.length; i++) ex[path[i]][r.nextInt(3)] = i + 1 < path.length ? path[i + 1] : -1;
        boolean[][] fixed = new boolean[n][3];
        for (int i = 0; i < n; i++) for (int d = 0; d < 3; d++) fixed[i][d] = ex[i][d] != -2;
        for (int i = 0; i < n; i++) for (int d = 0; d < 3; d++) if (ex[i][d] == -2) { int q; do { q = pool.get(r.nextInt(pool.size())); } while (q == i); ex[i][d] = q; }
        for (int q : pool) { // 아무 통로도 들어오지 않는 방이 없게(그 방 몬스터도 만나게) -- 들어오는 길이 둘 이상인 방으로 가던 틀린 통로 하나를 돌린다
            int[] in = new int[n];
            for (int i = 0; i < n; i++) for (int d = 0; d < 3; d++) if (ex[i][d] >= 0 && ex[i][d] != i) in[ex[i][d]]++;
            if (in[q] > 0) continue;
            outer:
            for (int k = 0; k < n * 3; k++) { int i = r.nextInt(n), d = r.nextInt(3); if (i != q && !fixed[i][d] && ex[i][d] >= 0 && in[ex[i][d]] >= 2) { ex[i][d] = q; break outer; } }
        }
        return ex;
    }
    /** 10x10 숲 방 -- 둘레는 지나갈 수 없는 숲(F), 통로 칸은 문(G), 가운데 열십자 흙길, 나무 둘(통과). 끝방은 동/서 통로 없이 쉼터. */
    static S5MineMap forestRoom(String id, String name, List<Portal> ps, boolean end) {
        int w = ROOM_W, c = w / 2, L = w - 1;
        char[][] t = new char[w][w];
        for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) t[y][x] = x == 0 || y == 0 || x == L || y == L ? 'F' : (x == c || y == c) ? ',' : '.';
        t[3][3] = 'T'; t[6][6] = 'T';
        for (Portal p : ps) t[p.y][p.x] = 'G';
        S5MineMap m = new S5MineMap(id, name, false, w, w, c, L - 1, t, new byte[w][w], ps);
        m.noMap = true; // 지도 없음(지금 어디인지 모르게)
        if (end) m.safe.add(new int[] { c, c, 6 });
        return m;
    }
    static final int HARPY_NEST_W = 46, HARPY_NEST_H = 38, FERRY_W = 30, FERRY_H = 22;
    // [2026-11-08] 하피의 둥지(보스룸) -- 숲 벽으로 둘러싼 풀밭에 지나갈 수 없는 대형 나무(3x2칸 'E'), 가운데는 비워 둔다(숲이 다 타면 하피가 내려앉는 곳).
    //  나무 위쪽 두 줄(하피가 앉는 자리 -- 화면은 나무 수관에 가려 그린다)은 비운다. 잡몹 없음(하피만). [2026-11-11] 아래 문 = 미로의 끝방, 위 문 = 나루터.
    static S5MineMap generateHarpyNest() {
        int w = HARPY_NEST_W, h = HARPY_NEST_H;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) ? 'F' : '.';
        List<int[]> trees = new ArrayList<>();
        int cx = w / 2, cy = h / 2;
        for (int ty : new int[] { 6, 15, 24 }) for (int tx : new int[] { 5, 14, 27, 36 }) {
            int jx = tx + ((tx * 7 + ty * 3) % 3) - 1, jy = ty + ((tx + ty) % 2);
            if (Math.abs(jx + 1 - cx) < 6 && Math.abs(jy - cy) < 5) continue; // 가운데는 비움
            for (int y = jy; y < jy + 2; y++) for (int x = jx; x < jx + 3; x++) t[y][x] = 'E';
            trees.add(new int[] { jx, jy });
        }
        t[h - 2][cx] = '.';
        t[h - 1][cx] = 'G';
        t[1][cx] = '.';
        t[0][cx] = 'G';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(cx, h - 1, 'G', FOREST_END, ROOM_W / 2, 1, "미로의 끝방으로"));
        portals.add(new Portal(cx, 0, 'G', HARPY_FERRY, FERRY_W / 2, FERRY_H - 3, "강이 보이는 나루터로"));
        byte[][] o = new byte[h][w];
        S5MineMap m = new S5MineMap(HARPY_NEST, "하피의 둥지", false, w, h, cx, h - 3, t, o, portals);
        m.trees = Collections.unmodifiableList(trees);
        m.bossX = cx - 1; m.bossY = cy - 1;
        return m;
    }
    /** NPC 종류/이름/대사만 바꾼 복사본(자리·바닥은 그대로). */
    private static Npc withKind(Npc n, String kind, String name, String line) {
        Npc c = new Npc(n.x, n.y, kind, name, line);
        c.ground = n.ground;
        return c;
    }
    /** [2026-11-15] 탑의 도시(140x120) -- 왕도보다 큰 성벽 도시. 아래(남쪽)는 강과 나무 선착장(나루터에서 배로 도착, 뱃사공), 가운데 광장과 분수,
     *  북쪽 가운데에 거대한 탑(Q -- 들어가는 문 앞에 탑 관리인), 동서로 붉은 지붕 건물 블록, 광장 옆 대장장이/신부. 광석 없음. */
    // [2026-11-15] 같은 날 "요새 도시가 왕도와 구조가 너무 똑같다 -- 더 웅장하게, 더 화려하게, 자신 있는 대로. 북쪽 맵으로 이동해서 거대한 탑 입구로" --
    //  왕도(십자 대로 + 네모 블록)와 달리 물 위에 지은 둥근 도시: 한가운데 큰 분수, 그 둘레로 안쪽 운하 -> 대리석 광장 -> 꽃 정원 -> 바깥 운하가 겹겹이 두르고
    //  (운하마다 다리), 바깥은 고리 길을 따라 건물이 호를 그리며 늘어선다. 남북·동서 큰길은 가로등과 조각상이 줄지어 서고, 네 모서리엔 연못 정원, 동쪽엔 대신전, 서쪽엔 장인 회관.
    //  남쪽은 강과 넓은 대리석 부두(뱃사공), 북쪽 큰길 끝 성문을 나서면 탑 입구 맵(TOWER_GATE -- 하늘까지 솟은 탑의 정면과 큰 문).
    //  새 바닥 글자: A = 조각상, i = 수정 가로등, g = 꽃밭(모두 못 지나감). 화면은 이 두 맵에서 돌바닥/길/건물/벽/나무를 전용 그림(대리석·모자이크·푸른 지붕·흰 성벽·꽃나무)으로 그린다.
    static final int TCITY_W = 151, TCITY_H = 131, GATE_W = 61, GATE_H = 76, GATE_DOOR_Y = 34;
    static S5MineMap generateTowerCity() {
        int w = TCITY_W, h = TCITY_H, c = w / 2, cy = 60, wt = 3, riv = h - 12;
        Random r = new Random(1115);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < wt || y < wt || x >= w - wt ? 'W' : y >= riv ? '~' : 's';
        // 겹겹의 고리 -- 가운데에서의 거리로
        for (int y = wt; y < riv; y++) for (int x = wt; x < w - wt; x++) {
            double d = Math.hypot(x - c, y - cy);
            boolean card = Math.abs(x - c) <= 4 || Math.abs(y - cy) <= 4, diag = Math.abs(Math.abs(x - c) - Math.abs(y - cy)) <= 1;
            char ch;
            if (d < 5.2) ch = '~'; // 큰 분수
            else if (d < 13) ch = d > 8.4 && d < 10.2 ? ',' : 's'; // 분수 광장(모자이크 띠)
            else if (d < 16) ch = card && (Math.abs(x - c) <= 2 || Math.abs(y - cy) <= 2) ? 'H' : '~'; // 안쪽 운하 + 다리 넷
            else if (d < 27) ch = d > 20.4 && d < 22.4 ? ',' : 's'; // 대리석 광장
            else if (d < 36) ch = card ? 's' : diag ? ',' : (d > 30.5 && d < 32.5 ? 'g' : (((x * 5 + y * 3) % 11 == 0) ? 'T' : '.')); // 꽃 정원(큰길/샛길은 열어 둔다)
            else if (d < 39) ch = card ? 'H' : diag ? 'H' : '~'; // 바깥 운하 + 다리 여덟
            else ch = (d > 42 && d < 44.4) || (d > 52 && d < 54.4) || (d > 62 && d < 64.4) ? ',' : 's'; // 바깥 구역 -- 고리 길
            t[y][x] = ch;
        }
        // 큰길(남북·동서) -- 가운데 모자이크 띠
        for (int y = wt; y < riv; y++) for (int x = wt; x < w - wt; x++) {
            double d = Math.hypot(x - c, y - cy);
            if (d < 39) continue;
            if (Math.abs(x - c) <= 4 || Math.abs(y - cy) <= 4) t[y][x] = Math.abs(x - c) <= 1 || Math.abs(y - cy) <= 1 ? ',' : 's';
        }
        // 동쪽 대신전(Q) / 서쪽 장인 회관(K) -- 동서 큰길 북쪽에 붙여
        for (int y = cy - 17; y <= cy - 7; y++) for (int x = c + 50; x <= c + 66; x++) t[y][x] = 'Q';
        for (int y = cy - 16; y <= cy - 7; y++) for (int x = c - 66; x <= c - 51; x++) t[y][x] = 'K';
        for (int y = cy - 6; y <= cy - 5; y++) { for (int x = c + 48; x <= c + 68; x++) t[y][x] = 's'; for (int x = c - 68; x <= c - 49; x++) t[y][x] = 's'; }
        // 네 모서리 연못 정원
        for (int[] g : new int[][] { { c - 55, cy - 44 }, { c + 55, cy - 44 }, { c - 55, cy + 40 }, { c + 55, cy + 40 } })
            for (int y = g[1] - 9; y <= g[1] + 9; y++) for (int x = g[0] - 9; x <= g[0] + 9; x++) {
                if (x < wt + 1 || x >= w - wt - 1 || y < wt + 1 || y >= riv - 7) continue;
                double d = Math.hypot(x - g[0], y - g[1]);
                if (d < 3.2) t[y][x] = '~';
                else if (d < 4.4) t[y][x] = 's';
                else if (d < 6.2) t[y][x] = Math.abs(x - g[0]) <= 0 || Math.abs(y - g[1]) <= 0 ? 's' : 'g';
                else if (d < 9.2) t[y][x] = Math.abs(x - g[0]) <= 0 || Math.abs(y - g[1]) <= 0 ? 's' : ((x + y) % 3 == 0 ? 'T' : '.');
            }
        // 건물 -- 고리 길 사이에 호를 그리며(큰길에서 떨어져)
        for (int[] ring : new int[][] { { 48, 22 }, { 58, 28 }, { 68, 34 } }) {
            int R = ring[0], n = ring[1];
            for (int i = 0; i < n; i++) {
                double a = (i + 0.5) * 2 * Math.PI / n;
                int bx = (int) Math.round(c + R * Math.cos(a)), by = (int) Math.round(cy + R * Math.sin(a)), bw = 3, bh = 3 + (i % 3 == 0 ? 1 : 0);
                if (Math.abs(bx - c) < 9 || Math.abs(by - cy) < 9) continue;
                boolean ok = true;
                for (int y = by - bh - 1; y <= by + bh + 1 && ok; y++) for (int x = bx - bw - 1; x <= bx + bw + 1; x++)
                    if (x < wt + 1 || x >= w - wt - 1 || y < wt + 1 || y >= riv - 7 || (t[y][x] != 's' && t[y][x] != ',')) { ok = false; break; } // 돌바닥/고리 길 위에만(정원·신전·운하와 안 겹치게)
                if (!ok) continue;
                for (int y = by - bh; y <= by + bh; y++) for (int x = bx - bw; x <= bx + bw; x++) t[y][x] = 'K';
            }
        }
        // 남쪽 대리석 부두 -- 넓은 단 + 가운데 선착장
        for (int y = riv - 6; y < riv; y++) for (int x = c - 24; x <= c + 24; x++) t[y][x] = y == riv - 6 || Math.abs(x - c) == 24 ? ',' : 's';
        for (int y = riv; y < h - 2; y++) for (int x = c - 2; x <= c + 2; x++) t[y][x] = 'H';
        for (int y = riv; y < riv + 3; y++) for (int x = c - 16; x <= c + 16; x++) if (Math.abs(x - c) > 2 && (Math.abs(x - c) % 8 < 2)) t[y][x] = 'H'; // 작은 잔교들
        // 큰길 가로등과 조각상(길 바로 바깥 줄), 분수 광장 둘레 조각상
        for (int k = 44; k < 70; k += 5) for (int sg = -1; sg <= 1; sg += 2) {
            char deco = k % 10 == 4 ? 'A' : 'i';
            int yy = cy + sg * k, xx = c + sg * k;
            if (yy > wt + 1 && yy < riv - 8) { t[yy][c - 6] = deco; t[yy][c + 6] = deco; }
            if (xx > wt + 1 && xx < w - wt - 2) { if (t[cy - 6][xx] == 's' || t[cy - 6][xx] == ',') t[cy - 6][xx] = deco; if (t[cy + 6][xx] == 's' || t[cy + 6][xx] == ',') t[cy + 6][xx] = deco; }
        }
        for (int i = 0; i < 8; i++) { double a = (i + 0.5) * Math.PI / 4; t[(int) Math.round(cy + 11 * Math.sin(a))][(int) Math.round(c + 11 * Math.cos(a))] = i % 2 == 0 ? 'A' : 'i'; }
        for (int i = 0; i < 16; i++) { double a = (i + 0.5) * Math.PI / 8; int x = (int) Math.round(c + 25 * Math.cos(a)), y = (int) Math.round(cy + 25 * Math.sin(a)); if (Math.abs(x - c) > 5 && Math.abs(y - cy) > 5) t[y][x] = i % 2 == 0 ? 'i' : 'A'; }
        t[cy][c] = 'A'; // 분수 한가운데 여신상
        for (int y = wt + 1; y < riv - 7; y++) for (int x = wt + 1; x < w - wt - 1; x++) if (t[y][x] == 's' && Math.hypot(x - c, y - cy) > 44 && r.nextDouble() < 0.012) t[y][x] = 'T'; // 길가 꽃나무
        // 북문 -- 성벽을 뚫고 탑 입구로
        for (int y = 1; y < wt; y++) for (int x = c - 4; x <= c + 4; x++) t[y][x] = Math.abs(x - c) <= 1 ? ',' : 's';
        t[0][c] = 'G';
        // [2026-11-16] "요새 도시를 요새 도시로, 동문·서문에 중무장한 경비병을 쫙" -- 성벽 안쪽에 덧벽, 성벽을 따라 성탑(L), 동문/서문과 문루.
        for (int y = wt; y < riv - 6; y++) for (int x = wt; x < w - wt; x++) if (x == wt || x == w - wt - 1 || y == wt) t[y][x] = 'W'; // 덧벽
        for (int y = wt + 1; y < riv - 10; y += 14) for (int sg = 0; sg < 2; sg++) { // 동·서 성벽의 성탑(3x3)
            int x0 = sg == 0 ? wt + 1 : w - wt - 4;
            if (Math.abs(y + 1 - cy) < 9) continue;
            for (int yy = y; yy < y + 3; yy++) for (int xx = x0; xx < x0 + 3; xx++) t[yy][xx] = 'L';
        }
        for (int x = wt + 9; x < w - wt - 6; x += 14) { // 북쪽 성벽의 성탑
            if (Math.abs(x + 1 - c) < 9) continue;
            for (int yy = wt + 1; yy < wt + 4; yy++) for (int xx = x; xx < x + 3; xx++) t[yy][xx] = 'L';
        }
        for (int x = 0; x <= wt; x++) for (int y = cy - 1; y <= cy + 1; y++) { t[y][x] = ','; t[y][w - 1 - x] = ','; } // 동문/서문 -- 성벽을 뚫는다
        t[cy][0] = 'G'; t[cy][w - 1] = 'G';
        for (int sg = 0; sg < 2; sg++) for (int gy : new int[] { cy - 5, cy + 3 }) // 문루(문 양옆 성탑) -- 문 앞 길이 좁아진다
            for (int yy = gy; yy < gy + 3; yy++) for (int xx = 0; xx < 3; xx++) t[yy][(sg == 0 ? wt + 1 : w - wt - 4) + xx] = 'L';
        for (int x = wt + 1; x <= wt + 3; x++) for (int y = cy - 2; y <= cy + 2; y++) { t[y][x] = Math.abs(y - cy) <= 1 ? ',' : 's'; t[y][w - 1 - x] = t[y][x]; }
        for (int y = wt; y <= wt + 1; y++) for (int x = c - 4; x <= c + 4; x++) t[y][x] = Math.abs(x - c) <= 1 ? ',' : 's'; // 북문 길(덧벽을 다시 뚫는다)
        int sx = c, sy = h - 4; // 선착장 끝(배에서 내리는 곳)
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = t[y][x] == 'H' ? '~' : 'g';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(c, 0, 'G', TOWER_GATE, GATE_W / 2, GATE_H - 2, "북문 -- 거대한 탑으로 가는 길"));
        ps.add(new Portal(w - 1, cy, 'G', DEMON_EAST, 2, DEMON_H / 2, "동문 -- 동쪽 악마의 들판")); // [2026-11-16]
        ps.add(new Portal(0, cy, 'G', DEMON_WEST, DEMON_W - 3, DEMON_H / 2, "서문 -- 서쪽 악마의 들판"));
        S5MineMap m = new S5MineMap(TOWER_CITY, "요새 도시 엘리시온", false, w, h, sx, sy, t, new byte[h][w], ps);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(npcAtCell(t, c + 2, h - 6, "riverferry", "선착장 뱃사공", "마물의 숲 나루터로 돌아가려나? 언제든 태워 주지."));
        npcs.add(npcAtCell(t, c - 5, riv - 3, "guide", "이동 안내원", "어디로 모실까요? 마을과 마을 사이는 제가 공짜로 데려다 드려요!")); // [2026-11-16] 마을 간 이동
        npcs.add(npcAtCell(t, c + 7, cy - 19, "tokenshop", "증표 상인", "탑에서 모은 증표를 가져오게. 자네 동료(용병)에게 맞는 장비 상자를 열어 주지. 보석도 바꿔 준다네."));
        npcs.add(npcAtCell(t, c - 50, cy - 5, "smith", "장인 회관 대장장이", "탑에서 쓸 장비라면 내게 맡기게."));
        npcs.add(npcAtCell(t, c + 58, cy - 5, "priest", "대신전 사제", "탑을 오르는 이에게 여신의 가호를. 상처를 치료해 드리지요."));
        { Npc wb = wbBoardNear(t, c + 4, cy + 3, "s,"); if (wb != null) npcs.add(wb); } // [2026-11-17] 월드보스 게시판
        { Npc mb = npcNear(t, c - 4, cy + 3, "s,", "mailbox", MAIL_NAME, MAIL_LINE); if (mb != null) npcs.add(mb); } // [2026-11-17] 우체부
        { Npc al = npcNear(t, c - 44, cy - 5, "s,.", "alchemist", "연금술사", ALCHEMIST_LINE); if (al != null) npcs.add(al); } // [2026-11-20] 장인 회관 대장장이 옆
        for (int sg = 0; sg < 2; sg++) { // [2026-11-16] 동문/서문 -- 길 양옆으로 늘어선 중무장 경비병(문마다 열둘) + 수비대장
            int gi = 0;
            for (int k = 0; k < 6; k++) for (int dy = -3; dy <= 3; dy += 6) {
                int gx2 = sg == 0 ? w - wt - 6 - k * 2 : wt + 5 + k * 2;
                guardAt(t, npcs, gx2, cy + dy, "guard", "성문 경비병", GATE_GUARD_LINES[gi++ % GATE_GUARD_LINES.length]);
            }
            guardAt(t, npcs, sg == 0 ? w - wt - 5 : wt + 4, cy - 2, "knight", sg == 0 ? "동문 수비대장" : "서문 수비대장", "성문 밖은 악마들의 땅이오. 탑 29층 수문장을 쓰러뜨린 증표를 가져오면 문을 열어 주겠소. 그 전엔 단 한 사람도 내보낼 수 없소!");
        }
        for (int k = 0; k < 2; k++) for (int sg = -1; sg <= 1; sg += 2) guardAt(t, npcs, c + sg * 3, wt + 3 + k * 3, "guard", "북문 경비병", ROAD_GUARD_LINES[(k * 2 + (sg + 1) / 2) % ROAD_GUARD_LINES.length]);
        m.npcs = npcs;
        m.safe.add(new int[] { c, cy, 80 });
        return m;
    }
    /** [2026-11-15] 탑 입구 -- 위쪽 절반은 하늘까지 솟은 탑의 정면(Q: 화면이 한 장의 큰 그림으로 그린다)과 한가운데 큰 문, 아래는 문으로 이어지는 의식의 길:
     *  모자이크 융단, 양옆 조각상과 수정 가로등, 좌우 반영 연못, 꽃밭과 꽃나무. 남쪽 문 = 탑의 도시 북문. 탑 관리인(층 이동)과 증표 상인이 문 옆에 선다. */
    static S5MineMap generateTowerGate() {
        int w = GATE_W, h = GATE_H, cx = w / 2, dy = GATE_DOOR_Y;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 1 || x >= w - 2 || y >= h - 1 ? 'W' : 's';
        for (int y = 1; y <= dy; y++) for (int x = 5; x <= w - 6; x++) t[y][x] = 'Q'; // 탑 정면
        // [2026-11-17] "탑을 위쪽까지 구경하고 싶어, 옆쪽 샛길 뚫어줘" -- 탑 양옆으로 두 칸 융단 샛길(꼭대기까지), 수정 가로등이 줄지어 서고 맨 위엔 전망대
        for (int y = 1; y <= dy; y++) for (int sg = 0; sg < 2; sg++) { int lx = sg == 0 ? 2 : w - 5; t[y][lx] = ','; t[y][lx + 1] = ','; t[y][sg == 0 ? 4 : w - 3] = y % 6 == 3 ? 'i' : 's'; }
        for (int sg = 0; sg < 2; sg++) for (int x = sg == 0 ? 2 : w - 5; x <= (sg == 0 ? 4 : w - 3); x++) t[1][x] = 's';
        for (int y = dy + 1; y < h - 1; y++) for (int x = cx - 2; x <= cx + 2; x++) t[y][x] = ','; // 융단
        for (int y = dy + 5; y <= dy + 6; y++) for (int x = 2; x < w - 2; x++) if (Math.abs(x - cx) > 2) t[y][x] = ','; // 층계참 띠
        for (int sg = -1; sg <= 1; sg += 2) {
            for (int y = dy + 10; y <= dy + 26; y++) for (int x = cx + sg * 8; x != cx + sg * 23; x += sg) t[y][x] = y == dy + 10 || y == dy + 26 || x == cx + sg * 8 || x == cx + sg * 22 ? 'g' : '~'; // 반영 연못(꽃밭 테두리)
            for (int y = dy + 9; y < h - 3; y += 4) { t[y][cx + sg * 4] = ((y - dy) / 4) % 2 == 0 ? 'A' : 'i'; }
            for (int y = dy + 3; y < h - 3; y += 3) { t[y][cx + sg * 26] = 'T'; t[y + 1][cx + sg * 26] = 'g'; }
            for (int y = dy + 30; y <= dy + 36; y++) for (int x = cx + sg * 9; x != cx + sg * 22; x += sg) t[y][x] = (x + y) % 4 == 0 ? 'T' : ((x * 3 + y) % 5 == 0 ? 'g' : '.'); // 아래쪽 정원
            t[dy + 2][cx + sg * 6] = 'A'; t[dy + 2][cx + sg * 12] = 'i'; t[dy + 2][cx + sg * 18] = 'A'; t[dy + 2][cx + sg * 24] = 'i'; // 문 앞 줄
        }
        t[dy][cx] = 'G';
        t[h - 1][cx] = 'G';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(cx, dy, 'G', towerId(1), 2, 6, "거대한 탑의 문"));
        ps.add(new Portal(cx, h - 1, 'G', TOWER_CITY, TCITY_W / 2, 1, "요새 도시 엘리시온으로"));
        S5MineMap m = new S5MineMap(TOWER_GATE, "거대한 탑 입구", false, w, h, cx, h - 3, t, new byte[h][w], ps);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(npcAtCell(t, cx + 3, dy + 1, "towergate", "탑 관리인", "이 탑은 열 층마다 마을이 있고, 아홉째 층마다 수문장이 지키고 있소. 방을 지나 오른쪽 문으로 나아가다 보면 계단방이 나온다오. 올라 본 층으로는 내가 바로 보내 주겠소."));
        npcs.add(npcAtCell(t, cx - 3, dy + 1, "tokenshop", "증표 상인", "증표를 모아 왔나? 동료 장비 상자를 열어 주지."));
        // [2026-11-16] "북쪽 탑 가는 길도 경비병들과 기사단장이 쏟아지는 악마들을 열심히 막고 있다는 대사" -- 융단 길 양옆으로 늘어선 경비병, 문 앞의 기사단장
        guardAt(t, npcs, cx + 3, dy + 4, "knight", "기사단장", "탑에서 쏟아져 나오는 악마들을 우리 기사단이 여기서 막고 있소! 도시 안으로는 단 한 마리도 들이지 않겠소. 그대는 걱정 말고 탑을 오르시오!");
        int rg = 0;
        for (int y = dy + 8; y < h - 4; y += 5) for (int sg = -1; sg <= 1; sg += 2) guardAt(t, npcs, cx + sg * 3, y, "guard", "기사단 경비병", ROAD_GUARD_LINES[rg++ % ROAD_GUARD_LINES.length]);
        m.npcs = npcs;
        m.safe.add(new int[] { cx, h / 2, 60 });
        return m;
    }
    // ---- [2026-11-16] 악마의 들판 -- "동쪽 필드, 서쪽 필드 추가, 악마형 몬스터, 난이도 어렵게, 한 마리 한 마리가 보스급(스킬, 순간이동, 바닥 불길)".
    //  요새 도시(탑의 도시) 동문/서문 밖. 그을린 땅에 용암 못과 검은 바위, 죽은 나무, 성문에서 이어지는 무너진 길.
    //  성문 경비병은 탑 29층 수문장을 쓰러뜨린 사람만 내보낸다(S55MineServiceImpl.mineMapBlock).
    //  악마는 자리가 정해져 있다(fixedMobs: {x, y, S5MineMobs.DEMON_NAMES 번호}) -- 임프/검사/마도사가 번갈아, 가장 깊은 곳에 발록 한 마리.
    static final int DEMON_W = 96, DEMON_H = 64, TCITY_GATE_Y = 60;
    int[][] fixedMobs;
    public int[][] fixedMobs() { return fixedMobs; }
    static S5MineMap generateDemonField(boolean east, int tier) { // [2026-11-17] tier 0 = 들판 1(요새 도시 성문), 1 = 들판 2, 2 = 들판 3(안쪽 = 최후의 방어선)
        int w = DEMON_W, h = DEMON_H, cy = h / 2, cx = w / 2;
        Random r = new Random(tier == 0 ? (east ? 6661 : 6662) : (east ? 6671 : 6672) + tier * 10);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : '.';
        demonBlobs(t, r, 18 + tier * 5, 12); // 용암 못(~)과 검은 바위(W) 덩어리 -- 깊을수록 더 많이
        for (int y = 3; y < h - 3; y++) for (int x = 3; x < w - 3; x++) if (t[y][x] == '.') { double q = r.nextDouble(); if (q < 0.035) t[y][x] = 'T'; else if (q < 0.10 + tier * 0.03) t[y][x] = ':'; }
        List<Portal> ps = new ArrayList<>();
        int sx, sy;
        String nm = (east ? "동쪽 악마의 들판" : "서쪽 악마의 들판") + DEMON_FIELD_NAME[tier];
        if (tier == 0) {
            int gx = east ? 0 : w - 1;
            for (int x = 1; x < w - 6; x++) { // 성문에서 들판 끝까지 굽이치는 무너진 길
                int xx = east ? x : w - 1 - x, yy = cy + (int) Math.round(Math.sin(x / 9.0) * 4);
                for (int k = -1; k <= 1; k++) t[yy + k][xx] = (x * 7 + k * 3) % 6 == 0 ? ':' : ',';
            }
            for (int y = cy - 3; y <= cy + 3; y++) for (int x = 1; x <= 9; x++) t[y][east ? x : w - 1 - x] = Math.abs(y - cy) <= 1 ? ',' : '.'; // 성문 앞 빈터
            demonRoadV(t, cx, 1, cy); // [2026-11-17] 북쪽 = 들판 2
            t[cy][gx] = 'G'; t[0][cx] = 'G';
            sx = east ? 3 : w - 4; sy = cy;
            ps.add(new Portal(gx, cy, 'G', TOWER_CITY, east ? TCITY_W - 2 : 1, TCITY_GATE_Y, "요새 도시 " + (east ? "동문" : "서문")));
            ps.add(new Portal(cx, 0, 'G', demonFieldId(east, 1), cx, h - 3, (east ? "동쪽" : "서쪽") + " 악마의 들판 2"));
        } else {
            for (int y = h - 9; y <= h - 3; y++) for (int x = cx - 4; x <= cx + 4; x++) if (Math.abs(x - cx) > 1) t[y][x] = '.'; // (길보다 먼저) // 남문 앞 빈터
            demonRoadV(t, cx, tier == 1 ? 1 : cy, h - 2);
            t[h - 1][cx] = 'G';
            sx = cx; sy = h - 3;
            ps.add(new Portal(cx, h - 1, 'G', demonFieldId(east, tier - 1), cx, 2, (east ? "동쪽" : "서쪽") + " 악마의 들판" + DEMON_FIELD_NAME[tier - 1]));
            if (tier == 1) { t[0][cx] = 'G'; ps.add(new Portal(cx, 0, 'G', demonFieldId(east, 2), cx, h - 3, (east ? "동쪽" : "서쪽") + " 악마의 들판 3")); }
            else { // 안쪽(서쪽 들판은 동쪽 끝, 동쪽 들판은 서쪽 끝) = 최후의 방어선
                int ix = east ? 0 : w - 1;
                if (east) demonRoadH(t, cy, 1, cx); else demonRoadH(t, cy, cx, w - 2);
                t[cy][ix] = 'G';
                ps.add(new Portal(ix, cy, 'G', LAST_LINE, east ? LL_W - 3 : 2, LL_H / 2, "최후의 방어선"));
            }
        }
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'W';
        S5MineMap m = new S5MineMap(demonFieldId(east, tier), nm, false, w, h, sx, sy, t, demonOre(t, r, sx, sy), ps);
        int[][] avoid = new int[ps.size()][];
        for (int i = 0; i < ps.size(); i++) avoid[i] = new int[] { ps.get(i).x, ps.get(i).y, 16 };
        m.fixedMobs = demonSpots(t, r, 15, tier, 16, avoid);
        return m;
    }
    // ---- [2026-11-17] 로드맵 -- 들판 2·3, 최후의 방어선, 마계 입구, 혼돈스러운 마계 ----
    private static final double[] DEMON_SHARE = { 0, 24, 0.10, 0.25, 0.30 }; // 별빛석 없음, 미스릴 24칸, 금/은/철 비율
    static final int LL_W = 120, LL_H = 76, ABYSS_W = 41, ABYSS_H = 33, CHAOS_W = 27, CHAOS_H = 21;
    static final String[] DEMON_FIELD_NAME = { "", " 2", " 3" };
    static String demonFieldId(boolean east, int tier) { return (east ? DEMON_EAST : DEMON_WEST) + (tier == 0 ? "" : String.valueOf(tier + 1)); }
    /** 세로로 굽이치는 무너진 길(3칸) -- y0~y1, 가운데 cx. 문 근처(끝 4칸)는 곧게. */
    private static void demonRoadV(char[][] t, int cx, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            int off = (int) Math.round(Math.sin((y - y0) / 7.0) * 4 * Math.min(1.0, Math.min(y - y0, y1 - y) / 8.0)); // 끝으로 갈수록 곧게(문과 이어지게)
            for (int k = -1; k <= 1; k++) t[y][cx + off + k] = (y * 5 + k * 3) % 6 == 0 ? ':' : ',';
        }
    }
    private static void demonRoadH(char[][] t, int cy, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            int off = (int) Math.round(Math.sin((x - x0) / 9.0) * 4 * Math.min(1.0, Math.min(x - x0, x1 - x) / 8.0));
            for (int k = -1; k <= 1; k++) t[cy + off + k][x] = (x * 7 + k * 3) % 6 == 0 ? ':' : ',';
        }
    }
    /** 용암 못 / 검은 바위 덩어리 n개(둘레는 갈라진 땅). */
    private static void demonBlobs(char[][] t, Random r, int n, int margin) {
        int h = t.length, w = t[0].length;
        for (int i = 0; i < n; i++) {
            int bx = margin + r.nextInt(w - margin * 2), by = 5 + r.nextInt(h - 10), rr = 2 + r.nextInt(4);
            char ch = i % 3 == 0 ? 'W' : '~';
            for (int y = by - rr - 1; y <= by + rr + 1; y++) for (int x = bx - rr - 2; x <= bx + rr + 2; x++) {
                if (x < 3 || y < 3 || x >= w - 3 || y >= h - 3) continue;
                double d = Math.hypot((x - bx) * 0.8, y - by) + r.nextDouble() * 0.9;
                if (d < rr) t[y][x] = ch;
                else if (d < rr + 1.3 && t[y][x] == '.') t[y][x] = ':';
            }
        }
    }
    /** 악마 자리 n곳 -- 들어오는 곳(xs, ys)들에서 far칸 넘게, 서로 8칸 넘게, 둘레가 트인 칸. kind = 0~2 번갈아, 가장 먼 곳은 3(발록). 번호 = 단계*4 + kind. */
    private static int[][] demonSpots(char[][] t, Random r, int n, int tier, int far, int[][] avoid) {
        int h = t.length, w = t[0].length;
        List<int[]> spots = new ArrayList<>();
        for (int tries = 0; tries < 8000 && spots.size() < n; tries++) {
            int x = 4 + r.nextInt(w - 8), y = 4 + r.nextInt(h - 8);
            boolean ok = true;
            for (int[] a : avoid) if (Math.abs(a[0] - x) + Math.abs(a[1] - y) < (a.length > 2 ? a[2] : far)) { ok = false; break; }
            for (int[] s : spots) if (Math.max(Math.abs(s[0] - x), Math.abs(s[1] - y)) < 8) { ok = false; break; }
            for (int yy = y - 1; yy <= y + 1 && ok; yy++) for (int xx = x - 1; xx <= x + 1; xx++) if (!walkable(t[yy][xx])) { ok = false; break; }
            if (ok) spots.add(new int[] { x, y, spots.size() % 3 });
        }
        int fi = -1; double fd = -1;
        for (int i = 0; i < spots.size(); i++) { double d = 1e9; for (int[] a : avoid) d = Math.min(d, Math.abs(a[0] - spots.get(i)[0]) + Math.abs(a[1] - spots.get(i)[1])); if (d > fd) { fd = d; fi = i; } }
        if (fi >= 0) spots.get(fi)[2] = 3;
        for (int[] s : spots) s[2] += tier * 4;
        return spots.toArray(new int[0][]);
    }
    private static byte[][] demonOre(char[][] t, Random r, int sx, int sy) {
        int h = t.length, w = t[0].length;
        double[][] score = new double[h][w];
        boolean[][] skip = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { skip[y][x] = Math.abs(x - sx) + Math.abs(y - sy) <= 4 || t[y][x] == ','; score[y][x] = r.nextDouble(); }
        return assignOre(t, score, DEMON_SHARE, skip);
    }
    /** [2026-11-17] 최후의 방어선(120x76, Lv.125) -- 무너진 요새 성벽 한 줄(북쪽, 군데군데 뚫림)과 그 앞 싸움터, 서문(서쪽 들판 3) / 동문(동쪽 들판 3) / 북쪽 = 마계 입구.
     *  남쪽 한가운데 용암 해자에 둘러싸인 외딴 진지(안전지대 -- 좁은 다리 하나로만 이어짐): 지친 병사들, 악마의 광석 조각 정제사, 사제. */
    static S5MineMap generateLastLine() {
        int w = LL_W, h = LL_H, cx = w / 2, cy = h / 2;
        Random r = new Random(7125);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : '.';
        demonBlobs(t, r, 26, 10);
        for (int y = 3; y < h - 3; y++) for (int x = 3; x < w - 3; x++) if (t[y][x] == '.') { double q = r.nextDouble(); if (q < 0.03) t[y][x] = 'T'; else if (q < 0.12) t[y][x] = ':'; }
        int wy = 15; // 무너진 성벽(돌 성벽 L, 뚫린 곳은 잔해)
        for (int x = 2; x < w - 2; x++) { boolean gap = Math.abs(x - cx) <= 2 || (x * 37 % 23 == 0) || (x % 29 >= 26); t[wy][x] = gap ? ':' : 'L'; t[wy + 1][x] = gap ? '.' : (x % 9 == 0 ? 'L' : t[wy + 1][x]); }
        demonRoadH(t, cy, 1, w - 2);
        demonRoadV(t, cx, 1, cy);
        // 외딴 진지 -- 남쪽 가운데, 반지름 8 둥근 돌 바닥 + 둘레 성벽(L), 그 밖 용암 해자, 북쪽 다리(H) 하나
        int ky = h - 14, kr = 8;
        for (int y = ky - kr - 4; y <= ky + kr + 4; y++) for (int x = cx - kr - 5; x <= cx + kr + 5; x++) {
            if (x < 3 || y < 3 || x >= w - 3 || y >= h - 3) continue;
            double d = Math.hypot((x - cx) * 0.85, y - ky);
            t[y][x] = d <= kr - 1 ? 's' : d <= kr + 0.3 ? 'L' : d <= kr + 3.2 ? '~' : t[y][x];
        }
        for (int y = cy + 2; y <= ky - kr + 1; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = y >= ky - kr - 3 ? 'H' : ',';
        for (int y = ky - kr; y <= ky - kr + 1; y++) t[y][cx] = 's';
        t[ky + 2][cx - 3] = 'i'; t[ky + 2][cx + 3] = 'i'; // 횃불(수정 가로등 칸 -- 화면이 불꽃으로)
        for (int y = 4; y <= 11; y++) for (int x = cx - 6; x <= cx + 6; x++) if (Math.hypot((x - cx) * 0.8, y - 7.5) < 5.2) t[y][x] = Math.abs(x - cx) <= 1 ? ',' : '.'; // [2026-11-17] 보로네오의 빈터(마계 입구로 가는 길 위)
        t[cy][0] = 'G'; t[cy][w - 1] = 'G'; t[0][cx] = 'G';
        int sx = cx, sy = ky + 1;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'W';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(0, cy, 'G', DEMON_WEST3, DEMON_W - 3, DEMON_H / 2, "서쪽 악마의 들판 3"));
        ps.add(new Portal(w - 1, cy, 'G', DEMON_EAST3, 2, DEMON_H / 2, "동쪽 악마의 들판 3"));
        ps.add(new Portal(cx, 0, 'G', ABYSS_GATE, ABYSS_W / 2, ABYSS_H - 3, "마계 입구"));
        S5MineMap m = new S5MineMap(LAST_LINE, "최후의 방어선", false, w, h, sx, sy, t, demonOre(t, r, sx, sy), ps);
        m.fixedMobs = demonSpots(t, r, 18, 3, 14, new int[][] { { 2, cy, 18 }, { w - 3, cy, 18 }, { cx, ky, kr + 14 }, { cx, 2, 12 }, { cx, 7, 14 } });
        m.bossX = cx - 1; m.bossY = 7; // [2026-11-17] 심판자 보로네오(2x2 왼쪽 위)
        List<Npc> npcs = new ArrayList<>();
        npcs.add(npcAtCell(t, cx - 3, ky - 2, "refiner", "악마 광석 정제사", "악마의 광석 조각을 가져오게. 불순물을 태워 내면 열에 하나쯤은 미스릴이 남지. ...마계의 문 얘기? 대개는 엉뚱한 땅에 내동댕이쳐지지만, 아주 가끔 별 하나 없는 어둠 속 '혼돈스러운 마계'로 떨어진다더군. 악마가 된 괴물을 쓰러뜨려야만 돌아올 수 있고, 차원이 뒤틀린 곳이라 반지 따위의 힘은 닿지 않는다지."));
        npcs.add(npcAtCell(t, cx + 3, ky - 2, "priest", "종군 사제", "보급이 끊긴 지 오래지만, 상처를 돌봐 드릴 힘은 남아 있습니다."));
        npcs.add(npcAtCell(t, cx + 6, ky - 1, "primalsmith", "태초의 보석 세공사", "마계의 정수와 거룡의 심장, 이 땅의 온갖 광석과 보석... 그 모두를 하나로 녹이면 태초의 빛이 깃든 보석이 되지.")); // [2026-11-20]
        guardAt(t, npcs, cx - 1, ky - kr + 2, "knight", "방어선 지휘관", "여기가 최후의 방어선이오. 사방이 악마요 -- 후방과 연락이 끊긴 지 벌써 몇 주째라오. 그래도 이 진지만큼은 내주지 않겠소!");
        for (int i = 0; i < LAST_GUARD_LINES.length; i++) guardAt(t, npcs, cx - 5 + (i % 3) * 5, ky + 3 + (i / 3) * 2, "guard", "지친 병사", LAST_GUARD_LINES[i]);
        m.npcs = npcs;
        m.safe.add(new int[] { cx, ky, kr });
        return m;
    }
    static final String[] LAST_GUARD_LINES = {
        "보급 마차가 끊긴 지 오래요... 남은 건 이 해자와 성벽뿐이오.",
        "밤마다 북쪽 하늘이 붉게 갈라진다오. 마계의 문이 숨을 쉬는 소리지.",
        "서쪽과 동쪽 들판의 인장을 둘 다 가져온 용사라니, 오랜만에 반가운 얼굴이구려.",
        "북쪽 하늘이 붉게 물들면 놈들이 한꺼번에 몰려온다오. 그때만큼은 진지 밖에 나서지 마시오.",
        "이 진지 안에서는 놈들도 다리를 건너오지 못하오. 잠시 쉬어 가시오." };
    /** [2026-11-17] 마계 입구(41x33) -- 갈라진 검붉은 땅 한가운데 거대한 포털(화면이 소용돌이 그림으로 -- 칸 하나가 문, 둘레 돌기둥). 남쪽 = 최후의 방어선.
     *  포털: 전체 지도 아무 곳으로 떨어지거나, 5% 확률로 혼돈스러운 마계(S55MineServiceImpl.mineAbyssWarp). */
    static S5MineMap generateAbyssGate() {
        int w = ABYSS_W, h = ABYSS_H, cx = w / 2, py = 12;
        Random r = new Random(7135);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : (r.nextDouble() < 0.07 ? ':' : '.');
        for (int y = 2; y < h - 2; y++) for (int x = 2; x < w - 2; x++) { double d = Math.hypot(x - cx, (y - py) * 1.2); if (d > 13 && d < 15.5 && Math.abs(x - cx) > 2) t[y][x] = '~'; } // 용암 고리(남쪽은 길)
        for (int y = py + 1; y < h - 1; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = ',';
        for (int a = 0; a < 360; a += 30) { if (a > 60 && a < 120) continue; int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * 5), y = py + (int) Math.round(Math.sin(Math.toRadians(a)) * 4); t[y][x] = 'W'; } // 돌기둥(남쪽 빼고)
        for (int y = py - 2; y <= py; y++) for (int x = cx - 2; x <= cx + 2; x++) t[y][x] = 'W'; // 포털 몸체(지나갈 수 없음)
        t[py][cx] = 'G';
        t[h - 1][cx] = 'G';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(cx, py, 'G', CHAOS, CHAOS_W / 2, CHAOS_H / 2 + 5, "마계로 통하는 문"));
        ps.add(new Portal(cx, h - 1, 'G', LAST_LINE, LL_W / 2, 2, "최후의 방어선으로"));
        S5MineMap m = new S5MineMap(ABYSS_GATE, "마계 입구", false, w, h, cx, h - 3, t, new byte[h][w], ps);
        m.abyssPortal = new int[] { cx, py };
        return m;
    }
    int[] abyssPortal;
    public int[] abyssPortal() { return abyssPortal; }
    /** [2026-11-17] 혼돈스러운 마계(27x21, 1인) -- 검은 우주에 떠 있는 바위섬 하나. 악마화된 몬스터 한 마리와 싸운다(들어올 때 무작위로 깨어난다 -- 자리는 모든 종류가 한 칸에, 평소엔 없음).
     *  쓰러뜨리면 남쪽 끝 귀환의 문(요새 도시)이 열린다. */
    static S5MineMap generateChaos() {
        int w = CHAOS_W, h = CHAOS_H, cx = w / 2, cy = h / 2;
        Random r = new Random(7136);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double d = Math.hypot((x - cx) / 10.5, (y - cy) / 7.5) + (r.nextDouble() - 0.5) * 0.12;
            t[y][x] = d < 1 ? (r.nextDouble() < 0.05 ? ':' : '.') : '^';
        }
        t[cy + 7][cx] = '.'; t[cy + 8][cx] = 'G';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(cx, cy + 8, 'G', TOWER_CITY, TCITY_W / 2, TCITY_H / 2, "귀환의 문 -- 요새 도시로"));
        S5MineMap m = new S5MineMap(CHAOS, "혼돈스러운 마계", false, w, h, cx, cy + 5, t, new byte[h][w], ps);
        m.chaosSpot = new int[] { cx, cy - 2 };
        return m;
    }
    /** [2026-11-17] 마계 마을(71x52) -- "마계 마을도 웅장하게". 북쪽 절반은 하늘을 찌르는 마왕성 정면(Q: 화면이 한 장의 큰 그림으로),
     *  성 앞 흑요석 광장에서 남쪽으로 뻗은 대로, 대로 양옆 용암 수로와 뼈 다리, 뿔 달린 석상과 보랏빛 화톳불이 줄지어 서고, 그 바깥으로 뾰족 지붕 집들.
     *  나가는 문은 없다 -- 마계 입구 포털로 가끔 떨어지고, 3초 뒤 요새 도시로 되돌아간다(S55MineServiceImpl.MINE_VILLAGE_MS). 몬스터 없음, 마계 주민만. */
    /** [2026-11-18] 바다 위(96x200, 이름 미정): 온통 바다(배로만 -- boat, 1인용 배 필요), 군데군데 바위섬(^)과 모래톱(b). 맨 위 가운데 = 남쪽 바닷가 부두, 맨 아래 가운데 = 해상도시 부두.
     *  가운데 뱃길(좌우 4칸)은 바위 없이 비워 둔다. 해상전투(바다 몬스터, 배 체력)와 바다 보스는 다음에. */
    // [2026-11-18] 같은 날 다시 -- "바다도시에서 우측 가면 좌우로 긴 맵, 물보스는 가운데 고정형인데 전 맵을 움직이도록, 맵에 전체적으로 등대, 등대 기믹"(시안 승인) --
    //  200x60. 서쪽 부두 = 해상도시, 오른쪽 끝(닿으면) = 남쪽 바닷가 7시 끝. 크라켄은 seaRoute(가운데 칸들을 잇는 고리)를 따라 돈다(서버 mineKrakenMove).
    //  길 둘레 KRK_LANE칸은 탁 트인 바다(바위 없음), 등대 여섯은 길에서 LH_OFF칸 떨어진 바위섬(3x2, 탑 + 자갈), 화약통 열은 길 바로 옆.
    static final int KRK_LANE = 4, LH_OFF = 8;
    static final int[][] OPEN_SEA_ROUTE = { { 24, 30 }, { 46, 13 }, { 78, 27 }, { 100, 46 }, { 120, 26 }, { 148, 13 }, { 176, 30 }, { 150, 48 }, { 110, 36 }, { 70, 48 }, { 38, 44 } };
    static S5MineMap generateOpenSea() {
        int w = 200, h = 60;
        Noise isle = new Noise(918_190L);
        Random r = new Random(9_181_900L);
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double v = isle.fbm(x / 7.0, y / 7.0);
            t[y][x] = y < 3 || y > h - 4 || x > w - 8 ? '~' : v > 0.76 ? '^' : r.nextDouble() < 0.002 ? '^' : '~';
        }
        int[][] route = OPEN_SEA_ROUTE;
        int n = route.length;
        for (int i = 0; i < n; i++) { // 크라켄 길 -- 둘레를 바다로
            int[] a = route[i], b = route[(i + 1) % n];
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int s = 0; s <= steps; s++) {
                int px = a[0] + (b[0] - a[0]) * s / steps, py = a[1] + (b[1] - a[1]) * s / steps;
                for (int dy = -KRK_LANE; dy <= KRK_LANE; dy++) for (int dx = -KRK_LANE; dx <= KRK_LANE; dx++) { int xx = px + dx, yy = py + dy; if (xx > 0 && yy > 0 && xx < w - 1 && yy < h - 1) t[yy][xx] = '~'; }
            }
        }
        List<int[]> lh = new ArrayList<>(); // 등대 -- 길 마디 하나 걸러 그 마디 가운데에서 바깥쪽으로 LH_OFF칸
        for (int i = 0; i < n && lh.size() < 6; i += 2) {
            int[] a = route[i], b = route[(i + 1) % n];
            double mx = (a[0] + b[0]) / 2.0, my = (a[1] + b[1]) / 2.0, dx = b[0] - a[0], dy = b[1] - a[1], len = Math.max(1, Math.hypot(dx, dy));
            for (int side : new int[] { (i / 2) % 2 == 0 ? 1 : -1, (i / 2) % 2 == 0 ? -1 : 1 }) {
                int lx = (int) Math.round(mx - dy / len * LH_OFF * side), ly = (int) Math.round(my + dx / len * LH_OFF * side);
                if (lx < 6 || ly < 5 || lx > w - 12 || ly > h - 6 || routeDist(route, lx, ly) < KRK_LANE + 3) continue;
                lh.add(new int[] { lx, ly });
                break;
            }
        }
        for (int[] l : lh) {
            for (int dy = -3; dy <= 4; dy++) for (int dx = -3; dx <= 4; dx++) t[l[1] + dy][l[0] + dx] = '~';
            for (int dy = 0; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) t[l[1] + dy][l[0] + dx] = 'r'; // 섬(탑 칸은 NPC가 N으로)
        }
        int[][] barrels = new int[10][]; // 화약통 -- 길 마디마다 길 바로 옆(몸 밖) 물 위
        for (int k = 0; k < barrels.length; k++) {
            int[] a = route[k % n], b = route[(k + 1) % n];
            double q = k % 2 == 0 ? 0.35 : 0.7, dx = b[0] - a[0], dy = b[1] - a[1], len = Math.max(1, Math.hypot(dx, dy));
            int side = k % 3 == 0 ? -1 : 1;
            barrels[k] = new int[] { (int) Math.round(a[0] + dx * q - dy / len * 4 * side), (int) Math.round(a[1] + dy * q + dx / len * 4 * side) };
            t[barrels[k][1]][barrels[k][0]] = '~';
        }
        List<Portal> ps = new ArrayList<>();
        int dkY = OPEN_SEA_DOCK_Y;
        for (int y = dkY - 4; y <= dkY + 4; y++) for (int x = 0; x < 7; x++) t[y][x] = '~';
        for (int y = dkY - 2; y <= dkY + 2; y++) t[y][0] = 'b';
        t[dkY][1] = 'B';
        ps.add(new Portal(1, dkY, 'B', SEA_CITY, 0, 0, "바다마을 윤슬항에 내리기"));
        for (int y = 1; y < h - 1; y++) { t[y][w - 1] = 'o'; ps.add(new Portal(w - 1, y, 'o', BEACH, 0, 0, "남쪽 바닷가로")); } // 오른쪽 끝(닿으면) = 남쪽 바닷가 7시 끝
        S5MineMap m = new S5MineMap(OPEN_SEA, "바다 위", false, w, h, 4, dkY, t, new byte[h][w], ps);
        m.boat = true;
        m.requireItem = BOAT_ITEM;
        m.bossX = route[0][0] - 2; m.bossY = route[0][1] - 2; // 크라켄 5x5 왼쪽 위(집 = 길 첫 마디)
        m.seaBarrels = barrels;
        m.seaRoute = route;
        m.lighthouses = lh.toArray(new int[0][]);
        return m;
    }
    /** 칸 (x, y)에서 크라켄 길(고리)까지 가장 가까운 거리. */
    static double routeDist(int[][] route, double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < route.length; i++) {
            int[] a = route[i], b = route[(i + 1) % route.length];
            double dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy, q = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / l2));
            best = Math.min(best, Math.hypot(x - a[0] - dx * q, y - a[1] - dy * q));
        }
        return best;
    }
    /** [2026-11-18] 그 열(x)에서 위에서부터 처음 나오는 물(~) 줄. */
    private static int firstWater(S5MineMap m, int x) { for (int y = 1; y < m.h - 1; y++) if (m.terrain[y][x] == '~') return y; return m.h - 2; }
    /** [2026-11-18] (x, y)에서 가장 가까운 물(~) 칸(배를 탄 채 나오는 자리). */
    private static int[] waterNear(S5MineMap m, int x, int y) {
        for (int r = 0; r < 20; r++) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
            int xx = x + dx, yy = y + dy;
            if (xx > 0 && yy > 0 && xx < m.w - 1 && yy < m.h - 1 && m.terrain[yy][xx] == '~') return new int[] { xx, yy };
        }
        return new int[] { m.spawnX, m.spawnY };
    }
    /** [2026-11-18] 맵 끝 한 줄(vertical이면 x = fixed인 열, 아니면 y = fixed인 줄)의 물 칸을 넘어가는 칸(o)으로 -- 대상 맵의 to 자리로. */
    private static void edgeGate(S5MineMap m, List<Portal> ps, boolean vertical, int fixed, int from, int to, String target, int[] at, String label) {
        for (int i = from; i <= to; i++) {
            int x = vertical ? fixed : i, y = vertical ? i : fixed;
            if (m.terrain[y][x] != '~') continue;
            m.terrain[y][x] = 'o';
            ps.add(new Portal(x, y, 'o', target, at[0], at[1], label));
        }
    }
    static final int BEACH_GATE = 14; // [2026-11-18] 남쪽 바닷가 7시/5시 끝 -- 옆 끝 아래 14칸 + 맨 아래 끝 14칸
    static final int OPEN_SEA_DOCK_Y = 30; // [2026-11-18] 바다 위 서쪽 부두 줄(해상도시), 66 -> 30(좌우로 긴 맵)
    /** [2026-11-18] 망망대해(80x80) -- 끝없는 바다, 나가는 문 없음, 몬스터 없음. 배로만(1인용 배). */
    static S5MineMap generateVastSea() {
        int w = 80, h = 80;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = '~';
        S5MineMap m = new S5MineMap(VAST_SEA, "망망대해", false, w, h, w / 2, h / 2, t, new byte[h][w], new ArrayList<Portal>());
        m.boat = true;
        m.requireItem = BOAT_ITEM;
        return m;
    }
    /** 그 열(x)에서 위에서부터 처음 만나는 물가 물 칸(바로 위가 걸을 수 있는 칸) {x, y}. */
    private static int[] shoreWater(S5MineMap m, int x) {
        for (int y = 2; y < m.h - 2; y++) if (m.terrain[y][x] == '~' && walkable(m.terrain[y - 1][x])) return new int[] { x, y };
        return new int[] { x, m.h - 3 };
    }
    /** 배 맵 to에서 from으로 가는 부두 판자 바로 옆 물 칸(from에서 배를 띄우면 여기로). */
    private static int[] landBeside(S5MineMap to, String from) {
        for (Portal p : to.portals) if (from.equals(p.target)) for (int[] d : new int[][] { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } })
            if (to.inside(p.x + d[0], p.y + d[1]) && to.terrain[p.y + d[1]][p.x + d[0]] == '~') return new int[] { p.x + d[0], p.y + d[1] };
        return new int[] { to.spawnX, to.spawnY };
    }
    public static int[] sailLanding(String from, String to) { return landBeside(get(to), from); }
    /** [2026-11-18] 해상도시(112x84, 바다 위): "둥글게 만들지 마" -- 크기가 제각각인 네모난 목재 부두(H) 일곱 곳을 두 칸 널다리로 엮은 도시.
     *  남쪽 돌 항구 광장(s) 아래로 부두(P)와 매어 둔 배(q), 배에 오르는 판자(B) = 다도해 뱃길. 가운데 시장 데크(앞줄 상점, 마을 바닥 V 광장),
     *  북쪽 시청(큰 집), 서쪽/동쪽 주거 데크, 남서/남동 작은 데크. 부두 사이는 바다(~). 몬스터 없음, 온 도시가 안전지대. */
    static S5MineMap generateSeaCity() {
        int w = 112, h = 84;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = '~';
        int[][] decks = { { 38, 56, 73, 67 }, { 28, 34, 81, 49 }, { 42, 6, 69, 26 }, { 4, 12, 22, 42 }, { 88, 18, 107, 50 }, { 8, 52, 24, 64 }, { 86, 60, 104, 72 } };
        for (int i = 0; i < decks.length; i++) for (int y = decks[i][1]; y <= decks[i][3]; y++) for (int x = decks[i][0]; x <= decks[i][2]; x++) t[y][x] = i == 0 ? 's' : 'H';
        int[][] walks = { { 55, 50, 56, 55 }, { 55, 27, 56, 33 }, { 23, 38, 27, 39 }, { 82, 42, 87, 43 }, { 13, 43, 14, 51 }, { 25, 58, 37, 59 },
                { 74, 64, 85, 65 }, { 95, 51, 96, 59 }, { 23, 16, 41, 17 }, { 70, 20, 87, 21 } }; // 널다리 {x0, y0, x1, y1}
        for (int[] k : walks) for (int y = k[1]; y <= k[3]; y++) for (int x = k[0]; x <= k[2]; x++) t[y][x] = 'H';
        for (int y = 41; y <= 47; y++) for (int x = 44; x <= 67; x++) t[y][x] = 'V'; // 시장 광장
        int[][] houses = { { 48, 8, 63, 13 }, // 시청
                { 6, 14, 10, 16 }, { 15, 14, 19, 16 }, { 6, 20, 10, 22 }, { 15, 20, 19, 22 }, { 6, 26, 10, 28 }, { 15, 26, 19, 28 }, { 6, 32, 10, 34 }, { 15, 32, 19, 34 },
                { 90, 23, 94, 25 }, { 100, 23, 104, 25 }, { 90, 29, 94, 31 }, { 100, 29, 104, 31 }, { 90, 35, 94, 37 }, { 100, 35, 104, 37 }, { 90, 45, 94, 47 }, { 100, 45, 104, 47 },
                { 31, 35, 35, 37 }, { 38, 35, 42, 37 }, { 45, 35, 49, 37 }, { 62, 35, 66, 37 }, { 69, 35, 73, 37 }, { 76, 35, 80, 37 }, // 시장 앞줄 상점
                { 41, 57, 45, 59 }, { 66, 57, 70, 59 }, { 12, 54, 16, 56 }, { 89, 62, 93, 64 }, { 97, 62, 101, 64 } };
        for (int[] k : houses) for (int y = k[1]; y <= k[3]; y++) for (int x = k[0]; x <= k[2]; x++) t[y][x] = 'K';
        int[] plank = pierAndShip(t, 54, 68, 1); // 부두(P) + 매어 둔 배(q) + 판자(B)
        for (int y = 33; y <= 35; y++) for (int x = 108; x <= 109; x++) t[y][x] = 'P'; // [2026-11-18] 동쪽 주거 데크 오른쪽 부두 -- 끝 판자 = 바다 위
        t[34][110] = 'B';
        int sx = 55, sy = 62;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = '~';
        List<Portal> ps = new ArrayList<>();
        ps.add(new Portal(plank[0], plank[1], 'B', OPEN_SEA, 0, 0, "배를 띄워 바다 위로"));
        ps.add(new Portal(110, 34, 'B', OPEN_SEA, 0, 0, "배를 띄워 바다 위로"));
        S5MineMap m = new S5MineMap(SEA_CITY, "바다마을 윤슬항", false, w, h, sx, sy, t, new byte[h][w], ps);
        m.dockX = plank[0] - 1;
        m.dockY = plank[1];
        m.villageX = 55; m.villageY = 44;
        m.safe.add(new int[] { 56, 42, 90 });
        return m;
    }
    static S5MineMap generateDemonVillage() {
        int w = 71, h = 52, cx = w / 2, py = 19;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 1 || x >= w - 2 || y >= h - 1 ? 'W' : '.';
        for (int y = 1; y <= py; y++) for (int x = 2; x <= w - 3; x++) t[y][x] = x >= 6 && x <= w - 7 ? 'Q' : 'W'; // 마왕성 정면(양옆은 바위 절벽)
        for (int y = py + 1; y <= py + 4; y++) for (int x = 3; x <= w - 4; x++) t[y][x] = ','; // 성 앞 광장
        for (int y = py + 5; y < h - 1; y++) for (int x = cx - 3; x <= cx + 3; x++) t[y][x] = ','; // 대로
        for (int y = py + 6; y < h - 3; y++) for (int sg = -1; sg <= 1; sg += 2) for (int k = 5; k <= 6; k++) t[y][cx + sg * k] = (y - py) % 8 == 2 ? 'H' : '~'; // 용암 수로 + 뼈 다리
        for (int y = py + 6; y < h - 3; y += 3) for (int sg = -1; sg <= 1; sg += 2) t[y][cx + sg * 4] = ((y - py) / 3) % 2 == 0 ? 'A' : 'i'; // 석상 / 화톳불
        for (int sg = -1; sg <= 1; sg += 2) { t[py + 2][cx + sg * 9] = 'A'; t[py + 2][cx + sg * 15] = 'i'; t[py + 2][cx + sg * 21] = 'A'; t[py + 2][cx + sg * 27] = 'i'; }
        int[][] houses = { { 10, 27 }, { 19, 27 }, { 10, 36 }, { 19, 36 }, { 10, 44 }, { 19, 44 } };
        for (int[] hs : houses) for (int sg = 0; sg < 2; sg++) {
            int hx = sg == 0 ? hs[0] : w - 1 - hs[0] - 6;
            for (int y = hs[1]; y < hs[1] + 4 && y < h - 2; y++) for (int x = hx; x < hx + 7; x++) t[y][x] = 'K';
            for (int x = hx; x < hx + 7; x++) if (hs[1] + 4 < h - 1) t[hs[1] + 4][x] = ','; // 집 앞 길
        }
        for (int y = py + 5; y < h - 1; y++) for (int x = 3; x < w - 3; x++) if (t[y][x] == '.' && (x * 7 + y * 13) % 29 == 0) t[y][x] = ':';
        List<Portal> ps = new ArrayList<>();
        byte[][] vo = new byte[h][w]; // [2026-11-18] "마계 마을에서 채광하면 정제된 마석" -- 풀/돌 바닥(. :)은 캘 수 있다(칸은 구리 광석 -- 덤으로 구리, 진짜 보상은 S55MineServiceImpl.mineDigCell)
        for (int y = py + 5; y < h - 1; y++) for (int x = 2; x < w - 2; x++) if (t[y][x] == '.' || t[y][x] == ':') vo[y][x] = 1;
        S5MineMap m = new S5MineMap(DEMON_VILLAGE, "마계 마을", false, w, h, cx, py + 3, t, vo, ps);
        List<Npc> npcs = new ArrayList<>();
        String[] lines = { "...인간? 이런 곳까지 어떻게 흘러들어 왔지?", "마왕 폐하의 성을 올려다보는 건 자유다. 들어갈 생각은 하지 마라.", "곧 문이 너를 뱉어 낼 거다. 운 좋은 줄 알아라.",
            "인간의 냄새... 오랜만이군.", "이 수로의 불은 천 년째 꺼진 적이 없지.", "여긴 네가 올 곳이 아니야." };
        int[][] at = { { cx - 8, py + 3 }, { cx + 8, py + 3 }, { cx - 2, py + 9 }, { cx + 2, py + 15 }, { cx - 2, py + 21 }, { cx + 2, py + 27 } };
        for (int i = 0; i < at.length; i++) if (t[at[i][1]][at[i][0]] == ',') npcs.add(npcAtCell(t, at[i][0], at[i][1], "demonfolk", "마계 주민", lines[i]));
        m.npcs = npcs;
        return m;
    }
    int[] chaosSpot;
    public int[] chaosSpot() { return chaosSpot; }
    /** [2026-11-17] 월드보스 게시판 -- (x0, y0) 둘레에서 비어 있는 바닥 칸(둘레가 트인 곳)을 찾아 세운다. */
    static final String ALCHEMIST_LINE = "흔한 광석 열 덩이를 녹이면 한 단계 귀한 광석 한 덩이가 남지. 구리는 철로, 철은 은으로, 은은 금으로. 그 위는... 연금술로도 안 되더군."; // [2026-11-20]
    static final String MAIL_NAME = "우체부", MAIL_LINE = "편지 왔어요~ 받을 우편이 있으면 바로 건네 드릴게요!";
    static final String WB_BOARD_LINE = "월드보스 소식이 빼곡히 붙어 있는 게시판이에요.";
    static Npc wbBoardNear(char[][] t, int x0, int y0, String ground) { return npcNear(t, x0, y0, ground, "wbboard", "월드보스 게시판", WB_BOARD_LINE); }
    /** [2026-11-17] (x0, y0) 둘레의 비어 있는(둘레가 트인) 바닥 칸에 NPC -- 게시판, 우체부. */
    static Npc npcNear(char[][] t, int x0, int y0, String ground, String kind, String name, String line) {
        for (int r = 2; r <= 12; r++) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
            int x = x0 + dx, y = y0 + dy;
            if (y < 1 || x < 1 || y >= t.length - 1 || x >= t[0].length - 1 || ground.indexOf(t[y][x]) < 0) continue;
            int open = 0;
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) if (ground.indexOf(t[y + d[1]][x + d[0]]) >= 0) open++;
            if (open < 4) continue;
            return npcAtCell(t, x, y, kind, name, line);
        }
        return null;
    }
    /** (x, y)가 걸을 수 있는 바닥이면 그 자리에 경비병/기사를 세운다(아니면 건너뛴다). */
    private static void guardAt(char[][] t, List<Npc> npcs, int x, int y, String kind, String name, String line) {
        if (y < 0 || x < 0 || y >= t.length || x >= t[0].length || (t[y][x] != 's' && t[y][x] != ',' && t[y][x] != '.')) return;
        npcs.add(npcAtCell(t, x, y, kind, name, line));
    }
    static final String[] GATE_GUARD_LINES = {
        "멈추시오! 성문 밖은 악마들의 땅이오. 탑 29층 수문장을 쓰러뜨린 증표가 있어야 문을 열어 드릴 수 있소.",
        "악마 한 마리가 탑의 수문장 하나만큼 강하다 하오. 부디 얕보지 마시오.",
        "놈들은 눈 깜짝할 새 등 뒤에 나타나고, 발밑에 불길을 일으킨다오.",
        "이 갑옷을 입고도 등골이 서늘하오. 문 너머에서 밤마다 울음소리가 들린다오.",
        "증표를 가진 용사라면 막지 않겠소. 살아서 돌아오시오." };
    static final String[] ROAD_GUARD_LINES = {
        "탑 문이 열릴 때마다 악마들이 쏟아져 나온다오. 우린 한 발짝도 물러서지 않소!",
        "기사단장님이 밤낮없이 앞장서 싸우고 계시오. 우리도 질 수 없지!",
        "동문과 서문 밖은 이미 악마들 차지요. 이 길만큼은 우리가 지켜 내겠소.",
        "방금도 한 무리를 막아 냈소. 숨 돌릴 틈이 없구려.",
        "탑을 오르는 용사들 덕에 놈들의 기세가 조금은 꺾였다오. 부디 힘내 주시오!" };
    /** 칸 (x, y)에 NPC를 세운다(그 칸 바닥을 기억하고 N으로). */
    private static Npc npcAtCell(char[][] t, int x, int y, String kind, String name, String line) {
        Npc n = new Npc(x, y, kind, name, line);
        n.ground = t[y][x];
        t[y][x] = 'N';
        return n;
    }
    /** [2026-11-15] 탑 한 층 -- 방(1~8), 보스(9), 마을(10). 같은 날 "주사위를 굴리는 건 안 해도 된다, 마물의 숲처럼 한 개 방을 반복 표현하는 방식으로,
     *  오른쪽으로만 이동(계단방은 위아래, 왼쪽은 탑 나가기), 오른쪽으로 가는 순간 주사위 굴리는 판정" -- 보드(칸 고리)를 없애고 층마다 방 하나(tw{층})와 계단방 하나(tw{층}s).
     *  방의 오른쪽 문을 지나면 같은 방으로 다시 들어오고 그 순간 무슨 방인지 정해진다(S55MineServiceImpl.mineTowerNext -- 전투/보물/증표/함정/행운/빈 방),
     *  towerRooms(층)번 지나면 계단방. 문/계단의 뜻은 S55MineServiceImpl.mineTowerPortal이 정한다(포탈의 목적지 좌표는 쓰지 않는다). */
    static S5MineMap generateTowerFloor(int f) {
        if (towerTownFloor(f)) return towerTown(f);
        if (towerBossFloor(f)) return towerBoss(f);
        return towerRoom(f, false);
    }
    static final int TROOM_W = 23, TROOM_H = 13;
    /** 탑 방(stair = false: 왼쪽 문 = 탑 나가기, 오른쪽 문 = 다음 방) / 계단방(stair = true: 위 계단 = 다음 층, 아래 계단 = 아래층, 왼쪽 문 = 탑 나가기). */
    static S5MineMap towerRoom(int f, boolean stair) {
        int w = TROOM_W, h = TROOM_H, cx = w / 2, cy = h / 2;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : 's';
        for (int x = 1; x < (stair ? cx + 1 : w - 1); x++) t[cy][x] = ','; // 융단 -- 왼쪽 문에서 오른쪽 문(계단방은 가운데)까지
        for (int[] p : new int[][] { { 5, 3 }, { 5, h - 4 }, { 11, 3 }, { 11, h - 4 }, { 17, 3 }, { 17, h - 4 } }) t[p[1]][p[0]] = 'L'; // 기둥
        List<Portal> ps = new ArrayList<>();
        t[cy][0] = 'G';
        ps.add(new Portal(0, cy, 'G', TOWER_GATE, GATE_W / 2, GATE_DOOR_Y + 1, "탑 밖으로 나가는 문"));
        if (!stair) {
            t[cy][w - 1] = 'G';
            ps.add(new Portal(w - 1, cy, 'G', towerId(f), 2, cy, "다음 방으로 가는 문"));
        } else {
            for (int y = 1; y < cy + 1; y++) t[y][cx] = ','; // [2026-11-16] "계단방에서 아래층은 못 가게" -- 계단방엔 위 계단과 왼쪽 문뿐(아래층은 층 이동 / 수문장 층·마을의 아래 계단으로)
            t[0][cx] = 'U';
            ps.add(new Portal(cx, 0, 'U', f + 1 <= TOWER_FLOORS ? towerId(f + 1) : null, 0, 0, (f + 1) + "층으로 올라가는 계단"));
        }
        S5MineMap m = new S5MineMap(stair ? towerStairId(f) : towerId(f), "탑 " + f + "층" + (stair ? " 계단방" : ""), false, w, h, 2, cy, t, new byte[h][w], ps);
        if (!stair) m.twSpots = new int[][] { { w - 8, cy - 3 }, { w - 8, cy }, { w - 8, cy + 3 } }; // 전투방이 되면 이 가운데 한 마리가 깨어난다
        return m;
    }
    /** 보스 층(9, 19, 29) -- 돌 광장 가운데 수문장. 아래 계단 = 아래층, 위 계단 = 다음 층 마을(수문장을 쓰러뜨려야), 왼쪽 문 = 탑 나가기. */
    static S5MineMap towerBoss(int f) {
        int w = 32, h = 28, cx = w / 2, cy = h / 2 - 2;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : ((x * 3 + y) % 9 == 0 ? ':' : 's');
        for (int y = 1; y < h - 1; y++) if (y < cy - 2 || y > cy + 3) t[y][cx] = ',';
        for (int x = 1; x < cx; x++) t[h / 2 + 4][x] = ',';
        for (int[] p : new int[][] { { 6, 5 }, { w - 7, 5 }, { 6, h - 6 }, { w - 7, h - 6 }, { 6, h / 2 }, { w - 7, h / 2 } }) t[p[1]][p[0]] = 'L';
        List<Portal> ps = new ArrayList<>();
        t[h / 2 + 4][0] = 'G';
        ps.add(new Portal(0, h / 2 + 4, 'G', TOWER_GATE, GATE_W / 2, GATE_DOOR_Y + 1, "탑 밖으로 나가는 문"));
        t[0][cx] = 'U';
        ps.add(new Portal(cx, 0, 'U', f + 1 <= TOWER_FLOORS ? towerId(f + 1) : null, 0, 0, (f + 1) + "층 마을로 올라가는 계단"));
        t[h - 1][cx] = 'S';
        ps.add(new Portal(cx, h - 1, 'S', towerId(f - 1), 0, 0, (f - 1) + "층으로 내려가는 계단"));
        S5MineMap m = new S5MineMap(towerId(f), "탑 " + f + "층 -- 수문장의 방", false, w, h, cx, h - 4, t, new byte[h][w], ps);
        m.bossX = cx - 1; m.bossY = cy - 1;
        return m;
    }
    /** 마을 층(10, 20, 30) -- 쉼터(안전지대), 탑 관리인/사제/대장장이/증표 상인. 위 계단 = 다음 층, 왼쪽 문 = 탑 나가기(아래 수문장 층으로는 못 내려간다). */
    static S5MineMap towerTown(int f) {
        int w = 30, h = 24, cx = w / 2, cy = h / 2;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) t[y][x] = x < 2 || y < 2 || x >= w - 2 || y >= h - 2 ? 'W' : (Math.hypot(x - cx, y - cy) < 7 ? 's' : '.');
        for (int y = cy - 1; y <= cy + 1; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = '~';
        for (int y = 1; y < cy - 1; y++) t[y][cx] = ',';
        for (int x = 1; x < cx - 1; x++) t[cy][x] = ',';
        List<Portal> ps = new ArrayList<>();
        t[cy][0] = 'G';
        ps.add(new Portal(0, cy, 'G', TOWER_GATE, GATE_W / 2, GATE_DOOR_Y + 1, "탑 밖으로 나가는 문"));
        t[0][cx] = 'U';
        ps.add(new Portal(cx, 0, 'U', f + 1 <= TOWER_FLOORS ? towerId(f + 1) : null, 0, 0, (f + 1) + "층으로 올라가는 계단"));
        t[h - 1][cx] = 'S'; // [2026-11-16] 지난 층 다시 가기 -- 아래 계단(수문장의 방으로)
        for (int y = cy + 2; y < h - 1; y++) t[y][cx] = ',';
        ps.add(new Portal(cx, h - 1, 'S', towerId(f - 1), 0, 0, (f - 1) + "층으로 내려가는 계단"));
        List<Npc> npcs = new ArrayList<>();
        npcs.add(npcAtCell(t, cx + 2, 4, "towergate", "탑 관리인", f + "층 마을까지 왔구려! 쉬었다 가시오. 위쪽 계단으로 올라가면 다음 구역이오."));
        npcs.add(npcAtCell(t, cx - 6, cy - 2, "priest", "탑 사제", "여기까지 오느라 고생했어요. 상처를 치료해 드리지요."));
        npcs.add(npcAtCell(t, cx + 6, cy, "smith", "탑 대장장이", "장비 손볼 게 있으면 말하게."));
        npcs.add(npcAtCell(t, cx + 5, 4, "tokenshop", "증표 상인", "증표를 모아 왔나? 동료 장비 상자를 열어 주지."));
        S5MineMap m = new S5MineMap(towerId(f), "탑 " + f + "층 마을", false, w, h, cx, h - 4, t, new byte[h][w], ps);
        m.npcs = npcs;
        m.safe.add(new int[] { cx, cy, 12 });
        return m;
    }

    /** [2026-11-15] 월드보스 전장 -- 돌로 둘러싼 넓은 원형 광장(50x40), 가운데 거룡(2x2, 움직이지 않음), 아래 입구(참여하면 여기로), 아래 문 = 왕도 광장.
     *  입구는 거룡에게서 12칸(인식 범위)보다 멀다. 잡몹 없음. */
    // [2026-11-22] 월드보스가 동쪽 성곽으로 옮겨 가 비어 있던 전장 자리(맵 ID/순서 그대로)를 왕도 수련장으로 -- "1시 허수아비들은 다른 맵으로"
    static final int WB_W = 56, WB_H = 34;
    /** [2026-11-22] 왕도 수련장(56x34) -- 성벽으로 두른 돌바닥 실내 마당, 보스 허수아비 여덟(S5MineMobs.SIM_TYPES 순서) 두 줄(가로 12칸, 세로 12칸 간격 -- 만검진 7x7이 겹치지 않게).
     *  아래 문 = 왕도 1시 수련관 문 앞. 몬스터/광석 없음. */
    static S5MineMap generateWbArena(S5MineMap city) {
        int w = WB_W, h = WB_H, cx = w / 2, c = 60;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean wall = x < 2 || y < 2 || x >= w - 2 || y >= h - 2;
            boolean rim = x == 2 || y == 2 || x == w - 3 || y == h - 3;
            t[y][x] = wall ? 'W' : rim ? 'r' : 's';
        }
        for (int y = h - 3; y < h; y++) for (int x = cx - 1; x <= cx + 1; x++) t[y][x] = 's';
        t[h - 1][cx] = 'G';
        List<int[]> dummies = new ArrayList<>();
        for (int j = 0; j < 2; j++) for (int i = 0; i < 4; i++) dummies.add(new int[] { cx - 18 + i * 12, 9 + j * 12 });
        for (int[] d : dummies) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (Math.abs(dx) + Math.abs(dy) == 2) t[d[1] + dy][d[0] + dx] = 'r'; // 허수아비 자리 표시(자갈 네 귀)
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(cx, h - 1, 'G', CITY, c + TRAIN_DOOR_DX, c + TRAIN_HALL[3] + 2, "왕도 1시 수련관 앞으로"));
        S5MineMap m = new S5MineMap(WB_ARENA, "왕도 수련장", false, w, h, cx, h - 4, t, new byte[h][w], portals);
        m.safe.add(new int[] { cx, h - 4, 3 });
        m.dummySpots = dummies;
        return m;
    }

    /** [2026-11-11] "보스맵 뒤쪽은 강이 보이는 나루터" -- 위쪽은 강(~), 가운데로 나무 선착장(H), 아래는 숲 풀밭(안전지대), 아래 문은 하피의 둥지 위 문으로. */
    static S5MineMap generateHarpyFerry() {
        int w = FERRY_W, h = FERRY_H, cx = w / 2;
        char[][] t = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c;
            if (y < 8) c = '~';
            else if (x < 2 || x >= w - 2 || y >= h - 2) c = 'F';
            else if (y == 8) c = ':'; // 강가 자갈
            else c = (x * 7 + y * 13) % 11 == 0 ? 'T' : '.';
            t[y][x] = c;
        }
        for (int y = 2; y < 9; y++) { t[y][cx - 1] = 'H'; t[y][cx] = 'H'; } // 선착장
        for (int y = 9; y < h - 1; y++) t[y][cx] = ',';
        t[h - 1][cx] = 'G';
        List<Portal> portals = new ArrayList<>();
        portals.add(new Portal(cx, h - 1, 'G', HARPY_NEST, HARPY_NEST_W / 2, 2, "하피의 둥지로"));
        S5MineMap m = new S5MineMap(HARPY_FERRY, "마물의 숲 나루터", false, w, h, cx, h - 3, t, new byte[h][w], portals);
        m.safe.add(new int[] { cx, 14, 16 });
        placeNpc(m, cx + 4, 11, "forestguard", "나루터 뱃사공", "강 건너로 가는 배는 아직 없다네. 바람이 이렇게 사나워서야, 원...");
        return m;
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
        // [2026-11-04] "설산 봉우리 가는 길 맨 위에 베이스캠프(보스방 입구에서 20걸음쯤)" -- 눈길 위 반지름 4 공터(바위/나무/호수 치움). 무작위 값을 쓰지 않아 나머지 칸은 그대로.
        int campY = 20, campX = pathX[campY];
        for (int y = campY - 4; y <= campY + 4; y++) for (int x = campX - 5; x <= campX + 5; x++)
            if (x > 0 && x < w - 1 && y > 0 && Math.hypot((x - campX) / 1.2, y - campY) <= 4.2) t[y][x] = 'n';
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
        for (int y = campY - 4; y <= campY + 4; y++) for (int x = Math.max(0, campX - 5); x <= Math.min(w - 1, campX + 5); x++) o[y][x] = 0; // 베이스캠프엔 광석 없음
        S5MineMap m = new S5MineMap(SNOWROAD, "설산 봉우리 가는 길", false, w, h, sx, sy, t, o, portals);
        m.villageX = campX; // 이동 안내원 자리(placeGuide)
        m.villageY = campY;
        m.safe.add(new int[] { campX, campY, 5 }); // 베이스캠프 모닥불
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
    /** [2026-11-05] 왕도 용병소(가운데 c 기준 칸 차이): 마당 {x0, y0, x1, y1}, 건물, 대장 자리. */
    static final int[] MERC_YARD = { -44, 14, -16, 34 }, MERC_HALL = { -38, 18, -22, 27 };
    static final int MERC_NPC_DX = -30, MERC_NPC_DY = 29;
    // [2026-11-06] "대도시 5시 방향 건물을 다 허물고 무기 강화 전용 NPC" -- 용병소와 좌우 대칭(남동쪽): 마당 + 건물 + 무기 강화사
    static final int[] ENH_YARD = { 16, 14, 44, 34 }, ENH_HALL = { 22, 18, 38, 27 };
    static final int ENH_NPC_DX = 30, ENH_NPC_DY = 29;
    // [2026-11-22] "왕도에 NPC들이 너무 난잡 -- 11시 방향 집들 깔끔하게 정리해서" -- 5시 장비 공방 NPC 줄(대장장이, 장인, 무기 강화사, 방어구 강화사, 5칸 간격)
    static final int[] ENH_ROW = { 22, 27, 32, 37 };
    // 11시 재료 공방(용병소와 위아래 대칭): 마당, 건물, NPC 줄(광석 전문가, 보석 세공사, 연금술사). 북서쪽 집은 맨 윗줄 넷만 같은 크기로 반듯하게.
    static final int[] CRAFT_YARD = { -44, -34, -16, -14 }, CRAFT_HALL = { -38, -28, -22, -19 };
    static final int CRAFT_NPC_DY = -17;
    static final int[] CRAFT_ROW = { -35, -30, -25 };
    // [2026-11-22] 같은 날 "1시 허수아비들은 다른 맵으로 빼고, 큰 건물 하나와 작은 건물 여러 개로" -- 1시 = 수련관(큰 건물, 문 = 왕도 수련장 맵) + 작은 집들.
    //  수련관 {x0, y0, x1, y1}(맨 아랫줄이 1층 앞면, 문은 TRAIN_DOOR_DX), 앞마당, 사범 줄(검기, 돌개, 필살 -- 문 왼쪽에 5칸 간격), 작은 집 {x0, y0}(6x7)
    static final int[] TRAIN_HALL = { 18, -52, 42, -37 }, TRAIN_YARD = { 12, -36, 46, -29 };
    static final int TRAIN_DOOR_DX = 36, TRAIN_NPC_DY = -34;
    static final int[] TRAIN_ROW = { 21, 26, 31 };
    static final int[][] TRAIN_HOUSES = { { 47, -52 }, { 47, -42 }, { 12, -24 }, { 21, -24 }, { 30, -24 }, { 39, -24 }, { 48, -24 } };
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
        // [2026-10-29] "대도시 1시 방향은 건물 없애고 허수아비 -- 죽지 않는 데미지 시험장" -- 성당 오른쪽 위 돌바닥 마당
        // 붉은 지붕 건물 블록(대로/광장에서 떨어진 곳), 건물 앞은 돌길
        // [2026-10-27] "왕도 내 건물 높게, 대도시처럼" -- 블록을 세로로 길게(8~10칸): 화면이 위 두 줄은 지붕, 그 아래는 층마다 창이 난 벽, 맨 아래는 1층 앞면으로 그린다.
        for (int by = wt + 4; by < h - wt - 12; by += 14) for (int bx = wt + 4; bx < w - wt - 8; bx += 11) {
            int bw = 6 + r.nextInt(3), bh = 8 + r.nextInt(3);
            if (bx < c && by < c) { bw = 8; bh = 10; } // [2026-11-22] 11시 -- 북서쪽 집은 같은 크기로 반듯하게(난수는 이미 썼다)
            boolean clear = true;
            for (int y = by - 1; y <= by + bh && clear; y++) for (int x = bx - 1; x <= bx + bw; x++) {
                if (Math.abs(x - c) <= road + 2 || Math.abs(y - c) <= road + 2 || Math.hypot(x - c, y - c) < 19) { clear = false; break; }
                if (x > c && y < c) { clear = false; break; } // [2026-10-30] "1시 방향 집들이 어색" -- [2026-11-22] 북동쪽은 수련관 + 작은 집(아래에서 손으로 짓는다)
                if (x < c && y > c) { clear = false; break; } // [2026-11-05] "왕도 7시 방향 건물을 전부 없애고 용병소" -- 남서쪽엔 안 짓는다(난수 소비는 그대로)
                if (x > c && y > c) { clear = false; break; } // [2026-11-06] "5시 방향 건물들 다 허물고" -- 남동쪽도(무기 강화사)
                if (x < c && y < c && by > wt + 4) { clear = false; break; } // [2026-11-22] 11시 재료 공방 -- 북서쪽은 맨 윗줄 집만
            }
            if (!clear) continue;
            for (int y = by; y < by + bh; y++) for (int x = bx; x < bx + bw; x++) t[y][x] = 'K';
            for (int x = bx - 1; x <= bx + bw; x++) t[by + bh][x] = 's';
        }
        // [2026-11-22] 1시 수련관 -- 큰 건물(문 = 왕도 수련장) + 앞마당(사범 셋) + 북쪽 대로까지 돌길, 둘레에 작은 집들(앞은 돌길)
        for (int y = c + TRAIN_YARD[1]; y <= c + TRAIN_YARD[3]; y++) for (int x = c + TRAIN_YARD[0]; x <= c + TRAIN_YARD[2]; x++) t[y][x] = 's';
        for (int y = c + TRAIN_HALL[1]; y <= c + TRAIN_HALL[3]; y++) for (int x = c + TRAIN_HALL[0]; x <= c + TRAIN_HALL[2]; x++) t[y][x] = 'K';
        for (int x = c + road + 1; x < c + TRAIN_YARD[0]; x++) for (int y = c + TRAIN_NPC_DY + 2; y <= c + TRAIN_NPC_DY + 3; y++) t[y][x] = 's';
        for (int[] hs : TRAIN_HOUSES) {
            for (int y = c + hs[1]; y < c + hs[1] + 7; y++) for (int x = c + hs[0]; x < c + hs[0] + 6; x++) t[y][x] = 'K';
            for (int x = c + hs[0] - 1; x <= c + hs[0] + 6; x++) t[c + hs[1] + 7][x] = 's';
        }
        t[c + TRAIN_HALL[3]][c + TRAIN_DOOR_DX] = 'G';
        // [2026-11-05] 용병소(남서쪽) -- 돌바닥 마당 + 큰 건물 하나, 건물 앞에 용병소 대장(MERC_NPC_DX/DY, buildWorld), 마당에서 남쪽 대로까지 돌길
        for (int y = c + MERC_YARD[1]; y <= c + MERC_YARD[3]; y++) for (int x = c + MERC_YARD[0]; x <= c + MERC_YARD[2]; x++) t[y][x] = 's';
        for (int y = c + MERC_HALL[1]; y <= c + MERC_HALL[3]; y++) for (int x = c + MERC_HALL[0]; x <= c + MERC_HALL[2]; x++) t[y][x] = 'K';
        for (int x = c + MERC_YARD[2]; x < c - road; x++) for (int y = c + MERC_NPC_DY + 1; y <= c + MERC_NPC_DY + 2; y++) t[y][x] = 's';
        // [2026-11-22] 11시 재료 공방(북서쪽) -- 용병소와 위아래 대칭: 마당 + 큰 건물, 마당 아래쪽에서 북쪽 대로까지 돌길
        for (int y = c + CRAFT_YARD[1]; y <= c + CRAFT_YARD[3]; y++) for (int x = c + CRAFT_YARD[0]; x <= c + CRAFT_YARD[2]; x++) t[y][x] = 's';
        for (int y = c + CRAFT_HALL[1]; y <= c + CRAFT_HALL[3]; y++) for (int x = c + CRAFT_HALL[0]; x <= c + CRAFT_HALL[2]; x++) t[y][x] = 'K';
        for (int x = c + CRAFT_YARD[2]; x < c - road; x++) for (int y = c + CRAFT_NPC_DY + 1; y <= c + CRAFT_NPC_DY + 2; y++) t[y][x] = 's';
        // [2026-11-06] 무기 강화소(남동쪽) -- 용병소와 대칭
        for (int y = c + ENH_YARD[1]; y <= c + ENH_YARD[3]; y++) for (int x = c + ENH_YARD[0]; x <= c + ENH_YARD[2]; x++) t[y][x] = 's';
        for (int y = c + ENH_HALL[1]; y <= c + ENH_HALL[3]; y++) for (int x = c + ENH_HALL[0]; x <= c + ENH_HALL[2]; x++) t[y][x] = 'K';
        for (int x = c + road + 1; x <= c + ENH_YARD[0]; x++) for (int y = c + ENH_NPC_DY + 1; y <= c + ENH_NPC_DY + 2; y++) t[y][x] = 's';
        // 가운데 광장(돌바닥 원) + 분수
        for (int y = c - 16; y <= c + 16; y++) for (int x = c - 16; x <= c + 16; x++) if (Math.hypot(x - c, y - c) < 16) t[y][x] = 's';
        for (int y = c - 3; y <= c + 3; y++) for (int x = c - 3; x <= c + 3; x++) if (Math.hypot(x - c, y - c) < 3.2) t[y][x] = '~';
        // 성당: 북쪽 대로 끝(성벽 안쪽)에 큰 건물, 문 앞에 신부
        int qx0 = c - 9, qx1 = c + 9, qy0 = wt + 3, qy1 = wt + 16;
        for (int y = qy0; y <= qy1; y++) for (int x = qx0; x <= qx1; x++) t[y][x] = 'Q';
        for (int y = qy1 + 1; y <= qy1 + 2; y++) for (int x = qx0 - 1; x <= qx1 + 1; x++) t[y][x] = 's';
        int px = c, py = qy1 + 1;
        t[py][px] = 'N';
        t[py][px + 4] = 'N'; // [2026-10-29] 원소술사(신부 옆, [2026-11-22] 네 칸 옆)
        // 텔레포트 게이트: 광장 남쪽 -- [2026-11-18] 3x3 워프 포인트(가운데 gx, gy, 아홉 칸 모두 게이트)
        int gx = c, gy = c + 8;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) t[gy + dy][gx + dx] = 'X';
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
        portals.add(new Portal(0, c, 'G', WESTROAD, 0, 0, "서문 -- 왕도 서쪽 마물의 숲 입구")); // [2026-11-05] 열림(좌표는 buildWorld)
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) portals.add(new Portal(gx + dx, gy + dy, 'X', TELE, 0, 0, "워프 포인트 · 왕도 아스테리아")); // [2026-11-18] 3x3
        portals.add(new Portal(c + TRAIN_DOOR_DX, c + TRAIN_HALL[3], 'G', WB_ARENA, WB_W / 2, WB_H - 3, "수련관 -- 왕도 수련장(보스 허수아비)")); // [2026-11-22]
        int sx = c, sy = h - wt - 2;
        boolean[][] reach = reachable(t, sx, sy);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (walkable(t[y][x]) && !reach[y][x]) t[y][x] = 'K';
        byte[][] o = new byte[h][w]; // 광석 없음(도시)
        S5MineMap m = new S5MineMap(CITY, "왕도 아스테리아", false, w, h, sx, sy, t, o, portals);
        List<Npc> npcs = new ArrayList<>();
        npcs.add(new Npc(px, py, "priest", "성당 신부", "여신의 가호가 함께하기를. 상처를 치료하고, 쓰러지면 이곳에서 깨어나도록 기도해 드리지요. 미스릴괴를 바치면 무기에 성스러운 빛을 깃들여 드립니다."));
        npcs.add(new Npc(px + 4, py, "elementalist", "원소술사", "세공된 보석엔 원소가 깃들어 있지. 하나만 주면 자네 무기에 물·번개·불·땅의 힘을 불어넣어 주겠네."));
        npcs.add(npcAtCell(t, c - 5, c + 6, "wbboard", "월드보스 게시판", WB_BOARD_LINE)); // [2026-11-17] 월드보스 게시판 -- [2026-11-22] 광장 워프 왼쪽 위
        npcs.add(npcAtCell(t, c - 5, c + 10, "mailbox", MAIL_NAME, MAIL_LINE)); // [2026-11-17] 우체부 -- [2026-11-22] 워프 왼쪽 아래
        m.npcs = npcs;
        return m; // [2026-11-22] 허수아비는 왕도 수련장(WB_ARENA 자리)으로 옮김
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
        // [2026-10-29] "바닷길 용궁 입구를 낮은 건물로, 바닷속이라는 걸 표현" -- 20줄 높이 성벽 -> 5줄 낮은 궁(폭도 좁게), 지붕 위로 해초, 양끝 산호
        int py0 = 18, py1 = 22;
        for (int y = py0; y <= py1; y++) for (int x = c - 14; x <= c + 14; x++) t[y][x] = 'L';
        for (int x = c - 14; x <= c + 14; x += 3) t[py0 - 1][x] = 'e';
        for (int y = py0 - 2; y <= py1; y++) { t[y][c - 16] = 'O'; t[y][c + 16] = 'O'; }
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
