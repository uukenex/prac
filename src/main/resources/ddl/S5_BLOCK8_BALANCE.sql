-- ============================================================
-- Season 5 - block8 (71-80) monster stat rebalance (2026-09-13 request)
--
-- "Fix floor 71+ monster attack power etc. to be balanced."
--
-- Root cause (same pattern as the earlier block7 fix,
-- S5_BLOCK7_DUAL_ENRAGE.sql): block8 monsters (108 normal, 208 boss
-- for the 79-floor boss) were left at old/weak values -- HP/ATK/DEF
-- were actually LOWER than block7's already-fixed numbers, an
-- inversion (block7 normal ATK 376 > block8 normal ATK 111; block7
-- boss ATK 400 > block8 boss ATK 243). PP_PER_KILL_VALUE was already
-- correct (225 -> 450, consistent with the established per-block x2
-- progression) and is left untouched.
--
-- Fix: set block8 HP/ATK/DEF to exactly 2x block7's current (fixed)
-- values, continuing the same x2-per-block convention.
--   107 (block7 normal): HP 26000 / ATK 376 / DEF 56  -> 108 x2
--   207 (block7 boss):   HP 60000 / ATK 400 / DEF 40  -> 208 x2
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

UPDATE TBOT_S5_MONSTER_INFO
   SET HP_VALUE = 52000, ATK_VALUE = 752, DEF_VALUE = 112
 WHERE MONSTER_ID = 108;

UPDATE TBOT_S5_MONSTER_INFO
   SET HP_VALUE = 120000, ATK_VALUE = 800, DEF_VALUE = 80
 WHERE MONSTER_ID = 208;

COMMIT;

SELECT MONSTER_ID, HP_VALUE, ATK_VALUE, DEF_VALUE, PP_PER_KILL_VALUE
FROM TBOT_S5_MONSTER_INFO WHERE MONSTER_ID IN (108, 208) ORDER BY MONSTER_ID;
EXIT;
