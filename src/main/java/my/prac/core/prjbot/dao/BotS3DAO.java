package my.prac.core.prjbot.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository("core.prjbot.BotS3DAO")
public interface BotS3DAO {

	/** [헬보스] 활성 보스 조회 (TBOT_POINT_NEW_BOSS, 전역) */
	HashMap<String, Object> selectHellBoss();

	/** [헬보스] HP 업데이트 (낙관적 잠금) */
	int updateHellBoss(HashMap<String, Object> map);

	/** [헬보스] 보스 재생성 INSERT (처치 후 자동 부활) */
	int insertHellBoss(HashMap<String, Object> map);

	/** [헬보스] 공격 로그 INSERT (TBOT_POINT_NEW_BATTLE_LOG, TARGET_MON_LV=999) */
	int insertHellBattleLog(HashMap<String, Object> map);

	/** [헬보스] 전체 기여도 TOP 조회 (표시용) */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

	/** [헬보스] 보상 대상 기여도 조회 (7000번대 미소지자만) */
	List<HashMap<String, Object>> selectHellEligibleContributors(HashMap<String, Object> map);

	/** [헬보스] 룰렛 대상 목록 (헬모드 kill + 최근 활동) */
	List<String> selectHellLotteryPool();

	/** [헬보스] BOSS_HELL 아이템 2개 이상 보유자 목록 (룰렛 제외 대상) */
	List<String> selectHellItemOwners();

	/** [헬보스] 헬모드 일반 공격 시 INSERT_DATE 20초 감소 (현재시간 하한) */
	int reduceHellBossInsertDate();

	/** [헬보스] 마지막 처치된 보스 조회 (리워드 표시용) */
	HashMap<String, Object> selectLastKilledHellBoss();

	/** [헬보스] 마지막 보상 수령자 조회 (TBOT_POINT_NEW_INVENTORY) */
	HashMap<String, Object> selectLastHellRewardRecipient();

	/** [헬보스] 마지막 GP 보상 수령자 조회 (TBOT_POINT_RANK, KILL_GP/NO_ITEM_GP) */
	HashMap<String, Object> selectLastHellGpRecipient();

	/** [헬보스] 최근 200회 공격의 평균 데미지 */
	Long selectRecentHellAvgDmg() throws Exception;

	/** [헬보스] 현재 보스 세션 최근 공격 로그 (TOP50) */
	List<HashMap<String, Object>> selectHellBossRecentLog(HashMap<String, Object> map);

	/** [카운트업 보스] HP 원자적 누적 */
	int updateHellBossCountUpAdd(HashMap<String, Object> map);

	/** [카운트업 보스] 종료 (END_YN=1) */
	int closeHellBoss(HashMap<String, Object> map);

	/** [카운트업 보스] 새 인스턴스 생성 (CUR_HP=0) */
	int insertHellBossCountUp(HashMap<String, Object> map);

	/** [마스터] 현재 활성 보스 강제 종료 */
	int forceCloseCurrentBoss();

	/** [마스터] 보스 타입/스탯 변경 (INSERT_DATE 유지) */
	int masterChangeBossStat(HashMap<String, Object> map);

	/** [카운트업] 공격마다 INSERT_DATE 30초 감소 */
	int reduceCountUpBossTimer();

	/** [카운트업] 보스 세션 공격 통계 (총 횟수, 첫/마지막 공격 시간) */
	HashMap<String, Object> selectCountUpBossStats(HashMap<String, Object> map);

	/** [카운트업] 개인 누적 데미지 조회 */
	long selectMyCountUpDmg(HashMap<String, Object> map);

}
