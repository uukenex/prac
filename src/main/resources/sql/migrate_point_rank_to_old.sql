-- ============================================================
-- TBOT_POINT_RANK → TBOT_POINT_RANK_OLD 이관
-- 월요일에 할것
-- ============================================================
-- 대상: INSERT_DATE < 전전달 1일 (이전 달치까지)
-- 보존(이관/삭제 제외):
--   NEW_YN='1' AND CMD LIKE 'ACHV_%'   (업적 이력)
--   NEW_YN='1' AND CMD = 'BAG_OPEN_SP' (가방 카운트)
-- CMD 형식: YYYYMM_원래CMD (예: 202501_DROP_SP)
-- ============================================================


-- ===========================================================
-- STEP 1. 이관 전 현황 확인 (사전 체크)
-- ===========================================================
SELECT TO_CHAR(INSERT_DATE, 'YYYYMM') AS YM
     , NEW_YN
     , COUNT(*)                        AS CNT
  FROM TBOT_POINT_RANK
 WHERE INSERT_DATE < TRUNC(ADD_MONTHS(SYSDATE, -1), 'MM')
   AND NOT (NEW_YN = '1' AND CMD LIKE 'ACHV_%')
   AND NOT (NEW_YN = '1' AND CMD = 'BAG_OPEN_SP')
 GROUP BY TO_CHAR(INSERT_DATE, 'YYYYMM'), NEW_YN
 ORDER BY 1, 2
;


-- ===========================================================
-- STEP 2. 이관 INSERT (월×유저×CMD 집계 → _OLD)
--         SCORE/SCORE_EXT 단위 변환 포함
-- ===========================================================
INSERT INTO TBOT_POINT_RANK_OLD
    (USER_NAME, ROOM_NAME, SCORE, SCORE_EXT, INSERT_DATE, CMD, NEW_YN)
SELECT
    USER_NAME
  , ROOM_NAME
  , ROUND(SIGN(TOTAL_RAW) * ABS(TOTAL_RAW) / POWER(10000, UNIT_IDX), 2) AS SCORE
  , CASE WHEN UNIT_IDX = 0 THEN NULL
         ELSE CHR(ASCII('a') + UNIT_IDX - 1)
    END                                                                   AS SCORE_EXT
  , INSERT_DATE
  , CMD
  , NEW_YN
FROM (
    SELECT
        USER_NAME
      , MAX(ROOM_NAME) AS ROOM_NAME
      , SUM(
            SCORE * POWER(10000,
                CASE WHEN SCORE_EXT IS NULL OR SCORE_EXT = ''
                     THEN 0
                     ELSE ASCII(SCORE_EXT) - ASCII('a') + 1
                END)
        )                                                                 AS TOTAL_RAW
      , TO_DATE(TO_CHAR(MIN(INSERT_DATE), 'YYYYMM') || '01', 'YYYYMMDD') AS INSERT_DATE
      , TO_CHAR(INSERT_DATE, 'YYYYMM') || '_' || CMD                      AS CMD
      , NEW_YN
      , CASE
            WHEN SUM(
                     SCORE * POWER(10000,
                         CASE WHEN SCORE_EXT IS NULL OR SCORE_EXT = ''
                              THEN 0
                              ELSE ASCII(SCORE_EXT) - ASCII('a') + 1
                         END)
                 ) = 0
            THEN 0
            ELSE FLOOR(LOG(10000,
                     ABS(SUM(
                             SCORE * POWER(10000,
                                 CASE WHEN SCORE_EXT IS NULL OR SCORE_EXT = ''
                                      THEN 0
                                      ELSE ASCII(SCORE_EXT) - ASCII('a') + 1
                                 END)
                         ))))
        END                                                               AS UNIT_IDX
    FROM TBOT_POINT_RANK
    WHERE INSERT_DATE < TRUNC(ADD_MONTHS(SYSDATE, -1), 'MM')
      AND NOT (NEW_YN = '1' AND CMD LIKE 'ACHV_%')
      AND NOT (NEW_YN = '1' AND CMD = 'BAG_OPEN_SP')
    GROUP BY USER_NAME, TO_CHAR(INSERT_DATE, 'YYYYMM'), CMD, NEW_YN
)
;


-- ===========================================================
-- STEP 3. 이관 후 검증
-- ===========================================================

-- 3-1. _OLD 입력 건수 확인
SELECT TO_CHAR(INSERT_DATE, 'YYYYMM') AS YM
     , NEW_YN
     , COUNT(*)                        AS CNT
  FROM TBOT_POINT_RANK_OLD
 GROUP BY TO_CHAR(INSERT_DATE, 'YYYYMM'), NEW_YN
 ORDER BY 1, 2
;

-- 3-2. 특정 유저 SP 합계 비교 (예시: 유저명 변경 후 사용)
SELECT 'ORIGINAL'  AS SRC
     , SUM(SCORE * POWER(10000,
           CASE WHEN SCORE_EXT IS NULL OR SCORE_EXT = '' THEN 0
                ELSE ASCII(SCORE_EXT) - ASCII('a') + 1 END)) AS TOTAL_RAW
  FROM TBOT_POINT_RANK
 WHERE USER_NAME = '유저명입력'
   AND NEW_YN    = '1'
UNION ALL
SELECT 'OLD_TABLE'
     , SUM(SCORE * POWER(10000,
           CASE WHEN SCORE_EXT IS NULL OR SCORE_EXT = '' THEN 0
                ELSE ASCII(SCORE_EXT) - ASCII('a') + 1 END))
  FROM TBOT_POINT_RANK_OLD
 WHERE USER_NAME = '유저명입력'
   AND NEW_YN    = '1'
;


-- ===========================================================
-- STEP 4. 원본 삭제 (STEP 3 검증 완료 후 실행)
-- ===========================================================
DELETE FROM TBOT_POINT_RANK
 WHERE INSERT_DATE < TRUNC(ADD_MONTHS(SYSDATE, -1), 'MM')
   AND NOT (NEW_YN = '1' AND CMD LIKE 'ACHV_%')
   AND NOT (NEW_YN = '1' AND CMD = 'BAG_OPEN_SP')
;

-- 삭제 건수 확인 후 COMMIT
-- COMMIT;
