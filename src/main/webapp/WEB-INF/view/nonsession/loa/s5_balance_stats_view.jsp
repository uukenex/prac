<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 밸런스 통계</title>
  <!-- [2026-09-30] "현재 밸런스가 잘 맞는지 실시간 통계자료 -- 직업 조합별로 전투력 당 어디 부분에서 많이 동료가
       쓰러져서 실패하는지, 어디 부분까지 쉽게 올라가는지" 요청. 전투가 끝날 때마다(승리/전멸/도망)
       TBOT_S5_BATTLE_STAT에 쌓이는 로그를 집계(GET /loa/api/tower-balance-stats). 층/조합/전투력비율별
       집계는 서버가 한 번에 내려주고, 축을 바꿔 보는 재집계·필터는 전부 프론트 JS. -->
  <style>
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC;
      --ok:#2F8F5C; --warn:#C6982E; --bad:#D8523F; --wall:#8E1F1F;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{margin:0; background:radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
         color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;}
    .wrap{max-width:1100px; margin:0 auto; padding:20px 16px 60px; display:flex; flex-direction:column; gap:14px;}
    h1{font-size:18px; font-weight:800; margin:4px 0 0;} h2{font-size:14px; font-weight:800; margin:0 0 4px;}
    .sub{font-size:12px; color:var(--ink-soft);}
    .nav{display:flex; gap:8px; flex-wrap:wrap; font-size:12px;}
    .nav a{color:var(--ink-soft); text-decoration:none; border:1.5px solid var(--line); background:#fff; padding:5px 12px; border-radius:999px; font-weight:700;}
    .nav a.on{background:var(--gold); border-color:var(--gold); color:#fff;}
    .card{background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line); border-radius:18px; padding:14px 16px; box-shadow:var(--shadow);}
    .ctrl{display:flex; gap:6px; flex-wrap:wrap; align-items:center;}
    .ctrl select,.ctrl button.act{padding:7px 12px; border:1.5px solid var(--line); border-radius:12px; background:#fff; font-size:13px;}
    .chip{background:#fff; color:var(--ink-soft); border:1.5px solid var(--line); border-radius:999px; padding:6px 13px; font-size:12px; font-weight:700; cursor:pointer;}
    .chip.on{background:var(--gold); color:#fff; border-color:var(--gold);}
    .cards{display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:10px;}
    .kpi{background:#fff; border:1.5px solid var(--line); border-radius:14px; padding:10px 12px;}
    .kpi .v{font-size:20px; font-weight:800;} .kpi .t{font-size:11.5px; color:var(--ink-soft);}
    .insight{background:#fff; border:1.5px dashed var(--gold); border-radius:14px; padding:10px 14px; font-size:13px; line-height:1.7;}
    .insight b.ok{color:var(--ok);} .insight b.bad{color:var(--bad);}
    .tbl-wrap{overflow-x:auto; border-radius:12px; border:1.5px solid var(--line); background:#fff;}
    table{border-collapse:collapse; width:100%; font-size:12.5px; min-width:560px;}
    th{background:#F6EBCB; font-size:12px; padding:8px; border-bottom:1.5px solid var(--line); white-space:nowrap;}
    td{padding:7px 8px; border-bottom:1px solid #F0E6C8; text-align:right; white-space:nowrap;}
    td.l,th.l{text-align:left;} tr.click{cursor:pointer;} tr.click:hover td{background:#FFF7DD;} tr.sel td{background:#FFF0C4;}
    .rate{position:relative; min-width:120px; text-align:left;} .rate i{position:absolute; left:0; top:3px; bottom:3px; border-radius:4px; opacity:.28;}
    .rate b{position:relative; font-weight:700; margin-left:6px;}
    .badge{display:inline-block; padding:1px 8px; border-radius:999px; font-size:10.5px; font-weight:800; color:#fff;}
    .b-easy{background:var(--ok);} .b-mid{background:var(--warn);} .b-hard{background:var(--bad);} .b-wall{background:var(--wall);} .b-low{background:#B8B2C4;}
    .heat td.h{text-align:center; font-size:11.5px; font-weight:700; min-width:54px; padding:6px 2px;}
    .note{font-size:11.5px; color:var(--ink-soft); line-height:1.6;}
    .empty{text-align:center; color:var(--ink-soft); padding:30px 8px; line-height:1.8;}
  </style>
</head>
<body>
<div class="wrap">
  <h1>⚖️ 람쥐탑 밸런스 통계</h1>
  <div class="sub">전투가 끝날 때마다(승리/전멸/도망) 쌓이는 실제 플레이 기록 기준 · 테스트/관리자 계정 제외 · 관리자용</div>
  <div class="nav">
    <a href="/loa/tower-battle-log">📜 로그 뷰어</a>
    <a href="/loa/tower-monster-info">👹 몬스터 정보</a>
    <a class="on" href="/loa/tower-balance-stats">⚖️ 밸런스 통계</a>
  </div>

  <div class="card">
    <div class="ctrl">
      <button class="chip" data-d="1">최근 1일</button>
      <button class="chip" data-d="3">3일</button>
      <button class="chip on" data-d="7">7일</button>
      <button class="chip" data-d="30">30일</button>
      <select id="comboSel"><option value="">직업 조합 전체</option></select>
      <label class="sub"><input type="checkbox" class="kindChk" value="N" checked> 일반</label>
      <label class="sub"><input type="checkbox" class="kindChk" value="E" checked> 강화</label>
      <label class="sub"><input type="checkbox" class="kindChk" value="M" checked> 중간보스</label>
      <label class="sub"><input type="checkbox" class="kindChk" value="B" checked> 보스</label>
      <button class="act" id="refreshBtn">새로고침</button>
    </div>
  </div>

  <div id="content"><div class="empty">불러오는 중...</div></div>

  <div class="note">
    · <b>전멸률</b> = 전멸한 전투 / 전체 전투(도망 포함). <b>쓰러진 동료</b>는 전투가 끝났을 때 HP 0인 파티원 수(전멸이면 파티 전원).<br>
    · <b>전투력 비율</b> = 그 전투 시점 파티 전투력 ÷ 그 몬스터 전투력(강화 ×2·중간보스 ×3·보스 공격력 ×1.6 반영). 비율이 높을수록 여유 있는 전투입니다.<br>
    · 판정: 전멸률 8% 이하 <span class="badge b-easy">쉬움</span> · 20% 이하 <span class="badge b-mid">보통</span> · 35% 이하 <span class="badge b-hard">어려움</span> · 초과 <span class="badge b-wall">벽</span> · 표본 5회 미만은 <span class="badge b-low">표본부족</span>.<br>
    · 기록은 이 기능이 배포된 이후 전투부터 쌓입니다(이전 전투는 없음).
  </div>
</div>

<script>
(function () {
  var JOB_KR = { WARRIOR: '전사', MAGE: '마법사', ROGUE: '도적', ARCHER: '궁수', PRIEST: '도사' };
  var raw = [], days = 7, comboSel = '';
  var MIN_N = 5;
  function qs(id) { return document.getElementById(id); }
  function comboLabel(c) { return String(c || '').split('-').map(function (j) { return JOB_KR[j] || j; }).join('·'); }
  function pct(a, b) { return b ? (a / b * 100) : 0; }
  function f1(x) { return (Math.round(x * 10) / 10).toFixed(1); }
  function grade(rate, n) {
    if (n < MIN_N) return { c: 'b-low', t: '표본부족', col: '#B8B2C4' };
    if (rate <= 8) return { c: 'b-easy', t: '쉬움', col: '#2F8F5C' };
    if (rate <= 20) return { c: 'b-mid', t: '보통', col: '#C6982E' };
    if (rate <= 35) return { c: 'b-hard', t: '어려움', col: '#D8523F' };
    return { c: 'b-wall', t: '벽', col: '#8E1F1F' };
  }
  function heatColor(rate, n) {
    if (!n) return '#F4F1EA';
    var a = Math.min(1, 0.18 + rate / 45);
    return 'rgba(216,82,63,' + a.toFixed(2) + ')';
  }
  function bandOf(f) { return Math.floor((f - 1) / 10); }
  function bandLabel(b) { return (b * 10 + 1) + '~' + (b * 10 + 10) + '층'; }

  function kinds() {
    var set = {};
    Array.prototype.forEach.call(document.querySelectorAll('.kindChk'), function (x) { if (x.checked) set[x.value] = true; });
    return set;
  }
  function filtered(ignoreCombo) {
    var ks = kinds();
    return raw.filter(function (r) { return ks[r.KIND] && (ignoreCombo || !comboSel || r.COMBO === comboSel); });
  }
  function agg(list, keyFn) {
    var m = {};
    list.forEach(function (r) {
      var k = keyFn(r); if (k === null) return;
      var o = m[k] || (m[k] = { key: k, n: 0, wipe: 0, flee: 0, dead: 0, turns: 0 });
      o.n += r.FIGHTS; o.wipe += r.WIPES; o.flee += r.FLEES; o.dead += r.DEADS; o.turns += r.TURNS;
    });
    return m;
  }
  function rateCell(rate, n) {
    var g = grade(rate, n);
    return '<td class="rate"><i style="width:' + Math.min(100, rate * 2) + '%;background:' + g.col + '"></i><b>' + f1(rate) + '%</b></td>';
  }

  function render() {
    var list = filtered(false);
    if (!raw.length) {
      qs('content').innerHTML = '<div class="card empty">아직 쌓인 전투 기록이 없습니다.<br>이 기능이 배포된 이후 전투(승리/전멸/도망)부터 기록되며, 테스트/관리자 계정은 제외됩니다.</div>';
      return;
    }
    var tot = agg(list, function () { return 'all'; }).all || { n: 0, wipe: 0, flee: 0, dead: 0, turns: 0 };
    var html = '<div class="cards">'
      + kpi('전투 횟수', tot.n) + kpi('전멸률', f1(pct(tot.wipe, tot.n)) + '%') + kpi('도망', tot.flee)
      + kpi('평균 쓰러진 동료', f1(tot.n ? tot.dead / tot.n : 0) + '명') + kpi('평균 턴', f1(tot.n ? tot.turns / tot.n : 0)) + '</div>';

    // ---- 층 구간별 ----
    var bands = agg(list, function (r) { return bandOf(r.FLOOR); });
    var bandKeys = Object.keys(bands).map(Number).sort(function (a, b) { return a - b; });
    var easyTop = -1, wallFrom = -1, wallTo = -1;
    for (var i = 0; i < bandKeys.length; i++) {
      var o = bands[bandKeys[i]], g = grade(pct(o.wipe, o.n), o.n);
      if (g.c === 'b-easy') easyTop = bandKeys[i];
      else if (g.c === 'b-low') continue;
      else break; // 처음으로 쉽지 않은 구간이 나오면 거기서 끊음(연속 구간만 "쉽게 올라가는 구간")
    }
    bandKeys.forEach(function (b) { var o = bands[b], g = grade(pct(o.wipe, o.n), o.n); if (g.c === 'b-wall' || g.c === 'b-hard') { if (wallFrom === -1) wallFrom = b; wallTo = b; } });
    var ins = [];
    if (easyTop >= 0) ins.push('쉽게 올라가는 구간: <b class="ok">~' + (easyTop * 10 + 10) + '층</b>까지 (전멸률 8% 이하 연속)');
    if (wallFrom >= 0) ins.push('전멸이 몰리는 구간: <b class="bad">' + bandLabel(wallFrom) + (wallTo !== wallFrom ? ' ~ ' + bandLabel(wallTo) : '') + '</b> (전멸률 20% 초과)');
    html += '<div class="insight"><b>' + (comboSel ? comboLabel(comboSel) : '전체 조합') + '</b> 요약 · ' + (ins.length ? ins.join(' · ') : '표본이 더 쌓이면 표시됩니다.') + '</div>';

    html += '<div class="card"><h2>📍 층 구간별 (10층 단위)</h2><div class="tbl-wrap"><table><thead><tr><th class="l">구간</th><th>전투</th><th class="l">전멸률</th><th>도망</th><th>평균 쓰러진 동료</th><th>평균 턴</th><th>판정</th></tr></thead><tbody>'
      + bandKeys.map(function (b) {
        var o = bands[b], r = pct(o.wipe, o.n), g = grade(r, o.n);
        return '<tr><td class="l"><b>' + bandLabel(b) + '</b></td><td>' + o.n + '</td>' + rateCell(r, o.n) + '<td>' + o.flee + '</td><td>' + f1(o.dead / o.n) + '</td><td>' + f1(o.turns / o.n) + '</td><td><span class="badge ' + g.c + '">' + g.t + '</span></td></tr>';
      }).join('') + '</tbody></table></div></div>';

    // ---- 전투력 비율별 ----
    var ratio = agg(list, function (r) { return r.BUCKET < 0 ? null : r.BUCKET; });
    var rk = Object.keys(ratio).map(Number).sort(function (a, b) { return a - b; });
    var safeAt = null;
    rk.forEach(function (b) { var o = ratio[b]; if (safeAt === null && o.n >= MIN_N && pct(o.wipe, o.n) <= 20) safeAt = b; });
    html += '<div class="card"><h2>⚡ 전투력 비율별 (파티 ÷ 몬스터)</h2><div class="sub" style="margin-bottom:6px;">'
      + (safeAt !== null ? '전멸률 20% 이하가 되는 최소 비율: <b>약 ' + safeAt + '배 이상</b>' : '전멸률 20% 이하 구간을 찾을 만큼 표본이 쌓이지 않았습니다.') + '</div>'
      + '<div class="tbl-wrap"><table><thead><tr><th class="l">전투력 비율</th><th>전투</th><th class="l">전멸률</th><th>평균 쓰러진 동료</th><th>판정</th></tr></thead><tbody>'
      + rk.map(function (b) {
        var o = ratio[b], r = pct(o.wipe, o.n), g = grade(r, o.n);
        var lab = b >= 8 ? '8배 이상' : (b + '~' + (b + 0.5) + '배');
        return '<tr><td class="l"><b>' + lab + '</b></td><td>' + o.n + '</td>' + rateCell(r, o.n) + '<td>' + f1(o.dead / o.n) + '</td><td><span class="badge ' + g.c + '">' + g.t + '</span></td></tr>';
      }).join('') + '</tbody></table></div></div>';

    // ---- 직업 조합별 (조합 필터 무시) ----
    var all = filtered(true);
    var combos = agg(all, function (r) { return r.COMBO; });
    var ck = Object.keys(combos).sort(function (a, b) { return combos[b].n - combos[a].n; });
    html += '<div class="card"><h2>🎭 직업 조합별</h2><div class="sub" style="margin-bottom:6px;">행을 누르면 위 표들이 그 조합만으로 다시 계산됩니다(한 번 더 누르면 해제).</div>'
      + '<div class="tbl-wrap"><table><thead><tr><th class="l">조합</th><th>전투</th><th class="l">전멸률</th><th>평균 쓰러진 동료</th><th>쉽게 오르는 층</th><th>전멸 몰리는 층</th></tr></thead><tbody>'
      + ck.map(function (c) {
        var o = combos[c], r = pct(o.wipe, o.n);
        var cb = agg(all.filter(function (x) { return x.COMBO === c; }), function (x) { return bandOf(x.FLOOR); });
        var keys = Object.keys(cb).map(Number).sort(function (a, b) { return a - b; });
        var top = -1, wall = -1;
        for (var j = 0; j < keys.length; j++) { var q = cb[keys[j]], gg = grade(pct(q.wipe, q.n), q.n); if (gg.c === 'b-easy') top = keys[j]; else if (gg.c !== 'b-low') break; }
        for (var j2 = 0; j2 < keys.length; j2++) { var q2 = cb[keys[j2]], g2 = grade(pct(q2.wipe, q2.n), q2.n); if (g2.c === 'b-wall' || g2.c === 'b-hard') { wall = keys[j2]; break; } }
        return '<tr class="click' + (c === comboSel ? ' sel' : '') + '" data-c="' + c + '"><td class="l"><b>' + comboLabel(c) + '</b></td><td>' + o.n + '</td>' + rateCell(r, o.n)
          + '<td>' + f1(o.dead / o.n) + '</td><td>' + (top >= 0 ? '~' + (top * 10 + 10) + '층' : '-') + '</td><td>' + (wall >= 0 ? bandLabel(wall) + '~' : '-') + '</td></tr>';
      }).join('') + '</tbody></table></div></div>';

    // ---- 히트맵: 상위 조합 x 층 구간 ----
    var top8 = ck.slice(0, 8);
    var hb = {}; all.forEach(function (r) { hb[bandOf(r.FLOOR)] = 1; });
    var hbk = Object.keys(hb).map(Number).sort(function (a, b) { return a - b; });
    if (top8.length && hbk.length) {
      html += '<div class="card heat"><h2>🔥 조합 × 층 구간 전멸률 히트맵 (전투 상위 8개 조합)</h2><div class="tbl-wrap"><table style="min-width:0"><thead><tr><th class="l">조합</th>'
        + hbk.map(function (b) { return '<th>' + (b * 10 + 1) + '~</th>'; }).join('') + '</tr></thead><tbody>'
        + top8.map(function (c) {
          var cb = agg(all.filter(function (x) { return x.COMBO === c; }), function (x) { return bandOf(x.FLOOR); });
          return '<tr><td class="l"><b>' + comboLabel(c) + '</b></td>' + hbk.map(function (b) {
            var q = cb[b]; if (!q) return '<td class="h" style="background:#F4F1EA;color:#B8B2C4">-</td>';
            var r = pct(q.wipe, q.n);
            return '<td class="h" title="' + comboLabel(c) + ' ' + bandLabel(b) + ' · ' + q.n + '회" style="background:' + heatColor(r, q.n) + '">' + Math.round(r) + '%<br><span style="font-weight:400;font-size:10px">' + q.n + '회</span></td>';
          }).join('') + '</tr>';
        }).join('') + '</tbody></table></div></div>';
    }
    qs('content').innerHTML = html;
    Array.prototype.forEach.call(document.querySelectorAll('tr.click'), function (tr) {
      tr.onclick = function () { var c = tr.getAttribute('data-c'); comboSel = (comboSel === c) ? '' : c; qs('comboSel').value = comboSel; render(); };
    });
  }
  function kpi(t, v) { return '<div class="kpi"><div class="v">' + v + '</div><div class="t">' + t + '</div></div>'; }

  function fillCombos() {
    var m = {}; raw.forEach(function (r) { m[r.COMBO] = (m[r.COMBO] || 0) + r.FIGHTS; });
    var keys = Object.keys(m).sort(function (a, b) { return m[b] - m[a]; });
    qs('comboSel').innerHTML = '<option value="">직업 조합 전체</option>' + keys.map(function (c) { return '<option value="' + c + '">' + comboLabel(c) + ' (' + m[c] + ')</option>'; }).join('');
    qs('comboSel').value = comboSel;
  }
  function load() {
    qs('content').innerHTML = '<div class="empty">불러오는 중...</div>';
    fetch('/loa/api/tower-balance-stats?days=' + days).then(function (r) { return r.json(); }).then(function (d) {
      raw = (d.rows || []).map(function (r) {
        return { COMBO: r.COMBO, FLOOR: +r.FLOOR, KIND: r.KIND, BUCKET: +r.BUCKET, FIGHTS: +r.FIGHTS, WIPES: +r.WIPES, FLEES: +r.FLEES, DEADS: +r.DEADS, TURNS: +r.TURNS };
      });
      fillCombos(); render();
    }).catch(function () { qs('content').innerHTML = '<div class="card empty">불러오지 못했습니다.</div>'; });
  }

  Array.prototype.forEach.call(document.querySelectorAll('.chip[data-d]'), function (b) {
    b.onclick = function () {
      Array.prototype.forEach.call(document.querySelectorAll('.chip[data-d]'), function (x) { x.classList.remove('on'); });
      b.classList.add('on'); days = parseFloat(b.getAttribute('data-d')); load();
    };
  });
  qs('comboSel').onchange = function () { comboSel = this.value; render(); };
  Array.prototype.forEach.call(document.querySelectorAll('.kindChk'), function (x) { x.onchange = render; });
  qs('refreshBtn').onclick = load;
  load();
})();
</script>
</body>
</html>
