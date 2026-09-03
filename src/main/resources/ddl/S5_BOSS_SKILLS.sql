-- ============================================================
-- Season 5 - live DB migration: TBOT_S5_USER_PROGRESS.BOSS_IMMUNE_CID /
-- BOSS_STUN_CID. Backs the new late-game boss skills (blockNo>=3, i.e.
-- floor 29+ bosses):
--   - BOSS_IMMUNE_CID: one party COMPANION_ID picked at fight-start who
--     takes 0 damage from that boss for the whole encounter
--   - BOSS_STUN_CID: a COMPANION_ID the boss just stunned (skips their
--     next party-attack turn, then auto-clears)
-- Both cleared whenever CUR_MONSTER_ID is cleared (kill or flee).
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'BOSS_IMMUNE_CID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (BOSS_IMMUNE_CID NUMBER, BOSS_STUN_CID NUMBER)';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME IN ('BOSS_IMMUNE_CID','BOSS_STUN_CID');
EXIT;
