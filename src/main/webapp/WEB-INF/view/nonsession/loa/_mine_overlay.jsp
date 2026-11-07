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
    <button type="button" class="mine-menu-item mpx" onclick="TowerMine.menuGo('exit')">🚪 나가기</button>
  </div>
  <button type="button" class="mine-greet mpx" id="mineGreetBtn" onclick="TowerMine.greet()">👋 안녕하세요</button>
  <%-- [2026-11-07] 필살기 -- 한 버튼을 반으로(왼쪽 만검진 / 오른쪽 일섬참, 쿨타임 공유), 관리자는 위에 쿨 초기화 --%>
  <div class="mine-ult" id="mineUlt" style="display:none"><button type="button" class="l" onclick="TowerMine.ult(0)" aria-label="만검진" title="만검진 (광역 필살, X)">🌧️<i>X</i></button><button type="button" class="r" onclick="TowerMine.ult(1)" aria-label="일섬참" title="일섬참 (단일 필살, C)">💥<i>C</i></button><span class="cd" id="mineUltCd" style="display:none"></span></div>
  <button type="button" class="mine-ult-rst" id="mineUltRst" style="display:none" onclick="TowerMine.ultReset()">⟳ 쿨 초기화</button>
  <button type="button" class="mine-atk" id="mineAtkBtn" onclick="TowerMine.atkBtn()" aria-label="무기로 공격" title="무기로 공격 (스페이스)">⚔️</button>
  <button type="button" class="mine-dig" id="mineDigBtn" onclick="TowerMine.digBtn()" aria-label="캐기" title="캐기 (Z)">⛏️<span class="dig-n" id="mineSta"></span></button>
  <button type="button" class="mine-auto-btn mpx" id="mineAutoBtn" onclick="TowerMine.toggleAuto()">🤖 자동채광</button>
  <div class="mine-msg mpx" id="mineMsg"></div>
  <div class="mine-smith mpx" id="mineSmith"></div>
  <div class="mine-loading" id="mineLoading">광산으로 가는 중...</div>
</div>
