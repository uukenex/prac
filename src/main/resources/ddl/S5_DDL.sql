-- ============================================================
-- Season 5 - Tower Climb System : Table DDL
-- Design doc: S5_TOWER_DESIGN.md
-- All numeric currency/combat stats use my.prac.core.util.PP's
-- VALUE + EXT (unit, 10000=a) 2-column pattern.
-- NOTE: keep comments ASCII-only in this file. Korean multibyte
-- text inside inline trailing comments has corrupted statement
-- parsing under this client/DB charset combo before (silently
-- swallowed the next line) -- Korean text only goes in the
-- actual VARCHAR2 data in S5_MASTER_DATA.sql.
-- ============================================================

-- sequences
CREATE SEQUENCE SEQ_S5_COMPANION_ID START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_S5_EQUIP_ID     START WITH 1 INCREMENT BY 1 NOCACHE;

-- floor board tile count (hunting floors only: FLOOR MOD 10 IN 1..8)
CREATE TABLE TBOT_S5_FLOOR_INFO (
    FLOOR       NUMBER        PRIMARY KEY,
    TILE_COUNT  NUMBER        NOT NULL
);

-- fixed board layout per floor
CREATE TABLE TBOT_S5_TILE_MASTER (
    FLOOR       NUMBER        NOT NULL,
    TILE_NO     NUMBER        NOT NULL,
    TILE_TYPE   VARCHAR2(10)  NOT NULL,
    PRIMARY KEY (FLOOR, TILE_NO)
);

-- user progress (block-level state)
-- STATUS: NORMAL / IN_COMBAT / TRAP
-- DICE_GRADE: DICE_6 / DICE_8 / DICE_10 / DICE_12 / DICE_20
-- UNLOCKED_BLOCK: highest block base floor unlocked by boss kill (0,10,20..)
CREATE TABLE TBOT_S5_USER_PROGRESS (
    USER_NAME         VARCHAR2(100) PRIMARY KEY,
    CUR_FLOOR         NUMBER        DEFAULT 1     NOT NULL,
    UNLOCKED_BLOCK    NUMBER        DEFAULT 0     NOT NULL,
    PP_VALUE          NUMBER        DEFAULT 0     NOT NULL,
    PP_EXT            VARCHAR2(1)   DEFAULT '', -- Oracle treats '' as NULL, so NOT NULL is not usable here
    STATUS            VARCHAR2(10)  DEFAULT 'NORMAL' NOT NULL,
    TRAP_TURN_LEFT    NUMBER        DEFAULT 0     NOT NULL,
    DICE_GRADE        VARCHAR2(10)  DEFAULT 'DICE_6' NOT NULL,
    KILL_COUNT_CUR    NUMBER        DEFAULT 0     NOT NULL,
    AUTO_HUNT_YN      CHAR(1)       DEFAULT 'N'   NOT NULL,
    LAST_ACTION_DATE  DATE          DEFAULT SYSDATE,
    REG_DATE          DATE          DEFAULT SYSDATE
);

-- per-floor board position (kept independently per floor, CUR_TILE=0 means not entered yet)
-- the board loops (no "end"): CUR_TILE wraps around TILE_COUNT via modulo on every roll.
CREATE TABLE TBOT_S5_USER_FLOOR_PROGRESS (
    USER_NAME   VARCHAR2(100) NOT NULL,
    FLOOR       NUMBER        NOT NULL,
    CUR_TILE    NUMBER        DEFAULT 0 NOT NULL,
    PRIMARY KEY (USER_NAME, FLOOR)
);

-- discovered tiles per (user, floor) -- landed tiles only, skipped tiles do not count.
-- VISIT_COUNT increments on every re-landing (not just the first); SPECIAL/SHOP tiles
-- only pay out on VISIT_COUNT=1, turning into a normal combat encounter from the 2nd
-- landing onward (anti-farming). Rows are wiped when the user retreats from this floor
-- to its block's village (see changeFloor) so a fresh expedition starts clean.
CREATE TABLE TBOT_S5_USER_TILE_VISIT (
    USER_NAME    VARCHAR2(100) NOT NULL,
    FLOOR        NUMBER        NOT NULL,
    TILE_NO      NUMBER        NOT NULL,
    VISIT_COUNT  NUMBER        DEFAULT 1 NOT NULL,
    VISIT_DATE   DATE DEFAULT SYSDATE,
    PRIMARY KEY (USER_NAME, FLOOR, TILE_NO)
);

-- monster master (one normal + one boss row per block, BLOCK_NO 1..10)
CREATE TABLE TBOT_S5_MONSTER_INFO (
    MONSTER_ID           NUMBER        PRIMARY KEY,
    BLOCK_NO             NUMBER        NOT NULL,
    MONSTER_NAME         VARCHAR2(50)  NOT NULL,
    HP_VALUE              NUMBER        NOT NULL,
    HP_EXT                VARCHAR2(1)   DEFAULT '',
    ATK_VALUE              NUMBER        NOT NULL,
    ATK_EXT                 VARCHAR2(1)   DEFAULT '',
    DEF_VALUE                NUMBER        NOT NULL,
    DEF_EXT                   VARCHAR2(1)   DEFAULT '',
    PP_PER_KILL_VALUE          NUMBER        NOT NULL,
    PP_PER_KILL_EXT              VARCHAR2(1)   DEFAULT '',
    BOSS_YN                       CHAR(1)       DEFAULT 'N' NOT NULL
);

-- user-owned companions (CLASS: WARRIOR/MAGE/ROGUE/ARCHER/PRIEST, GRADE 1..6, PARTY_SLOT 1..3 or NULL)
-- NAME: random Japanese-style name assigned at pull time. IMAGE_URL: portrait fetched once
-- from nekos.best at pull time and persisted (server-side, not just client localStorage
-- like the earlier user_info_view.jsp pattern) so every viewer sees the same face.
CREATE TABLE TBOT_S5_USER_COMPANION (
    COMPANION_ID    NUMBER        PRIMARY KEY,
    USER_NAME       VARCHAR2(100) NOT NULL,
    CLASS           VARCHAR2(10)  NOT NULL,
    GRADE           NUMBER(1)     NOT NULL,
    NAME            VARCHAR2(50),
    IMAGE_URL       VARCHAR2(500),
    CUR_HP_VALUE    NUMBER        NOT NULL,
    CUR_HP_EXT      VARCHAR2(1)   DEFAULT '',
    PARTY_SLOT      NUMBER,
    ACQUIRE_DATE    DATE          DEFAULT SYSDATE
);
CREATE INDEX IDX_S5_COMPANION_USER ON TBOT_S5_USER_COMPANION (USER_NAME);

-- user-owned equipment (PART: HELMET/WEAPON/ARMOR, GRADE 1..6)
CREATE TABLE TBOT_S5_USER_EQUIP (
    EQUIP_ID                NUMBER        PRIMARY KEY,
    USER_NAME                VARCHAR2(100) NOT NULL,
    CLASS                     VARCHAR2(10)  NOT NULL,
    PART                       VARCHAR2(10)  NOT NULL,
    GRADE                      NUMBER(1)     NOT NULL,
    EQUIPPED_COMPANION_ID       NUMBER,
    ACQUIRE_DATE                 DATE          DEFAULT SYSDATE
);
CREATE INDEX IDX_S5_EQUIP_USER ON TBOT_S5_USER_EQUIP (USER_NAME);

-- user purchased stat levels
CREATE TABLE TBOT_S5_USER_STAT (
    USER_NAME   VARCHAR2(100) PRIMARY KEY,
    ATK_MAX_LV  NUMBER DEFAULT 0 NOT NULL,
    ATK_MIN_LV  NUMBER DEFAULT 0 NOT NULL,
    HP_LV       NUMBER DEFAULT 0 NOT NULL
);

-- auto-hunt log
CREATE TABLE TBOT_S5_AUTO_HUNT_LOG (
    USER_NAME         VARCHAR2(100) PRIMARY KEY,
    FLOOR              NUMBER        NOT NULL,
    START_DATE          DATE          NOT NULL,
    LAST_SETTLE_DATE     DATE          NOT NULL
);

-- gacha master (GACHA_TYPE: COMPANION/EQUIP, PROB_G1..G6 = grade probabilities)
CREATE TABLE TBOT_S5_GACHA_MASTER (
    GACHA_ID       NUMBER        PRIMARY KEY,
    GACHA_TYPE     VARCHAR2(10)  NOT NULL,
    GACHA_NAME     VARCHAR2(100) NOT NULL,
    UNLOCK_FLOOR   NUMBER        NOT NULL,
    COST_VALUE     NUMBER        NOT NULL,
    COST_EXT       VARCHAR2(1)   DEFAULT '',
    PROB_G1        NUMBER(5,2)   DEFAULT 0,
    PROB_G2        NUMBER(5,2)   DEFAULT 0,
    PROB_G3        NUMBER(5,2)   DEFAULT 0,
    PROB_G4        NUMBER(5,2)   DEFAULT 0,
    PROB_G5        NUMBER(5,2)   DEFAULT 0,
    PROB_G6        NUMBER(5,2)   DEFAULT 0
);

-- achievement master
CREATE TABLE TBOT_S5_ACHIEVEMENT (
    ACH_ID        NUMBER        PRIMARY KEY,
    ACH_NAME      VARCHAR2(100) NOT NULL,
    ACH_DESC      VARCHAR2(200),
    ACH_TYPE      VARCHAR2(30)  NOT NULL,
    ACH_PARAM     VARCHAR2(50),
    HIDDEN_YN     CHAR(1)       DEFAULT 'N' NOT NULL,
    REWARD_TYPE   VARCHAR2(30),
    REWARD_VALUE  VARCHAR2(50)
);

-- user achievement clears
CREATE TABLE TBOT_S5_USER_ACH (
    USER_NAME   VARCHAR2(100) NOT NULL,
    ACH_ID      NUMBER        NOT NULL,
    CLEAR_DATE  DATE          DEFAULT SYSDATE,
    PRIMARY KEY (USER_NAME, ACH_ID)
);

-- special tile cumulative visit counter
CREATE TABLE TBOT_S5_USER_SPECIAL_VISIT (
    USER_NAME     VARCHAR2(100) PRIMARY KEY,
    VISIT_COUNT   NUMBER DEFAULT 0 NOT NULL
);

COMMIT;

-- combat-in-progress state (added after initial rollout, kept here so a fresh
-- deploy of this script produces the same schema as the live DB)
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    CUR_MONSTER_ID       NUMBER,
    CUR_MONSTER_HP_VALUE NUMBER,
    CUR_MONSTER_HP_EXT   VARCHAR2(1)
);

-- running total kill counter, used for MONSTER_KILL_TOTAL achievements
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    TOTAL_KILL_COUNT NUMBER DEFAULT 0 NOT NULL
);

-- combat skill state: mage stun flag on the monster, priest shield buffering
-- the party's next incoming hit
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    MONSTER_STUNNED_YN CHAR(1)     DEFAULT 'N' NOT NULL,
    SHIELD_VALUE       NUMBER      DEFAULT 0   NOT NULL,
    SHIELD_EXT         VARCHAR2(1) DEFAULT '' -- Oracle treats '' as NULL, so NOT NULL is not usable here
);

-- cooldown gate for /dice: 180s while NORMAL (movement), 30s while IN_COMBAT
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    LAST_DICE_ACTION_DATE DATE
);

-- highest floor ever reached (via STAIRS or boss-kill advance), kept monotonically
-- via GREATEST() whenever CUR_FLOOR is set. /change-floor only allows targets
-- within this range -- you must have actually climbed a floor once before you
-- can fast-travel to it.
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    MAX_FLOOR_REACHED NUMBER DEFAULT 0 NOT NULL
);

-- free-pull vouchers, granted by landing on a SHOP tile (replaces the old /tower-shop
-- command entirely). Consumed automatically the next time the matching gacha is pulled.
ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    COMPANION_VOUCHER NUMBER DEFAULT 0 NOT NULL,
    EQUIP_VOUCHER     NUMBER DEFAULT 0 NOT NULL
);

EXIT;
