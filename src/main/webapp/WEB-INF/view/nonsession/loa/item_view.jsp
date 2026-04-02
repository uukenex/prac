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
    body { background: #f5f3ee; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; }

    .shop-wrap { max-width: 960px; margin: 0 auto; padding: 28px 16px 60px; }

    /* 상단 */
    .shop-header { margin-bottom: 24px; }
    .shop-title { font-size: 22px; font-weight: 800; color: #3d2b1f; display: flex; align-items: center; gap: 8px; }
    .shop-subtitle { font-size: 13px; color: #999; margin-top: 4px; }

    /* 검색 + 유저 */
    .top-controls { display: flex; gap: 10px; margin-bottom: 18px; flex-wrap: wrap; }
    .search-wrap { flex: 1; min-width: 180px; position: relative; }
    .search-wrap input { width: 100%; padding: 10px 14px 10px 38px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; transition: border-color .2s; }
    .search-wrap input:focus { border-color: #c9a96e; }
    .search-wrap .ico-search { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: #bbb; font-size: 14px; }
    .user-wrap { display: flex; gap: 8px; align-items: center; }
    .user-wrap input { padding: 10px 14px; border: 1.5px solid #e0d9ce; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; width: 150px; transition: border-color .2s; }
    .user-wrap input:focus { border-color: #c9a96e; }
    .user-wrap input::placeholder { color: #bbb; }
    .btn-query { background: #c9a96e; color: #fff; border: none; padding: 10px 20px; border-radius: 24px; font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap; transition: background .2s; }
    .btn-query:hover { background: #b8935a; }

    /* 카테고리 탭 */
    .cat-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    .cat-tab { padding: 7px 18px; border-radius: 20px; border: 1.5px solid #e0d9ce; background: #fff; color: #888; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .2s; }
    .cat-tab:hover { border-color: #c9a96e; color: #c9a96e; }
    .cat-tab.active { background: #c9a96e; border-color: #c9a96e; color: #fff; }

    /* 보유 필터 + 카운트 */
    .bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
    .own-tabs { display: flex; gap: 6px; }
    .own-tab { padding: 4px 14px; border-radius: 16px; border: 1.5px solid #e0d9ce; background: #fff; color: #aaa; font-size: 12px; cursor: pointer; transition: all .15s; }
    .own-tab.active { background: #3d2b1f; border-color: #3d2b1f; color: #fff; }
    .count-label { font-size: 13px; color: #aaa; }
    .count-label strong { color: #3d2b1f; }

    /* 그리드 */
    .item-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 14px; }

    /* 카드 */
    .item-card { background: #fff; border-radius: 14px; padding: 16px 14px 14px; box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid transparent; transition: box-shadow .2s, border-color .2s; cursor: default; position: relative; }
    .item-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }
    .item-card.owned { border-color: #c9a96e; background: #fffdf7; }
    .card-badge { position: absolute; top: 10px; right: 10px; font-size: 10px; font-weight: 700; padding: 2px 7px; border-radius: 8px; }
    .badge-owned { background: #fff0d0; color: #c9a96e; }
    .badge-no    { background: #f0f0f0; color: #bbb; }
    .card-icon { font-size: 28px; margin-bottom: 8px; line-height: 1; }
    .card-name { font-size: 14px; font-weight: 700; color: #3d2b1f; margin-bottom: 6px; line-height: 1.3; }
    .card-id { font-size: 11px; color: #ccc; margin-bottom: 6px; font-family: monospace; }
    .card-price { font-size: 14px; font-weight: 700; color: #c9a96e; margin-bottom: 4px; }
    .card-price.no-price { color: #ddd; }
    .card-type-tag { display: inline-block; font-size: 10px; color: #aaa; background: #f5f3ee; border-radius: 6px; padding: 2px 7px; }
    .card-stats { margin-top: 8px; font-size: 11px; color: #888; border-top: 1px solid #f0ece4; padding-top: 7px; }
    .stat-line { display: flex; justify-content: space-between; padding: 1px 0; }
    .stat-line span:last-child { color: #5a9e6f; font-weight: 600; }
    .card-qty { font-size: 11px; color: #81b0d4; margin-top: 5px; }

    /* 빈 화면 */
    .empty { text-align: center; padding: 60px 0; color: #ccc; }
    .empty .ico { font-size: 36px; margin-bottom: 10px; }
    .empty p { font-size: 14px; }

    /* 로딩 */
    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }
  </style>
</head>
<body>
<div id="app" class="shop-wrap">

  <!-- 헤더 -->
  <div class="shop-header">
    <div class="shop-title">🛒 람쥐봇 아이템샵</div>
    <div class="shop-subtitle">총 {{ totalCount }}개 아이템<span v-if="searchedUser"> &nbsp;·&nbsp; {{ searchedUser }} 보유 현황</span></div>
  </div>

  <!-- 검색 + 유저 -->
  <div class="top-controls">
    <div class="search-wrap">
      <i class="fa fa-search ico-search"></i>
      <input v-model="keyword" placeholder="아이템 검색...">
    </div>
    <div class="user-wrap">
      <input v-model="userName" placeholder="유저명 (선택)" @keyup.enter="fetchItems">
      <button class="btn-query" @click="fetchItems">조회</button>
    </div>
  </div>

  <!-- 카테고리 탭 -->
  <div class="cat-tabs">
    <button class="cat-tab" :class="{active: activeType===''}" @click="activeType=''">전체</button>
    <button class="cat-tab" v-for="t in typeList" :key="t.code"
            :class="{active: activeType===t.code}" @click="activeType=t.code">
      {{ t.label }}
    </button>
  </div>

  <!-- 보유 필터 + 카운트 (유저 조회 후에만) -->
  <div class="bar">
    <div class="own-tabs" v-if="searchedUser">
      <button class="own-tab" :class="{active: ownFilter==='all'}"     @click="ownFilter='all'">전체</button>
      <button class="own-tab" :class="{active: ownFilter==='owned'}"   @click="ownFilter='owned'">보유</button>
      <button class="own-tab" :class="{active: ownFilter==='notOwned'}" @click="ownFilter='notOwned'">미보유</button>
    </div>
    <div v-else></div>
    <div class="count-label">현재 표시 <strong>{{ filteredItems.length }}</strong> 개</div>
  </div>

  <!-- 로딩 -->
  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

  <!-- 아이템 그리드 -->
  <div class="item-grid" v-else-if="filteredItems.length > 0">
    <div class="item-card" v-for="item in filteredItems" :key="item.ITEM_ID"
         :class="{owned: item.OWNED_YN === 'Y'}">
      <span class="card-badge badge-owned" v-if="searchedUser && item.OWNED_YN === 'Y'">보유</span>
      <span class="card-badge badge-no"    v-else-if="searchedUser">미보유</span>
      <div class="card-icon">{{ typeIcon(item.ITEM_TYPE) }}</div>
      <div class="card-id">#{{ item.ITEM_ID }}</div>
      <div class="card-name">{{ item.ITEM_NAME }}</div>
      <div class="card-price" :class="{'no-price': item.ITEM_SELL_PRICE <= 0}">
        {{ item.ITEM_SELL_PRICE > 0 ? item.ITEM_SELL_PRICE.toLocaleString() + ' SP' : '—' }}
        <span v-if="item.ITEM_SELL_PRICE_EXT" style="font-size:11px;color:#bbb"> / {{ item.ITEM_SELL_PRICE_EXT }}</span>
      </div>
      <span class="card-type-tag">{{ typeName(item.ITEM_TYPE) }}</span>
      <div class="card-stats" v-if="hasStats(item)">
        <div class="stat-line" v-if="item.ATK_MIN > 0 || item.ATK_MAX > 0"><span>공격력</span><span>{{ item.ATK_MIN }}~{{ item.ATK_MAX }}</span></div>
        <div class="stat-line" v-if="item.ATK_CRI > 0"><span>치명타율</span><span>+{{ item.ATK_CRI }}</span></div>
        <div class="stat-line" v-if="item.CRI_DMG > 0"><span>치명타피해</span><span>+{{ item.CRI_DMG }}</span></div>
        <div class="stat-line" v-if="item.HP_MAX > 0"><span>HP최대</span><span>+{{ item.HP_MAX }}</span></div>
        <div class="stat-line" v-if="item.HP_MAX_RATE > 0"><span>HP최대%</span><span>+{{ item.HP_MAX_RATE }}%</span></div>
        <div class="stat-line" v-if="item.HP_REGEN > 0"><span>HP회복</span><span>+{{ item.HP_REGEN }}</span></div>
        <div class="stat-line" v-if="item.ATK_MAX_RATE > 0"><span>공격력%</span><span>+{{ item.ATK_MAX_RATE }}%</span></div>
      </div>
      <div class="card-qty" v-if="item.OWN_QTY > 0">📦 보유 {{ item.OWN_QTY }}개</div>
    </div>
  </div>

  <!-- 빈 상태 -->
  <div class="empty" v-else-if="!loading">
    <div class="ico">🔍</div>
    <p>조건에 맞는 아이템이 없어요</p>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
<script>
  const TYPE_MAP = {
    'MARKET':  { label: '장비',   icon: '⚔️'  },
    'MARKET2': { label: '특수장비', icon: '✨'  },
    'POTION':  { label: '소모품',  icon: '🧪'  },
    'BOX':     { label: '상자',    icon: '📦'  },
    'SPECIAL': { label: '특수',    icon: '🌟'  },
  };

  new Vue({
    el: '#app',
    data: {
      userName: '',
      keyword: '',
      items: [],
      loading: false,
      searchedUser: '',
      activeType: '',
      ownFilter: 'all'
    },
    computed: {
      typeList() {
        const seen = new Set();
        const list = [];
        this.items.forEach(i => {
          if (i.ITEM_TYPE && !seen.has(i.ITEM_TYPE)) {
            seen.add(i.ITEM_TYPE);
            list.push({ code: i.ITEM_TYPE, label: this.typeName(i.ITEM_TYPE) });
          }
        });
        return list;
      },
      totalCount() { return this.items.length; },
      filteredItems() {
        return this.items.filter(item => {
          const typeOk = !this.activeType || item.ITEM_TYPE === this.activeType;
          const kwOk   = !this.keyword   || item.ITEM_NAME.includes(this.keyword);
          const ownOk  = this.ownFilter === 'all'
                      || (this.ownFilter === 'owned'    && item.OWNED_YN === 'Y')
                      || (this.ownFilter === 'notOwned' && item.OWNED_YN !== 'Y');
          return typeOk && kwOk && ownOk;
        });
      }
    },
    methods: {
      typeName(type) { return (TYPE_MAP[type] || {}).label || type || '기타'; },
      typeIcon(type) { return (TYPE_MAP[type] || {}).icon  || '🎁'; },
      hasStats(item) {
        return item.ATK_MIN > 0 || item.ATK_MAX > 0 || item.ATK_CRI > 0
            || item.CRI_DMG > 0 || item.HP_MAX > 0  || item.HP_MAX_RATE > 0
            || item.HP_REGEN > 0 || item.ATK_MAX_RATE > 0;
      },
      fetchItems() {
        this.loading = true;
        this.ownFilter = 'all';
        const url = '<%=request.getContextPath()%>/loa/api/items'
          + '?userName=' + encodeURIComponent(this.userName.trim());
        fetch(url)
          .then(r => r.json())
          .then(data => {
            this.items = data.items || [];
            this.searchedUser = data.userName.trim() || '';
          })
          .catch(() => alert('조회 중 오류가 발생했습니다.'))
          .finally(() => { this.loading = false; });
      }
    },
    mounted() {
      this.fetchItems();
    }
  });
</script>
</body>
</html>
