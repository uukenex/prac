-- ============================================================
-- Season 5 - macro suspend->ban grace period + 1-hour ban auto-unlock (2026-09-09 request)
--
-- Before this change, a SUSPENDED (SUSPEND_YN='Y') account got escalated to
-- permanent BAN (BAN_YN='Y') on the very next request -- zero grace attempts.
-- Real reports showed normal users tripping this: they get suspended, come
-- back later (not macroing), and the first retry instantly bans them.
--
-- Fix: SUSPEND_RETRY_COUNT now counts attempts made while already suspended;
-- only the SUSPEND_BAN_GRACE-th (=3, see BotS5ServiceImpl.SUSPEND_BAN_GRACE)
-- attempt actually escalates to a ban. And a ban itself is no longer forever:
-- BAN_DATE records when it was applied, and checkMacroLock() auto-clears
-- BAN_YN/SUSPEND_YN/MACRO_STREAK/SUSPEND_RETRY_COUNT once BAN_AUTO_UNLOCK_HOURS
-- (=1 hour) has passed since BAN_DATE.
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
    add_col_if_missing('SUSPEND_RETRY_COUNT', 'SUSPEND_RETRY_COUNT NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('BAN_DATE', 'BAN_DATE DATE');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.SUSPEND_RETRY_COUNT IS 'requests made while SUSPEND_YN=Y; reaching SUSPEND_BAN_GRACE(=3) escalates to BAN_YN=Y';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.BAN_DATE IS 'when BAN_YN was set to Y; checkMacroLock() auto-clears the ban BAN_AUTO_UNLOCK_HOURS(=1h) after this';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('SUSPEND_RETRY_COUNT','BAN_DATE') ORDER BY COLUMN_NAME;
EXIT;
