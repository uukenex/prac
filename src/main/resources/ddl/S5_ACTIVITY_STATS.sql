-- ============================================================
-- Season 5 - /tapstats (channel usage) rework: real-time counters instead
-- of scanning TBOT_WORD_HIS (2026-09-04).
--
-- Background: the first cut of /tapstats (BotS5DAO.selectChannelUsageStats)
-- worked by scanning TBOT_WORD_HIS (ROOM_NAME='WEB' vs not) to reverse-engineer
-- which channel each user came from. That table turned out to have 1.27M+ rows
-- and NO indexes at all (checked live: USER_IND_COLUMNS returned zero rows for
-- it) -- a query combining a leading-wildcard LIKE ('%party wipe%' style) with
-- several other full scans literally never returned within this session
-- (started, never completed). Not safe to ship as a live chat command that
-- could be invoked repeatedly.
--
-- Fix: track usage as real-time counters on TBOT_S5_USER_PROGRESS (same
-- pattern as TOTAL_KILL_COUNT / TOTAL_PP_EARNED_VALUE / DICE_ROLL_COUNT_TODAY),
-- bumped once per relevant action at the two controller layers where the
-- channel is actually known (Season5Controller = chat, Season5ViewController
-- = web). /tapstats then does one cheap aggregate over this small table
-- (dozens of rows) instead of touching the huge shared log table at all.
--
-- Trade-off (explicitly flagging this): this resets the "web/chat user" and
-- activity counts to start fresh from whenever this deploys -- the previous
-- log-scan approach could see all-time history (when it worked), this one
-- can't backfill it. Chose correctness-of-a-fast-query over a slow "complete"
-- one; the counters just need to accumulate again from here.
--
-- ASCII-only file, no Korean literals (schema only), safe to re-run (checks
-- existence first per column).
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('WEB_USED_YN',      'WEB_USED_YN CHAR(1) DEFAULT ''N'' NOT NULL');
    add_col_if_missing('CHAT_USED_YN',     'CHAT_USED_YN CHAR(1) DEFAULT ''N'' NOT NULL');
    add_col_if_missing('DICE_COUNT_WEB',   'DICE_COUNT_WEB NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('DICE_COUNT_CHAT',  'DICE_COUNT_CHAT NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('GACHA_COUNT_WEB',  'GACHA_COUNT_WEB NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('GACHA_COUNT_CHAT', 'GACHA_COUNT_CHAT NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('WIPE_COUNT_WEB',   'WIPE_COUNT_WEB NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('WIPE_COUNT_CHAT',  'WIPE_COUNT_CHAT NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WEB_USED_YN      IS 'Y once this user has done any web (SPA) action, set from Season5ViewController';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.CHAT_USED_YN     IS 'Y once this user has done any chat action, set from Season5Controller';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DICE_COUNT_WEB   IS 'lifetime dice(move+combat) presses via web';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DICE_COUNT_CHAT  IS 'lifetime dice(move+combat) presses via chat';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.GACHA_COUNT_WEB  IS 'lifetime gacha command invocations via web (10-pull counts as 1)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.GACHA_COUNT_CHAT IS 'lifetime gacha command invocations via chat (10-pull counts as 1)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WIPE_COUNT_WEB   IS 'lifetime full-party-wipe (defeat) events triggered via web';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WIPE_COUNT_CHAT  IS 'lifetime full-party-wipe (defeat) events triggered via chat';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('WEB_USED_YN','CHAT_USED_YN','DICE_COUNT_WEB','DICE_COUNT_CHAT',
                       'GACHA_COUNT_WEB','GACHA_COUNT_CHAT','WIPE_COUNT_WEB','WIPE_COUNT_CHAT')
  ORDER BY COLUMN_NAME;
EXIT;
