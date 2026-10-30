package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 시스템
 * 설계서: src/main/resources/ddl/S5_TOWER_DESIGN.md
 */
public interface BotS5Service {

    /** 유저 진행상태 조회 (없으면 null) */
    HashMap<String, Object> selectUserProgress(String userName);

    /**
     * 신규 유저 초기화: 진행상태(0층 마을)만 생성.
     * 동료 지급은 튜토리얼 순서대로 유저가 직접 진행한다:
     * /주사위(계정생성) → /동료뽑기 1(무료 2회) → /층변경 1 → /주사위(전투 시작)
     */
    void initUser(String userName);

    /** /탑현황 */
    String towerStatus(String userName);

    /** /탑현황 닉네임 — 다른 유저 조회(정확 일치 우선, 없으면 앞부분 일치 LIKE 검색) */
    String towerStatus(String userName, String targetQuery);

    /**
     * 닉네임(부분 입력 가능)으로 실제 유저명을 찾는다(정확 일치 우선, 없으면 앞부분 일치 LIKE
     * 검색 중 사전순 첫 번째). 아무도 없으면 null. 웹 SPA의 닉네임 검색창이 "정확히 일치하는
     * 계정이 없으면 바로 새 계정을 만들어버리는" 문제(예: "타락고냥이/바드"를 찾으려다 "타락고냥이"
     * 로 입력해서 엉뚱한 빈 계정이 새로 생김)를 막기 위해 조회 전 먼저 이걸로 실제 존재하는
     * 계정인지 확인하는 용도.
     */
    String resolveUserName(String targetQuery);

    /** /탑도움말, /탑명령어 — 웹(SPA) 탭 기능을 포함한 전체 명령어 텍스트 안내 */
    String help(String userName);

    /** /갱신 — TBOT_S5_CONFIG(이동/전투 쿨타임 등)를 DB에서 다시 읽어 메모리 값 갱신 */
    String refreshConfig();

    /** /이미지갱신 — IMAGE_URL 없는 동료(전체 유저 공통)를 찾아 nekos.best에서 이미지를 받아와 채워넣음(최대 20마리씩) */
    String refreshCompanionImages();

    /**
     * /주사위, /ㅈㅅㅇ — 상태에 따라 이동 또는 전투 1턴 처리.
     * channel은 "WEB" 또는 "CHAT"(카카오톡) — 하루 굴림 횟수 한도가 채널별로 다르다
     * ("웹/카톡 같이 쓰게 해달라, 카톡은 200회 더 주자" 요청): 총 굴림 수(채널 무관 공유
     * 카운터)가 DAILY_DICE_LIMIT(기본 1000) 미만이면 웹/카톡 둘 다 가능, 그 이상이면 웹은
     * 막히고 카톡만 DAILY_DICE_LIMIT+KAKAO_BONUS_DICE(기본 1200)까지 계속 가능하다.
     */
    String rollDice(String userName, String channel);

    /** /층변경 N — 같은 10층 구간 내에서 N번째 층으로 이동 */
    String changeFloor(String userName, int n);

    /**
     * /층내려가기(별칭 /층다운) — 지금 있는 사냥터층에서 같은 구간 안의 바로 아래 한 층으로
     * 이동(changeFloor(fm-1) 그대로 위임, 도착 처리/전투 도망 등 로직 재사용). 이미 이
     * 구간의 마을(X0층)이면 더 내려갈 곳이 없으니 /탑내려가기(구간 간 이동)를 안내.
     */
    String descendFloor(String userName);

    /**
     * /계단내려가기(별칭 /탑내려가기, /계단다운, /탑다운) — 100층 이하는 마을(X0층)에서만 사용 가능
     * (101층+ 계단 구역은 어디서든 그 구간 100의 배수 마을로 이동), 바로 아래 10층 구간의
     * 마을(예: 20층 → 10층)로 이동한다. 0층 마을에선 더 내려갈 곳이 없어 실패 메시지 반환.
     * 이미 한 번 올라온 구간이라 재진입 자격 확인(계단 최초 도달 여부)이 필요 없다.
     */
    String descendVillage(String userName);

    /**
     * /탑올라가기(별칭 /탑업) — descendVillage()와 대칭인 상승 버전. 마을에서만 사용 가능,
     * 이 구간 보스를 이미 처치해서(UNLOCKED_BLOCK) 위 구간이 열려있어야만 바로 위 10층
     * 구간의 마을로 이동한다. 아직 안 열린 구간은 실패 메시지 반환(편도 진행 규칙 유지).
     */
    String ascendVillage(String userName);

    /** /파티편성 — 인자 없으면 보유 동료 + 파티 슬롯 현황 표시 */
    String partyList(String userName);

    /** /파티편성 N — 목록 N번째 동료를 파티 편성/해제 토글 */
    String partyToggle(String userName, int idx);

    /** /동료가리기 N — 목록 N번째 동료를 숨김/숨김해제 토글(/파티편성 텍스트 목록에서 안 보이게) */
    String toggleCompanionHidden(String userName, int idx);

    /** /탑업적 — 달성한 업적 이름만 조회(설명/미달성 목록은 웹 UI '업적' 탭에서 확인) */
    String achievements(String userName);

    /** /탑업적 닉네임 — 다른 유저 업적 조회(정확 일치 우선, 없으면 앞부분 일치 LIKE 검색) */
    String achievements(String userName, String targetQuery);

    /** 번호 없이 bare로 /동료뽑기, /장비뽑기 쳤을 때 "몇 번이 뭔지" 안내(gachaType: COMPANION|EQUIP) */
    String gachaTierGuide(String userName, String gachaType);

    /** /동료뽑기 N — N번 가챠(계약서)로 동료 1명 뽑기 */
    String gachaCompanion(String userName, int gachaId);

    /** /동료뽑기10 N — N번 가챠로 동료 10연속 뽑기(무료뽑기 잔여분 자동 적용, PP 부족 시 중단) */
    String gachaCompanionTen(String userName, int gachaId);

    /** /장비뽑기 N — N번 가챠(보물상자)로 장비 1개 뽑기 */
    String gachaEquip(String userName, int gachaId);

    /** /장비뽑기10 N — N번 가챠로 장비 10연속 뽑기(PP 부족 시 중단) */
    String gachaEquipTen(String userName, int gachaId);

    /** [2026-09-15] /악세뽑기 N — N번 가챠(상자)로 악세서리(목걸이/반지/팔찌) 1개 뽑기 */
    String gachaAccessory(String userName, int gachaId);

    /** /악세뽑기10 N — N번 가챠로 악세서리 10연속 뽑기(PP 부족 시 중단) */
    String gachaAccessoryTen(String userName, int gachaId);

    /** 하급 동료 계약서(GACHA_ID=1) 무료뽑기 잔여 횟수 */
    int freeCompanionPullsLeft(String userName);

    /** /주사위구매 — 인자 없으면 해금된 주사위 목록, N 있으면 해당 등급으로 장착 */
    String diceShop(String userName, Integer n);

    /** 웹 SPA 주사위 UI용 — 등급별 {name, unlockFloor, unlocked, current} 구조화 목록 */
    List<HashMap<String, Object>> diceListInfo(String userName);

    /**
     * [2026-09-08] 30/50/60/70/80/90층 마을 도착 보상 — 주사위 강화(+, 최소 눈금 상승)/
     * 마이너스 주사위(-, 최소 눈금 하강) 상점. 둘 다 계정 전체 공통 적용(장착 중인 주사위
     * 등급 무관), 각 최대 6단계, 최대 눈금은 항상 그대로. 웹 SPA 주사위 UI에 +0~+6 강화 줄과
     * 그 아래 마이너스 구매 줄을 추가하기 위한 구조화 데이터.
     */
    HashMap<String, Object> diceEnhanceInfo(String userName);

    /** /주사위강화, /마이너스주사위 (인자 없이) — 현재 단계/다음 단계 비용·해금 조건 안내(채팅용 텍스트). */
    String diceEnhanceStatus(String userName);

    /** /주사위강화 구매 — 주사위 강화(+) 다음 단계를 PP로 구매. */
    String buyDiceBonus(String userName);

    /** /마이너스주사위 구매 — 마이너스 주사위(-) 다음 단계를 PP로 구매. */
    String buyDiceMalus(String userName);

    /** [2026-09-09] 이미 구매(해금)해둔 강화/마이너스 단계 중 하나를 무료로 선택 적용(단일선택,
     *  0은 항상 허용) — 웹 SPA 최소 눈금 줄에서 "이미 산 단계 중 뭘 켤지" 전환용. */
    String selectDiceMinAdjust(String userName, int value);

    /** 자동사냥 시간당 처치 수(config, /갱신으로 조절됨) — Season5ViewController의 PP/시간 추정치가 참조 */
    int autoHuntKillsPerHour();

    /** [2026-09-17] 웹 SPA 전투화면(포켓몬 배틀 스타일)에서 몬스터 이름을 보여주기 위한 조회용.
     *  주의: 사냥터(비보스) 몬스터는 TBOT_S5_MONSTER_INFO.MONSTER_NAME을 화면에 그대로 쓰지
     *  않는다 -- 실제 전투 로그(eliteMonsterName/floorMonsterName)는 층 위치 기준으로 순환
     *  배정되는 별도 이름 목록(FLOOR_MONSTER_NAME)을 쓰므로, DB 컬럼을 그대로 읽으면(예전
     *  Season5ViewController가 이렇게 했다가 실사례로 "뒤틀린 차원 촉수괴"(그 블록 DB상
     *  이름) vs "어둠 숲도둑 고블린"(실제 전투 로그 이름)처럼 완전히 다른 이름/몬스터로
     *  보이는 불일치가 났었음) 전투 로그와 다른 이름이 나온다. 보스는 DB 이름을 그대로
     *  쓰므로(floorMonsterName 참고) 결과가 같다. 몬스터가 없으면 null. */
    String currentFloorMonsterName(int floor);

    /** [2026-09-21] 전투화면 스탯표(POWER/GUARD)용 -- 이 층 몬스터의 실제 전투 ATK/DEF(V2
     *  오버레이/하드코어 스케일 반영). [atk, def] 순서, 몬스터 없으면 null. */
    int[] currentFloorMonsterAtkDef(int floor);

    /** /스탯구매 — 인자 없으면 현재 레벨/다음 비용, type 있으면 해당 스탯 구매(ATK/MINATK/HP) */
    String statShop(String userName, String type);

    /** 웹 SPA 상점탭 스탯 UI용 — 현재 레벨/상한/다음 상한이 열리는 층·비용을 구조화해서 반환 */
    HashMap<String, Object> statShopInfo(String userName);

    /** /장비목록 */
    String equipList(String userName);

    /** 웹 SPA 캐릭터 상세(클릭 확대) 카드용 — 장비/스탯구매 보너스가 반영된 유효 스탯
     *  [hp, atk, def, hpBase, atkBase, defBase] (base는 한계돌파만 뺀 값) */
    int[] companionEffectiveStat(String userName, int companionId);

    /** [2026-09-14] "전체보기 카드마다 공/방/체 + 한계돌파 보너스를 다 보여달라" 요청 --
     *  companionEffectiveStat()을 동료마다 따로 호출하면 N+1 조회가 되므로, 유저 스탯/장비를
     *  한 번씩만 조회해 전체 동료 목록에 EFF_HP/EFF_ATK/EFF_DEF/EFF_HP_BASE/EFF_ATK_BASE/
     *  EFF_DEF_BASE 필드를 얹어서 한 번에 돌려준다(selectUserCompanions와 같은 원본 키 그대로
     *  + 이 6개 필드 추가). */
    List<HashMap<String, Object>> companionsWithEffectiveStats(String userName);

    /** [2026-09-14] 동료 초상화 축소판 캐싱 프록시(/api/tower-avatar)용 -- 캐시에 있으면
     *  그대로, 없으면 원본(nekos.best)을 받아 160x160 JPEG로 축소해 캐싱 후 반환. 실패하면
     *  null(컨트롤러가 404 처리, 프론트는 기존 onerror 폴백으로 이모지 표시). */
    byte[] getCompanionAvatarThumbnail(int companionId);

    /** /장비장착 (인자 없이) — 미착용 장비 번호 + 파티원 번호를 함께 안내 */
    String equipWearUsage(String userName);

    /** /장비장착 N [M] — N번째 미착용 장비를 M번째 파티원(생략시 같은 클래스 파티원 자동탐색)에 장착 */
    String equipWear(String userName, int equipIdx, Integer companionIdx);

    /** /장비합성 N — N번째 장비와 동일(클래스/부위/등급) 미착용 장비 3개를 상위 등급 1개로 합성 */
    String equipSynthesis(String userName, int equipIdx);

    /** /장비일괄합성 — 미착용 장비 전체를 대상으로, 합성 가능한(동일 클래스/부위/등급 3개 이상,
     *  ★6 미만) 조합을 전부 반복 합성한다. 합성으로 새로 만들어진 장비가 또 3개가 되면
     *  연쇄적으로 계속 상위 등급까지 합성됨(예: ★1 9개 → ★2 3개 → ★3 1개). */
    String equipSynthesisAll(String userName);

    /** 웹 SPA 전용: M번째 파티원에게 6부위(무기/투구/갑옷/목걸이/반지/팔찌) 각각 착용 가능한
     *  미착용 장비 중 최고 등급을 한 번에 장착("일괄장착" 요청, 채팅 명령어 없음). 현재 착용보다
     *  등급이 높은 부위만 교체하고, 이미 최고 등급이거나 후보가 없는 부위는 건드리지 않는다. */
    String equipBestAll(String userName, int companionIdx);

    /** [2026-09-21] 전설제작 UI(상점 탭)용 -- 제작 가능한 ★7 전설장비 로스터 전체
     *  (LEGENDARY_ID/CLASS/PART/ITEM_NAME/EFFECT_TYPE/EFFECT_PARAM1/EFFECT_PARAM2/FLAVOR_TEXT). */
    List<HashMap<String, Object>> legendaryRoster();

    /** /전설제작 — 전설의조각 10개를 소모해 지정한 legendaryId의 ★7 전설장비 제작 시도
     *  (성공률 30%, 실패해도 조각은 소모됨). 정식 오픈 전까지는 NO_COOLDOWN_YN 계정만 사용 가능. */
    String craftLegendary(String userName, int legendaryId);

    /** [2026-09-21] "전설은 한번 만들어지면 전설의조각 9개로 바꿀수있도록도 해줘" -- 보유한
     *  ★7 전설장비(미착용 목록 N번)를 분해해서 조각 9개로 환급. */
    String disenchantLegendary(String userName, int equipIdx);
    /** [2026-10-22] 웹 해체 팝업 -- EQUIP_ID 기준(목록 번호가 어긋나 다른 장비를 가리키던 문제). */
    String disenchantLegendaryById(String userName, int equipId);
    /** [2026-10-23] ★1~★6 일반 장비/악세 분해(수량 지정, 확률로 전설의조각). */
    String dismantleEquip(String userName, int equipId, int qty);
    String dismantleEquipByIdx(String userName, int equipIdx, int qty);
    /** [2026-10-23] 웹 광산 -- 공용 맵 지형/포탈, 내 상태(스태미너/맵/위치/캔 칸/광석), 캐기, 위치 저장, 포탈 이동, 광산상점(광석 -> 시간당 PP). */
    HashMap<String, Object> mineMap(String mapId);
    /** [2026-10-23] 광산/광산상점 사용 가능 여부(TBOT_S5_CONFIG MINE_OPEN_YN / MINE_ADMIN_USERS). */
    boolean mineAccess(String userName);
    HashMap<String, Object> mineState(String userName);
    /** ver = 화면이 가진 맵 version(리셋 등으로 맵이 바뀌었으면 reload로 응답). 빈 값이면 확인하지 않는다. */
    /** dir = 바라보는 방향(up/down/left/right) -- [2026-10-25] 쌍날 곡괭이(앞뒤 칸)용. */
    HashMap<String, Object> mineDig(String userName, String mapId, String ver, int x, int y, String dir);
    HashMap<String, Object> mineSavePos(String userName, String mapId, String ver, int x, int y);
    /** dest = 배(판자) 포탈일 때 갈 항구 맵 ID(그 외 null). */
    HashMap<String, Object> mineWarp(String userName, String mapId, String ver, int x, int y, int px, int py, String dest);
    /** [2026-10-24] 광산 몬스터 상태 조회 / 공격. */
    HashMap<String, Object> mineMobs(String mapId);
    HashMap<String, Object> mineAttack(String userName, String mapId, String ver, int x, int y, int mobId, int mc);
    HashMap<String, Object> mineGoVillage(String userName);
    /** [2026-10-25] 이동 안내원 -- 안내원 맵끼리 무료 이동. */
    HashMap<String, Object> mineGuide(String userName, String mapId, String ver, int x, int y, String dest);
    /** [2026-10-26] 한기 턴(한기 맵에서 1초마다) -- 한기/눈바람 피해, 안전지대, 다음 눈바람 시각. */
    HashMap<String, Object> mineColdTick(String userName, String mapId, String ver, int x, int y);
    /** [2026-10-26] 방한 외투 -- action list/buy/wear (설산 마을 털옷 장인 / 설원 정박장 암상인). */
    HashMap<String, Object> mineCoat(String userName, String action, String code);

    /** [2026-10-28] 다도해(운영자만) -- quest/claim(신궁 수색 임무), boat(달빛석 -> 1인용 배), board(배 타고 뱃길로). */
    HashMap<String, Object> mineArchi(String userName, String action);
    /** [2026-10-28] 구리 정련(구리 20 -> 철 1), 오늘의 구리 납품(하루 1번 구리 100 -> 요일석 3 + 곡괭이 내구도). */
    HashMap<String, Object> mineRefine(String userName, int times);
    HashMap<String, Object> mineDeliver(String userName);
    /** [2026-10-28] 광산 진입 공지 확인(다시 안 띄움). */
    HashMap<String, Object> mineNoticeAck(String userName, int id);
    /** [2026-10-28] /광산공지 [내용] (관리자) -- 광산 소식 바꾸기(번호가 올라가 모두에게 한 번씩 다시 뜸), "끄기" = 안 띄움, 빈 내용 = 지금 공지 보기. */
    String setMineNotice(String userName, String text);
    /** [2026-10-28] 요일 동굴 문지기 -- 오늘의 요일석 20개로 다른 요일 동굴(오늘 하루), 오늘 요일로 되돌리기 무료. */
    HashMap<String, Object> mineDayGate(String userName, int day);
    /** [2026-10-23] 대장간 -- 광석(grade)으로 곡괭이 강화(최대/현재 내구도 함께 증가), times = 몇 번. */
    HashMap<String, Object> minePickUpgrade(String userName, int grade, int times);
    /** [2026-10-23] 매일 05:00 광산 맵 2일 주기 리셋 확인. @Scheduled 메서드라 인터페이스에도 반드시 선언(누락 시 컨텍스트 기동 실패). */
    void mineResetTick();
    /** [2026-10-24] 매일 0시 곡괭이 내구도 가득. @Scheduled 메서드라 인터페이스에도 반드시 선언(누락 시 컨텍스트 기동 실패). */
    void minePickRefillTick();
    HashMap<String, Object> mineShop(String userName);
    String mineShopBuy(String userName, int no, int times);
    /** [2026-10-23] 광부 투구 구매/업그레이드. */
    String mineHelmetUpgrade(String userName);
    /** [2026-10-24] 광산 장비(장신구/장화) 만들기·올리기, 반지 바꿔 끼기, 칠요석 합성, 선장 퀘스트(give/craft). */
    HashMap<String, Object> mineCraft(String userName, String code);
    HashMap<String, Object> mineEquipRing(String userName, String code);
    HashMap<String, Object> mineSynthSeven(String userName, int times);
    HashMap<String, Object> mineCaptain(String userName, String action);
    /** [2026-10-24] 명품상점 -- action buy/wear/gender. */
    HashMap<String, Object> mineLook(String userName, String action, String code, String slot);
    /** [2026-10-24] 왕도 성당 신부 -- action heal/save/reset. */
    HashMap<String, Object> minePriest(String userName, String action);
    /** [2026-10-24] 동굴 3층 보스 선공(화면이 보스 2칸 안에서 1.5초마다). */
    HashMap<String, Object> mineBossHit(String userName, String mapId, String ver, int x, int y);
    /** [2026-10-25] 전투 턴(몬스터 인식 중 1초마다) -- 쫓아와 때리기, 5칸 이상 멀어지면 인식 해제 + 체력 가득. */
    HashMap<String, Object> mineCombatTick(String userName, String mapId, String ver, int x, int y);
    /** [2026-10-24] 탐지 곡괭이 -- 주변 3x3 광석 미리 보기(읽기만). */
    HashMap<String, Object> mineScan(String userName, String mapId, int x, int y);
    /** [2026-10-24] 광산 함께 보기(내 위치 알리기 + 같은 맵 다른 유저), 나갈 때 지우기, "안녕하세요"(하루 1회 요일석 5개). */
    HashMap<String, Object> minePresence(String userName, String mapId, int x, int y, String dir);
    void mineLeave(String userName);
    HashMap<String, Object> mineGreet(String userName);
    /** [2026-10-25] 관리자 광산 캐릭터 현황(위치/장착/레벨/보유 수량, 읽기만). */
    List<HashMap<String, Object>> mineUserOverview();
    /** [2026-10-30] 광산 접속 화면 -- 내 캐릭터 현황(없으면 null). */
    HashMap<String, Object> mineMyOverview(String userName);
    /** [2026-10-25] 광산 유저별 외형 {유저명: look} -- 로그 뷰어 캐릭터 그림용(읽기만). */
    HashMap<String, Object> mineLooks();
    /** [2026-10-24] 관리자 전용 광산 좌표 이동(MINE_ADMIN_USERS 또는 EVENT_ADMIN_USERS). */
    HashMap<String, Object> mineAdminWarp(String userName, String mapId, int x, int y);

    /** [2026-10-28] 설산 동굴 -- 바라보는 얼음 광맥(tx, ty) 연속 채광(20번이면 빙석). */
    HashMap<String, Object> mineVein(String userName, String mapId, String ver, int x, int y, int tx, int ty);

    /** [2026-10-28] 사막 서쪽에서 개미굴로 떨어지기. */
    HashMap<String, Object> mineAntFall(String userName, String mapId, int x, int y);

    /** [2026-10-28] 광석 전문가(광석 100 -> 괴, 45%), 보석 세공사(보석 5 -> 세공된 보석, 25%), 왕도 장인(세공된 보석 -> 최대 내구도 +30). */
    HashMap<String, Object> mineSmelt(String userName, int grade, int times);
    /** [2026-10-28] 다도해 상인(양산). */
    HashMap<String, Object> mineArchiShop(String userName, String action, String code);
    HashMap<String, Object> mineGemCut(String userName, int gemIdx, int times);
    HashMap<String, Object> mineArtisan(String userName, int gemIdx);

    /** [2026-10-28] 개미굴 보물 상자(상자마다 맵 주기에 한 번, 보석). */
    HashMap<String, Object> mineAntChest(String userName);

    /** [2026-10-28] 피라미드 보상 상자(사막의 심장, 맵 주기마다 한 번). */
    HashMap<String, Object> mineDesertChest(String userName);

    /** [2026-10-08] 전설장비 강화 -- 웹용(EQUIP_ID 지정). 전설의조각을 넣어 +1씩 올리고(수치 제한 없음), 실패해도 수치는 유지(조각만 소모). */
    String enhanceLegendary(String userName, int equipId);

    /** [2026-10-08] 채팅 /전설강화 N -- N은 /전설강화로 보이는 ★7 전설장비 목록 번호. */
    String enhanceLegendaryByNo(String userName, int no);

    /** [2026-10-08] 채팅 /전설강화(인자 없음) -- 내 ★7 전설장비 목록과 다음 강화의 성공률/조각 비용. */
    String enhanceLegendaryList(String userName);

    /** [2026-09-21] 전투화면 UI 버전(V1=포켓몬 스타일 구버전, V2=삼국지 대전화면 신버전)
     *  선호 저장. version이 "V1"이 아니면 전부 V2로 정규화. */
    String setBattleScreenVersion(String userName, String version);

    /** [2026-09-21] 웹 UI 이동한도 표시용 -- {used, webLimit, totalLimit(카톡 보너스 포함)}. */
    HashMap<String, Object> moveLimitInfo(String userName);

    /** [2026-10-21 성능] 통계 카운터(주사위 눈 분포/활동 카운터/시간대별 활동)를 메모리에 모았다가 30초마다 한 번에 반영한다.
     *  @Scheduled 메서드라 인터페이스에도 반드시 선언(JDK 동적 프록시). */
    void flushStatBuffers();

    /** [2026-09-21 버그수정] @Scheduled 메서드는 반드시 인터페이스에 선언돼야 한다 -- 이 빈은
     *  JDK 동적 프록시(인터페이스 기반)로 노출되는데, 구현체에만 있고 인터페이스에 없는
     *  메서드를 스케줄러가 프록시로 호출하려다 "Need to invoke method ... but not found in
     *  any interface(s) of the exposed proxy type"로 컨텍스트 부팅 자체가 실패했다(라이브
     *  확인, ContextLoaderListener 예외로 전체 배포 실패 -> 전 사이트 404). CUR_FLOOR=0 미사용
     *  계정을 매일 새벽 4시 정리(BotS5ServiceImpl.cleanupJunkZeroFloorUsers 참고). */
    void cleanupJunkZeroFloorUsers();

    /** [2026-09-30] 시즌5 전용 로그 테이블(TBOT_S5_WORD_HIS) 30일 초과분 매일 새벽 4시 30분 삭제.
     *  @Scheduled 메서드라 위 cleanupJunkZeroFloorUsers와 같은 이유로 인터페이스에도 반드시 선언. */
    void purgeOldS5WordHis();

    /** [2026-10-01] 계단 구역 실제 오픈 상한(TBOT_S5_CONFIG STAIR_ZONE_MAX_FLOOR, 기본 200) -- 웹 UI가 이동 가능 층 범위 표시에 사용. */
    int stairZoneMaxFloor();

    /** [2026-09-30] 기존 로그 테이블(TBOT_WORD_HIS) 30일 초과분 매일 새벽 4시 40분 삭제. @Scheduled 메서드라
     *  위 메서드들과 같은 이유로 인터페이스에도 반드시 선언(누락하면 컨텍스트 부팅 실패). */
    void purgeOldWordHis();

    /** [2026-09-30] 경매장 시세(T_MARKET_ITEM_PRICE) 한 달이 지난 날짜를 아이템별 일 최소가/최대가 행 2개만 남기고 압축
     *  (매일 새벽 4시 50분). @Scheduled 메서드라 인터페이스에도 반드시 선언. */
    void compactOldMarketPrice();

    /** /장비해제 M — M번째 파티원이 착용 중인 장비(투구/무기/갑옷) 전부를 한 번에 해제 */
    String equipUnwearAll(String userName, int companionIdx);

    /** 웹 SPA 전용: 파티 슬롯 시트에서 장비 하나만 콕 집어 해제(채팅 명령어 없음). */
    String equipUnwearOne(String userName, int equipId);

    /**
     * ★N(grade=3/4/5) 동료 선택권 사용 — 10층 구간 앞4층(X1~X4) 완전탐사 보상으로 받은
     * 선택권을 소비해 직업(job)을 골라 그 등급/직업의 동료를 확정 생성한다. 등급은 그
     * 선택권을 지급한 구간(블록 1~3=★3, 4~5=★4, 6~10=★5)에 따라 이미 정해져 있고, 유저는
     * 보유한 등급 중 무엇을 쓸지와 직업만 고른다. 웹 UI 전용 기능(채팅 명령어 없음).
     */
    String redeemCompanionChoiceTicket(String userName, String job, int grade);

    /**
     * ★N(grade=3/4/5) 무기 선택권 사용 — 10층 구간 뒤4층(X5~X8) 완전탐사 보상으로 받은
     * 선택권을 소비해 직업(job)을 골라 그 직업 전용 무기(WEAPON 부위)를 확정 생성한다.
     * 웹 UI 전용.
     */
    String redeemWeaponChoiceTicket(String userName, String job, int grade);

    /** 유저별 보드(층) 조회 — 없으면 새로 생성(계단1/특수1~2/보물상자1/강화몬스터(3구간부터)+나머지 전투/함정/럭키). 마을 귀환 시 삭제되어 다음 진입 때 재생성됨. */
    List<HashMap<String, Object>> ensureUserBoard(String userName, int floor);

    /**
     * /탑랭킹 — 서버 전체 최고기록(익명 집계). 누가 세운 기록인지는 노출하지 않고 수치만
     * 보여주되, userName(요청한 본인)이 그 항목의 서버 최고기록과 같으면 "(me)"를 붙여준다
     * ("본인인 경우는 (me)라고 표기해달라" 요청, 2026-09-08) -- 다른 사람 기록은 여전히 비공개.
     */
    String ranking(String userName);

    /**
     * [2026-09-10] S4 낚시 연동 -- "낚시로 인한 PP 획득은 S5 유저만 받도록, 아니면 멘트도
     * 안 나오게" 요청. 그날 낚시로 잡은 물고기 등급(1~8)을 알려주면, 이 유저가 S5(탑) 진행
     * 기록이 있는 유저일 때만 현재 최고 도달층 기준으로 PP를 계산해서 지급하고 그 PP를
     * 반환한다. S5 진행기록이 없는 유저(탑을 아예 시작 안 한 유저)면 아무것도 안 하고
     * null을 반환하므로, 호출부(S4)는 null이면 보상/멘트 자체를 완전히 생략해야 한다.
     * 2026-09-11부터 적용(그 전엔 날짜 게이트로 항상 null) -- FISHING_PP_START_DATE 참고.
     * `/낚시`는 이미 하루 1회로 컨트롤러에서 막혀 있어(TBOT_S4_FISHING_LOG PK), 별도의
     * "하루 1회" 제한 로직 없이도 자연히 일일 보너스가 된다.
     */
    PP grantFishingBonus(String userName, int fishGrade);

    /**
     * 웹 SPA 파티 슬롯 배치 통합 액션(채팅 명령어 없음) — idx(=/파티편성 목록 번호)의 동료를
     * targetSlot(1~3) 자리에 배치한다. idx가 이미 편성된 동료면 자리 이동/맞교환, 아직 미편성인
     * 동료면 그 자리에 배치(비어있으면 단순 배치, 다른 동료가 있으면 그 동료는 파티 밖으로
     * 축출되고 장비도 함께 해제됨).
     */
    String partySwapSlot(String userName, int idx, int targetSlot);

    /** 웹 SPA 전용: 파티에 편성된 동료 전원을 한 번에 해제("일괄해제" 요청으로 신설). */
    String partyUnassignAll(String userName);

    /**
     * /이벤트지급(관리자 전용, 채팅 명령어) — 전체 유저에게 동료뽑기권(특정 등급 티어락)/
     * 장비뽑기권(범용)을 일괄 지급. `tier`(1=하급/2=중급/3=상급/4=최상급, TBOT_S5_GACHA_MASTER.
     * GACHA_ID 기준)로 어느 등급 계약서 전용 권을 줄지 고르고, `companionQty`가 0이면 동료뽑기권은
     * 지급 안 함(equipQty만 처리). 이 권을 보유한 유저는 아직 그 계약서가 해금될 층에 못 갔어도
     * 바로 뽑을 수 있다(hasUsableCompanionVoucher 참고). TBOT_S5_CONFIG.EVENT_ADMIN_USERS
     * ('|' 구분 유저명 목록)에 등록된 유저만 실행 가능, 그 외엔 조용히 "권한이 없습니다"만
     * 반환(누가 관리자인지 자체를 노출하지 않음).
     * [2026-09-15] accessoryQty(4번째 인자) 추가 -- 악세뽑기권(목걸이/반지/팔찌 상자)도
     * 같은 방식으로 등급 못박아 지급.
     */
    String grantEventVouchers(String userName, int tier, int companionQty, int equipQty, int accessoryQty);

    /**
     * 웹 SPA 전용: 현재 등록된 공지 버전/내용 조회 -- 로그인/권한 무관, 항상 공개 정보.
     * {"APP_VERSION": "...", "NOTICE_TEXT": "...", "DISMISSED": true/false} 형태.
     * userName이 주어지면 그 유저가 이 버전을 이미 "다시 보지 않기"로 닫았는지까지 DISMISSED에
     * 담아 돌려준다(유저 미지정/미등록이면 항상 false).
     */
    HashMap<String, Object> getNotice(String userName);

    /**
     * 웹 SPA 전용: 유저가 공지 모달에서 "다시 보지 않기"를 누르면 호출 -- 현재 등록된 공지
     * 버전을 그 유저의 NOTICE_SEEN_VERSION으로 저장해, 다음에 같은 버전이 다시 뜨지 않게 한다
     * ("공지가 페이지 들어갈 때마다 나온다, 유저별로 한 번씩 다시 보지 않게 해달라" 요청,
     * 2026-09-08). 관리자가 새 공지를 등록(APP_VERSION 갱신)하면 다시 보인다.
     */
    void dismissNotice(String userName);

    /**
     * /공지등록(관리자 전용) -- 새 공지 내용을 등록하고 버전(타임스탬프)을 새로 발급한다.
     * 웹 화면이 주기적으로 버전을 확인하다가 바뀐 걸 감지하면 새로고침 안내 팝업을 띄운다
     * ("새로고침 잘 안 하는 유저가 있다, 업데이트 시 강제로 새로고침 유도하고 공지도
     * 보여주고 싶다" 요청). EVENT_ADMIN_USERS 등록자만 실행 가능.
     */
    String setNotice(String userName, String text);

    /**
     * /탑통계, /ㅌㅌㄱ(관리자 전용) — 시즌5 유저가 웹(SPA)과 카톡(채팅) 중 어느 채널을 얼마나
     * 쓰는지, 주사위/가챠/전멸 활동량, 주사위 눈(1~20) 전역 분포까지 보여준다.
     * EVENT_ADMIN_USERS에 없으면 /이벤트지급과 동일하게 조용히 "권한이 없습니다"만 반환.
     */
    String towerStats(String userName);

    /**
     * /로그(관리자 전용) — 유저별 전투/웹 로그 뷰어 페이지 링크 안내. EVENT_ADMIN_USERS에
     * 없으면 /이벤트지급·/탑통계와 동일하게 "권한이 없습니다"만 반환.
     */
    String battleLogLink(String userName);

    /** [2026-10-28] /패치 [내용] (관리자) -- 광산에 있는 사람 화면 맨 위 띠 공지(내용 없으면 "곧 패치가 시작됩니다."), "끄기" = 내림, 배포하면 사라짐. */
    String setMinePatchNotice(String userName, String text);

    /** [2026-10-28] /광산 -- 광산 바로 들어가기 링크(계정이 없으면 지금 이름으로 만든다). */
    String mineLink(String userName);

    /** [2026-09-30] 몬스터 정보 표(웹 도감 탭 몬스터도감의 원본)용 -- 층별 일반/보스 몬스터의
     *  HP/ATK/DEF/처치 PP/전투력/안전기준을 실제 전투와 같은 계산(applyHardcoreFloorScale 등)으로 만든 표. */
    java.util.List<java.util.HashMap<String, Object>> monsterInfoTable();

    /** [2026-10-08] 웹 도감 탭(몬스터도감)용 -- monsterInfoTable과 같은 표를 10분 캐시해서 모든 유저에게 공개(층별 480행 계산이 무거워서). */
    java.util.List<java.util.HashMap<String, Object>> monsterCodex();

    /** [2026-10-08] 웹 도감 탭(아이템도감)용 -- ★7 전설장비 목록, 보유 인원/개수, 최고 강화 수치. */
    java.util.List<java.util.HashMap<String, Object>> legendaryCodex();

    /** [2026-10-20] 편성 화면 장비 카드용 -- 파티 동료별 장비 1개가 올려주는 체력/공격/방어와 강화로 늘어난 몫. 키 = 동료ID 문자열. */
    java.util.HashMap<String, java.util.List<java.util.HashMap<String, Object>>> partyEquipContribution(String userName);

    /** [2026-10-20] 동료 상세(웹) -- 최종 능력치 분해(기본/한계돌파/장비)와 장비별 기여 행. */
    java.util.HashMap<String, Object> companionDetail(String userName, int companionId);

    /** [2026-10-21 성능] 편성 화면용 -- 파티 동료 전원의 detail(companionDetail)과 byCompanion(장비 기여 행)을 한 번에(조회 최소화). */
    java.util.HashMap<String, Object> partyEquipAndDetail(String userName);

    /** [2026-10-20] 편성 화면 슬롯 카드 상단용 -- 파티 동료별 companionDetail. 키 = 동료ID 문자열. */
    java.util.HashMap<String, java.util.HashMap<String, Object>> partyDetail(String userName);

    /** [2026-10-20] 장비변경 팝업용 -- 그 부위에 낄 수 있는 장비의 기여 미리보기. */
    java.util.HashMap<String, Object> equipPreview(String userName, int companionId, String part);

    /** [2026-10-20] 성장형 동료(☆1) -- 현황(없으면 자동 생성). */
    java.util.HashMap<String, Object> growthStatus(String userName);
    String growthInfoText(String userName);
    String growthRename(String userName, String name);

    /** [2026-10-22] 연타 대응 -- 메모리로 판정한 주사위 쿨타임 거절 메시지(DB 미사용), 판정 못 하면 null. */
    String diceCooldownFast(String userName);

    /** [2026-10-22] 하단 메뉴 순서/숨기기(유저별 저장). */
    String setDockLayout(String userName, String layout);

    /** [2026-10-22] 인벤토리 화면용 장비 옵션 문구(실제 능력치 계산과 같은 V2 표 기준). */
    String equipOptionText(String part, int grade);

    /** [2026-10-22] 전투 영향 패치 -- 주사위를 막아야 하면 {type: PATCH|LEGEND, text}, 아니면 null. */
    java.util.HashMap<String, Object> diceGate(String userName);
    /** [2026-10-22] 패치 내용 확인(웹 확인 버튼, 채팅 /패치확인). */
    String ackPatch(String userName);
    /** [2026-10-22] 강화권(강화된 전설장비 해체 시 지급) 목록/사용. */
    java.util.List<java.util.HashMap<String, Object>> enhanceTickets(String userName);
    String useEnhanceTicket(String userName, int ticketId, int equipId);
    String enhanceTicketText(String userName);
    String useEnhanceTicketByNo(String userName, int ticketNo, int equipNo);

    /** [2026-10-21] 편성 화면 "이미지변경" -- 내 동료 두 명의 표시 이미지를 서로 바꾼다(★7/성장형 포함, 개별 이미지로 저장). */
    String swapCompanionImage(String userName, int companionId, int targetId);
    String growthChangeJob(String userName, String job);

    /** [2026-10-20] ★7 동료 소환 -- 현재 상태(업적/보유 여부/직업별 능력치와 고정 초상화). */
    java.util.HashMap<String, Object> star7Status(String userName);

    /** [2026-10-20] 채팅 /7성소환 [직업] -- 인자가 없으면 안내, 있으면 그 직업의 ★7 동료를 소환. */
    String star7Command(String userName, String arg);

    /** [2026-10-20] 웹 소환 실행. */
    String summonStar7(String userName, String job);

    /** [2026-10-20] 채팅 /7성방출 확인 -- "확인"이 없으면 경고만. */
    String star7ReleaseCommand(String userName, String arg);

    /** [2026-10-20] ★7 동료 방출(장비는 벗겨짐) -- 웹은 확인창을 거친 뒤 호출. */
    String releaseStar7(String userName);

    /**
     * 활동 카운터 1증가 + 그 채널 사용 플래그 세팅. statKey는 아래 6개 중 하나만 유효
     * (그 외는 조용히 무시): "DICE_WEB", "DICE_CHAT", "GACHA_WEB", "GACHA_CHAT", "WIPE_WEB",
     * "WIPE_CHAT". 채널이 갈리는 지점(Season5Controller=채팅, Season5ViewController=웹)에서
     * 호출해서 /탑통계가 집계할 실시간 카운터를 쌓는다.
     */
    void bumpActivityStat(String userName, String statKey);
}
