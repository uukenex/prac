<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 배틀로그</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f8f5f0; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; min-height: 100vh; }

    .wrap { max-width: 960px; margin: 0 auto; padding: 20px 12px 60px; }

    /* 헤더 */
    .page-header { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #3d2b1f; }

    /* 검색 영역 */
    .search-area { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; }
    .search-area input {
      padding: 8px 14px; border: 1.5px solid #e0d9ce; border-radius: 20px;
      background: #fff; color: #333; font-size: 13px; width: 160px; outline: none;
      transition: border-color .2s;
    }
    .search-area input:focus { border-color: #c9a96e; }
    .btn-search { background: #c9a96e; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #b8935a; }
    .period-label { font-size: 12px; color: #a07858; background: #fff; border: 1px solid #e0d9ce; border-radius: 14px; padding: 5px 12px; }

    /* 요약 통계 */
    .stat-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
    .stat-card { background: #fff; border: 1px solid #e8ddd0; border-radius: 10px; padding: 8px 14px; font-size: 12px; color: #a07858; min-width: 80px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
    .stat-card .sv { font-size: 18px; font-weight: 800; color: #c9a96e; display: block; }
    .stat-card.kill  .sv { color: #3a7a52; }
    .stat-card.death .sv { color: #b04040; }
    .stat-card.drop  .sv { color: #c9a96e; }

    .loading { text-align: center; padding: 60px; color: #bbb; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #bbb; font-size: 14px; }

    /* 배틀로그 테이블 */
    .log-table { width: 100%; border-collapse: collapse; font-size: 12px; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 6px rgba(0,0,0,.06); }
    .log-table th {
      background: #f5f0e8; color: #a07858; font-weight: 700; letter-spacing: .3px;
      padding: 9px 10px; text-align: left; border-bottom: 1px solid #e8ddd0;
    }
    .log-table td { padding: 7px 10px; border-bottom: 1px solid #f5f0e8; vertical-align: middle; }
    .log-table tr:last-child td { border-bottom: none; }
    .log-table tr:hover td { background: #fffbf5; }

    /* 행 타입별 왼쪽 색상 줄 */
    .log-table tr.row-kill   td:first-child { border-left: 3px solid #3a7a52; }
    .log-table tr.row-death  td:first-child { border-left: 3px solid #b04040; }
    .log-table tr.row-multi  td:first-child { border-left: 3px solid #d0c8bc; }
    .log-table tr.row-potion td:first-child { border-left: 3px solid #8860c0; }
    .log-table tr.row-boss   td:first-child { border-left: 3px solid #c06020; }

    /* 시간 */
    .col-date { color: #b0a090; white-space: nowrap; font-size: 11px; }

    /* 몬스터 칩 */
    .mon-chip { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
    .mon-chip.normal { background: #f0ece4; color: #7a6050; }
    .mon-chip.boss   { background: #fff0e0; color: #c06020; border: 1px solid #e8c090; }
    .mon-chip.potion { background: #f0e8ff; color: #7840b0; border: 1px solid #d0b0e8; }

    /* 모드 뱃지 */
    .mode-badge { display: inline-block; padding: 1px 6px; border-radius: 8px; font-size: 10px; font-weight: 700; margin-left: 3px; }
    .mode-nm   { background: #e8eeff; color: #4060c0; }
    .mode-hell { background: #ffe8e8; color: #c04040; }
    .mode-light{ background: #e8f8e0; color: #408030; }
    .mode-dark { background: #ede8ff; color: #6040b0; }
    .mode-yin  { background: #f0f0f8; color: #6060a0; }

    /* 결과 태그 */
    .result-icons { display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
    .tag { font-size: 10px; padding: 1px 6px; border-radius: 6px; font-weight: 600; }
    .tag-kill  { background: #e0f0e8; color: #2a6040; }
    .tag-death { background: #fce8e8; color: #a03030; }
    .tag-drop  { background: #fff4e0; color: #a07030; }
    .tag-crit  { background: #f0e8ff; color: #7040b0; }
    .tag-skill { background: #e0f0ff; color: #2060a0; }
    .tag-multi { background: #f0ece4; color: #a09080; }

    /* 데미지 */
    .dmg-give { color: #8a6030; font-weight: 700; }
    .dmg-recv { color: #b04040; font-size: 10px; }

    /* 페이지네이션 */
    .pagination { display: flex; justify-content: center; gap: 6px; margin-top: 16px; flex-wrap: wrap; }
    .page-btn { background: #fff; color: #a07858; border: 1px solid #e8ddd0; border-radius: 8px; padding: 6px 12px; font-size: 12px; cursor: pointer; transition: all .15s; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
    .page-btn:hover  { border-color: #c9a96e; color: #7a5030; }
    .page-btn.active { background: #c9a96e; border-color: #c9a96e; color: #fff; font-weight: 700; }
    .page-btn:disabled { opacity: .35; cursor: default; }

    @media (max-width: 600px) {
      .log-table th:nth-child(4),
      .log-table td:nth-child(4) { display: none; }
      .col-date { font-size: 10px; }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">📜 배틀로그</div>
  </div>

  <!-- 검색 -->
  <div class="search-area">
    <input v-model="inputUser" placeholder="유저명 입력" @keyup.enter="search">
    <button class="btn-search" @click="search">조회</button>
    <span class="period-label">최근 7일</span>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty"   v-else-if="!userName">유저명을 입력해 조회하세요.</div>
  <div class="empty"   v-else-if="list.length === 0 && !loading">배틀로그가 없습니다.</div>

  <template v-else-if="!loading && userName">

    <!-- 요약 통계 -->
    <div class="stat-row">
      <div class="stat-card"><span class="sv">{{ total }}</span>전체</div>
      <div class="stat-card kill"><span class="sv">{{ killCount }}</span>처치</div>
      <div class="stat-card death"><span class="sv">{{ deathCount }}</span>사망</div>
      <div class="stat-card drop"><span class="sv">{{ dropCount }}</span>드랍</div>
    </div>

    <!-- 테이블 -->
    <table class="log-table">
      <thead>
        <tr>
          <th>시간</th>
          <th>몬스터</th>
          <th>데미지</th>
          <th>받은피해</th>
          <th>결과</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, idx) in list" :key="idx" :class="rowClass(row)">
          <td class="col-date">{{ fmtDate(row.INSERT_DATE) }}</td>
          <td>
            <span class="mon-chip" :class="monChipClass(row)">{{ monLabel(row) }}</span>
            <span v-if="modeLabel(row)"  class="mode-badge" :class="modeBadgeClass(row)">{{ modeLabel(row) }}</span>
            <span v-if="luckyLabel(row)" class="mode-badge" :class="luckyBadgeClass(row)">{{ luckyLabel(row) }}</span>
          </td>
          <td><span class="dmg-give">{{ fmtNum(row.ATK_DMG) }}</span></td>
          <td><span class="dmg-recv" v-if="row.MON_DMG > 0">-{{ fmtNum(row.MON_DMG) }}</span></td>
          <td>
            <div class="result-icons">
              <span v-if="isMulti(row)"  class="tag tag-multi">다중</span>
              <span v-if="isPotion(row)" class="tag" style="background:#f0e8ff;color:#7840b0;">부활</span>
              <span v-if="row.KILL_YN  == 1" class="tag tag-kill">처치</span>
              <span v-if="row.DEATH_YN == 1" class="tag tag-death">사망</span>
              <span v-if="row.DROP_YN  == 1" class="tag tag-drop">🎁드랍</span>
              <span v-if="row.ATK_CRIT_YN == 1 && !isMulti(row)" class="tag tag-crit">⚡치명</span>
              <span v-if="row.JOB_SKILL_YN == 1" class="tag tag-skill">스킬</span>
              <span v-if="row.NOW_YN == 1" class="tag" style="background:#e8f4e0;color:#407030;">진행중</span>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- 페이지네이션 -->
    <div class="pagination" v-if="totalPages > 1">
      <button class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">◀</button>
      <button v-for="p in pageRange" :key="p"
              class="page-btn" :class="{ active: p === page }"
              @click="goPage(p)">{{ p }}</button>
      <button class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">▶</button>
    </div>

  </template>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
new Vue({
  el: '#app',
  data: {
    inputUser: '',
    userName: '',
    loading: false,
    list: [],
    total: 0,
    page: 1,
    size: 50
  },
  computed: {
    totalPages: function() { return Math.max(1, Math.ceil(this.total / this.size)); },
    pageRange: function() {
      var cur = this.page, max = this.totalPages;
      var start = Math.max(1, cur - 2), end = Math.min(max, cur + 2);
      var pages = [];
      for (var p = start; p <= end; p++) pages.push(p);
      return pages;
    },
    killCount:  function() { return this.list.filter(function(r) { return r.KILL_YN  == 1; }).length; },
    deathCount: function() { return this.list.filter(function(r) { return r.DEATH_YN == 1; }).length; },
    dropCount:  function() { return this.list.filter(function(r) { return r.DROP_YN  == 1; }).length; }
  },
  methods: {
    isMulti:  function(r) { return Number(r.GAIN_EXP) === -1; },
    isPotion: function(r) { return Number(r.TARGET_MON_LV) === 0; },
    isBoss:   function(r) { var n = Number(r.TARGET_MON_LV); return n === 99 || n === 999; },

    monLabel: function(r) {
      if (this.isPotion(r)) return '💊 부활물약';
      if (this.isBoss(r))   return '👹 상급악마';
      return r.MON_NAME || ('몬스터#' + r.TARGET_MON_LV);
    },
    monChipClass: function(r) {
      if (this.isPotion(r)) return 'potion';
      if (this.isBoss(r))   return 'boss';
      return 'normal';
    },
    modeLabel: function(r) {
      var nm = Number(r.NIGHTMARE_YN);
      if (nm === 1) return 'NM';
      if (nm === 2) return 'HELL';
      return '';
    },
    modeBadgeClass: function(r) {
      var nm = Number(r.NIGHTMARE_YN);
      if (nm === 1) return 'mode-nm';
      if (nm === 2) return 'mode-hell';
      return '';
    },
    luckyLabel: function(r) {
      var t = Number(r.LUCKY_YN);
      if (t === 1) return '빛';
      if (t === 2) return '다크';
      if (t === 3) return '음양';
      return '';
    },
    luckyBadgeClass: function(r) {
      var t = Number(r.LUCKY_YN);
      if (t === 1) return 'mode-light';
      if (t === 2) return 'mode-dark';
      if (t === 3) return 'mode-yin';
      return '';
    },
    rowClass: function(r) {
      if (this.isPotion(r)) return 'row-potion';
      if (this.isBoss(r))   return 'row-boss';
      if (this.isMulti(r))  return 'row-multi';
      if (r.DEATH_YN  == 1) return 'row-death';
      if (r.KILL_YN   == 1) return 'row-kill';
      return '';
    },
    fmtDate: function(s) {
      if (!s) return '';
      var parts = s.split(' ');
      if (parts.length < 2) return s;
      return parts[0].substring(5) + ' ' + parts[1].substring(0, 5);
    },
    fmtNum: function(v) {
      var n = Number(v);
      if (!n) return '0';
      if (n >= 100000000) return (n / 100000000).toFixed(1) + '억';
      if (n >= 10000)     return (n / 10000).toFixed(1) + '만';
      return String(n);
    },
    goPage: function(p) {
      if (p < 1 || p > this.totalPages) return;
      this.page = p;
      this.fetch();
      window.scrollTo(0, 0);
    },
    search: function() {
      var name = this.inputUser.trim();
      if (!name) return;
      this.userName = name;
      this.page = 1;
      this.fetch();
      sessionStorage.setItem('loaUserName', name);
    },
    fetch: function() {
      var self = this;
      self.loading = true;
      var url = '<%=request.getContextPath()%>/loa/api/battle-log'
        + '?userName=' + encodeURIComponent(self.userName)
        + '&page='     + self.page
        + '&size='     + self.size
        + '&days=7';
      fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(data) {
          self.list  = data.error ? [] : (data.list  || []);
          self.total = data.error ? 0  : (data.total || 0);
          self.loading = false;
        })
        .catch(function() { self.loading = false; });
    }
  },
  mounted: function() {
    var params = new URLSearchParams(window.location.search);
    var u = (params.get('userName') || params.get('user') || sessionStorage.getItem('loaUserName') || '').trim();
    if (u) {
      this.inputUser = u;
      this.userName  = u;
      this.fetch();
    }
  }
});
</script>
</body>
</html>
