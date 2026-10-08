-- ============================================================
-- TBOT_S5_USER_PROGRESS.WARD_ROBE_YN (2026-10-08)
-- 'Y' when the current one-hit ward (WARD_COMPANION_ID) came from the legendary robe START_BARRIER (not from a lucky tile).
-- When a battle ends (clearMonster) a robe ward that was never used is removed, so every battle starts with a fresh barrier and its message.
-- The lucky-tile ward keeps working as before (it survives until it blocks a hit).
-- Add the column BEFORE deploying the code that reads/writes it. Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'WARD_ROBE_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (WARD_ROBE_YN VARCHAR2(1))';
    END IF;
END;
/
