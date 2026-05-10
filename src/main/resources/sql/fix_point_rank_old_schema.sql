-- ============================================================
-- TBOT_POINT_RANK_OLD 스키마 → TBOT_POINT_RANK 와 동일하게 맞추기
-- SELECT * FROM TBOT_POINT_RANK UNION ALL SELECT * FROM TBOT_POINT_RANK_OLD
-- 가 가능하도록 컬럼 순서/타입 통일
-- ============================================================


-- ===========================================================
-- STEP 1. SCORE 컬럼 타입 변경 (NUMBER → NUMBER(20,6))
--         데이터가 있어 직접 MODIFY 불가 → 임시 컬럼 우회
-- ===========================================================
ALTER TABLE TBOT_POINT_RANK_OLD ADD SCORE_NEW NUMBER(20,6);

UPDATE TBOT_POINT_RANK_OLD SET SCORE_NEW = SCORE;
COMMIT;

ALTER TABLE TBOT_POINT_RANK_OLD DROP COLUMN SCORE;

ALTER TABLE TBOT_POINT_RANK_OLD RENAME COLUMN SCORE_NEW TO SCORE;


-- ===========================================================
-- STEP 2. SCORE_EXT 컬럼 추가 (기존 데이터는 NULL)
-- ===========================================================
ALTER TABLE TBOT_POINT_RANK_OLD ADD SCORE_EXT VARCHAR2(10);


-- ===========================================================
-- STEP 3. 컬럼 순서 확인 (UNION ALL 은 순서 기준이므로 중요)
--         TBOT_POINT_RANK 컬럼 순서와 동일해야 함
--         USER_NAME, ROOM_NAME, SCORE, SCORE_EXT, INSERT_DATE, CMD, NEW_YN
-- ===========================================================
-- 아래 쿼리로 두 테이블 컬럼 순서/타입 비교
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_ID, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME IN ('TBOT_POINT_RANK', 'TBOT_POINT_RANK_OLD')
 ORDER BY TABLE_NAME, COLUMN_ID
;


-- ===========================================================
-- STEP 4. 동작 확인
-- ===========================================================
SELECT * FROM TBOT_POINT_RANK       WHERE ROWNUM <= 1;
SELECT * FROM TBOT_POINT_RANK_OLD   WHERE ROWNUM <= 1;
SELECT * FROM TBOT_POINT_RANK UNION ALL SELECT * FROM TBOT_POINT_RANK_OLD WHERE ROWNUM <= 1;
