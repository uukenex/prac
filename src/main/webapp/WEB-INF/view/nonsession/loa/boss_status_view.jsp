<%@ page pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<title>보스현황 | 람쥐봇</title>
<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body {
  background: #0a0818;
  color: #c8c0e0;
  font-family: 'Segoe UI', 'Malgun Gothic', sans-serif;
  min-height: 100vh;
  overflow-x: hidden;
}
.wrap { max-width: 900px; margin: 0 auto; padding: 24px 16px 80px; }

/* ── 제목 ── */
.page-title {
  font-size: 22px; font-weight: 900; color: #e8c870; margin-bottom: 20px;
  display: flex; align-items: center; gap: 10px;
}
.page-title .sub { font-size: 13px; font-weight: 400; color: #7070a0; }

/* ── 카드 ── */
.card { background: #100e1e; border: 1px solid #2a2048; border-radius: 12px; padding: 20px; margin-bottom: 18px; }
.card-title {
  font-size: 14px; font-weight: 800; color: #c9a96e;
  margin-bottom: 14px; border-bottom: 1px solid #2a2048;
  padding-bottom: 8px; display: flex; align-items: center; gap: 6px;
}

/* ── 스탯 그리드 ── */
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
.stat-item { background: #14112a; border: 1px solid #2a2048; border-radius: 8px; padding: 12px 14px; text-align: center; }
.stat-label { font-size: 11px; color: #7070a0; margin-bottom: 6px; }
.stat-value { font-size: 20px; font-weight: 900; color: #e8c870; word-break: break-all; }
.stat-value.danger { color: #ff6060; }
.stat-value.good   { color: #60d060; }
.stat-value.info   { color: #60b0ff; }

/* ── HP 게이지 ── */
.hp-section { margin-bottom: 18px; }
.hp-name-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.boss-name { font-size: 18px; font-weight: 900; color: #fff; }
.hp-bar-wrap { background: #18152e; border-radius: 8px; overflow: hidden; height: 26px; position: relative; }
.hp-bar { height: 100%; border-radius: 8px; background: linear-gradient(90deg, #9b1c1c 0%, #ef4444 100%); transition: width .4s ease; min-width: 2px; }
.hp-bar-text {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  font-size: 12px; font-weight: 700; color: #fff; text-shadow: 0 0 4px #000; white-space: nowrap;
}

/* ── 대기 패널 ── */
.waiting-panel {
  background: #150d28; border: 1px solid #3a1f6a; border-radius: 12px;
  padding: 28px 20px; text-align: center; margin-bottom: 18px;
}
.waiting-icon { font-size: 48px; margin-bottom: 12px; }
.waiting-title { font-size: 18px; font-weight: 900; color: #a070ff; margin-bottom: 8px; }
.waiting-desc  { font-size: 13px; color: #7060a0; margin-bottom: 20px; }
.countdown { font-size: 32px; font-weight: 900; color: #c9a96e; font-variant-numeric: tabular-nums; }
.countdown-label { font-size: 12px; color: #7070a0; margin-bottom: 6px; }

/* ── 없음 패널 ── */
.none-panel {
  background: #100e1e; border: 1px solid #2a2048; border-radius: 12px;
  padding: 40px 20px; text-align: center; margin-bottom: 18px;
}

/* ── 마지막 처치 메시지 ── */
.kill-msg-box {
  background: #1a0e2a; border: 1px solid #4a2068; border-radius: 10px;
  padding: 14px 16px; font-size: 13px; color: #c9a0e0; white-space: pre-wrap; line-height: 1.6;
}

/* ── 다음 보스 예고 ── */
.next-boss-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; margin-top: 14px; }
.next-stat-item { background: #1a1030; border: 1px solid #3a2060; border-radius: 8px; padding: 10px; text-align: center; }
.next-stat-label { font-size: 10px; color: #8060a0; margin-bottom: 4px; }
.next-stat-value { font-size: 16px; font-weight: 800; color: #b090e0; }

/* ── 배틀로그 테이블 ── */
.log-table-wrap { overflow-x: auto; }
.log-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 380px; }
.log-table th {
  background: #14112a; color: #9090b8; font-weight: 700;
  padding: 8px 10px; text-align: left; border-bottom: 1px solid #2a2048; white-space: nowrap;
}
.log-table td { padding: 7px 10px; border-bottom: 1px solid #1a1830; vertical-align: middle; }
.log-table tr:hover td { background: #141230; }
.kill-badge { display: inline-block; background: #7f1d1d; color: #fca5a5; font-size: 11px; font-weight: 700; padding: 2px 7px; border-radius: 10px; }
.crit-badge { display: inline-block; background: #431407; color: #fb923c; font-size: 11px; padding: 2px 5px; border-radius: 6px; }
.job-badge  { display: inline-block; background: #1a1a4a; color: #9090d8; font-size: 11px; padding: 2px 6px; border-radius: 6px; }
.dmg-val    { font-weight: 700; color: #e8c870; font-variant-numeric: tabular-nums; }
.dmg-crit   { color: #fb923c; }
.time-txt   { color: #6060a0; font-size: 12px; }

/* ── 정렬 토글 ── */
.sort-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.sort-btn {
  background: #1e1840; border: 1px solid #3a3060; color: #9090b8;
  padding: 5px 14px; border-radius: 20px; cursor: pointer; font-size: 12px; font-weight: 600;
}
.sort-btn.active { background: #2a2450; color: #c9a96e; border-color: #c9a96e; }

/* ── 새로고침 ── */
.refresh-row { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.refresh-btn {
  background: #1e1840; border: 1px solid #3a3060; color: #c9a96e;
  padding: 8px 18px; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 600;
}
.refresh-btn:hover { background: #2a2450; }
.last-updated { font-size: 12px; color: #6060a0; }

/* ── 반응형 ── */
@media (max-width: 600px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .stat-value { font-size: 16px; }
  .countdown { font-size: 24px; }
  .next-boss-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>

<div id="app" class="wrap">
  <div class="page-title">
    🐉 헬보스 현황
    <span class="sub">상급악마 실시간 현황</span>
  </div>

  <div class="refresh-row">
    <button class="refresh-btn" @click="loadData">🔄 새로고침</button>
    <span class="last-updated" v-if="lastUpdated">{{ lastUpdated }} 기준</span>
  </div>

  <div v-if="loading" style="text-align:center;padding:40px;color:#7070a0;font-size:14px;">불러오는 중...</div>

  <template v-if="!loading">

    <!-- ═══ 생존 중 ═══ -->
    <template v-if="status === 'ALIVE'">
      <div class="card">
        <div class="card-title">⚔ 현재 헬보스</div>
        <!-- HP 게이지 -->
        <div class="hp-section">
          <div class="hp-name-row">
            <span class="boss-name">{{ boss.BOSS_TYPE || '상급악마' }}</span>
            <span style="font-size:13px;color:#9090b8;">HP {{ hpLabel }}</span>
          </div>
          <div class="hp-bar-wrap">
            <div class="hp-bar" :style="{ width: hpPct + '%' }"></div>
            <span class="hp-bar-text">{{ hpPct }}%</span>
          </div>
        </div>
        <!-- 스탯 -->
        <div class="stat-grid">
          <div class="stat-item">
            <div class="stat-label">최대 HP</div>
            <div class="stat-value">{{ fmtHp(boss.MAX_HP, boss.MAX_HP_EXT) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">잔여 HP</div>
            <div class="stat-value good">{{ fmtHp(boss.CUR_HP, boss.CUR_HP_EXT) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">공격력</div>
            <div class="stat-value danger">{{ fmt(boss.ATK_POWER) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">회피율</div>
            <div class="stat-value info">{{ fmt(boss.EVADE_RATE) }}%</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">크리방어율</div>
            <div class="stat-value">{{ fmt(boss.CRIT_DEF_RATE) }}%</div>
          </div>
          <div class="stat-item" v-if="boss.DEBUFF > 0">
            <div class="stat-label">디버프</div>
            <div class="stat-value danger">{{ fmt(boss.DEBUFF) }}</div>
          </div>
        </div>
      </div>

      <!-- 공격 로그 -->
      <div class="card">
        <div class="card-title">📜 공격 기록 <span style="font-size:11px;font-weight:400;color:#6060a0;">(최근 50건)</span></div>
        <div class="sort-row">
          <button class="sort-btn" :class="{ active: sortDesc }" @click="sortDesc = true">최신순</button>
          <button class="sort-btn" :class="{ active: !sortDesc }" @click="sortDesc = false">오래된순</button>
        </div>
        <div v-if="sortedLog.length === 0" style="text-align:center;color:#5050a0;padding:20px;">공격 기록이 없습니다.</div>
        <div class="log-table-wrap" v-else>
          <table class="log-table">
            <thead>
              <tr>
                <th>#</th>
                <th>유저</th>
                <th>직업</th>
                <th>데미지</th>
                <th>시각</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in sortedLog" :key="idx">
                <td style="color:#5050a0;font-size:12px;">{{ idx + 1 }}</td>
                <td style="font-weight:600;">{{ row.USER_NAME }}</td>
                <td><span class="job-badge">{{ row.JOB || '-' }}</span></td>
                <td>
                  <span class="dmg-val" :class="{ 'dmg-crit': row.ATK_CRIT_YN == '1' }">
                    {{ fmt(row.ATK_DMG) }}
                  </span>
                  <span v-if="row.ATK_CRIT_YN == '1'" class="crit-badge">⚡크리</span>
                </td>
                <td class="time-txt">{{ fmtDate(row.INSERT_DATE) }}</td>
                <td>
                  <span v-if="row.KILL_YN == '1'" class="kill-badge">💀 처치</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- ═══ 등장 대기 중 ═══ -->
    <template v-if="status === 'WAITING'">
      <div class="waiting-panel">
        <div class="waiting-icon">⏳</div>
        <div class="waiting-title">상급악마 재정비 중</div>
        <div class="waiting-desc">다음 등장까지</div>
        <div class="countdown-label">남은 시간</div>
        <div class="countdown">{{ countdownText }}</div>
      </div>

      <!-- 다음 보스 예고 -->
      <div class="card" v-if="boss && boss.MAX_HP">
        <div class="card-title">🔮 다음 상급악마 예고 정보</div>
        <div class="next-boss-grid">
          <div class="next-stat-item">
            <div class="next-stat-label">최대 HP</div>
            <div class="next-stat-value">??</div>
          </div>
          <div class="next-stat-item">
            <div class="next-stat-label">공격력</div>
            <div class="next-stat-value">??</div>
          </div>
          <div class="next-stat-item">
            <div class="next-stat-label">회피율</div>
            <div class="next-stat-value">??</div>
          </div>
          <div class="next-stat-item">
            <div class="next-stat-label">크리방어율</div>
            <div class="next-stat-value">??</div>
          </div>
        </div>
      </div>

      <!-- 마지막 처치 결과 -->
      <div class="card" v-if="lastKillMsg">
        <div class="card-title">🏆 최근 처치 결과</div>
        <div class="kill-msg-box">{{ lastKillMsg }}</div>
      </div>
    </template>

    <!-- ═══ 보스 없음 ═══ -->
    <div v-if="status === 'NONE'" class="none-panel">
      <div style="font-size:40px;margin-bottom:12px;">😴</div>
      <div style="font-size:16px;font-weight:700;color:#5050a0;">현재 출현한 상급악마가 없습니다.</div>
      <div v-if="lastKillMsg" style="margin-top:18px;">
        <div class="kill-msg-box">{{ lastKillMsg }}</div>
      </div>
    </div>

  </template>
</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2.7.16/dist/vue.min.js"></script>
<script>
new Vue({
  el: '#app',
  data: {
    loading: true,
    status: 'NONE',     // ALIVE | WAITING | NONE
    boss: {},
    startDate: '',
    spawnRemainMs: 0,
    recentLog: [],
    lastKillMsg: '',
    lastUpdated: '',
    sortDesc: true,
    _cdTimer: null,
    _autoTimer: null
  },
  computed: {
    hpPct: function () {
      var cur = parseFloat(this.boss.CUR_HP) || 0;
      var max = parseFloat(this.boss.MAX_HP) || 0;
      if (max <= 0) return 0;
      return Math.max(0, Math.min(100, Math.round(cur / max * 100)));
    },
    hpLabel: function () {
      return this.fmtHp(this.boss.CUR_HP, this.boss.CUR_HP_EXT) +
             ' / ' + this.fmtHp(this.boss.MAX_HP, this.boss.MAX_HP_EXT);
    },
    sortedLog: function () {
      if (!this.recentLog || this.recentLog.length === 0) return [];
      var arr = this.recentLog.slice();
      if (this.sortDesc) {
        // DESC: 이미 서버에서 정렬된 상태
        return arr;
      } else {
        // ASC: 오래된순
        return arr.reverse();
      }
    }
  },
  mounted: function () {
    this.loadData();
    var self = this;
    this._autoTimer = setInterval(function () { self.loadData(); }, 30000);
  },
  beforeDestroy: function () {
    if (this._cdTimer)    clearInterval(this._cdTimer);
    if (this._autoTimer)  clearInterval(this._autoTimer);
  },
  methods: {
    loadData: function () {
      var self = this;
      self.loading = true;
      var base = '<%=request.getContextPath()%>';
      fetch(base + '/loa/api/boss-status')
        .then(function (r) { return r.json(); })
        .then(function (d) {
          self.status       = d.status       || 'NONE';
          self.boss         = d.boss         || {};
          self.startDate    = d.startDate    || '';
          self.spawnRemainMs= d.spawnRemainMs|| 0;
          self.recentLog    = d.recentLog    || [];
          self.lastKillMsg  = (d.lastKillMsg || '').replace(/♬/g, '\n');

          var now = new Date();
          self.lastUpdated = String(now.getHours()).padStart(2,'0') + ':' +
                             String(now.getMinutes()).padStart(2,'0') + ':' +
                             String(now.getSeconds()).padStart(2,'0');

          if (self.status === 'WAITING') {
            self.startCountdown(self.spawnRemainMs);
          } else {
            if (self._cdTimer) { clearInterval(self._cdTimer); self._cdTimer = null; }
            self.countdownText = '';
          }
          self.loading = false;
        })
        .catch(function () { self.loading = false; });
    },

    startCountdown: function (initialMs) {
      if (this._cdTimer) clearInterval(this._cdTimer);
      var self = this;
      var endTime = Date.now() + initialMs;

      function tick() {
        var diff = endTime - Date.now();
        if (diff <= 0) {
          self.countdownText = '곧 등장!';
          clearInterval(self._cdTimer);
          // 자동 갱신
          setTimeout(function () { self.loadData(); }, 3000);
          return;
        }
        var h = Math.floor(diff / 3600000);
        var m = Math.floor((diff % 3600000) / 60000);
        var s = Math.floor((diff % 60000) / 1000);
        self.countdownText =
          (h > 0 ? h + '시간 ' : '') +
          String(m).padStart(2, '0') + '분 ' +
          String(s).padStart(2, '0') + '초';
      }
      tick();
      this._cdTimer = setInterval(tick, 1000);
    },

    fmtHp: function (val, ext) {
      if (val == null) return '?';
      var n = parseFloat(val);
      var e = (ext || '').trim();
      if (isNaN(n)) return '?';
      if (e) return n.toLocaleString() + e;
      return n.toLocaleString();
    },

    fmt: function (v) {
      if (v == null) return '0';
      return Number(v).toLocaleString();
    },

    fmtDate: function (v) {
      if (!v) return '-';
      var d = new Date(v);
      if (isNaN(d)) return String(v);
      var mo = String(d.getMonth() + 1).padStart(2, '0');
      var dd = String(d.getDate()).padStart(2, '0');
      var hh = String(d.getHours()).padStart(2, '0');
      var mm = String(d.getMinutes()).padStart(2, '0');
      var ss = String(d.getSeconds()).padStart(2, '0');
      return mo + '/' + dd + ' ' + hh + ':' + mm + ':' + ss;
    }
  }
});
</script>
</body>
</html>
