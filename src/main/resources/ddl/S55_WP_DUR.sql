-- [2026-11-18] 워프 포인트 내구도(모든 유저가 함께 쓰는 값) -- "출발지와 도착지의 내구도가 닳고, 다 되면 그 지역 이동 불가, 마을은 무한, 처음 200".
-- 줄이 없으면 200(가득)으로 본다. 쓸 때마다/고칠 때마다 MERGE(S55MineServiceImpl.mineWpDurSet).
CREATE TABLE TBOT_S55_WP_DUR (
    WP_ID     VARCHAR2(30) NOT NULL,
    DUR       NUMBER(6)    DEFAULT 200 NOT NULL,
    UPD_DATE  DATE         DEFAULT SYSDATE,
    CONSTRAINT PK_S55_WP_DUR PRIMARY KEY (WP_ID)
);
