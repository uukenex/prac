-- [2026-11-18] DB 점검 6번: 광산 캔 칸 기록(TBOT_S5_MINE_DUG)을 유저별 최신순(ORDER BY DUG_AT_MS DESC)으로 읽는 조회가
-- PK(USER_NAME, MAP_ID, CELL)로는 유저 범위만 좁히고 매번 정렬했다 -> 유저 + 캔 시각 인덱스로 정렬 없이 읽는다.
-- XE라 ONLINE 빌드가 안 돼 만드는 동안 쓰기가 잠깐 막힌다(2026-11-18 라이브 적용: 약 1.6만 행, 0.05초).
CREATE INDEX IX_S5_MINE_DUG_USER_AT ON TBOT_S5_MINE_DUG (USER_NAME, DUG_AT_MS);
