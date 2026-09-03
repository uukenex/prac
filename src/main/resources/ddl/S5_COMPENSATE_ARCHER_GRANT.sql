-- ============================================================
-- Season 5 - one-off compensation grant.
-- User reported (and confirmed via live data): already owned a GRADE=2 ARCHER,
-- pulled a GRADE=3 ARCHER that rolled the same name under the OLD
-- grade-independent name pool, got refunded 20% PP instead of receiving the
-- companion (the bug fixed by NAME_POOL_BY_JOB_GRADE + S5_NAME_TIER_MIGRATION.sql).
-- This grants the GRADE=3 ARCHER that should have been kept.
-- Stat = calcBaseStat("ARCHER", 3): GRADE_BASE[2]=(182,18,10) * ARCHER mult
-- (0.7,1.8,0.8) = HP round(127.4)=127, ATK round(32.4)=32, DEF round(8)=8
-- (ATK/DEF are derived at combat time from CLASS+GRADE, not stored columns --
-- only CUR_HP_VALUE needs to be set here, full HP).
-- Username/name are Korean -- CP949 bytes via HEXTORAW per CLAUDE.md.
-- ============================================================

INSERT INTO TBOT_S5_USER_COMPANION
    (COMPANION_ID, USER_NAME, CLASS, GRADE, NAME, IMAGE_URL, CUR_HP_VALUE, CUR_HP_EXT, PARTY_SLOT)
VALUES
    (SEQ_S5_COMPANION_ID.NEXTVAL,
     UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C5B8B6F4B0EDB3C9C0CC2FB9D9B5E5')), -- target user handle (Korean, see verify query below for plaintext)
     'ARCHER', 3,
     UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('B8AEC4DA')),
     NULL, 127, '', NULL);

COMMIT;

-- Verify: shows the plaintext username/name and confirms the byte-exact insert
SELECT COMPANION_ID, USER_NAME, CLASS, GRADE, NAME,
       RAWTOHEX(UTL_RAW.CAST_TO_RAW(USER_NAME)) AS USER_HEX,
       RAWTOHEX(UTL_RAW.CAST_TO_RAW(NAME)) AS NAME_HEX
FROM TBOT_S5_USER_COMPANION
WHERE COMPANION_ID = (SELECT MAX(COMPANION_ID) FROM TBOT_S5_USER_COMPANION);

EXIT;
