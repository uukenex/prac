-- ============================================================
-- Season 5 - Retroactive grant: 50F+ 50%-exploration accessory voucher
-- (2026-09-16 request)
--
-- New live reward (BotS5ServiceImpl.checkExploreHalfReward, wired into
-- rollDiceInternal): the first time a player's exploration on any floor
-- 50-99 reaches >=50% (visited*100/tileCount, integer division, same as
-- the up-stairs unlock gate added in the same change), they get one
-- accessory gacha voucher (ACCESSORY_VOUCHER_T{tier} on
-- TBOT_S5_USER_PROGRESS). Tier by which 10-floor block the floor belongs
-- to (BotS5ServiceImpl.accessoryVoucherTierForFloor):
--   50s = 1 (low/ha-geup), 60s = 2 (mid/jung-geup), 70s = 3 (high/sang-geup),
--   80s/90s = 4 (highest/choe-sang-geup, capped -- only 4 tiers exist).
-- Idempotency key mirrors the live app: TBOT_S5_USER_ACH.ACH_ID = 500+floor
-- (that range was unused before this change).
--
-- This script is the one-time backfill for existing users whose historical
-- exploration already clears 50% for a given floor, using whichever is
-- higher of:
--   - TBOT_S5_USER_FLOOR_BEST.BEST_VISITED_COUNT (snapshot taken whenever
--     the player left/reset that floor), or
--   - a live COUNT(*) from TBOT_S5_USER_TILE_VISIT (covers an expedition
--     currently in progress that hasn't been snapshotted yet).
--
-- ASCII-only, ok to re-run: the NOT EXISTS guard against TBOT_S5_USER_ACH
-- excludes anything already granted (by this script or by the live app),
-- so a second run grants nothing new.
-- ============================================================

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE S5_EXPLORE50_RETRO_TMP';
EXCEPTION
    WHEN OTHERS THEN NULL; -- table did not exist yet, ignore
END;
/

CREATE TABLE S5_EXPLORE50_RETRO_TMP AS
SELECT fp.USER_NAME, fp.FLOOR,
       LEAST(GREATEST(TRUNC(fp.FLOOR / 10) + 1 - 5, 1), 4) AS TIER
FROM (
    SELECT USER_NAME, FLOOR FROM TBOT_S5_USER_FLOOR_BEST WHERE FLOOR BETWEEN 50 AND 99
    UNION
    SELECT USER_NAME, FLOOR FROM TBOT_S5_USER_TILE_VISIT WHERE FLOOR BETWEEN 50 AND 99
) fp
JOIN TBOT_S5_FLOOR_INFO fi ON fi.FLOOR = fp.FLOOR
WHERE fi.TILE_COUNT > 0
  AND TRUNC(
        GREATEST(
            NVL((SELECT b.BEST_VISITED_COUNT FROM TBOT_S5_USER_FLOOR_BEST b
                  WHERE b.USER_NAME = fp.USER_NAME AND b.FLOOR = fp.FLOOR), 0),
            NVL((SELECT COUNT(*) FROM TBOT_S5_USER_TILE_VISIT v
                  WHERE v.USER_NAME = fp.USER_NAME AND v.FLOOR = fp.FLOOR), 0)
        ) * 100 / fi.TILE_COUNT
      ) >= 50
  AND NOT EXISTS (
        SELECT 1 FROM TBOT_S5_USER_ACH a
        WHERE a.USER_NAME = fp.USER_NAME AND a.ACH_ID = 500 + fp.FLOOR
      );

-- Preview before committing anything.
SELECT COUNT(*) AS TOTAL_GRANTS FROM S5_EXPLORE50_RETRO_TMP;
SELECT TIER, COUNT(*) AS FLOOR_GRANTS, COUNT(DISTINCT USER_NAME) AS USERS
FROM S5_EXPLORE50_RETRO_TMP GROUP BY TIER ORDER BY TIER;

-- Grant the vouchers (one per qualifying floor, grouped by user/tier).
MERGE INTO TBOT_S5_USER_PROGRESS p
USING (SELECT USER_NAME, COUNT(*) CNT FROM S5_EXPLORE50_RETRO_TMP WHERE TIER = 1 GROUP BY USER_NAME) g
ON (p.USER_NAME = g.USER_NAME)
WHEN MATCHED THEN UPDATE SET ACCESSORY_VOUCHER_T1 = NVL(ACCESSORY_VOUCHER_T1, 0) + g.CNT;

MERGE INTO TBOT_S5_USER_PROGRESS p
USING (SELECT USER_NAME, COUNT(*) CNT FROM S5_EXPLORE50_RETRO_TMP WHERE TIER = 2 GROUP BY USER_NAME) g
ON (p.USER_NAME = g.USER_NAME)
WHEN MATCHED THEN UPDATE SET ACCESSORY_VOUCHER_T2 = NVL(ACCESSORY_VOUCHER_T2, 0) + g.CNT;

MERGE INTO TBOT_S5_USER_PROGRESS p
USING (SELECT USER_NAME, COUNT(*) CNT FROM S5_EXPLORE50_RETRO_TMP WHERE TIER = 3 GROUP BY USER_NAME) g
ON (p.USER_NAME = g.USER_NAME)
WHEN MATCHED THEN UPDATE SET ACCESSORY_VOUCHER_T3 = NVL(ACCESSORY_VOUCHER_T3, 0) + g.CNT;

MERGE INTO TBOT_S5_USER_PROGRESS p
USING (SELECT USER_NAME, COUNT(*) CNT FROM S5_EXPLORE50_RETRO_TMP WHERE TIER = 4 GROUP BY USER_NAME) g
ON (p.USER_NAME = g.USER_NAME)
WHEN MATCHED THEN UPDATE SET ACCESSORY_VOUCHER_T4 = NVL(ACCESSORY_VOUCHER_T4, 0) + g.CNT;

-- Record the idempotency markers so the live app (checkExploreHalfReward)
-- never re-grants these on the user's next dice roll on that floor.
INSERT INTO TBOT_S5_USER_ACH (USER_NAME, ACH_ID)
SELECT USER_NAME, 500 + FLOOR FROM S5_EXPLORE50_RETRO_TMP;

DROP TABLE S5_EXPLORE50_RETRO_TMP;

COMMIT;

-- Sanity check after commit.
SELECT COUNT(*) AS ACH_ROWS_GRANTED FROM TBOT_S5_USER_ACH WHERE ACH_ID BETWEEN 550 AND 599;

EXIT;
