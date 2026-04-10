<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 업적</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <script src="<%=request.getContextPath()%>/assets/js/vue.min.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f8f5f0; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; }
    .wrap { max-width: 900px; margin: 0 auto; padding: 20px 12px 60px 140px; }

    .page-header { margin-bottom: 18px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #3d2b1f; }
    .search-row  { display: flex; gap: 8px; }
    .search-row input { padding: 8px 14px; border: 1.5px solid #e0d9ce; border-radius: 20px; background: #fff; font-size: 13px; width: 160px; outline: none; }
    .search-row input:focus { border-color: #c9a96e; }
    .btn-search { background: #c9a96e; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #b8935a; }
    .loading, .empty { text-align: center; padding: 60px; color: #bbb; }

    /* 총 업적 배지 */
    .achv-total { font-size: 42px; font-weight: 900; color: #c9a96e; text-align: center;
                  padding: 18px; background: #fff8ee; border: 2px solid #e8c870;
                  border-radius: 14px; margin-bottom: 14px; }
    .achv-total span { font-size: 14px; color: #aaa; font-weight: 400; display: block; margin-top: 4px; }

    /* 카드 */
    .card { background: #fff; border: 1.5px solid #e8ddd0; border-radius: 14px; padding: 16px; margin-bottom: 12px; }
    .card-title { font-size: 13px; font-weight: 700; color: #a07858; margin-bottom: 12px; }

    /* 업적 그리드 */
    .achv-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
    .achv-item {
      background: #f8f3ec; border: 1.5px solid #e8ddd0; border-radius: 10px;
      padding: 10px 12px; display: flex; align-items: center; gap: 8px;
    }
    .achv-item.done { background: #fffbe8; border-color: #e8c870; }
    .achv-icon { font-size: 22px; flex-shrink: 0; }
    .achv-info { min-width: 0; }
    .achv-name { font-size: 12px; color: #555; }
    .achv-val  { font-size: 15px; font-weight: 700; color: #3d2b1f; }
    .achv-sub  { font-size: 11px; color: #a07858; }

    /* 섹션 나눔 */
    .section-divider { font-size: 11px; color: #bbb; margin: 6px 0 10px; border-top: 1px solid #f0ebe4; padding-top: 8px; }

    /* 학살자/직업마스터 테이블 */
    .season-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .season-table th { background: #f8f3ec; padding: 5px 8px; color: #a07858; font-weight: 700; text-align: left; border-bottom: 1.5px solid #e8ddd0; }
    .season-table td { padding: 6px 8px; border-bottom: 1px solid #f4efe8; }
    .season-table tr.achieved { background: #fffbe8; font-weight: 700; }
    .badge-ok  { background: #c9a96e; color: #fff; border-radius: 8px; padding: 1px 7px; font-size: 11px; }
    .badge-no  { background: #eee; color: #aaa; border-radius: 8px; padding: 1px 7px; font-size: 11px; }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">🏆 람쥐봇 업적</div>
    <div class="search-row">
      <input v-model="inputUser" placeholder="유저명 입력" @keyup.enter="loadData">
      <button class="btn-search" @click="loadData">조회</button>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty"   v-else-if="!userName">유저명을 입력해 조회하세요.</div>

  <template v-else>

    <!-- 총 업적 수 -->
    <div class="achv-total">
      {{ data.totalAchv }}<span>총 달성 업적 수</span>
    </div>

    <!-- 전투 통산 -->
    <div class="card">
      <div class="card-title">⚔ 전투 통산 업적</div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.grouped.maxAttack > 0 ? 'done' : ''">
          <div class="achv-icon">🗡</div>
          <div class="achv-info">
            <div class="achv-name">통산 공격</div>
            <div class="achv-val">{{ fmt(data.stats.kills) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.attacks }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxTotalKill > 0 ? 'done' : ''">
          <div class="achv-icon">💀</div>
          <div class="achv-info">
            <div class="achv-name">통산 킬</div>
            <div class="achv-val">{{ fmt(data.stats.kills) }}마리</div>
            <div class="achv-sub">업적 {{ data.grouped.totalKills }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxNmKill > 0 ? 'done' : ''">
          <div class="achv-icon">🌙</div>
          <div class="achv-info">
            <div class="achv-name">나이트메어 킬</div>
            <div class="achv-val">업적 {{ data.grouped.nmKills }}개</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxHellKill > 0 ? 'done' : ''">
          <div class="achv-icon">🔥</div>
          <div class="achv-info">
            <div class="achv-name">헬 킬</div>
            <div class="achv-val">업적 {{ data.grouped.hellKills }}개</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxDeath > 0 ? 'done' : ''">
          <div class="achv-icon">💔</div>
          <div class="achv-info">
            <div class="achv-name">죽음 극복</div>
            <div class="achv-val">{{ fmt(data.stats.deaths) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.deaths }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.monKills > 0 ? 'done' : ''">
          <div class="achv-icon">👾</div>
          <div class="achv-info">
            <div class="achv-name">몬스터별 킬</div>
            <div class="achv-val">업적 {{ data.grouped.monKills }}개</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 아이템 -->
    <div class="card">
      <div class="card-title">💎 아이템 획득 업적</div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.grouped.maxLightItem > 0 ? 'done' : ''">
          <div class="achv-icon">✨</div>
          <div class="achv-info">
            <div class="achv-name">빛 아이템</div>
            <div class="achv-val">{{ fmt(data.stats.lightQty) }}개</div>
            <div class="achv-sub">업적 {{ data.grouped.lightItems }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxDarkItem > 0 ? 'done' : ''">
          <div class="achv-icon">🌑</div>
          <div class="achv-info">
            <div class="achv-name">어둠 아이템</div>
            <div class="achv-val">{{ fmt(data.stats.darkQty) }}개</div>
            <div class="achv-sub">업적 {{ data.grouped.darkItems }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxGrayItem > 0 ? 'done' : ''">
          <div class="achv-icon">☯</div>
          <div class="achv-info">
            <div class="achv-name">음양 아이템</div>
            <div class="achv-val">{{ fmt(data.stats.grayQty) }}개</div>
            <div class="achv-sub">업적 {{ data.grouped.grayItems }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.maxBag > 0 ? 'done' : ''">
          <div class="achv-icon">🎒</div>
          <div class="achv-info">
            <div class="achv-name">가방 획득</div>
            <div class="achv-sub">업적 {{ data.grouped.bags }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 활동 -->
    <div class="card">
      <div class="card-title">🛍 활동 업적</div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.grouped.sells > 0 ? 'done' : ''">
          <div class="achv-icon">🏪</div>
          <div class="achv-info"><div class="achv-name">상점 판매</div><div class="achv-sub">업적 {{ data.grouped.sells }}개 달성</div></div>
        </div>
        <div class="achv-item" :class="data.grouped.potions > 0 ? 'done' : ''">
          <div class="achv-icon">🧪</div>
          <div class="achv-info"><div class="achv-name">물약 사용</div><div class="achv-sub">업적 {{ data.grouped.potions }}개 달성</div></div>
        </div>
        <div class="achv-item" :class="data.grouped.skills > 0 ? 'done' : ''">
          <div class="achv-icon">⚡</div>
          <div class="achv-info"><div class="achv-name">직업 스킬</div><div class="achv-sub">업적 {{ data.grouped.skills }}개 달성</div></div>
        </div>
      </div>
    </div>

    <!-- 헬보스 업적 (6-1) -->
    <div class="card">
      <div class="card-title">👹 헬보스 업적</div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.hellBoss.atkCount > 0 ? 'done' : ''">
          <div class="achv-icon">⚔</div>
          <div class="achv-info">
            <div class="achv-name">헬보스 공격</div>
            <div class="achv-val">{{ fmt(data.hellBoss.atkCount) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.hellAtk }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.hellBoss.clearCount > 0 ? 'done' : ''">
          <div class="achv-icon">🏆</div>
          <div class="achv-info">
            <div class="achv-name">헬보스 처치 참여</div>
            <div class="achv-val">{{ fmt(data.hellBoss.clearCount) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.hellClear }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 룰렛 업적 (6-1) -->
    <div class="card">
      <div class="card-title">🎲 룰렛 업적</div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.roulette.maxAtkBonus >= 100 ? 'done' : ''">
          <div class="achv-icon">🎯</div>
          <div class="achv-info">
            <div class="achv-name">공격력 100% 달성</div>
            <div class="achv-val">최대 {{ data.roulette.maxAtkBonus }}%</div>
            <div class="achv-sub">업적 {{ data.grouped.rouletteAtk }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.roulette.maxCriDmgBonus >= 300 ? 'done' : ''">
          <div class="achv-icon">💥</div>
          <div class="achv-info">
            <div class="achv-name">치명타 300% 달성</div>
            <div class="achv-val">최대 {{ data.roulette.maxCriDmgBonus }}%</div>
            <div class="achv-sub">업적 {{ data.grouped.rouletteCri }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 몬스터 학살자 시즌 (6-1) -->
    <div class="card" v-if="data.slayerSeasons && data.slayerSeasons.length > 0">
      <div class="card-title">🔪 몬스터 학살자 시즌 (1위+30종 달성)</div>
      <table class="season-table">
        <thead>
          <tr><th>시즌</th><th>1위</th><th>킬수</th><th>종류</th><th>달성</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in data.slayerSeasons" :key="s.season" :class="s.achieved ? 'achieved' : ''">
            <td>{{ s.season }}</td>
            <td>{{ s.top }}</td>
            <td>{{ fmt(s.killCnt) }}</td>
            <td>{{ s.monTypes }}</td>
            <td><span :class="s.achieved ? 'badge-ok' : 'badge-no'">{{ s.achieved ? '달성' : '-' }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 직업마스터 시즌 (6-1) -->
    <div class="card" v-if="data.jobMasterSeasons && data.jobMasterSeasons.length > 0">
      <div class="card-title">📚 직업 마스터 달성 기록</div>
      <div class="achv-grid">
        <div class="achv-item done" v-for="s in data.jobMasterSeasons" :key="s">
          <div class="achv-icon">🎓</div>
          <div class="achv-info">
            <div class="achv-name">직업 마스터</div>
            <div class="achv-sub">{{ s.replace('_', ' / ') }}</div>
          </div>
        </div>
      </div>
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
    data: null
  },
  mounted() {
    var p = new URLSearchParams(window.location.search);
    var u = p.get('userName') || p.get('user') || sessionStorage.getItem('loaUserName') || '';
    if (u) { this.inputUser = u; this.loadData(); }
  },
  methods: {
    loadData() {
      var u = this.inputUser.trim();
      if (!u) return;
      this.userName = u;
      sessionStorage.setItem('loaUserName', u);
      this.loading = true;
      var self = this;
      fetch('<%=request.getContextPath()%>/loa/api/achievements?userName=' + encodeURIComponent(u))
        .then(function(r) { return r.json(); })
        .then(function(d) { self.data = d; self.loading = false; })
        .catch(function() { self.loading = false; });
    },
    fmt(n) { return (n || 0).toLocaleString('ko-KR'); }
  }
});
</script>
</body>
</html>
