-- ============================================================
-- Season 5 - 자동사냥 처치수 업적 신설 + 기존 자동사냥 PP 업적(14번) 실제 연동 +
-- 전투 도망(플리) 업적 신설 (2026-09-08 요청)
--
-- 배경:
--   1) "자동사냥으로 몇 회 처치 이런 것도 되니? 가능하면 자동사냥 관련 업적에
--      추가해줘" -- 자동사냥 킬은 이미 TOTAL_KILL_COUNT(수동+자동 합산)에는
--      반영돼서 8/9번(몬스터 사냥꾼/학살자) 업적 대상이지만, "자동사냥으로만"
--      몇 마리인지는 별도로 추적하는 컬럼이 없었다. AUTO_HUNT_KILL_TOTAL 신설,
--      settleAutoHunt()에서 함께 누적.
--   2) 코드 리뷰 중 발견: ACH_ID=14 "자동사냥 입문"(자동사냥으로 PP 1000 누적,
--      ACH_TYPE=AUTO_HUNT_PP_TOTAL)이 마스터 데이터엔 있었지만 실제로 체크하는
--      코드가 없어서(BotS5ServiceImpl 주석에 "TODO: 별도 누적 컬럼이 없어 아직
--      미체크") 이미 만들어진 뒤로 한 번도 지급되지 않고 있었다. 이번에 자동사냥
--      전용 누적 PP 컬럼(AUTO_HUNT_PP_TOTAL_VALUE/EXT)을 추가해서 드디어 연동.
--   3) "몬스터 전투중 도망치다 업적도 있으면 좋을 것 같다" -- 전투 중 /층변경으로
--      도망친 횟수(FLEE_COUNT_TOTAL) 신설, changeFloor()에서 누적.
--
-- ACH_ID 26~27 = AUTO_HUNT_KILL_TOTAL(자동사냥 누적 처치, param=마리수)
-- ACH_ID 28~29 = COMBAT_FLEE_TOTAL(전투 도망 누적 횟수, param=횟수)
-- ACH_ID 14는 이미 존재(S5_MASTER_DATA.sql) -- 이 파일에서 새로 만들지 않음, 코드만 연동.
--
-- 임계값(500/5000, 10/100)은 잠정치 -- 실측 후 조정 가능.
--
-- Korean literals as CP949(=KO16MSWIN949) hex via HEXTORAW per CLAUDE.md policy.
-- ASCII-only file.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('AUTO_HUNT_KILL_TOTAL',   'AUTO_HUNT_KILL_TOTAL NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('AUTO_HUNT_PP_TOTAL_VALUE','AUTO_HUNT_PP_TOTAL_VALUE NUMBER DEFAULT 0 NOT NULL');
    -- PP_EXT와 동일한 이유로 NOT NULL 불가(Oracle이 ''를 NULL로 취급) -- 위 PP_EXT 컬럼 주석 참고
    add_col_if_missing('AUTO_HUNT_PP_TOTAL_EXT', 'AUTO_HUNT_PP_TOTAL_EXT VARCHAR2(1) DEFAULT ''''');
    add_col_if_missing('FLEE_COUNT_TOTAL',       'FLEE_COUNT_TOTAL NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.AUTO_HUNT_KILL_TOTAL IS '자동사냥(미접속 정산)으로만 처치한 누적 마리수 -- 수동 전투 킬은 포함 안 함(그건 TOTAL_KILL_COUNT). ACH_ID 26/27 판정용';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.AUTO_HUNT_PP_TOTAL_VALUE IS '자동사냥으로만 획득한 누적 PP(PP.of 값 부분) -- ACH_ID 14(자동사냥 입문) 판정용';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.AUTO_HUNT_PP_TOTAL_EXT IS '위 AUTO_HUNT_PP_TOTAL_VALUE의 단위(a/b/... 1만배 접미사)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.FLEE_COUNT_TOTAL IS '전투 중 /층변경(도망) 누적 횟수 -- ACH_ID 28/29 판정용';

COMMIT;

DECLARE
    v_name_ahk1 VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0DAB5BFBBE7B3C9B2DB'));                           -- "자동사냥꾼"
    v_desc_ahk1 VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0DAB5BFBBE7B3C9C0B8B7CE20353030B8B6B8AEB8A620C3B3C4A1C7DFB4D9')); -- "자동사냥으로 500마리를 처치했다"
    v_name_ahk2 VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0DAB5BFBBE7B3C9C0C720B4DEC0CE'));                 -- "자동사냥의 달인"
    v_desc_ahk2 VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0DAB5BFBBE7B3C9C0B8B7CE2035303030B8B6B8AEB8A620C3B3C4A1C7DFB4D9')); -- "자동사냥으로 5000마리를 처치했다"
    v_name_flee1 VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FCB7ABC0FB20C8C4C5F0'));                        -- "전략적 후퇴"
    v_desc_flee1 VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FCC5F5BFA1BCAD203130C8B820B5B5B8C1C3C6B4D9'));  -- "전투에서 10회 도망쳤다"
    v_name_flee2 VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B5B5C1D6C0C720B4DEC0CE'));                        -- "도주의 달인"
    v_desc_flee2 VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FCC5F5BFA1BCAD20313030C8B820B5B5B8C1C3C6B4D9')); -- "전투에서 100회 도망쳤다"
BEGIN
    BEGIN
        INSERT INTO TBOT_S5_ACHIEVEMENT (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
        VALUES (26, v_name_ahk1, v_desc_ahk1, 'AUTO_HUNT_KILL_TOTAL', '500', 'N', 'PP', '300');
    EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
    BEGIN
        INSERT INTO TBOT_S5_ACHIEVEMENT (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
        VALUES (27, v_name_ahk2, v_desc_ahk2, 'AUTO_HUNT_KILL_TOTAL', '5000', 'N', 'GACHA_EQUIP', NULL);
    EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
    BEGIN
        INSERT INTO TBOT_S5_ACHIEVEMENT (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
        VALUES (28, v_name_flee1, v_desc_flee1, 'COMBAT_FLEE_TOTAL', '10', 'N', 'PP', '50');
    EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
    BEGIN
        INSERT INTO TBOT_S5_ACHIEVEMENT (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
        VALUES (29, v_name_flee2, v_desc_flee2, 'COMBAT_FLEE_TOTAL', '100', 'N', 'PP', '500');
    EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
    COMMIT;
END;
/

-- 결과 확인 (4건 신규 + 컬럼 4개)
SELECT ACH_ID, RAWTOHEX(UTL_RAW.CAST_TO_RAW(ACH_NAME)) AS NAME_HEX FROM TBOT_S5_ACHIEVEMENT WHERE ACH_ID IN (26,27,28,29) ORDER BY ACH_ID;
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('AUTO_HUNT_KILL_TOTAL','AUTO_HUNT_PP_TOTAL_VALUE','AUTO_HUNT_PP_TOTAL_EXT','FLEE_COUNT_TOTAL')
  ORDER BY COLUMN_NAME;
EXIT;
