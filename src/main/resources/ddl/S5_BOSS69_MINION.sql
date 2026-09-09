-- ============================================================
-- Season 5 - 69층 보스: 쓰러진 동료를 하수인으로 되살려 공격 (2026-09-10 요청)
--
-- "69층 보스는 동료를 죽이면 보스 하수인으로 살려서 플레이어를 공격하도록 하자
-- (공격불가상태이며 보스처치시 사라짐)" 요청.
--
-- 설계: 이 보스(69층 한정)의 반격으로 파티원이 쓰러지면(도사 부활도 실패한 경우),
-- 그 동료의 COMPANION_ID를 CUR_BOSS_MINION_CIDS(콤마구분)에 추가한다. 이미 HP0이라
-- 파티 공격에는 자동으로 안 낀다("공격불가"). 이후 매 턴, 하수인 명단에 있는 동료들이
-- 자기 자신의 유효 스탯(장비/스탯구매 반영)으로 파티 중 무작위 1명을 추가로 공격한다.
-- 전투가 어떻게 끝나든(처치/전멸/도망 전부 기존 clearMonster 경로를 거침) 자동으로
-- 초기화되므로("보스처치시 사라짐"), 별도 컬럼 신설 없이 기존 CUR_MONSTER_* 라이프사이클에
-- 얹혀서 관리된다.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_BOSS_MINION_CIDS';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (CUR_BOSS_MINION_CIDS VARCHAR2(60) NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CUR_BOSS_MINION_CIDS IS '69층 보스 전투 중 쓰러져 하수인이 된 동료 COMPANION_ID 콤마구분 목록 -- 전투 종료(clearMonster) 시 자동 초기화';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'CUR_BOSS_MINION_CIDS';
EXIT;
