-- ============================================================
-- Season 5 - Kakao login members for the admin log pages (2026-09-30).
-- Only the Kakao key (id) and the nickname are stored. Idempotent.
-- NICKNAME is NVARCHAR2 so emoji / rare characters survive the KO16MSWIN949 DB charset.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_KAKAO_MEMBER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_KAKAO_MEMBER (
                KAKAO_ID   VARCHAR2(40) NOT NULL,
                NICKNAME   NVARCHAR2(100),
                REG_DATE   DATE DEFAULT SYSDATE NOT NULL,
                LAST_LOGIN DATE DEFAULT SYSDATE NOT NULL,
                CONSTRAINT PK_S5_KAKAO_MEMBER PRIMARY KEY (KAKAO_ID)
            )';
    END IF;
END;
/
