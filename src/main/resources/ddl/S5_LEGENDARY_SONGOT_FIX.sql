-- ============================================================
-- Season 5 - "송곳" legendary sword effect correction (2026-09-21).
-- Original spec (2026-09-18): "steal 50% of enemy DEF, add to own damage"
-- (EFFECT_PARAM1=50). User re-clarified while building the crafting UI:
-- "방어력을 무시하고, 방어력만큼 내데미지에 더한다" (ignore DEF entirely, AND
-- add an amount equal to DEF to my damage) -- i.e. DEF becomes a pure bonus,
-- not a partial pierce. EFFECT_PARAM1 -> 100 (code side: BotS5ServiceImpl's
-- DEF_STEAL branch now computes dmg = ATK*roll + DEF*(PARAM1/100), bypassing
-- the normal "- DEF" subtraction entirely instead of post-hoc adding a
-- partial steal on top of the already-reduced damage).
-- ============================================================

UPDATE TBOT_S5_LEGENDARY_MASTER
   SET EFFECT_PARAM1 = 100,
       FLAVOR_TEXT = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FBC0C720B9E6BEEEB7C2C0BB20B9ABBDC3C7CFB0ED2C20B1D720B9E6BEEEB7C2B8B8C5AD20B3BB20B5A5B9CCC1F6BFA120B4F5C7D1B4D9'))
 WHERE LEGENDARY_ID = 1;

COMMIT;

-- Verification
SELECT LEGENDARY_ID, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, ASCIISTR(FLAVOR_TEXT) AS FLAVOR_ASCII
FROM TBOT_S5_LEGENDARY_MASTER WHERE LEGENDARY_ID = 1;
