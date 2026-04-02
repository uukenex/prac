<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>아이템 보유 현황</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/main.css?v=<%=System.currentTimeMillis()%>">
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    .item-page { max-width: 1200px; margin: 30px auto; padding: 0 15px; }
    .search-box { background: #2a2a2a; border-radius: 8px; padding: 18px 20px; margin-bottom: 24px; display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; }
    .search-box label { color: #ccc; font-size: 13px; display: block; margin-bottom: 4px; }
    .search-box input { background: #1a1a1a; border: 1px solid #444; color: #eee; padding: 8px 12px; border-radius: 4px; font-size: 14px; width: 200px; }
    .search-box input:focus { outline: none; border-color: #7ec8e3; }
    .btn-search { background: #4a90d9; color: #fff; border: none; padding: 9px 22px; border-radius: 4px; cursor: pointer; font-size: 14px; }
    .btn-search:hover { background: #357abd; }
    .owned-filter { display: flex; gap: 8px; align-items: center; margin-left: auto; }
    .owned-filter span { color: #999; font-size: 13px; }
    .btn-filter { background: #333; color: #ccc; border: 1px solid #555; padding: 5px 14px; border-radius: 20px; cursor: pointer; font-size: 12px; transition: all .2s; }
    .btn-filter.active, .btn-filter:hover { background: #4a90d9; color: #fff; border-color: #4a90d9; }
    .category-section { margin-bottom: 32px; }
    .category-header { display: flex; align-items: center; gap: 12px; padding: 10px 0; margin-bottom: 14px; border-bottom: 2px solid #333; }
    .category-title { font-size: 16px; font-weight: bold; color: #7ec8e3; }
    .category-count { font-size: 12px; color: #888; background: #2a2a2a; padding: 2px 10px; border-radius: 10px; }
    .category-owned { font-size: 12px; color: #4caf50; }
    .item-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
    .item-card { background: #1e1e1e; border: 1px solid #333; border-radius: 8px; padding: 12px; transition: border-color .15s; }
    .item-card:hover { border-color: #555; }
    .item-card.owned { border-color: #4caf50; background: #1a2a1a; }
    .card-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }
    .item-id { font-size: 11px; color: #666; font-family: monospace; }
    .owned-badge { font-size: 11px; background: #4caf50; color: #fff; padding: 1px 6px; border-radius: 10px; }
    .not-owned-badge { font-size: 11px; background: #3a3a3a; color: #777; padding: 1px 6px; border-radius: 10px; }
    .item-name { font-size: 13px; font-weight: bold; color: #ddd; margin-bottom: 8px; line-height: 1.3; }
    .item-stats { font-size: 12px; }
    .stat-row { display: flex; justify-content: space-between; padding: 2px 0; border-bottom: 1px solid #252525; }
    .stat-row:last-child { border-bottom: none; }
    .stat-label { color: #888; }
    .stat-val { color: #a5d6a7; font-weight: bold; }
    .item-price { font-size: 11px; color: #ffd700; margin-top: 8px; }
    .qty-badge { font-size: 11px; color: #81d4fa; margin-top: 3px; }
    .no-result { text-align: center; color: #666; padding: 60px; font-size: 15px; }
    .loading { text-align: center; color: #888; padding: 60px; font-size: 15px; }
    h2.page-title { color: #ddd; margin-bottom: 20px; font-size: 20px; }
    h2.page-title span { color: #7ec8e3; }
  </style>
</head>
<body>
  <jsp:include page="../layout/dropMenu_header.jsp" />
  <jsp:include page="../layout/menubar_header.jsp" />

  <div id="page-wrapper" class="boardPage-Wrapper">
    <div id="main">
      <div class="container">
        <div id="app" class="item-page">

          <h2 class="page-title">아이템 보유 현황<span v-if="searchedUser"> — {{ searchedUser }}</span></h2>

          <!-- 검색 -->
          <div class="search-box">
            <div>
              <label>유저명</label>
              <input v-model="userName" placeholder="유저명 입력" @keyup.enter="fetchItems">
            </div>
            <button class="btn-search" @click="fetchItems">
              <i class="fa fa-search"></i> 조회
            </button>
            <div class="owned-filter" v-if="items.length > 0">
              <span>보유:</span>
              <button class="btn-filter" :class="{active: ownedFilter==='all'}"     @click="ownedFilter='all'">전체</button>
              <button class="btn-filter" :class="{active: ownedFilter==='owned'}"   @click="ownedFilter='owned'">보유만</button>
              <button class="btn-filter" :class="{active: ownedFilter==='notOwned'}" @click="ownedFilter='notOwned'">미보유만</button>
            </div>
          </div>

          <!-- 로딩 -->
          <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

          <!-- 결과 없음 -->
          <div class="no-result" v-else-if="!loading && searched && items.length === 0">조회된 아이템이 없습니다.</div>

          <!-- 카테고리별 섹션 -->
          <template v-else>
            <div class="category-section" v-for="group in groupedItems" :key="group.type" v-if="group.items.length > 0">
              <div class="category-header">
                <span class="category-title">{{ group.type }}</span>
                <span class="category-count">{{ group.items.length }}개</span>
                <span class="category-owned" v-if="group.ownedCount > 0">보유 {{ group.ownedCount }}개</span>
              </div>
              <div class="item-grid">
                <div class="item-card" v-for="item in group.items" :key="item.ITEM_ID"
                     :class="{owned: item.OWNED_YN === 'Y'}">
                  <div class="card-top">
                    <span class="item-id">#{{ item.ITEM_ID }}</span>
                    <span class="owned-badge"     v-if="item.OWNED_YN === 'Y'">보유</span>
                    <span class="not-owned-badge" v-else>미보유</span>
                  </div>
                  <div class="item-name">{{ item.ITEM_NAME }}</div>
                  <div class="item-stats">
                    <div class="stat-row" v-if="item.ATK_MIN > 0 || item.ATK_MAX > 0">
                      <span class="stat-label">공격력</span>
                      <span class="stat-val">{{ item.ATK_MIN }}~{{ item.ATK_MAX }}</span>
                    </div>
                    <div class="stat-row" v-if="item.ATK_CRI > 0">
                      <span class="stat-label">치명타율</span>
                      <span class="stat-val">+{{ item.ATK_CRI }}</span>
                    </div>
                    <div class="stat-row" v-if="item.CRI_DMG > 0">
                      <span class="stat-label">치명타 피해</span>
                      <span class="stat-val">+{{ item.CRI_DMG }}</span>
                    </div>
                    <div class="stat-row" v-if="item.HP_MAX > 0">
                      <span class="stat-label">HP 최대</span>
                      <span class="stat-val">+{{ item.HP_MAX }}</span>
                    </div>
                    <div class="stat-row" v-if="item.HP_MAX_RATE > 0">
                      <span class="stat-label">HP 최대%</span>
                      <span class="stat-val">+{{ item.HP_MAX_RATE }}%</span>
                    </div>
                    <div class="stat-row" v-if="item.HP_REGEN > 0">
                      <span class="stat-label">HP 회복</span>
                      <span class="stat-val">+{{ item.HP_REGEN }}</span>
                    </div>
                    <div class="stat-row" v-if="item.ATK_MAX_RATE > 0">
                      <span class="stat-label">공격력%</span>
                      <span class="stat-val">+{{ item.ATK_MAX_RATE }}%</span>
                    </div>
                  </div>
                  <div class="item-price" v-if="item.ITEM_SELL_PRICE > 0">
                    {{ item.ITEM_SELL_PRICE.toLocaleString() }} SP
                    <span v-if="item.ITEM_SELL_PRICE_EXT"> / {{ item.ITEM_SELL_PRICE_EXT }}</span>
                  </div>
                  <div class="qty-badge" v-if="item.OWN_QTY > 0">보유 수량: {{ item.OWN_QTY }}</div>
                </div>
              </div>
            </div>
          </template>

        </div><!-- /#app -->
      </div>
    </div>
  </div>

  <script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.js"></script>
  <script>
    new Vue({
      el: '#app',
      data: {
        userName: '',
        items: [],
        loading: false,
        searched: false,
        searchedUser: '',
        ownedFilter: 'all'
      },
      computed: {
        filteredItems() {
          if (this.ownedFilter === 'all') return this.items;
          return this.items.filter(i =>
            this.ownedFilter === 'owned' ? i.OWNED_YN === 'Y' : i.OWNED_YN !== 'Y'
          );
        },
        groupedItems() {
          const order = [];
          const map = {};
          this.filteredItems.forEach(item => {
            const type = item.ITEM_TYPE || '기타';
            if (!map[type]) {
              map[type] = { type: type, items: [], ownedCount: 0 };
              order.push(map[type]);
            }
            map[type].items.push(item);
            if (item.OWNED_YN === 'Y') map[type].ownedCount++;
          });
          return order;
        }
      },
      methods: {
        fetchItems() {
          if (!this.userName.trim()) { alert('유저명을 입력해주세요.'); return; }
          this.loading = true;
          this.searched = false;
          this.ownedFilter = 'all';
          const url = '<%=request.getContextPath()%>/loa/api/items?userName=' + encodeURIComponent(this.userName.trim());
          fetch(url)
            .then(r => r.json())
            .then(data => {
              this.items = data.items || [];
              this.searchedUser = data.userName;
              this.searched = true;
            })
            .catch(() => alert('조회 중 오류가 발생했습니다.'))
            .finally(() => { this.loading = false; });
        }
      }
    });
  </script>
</body>
</html>
