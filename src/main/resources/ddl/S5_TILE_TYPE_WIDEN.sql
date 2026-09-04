-- ============================================================
-- Season 5 - CRITICAL live DB fix: TBOT_S5_USER_TILE_MASTER.TILE_TYPE was
-- VARCHAR2(10), but the new 'STAIRS_DOWN' tile type is 11 characters long
-- (VARCHAR2(9) 'STAIRS_UP' fit, 'STAIRS_DOWN' does not) -- every attempt to
-- generate a fresh per-user board (ensureUserBoard(), called from
-- changeFloor()/rollDice()/the SPA board view) threw ORA-12899 "value too
-- large for column" as soon as it tried to insert a STAIRS_DOWN row, which
-- surfaced to players as a 500 error on floor movement. Widened to
-- VARCHAR2(15) for headroom against future tile-type names.
--
-- ASCII-only file, safe to re-run (checks current width first).
-- ============================================================

DECLARE
    v_len NUMBER;
BEGIN
    SELECT CHAR_LENGTH INTO v_len FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_TILE_MASTER' AND COLUMN_NAME = 'TILE_TYPE';
    IF v_len < 15 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_TILE_MASTER MODIFY (TILE_TYPE VARCHAR2(15))';
    END IF;
END;
/

-- TBOT_S5_TILE_MASTER (old global board table, unused for gameplay now but
-- kept around) had the same VARCHAR2(10) definition -- widen too for
-- consistency/safety even though nothing writes STAIRS_DOWN into it anymore.
DECLARE
    v_len NUMBER;
BEGIN
    SELECT CHAR_LENGTH INTO v_len FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_TILE_MASTER' AND COLUMN_NAME = 'TILE_TYPE';
    IF v_len < 15 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_TILE_MASTER MODIFY (TILE_TYPE VARCHAR2(15))';
    END IF;
END;
/

COMMIT;

SELECT TABLE_NAME, COLUMN_NAME, CHAR_LENGTH FROM USER_TAB_COLUMNS
WHERE TABLE_NAME IN ('TBOT_S5_USER_TILE_MASTER', 'TBOT_S5_TILE_MASTER') AND COLUMN_NAME = 'TILE_TYPE';
EXIT;
