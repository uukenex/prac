-- ============================================================
-- Season 5 - legendary ring #3 (2026-09-29): COMMON ring, STAT_MULT x2.
-- Request: "keep it common (all jobs), no special ability, existing stats x2,
-- name it Jordan Ring". EFFECT_PARAM1 = 200 (%) multiplies the stat bonus this
-- ONE item gives (ring = half weapon ATK + half helmet HP). Code side:
-- BotS5ServiceImpl.computeEffectiveStat (sm multiplier via
-- selectEquipByCompanion's LEGENDARY_EFFECT_* columns).
-- CP949 hex via HEXTORAW (no raw Korean literals). Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_LEGENDARY_MASTER WHERE LEGENDARY_ID = 3;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_LEGENDARY_MASTER (LEGENDARY_ID, CLASS, PART, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, EFFECT_PARAM2, FLAVOR_TEXT)
        VALUES (3, 'COMMON', 'RING',
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C1B6B4F820B8B5')),
            'STAT_MULT', 200, 0,
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B4C9B7C2C4A1B0A120B5CE20B9E8B7CE20BCDAB1B8C4A1B4C220B9DDC1F6')));
    END IF;
END;
/

COMMIT;

SELECT LEGENDARY_ID, CLASS, PART, EFFECT_TYPE, EFFECT_PARAM1, RAWTOHEX(UTL_RAW.CAST_TO_RAW(ITEM_NAME)) AS NAME_HEX
FROM TBOT_S5_LEGENDARY_MASTER ORDER BY LEGENDARY_ID;
