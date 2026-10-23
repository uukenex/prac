-- ============================================================
-- Season 5 - web mine pickaxe durability (2026-10-23). Additive; the old app ignores the new columns.
--  "stamina -> pickaxe durability, start at 1000, set every user's current/max durability to 1000; repair at the village blacksmith with ores".
--  TBOT_S5_MINE_USER.PICK_DUR     : current durability (1 per dig, no time regen, no midnight reset)
--  TBOT_S5_MINE_USER.PICK_DUR_MAX : max durability (repair goes up to this)
--  The old STAMINA / STAMINA_AT_MS columns are no longer read.
--  The values of existing users are set to 1000/1000 only when the columns are created (re-running does not reset them).
--  The 2-day map reset cycle is stored by the app in TBOT_S5_CONFIG.MINE_RESET_CYCLE (no script needed).
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_MINE_USER' AND COLUMN_NAME = 'PICK_DUR';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_MINE_USER ADD (PICK_DUR NUMBER(7) DEFAULT 1000 NOT NULL, PICK_DUR_MAX NUMBER(7) DEFAULT 1000 NOT NULL)';
        EXECUTE IMMEDIATE 'UPDATE TBOT_S5_MINE_USER SET PICK_DUR = 1000, PICK_DUR_MAX = 1000';
        COMMIT;
    END IF;
END;
/
SELECT COUNT(*) AS USERS, MIN(PICK_DUR) AS MIN_DUR, MAX(PICK_DUR) AS MAX_DUR, MIN(PICK_DUR_MAX) AS MIN_MAX, MAX(PICK_DUR_MAX) AS MAX_MAX FROM TBOT_S5_MINE_USER;
EXIT;
