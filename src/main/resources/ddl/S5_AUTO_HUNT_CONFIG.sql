-- ============================================================
-- Season 5 - auto-hunt (offline settlement) rate/cap config (2026-09-05)
--
-- Was hardcoded in BotS5ServiceImpl.settleAutoHunt(): "10 min per kill"
-- (=6 kills/hour) and an 8-hour cap on how much offline time counts.
-- Moved into TBOT_S5_CONFIG alongside the other tunables (cooldowns,
-- DAILY_DICE_LIMIT) so it can be changed without a redeploy --
-- loadConfig()/@PostConstruct reads it at boot, /gaengsin(refresh) chat
-- command re-reads it afterwards.
--
-- AUTO_HUNT_KILLS_PER_HOUR: kills credited per hour of offline time.
-- AUTO_HUNT_MAX_HOURS: offline time cap fed into settlement (beyond this,
-- more idle time doesn't add more kills).
--
-- Values below (6, 8) match the current hardcoded behavior exactly --
-- this migration only moves them to config, it does not change balance.
-- Re-running this file is safe (MERGE, idempotent). ASCII-only.
-- ============================================================

MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'AUTO_HUNT_KILLS_PER_HOUR' AS K FROM DUAL) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN
    UPDATE SET CONFIG_VALUE = '6', UPDATE_DATE = SYSDATE
WHEN NOT MATCHED THEN
    INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO)
    VALUES ('AUTO_HUNT_KILLS_PER_HOUR', '6', 'auto-hunt kills credited per offline hour, was hardcoded 6 (10 min/kill)');

MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'AUTO_HUNT_MAX_HOURS' AS K FROM DUAL) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN
    UPDATE SET CONFIG_VALUE = '8', UPDATE_DATE = SYSDATE
WHEN NOT MATCHED THEN
    INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO)
    VALUES ('AUTO_HUNT_MAX_HOURS', '8', 'auto-hunt offline-time cap in hours, was hardcoded 8');

COMMIT;

SELECT CONFIG_KEY, CONFIG_VALUE FROM TBOT_S5_CONFIG
WHERE CONFIG_KEY IN ('AUTO_HUNT_KILLS_PER_HOUR', 'AUTO_HUNT_MAX_HOURS');
EXIT;
