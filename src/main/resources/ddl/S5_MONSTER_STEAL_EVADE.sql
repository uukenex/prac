-- ============================================================
-- Season 5 - dual-monster retaliation bug fix (no schema change) +
-- rogue-skill-steal rework (PP steal -> evasion steal).
-- (2026-09-18 request): "몬스터 I, II가 나왔을때 I번 몬스터 처치 후에도
-- 두마리의 몬스터가 둘다 공격하는 문제가있어" / "몬스터가 도적스킬 뺏을때
-- pp뺏는걸 없애주고 회피하는걸 뺏어줘"
--
-- 1) Dual-monster bug: fixed in code only (BotS5ServiceImpl) -- when monster
--    I dies and II is promoted to the sole monster, CUR_MONSTER_DUAL_YN was
--    never reset to 'N', so every later turn kept treating the fight as
--    "2 monsters" and always retaliated with both slots even though only
--    the promoted survivor was real. No DDL needed for this part.
--
-- 2) Rogue-skill-steal rework: midboss/boss stealing the party's ROGUE
--    ability used to drain player PP; now it instead grants the monster a
--    chance to fully evade (no damage) the party's NEXT attack, mirroring
--    the existing 79/89층 "boss79Ambush" 1-turn-evade mechanic. Needs one
--    new counter column, following the exact MONSTER_DEF_BUFF_PCT /
--    MONSTER_SHIELD_VALUE pattern (set when stolen, read+reset next turn).
-- ============================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'MONSTER_EVADE_PCT';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TBOT_S5_USER_PROGRESS ADD (MONSTER_EVADE_PCT NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

COMMIT;

-- Verification
SELECT COLUMN_NAME, DATA_TYPE, DATA_DEFAULT FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'TBOT_S5_USER_PROGRESS' AND COLUMN_NAME = 'MONSTER_EVADE_PCT';
