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
    body { background: #f8f5f0; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; min-height: 100vh; }

    .wrap { max-width: 900px; margin: 0 auto; padding: 20px 12px 60px; }

    /* 헤더 */
    .page-header { margin-bottom: 18px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
    .page-title  { font-size: 20px; font-weight: 800; color: #3d2b1f; }
    .search-row  { display: flex; gap: 8px; }
    .search-row input { padding: 8px 14px; border: 1.5px solid #e0d9ce; border-radius: 20px; background: #fff; color: #333; font-size: 13px; width: 160px; outline: none; transition: border-color .2s; }
    .search-row input:focus { border-color: #c9a96e; }
    .btn-search  { background: #c9a96e; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #b8935a; }

    .loading { text-align: center; padding: 60px; color: #bbb; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #bbb; }

    /* ── 장비 화면 레이아웃 ── */
    .equip-screen { display: grid; grid-template-columns: 1fr 190px; gap: 14px; align-items: start; }

    /* 캐릭터 왼쪽 영역 */
    .char-area { display: flex; flex-direction: column; align-items: center; gap: 8px; }

    /* 귀여운 캐릭터 박스 (세로 portrait) */
    .char-box {
      background: linear-gradient(160deg, #fff0fa 0%, #ede0ff 55%, #ddf0ff 100%);
      border: 3px solid #e8c0da;
      border-radius: 24px;
      width: 100%;
      max-width: 280px;
      height: 300px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 6px 24px rgba(200,140,180,.22), 0 0 0 5px rgba(232,192,218,.15);
      transition: transform .2s;
      overflow: hidden;
      padding: 10px;
    }
    .char-box:hover { transform: scale(1.02); }

    .char-name { font-size: 15px; font-weight: 800; color: #3d2b1f; text-align: center; }
    .char-job  { font-size: 12px; color: #a07858; text-align: center; }
    .char-lv   { font-size: 22px; font-weight: 900; color: #c9a96e; text-align: center; }

    /* 합계 칩 */
    .summary-row { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; margin-top: 4px; }
    .sum-chip { background: #fff; border: 1px solid #e8ddd0; border-radius: 8px; padding: 4px 10px; font-size: 11px; color: #888; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
    .sum-chip strong { color: #c9a96e; margin-left: 3px; }

    /* 물약 사용 뱃지 */
    .potion-badge { background: #fff; border: 1px solid #e8ddd0; border-radius: 8px; padding: 4px 12px; font-size: 11px; color: #a07858; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.04); }

    /* ── 오른쪽 슬롯 패널 ── */
    .slot-panel { display: flex; flex-direction: column; gap: 7px; }

    /* 개별 슬롯 (가로 행) */
    .slot {
      background: #fff;
      border: 1.5px solid #e8ddd0;
      border-radius: 12px;
      padding: 7px 10px;
      cursor: default;
      transition: border-color .2s, background .2s, box-shadow .2s;
      display: flex;
      align-items: center;
      gap: 9px;
      box-shadow: 0 1px 4px rgba(0,0,0,.05);
      min-height: 52px;
    }
    .slot.has-item { border-color: #c9a96e; cursor: pointer; }
    .slot.has-item:hover { background: #fffbf5; border-color: #b8935a; box-shadow: 0 3px 10px rgba(180,120,60,.12); }

    /* 특별 아이템 (기본 티어): 금테 강조 */
    .slot.special-item { border-color: #c9a96e; background: #fffdf7; }
    .slot.special-item:hover { background: #fff8e8; border-color: #b8935a; }

    /* 슬롯 아이콘 박스 */
    .slot-icon-box {
      width: 42px; height: 42px;
      background: linear-gradient(135deg, #f5f0e8, #ede4d4);
      border: 1.5px solid #e0d0b8;
      border-radius: 9px;
      display: flex; align-items: center; justify-content: center;
      font-size: 22px; flex-shrink: 0;
    }
    .slot.has-item    .slot-icon-box { background: linear-gradient(135deg, #fff8e8, #f5e8cc); border-color: #c9a96e; }
    .slot.special-item .slot-icon-box { background: linear-gradient(135deg, #fff3cc, #ffe090); border-color: #c9a96e; box-shadow: 0 0 6px rgba(201,169,110,.3); }

    /* 슬롯 텍스트 */
    .slot-info   { flex: 1; min-width: 0; }
    .slot-label  { font-size: 9px; color: #c0b0a0; letter-spacing: .4px; margin-bottom: 1px; }
    .slot-name   { font-size: 11px; color: #555; line-height: 1.3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .slot-qty    { font-size: 9px; color: #c9a96e; margin-top: 1px; }
    .slot-empty  { font-size: 11px; color: #ccc; }

    /* ── 툴팁/모달 ── */
    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 100; display: flex; align-items: center; justify-content: center; }
    .modal-box { background: #fff; border: 2px solid #c9a96e; border-radius: 16px; padding: 20px 22px; min-width: 240px; max-width: 320px; box-shadow: 0 8px 32px rgba(0,0,0,.15); position: relative; }
    .modal-close { position: absolute; top: 10px; right: 14px; font-size: 18px; color: #bbb; cursor: pointer; line-height: 1; }
    .modal-close:hover { color: #555; }
    .modal-title { font-size: 15px; font-weight: 800; color: #c9a96e; margin-bottom: 12px; }
    .modal-stat  { display: flex; justify-content: space-between; font-size: 12px; padding: 4px 0; border-bottom: 1px solid #f0ece4; }
    .modal-stat:last-child { border-bottom: none; }
    .modal-stat .lbl { color: #999; }
    .modal-stat .val { color: #5a9e6f; font-weight: 700; }

    /* 하단 기타 아이템 목록 */
    .others-section { margin-top: 14px; }
    .others-title { font-size: 12px; color: #bbb; margin-bottom: 8px; font-weight: 700; }
    .others-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 6px; }
    .other-chip { background: #fff; border: 1px solid #e8ddd0; border-radius: 8px; padding: 6px 10px; font-size: 11px; color: #888; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
    .other-chip .o-name { color: #3d2b1f; font-weight: 600; }
    .other-chip .o-qty  { color: #c9a96e; margin-left: 4px; }

    @media (max-width: 600px) {
      .equip-screen { grid-template-columns: 110px 1fr 110px; gap: 6px; }
      .char-box { width: 110px; height: 110px; font-size: 50px; }
    }

    @media (max-width: 360px) {
      /* 전체 레이아웃: 세로 스택 */
      .equip-screen { grid-template-columns: 1fr; gap: 8px; }

      /* 캐릭터 영역: 가로 헤더 카드로 전환 */
      .char-area {
        flex-direction: row;
        align-items: center;
        gap: 10px;
        background: linear-gradient(135deg, #fff0fa 0%, #ede0ff 55%, #ddf0ff 100%);
        border: 1.5px solid #e8c0da;
        border-radius: 12px;
        padding: 8px 10px;
      }
      .char-box {
        width: 52px; height: 52px; min-width: 52px; max-width: 52px;
        border-radius: 12px; font-size: 26px; flex-shrink: 0;
      }
      .char-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
      .char-lv   { font-size: 18px; font-weight: 900; text-align: left; }
      .char-name { font-size: 12px; font-weight: 800; text-align: left; }
      .char-job  { font-size: 10px; text-align: left; }
      .summary-row { justify-content: flex-start; margin-top: 3px; }
      .sum-chip  { font-size: 8px; padding: 1px 5px; }
      .potion-badge { font-size: 9px; padding: 2px 8px; text-align: left; }

      /* 슬롯 패널: 2열 그리드 */
      .slot-panel { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; }
      .slot { padding: 5px 7px; min-height: 36px; gap: 5px; }
      .slot-icon-box { width: 26px; height: 26px; font-size: 13px; border-radius: 6px; }
      .slot-label { font-size: 7.5px; }
      .slot-name  { font-size: 9px; }
      .slot-empty { font-size: 9px; }

      /* 모달 / 검색 */
      .modal-box { min-width: 0; width: 92vw; box-sizing: border-box; }
      .search-row input { width: 100px; font-size: 12px; padding: 7px 10px; }
      .btn-search { padding: 7px 12px; font-size: 12px; }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
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

      <!-- 왼쪽: 캐릭터 -->
      <div class="char-area">
        <div class="char-box">
          <img v-if="charImgUrl" :src="charImgUrl"
               style="width:100%;height:100%;object-fit:cover;border-radius:18px;" alt="캐릭터">
          <span v-else style="font-size:48px;">🧙</span>
        </div>
        <div class="char-meta">
          <div class="char-lv" v-if="userLv > 0">Lv {{ userLv }}</div>
          <div class="char-name">{{ userName }}</div>
          <div class="char-job" v-if="userJob">{{ userJob }}</div>
          <div class="summary-row">
            <div class="sum-chip" v-for="g in groupSummary" :key="g.label">
              {{ g.label }} <strong>{{ g.total }}</strong>
            </div>
          </div>
          <div class="potion-badge">🧪 물약 사용: {{ potionUseCount }}회</div>
        </div>
      </div>

      <!-- 오른쪽: 장비 슬롯 세로 목록 -->
      <div class="slot-panel">
        <div v-for="slot in allSlots" :key="slot.cat"
             class="slot" :class="slotClass(slot)"
             @click="openModal(slot)">
          <div class="slot-icon-box">{{ catIcon(slot.cat) }}</div>
          <div class="slot-info">
            <div class="slot-label">{{ slot.cat }}</div>
            <template v-if="slot.item">
              <div class="slot-name">{{ slot.item.ITEM_NAME }}</div>
              <div class="slot-qty" v-if="slot.item.TOTAL_QTY > 1">×{{ slot.item.TOTAL_QTY }}</div>
            </template>
            <div class="slot-empty" v-else>비어있음</div>
          </div>
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
      <div v-else style="color:#ccc;font-size:13px;margin-top:8px;">장착된 아이템 없음</div>
    </div>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
// 슬롯 정의: cat → ITEM_ID 범위 배열 (EQUIP_CATEGORIES 기준)
// 무기(100-200, 1100-1200, 2100-2200), 투구(200-300, 1200-1300), 갑옷(400-500, 1400-1500) 등
var SLOT_DEFS = {
  '무기': [{ min: 100,  max: 200  }, { min: 1100, max: 1200 }, { min: 2100, max: 2200 }],
  '투구': [{ min: 200,  max: 300  }, { min: 1200, max: 1300 }],
  '갑옷': [{ min: 400,  max: 500  }, { min: 1400, max: 1500 }],
  '전설': [{ min: 700,  max: 800  }],
  '날개': [{ min: 800,  max: 900  }]
};

// 각 티어의 기본(시작) 아이템 ID → 금테 강조
var SPECIAL_ITEM_IDS = [100, 200, 400, 700, 800, 1100, 1200, 1400, 2100];

// 합계 표시 범위 (행운/반지/토템/선물/업적/유물)
var GROUP_DEFS = [
  { label: '행운', min: 300,  max: 400  },
  { label: '반지', min: 500,  max: 600  },
  { label: '토템', min: 600,  max: 700  },
  { label: '선물', min: 900,  max: 1000 },
  { label: '업적', min: 8000, max: 9000 },
  { label: '유물', min: 9000, max: 99999 }
];

var CAT_ICONS = {
  '무기': '⚔️', '투구': '🪖', '갑옷': '🛡️', '전설': '✨', '날개': '🪽',
  '물약': '🧪', '유물': '🏺', '업적': '🏆', '행운': '🍀', '반지': '💍',
  '토템': '🗿', '선물': '🎁', '기타': '📦'
};

// nekos.best 캐릭터 이미지 (localStorage 캐싱)
var CHAR_IMG_KEY_PREFIX = 'loaCharImg_';

function getCharImgUrl(userName) {
  return localStorage.getItem(CHAR_IMG_KEY_PREFIX + userName) || null;
}

function fetchAndCacheCharImg(userName, callback) {
  var cached = getCharImgUrl(userName);
  if (cached) { callback(cached); return; }
  fetch('https://nekos.best/api/v2/neko')
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var url = data.results && data.results[0] ? data.results[0].url : null;
      if (url) {
        localStorage.setItem(CHAR_IMG_KEY_PREFIX + userName, url);
        callback(url);
      }
    })
    .catch(function() { /* 실패 시 무시 */ });
}

function inSlotRanges(id, cat) {
  var ranges = SLOT_DEFS[cat];
  for (var i = 0; i < ranges.length; i++) {
    if (id >= ranges[i].min && id < ranges[i].max) return true;
  }
  return false;
}

new Vue({
  el: '#app',
  data: {
    inputUser: '',
    userName: '',
    userLv: 0,
    userJob: '',
    loading: false,
    inventory: [],
    potionUseCount: 0,
    modal: null,
    charImgUrl: null
  },
  computed: {

    // 슬롯별 대표 아이템 (같은 카테고리 내 보유 중 가장 높은 티어)
    slotMap: function() {
      var map = {};
      Object.keys(SLOT_DEFS).forEach(function(cat) { map[cat] = null; });
      this.inventory.forEach(function(it) {
        var id = +it.ITEM_ID;
        Object.keys(SLOT_DEFS).forEach(function(cat) {
          if (inSlotRanges(id, cat)) {
            if (!map[cat] || id > +map[cat].ITEM_ID) map[cat] = it;
          }
        });
      });
      return map;
    },

    allSlots: function() {
      return ['투구','무기','갑옷','전설','날개'].map(this.mkSlot);
    },

    groupSummary: function() {
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

    // 슬롯·그룹에 안 들어간 나머지 (업적 등)
    otherItems: function() {
      var inv = this.inventory;
      var otherIds = [];
      inv.forEach(function(it) {
        var id = +it.ITEM_ID;
        var inMain = false;
        Object.keys(SLOT_DEFS).forEach(function(cat) {
          if (inSlotRanges(id, cat)) inMain = true;
        });
        GROUP_DEFS.forEach(function(g) {
          if (id >= g.min && id < g.max) inMain = true;
        });
        if (!inMain) otherIds.push(id);
      });
      var set = otherIds;
      return inv.filter(function(it) { return set.indexOf(+it.ITEM_ID) !== -1; });
    }
  },
  methods: {
    mkSlot: function(cat) { return { cat: cat, item: this.slotMap[cat] || null }; },
    catIcon: function(cat) { return CAT_ICONS[cat] || '📦'; },

    slotClass: function(slot) {
      if (!slot.item) return {};
      var id = +slot.item.ITEM_ID;
      return {
        'has-item': true,
        'special-item': SPECIAL_ITEM_IDS.indexOf(id) !== -1
      };
    },

    openModal: function(slot) {
      if (!slot.item) return;
      this.modal = slot;
    },

    itemStats: function(item) {
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

    fetchInfo: function() {
      var name = this.inputUser.trim();
      if (!name) return;
      this.loading = true;
      this.inventory = [];
      this.modal = null;
      var self = this;
      fetch('<%=request.getContextPath()%>/loa/api/user-info?userName=' + encodeURIComponent(name))
        .then(function(r) { return r.json(); })
        .then(function(data) {
          self.userName       = data.userName       || name;
          self.userLv         = data.lv             || 0;
          self.userJob        = data.job             || '';
          self.inventory      = data.inventory      || [];
          self.potionUseCount = data.potionUseCount || 0;
          self.loading = false;
          sessionStorage.setItem('loaUserName', self.userName);
          // 캐릭터 이미지 (localStorage 캐싱)
          self.charImgUrl = getCharImgUrl(self.userName);
          if (!self.charImgUrl) {
            fetchAndCacheCharImg(self.userName, function(url) {
              self.charImgUrl = url;
            });
          }
        })
        .catch(function() { self.loading = false; });
    }
  },
  mounted: function() {
    var params = new URLSearchParams(window.location.search);
    var u = (params.get('userName') || params.get('user') || '').trim();
    if (u) {
      this.inputUser = u;
      this.fetchInfo();
    }
  }
});
</script>
</body>
</html>
