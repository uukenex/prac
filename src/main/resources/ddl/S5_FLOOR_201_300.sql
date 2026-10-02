-- ============================================================
-- Season 5 - floor info for 201~300 (2026-10-01). Stair zone floors have 9 fixed tiles (same as 101~200).
-- 300 is a village row like 200 (a row exists for villages too). The floors stay LOCKED until
-- TBOT_S5_CONFIG STAIR_ZONE_MAX_FLOOR is raised (default 200, /refresh applies it). Idempotent.
-- To open 201~300:  INSERT INTO TBOT_S5_CONFIG (CONFIG_KEY, CONFIG_VALUE, MEMO, UPDATE_DATE) VALUES ('STAIR_ZONE_MAX_FLOOR', '300', 'stair zone open max', SYSDATE); COMMIT;  then run the in-game refresh command (/갱신).
-- ============================================================
INSERT INTO TBOT_S5_FLOOR_INFO (FLOOR, TILE_COUNT)
SELECT n, 9 FROM (SELECT LEVEL + 200 AS n FROM DUAL CONNECT BY LEVEL <= 100) x
 WHERE NOT EXISTS (SELECT 1 FROM TBOT_S5_FLOOR_INFO f WHERE f.FLOOR = x.n);
COMMIT;
