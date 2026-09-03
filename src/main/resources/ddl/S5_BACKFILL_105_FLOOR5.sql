-- ============================================================
-- Season 5 - one-time backfill for "5층 완전탐사"(ACH_ID 105) achievement.
--
-- Background: today's DB migration added TBOT_S5_USER_FLOOR_BEST and the
-- per-floor "N층 완전탐사" achievements (101~200). Because block-1 boss kill
-- permanently blocks re-entry to floors 1~9 ("과거 구간 복귀 불가"), any user
-- who already 100%-explored a block-1 floor and progressed past its boss
-- BEFORE this feature existed can never earn that floor's achievement
-- organically -- there is no way to go back and redo it.
--
-- Investigated via TBOT_WORD_HIS (chat log) for existing holders of the
-- account-wide "탐험왕"(ACH_ID 25) who have zero per-floor achievements yet:
--   - 안주파밍   : confirmed floor 5 (20:06:46 09-02 "8/8칸 발견 + 탐험왕
--                 달성", immediately followed by "/층이동 5" -> "5층 -> 5층,
--                 8/8칸 발견")
--   - 팔세쪽있음 : confirmed floor 5 (stairs 3->4 at 19:04, 4->5 at 19:26,
--                 exploration climbs to 8/8 on floor 5, 탐험왕 at 20:49:37,
--                 stays on the same 8-tile floor until reaching floor 6 later)
--   - castle/냉동홀붕: EXCLUDED per user decision -- no "탐험왕" text exists
--                 anywhere in their 29,696-row chat history despite holding
--                 ACH_ID 25 in TBOT_S5_USER_ACH, so it wasn't earned through
--                 organic play (likely a dev/test-time direct DB grant) and
--                 the floor cannot be determined.
--
-- Korean literals as CP949(=KO16MSWIN949) hex via HEXTORAW per CLAUDE.md
-- policy. ASCII-only file. Idempotent (re-running is safe).
-- ============================================================

DECLARE
    v_user1 VARCHAR2(100) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEC8C1D6C6C4B9D6')); -- 안주파밍
    v_user2 VARCHAR2(100) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C6C8BCBCC2CAC0D6C0BD')); -- 팔세쪽있음
    v_tile_count NUMBER;
    v_already    NUMBER;
BEGIN
    SELECT TILE_COUNT INTO v_tile_count FROM TBOT_S5_FLOOR_INFO WHERE FLOOR = 5;

    FOR v_user IN (SELECT v_user1 AS U FROM DUAL UNION ALL SELECT v_user2 FROM DUAL) LOOP
        MERGE INTO TBOT_S5_USER_FLOOR_BEST T
        USING (SELECT v_user.U AS U, 5 AS F FROM DUAL) S
        ON (T.USER_NAME = S.U AND T.FLOOR = S.F)
        WHEN MATCHED THEN
            UPDATE SET BEST_VISITED_COUNT = v_tile_count, TILE_COUNT = v_tile_count,
                       FULLY_EXPLORED_YN = 'Y', UPDATE_DATE = SYSDATE
        WHEN NOT MATCHED THEN
            INSERT (USER_NAME, FLOOR, BEST_VISITED_COUNT, TILE_COUNT, FULLY_EXPLORED_YN)
            VALUES (v_user.U, 5, v_tile_count, v_tile_count, 'Y');

        SELECT COUNT(*) INTO v_already FROM TBOT_S5_USER_ACH WHERE USER_NAME = v_user.U AND ACH_ID = 105;
        IF v_already = 0 THEN
            INSERT INTO TBOT_S5_USER_ACH (USER_NAME, ACH_ID, CLEAR_DATE) VALUES (v_user.U, 105, SYSDATE);
            -- 재실행해도 중복 지급되지 않도록, 업적을 이번에 처음 부여할 때만 뽑기권 지급
            UPDATE TBOT_S5_USER_PROGRESS SET COMPANION_VOUCHER = NVL(COMPANION_VOUCHER,0) + 1 WHERE USER_NAME = v_user.U;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- 결과 확인
SELECT USER_NAME, ACH_ID, CLEAR_DATE FROM TBOT_S5_USER_ACH WHERE ACH_ID = 105 ORDER BY USER_NAME;
SELECT USER_NAME, FLOOR, BEST_VISITED_COUNT, TILE_COUNT, FULLY_EXPLORED_YN FROM TBOT_S5_USER_FLOOR_BEST WHERE FLOOR = 5 ORDER BY USER_NAME;
SELECT USER_NAME, COMPANION_VOUCHER FROM TBOT_S5_USER_PROGRESS WHERE USER_NAME IN (
    UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BEC8C1D6C6C4B9D6')),
    UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C6C8BCBCC2CAC0D6C0BD'))
);
EXIT;
