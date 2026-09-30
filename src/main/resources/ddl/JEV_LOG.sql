-- ============================================================
-- Jev (TypeSafe System One) usage history (2026-09-30). One row per Jev call.
-- INSTRUCTIONS / CRITERIA = the exact question text and answer criteria (JSON) that were sent to Jev,
-- so it is always traceable which wording produced ANSWER / SCORE.
-- Example: user asked "hello" -> question id "search" (noul) -> ANSWER=no, SCORE=0.03, DECISION=NO_SEARCH.
-- Never purged by the app. Human-readable fields are NVARCHAR2 (bound via NCharStringTypeHandler). Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_JEV_LOG';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_JEV_LOG (
                REG_DATE      TIMESTAMP(3) DEFAULT SYSTIMESTAMP NOT NULL,
                PURPOSE       VARCHAR2(30),
                ROOM_NAME     NVARCHAR2(200),
                USER_NAME     NVARCHAR2(200),
                INPUT_TEXT    NVARCHAR2(500),
                QUESTION_ID   VARCHAR2(30),
                QUESTION_TYPE VARCHAR2(10),
                INSTRUCTIONS  NVARCHAR2(1000),
                CRITERIA      NVARCHAR2(1000),
                ANSWER        VARCHAR2(20),
                SCORE         NUMBER(6,4),
                THRESHOLD     NUMBER(4,2),
                DECISION      VARCHAR2(20),
                JEV_MODEL     VARCHAR2(60),
                INPUT_TOKENS  NUMBER,
                OUTPUT_TOKENS NUMBER,
                LATENCY_MS    NUMBER,
                STATUS        VARCHAR2(10),
                ERROR_MSG     VARCHAR2(300),
                RAW_JSON      VARCHAR2(1000)
            )';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_JEV_LOG_DATE ON TBOT_JEV_LOG (REG_DATE)';
    ELSE
        -- migrate the first version (QUESTION_TEXT was a fixed label with no real meaning)
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_JEV_LOG' AND COLUMN_NAME = 'INSTRUCTIONS';
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_JEV_LOG ADD (INSTRUCTIONS NVARCHAR2(1000), CRITERIA NVARCHAR2(1000))';
        END IF;
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_JEV_LOG' AND COLUMN_NAME = 'QUESTION_TEXT';
        IF v_cnt > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_JEV_LOG DROP COLUMN QUESTION_TEXT';
        END IF;
    END IF;
END;
/
