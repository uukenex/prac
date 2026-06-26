-- BAG_DROP_PITY -> BAG_DROP 마이그레이션
-- PK 충돌 시 INSERT_DATE +1초씩 retry

DECLARE
    CURSOR c IS
        SELECT ROWID AS RID, USER_NAME, INSERT_DATE
          FROM TBOT_POINT_NEW_INVENTORY
         WHERE GAIN_TYPE = 'BAG_DROP_PITY'
         ORDER BY USER_NAME, INSERT_DATE;

    v_date DATE;
    v_offset NUMBER;
BEGIN
    FOR r IN c LOOP
        v_offset := 1;
        v_date   := r.INSERT_DATE;

        -- 충돌 없는 INSERT_DATE 찾을 때까지 +1초
        LOOP
            BEGIN
                UPDATE TBOT_POINT_NEW_INVENTORY
                   SET GAIN_TYPE    = 'BAG_DROP',
                       DEL_YN       = '0',
                       INSERT_DATE  = v_date
                 WHERE ROWID = r.RID;
                EXIT; -- 성공하면 루프 종료
            EXCEPTION
                WHEN DUP_VAL_ON_INDEX THEN
                    v_date   := r.INSERT_DATE + v_offset / 86400;
                    v_offset := v_offset + 1;
            END;
        END LOOP;
    END LOOP;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('마이그레이션 완료');
END;
/

-- 검증
SELECT GAIN_TYPE, DEL_YN, COUNT(*) AS CNT
  FROM TBOT_POINT_NEW_INVENTORY
 WHERE GAIN_TYPE IN ('BAG_DROP', 'BAG_DROP_PITY')
 GROUP BY GAIN_TYPE, DEL_YN
 ORDER BY GAIN_TYPE, DEL_YN;
