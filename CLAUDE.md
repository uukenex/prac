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

라이브 서버: `http://rgb-tns.dev-apc.com` (예: `http://rgb-tns.dev-apc.com/loa/chat?...`, 웹뷰는 `/loa/tower-view?userName=...`).

## 라이브 `/loa/chat`을 curl로 직접 테스트하기
- **git-bash에서 curl로 `param0=/이미지갱신` 같은 값을 보내면 MSYS가 "/"로 시작하는 부분을
  Windows 경로로 멋대로 변환해버림**(예: `/이미지갱신` → `C:/Program Files/Git/이미지갱신`,
  한글도 그 과정에서 깨짐). `--data-urlencode`를 써도 안의 값이 "/"를 포함하면 걸림.
  → **한글 명령어는 미리 UTF-8 percent-encoding으로 직접 만들어서, "http://"로 시작하는
  완성된 URL 문자열 하나를 curl에 넘길 것** (이러면 MSYS가 경로로 오인 안 함):
  ```powershell
  # PowerShell에서 UTF-8 hex 계산
  [System.Text.Encoding]::UTF8.GetBytes("/이미지갱신") | ForEach-Object { "%{0:X2}" -f $_ } | Out-String
  ```
  ```bash
  # bash에서는 이렇게 완성된 URL 하나로 호출 (param0=/xxx 를 별도 인자로 안 넘김)
  curl "http://rgb-tns.dev-apc.com/loa/chat?param0=%2F...&sender=BG&room=t&fulltxt=%2F..."
  ```
  이거 안 하면 서버가 명령어를 못 알아듣고 응답이 빈 문자열로 옴(에러 없이 조용히 실패라
  더 헷갈림 — 2026-09-03에 이걸로 한참 헤맴).

## nekos.best(동료 초상화 API) 관련
- API(`https://nekos.best/api/v2/neko`)는 **User-Agent 헤더가 없으면 403** — `"APP_NAME
  (CONTACT_INFO)"` 형식 필요(문서: https://docs.nekos.best/getting-started/api-reference.html#user-agent).
  `BotS5ServiceImpl.fetchRandomNekoImage()`에 `"RgbTowerBot/1.0 (https://rgb-tns.dev-apc.com)"`
  헤더를 달아서 고쳐둠(2026-09-03).
- **API가 돌려주는 이미지 CDN 경로 자체는 Cloudflare JS 챌린지("Just a moment...")가 걸려있어서
  curl 등 스크립트로는 이미지 바이트를 못 받아옴**(User-Agent를 정상 브라우저 값으로 바꿔도
  동일). 실제 유저 브라우저의 `<img>` 태그 렌더링이 되는지는 아직 실브라우저로 확인 못 했음
  — 안 보인다는 얘기 나오면 이 챌린지가 원인일 가능성부터 볼 것.

## DB 스크립트 실행 (sqlplus)
- **DB 접속 정보(계정/비번/host:port/SERVICE_NAME)는 절대 기억해두거나 재사용하지 말고,
  DB 작업이 필요할 때마다 매번 사용자에게 물어볼 것**(2026-09-04 명시적 지시). 이전 대화에서
  받은 접속정보라도 세션이 끝나면(혹은 컴팩션되면) 잊은 것으로 간주하고 다시 요청.
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
- **이모지(surrogate pair, 4바이트 UTF-8)를 sqlplus로 직접 INSERT해서 왕복 테스트하면 그
  결과를 믿지 말 것** — 위 한글 CP949 함정과 같은 경로(git-bash↔sqlplus.exe↔Windows 콘솔
  코드페이지)에서 이모지도 깨진다(2026-09-05에 `NVARCHAR2`/`NCLOB` 컬럼으로 이미 바꿔둔
  컬럼에 이모지를 직접 INSERT했는데도 sqlplus에서 넣고 바로 조회하면 `?`/`？`로 깨져서 보여서
  "고친 게 안 먹히나?" 헷갈렸음 — 실제로는 컬럼은 정상, 터미널 왕복 경로만 문제였음).
  → **이모지가 실제로 잘 들어가는지 확인할 땐 `UNISTR('\D83C\DFB2')`처럼 UTF-16 서로게이트
  페어를 코드값(순수 ASCII 이스케이프)으로 직접 써서 INSERT하고, 조회도 `ASCIISTR(col)`로
  받아서(터미널에 이모지 글자 자체를 안 띄우고 `\D83C\DFB2` 같은 이스케이프 문자열로) 비교할
  것** — 이러면 스크립트 파일 자체는 ASCII만 있어서 인코딩 경로가 전혀 개입 안 함.
  참고 예시: `src/main/resources/ddl/S5_WORD_HIS_EMOJI_FIX.sql`(마이그레이션),
  그 마이그레이션 검증 시 실제로 쓴 쿼리 -- `INSERT INTO t (REQ) VALUES
  (UNISTR('test\D83C\DFB2dice')); SELECT ASCIISTR(REQ) FROM t;`.

#테스트