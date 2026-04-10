<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 헌터 랭크</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <script src="<%=request.getContextPath()%>/assets/js/vue.min.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f8f5f0; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; min-height: 100vh; }
    .wrap { max-width: 860px; margin: 0 auto; padding: 20px 12px 60px 140px; }

    .page-header { margin-bottom: 18px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #3d2b1f; }
    .search-row  { display: flex; gap: 8px; }
    .search-row input { padding: 8px 14px; border: 1.5px solid #e0d9ce; border-radius: 20px; background: #fff; color: #333; font-size: 13px; width: 160px; outline: none; }
    .search-row input:focus { border-color: #c9a96e; }
    .btn-search { background: #c9a96e; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #b8935a; }
    .loading, .empty { text-align: center; padding: 60px; color: #bbb; font-size: 15px; }

    /* 등급 배지 */
    .grade-badge {
      font-size: 48px; font-weight: 900; text-align: center; padding: 20px;
      background: linear-gradient(135deg, #fff8ee, #fff0d0);
      border: 2px solid #e8c870; border-radius: 16px; margin-bottom: 16px;
      text-shadow: 0 2px 8px rgba(200,150,50,.3);
    }
    .grade-SSS { color: #c00; }
    .grade-SS  { color: #e85; }
    .grade-S   { color: #c9a96e; }
    .grade-A   { color: #4a8; }
    .grade-B   { color: #48c; }
    .grade-C   { color: #888; }
    .grade-D   { color: #aaa; }
    .grade-F   { color: #ccc; }

    /* 섹션 카드 */
    .card { background: #fff; border: 1.5px solid #e8ddd0; border-radius: 14px; padding: 16px; margin-bottom: 14px; }
    .card-title { font-size: 13px; font-weight: 700; color: #a07858; margin-bottom: 12px; letter-spacing: .5px; }

    /* 지표 행 */
    .metric-row { margin-bottom: 14px; }
    .metric-label { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 4px; }
    .metric-name  { color: #555; }
    .metric-val   { color: #3d2b1f; font-weight: 700; }
    .bar-wrap { background: #f0ebe4; border-radius: 6px; height: 10px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 6px; transition: width .5s; }
    .bar-kill  { background: linear-gradient(90deg, #e8a040, #c9a96e); }
    .bar-drop  { background: linear-gradient(90deg, #60b060, #80d080); }
    .bar-death { background: linear-gradient(90deg, #8060c0, #a080e0); }
    .bar-done  { background: linear-gradient(90deg, #c9a96e, #ffd700); }

    /* 보정 뱃지 */
    .bonus-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
    .bonus-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
    .chip-hunter { background: #fff0d0; color: #b87820; border: 1px solid #e8c870; }
    .chip-item   { background: #f0e8ff; color: #7050a0; border: 1px solid #c0a0e0; }
    .chip-locked { background: #f0f0f0; color: #aaa; border: 1px solid #ddd; text-decoration: line-through; }

    /* 다음 등급 */
    .next-grade-box { font-size: 13px; color: #888; text-align: center; margin-top: 8px; }
    .next-grade-box strong { color: #c9a96e; font-size: 15px; }

    /* 원시 vs 보정 테이블 */
    .comp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .comp-table th { background: #f8f3ec; padding: 6px 10px; color: #a07858; font-weight: 700; text-align: left; border-bottom: 1.5px solid #e8ddd0; }
    .comp-table td { padding: 7px 10px; border-bottom: 1px solid #f0ebe4; }
    .comp-table td.num { text-align: right; font-variant-numeric: tabular-nums; font-weight: 600; color: #3d2b1f; }
    .comp-table td.plus { text-align: right; color: #60a060; font-size: 12px; }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">🎯 헌터 랭크 달성율</div>
    <div class="search-row">
      <input v-model="inputUser" placeholder="유저명 입력" @keyup.enter="fetch">
      <button class="btn-search" @click="fetch">조회</button>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty"   v-else-if="!userName">유저명을 입력해 조회하세요.</div>

  <template v-else>

    <!-- 현재 등급 -->
    <div class="grade-badge" :class="'grade-' + gradeBase">
      {{ data.grade || 'F' }}
      <div style="font-size:14px;font-weight:400;color:#888;margin-top:4px;">헌터 랭크</div>
    </div>

    <!-- 지표 카드 -->
    <div class="card">
      <div class="card-title">📊 보정 지표 달성율</div>

      <!-- 킬 -->
      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">⚔ 통산 킬수</span>
          <span class="metric-val">{{ fmt(data.adjusted.kills) }} / {{ fmt(nextKills) }}</span>
        </div>
        <div class="bar-wrap">
          <div class="bar-fill" :class="pct(data.adjusted.kills, nextKills) >= 100 ? 'bar-done' : 'bar-kill'"
               :style="{width: Math.min(100, pct(data.adjusted.kills, nextKills)) + '%'}"></div>
        </div>
      </div>

      <!-- 드랍 -->
      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">🎒 아이템 드랍수</span>
          <span class="metric-val">{{ fmt(data.adjusted.drops) }} / {{ fmt(nextDrops) }}</span>
        </div>
        <div class="bar-wrap">
          <div class="bar-fill" :class="pct(data.adjusted.drops, nextDrops) >= 100 ? 'bar-done' : 'bar-drop'"
               :style="{width: Math.min(100, pct(data.adjusted.drops, nextDrops)) + '%'}"></div>
        </div>
      </div>

      <!-- 죽음 -->
      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">💀 죽음 횟수</span>
          <span class="metric-val">{{ fmt(data.adjusted.deaths) }} / {{ fmt(nextDeaths) }}</span>
        </div>
        <div class="bar-wrap">
          <div class="bar-fill" :class="pct(data.adjusted.deaths, nextDeaths) >= 100 ? 'bar-done' : 'bar-death'"
               :style="{width: Math.min(100, pct(data.adjusted.deaths, nextDeaths)) + '%'}"></div>
        </div>
      </div>

      <div class="next-grade-box" v-if="data.nextGrade !== 'MAX'">
        다음 등급: <strong>{{ data.nextGrade }}</strong>
        (킬 {{ fmt(nextKills) }} / 드랍 {{ fmt(nextDrops) }} / 죽음 {{ fmt(nextDeaths) }} 필요)
      </div>
      <div class="next-grade-box" v-else>
        <strong>SSS</strong> 최고 등급 달성!
      </div>
    </div>

    <!-- 보정 내역 -->
    <div class="card">
      <div class="card-title">🔧 보정 내역</div>
      <table class="comp-table">
        <thead>
          <tr>
            <th>항목</th>
            <th style="text-align:right">킬/공격</th>
            <th style="text-align:right">드랍</th>
            <th style="text-align:right">죽음</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>원시 수치</td>
            <td class="num">{{ fmt(data.raw.kills) }}</td>
            <td class="num">{{ fmt(data.raw.drops) }}</td>
            <td class="num">{{ fmt(data.raw.deaths) }}</td>
          </tr>
          <tr>
            <td>
              헌터 공격 보정
              <span class="bonus-chip chip-hunter">{{ fmt(data.hunterBonus.hunterAttacks) }}회</span>
            </td>
            <td class="plus">+{{ fmt(data.hunterBonus.killBonus) }}</td>
            <td class="plus">-</td>
            <td class="plus">+{{ fmt(data.hunterBonus.deathBonus) }}</td>
          </tr>
          <tr>
            <td>
              아이템 보정
              <span :class="data.itemBonus.applied ? 'bonus-chip chip-item' : 'bonus-chip chip-locked'">
                어둠{{ data.itemBonus.darkQty }} / 음양{{ data.itemBonus.grayQty }}
                {{ data.itemBonus.applied ? '' : '(SS이상 미적용)' }}
              </span>
            </td>
            <td class="plus">{{ data.itemBonus.applied ? '+' + fmt(data.itemBonus.bonusVal) : '-' }}</td>
            <td class="plus">{{ data.itemBonus.applied ? '+' + fmt(data.itemBonus.bonusVal) : '-' }}</td>
            <td class="plus">{{ data.itemBonus.applied ? '+' + fmt(data.itemBonus.bonusVal) : '-' }}</td>
          </tr>
          <tr style="background:#f8f3ec;font-weight:700">
            <td>최종 보정 수치</td>
            <td class="num">{{ fmt(data.adjusted.kills) }}</td>
            <td class="num">{{ fmt(data.adjusted.drops) }}</td>
            <td class="num">{{ fmt(data.adjusted.deaths) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 등급표 -->
    <div class="card">
      <div class="card-title">📋 등급 기준표</div>
      <table class="comp-table">
        <thead>
          <tr><th>등급</th><th style="text-align:right">킬</th><th style="text-align:right">드랍</th><th style="text-align:right">죽음</th><th style="text-align:right">달성</th></tr>
        </thead>
        <tbody>
          <tr v-for="g in gradeTable" :key="g.name"
              :style="g.name === gradeBase ? 'background:#fff8ee;font-weight:700' : ''">
            <td><span :class="'grade-' + g.name">{{ g.name }}</span></td>
            <td class="num">{{ fmt(g.k) }}</td>
            <td class="num">{{ fmt(g.d) }}</td>
            <td class="num">{{ fmt(g.de) }}</td>
            <td class="num">{{ g.name === gradeBase ? '✓ 현재' : (data.adjusted.kills >= g.k && data.adjusted.drops >= g.d && data.adjusted.deaths >= g.de ? '✓' : '') }}</td>
          </tr>
        </tbody>
      </table>
    </div>

  </template>
</div>

<script>
new Vue({
  el: '#app',
  data: {
    inputUser: '',
    userName: '',
    loading: false,
    data: null,
    gradeTable: [
      {name:'SSS', k:50000, d:100000, de:1200},
      {name:'SS',  k:40000, d:50000,  de:700},
      {name:'S',   k:30000, d:30000,  de:500},
      {name:'A',   k:20000, d:20000,  de:400},
      {name:'B',   k:10000, d:10000,  de:200},
      {name:'C',   k:5000,  d:5000,   de:100},
      {name:'D',   k:1000,  d:1000,   de:50},
    ]
  },
  computed: {
    gradeBase() {
      if (!this.data) return 'F';
      return (this.data.grade || 'F').replace('+','');
    },
    nextKills()  { return this.data && this.data.nextReqs ? this.data.nextReqs.kills  : 0; },
    nextDrops()  { return this.data && this.data.nextReqs ? this.data.nextReqs.drops  : 0; },
    nextDeaths() { return this.data && this.data.nextReqs ? this.data.nextReqs.deaths : 0; }
  },
  mounted() {
    var p = new URLSearchParams(window.location.search);
    var u = p.get('userName') || p.get('user') || sessionStorage.getItem('loaUserName') || '';
    if (u) { this.inputUser = u; this.fetch(); }
  },
  methods: {
    fetch() {
      var u = this.inputUser.trim();
      if (!u) return;
      this.userName = u;
      sessionStorage.setItem('loaUserName', u);
      this.loading = true;
      fetch('<%=request.getContextPath()%>/loa/api/hunter-rank?userName=' + encodeURIComponent(u))
        .then(r => r.json())
        .then(d => { this.data = d; this.loading = false; })
        .catch(() => { this.loading = false; });
    },
    fmt(n) { return (n || 0).toLocaleString('ko-KR'); },
    pct(v, max) { return max > 0 ? Math.round(v / max * 1000) / 10 : 0; }
  }
});
</script>
</body>
</html>
