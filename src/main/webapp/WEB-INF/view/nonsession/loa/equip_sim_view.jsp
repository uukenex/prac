<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 장비 시뮬레이터</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: linear-gradient(135deg, #f0edff 0%, #fdf6ff 50%, #fff0f5 100%);
      color: #2d2446; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; min-height: 100vh;
    }

    .wrap { max-width: 1100px; margin: 0 auto; padding: 20px 14px 80px; }

    /* ── 헤더 ── */
    .page-header { margin-bottom: 20px; }
    .page-title {
      font-size: 22px; font-weight: 900;
      background: linear-gradient(90deg, #7c3aed, #db2777);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent;
      background-clip: text; margin-bottom: 14px;
    }
    .search-row { display: flex; gap: 8px; flex-wrap: wrap; }
    .search-row input {
      padding: 9px 18px; border: 2px solid #d8b4fe; border-radius: 24px;
      background: #fff; color: #2d2446; font-size: 14px; width: 190px;
      outline: none; transition: border-color .2s, box-shadow .2s;
    }
    .search-row input:focus { border-color: #7c3aed; box-shadow: 0 0 0 3px #ede9fe; }
    .btn {
      padding: 9px 20px; border-radius: 24px; font-size: 13px;
      font-weight: 700; cursor: pointer; border: none;
      transition: transform .1s, box-shadow .15s;
    }
    .btn:active { transform: scale(.97); }
    .btn-purple { background: linear-gradient(135deg,#7c3aed,#9333ea); color:#fff; box-shadow:0 2px 8px #c4b5fd; }
    .btn-purple:hover { box-shadow: 0 4px 14px #c4b5fd; }
    .btn-outline { background: #fff; color: #6d28d9; border: 2px solid #c4b5fd; }
    .btn-outline:hover { background: #f5f3ff; }
    .btn-pink { background: linear-gradient(135deg,#ec4899,#db2777); color:#fff; box-shadow:0 2px 8px #fbcfe8; }
    .btn-pink:hover { box-shadow: 0 4px 14px #fbcfe8; }
    .btn-sm { padding: 6px 14px; font-size: 12px; border-radius: 16px; }

    /* ── 스탯 패널 ── */
    .stat-panel {
      background: #fff;
      border-radius: 20px;
      padding: 20px 22px;
      margin-bottom: 16px;
      box-shadow: 0 4px 20px rgba(124,58,237,.10), 0 1px 4px rgba(0,0,0,.06);
      border: 1px solid #ede9fe;
    }
    .stat-panel-header {
      display: flex; align-items: center; gap: 10px;
      margin-bottom: 16px; flex-wrap: wrap;
    }
    .stat-panel-title { font-size: 16px; font-weight: 800; color: #5b21b6; }
    .badge {
      padding: 3px 12px; border-radius: 12px; font-size: 11px; font-weight: 700;
    }
    .badge-hunter { background: #ede9fe; color: #6d28d9; }
    .badge-job    { background: #fce7f3; color: #9d174d; }
    .badge-hell   { background: #fef2f2; color: #dc2626; }

    .hell-check {
      margin-left: auto; display: flex; align-items: center; gap: 6px;
      font-size: 13px; font-weight: 700; color: #dc2626; cursor: pointer;
      padding: 5px 12px; border-radius: 14px;
      background: #fff5f5; border: 1.5px solid #fecaca;
    }
    .hell-check input { width: 15px; height: 15px; cursor: pointer; accent-color: #dc2626; }
    .hell-check.active { background: #fef2f2; border-color: #f87171; }

    .stat-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(155px, 1fr));
      gap: 10px;
    }
    .stat-box {
      border-radius: 14px; padding: 12px 14px; text-align: center;
      border: 1.5px solid transparent; transition: transform .15s;
    }
    .stat-box:hover { transform: translateY(-2px); }
    .stat-box.atk  { background: #fff7ed; border-color: #fed7aa; }
    .stat-box.hp   { background: #f0fdf4; border-color: #bbf7d0; }
    .stat-box.crit { background: #eff6ff; border-color: #bfdbfe; }
    .stat-box.cdmg { background: #fdf4ff; border-color: #e9d5ff; }
    .stat-box.regen{ background: #f0fdfa; border-color: #99f6e4; }
    .stat-box.conv { background: #fefce8; border-color: #fde68a; }

    .stat-label { font-size: 11px; font-weight: 600; color: #6b7280; margin-bottom: 4px; }
    .stat-value { font-size: 20px; font-weight: 900; }
    .stat-value.atk  { color: #ea580c; }
    .stat-value.hp   { color: #16a34a; }
    .stat-value.crit { color: #2563eb; }
    .stat-value.cdmg { color: #9333ea; }
    .stat-value.regen{ color: #0d9488; }
    .stat-value.conv { color: #ca8a04; }

    /* ── 컨트롤 바 ── */
    .control-bar {
      background: #fff; border-radius: 16px;
      padding: 12px 18px; margin-bottom: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,.06);
      display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
      border: 1px solid #e9d5ff;
    }
    .control-label { font-size: 12px; font-weight: 600; color: #7c3aed; }
    .job-select {
      background: #faf5ff; color: #5b21b6;
      border: 1.5px solid #c4b5fd; border-radius: 12px;
      padding: 7px 12px; font-size: 13px; font-weight: 600;
      outline: none; cursor: pointer;
    }
    .job-select:focus { border-color: #7c3aed; }

    .equip-count {
      margin-left: auto; font-size: 12px; font-weight: 700;
      color: #7c3aed; background: #f5f3ff;
      padding: 4px 12px; border-radius: 10px;
    }

    /* ── 카테고리 탭 ── */
    .cat-tabs {
      display: flex; gap: 6px; flex-wrap: wrap;
      margin-bottom: 14px;
    }
    .cat-tab {
      padding: 6px 16px; border-radius: 20px; font-size: 12px; font-weight: 700;
      cursor: pointer; border: 1.5px solid #e5e7eb;
      background: #fff; color: #6b7280;
      transition: background .15s, color .15s, border-color .15s;
      user-select: none;
    }
    .cat-tab:hover { border-color: #a78bfa; color: #6d28d9; }
    .cat-tab.active {
      background: linear-gradient(135deg, #7c3aed, #9333ea);
      color: #fff; border-color: transparent;
      box-shadow: 0 2px 8px #c4b5fd;
    }
    .cat-tab .cnt {
      display: inline-block; background: rgba(255,255,255,.25);
      border-radius: 8px; padding: 0 6px; margin-left: 4px; font-size: 11px;
    }
    .cat-tab:not(.active) .cnt { background: #f3f4f6; color: #9ca3af; }

    /* ── 직업 메모 ── */
    .job-note {
      background: #fef3c7; border: 1.5px solid #fde68a; border-radius: 12px;
      padding: 8px 14px; font-size: 12px; font-weight: 600; color: #92400e;
      margin-bottom: 12px;
    }

    /* ── 아이템 그리드 ── */
    .item-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(175px, 1fr));
      gap: 10px;
    }

    .item-card {
      background: #fff; border: 2px solid #e5e7eb; border-radius: 14px;
      padding: 12px; cursor: pointer;
      transition: border-color .15s, box-shadow .15s, transform .1s, opacity .15s;
      user-select: none; position: relative;
    }
    .item-card:hover { box-shadow: 0 4px 16px rgba(124,58,237,.15); transform: translateY(-2px); }

    /* 보유 + 장착 */
    .item-card.equipped {
      border-color: #7c3aed;
      background: linear-gradient(135deg, #faf5ff, #f5f3ff);
      box-shadow: 0 2px 10px rgba(124,58,237,.20);
    }
    /* 보유 + 미장착 */
    .item-card.unequipped { opacity: .55; }
    .item-card.unequipped:hover { opacity: .85; }

    /* 미보유 + 발견됨 + 장착(시뮬) */
    .item-card.sim-equipped {
      border-color: #f59e0b;
      background: linear-gradient(135deg, #fffbeb, #fef3c7);
      box-shadow: 0 2px 10px rgba(245,158,11,.20);
    }
    /* 미보유 + 발견됨 + 미장착 */
    .item-card.not-owned { opacity: .50; }
    .item-card.not-owned:hover { opacity: .80; }

    /* 미발견 */
    .item-card.undiscovered { opacity: .35; cursor: default; }
    .item-card.undiscovered:hover { transform: none; box-shadow: none; }

    .toggle-icon {
      position: absolute; top: 9px; right: 10px;
      font-size: 14px; line-height: 1;
    }
    .equipped    .toggle-icon { color: #7c3aed; }
    .sim-equipped .toggle-icon { color: #f59e0b; }
    .not-owned   .toggle-icon { color: #d1d5db; }
    .unequipped  .toggle-icon { color: #d1d5db; }
    .undiscovered .toggle-icon { color: #e5e7eb; }

    .item-card-top {
      display: flex; align-items: flex-start; gap: 6px;
      margin-bottom: 5px; padding-right: 20px;
    }
    .item-name { font-size: 12px; font-weight: 700; color: #1e1b4b; line-height: 1.3; flex: 1; }
    .item-qty  { font-size: 11px; font-weight: 700; color: #7c3aed; white-space: nowrap; }
    .item-qty.sim { color: #f59e0b; }

    .item-type { display: inline-block; font-size: 10px; padding: 2px 8px; border-radius: 8px; font-weight: 700; }
    .type-market  { background: #dbeafe; color: #1d4ed8; }
    .type-boss    { background: #fee2e2; color: #b91c1c; }
    .type-drop    { background: #dcfce7; color: #15803d; }
    .type-gacha   { background: #fce7f3; color: #be185d; }
    .type-other   { background: #f3f4f6; color: #6b7280; }

    .item-stats { display: flex; flex-wrap: wrap; gap: 3px; margin-top: 5px; }
    .stat-chip  { font-size: 10px; padding: 2px 7px; border-radius: 7px; font-weight: 700; }
    .chip-atk   { background: #fff7ed; color: #c2410c; border: 1px solid #fed7aa; }
    .chip-hp    { background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; }
    .chip-crit  { background: #eff6ff; color: #1e40af; border: 1px solid #bfdbfe; }
    .chip-cdmg  { background: #fdf4ff; color: #7e22ce; border: 1px solid #e9d5ff; }
    .chip-regen { background: #f0fdfa; color: #134e4a; border: 1px solid #99f6e4; }
    .chip-rate  { background: #fefce8; color: #854d0e; border: 1px solid #fde68a; }

    .undiscovered-label {
      font-size: 11px; color: #9ca3af; margin-top: 6px; font-style: italic;
    }
    .not-owned-label {
      font-size: 10px; color: #f59e0b; font-weight: 700;
      margin-top: 3px; display: inline-block;
    }

    /* 빈 상태 / 로딩 */
    .empty   { text-align: center; padding: 60px; color: #9ca3af; font-size: 15px; }
    .loading { text-align: center; padding: 60px; color: #7c3aed; font-size: 14px; font-weight: 600; }
    .loading::after { content: '⠙'; animation: spin 1s linear infinite; margin-left: 6px; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .error-msg {
      color: #dc2626; text-align: center; padding: 20px; font-size: 14px;
      background: #fff5f5; border-radius: 12px; border: 1px solid #fecaca;
    }

    @media (max-width: 600px) {
      .stat-grid { grid-template-columns: repeat(2, 1fr); }
      .item-grid { grid-template-columns: repeat(2, 1fr); }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>

<div class="wrap" id="app">

  <!-- 헤더 -->
  <div class="page-header">
    <div class="page-title">🔬 장비 시뮬레이터</div>
    <div class="search-row">
      <input v-model="inputName" placeholder="유저명 입력" @keyup.enter="load" />
      <button class="btn btn-purple" @click="load">조회</button>
    </div>
  </div>

  <div v-if="loading" class="loading">불러오는 중</div>
  <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

  <template v-if="user">

    <!-- ── 스탯 패널 ── -->
    <div class="stat-panel">
      <div class="stat-panel-header">
        <span class="stat-panel-title">{{ user.userName }} · Lv.{{ user.lv }}</span>
        <span class="badge badge-hunter">헌터 {{ hunterGrade }}</span>
        <span class="badge badge-job">{{ simJob || user.job || '직업없음' }}</span>
        <label class="hell-check" :class="{ active: hellMode }">
          <input type="checkbox" v-model="hellMode" @change="recalc" />
          🔥 헬모드
        </label>
      </div>
      <div class="stat-grid">
        <div class="stat-box atk">
          <div class="stat-label">⚔ 공격력 MIN</div>
          <div class="stat-value atk">{{ fmt(stats.atkMin) }}</div>
        </div>
        <div class="stat-box atk">
          <div class="stat-label">⚔ 공격력 MAX</div>
          <div class="stat-value atk">{{ fmt(stats.atkMax) }}</div>
        </div>
        <div class="stat-box hp">
          <div class="stat-label">💚 최대 체력</div>
          <div class="stat-value hp">{{ fmt(stats.hpMax) }}</div>
        </div>
        <div class="stat-box regen">
          <div class="stat-label">💧 리젠</div>
          <div class="stat-value regen">{{ fmt(stats.regen) }}</div>
        </div>
        <div class="stat-box crit">
          <div class="stat-label">✨ 치명타율</div>
          <div class="stat-value crit">{{ stats.crit }}%</div>
        </div>
        <div class="stat-box cdmg">
          <div class="stat-label">💥 치명타 피해</div>
          <div class="stat-value cdmg">{{ stats.critDmg }}%</div>
        </div>
        <div class="stat-box conv" v-if="isHunter">
          <div class="stat-label">🔄 치피전환 (헌터)</div>
          <div class="stat-value conv">{{ stats.criConvert > 0 ? '+' + stats.criConvert + '%' : '-' }}</div>
        </div>
      </div>
    </div>

    <!-- ── 컨트롤 바 ── -->
    <div class="control-bar">
      <span class="control-label">직업 시뮬:</span>
      <select class="job-select" v-model="simJob" @change="recalc">
        <option value="">{{ user.job || '현재 직업' }}</option>
        <option v-for="j in jobList" :key="j" :value="j">{{ j }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="equipAll">전체 장착</button>
      <button class="btn btn-pink btn-sm" @click="unequipAll">전체 해제</button>
      <span class="equip-count">{{ equippedCount }}개 장착 / 전체 {{ allItems.length }}개</span>
    </div>

    <!-- 직업 메모 -->
    <div v-if="jobNote" class="job-note">ℹ️ {{ jobNote }}</div>

    <!-- ── 카테고리 탭 ── -->
    <div class="cat-tabs">
      <div v-for="cat in categories" :key="cat.key"
           class="cat-tab" :class="{ active: activeCat === cat.key }"
           @click="activeCat = cat.key">
        {{ cat.label }}<span class="cnt">{{ cat.count }}</span>
      </div>
    </div>

    <!-- ── 아이템 그리드 ── -->
    <div v-if="filteredItems.length === 0" class="empty">해당 카테고리에 아이템이 없습니다.</div>
    <div class="item-grid" v-else>
      <div v-for="item in filteredItems" :key="item.ITEM_ID"
           class="item-card" :class="cardClass(item)"
           @click="toggleItem(item)">

        <span class="toggle-icon">{{ toggleIcon(item) }}</span>

        <div class="item-card-top">
          <div class="item-name">{{ item.ITEM_NAME }}</div>
          <div v-if="isOwned(item)" class="item-qty">×{{ item.OWN_QTY }}</div>
          <div v-else-if="!isUndiscovered(item)" class="item-qty sim">시뮬</div>
        </div>

        <span class="item-type" :class="typeClass(item.ITEM_TYPE)">{{ typeLabel(item.ITEM_TYPE) }}</span>

        <!-- 미발견 -->
        <div v-if="isUndiscovered(item)" class="undiscovered-label">🔒 미발견 아이템</div>

        <!-- 스탯 (미발견 제외) -->
        <template v-else>
          <div class="item-stats">
            <span v-if="+item.ATK_MIN || +item.ATK_MAX" class="stat-chip chip-atk">
              ATK {{ +item.ATK_MIN }}~{{ +item.ATK_MAX }}
            </span>
            <span v-if="+item.HP_MAX"      class="stat-chip chip-hp">HP +{{ fmt(+item.HP_MAX) }}</span>
            <span v-if="+item.ATK_CRI"     class="stat-chip chip-crit">CRI +{{ item.ATK_CRI }}%</span>
            <span v-if="+item.CRI_DMG"     class="stat-chip chip-cdmg">CDMG +{{ item.CRI_DMG }}%</span>
            <span v-if="+item.HP_REGEN"    class="stat-chip chip-regen">리젠 +{{ fmt(+item.HP_REGEN) }}</span>
            <span v-if="+item.HP_MAX_RATE" class="stat-chip chip-rate">HP×{{ item.HP_MAX_RATE }}%</span>
            <span v-if="+item.ATK_MAX_RATE"class="stat-chip chip-rate">ATK×{{ item.ATK_MAX_RATE }}%</span>
          </div>
          <div v-if="!isOwned(item)" class="not-owned-label">📦 미보유 (시뮬용)</div>
        </template>
      </div>
    </div>

  </template>
</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
(function () {
  var ctx    = '<%=request.getContextPath()%>';
  var params = new URLSearchParams(window.location.search);

  // ── 카테고리 정의 ──────────────────────────────────────────────
  var CAT_MAP = {
    'MARKET':       '상점',
    'MARKET2':      '상점',
    'POTION':       '상점',
    'BOSS_HELL':    '유물',
    'BOSS_GACHA':   '가챠',
    'BAG_OPEN':     '드랍',
    'BAG_OPEN_NM':  '드랍',
    'DROP':         '드랍',
    'ACHV':         '드랍',
  };
  function getCatKey(type) {
    if (!type) return '기타';
    return CAT_MAP[type] || '기타';
  }

  new Vue({
    el: '#app',
    data: {
      inputName:  params.get('userName') || '',
      loading:    false,
      errorMsg:   '',
      user:       null,
      hunterGrade:'F',
      jobList:    [],
      allItems:   [],
      hellMode:   false,
      simJob:     '',
      activeCat:  '전체',
      stats: { atkMin:0, atkMax:0, hpMax:0, regen:0, crit:0, critDmg:0, criConvert:0 },
      calcTimer:  null,
    },

    computed: {
      isHunter: function () {
        var j = this.simJob || (this.user && this.user.job) || '';
        return j === '헌터';
      },

      equippedCount: function () {
        return this.allItems.filter(function (i) { return i._equipped; }).length;
      },

      categories: function () {
        var vm = this, counts = {};
        vm.allItems.forEach(function (item) {
          var k = getCatKey(item.ITEM_TYPE);
          counts[k] = (counts[k] || 0) + 1;
        });
        var list = [{ key:'전체', label:'전체', count: vm.allItems.length }];
        ['상점','유물','가챠','드랍','기타'].forEach(function (k) {
          if (counts[k]) list.push({ key: k, label: k, count: counts[k] });
        });
        return list;
      },

      filteredItems: function () {
        var vm = this;
        if (vm.activeCat === '전체') return vm.allItems;
        return vm.allItems.filter(function (item) {
          return getCatKey(item.ITEM_TYPE) === vm.activeCat;
        });
      },

      jobNote: function () {
        var j = this.simJob || (this.user && this.user.job) || '';
        var notes = {
          '곰':       '공격력 = 최대체력으로 전환 (치명타 미사용)',
          '헌터':     '헌터 등급 보너스 적용 / 치명타 100% 초과분은 치피전환',
          '어둠사냥꾼':'아이템 HP/리젠 ×1.25',
          '검성':     '기본 최대체력 ×2 추가',
          '용사':     '기본 최대체력 ×2 추가',
          '흡혈귀':   '리젠 = 0',
          '궁수':     '쿨타임 2분→10분 / 7% 확률 7배 데미지',
          '사냥꾼':   '쿨타임 2분→10분 / 동물·인간형 추가피해',
          '처단자':   '방어 무시 150% 추가데미지',
          '도적':     '50% 확률 조각 추가 드랍',
        };
        return notes[j] || '';
      },
    },

    watch: {
      inputName: function (val) {
        var url = new URL(window.location.href);
        url.searchParams.set('userName', val);
        window.history.replaceState({}, '', url.toString());
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
        vm.loading = true; vm.errorMsg = ''; vm.user = null; vm.allItems = [];

        fetch(ctx + '/loa/api/equip-sim-init?userName=' + encodeURIComponent(name))
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (data.error) { vm.errorMsg = data.error; return; }

            vm.user        = data.user;
            vm.hunterGrade = data.hunterGrade;
            vm.jobList     = data.jobList || [];
            vm.simJob      = '';
            vm.hellMode    = data.user.nightmareYn === 2;
            vm.activeCat   = '전체';

            // _equipped 상태 초기화
            // 보유 아이템은 기본 장착, 미보유/미발견은 미장착
            vm.allItems = (data.allItems || []).map(function (item) {
              var owned = +item.OWN_QTY > 0;
              return Object.assign({}, item, { _equipped: owned });
            });

            vm.recalc();
          })
          .catch(function () { vm.errorMsg = '서버 오류가 발생했습니다.'; })
          .finally(function () { vm.loading = false; });
      },

      isOwned: function (item) {
        return +item.OWN_QTY > 0;
      },

      isUndiscovered: function (item) {
        // ITEM_ID >= 7000 (보스/가챠) 이면서 아무도 획득한 적 없으면 미발견
        return +item.ITEM_ID >= 7000 && +item.EVER_OBTAINED_CNT === 0;
      },

      cardClass: function (item) {
        if (this.isUndiscovered(item)) return 'undiscovered';
        if (this.isOwned(item)) {
          return item._equipped ? 'equipped' : 'unequipped';
        }
        // 미보유
        return item._equipped ? 'sim-equipped' : 'not-owned';
      },

      toggleIcon: function (item) {
        if (this.isUndiscovered(item)) return '🔒';
        if (item._equipped) return this.isOwned(item) ? '✔' : '⚡';
        return '○';
      },

      toggleItem: function (item) {
        if (this.isUndiscovered(item)) return;  // 미발견은 클릭 불가
        item._equipped = !item._equipped;
        this.scheduleRecalc();
      },

      equipAll: function () {
        this.allItems.forEach(function (i) {
          if (+i.OWN_QTY > 0) i._equipped = true;  // 보유 아이템만 장착
        });
        this.recalc();
      },

      unequipAll: function () {
        this.allItems.forEach(function (i) { i._equipped = false; });
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
        vm.allItems.forEach(function (item) {
          if (item._equipped) {
            var qty = +item.OWN_QTY > 0 ? +item.OWN_QTY : 1;  // 미보유는 qty=1로 시뮬
            equipped.push({ itemId: +item.ITEM_ID, qty: qty });
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
                atkMin:     data.atkMin     || 0,
                atkMax:     data.atkMax     || 0,
                hpMax:      data.hpMax      || 0,
                regen:      data.regen      || 0,
                crit:       data.crit       || 0,
                critDmg:    data.critDmg    || 0,
                criConvert: data.criConvert || 0,
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
        if (t === 'BOSS_HELL')  return 'type-boss';
        if (t === 'BOSS_GACHA') return 'type-gacha';
        if (t.indexOf('MARKET') !== -1 || t === 'POTION') return 'type-market';
        if (t.indexOf('BAG') !== -1 || t.indexOf('DROP') !== -1 || t === 'ACHV') return 'type-drop';
        return 'type-other';
      },

      typeLabel: function (t) {
        var MAP = {
          'MARKET':      '상점', 'MARKET2':     '상점', 'POTION':       '포션',
          'BOSS_HELL':   '유물', 'BOSS_GACHA':  '가챠',
          'BAG_OPEN':    '드랍', 'BAG_OPEN_NM': '드랍(NM)',
          'ACHV':        '업적', 'DROP':        '드랍',
        };
        return MAP[t] || (t || '기타');
      },
    },
  });
})();
</script>
</body>
</html>
