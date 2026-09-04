-- ============================================================
-- Season 5 - live DB migration: tier-locked equipment gacha vouchers.
-- Mirrors the existing COMPANION_VOUCHER_T1~T4 design (added in
-- S5_BLOCK_REWARD_SCALING.sql) but for equipment boxes: EQUIP_VOUCHER_T1
-- (하급) ~ EQUIP_VOUCHER_T4 (최상급). Previously equipment only had a single
-- generic EQUIP_VOUCHER usable on whatever tier the player already has
-- naturally unlocked -- this meant /이벤트지급 [tier] N M could not actually
-- grant a tier-specific equipment voucher (M always went to the generic
-- column), and a player couldn't get a mid/high-tier free equipment pull
-- ahead of their own unlock progress via a targeted event grant.
-- consumeEquipVoucher()/hasUsableEquipVoucher() now check the tier-locked
-- column first (matching the requested gacha tier) and fall back to the
-- generic EQUIP_VOUCHER column if no tier-locked one is available -- so
-- existing generic vouchers (from treasure rooms etc.) keep working exactly
-- as before.
-- ASCII-only file, safe to re-run (checks existence first).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    FOR i IN 1..4 LOOP
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'EQUIP_VOUCHER_T' || i;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (EQUIP_VOUCHER_T' || i || ' NUMBER DEFAULT 0 NOT NULL)';
        END IF;
    END LOOP;
END;
/

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME LIKE 'EQUIP_VOUCHER_T%'
ORDER BY COLUMN_NAME;
EXIT;
