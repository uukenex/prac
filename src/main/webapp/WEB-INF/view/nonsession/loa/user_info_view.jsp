<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 장비 정보</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0;
        -webkit-user-select: none; user-select: none;
        -webkit-tap-highlight-color: transparent; }
    input, textarea { -webkit-user-select: text; user-select: text; }
    body { background: #f0ece6; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; min-height: 100vh; }
    .wrap { max-width: 860px; margin: 0 auto; padding: 16px 14px 60px; }

    /* ─ 헤더 ─ */
    .page-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; }
    .page-title  { font-size: 18px; font-weight: 800; color: #3d2b1f; }
    .search-row  { display: flex; gap: 8px; }
    .search-row input { padding: 8px 14px; border: 1.5px solid #ddd3c5; border-radius: 20px; background: #fff; color: #333; font-size: 13px; width: 160px; outline: none; transition: border-color .2s; }
    .search-row input:focus { border-color: #c9a96e; }
    .btn-search  { background: #c9a96e; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-search:hover { background: #b8935a; }
    .btn-attack  { background: #e05050; color: #fff; border: none; padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; transition: background .2s; }
    .btn-attack:hover  { background: #c43c3c; }
    .btn-attack:disabled { background: #e09898; cursor: default; }
    .btn-history { background: #5a7fc9; color: #fff; border: none; padding: 8px 16px; border-radius: 20px; font-size: 13px; font-weight: 700; cursor: pointer; transition: background .2s; }
    .btn-history:hover { background: #4465b0; }
    .loading { text-align: center; padding: 60px; color: #bbb; font-size: 15px; }
    .empty   { text-align: center; padding: 60px; color: #bbb; }

    /* ─ 토스트 ─ */
    .toast {
      position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
      background: rgba(30,20,10,.88); color: #fff; border-radius: 14px;
      padding: 12px 22px; font-size: 13px; line-height: 1.55;
      max-width: 88vw; white-space: pre-wrap; word-break: break-all;
      box-shadow: 0 4px 20px rgba(0,0,0,.3); z-index: 200;
      transition: opacity .3s;
    }
    .toast.hide { opacity: 0; pointer-events: none; }

    /* ─ 이력 패널 ─ */
    .history-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 150; display: flex; align-items: flex-end; justify-content: center; }
    .history-panel {
      background: #fff; border-radius: 20px 20px 0 0; width: 100%; max-width: 860px;
      max-height: 70vh; display: flex; flex-direction: column;
      box-shadow: 0 -4px 24px rgba(0,0,0,.2);
    }
    .history-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 16px 18px 12px; border-bottom: 1px solid #f0ece4;
    }
    .history-title { font-size: 15px; font-weight: 800; color: #3d2b1f; }
    .history-close { font-size: 20px; color: #bbb; cursor: pointer; padding: 4px; }
    .history-close:hover { color: #555; }
    .history-body { overflow-y: auto; padding: 12px 18px 24px; flex: 1; }
    .history-empty { text-align: center; padding: 40px; color: #bbb; font-size: 14px; }
    .log-item { margin-bottom: 14px; }
    .log-time { font-size: 11px; color: #aaa; margin-bottom: 3px; }
    .log-msg  {
      background: #f7f3ee; border-left: 3px solid #c9a96e;
      border-radius: 0 10px 10px 0; padding: 8px 12px;
      font-size: 12px; color: #333; white-space: pre-wrap; word-break: break-all; line-height: 1.5;
    }

    /* ═══════════ HERO ═══════════ */
    .hero {
      display: flex; gap: 20px; align-items: flex-start;
      background: linear-gradient(135deg, #2a1f14 0%, #3d2b1f 60%, #1a1a2e 100%);
      border-radius: 20px; padding: 20px; margin-bottom: 14px;
      box-shadow: 0 4px 20px rgba(0,0,0,.25);
    }
    .photo-col { flex-shrink: 0; width: 220px; }
    .char-photo {
      width: 220px; height: 320px; border-radius: 16px; overflow: hidden;
      background: linear-gradient(160deg,#2c1f30 0%,#1a1535 55%,#0f1a2e 100%);
      border: 2px solid rgba(201,169,110,.5);
      box-shadow: 0 8px 32px rgba(0,0,0,.4), inset 0 0 0 1px rgba(255,255,255,.05);
      display: flex; align-items: center; justify-content: center;
    }
    .char-photo img { width: 100%; height: 100%; object-fit: cover; }
    .char-photo .placeholder { font-size: 80px; opacity: .5; }

    .stats-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; padding-top: 4px; }
    .char-name { font-size: 20px; font-weight: 900; color: #fff; letter-spacing: .5px; word-break: break-all; }
    .char-sub  { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 2px; }
    .char-lv   { font-size: 14px; font-weight: 700; color: #c9a96e; }
    .char-job  { font-size: 13px; color: rgba(255,255,255,.6); }

    /* 경험치 바 */
    .exp-wrap { display: flex; flex-direction: column; gap: 3px; margin-top: 4px; }
    .exp-bar-bg {
      height: 6px; border-radius: 4px;
      background: rgba(255,255,255,.12);
      overflow: hidden;
    }
    .exp-bar-fill {
      height: 100%; border-radius: 4px;
      background: linear-gradient(90deg, #c9a96e, #f0d080);
      transition: width .4s ease;
    }
    .exp-text { font-size: 10px; color: rgba(255,255,255,.35); text-align: right; }

    /* SP / GP 패널 */
    .sp-gp-panel {
      background: rgba(255,255,255,.06); border: 1px solid rgba(201,169,110,.25);
      border-radius: 12px; padding: 12px 14px;
      display: flex; flex-direction: column; gap: 9px;
    }
    .panel-row   { display: flex; justify-content: space-between; align-items: baseline; }
    .panel-label { font-size: 11px; color: rgba(255,255,255,.45); }
    .panel-val   { font-size: 15px; font-weight: 800; color: #f0d080; }
    .panel-sub   { font-size: 12px; color: rgba(240,208,128,.6); font-weight: 600; }
    .panel-divider { height: 1px; background: rgba(255,255,255,.08); }
    .gp-val      { font-size: 15px; font-weight: 800; color: #80e0c0; }
    .gp-sub      { font-size: 12px; color: rgba(128,224,192,.6); font-weight: 600; }

    /* 하단 칩 */
    .bottom-chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .info-chip { background: rgba(255,255,255,.07); border: 1px solid rgba(255,255,255,.12); border-radius: 8px; padding: 4px 10px; font-size: 11px; color: rgba(255,255,255,.5); }

    /* ═══════════ 섹션 카드 ═══════════ */
    .section-card { background: #fff; border-radius: 16px; padding: 14px 16px; margin-bottom: 12px; box-shadow: 0 2px 10px rgba(0,0,0,.06); }
    .section-title { font-size: 12px; font-weight: 700; color: #a09080; letter-spacing: .5px; margin-bottom: 10px; }

    /* 단일 슬롯 그리드 */
    .single-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 8px; }
    .equip-slot {
      border: 1.5px solid #e8ddd0; border-radius: 12px; padding: 10px 8px;
      display: flex; flex-direction: column; align-items: center; gap: 5px;
      min-height: 80px; transition: border-color .2s, box-shadow .2s;
    }
    .equip-slot.has-item { border-color: #c9a96e; cursor: pointer; }
    .equip-slot.has-item:active { box-shadow: 0 3px 12px rgba(180,120,60,.15); background: #fffbf5; }
    .slot-icon { font-size: 22px; }
    .slot-cat  { font-size: 9px; color: #c0b0a0; }
    .slot-name { font-size: 10px; color: #444; text-align: center; line-height: 1.3; word-break: keep-all; }
    .slot-empty-txt { font-size: 10px; color: #ccc; }

    /* 무기 카드 */
    .weapon-row { display: flex; gap: 8px; flex-wrap: wrap; }
    .weapon-card {
      flex: 1 1 calc(20% - 8px); min-width: 110px; max-width: 160px;
      border: 1.5px solid #e8ddd0; border-radius: 12px;
      padding: 10px 10px 8px;
      display: flex; flex-direction: column; gap: 5px;
      transition: border-color .2s, box-shadow .2s;
    }
    .weapon-card.has-item { border-color: #c9a96e; cursor: pointer; }
    .weapon-card.has-item:active { box-shadow: 0 3px 12px rgba(180,120,60,.15); background: #fffbf5; }
    .weapon-card.empty { border-style: dashed; border-color: #e0d8ce; }
    .wc-num  { font-size: 9px; color: #c0b0a0; }
    .wc-icon { font-size: 20px; }
    .wc-name { font-size: 10px; color: #444; line-height: 1.3; }
    .wc-empty { font-size: 10px; color: #ccc; text-align: center; padding: 8px 0; }

    /* 세트 효과 */
    .set-list { display: flex; flex-direction: column; gap: 8px; }
    .set-item {
      border: 1.5px solid #e0cff0; border-radius: 10px;
      padding: 8px 12px;
      background: linear-gradient(90deg,#fdf8ff,#f8f0ff);
    }
    .set-id    { font-size: 10px; color: #9070c0; font-weight: 700; margin-bottom: 2px; }
    .set-desc  { font-size: 12px; color: #444; font-weight: 600; }
    .set-val   { font-size: 11px; color: #7050b0; margin-top: 1px; }

    /* 모달 */
    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 100; display: flex; align-items: center; justify-content: center; }
    .modal-box { background: #fff; border: 2px solid #c9a96e; border-radius: 16px; padding: 20px 22px; min-width: 240px; max-width: 320px; width: 90vw; box-shadow: 0 8px 32px rgba(0,0,0,.2); position: relative; }
    .modal-close { position: absolute; top: 10px; right: 14px; font-size: 18px; color: #bbb; cursor: pointer; }
    .modal-close:hover { color: #555; }
    .modal-title { font-size: 15px; font-weight: 800; color: #c9a96e; margin-bottom: 12px; padding-right: 20px; }
    .modal-stat  { display: flex; justify-content: space-between; font-size: 12px; padding: 5px 0; border-bottom: 1px solid #f0ece4; }
    .modal-stat:last-child { border-bottom: none; }
    .modal-stat .lbl { color: #999; }
    .modal-stat .val { color: #5a9e6f; font-weight: 700; }

    /* 그룹 칩 */
    .group-chips { display: flex; gap: 8px; flex-wrap: wrap; }
    .g-chip { background: #fff; border: 1px solid #e8ddd0; border-radius: 10px; padding: 6px 14px; font-size: 12px; color: #888; box-shadow: 0 1px 4px rgba(0,0,0,.05); }
    .g-chip strong { color: #c9a96e; margin-left: 3px; }

    /* ─ 모바일 ─ */
    @media (max-width: 600px) {
      .hero { flex-direction: column; gap: 16px; padding: 16px; }
      .photo-col { width: 100%; }
      .char-photo { width: 100%; height: 280px; }
      .single-grid { grid-template-columns: repeat(2,1fr); }
      .char-name { font-size: 17px; }
      .panel-val { font-size: 14px; }
      .gp-val    { font-size: 14px; }
    }
    @media (max-width: 480px) {
      .weapon-row { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 4px; }
      .weapon-card { flex: 0 0 130px; }
    }
    @media (max-width: 360px) {
      .hero { padding: 12px; }
      .char-photo { height: 240px; }
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
      <button class="btn-attack" @click="doAttack" :disabled="attacking" v-if="userName">⚔ 공격</button>
      <button class="btn-history" @click="showHistory=true" v-if="userName">📜 이력</button>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>
  <div class="empty"   v-else-if="!userName">유저명을 입력해 조회하세요.</div>

  <template v-else>

    <!-- ══ HERO ══ -->
    <div class="hero">
      <div class="photo-col">
        <div class="char-photo">
          <img v-if="charImgUrl" :src="charImgUrl" alt="캐릭터">
          <span v-else class="placeholder">🧙</span>
        </div>
      </div>

      <div class="stats-col">
        <div>
          <div class="char-name">{{ userName }}</div>
          <div class="char-sub">
            <span class="char-lv" v-if="userLv > 0">Lv.{{ userLv }}</span>
            <span class="char-job" v-if="userJob">{{ userJob }}</span>
          </div>
          <!-- 경험치 프로그레스바 -->
          <div class="exp-wrap" v-if="expNext > 0">
            <div class="exp-bar-bg">
              <div class="exp-bar-fill" :style="{ width: expPct + '%' }"></div>
            </div>
            <div class="exp-text">EXP {{ fmtNum(expCur) }} / {{ fmtNum(expNext) }}</div>
          </div>
        </div>

        <!-- SP / GP 패널 -->
        <div class="sp-gp-panel">
          <div class="panel-row">
            <span class="panel-label">💎 현재 SP</span>
            <span class="panel-val">{{ currentSp }}</span>
          </div>
          <div class="panel-row">
            <span class="panel-label" style="padding-left:14px;">누적 SP</span>
            <span class="panel-sub">{{ lifetimeSp }}</span>
          </div>
          <div class="panel-divider"></div>
          <div class="panel-row">
            <span class="panel-label">🪙 현재 GP</span>
            <span class="gp-val">{{ gpBalance }}</span>
          </div>
          <div class="panel-row">
            <span class="panel-label" style="padding-left:14px;">누적 GP</span>
            <span class="gp-sub">{{ totalEarnedGp }}</span>
          </div>
        </div>

        <!-- 기타 칩 -->
        <div class="bottom-chips">
          <div class="info-chip">🧪 물약 {{ potionUseCount }}회</div>
          <div class="info-chip" v-for="g in groupSummary" :key="g.label">
            {{ g.label }} <strong style="color:#c9a96e;">{{ g.total }}</strong>
          </div>
        </div>
      </div>
    </div>

    <!-- ══ 장비 슬롯 (투구/갑옷/전설/날개) ══ -->
    <div class="section-card">
      <div class="section-title">🛡 장비 슬롯</div>
      <div class="single-grid">
        <div v-for="slot in singleSlots" :key="slot.cat"
             class="equip-slot" :class="{ 'has-item': slot.item }"
             @click="openModal(slot.item)">
          <div class="slot-icon">{{ catIcon(slot.cat) }}</div>
          <div class="slot-cat">{{ slot.cat }}</div>
          <div class="slot-name"       v-if="slot.item">{{ slot.item.ITEM_NAME }}</div>
          <div class="slot-empty-txt"  v-else>비어있음</div>
        </div>
      </div>
    </div>

    <!-- ══ 무기 (최대 5개) ══ -->
    <div class="section-card">
      <div class="section-title">⚔ 무기 ({{ weaponItems.length }}/5)</div>
      <div class="weapon-row">
        <div v-for="(w, idx) in weaponItems" :key="w.ITEM_ID"
             class="weapon-card has-item" @click="openModal(w)">
          <div class="wc-num">#{{ idx+1 }}</div>
          <div class="wc-icon">⚔️</div>
          <div class="wc-name">{{ w.ITEM_NAME }}</div>
        </div>
        <div v-for="n in emptyWeaponSlots" :key="'e'+n" class="weapon-card empty">
          <div class="wc-empty">빈 슬롯</div>
        </div>
      </div>
    </div>

    <!-- ══ 세트 효과 ══ -->
    <div class="section-card" v-if="setBonuses.length > 0">
      <div class="section-title">✨ 활성 세트 효과</div>
      <div class="set-list">
        <div v-for="s in setBonuses" :key="s.SET_ID + '_' + s.REQUIRED_CNT" class="set-item">
          <div class="set-id">{{ s.SET_ID }}  ({{ s.OWNED_CNT }}종 보유)</div>
          <div class="set-desc">{{ s.BONUS_DESC }}</div>
          <div class="set-val">{{ bonusTypeLabel(s.BONUS_TYPE) }} +{{ fmtNum(s.BONUS_VALUE) }}</div>
        </div>
      </div>
    </div>

  </template>

  <!-- ══ 모달 ══ -->
  <div class="modal-overlay" v-if="modal" @click.self="modal=null">
    <div class="modal-box">
      <span class="modal-close" @click="modal=null">✕</span>
      <div class="modal-title">{{ modal.ITEM_NAME }}</div>
      <div class="modal-stat" v-for="s in itemStats(modal)" :key="s.lbl">
        <span class="lbl">{{ s.lbl }}</span>
        <span class="val">{{ s.val }}</span>
      </div>
      <div class="modal-stat">
        <span class="lbl">보유 수량</span>
        <span class="val">{{ modal.TOTAL_QTY }}</span>
      </div>
    </div>
  </div>

  <!-- ══ 토스트 ══ -->
  <div class="toast" :class="{ hide: !toastVisible }" v-if="toastMsg">{{ toastMsg }}</div>

  <!-- ══ 이력 패널 ══ -->
  <div class="history-overlay" v-if="showHistory" @click.self="showHistory=false">
    <div class="history-panel">
      <div class="history-header">
        <div class="history-title">⚔ 공격 이력 ({{ attackLog.length }}건)</div>
        <div class="history-close" @click="showHistory=false">✕</div>
      </div>
      <div class="history-body">
        <div class="history-empty" v-if="attackLog.length===0">아직 공격 이력이 없습니다.</div>
        <div class="log-item" v-for="(log, i) in attackLog" :key="i">
          <div class="log-time">{{ log.time }}</div>
          <div class="log-msg">{{ log.msg }}</div>
        </div>
      </div>
    </div>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
var WEAPON_RANGES = [{ min:100,max:200 }, { min:1100,max:1200 }, { min:2100,max:2200 }];
var SINGLE_SLOTS  = ['투구','갑옷','전설','날개'];
var SINGLE_RANGES = {
  '투구': [{ min:200,max:300 }, { min:1200,max:1300 }],
  '갑옷': [{ min:400,max:500 }, { min:1400,max:1500 }],
  '전설': [{ min:700,max:800 }],
  '날개': [{ min:800,max:900 }]
};
var GROUP_DEFS = [
  { label:'행운', min:300,  max:400  },
  { label:'반지', min:500,  max:600  },
  { label:'토템', min:600,  max:700  },
  { label:'선물', min:900,  max:1000 },
  { label:'업적', min:8000, max:9000 },
  { label:'유물', min:9000, max:99999 }
];
var CAT_ICONS = { '투구':'🪖','갑옷':'🛡️','전설':'✨','날개':'🪽' };
var BONUS_TYPE_LABELS = {
  'ATK_MIN':'최소 공격력', 'ATK_MAX':'최대 공격력',
  'HP_MAX':'최대 체력', 'HP_REGEN':'체력 회복',
  'ATK_CRI':'치명타율', 'CRI_DMG':'치명타 피해',
  'ATK_MAX_RATE':'최종 공격력%', 'HP_MAX_RATE':'최종 체력%'
};

function inRanges(id, ranges) {
  for (var i=0; i<ranges.length; i++)
    if (id >= ranges[i].min && id < ranges[i].max) return true;
  return false;
}
var IMG_PREFIX = 'loaCharImg_';
function getCachedImg(u) { return localStorage.getItem(IMG_PREFIX+u) || null; }
function fetchImg(u, cb) {
  var c = getCachedImg(u); if (c) { cb(c); return; }
  fetch('https://nekos.best/api/v2/neko')
    .then(function(r){ return r.json(); })
    .then(function(d){
      var url = d.results && d.results[0] ? d.results[0].url : null;
      if (url) { localStorage.setItem(IMG_PREFIX+u, url); cb(url); }
    }).catch(function(){});
}

new Vue({
  el: '#app',
  data: {
    inputUser:'', userName:'', userLv:0, userJob:'',
    expCur:0, expNext:0,
    currentSp:'—', lifetimeSp:'—',
    gpBalance:'—', totalEarnedGp:'—',
    loading:false, inventory:[], potionUseCount:0,
    setBonuses:[], modal:null, charImgUrl:null,
    attacking:false, toastMsg:'', toastVisible:false, toastTimer:null,
    showHistory:false, attackLog:[], room:'웹'
  },
  computed: {
    expPct: function() {
      if (!this.expNext || this.expNext <= 0) return 0;
      return Math.min(100, Math.round(this.expCur / this.expNext * 100));
    },
    weaponItems: function() {
      return this.inventory.filter(function(it){ return inRanges(+it.ITEM_ID, WEAPON_RANGES); });
    },
    emptyWeaponSlots: function() { var n=5-this.weaponItems.length; return n>0?n:0; },
    singleSlots: function() {
      var inv=this.inventory;
      return SINGLE_SLOTS.map(function(cat){
        var ranges=SINGLE_RANGES[cat], found=null;
        inv.forEach(function(it){
          if (inRanges(+it.ITEM_ID,ranges) && (!found || +it.ITEM_ID > +found.ITEM_ID)) found=it;
        });
        return { cat:cat, item:found };
      });
    },
    groupSummary: function() {
      var inv=this.inventory;
      return GROUP_DEFS.map(function(g){
        var total=0;
        inv.forEach(function(it){ var id=+it.ITEM_ID; if(id>=g.min&&id<g.max) total+=(+it.TOTAL_QTY||0); });
        return { label:g.label, total:total };
      }).filter(function(g){ return g.total>0; });
    }
  },
  methods: {
    catIcon: function(cat){ return CAT_ICONS[cat]||'📦'; },
    openModal: function(item){ if(item) this.modal=item; },
    bonusTypeLabel: function(t){ return BONUS_TYPE_LABELS[t] || t; },
    fmtNum: function(n) {
      if (n === undefined || n === null) return '0';
      return Number(n).toLocaleString();
    },
    itemStats: function(item){
      var stats=[], add=function(lbl,key){ var v=+item[key]; if(v&&v!==0) stats.push({lbl:lbl,val:v}); };
      add('공격력 최소','ATK_MIN'); add('공격력 최대','ATK_MAX');
      add('치명타율','ATK_CRI');   add('치명타 피해','CRI_DMG');
      add('최대 체력','HP_MAX');   add('HP 비율','HP_MAX_RATE');
      add('ATK 비율','ATK_MAX_RATE'); add('체력 회복','HP_REGEN');
      return stats;
    },
    showToast: function(msg) {
      var self = this;
      this.toastMsg = msg;
      this.toastVisible = true;
      if (this.toastTimer) clearTimeout(this.toastTimer);
      this.toastTimer = setTimeout(function(){ self.toastVisible = false; }, 4000);
    },
    fmtTime: function() {
      var d = new Date();
      var pad = function(n){ return String(n).padStart(2,'0'); };
      return pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds());
    },
    doAttack: function() {
      if (!this.userName || this.attacking) return;
      var self = this;
      this.attacking = true;
      var sender = encodeURIComponent(this.userName);
      var room   = encodeURIComponent(this.room);
      var url = '<%=request.getContextPath()%>/loa/chat?param0=%2F%EC%9B%B9%EA%B3%B5%EA%B2%A9&sender='+sender+'&room='+room+'&fulltxt=%2F%EC%9B%B9%EA%B3%B5%EA%B2%A9';
      fetch(url)
        .then(function(r){ return r.json(); })
        .then(function(d){
          var msg = (d && d.data) ? d.data : '응답 없음';
          self.attackLog.unshift({ time: self.fmtTime(), msg: msg });
          self.showToast(msg);
          self.attacking = false;
        })
        .catch(function(){
          self.showToast('공격 요청 실패');
          self.attacking = false;
        });
    },
    fetchInfo: function(){
      var name=this.inputUser.trim(); if(!name) return;
      this.loading=true; this.inventory=[]; this.modal=null;
      this.currentSp='—'; this.lifetimeSp='—'; this.gpBalance='—'; this.totalEarnedGp='—';
      this.setBonuses=[]; this.expCur=0; this.expNext=0;
      var self=this;
      fetch('<%=request.getContextPath()%>/loa/api/user-info?userName='+encodeURIComponent(name))
        .then(function(r){ return r.json(); })
        .then(function(d){
          self.userName       = d.userName       || name;
          self.userLv         = d.lv             || 0;
          self.userJob        = d.job             || '';
          self.expCur         = d.expCur         || 0;
          self.expNext        = d.expNext        || 0;
          self.inventory      = d.inventory      || [];
          self.potionUseCount = d.potionUseCount || 0;
          self.currentSp      = d.currentSp      || '0';
          self.lifetimeSp     = d.lifetimeSp     || '0';
          self.gpBalance      = d.gpBalance      || '0';
          self.totalEarnedGp  = d.totalEarnedGp  || '0';
          self.setBonuses     = d.setBonuses     || [];
          self.loading=false;
          sessionStorage.setItem('loaUserName', self.userName);
          self.charImgUrl = getCachedImg(self.userName);
          if (!self.charImgUrl) fetchImg(self.userName, function(url){ self.charImgUrl=url; });
        })
        .catch(function(){ self.loading=false; });
    }
  },
  mounted: function(){
    var params=new URLSearchParams(window.location.search);
    var u=(params.get('userName')||params.get('user')||'').trim();
    var r=(params.get('room')||'').trim();
    if(r) this.room=r;
    if(u){ this.inputUser=u; this.fetchInfo(); }
  }
});
</script>
</body>
</html>
