-- ============================================================
-- Season 5 - hourly/daily dice-roll cap config (2026-09-04)
--
-- Was a hardcoded `private static final int DAILY_DICE_LIMIT = 500;` in
-- BotS5ServiceImpl.checkAndBumpDailyDiceLimit(). Moved into TBOT_S5_CONFIG
-- alongside the cooldown values (MOVE_COOLDOWN_SEC etc.) so it can be
-- changed without a redeploy -- loadConfig()/@PostConstruct reads it at
-- boot, and the live /gaengsin(refresh) chat command re-reads it afterwards.
--
-- Value below (750) matches the requested bump from 500 -> 750.
-- Re-running this file is safe (MERGE, idempotent). ASCII-only value/key,
-- no Korean literal risk here.
-- ============================================================

MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'DAILY_DICE_LIMIT' AS K FROM DUAL) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN
    UPDATE SET CONFIG_VALUE = '750', UPDATE_DATE = SYSDATE
WHEN NOT MATCHED THEN
    INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO)
    VALUES ('DAILY_DICE_LIMIT', '750', 'daily dice roll cap (move+combat combined), was hardcoded 500');

COMMIT;

SELECT CONFIG_KEY, CONFIG_VALUE FROM TBOT_S5_CONFIG WHERE CONFIG_KEY = 'DAILY_DICE_LIMIT';
EXIT;
