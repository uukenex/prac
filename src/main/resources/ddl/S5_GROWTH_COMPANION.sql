-- ============================================================
-- Season 5 - growth companion (2026-10-20): one per user, marked by TBOT_S5_USER_COMPANION.GRADE = 8 (displayed as the white star "1").
--  1) The star-7 unique index used GRADE >= 7, which would also cover the growth companion (grade 8) -> recreate it as GRADE = 7
--     and add a second unique index so a user can have only one growth companion (GRADE = 8).
--  2) TBOT_S5_GROWTH_PORTRAIT: the single fixed portrait URL shared by every growth companion (one row; empty = emoji fallback in the web UI).
--     To set/change: DELETE FROM TBOT_S5_GROWTH_PORTRAIT; INSERT INTO TBOT_S5_GROWTH_PORTRAIT (ID, IMAGE_URL) VALUES (1, '<url>'); COMMIT;
-- ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'UX_S5_COMP_STAR7_USER';
    IF v_cnt > 0 THEN EXECUTE IMMEDIATE 'DROP INDEX UX_S5_COMP_STAR7_USER'; END IF;
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UX_S5_COMP_STAR7_USER ON TBOT_S5_USER_COMPANION (CASE WHEN GRADE = 7 THEN USER_NAME END)';

    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'UX_S5_COMP_GROWTH_USER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UX_S5_COMP_GROWTH_USER ON TBOT_S5_USER_COMPANION (CASE WHEN GRADE = 8 THEN USER_NAME END)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_GROWTH_PORTRAIT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_GROWTH_PORTRAIT (ID NUMBER PRIMARY KEY, IMAGE_URL VARCHAR2(500) NOT NULL, INSERT_DATE DATE DEFAULT SYSDATE NOT NULL)';
    END IF;
END;
/

SELECT INDEX_NAME, UNIQUENESS FROM USER_INDEXES WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' ORDER BY 1;
SELECT COUNT(*) AS PORTRAIT_ROWS FROM TBOT_S5_GROWTH_PORTRAIT;
EXIT;
