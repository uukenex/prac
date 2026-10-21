-- ============================================================
-- Season 5 - star-7 companion summon (2026-10-20)
--  1) TBOT_S5_STAR7_PORTRAIT: one fixed portrait URL per job, shared by every user (summon reads this; if a job has no row yet,
--     the first summon fetches a random image once and stores it here). To change a portrait: UPDATE ... SET IMAGE_URL = '<url>' WHERE JOB = '<job key>'.
--  2) Achievement ACH_ID 599 "legend begin" (type LEGEND_BEGIN, param 2): owning two star-5 companions at limit-break +6 unlocks the star-7 summon.
--     Korean text as CP949 hex per CLAUDE.md policy. ASCII-only file, safe to re-run.
-- ============================================================
DECLARE
    v_cnt NUMBER;
    v_name VARCHAR2(100) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0FCBCB3C0C720BDC3C0DB'));
    v_desc VARCHAR2(200) := UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C7D1B0E8B5B9C6C4202B36C0CE2035BCBA20B5BFB7E12032C3BCB8A620BFCFBCBAC7DFB4D9202837BCBA20B5BFB7E120BCD2C8AF20B0A1B4C929'));
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_STAR7_PORTRAIT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE TBOT_S5_STAR7_PORTRAIT (JOB VARCHAR2(10) PRIMARY KEY, IMAGE_URL VARCHAR2(500) NOT NULL, INSERT_DATE DATE DEFAULT SYSDATE NOT NULL)';
    END IF;
    SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_ACHIEVEMENT WHERE ACH_ID = 599;
    IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_ACHIEVEMENT (ACH_ID, ACH_NAME, ACH_DESC, ACH_TYPE, ACH_PARAM, HIDDEN_YN, REWARD_TYPE, REWARD_VALUE)
        VALUES (599, v_name, v_desc, 'LEGEND_BEGIN', '2', 'N', NULL, NULL);
    END IF;
    COMMIT;
END;
/

SELECT ACH_ID, ACH_TYPE, ACH_PARAM, RAWTOHEX(UTL_RAW.CAST_TO_RAW(ACH_NAME)) AS NAME_HEX FROM TBOT_S5_ACHIEVEMENT WHERE ACH_ID = 599;
SELECT COUNT(*) AS PORTRAIT_ROWS FROM TBOT_S5_STAR7_PORTRAIT;
EXIT;
