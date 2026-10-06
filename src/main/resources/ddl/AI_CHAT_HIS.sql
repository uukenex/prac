-- ============================================================
-- /chat (AI chat) question/answer history (2026-09-30).
-- Never deleted (kept permanently); the app only reads the last 7 days to restore the in-memory room queue after a restart
-- and to find similar past conversations in the same room. NVARCHAR2 columns so emoji survive
-- the KO16MSWIN949 DB charset (bound via NCharStringTypeHandler). Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_AI_CHAT_HIS';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_AI_CHAT_HIS (
                REG_DATE   TIMESTAMP(3) DEFAULT SYSTIMESTAMP NOT NULL,
                ROOM_NAME  NVARCHAR2(200) NOT NULL,
                USER_NAME  NVARCHAR2(200) NOT NULL,
                QUESTION   NVARCHAR2(500),
                ANSWER     NVARCHAR2(700)
            )';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_AI_CHAT_HIS_ROOM ON TBOT_AI_CHAT_HIS (ROOM_NAME, REG_DATE)';
    END IF;
END;
/

-- 2026-09-30: request/response timestamps and the models actually used (idempotent add).
-- REQ_DATE = when the message was received, RES_DATE = when the answer was produced,
-- MODEL = model that wrote the answer (e.g. gpt-6-luna / gemini), INTENT_MODEL = model that decided search or not (jev-latest / gpt model / keyword).
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_AI_CHAT_HIS' AND COLUMN_NAME = 'REQ_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_AI_CHAT_HIS ADD (REQ_DATE TIMESTAMP(3), RES_DATE TIMESTAMP(3), MODEL VARCHAR2(60), INTENT_MODEL VARCHAR2(60))';
    END IF;
END;
/

-- 2026-10-06: embedding of the question+answer summary for semantic similar-conversation search.
-- EMBEDDING = base64 of float32 vector (256 dims = 1368 chars), EMBED_MODEL = model that made it (rows from a different model are not compared).
-- Rows without a vector keep working through the old word-overlap search. Idempotent.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_AI_CHAT_HIS' AND COLUMN_NAME = 'EMBEDDING';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_AI_CHAT_HIS ADD (EMBEDDING VARCHAR2(4000), EMBED_MODEL VARCHAR2(60))';
    END IF;
END;
/
