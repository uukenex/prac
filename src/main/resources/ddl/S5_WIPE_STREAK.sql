-- ============================================================
-- Season 5 - track consecutive party-wipe streak per user, so the game can
-- hint "/towndown" (go back down a block) after repeated deaths on the
-- early floors (1-4) of a block (2026-09-05 request).
--
-- WIPE_STREAK_CUR: bumped +1 every party wipe, reset to 0 on any monster
-- kill (see resolveCombatTurn). Only checked against floors 1-4 of the
-- current block when deciding whether to show the hint -- the counter
-- itself is not floor-scoped (simplest to reason about: any win resets it).
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('WIPE_STREAK_CUR', 'WIPE_STREAK_CUR NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WIPE_STREAK_CUR IS 'consecutive party-wipe count, reset to 0 on any monster kill; used to hint /towndown after repeated deaths';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME = 'WIPE_STREAK_CUR';
EXIT;
