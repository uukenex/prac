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
    body { background: #0f0d1a; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #ccc; min-height: 100vh; }

    .wrap { max-width: 960px; margin: 0 auto; padding: 20px 12px 60px; }

    /* 헤더 */
    .page-header { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #c9a96e; }

    /* 검색 영역 */
    .search-area { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; }
    .search-area input {
      padding: 8px 14px; border: 1.5px solid #2a2040; border-radius: 20px;
      background: #1a1535; color: #ddd; font-size: 13px; width: 160px; outline: none;
      transition: border-color .2s;
    }
    .search-area input:focus { border-color: #c9a96e; }
    .btn-search { background: #c9a96e; color: #1a0800; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #e8c870; }

    /* 기간 필터 */
    .filter-row { display: flex; gap: 6px; flex-wrap: wrap; }
    .filter-btn { background: #1a1535; color: #9090b8; border: 1px solid #2a2040; border-radius: 14px; padding: 5px 13px; font-size: 12px; cursor: pointer; transition: all .15s; }
    .filter-btn:hover  { border-color: #5050a0; color: #d0d0f0; }
    .filter-btn.active { background: #2a2060; border-color: #c9a96e; color: #c9a96e; font-weight: 700; }

    /* 요약 통계 */
    .stat-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
    .stat-card { background: #1a1535; border: 1px solid #2a2040; border-radius: 10px; padding: 8px 14px; font-size: 12px; color: #9090b8; min-width: 80px; text-align: center; }
    .stat-card .sv { font-size: 18px; font-weight: 800; color: #c9a96e; display: block; }
    .stat-card.kill  .sv { color: #5a9e6f; }
    .stat-card.death .sv { color: #c06060; }
    .stat-card.drop  .sv { color: #c9a96e; }

    .loading { text-align: center; padding: 60px; color: #555; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #444; font-size: 14px; }

    /* 배틀로그 테이블 */
    .log-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .log-table th {
      background: #12102a; color: #6060a0; font-weight: 600; letter-spacing: .3px;
      padding: 8px 10px; text-align: left; border-bottom: 1px solid #2a2040; position: sticky; top: 0; z-index: 1;
    }
    .log-table td { padding: 7px 10px; border-bottom: 1px solid #1a1535; vertical-align: middle; }
    .log-table tr:hover td { background: #1a1535; }

    /* 행 타입별 왼쪽 색상 줄 */
    .log-table tr.row-kill   td:first-child { border-left: 3px solid #5a9e6f; }
    .log-table tr.row-death  td:first-child { border-left: 3px solid #c06060; }
    .log-table tr.row-multi  td:first-child { border-left: 3px solid #3a3a6a; }
    .log-table tr.row-potion td:first-child { border-left: 3px solid #9060c0; }
    .log-table tr.row-boss   td:first-child { border-left: 3px solid #c06020; }

    /* 시간 */
    .col-date { color: #6060a0; white-space: nowrap; font-size: 11px; }

    /* 몬스터 칩 */
    .mon-chip { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
    .mon-chip.normal { background: #1e1840; color: #a090d0; }
    .mon-chip.boss   { background: #2a1010; color: #e08050; border: 1px solid #5a2010; }
    .mon-chip.potion { background: #1a1040; color: #a080e0; border: 1px solid #4a2080; }

    /* 모드 뱃지 */
    .mode-badge { display: inline-block; padding: 1px 6px; border-radius: 8px; font-size: 10px; font-weight: 700; margin-left: 3px; }
    .mode-nm  { background: #1a2040; color: #6080e0; }
    .mode-hell{ background: #2a1010; color: #e05050; }
    .mode-light { background: #202818; color: #80c860; }
    .mode-dark  { background: #1a1040; color: #8060d0; }
    .mode-yin   { background: #202030; color: #a0a0e0; }

    /* 결과 아이콘 */
    .result-icons { display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
    .ri { font-size: 13px; }
    .tag { font-size: 10px; padding: 1px 5px; border-radius: 6px; font-weight: 600; }
    .tag-kill  { background: #1a3020; color: #5a9e6f; }
    .tag-death { background: #301818; color: #c06060; }
    .tag-drop  { background: #2a2010; color: #c9a96e; }
    .tag-crit  { background: #201030; color: #a060e0; }
    .tag-skill { background: #102030; color: #4090c0; }
    .tag-multi { background: #1a1a30; color: #6060a0; }

    /* 데미지 */
    .dmg-give { color: #e0a050; font-weight: 700; }
    .dmg-recv { color: #c06060; font-size: 10px; }

    /* 페이지네이션 */
    .pagination { display: flex; justify-content: center; gap: 6px; margin-top: 16px; flex-wrap: wrap; }
    .page-btn { background: #1a1535; color: #9090b8; border: 1px solid #2a2040; border-radius: 8px; padding: 6px 12px; font-size: 12px; cursor: pointer; transition: all .15s; }
    .page-btn:hover  { border-color: #5050a0; color: #d0d0f0; }
    .page-btn.active { background: #2a2060; border-color: #c9a96e; color: #c9a96e; font-weight: 700; }
    .page-btn:disabled { opacity: .35; cursor: default; }

    @media (max-width: 600px) {
      .log-table th:nth-child(4),
      .log-table td:nth-child(4) { display: none; } /* 데미지받음 숨김 */
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
    <div class="filter-row">
      <button v-for="f in filters" :key="f.days"
              class="filter-btn" :class="{ active: activeDays === f.days }"
              @click="setFilter(f.days)">{{ f.label }}</button>
    </div>
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
            <span v-if="modeLabel(row)" class="mode-badge" :class="modeBadgeClass(row)">{{ modeLabel(row) }}</span>
            <span v-if="luckyLabel(row)" class="mode-badge" :class="luckyBadgeClass(row)">{{ luckyLabel(row) }}</span>
          </td>
          <td><span class="dmg-give">{{ fmtNum(row.ATK_DMG) }}</span></td>
          <td><span class="dmg-recv" v-if="row.MON_DMG > 0">-{{ fmtNum(row.MON_DMG) }}</span></td>
          <td>
            <div class="result-icons">
              <span v-if="isMulti(row)"  class="tag tag-multi">다중</span>
              <span v-if="isPotion(row)" class="tag" style="background:#1a1040;color:#a080e0;">부활</span>
              <span v-if="row.KILL_YN  == 1" class="tag tag-kill">처치</span>
              <span v-if="row.DEATH_YN == 1" class="tag tag-death">사망</span>
              <span v-if="row.DROP_YN  == 1" class="tag tag-drop">🎁드랍</span>
              <span v-if="row.ATK_CRIT_YN == 1 && !isMulti(row)" class="tag tag-crit">⚡치명</span>
              <span v-if="row.JOB_SKILL_YN == 1" class="tag tag-skill">스킬</span>
              <span v-if="row.NOW_YN == 1" class="tag" style="background:#1a2010;color:#80a060;">진행중</span>
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
    size: 50,
    activeDays: 0,
    filters: [
      { label: '전체', days: 0 },
      { label: '오늘', days: 1 },
      { label: '3일',  days: 3 },
      { label: '7일',  days: 7 },
      { label: '30일', days: 30 }
    ]
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
    isBoss:   function(r) { return Number(r.TARGET_MON_LV) === 99; },

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
      // "YYYY-MM-DD HH:MI:SS" → "MM/DD HH:MI"
      var parts = s.split(' ');
      if (parts.length < 2) return s;
      var d = parts[0].substring(5); // MM-DD
      var t = parts[1].substring(0, 5); // HH:MI
      return d + ' ' + t;
    },
    fmtNum: function(v) {
      var n = Number(v);
      if (!n) return '0';
      if (n >= 100000000) return (n / 100000000).toFixed(1) + '억';
      if (n >= 10000)     return (n / 10000).toFixed(1) + '만';
      return String(n);
    },

    setFilter: function(days) {
      this.activeDays = days;
      this.page = 1;
      if (this.userName) this.fetch();
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
        + '&days='     + self.activeDays;
      fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(data) {
          if (data.error) { self.list = []; self.total = 0; }
          else {
            self.list  = data.list  || [];
            self.total = data.total || 0;
          }
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
