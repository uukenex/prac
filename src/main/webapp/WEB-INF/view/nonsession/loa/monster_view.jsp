<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 몬스터 도감</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f0f4f8; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; }

    .wrap { max-width: 1100px; margin: 0 auto; padding: 28px 16px 60px; }

    .page-header { margin-bottom: 22px; }
    .page-title   { font-size: 22px; font-weight: 800; color: #1a2a3a; display: flex; align-items: center; gap: 8px; }
    .page-subtitle{ font-size: 13px; color: #999; margin-top: 4px; }

    /* 검색 */
    .top-controls { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
    .search-wrap  { flex: 1; min-width: 200px; position: relative; }
    .search-wrap input { width: 100%; padding: 10px 14px 10px 36px; border: 1.5px solid #d4dde6; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; transition: border-color .2s; }
    .search-wrap input:focus { border-color: #5b8dd9; }
    .search-wrap .fa { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: #bbb; font-size: 13px; }

    /* 난이도 탭 */
    .diff-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
    .diff-tab  { padding: 8px 22px; border-radius: 22px; border: 2px solid #d4dde6; background: #fff; color: #888; font-size: 13px; font-weight: 700; cursor: pointer; transition: all .18s; }
    .diff-tab:hover { border-color: #5b8dd9; color: #5b8dd9; }
    .diff-tab.normal.active   { background: #5b8dd9; border-color: #5b8dd9; color: #fff; }
    .diff-tab.nightmare.active{ background: #9b59b6; border-color: #9b59b6; color: #fff; }
    .diff-tab.hell.active     { background: #e74c3c; border-color: #e74c3c; color: #fff; }

    /* 상태바 */
    .bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px; }
    .count-label  { font-size: 13px; color: #aaa; }
    .count-label strong { color: #1a2a3a; }
    .btn-sort     { padding: 4px 13px; border-radius: 16px; border: 1.5px solid #d4dde6; background: #fff; color: #888; font-size: 12px; cursor: pointer; transition: all .15s; display: flex; align-items: center; gap: 4px; }
    .btn-sort:hover { border-color: #5b8dd9; color: #5b8dd9; }

    /* 그리드 */
    .mon-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 13px; }

    /* 카드 */
    .mon-card { background: #fff; border-radius: 14px; padding: 15px 13px 13px; box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 2px solid transparent; transition: box-shadow .18s, border-color .18s; }
    .mon-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.10); }
    .mon-card.diff-normal    { border-color: transparent; }
    .mon-card.diff-nightmare { border-color: #c39bd3; background: #fdf9ff; }
    .mon-card.diff-hell      { border-color: #f1948a; background: #fff8f8; }

    .card-no   { font-size: 10px; color: #ccc; font-family: monospace; margin-bottom: 3px; }
    .card-name { font-size: 14px; font-weight: 800; color: #1a2a3a; margin-bottom: 8px; line-height: 1.3; display: flex; align-items: center; gap: 6px; }
    .diff-badge { font-size: 9px; font-weight: 700; padding: 2px 6px; border-radius: 6px; }
    .badge-nm  { background: #e8d5f5; color: #9b59b6; }
    .badge-hel { background: #fde8e8; color: #e74c3c; }

    .stat-block { margin-bottom: 8px; }
    .stat-row   { display: flex; justify-content: space-between; font-size: 12px; padding: 2px 0; }
    .stat-label { color: #999; }
    .stat-val   { font-weight: 600; color: #2c3e50; }
    .stat-val.hp    { color: #e74c3c; }
    .stat-val.atk   { color: #e67e22; }
    .stat-val.lv    { color: #2980b9; }
    .stat-val.exp   { color: #27ae60; }
    .stat-val.sp    { color: #c9a96e; }

    .divider { border: none; border-top: 1px solid #f0f0f0; margin: 8px 0; }

    .pattern-block { font-size: 11px; color: #aaa; margin-top: 4px; }
    .pattern-tag   { display: inline-block; background: #f0f4f8; border-radius: 5px; padding: 1px 6px; margin: 2px 2px 0 0; color: #6b7c93; font-size: 10px; }
    .pattern-tag.p4 { background: #fdf0f0; color: #c0392b; }
    .pattern-tag.p5 { background: #1a0000; color: #ff4444; }

    .drop-block { margin-top: 6px; font-size: 12px; }
    .drop-label { color: #bbb; font-size: 10px; display: block; margin-bottom: 2px; }
    .drop-name  { font-weight: 600; color: #5b8dd9; }
    .drop-sp    { color: #c9a96e; font-weight: 700; margin-left: 6px; }

    .note-block { margin-top: 8px; font-size: 11px; color: #888; background: #f8f9fa; border-radius: 6px; padding: 5px 8px; line-height: 1.5; }

    .empty   { text-align: center; padding: 60px 0; color: #ccc; }
    .empty .ico { font-size: 34px; margin-bottom: 10px; }
    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }

    /* 난이도별 note */
    .diff-note { font-size: 12px; padding: 8px 14px; border-radius: 8px; margin-bottom: 14px; }
    .diff-note.nightmare { background: #f5eeff; color: #7d3c98; }
    .diff-note.hell      { background: #fff0f0; color: #a93226; }
  </style>
</head>
<body>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">👾 람쥐봇 몬스터 도감</div>
    <div class="page-subtitle">총 {{ monsters.length }}마리 · 현재 표시 <strong>{{ filteredMonsters.length }}</strong>마리</div>
  </div>

  <div class="top-controls">
    <div class="search-wrap">
      <i class="fa fa-search"></i>
      <input v-model="keyword" placeholder="몬스터 이름 검색...">
    </div>
  </div>

  <!-- 난이도 탭 -->
  <div class="diff-tabs">
    <button class="diff-tab normal"    :class="{active: diff==='normal'}"    @click="diff='normal'">⚔️ 일반</button>
    <button class="diff-tab nightmare" :class="{active: diff==='nightmare'}" @click="diff='nightmare'">💜 나이트메어</button>
    <button class="diff-tab hell"      :class="{active: diff==='hell'}"      @click="diff='hell'">🔥 헬</button>
  </div>

  <div class="diff-note nightmare" v-if="diff==='nightmare'">
    💜 나이트메어: HP·ATK ×100 / Lv +150 / EXP ×50 / 드랍SP ×50
  </div>
  <div class="diff-note hell" v-if="diff==='hell'">
    🔥 헬: HP·ATK ×100 / Lv +400 / EXP ×150 / 드랍SP ×100,000 &nbsp;|&nbsp; ⚠️ 유저 능력치 대폭 삭감 (헌터등급으로 완화)
  </div>

  <div class="bar">
    <div></div>
    <div style="display:flex;align-items:center;gap:10px;">
      <button class="btn-sort" @click="toggleSort">
        <span>번호순</span>
        <span>{{ sortDir==='asc' ? '▲' : '▼' }}</span>
      </button>
      <div class="count-label">현재 표시 <strong>{{ filteredMonsters.length }}</strong>마리</div>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

  <div class="mon-grid" v-else-if="filteredMonsters.length > 0">
    <div class="mon-card" v-for="m in filteredMonsters" :key="m.MON_NO"
         :class="'diff-' + diff">

      <div class="card-no">#{{ m.MON_NO }}</div>
      <div class="card-name">
        {{ monIcon(m) }} {{ m.MON_NAME }}
        <span class="diff-badge badge-nm"  v-if="diff==='nightmare'">나메</span>
        <span class="diff-badge badge-hel" v-if="diff==='hell'">헬</span>
      </div>

      <div class="stat-block">
        <div class="stat-row">
          <span class="stat-label">레벨</span>
          <span class="stat-val lv">Lv.{{ calcLv(m) }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">HP</span>
          <span class="stat-val hp">{{ calcHp(m).toLocaleString() }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">ATK</span>
          <span class="stat-val atk">{{ calcAtkMin(m).toLocaleString() }}~{{ calcAtk(m).toLocaleString() }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">EXP</span>
          <span class="stat-val exp">{{ calcExp(m).toLocaleString() }}</span>
        </div>
        <div class="stat-row" v-if="calcDropSp(m) > 0">
          <span class="stat-label">드랍SP</span>
          <span class="stat-val sp">{{ formatSp(calcDropSp(m)) }}</span>
        </div>
      </div>

      <hr class="divider">

      <!-- 패턴 -->
      <div class="pattern-block" v-if="m.MON_PATTEN > 0">
        <span class="pattern-tag" v-for="p in patternList(m.MON_PATTEN)" :key="p.no"
              :class="{'p4': p.no===4, 'p5': p.no===5}">
          {{ p.label }}
        </span>
      </div>

      <!-- 드랍 아이템 -->
      <div class="drop-block" v-if="m.MON_DROP">
        <span class="drop-label">📦 드랍</span>
        <span class="drop-name">{{ m.MON_DROP }}</span>
      </div>

      <!-- 노트 -->
      <div class="note-block" v-if="m.MON_NOTE">{{ m.MON_NOTE }}</div>
    </div>
  </div>

  <div class="empty" v-else-if="!loading">
    <div class="ico">🔍</div>
    <p>조건에 맞는 몬스터가 없어요</p>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
  // 난이도 배율 상수 (BossAttackController 와 동일)
  var DIFF = {
    normal:    { hpAtk: 1,   lvAdd: 0,   exp: 1,   sp: 1        },
    nightmare: { hpAtk: 100, lvAdd: 150, exp: 50,  sp: 50       },
    hell:      { hpAtk: 100, lvAdd: 400, exp: 150, sp: 100000   }
  };

  var PATTERNS = {
    1: '1:주시',
    2: '2:공격',
    3: '3:방어',
    4: '4:필살기(×1.5)',
    5: '5:흡혈/즉사'
  };

  // SP 단위 포맷 (1a = 10000, 1b = 10000^2, ...)
  function formatSp(val) {
    if (val <= 0) return '0';
    var units = ['', 'a', 'b', 'c', 'd'];
    var base = 10000;
    for (var i = units.length - 1; i >= 1; i--) {
      var div = Math.pow(base, i);
      if (val >= div) {
        var n = val / div;
        return (Math.round(n * 100) / 100).toLocaleString() + ' ' + units[i] + ' SP';
      }
    }
    return val.toLocaleString() + ' SP';
  }

  var MON_ICONS = {
    boss: ['dragon', 'demon', 'titan', 'leviathan', 'golem'],
  };
  function monIcon(m) {
    if (m.MON_LV >= 100) return '🐲';
    if (m.MON_LV >= 50)  return '👿';
    if (m.MON_LV >= 30)  return '🗡️';
    if (m.MON_LV >= 10)  return '🐺';
    return '🐭';
  }

  new Vue({
    el: '#app',
    data: {
      monsters: [],
      loading: false,
      keyword: '',
      diff: 'normal',
      sortDir: 'asc'
    },
    computed: {
      filteredMonsters: function() {
        var self = this;
        var kw = self.keyword.trim();
        var list = self.monsters.filter(function(m) {
          return !kw || m.MON_NAME.indexOf(kw) !== -1;
        });
        var dir = self.sortDir === 'asc' ? 1 : -1;
        return list.slice().sort(function(a, b) {
          return dir * (parseInt(a.MON_NO) - parseInt(b.MON_NO));
        });
      }
    },
    methods: {
      toggleSort: function() { this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc'; },
      monIcon: monIcon,
      formatSp: formatSp,
      calcLv:  function(m) { return parseInt(m.MON_LV)  + DIFF[this.diff].lvAdd; },
      calcHp:  function(m) { return parseInt(m.MON_HP)  * DIFF[this.diff].hpAtk; },
      calcAtk: function(m) { return parseInt(m.MON_ATK) * DIFF[this.diff].hpAtk; },
      calcAtkMin: function(m) { return Math.floor(this.calcAtk(m) * 0.5); },
      calcExp: function(m) { return parseInt(m.MON_EXP) * DIFF[this.diff].exp; },
      calcDropSp: function(m) { return parseInt(m.DROP_SP || 0) * DIFF[this.diff].sp; },
      patternList: function(maxPat) {
        var list = [];
        for (var i = 1; i <= maxPat; i++) {
          list.push({ no: i, label: PATTERNS[i] || (i + ':?') });
        }
        return list;
      },
      fetchMonsters: function() {
        var self = this;
        self.loading = true;
        fetch('<%=request.getContextPath()%>/loa/api/monsters')
          .then(function(r) { return r.json(); })
          .then(function(data) { self.monsters = data.monsters || []; })
          .catch(function() { alert('데이터 로드 중 오류가 발생했습니다.'); })
          .finally(function() { self.loading = false; });
      }
    },
    mounted: function() { this.fetchMonsters(); }
  });
</script>
</body>
</html>
