-- [2026-09-30] 시즌5 전용 로그 테이블 TBOT_S5_WORD_HIS
--
-- 배경: 시즌5(람쥐탑) 명령/웹 액션 로그가 TBOT_WORD_HIS(157만 행/2.1GB, 다른 시즌 로그와 혼재)에
-- 섞여 있어 로그 뷰어/요약 집계가 무거워질 수 있고 정리도 어렵다. 시즌5만 이 테이블에 쌓고
-- (앱이 매일 새벽 4시 30일 초과분 자동 삭제), 기존 TBOT_WORD_HIS 는 시즌5가 아닌 로그만 받는다.
-- 컬럼은 TBOT_WORD_HIS 와 같은 타입(REQ/USER_NAME NVARCHAR2, RES NCLOB)이라 저장 핸들러도 동일.
--
-- 주의: sqlplus 는 세미콜론 뒤 같은 줄 주석을 ORA-00911 로 실패시킨다. 주석은 항상 별도 줄에 둘 것.

-- [1단계] 테이블/인덱스 (없을 때만 생성)
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_WORD_HIS';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_WORD_HIS ('
      || 'ROOM_NAME VARCHAR2(100), '
      || 'USER_NAME NVARCHAR2(100), '
      || 'REQ NVARCHAR2(1000), '
      || 'RES NCLOB, '
      || 'INSERT_DATE DATE DEFAULT SYSDATE NOT NULL)';
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_S5_WORD_HIS_DATE ON TBOT_S5_WORD_HIS (INSERT_DATE)';
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_S5_WORD_HIS_USER_DATE ON TBOT_S5_WORD_HIS (USER_NAME, INSERT_DATE)';
  END IF;
END;
/

-- [2단계] 기존 로그 이관 (복사만 함, 원본은 건드리지 않음)
-- 시즌5 유저(TBOT_S5_USER_PROGRESS)의 최근 30일 로그를 복사한다. 그 유저가 시즌5가 아닌 명령을
-- 쓴 로그도 함께 복사될 수 있다(30일 뒤 자동 삭제되므로 무해). 비어 있을 때만 실행되도록 막아둠.
-- 배포 직전에 한 번 더 돌리면 그 사이 옛 테이블에 쌓인 시즌5 로그를 이어 붙일 수 있다:
-- 그 경우엔 아래 WHERE 의 SYSDATE - 30 자리에 "마지막으로 이관한 시각"을 넣을 것.
INSERT INTO TBOT_S5_WORD_HIS (ROOM_NAME, USER_NAME, REQ, RES, INSERT_DATE)
SELECT A.ROOM_NAME, A.USER_NAME, A.REQ, A.RES, A.INSERT_DATE
  FROM TBOT_WORD_HIS A
 WHERE A.INSERT_DATE >= SYSDATE - 30
   AND A.USER_NAME IN (SELECT P.USER_NAME FROM TBOT_S5_USER_PROGRESS P)
   AND NOT EXISTS (SELECT 1 FROM TBOT_S5_WORD_HIS X WHERE ROWNUM = 1);

COMMIT;

SELECT COUNT(*) AS S5_WORD_HIS_ROWS FROM TBOT_S5_WORD_HIS;
