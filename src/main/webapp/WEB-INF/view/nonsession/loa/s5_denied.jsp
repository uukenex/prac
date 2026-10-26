<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>관리자 전용</title>
  <!-- [2026-10-28] "로그 뷰는 관리자만" -- 카카오 로그인은 했지만 관리자가 아닐 때(Season5ViewController.isLogAdmin). 로그 뷰어와 같은 양피지 톤. -->
  <style>
    :root{ --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82; --line:#E5D3A1; --gold:#C68A2E; }
    body{ margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center; padding:16px; box-sizing:border-box;
          background:radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
          color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif; }
    .card{ max-width:360px; width:100%; text-align:center; background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep));
           border:2px solid var(--line); border-radius:18px; padding:24px 20px; box-shadow:0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35); }
    h1{ font-size:18px; margin:6px 0 8px; }
    p{ font-size:13px; color:var(--ink-soft); margin:0 0 16px; line-height:1.6; }
    a{ display:inline-block; background:var(--gold); color:#fff; text-decoration:none; font-weight:700; font-size:13px; padding:9px 18px; border-radius:14px; }
  </style>
</head>
<body>
  <div class="card">
    <div style="font-size:34px">🔒</div>
    <h1>관리자만 볼 수 있어요</h1>
    <p>로그 뷰어와 광산 캐릭터 현황은 관리자 계정으로만 열려요.<br>다른 카카오 계정으로 로그인했다면 로그아웃한 뒤 다시 로그인해 주세요.</p>
    <a href="/loa/s5/logout">로그아웃</a>
  </div>
</body>
</html>
