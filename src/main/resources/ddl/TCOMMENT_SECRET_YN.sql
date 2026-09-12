-- ============================================================
-- Free board: secret post flag (2026-09-12 request)
--
-- "Free board should have a secret-post checkbox; if a post is marked
-- secret, only logged-in users can view it."
--
-- Adds SECRET_YN to TCOMMENT (shared by notice/free board rows via
-- COMMENT_CATEGORY). Default 'N' so existing rows and other
-- categories (notice) are unaffected. View-time gating is done in
-- FreeController (checks session login state), not in SQL.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TCOMMENT' AND COLUMN_NAME = 'SECRET_YN';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TCOMMENT ADD (SECRET_YN VARCHAR2(1) DEFAULT ''N'' NOT NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TCOMMENT.SECRET_YN IS 'Y = secret post, only visible to logged-in users (free board only); default N';

COMMIT;

SELECT COLUMN_NAME, DATA_DEFAULT, NULLABLE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TCOMMENT' AND COLUMN_NAME = 'SECRET_YN';
EXIT;
