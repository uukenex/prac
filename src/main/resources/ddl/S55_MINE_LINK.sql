-- [2026-11-03] 시즌 5.5(광산) 웹앱 -- 카카오 로그인 계정(KAKAO_ID) <-> 게임 캐릭터(USER_NAME = 람쥐봇 방 "닉네임/캐릭터") 연결.
-- 연결은 웹앱(/mine/) 첫 접속 때 람쥐봇 방 닉네임을 입력해 만든다 -- 기존 광산 아이디면 연결, 없으면 새로 만들어 연결(S55MineServiceImpl.s55Link).
CREATE TABLE TBOT_S55_MINE_LINK (
    KAKAO_ID   VARCHAR2(40)  NOT NULL,
    USER_NAME  VARCHAR2(100) NOT NULL,
    REG_DATE   DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_S55_MINE_LINK PRIMARY KEY (KAKAO_ID)
);
CREATE INDEX IDX_S55_MINE_LINK_USER ON TBOT_S55_MINE_LINK (USER_NAME);
