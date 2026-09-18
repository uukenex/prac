-- ============================================================
-- Season 5 - block 9/10 (floors 81-90, 91-98) monster ATK ratio fix
-- (2026-09-18 request): "81층 이후몬스터의 공격력은 78층과 동일한 비율로
-- 오르도록 변경해줘."
--
-- Block-to-block base ATK growth (regular monster, before floor-position
-- scaling) confirmed via live query:
--   block6=188, block7=320(1.70x), block8=639(2.00x),
--   block9=6120(9.58x !!), block10=12750(2.08x)
-- Boss ATK shows the identical pattern: block7 boss=400, block8 boss(79)=800
-- (2.00x), block9 boss(89)=15840 (19.8x !!), block10 boss(99)=33000 (2.08x).
-- Every block-to-block step is a smooth ~1.7-2.1x EXCEPT the 8->9 step,
-- which is a clear outlier (~10-20x). "78층과 동일한 비율로" = re-derive
-- block9/10 ATK using the same 2.00x ratio that already governs the
-- 7->8 (...78층) step, instead of the anomalous jump.
--
-- New values (2.00x chained from block8):
--   block9 regular : 639  x 2.00 = 1278   (was 6120)
--   block9 boss(89): 800  x 2.00 = 1600   (was 15840)
--   block10 regular: 1278 x 2.00 = 2556   (was 12750)
--   block10 boss(99): 1600 x 2.00 = 3200  (was 33000)
--
-- Scope: ATK_VALUE only (user explicitly said "공격력"), HP/DEF untouched
-- (DEF was already deliberately tuned x3.5 for blocks 9/10 in
-- S5_BLOCK9_10_TUNING.sql, a separate earlier request).
--
-- Floors 81+ are still CONTENT_LOCKED_FLOOR (=81 in BotS5ServiceImpl) --
-- no real player has ever reached this content, so this is pure pre-release
-- balance prep, safe to apply immediately.
-- ============================================================

-- block9 regular (용의 둥지 새끼비룡)
UPDATE TBOT_S5_MONSTER_INFO SET ATK_VALUE = 1278 WHERE MONSTER_ID = 109;
-- block9 boss (고룡 바하무트, 89층)
UPDATE TBOT_S5_MONSTER_INFO SET ATK_VALUE = 1600 WHERE MONSTER_ID = 209;
-- block10 regular (파멸의 균열 마수)
UPDATE TBOT_S5_MONSTER_INFO SET ATK_VALUE = 2556 WHERE MONSTER_ID = 110;
-- block10 boss (종말의 마룡왕 니드호그, 99층)
UPDATE TBOT_S5_MONSTER_INFO SET ATK_VALUE = 3200 WHERE MONSTER_ID = 210;

COMMIT;

-- Verification
SELECT MONSTER_ID, BLOCK_NO, MONSTER_NAME, BOSS_YN, HP_VALUE, ATK_VALUE, DEF_VALUE
  FROM TBOT_S5_MONSTER_INFO WHERE MONSTER_ID IN (109, 209, 110, 210) ORDER BY MONSTER_ID;
