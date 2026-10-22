-- ============================================================
-- Season 5 - exclude test/admin accounts from /탑랭킹 (2026-10-22).
--  TBOT_S5_CONFIG.RANK_EXCLUDE_USERS: '|' separated user names left out of every ranking aggregate.
--  Value below = '일어난다람쥐/카단' as CP949 bytes (ASCII-only file, no client charset conversion involved).
--  Picked up at server start or by /갱신. The running old code ignores unknown config keys.
-- Safe to re-run.
-- ============================================================
MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'RANK_EXCLUDE_USERS' AS K, UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC')) AS V FROM DUAL) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN UPDATE SET T.CONFIG_VALUE = S.V, T.UPDATE_DATE = SYSDATE
WHEN NOT MATCHED THEN INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO, UPDATE_DATE) VALUES (S.K, S.V, 'ranking exclude users (| separated)', SYSDATE);
COMMIT;
SELECT CONFIG_KEY, RAWTOHEX(UTL_RAW.CAST_TO_RAW(CONFIG_VALUE)) AS HEX_VALUE FROM TBOT_S5_CONFIG WHERE CONFIG_KEY = 'RANK_EXCLUDE_USERS';
EXIT;
