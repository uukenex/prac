<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 아이템샵</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f5f3ee; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; margin-left: 120px; }

    .shop-wrap { max-width: 1000px; margin: 0 auto; padding: 28px 16px 60px; }

    .shop-header { margin-bottom: 22px; }
    .shop-title { font-size: 22px; font-weight: 800; color: #3d2b1f; display: flex; align-items: center; gap: 8px; }
    .shop-subtitle { font-size: 13px; color: #999; margin-top: 4px; }

    /* 검색 + 유저 */
    .top-controls { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
    .search-wrap { flex: 1; min-width: 160px; position: relative; }
    .search-wrap input { width: 100%; padding: 10px 14px 10px 36px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; transition: border-color .2s; }
    .search-wrap input:focus { border-color: #c9a96e; }
    .search-wrap .fa { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: #bbb; font-size: 13px; }
    .user-wrap { display: flex; gap: 8px; align-items: center; }
    .user-wrap input { padding: 10px 14px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; width: 145px; }
    .user-wrap input:focus { border-color: #c9a96e; }
    .user-wrap input::placeholder { color: #bbb; }
    .btn-query { background: #c9a96e; color: #fff; border: none; padding: 10px 20px; border-radius: 24px; font-size: 13px; font-weight: 700; cursor: pointer; }
    .btn-query:hover { background: #b8935a; }

    /* 카테고리 탭 */
    .cat-tabs { display: flex; gap: 7px; flex-wrap: wrap; margin-bottom: 16px; }
    .cat-tab { padding: 7px 16px; border-radius: 20px; border: 1.5px solid #e0d9ce; background: #fff; color: #888; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .18s; white-space: nowrap; }
    .cat-tab:hover { border-color: #c9a96e; color: #c9a96e; }
    .cat-tab.active { background: #c9a96e; border-color: #c9a96e; color: #fff; }

    /* 상태바 */
    .bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
    .own-tabs { display: flex; gap: 6px; }
    .own-tab { padding: 4px 13px; border-radius: 16px; border: 1.5px solid #e0d9ce; background: #fff; color: #aaa; font-size: 12px; cursor: pointer; transition: all .15s; }
    .own-tab.active { background: #3d2b1f; border-color: #3d2b1f; color: #fff; }
    .count-label { font-size: 13px; color: #aaa; }
    .count-label strong { color: #3d2b1f; }

    /* 그리드 */
    .item-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(185px, 1fr)); gap: 5px; }

    /* 카드 */
    .item-card { background: #fff; border-radius: 14px; padding: 15px 13px 13px; box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid transparent; transition: box-shadow .18s, border-color .18s; position: relative; }
    .item-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.10); }
    .item-card.owned { border-color: #c9a96e; background: #fffdf7; }
    .card-badge { position: absolute; top: 10px; right: 10px; font-size: 10px; font-weight: 700; padding: 2px 7px; border-radius: 8px; }
    .badge-owned { background: #fff0d0; color: #c9a96e; }
    .badge-no    { background: #f2f2f2; color: #bbb; }
    .card-icon  { font-size: 26px; margin-bottom: 6px; line-height: 1; }
    .card-id    { font-size: 10px; color: #ccc; font-family: monospace; margin-bottom: 3px; }
    .card-name  { font-size: 13px; font-weight: 700; color: #3d2b1f; margin-bottom: 6px; line-height: 1.35; }
    .card-price { font-size: 14px; font-weight: 700; color: #c9a96e; margin-bottom: 4px; }
    .card-price.no-price { color: #ddd; font-weight: 400; }
    .card-type-tag { display: inline-block; font-size: 10px; color: #aaa; background: #f5f3ee; border-radius: 6px; padding: 2px 7px; }
    .card-stats { margin-top: 8px; font-size: 11px; color: #888; border-top: 1px solid #f0ece4; padding-top: 7px; }
    .stat-line  { display: flex; justify-content: space-between; padding: 1px 0; }
    .stat-line span:last-child { color: #5a9e6f; font-weight: 600; }
    .card-qty     { font-size: 11px; color: #81b0d4; margin-top: 5px; }
    .card-formula { font-size: 11px; color: #9c7c3a; background: #fffaee; border-radius: 6px; padding: 4px 8px; margin-top: 7px; line-height: 1.5; }
    .formula-lbl  { color: #bbb; font-size: 10px; display: block; margin-bottom: 1px; }
    .card-lv      { display: inline-block; font-size: 10px; color: #e07a5f; background: #fff3f0; border-radius: 6px; padding: 2px 7px; margin-top: 5px; }
    .card-desc    { font-size: 11px; color: #7a6652; background: #fdf8f2; border-radius: 6px; padding: 5px 8px; margin-top: 7px; line-height: 1.55; border-left: 3px solid #e8c98a; }
    .btn-sort     { padding: 4px 13px; border-radius: 16px; border: 1.5px solid #e0d9ce; background: #fff; color: #888; font-size: 12px; cursor: pointer; transition: all .15s; display: flex; align-items: center; gap: 4px; }
    .btn-sort:hover { border-color: #c9a96e; color: #c9a96e; }

    /* blur: 아무도 소유하지 않은 보스/업적/유물 아이템 */
    .card-blurred .card-name,
    .card-blurred .card-stats,
    .card-blurred .card-price,
    .card-blurred .card-lv     { filter: blur(4px); user-select: none; }
    .card-blurred .blur-hint   { display: block; }
    .blur-hint { display: none; font-size: 10px; color: #bbb; margin-top: 6px; text-align: center; }

    .empty   { text-align: center; padding: 60px 0; color: #ccc; }
    .empty .ico { font-size: 34px; margin-bottom: 10px; }
    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }

    /* ── 초소형 화면 (갤럭시 폴드 접힘 등) ── */
    @media (max-width: 360px) {
      .shop-wrap { padding: 12px 8px 50px; }
      .shop-title { font-size: 17px; }
      .top-controls { gap: 6px; }
      .search-wrap { min-width: 0; }
      .user-wrap input { width: 100px; font-size: 12px; padding: 8px 10px; }
      .btn-query { padding: 8px 12px; font-size: 12px; }
      .cat-tab { padding: 5px 10px; font-size: 11px; }
      .item-grid { grid-template-columns: 1fr 1fr; gap: 6px; }
      .item-card { padding: 10px 9px 10px; }
      .card-icon  { font-size: 20px; }
      .card-name  { font-size: 12px; }
      .card-price { font-size: 12px; }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="shop-wrap">

  <div class="shop-header">
    <div class="shop-title">🛒 람쥐봇 아이템샵</div>
    <div class="shop-subtitle">
      총 {{ totalCount }}개 아이템
      <span v-if="searchedUser"> &nbsp;·&nbsp; {{ searchedUser }} 보유 현황</span>
    </div>
  </div>

  <div class="top-controls">
    <div class="search-wrap">
      <i class="fa fa-search"></i>
      <input v-model="keyword" placeholder="아이템 검색...">
    </div>
    <div class="user-wrap">
      <input v-model="userName" placeholder="유저명 (선택)" @keyup.enter="fetchItems">
      <button class="btn-query" @click="fetchItems">조회</button>
    </div>
  </div>

  <!-- 카테고리 탭 -->
  <div class="cat-tabs">
    <button class="cat-tab" :class="{active: activeTab===''}" @click="activeTab=''">전체</button>
    <button class="cat-tab" v-for="cat in availableCategories" :key="cat.label"
            :class="{active: activeTab===cat.label}" @click="activeTab=cat.label">
      {{ cat.icon }} {{ cat.label }}
    </button>
  </div>

  <div class="bar">
    <div class="own-tabs" v-if="searchedUser">
      <button class="own-tab" :class="{active: ownFilter==='all'}"     @click="ownFilter='all'">전체</button>
      <button class="own-tab" :class="{active: ownFilter==='owned'}"   @click="ownFilter='owned'">보유</button>
      <button class="own-tab" :class="{active: ownFilter==='notOwned'}" @click="ownFilter='notOwned'">미보유</button>
    </div>
    <div v-else></div>
    <div style="display:flex;align-items:center;gap:10px;">
      <button class="btn-sort" @click="toggleSort">
        <span>번호순</span>
        <span>{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
      </button>
      <div class="count-label">현재 표시 <strong>{{ filteredItems.length }}</strong> 개</div>
    </div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

  <div class="item-grid" v-else-if="filteredItems.length > 0">
    <div class="item-card" v-for="item in filteredItems" :key="item.ITEM_ID"
         :class="{owned: item.OWNED_YN === 'Y', 'card-blurred': isBlurred(item)}">
      <span class="card-badge badge-owned" v-if="searchedUser && item.OWNED_YN === 'Y'">보유</span>
      <span class="card-badge badge-no"    v-else-if="searchedUser">미보유</span>
      <div class="card-icon">{{ item._cat.icon }}</div>
      <div class="card-id">#{{ item.ITEM_ID }}</div>
      <div class="card-name">{{ isBlurred(item) ? '???' : item.ITEM_NAME }}</div>
      <!-- 포션: 공식 표시 / 그 외: 고정가 표시 -->
      <template v-if="potionFormulas[String(item.ITEM_ID)]">
        <div class="card-formula">
          <span class="formula-lbl">💰 가격 공식</span>
          {{ potionFormulas[String(item.ITEM_ID)].formulaDesc }}
        </div>
      </template>
      <template v-else>
        <div class="card-price" :class="{'no-price': item.ITEM_SELL_PRICE <= 0}">
          {{ item.ITEM_SELL_PRICE > 0 ? item.ITEM_SELL_PRICE.toLocaleString() + (item.ITEM_SELL_PRICE_EXT ? ' ' + item.ITEM_SELL_PRICE_EXT : '') + ' SP' : '—' }}
        </div>
      </template>
      <span class="card-type-tag">{{ item._cat.label }}</span>
      <div class="card-stats" v-if="hasStats(item)">
        <div class="stat-line" v-if="item.ATK_MIN > 0 || item.ATK_MAX > 0"><span>공격력</span><span>{{ item.ATK_MIN }}~{{ item.ATK_MAX }}</span></div>
        <div class="stat-line" v-if="item.ATK_CRI > 0"><span>치명타율</span><span>+{{ item.ATK_CRI }}</span></div>
        <div class="stat-line" v-if="item.CRI_DMG > 0"><span>치명타피해</span><span>+{{ item.CRI_DMG }}%</span></div>
        <div class="stat-line" v-if="item.HP_MAX > 0"><span>HP최대</span><span>+{{ item.HP_MAX }}</span></div>
        <div class="stat-line" v-if="item.HP_MAX_RATE > 0"><span>HP최대%</span><span>+{{ item.HP_MAX_RATE }}%</span></div>
        <div class="stat-line" v-if="item.HP_REGEN > 0"><span>HP회복</span><span>+{{ item.HP_REGEN }}</span></div>
        <div class="stat-line" v-if="item.ATK_MAX_RATE > 0"><span>공격력%</span><span>+{{ item.ATK_MAX_RATE }}%</span></div>
      </div>
      <div class="card-lv" v-if="item.TARGET_LV > 0">🔒 Lv.{{ item.TARGET_LV }} 이상</div>
      <div class="card-desc" v-if="isBossItem(item) && !isBlurred(item) && item.ITEM_DESC" v-html="formatDesc(item.ITEM_DESC)"></div>
      <div class="card-qty" v-if="item.OWN_QTY > 0">📦 보유 {{ item.OWN_QTY }}개</div>
      <span class="blur-hint">🔒 미발견 아이템</span>
    </div>
  </div>

  <div class="empty" v-else-if="!loading">
    <div class="ico">🔍</div>
    <p>조건에 맞는 아이템이 없어요</p>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
  // ID 범위 → 카테고리 매핑 (order 순서대로 탭 표시)
  const CAT_RULES = [
    { label: '무기',    icon: '⚔️',  order:  1, test: function(id)       { var b=id%1000; return b>=100&&b<200; } },
    { label: '투구',    icon: '🪖',  order:  2, test: function(id)       { var b=id%1000; return b>=200&&b<300; } },
    { label: '행운',    icon: '🍀',  order:  3, test: function(id)       { var b=id%1000; return b>=300&&b<400; } },
    { label: '갑옷',    icon: '🥋',  order:  4, test: function(id)       { var b=id%1000; return b>=400&&b<500; } },
    { label: '악세사리', icon: '💎', order:  5, test: function(id)       { var b=id%1000; return b>=500&&b<600; } },
    { label: '토템',    icon: '🗿',  order:  6, test: function(id)       { var b=id%1000; return b>=600&&b<700; } },
    { label: '전설',    icon: '🌟',  order:  7, test: function(id)       { var b=id%1000; return b>=700&&b<800; } },
    { label: '날개',    icon: '🪽',  order:  8, test: function(id)       { var b=id%1000; return b>=800&&b<900; } },
    { label: '선물',    icon: '🎁',  order:  9, test: function(id)       { var b=id%1000; return b>=900&&b<1000; } },
    { label: '소모품',  icon: '🧪',  order: 10, test: function(id, type) { return type==='POTION'; } },
    { label: '보스',    icon: '👹',  order: 11, test: function(id)       { return id>=7000&&id<8000; } },
    { label: '업적',    icon: '🏆',  order: 12, test: function(id)       { return id>=8000&&id<9000; } },
    { label: '유물',    icon: '🏺',  order: 13, test: function(id)       { return id>=9000; } }
  ];

  function getCategory(itemId, itemType) {
    var id = parseInt(itemId);
    for (var i = 0; i < CAT_RULES.length; i++) {
      if (CAT_RULES[i].test(id, itemType)) return CAT_RULES[i];
    }
    return CAT_RULES[CAT_RULES.length - 1];
  }

  new Vue({
    el: '#app',
    data: {
      userName: '',
      keyword: '',
      items: [],
      potionFormulas: {},   // { "1001": { priceType, formulaDesc }, ... }
      loading: false,
      searchedUser: '',
      activeTab: '',
      ownFilter: 'all',
      sortDir: 'asc'
    },
    computed: {
      totalCount: function() { return this.items.length; },
      availableCategories: function() {
        var seen = {};
        var list = [];
        this.items.forEach(function(item) {
          var label = item._cat.label;
          if (!seen[label]) {
            seen[label] = true;
            list.push(item._cat);
          }
        });
        list.sort(function(a, b) { return a.order - b.order; });
        return list;
      },
      filteredItems: function() {
        var self = this;
        var filtered = this.items.filter(function(item) {
          var tabOk = !self.activeTab || item._cat.label === self.activeTab;
          var kwOk  = !self.keyword   || item.ITEM_NAME.indexOf(self.keyword) !== -1;
          var ownOk = self.ownFilter === 'all'
                   || (self.ownFilter === 'owned'    && item.OWNED_YN === 'Y')
                   || (self.ownFilter === 'notOwned' && item.OWNED_YN !== 'Y');
          return tabOk && kwOk && ownOk;
        });
        var dir = self.sortDir === 'asc' ? 1 : -1;
        return filtered.slice().sort(function(a, b) {
          if (a._cat.order !== b._cat.order) return a._cat.order - b._cat.order;
          return dir * (parseInt(a.ITEM_ID) - parseInt(b.ITEM_ID));
        });
      }
    },
    methods: {
      toggleSort: function() {
        this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
      },
      formatDesc: function(desc) {
        if (!desc) return '';
        return desc.split('♬').join('<br>');
      },
      isBossItem: function(item) {
        var id = parseInt(item.ITEM_ID);
        return id >= 7000 && id < 8000;
      },
      isBlurred: function(item) {
        var id = parseInt(item.ITEM_ID);
        if (id < 7000) return false;
        // 한 번이라도 획득 이력이 있으면 blur 해제 (판매 후에도 유지)
        return !item.EVER_OBTAINED_CNT || parseInt(item.EVER_OBTAINED_CNT) === 0;
      },
      hasStats: function(item) {
        return item.ATK_MIN>0||item.ATK_MAX>0||item.ATK_CRI>0
            || item.CRI_DMG>0||item.HP_MAX>0||item.HP_MAX_RATE>0
            || item.HP_REGEN>0||item.ATK_MAX_RATE>0;
      },
      fetchItems: function() {
        var self = this;
        self.loading = true;
        self.ownFilter = 'all';
        var url = '<%=request.getContextPath()%>/loa/api/items'
                + '?userName=' + encodeURIComponent(self.userName.trim());
        fetch(url)
          .then(function(r) { return r.json(); })
          .then(function(data) {
            var list = (data.items || []);
            // 카테고리 주입 + 정렬 (카테고리 order → ITEM_ID)
            list.forEach(function(item) {
              item._cat = getCategory(item.ITEM_ID, item.ITEM_TYPE);
            });
            list.sort(function(a, b) {
              if (a._cat.order !== b._cat.order) return a._cat.order - b._cat.order;
              return parseInt(a.ITEM_ID) - parseInt(b.ITEM_ID);
            });
            self.items = list;
            self.searchedUser = (data.userName || '').trim();
            if (self.searchedUser) sessionStorage.setItem('loaUserName', self.searchedUser);
          })
          .catch(function() { alert('조회 중 오류가 발생했습니다.'); })
          .finally(function() { self.loading = false; });
      }
    },
    mounted: function() {
      var self = this;
      var params = new URLSearchParams(window.location.search);
      var u = (params.get('userName') || params.get('user') || '').trim();
      if (u) self.userName = u;
      fetch('<%=request.getContextPath()%>/loa/api/potion-formulas')
        .then(function(r) { return r.json(); })
        .then(function(data) { self.potionFormulas = data; });
      self.fetchItems();
    }
  });
</script>
</body>
</html>
