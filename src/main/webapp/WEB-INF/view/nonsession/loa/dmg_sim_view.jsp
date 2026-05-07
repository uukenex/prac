<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>데미지 시뮬레이터</title>
<script src="https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.min.js"></script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #0f1117; color: #e0e0e0; font-family: 'Segoe UI', sans-serif; min-height: 100vh; }
.container { max-width: 860px; margin: 0 auto; padding: 20px 16px; }
h1 { font-size: 1.4rem; font-weight: 700; color: #e8c97a; margin-bottom: 18px; }

.search-bar { display: flex; gap: 8px; margin-bottom: 24px; }
.search-bar input { flex: 1; padding: 10px 14px; background: #1e2130; border: 1px solid #333; border-radius: 8px; color: #e0e0e0; font-size: 1rem; outline: none; }
.search-bar input:focus { border-color: #e8c97a; }
.search-bar button { padding: 10px 20px; background: #e8c97a; color: #1a1a2e; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; }
.search-bar button:hover { background: #f5d98b; }

.loading { text-align: center; padding: 40px; color: #888; font-size: 1.1rem; }
.error-msg { background: #2c1515; color: #ff6b6b; padding: 14px 18px; border-radius: 8px; margin-bottom: 16px; }

/* 유저 정보 */
.user-card { background: #1a1e2e; border: 1px solid #2a2f45; border-radius: 12px; padding: 16px 20px; margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.user-card .name { font-size: 1.2rem; font-weight: 700; color: #e8c97a; }
.badge { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.8rem; font-weight: 700; }
.badge-job { background: #1d3557; color: #74b9ff; }
.badge-grade { background: #2d1b69; color: #a29bfe; }
.badge-hell { background: #3d1515; color: #ff7675; }
.badge-lv { background: #1a3a1a; color: #55efc4; }
.boss-items { font-size: 0.75rem; color: #888; }

/* 섹션 */
.section { margin-bottom: 24px; }
.section-title { font-size: 0.9rem; font-weight: 700; color: #888; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 1px solid #2a2f45; }

/* 단계 테이블 */
.step-table { width: 100%; border-collapse: collapse; }
.step-table th { background: #1a1e2e; color: #888; font-size: 0.78rem; font-weight: 600; text-align: left; padding: 8px 12px; border-bottom: 1px solid #2a2f45; }
.step-table th.num { text-align: right; }
.step-table td { padding: 9px 12px; font-size: 0.88rem; border-bottom: 1px solid #1a1e2e; vertical-align: middle; }
.step-table tr:last-child td { border-bottom: none; }
.step-table tr:hover td { background: #1a1e2e; }

.step-label { color: #d0d0d0; font-weight: 500; }
.step-note { font-size: 0.75rem; color: #666; margin-top: 2px; }
.bonus-cell { text-align: right; font-family: monospace; }
.bonus-pos { color: #55efc4; }
.bonus-neg { color: #ff7675; }
.bonus-zero { color: #555; }
.total-cell { text-align: right; font-family: monospace; font-weight: 700; }

.row-base td { background: #1c2233; }
.row-final td { background: #1e2a1a; }
.row-job td { background: #23201a; }

/* 결과 카드 */
.result-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.result-card { background: #1a1e2e; border: 1px solid #2a2f45; border-radius: 10px; padding: 16px; }
.result-card .rc-title { font-size: 0.8rem; color: #888; margin-bottom: 10px; font-weight: 600; }
.result-card .rc-row { display: flex; justify-content: space-between; font-size: 0.85rem; padding: 3px 0; }
.result-card .rc-row .lbl { color: #aaa; }
.result-card .rc-row .val { font-weight: 700; font-family: monospace; }
.val-atk { color: #fd9644; }
.val-crit { color: #a29bfe; }
.val-hp { color: #55efc4; }
.val-eff { color: #e8c97a; font-size: 1rem; }

/* 최종 공격범위 하이라이트 */
.final-range { background: #1e2a10; border: 1px solid #3a4a20; border-radius: 10px; padding: 16px 20px; margin-bottom: 20px; }
.final-range .title { font-size: 0.8rem; color: #888; margin-bottom: 8px; }
.final-range .range { font-size: 1.5rem; font-weight: 700; color: #e8c97a; }
.final-range .crit-range { font-size: 1.1rem; color: #a29bfe; margin-top: 4px; }
.final-range .meta { font-size: 0.8rem; color: #666; margin-top: 6px; }
</style>
</head>
<body>
<div id="app" class="container">
  <h1>⚔️ 데미지 시뮬레이터</h1>

  <div class="search-bar">
    <input v-model="inputUser" placeholder="유저명 입력 (예: 일어난다람쥐/카단)" @keyup.enter="load" />
    <button @click="load">조회</button>
  </div>

  <div class="loading" v-if="loading">⏳ 데이터 로딩 중...</div>
  <div class="error-msg" v-if="error">{{ error }}</div>

  <template v-if="data && !loading">

    <!-- 유저 정보 -->
    <div class="user-card">
      <span class="name">{{ data.user.userName }}</span>
      <span class="badge badge-job">{{ data.user.job || '직업없음' }}</span>
      <span class="badge badge-lv">Lv.{{ data.user.lv }}</span>
      <span class="badge badge-grade" v-if="data.user.hunterGrade">{{ data.user.hunterGrade }} 등급</span>
      <span class="badge badge-hell" v-if="data.user.nightmareYn == 2">🔥 S3 (헬)</span>
      <span class="boss-items" v-if="data.user.bossItems && data.user.bossItems.length > 0">
        보스템: {{ data.user.bossItems.slice().sort((a,b)=>a-b).join(', ') }}
      </span>
    </div>

    <!-- 최종 공격범위 요약 -->
    <div class="final-range">
      <div class="title">⚔️ 최종 실전 공격범위</div>
      <div class="range">{{ fmt(data.eff.min) }} ~ {{ fmt(data.eff.max) }}</div>
      <div class="crit-range" v-if="data.crit.rate > 0">
        💥 크리 적용 {{ fmt(data.crit.min) }} ~ {{ fmt(data.crit.max) }}
        <span style="font-size:0.8rem;color:#888;margin-left:8px;">(치명률 {{ data.crit.rate }}%, ×{{ data.crit.mul }})</span>
      </div>
      <div class="meta">
        HP {{ fmt(data.hp.total) }}  /  리젠 {{ fmt(data.hp.regen) }}
      </div>
    </div>

    <!-- 공격력 단계 테이블 -->
    <div class="section">
      <div class="section-title">📊 공격력 계산 단계</div>
      <table class="step-table">
        <thead>
          <tr>
            <th style="width:36px;">#</th>
            <th>항목</th>
            <th class="num" style="width:130px;">증감 (MIN)</th>
            <th class="num" style="width:130px;">증감 (MAX)</th>
            <th class="num" style="width:130px;">누적 MIN</th>
            <th class="num" style="width:130px;">누적 MAX</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(s, i) in data.atkSteps" :key="i"
              :class="{'row-base': i===0, 'row-job': s.label.includes('배율') || s.label.includes('특수'), 'row-final': i===data.atkSteps.length-1}">
            <td style="color:#555;font-size:0.8rem;">{{ i+1 }}</td>
            <td>
              <div class="step-label">{{ s.label }}</div>
              <div class="step-note" v-if="s.note">{{ s.note }}</div>
            </td>
            <td class="bonus-cell">
              <span v-if="i===0" class="bonus-pos">{{ fmt(s.bonusMin) }}</span>
              <span v-else-if="s.bonusMin > 0" class="bonus-pos">+{{ fmt(s.bonusMin) }}</span>
              <span v-else-if="s.bonusMin < 0" class="bonus-neg">{{ fmt(s.bonusMin) }}</span>
              <span v-else class="bonus-zero">—</span>
            </td>
            <td class="bonus-cell">
              <span v-if="i===0" class="bonus-pos">{{ fmt(s.bonusMax) }}</span>
              <span v-else-if="s.bonusMax > 0" class="bonus-pos">+{{ fmt(s.bonusMax) }}</span>
              <span v-else-if="s.bonusMax < 0" class="bonus-neg">{{ fmt(s.bonusMax) }}</span>
              <span v-else class="bonus-zero">—</span>
            </td>
            <td class="total-cell" style="color:#e8c97a;">{{ fmt(s.totalMin) }}</td>
            <td class="total-cell" style="color:#e8c97a;">{{ fmt(s.totalMax) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 스탯 카드 -->
    <div class="result-grid">
      <div class="result-card">
        <div class="rc-title">⚔️ 공격력</div>
        <div class="rc-row"><span class="lbl">기본</span><span class="val val-atk">{{ fmt(data.user.baseAtkMinDisplay || 0) }}</span></div>
        <div class="rc-row" v-if="data.eff"><span class="lbl">실전 MIN</span><span class="val val-eff">{{ fmt(data.eff.min) }}</span></div>
        <div class="rc-row" v-if="data.eff"><span class="lbl">실전 MAX</span><span class="val val-eff">{{ fmt(data.eff.max) }}</span></div>
      </div>
      <div class="result-card">
        <div class="rc-title">💥 크리티컬</div>
        <div class="rc-row"><span class="lbl">치명타 확률</span><span class="val val-crit">{{ data.crit.rate }}%</span></div>
        <div class="rc-row"><span class="lbl">치명타 데미지</span><span class="val val-crit">{{ data.crit.dmg }}%</span></div>
        <div class="rc-row"><span class="lbl">크리 배율</span><span class="val val-crit">×{{ data.crit.mul }}</span></div>
        <div class="rc-row"><span class="lbl">크리 MIN</span><span class="val val-crit">{{ fmt(data.crit.min) }}</span></div>
        <div class="rc-row"><span class="lbl">크리 MAX</span><span class="val val-crit">{{ fmt(data.crit.max) }}</span></div>
      </div>
      <div class="result-card">
        <div class="rc-title">❤️ 체력</div>
        <div class="rc-row"><span class="lbl">기본 HP</span><span class="val val-hp">{{ fmt(data.hp.base) }}</span></div>
        <div class="rc-row" v-if="data.hp.mkt !== 0"><span class="lbl">아이템 HP</span><span class="val val-hp">+{{ fmt(data.hp.mkt) }}</span></div>
        <div class="rc-row" v-if="data.hp.rateBonus > 0"><span class="lbl">HP 비율 보너스</span><span class="val val-hp">+{{ data.hp.rateBonus }}%</span></div>
        <div class="rc-row" v-if="data.hp.jobBonus > 0"><span class="lbl">직업 HP</span><span class="val val-hp">+{{ fmt(data.hp.jobBonus) }}</span></div>
        <div class="rc-row" v-if="data.hp.hellNerf > 0"><span class="lbl">헬 너프</span><span class="val" style="color:#ff7675;">-{{ fmt(data.hp.hellNerf) }}</span></div>
        <div class="rc-row" style="border-top:1px solid #2a2f45;margin-top:6px;padding-top:6px;"><span class="lbl">최종 HP</span><span class="val val-hp" style="font-size:1rem;">{{ fmt(data.hp.total) }}</span></div>
        <div class="rc-row"><span class="lbl">HP 리젠</span><span class="val val-hp">{{ fmt(data.hp.regen) }}/턴</span></div>
      </div>
    </div>

  </template>
</div>

<script>
new Vue({
  el: '#app',
  data: {
    inputUser: new URLSearchParams(location.search).get('userName') || '',
    loading: false,
    error: '',
    data: null,
  },
  mounted() {
    if (this.inputUser) this.load();
  },
  methods: {
    load() {
      const u = this.inputUser.trim();
      if (!u) return;
      this.loading = true; this.error = ''; this.data = null;
      fetch('/loa/api/dmg-sim?userName=' + encodeURIComponent(u))
        .then(r => r.json())
        .then(d => {
          if (d.error) { this.error = d.error; }
          else { this.data = d; }
        })
        .catch(() => { this.error = '서버 오류가 발생했습니다.'; })
        .finally(() => { this.loading = false; });
    },
    fmt(n) {
      if (n == null) return '0';
      return Number(n).toLocaleString('ko-KR');
    }
  }
});
</script>
</body>
</html>
