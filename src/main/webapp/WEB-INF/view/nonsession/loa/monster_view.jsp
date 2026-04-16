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
    body { background: #f0f4f8; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; margin-left: 120px; }

    .wrap { max-width: 1100px; margin: 0 auto; padding: 28px 16px 60px; }

    .page-header { margin-bottom: 22px; }
    .page-title   { font-size: 22px; font-weight: 800; color: #1a2a3a; display: flex; align-items: center; gap: 8px; }
    .page-subtitle{ font-size: 13px; color: #999; margin-top: 4px; }

    /* 검색 */
    .top-controls { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
    .search-wrap  { flex: 1; min-width: 180px; position: relative; }
    .search-wrap input { width: 100%; padding: 10px 14px 10px 36px; border: 1.5px solid #d4dde6; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; transition: border-color .2s; }
    .search-wrap input:focus { border-color: #5b8dd9; }
    .search-wrap .fa { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: #bbb; font-size: 13px; }
    .user-wrap { display: flex; gap: 8px; align-items: center; }
    .user-wrap input { padding: 10px 14px; border: 1.5px solid #d4dde6; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; width: 145px; }
    .user-wrap input:focus { border-color: #5b8dd9; }
    .user-wrap input::placeholder { color: #bbb; }
    .btn-query { background: #5b8dd9; color: #fff; border: none; padding: 10px 20px; border-radius: 24px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-query:hover { background: #3a6fc4; }

    /* 유저 정보 패널 */
    .user-panel { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 14px; padding: 14px 18px; background: #fff; border-radius: 12px; box-shadow: 0 1px 6px rgba(0,0,0,.06); align-items: center; }
    .user-panel .up-name { font-size: 15px; font-weight: 800; color: #1a2a3a; }
    .user-panel .up-job  { font-size: 12px; color: #888; margin-left: 4px; }
    .user-panel .up-mode { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 8px; }
    .up-mode.nm0 { background: #edf2f7; color: #4a5568; }
    .up-mode.nm1 { background: #f5eeff; color: #7d3c98; }
    .up-mode.nm2 { background: #fff0f0; color: #a93226; }
    .up-lv-block { display: flex; flex-direction: column; gap: 4px; min-width: 180px; }
    .up-lv-row   { display: flex; align-items: center; gap: 8px; }
    .up-lv-num   { font-size: 20px; font-weight: 900; color: #2980b9; line-height: 1; }
    .up-lv-arrow { font-size: 13px; color: #aaa; }
    .up-lv-next  { font-size: 13px; color: #27ae60; font-weight: 700; }
    .exp-bar-wrap { position: relative; background: #e8f0fe; border-radius: 8px; height: 10px; overflow: hidden; }
    .exp-bar-fill { height: 100%; background: linear-gradient(90deg, #5b8dd9, #3a6fc4); border-radius: 8px; transition: width .4s; }
    .exp-bar-text { font-size: 10px; color: #5b8dd9; margin-top: 2px; text-align: right; }

    /* 유저 킬 요약 */
    .kill-summary { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; padding: 12px 16px; background: #fff; border-radius: 12px; box-shadow: 0 1px 6px rgba(0,0,0,.06); align-items: center; }
    .kill-summary .ks-label { font-size: 12px; color: #999; margin-right: 4px; }
    .kill-chip { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 700; padding: 4px 12px; border-radius: 14px; }
    .kill-chip.total   { background: #eaf0fb; color: #2c5282; }
    .kill-chip.normal  { background: #f0f4f8; color: #4a5568; }
    .kill-chip.light   { background: #fffde7; color: #b7791f; }
    .kill-chip.dark    { background: #f9f0ff; color: #6b46c1; }
    .kill-chip.yinyang { background: #e8f8f0; color: #276749; }

    .exp-base    { text-decoration: line-through; color: #ccc; font-size: 11px; margin-right: 4px; }
    .exp-adj     { font-weight: 700; }
    .exp-bonus   { color: #27ae60; }
    .exp-penalty { color: #e67e22; }
    .exp-zero    { color: #e74c3c; }
    .exp-arrow   { font-size: 11px; color: #bbb; margin: 0 2px; }
    .exp-pct     { font-size: 10px; margin-left: 3px; }

    /* 난이도 탭 */
    .diff-tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
    .diff-tab  { padding: 8px 22px; border-radius: 22px; border: 2px solid #d4dde6; background: #fff; color: #888; font-size: 13px; font-weight: 700; cursor: pointer; transition: all .18s; white-space: nowrap; }
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
    .mon-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(205px, 1fr)); gap: 5px; }

    /* 카드 */
    .mon-card { background: #fff; border-radius: 14px; padding: 15px 13px 13px; box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 2px solid transparent; transition: box-shadow .18s, border-color .18s; }
    .mon-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.10); }
    .mon-card.diff-normal    { border-color: transparent; }
    .mon-card.diff-nightmare { border-color: #c39bd3; background: #fdf9ff; }
    .mon-card.diff-hell      { border-color: #f1948a; background: #fff8f8; }
    .mon-card.has-kill       { border-color: #68d391 !important; }

    .card-no   { font-size: 10px; color: #ccc; font-family: monospace; margin-bottom: 3px; }
    .card-name { font-size: 14px; font-weight: 800; color: #1a2a3a; margin-bottom: 8px; line-height: 1.3; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
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

    .note-block { margin-top: 8px; font-size: 11px; color: #888; background: #f8f9fa; border-radius: 6px; padding: 5px 8px; line-height: 1.5; }

    /* 킬 통계 블록 */
    .kill-block { margin-top: 8px; border-top: 1px solid #f0f0f0; padding-top: 7px; }
    .kill-title { font-size: 10px; color: #aaa; margin-bottom: 5px; font-weight: 700; }
    .kill-rows  { display: flex; flex-wrap: wrap; gap: 4px; }
    .kill-diff-row { display: flex; align-items: center; gap: 6px; margin-bottom: 3px; font-size: 11px; }
    .kill-diff-label { min-width: 54px; color: #999; font-weight: 600; }
    .kill-tag   { font-size: 11px; font-weight: 700; padding: 1px 7px; border-radius: 10px; }
    .kill-tag.total   { background: #eaf0fb; color: #2c5282; }
    .kill-tag.normal  { background: #f0f4f8; color: #4a5568; }
    .kill-tag.light   { background: #fffde7; color: #b7791f; }
    .kill-tag.dark    { background: #f9f0ff; color: #6b46c1; }
    .kill-tag.yinyang { background: #e8f8f0; color: #276749; }

    .empty   { text-align: center; padding: 60px 0; color: #ccc; }
    .empty .ico { font-size: 34px; margin-bottom: 10px; }
    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }

    .diff-note { font-size: 12px; padding: 8px 14px; border-radius: 8px; margin-bottom: 14px; }
    .diff-note.nightmare { background: #f5eeff; color: #7d3c98; }
    .diff-note.hell      { background: #fff0f0; color: #a93226; }

    @media (max-width: 360px) {
      .mon-grid    { grid-template-columns: 1fr; }
      .up-lv-block { min-width: 0; }
      .search-wrap { min-width: 0; }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">👾 람쥐봇 몬스터 도감</div>
    <div class="page-subtitle">총 {{ monsters.length }}마리
      <span v-if="searchedUser"> &nbsp;·&nbsp; {{ searchedUser }} 킬 현황</span>
    </div>
  </div>

  <div class="top-controls">
    <div class="search-wrap">
      <i class="fa fa-search"></i>
      <input v-model="keyword" placeholder="몬스터 이름 검색...">
    </div>
    <div class="user-wrap">
      <input v-model="userName" placeholder="유저명 (선택)" @keyup.enter="fetchKills">
      <button class="btn-query" @click="fetchKills">조회</button>
    </div>
  </div>

  <!-- 유저 정보 패널 -->
  <div class="user-panel" v-if="searchedUser && userInfo.lv">
    <div>
      <span class="up-name">{{ searchedUser }}</span>
      <span class="up-job">{{ userInfo.job }}</span>
      <span class="up-mode" :class="'nm' + userInfo.nightmareYn" style="margin-left:6px;">
        {{ ['일반','나이트메어','헬'][userInfo.nightmareYn] }}
      </span>
    </div>
    <div class="up-lv-block">
      <div class="up-lv-row">
        <span class="up-lv-num">Lv.{{ userInfo.lv }}</span>
        <span class="up-lv-arrow">→</span>
        <span class="up-lv-next">Lv.{{ userInfo.lv + 1 }} 까지 {{ expRemain.toLocaleString() }} EXP</span>
      </div>
      <div class="exp-bar-wrap">
        <div class="exp-bar-fill" :style="{width: expPct + '%'}"></div>
      </div>
      <div class="exp-bar-text">{{ userInfo.expCur.toLocaleString() }} / {{ userInfo.expNext.toLocaleString() }} EXP ({{ expPct }}%)</div>
    </div>
  </div>

  <!-- 유저 킬 요약 (현재 탭 기준) -->
  <div class="kill-summary" v-if="searchedUser && totalKills > 0">
    <span class="ks-label">{{ searchedUser }} · {{ diffLabel }} 총 킬</span>
    <span class="kill-chip total">🗡️ {{ killSummary.total.toLocaleString() }}</span>
    <span class="kill-chip normal"  v-if="killSummary.normal  > 0">⚔️ 일반 {{ killSummary.normal.toLocaleString() }}</span>
    <span class="kill-chip light"   v-if="killSummary.light   > 0">✨ 빛 {{ killSummary.light.toLocaleString() }}</span>
    <span class="kill-chip dark"    v-if="killSummary.dark    > 0">🌑 다크 {{ killSummary.dark.toLocaleString() }}</span>
    <span class="kill-chip yinyang" v-if="killSummary.yinyang > 0">☯️ 음양 {{ killSummary.yinyang.toLocaleString() }}</span>
  </div>
  <div class="kill-summary" v-else-if="searchedUser && totalKills === 0">
    <span style="color:#ccc;font-size:13px;">{{ searchedUser }}님의 킬 기록이 없습니다.</span>
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
    🔥 헬: HP·ATK ×100 / Lv +400 / EXP ×150 / 드랍SP ×10,000 &nbsp;|&nbsp; ⚠️ 유저 능력치 대폭 삭감 (헌터등급으로 완화)
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
         :class="['diff-' + diff, killOf(m) && killOf(m).KILL_TOTAL > 0 ? 'has-kill' : '']">

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
          <span class="stat-val hp">{{ formatKr(calcHp(m)) }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">ATK</span>
          <span class="stat-val atk">{{ calcAtkMin(m).toLocaleString() }}~{{ calcAtk(m).toLocaleString() }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">EXP</span>
          <span class="stat-val exp" v-if="!searchedUser || !userInfo.lv">{{ formatKr(calcExp(m)) }}</span>
          <span v-else style="display:flex;align-items:center;gap:2px;">
            <span class="exp-base">{{ formatKr(calcExp(m)) }}</span>
            <span class="exp-arrow">→</span>
            <span class="exp-adj" :class="expAdjClass(m)">{{ formatKr(expAdj(m)) }}</span>
            <span class="exp-pct" :class="expAdjClass(m)">{{ expAdjLabel(m) }}</span>
          </span>
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

      <!-- 킬 통계: 현재 탭 난이도 기준 -->
      <template v-if="searchedUser">
        <div class="kill-block" v-if="killOf(m) && diffKill(m).total > 0">
          <div class="kill-title">🗡️ {{ diffKill(m).total.toLocaleString() }}마리</div>
          <div class="kill-rows">
            <span class="kill-tag normal"  v-if="diffKill(m).normal  > 0">⚔️ {{ num(diffKill(m).normal) }}</span>
            <span class="kill-tag light"   v-if="diffKill(m).light   > 0">✨ {{ num(diffKill(m).light) }}</span>
            <span class="kill-tag dark"    v-if="diffKill(m).dark    > 0">🌑 {{ num(diffKill(m).dark) }}</span>
            <span class="kill-tag yinyang" v-if="diffKill(m).yinyang > 0">☯️ {{ num(diffKill(m).yinyang) }}</span>
          </div>
        </div>
        <div class="kill-block" v-else>
          <div style="font-size:11px;color:#ddd;">미처치</div>
        </div>
      </template>
    </div>
  </div>

  <div class="empty" v-else-if="!loading">
    <div class="ico">🔍</div>
    <p>조건에 맞는 몬스터가 없어요</p>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
  var DIFF = {
    normal:    { hpAtk: 1,   lvAdd: 0,   exp: 1,   sp: 1       },
    nightmare: { hpAtk: 100, lvAdd: 150, exp: 50,  sp: 50      },
    hell:      { hpAtk: 100, lvAdd: 400, exp: 150, sp: 10000   }
  };

  var PATTERNS = {
    1: '1:주시', 2: '2:공격', 3: '3:방어',
    4: '4:필살기(×1.5)', 5: '5:흡혈/즉사'
  };

  // 만/억 단위 포맷 (35732500 → 3573만, 153500000 → 1억5350만)
  function formatKr(val) {
    if (!val || val < 10000) return val.toLocaleString();
    var eok = Math.floor(val / 100000000);
    var man = Math.floor((val % 100000000) / 10000);
    if (eok > 0 && man > 0) return eok + '억' + man + '만';
    if (eok > 0)             return eok + '억';
    return man + '만';
  }

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
      killMap: {},
      totalKills: 0,
      userInfo: {},   // { lv, expCur, expNext, job, nightmareYn }
      loading: false,
      userName: '',
      searchedUser: '',
      keyword: '',
      diff: 'normal',
      sortDir: 'asc'
    },
    computed: {
      diffLabel: function() {
        return { normal: '일반', nightmare: '나이트메어', hell: '헬' }[this.diff];
      },
      expPct: function() {
        var cur = parseInt(this.userInfo.expCur || 0);
        var next = parseInt(this.userInfo.expNext || 1);
        if (next <= 0) return 0;
        return Math.min(100, Math.round(cur / next * 1000) / 10);
      },
      expRemain: function() {
        var cur = parseInt(this.userInfo.expCur || 0);
        var next = parseInt(this.userInfo.expNext || 0);
        return Math.max(0, next - cur);
      },
      // 현재 탭 난이도 기준 전체 합산
      killSummary: function() {
        var self = this;
        var pfx = { normal: 'NM0', nightmare: 'NM1', hell: 'NM2' }[self.diff];
        var s = { total: 0, normal: 0, light: 0, dark: 0, yinyang: 0 };
        Object.values(self.killMap).forEach(function(k) {
          s.total   += parseInt(k[pfx + '_TOTAL']   || 0);
          s.normal  += parseInt(k[pfx + '_NORMAL']  || 0);
          s.light   += parseInt(k[pfx + '_LIGHT']   || 0);
          s.dark    += parseInt(k[pfx + '_DARK']    || 0);
          s.yinyang += parseInt(k[pfx + '_YINYANG'] || 0);
        });
        return s;
      },
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
      formatKr: formatKr,
      num: function(v) { return parseInt(v || 0).toLocaleString(); },
      killOf: function(m) { return this.killMap[String(m.MON_NO)] || null; },
      // 현재 탭 난이도의 킬 통계 반환
      diffKill: function(m) {
        var k = this.killOf(m);
        if (!k) return { total: 0, normal: 0, light: 0, dark: 0, yinyang: 0 };
        var pfx = { normal: 'NM0', nightmare: 'NM1', hell: 'NM2' }[this.diff];
        return {
          total:   parseInt(k[pfx + '_TOTAL']   || 0),
          normal:  parseInt(k[pfx + '_NORMAL']  || 0),
          light:   parseInt(k[pfx + '_LIGHT']   || 0),
          dark:    parseInt(k[pfx + '_DARK']    || 0),
          yinyang: parseInt(k[pfx + '_YINYANG'] || 0)
        };
      },
      // 레벨차 EXP 보정 (BossAttackController.renderMonsterCompactLine 동일 로직)
      expMult: function(m) {
        if (!this.userInfo || !this.userInfo.lv) return 1.0;
        var monLv   = this.calcLv(m);
        var userLv  = parseInt(this.userInfo.lv);
        var gap     = userLv - monLv;
        if (gap >= 0) return Math.max(0.1, 1.0 - gap * 0.1);
        return 1.0 + Math.min(-gap, 5) * 0.05;
      },
      expAdj: function(m) {
        return Math.round(this.calcExp(m) * this.expMult(m));
      },
      expAdjClass: function(m) {
        var mult = this.expMult(m);
        if (mult <= 0.1) return 'exp-zero';
        if (mult < 1.0)  return 'exp-penalty';
        if (mult > 1.0)  return 'exp-bonus';
        return 'exp';
      },
      expAdjLabel: function(m) {
        var mult = this.expMult(m);
        if (Math.abs(mult - 1.0) < 0.001) return '';
        var pct = Math.round((mult - 1.0) * 100);
        return pct > 0 ? '(+' + pct + '%)' : '(' + pct + '%)';
      },
      calcLv:     function(m) { return parseInt(m.MON_LV)  + DIFF[this.diff].lvAdd; },
      calcHp:     function(m) { return parseInt(m.MON_HP)  * DIFF[this.diff].hpAtk; },
      calcAtk:    function(m) { return parseInt(m.MON_ATK) * DIFF[this.diff].hpAtk; },
      calcAtkMin: function(m) { return Math.floor(this.calcAtk(m) * 0.5); },
      calcExp:    function(m) { return parseInt(m.MON_EXP) * DIFF[this.diff].exp; },
      calcDropSp: function(m) { return parseInt(m.DROP_SP || 0) * DIFF[this.diff].sp; },
      patternList: function(maxPat) {
        var list = [];
        for (var i = 1; i <= maxPat; i++) list.push({ no: i, label: PATTERNS[i] || (i + ':?') });
        return list;
      },
      fetchKills: function() {
        var self = this;
        var un = self.userName.trim();
        if (!un) { self.killMap = {}; self.totalKills = 0; self.searchedUser = ''; return; }
        fetch('<%=request.getContextPath()%>/loa/api/monster-kills?userName=' + encodeURIComponent(un))
          .then(function(r) { return r.json(); })
          .then(function(data) {
            self.killMap      = data.kills      || {};
            self.totalKills   = data.totalKills || 0;
            self.searchedUser = data.userName   || un;
            sessionStorage.setItem('loaUserName', self.searchedUser);
            self.userInfo     = data.userInfo   || {};
          })
          .catch(function() { alert('킬 통계 조회 중 오류가 발생했습니다.'); });
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
    mounted: function() {
      this.fetchMonsters();
      var params = new URLSearchParams(window.location.search);
      var u = (params.get('userName') || params.get('user') || '').trim();
      if (u) { this.userName = u; this.fetchKills(); }
    }
  });
</script>
</body>
</html>
