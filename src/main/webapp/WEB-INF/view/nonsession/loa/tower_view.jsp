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
      --treasure:#C6982E; --treasure-soft:#F2E6C6;
      --elite:#8E1F1F; --elite-soft:#F0D2D2;
      --village:#3C7CB8; --boss:#A31F2B;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    /* 이 페이지는 사이드바(_loa_nav.jsp)를 포함하지 않는 독립 SPA라 그 폭(120px)을
       비워둘 필요가 없다 -- 대신 하단 dock 내비게이션을 쓴다. 그래서 다른 /loa 페이지들과
       달리 margin-left를 두지 않는다(전에 넣었더니 폰/폴더블 펼친 화면처럼 600px보다
       넓은데 아직 데스크톱은 아닌 폭에서 왼쪽에 아무 것도 없는 여백만 남았었음). */
    body{
      margin:0;
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

    /* 부루마불 스타일: 칸들이 사각형 둘레를 따라 시계방향으로 빙 둘러 배치되는 순환 보드.
       칸 수(15~150)에 따라 정사각형 한 변의 칸수(S)가 달라지므로, 컨테이너 크기와 각 칸의
       left/top은 JS에서 인라인 스타일로 계산해서 넣는다(테두리 칸만 채우는 배치라 CSS 그리드로는
       표현이 애매함). 큰 보드(51층 이후 최대 150칸)는 화면보다 커질 수 있어 스크롤 가능한
       뷰포트 안에 넣고, 처음 로드 시 현재 위치로 자동 스크롤한다. */
    .tower-viewport{ position:relative; overflow:auto; max-height:60vh; border-radius:14px;
                      background:var(--parchment-deep); border:1.5px dashed var(--line); padding:10px; }
    .tower-track{ position:relative; }
    .tile{ position:absolute; border-radius:10px; display:flex;
           flex-direction:column; align-items:center; justify-content:center; font-size:9px; font-weight:700;
           color:#fff; box-shadow:0 2px 0 rgba(0,0,0,.12); text-align:center; line-height:1.15; padding:1px; }
    .tile .tno{ position:absolute; top:1px; left:3px; font-size:7px; opacity:.75; }
    .tile.combat{ background:var(--combat); } .tile.treasure{ background:var(--treasure); }
    .tile.pp{ background:var(--pp); } .tile.trap{ background:var(--trap); } .tile.special{ background:var(--special); }
    .tile.stairs{ background:var(--gold); } .tile.elite{ background:var(--elite); }
    .tile.hidden{ background:#D8CDB4; color:#8a7f68; }
    .tile.done{ opacity:.55; } .tile.here{ outline:2px solid var(--ink); transform:scale(1.15); opacity:1; z-index:2; }
    .legend{ display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }
    .legend-chip{ display:flex; align-items:center; gap:4px; font-size:10px; color:var(--ink-soft); background:#fff;
                  border:1px solid var(--line); border-radius:999px; padding:3px 9px; }
    .legend-dot{ width:8px; height:8px; border-radius:50%; }

    .party-slots{ display:grid; grid-template-columns:repeat(3,1fr); gap:8px; }
    .party-slot-box{ min-height:118px; border:2px dashed var(--line); border-radius:14px; padding:8px;
                      display:flex; flex-direction:column; align-items:center; justify-content:center;
                      text-align:center; font-size:10px; color:var(--ink-soft); background:#fff; }
    .party-slot-box.filled{ border-style:solid; border-color:var(--gold); background:var(--gold-soft); }
    .party-slot-box.drop-hover{ border-color:var(--pp); background:var(--pp-soft); transform:scale(1.04); }
    .party-slot-box .slot-label{ font-size:9px; opacity:.7; margin-bottom:4px; }
    .party-slot-box .cname{ font-size:12px; font-weight:800; color:var(--ink); }
    .party-slot-box .slot-equip{ font-size:9px; color:var(--ink-soft); margin-top:3px; }
    .party-slot-box .slot-unassign{ margin-top:6px; background:#fff; border:1px solid var(--line); border-radius:8px;
                                      font-size:9px; padding:2px 9px; cursor:pointer; color:var(--ink-soft); }
    .party-slot-box .slot-unassign:hover{ border-color:var(--combat); color:var(--combat); }
    .card-title-row{ display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:10px; }
    .card-title-row .card-title{ margin-bottom:0; }
    .btn-unassign-all{ background:#fff; border:1.5px solid var(--line); border-radius:10px;
                         font-size:11px; padding:5px 11px; cursor:pointer; color:var(--ink-soft); white-space:nowrap; }
    .btn-unassign-all:hover{ border-color:var(--combat); color:var(--combat); }

    .party-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr)); gap:8px; }
    .party-card{ background:#fff; border:2px solid var(--line); border-radius:14px; padding:9px; cursor:pointer; text-align:center;
                  touch-action:none; user-select:none; }
    .party-card.inparty{ border-color:var(--gold); background:var(--gold-soft); }
    .party-card.drag-ghost{ position:fixed; z-index:999; pointer-events:none; opacity:.85; box-shadow:0 8px 20px rgba(0,0,0,.3);
                             width:120px; }
    .party-card.drag-source-hidden{ opacity:.25; }
    /* 얼굴이 잘 안 보인다는 신고로 원형 → 사각형으로 변경 + 인물 사진은 보통 위쪽에 얼굴이
       있어서 object-position을 top으로 둬서 얼굴이 잘리지 않게 함. 클릭하면 확대(zoom-in). */
    .party-card .avatar{ width:64px; height:64px; border-radius:10px; object-fit:cover; object-position:50% 15%;
                          background:#EFE7D2; display:block; margin:0 auto 6px; border:2px solid var(--line);
                          cursor:zoom-in; }
    .party-card .avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:28px; cursor:default; }

    /* 캐릭터 확대 + 상세(장비/스탯) 모달 */
    .detail-overlay{ position:fixed; inset:0; background:rgba(20,14,32,.72); z-index:200;
                      display:none; align-items:center; justify-content:center; padding:20px; }
    .detail-overlay.open{ display:flex; }
    .detail-card{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line);
                   border-radius:20px; padding:16px; box-shadow:var(--shadow); max-width:360px; width:100%;
                   max-height:88vh; overflow:auto; position:relative; }
    .detail-card .detail-close{ position:absolute; top:10px; right:12px; background:none; border:none;
                                  font-size:20px; cursor:pointer; color:var(--ink-soft); line-height:1; }
    .detail-card .detail-img{ width:100%; aspect-ratio:1/1; object-fit:cover; object-position:50% 15%;
                                border-radius:14px; border:2px solid var(--line); background:#EFE7D2; display:block; }
    .detail-card .detail-name{ font-size:17px; font-weight:800; margin-top:10px; }
    .detail-card .detail-role{ font-size:12px; color:var(--ink-soft); margin-bottom:8px; }
    .detail-stats{ display:grid; grid-template-columns:repeat(3,1fr); gap:6px; margin:8px 0 10px; }
    .detail-stats .stat-box{ background:#fff; border:1.5px solid var(--line); border-radius:10px; padding:8px 4px; text-align:center; }
    .detail-stats .stat-box .stat-label{ font-size:9px; color:var(--ink-soft); }
    .detail-stats .stat-box .stat-val{ font-size:14px; font-weight:800; }
    .detail-equip-row{ display:flex; justify-content:space-between; background:#fff; border:1.5px solid var(--line);
                        border-radius:10px; padding:7px 10px; margin-bottom:5px; font-size:11px; }
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
    .shop-row button:disabled{ opacity:.4; cursor:not-allowed; }
    .shop-row.dice-locked{ opacity:.45; }
    .shop-row.dice-current{ border-color:var(--gold); background:var(--gold-soft); }

    /* 미착용 장비도 동료 카드처럼 드래그해서 파티 슬롯에 놓으면 그 동료에게 장착됨.
       버튼(장착/합성)은 그대로 남겨둬서 드래그 없이도 쓸 수 있게 함.
       직업별로 묶고 그 안에서 성급 내림차순 정렬, 카드 한 칸이 화면 폭을 다 먹던 걸
       party-grid처럼 여러 칸으로 배치되는 작은 카드로 줄임(개수 많아지면 스캔하기 쉽게). */
    .equip-group-title{ font-size:11px; font-weight:800; color:var(--ink-soft); margin:10px 0 6px; }
    .equip-group-title:first-child{ margin-top:0; }
    .equip-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(108px,1fr)); gap:8px; }
    .equip-card{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:8px;
                 font-size:11px; text-align:center; touch-action:none; user-select:none; cursor:grab; }
    .equip-card .eq-part{ font-size:18px; }
    .equip-card .eq-grade{ font-weight:800; margin:2px 0 6px; }
    .equip-card .btn-group{ display:flex; gap:4px; justify-content:center; }
    .equip-card button{ background:var(--gold); color:#fff; border:none; border-radius:8px;
                         padding:4px 6px; font-size:10px; cursor:pointer; }
    .equip-card button.ten{ background:var(--shop); }
    .equip-card.drag-ghost{ position:fixed; z-index:999; pointer-events:none; opacity:.9; box-shadow:0 8px 20px rgba(0,0,0,.3);
                             width:108px; cursor:grabbing; }
    .equip-card.drag-source-hidden{ opacity:.3; }

    .ach-row{ display:flex; gap:8px; align-items:flex-start; background:#fff; border:1.5px solid var(--line);
              border-radius:12px; padding:8px 12px; margin-bottom:6px; font-size:12px; }
    .ach-row.done{ background:#fffbe8; border-color:var(--gold); }

    .msg-toast{ position:fixed; left:50%; bottom:96px; transform:translateX(-50%); background:var(--ink); color:#fff;
                padding:12px 18px; border-radius:12px; font-size:12px; line-height:1.6; max-width:min(92vw,420px);
                text-align:left; z-index:50; box-shadow:0 6px 18px rgba(0,0,0,.3); display:none; white-space:pre-line; }

    .dock{ position:fixed; left:0; right:0; bottom:0; display:flex; justify-content:center;
           padding:10px 14px calc(10px + env(safe-area-inset-bottom));
           background:linear-gradient(180deg,rgba(251,243,223,0),var(--parchment) 22%); }
    .dock-inner{ max-width:900px; width:100%; display:flex; gap:8px; background:#fff;
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

    /* 층이동: 부루마불 보드 옆에 세우는 "탑" 모양 -- 9층(보스)이 맨 위, 0층(마을)이 맨 아래에
       오도록 DOM을 9->0 순서로 채워서 실제 탑처럼 보이게 한다. 칸 하나가 그 층 하나. */
    .tower-nav{ display:flex; flex-direction:column; gap:5px; max-width:320px; margin:0 auto; }
    .tower-floor{ display:flex; align-items:center; justify-content:space-between; gap:8px;
                  padding:9px 14px; border:2px solid var(--line); border-radius:10px; background:#fff;
                  cursor:pointer; font-size:12px; font-weight:700; color:var(--ink); }
    .tower-floor:active{ transform:scale(.97); }
    .tower-floor .tf-left{ display:flex; align-items:center; gap:8px; }
    .tower-floor .tf-num{ width:22px; height:22px; border-radius:50%; background:var(--parchment-deep);
                           display:flex; align-items:center; justify-content:center; font-size:11px;
                           flex-shrink:0; }
    .tower-floor .tf-kind{ font-size:10px; color:var(--ink-soft); font-weight:600; }
    .tower-floor .tf-explored{ font-size:10px; color:var(--pp); font-weight:800; }
    .tower-floor.here{ border-color:var(--gold); background:var(--gold-soft); }
    .tower-floor.here .tf-num{ background:var(--gold); color:#fff; }
    .tower-floor.village{ border-left:5px solid var(--village); }
    .tower-floor.boss{ border-left:5px solid var(--boss); }
    .tower-floor.locked{ opacity:.4; cursor:not-allowed; }

    .confirm-card{ max-width:280px; text-align:center; }
    .confirm-msg{ font-size:14px; margin:6px 0 18px; line-height:1.6; }
    .confirm-btns{ display:flex; gap:10px; }
    .confirm-btns button{ flex:1; padding:11px; border-radius:12px; border:none; font-size:13px;
                            font-weight:800; cursor:pointer; }
    .confirm-btns .btn-yes{ background:var(--gold); color:#fff; }
    .confirm-btns .btn-no{ background:#fff; border:1.5px solid var(--line); color:var(--ink-soft); }

    @media (max-width:600px){
      body{ padding-top:8px; }
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
      <div class="tower-viewport" id="towerViewport">
        <div class="tower-track" id="towerTrack">
          <div style="color:var(--ink-soft);font-size:12px;">데이터를 불러오는 중...</div>
        </div>
      </div>
      <div class="legend">
        <span class="legend-chip"><span class="legend-dot" style="background:var(--combat)"></span>전투</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--treasure)"></span>💎 보물상자</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--pp)"></span>🍀 럭키</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--trap)"></span>함정</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--special)"></span>특수</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--elite)"></span>💪 강화몬스터</span>
        <span class="legend-chip"><span class="legend-dot" style="background:var(--gold)"></span>계단</span>
        <span class="legend-chip"><span class="legend-dot" style="background:#D8CDB4"></span>미발견</span>
      </div>
    </div>
    <div class="card" id="autoHuntCard" style="margin-top:10px; display:none;">
      <div class="card-title">🔥 자동사냥</div>
      <div id="autoHuntBox" style="font-size:12px; color:var(--ink-soft); line-height:1.7;"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">🗼 층 이동 (눌러서 이동, 탐사완료 층엔 ✅ 표시)</div>
      <div class="tower-nav" id="towerNav"></div>
    </div>
  </div>

  <!-- 파티(동료 3명)와 그 동료들에게 장착하는 장비는 한 화면에서 같이 관리 -->
  <div class="panel" id="panel-party">
    <div class="card">
      <div class="card-title-row">
        <div class="card-title">파티 (동료 카드를 드래그해서 넣기/빼기/자리교환, 탭해도 편성/해제됨)</div>
        <button type="button" class="btn-unassign-all" onclick="TW.action('PARTY_UNASSIGN_ALL')">일괄해제</button>
      </div>
      <div class="party-slots" id="partySlots"></div>
    </div>
    <div class="card" id="ticketCard" style="margin-top:10px; display:none;">
      <div class="card-title">🎁 완전탐사 선택권 (10층 구간 앞/뒤 4개층 전부 완전탐사 시 지급, 직업을 골라 확정 획득)</div>
      <div id="ticketBox"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">보유 동료 (위 파티 칸으로 드래그하거나, 카드를 탭해서 편성/해제)</div>
      <div class="party-grid" id="partyGrid"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">파티 장비 현황</div>
      <div id="partyEquipBox"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">미착용 장비 (드래그해서 파티 슬롯에 장착, 또는 클릭으로 장착/합성)</div>
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
      <div class="card-title">🎲 주사위 (해금된 것끼리는 몇 번이든 무료로 교체 가능)</div>
      <div id="diceListBox"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">📊 스탯 강화</div>
      <div id="statShopBox"></div>
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

<div class="detail-overlay" id="detailOverlay" onclick="if(event.target===this) TW.closeDetail();">
  <div class="detail-card">
    <button class="detail-close" onclick="TW.closeDetail()">✕</button>
    <img class="detail-img" id="detailImg" src="" alt="">
    <div class="detail-name" id="detailName">-</div>
    <div class="detail-role" id="detailRole">-</div>
    <div class="detail-stats">
      <div class="stat-box"><div class="stat-label">HP</div><div class="stat-val" id="detailHp">-</div></div>
      <div class="stat-box"><div class="stat-label">공격력</div><div class="stat-val" id="detailAtk">-</div></div>
      <div class="stat-box"><div class="stat-label">방어력</div><div class="stat-val" id="detailDef">-</div></div>
    </div>
    <div class="card-title" style="font-size:13px;">착용 장비</div>
    <div id="detailEquipBox"></div>
  </div>
</div>

<div class="detail-overlay" id="confirmOverlay" onclick="if(event.target===this) TW.closeConfirm();">
  <div class="detail-card confirm-card">
    <div class="confirm-msg" id="confirmMsg">정말 이동하시겠습니까?</div>
    <div class="confirm-btns">
      <button type="button" class="btn-no" onclick="TW.closeConfirm()">취소</button>
      <button type="button" class="btn-yes" id="confirmYesBtn">이동</button>
    </div>
  </div>
</div>

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

  var TILE_CLASS = { COMBAT: 'combat', TREASURE: 'treasure', PP: 'pp', TRAP: 'trap', SPECIAL: 'special', STAIRS: 'stairs', ELITE: 'elite' };
  var TILE_KR    = { COMBAT: '전투', TREASURE: '보물상자', PP: '럭키', TRAP: '함정', SPECIAL: '특수', STAIRS: '계단', ELITE: '강화몬스터' };
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

        // '편성' 탭(파티 슬롯 토글/장비 장착·합성)과 '상점' 탭(뽑기) 전부 전투 중만 아니면
        // 어디서든 가능하므로 탭 자체를 막지 않는다. inVillage는 아래 주사위 버튼(보드가
        // 없는 마을에서는 이동/전투가 의미 없음)에만 쓰인다.
        state.inVillage = (p.CUR_FLOOR % 10 === 0);
        updateDiceButtonState();
        renderTickets(p);

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

        renderBoard(data.tiles, data.myTile ? data.myTile.CUR_TILE : 0);
        renderTowerNav(p, data.floorBest);
      })
      .catch(function () { toast('조회 실패'); });
  }

  // 층이동을 셀렉트박스+버튼 대신 부루마불 옆에 세우는 "탑" 그림으로 -- 9층(보스)이 맨 위,
  // 0층(마을)이 맨 아래에 오도록 n=9부터 역순으로 그린다. 탐사완료(FULLY_EXPLORED_YN)된 사냥터
  // 층엔 ✅ 배지를 붙이고, 아직 한 번도 계단으로 못 간 층(alwaysFree 아니고 MAX_FLOOR_REACHED
  // 미만)은 흐리게 표시 + 눌러도 이동 대신 안내만 뜨게 한다(실제 검증은 서버 changeFloor()가
  // 최종적으로 하므로 여기 reachable 판정은 UX용 힌트일 뿐).
  function renderTowerNav(p, floorBest) {
    var nav = document.getElementById('towerNav');
    nav.innerHTML = '';
    var curFloor = p.CUR_FLOOR;
    var blockBase = curFloor - (curFloor % 10);
    var maxReached = p.MAX_FLOOR_REACHED || 0;
    var bestByFloor = {};
    (floorBest || []).forEach(function (b) { bestByFloor[b.FLOOR] = b; });

    for (var n = 9; n >= 0; n--) {
      var floor = blockBase + n;
      var isHere = (floor === curFloor);
      var alwaysFree = (n === 0) || (n === 1); // 마을<->그 구간 첫 사냥터층은 항상 자유 이동(서버 changeFloor와 동일 규칙)
      var reachable = alwaysFree || floor <= maxReached;
      var kind = n === 0 ? '마을' : (n === 9 ? '보스' : '사냥터');
      var best = bestByFloor[floor];
      var explored = best && best.FULLY_EXPLORED_YN === 'Y';

      var row = document.createElement('div');
      row.className = 'tower-floor'
          + (isHere ? ' here' : '')
          + (n === 0 ? ' village' : '') + (n === 9 ? ' boss' : '')
          + (reachable ? '' : ' locked');
      row.innerHTML = '<span class="tf-left"><span class="tf-num">' + n + '</span>'
          + '<span>' + floor + '층</span><span class="tf-kind">' + kind + '</span></span>'
          + (isHere ? '<span class="tf-explored" style="color:var(--gold);">📍 현재 위치</span>'
                    : (explored ? '<span class="tf-explored">✅ 탐사완료</span>' : ''));
      if (reachable && !isHere) {
        row.onclick = (function (targetFloor, targetN, targetKind) {
          return function () { confirmMove(targetFloor, targetN, targetKind); };
        })(floor, n, kind);
      } else if (!reachable) {
        row.onclick = function () { toast('아직 가본 적 없는 층입니다. 계단으로 먼저 올라가야 해요.'); };
      }
      nav.appendChild(row);
    }
  }

  function confirmMove(floor, n, kind) {
    document.getElementById('confirmMsg').textContent = floor + '층(' + kind + ')으로 정말 이동하시겠습니까?';
    document.getElementById('confirmYesBtn').onclick = function () {
      closeConfirm();
      action('CHANGE_FLOOR', String(n));
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  function closeConfirm() {
    document.getElementById('confirmOverlay').classList.remove('open');
  }

  // 사각형 둘레를 시계방향으로 도는 칸 배치(부루마불 스타일). S = 정사각형 한 변의 칸 수,
  // 둘레 칸 수 = 4*(S-1). N개를 다 담을 수 있는 가장 작은 S를 골라서 칸 수가 늘어나도(최대
  // 150칸) 자동으로 정사각형이 커지기만 하고 모양은 항상 정사각형 루프를 유지한다.
  function perimeterPos(i, side) {
    var per = side - 1; // 변 하나에 놓이는 칸 간격 수
    var s = Math.floor(i / per), o = i % per;
    if (s === 0) return { x: o, y: 0 };
    if (s === 1) return { x: side - 1, y: o };
    if (s === 2) return { x: side - 1 - o, y: side - 1 };
    return { x: 0, y: side - 1 - o };
  }

  function renderBoard(tiles, curTile) {
    var viewport = document.getElementById('towerViewport');
    var track = document.getElementById('towerTrack');
    track.innerHTML = '';
    if (!tiles || !tiles.length) {
      track.style.width = ''; track.style.height = '';
      track.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">이 층은 보드가 없습니다 (마을/보스층)</div>';
      return;
    }
    var n = tiles.length;
    var side = Math.max(2, Math.ceil(n / 4) + 1);
    var cell = 40, gap = 4, step = cell + gap;
    track.style.width = (side * step) + 'px';
    track.style.height = (side * step) + 'px';

    var hereEl = null;
    tiles.forEach(function (t, idx) {
      var pos = perimeterPos(idx, side);
      var div = document.createElement('div');
      var isHere = (t.TILE_NO === curTile);
      div.style.left = (pos.x * step) + 'px';
      div.style.top = (pos.y * step) + 'px';
      div.style.width = cell + 'px';
      div.style.height = cell + 'px';
      if (t.DISCOVERED) {
        div.className = 'tile ' + tileClass(t.TILE_TYPE) + (isHere ? ' here' : ' done');
        div.innerHTML = '<span class="tno">' + t.TILE_NO + '</span>' + (TILE_KR[t.TILE_TYPE] || t.TILE_TYPE);
      } else {
        // 방문한 적 없는 칸은 종류를 감추고 물음표만 표시(fog of war)
        div.className = 'tile hidden' + (isHere ? ' here' : '');
        div.innerHTML = '<span class="tno">' + t.TILE_NO + '</span>?';
      }
      track.appendChild(div);
      if (isHere) hereEl = div;
    });

    // 큰 보드는 화면보다 커서 스크롤이 필요하므로, 로드 시 현재 위치가 가운데 보이도록 스크롤
    if (hereEl) {
      viewport.scrollLeft = Math.max(0, hereEl.offsetLeft - viewport.clientWidth / 2 + cell / 2);
      viewport.scrollTop = Math.max(0, hereEl.offsetTop - viewport.clientHeight / 2 + cell / 2);
    }
  }

  var PART_KR   = { HELMET: '투구', WEAPON: '무기', ARMOR: '갑옷' };
  var PART_EMOJI = { HELMET: '⛑️', WEAPON: '⚔️', ARMOR: '🛡️' };
  var JOB_KR    = { WARRIOR: '전사', MAGE: '마법사', ROGUE: '도적', ARCHER: '궁수', PRIEST: '도사' };
  var JOB_ORDER = ['WARRIOR', 'MAGE', 'ROGUE', 'ARCHER', 'PRIEST']; // 장비 목록 직업별 그룹핑 순서
  // 초상화(IMAGE_URL)는 외부 API(nekos.best) 실패/차단 시 비어있을 수 있어 직업별 이모지로 항상 얼굴이 보이게 폴백
  var JOB_EMOJI = { WARRIOR: '⚔️', MAGE: '🧙', ROGUE: '🗡️', ARCHER: '🏹', PRIEST: '💫' };

  // 동료 카드 드래그 편성: Pointer Events(마우스/터치 공용) 기반. 이동량이 작으면 탭으로 취급해
  // 기존처럼 즉시 토글하고, 일정 거리 이상 끌면 드래그로 취급한다. 미편성 동료를 파티 슬롯 영역
  // (#partySlots) 아무데나 놓으면 편성(서버가 다음 빈 슬롯 자동 배정)되고, 이미 편성된 동료를
  // 놓으면 기본은 해제(토글)지만 -- [신규] 드롭 위치가 자기 자신이 아닌 "다른" 구체적인 슬롯
  // 박스(.party-slot-box) 위라면 그 슬롯과 자리를 맞바꾼다("동료끼리 위치변경" 요청으로 추가,
  // PARTY_SWAP). 자기 슬롯 위나 슬롯 경계가 아닌 빈 여백에 놓으면 기존처럼 해제로 처리(빠르게
  // 빼고 싶을 때 쓰던 기존 제스처를 그대로 남겨둠). 별도 "해제 존"을 화면 하단에 두면 고정 dock
  // 내비게이션과 겹쳐서 드롭이 안 먹는 경우가 있어(실제 테스트로 발견) 파티 슬롯 영역 하나로 통일함.
  function attachPartyDrag(card, idx, inParty, partySlot) {
    card.addEventListener('pointerdown', function (ev) {
      if (ev.target.closest('.hide-btn')) return;
      if (ev.target.closest('.avatar')) return; // 초상화 클릭은 드래그/토글이 아니라 확대 카드 열기(아래 avatarEl.onclick)
      var startX = ev.clientX, startY = ev.clientY;
      var moved = false, ghost = null;

      function onMove(mv) {
        var dx = mv.clientX - startX, dy = mv.clientY - startY;
        if (!moved && Math.hypot(dx, dy) > 10) {
          moved = true;
          card.classList.add('drag-source-hidden');
          ghost = card.cloneNode(true);
          ghost.className = 'party-card drag-ghost';
          document.body.appendChild(ghost);
          document.querySelectorAll('.party-slot-box').forEach(function (b) { b.classList.add('drop-hover'); });
        }
        if (moved && ghost) {
          ghost.style.left = (mv.clientX - 60) + 'px';
          ghost.style.top = (mv.clientY - 40) + 'px';
        }
      }

      function onUp(up) {
        document.removeEventListener('pointermove', onMove);
        document.removeEventListener('pointerup', onUp);

        if (!moved) {
          card.classList.remove('drag-source-hidden');
          action('PARTY_TOGGLE', String(idx)); // 단순 탭 -- 기존과 동일하게 토글
          return;
        }
        // 드롭 위치 판정은 반드시 슬롯을 숨기기(drop-hover 제거) 전에 해야 한다 -- 먼저 숨기면
        // 레이아웃/표시가 바뀌어 elementFromPoint가 엉뚱한 걸 찾을 수 있음.
        var el = document.elementFromPoint(up.clientX, up.clientY);
        var slotBox = el && el.closest('.party-slot-box');
        var onZone = el && el.closest('#partySlots');

        card.classList.remove('drag-source-hidden');
        document.querySelectorAll('.party-slot-box').forEach(function (b) { b.classList.remove('drop-hover'); });
        if (ghost) document.body.removeChild(ghost);

        // 이미 편성된 동료를 자기 것이 아닌 "다른" 구체적인 슬롯 위에 놓으면 자리 교환/이동(신규).
        if (inParty && slotBox) {
          var targetSlot = parseInt(slotBox.dataset.slot, 10);
          if (targetSlot && targetSlot !== partySlot) {
            action('PARTY_SWAP', String(idx), String(targetSlot));
            return;
          }
        }
        if (onZone) {
          action('PARTY_TOGGLE', String(idx)); // 있으면 해제, 없으면 편성
        }
        // 그 외의 곳에 놓으면 아무 일도 없었던 것처럼 원위치(다음 loadPartyAndEquip에서 그대로 다시 그려짐)
      }

      document.addEventListener('pointermove', onMove);
      document.addEventListener('pointerup', onUp, { once: true });
    });
  }

  // 미착용 장비 카드를 파티 슬롯(#partySlots 안의 개별 .party-slot-box)으로 드래그하면 그
  // 슬롯의 동료에게 장착된다(EQUIP_WEAR param2=슬롯번호). attachPartyDrag와 같은 Pointer
  // Events 패턴이되, 드롭 대상이 "슬롯 전체 영역"이 아니라 "슬롯 하나"라는 점만 다르다.
  // 탭(이동 없음)은 버튼(장착/합성)이 이미 있으니 별도 동작 없이 무시한다.
  function attachEquipDrag(card, idx) {
    card.addEventListener('pointerdown', function (ev) {
      if (ev.target.closest('button')) return;
      var startX = ev.clientX, startY = ev.clientY;
      var moved = false, ghost = null;

      function onMove(mv) {
        var dx = mv.clientX - startX, dy = mv.clientY - startY;
        if (!moved && Math.hypot(dx, dy) > 10) {
          moved = true;
          card.classList.add('drag-source-hidden');
          ghost = card.cloneNode(true);
          ghost.className = 'equip-card drag-ghost';
          document.body.appendChild(ghost);
          document.querySelectorAll('.party-slot-box.filled').forEach(function (b) { b.classList.add('drop-hover'); });
        }
        if (moved && ghost) {
          ghost.style.left = (mv.clientX - 120) + 'px';
          ghost.style.top = (mv.clientY - 24) + 'px';
        }
      }

      function onUp(up) {
        document.removeEventListener('pointermove', onMove);
        document.removeEventListener('pointerup', onUp);
        if (!moved) return; // 탭은 버튼으로만 동작(카드 자체는 드래그 전용)

        var el = document.elementFromPoint(up.clientX, up.clientY);
        var slotBox = el && el.closest('.party-slot-box.filled');
        var slot = slotBox ? slotBox.dataset.slot : null;

        card.classList.remove('drag-source-hidden');
        document.querySelectorAll('.party-slot-box').forEach(function (b) { b.classList.remove('drop-hover'); });
        if (ghost) document.body.removeChild(ghost);

        if (slot) {
          action('EQUIP_WEAR', String(idx), slot); // 특정 슬롯(동료)에 직접 장착
        }
      }

      document.addEventListener('pointermove', onMove);
      document.addEventListener('pointerup', onUp, { once: true });
    });
  }

  // 캐릭터 확대/상세 카드에서 쓰려고 마지막으로 불러온 파티·장비 데이터를 기억해둔다
  // (모달을 열 때마다 다시 fetch하지 않고, 스탯만 별도로 조회).
  var lastParty = { companions: [], byCompanion: {} };

  function showCompanionDetail(companionId) {
    var c = lastParty.companions.filter(function (x) { return x.COMPANION_ID === companionId; })[0];
    if (!c) return;
    var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
    var detailImg = document.getElementById('detailImg');
    detailImg.onerror = function () { detailImg.style.display = 'none'; };
    detailImg.src = c.IMAGE_URL || '';
    detailImg.style.display = c.IMAGE_URL ? '' : 'none';
    document.getElementById('detailName').textContent = name;
    document.getElementById('detailRole').textContent = (JOB_KR[c.CLASS] || c.CLASS) + ' ★' + c.GRADE
        + (c.PARTY_SLOT ? ' · 파티 ' + c.PARTY_SLOT + '번' : ' · 대기중');
    document.getElementById('detailHp').textContent = '-';
    document.getElementById('detailAtk').textContent = '-';
    document.getElementById('detailDef').textContent = '-';

    var mine = lastParty.byCompanion[companionId] || [];
    var eqBox = document.getElementById('detailEquipBox');
    eqBox.innerHTML = '';
    ['HELMET', 'WEAPON', 'ARMOR'].forEach(function (part) {
      var found = mine.filter(function (e) { return e.PART === part; })[0];
      var row = document.createElement('div');
      row.className = 'detail-equip-row';
      row.innerHTML = '<span>' + PART_KR[part] + '</span><span>' + (found ? '★' + found.GRADE : '미착용') + '</span>';
      eqBox.appendChild(row);
    });

    document.getElementById('detailOverlay').classList.add('open');

    var u = userName();
    fetch(base + '/api/tower-companion-stat?userName=' + encodeURIComponent(u) + '&companionId=' + companionId)
      .then(function (r) { return r.json(); })
      .then(function (data) {
        document.getElementById('detailHp').textContent = data.hp != null ? data.hp : '-';
        document.getElementById('detailAtk').textContent = data.atk != null ? data.atk : '-';
        document.getElementById('detailDef').textContent = data.def != null ? data.def : '-';
      })
      .catch(function () {});
  }

  function closeDetail() {
    document.getElementById('detailOverlay').classList.remove('open');
  }

  // 10층 구간 완전탐사 보상(★3 선택권)은 채팅 명령어 없이 이 웹 화면에서만 쓸 수 있다.
  // /api/tower-status가 내려주는 progress(p)에 COMPANION_CHOICE_TICKET/WEAPON_CHOICE_TICKET
  // 이 이미 포함돼 있어서(BotS5Mapper selectUserProgress) 별도 API 없이 바로 씀.
  // 선택권 등급(3/4/5)은 그 선택권을 지급한 10층 구간(블록)에 따라 이미 정해져 있어서
  // (블록1~3=★3, 4~5=★4, 6~10=★5) 여러 등급을 동시에 들고 있을 수 있다 -- 등급별로 줄을 나눠 표시.
  function renderTickets(p) {
    var compByGrade = { 3: p.COMPANION_CHOICE_TICKET || 0, 4: p.COMPANION_CHOICE_TICKET_G4 || 0, 5: p.COMPANION_CHOICE_TICKET_G5 || 0 };
    var weapByGrade = { 3: p.WEAPON_CHOICE_TICKET || 0, 4: p.WEAPON_CHOICE_TICKET_G4 || 0, 5: p.WEAPON_CHOICE_TICKET_G5 || 0 };
    var totalTix = compByGrade[3] + compByGrade[4] + compByGrade[5] + weapByGrade[3] + weapByGrade[4] + weapByGrade[5];
    var card = document.getElementById('ticketCard');
    var box = document.getElementById('ticketBox');
    if (totalTix <= 0) { card.style.display = 'none'; return; }
    card.style.display = '';
    box.innerHTML = '';

    function jobPickerRow(label, count, actionType, grade) {
      if (count <= 0) return;
      var row = document.createElement('div');
      row.className = 'shop-row';
      row.style.flexWrap = 'wrap';
      var head = document.createElement('span');
      head.textContent = label + ' ' + count + '장';
      row.appendChild(head);
      var picker = document.createElement('span');
      picker.className = 'btn-group';
      picker.style.flexWrap = 'wrap';
      JOB_ORDER.forEach(function (job) {
        var btn = document.createElement('button');
        btn.textContent = JOB_KR[job];
        btn.onclick = function () { action(actionType, job, String(grade)); };
        picker.appendChild(btn);
      });
      row.appendChild(picker);
      box.appendChild(row);
    }

    [3, 4, 5].forEach(function (g) { jobPickerRow('★' + g + ' 동료 선택권', compByGrade[g], 'REDEEM_COMPANION_TICKET', g); });
    [3, 4, 5].forEach(function (g) { jobPickerRow('★' + g + ' 무기 선택권', weapByGrade[g], 'REDEEM_WEAPON_TICKET', g); });
  }

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

      // 장착된 장비를 동료별로 묶기 -- 파티 슬롯(아래)에서 "낀 장비"를 같이 보여주려면 슬롯을
      // 그리기 전에 먼저 계산해둬야 한다.
      var byCompanion = {};
      var unequipped = [];
      equips.forEach(function (e) {
        if (e.EQUIPPED_COMPANION_ID != null) {
          (byCompanion[e.EQUIPPED_COMPANION_ID] = byCompanion[e.EQUIPPED_COMPANION_ID] || []).push(e);
        } else {
          unequipped.push(e);
        }
      });
      lastParty = { companions: companions, byCompanion: byCompanion }; // 캐릭터 상세 카드(showCompanionDetail)용 캐시

      // 장비 부위 표시 순서(무기→투구→갑옷) -- 파티 슬롯 요약과 "파티 장비 현황" 둘 다 동일하게 씀
      var PART_ORDER = ['WEAPON', 'HELMET', 'ARMOR'];

      // 파티 슬롯(최대 3) 표시 -- 실제 어느 슬롯 번호에 넣을지는 서버가 정하므로(PARTY_TOGGLE이
      // 항상 비어있는 다음 슬롯에 자동 배정) 여기 3칸은 "드롭하면 편성됨"을 보여주는 용도.
      // 채워진 슬롯엔 낀 장비 요약과, 그 동료 한 명만 파티에서 빼는 "해제" 버튼도 같이 보여준다.
      var slotsBox = document.getElementById('partySlots');
      slotsBox.innerHTML = '';
      var bySlot = {};
      companions.forEach(function (c) { if (c.PARTY_SLOT) bySlot[c.PARTY_SLOT] = c; });
      for (var s = 1; s <= 3; s++) {
        var slotEl = document.createElement('div');
        var occ = bySlot[s];
        slotEl.className = 'party-slot-box' + (occ ? ' filled' : '');
        slotEl.dataset.slot = String(s); // 장비 드래그 드롭 시 "몇 번 파티원에게 장착할지" 판별용
        if (occ) {
          var occMine = byCompanion[occ.COMPANION_ID] || [];
          var gearText = PART_ORDER.map(function (part) {
            var found = occMine.filter(function (e) { return e.PART === part; })[0];
            return (PART_EMOJI[part] || '') + (found ? '★' + found.GRADE : '-');
          }).join(' ');
          var occIdx = companions.indexOf(occ) + 1; // PARTY_TOGGLE(해제)이 참조하는 "N번째 동료" 번호
          slotEl.innerHTML = '<div class="slot-label">파티 ' + s + '</div>'
              + '<div class="cname">' + (occ.NAME || JOB_KR[occ.CLASS] || occ.CLASS) + '</div>'
              + '<div class="role">' + (JOB_KR[occ.CLASS] || occ.CLASS) + ' ★' + occ.GRADE + '</div>'
              + '<div class="slot-equip">' + gearText + '</div>'
              + '<button type="button" class="slot-unassign" onclick="event.stopPropagation();TW.action(\'PARTY_TOGGLE\',\'' + occIdx + '\')">해제</button>';
        } else {
          slotEl.innerHTML = '<div class="slot-label">파티 ' + s + '</div><div>빈 슬롯</div>';
        }
        slotsBox.appendChild(slotEl);
      }

      // 동료 목록 + 편성 토글 (드래그 또는 탭 둘 다 지원 -- Pointer Events라 마우스/터치 공용).
      // [정렬 고정] 서버가 내려주는 순서는 PARTY_SLOT NULLS LAST, COMPANION_ID라 편성/해제할
      // 때마다 카드가 앞뒤로 튀어서 헷갈린다는 신고로, 화면 표시 순서만 COMPANION_ID(뽑은 순서)
      // 고정으로 다시 정렬한다 -- 단, /파티편성 N 텍스트 명령어·PARTY_TOGGLE 등이 참조하는 idx는
      // 서버가 내려준 원본 순서 그대로 써야 하므로 표시 순서와 별도로 유지한다.
      var displayOrder = companions.map(function (c, i) { return { c: c, idx: i + 1 }; })
          .sort(function (a, b) { return a.c.COMPANION_ID - b.c.COMPANION_ID; });
      var grid = document.getElementById('partyGrid');
      grid.innerHTML = '';
      displayOrder.forEach(function (entry) {
        var c = entry.c, idx = entry.idx;
        var hidden = c.HIDDEN_YN === 'Y';
        var inParty = !!c.PARTY_SLOT;
        var div = document.createElement('div');
        div.className = 'party-card' + (inParty ? ' inparty' : '') + (hidden ? ' hidden' : '');
        var img = c.IMAGE_URL ? c.IMAGE_URL : '';
        var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
        var emoji = JOB_EMOJI[c.CLASS] || '👤';
        div.innerHTML = '<div class="cname">' + name + '</div>'
            + '<div class="role">' + (JOB_KR[c.CLASS] || c.CLASS) + ' ★' + c.GRADE + '</div>'
            + '<div class="hpbar-track"><div class="hpbar-fill" style="width:100%"></div></div>'
            + '<div class="hp-num">HP ' + fmtPP(c.CUR_HP_VALUE, c.CUR_HP_EXT) + (c.PARTY_SLOT ? ' [파티' + c.PARTY_SLOT + ']' : '') + '</div>';
        // 이미지가 있으면 <img>를 쓰되, 로드 실패(차단/404 등) 시 직업 이모지로 교체.
        // 초상화를 누르면 확대 + 착용장비/스탯 상세 카드가 뜬다(드래그/편성 토글과는 별개 동작 --
        // attachPartyDrag 쪽에서 .avatar 클릭은 걸러내고 있음). outerHTML로 교체하면 핸들러가
        // 날아가므로, 이미지 로드 실패 시엔 새 엘리먼트를 만들어 직접 바꿔치기한다.
        var avatarClick = function (ev) { ev.stopPropagation(); showCompanionDetail(c.COMPANION_ID); };
        var avatarEl = document.createElement(img ? 'img' : 'div');
        avatarEl.className = 'avatar' + (img ? '' : ' avatar-emoji');
        avatarEl.onclick = avatarClick;
        if (img) {
            avatarEl.src = img;
            avatarEl.alt = '';
            avatarEl.onerror = function () {
                var fallback = document.createElement('div');
                fallback.className = 'avatar avatar-emoji';
                fallback.textContent = emoji;
                fallback.onclick = avatarClick;
                avatarEl.replaceWith(fallback);
            };
        } else {
            avatarEl.textContent = emoji;
        }
        div.insertBefore(avatarEl, div.firstChild);
        var hideBtn = document.createElement('button');
        hideBtn.className = 'hide-btn';
        hideBtn.type = 'button';
        hideBtn.textContent = hidden ? '👀' : '🙈';
        hideBtn.title = hidden ? '목록에 다시 표시' : '텍스트 목록(/파티편성)에서 숨기기';
        hideBtn.onclick = function (ev) { ev.stopPropagation(); action('COMPANION_HIDE', String(idx)); };
        div.appendChild(hideBtn);
        attachPartyDrag(div, idx, inParty, c.PARTY_SLOT || 0);
        grid.appendChild(div);
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
        var parts = PART_ORDER.map(function (part) {
          var found = mine.filter(function (e) { return e.PART === part; })[0];
          return PART_KR[part] + (found ? ' ★' + found.GRADE : ' 미착용');
        }).join(' / ');
        var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
        var hasAnyEquip = mine.length > 0;
        row.innerHTML = '<span>[파티' + c.PARTY_SLOT + '] ' + name + ' (' + (JOB_KR[c.CLASS] || c.CLASS) + ' ★' + c.GRADE + ') — ' + parts + '</span>'
            + (hasAnyEquip
                ? '<span class="btn-group"><button class="ten" onclick="TW.action(\'EQUIP_UNWEAR_ALL\',\'' + c.PARTY_SLOT + '\')">전체해제</button></span>'
                : '');
        partyBox.appendChild(row);
      });

      // 미착용 장비 목록 -- 위 파티 슬롯으로 드래그하면 그 동료에게 장착되고(attachEquipDrag),
      // 버튼으로 자동배정 장착/합성도 그대로 가능(드래그가 번거로운 경우를 위해 남겨둠).
      // idx(N번)는 서버(BotS5Service.equipWear/equipSynthesis)가 계산하는 "미착용 장비
      // 번호"와 반드시 같은 순서여야 하므로, 정렬/그룹핑은 화면 표시용으로만 하고 idx 자체는
      // API가 내려준 원본 순서(unequipped 배열 인덱스)를 그대로 쓴다.
      unequipped.forEach(function (e, i) { e.__idx = i + 1; });
      var grouped = {};
      unequipped.forEach(function (e) { (grouped[e.CLASS] = grouped[e.CLASS] || []).push(e); });
      // [정렬 고정] 그룹(직업) 순서를 "그 직업이 가진 최고 성급 내림차순"으로 뒀더니 장비를 하나
      // 장착/합성할 때마다 각 직업의 최고 성급이 바뀌면서 그룹 자체가 계속 자리를 바꿔 헷갈린다는
      // 신고로, 항상 고정 순서(JOB_ORDER: 전사→마법사→도적→궁수→도사)로 되돌림. 그룹 안의 카드는
      // 그대로 성급 내림차순 유지.
      var jobsInOrder = JOB_ORDER.filter(function (job) { return grouped[job]; });

      var box = document.getElementById('equipListBox');
      box.innerHTML = '';
      if (unequipped.length === 0) {
        box.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">미착용 장비가 없습니다.</div>';
      }
      jobsInOrder.forEach(function (job) {
        var list = grouped[job].slice().sort(function (a, b) { return b.GRADE - a.GRADE; });
        var title = document.createElement('div');
        title.className = 'equip-group-title';
        title.textContent = (JOB_KR[job] || job) + ' (' + list.length + ')';
        box.appendChild(title);

        var grid = document.createElement('div');
        grid.className = 'equip-grid';
        list.forEach(function (e) {
          var idx = e.__idx;
          var card = document.createElement('div');
          card.className = 'equip-card';
          card.innerHTML = '<div class="eq-part">' + (PART_EMOJI[e.PART] || '🎽') + '</div>'
              + '<div class="eq-grade">' + (PART_KR[e.PART] || e.PART) + ' ★' + e.GRADE + '</div>'
              + '<div class="btn-group">'
              + '<button onclick="TW.action(\'EQUIP_WEAR\',\'' + idx + '\')">장착</button>'
              + '<button class="ten" onclick="TW.action(\'EQUIP_SYNTH\',\'' + idx + '\')">합성</button>'
              + '</div>';
          attachEquipDrag(card, idx);
          grid.appendChild(card);
        });
        box.appendChild(grid);
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
        var voucherByTier = data.companionVoucherByTier || [0, 0, 0, 0]; // [티어1(하급)..티어4(최상급)]

        var chipText = [];
        if (starterFree > 0) chipText.push('튜토리얼 무료 ' + starterFree + '회');
        if (companionVoucher > 0) chipText.push('동료뽑기권 ' + companionVoucher + '장');
        var tierNames = ['하급', '중급', '상급', '최상급'];
        voucherByTier.forEach(function (n, i) {
          if (n > 0) chipText.push(tierNames[i] + ' 전용 동료뽑기권 ' + n + '장');
        });
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
          var isFree = (gi === 0 && starterFree > 0) || companionVoucher > 0 || (voucherByTier[gi] || 0) > 0;
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

        // 주사위: 해금된 것끼리 자유롭게 교체(무료). 사용중인 건 강조, 미해금은 흐리게 + 몇 층부터인지.
        var diceBox = document.getElementById('diceListBox');
        diceBox.innerHTML = '';
        (data.dice || []).forEach(function (d) {
          var row = document.createElement('div');
          row.className = 'shop-row' + (d.current ? ' dice-current' : '') + (!d.unlocked ? ' dice-locked' : '');
          var label = d.name + (d.current ? ' (사용중)' : '') + (!d.unlocked ? ' — ' + d.unlockFloor + '층부터 해금' : '');
          row.innerHTML = '<span>' + label + '</span>'
              + (d.unlocked && !d.current
                  ? '<span class="btn-group"><button onclick="TW.action(\'DICE_BUY\',\'' + d.idx + '\')">장착</button></span>'
                  : '');
          diceBox.appendChild(row);
        });

        // 스탯 강화: 현재 Lv/상한 + 다음 비용, 상한 도달 시 버튼 비활성화. 다음 상한이 몇층에서
        // 열리는지도 하단에 안내("어떤 마을 가면 몇까지 올릴 수 있는지" 요청).
        var st = data.stat || {};
        var statBox = document.getElementById('statShopBox');
        statBox.innerHTML = '';
        var statDefs = [
          { key: 'atkMaxLv', label: '공격력(최대)', cost: st.nextCostAtkMax, type: '공격력' },
          { key: 'atkMinLv', label: '공격력(최소)', cost: st.nextCostAtkMin, type: '최소공격력' },
          { key: 'hpLv',     label: '체력',         cost: st.nextCostHp,     type: '체력' }
        ];
        statDefs.forEach(function (def) {
          var lv = st[def.key] || 0;
          var cap = st.cap || 0;
          var maxed = lv >= cap;
          var row = document.createElement('div');
          row.className = 'shop-row';
          row.innerHTML = '<span>' + def.label + ' Lv' + lv + ' / ' + cap
              + (maxed ? ' (상한 도달)' : ' (' + def.cost + 'PP)') + '</span>'
              + '<span class="btn-group"><button' + (maxed ? ' disabled' : '')
              + ' onclick="TW.action(\'STAT_BUY\',\'' + def.type + '\')">강화</button></span>';
          statBox.appendChild(row);
        });
        if (st.nextVillageFloor != null) {
          var note = document.createElement('div');
          note.style.cssText = 'font-size:11px;color:var(--ink-soft);margin-top:6px;';
          note.textContent = '📈 다음 상한 ' + st.nextCap + '은 ' + st.nextVillageFloor + '층 마을 도달 시 열립니다(그 앞 보스 처치 필요).';
          statBox.appendChild(note);
        }
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
        var list = data.achievements || [];
        if (list.length === 0) {
          box.innerHTML = '<div class="ach-row">아직 달성한 업적이 없습니다</div>';
          return;
        }
        list.forEach(function (a) {
          var row = document.createElement('div');
          row.className = 'ach-row done';
          row.innerHTML = '<span>✅</span><span>' + a.ACH_NAME + ' - ' + a.ACH_DESC + '</span>';
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
    // 채팅에서 "/탑현황"·"/탑도움말"이 붙여주는 링크(?userName=닉네임)로 들어오면 그 유저로
    // 바로 조회되게 한다. 파라미터 이름이 userName이 아니어도(user_name, username 등) 웬만하면
    // 알아서 찾도록 흔한 변형을 다 확인 -- 못 찾으면 예전처럼 sessionStorage에 저장된 값으로 폴백.
    var qs = new URLSearchParams(window.location.search);
    var fromUrl = qs.get('userName') || qs.get('user_name') || qs.get('username') || qs.get('name');
    var saved = fromUrl || sessionStorage.getItem('loaUserName');
    if (saved) document.getElementById('userNameInput').value = saved;
    if (saved) loadStatus();
  });

  return { load: loadStatus, action: action, switchTab: switchTab, closeDetail: closeDetail, closeConfirm: closeConfirm };
})();
</script>
</body>
</html>
