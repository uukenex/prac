-- ============================================================
-- TSHAREBOARD_HIST LOB space reclaim (2026-10-07, DB performance review).
-- The SHARE_CONTENT NCLOB held about 91MB of real text in a 1.79GB LOB segment (space of rewritten/deleted versions was never returned; the
-- tablespace uses MANUAL segment space management so SHRINK SPACE is not available). MOVE LOB rebuilds the LOB segment compactly in the same tablespace.
-- Safe to re-run.
-- ============================================================
SET TIMING ON
ALTER TABLE TSHAREBOARD_HIST MOVE LOB(SHARE_CONTENT) STORE AS (TABLESPACE TS_ALL_D01);
-- NOTE: in practice the primary key index (SYS_C004425) became UNUSABLE by this MOVE LOB (DML on the table fails with ORA-01502 until rebuilt), so it is rebuilt right away.
BEGIN
    FOR r IN (SELECT INDEX_NAME FROM USER_INDEXES WHERE TABLE_NAME = 'TSHAREBOARD_HIST' AND STATUS = 'UNUSABLE') LOOP
        EXECUTE IMMEDIATE 'ALTER INDEX ' || r.INDEX_NAME || ' REBUILD';
    END LOOP;
END;
/
SELECT INDEX_NAME, STATUS FROM USER_INDEXES WHERE TABLE_NAME = 'TSHAREBOARD_HIST';
SELECT SEGMENT_NAME, SEGMENT_TYPE, ROUND(BYTES/1024/1024) MB FROM USER_SEGMENTS WHERE SEGMENT_NAME IN (SELECT SEGMENT_NAME FROM USER_LOBS WHERE TABLE_NAME = 'TSHAREBOARD_HIST');
SELECT COUNT(*) CNT FROM TSHAREBOARD_HIST;
