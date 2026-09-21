-- ============================================================
-- Season 5 - V2 balance follow-up: lower the target rounds-to-kill (2026-09-21).
--
-- After reviewing the V1-vs-V2 before/after comparison, user feedback:
-- "몬스터체력과 유저공격력 모두 다운스케일링이 필요해보여" (both monster HP and user
-- attack feel too high), clarified via follow-up as wanting REAL difficulty reduction
-- (fights should resolve faster), not just smaller numbers at the same pace.
--
-- The original V2 design derives monster HP directly from a target rounds-to-kill K
-- (HP ~= K * party damage-per-round), with K=4.5 (normal) / K=11 (boss). Monster ATK/DEF
-- are sized independently as a % of party stats (35% DEF / 15-20% ATK) and are NOT a
-- function of K, so they stay untouched -- only HP needs to change to hit a new K.
-- Because HP scales linearly with K, this is a straight multiply of the already-computed
-- HP_VALUE column by (new K / old K) -- no need to re-derive the linear formula from
-- scratch. Player-side GRADE_BASE_V2/EQUIP_BONUS_V2 are intentionally left untouched:
-- lowering player ATK would work AGAINST "fights end faster", so monster HP is the
-- correct (and sufficient) lever here.
--
-- New targets: K=2.5 (normal, was 4.5) / K=6 (boss, was 11) -- keeps the boss/normal
-- toughness ratio essentially the same (2.5/4.5=0.556 vs 6/11=0.545).
--
-- The 1~10층 low-floor dampening applied in BotS5ServiceImpl.applyHardcoreFloorScale()
-- multiplies whatever HP_VALUE is stored here, so it automatically carries through
-- (no Java change needed for this step).
-- ASCII-only file, safe to re-run is NOT safe here (would compound the multiply again) --
-- do not re-execute after first successful run.
-- ============================================================

UPDATE TBOT_S5_MONSTER_INFO_V2
   SET HP_VALUE = ROUND(HP_VALUE * 2.5 / 4.5)
 WHERE BOSS_YN = 'N';

UPDATE TBOT_S5_MONSTER_INFO_V2
   SET HP_VALUE = ROUND(HP_VALUE * 6.0 / 11.0)
 WHERE BOSS_YN = 'Y';

COMMIT;

-- Verification
SELECT FLOOR, BOSS_YN, HP_VALUE, ATK_VALUE, DEF_VALUE FROM TBOT_S5_MONSTER_INFO_V2
 WHERE FLOOR IN (1,5,8,9,11,19,29,39,49,51,59,69,79,89,99) ORDER BY FLOOR;
