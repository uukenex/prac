-- ============================================================
-- TBOT_S5_USER_PROGRESS.CUR_AMBUSH_DEAD_CIDS (2026-10-08)
-- Companion ids (comma separated) that were killed by the turn-1 stealth ambush (floor 101+ ambush can kill) in the current battle.
-- From turn 2 on, a living star-5+ priest gets the usual revive roll (10% / 15%) for each of them; cleared when the battle ends.
-- Add the column BEFORE deploying the code that reads/writes it. Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_AMBUSH_DEAD_CIDS';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (CUR_AMBUSH_DEAD_CIDS VARCHAR2(100))';
    END IF;
END;
/
