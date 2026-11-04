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
    /* [2026-11-04] 광산 입장하기(랜딩) -- 위 띠 + 탑 화면 광산 탭과 같은 카드 */
    .s55-land{ max-width:560px; margin:0 auto; padding:12px 12px calc(24px + env(safe-area-inset-bottom)); box-sizing:border-box; }
    .s55-top{ display:flex; align-items:center; gap:8px; padding:8px 10px; margin-bottom:10px; background:rgba(20,16,30,.88); border:2px solid #f4f0e6; box-shadow:3px 3px 0 rgba(0,0,0,.6); font-size:14px; }
    .s55-top img{ width:28px; height:28px; image-rendering:pixelated; }
    .s55-top .who{ flex:1; min-width:0; color:#cfc8e8; font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .s55-top a{ color:#a89ec8; font-size:12px; white-space:nowrap; }
    /* [2026-11-04] 앱 설치 안내(휴대폰 화면을 흉내 낸 그림) */
    .s55-gd{ position:fixed; inset:0; z-index:50; background:rgba(10,8,20,.86); overflow:auto; padding:16px; box-sizing:border-box; }
    .s55-gd-box{ max-width:520px; margin:0 auto; background:#1d1730; border:2px solid #f4f0e6; box-shadow:4px 4px 0 rgba(0,0,0,.6); padding:18px 14px; text-align:center; }
    .s55-gd-box h2{ margin:0 0 4px; font-size:19px; }
    .s55-gd-sub{ margin:0 0 12px; font-size:13px; color:#cfc8e8; }
    .s55-gd-kakao{ margin:0 0 12px; padding:10px; border:2px dashed #fee500; background:#2a2440; font-size:13px; line-height:1.6; }
    .s55-gd-kakao .s55-btn{ display:block; margin:8px auto 4px; }
    .s55-gd-kakao small{ color:#a89ec8; }
    .s55-gd-tabs{ display:flex; gap:6px; justify-content:center; margin:6px 0 12px; }
    .s55-gd-tabs button{ flex:1; padding:8px 4px; font-size:13px; font-weight:800; color:#cfc8e8; background:#2a2440; border:2px solid #5a4f7a; cursor:pointer; }
    .s55-gd-tabs button.on{ color:#fff; background:#3a2f55; border-color:#f4f0e6; }
    .s55-gd [hidden]{ display:none !important; }
    .s55-gd-steps{ display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:8px; }
    .s55-gd-step p{ margin:6px 0 0; font-size:12px; line-height:1.45; color:#e8e2f8; }
    .s55-gd-step p b:first-child{ display:inline-block; width:18px; height:18px; line-height:18px; border-radius:50%; background:#ffe14d; color:#1d1730; font-size:11px; margin-right:2px; }
    .s55-gd-step small{ color:#a89ec8; }
    .ph{ position:relative; height:150px; border:3px solid #0c0a14; border-radius:14px; background:#f4f1ea; overflow:hidden; display:flex; flex-direction:column; color:#333; font-size:9px; }
    .ph-bar{ display:flex; align-items:center; gap:4px; padding:5px 6px; background:#e2ddd2; }
    .ph-bar .url{ flex:1; background:#fff; border-radius:8px; padding:2px 5px; text-align:left; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .ph-bar.ios{ justify-content:space-between; font-size:9px; }
    .ph-body{ flex:1; background:repeating-linear-gradient(0deg, #ddd8cc 0 6px, #f4f1ea 6px 14px); margin:8px; border-radius:4px; }
    .ph-menu{ margin:auto 6px 6px; background:#fff; border-radius:8px; box-shadow:0 2px 6px rgba(0,0,0,.25); display:flex; flex-direction:column; text-align:left; }
    .ph-menu span{ padding:5px 7px; border-bottom:1px solid #eee; }
    .ph-tool{ display:flex; justify-content:space-around; padding:6px 2px; background:#e2ddd2; font-size:12px; }
    .ph-home{ background:linear-gradient(160deg, #3a5a8a, #6a3a7a); align-items:center; justify-content:center; }
    .ph-home .app, .ph-add{ display:flex; flex-direction:column; align-items:center; gap:3px; color:#fff; font-style:normal; }
    .ph-home .app img, .ph-add img{ width:42px; height:42px; border-radius:10px; image-rendering:pixelated; }
    .ph-home .app i{ font-style:normal; font-size:10px; }
    .ph-add{ margin:auto; color:#333; }
    .ph .hl{ outline:2px solid #ff4a4a; outline-offset:1px; border-radius:4px; background:#fff3c4; font-weight:800; animation:s55Blink 1.2s ease-in-out infinite; }
    .ph-home .app{ animation:s55Blink 1.2s ease-in-out infinite; }
    @keyframes s55Blink{ 50%{ outline-color:transparent; opacity:.75; } }
    #s55GdClose{ margin-top:14px; min-width:120px; }
    .s55-gd-box{ position:relative; }
    .s55-gd-x{ position:absolute; right:8px; top:6px; width:32px; height:32px; font-size:18px; color:#cfc8e8; background:none; border:none; cursor:pointer; }
  </style>
</head>
<body>
<%@ include file="_s55_live.jsp" %>
<% if ("game".equals(mode)) { %>
<%@ include file="_mine_overlay.jsp" %>
<%-- [2026-11-04] "광산으로 바로 들어가지 말고 광산 입장하기 페이지로 랜딩, 장착 정보나 로그를 볼 수 있게" -- 탑 화면 광산 탭과 같은 카드(MineHome: 장비 칸/체력·경험치/보유 + 내 광산 기록) --%>
<div class="s55-land" id="s55Land">
  <div class="s55-top">
    <img src="<%=ctx%>/img_loa/s55/icon-192.png" alt=""><b>람쥐 광산</b><span class="who"><%=userEsc%></span>
    <a href="#" onclick="s55Guide(); return false;">📲 앱 설치</a>
    <a href="<%=ctx%>/loa/s5/logout">로그아웃</a>
  </div>
  <% if (request.getAttribute("vapidPub") != null) { %>
  <div class="s55-top" id="s55PushBox"><span class="who">🔔 관리자 알림(접속·보스·전설 장비·카카오 연결·게시판)</span><button type="button" class="s55-btn" id="s55Push" style="margin:0;padding:6px 10px;font-size:12px">이 기기로 받기</button></div>
  <script>
  (function () {
    var key = '<%=request.getAttribute("vapidPub")%>', btn = document.getElementById('s55Push');
    function b64(s) { s = s.replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '='; var r = atob(s), a = new Uint8Array(r.length); for (var i = 0; i < r.length; i++) a[i] = r.charCodeAt(i); return a; }
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !window.Notification) { btn.textContent = '이 기기는 지원 안 함'; btn.disabled = true; return; }
    navigator.serviceWorker.ready.then(function (reg) { return reg.pushManager.getSubscription(); }).then(function (s) { if (s && Notification.permission === 'granted') btn.textContent = '받는 중 (다시 등록)'; });
    btn.onclick = function () {
      btn.disabled = true;
      Notification.requestPermission().then(function (p) {
        if (p !== 'granted') throw new Error('알림 권한이 꺼져 있어요');
        return navigator.serviceWorker.ready;
      }).then(function (reg) {
        return reg.pushManager.getSubscription().then(function (s) { return s || reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64(key) }); });
      }).then(function (s) {
        return fetch('<%=ctx%>/mine/push-sub', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'endpoint=' + encodeURIComponent(s.endpoint) }).then(function (r) { return r.json(); });
      }).then(function (d) { btn.disabled = false; btn.textContent = d.ok ? '받는 중 ✔ (시험 알림 보냄)' : '등록 실패'; })
        .catch(function (e) { btn.disabled = false; btn.textContent = (e && e.message) || '등록 실패'; });
    };
  })();
  </script>
  <% } %>
  <div id="s55Home"><div class="s55-msg">불러오는 중...</div></div>
</div>
<script src="<%=ctx%>/loa/s55/mine.js?v=<%=build%>"></script>
<%@ include file="_mine_home.jsp" %>
<script>
(function () {
  var user = <%=request.getAttribute("userJson")%>, base = '<%=ctx%>/loa';
  function home() {
    var box = document.getElementById('s55Home');
    fetch(base + '/api/tower-mine-home?userName=' + encodeURIComponent(user), { cache: 'no-store', credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
      if (d.error) { box.innerHTML = '<div class="s55-msg err">' + d.error + '</div>'; return; }
      MineHome.render(box, d, 's55Go()', '캘 때마다 곡괭이가 1씩 닳고, 대장장이에게서 강화해요 · 맵은 2일마다 새로 섞여요');
    }).catch(function () { box.innerHTML = '<div class="s55-msg err">불러오지 못했어요. <a href="" style="color:#cfc8e8">다시 시도</a></div>'; });
  }
  window.s55Go = function () {
    document.getElementById('s55Land').hidden = true;
    TowerMine.open({ base: base, user: user, onClose: function () { document.getElementById('s55Land').hidden = false; window.scrollTo(0, 0); home(); } });
  };
  home();
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
      var sim = (d.similar || []).map(function (c) { return '<button type="button" class="s55-btn s55-sim" data-n="' + esc(c.name) + '">' + esc(c.name) + ' (Lv.' + c.lv + ')</button>'; }).join(''); // [2026-11-04] 비슷한 닉네임 추천
      $('s55Confirm').innerHTML = (d.exists ? '기존 광산 캐릭터 <b>' + esc(d.name) + '</b> (Lv.' + d.lv + ')와 이을까요?'
          : (sim ? '<div style="margin-bottom:6px">혹시 이 캐릭터인가요?</div>' + sim + '<div style="margin:10px 0 0;color:#ffd27a">아니라면 ↓</div>' : '')
            + '<b>' + esc(d.name) + '</b> 캐릭터는 아직 없어요.<br>이 이름으로 새로 시작할까요?<br><small style="color:#ffb0a0">람쥐봇 방 닉네임과 한 글자라도 다르면 다른 캐릭터가 돼요.</small>')
        + '<br><button type="button" class="s55-btn main" id="s55Go">' + (d.exists ? '이 캐릭터로 시작' : '새로 시작') + '</button><button type="button" class="s55-btn" id="s55No">다시 입력</button>';
      $('s55Confirm').hidden = false;
      $('s55Go').onclick = link;
      Array.prototype.forEach.call(document.querySelectorAll('.s55-sim'), function (b) { b.onclick = function () { $('s55Name').value = b.getAttribute('data-n'); check(); }; });
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
        say(d.message || '연결했어요!', 'ok');
        s55Guide(function () { location.reload(); }); // [2026-11-04] 처음 연결 직후 -- 앱 설치 방법부터(닫으면 입장 화면)
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
<%-- [2026-11-04] "최초 로그인 연결에 성공하면 웹앱 설치 방법 알려줘(스샷 같은 걸로)" -- 처음 연결 직후 자동으로, 입장 화면에선 "📲 앱 설치" 로 다시 열기.
     그림은 실제 캡처 대신 휴대폰 화면을 흉내 낸 그림(기기마다 메뉴 모양이 조금 달라서). 카카오톡 안 브라우저에선 설치가 안 돼 "다른 브라우저로 열기"부터. --%>
<div class="s55-gd" id="s55Guide" hidden>
  <div class="s55-gd-box">
    <button type="button" class="s55-gd-x" onclick="document.getElementById('s55GdClose').click()" aria-label="닫기">✕</button>
    <h2>📲 람쥐 광산을 앱처럼 설치하기</h2>
    <p class="s55-gd-sub">홈 화면에 아이콘을 만들면 주소창 없이 전체 화면으로 열려요.</p>
    <div class="s55-gd-kakao" id="s55GdKakao" hidden>
      <b>지금은 카카오톡 안 브라우저예요.</b> 여기서는 설치가 안 돼요.<br>먼저 <b>다른 브라우저(크롬/사파리)로 열기</b>를 해 주세요.
      <a class="s55-btn main" id="s55GdExt" href="#">🌐 다른 브라우저로 열기</a>
      <small>안 되면 오른쪽 아래(또는 위) ⋮ / 공유 버튼 → "다른 브라우저로 열기"</small>
    </div>
    <button type="button" class="s55-btn main" id="s55GdInstall" hidden>⬇️ 지금 바로 설치</button>
    <div class="s55-gd-tabs"><button type="button" data-t="and" class="on">안드로이드 (크롬)</button><button type="button" data-t="ios">아이폰 (사파리)</button></div>
    <div class="s55-gd-steps" data-p="and">
      <div class="s55-gd-step"><div class="ph"><div class="ph-bar"><span class="url">rgb-tns.dev-apc.com/mine/</span><span class="hl">⋮</span></div><div class="ph-body"></div></div><p><b>1</b> 오른쪽 위 <b>⋮</b> 메뉴</p></div>
      <div class="s55-gd-step"><div class="ph"><div class="ph-bar"><span class="url">rgb-tns.dev-apc.com/mine/</span><span>⋮</span></div><div class="ph-menu"><span>새 탭</span><span>북마크</span><span class="hl">📲 홈 화면에 추가</span><span>공유</span></div></div><p><b>2</b> <b>홈 화면에 추가</b><br><small>("앱 설치"로 보일 수도 있어요)</small></p></div>
      <div class="s55-gd-step"><div class="ph ph-home"><span class="app"><img src="<%=ctx%>/img_loa/s55/icon-192.png" alt=""><i>람쥐광산</i></span></div><p><b>3</b> 홈 화면의 <b>람쥐광산</b> 아이콘으로 실행</p></div>
    </div>
    <div class="s55-gd-steps" data-p="ios" hidden>
      <div class="s55-gd-step"><div class="ph"><div class="ph-body"></div><div class="ph-tool"><span>‹</span><span>›</span><span class="hl">⬆︎</span><span>📖</span><span>⧉</span></div></div><p><b>1</b> 아래 가운데 <b>공유</b> 버튼(□↑)</p></div>
      <div class="s55-gd-step"><div class="ph"><div class="ph-menu"><span>복사</span><span>읽기 목록에 추가</span><span class="hl">➕ 홈 화면에 추가</span><span>북마크 추가</span></div></div><p><b>2</b> 아래로 내려 <b>홈 화면에 추가</b></p></div>
      <div class="s55-gd-step"><div class="ph"><div class="ph-bar ios"><span>취소</span><b>홈 화면에 추가</b><span class="hl">추가</span></div><div class="ph-add"><img src="<%=ctx%>/img_loa/s55/icon-192.png" alt=""><span>람쥐광산</span></div></div><p><b>3</b> 오른쪽 위 <b>추가</b> → 아이콘으로 실행</p></div>
    </div>
    <button type="button" class="s55-btn" id="s55GdClose">닫기</button><%-- [2026-11-04] "누르면 게임에 들어가는 것처럼 보인다" -- 닫기(닫으면 입장 화면: 장비/기록) --%>
  </div>
</div>
<script>
(function () {
  var g = document.getElementById('s55Guide'), deferred = null, after = null;
  var ua = navigator.userAgent || '', isKakao = /KAKAOTALK/i.test(ua), isIos = /iPhone|iPad|iPod/i.test(ua);
  window.addEventListener('beforeinstallprompt', function (e) { e.preventDefault(); deferred = e; var b = document.getElementById('s55GdInstall'); if (b) b.hidden = false; });
  function tab(t) {
    g.querySelectorAll('.s55-gd-tabs button').forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-t') === t); });
    g.querySelectorAll('.s55-gd-steps').forEach(function (p) { p.hidden = p.getAttribute('data-p') !== t; });
  }
  g.querySelectorAll('.s55-gd-tabs button').forEach(function (b) { b.onclick = function () { tab(b.getAttribute('data-t')); }; });
  document.getElementById('s55GdInstall').onclick = function () { if (!deferred) return; deferred.prompt(); deferred.userChoice.then(function () { deferred = null; document.getElementById('s55GdInstall').hidden = true; }); };
  document.getElementById('s55GdExt').onclick = function (e) { e.preventDefault(); location.href = 'kakaotalk://web/openExternal?url=' + encodeURIComponent(location.origin + '<%=ctx%>/mine/'); };
  document.getElementById('s55GdClose').onclick = function () { g.hidden = true; if (after) after(); };
  /** 설치 안내 열기 -- then = 닫을 때 할 일(처음 연결 직후엔 새로고침해 입장 화면으로). 이미 앱으로 열려 있으면 안 띄운다. */
  window.s55Guide = function (then) {
    after = then || null;
    var standalone = window.matchMedia && window.matchMedia('(display-mode: fullscreen), (display-mode: standalone)').matches || navigator.standalone;
    if (standalone && then) { then(); return; }
    document.getElementById('s55GdKakao').hidden = !isKakao;
    tab(isIos ? 'ios' : 'and');
    g.hidden = false;
  };
})();
</script>
<script>if ('serviceWorker' in navigator) navigator.serviceWorker.register('<%=ctx%>/mine/sw.js', { scope: '<%=ctx%>/mine/' }).catch(function () { });</script>
</body>
</html>
