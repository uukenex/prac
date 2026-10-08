-- ============================================================
-- Season 5 - legendary bracelet #5 (2026-10-08): common BRACELET, SKILL_RATE_MULT.
-- Effect: the equipped companion's class skill activation rates are multiplied by EFFECT_PARAM1 / 100 (150 = x1.5, e.g. revive 10% -> 15%,
-- capped at 100%). Applies to the class skills (priest revive, archer crit, mage stun, rogue steal/evade, warrior taunt), not to item effects.
-- Name = "scholar's armband" (Korean words, CP949 hex), flavor text in Korean (CP949 hex). Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_LEGENDARY_MASTER WHERE LEGENDARY_ID = 5;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_LEGENDARY_MASTER (LEGENDARY_ID, CLASS, PART, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, EFFECT_PARAM2, FLAVOR_TEXT)
        VALUES (5, 'COMMON', 'BRACELET',
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C7D0C0DAC0C720C5E4BDC3')),
            'SKILL_RATE_MULT', 150, 0,
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3A520BCD3C0C720C1F6BDC4C0BB20BCD5B8F1BFA120B0A8BEC620B5D020C5E4BDC32E20B8F6BFA120C0CDC8F920B1E2BCFAC0CC20B4F520C0DAC1D620B9DFB5BFC7D1B4D92E')));
    END IF;
END;
/

COMMIT;

SELECT LEGENDARY_ID, CLASS, PART, EFFECT_TYPE, EFFECT_PARAM1, RAWTOHEX(UTL_RAW.CAST_TO_RAW(ITEM_NAME)) AS NAME_HEX
FROM TBOT_S5_LEGENDARY_MASTER ORDER BY LEGENDARY_ID;
