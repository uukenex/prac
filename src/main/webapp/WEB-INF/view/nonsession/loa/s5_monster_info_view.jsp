<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 몬스터 정보</title>
  <!-- [2026-09-30] "/로그 페이지에서 연결시킬 페이지 -- 몬스터 정보를 볼 수 있는 db뷰. 층별 몬스터 공격력/방어력/
       체력/드랍pp량/적정전투력 한눈에" 요청. SPA(tower_view.jsp) 밖 독립 관리자용 페이지. 데이터는
       GET /loa/api/tower-monster-info(실제 전투와 같은 계산 경로로 만든 1~200층 표). -->
  <style>
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC; --boss:#A31F2B; --boss-soft:#F3D6D6;
      --stair:#3C7CB8; --stair-soft:#DCEAF6; --pp:#2F8F5C; --pp-soft:#D6EEDF;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{margin:0; background:radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
         color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;}
    .wrap{max-width:1100px; margin:0 auto; padding:20px 16px 60px; display:flex; flex-direction:column; gap:14px;}
    h1{font-size:18px; font-weight:800; margin:4px 0 0;}
    .sub{font-size:12px; color:var(--ink-soft);}
    .nav{display:flex; gap:8px; flex-wrap:wrap; font-size:12px;}
    .nav a{color:var(--ink-soft); text-decoration:none; border:1.5px solid var(--line); background:#fff; padding:5px 12px; border-radius:999px; font-weight:700;}
    .nav a.on{background:var(--gold); border-color:var(--gold); color:#fff;}
    .card{background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line); border-radius:18px; padding:14px 16px; box-shadow:var(--shadow);}
    .ctrl{display:flex; gap:6px; flex-wrap:wrap; align-items:center;}
    .ctrl input,.ctrl select{padding:8px 12px; border:1.5px solid var(--line); border-radius:12px; background:#fff; font-size:13px;}
    .chip{background:#fff; color:var(--ink-soft); border:1.5px solid var(--line); border-radius:999px; padding:6px 13px; font-size:12px; font-weight:700; cursor:pointer;}
    .chip.on{background:var(--gold); color:#fff; border-color:var(--gold);}
    .tbl-wrap{overflow-x:auto; border-radius:14px; border:1.5px solid var(--line); background:#fff;}
    table{border-collapse:collapse; width:100%; min-width:760px; font-size:12.5px;}
    th{position:sticky; top:0; background:#F6EBCB; color:var(--ink); font-size:12px; padding:9px 8px; border-bottom:1.5px solid var(--line); cursor:pointer; white-space:nowrap; user-select:none;}
    th.sorted:after{content:" ▾"; color:var(--gold);} th.sorted.asc:after{content:" ▴";}
    td{padding:7px 8px; border-bottom:1px solid #F0E6C8; text-align:right; white-space:nowrap;}
    td.l,th.l{text-align:left;} td.c{text-align:center;}
    tr.boss td{background:var(--boss-soft);} tr.stair td{background:#F4F9FD;}
    tr:hover td{background:#FFF7DD;}
    .tag{display:inline-block; padding:1px 8px; border-radius:999px; font-size:10.5px; font-weight:800;}
    .tag.boss{background:var(--boss); color:#fff;} .tag.stair{background:var(--stair-soft); color:var(--stair);} .tag.n{background:var(--gold-soft); color:var(--gold);}
    .bar{position:relative; min-width:90px;} .bar i{position:absolute; left:0; top:2px; bottom:2px; background:rgba(198,138,46,.22); border-radius:4px;}
    .bar b{position:relative; font-weight:600;}
    .pp{color:var(--pp); font-weight:700;}
    .note{font-size:11.5px; color:var(--ink-soft); line-height:1.6;}
    .loading{text-align:center; color:var(--ink-soft); padding:30px 0;}
  </style>
</head>
<body>
<div class="wrap">
  <h1>👹 람쥐탑 몬스터 정보</h1>
  <div class="sub">층별 몬스터 스펙과 적정 전투력 (실제 전투와 같은 계산으로 만든 값 · 관리자용)</div>
  <div class="nav">
    <a href="/loa/tower-battle-log">📜 로그 뷰어</a>
    <a class="on" href="/loa/tower-monster-info">👹 몬스터 정보</a>
    <a href="/loa/tower-balance-stats">⚖️ 밸런스 통계</a>
  </div>

  <div class="card">
    <div class="ctrl" id="bandBtns"></div>
    <div class="ctrl" style="margin-top:8px;">
      <input type="text" id="floorInput" placeholder="층 번호 (예: 87)" style="width:140px;">
      <select id="kindSel"><option value="">종류 전체</option><option>일반</option><option>보스</option><option>계단층</option></select>
      <span class="sub" id="countLabel"></span>
    </div>
  </div>

  <div class="tbl-wrap">
    <table>
      <thead><tr id="headRow"></tr></thead>
      <tbody id="body"><tr><td colspan="9" class="loading">불러오는 중...</td></tr></tbody>
    </table>
  </div>
  <div class="note">
    · <b>적정전투력</b> = 그 층 몬스터 1마리의 전투력(체력×1 + 공격력×10 + 방어력×8, /탑현황과 같은 식). <b>안전기준</b> = 적정전투력 × <span id="safeRatio">1.3</span> — 파티 전투력이 이 이상이면 추천 사냥터.<br>
    · 공격력은 보스층의 추가 배율(×1.6)까지 반영한 값입니다. 중간보스(×3)/강화몹(×2)은 이 표의 스펙에 배율만 곱해집니다.<br>
    · 처치 PP는 층 배율(<code>floorPpMultiplier</code>)까지 반영한 1마리 기본 보상입니다(자동사냥 추가 배율/몬스터 2마리 조우 미반영).<br>
    · 계단층(101~199층)은 층마다 일반 몬스터 + 중간보스 칸(스펙 ×3)으로 구성됩니다.
  </div>
</div>

<script>
(function () {
  var rows = [], band = 'all', sortKey = 'floor', sortAsc = true;
  var COLS = [
    { k: 'floor', t: '층', cls: 'c' }, { k: 'kind', t: '종류', cls: 'c' }, { k: 'name', t: '몬스터', cls: 'l' },
    { k: 'hp', t: '체력', bar: true }, { k: 'atk', t: '공격력', bar: true }, { k: 'def', t: '방어력', bar: true },
    { k: 'ppBase', t: '처치 PP', pp: true }, { k: 'power', t: '적정전투력', bar: true }, { k: 'safe', t: '안전기준(×1.3)', bar: true }
  ];
  function qs(id) { return document.getElementById(id); }
  function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function num(n) { return Number(n).toLocaleString('ko-KR'); }

  function buildHead() {
    qs('headRow').innerHTML = COLS.map(function (c) {
      return '<th class="' + (c.cls === 'l' ? 'l ' : '') + (sortKey === c.k ? 'sorted' + (sortAsc ? ' asc' : '') : '') + '" data-k="' + c.k + '">' + c.t + '</th>';
    }).join('');
    Array.prototype.forEach.call(qs('headRow').children, function (th) {
      th.onclick = function () {
        var k = th.getAttribute('data-k');
        if (sortKey === k) sortAsc = !sortAsc; else { sortKey = k; sortAsc = true; }
        buildHead(); render();
      };
    });
  }

  function buildBands() {
    var html = '<button class="chip on" data-b="all">전체</button>';
    for (var b = 0; b < 20; b++) html += '<button class="chip" data-b="' + b + '">' + (b * 10 + 1) + '~' + (b * 10 + 10) + '</button>';
    qs('bandBtns').innerHTML = html;
    Array.prototype.forEach.call(qs('bandBtns').children, function (btn) {
      btn.onclick = function () {
        band = btn.getAttribute('data-b');
        Array.prototype.forEach.call(qs('bandBtns').children, function (x) { x.classList.remove('on'); });
        btn.classList.add('on'); render();
      };
    });
  }

  function render() {
    var fq = qs('floorInput').value.trim(), kq = qs('kindSel').value;
    var list = rows.filter(function (r) {
      if (band !== 'all' && Math.floor((r.floor - 1) / 10) !== parseInt(band, 10)) return false;
      if (fq && String(r.floor) !== fq) return false;
      if (kq && r.kind !== kq) return false;
      return true;
    });
    list.sort(function (a, b) {
      var x = a[sortKey], y = b[sortKey];
      var c = (typeof x === 'string') ? String(x).localeCompare(String(y), 'ko') : (x - y);
      return sortAsc ? c : -c;
    });
    // 막대 기준(전체 최댓값 대비 비율)
    var max = {};
    COLS.forEach(function (c) { if (c.bar) max[c.k] = Math.max.apply(null, rows.map(function (r) { return r[c.k]; })) || 1; });
    qs('countLabel').textContent = list.length + '개 층';
    if (!list.length) { qs('body').innerHTML = '<tr><td colspan="9" class="loading">조건에 맞는 층이 없습니다.</td></tr>'; return; }
    qs('body').innerHTML = list.map(function (r) {
      var trc = r.kind === '보스' ? 'boss' : (r.kind === '계단층' ? 'stair' : '');
      var tag = r.kind === '보스' ? '<span class="tag boss">보스</span>' : (r.kind === '계단층' ? '<span class="tag stair">계단</span>' : '<span class="tag n">일반</span>');
      var tds = COLS.map(function (c) {
        if (c.k === 'floor') return '<td class="c"><b>' + r.floor + '</b></td>';
        if (c.k === 'kind') return '<td class="c">' + tag + '</td>';
        if (c.k === 'name') return '<td class="l">' + esc(r.name) + '</td>';
        if (c.pp) return '<td class="pp">' + esc(r.pp) + '</td>';
        var pct = Math.max(2, Math.round(r[c.k] / max[c.k] * 100));
        return '<td class="bar"><i style="width:' + pct + '%"></i><b>' + num(r[c.k]) + '</b></td>';
      }).join('');
      return '<tr class="' + trc + '">' + tds + '</tr>';
    }).join('');
  }

  qs('floorInput').addEventListener('input', render);
  qs('kindSel').addEventListener('change', render);
  buildBands(); buildHead();
  fetch('/loa/api/tower-monster-info').then(function (r) { return r.json(); }).then(function (d) {
    rows = d.rows || [];
    if (d.safeRatio) qs('safeRatio').textContent = d.safeRatio;
    render();
  }).catch(function () { qs('body').innerHTML = '<tr><td colspan="9" class="loading">불러오지 못했습니다.</td></tr>'; });
})();
</script>
</body>
</html>
