-- [2026-11-20] DB 우편 -- "우편 보내는 게 배포가 필요하니? 앞으로 DB로도 작업이 될 수 있으면" -- 이 표에 한 줄 넣으면 배포 없이 우편함에 뜬다
-- (서버가 60초 동안 기억해 둠 -- 바로 보이게 하려면 채팅 /갱신). 코드 우편(S55MineServiceImpl.MINE_MAILS)과 합쳐서 보여 준다.
--   MAIL_ID    : 우편 이름(영문/숫자 20자 안, 받았는지는 유저 아이템 MAIL_<MAIL_ID>로 -- 같은 이름을 다시 쓰지 말 것)
--   TITLE/BODY : 제목/내용(NVARCHAR2 -- 이모지 가능. sqlplus로 넣을 땐 UNISTR로, CLAUDE.md 참고)
--   ITEMS      : 줄 아이템 "코드:개수,코드:개수" (예: WP_KIT:10,MANA_STONE:1 -- 코드는 TBOT_S5_USER_ORE.ORE_CODE)
--   RECIPIENTS : 받는 사람 "이름|이름"(비우면 모두)
--   USE_YN     : 'N'이면 숨김
CREATE TABLE TBOT_S55_MAIL (
    MAIL_ID     VARCHAR2(20)    NOT NULL,
    TITLE       NVARCHAR2(200)  NOT NULL,
    BODY        NVARCHAR2(1000),
    ITEMS       VARCHAR2(1000)  NOT NULL,
    RECIPIENTS  NVARCHAR2(2000),
    USE_YN      CHAR(1)         DEFAULT 'Y' NOT NULL,
    CREATED_AT  DATE            DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_S55_MAIL PRIMARY KEY (MAIL_ID)
);
