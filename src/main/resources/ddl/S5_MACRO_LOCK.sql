-- ============================================================
-- Macro/bot detection + auto lockout for Season 5 (2026-09-05 request).
--
-- Trigger: user "PalSeJjokIssEum" (Korean nickname) observed firing
-- /dice-equivalent web actions at an almost perfectly uniform ~4-second
-- cadence for 20+ minutes straight (confirmed live via TBOT_WORD_HIS) --
-- a pattern no human sustains. Also self-reported "over 1000 rolls" in
-- a result message.
--
-- Design: track the raw interval between consecutive rollDice() calls
-- (regardless of whether the call succeeds or gets cooldown-rejected --
-- a macro pings on a fixed timer either way). If N consecutive intervals
-- are all within ~1 second of each other (and fast enough, <=20s, to be
-- automation-speed rather than normal play), flag SUSPEND_YN='Y'. Any
-- further attempt while SUSPEND_YN='Y' escalates straight to
-- BAN_YN='Y' (permanent). BAN_YN blocks every /dice attempt outright.
--
-- LAST_REQUEST_DATE is deliberately separate from the existing
-- LAST_DICE_ACTION_DATE (which only advances on actions that clear
-- cooldown) so this measures true client request cadence, not just
-- successful-action cadence.
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
    add_col_if_missing('LAST_REQUEST_DATE',        'LAST_REQUEST_DATE DATE NULL');
    add_col_if_missing('LAST_REQUEST_INTERVAL_SEC', 'LAST_REQUEST_INTERVAL_SEC NUMBER NULL');
    add_col_if_missing('MACRO_STREAK',             'MACRO_STREAK NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('SUSPEND_YN',               'SUSPEND_YN CHAR(1) DEFAULT ''N'' NOT NULL');
    add_col_if_missing('BAN_YN',                   'BAN_YN CHAR(1) DEFAULT ''N'' NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.LAST_REQUEST_DATE IS 'timestamp of the last rollDice() call attempt, success or not -- used for macro cadence detection';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.LAST_REQUEST_INTERVAL_SEC IS 'seconds between the previous two rollDice() call attempts';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.MACRO_STREAK IS 'consecutive suspiciously-uniform request intervals, reset on a natural gap';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.SUSPEND_YN IS 'Y = auto-suspended on suspected macro use, blocks /dice until an admin clears it';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.BAN_YN IS 'Y = permanently banned (macro use confirmed by retrying while suspended), blocks /dice permanently';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('LAST_REQUEST_DATE','LAST_REQUEST_INTERVAL_SEC','MACRO_STREAK','SUSPEND_YN','BAN_YN')
  ORDER BY COLUMN_NAME;
EXIT;
