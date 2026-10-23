-- ============================================================
-- Season 5 - web mine pickaxe: initial durability 1000 -> 200 (2026-10-23, "start durability 200"). Run after S5_MINE_PICKAXE.sql.
--  New users get 200/200 (column defaults). Existing users still at the untouched 1000/1000 (nobody could use durability yet -- the app
--  code was not deployed when this ran; all 8 users were 1000/1000) are moved to 200/200. Rows already changed by play are not touched.
-- ASCII-only file, safe to re-run.
-- ============================================================
ALTER TABLE TBOT_S5_MINE_USER MODIFY (PICK_DUR DEFAULT 200, PICK_DUR_MAX DEFAULT 200);
UPDATE TBOT_S5_MINE_USER SET PICK_DUR = 200, PICK_DUR_MAX = 200 WHERE PICK_DUR = 1000 AND PICK_DUR_MAX = 1000;
COMMIT;
SELECT COUNT(*) AS USERS, MIN(PICK_DUR) AS MIN_DUR, MAX(PICK_DUR) AS MAX_DUR, MIN(PICK_DUR_MAX) AS MIN_MAX, MAX(PICK_DUR_MAX) AS MAX_MAX FROM TBOT_S5_MINE_USER;
SELECT COLUMN_NAME, DATA_DEFAULT FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_MINE_USER' AND COLUMN_NAME IN ('PICK_DUR', 'PICK_DUR_MAX');
EXIT;
