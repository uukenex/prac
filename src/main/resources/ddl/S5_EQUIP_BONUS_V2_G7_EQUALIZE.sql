-- ============================================================
-- Season 5 - make grade-7 (legendary) equip stat bonus identical to grade 6
-- (2026-09-21). "7성장비는 6성과 능력치는 동일하도록해줘. 특수능력만 추가되는걸로
-- 하자" -- legendary items should differentiate purely through their unique
-- combat effect (TBOT_S5_LEGENDARY_MASTER.EFFECT_TYPE), not extra flat stats.
-- Was: grade7 = 1450/0.45/270/0.45/145/0.45 (~1.8x grade6). Now: identical
-- to grade6 (760/0.312/139/0.312/76/0.312).
-- ============================================================

UPDATE TBOT_S5_EQUIP_BONUS_V2
   SET HELM_FLAT = (SELECT HELM_FLAT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6),
       HELM_PCT = (SELECT HELM_PCT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6),
       WEAPON_FLAT = (SELECT WEAPON_FLAT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6),
       WEAPON_PCT = (SELECT WEAPON_PCT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6),
       ARMOR_FLAT = (SELECT ARMOR_FLAT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6),
       ARMOR_PCT = (SELECT ARMOR_PCT FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE = 6)
 WHERE GRADE = 7;

COMMIT;

-- Verification
SELECT GRADE, HELM_FLAT, HELM_PCT, WEAPON_FLAT, WEAPON_PCT, ARMOR_FLAT, ARMOR_PCT
FROM TBOT_S5_EQUIP_BONUS_V2 WHERE GRADE IN (6, 7) ORDER BY GRADE;
