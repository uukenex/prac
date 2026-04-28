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
      font-size: 22px; font-weight: 900; margin-bottom: 14px;
      background: linear-gradient(90deg,#7c3aed,#db2777);
      -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text;
    }
    .search-row { display:flex; gap:8px; flex-wrap:wrap; }
    .search-row input {
      padding:9px 18px; border:2px solid #d8b4fe; border-radius:24px;
      background:#fff; color:#2d2446; font-size:14px; width:190px;
      outline:none; transition:border-color .2s,box-shadow .2s;
    }
    .search-row input:focus { border-color:#7c3aed; box-shadow:0 0 0 3px #ede9fe; }
    .btn {
      padding:9px 20px; border-radius:24px; font-size:13px; font-weight:700;
      cursor:pointer; border:none; transition:transform .1s,box-shadow .15s;
    }
    .btn:active { transform:scale(.97); }
    .btn-purple { background:linear-gradient(135deg,#7c3aed,#9333ea); color:#fff; box-shadow:0 2px 8px #c4b5fd; }
    .btn-purple:hover { box-shadow:0 4px 14px #c4b5fd; }
    .btn-outline { background:#fff; color:#6d28d9; border:2px solid #c4b5fd; }
    .btn-outline:hover { background:#f5f3ff; }
    .btn-pink { background:linear-gradient(135deg,#ec4899,#db2777); color:#fff; box-shadow:0 2px 8px #fbcfe8; }
    .btn-pink:hover { box-shadow:0 4px 14px #fbcfe8; }
    .btn-sm { padding:6px 14px; font-size:12px; border-radius:16px; }

    /* ── 스탯 패널 ── */
    .stat-panel {
      background:#fff; border-radius:20px; padding:20px 22px; margin-bottom:16px;
      box-shadow:0 4px 20px rgba(124,58,237,.10),0 1px 4px rgba(0,0,0,.06);
      border:1px solid #ede9fe;
    }
    .stat-panel-header { display:flex; align-items:center; gap:10px; margin-bottom:16px; flex-wrap:wrap; }
    .stat-panel-title  { font-size:16px; font-weight:800; color:#5b21b6; }
    .badge { padding:3px 12px; border-radius:12px; font-size:11px; font-weight:700; }
    .badge-hunter { background:#ede9fe; color:#6d28d9; }
    .badge-job    { background:#fce7f3; color:#9d174d; }
    .hell-check {
      margin-left:auto; display:flex; align-items:center; gap:6px;
      font-size:13px; font-weight:700; color:#dc2626; cursor:pointer;
      padding:5px 12px; border-radius:14px; background:#fff5f5; border:1.5px solid #fecaca;
    }
    .hell-check input { width:15px; height:15px; cursor:pointer; accent-color:#dc2626; }
    .hell-check.active { background:#fef2f2; border-color:#f87171; }

    .stat-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(155px,1fr)); gap:10px; }
    .stat-box { border-radius:14px; padding:12px 14px; text-align:center; border:1.5px solid transparent; }
    .stat-box.atk   { background:#fff7ed; border-color:#fed7aa; }
    .stat-box.atk2  { background:#fff3e0; border-color:#ffb74d; }  /* 유효 공격 */
    .stat-box.hp    { background:#f0fdf4; border-color:#bbf7d0; }
    .stat-box.crit  { background:#eff6ff; border-color:#bfdbfe; }
    .stat-box.cdmg  { background:#fdf4ff; border-color:#e9d5ff; }
    .stat-box.regen { background:#f0fdfa; border-color:#99f6e4; }
    .stat-box.conv  { background:#fefce8; border-color:#fde68a; }
    .stat-label { font-size:11px; font-weight:600; color:#6b7280; margin-bottom:4px; }
    .stat-sub   { font-size:10px; color:#9ca3af; margin-top:2px; }
    .stat-value { font-size:20px; font-weight:900; }
    .stat-value.atk  { color:#ea580c; }
    .stat-value.atk2 { color:#d97706; }
    .stat-value.hp   { color:#16a34a; }
    .stat-value.crit { color:#2563eb; }
    .stat-value.cdmg { color:#9333ea; }
    .stat-value.regen{ color:#0d9488; }
    .stat-value.conv { color:#ca8a04; }

    /* ── 컨트롤 바 ── */
    .control-bar {
      background:#fff; border-radius:16px; padding:12px 18px; margin-bottom:12px;
      box-shadow:0 2px 10px rgba(0,0,0,.06); border:1px solid #e9d5ff;
      display:flex; align-items:center; gap:10px; flex-wrap:wrap;
    }
    .control-label { font-size:12px; font-weight:600; color:#7c3aed; }
    .job-select {
      background:#faf5ff; color:#5b21b6; border:1.5px solid #c4b5fd;
      border-radius:12px; padding:7px 12px; font-size:13px; font-weight:600;
      outline:none; cursor:pointer;
    }
    .job-select:focus { border-color:#7c3aed; }
    .equip-count {
      margin-left:auto; font-size:12px; font-weight:700;
      color:#7c3aed; background:#f5f3ff; padding:4px 12px; border-radius:10px;
    }

    /* ── 직업 메모 ── */
    .job-note {
      background:#fef3c7; border:1.5px solid #fde68a; border-radius:12px;
      padding:8px 14px; font-size:12px; font-weight:600; color:#92400e; margin-bottom:12px;
    }

    /* ── 카테고리 탭 ── */
    .cat-group { margin-bottom:10px; }
    .cat-group-label { font-size:11px; font-weight:700; color:#9ca3af; margin-bottom:5px; letter-spacing:.5px; }
    .cat-tabs { display:flex; gap:5px; flex-wrap:wrap; margin-bottom:4px; }
    .cat-tab {
      padding:5px 14px; border-radius:18px; font-size:12px; font-weight:700;
      cursor:pointer; border:1.5px solid #e5e7eb; background:#fff; color:#6b7280;
      transition:background .15s,color .15s,border-color .15s; user-select:none;
    }
    .cat-tab:hover { border-color:#a78bfa; color:#6d28d9; background:#f5f3ff; }
    .cat-tab.active {
      background:linear-gradient(135deg,#7c3aed,#9333ea);
      color:#fff; border-color:transparent; box-shadow:0 2px 8px #c4b5fd;
    }
    .cat-tab .cnt {
      display:inline-block; border-radius:8px; padding:0 5px; margin-left:3px; font-size:10px;
    }
    .cat-tab:not(.active) .cnt { background:#f3f4f6; color:#9ca3af; }
    .cat-tab.active .cnt { background:rgba(255,255,255,.25); }

    /* ── 아이템 그리드 ── */
    .item-grid {
      display:grid; grid-template-columns:repeat(auto-fill,minmax(170px,1fr)); gap:10px;
    }
    .item-card {
      background:#fff; border:2px solid #e5e7eb; border-radius:14px; padding:12px;
      cursor:pointer; transition:border-color .15s,box-shadow .15s,transform .1s,opacity .15s;
      user-select:none; position:relative;
    }
    .item-card:hover { box-shadow:0 4px 16px rgba(124,58,237,.15); transform:translateY(-2px); }
    /* 보유 + 장착 */
    .item-card.equipped {
      border-color:#7c3aed; background:linear-gradient(135deg,#faf5ff,#f5f3ff);
      box-shadow:0 2px 10px rgba(124,58,237,.20);
    }
    /* 보유 + 미장착 */
    .item-card.unequipped { opacity:.5; }
    .item-card.unequipped:hover { opacity:.8; }
    /* 미보유 + 장착(시뮬) */
    .item-card.sim-equipped {
      border-color:#f59e0b; background:linear-gradient(135deg,#fffbeb,#fef3c7);
      box-shadow:0 2px 10px rgba(245,158,11,.20);
    }
    /* 미보유 + 미장착 */
    .item-card.not-owned { opacity:.45; }
    .item-card.not-owned:hover { opacity:.75; }
    /* 미발견 */
    .item-card.undiscovered { opacity:.28; cursor:default; pointer-events:none; }

    .toggle-icon { position:absolute; top:9px; right:10px; font-size:14px; }
    .equipped     .toggle-icon { color:#7c3aed; }
    .sim-equipped .toggle-icon { color:#f59e0b; }
    .not-owned    .toggle-icon, .unequipped .toggle-icon { color:#d1d5db; }
    .undiscovered .toggle-icon { color:#e5e7eb; }

    .item-card-top { display:flex; align-items:flex-start; gap:6px; margin-bottom:5px; padding-right:22px; }
    .item-name { font-size:12px; font-weight:700; color:#1e1b4b; line-height:1.3; flex:1; }
    .item-qty  { font-size:11px; font-weight:700; color:#7c3aed; white-space:nowrap; }
    .item-qty.sim { color:#f59e0b; }

    .item-type { display:inline-block; font-size:10px; padding:2px 8px; border-radius:8px; font-weight:700; margin-bottom:4px; }
    .type-market { background:#dbeafe; color:#1d4ed8; }
    .type-boss   { background:#fee2e2; color:#b91c1c; }
    .type-achv   { background:#d1fae5; color:#065f46; }
    .type-relic  { background:#fde68a; color:#78350f; }
    .type-potion { background:#e0f2fe; color:#0369a1; }
    .type-other  { background:#f3f4f6; color:#6b7280; }

    .item-stats  { display:flex; flex-wrap:wrap; gap:3px; margin-top:4px; }
    .stat-chip   { font-size:10px; padding:2px 7px; border-radius:7px; font-weight:700; }
    .chip-atk    { background:#fff7ed; color:#c2410c; border:1px solid #fed7aa; }
    .chip-hp     { background:#f0fdf4; color:#166534; border:1px solid #bbf7d0; }
    .chip-crit   { background:#eff6ff; color:#1e40af; border:1px solid #bfdbfe; }
    .chip-cdmg   { background:#fdf4ff; color:#7e22ce; border:1px solid #e9d5ff; }
    .chip-regen  { background:#f0fdfa; color:#134e4a; border:1px solid #99f6e4; }
    .chip-rate   { background:#fefce8; color:#854d0e; border:1px solid #fde68a; }

    .not-owned-label { font-size:10px; color:#f59e0b; font-weight:700; margin-top:3px; display:block; }

    .empty   { text-align:center; padding:60px; color:#9ca3af; font-size:15px; }
    .loading { text-align:center; padding:60px; color:#7c3aed; font-size:14px; font-weight:600; }
    .error-msg {
      color:#dc2626; text-align:center; padding:20px; font-size:14px;
      background:#fff5f5; border-radius:12px; border:1px solid #fecaca; margin-bottom:12px;
    }

    @media (max-width:600px) {
      .stat-grid { grid-template-columns:repeat(2,1fr); }
      .item-grid { grid-template-columns:repeat(2,1fr); }
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

  <div v-if="loading" class="loading">불러오는 중…</div>
  <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

  <template v-if="user">

    <!-- ── 스탯 패널 ── -->
    <div class="stat-panel">
      <div class="stat-panel-header">
        <span class="stat-panel-title">{{ user.userName }} · Lv.{{ user.lv }}</span>
        <span class="badge badge-hunter">헌터 {{ hunterGrade }}</span>
        <span class="badge badge-job">{{ currentJob || '직업없음' }}</span>
        <label class="hell-check" :class="{ active: hellMode }">
          <input type="checkbox" v-model="hellMode" @change="recalc" /> 🔥 헬모드
        </label>
      </div>

      <div class="stat-grid">
        <!-- 기본 공격력 -->
        <div class="stat-box atk">
          <div class="stat-label">⚔ 공격력 MIN</div>
          <div class="stat-value atk">{{ fmt(stats.atkMin) }}</div>
        </div>
        <div class="stat-box atk">
          <div class="stat-label">⚔ 공격력 MAX</div>
          <div class="stat-value atk">{{ fmt(stats.atkMax) }}</div>
        </div>

        <!-- 유효 공격력 (직업 배율 적용) — 배율 ≠ 1 일 때만 표시 -->
        <template v-if="stats.jobDmgMul && stats.jobDmgMul !== 1">
          <div class="stat-box atk2">
            <div class="stat-label">💥 유효데미지 MIN <span style="color:#f59e0b">(×{{ stats.jobDmgMul }})</span></div>
            <div class="stat-value atk2">{{ fmt(stats.effAtkMin) }}</div>
          </div>
          <div class="stat-box atk2">
            <div class="stat-label">💥 유효데미지 MAX <span style="color:#f59e0b">(×{{ stats.jobDmgMul }})</span></div>
            <div class="stat-value atk2">{{ fmt(stats.effAtkMax) }}</div>
          </div>
        </template>

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
          <div class="stat-label">🎯 치명타 피해</div>
          <div class="stat-value cdmg">{{ stats.critDmg }}%</div>
        </div>

        <!-- 치피전환 (헌터 전용) -->
        <div class="stat-box conv" v-if="isHunterJob">
          <div class="stat-label">🔄 치피전환 (헌터)</div>
          <div class="stat-value conv">{{ stats.criConvert > 0 ? '+' + stats.criConvert + '%' : '-' }}</div>
          <div class="stat-sub">{{ hunterGrade }}등급 전생상한 {{ stats.criCap }}%</div>
        </div>
      </div>
    </div>

    <!-- ── 컨트롤 바 ── -->
    <div class="control-bar">
      <span class="control-label">직업 시뮬:</span>
      <select class="job-select" v-model="simJob" @change="recalc">
        <option value="">{{ user.job || '직업없음' }} (현재)</option>
        <option v-for="j in jobList" :key="j" :value="j">{{ j }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="equipAll">전체 장착</button>
      <button class="btn btn-pink   btn-sm" @click="unequipAll">전체 해제</button>
      <span class="equip-count">{{ equippedCount }}개 장착 / 전체 {{ allItems.length }}개</span>
    </div>

    <!-- 직업 메모 -->
    <div v-if="jobNote" class="job-note">ℹ️ {{ jobNote }}</div>

    <!-- ── 카테고리 탭 ── -->
    <div class="cat-group">
      <!-- 장비 -->
      <div class="cat-group-label">장비</div>
      <div class="cat-tabs">
        <div class="cat-tab" :class="{active: activeCat==='전체'}" @click="activeCat='전체'">
          전체<span class="cnt">{{ allItems.length }}</span>
        </div>
        <template v-for="cat in equipCats">
          <div v-if="catCount(cat)>0" :key="cat"
               class="cat-tab" :class="{active: activeCat===cat}" @click="activeCat=cat">
            {{ cat }}<span class="cnt">{{ catCount(cat) }}</span>
          </div>
        </template>
      </div>
      <!-- 특수 -->
      <div class="cat-group-label" style="margin-top:8px">특수</div>
      <div class="cat-tabs">
        <template v-for="cat in specialCats">
          <div v-if="catCount(cat)>0" :key="cat"
               class="cat-tab" :class="{active: activeCat===cat}" @click="activeCat=cat">
            {{ cat }}<span class="cnt">{{ catCount(cat) }}</span>
          </div>
        </template>
      </div>
    </div>

    <!-- ── 아이템 그리드 ── -->
    <div v-if="filteredItems.length===0" class="empty">해당 카테고리에 아이템이 없습니다.</div>
    <div class="item-grid" v-else>
      <div v-for="item in filteredItems" :key="item.ITEM_ID"
           class="item-card" :class="cardClass(item)"
           @click="toggleItem(item)">

        <span class="toggle-icon">{{ toggleIcon(item) }}</span>

        <!-- 미발견: 이름/스탯 모두 숨김 -->
        <template v-if="isUndiscovered(item)">
          <div class="item-card-top">
            <div class="item-name" style="color:#d1d5db;font-style:italic">🔒 ???</div>
          </div>
          <span class="item-type" :class="typeClass(item.ITEM_TYPE)">{{ typeLabel(item.ITEM_TYPE) }}</span>
        </template>

        <!-- 일반 아이템 -->
        <template v-else>
          <div class="item-card-top">
            <div class="item-name">{{ item.ITEM_NAME }}</div>
            <div v-if="isOwned(item)" class="item-qty">×{{ item.OWN_QTY }}</div>
            <div v-else class="item-qty sim">시뮬</div>
          </div>
          <span class="item-type" :class="typeClass(item.ITEM_TYPE)">{{ typeLabel(item.ITEM_TYPE) }}</span>
          <div class="item-stats">
            <span v-if="+item.ATK_MIN||+item.ATK_MAX" class="stat-chip chip-atk">
              ATK {{ +item.ATK_MIN }}~{{ +item.ATK_MAX }}
            </span>
            <span v-if="+item.HP_MAX"       class="stat-chip chip-hp">HP +{{ fmt(+item.HP_MAX) }}</span>
            <span v-if="+item.ATK_CRI"      class="stat-chip chip-crit">CRI +{{ item.ATK_CRI }}%</span>
            <span v-if="+item.CRI_DMG"      class="stat-chip chip-cdmg">CDMG +{{ item.CRI_DMG }}%</span>
            <span v-if="+item.HP_REGEN"     class="stat-chip chip-regen">리젠 +{{ fmt(+item.HP_REGEN) }}</span>
            <span v-if="+item.HP_MAX_RATE"  class="stat-chip chip-rate">HP×{{ item.HP_MAX_RATE }}%</span>
            <span v-if="+item.ATK_MAX_RATE" class="stat-chip chip-rate">ATK×{{ item.ATK_MAX_RATE }}%</span>
          </div>
          <span v-if="!isOwned(item)" class="not-owned-label">📦 미보유 (시뮬용)</span>
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

  // ── ITEM_ID 범위 기반 카테고리 ─────────────────────────────────
  // EQUIP_CATEGORIES 의 int[][] 범위와 동일하게 유지
  function getSimCat(id) {
    id = +id;
    if (id >= 9000) return '유물';
    if (id >= 8000) return '업적';
    if (id >= 7000) return '보스';
    if ((id >= 100 && id < 200) || (id >= 1100 && id < 1200) || (id >= 2100 && id < 2200)) return '무기';
    if ((id >= 200 && id < 300) || (id >= 1200 && id < 1300)) return '투구';
    if ((id >= 400 && id < 500) || (id >= 1400 && id < 1500)) return '갑옷';
    if (id >= 300 && id < 400) return '행운';
    if (id >= 500 && id < 600) return '반지';
    if (id >= 600 && id < 700) return '토템';
    if (id >= 700 && id < 800) return '전설';
    if (id >= 800 && id < 900) return '날개';
    if (id >= 900 && id < 1000) return '선물';
    if (id >= 1000 && id < 1100) return '물약';
    return '기타';
  }

  var EQUIP_CATS   = ['무기','투구','갑옷','반지','전설','날개','토템','행운','선물','물약','기타'];
  var SPECIAL_CATS = ['보스','업적','유물'];

  new Vue({
    el: '#app',
    data: {
      inputName:   params.get('userName') || '',
      loading:     false,
      errorMsg:    '',
      user:        null,
      hunterGrade: 'F',
      jobList:     [],
      allItems:    [],
      hellMode:    false,
      simJob:      '',
      activeCat:   '전체',
      stats: { atkMin:0,atkMax:0,effAtkMin:0,effAtkMax:0,jobDmgMul:1,
               hpMax:0,regen:0,crit:0,critDmg:0,criConvert:0,criCap:5 },
      calcTimer:   null,
      equipCats:   EQUIP_CATS,
      specialCats: SPECIAL_CATS,
    },

    computed: {
      currentJob: function () {
        return this.simJob || (this.user && this.user.job) || '';
      },
      isHunterJob: function () {
        return this.currentJob === '헌터';
      },
      equippedCount: function () {
        return this.allItems.filter(function (i) { return i._equipped; }).length;
      },
      filteredItems: function () {
        var vm = this;
        if (vm.activeCat === '전체') return vm.allItems;
        return vm.allItems.filter(function (i) {
          return getSimCat(i.ITEM_ID) === vm.activeCat;
        });
      },
      jobNote: function () {
        var j = this.currentJob;
        var mul = this.stats.jobDmgMul;
        var notes = {
          '곰':       '공격력 = 최대체력 전환 / 치명타 미사용',
          '헌터':     '등급 보너스 적용 / 치명타 100% 초과→치피전환',
          '어둠사냥꾼':'아이템 HP/리젠 ×1.25',
          '검성':     '기본 최대체력 ×2 추가',
          '용사':     '기본 최대체력 ×2 추가',
          '흡혈귀':   '리젠 = 0',
          '궁수':     '쿨타임 2→10분 / 7%확률 7배 데미지',
          '사냥꾼':   '쿨타임 2→10분 / 동물·인간형 추가피해',
          '처단자':   '방어 무시 150% 추가데미지 / 빛 몬스터 +50%',
          '도적':     '50% 확률 조각 추가드랍 / 20% 더블어택',
          '복수자':   '피해반사 메인딜 (직접 공격력 ×0.2)',
          '궁사':     '최대-최소 차이당 연사 (최대 5연사)',
          '음양사':   '공격 시 아군 강화 + 자신 힐',
          '축복술사': '공격 시 무작위 아군 축복 / 쿨타임 30분',
          '도박사':   '공격·피격 시 랜덤 도박',
        };
        var base = notes[j] || '';
        if (mul && mul !== 1) {
          var pct = Math.round(mul * 100);
          base = (base ? base + ' / ' : '') + '직업 데미지 배율 ' + pct + '%';
        }
        return base;
      },
    },

    watch: {
      inputName: function (val) {
        var url = new URL(window.location.href);
        url.searchParams.set('userName', val);
        window.history.replaceState({}, '', url.toString());
      },
    },

    mounted: function () { if (this.inputName) this.load(); },

    methods: {
      catCount: function (cat) {
        return this.allItems.filter(function (i) {
          return getSimCat(i.ITEM_ID) === cat;
        }).length;
      },

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
            // 보유 아이템은 기본 장착, 미보유/미발견은 해제
            vm.allItems = (data.allItems || []).map(function (item) {
              return Object.assign({}, item, { _equipped: +item.OWN_QTY > 0 });
            });
            vm.recalc();
          })
          .catch(function () { vm.errorMsg = '서버 오류가 발생했습니다.'; })
          .finally(function () { vm.loading = false; });
      },

      isOwned: function (item) { return +item.OWN_QTY > 0; },

      isUndiscovered: function (item) {
        // ITEM_ID >= 7000 이면서 누구도 획득한 적 없으면 미발견
        return +item.ITEM_ID >= 7000 && +item.EVER_OBTAINED_CNT === 0;
      },

      cardClass: function (item) {
        if (this.isUndiscovered(item)) return 'undiscovered';
        if (this.isOwned(item))  return item._equipped ? 'equipped'     : 'unequipped';
        return item._equipped ? 'sim-equipped' : 'not-owned';
      },

      toggleIcon: function (item) {
        if (this.isUndiscovered(item)) return '🔒';
        if (item._equipped) return this.isOwned(item) ? '✔' : '⚡';
        return '○';
      },

      toggleItem: function (item) {
        if (this.isUndiscovered(item)) return;
        item._equipped = !item._equipped;
        this.scheduleRecalc();
      },

      equipAll: function () {
        // 보유 아이템만 장착 (미보유는 유지)
        this.allItems.forEach(function (i) { if (+i.OWN_QTY > 0) i._equipped = true; });
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
            equipped.push({ itemId: +item.ITEM_ID, qty: +item.OWN_QTY > 0 ? +item.OWN_QTY : 1 });
          }
        });
        fetch(ctx + '/loa/api/equip-sim-calc', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userName: vm.user.userName, simJob: vm.simJob,
            hellMode: vm.hellMode, equippedItems: equipped
          })
        })
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (!data.error) {
              vm.stats = {
                atkMin:     data.atkMin     || 0,
                atkMax:     data.atkMax     || 0,
                effAtkMin:  data.effAtkMin  || 0,
                effAtkMax:  data.effAtkMax  || 0,
                jobDmgMul:  data.jobDmgMul  || 1,
                hpMax:      data.hpMax      || 0,
                regen:      data.regen      || 0,
                crit:       data.crit       || 0,
                critDmg:    data.critDmg    || 0,
                criConvert: data.criConvert || 0,
                criCap:     data.criCap     || 5,
              };
              vm.hunterGrade = data.hunterGrade || vm.hunterGrade;
            }
          })
          .catch(function () {});
      },

      fmt: function (n) { return Number(n).toLocaleString(); },

      typeClass: function (t) {
        if (!t) return 'type-other';
        if (t === 'BOSS_HELL' || t === 'BOSS_GACHA') return 'type-boss';
        if (t === 'ACHV') return 'type-achv';
        if (t === 'POTION') return 'type-potion';
        if (t.indexOf('MARKET') !== -1) return 'type-market';
        return 'type-other';
      },

      typeLabel: function (t) {
        var M = { MARKET:'상점',MARKET2:'상점',POTION:'물약',
                  BOSS_HELL:'보스유물',BOSS_GACHA:'보스가챠',
                  ACHV:'업적',BAG_OPEN:'드랍',BAG_OPEN_NM:'드랍(NM)',DROP:'드랍' };
        return M[t] || (t || '기타');
      },
    },
  });
})();
</script>
</body>
</html>
