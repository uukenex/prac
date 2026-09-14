-- ============================================================
-- Season 5 - 99-floor boss: revives once at 200% HP (2026-09-14 request)
--
-- "99th floor boss has no instant-kill, attacks 3 party members at once, and
-- when defeated revives once at 200% HP (all other special abilities removed)".
--
-- Design: when this boss's HP hits 0 for the first time in a given fight, instead
-- of ending combat normally, its HP is reset to 200% of its base max and a flag
-- column marks that the one-time revival has been used -- the second time it hits
-- 0, combat resolves normally (reward/floor progression). Whatever happens
-- (kill/wipe/flee, all go through the existing clearMonster path), this flag is
-- reset for the next fight, same lifecycle as CUR_BOSS_MINION_CIDS/CUR_MONSTER_DUAL_YN.
--
-- See BotS5ServiceImpl.resolveCombatTurn() / startCombat().
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER_REVIVED_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (CUR_MONSTER_REVIVED_YN CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_MONSTER_REVIVED_YN IS '99-floor boss one-time self-revival (200% HP) already used this fight -- reset on clearMonster (kill/wipe/flee)';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_MONSTER_REVIVED_YN';
EXIT;
