-- ============================================================
-- Season 5 - battle stat source flag (2026-09-30).
-- SRC = 'LIVE' rows written by the app at fight end; 'LOG' rows reconstructed by the
-- admin backfill (/통계집계) from past combat log text. Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_BATTLE_STAT' AND COLUMN_NAME = 'SRC';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_BATTLE_STAT ADD (SRC VARCHAR2(4) DEFAULT ''LIVE'' NOT NULL)';
    END IF;
END;
/

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_BATTLE_STAT' AND COLUMN_NAME = 'SRC';
