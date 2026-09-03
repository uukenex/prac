-- ============================================================
-- Season 5 - "N층 완전탐사" achievements (ACH_ID = 100 + floor, 101~200 for
-- floors 1~100). Granted from BotS5ServiceImpl.snapshotFloorBest() the first
-- time a floor's TBOT_S5_USER_FLOOR_BEST.FULLY_EXPLORED_YN flips to 'Y'
-- (persists across village-return resets). Each one also hands out 1
-- COMPANION_VOUCHER (applied in Java -- REWARD_TYPE/REWARD_VALUE here are
-- descriptive/for future generic reward-application code, not yet auto-applied
-- by ID alone).
--
-- Idempotent: re-running skips ACH_IDs that already exist.
-- Korean text written as CP949(=KO16MSWIN949) hex via HEXTORAW per CLAUDE.md
-- policy, concatenated with TO_CHAR(floor) (ASCII digits, no encoding risk).
-- ASCII-only file otherwise.
--   suffix1 "층 완전탐사"                              = C3FE20BFCFC0FCC5BDBBE7
--   suffix2 "층을 100% 탐험하면 동료뽑기권 1장을 얻습니다." = C3FEC0BB203130302520C5BDC7E8C7CFB8E920B5BFB7E1BBCCB1E2B1C72031C0E5C0BB20BEF2BDC0B4CFB4D92E
-- ============================================================

DECLARE
    v_name_suffix VARCHAR2(50) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FE20BFCFC0FCC5BDBBE7'));
    v_desc_suffix VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FEC0BB203130302520C5BDC7E8C7CFB8E920B5BFB7E1BBCCB1E2B1C72031C0E5C0BB20BEF2BDC0B4CFB4D92E'));
BEGIN
    FOR f IN 1..100 LOOP
        IF MOD(f, 10) BETWEEN 1 AND 8 THEN -- 사냥터층만(마을/보스층 제외)
            BEGIN
                INSERT INTO TBOT_S5_ACHIEVEMENT
                    (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
                VALUES
                    (100 + f, TO_CHAR(f) || v_name_suffix, TO_CHAR(f) || v_desc_suffix,
                     'FLOOR_EXPLORE', TO_CHAR(f), 'N', 'COMPANION_VOUCHER', '1');
            EXCEPTION
                WHEN DUP_VAL_ON_INDEX THEN NULL; -- 이미 있으면 건너뜀(재실행 안전)
            END;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- 결과 확인 (사냥터층 80개가 있어야 함: floor 1~8,11~18,...,91~98)
SELECT COUNT(*) AS FLOOR_ACH_COUNT FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE = 'FLOOR_EXPLORE';
SELECT ACH_ID, ACH_NAME FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE = 'FLOOR_EXPLORE' ORDER BY ACH_ID FETCH FIRST 5 ROWS ONLY;
EXIT;
