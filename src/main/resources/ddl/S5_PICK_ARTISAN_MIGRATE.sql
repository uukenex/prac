-- [2026-11-13] 곡괭이 왕도 장인 강화 가격 변경 마이그레이션
-- 예전: 최대 내구도 1000 이상부터 세공된 보석 1개 = +30 (회차와 상관없이)
-- 지금: 회차 n(1부터)마다 보석 (n - 1) / 10 + 1개 = +30 (1~10회차 1개, 11~20회차 2개, ...)
-- 이미 올린 유저: 쓴 보석 수 G = (PICK_DUR_MAX - 1000) / 30 을 새 가격으로 다시 써서 회차 r을 구하고,
-- 마지막 회차를 못 채우고 남는 보석이 있으면 한 회차 더(r + 1). PICK_DUR_MAX = 1000 + 30 r, PICK_DUR = LEAST(PICK_DUR, 새 최대).
-- 백업: BAK1113_PICK (USER_NAME, PICK_DUR, PICK_DUR_MAX). 한글 리터럴 없음(ASCII만).

CREATE TABLE BAK1113_PICK AS SELECT USER_NAME, PICK_DUR, PICK_DUR_MAX FROM TBOT_S5_MINE_USER WHERE PICK_DUR_MAX > 1000;

DECLARE
    g NUMBER;
    r NUMBER;
    c NUMBER;
    nm NUMBER;
BEGIN
    FOR u IN (SELECT USER_NAME, PICK_DUR, PICK_DUR_MAX FROM TBOT_S5_MINE_USER WHERE PICK_DUR_MAX > 1000 FOR UPDATE) LOOP
        g := TRUNC((u.PICK_DUR_MAX - 1000) / 30);
        r := 0;
        LOOP
            c := TRUNC(r / 10) + 1;
            EXIT WHEN g < c;
            g := g - c;
            r := r + 1;
        END LOOP;
        IF g > 0 THEN
            r := r + 1;
        END IF;
        nm := 1000 + 30 * r + MOD(u.PICK_DUR_MAX - 1000, 30);
        UPDATE TBOT_S5_MINE_USER SET PICK_DUR_MAX = nm, PICK_DUR = LEAST(PICK_DUR, nm) WHERE USER_NAME = u.USER_NAME;
    END LOOP;
    COMMIT;
END;
/

SELECT b.PICK_DUR_MAX AS OLD_MAX, m.PICK_DUR_MAX AS NEW_MAX, b.PICK_DUR AS OLD_DUR, m.PICK_DUR AS NEW_DUR
FROM BAK1113_PICK b JOIN TBOT_S5_MINE_USER m ON m.USER_NAME = b.USER_NAME ORDER BY b.PICK_DUR_MAX DESC;
