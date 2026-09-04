-- ============================================================
-- Season 5 - roll back anyone who already cleared the 29th-floor boss
-- while the "ignored target" gimmick was backwards (2026-09-05 request).
--
-- Background: the block3+ boss gimmick (BOSS_IMMUNE_CID) picks 1 party
-- member and was supposed to make that member's attacks not land on the
-- boss (they only get hit, never deal damage). It shipped inverted: the
-- picked member instead took 0 damage from the boss (effectively immune),
-- making these boss fights much easier than intended. That bug is fixed
-- in code now (BotS5ServiceImpl.resolveCombatTurn), but anyone who already
-- beat the 29-floor boss did so under the easier (wrong) rules.
--
-- Fix (explicit user request): for every user who has already cleared
-- that boss (UNLOCKED_BLOCK >= 30), roll CUR_FLOOR / MAX_FLOOR_REACHED
-- back to 28 and re-lock UNLOCKED_BLOCK to 20, so they must beat the
-- 29-boss again under the corrected rules. Their original values are
-- saved into PENDING_RESTORE_* columns; BotS5ServiceImpl.resolveCombatTurn
-- detects a boss-29 kill with a saved PENDING_RESTORE_FLOOR and restores
-- the user straight back to their original floor/unlock/auto-hunt state
-- instead of just advancing one block -- so no permanent progress loss,
-- just a mandatory re-fight of that one boss under fair rules.
--
-- Explicitly NOT touched (per request): equips/purchases/stat levels/
-- gacha vouchers/PP balance, and TBOT_S5_USER_FLOOR_BEST (100%-exploration
-- records / achievements) -- this script only ever writes to CUR_FLOOR,
-- MAX_FLOOR_REACHED, UNLOCKED_BLOCK, the PENDING_RESTORE_* columns, combat
-- state (STATUS/CUR_MONSTER_*/BOSS_*_CID, cleared so no stale mid-fight
-- state survives the floor jump) and TBOT_S5_AUTO_HUNT_LOG.FLOOR.
--
-- Safe to re-run: both UPDATEs are guarded by PENDING_RESTORE_FLOOR IS NULL
-- so an already-rolled-back user is left alone.
--
-- ASCII-only file, no Korean literals.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('PENDING_RESTORE_FLOOR',          'PENDING_RESTORE_FLOOR NUMBER NULL');
    add_col_if_missing('PENDING_RESTORE_MAX_FLOOR',       'PENDING_RESTORE_MAX_FLOOR NUMBER NULL');
    add_col_if_missing('PENDING_RESTORE_UNLOCKED_BLOCK',  'PENDING_RESTORE_UNLOCKED_BLOCK NUMBER NULL');
    add_col_if_missing('PENDING_RESTORE_AUTOHUNT_FLOOR',  'PENDING_RESTORE_AUTOHUNT_FLOOR NUMBER NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.PENDING_RESTORE_FLOOR IS 'saved CUR_FLOOR from before the 2026-09-05 boss29 rollback, restored on the next legit 29-boss kill';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.PENDING_RESTORE_MAX_FLOOR IS 'saved MAX_FLOOR_REACHED from before the 2026-09-05 boss29 rollback';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.PENDING_RESTORE_UNLOCKED_BLOCK IS 'saved UNLOCKED_BLOCK from before the 2026-09-05 boss29 rollback';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.PENDING_RESTORE_AUTOHUNT_FLOOR IS 'saved TBOT_S5_AUTO_HUNT_LOG.FLOOR from before the 2026-09-05 boss29 rollback, if the user had an auto-hunt log row';

COMMIT;

-- How many users are about to be rolled back (for the record).
SELECT COUNT(*) AS AFFECTED_USERS FROM TBOT_S5_USER_PROGRESS
WHERE UNLOCKED_BLOCK >= 30 AND PENDING_RESTORE_FLOOR IS NULL;

-- Save the current auto-hunt floor (if any) before we touch the main row.
UPDATE TBOT_S5_USER_PROGRESS t
SET t.PENDING_RESTORE_AUTOHUNT_FLOOR = (
        SELECT ah.FLOOR FROM TBOT_S5_AUTO_HUNT_LOG ah WHERE ah.USER_NAME = t.USER_NAME
    )
WHERE t.UNLOCKED_BLOCK >= 30
  AND t.PENDING_RESTORE_FLOOR IS NULL;

-- Save original progress and roll back to right before the 29-boss.
-- Also clears any in-flight combat state so no stale monster/gimmick
-- reference survives the floor jump.
UPDATE TBOT_S5_USER_PROGRESS t
SET t.PENDING_RESTORE_FLOOR = t.CUR_FLOOR,
    t.PENDING_RESTORE_MAX_FLOOR = t.MAX_FLOOR_REACHED,
    t.PENDING_RESTORE_UNLOCKED_BLOCK = t.UNLOCKED_BLOCK,
    t.CUR_FLOOR = 28,
    t.MAX_FLOOR_REACHED = 28,
    t.UNLOCKED_BLOCK = 20,
    t.STATUS = 'NORMAL',
    t.CUR_MONSTER_ID = NULL,
    t.CUR_MONSTER_HP_VALUE = NULL,
    t.CUR_MONSTER_HP_EXT = NULL,
    t.CUR_MONSTER_ELITE_YN = 'N',
    t.BOSS_IMMUNE_CID = NULL,
    t.BOSS_STUN_CID = NULL,
    t.KILL_COUNT_CUR = 0
WHERE t.UNLOCKED_BLOCK >= 30
  AND t.PENDING_RESTORE_FLOOR IS NULL;

-- Point any active auto-hunt at 28 too, for users just rolled back.
UPDATE TBOT_S5_AUTO_HUNT_LOG ah
SET ah.FLOOR = 28
WHERE ah.USER_NAME IN (
    SELECT USER_NAME FROM TBOT_S5_USER_PROGRESS WHERE PENDING_RESTORE_AUTOHUNT_FLOOR IS NOT NULL
);

COMMIT;

-- Verify: list who just got rolled back and what will be restored later.
SELECT USER_NAME, CUR_FLOOR, MAX_FLOOR_REACHED, UNLOCKED_BLOCK,
       PENDING_RESTORE_FLOOR, PENDING_RESTORE_MAX_FLOOR, PENDING_RESTORE_UNLOCKED_BLOCK,
       PENDING_RESTORE_AUTOHUNT_FLOOR
FROM TBOT_S5_USER_PROGRESS
WHERE PENDING_RESTORE_FLOOR IS NOT NULL
ORDER BY USER_NAME;
EXIT;
