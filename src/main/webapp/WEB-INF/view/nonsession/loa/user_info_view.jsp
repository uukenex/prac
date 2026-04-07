<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 장비 정보</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #1a1a2e; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #e0e0e0; min-height: 100vh; }

    .wrap { max-width: 900px; margin: 0 auto; padding: 20px 12px 60px; }

    /* 헤더 */
    .page-header { margin-bottom: 18px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #a8c0ff; }
    .search-row  { display: flex; gap: 8px; }
    .search-row input { padding: 8px 14px; border: 1.5px solid #3a3a6a; border-radius: 20px; background: #16213e; color: #e0e0e0; font-size: 13px; width: 160px; outline: none; }
    .search-row input:focus { border-color: #a8c0ff; }
    .btn-search  { background: #4a6fa5; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #3a5f95; }

    .loading { text-align: center; padding: 60px; color: #666; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #555; }

    /* ── 장비 화면 레이아웃 ── */
    .equip-screen { display: grid; grid-template-columns: 140px 1fr 140px; gap: 10px; min-height: 420px; }

    /* 좌우 슬롯 패널 */
    .slot-col { display: flex; flex-direction: column; gap: 8px; }

    /* 캐릭터 중앙 영역 */
    .char-area { display: flex; flex-direction: column; align-items: center; gap: 8px; }
    .char-box  { background: #0f3460; border: 2px solid #4a6fa5; border-radius: 16px; width: 160px; height: 220px; display: flex; align-items: center; justify-content: center; font-size: 72px; flex-shrink: 0; }
    .char-name { font-size: 14px; font-weight: 800; color: #a8c0ff; text-align: center; }
    .char-job  { font-size: 12px; color: #7fa8d4; text-align: center; }
    .char-lv   { font-size: 20px; font-weight: 900; color: #ffd700; text-align: center; }

    /* 상단/하단 슬롯 (가로 배치) */
    .slot-row  { display: flex; gap: 8px; justify-content: center; }

    /* 개별 슬롯 */
    .slot {
      background: #16213e;
      border: 1.5px solid #2a2a5a;
      border-radius: 12px;
      padding: 8px 10px;
      min-height: 68px;
      cursor: default;
      transition: border-color .2s, background .2s;
      position: relative;
    }
    .slot.has-item { border-color: #4a6fa5; cursor: pointer; }
    .slot.has-item:hover { background: #1e2f5a; border-color: #a8c0ff; }
    .slot.slot-sm { min-height: 56px; }

    /* 특별 아이템 (100,200,400,700,800): 금테 */
    .slot.special-item { border-color: #c9a96e; }
    .slot.special-item:hover { background: #1e1a10; border-color: #ffd700; }

    .slot-label { font-size: 9px; color: #556; text-transform: uppercase; letter-spacing: .5px; margin-bottom: 2px; }
    .slot-icon  { font-size: 20px; line-height: 1; }
    .slot-name  { font-size: 10px; color: #9ab; margin-top: 3px; line-height: 1.3; }
    .slot-qty   { font-size: 9px; color: #ffd700; margin-top: 2px; }
    .slot-empty { font-size: 11px; color: #333; margin-top: 4px; }

    /* 합계 칸 (행운/반지/토템/선물) */
    .summary-row { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; margin-top: 6px; }
    .sum-chip { background: #16213e; border: 1px solid #2a2a5a; border-radius: 8px; padding: 4px 10px; font-size: 11px; color: #9ab; }
    .sum-chip strong { color: #ffd700; margin-left: 3px; }

    /* 포션 사용 뱃지 */
    .potion-badge { background: #0f3460; border: 1px solid #4a6fa5; border-radius: 8px; padding: 4px 12px; font-size: 11px; color: #a8c0ff; text-align: center; margin-top: 4px; }

    /* ── 툴팁/모달 ── */
    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.6); z-index: 100; display: flex; align-items: center; justify-content: center; }
    .modal-box { background: #0f3460; border: 2px solid #c9a96e; border-radius: 16px; padding: 20px 22px; min-width: 240px; max-width: 320px; box-shadow: 0 8px 32px rgba(0,0,0,.5); position: relative; }
    .modal-close { position: absolute; top: 10px; right: 14px; font-size: 18px; color: #888; cursor: pointer; line-height: 1; }
    .modal-close:hover { color: #fff; }
    .modal-title { font-size: 15px; font-weight: 800; color: #ffd700; margin-bottom: 12px; }
    .modal-stat  { display: flex; justify-content: space-between; font-size: 12px; padding: 4px 0; border-bottom: 1px solid #1a2a4a; }
    .modal-stat:last-child { border-bottom: none; }
    .modal-stat .lbl { color: #9ab; }
    .modal-stat .val { color: #a8e6cf; font-weight: 700; }

    /* 하단 기타 아이템 목록 */
    .others-section { margin-top: 14px; }
    .others-title { font-size: 12px; color: #556; margin-bottom: 8px; font-weight: 700; }
    .others-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 6px; }
    .other-chip { background: #16213e; border: 1px solid #2a2a5a; border-radius: 8px; padding: 6px 10px; font-size: 11px; color: #9ab; }
    .other-chip .o-name { color: #c0c0c0; font-weight: 600; }
    .other-chip .o-qty  { color: #ffd700; margin-left: 4px; }

    @media (max-width: 600px) {
      .equip-screen { grid-template-columns: 110px 1fr 110px; gap: 6px; }
      .char-box { width: 120px; height: 170px; font-size: 54px; }
    }
  </style>
</head>
<body>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">⚔ 람쥐봇 장비 정보</div>
    <div class="search-row">
      <input v-model="inputUser" placeholder="유저명 입력" @keyup.enter="fetchInfo">
      <button class="btn-search" @click="fetchInfo">조회</button>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty" v-else-if="!userName">유저명을 입력해 조회하세요.</div>

  <template v-else>
    <!-- ── 장비 화면 ── -->
    <div class="equip-screen">

      <!-- 좌측 슬롯: 무기, 갑옷 -->
      <div class="slot-col">
        <div v-for="slot in leftSlots" :key="slot.cat"
             class="slot" :class="slotClass(slot)"
             @click="openModal(slot)">
          <div class="slot-label">{{ slot.cat }}</div>
          <template v-if="slot.item">
            <div class="slot-icon">{{ catIcon(slot.cat) }}</div>
            <div class="slot-name">{{ slot.item.ITEM_NAME }}</div>
            <div class="slot-qty" v-if="slot.item.TOTAL_QTY > 1">×{{ slot.item.TOTAL_QTY }}</div>
          </template>
          <div class="slot-empty" v-else>-</div>
        </div>
      </div>

      <!-- 중앙: 캐릭터 + 상/하 슬롯 -->
      <div class="char-area">
        <!-- 상단: 투구 -->
        <div class="slot-row">
          <div v-for="slot in topSlots" :key="slot.cat"
               class="slot slot-sm" :class="slotClass(slot)"
               @click="openModal(slot)">
            <div class="slot-label">{{ slot.cat }}</div>
            <template v-if="slot.item">
              <div class="slot-icon">{{ catIcon(slot.cat) }}</div>
              <div class="slot-name">{{ slot.item.ITEM_NAME }}</div>
            </template>
            <div class="slot-empty" v-else>-</div>
          </div>
        </div>

        <!-- 캐릭터 -->
        <div class="char-box">🧙</div>
        <div class="char-lv">Lv {{ userName }}</div>
        <div class="char-name">{{ userName }}</div>

        <!-- 하단: 날개 -->
        <div class="slot-row">
          <div v-for="slot in bottomSlots" :key="slot.cat"
               class="slot slot-sm" :class="slotClass(slot)"
               @click="openModal(slot)">
            <div class="slot-label">{{ slot.cat }}</div>
            <template v-if="slot.item">
              <div class="slot-icon">{{ catIcon(slot.cat) }}</div>
              <div class="slot-name">{{ slot.item.ITEM_NAME }}</div>
            </template>
            <div class="slot-empty" v-else>-</div>
          </div>
        </div>

        <!-- 합계 요약 (행운/반지/토템/선물) -->
        <div class="summary-row">
          <div class="sum-chip" v-for="g in groupSummary" :key="g.label">
            {{ g.label }} <strong>{{ g.total }}</strong>
          </div>
        </div>
        <!-- 물약 사용 -->
        <div class="potion-badge">🧪 물약 사용: {{ potionUseCount }}회</div>
      </div>

      <!-- 우측 슬롯: 전설, 유물 -->
      <div class="slot-col">
        <div v-for="slot in rightSlots" :key="slot.cat"
             class="slot" :class="slotClass(slot)"
             @click="openModal(slot)">
          <div class="slot-label">{{ slot.cat }}</div>
          <template v-if="slot.item">
            <div class="slot-icon">{{ catIcon(slot.cat) }}</div>
            <div class="slot-name">{{ slot.item.ITEM_NAME }}</div>
          </template>
          <div class="slot-empty" v-else>-</div>
        </div>
      </div>
    </div>

    <!-- 기타 아이템 (업적 등) -->
    <div class="others-section" v-if="otherItems.length > 0">
      <div class="others-title">기타 아이템</div>
      <div class="others-grid">
        <div class="other-chip" v-for="it in otherItems" :key="it.ITEM_ID">
          <span class="o-name">{{ it.ITEM_NAME }}</span>
          <span class="o-qty">×{{ it.TOTAL_QTY }}</span>
        </div>
      </div>
    </div>
  </template>

  <!-- ── 모달 ── -->
  <div class="modal-overlay" v-if="modal" @click.self="modal=null">
    <div class="modal-box">
      <span class="modal-close" @click="modal=null">✕</span>
      <div class="modal-title">{{ catIcon(modal.cat) }} {{ modal.item ? modal.item.ITEM_NAME : modal.cat }}</div>
      <template v-if="modal.item">
        <div class="modal-stat" v-for="s in itemStats(modal.item)" :key="s.lbl">
          <span class="lbl">{{ s.lbl }}</span>
          <span class="val">{{ s.val }}</span>
        </div>
        <div class="modal-stat">
          <span class="lbl">보유 수량</span>
          <span class="val">{{ modal.item.TOTAL_QTY }}</span>
        </div>
      </template>
      <div v-else style="color:#666;font-size:13px;margin-top:8px;">장착된 아이템 없음</div>
    </div>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
// 옵션 표시할 특정 아이템 ID 목록
var OPTION_ITEM_IDS = [100, 200, 400, 700, 800];

// 슬롯 정의: cat → ITEM_ID 범위
var SLOT_DEFS = {
  '무기': { min: 100, max: 200 },
  '투구': { min: 200, max: 300 },
  '갑옷': { min: 400, max: 500 },
  '전설': { min: 700, max: 800 },
  '날개': { min: 800, max: 900 },
  '유물': { min: 9000, max: 99999 }
};

// 합계 표시 범위
var GROUP_DEFS = [
  { label: '행운', min: 300, max: 400 },
  { label: '반지', min: 500, max: 600 },
  { label: '토템', min: 600, max: 700 },
  { label: '선물', min: 900, max: 1000 }
];

var CAT_ICONS = {
  '무기': '⚔️', '투구': '🪖', '갑옷': '🛡️', '전설': '✨', '날개': '🪽',
  '유물': '🏺', '업적': '🏆', '행운': '🍀', '반지': '💍', '토템': '🗿', '선물': '🎁', '기타': '📦'
};

new Vue({
  el: '#app',
  data: {
    inputUser: '',
    userName: '',
    loading: false,
    inventory: [],
    potionUseCount: 0,
    modal: null
  },
  computed: {
    // 슬롯별 대표 아이템 (보유 중 첫 번째)
    slotMap() {
      var map = {};
      Object.keys(SLOT_DEFS).forEach(function(cat) {
        map[cat] = null;
      });
      this.inventory.forEach(function(it) {
        var id = +it.ITEM_ID;
        Object.keys(SLOT_DEFS).forEach(function(cat) {
          var def = SLOT_DEFS[cat];
          if (id >= def.min && id < def.max) {
            if (!map[cat]) map[cat] = it;
          }
        });
      });
      return map;
    },
    leftSlots()   { return [this.mkSlot('무기'), this.mkSlot('갑옷')]; },
    topSlots()    { return [this.mkSlot('투구')]; },
    bottomSlots() { return [this.mkSlot('날개')]; },
    rightSlots()  { return [this.mkSlot('전설'), this.mkSlot('유물')]; },

    groupSummary() {
      var inv = this.inventory;
      return GROUP_DEFS.map(function(g) {
        var total = 0;
        inv.forEach(function(it) {
          var id = +it.ITEM_ID;
          if (id >= g.min && id < g.max) total += (+it.TOTAL_QTY || 0);
        });
        return { label: g.label, total: total };
      }).filter(function(g) { return g.total > 0; });
    },

    // 슬롯에 안 들어간 나머지 (업적 등)
    otherItems() {
      var mainIds = new Set();
      var inv = this.inventory;
      inv.forEach(function(it) {
        var id = +it.ITEM_ID;
        var inMain = false;
        Object.keys(SLOT_DEFS).forEach(function(cat) {
          var def = SLOT_DEFS[cat];
          if (id >= def.min && id < def.max) inMain = true;
        });
        GROUP_DEFS.forEach(function(g) {
          if (id >= g.min && id < g.max) inMain = true;
        });
        if (!inMain) mainIds.add(id);
      });
      return inv.filter(function(it) { return mainIds.has(+it.ITEM_ID); });
    }
  },
  methods: {
    mkSlot(cat) { return { cat: cat, item: this.slotMap[cat] || null }; },
    catIcon(cat) { return CAT_ICONS[cat] || '📦'; },

    slotClass(slot) {
      if (!slot.item) return {};
      var id = +slot.item.ITEM_ID;
      return {
        'has-item': true,
        'special-item': OPTION_ITEM_IDS.indexOf(id) !== -1
      };
    },

    openModal(slot) {
      if (!slot.item) return;
      this.modal = slot;
    },

    itemStats(item) {
      var stats = [];
      var add = function(lbl, key) {
        var v = +item[key];
        if (v && v !== 0) stats.push({ lbl: lbl, val: v });
      };
      add('공격력 최소', 'ATK_MIN');
      add('공격력 최대', 'ATK_MAX');
      add('치명타율', 'ATK_CRI');
      add('치명타 피해', 'CRI_DMG');
      add('최대 체력', 'HP_MAX');
      add('HP 비율', 'HP_MAX_RATE');
      add('ATK 비율', 'ATK_MAX_RATE');
      add('체력 회복', 'HP_REGEN');
      return stats;
    },

    fetchInfo() {
      var name = this.inputUser.trim();
      if (!name) return;
      this.loading = true;
      this.inventory = [];
      this.modal = null;
      var self = this;
      fetch('<%=request.getContextPath()%>/loa/api/user-info?userName=' + encodeURIComponent(name))
        .then(function(r) { return r.json(); })
        .then(function(data) {
          self.userName = data.userName || name;
          self.inventory = data.inventory || [];
          self.potionUseCount = data.potionUseCount || 0;
          self.loading = false;
        })
        .catch(function() { self.loading = false; });
    }
  }
});
</script>
</body>
</html>
