-- ============================================================
-- Season 5 - 81F+ stairs gate behind mid-boss kill (2026-09-15 request)
--
-- "From floor 81 up, finding the up-stairs tile should not unlock the next
-- floor until this floor's mid-boss has been defeated once, and the player
-- must reach the stairs tile again afterward."
--
-- New per-(user,floor) flag on TBOT_S5_USER_FLOOR_PROGRESS. Only touched by
-- an explicit UPDATE (markFloorMidbossKilled) when a mid-boss (COMBAT-tile
-- disguised monster, floor >= 81) is killed -- upsertUserFloorProgress's
-- normal MERGE only ever sets CUR_TILE/ENTRY_TILE so this flag survives
-- ordinary floor-to-floor movement, and gets reset for free whenever the
-- row itself is deleted (village return -> resetBlockExploration, i.e. the
-- player must re-clear the mid-boss on a fresh attempt at that block).
--
-- See BotS5ServiceImpl case "STAIRS_UP" (gate check) and the midBoss kill
-- reward block in resolveCombatTurn() (flag set).
--
-- ASCII-only file, no Korean literals, safe to re-run. Not live-impacting
-- yet -- floor 81+ is still behind CONTENT_LOCKED_FLOOR=81 in code.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_FLOOR_PROGRESS' AND COLUMN_NAME = 'MIDBOSS_KILLED_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_FLOOR_PROGRESS ADD (MIDBOSS_KILLED_YN CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_FLOOR_PROGRESS.MIDBOSS_KILLED_YN IS 'Y once this (user,floor) mid-boss has been defeated this expedition -- gates the 81F+ up-stairs tile';

COMMIT;

SELECT COLUMN_NAME, DATA_DEFAULT, NULLABLE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_FLOOR_PROGRESS' AND COLUMN_NAME = 'MIDBOSS_KILLED_YN';

EXIT;
