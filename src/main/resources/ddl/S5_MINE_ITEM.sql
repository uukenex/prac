-- ============================================================
-- Season 5 - web mine items (2026-10-23). Additive only; old code ignores it. ASCII-only, safe to re-run.
--  TBOT_S5_MINE_ITEM: one row per (user, item). ITEM_LEVEL = tier for leveled items, 1 for plain ones.
--    MINER_HELMET : miner helmet tier (mine shop; tier 1 = 100 iron ore, tier 2 = 100 silver ore, ...)
--                   mine level L needs tier >= L-1 to enter/move; visibility = tier - (L-1) (0 very dark, 1 better, 2+ all)
--    DRAGON_RING  : dragon king ring -- hidden (not shown or obtainable yet); maps with "REQUIRE DRAGON_RING" (sea dungeon) need it
--  Grant by hand (admin test): MERGE/INSERT a row with ITEM_CODE = 'DRAGON_RING', ITEM_LEVEL = 1.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_MINE_ITEM';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_MINE_ITEM (USER_NAME VARCHAR2(100) NOT NULL, ITEM_CODE VARCHAR2(30) NOT NULL, '
            || 'ITEM_LEVEL NUMBER DEFAULT 1 NOT NULL, UPDATE_DATE DATE DEFAULT SYSDATE NOT NULL, '
            || 'CONSTRAINT PK_S5_MINE_ITEM PRIMARY KEY (USER_NAME, ITEM_CODE))';
    END IF;
END;
/
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_MINE_ITEM';
EXIT;
