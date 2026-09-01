-- ============================================================
-- Season 5 - 탑 등반 시스템 : 마스터 데이터
-- S5_DDL.sql 실행 후 적용
-- ============================================================

-- ============================================================
-- 1) 층별 보드 칸 수 + 고정 보드 배치 (사냥터층: FLOOR MOD 10 IN 1..8)
--    칸수 5~13 랜덤, 칸종류 분포: 전투45% / PP20% / 상점10% / 함정10% / 특수15%
-- ============================================================
DECLARE
    v_tile_count NUMBER;
    v_rand       NUMBER;
    v_type       VARCHAR2(10);
BEGIN
    FOR f IN 1..100 LOOP
        IF MOD(f, 10) BETWEEN 1 AND 8 THEN
            v_tile_count := TRUNC(DBMS_RANDOM.VALUE(5, 14)); -- 5~13

            INSERT INTO TBOT_S5_FLOOR_INFO (FLOOR, TILE_COUNT)
            VALUES (f, v_tile_count);

            FOR t IN 1..v_tile_count LOOP
                v_rand := DBMS_RANDOM.VALUE(0, 100);
                IF    v_rand < 45 THEN v_type := 'COMBAT';
                ELSIF v_rand < 65 THEN v_type := 'PP';
                ELSIF v_rand < 75 THEN v_type := 'SHOP';
                ELSIF v_rand < 85 THEN v_type := 'TRAP';
                ELSE                    v_type := 'SPECIAL';
                END IF;

                INSERT INTO TBOT_S5_TILE_MASTER (FLOOR, TILE_NO, TILE_TYPE)
                VALUES (f, t, v_type);
            END LOOP;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- ============================================================
-- 2) 몬스터 마스터 (구간별 일반 1 + 보스 1)
--    41층 이후 일반 몬스터 수치는 원본 그대로(재검증 필요), 보스는 전부 잠정치
--    (HP×6 / ATK×2.2 / DEF×1.5 비율로 산정, S5_TOWER_DESIGN.md 참고)
-- ============================================================
-- 일반 몬스터
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (101, 1, '하수구 뿔쥐',       120,     '', 12,    '', 3,    '', 1,    '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (102, 2, '버려진 광산 좀비',   350,     '', 28,    '', 8,    '', 3,    '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (103, 3, '통곡의 늪지 악어',   900,     '', 15,    '', 8,    '', 7,    '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (104, 4, '붉은 화산 가고일',   2400,    '', 26,    '', 15,   '', 15,   '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (105, 5, '얼어붙은 요새 설인', 6800,    '', 160,   '', 90,   '', 30,   '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (106, 6, '심연의 기사단 망령', 22000,   '', 350,   '', 210,  '', 70,   '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (107, 7, '뒤틀린 차원의 괴수', 58000,   '', 620,   '', 450,  '', 150,  '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (108, 8, '천공의 성 가디언',   160000,  '', 1200,  '', 1000, '', 300,  '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (109, 9, '용의 둥지 새끼용',   450000,  '', 2400,  '', 2200, '', 600,  '', 'N');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (110,10, '파멸의 균열 마룡',   1200000, '', 5000,  '', 5000, '', 1200, '', 'N');

-- 보스 (잠정치)
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (201, 1, '9층 보스',   720,     '', 26,    '', 5,    '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (202, 2, '19층 보스',  2100,    '', 62,    '', 12,   '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (203, 3, '29층 보스',  5400,    '', 33,    '', 12,   '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (204, 4, '39층 보스',  14400,   '', 57,    '', 23,   '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (205, 5, '49층 보스',  40800,   '', 352,   '', 135,  '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (206, 6, '59층 보스',  132000,  '', 770,   '', 315,  '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (207, 7, '69층 보스',  348000,  '', 1364,  '', 675,  '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (208, 8, '79층 보스',  960000,  '', 2640,  '', 1500, '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (209, 9, '89층 보스',  2700000, '', 5280,  '', 3300, '', 0, '', 'Y');
INSERT INTO TBOT_S5_MONSTER_INFO VALUES (210,10, '99층 보스',  7200000, '', 11000, '', 7500, '', 0, '', 'Y');

COMMIT;

-- ============================================================
-- 3) 가챠 마스터 (동료 계약서 4 + 장비 보물상자 4)
-- ============================================================
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (1, 'COMPANION', '하급 동료 계약서',   0,  100,   '', 70, 24, 5,  0.9, 0.1, 0);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (2, 'COMPANION', '중급 동료 계약서',   30, 1500,  '', 0,  65, 25, 8,   1.8, 0.2);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (3, 'COMPANION', '상급 동료 계약서',   60, 12000, '', 0,  0,  55, 33,  10,  2.0);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (4, 'COMPANION', '최상급 동료 계약서', 80, 50000, '', 0,  0,  0,  60,  32,  8.0);

INSERT INTO TBOT_S5_GACHA_MASTER VALUES (5, 'EQUIP', '낡은 장비 상자',   0,  60,    '', 75, 20, 4.5, 0.5, 0,  0);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (6, 'EQUIP', '쓸만한 장비 상자', 30, 900,   '', 0,  70, 22,  7,   1,  0);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (7, 'EQUIP', '빛나는 장비 상자', 60, 8000,  '', 0,  0,  60,  30,  8,  2.0);
INSERT INTO TBOT_S5_GACHA_MASTER VALUES (8, 'EQUIP', '전설의 장비 상자', 80, 35000, '', 0,  0,  0,   55,  35, 10.0);

COMMIT;

-- ============================================================
-- 4) 업적 마스터 (일반 16 + 히든 8, S5_TOWER_DESIGN.md 초안 그대로)
-- ============================================================
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (1,  '첫 걸음',          '탑 등반을 시작했다',                 'FLOOR_REACHED',        '1',   'N', 'PP',   '10');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (2,  '10층 마을 도착',    '첫 마을에 도착했다',                 'FLOOR_REACHED',        '10',  'N', 'PP',   '50');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (3,  '30층 마을 도착',    '중급 동료 계약서가 열렸다',           'FLOOR_REACHED',        '30',  'N', 'STAT_CAP_UP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (4,  '50층 마을 도착',    '상급 동료 계약서가 열렸다',           'FLOOR_REACHED',        '50',  'N', 'STAT_CAP_UP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (5,  '70층 마을 도착',    '최상급 동료 계약서가 열렸다',         'FLOOR_REACHED',        '70',  'N', 'STAT_CAP_UP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (6,  '탑의 정상',         '100층에 도달했다',                   'FLOOR_REACHED',        '100', 'N', 'TITLE', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (7,  '첫 보스 처치',      '9층 보스를 쓰러뜨렸다',               'BOSS_KILL',             '9',   'N', 'PP',   '100');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (8,  '몬스터 사냥꾼',     '몬스터 100마리를 처치했다',           'MONSTER_KILL_TOTAL',    '100', 'N', 'PP',   '200');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (9,  '몬스터 학살자',     '몬스터 1000마리를 처치했다',          'MONSTER_KILL_TOTAL',    '1000','N', 'GACHA_EQUIP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (10, '첫 동료 계약',      '첫 동료를 영입했다',                 'GACHA_PULL_COUNT',      '1',   'N', 'PP',   '30');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (11, '동료 수집가',       '동료 계약서를 50회 뽑았다',           'GACHA_PULL_COUNT',      '50',  'N', 'GACHA_COMPANION', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (12, '첫 장비 합성',      '장비를 처음 합성했다',               'EQUIP_SYNTHESIS',       '1',   'N', 'PP',   '30');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (13, '장인의 손길',       '장비를 30회 합성했다',               'EQUIP_SYNTHESIS',       '30',  'N', 'GACHA_EQUIP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (14, '자동사냥 입문',     '자동사냥으로 PP 1000을 모았다',       'AUTO_HUNT_PP_TOTAL',    '1000','N', 'PP',   '100');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (15, '파티 완전체',       '동료 3명으로 파티를 편성했다',        'PARTY_FULL',            '3',   'N', 'PP',   '50');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (16, '함정 생존왕',       '함정칸에서 20회 생존했다',            'TRAP_SURVIVE',          '20',  'N', 'PP',   '80');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (17, '???',              '탐험가의 감',                        'SPECIAL_TILE_VISIT',    '10',  'Y', 'STAT_CAP_UP', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (18, '???',              '길 잃은 자',                          'SPECIAL_TILE_VISIT',    '50',  'Y', 'GACHA_EQUIP_RARE', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (19, '???',              '운명의 방문자',                       'SPECIAL_TILE_VISIT',    '100', 'Y', 'GACHA_COMPANION_LEGEND', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (20, '???',              '무모한 도전',                         'TRAP_THEN_BOSS',        NULL,  'Y', 'TITLE', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (21, '???',              '한 우물',                             'AUTO_HUNT_FULL_5',      '5',   'Y', 'PP',   '2000');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (22, '???',              '도박사',                              'GACHA_GRADE6_HIT',      NULL,  'Y', 'TITLE', NULL);
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (23, '???',              '무패 행진',                           'BLOCK_CLEAR_NO_LOSE',   NULL,  'Y', 'PP',   '500');
INSERT INTO TBOT_S5_ACHIEVEMENT VALUES (24, '???',              '전 직업 마스터',                      'ALL_CLASS_PARTY',       NULL,  'Y', 'PP',   '500');

COMMIT;

EXIT;
