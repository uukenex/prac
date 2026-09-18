-- ============================================================
-- Season 5 - block 9/10 (floors 81-90, 91-98) pre-release tuning
-- (2026-09-18 request): "81층부터는 100칸으로 변경, 계단은 위/아래 각 2개로
-- (code-side, see BotS5ServiceImpl.ensureUserBoard), 몬스터방어력을
-- 3~4배 정도 올려줘".
--
-- Floors 81+ are still CONTENT_LOCKED_FLOOR (=81 in BotS5ServiceImpl) --
-- no real player has ever been able to reach these floors, so this is
-- pure pre-release balance prep with zero live-player impact (unlike the
-- earlier 61+/71+ open-day balance passes, which had to be careful about
-- already-active players).
--
-- 1) TILE_COUNT -> 100 for every hunting-floor position (1..8 within a
--    block) in blocks 9 and 10 (was ~190-210, matching block 7/8's size).
--    "81층부터" (from floor 81 onward) read literally -- applies to block
--    10 too, not just block 9, so there is no inconsistency waiting when
--    block 10 opens later.
-- 2) Regular-monster (BOSS_YN='N') DEF_VALUE -> x3.5 for blocks 9 and 10.
--    Picked the middle of the requested "3~4배" range. Bosses intentionally
--    left untouched (established precedent in this codebase -- boss stats
--    are separately/carefully tuned, e.g. S5_BLOCK7PLUS_LONGER_FIGHTS.sql's
--    "보스는 이미 별도로 신중하게 검증된 밸런스라 건드리지 않음").
--
-- Idempotency note: re-running the DEF update would multiply by 3.5 AGAIN
-- (there's no "already applied" marker to check against, unlike the CLASS-
-- value migrations elsewhere) -- do not run this script twice.
-- ============================================================

UPDATE TBOT_S5_FLOOR_INFO
   SET TILE_COUNT = 100
 WHERE FLOOR BETWEEN 81 AND 98
   AND MOD(FLOOR, 10) BETWEEN 1 AND 8;

UPDATE TBOT_S5_MONSTER_INFO
   SET DEF_VALUE = ROUND(DEF_VALUE * 3.5)
 WHERE BLOCK_NO IN (9, 10)
   AND BOSS_YN = 'N';

COMMIT;

-- Verification
SELECT FLOOR, TILE_COUNT FROM TBOT_S5_FLOOR_INFO WHERE FLOOR BETWEEN 81 AND 98 ORDER BY FLOOR;
SELECT BLOCK_NO, MONSTER_NAME, BOSS_YN, HP_VALUE, ATK_VALUE, DEF_VALUE
  FROM TBOT_S5_MONSTER_INFO WHERE BLOCK_NO IN (9, 10) ORDER BY BLOCK_NO, BOSS_YN;
