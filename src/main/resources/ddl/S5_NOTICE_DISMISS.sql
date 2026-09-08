-- ============================================================
-- Season 5 - per-user "don't show again" for the web SPA update notice
-- (2026-09-08 request).
--
-- Problem: the existing notice modal (S5_NOTICE.sql, TBOT_S5_NOTICE) only
-- tracked "last seen version" in the browser's localStorage. Users who
-- open tower-view through a webview that doesn't persist storage between
-- launches (a common pattern for links opened from a KakaoTalk chat bot)
-- saw the notice pop up on literally every visit, since localStorage never
-- retained anything to compare against. Fix: track dismissal server-side,
-- keyed by the same USER_NAME the whole tower-view page already keys off
-- of, so it survives regardless of browser/webview storage behavior.
--
-- NOTICE_SEEN_VERSION stores the TBOT_S5_NOTICE.APP_VERSION the user last
-- pressed "다시 보지 않기" on. The modal shows again only once APP_VERSION
-- changes (admin runs /공지등록 again) -- see getNotice(userName)/
-- dismissNotice(userName) in BotS5ServiceImpl.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NOTICE_SEEN_VERSION';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (NOTICE_SEEN_VERSION VARCHAR2(50) NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.NOTICE_SEEN_VERSION IS 'TBOT_S5_NOTICE.APP_VERSION the user last dismissed via the web notice modal''s "dont show again" button; NULL = never dismissed anything';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'NOTICE_SEEN_VERSION';
EXIT;
