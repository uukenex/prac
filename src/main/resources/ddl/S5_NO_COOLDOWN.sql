-- ============================================================
-- Season 5 - live DB migration: TBOT_S5_USER_PROGRESS.NO_COOLDOWN_YN
-- Per-user cooldown exemption (admin-granted only, no chat command exposes
-- it) -- when 'Y', checkDiceCooldown() skips the move/combat wait entirely
-- for that one user. ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NO_COOLDOWN_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (NO_COOLDOWN_YN CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NO_COOLDOWN_YN';
EXIT;
