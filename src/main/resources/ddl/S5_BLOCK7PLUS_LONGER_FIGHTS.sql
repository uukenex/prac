-- ============================================================
-- Season 5 - 61F+ regular monsters: more HP, a bit less ATK (2026-09-16 request)
--
-- "From floor 61 onward, raise monster HP and lower ATK a bit, so fights
--  last a bit longer."
--
-- Scope: regular monsters only (BOSS_YN='N'), BLOCK_NO 7..10 (61F+ hunting
-- floors -- 9/10 are not live yet, CONTENT_LOCKED_FLOOR=81 in code, but
-- updated now so the same tuning is already in place whenever that content
-- opens). Boss rows (BOSS_YN='Y') are intentionally untouched -- those have
-- already been through separate, carefully verified balance passes this
-- session (Monte Carlo simulated win rates etc.) that this casual "make
-- fights last a bit longer" request should not disturb.
--
-- HP x1.3 (+30%), ATK x0.85 (-15%), DEF unchanged.
--
-- ASCII-only file, no Korean literals, safe to re-run (idempotent multiplier
-- would compound on rerun, so this is a one-time tuning pass -- re-running
-- it again would apply the multipliers a second time; do not blindly rerun).
-- ============================================================

UPDATE TBOT_S5_MONSTER_INFO
   SET HP_VALUE  = ROUND(HP_VALUE * 1.3),
       ATK_VALUE = ROUND(ATK_VALUE * 0.85)
 WHERE BOSS_YN = 'N'
   AND BLOCK_NO >= 7;

COMMIT;

SELECT BLOCK_NO, BOSS_YN, MONSTER_NAME, HP_VALUE, ATK_VALUE, DEF_VALUE
FROM TBOT_S5_MONSTER_INFO
WHERE BLOCK_NO >= 7
ORDER BY BLOCK_NO, BOSS_YN;

EXIT;
