<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 로그 뷰어</title>
  <!-- [2026-09-22] "전체 web포함 전투로그를 유저별로 볼수있게 페이지도 구성해줘. 이건 spa에
       넣지않고 별도페이지로 구성할거야" 요청 -- tower_view.jsp(SPA)와 완전히 분리된 독립
       페이지. 색상 팔레트만 tower_view.jsp의 :root 변수를 그대로 재사용해 톤을 맞추고,
       마크업/JS는 이 페이지 전용으로 새로 작성.
       [2026-09-22 후속] "/로그 입력하면 랜딩, 유저명은 콤보박스(전체가 디폴트), 5분/15분/
       60분/3시간/24시간 버튼은 DB 재조회 없이 필터링으로" 요청으로 전면 재설계 -- 페이지
       진입 시 자동으로 GET /api/tower-battle-log-recent(전체 유저, 최근 24시간)를 한 번만
       불러오고, 시간대 버튼/검색어는 그 결과셋 안에서 순수 JS 필터링만 한다(추가 API 호출
       없음). 유저 콤보박스를 바꿀 때만 새로 서버에 요청. 기존 페이지네이션 API
       (/api/tower-battle-log)는 남겨뒀지만 이 페이지는 더 이상 안 씀. -->
  <style>
    /* [2026-10-28] "로그 페이지도 리뉴얼 -- 인게임과 비슷하게, 한눈에 정보가 많이" -- 광산 화면 창과 같은 어두운 판 + 금색 숫자,
       넓은 화면에선 왼쪽(필터 + 사용자 요약, 고정) | 오른쪽(로그 2열) 배치. */
    :root{
      --bg:#14101e; --panel:#1d1730; --slot:#2a2340; --edge:rgba(244,240,230,.35); --edge-soft:rgba(244,240,230,.14);
      --ink:#f4f0e6; --ink-soft:#b9b0c9; --cream:#ffe9a8; --gold:#ffe14d; --on:#5fd38a; --red:#ff7a7a; --mine:#c9b4ff;
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{ margin:0; background:var(--bg); color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif; }
    .wrap{ max-width:1400px; margin:0 auto; padding:14px 16px 60px; display:flex; flex-direction:column; gap:12px; }
    h1{ font-size:17px; font-weight:800; margin:0; display:flex; align-items:center; gap:8px; flex-wrap:wrap; padding-right:150px; }
    h1 a{ font-size:12px; font-weight:700; color:var(--cream); text-decoration:none; border:2px solid var(--edge); padding:4px 10px; background:var(--slot); }
    .sub{ font-size:12px; color:var(--ink-soft); margin:-6px 0 0; }
    .layout{ display:grid; grid-template-columns:1fr; gap:12px; align-items:start; }
    @media (min-width:980px){
      .layout{ grid-template-columns:360px minmax(0,1fr); }
      .side{ position:sticky; top:10px; max-height:calc(100vh - 20px); overflow:auto; }
      .sum-scroll{ max-height:none !important; }
    }
    .side{ display:flex; flex-direction:column; gap:10px; min-width:0; }
    .main{ display:flex; flex-direction:column; gap:8px; min-width:0; }

    .card{ background:var(--panel); border:2px solid var(--edge); padding:10px; }

    .search-row{ display:flex; gap:6px; flex-wrap:wrap; }
    .search-row input, .search-row select{ padding:7px 10px; border:2px solid var(--edge); background:var(--slot); color:var(--ink); font-size:13px; flex:1 1 130px; min-width:0; font-family:inherit; }
    .search-row button{ background:var(--slot); color:var(--gold); border:2px solid var(--gold); padding:7px 14px; font-size:13px; font-weight:700; cursor:pointer; flex-shrink:0; font-family:inherit; }
    .search-row button:active, .time-btn:active{ transform:scale(.96); }

    .time-row{ display:flex; gap:4px; flex-wrap:wrap; }
    .time-btn{ background:var(--slot); color:var(--ink-soft); border:2px solid var(--edge-soft); padding:4px 9px; font-size:12px; font-weight:700; cursor:pointer; font-family:inherit; }
    .time-btn.active{ color:var(--gold); border-color:var(--gold); }
    button:focus-visible, input:focus-visible, select:focus-visible{ outline:2px solid var(--gold); outline-offset:1px; }

    .sum-head{ display:flex; justify-content:space-between; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:6px; }
    .sum-title{ font-size:13px; font-weight:800; color:var(--cream); }
    .sum-table{ width:100%; border-collapse:collapse; font-size:12px; }
    .sum-table th{ text-align:left; font-size:11px; color:var(--ink-soft); font-weight:700; padding:4px 5px; border-bottom:2px solid var(--edge); }
    .sum-table td{ padding:3px 5px; border-bottom:1px solid var(--edge-soft); }
    .sum-table th.num, .sum-table td.num{ text-align:right; font-family:ui-monospace, Consolas, monospace; font-variant-numeric:tabular-nums; }
    .sum-table td.num b{ color:var(--gold); }
    .sum-row{ cursor:pointer; }
    .sum-row:hover{ background:rgba(255,255,255,.05); }
    .sum-row.active{ background:rgba(95,211,138,.14); box-shadow:inset 3px 0 0 var(--on); }
    .sum-sub{ color:var(--ink-soft); font-size:11px; }
    .sum-scroll{ max-height:280px; overflow-y:auto; }

    .meta-row{ display:flex; justify-content:space-between; align-items:center; font-size:12px; color:var(--ink-soft); flex-wrap:wrap; gap:4px; }

    .log-list{ display:grid; grid-template-columns:repeat(auto-fill, minmax(min(100%, 430px), 1fr)); gap:8px; align-items:start; }
    .log-empty{ text-align:center; color:var(--ink-soft); font-size:13px; padding:30px 0; grid-column:1 / -1; }
    .log-item{ background:var(--panel); border:2px solid var(--edge-soft); padding:8px 10px; min-width:0; }
    .log-item-head{ display:flex; justify-content:space-between; align-items:center; gap:6px; margin-bottom:4px; font-size:11px; color:var(--ink-soft); flex-wrap:wrap; }
    .log-item-head .who{ font-weight:800; color:var(--ink); font-size:12.5px; }
    .log-item-head > span:last-child{ font-family:ui-monospace, Consolas, monospace; }
    .log-chip{ display:inline-block; padding:1px 6px; font-weight:700; font-size:10px; border:1px solid currentColor; }
    .log-chip.web{ color:var(--on); }
    .log-chip.chat{ color:var(--cream); }
    .log-chip.mine{ color:var(--mine); }
    /* [2026-10-25] 광산 캐릭터 그림(16x16 도트) */
    .mchar{ width:30px; height:30px; image-rendering:pixelated; vertical-align:middle; background:rgba(255,255,255,.05); border:1px solid var(--edge-soft); }
    .sum-table .mchar{ width:22px; height:22px; margin-right:4px; }
    .mchar.wide, .sum-table .mchar.wide{ width:auto; } /* [2026-10-28] 환생 날개(그림이 넓다) -- 높이만 맞추고 비율 유지 */
    .who-wrap{ display:inline-flex; align-items:center; gap:5px; flex-wrap:wrap; }
    .cat-row .time-btn{ font-size:13px; padding:6px 12px; }
    .log-req{ font-weight:700; font-size:12.5px; margin-bottom:3px; word-break:break-all; color:var(--cream); }
    .log-res{ font-size:12px; color:var(--ink); white-space:pre-wrap; word-break:break-all; line-height:1.45; }

    /* [2026-10-28] 광산 한 판(입장 -> 한 일 -> 퇴장) 묶음 카드 */
    .sess{ border-color:var(--edge); }
    .sess.live{ border-color:var(--on); }
    .live-chip{ color:var(--on) !important; }
    .sess-line{ font-size:11.5px; color:var(--ink-soft); word-break:break-all; display:flex; gap:10px; flex-wrap:wrap; }
    .sess-line b{ color:var(--ink); font-weight:700; }
    .sess-stats{ display:grid; grid-template-columns:repeat(auto-fill, minmax(76px, 1fr)); gap:3px; margin:5px 0; }
    .sess-stats div{ background:var(--slot); border:1px solid var(--edge-soft); padding:2px 5px; }
    .sess-stats small{ display:block; font-size:9.5px; color:var(--ink-soft); }
    .sess-stats b{ font-size:12.5px; color:var(--gold); font-family:ui-monospace, Consolas, monospace; }
    .ore-row{ display:flex; gap:6px; align-items:flex-start; margin:3px 0; }
    .ore-row > b{ font-size:10.5px; color:var(--ink-soft); width:34px; flex-shrink:0; padding-top:2px; }
    .ore-items{ display:flex; flex-wrap:wrap; gap:3px; min-width:0; }
    .it{ display:inline-flex; align-items:center; gap:3px; font-size:11.5px; background:rgba(255,255,255,.06); border:1px solid var(--edge-soft); padding:1px 5px 1px 3px; white-space:nowrap; }
    .it b{ font-family:ui-monospace, Consolas, monospace; }
    .ore-plus, .it.plus b{ color:var(--on); font-weight:700; }
    .ore-minus, .it.minus b{ color:var(--red); font-weight:700; }
    .ore-sw{ width:10px; height:10px; display:inline-block; transform:rotate(45deg) scale(.82); border:1px solid rgba(0,0,0,.6); flex-shrink:0; }
    .ore-sw.cut{ box-shadow:0 0 0 1px #fff; }
    .buy-list{ list-style:none; margin:4px 0 0; padding:0; font-size:11.5px; line-height:1.5; border-top:1px solid var(--edge-soft); }
    .buy-list li{ padding:2px 0; word-break:break-all; }
    .sess details{ margin-top:4px; }
    .sess summary{ cursor:pointer; font-size:11.5px; font-weight:700; color:var(--mine); }
    .ev-list{ list-style:none; margin:4px 0 0; padding:0; font-size:11.5px; line-height:1.45; max-height:320px; overflow:auto; }
    .ev-list li{ padding:2px 0; border-bottom:1px dashed var(--edge-soft); word-break:break-all; }
    .ev-t{ color:var(--ink-soft); font-family:ui-monospace, Consolas, monospace; margin-right:5px; }

    .loading{ text-align:center; color:var(--ink-soft); font-size:13px; padding:20px 0; }
  </style>
</head>
<body>
<!-- [2026-09-30] 시즌5 카카오 로그인: 세션 만료(API 401) 시 로그인 화면으로, 우상단에 닉네임/로그아웃 표시. -->
<script>
(function () {
  var of = window.fetch;
  window.fetch = function () {
    return of.apply(this, arguments).then(function (r) {
      if (r.status === 401) location.href = '/loa/s5/login?next=' + encodeURIComponent(location.pathname);
      return r;
    });
  };
  of('/loa/s5/me', { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
    if (!d || !d.loggedIn) return;
    var el = document.createElement('div');
    el.style.cssText = 'position:fixed;top:8px;right:10px;z-index:50;font-size:11px;background:#2a2340;border:2px solid rgba(244,240,230,.35);padding:3px 8px;color:#b9b0c9;';
    el.appendChild(document.createTextNode('🟡 ' + (d.nickname || '') + ' · '));
    var a = document.createElement('a');
    a.href = '/loa/s5/logout'; a.textContent = '로그아웃'; a.style.cssText = 'color:#ff7a7a;font-weight:700;text-decoration:none;';
    el.appendChild(a);
    document.body.appendChild(el);
  }).catch(function () {});
})();
</script>

  <div class="wrap">
    <h1>📜 람쥐탑 로그 뷰어 <a href="/loa/tower-mine-users">⛏️ 광산 캐릭터 현황 →</a></h1>
    <div class="sub">채팅 명령어 + 웹 액션을 유저별로 조회(관리자용, SPA와 별도 페이지) -- 최근 24시간 이내만</div>

    <div class="layout"><div class="side">
    <div class="card">
      <!-- [2026-10-24] "광산 로그랑 탑 로그 분리, 필터 버튼도 분리" 요청 -- 아래 목록과 사용자 요약 모두 이 구분을 따른다 -->
      <div class="time-row cat-row" id="catRow" style="margin-bottom:8px;">
        <!-- [2026-10-25] "광산이 먼저 나오게" -- 광산 로그를 첫 버튼이자 기본 선택으로 -->
        <button type="button" class="time-btn active" data-cat="mine">⛏️ 광산 로그</button>
        <button type="button" class="time-btn" data-cat="tower">🗼 탑 로그</button>
        <button type="button" class="time-btn" data-cat="all">전체</button>
      </div>
      <div class="search-row">
        <select id="userNameSelect"><option value="">전체 유저</option></select>
        <input type="text" id="keywordInput" placeholder="검색어(선택, 요청/응답 부분일치)">
        <button type="button" id="refreshBtn">새로고침</button>
      </div>
      <div class="time-row" id="timeRow" style="margin-top:8px;">
        <button type="button" class="time-btn" data-min="5">최근 5분</button>
        <button type="button" class="time-btn" data-min="15">최근 15분</button>
        <button type="button" class="time-btn" data-min="60">최근 60분</button>
        <button type="button" class="time-btn" data-min="180">최근 3시간</button>
        <button type="button" class="time-btn active" data-min="1440">최근 24시간</button>
      </div>
    </div>

    <div class="card">
      <div class="sum-head">
        <span class="sum-title">👥 최근 명령어 사용자 (사람당 사용 횟수)</span>
        <div class="time-row" id="sumRow">
          <button type="button" class="time-btn" data-h="1">최근 1시간</button>
          <button type="button" class="time-btn active" data-h="24">최근 24시간</button>
          <button type="button" class="time-btn" data-h="72">최근 3일</button>
        </div>
      </div>
      <div class="sum-scroll" id="sumBody"><div class="log-empty">불러오는 중...</div></div>
      <div class="sum-sub" style="margin-top:6px;">사용자 줄을 누르면 그 유저의 로그(최근 24시간)를 보여줍니다. 광산은 한 판을 몇 줄로 묶어 저장해서 행동 수가 아니라 저장된 줄 수예요(2026-10-28부터).</div>
    </div>
    </div><div class="main">

    <div class="meta-row" id="metaRow" style="display:none;">
      <span id="totalLabel"></span>
      <span id="fetchedLabel"></span>
    </div>

    <div id="loadingEl" class="loading" style="display:none;">불러오는 중...</div>
    <div class="log-list" id="logList"></div>
    </div></div>
  </div>

  <%@ include file="_mine_char.jsp" %>
  <%@ include file="_mine_ore.jsp" %>
  <script>
  (function () {
    var base = ''; // 같은 오리진(/loa/...)이라 상대경로로 충분.
    // [2026-10-25] "광산 로그 보여주는 곳에 캐릭터 이미지" -- 광산 유저별 외형을 한 번 받아 MineChar(_mine_char.jsp, 광산 화면과 같은 그림)로 그린다.
    var looks = {};
    function face(name) {
      var lk = looks[name];
      // [2026-10-28] 환생 날개까지(날개가 있으면 그림이 넓어 .mchar.wide로 비율 유지)
      return lk && window.MineChar ? '<img class="mchar' + (MineChar.wide && MineChar.wide(lk) ? ' wide' : '') + '" src="' + MineChar.dataUrl(lk, true) + '" alt="" title="' + escapeHtml(name) + (lk.rebirth > 0 ? ' (환생' + lk.rebirth + ')' : '') + '">' : '';
    }
    // 서버에서 한 번 불러온 "최근 24시간" 전체 로그(선택된 유저 기준). 시간대 버튼/검색어는
    // 전부 이 배열 안에서 필터링만 한다(추가 fetch 없음).
    var allItems = [];
    var state = { userName: '', windowMin: 1440, keyword: '', cat: 'mine' };
    // [2026-10-24] 로그 구분: 광산 = ROOM_NAME 'MINE'(이후) 또는 예전에 'WEB'으로 쌓인 광산 로그(요청이 "[웹] 광산"으로 시작),
    //  웹 = 그 밖의 'WEB'(탑 웹 액션), 채팅 = 그 외 방 이름. 탑 로그 = 채팅 + 웹.
    function catOf(it) {
      if (it.ROOM_NAME === 'MINE' || (it.ROOM_NAME === 'WEB' && String(it.REQ || '').indexOf('[웹] 광산') === 0)) return 'mine';
      return it.ROOM_NAME === 'WEB' ? 'web' : 'chat';
    }
    // 최근 명령어 사용자 요약(서버에서 3일치를 한 번에 집계해 받아오고, 1시간/24시간/3일 전환은 JS로만).
    var sumUsers = [];
    var sumHours = 24;

    function qs(id) { return document.getElementById(id); }

    function escapeHtml(s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // INSERT_DATE는 서버가 'YYYY-MM-DD HH24:MI:SS'(Oracle DB 서버 로컬시간)로 내려준다 --
    // 공백을 'T'로 바꿔주면 브라우저가 로컬시간대 Date로 파싱한다(같은 서버 시간대라 그대로 비교 가능).
    function parseLogDate(s) {
      if (!s) return null;
      var t = new Date(s.replace(' ', 'T'));
      return isNaN(t.getTime()) ? null : t;
    }

    function loadUserOptions(preselect) {
      fetch(base + '/loa/api/tower-battle-log-users').then(function (r) { return r.json(); }).then(function (data) {
        var sel = qs('userNameSelect');
        (data.userNames || []).forEach(function (u) {
          var opt = document.createElement('option');
          opt.value = u; opt.textContent = u;
          sel.appendChild(opt);
        });
        // [버그 방지] 옵션이 다 채워지기 전에 select.value를 세팅하면(비동기라 순서 보장 안 됨)
        // 아직 없는 option이라 무시되고 "전체 유저"로 되돌아간다 -- 옵션 추가가 끝난 뒤에 세팅.
        if (preselect) sel.value = preselect;
      }).catch(function () { /* 콤보박스 채우기 실패는 조용히 무시(전체 유저 옵션은 이미 있음) */ });
    }

    function sumKey(h) { return h === 1 ? '1H' : (h === 24 ? '24H' : '3D'); }

    function renderSummary() {
      var k = sumKey(sumHours);
      var rows = sumUsers.map(function (u) {
        var all = Number(u['CNT_' + k] || 0), web = Number(u['WEB_' + k] || 0), mine = Number(u['MINE_' + k] || 0), chat = all - web - mine;
        var total = state.cat === 'mine' ? mine : state.cat === 'tower' ? chat + web : all;
        return { name: u.USER_NAME, total: total, web: web, chat: chat, mine: mine, last: u.LAST_DATE };
      }).filter(function (r) { return r.total > 0; });
      rows.sort(function (a, b) { return b.total - a.total || (a.last < b.last ? 1 : -1); });
      var body = qs('sumBody');
      if (rows.length === 0) { body.innerHTML = '<div class="log-empty">이 기간에 명령어를 사용한 사람이 없습니다.</div>'; return; }
      var totalAll = rows.reduce(function (s, r) { return s + r.total; }, 0);
      var showTower = state.cat !== 'mine', showMine = state.cat !== 'tower';
      body.innerHTML = '<table class="sum-table"><thead><tr><th>유저</th><th class="num">합계</th>'
        + (showTower ? '<th class="num">채팅</th><th class="num">웹</th>' : '') + (showMine ? '<th class="num">광산</th>' : '') + '<th>마지막 사용</th></tr></thead><tbody>'
        + rows.map(function (r) {
          return '<tr class="sum-row' + (state.userName === r.name ? ' active' : '') + '" data-user="' + escapeHtml(r.name).replace(/"/g, '&quot;') + '">'
            + '<td>' + (state.cat === 'mine' ? face(r.name) : '') + escapeHtml(r.name) + '</td><td class="num"><b>' + r.total + '회</b></td>'
            + (showTower ? '<td class="num">' + r.chat + '</td><td class="num">' + r.web + '</td>' : '')
            + (showMine ? '<td class="num">' + r.mine + '</td>' : '')
            + '<td class="sum-sub">' + escapeHtml((r.last || '').substring(5, 16)) + '</td></tr>';
        }).join('')
        + '</tbody></table><div class="sum-sub" style="margin-top:6px;">' + rows.length + '명 · 총 ' + totalAll + '회</div>';
      Array.prototype.forEach.call(body.querySelectorAll('.sum-row'), function (tr) {
        tr.addEventListener('click', function () {
          var name = tr.getAttribute('data-user');
          state.userName = name;
          qs('userNameSelect').value = name;
          renderSummary();
          fetchFromServer();
        });
      });
    }

    function fetchSummary() {
      qs('sumBody').innerHTML = '<div class="log-empty">불러오는 중...</div>';
      fetch(base + '/loa/api/tower-battle-log-summary').then(function (r) { return r.json(); }).then(function (data) {
        sumUsers = data.users || [];
        renderSummary();
      }).catch(function () {
        qs('sumBody').innerHTML = '<div class="log-empty">요약 조회 중 오류가 발생했습니다.</div>';
      });
    }

    // ---- [2026-10-28] 광산 한 판(접속 기록) -- 서버(S5MineSessionLog)가 한 판을 몇 줄로 나눠 쓴다:
    //  REQ "[웹] 광산 기록 #sid ...", RES = JSON {sid, seq, start, sm(입장 위치), ev[{t, k, a, m, n, x, lv, o{이름: 증감}}], end, em, why}.
    //  k: in 입장 / out 퇴장 / dig 채굴(n회 묶음) / kill 처치(n마리 묶음, x 경험치, lv 레벨 업) / boss 보스 처치 / died 쓰러짐 / move 이동 / act 구매·제작·강화 등 / etc 광석 변동.
    var SESS_PREFIX = '[웹] 광산 기록';
    var EV_ICON = { 'in': '🚪', out: '🚪', dig: '⛏️', kill: '⚔️', boss: '👑', died: '💫', move: '🧭', act: '🛒', etc: '🔁' };
    function sessOf(it) {
      if (catOf(it) !== 'mine' || String(it.REQ || '').indexOf(SESS_PREFIX) !== 0) return null;
      try {
        var j = JSON.parse(it.RES);
        if (!j || !j.sid) return null;
        if (it.LIVE) j.live = true;
        return j;
      } catch (e) { return null; }
    }
    function pad2(n) { return (n < 10 ? '0' : '') + n; }
    function hms(ms) { var d = new Date(ms); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds()); }
    function mdhm(ms) { var d = new Date(ms); return pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()); }
    function addOre(acc, o) { Object.keys(o || {}).forEach(function (k) { acc[k] = (acc[k] || 0) + Number(o[k] || 0); }); }
    // [2026-10-28] 얻음/씀을 광산 인벤토리처럼(색 보석 + 수량, 분류 순서대로)
    function oreItems(o, sign) {
      return Object.keys(o || {}).filter(function (k) { var v = o[k]; return v !== 0 && (sign > 0 ? v > 0 : v < 0); })
        .sort(function (x, y) { var gx = MineOre.byName(x), gy = MineOre.byName(y); return MineOre.cat(gx) - MineOre.cat(gy) || gx - gy; })
        .map(function (k) { var v = o[k], g = MineOre.byName(k); return '<span class="it ' + (v > 0 ? 'plus' : 'minus') + '">' + (g ? MineOre.swatch(g) : '') + escapeHtml(k) + ' <b>' + (v > 0 ? '+' : '−') + Math.abs(v) + '</b></span>'; })
        .join('');
    }
    function oreText(o, sign) { // sign: 1 = 얻은 것만, -1 = 쓴 것만, 0 = 둘 다
      return Object.keys(o || {}).filter(function (k) { var v = o[k]; return v !== 0 && (sign === 0 || (sign > 0 ? v > 0 : v < 0)); })
        .map(function (k) { var v = o[k]; return '<span class="' + (v > 0 ? 'ore-plus' : 'ore-minus') + '">' + escapeHtml(k) + ' ' + (v > 0 ? '+' : '−') + Math.abs(v) + '</span>'; })
        .join(' · ');
    }
    function renderSession(g) {
      var chunks = Object.keys(g.chunks).map(function (k) { return g.chunks[k]; }).sort(function (a, b) { return a.seq - b.seq; });
      var first = chunks[0], ev = [], endC = null, live = false, dropped = 0;
      chunks.forEach(function (c) { ev = ev.concat(c.ev || []); if (c.end) endC = c; if (c.live) live = true; dropped += Number(c.dropped || 0); });
      if (endC) live = false;
      var dig = 0, kill = 0, died = 0, move = 0, exp = 0, lv = 0, gain = {}, spend = {}, buys = [];
      ev.forEach(function (e) {
        if (e.k === 'dig') dig += e.n || 0;
        else if (e.k === 'kill') kill += e.n || 0;
        else if (e.k === 'boss') kill++;
        else if (e.k === 'died') died++;
        else if (e.k === 'move') move++;
        exp += Number(e.x || 0);
        if (e.lv) lv = Math.max(lv, e.lv);
        var o = e.o || {}, neg = false;
        Object.keys(o).forEach(function (k) { if (o[k] > 0) gain[k] = (gain[k] || 0) + o[k]; else if (o[k] < 0) { spend[k] = (spend[k] || 0) + o[k]; neg = true; } });
        if (e.k === 'act' || neg) buys.push(e);
      });
      var lastT = endC ? endC.end : (ev.length ? ev[ev.length - 1].t : first.start);
      var mins = Math.max(0, Math.round((lastT - first.start) / 60000));
      var head = '<div class="log-item-head"><span class="who-wrap">' + face(g.user) + (state.userName ? '' : '<span class="who">' + escapeHtml(g.user) + '</span>')
        + '<span class="log-chip mine">⛏️ 한 판</span>' + (live ? '<span class="log-chip live-chip">🟢 플레이 중</span>' : '') + '</span>'
        + '<span>' + escapeHtml(mdhm(first.start)) + ' ~ ' + (live ? '지금' : escapeHtml(hms(lastT))) + ' (' + mins + '분)</span></div>';
      // [2026-10-28] 한 일은 숫자 칸으로(인게임 창처럼 금색 숫자), 0인 칸은 뺀다
      var stats = [['⛏️ 채굴', dig], ['⚔️ 처치', kill], ['✨ 경험치', exp ? '+' + exp : 0], ['⬆️ 레벨', lv ? 'Lv.' + lv : 0], ['💫 쓰러짐', died], ['🧭 이동', move], ['🛒 구매·사용', buys.length]]
        .filter(function (x) { return x[1]; });
      var body = '<div class="sess-line"><span>📍 <b>' + escapeHtml(first.sm || '-') + '</b></span>'
        + (endC ? '<span>🚪 <b>' + escapeHtml(endC.em || '-') + '</b> (' + escapeHtml(endC.why || '') + ')</span>' : '') + '<span>#' + escapeHtml(first.sid) + '</span></div>'
        + (stats.length ? '<div class="sess-stats">' + stats.map(function (x) { return '<div><small>' + x[0] + '</small><b>' + escapeHtml(x[1]) + '</b></div>'; }).join('') + '</div>' : '')
        + (Object.keys(gain).length ? '<div class="ore-row"><b>얻음</b><div class="ore-items">' + oreItems(gain, 1) + '</div></div>' : '')
        + (Object.keys(spend).length ? '<div class="ore-row"><b>씀</b><div class="ore-items">' + oreItems(spend, -1) + '</div></div>' : '')
        + (buys.length ? '<ul class="buy-list">' + buys.map(function (e) {
            var ot = oreText(e.o, 0);
            return '<li><span class="ev-t">' + hms(e.t) + '</span>' + escapeHtml(e.a || '') + (e.m ? ' -- ' + escapeHtml(e.m) : '') + (ot ? ' <span class="sum-sub">[' + ot + ']</span>' : '') + '</li>';
          }).join('') + '</ul>' : '')
        + '<details><summary>자세히 보기 (' + ev.length + '건' + (dropped ? ', 너무 많아 앞 ' + dropped + '건 생략' : '') + ')</summary><ul class="ev-list">'
        + ev.map(function (e) {
            var ot = oreText(e.o, 0);
            var what = e.k === 'dig' ? '채굴 ' + (e.n || 0) + '회' : e.k === 'kill' ? '처치 ' + (e.n || 0) + '마리' + (e.x ? ' (경험치 +' + e.x + ')' : '') + (e.lv ? ' Lv.' + e.lv + ' 달성' : '') : (e.a || e.k);
            return '<li><span class="ev-t">' + hms(e.t) + '</span>' + (EV_ICON[e.k] || '•') + ' ' + escapeHtml(what)
              + (e.m ? ' -- ' + escapeHtml(e.m) : '') + (ot ? '<br><span class="sum-sub">' + ot + '</span>' : '') + '</li>';
          }).join('') + '</ul></details>';
      return '<div class="log-item sess' + (live ? ' live' : '') + '">' + head + body + '</div>';
    }

    function renderItems() {
      var now = Date.now();
      var windowMs = state.windowMin * 60 * 1000;
      var kw = state.keyword.toLowerCase();
      var filtered = allItems.filter(function (it) {
        var d = parseLogDate(it.INSERT_DATE);
        if (!it.LIVE && d && (now - d.getTime()) > windowMs) return false; // 진행 중 세션은 시간 필터와 무관
        var cat = catOf(it);
        if (state.cat === 'mine' ? cat !== 'mine' : state.cat === 'tower' ? cat === 'mine' : false) return false;
        if (kw) {
          var hay = ((it.REQ || '') + ' ' + (it.RES || '')).toLowerCase();
          if (hay.indexOf(kw) === -1) return false;
        }
        return true;
      });

      // [2026-10-28] 광산 한 판 묶기 -- 같은 유저·같은 sid의 "[웹] 광산 기록" 줄(과 진행 중인 부분)을 카드 하나로. 카드는 가장 최근 줄 자리에 놓는다.
      var cards = [], groups = {};
      filtered.forEach(function (it) {
        var j = sessOf(it);
        if (!j) { cards.push(it); return; }
        var key = it.USER_NAME + '|' + j.sid, g = groups[key];
        if (!g) { g = groups[key] = { sess: true, user: it.USER_NAME, chunks: {} }; cards.push(g); }
        var prev = g.chunks[j.seq];
        if (!prev || (prev.live && !j.live)) g.chunks[j.seq] = j; // 같은 조각이면 DB에 쓴 쪽
      });

      var list = qs('logList');
      if (cards.length === 0) {
        list.innerHTML = '<div class="log-empty">표시할 로그가 없습니다.</div>';
      } else {
        list.innerHTML = cards.map(function (it) {
          if (it.sess) return renderSession(it);
          var cat = catOf(it);
          var chip = cat === 'mine' ? '<span class="log-chip mine">⛏️ 광산</span>' : cat === 'web' ? '<span class="log-chip web">WEB</span>'
              : '<span class="log-chip chat">' + escapeHtml(it.ROOM_NAME || '채팅') + '</span>';
          var who = state.userName ? '' : '<span class="who">' + escapeHtml(it.USER_NAME) + '</span>';
          return '<div class="log-item">'
            + '<div class="log-item-head"><span class="who-wrap">' + (cat === 'mine' ? face(it.USER_NAME) : '') + who + chip + '</span>' + '<span>' + escapeHtml(it.INSERT_DATE) + '</span></div>'
            + '<div class="log-req">' + escapeHtml(it.REQ) + '</div>'
            + '<div class="log-res">' + escapeHtml(it.RES) + '</div>'
            + '</div>';
        }).join('');
      }

      qs('metaRow').style.display = '';
      qs('totalLabel').textContent = '표시 ' + cards.length + '건' + (cards.length !== filtered.length ? ' (광산 한 판은 묶어서 1건)' : '');
      qs('fetchedLabel').textContent = '(전체 불러온 ' + allItems.length + '건 중, 최근 24시간)';
    }

    function fetchFromServer() {
      qs('loadingEl').style.display = '';
      qs('logList').innerHTML = '';
      qs('metaRow').style.display = 'none';

      var url = base + '/loa/api/tower-battle-log-recent?userName=' + encodeURIComponent(state.userName) + '&hours=24&cat=' + state.cat;
      fetch(url).then(function (r) { return r.json(); }).then(function (data) {
        qs('loadingEl').style.display = 'none';
        allItems = (data.live || []).concat(data.items || []); // [2026-10-28] 진행 중인 광산 한 판(아직 안 쓴 부분)도 함께
        renderItems();
      }).catch(function () {
        qs('loadingEl').style.display = 'none';
        qs('logList').innerHTML = '<div class="log-empty">조회 중 오류가 발생했습니다.</div>';
      });
    }

    qs('userNameSelect').addEventListener('change', function () {
      state.userName = this.value;
      fetchFromServer(); // 유저가 바뀌면 서버 데이터 자체가 달라지므로 이때만 재조회
    });
    qs('refreshBtn').addEventListener('click', function () { fetchFromServer(); fetchSummary(); });
    Array.prototype.forEach.call(document.querySelectorAll('#sumRow .time-btn'), function (btn) {
      btn.addEventListener('click', function () {
        document.querySelectorAll('#sumRow .time-btn').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        sumHours = parseInt(btn.getAttribute('data-h'), 10);
        renderSummary(); // 이미 받아온 3일치 집계 안에서만 전환(서버 재조회 없음)
      });
    });
    Array.prototype.forEach.call(document.querySelectorAll('#catRow .time-btn'), function (btn) {
      btn.addEventListener('click', function () {
        document.querySelectorAll('#catRow .time-btn').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        state.cat = btn.getAttribute('data-cat');
        renderSummary();
        fetchFromServer(); // 광산 로그가 많아 개수 상한에 밀리지 않게 구분별로 서버에서 다시 받는다
      });
    });
    qs('keywordInput').addEventListener('input', function () {
      state.keyword = this.value.trim();
      renderItems();
    });
    Array.prototype.forEach.call(document.querySelectorAll('#timeRow .time-btn'), function (btn) {
      btn.addEventListener('click', function () {
        document.querySelectorAll('#timeRow .time-btn').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        state.windowMin = parseInt(btn.getAttribute('data-min'), 10);
        renderItems(); // 이미 불러온 데이터 안에서만 필터링(서버 재조회 없음)
      });
    });

    // URL에 ?userName=... 이 있으면 그 유저로 바로 조회(다른 페이지에서 링크 타고 들어올 때 대비).
    // 없으면(예: /로그 명령어로 랜딩) 기본값 "전체 유저"로 최근 24시간을 바로 보여준다.
    var qp = new URLSearchParams(window.location.search);
    var initialUser = qp.get('userName') || '';
    state.userName = initialUser;

    loadUserOptions(initialUser);
    fetchFromServer();
    fetchSummary();
    fetch(base + '/loa/api/tower-mine-looks').then(function (r) { return r.json(); }).then(function (d) {
      looks = (d && d.looks) || {};
      if (sumUsers.length) renderSummary();
      if (allItems.length) renderItems();
    }).catch(function () { /* 그림이 없어도 로그는 그대로 보인다 */ });
  })();
  </script>
</body>
</html>
