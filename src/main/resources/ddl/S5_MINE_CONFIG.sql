-- ============================================================
-- Season 5 - web mine open switch (2026-10-23). Re-runnable.
--  MINE_OPEN_YN     : 'Y' = mine / mine shop open to everyone, 'N' (default) = only MINE_ADMIN_USERS
--  MINE_ADMIN_USERS : '|' separated user names allowed before the open (admin test account)
--  Apply with /갱신 (refreshConfig) after changing. Korean user name is inserted as CP949 bytes (ASCII-only file).
--  To open for everyone: UPDATE TBOT_S5_CONFIG SET CONFIG_VALUE = 'Y', UPDATE_DATE = SYSDATE WHERE CONFIG_KEY = 'MINE_OPEN_YN'; COMMIT; then /갱신
-- ============================================================
MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'MINE_OPEN_YN' AS K FROM DUAL) S ON (T.CONFIG_KEY = S.K)
WHEN NOT MATCHED THEN INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO, UPDATE_DATE)
VALUES ('MINE_OPEN_YN', 'N', 'web mine / mine shop open to everyone (Y/N); N = only MINE_ADMIN_USERS', SYSDATE);

MERGE INTO TBOT_S5_CONFIG T
USING (SELECT 'MINE_ADMIN_USERS' AS K FROM DUAL) S ON (T.CONFIG_KEY = S.K)
WHEN NOT MATCHED THEN INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO, UPDATE_DATE)
VALUES ('MINE_ADMIN_USERS', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC')), 'users allowed in the web mine before it opens (| separated)', SYSDATE);
COMMIT;
SELECT CONFIG_KEY, CONFIG_VALUE, RAWTOHEX(UTL_RAW.CAST_TO_RAW(CONFIG_VALUE)) HEX FROM TBOT_S5_CONFIG WHERE CONFIG_KEY IN ('MINE_OPEN_YN', 'MINE_ADMIN_USERS');
EXIT;
