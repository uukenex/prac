package my.prac.core.prjbot.service;

import java.util.HashMap;

/**
 * [시즌5] 탑 등반 시스템
 * 1차 구현 범위(MVP): 이동/전투/층변경/파티편성/업적 조회.
 * 미구현(TODO, 후속 작업): 가챠(동료뽑기/장비뽑기), 상점 구매, 주사위구매, 스탯구매,
 * 자동사냥 정산, 장비 장착/합성, 직업별 특수효과(전사 도발/마법사 스턴/도적 스틸/궁수 즉사/도사 보호막).
 */
public interface BotS5Service {

    /** 유저 진행상태 조회 (없으면 null) */
    HashMap<String, Object> selectUserProgress(String userName);

    /** 신규 유저 초기화: 진행상태 + 스타터 동료(★1 전사, 파티1번 편성) 지급 */
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
}
