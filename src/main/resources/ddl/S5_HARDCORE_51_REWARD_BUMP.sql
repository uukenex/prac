-- ============================================================
-- 51F+ (block6-10) reward bump to match the hardcore difficulty
-- increase (2026-09-07 request).
--
-- Context: blocks 6-10 (floor 51+) already got harder via
-- S5_HARDCORE_51.sql -- board size ~190-210 tiles (was ~100-150,
-- roughly +40-60% more tiles to fully explore), a mid-boss ambush
-- (20% chance, 3x stats) on normal COMBAT tiles, and two new
-- punishing trap types (RESET_TILE sends you back, SKILL_LOCK
-- disables job skills for the next fight). The base per-kill PP
-- reward for these blocks was never adjusted to compensate.
--
-- Fix: +50% PP_PER_KILL_VALUE for the NORMAL monster row of each
-- affected block (106-110). This is a single source of truth that
-- every PP-paying path already reads from (normal kill, ELITE tile
-- 2x, mid-boss 3x, TREASURE tile 5x, PP_BONUS lucky tile 3x, ROGUE
-- steal %, etc. via BotS5ServiceImpl) -- no code change needed,
-- everything scales from this one column.
-- Boss rows (BOSS_YN='Y') keep PP_PER_KILL_VALUE=0 by design
-- (bosses reward via block-unlock/vouchers, not direct PP) --
-- untouched here.
--
-- Before -> after:
--   106 (block6, 51-60F): 70   -> 105
--   107 (block7, 61-70F): 150  -> 225
--   108 (block8, 71-80F): 300  -> 450
--   109 (block9, 81-90F): 600  -> 900
--   110 (block10,91-98F): 1200 -> 1800
--
-- ASCII-only file, no Korean literals, safe to re-run (idempotent
-- guard: only applies if still at the old value, so re-running
-- this script after it already succeeded is a no-op).
-- ============================================================

UPDATE TBOT_S5_MONSTER_INFO SET PP_PER_KILL_VALUE = 105  WHERE MONSTER_ID = 106 AND PP_PER_KILL_VALUE = 70;
UPDATE TBOT_S5_MONSTER_INFO SET PP_PER_KILL_VALUE = 225  WHERE MONSTER_ID = 107 AND PP_PER_KILL_VALUE = 150;
UPDATE TBOT_S5_MONSTER_INFO SET PP_PER_KILL_VALUE = 450  WHERE MONSTER_ID = 108 AND PP_PER_KILL_VALUE = 300;
UPDATE TBOT_S5_MONSTER_INFO SET PP_PER_KILL_VALUE = 900  WHERE MONSTER_ID = 109 AND PP_PER_KILL_VALUE = 600;
UPDATE TBOT_S5_MONSTER_INFO SET PP_PER_KILL_VALUE = 1800 WHERE MONSTER_ID = 110 AND PP_PER_KILL_VALUE = 1200;

COMMIT;

SELECT MONSTER_ID, BLOCK_NO, MONSTER_NAME, PP_PER_KILL_VALUE, BOSS_YN
FROM TBOT_S5_MONSTER_INFO
WHERE BLOCK_NO >= 6
ORDER BY BLOCK_NO, BOSS_YN;
EXIT;
