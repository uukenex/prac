-- ============================================================
-- 51F+ hardcore content scaffolding (2026-09-06 request).
-- Content is still gated by CONTENT_LOCKED_FLOOR=51 in
-- BotS5ServiceImpl (see S5_TOWER_DESIGN.md) -- no live player can
-- reach floor 51 yet, so this can be applied safely ahead of the
-- actual unlock.
--
-- Adds:
--   1) TBOT_S5_USER_PROGRESS: mid-boss combat state (CUR_MONSTER_
--      MIDBOSS_YN, MONSTER_DEF_BUFF_PCT, MONSTER_SHIELD_VALUE) --
--      mirrors the existing CUR_MONSTER_ELITE_YN pattern, used only
--      while IN_COMBAT with a floor51+ mid-boss encounter.
--   2) TBOT_S5_USER_FLOOR_PROGRESS: ENTRY_TILE, the stairs tile the
--      player first landed on this expedition -- used by the new
--      "RESET_TILE" trap (return to the first staircase tile).
--   3) TBOT_S5_FLOOR_INFO: bump TILE_COUNT to ~200 for floor 51+
--      hunting floors (was randomly 100-150 from S5_MASTER_DATA.sql)
--      so 51F+ boards are noticeably bigger than 41-49F.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_table VARCHAR2, p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('TBOT_S5_USER_PROGRESS', 'CUR_MONSTER_MIDBOSS_YN', 'CUR_MONSTER_MIDBOSS_YN CHAR(1) DEFAULT ''N'' NOT NULL');
    add_col_if_missing('TBOT_S5_USER_PROGRESS', 'MONSTER_DEF_BUFF_PCT',   'MONSTER_DEF_BUFF_PCT NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('TBOT_S5_USER_PROGRESS', 'MONSTER_SHIELD_VALUE',  'MONSTER_SHIELD_VALUE NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('TBOT_S5_USER_FLOOR_PROGRESS', 'ENTRY_TILE',      'ENTRY_TILE NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_MONSTER_MIDBOSS_YN IS 'Y = current combat is a floor51+ mid-boss (map/encounter text look identical to a normal monster)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.MONSTER_DEF_BUFF_PCT IS 'one-shot defense buff percent banked by a mid-boss stealing the WARRIOR skill, consumed on the next party attack turn';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.MONSTER_SHIELD_VALUE IS 'one-shot self shield banked by a mid-boss stealing the PRIEST skill, consumed on the next party attack turn';
COMMENT ON COLUMN TBOT_S5_USER_FLOOR_PROGRESS.ENTRY_TILE IS 'the stairs tile the player landed on when they entered this floor this expedition -- used by the RESET_TILE trap (floor51+)';

-- Floor51+ hunting floors get a much bigger board (was random 100-150,
-- see S5_MASTER_DATA.sql comment) -- random 190-210 to keep some of the
-- existing per-floor variety instead of a single flat number.
UPDATE TBOT_S5_FLOOR_INFO
   SET TILE_COUNT = TRUNC(DBMS_RANDOM.VALUE(190, 211))
 WHERE FLOOR >= 51 AND MOD(FLOOR, 10) BETWEEN 1 AND 8;

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('CUR_MONSTER_MIDBOSS_YN','MONSTER_DEF_BUFF_PCT','MONSTER_SHIELD_VALUE')
  ORDER BY COLUMN_NAME;
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_FLOOR_PROGRESS'
  AND COLUMN_NAME = 'ENTRY_TILE';
SELECT FLOOR, TILE_COUNT FROM TBOT_S5_FLOOR_INFO WHERE FLOOR >= 51 AND MOD(FLOOR,10) BETWEEN 1 AND 8 ORDER BY FLOOR;
EXIT;
