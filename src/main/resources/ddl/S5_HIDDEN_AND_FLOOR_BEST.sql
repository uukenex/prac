-- ============================================================
-- Season 5 - live DB migration for already-deployed installs (fresh installs
-- get these from S5_DDL.sql directly, which has already been updated too):
--   1) TBOT_S5_USER_COMPANION.HIDDEN_YN  -- "/동료가리기 N" hide-from-list toggle
--   2) TBOT_S5_USER_FLOOR_BEST           -- best-ever exploration % per (user,floor),
--      never wiped by village-return resets; also backs the new
--      "N층 완전탐사" achievements (see S5_FLOOR_EXPLORE_ACHIEVEMENTS.sql)
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME = 'HIDDEN_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_COMPANION ADD (HIDDEN_YN VARCHAR2(1) DEFAULT ''N'')';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_USER_FLOOR_BEST';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_USER_FLOOR_BEST (
                USER_NAME            VARCHAR2(100) NOT NULL,
                FLOOR                NUMBER        NOT NULL,
                BEST_VISITED_COUNT   NUMBER        DEFAULT 0 NOT NULL,
                TILE_COUNT           NUMBER        DEFAULT 0 NOT NULL,
                FULLY_EXPLORED_YN    VARCHAR2(1)   DEFAULT ''N'' NOT NULL,
                UPDATE_DATE          DATE DEFAULT SYSDATE,
                PRIMARY KEY (USER_NAME, FLOOR)
            )';
    END IF;
END;
/

COMMIT;

-- 결과 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME = 'HIDDEN_YN';
SELECT COUNT(*) AS TABLE_EXISTS FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_USER_FLOOR_BEST';
EXIT;
