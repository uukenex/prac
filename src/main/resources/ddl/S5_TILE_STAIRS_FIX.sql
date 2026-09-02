-- ============================================================
-- Season 5 - [긴급] 계단 누락 층 수정 + 보드 칸 수 확장 (기배포 DB용)
--
-- 배경: 2026-09-02 실 DB 점검 결과, TBOT_S5_TILE_MASTER를 랜덤 생성할 때
-- STAIRS(계단) 칸이 확률 배치라서 칸 수가 적은 층은 한 개도 안 나올 수
-- 있었고, 실제로 80개 사냥터층(1~8/11~18/…/91~98) 중 28개 층에 계단이
-- 전혀 없어서 그 층에 들어간 유저는 영영 다음 층으로 못 올라가는 상태였음
-- (5층 포함 -- "5층 이후 전투가 안 된다"는 문의의 실제 원인으로 추정).
--
-- 이 스크립트는 기존 유저 진행 데이터(방문기록/보드 위치)를 전혀 건드리지
-- 않고 다음 두 가지만 한다. 여러 번 실행해도 안전(idempotent):
--   1) STAIRS가 0개인 층: 그 층에서 가장 번호가 큰 칸 하나를 STAIRS로 전환
--      (긴급 탈출구 확보, 즉시 효과)
--   2) 보드 칸 수 확장: 1~48층은 15~30칸, 51~98층은 100~150칸이 되도록
--      기존 칸 뒤에 새 칸을 이어붙임(기존 칸은 번호/유형 변경 없음).
--      50층 이후는 계단 찾아 올라가는 데 더 헤매도록 큰 보드로 요청받음.
-- ============================================================

-- 0) (참고용) 수정 전 상태 확인 -- 계단 0개인 층 목록
SELECT FLOOR, TILE_COUNT FROM TBOT_S5_FLOOR_INFO fi
WHERE NOT EXISTS (
    SELECT 1 FROM TBOT_S5_TILE_MASTER tm WHERE tm.FLOOR = fi.FLOOR AND tm.TILE_TYPE = 'STAIRS'
)
ORDER BY FLOOR;

DECLARE
    v_fix_tile_no NUMBER;
    v_rand        NUMBER;
    v_type        VARCHAR2(10);
    v_target      NUMBER;
BEGIN
    -- 1) 계단 0개 층 긴급 수정
    FOR r IN (
        SELECT FLOOR FROM TBOT_S5_FLOOR_INFO fi
        WHERE NOT EXISTS (
            SELECT 1 FROM TBOT_S5_TILE_MASTER tm WHERE tm.FLOOR = fi.FLOOR AND tm.TILE_TYPE = 'STAIRS'
        )
    ) LOOP
        SELECT MAX(TILE_NO) INTO v_fix_tile_no FROM TBOT_S5_TILE_MASTER WHERE FLOOR = r.FLOOR;
        UPDATE TBOT_S5_TILE_MASTER SET TILE_TYPE = 'STAIRS'
        WHERE FLOOR = r.FLOOR AND TILE_NO = v_fix_tile_no;
    END LOOP;
    COMMIT;

    -- 2) 보드 칸 수 확장 (기존 칸엔 손 안 대고 뒤에 이어붙이기만 함)
    FOR fi IN (SELECT FLOOR, TILE_COUNT FROM TBOT_S5_FLOOR_INFO ORDER BY FLOOR) LOOP
        IF fi.FLOOR <= 48 THEN
            v_target := TRUNC(DBMS_RANDOM.VALUE(15, 31));   -- 15~30
        ELSE
            v_target := TRUNC(DBMS_RANDOM.VALUE(100, 151)); -- 100~150
        END IF;

        IF fi.TILE_COUNT < v_target THEN
            FOR t IN (fi.TILE_COUNT + 1)..v_target LOOP
                v_rand := DBMS_RANDOM.VALUE(0, 100);
                IF    v_rand < 35 THEN v_type := 'COMBAT';
                ELSIF v_rand < 50 THEN v_type := 'PP';
                ELSIF v_rand < 60 THEN v_type := 'SHOP';
                ELSIF v_rand < 70 THEN v_type := 'TRAP';
                ELSIF v_rand < 85 THEN v_type := 'SPECIAL';
                ELSE                    v_type := 'STAIRS';
                END IF;
                INSERT INTO TBOT_S5_TILE_MASTER (FLOOR, TILE_NO, TILE_TYPE)
                VALUES (fi.FLOOR, t, v_type);
            END LOOP;
            UPDATE TBOT_S5_FLOOR_INFO SET TILE_COUNT = v_target WHERE FLOOR = fi.FLOOR;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- 결과 확인: 이제 전부 STAIRS_CNT >= 1 이어야 함
SELECT FLOOR, TILE_COUNT,
       (SELECT COUNT(*) FROM TBOT_S5_TILE_MASTER tm WHERE tm.FLOOR = fi.FLOOR AND tm.TILE_TYPE='STAIRS') AS STAIRS_CNT
FROM TBOT_S5_FLOOR_INFO fi
ORDER BY FLOOR;

EXIT;
