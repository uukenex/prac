-- ============================================================
-- Season 5 - weapon equipment CLASS consolidation (2026-09-17 request):
-- "장비 분류 통합, 무기류 먼저 -- 검(전사/도적)/지팡이(도사/마법사)/활(궁수)
--  3종으로" -- collapses TBOT_S5_USER_EQUIP.CLASS for PART='WEAPON' rows
-- from the 5 job values down to 3 shared weapon-type values, so a warrior
-- and a rogue can now wear/synthesize the same sword pool instead of two
-- separate ones.
--
--   WARRIOR -> SWORD   ROGUE  -> SWORD
--   MAGE    -> STAFF   PRIEST -> STAFF
--   ARCHER  -> BOW
--
-- Only PART='WEAPON' rows are touched. HELMET/ARMOR/NECKLACE/RING/BRACELET
-- keep the existing 1 job = 1 CLASS scheme untouched (out of scope for this
-- pass -- "무기류 먼저" / weapons first).
--
-- CLASS is a plain VARCHAR2(10) with no CHECK/FK constraint (same column
-- PART already reused for the NECKLACE/RING/BRACELET accessory rollout in
-- S5_ACCESSORY_GACHA.sql without any ALTER TABLE), so this is a pure data
-- UPDATE -- no schema change. All values are ASCII, no HEXTORAW needed.
--
-- IDEMPOTENT: re-running is a no-op the second time, because each UPDATE's
-- WHERE clause only matches rows still on the OLD job values -- once a row
-- is SWORD/STAFF/BOW it no longer matches CLASS IN ('WARRIOR','ROGUE') etc.
--
-- *** DEPLOY ORDER -- READ BEFORE RUNNING ***
-- The application code (BotS5ServiceImpl.weaponAllowedJobs/equipClassLabel,
-- Season5ViewController/tower_view.jsp's matching JS) is written to accept
-- BOTH the old job-named CLASS values and these new SWORD/STAFF/BOW values
-- at the same time, specifically so this script is safe to run at any time
-- AFTER that code is live. Do NOT run this script against a server that is
-- still running the OLD code (pre weapon-consolidation) -- old code does a
-- strict CLASS==companion.CLASS equality check, so any row already rewritten
-- to SWORD/STAFF/BOW would become unequippable by old code until the new
-- code is deployed. Safe order: deploy new code -> confirm it is live ->
-- run this script.
-- ============================================================

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'SWORD'
 WHERE PART = 'WEAPON'
   AND CLASS IN ('WARRIOR', 'ROGUE');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'STAFF'
 WHERE PART = 'WEAPON'
   AND CLASS IN ('MAGE', 'PRIEST');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'BOW'
 WHERE PART = 'WEAPON'
   AND CLASS = 'ARCHER';

COMMIT;

-- Verification: every PART='WEAPON' row should now be one of SWORD/STAFF/BOW,
-- and this GROUP BY should show zero rows for any other CLASS value.
SELECT CLASS, COUNT(*) AS CNT
  FROM TBOT_S5_USER_EQUIP
 WHERE PART = 'WEAPON'
 GROUP BY CLASS
 ORDER BY CLASS;
