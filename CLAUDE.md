# 작업 규칙

## Git
- master 직접 push는 사용자가 처리. 단, 브랜치 커밋을 master로 cherry-pick 후 push하는 것은 허용.
- 브랜치 작업 중 파일 수정은 권한 확인 없이 바로 처리.
- 커밋 요청 시 push까지 자동으로 처리. PR 링크도 함께 제공.

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

#테스트