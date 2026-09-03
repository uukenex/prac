-- ============================================================
-- Season 5 - block-based reward scaling (2026-09-04)
--
-- Part A: choice-ticket grade now scales by which 10-floor block granted it
--   (see S5_BLOCK_CHOICE_TICKET.sql for the base feature). Existing
--   COMPANION_CHOICE_TICKET / WEAPON_CHOICE_TICKET columns are reused as the
--   "grade 3" tier bucket; two more columns per type hold grade 4 / grade 5.
--   Tier by block (BotS5ServiceImpl.checkBlockExploreTicket):
--     block 1-3 (floors 1-30)  -> grade 3
--     block 4-5 (floors 31-50) -> grade 4
--     block 6-10 (floors 51+)  -> grade 5
--
-- Part B: per-floor "N-floor full-explore" achievement (ACH_ID 101-200)
--   reward is no longer a flat "1 generic companion voucher" -- it now scales
--   with block, cycling through companion-gacha tiers (GACHA_ID 1-4 =
--   starter/mid/high/top) 3 blocks at a time, count cycling 1/3/5:
--     block  1 (floor  1- 8): starter tier x1  (unchanged from before)
--     block  2 (floor 11-18): starter tier x3
--     block  3 (floor 21-28): starter tier x5
--     block  4 (floor 31-38): mid tier     x1
--     block  5 (floor 41-48): mid tier     x3
--     block  6 (floor 51-58): mid tier     x5
--     block  7 (floor 61-68): high tier    x1
--     block  8 (floor 71-78): high tier    x3
--     block  9 (floor 81-88): high tier    x5
--     block 10 (floor 91-98): top tier     x1
--   (BotS5ServiceImpl.floorVoucherReward). These are tier-locked vouchers
--   (only usable on that exact gacha tier) held in 4 new columns, separate
--   from the existing generic COMPANION_VOUCHER (still used by the SHOP-tile
--   freebie and stays usable on any unlocked tier -- untouched by this).
--
-- Idempotent-ish: ALTER TABLE ADD will error if re-run (columns already
-- exist) -- that's fine, it's a one-time schema change, just skip re-running.
-- No Korean literals in this file (schema/comment change only, all English
-- to sidestep the encoding pitfalls documented in CLAUDE.md).
-- ============================================================

ALTER TABLE TBOT_S5_USER_PROGRESS ADD (
    COMPANION_CHOICE_TICKET_G4 NUMBER DEFAULT 0 NOT NULL,
    COMPANION_CHOICE_TICKET_G5 NUMBER DEFAULT 0 NOT NULL,
    WEAPON_CHOICE_TICKET_G4    NUMBER DEFAULT 0 NOT NULL,
    WEAPON_CHOICE_TICKET_G5    NUMBER DEFAULT 0 NOT NULL,
    COMPANION_VOUCHER_T1       NUMBER DEFAULT 0 NOT NULL,
    COMPANION_VOUCHER_T2       NUMBER DEFAULT 0 NOT NULL,
    COMPANION_VOUCHER_T3       NUMBER DEFAULT 0 NOT NULL,
    COMPANION_VOUCHER_T4       NUMBER DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_CHOICE_TICKET_G4 IS 'grade-4 companion choice ticket count (block 4-5 full-explore reward)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_CHOICE_TICKET_G5 IS 'grade-5 companion choice ticket count (block 6-10 full-explore reward)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WEAPON_CHOICE_TICKET_G4    IS 'grade-4 weapon choice ticket count (block 4-5 full-explore reward)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.WEAPON_CHOICE_TICKET_G5    IS 'grade-5 weapon choice ticket count (block 6-10 full-explore reward)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_VOUCHER_T1       IS 'tier-locked free pulls for COMPANION gacha tier 1 (starter), from per-floor full-explore reward';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_VOUCHER_T2       IS 'tier-locked free pulls for COMPANION gacha tier 2 (mid)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_VOUCHER_T3       IS 'tier-locked free pulls for COMPANION gacha tier 3 (high)';
COMMENT ON COLUMN TBOT_S5_USER_PROGRESS.COMPANION_VOUCHER_T4       IS 'tier-locked free pulls for COMPANION gacha tier 4 (top)';

COMMIT;

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS'
  AND COLUMN_NAME IN ('COMPANION_CHOICE_TICKET_G4','COMPANION_CHOICE_TICKET_G5',
                       'WEAPON_CHOICE_TICKET_G4','WEAPON_CHOICE_TICKET_G5',
                       'COMPANION_VOUCHER_T1','COMPANION_VOUCHER_T2','COMPANION_VOUCHER_T3','COMPANION_VOUCHER_T4')
  ORDER BY COLUMN_NAME;
EXIT;
