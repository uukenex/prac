-- ============================================================
-- Season 5 - per-companion image override (2026-10-21): "swap portraits between companions" feature.
--  TBOT_S5_USER_COMPANION.IMAGE_OVERRIDE: when not null it wins over the grade rule that picks the portrait
--  (star-7 = per-job fixed portrait, growth = shared fixed portrait, others = IMAGE_URL). NULL = unchanged behavior.
--  Additive and nullable, so the old code keeps working if this is applied before the deploy.
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME = 'IMAGE_OVERRIDE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_COMPANION ADD (IMAGE_OVERRIDE VARCHAR2(500) NULL)';
    END IF;
END;
/

SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME IN ('IMAGE_URL', 'IMAGE_OVERRIDE');
EXIT;
