-- [2026-11-04] 시즌 5.5 웹앱 관리자 푸시 알림 -- 관리자 폰(웹앱 /mine/ 설치)의 푸시 구독. ENDPOINT = 브라우저가 준 푸시 주소.
-- 서버는 내용 없이 "알림 있음"만 보내고(암호화 없이), 폰의 서비스 워커가 /mine/push-pull 로 내용을 받아 띄운다(S55AdminPush).
CREATE TABLE TBOT_S55_PUSH_SUB (
    ENDPOINT   VARCHAR2(1000) NOT NULL,
    KAKAO_ID   VARCHAR2(40),
    USER_NAME  VARCHAR2(100),
    REG_DATE   DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_S55_PUSH_SUB PRIMARY KEY (ENDPOINT)
);
