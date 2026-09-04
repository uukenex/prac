-- ============================================================
-- Season 5 - live DB migration: track lifetime total PP earned per user.
-- TBOT_S5_USER_PROGRESS.TOTAL_PP_EARNED_VALUE/EXT (new columns, PP.java value+unit
-- pair, same pattern as PP_VALUE/PP_EXT) -- incremented alongside every PP grant
-- inside BotS5ServiceImpl.addPp() (the single choke point for all PP income:
-- combat kills, auto-hunt settlement, treasure rooms, lucky tiles, rogue steal,
-- gacha duplicate refunds). Unlike PP_VALUE this never decreases when PP is
-- spent, so it tracks the player's all-time economy total.
-- Requested for /탑현황 to show "누적 획득 PP" alongside current holdings.
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TOTAL_PP_EARNED_VALUE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (TOTAL_PP_EARNED_VALUE NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TOTAL_PP_EARNED_EXT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (TOTAL_PP_EARNED_EXT VARCHAR2(1) DEFAULT '''' )';
    END IF;
END;
/

-- Backfill: existing users already earned at least their current PP_VALUE lifetime
-- (can't have less lifetime-earned than what they currently hold) -- without this
-- their "누적 획득 PP" would show 0 despite having a real balance. Only touches rows
-- still at the just-added default of 0, so safe to re-run.
UPDATE TBOT_S5_USER_PROGRESS
SET TOTAL_PP_EARNED_VALUE = PP_VALUE, TOTAL_PP_EARNED_EXT = PP_EXT
WHERE TOTAL_PP_EARNED_VALUE = 0;

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME IN ('TOTAL_PP_EARNED_VALUE', 'TOTAL_PP_EARNED_EXT');
EXIT;
