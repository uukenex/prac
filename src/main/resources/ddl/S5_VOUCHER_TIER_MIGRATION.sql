-- ============================================================
-- Season 5 - 등급 무관 범용 뽑기권(COMPANION_VOUCHER/EQUIP_VOUCHER) 폐지.
--
-- 배경: 범용 권은 "해금 여부와 무관하게 아무 등급 가챠에나 쓸 수 있다"는 설계였는데,
-- 이게 곧 "하급 보물상자에서 나온 권 1장으로 35000 PP짜리 최상급 가챠까지 뚫린다"는
-- 뜻이었다("팔세쪽있음" 신고로 확인 -- 중급 전용 권만 지급했는데 상급/최상급 상자에도
-- "무료뽑기권으로만 가능" 표시가 붙어있었음. 실제로도 그 버튼을 누르면 진짜로 뽑힘).
-- 앞으로는 항상 등급별 티어락 권(COMPANION_VOUCHER_T1~4/EQUIP_VOUCHER_T1~4)만 쓰고,
-- 범용 권을 지급하던 유일한 지점(보물상자 TREASURE 칸)도 이제 T1(하급) 전용으로 지급하도록
-- 코드를 고쳤다. 기존에 쌓여있던 범용 권 잔량은 잃어버리지 않게 전부 T1(하급)로 옮긴다
-- ("범용은 초급으로 대체한다" 요청).
--
-- ASCII-only file, 재실행해도 안전(이관 후 잔량이 0이면 더 이상 옮길 게 없음).
-- ============================================================

UPDATE TBOT_S5_USER_PROGRESS
SET COMPANION_VOUCHER_T1 = NVL(COMPANION_VOUCHER_T1, 0) + NVL(COMPANION_VOUCHER, 0),
    COMPANION_VOUCHER = 0
WHERE NVL(COMPANION_VOUCHER, 0) > 0;

UPDATE TBOT_S5_USER_PROGRESS
SET EQUIP_VOUCHER_T1 = NVL(EQUIP_VOUCHER_T1, 0) + NVL(EQUIP_VOUCHER, 0),
    EQUIP_VOUCHER = 0
WHERE NVL(EQUIP_VOUCHER, 0) > 0;

COMMIT;

-- 검증: 범용 권 잔량이 하나도 남지 않아야 함 (식별자는 30바이트 제한이라 짧게)
SELECT COUNT(*) AS REMAIN_CNT FROM TBOT_S5_USER_PROGRESS
WHERE NVL(COMPANION_VOUCHER, 0) > 0 OR NVL(EQUIP_VOUCHER, 0) > 0;

SELECT USER_NAME, COMPANION_VOUCHER_T1, EQUIP_VOUCHER_T1, UNLOCKED_BLOCK
FROM TBOT_S5_USER_PROGRESS
WHERE USER_NAME IN ('Skhy', UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C6C8BCBCC2CAC0D6C0BD')))
ORDER BY USER_NAME;
EXIT;
