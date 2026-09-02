-- ============================================================
-- Season 5 - 몬스터 데이터 무결성 점검
-- "5층 이후 전투가 안 된다" 류 증상은 코드상 BotS5ServiceImpl.startCombat()의
--   return "몬스터 정보가 없습니다 (관리자 문의)."
-- 분기에서 나온다 -- 즉 TBOT_S5_MONSTER_INFO에 현재 층이 속한 BLOCK_NO(=floor/10
-- 내림 후 +1) & BOSS_YN 조합의 로우가 없다는 뜻. 정상이면 BLOCK_NO 1~10 ×
-- BOSS_YN(N/Y) = 20행이 전부 있어야 한다. 아래 쿼리로 실제 배포 DB에 빠진
-- 블록이 있는지 바로 확인 가능.
-- ============================================================

-- 1) 블록별 로우 수 (기대값: 1~10 각 블록당 N 1개 + Y 1개, 총 20행)
SELECT BLOCK_NO, BOSS_YN, COUNT(*) AS CNT
FROM TBOT_S5_MONSTER_INFO
GROUP BY BLOCK_NO, BOSS_YN
ORDER BY BLOCK_NO, BOSS_YN;

-- 2) 비어있는 블록 찾기 (1~10 중 N 또는 Y가 빠진 블록)
SELECT b.BLOCK_NO, bt.BOSS_YN
FROM (SELECT LEVEL AS BLOCK_NO FROM DUAL CONNECT BY LEVEL <= 10) b
CROSS JOIN (SELECT 'N' AS BOSS_YN FROM DUAL UNION ALL SELECT 'Y' FROM DUAL) bt
WHERE NOT EXISTS (
    SELECT 1 FROM TBOT_S5_MONSTER_INFO m
    WHERE m.BLOCK_NO = b.BLOCK_NO AND m.BOSS_YN = bt.BOSS_YN
)
ORDER BY b.BLOCK_NO, bt.BOSS_YN;

-- 3) 전체 목록 확인용
SELECT MONSTER_ID, BLOCK_NO, MONSTER_NAME, HP_VALUE, ATK_VALUE, DEF_VALUE, BOSS_YN
FROM TBOT_S5_MONSTER_INFO
ORDER BY BOSS_YN, BLOCK_NO;

-- 결과 2)에 행이 하나라도 나오면 그 BLOCK_NO×BOSS_YN 조합에 해당하는 층에서
-- 전투 시도 시 "몬스터 정보가 없습니다" 메시지가 뜬다. 해결: 해당 MONSTER_ID로
-- S5_MASTER_DATA.sql의 INSERT 문 중 누락된 행만 다시 실행.
EXIT;
