-- ============================================================
-- Season 5 - one-time cleanup: after the companion name-pool migration
-- (S5_COMPANION_NAME_MIGRATION.sql, 직업당 3종으로 축소), some users ended
-- up with two companions of the same (CLASS, NAME) -- i.e. genuine
-- duplicates that only exist because the migration renamed pre-existing
-- rows onto a name another row already had. Detected via:
--   SELECT USER_NAME, CLASS, NAME, COUNT(*) FROM TBOT_S5_USER_COMPANION
--   WHERE NAME IS NOT NULL GROUP BY USER_NAME, CLASS, NAME HAVING COUNT(*)>1
-- For each duplicate group, keep exactly one (party slot first, then
-- highest grade, then lowest COMPANION_ID as tiebreak) and delete the
-- rest, refunding PP per deleted copy (grade * 50, matching the in-game
-- gacha "20% of a mid-tier pull" dupe-refund order of magnitude).
-- None of the deleted IDs have equipment worn (checked live beforehand).
-- ASCII-only file. Already applied live on 2026-09-03 (verified: 7 rows
-- deleted, both PP refunds landed exactly once, zero duplicate groups
-- remain) -- kept here as a record, DELETE is not idempotent so do not
-- re-run against the same IDs.
-- ============================================================

-- 삭제 대상(모두 미착용 확인됨): 32, 57, 56, 49, 28, 40, 55
DELETE FROM TBOT_S5_USER_COMPANION WHERE COMPANION_ID IN (32, 57, 56, 49, 28, 40, 55);

-- PP 환급: castle/냉동홀붕 50(유이 ★1 1마리 삭제), 팔세쪽있음 350(★1급 4마리*50 + ★2급 1마리*100 + ★1급 1마리*50)
UPDATE TBOT_S5_USER_PROGRESS SET PP_VALUE = PP_VALUE + 50
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8')); -- castle/냉동홀붕

UPDATE TBOT_S5_USER_PROGRESS SET PP_VALUE = PP_VALUE + 350
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C6C8BCBCC2CAC0D6C0BD')); -- 팔세쪽있음

COMMIT;

SELECT USER_NAME, CLASS, NAME, COUNT(*) FROM TBOT_S5_USER_COMPANION
WHERE NAME IS NOT NULL GROUP BY USER_NAME, CLASS, NAME HAVING COUNT(*) > 1;
EXIT;
