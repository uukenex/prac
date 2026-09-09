-- ============================================================
-- Season 5 - single-select "min dice adjust" (2026-09-09 request)
--
-- Before this change, DICE_MIN_BONUS (+ tiers) and DICE_MIN_MALUS (- tier) were both
-- applied simultaneously and additively (diceMinFor = 1 + bonus - malus). The web UI
-- highlighted BOTH tracks' "current" tier at once, which looked like a multi-select bug
-- (a user with bonus=2 AND malus=1 saw both "-1" and "+2" lit up at the same time), and
-- the "+0" baseline pip had no click handler at all (could never be selected for free).
--
-- Fix: DICE_MIN_BONUS/DICE_MIN_MALUS now track purchase progress only (how many tiers
-- unlocked in each direction). A new column DICE_MIN_ADJUST is the single value (-1..+6,
-- default 0) actually applied to rolls (see BotS5ServiceImpl.diceMinFor()). Players can
-- freely switch between any already-purchased tier (including the always-free 0) via the
-- new selectDiceMinAdjust()/DICE_MIN_SELECT web action -- exactly one tier highlighted at
-- a time, matching the "max dice" row's single-select behavior.
--
-- This script also backfills DICE_MIN_ADJUST for existing rows so nobody keeps looking
-- like they have two tiers active at once: whichever direction has *some* purchase wins,
-- preferring the (generally pricier) bonus track when a user bought into both --
-- CASE WHEN DICE_MIN_BONUS > 0 THEN DICE_MIN_BONUS WHEN DICE_MIN_MALUS > 0 THEN
-- -DICE_MIN_MALUS ELSE 0 END. This is a policy choice (not a "correct" reconstruction of
-- old additive behavior, which a single value can't always reproduce) -- flagged to the
-- user as such.
--
-- ASCII-only file, no Korean literals, safe to re-run (backfill only touches rows still
-- at the column default of 0, so re-running after a user has since chosen a value won't
-- clobber their choice).
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('DICE_MIN_ADJUST', 'DICE_MIN_ADJUST NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DICE_MIN_ADJUST IS 'single value (-1..+6) actually applied to dice rolls (diceMinFor()); DICE_MIN_BONUS/DICE_MIN_MALUS are purchase progress only, this is which one is switched on';

-- backfill: only rows still at the just-added default (0) with some purchase progress.
UPDATE TBOT_S5_USER_PROGRESS
SET DICE_MIN_ADJUST = CASE WHEN DICE_MIN_BONUS > 0 THEN DICE_MIN_BONUS
                            WHEN DICE_MIN_MALUS > 0 THEN -DICE_MIN_MALUS
                            ELSE 0 END
WHERE DICE_MIN_ADJUST = 0
  AND (DICE_MIN_BONUS > 0 OR DICE_MIN_MALUS > 0);

COMMIT;

SELECT COUNT(*) AS BACKFILLED_NONZERO FROM TBOT_S5_USER_PROGRESS WHERE DICE_MIN_ADJUST != 0;
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME = 'DICE_MIN_ADJUST';
EXIT;
