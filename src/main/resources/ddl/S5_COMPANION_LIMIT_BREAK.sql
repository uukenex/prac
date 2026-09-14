-- ============================================================
-- Season 5 - companion "limit break" system (2026-09-14 request)
--
-- Previously, pulling a companion you already own (same CLASS+NAME, which by
-- design always means the same GRADE too -- see NAME_POOL_BY_JOB_GRADE) just
-- refunded PP (COMPANION_DUPE_REFUND). Now the duplicate instead levels up
-- that owned companion's LIMIT_BREAK stat (max 6, +10% to HP/ATK/DEF per
-- level, applied last in computeEffectiveStat() -- so level 6 = +60% overall).
-- Once a companion is already at LIMIT_BREAK=6, further duplicates fall back
-- to the original PP refund (nothing left to upgrade).
--
-- See BotS5ServiceImpl.pullCompanionCore() / computeEffectiveStat() /
-- LIMIT_BREAK_MAX / LIMIT_BREAK_PCT_PER_LV.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME = 'LIMIT_BREAK';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_COMPANION ADD (LIMIT_BREAK NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_COMPANION.LIMIT_BREAK IS 'limit break level (0-6) from pulling duplicates of this companion; +10% HP/ATK/DEF per level, applied last in computeEffectiveStat()';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_COMPANION' AND COLUMN_NAME = 'LIMIT_BREAK';
EXIT;
