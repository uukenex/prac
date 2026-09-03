-- ============================================================
-- Season 5 - live DB migration: per-user regenerating board + elite monster room.
--   1) TBOT_S5_USER_TILE_MASTER (new table) -- per (user, floor) tile layout,
--      generated lazily by ensureUserBoard() on first visit, deleted whenever
--      the user returns to the village (changeFloor()) or a block is reset
--      after a boss kill (resetBlockExploration()), so it's freshly re-rolled
--      on the next expedition. TBOT_S5_TILE_MASTER (the old global board) is
--      kept around unused rather than dropped.
--   2) TBOT_S5_USER_PROGRESS.CUR_MONSTER_ELITE_YN (new column) -- flags whether
--      the monster currently in combat is from an ELITE tile (2x stats/reward),
--      needed because ATK/DEF/reward are recomputed from monster master data
--      every turn (only HP is stored once at combat start).
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_USER_TILE_MASTER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_USER_TILE_MASTER (
            USER_NAME  VARCHAR2(100) NOT NULL,
            FLOOR      NUMBER        NOT NULL,
            TILE_NO    NUMBER        NOT NULL,
            TILE_TYPE  VARCHAR2(10)  NOT NULL,
            PRIMARY KEY (USER_NAME, FLOOR, TILE_NO)
        )';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER_ELITE_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (CUR_MONSTER_ELITE_YN CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMIT;

SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_USER_TILE_MASTER';
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER_ELITE_YN';
EXIT;
