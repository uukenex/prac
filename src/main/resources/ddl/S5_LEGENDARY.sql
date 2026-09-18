-- ============================================================
-- Season 5 - legendary fragment / crafting / grade-7 legendary equipment
-- (2026-09-18 request): 50+ boss floors drop "legendary fragment" items
-- probabilistically on kill; 10 fragments can be crafted (30% success,
-- random result) into a unique-named grade-7 legendary equipment piece
-- with its own combat effect.
--
-- [scope note, same-day follow-up messages] "아이템은 송곳만 예시로 만들고
-- 나머지는 내가 만들거야" -- only ONE legendary item (송곳) is seeded here;
-- the rest of the roster will be added later (by the user) via simple
-- INSERTs into TBOT_S5_LEGENDARY_MASTER, no code changes needed since the
-- effect dispatch is data-driven (EFFECT_TYPE/EFFECT_PARAM1/2).
-- "일반사용자에겐 아직 제작부분은 오픈하지 말고" -- crafting is gated behind
-- NO_COOLDOWN_YN (existing admin/test-account flag) in code, not here.
--
-- Idempotent: ALTERs check column existence first (S5_DAILY_DICE_LIMIT.sql
-- pattern), CREATE TABLE/INSERT check existence too since USER_PROGRESS has
-- live player data and this script may need re-running during development.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'LEGEND_FRAGMENT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (LEGEND_FRAGMENT NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'BOSS_KILL_COUNT_TODAY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (BOSS_KILL_COUNT_TODAY NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'BOSS_KILL_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (BOSS_KILL_DATE DATE)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_EQUIP' AND COLUMN_NAME = 'LEGENDARY_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_EQUIP ADD (LEGENDARY_ID NUMBER)';
    END IF;
END;
/

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_LEGENDARY_MASTER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_LEGENDARY_MASTER (
                LEGENDARY_ID   NUMBER        PRIMARY KEY,
                CLASS          VARCHAR2(10)  NOT NULL,
                PART           VARCHAR2(10)  NOT NULL,
                ITEM_NAME      VARCHAR2(50)  NOT NULL,
                EFFECT_TYPE    VARCHAR2(20)  NOT NULL,
                EFFECT_PARAM1  NUMBER        NOT NULL,
                EFFECT_PARAM2  NUMBER        DEFAULT 0,
                FLAVOR_TEXT    VARCHAR2(200)
            )';
    END IF;
END;
/

-- Seed roster: only "송곳" for now (SWORD/WEAPON, DEF_STEAL 50%).
-- ITEM_NAME/FLAVOR_TEXT inserted as CP949 hex via HEXTORAW (CLAUDE.md rule --
-- never write raw Korean literals into a tracked .sql file).
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_LEGENDARY_MASTER WHERE LEGENDARY_ID = 1;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_LEGENDARY_MASTER (LEGENDARY_ID, CLASS, PART, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, EFFECT_PARAM2, FLAVOR_TEXT)
        VALUES (1, 'SWORD', 'WEAPON',
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('BCDBB0F7')),
            'DEF_STEAL', 50, 0,
            UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FBC0C720B9E6BEEEB7C2C0BB20BBA9BED1BEC620B3BB20B0F8B0DDB7C2BFA120B4F5C7D1B4D92028C1D6BBE7C0A720B1BCB8B020C8C420C7D5BBEA29')));
    END IF;
END;
/

COMMIT;

-- Verification
SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME IN ('LEGEND_FRAGMENT', 'BOSS_KILL_COUNT_TODAY', 'BOSS_KILL_DATE');
SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_EQUIP' AND COLUMN_NAME = 'LEGENDARY_ID';
SELECT LEGENDARY_ID, CLASS, PART, ITEM_NAME, EFFECT_TYPE, EFFECT_PARAM1, ASCIISTR(FLAVOR_TEXT) AS FLAVOR_ASCII
FROM TBOT_S5_LEGENDARY_MASTER;
