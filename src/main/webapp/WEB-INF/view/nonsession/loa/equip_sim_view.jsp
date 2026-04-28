<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 장비 시뮬레이터</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #0d0b18; color: #d0cce8; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; min-height: 100vh; }

    .wrap { max-width: 1000px; margin: 0 auto; padding: 20px 12px 60px; }

    /* ── 헤더 ── */
    .page-header { margin-bottom: 18px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #c9a96e; margin-bottom: 12px; }
    .search-row  { display: flex; gap: 8px; flex-wrap: wrap; }
    .search-row input {
      padding: 9px 16px; border: 1.5px solid #2e2a50; border-radius: 20px;
      background: #16132a; color: #d0cce8; font-size: 14px; width: 180px;
      outline: none; transition: border-color .2s;
    }
    .search-row input:focus { border-color: #c9a96e; }
    .btn { padding: 9px 20px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; border: none; transition: background .2s; }
    .btn-gold  { background: #c9a96e; color: #1a0800; }
    .btn-gold:hover { background: #b8935a; }
    .btn-dark  { background: #1e1840; color: #a090cc; border: 1px solid #2e2a50; }
    .btn-dark:hover { background: #2a2460; }
    .btn-red   { background: #3a1020; color: #e08888; border: 1px solid #5a2030; }
    .btn-red:hover { background: #4a1428; }
    .btn-sm { padding: 5px 12px; font-size: 12px; border-radius: 14px; }

    /* ── 스탯 패널 ── */
    .stat-panel {
      background: linear-gradient(135deg, #16132a 0%, #1a1535 100%);
      border: 1px solid #2e2a50; border-radius: 16px;
      padding: 18px 20px; margin-bottom: 18px;
    }
    .stat-panel-header { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
    .stat-panel-title  { font-size: 15px; font-weight: 800; color: #c9a96e; }
    .hunter-badge {
      padding: 3px 10px; border-radius: 10px; font-size: 11px; font-weight: 700;
      background: #2a2060; color: #a090e0; border: 1px solid #3a3080;
    }
    .hell-check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #e08888; cursor: pointer; margin-left: auto; }
    .hell-check input { width: 16px; height: 16px; cursor: pointer; accent-color: #e05050; }

    .stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
    .stat-box {
      background: #0e0c1a; border: 1px solid #2a2040; border-radius: 10px;
      padding: 10px 14px; text-align: center;
    }
    .stat-label { font-size: 11px; color: #7070a0; margin-bottom: 4px; }
    .stat-value { font-size: 18px; font-weight: 900; color: #e8d8a8; }
    .stat-value.atk  { color: #f0a050; }
    .stat-value.hp   { color: #60d890; }
    .stat-value.crit { color: #80b8f0; }
    .stat-value.cdmg { color: #e080e0; }
    .stat-value.regen{ color: #60d8b0; }

    /* ── 컨트롤 바 ── */
    .control-bar {
      display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
      margin-bottom: 14px; background: #13102260; padding: 12px 16px;
      border-radius: 12px; border: 1px solid #2a2040;
    }
    .control-label { font-size: 12px; color: #8080a8; }
    .job-select {
      background: #1e1840; color: #c0b0e8; border: 1px solid #3a3070;
      border-radius: 10px; padding: 7px 12px; font-size: 13px; outline: none; cursor: pointer;
    }
    .job-select:focus { border-color: #c9a96e; }

    /* ── 아이템 그리드 ── */
    .section-title { font-size: 14px; font-weight: 700; color: #9090c0; margin-bottom: 10px; }
    .item-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; }

    .item-card {
      background: #16132a; border: 1.5px solid #2a2040; border-radius: 12px;
      padding: 12px; cursor: pointer; transition: border-color .15s, background .15s, opacity .15s;
      user-select: none; position: relative;
    }
    .item-card:hover { background: #1e1840; border-color: #4040a0; }
    .item-card.equipped { border-color: #c9a96e; background: #1e1a2e; }
    .item-card.equipped:hover { border-color: #e0c080; }
    .item-card.unequipped { opacity: .45; }

    .item-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 6px; margin-bottom: 6px; }
    .item-name  { font-size: 12px; font-weight: 700; color: #c8bce8; line-height: 1.3; flex: 1; }
    .item-qty   { font-size: 10px; color: #7070a0; white-space: nowrap; }
    .item-type  { font-size: 10px; padding: 2px 7px; border-radius: 8px; font-weight: 600; white-space: nowrap; }
    .type-market  { background: #1a2040; color: #7090e0; }
    .type-boss    { background: #2a1020; color: #e06060; }
    .type-drop    { background: #102020; color: #60c0a0; }
    .type-other   { background: #201a10; color: #c0a060; }

    .item-stats { display: flex; flex-wrap: wrap; gap: 3px; margin-top: 4px; }
    .stat-chip  { font-size: 10px; padding: 2px 6px; border-radius: 6px; font-weight: 600; }
    .chip-atk   { background: #2a1a00; color: #e0a040; }
    .chip-hp    { background: #001a10; color: #40c880; }
    .chip-crit  { background: #001830; color: #60a8e0; }
    .chip-cdmg  { background: #1a0030; color: #c060e0; }
    .chip-regen { background: #001a14; color: #40c8a0; }
    .chip-rate  { background: #1a1000; color: #e0c040; }

    .equip-toggle {
      position: absolute; top: 8px; right: 8px;
      font-size: 14px; opacity: .7;
    }
    .equipped .equip-toggle { color: #c9a96e; opacity: 1; }
    .unequipped .equip-toggle { color: #5050a0; }

    /* 비어있을 때 */
    .empty { text-align: center; padding: 60px; color: #5050a0; font-size: 15px; }
    .loading { text-align: center; padding: 60px; color: #7070b0; font-size: 14px; }
    .error-msg { color: #e06060; text-align: center; padding: 20px; font-size: 14px; }

    /* 직업 특이사항 메모 */
    .job-note { font-size: 12px; color: #8888b0; background: #12102060; border: 1px solid #2020408 0;
                border-radius: 8px; padding: 6px 12px; margin-top: 8px; }

    @media (max-width: 600px) {
      .stat-grid { grid-template-columns: repeat(2, 1fr); }
      .item-grid  { grid-template-columns: repeat(2, 1fr); }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>

<div class="wrap" id="app">

  <!-- 헤더 / 검색 -->
  <div class="page-header">
    <div class="page-title">🔬 장비 시뮬레이터</div>
    <div class="search-row">
      <input v-model="inputName" placeholder="유저명 입력" @keyup.enter="load" />
      <button class="btn btn-gold" @click="load">조회</button>
    </div>
  </div>

  <!-- 로딩/에러 -->
  <div v-if="loading" class="loading">불러오는 중…</div>
  <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

  <!-- 메인 컨텐츠 -->
  <template v-if="user">

    <!-- 스탯 패널 -->
    <div class="stat-panel">
      <div class="stat-panel-header">
        <span class="stat-panel-title">
          {{ user.userName }} (Lv.{{ user.lv }})
        </span>
        <span class="hunter-badge">헌터 {{ hunterGrade }}</span>
        <span class="hunter-badge" style="background:#1a2a10;color:#80d860;border-color:#3a5020;">
          {{ simJob || user.job || '직업없음' }}
        </span>
        <label class="hell-check">
          <input type="checkbox" v-model="hellMode" @change="recalc" />
          🔥 헬모드
        </label>
      </div>
      <div class="stat-grid">
        <div class="stat-box">
          <div class="stat-label">공격력 (MIN)</div>
          <div class="stat-value atk">{{ fmt(stats.atkMin) }}</div>
        </div>
        <div class="stat-box">
          <div class="stat-label">공격력 (MAX)</div>
          <div class="stat-value atk">{{ fmt(stats.atkMax) }}</div>
        </div>
        <div class="stat-box">
          <div class="stat-label">최대 체력</div>
          <div class="stat-value hp">{{ fmt(stats.hpMax) }}</div>
        </div>
        <div class="stat-box">
          <div class="stat-label">리젠</div>
          <div class="stat-value regen">{{ fmt(stats.regen) }}</div>
        </div>
        <div class="stat-box">
          <div class="stat-label">치명타율</div>
          <div class="stat-value crit">{{ stats.crit }}%</div>
        </div>
        <div class="stat-box">
          <div class="stat-label">치명타 피해</div>
          <div class="stat-value cdmg">{{ stats.critDmg }}%</div>
        </div>
      </div>
    </div>

    <!-- 컨트롤 바 -->
    <div class="control-bar">
      <span class="control-label">직업 시뮬:</span>
      <select class="job-select" v-model="simJob" @change="recalc">
        <option value="">{{ user.job || '현재 직업' }}</option>
        <option v-for="j in jobList" :key="j" :value="j">{{ j }}</option>
      </select>
      <button class="btn btn-dark btn-sm" @click="equipAll">전체 장착</button>
      <button class="btn btn-red btn-sm" @click="unequipAll">전체 해제</button>
      <span class="control-label" style="margin-left:auto;">
        장착 {{ equippedCount }}개 / 전체 {{ ownedItems.length }}개
      </span>
    </div>

    <!-- 직업 메모 -->
    <div v-if="jobNote" class="job-note">ℹ️ {{ jobNote }}</div>

    <!-- 아이템 그리드 -->
    <div class="section-title" style="margin-top:14px;">보유 아이템 (클릭하여 장착/해제)</div>

    <div v-if="ownedItems.length === 0" class="empty">보유 아이템이 없습니다.</div>
    <div class="item-grid" v-else>
      <div v-for="item in ownedItems" :key="item.ITEM_ID"
           class="item-card"
           :class="item.equipped ? 'equipped' : 'unequipped'"
           @click="toggleItem(item)">

        <span class="equip-toggle">{{ item.equipped ? '✔' : '○' }}</span>

        <div class="item-card-top">
          <div class="item-name">{{ item.ITEM_NAME }}</div>
          <div class="item-qty">×{{ item.OWN_QTY }}</div>
        </div>

        <span class="item-type" :class="typeClass(item.ITEM_TYPE)">
          {{ typeLabel(item.ITEM_TYPE) }}
        </span>

        <div class="item-stats">
          <span v-if="+item.ATK_MIN || +item.ATK_MAX" class="stat-chip chip-atk">
            ATK {{ +item.ATK_MIN }}~{{ +item.ATK_MAX }}
          </span>
          <span v-if="+item.HP_MAX" class="stat-chip chip-hp">HP +{{ fmt(+item.HP_MAX) }}</span>
          <span v-if="+item.ATK_CRI" class="stat-chip chip-crit">CRI +{{ item.ATK_CRI }}%</span>
          <span v-if="+item.CRI_DMG" class="stat-chip chip-cdmg">CDMG +{{ item.CRI_DMG }}%</span>
          <span v-if="+item.HP_REGEN" class="stat-chip chip-regen">리젠 +{{ fmt(+item.HP_REGEN) }}</span>
          <span v-if="+item.HP_MAX_RATE" class="stat-chip chip-rate">HP×{{ item.HP_MAX_RATE }}%</span>
          <span v-if="+item.ATK_MAX_RATE" class="stat-chip chip-rate">ATK×{{ item.ATK_MAX_RATE }}%</span>
        </div>
      </div>
    </div>

  </template>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
(function () {
  var ctx = '<%=request.getContextPath()%>';
  var params = new URLSearchParams(window.location.search);

  new Vue({
    el: '#app',
    data: {
      inputName:   params.get('userName') || '',
      loading:     false,
      errorMsg:    '',

      user:        null,
      hunterGrade: 'F',
      hellNerfMult: 0.04,
      jobList:     [],
      ownedItems:  [],

      hellMode: false,
      simJob:   '',

      stats: { atkMin: 0, atkMax: 0, hpMax: 0, regen: 0, crit: 0, critDmg: 0 },

      calcTimer: null,
    },

    computed: {
      equippedCount: function () {
        return this.ownedItems.filter(function (i) { return i.equipped; }).length;
      },
      jobNote: function () {
        var j = this.simJob || (this.user && this.user.job) || '';
        var notes = {
          '곰':      '공격력 = 최대체력 (치명타 미사용)',
          '헌터':    '헌터 등급에 따라 공격력/HP 보너스 적용',
          '어둠사냥꾼': '아이템 HP/리젠 ×1.25 적용',
          '검성':    '기본 최대체력 ×2 추가',
          '용사':    '기본 최대체력 ×2 추가',
          '흡혈귀':  '리젠 = 0',
        };
        return notes[j] || '';
      },
    },

    watch: {
      inputName: function (val) {
        if (params.get('userName') !== val) {
          var url = new URL(window.location.href);
          url.searchParams.set('userName', val);
          window.history.replaceState({}, '', url.toString());
        }
      },
    },

    mounted: function () {
      if (this.inputName) this.load();
    },

    methods: {
      load: function () {
        var vm = this;
        var name = vm.inputName.trim();
        if (!name) return;
        vm.loading = true; vm.errorMsg = ''; vm.user = null;

        fetch(ctx + '/loa/api/equip-sim-init?userName=' + encodeURIComponent(name))
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (data.error) { vm.errorMsg = data.error; return; }

            vm.user        = data.user;
            vm.hunterGrade = data.hunterGrade;
            vm.hellNerfMult= data.hellNerfMult;
            vm.jobList     = data.jobList || [];
            vm.simJob      = '';
            vm.hellMode    = data.user.nightmareYn === 2;

            // 아이템에 equipped 상태 추가 (기본 전체 장착)
            vm.ownedItems = (data.ownedItems || []).map(function (item) {
              return Object.assign({}, item, { equipped: true });
            });

            vm.recalc();
          })
          .catch(function () { vm.errorMsg = '서버 오류가 발생했습니다.'; })
          .finally(function () { vm.loading = false; });
      },

      toggleItem: function (item) {
        item.equipped = !item.equipped;
        this.scheduleRecalc();
      },

      equipAll: function () {
        this.ownedItems.forEach(function (i) { i.equipped = true; });
        this.recalc();
      },

      unequipAll: function () {
        this.ownedItems.forEach(function (i) { i.equipped = false; });
        this.recalc();
      },

      scheduleRecalc: function () {
        var vm = this;
        clearTimeout(vm.calcTimer);
        vm.calcTimer = setTimeout(function () { vm.recalc(); }, 120);
      },

      recalc: function () {
        var vm = this;
        if (!vm.user) return;

        var equipped = [];
        vm.ownedItems.forEach(function (item) {
          if (item.equipped) {
            equipped.push({ itemId: +item.ITEM_ID, qty: +item.OWN_QTY });
          }
        });

        fetch(ctx + '/loa/api/equip-sim-calc', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userName:      vm.user.userName,
            simJob:        vm.simJob,
            hellMode:      vm.hellMode,
            equippedItems: equipped
          })
        })
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (!data.error) {
              vm.stats = {
                atkMin:  data.atkMin  || 0,
                atkMax:  data.atkMax  || 0,
                hpMax:   data.hpMax   || 0,
                regen:   data.regen   || 0,
                crit:    data.crit    || 0,
                critDmg: data.critDmg || 0,
              };
            }
          })
          .catch(function () {});
      },

      fmt: function (n) {
        return Number(n).toLocaleString();
      },

      typeClass: function (t) {
        if (!t) return 'type-other';
        if (t.indexOf('BOSS') !== -1) return 'type-boss';
        if (t.indexOf('MARKET') !== -1) return 'type-market';
        if (t.indexOf('DROP') !== -1) return 'type-drop';
        return 'type-other';
      },

      typeLabel: function (t) {
        if (!t) return '기타';
        if (t === 'BOSS_HELL')  return '유물';
        if (t === 'BOSS_GACHA') return '가챠';
        if (t.indexOf('MARKET') !== -1) return '상점';
        if (t.indexOf('DROP') !== -1)   return '드랍';
        return t;
      },
    },
  });
})();
</script>
</body>
</html>
