-- ============================================================
-- Season 5 - Retroactive merge: duplicate companions created by the
-- redeemCompanionChoiceTicket() bug (2026-09-18 request)
--
-- Bug: unlike the normal gacha path (pullCompanionCore, 2026-09-14
-- redesign), the achievement-reward "companion choice ticket" redemption
-- never checked whether the user already owned a companion with the same
-- (CLASS, NAME) -- it always inserted a brand-new TBOT_S5_USER_COMPANION
-- row. A player who redeemed a ticket for a companion they already had got
-- a second, fully separate copy instead of a LIMIT_BREAK bump on the one
-- they had. Fixed in BotS5ServiceImpl.redeemCompanionChoiceTicket()
-- (now shares findOwnedCompanion()/limit-break logic with the gacha path).
--
-- This script is the one-time cleanup for the duplicate rows that bug
-- already created live. For each (USER_NAME, CLASS, NAME) group with more
-- than one row, one row is kept and the rest are folded into it:
--   - LIMIT_BREAK on the kept row += 1 per merged duplicate, capped at 6
--     (LIMIT_BREAK_MAX); any duplicate that would exceed the cap instead
--     refunds COMPANION_DUPE_REFUND[grade-1] PP (same table used by the
--     live gacha dupe-refund path), applied in ACQUIRE_DATE order so a
--     duplicate that arrives after the kept row is already maxed refunds
--     PP instead of another LIMIT_BREAK step.
--   - Verified before writing this script: none of the rows being deleted
--     have PARTY_SLOT set, are referenced by TBOT_S5_USER_EQUIP.
--     EQUIPPED_COMPANION_ID, or by TBOT_S5_USER_PROGRESS.WARD_COMPANION_ID
--     -- except one case (see below) where the *kept* row is the one that
--     was actively in use, not one of the deleted ones.
--
-- Six of the seven groups are the simple case (kept row already the
-- earliest by ACQUIRE_DATE, duplicates unused/unequipped/unpartied):
--   castle/... MAGE 유나:   keep 326 (LB 4->6, 2 dupes), delete 779,780
--   castle/... MAGE 카나:   keep 605 (LB 1->2, 1 dupe),  delete 781
--   .../달소   PRIEST 노조미: keep 519 (LB already 6/max), delete 766,767,
--                            refund 400+400=800 PP
--   .../달소   PRIEST 미레이: keep 474 (LB 2->4, 2 dupes), delete 763,764
--   .../달소   PRIEST 아사히: keep 247 (LB already 6/max), delete 765,
--                            refund 400 PP
--   은용       PRIEST 미레이: keep 439 (LB 0->1, 1 dupe),  delete 640
--
-- One group is the exception: 키리레이나/바드 WARRIOR 코타로. The
-- *duplicate* row (665, created by the bug on 2026-09-12) is the one the
-- player has actually been playing with -- PARTY_SLOT=3 and 6 equipped
-- items -- while the original (591, LB=2) sits unused with no gear/party.
-- To avoid disturbing the player's live party/gear, row 665 is kept
-- (LB 0->3: 1 for absorbing 591 itself, +2 for the LIMIT_BREAK 591 had
-- already accumulated from earlier legitimate gacha dupes) and 591 is
-- deleted instead of the usual "keep the earliest" rule.
--
-- PP refund for 달소 (노조미 800 + 아사히 400 = 1200 base units) computed
-- by hand against this account's live PP_VALUE/TOTAL_PP_EARNED_VALUE at
-- the time this script was written (matches my.prac.core.util.PP.add()
-- semantics exactly) and applied as a direct SET rather than a formula,
-- so it does not depend on replicating PP's unit-normalization logic in
-- SQL. Re-running this UPDATE is harmless (idempotent: same fixed target
-- values both times) as long as nothing else changed 달소's PP between
-- runs -- this script is meant to be run once.
--
-- Deletes are guarded by matching on the known IDs only (no wildcard
-- WHERE), so a second run simply deletes nothing (rows already gone) and
-- the LIMIT_BREAK updates are natural no-ops (already at target value).
-- ASCII apart from the Korean USER_NAME/NAME literals needed to identify
-- the exact rows -- these are WHERE-clause matches against existing data,
-- not new Korean data being written, so the CP949 INSERT pitfall in
-- CLAUDE.md does not apply.
-- ============================================================

-- ---- LIMIT_BREAK bumps on the surviving rows ----
UPDATE TBOT_S5_USER_COMPANION SET LIMIT_BREAK = 6 WHERE COMPANION_ID = 326; -- castle/... MAGE 유나
UPDATE TBOT_S5_USER_COMPANION SET LIMIT_BREAK = 2 WHERE COMPANION_ID = 605; -- castle/... MAGE 카나
UPDATE TBOT_S5_USER_COMPANION SET LIMIT_BREAK = 4 WHERE COMPANION_ID = 474; -- .../달소 PRIEST 미레이
UPDATE TBOT_S5_USER_COMPANION SET LIMIT_BREAK = 1 WHERE COMPANION_ID = 439; -- 은용 PRIEST 미레이
UPDATE TBOT_S5_USER_COMPANION SET LIMIT_BREAK = 3 WHERE COMPANION_ID = 665; -- 키리레이나/바드 WARRIOR 코타로 (keep the actively-used row)
-- 519 (노조미) and 247 (아사히) stay at LIMIT_BREAK=6 -- already maxed, dupes refunded as PP instead.

-- ---- Delete the duplicate rows ----
DELETE FROM TBOT_S5_USER_COMPANION WHERE COMPANION_ID IN (779, 780, 781, 766, 767, 763, 764, 765, 640, 591);

-- ---- PP refund for 도륙이냥/달소 (1200 base units: 800 for 노조미 + 400 for 아사히) ----
UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 7.223187, PP_EXT = 'a',
    TOTAL_PP_EARNED_VALUE = 364.100173, TOTAL_PP_EARNED_EXT = 'a'
WHERE USER_NAME = '도륙이냥/달소';

COMMIT;

-- Sanity check: no more duplicate (USER_NAME,CLASS,NAME) groups should remain.
SELECT USER_NAME, CLASS, NAME, COUNT(*) CNT
FROM TBOT_S5_USER_COMPANION
GROUP BY USER_NAME, CLASS, NAME
HAVING COUNT(*) > 1;

SELECT COMPANION_ID, USER_NAME, NAME, LIMIT_BREAK, PARTY_SLOT
FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID IN (326, 605, 474, 439, 665);

EXIT;
