package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

public interface BotS4Service {

    /** [낚시] 유저 장비 조회 (없으면 null) */
    HashMap<String, Object> selectUserEquip(String userName);

    /** [낚시] 유저 장비 초기화 (일반낚시대/일반찌) */
    void initUserEquip(String userName);

    /** [낚시] 오늘 낚시 기록 조회 */
    HashMap<String, Object> selectTodayFishingLog(String userName);

    /** [낚시] 낚시 실행 — 물고기 추첨 + 로그 + 인벤 + 업적 처리 후 결과 메시지 반환 */
    String fishing(String userName, int rodGrade, int bobberGrade);

    /** [낚시가방] 유저 물고기 인벤토리 조회 */
    List<HashMap<String, Object>> selectFishInv(String userName);

    /** [낚시가방] 유저 업적 달성 목록 조회 */
    List<HashMap<String, Object>> selectUserAchievements(String userName);

    /** [낚시구매] 전체 업적 마스터 조회 */
    List<HashMap<String, Object>> selectAchievementList();
}
