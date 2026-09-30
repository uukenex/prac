-- ============================================================
-- Season 5 - combat result log for the balance stats page (2026-09-30).
-- One row per finished fight (WIN / WIPE / FLEE). Test/admin accounts
-- (NO_COOLDOWN_YN='Y') are not logged. Powers use the same combatPower()
-- weights as /tower status. Idempotent.
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_BATTLE_STAT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE TBOT_S5_BATTLE_STAT (
                REG_DATE     DATE DEFAULT SYSDATE NOT NULL,
                USER_NAME    VARCHAR2(200) NOT NULL,
                FLOOR        NUMBER NOT NULL,
                KIND         VARCHAR2(1) NOT NULL,
                COMBO        VARCHAR2(60),
                PARTY_SIZE   NUMBER,
                PARTY_POWER  NUMBER,
                MON_POWER    NUMBER,
                RESULT       VARCHAR2(4) NOT NULL,
                TURNS        NUMBER,
                DEAD_CNT     NUMBER
            )';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_S5_BATTLE_STAT_DATE ON TBOT_S5_BATTLE_STAT (REG_DATE)';
    END IF;
END;
/

SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_BATTLE_STAT' ORDER BY COLUMN_ID;
