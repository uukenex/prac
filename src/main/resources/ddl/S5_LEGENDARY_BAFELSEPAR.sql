-- ============================================================
-- Season 5 - legendary bow #2 (2026-09-29): ARCHER-only, DOUBLE_SHOT.
-- Request: "add a star-7 bow 'Bafelsepar bow' for archers; 30% double shot".
-- Effect: on each attack, EFFECT_PARAM1 % chance to fire once more with the
-- same dice roll (extra hit re-applies DEF/min-dmg/crit independently).
-- Code: BotS5ServiceImpl party attack loop (DOUBLE_SHOT branch).
-- ITEM_NAME/FLAVOR_TEXT are CP949 hex via HEXTORAW (CLAUDE.md rule: no raw
-- Korean literals in tracked .sql files). Idempotent (skips if id=2 exists).
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_LEGENDARY_MASTER WHERE LEGENDARY_ID = 2;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_LEGENDARY_MASTER (LEGENDARY_ID, CLASS, PART, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, EFFECT_PARAM2, FLAVOR_TEXT)
        VALUES (2, 'BOW', 'WEAPON',
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B9D9C6E7BCBCC6C4B8A320C8B0')),
            'DOUBLE_SHOT', 30, 0,
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BDC3C0A7B8A620B4E7B1E2B4C220BCF8B0A32C20C8ADBBECC0CC20C7CFB3AA20B4F520B1D7B8B2C0DAC3B3B7B320B5FBB6F3BAD9B4C2B4D9')));
    END IF;
END;
/

COMMIT;

SELECT LEGENDARY_ID, CLASS, PART, EFFECT_TYPE, EFFECT_PARAM1, RAWTOHEX(UTL_RAW.CAST_TO_RAW(ITEM_NAME)) AS NAME_HEX
FROM TBOT_S5_LEGENDARY_MASTER ORDER BY LEGENDARY_ID;
