<%@ page pageEncoding="UTF-8" %><%--
  [2026-11-03] 시즌 5.5 -- 광산 게임 화면 마크업(캔버스/HUD/버튼). tower_view.jsp와 광산 웹앱(s55_mine.jsp)이 정적 포함하고, TowerMine(mine.js)이 이 id들을 쓴다.
--%>
<div class="mine-overlay" id="mineOverlay">
  <canvas id="mineCanvas"></canvas>
  <div class="mine-patch" id="minePatch" role="status" aria-live="polite"></div>
  <div class="mine-hud-tl"><div class="mine-ping mpx" id="minePing"></div><div class="mine-luck mpx" id="mineLuck"></div><div class="mine-terr" id="mineTerr"></div></div><%-- [2026-11-05] 좌표는 지도 아래로, 핑은 100ms 이상일 때만, 행운의 주문서는 따로 --%>
  <div class="mine-hud-tr">
    <div class="mine-cold mpx" id="mineCold"></div>
    <div class="mine-regen" id="mineRegen"></div>
    <canvas id="mineMini" width="255" height="255" onclick="TowerMine.toggleMini()"></canvas>
    <div class="mine-coord mpx" id="mineCoord" onclick="TowerMine.openWarp()">📍 0, 0</div>
    <button type="button" class="mine-world-btn mpx" onclick="TowerMine.openWorld()">🗺️ 전체 지도</button><%-- [2026-11-05] 지도를 크게 했을 때만 보인다(CSS) --%>
  </div>
  <div class="mine-hp mpx" id="mineHp"></div>
  <div class="mine-log mpx" id="mineLog"></div>
  <!-- [2026-10-28] 왼쪽 버튼 다섯 개를 ☰ 메뉴 하나로 -->
  <button type="button" class="mine-btn mine-menu-btn mpx" id="mineMenuBtn" onclick="TowerMine.toggleMenu()" aria-expanded="false" aria-controls="mineMenu">☰ 메뉴</button>
  <div class="mine-menu mpx" id="mineMenu">
    <button type="button" class="mine-menu-item mpx" onclick="TowerMine.menuGo('equip')">🎒 인벤토리</button>
    <button type="button" class="mine-menu-item mpx" id="mineMercMenu" style="display:none" onclick="TowerMine.menuGo('merc')">🛡️ 용병 관리</button>
    <button type="button" class="mine-menu-item mpx" onclick="TowerMine.menuGo('home')">🏠 마을로</button>
    <button type="button" class="mine-menu-item mpx" onclick="TowerMine.menuGo('log')">📜 최근 기록</button>
    <%-- [2026-11-19] 화면 크기(확대/축소 +-2단계, 눌러도 메뉴는 안 닫힌다) --%>
    <div class="mine-menu-item mpx" style="display:flex;align-items:center;gap:6px;cursor:default">🔍 화면 <button type="button" class="mpx" style="min-width:28px;padding:2px 0;cursor:pointer" onclick="TowerMine.zoom(-1)" aria-label="화면 축소">－</button><span id="mineZoomLv" style="min-width:30px;text-align:center">보통</span><button type="button" class="mpx" style="min-width:28px;padding:2px 0;cursor:pointer" onclick="TowerMine.zoom(1)" aria-label="화면 확대">＋</button></div>
    <%-- [2026-11-20] 캐릭터 그림 16x16 / 64x64 --%>
    <div class="mine-menu-item mpx" style="display:flex;align-items:center;gap:6px;cursor:default">🧍 캐릭터 <button type="button" class="mpx" style="padding:2px 6px;cursor:pointer" onclick="TowerMine.charRes(false)">16×16</button><button type="button" class="mpx" style="padding:2px 6px;cursor:pointer" onclick="TowerMine.charRes(true)">64×64</button><span id="mineCharRes" style="min-width:44px;text-align:center;opacity:.8">64×64</span></div>
    <%-- [2026-11-20] 데미지 숫자: 속성별 / 합산 --%>
    <div class="mine-menu-item mpx" style="display:flex;align-items:center;gap:6px;cursor:default">💥 데미지 <button type="button" class="mpx" style="padding:2px 6px;cursor:pointer" onclick="TowerMine.dmgSum(false)">속성별</button><button type="button" class="mpx" style="padding:2px 6px;cursor:pointer" onclick="TowerMine.dmgSum(true)">합산</button><span id="mineDmgSum" style="min-width:40px;text-align:center;opacity:.8">속성별</span></div>
    <button type="button" class="mine-menu-item mpx" onclick="TowerMine.menuGo('exit')">🚪 나가기</button>
  </div>
  <button type="button" class="mine-greet mpx" id="mineGreetBtn" onclick="TowerMine.greet()">👋 안녕하세요</button>
  <%-- [2026-11-07] 필살기 -- 한 버튼을 반으로(왼쪽 만검진 / 오른쪽 일섬참, 쿨타임 공유), 관리자는 위에 쿨 초기화 --%>
  <%-- [2026-11-08] "반 나뉜 것 말고 태극 모양, 비스듬히, X쪽 파랑 C쪽 빨강(음양 색)" -- 두 조각이 각자 눌린다. 아이콘 그림 img_loa/s55/fx/ult_icons.png(64x64 두 칸: 칼 세 자루 / 斬) --%>
  <%-- [2026-11-21] 삼태극(시안 승인) -- 위 = 각성(V, 노란 불꽃), 왼쪽 아래 = 만검진(X), 오른쪽 아래 = 일섬참(C) --%><div class="mine-ult" id="mineUlt" style="display:none"><svg viewBox="-4 -4 108 112" aria-hidden="false"><defs><clipPath id="ultClipA"><rect x="16.95" y="55.95" width="34.1" height="34.1"/></clipPath><clipPath id="ultClipB"><rect x="60.95" y="35.95" width="34.1" height="34.1"/></clipPath><radialGradient id="ultGoldG" cx="50%" cy="35%" r="65%"><stop offset="0" stop-color="#f6c63a"/><stop offset="1" stop-color="#b8780a"/></radialGradient><linearGradient id="ultFlameG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff38a"/><stop offset="1" stop-color="#ffb31a"/></linearGradient></defs><circle cx="50" cy="55" r="50" class="sh"/><path d="M50,50 A25,25 0 0,1 19.2,89.38 A50,50 0 0,1 31.29,3.63 A25,25 0 0,0 50,50 Z" class="ya" onclick="TowerMine.ult(0)"><title>만검진 (광역 필살, X)</title></path><path d="M50,50 A25,25 0 0,1 99.51,56.98 A50,50 0 0,1 19.2,89.38 A25,25 0 0,0 50,50 Z" class="yb" onclick="TowerMine.ult(1)"><title>일섬참 (단일 필살, C)</title></path><path d="M50,50 A25,25 0 0,1 31.29,3.63 A50,50 0 0,1 99.51,56.98 A25,25 0 0,0 50,50 Z" class="yc" onclick="TowerMine.ult(2)"><title>각성 (피해 2배·체력 2배, V)</title></path><path d="M50,50 A25,25 0 0,1 31.29,3.63 M50,50 A25,25 0 0,1 99.51,56.98 M50,50 A25,25 0 0,1 19.2,89.38" class="sc"/><circle cx="50" cy="50" r="49" class="rim"/><image href="<%=request.getContextPath()%>/img_loa/s55/fx/ult_icons.png?v=1" x="16.95" y="55.95" width="68.2" height="34.1" clip-path="url(#ultClipA)" preserveAspectRatio="none" class="ia"/><image href="<%=request.getContextPath()%>/img_loa/s55/fx/ult_icons.png?v=1" x="26.85" y="35.95" width="68.2" height="34.1" clip-path="url(#ultClipB)" preserveAspectRatio="none" class="ib"/><g class="ic" transform="translate(38,26) scale(34.1)"><path d="M0,-0.62 C0.1,-0.42 0.34,-0.3 0.36,-0.02 C0.38,0.22 0.22,0.38 0,0.4 C-0.22,0.38 -0.38,0.22 -0.36,-0.02 C-0.35,-0.18 -0.24,-0.26 -0.18,-0.4 C-0.12,-0.28 -0.06,-0.24 -0.04,-0.3 C-0.04,-0.42 -0.06,-0.52 0,-0.62 Z" fill="#ffd21a" stroke="#8a3a00" stroke-width="0.09" stroke-linejoin="round"/><path d="M0,-0.62 C0.1,-0.42 0.34,-0.3 0.36,-0.02 C0.38,0.22 0.22,0.38 0,0.4 C-0.22,0.38 -0.38,0.22 -0.36,-0.02 C-0.35,-0.18 -0.24,-0.26 -0.18,-0.4 C-0.12,-0.28 -0.06,-0.24 -0.04,-0.3 C-0.04,-0.42 -0.06,-0.52 0,-0.62 Z" fill="url(#ultFlameG)" transform="scale(.8) translate(0,.06)"/><path d="M0,-0.16 C0.08,-0.06 0.18,0.02 0.17,0.14 C0.16,0.26 0.08,0.32 0,0.32 C-0.08,0.32 -0.17,0.26 -0.17,0.14 C-0.17,0.04 -0.08,-0.04 0,-0.16 Z" fill="#fffbe0"/></g><text x="34" y="73" class="lk lka">🔒</text><text x="78" y="53" class="lk lkb">🔒</text><text x="38" y="25" class="lk lkc">🔒</text><g class="ultkey"><rect x="28" y="85.1" width="12" height="11" rx="2"/><text x="34" y="94.1">X</text><rect x="72" y="65.1" width="12" height="11" rx="2"/><text x="78" y="74.1">C</text><rect x="32" y="37.1" width="12" height="11" rx="2"/><text x="38" y="46.1">V</text></g></svg><span class="cd" id="mineUltCd" style="display:none"></span></div>
  <button type="button" class="mine-ult-rst" id="mineUltRst" style="display:none" onclick="TowerMine.ultReset()">⟳ 쿨 초기화</button>
  <button type="button" class="mine-atk" id="mineAtkBtn" onclick="TowerMine.atkBtn()" aria-label="무기로 공격" title="무기로 공격 (스페이스)">⚔️</button>
  <button type="button" class="mine-dig" id="mineDigBtn" onclick="TowerMine.digBtn()" aria-label="캐기" title="캐기 (Z)">⛏️<span class="dig-n" id="mineSta"></span></button>
  <button type="button" class="mine-auto-btn mpx" id="mineAutoBtn" onclick="TowerMine.toggleAuto()">🤖 자동채광</button>
  <div class="mine-msg mpx" id="mineMsg"></div>
  <div class="mine-smith mpx" id="mineSmith"></div>
  <div class="mine-loading" id="mineLoading">광산으로 가는 중...</div>
</div>
