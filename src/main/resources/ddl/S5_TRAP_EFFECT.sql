-- ============================================================
-- Season 5 - live DB migration: TBOT_S5_USER_PROGRESS.TRAP_EFFECT
-- ("함정에 걸려도 실제 효과가 없다"는 버그 수정의 일부 -- TRAP_TURN_LEFT는
-- 있었지만 어떤 효과인지 저장하는 컬럼이 없어서 실제 스탯 계산에 전혀
-- 반영되지 않고 있었음. ATK_DOWN/DEF_DOWN 중 어느 쪽인지 여기 저장.)
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TRAP_EFFECT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (TRAP_EFFECT VARCHAR2(10))';
    END IF;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TRAP_EFFECT';
EXIT;
