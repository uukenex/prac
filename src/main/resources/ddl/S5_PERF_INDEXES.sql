-- ============================================================
-- Performance (2026-10-21): index for "which equipment is worn by this companion".
-- selectEquipByCompanion / selectSkillRateMults / many per-turn lookups filter TBOT_S5_USER_EQUIP by EQUIPPED_COMPANION_ID, which had no index
-- (only PK and USER_NAME). Rows with a NULL companion (unequipped, the vast majority for hoarders) are not stored in this B-tree, so it stays tiny.
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_S5_EQUIP_COMP';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_S5_EQUIP_COMP ON TBOT_S5_USER_EQUIP (EQUIPPED_COMPANION_ID)';
    END IF;
END;
/
DECLARE
    v_cnt2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt2 FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_S5_EQUIP_LEG';
    IF v_cnt2 = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_S5_EQUIP_LEG ON TBOT_S5_USER_EQUIP (LEGENDARY_ID)';
    END IF;
END;
/
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'TBOT_S5_USER_EQUIP', CASCADE => TRUE);
END;
/
SELECT INDEX_NAME, STATUS FROM USER_INDEXES WHERE TABLE_NAME = 'TBOT_S5_USER_EQUIP' ORDER BY 1;
EXIT;
