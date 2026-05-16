-- 가방 상점 아이템 확인 (91: 노말, 92: 나메, 93: 헬)
-- ITEM_TYPE 변경 없음. item_id 기준으로 Java에서 직접 판별.
SELECT ITEM_ID, ITEM_NAME, ITEM_TYPE FROM TBOT_POINT_NEW_ITEM WHERE ITEM_ID IN (91, 92, 93);
