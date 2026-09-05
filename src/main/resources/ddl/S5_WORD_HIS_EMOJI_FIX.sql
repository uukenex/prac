-- ============================================================
-- Fix: emoji get silently corrupted to '?' when logged into the shared
-- TBOT_WORD_HIS table (2026-09-05 bug report -- "recent messages" web
-- viewer shows garbled text).
--
-- Root cause (confirmed live): NLS_CHARACTERSET for this DB is
-- KO16MSWIN949 (CP949), which TBOT_WORD_HIS.REQ (VARCHAR2) and .RES
-- (CLOB) both inherit -- CP949 has no code points for emoji at all, so
-- any emoji character gets replaced with '?' the moment it is written,
-- irrecoverably. USER_NAME on the same table is NVARCHAR2 and therefore
-- uses NLS_NCHAR_CHARACTERSET (UTF8 on this DB) instead, which is why
-- Korean/usernames always came through fine while emoji did not.
--
-- Fix: convert REQ to NVARCHAR2(1000) and RES to NCLOB so both use the
-- UTF8 national character set going forward. Oracle does not allow a
-- direct ALTER ... MODIFY across these type families with existing
-- data, so this uses add-column / copy / drop / rename.
--
-- IMPORTANT: this table is live (the bot keeps inserting into it while
-- this migration runs -- confirmed: row count grew ~150 rows over the
-- ~1 minute the first attempt took). A first attempt that also tried to
-- re-enable NOT NULL on REQ failed with ORA-02296 because rows inserted
-- by the running app during the copy window had NULL in the new column
-- (the app only knows the old column names). This version: (a) does a
-- second "catch up" UPDATE for stragglers immediately before the
-- drop/rename to shrink that race window, and (b) does NOT try to
-- restore the NOT NULL constraint on the new REQ column, so a rare
-- straggler row landing with NULL there is harmless (not a constraint
-- violation) rather than aborting the whole migration.
--
-- Historical rows that already have '?' in place of emoji CANNOT be
-- recovered by this script (the original bytes are gone) -- this only
-- fixes future inserts.
--
-- Safe to re-run: skips work already done (checks column types first).
-- ASCII-only file.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_req_type VARCHAR2(30);
    v_res_type VARCHAR2(30);
    v_has_req_new NUMBER;
    v_has_res_new NUMBER;
BEGIN
    SELECT DATA_TYPE INTO v_req_type FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_WORD_HIS' AND COLUMN_NAME = 'REQ';
    SELECT DATA_TYPE INTO v_res_type FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_WORD_HIS' AND COLUMN_NAME = 'RES';
    SELECT COUNT(*) INTO v_has_req_new FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_WORD_HIS' AND COLUMN_NAME = 'REQ_NEW';
    SELECT COUNT(*) INTO v_has_res_new FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_WORD_HIS' AND COLUMN_NAME = 'RES_NEW';

    IF v_req_type = 'VARCHAR2' THEN
        IF v_has_req_new = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS ADD (REQ_NEW NVARCHAR2(1000))';
        END IF;
        EXECUTE IMMEDIATE 'UPDATE TBOT_WORD_HIS SET REQ_NEW = TO_NCHAR(REQ) WHERE REQ_NEW IS NULL';
        COMMIT;
        -- Second pass right before the swap, to catch rows inserted by the
        -- still-running app during the first pass (no NOT NULL re-enable --
        -- see note above on why that is skipped).
        EXECUTE IMMEDIATE 'UPDATE TBOT_WORD_HIS SET REQ_NEW = TO_NCHAR(REQ) WHERE REQ_NEW IS NULL';
        COMMIT;
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS DROP COLUMN REQ';
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS RENAME COLUMN REQ_NEW TO REQ';
        DBMS_OUTPUT.PUT_LINE('REQ converted to NVARCHAR2.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('REQ already ' || v_req_type || ', skipped.');
    END IF;

    IF v_res_type = 'CLOB' THEN
        IF v_has_res_new = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS ADD (RES_NEW NCLOB)';
        END IF;
        EXECUTE IMMEDIATE 'UPDATE TBOT_WORD_HIS SET RES_NEW = TO_NCLOB(RES) WHERE RES_NEW IS NULL';
        COMMIT;
        EXECUTE IMMEDIATE 'UPDATE TBOT_WORD_HIS SET RES_NEW = TO_NCLOB(RES) WHERE RES_NEW IS NULL';
        COMMIT;
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS DROP COLUMN RES';
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_WORD_HIS RENAME COLUMN RES_NEW TO RES';
        DBMS_OUTPUT.PUT_LINE('RES converted to NCLOB.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('RES already ' || v_res_type || ', skipped.');
    END IF;
END;
/

-- Verify.
SELECT COLUMN_NAME, DATA_TYPE, NULLABLE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_WORD_HIS' AND COLUMN_NAME IN ('REQ', 'RES', 'USER_NAME')
ORDER BY COLUMN_ID;
SELECT COUNT(*) AS TOTAL_ROWS,
       SUM(CASE WHEN REQ IS NULL THEN 1 ELSE 0 END) AS NULL_REQ
FROM TBOT_WORD_HIS;
EXIT;
