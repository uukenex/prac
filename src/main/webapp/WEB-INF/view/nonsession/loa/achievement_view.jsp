<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 업적</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f5f3ee; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; margin-left: 120px; }

    .wrap { max-width: 900px; margin: 0 auto; padding: 28px 16px 60px; }

    .page-header { margin-bottom: 22px; }
    .page-title  { font-size: 22px; font-weight: 800; color: #3d2b1f; display: flex; align-items: center; gap: 8px; }

    .top-controls { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
    .user-wrap { display: flex; gap: 8px; align-items: center; }
    .user-wrap input { padding: 10px 14px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff;
                       font-size: 14px; color: #333; outline: none; width: 160px; }
    .user-wrap input:focus { border-color: #c9a96e; }
    .user-wrap input::placeholder { color: #bbb; }
    .btn-query { background: #c9a96e; color: #fff; border: none; padding: 10px 20px; border-radius: 24px;
                 font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-query:hover { background: #b8935a; }

    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #ccc; }

    .achv-total { font-size: 42px; font-weight: 900; color: #c9a96e; text-align: center;
                  padding: 18px; background: #fff; border-radius: 14px;
                  box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid #e8ddd0;
                  margin-bottom: 12px; }
    .achv-total span { font-size: 14px; color: #aaa; font-weight: 400; display: block; margin-top: 4px; }

    .card { background: #fff; border-radius: 14px; padding: 16px; margin-bottom: 12px;
            box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid #e8ddd0; }
    .card-title { font-size: 13px; font-weight: 700; color: #a07858; margin-bottom: 12px;
                  display: flex; align-items: center; gap: 6px; }
    .card-title .achv-cnt { font-size: 11px; font-weight: 600; background: #f4ebe0; color: #a07858;
                             border-radius: 10px; padding: 1px 8px; }

    .achv-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
    .achv-item { background: #f8f3ec; border: 1.5px solid #e8ddd0; border-radius: 10px;
                 padding: 10px 12px; display: flex; align-items: center; gap: 8px; }
    .achv-item.done { background: #fffbe8; border-color: #e8c870; }
    .achv-icon { font-size: 22px; flex-shrink: 0; }
    .achv-info { min-width: 0; }
    .achv-name { font-size: 12px; color: #555; }
    .achv-val  { font-size: 15px; font-weight: 700; color: #3d2b1f; }
    .achv-sub  { font-size: 11px; color: #a07858; }

    .season-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; }
    .season-item { background: #fffbe8; border: 1.5px solid #e8c870; border-radius: 10px;
                   padding: 10px 12px; display: flex; align-items: flex-start; gap: 8px; }
    .season-icon { font-size: 18px; flex-shrink: 0; margin-top: 1px; }
    .season-info .s-name { font-size: 11px; color: #888; }
    .season-info .s-val  { font-size: 13px; font-weight: 700; color: #3d2b1f; }
    .season-info .s-sub  { font-size: 10px; color: #b89a60; margin-top: 2px; }

    /* 학살자 인라인 몬스터 목록 */
    .s-mons { margin-top: 5px; display: flex; flex-direction: column; gap: 1px; }
    .s-mon-item { font-size: 10px; color: #666; padding: 1px 0; border-top: 1px solid #f0e8d0; }

    /* 몬스터별 킬 상세 칩 */
    .s-kill-chips { margin-top: 4px; display: flex; flex-wrap: wrap; gap: 3px; }
    .kc { font-size: 10px; padding: 1px 5px; border-radius: 6px; font-weight: 600; }
    .kc-n  { background: #f0f4f8; color: #4a5568; }
    .kc-l  { background: #fffde7; color: #b7791f; }
    .kc-d  { background: #f9f0ff; color: #6b46c1; }
    .kc-yy { background: #e8f8f0; color: #276749; }
    .kc-nm { background: #f5eeff; color: #7d3c98; }
    .kc-h  { background: #fff0f0; color: #a93226; }

    .raw-toggle { background: none; border: 1.5px solid #e8ddd0; border-radius: 8px; padding: 6px 14px;
                  font-size: 12px; color: #a07858; cursor: pointer; margin-bottom: 8px; }
    .raw-toggle:hover { background: #f8f3ec; }
    .raw-list { background: #f8f3ec; border-radius: 10px; padding: 12px; font-size: 11px; font-family: monospace;
                color: #555; max-height: 320px; overflow-y: auto; line-height: 1.8; }
    .raw-list .rl-label { color: #3d2b1f; font-weight: 600; }
    .raw-list .rl-cmd   { color: #aaa; font-size: 10px; margin-left: 4px; }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">🏆 람쥐봇 업적</div>
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

    <!-- 총 업적 수 -->
    <div class="achv-total">
      {{ data.totalAchv }}<span>총 달성 업적 수</span>
    </div>

    <!-- ① 전투 통산 업적 -->
    <div class="card">
      <div class="card-title">
        ⚔ 전투 통산 업적
        <span class="achv-cnt">업적 {{ data.grouped.attacks + data.grouped.totalKills + data.grouped.nmKills + data.grouped.hellKills + data.grouped.deaths + data.grouped.hellAtk + data.grouped.hellClear }}개</span>
      </div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.stats.realAttacks > 0 ? 'done' : ''">
          <div class="achv-icon">🗡</div>
          <div class="achv-info">
            <div class="achv-name">통산 공격</div>
            <div class="achv-val">{{ fmt(data.stats.realAttacks) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.attacks }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.stats.realKills > 0 ? 'done' : ''">
          <div class="achv-icon">💀</div>
          <div class="achv-info">
            <div class="achv-name">통산 킬</div>
            <div class="achv-val">{{ fmt(data.stats.realKills) }}마리</div>
            <div class="achv-sub">업적 {{ data.grouped.totalKills }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.stats.realNmKills > 0 ? 'done' : ''">
          <div class="achv-icon">🌙</div>
          <div class="achv-info">
            <div class="achv-name">나이트메어 킬</div>
            <div class="achv-val">{{ fmt(data.stats.realNmKills) }}킬</div>
            <div class="achv-sub">업적 {{ data.grouped.nmKills }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.stats.realHellKills > 0 ? 'done' : ''">
          <div class="achv-icon">🔥</div>
          <div class="achv-info">
            <div class="achv-name">헬 몬스터 킬</div>
            <div class="achv-val">{{ fmt(data.stats.realHellKills) }}킬</div>
            <div class="achv-sub">업적 {{ data.grouped.hellKills }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.stats.realDeaths > 0 ? 'done' : ''">
          <div class="achv-icon">💔</div>
          <div class="achv-info">
            <div class="achv-name">죽음 극복</div>
            <div class="achv-val">{{ fmt(data.stats.realDeaths) }}회</div>
            <div class="achv-sub">업적 {{ data.grouped.deaths }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.hellBoss.atkCount > 0 ? 'done' : ''">
          <div class="achv-icon">👹</div>
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

    <!-- ② 몬스터별 킬 -->
    <div class="card" v-if="data.monKillList && data.monKillList.length > 0">
      <div class="card-title">
        👾 몬스터별 킬
        <span class="achv-cnt">업적 {{ data.grouped.monKills }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="m in data.monKillList" :key="m.monNo">
          <div class="season-icon">⚔</div>
          <div class="season-info">
            <div class="s-name">#{{ m.monNo }} {{ m.monName }}</div>
            <div class="s-val">{{ fmt(m.killTotal) }}킬</div>
            <div class="s-kill-chips">
              <span class="kc kc-n" v-if="m.nm0Normal > 0">{{ fmt(m.nm0Normal) }}</span>
              <span class="kc kc-l" v-if="m.nm0Light  > 0">빛 {{ fmt(m.nm0Light) }}</span>
              <span class="kc kc-d" v-if="m.nm0Dark   > 0">어둠 {{ fmt(m.nm0Dark) }}</span>
              <span class="kc kc-yy" v-if="m.nm0Yinyang > 0">음양 {{ fmt(m.nm0Yinyang) }}</span>
              <span class="kc kc-nm" v-if="m.nm1Total  > 0">나메 {{ fmt(m.nm1Total) }}</span>
              <span class="kc kc-h"  v-if="m.nm2Total  > 0">헬 {{ fmt(m.nm2Total) }}</span>
            </div>
            <div class="s-sub" v-if="m.maxKill > 0">업적 {{ fmt(m.maxKill) }}킬 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ③ 최초 토벌 달성 -->
    <div class="card" v-if="data.firstClearList && data.firstClearList.length > 0">
      <div class="card-title">
        🗺 최초 토벌 달성 기록
        <span class="achv-cnt">업적 {{ data.grouped.firstClear }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="item in data.firstClearList" :key="item.monNo">
          <div class="season-icon">🏅</div>
          <div class="season-info">
            <div class="s-name">#{{ item.monNo }}</div>
            <div class="s-val">{{ item.monName }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ④ 공개 처치 방송 -->
    <div class="card" v-if="data.broadcastList && data.broadcastList.length > 0">
      <div class="card-title">
        📢 공개 처치 방송
        <span class="achv-cnt">업적 {{ data.grouped.broadcast }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="item in data.broadcastList" :key="item.monNo">
          <div class="season-icon">📡</div>
          <div class="season-info">
            <div class="s-name">#{{ item.monNo }}</div>
            <div class="s-val">{{ item.monName }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑤ 아이템 획득 업적 -->
    <div class="card">
      <div class="card-title">
        💎 아이템 획득 업적
        <span class="achv-cnt">업적 {{ data.grouped.lightItems + data.grouped.darkItems + data.grouped.grayItems + data.grouped.bags }}개</span>
      </div>
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
            <div class="achv-val">{{ fmt(data.stats.bagTotal) }}개</div>
            <div class="achv-sub">업적 {{ data.grouped.bags }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑥ 활동 업적 -->
    <div class="card">
      <div class="card-title">
        🛍 활동 업적
        <span class="achv-cnt">업적 {{ data.grouped.sells + data.grouped.potions + data.grouped.skills }}개</span>
      </div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.grouped.sells > 0 ? 'done' : ''">
          <div class="achv-icon">🏪</div>
          <div class="achv-info">
            <div class="achv-name">상점 판매</div>
            <div class="achv-sub">업적 {{ data.grouped.sells }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.potions > 0 ? 'done' : ''">
          <div class="achv-icon">🧪</div>
          <div class="achv-info">
            <div class="achv-name">물약 사용</div>
            <div class="achv-sub">업적 {{ data.grouped.potions }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.grouped.skills > 0 ? 'done' : ''">
          <div class="achv-icon">⚡</div>
          <div class="achv-info">
            <div class="achv-name">직업 스킬</div>
            <div class="achv-sub">업적 {{ data.grouped.skills }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑦ 룰렛 업적 -->
    <div class="card">
      <div class="card-title">
        🎲 룰렛 업적
        <span class="achv-cnt">업적 {{ data.grouped.rouletteAtk + data.grouped.rouletteCri }}개</span>
      </div>
      <div class="achv-grid">
        <div class="achv-item" :class="data.roulette.atkSuccessCnt > 0 ? 'done' : ''">
          <div class="achv-icon">🎯</div>
          <div class="achv-info">
            <div class="achv-name">공격력 100% 달성</div>
            <div class="achv-val">{{ data.roulette.atkSuccessCnt }}회 성공</div>
            <div class="achv-sub">업적 {{ data.grouped.rouletteAtk }}개 달성</div>
          </div>
        </div>
        <div class="achv-item" :class="data.roulette.criSuccessCnt > 0 ? 'done' : ''">
          <div class="achv-icon">💥</div>
          <div class="achv-info">
            <div class="achv-name">치명타 300% 달성</div>
            <div class="achv-val">{{ data.roulette.criSuccessCnt }}회 성공</div>
            <div class="achv-sub">업적 {{ data.grouped.rouletteCri }}개 달성</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑨ 특수 업적 -->
    <div class="card" v-if="data.specialList && data.specialList.length > 0">
      <div class="card-title">
        ⭐ 특수 업적
        <span class="achv-cnt">업적 {{ data.grouped.special }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="code in data.specialList" :key="code">
          <div class="season-icon">🌟</div>
          <div class="season-info">
            <div class="s-val">ACHV-{{ code }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑩ 직업 마스터 달성 기록 -->
    <div class="card" v-if="data.masterSeasons && data.masterSeasons.length > 0">
      <div class="card-title">
        📚 직업 마스터 달성 기록
        <span class="achv-cnt">업적 {{ data.masterSeasons.length }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="s in data.masterSeasons" :key="s.season">
          <div class="season-icon">🎓</div>
          <div class="season-info">
            <div class="s-name">{{ formatSeason(s.season) }} 시즌</div>
            <div class="s-val">마스터 {{ s.count }}회</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑪ 몬스터 학살자 달성 기록 -->
    <div class="card" v-if="data.slayerSeasons && data.slayerSeasons.length > 0">
      <div class="card-title">
        🔪 몬스터 학살자 달성 기록
        <span class="achv-cnt">업적 {{ data.grouped.slayer }}개</span>
      </div>
      <div class="season-grid">
        <div class="season-item" v-for="s in data.slayerSeasons" :key="s.season">
          <div class="season-icon">🗡</div>
          <div class="season-info">
            <div class="s-name">{{ formatSeason(s.season) }} 시즌</div>
            <div class="s-val">{{ s.monCount }}종 달성</div>
            <div class="s-mons" v-if="s.mons && s.mons.length">
              <div class="s-mon-item" v-for="m in s.mons" :key="m.monNo">
                #{{ m.monNo }} {{ m.monName }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑫ 모든 업적 텍스트 보기 -->
    <div class="card">
      <div class="card-title">
        📋 모든 업적 텍스트
        <span class="achv-cnt">총 {{ data.allCmds.length }}개</span>
      </div>
      <button class="raw-toggle" @click="showRaw = !showRaw">
        {{ showRaw ? '▲ 접기' : '▼ 펼치기' }}
      </button>
      <div class="raw-list" v-if="showRaw">
        <div v-for="entry in data.allCmds" :key="entry.cmd">
          <span class="rl-label">{{ entry.label }}</span>
          <span class="rl-cmd">({{ entry.cmd }})</span>
        </div>
      </div>
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
    showRaw: false
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
      self.showRaw = false;
      fetch('<%=request.getContextPath()%>/loa/api/achievements?userName=' + encodeURIComponent(u))
        .then(function(r) { return r.json(); })
        .then(function(d) { self.data = d; self.loading = false; })
        .catch(function() { self.loading = false; });
    },
    fmt: function(n) { return (n || 0).toLocaleString('ko-KR'); },
    formatSeason: function(season) {
      if (!season) return '';
      return season.substring(0,4) + '-' + season.substring(4,6) + '-' + season.substring(6,8);
    }
  }
});
</script>
</body>
</html>
