<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>장비 랭킹</title>
<script src="https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.min.js"></script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #0f1117; color: #e0e0e0; font-family: 'Segoe UI', sans-serif; min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 20px 16px; }
h1 { font-size: 1.4rem; font-weight: 700; color: #e8c97a; margin-bottom: 6px; }
.subtitle { font-size: 0.8rem; color: #666; margin-bottom: 20px; }

.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
.toolbar input { padding: 8px 12px; background: #1e2130; border: 1px solid #333; border-radius: 8px;
  color: #e0e0e0; font-size: 0.9rem; outline: none; width: 200px; }
.toolbar input:focus { border-color: #e8c97a; }
.toolbar button { padding: 8px 16px; background: #e8c97a; color: #1a1a2e; border: none; border-radius: 8px;
  font-weight: 700; cursor: pointer; font-size: 0.9rem; }
.toolbar button:hover { background: #f5d98b; }
.toolbar .count { color: #888; font-size: 0.85rem; margin-left: auto; }

.loading { text-align: center; padding: 40px; color: #888; }
.error-msg { background: #2c1515; color: #ff6b6b; padding: 12px 16px; border-radius: 8px; margin-bottom: 16px; }

/* 테이블 */
.rank-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.82rem; min-width: 900px; }
thead th {
  background: #1a1e2e; color: #888; font-size: 0.75rem; font-weight: 700;
  padding: 10px 10px; text-align: right; border-bottom: 2px solid #2a2f45;
  cursor: pointer; user-select: none; white-space: nowrap;
  position: sticky; top: 0; z-index: 1;
}
thead th.left { text-align: left; }
thead th:hover { color: #e8c97a; }
thead th.sorted { color: #e8c97a; }
thead th .arr { margin-left: 4px; }

tbody tr:hover td { background: #1a1e2e; }
tbody tr:nth-child(even) td { background: rgba(255,255,255,0.02); }

td { padding: 8px 10px; border-bottom: 1px solid #1a1e2e; vertical-align: middle; }
td.right { text-align: right; font-family: monospace; }
td.left  { text-align: left; }

.rank-num { color: #555; font-size: 0.78rem; width: 36px; }
.rank-1 { color: #ffd700; font-weight: 700; }
.rank-2 { color: #c0c0c0; font-weight: 700; }
.rank-3 { color: #cd7f32; font-weight: 700; }

.uname { color: #e8c97a; font-weight: 600; }
.lv-badge { display: inline-block; background: #1a3a1a; color: #55efc4; border-radius: 6px; padding: 1px 7px; font-size: 0.75rem; }

.val-atk  { color: #fd9644; }
.val-crit { color: #a29bfe; }
.val-dark { color: #74b9ff; }
.val-set  { color: #55efc4; font-size: 0.75rem; }
.val-boss { color: #ff7675; font-weight: 700; }
.val-zero { color: #444; }

.boss-badge { display: inline-block; background: #3d1515; color: #ff7675; border-radius: 4px; padding: 1px 5px; font-size: 0.7rem; margin-left: 2px; }
.val-nerf { color: #fd9644; font-size: 0.78rem; }
</style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="container">
  <h1>🔬 장비 랭킹</h1>
  <p class="subtitle">직업 보너스 미적용 · 헬모드 너프 기준 · 최근 3일 활성 유저 (어둠/보스템 맥스치 반영)
    <span v-if="cachedAt"> · 기준: {{ cachedAtStr }}</span>
  </p>

  <div class="toolbar">
    <button @click="load" :disabled="loading">{{ loading ? '로딩 중...' : '새로고침' }}</button>
    <input v-model="search" placeholder="유저명 검색..." />
    <span class="count" v-if="filtered.length > 0">{{ filtered.length }}명</span>
  </div>

  <div class="loading" v-if="loading">⏳ 전체 유저 스탯 계산 중...</div>
  <div class="error-msg" v-if="error">{{ error }}</div>

  <div class="rank-wrap" v-if="!loading && filtered.length > 0">
    <table>
      <thead>
        <tr>
          <th class="left" style="width:36px;">#</th>
          <th class="left" @click="sort('userName')">유저명 <span class="arr">{{ arrow('userName') }}</span></th>
          <th @click="sort('lv')">레벨 <span class="arr">{{ arrow('lv') }}</span></th>
          <th @click="sort('atkMin')">장비 MIN <span class="arr">{{ arrow('atkMin') }}</span></th>
          <th @click="sort('atkMax')">장비 MAX <span class="arr">{{ arrow('atkMax') }}</span></th>
          <th @click="sort('crit')">크리율% <span class="arr">{{ arrow('crit') }}</span></th>
          <th @click="sort('critDmg')">크리뎀% <span class="arr">{{ arrow('critDmg') }}</span></th>
          <th @click="sort('darkAtkMin')">어둠 MIN <span class="arr">{{ arrow('darkAtkMin') }}</span></th>
          <th @click="sort('darkAtkMax')">어둠 MAX <span class="arr">{{ arrow('darkAtkMax') }}</span></th>
          <th class="left" @click="sort('setInfo')">세트효과 <span class="arr">{{ arrow('setInfo') }}</span></th>
          <th @click="sort('bossBonus')">보스템 보너스 <span class="arr">{{ arrow('bossBonus') }}</span></th>
          <th @click="sort('bossEstMax')" class="sorted">추정 MAX <span class="arr">{{ arrow('bossEstMax') }}</span></th>
          <th @click="sort('hellNerfPct')">헬너프율 <span class="arr">{{ arrow('hellNerfPct') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(r, i) in sorted" :key="r.userName">
          <td class="rank-num left"
              :class="{'rank-1': globalRank(r)===1, 'rank-2': globalRank(r)===2, 'rank-3': globalRank(r)===3}">
            {{ globalRank(r) }}
          </td>
          <td class="left"><span class="uname">{{ r.userName }}</span></td>
          <td class="right"><span class="lv-badge">Lv.{{ r.lv }}</span></td>
          <td class="right val-atk">{{ fmt(r.atkMin) }}</td>
          <td class="right val-atk">{{ fmt(r.atkMax) }}</td>
          <td class="right val-crit">{{ r.crit }}%</td>
          <td class="right val-crit">{{ r.critDmg }}%</td>
          <td class="right" :class="r.darkAtkMin > 0 ? 'val-dark' : 'val-zero'">
            {{ r.darkAtkMin > 0 ? fmt(r.darkAtkMin) : '—' }}
          </td>
          <td class="right" :class="r.darkAtkMax > 0 ? 'val-dark' : 'val-zero'">
            {{ r.darkAtkMax > 0 ? fmt(r.darkAtkMax) : '—' }}
          </td>
          <td class="left">
            <span class="val-set" v-if="r.setInfo">{{ r.setInfo }}</span>
            <span class="val-zero" v-else>—</span>
          </td>
          <td class="right">
            <span v-if="r.bossBonus > 0" class="val-boss">+{{ fmt(r.bossBonus) }}</span>
            <span v-if="r.has7009" class="boss-badge">7009</span>
            <span v-if="r.has7013" class="boss-badge">7013</span>
            <span class="val-zero" v-if="r.bossBonus === 0">—</span>
          </td>
          <td class="right val-boss">{{ fmt(r.bossEstMax) }}</td>
          <td class="right val-nerf">
            <span v-if="r.hellNerfPct > 0">-{{ r.hellNerfPct }}%</span>
            <span v-else class="val-zero">—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</div>

<script>
new Vue({
  el: '#app',
  data: {
    loading: false,
    error: '',
    rows: [],
    cachedAt: null,
    search: '',
    sortKey: 'bossEstMax',
    sortAsc: false,
  },
  mounted() { this.load(); },
  computed: {
    filtered() {
      const q = this.search.trim().toLowerCase();
      return q ? this.rows.filter(r => r.userName.toLowerCase().includes(q)) : this.rows;
    },
    sorted() {
      const key = this.sortKey, asc = this.sortAsc;
      return [...this.filtered].sort((a, b) => {
        let va = a[key], vb = b[key];
        if (typeof va === 'string') return asc ? va.localeCompare(vb) : vb.localeCompare(va);
        return asc ? va - vb : vb - va;
      });
    },
    cachedAtStr() {
      if (!this.cachedAt) return '';
      const d = new Date(this.cachedAt);
      return d.toLocaleString('ko-KR', { hour12: false });
    },
  },
  methods: {
    load() {
      this.loading = true; this.error = ''; this.rows = [];
      fetch('/loa/api/equip-rank')
        .then(r => r.json())
        .then(d => {
          if (d.error) { this.error = d.error; }
          else { this.rows = d.list || []; this.cachedAt = d.cachedAt || null; }
        })
        .catch(() => { this.error = '서버 오류가 발생했습니다.'; })
        .finally(() => { this.loading = false; });
    },
    sort(key) {
      if (this.sortKey === key) { this.sortAsc = !this.sortAsc; }
      else { this.sortKey = key; this.sortAsc = false; }
    },
    arrow(key) {
      if (this.sortKey !== key) return '↕';
      return this.sortAsc ? '↑' : '↓';
    },
    /* 검색 결과와 무관하게 전체 기준 순위 */
    globalRank(row) {
      return this.rows.indexOf(row) + 1;
    },
    fmt(n) {
      if (n == null) return '0';
      return Number(n).toLocaleString('ko-KR');
    },
  }
});
</script>
</body>
</html>
