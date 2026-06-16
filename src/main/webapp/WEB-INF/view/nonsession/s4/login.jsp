<%@ page pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>람쥐봇 낚시 - 로그인</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    min-height: 100vh;
    background: linear-gradient(135deg, #0d1b2a 0%, #1a2f45 100%);
    display: flex; align-items: center; justify-content: center;
    font-family: 'Noto Sans KR', sans-serif;
  }
  .card {
    background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 20px;
    padding: 60px 48px;
    text-align: center;
    width: 360px;
    backdrop-filter: blur(10px);
  }
  .fish-icon { font-size: 64px; margin-bottom: 16px; display: block; }
  h1 { color: #c9a96e; font-size: 22px; font-weight: 800; margin-bottom: 8px; }
  .sub { color: #7a8ba0; font-size: 14px; margin-bottom: 40px; }
  .kakao-btn {
    display: flex; align-items: center; justify-content: center; gap: 10px;
    background: #FEE500; color: #191919;
    border: none; border-radius: 12px;
    width: 100%; padding: 14px;
    font-size: 15px; font-weight: 700; cursor: pointer;
    text-decoration: none;
    transition: opacity .15s;
  }
  .kakao-btn:hover { opacity: .88; }
  .kakao-btn img { width: 22px; height: 22px; }
  .err { color: #ff6b6b; font-size: 13px; margin-top: 16px; }
</style>
</head>
<body>
<div class="card">
  <span class="fish-icon">🎣</span>
  <h1>람쥐봇 낚시</h1>
  <p class="sub">하루 한 번, 오늘의 물고기를 낚아보세요!</p>
  <a class="kakao-btn" href="<%=request.getContextPath()%>/s4/kakao/auth">
    <svg width="22" height="22" viewBox="0 0 24 24" fill="#191919"><path d="M12 3C6.477 3 2 6.477 2 11c0 2.96 1.68 5.55 4.2 7.1L5.1 21.9a.5.5 0 0 0 .72.56L9.7 19.9A11.3 11.3 0 0 0 12 20c5.523 0 10-3.477 10-8S17.523 3 12 3z"/></svg>
    카카오로 시작하기
  </a>
  <%
    String err = request.getParameter("err");
    if ("cancel".equals(err)) { %>
  <p class="err">로그인이 취소되었습니다.</p>
  <% } else if ("token".equals(err)) { %>
  <p class="err">인증에 실패했습니다. 다시 시도해주세요.</p>
  <% } %>
</div>
</body>
</html>
