<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%><%--
  [2026-11-04] 시즌 5.5 광산 웹앱(PWA) 전용 주소 /mine/ -- S55MineController. mode = login(카카오 로그인) / link(닉네임 입력해 연결) / game(바로 광산).
  광산 화면은 탑 화면과 같은 것을 쓴다: 마크업 _mine_overlay.jsp, 스타일 /loa/s55/tower.css, 스크립트 /loa/s55/mine.js(TowerMine).
--%><%
  String ctx = request.getContextPath();
  String build = request.getAttribute("pageBuild") == null ? "" : String.valueOf(request.getAttribute("pageBuild"));
  String mode = String.valueOf(request.getAttribute("mode"));
  String nickEsc = request.getAttribute("nickname") == null ? "" : String.valueOf(request.getAttribute("nickname")).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  String userEsc = request.getAttribute("userName") == null ? "" : String.valueOf(request.getAttribute("userName")).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
%><!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
  <title>람쥐 광산</title>
  <link rel="manifest" href="<%=ctx%>/mine/manifest.webmanifest">
  <meta name="theme-color" content="#241a3a">
  <meta name="mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
  <meta name="apple-mobile-web-app-title" content="람쥐광산">
  <link rel="apple-touch-icon" href="<%=ctx%>/img_loa/s55/icon-192.png">
  <link rel="icon" type="image/png" href="<%=ctx%>/img_loa/s55/icon-192.png">
  <link rel="stylesheet" href="<%=ctx%>/loa/s55/tower.css?v=<%=build%>">
  <style>
    html, body{ margin:0; height:100%; background:#241a3a; color:#f4f0e6; font-family:'Malgun Gothic', 'Apple SD Gothic Neo', sans-serif; overscroll-behavior:none; }
    .s55-wrap{ min-height:100%; display:flex; align-items:center; justify-content:center; padding:20px 16px; box-sizing:border-box; }
    .s55-card{ width:100%; max-width:380px; background:rgba(20,16,30,.88); border:2px solid #f4f0e6; box-shadow:4px 4px 0 rgba(0,0,0,.6); padding:22px 18px; text-align:center; }
    .s55-card h1{ margin:8px 0 4px; font-size:21px; letter-spacing:1px; }
    .s55-card .sub{ font-size:13px; color:#cfc8e8; line-height:1.6; margin:0 0 16px; }
    .s55-card img.logo{ width:80px; height:80px; image-rendering:pixelated; }
    .s55-input{ width:100%; box-sizing:border-box; font-size:16px; padding:11px 12px; border:2px solid #f4f0e6; background:#140d0a; color:#fff; outline:none; }
    .s55-input:focus{ border-color:#ffe14d; }
    .s55-btn{ display:inline-block; margin:10px 4px 0; padding:11px 16px; font-size:15px; font-weight:800; color:#fff; background:#3a2f55; border:2px solid #f4f0e6; box-shadow:3px 3px 0 rgba(0,0,0,.6); cursor:pointer; text-decoration:none; }
    .s55-btn.main{ background:#2f8f5c; }
    .s55-btn.kakao{ background:#fee500; color:#191600; border-color:#191600; width:100%; box-sizing:border-box; }
    .s55-btn:disabled{ opacity:.5; }
    .s55-msg{ font-size:13px; line-height:1.55; margin-top:12px; min-height:18px; }
    .s55-msg.err{ color:#ff8a8a; } .s55-msg.ok{ color:#9fe0a8; }
    .s55-confirm{ margin-top:12px; padding:12px; border:2px dashed #b89cff; background:#241a3a; font-size:14px; line-height:1.6; }
    .s55-small{ font-size:11px; color:#a89ec8; margin-top:16px; line-height:1.6; }
    .s55-small a{ color:#cfc8e8; }
  </style>
</head>
<body>
<%@ include file="_s55_live.jsp" %>
<% if ("game".equals(mode)) { %>
<%@ include file="_mine_overlay.jsp" %>
<div class="s55-wrap" id="s55Out" hidden>
  <div class="s55-card">
    <img class="logo" src="<%=ctx%>/img_loa/s55/icon-192.png" alt="">
    <h1>광산에서 나왔어요</h1>
    <p class="sub"><%=userEsc%></p>
    <button type="button" class="s55-btn main" onclick="s55Go()">⛏️ 다시 들어가기</button>
    <div class="s55-small"><a href="<%=ctx%>/loa/s5/logout">로그아웃</a></div>
  </div>
</div>
<script src="<%=ctx%>/loa/s55/mine.js?v=<%=build%>"></script>
<script>
(function () {
  var user = <%=request.getAttribute("userJson")%>;
  window.s55Go = function () {
    document.getElementById('s55Out').hidden = true;
    TowerMine.open({ base: '<%=ctx%>/loa', user: user, onClose: function () { document.getElementById('s55Out').hidden = false; } });
  };
  s55Go();
})();
</script>
<% } else if ("link".equals(mode)) { %>
<div class="s55-wrap">
  <div class="s55-card">
    <img class="logo" src="<%=ctx%>/img_loa/s55/icon-192.png" alt="">
    <h1>람쥐 광산</h1>
    <p class="sub"><b><%=nickEsc%></b> 님, 처음 한 번만 광산 캐릭터를 정해 주세요.<br>람쥐봇 방에서 쓰는 <b>카카오톡 닉네임</b>을 그대로 입력하면<br>기존 광산 캐릭터와 이어져요. 없으면 새로 만들어요.</p>
    <input class="s55-input" id="s55Name" maxlength="40" placeholder="예) 일어난다람쥐/카단" autocomplete="off" autocapitalize="off" spellcheck="false">
    <button type="button" class="s55-btn main" id="s55Check">확인</button>
    <div class="s55-confirm" id="s55Confirm" hidden></div>
    <div class="s55-msg" id="s55Msg"></div>
    <div class="s55-small">카카오 계정 하나에 광산 캐릭터 하나가 이어져요. 다른 카카오 계정에 이미 이어진 닉네임은 쓸 수 없어요.<br><a href="<%=ctx%>/loa/s5/logout">다른 카카오 계정으로 로그인</a></div>
  </div>
</div>
<script>
(function () {
  var base = '<%=ctx%>/mine/', $ = function (id) { return document.getElementById(id); }, picked = null;
  function say(t, cls) { $('s55Msg').textContent = t || ''; $('s55Msg').className = 's55-msg ' + (cls || ''); }
  function esc(t) { return String(t).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function check() {
    var n = $('s55Name').value.trim();
    if (!n) { say('닉네임을 입력해 주세요.', 'err'); return; }
    $('s55Check').disabled = true; say('확인하는 중...');
    fetch(base + 'check?name=' + encodeURIComponent(n), { cache: 'no-store', credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
      $('s55Check').disabled = false;
      if (d.login === false) { location.reload(); return; }
      if (!d.ok) { say(d.message || '확인하지 못했어요.', 'err'); $('s55Confirm').hidden = true; return; }
      if (d.taken) { say('이미 다른 카카오 계정에 이어진 닉네임이에요. 본인 캐릭터라면 관리자에게 문의해 주세요.', 'err'); $('s55Confirm').hidden = true; return; }
      picked = d.name; say('');
      $('s55Confirm').innerHTML = (d.exists ? '기존 광산 캐릭터 <b>' + esc(d.name) + '</b> (Lv.' + d.lv + ')와 이을까요?' : '<b>' + esc(d.name) + '</b> 캐릭터는 아직 없어요.<br>이 이름으로 새로 시작할까요?')
        + '<br><button type="button" class="s55-btn main" id="s55Go">' + (d.exists ? '이 캐릭터로 시작' : '새로 시작') + '</button><button type="button" class="s55-btn" id="s55No">다시 입력</button>';
      $('s55Confirm').hidden = false;
      $('s55Go').onclick = link;
      $('s55No').onclick = function () { $('s55Confirm').hidden = true; picked = null; $('s55Name').focus(); };
    }).catch(function () { $('s55Check').disabled = false; say('요청이 실패했어요. 다시 시도해 주세요.', 'err'); });
  }
  function link() {
    if (!picked) return;
    $('s55Go').disabled = true;
    fetch(base + 'link', { method: 'POST', cache: 'no-store', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'name=' + encodeURIComponent(picked) })
      .then(function (r) { return r.json(); }).then(function (d) {
        if (d.login === false) { location.reload(); return; }
        if (!d.ok) { $('s55Go').disabled = false; say(d.message || '연결하지 못했어요.', 'err'); return; }
        say((d.message || '연결했어요!') + ' 광산으로 들어가요...', 'ok');
        setTimeout(function () { location.reload(); }, 800);
      }).catch(function () { $('s55Go').disabled = false; say('요청이 실패했어요. 다시 시도해 주세요.', 'err'); });
  }
  $('s55Check').onclick = check;
  $('s55Name').onkeydown = function (e) { if (e.key === 'Enter') check(); };
})();
</script>
<% } else { %>
<div class="s55-wrap">
  <div class="s55-card">
    <img class="logo" src="<%=ctx%>/img_loa/s55/icon-192.png" alt="">
    <h1>람쥐 광산</h1>
    <p class="sub">람쥐봇 시즌 5.5 광산 게임이에요.<br>카카오 계정으로 로그인하면 시작할 수 있어요.</p>
    <a class="s55-btn kakao" href="<%=ctx%>/loa/s5/kakao/auth?next=/mine/">카카오로 로그인</a>
    <div class="s55-small">카카오 로그인으로는 회원 번호(키값)와 닉네임만 받아요.<br>홈 화면에 추가하면 앱처럼 쓸 수 있어요.</div>
  </div>
</div>
<% } %>
<script>if ('serviceWorker' in navigator) navigator.serviceWorker.register('<%=ctx%>/mine/sw.js', { scope: '<%=ctx%>/mine/' }).catch(function () { });</script>
</body>
</html>
