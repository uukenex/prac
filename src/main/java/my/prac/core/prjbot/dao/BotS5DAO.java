package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotS5DAO")
public interface BotS5DAO {

    // ── 유저 진행 상태 ──
    HashMap<String, Object> selectUserProgress(@Param("userName") String userName);
    int insertUserProgress(HashMap<String, Object> map);
    int updateUserProgress(HashMap<String, Object> map);

    // ── 층 보드 ──
    HashMap<String, Object> selectFloorInfo(@Param("floor") int floor);
    List<HashMap<String, Object>> selectTileMaster(@Param("floor") int floor);

    HashMap<String, Object> selectUserFloorProgress(@Param("userName") String userName, @Param("floor") int floor);
    int upsertUserFloorProgress(HashMap<String, Object> map);
    int deleteUserFloorProgress(@Param("userName") String userName, @Param("floor") int floor);

    // ── 칸 발견(방문) 기록 ──
    int insertTileVisit(@Param("userName") String userName, @Param("floor") int floor, @Param("tileNo") int tileNo);
    int countTileVisits(@Param("userName") String userName, @Param("floor") int floor);
    int selectTileVisitCount(@Param("userName") String userName, @Param("floor") int floor, @Param("tileNo") int tileNo);
    int deleteTileVisits(@Param("userName") String userName, @Param("floor") int floor);
    List<HashMap<String, Object>> selectVisitedTileNos(@Param("userName") String userName, @Param("floor") int floor);

    // ── 몬스터 ──
    HashMap<String, Object> selectMonster(@Param("blockNo") int blockNo, @Param("bossYn") String bossYn);

    // ── 동료 ──
    List<HashMap<String, Object>> selectUserCompanions(@Param("userName") String userName);
    int countUserCompanions(@Param("userName") String userName);
    int insertCompanion(HashMap<String, Object> map);
    int updateCompanionHp(HashMap<String, Object> map);
    int updateCompanionPartySlot(HashMap<String, Object> map);

    // ── 업적 ──
    List<HashMap<String, Object>> selectAchievementList();
    List<HashMap<String, Object>> selectUserAchievements(@Param("userName") String userName);
    int insertUserAch(HashMap<String, Object> map);

    // ── 특수칸 ──
    HashMap<String, Object> selectUserSpecialVisit(@Param("userName") String userName);
    int upsertSpecialVisitIncrement(@Param("userName") String userName);

    // ── 가챠 마스터 ──
    HashMap<String, Object> selectGacha(@Param("gachaId") int gachaId);
    List<HashMap<String, Object>> selectGachaList(@Param("gachaType") String gachaType, @Param("maxUnlockFloor") int maxUnlockFloor);

    // ── 장비 ──
    List<HashMap<String, Object>> selectUserEquip(@Param("userName") String userName);
    List<HashMap<String, Object>> selectEquipByCompanion(@Param("companionId") int companionId);
    int countUserEquip(@Param("userName") String userName);
    int insertEquip(HashMap<String, Object> map);
    int deleteEquip(@Param("equipId") int equipId);
    int updateEquipEquippedCompanion(HashMap<String, Object> map);
    List<HashMap<String, Object>> selectSameEquipForSynthesis(@Param("userName") String userName,
            @Param("clazz") String clazz, @Param("part") String part, @Param("grade") int grade);

    // ── 스탯 구매 ──
    HashMap<String, Object> selectUserStat(@Param("userName") String userName);
    int upsertUserStat(HashMap<String, Object> map);

    // ── 자동사냥 ──
    HashMap<String, Object> selectAutoHuntLog(@Param("userName") String userName);
    int upsertAutoHuntLog(HashMap<String, Object> map);

    // ── 서버 설정값(config) ──
    List<HashMap<String, Object>> selectAllConfig();
}
