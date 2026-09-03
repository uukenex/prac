-- ============================================================
-- Season 5 - live DB migration: TBOT_S5_USER_PROGRESS.LUCKY_TURN_LEFT /
-- LUCKY_EFFECT. Backs the new "럭키칸" (TILE_TYPE stays 'PP' for backward
-- compatibility, display renamed to "🍀 럭키") multi-effect tile: PP
-- bonus / heal are instant, ATK_UP / DEF_UP are a party-wide +30% buff for
-- 3 board moves (mirrors TRAP_TURN_LEFT / TRAP_EFFECT).
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LUCKY_TURN_LEFT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (LUCKY_TURN_LEFT NUMBER DEFAULT 0 NOT NULL, LUCKY_EFFECT VARCHAR2(10))';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME IN ('LUCKY_TURN_LEFT','LUCKY_EFFECT');
EXIT;
