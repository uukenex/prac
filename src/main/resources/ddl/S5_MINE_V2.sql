-- ============================================================
-- Season 5 - web mine v2 (2026-10-23). Additive only; run after S5_MINE.sql.
--  * several maps (field + cave1, more later): TBOT_S5_MINE_USER.MAP_ID = map the character is on (NULL = field)
--  * dug cells are permanent per user (00:00 only resets stamina): TBOT_S5_MINE_DUG, one row per (user, map, cell)
--    (TBOT_S5_MINE_USER.MAP_DAY / MINED from v1 are no longer used)
--  * mine shop: ore -> permanent hourly PP bonus, paid with the auto hunt settlement: TBOT_S5_MINE_USER.PP_BONUS (PP per hour)
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_MINE_USER' AND COLUMN_NAME = 'MAP_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_MINE_USER ADD (MAP_ID VARCHAR2(20) NULL)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_MINE_USER' AND COLUMN_NAME = 'PP_BONUS';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_MINE_USER ADD (PP_BONUS NUMBER DEFAULT 0 NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_MINE_DUG';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_MINE_DUG (USER_NAME VARCHAR2(100) NOT NULL, MAP_ID VARCHAR2(20) NOT NULL, '
            || 'CELL NUMBER(8) NOT NULL, ORE_GRADE NUMBER(2) NOT NULL, DUG_AT_MS NUMBER(15) NOT NULL, '
            || 'CONSTRAINT PK_S5_MINE_DUG PRIMARY KEY (USER_NAME, MAP_ID, CELL))';
    END IF;
END;
/
SELECT TABLE_NAME, COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME IN ('TBOT_S5_MINE_USER', 'TBOT_S5_MINE_DUG') ORDER BY TABLE_NAME, COLUMN_ID;
EXIT;
