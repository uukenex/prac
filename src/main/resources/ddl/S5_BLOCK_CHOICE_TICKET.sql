-- ============================================================
-- Season 5 - 10층 구간별 "완전탐사 선택권" (2026-09-04)
--
-- 매 10층 구간(마을+사냥터8+보스)마다:
--   - 앞 4개 사냥터층(X1~X4)을 전부 완전탐사(역대 최고기록 TBOT_S5_USER_FLOOR_BEST
--     기준, 마을 복귀로 리셋 안 됨) 하면 -> ★3 동료 선택권 1장
--   - 뒤 4개 사냥터층(X5~X8)을 전부 완전탐사하면 -> ★3 무기 선택권 1장
-- 기존 "N층 완전탐사"(ACH_ID 101~200, 동료뽑기권=랜덤) 업적과는 별개로,
-- 이건 등급/부위(무기)가 확정이고 직업만 유저가 고르는 "선택권"이라 랜덤
-- 가챠권과 구분해서 컬럼을 따로 둠. 선택권은 웹 UI에서만 사용 가능(채팅
-- 명령어 없음) -- BotS5ServiceImpl.redeemCompanionChoiceTicket/redeemWeaponChoiceTicket,
-- Season5ViewController /api/tower-action?type=REDEEM_COMPANION_TICKET|REDEEM_WEAPON_TICKET.
--
-- 부여 시점: BotS5ServiceImpl.snapshotFloorBest()에서 층 하나가 새로 완전탐사될
-- 때마다 그 층이 속한 4층 그룹(X1~X4 또는 X5~X8) 전체가 완전탐사인지 확인해서
-- grantAchievement로 중복 없이 지급(달성 여부는 TBOT_S5_USER_ACH로 추적).
--
-- ACH_ID 301~310: 구간별(블록 1~10) 동료 선택권, ACH_TYPE='BLOCK_EXPLORE_LOW', ACH_PARAM=블록번호
-- ACH_ID 401~410: 구간별(블록 1~10) 무기 선택권,   ACH_TYPE='BLOCK_EXPLORE_HIGH', ACH_PARAM=블록번호
--
-- Korean literals as CP949(=KO16MSWIN949) hex via HEXTORAW per CLAUDE.md policy,
-- concatenated with TO_CHAR(층번호)(ASCII 숫자라 인코딩 위험 없음). ASCII-only file.
-- ============================================================

ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    COMPANION_CHOICE_TICKET NUMBER DEFAULT 0 NOT NULL,
    WEAPON_CHOICE_TICKET    NUMBER DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_CHOICE_TICKET IS '10층 구간 앞4층(X1~X4) 완전탐사 보상 - 직업 골라 ★3 동료 획득권(웹 UI 전용)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WEAPON_CHOICE_TICKET    IS '10층 구간 뒤4층(X5~X8) 완전탐사 보상 - 직업 골라 ★3 무기 획득권(웹 UI 전용)';

DECLARE
    v_name_comp VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FE20B5BFB7E120BCB1C5C3B1C7'));               -- "층 동료 선택권"
    v_name_weap VARCHAR2(50)  := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FE20B9ABB1E220BCB1C5C3B1C7'));               -- "층 무기 선택권"
    v_desc_comp VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FEC0BB20B8F0B5CE20BFCFC0FCC5BDBBE7C7CFB8E920A1DA3320B5BFB7E120BCB1C5C3B1C72031C0E5C0BB20B9DEBDC0B4CFB4D92E')); -- "층을 모두 완전탐사하면 ★3 동료 선택권 1장을 받습니다."
    v_desc_weap VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C3FEC0BB20B8F0B5CE20BFCFC0FCC5BDBBE7C7CFB8E920A1DA3320B9ABB1E220BCB1C5C3B1C72031C0E5C0BB20B9DEBDC0B4CFB4D92E')); -- "층을 모두 완전탐사하면 ★3 무기 선택권 1장을 받습니다."
    v_low_start NUMBER; v_low_end NUMBER; v_high_start NUMBER; v_high_end NUMBER;
BEGIN
    FOR b IN 1..10 LOOP
        v_low_start  := (b - 1) * 10 + 1;
        v_low_end    := (b - 1) * 10 + 4;
        v_high_start := (b - 1) * 10 + 5;
        v_high_end   := (b - 1) * 10 + 8;
        BEGIN
            INSERT INTO TBOT_S5_ACHIEVEMENT
                (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
            VALUES
                (300 + b,
                 TO_CHAR(v_low_start) || '~' || TO_CHAR(v_low_end) || v_name_comp,
                 TO_CHAR(v_low_start) || '~' || TO_CHAR(v_low_end) || v_desc_comp,
                 'BLOCK_EXPLORE_LOW', TO_CHAR(b), 'N', 'COMPANION_CHOICE_TICKET', '1');
        EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
        BEGIN
            INSERT INTO TBOT_S5_ACHIEVEMENT
                (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
            VALUES
                (400 + b,
                 TO_CHAR(v_high_start) || '~' || TO_CHAR(v_high_end) || v_name_weap,
                 TO_CHAR(v_high_start) || '~' || TO_CHAR(v_high_end) || v_desc_weap,
                 'BLOCK_EXPLORE_HIGH', TO_CHAR(b), 'N', 'WEAPON_CHOICE_TICKET', '1');
        EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL; END;
    END LOOP;
    COMMIT;
END;
/

-- 결과 확인 (20건이어야 함)
SELECT COUNT(*) AS BLOCK_TICKET_ACH_COUNT FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE IN ('BLOCK_EXPLORE_LOW','BLOCK_EXPLORE_HIGH');
SELECT ACH_ID, ACH_NAME FROM TBOT_S5_ACHIEVEMENT WHERE ACH_TYPE IN ('BLOCK_EXPLORE_LOW','BLOCK_EXPLORE_HIGH') ORDER BY ACH_ID;
EXIT;
