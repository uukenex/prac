<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 헌터 랭크</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f5f3ee; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; margin-left: 120px; }

    .wrap { max-width: 860px; margin: 0 auto; padding: 28px 16px 60px; }

    .page-header { margin-bottom: 22px; }
    .page-title  { font-size: 22px; font-weight: 800; color: #3d2b1f; display: flex; align-items: center; gap: 8px; }

    .top-controls { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
    .user-wrap { display: flex; gap: 8px; align-items: center; }
    .user-wrap input { padding: 10px 14px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; width: 160px; }
    .user-wrap input:focus { border-color: #c9a96e; }
    .user-wrap input::placeholder { color: #bbb; }
    .btn-query { background: #c9a96e; color: #fff; border: none; padding: 10px 20px; border-radius: 24px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-query:hover { background: #b8935a; }

    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #ccc; }

    /* 등급 배지 */
    .grade-badge {
      font-size: 48px; font-weight: 900; text-align: center; padding: 20px;
      background: #fff; border-radius: 14px; margin-bottom: 14px;
      box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid #e8ddd0;
    }
    .grade-sub { font-size: 14px; font-weight: 400; color: #888; margin-top: 4px; }
    .grade-SSS { color: #c00; }
    .grade-SS  { color: #e85; }
    .grade-S   { color: #c9a96e; }
    .grade-A   { color: #4a8; }
    .grade-B   { color: #48c; }
    .grade-C   { color: #888; }
    .grade-D   { color: #aaa; }
    .grade-F   { color: #ccc; }

    /* 카드 */
    .card { background: #fff; border-radius: 14px; padding: 16px; margin-bottom: 12px;
            box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid #e8ddd0; }
    .card-title { font-size: 13px; font-weight: 700; color: #a07858; margin-bottom: 14px; letter-spacing: .5px; }

    /* 지표 */
    .metric-row { margin-bottom: 14px; }
    .metric-label { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 5px; }
    .metric-name  { color: #555; }
    .metric-val   { color: #3d2b1f; font-weight: 700; }
    .bar-wrap { background: #f0ebe4; border-radius: 6px; height: 10px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 6px; transition: width .5s; }
    .bar-kill  { background: linear-gradient(90deg, #e8a040, #c9a96e); }
    .bar-drop  { background: linear-gradient(90deg, #60b060, #80d080); }
    .bar-death { background: linear-gradient(90deg, #8060c0, #a080e0); }
    .bar-done  { background: linear-gradient(90deg, #c9a96e, #ffd700); }
    .bar-secret {
      width: 100% !important; height: 100%;
      background: repeating-linear-gradient(45deg, #ccc 0px, #ccc 4px, #e8e8e8 4px, #e8e8e8 9px);
    }
    .val-secret { color: #ccc !important; letter-spacing: 2px; }

    /* 보정 칩 */
    .bonus-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; margin-left: 6px; }
    .chip-hunter { background: #fff0d0; color: #b87820; border: 1px solid #e8c870; }
    .chip-item   { background: #f0e8ff; color: #7050a0; border: 1px solid #c0a0e0; }
    .chip-locked { background: #f0f0f0; color: #aaa; border: 1px solid #ddd; text-decoration: line-through; }

    /* 다음 등급 */
    .next-grade-box { font-size: 13px; color: #888; text-align: center; margin-top: 10px; }
    .next-grade-box strong { color: #c9a96e; font-size: 15px; }

    /* 테이블 */
    .comp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .comp-table th { background: #f8f3ec; padding: 6px 10px; color: #a07858; font-weight: 700; text-align: left; border-bottom: 1.5px solid #e8ddd0; }
    .comp-table td { padding: 7px 10px; border-bottom: 1px solid #f0ebe4; }
    .comp-table td.num  { text-align: right; font-weight: 600; color: #3d2b1f; }
    .comp-table td.plus { text-align: right; color: #60a060; font-size: 12px; }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">🎯 헌터 랭크 달성율</div>
  </div>

  <div class="top-controls">
    <div class="user-wrap">
      <input v-model="inputUser" placeholder="유저명 입력" @keyup.enter="loadData">
      <button class="btn-query" @click="loadData">조회</button>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty"   v-else-if="!data">유저명을 입력해 조회하세요.</div>

  <template v-else>

    <!-- 현재 등급 -->
    <div class="grade-badge" :class="'grade-' + gradeBase">
      {{ data.grade || 'F' }}
      <div class="grade-sub">헌터 랭크</div>
    </div>

    <!-- 지표 카드 -->
    <div class="card">
      <div class="card-title">📊 보정 지표 달성율</div>

      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">⚔ 통산 킬수</span>
          <span v-if="!isHidden" class="metric-val">{{ fmt(data.adjusted.kills) }} / {{ fmt(nextKills) }}</span>
          <span v-else class="metric-val val-secret">??? / ???</span>
        </div>
        <div class="bar-wrap">
          <div v-if="!isHidden" class="bar-fill" :class="pct(data.adjusted.kills, nextKills) >= 100 ? 'bar-done' : 'bar-kill'"
               :style="{width: Math.min(100, pct(data.adjusted.kills, nextKills)) + '%'}"></div>
          <div v-else class="bar-secret"></div>
        </div>
      </div>

      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">🎒 아이템 드랍수</span>
          <span v-if="!isHidden" class="metric-val">{{ fmt(data.adjusted.drops) }} / {{ fmt(nextDrops) }}</span>
          <span v-else class="metric-val val-secret">??? / ???</span>
        </div>
        <div class="bar-wrap">
          <div v-if="!isHidden" class="bar-fill" :class="pct(data.adjusted.drops, nextDrops) >= 100 ? 'bar-done' : 'bar-drop'"
               :style="{width: Math.min(100, pct(data.adjusted.drops, nextDrops)) + '%'}"></div>
          <div v-else class="bar-secret"></div>
        </div>
      </div>

      <div class="metric-row">
        <div class="metric-label">
          <span class="metric-name">💀 죽음 횟수</span>
          <span v-if="!isHidden" class="metric-val">{{ fmt(data.adjusted.deaths) }} / {{ fmt(nextDeaths) }}</span>
          <span v-else class="metric-val val-secret">??? / ???</span>
        </div>
        <div class="bar-wrap">
          <div v-if="!isHidden" class="bar-fill" :class="pct(data.adjusted.deaths, nextDeaths) >= 100 ? 'bar-done' : 'bar-death'"
               :style="{width: Math.min(100, pct(data.adjusted.deaths, nextDeaths)) + '%'}"></div>
          <div v-else class="bar-secret"></div>
        </div>
      </div>

      <div class="next-grade-box" v-if="isHidden" style="color:#bbb">
        🔒 SS 랭크 이상의 달성 조건은 공개되지 않습니다
      </div>
      <div class="next-grade-box" v-else-if="data.nextGrade !== 'MAX'">
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
            <td>헌터 공격 보정 <span class="bonus-chip chip-hunter">{{ fmt(data.hunterBonus.hunterAttacks) }}회</span></td>
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
            <template v-if="g.secret">
              <td class="num val-secret">???</td>
              <td class="num val-secret">???</td>
              <td class="num val-secret">???</td>
              <td class="num val-secret">🔒</td>
            </template>
            <template v-else>
              <td class="num">{{ fmt(g.k) }}</td>
              <td class="num">{{ fmt(g.d) }}</td>
              <td class="num">{{ fmt(g.de) }}</td>
              <td class="num">{{ g.name === gradeBase ? '✓ 현재' : (data.adjusted.kills >= g.k && data.adjusted.drops >= g.d && data.adjusted.deaths >= g.de ? '✓' : '') }}</td>
            </template>
          </tr>
        </tbody>
      </table>
    </div>

  </template>
</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
new Vue({
  el: '#app',
  data: {
    inputUser: '',
    loading: false,
    data: null,
    gradeTable: [
      {name:'SSS', k:0, d:0, de:0, secret:true},
      {name:'SS',  k:0, d:0, de:0, secret:true},
      {name:'S',   k:30000, d:30000,  de:500},
      {name:'A',   k:20000, d:20000,  de:400},
      {name:'B',   k:10000, d:10000,  de:200},
      {name:'C',   k:5000,  d:5000,   de:100},
      {name:'D',   k:1000,  d:1000,   de:50}
    ]
  },
  computed: {
    gradeBase: function() {
      if (!this.data) return 'F';
      return (this.data.grade || 'F').replace('+', '');
    },
    isHidden: function() {
      return this.gradeBase === 'SS' || this.gradeBase === 'SSS';
    },
    nextKills:  function() { return this.data && this.data.nextReqs ? this.data.nextReqs.kills  : 0; },
    nextDrops:  function() { return this.data && this.data.nextReqs ? this.data.nextReqs.drops  : 0; },
    nextDeaths: function() { return this.data && this.data.nextReqs ? this.data.nextReqs.deaths : 0; }
  },
  mounted: function() {
    var params = new URLSearchParams(window.location.search);
    var u = (params.get('userName') || params.get('user') || sessionStorage.getItem('loaUserName') || '').trim();
    if (u) { this.inputUser = u; this.loadData(); }
  },
  methods: {
    loadData: function() {
      var self = this;
      var u = self.inputUser.trim();
      if (!u) return;
      sessionStorage.setItem('loaUserName', u);
      self.loading = true;
      fetch('<%=request.getContextPath()%>/loa/api/hunter-rank?userName=' + encodeURIComponent(u))
        .then(function(r) { return r.json(); })
        .then(function(d) { self.data = d; self.loading = false; })
        .catch(function() { self.loading = false; });
    },
    fmt: function(n) { return (n || 0).toLocaleString('ko-KR'); },
    pct: function(v, max) { return max > 0 ? Math.round(v / max * 1000) / 10 : 0; }
  }
});
</script>
</body>
</html>
