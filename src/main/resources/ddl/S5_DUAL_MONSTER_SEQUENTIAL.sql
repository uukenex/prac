-- ============================================================
-- Season 5 - block7+(61-70) "dual monster" sequential kill (2026-09-12 request)
--
-- "When two monsters appear, hit monster I first; once I dies, hit II."
--
-- Previously the dual-monster encounter pooled HP into one value
-- (HP x2, one death event). This split it into two separate HP pools
-- so monster I must be fully killed before monster II takes damage.
-- Monster I still uses the existing CUR_MONSTER_HP_VALUE column;
-- this migration adds CUR_MONSTER2_HP_VALUE for monster II (NULL =
-- no second monster pending, i.e. not a dual encounter or already
-- promoted/consumed). See resolveCombatTurn()/startCombat() in
-- BotS5ServiceImpl.java.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER2_HP_VALUE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (CUR_MONSTER2_HP_VALUE NUMBER NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_MONSTER2_HP_VALUE IS 'Block7+ dual-monster encounter: monster II HP, waiting until monster I (CUR_MONSTER_HP_VALUE) dies; NULL = not applicable / already promoted';

COMMIT;

SELECT COLUMN_NAME, NULLABLE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER2_HP_VALUE';
EXIT;
