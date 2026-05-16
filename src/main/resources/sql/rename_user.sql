-- ============================================================
-- 유저 닉네임(USER_NAME) 변경 스크립트
-- 사용 방법: OLD_NAME / NEW_NAME 을 실제 값으로 치환 후 실행
-- ============================================================

DEFINE OLD_NAME = '변경전닉네임'
DEFINE NEW_NAME = '변경후닉네임'

-- ─────────────────────────────────────────────
-- 1. 기본 유저 테이블
-- ─────────────────────────────────────────────
UPDATE TUSER
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 2. 게임 캐릭터 (New 버전)
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_NEW_USER
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 3. 전투 로그
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_NEW_BATTLE_LOG
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 4. 인벤토리
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_NEW_INVENTORY
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 5. 포인트 랭킹
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_RANK
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 6. 장비 강화
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_WEAPON
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_WEAPON_LOG
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_ACC
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_ACC_LOG
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_HIT_RING
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 7. 아이템 보유
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_ITEM_USER
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 8. 스탯 기록
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_STAT_USER
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 9. 돌 / 업다운
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_STONE
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_UPDOWN
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 10. 야구 게임
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_BASEBALL_M
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 11. 보스 관련 로그
-- ─────────────────────────────────────────────
UPDATE TBOT_BOSS_HIT_LOG
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_BOSS_LOG
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 12. 대전 (FIGHT) - USER_NAME 및 TARGET_NAME 모두 변경
-- ─────────────────────────────────────────────
UPDATE TBOT_POINT_FIGHT
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_POINT_FIGHT
   SET TARGET_NAME = '&NEW_NAME'
 WHERE TARGET_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 13. 차단 목록
-- ─────────────────────────────────────────────
UPDATE TBOT_BLOCK
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 14. 커스텀 명령어
-- ─────────────────────────────────────────────
UPDATE TBOT_WORD_SAVE
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_WORD_HIS
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_WORD_REPLACE
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

UPDATE TBOT_WORD_SAVE_MASTER
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 15. 대체 캐릭터 목록
-- ─────────────────────────────────────────────
UPDATE TBOT_LOA_ALT_CHAR
   SET USER_NAME = '&NEW_NAME'
 WHERE USER_NAME = '&OLD_NAME';

-- ─────────────────────────────────────────────
-- 변경 내용 확인 후 커밋
-- ─────────────────────────────────────────────
-- COMMIT;
