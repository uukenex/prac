-- Global dice-face roll histogram for /towerStats(/tapTonggye).
-- Rows are pre-seeded for face values 1..20 (covers DICE_6..DICE_20) so
-- bumpDiceFaceStat() only ever needs a plain UPDATE, never an insert/merge.
-- ASCII-only file (no Korean literals), safe to run with any NLS_LANG.

DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'TBOT_S5_DICE_STATS';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE '
      CREATE TABLE TBOT_S5_DICE_STATS (
        FACE_VALUE NUMBER(2) NOT NULL,
        ROLL_COUNT NUMBER DEFAULT 0 NOT NULL,
        CONSTRAINT PK_S5_DICE_STATS PRIMARY KEY (FACE_VALUE)
      )';
  END IF;
END;
/

BEGIN
  FOR i IN 1..20 LOOP
    DECLARE
      v_cnt NUMBER;
    BEGIN
      SELECT COUNT(*) INTO v_cnt FROM TBOT_S5_DICE_STATS WHERE FACE_VALUE = i;
      IF v_cnt = 0 THEN
        INSERT INTO TBOT_S5_DICE_STATS (FACE_VALUE, ROLL_COUNT) VALUES (i, 0);
      END IF;
    END;
  END LOOP;
  COMMIT;
END;
/

COMMENT ON TABLE TBOT_S5_DICE_STATS IS 'Season5 global dice-face roll counters (1..20), used by /towerStats';
COMMENT ON COLUMN TBOT_S5_DICE_STATS.FACE_VALUE IS 'Rolled face value (1..20, covers DICE_6/8/10/12/20)';
COMMENT ON COLUMN TBOT_S5_DICE_STATS.ROLL_COUNT IS 'How many times this face has come up across all rolls (movement/attack/shield/retaliation)';
