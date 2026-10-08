-- ============================================================
-- Season 5 - floor info for 301~500 (2026-10-08). Same rule as 201~300: stair zone floors have 9 fixed tiles,
-- and villages (400, 500 ...) have a row too. The code is ready up to floor 500 (STAIR_ZONE_READY_MAX); the floors that can
-- actually be entered are limited by TBOT_S5_CONFIG STAIR_ZONE_MAX_FLOOR (applied by the in-game refresh command).
-- Run this BEFORE raising STAIR_ZONE_MAX_FLOOR above 300. Idempotent.
-- ============================================================
INSERT INTO TBOT_S5_FLOOR_INFO (FLOOR, TILE_COUNT)
SELECT n, 9 FROM (SELECT LEVEL + 300 AS n FROM DUAL CONNECT BY LEVEL <= 200) x
 WHERE NOT EXISTS (SELECT 1 FROM TBOT_S5_FLOOR_INFO f WHERE f.FLOOR = x.n);
COMMIT;
