-- ============================================================
-- /chat (AI chat) question/answer history (2026-09-30).
-- Kept 7 days (purged by the app), used to restore the in-memory room queue after a restart
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
