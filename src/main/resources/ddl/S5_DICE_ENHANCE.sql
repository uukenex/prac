-- ============================================================
-- Season 5 - 30/50/60/70/80/90층 마을 도착 보상: 주사위 강화(+)/마이너스 주사위(-)
-- 상점 (2026-09-08 요청)
--
-- "주사위 눈금 나오는 확률이 1~낀주사위의 최대치인데, 최소치를 1씩 늘려주는 상점강화를
-- 추가하고 싶다"는 요청으로 시작 -- 사용자 확인 결과:
--   - 전부 PP로 구매(자동지급 아님), 가격은 서버가 잠정치로 설정
--   - 계정 전체 공통 적용(장착 중인 주사위 등급 무관)
--   - "마이너스 주사위" 구매도 별도로 만들어 최소치를 반대로(0/음수까지) 낮출 수 있게
--     해달라(4눈금 주사위 등으로 탐사 시 정밀 이동 목적) -- 이것도 "최소치만" 조정
--     (최대치는 항상 그대로), 강화(+)와 완전히 대칭.
--
-- 두 컬럼 다 30/50/60/70/80/90층 마을 도착 순서대로 최대 6단계까지 순차 구매(이전 단계
-- 보유 + 그 층 도달 필요) -- BotS5ServiceImpl.DICE_ENHANCE_UNLOCK/DICE_BONUS_COST/
-- DICE_MALUS_COST, buyDiceEnhance()/diceMinFor()/rollFace() 참고.
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    PROCEDURE add_col_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (' || p_ddl || ')';
        END IF;
    END;
BEGIN
    add_col_if_missing('DICE_MIN_BONUS', 'DICE_MIN_BONUS NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('DICE_MIN_MALUS', 'DICE_MIN_MALUS NUMBER DEFAULT 0 NOT NULL');
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DICE_MIN_BONUS IS '주사위 강화 단계(0~6) -- 모든 주사위 굴림의 최소 눈금을 +N, 최대치는 그대로. 30/50/60/70/80/90층 마을 도착 순서대로 순차 PP 구매';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.DICE_MIN_MALUS IS '마이너스 주사위 단계(0~6) -- 모든 주사위 굴림의 최소 눈금을 -N(0/음수까지 가능, 탐사 정밀 이동용), 최대치는 그대로. DICE_MIN_BONUS와 동일한 해금 순서로 별도 PP 구매';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('DICE_MIN_BONUS','DICE_MIN_MALUS') ORDER BY COLUMN_NAME;
EXIT;
