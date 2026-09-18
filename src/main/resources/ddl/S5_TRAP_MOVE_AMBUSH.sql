-- ============================================================
-- Season 5 - new trap effect "MOVE" (-4~+1 tile shift) + trap-induced
-- combat ambush.
-- (2026-09-18 request): "함정칸 밟으면 -4~1칸으로 이동하게 만드는 트랩도
-- 만들어주고, 함정칸으로인해 전투가 발생하면 한대맞고 시작하도록 해줘
-- (일정층수 이상 선공몬스터한테는 그 전투에 몬스터 데미지가 10%증가한다)"
--
-- MOVE itself needs no new column (reuses TBOT_S5_USER_FLOOR_PROGRESS.CUR_TILE,
-- same as the existing RESET_TILE trap effect). Only the "forced ambush"
-- flag for the resulting combat is new -- same 1-shot-set/read pattern as
-- CUR_MONSTER_DUAL_YN etc.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TRAP_AMBUSH_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (TRAP_AMBUSH_YN CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMIT;

-- Verification
SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'TRAP_AMBUSH_YN';
