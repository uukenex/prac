-- ============================================================
-- Season 5 - web SPA update notice / forced-refresh nudge (2026-09-07 request).
--
-- Problem: some web users rarely refresh the tab, so they keep running a
-- stale build of tower_view.jsp after a deploy. Fix: a tiny singleton
-- table the admin updates via chat command (/notice register, see
-- Season5Controller.setNotice / BotS5ServiceImpl.setNotice) whenever they
-- deploy something -- the web page polls it and shows a "new update,
-- please refresh" modal (with the notice text) the moment the version
-- changes, plus a persistent button to reopen the same notice anytime.
--
-- NOTICE_TEXT is NCLOB (not plain VARCHAR2/CLOB) for the exact same reason
-- TBOT_WORD_HIS.RES was migrated to NCLOB (see S5_WORD_HIS_EMOJI_FIX.sql
-- and the CLAUDE.md "sqlplus Unicode round-trip" note) -- this DB's
-- default charset is KO16MSWIN949 (CP949), which cannot represent all
-- Korean text safely through every write path, let alone emoji. Writing
-- the notice is done via a chat command (JDBC bind through the custom
-- NCharClobTypeHandler, same one used for TBOT_WORD_HIS.RES), never via
-- sqlplus, so the CP949 pitfall never applies here in practice -- NCLOB
-- is used anyway as defense in depth and for consistency.
-- APP_VERSION is a plain VARCHAR2 (ASCII timestamp string, e.g.
-- 20260907153000) generated in Java -- no Korean, no encoding risk.
--
-- ASCII-only file, no Korean literals, safe to re-run (idempotent insert).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_NOTICE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_NOTICE (
                NOTICE_ID    NUMBER        PRIMARY KEY,
                APP_VERSION  VARCHAR2(50)  NOT NULL,
                NOTICE_TEXT  NCLOB,
                UPDATE_DATE  DATE DEFAULT SYSDATE NOT NULL
            )';
    END IF;
END;
/

COMMENT ON TABLE TBOT_S5_NOTICE IS 'singleton row (NOTICE_ID=1) driving the web SPA update-notice/forced-refresh modal';
COMMENT ON COLUMN TBOT_S5_NOTICE.APP_VERSION IS 'bumped (timestamp string) every time the notice is (re)registered; the web page compares this against what it last saw';
COMMENT ON COLUMN TBOT_S5_NOTICE.NOTICE_TEXT IS 'Korean-safe (NCLOB) announcement text shown in the modal; set via chat command, never sqlplus';

MERGE INTO TBOT_S5_NOTICE T
USING (SELECT 1 AS ID FROM DUAL) S
ON (T.NOTICE_ID = S.ID)
WHEN NOT MATCHED THEN
    INSERT (NOTICE_ID, APP_VERSION, NOTICE_TEXT)
    VALUES (1, TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS'), NULL);

COMMIT;

SELECT NOTICE_ID, APP_VERSION, ASCIISTR(NOTICE_TEXT) AS NOTICE_TEXT_ASCII FROM TBOT_S5_NOTICE;
EXIT;
