-- ============================================================
-- Season 5 - "N층 탐사 50%" achievements (ACH_ID = 500 + floor, 550~599 for
-- floors 50~99). Granted from BotS5ServiceImpl.checkExploreHalfReward() the
-- first time a floor's exploration (visited/tileCount) crosses 50% -- the
-- same moment the 50F+ up-stairs gate opens. Each one hands out 1
-- ACCESSORY_VOUCHER (tier varies by 10-floor block, applied in Java --
-- REWARD_TYPE/REWARD_VALUE here are descriptive only, same convention as
-- S5_FLOOR_EXPLORE_ACHIEVEMENTS.sql, not auto-applied by ID alone).
--
-- Without this master row, TBOT_S5_USER_ACH rows in this ID range grant
-- correctly (the app only needs the bare ID for idempotency) but show up as
-- "??" with no name in /list (achievements() looks up ACH_NAME by ACH_ID).
-- This file backfills the missing display data for grants already made by
-- S5_EXPLORE50_ACCESSORY_RETRO.sql plus any the live app grants from now on.
--
-- Idempotent: re-running skips ACH_IDs that already exist.
-- Korean text written as CP949(=KO16MSWIN949) hex via HEXTORAW per CLAUDE.md
-- policy(실제 INSERT되는 문자열 리터럴만 -- 아래 HEXTORAW 인자), concatenated with
-- TO_CHAR(floor) (ASCII digits, no encoding risk).
--   name_suffix "층 탐사 50% 달성"                              = C3FE20C5BDBBE72035302520B4DEBCBA
--   desc_suffix "층을 50% 이상 탐험하면 악세뽑기권 1장을 얻습니다." = C3FEC0BB2035302520C0CCBBF320C5BDC7E8C7CFB8E920BEC7BCBCBBCCB1E2B1C72031C0E5C0BB20BEF2BDC0B4CFB4D92E
-- ============================================================

DECLARE
    v_name_suffix VARCHAR2(50) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FE20C5BDBBE72035302520B4DEBCBA'));
    v_desc_suffix VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FEC0BB2035302520C0CCBBF320C5BDC7E8C7CFB8E920BEC7BCBCBBCCB1E2B1C72031C0E5C0BB20BEF2BDC0B4CFB4D92E'));
    v_tier NUMBER;
BEGIN
    FOR f IN 50..99 LOOP
        IF MOD(f, 10) BETWEEN 1 AND 8 THEN -- 사냥터층만(마을/보스층 제외)
            v_tier := LEAST(GREATEST(TRUNC(f / 10) + 1 - 5, 1), 4); -- accessoryVoucherTierForFloor()와 동일 산식
            BEGIN
                INSERT INTO TBOT_S5_ACHIEVEMENT
                    (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
                VALUES
                    (500 + f, TO_CHAR(f) || v_name_suffix, TO_CHAR(f) || v_desc_suffix,
                     'FLOOR_EXPLORE_HALF', TO_CHAR(f), 'N', 'ACCESSORY_VOUCHER', TO_CHAR(v_tier));
            EXCEPTION
                WHEN DUP_VAL_ON_INDEX THEN NULL; -- 이미 있으면 건너뜀(재실행 안전)
            END;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- 결과 확인 (사냥터층 40개가 있어야 함: floor 51~58,61~68,71~78,81~88,91~98)
SELECT COUNT(*) AS HALF_ACH_COUNT FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE = 'FLOOR_EXPLORE_HALF';
SELECT ACH_ID, ACH_NAME, REWARD_VALUE FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE = 'FLOOR_EXPLORE_HALF' AND ROWNUM <= 5 ORDER BY ACH_ID;
EXIT;
