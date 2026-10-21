-- ============================================================
-- Season 5 - star-7 companion (2026-10-20): at most ONE star-7 companion per user.
-- A function-based unique index: the key is USER_NAME only for GRADE >= 7 rows and NULL otherwise,
-- and Oracle does not index all-NULL keys, so non-star-7 companions are unaffected.
-- The app also checks before inserting (promoteToStar7); this index is the race-proof backstop.
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'UX_S5_COMP_STAR7_USER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UX_S5_COMP_STAR7_USER ON TBOT_S5_USER_COMPANION (CASE WHEN GRADE >= 7 THEN USER_NAME END)';
    END IF;
END;
/

SELECT INDEX_NAME, UNIQUENESS FROM USER_INDEXES WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION';
EXIT;
