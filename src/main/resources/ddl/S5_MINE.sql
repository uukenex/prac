-- ============================================================
-- Season 5 - web mine (2D top-down ore digging on one shared 255x255 map, 2026-10-23). Additive only; old code ignores it.
--  Map file: classpath s5/mine_map_255.txt (terrain + ore grade per cell, same for every user).
--  TBOT_S5_MINE_USER: per-user stamina, character position (saved when leaving the mine) and today's dug cells.
--    STAMINA        : stamina at STAMINA_AT_MS (max 30, +2 at every o'clock, reset to 30 at 00:00 KST; computed lazily)
--    STAMINA_AT_MS  : epoch millis of the last stamina write (no DATE column, so DB/JVM time zone does not matter)
--    POS_X, POS_Y   : character tile (NULL = village spawn); POS_AT_MS = when it was saved (movement speed check)
--    MAP_DAY        : yyyyMMdd (KST) of MINED; each user can dig each cell once per day
--    MINED          : comma separated cell indexes (y * 255 + x) dug on MAP_DAY
--  TBOT_S5_USER_ORE: ore inventory (one row per user and ore code).
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_MINE_USER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_MINE_USER (USER_NAME VARCHAR2(100) PRIMARY KEY, '
            || 'STAMINA NUMBER(4) NOT NULL, STAMINA_AT_MS NUMBER(15) NOT NULL, POS_X NUMBER(4) NULL, POS_Y NUMBER(4) NULL, '
            || 'POS_AT_MS NUMBER(15) NULL, MAP_DAY VARCHAR2(8) NULL, '
            || 'MINED VARCHAR2(4000) NULL, TOTAL_DIG NUMBER DEFAULT 0 NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_USER_ORE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_USER_ORE (USER_NAME VARCHAR2(100) NOT NULL, ORE_CODE VARCHAR2(20) NOT NULL, '
            || 'QTY NUMBER DEFAULT 0 NOT NULL, CONSTRAINT PK_S5_USER_ORE PRIMARY KEY (USER_NAME, ORE_CODE))';
    END IF;
END;
/
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME IN ('TBOT_S5_MINE_USER', 'TBOT_S5_USER_ORE');
EXIT;
