-- ============================================================
-- Season 5 - per-user battle screen UI version preference (2026-09-21).
-- "이전버전은 v1, 지금은v2로 해서 유저가 선택한걸 띄워주도록 하자. 전투화면 v1,v2는
-- db에저장해서 선택한걸 저장하도록 해줘" -- old ("Pokemon-style") battle screen kept as V1,
-- new (Three-Kingdoms-style) battle screen as V2. Default is V2 for everyone (including
-- existing rows) since that's now the intended default experience; users can switch back.
-- Idempotent: checks column existence first (S5_DAILY_DICE_LIMIT.sql pattern).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'BATTLE_SCREEN_VERSION';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE TBOT_S5_USER_PROGRESS ADD (BATTLE_SCREEN_VERSION VARCHAR2(2) DEFAULT 'V2' NOT NULL)]';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'BATTLE_SCREEN_VERSION';
