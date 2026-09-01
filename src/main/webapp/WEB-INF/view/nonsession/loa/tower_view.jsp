<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 등반기</title>
  <style>
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC;
      --combat:#D8523F; --combat-soft:#F7DAD2;
      --shop:#6C4E9E; --shop-soft:#E4DAF2;
      --pp:#2F8F5C; --pp-soft:#D6EEDF;
      --trap:#3A3548; --trap-soft:#DAD6E4;
      --special:#D64B87; --special-soft:#F8DCE9;
      --village:#3C7CB8; --boss:#A31F2B;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    body{
      margin:0; margin-left:120px;
      background: radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
      color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;
    }
    .wrap{ max-width:900px; margin:0 auto; padding:20px 16px 120px; display:flex; flex-direction:column; gap:14px; }

    .top-controls{ display:flex; gap:8px; margin-bottom:2px; }
    .top-controls input{ padding:9px 14px; border:1.5px solid var(--line); border-radius:22px; background:#fff; font-size:13px; width:150px; }
    .btn-query{ background:var(--gold); color:#fff; border:none; padding:9px 18px; border-radius:22px; font-size:13px; font-weight:700; cursor:pointer; }

    .card{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line);
           border-radius:20px; padding:16px; box-shadow:var(--shadow); }
    .card-title{ font-size:16px; font-weight:800; display:flex; align-items:center; gap:6px; margin-bottom:10px; }

    .statusbar{ display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; }
    .who-name{ font-size:15px; font-weight:800; }
    .who-floor{ font-size:12px; color:var(--ink-soft); }
    .pp-badge{ display:flex; align-items:baseline; gap:5px; background:var(--pp-soft); border:2px solid #BFE3CD;
               border-radius:999px; padding:7px 14px; }
    .pp-badge .val{ font-size:15px; font-weight:800; color:var(--pp); }

    .tabs{ display:flex; gap:6px; overflow-x:auto; }
    .tab-btn{ flex-shrink:0; background:#fff; border:1.5px solid var(--line); border-radius:14px; padding:8px 14px;
              font-size:12px; font-weight:700; color:var(--ink-soft); cursor:pointer; }
    .tab-btn.active{ background:var(--gold); border-color:var(--gold); color:#fff; }
    .panel{ display:none; }
    .panel.active{ display:block; }

    .tower-track{ display:flex; flex-direction:column-reverse; gap:8px; }
    .tile-row{ display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
    .tile{ position:relative; flex:1 1 44px; min-width:44px; height:46px; border-radius:12px; display:flex;
           flex-direction:column; align-items:center; justify-content:center; font-size:10px; font-weight:700;
           color:#fff; box-shadow:0 2px 0 rgba(0,0,0,.12); }
    .tile .tno{ position:absolute; top:2px; left:5px; font-size:8px; opacity:.75; }
    .tile.combat{ background:var(--combat); } .tile.shop{ background:var(--shop); }
    .tile.pp{ background:var(--pp); } .tile.trap{ background:var(--trap); } .tile.special{ background:var(--special); }
    .tile.done{ opacity:.35; } .tile.here{ outline:2px solid var(--ink); transform:scale(1.08); }
    .rail-end{ flex-shrink:0; width:60px; height:46px; border-radius:12px; display:flex; flex-direction:column;
               align-items:center; justify-content:center; font-size:9px; font-weight:700; color:#fff; }
    .rail-end.village{ background:var(--village); } .rail-end.boss{ background:var(--boss); }
    .legend{ display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }
    .legend-chip{ display:flex; align-items:center; gap:4px; font-size:10px; color:var(--ink-soft); background:#fff;
                  border:1px solid var(--line); border-radius:999px; padding:3px 9px; }
    .legend-dot{ width:8px; height:8px; border-radius:50%; }

    .party-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(140px,1fr)); gap:8px; }
    .party-card{ background:#fff; border:2px solid var(--line); border-radius:14px; padding:9px; cursor:pointer; }
    .party-card.inparty{ border-color:var(--gold); background:var(--gold-soft); }
    .party-card .role{ font-size:11px; font-weight:700; }
    .hpbar-track{ height:6px; border-radius:4px; background:#EFE7D2; overflow:hidden; margin-top:4px; }
    .hpbar-fill{ height:100%; background:linear-gradient(90deg,#5FBE85,#2F8F5C); }
    .hp-num{ font-size:9px; color:var(--ink-soft); margin-top:2px; }

    .shop-row{ display:flex; justify-content:space-between; align-items:center; background:#fff; border:1.5px solid var(--line);
               border-radius:12px; padding:9px 12px; margin-bottom:6px; font-size:12px; }
    .shop-row button{ background:var(--gold); color:#fff; border:none; border-radius:10px; padding:6px 12px; font-size:11px; cursor:pointer; }

    .ach-row{ display:flex; gap:8px; align-items:flex-start; background:#fff; border:1.5px solid var(--line);
              border-radius:12px; padding:8px 12px; margin-bottom:6px; font-size:12px; }
    .ach-row.done{ background:#fffbe8; border-color:var(--gold); }

    .msg-toast{ position:fixed; left:50%; bottom:96px; transform:translateX(-50%); background:var(--ink); color:#fff;
                padding:10px 18px; border-radius:12px; font-size:12px; max-width:90vw; text-align:center; z-index:50;
                box-shadow:0 6px 18px rgba(0,0,0,.3); display:none; white-space:pre-line; }

    .dock{ position:fixed; left:0; right:0; bottom:0; display:flex; justify-content:center;
           padding:10px 14px calc(10px + env(safe-area-inset-bottom));
           background:linear-gradient(180deg,rgba(251,243,223,0),var(--parchment) 22%); }
    .dock-inner{ max-width:900px; width:100%; margin-left:120px; display:flex; gap:8px; background:#fff;
                 border:2px solid var(--line); border-radius:20px; padding:8px; box-shadow:0 10px 26px -10px rgba(46,36,64,.4); }
    .dbtn{ flex:1; display:flex; flex-direction:column; align-items:center; gap:2px; background:transparent; border:none;
           border-radius:14px; padding:8px 4px 6px; color:var(--ink-soft); font-size:10px; font-weight:700; cursor:pointer; }
    .dbtn .d-icn{ font-size:18px; }
    .dbtn:hover{ background:var(--parchment-deep); }
    .dbtn.primary{ background:linear-gradient(180deg,#E4633F,var(--combat)); color:#fff; box-shadow:0 3px 0 #A93A28; }
    .dbtn.primary .d-icn{ font-size:22px; }

    select.floor-select{ padding:6px 10px; border-radius:10px; border:1.5px solid var(--line); font-size:12px; }

    @media (max-width:600px){
      body{ margin-left:0; padding-top:8px; }
      .dock-inner{ margin-left:0; }
    }
  </style>
</head>
<body>
<div class="wrap">

  <div class="top-controls">
    <input type="text" id="userNameInput" placeholder="유저명 입력">
    <button class="btn-query" onclick="TW.load()">조회</button>
  </div>

  <div class="card statusbar" id="statusCard">
    <div>
      <div class="who-name" id="whoName">-</div>
      <div class="who-floor" id="whoFloor">유저명을 입력하고 조회를 눌러주세요</div>
    </div>
    <div class="pp-badge">💰 <span class="val" id="ppVal">0</span></div>
  </div>

  <div class="tabs">
    <button class="tab-btn active" data-tab="board">🗼 탑</button>
    <button class="tab-btn" data-tab="party">🎒 파티</button>
    <button class="tab-btn" data-tab="shop">🛍️ 상점</button>
    <button class="tab-btn" data-tab="equip">⚔️ 장비</button>
    <button class="tab-btn" data-tab="ach">🏆 업적</button>
  </div>

  <div class="panel active" id="panel-board">
    <div class="card">
      <div class="card-title">보드</div>
      <div class="tower-track" id="towerTrack">
        <div style="color:var(--ink-soft);font-size:12px;">데이터를 불러오는 중...</div>
      </div>
      <div class="legend">
        <span class="legend-chip"><span class="legend-dot" style="background:var(--combat)"></span>전투</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--shop)"></span>비밀상점</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--pp)"></span>PP</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--trap)"></span>함정</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--special)"></span>특수</span>
      </div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">층 이동</div>
      <select class="floor-select" id="floorSelect">
        <option value="0">0 (마을)</option>
        <option value="1">1</option><option value="2">2</option><option value="3">3</option>
        <option value="4">4</option><option value="5">5</option><option value="6">6</option>
        <option value="7">7</option><option value="8">8</option><option value="9">9 (보스)</option>
      </select>
      <button class="btn-query" onclick="TW.action('CHANGE_FLOOR', document.getElementById('floorSelect').value)">이동</button>
    </div>
  </div>

  <div class="panel" id="panel-party">
    <div class="card">
      <div class="card-title">보유 동료 (클릭해서 파티 편성/해제, 최대 3명)</div>
      <div class="party-grid" id="partyGrid"></div>
    </div>
  </div>

  <div class="panel" id="panel-shop">
    <div class="card">
      <div class="card-title">동료 계약서</div>
      <div id="companionGachaList"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">장비 보물상자</div>
      <div id="equipGachaList"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">주사위 / 스탯</div>
      <button class="btn-query" onclick="TW.action('DICE_BUY','')">주사위 목록</button>
      <button class="btn-query" onclick="TW.action('STAT_BUY','')">스탯 현황</button>
      <div style="margin-top:8px; display:flex; gap:6px; flex-wrap:wrap;">
        <button class="btn-query" onclick="TW.action('STAT_BUY','공격력')">공격력 강화</button>
        <button class="btn-query" onclick="TW.action('STAT_BUY','최소공격력')">최소공격력 강화</button>
        <button class="btn-query" onclick="TW.action('STAT_BUY','체력')">체력 강화</button>
      </div>
    </div>
  </div>

  <div class="panel" id="panel-equip">
    <div class="card">
      <div class="card-title">보유 장비 (번호는 미착용 목록 기준)</div>
      <div id="equipListBox"></div>
      <div style="margin-top:8px; display:flex; gap:6px;">
        <input type="number" id="equipWearIdx" placeholder="장비#" style="width:70px;padding:6px;">
        <button class="btn-query" onclick="TW.action('EQUIP_WEAR', document.getElementById('equipWearIdx').value)">장착(자동배정)</button>
        <button class="btn-query" onclick="TW.action('EQUIP_SYNTH', document.getElementById('equipWearIdx').value)">합성</button>
      </div>
    </div>
  </div>

  <div class="panel" id="panel-ach">
    <div class="card">
      <div class="card-title" id="achTitle">업적</div>
      <div id="achList"></div>
    </div>
  </div>

</div>

<div class="msg-toast" id="msgToast"></div>

<nav class="dock">
  <div class="dock-inner">
    <button class="dbtn" onclick="TW.action('PARTY_TOGGLE', prompt('편성/해제할 동료 번호 (파티 탭 목록 기준)'))"><span class="d-icn">🎒</span>편성</button>
    <button class="dbtn primary" onclick="TW.action('DICE','')"><span class="d-icn">🎲</span>주사위</button>
    <button class="dbtn" onclick="TW.switchTab('shop')"><span class="d-icn">🛍️</span>상점</button>
  </div>
</nav>

<script>
var TW = (function () {
  var base = '<%=request.getContextPath()%>/loa';

  function userName() {
    var v = document.getElementById('userNameInput').value.trim();
    if (v) sessionStorage.setItem('loaUserName', v);
    return v || sessionStorage.getItem('loaUserName') || '';
  }

  function toast(msg) {
    var el = document.getElementById('msgToast');
    el.textContent = msg;
    el.style.display = 'block';
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { el.style.display = 'none'; }, 3200);
  }

  function switchTab(name) {
    document.querySelectorAll('.tab-btn').forEach(function (b) { b.classList.toggle('active', b.dataset.tab === name); });
    document.querySelectorAll('.panel').forEach(function (p) { p.classList.toggle('active', p.id === 'panel-' + name); });
    if (name === 'shop') loadShop();
    if (name === 'equip') loadEquip();
    if (name === 'ach') loadAchievements();
    if (name === 'party') loadParty();
  }

  function tileClass(type) {
    return { COMBAT: 'combat', SHOP: 'shop', PP: 'pp', TRAP: 'trap', SPECIAL: 'special' }[type] || 'combat';
  }

  function loadStatus() {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    fetch(base + '/api/tower-status?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.error) { toast(data.error); return; }
        var p = data.progress;
        document.getElementById('whoName').textContent = u;
        document.getElementById('whoFloor').textContent = p.CUR_FLOOR + '층 · 상태 ' + p.STATUS
            + (p.AUTO_HUNT_YN === 'Y' ? ' · 자동사냥ON' : '');
        document.getElementById('ppVal').textContent = (p.PP_VALUE || 0).toFixed ? p.PP_VALUE.toFixed(2) + (p.PP_EXT || '') : p.PP_VALUE;

        var track = document.getElementById('towerTrack');
        track.innerHTML = '';
        if (data.tiles && data.tiles.length) {
          var curTile = data.myTile ? data.myTile.CUR_TILE : 0;
          data.tiles.forEach(function (t) {
            var div = document.createElement('div');
            div.className = 'tile ' + tileClass(t.TILE_TYPE) + (t.TILE_NO <= curTile ? ' done' : '') + (t.TILE_NO === curTile ? ' here' : '');
            div.innerHTML = '<span class="tno">' + t.TILE_NO + '</span>' + t.TILE_TYPE;
            track.appendChild(div);
          });
        } else {
          track.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">이 층은 보드가 없습니다 (마을/보스층)</div>';
        }
      })
      .catch(function () { toast('조회 실패'); });
  }

  function loadParty() {
    var u = userName();
    if (!u) return;
    fetch(base + '/api/tower-party?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var grid = document.getElementById('partyGrid');
        grid.innerHTML = '';
        (data.companions || []).forEach(function (c, idx) {
          var div = document.createElement('div');
          div.className = 'party-card' + (c.PARTY_SLOT ? ' inparty' : '');
          var hpMax = c.CUR_HP_VALUE; // 표시는 현재치만 (최대치는 서버 계산)
          div.innerHTML = '<div class="role">' + c.CLASS + ' ★' + c.GRADE + '</div>'
              + '<div class="hpbar-track"><div class="hpbar-fill" style="width:100%"></div></div>'
              + '<div class="hp-num">HP ' + c.CUR_HP_VALUE + c.CUR_HP_EXT + (c.PARTY_SLOT ? ' [파티' + c.PARTY_SLOT + ']' : '') + '</div>';
          div.onclick = function () { action('PARTY_TOGGLE', String(idx + 1)); };
          grid.appendChild(div);
        });
      });
  }

  function loadShop() {
    var u = userName();
    fetch(base + '/api/tower-shop?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var cBox = document.getElementById('companionGachaList');
        cBox.innerHTML = '';
        (data.companionGacha || []).forEach(function (g) {
          var row = document.createElement('div');
          row.className = 'shop-row';
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + g.COST_VALUE + g.COST_EXT + ' PP)</span>'
              + '<button onclick="TW.action(\'GACHA_COMPANION\',\'' + g.GACHA_ID + '\')">뽑기</button>';
          cBox.appendChild(row);
        });
        var eBox = document.getElementById('equipGachaList');
        eBox.innerHTML = '';
        (data.equipGacha || []).forEach(function (g) {
          var row = document.createElement('div');
          row.className = 'shop-row';
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + g.COST_VALUE + g.COST_EXT + ' PP)</span>'
              + '<button onclick="TW.action(\'GACHA_EQUIP\',\'' + g.GACHA_ID + '\')">뽑기</button>';
          eBox.appendChild(row);
        });
      });
  }

  function loadEquip() {
    var u = userName();
    fetch(base + '/api/tower-equip?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var box = document.getElementById('equipListBox');
        box.innerHTML = '';
        var uIdx = 1;
        (data.equips || []).forEach(function (e) {
          var row = document.createElement('div');
          row.className = 'shop-row';
          var label = e.EQUIPPED_COMPANION_ID ? '[장착중]' : ('[#' + (uIdx++) + ' 미착용]');
          row.innerHTML = '<span>' + e.CLASS + ' ' + e.PART + ' ★' + e.GRADE + ' ' + label + '</span>';
          box.appendChild(row);
        });
      });
  }

  function loadAchievements() {
    var u = userName();
    fetch(base + '/api/tower-achievements?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        document.getElementById('achTitle').textContent = '업적 (' + data.clearedCount + '/' + data.total + ')';
        var box = document.getElementById('achList');
        box.innerHTML = '';
        (data.achievements || []).forEach(function (a) {
          var row = document.createElement('div');
          row.className = 'ach-row' + (a.DONE ? ' done' : '');
          row.innerHTML = '<span>' + (a.DONE ? '✅' : '⬜') + '</span><span>' + a.ACH_NAME + ' - ' + a.ACH_DESC + '</span>';
          box.appendChild(row);
        });
      });
  }

  function action(type, param1, param2) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=' + type
        + '&param1=' + encodeURIComponent(param1 || '') + '&param2=' + encodeURIComponent(param2 || '');
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      toast(data.message || data.error || '완료');
      loadStatus();
      if (document.getElementById('panel-party').classList.contains('active')) loadParty();
      if (document.getElementById('panel-shop').classList.contains('active')) loadShop();
      if (document.getElementById('panel-equip').classList.contains('active')) loadEquip();
    }).catch(function () { toast('요청 실패'); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var saved = sessionStorage.getItem('loaUserName');
    if (saved) document.getElementById('userNameInput').value = saved;
    document.querySelectorAll('.tab-btn').forEach(function (b) {
      b.addEventListener('click', function () { switchTab(b.dataset.tab); });
    });
    if (saved) loadStatus();
  });

  return { load: loadStatus, action: action, switchTab: switchTab };
})();
</script>
</body>
</html>
