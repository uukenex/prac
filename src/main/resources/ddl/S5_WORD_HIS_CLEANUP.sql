-- [2026-09-30] TBOT_WORD_HIS 오래된 로그 정리 (수동 실행용, 자동 실행 아님)
--
-- 배경: TBOT_WORD_HIS 는 약 157만 행 / 2.1GB 로 계속 커지는 채팅+웹 명령 로그 테이블.
-- 이 테이블을 읽는 기능 중 가장 오래된 과거를 보는 것은 selectMarketCondition(최근 60일)이라
-- 60일보다 오래된 행은 어떤 기능도 읽지 않는다. 여유를 두고 기본 보관기간을 90일로 잡았다.
--
-- 사용법 (sqlplus, 계정은 각자 접속):
--   1) 먼저 아래 [1단계]로 삭제 대상 행 수만 확인한다.
--   2) 보관기간을 바꾸려면 DEFINE 값만 수정한다.
--   3) [2단계]를 실행하면 5000행씩 지우고 매번 COMMIT 한다(중간에 끊어도 이미 지운 만큼은 유지).
--   4) 삭제는 되돌릴 수 없다. 필요하면 실행 전에 백업 테이블을 만들어 둘 것(하단 [백업] 참고).
--
-- 주의: sqlplus 는 세미콜론 뒤 같은 줄 주석을 ORA-00911 로 실패시킨다. 주석은 항상 별도 줄에 둘 것.

DEFINE KEEP_DAYS = 90

-- [1단계] 삭제 대상 행 수 확인 (읽기 전용)
SELECT COUNT(*) AS DELETE_TARGET_ROWS
  FROM TBOT_WORD_HIS
 WHERE INSERT_DATE < SYSDATE - &KEEP_DAYS;

-- [백업] (선택) 지우기 전에 남겨두고 싶다면 아래 주석을 풀어 실행. 용량이 크니 디스크 여유 확인.
-- CREATE TABLE TBOT_WORD_HIS_ARCHIVE AS
--   SELECT * FROM TBOT_WORD_HIS WHERE INSERT_DATE < SYSDATE - &KEEP_DAYS;

-- [2단계] 배치 삭제 (5000행 단위 커밋)
SET SERVEROUTPUT ON
DECLARE
  v_deleted NUMBER := 0;
  v_batch   NUMBER;
BEGIN
  LOOP
    DELETE FROM TBOT_WORD_HIS
     WHERE INSERT_DATE < SYSDATE - &KEEP_DAYS
       AND ROWNUM <= 5000;
    v_batch := SQL%ROWCOUNT;
    COMMIT;
    v_deleted := v_deleted + v_batch;
    EXIT WHEN v_batch = 0;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('deleted rows: ' || v_deleted);
END;
/

-- [3단계] 삭제 후 공간 회수(선택). 삭제만으로는 테이블 세그먼트 크기가 줄지 않는다.
-- 행 이동이 생기므로 인덱스가 UNUSABLE 이 될 수 있어 SHRINK 후 인덱스 상태를 확인할 것.
-- ALTER TABLE TBOT_WORD_HIS ENABLE ROW MOVEMENT;
-- ALTER TABLE TBOT_WORD_HIS SHRINK SPACE CASCADE;

-- [4단계] 통계 갱신 (실행계획이 새 행 수를 알도록)
-- BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, 'TBOT_WORD_HIS', CASCADE => TRUE); END;
-- /
