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
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC;
      --pp:#2F8F5C; --pp-soft:#D6EEDF;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{
      margin:0;
      background: radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
      color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;
    }
    .wrap{ max-width:820px; margin:0 auto; padding:20px 16px 60px; display:flex; flex-direction:column; gap:14px; }

    h1{ font-size:18px; font-weight:800; margin:4px 0 0; display:flex; align-items:center; gap:6px; }
    .sub{ font-size:12px; color:var(--ink-soft); margin:0 0 4px; }

    .card{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line);
           border-radius:18px; padding:14px 16px; box-shadow:var(--shadow); }

    .search-row{ display:flex; gap:8px; flex-wrap:wrap; }
    .search-row input, .search-row select{ padding:9px 13px; border:1.5px solid var(--line); border-radius:14px;
                        background:#fff; font-size:13px; flex:1 1 140px; min-width:0; }
    .search-row button{ background:var(--gold); color:#fff; border:none; padding:9px 18px; border-radius:14px;
                         font-size:13px; font-weight:700; cursor:pointer; flex-shrink:0; }
    .search-row button:active{ transform:scale(.96); }
    .search-row button.secondary{ background:#fff; color:var(--ink); border:1.5px solid var(--line); }

    .time-row{ display:flex; gap:6px; flex-wrap:wrap; }
    .time-btn{ background:#fff; color:var(--ink-soft); border:1.5px solid var(--line); border-radius:999px;
               padding:6px 14px; font-size:12px; font-weight:700; cursor:pointer; }
    .time-btn.active{ background:var(--gold); color:#fff; border-color:var(--gold); }
    .time-btn:active{ transform:scale(.95); }

    .sum-head{ display:flex; justify-content:space-between; align-items:center; gap:8px; flex-wrap:wrap; margin-bottom:8px; }
    .sum-title{ font-size:13px; font-weight:800; }
    .sum-table{ width:100%; border-collapse:collapse; font-size:12.5px; }
    .sum-table th{ text-align:left; font-size:11px; color:var(--ink-soft); font-weight:700; padding:4px 6px; border-bottom:1.5px solid var(--line); }
    .sum-table td{ padding:7px 6px; border-bottom:1px solid var(--gold-soft); }
    .sum-table th.num, .sum-table td.num{ text-align:right; font-variant-numeric:tabular-nums; }
    .sum-row{ cursor:pointer; }
    .sum-row:hover{ background:var(--gold-soft); }
    .sum-row.active{ background:var(--pp-soft); }
    .sum-sub{ color:var(--ink-soft); font-size:11px; }
    .sum-scroll{ max-height:280px; overflow-y:auto; }

    .meta-row{ display:flex; justify-content:space-between; align-items:center; font-size:12px; color:var(--ink-soft); flex-wrap:wrap; gap:4px; }

    .log-list{ display:flex; flex-direction:column; gap:10px; }
    .log-empty{ text-align:center; color:var(--ink-soft); font-size:13px; padding:30px 0; }
    .log-item{ background:#fff; border:1.5px solid var(--line); border-radius:14px; padding:12px 14px; }
    .log-item-head{ display:flex; justify-content:space-between; align-items:center; gap:8px; margin-bottom:6px;
                     font-size:11px; color:var(--ink-soft); flex-wrap:wrap; }
    .log-item-head .who{ font-weight:700; color:var(--ink); }
    .log-chip{ display:inline-block; padding:2px 8px; border-radius:999px; font-weight:700; font-size:10px; }
    .log-chip.web{ background:var(--pp-soft); color:var(--pp); }
    .log-chip.chat{ background:var(--gold-soft); color:var(--gold); }
    .log-req{ font-weight:700; font-size:13px; margin-bottom:4px; word-break:break-all; }
    .log-res{ font-size:12.5px; color:var(--ink); white-space:pre-wrap; word-break:break-all; line-height:1.5; }

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
    el.style.cssText = 'position:fixed;top:8px;right:10px;z-index:50;font-size:11px;background:rgba(255,255,255,.92);border:1.5px solid #E5D3A1;border-radius:999px;padding:4px 10px;color:#6B5E82;';
    el.appendChild(document.createTextNode('🟡 ' + (d.nickname || '') + ' · '));
    var a = document.createElement('a');
    a.href = '/loa/s5/logout'; a.textContent = '로그아웃'; a.style.cssText = 'color:#A31F2B;font-weight:700;text-decoration:none;';
    el.appendChild(a);
    document.body.appendChild(el);
  }).catch(function () {});
})();
</script>

  <div class="wrap">
    <h1>📜 람쥐탑 로그 뷰어</h1>
    <div class="sub">채팅 명령어 + 웹 액션을 유저별로 조회(관리자용, SPA와 별도 페이지) -- 최근 24시간 이내만</div>
    <div style="display:flex;gap:8px;flex-wrap:wrap;font-size:12px;">
      <a href="/loa/tower-balance-stats" style="color:#6B5E82;text-decoration:none;border:1.5px solid #E5D3A1;background:#fff;padding:5px 12px;border-radius:999px;font-weight:700;">⚖️ 밸런스 통계</a>
    </div>

    <div class="card">
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
      <div class="sum-sub" style="margin-top:6px;">사용자 줄을 누르면 그 유저의 로그(최근 24시간)를 아래에 보여줍니다.</div>
    </div>

    <div class="meta-row" id="metaRow" style="display:none;">
      <span id="totalLabel"></span>
      <span id="fetchedLabel"></span>
    </div>

    <div id="loadingEl" class="loading" style="display:none;">불러오는 중...</div>
    <div class="log-list" id="logList"></div>
  </div>

  <script>
  (function () {
    var base = ''; // 같은 오리진(/loa/...)이라 상대경로로 충분.
    // 서버에서 한 번 불러온 "최근 24시간" 전체 로그(선택된 유저 기준). 시간대 버튼/검색어는
    // 전부 이 배열 안에서 필터링만 한다(추가 fetch 없음).
    var allItems = [];
    var state = { userName: '', windowMin: 1440, keyword: '' };
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
        var total = Number(u['CNT_' + k] || 0), web = Number(u['WEB_' + k] || 0);
        return { name: u.USER_NAME, total: total, web: web, chat: total - web, last: u.LAST_DATE };
      }).filter(function (r) { return r.total > 0; });
      rows.sort(function (a, b) { return b.total - a.total || (a.last < b.last ? 1 : -1); });
      var body = qs('sumBody');
      if (rows.length === 0) { body.innerHTML = '<div class="log-empty">이 기간에 명령어를 사용한 사람이 없습니다.</div>'; return; }
      var totalAll = rows.reduce(function (s, r) { return s + r.total; }, 0);
      body.innerHTML = '<table class="sum-table"><thead><tr><th>유저</th><th class="num">합계</th><th class="num">채팅</th><th class="num">웹</th><th>마지막 사용</th></tr></thead><tbody>'
        + rows.map(function (r) {
          return '<tr class="sum-row' + (state.userName === r.name ? ' active' : '') + '" data-user="' + escapeHtml(r.name).replace(/"/g, '&quot;') + '">'
            + '<td>' + escapeHtml(r.name) + '</td><td class="num"><b>' + r.total + '회</b></td>'
            + '<td class="num">' + r.chat + '</td><td class="num">' + r.web + '</td>'
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

    function renderItems() {
      var now = Date.now();
      var windowMs = state.windowMin * 60 * 1000;
      var kw = state.keyword.toLowerCase();
      var filtered = allItems.filter(function (it) {
        var d = parseLogDate(it.INSERT_DATE);
        if (d && (now - d.getTime()) > windowMs) return false;
        if (kw) {
          var hay = ((it.REQ || '') + ' ' + (it.RES || '')).toLowerCase();
          if (hay.indexOf(kw) === -1) return false;
        }
        return true;
      });

      var list = qs('logList');
      if (filtered.length === 0) {
        list.innerHTML = '<div class="log-empty">표시할 로그가 없습니다.</div>';
      } else {
        list.innerHTML = filtered.map(function (it) {
          var isWeb = it.ROOM_NAME === 'WEB';
          var chip = isWeb ? '<span class="log-chip web">WEB</span>' : '<span class="log-chip chat">' + escapeHtml(it.ROOM_NAME || '채팅') + '</span>';
          var who = state.userName ? '' : '<span class="who">' + escapeHtml(it.USER_NAME) + '</span>';
          return '<div class="log-item">'
            + '<div class="log-item-head">' + who + chip + '<span>' + escapeHtml(it.INSERT_DATE) + '</span></div>'
            + '<div class="log-req">' + escapeHtml(it.REQ) + '</div>'
            + '<div class="log-res">' + escapeHtml(it.RES) + '</div>'
            + '</div>';
        }).join('');
      }

      qs('metaRow').style.display = '';
      qs('totalLabel').textContent = '표시 ' + filtered.length + '건';
      qs('fetchedLabel').textContent = '(전체 불러온 ' + allItems.length + '건 중, 최근 24시간)';
    }

    function fetchFromServer() {
      qs('loadingEl').style.display = '';
      qs('logList').innerHTML = '';
      qs('metaRow').style.display = 'none';

      var url = base + '/loa/api/tower-battle-log-recent?userName=' + encodeURIComponent(state.userName) + '&hours=24';
      fetch(url).then(function (r) { return r.json(); }).then(function (data) {
        qs('loadingEl').style.display = 'none';
        allItems = data.items || [];
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
  })();
  </script>
</body>
</html>
