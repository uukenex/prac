-- ============================================================
-- Season 5 - "계단이 1개만 있어서 다음 층으로 못 올라가는 유저" 신고 대응.
--
-- 배경: S5_STAIRS_UP_DOWN_MIGRATION.sql 은 기존에 이미 존재하던 'STAIRS' 타일을
-- 'STAIRS_UP'으로 이름만 바꿨을 뿐, 짝이 되는 'STAIRS_DOWN' 타일은 추가하지
-- 않았다("각 층 계단 2개(위/아래) 고정" 설계는 이후에 나온 요청이라 기존 보드에는
-- 반영이 안 돼 있었음). 그 결과 마이그레이션 이후로 한 번도 마을에 안 돌아가서
-- 보드가 재생성되지 않은 유저는 계단이 1개(STAIRS_UP)뿐인 상태로 남아 있었다.
--
-- STAIRS_UP 자체는 층 이동 자격(MAX_FLOOR_REACHED) 갱신에 필요한 전부라 이론상
-- 진행 자체는 막히지 않지만, 설계대로 "계단 2개 고정"에도 안 맞고 STAIRS_DOWN이
-- 없으면 위층에서 내려올 때 "도착 지점을 계단칸으로 맞추는" 연출도 못 받는다.
-- 이번 신고 건도 포함해서, 안전하게 해당 보드들에 짝 계단을 채워 넣는다.
--
-- 방법: STAIRS_UP 1개 / STAIRS_DOWN 0개인 (USER_NAME, FLOOR) 보드마다, 그 보드의
-- COMBAT/TRAP/PP 타일 중 TILE_NO가 가장 작은 것 하나를 STAIRS_DOWN으로 바꾼다
-- (중복성이 있는 타입만 대상으로 해서 TREASURE/ELITE/SPECIAL 같은 유일 타일은
-- 건드리지 않음). 이미 계단이 2개인 보드는 대상에서 자동 제외되므로 재실행해도
-- 안전(idempotent).
-- ============================================================

UPDATE TBOT_S5_USER_TILE_MASTER t
SET TILE_TYPE = 'STAIRS_DOWN'
WHERE (USER_NAME, FLOOR, TILE_NO) IN (
    SELECT USER_NAME, FLOOR, TILE_NO FROM (
        SELECT USER_NAME, FLOOR, TILE_NO,
               ROW_NUMBER() OVER (PARTITION BY USER_NAME, FLOOR ORDER BY TILE_NO) rn
        FROM TBOT_S5_USER_TILE_MASTER
        WHERE TILE_TYPE IN ('COMBAT', 'TRAP', 'PP')
        AND (USER_NAME, FLOOR) IN (
            SELECT USER_NAME, FLOOR FROM TBOT_S5_USER_TILE_MASTER
            GROUP BY USER_NAME, FLOOR
            HAVING SUM(CASE WHEN TILE_TYPE = 'STAIRS_UP' THEN 1 ELSE 0 END) = 1
               AND SUM(CASE WHEN TILE_TYPE = 'STAIRS_DOWN' THEN 1 ELSE 0 END) = 0
        )
    )
    WHERE rn = 1
);

COMMIT;

-- 검증: 계단 1개짜리 보드가 더 이상 없어야 함
SELECT COUNT(*) AS REMAINING_1STAIR_BOARDS FROM (
    SELECT USER_NAME, FLOOR FROM TBOT_S5_USER_TILE_MASTER
    GROUP BY USER_NAME, FLOOR
    HAVING SUM(CASE WHEN TILE_TYPE = 'STAIRS_UP' THEN 1 ELSE 0 END) = 1
       AND SUM(CASE WHEN TILE_TYPE = 'STAIRS_DOWN' THEN 1 ELSE 0 END) = 0
);

SELECT TILE_TYPE, COUNT(*) FROM TBOT_S5_USER_TILE_MASTER GROUP BY TILE_TYPE ORDER BY TILE_TYPE;
EXIT;
