<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>광산 캐릭터 현황</title>
  <!-- [2026-10-25] "캐릭터별 현재 위치, 장착 아이템들, 레벨, 보유 수량 보이는 페이지" 요청 -- 관리자용(로그 뷰어와 같은 카카오 로그인).
       데이터는 GET /api/tower-mine-users(BotS5Service.mineUserOverview, 읽기만). 광산 화면을 띄운 유저는 실시간 위치(🟢), 아니면 마지막 저장 위치.
       톤은 s5_battle_log_view.jsp(양피지 팔레트)와 맞췄다. -->
  <style>
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC;
      --pp:#2F8F5C; --pp-soft:#D6EEDF; --mine:#5B3FA0; --mine-soft:#E3DDF3;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{ margin:0; background: radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
          color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif; min-height:100vh; }
    .wrap{ max-width:880px; margin:0 auto; padding:20px 16px 60px; display:flex; flex-direction:column; gap:14px; }
    h1{ font-size:18px; font-weight:800; margin:4px 0 0; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
    h1 a{ margin-left:auto; font-size:12px; font-weight:700; color:var(--gold); text-decoration:none; background:var(--gold-soft); border-radius:999px; padding:5px 12px; }
    .sub{ font-size:12px; color:var(--ink-soft); margin:0; }
    .bar{ display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
    .bar input[type=text], .bar select{ padding:8px 12px; border:1.5px solid var(--line); border-radius:14px; background:#fff; font-size:13px; color:var(--ink); }
    .bar input[type=text]{ flex:1 1 160px; min-width:0; }
    .bar label{ white-space:nowrap; }
    .bar button{ background:var(--gold); color:#fff; border:none; padding:8px 16px; border-radius:14px; font-size:13px; font-weight:700; cursor:pointer; }
    .bar label{ font-size:12px; color:var(--ink-soft); display:flex; align-items:center; gap:4px; }
    .meta{ font-size:12px; color:var(--ink-soft); }

    .list{ display:grid; grid-template-columns:repeat(auto-fill, minmax(min(100%, 400px), 1fr)); gap:12px; }
    .ch{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line); border-radius:18px;
         padding:12px 14px; box-shadow:var(--shadow); display:flex; flex-direction:column; gap:9px; min-width:0; }
    .ch.on{ border-color:#9fd3b4; }
    .ch-head{ display:flex; align-items:baseline; gap:8px; flex-wrap:wrap; }
    .ch-name{ font-weight:800; font-size:14.5px; word-break:break-all; }
    .mchar{ width:40px; height:40px; image-rendering:pixelated; align-self:center; background:#F3E6C4; border:1.5px solid var(--line); border-radius:10px; flex-shrink:0; }
    .mchar.wide{ width:auto; } /* [2026-10-28] 환생 날개가 있으면 그림이 넓다(16+2ext x 18) -- 높이만 맞추고 비율 유지 */
    .dot{ width:8px; height:8px; border-radius:50%; background:#c9bfa4; display:inline-block; flex-shrink:0; align-self:center; }
    .ch.on .dot{ background:var(--pp); box-shadow:0 0 0 3px var(--pp-soft); }
    .lv{ margin-left:auto; font-weight:800; font-size:13px; color:var(--mine); background:var(--mine-soft); border-radius:999px; padding:2px 10px; font-variant-numeric:tabular-nums; }
    .where{ font-size:12.5px; display:flex; gap:6px; flex-wrap:wrap; align-items:center; }
    .where b{ font-weight:800; }
    .where .xy{ font-variant-numeric:tabular-nums; background:#fff; border:1px solid var(--line); border-radius:8px; padding:1px 7px; font-size:12px; }
    .where .ago{ color:var(--ink-soft); font-size:11.5px; }
    .stats{ display:grid; grid-template-columns:repeat(4, 1fr); gap:6px; }
    .stat{ background:#fff; border:1px solid var(--line); border-radius:10px; padding:5px 7px; min-width:0; }
    .stat .k{ font-size:10px; color:var(--ink-soft); letter-spacing:.02em; }
    .stat .v{ font-size:12.5px; font-weight:800; font-variant-numeric:tabular-nums; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .meter{ height:4px; background:var(--gold-soft); border-radius:3px; margin-top:3px; overflow:hidden; }
    .meter i{ display:block; height:100%; background:var(--gold); }
    .sec{ font-size:10.5px; color:var(--ink-soft); font-weight:700; letter-spacing:.04em; margin-bottom:3px; }
    .chips{ display:flex; flex-wrap:wrap; gap:4px; }
    .chip{ font-size:11.5px; border-radius:999px; padding:2px 9px; background:#fff; border:1px solid var(--line); color:var(--ink-soft); white-space:nowrap; }
    .chip.worn{ background:var(--pp-soft); border-color:#a9d8bd; color:#1f6b43; font-weight:700; }
    .chip .t{ font-variant-numeric:tabular-nums; }
    .none{ font-size:11.5px; color:#a99c80; }
    .ores{ display:flex; flex-wrap:wrap; gap:4px; }
    .ore{ font-size:11.5px; border-radius:8px; padding:2px 7px; background:#fff; border:1px solid var(--line); white-space:nowrap; }
    .ore b{ font-variant-numeric:tabular-nums; margin-left:3px; }
    .ore.g6{ background:#FFF4C9; border-color:#E9CF6B; }           /* 별빛석 */
    .ore.g5{ background:#E4F1FB; border-color:#A9CBE6; }           /* 미스릴 */
    .ore.day{ background:#F1ECE2; }                                /* 요일석 */
    .ore.seven{ background:var(--mine-soft); border-color:#c6b8ea; } /* 칠요석 */
    .ore.gem{ background:#FBE8EE; border-color:#EDBACB; }          /* 보석 */
    .ore.dia{ background:#E8F7F8; border-color:#9fd8dd; font-weight:700; }
    .empty, .loading{ text-align:center; color:var(--ink-soft); font-size:13px; padding:30px 0; }
    @media (max-width:420px){ .stats{ grid-template-columns:repeat(2, 1fr); } }
  </style>
</head>
<body>
<script>
(function () {
  var of = window.fetch;
  window.fetch = function () {
    return of.apply(this, arguments).then(function (r) {
      if (r.status === 401) location.href = '/loa/s5/login?next=' + encodeURIComponent(location.pathname);
      return r;
    });
  };
})();
</script>
  <div class="wrap">
    <h1>⛏️ 광산 캐릭터 현황 <a href="/loa/tower-battle-log">📜 로그 뷰어</a></h1>
    <p class="sub">🟢 지금 광산 화면을 보고 있는 캐릭터는 실시간 위치, 나머지는 마지막으로 저장된 위치예요. 보유 수량은 0개인 건 뺐어요.</p>
    <div class="bar">
      <input type="text" id="q" placeholder="캐릭터 이름 검색">
      <select id="sort">
        <option value="recent">최근 활동순</option>
        <option value="lv">레벨순</option>
        <option value="dig">채광 수순</option>
        <option value="kills">처치 수순</option>
      </select>
      <label><input type="checkbox" id="auto" checked> 10초마다 새로고침</label>
      <button type="button" id="reload">새로고침</button>
    </div>
    <div class="meta" id="meta"></div>
    <div class="list" id="list"><div class="loading">불러오는 중…</div></div>
  </div>
<%@ include file="_mine_char.jsp" %>
<script>
(function () {
  var users = [], serverNow = Date.now(), timer = null;
  function qs(id) { return document.getElementById(id); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function n(v) { return Number(v || 0).toLocaleString('ko-KR'); }
  function ago(ms) {
    if (!ms) return '기록 없음';
    var s = Math.max(0, Math.round((serverNow - ms) / 1000));
    if (s < 60) return s + '초 전';
    if (s < 3600) return Math.floor(s / 60) + '분 전';
    if (s < 86400) return Math.floor(s / 3600) + '시간 전';
    return Math.floor(s / 86400) + '일 전';
  }
  var KIND = { helmet: '⛑️', ring: '💍', necklace: '📿', boots: '👢', tool: '⛏️', dragon: '🌊', coat: '🧥' };
  var SLOT = { hat: '모자', hair: '머리', top: '상의', bottom: '하의', shoes: '신발' };
  function oreClass(g) {
    if (g === 29) return 'dia';
    if (g >= 21) return 'gem';
    if (g === 18) return 'seven';
    if (g >= 11) return 'day';
    return g === 6 ? 'g6' : g === 5 ? 'g5' : '';
  }
  function card(u) {
    var pct = u.expNeed > 0 ? Math.min(100, u.exp / u.expNeed * 100) : 0;
    var pickPct = u.pickMax > 0 ? Math.min(100, u.pickDur / u.pickMax * 100) : 0;
    var gear = (u.gear || []).map(function (g) {
      return '<span class="chip' + (g.worn ? ' worn' : '') + '" title="' + (g.worn ? '착용 중' : '보유(미착용)') + '">' + (KIND[g.kind] || '') + ' ' + esc(g.name)
          + (g.maxTier > 1 ? ' <span class="t">' + g.tier + '/' + g.maxTier + '</span>' : '') + '</span>';
    }).join('');
    var look = Object.keys(SLOT).filter(function (k) { return u.look && u.look[k]; }).map(function (k) {
      return '<span class="chip">' + SLOT[k] + ' · ' + esc(u.look[k]) + '</span>';
    }).join('');
    var ores = (u.ores || []).map(function (o) {
      return '<span class="ore ' + oreClass(o.grade) + '">' + esc(o.name) + '<b>' + n(o.qty) + '</b></span>';
    }).join('');
    return '<div class="ch' + (u.online ? ' on' : '') + '">'
      + '<div class="ch-head">' + (window.MineChar ? '<img class="mchar' + (MineChar.wide && MineChar.wide(u.lookRaw) ? ' wide' : '') + '" src="' + MineChar.dataUrl(u.lookRaw || {}, true) + '" alt="">' : '') // [2026-10-25] 광산 화면과 같은 캐릭터 그림 ([2026-10-28] 환생 날개 포함)
      + '<span class="dot" title="' + (u.online ? '광산 접속 중' : '접속 안 함') + '"></span><span class="ch-name">' + esc(u.userName) + '</span>'
      + '<span class="lv">Lv.' + u.lv + (u.rebirth > 0 ? '(환생' + u.rebirth + ')' : '') + '</span></div>'
      + '<div class="where">📍 <b>' + esc(u.mapName) + '</b><span class="xy">' + u.x + ', ' + u.y + '</span>'
      + '<span class="ago">' + (u.online ? '지금 접속 중' : '마지막 활동 ' + ago(u.lastAt)) + '</span></div>'
      + '<div class="stats">'
      +   '<div class="stat"><div class="k">경험치</div><div class="v">' + Math.floor(pct) + '%</div><div class="meter"><i style="width:' + pct + '%"></i></div></div>'
      +   '<div class="stat"><div class="k">곡괭이</div><div class="v">' + n(u.pickDur) + ' / ' + n(u.pickMax) + '</div><div class="meter"><i style="width:' + pickPct + '%"></i></div></div>'
      +   '<div class="stat"><div class="k">채광</div><div class="v">' + n(u.totalDig) + '</div></div>'
      +   '<div class="stat"><div class="k">처치</div><div class="v">' + n(u.kills) + '</div></div>'
      + '</div>'
      + '<div><div class="sec">장비 · ' + esc(u.pickName) + ' 사용 중' + (u.dragonQuest === 1 ? ' · 선장 퀘스트 진행 중' : '') + '</div><div class="chips">' + (gear || '<span class="none">장비 없음</span>') + '</div></div>'
      + '<div><div class="sec">외형 · ' + (u.gender === 1 ? '여' : '남') + ' · 치장 ' + u.cosOwned + '개 보유</div><div class="chips">' + (look || '<span class="none">기본 외형</span>') + '</div></div>'
      + '<div><div class="sec">보유</div><div class="ores">' + (ores || '<span class="none">없음</span>') + '</div></div>'
      + '</div>';
  }
  function render() {
    var q = qs('q').value.trim().toLowerCase(), sort = qs('sort').value;
    var list = users.filter(function (u) { return !q || String(u.userName).toLowerCase().indexOf(q) >= 0; });
    list.sort(function (a, b) {
      if (a.online !== b.online) return a.online ? -1 : 1;
      if (sort === 'lv') return (b.rebirth || 0) - (a.rebirth || 0) || b.lv - a.lv || b.exp - a.exp; // [2026-10-28] 환생이 먼저
      if (sort === 'dig') return b.totalDig - a.totalDig;
      if (sort === 'kills') return b.kills - a.kills;
      return (b.lastAt || 0) - (a.lastAt || 0);
    });
    var on = users.filter(function (u) { return u.online; }).length;
    qs('meta').textContent = '캐릭터 ' + users.length + '명 · 지금 접속 ' + on + '명 · ' + new Date(serverNow).toLocaleTimeString('ko-KR') + ' 기준';
    qs('list').innerHTML = list.length ? list.map(card).join('') : '<div class="empty">조건에 맞는 캐릭터가 없어요.</div>';
  }
  function load() {
    fetch('/loa/api/tower-mine-users', { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
      if (!d || !d.users) { qs('list').innerHTML = '<div class="empty">' + esc((d && d.error) || '불러오지 못했어요.') + '</div>'; return; }
      users = d.users; serverNow = d.now || Date.now();
      render();
    }).catch(function () { qs('list').innerHTML = '<div class="empty">불러오지 못했어요. 새로고침을 눌러 주세요.</div>'; });
  }
  function schedule() {
    if (timer) clearInterval(timer);
    timer = qs('auto').checked ? setInterval(function () { if (!document.hidden) load(); }, 10000) : null;
  }
  qs('q').addEventListener('input', render);
  qs('sort').addEventListener('change', render);
  qs('reload').addEventListener('click', load);
  qs('auto').addEventListener('change', schedule);
  load();
  schedule();
})();
</script>
</body>
</html>
