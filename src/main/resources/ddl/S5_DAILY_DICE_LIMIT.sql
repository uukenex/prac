-- ============================================================
-- Season 5 - live DB migration: daily dice-roll cap (500/day per user).
-- TBOT_S5_USER_PROGRESS.DICE_ROLL_COUNT_TODAY (rolls so far "today") +
-- DICE_ROLL_DATE (which calendar day that count applies to, compared by
-- yyyyMMdd in Java -- NULL initially means "never rolled", treated as a
-- fresh day on the first roll). BotS5ServiceImpl.rollDice() checks/bumps
-- this right after the existing per-action cooldown check passes, so a
-- cooldown-blocked attempt never counts against the daily cap. Accounts
-- with NO_COOLDOWN_YN='Y' (admin test accounts) are exempt from this cap
-- too, same rationale as their cooldown exemption.
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'DICE_ROLL_COUNT_TODAY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (DICE_ROLL_COUNT_TODAY NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'DICE_ROLL_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (DICE_ROLL_DATE DATE)';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME IN ('DICE_ROLL_COUNT_TODAY', 'DICE_ROLL_DATE');
EXIT;
