# Season 4 - 낚시 시스템 설계서

## 개요

- 컨트롤러: `Season4Controller.java` (BossAttack 계열과 완전 분리)
- Mapper: `Season4Mapper.xml`
- Service: `Season4Service.java` / `Season4ServiceImpl.java`
- 공유 테이블: `TBOT_WORD_HIS` 만 중복 (loachat에서 처리, 문제없음)
- 초기 구현 범위: ★1~★3만 활성 (★4~★8은 DB에 등록, ACTIVE_YN='0')

---

## 명령어

| 명령어 | 기능 |
|---|---|
| `/낚시` | 하루 1회 낚시 실행 (출첵 개념) |
| `/낚시가방` | 보유 물고기 목록 + 업적 현황 조회 |
| `/낚시구매` | 낚시대/찌 업그레이드 목록 (해금 여부 표시) |

---

## DB 테이블

### TBOT_S4_FISH_INFO (물고기 마스터)
```sql
CREATE TABLE TBOT_S4_FISH_INFO (
    FISH_ID      NUMBER        PRIMARY KEY,
    FISH_NAME    VARCHAR2(50)  NOT NULL,
    FISH_GRADE   NUMBER(1)     NOT NULL,  -- 1~8
    WEIGHT       NUMBER(5,2)   NOT NULL,  -- 확률 가중치 (같은 등급 내 분배)
    ACTIVE_YN    CHAR(1)       DEFAULT '1',  -- 초기 ★4~★8은 '0'
    REG_DATE     DATE          DEFAULT SYSDATE
);
```

### TBOT_S4_FISH_INV (유저 물고기 인벤토리)
```sql
CREATE TABLE TBOT_S4_FISH_INV (
    USER_NAME        VARCHAR2(100) NOT NULL,
    FISH_ID          NUMBER        NOT NULL,
    QTY              NUMBER        DEFAULT 0,
    FIRST_CATCH_DATE DATE,
    PRIMARY KEY (USER_NAME, FISH_ID)
);
```

### TBOT_S4_FISHING_LOG (일별 낚시 기록 - 하루 1회 중복방지)
```sql
CREATE TABLE TBOT_S4_FISHING_LOG (
    USER_NAME    VARCHAR2(100) NOT NULL,
    FISHING_DATE DATE          NOT NULL,  -- TRUNC(SYSDATE)
    FISH_ID      NUMBER        NOT NULL,
    INSERT_DATE  DATE          DEFAULT SYSDATE,
    PRIMARY KEY (USER_NAME, FISHING_DATE)
);
```

### TBOT_S4_ACHIEVEMENT (업적 마스터)
```sql
CREATE TABLE TBOT_S4_ACHIEVEMENT (
    ACH_ID       NUMBER        PRIMARY KEY,
    ACH_NAME     VARCHAR2(100) NOT NULL,
    ACH_DESC     VARCHAR2(200),
    ACH_TYPE     VARCHAR2(50)  NOT NULL,  -- FIRST_CATCH_GRADE / ALL_KIND_GRADE
    ACH_PARAM    VARCHAR2(50),            -- 조건 파라미터 (등급 숫자 등)
    REWARD_TYPE  VARCHAR2(50),            -- ROD_UNLOCK / BOBBER_UNLOCK
    REWARD_GRADE NUMBER(1)                -- 해금되는 장비 등급
);
```

### TBOT_S4_USER_ACH (유저 업적 달성)
```sql
CREATE TABLE TBOT_S4_USER_ACH (
    USER_NAME  VARCHAR2(100) NOT NULL,
    ACH_ID     NUMBER        NOT NULL,
    CLEAR_DATE DATE          DEFAULT SYSDATE,
    PRIMARY KEY (USER_NAME, ACH_ID)
);
```

### TBOT_S4_USER_EQUIP (유저 장비)
```sql
CREATE TABLE TBOT_S4_USER_EQUIP (
    USER_NAME    VARCHAR2(100) PRIMARY KEY,
    ROD_GRADE    NUMBER(1)     DEFAULT 1,     -- 낚시대 등급 1~5
    BOBBER_GRADE NUMBER(1)     DEFAULT 1,     -- 찌 등급 1~5
    REG_DATE     DATE          DEFAULT SYSDATE
);
```

---

## 물고기 마스터 데이터 (INSERT)

```sql
-- ★1 (흔한 민물, ACTIVE_YN='1')
INSERT INTO TBOT_S4_FISH_INFO VALUES (101, '피라미',   1, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (102, '붕어',     1, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (103, '미꾸라지', 1, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (104, '송사리',   1, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (105, '참붕어',   1, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (106, '모래무지', 1, 1.0, '1', SYSDATE);

-- ★2 (낚시터/갯벌, ACTIVE_YN='1')
INSERT INTO TBOT_S4_FISH_INFO VALUES (201, '잉어',   2, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (202, '메기',   2, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (203, '빙어',   2, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (204, '가물치', 2, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (205, '바지락', 2, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (206, '홍합',   2, 1.0, '1', SYSDATE);

-- ★3 (청정계곡/연안, ACTIVE_YN='1')
INSERT INTO TBOT_S4_FISH_INFO VALUES (301, '은어', 3, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (302, '꺽지', 3, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (303, '연어', 3, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (304, '재첩', 3, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (305, '가리비', 3, 1.0, '1', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (306, '참게', 3, 1.0, '1', SYSDATE);

-- ★4 (배낚시 필요, ACTIVE_YN='0' - 비활성)
INSERT INTO TBOT_S4_FISH_INFO VALUES (401, '문어',   4, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (402, '오징어', 4, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (403, '소라',   4, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (404, '굴',     4, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (405, '고등어', 4, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (406, '전갱이', 4, 1.0, '0', SYSDATE);

-- ★5 (잠수/전문장비, ACTIVE_YN='0')
INSERT INTO TBOT_S4_FISH_INFO VALUES (501, '참돔', 5, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (502, '광어', 5, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (503, '전복', 5, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (504, '해삼', 5, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (505, '대게', 5, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (506, '낙지', 5, 1.0, '0', SYSDATE);

-- ★6 (원양/심해 입구, ACTIVE_YN='0')
INSERT INTO TBOT_S4_FISH_INFO VALUES (601, '방어',     6, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (602, '성게',     6, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (603, '킹크랩',   6, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (604, '갯장어',   6, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (605, '자연산굴', 6, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (606, '참가자미', 6, 1.0, '0', SYSDATE);

-- ★7 (원양어선급, ACTIVE_YN='0')
INSERT INTO TBOT_S4_FISH_INFO VALUES (701, '참다랑어', 7, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (702, '복어',     7, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (703, '바다가재', 7, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (704, '거북손',   7, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (705, '귀상어',   7, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (706, '개복치',   7, 1.0, '0', SYSDATE);

-- ★8 (현실 채집 거의 불가, ACTIVE_YN='0')
INSERT INTO TBOT_S4_FISH_INFO VALUES (801, '대왕오징어',   8, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (802, '철갑상어',     8, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (803, '돗돔',         8, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (804, '심해아귀',     8, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (805, '나폴레옹피시', 8, 1.0, '0', SYSDATE);
INSERT INTO TBOT_S4_FISH_INFO VALUES (806, '쥐가오리',     8, 1.0, '0', SYSDATE);

COMMIT;
```

---

## 장비 시스템

### 낚시대 (최대 낚을 수 있는 등급 결정)

| 등급 | 이름 | 접근 가능 등급 | 해금 조건 |
|---|---|---|---|
| 1 | 일반낚시대 | ★1~★3 | 기본 지급 |
| 2 | 고급낚시대 | ★1~★5 | ★3 첫 획득 업적 |
| 3 | 희귀낚시대 | ★1~★6 | ★5 첫 획득 업적 |
| 4 | 영웅낚시대 | ★1~★7 | ★6 첫 획득 업적 |
| 5 | 전설낚시대 | ★1~★8 | ★7 첫 획득 업적 |

### 찌 (고등급 확률 보정)

| 등급 | 이름 | 효과 | 해금 조건 |
|---|---|---|---|
| 1 | 일반찌 | 기본 확률 | 기본 지급 |
| 2 | 고급찌 | 고등급 확률 소폭 상승 | ★1 전 종류(6종) 획득 |
| 3 | 희귀찌 | 고등급 확률 중폭 상승 | ★2 전 종류(6종) 획득 |
| 4 | 영웅찌 | 고등급 확률 대폭 상승 | ★3 전 종류(6종) 획득 |

---

## 확률 구조

- 낚시대 등급 → 접근 가능한 최대 ★ 결정
- 찌 등급 → 접근 가능한 범위 내에서 고등급 쪽으로 확률 보정
- 같은 등급 내 어종은 WEIGHT 기반 균등 분배 (기본값 1.0)

### 기본 확률 (일반낚시대 + 일반찌, ★1~★3 범위)

| 등급 | 확률 |
|---|---|
| ★1 | 70% |
| ★2 | 25% |
| ★3 | 5% |

---

## 업적 마스터 데이터

```sql
-- ACH_TYPE: FIRST_CATCH_GRADE = 특정 등급 첫 획득
-- ACH_TYPE: ALL_KIND_GRADE    = 특정 등급 종류 전부 획득
-- REWARD_TYPE: ROD_UNLOCK     = 낚시대 해금
-- REWARD_TYPE: BOBBER_UNLOCK  = 찌 해금

INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (1, '★3 첫 획득!',        '★3 등급 물고기를 처음 낚았다!',      'FIRST_CATCH_GRADE', '3', 'ROD_UNLOCK',    2);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (2, '★1 도감 완성!',      '★1 물고기 전 종류를 획득했다!',      'ALL_KIND_GRADE',    '1', 'BOBBER_UNLOCK', 2);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (3, '★2 도감 완성!',      '★2 물고기 전 종류를 획득했다!',      'ALL_KIND_GRADE',    '2', 'BOBBER_UNLOCK', 3);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (4, '★3 도감 완성!',      '★3 물고기 전 종류를 획득했다!',      'ALL_KIND_GRADE',    '3', 'BOBBER_UNLOCK', 4);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (5, '★5 첫 획득!',        '★5 등급 물고기를 처음 낚았다!',      'FIRST_CATCH_GRADE', '5', 'ROD_UNLOCK',    3);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (6, '★6 첫 획득!',        '★6 등급 물고기를 처음 낚았다!',      'FIRST_CATCH_GRADE', '6', 'ROD_UNLOCK',    4);
INSERT INTO TBOT_S4_ACHIEVEMENT VALUES (7, '★7 첫 획득!',        '★7 등급 물고기를 처음 낚았다!',      'FIRST_CATCH_GRADE', '7', 'ROD_UNLOCK',    5);

COMMIT;
```

---

## 신규 유저 초기화

계정 생성 시 `TBOT_S4_USER_EQUIP`에 ROD_GRADE=1, BOBBER_GRADE=1 INSERT

---

## 구현 순서 (추후 작업 시)

1. DDL 실행 (테이블 6개 생성)
2. 마스터 데이터 INSERT (물고기 48종 + 업적 7개)
3. `Season4Mapper.xml` 작성
4. `Season4Service.java` / `Season4ServiceImpl.java` 작성
5. `Season4Controller.java` 작성
   - `/낚시` → 하루 1회 체크 → 등급 추첨 → 어종 추첨 → 인벤 적재 → 업적 체크
   - `/낚시가방` → 보유 어종 목록 + 업적 현황
   - `/낚시구매` → 장비 목록 + 해금/미해금 표시
