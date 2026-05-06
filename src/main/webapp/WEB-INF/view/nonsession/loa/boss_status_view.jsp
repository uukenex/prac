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
.wrap {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 16px 80px;
  overflow-x: hidden;
}

/* ── 제목 ── */
.page-title {
  font-size: 22px;
  font-weight: 900;
  color: #e8c870;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title .sub {
  font-size: 13px;
  font-weight: 400;
  color: #7070a0;
}

/* ── 카드 공통 ── */
.card {
  background: #100e1e;
  border: 1px solid #2a2048;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 18px;
}
.card-title {
  font-size: 14px;
  font-weight: 800;
  color: #c9a96e;
  margin-bottom: 14px;
  border-bottom: 1px solid #2a2048;
  padding-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ── 보스 기본정보 그리드 ── */
.boss-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.boss-stat-item {
  background: #14112a;
  border: 1px solid #2a2048;
  border-radius: 8px;
  padding: 12px 14px;
  text-align: center;
}
.boss-stat-label {
  font-size: 11px;
  color: #7070a0;
  margin-bottom: 6px;
}
.boss-stat-value {
  font-size: 20px;
  font-weight: 900;
  color: #e8c870;
  word-break: break-all;
}
.boss-stat-value.danger { color: #ff6060; }
.boss-stat-value.good   { color: #60d060; }

/* ── HP 게이지 ── */
.hp-section { margin-bottom: 18px; }
.hp-bar-wrap {
  background: #18152e;
  border-radius: 8px;
  overflow: hidden;
  height: 22px;
  margin-top: 8px;
  position: relative;
}
.hp-bar {
  height: 100%;
  border-radius: 8px;
  background: linear-gradient(90deg, #c00 0%, #e04040 100%);
  transition: width .5s ease;
  min-width: 2px;
}
.hp-bar-text {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 0 4px #000;
  white-space: nowrap;
}

/* ── 사망 표시 ── */
.boss-dead-panel {
  background: #1a0808;
  border: 1px solid #5a1010;
  border-radius: 12px;
  padding: 28px 20px;
  text-align: center;
  margin-bottom: 18px;
}
.boss-dead-icon { font-size: 48px; margin-bottom: 12px; }
.boss-dead-title { font-size: 18px; font-weight: 900; color: #ff6060; margin-bottom: 8px; }
.boss-dead-desc  { font-size: 14px; color: #906060; margin-bottom: 12px; }
.boss-next-time  { font-size: 22px; font-weight: 900; color: #e8c870; }
.boss-next-label { font-size: 12px; color: #7070a0; margin-bottom: 4px; }
.countdown       { font-size: 28px; font-weight: 900; color: #c9a96e; font-variant-numeric: tabular-nums; }

/* ── 배틀로그 테이블 ── */
.log-table-wrap { overflow-x: auto; }
.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 400px;
}
.log-table th {
  background: #14112a;
  color: #9090b8;
  font-weight: 700;
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid #2a2048;
  white-space: nowrap;
}
.log-table td {
  padding: 7px 10px;
  border-bottom: 1px solid #1a1830;
  vertical-align: middle;
}
.log-table tr:hover td { background: #141230; }
.kill-badge {
  display: inline-block;
  background: #8b1a1a;
  color: #ffaaaa;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
}
.job-badge {
  display: inline-block;
  background: #1a1a4a;
  color: #9090d8;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 6px;
}
.dmg-value { font-weight: 700; color: #e8c870; font-variant-numeric: tabular-nums; }
.crit-dmg  { color: #ff9050; }
.time-text { color: #6060a0; font-size: 12px; }

/* ── 새로고침 버튼 ── */
.refresh-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.refresh-btn {
  background: #1e1840;
  border: 1px solid #3a3060;
  color: #c9a96e;
  padding: 8px 18px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
.refresh-btn:hover { background: #2a2450; }
.last-updated { font-size: 12px; color: #6060a0; }

/* ── 반응형 ── */
@media (max-width: 600px) {
  .boss-stat-grid { grid-template-columns: repeat(2, 1fr); }
  .boss-stat-value { font-size: 16px; }
  .countdown { font-size: 22px; }
  .boss-next-time { font-size: 18px; }
}
</style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>

<div id="app" class="wrap">
  <div class="page-title">
    🐉 보스현황
    <span class="sub">실시간 보스 공략 현황</span>
  </div>

  <div class="refresh-row">
    <button class="refresh-btn" @click="loadData">🔄 새로고침</button>
    <span class="last-updated" v-if="lastUpdated">{{ lastUpdated }} 기준</span>
  </div>

  <!-- 로딩 -->
  <div v-if="loading" style="text-align:center;padding:40px;color:#7070a0;font-size:14px;">
    불러오는 중...
  </div>

  <template v-if="!loading">

    <!-- ═══ 보스 생존 중 ═══ -->
    <template v-if="isAlive">

      <!-- 보스 기본정보 -->
      <div class="card">
        <div class="card-title">⚔ 현재 보스 정보</div>

        <!-- HP 게이지 -->
        <div class="hp-section" v-if="bossInfo.MON_HP">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span style="font-size:15px;font-weight:800;color:#fff;">{{ bossInfo.MON_NAME || '알 수 없는 보스' }}</span>
            <span style="font-size:13px;color:#9090b8;">Lv.{{ bossInfo.MON_LV }}</span>
          </div>
          <div class="hp-bar-wrap">
            <div class="hp-bar" :style="{ width: hpPercent + '%' }"></div>
            <span class="hp-bar-text">{{ formatNum(remainHp) }} / {{ formatNum(bossInfo.MON_HP) }} HP ({{ hpPercent }}%)</span>
          </div>
        </div>

        <div class="boss-stat-grid">
          <div class="boss-stat-item">
            <div class="boss-stat-label">공격력</div>
            <div class="boss-stat-value danger">{{ formatNum(bossInfo.MON_ATK) }}</div>
          </div>
          <div class="boss-stat-item">
            <div class="boss-stat-label">최대 HP</div>
            <div class="boss-stat-value">{{ formatNum(bossInfo.MON_HP) }}</div>
          </div>
          <div class="boss-stat-item">
            <div class="boss-stat-label">잔여 HP</div>
            <div class="boss-stat-value good">{{ formatNum(remainHp) }}</div>
          </div>
          <div class="boss-stat-item">
            <div class="boss-stat-label">총 데미지</div>
            <div class="boss-stat-value">{{ formatNum(bossState.TOTAL_DEALT) }}</div>
          </div>
          <div class="boss-stat-item">
            <div class="boss-stat-label">공격 참여</div>
            <div class="boss-stat-value">{{ bossState.ATTACKER_CNT }}명</div>
          </div>
          <div class="boss-stat-item" v-if="bossInfo.MON_NOTE">
            <div class="boss-stat-label">특수기</div>
            <div class="boss-stat-value" style="font-size:13px;">{{ bossInfo.MON_NOTE }}</div>
          </div>
        </div>
      </div>

      <!-- 최근 배틀로그 -->
      <div class="card">
        <div class="card-title">📜 최근 공격 기록 <span style="font-size:12px;font-weight:400;color:#6060a0;">(최근 20건)</span></div>
        <div v-if="recentLog.length === 0" style="text-align:center;color:#5050a0;padding:20px;">
          공격 기록이 없습니다.
        </div>
        <div class="log-table-wrap" v-else>
          <table class="log-table">
            <thead>
              <tr>
                <th>#</th>
                <th>유저</th>
                <th>직업</th>
                <th>데미지</th>
                <th>패턴</th>
                <th>시각</th>
                <th>처치</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in recentLog" :key="idx">
                <td style="color:#5050a0;font-size:12px;">{{ idx + 1 }}</td>
                <td style="font-weight:600;">{{ row.USER_NAME }}</td>
                <td><span class="job-badge">{{ row.JOB || '-' }}</span></td>
                <td><span class="dmg-value" :class="{ 'crit-dmg': isCrit(row) }">{{ formatNum(row.ATK_DMG) }}</span></td>
                <td style="font-size:12px;color:#7070a0;">{{ pattenText(row.MON_PATTEN) }}</td>
                <td class="time-text">{{ fmtDate(row.INSERT_DATE) }}</td>
                <td>
                  <span v-if="row.KILL_YN == 1" class="kill-badge">💀 처치</span>
                  <span v-else style="color:#4040a0;font-size:12px;">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </template>

    <!-- ═══ 보스 사망 중 ═══ -->
    <div v-else class="boss-dead-panel">
      <div class="boss-dead-icon">💀</div>
      <div class="boss-dead-title">보스가 없습니다</div>
      <div class="boss-dead-desc">현재 진행 중인 보스 전투가 없습니다.</div>
      <template v-if="lastKill.KILL_TIME">
        <div class="boss-next-label">마지막 보스 처치</div>
        <div class="boss-next-time" style="margin-bottom:16px;">{{ fmtDate(lastKill.KILL_TIME) }}</div>
        <div class="boss-next-label">다음 보스 등장까지</div>
        <div class="countdown">{{ countdownText }}</div>
      </template>
    </div>

  </template>
</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2.7.16/dist/vue.min.js"></script>
<script>
new Vue({
  el: '#app',
  data: {
    loading: true,
    isAlive: false,
    bossInfo:  {},
    bossState: {},
    lastKill:  {},
    recentLog: [],
    lastUpdated: '',
    countdownText: '',
    _cdTimer: null
  },
  computed: {
    remainHp: function () {
      var maxHp    = (this.bossInfo.MON_HP    || 0) * 1;
      var dealt    = (this.bossState.TOTAL_DEALT || 0) * 1;
      return Math.max(0, maxHp - dealt);
    },
    hpPercent: function () {
      var maxHp = (this.bossInfo.MON_HP || 0) * 1;
      if (maxHp <= 0) return 0;
      return Math.max(0, Math.min(100, Math.round(this.remainHp / maxHp * 100)));
    }
  },
  mounted: function () {
    this.loadData();
    var self = this;
    setInterval(function () { self.loadData(); }, 30000);
  },
  beforeDestroy: function () {
    if (this._cdTimer) clearInterval(this._cdTimer);
  },
  methods: {
    loadData: function () {
      var self = this;
      self.loading = true;
      var base = '<%=request.getContextPath()%>';
      fetch(base + '/loa/api/boss-status')
        .then(function (r) { return r.json(); })
        .then(function (d) {
          self.isAlive   = d.isAlive;
          self.bossInfo  = d.bossInfo  || {};
          self.bossState = d.bossState || {};
          self.lastKill  = d.lastKill  || {};
          self.recentLog = d.recentLog || [];

          var now = new Date();
          self.lastUpdated = now.getHours().toString().padStart(2,'0') + ':' +
                             now.getMinutes().toString().padStart(2,'0') + ':' +
                             now.getSeconds().toString().padStart(2,'0');

          // 카운트다운 시작 (보스 사망 시)
          if (!self.isAlive && self.lastKill.KILL_TIME) {
            self.startCountdown(self.lastKill.KILL_TIME);
          }
          self.loading = false;
        })
        .catch(function () { self.loading = false; });
    },

    startCountdown: function (killTimeRaw) {
      if (this._cdTimer) clearInterval(this._cdTimer);
      var self = this;
      // 보스 재등장 주기: 60분으로 가정 (서버 설정에 따라 변경)
      var RESPAWN_MS = 60 * 60 * 1000;

      function tick() {
        var killTime = new Date(killTimeRaw);
        var nextSpawn = new Date(killTime.getTime() + RESPAWN_MS);
        var diff = nextSpawn - new Date();
        if (diff <= 0) {
          self.countdownText = '곧 등장 예정';
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

    formatNum: function (v) {
      if (v == null) return '0';
      return Number(v).toLocaleString();
    },

    fmtDate: function (v) {
      if (!v) return '-';
      // Oracle Timestamp: "2024-05-06T12:34:56.000+0900" 또는 숫자
      var d = new Date(v);
      if (isNaN(d)) return String(v);
      var mo = String(d.getMonth() + 1).padStart(2, '0');
      var dd = String(d.getDate()).padStart(2, '0');
      var hh = String(d.getHours()).padStart(2, '0');
      var mm = String(d.getMinutes()).padStart(2, '0');
      var ss = String(d.getSeconds()).padStart(2, '0');
      return mo + '/' + dd + ' ' + hh + ':' + mm + ':' + ss;
    },

    isCrit: function (row) {
      // MON_PATTEN=2 이면 크리티컬 (프로젝트 규칙에 따라 조정)
      return (row.MON_PATTEN * 1) === 2;
    },

    pattenText: function (p) {
      var t = p * 1;
      if (t === 1) return '일반';
      if (t === 2) return '크리';
      if (t === 3) return '스킬';
      if (t === 4) return '연속';
      return '-';
    }
  }
});
</script>
</body>
</html>
