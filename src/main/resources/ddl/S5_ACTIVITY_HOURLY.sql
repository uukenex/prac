-- ============================================================
-- Season 5 - hourly activity buckets for /towerStats time-windowed view
-- (2026-09-06 request: "want to see 1-hour / 24-hour / today figures too,
-- not just all-time").
--
-- Design: a small table keyed by (hour bucket, channel, stat type), bumped
-- alongside the existing all-time counters in bumpActivityStat(). This
-- avoids scanning TBOT_WORD_HIS (see S5_ACTIVITY_STATS.sql for why that
-- was abandoned -- 1.28M rows, no useful index for this). Growth is tiny:
-- at most 24 buckets/day x 2 channels x 3 stat types = 144 rows/day.
--
-- Time windows are calendar-based (current hour bucket / last 24 buckets /
-- today's buckets since midnight), not a strict rolling window -- simple
-- and good enough for an admin glance command.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_ACTIVITY_HOURLY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_ACTIVITY_HOURLY (
                BUCKET_DATE DATE NOT NULL,
                CHANNEL     VARCHAR2(10) NOT NULL,
                STAT_TYPE   VARCHAR2(20) NOT NULL,
                CNT         NUMBER DEFAULT 0 NOT NULL,
                CONSTRAINT PK_S5_ACTIVITY_HOURLY PRIMARY KEY (BUCKET_DATE, CHANNEL, STAT_TYPE)
            )';
    END IF;
END;
/

COMMENT ON TABLE TBOT_S5_ACTIVITY_HOURLY IS 'Season5 hourly activity buckets for /towerStats time-windowed view (1h/24h/today), bumped alongside the all-time counters';
COMMENT ON COLUMN TBOT_S5_ACTIVITY_HOURLY.BUCKET_DATE IS 'TRUNC(SYSDATE, HH24) at write time';
COMMENT ON COLUMN TBOT_S5_ACTIVITY_HOURLY.CHANNEL IS 'WEB or CHAT';
COMMENT ON COLUMN TBOT_S5_ACTIVITY_HOURLY.STAT_TYPE IS 'DICE, GACHA, or WIPE';

COMMIT;

SELECT COUNT(*) AS ROW_COUNT FROM TBOT_S5_ACTIVITY_HOURLY;
EXIT;
