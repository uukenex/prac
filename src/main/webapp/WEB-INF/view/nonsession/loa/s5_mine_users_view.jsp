<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>광산 캐릭터 현황</title>
  <!-- [2026-10-25] "캐릭터별 현재 위치, 장착 아이템들, 레벨, 보유 수량 보이는 페이지" 요청 -- 관리자용(로그 뷰어와 같은 카카오 로그인).
       데이터는 GET /api/tower-mine-users(BotS5Service.mineUserOverview, 읽기만). 광산 화면을 띄운 유저는 실시간 위치(🟢), 아니면 마지막 저장 위치.
       [2026-10-28] "인게임과 비슷하게 정렬, 한눈에 정보가 많이" -- 광산 화면 창과 같은 어두운 판 + 금색 숫자, 인벤토리처럼
       캐릭터 그림 양옆에 부위별 칸(무기/반지/장갑 | 옷/목걸이/신발, 아래 채집 도구), 기타 탭과 같은 분류로 보유 수량.
       카드/표 두 가지 보기(표 = 한 줄에 한 명). -->
  <style>
    :root{
      --bg:#14101e; --panel:#1d1730; --slot:#2a2340; --edge:rgba(244,240,230,.35); --edge-soft:rgba(244,240,230,.14);
      --ink:#f4f0e6; --ink-soft:#b9b0c9; --cream:#ffe9a8; --gold:#ffe14d; --on:#5fd38a; --hp:#e2524f; --exp:#ffcf4a; --dur:#5cb8ff; --red:#a33a3a;
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{ margin:0; background:var(--bg); color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif; min-height:100vh; }
    .num{ font-family:ui-monospace, Consolas, monospace; font-variant-numeric:tabular-nums; }
    .wrap{ max-width:1280px; margin:0 auto; padding:16px 16px 60px; display:flex; flex-direction:column; gap:12px; }
    .top{ position:sticky; top:0; z-index:5; background:rgba(20,16,30,.97); border-bottom:2px solid var(--edge); margin:-16px -16px 0; padding:12px 16px 10px; display:flex; flex-direction:column; gap:8px; }
    h1{ font-size:17px; font-weight:800; margin:0; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
    h1 a{ margin-left:auto; font-size:12px; font-weight:700; color:var(--cream); text-decoration:none; border:2px solid var(--edge); padding:4px 10px; background:var(--slot); }
    .bar{ display:flex; gap:6px; flex-wrap:wrap; align-items:center; }
    .bar input[type=text], .bar select{ padding:7px 10px; border:2px solid var(--edge); background:var(--slot); color:var(--ink); font-size:13px; font-family:inherit; }
    .bar input[type=text]{ flex:1 1 160px; min-width:0; }
    .bar button{ background:var(--slot); color:var(--ink); border:2px solid var(--edge); padding:7px 12px; font-size:13px; font-weight:700; cursor:pointer; font-family:inherit; }
    .bar button.on{ border-color:var(--gold); color:var(--gold); }
    .bar button:focus-visible, .bar input:focus-visible, .bar select:focus-visible{ outline:2px solid var(--gold); outline-offset:1px; }
    .bar label{ font-size:12px; color:var(--ink-soft); display:flex; align-items:center; gap:4px; white-space:nowrap; }
    .meta{ font-size:12px; color:var(--ink-soft); display:flex; gap:10px; flex-wrap:wrap; }
    .meta b{ color:var(--gold); }

    /* ---- 카드 보기 ---- */
    .list{ display:grid; grid-template-columns:repeat(auto-fill, minmax(min(100%, 410px), 1fr)); gap:10px; }
    .ch{ background:var(--panel); border:2px solid var(--edge); padding:10px; display:flex; flex-direction:column; gap:8px; min-width:0; }
    .ch.on{ border-color:var(--on); }
    .ch-head{ display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
    .dot{ width:8px; height:8px; background:#5a5270; display:inline-block; flex-shrink:0; }
    .on .dot{ background:var(--on); box-shadow:0 0 0 2px rgba(95,211,138,.3); }
    .ch-name{ font-weight:800; font-size:14px; word-break:break-all; }
    .lv{ font-weight:800; font-size:12px; color:var(--gold); border:1px solid var(--edge); background:var(--slot); padding:1px 7px; white-space:nowrap; }
    .lv.rb{ color:#ffb0f0; }
    .where{ margin-left:auto; font-size:12px; color:var(--ink-soft); text-align:right; }
    .where b{ color:var(--ink); }
    .where .num{ color:var(--cream); }

    .body{ display:grid; grid-template-columns:minmax(0, 1.25fr) minmax(0, 1fr); gap:8px; }
    @media (max-width:440px){ .body{ grid-template-columns:1fr; } }
    /* 인벤토리 장비 탭처럼: 칸 3 | 캐릭터 | 칸 3, 아래 채집 도구 */
    .doll{ display:grid; grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); gap:4px; align-items:stretch; }
    .doll-col{ display:flex; flex-direction:column; gap:4px; }
    .doll-char{ display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:4px; background:rgba(255,255,255,.05); border:2px solid var(--edge-soft); min-width:64px; }
    .doll-char img{ height:52px; width:52px; image-rendering:pixelated; }
    .doll-char img.wide{ width:auto; }
    .doll-char small{ color:var(--cream); font-weight:800; font-size:11px; }
    .slot{ background:var(--slot); border:2px solid var(--edge-soft); padding:3px 5px; min-width:0; flex:1; display:flex; flex-direction:column; justify-content:center; }
    .slot.on{ border-color:#ffcf4a; }
    .slot b{ font-size:10px; color:var(--ink-soft); font-weight:700; white-space:nowrap; }
    .slot span{ font-size:11.5px; line-height:1.25; color:#8f86a3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; word-break:keep-all; overflow-wrap:anywhere; }
    .slot.on span{ color:var(--cream); font-weight:700; }
    .tool{ grid-column:1 / -1; flex-direction:row; align-items:center; gap:6px; }
    .tool span{ flex:1; }

    .stats{ display:flex; flex-direction:column; gap:5px; min-width:0; }
    .bar-row{ font-size:11px; }
    .bar-row .k{ display:flex; justify-content:space-between; gap:4px; color:var(--ink-soft); }
    .bar-row .k .num{ color:var(--ink); }
    .meter{ height:6px; background:#0d0a14; border:1px solid var(--edge-soft); margin-top:2px; }
    .meter i{ display:block; height:100%; }
    .kv{ display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:3px; }
    .kv div{ background:var(--slot); border:1px solid var(--edge-soft); padding:2px 5px; min-width:0; }
    .kv small{ display:block; font-size:9.5px; color:var(--ink-soft); }
    .kv b{ font-size:12px; color:var(--gold); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; display:block; }

    .inv{ display:flex; flex-direction:column; gap:3px; border-top:1px solid var(--edge-soft); padding-top:6px; }
    .inv-row{ display:flex; gap:6px; align-items:flex-start; }
    .inv-row > b{ font-size:10.5px; color:var(--ink-soft); width:62px; flex-shrink:0; padding-top:2px; white-space:nowrap; }
    .inv-items{ display:flex; flex-wrap:wrap; gap:3px; min-width:0; }
    .it{ display:inline-flex; align-items:center; gap:3px; font-size:11.5px; background:rgba(255,255,255,.06); border:1px solid var(--edge-soft); padding:1px 5px 1px 3px; white-space:nowrap; }
    .it b{ color:var(--gold); font-family:ui-monospace, Consolas, monospace; }
    .ore-sw{ width:10px; height:10px; display:inline-block; transform:rotate(45deg) scale(.82); border:1px solid rgba(0,0,0,.6); flex-shrink:0; }
    .ore-sw.cut{ box-shadow:0 0 0 1px #fff; }
    .look{ font-size:11px; color:var(--ink-soft); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .look span{ color:var(--ink); }
    .none{ font-size:11px; color:#6f6784; }

    /* ---- 표 보기(한 줄에 한 명) ---- */
    .tbl-wrap{ overflow-x:auto; border:2px solid var(--edge); background:var(--panel); }
    table.tbl{ border-collapse:collapse; width:100%; font-size:12px; min-width:1080px; }
    .tbl th{ position:sticky; top:0; background:#241d38; color:var(--ink-soft); font-size:11px; text-align:left; padding:6px; border-bottom:2px solid var(--edge); white-space:nowrap; }
    .tbl td{ padding:4px 6px; border-bottom:1px solid var(--edge-soft); vertical-align:middle; white-space:nowrap; }
    .tbl tr.on td:first-child{ box-shadow:inset 3px 0 0 var(--on); }
    .tbl td.r, .tbl th.r{ text-align:right; }
    .tbl img{ height:24px; width:24px; image-rendering:pixelated; vertical-align:middle; }
    .tbl img.wide{ width:auto; }
    .tbl .eq{ color:var(--cream); }
    .tbl .eq.off{ color:#6f6784; }
    .tbl .mini{ display:inline-block; width:46px; height:5px; background:#0d0a14; border:1px solid var(--edge-soft); vertical-align:middle; margin-left:4px; }
    .tbl .mini i{ display:block; height:100%; }
    .empty, .loading{ text-align:center; color:var(--ink-soft); font-size:13px; padding:30px 0; }
    @media (prefers-reduced-motion: reduce){ *{ transition:none !important; } }
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
    <div class="top">
      <h1>⛏️ 광산 캐릭터 현황 <a href="/loa/tower-battle-log">📜 로그 뷰어</a></h1>
      <div class="bar">
        <input type="text" id="q" placeholder="캐릭터 이름 / 맵 검색">
        <select id="sort">
          <option value="recent">최근 활동순</option>
          <option value="lv">레벨순(환생 먼저)</option>
          <option value="dig">채광 수순</option>
          <option value="kills">처치 수순</option>
          <option value="map">맵별</option>
        </select>
        <button type="button" id="vCard" class="on">🎴 카드</button>
        <button type="button" id="vTable">📋 표</button>
        <label><input type="checkbox" id="auto" checked> 10초 자동</label>
        <button type="button" id="reload">새로고침</button>
      </div>
      <div class="meta" id="meta"></div>
    </div>
    <div id="list"><div class="loading">불러오는 중…</div></div>
  </div>
<%@ include file="_mine_char.jsp" %>
<%@ include file="_mine_ore.jsp" %>
<script>
(function () {
  var users = [], serverNow = Date.now(), timer = null, view = 'card';
  try { if (localStorage.getItem('mineUsersView') === 'table') view = 'table'; } catch (e) {}
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
  function pct(a, b) { return b > 0 ? Math.max(0, Math.min(100, a / b * 100)) : 0; }
  // 광산 화면 인벤토리(EQ_SLOTS)와 같은 칸, 같은 배치
  var SLOTS = { weapon: '⚔️ 무기', ring: '💍 반지', gloves: '🧤 장갑', coat: '🧥 옷', necklace: '📿 목걸이', boots: '👢 신발', tool: '⛏️ 채집 도구' };
  var SLOT_ORDER = ['weapon', 'ring', 'gloves', 'coat', 'necklace', 'boots', 'tool'];
  var LOOK = { hat: '모자', hair: '머리', top: '상의', bottom: '하의', shoes: '신발' };
  function wornOf(u, sl) {
    var rows = (u.equip || []).filter(function (e) { return e.slot === sl; });
    return { w: rows.filter(function (e) { return e.worn; })[0], n: rows.length };
  }
  function img(u) {
    if (!window.MineChar) return '';
    return '<img class="' + (MineChar.wide && MineChar.wide(u.lookRaw) ? 'wide' : '') + '" src="' + MineChar.dataUrl(u.lookRaw || {}, true) + '" alt="">';
  }
  function slot(u, sl, cls) {
    var o = wornOf(u, sl);
    return '<div class="slot' + (o.w ? ' on' : '') + (cls ? ' ' + cls : '') + '" title="' + esc(o.w ? o.w.effect : '') + '"><b>' + SLOTS[sl] + '</b><span>'
      + esc(o.w ? o.w.name : (o.n ? '벗음' : '없음')) + '</span></div>'; // 효과는 칸에 마우스를 올리면(title)
  }
  function meter(label, a, b, color) {
    return '<div class="bar-row"><div class="k"><span>' + label + '</span><span class="num">' + n(a) + ' / ' + n(b) + '</span></div>'
      + '<div class="meter"><i style="width:' + pct(a, b) + '%;background:' + color + '"></i></div></div>';
  }
  function invHtml(u) {
    var by = {};
    (u.ores || []).forEach(function (o) { var k = MineOre.cat(o.grade); (by[k] = by[k] || []).push(o); });
    var rows = MineOre.cats.map(function (c, k) { return [c[0], by[k]]; }).concat([['📦 그 밖', by[MineOre.cats.length]]]).filter(function (r) { return r[1] && r[1].length; });
    if (!rows.length) return '<div class="none">가진 게 없어요</div>';
    return rows.map(function (r) {
      return '<div class="inv-row"><b>' + r[0] + '</b><div class="inv-items">' + r[1].map(function (o) {
        return '<span class="it" title="' + esc(o.name) + '">' + MineOre.swatch(o.grade) + esc(o.name) + ' <b>' + n(o.qty) + '</b></span>';
      }).join('') + '</div></div>';
    }).join('');
  }
  function lvBadge(u) { return '<span class="lv' + (u.rebirth > 0 ? ' rb' : '') + '">Lv.' + u.lv + (u.rebirth > 0 ? ' ✦' + u.rebirth : '') + '</span>'; }
  function card(u) {
    var look = Object.keys(LOOK).filter(function (k) { return u.look && u.look[k]; }).map(function (k) { return LOOK[k] + ' <span>' + esc(u.look[k]) + '</span>'; }).join(' · ');
    return '<div class="ch' + (u.online ? ' on' : '') + '">'
      + '<div class="ch-head"><span class="dot" title="' + (u.online ? '광산 접속 중' : '접속 안 함') + '"></span><span class="ch-name">' + esc(u.userName) + '</span>' + lvBadge(u)
      +   '<span class="where">📍 <b>' + esc(u.mapName) + '</b> <span class="num">' + u.x + ',' + u.y + '</span> · ' + (u.online ? '<span style="color:var(--on)">접속 중</span>' : ago(u.lastAt)) + '</span></div>'
      + '<div class="body">'
      +   '<div class="doll"><div class="doll-col">' + slot(u, 'weapon') + slot(u, 'ring') + slot(u, 'gloves') + '</div>'
      +     '<div class="doll-char">' + img(u) + '<small>' + (u.gender === 1 ? '♀' : '♂') + ' Lv.' + u.lv + '</small></div>'
      +     '<div class="doll-col">' + slot(u, 'coat') + slot(u, 'necklace') + slot(u, 'boots') + '</div>' + slot(u, 'tool', 'tool') + '</div>'
      +   '<div class="stats">'
      +     meter('❤️ 체력', u.hp, u.hpMax, 'var(--hp)')
      +     meter('✨ 경험치', u.exp, u.expNeed, 'var(--exp)')
      +     meter('⛏️ 곡괭이', u.pickDur, u.pickMax, 'var(--dur)')
      +     '<div class="kv">'
      +       '<div><small>공격력</small><b class="num">' + n(u.atk) + '</b></div>'
      +       '<div><small>한기 방어</small><b class="num">' + n(u.coldDef) + '</b></div>'
      +       '<div><small>⛑️ 투구</small><b class="num">' + (u.helmet || 0) + '/' + (u.helmetMax || 0) + '</b></div>'
      +       '<div><small>채광</small><b class="num">' + n(u.totalDig) + '</b></div>'
      +       '<div><small>처치</small><b class="num">' + n(u.kills) + '</b></div>'
      +       '<div><small>치장</small><b class="num">' + n(u.cosOwned) + '</b></div>'
      +     '</div>'
      +     (u.dragonQuest === 1 ? '<div class="look">🌊 선장 퀘스트 진행 중</div>' : '')
      +     '<div class="look" title="' + esc(look.replace(/<[^>]+>/g, '')) + '">👗 ' + (look || '기본 외형') + '</div>'
      +   '</div>'
      + '</div>'
      + '<div class="inv">' + invHtml(u) + '</div>'
      + '</div>';
  }
  function tableHtml(list) {
    var top = function (u, cat) { // 분류 하나의 합계
      return (u.ores || []).filter(function (o) { return MineOre.cat(o.grade) === cat; }).reduce(function (s, o) { return s + Number(o.qty || 0); }, 0);
    };
    var mini = function (a, b, c) { return '<span class="mini"><i style="width:' + pct(a, b) + '%;background:' + c + '"></i></span>'; };
    return '<div class="tbl-wrap"><table class="tbl"><thead><tr><th></th><th>캐릭터</th><th class="r">Lv</th><th>위치</th><th class="r">체력</th><th class="r">경험치</th><th class="r">공격</th><th class="r">곡괭이</th>'
      + SLOT_ORDER.map(function (s) { return '<th>' + SLOTS[s] + '</th>'; }).join('')
      + '<th class="r">⛏️광석</th><th class="r">💎보석</th><th class="r">✨세공</th><th class="r">채광</th><th class="r">처치</th></tr></thead><tbody>'
      + list.map(function (u) {
          return '<tr class="' + (u.online ? 'on' : '') + '"><td>' + img(u) + '</td><td><b>' + esc(u.userName) + '</b></td><td class="r">' + lvBadge(u) + '</td>'
            + '<td>' + esc(u.mapName) + ' <span class="num" style="color:var(--ink-soft)">' + u.x + ',' + u.y + '</span> · ' + (u.online ? '<span style="color:var(--on)">접속 중</span>' : '<span style="color:var(--ink-soft)">' + ago(u.lastAt) + '</span>') + '</td>'
            + '<td class="r num">' + n(u.hp) + mini(u.hp, u.hpMax, 'var(--hp)') + '</td>'
            + '<td class="r num">' + Math.floor(pct(u.exp, u.expNeed)) + '%' + mini(u.exp, u.expNeed, 'var(--exp)') + '</td>'
            + '<td class="r num" style="color:var(--gold)">' + n(u.atk) + '</td>'
            + '<td class="r num">' + n(u.pickDur) + mini(u.pickDur, u.pickMax, 'var(--dur)') + '</td>'
            + SLOT_ORDER.map(function (s) { var o = wornOf(u, s); return '<td class="eq' + (o.w ? '' : ' off') + '" title="' + esc(o.w ? o.w.effect : '') + '">' + esc(o.w ? o.w.name : '-') + '</td>'; }).join('')
            + '<td class="r num">' + n(top(u, 0)) + '</td><td class="r num">' + n(top(u, 1)) + '</td><td class="r num">' + n(top(u, 3)) + '</td>'
            + '<td class="r num">' + n(u.totalDig) + '</td><td class="r num">' + n(u.kills) + '</td></tr>';
        }).join('')
      + '</tbody></table></div>';
  }
  function render() {
    var q = qs('q').value.trim().toLowerCase(), sort = qs('sort').value;
    var list = users.filter(function (u) { return !q || (String(u.userName) + ' ' + String(u.mapName)).toLowerCase().indexOf(q) >= 0; });
    list.sort(function (a, b) {
      if (a.online !== b.online) return a.online ? -1 : 1;
      if (sort === 'lv') return (b.rebirth || 0) - (a.rebirth || 0) || b.lv - a.lv || b.exp - a.exp; // [2026-10-28] 환생이 먼저
      if (sort === 'dig') return b.totalDig - a.totalDig;
      if (sort === 'kills') return b.kills - a.kills;
      if (sort === 'map') return String(a.mapName).localeCompare(String(b.mapName)) || b.lv - a.lv;
      return (b.lastAt || 0) - (a.lastAt || 0);
    });
    var on = users.filter(function (u) { return u.online; }).length;
    var maps = {};
    users.forEach(function (u) { if (u.online) maps[u.mapName] = (maps[u.mapName] || 0) + 1; });
    var mapTxt = Object.keys(maps).sort(function (a, b) { return maps[b] - maps[a]; }).map(function (m) { return esc(m) + ' ' + maps[m]; }).join(' · ');
    qs('meta').innerHTML = '<span>캐릭터 <b>' + users.length + '</b>명</span><span>🟢 접속 <b>' + on + '</b>명' + (mapTxt ? ' (' + mapTxt + ')' : '') + '</span>'
      + '<span>' + new Date(serverNow).toLocaleTimeString('ko-KR') + ' 기준</span>';
    qs('vCard').className = view === 'card' ? 'on' : '';
    qs('vTable').className = view === 'table' ? 'on' : '';
    if (!list.length) { qs('list').innerHTML = '<div class="empty">조건에 맞는 캐릭터가 없어요.</div>'; return; }
    qs('list').innerHTML = view === 'table' ? tableHtml(list) : '<div class="list">' + list.map(card).join('') + '</div>';
  }
  function setView(v) { view = v; try { localStorage.setItem('mineUsersView', v); } catch (e) {} render(); }
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
  qs('vCard').addEventListener('click', function () { setView('card'); });
  qs('vTable').addEventListener('click', function () { setView('table'); });
  load();
  schedule();
})();
</script>
</body>
</html>
