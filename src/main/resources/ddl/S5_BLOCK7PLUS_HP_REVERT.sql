-- ============================================================
-- Season 5 - revert the 61F+ regular-monster HP bump (2026-09-16 request)
--
-- "Put the 61F+ HP back to what it was." Follows S5_BLOCK7PLUS_LONGER_FIGHTS.sql
-- (HP x1.3 / ATK x0.85 on regular monsters, BLOCK_NO 7..10). Only the HP
-- change is reverted here, back to the exact pre-change values; the ATK
-- x0.85 reduction stays as-is (not reverted -- only HP was called out).
--
-- ASCII-only file, no Korean literals, safe to re-run (sets exact absolute
-- values rather than dividing, so re-running is a no-op).
-- ============================================================

UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 26000    WHERE BOSS_YN = 'N' AND BLOCK_NO = 7;
UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 52000    WHERE BOSS_YN = 'N' AND BLOCK_NO = 8;
UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 450000   WHERE BOSS_YN = 'N' AND BLOCK_NO = 9;
UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 1200000  WHERE BOSS_YN = 'N' AND BLOCK_NO = 10;

COMMIT;

SELECT BLOCK_NO, BOSS_YN, MONSTER_NAME, HP_VALUE, ATK_VALUE, DEF_VALUE
FROM TBOT_S5_MONSTER_INFO
WHERE BLOCK_NO >= 7
ORDER BY BLOCK_NO, BOSS_YN;

EXIT;
