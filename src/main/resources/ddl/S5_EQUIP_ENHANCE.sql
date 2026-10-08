-- ============================================================
-- Season 5 - legendary equipment enhancement (2026-10-08)
-- TBOT_S5_USER_EQUIP.ENHANCE_LEVEL : current enhancement level of this item (0 = not enhanced, no upper limit).
-- TBOT_S5_USER_EQUIP.ENHANCE_PITY  : consecutive failed attempts on this item (each one adds a bonus to the next success rate; reset on success).
-- Only star-7 legendary items are enhanced by the code, the columns exist on every row (default 0).
-- Add the columns BEFORE deploying the code that reads/writes them. Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_EQUIP' AND COLUMN_NAME = 'ENHANCE_LEVEL';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_EQUIP ADD (ENHANCE_LEVEL NUMBER DEFAULT 0 NOT NULL)';
    END IF;
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_EQUIP' AND COLUMN_NAME = 'ENHANCE_PITY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_EQUIP ADD (ENHANCE_PITY NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/
