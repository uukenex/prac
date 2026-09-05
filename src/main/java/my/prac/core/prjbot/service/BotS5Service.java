package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

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

    /** /탑도움말, /탑명령어 — 웹(SPA) 탭 기능을 포함한 전체 명령어 텍스트 안내 */
    String help(String userName);

    /** /갱신 — TBOT_S5_CONFIG(이동/전투 쿨타임 등)를 DB에서 다시 읽어 메모리 값 갱신 */
    String refreshConfig();

    /** /이미지갱신 — IMAGE_URL 없는 동료(전체 유저 공통)를 찾아 nekos.best에서 이미지를 받아와 채워넣음(최대 20마리씩) */
    String refreshCompanionImages();

    /** /주사위, /ㅈㅅㅇ — 상태에 따라 이동 또는 전투 1턴 처리 */
    String rollDice(String userName);

    /** /층변경 N — 같은 10층 구간 내에서 N번째 층으로 이동 */
    String changeFloor(String userName, int n);

    /**
     * /층내려가기(별칭 /층다운) — 지금 있는 사냥터층에서 같은 구간 안의 바로 아래 한 층으로
     * 이동(changeFloor(fm-1) 그대로 위임, 도착 처리/전투 도망 등 로직 재사용). 이미 이
     * 구간의 마을(X0층)이면 더 내려갈 곳이 없으니 /탑내려가기(구간 간 이동)를 안내.
     */
    String descendFloor(String userName);

    /**
     * /탑내려가기(별칭 /탑다운) — 마을(X0층)에서만 사용 가능, 바로 아래 10층 구간의
     * 마을(예: 20층 → 10층)로 이동한다. 0층 마을에선 더 내려갈 곳이 없어 실패 메시지 반환.
     * 이미 한 번 올라온 구간이라 재진입 자격 확인(계단 최초 도달 여부)이 필요 없다.
     */
    String descendVillage(String userName);

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

    /** 하급 동료 계약서(GACHA_ID=1) 무료뽑기 잔여 횟수 */
    int freeCompanionPullsLeft(String userName);

    /** /주사위구매 — 인자 없으면 해금된 주사위 목록, N 있으면 해당 등급으로 장착 */
    String diceShop(String userName, Integer n);

    /** 웹 SPA 주사위 UI용 — 등급별 {name, unlockFloor, unlocked, current} 구조화 목록 */
    List<HashMap<String, Object>> diceListInfo(String userName);

    /** 자동사냥 시간당 처치 수(config, /갱신으로 조절됨) — Season5ViewController의 PP/시간 추정치가 참조 */
    int autoHuntKillsPerHour();

    /** /스탯구매 — 인자 없으면 현재 레벨/다음 비용, type 있으면 해당 스탯 구매(ATK/MINATK/HP) */
    String statShop(String userName, String type);

    /** 웹 SPA 상점탭 스탯 UI용 — 현재 레벨/상한/다음 상한이 열리는 층·비용을 구조화해서 반환 */
    HashMap<String, Object> statShopInfo(String userName);

    /** /장비목록 */
    String equipList(String userName);

    /** 웹 SPA 캐릭터 상세(클릭 확대) 카드용 — 장비/스탯구매 보너스가 반영된 유효 스탯 [hp, atk, def] */
    int[] companionEffectiveStat(String userName, int companionId);

    /** /장비장착 (인자 없이) — 미착용 장비 번호 + 파티원 번호를 함께 안내 */
    String equipWearUsage(String userName);

    /** /장비장착 N [M] — N번째 미착용 장비를 M번째 파티원(생략시 같은 클래스 파티원 자동탐색)에 장착 */
    String equipWear(String userName, int equipIdx, Integer companionIdx);

    /** /장비합성 N — N번째 장비와 동일(클래스/부위/등급) 미착용 장비 3개를 상위 등급 1개로 합성 */
    String equipSynthesis(String userName, int equipIdx);

    /** /장비해제 M — M번째 파티원이 착용 중인 장비(투구/무기/갑옷) 전부를 한 번에 해제 */
    String equipUnwearAll(String userName, int companionIdx);

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

    /** /탑랭킹 — 서버 전체 최고기록(익명 집계). 누가 세운 기록인지는 노출하지 않고 수치만 보여준다. */
    String ranking();

    /**
     * 웹 SPA 파티 슬롯끼리 자리 교체 전용(채팅 명령어 없음) — idx(=/파티편성 목록 번호)의 동료를
     * targetSlot(1~3) 자리로 옮긴다. targetSlot이 비어있으면 단순 이동, 이미 다른 동료가 있으면
     * 서로 자리를 맞바꾼다. idx의 동료가 파티 밖이면(드래그 대상이 미편성 동료) 실패 처리.
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
     */
    String grantEventVouchers(String userName, int tier, int companionQty, int equipQty);

    /**
     * /탑통계, /ㅌㅌㄱ(관리자 전용) — 시즌5 유저가 웹(SPA)과 카톡(채팅) 중 어느 채널을 얼마나
     * 쓰는지, 주사위/가챠/전멸 활동량, 주사위 눈(1~20) 전역 분포까지 보여준다.
     * EVENT_ADMIN_USERS에 없으면 /이벤트지급과 동일하게 조용히 "권한이 없습니다"만 반환.
     */
    String towerStats(String userName);

    /**
     * 활동 카운터 1증가 + 그 채널 사용 플래그 세팅. statKey는 아래 6개 중 하나만 유효
     * (그 외는 조용히 무시): "DICE_WEB", "DICE_CHAT", "GACHA_WEB", "GACHA_CHAT", "WIPE_WEB",
     * "WIPE_CHAT". 채널이 갈리는 지점(Season5Controller=채팅, Season5ViewController=웹)에서
     * 호출해서 /탑통계가 집계할 실시간 카운터를 쌓는다.
     */
    void bumpActivityStat(String userName, String statKey);
}
