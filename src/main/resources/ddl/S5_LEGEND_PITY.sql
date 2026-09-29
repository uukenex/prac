-- ============================================================
-- Season 5 - legendary craft "pity" counter (2026-09-29).
-- Request: raise disenchant refund to 15 fragments + failure-streak bonus
-- (each consecutive craft failure adds +10 %p to the next success chance,
-- reset to 0 on success). LEGEND_PITY = consecutive failures so far.
-- Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LEGEND_PITY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (LEGEND_PITY NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LEGEND_PITY';
