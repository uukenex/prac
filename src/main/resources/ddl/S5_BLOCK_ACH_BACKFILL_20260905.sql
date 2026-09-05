-- ============================================================
-- Backfill: block-exploration achievement/ticket missed by users who had
-- already fully explored a 4-floor group (X1-X4 or X5-X8) BEFORE the
-- "block exploration ticket" feature shipped (2026-09-05 bug report).
--
-- Root cause: BotS5ServiceImpl.checkBlockExploreTicket() only runs from
-- inside snapshotFloorBest()'s "just NEWLY granted this floor's own
-- achievement" branch -- i.e. it only fires at the moment the LAST floor
-- of a 4-floor group first reaches 100%. If all 4 floors were already
-- fully explored (and their individual per-floor achievements already
-- granted) before this feature existed, that trigger moment already
-- passed and can never fire again for that user/group -- a one-time
-- migration gap, not an ongoing bug (any group whose last floor completes
-- after this feature's deploy works fine).
--
-- This script was generated from a live identification query (see
-- S5_TOWER_DESIGN.md for the query) that found exactly one affected
-- (user, group): castle/naengdonghol-bung, floors 1-4 (LOW group of
-- block 1), ACH_ID 301, tier 3 (base companion choice ticket column,
-- no _G4/_G5 suffix). Mirrors exactly what checkBlockExploreTicket()
-- would have done live.
--
-- Korean user name literal below is via HEXTORAW (CP949) per this repo's
-- established encoding-safety practice (see CLAUDE.md), not plain UTF-8
-- text, so this file itself stays ASCII-only.
-- ============================================================

DECLARE
    v_user VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8'));
    v_cnt  NUMBER;
BEGIN
    -- Idempotent: only act if not already granted.
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_USER_ACH WHERE USER_NAME = v_user AND ACH_ID = 301;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_USER_ACH (USER_NAME, ACH_ID) VALUES (v_user, 301);
        UPDATE TBOT_S5_USER_PROGRESS
           SET COMPANION_CHOICE_TICKET = NVL(COMPANION_CHOICE_TICKET, 0) + 1
         WHERE USER_NAME = v_user;
    END IF;
END;
/

COMMIT;

-- Verify.
SELECT USER_NAME, ACH_ID FROM TBOT_S5_USER_ACH
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8')) AND ACH_ID = 301;
SELECT USER_NAME, COMPANION_CHOICE_TICKET FROM TBOT_S5_USER_PROGRESS
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8'));
EXIT;
