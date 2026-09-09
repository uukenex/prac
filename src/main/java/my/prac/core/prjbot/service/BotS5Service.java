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
     * /탑내려가기(별칭 /탑다운) — 마을(X0층)에서만 사용 가능, 바로 아래 10층 구간의
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
     */
    String grantEventVouchers(String userName, int tier, int companionQty, int equipQty);

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
     * 활동 카운터 1증가 + 그 채널 사용 플래그 세팅. statKey는 아래 6개 중 하나만 유효
     * (그 외는 조용히 무시): "DICE_WEB", "DICE_CHAT", "GACHA_WEB", "GACHA_CHAT", "WIPE_WEB",
     * "WIPE_CHAT". 채널이 갈리는 지점(Season5Controller=채팅, Season5ViewController=웹)에서
     * 호출해서 /탑통계가 집계할 실시간 카운터를 쌓는다.
     */
    void bumpActivityStat(String userName, String statKey);
}
