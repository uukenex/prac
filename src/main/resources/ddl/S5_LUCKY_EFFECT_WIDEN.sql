-- ============================================================
-- Season 5 - live DB hotfix: LUCKY_EFFECT column too narrow.
-- ORA-12899: value too large for column "LUCKY_EFFECT" (actual: 12, maximum: 10)
--
-- TBOT_S5_USER_PROGRESS.LUCKY_EFFECT was created as VARCHAR2(10), but the
-- block6+ lucky-tile effect pool (luckyEffectList in BotS5ServiceImpl) has
-- grown past that: "SHIELD_ON_ATK" is 13 chars, "HP_DOUBLE_1T" is 12 chars
-- (the one that actually hit the error live). An earlier design-doc note
-- claimed "no column size change needed" when these were added -- that
-- was wrong; nobody had rolled one of the >10-char values live yet until
-- now. Widen to VARCHAR2(20) for headroom against future additions.
-- ============================================================

DECLARE
    v_len NUMBER;
BEGIN
    SELECT DATA_LENGTH INTO v_len FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LUCKY_EFFECT';
    IF v_len < 20 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS MODIFY (LUCKY_EFFECT VARCHAR2(20))';
    END IF;
END;
/

COMMIT;

-- Verification
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LUCKY_EFFECT';
