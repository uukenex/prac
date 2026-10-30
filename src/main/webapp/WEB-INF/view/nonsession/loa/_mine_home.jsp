<%@ page pageEncoding="UTF-8" %><%--
  [2026-10-30] "광산 접속 화면 -- 관리자 페이지에서 보이는 캐릭터 정보를 그대로, 아래 로그도 로그 뷰어와 똑같이".
  tower_view.jsp 광산 탭이 /api/tower-mine-home(내 현황 + 내 광산 기록 24시간 + 진행 중인 판)을 받아 MineHome.render(box, data)로 그린다.
  캐릭터 카드 = s5_mine_users_view.jsp의 카드, 기록 = s5_battle_log_view.jsp의 "광산 한 판" 카드(같은 sid 줄을 묶는다). 스타일은 .mh 안에만.
  필요: MineChar(_mine_char.jsp), MineOre(_mine_ore.jsp).
--%>
<style>
  .mh{ --bg:#14101e; --panel:#1d1730; --slot:#2a2340; --edge:rgba(244,240,230,.35); --edge-soft:rgba(244,240,230,.14);
       --ink:#f4f0e6; --ink-soft:#b9b0c9; --cream:#ffe9a8; --gold:#ffe14d; --on:#5fd38a; --hp:#e2524f; --exp:#ffcf4a; --dur:#5cb8ff; --mine:#c9b4ff; --red:#ff7a7a;
       color:var(--ink); display:flex; flex-direction:column; gap:10px; font-family:'Malgun Gothic','Segoe UI',sans-serif; }
  .mh .num{ font-family:ui-monospace, Consolas, monospace; font-variant-numeric:tabular-nums; }
  .mh-enter{ display:flex; gap:8px; align-items:center; justify-content:space-between; flex-wrap:wrap; background:var(--panel); border:2px solid var(--edge); padding:10px; }
  .mh-enter small{ color:var(--ink-soft); font-size:11.5px; line-height:1.45; flex:1 1 180px; }
  .mh-enter button{ background:#7a3e14; color:#fff; border:2px solid #f4f0e6; font-weight:800; font-size:15px; padding:10px 18px; cursor:pointer; font-family:inherit; }
  .mh .ch{ background:var(--panel); border:2px solid var(--edge); padding:10px; display:flex; flex-direction:column; gap:8px; min-width:0; }
  .mh .ch.on{ border-color:var(--on); }
  .mh .ch-head{ display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
  .mh .dot{ width:8px; height:8px; background:#5a5270; display:inline-block; flex-shrink:0; }
  .mh .on .dot{ background:var(--on); }
  .mh .ch-name{ font-weight:800; font-size:14px; word-break:break-all; }
  .mh .lv{ font-weight:800; font-size:12px; color:var(--gold); border:1px solid var(--edge); background:var(--slot); padding:1px 7px; white-space:nowrap; }
  .mh .lv.rb{ color:#ffb0f0; }
  .mh .where{ margin-left:auto; font-size:12px; color:var(--ink-soft); text-align:right; }
  .mh .where b{ color:var(--ink); }
  .mh .where .num{ color:var(--cream); }
  .mh .body{ display:grid; grid-template-columns:minmax(0, 1.25fr) minmax(0, 1fr); gap:8px; }
  @media (max-width:440px){ .mh .body{ grid-template-columns:1fr; } }
  .mh .doll{ display:grid; grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); gap:4px; align-items:stretch; }
  .mh .doll-col{ display:flex; flex-direction:column; gap:4px; }
  .mh .doll-char{ display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:4px; background:rgba(255,255,255,.05); border:2px solid var(--edge-soft); min-width:64px; }
  .mh .doll-char img{ height:52px; width:52px; image-rendering:pixelated; object-fit:contain; }
  .mh .doll-char img.wide{ width:auto; }
  .mh .doll-char small{ color:var(--cream); font-weight:800; font-size:11px; }
  .mh .slot{ background:var(--slot); border:2px solid var(--edge-soft); padding:3px 5px; min-width:0; flex:1; display:flex; flex-direction:column; justify-content:center; }
  .mh .slot.on{ border-color:#ffcf4a; }
  .mh .slot b{ font-size:10px; color:var(--ink-soft); font-weight:700; white-space:nowrap; }
  .mh .slot span{ font-size:11.5px; line-height:1.25; color:#8f86a3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; word-break:keep-all; overflow-wrap:anywhere; }
  .mh .slot.on span{ color:var(--cream); font-weight:700; }
  .mh .tool{ grid-column:1 / -1; flex-direction:row; align-items:center; gap:6px; }
  .mh .tool span{ flex:1; }
  .mh .stats{ display:flex; flex-direction:column; gap:5px; min-width:0; }
  .mh .bar-row{ font-size:11px; }
  .mh .bar-row .k{ display:flex; justify-content:space-between; gap:4px; color:var(--ink-soft); }
  .mh .bar-row .k .num{ color:var(--ink); }
  .mh .meter{ height:6px; background:#0d0a14; border:1px solid var(--edge-soft); margin-top:2px; }
  .mh .meter i{ display:block; height:100%; }
  .mh .kv{ display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:3px; }
  .mh .kv div{ background:var(--slot); border:1px solid var(--edge-soft); padding:2px 5px; min-width:0; }
  .mh .kv small{ display:block; font-size:9.5px; color:var(--ink-soft); }
  .mh .kv b{ font-size:12px; color:var(--gold); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; display:block; }
  .mh .inv{ display:flex; flex-direction:column; gap:3px; border-top:1px solid var(--edge-soft); padding-top:6px; }
  .mh .inv-row{ display:flex; gap:6px; align-items:flex-start; }
  .mh .inv-row > b{ font-size:10.5px; color:var(--ink-soft); width:62px; flex-shrink:0; padding-top:2px; white-space:nowrap; }
  .mh .inv-items, .mh .ore-items{ display:flex; flex-wrap:wrap; gap:3px; min-width:0; }
  .mh .it{ display:inline-flex; align-items:center; gap:3px; font-size:11.5px; background:rgba(255,255,255,.06); border:1px solid var(--edge-soft); padding:1px 5px 1px 3px; white-space:nowrap; }
  .mh .it b{ font-family:ui-monospace, Consolas, monospace; color:var(--gold); }
  .mh .it.plus b{ color:var(--on); } .mh .it.minus b{ color:var(--red); }
  .mh .ore-sw{ width:10px; height:10px; display:inline-block; transform:rotate(45deg) scale(.82); border:1px solid rgba(0,0,0,.6); flex-shrink:0; }
  .mh .ore-sw.cut{ box-shadow:0 0 0 1px #fff; }
  .mh .look{ font-size:11px; color:var(--ink-soft); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mh .look span{ color:var(--ink); }
  .mh .none{ font-size:11px; color:#6f6784; }
  .mh-logs-h{ font-size:13px; font-weight:800; color:var(--cream); margin-top:4px; background:var(--panel); border:2px solid var(--edge); padding:6px 10px; } /* 밝은 카드 위에서도 보이게 */
  .mh .log-item{ background:var(--panel); border:2px solid var(--edge-soft); padding:8px 10px; min-width:0; }
  .mh .sess{ border-color:var(--edge); } .mh .sess.live{ border-color:var(--on); }
  .mh .log-head{ display:flex; justify-content:space-between; gap:6px; flex-wrap:wrap; font-size:11px; color:var(--ink-soft); margin-bottom:4px; }
  .mh .log-head span:last-child{ font-family:ui-monospace, Consolas, monospace; }
  .mh .chip{ padding:1px 6px; font-weight:700; font-size:10px; border:1px solid currentColor; color:var(--mine); }
  .mh .chip.live{ color:var(--on); }
  .mh .sess-line{ font-size:11.5px; color:var(--ink-soft); word-break:break-all; display:flex; gap:10px; flex-wrap:wrap; }
  .mh .sess-line b{ color:var(--ink); font-weight:700; }
  .mh .sess-stats{ display:grid; grid-template-columns:repeat(auto-fill, minmax(76px, 1fr)); gap:3px; margin:5px 0; }
  .mh .sess-stats div{ background:var(--slot); border:1px solid var(--edge-soft); padding:2px 5px; }
  .mh .sess-stats small{ display:block; font-size:9.5px; color:var(--ink-soft); }
  .mh .sess-stats b{ font-size:12.5px; color:var(--gold); font-family:ui-monospace, Consolas, monospace; }
  .mh .ore-row{ display:flex; gap:6px; align-items:flex-start; margin:3px 0; }
  .mh .ore-row > b{ font-size:10.5px; color:var(--ink-soft); width:34px; flex-shrink:0; padding-top:2px; }
  .mh .buy-list, .mh .ev-list{ list-style:none; margin:4px 0 0; padding:0; font-size:11.5px; line-height:1.5; }
  .mh .buy-list{ border-top:1px solid var(--edge-soft); }
  .mh .ev-list{ max-height:300px; overflow:auto; }
  .mh .ev-list li{ padding:2px 0; border-bottom:1px dashed var(--edge-soft); word-break:break-all; }
  .mh .ev-t{ color:var(--ink-soft); font-family:ui-monospace, Consolas, monospace; margin-right:5px; }
  .mh details{ margin-top:4px; } .mh summary{ cursor:pointer; font-size:11.5px; font-weight:700; color:var(--mine); }
  .mh .sub{ color:var(--ink-soft); font-size:11px; }
</style>
<script>
window.MineHome = (function () {
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function n(v) { return Number(v || 0).toLocaleString('ko-KR'); }
  function pct(a, b) { return b > 0 ? Math.max(0, Math.min(100, a / b * 100)) : 0; }
  function pad2(x) { return (x < 10 ? '0' : '') + x; }
  function hms(ms) { var d = new Date(ms); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds()); }
  function mdhm(ms) { var d = new Date(ms); return pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()); }
  var GRADE_COL = ['#ffffff', '#4fd16a', '#4a9eff', '#b56cff', '#ff9a2e', '#ff4a4a', '#c8d0dc', '#ffd040'];
  var SLOTS = { weapon: '⚔️ 무기', ring: '💍 반지', gloves: '🧤 장갑', coat: '🧥 옷', necklace: '📿 목걸이', boots: '👢 신발', tool: '⛏️ 채집 도구' };
  var LOOK = { hat: '모자', hair: '머리', top: '상의', bottom: '하의', shoes: '신발' };
  function wornOf(u, sl) { var rows = (u.equip || []).filter(function (e) { return e.slot === sl; }); return { w: rows.filter(function (e) { return e.worn; })[0], n: rows.length }; }
  function slot(u, sl, cls) {
    var o = wornOf(u, sl), nm = o.w ? esc(o.w.name) : '없음';
    if (o.w && o.w.grade != null) nm = '<span style="color:' + GRADE_COL[o.w.grade] + '">' + nm + '</span>';
    return '<div class="slot' + (o.w ? ' on' : '') + (cls ? ' ' + cls : '') + '" title="' + esc(o.w ? o.w.effect : '') + '"><b>' + SLOTS[sl] + '</b><span>' + nm + '</span></div>';
  }
  function meter(label, a, b, color) {
    return '<div class="bar-row"><div class="k"><span>' + label + '</span><span class="num">' + n(a) + ' / ' + n(b) + '</span></div><div class="meter"><i style="width:' + pct(a, b) + '%;background:' + color + '"></i></div></div>';
  }
  function invHtml(u) {
    var by = {};
    (u.ores || []).forEach(function (o) { var k = MineOre.cat(o.grade); (by[k] = by[k] || []).push(o); });
    var rows = MineOre.cats.map(function (c, k) { return [c[0], by[k]]; }).concat([['📦 그 밖', by[MineOre.cats.length]]]).filter(function (r) { return r[1] && r[1].length; });
    if (!rows.length) return '<div class="none">가진 게 없어요</div>';
    return rows.map(function (r) {
      return '<div class="inv-row"><b>' + r[0] + '</b><div class="inv-items">' + r[1].map(function (o) { return '<span class="it">' + MineOre.swatch(o.grade) + esc(o.name) + ' <b>' + n(o.qty) + '</b></span>'; }).join('') + '</div></div>';
    }).join('');
  }
  function card(u) {
    var look = Object.keys(LOOK).filter(function (k) { return u.look && u.look[k]; }).map(function (k) { return LOOK[k] + ' <span>' + esc(u.look[k]) + '</span>'; }).join(' · ');
    var img = window.MineChar ? '<img class="' + (MineChar.wide && MineChar.wide(u.lookRaw) ? 'wide' : '') + '" src="' + MineChar.dataUrl(u.lookRaw || {}, true) + '" alt="">' : '';
    return '<div class="ch' + (u.online ? ' on' : '') + '">'
      + '<div class="ch-head"><span class="dot"></span><span class="ch-name">' + esc(u.userName) + '</span><span class="lv' + (u.rebirth > 0 ? ' rb' : '') + '">Lv.' + u.lv + (u.rebirth > 0 ? ' ✦' + u.rebirth : '') + '</span>'
      +   '<span class="where">📍 <b>' + esc(u.mapName) + '</b> <span class="num">' + u.x + ',' + u.y + '</span>' + (u.online ? ' · <span style="color:var(--on)">접속 중</span>' : '') + '</span></div>'
      + '<div class="body"><div class="doll"><div class="doll-col">' + slot(u, 'weapon') + slot(u, 'ring') + slot(u, 'gloves') + '</div>'
      +   '<div class="doll-char">' + img + '<small>' + (u.gender === 1 ? '♀' : '♂') + ' Lv.' + u.lv + '</small></div>'
      +   '<div class="doll-col">' + slot(u, 'coat') + slot(u, 'necklace') + slot(u, 'boots') + '</div>' + slot(u, 'tool', 'tool') + '</div>'
      + '<div class="stats">' + meter('❤️ 체력', u.hp, u.hpMax, 'var(--hp)') + meter('✨ 경험치', u.exp, u.expNeed, 'var(--exp)') + meter('⛏️ 곡괭이', u.pickDur, u.pickMax, 'var(--dur)')
      +   '<div class="kv"><div><small>공격력</small><b class="num">' + n(u.atk) + '</b></div><div><small>한기 방어</small><b class="num">' + n(u.coldDef) + '</b></div>'
      +   '<div><small>⛑️ 투구</small><b class="num">' + (u.helmet || 0) + '/' + (u.helmetMax || 0) + '</b></div><div><small>채광</small><b class="num">' + n(u.totalDig) + '</b></div>'
      +   '<div><small>처치</small><b class="num">' + n(u.kills) + '</b></div><div><small>치장</small><b class="num">' + n(u.cosOwned) + '</b></div></div>'
      +   '<div class="look">👗 ' + (look || '기본 외형') + '</div></div></div>'
      + '<div class="inv">' + invHtml(u) + '</div></div>';
  }
  // ---- 광산 한 판(로그 뷰어와 같은 묶음) ----
  var PREFIX = '[웹] 광산 기록', EV_ICON = { drop: '🗡️', 'in': '🚪', out: '🚪', dig: '⛏️', kill: '⚔️', boss: '👑', died: '💫', move: '🧭', act: '🛒', etc: '🔁' };
  function sessOf(it) {
    if (String(it.REQ || '').indexOf(PREFIX) !== 0) return null;
    try { var j = JSON.parse(it.RES); if (!j || !j.sid) return null; if (it.LIVE) j.live = true; return j; } catch (e) { return null; }
  }
  function oreItems(o, sign) {
    return Object.keys(o || {}).filter(function (k) { var v = o[k]; return v !== 0 && (sign > 0 ? v > 0 : v < 0); })
      .sort(function (x, y) { var gx = MineOre.byName(x), gy = MineOre.byName(y); return MineOre.cat(gx) - MineOre.cat(gy) || gx - gy; })
      .map(function (k) { var v = o[k], g = MineOre.byName(k); return '<span class="it ' + (v > 0 ? 'plus' : 'minus') + '">' + (g ? MineOre.swatch(g) : '') + esc(k) + ' <b>' + (v > 0 ? '+' : '−') + Math.abs(v) + '</b></span>'; }).join('');
  }
  function oreText(o) { return Object.keys(o || {}).filter(function (k) { return o[k]; }).map(function (k) { return esc(k) + ' ' + (o[k] > 0 ? '+' : '−') + Math.abs(o[k]); }).join(' · '); }
  function session(g) {
    var chunks = Object.keys(g.chunks).map(function (k) { return g.chunks[k]; }).sort(function (a, b) { return a.seq - b.seq; });
    var first = chunks[0], ev = [], endC = null, live = false;
    chunks.forEach(function (c) { ev = ev.concat(c.ev || []); if (c.end) endC = c; if (c.live) live = true; });
    if (endC) live = false;
    var dig = 0, kill = 0, died = 0, move = 0, exp = 0, lv = 0, wdrop = 0, gain = {}, spend = {}, buys = [];
    ev.forEach(function (e) {
      if (e.k === 'dig') dig += e.n || 0; else if (e.k === 'kill') kill += e.n || 0; else if (e.k === 'boss') kill++; else if (e.k === 'died') died++; else if (e.k === 'move') move++;
      exp += Number(e.x || 0); if (e.lv) lv = Math.max(lv, e.lv);
      var o = e.o || {}, neg = false;
      Object.keys(o).forEach(function (k) { if (o[k] > 0) gain[k] = (gain[k] || 0) + o[k]; else if (o[k] < 0) { spend[k] = (spend[k] || 0) + o[k]; neg = true; } });
      if (e.k === 'act' || e.k === 'drop' || neg) buys.push(e);
      if (e.k === 'drop') wdrop++;
    });
    var lastT = endC ? endC.end : (ev.length ? ev[ev.length - 1].t : first.start), mins = Math.max(0, Math.round((lastT - first.start) / 60000));
    var stats = [['⛏️ 채굴', dig], ['⚔️ 처치', kill], ['✨ 경험치', exp ? '+' + n(exp) : 0], ['⬆️ 레벨', lv ? 'Lv.' + lv : 0], ['💫 쓰러짐', died], ['🧭 이동', move], ['🛒 구매·사용', buys.length - wdrop], ['🗡️ 무기', wdrop]].filter(function (x) { return x[1]; });
    return '<div class="log-item sess' + (live ? ' live' : '') + '"><div class="log-head"><span><span class="chip">⛏️ 한 판</span>' + (live ? ' <span class="chip live">🟢 플레이 중</span>' : '') + '</span>'
      + '<span>' + mdhm(first.start) + ' ~ ' + (live ? '지금' : hms(lastT)) + ' (' + mins + '분)</span></div>'
      + '<div class="sess-line"><span>📍 <b>' + esc(first.sm || '-') + '</b></span>' + (endC ? '<span>🚪 <b>' + esc(endC.em || '-') + '</b> (' + esc(endC.why || '') + ')</span>' : '') + '</div>'
      + (stats.length ? '<div class="sess-stats">' + stats.map(function (x) { return '<div><small>' + x[0] + '</small><b>' + esc(x[1]) + '</b></div>'; }).join('') + '</div>' : '')
      + (Object.keys(gain).length ? '<div class="ore-row"><b>얻음</b><div class="ore-items">' + oreItems(gain, 1) + '</div></div>' : '')
      + (Object.keys(spend).length ? '<div class="ore-row"><b>씀</b><div class="ore-items">' + oreItems(spend, -1) + '</div></div>' : '')
      + (buys.length ? '<ul class="buy-list">' + buys.map(function (e) { var ot = oreText(e.o); return '<li><span class="ev-t">' + hms(e.t) + '</span>' + esc(e.a || '') + (e.m ? ' -- ' + esc(e.m) : '') + (ot ? ' <span class="sub">[' + ot + ']</span>' : '') + '</li>'; }).join('') + '</ul>' : '')
      + '<details><summary>자세히 보기 (' + ev.length + '건)</summary><ul class="ev-list">' + ev.map(function (e) {
          var ot = oreText(e.o), what = e.k === 'dig' ? '채굴 ' + (e.n || 0) + '회' : e.k === 'kill' ? '처치 ' + (e.n || 0) + '마리' + (e.x ? ' (경험치 +' + n(e.x) + ')' : '') + (e.lv ? ' Lv.' + e.lv + ' 달성' : '') : (e.a || e.k);
          return '<li><span class="ev-t">' + hms(e.t) + '</span>' + (EV_ICON[e.k] || '•') + ' ' + esc(what) + (e.m ? ' -- ' + esc(e.m) : '') + (ot ? '<br><span class="sub">' + ot + '</span>' : '') + '</li>';
        }).join('') + '</ul></details></div>';
  }
  function logsHtml(items) {
    var cards = [], groups = {};
    items.forEach(function (it) {
      var j = sessOf(it);
      if (!j) { cards.push(it); return; }
      var g = groups[j.sid];
      if (!g) { g = groups[j.sid] = { sess: true, chunks: {} }; cards.push(g); }
      var prev = g.chunks[j.seq];
      if (!prev || (prev.live && !j.live)) g.chunks[j.seq] = j;
    });
    if (!cards.length) return '<div class="none">최근 24시간 광산 기록이 없어요.</div>';
    return cards.map(function (it) {
      if (it.sess) return session(it);
      return '<div class="log-item"><div class="log-head"><span><span class="chip">⛏️ 광산</span></span><span>' + esc(it.INSERT_DATE) + '</span></div><div style="font-size:12px;font-weight:700;color:var(--cream)">' + esc(it.REQ) + '</div><div style="font-size:12px;white-space:pre-wrap;word-break:break-all">' + esc(it.RES) + '</div></div>';
    }).join('');
  }
  /** data = /api/tower-mine-home 결과, enter = 들어가기 버튼 onclick 문자열, info = 버튼 옆 안내(HTML). */
  function render(box, data, enter, info) {
    var h = '<div class="mh"><div class="mh-enter"><small>' + (info || '') + '</small><button type="button" onclick="' + enter + '">⛏️ 들어가기</button></div>';
    h += data && data.me ? card(data.me) : '<div class="none">아직 광산 기록이 없어요. 들어가서 캐 보세요!</div>';
    h += '<div class="mh-logs-h">📜 내 광산 기록 (최근 24시간)</div>' + logsHtml(((data && data.live) || []).concat((data && data.items) || [])) + '</div>';
    box.innerHTML = h;
  }
  return { render: render };
})();
</script>
