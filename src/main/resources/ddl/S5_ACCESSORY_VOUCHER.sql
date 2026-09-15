-- ============================================================
-- Season 5 - tier-locked accessory gacha vouchers (2026-09-15 request)
--
-- "let /event-grant also grant accessory gacha vouchers via a 4th
-- parameter: /event-grant basic 0 0 1 => 1x basic-tier accessory voucher".
--
-- Mirrors the existing COMPANION_VOUCHER_T1..4 / EQUIP_VOUCHER_T1..4 columns
-- exactly (see S5_TOWER_DESIGN.md / grantEventVouchers()) -- 4 tier-locked
-- counters (T1=basic..T4=top), each usable only on that exact accessory
-- gacha tier regardless of unlock floor (same as equip/companion vouchers).
--
-- See BotS5ServiceImpl.grantEventVouchers()/hasUsableAccessoryVoucher()/
-- consumeAccessoryVoucher()/pullAccessoryCore().
--
-- ASCII-only file, no Korean literals, safe to re-run.
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    FOR i IN 1..4 LOOP
        SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'ACCESSORY_VOUCHER_T' || i;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (ACCESSORY_VOUCHER_T' || i || ' NUMBER DEFAULT 0 NOT NULL)';
        END IF;
    END LOOP;
END;
/

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.ACCESSORY_VOUCHER_T1 IS 'tier-locked free accessory gacha voucher count, tier 1 (basic) -- /event-grant only';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.ACCESSORY_VOUCHER_T2 IS 'tier-locked free accessory gacha voucher count, tier 2 (mid)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.ACCESSORY_VOUCHER_T3 IS 'tier-locked free accessory gacha voucher count, tier 3 (high)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.ACCESSORY_VOUCHER_T4 IS 'tier-locked free accessory gacha voucher count, tier 4 (top)';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME LIKE 'ACCESSORY_VOUCHER_T%'
ORDER BY COLUMN_NAME;
EXIT;
