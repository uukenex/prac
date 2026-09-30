<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 로그인</title>
  <%-- [2026-09-30] 시즌5 로그/몬스터정보/밸런스통계 페이지 접근용 카카오 로그인 화면(Season5KakaoLoginController). --%>
  <style>
    :root{ --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82; --line:#E5D3A1; --gold:#C68A2E; }
    *,*::before,*::after{box-sizing:border-box;}
    body{margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center; padding:16px;
         background:radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
         color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;}
    .card{width:100%; max-width:360px; text-align:center; background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep));
          border:2px solid var(--line); border-radius:18px; padding:34px 26px 28px; box-shadow:0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);}
    .ico{font-size:46px;} h1{font-size:19px; margin:8px 0 4px;} .sub{font-size:12.5px; color:var(--ink-soft); margin-bottom:24px; line-height:1.6;}
    .kakao{display:flex; align-items:center; justify-content:center; gap:8px; background:#FEE500; color:#191919; border-radius:12px;
           padding:13px; font-size:15px; font-weight:800; text-decoration:none;}
    .kakao:hover{opacity:.9;}
    .err{margin-top:14px; font-size:12.5px; color:#A31F2B;}
    .note{margin-top:18px; font-size:11px; color:var(--ink-soft);}
  </style>
</head>
<body>
<div class="card">
  <div class="ico">🗼</div>
  <h1>람쥐탑 관리자 페이지</h1>
  <div class="sub">로그 뷰어 · 몬스터 정보 · 밸런스 통계는<br>카카오 로그인 후 볼 수 있습니다.</div>
  <a class="kakao" href="<%=request.getContextPath()%>/loa/s5/kakao/auth">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="#191919"><path d="M12 3C6.477 3 2 6.477 2 11c0 2.96 1.68 5.55 4.2 7.1L5.1 21.9a.5.5 0 0 0 .72.56L9.7 19.9A11.3 11.3 0 0 0 12 20c5.523 0 10-3.477 10-8S17.523 3 12 3z"/></svg>
    카카오로 로그인
  </a>
  <%
    String err = String.valueOf(request.getAttribute("err"));
    if ("cancel".equals(err)) { %><div class="err">로그인이 취소되었습니다.</div>
  <% } else if ("token".equals(err)) { %><div class="err">인증에 실패했습니다. 다시 시도해주세요.</div>
  <% } else if ("state".equals(err)) { %><div class="err">로그인 세션이 만료되었습니다. 다시 시도해주세요.</div><% } %>
  <div class="note">카카오 고유번호와 닉네임만 저장됩니다.</div>
</div>
</body>
</html>
