-- ============================================================
-- Board Kakao login (2026-10-08): the normal id/password login and sign-up of the board are removed, members log in with Kakao.
-- TBOARD_KAKAO_MEMBER maps a Kakao id to a board user id:
--   * normal member: USER_ID = 'K' || kakao id (the nickname comes from Kakao and is refreshed on every login)
--   * admin: a row whose USER_ID is an existing TUSER id (THJEON, nickname shown on the board = TUSER.USER_NICK) -- seeded below.
-- TUSER and the board tables are NOT changed. TBOARD_USER_V = TUSER + Kakao members, so the board queries can join it instead of TUSER
-- (posts of old members keep their nickname, posts of Kakao members show the Kakao nickname). Idempotent, ASCII only.
-- Admin seed: Kakao id 4868121890 (the account that logged in to the season-5 pages as the owner) -> THJEON. To add another admin:
--   INSERT INTO TBOARD_KAKAO_MEMBER (KAKAO_ID, USER_ID, NICKNAME) VALUES ('<kakao id>', 'THJEON', UNISTR('\AD00\B9AC\C790'));
-- (only one Kakao account per USER_ID is allowed by the unique key; to give another account admin rights use another TUSER admin id.)
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOARD_KAKAO_MEMBER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOARD_KAKAO_MEMBER (
                KAKAO_ID   VARCHAR2(30)   NOT NULL,
                USER_ID    VARCHAR2(20)   NOT NULL,
                NICKNAME   NVARCHAR2(100) NOT NULL,
                REG_DATE   DATE DEFAULT SYSDATE NOT NULL,
                LAST_LOGIN DATE DEFAULT SYSDATE NOT NULL,
                CONSTRAINT PK_BOARD_KAKAO_MEMBER PRIMARY KEY (KAKAO_ID),
                CONSTRAINT UK_BOARD_KAKAO_USER UNIQUE (USER_ID)
            )';
    END IF;
END;
/

INSERT INTO TBOARD_KAKAO_MEMBER (KAKAO_ID, USER_ID, NICKNAME)
SELECT '4868121890', 'THJEON', UNISTR('\AD00\B9AC\C790') FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TBOARD_KAKAO_MEMBER WHERE KAKAO_ID = '4868121890' OR USER_ID = 'THJEON');
COMMIT;

CREATE OR REPLACE VIEW TBOARD_USER_V AS
SELECT USER_ID, USER_PASS, USER_NAME, USER_EMAIL, USER_PHONE, TO_NCHAR(USER_NICK) AS USER_NICK, INSERT_DATE, LAST_LOGIN_DATE
  FROM TUSER
UNION ALL
SELECT k.USER_ID, NULL, TO_CHAR(NULL), NULL, NULL, k.NICKNAME, k.REG_DATE, k.LAST_LOGIN
  FROM TBOARD_KAKAO_MEMBER k
 WHERE NOT EXISTS (SELECT 1 FROM TUSER t WHERE t.USER_ID = k.USER_ID);

SELECT USER_ID, KAKAO_ID FROM TBOARD_KAKAO_MEMBER;
SELECT COUNT(*) AS VIEW_ROWS FROM TBOARD_USER_V;
