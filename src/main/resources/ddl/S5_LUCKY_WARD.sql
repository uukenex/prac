-- ============================================================
-- Season 5 - lucky-tile death ward (2026-09-15 request)
--
-- New DEATH_WARD lucky-tile effect: grants one random party member a
-- one-time "next lethal hit is fully negated" ward (HP reverts to its
-- pre-hit value, no matter the HP total). Tracked separately from the
-- existing LUCKY_TURN_LEFT/LUCKY_EFFECT columns (which stay turn-counted
-- whole-party buffs) because this is a single-target, consume-on-hit flag
-- with no turn countdown.
--
-- See BotS5ServiceImpl.rollDiceInternal() (case "PP": DEATH_WARD branch)
-- and resolveCombatTurn() (WARD_COMPANION_ID consumption in the counter-
-- attack damage loop).
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'WARD_COMPANION_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (WARD_COMPANION_ID NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WARD_COMPANION_ID IS 'companion_id of the party member currently holding a one-time DEATH_WARD lucky-tile effect, 0 = none';

COMMIT;

SELECT COLUMN_NAME, DATA_DEFAULT, NULLABLE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'WARD_COMPANION_ID';

EXIT;
