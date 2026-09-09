-- ============================================================
-- Season 5 - special tile grants "roll two dice next move" (2026-09-09 request)
--
-- Landing on a SPECIAL tile now also arms a one-shot flag: the user's very next
-- movement roll (rollDiceInternal) rolls the currently-equipped dice twice and
-- sums the two faces instead of rolling once. Can move further (good) or
-- overshoot a tile you wanted to land on / walk into an unwanted monster tile
-- (bad) -- intentionally a "could go either way" effect, not a pure buff.
-- See BotS5ServiceImpl.handleSpecialTile() / rollDiceInternal().
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('DOUBLE_DICE_YN', 'DOUBLE_DICE_YN CHAR(1) DEFAULT ''N'' NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DOUBLE_DICE_YN IS 'Y = next movement dice roll rolls twice and sums (one-shot flag, set by landing on a SPECIAL tile, consumed by the next roll)';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME = 'DOUBLE_DICE_YN';
EXIT;
