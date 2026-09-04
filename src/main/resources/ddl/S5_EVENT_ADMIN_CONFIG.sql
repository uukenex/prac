-- ============================================================
-- Season 5 - /이벤트지급(관리자 전용 이벤트 뽑기권 지급 명령어) 관리자 목록 설정.
--
-- 재사용 템플릿: 관리자를 추가/교체할 때마다 이 파일의 "관리자 목록" 서브쿼리만 고쳐서
-- 다시 실행하면 됨(MERGE라 몇 번을 다시 돌려도 안전 -- 항상 이 파일에 적힌 목록으로 덮어씀).
--
-- TBOT_S5_CONFIG.EVENT_ADMIN_USERS는 '|'로 구분된 유저명 목록이고, 비어있으면(기본값)
-- /이벤트지급은 아무도 실행할 수 없다(BotS5ServiceImpl.isEventAdmin 참고) -- 뽑기권처럼
-- 실제 경제가치가 있는 걸 아무나 채팅으로 못 뿌리게 하는 유일한 방어 장치라 신중히 관리할 것.
--
-- 최초 활성화를 위해 이번 세션 내내 테스트/관리 계정으로 써온 "일어난다람쥐/카단" 1명만
-- 등록해둠 -- 다른 관리자가 더 필요하면 아래 서브쿼리에 UNION ALL로 한 줄씩 추가(닉네임의
-- CP949 hex는 CLAUDE.md에 있는 방법대로 계산):
--   PowerShell> $enc = [System.Text.Encoding]::GetEncoding(949)
--   PowerShell> -join ($enc.GetBytes('닉네임') | ForEach-Object { $_.ToString('X2') })
--
-- 적용 후 실제로 반영되려면 서버가 이 값을 다시 읽어야 한다 -- 앱 재기동 또는 라이브
-- 채팅에서 /갱신 한 번 실행(BotS5ServiceImpl.loadConfig()가 EVENT_ADMIN_USERS도 같이 읽음).
-- ============================================================

MERGE INTO TBOT_S5_CONFIG T
USING (
    SELECT 'EVENT_ADMIN_USERS' AS K,
           LISTAGG(NM, '|') WITHIN GROUP (ORDER BY NM) AS V
    FROM (
        -- 관리자 목록 (한 명당 한 줄, CP949 hex) -- 여기에 UNION ALL로 계속 추가 가능
        SELECT UTL_RAW.CAST_TO_VARCHAR2(HEXTORAW('C0CFBEEEB3ADB4D9B6F7C1E32FC4ABB4DC')) AS NM FROM DUAL
    )
) S
ON (T.CONFIG_KEY = S.K)
WHEN MATCHED THEN
    UPDATE SET CONFIG_VALUE = S.V
WHEN NOT MATCHED THEN
    INSERT (CONFIG_KEY, CONFIG_VALUE, MEMO)
    VALUES (S.K, S.V, 'Pipe-separated user names allowed to run the /event voucher grant command');

COMMIT;

-- 검증: 저장된 값이 실제로 의도한 닉네임과 바이트 단위로 일치하는지 확인(sqlplus 왕복만 믿지 말 것)
SELECT CONFIG_KEY, CONFIG_VALUE, RAWTOHEX(UTL_RAW.CAST_TO_RAW(CONFIG_VALUE)) AS VALUE_HEX
FROM TBOT_S5_CONFIG WHERE CONFIG_KEY = 'EVENT_ADMIN_USERS';

EXIT;
