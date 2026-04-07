<%--
  공통 좌측 네비게이션 사이드바
  각 페이지에서 <body> 바로 아래에 <%@ include file="_loa_nav.jsp" %> 로 포함
--%>
<style>
  /* ── 사이드바 ── */
  .loa-nav {
    position: fixed;
    left: 0; top: 0;
    width: 120px;
    height: 100vh;
    background: #0e0c1a;
    border-right: 1px solid #2a2040;
    display: flex;
    flex-direction: column;
    z-index: 200;
    overflow: hidden;
  }
  .loa-nav-title {
    background: linear-gradient(135deg, #b8914a 0%, #e8c870 60%, #c9a96e 100%);
    color: #1a0800;
    font-weight: 900;
    font-size: 13px;
    padding: 15px 8px;
    text-align: center;
    letter-spacing: 1px;
    flex-shrink: 0;
  }
  .loa-nav-item {
    display: block;
    color: #9090b8;
    padding: 13px 14px;
    font-size: 12px;
    font-weight: 600;
    text-decoration: none;
    border-bottom: 1px solid #18152e;
    transition: background .15s, color .15s;
    border-left: 3px solid transparent;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .loa-nav-item:hover { background: #1a1535; color: #d0d0f0; border-left-color: #5050a0; }
  .loa-nav-item.active { background: #1e1840; color: #c9a96e; border-left-color: #c9a96e; font-weight: 800; }

  /* 사이드바 너비만큼 본문 밀기 */
  body { margin-left: 120px !important; }

  @media (max-width: 600px) {
    .loa-nav { width: 80px; }
    .loa-nav-item { font-size: 10px; padding: 10px 6px; }
    body { margin-left: 80px !important; }
  }
</style>

<nav class="loa-nav" id="loaNav">
  <div class="loa-nav-title">람쥐봇</div>
  <a class="loa-nav-item" data-page="user-info" href="#">⚔ 장비정보</a>
  <a class="loa-nav-item" data-page="monster"   href="#">👾 몬스터</a>
  <a class="loa-nav-item" data-page="item"       href="#">🛒 아이템샵</a>
  <a class="loa-nav-item" data-page="job"        href="#">📚 직업도감</a>
</nav>

<script>
(function () {
  var base = '<%=request.getContextPath()%>/loa';

  // userName 우선순위: URL param > sessionStorage
  var params = new URLSearchParams(window.location.search);
  var u = (params.get('userName') || params.get('user') || sessionStorage.getItem('loaUserName') || '').trim();

  function buildUrl(page) {
    return base + '/' + page + '-view' + (u ? '?userName=' + encodeURIComponent(u) : '');
  }

  // 링크 설정
  document.querySelectorAll('.loa-nav-item').forEach(function (a) {
    a.href = buildUrl(a.getAttribute('data-page'));
  });

  // 현재 페이지 하이라이트
  var path = window.location.pathname;
  ['user-info', 'monster', 'item', 'job'].forEach(function (page) {
    if (path.indexOf(page) !== -1) {
      var el = document.querySelector('.loa-nav-item[data-page="' + page + '"]');
      if (el) el.classList.add('active');
    }
  });
})();
</script>
