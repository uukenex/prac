-- ============================================================
-- Season 5 - web mine combat level (2026-10-24). Additive; the old app ignores the new columns.
--  "monsters on the mine maps, Baram-style combat, no monster first strike, mine-only level".
--  TBOT_S5_MINE_USER.M_LV       : mine combat level (starts at 1)
--  TBOT_S5_MINE_USER.M_EXP      : exp inside the current level
--  TBOT_S5_MINE_USER.M_HP       : current HP (NULL = full)
--  TBOT_S5_MINE_USER.M_HP_AT_MS : when M_HP was stored (HP regenerates from there, full in 60s)
--  TBOT_S5_MINE_USER.M_KILLS    : total monsters defeated
--  Monster HP / respawn state is kept in app memory (no table).
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_MINE_USER' AND COLUMN_NAME = 'M_LV';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_MINE_USER ADD (M_LV NUMBER(4) DEFAULT 1 NOT NULL, M_EXP NUMBER(12) DEFAULT 0 NOT NULL, '
            || 'M_HP NUMBER(9), M_HP_AT_MS NUMBER(15), M_KILLS NUMBER(9) DEFAULT 0 NOT NULL)';
    END IF;
END;
/
SELECT COUNT(*) AS USERS, MIN(M_LV) AS MIN_LV, MAX(M_LV) AS MAX_LV, SUM(M_KILLS) AS KILLS FROM TBOT_S5_MINE_USER;
EXIT;
