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
    .tile.stairs{ background:var(--gold); }
    .tile.hidden{ background:#D8CDB4; color:#8a7f68; }
    .tile.done{ opacity:.55; } .tile.here{ outline:2px solid var(--ink); transform:scale(1.08); opacity:1; }
    .rail-end{ flex-shrink:0; width:60px; height:46px; border-radius:12px; display:flex; flex-direction:column;
               align-items:center; justify-content:center; font-size:9px; font-weight:700; color:#fff; }
    .rail-end.village{ background:var(--village); } .rail-end.boss{ background:var(--boss); }
    .legend{ display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }
    .legend-chip{ display:flex; align-items:center; gap:4px; font-size:10px; color:var(--ink-soft); background:#fff;
                  border:1px solid var(--line); border-radius:999px; padding:3px 9px; }
    .legend-dot{ width:8px; height:8px; border-radius:50%; }

    .party-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr)); gap:8px; }
    .party-card{ background:#fff; border:2px solid var(--line); border-radius:14px; padding:9px; cursor:pointer; text-align:center; }
    .party-card.inparty{ border-color:var(--gold); background:var(--gold-soft); }
    .party-card .avatar{ width:48px; height:48px; border-radius:50%; object-fit:cover; background:#EFE7D2;
                          display:block; margin:0 auto 6px; border:2px solid var(--line); }
    .party-card .avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:24px; }
    .party-card{ position:relative; }
    .party-card.hidden{ opacity:.4; }
    .party-card .hide-btn{ position:absolute; top:4px; right:6px; font-size:13px; cursor:pointer; background:none; border:none; padding:2px; }
    .party-card .cname{ font-size:12px; font-weight:800; }
    .party-card .role{ font-size:10px; color:var(--ink-soft); }
    .hpbar-track{ height:6px; border-radius:4px; background:#EFE7D2; overflow:hidden; margin-top:4px; }
    .hpbar-fill{ height:100%; background:linear-gradient(90deg,#5FBE85,#2F8F5C); }
    .hp-num{ font-size:9px; color:var(--ink-soft); margin-top:2px; }

    .shop-header{ display:flex; align-items:center; justify-content:space-between; gap:8px; flex-wrap:wrap; }
    .shop-header .pp-badge{ margin:0; }
    .free-chip{ font-size:11px; font-weight:700; color:var(--gold); background:var(--gold-soft);
                border-radius:999px; padding:4px 10px; }
    .shop-row .btn-group{ display:flex; gap:5px; flex-shrink:0; }
    .shop-row button.ten{ background:var(--shop); }
    .shop-row button.free{ background:var(--pp); }

    .shop-row{ display:flex; justify-content:space-between; align-items:center; background:#fff; border:1.5px solid var(--line);
               border-radius:12px; padding:9px 12px; margin-bottom:6px; font-size:12px; }
    .shop-row button{ background:var(--gold); color:#fff; border:none; border-radius:10px; padding:6px 12px; font-size:11px; cursor:pointer; }

    .ach-row{ display:flex; gap:8px; align-items:flex-start; background:#fff; border:1.5px solid var(--line);
              border-radius:12px; padding:8px 12px; margin-bottom:6px; font-size:12px; }
    .ach-row.done{ background:#fffbe8; border-color:var(--gold); }

    .msg-toast{ position:fixed; left:50%; bottom:96px; transform:translateX(-50%); background:var(--ink); color:#fff;
                padding:12px 18px; border-radius:12px; font-size:12px; line-height:1.6; max-width:min(92vw,420px);
                text-align:left; z-index:50; box-shadow:0 6px 18px rgba(0,0,0,.3); display:none; white-space:pre-line; }

    .dock{ position:fixed; left:0; right:0; bottom:0; display:flex; justify-content:center;
           padding:10px 14px calc(10px + env(safe-area-inset-bottom));
           background:linear-gradient(180deg,rgba(251,243,223,0),var(--parchment) 22%); }
    .dock-inner{ max-width:900px; width:100%; margin-left:120px; display:flex; gap:8px; background:#fff;
                 border:2px solid var(--line); border-radius:20px; padding:8px; box-shadow:0 10px 26px -10px rgba(46,36,64,.4); }
    .dbtn{ flex:1; display:flex; flex-direction:column; align-items:center; gap:2px; background:transparent; border:none;
           border-radius:14px; padding:8px 4px 6px; color:var(--ink-soft); font-size:10px; font-weight:700; cursor:pointer; }
    .dbtn .d-icn{ font-size:18px; }
    .dbtn:hover{ background:var(--parchment-deep); }
    .dbtn.active{ color:var(--gold); }
    .dbtn.primary{ background:linear-gradient(180deg,#E4633F,var(--combat)); color:#fff; box-shadow:0 3px 0 #A93A28; }
    .dbtn.primary .d-icn{ font-size:22px; }
    .dbtn:disabled{ opacity:.35; cursor:not-allowed; }
    .dbtn:disabled:hover{ background:transparent; }

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
        <span class="legend-chip"><span class="legend-dot" style="background:var(--gold)"></span>계단</span>
        <span class="legend-chip"><span class="legend-dot" style="background:#D8CDB4"></span>미발견</span>
      </div>
    </div>
    <div class="card" id="autoHuntCard" style="margin-top:10px; display:none;">
      <div class="card-title">🔥 자동사냥</div>
      <div id="autoHuntBox" style="font-size:12px; color:var(--ink-soft); line-height:1.7;"></div>
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

  <!-- 파티(동료 3명)와 그 동료들에게 장착하는 장비는 한 화면에서 같이 관리 -->
  <div class="panel" id="panel-party">
    <div class="card">
      <div class="card-title">보유 동료 (클릭해서 파티 편성/해제, 최대 3명)</div>
      <div class="party-grid" id="partyGrid"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">파티 장비 현황</div>
      <div id="partyEquipBox"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">미착용 장비 (클릭으로 장착/합성)</div>
      <div id="equipListBox"></div>
    </div>
  </div>

  <div class="panel" id="panel-shop">
    <div class="card">
      <div class="shop-header">
        <div class="pp-badge">💰 <span class="val" id="shopPpVal">0</span></div>
        <span class="free-chip" id="shopFreeChip" style="display:none;"></span>
      </div>
    </div>
    <div class="card" style="margin-top:10px;">
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
    <button class="dbtn active" data-tab="board" onclick="TW.switchTab('board')"><span class="d-icn">🗼</span>탑</button>
    <button class="dbtn" data-tab="party" id="dbtnParty" onclick="TW.switchTab('party')"><span class="d-icn">🎒</span>편성</button>
    <button class="dbtn primary" id="dbtnDice" onclick="TW.action('DICE','')"><span class="d-icn">🎲</span>주사위</button>
    <button class="dbtn" data-tab="shop" id="dbtnShop" onclick="TW.switchTab('shop')"><span class="d-icn">🛍️</span>상점</button>
    <button class="dbtn" data-tab="ach" onclick="TW.switchTab('ach')"><span class="d-icn">🏆</span>업적</button>
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

  // 서버 메시지는 ♬(NL)를 줄바꿈 구분자로 씀(카톡봇 등 채팅 환경 공용) -- 웹에서는 실제 개행으로 변환
  function formatMsg(msg) {
    return (msg == null ? '' : String(msg)).split('♬').join('\n');
  }

  // PP_EXT/COST_EXT 등 단위 컬럼은 ''가 Oracle에서 NULL로 저장되므로 항상 null-safe 처리
  function fmtPP(value, ext) {
    var v = (value == null) ? 0 : value;
    var e = (ext == null) ? '' : ext;
    return v + e;
  }

  function toast(msg) {
    var el = document.getElementById('msgToast');
    var text = formatMsg(msg);
    el.textContent = text;
    el.style.display = 'block';
    clearTimeout(toast._t);
    var duration = Math.min(8000, Math.max(3200, text.length * 60));
    toast._t = setTimeout(function () { el.style.display = 'none'; }, duration);
  }

  var state = { inVillage: false };

  // 주사위는 마을이 아니면서, 동시에 '탑' 화면을 보고 있을 때만 누를 수 있다.
  function updateDiceButtonState() {
    var onBoardTab = document.getElementById('panel-board').classList.contains('active');
    document.getElementById('dbtnDice').disabled = state.inVillage || !onBoardTab;
  }

  function switchTab(name) {
    document.querySelectorAll('.dbtn[data-tab]').forEach(function (b) { b.classList.toggle('active', b.dataset.tab === name); });
    document.querySelectorAll('.panel').forEach(function (p) { p.classList.toggle('active', p.id === 'panel-' + name); });
    if (name === 'shop') loadShop();
    if (name === 'ach') loadAchievements();
    if (name === 'party') loadPartyAndEquip();
    updateDiceButtonState();
  }

  var TILE_CLASS = { COMBAT: 'combat', SHOP: 'shop', PP: 'pp', TRAP: 'trap', SPECIAL: 'special', STAIRS: 'stairs' };
  var TILE_KR    = { COMBAT: '전투', SHOP: '상점', PP: 'PP', TRAP: '함정', SPECIAL: '특수', STAIRS: '계단' };
  function tileClass(type) { return TILE_CLASS[type] || 'combat'; }

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

        // '편성' 탭 안에는 파티 슬롯 토글(마을 전용)과 장비 장착/합성(어디서든 가능)이
        // 함께 있어서 탭 자체를 막지는 않는다 -- 파티 슬롯 토글만 마을 밖에서 누르면
        // 서버(BotS5Service.partyToggle)가 거부 메시지를 토스트로 띄운다.
        // 뽑기(상점)도 마을이 아니어도 가능 -- 상점 탭도 항상 열어둠.
        state.inVillage = (p.CUR_FLOOR % 10 === 0);
        updateDiceButtonState();

        var huntCard = document.getElementById('autoHuntCard');
        if (data.autoHunt) {
          huntCard.style.display = '';
          var h = data.autoHunt;
          var mins = h.elapsedMinutes || 0;
          var hh = Math.floor(mins / 60), mm = mins % 60;
          document.getElementById('autoHuntBox').innerHTML =
              h.floor + '층 (' + (h.monsterName || '?') + ') 기준으로 정산됩니다.<br>'
              + '시간당 약 ' + (h.ppPerHourFormatted || '?') + ' PP<br>'
              + '누적 경과: ' + hh + '시간 ' + mm + '분 (최대 8시간까지 인정, 다음 /주사위 때 일괄 정산)';
        } else {
          huntCard.style.display = 'none';
        }

        var track = document.getElementById('towerTrack');
        track.innerHTML = '';
        if (data.tiles && data.tiles.length) {
          var curTile = data.myTile ? data.myTile.CUR_TILE : 0;
          data.tiles.forEach(function (t) {
            var div = document.createElement('div');
            var isHere = (t.TILE_NO === curTile);
            if (t.DISCOVERED) {
              div.className = 'tile ' + tileClass(t.TILE_TYPE) + (isHere ? ' here' : ' done');
              div.innerHTML = '<span class="tno">' + t.TILE_NO + '</span>' + (TILE_KR[t.TILE_TYPE] || t.TILE_TYPE);
            } else {
              // 방문한 적 없는 칸은 종류를 감추고 물음표만 표시(fog of war)
              div.className = 'tile hidden' + (isHere ? ' here' : '');
              div.innerHTML = '<span class="tno">' + t.TILE_NO + '</span>?';
            }
            track.appendChild(div);
          });
        } else {
          track.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">이 층은 보드가 없습니다 (마을/보스층)</div>';
        }
      })
      .catch(function () { toast('조회 실패'); });
  }

  var PART_KR = { HELMET: '투구', WEAPON: '무기', ARMOR: '갑옷' };
  var JOB_KR  = { WARRIOR: '전사', MAGE: '마법사', ROGUE: '도적', ARCHER: '궁수', PRIEST: '도사' };
  // 초상화(IMAGE_URL)는 외부 API(nekos.best) 실패/차단 시 비어있을 수 있어 직업별 이모지로 항상 얼굴이 보이게 폴백
  var JOB_EMOJI = { WARRIOR: '⚔️', MAGE: '🧙', ROGUE: '🗡️', ARCHER: '🏹', PRIEST: '💫' };

  // 파티(동료 최대 3명)와 그 동료들에게 장착하는 장비는 한 화면에서 같이 관리한다.
  function loadPartyAndEquip() {
    var u = userName();
    if (!u) return;
    Promise.all([
      fetch(base + '/api/tower-party?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }),
      fetch(base + '/api/tower-equip?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); })
    ]).then(function (results) {
      var companions = results[0].companions || [];
      var equips = results[1].equips || [];

      // 동료 목록 + 편성 토글
      var grid = document.getElementById('partyGrid');
      grid.innerHTML = '';
      companions.forEach(function (c, idx) {
        var hidden = c.HIDDEN_YN === 'Y';
        var div = document.createElement('div');
        div.className = 'party-card' + (c.PARTY_SLOT ? ' inparty' : '') + (hidden ? ' hidden' : '');
        var img = c.IMAGE_URL ? c.IMAGE_URL : '';
        var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
        var emoji = JOB_EMOJI[c.CLASS] || '👤';
        div.innerHTML = '<div class="cname">' + name + '</div>'
            + '<div class="role">' + (JOB_KR[c.CLASS] || c.CLASS) + ' ★' + c.GRADE + '</div>'
            + '<div class="hpbar-track"><div class="hpbar-fill" style="width:100%"></div></div>'
            + '<div class="hp-num">HP ' + fmtPP(c.CUR_HP_VALUE, c.CUR_HP_EXT) + (c.PARTY_SLOT ? ' [파티' + c.PARTY_SLOT + ']' : '') + '</div>';
        // 이미지가 있으면 <img>를 쓰되, 로드 실패(차단/404 등) 시 직업 이모지로 교체
        var avatarEl = document.createElement(img ? 'img' : 'div');
        avatarEl.className = 'avatar' + (img ? '' : ' avatar-emoji');
        if (img) {
            avatarEl.src = img;
            avatarEl.alt = '';
            avatarEl.onerror = function () {
                avatarEl.outerHTML = '<div class="avatar avatar-emoji">' + emoji + '</div>';
            };
        } else {
            avatarEl.textContent = emoji;
        }
        div.insertBefore(avatarEl, div.firstChild);
        div.onclick = function () { action('PARTY_TOGGLE', String(idx + 1)); };
        var hideBtn = document.createElement('button');
        hideBtn.className = 'hide-btn';
        hideBtn.type = 'button';
        hideBtn.textContent = hidden ? '👀' : '🙈';
        hideBtn.title = hidden ? '목록에 다시 표시' : '텍스트 목록(/파티편성)에서 숨기기';
        hideBtn.onclick = function (ev) { ev.stopPropagation(); action('COMPANION_HIDE', String(idx + 1)); };
        div.appendChild(hideBtn);
        grid.appendChild(div);
      });

      // 장착된 장비를 동료별로 묶기
      var byCompanion = {};
      var unequipped = [];
      equips.forEach(function (e) {
        if (e.EQUIPPED_COMPANION_ID != null) {
          (byCompanion[e.EQUIPPED_COMPANION_ID] = byCompanion[e.EQUIPPED_COMPANION_ID] || []).push(e);
        } else {
          unequipped.push(e);
        }
      });

      // 파티 슬롯별 장비 현황
      var partyBox = document.getElementById('partyEquipBox');
      partyBox.innerHTML = '';
      var partied = companions.filter(function (c) { return c.PARTY_SLOT; })
                               .sort(function (a, b) { return a.PARTY_SLOT - b.PARTY_SLOT; });
      if (partied.length === 0) {
        partyBox.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">파티에 편성된 동료가 없습니다.</div>';
      }
      partied.forEach(function (c) {
        var row = document.createElement('div');
        row.className = 'shop-row';
        var mine = byCompanion[c.COMPANION_ID] || [];
        var parts = ['HELMET', 'WEAPON', 'ARMOR'].map(function (part) {
          var found = mine.filter(function (e) { return e.PART === part; })[0];
          return PART_KR[part] + (found ? ' ★' + found.GRADE : ' 미착용');
        }).join(' / ');
        var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
        row.innerHTML = '<span>[파티' + c.PARTY_SLOT + '] ' + name + ' (' + (JOB_KR[c.CLASS] || c.CLASS) + ' ★' + c.GRADE + ') — ' + parts + '</span>';
        partyBox.appendChild(row);
      });

      // 미착용 장비 목록 -- 번호 입력 없이 버튼 클릭으로 장착(자동배정)/합성
      var box = document.getElementById('equipListBox');
      box.innerHTML = '';
      if (unequipped.length === 0) {
        box.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">미착용 장비가 없습니다.</div>';
      }
      unequipped.forEach(function (e, i) {
        var row = document.createElement('div');
        row.className = 'shop-row';
        var idx = i + 1;
        row.innerHTML = '<span>' + (JOB_KR[e.CLASS] || e.CLASS) + ' ' + (PART_KR[e.PART] || e.PART) + ' ★' + e.GRADE + '</span>'
            + '<span class="btn-group">'
            + '<button onclick="TW.action(\'EQUIP_WEAR\',\'' + idx + '\')">장착</button>'
            + '<button class="ten" onclick="TW.action(\'EQUIP_SYNTH\',\'' + idx + '\')">합성</button>'
            + '</span>';
        box.appendChild(row);
      });
    });
  }

  function loadShop() {
    var u = userName();
    fetch(base + '/api/tower-shop?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        document.getElementById('shopPpVal').textContent = fmtPP(data.ppValue, data.ppExt);
        var starterFree = data.freeCompanionPullsLeft || 0;
        var companionVoucher = data.companionVoucher || 0;
        var equipVoucher = data.equipVoucher || 0;

        var chipText = [];
        if (starterFree > 0) chipText.push('튜토리얼 무료 ' + starterFree + '회');
        if (companionVoucher > 0) chipText.push('동료뽑기권 ' + companionVoucher + '장');
        if (equipVoucher > 0) chipText.push('장비뽑기권 ' + equipVoucher + '장');
        var freeChip = document.getElementById('shopFreeChip');
        if (chipText.length > 0) {
          freeChip.style.display = '';
          freeChip.textContent = '🎁 ' + chipText.join(' · ');
        } else {
          freeChip.style.display = 'none';
        }

        var cBox = document.getElementById('companionGachaList');
        cBox.innerHTML = '';
        (data.companionGacha || []).forEach(function (g, gi) {
          var row = document.createElement('div');
          row.className = 'shop-row';
          // 스타터(1번) 계약서는 튜토리얼 무료도 적용, 나머지는 뽑기권만 적용
          var isFree = (gi === 0 && starterFree > 0) || companionVoucher > 0;
          var singleLabel = isFree ? '무료뽑기' : '뽑기';
          // 서버(BotS5Service.gachaCompanion 등)는 이제 GACHA_ID(DB PK)가 아니라 표시번호
          // (1부터, UNLOCK_FLOOR 순 = 이 목록 순서)를 받는다 -- 채팅 명령어 /동료뽑기 N 과
          // 번호 체계를 통일하기 위함(장비뽑기는 GACHA_ID가 5~8부터 시작해서 혼란스러웠음).
          var displayNo = gi + 1;
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)</span>'
              + '<span class="btn-group">'
              + '<button class="' + (isFree ? 'free' : '') + '" onclick="TW.action(\'GACHA_COMPANION\',\'' + displayNo + '\')">' + singleLabel + '</button>'
              + '<button class="ten" onclick="TW.action(\'GACHA_COMPANION_10\',\'' + displayNo + '\')">10연속</button>'
              + '</span>';
          cBox.appendChild(row);
        });
        var eBox = document.getElementById('equipGachaList');
        eBox.innerHTML = '';
        (data.equipGacha || []).forEach(function (g, gi) {
          var row = document.createElement('div');
          row.className = 'shop-row';
          var isFree = equipVoucher > 0;
          var displayNo = gi + 1;
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)</span>'
              + '<span class="btn-group">'
              + '<button class="' + (isFree ? 'free' : '') + '" onclick="TW.action(\'GACHA_EQUIP\',\'' + displayNo + '\')">' + (isFree ? '무료뽑기' : '뽑기') + '</button>'
              + '<button class="ten" onclick="TW.action(\'GACHA_EQUIP_10\',\'' + displayNo + '\')">10연속</button>'
              + '</span>';
          eBox.appendChild(row);
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
      if (document.getElementById('panel-party').classList.contains('active')) loadPartyAndEquip();
      if (document.getElementById('panel-shop').classList.contains('active')) loadShop();
    }).catch(function () { toast('요청 실패'); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var saved = sessionStorage.getItem('loaUserName');
    if (saved) document.getElementById('userNameInput').value = saved;
    if (saved) loadStatus();
  });

  return { load: loadStatus, action: action, switchTab: switchTab };
})();
</script>
</body>
</html>
