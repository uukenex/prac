package my.prac.core.prjbot.service;

import java.util.HashMap;

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

    /** /주사위, /ㅈㅅㅇ — 상태에 따라 이동 또는 전투 1턴 처리 */
    String rollDice(String userName);

    /** /층변경 N — 같은 10층 구간 내에서 N번째 층으로 이동 */
    String changeFloor(String userName, int n);

    /** /파티편성 — 인자 없으면 보유 동료 + 파티 슬롯 현황 표시 */
    String partyList(String userName);

    /** /파티편성 N — 목록 N번째 동료를 파티 편성/해제 토글 */
    String partyToggle(String userName, int idx);

    /** /업적 — 달성 목록 + 히든 제외 미달성 목록 */
    String achievements(String userName);

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

    /** /스탯구매 — 인자 없으면 현재 레벨/다음 비용, type 있으면 해당 스탯 구매(ATK/MINATK/HP) */
    String statShop(String userName, String type);

    /** /장비목록 */
    String equipList(String userName);

    /** /장비장착 N [M] — N번째 미착용 장비를 M번째 파티원(생략시 같은 클래스 파티원 자동탐색)에 장착 */
    String equipWear(String userName, int equipIdx, Integer companionIdx);

    /** /장비합성 N — N번째 장비와 동일(클래스/부위/등급) 미착용 장비 3개를 상위 등급 1개로 합성 */
    String equipSynthesis(String userName, int equipIdx);
}
