-- ============================================================
-- TBOT_WORD_HIS space reclaim + lookup index (2026-10-07, DB performance review).
-- Why: ~275K rows (about 40MB of data) but the table segment was 2.1GB because rows deleted by the 30-day purge never gave space back
-- (MANUAL segment space management tablespace, so SHRINK SPACE is not available). selectIssueCase also did two full scans.
-- What: ALTER TABLE MOVE rebuilds the segment compactly (DML on the table is blocked while it runs; the app swallows log-insert errors),
-- then the indexes are rebuilt/created. The new function index (TRIM(REQ), INSERT_DATE) serves the selectIssueCase fallback lookup.
-- Indexes are moved into TS_ALL_D01 because USERS has too little free space to rebuild in place.
-- Safe to re-run: MOVE is repeatable, the index is created only when missing.
-- ============================================================
SET TIMING ON
SET SERVEROUTPUT ON
ALTER TABLE TBOT_WORD_HIS MOVE;
ALTER INDEX IDX_WORD_HIS_USER_DATE REBUILD TABLESPACE TS_ALL_D01;
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_WORD_HIS_REQ_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_WORD_HIS_REQ_DATE ON TBOT_WORD_HIS (TRIM(REQ), INSERT_DATE) TABLESPACE TS_ALL_D01';
    END IF;
END;
/
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'TBOT_WORD_HIS', CASCADE => TRUE);
END;
/
SELECT INDEX_NAME, STATUS FROM USER_INDEXES WHERE TABLE_NAME = 'TBOT_WORD_HIS';
SELECT SEGMENT_NAME, ROUND(BYTES/1024/1024) MB FROM USER_SEGMENTS WHERE SEGMENT_NAME IN ('TBOT_WORD_HIS', 'IDX_WORD_HIS_USER_DATE', 'IDX_WORD_HIS_REQ_DATE');
SELECT COUNT(*) CNT FROM TBOT_WORD_HIS;
