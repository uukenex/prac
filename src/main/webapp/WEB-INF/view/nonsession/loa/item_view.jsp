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
    .search-box { background: #2a2a2a; border-radius: 8px; padding: 20px; margin-bottom: 24px; display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; }
    .search-box label { color: #ccc; font-size: 13px; display: block; margin-bottom: 4px; }
    .search-box input { background: #1a1a1a; border: 1px solid #444; color: #eee; padding: 8px 12px; border-radius: 4px; font-size: 14px; width: 180px; }
    .search-box input:focus { outline: none; border-color: #7ec8e3; }
    .btn-search { background: #4a90d9; color: #fff; border: none; padding: 9px 22px; border-radius: 4px; cursor: pointer; font-size: 14px; }
    .btn-search:hover { background: #357abd; }
    .filter-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; align-items: center; }
    .filter-bar span { color: #999; font-size: 13px; margin-right: 4px; }
    .btn-filter { background: #333; color: #ccc; border: 1px solid #555; padding: 5px 14px; border-radius: 20px; cursor: pointer; font-size: 12px; transition: all .2s; }
    .btn-filter.active, .btn-filter:hover { background: #4a90d9; color: #fff; border-color: #4a90d9; }
    .stats-bar { color: #aaa; font-size: 13px; margin-bottom: 12px; }
    .stats-bar strong { color: #7ec8e3; }
    .item-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
    .item-card { background: #1e1e1e; border: 1px solid #333; border-radius: 8px; padding: 14px; transition: border-color .2s; }
    .item-card:hover { border-color: #555; }
    .item-card.owned { border-color: #4caf50; background: #1a2a1a; }
    .item-card.owned .card-header { color: #4caf50; }
    .card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
    .item-name { font-size: 14px; font-weight: bold; color: #ddd; line-height: 1.3; }
    .owned-badge { font-size: 11px; background: #4caf50; color: #fff; padding: 2px 7px; border-radius: 10px; white-space: nowrap; }
    .not-owned-badge { font-size: 11px; background: #555; color: #999; padding: 2px 7px; border-radius: 10px; white-space: nowrap; }
    .item-type { font-size: 11px; color: #888; margin-bottom: 8px; }
    .item-stats { font-size: 12px; color: #aaa; }
    .item-stats .stat-row { display: flex; justify-content: space-between; padding: 2px 0; border-bottom: 1px solid #2a2a2a; }
    .stat-row:last-child { border-bottom: none; }
    .stat-label { color: #888; }
    .stat-val { color: #c8e6c9; font-weight: bold; }
    .stat-val.zero { color: #555; }
    .item-price { font-size: 12px; color: #ffd700; margin-top: 8px; }
    .qty-badge { font-size: 12px; color: #81d4fa; margin-top: 4px; }
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

          <h2 class="page-title">아이템 보유 현황 <span v-if="searchedUser">— {{ searchedUser }}</span></h2>

          <!-- 검색 -->
          <div class="search-box">
            <div>
              <label>유저명</label>
              <input v-model="userName" placeholder="유저명 입력" @keyup.enter="fetchItems">
            </div>
            <div>
              <label>방 이름</label>
              <input v-model="roomName" placeholder="방 이름 입력" @keyup.enter="fetchItems">
            </div>
            <button class="btn-search" @click="fetchItems">
              <i class="fa fa-search"></i> 조회
            </button>
          </div>

          <!-- 필터 -->
          <div class="filter-bar" v-if="items.length > 0">
            <span>타입:</span>
            <button class="btn-filter" :class="{active: activeType === ''}" @click="activeType = ''">전체</button>
            <button class="btn-filter" v-for="t in itemTypes" :key="t"
                    :class="{active: activeType === t}" @click="activeType = t">{{ t }}</button>
            <span style="margin-left:12px">보유:</span>
            <button class="btn-filter" :class="{active: ownedFilter === 'all'}" @click="ownedFilter = 'all'">전체</button>
            <button class="btn-filter" :class="{active: ownedFilter === 'owned'}" @click="ownedFilter = 'owned'">보유</button>
            <button class="btn-filter" :class="{active: ownedFilter === 'notOwned'}" @click="ownedFilter = 'notOwned'">미보유</button>
          </div>

          <!-- 통계 -->
          <div class="stats-bar" v-if="items.length > 0">
            전체 <strong>{{ filteredItems.length }}</strong>개 &nbsp;|&nbsp;
            보유 <strong>{{ ownedCount }}</strong>개 &nbsp;|&nbsp;
            미보유 <strong>{{ filteredItems.length - ownedCount }}</strong>개
          </div>

          <!-- 로딩 -->
          <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

          <!-- 결과 없음 -->
          <div class="no-result" v-else-if="!loading && searched && filteredItems.length === 0">
            조건에 맞는 아이템이 없습니다.
          </div>

          <!-- 아이템 카드 그리드 -->
          <div class="item-grid" v-else>
            <div class="item-card" v-for="item in filteredItems" :key="item.ITEM_ID"
                 :class="{owned: item.OWNED_YN === 'Y'}">
              <div class="card-header">
                <div class="item-name">{{ item.ITEM_NAME }}</div>
                <span class="owned-badge" v-if="item.OWNED_YN === 'Y'">보유</span>
                <span class="not-owned-badge" v-else>미보유</span>
              </div>
              <div class="item-type">{{ item.ITEM_TYPE }} &nbsp;#{{ item.ITEM_ID }}</div>

              <div class="item-stats">
                <div class="stat-row" v-if="item.ATK_MIN > 0 || item.ATK_MAX > 0">
                  <span class="stat-label">공격력</span>
                  <span class="stat-val">{{ item.ATK_MIN }} ~ {{ item.ATK_MAX }}</span>
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
                  <span class="stat-label">HP 최대(%)</span>
                  <span class="stat-val">+{{ item.HP_MAX_RATE }}%</span>
                </div>
                <div class="stat-row" v-if="item.HP_REGEN > 0">
                  <span class="stat-label">HP 회복</span>
                  <span class="stat-val">+{{ item.HP_REGEN }}</span>
                </div>
                <div class="stat-row" v-if="item.ATK_MAX_RATE > 0">
                  <span class="stat-label">공격력(%)</span>
                  <span class="stat-val">+{{ item.ATK_MAX_RATE }}%</span>
                </div>
              </div>

              <div class="item-price" v-if="item.ITEM_SELL_PRICE > 0">
                <i class="fa fa-coins"></i> {{ item.ITEM_SELL_PRICE.toLocaleString() }} SP
                <span v-if="item.ITEM_SELL_PRICE_EXT"> / {{ item.ITEM_SELL_PRICE_EXT }}</span>
              </div>
              <div class="qty-badge" v-if="item.OWN_QTY > 0">
                <i class="fa fa-archive"></i> 보유 수량: {{ item.OWN_QTY }}
              </div>
            </div>
          </div>

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
        roomName: '',
        items: [],
        loading: false,
        searched: false,
        searchedUser: '',
        activeType: '',
        ownedFilter: 'all'
      },
      computed: {
        itemTypes() {
          const types = [...new Set(this.items.map(i => i.ITEM_TYPE).filter(Boolean))];
          return types.sort();
        },
        filteredItems() {
          return this.items.filter(item => {
            const typeOk = !this.activeType || item.ITEM_TYPE === this.activeType;
            const ownedOk = this.ownedFilter === 'all'
              || (this.ownedFilter === 'owned'    && item.OWNED_YN === 'Y')
              || (this.ownedFilter === 'notOwned' && item.OWNED_YN !== 'Y');
            return typeOk && ownedOk;
          });
        },
        ownedCount() {
          return this.filteredItems.filter(i => i.OWNED_YN === 'Y').length;
        }
      },
      methods: {
        fetchItems() {
          if (!this.userName.trim()) {
            alert('유저명을 입력해주세요.');
            return;
          }
          this.loading = true;
          this.searched = false;
          this.activeType = '';
          this.ownedFilter = 'all';

          const url = '<%=request.getContextPath()%>/loa/api/items'
            + '?userName=' + encodeURIComponent(this.userName.trim())
            + '&roomName=' + encodeURIComponent(this.roomName.trim());

          fetch(url)
            .then(res => res.json())
            .then(data => {
              this.items = data.items || [];
              this.searchedUser = data.userName + (data.roomName ? ' / ' + data.roomName : '');
              this.searched = true;
            })
            .catch(err => {
              console.error(err);
              alert('조회 중 오류가 발생했습니다.');
            })
            .finally(() => { this.loading = false; });
        }
      }
    });
  </script>
</body>
</html>
