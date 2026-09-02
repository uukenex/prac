-- ============================================================
-- Season 5 - server config table
-- Loaded into memory (static fields) by BotS5ServiceImpl.@PostConstruct at
-- server startup, and re-read via the /갱신 chat command afterwards so values
-- can change without a redeploy.
-- (Comments/memo kept in English on purpose -- Korean literals in this DB's
-- charset (KO16MSWIN949) have corrupted before when written via sqlplus from
-- this environment; see CLAUDE.md "DB 스크립트 실행" section.)
-- ============================================================
CREATE TABLE TBOT_S5_CONFIG (
    CONFIG_KEY      VARCHAR2(50)  NOT NULL,
    CONFIG_VALUE    VARCHAR2(200) NOT NULL,
    MEMO            VARCHAR2(200),
    UPDATE_DATE     DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_TBOT_S5_CONFIG PRIMARY KEY (CONFIG_KEY)
);

COMMENT ON TABLE  TBOT_S5_CONFIG              IS 'Season5 server config (loaded into memory at boot, refreshed via /gaengsin)';
COMMENT ON COLUMN TBOT_S5_CONFIG.CONFIG_KEY   IS 'config key, e.g. MOVE_COOLDOWN_SEC';
COMMENT ON COLUMN TBOT_S5_CONFIG.CONFIG_VALUE IS 'config value stored as string, parsed by the reader';

INSERT INTO TBOT_S5_CONFIG (CONFIG_KEY, CONFIG_VALUE, MEMO)
VALUES ('MOVE_COOLDOWN_SEC', '180', 'move (non-combat dice) cooldown seconds, default 180 = 3min');
INSERT INTO TBOT_S5_CONFIG (CONFIG_KEY, CONFIG_VALUE, MEMO)
VALUES ('COMBAT_COOLDOWN_SEC', '30', 'combat (attack dice) cooldown seconds, default 30');

COMMIT;

SELECT * FROM TBOT_S5_CONFIG ORDER BY CONFIG_KEY;
EXIT;
