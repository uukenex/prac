package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotS4DAO")
public interface BotS4DAO {

    HashMap<String, Object> selectMemberByKakaoId(@Param("kakaoId") String kakaoId);
    int upsertMember(HashMap<String, Object> map);

    HashMap<String, Object> selectUserEquip(@Param("userName") String userName);
    int upsertUserEquip(HashMap<String, Object> map);

    HashMap<String, Object> selectTodayFishingLog(@Param("userName") String userName);
    int insertFishingLog(HashMap<String, Object> map);

    List<HashMap<String, Object>> selectFishByGrade(@Param("grade") int grade);
    int upsertFishInv(HashMap<String, Object> map);
    List<HashMap<String, Object>> selectFishInv(@Param("userName") String userName);

    List<HashMap<String, Object>> selectAchievementList();
    List<HashMap<String, Object>> selectUserAchievements(@Param("userName") String userName);
    int insertUserAch(HashMap<String, Object> map);

    int selectCaughtKindCountByGrade(@Param("userName") String userName, @Param("grade") int grade);
    int selectTotalKindCountByGrade(@Param("grade") int grade);
    List<String> selectS4UserSearch(HashMap<String, Object> map);
}
