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
       마크업/JS는 이 페이지 전용으로 새로 작성(데이터: GET /loa/api/tower-battle-log). -->
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
    .search-row input{ padding:9px 13px; border:1.5px solid var(--line); border-radius:14px; background:#fff;
                        font-size:13px; flex:1 1 140px; min-width:0; }
    .search-row button{ background:var(--gold); color:#fff; border:none; padding:9px 18px; border-radius:14px;
                         font-size:13px; font-weight:700; cursor:pointer; flex-shrink:0; }
    .search-row button:active{ transform:scale(.96); }
    .search-row button.secondary{ background:#fff; color:var(--ink); border:1.5px solid var(--line); }

    .meta-row{ display:flex; justify-content:space-between; align-items:center; font-size:12px; color:var(--ink-soft); }

    .log-list{ display:flex; flex-direction:column; gap:10px; }
    .log-empty{ text-align:center; color:var(--ink-soft); font-size:13px; padding:30px 0; }
    .log-item{ background:#fff; border:1.5px solid var(--line); border-radius:14px; padding:12px 14px; }
    .log-item-head{ display:flex; justify-content:space-between; align-items:center; gap:8px; margin-bottom:6px;
                     font-size:11px; color:var(--ink-soft); }
    .log-chip{ display:inline-block; padding:2px 8px; border-radius:999px; font-weight:700; font-size:10px; }
    .log-chip.web{ background:var(--pp-soft); color:var(--pp); }
    .log-chip.chat{ background:var(--gold-soft); color:var(--gold); }
    .log-req{ font-weight:700; font-size:13px; margin-bottom:4px; word-break:break-all; }
    .log-res{ font-size:12.5px; color:var(--ink); white-space:pre-wrap; word-break:break-all; line-height:1.5; }

    .pager{ display:flex; justify-content:center; align-items:center; gap:10px; margin-top:4px; }
    .pager button{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:7px 14px;
                    font-size:12px; cursor:pointer; }
    .pager button:disabled{ opacity:.4; cursor:default; }
    .pager .page-num{ font-size:12px; font-weight:700; color:var(--ink-soft); }

    .loading{ text-align:center; color:var(--ink-soft); font-size:13px; padding:20px 0; }
  </style>
</head>
<body>
  <div class="wrap">
    <h1>📜 람쥐탑 로그 뷰어</h1>
    <div class="sub">채팅 명령어 + 웹 액션을 유저별로 최신순 조회(관리자용, SPA와 별도 페이지)</div>

    <div class="card">
      <div class="search-row">
        <input type="text" id="userNameInput" placeholder="유저명 (예: 일어난다람쥐/카단)">
        <input type="text" id="keywordInput" placeholder="검색어(선택, 요청/응답 부분일치)">
        <button type="button" id="queryBtn">조회</button>
      </div>
    </div>

    <div class="meta-row" id="metaRow" style="display:none;">
      <span id="totalLabel"></span>
      <span id="pageLabel"></span>
    </div>

    <div id="loadingEl" class="loading" style="display:none;">불러오는 중...</div>
    <div class="log-list" id="logList"></div>

    <div class="pager" id="pagerEl" style="display:none;">
      <button type="button" id="prevBtn">◀ 이전</button>
      <span class="page-num" id="pageNumEl"></span>
      <button type="button" id="nextBtn">다음 ▶</button>
    </div>
  </div>

  <script>
  (function () {
    var base = ''; // 같은 오리진(/loa/...)이라 상대경로로 충분.
    var PAGE_SIZE = 50;
    var state = { userName: '', keyword: '', page: 1, totalPages: 1 };

    function qs(id) { return document.getElementById(id); }

    // URL에 ?userName=... 이 있으면 바로 채워서 조회(챗봇 안내 링크에서 바로 열 수 있게).
    function initFromQuery() {
      var params = new URLSearchParams(window.location.search);
      var u = params.get('userName');
      if (u) {
        qs('userNameInput').value = u;
        runQuery(1);
      }
    }

    function escapeHtml(s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function renderItems(items) {
      var list = qs('logList');
      if (!items || items.length === 0) {
        list.innerHTML = '<div class="log-empty">표시할 로그가 없습니다.</div>';
        return;
      }
      var html = items.map(function (it) {
        var isWeb = it.ROOM_NAME === 'WEB';
        var chip = isWeb ? '<span class="log-chip web">WEB</span>' : '<span class="log-chip chat">' + escapeHtml(it.ROOM_NAME || '채팅') + '</span>';
        return '<div class="log-item">'
          + '<div class="log-item-head">' + chip + '<span>' + escapeHtml(it.INSERT_DATE) + '</span></div>'
          + '<div class="log-req">' + escapeHtml(it.REQ) + '</div>'
          + '<div class="log-res">' + escapeHtml(it.RES) + '</div>'
          + '</div>';
      }).join('');
      list.innerHTML = html;
    }

    function runQuery(page) {
      var userName = qs('userNameInput').value.trim();
      if (!userName) { alert('유저명을 입력하세요.'); return; }
      state.userName = userName;
      state.keyword = qs('keywordInput').value.trim();
      state.page = page || 1;

      qs('loadingEl').style.display = '';
      qs('logList').innerHTML = '';
      qs('metaRow').style.display = 'none';
      qs('pagerEl').style.display = 'none';

      var url = base + '/loa/api/tower-battle-log?userName=' + encodeURIComponent(state.userName)
          + '&keyword=' + encodeURIComponent(state.keyword)
          + '&page=' + state.page + '&pageSize=' + PAGE_SIZE;

      fetch(url).then(function (r) { return r.json(); }).then(function (data) {
        qs('loadingEl').style.display = 'none';
        if (data.error) { qs('logList').innerHTML = '<div class="log-empty">' + escapeHtml(data.error) + '</div>'; return; }
        state.totalPages = Math.max(1, data.totalPages || 1);
        renderItems(data.items);
        qs('metaRow').style.display = '';
        qs('totalLabel').textContent = '총 ' + (data.total || 0) + '건';
        qs('pageLabel').textContent = '페이지당 ' + PAGE_SIZE + '건';
        qs('pagerEl').style.display = '';
        qs('pageNumEl').textContent = state.page + ' / ' + state.totalPages;
        qs('prevBtn').disabled = state.page <= 1;
        qs('nextBtn').disabled = state.page >= state.totalPages;
      }).catch(function () {
        qs('loadingEl').style.display = 'none';
        qs('logList').innerHTML = '<div class="log-empty">조회 중 오류가 발생했습니다.</div>';
      });
    }

    qs('queryBtn').addEventListener('click', function () { runQuery(1); });
    qs('userNameInput').addEventListener('keydown', function (e) { if (e.key === 'Enter') runQuery(1); });
    qs('keywordInput').addEventListener('keydown', function (e) { if (e.key === 'Enter') runQuery(1); });
    qs('prevBtn').addEventListener('click', function () { if (state.page > 1) runQuery(state.page - 1); });
    qs('nextBtn').addEventListener('click', function () { if (state.page < state.totalPages) runQuery(state.page + 1); });

    initFromQuery();
  })();
  </script>
</body>
</html>
