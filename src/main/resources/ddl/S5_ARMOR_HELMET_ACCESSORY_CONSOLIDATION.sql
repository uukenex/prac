-- ============================================================
-- Season 5 - armor/helmet/accessory equipment CLASS consolidation
-- (2026-09-17 request, follow-up to S5_WEAPON_CLASS_CONSOLIDATION.sql):
-- "갑옷,투구는 이렇게 바꿔줘. 전사,도적 -> 갑주, 투구 / 마법사,도사 -> 로브,
--  머리띠 / 궁수 -> 재킷, 깃장식. 악세사리3종은 전부 공용으로 바꿔줘."
--
-- Same 3-group pattern already applied to weapons (SWORD/STAFF/BOW), now
-- extended to ARMOR and HELMET:
--   ARMOR:  WARRIOR/ROGUE -> PLATE     MAGE/PRIEST -> ROBE      ARCHER -> JACKET
--   HELMET: WARRIOR/ROGUE -> HELM      MAGE/PRIEST -> HEADBAND  ARCHER -> PLUME
--
-- Accessories (NECKLACE/RING/BRACELET) go further -- fully universal, all 5
-- jobs collapse into a single CLASS value usable by anyone:
--   NECKLACE/RING/BRACELET: (any of the 5 job values) -> COMMON
--
-- Same rationale/precedent as the weapon migration: CLASS is a plain
-- VARCHAR2(10) with no CHECK/FK, so this is a pure data UPDATE, no ALTER
-- TABLE. All values are ASCII, no HEXTORAW needed. Idempotent (each
-- UPDATE's WHERE clause only matches rows still on an old value).
--
-- *** DEPLOY ORDER -- READ BEFORE RUNNING ***
-- Same as the weapon migration: the application code already accepts BOTH
-- old job-named CLASS values and these new group/COMMON values at the same
-- time. Do NOT run this against a server still running OLD code (pre this
-- consolidation) -- safe order is deploy new code -> confirm it is live ->
-- run this script.
-- ============================================================

-- ARMOR
UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'PLATE'
 WHERE PART = 'ARMOR'
   AND CLASS IN ('WARRIOR', 'ROGUE');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'ROBE'
 WHERE PART = 'ARMOR'
   AND CLASS IN ('MAGE', 'PRIEST');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'JACKET'
 WHERE PART = 'ARMOR'
   AND CLASS = 'ARCHER';

-- HELMET
UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'HELM'
 WHERE PART = 'HELMET'
   AND CLASS IN ('WARRIOR', 'ROGUE');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'HEADBAND'
 WHERE PART = 'HELMET'
   AND CLASS IN ('MAGE', 'PRIEST');

UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'PLUME'
 WHERE PART = 'HELMET'
   AND CLASS = 'ARCHER';

-- ACCESSORIES -- fully universal, all 5 job values collapse into COMMON
UPDATE TBOT_S5_USER_EQUIP
   SET CLASS = 'COMMON'
 WHERE PART IN ('NECKLACE', 'RING', 'BRACELET')
   AND CLASS IN ('WARRIOR', 'MAGE', 'ROGUE', 'ARCHER', 'PRIEST');

COMMIT;

-- Verification: every ARMOR/HELMET row should now be one of the 3 new
-- values per part, and every accessory row should be COMMON. Zero rows
-- for any other CLASS value in these groups means the migration is complete.
SELECT PART, CLASS, COUNT(*) AS CNT
  FROM TBOT_S5_USER_EQUIP
 WHERE PART IN ('ARMOR', 'HELMET', 'NECKLACE', 'RING', 'BRACELET')
 GROUP BY PART, CLASS
 ORDER BY PART, CLASS;
