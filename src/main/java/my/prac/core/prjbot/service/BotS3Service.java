package my.prac.core.prjbot.service;

import java.util.HashMap;
import java.util.List;

public interface BotS3Service {

	/** [헬보스] 활성 보스 조회 (TBOT_POINT_NEW_BOSS, 전역) */
	HashMap<String, Object> selectHellBoss() throws Exception;

	/** [헬보스] HP 업데이트 + 공격 로그 INSERT (트랜잭션) */
	void updateHellBossTx(HashMap<String, Object> map) throws Exception;

	/** [헬보스] 보스 재생성 INSERT */
	void insertHellBoss(HashMap<String, Object> map) throws Exception;

	/** [헬보스] 전체 기여도 TOP 조회 (표시용) */
	List<HashMap<String, Object>> selectHellTop3Contributors(HashMap<String, Object> map);

	/** [헬보스] 보상 대상 기여도 조회 (7000번대 미소지자만) */
	List<HashMap<String, Object>> selectHellEligibleContributors(HashMap<String, Object> map);

	/** [헬보스] 룰렛 대상 목록 (헬모드 kill + 최근 활동) */
	List<String> selectHellLotteryPool();

	/** [헬보스] BOSS_HELL 아이템 2개 이상 보유자 목록 (룰렛 제외 대상) */
	List<String> selectHellItemOwners();

	/** [헬보스] 헬모드 일반 공격 시 INSERT_DATE 20초 감소 (현재시간 하한) */
	void reduceHellBossInsertDate();

	/** [헬보스] 마지막 처치 보스 + 보상 수령자 메시지 (보스 없을 때 표시용) */
	String getLastKillRewardMsg();

	/** [헬보스] 처치 결과 메시지 캐시 저장 (재정비 대기화면 표시용) */
	void saveLastKillMsg(String msg);

	/** [헬보스] 최근 200회 공격의 평균 데미지 */
	Long selectRecentHellAvgDmg() throws Exception;

	/** [헬보스] 현재 보스 세션 최근 공격 로그 (TOP50) */
	List<HashMap<String, Object>> selectHellBossRecentLog(HashMap<String, Object> map);

	/** [카운트업 보스] HP 누적 + 공격 로그 (트랜잭션) */
	void updateHellBossCountUpTx(HashMap<String, Object> map) throws Exception;

	/** [카운트업 보스] 종료 (END_YN=1). 성공 시 1, 이미 종료된 경우 0 반환 */
	int closeHellBoss(int seq) throws Exception;

	/** [카운트업 보스] 새 인스턴스 생성 (CUR_HP=0) */
	void insertHellBossCountUp(HashMap<String, Object> map) throws Exception;

	/** [마스터] 현재 활성 보스 강제 종료 */
	int forceCloseCurrentBoss();

	/** [마스터] 보스 타입/스탯 변경 (INSERT_DATE 유지) */
	int masterChangeBossStat(HashMap<String, Object> map);

	/** [카운트업] 개인 누적 데미지 조회 */
	long selectMyCountUpDmg(String userName, String bossStartDate);

}
