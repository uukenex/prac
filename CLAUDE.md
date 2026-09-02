# 작업 규칙

## Git
- **2026-09-03부터: 앞으로는 master에서 바로 작업.** 별도 feature 브랜치 안 만들고 master에서 직접 수정 → 커밋 → push까지 진행.
- 파일 수정은 권한 확인 없이 바로 처리.
- 커밋 요청 시 push까지 자동으로 처리. PR 링크도 함께 제공(브랜치 작업 시).

## 앱 실행 구조
Spring Boot 웹앱. 채팅 명령어는 아래 형태로 HTTP GET 요청이 들어옴:

```
GET /loa/chat
  ?param0=/{명령어}
  &sender={유저명}/{방이름}
  &fulltxt=/{전체입력}
  &room={방이름}
  &param1=
  &param2=
```

예시:
- `param0` → `/ㄱ` (명령어, 인코딩됨)
- `sender` → `일어난다람쥐/카단` (유저명/캐릭터명)
- `room` → `람쥐봇 문의방`

## DB 스크립트 실행 (sqlplus)
- DB 문자셋은 `KO16MSWIN949`(CP949)인데, 이 저장소에서 만드는 `.sql` 파일은 UTF-8로 저장됨.
  `export NLS_LANG=KOREAN_KOREA.AL32UTF8` 후 sqlplus로 실행해도 **완전히 안전하지 않음** —
  2026-09-02에 이렇게 실행하고 sqlplus로 재조회했을 땐 한글이 정상으로 "보였지만", 실제
  라이브 봇(JDBC)에서는 그 값이 깨져서 나왔음(git-bash↔sqlplus.exe↔Windows 콘솔 코드페이지를
  거치는 동안 어딘가에서 재변환됨 — sqlplus 자체 조회 결과는 같은 경로로 왕복하니 겉보기엔
  맞는 것처럼 보이는 함정이 있었음).
- **한글 리터럴이 들어간 INSERT/UPDATE를 sqlplus로 DB에 반영할 땐, 한글을 SQL 파일에 직접
  쓰지 말고 CP949(=KO16MSWIN949) 바이트를 HEXTORAW로 넣는 방식을 쓸 것** (ASCII만 있는
  파일이라 인코딩 경로 자체가 개입할 여지가 없음):
  ```powershell
  $enc = [System.Text.Encoding]::GetEncoding(949)
  $hex = -join ($enc.GetBytes('한글문자열') | ForEach-Object { $_.ToString('X2') })
  # → UPDATE tbl SET col = UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('<hex>')) WHERE ...
  ```
  적용 후엔 `RAWTOHEX(UTL_RAW.CAST_TO_RAW(col))`로 저장된 바이트가 의도한 hex와 정확히
  일치하는지 확인하고(터미널 렌더링에 의존하지 않는 방식), **가능하면 실제 앱(JDBC) 쪽
  출력으로도 한 번 더 확인**할 것 — sqlplus 왕복 결과만 믿지 말 것.
  참고 예시: `src/main/resources/ddl/S5_MONSTER_RENAME_HEX.sql`
- 조회(SELECT)만 할 땐 `export NLS_LANG=KOREAN_KOREA.AL32UTF8` 정도로 충분.

#테스트