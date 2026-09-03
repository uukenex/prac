-- ============================================================
-- Season 5 - live DB migration: 3-tier cooldown restructure.
--   1) TBOT_S5_USER_PROGRESS.NEXT_COOLDOWN_SEC (new column) -- stores which
--      cooldown applies to the NEXT dice action, decided at action time
--      rather than re-derived from current STATUS (needed because "combat
--      just ended this turn" and "ordinary board move" are both
--      STATUS=NORMAL afterwards but use different cooldowns).
--   2) TBOT_S5_CONFIG: MOVE_COOLDOWN_SEC 100->15, add COMBAT_END_COOLDOWN_SEC=100
--      (COMBAT_COOLDOWN_SEC stays 15, unchanged).
-- ASCII-only file, safe to re-run (checks existence first / MERGE for config).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NEXT_COOLDOWN_SEC';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (NEXT_COOLDOWN_SEC NUMBER)';
    END IF;
END;
/

UPDATE TBOT_S5_CONFIG SET CONFIG_VALUE = '15' WHERE CONFIG_KEY = 'MOVE_COOLDOWN_SEC';

MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'COMBAT_END_COOLDOWN_SEC' AS K FROM DUAL) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN
    UPDATE SET CONFIG_VALUE = '100'
WHEN NOT MATCHED THEN
    INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO)
    VALUES ('COMBAT_END_COOLDOWN_SEC', '100', 'cooldown seconds applied right after combat ends (kill or party wipe), separate from the ongoing per-turn combat cooldown');

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NEXT_COOLDOWN_SEC';
SELECT CONFIG_KEY, CONFIG_VALUE FROM TBOT_S5_CONFIG ORDER BY CONFIG_KEY;
EXIT;
