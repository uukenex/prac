-- ============================================================
-- Season 5 - "keep me signed in" for the Kakao login of the admin log pages (2026-10-28).
-- The login itself only lived in the HttpSession (60 min idle, wiped on every deploy, and the
-- KakaoTalk in-app browser drops the session cookie when it closes), so the log page asked to
-- log in again almost every time. After a successful login the browser now also gets a long
-- lived random cookie; only its SHA-256 hash is stored here (a DB leak does not give usable cookies).
-- Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_KAKAO_REMEMBER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_KAKAO_REMEMBER (
                TOKEN_HASH  VARCHAR2(64) NOT NULL,
                KAKAO_ID    VARCHAR2(40) NOT NULL,
                REG_DATE    DATE DEFAULT SYSDATE NOT NULL,
                LAST_USED   DATE DEFAULT SYSDATE NOT NULL,
                EXPIRE_DATE DATE NOT NULL,
                CONSTRAINT PK_S5_KAKAO_REMEMBER PRIMARY KEY (TOKEN_HASH)
            )';
    END IF;
END;
/
