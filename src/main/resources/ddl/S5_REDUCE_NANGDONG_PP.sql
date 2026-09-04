-- ============================================================
-- Season 5 - castle/냉동홀붕 계정의 비정상적으로 많은 PP를 진행도에 맞게 보수적으로 삭감.
--
-- 배경: 이 계정은 7층(1블록 안, UNLOCKED_BLOCK=0)에 머물러 있고 총 처치수 468마리인데,
-- 보유 PP가 8214.09(누적 획득 10473.39)로 같은 구간 2위 유저(86.85 PP, 190킬)의 약 95배에
-- 달했다. 1블록 몬스터 PP_PER_KILL=1인 걸 감안하면 자동사냥(최대 8시간 캡, 시간당 6마리)+
-- 수동처치를 전부 합쳐도 정상적으로는 700~1500 PP 선이 상한이라, 실제 값(10473)은 그 7~10배.
-- 같은 계정이 오늘 이미 확인된 범용뽑기권 버그를 웹에서 반복 악용한 전적이 있어(그건 별도로
-- S5_REVOKE_NANGDONG_EXPLOIT.sql로 처리 완료), PP 쪽도 비슷하게 웹에서 뭔가(럭키칸 반복
-- 클릭 등) 있었을 것으로 추정되나, 웹 액션은 오늘에서야 로깅을 추가해서 그 이전 기록은
-- 재구성이 불가능하다. 정확한 "원래 값"을 역산할 수 없어, 유저 판단으로 진행도(7층, 468킬)에
-- 맞는 선(1200 PP)까지 보수적으로 삭감한다.
--
-- ASCII-only file. 이미 삭감된 상태에서 재실행하면 같은 값으로 다시 세팅될 뿐이라 안전.
-- ============================================================

UPDATE TBOT_S5_USER_PROGRESS
SET PP_VALUE = 1200,
    PP_EXT = '',
    TOTAL_PP_EARNED_VALUE = 1200,
    TOTAL_PP_EARNED_EXT = ''
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8'));

COMMIT;

SELECT USER_NAME, PP_VALUE, PP_EXT, TOTAL_PP_EARNED_VALUE, TOTAL_PP_EARNED_EXT
FROM TBOT_S5_USER_PROGRESS
WHERE USER_NAME = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('636173746C652FB3C3B5BFC8A6BAD8'));
EXIT;
