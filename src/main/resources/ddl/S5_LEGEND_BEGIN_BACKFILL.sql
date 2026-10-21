-- ============================================================
-- Season 5 - backfill achievement 599 (legend begin) for users who already own two or more star-5 companions at limit-break +6 (2026-10-20).
-- Idempotent (skips users who already have it). ASCII-only.
-- ============================================================
INSERT INTO TBOT_S5_USER_ACH (USER_NAME, ACH_ID)
SELECT c.USER_NAME, 599
  FROM TBOT_S5_USER_COMPANION c
 WHERE c.GRADE = 5 AND c.LIMIT_BREAK >= 6
 GROUP BY c.USER_NAME
HAVING COUNT(*) >= 2
   AND NOT EXISTS (SELECT 1 FROM TBOT_S5_USER_ACH a WHERE a.USER_NAME = c.USER_NAME AND a.ACH_ID = 599);
COMMIT;

SELECT COUNT(*) AS GRANTED FROM TBOT_S5_USER_ACH WHERE ACH_ID = 599;
EXIT;
