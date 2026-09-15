-- ============================================================
-- Season 5 - accessory gacha: necklace/ring/bracelet (2026-09-15 request)
--
-- "gear gacha is fine, but add a separate accessory gacha for 3 new parts
-- (necklace/ring/bracelet). Necklace = half of (weapon+armor) bonus, ring =
-- half of (weapon+helmet), bracelet = half of (armor+helmet). Same 4 tiers
-- as the existing gear gacha (basic/mid/high/top), unlocked at village floors
-- 50/60/70/80, priced a bit higher than the equivalent-floor gear box."
--
-- Reuses the existing PART/GRADE-based TBOT_S5_USER_EQUIP table (PART just
-- gets 3 new values: NECKLACE/RING/BRACELET, both fit the existing
-- VARCHAR2(10) column) and TBOT_S5_GACHA_MASTER (GACHA_TYPE='ACCESSORY',
-- fits the existing VARCHAR2(10) column) -- no ALTER TABLE needed, this
-- script only inserts new gacha rows. Probabilities per tier are copied
-- verbatim from the matching existing EQUIP tier (gacha_id 5/6/7/8) --
-- "same as the existing gacha" request.
--
-- See BotS5ServiceImpl.pullAccessoryCore()/computeEffectiveStat() (NECKLACE/
-- RING/BRACELET branches)/partNameOf()/equipBonusText().
--
-- ASCII-only file (Korean box names inserted via CP949 HEXTORAW per project
-- convention), safe to re-run (checks GACHA_ID existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_GACHA_MASTER WHERE GACHA_ID = 9;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_GACHA_MASTER
            (GACHA_ID, GACHA_TYPE, GACHA_NAME, UNLOCK_FLOOR, COST_VALUE, COST_EXT,
             PROB_G1, PROB_G2, PROB_G3, PROB_G4, PROB_G5, PROB_G6)
        VALUES
            (9, 'ACCESSORY', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B3B0C0BA20BEC7BCBCBCADB8AE20BBF3C0DA')),
             50, 5000, NULL, 75, 20, 4.5, 0.5, 0, 0);
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_GACHA_MASTER WHERE GACHA_ID = 10;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_GACHA_MASTER
            (GACHA_ID, GACHA_TYPE, GACHA_NAME, UNLOCK_FLOOR, COST_VALUE, COST_EXT,
             PROB_G1, PROB_G2, PROB_G3, PROB_G4, PROB_G5, PROB_G6)
        VALUES
            (10, 'ACCESSORY', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEB5B8B8C7D120BEC7BCBCBCADB8AE20BBF3C0DA')),
             60, 10000, NULL, 0, 70, 22, 7, 1, 0);
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_GACHA_MASTER WHERE GACHA_ID = 11;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_GACHA_MASTER
            (GACHA_ID, GACHA_TYPE, GACHA_NAME, UNLOCK_FLOOR, COST_VALUE, COST_EXT,
             PROB_G1, PROB_G2, PROB_G3, PROB_G4, PROB_G5, PROB_G6)
        VALUES
            (11, 'ACCESSORY', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BAFBB3AAB4C220BEC7BCBCBCADB8AE20BBF3C0DA')),
             70, 22000, NULL, 0, 0, 60, 30, 8, 2);
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_GACHA_MASTER WHERE GACHA_ID = 12;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_GACHA_MASTER
            (GACHA_ID, GACHA_TYPE, GACHA_NAME, UNLOCK_FLOOR, COST_VALUE, COST_EXT,
             PROB_G1, PROB_G2, PROB_G3, PROB_G4, PROB_G5, PROB_G6)
        VALUES
            (12, 'ACCESSORY', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FCBCB3C0C720BEC7BCBCBCADB8AE20BBF3C0DA')),
             80, 45000, NULL, 0, 0, 0, 55, 35, 10);
    END IF;
END;
/

COMMIT;

SELECT GACHA_ID, GACHA_TYPE, GACHA_NAME, UNLOCK_FLOOR, COST_VALUE FROM TBOT_S5_GACHA_MASTER WHERE GACHA_ID >= 9 ORDER BY GACHA_ID;
EXIT;
