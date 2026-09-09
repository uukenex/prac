-- ============================================================
-- Season 5 - 61층+(블록7) 일반 몬스터 2마리 동시 등장 + 69층 보스 10턴 폭주 타이머 +
-- 블록7(61~70층) 스탯 재조정 (2026-09-09 요청)
--
-- 배경: "61층부터는 일반몬스터 두마리가 나오고, 50층구간(블록6)보다 더 쌔야 한다" +
-- "69층 보스는 10턴내 처치 옵션(폭주 타이머)을 추가해달라" 요청.
--
-- 1) TBOT_S5_USER_PROGRESS에 컬럼 2개 신설:
--    - CUR_MONSTER_DUAL_YN: 지금 싸우는 몬스터가 "2마리(합산 체력)" 인코딩인지
--      (CUR_MONSTER_ELITE_YN/CUR_MONSTER_MIDBOSS_YN과 동일 패턴, 별도 몬스터 슬롯을
--      새로 만들지 않고 기존 보스의 "한 턴 2명 공격" 다중타겟 인프라를 재사용해서
--      체력/보상만 2배로 만드는 방식으로 구현 -- BotS5ServiceImpl 참고)
--    - CUR_COMBAT_TURN: 지금 전투가 몇 턴째인지(새 전투 시작/종료 시 0으로 리셋) --
--      69층 보스 10턴 폭주 판정용.
--
-- 2) 블록7(61~70층) 몬스터 스탯 -- 블록6(51~60층) 대비 명확히 강하도록 재조정.
--    기존(51층+ ATK 3배만 적용된 값)은 블록6이 9/7 재조정(S5_BLOCK6_SPECUP.sql, 13000/
--    188/28) 이후로도 그대로 방치돼 있어서, 오히려 일반 몬스터 ATK(87)가 블록6(188)보다
--    낮고 보스 ATK(192)도 블록6 보스(225)보다 낮은 역전 현상이 있었다(신고로 확인).
--    - 107(일반, 68층=구간 내 마지막 사냥터층 기준값=100%): 5500/87/10 -> 26000/376/56
--      (블록6 106의 현재값 13000/188/28을 정확히 2배 -- BotS5ServiceImpl의
--      applyHardcoreFloorScale()이 61층(50%)~68층(100%) 사이를 자동으로 선형 보간하므로,
--      61층 값이 정확히 블록6 58층 값(13000/188/28)과 매끄럽게 이어짐).
--    - 207(69층 보스): 33000/192/15 -> 60000/400/40 (블록6 보스 206의 30000/225/15보다
--      명확히 강하게)
--    PP_PER_KILL은 이번엔 건드리지 않음(이미 +50% 보상 인상 적용돼 있음, 필요시 별도 조정).
--
-- ASCII-only file, no Korean literals in UPDATE (숫자만), safe to re-run.
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
    add_col_if_missing('CUR_MONSTER_DUAL_YN', 'CUR_MONSTER_DUAL_YN CHAR(1) DEFAULT ''N'' NOT NULL');
    add_col_if_missing('CUR_COMBAT_TURN', 'CUR_COMBAT_TURN NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_MONSTER_DUAL_YN IS '61층+(블록7) 일반 몬스터 조우가 "2마리"(합산체력, 다중타겟 반격) 인코딩인지 -- Y면 HP/PP보상 2배 + 매 턴 파티 2명 공격';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_COMBAT_TURN IS '지금 전투 진행 턴수(새 전투 시작/종료 시 0으로 리셋) -- 69층 보스 10턴 폭주 타이머 등 턴제한 판정용';

COMMIT;

UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 26000, ATK_VALUE = 376, DEF_VALUE = 56 WHERE MONSTER_ID = 107;
UPDATE TBOT_S5_MONSTER_INFO SET HP_VALUE = 60000, ATK_VALUE = 400, DEF_VALUE = 40  WHERE MONSTER_ID = 207;

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('CUR_MONSTER_DUAL_YN','CUR_COMBAT_TURN') ORDER BY COLUMN_NAME;
SELECT MONSTER_ID, BLOCK_NO, MONSTER_NAME, HP_VALUE, ATK_VALUE, DEF_VALUE, PP_PER_KILL_VALUE, BOSS_YN
FROM TBOT_S5_MONSTER_INFO WHERE BLOCK_NO IN (6,7) ORDER BY BLOCK_NO, BOSS_YN;
EXIT;
