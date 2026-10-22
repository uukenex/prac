-- ============================================================
-- Season 5 - per-user bottom menu layout (2026-10-22). Additive only; old code ignores it.
--  TBOT_S5_USER_PROGRESS.DOCK_LAYOUT: comma separated tab keys in display order, '!' prefix = hidden
--  (e.g. 'board,party,shop,inven,ach,codex,!msg,settings'). NULL = default order, nothing hidden.
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'DOCK_LAYOUT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (DOCK_LAYOUT VARCHAR2(200) NULL)';
    END IF;
END;
/
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'DOCK_LAYOUT';
EXIT;
