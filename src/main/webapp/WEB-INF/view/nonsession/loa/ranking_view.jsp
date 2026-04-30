<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 랭킹</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #0e0c1a; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #d0d0f0; min-height: 100vh; }

    .wrap { max-width: 960px; margin: 0 auto; padding: 20px 12px 60px; }

    /* 헤더 */
    .page-header {
      margin-bottom: 18px;
      display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
    }
    .page-title { font-size: 22px; font-weight: 900; color: #c9a96e; letter-spacing: 1px; }

    /* 옵션 바 */
    .option-bar {
      display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
      background: #1a1535; border: 1px solid #2a2040; border-radius: 10px;
      padding: 10px 16px; margin-bottom: 20px;
    }
    .opt-label { font-size: 13px; color: #9090b8; font-weight: 600; }
    .checkbox-wrap {
      display: flex; align-items: center; gap: 6px; cursor: pointer;
    }
    .checkbox-wrap input[type=checkbox] { width: 16px; height: 16px; cursor: pointer; accent-color: #c9a96e; }
    .checkbox-wrap span { font-size: 13px; color: #d0d0f0; font-weight: 600; user-select: none; }
    .alt-chips { display: flex; flex-wrap: wrap; gap: 5px; }
    .alt-chip {
      background: #2a2040; color: #9090b8; border-radius: 12px;
      padding: 3px 10px; font-size: 11px; font-weight: 600;
    }

    /* 랭킹 섹션 */
    .rank-sections { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 640px) { .rank-sections { grid-template-columns: 1fr; } }

    .rank-card {
      background: #14112a; border: 1px solid #2a2040; border-radius: 12px;
      overflow: hidden;
    }
    .rank-card-title {
      background: linear-gradient(90deg, #1e1840, #141130);
      border-bottom: 1px solid #2a2040;
      padding: 10px 16px; font-size: 13px; font-weight: 800; color: #c9a96e;
      letter-spacing: .5px;
    }
    .rank-card-body { padding: 4px 0; }

    .rank-row {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 16px; border-bottom: 1px solid #1a1535; transition: background .1s;
    }
    .rank-row:last-child { border-bottom: none; }
    .rank-row:hover { background: #1a1535; }
    .rank-row.hidden { display: none !important; }

    .rank-no {
      width: 26px; font-size: 14px; font-weight: 900; flex-shrink: 0;
      text-align: center;
    }
    .rank-no.r1 { color: #ffd700; }
    .rank-no.r2 { color: #c0c0c0; }
    .rank-no.r3 { color: #cd7f32; }
    .rank-no.rn { color: #5050a0; }

    .rank-name { font-size: 13px; font-weight: 700; color: #d0d0f0; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .rank-name.alt { color: #5050a0 !important; }
    .rank-sub  { font-size: 11px; color: #6060a0; flex-shrink: 0; }
    .rank-val  { font-size: 12px; font-weight: 700; color: #c9a96e; flex-shrink: 0; white-space: nowrap; }

    /* 로딩 / 빈 */
    .loading { text-align: center; padding: 40px; color: #5050a0; font-size: 14px; }
    .empty   { text-align: center; padding: 30px; color: #5050a0; font-size: 12px; }

    /* 새로고침 버튼 */
    .btn-refresh {
      background: #1e1840; color: #c9a96e; border: 1px solid #3a3060;
      border-radius: 20px; padding: 7px 18px; font-size: 12px; font-weight: 700;
      cursor: pointer; transition: background .15s;
    }
    .btn-refresh:hover { background: #2a2450; }
    .last-updated { font-size: 11px; color: #5050a0; }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>

<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">🏆 랭킹</div>
    <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
      <span class="last-updated" v-if="lastUpdated">{{ lastUpdated }} 기준</span>
      <button class="btn-refresh" @click="loadAll">🔄 새로고침</button>
    </div>
  </div>

  <!-- 부캐 필터 -->
  <div class="option-bar">
    <span class="opt-label">필터</span>
    <label class="checkbox-wrap">
      <input type="checkbox" v-model="hideAlt">
      <span>부캐 숨기기</span>
    </label>
    <div class="alt-chips" v-if="altChars.length">
      <span class="alt-chip" v-for="a in altChars" :key="a">{{ a }}</span>
    </div>
  </div>

  <!-- 로딩 -->
  <div class="loading" v-if="loading">랭킹 불러오는 중...</div>

  <div class="rank-sections" v-if="!loading">

    <!-- 떠오르는 샛별 -->
    <div class="rank-card">
      <div class="rank-card-title">✨ 떠오르는 샛별 <small style="font-size:10px;color:#6060a0;">(최근 6시간)</small></div>
      <div class="rank-card-body">
        <template v-if="filteredRising.length">
          <div class="rank-row" v-for="(r,i) in filteredRising" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}<span v-if="r.JOB" style="color:#6060a0;font-size:11px;"> ({{ r.JOB }})</span></span>
            <span class="rank-val">{{ r.ATK_CNT }}회</span>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

    <!-- MAX 데미지 -->
    <div class="rank-card">
      <div class="rank-card-title">💥 MAX 데미지 TOP5</div>
      <div class="rank-card-body">
        <template v-if="filteredMaxDmg.length">
          <div class="rank-row" v-for="(r,i) in filteredMaxDmg" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}</span>
            <span class="rank-val">{{ fmtNum(r.MAX_DAMAGE) }}</span>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

    <!-- SP 누적 랭킹 -->
    <div class="rank-card">
      <div class="rank-card-title">⭐ SP 누적 랭킹 TOP10</div>
      <div class="rank-card-body">
        <template v-if="filteredSpTop.length">
          <div class="rank-row" v-for="(r,i) in filteredSpTop" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}<span style="color:#6060a0;font-size:11px;"> Lv.{{ r.LV }}</span></span>
            <span class="rank-val">{{ fmtSp(r.TOT_SP) }}</span>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

    <!-- 공격 횟수 -->
    <div class="rank-card">
      <div class="rank-card-title">⚔️ 공격 횟수 TOP10</div>
      <div class="rank-card-body">
        <template v-if="filteredAtkTop.length">
          <div class="rank-row" v-for="(r,i) in filteredAtkTop" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}<span style="color:#6060a0;font-size:11px;"> Lv.{{ r.LV }}</span></span>
            <span class="rank-val">{{ fmtNum(r.ATK_CNT) }}회</span>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

    <!-- 업적 갯수 -->
    <div class="rank-card">
      <div class="rank-card-title">🏅 업적 갯수 TOP5</div>
      <div class="rank-card-body">
        <template v-if="filteredAchv.length">
          <div class="rank-row" v-for="(r,i) in filteredAchv" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}</span>
            <span class="rank-val">{{ r.ACHV_CNT }}개</span>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

    <!-- GP 랭킹 -->
    <div class="rank-card">
      <div class="rank-card-title">💎 GP 랭킹</div>
      <div class="rank-card-body">
        <template v-if="filteredGp.length">
          <div class="rank-row" v-for="(r,i) in filteredGp" :key="i">
            <span class="rank-no" :class="rankClass(i+1)">{{ rankIcon(i+1) }}</span>
            <span class="rank-name" :class="{ alt: isAlt(r.USER_NAME) }">{{ r.USER_NAME }}</span>
            <div style="text-align:right;">
              <div class="rank-val">{{ fmtGp(r.CURRENT_GP) }} GP</div>
              <div class="rank-sub">누적 {{ fmtGp(r.TOTAL_EARNED_GP) }} GP</div>
            </div>
          </div>
        </template>
        <div class="empty" v-else>데이터 없음</div>
      </div>
    </div>

  </div><!-- /rank-sections -->
</div><!-- /app -->

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
(function() {
  var base = '<%=request.getContextPath()%>/loa';

  new Vue({
    el: '#app',
    data: {
      loading: true,
      hideAlt: false,
      altChars: [],
      rising:  [],
      maxDmg:  [],
      spAtk:   [],
      achv:    [],
      gp:      [],
      lastUpdated: ''
    },
    computed: {
      filteredRising:  function() { return this.filterList(this.rising).slice(0, 5); },
      filteredMaxDmg:  function() { return this.filterList(this.maxDmg).slice(0, 5); },
      filteredSpTop:   function() {
        var sorted = this.spAtk.slice().sort(function(a,b){ return Number(b.TOT_SP) - Number(a.TOT_SP); });
        return this.filterList(sorted).slice(0, 10);
      },
      filteredAtkTop:  function() {
        var sorted = this.spAtk.slice().sort(function(a,b){ return Number(b.ATK_CNT) - Number(a.ATK_CNT); });
        return this.filterList(sorted).slice(0, 10);
      },
      filteredAchv:    function() { return this.filterList(this.achv).slice(0, 5); },
      filteredGp:      function() { return this.filterList(this.gp).slice(0, 10); }
    },
    methods: {
      isAlt: function(name) { return this.altChars.indexOf(name) !== -1; },
      filterList: function(list) {
        if (!this.hideAlt) return list;
        var self = this;
        return list.filter(function(r) { return !self.isAlt(r.USER_NAME); });
      },
      rankClass: function(n) {
        if (n === 1) return 'r1';
        if (n === 2) return 'r2';
        if (n === 3) return 'r3';
        return 'rn';
      },
      rankIcon: function(n) {
        if (n === 1) return '👑';
        if (n === 2) return '🥈';
        if (n === 3) return '🥉';
        return n + '위';
      },
      fmtNum: function(v) {
        if (v == null) return '-';
        return Number(v).toLocaleString();
      },
      fmtGp: function(v) {
        if (v == null) return '0.00';
        return Number(v).toFixed(2);
      },
      fmtSp: function(v) {
        // SP 단위 표시 (10000sp=1a, 10000a=1b)
        var sp = Number(v) || 0;
        var b = Math.floor(sp / 100000000);
        var a = Math.floor((sp % 100000000) / 10000);
        var s = sp % 10000;
        if (b > 0) return b + 'b ' + a + 'a';
        if (a > 0) return a + 'a ' + s + 'sp';
        return s + 'sp';
      },
      loadAll: function() {
        var self = this;
        self.loading = true;
        // 부캐 리스트 로드
        fetch(base + '/api/alt-chars')
          .then(function(r){ return r.json(); })
          .then(function(d){ self.altChars = d.list || []; })
          .catch(function(){});
        // 랭킹 로드
        fetch(base + '/api/ranking')
          .then(function(r){ return r.json(); })
          .then(function(d){
            self.rising  = d.rising  || [];
            self.maxDmg  = d.maxDmg  || [];
            self.spAtk   = d.spAtk   || [];
            self.achv    = d.achv    || [];
            self.gp      = d.gp      || [];
            var now = new Date();
            self.lastUpdated = now.getHours() + ':' + String(now.getMinutes()).padStart(2,'0');
            self.loading = false;
          })
          .catch(function(){ self.loading = false; });
      }
    },
    created: function() { this.loadAll(); }
  });
})();
</script>
</body>
</html>
