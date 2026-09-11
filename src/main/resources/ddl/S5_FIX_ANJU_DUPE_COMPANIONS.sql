-- ============================================================
-- Season 5 - literal duplicate companions (same CLASS+NAME, which per
-- NAME_POOL_BY_JOB_GRADE design always implies the same GRADE too -- this
-- should be structurally impossible, caught by the "already owned -> PP
-- refund instead of insert" dupe check in pullCompanionCore()). Reported by
-- the user for anjufarming (2026-09-11); a full GROUP BY CLASS,NAME,USER_NAME
-- HAVING COUNT(*)>1 scan over TBOT_S5_USER_COMPANION found it was NOT
-- isolated -- 7 duplicate pairs across 6 users total (old bug, presumably
-- fixed in current code already; this migration only fixes the leftover data).
--
-- For every pair, verified live which copy (if either) has PARTY_SLOT set
-- and/or TBOT_S5_USER_EQUIP rows pointing at it as EQUIPPED_COMPANION_ID --
-- kept that one (or the lower COMPANION_ID = first ever pulled, when neither
-- is in a party) and deleted the other. Confirmed none of the deleted rows
-- have equipment attached (no orphaned gear risk).
--
--   USER               CLASS/NAME(GRADE)      KEEP(party/equip)   DELETE   REFUND
--   rosterly           WARRIOR/jin(3)          303 (no)            375     400
--   soulju-chum        MAGE/kana(4)            194 (slot2,3 eq)    447     2000
--   anjufarming        ROGUE/lei(3)            5   (no)            531     400
--   anjufarming        ROGUE/yuito(4)          528 (slot3,3 eq)    613     2000
--   anjufarming        ROGUE/kagerou(4)        538 (slot1,3 eq)    541     2000
--   eunyong            WARRIOR/kotaro(3)       393 (no)            561     400
--   ileonandaramjwi/ka PRIEST/momoka(2)        74  (no)            160     80
--
-- Compensation per user policy ("중복 처리는 PP로 지급") = COMPANION_DUPE_REFUND
-- table (BotS5ServiceImpl, index = grade-1): grade2=80, grade3=400, grade4=2000.
-- Added to both PP_VALUE (spendable) and TOTAL_PP_EARNED_VALUE/EXT (lifetime
-- counter), matching what addPp() would have done at pull time. Each UPDATE's
-- WHERE guards on the pre-migration PP_VALUE so a concurrent PP change since
-- this script was written won't get silently clobbered (re-check before
-- re-running if 0 rows update).
--
-- ASCII-only file, no Korean literals (CP949 HEXTORAW for username match).
-- ============================================================

SET FEEDBACK ON

-- rosterly (CP949 hex B7CEBDBAC5CDB8AE)
DELETE FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID = 375
  AND USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B7CEBDBAC5CDB8AE'))
  AND PARTY_SLOT IS NULL;

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 1.844261, PP_EXT = 'a',
    TOTAL_PP_EARNED_VALUE = 20.815641, TOTAL_PP_EARNED_EXT = 'a'
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B7CEBDBAC5CDB8AE'))
  AND PP_VALUE = 1.804261;

-- soulju-chum (CP949 hex BCD2BFEFC1D6C3E3)
DELETE FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID = 447
  AND USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BCD2BFEFC1D6C3E3'))
  AND PARTY_SLOT IS NULL;

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 2582.815, PP_EXT = '',
    TOTAL_PP_EARNED_VALUE = 35.081309, TOTAL_PP_EARNED_EXT = 'a'
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BCD2BFEFC1D6C3E3'))
  AND PP_VALUE = 582.815;

-- anjufarming (CP949 hex BEC8C1D6C6C4B9D6) -- 3 pairs, one combined PP grant (4400)
DELETE FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID IN (531, 613, 541)
  AND USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEC8C1D6C6C4B9D6'))
  AND PARTY_SLOT IS NULL;

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 5074.395, PP_EXT = '',
    TOTAL_PP_EARNED_VALUE = 10.840897, TOTAL_PP_EARNED_EXT = 'a'
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEC8C1D6C6C4B9D6'))
  AND PP_VALUE = 674.395;

-- eunyong (CP949 hex C0BABFEB)
DELETE FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID = 561
  AND USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0BABFEB'))
  AND PARTY_SLOT IS NULL;

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 1474.18, PP_EXT = '',
    TOTAL_PP_EARNED_VALUE = 5.097648, TOTAL_PP_EARNED_EXT = 'a'
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0BABFEB'))
  AND PP_VALUE = 1074.18;

-- ileonandaramjwi/kadan (CP949 hex C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC)
DELETE FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID = 160
  AND USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC'))
  AND PARTY_SLOT IS NULL;

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 2805.4, PP_EXT = '',
    TOTAL_PP_EARNED_VALUE = 3115.1, TOTAL_PP_EARNED_EXT = ''
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC'))
  AND PP_VALUE = 2725.4;

COMMIT;

-- verify: no more duplicates anywhere
SELECT USER_NAME, CLASS, NAME, COUNT(*) AS CNT
FROM TBOT_S5_USER_COMPANION
GROUP BY USER_NAME, CLASS, NAME
HAVING COUNT(*) > 1;

-- verify PP applied
SELECT USER_NAME, PP_VALUE, PP_EXT, TOTAL_PP_EARNED_VALUE, TOTAL_PP_EARNED_EXT
FROM TBOT_S5_USER_PROGRESS
WHERE USER_NAME IN (
  UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B7CEBDBAC5CDB8AE')),
  UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BCD2BFEFC1D6C3E3')),
  UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEC8C1D6C6C4B9D6')),
  UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0BABFEB')),
  UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC'))
);
EXIT;
