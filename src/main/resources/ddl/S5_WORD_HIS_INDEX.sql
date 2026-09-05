-- ============================================================
-- Fix: web "recent messages" feature (/api/tower-messages ->
-- BotS5DAO.selectUserRecentMessages) barely works / hangs (2026-09-05 bug report).
--
-- Root cause: that query is `SELECT ... FROM TBOT_WORD_HIS WHERE USER_NAME = ?
-- ORDER BY INSERT_DATE DESC` against a table with 1,282,733 rows and ZERO
-- indexes (confirmed live via USER_IND_COLUMNS -- same table flagged during
-- the 2026-09-04 /towerStats investigation). Every page load of tower_view.jsp
-- triggers a full table scan + sort, which is slow enough to feel broken and
-- can outright time out.
--
-- Fix: composite index on (USER_NAME, INSERT_DATE) -- matches the query's
-- equality-then-order-by shape exactly, turning it into a cheap index range
-- scan (already sorted, so ROWNUM<=10 short-circuits almost immediately).
--
-- This table is shared across the whole bot (not season5-only); a read index
-- like this only helps other USER_NAME-filtered lookups against it too, and
-- the write overhead of one extra index on inserts is negligible.
--
-- Safe to re-run (checks existence first). ASCII-only file.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_WORD_HIS_USER_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_WORD_HIS_USER_DATE ON TBOT_WORD_HIS (USER_NAME, INSERT_DATE)';
    END IF;
END;
/

SELECT INDEX_NAME, COLUMN_NAME, COLUMN_POSITION
FROM USER_IND_COLUMNS
WHERE TABLE_NAME = 'TBOT_WORD_HIS'
ORDER BY INDEX_NAME, COLUMN_POSITION;
EXIT;
