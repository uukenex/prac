-- ============================================================
-- Season 5 patch 2026-10-22 -- POST-DEPLOY ONLY. Run this ONLY AFTER the QTY-aware code (S5 patch 2026-10-22) is deployed.
-- (If the old code is still running it would read a merged row as a single item and the rest would effectively vanish.)
-- Merges unequipped non-legendary items of the same (USER_NAME, CLASS, PART, GRADE) into the row with the smallest EQUIP_ID
-- and sets its QTY to the total. Each group is locked (FOR UPDATE), recounted under the lock and committed on its own, so items
-- added or equipped by the live game at the same moment are not lost or double counted (they wait for the lock).
-- Safe to re-run (groups that are already single rows are skipped).
-- ASCII-only file.
-- ============================================================
SET SERVEROUTPUT ON
DECLARE
    TYPE t_key IS RECORD (USER_NAME VARCHAR2(100), CLASS VARCHAR2(100), PART VARCHAR2(100), GRADE NUMBER);
    TYPE t_keys IS TABLE OF t_key;
    v_keys   t_keys;
    v_keep   NUMBER;
    v_total  NUMBER;
    v_groups NUMBER := 0;
    v_rows   NUMBER := 0;
    CURSOR c_lock(p_user VARCHAR2, p_class VARCHAR2, p_part VARCHAR2, p_grade NUMBER) IS
        SELECT EQUIP_ID FROM TBOT_S5_USER_EQUIP
         WHERE USER_NAME = p_user AND CLASS = p_class AND PART = p_part AND GRADE = p_grade
           AND EQUIPPED_COMPANION_ID IS NULL AND LEGENDARY_ID IS NULL
           FOR UPDATE;
BEGIN
    SELECT USER_NAME, CLASS, PART, GRADE BULK COLLECT INTO v_keys
      FROM TBOT_S5_USER_EQUIP
     WHERE EQUIPPED_COMPANION_ID IS NULL AND LEGENDARY_ID IS NULL
     GROUP BY USER_NAME, CLASS, PART, GRADE
    HAVING COUNT(*) > 1;

    FOR i IN 1 .. v_keys.COUNT LOOP
        FOR r IN c_lock(v_keys(i).USER_NAME, v_keys(i).CLASS, v_keys(i).PART, v_keys(i).GRADE) LOOP
            NULL; -- just take the row locks
        END LOOP;
        SELECT MIN(EQUIP_ID), SUM(NVL(QTY, 1)) INTO v_keep, v_total
          FROM TBOT_S5_USER_EQUIP
         WHERE USER_NAME = v_keys(i).USER_NAME AND CLASS = v_keys(i).CLASS AND PART = v_keys(i).PART AND GRADE = v_keys(i).GRADE
           AND EQUIPPED_COMPANION_ID IS NULL AND LEGENDARY_ID IS NULL;
        IF v_keep IS NOT NULL THEN
            UPDATE TBOT_S5_USER_EQUIP SET QTY = v_total WHERE EQUIP_ID = v_keep;
            DELETE FROM TBOT_S5_USER_EQUIP
             WHERE USER_NAME = v_keys(i).USER_NAME AND CLASS = v_keys(i).CLASS AND PART = v_keys(i).PART AND GRADE = v_keys(i).GRADE
               AND EQUIPPED_COMPANION_ID IS NULL AND LEGENDARY_ID IS NULL AND EQUIP_ID <> v_keep;
            v_rows := v_rows + SQL%ROWCOUNT;
            v_groups := v_groups + 1;
        END IF;
        COMMIT;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('merged groups=' || v_groups || ', removed rows=' || v_rows);
END;
/

-- verification: no duplicate unequipped non-legendary groups remain, and unit totals are unchanged (compare with the pre-run number)
SELECT COUNT(*) AS DUP_GROUPS_LEFT FROM (
    SELECT 1 FROM TBOT_S5_USER_EQUIP WHERE EQUIPPED_COMPANION_ID IS NULL AND LEGENDARY_ID IS NULL
     GROUP BY USER_NAME, CLASS, PART, GRADE HAVING COUNT(*) > 1);
SELECT COUNT(*) AS ROWS_NOW, SUM(QTY) AS UNITS_NOW FROM TBOT_S5_USER_EQUIP;
SELECT COUNT(*) AS BAD_EQUIPPED_OR_LEGEND_QTY FROM TBOT_S5_USER_EQUIP WHERE QTY <> 1 AND (EQUIPPED_COMPANION_ID IS NOT NULL OR LEGENDARY_ID IS NOT NULL);
EXIT;
