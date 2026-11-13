<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%><%--
  [2026-10-28] tower_view.jsp에서 떼어 낸 웹 광산 스크립트(TowerMine) + 캐릭터 그림(_mine_char.jsp).
  tower_view.jsp가 커져서(약 8,400줄) Tomcat이 JSP를 한 메서드(_jspService)로 만들 때 자바 메서드 64KB 한계를 넘어 페이지가 500이 났다.
  tower_view.jsp가 <jsp:include page="_tower_mine.jsp" />(동적 포함 -- 따로 컴파일되는 별개 서블릿)로 붙인다. 정적 포함(<%@ include %>)으로 바꾸면 다시 한 메서드에 합쳐지니 안 된다.
--%>
<%@ include file="_mine_char.jsp" %>
<script>
/* [2026-10-23] 웹 광산(공용 맵 여러 개, 2D 도트) -- 서버 BotS5ServiceImpl.mine*, 맵 파일 s5/*.txt(S5MineMap).
 * 지형/포탈은 서버가 준 문자열(모든 유저 같은 맵, 맵별 version으로 브라우저에 저장)을 16x16 도트 타일로 그린다. 광석은 캐 봐야 안다(서버만 앎).
 * 이동: 방향키/WASD 또는 화면 끌기(누른 곳 기준 방향으로 계속 이동). 캐기: ⛏️ 버튼/스페이스 -- 지금 서 있는 칸(한 번 캔 칸은 다시 못 캠).
 * 포탈 칸(지역 통로/동굴 입구/나가는 길/지하 문)으로 걸어 들어가면 서버가 대상 맵으로 옮겨 준다(잠긴 곳은 안내만).
 * 캐릭터 위치는 이동 중 몇 초마다, 캘 때, 포탈을 탈 때, 나갈 때(sendBeacon) 저장한다. 서버가 위치를 거절하면 서버 위치로 되돌린다. */
var TowerMine = (function () {
  var T = 16, STEP_MS = 140, SAVE_MS = 2500; // [2026-10-24] 위치 저장 4초 -> 2.5초(걷는 중에도 저장)
  // [2026-10-23] 사막(d 모래, R 사암, C 선인장, 모래바람), 바닷가(b 모래사장, P 부두, q 배, B 배에 오르는 판자), NPC(N: 대장장이/선장)
  var WALK = '.,:VHcrdbPTsnYupe', PORTALS = 'GMUSBXZ'; // [2026-10-24] 나무(T)도 지나갈 수 있다(광석은 없음), s 성 바닥 / n 눈밭 / Y 눈 덮인 소나무
  var WIND_MS = 900; // 모래바람에 한 칸 밀리는 간격(가만히 있을 때)
  var ORE_NAME = ['', '구리 광석', '철광석', '은광석', '금광석', '미스릴 원석', '별빛석'];
  var ORE_COL = [null, ['#b8652e', '#f0a066'], ['#7d8790', '#c9d1d8'], ['#cfd9e3', '#ffffff'], ['#f2b705', '#fff1a1'], ['#20c9b8', '#b9fff6'], ['#f23fd0', '#ffffff']];
  // [2026-10-23] 요일 던전의 돌(등급 값 11~17 = 월~일): 월 은빛 달, 화 붉은 불, 수 푸른 물, 목 초록 나무, 금 금빛, 토 흙빛, 일 주황 해
  ORE_NAME[11] = '월요석'; ORE_NAME[12] = '화요석'; ORE_NAME[13] = '수요석'; ORE_NAME[14] = '목요석'; ORE_NAME[15] = '금요석'; ORE_NAME[16] = '토요석'; ORE_NAME[17] = '일요석';
  ORE_COL[11] = ['#b9c6ff', '#ffffff']; ORE_COL[12] = ['#ff4b2e', '#ffc0a8']; ORE_COL[13] = ['#2f8cff', '#b8dcff']; ORE_COL[14] = ['#33b34a', '#c4f5c8'];
  ORE_COL[15] = ['#ffcf26', '#fff6b8']; ORE_COL[16] = ['#a8693a', '#e8c09a']; ORE_COL[17] = ['#ff8c12', '#ffe0a0'];
  ORE_NAME[18] = '칠요석'; ORE_COL[18] = ['#9b5cff', '#ffffff']; // [2026-10-24] 요일석 7종 합성
  // [2026-10-24] 보석(등급 값 21~29): 재스퍼, 루비, 스타 사파이어, 아쿠아마린, 토파즈, 가넷, 스피넬, 에메랄드, 다이아몬드
  ['재스퍼', '루비', '스타 사파이어', '아쿠아마린', '토파즈', '가넷', '스피넬', '에메랄드', '다이아몬드'].forEach(function (n, i) { ORE_NAME[21 + i] = n; });
  [['#a8452f', '#e8907a'], ['#d4104f', '#ff9ab8'], ['#2448b8', '#bcd0ff'], ['#4fc8d8', '#d4fbff'], ['#f0a530', '#ffe2a8'],
   ['#7d1426', '#e0697c'], ['#e0508e', '#ffc4dc'], ['#159a4c', '#a8f0c4'], ['#cfefff', '#ffffff']].forEach(function (c, i) { ORE_COL[21 + i] = c; });
  var RARE_GEM = { 22: 1, 23: 1, 26: 1, 28: 1, 29: 1, 31: 1 }; // 루비, 스타 사파이어, 가넷, 에메랄드, 다이아몬드(나오면 번쩍), [2026-10-28] 타락한 대주교의 인장
  // [2026-10-28] 특수 재료: 빙석(19, 설산), 타락한 대주교의 인장(31, 성당 옛터) -- 성유물 조각(30)은 없앴다
  ORE_NAME[19] = '빙석'; ORE_COL[19] = ['#7fd0f0', '#eafaff'];
  ORE_NAME[31] = '타락한 대주교의 인장'; ORE_COL[31] = ['#7a2a8a', '#f0c8ff'];
  ORE_NAME[20] = '달빛석'; ORE_COL[20] = ['#8fa8e8', '#f4f8ff']; // [2026-10-28] 다도해 -- 1인용 배 재료
  // [2026-10-28] 다도해 바다 보석(본섬/일곱 섬에서 캘 때) -- 진주/산호석/자수정/오팔/흑진주
  ORE_NAME[34] = '진주'; ORE_COL[34] = ['#f4eee4', '#ffffff']; ORE_NAME[35] = '산호석'; ORE_COL[35] = ['#ff7a8a', '#ffd0d8']; ORE_NAME[36] = '자수정'; ORE_COL[36] = ['#9a5ad8', '#e0c8ff'];
  ORE_NAME[37] = '오팔'; ORE_COL[37] = ['#8ad8d0', '#fff0f8']; ORE_NAME[38] = '흑진주'; ORE_COL[38] = ['#3a3a4a', '#b8b8d0'];
  ORE_NAME[32] = '닌자의 비급서'; ORE_COL[32] = ['#3a2a1a', '#e8d8a8'];
  ORE_NAME[33] = '얼음의 심장'; ORE_COL[33] = ['#4fc8f0', '#f0fcff']; RARE_GEM[33] = 1; // [2026-10-28] 설산 봉우리 얼음 여왕 보상
  // [2026-10-29] 장비 등급(일반 흰색 / 고급 초록 / 희귀 파랑 / 영웅 보라 / 전설 주황 / 유물 빨강 / 고대 은색 / 신화 무지개) -- 플레이어는 색으로만 안다
  var GRADE_NAME = ['일반', '고급', '희귀', '영웅', '전설', '유물', '고대', '신화']; // [2026-11-05] 무기 드랍 표시(서버 GRADE_NAMES와 같은 순서)
  var GRADE_COL = ['#ffffff', '#4fd16a', '#4a9eff', '#b56cff', '#ff9a2e', '#ff4a4a', '#c8d0dc', '#ffffff'];
  function gradeName(nm, g) { return g == null ? nm : '<span class="gr gr' + g + '"' + (g < 6 ? ' style="color:' + GRADE_COL[g] + '"' : '') + '>' + nm + '</span>'; }
  // [2026-10-29] 속성(서버 S5MineMobs.EL_* 순서): 무, 땅, 번개, 물, 얼음, 바람, 불, 어둠, 빛
  // [2026-10-29] 개체 단계(0 하급 / 1 보통 / 2 상급) -- 서버 S5MineMobs.RANK_*와 같은 값. 상급 = ⭐ + 주황 이름, 하급 = 회색 이름
  var RANK_HP = [0.75, 1, 1.4], RANK_LV = [-2, 0, 3];
  var ELEM_ICON = ['', '🪨', '⚡', '💧', '❄️', '🌪️', '🔥', '🌑', '☀️'], ELEM_NAME = ['무', '땅', '번개', '물', '얼음', '바람', '불', '어둠', '빛'];
  // [2026-10-29] "구리~미스릴, 빙석, 요일석, 보석, 바다 보석, 괴 말고 다른 게 나오면 이펙트를 조금 더" -- 별빛석/달빛석/칠요석/세공품/고유 증표
  function specialDrop(g) { return !((g >= 1 && g <= 5) || g === 19 || (g >= 11 && g <= 17) || (g >= 21 && g <= 29) || (g >= 34 && g <= 38) || (g >= 40 && g <= 44)); }
  ORE_NAME[60] = '닌자의 검 조각'; ORE_COL[60] = ['#4a4f5c', '#d8dee8']; // [2026-10-29] 같은 등급 이하 드랍 무기 자동 분해(60~63)
  ORE_NAME[61] = '비철도 조각'; ORE_COL[61] = ['#6a5a4a', '#d8c8b0']; ORE_NAME[62] = '카타나 조각'; ORE_COL[62] = ['#5a2a2a', '#f0c8c8']; ORE_NAME[63] = '백화검 조각'; ORE_COL[63] = ['#c8c8d8', '#ffffff'];
  ORE_NAME[66] = '사슬 갑옷 조각'; ORE_COL[66] = ['#6a7078', '#d0d6de']; ORE_NAME[67] = '무사의 흉갑 조각'; ORE_COL[67] = ['#7a2a24', '#e8b0a0']; // [2026-11-06] 갑옷 조각(66~69)
  ORE_NAME[68] = '닌자의 갑주 조각'; ORE_COL[68] = ['#2a2e3a', '#9aa4c0']; ORE_NAME[69] = '백화 갑주 조각'; ORE_COL[69] = ['#d8d8e8', '#fff8e0'];
  ORE_NAME[70] = '은하수 조각'; ORE_COL[70] = ['#1a1e4a', '#c8d8ff']; RARE_GEM[70] = 1; // [2026-11-06] 달빛석 + 칠요석
  ORE_NAME[39] = '사막의 심장'; ORE_COL[39] = ['#e8a23a', '#fff0c0']; RARE_GEM[39] = 1; // [2026-10-28] 피라미드 보상 상자
  ORE_NAME[65] = '번개의 심장'; ORE_COL[65] = ['#2a6ad8', '#ffe94a']; RARE_GEM[65] = 1; // [2026-11-05] 천둥 황소
  ORE_NAME[71] = '바람의 심장'; ORE_COL[71] = ['#3ac8a0', '#f0fff8']; RARE_GEM[71] = 1; // [2026-11-11] 하피 여왕
  ORE_NAME[64] = '화산의 심장'; ORE_COL[64] = ['#d8341a', '#ffd08a']; RARE_GEM[64] = 1; // [2026-10-30] 용암 거신(낮)
  // [2026-10-28] 괴(40~44 = 구리~미스릴, 광석 전문가), 세공된 보석(45~53, 보석 세공사)
  ['동괴', '철괴', '은괴', '금괴', '미스릴괴'].forEach(function (n, i) { ORE_NAME[40 + i] = n; ORE_COL[40 + i] = ORE_COL[1 + i]; });
  for (var cgi = 0; cgi < 9; cgi++) { ORE_NAME[45 + cgi] = '세공된 ' + ORE_NAME[21 + cgi]; ORE_COL[45 + cgi] = ORE_COL[21 + cgi]; RARE_GEM[45 + cgi] = 1; }
  // [2026-10-28] 세공된 빙석(54), 세공된 바다 보석(55~59)
  ORE_NAME[54] = '세공된 빙석'; ORE_COL[54] = ORE_COL[19]; RARE_GEM[54] = 1;
  for (var sgi = 0; sgi < 5; sgi++) { ORE_NAME[55 + sgi] = '세공된 ' + ORE_NAME[34 + sgi]; ORE_COL[55 + sgi] = ORE_COL[34 + sgi]; RARE_GEM[55 + sgi] = 1; }
  // 세공 목록 [원재료 등급, 세공된 등급] -- 서버 번호(0~8 보석, 9 빙석, 10~14 바다 보석)와 같은 순서
  var CUT_LIST = [];
  for (var cli = 0; cli < 9; cli++) CUT_LIST.push([21 + cli, 45 + cli]);
  CUT_LIST.push([19, 54]);
  for (var clj = 0; clj < 5; clj++) CUT_LIST.push([34 + clj, 55 + clj]);
  var TERR_NAME = { '.': '풀밭', ',': '흙길', ':': '바위 지대', 'V': '마을', 'H': '다리', '~': '물', 'T': '숲', '^': '바위산',
                    'c': '동굴 바닥', 'r': '자갈 바닥', '#': '동굴 벽', 'h': '초가집', 'j': '초가집', 'k': '초가집', 'l': '초가집', 'G': '지역 통로', 'I': '얼음 광맥', 'F': '울창한 숲', 'M': '동굴 입구', 'U': '나가는 길', 'S': '지하로 가는 문',
                    'd': '모래', 'R': '사암', 'C': '선인장', 'b': '모래사장', 'P': '부두', 'q': '배', 'B': '배에 오르는 판자', 'N': 'NPC',
                    's': '성 바닥', 'W': '성벽', 'n': '눈밭', 'Y': '눈 덮인 숲', 'K': '건물', 'Q': '성당', 'X': '텔레포트 게이트', 'u': '바다 모래', 'p': '용궁 길', 'e': '해초', 'O': '산호', 'L': '용궁 기둥', 'Z': '물' };
  var MINI_COL = { 'F': [24, 58, 30], '.': [95, 174, 69], ',': [201, 164, 106], ':': [143, 133, 120], 'V': [232, 215, 168], 'H': [139, 90, 43], '~': [58, 120, 212],
                   'T': [47, 125, 50], '^': [90, 78, 68], 'c': [107, 91, 75], 'r': [125, 109, 92], '#': [30, 24, 20],
                   'h': [190, 140, 60], 'j': [190, 140, 60], 'k': [190, 140, 60], 'l': [190, 140, 60],
                   'd': [227, 196, 122], 'R': [184, 135, 90], 'C': [63, 143, 58], 'b': [236, 217, 160], 'P': [139, 90, 43], 'q': [107, 66, 32], 'N': [64, 120, 255],
                   's': [169, 164, 154], 'W': [74, 69, 64], 'n': [242, 246, 250], 'Y': [47, 93, 74], 'K': [168, 68, 58], 'Q': [223, 230, 242], 'X': [122, 92, 255], 'u': [111, 159, 192], 'p': [184, 72, 58], 'e': [47, 122, 106], 'O': [224, 122, 138], 'L': [138, 30, 30], 'Z': [58, 120, 212], 'I': [127, 208, 240] };

  var cfg = null, map = null, st = null, portalAt = {}, npcAt = {}, ship = null, lastWindT = 0, talkBubble = null, smithOpen = false;
  var el = {}, ctx = null, miniCtx = null, miniBase = null, scale = 3, dpr = 1;
  var tiles = null, sprites = null, hole = null, oreIcons = {};
  var P = { x: 0, y: 0, fx: 0, fy: 0, tx: 0, ty: 0, t0: 0, moving: false, dir: 'down', step: 0, digT0: 0 };
  var keys = [], tapDir = null, drag = null, dug = {}, recent = [], floats = [], flashT0 = 0, lastBump = '', lastBumpT = 0, exitSent = false;
  // [2026-10-23] 맵 이동/마을로 이동 화면 전환 -- 페이드 아웃(검게) -> 서버 이동 -> 새 맵으로 페이드 인 + 가운데 맵 이름.
  var FADE_OUT_MS = 380, FADE_IN_MS = 650, BANNER_MS = 1900;
  var fade = null, banner = null; // fade = { mode: 'out'|'in', t0 }, banner = { text, t0 }
  var raf = 0, opened = false, busy = false, lastSaved = '', saveTimer = 0, msgTimer = 0, bound = false;

  // ---------------------------------------------------------------- 도트 그리기
  function mk(w, h) { var c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  function rng(seed) {
    var s = (seed * 2654435761) >>> 0 || 1;
    return function () { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return (s % 100000) / 100000; };
  }
  function tile(base, specks, seed, extra) {
    var c = mk(T, T), g = c.getContext('2d'), r = rng(seed);
    g.fillStyle = base; g.fillRect(0, 0, T, T);
    (specks || []).forEach(function (sp) {
      g.fillStyle = sp[0];
      for (var i = 0; i < sp[1]; i++) g.fillRect(Math.floor(r() * T), Math.floor(r() * T), sp[2] || 1, sp[3] || 1);
    });
    if (extra) extra(g, r);
    return c;
  }
  function px(g, col, x, y, w, h) { g.fillStyle = col; g.fillRect(x, y, w || 1, h || 1); }
  function over(base, fn) { var c = mk(T, T), g = c.getContext('2d'); g.drawImage(base, 0, 0); fn(g); return c; }
  function grassTile(seed) { return tile('#5fae45', [['#4c9638', 16], ['#79c95a', 10], ['#3f8a2f', 5, 1, 2]], seed); }
  function cliffTile(seed) {
    return tile('#6d635a', [['#5a5149', 10]], 20 + seed, function (g, r) {
      for (var i = 0; i < 4; i++) {
        var x = Math.floor(r() * 10), y = Math.floor(r() * 10), w = 4 + Math.floor(r() * 4), h = 3 + Math.floor(r() * 3);
        px(g, '#8a7f74', x, y, w, h); px(g, '#a3998d', x, y, w, 1); px(g, '#4a423b', x, y + h, w, 1); px(g, '#4a423b', x + w, y, 1, h + 1);
      }
    });
  }
  function caveWall(seed) {
    return tile('#2a221c', [['#1d1713', 12]], 40 + seed, function (g, r) {
      for (var i = 0; i < 3; i++) {
        var x = Math.floor(r() * 11), y = Math.floor(r() * 11), w = 3 + Math.floor(r() * 4), h = 2 + Math.floor(r() * 3);
        px(g, '#3d3229', x, y, w, h); px(g, '#54453a', x, y, w, 1); px(g, '#151110', x, y + h, w, 1);
      }
    });
  }
  function outline(c, col) {
    var g = c.getContext('2d'), w = c.width, h = c.height, d = g.getImageData(0, 0, w, h), a = d.data, add = [];
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) {
      if (a[(y * w + x) * 4 + 3]) continue;
      var n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(function (v) {
        var nx = x + v[0], ny = y + v[1];
        return nx >= 0 && ny >= 0 && nx < w && ny < h && a[(ny * w + nx) * 4 + 3] > 0;
      });
      if (n) add.push([x, y]);
    }
    g.fillStyle = col;
    add.forEach(function (p) { g.fillRect(p[0], p[1], 1, 1); });
    return c;
  }
  function buildTiles() {
    var cliff = [cliffTile(1), cliffTile(2)], wall = [caveWall(1), caveWall(2)];
    tiles = {
      grass: [grassTile(1), grassTile(2), grassTile(3)],
      path: [tile('#c9a46a', [['#b48c55', 14], ['#dcbb82', 9], ['#9e7a48', 3]], 4), tile('#c9a46a', [['#b48c55', 14], ['#dcbb82', 9], ['#9e7a48', 3]], 5)],
      rock: [tile('#8f8578', [['#7a7064', 12], ['#a59b8d', 9], ['#5f574e', 4, 2, 1]], 6), tile('#8f8578', [['#7a7064', 12], ['#a59b8d', 9], ['#5f574e', 4, 2, 1]], 7)],
      village: tile('#d9c9a0', null, 8, function (g) {
        for (var y = 0; y < T; y += 4) for (var x = (y / 4) % 2 ? 2 : 0; x < T; x += 4) { px(g, '#c8b68a', x, y, 3, 3); px(g, '#b29f72', x + 2, y + 2, 1, 1); }
      }),
      bridge: tile('#8b5a2b', null, 9, function (g) {
        for (var y = 0; y < T; y += 4) { px(g, '#6b4220', 0, y + 3, T, 1); px(g, '#a8733d', 0, y, T, 1); px(g, '#3b2412', 3, y + 1); px(g, '#3b2412', 12, y + 1); }
      }),
      water: [0, 1].map(function (f) {
        return tile('#3a78d4', [['#2f63b3', 10]], 10 + f, function (g) {
          for (var y = 2 + f * 2; y < T; y += 5) { px(g, '#6aa4ef', (y * 3 + f * 5) % 12, y, 4, 1); px(g, '#6aa4ef', (y * 7 + 8 + f * 3) % 13, y + 2, 3, 1); }
        });
      }),
      tree: [1, 2].map(function (s) {
        var c = grassTile(s), g = c.getContext('2d');
        px(g, 'rgba(0,0,0,.25)', 3, 13, 10, 2);
        px(g, '#6b4220', 7, 10, 2, 5); px(g, '#4e2f16', 8, 10, 1, 5);
        for (var y = 1; y < 12; y++) for (var x = 2; x < 14; x++) {
          var dx = x - 7.5, dy = y - 6.2, d = dx * dx + dy * dy * 1.15;
          if (d < 30) px(g, d > 22 ? '#1b4d1f' : (dx < -1 && dy < -1 ? '#4fae52' : '#2f7d32'), x, y);
        }
        px(g, '#6fc86f', 5, 3, 2, 1); px(g, '#6fc86f', 4, 4, 1, 1);
        return c;
      }),
      // [2026-11-05] 울창한 숲(F, 지나갈 수 없음) -- 겹겹이 겹친 어두운 나뭇잎, 마물의 숲 던전 입구(M) -- 고목 사이 어두운 굴 + 가시덩굴(막힘)
      forest: [1, 2].map(function (s) {
        var c = tile('#16341b', [['#0f2613', 14], ['#1e4423', 8]], 40 + s), g = c.getContext('2d');
        [[4, 4, 5], [12, 3, 4], [8, 10, 5], [14, 12, 4], [1, 12, 4]].forEach(function (b, i) {
          var cx = (b[0] + s * 3) % T, cy = b[1];
          for (var y = cy - b[2]; y <= cy + b[2]; y++) for (var x = cx - b[2]; x <= cx + b[2]; x++) {
            var d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (x >= 0 && y >= 0 && x < T && y < T && d <= b[2] * b[2]) px(g, d > (b[2] - 1) * (b[2] - 1) ? '#0e2412' : (x < cx && y < cy ? '#3a7a3e' : (i % 2 ? '#245a2a' : '#2a6630')), x, y);
          }
        });
        return c;
      }),
      cliff: cliff,
      cave: [tile('#5b4d40', [['#4f4236', 14], ['#6a5a4b', 8]], 30), tile('#5b4d40', [['#4f4236', 14], ['#6a5a4b', 8]], 31)],
      gravel: tile('#665646', [['#7d6b58', 10, 2, 1], ['#4a3e33', 8], ['#8c7a66', 5]], 32),
      wall: wall,
      // 지역 통로(돌 아치) -- 잠김: 나무 바리케이드
      gate: [false, true].map(function (locked) {
        return over(cliff[0], function (g) {
          px(g, '#9a8f84', 1, 2, 3, 14); px(g, '#9a8f84', 12, 2, 3, 14); px(g, '#b5aa9e', 1, 1, 14, 3); px(g, '#7a7066', 1, 4, 14, 1);
          px(g, '#14100d', 4, 5, 8, 11);
          if (locked) { px(g, '#8b5a2b', 4, 7, 8, 2); px(g, '#8b5a2b', 4, 11, 8, 2); px(g, '#5a3a1c', 5, 7, 1, 6); px(g, '#5a3a1c', 10, 7, 1, 6); }
        });
      }),
      // 동굴 입구(바위산에 뚫린 굴 + 나무 버팀목)
      mouth: over(cliff[1], function (g) {
        for (var y = 3; y < 16; y++) for (var x = 2; x < 14; x++) {
          var dx = x - 7.5, dy = y - 13;
          if (dx * dx / 36 + dy * dy / 100 < 1) px(g, y > 12 ? '#1a1410' : '#0b0807', x, y);
        }
        px(g, '#7a4a1f', 2, 4, 2, 12); px(g, '#7a4a1f', 12, 4, 2, 12); px(g, '#8b5a2b', 1, 3, 14, 2); px(g, '#5a3a1c', 1, 5, 14, 1);
      }),
      // 나가는 길(위로 난 빛 + 사다리)
      exit: over(wall[0], function (g) {
        px(g, '#e8d9a0', 4, 0, 8, 16); px(g, '#fff4c8', 6, 0, 4, 16);
        px(g, '#7a4a1f', 4, 0, 1, 16); px(g, '#7a4a1f', 11, 0, 1, 16);
        for (var y = 1; y < 16; y += 3) px(g, '#9c6a35', 4, y, 8, 1);
      }),
      // 지하로 가는 문(잠김: 쇠띠 + 자물쇠)
      door: [false, true].map(function (locked) {
        return over(wall[1], function (g) {
          px(g, '#3a2a1c', 2, 1, 12, 15); px(g, '#7a4a1f', 3, 2, 10, 14); px(g, '#8b5a2b', 3, 2, 10, 1);
          px(g, '#5a3a1c', 7, 2, 1, 14); px(g, '#6b6f75', 3, 5, 10, 1); px(g, '#6b6f75', 3, 11, 10, 1);
          if (locked) { px(g, '#d9b44a', 6, 8, 4, 3); px(g, '#d9b44a', 7, 6, 2, 2); px(g, '#7a5c10', 7, 9, 2, 1); }
          else px(g, '#d9b44a', 11, 9, 1, 2);
        });
      })
    };
    tiles.house = thatchedHouse(tiles.village);
    // [2026-10-23] 사막/바닷가 타일
    tiles.sand = [1, 2].map(function (s) {
      return tile('#e3c47a', [['#d2ad5c', 12], ['#f0d896', 8]], 50 + s, function (g) {
        px(g, '#cfa857', 2 + s * 3, 5, 5, 1); px(g, '#cfa857', 8 - s, 11, 6, 1); // 바람 무늬
      });
    });
    tiles.sandstone = [1, 2].map(function (s) {
      return tile('#b8875a', [['#a3744a', 10]], 60 + s, function (g, r) {
        for (var i = 0; i < 4; i++) {
          var x = Math.floor(r() * 10), y = Math.floor(r() * 10), w = 4 + Math.floor(r() * 4), h = 3 + Math.floor(r() * 3);
          px(g, '#cf9d6a', x, y, w, h); px(g, '#e2b682', x, y, w, 1); px(g, '#8a5f39', x, y + h, w, 1);
        }
      });
    });
    tiles.cactus = over(tiles.sand[0], function (g) {
      px(g, 'rgba(0,0,0,.18)', 4, 14, 9, 2);
      px(g, '#2f7d32', 7, 2, 3, 13); px(g, '#4fae52', 7, 2, 1, 13);
      px(g, '#2f7d32', 3, 6, 2, 5); px(g, '#2f7d32', 3, 10, 4, 2);
      px(g, '#2f7d32', 12, 4, 2, 5); px(g, '#2f7d32', 10, 8, 4, 2);
      px(g, '#c8f0a0', 8, 4); px(g, '#c8f0a0', 8, 9); px(g, '#ff7fa8', 8, 1);
    });
    tiles.beach = [1, 2].map(function (s) { return tile('#ecd9a0', [['#dcc488', 12], ['#f8ecc4', 8], ['#c9b07a', 2]], 70 + s); });
    // [2026-10-28] 바닷길 입구(용왕의 반지를 얻은 뒤) -- 물 위 푸른 소용돌이(두 프레임)
    tiles.seaGate = [0, 1].map(function (f) {
      return over(tiles.water[f], function (g) {
        px(g, '#bfe8ff', 4, 4, 8, 1); px(g, '#bfe8ff', 3, 5, 1, 6); px(g, '#bfe8ff', 12, 5, 1, 6); px(g, '#bfe8ff', 4, 11, 8, 1);
        px(g, '#ffffff', 6, 6 + f, 4, 1); px(g, '#ffffff', 5, 7, 1, 2); px(g, '#ffffff', 10, 7, 1, 2); px(g, '#7fd0f0', 7, 8 - f, 2, 2);
      });
    });
    tiles.plank = [false, true].map(function (locked) {
      return over(tiles.water[0], function (g) {
        px(g, '#9c6a35', 0, 5, T, 6); px(g, '#7a4a1f', 0, 5, T, 1); px(g, '#7a4a1f', 0, 10, T, 1);
        px(g, '#5a3a1c', 4, 5, 1, 6); px(g, '#5a3a1c', 11, 5, 1, 6);
        if (locked) { px(g, '#d9d0b8', 0, 3, T, 1); px(g, '#d9d0b8', 0, 12, T, 1); px(g, '#b5251f', 6, 6, 4, 4); } // 밧줄 + 빨간 표시(못 오름)
      });
    });
    // [2026-10-24] 성곽(돌바닥 s, 성벽 W) / 설원(눈밭 n, 눈 덮인 소나무 Y, 얼음 호수) 타일
    tiles.stone = [1, 2].map(function (s) {
      return tile('#a9a49a', [['#97928a', 10], ['#bdb8ae', 6]], 80 + s, function (g) {
        for (var y = 0; y < T; y += 8) { px(g, '#87827a', 0, y + 7, T, 1); px(g, '#87827a', (y / 8) % 2 ? 4 : 11, y, 1, 7); }
      });
    });
    tiles.castleWall = [1, 2].map(function (s) {
      return tile('#4a4540', [['#3e3a35', 8]], 90 + s, function (g) {
        for (var y = 0; y < T; y += 4) {
          px(g, '#5e5852', 0, y, T, 1); px(g, '#2f2b28', 0, y + 3, T, 1);
          for (var x = ((y / 4) % 2) * 4; x < T; x += 8) px(g, '#2f2b28', x, y, 1, 4);
        }
      });
    });
    tiles.snow = [1, 2].map(function (s) { return tile('#eef3f8', [['#dbe5ef', 12], ['#ffffff', 8], ['#c9d6e3', 3]], 100 + s); });
    tiles.pine = [1, 2].map(function (s) {
      var c = tile('#eef3f8', [['#dbe5ef', 10]], 110 + s), g = c.getContext('2d');
      px(g, 'rgba(0,0,0,.18)', 4, 14, 9, 2);
      px(g, '#5a3a1c', 7, 12, 2, 3);
      for (var y = 1; y < 13; y++) {
        var half = 1 + ((y - 1) % 4) + Math.floor((y - 1) / 4);
        for (var x = 8 - half; x < 8 + half; x++) px(g, (y - 1) % 4 === 0 ? '#ffffff' : (x < 8 ? '#3d7a5e' : '#2f5d4a'), x, y);
      }
      return c;
    });
    // [2026-10-28] 설산 동굴 얼음 광맥(I) -- 어두운 바위 속에 솟은 푸른 결정
    tiles.vein = [1, 2].map(function (s) {
      return tile('#4e5a68', [['#3e4856', 10], ['#5e6a78', 6]], 140 + s, function (g) {
        px(g, '#7fd0f0', 2 + s, 6, 3, 9); px(g, '#d8f6ff', 3 + s, 6, 1, 7); px(g, '#9fe8ff', 7, 2, 4, 13); px(g, '#ecfbff', 8, 3, 1, 10);
        px(g, '#7fd0f0', 12, 8 - s, 2, 7); px(g, '#ffffff', 9, 4); px(g, '#ffffff', 4 + s, 8);
      });
    });
    // [2026-10-29] 빙석을 다 캔 광맥 -- 얼음 결정이 빠진 어두운 바위(금 간 자국만)
    tiles.veinDone = tile('#454c56', [['#3a4048', 10], ['#525a64', 6]], 147, function (g) { px(g, '#2e333a', 4, 5, 1, 7); px(g, '#2e333a', 9, 3, 1, 9); px(g, '#2e333a', 12, 7, 1, 5); px(g, '#5e7a8a', 8, 6); });
    tiles.ice = [1, 2].map(function (s) {
      return tile('#bfe3f5', [['#a8d4ec', 10], ['#e6f6ff', 6, 3, 1]], 120 + s, function (g) { px(g, '#ffffff', 3 + s, 4, 5, 1); px(g, '#8fc4e0', 9, 10 - s, 4, 1); });
    });
    // [2026-10-24] 왕도 아스테리아 -- 붉은 지붕 건물(K: 지붕 / 아래 칸이 길이면 벽면), 성당(Q: 청회색 지붕 / 흰 돌 벽면 + 아치 창), 텔레포트 게이트(X, 두 프레임)
    tiles.roof = [1, 2].map(function (s) {
      return tile('#a8443a', [['#93372f', 8]], 130 + s, function (g) {
        for (var y = 1; y < T; y += 4) { px(g, '#7d2c25', 0, y + 2, T, 1); for (var x = (y % 8 === 1 ? 0 : 2); x < T; x += 4) px(g, '#c4594d', x, y, 2, 1); }
      });
    });
    tiles.house2 = [1, 2].map(function (s) {
      return tile('#efe4c8', [['#e2d5b4', 6]], 140 + s, function (g) {
        px(g, '#7d2c25', 0, 0, T, 2); px(g, '#c9b98f', 0, 15, T, 1);
        if (s === 1) { px(g, '#4a2f18', 6, 7, 4, 8); px(g, '#7a5230', 7, 8, 2, 7); px(g, '#d9b44a', 8, 11); }
        else { px(g, '#3b5a7a', 3, 5, 4, 4); px(g, '#3b5a7a', 9, 5, 4, 4); px(g, '#efe4c8', 5, 5, 1, 4); px(g, '#efe4c8', 11, 5, 1, 4); }
      });
    });
    // [2026-10-27] "왕도 건물 높게, 대도시처럼" -- 높은 건물의 층(창 두 개가 난 벽 + 층 띠, 몇몇 창은 불이 켜져 있다)
    tiles.facade = [1, 2].map(function (s) {
      return tile('#e6d9b8', [['#d9caa3', 6]], 170 + s, function (g) {
        px(g, '#b8a47a', 0, 0, T, 1); px(g, '#cdbb90', 0, 15, T, 1);
        px(g, '#4a5a70', 2, 3, 4, 8); px(g, '#4a5a70', 10, 3, 4, 8);
        px(g, s === 2 ? '#ffd66b' : '#7aa0c8', 3, 4, 2, 6); px(g, s === 2 ? '#ffe9a8' : '#9cc0e0', 11, 4, 2, 6);
        px(g, '#8c7a58', 1, 11, 6, 1); px(g, '#8c7a58', 9, 11, 6, 1);
      });
    });
    tiles.church = [1, 2].map(function (s) {
      return tile('#6f7f99', [['#61708a', 8]], 150 + s, function (g) {
        for (var y = 2; y < T; y += 4) px(g, '#55637c', 0, y, T, 1);
        if (s === 2) { px(g, '#f2e6b0', 7, 2, 2, 10); px(g, '#f2e6b0', 4, 5, 8, 2); } // 십자가
      });
    });
    tiles.churchWall = [1, 2].map(function (s) {
      return tile('#eef1f6', [['#dfe4ec', 8]], 160 + s, function (g) {
        px(g, '#55637c', 0, 0, T, 2);
        px(g, '#7a9ccf', 5, 5, 6, 9); px(g, '#7a9ccf', 6, 4, 4, 1); px(g, '#c9daf2', 7, 6, 2, 7); // 아치 창(스테인드글라스)
      });
    });
    tiles.tele = [0, 1].map(function (f) {
      return over(tiles.stone[0], function (g) {
        for (var y = 0; y < T; y++) for (var x = 0; x < T; x++) {
          var d = Math.hypot(x - 7.5, y - 7.5);
          if (d < 7.5 && d > 6) px(g, f ? '#b9a8ff' : '#7a5cff', x, y);
          else if (d < 4.5 && d > 3.4) px(g, f ? '#7a5cff' : '#c6f0ff', x, y);
          else if (d < 2) px(g, '#ffffff', x, y);
        }
      });
    });
    // [2026-10-24] 바닷길(용궁) -- 바다 모래(u), 용궁 길(p, 붉은 판석 + 금 테), 해초(e), 산호(O), 용궁 기둥/성벽(L, 붉은 칠 + 금장식)
    tiles.seaSand = [1, 2].map(function (s) { return tile('#6f9fc0', [['#5f8fb0', 10], ['#8fbfdc', 8], ['#4f7f9f', 3]], 170 + s); });
    tiles.palace = [1, 2].map(function (s) {
      return tile('#b8483a', [['#a83c30', 6]], 180 + s, function (g) {
        px(g, '#e8c25a', 0, 0, T, 1); px(g, '#e8c25a', 0, 0, 1, T); px(g, '#7d2c25', 0, 15, T, 1); px(g, '#7d2c25', 15, 0, 1, T);
        if (s === 1) { px(g, '#e8c25a', 7, 7, 2, 2); }
      });
    });
    tiles.seaweed = [1, 2].map(function (s) {
      return over(tiles.seaSand[s - 1], function (g) {
        for (var k = 0; k < 3; k++) { var bx = 3 + k * 4 + (s - 1); for (var y = 4 + k; y < 15; y++) px(g, (y % 3) ? '#2f7a6a' : '#4fae8a', bx + ((y >> 1) % 2), y); }
      });
    });
    tiles.coral = [1, 2].map(function (s) {
      return over(tiles.seaSand[0], function (g) {
        var col = s === 1 ? '#e07a8a' : '#f0a050', dk = s === 1 ? '#a8485a' : '#b8702a';
        px(g, dk, 2, 9, 12, 6); px(g, col, 3, 8, 10, 5); px(g, col, 4, 3, 2, 6); px(g, col, 9, 2, 2, 7); px(g, col, 12, 5, 2, 4);
        px(g, '#ffd0d8', 4, 3); px(g, '#ffd0d8', 9, 2); px(g, '#ffd0d8', 12, 5);
      });
    });
    // [2026-10-29] "바닷길 용궁 입구를 낮은 건물로, 바닷속이라는 걸" -- 물에 잠긴 푸른 돌담(이끼 낀 청기와 + 따개비 + 해초 자락)
    tiles.palaceWall = [1, 2].map(function (s) {
      return tile('#3f6f7a', [['#356270', 8], ['#4f8a90', 5]], 190 + s, function (g) {
        px(g, '#2f8a9a', 0, 0, T, 3); px(g, '#5fc0c8', 0, 0, T, 1); px(g, '#1f4a55', 0, 14, T, 2); // 청기와 처마
        px(g, '#e8f4f0', 3, 9, 2, 1); px(g, '#e8f4f0', 11, 6, 1, 2); px(g, '#cfe8e0', 7, 11); // 따개비
        if (s === 1) { for (var y = 4; y < 14; y++) px(g, (y % 3) ? '#2f7a6a' : '#4fae8a', 13 + ((y >> 1) % 2), y); } // 해초 자락
        else px(g, '#e07a8a', 5, 5, 2, 2); // 산호 조각
      });
    });
    tiles.npc = { smith: npcSprite('smith'), captain: npcSprite('captain'), boutique: npcSprite('boutique'), priest: npcSprite('priest'), guide: npcSprite('guide'),
      blackmarket: blackSprite(), furrier: furrierSprite(), stall: stallSprite(), daykeeper: keeperSprite(), // [2026-10-26], [2026-10-28] 요일 동굴 문지기
      ninjaquest: npcSprite('ninjaquest'), boatwright: npcSprite('boatwright'), ferry: npcSprite('ferry'), chest: chestSprite(), antchest: chestSprite(), smelter: npcSprite('smelter'), gemcutter: npcSprite('gemcutter'), artisan: npcSprite('artisan'), archishop: npcSprite('archishop'), elementalist: npcSprite('gemcutter'), wallguard: keeperSprite(), swordmaster: npcSprite('artisan'), basecamp: furrierSprite(), forestguard: keeperSprite(), citymerchant: npcSprite('archishop'), mercenary: keeperSprite(), enhancer: npcSprite('smith'), whirlmaster: npcSprite('ferry'), ultmaster: npcSprite('ninjaquest') }; // [2026-10-28] 개미굴 보물 상자 // [2026-10-28] 피라미드 보상 상자 // [2026-10-28] 다도해 NPC(목록에 없으면 그림 없이 이름표만 나왔다)
    hole = mk(T, T);
    var hg = hole.getContext('2d');
    for (var y = 0; y < T; y++) for (var x = 0; x < T; x++) {
      var dx = x - 7.5, dy = (y - 8) * 1.3, d = dx * dx + dy * dy;
      if (d < 34) px(hg, d > 22 ? '#6b4a30' : (d > 14 ? '#3a281b' : '#1d140d'), x, y);
    }
    px(hg, '#8a6a4a', 2, 4, 2, 1); px(hg, '#8a6a4a', 12, 11, 2, 1);
  }
  // [2026-10-23] 마을 초가집(2x2 칸) -- 32x32로 한 채를 그려 16x16 네 조각(h 왼위, j 오위, k 왼아래, l 오아래)으로 나눈다. 바닥은 마을 타일.
  function thatchedHouse(floor) {
    var S = T * 2, c = mk(S, S), g = c.getContext('2d');
    px(g, 'rgba(0,0,0,.22)', 4, 28, 25, 3);                       // 그림자
    px(g, '#9a8f80', 4, 27, 24, 2);                               // 주춧돌
    px(g, '#e8dcc0', 6, 15, 20, 12);                              // 흙벽
    px(g, '#d6c8a8', 6, 15, 20, 1);
    px(g, '#6b4a2a', 6, 15, 2, 12); px(g, '#6b4a2a', 24, 15, 2, 12); px(g, '#6b4a2a', 6, 20, 20, 1); // 기둥/인방
    px(g, '#4a2f18', 13, 19, 6, 8); px(g, '#7a5230', 14, 20, 4, 7); px(g, '#4a2f18', 16, 20, 1, 7); px(g, '#d9b44a', 17, 23, 1, 1); // 문
    px(g, '#3b2a1a', 20, 21, 3, 3); px(g, '#cdbf9e', 21, 21, 1, 3); px(g, '#cdbf9e', 20, 22, 3, 1); // 창
    for (var y = 1; y <= 16; y++) {                               // 둥근 초가지붕
      var t = (16 - y) / 15, half = Math.round(15 * Math.sqrt(Math.max(0, 1 - t * t)));
      for (var x = 16 - half; x < 16 + half; x++) {
        var col = (y % 4 === 0) ? '#a07c34' : ((x + y) % 5 === 0 ? '#c49a45' : (x < 12 && y < 10 ? '#ecd08a' : '#d9b25a'));
        px(g, col, x, y);
      }
    }
    px(g, '#8c6a2a', 1, 16, 30, 1);                               // 처마
    px(g, '#7a5a20', 15, 0, 2, 2);                                // 용마루 끝
    outline(c, '#2a1d10');
    var parts = [[0, 0], [T, 0], [0, T], [T, T]].map(function (o) {
      var p = mk(T, T), pg = p.getContext('2d');
      pg.drawImage(floor, 0, 0);
      pg.drawImage(c, o[0], o[1], T, T, 0, 0, T, T);
      return p;
    });
    return { h: parts[0], j: parts[1], k: parts[2], l: parts[3] };
  }
  // [2026-10-26] 암상인(검보라 두건 망토, 그늘진 얼굴에 눈만 번뜩, 보따리), 털옷 장인(흰 털모자 + 갈색 털외투), 암상인이 없을 때의 빈 노점(천 덮은 나무 상자)
  function blackSprite() {
    var c = mk(T, T), g = c.getContext('2d');
    px(g, '#2a1f33', 3, 4, 10, 11); px(g, '#1a1322', 3, 4, 1, 11); px(g, '#1a1322', 12, 4, 1, 11);
    px(g, '#2a1f33', 4, 0, 8, 5); px(g, '#3a2b48', 5, 0, 6, 1);
    px(g, '#0d0a12', 5, 2, 6, 3); px(g, '#ffd23f', 6, 3); px(g, '#ffd23f', 9, 3);
    px(g, '#c9a04a', 7, 8, 2, 1); px(g, '#8a6a2a', 6, 11, 4, 3);
    return outline(c, '#140d0a');
  }
  function furrierSprite() {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#f2c194';
    px(g, '#3a2b22', 5, 12, 2, 3); px(g, '#3a2b22', 9, 12, 2, 3);
    px(g, '#8a5a32', 3, 7, 10, 6); px(g, '#e8dcc8', 3, 7, 10, 1); px(g, '#e8dcc8', 7, 8, 2, 5);
    px(g, skin, 4, 2, 8, 5); px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4); px(g, '#f29b8a', 5, 5); px(g, '#f29b8a', 10, 5);
    px(g, '#e8dcc8', 3, 0, 10, 3); px(g, '#c8b8a0', 4, 1, 8, 1);
    return outline(c, '#140d0a');
  }
  // [2026-10-28] 요일 동굴 문지기 -- 잿빛 두건 망토 노인, 손에 든 등불
  // [2026-10-28] 피라미드 보상 상자 -- 금테 두른 나무 상자
  function chestSprite() {
    var c = mk(T, T), g = c.getContext('2d');
    px(g, '#7a4a1f', 2, 6, 12, 8); px(g, '#9a6a2f', 2, 3, 12, 4); px(g, '#5a3414', 2, 7, 12, 1);
    px(g, '#ffd23f', 2, 3, 1, 11); px(g, '#ffd23f', 13, 3, 1, 11); px(g, '#ffd23f', 7, 6, 2, 3); px(g, '#fff3b0', 7, 7);
    px(g, '#e8c27a', 0, 14, 16, 2); px(g, '#d6ae64', 3, 14, 4, 1);
    return outline(c, '#140d0a');
  }
  function keeperSprite() {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#e8c09a';
    px(g, '#5a5560', 3, 5, 10, 10); px(g, '#46424c', 3, 5, 1, 10); px(g, '#46424c', 12, 5, 1, 10);
    px(g, '#6a6470', 4, 0, 8, 6); px(g, skin, 5, 2, 6, 4); px(g, '#1a1a1a', 6, 3); px(g, '#1a1a1a', 9, 3); px(g, '#e8e8e8', 5, 5, 6, 2);
    px(g, '#8a5a2b', 13, 6, 1, 4); px(g, '#ffd23f', 12, 10, 3, 3); px(g, '#fff3b0', 13, 11);
    return outline(c, '#140d0a');
  }
  function stallSprite() {
    var c = mk(T, T), g = c.getContext('2d');
    px(g, '#7a5a30', 2, 8, 12, 7); px(g, '#5a3f20', 2, 8, 12, 1); px(g, '#5a3f20', 7, 8, 1, 7);
    px(g, '#3a2b48', 1, 5, 14, 3); px(g, '#2a1f33', 1, 7, 14, 1);
    return outline(c, '#140d0a');
  }
  // [2026-10-23] NPC 그림 -- 대장장이(가죽 앞치마 + 망치), 선장(흰 제복 + 모자 + 수염). 칸 위에 서 있는 모습(바닥은 칸 타일).
  /** [2026-10-30] 은둔한 대장장이 -- 후드를 눌러쓴 검은 옷. */
  function hermitSprite() {
    var c = npcSprite('priest'), g = c.getContext('2d');
    px(g, '#1a1626', 4, 1, 8, 4); px(g, '#0c0a12', 5, 3, 6, 2); px(g, '#24212b', 4, 7, 8, 5); px(g, '#ffb03a', 6, 4); px(g, '#ffb03a', 9, 4);
    return c;
  }
  function npcSprite(kind) {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#f2c194';
    var smith = kind === 'smith', bq = kind === 'boutique', pr = kind === 'priest', gd = kind === 'guide'; // [2026-10-25] 이동 안내원(초록 여행복 + 깃발 지팡이)
     // [2026-10-24] 명품상점 주인(보라 연미복 + 실크햇 + 콧수염), 성당 신부(검은 사제복 + 흰 깃 + 금 십자가)
    var nq = kind === 'ninjaquest', bw = kind === 'boatwright', fr = kind === 'ferry';
    var so = kind === 'smelter', gc = kind === 'gemcutter', ar = kind === 'artisan', ah = kind === 'archishop'; // [2026-10-28] 다도해 상인(푸른 옷) // [2026-10-28] 광석 전문가(주황 앞치마), 보석 세공사(청록), 왕도 장인(검붉은) // [2026-10-28] 다도해 -- 신궁 수문장(붉은 갑옷), 배 목수(갈색 작업복), 뱃사공(파란 옷)
    var body = ah ? '#2a7ac8' : so ? '#c0682a' : gc ? '#2a8a8a' : ar ? '#7a2a2a' : smith ? '#7a4a1f' : bq ? '#7b3fa0' : pr ? '#24212b' : gd ? '#2f8a5a' : nq ? '#a8202a' : bw ? '#8a6a3a' : fr ? '#2a5aa8' : '#f4f4f0', bodyD = ah ? '#1a5a98' : so ? '#8a4a1a' : gc ? '#1a5e5e' : ar ? '#4e1a1a' : smith ? '#5a3a1c' : bq ? '#5a2a78' : pr ? '#141218' : gd ? '#1f6342' : nq ? '#6a1018' : bw ? '#6a4a22' : fr ? '#1a3a78' : '#c9ccd6', pants = smith ? '#3a2b22' : bq ? '#2a1d3a' : pr ? '#24212b' : gd ? '#5a4630' : nq ? '#1a1a22' : bw ? '#3a2b22' : fr ? '#22305e' : '#22305e';
    px(g, pants, 5, 12, 2, 3); px(g, pants, 9, 12, 2, 3); px(g, '#2a1d10', 5, 15, 2, 1); px(g, '#2a1d10', 9, 15, 2, 1);
    px(g, body, 4, 7, 8, 5); px(g, bodyD, 4, 7, 1, 5); px(g, bodyD, 11, 7, 1, 5);
    if (smith) { px(g, '#4a2f18', 5, 8, 6, 4); px(g, '#c49a45', 7, 9, 2, 1); } // 앞치마
    else if (bq) { px(g, '#f4f0e6', 7, 7, 2, 4); px(g, '#d4104f', 7, 7, 2, 1); px(g, '#d9b44a', 6, 9); px(g, '#d9b44a', 9, 9); }
    else if (gd) { px(g, '#e8c84a', 4, 9, 8, 1); px(g, '#6b4220', 9, 10, 2, 2); } // 노란 띠 + 가죽 주머니
    else if (pr) { px(g, '#24212b', 4, 12, 8, 3); px(g, '#f4f0e6', 6, 7, 4, 1); px(g, '#d9b44a', 7, 8, 2, 4); px(g, '#d9b44a', 6, 9, 4, 1); } // 긴 사제복 + 흰 깃 + 십자가
    else { px(g, '#d9b44a', 7, 8, 1, 1); px(g, '#d9b44a', 7, 10, 1, 1); px(g, '#22305e', 4, 11, 8, 1); }
    px(g, skin, 3, 8, 1, 3); px(g, skin, 12, 8, 1, 3);
    px(g, skin, 4, 1, 8, 6);
    px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4);
    if (bq) {
      px(g, '#1a1a1a', 5, 0, 6, 1); px(g, '#1a1a1a', 3, 1, 10, 1); px(g, '#d4af37', 5, 0, 6, 1); // 실크햇 + 금띠
      px(g, '#3b2a1a', 6, 5, 4, 1); px(g, '#d4af37', 9, 3, 2, 2); // 콧수염 + 외알 안경
    } else if (gd) {
      px(g, '#c0392b', 3, 0, 10, 2); px(g, '#8e2a20', 4, 2, 8, 1); px(g, '#7a4a26', 4, 3, 1, 2); px(g, '#7a4a26', 11, 3, 1, 2); // 붉은 여행 모자 + 갈색 머리
      px(g, '#8a5a2b', 13, 1, 1, 13); px(g, '#ffd23f', 14, 1, 2, 3); px(g, '#e8433a', 14, 2, 2, 1); // 깃발 지팡이
    } else if (pr) {
      px(g, '#8a8f98', 4, 0, 8, 2); px(g, '#8a8f98', 4, 2, 1, 2); px(g, '#8a8f98', 11, 2, 1, 2); // 은발
    } else if (smith) {
      px(g, '#3b2a1a', 4, 0, 8, 2); px(g, '#3b2a1a', 4, 6, 8, 1); // 머리/수염
      px(g, '#8a8f98', 12, 4, 3, 2); px(g, '#6b4220', 13, 6, 1, 5);  // 망치
    } else {
      px(g, '#22305e', 3, 0, 10, 2); px(g, '#1a234a', 4, 2, 8, 1); px(g, '#d9b44a', 7, 0, 2, 1); // 선장 모자
      px(g, '#e8e8e8', 4, 5, 8, 2); // 흰 수염
    }
    return outline(c, '#140d0a');
  }
  // [2026-10-23] 정박한 배 -- 배 칸(q) 묶음 크기로 한 장을 그려 칸별로 잘라 쓴다(물 위에).
  function buildShip(sx, sy, w, h) {
    var c = mk(w * T, h * T), g = c.getContext('2d'), W = w * T, H = h * T;
    px(g, 'rgba(0,0,0,.25)', 4, 6, W - 4, H - 4);
    for (var y = 2; y < H - 2; y++) { // 선체(앞뒤가 둥근 길쭉한 배, 위에서 본 모습)
      var t = Math.abs((y - H / 2) / (H / 2)), half = Math.round((W / 2 - 2) * Math.sqrt(Math.max(0, 1 - Math.pow(t, 4))));
      for (var x = W / 2 - half; x < W / 2 + half; x++) {
        var edge = x < W / 2 - half + 2 || x >= W / 2 + half - 2;
        px(g, edge ? '#4a2c12' : ((y % 6 === 0) ? '#8b5a2b' : '#a8733d'), x, y);
      }
    }
    px(g, '#5a3a1c', W / 2 - 1, 6, 2, H - 12);                          // 돛대
    px(g, '#f4efe0', W / 2 - 14, H * 0.3, 28, 10); px(g, '#d9d0b8', W / 2 - 14, H * 0.3 + 9, 28, 1); // 가로 돛
    px(g, '#f4efe0', W / 2 - 10, H * 0.62, 20, 8); px(g, '#d9d0b8', W / 2 - 10, H * 0.62 + 7, 20, 1);
    px(g, '#b5251f', W / 2 - 3, 4, 6, 3);                                // 깃발
    outline(c, '#140d0a');
    return { x: sx, y: sy, w: w, h: h, img: c };
  }
  function hashXY(x, y) { var h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return h; }
  function tileFor(c, x, y, wf) {
    var v = hashXY(x, y), p;
    switch (c) {
      case '.': return tiles.grass[v % 3];
      case ',': return tiles.path[v % 2];
      case ':': return tiles.rock[v % 2];
      case 'V': return tiles.village;
      case 'H': return tiles.bridge;
      case '~': return map && (map.id === 'north' ? y < map.h - 20 : /^snow/.test(map.id)) ? tiles.ice[v % 2] : tiles.water[(wf + (x + y) % 2) % 2]; // 설원/설산 호수는 얼음
      case 'T': return tiles.tree[v % 2];
      case 'F': return tiles.forest[v % 2]; // [2026-11-05] 울창한 숲
      case 'E': return tiles.grass[v % 3]; // [2026-11-08] 하피의 둥지 대형 나무 밑(나무는 따로 위에 그린다 -- forestTreesDraw)
      case 's': return tiles.stone[v % 2];
      case 'W': return tiles.castleWall[v % 2];
      case 'K': { // 아래가 길이면 1층 앞면, [2026-10-27] 위 두 줄은 지붕, 그 사이는 층마다 창(높은 건물)
        if (cellAt(x, y + 1) !== 'K') return tiles.house2[v % 2];
        var ku = 0; while (ku < 2 && cellAt(x, y - ku - 1) === 'K') ku++;
        return ku < 2 ? tiles.roof[v % 2] : tiles.facade[v % 5 === 0 ? 1 : 0];
      }
      case 'Q': { // 십자가는 지붕 맨 윗줄에 6칸마다, [2026-10-27] 위 세 줄만 지붕, 그 아래는 아치 창이 줄지은 높은 벽면
        var qu = 0; while (qu < 3 && cellAt(x, y - qu - 1) === 'Q') qu++;
        return qu < 3 && cellAt(x, y + 1) === 'Q' ? tiles.church[qu === 0 && (x - (map.w >> 1)) % 6 === 0 ? 1 : 0] : tiles.churchWall[v % 2];
      }
      case 'X': return tiles.tele[wf];
      case 'u': return tiles.seaSand[v % 2];
      case 'p': return tiles.palace[v % 2];
      case 'e': return tiles.seaweed[v % 2];
      case 'O': return tiles.coral[v % 2];
      case 'L': return tiles.palaceWall[v % 2];
      case 'Z': return st && st.dragon && st.dragon.has ? tiles.seaGate[wf] : tiles.water[(wf + (x + y) % 2) % 2]; // [2026-10-24] 숨은 입구 -- 물과 똑같이, [2026-10-28] 용왕의 반지를 얻으면 소용돌이로 보인다
      case 'n': return tiles.snow[v % 2];
      case 'I': return dug[x + ',' + y] ? tiles.veinDone : tiles.vein[v % 2]; // [2026-10-28] 설산 동굴 얼음 광맥, [2026-10-29] 빙석을 캔 광맥은 빛을 잃은 바위
      case 'Y': return tiles.pine[v % 2];
      case 'c': return tiles.cave[v % 2];
      case 'r': return tiles.gravel;
      case '#': return tiles.wall[v % 2];
      case 'h': case 'j': case 'k': case 'l': return tiles.house[c];
      case 'G': p = portalAt[x + ',' + y]; return tiles.gate[p && !p.locked ? 0 : 1];
      case 'M': return map && map.id === 'westroad' ? (tiles.forestGate || (tiles.forestGate = over(tiles.forest[0], function (g) { // [2026-11-05] 숲 던전 입구
        px(g, '#4e2f16', 1, 3, 3, 13); px(g, '#6b4220', 2, 3, 1, 13); px(g, '#4e2f16', 12, 3, 3, 13); px(g, '#6b4220', 13, 3, 1, 13);
        px(g, '#0a0806', 4, 6, 8, 10); px(g, '#0a0806', 5, 5, 6, 1); px(g, '#1a1410', 4, 15, 8, 1);
        for (var i = 0; i < 8; i++) { px(g, '#3a7a2a', 4 + i, 7 + (i % 3) * 3, 1, 1); px(g, '#3a7a2a', 11 - i, 8 + (i % 3) * 3, 1, 1); }
        px(g, '#a83a3a', 6, 9); px(g, '#a83a3a', 9, 12); px(g, '#a83a3a', 7, 14); px(g, '#2a6630', 3, 2, 10, 2);
      }))) : tiles.mouth;
      case 'U': return tiles.exit;
      case 'S': p = portalAt[x + ',' + y]; return tiles.door[p && !p.locked ? 0 : 1];
      case 'd': return tiles.sand[v % 2];
      case 'R': return tiles.sandstone[v % 2];
      case 'C': return tiles.cactus;
      case 'b': return tiles.beach[v % 2];
      case 'P': return tiles.bridge;
      case 'q': return tiles.water[(wf + (x + y) % 2) % 2]; // 배 그림은 draw()에서 위에 덮는다
      case 'B': p = portalAt[x + ',' + y]; return tiles.plank[p && !p.locked ? 0 : 1];
      case 'N': // NPC 바닥: 마을이면 마을, 부두 위면 부두, 그 외 모래(NPC 그림은 draw()에서 위에)
        p = npcAt[x + ',' + y];
        if (p && p.ground && p.ground !== 'N') return tileFor(p.ground, x, y, wf); // [2026-10-25] 이동 안내원 등 -- 원래 바닥
        return map && map.id === 'field' ? tiles.village : (p && p.kind === 'captain' ? tiles.bridge : p && p.kind === 'priest' ? tiles.stone[0] : tiles.sand[0]);
      default: return tiles.cliff[v % 2];
    }
  }

  // 캐릭터: 방향(down/up/right/left) x 걸음(0/1/2) x 캐기(0 없음, 1 들기, 2 내려치기). 왼쪽은 오른쪽을 뒤집어서.
  /** 색을 밝게(f > 0)/어둡게(f < 0). */
  function shade(hex, f) {
    var n = parseInt(hex.slice(1), 16), r = n >> 16, gg = (n >> 8) & 255, b = n & 255;
    var m = function (v) { return Math.max(0, Math.min(255, Math.round(f < 0 ? v * (1 + f) : v + (255 - v) * f))); };
    return 'rgb(' + m(r) + ',' + m(gg) + ',' + m(b) + ')';
  }
  // [2026-10-24] 명품상점 외형 -- lk = { gender 0 남/1 여, hat '색|모양', hair '색', top '색|모양', bottom '색', shoes '색' } (빈 값 = 기본)
  // [2026-10-25] 그리는 코드는 로그 뷰어와 같이 쓰려고 _mine_char.jsp(window.MineChar)로 옮겼다.
  function charSprite(dir, step, dig, lk) { return MineChar.sprite(dir, step, dig, lk); }
  var spriteKey = null;
  function buildSprites(lk) {
    var key = JSON.stringify(lk || {});
    if (sprites && key === spriteKey) return;
    spriteKey = key;
    sprites = {};
    ['down', 'up', 'right', 'left'].forEach(function (d) {
      for (var s = 0; s < 3; s++) for (var k = 0; k < 5; k++) sprites[d + s + k] = charSprite(d, s, k, lk); // [2026-10-28] k 3/4 = 무기 휘두르기
    });
  }
  // 광석 아이콘(광석 탭/광산상점에서도 씀)
  function oreIcon(grade) {
    if (oreIcons[grade]) return oreIcons[grade];
    var c = mk(T, T), g = c.getContext('2d'), col = ORE_COL[grade] || ORE_COL[1];
    if (grade === 33) { // [2026-10-28] 얼음의 심장 -- 얼음 결정 안에 뛰는 심장
      [[7, 1, 2, 1], [5, 2, 6, 1], [3, 3, 10, 2], [2, 5, 12, 5], [3, 10, 10, 2], [5, 12, 6, 1], [7, 13, 2, 2]].forEach(function (r) { px(g, col[0], r[0], r[1], r[2], r[3]); });
      px(g, '#d8f6ff', 4, 4, 2, 2); px(g, '#ffffff', 4, 4); px(g, '#ffffff', 11, 11);
      px(g, '#e8466a', 5, 6, 2, 1); px(g, '#e8466a', 9, 6, 2, 1); px(g, '#e8466a', 4, 7, 8, 2); px(g, '#e8466a', 5, 9, 6, 1); px(g, '#e8466a', 6, 10, 4, 1); px(g, '#e8466a', 7, 11, 2, 1);
      px(g, '#ffb0c0', 5, 7);
      outline(c, '#0a2a3a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade === 65) { // [2026-11-05] 번개의 심장 -- 푸른 심장을 가르는 금빛 번개
      px(g, '#2a6ad8', 4, 4, 3, 2); px(g, '#2a6ad8', 9, 4, 3, 2); px(g, '#2a6ad8', 3, 5, 10, 4); px(g, '#2a6ad8', 4, 9, 8, 2); px(g, '#2a6ad8', 5, 11, 6, 1); px(g, '#2a6ad8', 6, 12, 4, 1); px(g, '#2a6ad8', 7, 13, 2, 1);
      px(g, '#8ac8ff', 5, 5, 2, 1); px(g, '#ffe94a', 8, 5, 2, 2); px(g, '#ffe94a', 7, 7, 2, 2); px(g, '#ffe94a', 6, 9, 3, 1); px(g, '#ffe94a', 8, 10, 1, 2); px(g, '#fff8c0', 8, 5);
      outline(c, '#0a1a3a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade === 70) { // [2026-11-06] 은하수 조각 -- 남색 조각 안에 흘러가는 은하수(별 점)
      [[6, 1, 4, 1], [4, 2, 8, 2], [3, 4, 10, 3], [2, 7, 11, 3], [3, 10, 9, 2], [5, 12, 5, 2], [7, 14, 2, 1]].forEach(function (r) { px(g, '#1a1e4a', r[0], r[1], r[2], r[3]); });
      [[4, 9], [5, 8], [6, 7], [7, 7], [8, 6], [9, 5], [10, 5], [11, 4]].forEach(function (p) { px(g, '#6a5ad8', p[0], p[1], 1, 2); });
      [[5, 8], [7, 7], [9, 5], [11, 4]].forEach(function (p) { px(g, '#ffffff', p[0], p[1]); });
      px(g, '#c8d8ff', 4, 4); px(g, '#ffe8a0', 10, 10); px(g, '#c8d8ff', 7, 12); px(g, '#f0c8ff', 12, 7);
      outline(c, '#05061a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade === 64) { // [2026-10-30] 화산의 심장 -- 붉은 심장에 금빛 불길
      px(g, '#d8341a', 4, 4, 3, 2); px(g, '#d8341a', 9, 4, 3, 2); px(g, '#d8341a', 3, 5, 10, 4); px(g, '#d8341a', 4, 9, 8, 2); px(g, '#d8341a', 5, 11, 6, 1); px(g, '#d8341a', 6, 12, 4, 1); px(g, '#d8341a', 7, 13, 2, 1);
      px(g, '#ffb03a', 7, 6, 2, 5); px(g, '#ffb03a', 6, 8, 4, 2); px(g, '#ffe8a0', 7, 8, 2, 2); px(g, '#ffd08a', 5, 5, 2, 1);
      outline(c, '#4a0a04');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade === 39) { // [2026-10-28] 사막의 심장 -- 모래빛 심장 둘레에 흩날리는 모래
      px(g, '#e8a23a', 4, 4, 3, 2); px(g, '#e8a23a', 9, 4, 3, 2); px(g, '#e8a23a', 3, 5, 10, 4); px(g, '#e8a23a', 4, 9, 8, 1); px(g, '#e8a23a', 5, 10, 6, 1); px(g, '#e8a23a', 6, 11, 4, 1); px(g, '#e8a23a', 7, 12, 2, 1);
      px(g, '#b06a1a', 9, 8, 3, 1); px(g, '#b06a1a', 8, 10, 3, 1); px(g, '#fff0c0', 5, 5, 2, 1); px(g, '#fff0c0', 4, 6);
      px(g, '#f4d68a', 1, 2); px(g, '#f4d68a', 14, 3); px(g, '#f4d68a', 13, 12); px(g, '#f4d68a', 2, 11);
      outline(c, '#3a2008');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade === 32) { // [2026-10-28] 닌자의 비급서 -- 두루마리 책
      px(g, '#e8d8a8', 3, 4, 10, 9); px(g, '#3a2a1a', 3, 3, 10, 1); px(g, '#3a2a1a', 3, 13, 10, 1); px(g, '#c0392b', 7, 4, 2, 9);
      px(g, '#3a2a1a', 5, 6, 1, 1); px(g, '#3a2a1a', 10, 6, 1, 1); px(g, '#3a2a1a', 5, 9, 1, 1); px(g, '#3a2a1a', 10, 9, 1, 1);
      outline(c, '#140d0a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade >= 40 && grade <= 44) { // [2026-10-28] 괴 -- 사다리꼴 주괴(윗면 밝게)
      px(g, col[1], 5, 4, 6, 2); px(g, col[0], 3, 6, 10, 6); px(g, col[1], 4, 6, 8, 1); px(g, shade(col[0], -0.35), 3, 11, 10, 1); px(g, '#ffffff', 6, 4, 2, 1);
      outline(c, '#140d0a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    if (grade >= 21) { // [2026-10-24] 보석: 깎은 보석 모양(위 테이블 + 아래 뾰족)
      for (var gy = 3; gy < 14; gy++) {
        var half = gy < 6 ? 3 + (gy - 3) : Math.max(0, 6 - Math.round((gy - 6) * 0.8));
        for (var gx = 8 - half; gx < 8 + half; gx++) px(g, gy < 6 ? col[1] : ((gx + gy) % 3 === 0 ? col[1] : col[0]), gx, gy);
      }
      px(g, '#ffffff', 6, 4, 2, 1); px(g, '#ffffff', 5, 5);
      if (grade === 23) { px(g, '#ffffff', 8, 8); px(g, '#ffffff', 7, 9, 3, 1); px(g, '#ffffff', 8, 10); } // 스타 사파이어의 별
      if (grade >= 45 && grade <= 59) { px(g, '#ffffff', 1, 1); px(g, '#ffffff', 0, 2, 3, 1); px(g, '#ffffff', 1, 3); px(g, '#fff6b8', 13, 11); px(g, '#fff6b8', 12, 12, 3, 1); px(g, '#fff6b8', 13, 13); } // [2026-10-28] 세공된 보석 -- 반짝임 둘
      if (RARE_GEM[grade]) { px(g, '#ffffff', 13, 1); px(g, '#ffffff', 12, 2, 3, 1); px(g, '#ffffff', 13, 3); }
      outline(c, '#140d0a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    for (var y = 3; y < 15; y++) for (var x = 1; x < 15; x++) {
      var dx = x - 7.5, dy = (y - 9) * 1.2, d = dx * dx + dy * dy;
      if (d < 40) px(g, d > 28 ? '#5a524b' : (dy < -2 ? '#9c938a' : '#7b736b'), x, y);
    }
    var gems = [[4, 7], [9, 5], [7, 10], [11, 9], [5, 11]];
    gems.slice(0, 3 + Math.min(2, Math.floor(grade / 2))).forEach(function (p) {
      px(g, col[0], p[0], p[1], 2, 2); px(g, col[1], p[0], p[1]);
    });
    if (grade >= 5) { px(g, '#ffffff', 12, 2); px(g, '#ffffff', 11, 3, 3, 1); px(g, '#ffffff', 12, 4); }
    outline(c, '#140d0a');
    oreIcons[grade] = c.toDataURL();
    return oreIcons[grade];
  }

  // ---------------------------------------------------------------- [2026-10-24] 몬스터
  // 위치는 시각으로 정한다(서버 S5MineMobs.cellAt과 같은 식) -- 체력/쓰러짐/멈춤만 서버에서 1.5초마다 받아 온다(mobSt).
  var mobHitAt = {}; // [2026-11-03] 공격한 순간(몬스터 흔들림)
  var treeFallAt = {}, treeSt = [], treeNeed = 60, harpySt = null, harpyImmT = 0, harpyGustAt = 0; // [2026-11-08] 하피의 둥지 -- 나무 [번호, 상태 0 살아 있음/1 불탐/2 다 탐, 불 쌓인 양], 하피 {hide, weak, move}
  var mobSt = {}, srvOff = 0, mobTimer = 0, mobSprites = {}, mobOcc = {}, atkBusy = false, lastAtkT = 0;
  function mix(a, b) {
    var h = (Math.imul(a, 0x9E3779B1 | 0) + Math.imul(b, 0x85EBCA77 | 0)) | 0;
    h ^= h >>> 15; h = Math.imul(h, 0x2C1B3C6D); h ^= h >>> 12; h = Math.imul(h, 0x297A2D39); h ^= h >>> 15;
    return h & 0x7fffffff;
  }
  function nowSrv() { return Date.now() + srvOff; }
  /** 몬스터 화면 위치 {x, y(소수, 칸이 바뀔 때 0.6초 동안 옮겨 감), cx, cy(정수 칸)} -- 쓰러졌으면 null. */
  // [2026-10-24] "공격할 때 몬스터가 순간이동하는 것 같다" -- 집 칸(c[0])과 상하좌우 1칸만, 절반 넘게 집에 머문다(서버 S5MineMobs.cellAt과 같은 식),
  //  옮겨 갈 땐 0.9초 동안 걸어가고, 맞아서 멈췄다가 풀릴 때도 멈춘 칸에서 걸어서 돌아간다.
  function mobCell(m, slot) {
    var r = mix(m.id, slot) % (m.c.length * 2);
    return m.c[r < m.c.length ? r : 0];
  }
  function mobPos(m, t) {
    var s = mobSt[m.id];
    if (s && s.du > t) return null;
    if (m.d && !s) return null; // [2026-10-28] 대주교 수하 -- 불려 나오기 전엔 없다
    if (s && s.fu > t) {
      var md = s.md || 950; // [2026-11-05] 걷는 시간 = 위치가 바뀐 간격(끊김 없이 다음 위치까지 이어 걷게)
      if (s.ox != null && t - s.mt < md) { var q = Math.max(0, (t - s.mt) / md); return { x: s.ox + (s.fx - s.ox) * q, y: s.oy + (s.fy - s.oy) * q, cx: s.fx, cy: s.fy }; } // [2026-10-25] 쫓아올 때 한 칸씩 걸어서
      return { x: s.fx, y: s.fy, cx: s.fx, cy: s.fy };
    }
    var SL = map.mobSlotMs || 3000, slot = Math.floor(t / SL);
    var b = mobCell(m, slot), a = mobCell(m, slot - 1);
    var bx = b % map.w, by = Math.floor(b / map.w), ax = a % map.w, ay = Math.floor(a / map.w), k = Math.min(1, (t - slot * SL) / 900);
    if (s && s.fu > 0 && t - s.fu < 900) { ax = s.fx; ay = s.fy; k = (t - s.fu) / 900; } // 멈춤이 풀린 직후
    return { x: ax + (bx - ax) * k, y: ay + (by - ay) * k, cx: k < 0.5 ? ax : bx, cy: k < 0.5 ? ay : by };
  }
  /** 몬스터 그림(종류별 16x16) -- kind: small 작은 짐승, quad 네발짐승, human 사람형, flyer 날짐승/유령, crab 게/전갈, blob 물컹이, snake 뱀. */
  // [2026-10-29] 보스 전용 그림(48x48) -- 예전엔 사람형 16x16을 색만 바꿔 키웠다. 보스마다 고유 디자인, f = 숨쉬기/흔들림 2프레임,
  //  빛(눈·용암·불꽃·얼음 결정·기운)은 테두리 없이 위에 겹친다. 여왕개미(보스는 아니지만 보스급)도 같은 그림판에.
  var BOSS_ART = { '파라오 미라': 'pharaoh', '고대 광석 골렘': 'golem', '타락한 대주교': 'archbishop', '얼음 여왕': 'icequeen', '닌자 두목': 'ninjaboss', '닌자 두목의 분신': 'ninjaboss', '용암 거신': 'lavagiant', '여왕개미': 'antqueen', '천둥 황소': 'thunderbull', '하피 여왕': 'harpy' };
  var bossArt = (function () {
    function painter(g) {
      var P = function (x, y, c) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= 48 || y >= 48) return; g.fillStyle = c; g.fillRect(x, y, 1, 1); };
      return {
        P: P,
        R: function (x, y, w, h, c) { g.fillStyle = c; g.fillRect(x, y, w, h); },
        E: function (cx, cy, rx, ry, c) { for (var y = -ry; y <= ry; y++) for (var x = -rx; x <= rx; x++) if ((x * x) / (rx * rx + 0.5) + (y * y) / (ry * ry + 0.5) <= 1) P(cx + x, cy + y, c); },
        L: function (x0, y0, x1, y1, c, w) { var n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) || 1; for (var i = 0; i <= n; i++) { var x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n; P(x, y, c); if (w > 1) P(x + 1, y, c); if (w > 2) P(x, y + 1, c); } },
        D: function (cx, cy, r, c, hi) { for (var y = -r; y <= r; y++) for (var x = -r; x <= r; x++) if (Math.abs(x) + Math.abs(y) <= r) P(cx + x, cy + y, c); if (hi) P(cx - 1, cy - 1, hi); }
      };
    }
    var ART = {
      harpy: function (pt, pf, f, pose) { // [2026-11-08] 하피 -- 바람 보스. 연두·청록 깃털 날개(프레임마다 위/아래 날갯짓), 사람 얼굴과 긴 은발, 노란 새 다리와 발톱, 바람 줄기
        var w0 = '#5ab89a', w1 = '#2f7a68', w2 = '#a8ecd0', sk = '#f0c8a8', sk2 = '#c89878', hr = '#d8e4ec', hr2 = '#9aa8b8', ft = '#e8b830', ft2 = '#a87a18', k = '#140d0a';
        var up = f ? 1 : 0;
        // 날개 -- 위로(0) / 아래로(1)
        var wing = function (dir) {
          for (var i = 0; i < 7; i++) { var x0 = 24 + dir * (6 + i * 2.6), y0 = up ? 26 + i * 1.4 : 12 + i * 0.6, len = 11 - i * 0.6;
            pt.L(24 + dir * 5, 22, x0, y0, w1, 2);
            for (var j = 0; j < len; j++) pt.P(x0 + dir * 0.3 * j, y0 + j, j < 2 ? w2 : (j % 3 ? w0 : w1)); }
          pt.E(24 + dir * 9, up ? 27 : 18, 6, 4, w0); pt.E(24 + dir * 8, up ? 26 : 17, 3, 2, w2);
        };
        wing(-1); wing(1);
        // 몸(깃털 덮인 가슴) + 꼬리깃
        pt.E(24, 28, 7, 9, w0); pt.E(24, 26, 5, 6, w2); for (var by = 22; by < 36; by += 3) pt.R(20, by, 9, 1, w1);
        pt.L(21, 36, 18, 41, w1, 2); pt.L(27, 36, 30, 41, w1, 2); pt.L(24, 37, 24, 42, w0, 2);
        // 새 다리 + 발톱
        pt.R(20, 37, 2, 6, ft); pt.R(27, 37, 2, 6, ft); pt.P(18, 43, ft2); pt.P(19, 44, ft2); pt.P(21, 44, ft2); pt.P(29, 44, ft2); pt.P(30, 44, ft2); pt.P(26, 44, ft2);
        // 얼굴 + 긴 은발(바람에 날림)
        pt.E(24, 13, 5, 6, sk); pt.R(20, 15, 8, 1, sk2);
        for (var hy = 6; hy < 24; hy++) { var flow = Math.round(Math.sin(hy * 0.5 + (f ? 1.2 : 0)) * 1.5); pt.R(17 + flow, hy, 3, 1, hy % 3 ? hr : hr2); pt.R(29 + flow, hy, 3, 1, hy % 3 ? hr : hr2); }
        pt.R(19, 6, 11, 3, hr); pt.R(20, 8, 3, 2, hr); pt.R(26, 8, 3, 2, hr);
        pf.R(21, 12, 2, 2, '#8affd8'); pf.R(26, 12, 2, 2, '#8affd8'); // 빛나는 청록 눈(바람 속성)
        pt.P(24, 16, '#c05050');
        // 바람 줄기(프레임마다 자리 바뀜)
        var ws = f ? [[2, 34, 10, 32], [38, 36, 46, 33], [6, 8, 13, 6]] : [[3, 30, 11, 29], [37, 31, 45, 29], [35, 6, 42, 4]];
        ws.forEach(function (q) { pf.L(q[0], q[1], q[2], q[3], 'rgba(220,255,240,.85)'); pf.P(q[2] + 1, q[3] - 1, '#ffffff'); });
      },
      pharaoh: function (pt, pf, f) { // [2026-11-04] 파라오 미라 -- 금·청 줄무늬 네메스 두건과 코브라 장식, 붕대 감긴 몸, 흩날리는 붕대, 보랏빛 눈(어둠 속성)
        var w0 = '#d8c8a0', w1 = '#a8946a', w2 = '#f0e6cc', gd = '#e8b830', bl = '#2a4aa8', u = f ? 1 : 0;
        pt.R(17, 34, 6, 12, w0); pt.R(26, 34, 6, 12, w0); for (var ly = 35; ly < 46; ly += 3) { pt.R(17, ly, 6, 1, w1); pt.R(26, ly, 6, 1, w1); }
        pt.E(24, 27, 11, 10, w0); for (var by = 18; by < 37; by += 3) pt.R(14, by, 21, 1, w1); pt.L(15, 20, 33, 35, w1); pt.L(33, 20, 16, 35, w2);
        pt.R(5, 21 + u, 10, 5, w0); pt.R(33, 21 + u, 10, 5, w0); pt.R(5, 22 + u, 10, 1, w1); pt.R(33, 24 + u, 10, 1, w1); // 앞으로 내민 두 팔
        for (var i = 0; i < 9; i++) { pt.P(5 - (i >> 1), 26 + u + i + Math.round(Math.sin(i + (f ? 1.5 : 0))), w2); pt.P(42 + (i >> 1), 26 + u + i + Math.round(Math.cos(i + (f ? 1.5 : 0))), w2); } // 늘어진 붕대
        for (var hy = 3; hy < 23; hy++) { var hw = hy < 7 ? 8 + hy : Math.max(9, 15 - Math.max(0, hy - 16)); pt.R(24 - hw, hy, hw * 2, 1, hy % 2 ? gd : bl); } // 네메스 두건(줄무늬, 얼굴은 위에 덮는다)
        pt.R(15, 3, 18, 3, gd); pt.R(15, 4, 18, 1, bl);
        pt.R(18, 7, 12, 11, w0); pt.R(18, 10, 12, 1, w1); pt.R(18, 14, 12, 1, w1); pt.R(18, 17, 12, 1, w1); // 붕대 얼굴
        pt.R(23, 0, 2, 4, gd); pt.P(22, 1, gd); pt.P(25, 1, gd); pt.P(23, 1, '#c02020'); // 코브라
        pt.R(22, 18, 4, 4, bl); pt.R(22, 19, 4, 1, gd); // 턱수염
        pf.R(20, 11, 2, 2, '#c060ff'); pf.R(26, 11, 2, 2, '#c060ff'); if (f) { pf.P(19, 11, '#f0c8ff'); pf.P(28, 11, '#f0c8ff'); }
        [[4, 14], [44, 12], [3, 38], [45, 40], [10, 4], [38, 3]].forEach(function (q, i) { var o = (f ? 2 : 0) * (i % 2 ? 1 : -1); pf.P(q[0], q[1] + o, '#7a3aa8'); pf.P(q[0], q[1] + o + 1, '#4a1a6a'); });
      },
      golem: function (pt, pf, f) { // 고대 광석 골렘 -- 광석 결정이 박힌 바위 몸, 하늘빛 눈
        var b = '#6a5a7a', d = '#4a3e58', h = '#8a7a9a', u = f ? -2 : 0;
        pt.R(15, 36, 7, 11, d); pt.R(27, 36, 7, 11, d); pt.R(14, 44, 9, 3, '#3a3048'); pt.R(26, 44, 9, 3, '#3a3048');
        pt.E(24, 26 + (f ? 1 : 0), 15, 12, b); pt.E(27, 30, 11, 7, d); pt.E(19, 20, 7, 5, h);
        pt.E(7, 28 + u, 6, 9, b); pt.E(8, 33 + u, 5, 4, d); pt.E(41, 28 + u, 6, 9, b); pt.E(40, 33 + u, 5, 4, d);
        pt.E(24, 11, 8, 6, b); pt.E(22, 9, 4, 2, h);
        pt.L(16, 22, 21, 30, d); pt.L(30, 18, 34, 27, d); pt.L(10, 24, 7, 32, d);
        pt.D(19, 25, 2, '#ffd23f', '#fff6b0'); pt.D(31, 23, 2, '#20c9b8', '#b9fff6'); pt.D(9, 22 + u, 2, '#20c9b8', '#b9fff6'); pt.D(40, 24 + u, 2, '#ffd23f', '#fff6b0'); pt.D(26, 31, 1, '#f23fd0', '#ffffff');
        pf.R(20, 10, 2, 2, '#7ff0ff'); pf.R(26, 10, 2, 2, '#7ff0ff'); if (f) { pf.P(19, 10, '#bff8ff'); pf.P(28, 10, '#bff8ff'); pf.P(24, 31, '#ffffff'); }
      },
      archbishop: function (pt, pf, f) { // 타락한 대주교 -- 주교관, 찢어진 사제복, 해골 지팡이, 보라 기운
        var r = '#3a1a4a', rd = '#24102e', g = '#c8a040';
        for (var y = 15; y < 47; y++) { var hw = Math.round(6 + (y - 15) * 0.42); pt.R(22 - hw, y, hw * 2, 1, (y + 15) % 7 < 2 ? rd : r); }
        for (var x = 9; x < 36; x += 3) pt.R(x, 46 - (x % 2), 2, 1 + (x % 2), rd);
        pt.R(21, 15, 2, 31, g); pt.R(10, 44, 25, 1, g);
        pt.R(17, 8, 10, 8, '#b8c0a0'); pt.R(17, 14, 10, 2, '#98a080');
        pt.R(16, 0, 12, 9, r); pt.R(21, 1, 2, 7, g); pt.R(19, 3, 6, 2, g); pt.R(15, 7, 14, 2, g);
        pf.R(19, 10, 2, 2, '#ff3040'); pf.R(24, 10, 2, 2, '#ff3040');
        pt.R(37, 8, 2, 39, '#4a3020'); pt.E(38, 6, 4, 4, '#e8e0c8'); pt.R(36, 5, 1, 2, '#2a2020'); pt.R(39, 5, 1, 2, '#2a2020'); pt.R(37, 8, 3, 1, '#2a2020');
        pt.R(33, 22, 5, 3, '#b8c0a0'); pt.R(6, 24, 4, 3, '#b8c0a0');
        [[38, 0], [36, 1], [40, 1]].forEach(function (q, i) { pf.P(q[0], q[1] - (f && i === 0 ? 1 : 0), i ? '#b06aff' : '#e0c0ff'); });
        [[4, 18], [43, 30], [6, 36], [41, 14], [2, 28], [44, 22]].forEach(function (q, i) { var o = (f ? 3 : 0) * (i % 2 ? 1 : -1); pf.P(q[0], q[1] + o, '#7a3aa8'); pf.P(q[0], q[1] + o + 1, '#4a1a6a'); });
      },
      icequeen: function (pt, pf, f) { // 얼음 여왕 -- 얼음 왕관, 흘러내리는 머릿결, 넓게 퍼진 얼음 드레스, 떠도는 결정
        for (var y = 24; y < 47; y++) { var hw = Math.round(5 + (y - 24) * 0.75); for (var x = -hw; x < hw; x++) pt.P(24 + x, y, (x + y) % 9 === 0 ? '#ffffff' : x < -hw / 2 ? '#8fd0f0' : x > hw / 2 ? '#a8dcf4' : '#bfefff'); }
        pt.R(19, 16, 10, 9, '#9fd8f0'); pt.R(21, 17, 6, 2, '#dff6ff');
        pt.R(13, 17, 6, 3, '#9fd8f0'); pt.R(29, 17, 6, 3, '#9fd8f0'); pt.R(11, 19, 3, 3, '#e8f4ff'); pt.R(34, 19, 3, 3, '#e8f4ff');
        for (var hy = 3; hy < 26; hy++) { // 머릿결 -- 양옆으로 흘러내리며 살짝 물결
          var wv = Math.round(Math.sin(hy * 0.5 + (f ? 1 : 0)) * 1);
          pt.R(16 + wv - (hy > 12 ? 1 : 0), hy, 3, 1, hy % 4 ? '#dff4ff' : '#bfe4f8'); pt.R(29 - wv + (hy > 12 ? 1 : 0), hy, 3, 1, hy % 4 ? '#dff4ff' : '#bfe4f8');
        }
        pt.R(18, 2, 12, 4, '#dff4ff'); pt.R(19, 6, 10, 9, '#e8f4ff'); pt.R(19, 5, 10, 2, '#cfeaf8');
        pt.R(21, 9, 2, 2, '#3a7ad8'); pt.R(25, 9, 2, 2, '#3a7ad8'); pt.P(21, 9, '#ffffff'); pt.P(25, 9, '#ffffff'); pt.R(23, 12, 2, 1, '#8ab8e0');
        [[19, 1, 3], [23, -1, 4], [27, 1, 3]].forEach(function (q) { for (var k = 0; k < q[2]; k++) pt.R(q[0] - ((q[2] - 1 - k) / 2 | 0), q[1] + k + 1, 1 + (q[2] - 1 - k > 0 ? 1 : 0), 1, '#7fd0f0'); pf.P(q[0], q[1] + 1, '#ffffff'); });
        [[6, 12], [42, 16], [4, 32], [44, 34], [10, 42], [38, 6]].forEach(function (q, i) { var o = (f ? 2 : 0) * (i % 2 ? 1 : -1); pf.D(q[0], q[1] + o, 1, '#bfefff', '#ffffff'); });
      },
      ninjaboss: function (pt, pf, f, pose) { // 닌자 두목 -- 노란 눈, 휘날리는 붉은 목도리, 쌍검(pose = 인을 맺은 자세: 칼을 가슴 앞에 교차)
        var b = '#2a0f1a', l = '#4a2030', s = '#c0283a';
        pt.R(15, 34, 6, 12, b); pt.R(27, 34, 6, 12, b); pt.R(13, 44, 8, 3, '#140810'); pt.R(27, 44, 8, 3, '#140810');
        pt.E(24, 27, 11, 10, b); pt.E(21, 24, 5, 5, l);
        pt.R(16, 4, 16, 14, b); pt.R(17, 3, 14, 2, l); pt.R(18, 9, 12, 4, '#e8c8a0'); pf.R(20, 10, 3, 2, '#ffe14d'); pf.R(26, 10, 3, 2, '#ffe14d');
        pt.R(15, 17, 18, 3, s);
        for (var i = 0; i < 16; i++) { var wy = 18 + Math.round(Math.sin(i * 0.6 + (f ? 1.6 : 0)) * 2) + (i >> 2); pt.R(14 - i, wy, 1, 3, s); }
        if (pose) { pt.R(19, 24, 10, 4, b); pt.L(14, 34, 34, 14, '#c3cad1', 2); pt.L(34, 34, 14, 14, '#c3cad1', 2); pf.D(24, 24, 2, '#b48aff', '#ffffff'); }
        else {
          pt.R(8, 24, 6, 4, b); pt.R(34, 24, 6, 4, b);
          pt.L(6, 26, 2, 6, '#c3cad1', 2); pt.L(42, 26, 46, 6, '#c3cad1', 2); pt.P(2, 5, '#ffffff'); pt.P(46, 5, '#ffffff'); pt.R(5, 25, 4, 2, '#3a3f4a'); pt.R(40, 25, 4, 2, '#3a3f4a');
          pf.L(3, 9 + (f ? 1 : 0), 5, 22, '#b48aff'); pf.L(45, 9 + (f ? 1 : 0), 43, 22, '#b48aff');
        }
      },
      lavagiant: function (pt, pf, f) { // 용암 거신 -- 검은 바위 피부 사이로 흐르는 용암, 일렁이는 불꽃 머리
        var b = '#2a1a14', d = '#1a0e0a', h = '#4a3020';
        pt.R(14, 34, 8, 13, b); pt.R(26, 34, 8, 13, b);
        pt.E(24, 25, 16, 12, b); pt.E(17, 21, 8, 5, h); pt.E(30, 30, 9, 6, d);
        pt.E(6, 22, 6, 7, b); pt.E(42, 22, 6, 7, b); pt.R(2, 26, 8, 12, b); pt.R(38, 26, 8, 12, b); pt.E(6, 39, 5, 4, d); pt.E(42, 39, 5, 4, d);
        pt.E(24, 11, 8, 7, b); pt.E(22, 9, 4, 3, h);
        [[14, 20, 20, 30], [28, 18, 32, 27], [20, 31, 26, 35], [5, 24, 7, 33], [41, 25, 43, 34], [17, 37, 18, 45], [30, 37, 29, 45]].forEach(function (q) { pf.L(q[0], q[1], q[2], q[3], '#ff6a1a'); pf.L(q[0] + 1, q[1], q[2] + 1, q[3], f ? '#fff0a0' : '#ffd040'); });
        pf.R(20, 10, 3, 2, '#fff6b0'); pf.R(26, 10, 3, 2, '#fff6b0');
        for (var x = 15; x <= 33; x++) { // 불꽃 머리 -- 높이가 들쭉날쭉, 프레임마다 일렁임
          var fh = Math.max(1, Math.round(4 + 2.5 * Math.sin(x * 1.7 + (f ? 2.2 : 0)) + 1.5 * Math.sin(x * 0.55) - Math.abs(x - 24) * 0.18));
          for (var k = 0; k < fh; k++) pf.P(x, 5 - k, k < fh * 0.35 ? '#fff0a0' : k < fh * 0.7 ? '#ff9a20' : '#e8401a');
        }
        pf.P(6, 44 + (f ? 1 : 0), '#ff8a1a'); pf.P(42, 45 - (f ? 1 : 0), '#ff8a1a');
      },
      thunderbull: function (pt, pf, f) { // [2026-11-05] 천둥 황소 -- 짙은 남색 황소, 금빛 뿔, 푸른 눈, 몸을 타고 흐르는 번개(프레임마다 갈래가 바뀜)
        var b = '#2a3058', d = '#181c38', h = '#46508a', k = '#0e0e1a', u = f ? 1 : 0;
        pt.R(10, 34, 6, 11, b); pt.R(19, 35, 5, 10, d); pt.R(28, 35, 5, 10, d); pt.R(35, 34, 6, 11, b);
        pt.R(9, 44, 8, 3, k); pt.R(18, 44, 7, 3, k); pt.R(27, 44, 7, 3, k); pt.R(34, 44, 8, 3, k);
        pt.E(26, 28, 18, 10, b); pt.E(23, 23, 12, 5, h); pt.E(31, 33, 12, 5, d); pt.E(18, 19, 7, 5, d);
        pt.E(10, 21, 9, 9, b); pt.E(8, 18, 5, 4, h); pt.E(6, 27, 6, 4, d); pt.P(3, 27, k); pt.P(7, 28, k);
        pt.L(5, 14, 1, 6, '#f0d878', 2); pt.L(14, 13, 19, 5, '#f0d878', 2); pt.P(1, 5, '#fff6c8'); pt.P(19, 4, '#fff6c8');
        pt.L(43, 24, 46, 34, d, 2); pt.E(46, 36, 2, 2, '#f0d878');
        pf.R(6, 19, 2, 2, '#bff8ff'); pf.R(12, 19, 2, 2, '#bff8ff');
        var zz = f ? [[14, 22, 20, 28, 16, 33], [30, 20, 26, 27, 33, 31], [38, 26, 42, 32, 39, 38], [22, 30, 27, 34, 24, 40]] : [[18, 21, 24, 27, 20, 32], [32, 22, 28, 28, 35, 33], [10, 30, 14, 36, 11, 41], [40, 22, 36, 28, 41, 33]];
        zz.forEach(function (q) { pf.L(q[0], q[1], q[2], q[3], '#ffe94a'); pf.L(q[2], q[3], q[4], q[5], '#ffe94a'); pf.P(q[2], q[3], '#ffffff'); });
        pf.L(2, 6 - u, 0, 1 - u, '#bff8ff'); pf.L(19, 4 + u, 22, 0 + u, '#bff8ff');
      },
      antqueen: function (pt, pf, f) { // 여왕개미 -- 줄무늬 큰 배, 왕관 더듬이, 붉은 눈, 독침의 독
        var b = '#c03a6a', d = '#801a40', h = '#e86a98';
        pt.E(13, 31, 12, 9, b); for (var x = 4; x < 24; x += 4) pt.R(x, 24, 1, 15, d); pt.E(10, 27, 5, 3, h);
        pt.E(28, 27, 6, 6, b); pt.E(38, 20, 7, 6, b); pt.E(36, 18, 3, 2, h);
        pf.R(40, 18, 2, 2, '#ff3030'); pf.R(36, 18, 2, 2, '#ff3030');
        pt.L(43, 23, 47, 26, d, 2); pt.L(43, 24, 46, 28, d);
        pt.L(36, 14, 32 - (f ? 1 : 0), 4, d); pt.L(40, 14, 44 + (f ? 1 : 0), 4, d); pt.D(32 - (f ? 1 : 0), 4, 1, '#ffd23f', '#fff6b0'); pt.D(44 + (f ? 1 : 0), 4, 1, '#ffd23f', '#fff6b0'); pt.R(33, 13, 10, 2, '#ffd23f');
        [[24, 32, 20 - (f ? 1 : 0), 42], [28, 33, 27, 44], [32, 32, 36 + (f ? 1 : 0), 43], [25, 29, 19, 37], [31, 29, 37, 37]].forEach(function (q) { pt.L(q[0], q[1], q[2], q[3], d, 2); });
        pt.L(2, 34, 0, 38, d, 2); pf.P(0, 40 + (f ? 1 : 0), '#7fff60'); pf.P(1, 41 + (f ? 1 : 0), '#4fd040');
      }
    };
    return function (name, f, pose) {
      var c = mk(48, 48), fx = mk(48, 48);
      ART[name](painter(c.getContext('2d')), painter(fx.getContext('2d')), f, pose);
      outline(c, '#140d0a');
      c.getContext('2d').drawImage(fx, 0, 0);
      return c;
    };
  })();
  var EYE_PX = { mammoth: [[12, 5]], quad: [[13, 6]], human: [[6, 4], [9, 4]], small: [[12, 7]], flyer: [[7, 7], [8, 7]], crab: [[6, 7], [9, 7]], blob: [[6, 10], [9, 10]] }; // [2026-11-05] 몬스터 그림(16칸)의 눈 자리 -- 밤 붉은 눈
  function mobSprite(k, pose, f) {
    var art = BOSS_ART[(map.mobTypes[k] || {}).name]; // [2026-10-29] 보스 전용 그림(48x48, 2프레임)
    if (art) { var bk = 'b' + k + (pose ? 'p' : '') + ((f | 0) % 2); if (!mobSprites[bk]) mobSprites[bk] = bossArt(art, (f | 0) % 2, pose); return mobSprites[bk]; }
    var ck = pose ? k + 'p' : k; // [2026-10-28] pose = 닌자 두목 분신술 중 진짜(인을 맺은 자세)
    if (mobSprites[ck]) return mobSprites[ck];
    var ty = map.mobTypes[k] || {}, c = mk(T, T), g = c.getContext('2d'), col = ty.color || '#888888', dk = shade(col, -0.35), lt = shade(col, 0.3);
    var eye = ty.tier >= 4 ? '#ff3b30' : '#1a1a1a';
    switch (ty.kind) {
      case 'ninja': // [2026-10-28] 닌자 -- 두건 + 눈가만 드러난 복면, 붉은 목도리. 보통은 팔을 내리고 쿠나이, pose면 가슴 앞에서 두 손을 모아 인을 맺는다
        px(g, dk, 5, 12, 2, 3); px(g, dk, 9, 12, 2, 3); px(g, col, 4, 7, 8, 5); px(g, lt, 5, 8, 1, 3);
        px(g, col, 4, 1, 8, 6); px(g, dk, 4, 0, 8, 1); px(g, '#e8c8a0', 5, 3, 6, 2); px(g, eye, 6, 3); px(g, eye, 9, 3);
        px(g, '#c0283a', 4, 7, 8, 1); px(g, '#c0283a', 12, 6, 2, 1); px(g, '#c0283a', 13, 7, 2, 1);
        if (pose) { px(g, dk, 4, 8, 3, 1); px(g, dk, 9, 8, 3, 1); px(g, '#e8c8a0', 7, 7, 2, 3); px(g, '#e8c8a0', 7, 5, 1, 2); px(g, '#b48aff', 6, 4, 1, 1); px(g, '#b48aff', 9, 5, 1, 1); }
        else { px(g, dk, 3, 8, 1, 3); px(g, dk, 12, 8, 1, 3); px(g, '#c3cad1', 13, 9, 1, 3); px(g, '#c3cad1', 12, 11, 3, 1); }
        break;
      case 'small':
        px(g, col, 4, 8, 8, 6); px(g, lt, 5, 9, 4, 2); px(g, col, 9, 5, 5, 5); px(g, dk, 10, 4, 1, 2); px(g, dk, 12, 4, 1, 2);
        px(g, eye, 12, 7); px(g, dk, 2, 7, 2, 4); px(g, dk, 5, 14, 2, 1); px(g, dk, 9, 14, 2, 1); break;
      case 'mammoth': // [2026-11-05] 왕도 서쪽길 매머드 -- 덥수룩한 큰 몸, 둥근 머리, 늘어진 코, 하얀 엄니
        px(g, col, 1, 4, 11, 8); px(g, lt, 2, 4, 9, 1); px(g, dk, 1, 10, 11, 2); px(g, col, 10, 3, 5, 6); px(g, lt, 11, 3, 3, 1);
        px(g, dk, 14, 8, 1, 5); px(g, dk, 13, 12, 1, 1); px(g, '#f4ecd8', 11, 9, 1, 2); px(g, '#f4ecd8', 12, 10, 2, 1); px(g, eye, 12, 5);
        px(g, dk, 0, 5, 1, 3); px(g, dk, 2, 12, 2, 3); px(g, dk, 5, 12, 2, 3); px(g, dk, 8, 12, 2, 3); px(g, dk, 10, 12, 2, 3); break;
      case 'quad':
        px(g, col, 2, 7, 10, 5); px(g, lt, 3, 7, 8, 1); px(g, col, 11, 4, 4, 5); px(g, dk, 12, 3, 1, 2); px(g, dk, 14, 3, 1, 2);
        px(g, eye, 13, 6); px(g, dk, 15, 7, 1, 1); px(g, dk, 1, 6, 2, 2);
        px(g, dk, 3, 12, 2, 3); px(g, dk, 6, 12, 2, 3); px(g, dk, 9, 12, 2, 3); px(g, dk, 11, 12, 1, 3); break;
      case 'human':
        px(g, dk, 5, 12, 2, 3); px(g, dk, 9, 12, 2, 3); px(g, col, 4, 7, 8, 5); px(g, lt, 5, 7, 6, 1); px(g, dk, 3, 8, 1, 3); px(g, dk, 12, 8, 1, 3);
        px(g, lt, 4, 1, 8, 6); px(g, col, 4, 0, 8, 2); px(g, eye, 6, 4); px(g, eye, 9, 4); px(g, '#c3cad1', 13, 4, 1, 7); break; // 손에 든 칼
      case 'flyer':
        px(g, col, 6, 5, 4, 6); px(g, lt, 7, 5, 2, 2); px(g, dk, 1, 4, 5, 2); px(g, dk, 10, 4, 5, 2); px(g, dk, 2, 6, 3, 1); px(g, dk, 11, 6, 3, 1);
        px(g, eye, 7, 7); px(g, eye, 8, 7); break;
      case 'crab':
        px(g, col, 3, 8, 10, 5); px(g, lt, 4, 8, 8, 1); px(g, dk, 1, 6, 3, 3); px(g, dk, 12, 6, 3, 3);
        px(g, eye, 6, 7); px(g, eye, 9, 7); for (var i = 0; i < 3; i++) { px(g, dk, 2 + i * 2, 13, 1, 2); px(g, dk, 9 + i * 2, 13, 1, 2); } break;
      case 'blob':
        for (var y = 6; y < 15; y++) { var hw = Math.round(6 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 13) / 7, 2)))); px(g, y < 9 ? lt : col, 8 - hw, y, hw * 2, 1); }
        px(g, eye, 6, 10); px(g, eye, 9, 10); px(g, '#ffffff', 5, 8, 2, 1); break;
      case 'snake':
        px(g, col, 2, 12, 6, 2); px(g, col, 6, 9, 2, 4); px(g, col, 6, 9, 6, 2); px(g, col, 10, 5, 2, 5); px(g, col, 10, 4, 4, 3);
        px(g, lt, 3, 12, 4, 1); px(g, eye, 12, 5); px(g, '#e8433a', 14, 6, 1, 1); break;
      default:
        px(g, col, 4, 6, 8, 8); px(g, eye, 6, 9); px(g, eye, 9, 9);
    }
    mobSprites[ck] = outline(c, '#140d0a');
    return mobSprites[ck];
  }
  /** 플레이어 옆(바라보는 칸 먼저) 살아 있는 몬스터 -- { m, dir }. */
  function adjMob() {
    var self = mobOcc[P.x + ',' + P.y]; // [2026-10-29] "공격할 때 본인 위치도" -- 내 칸에 겹친 몬스터부터
    if (self) return { m: self, dir: P.dir };
    var dirs = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }, f = dirs[P.dir], m = f && mobOcc[(P.x + f[0]) + ',' + (P.y + f[1])];
    if (m) return { m: m, dir: P.dir };
    for (var key in dirs) { var v = dirs[key], mm = mobOcc[(P.x + v[0]) + ',' + (P.y + v[1])]; if (mm) return { m: mm, dir: key }; }
    // [2026-10-28] "공격 버튼은 내 캐릭터 주변 동서남북을 포함한 8방향을 타겟으로" -- 상하좌우에 없으면 대각선(바라보는 쪽은 좌우)
    var diag = [[-1, -1], [1, -1], [-1, 1], [1, 1]];
    for (var di = 0; di < diag.length; di++) { var dm = mobOcc[(P.x + diag[di][0]) + ',' + (P.y + diag[di][1])]; if (dm) return { m: dm, dir: diag[di][0] < 0 ? 'left' : 'right' }; }
    return null;
  }
  /** 서버 몬스터 상태 [id, hp, deadUntil, fx, fy, frozenUntil] -> 화면 상태. 멈춘(쫓아오는) 칸이 바뀌었으면 옛 칸에서 걸어오게. */
  // [2026-11-06] "몬스터가 쓰러지는 이미지" -- 화면에 보이던 몬스터가 막 쓰러지면(살아 있던 상태 -> 쓰러짐) 그 자리에서 옆으로 넘어지며 흐려지고 먼지가 인다.
  //  맵에 막 들어온 첫 목록(예전에 쓰러진 것들)은 빼려고 mobDeathReady(첫 몬스터 목록을 받은 뒤 true).
  var mobDeaths = [], mobDeathReady = false;
  function mobDeathAdd(id) {
    var mb = map && map.mobs ? map.mobs[id] : null, p = mb && mb._p, ty = mb && map.mobTypes[mb.k];
    if (!p || !ty || ty.boss || BOSS_ART[ty.name]) return;
    mobDeaths.push({ k: mb.k, x: p.x, y: p.y, t0: performance.now(), dir: mb.id % 2 ? 1 : -1 });
    if (mobDeaths.length > 30) mobDeaths.shift();
  }
  function mobDeathDraw(now, TS, camX, camY) {
    mobDeaths = mobDeaths.filter(function (d) { return now - d.t0 < 900; });
    mobDeaths.forEach(function (d) {
      var q = (now - d.t0) / 900, fall = Math.min(1, q / 0.32), x = d.x * TS - camX, y = d.y * TS - camY, sc = TS / 16;
      ctx.save();
      ctx.globalAlpha = q < 0.55 ? 1 : 1 - (q - 0.55) / 0.45;
      ctx.translate(x + TS / 2, y + TS - 2 * sc + (q > 0.55 ? (q - 0.55) * 6 * sc : 0));
      ctx.rotate(d.dir * fall * fall * Math.PI / 2); // 발밑을 축으로 옆으로 넘어진다
      if (q < 0.18) ctx.filter = 'brightness(2.2)'; // 맞은 순간 하얗게 번쩍
      ctx.drawImage(mobSprite(d.k), -TS / 2, -TS, TS, TS);
      ctx.restore();
      if (fall >= 1 && q < 0.8) for (var i = 0; i < 5; i++) { var a2 = Math.PI * (0.9 + i * 0.3), r2 = (3 + (q - 0.32) * 18) * sc; fxDot(x + TS / 2 + d.dir * 5 * sc + Math.cos(a2) * r2, y + TS - 2 * sc + Math.sin(a2) * r2 * 0.4, (1.6 - q) * sc, 'rgba(210,190,150,.8)', 0.8 - q); }
      ctx.globalAlpha = 1;
    });
  }
  function mobStOf(a, old) {
    var n = { hp: a[1], du: a[2], fx: a[3], fy: a[4], fu: a[5], sl: a[6] === 1, su: a[7] || 0, vu: a[8] || 0, vp: a[9] || 0 }, t = nowSrv(); // [2026-10-28] sl = 닌자 은신 중, [2026-11-07] su = 일섬참 기절 끝 시각
    if (mobDeathReady && n.du > t && n.du < t + 3600000 && !(old && old.du > t)) mobDeathAdd(a[0]); // [2026-11-06] 쓰러짐(수하 숨김 = 아주 먼 시각은 뺀다)
    // [2026-11-05] "화면 안인데도 다가올 때 끊겨 보인다" -- 예전엔 새 위치가 오면 옛 목표 칸에서 다시 출발(덜 걸었으면 툭 튐)하고 늘 0.95초만 걸었다(턴이 늦으면 멈칫).
    //  이제 지금 보이는 자리에서 출발하고, 걷는 시간은 지난번 위치가 바뀐 뒤 흐른 시간(0.6~2.2초)이라 다음 위치가 올 즈음 도착한다.
    if (old && old.fu > t && n.fu > t && (old.fx !== n.fx || old.fy !== n.fy)) {
      var omd = old.md || 950, cx0 = old.fx, cy0 = old.fy;
      if (old.ox != null && t - old.mt < omd) { var oq = Math.max(0, (t - old.mt) / omd); cx0 = old.ox + (old.fx - old.ox) * oq; cy0 = old.oy + (old.fy - old.oy) * oq; }
      n.ox = cx0; n.oy = cy0; n.mt = t; n.md = Math.max(600, Math.min(2200, old.pt ? t - old.pt : 950)); n.pt = t;
    }
    else if (n.fu > t && !(old && old.fu > t) && map && map.mobs && map.mobs[a[0]]) { // [2026-11-05] "다가올 때 순간이동" -- 막 쫓기 시작: 어슬렁거리던 자리에서 걸어온다
      var wp = mobPos(map.mobs[a[0]], t);
      if (wp && (Math.abs(wp.x - n.fx) + Math.abs(wp.y - n.fy)) > 0.05 && Math.abs(wp.x - n.fx) + Math.abs(wp.y - n.fy) < 12) { n.ox = wp.x; n.oy = wp.y; n.mt = t; n.md = 950; }
      n.pt = t;
    }
    else if (old && old.mt != null && t - old.mt < (old.md || 950)) { n.ox = old.ox; n.oy = old.oy; n.mt = old.mt; n.md = old.md; n.pt = old.pt; } // [2026-11-03] 450 -> 950(1초 턴 사이를 이어서)
    else if (old && old.pt && old.fx === n.fx && old.fy === n.fy) n.pt = old.pt;
    return n;
  }
  function pollMobs() {
    if (!opened || !map || !map.mobs || !map.mobs.length) return;
    var mid = map.id;
    api('/api/tower-mine-mobs?mapId=' + encodeURIComponent(mid)).then(function (d) { applyMobs(d, mid); }) /* [2026-11-06] 소켓이 열려 있으면 소켓으로 */.catch(function () { });
  }
  function applyMobs(d, mid) {
    if (!map || map.id !== mid || !d) return;
    if (d.now) srvOff = d.now - Date.now();
    var ns = {}, tn = nowSrv();
    (d.mobs || []).forEach(function (a) { ns[a[0]] = mobStOf(a, mobSt[a[0]]); });
    if (d.near) for (var nid in mobSt) { // [2026-11-06] 큰 맵은 서버가 내 둘레(near)만 보낸다 -- 그 밖의 예전 상태는 그대로 둔다
      var no = mobSt[nid], nb = map.mobs[nid];
      if (!ns[nid] && no && nb && Math.max(Math.abs(nb.x - d.near[0]), Math.abs(nb.y - d.near[1])) > d.near[2] && Math.max(Math.abs((no.fx | 0) - d.near[0]), Math.abs((no.fy | 0) - d.near[1])) > d.near[2]) ns[nid] = no;
    }
    for (var id in mobSt) { // [2026-11-03] 방금(1.5초 안) 공격하며 세운 몬스터는 그 응답이 오기 전에 만든 목록이 덮지 않게
      var o = mobSt[id];
      if (o && o.loc && tn - o.loc < 1500 && (!ns[id] || ns[id].fu < o.fu)) ns[id] = o;
    }
    thunderUntil = d.hideUntil && d.hideUntil > tn ? d.hideUntil : 0; // [2026-11-05] 천둥 황소 남은 시간(맵 안 모두에게)
    if (d.trees) { d.trees.forEach(function (q) { var o = treeSt[q[0]]; if (q[1] === 3 && o && (o[1] | 0) === 0 && !treeFallAt[q[0]]) treeFallAt[q[0]] = performance.now(); if (q[1] !== 3) delete treeFallAt[q[0]]; }); treeSt = d.trees; treeNeed = d.treeNeed || treeNeed; } // [2026-11-08] 하피의 둥지, [2026-11-11] 쓰러지는 모습
    harpySt = d.harpy || null;
    if (d.thunderNeed) { thunderKills = d.thunderKills || 0; thunderNeed = d.thunderNeed; thunderRestAt = d.thunderRestAt || 0; } // [2026-11-06] 절반부터 전기 // [2026-11-08] 쓰러진 뒤 2시간 쉼
    if (d.hideUntil && d.hideUntil > tn) { // [2026-11-05] 천둥 황소가 나와 마물(보스 제외)이 모두 숨었다 -- 서버는 줄을 보내지 않고 시각만
      map.mobs.forEach(function (mb) { var mt = map.mobTypes[mb.k]; if (mt && !mt.boss) ns[mb.id] = { hp: 0, du: d.hideUntil, fx: mb.x, fy: mb.y, fu: 0 }; });
      if (hideNoticeT !== d.hideUntil) { hideNoticeT = d.hideUntil; flashT0 = performance.now(); msg('⚡ 마물들이 한순간에 숲 속으로 흩어졌어요... 숲 한가운데에서 천둥이 울려요!', 4500); }
    }
    mobSt = ns;
    mobDeathReady = true; // [2026-11-06] 첫 목록을 받은 뒤부터 쓰러짐 그림
  }

  // ---------------------------------------------------------------- 화면
  function $(id) { return document.getElementById(id); }
  function bindDom() {
    if (bound) return;
    bound = true;
    el.ov = $('mineOverlay'); el.cv = $('mineCanvas'); el.coord = $('mineCoord'); el.terr = $('mineTerr'); el.ping = $('minePing');
    el.sta = $('mineSta'); el.regen = $('mineRegen'); el.mini = $('mineMini'); el.dig = $('mineDigBtn'); el.atk = $('mineAtkBtn'); el.ult = $('mineUlt'); el.ultCd = $('mineUltCd'); el.ultRst = $('mineUltRst'); el.menu = $('mineMenu'); el.menuBtn = $('mineMenuBtn'); // [2026-10-28] 공격 버튼 분리, ☰ 메뉴
    el.msg = $('mineMsg'); el.load = $('mineLoading'); el.log = $('mineLog'); el.smith = $('mineSmith');
    el.hp = $('mineHp'); el.auto = $('mineAutoBtn'); // [2026-10-24] 광산 레벨/체력, 자동채광
    el.cold = $('mineCold'); // [2026-10-26] 한기/안전지대/눈바람 남은 시간
    el.greet = $('mineGreetBtn'); // [2026-10-24] 안녕하세요
    el.patch = $('minePatch'); // [2026-10-28] 패치 전 공지 띠
    ctx = el.cv.getContext('2d'); miniCtx = el.mini.getContext('2d');
    el.cv.addEventListener('pointerdown', function (e) {
      el.cv.setPointerCapture && el.cv.setPointerCapture(e.pointerId);
      drag = { ox: e.clientX, oy: e.clientY, cx: e.clientX, cy: e.clientY };
    });
    el.cv.addEventListener('pointermove', function (e) { if (drag) { drag.cx = e.clientX; drag.cy = e.clientY; } });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (t) { el.cv.addEventListener(t, function () { drag = null; }); });
    window.addEventListener('resize', function () { if (opened) fit(); });
    window.addEventListener('pagehide', function () { if (opened) saveBeacon(true); });
    document.addEventListener('visibilitychange', function () {
      if (!opened) return;
      if (document.visibilityState === 'hidden') saveBeacon(true);
      else if (exitSent && map) { exitSent = false; api('/api/tower-mine?enter=1').catch(function () { }); } // 다른 앱/탭에서 돌아옴 = 다시 입장
    });
    document.addEventListener('keydown', function (e) {
      if (!opened) return;
      var d = keyDir(e.key);
      if (d) { e.preventDefault(); if (keys.indexOf(d) < 0) keys.push(d); tapDir = d; return; } // 짧게 눌렀다 떼도 한 칸은 움직이게
      if (smithOpen) { if (e.key === 'Escape') closeSmith(); else if (e.key === 'Enter' && smithView === 'warp') adminWarp(); return; } // 대장간 창이 열려 있으면 이동/캐기 대신 창만
      // [2026-10-29] "PC에서 Z키로 채광, 스페이스키로 공격" -- 화면 버튼(⛏️ 채광 / ⚔️ 공격)과 똑같이. 한글 입력 상태에서도 되게 e.code로도 본다.
      //  Enter는 예전처럼 옆에 몬스터가 있으면 공격, 없으면 캐기.
      if (e.key === ' ' || e.code === 'Space') { e.preventDefault(); atkBtn(); }
      else if (e.code === 'KeyZ' || e.key === 'z' || e.key === 'Z') { e.preventDefault(); digBtn(); }
      else if ((e.code === 'KeyX' || e.key === 'x' || e.key === 'X') && st && st.ult) { e.preventDefault(); ultBtn(0); } // [2026-11-07] 필살기 단축키 -- X 만검진(왼쪽) / C 일섬참(오른쪽)
      else if ((e.code === 'KeyC' || e.key === 'c' || e.key === 'C') && st && st.ult) { e.preventDefault(); ultBtn(1); }
      else if (e.key === 'Enter') { e.preventDefault(); act(); } // [2026-10-24] 옆에 몬스터가 있으면 공격, 없으면 캐기
      else if (e.key === 'Escape') close();
    });
    document.addEventListener('keyup', function (e) {
      var d = keyDir(e.key);
      if (d) keys = keys.filter(function (k) { return k !== d; });
    });
  }
  function keyDir(k) {
    switch (k) {
      case 'ArrowUp': case 'w': case 'W': return 'up';
      case 'ArrowDown': case 's': case 'S': return 'down';
      case 'ArrowLeft': case 'a': case 'A': return 'left';
      case 'ArrowRight': case 'd': case 'D': return 'right';
    }
    return null;
  }
  function fit() {
    dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    el.cv.width = Math.floor(el.cv.clientWidth * dpr);
    el.cv.height = Math.floor(el.cv.clientHeight * dpr);
    // 화면 짧은 변 기준 약 9칸이 보이도록 정수배(도트가 뭉개지지 않게)
    scale = Math.max(2, Math.round(Math.min(el.cv.width, el.cv.height) / (T * 9)));
    ctx.imageSmoothingEnabled = false;
  }
  /** [2026-10-30] "접속 시 1회만 공지" -- 이 기기에서 한 번만(새 공지는 키를 바꾼다). */
  function noticeOnce() {
    try {
      if (localStorage.getItem('tw-mine-notice') === 'pick0') return;
      localStorage.setItem('tw-mine-notice', 'pick0');
    } catch (e) { return; }
    setTimeout(function () { msg('📢 곡괭이 내구도가 없어도 채집이 가능해졌어요!', 6000); }, 800);
  }
  /** [2026-10-30] "멘트에 엔터" -- 문장(. ! ? 뒤 띄어쓰기)마다 줄을 바꾼다. 화면은 white-space:pre-line. */
  function brk(text) { return String(text == null ? '' : text).replace(/([.!?…]+)[ \t]+(?=\S)/g, '$1\n'); }
  function msg(text, ms) {
    el.msg.textContent = brk(text);
    el.msg.style.display = 'block';
    clearTimeout(msgTimer);
    msgTimer = setTimeout(function () { el.msg.style.display = 'none'; }, ms || 2600);
  }
  function cellAt(x, y) { return map && x >= 0 && y >= 0 && x < map.w && y < map.h ? map.rows[y].charAt(x) : '^'; }
  function walk(x, y) {
    var c = cellAt(x, y);
    if (st && st.look && st.look.shoesCode === 'SHOE_GM') return !!map && x >= 0 && y >= 0 && x < map.w && y < map.h && PORTALS.indexOf(c) < 0 && c !== 'N'; // [2026-10-28] 바람의 신(운영자) -- 장애물/몬스터 통과, NPC/포탈 칸에선 멈춘다(말 걸기/들어가기)
    if (mobOcc[x + ',' + y]) return false; // [2026-10-24] 몬스터가 있는 칸은 못 지나간다
    if (antGate(x)) return false; // [2026-11-04] 사막 서쪽 방벽 문 -- Lv.41 미만은 서쪽으로 못 지나간다(바람에도)
    return WALK.indexOf(c) >= 0 || (c === '~' && (!!(st && st.waterWalk) || !!(map && map.boat))); // [2026-10-24] 용왕의 반지 = 물 위 걷기, [2026-10-28] 다도해 뱃길 = 배
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  // [2026-10-23] 스태미너 -> 곡괭이 내구도(캘 때마다 1, 시간 회복 없음, 마을 대장장이에게 광석으로 강화 = 최대/현재 함께 증가)
  function resetText(ms) {
    if (!ms) return '';
    var d = new Date(ms);
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }
  function showStamina() {
    el.sta.textContent = st.durability + ' / ' + st.durabilityMax; // [2026-10-30] 채광 버튼 꼬리표
    el.sta.classList.toggle('zero', st.durability < 1);
    // [2026-10-24] 오른쪽 위 안내는 맵 리셋 시각만(대장장이 안내/보석 목록은 뺐다)
    regenText();
    el.dig.disabled = busy; // 내구도가 없어도 누를 수 있다(캐기는 dig()가 안내)
    if (el.atk) el.atk.disabled = busy;
  }
  // [2026-10-24] 광산 레벨/체력/경험치(체력은 서버가 준 시각부터 60초에 다 차게 화면도 같이 올린다)
  var combatT0 = 0, hpShownT = 0;
  // [2026-10-29] 무기 속성 부여 -- 공격 버튼 위에 속성 아이콘 + 남은 횟수
  function updEnch() {
    if (!el.atk) return;
    var e = st && st.ench, on = e && e.left > 0 && e.el;
    el.atk.innerHTML = '⚔️' + (on ? '<small class="ench">' + ELEM_ICON[e.el] + e.left + '</small>' : '');
    el.atk.title = '무기로 공격 (스페이스)' + (on ? ' -- ' + ELEM_NAME[e.el] + ' 속성 ' + e.left + '번 남음' : '');
  }
  function setCombat(c) {
    if (!c) return;
    updEnch();
    st.combat = c; combatT0 = Date.now(); renderHp();
    // [2026-10-28] 환생 가능(99레벨 경험치 가득) -- 화면을 연 뒤 처음 한 번만 안내(잡는 순간엔 서버 처치 메시지에도 같은 안내가 붙는다)
    if (c.rebirthReady && !rbHintShown) { rbHintShown = true; msg('✨ 경험치가 가득 찼어요! 왕도 12시 방향 성당의 신부님께 가면 환생할 수 있어요.', 4200); }
  }
  var rbHintShown = false;
  /** [2026-10-28] "Lv.1(환생1)" -- 칸이 좁으면(체력 숫자가 길면) "Lv.1✦1"로 줄인다(마우스를 올리면 전체). */
  function lvLabel(c, compact) {
    var rb = c.rebirth | 0;
    if (!rb) return 'Lv.' + c.lv;
    return 'Lv.' + c.lv + '<span class="rb">' + (compact ? (rb > RB_BASE ? '✦진' + (rb - RB_BASE) : '✦' + rb) : '(' + rbName(rb) + ')') + '</span>';
  }
  /** [2026-11-08] "환생3" / "진환생1"(6환생부터 진환생 -- 서버 mineRbName). */
  function rbName(rb) { return rb > RB_BASE ? '진환생' + (rb - RB_BASE) : '환생' + rb; }
  function rbStage(rb) { return rb > RB_BASE ? '진환생 ' + (rb - RB_BASE) + '단계' : '환생 ' + rb + '단계'; }
  function hpNow() {
    var c = st && st.combat;
    if (!c) return 0;
    // [2026-10-26] 서버와 같이 마지막 전투 뒤 대기(regenFrom, 서버 시각)가 지나야 차오른다. c.hp = 서버 시각 c.at 때의 체력.
    // [2026-10-28] 전투 중에도 1/4 속도로(대기 없이), 그늘/모닥불 등 안전지대에선 x1.5(서버 mineHpNow와 같은 식)
    var tSrv = c.at + (Date.now() - combatT0), from = c.fight ? c.at : Math.max(c.at, c.regenFrom || 0);
    var mul = (c.fight ? (c.regenFight != null ? c.regenFight : 0) : 1) * (inSafe() ? (c.regenSafe || 1) : 1);
    if (st && st.gloveCode === 'BERSERK_GLOVES') mul *= 0.8; // [2026-10-29] 광전사의 장갑 -- 회복 속도 -20%(서버 MINE_BERSERK_REGEN_MUL)
    var h = c.hp + Math.max(0, tSrv - from) * c.hpMax * mul / (c.fullMs || 60000);
    if (st && st.armorRegen > 0) h = Math.min(c.hpMax, h + Math.max(0, tSrv - c.at) * c.hpMax * st.armorRegen / 100 / 1000); // [2026-11-06] 전설 이상 갑옷(서버 mineHpNow와 같은 식)
    if (st && st.gloveCode === 'TWIN_GLOVES') h = Math.max(Math.min(c.hp, 1), h - Math.max(0, tSrv - c.at) * c.hpMax * 0.01 / 1000); // [2026-10-30] 쌍격의 장갑 -- 초당 -1%(서버 mineHpNow와 같은 식)
    return Math.min(c.hpMax, Math.floor(h));
  }
  function renderHp() {
    var c = st && st.combat;
    if (!c || !el.hp) return;
    // [2026-10-25] 최대 체력 잠금 -- 막대는 원래 최대 체력 기준, 잠긴 몫은 오른쪽 회색 빗금
    var base = c.hpBase || c.hpMax, lk = c.hpLock || 0;
    var h = hpNow(), pct = Math.round(h * 100 / base), ep = Math.min(100, Math.round(c.exp * 100 / Math.max(1, c.expNeed)));
    var rb = c.rebirth | 0, ready = !!c.rebirthReady;
    var key = c.lv + ':' + h + ':' + ep + ':' + lk + ':' + rb + ':' + ready;
    if (el.hp._k === key) return;
    el.hp._k = key;
    el.hp.title = (rb ? 'Lv.' + c.lv + '(' + rbName(rb) + ') ' : '') + '체력 ' + h + ' / ' + c.hpMax + (lk ? ' (최대 체력 ' + lk + '% 잠김)' : '')
        + (ready ? ' -- 환생 가능! 왕도 12시 방향 성당 신부님께' : '');
    // [2026-10-28] 환생 꼬리표 -- 폭 112px 안에 들어가면 "Lv.1(환생1)", 넘치면 "Lv.1✦1", 그래도 넘치면(환생 뒤 체력이 7자리) 체력을 "1415k"로
    // [2026-11-05] "체력바 옆 숫자가 UI를 넘어온다" -- 환생 안 한 사람도 넘치면 줄이고(예전엔 환생한 사람만), 1.2만 -> "12.3k", 그래도 넘치면 "1.4M"
    for (var fit = 0; fit < 4; fit++) {
      var hs = fit === 3 && h >= 1000000 ? (h / 1000000).toFixed(h < 10000000 ? 1 : 0) + 'M' : fit >= 2 && h >= 10000 ? (h / 1000).toFixed(h < 100000 ? 1 : 0) + 'k' : String(h);
      el.hp.innerHTML = '<span class="hp-l"><span>' + lvLabel(c, fit > 0) + '</span><span class="hp-n">' + (lk ? '🔒' : '❤') + hs + '</span></span>' // [2026-10-28] 짧게
          + '<span class="mine-bar hp"><i style="width:' + pct + '%"></i>' + (lk ? '<b style="width:' + lk + '%"></b>' : '') + '</span><span class="mine-bar ex' + (ready ? ' ready' : '') + '"><i style="width:' + ep + '%"></i></span>'
          + (ready ? '<span class="hp-rb">✨ 환생 가능!</span>' : '');
      var hl = el.hp.firstChild;
      if (!hl || hl.scrollWidth <= hl.clientWidth + 1) break;
    }
  }
  // [2026-10-24] 자동채광 -- 켜 두면 캐릭터가 스스로 가장 가까운(30칸 안) 안 캔 칸으로 걸어가서 캔다. 방향키/끌기로 잠깐 직접 조작할 수 있다.
  var auto = false, noOre = {};
  function setAuto(on, text) {
    auto = on;
    if (el.auto) { el.auto.classList.toggle('on', on); el.auto.textContent = on ? '🤖 자동채광 ON' : '🤖 자동채광'; }
    if (text) msg(text, 2800);
  }
  function toggleAuto() {
    if (!opened || !map) return;
    setAuto(!auto, !auto ? '🤖 자동채광 시작! 캐릭터가 알아서 주변을 돌며 캐요. (방향키로 잠깐 조작 가능)' : '🤖 자동채광을 껐어요.');
  }
  /** [2026-11-11] "땅파기 불가능한 곳은 그냥 지나치게" -- 서버가 알려 준 광석 없는 칸(map.noOre: {y, x, 길이} 구간)을 맵마다 한 번 표로. */
  function noOreCell(x, y) {
    if (!map || !map.noOre) return false;
    if (!map._noOre) { var s = map._noOre = {}; map.noOre.forEach(function (r) { for (var i = 0; i < r[2]; i++) s[(r[1] + i) + ',' + r[0]] = 1; }); }
    return !!map._noOre[x + ',' + y];
  }
  function minable(x, y) {
    var c = cellAt(x, y), k = x + ',' + y;
    return 'VTYPHe~'.indexOf(c) < 0 && WALK.indexOf(c) >= 0 && !dug[k] && !noOre[k] && !noOreCell(x, y);
  }
  /** 가장 가까운 캘 칸으로 가는 첫 걸음 방향(넓이 우선 탐색, 30칸 안). 없으면 null. */
  function autoStep() {
    var W = map.w, start = P.x + P.y * W, seen = {}, first = {}, q = [start], head = 0;
    var D = [['up', 0, -1], ['down', 0, 1], ['left', -1, 0], ['right', 1, 0]];
    seen[start] = 1;
    while (head < q.length && head < 5000) {
      var cur = q[head++], cx = cur % W, cy = (cur - cx) / W;
      for (var i = 0; i < 4; i++) {
        var nx = cx + D[i][1], ny = cy + D[i][2], ni = nx + ny * W;
        if (seen[ni] || Math.abs(nx - P.x) + Math.abs(ny - P.y) > 30 || !walk(nx, ny)) continue;
        seen[ni] = 1;
        first[ni] = cur === start ? D[i][0] : first[cur];
        if (minable(nx, ny)) return first[ni];
        q.push(ni);
      }
    }
    return null;
  }
  function showCoord() {
    el.coord.textContent = '📍 ' + P.x + ', ' + P.y;
    var c = cellAt(P.x, P.y), g = dug[P.x + ',' + P.y];
    el.terr.textContent = map.name + (st && st.dayStone ? ' (오늘 ' + st.dayLabel + ': ' + st.dayStone + ')' : '') + ' · ' + (TERR_NAME[c] || '')
        + (g ? ' · 캔 자리(' + ORE_NAME[g] + ')' : (c === 'V' ? ' · 여기선 못 캐요' : ''));
  }
  // [2026-10-23] 광부 투구 시야 -- 어두운 맵(동굴)에서 투구 단계 - 필요 단계: 0 이하 아주 어둡게 / 1 잘 보임 / 2 이상 전체(서버 mineMapBlock과 같은 기준)
  function sight() {
    if (!map || !map.dark) return 9;
    return (st && st.helmet || 0) + (st && st.combat ? st.combat.rebirth | 0 : 0) - (map.helmetNeed || 0); // [2026-10-29] "환생 옵션에 동굴 밝기 +1씩" -- 시야만(들어가는 조건은 투구 그대로)
  }
  function helmetBlocked() {
    return map && (st && st.helmet || 0) < (map.helmetNeed || 0);
  }
  // [2026-10-24] 구역별 권장 레벨(서버 MINE_MIN_LV를 st.mapMinLv로 받는다) -- 막지 않고 전체 지도에 표시만("레벨 모자라도 갈 수 있게")
  function myLv() { return st && st.combat ? st.combat.lv : 1; }
  function minLvOf(id) { return st && st.mapMinLv && st.mapMinLv[id] || 1; }
  // [2026-10-28] "설산에서 왕도 통로 미니맵이 늘려 퍼져서 마음에 안 든다" -- 미니맵 칸은 정사각인데 긴 맵(왕도 가는 길 260x44)을 통째로 넣어 옆으로 늘어났다.
  //  가로세로 1.8배 넘게 긴 맵은 플레이어 주변 정사각 창만 보여 주고(따라 움직임), 그 밖의 정사각이 아닌 맵은 늘리지 않고 비율 그대로(CSS object-fit).
  function miniView() {
    var w = map.w, h = map.h;
    if (Math.max(w, h) < 40) return { x0: -Math.floor((40 - w) / 2), y0: -Math.floor((40 - h) / 2), w: 40, h: 40 }; // [2026-11-11] 미궁 방(10x10)처럼 작은 맵은 확대하지 않고 가운데에 작게
    // [2026-10-28] "늘어져 보이는 걸 방지, 좌우로 기니 상하 레터박스, 전체를 볼 수 있어야" -- 긴 맵도 통째로, 정사각 캔버스 가운데에 두고 위아래(좌우) 빈 띠
    if (w > h * 1.8) return { x0: 0, y0: -Math.floor((w - h) / 2), w: w, h: w };
    if (h > w * 1.8) return { x0: -Math.floor((h - w) / 2), y0: 0, w: h, h: h };
    return { x0: 0, y0: 0, w: w, h: h };
  }
  function buildMiniBase() {
    var mv = miniView();
    el.mini.width = mv.w; el.mini.height = mv.h;
    miniBase = mk(map.w, map.h);
    var g = miniBase.getContext('2d'), img = g.createImageData(map.w, map.h);
    var blind = sight() <= 0; // 투구 없이 어두운 동굴 -- 미니맵에도 지형이 안 보인다
    for (var y = 0; y < map.h; y++) for (var x = 0; x < map.w; x++) {
      var c = blind ? [10, 8, 6] : (MINI_COL[map.rows[y].charAt(x)] || [255, 214, 64]), i = (y * map.w + x) * 4;
      img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }
  var NPC_MINI_COL = { smith: '#ff9a2a', boutique: '#c06bff', priest: '#ffffff', guide: '#3fdc6a', captain: '#5ad8ff', furrier: '#ffd27a', blackmarket: '#3a3a3a',
    daykeeper: '#ffe14d', wallguard: '#ffe14d', swordmaster: '#ff6a3a', basecamp: '#bfefff', forestguard: '#ff8a5a', citymerchant: '#7cf08a', mercenary: '#ffb04a', enhancer: '#ff8a3a', whirlmaster: '#8ef08e', ultmaster: '#ffd27a', ninjaquest: '#ff4a4a', boatwright: '#c8964a', ferry: '#3a8aff', chest: '#ffd23f', smelter: '#ff8a3a', gemcutter: '#3ad8d8', artisan: '#c84a4a', archishop: '#3a9aff' };
  function drawMini() {
    var v = miniView(), ox = v.x0, oy = v.y0; // [2026-10-28] 긴 맵은 플레이어 주변 창만
    var k = Math.max(1, Math.round(v.w / 128)); // 큰 맵은 점을 크게
    miniCtx.clearRect(0, 0, v.w, v.h);
    if (v.x0 < 0 || v.y0 < 0) { miniCtx.fillStyle = '#0b0912'; miniCtx.fillRect(0, 0, v.w, v.h); } // 레터박스 띠
    if (map.noMap && !(st && st.adminMaps)) { // [2026-11-06] 관리자는 개미굴에서도 지도가 보인다 // [2026-10-28] 개미굴 -- 지도가 안 보이는 곳(내 위치도, 길도)
      miniCtx.fillStyle = '#120c08'; miniCtx.fillRect(0, 0, v.w, v.h);
      miniCtx.fillStyle = '#6a4a30'; miniCtx.font = 'bold ' + Math.round(v.w / 5) + 'px sans-serif'; miniCtx.textAlign = 'center'; miniCtx.textBaseline = 'middle';
      miniCtx.fillText('?', v.w / 2, v.h / 2);
      return;
    }
    miniCtx.drawImage(miniBase, -ox, -oy);
    Object.keys(dug).forEach(function (key) {
      var p = key.split(','), g = dug[key];
      if (g >= 4 && ORE_COL[g]) { miniCtx.fillStyle = ORE_COL[g][0]; miniCtx.fillRect(+p[0] - ox - k, +p[1] - oy - k, 2 * k + 1, 2 * k + 1); }
    });
    (map.portals || []).forEach(function (p) {
      if (p.kind === 'Z' && !(st && st.dragon && st.dragon.has)) return; // [2026-10-24] 숨은 입구(바닷길)는 미니맵에도 안 찍는다([2026-10-28] 용왕의 반지를 얻으면 보인다)
      miniCtx.fillStyle = p.locked ? '#9a9a9a' : '#ffd23f';
      miniCtx.fillRect(p.x - ox - 2 * k, p.y - oy - 2 * k, 4 * k + 1, 4 * k + 1);
    });
    // [2026-10-28] "미니맵에 NPC 색 표시" -- NPC마다 색 점(테두리 흰색). 대장장이 주황, 상점 보라, 신부 흰색, 안내원 초록, 그 밖 하늘색
    (map.npcs || []).forEach(function (n) {
      var col = NPC_MINI_COL[n.kind] || '#5ad8ff';
      miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(n.x - ox - 2 * k - 1, n.y - oy - 2 * k - 1, 4 * k + 3, 4 * k + 3);
      miniCtx.fillStyle = col; miniCtx.fillRect(n.x - ox - 2 * k, n.y - oy - 2 * k, 4 * k + 1, 4 * k + 1);
    });
    if (hermitOn()) { // [2026-10-30] "그 NPC 지도에 위치 표시" -- 은둔한 대장장이(같은 맵에 나와 있을 때) 금색 점
      miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(hermit.x - ox - 3 * k - 1, hermit.y - oy - 3 * k - 1, 6 * k + 3, 6 * k + 3);
      miniCtx.fillStyle = '#ffc61a'; miniCtx.fillRect(hermit.x - ox - 3 * k, hermit.y - oy - 3 * k, 6 * k + 1, 6 * k + 1);
    }
    // [2026-10-28] "미니맵에서 보스 위치도" -- 맵 맨 끝 몬스터가 보스면(살아 있을 때) 붉은 점(흰 테두리, 크게)
    var bm = map.mobs && map.mobs[map.mobs.length - 1], bty = bm && map.mobTypes[bm.k];
    if (bty && bty.boss && bm._p) {
      miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(bm._p.cx - ox - 3 * k - 1, bm._p.cy - oy - 3 * k - 1, 6 * k + 3, 6 * k + 3);
      miniCtx.fillStyle = '#e8202a'; miniCtx.fillRect(bm._p.cx - ox - 3 * k, bm._p.cy - oy - 3 * k, 6 * k + 1, 6 * k + 1);
    }
    // [2026-10-28] "플레이어 위치가 맵에 나오면 좋겠어, 서로 볼 수 있게" -- 같은 맵의 다른 유저(1초 위치 알림) 하늘색 점, 운영자는 보라
    for (var oid in others) {
      var o = others[oid];
      miniCtx.fillStyle = '#10202a'; miniCtx.fillRect(o.x - ox - 2 * k - 1, o.y - oy - 2 * k - 1, 4 * k + 3, 4 * k + 3);
      miniCtx.fillStyle = o.look && o.look.gm ? '#c06bff' : '#3fd4ff'; miniCtx.fillRect(o.x - ox - 2 * k, o.y - oy - 2 * k, 4 * k + 1, 4 * k + 1);
    }
    miniCtx.fillStyle = '#ff2b2b'; miniCtx.fillRect(P.x - ox - 3 * k, P.y - oy - 3 * k, 6 * k + 1, 6 * k + 1);
    miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(P.x - ox - k, P.y - oy - k, 2 * k + 1, 2 * k + 1);
  }
  // [2026-10-28] "최근 캔 곳에 최근 획득이나 구매한 걸, 사용한 건 안 나와도" -- 위: 최근 획득·구매(창을 열 때 서버에서), 아래: 최근 캔 곳
  var recentGet = [];
  function renderLog() {
    var h = '<div class="mine-log-head">🎁 최근 획득·구매<button type="button" class="mine-mini-btn" style="pointer-events:auto" onclick="TowerMine.toggleLog()">✖ 닫기</button></div>'; // [2026-11-04] 닫기
    if (!recentGet.length) h += '<div class="mine-log-empty">아직 없어요. (서버를 다시 켜면 비워져요)</div>';
    else h += recentGet.slice(0, 20).map(function (e) {
      var body = e.t === 'buy' ? '🛒 ' + e.text : '<b>+' + e.qty + '</b> ' + e.name;
      return '<div class="mine-log-row"><span class="mine-log-src">' + e.src + '</span> ' + body + '<span class="mine-log-when">' + digTime(e.at) + '</span></div>';
    }).join('');
    h += '<div class="mine-log-head" style="margin-top:8px">⛏️ 최근 캔 곳</div>';
    if (!recent.length) h += '<div class="mine-log-empty">아직 캔 곳이 없어요.</div>';
    else h += recent.slice(0, 30).map(function (d) {
      return '<div class="mine-log-row"><span class="mine-log-xy">' + d.mapName + ' (' + d.x + ', ' + d.y + ')</span> <img src="' + oreIcon(d.grade) + '" alt=""> ' + ORE_NAME[d.grade]
          + '<span class="mine-log-when">' + digTime(d.at) + '</span></div>';
    }).join('');
    el.log.innerHTML = h;
  }

  // ---------------------------------------------------------------- 진행
  function curPos(now) {
    if (!P.moving) return { x: P.x, y: P.y };
    var k = Math.min(1, (now - P.t0) / stepMs());
    return { x: P.fx + (P.tx - P.fx) * k, y: P.fy + (P.ty - P.fy) * k };
  }
  function heldDir() {
    if (keys.length) return keys[keys.length - 1];
    if (!drag) return null;
    var dx = drag.cx - drag.ox, dy = drag.cy - drag.oy;
    if (dx * dx + dy * dy < 16 * 16) return null;
    return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
  }
  function update(now) {
    gossipTick(now); // [2026-11-05] 왕도 주민 소문
    // [2026-10-24] 몬스터가 서 있는 칸(지나갈 수 없음, 공격 대상) + ⛏️/⚔️ 버튼 모양 + 체력 표시
    mobOcc = {};
    if (map.mobs) {
      var tS = nowSrv();
      // [2026-11-05] "마물의 숲(1,400마리)이 끊겨 보인다" -- 매 프레임 모든 몬스터의 위치/칸 표를 새로 만들던 것을, 화면 둘레(+4칸) 안 몬스터와
      //  쫓아오는(서버가 붙잡은) 몬스터, 보스만 계산한다. 나머지는 화면 밖이라 그릴 일도, 막거나 공격할 일도 없다.
      var cvw = el.cv ? Math.ceil(el.cv.width / (T * scale) / 2) + 4 : 24, cvh = el.cv ? Math.ceil(el.cv.height / (T * scale) / 2) + 4 : 16, lastI = map.mobs.length - 1;
      for (var mi = 0; mi < map.mobs.length; mi++) {
        var mm = map.mobs[mi], mst = mobSt[mm.id];
        if (mi !== lastI && !(mst && mst.fu > tS) && (Math.abs(mm.x - P.x) > cvw || Math.abs(mm.y - P.y) > cvh)) { mm._p = null; continue; }
        var mp = mobPos(mm, tS);
        if (mp) {
          mm._p = mp; mobOcc[mp.cx + ',' + mp.cy] = mm;
          if ((map.mobTypes[mm.k] || {}).boss) { mobOcc[(mp.cx + 1) + ',' + mp.cy] = mm; mobOcc[mp.cx + ',' + (mp.cy + 1)] = mm; mobOcc[(mp.cx + 1) + ',' + (mp.cy + 1)] = mm; } // [2026-10-29] 보스 = 2x2칸
        } else mm._p = null;
      }
    }
    // [2026-10-28] "무기로 공격 버튼과 채광 버튼 분리" -- ⛏️는 캐기만, ⚔️는 바로 옆 몬스터가 있을 때 또렷해진다
    var near = !P.moving && adjMob();
    if (el.atk && el.atk.classList.contains('near') !== !!near) el.atk.classList.toggle('near', !!near);
    var ultShow = st && st.ult && (((st.ult.l || [])[0] | 0) + ((st.ult.l || [])[1] | 0) > 0 || st.adminMaps) ? '' : 'none', rstShow = ultShow === '' && st.adminMaps ? '' : 'none'; // [2026-11-07] 필살기 버튼(배웠거나 관리자), 관리자 쿨 초기화
    if (el.ult && el.ult.style.display !== ultShow) el.ult.style.display = ultShow;
    if (el.ult && st && st.ult) { var ulq = st.ult.l || []; el.ult.classList.toggle('lk0', !(ulq[0] | 0)); el.ult.classList.toggle('lk1', !(ulq[1] | 0)); } // [2026-11-08] 하나만 배웠으면 안 배운 조각은 흐리게 + 자물쇠
    if (el.ultRst && el.ultRst.style.display !== rstShow) el.ultRst.style.display = rstShow;
    var mmb = $('mineMercMenu'), mmShow = st && st.mercs ? '' : 'none'; // [2026-11-06] 용병 관리 메뉴(지금은 관리자만)
    if (mmb && mmb.style.display !== mmShow) mmb.style.display = mmShow;
    // [2026-10-28] "광석 캐기가 불가능한 지역에서는 캐기 버튼을 없애도" -- 캘 칸 없는 맵은 ⛏️와 자동채광 숨김(⚔️는 그대로)
    var digShow = map.noDig && !map.trees ? 'none' : ''; // [2026-11-11] 하피의 둥지 -- 캐기 버튼 = 곡괭이로 나무 찍기
    if (el.dig.style.display !== digShow) el.dig.style.display = digShow;
    if (el.auto) { var autoShow = map.noDig || map.vein ? 'none' : ''; if (el.auto.style.display !== autoShow) { el.auto.style.display = autoShow; if ((map.noDig || map.vein) && auto) setAuto(false); } } // [2026-10-28] 설산 동굴은 자동채광 없음
    if (now - hpShownT > 500) { hpShownT = now; renderHp(); }
    bossTick(now); // [2026-10-24] 동굴 3층 보스 선공 + [2026-10-25] 몬스터 인식 중 전투 턴
    coldTick(now); coldWarn(); // [2026-10-26] 한기/눈바람
    scanTick(); // [2026-10-24] 탐지 곡괭이
    var ng = nearOther(); if (el.greet && (el.greet.style.display === "block") !== ng) el.greet.style.display = ng ? "block" : "none"; // [2026-10-24] 근처에 다른 유저가 있으면 👋
    if (P.moving && now - P.t0 >= stepMs()) {
      P.moving = false; P.x = P.tx; P.y = P.ty;
      showCoord(); drawMini();
      antFallCheck(); // [2026-10-28] 사막 서쪽 -- 개미굴
    }
    if (!P.moving && !busy && now - P.digT0 > 380) {
      var d = heldDir() || tapDir;
      if (d && el.menu && el.menu.classList.contains('open')) setMenu(false); // [2026-11-05] 움직이면 메뉴 창 닫힘
      tapDir = null;
      if (smithOpen) d = null; // 대장간 창이 열려 있는 동안은 움직이지 않는다
      if (d && stunnedMe()) { // [2026-11-04] 붕대 스턴
        if (now - lastBumpT > 1500 || lastBump !== 'stun') msg(stunMsg(), 1500);
        lastBump = 'stun'; lastBumpT = now;
        d = null;
      }
      if (d && frozenMe()) { // [2026-10-28] 얼음 여왕 빙결 -- 못 움직인다
        iceStruggle(now); // [2026-11-04] 몸부림 -- 20번이면 풀린다
        if (now - lastBumpT > 2500 || lastBump !== 'ice') msg('🧊 얼어붙었어요! ⬅️➡️ 움직이거나 ⚔️ 공격해서 얼음을 깨 보세요. 동료가 곁(2칸)에 와도 풀려요.');
        lastBump = 'ice'; lastBumpT = now;
        d = null;
      }
      if (!d && auto && !smithOpen && !fade && !helmetBlocked()) { // [2026-10-24] 자동채광
        if (st.durability < 1) setAuto(false, '⛏️ 곡괭이 내구도가 다 닳아 자동채광을 멈췄어요.');
        else if (minable(P.x, P.y)) { dig(); return; }
        else {
          d = autoStep();
          if (!d) setAuto(false, '🤖 근처(30칸)에 캘 곳이 없어 자동채광을 멈췄어요. 다른 곳으로 옮겨서 다시 켜 주세요.');
        }
      }
      if (d && helmetBlocked()) { // 광산 레벨에 비해 투구 단계가 낮으면 움직일 수 없다(마을로 버튼으로 나갈 수 있음)
        if (now - lastBumpT > 2500 || lastBump !== 'helmet') msg('⛑️ 광부 투구 ' + map.helmetNeed + '단계 이상이 있어야 움직일 수 있어요. (🏠 마을로 나갈 수 있어요)');
        lastBump = 'helmet'; lastBumpT = now;
        d = null;
      }
      if (d) {
        P.dir = d;
        var nx = P.x + (d === 'left' ? -1 : d === 'right' ? 1 : 0), ny = P.y + (d === 'up' ? -1 : d === 'down' ? 1 : 0);
        if (hermitOn() && nx === hermit.x && ny === hermit.y) {
          talk(hermitNpc(), now); // [2026-10-30] 은둔한 대장장이
        } else if (bossNpcOn() && nx === bossNpc.x && ny === bossNpc.y) {
          talk(bossNpcObj(), now); // [2026-11-13] 보스 소환사
        } else if (walk(nx, ny)) {
          P.fx = P.x; P.fy = P.y; P.tx = nx; P.ty = ny; P.t0 = now; P.moving = true; P.step = P.step === 1 ? 2 : 1;
        } else if (PORTALS.indexOf(cellAt(nx, ny)) >= 0) {
          enterPortal(nx, ny, now);
        } else if (cellAt(nx, ny) === 'N') {
          talk(npcAt[nx + ',' + ny], now);
        } else if (antGate(nx) && (now - lastBumpT > 2500 || lastBump !== 'antgate')) { // [2026-11-04] 방벽 문 경비병
          lastBump = 'antgate'; lastBumpT = now;
          msg('🛡️ 사막 경비병: 멈추시오! 서쪽은 모래가 꺼져 개미굴로 빠지는 곳이오. Lv.' + ANT_GATE_LV + '이 되면 다시 오시오.', 3000);
        }
      } else if (map.wind && !smithOpen && !fade && now - lastWindT >= WIND_MS) {
        // [2026-10-23] 모래바람 -- 가만히 있으면 바람 방향(좌/우)으로 한 칸씩 밀린다(막힌 칸이면 그대로). 방향은 시각으로 정해 모두 같다.
        lastWindT = now;
        var wd = windDir(), wx = P.x + wd;
        if (walk(wx, P.y)) { P.fx = P.x; P.fy = P.y; P.tx = wx; P.ty = P.y; P.t0 = now; P.moving = true; }
      }
    }
  }
  /** 바람 방향(1 = 오른쪽, -1 = 왼쪽) -- 7초마다 바뀌는 돌풍, 기기 시각 기준이라 모든 유저가 같은 방향을 본다. */
  function windDir() {
    var ph = Math.floor(Date.now() / 7000);
    return (hashXY(ph, 7) % 2) ? 1 : -1;
  }
  // [2026-10-30] 은둔한 대장장이 -- 위치 알림(presTick)으로 받는다. 맵 정보에 없고 지도에도 안 나온다.
  var hermit = null, hermitAdminSaid = '', hermitStep = 'talk', hermitList = null, hermitPick = null, hermitBusy = false;
  function hermitOn() { return !!(hermit && map && hermit.m === map.id && hermit.until > nowSrv()); }
  // [2026-11-13] 보스 소환사 -- 보스룸에 보스가 없을 때 보스 집 칸에 선다(위치 알림 bossCall). 소환하면 3초 카운트(at) 뒤 사라지고 보스가 나온다.
  var bossNpc = null, bossCallInfo = null, bossCallBusy = false, worldBossOpen = false;
  function bossNpcOn() { return !!(bossNpc && map && bossNpc.m === map.id && !(bossNpc.at && bossNpc.at <= nowSrv())); }
  function bossNpcObj() { return { x: bossNpc.x, y: bossNpc.y, kind: 'bosscall', name: '보스 소환사', line: '이곳의 주인을 불러내 드리지요. 한 번 부르면 두 시간 뒤에 다시 부를 수 있소.' }; }
  function callLeftTxt(ms) { var m = Math.ceil(ms / 60000); return m >= 60 ? Math.floor(m / 60) + '시간' + (m % 60 ? ' ' + m % 60 + '분' : '') : m + '분'; }
  function callHhmm(ms) { var d = new Date(ms - srvOff), p = function (v) { return (v < 10 ? '0' : '') + v; }; return (d.toDateString() === new Date().toDateString() ? '' : (d.getMonth() + 1) + '/' + d.getDate() + ' ') + p(d.getHours()) + ':' + p(d.getMinutes()); }
  function bossCallHtml(n) {
    var d = bossCallInfo, h = npcHead('🔮 ' + n.name, false) + talkBlock(n, n.line);
    if (!d) return h + '<div class="mine-smith-say">...</div>';
    if (!d.ok) return h + '<div class="mine-smith-say">' + escAttr(d.message || '...') + '</div>';
    var left = (d.readyAt || 0) - nowSrv(), can = !d.up && left <= 0;
    h += '<div class="mine-smith-say">👑 <b>' + escAttr(d.boss) + '</b> -- ' + (d.up ? '지금 나와 있어요!' : can ? '지금 불러낼 수 있어요.' : callHhmm(d.readyAt) + '부터 다시 부를 수 있어요 (' + callLeftTxt(left) + ' 뒤).')
        + '<br><small>한 번 부르면 ' + Math.round((d.cdMin || 120) / 60) + '시간 뒤에 다시 부를 수 있어요. 사람마다 따로예요.</small></div>';
    return h + '<div class="mine-smith-menu"><button type="button" ' + (can && !bossCallBusy ? '' : 'disabled ') + 'onclick="TowerMine.bossCall()">👑 보스 소환</button></div>';
  }
  function bossCallGo() {
    if (bossCallBusy) return;
    bossCallBusy = true;
    if (smithOpen && smithView === 'bosscall') renderSmith(smithNpc);
    api('/api/tower-mine-bosscall?action=call').then(function (d) {
      bossCallBusy = false;
      if (!d || d.tooFast) return;
      if (d.ok) {
        closeSmith();
        if (bossNpc && map && bossNpc.m === map.id) bossNpc.at = d.at;
        (st && st.bosses || []).forEach(function (b) { if (map && b.map === map.id) b.callAt = d.readyAt; });
        msg(d.message || '👑 보스를 불러냈어요!', 3000);
        return;
      }
      bossCallInfo = d;
      if (smithOpen && smithView === 'bosscall') renderSmith(smithNpc);
      if (d.message) msg(d.message, 2600);
    }).catch(function () { bossCallBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  /** 전체 지도 "👑 보스" -- 보스마다 지금 있는지 + 내가 다시 소환할 수 있는 시각(천둥 황소는 처치 수). */
  function worldBossHtml() {
    var L = (st && st.bosses) || [], tn = nowSrv();
    var rows = L.map(function (b) {
      var sub = b.need ? '⚔️ 마물 처치 ' + (b.kills | 0) + '/' + b.need + ' -- 다 차면 나타나요'
          : (b.callAt > tn ? '🔒 ' + callHhmm(b.callAt) + '부터 소환 가능 (' + callLeftTxt(b.callAt - tn) + ' 뒤)' : '✅ 지금 소환할 수 있어요');
      return '<div class="mine-smith-row" style="display:block"><b>' + escAttr(b.name) + '</b> <small>' + escAttr(b.mapName || '') + '</small> · '
          + (b.alive ? '<b style="color:#ff6a6a">👑 나와 있어요</b>' : '<span style="color:#a89ec8">💤 없음</span>') + '<br><small>' + sub + '</small></div>';
    }).join('');
    return '<div class="mine-smith-say" style="border:1px solid #ffd27a">👑 <b>보스</b> -- 보스룸 소환사에게 말을 걸어 불러내요. 소환은 보스마다, 사람마다 2시간에 한 번이에요.</div>'
        + (rows || '<div class="mine-smith-say">아직 갈 수 있는 보스룸이 없어요.</div>');
  }
  function hermitNpc() { return { x: hermit.x, y: hermit.y, kind: 'hermit', name: '???', line: '...' }; }
  function hermitHtml(n) {
    var h = npcHead('❔ ???', false) + talkBlock(n, '...');
    if (hermitStep === 'talk') return h + '<div class="mine-smith-menu list"><button type="button" onclick="TowerMine.hermitGo(\'list\')">보스를 모두 처치했다</button></div>';
    if (hermitStep === 'list') {
      if (!hermitList) return h + '<div class="mine-smith-say">...</div>';
      if (!hermitList.length) return h + '<div class="mine-smith-say">...무기가 없군.</div>';
      return h + '<div class="mine-smith-say">...무기를 보여라.</div>' + hermitList.map(function (w) {
        return '<div class="mine-smith-row"><span class="nm" style="color:' + GRADE_COL[w.grade] + '">🗡️ ' + w.name + '<small>' + w.gradeName + (w.maxName ? '' : ' · 더 올릴 수 없음') + '</small></span>'
          + (w.maxName ? '<button type="button" onclick="TowerMine.hermitGo(\'pick\',\'' + w.code + '\')">선택</button>' : '') + '</div>';
      }).join('');
    }
    var p = hermitPick;
    return h + '<div class="mine-smith-say">지금 하면 최대 <b style="color:' + GRADE_COL[p.maxGrade] + '">' + p.maxName + '</b> 등급이 되겠군. 하겠나..?</div>'
      + p.cost.map(function (c) { return '<div class="mine-smith-row"><span class="nm">' + c.name + '<small style="color:' + (c.have >= c.need ? '#7cf08a' : '#ff8a8a') + '">' + c.have + ' / ' + c.need + '개</small></span></div>'; }).join('')
      + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.hermitGo(\'do\')">확인</button><button type="button" onclick="TowerMine.hermitGo(\'talk\')">취소</button></div>';
  }
  function hermitGo(step, code) {
    if (hermitBusy) return;
    if (step === 'talk') { hermitStep = 'talk'; renderSmith(smithNpc); return; }
    if (step === 'pick') { (hermitList || []).forEach(function (w) { if (w.code === code) hermitPick = w; }); hermitStep = 'confirm'; renderSmith(smithNpc); return; }
    hermitBusy = true;
    var q = step === 'do' ? 'action=do&code=' + encodeURIComponent(hermitPick.code) : 'action=list';
    if (step === 'list') { hermitStep = 'list'; hermitList = null; renderSmith(smithNpc); }
    api('/api/tower-mine-hermit?' + q).then(function (d) {
      hermitBusy = false;
      if (!d) return;
      if (step === 'list') {
        if (!d.ok) { closeSmith(); msg(d.message || '...', 2500); return; }
        hermitList = d.weapons; renderSmith(smithNpc); return;
      }
      if (d.ores) st.ores = d.ores;
      if (d.ok) { applyGear(d); flashT0 = performance.now(); hermitStep = 'talk'; closeSmith(); }
      msg(d.message || '...', 4200);
    }).catch(function () { hermitBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  /** NPC에게 말 걸기 -- 머리 위 말풍선 + 대장장이면 강화 창. */
  function talk(n, now) {
    if (!n) return;
    var key = 'npc' + n.x + ',' + n.y;
    if (lastBump === key && now - lastBumpT < 1200) return;
    lastBump = key; lastBumpT = now;
    if (n.kind === 'blackmarket' && !blackOpen()) { msg('🕶️ 천만 덮인 빈 노점이에요. 암상인은 매시 정각부터 15분까지만 나타나요.', 2800); return; } // [2026-10-26]
    var line = n.line, dg = st && st.dragon || {};
    if (n.kind === 'captain' && dg.has) line = '물 위를 걷는 기분은 어떤가?';
    else if (n.kind === 'captain' && dg.quest >= 1) line = '재료는 다 모았나?';
    talkBubble = { x: n.x, y: n.y, text: n.name + ': ' + line, t0: now };
    if (n.kind === 'hermit') { setAuto(false); hermitStep = 'talk'; openSmith(n, 'hermit'); return; } // [2026-10-30]
    if (n.kind === 'bosscall') { // [2026-11-13] 보스 소환사
      setAuto(false); bossCallInfo = null; openSmith(n, 'bosscall');
      api('/api/tower-mine-bosscall?action=info').then(function (d) { bossCallInfo = d; if (smithOpen && smithView === 'bosscall') renderSmith(smithNpc); }).catch(function () {});
      return;
    }
    if (n.kind === 'smith') openSmith(n, 'menu');
    else if (n.kind === 'captain') openSmith(n, 'captain'); // [2026-10-24] 선장 퀘스트 창
    else if (n.kind === 'boutique') openSmith(n, 'boutique'); // [2026-10-24] 명품상점
    else if (n.kind === 'priest') { priestConfirm = false; openSmith(n, 'priest'); } // [2026-10-24] 왕도 성당 신부 ([2026-10-28] 환생 확인 창은 닫힌 채로)
    else if (n.kind === 'elementalist') openSmith(n, 'elem'); // [2026-10-29]
    else if (n.kind === 'swordmaster') { setAuto(false); openSmith(n, 'aura'); } // [2026-11-04] 검기 사범
    else if (n.kind === 'whirlmaster') { setAuto(false); openSmith(n, 'whirl'); } // [2026-11-07] 돌개 사범
    else if (n.kind === 'ultmaster') { setAuto(false); openSmith(n, 'ult'); } // [2026-11-07] 필살 사범
    else if (n.kind === 'guide') { setAuto(false); openSmith(n, 'guide'); } // [2026-10-25] 이동 안내원
    else if (n.kind === 'blackmarket' || n.kind === 'furrier') { setAuto(false); openCoat(n); } // [2026-10-26] 방한 외투
    else if (n.kind === 'daykeeper') { setAuto(false); openSmith(n, 'daygate'); } // [2026-10-28] 요일 동굴 문지기
    else if (n.kind === 'ninjaquest' || n.kind === 'boatwright' || n.kind === 'ferry') { setAuto(false); openSmith(n, 'archi'); } // [2026-10-28] 다도해
    else if (n.kind === 'chest' || n.kind === 'antchest') openChest(n); // [2026-10-28] 피라미드 보상 상자, 개미굴 보물 상자
    else if (n.kind === 'smelter' || n.kind === 'gemcutter' || n.kind === 'artisan') { setAuto(false); openSmith(n, n.kind); } // [2026-10-28] 광석 전문가 / 보석 세공사 / 왕도 장인
    else if (n.kind === 'citymerchant') { setAuto(false); openSmith(n, 'citymerchant'); api('/api/tower-mine-cityshop?action=list').then(function (d) { cityShop = d; if (d && d.luckUntil != null) st.luckUntil = d.luckUntil; if (smithOpen && smithView === 'citymerchant') renderSmith(smithNpc); }).catch(function () {}); } // [2026-11-05] 왕도 상인
    else if (n.kind === 'enhancer') { setAuto(false); openSmith(n, 'enh'); } // [2026-11-06] 무기 강화사
    else if (n.kind === 'mercenary') { setAuto(false); openSmith(n, 'merc'); api('/api/tower-mine-merc?action=list').then(function (d) { mercShop = d; if (d && d.list) st.mercs = d.list; if (d && d.merc) st.merc = d.merc; if (smithOpen && smithView === 'merc') renderSmith(smithNpc); }).catch(function () {}); } // [2026-11-05] 왕도 용병소
    else if (n.kind === 'archishop') { setAuto(false); openSmith(n, 'archishop'); api('/api/tower-mine-archishop?action=list').then(function (d) { applyGear(d); if (smithOpen && smithView === 'archishop') renderSmith(smithNpc); }).catch(function () {}); } // [2026-10-28] 다도해 상인
  }
  // ---------------------------------------------------------------- 대장간(곡괭이 강화: 최대/현재 내구도 함께 증가)
  function oreHave(g) {
    var q = 0;
    (st.ores || []).forEach(function (o) { if (o.grade === g) q = o.qty; });
    return q;
  }
  // [2026-10-24] NPC 창 -- 대장장이: 메뉴 -> [구매](투구/장신구/장화) / [강화](곡괭이) / [합성](칠요석), 선장: 용왕의 반지 퀘스트.
  //  smithView = 'menu' | 'buy' | 'upgrade' | 'synth' | 'captain'
  var smithView = 'menu', captainSay = '';
  var GEAR_ICON = { ring: '💍', necklace: '📿', boots: '👢', tool: '⛏️', gloves: '🧤' };
  function npcHead(t, back) {
    return '<div class="mine-smith-head">' + t + ' <span>' + (back ? '<button type="button" onclick="TowerMine.smithGo(\'menu\')">뒤로</button> ' : '')
        + '<button type="button" class="mine-close" onclick="TowerMine.closeSmith()">✖ 닫기</button></span></div>';
  }
  /** 재료 목록(아이콘 + 이름 보유/필요, 모자라면 붉게). */
  function costHtml(costs) {
    return '<span class="mine-cost">' + (costs || []).map(function (c) {
      return '<span class="' + (c.have >= c.need ? 'ok' : 'no') + '"><img src="' + oreIcon(c.grade) + '" alt="">' + c.name + ' ' + c.have + '/' + c.need + '</span>';
    }).join('') + '</span>';
  }
  // [2026-10-28] "마을 장비 상점(대장장이)도 캐러셀 형태로(부위별)" -- 반지/목걸이/장화/곡괭이 줄마다 가로로 넘기는 카드
  var GEAR_PARTS = [['ring', '💍 반지', '하나만 착용 가능!'], ['necklace', '📿 목걸이', '하나만 착용 가능!'], ['boots', '👢 장화', '하나만 착용 가능!'], ['tool', '⛏️ 곡괭이', '하나만 착용 가능!'], ['gloves', '🧤 장갑', '왕도 대장장이 · 하나만 착용 가능!']]; // [2026-10-28] 재생/광전사의 장갑
  function gearCard(g) {
    var worn = g.kind === 'ring' || g.kind === 'gloves' || g.kind === 'necklace' ? g.worn && g.tier > 0 : g.kind === 'tool' ? !!g.on : g.tier > 0;
    var h = '<div class="cos-card gear-card' + (worn ? ' worn' : '') + '"><div class="gear-ico">' + (GEAR_ICON[g.kind] || '💍') + '</div>'
        + '<div class="cos-nm">' + g.name + '</div><small class="gear-tier">' + (g.tier > 0 ? g.tier + '단계' + (g.maxTier ? ' / ' + g.maxTier : '') : '없음') + '</small>';
    if (g.effect) h += '<small class="gear-eff">지금: ' + g.effect + '</small>';
    if (g.dim) h += '<small class="gear-eff" style="color:#9aa">🌑 빛을 잃음(' + (g.dimLeft ? g.dimLeft + ' 뒤' : '12시간 뒤') + ' 회복)</small>'; // [2026-10-29] 맵 리셋 -> 12시간
    if ((g.kind === 'ring' || g.kind === 'tool' || g.kind === 'gloves' || g.kind === 'necklace') && g.tier > 0)
      h += worn ? '<span class="mine-worn">' + (g.kind === 'tool' ? '사용 중' : '착용 중') + '</span>' + (g.kind === 'tool' || g.kind === 'gloves' || g.kind === 'necklace' ? '<button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + g.code + '\')">해제</button>' : '')
          : '<button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + g.code + '\')">장착</button>';
    if (!g.nextTier) return h + '<small class="gear-eff">최고 단계예요.</small></div>';
    var away = g.cityOnly && map.id !== 'city'; // [2026-10-25] 높은 단계는 왕도 대장장이만(곡괭이는 어디서나)
    h += '<small class="gear-next">' + (g.cityOnly ? '🏰 ' : '') + g.nextTier + '단계: ' + g.nextEffect + '</small>' + costHtml(g.costs);
    return h + (away ? '<span class="mine-worn" style="color:#ffd27a">왕도 대장장이</span>'
        : '<button type="button" ' + (g.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.craft(\'' + g.code + '\')">' + (g.tier > 0 ? '올리기' : '만들기') + '</button>') + '</div>';
  }
  function gearHtml() {
    var h = '';
    GEAR_PARTS.forEach(function (p) {
      // [2026-11-04] "이미 산 상품은 안 나오게" -- 최고 단계까지 산 것은 빼고, 더 올릴 단계가 있는 것만(장착/해제는 인벤토리에서)
      var list = (st.gear || []).filter(function (g) { return g.kind === p[0] && g.nextTier; });
      if (!list.length) return;
      h += '<div class="cos-slot"><div class="cos-slot-h">' + p[1] + ' <small>' + p[2] + '</small></div><div class="cos-car" data-slot="g-' + p[0] + '">'
          + list.map(gearCard).join('') + '</div></div>';
    });
    return h || '<div class="mine-eq-empty">여기서 살 수 있는 장비는 모두 최고 단계까지 갖췄어요. 장착/해제는 인벤토리에서 할 수 있어요.</div>';
  }
  function synthHtml() {
    var stones = [];
    var dayNeed = st.sevenDayNeed || 30; // [2026-10-28] 요일석 종류마다 30개(서버 MINE_SEVEN_DAY_STONES)
    for (var g = 11; g <= 17; g++) stones.push({ grade: g, name: ORE_NAME[g], have: oreHave(g), need: dayNeed });
    stones.push({ grade: 6, name: ORE_NAME[6], have: oreHave(6), need: 1 }); // [2026-10-25] 칠요석 하나에 별빛석 1개
    var can = (st.sevenMax || 0) > 0 ? '' : 'data-lack="1" ';
    return '<div class="mine-smith-say">요일석 7종(월요석~일요석)을 ' + dayNeed + '개씩, 거기에 별빛석 하나를 더 가져오면 별빛으로 묶어 칠요석 하나로 합쳐 주지.</div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(18) + '" alt=""><span class="nm">칠요석<small>보유 ' + oreHave(18) + '개 · 지금 ' + (st.sevenMax || 0) + '개 만들 수 있어요</small>'
        + costHtml(stones) + '</span>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(1)">1개</button>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(9999)">최대</button></div>'
        + galaxyHtml()
        + copperHtml();
  }
  // [2026-10-28] "구리 광석 사용처를 늘려줘" -- 구리 정련(구리 20 -> 철광석 1), 오늘의 구리 납품(하루 1번 구리 100 -> 오늘 요일석 3 + 곡괭이 내구도 +50)
  function copperHtml() {
    var cu = oreHave(1), canR = cu >= 20 ? '' : 'data-lack="1" ', canD = cu >= 100 && !st.deliverDone ? '' : 'data-lack="1" ';
    return '<div class="mine-smith-sub"><b>🔥 구리 정련 · 📦 오늘의 납품</b></div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(1) + '" alt=""><span class="nm">구리 광석 20개 → 철광석 1개<small>보유 구리 ' + cu + '개</small></span>'
        + '<button type="button" ' + canR + 'onclick="TowerMine.refine(1)">1번</button>'
        + '<button type="button" ' + canR + 'onclick="TowerMine.refine(9999)">최대</button></div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(11 + ((new Date(nowSrv() + 9 * 3600000).getUTCDay() + 6) % 7)) + '" alt=""><span class="nm">구리 100개 납품 (하루 1번)'
        + '<small>오늘 요일석 3개 + 곡괭이 내구도 +50' + (st.deliverDone ? ' · 오늘은 납품 완료' : '') + '</small></span>'
        + '<button type="button" ' + canD + 'onclick="TowerMine.deliver()">납품</button></div>';
  }
  function refine(times) { gearAct('/api/tower-mine-refine?times=' + times); }
  function deliver() { gearAct('/api/tower-mine-deliver'); }
  function captainHtml(n) {
    var d = st.dragon || {}, h = npcHead('⚓ ' + (n ? n.name : '늙은 선장'), false);
    if (d.has) return h + talkBlock(n, captainSay || '허허, 물 위를 걷는 기분은 어떤가? 🌊');
    if (d.quest >= 1) {
      return h + talkBlock(n, captainSay || d.story || '')
          + '<div class="mine-smith-row"><span class="nm">🌊 용왕의 반지<small>를 착용하면 물 위를 걸을 수 있어요</small>' + costHtml(d.costs) + '</span>'
          + '<button type="button" ' + (d.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.captain(\'craft\')">만들어 주세요</button></div>';
    }
    return h + talkBlock(n, captainSay || (n ? n.line : ''))
        + '<div class="mine-smith-row"><img src="' + oreIcon(4) + '" alt=""><span class="nm">금광석 ' + d.goldNeed + '개 주기<small>보유 ' + (d.gold || 0) + '개</small></span>'
        + '<button type="button" ' + ((d.gold || 0) >= d.goldNeed ? '' : 'data-lack="1" ') + 'onclick="TowerMine.captain(\'give\')">주기</button></div>';
  }
  // [2026-10-24] 배 -- 다른 항구를 고르면 그곳 부두로
  function shipHtml() {
    return npcHead('⛵ 배 타기', false) + '<div class="mine-smith-say">어디로 갈까요? (무료)</div><div class="mine-smith-menu">'
        + (map.shipTo || []).filter(function (s) { return !s.admin || (st && st.adminMaps); }) 
          .map(function (s) { return '<button type="button" onclick="TowerMine.sail(\'' + s.id + '\')">⛵ ' + s.name + (s.admin ? ' 🏯' : '') + '</button>'; }).join('') + '</div>';
  }
  function sail(dest) {
    var n = smithNpc;
    closeSmith();
    if (!n || busy) return;
    travel('/api/tower-mine-warp?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&px=' + n.px + '&py=' + n.py + '&dest=' + encodeURIComponent(dest) + verQ(), '배를 타지 못했어요.');
  }
  // [2026-10-24] 명품상점 -- 부위별 외형(능력치 없음) + 성별 전환
  var LOOK_SLOT_NAME = { hat: '🎩 모자', hair: '💇 머리', top: '👘 상의', bottom: '👖 하의', shoes: '👞 신발', wing: '🪽 날개 염색' }; // [2026-10-29] 날개 염색
  // [2026-10-24] "치장 아이템을 착용했을 때 모습을 이미지로" -- 목록마다 그 아이템을 입은 내 캐릭터, 누르면 위에 크게 입어보기(앞/옆/뒤)
  var lookImgCache = {}, tryLook = null, tryName = '';
  function lookImg(lk, dir) {
    var key = JSON.stringify(lk) + dir;
    if (!lookImgCache[key]) lookImgCache[key] = (lk && lk.rebirth > 0 ? MineChar.withWings(dir, 0, 0, lk) : charSprite(dir, 0, 0, lk)).toDataURL(); // [2026-10-29] 환생했으면 날개까지
    return lookImgCache[key];
  }
  function lookWith(slot, look) {
    var lk = {}, cur = st.look || {};
    for (var k in cur) lk[k] = cur[k];
    lk[slot] = look;
    if (slot === 'wing') lk.rebirth = Math.max(2, lk.rebirth | 0); // [2026-10-29] 날개 염색 미리보기 -- 환생 전이어도 날개를 보여 준다
    return lk;
  }
  function tryOn(code) {
    var lk = st.look || {};
    if (code === 'gender') { tryLook = lookWith('gender', lk.gender === 1 ? 0 : 1); tryName = lk.gender === 1 ? '남성' : '여성'; }
    else if (code === 'me') { tryLook = null; tryName = ''; }
    else (st.cosShop || []).forEach(function (c) { if (c.code === code) { tryLook = lookWith(c.slot, c.look); tryName = c.name; } });
    renderSmith(smithNpc);
  }
  function boutiqueHtml(n) {
    var lk = st.look || {}, show = tryLook || lk;
    var h = npcHead('👗 명품상점', false) + talkBlock(n, n ? n.line : '')
        + '<div class="mine-look-top"><div class="mine-look-prevs">'
        + ['down', 'right', 'up'].map(function (d) { return '<img class="mine-look-prev" src="' + lookImg(show, d) + '" alt="">'; }).join('') + '</div>'
        + '<div class="mine-smith-say">' + (tryLook ? '👀 입어보기: <b>' + tryName + '</b> <button type="button" class="mine-mini-btn" onclick="TowerMine.tryOn(\'me\')">내 모습</button>' : '지금 내 모습')
        + '<br><small>아이템 그림을 누르면 입어 볼 수 있어요.</small></div></div>'
        + '<div class="mine-smith-row"><img class="mine-look-mini" src="' + lookImg(lookWith('gender', lk.gender === 1 ? 0 : 1), 'down') + '" alt="" onclick="TowerMine.tryOn(\'gender\')">'
        + '<span class="nm">⚧ 성별: ' + (lk.gender === 1 ? '여성' : '남성') + ' → ' + (lk.gender === 1 ? '남성' : '여성') + costHtml(st.genderCosts) + '</span>'
        + '<button type="button" ' + (st.genderCan ? '' : 'disabled ') + 'onclick="TowerMine.lookAct(\'gender\')">바꾸기</button></div>';
    // [2026-10-28] "치장상점 UI -- 부위별 캐러셀" -- 부위마다 가로로 넘기는 카드 줄(그림을 누르면 입어 보기). 다시 그려도 넘긴 자리는 그대로(renderSmithView).
    ['hat', 'hair', 'top', 'bottom', 'shoes', 'wing'].forEach(function (slot) {
      var cur = lk[slot + 'Code'] || '', list = (st.cosShop || []).filter(function (c) { return c.slot === slot; });
      var owned = list.filter(function (c) { return c.owned; }).length;
      h += '<div class="cos-slot"><div class="cos-slot-h">' + LOOK_SLOT_NAME[slot] + ' <small>' + owned + '/' + list.length + ' 보유</small>'
          + (cur ? ' <button type="button" class="mine-mini-btn" onclick="TowerMine.lookAct(\'wear\', \'\', \'' + slot + '\')">기본으로</button>' : ' <span class="mine-worn">기본 착용 중</span>') + '</div>'
          + '<div class="cos-car" data-slot="' + slot + '">';
      list.forEach(function (c) {
        var btn;
        if (c.code === cur) btn = '<span class="mine-worn">착용 중</span>';
        else if (c.owned) btn = '<button type="button" onclick="TowerMine.lookAct(\'wear\', \'' + c.code + '\')">입기</button>';
        else btn = '<button type="button" ' + (c.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.lookAct(\'buy\', \'' + c.code + '\')">구매</button>';
        h += '<div class="cos-card' + (c.code === cur ? ' worn' : '') + (c.note ? ' gm' : '') + '">'
            + '<img src="' + lookImg(lookWith(c.slot, c.look), 'down') + '" alt="' + c.name + ' 입은 모습" onclick="TowerMine.tryOn(\'' + c.code + '\')">'
            + '<div class="cos-nm">' + c.name + '</div>'
            + (c.note ? '<small class="cos-note">✨ ' + c.note + '</small>' : '') // [2026-10-28] 운영자 전용 설명
            + (c.owned ? '<small class="cos-own">가지고 있어요</small>' : costHtml(c.costs)) + btn + '</div>';
      });
      h += '</div></div>';
    });
    return h;
  }
  function lookAct(action, code, slot) {
    tryLook = null; // 사거나 입으면 입어보기 대신 실제 모습
    gearAct('/api/tower-mine-look?action=' + action + '&code=' + encodeURIComponent(code || '') + '&slot=' + encodeURIComponent(slot || ''));
  }
  // [2026-10-24] 관리자 전용 좌표 이동 -- 왼쪽 위 좌표를 누르면 맵/X/Y 입력, 확인(또는 Enter)으로 이동
  function warpHtml() {
    return npcHead('🛠️ 관리자 좌표 이동', false)
        + '<div class="mine-warp"><label>맵 <select id="mineWarpMap">' + (st.adminMaps || []).map(function (m) {
          return '<option value="' + m.id + '"' + (m.id === map.id ? ' selected' : '') + '>' + m.name + ' (' + m.w + 'x' + m.h + ')</option>';
        }).join('') + '</select></label>'
        + '<label>X <input id="mineWarpX" type="number" inputmode="numeric" value="' + P.x + '"></label>'
        + '<label>Y <input id="mineWarpY" type="number" inputmode="numeric" value="' + P.y + '"></label>'
        + '<button type="button" onclick="TowerMine.adminWarp()">확인</button></div>';
  }
  // [2026-10-28] "좌표를 누르면 맵/X/Y 입력 대신 전체 지도에서 지역을 눌러 이동" -- 좌표를 누르면 전체 지도(관리자는 지역을 눌러 이동),
  //  좌표 입력 창은 전체 지도 아래 [좌표 입력] 버튼으로(openWarpForm)
  function openWarp() {
    if (!opened || !map || busy || !st || !st.adminMaps) return;
    openWorld();
  }
  function openWarpForm() {
    if (!opened || !map || busy || !st || !st.adminMaps) return;
    openSmith({ name: '좌표 이동', line: '', kind: 'warp' }, 'warp');
    var ix = $('mineWarpX');
    if (ix) { ix.focus(); ix.select(); }
  }
  function adminWarp() {
    var mid = $('mineWarpMap').value, x = parseInt($('mineWarpX').value, 10), y = parseInt($('mineWarpY').value, 10);
    if (isNaN(x) || isNaN(y)) { msg('좌표를 숫자로 입력해 주세요.'); return; }
    closeSmith();
    setAuto(false);
    travel('/api/tower-mine-admin-warp?mapId=' + encodeURIComponent(mid) + '&x=' + x + '&y=' + y, '이동하지 못했어요.');
  }
  // [2026-10-24] 전체 지도 -- 지역이 어떻게 이어졌는지(걸어서 / 배 / 잠김)와 지금 있는 곳. 구조는 S5MineMap.buildWorld와 같다.
  // [2026-10-24] 사막 북쪽 모래폭풍 너머 초원 -> 왕도 아스테리아 추가(설원은 오른쪽 위로, 배로만)
  // [2026-10-26] 설원 위로 설산 1~3층과 설산 마을(지도를 위로 늘렸다)
  var WORLD_NODES = [
    { id: 'snowpeak', name: '설산 봉우리 🧊', x: 262, y: -264, fill: '#9fd8f0', ink: '#0a2a3a' }, // [2026-10-28] 설산 마을 북문 -> 봉우리 가는 길 -> 봉우리(얼음 여왕)
    { id: 'snowroad', name: '봉우리 가는 길', x: 262, y: -194, fill: '#d0e4f4', ink: '#1a2a3a' },
    { id: 'snowtown', name: '설산 마을', x: 262, y: -124, fill: '#d9c9a0', ink: '#2a1d10', town: true },
    { id: 'snowcave', name: '설산 동굴 💠', x: 262, y: -124, fill: '#4e6a84', ink: '#e8f8ff' }, // [2026-10-28] 설산 마을 동문
    { id: 'snowcave2', name: '설산 동굴 2층', x: 262, y: -124, fill: '#2e4a64', ink: '#e8f8ff' }, // [2026-10-28] // [2026-10-28] 마을은 전체 지도에 레벨 표시 안 함
    { id: 'snow3', name: '설산 3층', x: 262, y: -68, fill: '#c8d8ea', ink: '#1a2a3a' },
    { id: 'snow2', name: '설산 2층', x: 262, y: -12, fill: '#d6e2f0', ink: '#1a2a3a' },
    { id: 'snow1', name: '설산 1층', x: 262, y: 44, fill: '#e2ebf5', ink: '#1a2a3a' },
    { id: 'ruins', name: '성당 옛터 👿', x: 160, y: -110, fill: '#5a4a6a', ink: '#f0e0ff' }, // [2026-10-28] 왕도 가는 길 북쪽 샛길
    { id: 'royalroad', name: '왕도 북쪽 샛길', x: 160, y: -40, fill: '#b8a27a', ink: '#2a1d10' }, // [2026-10-27] 설산 마을 서문 -> 왕도 동문
    { id: 'city', name: '왕도 아스테리아', x: 160, y: 28, fill: '#c8a46a', ink: '#2a1d10', town: true }, // [2026-10-27] 일반 플레이어에게 열림, [2026-10-28] 레벨 표시 없음
    { id: 'steppe', name: '왕도 남쪽 초원', x: 160, y: 100, fill: '#8fbf5a', ink: '#10300a' },
    { id: 'cave3', name: '광산 동굴 3층', x: 52, y: 52, fill: '#2a2230', ink: '#e8d8ff' },
    { id: 'cave4', name: '광산 동굴 4층', x: 52, y: 52, fill: '#1a1424', ink: '#e8d8ff' }, // [2026-10-28] 3층 보스가 지키는 문 너머
    { id: 'north', name: '북쪽 끝 설원', x: 262, y: 100, fill: '#eef3f8', ink: '#2a3440' },
    { id: 'desert', name: '북쪽 사막', x: 160, y: 172, fill: '#e3c47a', ink: '#3b2a10' },
    { id: 'forestmaze', name: '마물의 숲 미궁 🌲', x: -42, y: -40, fill: '#2e5a2e', ink: '#e8ffe0', w: 76 }, // [2026-11-11] 두고개식 미궁(방 15개 + 끝방은 한 칸으로)
    { id: 'harpynest', name: '하피의 둥지 🪶', x: -42, y: -110, fill: '#3a7a68', ink: '#f0fff8', w: 76 }, // [2026-11-11] 하피 여왕
    { id: 'harpyferry', name: '마물의 숲 나루터', x: -150, y: -110, fill: '#3a6a8a', ink: '#f0f8ff', w: 76 }, // [2026-11-11] 둥지 뒤 강가
    { id: 'westroad', name: '마물의 숲 입구 🦣', x: -42, y: 28, fill: '#9a8a5a', ink: '#1a1408', w: 76 }, // [2026-11-05] 왕도 서문 너머(매머드 무리)
    { id: 'antnest', name: '개미굴 🐜', x: -42, y: 172, fill: '#4a2a1a', ink: '#ffd8b0', w: 76 }, // [2026-10-28] 사막 서쪽에서 빠짐
    { id: 'pyramid', name: '피라미드 📦', x: -42, y: 242, fill: '#d8b060', ink: '#3b2a10', w: 76 }, // 개미굴을 빠져나오면
    { id: 'cave2', name: '광산 동굴 2층', x: 52, y: 122, fill: '#4a3d33', ink: '#fff' },
    { id: 'cave1', name: '광산 동굴 1층', x: 52, y: 192, fill: '#6b5b4b', ink: '#fff' },
    { id: 'dungeon', name: '요일 던전', x: 52, y: 252, fill: '#5b4d40', ink: '#fff' },
    { id: 'field', name: '중앙 평원 🏠', x: 160, y: 252, fill: '#5fae45', ink: '#10300a' },
    { id: 'castle', name: '동쪽 성곽', x: 258, y: 252, fill: '#8a857c', ink: '#fff' },
    { id: 'beach', name: '남쪽 바닷가', x: 160, y: 344, fill: '#ecd9a0', ink: '#3b2a10' },
    { id: 'island', name: '남쪽 섬', x: 60, y: 422, fill: '#3fa36b', ink: '#fff' },
    { id: 'archi', name: '다도해 🏯', x: 252, y: 422, fill: '#2f8a8a', ink: '#fff' }, 
    { id: 'shrine', name: '신궁 1층', x: 52, y: 490, fill: '#8a1e1e', ink: '#fff', sub: true },
    { id: 'shrine2', name: '신궁 2층', x: 160, y: 490, fill: '#7a1a1a', ink: '#fff', sub: true },
    { id: 'shrine3', name: '신궁 3층', x: 268, y: 490, fill: '#6a1414', ink: '#fff', sub: true },
    { id: 'shrineboss', name: '닌자 두목 🥷', x: 106, y: 550, fill: '#3a0f1a', ink: '#ffd8e0', sub: true },
    { id: 'archisea', name: '다도해 뱃길', x: 214, y: 550, fill: '#2f6a9a', ink: '#fff', sub: true },
    { id: 'isle1', name: '동백섬', x: 44, y: 625, fill: '#3f8a4a', ink: '#fff', sub: true, w: 72 },
    { id: 'isle2', name: '갈매기섬', x: 120, y: 625, fill: '#7a8a8a', ink: '#fff', sub: true, w: 72 },
    { id: 'isle3', name: '거북섬', x: 196, y: 625, fill: '#c8b070', ink: '#2a1d10', sub: true, w: 72 },
    { id: 'isle4', name: '안개섬', x: 272, y: 625, fill: '#5a6a6a', ink: '#fff', sub: true, w: 72 },
    { id: 'isle5', name: '산호섬', x: 82, y: 675, fill: '#e07a8a', ink: '#2a1010', sub: true, w: 72 },
    { id: 'isle6', name: '달맞이섬', x: 160, y: 675, fill: '#8a9ac8', ink: '#fff', sub: true, w: 72 },
    { id: 'isle7', name: '용암섬', x: 238, y: 675, fill: '#a83a1a', ink: '#fff', sub: true, w: 72 }
  ];
  // [2026-10-28] "전체 지도 보기가 어렵다, 지역 구간별로 나눠 놓으면" -- 네 구간(탭)으로 나눠 그린다. 처음엔 지금 있는 구간.
  //  구간마다 칸 자리(x, y)를 따로 두고(이름/색/폭은 WORLD_NODES), 다른 구간으로 이어지는 곳은 점선 칸(@ -- 누르면 그 구간 탭으로).
  var WORLD_REGIONS = [
    { id: 'plain', name: '🌾 평원', vh: 390,
      nodes: [['@desert', 160, 30, 'desert', '🏜️ 사막·왕도 ▲'], ['cave4', -58, 40], ['cave3', -58, 100], ['cave2', -58, 160], ['cave1', 52, 160], ['field', 160, 180], ['castle', 268, 180],
        ['dungeon', 52, 240], ['beach', 160, 270], ['@sea', 92, 350, 'sea', '🏝️ 남쪽 바다 ⛵'], ['@snow', 240, 350, 'snow', '❄️ 설원 ⛵']],
      links: [['field', '@desert'], ['field', 'cave1'], ['cave1', 'cave2'], ['cave2', 'cave3'], ['cave3', 'cave4'], ['field', 'dungeon'], ['field', 'castle'], ['field', 'beach'], ['beach', '@sea', 'sea'], ['beach', '@snow', 'sea']] },
    { id: 'desert', name: '🏜️ 사막·왕도', vh: 410, tx: 226,
      nodes: [['ruins', 160, 30], ['royalroad', 160, 100], ['@snowtown', 268, 100, 'snow', '❄️ 설산 마을 ▶'], ['city', 160, 170], ['westroad', 44, 170], ['forestmaze', 44, 100], ['harpynest', 44, 30], ['harpyferry', -64, 30], ['steppe', 160, 240], ['desert', 160, 310],
        ['antnest', 44, 310], ['pyramid', 44, 380], ['@plain', 172, 380, 'plain', '🌾 평원 ▼']],
      links: [['royalroad', 'ruins'], ['royalroad', '@snowtown'], ['royalroad', 'city'], ['city', 'westroad'], ['westroad', 'forestmaze'], ['forestmaze', 'harpynest'], ['harpynest', 'harpyferry'], ['city', 'steppe'], ['steppe', 'desert'], ['desert', 'antnest', 'fall', '🕳️'], ['antnest', 'pyramid'], ['desert', '@plain']] },
    { id: 'snow', name: '❄️ 설원·설산', vh: 420,
      nodes: [['snowpeak', 160, 30], ['snowroad', 160, 90], ['snowtown', 160, 150], ['@royal', 46, 150, 'desert', '🏰 왕도 ◀'], ['snowcave', 272, 150], ['snowcave2', 272, 230], ['snow3', 160, 210], ['snow2', 160, 270], ['snow1', 160, 330],
        ['north', 160, 390], ['@beach2', 276, 390, 'plain', '⛵ 바닷가']],
      links: [['snowroad', 'snowpeak'], ['snowtown', 'snowroad'], ['snowtown', '@royal'], ['snowtown', 'snowcave'], ['snowcave', 'snowcave2'], ['snow3', 'snowtown'], ['snow2', 'snow3'], ['snow1', 'snow2'], ['north', 'snow1'], ['north', '@beach2', 'sea']] },
    // [2026-10-30] "다도해 뱃길에서 일곱 섬 가는 길도 실제 뱃길처럼" -- 실제 맵처럼 본섬에서 북쪽으로 물길, 왼쪽/오른쪽 번갈아 부두(동백 > 갈매기 > ... > 용암)
    { id: 'sea', name: '🏝️ 남쪽 바다', vh: 760, channel: { x: 250, top: 128, isles: ['isle1', 'isle2', 'isle3', 'isle4', 'isle5', 'isle6', 'isle7'] },
      nodes: [['@beach', 60, 28, 'plain', '⛵ 남쪽 바닷가'], ['island', 60, 560], ['archi', 250, 560], ['shrine', 140, 630], ['shrine2', 250, 630], ['shrine3', 360, 630],
        ['shrineboss', 250, 700], ['archisea', 250, 500], ['isle1', 170, 440], ['isle2', 330, 388], ['isle3', 170, 336], ['isle4', 330, 284],
        ['isle5', 170, 232], ['isle6', 330, 180], ['isle7', 170, 128]],
      links: [['@beach', 'island', 'sea'], ['island', 'archi', 'sea', '⛵'], ['archi', 'shrine'], ['shrine', 'shrine2'], ['shrine2', 'shrine3'], ['archi', 'shrineboss'], ['archi', 'archisea', 'sea', '⛵']] }
  ];
  var worldTab = null;
  function regionOf(id) {
    for (var i = 0; i < WORLD_REGIONS.length; i++) if (WORLD_REGIONS[i].nodes.some(function (n) { return n[0] === id; })) return WORLD_REGIONS[i].id;
    return map && map.archi ? 'sea' : 'plain';
  }
  function worldTabGo(t) { worldTab = t; if (smithOpen) renderSmith(smithNpc); }
  // [2026-10-28] "전체 지도 보기 불편해, 확대 또는 드래그가 가능한 전체 맵" -- 네 구간 배치를 한 장에 이어 붙이고(WORLD_PLACE), 끌어서 이동 / 휠·두 손가락·+− 로 확대.
  //  구간 사이 길(사막-평원, 샛길-설산 마을, 바닷가-남쪽 섬/설원)은 구간 밖 점선 칸 대신 실제 칸끼리 잇는다. 처음엔 지금 있는 곳을 가운데로.
  var WORLD_PLACE = { desert: [0, 0], snow: [360, 0], plain: [110, 440], sea: [290, 800] } /* [2026-11-11] 남쪽 바다를 오른쪽으로 270 -- 바닷가 -> 남쪽 섬 뱃길이 용암섬/산호섬을 가로지르지 않게 */ /* [2026-11-08] 평원을 오른쪽으로 110 -- 광산 동굴을 왼쪽으로 펼쳐 피라미드 보스 글자와 안 겹치게 */, WORLD_SIZE = [960, 1560]; // [2026-10-30] 남쪽 바다는 동쪽 성곽 옆이 아니라 평원 한참 아래로
  var WORLD_CROSS = [['desert', 'desert', 'plain', 'field', 'walk'], ['desert', 'royalroad', 'snow', 'snowtown', 'walk'], ['plain', 'beach', 'sea', 'island', 'sea', '⛵'], ['plain', 'beach', 'snow', 'north', 'sea', '⛵', [[650, 710], [650, 390]]]]; // [2026-11-04] 바닷가 -> 설원 뱃길은 동쪽 바다로 돌아서(성곽/설원 칸을 가로지르지 않게)
  // [2026-10-30] "던전 층수는 다르게 표기" -- 층이 있는 곳은 겹친 카드 + 층 표시(B1/1F), 이름에서 "N층"을 빼고 층 사이 길은 노란 점선
  var WORLD_FLOOR = { cave1: 'B1', cave2: 'B2', cave3: 'B3', cave4: 'B4', snowcave: 'B1', snowcave2: 'B2', shrine: '1F', shrine2: '2F', shrine3: '3F', snow1: '1F', snow2: '2F', snow3: '3F' };
  var worldVB = null, wMoved = false;
  function worldPos(rid, nid) {
    var R = WORLD_REGIONS.filter(function (r) { return r.id === rid; })[0], o = WORLD_PLACE[rid];
    var e = R && R.nodes.filter(function (n) { return n[0] === nid; })[0];
    return e ? { x: o[0] + e[1], y: o[1] + e[2] } : null;
  }
  function worldHtml() {
    var info = {}, W = 96, H = 32, lv = myLv(), adminW = !!(st && st.adminMaps);
    WORLD_NODES.forEach(function (n) { info[n.id] = n; });
    var STY = { walk: 'stroke="#f4f0e6" stroke-width="3"', sea: 'stroke="#6fc8f0" stroke-width="3" stroke-dasharray="7 5"', fall: 'stroke="#c8a070" stroke-width="3" stroke-dasharray="3 4"' };
    var bossOf = {};
    ((st && st.bosses) || []).forEach(function (b) { bossOf[b.map === 'pyramidb' ? 'pyramid' : b.map] = b; }); // [2026-11-08] 파라오 미라(피라미드 지하) -- 피라미드 칸에
    var leftTxt = function (ms) { var m = Math.ceil(ms / 60000); return m >= 60 ? Math.floor(m / 60) + '시간' + (m % 60 ? ' ' + m % 60 + '분' : '') : m + '분'; };
    var hhmm = function (ms) { var d = new Date(ms), p = function (v) { return (v < 10 ? '0' : '') + v; }; return (d.toDateString() === new Date().toDateString() ? '' : (d.getMonth() + 1) + '/' + d.getDate() + ' ') + p(d.getHours()) + ':' + p(d.getMinutes()); }; // [2026-11-11] 오늘이면 시각만(칸 위 한 줄에 들어가게)
    var here = null, lines = '', nodes = '', labels = '';
    WORLD_REGIONS.forEach(function (R) {
      var o = WORLD_PLACE[R.id], N = {};
      R.nodes.forEach(function (e) {
        if (e[0].charAt(0) === '@') return; // 구간 밖 점선 칸은 한 장 지도에선 실제 칸끼리 잇는다(WORLD_CROSS)
        var n = info[e[0]];
        if (!n || n.locked || n.dev) return;
        N[e[0]] = { id: n.id, x: o[0] + e[1], y: o[1] + e[2], name: n.name, fill: n.fill, ink: n.ink, w: n.w, town: n.town };
      });
      labels += '<text x="' + (o[0] + (R.tx || 6)) + '" y="' + (o[1] + 14) + '" class="wr">' + R.name + '</text>'; // [2026-11-11] tx -- 맨 윗줄 칸(하피의 둥지 보스 글자)과 안 겹치게
      R.links.forEach(function (l) {
        var p = N[l[0]], q = N[l[1]];
        if (!p || !q) return;
        lines += '<line x1="' + p.x + '" y1="' + p.y + '" x2="' + q.x + '" y2="' + q.y + '" ' + (WORLD_FLOOR[p.id] && WORLD_FLOOR[q.id] ? 'stroke="#ffd27a" stroke-width="3" stroke-dasharray="2 5"' : STY[l[2] || 'walk']) + '/>'
            + (l[3] ? '<text x="' + ((p.x + q.x) / 2 + 6) + '" y="' + ((p.y + q.y) / 2 + 4) + '" class="wl">' + l[3] + '</text>' : '');
      });
      if (R.channel && N.archisea) { // 뱃길 물길 + 섬으로 가는 갈래
        var chx = o[0] + R.channel.x;
        lines += '<line x1="' + chx + '" y1="' + N.archisea.y + '" x2="' + chx + '" y2="' + (o[1] + R.channel.top) + '" stroke="#2f6a9a" stroke-width="10" stroke-linecap="round"/>'
            + '<line x1="' + chx + '" y1="' + N.archisea.y + '" x2="' + chx + '" y2="' + (o[1] + R.channel.top) + '" stroke="#6fc8f0" stroke-width="2" stroke-dasharray="7 5"/>';
        R.channel.isles.forEach(function (id) { var p = N[id]; if (p) lines += '<line x1="' + chx + '" y1="' + p.y + '" x2="' + p.x + '" y2="' + p.y + '" ' + STY.sea + '/>'; });
      }
      Object.keys(N).forEach(function (k) {
        var n = N[k], flr = WORLD_FLOOR[n.id], dm = (st && st.mapDims && st.mapDims[n.id]) || [100, 100]; // [2026-11-11] "실제 맵 크기를 반영해 크기가 조금씩 다르게" -- 넓이의 제곱근 비율(10칸 미궁 ~ 300칸 다도해)
        var nw = Math.round(14 + 5.2 * Math.sqrt(dm[0])), nh = Math.round(8 + 1.9 * Math.sqrt(dm[1]));
        var isHere = map && (map.id === n.id || (n.id === 'archi' && map.archi && !info[map.id]) || (n.id === 'forestmaze' && /^forest/.test(map.id))); // [2026-11-11] 미궁 방/끝방은 미궁 한 칸
        if (isHere) here = { x: n.x, y: n.y };
        if (bossOf[n.id]) {
          var bb = bossOf[n.id], by = n.y - nh / 2 - 5, bx = n.x - nw / 2, bh = ''; // [2026-11-11] "보스 출현 중이 너무 아래" -- 권장 Lv 밑(아래 칸과 붙어 보임) -> 칸 바로 위(지금 있는 곳이면 📍를 앞에 붙이고 "📍 여기"는 뺀다)
          if (bb.alive) labels += '<text x="' + bx + '" y="' + by + '" class="wl" style="fill:#ff6a6a;font-weight:800">' + bh + '👑 보스 출현 중</text>'; // [2026-11-13] 젠타임은 없애고(소환) "👑 보스" 버튼 목록에 내 소환 가능 시각
        }
        nodes += (adminW ? '<g style="cursor:pointer" onclick="TowerMine.worldWarp(\'' + n.id + '\')">' : '')
            // [2026-11-11] "사각형 안에 이름 말고 배경 없는 글씨, 권장 레벨 대신 Lv30 피라미드, 인원은 언제든" -- 칸(맵 크기) 안엔 인원만, 이름은 칸 아래 떠 있는 글씨
            + (flr ? '<rect x="' + (n.x - nw / 2 + 4) + '" y="' + (n.y - nh / 2 - 4) + '" width="' + nw + '" height="' + nh + '" rx="3" fill="' + n.fill + '" opacity=".55" stroke="#140d0a" stroke-width="1.5"/>' : '')
            + '<rect x="' + (n.x - nw / 2) + '" y="' + (n.y - nh / 2) + '" width="' + nw + '" height="' + nh + '" rx="3" fill="' + n.fill + '"'
            + ' stroke="' + (isHere ? '#ffe14d' : '#140d0a') + '" stroke-width="' + (isHere ? 3.5 : 1.5) + '"/>'
            + '<text x="' + n.x + '" y="' + (n.y + 3.5) + '" text-anchor="middle" class="wc" fill="' + n.ink + '" data-cnt="' + n.id + '">👥' + (mapN[n.id] || 0) + '</text>'
            + '<text x="' + n.x + '" y="' + (n.y + nh / 2 + 11) + '" text-anchor="middle" class="wn' + (isHere ? ' wn-here' : '') + '">'
            + (isHere ? '📍 ' : '') + (!n.town && minLvOf(n.id) > 1 ? '<tspan class="wlv"' + (minLvOf(n.id) > lv ? ' style="fill:#ff8a8a"' : '') + '>Lv' + minLvOf(n.id) + '</tspan> ' : '') + n.name + '</text>'
            + (adminW ? '</g>' : '');
      });
    });
    WORLD_CROSS.forEach(function (c) { // 구간 사이 길
      var p = worldPos(c[0], c[1]), q = worldPos(c[2], c[3]);
      if (!p || !q) return;
      if (c[6]) { // [2026-11-04] 꺾인 길(거쳐 가는 점) -- 글자는 가운데 꺾인 구간에
        var pts = [[p.x, p.y]].concat(c[6]).concat([[q.x, q.y]]), mid = c[6][Math.floor((c[6].length - 1) / 2)], mid2 = c[6][Math.min(c[6].length - 1, Math.floor(c[6].length / 2))];
        lines = '<polyline fill="none" points="' + pts.map(function (v) { return v[0] + ',' + v[1]; }).join(' ') + '" ' + STY[c[4]] + '/>'
            + (c[5] ? '<text x="' + ((mid[0] + mid2[0]) / 2 + 6) + '" y="' + ((mid[1] + mid2[1]) / 2 + 4) + '" class="wl">' + c[5] + '</text>' : '') + lines;
        return;
      }
      lines = '<line x1="' + p.x + '" y1="' + p.y + '" x2="' + q.x + '" y2="' + q.y + '" ' + STY[c[4]] + '/>'
          + (c[5] ? '<text x="' + ((p.x + q.x) / 2 + 6) + '" y="' + ((p.y + q.y) / 2 + 4) + '" class="wl">' + c[5] + '</text>' : '') + lines;
    });
    if (!worldVB) { var c0 = here || { x: WORLD_SIZE[0] / 2, y: WORLD_SIZE[1] / 2 }; worldVB = { x: c0.x - 190, y: c0.y - 230, w: 380, h: 460 }; }
    worldHere = here;
    var svg = '<div class="world-ctrl">'
        + '<button type="button" onclick="TowerMine.worldZoom(1.3)">＋</button><button type="button" onclick="TowerMine.worldZoom(1 / 1.3)">－</button>'
        + '<button type="button" onclick="TowerMine.worldGo(\'me\')">📍 내 위치</button>'
        + '<button type="button"' + (worldBossOpen ? ' style="outline:2px solid #ffd27a"' : '') + ' onclick="TowerMine.worldBoss()">👑 보스</button>' // [2026-11-13]
        + WORLD_REGIONS.map(function (r) { return '<button type="button" onclick="TowerMine.worldGo(\'' + r.id + '\')">' + r.name + '</button>'; }).join('') + '</div>'
        + (worldBossOpen ? worldBossHtml() : '')
        + '<div class="world-wrap"><svg id="mineWorldSvg" class="mine-world world-pan' + (worldVB.w <= WORLD_CNT_W ? ' world-close' : '') + '" viewBox="' + worldVB.x + ' ' + worldVB.y + ' ' + worldVB.w + ' ' + worldVB.h + '" preserveAspectRatio="xMidYMid meet" role="img" aria-label="광산 전체 지도">'
        + '<rect x="-400" y="-400" width="' + (WORLD_SIZE[0] + 800) + '" height="' + (WORLD_SIZE[1] + 800) + '" fill="#1d1730"/>'
        + lines + nodes + labels + '</svg></div>'
        + '<div class="mine-smith-say">끌어서 움직이고, 휠·두 손가락·＋－로 확대해요.</div>';
    if (adminW) svg += '<div class="mine-smith-say">🛠️ 관리자: 지역을 누르면 그 맵 시작점으로 바로 이동해요. '
        + '<button type="button" class="mine-mini-btn" onclick="TowerMine.openWarpForm()">좌표 입력</button></div>';
    if (adminW && st.mazeGraph) svg += mazeGraphHtml(st.mazeGraph); // [2026-11-11] 관리자 -- 미궁 안 전체 지도
    return npcHead('🗺️ 전체 지도', false) + svg;
  }
  /** [2026-11-11] 관리자 미궁 지도 -- 방(몬스터)마다 동/서/북/남 통로가 가는 방, 끝방까지 걸음. 끝방 쪽으로 가는 통로는 ✔, 지금 있는 방은 📍. 걸음이 적은 방부터. */
  function mazeGraphHtml(g) {
    var by = {}; g.forEach(function (r) { by[r.id] = r; });
    var nm = function (id) { return id === 'forestend' ? '🏁 미로의 끝방' : id === 'westroad' ? '🚪 마물의 숲 입구' : by[id] ? by[id].mob : id; };
    var rows = g.slice().sort(function (a, b) { return (a.left < 0 ? 99 : a.left) - (b.left < 0 ? 99 : b.left); }).map(function (r) {
      var cell = function (d, lb) { var tg = r[d], ok = tg === 'forestend' || (by[tg] && by[tg].left >= 0 && by[tg].left < r.left); return '<span style="margin-right:8px' + (ok ? ';color:#7cf08a;font-weight:800' : '') + '">' + lb + ' ' + escAttr(nm(tg)) + (ok ? ' ✔' : '') + '</span>'; };
      return '<div class="mine-smith-row" style="display:block' + (map && map.id === r.id ? ';outline:2px solid #ffd23f' : '') + '"><b>' + (map && map.id === r.id ? '📍 ' : '') + escAttr(r.mob) + '</b> <small>(' + (r.id === 'forestmaze' ? '입구 방 · ' : '') + '끝까지 ' + r.left + '걸음)</small><br><small>'
          + cell('e', '동→') + cell('w', '서→') + cell('n', '북→') + cell('s', '남→') + '</small></div>';
    }).join('');
    return '<div class="mine-smith-say" style="margin-top:8px">🌲 <b>마물의 숲 미궁 지도</b> (관리자만) -- ✔ = 끝방에 가까워지는 통로</div>' + rows;
  }
  var worldHere = null;
  // [2026-10-30] 접속 인원 -- 위치 알림으로 받는다(이름 없이 숫자만). 맵별 인원은 전체 지도를 가장 크게 확대했을 때만 보인다(.world-close)
  var onlineN = null, mapN = {}, WORLD_MIN_W = 160, WORLD_CNT_W = 300; // [2026-11-04] "어느 정도 커지면 인원수" -- 한두 번 확대(처음 폭 380)
  function regenText() {
    if (!el.regen || !st) return;
    el.regen.textContent = (st.nextResetAt ? '🔄 맵 리셋 ' + resetText(st.nextResetAt) : '') + (onlineN != null ? (st.nextResetAt ? ' · ' : '') + '👥 접속 ' + onlineN + '명' : '');
  }
  function worldCounts() {
    var sv = document.getElementById('mineWorldSvg');
    if (sv) sv.querySelectorAll('[data-cnt]').forEach(function (t) { t.textContent = '👥' + (mapN[t.getAttribute('data-cnt')] || 0); });
  }
  function worldApply() {
    var el2 = document.getElementById('mineWorldSvg');
    if (el2 && worldVB) { el2.setAttribute('viewBox', worldVB.x + ' ' + worldVB.y + ' ' + worldVB.w + ' ' + worldVB.h); el2.classList.toggle('world-close', worldVB.w <= WORLD_CNT_W); }
  }
  function worldZoomAt(f, cx, cy) { // f > 1 = 확대. (cx, cy) = 지도 좌표 기준점
    var nw = Math.max(WORLD_MIN_W, Math.min(1400, worldVB.w / f)), k = nw / worldVB.w;
    worldVB = { x: cx - (cx - worldVB.x) * k, y: cy - (cy - worldVB.y) * k, w: nw, h: worldVB.h * k };
    worldApply();
  }
  function worldZoom(f) { worldZoomAt(f, worldVB.x + worldVB.w / 2, worldVB.y + worldVB.h / 2); }
  function worldGo(id) {
    var c = null;
    if (id === 'me') c = worldHere;
    else if (WORLD_PLACE[id]) { var R = WORLD_REGIONS.filter(function (r) { return r.id === id; })[0]; c = { x: WORLD_PLACE[id][0] + 160, y: WORLD_PLACE[id][1] + (R ? R.vh / 2 : 200) }; }
    if (!c) return;
    worldVB = { x: c.x - worldVB.w / 2, y: c.y - worldVB.h / 2, w: worldVB.w, h: worldVB.h };
    worldApply();
  }
  /** 지도 끌기/확대 -- 화면에 그린 뒤 붙인다(renderSmithView). */
  function worldBind() {
    var sv = document.getElementById('mineWorldSvg');
    if (!sv) return;
    var pts = {}, start = null, pinch = null;
    var toMap = function (clientX, clientY) { var r = sv.getBoundingClientRect(), s2 = Math.max(worldVB.w / r.width, worldVB.h / r.height); return { x: worldVB.x + (clientX - r.left - (r.width - worldVB.w / s2) / 2) * s2, y: worldVB.y + (clientY - r.top - (r.height - worldVB.h / s2) / 2) * s2, s: s2 }; };
    sv.addEventListener('pointerdown', function (e) {
      pts[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pts);
      if (ids.length === 1) { wMoved = false; start = { cx: e.clientX, cy: e.clientY, vb: { x: worldVB.x, y: worldVB.y }, s: toMap(e.clientX, e.clientY).s }; }
      else if (ids.length === 2) { var p1 = pts[ids[0]], p2 = pts[ids[1]]; pinch = { d: Math.hypot(p1.x - p2.x, p1.y - p2.y), w: worldVB.w }; start = null; }
    });
    sv.addEventListener('pointermove', function (e) {
      if (!pts[e.pointerId]) return;
      pts[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pts);
      if (ids.length === 2 && pinch) {
        var p1 = pts[ids[0]], p2 = pts[ids[1]], d = Math.hypot(p1.x - p2.x, p1.y - p2.y), m = toMap((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
        // [2026-10-28] "두 손가락으로 확대하면 너무 한 번에 커진다" -- 예전엔 (처음 폭 / 지금 폭)을 곱해서 움직일 때마다 확대가 눈덩이처럼 불었다.
        //  목표 폭 = 처음 폭 / (손가락 간격 비율^0.8) -- 손가락을 벌린 만큼보다 조금 덜(0.8제곱) 커진다.
        worldZoomAt((worldVB.w / pinch.w) * Math.pow(d / pinch.d, 0.8), m.x, m.y);
        wMoved = true;
        e.preventDefault();
        return;
      }
      if (!start) return;
      var dx = e.clientX - start.cx, dy = e.clientY - start.cy;
      if (Math.abs(dx) + Math.abs(dy) > 6) wMoved = true;
      if (!wMoved) return;
      worldVB.x = start.vb.x - dx * start.s; worldVB.y = start.vb.y - dy * start.s;
      worldApply();
      e.preventDefault();
    });
    var end = function (e) { delete pts[e.pointerId]; if (Object.keys(pts).length < 2) pinch = null; if (!Object.keys(pts).length) start = null; };
    sv.addEventListener('pointerup', end);
    sv.addEventListener('pointercancel', end);
    sv.addEventListener('pointerleave', end);
    sv.addEventListener('wheel', function (e) { e.preventDefault(); var m = toMap(e.clientX, e.clientY); worldZoomAt(e.deltaY < 0 ? 1.15 : 1 / 1.15, m.x, m.y); }, { passive: false });
  }
  /** [2026-10-28] 관리자 -- 전체 지도에서 누른 지역(맵) 시작점으로 이동(서버 mineAdminWarp가 관리자인지 다시 확인). */
  function worldWarp(id) {
    if (!st || !st.adminMaps || busy || wMoved) return; // [2026-10-28] 끌다가 놓은 건 누른 게 아니다
    var e = st.adminMaps.filter(function (m) { return m.id === id; })[0];
    if (!e) { msg('이동할 수 없는 곳이에요.'); return; }
    closeSmith();
    setAuto(false);
    travel('/api/tower-mine-admin-warp?mapId=' + encodeURIComponent(id) + '&x=' + (e.sx != null ? e.sx : 0) + '&y=' + (e.sy != null ? e.sy : 0), '이동하지 못했어요.');
  }
  // [2026-10-28] 🎒 장비 -- "반지류 하나, 옷류 하나, 장갑류 하나": 칸마다 가진 장비를 보여 주고 끼기/빼기(같은 칸의 다른 장비는 저절로 빠진다).
  var EQ_SLOTS = [
    { id: 'weapon', name: '⚔️ 무기', hint: '하나만 장착 가능', none: '' }, // [2026-10-28] 칼(기본), 양산(다도해 상인)
    { id: 'armor', name: '🛡️ 갑옷·옷', hint: '하나만 장착 가능', none: '장착 가능 장비 없음 (20·40·60·80레벨 근처 몬스터가 드물게 떨어뜨려요)' }, // [2026-11-06] "갑옷과 옷은 한 카테고리" -- 방한 외투도 이 칸
    { id: 'ring', name: '💍 반지', hint: '하나만 장착 가능', none: '장착 가능 장비 없음' },
    { id: 'gloves', name: '🧤 장갑', hint: '하나만 장착 가능', none: '장착 가능 장비 없음' },
    { id: 'necklace', name: '📿 목걸이', hint: '하나만 장착 가능', none: '장착 가능 장비 없음' },
    { id: 'boots', name: '👢 신발', hint: '하나만 장착 가능', none: '장착 가능 장비 없음' }, // [2026-10-28] "신발이 빠져서 추가"
    { id: 'tool', name: '⛏️ 채집 도구', hint: '하나만 장착 가능', none: '' } // [2026-10-28] 무기(칼/양산)와 채집 도구(곡괭이류)는 칸이 따로
  ];
  // [2026-10-28] "장비 버튼 -> 캐러셀, 장착 탭과 치장 탭으로 나눠서 둘 다 조정" -- 장착: 반지/옷/장갑 줄, 치장: 가진 치장만 부위별 줄(입기/기본으로)
  var equipTab = 'gear';
  function equipTabGo(t) { equipTab = t; if (smithOpen) renderSmith(smithNpc); }
  // [2026-10-28] "메뉴의 장비 -> 인벤토리, 장비/치장/기타로 분류, 탭은 항상 상단에 고정" -- 탭을 위쪽 고정 머리(닫기 줄) 안에 넣는다
  function equipHtml() {
    var tab = function (id, label) { return '<button type="button" class="' + (equipTab === id ? 'on' : '') + '" onclick="TowerMine.equipTab(\'' + id + '\')">' + label + '</button>'; };
    var h = '<div class="mine-smith-head inv-head">🎒 인벤토리 <span><button type="button" class="mine-close" onclick="TowerMine.closeSmith()">✖ 닫기</button></span>'
        + '<div class="eq-tabs">' + tab('gear', '⚔️ 장비') + tab('look', '👗 치장') + tab('skill', '🌀 스킬') + tab('etc', '📦 기타') + '</div></div>';
    if (equipTab === 'etc') return h + invEtcHtml();
    if (equipTab === 'skill') return h + skillHtml(); // [2026-11-04] 검기
    if (equipTab === 'look') {
      var lk = st.look || {};
      h += '<div class="mine-look-top"><div class="mine-look-prevs">' + ['down', 'right', 'up'].map(function (d) { return '<img class="mine-look-prev" src="' + lookImg(lk, d) + '" alt="">'; }).join('') + '</div>'
          + '<div class="mine-smith-say">가지고 있는 치장만 보여요. 새 치장은 명품상점에서 살 수 있어요.</div></div>';
      ['hat', 'hair', 'top', 'bottom', 'shoes', 'wing'].forEach(function (slot) {
        var cur = lk[slot + 'Code'] || '', list = (st.cosShop || []).filter(function (c) { return c.slot === slot && c.owned; });
        h += '<div class="cos-slot"><div class="cos-slot-h">' + LOOK_SLOT_NAME[slot] + ' <small>' + list.length + '개</small></div><div class="cos-car" data-slot="l-' + slot + '">'
            + '<div class="cos-card' + (cur ? '' : ' worn') + '"><img src="' + lookImg(lookWith(slot, ''), 'down') + '" alt=""><div class="cos-nm">기본</div>'
            + (cur ? '<button type="button" onclick="TowerMine.lookAct(\'wear\', \'\', \'' + slot + '\')">입기</button>' : '<span class="mine-worn">착용 중</span>') + '</div>';
        list.forEach(function (c) {
          h += '<div class="cos-card' + (c.code === cur ? ' worn' : '') + (c.note ? ' gm' : '') + '"><img src="' + lookImg(lookWith(c.slot, c.look), 'down') + '" alt="' + c.name + '"><div class="cos-nm">' + c.name + '</div>'
              + (c.code === cur ? '<span class="mine-worn">착용 중</span>' : '<button type="button" onclick="TowerMine.lookAct(\'wear\', \'' + c.code + '\')">입기</button>') + '</div>';
        });
        h += '</div></div>';
      });
      return h;
    }
    return h + dollHtml(); // [2026-10-28] 캐릭터 그림 + 부위별 칸(누르면 그 부위 장비 고르기)
    EQ_SLOTS.forEach(function (sl) {
      var rows = (st.equip || []).filter(function (e) { return e.slot === sl.id; });
      h += '<div class="cos-slot"><div class="cos-slot-h">' + sl.name + (sl.hint ? ' <small>' + sl.hint + '</small>' : '') + '</div>';
      if (!rows.length) { h += '<div class="mine-eq-empty">' + sl.none + '</div></div>'; return; }
      h += '<div class="cos-car" data-slot="e-' + sl.id + '">' + rows.map(function (e) {
        return '<div class="cos-card gear-card' + (e.worn ? ' worn' : '') + '" data-code="' + e.code + '" onclick="TowerMine.eqInfo(this, event)"><div class="gear-ico">' + sl.name.split(' ')[0] + '</div><div class="cos-nm">' + e.name + '</div><small class="gear-eff">' + e.effect + '</small>'
            + (e.worn ? '<span class="mine-worn">착용 중</span><button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + e.code + '\')">해제</button>'
                : '<button type="button" onclick="TowerMine.equipRing(\'' + e.code + '\')">' + '장착' + '</button>') + '</div>';
      }).join('') + '</div></div>';
    });
    return h + '<div class="mine-smith-say">장비를 누르면 설명이 나와요.</div>'; // [2026-10-28] 방한 장비 안내는 카드 설명으로
  }
  // [2026-10-28] "인벤토리에 캐릭터 이미지 + 부위별 장착 장비, 누르면 그 부위에 장착 가능한 것이 팝(장착 해제 포함)" -- 가운데 캐릭터, 둘레에 부위 칸
  var eqPop = null;
  function eqPopGo(id) { eqPop = id; if (smithOpen) renderSmith(smithNpc); }
  function dollHtml() {
    var slot = function (sl) {
      var rows = (st.equip || []).filter(function (e) { return e.slot === sl.id; }), w = rows.filter(function (e) { return e.worn; })[0];
      var en = sl.id === 'weapon' && st.wenh && st.wenh.lv ? ' <b style="color:#ffd27a">+' + st.wenh.lv + '</b>' : ''; // [2026-11-06] 무기 칸 강화
      return '<button type="button" class="doll-slot' + (w ? ' on' : '') + '" onclick="TowerMine.eqPop(\'' + sl.id + '\')"><b>' + sl.name + en + '</b><span>' + (w ? gradeName(w.name, w.grade) : '없음') + '</span></button>';
    };
    var by = {}; EQ_SLOTS.forEach(function (sl) { by[sl.id] = sl; });
    var h = '<div class="doll"><div class="doll-col">' + ['weapon', 'armor', 'ring', 'gloves'].map(function (k) { return slot(by[k]); }).join('') + '</div>' // [2026-11-06] 갑옷 칸 -- 좌우 4칸씩
        + '<div class="doll-char"><img src="' + lookImg(st.look || {}, 'down') + '" alt="내 캐릭터"><small>Lv.' + myLv() + '</small></div>'
        + '<div class="doll-col">' + ['necklace', 'boots', 'tool'].map(function (k) { return slot(by[k]); }).join('') + '</div></div>'
        + '<div class="mine-smith-say">칸을 누르면 그 부위에 장착할 수 있는 장비가 나와요.</div>';
    if (eqPop && by[eqPop]) {
      var sl = by[eqPop], rows = (st.equip || []).filter(function (e) { return e.slot === sl.id; });
      var verb = ['장착', '해제']; // [2026-10-30] "들기, 걸기 대신 장착/해제로"
      h += '<div class="eq-pop" onclick="if (event.target === this) TowerMine.eqPop(null)"><div class="eq-pop-box"><div class="eq-pop-h">' + sl.name + (sl.hint ? ' <small>' + sl.hint + '</small>' : '')
          + '<button type="button" onclick="TowerMine.eqPop(null)">✖</button></div>';
      if (!rows.length) h += '<div class="mine-eq-empty">' + sl.none + '</div>';
      rows.forEach(function (e) {
        var off = e.worn && e.code !== 'SWORD' && e.code !== 'BASIC_PICK'; // 무기/채집 도구는 해제하면 기본(칼/기본 곡괭이) -- 기본은 "사용 중"
        h += '<div class="mine-smith-row' + (e.worn ? ' worn' : '') + '"><span class="nm">' + (e.worn ? '✔ ' : '') + gradeName(e.name, e.grade) + '<small>' + (e.grades ? effLines(eqTip(e)) : eqTip(e)) + '</small>'
            + (e.grades && e.grades.length ? '<span class="gchips">' + e.grades.map(function (g) { // [2026-10-30] 무기 등급별 보관 -- 가진 등급을 색 칩으로, 누르면 그 등급을 든다
                return '<button type="button" class="gchip' + (g === e.grade ? ' on' : '') + (g === 7 ? ' g7' : '') + '"' + (g < 7 ? ' style="background:' + GRADE_COL[g] + '"' : '') + ' onclick="TowerMine.equipRing(\'' + e.code + '#' + g + '\')" aria-label="등급 ' + (g + 1) + '"></button>';
              }).join('') + '</span>' : '') + '</span>'
            + (e.worn ? (off ? '<button type="button" onclick="TowerMine.equipRing(\'' + e.code + '\')">' + verb[1] + '</button>' : '<span class="mine-worn">사용 중</span>')
                : '<button type="button" onclick="TowerMine.equipRing(\'' + e.code + '\')">' + verb[0] + '</button>') + '</div>';
      });
      if (sl.id === 'weapon') h += wenhHtml(); // [2026-11-06] "강화는 슬롯에" -- 무기 칸 팝업 아래
      h += '</div></div>';
    }
    return h;
  }
  // [2026-11-06] "강화는 슬롯에 강화되는 형태로, 따로 탭은 없어도" -- 무기 칸 팝업 아래: 칸 단계, 지금 무기 성능(등급 몫 + 강화 몫), 다음 단계 확률/성능/재료
  // [2026-11-06] "강화 표시에 탭이나 엔터" -- "공격력 +5400 (등급 3600 + 강화 1800) · 공격력 +9% (...)"를 옵션마다 한 줄, 등급/강화 몫은 들여 쓴 다음 줄로
  function effLines(s) {
    return String(s || '').split(' · ').map(function (p) {
      var m = p.match(/^(.*?) \(등급 (.*?) \+ 강화 (.*?)\)$/);
      return m ? m[1] + '<br><span style="opacity:.7">&nbsp;&nbsp;└ 등급 ' + m[2] + ' · 강화 +' + m[3] + '</span>' : p;
    }).join('<br>');
  }
  function wenhHtml(atNpc) { // [2026-11-06] atNpc = 무기 강화사 창(버튼), 아니면 인벤토리 무기 칸 팝업(보기만)
    var w = st.wenh || { lv: 0 }, h = '<div class="mine-smith-say" style="margin-top:8px">🔨 <b>무기 칸 강화 +' + (w.lv || 0) + '</b> · 어떤 무기를 들어도 적용<br>'
        + '<small>1강마다 공격력 +10% · 5강마다 공격력 % +1 (전설 이상)<br>실패하면 재료만 사라져요</small></div>';
    if (!w.name) return h + '<div class="mine-eq-empty">드랍 무기를 장착해야 강화할 수 있어요.</div>';
    h += '<div class="mine-smith-row"><span class="nm"><span>' + gradeName(w.name, w.grade) + ' <b style="color:#ffd27a">+' + w.lv + '</b></span><small>' + effLines(w.effect) + '</small>';
    if (w.pct != null && !atNpc) h += '<small style="color:#9fe0ff">다음 +' + (w.lv + 1) + ' · 성공 ' + w.pct + '%<br>왕도 5시 방향 무기 강화사에게서</small></span></div>';
    else if (w.pct != null) h += '<small style="color:#9fe0ff;margin-top:6px">▶ 다음 +' + (w.lv + 1) + ' · 성공 ' + w.pct + '%<br>' + effLines(w.nextEffect) + '</small>' + costHtml(w.costs) + '</span>'
        + '<button type="button" ' + (w.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.enhance()">강화</button></div>';
    else h += '<small>최고 강화예요</small></span><button type="button" data-lack="1" disabled>완료</button></div>';
    return h;
  }
  function enhance() { gearAct('/api/tower-mine-enhance?code=weapon'); }
  // [2026-11-06] "메뉴에 용병 관리(최대 3칸, 직업별)" + "용병 관리 창은 인벤토리의 장비/치장/스킬 탭을 그대로" -- 위에 직업 3칸(계약한 용병 고르기), 아래 탭.
  //  장비 = 기본 칼/활/지팡이 + 기본 갑옷(바꿀 수 없음), 스킬 = 특화 속성 + 20레벨마다 단계.
  var mercSel = null, mercTabV = 'gear'; // [2026-11-06] 치장 탭은 없앰(이름마다 모습)
  function fmtN(v) { v = +v || 0; return v >= 1e6 ? (v / 1e6).toFixed(1) + 'M' : v >= 1e4 ? (v / 1e3).toFixed(1) + 'k' : String(v); }
  function mercSumHtml(m) {
    return MERC_ICON[m.t | 0] + ' <b>' + (m.name || '전사') + '</b> Lv.' + (m.lv || '?') + (m.elName ? ' · ' + (EL_ICON[m.el] || '') + m.elName : '') + '<br>공격력 ' + fmtN(m.atk) + ' · 체력 ' + (m.hp != null ? fmtN(m.hp) + '/' : '') + fmtN(m.max);
  }
  var MERC_GROW = ['20레벨마다 검기 +1단계(범위·피해)', '20레벨마다 한 명 피해 +20%', '속성 구슬(파이어볼 / 라이트닝볼 / 아이스볼) 한 명'];
  function mercMgrHtml() {
    var list = st.mercs || [], on = list.filter(function (x) { return x.on; })[0], own = list.filter(function (x) { return x.owned; });
    if (mercSel == null || !list[mercSel] || (own.length && !list[mercSel].owned)) mercSel = on ? on.t : own.length ? own[0].t : 0;
    var tab = function (id, label) { return '<button type="button" class="' + (mercTabV === id ? 'on' : '') + '" onclick="TowerMine.mercTab(\'' + id + '\')">' + label + '</button>'; };
    var h = '<div class="mine-smith-head inv-head">🛡️ 용병 관리 <span><button type="button" class="mine-close" onclick="TowerMine.closeSmith()">✖ 닫기</button></span>'
        + '<div class="eq-tabs">' + tab('gear', '⚔️ 장비') + tab('skill', '🌀 스킬') + '</div></div>';
    if (own.length <= 1) { // [2026-11-06] "1개만 노출(지금은 1개만)" -- 계약한 용병 한 명만(없으면 안내)
      if (!own.length) return h + '<div class="mine-smith-say">아직 계약한 용병이 없어요.<br><small>왕도 7시 방향 용병소에서 계약할 수 있어요 (Lv.5~30, 불·물·번개 중 무작위 특화)</small></div>';
    } else h += '<div class="eq-tabs merc-sel">' + list.filter(function (x) { return x.owned; }).map(function (x) {
      return '<button type="button" class="' + (x.t === mercSel ? 'on' : '') + '" onclick="TowerMine.mercSel(' + x.t + ')">' + MERC_ICON[x.t] + ' ' + (x.owned && x.nm ? x.nm : x.name) + (x.owned ? ' ' + x.lv : '') + (x.on ? ' ✔' : x.unpaid && x.owned ? ' ⚠️' : '') + '</button>';
    }).join('') + '</div>';
    var m = list[mercSel] || {};
    if (!m.owned) return h + '<div class="mine-smith-say">아직 계약하지 않은 ' + (m.name || '용병') + '예요.<br><small>왕도 7시 방향 용병소에서 계약할 수 있어요 (Lv.5~30, 불·물·번개 중 무작위 특화)</small></div>';
    if (mercTabV === 'skill') { // [2026-11-06] "스킬 설명, 아이콘, 데미지 비율 + 끔/1/2/3 골라 안 쓰게" -- 평타(20레벨마다 1~5) / 2차 스킬(40/60/80에 1~3, 20초마다)
      var ic1 = MERC_SK_ICON[m.t], ic2 = m.t === 0 ? '⚔️' : m.t === 1 ? '🌧️' : m.el === 6 ? '☄️' : m.el === 2 ? '⚡' : '🌪️';
      var nm1 = m.t === 0 ? m.elName + ' 검기' : m.t === 1 ? (m.el === 6 ? '불화살' : m.el === 2 ? '번개 화살' : '물화살') : (m.el === 6 ? '파이어볼' : m.el === 2 ? '라이트닝볼' : '아이스볼');
      var nm2 = m.t === 0 ? '이기어검' : m.t === 1 ? '화살비' : m.el === 6 ? '메테오' : m.el === 2 ? '체인 라이트닝' : '토네이도';
      var selRow = function (which, cur, max, top, lvOf) { // 끔 / 1 / 2 ... (열리지 않은 단계는 자물쇠)
        var r = '<div class="merc-sk-sel">', o = cur === 0;
        r += '<button type="button" class="' + (o ? 'on' : '') + '" onclick="TowerMine.mercSkill(' + m.t + ', ' + which + ', 0)">끔</button>';
        for (var n = 1; n <= top; n++) r += n <= max ? '<button type="button" class="' + (cur === n ? 'on' : '') + '" onclick="TowerMine.mercSkill(' + m.t + ', ' + which + ', ' + n + ')">' + n + '</button>'
            : '<button type="button" class="lock" disabled title="Lv.' + lvOf(n) + '부터">🔒' + n + '</button>';
        return r + '</div>';
      };
      var list = function (arr, cur, max, lvOf) { return '<div class="merc-sk-list">' + (arr || []).map(function (d, i) { var n = i + 1; return '<div class="' + (n === cur ? 'on' : n > max ? 'lock' : '') + '"><b>' + n + '단계</b> <span>Lv.' + lvOf(n) + '</span><br>' + d + '</div>'; }).join('') + '</div>'; };
      var lv1 = function (n) { return Math.max(1, (n - 1) * 20); }, lv2 = function (n) { return 40 + (n - 1) * 20; };
      h += '<div class="merc-sk"><div class="merc-sk-h"><span class="ico">' + ic1 + '</span><div><b>평타 · ' + nm1 + '</b><small>' + (EL_ICON[m.el] || '') + ' ' + m.elName + ' 속성 · 공격할 때마다 · 상성 몬스터 피해 x1.5</small></div></div>';
      if (m.t === 2) h += '<div class="merc-sk-cur">' + m.skill + '<br><small>평타는 단계 없이 늘 한 명(구슬은 속성마다 모양이 달라요)</small></div>';
      else h += selRow(1, m.st, m.stMax, 5, lv1) + '<div class="merc-sk-cur">' + (m.st ? '▶ ' + m.st + '단계 · ' + (m.skills1 || [])[m.st - 1] : '꺼져 있어요 -- 단계 효과 없이 기본 공격만') + '</div>' + list(m.skills1, m.st, m.stMax, lv1);
      h += '</div>';
      h += '<div class="merc-sk' + (m.s2Max ? '' : ' lock') + '"><div class="merc-sk-h"><span class="ico">' + ic2 + '</span><div><b>2차 스킬 · ' + nm2 + '</b><small>쿨타임 20초 · 40레벨에 열리고 60·80레벨에 강화</small></div></div>'
          + (m.s2Max ? selRow(2, m.s2, m.s2Max, 3, lv2) + '<div class="merc-sk-cur">' + (m.s2 ? '▶ ' + m.s2 + '단계 · ' + (m.skills2 || [])[m.s2 - 1] : '꺼져 있어요 -- 20초마다 쓰지 않아요') + '</div>' : '<div class="merc-sk-cur">🔒 용병 Lv.40에 열려요 (지금 Lv.' + m.lv + ')</div>')
          + list(m.skills2, m.s2, m.s2Max, lv2) + '</div>';
      h += '<div class="mine-smith-say"><small>피해는 모두 용병 공격력(공격력 ' + fmtN(m.atk) + ') 기준이에요. 끄거나 낮은 단계를 고르면 그만큼 약하게 공격해요.</small></div>';
      if (st.adminMaps && m.lv < 99) h += '<div class="mine-smith-row"><span class="nm">🛠️ 관리자<small>용병 레벨을 99로</small></span><button type="button" onclick="TowerMine.mercLv99(' + m.t + ')">Lv.99</button></div>';
      return h;
    }
    h += mercViewBtns();
    var pct = m.need ? Math.min(100, Math.round(m.exp * 100 / m.need)) : 100, dslot = function (a, b) { return '<div class="doll-slot on" style="cursor:default"><b>' + a + '</b><span>' + b + '</span></div>'; };
    h += '<div class="doll"><div class="doll-col">' + dslot('⚔️ 무기', m.weapon || '기본 무기') + dslot('🛡️ 갑옷', '기본 갑옷') + '</div>'
        + '<div class="doll-char' + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(m.t, m.ni, m.nm) + '<small>' + (m.nm || '') + ' Lv.' + m.lv + '</small></div>'
        + '<div class="doll-col">' + dslot((EL_ICON[m.el] || '') + ' 속성', m.elName + ' 특화') + dslot('🌀 스킬', m.st + '단계') + '</div></div>'
        + '<div class="mine-smith-say">공격력 ' + fmtN(m.atk) + ' · 체력 ' + fmtN(m.max) + ' · 사거리 ' + m.reach + '칸'
        + '<span class="mine-bar" style="margin-top:6px"><i style="display:block;height:100%;width:' + pct + '%;background:#9fe0ff"></i></span>'
        + '<small>경험치 ' + (m.need ? fmtN(m.exp) + ' / ' + fmtN(m.need) : '최고 레벨') + ' · 내가 얻는 경험치의 1%</small><br><small>장비는 기본 장비만 써요.</small></div>'
        + '<div class="mine-smith-say" style="margin-bottom:4px">공격 방식</div><div class="merc-view merc-mode"><button type="button" class="' + (m.mode ? '' : 'on') + '" onclick="TowerMine.mercMode(0)">🤝 협공 모드<small>나와 싸우는 몬스터만</small></button>'
        + '<button type="button" class="' + (m.mode ? 'on' : '') + '" onclick="TowerMine.mercMode(1)">⚔️ 선공 모드<small>사거리 안 몬스터를 먼저</small></button></div>' // [2026-11-06] 선공/협공
        + '<div class="mine-smith-row"><span class="nm">' + (m.on ? '✔ 함께 다니는 중' : '용병소에서 쉬는 중') + '</span><button type="button" onclick="TowerMine.mercUse(' + m.t + ')">' + (m.want || m.on ? '쉬게 하기' : '데리고 다니기') + '</button></div>'
        + mercPayHtml(m);
    return h;
  }
  // [2026-11-06] 급여 -- 남은 기간(선금 포함), 다음 급여, 하루치 주기(선금, 7일까지), 자동 차감 켜기/끄기
  function mercLeft(ms) { if (ms <= 0) return '0분'; var d = Math.floor(ms / 86400000), h = Math.floor(ms % 86400000 / 3600000), mi = Math.floor(ms % 3600000 / 60000); return (d ? d + '일 ' : '') + (d || h ? h + '시간' : mi + '분'); }
  function mercPayHtml(m) {
    return ''; // [2026-11-11] 날마다 내던 급여 없앰(처음 계약 때 한 번만)
    var left = (m.paidUntil || 0) - nowSrv();
    return '<div class="mine-smith-row merc-pay' + (m.unpaid ? ' lack' : '') + '"><span class="nm">💰 급여 <small>' + (m.unpaid ? '밀렸어요 -- 주면 바로 다시 일해요' : '남은 기간 ' + mercLeft(left) + (left > 86400000 ? ' (선금 포함)' : '')) + '</small>'
        + '<small>다음 급여: <b>' + m.sal + '</b> · 자동 차감 ' + (m.auto ? '켜짐' : '꺼짐') + '</small></span>'
        + '<span class="merc-pay-btns"><button type="button" onclick="TowerMine.mercPay(' + m.t + ')">' + (m.unpaid ? '급여 주기' : '선금 1일') + '</button>'
        + '<button type="button" class="' + (m.auto ? 'on' : '') + '" onclick="TowerMine.mercAuto(' + m.t + ')">자동 ' + (m.auto ? 'ON' : 'OFF') + '</button></span></div>';
  }
  function mercSkill(c, which, n) { // [2026-11-06] 스킬 단계 고르기(0 = 끔)
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=skill&tier=' + c + '&code=' + which + ':' + n).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function mercMode(md) { // [2026-11-06] 용병 선공 / 협공
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=mode&tier=' + md).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function mercLv99(c) { // [2026-11-06] 관리자 -- 용병 Lv.99
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=lv99&tier=' + c).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function mercPay(c) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=pay&tier=' + c).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function mercAuto(c) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=auto&tier=' + c).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  /** 급여 자동 차감/밀림 안내(접속할 때 밀린 날 포함). */
  function mercPayNote(list) {
    if (!list || !list.length) return;
    list.forEach(function (t) { killFeed(t, /⚠️|⏰/.test(t) ? '#ffb04a' : '#ffe9a8'); });
    msg(list.join('\n'), 6000);
  }
  function mercSelGo(c) { mercSel = c; if (smithOpen) renderSmith(smithNpc); }
  function mercTabGo(t) { mercTabV = t; if (smithOpen) renderSmith(smithNpc); }
  function mercAfter(d) {
    if (d.list) st.mercs = d.list;
    if (d.mercPay) setTimeout(function () { mercPayNote(d.mercPay); }, 3300);
    st.merc = d.merc || null;
    if (d.ores) st.ores = d.ores;
    mercShop = d;
    msg(d.message || '완료', 3200);
    if (smithOpen) renderSmith(smithNpc);
  }
  function mercUse(c) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=use&tier=' + c).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  // [2026-10-28] 인벤토리 기타 탭 -- "광석/보석/고유(보스 아이템) 등 카테고리로". 가진 것만(0개는 숨김).
  var INV_CATS = [
    ['⛏️ 광석', function (g) { return (g >= 1 && g <= 6) || g === 19 || g === 20 || g === 70; }], // [2026-11-06] 은하수 조각 // [2026-10-28] 빙석, 달빛석도 광석
    ['💎 보석', function (g) { return (g >= 21 && g <= 29) || (g >= 34 && g <= 38); }],
    ['🔥 괴', function (g) { return g >= 40 && g <= 44; }],
    ['✨ 세공된 보석', function (g) { return g >= 45 && g <= 59; }],
    ['📅 요일석', function (g) { return g >= 11 && g <= 18; }],
    ['👑 고유 증표 (보스·피라미드)', function (g) { return g === 31 || g === 32 || g === 33 || g === 39 || g === 64 || g === 65 || g === 71; }],
    ['🧩 조각', function (g) { return (g >= 60 && g <= 63) || (g >= 66 && g <= 69); }] // [2026-10-29] 무기 조각, [2026-11-06] 갑옷 조각
  ];
  function invEtcHtml() {
    var ores = (st.ores || []).filter(function (o) { return o.qty > 0; }), used = {}, h = '';
    INV_CATS.forEach(function (c) {
      var list = ores.filter(function (o) { return c[1](o.grade); });
      list.forEach(function (o) { used[o.grade] = 1; });
      if (!list.length) return;
      h += '<div class="minv-cat"><div class="cos-slot-h">' + c[0] + ' <small>' + list.length + '종</small></div><div class="minv-grid">'
          + list.map(function (o) { return '<div class="minv-item"><img src="' + oreIcon(o.grade) + '" alt=""><span>' + (o.name || ORE_NAME[o.grade]) + '</span><b>' + o.qty + '</b></div>'; }).join('') + '</div></div>';
    });
    var rest = ores.filter(function (o) { return !used[o.grade]; });
    if (rest.length) h += '<div class="minv-cat"><div class="cos-slot-h">📦 그 밖</div><div class="minv-grid">'
        + rest.map(function (o) { return '<div class="minv-item"><img src="' + oreIcon(o.grade) + '" alt=""><span>' + (o.name || ORE_NAME[o.grade]) + '</span><b>' + o.qty + '</b></div>'; }).join('') + '</div></div>';
    var a = st.archi || {}, keys = [];
    if (a.boat) keys.push('⛵ 1인용 배');
    if (a.key) keys.push('🗝️ 신궁 열쇠');
    if (keys.length) h += '<div class="minv-cat"><div class="cos-slot-h">🗝️ 소지품</div><div class="minv-grid">' + keys.map(function (k) { return '<div class="minv-item"><span>' + k + '</span></div>'; }).join('') + '</div></div>';
    return h || '<div class="mine-smith-say">아직 가진 게 없어요.</div>';
  }
  // [2026-10-28] "장비를 누르면 설명" -- 카드(버튼 말고)를 누르면 효과와 주의점을 안내
  var EQ_TIPS = { ASSASSIN_NECKLACE: '공격할 때 15% 확률로 은신. 은신 중엔 몸이 반투명해지고 몬스터의 일반 공격을 2번 회피(15초지속). 회피가 남아 있을 때 공격하면 은신이 풀리며 2배 피해! 원거리 공격과 한기·더위는 못 피해요. ',
    MIND_NECKLACE: '착용시 몬스터 처치 경험치 추가(1~3단계 +10/20/30%)',
    SWORD: '기본 무기', 
    PARASOL: '공격력 +10, 다도해 햇빛 피해 2단계 감소', 
    BASIC_PICK: '한 칸씩 캐는 기본 곡괭이예요.', 
    COAT: '설산의 한기를 단계만큼 막아 줘요(1~3). 몸이 둔해져 몬스터에게 받는 피해가 2배. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요. 털옷 장인 의뢰로 다음 단계로.',
    COAT1: '설산의 한기를 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    COAT2: '설산의 한기를 크게 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    GLOVES: '한기 방어 +단계(1~3). 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요. 털옷 장인 의뢰로 다음 단계로.',
    DRAGON_RING: '끼고 있는 동안 물 위를 땅처럼 걸을 수 있어요.(물 위에서는 못 빼요).',
    REGEN_GLOVES: '몬스터에게 받은 피해의 50%를 5초에 걸쳐 회복 (2단계: 65% + 처치 시 최대 체력 3%)', // [2026-11-04] 80%/4초 -> 50%/5초, 2단계
    TWIN_GLOVES: '한 번 공격할 때 기본 공격력의 55%로 더블어택(합쳐서 110%). 타격마다 치명타 확률 +5%. 초당 최대 체력의 1% 소모',
    BERSERK_GLOVES: '공격력이 50% 올라가는 대신 체력이 차오르는 속도가 20% 느려져요.' };
  /** [2026-11-04] "2단계 장비 옵션이 안 나온다" -- 고정 설명(EQ_TIPS)은 1단계 기준이라, 이름에 (n단계)가 붙은 장비는 서버가 준 그 단계 효과를 보여 준다. */
  function eqTip(e) { var t = EQ_TIPS[e.code]; return t && !/\(\d단계\)/.test(e.name) ? t : e.effect; }
  function eqInfo(elm, ev) {
    if (ev && ev.target && ev.target.tagName === 'BUTTON') return; // 끼기/빼기 버튼은 그대로
    var code = elm.getAttribute('data-code'), e = null;
    (st.equip || []).forEach(function (q) { if (q.code === code) e = q; });
    if (!e) return;
    msg('ℹ️ ' + e.name + ' -- ' + eqTip(e), 4200);
  }
  function openEquip() {
    if (!opened || !map) return;
    eqPop = null;
    openSmith({ name: '인벤토리', line: '', kind: 'equip' }, 'equip');
  }
  // [2026-10-28] 다도해 NPC -- 신궁 수문장(수색 임무: 닌자 100명 -> 비급서 + 달빛석), 배 목수(달빛석 -> 1인용 배), 뱃사공(배 타기 -> 뱃길)
  function archiHtml(n) {
    if (!st.archi) return npcHead(n.kind === 'ninjaquest' ? '🥷 신궁 수문장' : n.kind === 'boatwright' ? '🔨 배 목수' : '⛵ 뱃사공', false) + talkBlock(n, n.line)
        + '<div class="mine-smith-say">🚧 아직 준비 중이에요. 곧 열려요!</div>'; // [2026-10-28] 본섬만 열림 -- 신궁 임무/배/뱃길은 아직 운영자만
    var a = st.archi || {}, h = npcHead(n.kind === 'ninjaquest' ? '🥷 신궁 수문장' : n.kind === 'boatwright' ? '🔨 배 목수' : '⛵ 뱃사공', false) + talkBlock(n, n.line);
    var have = '<div class="mine-smith-say">🌙 달빛석 ' + (a.moon || 0) + '개 · 📜 비급서 ' + (a.books || 0) + '권 · ⛵ 1인용 배 ' + (a.boat ? '있음' : '없음') + (a.key ? ' · 🗝️ 신궁 열쇠' : '') + '</div>';
    if (n.kind === 'ninjaquest') { // [2026-10-28] 수색 임무 보상 = 닌자 두목의 방 열쇠(비급서/달빛석은 두목 첫 처치 보상)
      var boss = '<div class="mine-smith-say">🗝️ 신궁 열쇠가 있으면 동쪽 문(닌자 두목의 방)에 들어갈 수 있어요. 두목은 은신과 분신술을 써요 -- 다섯 중 <b>자세가 다른 하나</b>가 진짜!'
          + ' 쓰러뜨리면 30%로 📜 비급서 + 🌙 달빛석!' + '</div>';
      if (a.key) return h + have + boss;
      if (!a.quest) return h + have + '<div class="mine-smith-row"><span class="nm">신궁 수색<small>신궁 1~3층의 닌자 ' + (a.need || 100) + '명 처치 -> 🗝️ 닌자 두목의 방 열쇠</small></span><button type="button" onclick="TowerMine.archiAct(\'quest\')">임무 받기</button></div>';
      var done = (a.kills || 0) >= (a.need || 100), pct = Math.min(100, Math.round((a.kills || 0) * 100 / (a.need || 100)));
      return h + have + '<div class="mine-smith-row"><span class="nm">🥷 수색 중 ' + (a.kills || 0) + ' / ' + (a.need || 100) + '<span class="mine-bar hp" style="margin-top:4px"><i style="width:' + pct + '%"></i></span>'
          + '<small>' + (done ? '다 처치했어요! 열쇠를 받으세요.' : '신궁 서쪽 문으로 들어가 1~3층의 닌자를 처치하세요. 닌자는 가끔 은신해 피하고 다음 공격이 2배예요.') + '</small></span>'
          + '<button type="button" ' + (done ? '' : 'disabled ') + 'onclick="TowerMine.archiAct(\'claim\')">열쇠 받기</button></div>';
    }
    if (n.kind === 'boatwright') {
      if (a.boat) return h + have + '<div class="mine-smith-say">⛵ 이미 1인용 배가 있어요. 뱃사공에게 배 타기를 부탁하세요.</div>';
      return h + have + '<div class="mine-smith-row"><span class="nm">⛵ 1인용 배<small>뱃길로 나가 일곱 섬에 내릴 수 있어요</small>' + costHtml(a.boatCosts) + '</span>'
          + '<button type="button" ' + (a.boatCan ? '' : 'data-lack="1" ') + 'onclick="TowerMine.archiAct(\'boat\')">만들기</button></div>';
    }
    return h + have + '<div class="mine-smith-menu"><button type="button" ' + (a.boat ? '' : 'disabled ') + 'onclick="TowerMine.archiAct(\'board\')">⛵ 배 타기<small>' + (a.boat ? '다도해 뱃길로' : '1인용 배가 있어야 해요') + '</small></button></div>'
        + '<div class="mine-smith-say">☀️ 다도해는 낮(30분)과 밤(30분)이 번갈아 와요. 낮에는 섬마다 햇볕에 초당 3~8%씩 체력이 깎이니(환생할수록 덜), 야자수 그늘(🌴)에서 쉬어 가세요.</div>';
  }
  function archiAct(action) {
    if (action === 'board') { closeSmith(); travel('/api/tower-mine-archi?action=board', '배를 타지 못했어요.'); return; }
    gearAct('/api/tower-mine-archi?action=' + action);
  }
  /** 다도해 낮/밤 -- 서버와 같은 식(리셋 기준 시각부터 dayCycleMs마다 앞 dayMs가 낮). */
  function archiDay(t) { return !map || !map.dayCycleMs || ((t - map.dayAnchor) % map.dayCycleMs + map.dayCycleMs) % map.dayCycleMs < map.dayMs; }
  function archiLeftMin(t) { var p = ((t - map.dayAnchor) % map.dayCycleMs + map.dayCycleMs) % map.dayCycleMs; return Math.max(1, Math.ceil(((p < map.dayMs ? map.dayMs : map.dayCycleMs) - p) / 60000)); }
  function openWorld() {
    if (!opened || !map) return;
    worldVB = null; // [2026-10-28] 열 때마다 지금 있는 곳을 가운데로
    openSmith({ name: '전체 지도', line: '', kind: 'world' }, 'world');
  }
  // [2026-10-24] 왕도 -- 성당 신부(체력 회복 / 부활 지점), 텔레포트 게이트(금광석을 내고 다른 지역으로)
  // [2026-10-28] 환생 -- 99레벨 경험치 가득이면 신부 창의 "환생" 버튼이 켜지고, 누르면 효과 설명 + 확인(priestConfirm) 뒤에 서버로
  var priestConfirm = false, RB_MAX = 6, RB_BASE = 5; // [2026-11-08] 5환생 다음은 진환생1 하나(서버 MINE_REBIRTH_BASE/MAX)
  function rbMul(base, n) { var v = Math.pow(base, n); return v === Math.round(v) ? String(v) : String(Math.round(v * 100) / 100); }
  // [2026-10-29] 왕도 원소술사 -- 세공된 보석 하나로 무기에 물/번개/불/땅 속성(ENCH_HITS번 공격, 상성 x1.5)
  var ENCH_HITS = 1000, ENCH_HITS_ELEM = 500; // 성당 신부 1000번, [2026-10-29] 원소술사 500번
  var ELEM_SHOP = [['water', 3, 48, '불'], ['thunder', 2, 49, '땅·바람'], ['fire', 6, 46, '물·얼음'], ['earth', 1, 45, '번개']];
  function elemHtml(n) {
    var e = st.ench || {}, have = function (g) { var q = 0; (st.ores || []).forEach(function (o) { if (o.grade === g) q = o.qty; }); return q; };
    return npcHead('🔮 ' + (n ? n.name : '원소술사'), false) + talkBlock(n, n ? n.line : '')
      + '<div class="mine-smith-say">지금 무기: ' + (e.left > 0 && e.el ? ELEM_ICON[e.el] + ' ' + ELEM_NAME[e.el] + ' 속성 (' + e.left + '번 남음)' : '속성 없음') + ' · 새로 부여하면 이전 속성은 사라져요.</div>'
      + ELEM_SHOP.map(function (s) {
          return '<div class="mine-smith-row"><img src="' + oreIcon(s[2]) + '" alt=""><span class="nm">' + ELEM_ICON[s[1]] + ' ' + ELEM_NAME[s[1]] + ' 속성<small>' + ORE_NAME[s[2]] + ' 1개 (보유 ' + have(s[2]) + ') · ' + ENCH_HITS_ELEM + '번 · ' + s[3] + ' 몬스터 x1.5</small></span>'
            + '<button type="button" ' + (have(s[2]) ? '' : 'data-lack="1" ') + 'onclick="TowerMine.priestAct(\'ench_' + s[0] + '\')">부여</button></div>';
        }).join('')
      + '<div class="mine-smith-row"><span class="nm">⚪ 무속성으로 되돌리기<small>무기에 깃든 속성을 없애요 (무료)</small></span>' // [2026-10-29]
      + '<button type="button" ' + (e.left > 0 && e.el ? '' : 'data-lack="1" ') + 'onclick="TowerMine.priestAct(\'ench_none\')">되돌리기</button></div>';
  }
  function priestHtml(n) {
    var city = !!(st && st.respawnCity), c = (st && st.combat) || {}, rb = c.rebirth | 0, ready = !!c.rebirthReady;
    var line = ready ? '때가 되었군요. 수많은 고비를 넘은 영혼이여, 원한다면 여신의 이름으로 새 날개를 달아 다시 태어나게 해 드리지요.' : (n ? n.line : '');
    var rbSub = rb >= RB_MAX ? rbStage(RB_MAX) + ' 완료' : ready ? (rb >= RB_BASE ? '지금 진환생할 수 있어요!' : '지금 환생할 수 있어요!') : 'Lv.99 + 경험치 가득일 때';
    var html = npcHead('⛪ ' + (n ? n.name : '성당 신부'), false)
        + talkBlock(n, line)
        + '<div class="mine-smith-menu list"><button type="button" onclick="TowerMine.priestAct(\'heal\')">✨ 치료<small>체력 회복 (무료)</small></button>'
        + (map && map.id === 'city' ? '<button type="button" onclick="TowerMine.priestAct(\'ench_light\')">☀️ 성속성 부여<small>미스릴괴 1개 · ' + ENCH_HITS + '번 · 어둠 x1.5</small></button>' : '') // [2026-10-29]
        + (city ? '<button type="button" onclick="TowerMine.priestAct(\'reset\')">🏠 부활 지점 해제<small>지금: 왕도 광장</small></button>'
                : '<button type="button" onclick="TowerMine.priestAct(\'save\')">🙏 기도<small>쓰러지면 왕도 광장에서 깨어나기</small></button>')
        + '<button type="button" ' + (ready ? 'class="rb-on" ' : 'disabled ') + 'onclick="TowerMine.priestAct(\'rebirthAsk\')">🕊️ ' + (rb >= RB_BASE ? '진환생' : '환생') + (rb ? ' (지금 ' + rbStage(rb) + ')' : '') + '<small>' + rbSub + '</small></button></div>';
    // [2026-10-28] 환생 -- 1단계: 효과 설명, 2단계: 최종 확인(되돌릴 수 없음). "환생 시 멘트 조정" -- 좋아지는 것 / 어려워지는 것 / 그대로인 것으로 나눠서
    if (priestConfirm && ready) {
      var nrb = rb + 1;
      if (priestConfirm === 1) html += '<div class="mine-rb-box">🕊️ <b>' + rbStage(nrb) + '</b> -- 여신의 빛 속에서 지금까지의 수련을 내려놓고 새 날개로 다시 태어나요.<br>'
          + '<b>✨ 강해지는 것</b><br>'
          + '· 최대 체력 <b>+' + (nrb * 50) + '%</b> · 공격력 <b>+' + (nrb * 50) + '%</b><br>'
          + '· 채굴·전투로 얻는 구리~미스릴 광석 <b>x' + (1 + nrb) + '</b><br>'
          + '· 동굴 밝기 <b>+' + nrb + '</b> (투구 단계처럼 더 환하게)<br>' // [2026-10-29]
          + '· 방한·햇빛 저항 <b>+' + nrb + '</b>' + '<br>' // [2026-10-29] 효과도 문구대로: 방한·햇빛 단계 +회차
          + (nrb > RB_BASE ? '' : '· 등에 <b>환생 날개</b>가 돋아나요(단계가 오를수록 커져요, 최대 ' + RB_BASE + '단계)<br>')
          + '<b>⏳ 다시 쌓는 것</b><br>'
          + '· 레벨이 <b>Lv.1(' + rbName(nrb) + ')</b>로 돌아가고 경험치는 0이 돼요<br>'
          + '· 레벨마다 필요한 경험치 <b>x' + rbMul(2, nrb) + '</b> (환생할 때마다 2배씩)<br>'
          + (nrb > RB_BASE ? '· 왕도 상인의 <b>행운의 주문서</b>(경험치 3배)를 더 이상 쓸 수 없어요<br>' : '') // [2026-11-08] 진환생
          + '<b>🎒 그대로인 것</b><br>'
          + '· 광석·보석·장비·치장은 모두 그대로예요.'
          + '<div class="mine-smith-menu"><button type="button" class="rb-on" onclick="TowerMine.priestAct(\'rebirthFinal\')">다음</button>'
          + '<button type="button" onclick="TowerMine.priestAct(\'rebirthNo\')">그만두기</button></div></div>';
      else html += '<div class="mine-rb-box">⚠️ <b>최종 확인</b><br>정말 <b>' + rbStage(nrb) + '</b>로 다시 태어날까요?<br>지금 <b>Lv.' + c.lv + '</b>가 <b>Lv.1</b>로 돌아가며, <b>되돌릴 수 없어요.</b>'
          + '<div class="mine-smith-menu"><button type="button" class="rb-on" onclick="TowerMine.priestAct(\'rebirth\')">🕊️ 최종 확인 -- ' + (nrb > RB_BASE ? '진환생' : '환생') + '하기</button>'
          + '<button type="button" onclick="TowerMine.priestAct(\'rebirthNo\')">그만두기</button></div></div>';
    }
    return html;
  }
  function priestAct(action) {
    if (action === 'rebirthAsk' || action === 'rebirthNo' || action === 'rebirthFinal') { priestConfirm = action === 'rebirthAsk' ? 1 : action === 'rebirthFinal' ? 2 : 0; if (smithOpen) renderSmith(smithNpc); return; } // [2026-10-28] 설명 -> 최종 확인
    if (busy) return;
    busy = true;
    priestConfirm = false;
    api('/api/tower-mine-priest?action=' + action).then(function (d) {
      busy = false;
      if (d.combat) setCombat(d.combat);
      if (d.respawnCity != null) st.respawnCity = d.respawnCity;
      if (d.ench) { st.ench = d.ench; flashT0 = performance.now(); updEnch(); api('/api/tower-mine').then(function (s) { if (s && s.ores) st.ores = s.ores; if (smithOpen) renderSmith(smithNpc); }); } // [2026-10-29] 속성 부여(쓴 재료 반영)
      if (action === 'heal') flashT0 = performance.now();
      if (d.rebirthDone) { // [2026-10-28] 환생 -- 날개는 st.combat.rebirth로 그린다(외형도 같이 갱신)
        if (d.look) st.look = d.look;
        flashT0 = performance.now();
        banner = { text: '🕊️ ' + rbStage(d.rebirth) + '! Lv.1(' + rbName(d.rebirth) + ')', t0: performance.now() };
      }
      msg(d.message || d.error || '완료', d.rebirthDone ? 5000 : 3000);
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function teleHtml() {
    var gold = 0;
    (st.ores || []).forEach(function (o) { if (o.grade === 4) gold = o.qty; });
    var need = map.teleGold != null ? map.teleGold : 5, can = gold >= need ? '' : 'data-lack="1" ';
    return npcHead('🌀 텔레포트 게이트', false)
        + '<div class="mine-smith-say">' + (need > 0 ? '금광석 ' + need + '개를 바치면 마법진이 빛나며 먼 곳으로 데려다줘요. (보유 ' + gold + '개)' : '마법진에 올라서면 먼 곳으로 데려다줘요. (무료)') + '</div><div class="mine-smith-menu" style="flex-wrap:wrap">'
        + (map.teleTo || []).map(function (d) { return '<button type="button" ' + can + 'onclick="TowerMine.teleport(\'' + d.id + '\')">🌀 ' + d.name + '</button>'; }).join('') + '</div>';
  }
  // [2026-10-25] 이동 안내원 -- 안내원이 있는 마을끼리 무료로(왕도는 관리자만 보임)
  function guideHtml(n) {
    var admin = !!(st && st.adminMaps);
    // [2026-10-26] 설산 마을, [2026-10-27] 왕도는 한 번 걸어서 가 본 뒤에만
    var dests = (map.guideTo || []).filter(function (d) { return (!d.dev || admin) && (!d.needVisit || (st && (d.id === 'city' ? st.visitedCity : d.id === 'archi' ? st.visitedArchi : st.visitedSnowtown))); });
    // [2026-10-25] 마을(평원 마을/왕도)끼리만 -- 왕도 길이 아직 안 열린 유저에겐 갈 곳이 없다고 안내
    if (!dests.length) return npcHead('🧭 ' + (n ? n.name : '이동 안내원'), false)
        + talkBlock(n, '지금은 모셔다 드릴 다른 마을이 없어요. 새 마을로 가는 길이 열리면 그때 꼭 찾아 주세요!');
    return npcHead('🧭 ' + (n ? n.name : '이동 안내원'), false)
        + talkBlock(n, n ? n.line : '')
        + '<div class="mine-smith-menu" style="flex-wrap:wrap">' + dests.map(function (d) {
          return '<button type="button" style="flex:1 1 40%" onclick="TowerMine.guideGo(\'' + d.id + '\')">🧭 ' + d.name + '<small>무료</small></button>';
        }).join('') + '</div>';
  }
  function guideGo(dest) {
    closeSmith();
    if (busy || !map) return;
    travel('/api/tower-mine-guide?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&dest=' + encodeURIComponent(dest) + verQ(), '이동하지 못했어요.');
  }
  // [2026-10-28] "광산 진입 시 다운스케일링과 신규 맵 추가 간략 공지 한 번" -- 서버가 아직 안 본 소식을 주면 들어올 때 한 번 띄우고, 확인을 누르면 서버에 기록(다시 안 뜸)
  var noticeShown = false, noticeData = null;
  function noticeHtml() {
    var d = noticeData || {};
    return npcHead('📢 광산 소식', false)
        + '<div class="mine-ask" style="font-size:13px;font-weight:600;line-height:1.7">' + escAttr(d.text || '').replace(/\n/g, '<br>') + '</div>'
        + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.noticeOk()">확인</button></div>';
  }
  function noticeOk() {
    var d = noticeData;
    closeSmith();
    if (d && d.id) api('/api/tower-mine-notice-ack?id=' + d.id).catch(function () { });
  }
  // [2026-10-28] 요일 동굴 문지기 -- 오늘의 요일석 20개로 다른 요일 동굴(오늘 하루 그대로), 같은 요일 다시 들어가기/오늘 요일로 되돌리기는 무료
  var DAY_KO = ['월', '화', '수', '목', '금', '토', '일'];
  function dayGateHtml(n) {
    var today = st.todayDay != null ? st.todayDay : 0, cur = st.dungeonDay != null ? st.dungeonDay : today, have = oreHave(11 + today);
    var btns = DAY_KO.map(function (k, d) {
      var free = d === today || d === cur, can = free || have >= 20;
      var sub = d === today ? '오늘 · 무료' : d === cur ? '열어 둔 동굴 · 무료' : ORE_NAME[11 + today] + ' 20개';
      return '<button type="button" style="flex:1 1 30%" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.dayGo(' + d + ')">🕳️ ' + k + '요일<small>' + sub + '</small></button>';
    }).join('');
    return npcHead('🕳️ ' + (n ? n.name : '요일 동굴 문지기'), false) + talkBlock(n, n ? n.line : '')
        + '<div class="mine-smith-dur">' + ORE_NAME[11 + today] + ' 보유 <b>' + have + '</b>개 · 지금 열린 동굴 <b>' + DAY_KO[cur] + '요일</b></div>'
        + '<div class="mine-smith-menu" style="flex-wrap:wrap">' + btns + '</div>';
  }
  function dayGo(d) {
    closeSmith();
    if (busy) return;
    travel('/api/tower-mine-daygate?day=' + d, '들어가지 못했어요.');
  }
  // [2026-10-25] "마을 이동을 누르면 브라우저 알림창이 뜬다" -- 게임 화면 안의 확인 창(NPC 창 모양)
  var askCfg = null;
  function ask(title, text, okLabel, onOk) {
    askCfg = { title: title, text: text, ok: okLabel, fn: onOk };
    openSmith({ name: title, line: '', kind: 'ask' }, 'ask');
  }
  function askHtml() {
    var c = askCfg || {};
    return npcHead(c.title || '확인', false) + '<div class="mine-ask">' + (c.text || '') + '</div>'
        + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.askYes()">' + (c.ok || '확인') + '</button>'
        + '<button type="button" onclick="TowerMine.closeSmith()">취소</button></div>';
  }
  function askYes() {
    var c = askCfg;
    askCfg = null;
    closeSmith();
    if (c && c.fn) c.fn();
  }
  // [2026-10-26] 방한 외투 -- 설산 마을 털옷 장인, 설원 정박장 암상인(매시 정각~15분, 대화 중에도 15분이 되면 사라짐)
  var coatData = null, blackTimer = 0;
  function blackOpen() { return new Date(nowSrv()).getUTCMinutes() < 15; } // 분은 KST와 UTC가 같다
  function openCoat(n) {
    coatData = null;
    openSmith(n, 'coat');
    api('/api/tower-mine-coat?action=list').then(function (d) {
      coatData = d;
      if (smithOpen && smithView === 'coat') renderSmith(smithNpc);
    }).catch(function () { });
    clearInterval(blackTimer);
    if (n.kind !== 'blackmarket') return;
    blackTimer = setInterval(function () {
      if (!smithOpen || smithView !== 'coat' || !smithNpc || smithNpc.kind !== 'blackmarket') { clearInterval(blackTimer); return; }
      if (!blackOpen()) {
        clearInterval(blackTimer);
        closeSmith();
        flashT0 = performance.now();
        msg('🕶️ 눈 깜짝할 새 암상인이 사라졌어요... 다음 정각에 다시 나타나요.', 3600);
        return;
      }
      var d = new Date(nowSrv()), left = (15 - d.getUTCMinutes()) * 60 - d.getUTCSeconds(), el2 = $('mineBlackLeft');
      if (el2) el2.textContent = '⏳ 암상인이 사라지기까지 ' + Math.floor(left / 60) + ':' + pad2(left % 60);
    }, 1000);
  }
  // [2026-10-28] 털옷 장인 -- "전부 퀘스트 형태로 순차 획득, 이전 것은 못 쓰는 업그레이드 형태" -- 외투 3단계 / 장갑 3단계 의뢰 사슬
  function coatHtml(n) {
    var bm = n && n.kind === 'blackmarket', d = coatData;
    var h = npcHead((bm ? '🕶️ ' : '🧥 ') + (n ? n.name : '털옷 장인'), false) + talkBlock(n, n ? n.line : '');
    if (bm) return h + '<div class="mine-smith-say">🕶️ 이제 털옷은 안 판다네... 설산 마을 털옷 장인의 의뢰로만 만들 수 있지.</div>';
    if (!d) return h + '<div class="mine-smith-say">물건을 꺼내는 중...</div>';
    var CN = ['방한 외투', '두꺼운 방한 외투', '방한 갑옷'], GN = ['방한 장갑', '두꺼운 방한 장갑', '방한 건틀릿'];
    h += '<div class="mine-smith-dur">🧥 ' + (d.coatTier ? CN[d.coatTier - 1] + (d.coatWorn ? ' (입음)' : ' (벗음)') : '외투 없음')
        + ' · 🧤 ' + (d.gloveTier ? GN[d.gloveTier - 1] + (d.glovesOn ? ' (낌)' : ' (벗음)') : '장갑 없음') + ' · 한기 방어 ' + (d.coldDef || 0) + '</div>';
    var block = function (title, steps, code, tier, worn) {
      var b = '<div class="mine-smith-sub"><b>📜 ' + title + '</b></div>';
      (steps || []).forEach(function (s) {
        if (s.done) b += '<div class="mine-smith-row"><span class="nm">✔ ' + s.tier + '단계 ' + s.name + '<small>' + s.effect + (s.tier === tier ? ' · 지금 가진 것' : ' · 다음 단계로 바뀌었어요') + '</small></span>'
            + (s.tier === tier ? '<button type="button" onclick="TowerMine.coatAct(\'wear\',\'' + code + '\')">' + (worn ? '해제' : '장착') + '</button>' : '') + '</div>';
        else if (s.next) b += '<div class="mine-smith-row"><span class="nm">' + s.tier + '단계 ' + s.name + '<small>' + s.effect + '</small>' + costHtml(s.costs) + '</span>'
            + '<button type="button" ' + (s.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.coatAct(\'up\',\'' + code + '\')">건네기</button></div>';
        else b += '<div class="mine-smith-row" style="opacity:.5"><span class="nm">🔒 ' + s.tier + '단계 ' + s.name + '<small>' + s.effect + ' · 앞 단계를 먼저</small></span></div>';
      });
      return b;
    };
    return h + block('털옷 의뢰 (외투 -> 두꺼운 외투 -> 갑옷)', d.coatSteps, 'coat', d.coatTier, d.coatWorn)
        + block('장갑 의뢰 (장갑 -> 두꺼운 장갑 -> 건틀릿)', d.gloveSteps, 'gloves', d.gloveTier, d.glovesOn)
        + '<div class="mine-smith-say">한기 방어 = 외투 단계 + 장갑 단계. 외투를 입으면 몸이 둔해져 몬스터에게 받는 피해 2배(갑옷은 1.5배). 사막·남쪽 섬·다도해에선 더워요.</div>';
  }
  function coatAct(action, code) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-coat?action=' + action + '&code=' + encodeURIComponent(code)).then(function (d) {
      busy = false;
      coatData = d;
      if (d.ores) st.ores = d.ores;
      if (d.coatDef != null) st.coatDef = d.coatDef;
      if (d.ok && action === 'buy') flashT0 = performance.now();
      msg(d.message || d.error || '완료', 3200);
      if (smithOpen && smithView === 'coat') renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function teleport(dest) {
    var n = smithNpc;
    closeSmith();
    if (!n || busy) return;
    travel('/api/tower-mine-warp?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&px=' + n.px + '&py=' + n.py + '&dest=' + encodeURIComponent(dest) + verQ(), '텔레포트하지 못했어요.');
  }
  // [2026-10-24] NPC 초상화 -- "말 걸면 마비노기처럼 초상화, 애니메이션틱하게": 40x48 도트 흉상을 크게(픽셀 그대로),
  //  처음 열 때 옆에서 미끄러져 들어오고(CSS), 숨쉬듯 들썩이며(CSS), 가끔 눈을 깜빡이고, 대사가 한 글자씩 나오는 동안 입을 움직인다.
  var PORTRAIT = {
    smith: { skin: '#e8b48a', hair: '#3b2a1a', cloth: '#7a4a1f', clothD: '#5a3a1c' },
    captain: { skin: '#f2c194', hair: '#e8e8e8', cloth: '#f4f4f0', clothD: '#c9ccd6' },
    ninjaquest: { skin: '#e8b48a', hair: '#1a1a22', cloth: '#a8202a', clothD: '#6a1018' }, // [2026-10-28] 다도해
    boatwright: { skin: '#e0a878', hair: '#6a4a22', cloth: '#8a6a3a', clothD: '#6a4a22' },
    ferry: { skin: '#e8b48a', hair: '#2a2a2a', cloth: '#2a5aa8', clothD: '#1a3a78' },
    boutique: { skin: '#f2c194', hair: '#2a1d10', cloth: '#7b3fa0', clothD: '#5a2a78' },
    priest: { skin: '#f0c8a0', hair: '#b8bec8', cloth: '#24212b', clothD: '#141218' },
    guide: { skin: '#f2c194', hair: '#7a4a26', cloth: '#2f8a5a', clothD: '#1f6342' },
    blackmarket: { skin: '#c8a080', hair: '#1a1322', cloth: '#2a1f33', clothD: '#1a1322' }, // [2026-10-26]
    furrier: { skin: '#f2c194', hair: '#5a3a22', cloth: '#8a5a32', clothD: '#6a4222' },
    daykeeper: { skin: '#e8c09a', hair: '#e8e8e8', cloth: '#5a5560', clothD: '#46424c' }, // [2026-10-28] 요일 동굴 문지기
    wallguard: { skin: '#d8a070', hair: '#3a2a1a', cloth: '#8a6a3a', clothD: '#5a4422' }, // [2026-11-04] 사막 경비병
    swordmaster: { skin: '#e8b48a', hair: '#1a1a22', cloth: '#7a2a2a', clothD: '#4a1818' }, // [2026-11-04] 검기 사범
    basecamp: { skin: '#e8b48a', hair: '#6a4a2a', cloth: '#3a6a9a', clothD: '#2a4a6a' }, // [2026-11-04] 베이스캠프 대장
    forestguard: { skin: '#d8a47a', hair: '#3a2a1a', cloth: '#4a5a2a', clothD: '#2a3a18' }, // [2026-11-05] 마물의 숲 입구 파수꾼
    citymerchant: { skin: '#e8b48a', hair: '#5a3a1a', cloth: '#2a7a4a', clothD: '#1a5030' }, // [2026-11-05] 왕도 상인
    mercenary: { skin: '#d8a070', hair: '#2a1a10', cloth: '#6a4a2a', clothD: '#4a3018' }, // [2026-11-05] 용병소 대장
    enhancer: { skin: '#c8885a', hair: '#3a3a3a', cloth: '#5a3a2a', clothD: '#2a1a12' }, // [2026-11-06] 무기 강화사(가죽 앞치마)
    whirlmaster: { skin: '#e8b48a', hair: '#d8d8e0', cloth: '#2a6a8a', clothD: '#1a4a62' }, // [2026-11-07] 돌개 사범
    ultmaster: { skin: '#e0a878', hair: '#1a1a22', cloth: '#3a2a5a', clothD: '#24183a' } // [2026-11-07] 필살 사범
  };
  var portraitCache = {};
  /** frame: 0 보통, 1 눈 감음, 2 입 벌림. */
  function portrait(kind, frame) {
    var key = kind + frame;
    if (portraitCache[key]) return portraitCache[key];
    var q = PORTRAIT[kind] || PORTRAIT.smith, c = mk(40, 48), g = c.getContext('2d');
    px(g, q.clothD, 3, 36, 34, 12); px(g, q.cloth, 5, 34, 30, 14);              // 어깨/옷
    px(g, shade(q.skin, -0.14), 16, 28, 8, 7);                                  // 목
    for (var y = 8; y < 32; y++) {                                              // 얼굴
      var hw = Math.round(11 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 19) / 13, 2))));
      px(g, q.skin, 20 - hw, y, hw * 2, 1);
    }
    px(g, '#f29b8a', 11, 22, 3, 2); px(g, '#f29b8a', 26, 22, 3, 2);           // 볼
    if (frame === 1) { px(g, '#1a1a1a', 13, 19, 5, 1); px(g, '#1a1a1a', 22, 19, 5, 1); }
    else {
      px(g, '#ffffff', 13, 17, 5, 4); px(g, '#ffffff', 22, 17, 5, 4);
      px(g, '#2a1d10', 15, 17, 3, 4); px(g, '#2a1d10', 22, 17, 3, 4);
      px(g, '#ffffff', 16, 17); px(g, '#ffffff', 23, 17);
      px(g, '#1a1a1a', 13, 16, 5, 1); px(g, '#1a1a1a', 22, 16, 5, 1);
    }
    px(g, shade(q.hair, -0.25), 12, 14, 6, 1); px(g, shade(q.hair, -0.25), 22, 14, 6, 1); // 눈썹
    px(g, shade(q.skin, -0.2), 19, 21, 2, 3);                                   // 코
    if (frame === 2) { px(g, '#7a2a2a', 17, 26, 6, 3); px(g, '#d9536f', 18, 28, 4, 1); } else px(g, '#9a4a3a', 17, 27, 6, 1);
    if (kind === 'smith') {
      px(g, q.hair, 9, 6, 22, 6); px(g, '#b5251f', 9, 10, 22, 2);               // 머리 + 머리띠
      px(g, q.hair, 9, 24, 6, 9); px(g, q.hair, 25, 24, 6, 9); px(g, q.hair, 13, 30, 14, 4); // 수염
      px(g, '#4a2f18', 11, 34, 3, 14); px(g, '#4a2f18', 26, 34, 3, 14); px(g, '#c49a45', 12, 40, 1, 1); // 앞치마 끈
    } else if (kind === 'captain') {
      px(g, '#22305e', 8, 3, 24, 8); px(g, '#1a234a', 5, 10, 30, 2); px(g, '#d9b44a', 17, 4, 6, 3); // 선장 모자 + 금 휘장
      px(g, q.hair, 9, 25, 6, 8); px(g, q.hair, 25, 25, 6, 8); px(g, q.hair, 13, 29, 14, 5);     // 흰 수염
      px(g, '#d9b44a', 5, 34, 6, 2); px(g, '#d9b44a', 29, 34, 6, 2); px(g, '#d9b44a', 19, 38, 2, 2); px(g, '#d9b44a', 19, 43, 2, 2);
    } else if (kind === 'boutique') {
      px(g, q.hair, 9, 8, 22, 4);
      px(g, '#1a1a1a', 11, 0, 18, 8); px(g, '#d4af37', 11, 5, 18, 2); px(g, '#1a1a1a', 6, 8, 28, 2); // 실크햇
      px(g, '#3b2a1a', 13, 24, 14, 2); px(g, '#3b2a1a', 11, 22, 2, 2); px(g, '#3b2a1a', 27, 22, 2, 2); // 말아 올린 콧수염
      px(g, '#d4af37', 21, 15, 7, 1); px(g, '#d4af37', 21, 21, 7, 1); px(g, '#d4af37', 21, 15, 1, 7); px(g, '#d4af37', 27, 15, 1, 7); px(g, '#d4af37', 28, 22, 1, 8); // 외알 안경
      px(g, '#f4f0e6', 15, 34, 10, 3); px(g, '#d4104f', 16, 35, 8, 3); px(g, '#ffd0e0', 19, 36, 2, 1); // 나비넥타이
    } else if (kind === 'blackmarket') { // [2026-10-26] 두건으로 눈 위까지 덮고 그늘진 얼굴, 금니
      px(g, q.cloth, 6, 2, 28, 12); px(g, q.cloth, 4, 10, 6, 22); px(g, q.cloth, 30, 10, 6, 22); px(g, shade(q.cloth, 0.2), 10, 2, 20, 2);
      px(g, 'rgba(10,6,14,0.55)', 10, 12, 20, 9); px(g, '#ffd23f', 15, 18, 3, 2); px(g, '#ffd23f', 23, 18, 3, 2);
      px(g, '#ffd23f', 21, 27, 2, 1); px(g, '#c9a04a', 18, 38, 4, 4);
    } else if (kind === 'furrier') { // [2026-10-26] 흰 털모자 + 털 깃, 덥수룩한 수염
      px(g, '#e8dcc8', 6, 2, 28, 10); px(g, '#c8b8a0', 8, 9, 24, 2); px(g, '#e8dcc8', 4, 33, 32, 4);
      px(g, q.hair, 11, 24, 18, 6); px(g, q.hair, 9, 20, 3, 6); px(g, q.hair, 28, 20, 3, 6);
    } else if (kind === 'guide') {
      px(g, q.hair, 9, 7, 22, 5); px(g, q.hair, 8, 10, 3, 8); px(g, q.hair, 29, 10, 3, 8);   // 갈색 머리
      px(g, '#c0392b', 8, 1, 24, 7); px(g, '#8e2a20', 5, 7, 30, 2); px(g, '#ffd23f', 25, 2, 3, 3); // 붉은 여행 모자 + 노란 깃털
      px(g, '#e8c84a', 5, 38, 30, 2); px(g, '#6b4220', 26, 40, 5, 5);           // 노란 띠 + 가죽 주머니
      px(g, '#e8f0ff', 9, 34, 22, 2);                                            // 흰 깃
    } else if (kind === 'priest') {
      px(g, q.hair, 9, 5, 22, 6); px(g, q.hair, 8, 9, 3, 9); px(g, q.hair, 29, 9, 3, 9);  // 은발
      px(g, '#f4f0e6', 15, 33, 10, 3);                                           // 흰 깃
      px(g, '#d9b44a', 19, 37, 2, 9); px(g, '#d9b44a', 16, 39, 8, 2);           // 금 십자가
    }
    outline(c, '#140d0a');
    portraitCache[key] = c.toDataURL();
    return portraitCache[key];
  }
  function escAttr(t) { return String(t || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  /** NPC 대화 칸(초상화 + 이름 + 한 글자씩 나오는 대사). 화면에 붙인 뒤 startTalk()가 움직인다. */
  // [2026-11-06] "NPC 대사 중 엔터 없이 쭉 말하는 건 두 번 대화로 나눠서" -- 긴 대사는 문장(없으면 쉼표/띄어쓰기) 경계 중 가운데에서 둘로.
  //  NPC 창은 첫 부분 뒤 "▼"(누르면 다음), 지도 말풍선은 보이는 시간의 앞/뒤 반씩.
  var talkSplitCache = {};
  function talkSplit(text, min) {
    var t = String(text == null ? '' : text), ck = min + '|' + t;
    if (talkSplitCache[ck]) return talkSplitCache[ck];
    var n = t.length, best = -1, bd = 1e9, re = /([.!?…~]+|,)\s+/g, m;
    if (n > min) {
      while ((m = re.exec(t))) { var i = m.index + m[0].length; if (i > 3 && i < n - 3) { var d = Math.abs(i - n / 2); if (d < bd) { bd = d; best = i; } } }
      if (best < 0 || bd > n * 0.3) for (var k = 4; k < n - 4; k++) if (t.charAt(k) === ' ') { var d2 = Math.abs(k + 1 - n / 2); if (d2 < bd) { bd = d2; best = k + 1; } }
    }
    var out = best > 0 ? [t.slice(0, best).trim(), t.slice(best).trim()] : [t];
    return (talkSplitCache[ck] = out);
  }
  var talkPage = {}, talkCurKey = '';
  function talkNext() { talkPage[talkCurKey] = (talkPage[talkCurKey] | 0) + 1; if (smithOpen) renderSmith(smithNpc); }
  function talkBlock(n, text) {
    if (!n || !PORTRAIT[n.kind]) return '<div class="mine-smith-say">' + (text || '') + '</div>';
    var parts = talkSplit(text, 40), key = n.kind + '|' + text, pg = Math.min(parts.length - 1, talkPage[key] | 0), more = pg < parts.length - 1;
    talkCurKey = key;
    return '<div class="mine-npc-talk"><img class="mine-portrait" id="minePortrait" src="' + portrait(n.kind, 0) + '" alt="' + escAttr(n.name) + '">'
        + '<div class="mine-npc-bubble"' + (more ? ' style="cursor:pointer" onclick="TowerMine.talkNext()"' : '') + '><b>' + escAttr(n.name) + '</b><span id="mineNpcLine" data-text="' + escAttr(brk(parts[pg])) + '"></span>'
        + (more ? '<span class="talk-more" aria-label="다음">▼</span>' : '') + '</div></div>';
  }
  var talkTimer = 0, talkKey = '', talkShown = 0;
  function startTalk() {
    clearInterval(talkTimer);
    var sp = $('mineNpcLine'), img = $('minePortrait');
    if (!sp || !img || !smithNpc) return;
    var text = sp.getAttribute('data-text') || '', kind = smithNpc.kind, key = kind + '|' + text, tick = 0;
    if (key !== talkKey) { talkKey = key; talkShown = 0; img.classList.add('pop'); }
    sp.textContent = text.slice(0, talkShown);
    talkTimer = setInterval(function () {
      tick++;
      var sp2 = $('mineNpcLine'), im2 = $('minePortrait');
      if (!sp2 || !im2 || !smithOpen) { clearInterval(talkTimer); return; }
      if (talkShown < text.length) { talkShown = Math.min(text.length, talkShown + 1); sp2.textContent = text.slice(0, talkShown); }
      var talking = talkShown < text.length, blink = tick % 40 < 2;
      var fr = blink ? 1 : (talking && tick % 3 !== 0 ? (tick % 2 ? 2 : 0) : 0);
      if (im2._fr !== fr) { im2._fr = fr; im2.src = portrait(kind, fr); }
    }, 55);
  }
  /** [2026-10-28] 캐러셀 창(명품상점/대장간 구매/🎒 장비) -- 다시 그려도 넘겨 둔 자리와 세로 스크롤 그대로, 처음 열면 착용 카드가 보이게. */
  function carouselHtml(html) {
    var keep = {}, keepY = el.smith.scrollTop;
    Array.prototype.forEach.call(el.smith.querySelectorAll('.cos-car'), function (c) { keep[c.getAttribute('data-slot')] = c.scrollLeft; });
    el.smith.innerHTML = html;
    var place = function () { // 그림이 그려진 뒤(다음 프레임)에 넘긴다 -- 바로 넘기면 칸 맞춤이 첫 카드로 되돌린다
      Array.prototype.forEach.call(el.smith.querySelectorAll('.cos-car'), function (c) {
        var v = keep[c.getAttribute('data-slot')], worn = c.querySelector('.cos-card.worn');
        if (v != null) c.scrollLeft = v;
        else if (worn) c.scrollLeft = Math.max(0, worn.offsetLeft - c.offsetLeft - 8);
      });
      el.smith.scrollTop = keepY;
    };
    place();
    requestAnimationFrame(place);
  }
  function renderSmith(n) {
    renderSmithView(n);
    startTalk(); // [2026-10-24] NPC 초상화/대사
  }
  function renderSmithView(n) {
    if (el.smith) el.smith.classList.toggle('merc-wide', smithView === 'merc'); // [2026-11-06] "PC는 옆 화면이 넓으니 용병소는 옆으로 넓게"
    if (smithView === 'priest') {
      el.smith.innerHTML = priestHtml(n);
      return;
    }
    if (smithView === 'hermit') { el.smith.innerHTML = hermitHtml(n); return; } // [2026-10-30]
    if (smithView === 'bosscall') { el.smith.innerHTML = bossCallHtml(n); return; } // [2026-11-13]
    if (smithView === 'elem') { el.smith.innerHTML = elemHtml(n); return; } // [2026-10-29] 원소술사
    if (smithView === 'aura') { el.smith.innerHTML = auraNpcHtml(n); return; } // [2026-11-04] 검기 사범
    if (smithView === 'whirl') { el.smith.innerHTML = whirlNpcHtml(n); return; } // [2026-11-07] 돌개 사범
    if (smithView === 'ult') { el.smith.innerHTML = ultNpcHtml(n); return; } // [2026-11-07] 필살 사범
    if (smithView === 'tele') {
      el.smith.innerHTML = teleHtml();
      return;
    }
    if (smithView === 'guide') {
      el.smith.innerHTML = guideHtml(n);
      return;
    }
    if (smithView === 'coat') { // [2026-10-26] 방한 외투
      el.smith.innerHTML = coatHtml(n);
      return;
    }
    if (smithView === 'notice') { // [2026-10-28] 광산 소식
      el.smith.innerHTML = noticeHtml();
      return;
    }
    if (smithView === 'citymerchant') { // [2026-11-05] 왕도 상인 -- 행운의 주문서
      var lu = (st && st.luckUntil) || 0, left = lu - nowSrv(), cs = cityShop || {}, trb = ((st && st.combat && st.combat.rebirth) | 0) > RB_BASE; // [2026-11-08] 진환생은 못 씀
      el.smith.innerHTML = npcHead('🍀 ' + n.name, false) + talkBlock(n, n.line)
        + '<div class="mine-smith-row"><span class="nm">📜 행운의 주문서 (10분)<small>경험치 3배 · 드랍률 2배' + (left > 0 ? ' · 남은 시간 ' + Math.ceil(left / 60000) + '분 (사면 10분 더)' : '') + '</small><small>' + (trb ? '진환생하면 쓸 수 없어요' : cs.cost ? '필요: ' + cs.cost : '') + '</small></span>'
        + '<button type="button" ' + (trb ? 'disabled ' : cs.can === false ? 'data-lack="1" ' : '') + 'onclick="TowerMine.cityBuy()">사기</button></div>';
      return;
    }
    if (smithView === 'mercmgr') { el.smith.innerHTML = mercMgrHtml(); return; } // [2026-11-06] 용병 관리
    if (smithView === 'enh') { el.smith.innerHTML = npcHead('🔨 ' + n.name, false) + talkBlock(n, n.line) + wenhHtml(true); return; } // [2026-11-06] 무기 강화사
    if (smithView === 'merc') { // [2026-11-05] 왕도 용병소
      var ms2 = mercShop || {}, mi2 = st.merc || {}, mh = npcHead('🛡️ ' + n.name, false) + talkBlock(n, n.line);
      if (st.merc) mh += '<div class="mine-smith-say">함께하는 용병: ' + mercSumHtml(mi2) + '</div>';
      // [2026-11-06] "처음 계약 때 동료 이미지, 속성, 레벨, 이름, 직업 정도 캐러셀로" -- 직업마다 후보 한 명(서버가 뽑아 둠), 계약하면 첫날 급여
      mh += '<div class="mine-smith-say"><small>' + (ms2.curLv > 0 ? '지금 용병(Lv.' + ms2.curLv + ')보다 2~15레벨 낮은 동료들이에요 · 계약하면 지금 용병은 떠나요' : '처음 계약은 Lv.5~30') // [2026-11-06] 교체 후보 / 급여 구간
        + '<br>' + (ms2.offers && ms2.offers.some(function (o) { return o.free; }) ? '교체는 무료예요 · 급여는 처음 계약할 때 한 번만 냈어요' : '급여는 처음 계약할 때 한 번만 내요(동괴 / 은괴 / 금괴 / 미스릴괴 중 하나, 가끔 세공된 보석) · 그 뒤로는 교체도 무료') + '</small></div>'; // [2026-11-11] 날마다 내던 급여 없앰
      if (ms2.offers) {
        // [2026-11-06] "직업별로 5명씩, 3줄로" -- 전사 / 궁수 / 마법사 줄(모바일은 줄마다 옆으로 넘기기, PC는 넓은 창에 5명이 한 줄로)
        var offCard = function (o) {
          return '<div class="cos-card merc-offer' + (o.owned ? ' worn' : '') + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(o.t, o.ni, o.nm)
              + '<div class="cos-nm">' + o.nm + '</div><small>' + MERC_ICON[o.t] + ' ' + o.job + ' · Lv.' + o.lv + '</small>'
              + '<small class="mo-el mo-el' + o.el + '">' + (EL_ICON[o.el] || '') + ' ' + o.elName + ' 특화</small>'
              + '<small class="mo-sk">' + o.skill + '</small>'
              + (o.owned ? '<span class="mine-worn">계약함</span>' : '<small>' + (o.free ? '🆓 무료' : '첫 급여 ' + o.sal) + '</small><button type="button" ' + (o.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.mercHire(' + o.i + ')">계약</button>') + '</div>';
        };
        mh += mercViewBtns();
        [0, 1, 2].forEach(function (jt) {
          var row = ms2.offers.filter(function (o) { return (o.t | 0) === jt; });
          if (row.length) mh += '<div class="merc-job-h">' + MERC_ICON[jt] + ' ' + ['전사', '궁수', '마법사'][jt] + ' <small>' + row.length + '명</small></div><div class="cos-car merc-offers">' + row.map(offCard).join('') + '</div>';
        });
        var curM = (st.mercs || []).filter(function (x) { return x.owned; })[0];
        if (curM) mh += '<div class="mine-smith-say"><small>다른 용병과 계약하면 지금 함께하는 <b>' + curM.nm + '</b>은(는) 떠나고 새 용병으로 바뀌어요.</small></div>';
        if (mercAskI != null) { // [2026-11-06] "계약할까요 멘트를 인게임 팝업으로"
          var ao = ms2.offers.filter(function (o) { return o.i === mercAskI; })[0];
          if (ao) mh += '<div class="eq-pop" onclick="if (event.target === this) TowerMine.mercHire(null)"><div class="eq-pop-box merc-ask"><div class="eq-pop-h">🛡️ 용병 계약<button type="button" onclick="TowerMine.mercHire(null)">✖</button></div>'
              + '<div class="merc-ask-body' + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(ao.t, ao.ni, ao.nm) + '<div><b>' + ao.nm + '</b><br>' + MERC_ICON[ao.t] + ' ' + ao.job + ' · Lv.' + ao.lv + '<br><span class="mo-el' + ao.el + '">' + (EL_ICON[ao.el] || '') + ' ' + ao.elName + ' 특화</span><br><small>' + ao.skill + '</small></div></div>'
              + '<div class="mine-smith-say">' + ao.nm + '와(과) 계약할까요?<br><small>' + (ao.free ? '급여 없이 바로 계약해요.' : '첫 계약 급여 <b>' + ao.sal + '</b>를 바로 줘요. 앞으로 급여는 없어요(교체도 무료).') + '</small>'
              + (curM ? '<br><small style="color:#ffb04a">⚠️ 지금 용병 <b>' + curM.nm + '</b>(Lv.' + curM.lv + ')은(는) 떠나요. 레벨도 사라져요.</small>' : '') + '</div>'
              + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.mercHireGo(' + ao.i + ')">계약하기</button><button type="button" onclick="TowerMine.mercHire(null)">취소</button></div></div></div>';
        }
      } else mh += '<div class="mine-eq-empty">' + (ms2.message || '불러오는 중...') + '</div>';
      el.smith.innerHTML = mh;
      return;
    }
    if (smithView === 'archishop') { // [2026-10-28] 다도해 상인
      var ah = npcHead('⛱️ ' + n.name, false) + talkBlock(n, n.line);
      (st.archiShop || []).forEach(function (it) {
        ah += '<div class="mine-smith-row"><span class="nm">⛱️ ' + it.name + '<small>' + it.effect + (it.owned ? ' · 보유 -- 🎒 장비 창 무기 칸에서 들기' : '') + '</small>' + (it.owned ? '' : costHtml(it.costs)) + '</span>'
            + (it.owned ? '' : '<button type="button" ' + (it.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.archiBuy(\'' + it.code + '\')">사기</button>') + '</div>';
      });
      el.smith.innerHTML = ah;
      return;
    }
    if (smithView === 'smelter' || smithView === 'gemcutter' || smithView === 'artisan') { // [2026-10-28]
      el.smith.innerHTML = craftNpcHtml(n);
      return;
    }
    if (smithView === 'daygate') { // [2026-10-28] 요일 동굴 문지기
      el.smith.innerHTML = dayGateHtml(n);
      return;
    }
    if (smithView === 'ask') {
      el.smith.innerHTML = askHtml();
      return;
    }
    if (smithView === 'world') {
      el.smith.innerHTML = worldHtml();
      worldBind(); // [2026-10-28] 끌기/확대
      return;
    }
    if (smithView === 'archi') { // [2026-10-28] 다도해 NPC
      el.smith.innerHTML = archiHtml(n);
      return;
    }
    if (smithView === 'equip') { carouselHtml(equipHtml()); return; } // [2026-10-28] 🎒 장비(장착/치장 탭, 캐러셀)
    if (smithView === 'warp') {
      el.smith.innerHTML = warpHtml();
      return;
    }
    if (smithView === 'captain') {
      el.smith.innerHTML = captainHtml(n);
      return;
    }
    if (smithView === 'ship') {
      el.smith.innerHTML = shipHtml();
      return;
    }
    if (smithView === 'boutique') { carouselHtml(boutiqueHtml(n)); return; }
    if (smithView === 'menu') {
      el.smith.innerHTML = npcHead(map && map.id === 'city' ? '🏰 왕도 대장간' : '🔨 대장간', false) // [2026-10-25] 왕도 대장장이(더 높은 단계)
          + talkBlock(n, n ? n.line : '')
          + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.smithGo(\'buy\')">⛑️ 구매<small>투구·장신구·장화</small></button>'
          + '<button type="button" onclick="TowerMine.smithGo(\'upgrade\')">⛏️ 강화<small>곡괭이 내구도</small></button>'
          + '<button type="button" onclick="TowerMine.smithGo(\'synth\')">🌈 합성<small>칠요석 · 구리</small></button></div>';
      return;
    }
    if (smithView === 'buy') { carouselHtml(npcHead('⛑️ 대장간 · 구매', true) + helmetHtml() + gearHtml()); return; } // [2026-10-28] 부위별 캐러셀
    if (smithView === 'synth') {
      el.smith.innerHTML = npcHead('🌈 대장간 · 합성', true) + synthHtml();
      return;
    }
    var capped = st.pickCap && st.durabilityMax >= st.pickCap; // [2026-10-24] 최대 내구도 상한
    var rows = (st.pickUpgrades || []).map(function (r) {
      var have = oreHave(r.grade), can = have >= r.need && !capped;
      return '<div class="mine-smith-row"><img src="' + oreIcon(r.grade) + '" alt=""><span class="nm">' + r.oreName + ' ' + r.need + '개 → 최대 +' + r.gain
          + '<small>보유 ' + have + '개</small></span>'
          + '<button type="button" ' + (can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',1)">1회</button>'
          + '<button type="button" ' + (can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',9999)">최대</button></div>';
    });
    el.smith.innerHTML = npcHead('⛏️ 대장간 · 곡괭이 강화', true)
        + '<div class="mine-smith-dur">⛏️ 곡괭이 내구도 <b>' + st.durability + ' / ' + st.durabilityMax + '</b>' + (st.pickCap ? ' <small>(최대 ' + st.pickCap + ')</small>' : '') + '</div>'
        + (capped ? '<div class="mine-smith-say">곡괭이가 이미 최대치예요. 더는 강화할 수 없어요.</div>' : '')
        + rows.join('');
  }
  // [2026-10-24] 광부 투구 업그레이드(광산상점 탭에서 대장장이 창으로 옮김)
  function helmetHtml() {
    var hm = st.helmetInfo || {};
    var h = '<div class="mine-smith-dur">⛑️ 광부 투구 <b>' + (hm.tier > 0 ? hm.tier + '단계' : '없음') + '</b></div>'
        + '<div class="mine-smith-say">어두운 동굴의 시야가 넓어져요. 동굴 N층은 투구 N-1단계부터 움직일 수 있고, 단계가 그보다 높을수록 더 밝게 보여요(3층까지).</div>';
    if (!hm.nextTier) return h + '<div class="mine-smith-say">지금 열린 최고 단계예요.</div>';
    var away = hm.cityOnly && map.id !== 'city', ok = hm.have >= hm.need && (!hm.cityOnly || hm.extraOk); // [2026-10-25] 4단계 = 왕도 대장장이 + 칠요석
    // [2026-10-29] "투구 4단계 제작이 안 된다" -- 버튼만 흐려지고 이유가 안 보였다(실제론 미스릴 100개 + 칠요석 2개를 못 채운 경우). 모자란 것을 적어 준다.
    var lack = [];
    if (hm.have < hm.need) lack.push(hm.oreName + ' ' + (hm.need - hm.have) + '개');
    if (hm.cityOnly) (hm.extra || []).forEach(function (c) { if (c.have < c.need) lack.push(c.name + ' ' + (c.need - c.have) + '개'); });
    if (lack.length) h += '<div class="mine-smith-say" style="color:#ff9a8a">모자라요: ' + lack.join(', ') + (away ? ' · 만들기는 🏰 왕도 대장장이에게서' : '') + '</div>';
    else if (away) h += '<div class="mine-smith-say" style="color:#ffd27a">재료는 다 모였어요! 🏰 왕도 아스테리아의 대장장이에게 가져가세요.</div>';
    return h + '<div class="mine-smith-row"><img src="' + oreIcon(hm.oreGrade) + '" alt=""><span class="nm">' + (hm.cityOnly ? '🏰 ' : '') + hm.oreName + ' ' + hm.need + '개 → ' + hm.nextTier + '단계'
        + '<small>보유 ' + hm.have + '개' + (hm.cityOnly ? ' · 동굴 3층까지 환하게' : '') + '</small>' + (hm.extra ? costHtml(hm.extra) : '') + '</span>'
        + (away ? '<span class="mine-worn" style="color:#ffd27a">왕도 대장장이</span>'
            : '<button type="button" ' + (ok ? '' : 'data-lack="1" ') + 'onclick="TowerMine.helmetUpgrade()">' + (hm.tier > 0 ? '업그레이드' : '만들기') + '</button>') + '</div>';
  }
  function helmetUpgrade() {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-helmet').then(function (d) {
      msg(d.message || d.error || '완료', 3000);
      return api('/api/tower-mine');
    }).then(function (s) {
      busy = false;
      if (s && !s.error) { st.helmet = s.helmet; st.helmetInfo = s.helmetInfo; applyGear(s); buildMiniBase(); drawMini(); }
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  // [2026-10-24] 장비/합성/선장 응답 -- 서버가 장비 목록·광석·이동 속도·물 위 걷기를 함께 돌려준다.
  function applyGear(d) {
    if (!d) return;
    ['gear', 'equip', 'wenh', 'armorRegen', 'gloveCode', 'archiShop', 'archi', 'dragon', 'sevenMax', 'galaxyMax', 'sevenDayNeed', 'deliverDone', 'ores', 'waterWalk', 'stepMs', 'look', 'cosShop', 'genderCosts', 'genderCan', 'durability', 'durabilityMax'].forEach(function (k) { if (d[k] != null) st[k] = d[k]; });
    if (d.durability != null) showStamina(); // [2026-10-28] 구리 납품 -- 곡괭이 내구도 표시
    STEP_MS = st.stepMs || 140;
    buildSprites(st.look); // [2026-10-24] 외형이 바뀌었으면 캐릭터 그림을 다시
  }
  // [2026-10-25] 전투 턴 -- "전투 중엔 체력 회복 없이, 안 때려도 몬스터가 일정 턴마다 공격, 5칸 이상 벌어지면 인식이 풀리고 체력 가득" 요청.
  //  몬스터에게 인식돼 있거나(st.combat.fight) 동굴 3층 보스 2칸 안이면 1초마다 서버에 묻는다(쫓아온 칸, 맞은 피해, 인식 해제).
  var lastBossT = 0, bossBusy = false, bossWarned = false;
  var bolts = [], BOLT_MS = 420, BOLT_HIT_MS = 280, boltWarned = false; // [2026-10-28] 용암 거신 불덩이(원거리 공격)
  var pStealth = null; // [2026-10-30] 자객의 목걸이 은신 [남은 회피, 끝나는 시각(서버)]
  function pStealthOn() { return !!(pStealth && pStealth[0] > 0 && pStealth[1] > nowSrv()); }
  /** [2026-10-30] "투사체가 플레이어에게 닿도록" -- 쏜 몬스터 몸통(보스는 2x2 가운데)에서 출발. */
  function boltFrom(h) {
    var mb = map.mobs && map.mobs[h.mobId], mt = mb && map.mobTypes[mb.k];
    return mt && mt.boss ? { fx: h.fx + 0.5, fy: h.fy + 0.3 } : { fx: h.fx, fy: h.fy - 0.25 };
  }
  var slowUntil = 0, venomWarned = false; // [2026-10-28] 여왕개미 독산 -- 느려짐(서버 시각까지 걸음 2배)
  var healUntil = 0; // [2026-10-28] 재생의 장갑 -- 이때까지(서버 시각) 전투 턴으로 회복을 받는다
  function stepMs() { return nowSrv() < slowUntil ? STEP_MS * 2 : STEP_MS; }
  // [2026-10-28] 용암 칸(불덩이가 떨어진 칸, "y * w + x") -- 서버가 전투/환경 턴마다 알려 준다. 전투가 끝나면 빈 목록(식음).
  var lavaCells = {};
  function lavaSet(list) {
    var n = {};
    (list || []).forEach(function (c) { n[(c % map.w) + ',' + Math.floor(c / map.w)] = 1; });
    lavaCells = n;
  }
  function bossTick(now) {
    if (!map || busy || bossBusy || fade || now - lastBossT < 1000) return;
    var fight = !!(st.combat && st.combat.fight), nearBoss = false, bt = null;
    if (map.mobs && map.mobs.length) {
      var boss = map.mobs[map.mobs.length - 1]; bt = map.mobTypes[boss.k];
      nearBoss = !!(bt && bt.boss && boss._p && Math.abs(boss._p.cx - P.x) + Math.abs(boss._p.cy - P.y) <= (bt.aggro || (bt.stay ? 4 : 2))); // [2026-10-28] 보스 12칸(서버가 aggro로 보냄)
    }
    // [2026-10-28] 레벨 차이 선공 -- 몬스터 레벨이 내 레벨보다 20 이상 높으면 6칸 안에서 먼저 덤빈다(서버 mineMobPreempts와 같은 식)
    var nearStrong = !fight && !nearBoss && (map.mobs || []).some(function (mb) {
      var ty = map.mobTypes[mb.k];
      return ty && !ty.boss && !ty.minion && mb._p && (ty.first || 1 + 4 * (ty.tier - 1) - Math.min(99, myLv()) >= 20) && Math.abs(mb._p.cx - P.x) + Math.abs(mb._p.cy - P.y) <= (ty.aggro || 6); // [2026-11-05] 왕도 서쪽길 무리는 더 넓게
    });
    var mercPre = !fight && st.merc && st.merc.mode === 1 && !(st.merc.rest > nowSrv()) && (map.mobs || []).some(function (mb) { // [2026-11-06] 용병 선공 모드 -- 사거리 안 몬스터가 보이면 전투 턴(서버가 하나 골라 먼저 친다)
      var ty = map.mobTypes[mb.k];
      var vh2 = viewHalf(); // [2026-11-07] "선공 -- 플레이어 기준 맵에 보이면"
      return ty && !ty.boss && !ty.minion && !ty.dummy && mb._p && Math.abs(mb._p.cx - P.x) <= vh2[0] && Math.abs(mb._p.cy - P.y) <= vh2[1] && !(mobSt[mb.id] && mobSt[mb.id].du > nowSrv());
    });
    if (!fight && !nearBoss && !nearStrong && !mercPre && !frozenMe() && !(healUntil > nowSrv())) return; // [2026-10-28] 재생의 장갑 회복 중에도 // [2026-10-28] 얼어 있으면 얼음 여왕이 쫓아오는 것도 받는다
    lastBossT = now; bossBusy = true;
    tickC = function (d) { // [2026-11-03] 1초 턴(presTick)에 함께 -- 응답의 c
      bossBusy = false;
      if (!d || !d.ok) return;
      // [2026-10-28] "몬스터가 인식했을 때 나오는 문구 없애줘, 벗어났을 때도 필요 없어" -- 보스 선공/레벨 차이 선공/인식 풀림 안내는 띄우지 않는다
      if (d.hpLockUp) floats.push({ x: P.x, y: P.y - 0.9, text: '🔒 최대 체력 -' + d.hpLockUp + '%', color: '#c9c3d6', t0: performance.now() + 300 });
      if (d.summoned) flashT0 = performance.now(); // [2026-10-28] "보스 스킬 쓸 때 가운데 멘트 없애줘, 특히 성당 보스" -- 번쩍임만
      (d.mobs || []).forEach(function (a) { mobSt[a[0]] = mobStOf(a, mobSt[a[0]]); });
      if (d.lava) lavaSet(d.lava);
      if (d.shock) shockSet(d.shock); // [2026-11-05] 천둥 황소 장판
      if (d.knockX != null) setPos(d.knockX, d.knockY); // [2026-11-05] 밀쳐내기
      if (d.slowUntil) slowUntil = d.slowUntil; // [2026-10-28] 여왕개미 독산
      if (d.stunUntil) stunUntil = d.stunUntil; // [2026-11-04] 파라오 미라 붕대 스턴
      if (d.healUntil != null) healUntil = d.healUntil; // [2026-10-28] 재생의 장갑
      if (d.healed) floats.push({ x: P.x - 0.2, y: P.y - 0.5, text: '+' + d.healed + ' 💚', color: '#7cf08a', t0: performance.now() });
      if (d.veinBroken) veinBroke(); // [2026-10-28] 설산 동굴
      var t = performance.now();
      (d.hits || []).forEach(function (h, k) {
        var hmb = map.mobs && map.mobs[h.mobId], hwind = !!(hmb && (map.mobTypes[hmb.k] || {}).name === '하피 여왕'); // [2026-11-11] 하피 공격은 바람 속성
        if (hwind && h.ranged) { var bw0 = boltFrom(h); bolts.push({ fx: bw0.fx, fy: bw0.fy, me: true, t0: t, wind: true }); floats.push({ x: P.x, y: P.y - 0.3, text: '🌪️🪶 -' + h.dmg, color: '#7ae8c8', big: true, t0: t + BOLT_MS }); return; }
        if (hwind) { floats.push({ x: P.x, y: P.y - 0.3, text: '🌪️ -' + h.dmg, color: '#7ae8c8', big: true, t0: t + k * 160 }); return; }
        if (h.poison) { floats.push({ x: P.x + 0.3, y: P.y - 0.1, text: '-' + h.dmg + '☠️', color: '#9ad84a', t0: t + k * 120 }); return; } // [2026-10-28] 여왕개미 독
        if (h.venom) { // [2026-10-28] 여왕개미 독산 -- 초록 덩이, 맞으면 느려지고 중독
          var bv = boltFrom(h); bolts.push({ fx: bv.fx, fy: bv.fy, me: true, t0: t, venom: true });
          floats.push({ x: P.x, y: P.y - 0.3, text: '🧪 -' + h.dmg + ' 느려짐·중독', color: '#9ad84a', big: true, t0: t + BOLT_MS });
          return;
        }
        if (h.ranged && h.dark) { // [2026-11-11] 파라오 미라 어둠 화살(붕대 사이)
          var bdk = boltFrom(h); bolts.push({ fx: bdk.fx, fy: bdk.fy, me: true, t0: t, dark: true });
          floats.push({ x: P.x, y: P.y - 0.3, text: '🌑 -' + h.dmg, color: '#c8a8ff', big: true, t0: t + BOLT_MS });
          return;
        }
        if (h.ranged && h.stun) { // [2026-11-04] 파라오 미라 붕대 감기 -- 붕대가 날아와 감기고 잠깐 못 움직인다
          var bb = boltFrom(h); bolts.push({ fx: bb.fx, fy: bb.fy, me: true, t0: t, bandage: true });
          floats.push({ x: P.x, y: P.y - 0.3, text: '🩹 붕대! -' + h.dmg, color: '#f0e6cc', big: true, t0: t + BOLT_MS });
          return;
        }
        if (h.ranged) { // [2026-10-28] 용암 거신 불덩이 -- 보스 칸에서 날아와 맞는다
          var bf = boltFrom(h); bolts.push({ fx: bf.fx, fy: bf.fy, me: true, t0: t });
          floats.push({ x: P.x, y: P.y - 0.3, text: '☄️ -' + h.dmg, color: '#ff8a2a', big: true, t0: t + BOLT_MS });
          return;
        }
        if (h.thunder) { // [2026-11-05] 천둥 황소 -- 전광석화(번개 줄기) / 전기창(푸른 창이 날아와 폭발) / 밀쳐내기 + 근접 장판
          var bcx = h.fx + 1, bcy = h.fy + 1;
          if (h.thunder === 'dash') { if (h.pull) zapAdd(bcx, bcy, h.tx + 1, h.ty + 1); zapAdd(h.tx + 1, h.ty + 1, P.x + 0.5, P.y + 0.5); flashT0 = t; floats.push({ x: P.x, y: P.y - 0.3, text: '⚡ 전광석화 -' + h.dmg, color: '#ffe94a', big: true, t0: t }); }
          else if (h.thunder === 'spear') { var bs2 = boltFrom(h); bolts.push({ fx: bs2.fx, fy: bs2.fy, me: true, t0: t, spear: true }); floats.push({ x: P.x, y: P.y - 0.3, text: '🔱 -' + h.dmg, color: '#7ad8ff', big: true, t0: t + BOLT_MS }); }
          else { for (var zi = 0; zi < 6; zi++) { var za = zi * Math.PI / 3; zapAdd(bcx, bcy, bcx + Math.cos(za) * 3.5, bcy + Math.sin(za) * 3.5); } flashT0 = t; floats.push({ x: P.x, y: P.y - 0.3, text: '💥 -' + h.dmg, color: '#ffe94a', big: true, t0: t }); }
          return;
        }
        if (h.dodge) { floats.push({ x: P.x, y: P.y - 0.3, text: '💨 회피', color: '#c8c8ff', big: true, t0: t + k * 160 }); return; } // [2026-10-30] 자객의 목걸이 은신
        floats.push({ x: P.x, y: P.y - 0.3, text: (h.x2 ? '🗡️ 2배! ' : '') + '-' + h.dmg, color: h.x2 ? '#ff3a6a' : '#ff4a4a', big: true, t0: t + k * 160 });
      }); // [2026-10-28] 은신했던 닌자의 기습
      if (d.merc) mercApply(d.merc); // [2026-11-05] 용병
      if (d.mercXp) mercXpApply(d.mercXp); // [2026-11-06] 용병 경험치
      if (d.mercs) { st.mercs = d.mercs; if (smithOpen && smithView === 'mercmgr') renderSmith(smithNpc); } // [2026-11-06] 급여 차감/밀림
      if (d.mercPay) { mercPayNote(d.mercPay); if (!d.merc && st) st.merc = null; }
      if (d.levelUp) floats.push({ x: P.x, y: P.y - 1, text: '⬆️ 레벨 업!', color: '#ffe14d', big: true, t0: performance.now() });
      if ('pStealth' in d) pStealth = d.pStealth; // [2026-10-30]
      if (d.rewardMsg) { if (d.ores) st.ores = d.ores; flashT0 = t; msg(d.rewardMsg, 4200); } // [2026-10-28] 함께 싸운 닌자 두목 첫 처치 보상
      if (d.combat) setCombat(d.combat);
      else if (st.combat && d.fight === false) { st.combat.fight = false; renderHp(); }
      // (인식 풀림 안내는 띄우지 않는다 -- 위 [2026-10-28])
      if (d.died && d.state) fallDown(d);
    };
  }
  // [2026-10-26] 한기 -- 한기 맵(설원/설산)에서 1초마다 서버에 묻는다(한기/눈바람 피해, 안전지대, 다음 눈바람 시각).
  //  눈바람 5초 전 "곧 눈바람이 몰아칩니다" + 가장 가까운 모닥불 방향 안내. 안전지대(모닥불 둘레)에선 한기도 눈바람도 없다.
  var cold = null, lastColdT = 0, coldBusy = false, bzWarned = 0, lastBz = 0;
  function inSafe() {
    return (map && map.safe || []).some(function (z) { return (P.x - z[0]) * (P.x - z[0]) + (P.y - z[1]) * (P.y - z[1]) <= z[2] * z[2]; });
  }
  function nearestSafeText() {
    var best = null, bd = 1e9;
    (map && map.safe || []).forEach(function (z) { var d = Math.hypot(z[0] - P.x, z[1] - P.y) - z[2]; if (d < bd) { bd = d; best = z; } });
    if (!best) return '';
    var dx = best[0] - P.x, dy = best[1] - P.y, ns = dy < -2 ? '북' : dy > 2 ? '남' : '', ew = dx > 2 ? '동' : dx < -2 ? '서' : '';
    return '가장 가까운 모닥불: ' + (ns + ew || '바로 옆') + (ns + ew ? '쪽 ' + Math.max(1, Math.round(bd)) + '칸' : '');
  }
  // [2026-10-28] 성당 옛터 -- 같은 환경 턴으로 대주교 마법(불벼락 = 대주교 12칸 안, 어둠가스 = 맵 전체)을 받는다. 예고 후 화면 효과.
  var raid = null, raidFx = { fire: 0, gas: 0 }, raidWarned = {};
  function coldTick(now) {
    if (!map || !(map.cold || map.raid || map.hot || map.thunder) || busy || coldBusy || fade || now - lastColdT < 1000) return; // [2026-10-28] 더운 곳(hot)도
    lastColdT = now; coldBusy = true;
    var mid = map.id;
    tickE = function (d) { // [2026-11-03] 1초 턴(presTick)에 함께 -- 응답의 e
      coldBusy = false;
      if (!d || !d.ok || !map || map.id !== mid) return;
      if (cold && cold.nextBz && d.nextBlizzardAt && d.nextBlizzardAt !== cold.nextBz) lastBz = cold.nextBz;
      cold = { eff: d.coldEff, lv: d.cold, def: d.coatDef, nextBz: d.nextBlizzardAt, heat: d.heat || 0, heatRate: d.heatRate, coldRate: d.coldRate, rb: d.rebirth || 0 };
      var t = performance.now();
      if (d.coldDmg) floats.push({ x: P.x + 0.3, y: P.y - 0.2, text: '-' + d.coldDmg + '❄', color: '#9fd8ff', t0: t });
      if (d.heatDmg) { floats.push({ x: P.x + 0.3, y: P.y - 0.2, text: '-' + d.heatDmg + '☀', color: '#ffb347', t0: t }); if (!heatWarned) { heatWarned = true; msg(map.archi ? '☀️ 한낮의 햇볕이 뜨거워요! 🌴 야자수 그늘에서는 괜찮아요.' : '🥵 방한 장비를 낀 채로는 너무 더워요! 🎒 장비에서 벗으면 괜찮아요.', 3600); } }
      if (d.blizzardDmg) { flashT0 = t; floats.push({ x: P.x, y: P.y - 0.7, text: '🌨️ -' + d.blizzardDmg, color: '#ffffff', big: true, t0: t }); msg('🌨️ 눈바람에 휩쓸렸어요! 최대 체력의 10%를 잃었어요.', 2800); }
      else if (d.blizzardSafe) msg('🔥 모닥불 곁에서 눈바람을 피했어요!', 2200);
      if (map.raid) {
        raid = { awake: !!d.raidAwake, nextFire: d.nextFireAt || 0, nextGas: d.nextGasAt || 0 };
        if (d.fireAt) raidFx.fire = performance.now();
        if (d.gasAt) raidFx.gas = performance.now();
        if (d.fireDmg) floats.push({ x: P.x, y: P.y - 0.7, text: '🔥 -' + d.fireDmg, color: '#ff6a3a', big: true, t0: t }); // [2026-10-28] 가운데 멘트 없이 숫자만
        if (d.gasDmg) floats.push({ x: P.x, y: P.y - 1.1, text: '☠️ -' + d.gasDmg, color: '#c08aff', big: true, t0: t + 150 });
      }
      if (map.ice) iceApply(d, t); // [2026-10-28] 설산 봉우리 -- 빙결/얼음땡
      if (d.lava) lavaSet(d.lava); // [2026-10-28] 용암섬
      if (d.shock) shockSet(d.shock); // [2026-11-05] 천둥 황소 장판
      if (d.stormNext) stormNext = d.stormNext;
      if (d.shockDmg) floats.push({ x: P.x - 0.3, y: P.y - 0.2, text: '-' + d.shockDmg + '⚡', color: '#ffe94a', t0: t }); // [2026-11-06] 황소가 나와 있으면 초당 1% 감전도 여기
      if (d.storm) { stormT0 = performance.now(); flashT0 = stormT0; if (d.stunUntil) { stunUntil = d.stunUntil; zapStunUntil = d.stunUntil; } floats.push({ x: P.x, y: P.y - 0.7, text: '⚡ 감전! -' + (d.stormDmg || 0), color: '#ffe94a', big: true, t0: t }); }
      if (d.gustNext) harpyGustAt = d.gustNext; // [2026-11-11] 하피 돌풍(다음 시각 -- 2초 전부터 화면 위에 경고)
      if (d.gust) { flashT0 = performance.now(); if (d.knockX != null) setPos(d.knockX, d.knockY); floats.push({ x: P.x, y: P.y - 0.7, text: '🌪️ 돌풍! -' + (d.gustDmg || 0), color: '#bfeee0', big: true, t0: t }); msg('🌪️ 하피 여왕의 돌풍에 날려 갔어요!', 2400); }
      if (d.lavaDmg) floats.push({ x: P.x - 0.3, y: P.y - 0.2, text: '-' + d.lavaDmg + '🌋', color: '#ff5a1a', t0: t });
      if (d.combat) setCombat(d.combat);
      renderCold();
      if (d.died && d.state) fallDown(d);
    };
  }
  var heatWarned = false;
  // [2026-10-28] 설산 봉우리 -- 얼음 여왕이 18초마다 한 명씩 얼린다(최대 15초). 언 동안 못 움직이고(서버도 막음), 여왕에게 맞으면 즉사.
  //  얼지 않은 동료가 2칸 안에 오면 풀린다(얼음땡). 서버가 환경 턴으로 알려 준다: iceAwake, nextFreezeAt, frozen {x, y, until}, thawed.
  var iceSt = null, iceWarned = 0;
  var stunUntil = 0; // [2026-11-04] 파라오 미라 붕대에 감김(서버 시각까지)
  function stunnedMe() { return stunUntil > nowSrv(); }
  // [2026-11-05] 천둥 황소 -- 전기 장판 칸, 번개 줄기(전광석화/밀쳐내기), 전체 낙뢰, 감전 스턴, 마물이 숨은 시각
  var shockCells = {}, zaps = [], stormT0 = 0, stormNext = 0, zapStunUntil = 0, hideNoticeT = 0, ZAP_MS = 450, thunderUntil = 0, thunderKills = 0, thunderNeed = 0, thunderRestAt = 0, ambZapT = 0;
  function shockSet(list) { var n = {}; (list || []).forEach(function (c) { n[(c % map.w) + ',' + Math.floor(c / map.w)] = 1; }); shockCells = n; }
  function zapAdd(x0, y0, x1, y1) { zaps.push({ x0: x0, y0: y0, x1: x1, y1: y1, t0: performance.now(), seed: Math.random() * 1000 }); }
  function stunMsg() { return zapStunUntil >= stunUntil ? '⚡ 감전되어 움직일 수 없어요!' : '🩹 붕대에 묶여 움직일 수 없어요!'; }
  function drawZapStun(x, y, now) { // 감전 -- 캐릭터 둘레에 튀는 노란 불꽃
    var TS = T * scale;
    ctx.save(); ctx.strokeStyle = '#ffe94a'; ctx.lineWidth = Math.max(1, scale * 0.8); ctx.shadowColor = '#7ad8ff'; ctx.shadowBlur = 6 * scale;
    for (var i = 0; i < 4; i++) {
      var a = now / 90 + i * 1.7, r0 = TS * 0.35, r1 = TS * 0.62, cx0 = x + TS / 2, cy0 = y + TS / 2;
      ctx.beginPath(); ctx.moveTo(cx0 + Math.cos(a) * r0, cy0 + Math.sin(a) * r0); ctx.lineTo(cx0 + Math.cos(a + 0.3) * (r0 + r1) / 2, cy0 + Math.sin(a - 0.3) * (r0 + r1) / 2); ctx.lineTo(cx0 + Math.cos(a) * r1, cy0 + Math.sin(a) * r1); ctx.stroke();
    }
    ctx.restore();
  }
  /** 붕대에 감긴 모습 -- 몸을 가로지르는 베이지 붕대 줄(흔들림). (x, y) = 캐릭터 그림 왼쪽 위. */
  function drawBandage(x, y, now) {
    var TS = T * scale;
    ctx.save();
    for (var i = 0; i < 4; i++) {
      var yy = y + TS * (0.3 + i * 0.15) + Math.sin(now / 120 + i) * scale * 0.5;
      ctx.fillStyle = i % 2 ? '#e8dcc0' : '#d0c098'; ctx.globalAlpha = 0.9;
      ctx.beginPath(); ctx.moveTo(x + TS * 0.12, yy); ctx.lineTo(x + TS * 0.88, yy - TS * 0.07); ctx.lineTo(x + TS * 0.88, yy - TS * 0.07 + 2.5 * scale); ctx.lineTo(x + TS * 0.12, yy + 2.5 * scale); ctx.closePath(); ctx.fill();
    }
    ctx.restore();
  }
  // [2026-11-04] 빙결 몸부림 -- 움직이거나 공격하려 할 때마다 1(0.12초에 한 번까지), 얼음마다 처음부터 센다. ICE_STRUGGLE번이면 서버에 알린다(얼음이 깨짐).
  var ICE_STRUGGLE = 20, iceStruggleN = 0, iceStruggleKey = 0, iceStruggleT = 0, iceStruggleBusy = false;
  function iceStruggle(now) {
    if (!frozenMe() || iceStruggleBusy || now - iceStruggleT < 120) return;
    iceStruggleT = now;
    var k = iceSt.frozen.k || iceSt.frozen.until; // [2026-11-07] 얼음마다 안 바뀌는 번호(예전 until은 15초 뒤 매초 바뀌어 수가 0으로 돌아갔다)
    if (k !== iceStruggleKey) { iceStruggleKey = k; iceStruggleN = 0; }
    iceStruggleN++;
    floats.push({ x: P.x + (Math.random() - 0.5) * 0.8, y: P.y - 0.2, text: '❄', color: '#bfefff', t0: now });
    if (iceStruggleN < ICE_STRUGGLE) return;
    iceStruggleBusy = true;
    api('/api/tower-mine-struggle?n=' + iceStruggleN).then(function (d) {
      iceStruggleBusy = false;
      if (d && d.ok) {
        iceSt.frozen = null; iceStruggleN = 0;
        flashT0 = performance.now();
        floats.push({ x: P.x, y: P.y - 0.7, text: '🧊 쨍그랑!', color: '#bfefff', big: true, t0: performance.now() });
        msg('🧊 얼음을 깨고 빠져나왔어요!', 2200);
      } else iceStruggleN = Math.max(0, ICE_STRUGGLE - 3); // 아직이면(너무 빨랐음) 조금 더
    }).catch(function () { iceStruggleBusy = false; });
  }
  function frozenMe() { return !!(iceSt && iceSt.frozen && iceSt.frozen.until > nowSrv() && map && map.ice); }
  function iceApply(d, t) {
    var was = frozenMe();
    iceSt = { awake: !!d.iceAwake, next: d.nextFreezeAt || 0, frozen: d.frozen || null };
    if (d.frozen && !was) {
      setAuto(false); keys = []; drag = null; tapDir = null;
      setPos(d.frozen.x, d.frozen.y);
      flashT0 = t;
      floats.push({ x: P.x, y: P.y - 0.9, text: '🧊 빙결!', color: '#bfefff', big: true, t0: t }); // [2026-10-28] 가운데 멘트 대신(위 HUD에 "빙결 -- 동료가 와야 풀려요")
    }
    if (d.thawed === 'saved') { floats.push({ x: P.x, y: P.y - 0.7, text: '🧊 얼음땡!', color: '#bfefff', big: true, t0: t }); msg('🧊 얼음땡! 얼어붙은 동료를 구했어요!', 2800); }
    else if (d.thawed === 'rescued' && !d.frozen) { floats.push({ x: P.x, y: P.y - 0.7, text: '🧊 땡!', color: '#bfefff', big: true, t0: t }); msg('🧊 동료가 얼음을 깨 줬어요! 다시 움직일 수 있어요.', 2800); }
    else if (d.thawed === 'melted' && !d.frozen) msg('🧊 얼음이 녹았어요. 다시 움직일 수 있어요.', 2400);
  }
  function iceWarn() {
    if (!iceSt || !iceSt.awake || !iceSt.next || frozenMe()) return;
    var left = iceSt.next - nowSrv();
    // [2026-10-28] 빙결 예고 멘트는 없앴다(위 HUD에 남은 초)
  }
  /** 얼음 감옥(언 캐릭터 위) -- (x, y) = 캐릭터 그림 왼쪽 위. [2026-10-29] "빙결 이펙트 더 멋지게(16x16 도트)":
   *  20x22 도트(캐릭터 16x16 둘레 + 위로 솟은 얼음 가시) -- 들쭉날쭉한 결정 면(밝은 면/어두운 면), 발밑 서리, 반짝임 3프레임. 반투명이라 안이 비친다. */
  var iceFrames = null;
  function iceFrame(f) {
    if (!iceFrames) iceFrames = [0, 1, 2].map(function (k) {
      var c = mk(20, 22), g = c.getContext('2d');
      var body = 'rgba(150,215,250,0.50)', lite = 'rgba(225,248,255,0.62)', deep = 'rgba(80,160,225,0.50)', rim = '#e8faff', rimD = '#6fb4e0';
      // 몸통(위가 좁고 아래가 넓은 결정 덩어리) -- 행마다 [시작, 끝]
      var rows = [[6, 13], [5, 14], [4, 15], [3, 16], [3, 16], [2, 17], [2, 17], [2, 17], [1, 18], [1, 18], [1, 18], [1, 18], [1, 18], [1, 18], [1, 18], [2, 17], [1, 18], [0, 19]];
      rows.forEach(function (r, i) { var y = 4 + i; px(g, body, r[0], y, r[1] - r[0] + 1, 1); px(g, rim, r[0], y); px(g, rimD, r[1], y); });
      // 면: 왼쪽 위 밝은 면, 오른쪽 아래 어두운 면, 가운데 갈라진 결
      for (var i = 0; i < 8; i++) px(g, lite, 3 + (i >> 1), 6 + i, 3 - (i >> 2), 1);
      for (var j = 0; j < 7; j++) px(g, deep, 13 - (j >> 1), 13 + j, 4 + (j >> 1), 1);
      [[9, 8], [10, 9], [10, 10], [9, 11], [11, 12], [12, 13]].forEach(function (q) { px(g, rim, q[0], q[1]); });
      // 머리 위 얼음 가시(가운데 큰 것 + 양옆)
      [[9, 0, 4], [6, 2, 2], [13, 1, 3], [3, 4, 1], [16, 4, 1]].forEach(function (q) {
        for (var t = 0; t < q[2]; t++) { px(g, 'rgba(190,235,255,0.85)', q[0] - (t >> 1), q[1] + t, 1 + (t >> 1) * 2 + 1, 1); }
        px(g, '#ffffff', q[0], q[1]);
      });
      // 발밑 서리
      for (var x = 0; x < 20; x++) if ((x * 7) % 5 !== 0) px(g, x % 3 ? '#dff6ff' : '#9fd8f0', x, 21);
      outline(c, '#2f6a9a'); // 눈밭에서도 또렷하게
      // 반짝임(프레임마다 자리가 바뀐다)
      [[[4, 7], [15, 11], [8, 17]], [[12, 6], [5, 14], [16, 17]], [[7, 9], [14, 8], [3, 18]]][k].forEach(function (q) {
        px(g, '#ffffff', q[0], q[1]); px(g, 'rgba(255,255,255,0.7)', q[0] - 1, q[1]); px(g, 'rgba(255,255,255,0.7)', q[0] + 1, q[1]); px(g, 'rgba(255,255,255,0.7)', q[0], q[1] - 1); px(g, 'rgba(255,255,255,0.7)', q[0], q[1] + 1);
      });
      return c;
    });
    return iceFrames[f % 3];
  }
  function drawIce(x, y, now) {
    var u = scale; // 16 도트 한 칸
    var smooth = ctx.imageSmoothingEnabled; ctx.imageSmoothingEnabled = false;
    ctx.drawImage(iceFrame(Math.floor(now / 240)), x - 2 * u, y - 5 * u, 20 * u, 22 * u);
    ctx.imageSmoothingEnabled = smooth;
  }
  function coldWarn() {
    if (map && map.ice) iceWarn(); // [2026-10-28] 설산 봉우리
    if (map && map.raid) { raidWarn(); return; } // [2026-10-28]
    if (map && map.hot) { renderCold(); return; } // [2026-10-28] 더운 곳
    if (!map || !map.cold) { if (el.cold && el.cold.style.display !== 'none') el.cold.style.display = 'none'; return; }
    if (cold && cold.nextBz) {
      var left = cold.nextBz - nowSrv();
      if (left > 0 && left <= 5000 && bzWarned !== cold.nextBz) {
        bzWarned = cold.nextBz;
        msg(inSafe() ? '🌨️ 곧 눈바람이 몰아칩니다! 모닥불 곁이라 괜찮아요.' : '🌨️ 곧 눈바람이 몰아칩니다! 🔥 안전지대(모닥불)로 피하세요! ' + nearestSafeText(), 5000);
      }
    }
    renderCold();
  }
  function rateTxt(r) { return String(Math.round(r * 100) / 100); } // [2026-10-28] 환생 경감 뺀 실제 초당 %(소수 둘째 자리까지)
  function renderCold() {
    if (!el.cold || (map && map.raid)) return; // 성당 옛터는 raidWarn이 쓴다
    if (map && map.archi && map.sun) { // [2026-10-28] 다도해 -- 낮(햇빛 %)/밤, 남은 분
      var tn = nowSrv(), day = archiDay(tn), heat = cold ? cold.heat : 0;
      var hr = cold && cold.heatRate != null ? cold.heatRate : (heat || map.sun);
      var at = (inSafe() ? '🌴 그늘' : !day ? '🌙 밤' : hr > 0 ? '☀️ -' + rateTxt(hr) + '%/초' : '😇 햇빛 막음') + ' ' + archiLeftMin(tn) + '분'; // [2026-10-28] 환생 경감 반영
      if (el.cold._t !== at) { el.cold._t = at; el.cold.textContent = at; }
      el.cold.style.display = 'block';
      return;
    }
    if (map && map.hot) { // [2026-10-28] 더위 -- 방한 장비를 끼고 있을 때만 보인다
      var ht = cold && cold.heat ? (inSafe() ? '🌴 그늘' : '🥵 더위 -' + rateTxt(cold.heatRate != null ? cold.heatRate : cold.heat) + '%/초') : '';
      if (el.cold._t !== ht) { el.cold._t = ht; el.cold.textContent = ht; }
      el.cold.style.display = ht ? 'block' : 'none';
      return;
    }
    if (!map || !map.cold) { el.cold.style.display = 'none'; return; }
    var eff = cold ? cold.eff : map.cold, left = cold && cold.nextBz ? cold.nextBz - nowSrv() : 0;
    var txt = inSafe() ? '🔥 안전지대' : eff > 0 ? '🥶 한기 -' + rateTxt(cold && cold.coldRate != null ? cold.coldRate : eff) + '%/초' : '🧥 한기 막음'; // [2026-10-28] 짧게(화면 가림)
    if (left > 0 && left <= 10000) txt += ' 🌨️' + Math.ceil(left / 1000) + '초';
    if (map.ice) { // [2026-10-28] 설산 봉우리 -- 언 동안 남은 초 / 여왕이 깨어 있으면 다음 빙결까지
      if (frozenMe()) txt = '🧊 빙결 -- 동료가 와야 풀려요'; // [2026-10-28] 저절로 안 녹는다
      else if (iceSt && iceSt.awake && iceSt.next > nowSrv()) txt += ' ❄️' + Math.ceil((iceSt.next - nowSrv()) / 1000) + '초';
    }
    if (el.cold._t !== txt) { el.cold._t = txt; el.cold.textContent = txt; }
    if (el.cold.style.display !== 'block') el.cold.style.display = 'block';
  }
  // [2026-10-27] 왕도 주민 -- "왕도 내 움직이는 NPC(말풍선으로만 말하고 대화 창 없음)". 서버가 준 길(칸 목록)을 시각으로 왕복해서
  //  모든 화면에서 같은 자리에 보인다. 길을 막지 않고(지나갈 수 있음), 7초마다 셋 중 하나꼴로 3.6초 말풍선. 외형은 번호로 정해진다.
  // [2026-11-05] 왕도 주민 소문(유저 이야기) -- 왕도에 있는 동안 1분마다 서버에서 받아 온다
  // [2026-11-05] NPC 대화 사전(업데이트 소식, updLines) -- 어느 맵이든 1분마다 함께 받아, 왕도 주민과 마을 NPC가 가끔 말한다
  var gossip = [], updLines = [], gossipT = -1e9;
  function gossipTick(now) {
    if (!map || now - gossipT < 60000) return;
    gossipT = now;
    api('/api/tower-mine-gossip').then(function (d) { /* [2026-11-06] 소켓이 열려 있으면 소켓으로 */ if (d && d.lines) gossip = d.lines; if (d && d.updates) updLines = d.updates; }).catch(function () { });
  }
  // [2026-11-05] 용병(동료) -- 평소엔 내가 지나온 칸을 두세 칸 뒤에서 따라오고, 전투 턴(서버)이 고른 몬스터 옆 칸으로 달려가 싸운다(흩어짐).
  //  체력이 다하면 내 뒤 칸에서 쉰다(흐리게 + 💤). 다른 유저의 용병도 같은 식(쉬는 중/싸우는 칸은 위치 알림으로 온다).
  // [2026-11-06] "용병은 전부 여자 캐릭, S5 동료(고양이 소녀 초상화)를 캐릭터화, 용병 치장은 없앰" -- 직업 옷 + 고양이 귀(머리색) + 꼬리, 이름(번호)마다 머리색.
  //  전사 = 은빛 갑옷 + 기본 칼, 궁수 = 초록 망토 + 기본 활, 마법사 = 별빛 로브 + 기본 지팡이. 날개는 그리지 않는다.
  var MERC_BASE = [
    { gender: 1, top: '#c0c8d4|armor', bottom: '#7a2a3a', shoes: '#4a3a2a', weapon: 'sword' },
    { gender: 1, top: '#3f8a4a|cape', bottom: '#5a4a2a', shoes: '#5b3a1e', weapon: 'bow' },
    { gender: 1, top: '#5a3aa0|star', bottom: '#2a1a4a', shoes: '#2a1a3a', weapon: 'staff' }
  ];
  var MERC_HAIR = ['#2b1d16', '#f4d77a', '#ff9ac8', '#e8e8f0', '#c8843a', '#7ab8ff', '#b58aff', '#d84a4a', '#6ad8a8', '#8a4a2a'];
  // [2026-11-06] "초상화 기반 용병 캐릭터(64x64)" -- 이름 번호(MINE_MERC_NAMEPOOL 순서)마다 초상화에서 본 모습: 머리색/모양(long 긴 / twin 양갈래 / short 짧은 / bob 단발 / bun 올림),
  //  눈, 윗옷/치마, 장식색(acc 리본·넥타이), 고양이 귀(ears), 메이드(apron/band/frill), 기모노(kimono/obi), 모자(hat/beret) 등. MineChar.nekoHD가 그린다.
  var MERC_HD = (function () {
    var W = '#f8f8fa';
    return [
      [ // 전사
        { hair: '#e8e0f0', style: 'long', eye: '#e0405a', top: '#2a2230', skirt: '#2a2230', acc: '#1a1420', shirt: W, sleeve: W, ears: 1, orn: '#e04060', frill: 1, legs: '#2a2230' }, // 렌지
        { hair: '#b49884', style: 'long', eye: '#5a78d8', top: '#6e6e7a', skirt: '#ececf0', acc: '#222228', shirt: W, tie: 1, ears: 1 }, // 아츠시
        { hair: '#eeecf0', style: 'long', eye: '#e8b030', top: '#1e1a20', skirt: '#1e1a20', acc: '#c0202a', orn: '#c0202a', frill: 1, frillCol: '#c0202a', legs: '#1e1a20' }, // 츠요시
        { hair: '#f6e0a0', style: 'long', eye: '#e0a030', top: '#f6eef2', skirt: '#e86090', acc: '#c03050', obi: '#c03050', kimono: 1, ears: 1, orn: '#8a8ae0' }, // 고우키
        { hair: '#e0204a', style: 'twin', eye: '#8a90b0', top: '#f2b4c4', skirt: '#2a2428', acc: '#1e1a20', belt: '#1e1a20', orn: '#1e1a20' }, // 타이가
        { hair: '#8ec0cc', style: 'twin', eye: '#e8603a', top: '#3a8c5c', skirt: '#5a6070', acc: '#e04a3a', sleeve: '#c8ccd4', ears: 1 }, // 유이토
        { hair: '#efe9f6', style: 'long', eye: '#e8c040', top: '#2a2230', skirt: '#2a2230', acc: '#f08ab0', apron: 1, frill: 1, ears: 1, orn: '#f08ab0', legs: '#f4f4f6' }, // 카게로우
        { hair: '#3a2622', style: 'bun', eye: '#48c060', top: '#5a1a2a', skirt: '#5a1a2a', acc: '#d0a040', obi: '#d0a040', kimono: 1, ears: 1, orn: '#e04050', orn2: '#f4f0f0' }, // 나기
        { hair: '#d8c4d4', style: 'long', eye: '#9050c0', top: '#2a3a6a', skirt: '#4a5a7a', acc: '#c03040', shirt: W, tie: 1, ears: 1, orn: '#e04050' }, // 야토
        { hair: '#d8dade', style: 'long', eye: '#e02030', top: '#2a2228', skirt: '#3a3238', acc: '#c02030', band: 1, bandCol: '#1e1a20', legs: '#2a2228' } // 시노
      ],
      [ // 궁수
        { hair: '#f2d6cc', style: 'long', eye: '#40b4c4', top: '#c6a676', skirt: '#b8946a', acc: '#c0303a', shirt: W, ears: 1 }, // 세리나
        { hair: '#f08ca8', style: 'long', eye: '#8a5ac8', top: '#f4a8bc', skirt: '#2a1e22', acc: '#2a1e22', ears: 1, orn: '#ffffff' }, // 츠키미
        { hair: '#2c2434', style: 'long', eye: '#e8b030', top: '#1e1e28', skirt: '#1e1e28', acc: '#f4f4f6', apron: 1, frill: 1, band: 1, legs: '#1e1e28' }, // 스즈네
        { hair: '#4a84dc', style: 'long', eye: '#f0c040', top: '#f2f4fc', skirt: '#c8303c', acc: '#4a70c8', obi: '#4a70c8', kimono: 1, ears: 1, orn: '#f05080' }, // 아마츠
        { hair: '#f4f2f6', style: 'bob', eye: '#c84aa8', top: '#f2f2f6', skirt: '#2a2230', acc: '#3a58c0', tie: 1, band: 1, frill: 1, ears: 1 } // 히카리
      ],
      [ // 마법사
        { hair: '#8c5232', style: 'short', eye: '#e88030', top: '#6a4a30', skirt: '#c8a888', acc: '#3a2a20', shirt: W, tie: 1, ears: 1, orb: '#ff9a3a' }, // 리오
        { hair: '#b8a080', style: 'long', eye: '#7a6a5a', top: '#8a8070', skirt: '#8a8070', acc: '#2a2a2a', ears: 1, legs: '#222226', orb: '#c8e0a0' }, // 카나
        { hair: '#9c6a4a', style: 'long', eye: '#4a84c4', top: '#f2f2f6', skirt: '#f2f2f6', acc: '#d03040', collar: '#2a3a6a', ears: 1, orb: '#6ac8ff' }, // 미코토
        { hair: '#b88ae0', style: 'long', eye: '#8ad040', top: '#f4f4f6', skirt: '#2a2830', acc: '#222228', sleeve: '#b8b8c0', beret: '#2a2830', orb: '#c8a0ff' }, // 세라
        { hair: '#9cc890', style: 'long', eye: '#8a80c8', top: '#f4f6e4', skirt: '#f4f6e4', acc: '#f0a020', frill: 1, frillCol: '#f0b040', band: 1, bandCol: '#f8f4dc', orb: '#b0f080' }, // 아리스
        { hair: '#4ec8c0', style: 'twin', eye: '#3a7ad8', top: '#f2f2f6', skirt: '#222228', acc: '#e85090', sleeve: '#e85090', tie: 1, ears: 1, orb: '#7af0e0' }, // 코하루
        { hair: '#d89050', style: 'long', eye: '#4a70d0', top: '#f4f0f0', skirt: '#1e1e24', acc: '#1e1e24', tie: 1, band: 1, ears: 1, legs: '#1e1e24', orb: '#ffc080' }, // 미레이
        { hair: '#f8a0b8', style: 'twin', eye: '#e04878', top: '#f8b4c6', skirt: '#f8b4c6', acc: '#e04878', apron: 1, frill: 1, band: 1, ears: 1, legs: '#f8f8fa', orb: '#ff8ab8' }, // 스이렌
        { hair: '#94c8f0', style: 'long', eye: '#3a90d8', top: '#f2f6fc', skirt: '#f2f6fc', acc: '#4a6ad0', hat: 1, frill: 1, orb: '#9ad8ff' }, // 아마네
        { hair: '#e8e6f0', style: 'long', eye: '#b090d8', top: '#f4f4f6', skirt: '#f4f4f6', acc: '#1e1a20', frill: 1, frillCol: '#2a2228', obi: '#2a2228', orn: '#1e1a20', orb: '#e0d0ff' } // 미코
      ]
    ];
  })();
  var MERC_ICON = ['⚔️', '🏹', '🔮'];
  var EL_ICON = { 6: '🔥', 2: '⚡', 3: '💧' };
  var MERC_SK_ICON = ['🗡️', '🏹', '🔮']; // [2026-11-06] 용병 평타 아이콘
  var MERC_DMG_COL = { 6: '#ff6a2a', 2: '#ffe14d', 3: '#5ab0ff' }; // [2026-11-06] 동료 피해 숫자 속성 색(불 주황빨강 / 번개 노랑 / 물 파랑) + 속성 아이콘
  var MERC_DV = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] };
  var merc = { x: null, y: null, lt: 0, dir: 'down', mv: false, atkT: -1e9, tg: null, trail: [], map: '' }, mercShop = null, mercSpr = {}, mercImgC = {};
  function mercLook(t, ni) {
    var b = MERC_BASE[t] || MERC_BASE[0], h = MERC_HAIR[(ni | 0) % MERC_HAIR.length], lk = {};
    for (var p in b) lk[p] = b[p];
    lk.hair = h; lk.hat = h + '|neko'; lk.tail = h; lk.job = t | 0;
    var hp = MERC_HD[t | 0] || MERC_HD[0], hs = hp[(ni | 0) % hp.length]; // [2026-11-06] 초상화 기반 64x64 모습
    if (hs) { lk.hd = {}; for (var q in hs) lk.hd[q] = hs[q]; lk.hd.job = t | 0; lk.hd.ears = 0; } // [2026-11-06] "SD 캐릭터는 귀 없는 버전으로만"
    return lk;
  }
  // [2026-11-06] "도트 해상도를 32x32 정도로" -- 용병은 MineChar.neko(32x32 고양이 소녀)로 그린다
  // [2026-11-06] "초상화 기반으로 다시, 64x64" -- MineChar.nekoHD(초상화마다 머리색/모양/옷/귀), 없으면 예전 32x32
  function mercCanvas(dir, step, dig, lk) { return lk.hd && MineChar.nekoHD ? MineChar.nekoHD(dir, step, dig, lk.hd) : MineChar.neko ? MineChar.neko(dir, step, dig, lk) : MineChar.sprite(dir, step, dig, lk); }
  function mercSprite(t, dir, step, dig, k) {
    var key = t + dir + step + '_' + dig + '_' + (k | 0);
    if (!mercSpr[key]) mercSpr[key] = mercCanvas(dir, step, dig, mercLook(t, k));
    return mercSpr[key];
  }
  // [2026-11-06] "동료 이미지를 받아 소스에 저장하고 초상화 보기 / 캐릭터 보기 버튼(기본 캐릭터)" -- S5 동료(같은 이름) 초상화를 img_loa/s55/merc/{직업}_{이름 번호}.jpg로.
  var mercView = (function () { try { return localStorage.getItem('tw-merc-view') === 'portrait' ? 'portrait' : 'char'; } catch (e) { return 'char'; } })();
  function mercViewGo(v) { mercView = v === 'portrait' ? 'portrait' : 'char'; try { localStorage.setItem('tw-merc-view', mercView); } catch (e) { } if (smithOpen) renderSmith(smithNpc); }
  function mercViewBtns() { return '<div class="merc-view"><button type="button" class="' + (mercView === 'char' ? 'on' : '') + '" onclick="TowerMine.mercView(\'char\')">🧍 캐릭터 보기</button><button type="button" class="' + (mercView === 'portrait' ? 'on' : '') + '" onclick="TowerMine.mercView(\'portrait\')">🖼️ 초상화 보기</button></div>'; }
  function mercFaceImg(t, ni, alt) {
    if (mercView === 'portrait') return '<img class="merc-portrait" src="' + cfg.base.replace(/\/loa$/, '') + '/img_loa/s55/merc/' + (t | 0) + '_' + (ni | 0) + '.jpg?v=3" alt="' + (alt || '') + '" loading="lazy">';
    return '<img src="' + mercImg(t, ni) + '" alt="' + (alt || '') + '">';
  }
  function mercImg(t, k, dir) { var key = t + '_' + (k | 0) + (dir || 'down'); if (!mercImgC[key]) mercImgC[key] = mercCanvas(dir || 'down', 0, 0, mercLook(t, k)).toDataURL(); return mercImgC[key]; }
  // [2026-11-06] "이펙트가 중요해" -- 속성(불/번개/물) x 직업 x 단계(20레벨마다):
  //  전사 = 속성 색 베기 + 검기 파동(단계만큼 퍼짐), 궁수 = 불화살(3단계부터 하늘에서 불비) / 번개 화살 / 물화살,
  //  마법사 = 화염구(3단계부터 메테오) + 폭발 / 체인 라이트닝(튕긴 순서대로) / 토네이도(나 -> 대상으로 지나가며 휩쓴 뒤 그 자리에서 휘몰아침).
  var mercFx = [];
  var EL_COL = { 6: ['#fff2a8', '#ff9a20', '#e8401a'], 2: ['#ffffff', '#fff27a', '#ffc21a'], 3: ['#e8f8ff', '#6ac8ff', '#2a6ad0'] }; // [2026-11-06] 번개는 노랑(물 파랑과 구별)
  function elc(el) { return EL_COL[el] || EL_COL[6]; }
  // [2026-11-06] k = 그림 종류: 'ball' 마법사 평타 파이어볼(속성 색), 'sword' 전사 2차 이기어검, 'skill' 마법사 2차(속성: 불 메테오 / 번개 체인 / 물 토네이도). 궁수 2차 화살비는 mercRains(땅에 남는다).
  function mercFly(f) { return f.k === 'ball' ? 300 : f.k === 'sword' ? 620 : f.c === 0 ? 0 : f.c === 1 ? 230 : f.el === 6 ? (f.s >= 3 ? 460 : 320) : f.el === 2 ? 40 : 440; }
  function mercShot(c, el, st0, fx, fy, tx, ty, sp, now, k, tid) {
    mercFx.push({ c: c | 0, el: el | 0 || 6, s: st0 | 0 || 1, x0: fx, y0: fy, x1: tx, y1: ty, sp: sp || [], t0: now, k: k || ((c | 0) === 2 ? 'ball' : ''), tid: tid });
    if (mercFx.length > 24) mercFx.shift();
  }
  // [2026-11-06] "원거리 공격이 허공에 쏘는 느낌 -- 상대 이미지에 잘 맞게" -- 서버가 준 칸 대신, 그 몬스터가 지금 화면에 그려진 자리(걷는 중 보간, 겹침 비껴 그리기, 보스 2칸 가운데)로.
  function mobAt(id, fx, fy) {
    var mb = id != null && map && map.mobs ? map.mobs[id] : null, p = mb && mb._p;
    if (!p) return [fx, fy];
    var ty = map.mobTypes[mb.k] || {}, ms = mobSt[id], jx = ms && ms.fu > nowSrv() && !ty.boss ? ((mb.id % 5) - 2) / 16 : 0, jy = ms && ms.fu > nowSrv() && !ty.boss ? ((((mb.id / 5) | 0) % 3) - 1) / 16 : 0;
    return ty.boss ? [p.x + 0.5, p.y + 0.3] : [p.x + jx, p.y + jy - 0.15];
  }
  var mercRains = [];
  function mercRainDraw(now, TS, camX, camY) { // 화살비 -- 둘레 r칸 땅에 화살이 계속 꽂힌다(끝날 때까지)
    var tn = nowSrv();
    mercRains = mercRains.filter(function (r) { return r.until > tn; });
    mercRains.forEach(function (r) {
      var C = elc(r.el), s = TS / 16, cx = r.x * TS - camX + TS / 2, cy = r.y * TS - camY + TS / 2, R2 = (r.r + 0.5) * TS, left = Math.min(1, (r.until - tn) / 600);
      ctx.save();
      ctx.globalAlpha = 0.16 * left; ctx.fillStyle = C[2]; ctx.beginPath(); ctx.ellipse(cx, cy, R2, R2 * 0.75, 0, 0, Math.PI * 2); ctx.fill();
      ctx.globalAlpha = 0.5 * left; ctx.strokeStyle = C[1]; ctx.lineWidth = 1.2 * s; ctx.setLineDash([4 * s, 3 * s]); ctx.beginPath(); ctx.ellipse(cx, cy, R2, R2 * 0.75, 0, 0, Math.PI * 2); ctx.stroke(); ctx.setLineDash([]);
      var n = 6 + r.r * 4;
      for (var i = 0; i < n; i++) {
        var ph = ((now - r.t0) / 520 + i * 0.618) % 1, slot = Math.floor((now - r.t0) / 520 + i * 0.618);
        var hx = Math.sin(slot * 12.9898 + i * 4.1) * 43758.5453, hy = Math.sin(slot * 78.233 + i * 2.7) * 12345.678;
        hx = (hx - Math.floor(hx)) * 2 - 1; hy = (hy - Math.floor(hy)) * 2 - 1;
        var gx = cx + hx * R2 * 0.9, gy = cy + hy * R2 * 0.65;
        if (ph < 0.55) { var q = ph / 0.55, ax = gx, ay = gy - (1 - q) * 3.2 * TS; // [2026-11-06] "화살비는 수직으로"
          ctx.globalAlpha = left; ctx.strokeStyle = '#c8a070'; ctx.lineWidth = 1.2 * s; ctx.beginPath(); ctx.moveTo(ax, ay - 0.9 * TS); ctx.lineTo(ax, ay); ctx.stroke();
          fxDot(ax, ay, 1.6 * s, C[1], left); fxDot(ax - 1.2 * s, ay - 0.85 * TS, 1 * s, C[0], 0.6 * left); fxDot(ax + 1.2 * s, ay - 0.85 * TS, 1 * s, C[0], 0.6 * left);
        } else { var q2 = (ph - 0.55) / 0.45; ctx.globalAlpha = left * (1 - q2); ctx.strokeStyle = '#8a6a40'; ctx.lineWidth = 1.2 * s; ctx.beginPath(); ctx.moveTo(gx, gy); ctx.lineTo(gx, gy - 0.4 * TS); ctx.stroke(); fxBurst(gx, gy, r.el, q2, 4, s); }
      }
      ctx.restore();
    });
  }
  function mercFxDraw(now, TS, camX, camY) {
    try { mercRainDraw(now, TS, camX, camY); } catch (e) { }
    mercFx = mercFx.filter(function (f) { return now - f.t0 < mercFly(f) + 520; });
    mercFx.forEach(function (f) {
      if (now < f.t0) return;
      ctx.save();
      try { mercFxOne(f, now, TS, camX, camY); } catch (e) { }
      ctx.restore();
    });
  }
  function fxDot(x, y, r, col, a) { ctx.globalAlpha = a == null ? 1 : Math.max(0, a); ctx.fillStyle = col; ctx.beginPath(); ctx.arc(x, y, Math.max(0.5, r), 0, Math.PI * 2); ctx.fill(); }
  function fxBolt(x0, y0, x1, y1, seed, amp, C, w) { // 지그재그 번개 한 줄(seed가 같으면 같은 모양)
    var n = 7, pts = [[x0, y0]];
    for (var i = 1; i < n; i++) { var r = Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453; r = (r - Math.floor(r)) * 2 - 1; var q = i / n, nx = -(y1 - y0), ny = x1 - x0, l = Math.hypot(nx, ny) || 1; pts.push([x0 + (x1 - x0) * q + nx / l * r * amp, y0 + (y1 - y0) * q + ny / l * r * amp]); }
    pts.push([x1, y1]);
    [[w * 2.4, C[2], 0.45], [w * 1.3, C[1], 0.9], [w * 0.6, '#ffffff', 1]].forEach(function (L) {
      ctx.globalAlpha = L[2]; ctx.strokeStyle = L[1]; ctx.lineWidth = L[0]; ctx.lineJoin = 'round';
      ctx.beginPath(); ctx.moveTo(pts[0][0], pts[0][1]); for (var j = 1; j < pts.length; j++) ctx.lineTo(pts[j][0], pts[j][1]); ctx.stroke();
    });
  }
  function fxBurst(x, y, el, q, rad, s) { // 맞은 자리: 불 = 불티가 튐 / 번개 = 별 모양 스파크 / 물 = 물보라 고리
    var C = elc(el), a = 1 - q;
    if (el === 6) { for (var i = 0; i < 9; i++) { var an = i * 0.7 + 0.4, d = (2 + q * rad) * s; fxDot(x + Math.cos(an) * d, y + Math.sin(an) * d - q * 4 * s, (2.2 - q * 1.6) * s, i % 2 ? C[1] : C[0], a); } }
    else if (el === 2) { ctx.globalAlpha = a; ctx.strokeStyle = C[1]; ctx.lineWidth = 1.6 * s; for (var r = 0; r < 8; r++) { var a2 = r * Math.PI / 4 + 0.2, l1 = (r % 2 ? 2 : 3 + q * rad) * s; ctx.beginPath(); ctx.moveTo(x + Math.cos(a2) * 1.5 * s, y + Math.sin(a2) * 1.5 * s); ctx.lineTo(x + Math.cos(a2) * l1 * 2, y + Math.sin(a2) * l1 * 2); ctx.stroke(); } fxDot(x, y, 2.5 * s, '#ffffff', a); }
    else { ctx.globalAlpha = a; ctx.strokeStyle = C[1]; ctx.lineWidth = 1.5 * s; ctx.beginPath(); ctx.ellipse(x, y + 2 * s, (3 + q * rad) * s, (1.5 + q * rad * 0.45) * s, 0, 0, Math.PI * 2); ctx.stroke(); for (var w2 = 0; w2 < 6; w2++) { var an2 = -Math.PI * (0.15 + w2 * 0.14); fxDot(x + Math.cos(an2) * (2 + q * rad) * s, y + Math.sin(an2) * (3 + q * rad * 0.8) * s + q * q * 6 * s, 1.3 * s, C[0], a); } }
  }
  function mercFxOne(f, now, TS, camX, camY) {
    var C = elc(f.el), s = TS / 16, fly = mercFly(f), age = now - f.t0, k = fly ? age / fly : 1;
    var P2 = function (x, y) { return [x * TS - camX + TS / 2, y * TS - camY + TS / 2]; };
    var TP = mobAt(f.tid, f.x1, f.y1), E = P2(TP[0], TP[1]), S0 = P2(f.x0, f.y0), ex = E[0], ey = E[1], sx = S0[0], sy = S0[1];
    if (f.k === 'ball') { // [2026-11-06] 마법사 평타 -- "속성별로 파이어볼 / 라이트닝볼 / 아이스볼"
      if (k < 1) {
        var bx0 = sx + (ex - sx) * k, by0 = sy + (ey - sy) * k, ux0 = (ex - sx) / (Math.hypot(ex - sx, ey - sy) || 1), uy0 = (ey - sy) / (Math.hypot(ex - sx, ey - sy) || 1);
        if (f.el === 2) { // 라이트닝볼 -- 지지직 튀는 전기 구슬
          for (var lb = 0; lb < 4; lb++) { var la = age / 45 + lb * 1.57; fxBolt(bx0, by0, bx0 + Math.cos(la) * 9 * s, by0 + Math.sin(la) * 9 * s, Math.floor(age / 40) + lb * 3, 1.6 * s, C, 0.6 * s); }
          for (var ls = 1; ls <= 4; ls++) fxDot(bx0 - ux0 * ls * 3.5 * s + Math.sin(age / 15 + ls * 3) * 2 * s, by0 - uy0 * ls * 3.5 * s + Math.cos(age / 17 + ls) * 2 * s, 1.1 * s, ls % 2 ? '#ffffff' : C[1], 1 - ls * 0.2);
        } else if (f.el === 3) { // 아이스볼 -- 얼음 결정 + 서리 조각 꼬리
          for (var ib = 1; ib <= 5; ib++) { var ix = bx0 - ux0 * ib * 3 * s + Math.sin(ib * 2.3) * 1.5 * s, iy = by0 - uy0 * ib * 3 * s + Math.cos(ib * 1.7) * 1.5 * s, isz = (2.2 - ib * 0.3) * s;
            ctx.globalAlpha = 1 - ib * 0.17; ctx.fillStyle = ib % 2 ? '#ffffff' : '#bfefff'; ctx.beginPath(); ctx.moveTo(ix, iy - isz); ctx.lineTo(ix + isz * 0.6, iy); ctx.lineTo(ix, iy + isz); ctx.lineTo(ix - isz * 0.6, iy); ctx.fill(); }
        } else for (var bt = 5; bt >= 1; bt--) fxDot(bx0 - ux0 * bt * 3 * s + Math.sin(age / 25 + bt) * s, by0 - uy0 * bt * 3 * s, (3.4 - bt * 0.45) * s, bt < 2 ? C[0] : bt < 4 ? C[1] : C[2], 1 - bt * 0.15); // 파이어볼 불꽃 꼬리
        var BC = f.el === 3 ? ['#bfeaff', '#4ab0f0', '#2a6ad0'] : C, bg = ctx.createRadialGradient(bx0, by0, 0, bx0, by0, 5.5 * s);
        bg.addColorStop(0, '#ffffff'); bg.addColorStop(0.35, BC[0]); bg.addColorStop(0.7, BC[1]); bg.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.globalAlpha = 1; ctx.fillStyle = bg; ctx.beginPath(); ctx.arc(bx0, by0, 5.5 * s, 0, Math.PI * 2); ctx.fill();
        if (f.el === 3) { ctx.save(); ctx.translate(bx0, by0); ctx.rotate(age / 120); ctx.globalAlpha = 0.9; ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 0.8 * s; ctx.strokeRect(-2.4 * s, -2.4 * s, 4.8 * s, 4.8 * s); ctx.restore(); }
      } else if (f.el === 3) { // 아이스볼 -- 맞으면 얼음 조각이 흩어진다
        var iq = Math.min(1, (age - fly) / 320);
        for (var sh = 0; sh < 8; sh++) { var sa = sh * 0.785 + 0.3, sd = (2 + iq * 9) * s, sxx = ex + Math.cos(sa) * sd, syy = ey + Math.sin(sa) * sd + iq * iq * 4 * s, ss = 1.8 * s;
          ctx.globalAlpha = 1 - iq; ctx.fillStyle = sh % 2 ? '#ffffff' : '#9fe0ff'; ctx.beginPath(); ctx.moveTo(sxx, syy - ss); ctx.lineTo(sxx + ss * 0.6, syy); ctx.lineTo(sxx, syy + ss); ctx.lineTo(sxx - ss * 0.6, syy); ctx.fill(); }
        fxDot(ex, ey, (4 - iq * 3) * s, '#e8f8ff', 1 - iq);
      } else fxBurst(ex, ey, f.el, Math.min(1, (age - fly) / 320), 9, s);
      return;
    }
    if (f.k === 'sword') { // [2026-11-06] 전사 2차 -- 이기어검: 빛나는 검이 하나씩 휘어 날아가 꽂힌다
      var pts = f.sp && f.sp.length ? f.sp : [[f.x1, f.y1]];
      pts.forEach(function (q, i) {
        var kk = (age - i * 45) / 260;
        if (kk < 0) return;
        var Q2 = mobAt(q[2], q[0], q[1]), E2 = P2(Q2[0], Q2[1]);
        if (kk < 1) {
          var side = i % 2 ? 1 : -1, cx2 = sx + (E2[0] - sx) * kk - Math.sin(kk * Math.PI) * (E2[1] - sy) * 0.25 * side, cy2 = sy + (E2[1] - sy) * kk + Math.sin(kk * Math.PI) * (E2[0] - sx) * 0.25 * side - Math.sin(kk * Math.PI) * TS * 0.6;
          var ang = Math.atan2(E2[1] - cy2, E2[0] - cx2);
          ctx.save(); ctx.translate(cx2, cy2); ctx.rotate(ang);
          ctx.globalAlpha = 0.35; ctx.fillStyle = C[1]; ctx.fillRect(-14 * s, -2.5 * s, 22 * s, 5 * s);
          ctx.globalAlpha = 1; ctx.fillStyle = '#eef4fb'; ctx.beginPath(); ctx.moveTo(10 * s, 0); ctx.lineTo(-3 * s, -1.6 * s); ctx.lineTo(-3 * s, 1.6 * s); ctx.fill();
          ctx.fillStyle = C[0]; ctx.fillRect(-3 * s, -0.4 * s, 12 * s, 0.8 * s);
          ctx.fillStyle = '#ffd23f'; ctx.fillRect(-4.5 * s, -3 * s, 1.5 * s, 6 * s);
          ctx.fillStyle = '#5a3a1a'; ctx.fillRect(-8.5 * s, -0.9 * s, 4 * s, 1.8 * s);
          ctx.restore();
        } else fxBurst(E2[0], E2[1], f.el, Math.min(1, (kk - 1) * 260 / 300), 8, s);
      });
      return;
    }
    if (f.c === 0) { // 전사: 속성 베기(검기 칼날은 플레이어 검기 그림 auraFx로 -- mercApply)
      var a = Math.min(1, age / 150), al = 1 - Math.max(0, (age - 150) / 320);
      if (f.k !== 'ring') {
        if (al > 0) {
          ctx.globalAlpha = al; ctx.lineCap = 'round';
          ctx.strokeStyle = C[2]; ctx.lineWidth = 5 * s; ctx.beginPath(); ctx.arc(ex - 2 * s, ey + 2 * s, TS * 0.6, -2.4, -2.4 + 2.3 * a); ctx.stroke();
          ctx.strokeStyle = C[0]; ctx.lineWidth = 2.2 * s; ctx.beginPath(); ctx.arc(ex - 2 * s, ey + 2 * s, TS * 0.6, -2.4, -2.4 + 2.3 * a); ctx.stroke();
        }
        return;
      }
      if (al > 0) {
        ctx.globalAlpha = al; ctx.lineCap = 'round';
        ctx.strokeStyle = C[2]; ctx.lineWidth = 5 * s; ctx.beginPath(); ctx.arc(ex - 2 * s, ey + 2 * s, TS * 0.6, -2.4, -2.4 + 2.3 * a); ctx.stroke();
        ctx.strokeStyle = C[0]; ctx.lineWidth = 2.2 * s; ctx.beginPath(); ctx.arc(ex - 2 * s, ey + 2 * s, TS * 0.6, -2.4, -2.4 + 2.3 * a); ctx.stroke();
      }
      var q = Math.min(1, age / 460), rr = (f.s + 0.5) * TS * (0.25 + 0.75 * q);
      ctx.globalAlpha = (1 - q) * 0.85; ctx.strokeStyle = C[1]; ctx.lineWidth = (2 + f.s * 0.6) * s;
      ctx.beginPath(); ctx.arc(ex, ey, rr, 0, Math.PI * 2); ctx.stroke();
      for (var i = 0; i < 6 + f.s * 2; i++) { var an = i / (6 + f.s * 2) * Math.PI * 2 + age / 300; fxDot(ex + Math.cos(an) * rr, ey + Math.sin(an) * rr, 1.6 * s, f.el === 3 ? C[0] : C[1], 1 - q); }
      if (f.el === 2 && age < 260) for (var b = 0; b < Math.min(4, f.s + 1); b++) { var bn = b * 1.7 + 0.5; fxBolt(ex, ey, ex + Math.cos(bn) * rr, ey + Math.sin(bn) * rr, b + Math.floor(age / 60), 3 * s, C, 1.2 * s); }
      return;
    }
    if (f.c === 1) { // 궁수
      if (f.k === 'firerain') { // (예전 불비 -- 이제 궁수 2차 화살비가 mercRains로)
        var n = 2 + f.s;
        for (var r = 0; r < n; r++) {
          var t0 = r * 55, ra = (age - t0) / 260;
          if (ra < 0 || ra > 2.2) continue;
          var ox = ((r * 37) % 5 - 2) * 0.45 * TS, oy = ((r * 53) % 3 - 1) * 0.35 * TS, tx = ex + ox, ty = ey + oy;
          if (ra < 1) {
            var hx = tx + (1 - ra) * 2.2 * TS, hy = ty - (1 - ra) * 6 * TS, gr = ctx.createLinearGradient(hx + 1.1 * TS, hy - 3 * TS, hx, hy);
            gr.addColorStop(0, 'rgba(232,64,26,0)'); gr.addColorStop(1, C[1]);
            ctx.globalAlpha = 1; ctx.strokeStyle = gr; ctx.lineWidth = 2.5 * s; ctx.lineCap = 'round';
            ctx.beginPath(); ctx.moveTo(hx + 1.1 * TS, hy - 3 * TS); ctx.lineTo(hx, hy); ctx.stroke();
            fxDot(hx, hy, 2.4 * s, C[0], 1);
          } else fxBurst(tx, ty, 6, (ra - 1) / 1.2, 8, s);
        }
        return;
      }
      if (k < 1) {
        var cx = sx + (ex - sx) * k, cy = sy + (ey - sy) * k - Math.sin(k * Math.PI) * TS * 0.3, ang = Math.atan2(ey - sy, ex - sx) + (k - 0.5) * -0.5;
        var bx = Math.cos(ang), by = Math.sin(ang);
        if (f.el === 6) for (var t1 = 1; t1 <= 6; t1++) fxDot(cx - bx * t1 * 2.6 * s + Math.sin(age / 30 + t1) * s, cy - by * t1 * 2.6 * s, (3 - t1 * 0.35) * s, t1 < 3 ? C[0] : t1 < 5 ? C[1] : C[2], 1 - t1 * 0.13);
        else if (f.el === 2) fxBolt(cx - bx * 14 * s, cy - by * 14 * s, cx, cy, Math.floor(age / 40), 2.5 * s, C, 1 * s);
        else for (var t2 = 1; t2 <= 5; t2++) fxDot(cx - bx * t2 * 3 * s, cy - by * t2 * 3 * s + Math.sin(t2 * 2 + age / 50) * 1.5 * s, (1.8 - t2 * 0.2) * s, t2 % 2 ? C[0] : C[1], 0.9 - t2 * 0.12);
        ctx.globalAlpha = 1; ctx.translate(cx, cy); ctx.rotate(ang);
        ctx.fillStyle = '#c8a070'; ctx.fillRect(-6 * s, -0.6 * s, 10 * s, 1.2 * s);
        ctx.fillStyle = C[1]; ctx.beginPath(); ctx.moveTo(7 * s, 0); ctx.lineTo(3 * s, -2.4 * s); ctx.lineTo(3 * s, 2.4 * s); ctx.fill();
        ctx.fillStyle = C[2]; ctx.fillRect(-7 * s, -2.2 * s, 2.4 * s, 1.4 * s); ctx.fillRect(-7 * s, 0.8 * s, 2.4 * s, 1.4 * s);
      } else fxBurst(ex, ey, f.el, Math.min(1, (age - fly) / 320), 7, s);
      return;
    }
    // 마법사
    if (f.el === 2) { // 체인 라이트닝 -- 나 -> 대상 -> 튕긴 순서
      if (age > 420) return;
      var nodes = [[sx, sy], [ex, ey]].concat((f.sp || []).map(function (q2) { var Q3 = mobAt(q2[2], q2[0], q2[1]); return P2(Q3[0], Q3[1]); })), fade = age < 260 ? 1 : 1 - (age - 260) / 160;
      var shown = Math.min(nodes.length - 1, 1 + Math.floor(age / 45));
      for (var c2 = 0; c2 < shown; c2++) { ctx.globalAlpha = fade; fxBolt(nodes[c2][0], nodes[c2][1], nodes[c2 + 1][0], nodes[c2 + 1][1], c2 * 7 + Math.floor(age / 50), 0.22 * TS, C, 1.6 * s); }
      for (var c3 = 1; c3 <= shown; c3++) { fxDot(nodes[c3][0], nodes[c3][1], (4 + Math.sin(age / 25 + c3) * 1.5) * s, 'rgba(168,220,255,.55)', fade); fxDot(nodes[c3][0], nodes[c3][1], 2 * s, '#ffffff', fade); }
      return;
    }
    if (f.el === 3) { // 토네이도 -- 길을 지나가며 휩쓸고 대상 자리에서 휘몰아친다
      var stay = age > fly, q3 = stay ? Math.min(1, (age - fly) / 380) : 0, kk = Math.min(1, k);
      var tx3 = sx + (ex - sx) * kk, ty3 = sy + (ey - sy) * kk, wide = stay ? 1 + ((f.s + 1) >> 1) * 0.55 * Math.sin(q3 * Math.PI) : 1, al3 = stay ? 1 - q3 * q3 : 1;
      for (var L = 0; L < 7; L++) {
        var hh = L / 6, w3 = (3 + hh * 9) * s * wide, yy = ty3 + 6 * s - hh * 1.5 * TS, xx = tx3 + Math.sin(age / 70 + L * 0.9) * (2 + hh * 3) * s;
        ctx.globalAlpha = al3 * (0.35 + 0.1 * L); ctx.strokeStyle = L % 2 ? C[1] : C[0]; ctx.lineWidth = (1.4 + hh) * s;
        ctx.beginPath(); ctx.ellipse(xx, yy, w3, w3 * 0.32, 0, Math.PI * (0.05 + (age / 160 + L) % 1), Math.PI * (1.6 + (age / 160 + L) % 1)); ctx.stroke();
      }
      for (var d3 = 0; d3 < 5; d3++) { var an3 = age / 90 + d3 * 1.26; fxDot(tx3 + Math.cos(an3) * 11 * s * wide, ty3 - (d3 * 4) * s + Math.sin(an3) * 3 * s, 1.4 * s, C[0], al3); }
      return;
    }
    // 불: 화염구 / 메테오(3단계부터) -> 폭발(단계만큼)
    var big = f.s >= 3;
    if (k < 1) {
      var fx0 = big ? ex + 3 * TS : sx, fy0 = big ? ey - 7 * TS : sy, mx = fx0 + (ex - fx0) * k * (big ? k : 1), my = fy0 + (ey - fy0) * k * (big ? k : 1);
      var dx = ex - fx0, dy = ey - fy0, dl = Math.hypot(dx, dy) || 1, ux = dx / dl, uy = dy / dl;
      for (var t4 = 7; t4 >= 1; t4--) fxDot(mx - ux * t4 * (big ? 5 : 3) * s + Math.sin(age / 25 + t4) * s, my - uy * t4 * (big ? 5 : 3) * s, ((big ? 6 : 3.4) - t4 * (big ? 0.6 : 0.35)) * s, t4 < 3 ? C[0] : t4 < 5 ? C[1] : C[2], 1 - t4 * 0.11);
      if (big) { fxDot(mx, my, 5.5 * s, '#5a2a1a', 1); fxDot(mx - 1.5 * s, my - 1.5 * s, 2.5 * s, '#8a4a2a', 1); }
      var g4 = ctx.createRadialGradient(mx, my, 0, mx, my, (big ? 9 : 5) * s);
      g4.addColorStop(0, 'rgba(255,242,168,.95)'); g4.addColorStop(0.5, 'rgba(255,154,32,.6)'); g4.addColorStop(1, 'rgba(232,64,26,0)');
      ctx.globalAlpha = 1; ctx.fillStyle = g4; ctx.beginPath(); ctx.arc(mx, my, (big ? 9 : 5) * s, 0, Math.PI * 2); ctx.fill();
      return;
    }
    var q5 = Math.min(1, (age - fly) / (big ? 480 : 380)), R5 = (f.s + 0.5) * TS * (0.3 + 0.7 * Math.sqrt(q5));
    var g5 = ctx.createRadialGradient(ex, ey, 0, ex, ey, R5);
    g5.addColorStop(0, 'rgba(255,242,168,.95)'); g5.addColorStop(0.45, 'rgba(255,154,32,.65)'); g5.addColorStop(1, 'rgba(232,64,26,0)');
    ctx.globalAlpha = 1 - q5; ctx.fillStyle = g5; ctx.beginPath(); ctx.arc(ex, ey, R5, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = C[0]; ctx.lineWidth = (big ? 3 : 1.8) * s; ctx.beginPath(); ctx.arc(ex, ey, R5 * 0.95, 0, Math.PI * 2); ctx.stroke();
    if (big) { ctx.globalAlpha = (1 - q5) * 0.6; ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 1.5 * s; ctx.beginPath(); ctx.arc(ex, ey, R5 * 1.25, 0, Math.PI * 2); ctx.stroke(); }
    fxBurst(ex, ey, 6, q5, 10 + f.s * 3, s);
  }
  function mercBehind(x, y, dir) { var v = MERC_DV[dir] || MERC_DV.down; return walk(x - v[0], y - v[1]) ? [x - v[0], y - v[1]] : [x, y]; }
  function mercBeside(tx, ty, fx, fy, px, py) {
    var b = null, bd = 1e9;
    [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(function (v) {
      var nx = tx + v[0], ny = ty + v[1];
      if (!walk(nx, ny) || (nx === px && ny === py)) return;
      var d = Math.abs(nx - fx) + Math.abs(ny - fy);
      if (d < bd) { bd = d; b = [nx, ny]; }
    });
    return b || [tx, ty];
  }
  function mercMove(mp, gx, gy, now) {
    var dt = Math.min(0.1, (now - (mp.lt || now)) / 1000);
    mp.lt = now;
    if (mp.x == null || Math.abs(gx - mp.x) + Math.abs(gy - mp.y) > 14) { mp.x = gx; mp.y = gy; mp.mv = false; return; }
    var dx = gx - mp.x, dy = gy - mp.y, d = Math.hypot(dx, dy);
    if (d < 0.03) { mp.x = gx; mp.y = gy; mp.mv = false; return; }
    mp.mv = true;
    mp.dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
    var k = Math.min(1, (d > 3 ? 13 : 9) * dt / d); // [2026-11-07] 바짝 따라오게 조금 빨리
    mp.x += dx * k; mp.y += dy * k;
  }
  function mercFace(mp, tx, ty) { var dx = tx - mp.x, dy = ty - mp.y; mp.dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'); }
  function mercDraw(mp, t, rest, hpf, atkT, now, V, skin) {
    var x = Math.round(mp.x * V.TS - V.camX), y = Math.round(mp.y * V.TS - V.camY), sc = V.scale;
    if (x < -V.TS || y < -V.TS * 2 || x > V.W || y > V.H) return;
    ctx.fillStyle = 'rgba(0,0,0,.22)'; ctx.fillRect(x + 3 * sc, y + 13 * sc, 10 * sc, 3 * sc);
    var ad = now - atkT, dig = ad >= 0 && ad < 360 ? (ad < 180 ? 3 : 4) : 0;
    if (rest) ctx.globalAlpha = 0.55;
    var spr = mercSprite(t, mp.dir, mp.mv ? 1 + Math.floor(now / 150) % 2 : 0, dig, skin), sz = spr.width >= 64 ? Math.round(V.TS * 1.15) : V.TS; // [2026-11-06] 64x64 용병은 1.15배(발 기준)
    ctx.drawImage(spr, x - Math.round((sz - V.TS) / 2), y - 3 * sc - (sz - V.TS), sz, sz);
    ctx.globalAlpha = 1;
    if (rest) { ctx.font = Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.fillText('💤', x + V.TS * 0.8, y - 2 * sc - Math.sin(now / 300) * 2 * sc); }
    else if (hpf != null && hpf < 1) {
      var bw = 12 * sc;
      ctx.fillStyle = 'rgba(0,0,0,.6)'; ctx.fillRect(x + 2 * sc, y - 4 * sc, bw, 2 * sc);
      ctx.fillStyle = hpf > 0.5 ? '#7cf08a' : hpf > 0.25 ? '#ffd23f' : '#ff5a4a'; ctx.fillRect(x + 2 * sc, y - 4 * sc, bw * Math.max(0, hpf), 2 * sc);
    }
  }
  function mercDrawMine(now, V) {
    var mi = st && st.merc;
    if (!mi || !map || map.boat) return;
    if (merc.map !== map.id) { merc.map = map.id; merc.x = null; merc.trail = []; merc.tg = null; }
    var tr = merc.trail, l = tr[tr.length - 1];
    if (!l || l[0] !== P.x || l[1] !== P.y) { tr.push([P.x, P.y]); if (tr.length > 2) tr.shift(); } // [2026-11-07] "더 바짝 붙어서" -- 내가 한 걸음 전에 있던 칸
    var rest = mi.rest > nowSrv(), fight = !rest && merc.tg && now - merc.tg.t < 1800, g;
    if (rest) g = mercBehind(P.x, P.y, P.dir);
    else if (fight && (mi.t | 0) > 0) g = merc.x != null && Math.abs(Math.round(merc.x) - P.x) + Math.abs(Math.round(merc.y) - P.y) <= 2 && !(Math.round(merc.x) === P.x && Math.round(merc.y) === P.y) ? [Math.round(merc.x), Math.round(merc.y)] : mercBehind(P.x, P.y, P.dir); // [2026-11-06] 궁수/마법사 -- 제자리(내 곁)에서 쏜다
    else if (fight) g = merc.tg.g || (merc.tg.g = mercBeside(merc.tg.x, merc.tg.y, Math.round(merc.x == null ? P.x : merc.x), Math.round(merc.y == null ? P.y : merc.y), P.x, P.y));
    else g = tr.length >= 2 ? tr[0] : mercBehind(P.x, P.y, P.dir);
    if (g[0] === P.x && g[1] === P.y && !rest) g = mercBehind(P.x, P.y, P.dir);
    mercMove(merc, g[0], g[1], now);
    if (fight && !merc.mv) mercFace(merc, merc.tg.x, merc.tg.y);
    mercDraw(merc, mi.t | 0, rest, mi.max ? mi.hp / mi.max : null, merc.atkT, now, V, mi.ni);
  }
  function mercDrawOther(ou, op, now, V) {
    var mp = ou.mp || (ou.mp = { x: null, y: null, dir: 'down' }), ox = Math.round(op.x), oy = Math.round(op.y), g;
    var mc = ou.mc | 0;
    if (ou.mt && !ou.mr && mc > 0) { mp.gk = null; g = mp.x != null && Math.abs(Math.round(mp.x) - ox) + Math.abs(Math.round(mp.y) - oy) <= 2 && !(Math.round(mp.x) === ox && Math.round(mp.y) === oy) ? [Math.round(mp.x), Math.round(mp.y)] : mercBehind(ox, oy, ou.dir || 'down'); } // [2026-11-06] 원거리 직업
    else if (ou.mt && !ou.mr) {
      var gk = ou.mt[0] + ',' + ou.mt[1];
      if (mp.gk !== gk) { mp.gk = gk; mp.g = mercBeside(ou.mt[0], ou.mt[1], mp.x == null ? ox : Math.round(mp.x), mp.y == null ? oy : Math.round(mp.y), ox, oy); }
      g = mp.g;
    } else { mp.gk = null; g = mercBehind(ox, oy, ou.dir || 'down'); }
    mercMove(mp, g[0], g[1], now);
    if (ou.mt && !ou.mr && !mp.mv) mercFace(mp, ou.mt[0], ou.mt[1]);
    var at2 = ou.mt && !ou.mr ? now - now % 1000 : -1e9;
    if (at2 > 0 && mp.fxT !== at2 && mp.x != null) { mp.fxT = at2; mercShot(mc, ou.me, ou.ms, mp.x, mp.y, ou.mt[0], ou.mt[1], null, now); } // [2026-11-06] 다른 유저의 용병 이펙트(1초마다)
    mercDraw(mp, mc, ou.mr, null, at2, now, V, ou.ml);
  }
  /** 전투 턴 응답의 용병 소식 -- 체력/쉬기, 때린 몬스터(전사는 그 옆으로 달려감), 속성 이펙트, 피해 숫자, 드랍. [2026-11-06] 경험치는 내 것(문구에 용병 표시 없음). */
  function mercApply(m) {
    if (!st) return;
    st.merc = m;
    var t = performance.now(), mt = m.t | 0, f0 = { c: mt, el: m.el, s: m.st, k: mt === 2 ? 'ball' : '' }, fly = mercFly(f0), ei = EL_ICON[m.el] || '';
    var rowsAll = (m.row ? [m.row] : []).concat(m.rows || []), applyRows = function () { rowsAll.forEach(function (r) { mobSt[r[0]] = mobStOf(r, mobSt[r[0]]); }); }; // 둘레/체인 몬스터
    if (rowsAll.length) { var hitDelay = !m.sk ? fly + (mt ? 0 : 110) : m.sk.k === 'meteor' ? 600 : m.sk.k === 'tornado' ? 580 : m.sk.k === 'sword' ? 520 : Math.max(fly, 300); if (hitDelay > 30) setTimeout(applyRows, hitDelay); else applyRows(); } // [2026-11-06] 화살/구슬이 닿기 전에 몬스터가 먼저 쓰러져 사라지면 허공에 쏘는 것처럼 보였다
    if (m.tx != null) {
      if (!merc.tg || merc.tg.x !== m.tx || merc.tg.y !== m.ty) merc.tg = { x: m.tx, y: m.ty, t: t };
      else merc.tg.t = t;
      merc.atkT = t;
      mercShot(mt, m.el, m.st, merc.x == null ? P.x : merc.x, merc.y == null ? P.y : merc.y, m.tx, m.ty, m.sp, t + (mt ? 0 : 110), null, m.tg);
      if (mt === 0 && m.ms) merc.tg.g = [m.ms[0], m.ms[1]]; // [2026-11-06] 검사 용병이 서는 칸 = 서버가 검기를 내는 칸
      if (mt === 0 && m.adir && m.st > 0) { // [2026-11-06] 용병 검기 = 플레이어 검기 칼날(속성 색, 단계 칸만큼 앞으로)
        var adr = m.adir[0] > 0 ? 'right' : m.adir[0] < 0 ? 'left' : m.adir[1] < 0 ? 'up' : 'down';
        auraFx.push({ x: m.ms ? m.ms[0] : m.tx - m.adir[0], y: m.ms ? m.ms[1] : m.ty - m.adir[1], dir: adr, r: m.st, els: [m.el], rs: [m.st], t0: t, w: false, m: true });
      }
      // [2026-11-06] "동료가 실제 얼마 데미지를 무슨 속성(색)으로 줬는지 (동료 이모티콘)으로" -- 🐱 + 피해, 속성 색(불 주황 / 번개 노랑 / 물 파랑), 상성이면 크게 + !
      //  "메테오는 땅에 닿을 때 데미지" -- 2차 스킬이 나간 턴은 스킬이 닿는 때에 숫자(와 쓰러짐)
      var land = !m.sk ? fly + (mt ? 0 : 110) : m.sk.k === 'meteor' ? 600 : m.sk.k === 'tornado' ? 580 : m.sk.k === 'sword' ? 420 : m.sk.k === 'chain' ? 200 : fly, ecol = MERC_DMG_COL[m.el] || '#ffd27a';
      floats.push({ x: m.tx, y: m.ty - 0.3, text: '🐱' + ei + m.dmg + (m.beat ? '!' : ''), color: ecol, big: !!m.beat, t0: t + land });
      (m.sp || []).forEach(function (q, i) { floats.push({ x: q[0], y: q[1] - 0.3, text: '🐱' + ei + q[2], color: ecol, t0: t + land + (m.sk && (m.sk.k === 'sword' || m.sk.k === 'chain') ? 45 * (i + 1) : 40) }); });
    }
    if (m.sk) { // [2026-11-06] 2차 스킬(20초마다) -- 이름을 머리 위에, 이펙트
      var sk = m.sk, mx0 = merc.x == null ? P.x : merc.x, my0 = merc.y == null ? P.y : merc.y;
      if (sk.k === 'rain') mercRains.push({ x: sk.x, y: sk.y, r: sk.r, until: sk.until, el: m.el, t0: t });
      else if (sk.k === 'sword') mercShot(0, m.el, sk.s, mx0, my0, sk.x, sk.y, sk.pts, t + 120, 'sword');
      else if (sk.k === 'meteor') mercShot(2, 6, Math.max(3, sk.r), mx0, my0, sk.x, sk.y, null, t + 120, 'skill', m.tg);
      else if (sk.k === 'chain') mercShot(2, 2, sk.s, mx0, my0, sk.x, sk.y, sk.pts, t + 120, 'skill', m.tg);
      else if (sk.k === 'tornado') mercShot(2, 3, sk.r * 2 - 1, mx0, my0, sk.x, sk.y, null, t + 120, 'skill', m.tg);
      floats.push({ x: mx0, y: my0 - 1, text: '✨ ' + sk.nm, color: '#ffe9a8', big: true, t0: t });
    }
    if (m.bossMsg) { msg(m.bossMsg, 5000); killFeed(m.bossMsg, '#ffe14d'); } // [2026-11-07] 용병이 쓰러뜨린 보스
    if (m.shock) shockSet([]);
    if (m.got && merc.x != null) floats.push({ x: merc.x, y: merc.y - 0.3, text: '-' + m.got, color: '#ff9a9a', t0: t + 200 });
    if (m.down) { merc.tg = null; killFeed('💤 ' + (m.nm || m.name) + '이(가) 지쳐서 뒤에서 쉬어요', '#c9c3d6'); }
    if (m.kexp) killFeed('내 경험치 +' + m.kexp, '#9fe0ff'); // [2026-11-08] kexp = 이번 턴 용병 처치 경험치(exp는 용병이 모은 경험치라 처치 없이도 늘 있었다) // [2026-11-06] "동료가 벌어 준 경험치는 표기할 필요 없고, 동료가 획득한 / 내가 획득한 경험치로" -- 용병이 잡은 몬스터 경험치도 내 것
    if (m.ores) st.ores = m.ores;
    if (m.drops) lootDrops(m.drops, m.tx, m.ty);
    if (m.dropGrade != null || m.dropShard) weaponLoot(m, m.tx, m.ty);
  }
  /** [2026-11-06] 용병 경험치(내 경험치의 1%) -- 용병 관리 칸, 레벨 업 알림. */
  function mercXpApply(x) {
    if (!st || !x) return;
    if (st.mercs) st.mercs[x.t] = x;
    if (st.merc && st.merc.t === x.t) { for (var p in x) if (p !== 'gain' && p !== 'up') st.merc[p] = x[p]; }
    if (x.gain) killFeed('🐱 동료 경험치 +' + x.gain, '#d8a8ff'); // [2026-11-06] 동료가 획득한 경험치
    if (x.gain && merc.x != null) floats.push({ x: merc.x, y: merc.y - 0.6, text: '+' + x.gain, color: '#9fe0ff', t0: performance.now() + 150 }); // [2026-11-06] 용병 경험치(처치마다 최소 1)
    if (x.up) { killFeed('🎉 ' + (x.nm || x.name) + ' Lv.' + x.lv + '!', '#ffe14d'); if (merc.x != null) floats.push({ x: merc.x, y: merc.y - 1, text: '⬆️ Lv.' + x.lv, color: '#ffe14d', big: true, t0: performance.now() }); }
    if (smithOpen && smithView === 'mercmgr') renderSmith(smithNpc);
  }
  // [2026-11-06] "마물의 숲에서 몹이 뭉치면 렉" -- 캔버스에서 가장 비싼 일이 테두리 글자(strokeText). 이름표/피해 숫자를 작은 그림으로 한 번 그려 두고 drawImage로 다시 쓴다.
  //  share = true(이름표)는 같은 글자끼리 나눠 쓰고(600개 넘으면 비움), 피해 숫자는 그 숫자에만 붙여 둔다. (x, y) = 예전 textAlign center 글자의 기준선 자리.
  var textImgC = {}, textImgN = 0;
  function textImg(text, font, fill, lw, share) {
    var key = share ? font + '|' + fill + '|' + lw + '|' + text : null;
    if (key && textImgC[key]) return textImgC[key];
    var g0 = (textImg.m || (textImg.m = document.createElement('canvas').getContext('2d')));
    g0.font = font;
    var tw = g0.measureText(text).width, fs = parseInt((font.match(/(\d+)px/) || [0, 12])[1], 10), pad = Math.ceil(lw) + 2;
    var c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(tw + pad * 2)); c.height = Math.ceil(fs * 1.4 + pad * 2);
    var g = c.getContext('2d'), by = Math.round(pad + fs * 1.05);
    g.font = font; g.textAlign = 'center'; g.lineWidth = lw; g.strokeStyle = '#000';
    g.strokeText(text, c.width / 2, by); g.fillStyle = fill; g.fillText(text, c.width / 2, by);
    c._by = by; c._tw = tw;
    if (key) { if (++textImgN > 600) { textImgC = {}; textImgN = 1; } textImgC[key] = c; }
    return c;
  }
  function drawTextImg(c, x, y) { ctx.drawImage(c, Math.round(x - c.width / 2), Math.round(y - c._by)); }
  function myNick() { var u = (cfg && cfg.user) || ''; var i = u.indexOf('/'); return i > 0 ? u.slice(0, i) : u; }
  /** [2026-11-05] 캐릭터 머리 위 이름 -- 작은 글씨 + 검은 테두리. */
  function nameTag(text, x, y, col) {
    ctx.font = 'bold ' + Math.round(11 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
    ctx.lineWidth = 3 * dpr; ctx.strokeStyle = 'rgba(0,0,0,.85)'; ctx.strokeText(text, x, y);
    ctx.fillStyle = col || '#fff'; ctx.fillText(text, x, y);
  }
  var CIT_LINES = ['왕도 아스테리아에 오신 걸 환영해요!', '분수대 물이 오늘따라 맑네요~', '성당 신부님께 기도하면 여기서 깨어난대요.', '텔레포트 게이트가 공짜래요!',
    '동문 밖 길엔 와이번이 나온대요...', '초원 오크들이 요즘 부쩍 사나워졌어요.', '왕도 대장장이 솜씨가 최고예요!', '설산을 넘어오셨다고요? 대단해요!',
    '빵 사러 가는 길이에요~', '기사단이 순찰 중이니 안심하세요.', '어젯밤 별빛석이 떨어지는 걸 봤어요!', '광산에서 미스릴을 캤다는 소문이 있어요.',
    '오늘 장 서는 날인가요?', '서문은 왜 늘 닫혀 있을까요?'];
  var citCache = {};
  function citPos(route, i, t) {
    var SL = 1400, n = route.length, period = Math.max(1, (n - 1) * 2), s = Math.floor(t / SL) + mix(i, 99) % period, f = (t % SL) / SL;
    function at(k) { k = ((k % period) + period) % period; return route[k < n ? k : period - k]; }
    var a = at(s), b = at(s + 1), ax = a % map.w, ay = Math.floor(a / map.w), bx = b % map.w, by = Math.floor(b / map.w), k2 = Math.min(1, f / 0.8);
    var dir = bx > ax ? 'right' : bx < ax ? 'left' : by < ay ? 'up' : 'down';
    return { x: ax + (bx - ax) * k2, y: ay + (by - ay) * k2, dir: dir, moving: k2 < 1 && (ax !== bx || ay !== by) };
  }
  function citLook(i) {
    var HAIR = ['#2b1d16', '#7a4a26', '#f0d060', '#d8dde6', '#a83a2a', '#3a3a3a'],
        TOP = ['#c0392b|robe', '#2e8b57|robe', '#3f5fd1', '#f4f1e8|hanbok', '#7b3fa0', '#e0b030', '#3a3f4a|armor', '#8a5a32'],
        HAT = ['', '', '', '#e8c84a|straw', '#222630|pirate', '#9aa3ad|helmet', '#4b3a9a|wizard'], BOT = ['#1f1f24', '#e8e4da', '#a8323a', '#3a2b22', '#2c45a3'];
    return { gender: mix(i, 1) % 2, hair: HAIR[mix(i, 2) % HAIR.length], top: TOP[mix(i, 3) % TOP.length], hat: HAT[mix(i, 4) % HAT.length], bottom: BOT[mix(i, 5) % BOT.length], shoes: '' };
  }
  function citSprite(i, dir, step) {
    var k = i + dir + step;
    if (!citCache[k]) citCache[k] = MineChar.sprite(dir, step, 0, citLook(i));
    return citCache[k];
  }
  /** [2026-10-28] 대주교 마법 예고(불벼락 2초 전, 어둠가스 3초 전) + 위쪽 표시(깨어 있음/잠듦, 다음 마법까지 초). */
  function raidWarn() {
    if (!el.cold) return;
    var ts = nowSrv(), txt;
    if (!raid || !raid.awake) txt = '👿 대주교 대기 중'; // [2026-10-28] 짧게(체력 칸과 같은 폭)
    else {
      var lf = raid.nextFire - ts, lg = raid.nextGas - ts, boss = map.mobs && map.mobs[map.mobs.length - 1], bp = boss && boss._p;
      var far = bp ? Math.max(Math.abs(bp.cx - P.x), Math.abs(bp.cy - P.y)) > 12 : false;
      // [2026-10-28] 불벼락/어둠가스 예고 멘트는 없앴다(위 HUD에 남은 초)
      txt = '🔥' + Math.max(0, Math.ceil(lf / 1000)) + '초 ☠️' + Math.max(0, Math.ceil(lg / 1000)) + '초';
    }
    if (el.cold._t !== txt) { el.cold._t = txt; el.cold.textContent = txt; }
    if (el.cold.style.display !== 'block') el.cold.style.display = 'block';
  }
  /** [2026-10-28] 안전지대 경계선 -- "피해 안 받는 곳 음영 처리 더": 점선 원(천천히 돈다)으로 어디까지가 안전한지 보이게. */
  function safeRing(zx, zy, rr, col) {
    ctx.save(); ctx.strokeStyle = col; ctx.lineWidth = Math.max(2, 2 * dpr); ctx.setLineDash([6 * dpr, 5 * dpr]); ctx.lineDashOffset = -(performance.now() / 60) % 22;
    ctx.beginPath(); ctx.arc(zx, zy, rr - dpr, 0, Math.PI * 2); ctx.stroke(); ctx.restore();
  }
  /** [2026-10-28] 야자수(다도해 그늘 가운데) -- 줄기 + 바람에 살짝 흔들리는 잎. */
  function drawPalm(cx, cy, TS, now) {
    var s = TS / 16, sw = Math.sin(now / 700) * s;
    ctx.fillStyle = '#7a5530'; ctx.fillRect(cx - s, cy - 6 * s, 2 * s, 10 * s);
    ctx.fillStyle = '#2f8a4a';
    [[-7, -1], [7, -1], [-5, -4], [5, -4], [0, -5]].forEach(function (l) { ctx.beginPath(); ctx.moveTo(cx, cy - 7 * s); ctx.quadraticCurveTo(cx + l[0] * 0.5 * s + sw, cy + (l[1] - 9) * s, cx + l[0] * s + sw, cy + l[1] * s - 4 * s); ctx.lineTo(cx + l[0] * s * 0.8 + sw, cy + l[1] * s - 2 * s); ctx.closePath(); ctx.fill(); });
  }
  /** [2026-10-28] 1인용 배(다도해 뱃길) -- 캐릭터 발밑 나무 배, 물결에 살짝 흔들린다. (x, y) = 캐릭터 그림 왼쪽 위. */
  function drawBoat(x, y, now) {
    var TS = T * scale, u = TS / 16, bob = Math.round(Math.sin(now / 300) * u);
    ctx.fillStyle = '#5a3a1e'; ctx.fillRect(x - 2 * u, y + 11 * u + bob, TS + 4 * u, 3 * u);
    ctx.fillStyle = '#8a5a2b'; ctx.fillRect(x - u, y + 9 * u + bob, TS + 2 * u, 3 * u);
    ctx.fillStyle = '#c49a45'; ctx.fillRect(x, y + 9 * u + bob, TS, u);
    ctx.fillStyle = 'rgba(255,255,255,0.55)'; ctx.fillRect(x - 3 * u + ((now / 90) % 4) * u, y + 14 * u + bob, 2 * u, u); ctx.fillRect(x + TS + ((now / 110) % 3) * u, y + 14 * u + bob, 2 * u, u);
  }
  /** 모닥불(안전지대 가운데) -- 장작 + 일렁이는 불꽃. */
  function drawFire(cx, cy, TS, now) {
    var s = TS / 16, f = Math.sin(now / 90) * 0.6 + Math.sin(now / 53) * 0.6;
    ctx.fillStyle = '#5a3a1e'; ctx.fillRect(cx - 5 * s, cy + 3 * s, 10 * s, 2 * s); ctx.fillRect(cx - 3 * s, cy + 2 * s, 6 * s, s);
    ctx.fillStyle = '#ff7a1a'; ctx.beginPath(); ctx.moveTo(cx - 4 * s, cy + 3 * s); ctx.quadraticCurveTo(cx, cy - (8 + f) * s, cx + 4 * s, cy + 3 * s); ctx.fill();
    ctx.fillStyle = '#ffd23f'; ctx.beginPath(); ctx.moveTo(cx - 2 * s, cy + 3 * s); ctx.quadraticCurveTo(cx, cy - (3 + f) * s, cx + 2 * s, cy + 3 * s); ctx.fill();
  }
  /** 쓰러짐 -- 어둡게 했다가 깨어난 곳(평원 마을 / 왕도 성당)으로. */
  function fallDown(d) {
    setAuto(false);
    busy = true;
    fadeOutThen(function () { return Promise.resolve(d); }).then(function () { return enterState(d.state); })
      .then(function () { fadeIn('💫 깨어났어요'); msg(d.message || '', 3600); });
  }
  // [2026-10-24] 탐지 곡괭이 -- 멈춰 선 칸이 바뀌면 주변 3x3의 안 캔 광석을 물어 와서 칸 위에 아이콘으로 보여 준다
  var scan = null, scanKey = '', scanBusy = false, scanImgs = {};
  function detectOn() {
    var on = false;
    (st && st.gear || []).forEach(function (g) { if (g.code === 'DETECT_PICK' && g.on && !g.dim) on = true; });
    return on;
  }
  function scanTick() {
    if (!map || P.moving || scanBusy || busy || !detectOn()) return;
    var key = map.id + ':' + P.x + ',' + P.y;
    if (key === scanKey) return;
    scanKey = key; scanBusy = true;
    api('/api/tower-mine-scan?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y).then(function (d) {
      scanBusy = false;
      scan = d && d.ok ? { map: map.id, x: d.x, y: d.y, r: d.r || 1, cells: d.cells || [] } : null;
      if (d && d.dim) {
        (st.gear || []).forEach(function (g) { if (g.code === 'DETECT_PICK') g.dim = true; });
        if (d.message) { flashT0 = performance.now(); msg('🌟 ' + d.message, 4200); } // [2026-10-28] 빛나는 돌 탐지 -- 이번 결과까지 보여 주고 멈춤
        if (scan) scan.off = true;
      }
    }).catch(function () { scanBusy = false; scanKey = ''; });
  }
  function scanImg(g) {
    if (!scanImgs[g]) { scanImgs[g] = new Image(); scanImgs[g].src = oreIcon(g > 0 ? g : 24); }
    return scanImgs[g];
  }
  // [2026-10-24] 광산에서 다른 유저 만나기 -- 1초마다 내 위치를 알리고 같은 맵 다른 유저(이름 없이)를 받아 그린다.
  //  근처(4칸)에 다른 유저가 있으면 👋 안녕하세요 버튼(말풍선이 서로 보임, 하루 1회 오늘 요일석 5개).
  var tickC = null, tickE = null; // [2026-11-03] 다음 1초 턴에 실을 전투 턴/환경 턴 응답 처리
  var others = {}, presTimer = 0, presBusy = false, otherSprites = {}, myBubbleT0 = 0, greetBusy = false;
  // [2026-10-28] 패치 전 공지(/패치공지) -- 위치 알림 응답에 실려 오면 화면 맨 위 띠로 계속 띄우고, 새 공지면 한 번 크게 알린다.
  //  공지가 내려가면(끄기/배포) 띠도 내린다. 띠 높이만큼 좌표/미니맵을 아래로(--patch-h).
  var patchShownId = 0;
  function patchShow(d) {
    var box = el.patch;
    if (!box) return;
    if (d.patchId) {
      var text = '📢 ' + d.patchText;
      if (box.textContent !== text) box.textContent = text;
      box.style.display = 'block';
      if (d.patchId !== patchShownId) { patchShownId = d.patchId; flashT0 = performance.now(); msg('📢 패치 공지: ' + d.patchText, 5000); }
    } else box.style.display = 'none';
    el.ov.style.setProperty('--patch-h', box.style.display === 'block' ? (box.offsetHeight + 4) + 'px' : '0px');
  }
  // [2026-11-07] "같은 아이디로 이미 접속 중이면 안 되게" -- 이 광산 화면(탭)의 번호. 같은 탭 새로고침은 같은 번호(sessionStorage).
  var mineCid = (function () { var v = ''; try { v = sessionStorage.getItem('tw-mine-cid') || ''; } catch (e) { } if (!/^[a-z0-9]{6,20}$/.test(v)) { v = Math.random().toString(36).slice(2, 12) + Date.now().toString(36).slice(-4); try { sessionStorage.setItem('tw-mine-cid', v); } catch (e) { } } return v; })();
  var dupClosing = false;
  function dupKick(m) { // 다른 곳에서 들어와 이 화면을 끝낸다
    if (!opened) return;
    dupClosing = true;
    close();
    setTimeout(function () { try { alert(m || '다른 곳에서 접속해 이 화면은 종료됐어요.'); } catch (e) { } }, 50);
  }
  /** [2026-11-07] 화면에 보이는 칸 수(가로/세로 절반) -- 용병 선공 범위(서버에도 1초 턴으로 알린다). */
  function viewHalf() { var TSn = T * scale; return el && el.cv && TSn > 0 ? [Math.max(3, Math.floor(el.cv.width / TSn / 2)), Math.max(3, Math.floor(el.cv.height / TSn / 2))] : [9, 6]; }
  function presTick() {
    if (!opened || !map || presBusy) return;
    presBusy = true;
    var mid = map.id;
    // [2026-11-03] 시즌 5.5 경량화 -- 위치 알림 + 전투 턴 + 환경 턴 + 몬스터 상태를 요청 하나로(/api/tower-mine-tick)
    var tc = tickC, te = tickE, tm = !!(map.mobs && map.mobs.length);
    tickC = tickE = null;
    var pt0 = performance.now(), pws = !!(wsOk && ws && ws.readyState === 1); // [2026-11-03] 응답 속도(핑) -- 1초 턴 왕복 시간, 연결 방식(WS/HTTP)
    apiFast('/api/tower-mine-tick?mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + '&d=' + P.dir + verQ() + (tc ? '&c=1' : '') + (te ? '&e=1' : '') + (tm ? '&m=1' : '') + (st && st.merc && st.merc.mode === 1 ? '&vw=' + viewHalf()[0] + '&vh=' + viewHalf()[1] : '') + '&cid=' + mineCid + (netAvg >= 0 ? '&pg=' + netAvg + '&dv=' + NET_DV + '&nw=' + (pws ? 1 : 0) + '&fg=' + netGap + '&bg=' + (document.hidden ? 1 : 0) + '&wv=' + (pws ? wsVia : 0) : '')).then(function (r) {
      if (r && r.dup) { dupKick(r.message); return; } // [2026-11-07] 다른 곳에서 같은 캐릭터로 들어와 있다
      showPing(performance.now() - pt0, pws, r && r.ms);
      if (tc) tc(r && r.c);
      if (te) te(r && r.e);
      if (tm && r && r.m) applyMobs(r.m, mid);
      return r && r.p;
    }).then(function (d) {
      presBusy = false;
      if (d) patchShow(d); // [2026-10-28] 패치 전 공지
      if (d && d.rewardMsg) { flashT0 = performance.now(); msg(d.rewardMsg, 4200); } // [2026-10-28] 닌자 두목 함께 처치 보상
      if (d && d.online != null) { onlineN = d.online; mapN = d.mapN || {}; regenText(); worldCounts(); } // [2026-10-30] 접속 인원
      if (!map || map.id !== mid || !d) return;
      hermit = d.hermit ? { m: mid, x: d.hermit.x, y: d.hermit.y, until: d.hermit.until } : null; // [2026-10-30]
      bossNpc = d.bossCall ? { m: mid, x: d.bossCall.x, y: d.bossCall.y, at: d.bossCall.at || (bossNpc && bossNpc.m === mid && bossNpc.at > nowSrv() ? bossNpc.at : 0) } : null; // [2026-11-13] 보스 소환사(at = 카운트가 끝나 보스가 나오는 시각)
      if (d.hermitAdmin && d.hermitAdmin !== hermitAdminSaid) { hermitAdminSaid = d.hermitAdmin; msg('🕵️ 관리자 안내: 은둔한 대장장이 ' + d.hermitAdmin, 5000); }
      var now = performance.now(), seen = {};
      (d.others || []).forEach(function (o) {
        seen[o.id] = 1;
        var cur = others[o.id];
        if (!cur) others[o.id] = cur = { x: o.x, y: o.y, fx: o.x, fy: o.y, t0: now };
        else if (cur.x !== o.x || cur.y !== o.y) { var pp = otherPos(cur, now); cur.fx = pp.x; cur.fy = pp.y; cur.x = o.x; cur.y = o.y; cur.t0 = now; }
        cur.dir = o.d || 'down'; cur.n = o.n || ''; cur.look = o.look || {}; cur.bubble = o.bubble || ''; cur.frozen = !!o.frozen; cur.stealth = !!o.stealth; cur.mc = o.mc; cur.mr = !!o.mr; cur.mt = o.mt || null; // [2026-11-05] 용병 // [2026-10-28] 얼음 여왕 빙결
        if (o.a && o.a.length) { // [2026-11-07] 다른 사람 공격 -- 처음 보는 번호만, 서버 시각 간격대로
          var fresh = o.a.filter(function (a) { return a.s > (cur.as || 0); }), base = fresh.length ? fresh[0].at : 0;
          fresh.forEach(function (a) { othAtkPlay(cur, o.id, a, Math.max(0, Math.min(1500, a.at - base))); cur.as = Math.max(cur.as || 0, a.s); });
        }
      });
      for (var id in others) if (!seen[id]) delete others[id];
      drawMini(); // [2026-10-28] 미니맵에 다른 유저 위치
    }).catch(function () { presBusy = false; if (tc) bossBusy = false; if (te) coldBusy = false; });
  }
  // [2026-11-07] "다른 사람 공격 모션 보이게, 피해 숫자도 다른 색으로" -- 휘두르기(0.38초) + 켠 검기 + 피해 숫자(연보라, 조금 작게 -- 내 숫자와 구분)
  var OTH_DMG_COL = '#c9b8f0', OTH_CRIT_COL = '#f2b8ff';
  function othAtkPlay(cur, oid, a, delay) {
    var t = performance.now() + delay;
    cur.swT0 = t; cur.swDir = a.d || cur.dir || 'down';
    // [2026-11-08] 다른 사람 필살기 -- 만검진 장판(바닥 + 떨어지는 칼) / 일섬참 斬(화면 전체 어둡게·번쩍은 내 것만)
    if (a.u === 0 && a.z) { ultImg('sword'); ultFx.push({ k: 0, x: a.z[0], y: a.z[1], r: a.z[2], t0: t, sw: ultSwords(a.z[2], a.s || 1), o: 1 }); }
    if (a.u === 1 && a.h && a.h.length) { ultImg('zan'); ultFx.push({ k: 1, x: a.h[0][0], y: a.h[0][1], t0: t, o: 1 }); }
    if (a.au && a.au.length) {
      var els = a.au.map(function (q) { return q[0]; }), rs = a.au.map(function (q) { return Math.min(5, q[1]); });
      auraFx.push({ x: cur.x, y: cur.y, dir: cur.swDir, r: Math.max.apply(null, rs), els: els, rs: rs, t0: t, w: !!a.w && !(a.wl && a.wl.length), o: 1, oid: oid });
    }
    if (a.wl && a.wl.length) { whirlLoad(); auraFx.push({ x: cur.x, y: cur.y, dir: cur.swDir, r: 1, els: a.wl, rs: [], t0: t, w: true, wo: true, o: 1, oid: oid }); } // [2026-11-07] 돌개베기
    (a.h || []).slice(0, 12).forEach(function (h, i) {
      var el = h[3] >= 10 ? h[3] - 10 : 0;
      floats.push({ x: h[0] + (i ? (i % 2 ? 0.3 : -0.3) : 0), y: h[1] - 0.28 * i, text: (h[3] === 1 ? '💥' : '') + (el ? ELEM_ICON[el] : '') + h[2], color: h[3] === 1 ? OTH_CRIT_COL : OTH_DMG_COL, sm: 1, t0: t + 110 + 60 * i });
    });
  }
  function otherPos(o, now) {
    var k = Math.min(1, (now - o.t0) / 950);
    return { x: o.fx + (o.x - o.fx) * k, y: o.fy + (o.y - o.fy) * k, moving: k < 1 };
  }
  function otherSprite(look, dir, step, dk) {
    var key = JSON.stringify(look || {});
    if (!otherSprites[key]) otherSprites[key] = {};
    var c = otherSprites[key], sk = dir + step + (dk || 0);
    if (!c[sk]) c[sk] = charSprite(dir, step, dk || 0, look || {}); // [2026-11-07] dk 3/4 = 무기 휘두르기
    return c[sk];
  }
  function nearOther() {
    for (var id in others) { var o = others[id]; if (Math.abs(o.x - P.x) + Math.abs(o.y - P.y) <= 8) return true; } // [2026-11-11] "너무 가까워야 인사가 된다" 4 -> 8칸(서버 MINE_GREET_RANGE와 같게)
    return false;
  }
  // [2026-10-28] 운영자 장비 -- "누가 봐도 운영진" + "화려하게 반짝이는 이펙트". (x, y) = 캐릭터 그림 왼쪽 위.
  //  gmAura: 캐릭터 뒤 금빛/보랏빛 후광(숨 쉬듯). gmSparkle: 주위를 도는 십자 반짝이 + 발밑에서 피어오르는 금가루 + 머리 위 "운영자" 이름표.
  var GM_COLORS = ['#ffe14d', '#7ff0ff', '#ff8ad8', '#ffffff', '#b07cff'];
  function gmAura(x, y, now) {
    var TS = T * scale; // draw()의 TS는 지역 변수라 여기서 다시 구한다(없으면 ReferenceError로 화면 그리기가 멈춘다)
    var mx = x + TS / 2, my = y + TS * 0.55, pulse = 0.5 + 0.5 * Math.sin(now / 320);
    var grd = ctx.createRadialGradient(mx, my, TS * 0.1, mx, my, TS * (0.95 + pulse * 0.15));
    grd.addColorStop(0, 'rgba(255,225,77,' + (0.45 + pulse * 0.2) + ')');
    grd.addColorStop(0.55, 'rgba(176,124,255,' + (0.22 + pulse * 0.12) + ')');
    grd.addColorStop(1, 'rgba(176,124,255,0)');
    ctx.fillStyle = grd;
    ctx.fillRect(mx - TS * 1.2, my - TS * 1.2, TS * 2.4, TS * 2.4);
  }
  function gmStar(x, y, r, col, a) {
    ctx.globalAlpha = a; ctx.fillStyle = col;
    ctx.fillRect(Math.round(x - r), Math.round(y - r / 4), Math.round(r * 2), Math.max(1, Math.round(r / 2)));
    ctx.fillRect(Math.round(x - r / 4), Math.round(y - r), Math.max(1, Math.round(r / 2)), Math.round(r * 2));
    ctx.globalAlpha = 1;
  }
  function gmSparkle(x, y, now, seed) {
    var TS = T * scale;
    var mx = x + TS / 2, my = y + TS * 0.55, u = TS / 16;
    for (var i = 0; i < 6; i++) { // 주위를 도는 반짝이
      var an = now / 650 + i * Math.PI / 3 + seed, rr = TS * (0.62 + 0.08 * Math.sin(now / 260 + i));
      var tw = 0.5 + 0.5 * Math.sin(now / 150 + i * 1.9 + seed);
      gmStar(mx + Math.cos(an) * rr, my + Math.sin(an) * rr * 0.75, u * (1.2 + tw * 1.6), GM_COLORS[(i + Math.floor(now / 400)) % GM_COLORS.length], 0.35 + tw * 0.65);
    }
    for (var k = 0; k < 5; k++) { // 발밑에서 피어오르는 금가루
      var t = ((now / 1100) + k / 5 + seed * 0.13) % 1;
      gmStar(mx + Math.sin(k * 2.3 + t * 6) * TS * 0.42, y + TS - t * TS * 1.3, u * (1.4 - t), k % 2 ? '#ffe14d' : '#ffffff', 1 - t);
    }
    var label = '★ 운영자 ★';
    ctx.font = 'bold ' + Math.round(11 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
    ctx.lineWidth = 3 * dpr; ctx.strokeStyle = '#3a0f6e'; ctx.strokeText(label, mx, y - 2 * u);
    ctx.fillStyle = GM_COLORS[Math.floor(now / 250) % 2 ? 0 : 3]; ctx.fillText(label, mx, y - 2 * u);
  }
  // [2026-10-28] 환생 날개 -- 캐릭터 그림(16x16, 왼쪽 위 (x, y)) 뒤에 MineChar.wings(단계, 방향)을 깐다. 날개 캔버스의 (ext, 2)가 캐릭터 자리.
  //  가끔(1.6초마다 0.25초) 한 칸 들썩여 날갯짓처럼. draw()의 TS/scale 대신 scale만 쓴다(전역).
  function drawWings(x, y, rb, dir, now, seed, dye) {
    if (!(rb > 0) || !window.MineChar || !MineChar.wings) return;
    // [2026-10-29] 날개 32x32급 + 쉬지 않고 펄럭임(4프레임), 염색(dye) -- 날개 캔버스 한 칸 = 캐릭터 32 도트 한 칸(scale / 2)
    var f = Math.floor((now + (seed || 0) * 397) / 170) % 4, w = MineChar.wings(rb, dir || 'down', f, dye), hs = scale / 2;
    ctx.drawImage(w.c, x - w.ext * hs, y - w.top * hs, w.c.width * hs, w.c.height * hs);
  }

  function drawBubble(text, px0, py0, ph) {
    var pp = talkSplit(text, 26); // [2026-11-06] 긴 말은 앞/뒤 반씩(ph = 보이는 시간 안에서 앞 0 / 뒤 1)
    if (pp.length > 1) text = pp[ph == null ? Math.floor(performance.now() / 2200) % 2 : ph];
    ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
    var w = ctx.measureText(text).width + 14 * dpr, h = 20 * dpr, bx = Math.round(px0 - w / 2), by = Math.round(py0 - h);
    ctx.fillStyle = 'rgba(255,253,240,0.96)'; ctx.fillRect(bx, by, w, h);
    ctx.strokeStyle = '#140d0a'; ctx.lineWidth = 2 * dpr; ctx.strokeRect(bx, by, w, h);
    ctx.fillStyle = '#2a1d10'; ctx.textAlign = 'center'; ctx.fillText(text, px0, by + 14 * dpr);
  }
  function greet() {
    if (!opened || greetBusy) return;
    greetBusy = true;
    api('/api/tower-mine-greet').then(function (d) {
      greetBusy = false;
      if (d && d.ok) myBubbleT0 = performance.now();
      if (d && d.ores) st.ores = d.ores;
      if (d && d.reward) flashT0 = performance.now();
      if (d && d.ok && !d.reward) return; // [2026-10-29] "중복 인사 때 멘트 없애줘" -- 오늘 보상을 이미 받았으면 말풍선만
      msg((d && (d.message || d.error)) || '인사하지 못했어요.', 3200);
    }).catch(function () { greetBusy = false; });
  }
  /** ⛏️/⚔️ 버튼·스페이스 -- 옆에 몬스터가 있으면 공격, 없으면 지금 칸 캐기. */
  // [2026-10-28] 버튼을 나눴다 -- ⛏️ = 캐기만, ⚔️ = 바로 옆 몬스터 공격만(스페이스/엔터는 예전처럼 act: 옆에 몬스터가 있으면 공격, 없으면 캐기)
  function digBtn() {
    setMenu(false); // [2026-11-05] 채광/공격하면 메뉴 창 닫힘
    if (stunnedMe()) { msg(stunMsg(), 1500); return; } // [2026-11-04]
    if (frozenMe()) { iceStruggle(performance.now()); msg('🧊 얼어붙었어요! ⬅️➡️ 움직이거나 ⚔️ 공격해서 얼음을 깨 보세요. 동료가 곁(2칸)에 와도 풀려요.'); return; }
    dig();
  }
  // [2026-11-04] 검기 -- st.aura = { l: 배운 단계[9], a: 켠 단계[9] }(속성 번호 1~8 = 땅/번개/물/얼음/바람/불/어둠/빛)
  var AURA_COL = ['', '#d8a860', '#ffe14d', '#4aa8ff', '#a8f4ff', '#8ef08e', '#ff6a3a', '#b07aff', '#fff4b0'], AURA_FX_MS = 620, auraFx = []; // [2026-11-05] "검기가 느리게 나가면" 300 -> 620
  function auraEls() { var a = (st && st.aura && st.aura.a) || [], out = []; for (var e = 1; e <= 8; e++) if (a[e] > 0) out.push(e); return out; }
  function auraRange() { var a = (st && st.aura && st.aura.a) || [], r = 0; for (var e = 1; e <= 8; e++) r = Math.max(r, a[e] || 0); return Math.min(5, r); }
  // [2026-11-07] "관리자 공격 -- 캐릭터 본인 칸 주변에 돌개바람, 참고 이미지처럼 3D 느낌" -- img_loa/s55/fx/whirl.png(12프레임 96x72, 6줄 = 앞뒤 x 줄기 3개).
  //  기울어진 바닥 원을 칼바람 세 줄기가 돈다. 뒤쪽 반은 캐릭터보다 먼저, 앞쪽 반은 캐릭터 다음에 그려서 캐릭터를 감싸는 것처럼 보인다. 줄기마다 켠 속성 색으로 물들인다.
  var WHIRL_FW = 96, WHIRL_FH = 72, WHIRL_N = 12, whirlImg = null, whirlTints = {};
  function whirlLoad() { if (!whirlImg) { whirlImg = new Image(); whirlImg.src = cfg.base.replace(/\/loa$/, '') + '/img_loa/s55/fx/whirl.png?v=1'; } return whirlImg.complete && whirlImg.naturalWidth > 0; }
  function whirlSheet(col) {
    if (!whirlLoad()) return null;
    if (whirlTints[col]) return whirlTints[col];
    var c = document.createElement('canvas'); c.width = whirlImg.naturalWidth; c.height = whirlImg.naturalHeight;
    var g = c.getContext('2d'); g.drawImage(whirlImg, 0, 0);
    g.globalCompositeOperation = 'hue'; g.fillStyle = col; g.fillRect(0, 0, c.width, c.height); // 불 색 -> 속성 색(흰 심은 그대로 희게)
    g.globalCompositeOperation = 'saturation'; g.fillRect(0, 0, c.width, c.height);
    g.globalCompositeOperation = 'destination-in'; g.drawImage(whirlImg, 0, 0); // 투명한 곳은 그대로
    return (whirlTints[col] = c);
  }
  /** layer 0 = 캐릭터 뒤쪽 반(캐릭터보다 먼저), 1 = 앞쪽 반(캐릭터 다음). x, y = 캐릭터 그림 왼쪽 위. */
  function whirlDraw(layer, x, y, now, oid) {
    var TS = T * scale; // [2026-11-08] "공격하면 캐릭터가 사라지고 피해 숫자가 늦게" -- TS는 draw() 안 변수라 여기서 오류가 나 그 프레임 나머지(내 캐릭터, 숫자)가 안 그려졌다
    for (var i = 0; i < auraFx.length; i++) {
      var f = auraFx[i];
      if (!f.w || (f.oid || '') !== (oid || '')) continue; // [2026-11-07] oid = 다른 사람(없으면 나)
      var e0 = now - f.t0, life = AURA_FX_MS + 180;
      if (e0 < 0 || e0 >= life) continue;
      var al = Math.min(1, 0.55 + e0 / 260) * (e0 > AURA_FX_MS ? 1 - (e0 - AURA_FX_MS) / 180 : 1), fr = Math.floor(e0 / 45) % WHIRL_N;
      var w = TS * 3, h = w * WHIRL_FH / WHIRL_FW, dx = Math.round(x + TS / 2 - w / 2), dy = Math.round(y + TS * 0.85 - h * 0.62);
      ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = Math.max(0, al); ctx.imageSmoothingEnabled = true;
      for (var sk = 0; sk < 3; sk++) {
        var sh = whirlSheet(AURA_COL[f.els[sk % f.els.length]] || '#ff9a3a');
        if (!sh) break;
        ctx.drawImage(sh, fr * WHIRL_FW, (layer * 3 + sk) * WHIRL_FH, WHIRL_FW, WHIRL_FH, dx, dy, w, h);
      }
      ctx.restore();
    }
  }
  function auraFxGo() { var r = auraRange(), a = (st && st.aura && st.aura.a) || []; var we = whirlEls(); if ((st && st.adminMaps) || we.length) whirlLoad(); if (r > 0) auraFx.push({ x: P.x, y: P.y, dir: P.dir, r: r, els: auraEls(), rs: auraEls().map(function (e) { return a[e]; }), t0: performance.now(), w: !!(st && st.adminMaps) && !we.length }); if (we.length) auraFx.push({ x: P.x, y: P.y, dir: P.dir, r: 1, els: we, rs: [], t0: performance.now(), w: true, wo: true }); } // [2026-11-07] 돌개베기(wo = 돌개바람만) // [2026-11-05] w = 관리자(둘레 공격) -- [2026-11-07] 돌개바람은 whirlDraw가 캐릭터 앞뒤로 // [2026-11-04] 속성마다 자기 단계만큼
  // [2026-11-04] 획득 카드 -- 화면 가운데(조금 위)에 잠깐 쌓였다가(1.8초) 왼쪽 아래(☰ 메뉴 -> 최근 획득)로 작아지며 빨려 들어간다. 귀한 것은 금테 + 번쩍.
  var lootBox = null;
  function lootDrops(list, px, py) {
    if (!list || !list.length) return;
    var sp = false;
    lootToast(list.map(function (dr) { var s = specialDrop(dr.grade); if (s) sp = true; if (cutGrade(dr.grade)) rainbowGo(px, py); return { icon: oreIcon(dr.grade), text: '+' + dr.qty + ' ' + dr.name, color: (ORE_COL[dr.grade] || ORE_COL[1])[1], big: s }; }));
    if (sp) flashT0 = performance.now();
  }
  /** [2026-11-05] 세공된 보석(등급 45~53, 세공 빙석 54, 세공 바다 보석 55~59). */
  function cutGrade(g) { return g >= 45 && g <= 59; }
  /** [2026-11-05] 무기 드랍 -- 이름 뒤에 (등급), 전설 이상이면 무지개 빛. 같은 등급이 또 나와 분해되면 조각. */
  function weaponLoot(d, px, py) {
    if (d.dropGrade >= 4) rainbowGo(px, py);
    lootToast(d.dropShard ? [{ text: '🧩 ' + (d.dropShardName || '조각') + ' +' + d.dropShard + (d.dropName ? ' (' + d.dropName + ' ' + (GRADE_NAME[d.dropGrade] || '') + ')' : ''), color: '#e8e2f8' }]
      : [{ text: (d.dropArmor ? '🛡️ ' : '🗡️ ') + d.dropName + ' (' + (GRADE_NAME[d.dropGrade] || '?') + ')', color: GRADE_COL[d.dropGrade] || '#ffd27a', big: true }]); // [2026-11-06] 갑옷
  }
  // [2026-11-05] "아이템 드랍 위치로부터 퍼지는 이펙트 -- 에픽 빔 같은 느낌" -- 전설 이상 무기 / 세공된 보석이 나오면 떨어진 자리에서
  //  흰빛-금빛-주황 빛줄기가 부채꼴로 화면 끝까지 쭉 뻗고(0.4초), 사이사이 가는 무지개 줄기와 가운데 번쩍임, 2.2초에 걸쳐 사라진다.
  var beamFx = null, BEAM_MS = 2200;
  function rainbowGo(x, y) { beamFx = { x: x != null ? x : P.x, y: y != null ? y : P.y, t0: performance.now(), seed: Math.random() }; }
  function drawRainbow(tn, W, H, TS, camX, camY) {
    if (!beamFx) return;
    var q = (tn - beamFx.t0) / BEAM_MS;
    if (q >= 1) { beamFx = null; return; }
    if (q < 0) return;
    var cx = beamFx.x * TS - camX + TS / 2, cy = beamFx.y * TS - camY + TS / 2;
    var grow = 1 - Math.pow(1 - Math.min(1, q / 0.18), 3), a = q < 0.18 ? 1 : 1 - (q - 0.18) / 0.82, L = Math.hypot(W, H) * 1.1 * grow, sd = beamFx.seed;
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    // 굵은 빛줄기 -- [각도, 반폭, 길이 배수]
    [[-2.35, 0.09, 1], [-1.95, 0.05, 0.75], [-1.62, 0.12, 1], [-1.15, 0.045, 0.7], [-0.72, 0.1, 1], [-0.3, 0.05, 0.6], [0.45, 0.06, 0.7], [2.75, 0.05, 0.55], [3.5, 0.045, 0.5]].forEach(function (r, i) {
      var ang = r[0] + (sd - 0.5) * 0.4 + Math.sin(tn / 320 + i) * 0.015, hw = r[1] * (0.85 + 0.15 * Math.sin(tn / 55 + i * 2)), len = L * r[2];
      var ex = cx + Math.cos(ang) * len, ey = cy + Math.sin(ang) * len, g = ctx.createLinearGradient(cx, cy, ex, ey);
      g.addColorStop(0, 'rgba(255,255,235,' + (0.95 * a) + ')');
      g.addColorStop(0.22, 'rgba(255,232,90,' + (0.75 * a) + ')');
      g.addColorStop(0.6, 'rgba(255,150,40,' + (0.35 * a) + ')');
      g.addColorStop(1, 'rgba(255,110,30,0)');
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(ang - hw) * len, cy + Math.sin(ang - hw) * len); ctx.lineTo(cx + Math.cos(ang + hw) * len, cy + Math.sin(ang + hw) * len); ctx.closePath(); ctx.fill();
      var cg = ctx.createLinearGradient(cx, cy, cx + Math.cos(ang) * len * 0.7, cy + Math.sin(ang) * len * 0.7);
      cg.addColorStop(0, 'rgba(255,255,255,' + (0.9 * a) + ')'); cg.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.strokeStyle = cg; ctx.lineWidth = Math.max(1.5, TS * 0.05);
      ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(ang) * len * 0.7, cy + Math.sin(ang) * len * 0.7); ctx.stroke();
    });
    // 가는 무지개 줄기
    for (var k = 0; k < 7; k++) {
      var ra = -2.6 + k * 0.62 + (sd - 0.5) * 0.4, rl = L * (0.45 + 0.08 * (k % 3)), rh = 0.018;
      var rg2 = ctx.createLinearGradient(cx, cy, cx + Math.cos(ra) * rl, cy + Math.sin(ra) * rl);
      rg2.addColorStop(0, 'hsla(' + ((k * 51 + tn / 6) % 360) + ',100%,70%,' + (0.7 * a) + ')'); rg2.addColorStop(1, 'hsla(' + ((k * 51 + tn / 6) % 360) + ',100%,60%,0)');
      ctx.fillStyle = rg2;
      ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(ra - rh) * rl, cy + Math.sin(ra - rh) * rl); ctx.lineTo(cx + Math.cos(ra + rh) * rl, cy + Math.sin(ra + rh) * rl); ctx.closePath(); ctx.fill();
    }
    // 가운데 번쩍임 + 가로 빛번짐
    var fr = TS * (1.2 + 1.6 * a), cg2 = ctx.createRadialGradient(cx, cy, 0, cx, cy, fr);
    cg2.addColorStop(0, 'rgba(255,255,255,' + a + ')'); cg2.addColorStop(0.35, 'rgba(255,240,150,' + (0.7 * a) + ')'); cg2.addColorStop(1, 'rgba(255,170,60,0)');
    ctx.fillStyle = cg2; ctx.beginPath(); ctx.arc(cx, cy, fr, 0, Math.PI * 2); ctx.fill();
    var sw = TS * 5 * (0.5 + a * 0.5), sg = ctx.createLinearGradient(cx - sw, cy, cx + sw, cy);
    sg.addColorStop(0, 'rgba(255,230,140,0)'); sg.addColorStop(0.5, 'rgba(255,255,230,' + (0.8 * a) + ')'); sg.addColorStop(1, 'rgba(255,230,140,0)');
    ctx.fillStyle = sg; ctx.fillRect(cx - sw, cy - TS * 0.06, sw * 2, TS * 0.12);
    ctx.restore();
  }
  function lootToast(items) {
    if (!el.ov || !items || !items.length) return;
    items.forEach(function (it) { killFeed(it.text, it.color, it.icon); }); // [2026-11-05] 왼쪽 획득 기록(경험치 줄과 같은 곳)
    items = items.filter(function (it) { return it.big; }); // 날아가는 카드는 귀한 것만
    if (!items.length) return;
    if (!lootBox || !lootBox.parentNode) {
      lootBox = document.createElement('div');
      lootBox.style.cssText = 'position:absolute;left:50%;top:38%;transform:translateX(-50%);z-index:5;display:flex;flex-direction:column;align-items:center;gap:4px;pointer-events:none;'; // [2026-11-04] 화면 가운데에서 아이템 창(메뉴) 쪽으로
      el.ov.appendChild(lootBox);
    }
    items.forEach(function (it, i) {
      var c = document.createElement('div');
      c.style.cssText = 'display:flex;align-items:center;gap:6px;padding:4px 9px;background:rgba(20,16,30,.88);border:2px solid ' + (it.big ? '#ffd27a' : '#f4f0e6') + ';box-shadow:2px 2px 0 rgba(0,0,0,.6);font:800 ' + (it.big ? 13 : 12) + 'px monospace;color:' + (it.color || '#fff') + ';white-space:nowrap;opacity:0;transform:translateY(10px);transition:transform .25s ease-out,opacity .25s;';
      c.innerHTML = (it.icon ? '<img src="' + it.icon + '" alt="" style="width:16px;height:16px;image-rendering:pixelated">' : '') + '<span></span>';
      c.lastChild.textContent = it.text;
      lootBox.appendChild(c);
      while (lootBox.children.length > 8) lootBox.removeChild(lootBox.firstChild);
      setTimeout(function () { c.style.opacity = '1'; c.style.transform = 'none'; }, 30 + i * 90);
      setTimeout(function () { // 왼쪽 아래 메뉴 버튼 쪽으로
        if (!c.parentNode) return;
        var r = c.getBoundingClientRect(), tg = el.menuBtn ? el.menuBtn.getBoundingClientRect() : null; if (tg && !tg.width) tg = null; // 메뉴 버튼이 숨어 있으면(NPC 창) 왼쪽 아래 구석으로
        var tx = tg ? tg.left + tg.width / 2 : 24, ty = tg ? tg.top + tg.height / 2 : window.innerHeight - 60;
        c.style.transition = 'transform .55s cubic-bezier(.5,0,.9,.4),opacity .55s ease-in';
        c.style.transform = 'translate(' + Math.round(tx - (r.left + r.width / 2)) + 'px,' + Math.round(ty - (r.top + r.height / 2)) + 'px) scale(.15)';
        c.style.opacity = '0';
        setTimeout(function () { if (c.parentNode) c.parentNode.removeChild(c); if (el.menuBtn) { el.menuBtn.style.transition = 'transform .12s'; el.menuBtn.style.transform = 'scale(1.18)'; setTimeout(function () { el.menuBtn.style.transform = ''; }, 140); } }, 560);
      }, 1800 + i * 160);
    });
  }
  /** 공격 응답의 검기 -- 바로 앞 적의 속성 숫자(aura), 너머 몬스터(auraMobs: 상태/숫자/처치), 드랍. */
  // [2026-11-08] 하피의 둥지 대형 나무 -- 48x64(3칸 x 4칸, 아래 2칸이 지나갈 수 없는 밑동 'E'), 상태 0 보통 / 1 불탐(2프레임) / 2 다 탐(2시간 뒤 다시 자람).
  //  캐릭터 위에 그린다(수관 뒤에 앉은 하피가 가려진다). 내가 수관 밑에 있으면 반투명. 불이 쌓이는 중이면 밑동 아래에 주황 막대.
  var bigTreeCache = {};
  function bigTreeArt(st, f) {
    var key = st + ':' + f;
    if (bigTreeCache[key]) return bigTreeCache[key];
    var c = document.createElement('canvas'); c.width = 48; c.height = 64;
    var g = c.getContext('2d'), s0 = 7;
    var r = function () { s0 = (s0 * 16807) % 2147483647; return s0 / 2147483647; };
    var P = function (x, y, col) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= 48 || y >= 64) return; g.fillStyle = col; g.fillRect(x, y, 1, 1); };
    var Rr = function (x, y, w, h, col) { g.fillStyle = col; g.fillRect(x, y, w, h); };
    var E = function (cx, cy, rx, ry, col) { for (var y = -ry; y <= ry; y++) for (var x = -rx; x <= rx; x++) if ((x * x) / (rx * rx + .5) + (y * y) / (ry * ry + .5) <= 1) P(cx + x, cy + y, col); };
    if (st === 2) { // 다 탄 나무 -- 검은 줄기와 앙상한 가지, 잿더미, 깜빡이는 불씨
      Rr(20, 26, 8, 34, '#1a1210'); Rr(21, 26, 2, 34, '#2a201a'); Rr(19, 58, 10, 4, '#120c0a');
      [[24, 30, 10, 18], [24, 34, 38, 22], [24, 40, 8, 32], [24, 38, 40, 30], [24, 28, 24, 12]].forEach(function (b) { var n = Math.max(Math.abs(b[2] - b[0]), Math.abs(b[3] - b[1])); for (var i = 0; i <= n; i++) P(b[0] + (b[2] - b[0]) * i / n, b[1] + (b[3] - b[1]) * i / n, '#1a1210'); });
      for (var a = 0; a < 60; a++) P(6 + r() * 36, 56 + r() * 6, r() < .5 ? '#4a4440' : '#6a625a');
      for (var e = 0; e < 6; e++) P(10 + r() * 28, 20 + r() * 36, (e + f) % 2 ? '#ff6a2a' : '#ffb03a');
      return (bigTreeCache[key] = c);
    }
    var lf = st === 1 ? ['#2a4a1a', '#3a5a20', '#5a5a1a'] : ['#1e4a22', '#2a6a2c', '#3e8a38', '#5aa848'];
    Rr(19, 40, 10, 22, '#4a2e1a'); Rr(20, 40, 3, 22, '#6a4428'); Rr(26, 40, 2, 22, '#2e1c10'); Rr(15, 58, 18, 4, '#3a2414'); Rr(13, 60, 6, 2, '#3a2414'); Rr(29, 60, 6, 2, '#3a2414');
    E(24, 24, 22, 20, lf[0]); E(22, 21, 19, 17, lf[1]); E(19, 16, 13, 11, lf[2]); if (lf[3]) E(16, 12, 7, 6, lf[3]);
    for (var i = 0; i < 90; i++) { var x = 4 + r() * 40, y = 6 + r() * 36; if (((x - 24) * (x - 24)) / 484 + ((y - 24) * (y - 24)) / 400 < 1) P(x, y, r() < .5 ? lf[0] : lf[2]); }
    if (st === 1) { // 불탐 -- 잎이 그을리고 불꽃이 위로 넘실(프레임마다 모양 바뀜), 연기
      s0 = f ? 31 : 17;
      for (var k = 0; k < 14; k++) { var fx = 6 + r() * 36, fy = 8 + r() * 30, h = 4 + r() * 8; for (var j = 0; j < h; j++) { var w = Math.max(0, 2 - j / 4); Rr(fx - w, fy - j, 1 + w * 2, 1, j < h * .3 ? '#ffe46a' : j < h * .7 ? '#ff8a2a' : '#e0401a'); } }
      for (var q = 0; q < 10; q++) { var sx = 10 + r() * 28, sy = r() * 10; P(sx, sy, 'rgba(80,80,80,.7)'); P(sx + 1, sy - 1, 'rgba(120,120,120,.5)'); }
    }
    return (bigTreeCache[key] = c);
  }
  // [2026-11-11] 곡괭이에 쓰러진 나무 -- 밑동(잘린 면 나이테, 뿌리, 톱밥, 누운 통나무 토막, 새싹). 48x64(나무와 같은 틀).
  var stumpCache = null;
  function stumpArt() {
    if (stumpCache) return stumpCache;
    var c = document.createElement('canvas'); c.width = 48; c.height = 64; var g = c.getContext('2d');
    var P1 = function (x, y, col) { g.fillStyle = col; g.fillRect(Math.round(x), Math.round(y), 1, 1); }, Rr = function (x, y, w, h, col) { g.fillStyle = col; g.fillRect(x, y, w, h); };
    var E = function (cx, cy, rx, ry, col) { for (var y = -ry; y <= ry; y++) for (var x = -rx; x <= rx; x++) if ((x * x) / (rx * rx + .5) + (y * y) / (ry * ry + .5) <= 1) P1(cx + x, cy + y, col); };
    Rr(17, 50, 14, 10, '#4a2e1a'); Rr(18, 50, 3, 10, '#6a4428'); Rr(28, 50, 2, 10, '#2e1c10');
    Rr(13, 58, 6, 3, '#3a2414'); Rr(29, 58, 6, 3, '#3a2414'); Rr(11, 60, 4, 2, '#3a2414'); Rr(33, 60, 4, 2, '#3a2414');
    E(24, 50, 7, 2, '#c8a070'); E(24, 50, 5, 1, '#a8804a'); P1(24, 50, '#6a4428'); P1(21, 50, '#8a6038'); P1(27, 50, '#8a6038');
    for (var i = 0; i < 9; i++) P1(14 + (i * 7) % 20, 61 + (i % 2), i % 3 ? '#d8b888' : '#b89060');
    Rr(32, 55, 12, 4, '#5a3820'); Rr(32, 55, 12, 1, '#7a5030'); E(44, 57, 1, 2, '#c8a070');
    P1(15, 48, '#5aa848'); P1(14, 47, '#7ad860'); P1(16, 47, '#3e8a38');
    return (stumpCache = c);
  }
  var TREE_FALL_MS = 800;
  function forestTreesDraw(now, TS, camX, camY, W, H) {
    var f = Math.floor(now / 260) % 2, burnt = 0;
    map.trees.forEach(function (tp, i) {
      var q = treeSt[i] || [i, 0, 0], st0 = q[1] | 0;
      if (st0 >= 2) burnt++;
      var x = tp[0] * TS - camX, y = (tp[1] - 2) * TS - camY;
      if (x > W || y > H || x + 3 * TS < 0 || y + 4 * TS < 0) return;
      var under = st0 < 2 && P.x >= tp[0] && P.x <= tp[0] + 2 && P.y >= tp[1] - 2 && P.y < tp[1];
      if (under) ctx.globalAlpha = 0.5;
      if (st0 === 3) { // [2026-11-11] 쓰러진 나무 -- 밑동, 방금 쓰러졌으면 밑동을 축으로 옆으로 넘어가며 흙먼지(TREE_FALL_MS)
        ctx.drawImage(stumpArt(), Math.round(x), Math.round(y), 3 * TS, 4 * TS);
        var fk = treeFallAt[i] ? (now - treeFallAt[i]) / TREE_FALL_MS : 1;
        if (fk < 1) {
          var ang = Math.min(1, fk * fk * 1.15) * 1.48, px0 = x + 1.5 * TS, py0 = y + 3.6 * TS;
          ctx.save(); ctx.translate(px0, py0); ctx.rotate(ang); ctx.globalAlpha = fk > 0.8 ? (1 - fk) * 5 : 1;
          ctx.beginPath(); ctx.rect(-1.5 * TS, -4 * TS, 3 * TS, 3.35 * TS); ctx.clip();
          ctx.drawImage(bigTreeArt(0, 0), -1.5 * TS, -3.6 * TS, 3 * TS, 4 * TS);
          ctx.restore();
          if (fk > 0.45) { ctx.globalAlpha = Math.max(0, 0.6 * (1 - fk) / 0.55); for (var di = 0; di < 14; di++) { var da = di * 0.7, dr = TS * (0.25 + di * 0.1) * (0.6 + fk); ctx.fillStyle = di % 2 ? '#c8b898' : '#a89878'; ctx.fillRect(px0 + TS * fk + Math.cos(da) * dr, py0 - Math.abs(Math.sin(da)) * dr * 0.5, TS * 0.12, TS * 0.1); } ctx.globalAlpha = 1; }
        } else if (treeFallAt[i]) delete treeFallAt[i];
        return;
      }
      ctx.drawImage(bigTreeArt(st0, st0 ? f : 0), Math.round(x), Math.round(y), 3 * TS, 4 * TS);
      ctx.globalAlpha = 1;
      if (st0 === 0 && q[2] > 0) { // 불이 쌓이는 중
        var bw = 2 * TS, bx = Math.round(x + TS / 2), by = Math.round(y + 4 * TS + 2);
        ctx.fillStyle = 'rgba(0,0,0,.55)'; ctx.fillRect(bx, by, bw, 4);
        ctx.fillStyle = '#ff8a2a'; ctx.fillRect(bx, by, Math.round(bw * Math.min(1, q[2] / treeNeed)), 4);
      }
    });
  }
  // [2026-11-11] "미궁의 끝까지 몇 걸음이 안 나온다" -- 밤/어두운 맵 덧칠보다 먼저 그려서 가려졌다. 덧칠 뒤(ultHud 옆)에 그린다. 둥지(남은 나무/돌풍)도 같이.
  // [2026-11-11] "미궁 몇 걸음이 메시지 박스랑 겹친다(폴드 접은 화면)" -- 아래 가운데 안내는 체력 바 바로 위(메시지 박스는 아래 118px부터 위로),
  //  좁은 화면이면 오른쪽 버튼 줄(118px)을 피해 왼쪽으로. hudBotY = 글자 기준선(상자 = 기준선 -21 ~ +9, 화면 배율 곱한 값).
  function hudBotY(H) {
    var r = el.hp && el.hp.offsetParent ? el.hp.getBoundingClientRect() : null, c = el.cv.getBoundingClientRect();
    if (!r || !r.height || !c.height) return Math.round(H - 118 * dpr);
    var k = H / c.height;
    return Math.round((Math.min(r.top - c.top, c.height - 64) - 15) * k);
  }
  function hudCx(W, w) {
    if (W / dpr >= 700) return W / 2;
    return Math.max(8 * dpr + w / 2, Math.min(W / 2, W - 118 * dpr - w / 2));
  }
  function forestHud(W, H) {
    if (map.trees) { // [2026-11-11] "남은 나무는 안 알려줘도 돼" -- 돌풍 2초 전 경고만(체력 바 위, 크게)
      var gl = harpyGustAt - nowSrv();
      if (gl > 0 && gl < 2000) {
        var gtx = '🌪️ 돌풍이 몰려와요!';
        ctx.font = 'bold ' + Math.round(18 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
        var gw = ctx.measureText(gtx).width + 28 * dpr, gy = hudBotY(H), gx = hudCx(W, gw);
        ctx.globalAlpha = 0.82; ctx.fillStyle = '#0e2420'; ctx.fillRect(gx - gw / 2, gy - 21 * dpr, gw, 30 * dpr);
        ctx.globalAlpha = 1; ctx.strokeStyle = '#7ae8c8'; ctx.lineWidth = 1.5 * dpr; ctx.strokeRect(gx - gw / 2, gy - 21 * dpr, gw, 30 * dpr);
        ctx.fillStyle = '#e8fff4'; ctx.fillText(gtx, gx, gy);
        ctx.textAlign = 'left';
      }
    }
    if (map.mazeLeft != null) { // [2026-11-11] 미궁 힌트 -- "미궁의 끝까지 몇 걸음"(통로를 지날 때마다 1걸음). "너무 안 보인다" -- 천둥 황소 타이머처럼 체력 바 위, 크게
      var mz = '🌲 미궁의 끝까지 ' + map.mazeLeft + '걸음';
      ctx.font = 'bold ' + Math.round(18 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
      var mzw = ctx.measureText(mz).width + 28 * dpr, mzy = hudBotY(H), mzx = hudCx(W, mzw);
      ctx.globalAlpha = 0.82; ctx.fillStyle = '#0e2414'; ctx.fillRect(mzx - mzw / 2, mzy - 21 * dpr, mzw, 30 * dpr);
      ctx.globalAlpha = 1; ctx.strokeStyle = '#7cf08a'; ctx.lineWidth = 1.5 * dpr; ctx.strokeRect(mzx - mzw / 2, mzy - 21 * dpr, mzw, 30 * dpr);
      ctx.fillStyle = '#c8ffd0'; ctx.fillText(mz, mzx, mzy);
      ctx.textAlign = 'left';
    }
  }
  function auraShow(d, fxp, fyp, t) {
    if (d.treeFire != null) { msg(d.treeCombo ? '🔥⚡ 불과 번개에 나무가 무너져요!' : '🔥 나무가 무너지기 시작했어요!', 2600); treeSt.forEach(function (q) { if (q[0] === d.treeFire) { q[1] = 1; q[2] = treeNeed; } }); } // [2026-11-08] 하피의 둥지
    (d.aura || []).forEach(function (h, i) { floats.push({ x: fxp + (i % 2 ? 0.3 : -0.3), y: fyp - 0.32 * (i + 1), text: ELEM_ICON[h[0]] + h[1] + (h[2] ? ' x1.5' : ''), color: AURA_COL[h[0]], big: !!h[2], t0: t + 70 * (i + 1) }); });
    var anyKill = false;
    (d.auraMobs || []).forEach(function (am, j) {
      var mm = map.mobs && map.mobs[am.id], p = mm && mm._p ? { x: mm._p.x, y: mm._p.y } : { x: am.row[3], y: am.row[4] };
      mobSt[am.id] = mobStOf(am.row, mobSt[am.id]);
      mobHitAt[am.id] = performance.now();
      var dl = 60 + j * 40;
      (am.hits || []).forEach(function (h, i) { floats.push({ x: p.x + (i % 2 ? 0.3 : -0.3), y: p.y - 0.32 * i, text: ELEM_ICON[h[0]] + h[1] + (h[2] ? ' x1.5' : ''), color: AURA_COL[h[0]], big: !!h[2], t0: t + dl + 70 * i }); });
      if (am.killed) { anyKill = true; if (!d.expFull) floats.push({ x: p.x, y: p.y + 0.4, text: '+' + am.exp + ' EXP', color: '#9fe0ff', t0: t + dl + 100 }); } // [2026-11-07] 경험치가 가득 찬(안 오르는) 처치는 EXP 숫자를 안 띄운다
    });
    lootDrops(d.auraDrops, fxp, fyp); // [2026-11-04] 화면 구석 카드
    if (!d.killed && d.dropGrade != null) { flashT0 = t; weaponLoot(d, fxp, fyp); }
    if (anyKill && d.ores) st.ores = d.ores;
    // [2026-11-05] "쓰러뜨렸어요 문구 빼고 경험치 몇 올랐는지만, 검은 상자 말고 왼쪽 빈 화면에 글씨만 띄웠다 서서히 사라지게" -- 일반 공격 처치 + 검기 처치를 합쳐 한 줄
    if (d.mercXp) mercXpApply(d.mercXp); // [2026-11-06] 용병 경험치(내 경험치의 1%)
    var exSum = d.killed && d.expGain ? d.expGain : 0, kn = d.killed ? 1 : 0;
    (d.auraMobs || []).forEach(function (am) { if (am.killed) { exSum += am.exp || 0; kn++; } });
    if (d.expReal != null) exSum = d.expReal; // [2026-11-07] 실제로 오른 경험치(99레벨 가득이면 0 -- 안 띄움)
    if (exSum > 0) killFeed('내 경험치 +' + exSum.toLocaleString() + (kn > 1 ? '  ×' + kn : ''));
  }
  // 왼쪽 빈 화면 -- 글씨만(그림자) 떠올랐다가 1.6초 뒤 위로 살짝 떠오르며 사라진다. 최대 6줄.
  var killBox = null;
  function killFeed(text, color, icon) {
    if (!el.ov) return;
    if (!killBox || !killBox.parentNode) {
      killBox = document.createElement('div');
      killBox.style.cssText = 'position:absolute;left:14px;top:57%;z-index:4;display:flex;flex-direction:column;align-items:flex-start;gap:2px;pointer-events:none;';
      el.ov.appendChild(killBox);
    }
    var c = document.createElement('div');
    c.innerHTML = (icon ? '<img src="' + icon + '" alt="" style="width:15px;height:15px;image-rendering:pixelated;vertical-align:-2px;margin-right:4px">' : '') + '<span></span>';
    c.lastChild.textContent = text;
    c.style.cssText = 'font:800 15px monospace;color:' + (color || '#9fe0ff') + ';text-shadow:0 0 3px #000,1px 1px 0 #000,-1px -1px 0 #000,1px -1px 0 #000,-1px 1px 0 #000;white-space:nowrap;opacity:0;transform:translateX(-10px);transition:opacity .2s,transform .2s;';
    killBox.appendChild(c);
    while (killBox.children.length > 8) killBox.removeChild(killBox.firstChild);
    setTimeout(function () { c.style.opacity = '1'; c.style.transform = 'none'; }, 20);
    setTimeout(function () { c.style.transition = 'opacity 1.1s ease-in,transform 1.1s ease-out'; c.style.opacity = '0'; c.style.transform = 'translateY(-14px)'; }, 2600);
    setTimeout(function () { if (c.parentNode) c.parentNode.removeChild(c); }, 3800);
  }
  /** 옆에 몬스터가 없을 때 공격 버튼 = 검기만 날린다(서버 mob=-1). */
  function auraAttack() {
    if (!opened || !map || busy || fade || atkBusy) return;
    var now = performance.now();
    if (now - lastAtkT < 420) return;
    lastAtkT = now; atkBusy = true; P.digT0 = now; P.swing = true;
    auraFxGo();
    apiFast('/api/tower-mine-attack?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&mob=-1&d=' + P.dir + verQ()).then(function (d) {
      atkBusy = false;
      if (!d || d.tooFast) return;
      if (d.reload) { reload(); return; }
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (!d.ok) { if (d.message) msg(d.message); return; }
      var t = performance.now();
      auraShow(d, P.x, P.y, t);
      if (d.combat) setCombat(d.combat);
      if (d.levelUp) { flashT0 = t; banner = { text: '🎉 레벨 업! Lv.' + d.combat.lv, t0: t }; }
      if (d.rebirthReadyNow) { rbHintShown = true; flashT0 = t; banner = { text: '✨ 환생 가능! 왕도 성당 신부님께', t0: t }; }
      if (d.hpLockFree) msg('🔓 레벨 업! 잠겼던 최대 체력이 풀렸어요.', 2600);
    }).catch(function () { atkBusy = false; });
  }
  /** 인벤토리 스킬 탭 -- 배운 속성마다 끔/1~배운 단계 중 고르기(장비 등급처럼). */
  function skillHtml() {
    var au = (st && st.aura) || { l: [], a: [] }, any = false, R = auraRange();
    var h = '<div class="mine-smith-say">🌀 <b>검기</b> -- 공격하면 바라보는 방향으로 날아가요. 속성마다 자기 단계만큼 멀리(1단계 = 1칸 ~ 5단계 = 5칸), 공격력의 (단계 x 5%) 속성 피해(상성이면 x1.5). 바로 앞 적은 일반 공격 + 켠 속성 전부를 맞고, 옆에 적이 없어도 공격 버튼으로 날릴 수 있어요.</div>'
        + '<div style="margin:6px 0 4px;font-size:11px;color:#a89ec8">숫자 = 단계(단계마다 5%), 흐린 칸은 아직 안 배운 단계</div>'
        + '<div style="margin:0 0 8px;font-weight:800">지금 사거리 <b style="color:#ffd27a">' + R + '칸</b>' + (R ? ' · ' + auraEls().map(function (e) { return '<span style="color:' + AURA_COL[e] + '">' + ELEM_ICON[e] + au.a[e] + '</span>'; }).join(' ') : '') + '</div>';
    for (var e = 1; e <= 8; e++) {
      var L = (au.l && au.l[e]) || 0, A = (au.a && au.a[e]) || 0;
      if (!L) continue;
      any = true;
      // [2026-11-04] "단계 선택이 옆으로 길어 잘린다" -- 한 줄에 이름 + 끔/1~5 작은 칸(같은 너비, 줄바꿈 없음), 배우지 않은 단계는 흐리게
      h += '<div style="display:flex;align-items:center;gap:3px;margin:0 0 6px"><span style="flex:0 0 58px;font-weight:800;font-size:12px;white-space:nowrap;color:' + AURA_COL[e] + '">' + ELEM_ICON[e] + ' ' + ELEM_NAME[e] + '</span>';
      for (var v = 0; v <= 5; v++) {
        var on = v === A, can = v <= L;
        h += '<button type="button" class="mine-mini-btn" style="flex:1 1 0;min-width:0;padding:3px 0;' + (on ? 'background:' + AURA_COL[e] + ';color:#1a1424;' : '') + (can ? '' : 'opacity:.3;') + '"' + (can ? ' onclick="TowerMine.auraSet(' + e + ',' + v + ')"' : ' disabled') + '>' + (v ? v : '끔') + '</button>';
      }
      h += '</div>';
    }
    if (!any) h += '<div class="mine-eq-empty">아직 배운 검기가 없어요. 왕도 원소술사 옆 검기 사범에게서 보스 드랍템 + 달빛석 + 세공된 바다 보석으로 배울 수 있어요.</div>';
    if (st && st.whirl) { // [2026-11-07] 돌개베기
      var wl = st.whirl, anyW = false;
      h += '<div class="mine-smith-say" style="margin-top:12px">🌪️ <b>돌개베기</b> -- 공격하면 내 둘레 8칸을 한꺼번에 베어요. 켠 속성마다 공격력의 (단계 x 5%) 속성 피해(상성 x1.5).</div>';
      for (var we = 1; we <= 8; we++) {
        var WL = (wl.l && wl.l[we]) || 0, WA = (wl.a && wl.a[we]) || 0;
        if (!WL) continue;
        anyW = true;
        h += '<div style="display:flex;align-items:center;gap:3px;margin:0 0 6px"><span style="flex:0 0 58px;font-weight:800;font-size:12px;white-space:nowrap;color:' + AURA_COL[we] + '">' + ELEM_ICON[we] + ' ' + ELEM_NAME[we] + '</span>';
        for (var wv = 0; wv <= 5; wv++) { var won = wv === WA, wcan = wv <= WL; h += '<button type="button" class="mine-mini-btn" style="flex:1 1 0;min-width:0;padding:3px 0;' + (won ? 'background:' + AURA_COL[we] + ';color:#1a1424;' : '') + (wcan ? '' : 'opacity:.3;') + '"' + (wcan ? ' onclick="TowerMine.whirlSet(' + we + ',' + wv + ')"' : ' disabled') + '>' + (wv ? wv : '끔') + '</button>'; }
        h += '</div>';
      }
      if (!anyW) h += '<div class="mine-eq-empty">아직 배운 돌개베기가 없어요. 왕도 검기 사범 옆 돌개 사범에게서 배울 수 있어요.</div>';
    }
    if (st && st.ult) { // [2026-11-07] 필살기
      var ul = st.ult.l || [0, 0];
      h += '<div class="mine-smith-say" style="margin-top:12px">⚔️ <b>필살기</b> -- ⚔️ 버튼 위 둥근 버튼(왼쪽 반 만검진 / 오른쪽 반 일섬참), 쿨타임 2분을 함께 써요.<br>🌧️ 만검진 Lv.' + (ul[0] | 0) + ' · 💥 일섬참 Lv.' + (ul[1] | 0) + (ul[0] | ul[1] ? '' : ' -- 왕도 필살 사범에게서 배울 수 있어요') + '</div>';
    }
    return h;
  }
  // [2026-11-04] 검기 사범 -- 속성별 보스 드랍템(등급 번호): 땅 = 사막의 심장, 얼음 = 얼음의 심장, 불 = 화산의 심장, 어둠 = 타락한 대주교의 인장. n단계 = 드랍템 2n-1 + 달빛석 2n-1
  var AURA_BOSS = { 1: [39, '피라미드(파라오 미라)', 57], 4: [33, '설산(얼음 여왕)', 55], 6: [64, '화산(용암 거신)', 56], 7: [31, '성당(타락한 대주교)', 59], 2: [65, '마물의 숲(천둥 황소)', 58], 5: [71, '하피의 둥지(하피 여왕)', 55] }, /* [2026-11-11] 바람 = 바람의 심장 + 세공 진주 */ MOON_G = 20; // [2026-11-04] 세 번째 = 세공 바다 보석 등급
  function oreHave(g) { var q = 0; (st.ores || []).forEach(function (o) { if (o.grade === g) q = o.qty; }); return q; }
  function auraNpcHtml(n) {
    var au = (st && st.aura) || { l: [], a: [] }, moon = oreHave(MOON_G);
    var h = npcHead('🌀 ' + (n ? n.name : '검기 사범'), false) + talkBlock(n, n ? n.line : '')
        + '<div class="mine-smith-say">단계마다 공격력의 5% 속성 피해(최대 25%), 사거리 +1칸(최대 5칸). n단계 = 보스 드랍템 (2n-1) + 달빛석 (2n-1) + 세공된 바다 보석 5개 · 달빛석 보유 ' + moon + '</div>';
    // [2026-11-05] 2환생 특전 -- 아직 안 배운 속성 하나를 1단계로 무료(한 번). 속성 버튼을 따로 보여 준다.
    if (au.gift === 1) {
      var gb = '';
      for (var ge = 1; ge <= 8; ge++) if (AURA_BOSS[ge] && !((au.l && au.l[ge]) || 0)) gb += '<button type="button" class="mine-mini-btn" style="color:' + AURA_COL[ge] + '" onclick="TowerMine.auraGift(' + ge + ')">' + ELEM_ICON[ge] + ' ' + ELEM_NAME[ge] + '</button>';
      h += '<div class="mine-smith-say" style="border:1px solid #ffd27a">🎁 <b>2환생 특전</b> -- 아직 배우지 않은 검기 하나를 1단계로 무료로 가르쳐 준대요. (한 번만)'
          + (gb ? '<div style="display:flex;flex-wrap:wrap;gap:4px;margin-top:6px">' + gb + '</div>' : '<div style="margin-top:4px;color:#a89ec8">고를 수 있는 속성이 없어요 (모두 이미 배움)</div>') + '</div>';
    } else if (au.gift === 2) h += '<div class="mine-smith-say" style="opacity:.6">🎁 2환생 특전 -- 받았어요</div>';
    else h += '<div class="mine-smith-say" style="opacity:.6">🎁 2환생 특전 -- 2환생을 하면 검기 하나를 1단계로 무료로 받을 수 있어요</div>';
    for (var e = 1; e <= 8; e++) {
      var L = (au.l && au.l[e]) || 0, b = AURA_BOSS[e];
      var head = '<span class="nm"><span style="color:' + AURA_COL[e] + '">' + ELEM_ICON[e] + ' ' + ELEM_NAME[e] + ' 검기</span> ' + L + '/5';
      if (!b) { h += '<div class="mine-smith-row">' + head + '<small>이 속성의 보스는 아직 없어요 (추가 예정)</small></span><button type="button" data-lack="1" disabled>준비 중</button></div>'; continue; }
      if (L >= 5) { h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>끝까지 익혔어요</small></span><button type="button" data-lack="1" disabled>완료</button></div>'; continue; }
      var c = 2 * (L + 1) - 1, hb = oreHave(b[0]), hs = oreHave(b[2]), ok = hb >= c && moon >= c && hs >= 5;
      h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>' + (L + 1) + '단계: ' + ORE_NAME[b[0]] + ' ' + c + ' (보유 ' + hb + ') + 달빛석 ' + c + ' + ' + ORE_NAME[b[2]] + ' 5 (보유 ' + hs + ') · ' + b[1] + '</small></span>'
          + '<button type="button" ' + (ok ? '' : 'data-lack="1" ') + 'onclick="TowerMine.auraLearn(' + e + ')">배우기</button></div>';
    }
    return h + '<div class="mine-smith-say">배운 단계는 인벤토리 🌀 스킬 탭에서 골라 켜고 끌 수 있어요.</div>';
  }
  function auraLearn(e) {
    api('/api/tower-mine-aura-learn?el=' + e).then(function (d) {
      if (d && d.ok) { st.aura = d.aura; if (d.ores) st.ores = d.ores; flashT0 = performance.now(); }
      if (smithOpen) renderSmith(smithNpc);
      msg((d && d.message) || '배우지 못했어요.', 3000);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function auraGift(e) { // [2026-11-05] 2환생 특전
    if (!confirm('🎁 2환생 특전(한 번만)으로 ' + ELEM_NAME[e] + ' 검기 1단계를 받을까요?')) return;
    api('/api/tower-mine-aura-gift?el=' + e).then(function (d) {
      if (d && d.ok) { st.aura = d.aura; flashT0 = performance.now(); }
      if (smithOpen) renderSmith(smithNpc);
      msg((d && d.message) || '받지 못했어요.', 3500);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function auraSet(e, v) {
    api('/api/tower-mine-aura?el=' + e + '&lv=' + v).then(function (d) {
      if (d && d.ok) { st.aura = d.aura; if (smithOpen) renderSmith(smithNpc); msg(v ? '🌀 ' + ELEM_NAME[e] + ' 검기 ' + v + '단계 · 사거리 ' + auraRange() + '칸' : '🌀 ' + ELEM_NAME[e] + ' 검기를 껐어요.', 1800); }
      else msg((d && d.message) || '바꾸지 못했어요.');
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  // [2026-11-07] 3환생 스킬 -- 필살기(⚔️ 위 둥근 버튼: 왼쪽 반 만검진 / 오른쪽 반 일섬참, 쿨타임 공유 2분) + 관리자 쿨타임 초기화. 예전 관리자 전체 공격은 없앴다.
  //  만검진: 쓴 자리 13x13 바닥에 금빛 장판, 5초 동안 검이 연달아 떨어진다(서버 타격은 1초마다 -- 화면이 다음 타격을 부른다). [2026-11-07] "칼이 옆으로 25도" -- 왼쪽 위에서 비스듬히.
  //  일섬참: 화면에 보이는 가장 가까운 몬스터 위에 '斬'이 찍히고 대각선으로 크게 베어진다. 그림 img_loa/s55/fx/sword.png, zan.png.
  var novaFx = [], ultFx = [], ultBusy = false, ultReadyAt = 0, ultSeen = null, ultHudT = -1, ultImgs = {};
  var ULT_ANG = 0.44, ULT_SIN = Math.sin(0.44), ULT_COS = Math.cos(0.44); // [2026-11-07] 만검진 칼 기울기 25도
  var ULT_NAME = ['만검진', '일섬참'], ULT_ZONE_MS = 5000, ULT_COST = [0, 2, 5, 8, 11, 14], GALAXY_G = 70;
  function ultImg(n) {
    if (!ultImgs[n]) { var im = new Image(); im.src = cfg.base.replace(/\/loa$/, '') + '/img_loa/s55/fx/' + n + '.png?v=1'; ultImgs[n] = im; }
    var i = ultImgs[n]; return i.complete && i.naturalWidth ? i : null;
  }
  function ultCd() {
    var u = st && st.ult;
    if (u && u !== ultSeen) { ultSeen = u; ultReadyAt = performance.now() + (u.cd || 0); ultHudT = -1; }
    return Math.max(0, ultReadyAt - performance.now());
  }
  function ultBtn(k) {
    setMenu(false);
    if (!opened || !map || busy || fade || ultBusy || !(st && st.ult)) return;
    if (stunnedMe() || frozenMe()) return;
    var L = (st.ult.l || [])[k] | 0;
    if (!L) { msg('아직 ' + ULT_NAME[k] + '을(를) 배우지 않았어요. 왕도 필살 사범에게 배울 수 있어요.', 2200); return; }
    var cd = ultCd();
    if (cd > 0) { msg('⏳ 필살기는 ' + Math.ceil(cd / 1000) + '초 뒤에 다시 쓸 수 있어요.', 1400); return; }
    ultBusy = true; P.digT0 = performance.now(); P.swing = true; ultImg('sword'); ultImg('zan');
    var mid = map.id;
    apiFast('/api/tower-mine-ult?k=' + k + '&t=0&mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
      ultBusy = false;
      if (!d) return;
      if (d.ult) st.ult = d.ult;
      if (d.reload) { reload(); return; }
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (!d.ok) { if (d.message) msg(d.message, 2000); return; }
      var t = performance.now();
      if (k === 0 && d.zone) {
        ultFx.push({ k: 0, x: d.zone.x, y: d.zone.y, r: d.zone.r || 3, t0: t, sw: ultSwords(d.zone.r || 3, 1 + Math.floor(Math.random() * 2e9)) });
        for (var i = 1; i < 5; i++) (function (n) { setTimeout(function () { ultTick(mid, n); }, n * 1000); })(i);
      }
      if (k === 1) { var m0 = (d.ultMobs || [])[0]; if (m0) { var p0 = ultMobPos(m0); ultFx.push({ k: 1, x: p0.x, y: p0.y, t0: t }); } }
      if (k === 1 && d.dashX != null) { var ox0 = P.x, oy0 = P.y; setPos(d.dashX, d.dashY); ultFx.push({ k: 2, x0: ox0, y0: oy0, x: d.dashX, y: d.dashY, t0: t }); } // [2026-11-08] 일섬참 -- 대상 바로 옆으로 붙는다(지나온 길에 잔상)
      ultApply(d, t, k);
    }).catch(function () { ultBusy = false; });
  }
  function ultTick(mid, n) {
    if (!opened || !map || map.id !== mid) return;
    apiFast('/api/tower-mine-ult?k=0&t=' + n + '&mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
      if (!d || !d.ok) return;
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      ultApply(d, performance.now(), 0);
    }).catch(function () { });
  }
  function ultMobPos(am) { var mm = map.mobs && map.mobs[am.id]; return mm && mm._p ? { x: mm._p.x, y: mm._p.y } : { x: am.row[3], y: am.row[4] }; }
  function ultApply(d, t, k) {
    var exSum = 0, kn = 0;
    (d.ultMobs || []).forEach(function (am, j) {
      var p = ultMobPos(am), dl = k === 1 ? 130 : (j % 10) * 15;
      mobSt[am.id] = mobStOf(am.row, mobSt[am.id]);
      mobHitAt[am.id] = t;
      // [2026-11-07] 만검진 -- 1초 피해를 4번으로 나눠 0.25초 간격으로(숫자 + X자 베기)
      (am.hits || []).forEach(function (h, hi) { var hd = k === 0 ? hi * 250 : 0; novaFx.push({ t0: t + dl + hd, mx: p.x, my: p.y, col: k === 1 ? '#8a6aff' : '#ffb02a' }); floats.push({ x: p.x + (k === 0 ? (hi % 2 ? 0.25 : -0.25) : 0), y: p.y - 0.2, text: (h[2] ? '💥' : '') + h[1], color: h[2] ? '#ffd23f' : k === 1 ? '#e8dcff' : '#ffe9a8', big: k === 1 || !!h[2], t0: t + dl + 60 + hd }); });
      if (am.killed) { exSum += am.exp || 0; kn++; }
    });
    if (d.expReal != null) exSum = d.expReal;
    if (d.bossMsg) { msg(d.bossMsg, 5000); killFeed(d.bossMsg, '#ffe14d'); flashT0 = t; } // [2026-11-07] 필살기로 쓰러뜨린 보스
    if (d.shock) shockSet([]);
    if (k === 1 && (d.ultMobs || []).some(function (am) { return !am.killed; })) floats.push({ x: ultMobPos(d.ultMobs[0]).x, y: ultMobPos(d.ultMobs[0]).y - 0.9, text: '💫 기절 ' + Math.round((d.stunMs || 2000) / 1000) + '초', color: '#ffe94a', t0: t + 300 }); // [2026-11-07] 일섬참 기절
    lootDrops(d.ultDrops, P.x, P.y);
    if (d.ores) st.ores = d.ores;
    if (d.dropGrade != null) { flashT0 = t; weaponLoot(d, P.x, P.y); }
    if (d.mercXp) mercXpApply(d.mercXp);
    if (exSum > 0) killFeed('내 경험치 +' + exSum.toLocaleString() + (kn > 1 ? '  ×' + kn : ''));
    if (d.combat) setCombat(d.combat);
    if (d.levelUp) { flashT0 = t; banner = { text: '🎉 레벨 업! Lv.' + d.combat.lv, t0: t }; }
  }
  function ultReset() {
    api('/api/tower-mine-ult-reset').then(function (d) { if (d && d.ult) st.ult = d.ult; msg((d && d.message) || '초기화하지 못했어요.', 1600); }).catch(function () { });
  }
  /** 버튼 쿨타임 -- 남은 시간 + 어둡게 덮은 부채꼴(초가 바뀔 때만 고친다). */
  function ultHud() {
    if (!el.ultCd || !st || !st.ult) return;
    var cd = ultCd(), s = Math.ceil(cd / 1000);
    if (s === ultHudT) return;
    ultHudT = s;
    if (s <= 0) { el.ultCd.style.display = 'none'; return; }
    var p = Math.min(100, cd / (st.ult.gap || 120000) * 100);
    el.ultCd.style.display = '';
    el.ultCd.style.background = 'conic-gradient(rgba(10,6,16,.72) 0 ' + p + '%, transparent ' + p + '% 100%)';
    el.ultCd.textContent = Math.floor(s / 60) + ':' + ('0' + s % 60).slice(-2);
  }
  function ultRnd(seed) { var s = seed % 2147483647; if (s <= 0) s += 2147483646; return function () { s = s * 16807 % 2147483647; return (s - 1) / 2147483646; }; }
  /** 만검진 -- 떨어질 검 40자루(시각, 칸 안 자리)를 한 번만 정해 둔다. */
  function ultSwords(r, seed) {
    var R = ultRnd(seed), a = [];
    // [2026-11-07] "칼이 떨어지는 속도를 빠르게" -- 반경 3칸(7x7)에 0.058초마다 한 자루. [2026-11-08] 범위 +3칸 -- 넓이에 맞춰 자루 수를 늘린다(빽빽함은 그대로)
    var n = Math.round(80 * Math.pow((r + 0.5) / 3.5, 2)), gap = 4640 / n;
    for (var i = 0; i < n; i++) a.push({ t: 120 + i * gap, x: (R() * 2 - 1) * (r + 0.3), y: (R() * 2 - 1) * (r + 0.3) * 0.75 });
    return a;
  }
  /** 바닥 장판(몬스터·캐릭터보다 먼저). */
  function ultFloorDraw(now, TS, camX, camY) {
    ultFx = ultFx.filter(function (f) { return now - f.t0 < (f.k === 0 ? ULT_ZONE_MS + 400 : f.k === 2 ? 320 : 1100); });
    ultFx.forEach(function (f) {
      if (f.k !== 0) return;
      var t = now - f.t0, cx = f.x * TS - camX + TS / 2, cy = f.y * TS - camY + TS / 2 + TS * 0.2, rad = TS * (f.r + 0.5), fo = t > ULT_ZONE_MS ? Math.max(0, 1 - (t - ULT_ZONE_MS) / 400) : 1, on = Math.min(1, t / 250);
      ctx.save();
      ctx.globalAlpha = 0.35 * on * fo;
      var gr = ctx.createRadialGradient(cx, cy, 0, cx, cy, rad); gr.addColorStop(0, 'rgba(255,214,120,.55)'); gr.addColorStop(1, 'rgba(255,150,40,.1)');
      ctx.fillStyle = gr; ctx.beginPath(); ctx.ellipse(cx, cy, rad, rad * 0.62, 0, 0, Math.PI * 2); ctx.fill();
      ctx.globalAlpha = 0.9 * on * fo; ctx.strokeStyle = '#ffcf5a'; ctx.lineWidth = 2.5 * dpr;
      ctx.beginPath(); ctx.ellipse(cx, cy, rad, rad * 0.62, 0, 0, Math.PI * 2); ctx.stroke();
      ctx.lineWidth = 1 * dpr; ctx.beginPath(); ctx.ellipse(cx, cy, rad * 0.86, rad * 0.53, 0, 0, Math.PI * 2); ctx.stroke();
      ctx.fillStyle = '#fff3a0';
      for (var k = 0; k < 12; k++) { var a = k / 12 * Math.PI * 2 + t / 900; ctx.fillRect(cx + Math.cos(a) * rad * 0.93 - 2 * dpr, cy + Math.sin(a) * rad * 0.575 - 2 * dpr, 4 * dpr, 4 * dpr); }
      ctx.restore();
    });
  }
  /** 떨어지는 검 / 일섬참(몬스터·캐릭터 다음). */
  function ultFxDraw(now, TS, camX, camY, W, H) {
    ultFx.forEach(function (f) {
      var t = now - f.t0;
      if (f.k === 0) {
        var sw = ultImg('sword'), fo = t > ULT_ZONE_MS ? Math.max(0, 1 - (t - ULT_ZONE_MS) / 400) : 1, sm = ctx.imageSmoothingEnabled;
        ctx.imageSmoothingEnabled = false;
        f.sw.forEach(function (s) {
          var e = t - s.t;
          if (e < 0 || e > 700) return;
          var x = (f.x + s.x) * TS - camX + TS / 2, y = (f.y + s.y) * TS - camY + TS * 0.7, sh = TS * 1.6, sww = sh / 3, fall = 90, k2 = Math.min(1, e / fall), d = (1 - k2) * (1 - k2) * TS * 5; // [2026-11-07] 떨어지는 시간 0.18 -> 0.09초
          ctx.save(); ctx.translate(x - ULT_SIN * d, y - ULT_COS * d); ctx.rotate(-ULT_ANG); // 칼끝이 닿는 점을 기준으로 25도 기울여 날아온다
          ctx.globalAlpha = (e < fall ? 1 : Math.max(0, 1 - (e - fall) / 520)) * fo;
          if (sw) ctx.drawImage(sw, -sww / 2, -sh, sww, sh);
          if (e < fall) { ctx.globalAlpha = 0.3 * fo; ctx.fillStyle = '#fff8d0'; ctx.fillRect(-dpr, -sh - TS * 1.4, 2 * dpr, TS * 1.4); } // 떨어지는 빛줄기
          ctx.restore();
          if (e >= fall && e < fall + 220) { // 꽂히는 순간 -- 둥근 충격 + 불똥
            var q = (e - fall) / 220;
            ctx.globalAlpha = (1 - q) * fo; ctx.strokeStyle = '#fff3a0'; ctx.lineWidth = 2 * dpr;
            ctx.beginPath(); ctx.ellipse(x, y, TS * 0.7 * q + 4, TS * 0.25 * q + 2, 0, 0, Math.PI * 2); ctx.stroke();
            ctx.fillStyle = '#ffd27a';
            for (var p = 0; p < 6; p++) { var a2 = p * 1.05; ctx.fillRect(x + Math.cos(a2) * TS * 0.6 * q, y - Math.abs(Math.sin(a2)) * TS * 0.5 * q, 3 * dpr, 3 * dpr); }
          }
        });
        ctx.imageSmoothingEnabled = sm; ctx.globalAlpha = 1;
        return;
      }
      if (f.k === 2) { // [2026-11-08] 일섬참으로 붙을 때 지나온 길 -- 흰 빛줄기가 빠르게 사라진다
        var qd = t / 320, ax = f.x0 * TS - camX + TS / 2, ay = f.y0 * TS - camY + TS / 2, bx = f.x * TS - camX + TS / 2, by = f.y * TS - camY + TS / 2;
        ctx.lineCap = 'round'; ctx.globalAlpha = Math.max(0, 1 - qd) * 0.5; ctx.strokeStyle = '#b8a0ff'; ctx.lineWidth = 12 * dpr;
        ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.stroke();
        ctx.globalAlpha = Math.max(0, 1 - qd); ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 3 * dpr; ctx.stroke();
        ctx.lineCap = 'butt'; ctx.globalAlpha = 1;
        return;
      }
      // 일섬참
      var zan = ultImg('zan'), tx = f.x * TS - camX + TS / 2, ty = f.y * TS - camY + TS / 2;
      var q0 = Math.min(1, t / 110), al = t < 110 ? q0 : t > 700 ? Math.max(0, 1 - (t - 700) / 400) : 1, sc = 1.5 - 0.5 * (1 - (1 - q0) * (1 - q0));
      if (!f.o) { ctx.globalAlpha = 0.3 * al; ctx.fillStyle = '#0c0618'; ctx.fillRect(0, 0, W, H); } // [2026-11-08] 다른 사람 것은 화면을 어둡게 하지 않는다
      var sz = TS * 4.2 * sc;
      if (zan) { ctx.globalAlpha = al * 0.95; ctx.drawImage(zan, tx - sz / 2, ty - sz / 2, sz, sz); }
      if (t > 120) {
        var k3 = Math.min(1, (t - 120) / 120), fo3 = t > 500 ? Math.max(0, 1 - (t - 500) / 400) : 1;
        ctx.lineCap = 'round'; ctx.globalAlpha = fo3 * 0.6; ctx.strokeStyle = '#b8a0ff'; ctx.lineWidth = 14 * dpr;
        ctx.beginPath(); ctx.moveTo(tx - TS * 2.2, ty - TS * 1.6); ctx.lineTo(tx - TS * 2.2 + TS * 4.4 * k3, ty - TS * 1.6 + TS * 3.2 * k3); ctx.stroke();
        ctx.globalAlpha = fo3; ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 4 * dpr; ctx.stroke();
        ctx.lineCap = 'butt';
      }
      if (t < 160 && !f.o) { ctx.globalAlpha = (1 - t / 160) * 0.5; ctx.fillStyle = '#e8e0ff'; ctx.fillRect(0, 0, W, H); }
      ctx.globalAlpha = 1;
    });
  }
  // [2026-11-07] 돌개베기(돌개 사범) -- st.whirl = { l: 배운 단계[9], a: 켠 단계[9] }. 공격할 때 내 둘레 8칸, 그림은 돌개바람.
  function whirlEls() { var a = (st && st.whirl && st.whirl.a) || [], out = []; for (var e = 1; e <= 8; e++) if (a[e] > 0) out.push(e); return out; }
  function whirlSet(e, v) {
    api('/api/tower-mine-whirl?el=' + e + '&lv=' + v).then(function (d) {
      if (d && d.ok) { st.whirl = d.whirl; if (smithOpen) renderSmith(smithNpc); msg(v ? '🌪️ ' + ELEM_NAME[e] + ' 돌개베기 ' + v + '단계' : '🌪️ ' + ELEM_NAME[e] + ' 돌개베기를 껐어요.', 1800); }
      else msg((d && d.message) || '바꾸지 못했어요.');
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function whirlLearn(e) {
    api('/api/tower-mine-whirl-learn?el=' + e).then(function (d) {
      if (d && d.ok) { st.whirl = d.whirl; if (d.ores) st.ores = d.ores; flashT0 = performance.now(); }
      if (smithOpen) renderSmith(smithNpc);
      msg((d && d.message) || '배우지 못했어요.', 3000);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function ultGift(k) { // [2026-11-08] 3환생 특전
    if (!confirm('🎁 3환생 특전(한 번만)으로 ' + ULT_NAME[k] + ' 1레벨을 받을까요?')) return;
    api('/api/tower-mine-ult-gift?k=' + k).then(function (d) {
      if (d && d.ok) { st.ult = d.ult; flashT0 = performance.now(); }
      if (smithOpen) renderSmith(smithNpc);
      msg((d && d.message) || '받지 못했어요.', 3500);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function ultLearn(k) {
    api('/api/tower-mine-ult-learn?k=' + k).then(function (d) {
      if (d && d.ok) { st.ult = d.ult; if (d.ores) st.ores = d.ores; flashT0 = performance.now(); }
      if (smithOpen) renderSmith(smithNpc);
      msg((d && d.message) || '배우지 못했어요.', 3000);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  var SK3_LOCK = '<div class="mine-smith-say" style="border:1px solid #ffd27a">🔒 세 번째 환생을 마친 사람에게만 가르쳐 준대요.</div>';
  function whirlNpcHtml(n) {
    var wl = st && st.whirl, moon = oreHave(MOON_G);
    var h = npcHead('🌪️ ' + (n ? n.name : '돌개 사범'), false) + talkBlock(n, n ? n.line : '');
    if (!wl || !st.ult) return h + SK3_LOCK;
    h += '<div class="mine-smith-say">공격하면 내 둘레 8칸을 한꺼번에 베어요. 단계마다 공격력의 5% 속성 피해(최대 25%). 8속성 모두 배울 수 있어요. n단계 = 보스 드랍템 (2n-1) + 달빛석 (2n-1) + 세공된 바다 보석 5개(보스가 없는 속성은 별빛석 (2n-1) + 달빛석 (2n-1)) · 달빛석 보유 ' + moon + '</div>';
    for (var e = 1; e <= 8; e++) {
      var L = (wl.l && wl.l[e]) || 0, b = AURA_BOSS[e] || [6, '보스가 없는 속성 -- 별빛석으로', 0]; // [2026-11-08] "돌개는 모든 속성" -- 보스가 없는 속성은 별빛석 + 달빛석
      var head = '<span class="nm"><span style="color:' + AURA_COL[e] + '">' + ELEM_ICON[e] + ' ' + ELEM_NAME[e] + ' 돌개베기</span> ' + L + '/5';
      if (L >= 5) { h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>끝까지 익혔어요</small></span><button type="button" data-lack="1" disabled>완료</button></div>'; continue; }
      var c = 2 * (L + 1) - 1, hb = oreHave(b[0]), hs = b[2] ? oreHave(b[2]) : 99, ok = hb >= c && moon >= c && hs >= 5;
      h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>' + (L + 1) + '단계: ' + ORE_NAME[b[0]] + ' ' + c + ' (보유 ' + hb + ') + 달빛석 ' + c + (b[2] ? ' + ' + ORE_NAME[b[2]] + ' 5 (보유 ' + hs + ')' : '') + ' · ' + b[1] + '</small></span>'
          + '<button type="button" ' + (ok ? '' : 'data-lack="1" ') + 'onclick="TowerMine.whirlLearn(' + e + ')">배우기</button></div>';
    }
    return h + '<div class="mine-smith-say">배운 단계는 인벤토리 🌀 스킬 탭에서 골라 켜고 끌 수 있어요.</div>';
  }
  function ultNpcHtml(n) {
    var u = st && st.ult, gx = oreHave(GALAXY_G);
    var h = npcHead('⚔️ ' + (n ? n.name : '필살 사범'), false) + talkBlock(n, n ? n.line : '');
    if (!u) return h + SK3_LOCK;
    // [2026-11-08] 3환생 특전 -- 아직 안 배운 필살기 하나를 1레벨로 무료(한 번)
    if (u.gift === 1) {
      var gb = '';
      [['🌧️', '만검진'], ['💥', '일섬참']].forEach(function (s, k) { if (!((u.l || [])[k] | 0)) gb += '<button type="button" class="mine-mini-btn" onclick="TowerMine.ultGift(' + k + ')">' + s[0] + ' ' + s[1] + '</button>'; });
      h += '<div class="mine-smith-say" style="border:1px solid #ffd27a">🎁 <b>3환생 특전</b> -- 필살기 하나를 1레벨로 무료로 가르쳐 준대요. (한 번만)'
          + (gb ? '<div style="display:flex;flex-wrap:wrap;gap:4px;margin-top:6px">' + gb + '</div>' : '<div style="margin-top:4px;color:#a89ec8">고를 수 있는 필살기가 없어요 (모두 이미 배움)</div>') + '</div>';
    } else if (u.gift === 2) h += '<div class="mine-smith-say" style="opacity:.6">🎁 3환생 특전 -- 받았어요</div>';
    h += '<div class="mine-smith-say">두 필살기를 모두 쓸 수 있어요. ⚔️ 버튼 위 둥근 버튼의 왼쪽 반이 만검진, 오른쪽 반이 일섬참 -- 쿨타임 2분을 함께 써요. 레벨마다 은하수 조각 2 / 5 / 8 / 11 / 14개 · 보유 ' + gx + '</div>';
    [['🌧️', '만검진', '쓴 자리 13x13에 5초 동안 검이 떨어지는 장판(1초마다 맞은 적의 받는 피해 +10%, 최대 50%) -- 1초마다 공격력의 ', function (lv) { return 125 + 20 * (lv - 1); }, '%'], ['💥', '일섬참', '화면에 보이는 가장 가까운 몬스터 하나를 공격력의 ', function (lv) { return (700 + 150 * (lv - 1)) + '%로 베고 ' + (lv + 1) + '초 기절'; }, '']].forEach(function (s, k) {
      var L = (u.l || [])[k] | 0, head = '<span class="nm">' + s[0] + ' ' + s[1] + ' ' + L + '/5';
      if (L >= 5) { h += '<div class="mine-smith-row"><img src="' + oreIcon(GALAXY_G) + '" alt="">' + head + '<small>' + s[2] + s[3](5) + s[4] + ' · 끝까지 익혔어요</small></span><button type="button" data-lack="1" disabled>완료</button></div>'; return; }
      var c = ULT_COST[L + 1];
      h += '<div class="mine-smith-row"><img src="' + oreIcon(GALAXY_G) + '" alt="">' + head + '<small>' + (L + 1) + '레벨: ' + s[2] + s[3](L + 1) + s[4] + ' · 은하수 조각 ' + c + '개</small></span>'
          + '<button type="button" ' + (gx >= c ? '' : 'data-lack="1" ') + 'onclick="TowerMine.ultLearn(' + k + ')">배우기</button></div>';
    });
    return h;
  }
  function atkBtn() {
    setMenu(false); // [2026-11-05]
    if (stunnedMe()) { msg(stunMsg(), 1500); return; } // [2026-11-04]
    if (frozenMe()) { iceStruggle(performance.now()); msg('🧊 얼어붙었어요! ⬅️➡️ 움직이거나 ⚔️ 공격해서 얼음을 깨 보세요. 동료가 곁(2칸)에 와도 풀려요.'); return; }
    var a = !P.moving && adjMob();
    if (a) { attack(a); return; }
    if (!P.moving && (auraRange() > 0 || whirlEls().length)) { auraAttack(); return; } // [2026-11-07] 돌개베기 // [2026-11-04] 옆에 몬스터가 없어도 검기는 날아간다
    // [2026-10-28] "아무것도 없을 때도 무기 휘두르는 액션" -- 서버에 묻지 않고 그 자리에서 휘두르기만
    var sn = performance.now();
    if (busy || P.moving || sn - lastAtkT < 420) return;
    lastAtkT = sn; P.digT0 = sn; P.swing = true;
  }
  // [2026-10-28] ☰ 메뉴 -- 장비/마을로/최근 캔 곳/나가기(고르면 닫힌다). 자동채광은 채광 버튼 아래로 뺐다.
  function setMenu(on) {
    if (!el.menu) return;
    el.menu.classList.toggle('open', on);
    el.menuBtn.classList.toggle('open', on);
    el.menuBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
  }
  function toggleMenu() { setMenu(!el.menu.classList.contains('open')); }
  function menuGo(k) {
    setMenu(false);
    if (k === 'equip') openEquip();
    else if (k === 'merc') { if (opened && map) { mercSel = null; openSmith({ name: '용병 관리', line: '', kind: 'mercmgr' }, 'mercmgr'); } } // [2026-11-06] 용병 관리
    else if (k === 'home') goVillage();
    else if (k === 'log') toggleLog();
    else if (k === 'exit') close();
  }
  function act() {
    if (frozenMe()) { msg('🧊 얼어붙었어요! ⬅️➡️ 움직이거나 ⚔️ 공격해서 얼음을 깨 보세요. 동료가 곁(2칸)에 와도 풀려요.'); return; } // [2026-10-28]
    var a = !P.moving && adjMob();
    if (a) attack(a); else dig();
  }
  // [2026-10-24] 공격(바람의나라식: 바로 옆 몬스터를 때리고, 살아남은 몬스터는 그 자리에 멈춰 반격)
  function attack(a) {
    if (!opened || !map || busy || fade) return;
    var now = performance.now();
    if (now - lastAtkT < 420) return;
    if (atkBusy) { if (now - P.digT0 >= 420) { P.dir = a.dir; P.digT0 = now; P.swing = true; } return; } // [2026-11-03] 응답을 기다리는 중에도 휘두르기는 보인다 -- [2026-11-04] 공격 간격(lastAtkT)은 건드리지 않는다(응답이 오면 바로 다음 공격)
    lastAtkT = now; atkBusy = true;
    P.dir = a.dir; P.digT0 = now; P.swing = true; // [2026-10-28] 무기 휘두르기
    auraFxGo(); // [2026-11-04] 검기
    if (a.m) mobHitAt[a.m.id] = performance.now(); // [2026-11-03] 서버 응답 전에 맞은 몬스터가 바로 흔들린다(지연 숨기기)
    var m = a.m, mp = m._p;
    if (mp) { // [2026-11-03] "때리는 동안 몬스터가 제 길로 가다 끌려온다" -- 응답 전부터 화면에서도 그 칸에 세운다(서버도 mc 칸에 세운다)
      var os = mobSt[m.id], tn = nowSrv();
      mobSt[m.id] = { hp: os ? os.hp : 1, du: 0, fx: mp.cx, fy: mp.cy, fu: Math.max(os && os.fu || 0, tn + 8000), sl: !!(os && os.sl), su: os ? os.su || 0 : 0, loc: tn };
    }
    var mc =mp ? mp.cy * map.w + mp.cx : -1; // 화면이 보던 몬스터 칸 -- 서버가 그 칸에 멈춰 세운다(순간이동처럼 안 보이게)
    apiFast('/api/tower-mine-attack?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&mob=' + m.id + '&mc=' + mc + '&d=' + P.dir + verQ()).then(function (d) {
      atkBusy = false;
      if (!d || d.tooFast) return;
      if (d.reload) { reload(); return; }
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (d.mobDeadUntil != null) mobSt[m.id] = mobStOf([m.id, d.mobHp, d.mobDeadUntil, d.mobFx, d.mobFy, d.mobFrozenUntil, d.mobStealth ? 1 : 0], mobSt[m.id]);
      (d.mobRows || []).forEach(function (a) { mobSt[a[0]] = mobStOf(a, mobSt[a[0]]); }); // [2026-10-28] 분신술 -- 분신/두목 상태
      if (!d.ok) { if (d.message && !d.far) msg(d.message); return; }
      if (d.harpyImmune) { var hit0 = performance.now(); floats.push({ x: mp ? mp.x : P.x, y: (mp ? mp.y : P.y) - 0.2, text: '🪶 무효', color: '#bfeee0', big: false, t0: hit0 }); if (hit0 - harpyImmT > 6000) { harpyImmT = hit0; msg('🪶 하피가 날고 있어 일반 공격이 닿지 않아요. 검기와 필살기만 들어가요.', 3200); } } // [2026-11-08]
      if (d.veinBroken) veinBroke(); // [2026-10-28] 설산 동굴 -- 반격에 맞음
      if (d.healUntil != null) healUntil = d.healUntil; // [2026-10-28] 재생의 장갑
      lastSaved = map.id + ':' + P.x + ',' + P.y;
      var t = performance.now(), fxp = mp ? mp.x : P.x, fyp = mp ? mp.y : P.y;
      var atkEl = st.ench && st.ench.left > 0 && st.ench.el ? ELEM_ICON[st.ench.el] : ''; // [2026-10-30] "속성 공격 시 표기" -- 부여한 속성 아이콘, 상성이면 x1.5
      // [2026-10-28] 닌자 -- 은신 회피("💨 은신!"), 분신("💨 펑!"), 은신했던 닌자의 2배 반격("2배!")
      if (d.evade) floats.push({ x: fxp, y: fyp, text: d.stealthLeft ? '💨 은신! (' + d.stealthLeft + ')' : '💨 은신!', color: '#c8c8ff', big: true, t0: t }); // [2026-10-28] 닌자 두목 -- 남은 공격 수
      else if (d.clone) floats.push({ x: fxp, y: fyp, text: '💨 펑! 분신', color: '#d8c8ff', big: true, t0: t });
      else if (d.hits) d.hits.forEach(function (hv, hi) { var hc = d.hitCrits && d.hitCrits[hi]; floats.push({ x: fxp + (hi ? 0.35 : -0.25), y: fyp - hi * 0.35, text: (d.assassinHit ? '🗡️' : '') + (hc ? '💥' : '') + atkEl + hv + (d.elHit ? ' x1.5' : ''), color: d.elHit ? '#7fe8ff' : hc ? '#ffdd33' : '#ff5a4a', big: !!hc, t0: t + hi * 140 }); }); // [2026-10-30] 쌍격의 장갑 -- 숫자 두 개
      else floats.push({ x: fxp, y: fyp, text: (d.assassinHit ? '🗡️ 2배! ' : '') + (d.crit ? '💥' : '') + atkEl + d.dmg + (d.elHit ? ' x1.5' : ''), color: d.assassinHit ? '#ff3a6a' : d.elHit ? '#7fe8ff' : d.crit ? '#ffdd33' : '#ff5a4a', big: !!(d.crit || d.elHit || d.assassinHit), t0: t }); // [2026-10-29] 속성 상성 x1.5 / [2026-10-30] 자객의 목걸이 2배
      auraShow(d, fxp, fyp, t); // [2026-11-04] 검기 -- 속성 숫자 + 너머 몬스터
      if (d.simDown) msg('🎯 ' + ((map.mobTypes[m.k] || {}).name || '보스 허수아비') + '의 체력이 0이 됐어요! 3초 뒤 다 찬 채로 다시 일어나요.', 2400); // [2026-11-07] 보스 허수아비
      if ('pStealth' in d) pStealth = d.pStealth; // [2026-10-30] 자객의 목걸이
      if (d.assassinOn) floats.push({ x: P.x, y: P.y - 0.6, text: '🥷 은신!', color: '#c8c8ff', big: true, t0: t + 80 });
      if (d.dodged) floats.push({ x: P.x, y: P.y - 0.3, text: '💨 회피', color: '#c8c8ff', big: true, t0: t + 180 });
      if (d.enchLeft != null && st.ench) { st.ench.left = d.enchLeft; if (!d.enchLeft) st.ench.el = 0; updEnch(); }
      if (d.counter) floats.push({ x: P.x, y: P.y - 0.3, text: (d.stealthHit ? '🗡️ 2배! ' : '') + '-' + d.counter, color: d.stealthHit ? '#ff3a6a' : '#ff9a3a', big: !!d.stealthHit, t0: t + 180 });
      if (d.split) flashT0 = t; // [2026-10-28] 분신술 -- 가운데 멘트 없이 번쩍임만
      setCombat(d.combat);
      if (d.killed) {
        if (d.ores) st.ores = d.ores;
        lootDrops(d.drops, fxp, fyp); // [2026-11-04] "드랍 아이템 효과가 가운데 상자를 가린다" -- 화면 구석 카드 -> 왼쪽 아래로 빨려 들어감
        if (d.dropGrade != null) api('/api/tower-mine').then(function (s2) { if (s2 && !s2.error) { st.equip = s2.equip; st.gear = s2.gear; if (s2.ores) st.ores = s2.ores; if (s2.look) st.look = s2.look; if (smithOpen) renderSmith(smithNpc); } }).catch(function () { }); // [2026-10-29] "무기를 얻었는데 인벤토리에 안 보인다" -- 장비 목록/조각을 바로 다시 받는다
        if (d.dropGrade != null) { flashT0 = t; weaponLoot(d, fxp, fyp); } // [2026-10-29] 같은 등급 이하 = 분해 // [2026-10-29] 닌자의 검 -- 등급은 색으로만
        if (d.expGain > 0) floats.push({ x: fxp, y: fyp + 0.4, text: '+' + d.expGain + ' EXP', color: '#9fe0ff', t0: t + 100 }); // [2026-11-07] 경험치가 가득 차 안 오르면 안 띄운다
        if (d.bossKilled || d.shrineKey || (d.ninjaKills && d.ninjaKills % 10 === 0)) msg(d.message || '', 3600); // [2026-11-05] 처치 문구는 왼쪽 경험치 줄로(auraShow) -- 보스 처치/신궁 열쇠/수색 진행만 가운데에
      }
      if (d.levelUp) { flashT0 = t; banner = { text: '🎉 레벨 업! Lv.' + d.combat.lv, t0: t }; }
      if (d.rebirthReadyNow) { rbHintShown = true; flashT0 = t; banner = { text: '✨ 환생 가능! 왕도 성당 신부님께', t0: t }; } // [2026-10-28] 99레벨 경험치가 방금 가득
      // [2026-10-25] 최대 체력 잠금 -- 새 전투마다 10%(최대 50%), 레벨업하면 풀림
      if (d.hpLockUp) floats.push({ x: P.x, y: P.y - 0.9, text: '🔒 최대 체력 -' + d.hpLockUp + '%', color: '#c9c3d6', t0: t + 300 });
      if (d.hpLockFree) msg('🔓 레벨 업! 잠겼던 최대 체력이 풀렸어요.', 2600);
      if (d.died && d.state) {
        setAuto(false);
        busy = true;
        fadeOutThen(function () { return Promise.resolve(d); }).then(function () { return enterState(d.state); })
          .then(function () { fadeIn('💫 마을에서 깨어났어요'); msg(d.message || '', 3600); });
      }
    }).catch(function () { atkBusy = false; });
  }
  function gearAct(path) {
    if (busy) return;
    busy = true;
    api(path).then(function (d) {
      busy = false;
      applyGear(d);
      var text = d.message || d.error || '완료';
      if (smithView === 'captain') captainSay = text; // 선장 대답은 창 안에(먹튀 대사 포함)
      else msg(text, 3200);
      if (d.craftedRing || d.questStarted || d.enhOk) flashT0 = performance.now(); // [2026-11-06] 강화 성공
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function craft(code) { gearAct('/api/tower-mine-craft?code=' + encodeURIComponent(code)); }
  // [2026-10-28] 광석 전문가(광석 100 -> 괴, 45%) / 보석 세공사(보석 5 -> 세공된 보석, 25%) / 왕도 장인(세공된 보석 -> 최대 내구도 +30, 1000 이상일 때)
  var cityShop = null;
  function mercHire(t) { // [2026-11-05] 왕도 용병소 -- 더 센 용병으로 바꿔 고용
    if (busy) return;
    mercAskI = t; // [2026-11-06] 브라우저 확인창 대신 게임 안 팝업(t = 후보 칸, null = 닫기)
    if (smithOpen) renderSmith(smithNpc);
  }
  var mercAskI = null;
  function mercHireGo(t) {
    if (busy) return;
    mercAskI = null;
    busy = true;
    api('/api/tower-mine-merc?action=hire&tier=' + t).then(function (d) {
      busy = false;
      if (!d) return;
      if (d.ok) flashT0 = performance.now();
      mercAfter(d);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function cityBuy() { // [2026-11-05] 왕도 상인 -- 행운의 주문서
    if (busy) return;
    busy = true;
    api('/api/tower-mine-cityshop?action=buy').then(function (d) {
      busy = false;
      if (!d) return;
      cityShop = d;
      if (d.luckUntil != null) st.luckUntil = d.luckUntil;
      if (d.ores) st.ores = d.ores;
      if (d.ok) flashT0 = performance.now();
      msg(d.message || '완료', 3200);
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function archiBuy(code) { gearAct('/api/tower-mine-archishop?action=buy&code=' + encodeURIComponent(code)); } // [2026-10-28] 다도해 상인
  function smelt(grade, times) { gearAct('/api/tower-mine-smelt?grade=' + grade + '&times=' + times); }
  function gemCut(gem, times) { gearAct('/api/tower-mine-gemcut?gem=' + gem + '&times=' + times); }
  function artisan(gem) { gearAct('/api/tower-mine-artisan?gem=' + gem); }
  function craftNpcHtml(n) {
    var h = npcHead((n.kind === 'smelter' ? '🔥 ' : n.kind === 'gemcutter' ? '💎 ' : '🔨 ') + n.name, false) + talkBlock(n, n.line);
    var row = function (icon, name, sub, btns) { return '<div class="mine-smith-row"><img src="' + oreIcon(icon) + '" alt=""><span class="nm">' + name + '<small>' + sub + '</small></span>' + btns + '</div>'; };
    var btn = function (fn, a, t, ok) { return '<button type="button" ' + (ok ? '' : 'data-lack="1" ') + 'onclick="TowerMine.' + fn + '(' + a + ', ' + t + ')">' + t + '번</button>'; };
    if (n.kind === 'smelter') {
      h += '<div class="mine-smith-say">광석 100개 -> 괴 1개 (성공 45%). 괴는 좋은 장비의 재료예요.</div>';
      for (var g = 1; g <= 5; g++) { var have = oreHave(g); h += row(39 + g, ORE_NAME[g] + ' 100 -> ' + ORE_NAME[39 + g], '보유 ' + have + ' · ' + ORE_NAME[39 + g] + ' ' + oreHave(39 + g) + '개', btn('smelt', g, 1, have >= 100) + btn('smelt', g, 10, have >= 1000)); }
      return h;
    }
    if (n.kind === 'gemcutter') {
      h += '<div class="mine-smith-say">보석 5개 -> 세공된 보석 1개 (성공 25%). 세공된 보석은 고급 장비와 왕도 장인에게 쓰여요.</div>';
      CUT_LIST.forEach(function (cl, i) { var hv = oreHave(cl[0]); h += row(cl[1], ORE_NAME[cl[0]] + ' 5 -> ' + ORE_NAME[cl[1]], '보유 ' + hv + ' · 세공 ' + oreHave(cl[1]) + '개', btn('gemCut', i, 1, hv >= 5) + btn('gemCut', i, 10, hv >= 50)); }); // [2026-10-28] 빙석/바다 보석도
      return h;
    }
    var mx = st.durabilityMax || 0;
    var aRound = Math.max(0, Math.floor((mx - 1000) / 30)) + 1, aCost = Math.floor((aRound - 1) / 10) + 1; // [2026-11-13] 10회차마다 보석 +1개(서버 mineArtisanCost)
    h += '<div class="mine-smith-say">곡괭이 최대 내구도가 1000 이상이면, 세공된 보석으로 최대 내구도 +30 (지금 ' + (st.durability || 0) + ' / ' + mx + ')'
        + (mx >= 1000 ? '<br>다음은 <b>' + aRound + '회차</b> -- 같은 보석 <b>' + aCost + '개</b> (10회차마다 1개씩 늘어요)' : '') + '</div>';
    var any = false;
    CUT_LIST.forEach(function (cl, j) {
      var c = oreHave(cl[1]);
      if (!c) return;
      any = true;
      h += row(cl[1], ORE_NAME[cl[1]], '보유 ' + c + '개', '<button type="button" ' + (mx >= 1000 && c >= aCost ? '' : 'data-lack="1" ') + 'onclick="TowerMine.artisan(' + j + ')">' + aCost + '개 박아 넣기</button>');
    });
    if (!any) h += '<div class="mine-smith-say">세공된 보석이 없어요. 마을의 보석 세공사에게서 만들 수 있어요.</div>';
    return h;
  }
  function equipRing(code) { gearAct('/api/tower-mine-ring?code=' + encodeURIComponent(code)); }
  function synth(times) { gearAct('/api/tower-mine-synth?times=' + times); }
  // [2026-11-06] 은하수 조각 -- 달빛석 1 + 칠요석 1(16강부터 강화 재료)
  function galaxyHtml() {
    var gm = st.galaxyMax || 0, can = gm > 0 ? '' : 'data-lack="1" ';
    return '<div class="mine-smith-say">달빛석의 음기에 칠요석을 하나 녹여 넣으면... 밤하늘을 통째로 가둔 듯한 조각이 되지. 아주 높은 강화에 쓴다더군.</div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(70) + '" alt=""><span class="nm">은하수 조각<small>보유 ' + oreHave(70) + '개 · 지금 ' + gm + '개 만들 수 있어요</small>'
        + costHtml([{ grade: 20, name: ORE_NAME[20], have: oreHave(20), need: 1 }, { grade: 18, name: ORE_NAME[18], have: oreHave(18), need: 1 }]) + '</span>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synthGalaxy(1)">1개</button>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synthGalaxy(9999)">최대</button></div>';
  }
  function synthGalaxy(times) { gearAct('/api/tower-mine-synth-galaxy?times=' + times); }
  function captain(action) { gearAct('/api/tower-mine-captain?action=' + action); }
  var smithNpc = null;
  function openSmith(n, view) {
    talkPage = {}; // [2026-11-06] 나눈 대사는 창을 새로 열면 처음부터
    smithNpc = n;
    smithOpen = true;
    smithView = view || 'menu';
    tryLook = null; // [2026-10-24] 명품상점 입어보기 초기화
    captainSay = '';
    keys = []; drag = null;
    renderSmith(n);
    el.smith.style.display = 'block';
    el.ov.classList.add('npc-open'); // [2026-10-25] 나가기 버튼 등을 숨긴다
  }
  function smithGo(v) { smithView = v; renderSmith(smithNpc); }
  function closeSmith() {
    smithOpen = false;
    el.smith.style.display = 'none';
    if (el.ov) el.ov.classList.remove('npc-open');
  }
  function pickUpgrade(grade, times) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-pick-upgrade?grade=' + grade + '&times=' + times).then(function (d) {
      busy = false;
      if (d.durability != null) { st.durability = d.durability; st.durabilityMax = d.durabilityMax; }
      if (d.ores) st.ores = d.ores;
      showStamina();
      if (smithOpen) renderSmith(smithNpc);
      msg(d.message || d.error || '강화하지 못했어요.', 2800);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  /** 화면을 검게 덮으면서(FADE_OUT_MS) 서버 요청 work()를 함께 보내고, 둘 다 끝나면 응답을 넘긴다. */
  function fadeOutThen(work) {
    fade = { mode: 'out', t0: performance.now() };
    var wait = new Promise(function (r) { setTimeout(r, FADE_OUT_MS); });
    return Promise.all([work(), wait]).then(function (res) { return res[0]; });
  }
  function fadeIn(label) {
    fade = { mode: 'in', t0: performance.now() };
    if (label) banner = { text: label, t0: performance.now() };
  }
  // [2026-10-28] 피라미드 보상 상자 -- 사막의 심장(맵 주기마다 한 번)
  var chestBusy = false;
  function openChest(n) {
    if (chestBusy) return;
    chestBusy = true;
    api('/api/tower-mine-chest' + (n.kind === 'antchest' ? '?kind=ant' : '')).then(function (d) {
      chestBusy = false;
      if (d && d.ores) st.ores = d.ores;
      if (d && d.ok) {
        flashT0 = performance.now();
        if (d.grade) { var im = new Image(); im.src = oreIcon(d.grade); floats.push({ x: n.x, y: n.y - 0.6, text: '+1 사막의 심장', color: '#ffc04a', big: true, icon: im, t0: performance.now() }); }
        (d.drops || []).forEach(function (w, i) { // 보석이 차례로 솟는다
          var gi = new Image(); gi.src = oreIcon(w.grade);
          floats.push({ x: n.x + ((i % 3) - 1) * 0.9, y: n.y - 1.2 - Math.floor(i / 3) * 0.5, text: '+' + w.qty + ' ' + w.name, color: (ORE_COL[w.grade] || ORE_COL[1])[1], icon: gi, t0: performance.now() + 220 + i * 120 });
        });
      }
      msg((d && (d.message || d.error)) || '상자를 열지 못했어요.', 4200);
    }).catch(function () { chestBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  // [2026-10-28] 사막 서쪽(DESERT_FALL_X 왼쪽)을 걸으면 한 칸마다 ANT_FALL_P 확률로 개미굴에 빠진다
  var ANT_FALL_X = 0.3, ANT_FALL_P = 0.012, ANT_GATE_LV = 41;
  /** [2026-11-04] 사막 서쪽 방벽(x = w * 0.3) -- 동쪽에서 서쪽으로 넘어가는 칸이고 Lv.41 미만이면 막는다(서쪽에서 나오는 건 된다). */
  function antGate(x) {
    if (!map || map.id !== 'desert') return false;
    var wx = Math.floor(map.w * ANT_FALL_X);
    return x < wx && P.x >= wx && myLv() < ANT_GATE_LV;
  }
  function antFallCheck() {
    if (!map || map.id !== 'desert' || busy || fade || smithOpen || P.x >= map.w * ANT_FALL_X || Math.random() >= ANT_FALL_P) return;
    setAuto(false);
    flashT0 = performance.now();
    travel('/api/tower-mine-antfall?mapId=desert&x=' + P.x + '&y=' + P.y, '모래가 꺼질 뻔했지만 무사했어요.');
  }
  /** 이동 요청 공통 -- 성공하면 새 상태로 바꾼 뒤 페이드 인, 실패하면 원래 화면으로 페이드 인. */
  function travel(path, failText) {
    busy = true;
    fadeOutThen(function () { return api(path); }).then(function (d) {
      if (d.ok && d.state) {
        return enterState(d.state).then(function () { fadeIn('🗺️ ' + map.name); msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : (d.message || ''), 2600); });
      }
      busy = false;
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (d.reload) return reload();
      fadeIn(null);
      msg(d.message || d.error || failText);
    }).catch(function () { busy = false; fadeIn(null); msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function enterPortal(px2, py2, now) {
    var p = portalAt[px2 + ',' + py2];
    if (!p) return;
    if (p.kind === 'Z' && !(st && st.waterWalk)) return; // [2026-10-24] 숨은 입구 -- 용왕의 반지가 없으면 그냥 물(아무 반응 없음)
    if (p.kind === 'X' && map.teleTo) { // [2026-10-24] 왕도 텔레포트 게이트 -- 목적지 고르기
      var tk = 'tele' + px2 + ',' + py2;
      if (lastBump === tk && now - lastBumpT < 1200) return;
      lastBump = tk; lastBumpT = now;
      setAuto(false);
      openSmith({ name: '텔레포트 게이트', line: '', kind: 'tele', px: px2, py: py2 }, 'tele');
      return;
    }
    if (p.kind === 'B' && !p.locked && map.shipTo) { // [2026-10-24] 배 -- 갈 항구 고르기
      var bk = 'ship' + px2 + ',' + py2;
      if (lastBump === bk && now - lastBumpT < 1200) return;
      lastBump = bk; lastBumpT = now;
      setAuto(false);
      openSmith({ name: '배', line: '', kind: 'ship', px: px2, py: py2 }, 'ship');
      return;
    }
    if (p.locked) {
      var key = px2 + ',' + py2;
      if (lastBump !== key || now - lastBumpT > 2500) msg(p.label.indexOf('눈바람') >= 0 ? '🌨️ ' + p.label + '.' : '🔒 ' + p.label + ' -- 아직 갈 수 없어요. (준비 중)'); // [2026-10-25] 설원 마을 문
      lastBump = key; lastBumpT = now;
      return;
    }
    travel('/api/tower-mine-warp?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&px=' + px2 + '&py=' + py2 + verQ(), '들어갈 수 없어요.');
  }
  function draw(now) {
    var W = el.cv.width, H = el.cv.height, TS = T * scale, pos = curPos(now);
    var camX = Math.round(pos.x * TS + TS / 2 - W / 2), camY = Math.round(pos.y * TS + TS / 2 - H / 2);
    ctx.fillStyle = '#0b0b10';
    ctx.fillRect(0, 0, W, H);
    var x0 = Math.floor(camX / TS), y0 = Math.floor(camY / TS), x1 = Math.ceil((camX + W) / TS), y1 = Math.ceil((camY + H) / TS);
    var wf = Math.floor(now / 520) % 2;
    for (var y = y0; y <= y1; y++) {
      if (y < 0 || y >= map.h) continue;
      var row = map.rows[y];
      for (var x = x0; x <= x1; x++) {
        if (x < 0 || x >= map.w) continue;
        var sx = x * TS - camX, sy = y * TS - camY;
        var ch = row.charAt(x);
        ctx.drawImage(tileFor(ch, x, y, wf), sx, sy, TS, TS);
        if (dug[x + ',' + y] && ch !== 'I') ctx.drawImage(hole, sx, sy, TS, TS); // [2026-10-29] 광맥은 구멍 대신 veinDone 타일
        if (ch === 'q' && ship) ctx.drawImage(ship.img, (x - ship.x) * T, (y - ship.y) * T, T, T, sx, sy, TS, TS);
        else if (ch === 'N') {
          var np = npcAt[x + ',' + y];
          var nk = np && np.kind === 'blackmarket' && !blackOpen() ? 'stall' : np && np.kind; // [2026-10-26] 암상인이 없을 땐 빈 노점
          if (np && tiles.npc[nk]) ctx.drawImage(tiles.npc[nk], sx, sy - 3 * scale, TS, TS);
        }
      }
    }
    // [2026-10-28] 다도해 그늘(야자수) -- 시원한 그늘 둘레 + 가운데 야자수
    if (map.archi && map.safe) {
      map.safe.forEach(function (z) {
        var zx = (z[0] + 0.5) * TS - camX, zy = (z[1] + 0.5) * TS - camY, rr = (z[2] + 0.5) * TS;
        if (zx < -rr || zy < -rr || zx > W + rr || zy > H + rr) return;
        var gr = ctx.createRadialGradient(zx, zy, TS * 0.3, zx, zy, rr);
        gr.addColorStop(0, 'rgba(20,70,45,0.46)'); gr.addColorStop(0.85, 'rgba(20,70,45,0.34)'); gr.addColorStop(1, 'rgba(20,70,45,0.28)'); // [2026-10-28] 안전지대 음영 더 진하게 + 경계선
        ctx.fillStyle = gr; ctx.beginPath(); ctx.arc(zx, zy, rr, 0, Math.PI * 2); ctx.fill(); safeRing(zx, zy, rr, 'rgba(150,255,190,0.85)');
        if (!npcAt[z[0] + ',' + z[1]]) drawPalm(zx, zy, TS, now);
      });
    }
    // [2026-10-26] 안전지대(모닥불) -- 따뜻한 빛 둘레 + 가운데 모닥불(NPC 노점 칸이면 불은 안 그린다)
    if (map.cold && map.safe) {
      map.safe.forEach(function (z) {
        var zx = (z[0] + 0.5) * TS - camX, zy = (z[1] + 0.5) * TS - camY, rr = (z[2] + 0.5) * TS;
        if (zx < -rr || zy < -rr || zx > W + rr || zy > H + rr) return;
        var gr = ctx.createRadialGradient(zx, zy, TS * 0.3, zx, zy, rr);
        gr.addColorStop(0, 'rgba(255,170,60,0.44)'); gr.addColorStop(0.85, 'rgba(255,170,60,0.30)'); gr.addColorStop(1, 'rgba(255,170,60,0.24)'); // [2026-10-28] 안전지대 음영 더 진하게 + 경계선
        ctx.fillStyle = gr; ctx.beginPath(); ctx.arc(zx, zy, rr, 0, Math.PI * 2); ctx.fill(); safeRing(zx, zy, rr, 'rgba(255,210,120,0.9)');
        if (!npcAt[z[0] + ',' + z[1]]) drawFire(zx, zy, TS, now);
      });
    }
    // [2026-11-05] 전기 장판 -- 깜빡이는 푸른 바닥 + 튀는 불꽃
    for (var sk2 in shockCells) {
      var sp2 = sk2.split(','), sx2 = +sp2[0] * TS - camX, sy2 = +sp2[1] * TS - camY;
      if (sx2 < -TS || sy2 < -TS || sx2 > W || sy2 > H) continue;
      var fl = 0.35 + 0.25 * Math.abs(Math.sin(now / 70 + (+sp2[0]) * 2.1 + (+sp2[1]) * 1.3));
      ctx.fillStyle = 'rgba(60,140,255,' + fl + ')'; ctx.fillRect(sx2, sy2, TS, TS);
      if (((now / 80 | 0) + (+sp2[0]) * 3 + (+sp2[1]) * 5) % 4 === 0) { ctx.strokeStyle = '#fff6a0'; ctx.lineWidth = 1.5 * dpr; ctx.beginPath(); ctx.moveTo(sx2 + TS * 0.2, sy2 + TS * 0.3); ctx.lineTo(sx2 + TS * 0.5, sy2 + TS * 0.55); ctx.lineTo(sx2 + TS * 0.4, sy2 + TS * 0.6); ctx.lineTo(sx2 + TS * 0.8, sy2 + TS * 0.8); ctx.stroke(); }
    }
    // [2026-10-28] 용암 칸 -- 일렁이는 붉은 바닥 + 거품
    for (var lk in lavaCells) {
      var lp = lk.split(','), lx = +lp[0] * TS - camX, ly = +lp[1] * TS - camY;
      if (lx < -TS || ly < -TS || lx > W || ly > H) continue;
      var glow = 0.75 + 0.2 * Math.sin(now / 300 + (+lp[0]) * 1.3 + (+lp[1]) * 0.7);
      ctx.fillStyle = 'rgba(200,40,10,' + glow + ')'; ctx.fillRect(lx, ly, TS, TS);
      ctx.fillStyle = 'rgba(255,170,40,' + (glow * 0.8) + ')'; ctx.fillRect(lx + TS * 0.15, ly + TS * 0.15, TS * 0.7, TS * 0.7);
      var bb = ((now / 700 + (+lp[0]) * 0.37 + (+lp[1]) * 0.61) % 1);
      ctx.fillStyle = 'rgba(255,230,140,' + (1 - bb) + ')'; ctx.beginPath(); ctx.arc(lx + TS * (0.3 + 0.4 * ((+lp[0] * 7 + +lp[1] * 3) % 5) / 5), ly + TS * (0.7 - 0.4 * bb), TS * 0.08 * (1 + bb), 0, Math.PI * 2); ctx.fill();
    }
    // [2026-10-24] 탐지 곡괭이 -- 주변의 안 캔 광석. [2026-10-28] "범위를 보여주고 물풍선으로" -- 탐지 범위 점선 테두리 + 광석 아이콘을 담은 물풍선이 둥실둥실
    if (scan && scan.map === map.id) {
      var sr = scan.r, bx = (scan.x - sr) * TS - camX, by = (scan.y - sr) * TS - camY, bw = (sr * 2 + 1) * TS;
      ctx.save(); ctx.setLineDash([6 * dpr, 5 * dpr]); ctx.lineWidth = 1.5 * dpr;
      ctx.strokeStyle = scan.off ? 'rgba(255,220,120,0.55)' : 'rgba(140,220,255,0.6)'; ctx.strokeRect(bx + 1, by + 1, bw - 2, bw - 2);
      ctx.fillStyle = scan.off ? 'rgba(255,220,120,0.05)' : 'rgba(140,220,255,0.06)'; ctx.fillRect(bx, by, bw, bw); ctx.restore();
      scan.cells.forEach(function (c, i) {
        var gx = scan.x + c[0], gy = scan.y + c[1];
        if (dug[gx + "," + gy]) return;
        var cx = gx * TS - camX + TS / 2, cy = gy * TS - camY + TS * 0.42 + Math.sin(now / 420 + i * 1.7) * TS * 0.07, br = TS * 0.34;
        var glow = c[2] === 6 || c[2] === 20, bg = ctx.createRadialGradient(cx - br * 0.35, cy - br * 0.4, br * 0.1, cx, cy, br);
        bg.addColorStop(0, glow ? 'rgba(255,250,220,0.85)' : 'rgba(235,250,255,0.8)'); bg.addColorStop(0.6, glow ? 'rgba(255,215,110,0.35)' : 'rgba(120,200,255,0.3)'); bg.addColorStop(1, glow ? 'rgba(255,190,60,0.55)' : 'rgba(70,160,240,0.5)');
        ctx.fillStyle = bg; ctx.beginPath(); ctx.arc(cx, cy, br, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = glow ? 'rgba(255,230,140,0.95)' : 'rgba(200,240,255,0.9)'; ctx.lineWidth = 1.2 * dpr; ctx.stroke();
        ctx.drawImage(scanImg(c[2]), cx - br * 0.6, cy - br * 0.6, br * 1.2, br * 1.2);
        ctx.fillStyle = 'rgba(255,255,255,0.85)'; ctx.beginPath(); ctx.ellipse(cx - br * 0.4, cy - br * 0.45, br * 0.18, br * 0.1, -0.6, 0, Math.PI * 2); ctx.fill(); // 풍선 반짝임
      });
    }
    if (ultFx.length) ultFloorDraw(now, TS, camX, camY); // [2026-11-07] 만검진 바닥 장판(몬스터보다 먼저)
    // [2026-10-24] 몬스터(그림 + 맞은 몬스터 체력 막대 + 가까우면 이름)
    // [2026-11-05] 마물의 숲 입구 밤 -- 몬스터 눈이 빨갛게 빛난다(밤 어둠 위에 덧그리려고 자리만 모아 둔다). EYE_PX = 16칸 그림의 눈 자리.
    var wildNight = map.id === 'westroad' && map.dayCycleMs && !archiDay(nowSrv()), redEyes = [];
    if (map.mobs) {
      // [2026-10-28] 닌자 두목 분신술 -- 분신은 두목과 똑같이(그림/크기/이름/체력 막대) 그리고, 분신이 나와 있는 동안 진짜만 인을 맺은 자세
      var bossM = map.mobs[map.mobs.length - 1], bossT = bossM && map.mobTypes[bossM.k], splitOn = false;
      var tagCells = {};
      if (bossT && bossT.boss) for (var si = 0; si < map.mobs.length; si++) { var stt = map.mobTypes[map.mobs[si].k]; if (stt && stt.clone && map.mobs[si]._p) { splitOn = true; break; } }
      for (var mi = 0; mi < map.mobs.length; mi++) {
        var mb = map.mobs[mi], mp = mb._p;
        if (!mp) continue;
        var mx = mp.x * TS - camX, my = mp.y * TS - camY;
        if (mx < -TS || my < -TS || mx > W || my > H) continue;
        var ty = map.mobTypes[mb.k] || {}, sk = mb.k, bid = mb.id, pose = false;
        if (ty.clone && bossT && bossT.boss) { ty = bossT; sk = bossM.k; bid = bossM.id; } else if (splitOn && mb === bossM) pose = true;
        var hq = mobHitAt[mb.id] ? now - mobHitAt[mb.id] : 999;
        if (hq < 200) mx += Math.round(Math.sin(hq / 16) * 2.5 * scale * (1 - hq / 200)); // [2026-11-03] 맞은 몬스터 흔들림
        var bob = ty.kind === 'flyer' ? Math.sin(now / 260 + bid) * 2 * scale : Math.abs(Math.sin(now / 420 + bid)) * scale;
        var ms = mobSt[mb.id];
        if (ms && ms.fu > nowSrv() && !ty.boss) { mx += ((mb.id % 5) - 2) * scale; my += (((mb.id / 5) | 0) % 3 - 1) * scale; } // [2026-11-05] 쫓아오며 겹친 몬스터 -- 조금씩 비껴 그려 여러 마리인 게 보이게
        if (ms && ms.sl) ctx.globalAlpha = 0.3 + 0.15 * Math.sin(now / 90); // [2026-10-28] 은신한 닌자 -- 반투명하게 깜빡
        ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.fillRect(mx + 3 * scale, my + 13 * scale, 10 * scale, 2 * scale);
        if (ty.sim != null) { var sw2 = TS * 2; ctx.drawImage(mobSprite(ty.sim, false, Math.floor(now / 480 + bid)), Math.round(mx + TS / 2 - sw2 / 2), Math.round(my + TS - sw2 - bob), sw2, sw2); } // [2026-11-07] 보스 허수아비 -- 그 보스 그림(2칸 크기)
        else if (ty.boss) { // [2026-10-24] 보스 -- 1.8배 크기, 붉은 기운
          ctx.fillStyle = 'rgba(255,40,40,' + (0.18 + 0.1 * Math.sin(now / 300)) + ')'; ctx.beginPath(); ctx.arc(mx + TS, my + TS, TS * 1.5, 0, Math.PI * 2); ctx.fill();
          var bw = TS * 2.4; // [2026-10-29] 보스 2x2칸 -- 그림은 두 칸 가운데, 발은 아래 칸
          ctx.drawImage(mobSprite(sk, pose, Math.floor(now / 480 + bid)), Math.round(mx + TS - bw / 2), Math.round(my + 2 * TS - bw - bob), bw, bw);
        } else if (BOSS_ART[ty.name]) { var qw = TS * 1.5; ctx.drawImage(mobSprite(sk, false, Math.floor(now / 480 + bid)), Math.round(mx + TS / 2 - qw / 2), Math.round(my + TS - qw - bob), qw, qw); } // 여왕개미(보스급)
        else {
          ctx.drawImage(mobSprite(sk), Math.round(mx), Math.round(my - 2 * scale - bob), TS, TS);
          if (wildNight && EYE_PX[ty.kind]) EYE_PX[ty.kind].forEach(function (e) { redEyes.push([mx + (e[0] + 0.5) * scale, my - 2 * scale - bob + (e[1] + 0.5) * scale]); });
        }
        ctx.globalAlpha = 1;
        if (ms && ms.vu > nowSrv() && ms.vp > 0) drawTextImg(textImg('🔻+' + ms.vp + '%', 'bold ' + Math.round(10 * dpr) + 'px monospace', '#ff8a6a', 3 * dpr, true), mx + (ty.boss ? TS : TS / 2), my - (ty.boss ? TS * 1.3 : 8 * scale)); // [2026-11-08] 만검진 받는 피해 증가
        if (ms && ms.su > nowSrv()) { try { drawZapStun(ty.boss ? mx + TS / 2 : mx, (ty.boss ? my + TS : my) - 3 * scale, now); } catch (e) { } } // [2026-11-07] 일섬참 기절(5초) -- 감전과 같은 불꽃
        if (bid !== mb.id) ms = mobSt[bid]; // 분신 체력 막대 = 두목 체력
        var hpMx = bid !== mb.id ? ty.hp : Math.max(1, Math.round(ty.hp * RANK_HP[mb.r == null ? 1 : mb.r])); // [2026-10-29] 개체 단계별 최대 체력
        if (ms && ms.hp > hpMx) hpMx = ms.hp; // [2026-10-30] 저장된 옛 맵 정보라도 막대가 넘치거나 안 보이지 않게
        if (ty.sim != null) { // [2026-11-07] 보스 허수아비 -- 체력 막대 + 숫자를 늘 보여 준다(5초 안 맞으면 다 참)
          var shp = ms ? Math.max(0, ms.hp) : hpMx, sbw = 40 * scale, sbx = mx + TS / 2 - sbw / 2, sby = my + TS - 2 * TS - 6 * scale;
          ctx.fillStyle = '#140d0a'; ctx.fillRect(sbx - scale, sby - scale, sbw + 2 * scale, 5 * scale);
          ctx.fillStyle = '#e8433a'; ctx.fillRect(sbx, sby, Math.max(shp > 0 ? 1 : 0, sbw * shp / hpMx), 3 * scale);
          drawTextImg(textImg(shp.toLocaleString() + ' / ' + hpMx.toLocaleString(), 'bold ' + Math.round(9 * dpr) + 'px monospace', '#ffffff', 3 * dpr, true), mx + TS / 2, sby - 3 * scale);
        } else if (ms && ms.hp > 0 && ms.hp < hpMx) {
          if (ty.boss) { // [2026-10-30] 보스 -- 2칸 폭으로 그림 위에
            ctx.fillStyle = '#140d0a'; ctx.fillRect(mx + TS - 15 * scale, my - 9 * scale, 30 * scale, 4 * scale);
            ctx.fillStyle = '#e8433a'; ctx.fillRect(mx + TS - 14 * scale, my - 8 * scale, Math.max(1, 28 * scale * ms.hp / hpMx), 2 * scale);
          } else {
          ctx.fillStyle = '#140d0a'; ctx.fillRect(mx + 1 * scale, my - 4 * scale, 14 * scale, 3 * scale);
          ctx.fillStyle = '#e8433a'; ctx.fillRect(mx + 2 * scale, my - 3 * scale, Math.max(1, 12 * scale * ms.hp / hpMx), scale);
          }
        }
        if (Math.abs(mp.cx - P.x) + Math.abs(mp.cy - P.y) <= 4 && !tagCells[mp.cx + ',' + mp.cy]) { // [2026-11-06] 렉 -- 겹친 칸은 이름 하나만, 이름표는 그림으로 저장해 다시 쓴다
          tagCells[mp.cx + ',' + mp.cy] = 1;
          var nm = ty.dummy ? '🎯 ' + (ELEM_ICON[ty.el] || '') + ty.name + (ty.sim != null && ty.lv ? ' Lv.' + ty.lv : '') : (ty.boss ? '👑 보스 ' : '') + (mb.r === 2 ? '⭐' : '') + (ELEM_ICON[ty.el] || '') + ty.name + ' Lv.' + Math.max(1, (ty.lv || (1 + 4 * (ty.tier - 1))) + RANK_LV[mb.r == null ? 1 : mb.r]); // [2026-10-28] 얼음 여왕은 서버가 준 레벨(95), [2026-10-29] 속성 아이콘
          drawTextImg(textImg(nm, 'bold ' + Math.round(11 * dpr) + 'px sans-serif', ty.boss ? '#ff4a4a' : mb.r === 2 ? '#ffb347' : mb.r === 0 ? '#c8c8c8' : ty.tier >= 7 ? '#ff8a8a' : ty.tier >= 4 ? '#ffd27a' : '#ffffff', 3 * dpr, true), mx + (ty.boss ? TS : TS / 2), my - (ty.boss ? 11 : 6) * scale); // [2026-10-28] 보스 이름이 두 줄로 보이던 문제 -- 테두리도 글자와 같은 높이
        }
      }
    }
    // [2026-10-27] 왕도 주민(길을 왕복, 가끔 말풍선)
    if (map.citizens && map.citizens.length) {
      var tc = nowSrv(), cwin = Math.floor(tc / 7000);
      for (var ci = 0; ci < map.citizens.length; ci++) {
        var cp = citPos(map.citizens[ci], ci, tc), cxp = Math.round(cp.x * TS - camX), cyp = Math.round(cp.y * TS - camY);
        if (cxp < -TS || cyp < -TS * 2 || cxp > W || cyp > H) continue;
        ctx.fillStyle = 'rgba(0,0,0,.22)'; ctx.fillRect(cxp + 3 * scale, cyp + 13 * scale, 10 * scale, 3 * scale);
        ctx.drawImage(citSprite(ci, cp.dir, cp.moving ? 1 + Math.floor(now / 170) % 2 : 0), cxp, cyp - 3 * scale, TS, TS);
        if (mix(ci + 1, cwin) % 3 === 0 && tc % 7000 < 3600) { var gm = mix(ci + 3, cwin) % 3, gl = gm === 1 && updLines.length ? updLines : gm !== 0 && gossip.length ? gossip : CIT_LINES; drawBubble(gl[mix(ci + 7, cwin) % gl.length], cxp + TS / 2, cyp - 6 * scale, tc % 7000 < 1800 ? 0 : 1); } // [2026-11-05] 셋 중 하나씩 유저 소문 / 업데이트 소식 / 기본 말
      }
    }
    // [2026-10-24] 다른 유저(이름 없이) -- 1초마다 받은 칸 사이를 걸어서, 인사하면 말풍선
    for (var oid in others) {
      var ou = others[oid], op = otherPos(ou, now), ox = Math.round(op.x * TS - camX), oy = Math.round(op.y * TS - camY);
      if (ox < -TS || oy < -TS || ox > W || oy > H) continue;
      if (ou.mc != null && !map.boat) mercDrawOther(ou, op, now, { TS: TS, camX: camX, camY: camY, scale: scale, W: W, H: H }); // [2026-11-05] 다른 유저의 용병
      ctx.fillStyle = "rgba(0,0,0,.22)"; ctx.fillRect(ox + 3 * scale, oy + 13 * scale, 10 * scale, 3 * scale);
      if (ou.look && ou.look.gm) { try { gmAura(ox, oy - 3 * scale, now); } catch (e) { } } // 치장 효과가 화면 그리기를 멈추지 않게
      if (map.boat) drawBoat(ox, oy - 3 * scale, now); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
      if (ou.look && ou.look.rebirth) { try { drawWings(ox, oy - 3 * scale, ou.look.rebirth, ou.dir || "down", now, +oid || 1, ou.look.wing); } catch (e) { } } // [2026-10-28] 환생 날개
      if (ou.stealth) ctx.globalAlpha = 0.35; // [2026-10-30] 자객의 목걸이 은신
      var osw = ou.swT0 && now >= ou.swT0 && now - ou.swT0 < 380 ? ((now - ou.swT0) < 190 ? 3 : 4) : 0; // [2026-11-07] 다른 사람 공격 -- 무기 휘두르기
      if (auraFx.length) { var oA = ctx.globalAlpha; ctx.globalAlpha = 1; whirlDraw(0, ox, oy - 3 * scale, now, oid); ctx.globalAlpha = oA; } // [2026-11-07] 관리자 돌개바람(뒤)
      ctx.drawImage(otherSprite(ou.look, osw ? ou.swDir : ou.dir || "down", osw ? 0 : op.moving ? 1 + Math.floor(now / 160) % 2 : 0, osw), ox, oy - 3 * scale, TS, TS);
      ctx.globalAlpha = 1;
      if (auraFx.length) whirlDraw(1, ox, oy - 3 * scale, now, oid); // [2026-11-07] 관리자 돌개바람(앞)
      if (ou.look && ou.look.gm) { try { gmSparkle(ox, oy - 3 * scale, now, +oid || 1); } catch (e) { } }
      if (ou.frozen) drawIce(ox, oy - 3 * scale, now); // [2026-10-28] 얼어붙은 동료
      if (ou.n) nameTag(ou.n, ox + TS / 2, oy - 4 * scale, '#ffffff'); // [2026-11-05] 닉네임
      if (ou.bubble) drawBubble(ou.bubble, ox + TS / 2, oy - (ou.n ? 9 : 6) * scale);
    }
    // [2026-11-05] NPC 대화 사전 -- 화면 안의 NPC(상자/노점 제외)가 9초마다 넷 중 하나꼴로 업데이트 소식을 말풍선으로
    if (updLines.length && map.npcs) {
      var tu = nowSrv(), uw = Math.floor(tu / 9000);
      if (tu % 9000 < 4500) map.npcs.forEach(function (np, ni) {
        if (/chest|stall|blackmarket/.test(np.kind) || mix(ni + 11, uw) % 4 !== 0) return;
        var nxp = Math.round(np.x * TS - camX), nyp = Math.round(np.y * TS - camY);
        if (nxp < -TS || nyp < -TS * 2 || nxp > W || nyp > H) return;
        drawBubble(updLines[mix(ni + 5, uw) % updLines.length], nxp + TS / 2, nyp - 6 * scale, tu % 9000 < 2250 ? 0 : 1);
      });
    }
    if (hermitOn()) { // [2026-10-30] 은둔한 대장장이
      var hxp = Math.round(hermit.x * TS - camX), hyp = Math.round(hermit.y * TS - camY);
      if (!tiles.npc.hermit) tiles.npc.hermit = hermitSprite();
      ctx.drawImage(tiles.npc.hermit, hxp, hyp - 3 * scale, TS, TS);
      if (Math.abs(hermit.x - P.x) + Math.abs(hermit.y - P.y) <= 8) {
        ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
        ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText('💬 ???', hxp + TS / 2, hyp - 6 * dpr);
        ctx.fillStyle = '#9fe0ff'; ctx.fillText('💬 ???', hxp + TS / 2, hyp - 6 * dpr);
      }
    }
    if (bossNpcOn()) { // [2026-11-13] 보스 소환사(명품상점 주인 그림을 같이 쓴다)
      var bxp = Math.round(bossNpc.x * TS - camX), byp = Math.round(bossNpc.y * TS - camY), cdn = bossNpc.at ? bossNpc.at - nowSrv() : 0;
      if (!tiles.npc.boutique) tiles.npc.boutique = npcSprite('boutique');
      if (cdn > 0) { ctx.save(); ctx.globalAlpha = Math.max(0.15, Math.min(1, cdn / 3000)); } // 카운트 동안 점점 흐려지며 사라진다
      ctx.drawImage(tiles.npc.boutique, bxp, byp - 3 * scale, TS, TS);
      if (cdn > 0) ctx.restore();
      if (!(cdn > 0) && Math.abs(bossNpc.x - P.x) + Math.abs(bossNpc.y - P.y) <= 10) {
        ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
        ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText('🔮 보스 소환사', bxp + TS / 2, byp - 6 * dpr);
        ctx.fillStyle = '#e0b0ff'; ctx.fillText('🔮 보스 소환사', bxp + TS / 2, byp - 6 * dpr);
      }
      if (cdn > 0) { // 화면 가운데 큰 숫자 3 -> 2 -> 1
        var cn = Math.ceil(cdn / 1000), fr = 1 - (cdn % 1000) / 1000, fs = Math.round((90 + 40 * (1 - fr)) * dpr);
        ctx.save(); ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.globalAlpha = Math.min(1, 0.35 + (1 - fr));
        ctx.font = '900 ' + fs + 'px sans-serif'; ctx.lineWidth = 10 * dpr; ctx.strokeStyle = '#1a0a24'; ctx.strokeText(String(cn), W / 2, H * 0.42);
        ctx.fillStyle = '#ffd23f'; ctx.fillText(String(cn), W / 2, H * 0.42);
        ctx.globalAlpha = 1; ctx.font = '800 ' + Math.round(18 * dpr) + 'px sans-serif'; ctx.lineWidth = 5 * dpr;
        ctx.strokeText('👑 보스 소환!', W / 2, H * 0.42 - fs * 0.75); ctx.fillStyle = '#ff9a6a'; ctx.fillText('👑 보스 소환!', W / 2, H * 0.42 - fs * 0.75);
        ctx.restore();
        if (cdn < 120 && !bossNpc.flashed) { bossNpc.flashed = true; flashT0 = performance.now(); }
      }
    }
    mercDrawMine(now, { TS: TS, camX: camX, camY: camY, scale: scale, W: W, H: H }); // [2026-11-05] 내 용병
    // 캐릭터
    var cx = Math.round(pos.x * TS - camX), cy = Math.round(pos.y * TS - camY);
    ctx.fillStyle = 'rgba(0,0,0,.28)';
    ctx.fillRect(cx + 3 * scale, cy + 13 * scale, 10 * scale, 3 * scale);
    var digging = now - P.digT0 < 380, dk = digging ? ((now - P.digT0) < 190 ? 1 : 2) + (P.swing ? 2 : 0) : 0; // [2026-10-28] 공격 = 무기를 휘두른다(곡괭이 대신)
    var meGm = !!(st && st.look && st.look.gm); // [2026-10-28] 운영자 장비 -- 반짝이 + "운영자" 표시
    if (meGm) { try { gmAura(cx, cy - 3 * scale, now); } catch (e) { } }
    if (map.boat) drawBoat(cx, cy - 3 * scale, now); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
    var myRb = st && st.combat ? st.combat.rebirth | 0 : 0; // [2026-10-28] 환생 날개(내 것은 전투 정보의 환생 횟수로)
    var hid = pStealthOn(); // [2026-10-30] 자객의 목걸이 은신 -- 반투명하게(살짝 일렁임)
    if (hid) ctx.globalAlpha = 0.42 + 0.08 * Math.sin(now / 160);
    if (myRb) { try { drawWings(cx, cy - 3 * scale, myRb, P.dir, now, 0, st && st.look && st.look.wing); } catch (e) { } }
    if (hid) ctx.globalAlpha = 0.42 + 0.08 * Math.sin(now / 160);
    if (auraFx.length) { var hidA = ctx.globalAlpha; ctx.globalAlpha = 1; whirlDraw(0, cx, cy - 3 * scale, now); ctx.globalAlpha = hidA; } // [2026-11-07] 관리자 돌개바람(뒤쪽 반)
    ctx.drawImage(sprites[P.dir + (P.moving ? P.step : 0) + dk], cx, cy - 3 * scale, TS, TS);
    ctx.globalAlpha = 1;
    if (auraFx.length) whirlDraw(1, cx, cy - 3 * scale, now); // [2026-11-07] 관리자 돌개바람(앞쪽 반)
    if (meGm) { try { gmSparkle(cx, cy - 3 * scale, now, 0); } catch (e) { } }
    if (frozenMe()) drawIce(cx, cy - 3 * scale, now); // [2026-10-28] 얼음 여왕 빙결
    if (stunnedMe()) { if (zapStunUntil >= stunUntil) drawZapStun(cx, cy - 3 * scale, now); else drawBandage(cx, cy - 3 * scale, now); } // [2026-11-04] 붕대 스턴, [2026-11-05] 감전
    nameTag(myNick(), cx + TS / 2, cy - 4 * scale, '#ffe14d'); // [2026-11-05] 내 닉네임(노란색)
    if (performance.now() - myBubbleT0 < 4000) drawBubble("안녕하세요!", cx + TS / 2, cy - 9 * scale); // [2026-10-24] 내가 한 인사
    if (map.trees) { try { forestTreesDraw(now, TS, camX, camY, W, H); } catch (e) { } } // [2026-11-08] 하피의 둥지 대형 나무
    // 어두운 맵(동굴): 캐릭터 주변만 밝게
    var sv = sight();
    if (sv < 2) { // 2단계 이상이면 어둠 없음(전체가 보임)
      var lx = cx + TS / 2, ly = cy + TS / 2;
      var dim = sv <= 0 ? { r0: 0.45, r1: 2.3, mid: 0.8, edge: 0.99 } : { r0: 1.6, r1: 6.0, mid: 0.5, edge: 0.9 };
      var grd = ctx.createRadialGradient(lx, ly, TS * dim.r0, lx, ly, TS * dim.r1);
      grd.addColorStop(0, 'rgba(6,4,2,0)');
      grd.addColorStop(0.55, 'rgba(6,4,2,' + dim.mid + ')');
      grd.addColorStop(1, 'rgba(6,4,2,' + dim.edge + ')');
      ctx.fillStyle = grd;
      ctx.fillRect(0, 0, W, H);
    }
    // [2026-10-28] 다도해 밤 -- 푸르스름하게 어둡게(낮엔 햇살이 살짝 노랗게)
    if (map.dayCycleMs && (map.archi ? map.id.indexOf('shrine') !== 0 : map.outdoor)) { // [2026-10-28] 신궁 1~3층/닌자 두목의 방은 실내, [2026-11-05] 모든 바깥 맵에 밤
      var isDay = archiDay(nowSrv());
      ctx.fillStyle = isDay ? 'rgba(255,220,120,0.06)' : 'rgba(10,20,70,0.38)';
      ctx.fillRect(0, 0, W, H);
      if (!isDay) { // [2026-11-05] "밤이 되면 시야가 어두워지게" -- 캐릭터 둘레(약 4칸)만 밝고 멀수록 캄캄하게
        // [2026-11-05] 마물의 숲 입구는 "조금 떨어진 곳은 빨간 눈만 보이게" -- 더 좁고 거의 캄캄하게(눈은 그 위에 덧그린다)
        var nlx = cx + TS / 2, nly = cy + TS / 2, ng = ctx.createRadialGradient(nlx, nly, TS * (wildNight ? 1.8 : 2.5), nlx, nly, TS * (wildNight ? 5.5 : 9));
        ng.addColorStop(0, 'rgba(4,6,20,0)');
        ng.addColorStop(0.5, 'rgba(4,6,20,' + (wildNight ? 0.72 : 0.45) + ')');
        ng.addColorStop(1, 'rgba(4,6,20,' + (wildNight ? 0.97 : 0.78) + ')');
        ctx.fillStyle = ng;
        ctx.fillRect(0, 0, W, H);
      }
    }
    if (map.thunder && stormNext && stormNext - nowSrv() < 1500 && stormNext > nowSrv()) { ctx.fillStyle = 'rgba(160,200,255,' + (0.08 + 0.08 * Math.sin(now / 40)) + ')'; ctx.fillRect(0, 0, W, H); } // [2026-11-05] 낙뢰 직전 깜빡임
    drawRainbow(performance.now(), W, H, TS, camX, camY); // [2026-11-05] 전설 이상 / 세공된 보석 -- 떨어진 자리에서 빛줄기
    if (performance.now() - stormT0 < 700) { // [2026-11-05] 전체 낙뢰 -- 화면이 하얗게 번쩍, 하늘에서 내 머리 위로 번개
      var sq = (performance.now() - stormT0) / 700;
      ctx.fillStyle = 'rgba(220,235,255,' + (0.55 * (1 - sq)) + ')'; ctx.fillRect(0, 0, W, H);
      if (sq < 0.6) { var sx3 = cx + TS / 2, pts3 = [[sx3, 0]]; for (var si3 = 1; si3 <= 8; si3++) pts3.push([sx3 + (Math.sin(stormT0 + si3 * 7.3) * TS * 0.8), (cy + TS / 2) * si3 / 8]); ctx.save(); ctx.shadowColor = '#7ad8ff'; ctx.shadowBlur = 14 * dpr; ctx.strokeStyle = '#fffbd0'; ctx.lineWidth = 4 * dpr; ctx.beginPath(); pts3.forEach(function (p, i) { if (i) ctx.lineTo(p[0], p[1]); else ctx.moveTo(p[0], p[1]); }); ctx.stroke(); ctx.restore(); }
    }
    if (redEyes && redEyes.length) { // [2026-11-05] 마물의 숲 밤 -- 어둠 속에서 빛나는 붉은 눈
      var eg = 0.75 + 0.25 * Math.sin(now / 240);
      ctx.save();
      ctx.shadowColor = '#ff2020'; ctx.shadowBlur = 6 * scale;
      ctx.fillStyle = 'rgba(255,40,30,' + eg + ')';
      redEyes.forEach(function (p) { ctx.fillRect(Math.round(p[0] - scale * 0.6), Math.round(p[1] - scale * 0.6), Math.max(2, Math.round(scale * 1.2)), Math.max(2, Math.round(scale * 1.2))); });
      ctx.restore();
    }
    // [2026-10-23] 모래바람 -- 화면이 누렇게 흐려지고 바람 방향으로 모래가 날린다.
    if (map.wind) {
      var wdir = windDir();
      ctx.fillStyle = 'rgba(214,170,96,0.16)';
      ctx.fillRect(0, 0, W, H);
      ctx.strokeStyle = 'rgba(240,214,150,0.55)';
      ctx.lineWidth = Math.max(1, dpr);
      ctx.beginPath();
      for (var si = 0; si < 46; si++) {
        var spd = 0.35 + (si % 5) * 0.08, len = (10 + (si % 4) * 6) * dpr;
        var lxs = ((si * 197 + now * spd * wdir) % (W + 200) + (W + 200)) % (W + 200) - 100;
        var lys = (si * 131 % 1000) / 1000 * H + Math.sin(now / 400 + si) * 6 * dpr;
        ctx.moveTo(lxs, lys); ctx.lineTo(lxs - len * wdir, lys + 1.5 * dpr);
      }
      ctx.stroke();
    }
    // [2026-10-26] 한기 맵 -- 눈이 내리고, 눈바람이 부는 동안(2.5초)은 화면이 하얗게 흐려지며 눈발이 옆으로 몰아친다
    if (map.cold) {
      var tsv = nowSrv(), bzOn = (cold && cold.nextBz && tsv >= cold.nextBz && tsv < cold.nextBz + 2500) || (lastBz && tsv >= lastBz && tsv < lastBz + 2500);
      ctx.fillStyle = bzOn ? 'rgba(235,245,255,0.42)' : 'rgba(220,235,255,0.05)';
      ctx.fillRect(0, 0, W, H);
      ctx.fillStyle = 'rgba(255,255,255,0.85)';
      for (var fi = 0, fn = bzOn ? 160 : 45; fi < fn; fi++) {
        var fxp = ((fi * 211 + now * (bzOn ? 0.9 : 0.02)) % (W + 40) + (W + 40)) % (W + 40) - 20;
        var fyp = ((fi * 137 + now * (bzOn ? 0.18 : 0.05)) % (H + 40)) - 20;
        ctx.fillRect(fxp, fyp, (bzOn ? 4 : 2) * dpr, 2 * dpr);
      }
    }
    // [2026-10-28] 대주교 마법 -- 불벼락(붉게 번쩍 + 하늘에서 내리치는 번개 줄기), 어둠가스(보랏빛이 퍼졌다 걷힘)
    if (map.raid) {
      var pf = performance.now() - raidFx.fire, pg = performance.now() - raidFx.gas;
      if (pf < 900) {
        ctx.fillStyle = 'rgba(255,90,40,' + (0.45 * (1 - pf / 900)) + ')'; ctx.fillRect(0, 0, W, H);
        ctx.strokeStyle = 'rgba(255,230,140,' + (1 - pf / 900) + ')'; ctx.lineWidth = 3 * dpr;
        for (var li = 0; li < 6; li++) {
          var lx0 = (li * 173 + 97) % W;
          ctx.beginPath(); ctx.moveTo(lx0, 0);
          for (var sg = 1; sg <= 6; sg++) ctx.lineTo(lx0 + Math.sin(li * 7 + sg * 3) * 30 * dpr, H * sg / 6);
          ctx.stroke();
        }
      }
      if (pg < 2500) { ctx.fillStyle = 'rgba(70,20,100,' + (0.5 * (1 - pg / 2500)) + ')'; ctx.fillRect(0, 0, W, H); }
    }
    // [2026-10-24] 바닷길(용궁) -- 화면이 푸르게 물들고 거품이 올라간다
    if (map.underwater) {
      ctx.fillStyle = 'rgba(30,110,190,0.18)';
      ctx.fillRect(0, 0, W, H);
      ctx.fillStyle = 'rgba(220,245,255,0.55)';
      for (var bi = 0; bi < 26; bi++) {
        var bxp = (bi * 263 % 1000) / 1000 * W + Math.sin(now / 700 + bi) * 8 * dpr, byp = H - ((now * (0.03 + (bi % 4) * 0.012) + bi * 97) % (H + 40)) + 20;
        ctx.beginPath(); ctx.arc(bxp, byp, (2 + bi % 3) * dpr, 0, Math.PI * 2); ctx.fill();
      }
    }
    // [2026-10-23] 가까운 NPC 이름 + 말풍선
    (map.npcs || []).forEach(function (n) {
      if (Math.abs(n.x - P.x) + Math.abs(n.y - P.y) > 8) return; // [2026-10-24] 5 -> 8: 마을 가운데에서 대장장이(5칸)만 보이고 명품상점(6칸)은 안 보이던 문제
      var nxp = n.x * TS - camX + TS / 2, nyp = n.y * TS - camY - 6 * dpr;
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText('💬 ' + n.name, nxp, nyp);
      ctx.fillStyle = '#9fe0ff'; ctx.fillText('💬 ' + n.name, nxp, nyp);
    });
    if (talkBubble && now - talkBubble.t0 < 4500) {
      var bx = talkBubble.x * TS - camX + TS / 2, by = talkBubble.y * TS - camY - 22 * dpr;
      ctx.font = 'bold ' + Math.round(13 * dpr) + 'px sans-serif';
      var words = talkBubble.text, lines = [], cur = '';
      for (var wi = 0; wi < words.length; wi++) { cur += words.charAt(wi); if ((cur.length >= 18 && words.charAt(wi) === ' ') || cur.length >= 26) { lines.push(cur); cur = ''; } }
      if (cur) lines.push(cur);
      var lh = 17 * dpr, bw = 0;
      lines.forEach(function (l) { bw = Math.max(bw, ctx.measureText(l).width); });
      bw += 16 * dpr;
      var bh = lines.length * lh + 10 * dpr, bx0 = Math.max(4, Math.min(W - bw - 4, bx - bw / 2)), by0 = Math.max(4, by - bh);
      ctx.fillStyle = 'rgba(255,253,240,0.96)'; ctx.fillRect(bx0, by0, bw, bh);
      ctx.strokeStyle = '#140d0a'; ctx.lineWidth = 2 * dpr; ctx.strokeRect(bx0, by0, bw, bh);
      ctx.fillStyle = '#2a1d10'; ctx.textAlign = 'left';
      lines.forEach(function (l, i) { ctx.fillText(l, bx0 + 8 * dpr, by0 + 5 * dpr + (i + 0.8) * lh); });
    } else if (talkBubble) talkBubble = null;
    // 가까운 포탈 이름
    (map.portals || []).forEach(function (p) {
      var zSeen = p.kind === 'Z' && st && st.dragon && st.dragon.has; // [2026-10-28] 용왕의 반지를 얻으면 바닷길 입구 이름도
      if ((p.kind === 'Z' && !zSeen) || Math.abs(p.x - P.x) + Math.abs(p.y - P.y) > 4) return; // [2026-10-24] 숨은 입구(Z)는 이름을 안 보인다
      var tx = p.x * TS - camX + TS / 2, ty = p.y * TS - camY - 4 * dpr;
      var label = zSeen ? '🌀 바닷길 입구' + (st.waterWalk ? '' : ' (용왕의 반지를 끼면)') : (p.locked ? (p.label.indexOf('눈바람') >= 0 ? '🌨️ ' : '🔒 ') : '🚪 ') + p.label;
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(label, tx, ty);
      ctx.fillStyle = p.locked ? '#d0d0d0' : '#ffe14d'; ctx.fillText(label, tx, ty);
    });
    // [2026-10-28] 불덩이 -- 보스 칸에서 내 칸으로 BOLT_MS 동안 날아가며 꼬리를 남긴다
    //  [2026-10-30] "땅에 맞는 것 같다" -- 날아가는 동안 내가 움직여도 따라오고(지금 그려진 내 자리), 몸통에 맞아 터진다(고리 + 불똥)
    // [2026-11-05] 번개 줄기 -- 지그재그 노란 선 + 푸른 잔광
    zaps = zaps.filter(function (z) { return performance.now() - z.t0 < ZAP_MS; });
    zaps.forEach(function (z) {
      var q = (performance.now() - z.t0) / ZAP_MS, n = 7, pts = [];
      for (var i = 0; i <= n; i++) { var k = i / n, j = i === 0 || i === n ? 0 : (Math.sin(z.seed + i * 12.9898 + Math.floor(q * 6)) * 0.45); var nxv = -(z.y1 - z.y0), nyv = z.x1 - z.x0, ln = Math.hypot(nxv, nyv) || 1; pts.push([(z.x0 + (z.x1 - z.x0) * k + nxv / ln * j) * TS - camX, (z.y0 + (z.y1 - z.y0) * k + nyv / ln * j) * TS - camY]); }
      ctx.save(); ctx.globalAlpha = 1 - q; ctx.shadowColor = '#7ad8ff'; ctx.shadowBlur = 10 * dpr;
      [[6, 'rgba(122,216,255,0.6)'], [2.5, '#fff6a0']].forEach(function (st2) { ctx.lineWidth = st2[0] * dpr; ctx.strokeStyle = st2[1]; ctx.beginPath(); pts.forEach(function (p, i) { if (i) ctx.lineTo(p[0], p[1]); else ctx.moveTo(p[0], p[1]); }); ctx.stroke(); });
      ctx.restore();
    });
    bolts = bolts.filter(function (b) { return now - b.t0 < BOLT_MS + BOLT_HIT_MS; });
    bolts.forEach(function (b) {
      var k = Math.min(1, (now - b.t0) / BOLT_MS), tx = b.me ? pos.x : b.tx, ty = (b.me ? pos.y : b.ty) - 0.2;
      var c0 = b.dark ? '#e0ccff' : b.wind ? '#f0fff8' : b.spear ? '#fff6a0' : b.bandage ? '#f4ead0' : b.venom ? '#d8ff8a' : '#ffe08a', c1 = b.dark ? '#5a2a9a' : b.wind ? '#3ac8a0' : b.spear ? '#4ad8ff' : b.bandage ? '#b8a478' : b.venom ? '#6ab82a' : '#ff7a1a'; // [2026-11-11] 파라오 어둠 화살 // [2026-11-11] 하피 깃털(바람) // [2026-11-05] 전기창 // [2026-11-04] 붕대
      if (k < 1) {
        for (var tr = 3; tr >= 0; tr--) {
          var kk = Math.max(0, k - tr * 0.06), bx = (b.fx + (tx - b.fx) * kk) * TS - camX + TS / 2, by = (b.fy + (ty - b.fy) * kk) * TS - camY + TS / 2 - Math.sin(kk * Math.PI) * TS * 0.5;
          ctx.globalAlpha = 1 - tr * 0.22;
          ctx.fillStyle = tr ? c1 : c0; ctx.beginPath(); ctx.arc(bx, by, TS * (0.22 - tr * 0.04), 0, Math.PI * 2); ctx.fill();
        }
      } else {
        var q = (now - b.t0 - BOLT_MS) / BOLT_HIT_MS, hx = tx * TS - camX + TS / 2, hy = ty * TS - camY + TS / 2;
        ctx.globalAlpha = Math.max(0, 0.55 * (1 - q)); ctx.fillStyle = c0; ctx.beginPath(); ctx.arc(hx, hy, TS * (0.42 - 0.15 * q), 0, Math.PI * 2); ctx.fill();
        ctx.globalAlpha = Math.max(0, 1 - q); ctx.strokeStyle = c1; ctx.lineWidth = 3 * dpr; ctx.beginPath(); ctx.arc(hx, hy, TS * (0.2 + 0.55 * q), 0, Math.PI * 2); ctx.stroke();
        ctx.fillStyle = c1;
        for (var sp = 0; sp < 8; sp++) { var an = sp * Math.PI / 4 + 0.3, rr = TS * (0.18 + 0.6 * q), ps = Math.max(1, 3.5 * dpr * (1 - q)); ctx.fillRect(hx + Math.cos(an) * rr - ps / 2, hy + Math.sin(an) * rr - ps / 2, ps, ps); }
      }
      ctx.globalAlpha = 1;
    });
    // [2026-11-04] 검기 -- 속성마다 그 색의 초승달 칼날이 바라보는 방향으로 사거리만큼 날아간다(속성이 많으면 겹겹이 조금씩 늦게)
    // [2026-11-05] "검기 더 진하고 두껍게" -- 굵기 약 2배, 바깥 번짐 + 밝은 심, 끝까지 진하게. 관리자는 폭 3칸이라 칼날도 3칸 너비.
    auraFx = auraFx.filter(function (f) { return now - f.t0 < AURA_FX_MS + 180; });
    var auraWide = !!(st && st.adminMaps);
    auraFx.forEach(function (f) {
      var el0 = now - f.t0, k = Math.min(1, el0 / AURA_FX_MS), fo = el0 > AURA_FX_MS ? 1 - (el0 - AURA_FX_MS) / 180 : 1;
      if (el0 < 0) return; // [2026-11-07] 다른 사람 공격 -- 서버 시각 간격만큼 늦게 시작
      if (f.wo) return; // [2026-11-07] 돌개베기 -- 칼날은 없고 돌개바람(whirlDraw)만
      k = 1 - (1 - k) * (1 - k); // 처음엔 빠르게, 끝에서 느려지며 멎는다
      var dx = f.dir === 'left' ? -1 : f.dir === 'right' ? 1 : 0, dy = f.dir === 'up' ? -1 : f.dir === 'down' ? 1 : 0, ang = Math.atan2(dy, dx);
      ctx.lineCap = 'round';
      f.els.forEach(function (e, i) {
        var kk = Math.max(0, Math.min(1, (k - i * 0.06) / (1 - Math.min(0.5, i * 0.06)))), dist = 0.5 + ((f.rs ? f.rs[i] : f.r) - 0.2) * kk, lw = Math.max(5, 12 - i * 0.8) * dpr;
        var fw = (f.o ? f.w : auraWide) && !f.m; // [2026-11-06] 용병 검기(f.m)는 폭 1칸 // [2026-11-07] 다른 사람 검기(f.o)는 그 사람이 관리자일 때만 넓게
        var rr = TS * ((fw ? 1.5 : 0.5) + i * 0.07), back = TS * 0.4 + (fw ? TS : 0); // [2026-11-05] 관리자 폭 3칸 -- 칼날 반지름 3배, 앞 끝은 같은 자리
        var cx = (f.x + dx * dist) * TS - camX + TS / 2 - dx * back, cy = (f.y + dy * dist) * TS - camY + TS / 2 - dy * back, sp = fw ? 0.62 : 1.05;
        ctx.globalAlpha = Math.max(0, fo * 0.4); ctx.strokeStyle = AURA_COL[e]; ctx.lineWidth = lw * 2.4; // 바깥 번짐
        ctx.beginPath(); ctx.arc(cx - dx * TS * 0.2, cy - dy * TS * 0.2, rr, ang - sp * 0.8, ang + sp * 0.8); ctx.stroke();
        ctx.globalAlpha = Math.max(0, fo); ctx.lineWidth = lw; // 칼날
        ctx.beginPath(); ctx.arc(cx, cy, rr, ang - sp, ang + sp); ctx.stroke();
        ctx.globalAlpha = Math.max(0, fo * 0.85); ctx.strokeStyle = '#ffffff'; ctx.lineWidth = Math.max(1.5 * dpr, lw * 0.28); // 밝은 심
        ctx.beginPath(); ctx.arc(cx + dx * lw * 0.15, cy + dy * lw * 0.15, rr, ang - sp * 0.85, ang + sp * 0.85); ctx.stroke();
      });
      ctx.globalAlpha = 1; ctx.lineCap = 'butt';
    });
    // [2026-11-07] 필살기에 맞은 몬스터마다 X자 베기(f.col 색) + 떨어지는 검 / 일섬참, 버튼 쿨타임
    novaFx = novaFx.filter(function (f) { return now - f.t0 < 380; });
    novaFx.forEach(function (f) {
      var e0 = now - f.t0;
      if (e0 < 0) return;
      ctx.lineCap = 'round';
      var q = Math.min(1, e0 / 140), fo2 = e0 > 140 ? 1 - (e0 - 140) / 240 : 1, mx = f.mx * TS - camX + TS / 2, my = f.my * TS - camY + TS / 2, r = TS * 0.6;
      [[-1, -1, 1, 1], [1, -1, -1, 1]].forEach(function (c, i) {
        var qq = Math.max(0, Math.min(1, q * 2 - i));
        if (qq <= 0) return;
        var x0 = mx + c[0] * r, y0 = my + c[1] * r, x1 = x0 + (c[2] - c[0]) * r * qq, y1 = y0 + (c[3] - c[1]) * r * qq;
        ctx.globalAlpha = Math.max(0, fo2 * 0.55); ctx.strokeStyle = f.col || '#ff3a4a'; ctx.lineWidth = 9 * dpr;
        ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke();
        ctx.globalAlpha = Math.max(0, fo2); ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 2.5 * dpr;
        ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke();
      });
      ctx.globalAlpha = 1; ctx.lineCap = 'butt';
    });
    if (ultFx.length) ultFxDraw(now, TS, camX, camY, W, H);
    ultHud();
    try { forestHud(W, H); } catch (e) { } // [2026-11-11] 미궁 걸음 / 둥지 남은 나무(밤 덧칠 뒤)
    // [2026-11-05] "보스 1시간 카운트는 맵 안 모두에게" -- 천둥 황소가 나와 있는 동안 화면 위 가운데에 남은 시간
    // [2026-11-06] "보스 카운트가 절반을 넘으면 맵에 전기가 점점" -- 절반(0) ~ 다 참(1)에 따라 푸른 깜빡임과 하늘에서 떨어지는 번개가 잦아지고, 황소가 나오면 가장 세게
    if (map.id === 'westroad') {
      var tOn = thunderUntil > nowSrv(), half = thunderNeed / 2, tk = tOn ? 1 : thunderNeed && thunderKills >= half ? Math.min(1, (thunderKills - half) / Math.max(1, half)) : -1;
      if (tk >= 0) {
        var fl = 0.5 + 0.5 * Math.sin(now / 90 + Math.sin(now / 37) * 2);
        ctx.fillStyle = 'rgba(110,160,255,' + ((0.025 + 0.07 * tk) * fl) + ')'; ctx.fillRect(0, 0, W, H);
        var dtz = Math.min(0.2, Math.max(0, (now - (ambZapT || now)) / 1000)); ambZapT = now;
        if (Math.random() < dtz * (0.35 + 2.4 * tk)) {
          var vx = W / TS, vy = H / TS, zx = camX / TS + Math.random() * vx, zy = camY / TS + vy * (0.25 + Math.random() * 0.7);
          zapAdd(zx + (Math.random() - 0.5) * 3, camY / TS - 1, zx, zy);
          if (tk > 0.6 && Math.random() < 0.4) flashT0 = now - 120;
        }
      }
    } else ambZapT = 0;
    // [2026-11-06] "남은 시간이 가려진다, 1분 이상이면 분만" -- 위쪽(지도/좌표와 겹치던 곳) 대신 아래 가운데(체력바 위)
    if (thunderRestAt > nowSrv() && !(thunderUntil > nowSrv()) && map.id === 'westroad') { // [2026-11-08] 천둥 황소가 쓰러진 뒤 2시간 -- 밤 처치를 세지 않는다
      var rl = thunderRestAt - nowSrv(), rtx = '⚡ 천둥 황소 휴식  ' + (rl >= 3600000 ? Math.floor(rl / 3600000) + '시간 ' : '') + Math.ceil((rl % 3600000) / 60000) + '분 뒤부터 다시 세요';
      ctx.font = 'bold ' + Math.round(13 * dpr) + 'px monospace'; ctx.textAlign = 'center';
      var rw = ctx.measureText(rtx).width + 20 * dpr, ry = hudBotY(H) - 2 * dpr, rx = hudCx(W, rw); // [2026-11-11] 메시지 박스와 안 겹치게(hudBotY)
      ctx.globalAlpha = 0.7; ctx.fillStyle = '#10142a'; ctx.fillRect(rx - rw / 2, ry - 16 * dpr, rw, 22 * dpr);
      ctx.globalAlpha = 1; ctx.fillStyle = '#b8c8ff'; ctx.fillText(rtx, rx, ry);
      ctx.textAlign = 'left';
    }
    if (thunderUntil > 0 && map.id === 'westroad') {
      var tl = Math.max(0, thunderUntil - nowSrv());
      if (tl > 0) {
        var ttx = '⚡ 천둥 황소  ' + (tl >= 60000 ? Math.floor(tl / 60000) + '분' : Math.ceil(tl / 1000) + '초');
        ctx.font = 'bold ' + Math.round(15 * dpr) + 'px monospace'; ctx.textAlign = 'center';
        var tw = ctx.measureText(ttx).width + 20 * dpr, ty = hudBotY(H) - 2 * dpr, tx0 = hudCx(W, tw); // [2026-11-11] 메시지 박스와 안 겹치게(hudBotY)
        ctx.globalAlpha = 0.78; ctx.fillStyle = '#10142a'; ctx.fillRect(tx0 - tw / 2, ty - 17 * dpr, tw, 24 * dpr);
        ctx.globalAlpha = 1; ctx.strokeStyle = '#ffe14d'; ctx.lineWidth = 1 * dpr; ctx.strokeRect(tx0 - tw / 2, ty - 17 * dpr, tw, 24 * dpr);
        ctx.fillStyle = '#ffe14d'; ctx.fillText(ttx, tx0, ty);
      }
    }
    // [2026-11-04] 빙결 몸부림 게이지 -- 캐릭터 위에 얼음 막대(횟수는 안 보여 줌) + 깜빡이는 ⬅️➡️⚔️
    if (frozenMe()) {
      var ip = curPos(now), ix = ip.x * TS - camX + TS / 2, iy = ip.y * TS - camY - 14 * dpr, bw = TS * 1.3, bh = 7 * dpr;
      var ik = iceSt.frozen.until === iceStruggleKey ? Math.min(1, iceStruggleN / ICE_STRUGGLE) : 0, shk = now - iceStruggleT < 120 ? (Math.random() - 0.5) * 3 * dpr : 0;
      ctx.fillStyle = 'rgba(10,20,40,.85)'; ctx.fillRect(ix - bw / 2 - 2 * dpr + shk, iy - 2 * dpr, bw + 4 * dpr, bh + 4 * dpr);
      ctx.fillStyle = '#2a4a6a'; ctx.fillRect(ix - bw / 2 + shk, iy, bw, bh);
      ctx.fillStyle = '#bfefff'; ctx.fillRect(ix - bw / 2 + shk, iy, bw * ik, bh);
      ctx.strokeStyle = '#f4f0e6'; ctx.lineWidth = 1 * dpr; ctx.strokeRect(ix - bw / 2 + shk, iy, bw, bh);
      ctx.globalAlpha = 0.55 + 0.45 * Math.abs(Math.sin(now / 260));
      ctx.font = Math.round(15 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
      ctx.fillText('⬅️ ➡️ ⚔️', ix, iy - 7 * dpr);
      ctx.globalAlpha = 1;
    }
    try { mobDeathDraw(now, TS, camX, camY); } catch (e) { } // [2026-11-06] 몬스터 쓰러짐
    try { mercFxDraw(now, TS, camX, camY); } catch (e) { } // [2026-11-06] 용병 공격 이펙트
    // 캔 광석 글자
    floats = floats.filter(function (f) { return now - f.t0 < 1400; });
    floats.forEach(function (f) {
      if (now < f.t0) return; // [2026-11-06] 화살/마법탄이 닿은 뒤에
      var k = (now - f.t0) / 1400, fx = f.x * TS - camX + TS / 2, fy = f.y * TS - camY - k * 36 * dpr - 6 * dpr;
      ctx.globalAlpha = 1 - k * k;
      if (!f._im) f._im = textImg(f.text, 'bold ' + Math.round((f.big ? 17 : f.sm ? 12 : 14) * dpr) + 'px monospace', f.color, 4 * dpr, false); // [2026-11-06] 렉 -- 글자는 한 번만 그리고 떠 있는 동안 그림으로
      if (f.icon) ctx.drawImage(f.icon, fx - 9 * dpr - f._im._tw / 2 - 8 * dpr, fy - 12 * dpr, 16 * dpr, 16 * dpr);
      drawTextImg(f._im, fx + 9 * dpr, fy);
      ctx.globalAlpha = 1;
    });
    if (now - flashT0 < 420) { ctx.fillStyle = 'rgba(255,255,255,' + (0.55 * (1 - (now - flashT0) / 420)) + ')'; ctx.fillRect(0, 0, W, H); }
    if (fade) {
      var fe = now - fade.t0, fa;
      if (fade.mode === 'out') fa = Math.min(1, fe / FADE_OUT_MS);
      else { fa = 1 - fe / FADE_IN_MS; if (fa <= 0) { fade = null; fa = 0; } }
      if (fa > 0) { ctx.fillStyle = 'rgba(0,0,0,' + fa + ')'; ctx.fillRect(0, 0, W, H); }
    }
    if (banner) { // 새 맵 이름(가운데, 잠깐 보였다 사라짐)
      var be = now - banner.t0;
      if (be > BANNER_MS) banner = null;
      else {
        ctx.globalAlpha = be < 250 ? be / 250 : Math.max(0, 1 - (be - BANNER_MS * 0.6) / (BANNER_MS * 0.4));
        ctx.font = 'bold ' + Math.round(22 * dpr) + 'px sans-serif';
        ctx.textAlign = 'center';
        ctx.lineWidth = 6 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(banner.text, W / 2, H * 0.32);
        var bfill = banner.color || '#ffe9a8';
        if (banner.rainbow) { var tw = ctx.measureText(banner.text).width; bfill = ctx.createLinearGradient(W / 2 - tw / 2, 0, W / 2 + tw / 2, 0); ['#ff4a4a', '#ffa500', '#ffe14d', '#4fd16a', '#4a9eff', '#b56cff'].forEach(function (cc, k) { bfill.addColorStop(k / 5, cc); }); }
        ctx.fillStyle = bfill; ctx.fillText(banner.text, W / 2, H * 0.32);
        ctx.globalAlpha = 1;
      }
    }
    // 끌기 조이스틱
    if (drag) {
      var rc = el.cv.getBoundingClientRect(), ox = (drag.ox - rc.left) * dpr, oy = (drag.oy - rc.top) * dpr;
      ctx.strokeStyle = 'rgba(255,255,255,.55)'; ctx.lineWidth = 3 * dpr;
      ctx.beginPath(); ctx.arc(ox, oy, 34 * dpr, 0, Math.PI * 2); ctx.stroke();
      var ddx = (drag.cx - drag.ox) * dpr, ddy = (drag.cy - drag.oy) * dpr, len = Math.sqrt(ddx * ddx + ddy * ddy), lim = 34 * dpr;
      if (len > lim) { ddx = ddx / len * lim; ddy = ddy / len * lim; }
      ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.beginPath(); ctx.arc(ox + ddx, oy + ddy, 13 * dpr, 0, Math.PI * 2); ctx.fill();
    }
  }
  function loop(now) {
    if (!opened) return;
    raf = requestAnimationFrame(loop);
    if (lastFrameT && !document.hidden && now - lastFrameT > frameGapMax) frameGapMax = now - lastFrameT; // [2026-11-04] 화면 멈춤(핑 옆 "화면 n")
    lastFrameT = now;
    if (!map) return;
    update(now);
    draw(now);
  }

  // ---------------------------------------------------------------- 서버
  function apiRaw(path) {
    return fetch(cfg.base + path + (path.indexOf('?') < 0 ? '?' : '&') + 'userName=' + encodeURIComponent(cfg.user)).then(function (r) { return r.json(); });
  }
  // [2026-11-06] "전부 ws로 하기로 한 거 아니었나" -- 1초 턴/공격/캐기뿐 아니라 광산 요청(/api/tower-mine*)은 소켓이 열려 있으면 전부 소켓으로(서버 S55MineSocket.generic이
  //  컨트롤러의 같은 메서드를 부른다). 서버가 {_http: true}로 답하면(소켓으로 못 받는 주소) 그때만 HTTP. 소켓이 없으면 예전처럼 HTTP.
  //  답이 8초 안에 안 오면 실패로 끝낸다(사고팔기 같은 요청이 두 번 되지 않게 HTTP로 다시 보내지 않는다).
  function api(path) {
    if (wsOk && ws && ws.readyState === 1 && path.indexOf('/api/tower-mine') === 0) return wsCall(path).then(function (d) { return d && d._http ? apiRaw(path) : d; }).then(apiPost);
    return apiRaw(path).then(apiPost);
  }
  function wsCall(path, tmo) {
    return new Promise(function (ok, no) {
      var i = ++wsSeq, sock = ws;
      wsWait[i] = { ok: ok, no: no, t: setTimeout(function () { delete wsWait[i]; if (ws === sock) { wsClose(); wsTimer = setTimeout(wsOpen, 3000); } no(new Error('timeout')); }, tmo || 8000) };
      ws.send(JSON.stringify({ i: i, p: path }));
    });
  }
  function apiPost(d) {
    // [2026-10-23] 2일 주기 맵 리셋 중 -- 안내 후 광산에서 나간다(리셋이 끝나면 다시 들어오면 새 맵)
    if (d && d.resetting && opened && map) { msg(d.message || d.error, 4000); setTimeout(close, 2500); }
    return d;
  }
  // [2026-11-03] 시즌 5.5 "네트워크 지연을 안 느끼게" -- 1초 턴/공격/캐기는 연결 하나(WebSocket /s55ws, S55MineSocket)로 주고받는다.
  //  메시지는 HTTP 때와 같은 주소 문자열, 답도 같은 응답. 연결이 없거나 끊기면 예전처럼 HTTP(api)로 보내고, 끊기면 1·2·4...10초 뒤 다시 잇는다.
  var ws = null, wsOk = false, wsSeq = 0, wsWait = {}, wsRetry = 0, wsTimer = 0;
  // [2026-11-04] "핑이 비정상으로 높다" -- Cloudflare를 거치면 도쿄/홍콩을 돌아와 150~600ms. 서버가 직접 주소(st.wsHost)를 주면 http 화면에선 소켓을 그리로 바로 잇는다.
  //  한 번도 못 열고 끊기면(막힘) 이 화면에선 예전 주소로 돌아간다.
  var wsDirectBad = false, wsVia = 0; // [2026-11-05] 지금 소켓 길(1 서울 CloudFront / 2 예전 주소) -- 관리자 통신 상태에
  //  [2026-11-04] 서버 주소는 숨긴다(사용자 지시) -- wsHost는 서울 지점 중계(CloudFront) 도메인. 보안 연결도 되니 https 화면도 wss://로.
  // [2026-11-06] "폰/PC가 HTTP로 통신할 때가 있다 -- 전부 ws로" -- 입장(첫 상태)도 소켓으로 보내려고, 지난번에 받은 서울 주소(wsHost)를 이 기기에 기억해 두고 입장 전에 소켓부터 연다.
  var wsHostMem = (function () { try { return localStorage.getItem('tw-mine-wshost') || ''; } catch (e) { return ''; } })();
  // [2026-11-08] "로컬에서 반영 사항이 안 보인다" -- 로컬(localhost/사설 IP)에서 연 화면도 라이브 서버의 서울 직통 주소로 소켓을 열어
  //  맵/상태/공격이 전부 라이브로 갔다. 로컬에서 열었으면 직통 주소를 쓰지 않는다(그 페이지를 준 서버로).
  var WS_LOCAL = /^(localhost|127\.|10\.|192\.168\.|\[?::1\]?$)/.test(location.hostname);
  function wsHostOf() { return WS_LOCAL ? null : (st && st.wsHost) || wsHostMem; }
  function wsDirect() { return !!(wsHostOf() && !wsDirectBad); }
  /** 소켓이 열릴 때까지(최대 ms) 기다린다 -- 못 열면 그냥 진행(HTTP). */
  function wsReady(ms) { return new Promise(function (res) { if (wsOk || !ws) { res(); return; } var t0 = Date.now(), iv = setInterval(function () { if (wsOk || !ws || Date.now() - t0 > ms) { clearInterval(iv); res(); } }, 40); }); }
  function wsOpen() {
    if (!opened || ws || !window.WebSocket) return;
    var direct = wsDirect(), opened1 = false;
    try { ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + (direct ? wsHostOf() : location.host) + cfg.base.replace(/\/loa$/, '') + '/s55ws?u=' + encodeURIComponent(cfg.user)); } catch (e) { ws = null; if (direct) { wsDirectBad = true; wsOpen(); } return; }
    ws.onopen = function () { wsOk = true; wsRetry = 0; opened1 = true; wsVia = direct ? 1 : 2; };
    ws.onmessage = function (ev) {
      var m; try { m = JSON.parse(ev.data); } catch (e) { return; }
      if (window.twAlive) window.twAlive(); // 탑 화면 "접속 끊김" 감시에 살아 있다고 알린다
      var w = wsWait[m.i];
      if (w) { delete wsWait[m.i]; clearTimeout(w.t); w.ok(m.d); }
    };
    ws.onclose = ws.onerror = function () {
      var was = ws;
      if (direct && !opened1) { wsDirectBad = true; wsRetry = 0; } // 직접 주소가 막혔다 -- 다음엔 예전 주소로
      ws = null; wsOk = false;
      for (var k in wsWait) { clearTimeout(wsWait[k].t); wsWait[k].no(new Error('ws')); }
      wsWait = {};
      if (opened && was && netMode() === 'ws') { clearTimeout(wsTimer); wsTimer = setTimeout(wsOpen, Math.min(10000, 1000 * Math.pow(2, wsRetry++))); } // [2026-11-04] HTTP를 고른 동안엔 다시 잇지 않는다
    };
  }
  /** 왼쪽 위 핑 -- 최근 3번 평균(ms), 150 미만 초록 / 300 미만 노랑 / 그 위 빨강. */
  var pingHist = [];
  // [2026-11-04] "유저 핑 838ms" -- 어디서 늦는지 나눠 보려고: 서버 처리(응답의 ms), 화면 멈춤(그 사이 가장 길었던 프레임 간격, 100ms 넘을 때만)
  var frameGapMax = 0, lastFrameT = 0, netAvg = -1, netGap = 0;
  var NET_DV = /KAKAOTALK/i.test(navigator.userAgent) ? 2 : /Mobi|Android|iPhone|iPad/i.test(navigator.userAgent) ? 1 : 0; // 0 PC / 1 폰 / 2 카톡 안
  function showPing(ms, viaWs, srvMs) {
    if (!el.ping) return;
    pingHist.push(ms); if (pingHist.length > 3) pingHist.shift();
    var avg = Math.round(pingHist.reduce(function (a, b) { return a + b; }, 0) / pingHist.length);
    var gap = Math.round(frameGapMax); frameGapMax = 0;
    netAvg = avg; netGap = gap; // [2026-11-04] 다음 턴에 서버로(관리자 화면 -- PC/폰 비교)
    el.ping.textContent = '📶 ' + avg + 'ms · ' + (viaWs ? 'WS' : 'HTTP') + (srvMs != null ? ' · 서버 ' + srvMs : '') + (gap > 100 ? ' · 화면 ' + gap : '');
    var pshow = avg >= 100 ? 'inline-block' : 'none'; // [2026-11-05] "핑 정보는 100ms 이상일 때만"
    if (el.ping.style.display !== pshow) el.ping.style.display = pshow;
    luckHud(); // [2026-11-04] 누르면 통신 방식 바꾸기(netToggle)
    el.ping.className = 'mine-ping mpx ' + (avg < 150 ? 'ok' : avg < 300 ? 'mid' : 'bad');
    if (!el.ping._tap) { // [2026-11-04] "네트워크 속도 부분을 누르면 통신 방식이 바뀌게"
      el.ping._tap = 1; el.ping.style.pointerEvents = 'auto'; el.ping.style.cursor = 'pointer';
      el.ping.title = '눌러서 통신 방식(WebSocket/HTTP) 바꾸기';
      el.ping.onclick = function () { netToggle(); };
    }
  }
  /** [2026-11-05] 행운의 주문서 남은 시간 -- 핑과 따로(왼쪽 위), 끝나면 숨김. */
  function luckHud() {
    var lu = el.luck || (el.luck = document.getElementById('mineLuck'));
    if (!lu) return;
    var lk = st && st.luckUntil ? st.luckUntil - nowSrv() : 0, tx = lk > 0 ? '🍀 경험치 3배 · 드랍 2배 ' + Math.floor(lk / 60000) + ':' + ('0' + Math.floor(lk / 1000) % 60).slice(-2) : '';
    if (lu._t !== tx) { lu._t = tx; lu.textContent = tx; lu.style.display = tx ? 'block' : 'none'; }
  }
  /** [2026-11-04] 이 기기에서 고른 통신 방식(localStorage tw-mine-net = ws/http), 없으면 서버 설정(st.ws, TBOT_S5_CONFIG.S55_WS). */
  function netMode() {
    var v = null;
    if (wsDirect()) return 'ws'; // [2026-11-05] PC·폰 모두(사용자 지시 "폰이나 http도 전부 ws로") -- 빠른 길(서울 CloudFront WebSocket)로 고정 -- HTTP는 Cloudflare 해외 경유라 항상 느림(소켓이 끊기면 그때만 HTTP로 대신)
    try { v = localStorage.getItem('tw-mine-net2'); } catch (e) { } // [2026-11-05] 예전에 고른 HTTP 선택(tw-mine-net)은 한 번 초기화
    return v === 'ws' || v === 'http' ? v : (st && (st.ws || wsDirect()) ? 'ws' : 'http'); // [2026-11-04] 직접 주소가 있으면 기본 WebSocket
  }
  function netToggle() {
    if (wsDirect()) { msg('📶 빠른 WebSocket(서울)으로 고정돼 있어요.', 2200); return; } // [2026-11-05]
    var next = netMode() === 'ws' ? 'http' : 'ws';
    try { localStorage.setItem('tw-mine-net2', next); } catch (e) { }
    pingHist = [];
    if (next === 'ws') { wsRetry = 0; wsOpen(); } else wsClose();
    msg(next === 'ws' ? '📶 통신 방식: WebSocket' : '📶 통신 방식: HTTP (예전 방식)', 2200);
  }
  function wsClose() { clearTimeout(wsTimer); var w = ws; ws = null; wsOk = false; if (w) { try { w.close(); } catch (e) { } } }
  function apiFast(path) {
    if (!wsOk || !ws || ws.readyState !== 1) return api(path);
    return new Promise(function (ok, no) {
      var i = ++wsSeq;
      // [2026-11-04] "컴으로 하면 끊긴다" -- PC(절전/와이파이 바뀜/백신 웹 보호/회사 프록시)에선 소켓이 열린 채로 답만 안 오는 일이 있다(onclose가 안 옴).
      //  그러면 매 턴 5초 기다리다 실패만 반복 -> 1분 뒤 "접속 끊김". 답이 3초 안에 안 오면 그 소켓을 버리고 바로 HTTP로 다시 보내고, 소켓은 3초 뒤 새로 잇는다.
      var sock = ws;
      wsWait[i] = { ok: ok, no: no, t: setTimeout(function () {
        delete wsWait[i];
        if (ws === sock) { wsClose(); wsTimer = setTimeout(wsOpen, 3000); }
        // 턴(위치 알림)만 HTTP로 다시 보낸다 -- 전투/환경 턴(c/e)은 빼고(소켓 쪽이 이미 처리했을 수 있어 두 번 맞지 않게), 공격/캐기는 다시 보내지 않는다(두 번 되지 않게)
        if (path.indexOf('/api/tower-mine-tick') === 0) apiRaw(path.replace(/&[ce]=1/g, '')).then(ok, no); else no(new Error('timeout'));
      }, 3000) };
      ws.send(JSON.stringify({ i: i, p: path }));
    }).then(apiPost);
  }
  /** 서버에 보내는 맵 version -- 리셋 등으로 맵이 바뀌었으면 서버가 reload로 답한다. */
  function verQ() { return map && map.version ? '&v=' + encodeURIComponent(map.version) : ''; }
  // [2026-11-03] 미리 받은 맵(메모리) -- 포탈을 타도 다시 받지 않는다(localStorage는 용량이 차면 못 쓸 수 있어서 함께 둔다)
  var mapMem = {};
  function ensureMap(id, ver) {
    var key = 'tw-mine-map-' + id;
    if (mapMem[id] && mapMem[id].version === ver) return Promise.resolve(mapMem[id]);
    try {
      var c = JSON.parse(localStorage.getItem(key) || 'null');
      if (c && c.version === ver && c.rows) { mapMem[id] = c; return Promise.resolve(c); }
    } catch (e) { }
    var mapPath = '/api/tower-mine-map?mapId=' + encodeURIComponent(id), mapHttp = function () { return fetch(cfg.base + mapPath).then(function (r) { return r.json(); }); };
    return (wsOk && ws && ws.readyState === 1 ? wsCall(mapPath, 15000).then(function (d) { return d && d._http ? mapHttp() : d; }, mapHttp) : mapHttp()).then(function (m) { // [2026-11-06] 소켓이 열려 있으면 소켓으로(실패하면 HTTP -- 읽기만이라 다시 보내도 된다)
      if (m.error || !m.rows) throw new Error(m.error || '맵을 불러오지 못했어요.');
      try { localStorage.removeItem('tw-mine-map'); localStorage.setItem(key, JSON.stringify(m)); } catch (e) { }
      mapMem[id] = m;
      return m;
    });
  }
  /** [2026-11-03] "게임 시작 때 모든 리소스 로딩을 먼저, 진행 중엔 매끄럽게" -- 모든 맵(버전이 바뀐 것만)을 6개씩 나눠 받고 광석 그림도 미리 만든다.
   *  처음 한 번은 몇 초 걸리고, 그 뒤엔 저장본(localStorage)으로 바로. 실패한 맵은 그냥 두고(들어갈 때 다시 받는다) 게임은 시작한다. */
  function preloadAll(vers) {
    for (var g = 1; g <= 65; g++) { try { oreIcon(g); } catch (e) { } }
    var ids = Object.keys(vers || {}).filter(function (id) { return !(mapMem[id] && mapMem[id].version === vers[id]); });
    if (!ids.length) return Promise.resolve();
    var done = 0, total = ids.length, i = 0;
    var show = function () { if (opened && el.load.style.display !== 'none') el.load.textContent = '리소스 준비 중... (' + done + ' / ' + total + ')'; };
    show();
    var worker = function () {
      if (i >= ids.length) return Promise.resolve();
      var id = ids[i++];
      return ensureMap(id, vers[id]).catch(function () { }).then(function () { done++; show(); return worker(); });
    };
    var ws6 = [];
    for (var k = 0; k < 6; k++) ws6.push(worker());
    return Promise.all(ws6);
  }
  /** 서버 상태(맵/위치/캔 칸)로 화면을 다시 맞춘다 -- 처음 들어올 때, 포탈을 탄 뒤, 다른 곳에서 위치가 바뀌었을 때. */
  function enterState(s) {
    st = s;
    STEP_MS = s.stepMs || 140; // [2026-10-24] 신속의 장화
    if (s.mercPay) setTimeout(function () { mercPayNote(s.mercPay); }, 1500); // [2026-11-06] 접속하지 않은 동안의 용병 급여
    if (s.weaponOffMsg) setTimeout(function () { msg(s.weaponOffMsg, 3600); }, 900); // [2026-10-29] 무기 장착 레벨이 모자라 칼로 바꿔 듦
    if (s.wsHost && s.wsHost !== wsHostMem) { wsHostMem = s.wsHost; try { localStorage.setItem('tw-mine-wshost', s.wsHost); } catch (e) { } } // [2026-11-06] 다음 입장 때 소켓을 먼저
    buildSprites(s.look); setCombat(s.combat); mobSt = {}; mobDeathReady = false; mobDeaths = []; noOre = {}; lavaCells = {}; scan = null; scanKey = ""; bossWarned = false; cold = null; lastBz = 0; raid = null; heatWarned = false; iceSt = null; /* [2026-10-26] 한기, [2026-10-28] 대주교, 더위, 얼음 여왕 */
    if (s.notice && !noticeShown) { noticeShown = true; noticeData = { id: s.noticeId, text: s.notice }; setTimeout(function () { openSmith({ name: '광산 소식', line: '', kind: 'notice' }, 'notice'); }, 700); } // [2026-10-28] 광산 소식(한 번)
    others = {}; myBubbleT0 = 0; // [2026-10-24] 외형, 광산 레벨, 몬스터 상태(새 맵), 자동채광 기록, 탐지, 보스 경고
    el.coord.classList.toggle('admin', !!s.adminMaps); // [2026-10-24] 관리자면 좌표를 눌러 이동
    return ensureMap(s.mapId, s.mapVersion).then(function (m) {
      map = m;
      setTimeout(function () { try { var seen = {}; (m.mobs || []).forEach(function (mb) { if (seen[mb.k] || map !== m) return; seen[mb.k] = 1; mobSprite(mb.k); }); } catch (e) { } }, 0); // [2026-11-06] 몬스터 그림은 종류마다 한 번 그려 두고 다시 쓴다 -- 맵에 들어올 때 미리(처음 마주칠 때 멈칫하지 않게)
      portalAt = {};
      (m.portals || []).forEach(function (p) { portalAt[p.x + ',' + p.y] = p; });
      npcAt = {};
      (m.npcs || []).forEach(function (n) { npcAt[n.x + ',' + n.y] = n; });
      // 배(q) 칸 묶음 -> 한 장 그림
      ship = null;
      var qx0 = 1e9, qy0 = 1e9, qx1 = -1, qy1 = -1;
      for (var yy = 0; yy < m.h; yy++) {
        var rr = m.rows[yy], qi = rr.indexOf('q');
        while (qi >= 0) { qx0 = Math.min(qx0, qi); qx1 = Math.max(qx1, qi); qy0 = Math.min(qy0, yy); qy1 = Math.max(qy1, yy); qi = rr.indexOf('q', qi + 1); }
      }
      if (qx1 >= 0) ship = buildShip(qx0, qy0, qx1 - qx0 + 1, qy1 - qy0 + 1);
      closeSmith(); talkBubble = null; lastWindT = performance.now();
      dug = {};
      var a = s.dug || [];
      for (var i = 0; i + 2 < a.length; i += 3) dug[a[i] + ',' + a[i + 1]] = a[i + 2];
      recent = (s.recent || []).slice();
      buildMiniBase();
      P.dir = 'down'; keys = []; drag = null; floats = []; tapDir = null;
      setPos(s.x, s.y);
      busy = false; // showStamina보다 먼저 -- 안 그러면 ⛏️ 버튼이 비활성처럼 보인 채로 남는다
      showStamina(); renderLog();
    });
  }
  function reload() {
    busy = true;
    return api('/api/tower-mine').then(enterState).then(function () { fadeIn('🗺️ ' + map.name); })
      .catch(function () { busy = false; fadeIn(null); msg('불러오기 실패 -- 다시 들어와 주세요.'); });
  }
  function setPos(x, y) {
    P.x = P.tx = P.fx = x; P.y = P.ty = P.fy = y; P.moving = false;
    lastSaved = map.id + ':' + x + ',' + y;
    showCoord(); drawMini();
  }
  function savePos() {
    // [2026-10-24] 걷는 중에도 저장(P.x/P.y = 마지막으로 다 걸은 칸) -- 예전엔 움직이는 동안 건너뛰어서, 오래 계속 걸으면 멈출 때 한꺼번에 먼 거리를 보내
    //  서버 순간이동 검사(최근 15초 이동 가능 거리)에 걸려 되돌아갔다.
    if (!opened || busy || !map) return;
    var cur = map.id + ':' + P.x + ',' + P.y;
    if (cur === lastSaved) return;
    lastSaved = cur;
    api('/api/tower-mine-pos?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
      if (d && d.reload) { reload(); return; }
      if (d && d.ok === false && d.x != null) { setPos(d.x, d.y); msg(d.frozen ? '🧊 얼어붙어서 움직일 수 없어요!' : '이동 기록이 맞지 않아 (' + d.x + ', ' + d.y + ')로 돌아갔어요.'); }
    }).catch(function () { lastSaved = ''; });
  }
  /** exit=true: 광산을 나가거나 화면을 벗어날 때 -- 위치가 그대로여도 보내서 서버가 퇴장 로그를 남긴다(한 번만). */
  function saveBeacon(exit) {
    if (!map) return;
    var x = P.moving ? P.fx : P.x, y = P.moving ? P.fy : P.y;
    if (exit && exitSent) return;
    if (!exit && map.id + ':' + x + ',' + y === lastSaved) return;
    lastSaved = map.id + ':' + x + ',' + y;
    if (exit) exitSent = true;
    var body = new URLSearchParams();
    body.append('userName', cfg.user); body.append('mapId', map.id); body.append('x', x); body.append('y', y); body.append('v', map.version || '');
    if (exit) { body.append('exit', '1'); body.append('cid', mineCid); } // [2026-11-07] 나가면 바로 다른 곳에서 들어올 수 있게
    if (!exit && wsOk && ws && ws.readyState === 1) { // [2026-11-06] 걷는 중 저장은 소켓으로(나갈 때는 창이 닫히며 소켓이 끊길 수 있어 예전처럼 sendBeacon)
      wsCall('/api/tower-mine-pos?mapId=' + encodeURIComponent(map.id) + '&x=' + x + '&y=' + y + '&v=' + encodeURIComponent(map.version || '')).catch(function () { lastSaved = ''; });
      return;
    }
    try {
      if (!(navigator.sendBeacon && navigator.sendBeacon(cfg.base + '/api/tower-mine-pos', body))) {
        fetch(cfg.base + '/api/tower-mine-pos', { method: 'POST', body: body, keepalive: true });
      }
    } catch (e) { }
  }
  // [2026-10-28] 설산 동굴 -- 땅은 못 파고, 바라보는(없으면 상하좌우) 얼음 광맥(I)을 연속 채광. 20번이면 빙석, 몬스터에게 맞으면 처음부터.
  var veinBusy = false, veinCount = 0;
  function veinDig() {
    if (!map || busy || veinBusy || fade || P.moving) return;
    var dirs = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }, f = dirs[P.dir];
    var live = function (d) { var cx = P.x + d[0], cy = P.y + d[1]; return cellAt(cx, cy) === 'I' && !dug[cx + ',' + cy]; }; // [2026-10-29] 다 캔 광맥은 건너뛴다
    var doneNear = false;
    for (var k0 in dirs) if (cellAt(P.x + dirs[k0][0], P.y + dirs[k0][1]) === 'I' && !live(dirs[k0])) doneNear = true;
    if (!(f && live(f))) {
      f = null;
      for (var k in dirs) if (live(dirs[k])) { P.dir = k; f = dirs[k]; break; }
    }
    if (!f) { msg(doneNear ? '💠 이미 빙석을 캔 광맥이에요. 다른 광맥을 찾아보세요. (맵 리셋 때 다시 차올라요)' : '💠 얼음 광맥 바로 앞에서 캐요. 이 동굴은 땅을 팔 수 없어요.', 2200); return; }
    var tx = P.x + f[0], ty = P.y + f[1];
    veinBusy = true; P.digT0 = performance.now(); P.swing = false;
    api('/api/tower-mine-vein?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&tx=' + tx + '&ty=' + ty + verQ()).then(function (d) {
      veinBusy = false;
      if (!d || d.tooFast) return;
      if (!d.ok) { if (d.veinDone) { dug[tx + ',' + ty] = 19; buildMiniBase(); } if (d.message) msg(d.message, 1800); return; }
      veinCount = d.gain ? 0 : d.count;
      (d.veinCells || []).forEach(function (c) { dug[c[0] + ',' + c[1]] = 19; }); // [2026-10-29] 다 캔 광맥(이어진 칸 전체)
      var t = performance.now();
      if (d.cutIce) { // [2026-10-28] 설산 동굴 2층 -- 가끔 세공된 빙석
        if (d.ores) st.ores = d.ores;
        var ci = new Image(); ci.src = oreIcon(54);
        floats.push({ x: tx, y: ty - 0.9, text: '+1 세공된 빙석', color: '#bfefff', big: true, icon: ci, t0: performance.now() + 200 });
      }
      if (d.gain) {
        if (d.ores) st.ores = d.ores;
        flashT0 = t;
        var im = new Image(); im.src = oreIcon(d.grade || 19);
        floats.push({ x: tx, y: ty - 0.4, text: '+1 빙석', color: '#bfefff', big: true, icon: im, t0: t });
        msg(d.message, 2800);
      } else floats.push({ x: tx, y: ty - 0.2, text: '💠 ' + d.count + '/' + d.need, color: d.count >= d.need - 3 ? '#ffe14d' : '#cfefff', t0: t });
    }).catch(function () { veinBusy = false; });
  }
  function veinBroke() {
    if (!map || !map.vein || !veinCount) return;
    veinCount = 0;
    floats.push({ x: P.x, y: P.y - 1.0, text: '💥 연속 채광 끊김!', color: '#ff8a8a', big: true, t0: performance.now() + 120 });
    msg('💥 박쥐에게 맞아 연속 채광이 끊겼어요! 다시 20번을 캐야 해요.', 2400);
  }
  // [2026-11-11] 하피의 둥지 -- "곡괭이로 채광할 수도, 10회 정도면 쓰러져 밑동만" -- 바라보는 칸(없으면 둘레 8칸)의 나무를 찍는다
  var chopBusy = false;
  function chopTree() {
    if (!opened || !map || chopBusy || P.moving) return;
    var dv = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }[P.dir] || [0, 1];
    var tx = P.x + dv[0], ty = P.y + dv[1];
    if (cellAt(tx, ty) !== 'E') { tx = -1; for (var oy = -1; oy <= 1 && tx < 0; oy++) for (var ox = -1; ox <= 1; ox++) if (cellAt(P.x + ox, P.y + oy) === 'E') { tx = P.x + ox; ty = P.y + oy; break; } }
    if (tx < 0) { msg('🪓 나무 바로 옆에서 캐면 곡괭이로 나무를 찍을 수 있어요.', 2200); return; }
    chopBusy = true; P.digT0 = performance.now(); P.swing = false;
    apiFast('/api/tower-mine-chop?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&tx=' + tx + '&ty=' + ty + verQ()).then(function (d) {
      chopBusy = false;
      if (!d || d.tooFast) return;
      if (d.reload) { reload(); return; }
      if (!d.ok) { if (d.message) msg(d.message, 2200); return; }
      floats.push({ x: tx, y: ty - 0.3, text: '🪓 ' + d.chop + '/' + d.need, color: '#ffe0a0', t0: performance.now() });
      if (d.felled) { flashT0 = performance.now(); msg(d.message, 2600); treeFallAt[d.tree] = performance.now(); treeSt.forEach(function (q) { if (q[0] === d.tree) q[1] = 3; }); }
    }).catch(function () { chopBusy = false; });
  }
  function dig() {
    if (el.menu && el.menu.classList.contains('open')) setMenu(false); // [2026-11-05]
    if (map && map.trees) { chopTree(); return; } // [2026-11-11] 하피의 둥지
    if (map && map.vein) { veinDig(); return; } // [2026-10-28] 설산 동굴
    if (!opened || busy || !map) return;
    if (P.moving) return;
    var x = P.x, y = P.y, c = cellAt(x, y);
    if (c === 'V') { msg('마을에서는 캘 수 없어요. 마을 밖으로 나가 보세요.'); return; }
    if (c === 'T') { msg('나무 밑은 캘 수 없어요. 옆 칸에서 캐 보세요.'); return; }
    if (dug[x + ',' + y]) { msg('이미 캔 자리예요. 다른 곳을 찾아보세요.'); return; }
    if (noOreCell(x, y) || c === 'e') { msg('여기서는 캘 수 없어요.'); return; } // [2026-11-11] 서버에 묻지 않고 바로(곡괭이질 시간 낭비 없이)
    busy = true;
    el.dig.disabled = true;
    P.digT0 = performance.now(); P.swing = false; // 곡괭이
    apiFast('/api/tower-mine-dig?mapId=' + encodeURIComponent(map.id) + '&x=' + x + '&y=' + y + '&dir=' + P.dir + verQ()).then(function (d) { // [2026-10-25] dir = 쌍날 곡괭이 앞뒤
      busy = false;
      if (d.durability != null) { st.durability = d.durability; st.durabilityMax = d.durabilityMax; }
      if (d.ok) {
        lastSaved = map.id + ':' + x + ',' + y;
        dug[x + ',' + y] = d.grade;
        (st.ores || []).forEach(function (o) { if (o.grade === d.grade) o.qty = d.qty; }); // 대장간 창의 보유 수량도 맞춘다
        recent.unshift({ mapName: map.name, x: x, y: y, grade: d.grade, at: Date.now() });
        var img = new Image(); img.src = oreIcon(d.grade);
        var rare = (d.grade >= 5 && d.grade <= 6) || !!RARE_GEM[d.grade]; // 미스릴/별빛석, 귀한 보석(바닷길에서 캔 칸은 보석)
        floats.push({ x: x, y: y, text: '+' + (d.gain || 1) + ' ' + d.oreName, color: (ORE_COL[d.grade] || ORE_COL[1])[1], big: rare, icon: img, t0: performance.now() });
        if (rare) { flashT0 = performance.now(); msg('✨ ' + d.oreName + '! ' + map.name, 3200); } // [2026-10-28] 좌표 빼기
        if (specialDrop(d.grade)) banner = { text: '✨ ' + d.oreName + '!', t0: performance.now() }; // [2026-10-29] 귀한 것은 가운데 이름까지
        // [2026-10-24] 광역 곡괭이 -- 주변 칸도 함께 캔 결과
        (d.wide || []).forEach(function (w, i) {
          dug[w.x + ',' + w.y] = w.grade;
          recent.unshift({ mapName: map.name, x: w.x, y: w.y, grade: w.grade, at: Date.now() });
          var wi = new Image(); wi.src = oreIcon(w.grade);
          floats.push({ x: w.x, y: w.y, text: '+' + (w.gain || 1) + ' ' + w.oreName, color: (ORE_COL[w.grade] || ORE_COL[1])[1], icon: wi, t0: performance.now() + 60 * (i + 1) });
        });
        if (d.ores) st.ores = d.ores;
        if (d.iceGain) { // [2026-10-28] 설산 빙석
          var iimg = new Image(); iimg.src = oreIcon(19);
          floats.push({ x: x, y: y - 1.0, text: '+1 빙석', color: '#bfefff', icon: iimg, t0: performance.now() + 350 });
        }
        if (d.detectDim) { // 탐지 곡괭이가 별빛석에 빛을 잃음 -- 12시간 뒤 회복
          (st.gear || []).forEach(function (g) { if (g.code === 'DETECT_PICK') g.dim = true; });
          scan = null;
          msg('🌑 탐지 곡괭이가 별빛에 눌려 빛을 잃었어요... 12시간 뒤 다시 빛나요.', 4200);
        }
        if (d.cutGrade) { // [2026-11-05] 채광 중 가끔 세공된 보석
          var cimg = new Image(); cimg.src = oreIcon(d.cutGrade);
          floats.push({ x: x, y: y - 1.0, text: '+1 ' + d.cutName, color: '#fff4b0', big: true, icon: cimg, t0: performance.now() + 350 });
          flashT0 = performance.now(); rainbowGo(x, y); killFeed('+1 ' + d.cutName, '#fff4b0', cimg.src); // [2026-11-05] 무지개 빛 + 왼쪽 기록(가운데 상자 대신)
        }
        if (d.gemGrade) { // [2026-10-24] 보석이 함께 나옴
          (st.ores || []).forEach(function (o) { if (o.grade === d.gemGrade) o.qty = d.gemQty; });
          var gimg = new Image(); gimg.src = oreIcon(d.gemGrade);
          killFeed('+1 ' + d.gemName, (ORE_COL[d.gemGrade] || ORE_COL[1])[1], gimg.src); // [2026-11-05] 왼쪽 획득 기록
          floats.push({ x: x, y: y - 0.6, text: '+1 ' + d.gemName, color: (ORE_COL[d.gemGrade] || ORE_COL[1])[1], big: !!RARE_GEM[d.gemGrade], icon: gimg, t0: performance.now() + 250 });
          if (RARE_GEM[d.gemGrade]) { flashT0 = performance.now(); msg('💎 ' + d.gemName + '! ' + map.name, 3600); } // [2026-10-28] 좌표 빼기 }
          else msg('💎 ' + d.gemName + '도 함께 나왔어요!', 2200);
        }
        showCoord(); drawMini(); renderLog();
      } else {
        if (d.reload) { reload(); return; }
        if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
        if (d.seaLimit) setAuto(false); // [2026-10-24] 바닷길 하루 보석 한도
        if (d.digFail) floats.push({ x: x, y: y, text: '💨 실패', color: '#c9c3d6', t0: performance.now() }); // [2026-10-30] 내구도 0 -- 성공률 20%
        else if (d.noOre || auto) noOre[x + ',' + y] = 1; // [2026-10-24] 자동채광이 같은 칸을 다시 고르지 않게
        if (!(auto && d.noOre)) msg(d.message || d.error || '캐지 못했어요.');
      }
      showStamina();
    }).catch(function () { busy = false; showStamina(); msg('요청 실패 -- 다시 시도해주세요.'); });
  }

  function open(o) {
    cfg = o;
    bindDom();
    if (!tiles) { buildTiles(); buildSprites(); }
    el.ov.classList.add('open');
    el.load.style.display = 'flex';
    el.log.style.display = 'none';
    document.body.style.overflow = 'hidden';
    opened = true;
    map = null;
    busy = true;
    fade = null; banner = null;
    fit();
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(loop);
    exitSent = false;
    var vers = null;
    if (netMode() === 'ws') wsOpen(); // [2026-11-06] 입장 요청도 소켓으로 -- 기억해 둔 서울 주소로 먼저 열고(최대 1.5초) 보낸다
    wsReady(1500).then(function () { return api('/api/tower-mine?enter=1&cid=' + mineCid); }).then(function (s) { // enter=1: 서버가 입장 로그를 남긴다
      if (s.dup) { var de = new Error(s.message); de.dup = true; throw de; } // [2026-11-07] 같은 캐릭터로 이미 접속 중
      if (s.error) throw new Error(s.error);
      vers = s.mapVers;
      return enterState(s);
    }).then(function () {
      return preloadAll(vers); // [2026-11-03] 게임 시작 때 모든 맵을 먼저 받아 둔다(진행 중엔 포탈을 타도 바로)
    }).then(function () {
      if (!opened) return;
      el.load.style.display = 'none';
      noticeOnce();
      fadeIn('🗺️ ' + map.name);
      msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : '방향키·화면 끌기로 이동, ⛏️(스페이스)로 캐기, 몬스터 옆에서는 ⚔️로 공격. 🤖 자동채광을 켜면 알아서 캐요.', 3500);
      if (netMode() === 'ws') wsOpen(); // [2026-11-03] 시즌 5.5 WebSocket -- [2026-11-04] 기본은 서버 설정, 핑을 누르면 이 기기에서 바꾼다 -- [2026-11-04] 설정(S55_WS = 1)일 때만, 기본은 HTTP
      clearInterval(mobTimer); pollMobs(); // [2026-10-24] 몬스터 체력/쓰러짐 조회 -- [2026-11-03] 처음 한 번, 그 뒤로는 1초 턴(presTick)에 함께
      clearInterval(presTimer); presTimer = setInterval(presTick, 1000); // [2026-10-24] 다른 유저 위치 주고받기
      clearInterval(saveTimer);
      saveTimer = setInterval(function () {
        savePos();
      }, SAVE_MS);
    }).catch(function (e) {
      if (e && e.dup) { el.load.style.whiteSpace = 'pre-line'; el.load.textContent = e.message; return; } // [2026-11-07] 다른 곳에서 접속 중 -- 안내만
      el.load.textContent = '광산을 불러오지 못했어요. (' + (e && e.message ? e.message : '오류') + ')';
    });
  }
  function close() {
    if (!opened) return;
    setMenu(false);
    if (map && !dupClosing) saveBeacon(true); // [2026-11-07] 다른 곳에서 들어와 끝나는 화면은 위치를 저장하지 않는다(그쪽 캐릭터를 옛 자리로 되돌리지 않게)
    dupClosing = false;
    closeSmith();
    opened = false;
    cancelAnimationFrame(raf);
    clearInterval(saveTimer);
    clearInterval(mobTimer); setAuto(false); wsClose(); // [2026-10-24] [2026-11-03] WebSocket도 닫는다
    clearInterval(presTimer); others = {}; // [2026-10-24] 함께 보기
    keys = []; drag = null;
    el.ov.classList.remove('open');
    el.load.textContent = '광산으로 가는 중...';
    document.body.style.overflow = '';
    if (cfg && cfg.onClose) cfg.onClose();
  }
  // [2026-10-23] 마을로 이동 -- 어디에 있든 중앙 평원 마을(시작점)으로
  function goVillage() {
    if (!opened || busy || !map) return;
    var city = !!(st && st.respawnCity); // [2026-10-25] 부활 지점(성당에서 기도했으면 왕도 성당)으로
    if (!city && map.id === 'field' && Math.abs(P.x - map.spawnX) + Math.abs(P.y - map.spawnY) <= 6) { msg('이미 마을이에요.'); return; }
    if (st && st.combat && st.combat.fight) { msg('⚔️ 몬스터가 쫓아오고 있어요! 5칸 넘게 떨어진 뒤에 이동할 수 있어요.', 2800); return; }
    ask('🏠 마을로', (city ? '부활 지점인 <b>왕도 아스테리아 광장</b>' : '<b>중앙 평원 마을</b>') + '(으)로 이동할까요?', '이동', function () {
      travel('/api/tower-mine-village', '이동하지 못했어요.');
    });
  }
  function toggleMini() { el.mini.classList.toggle('big'); }
  function toggleLog() {
    var open = el.log.style.display !== 'block';
    el.log.style.display = open ? 'block' : 'none';
    if (open) { // [2026-10-28] 열 때마다 최근 획득·구매를 새로
      renderLog();
      api('/api/tower-mine-recent-get').then(function (d) { recentGet = (d && d.list) || []; renderLog(); }).catch(function () {});
    }
  }
  // 캔 시각: 오늘이면 "15:42", 아니면 "10/22 15:42"
  function digTime(ms) {
    if (!ms) return '';
    var d = new Date(ms), now = new Date();
    var hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    return d.toDateString() === now.toDateString() ? hm : (d.getMonth() + 1) + '/' + d.getDate() + ' ' + hm;
  }

  return { bossCall: bossCallGo, worldBoss: function () { worldBossOpen = !worldBossOpen; if (smithOpen && smithView === 'world') renderSmith(smithNpc); }, hermitGo: hermitGo, open: open, close: close, eqPop: eqPopGo, archiBuy: archiBuy, cityBuy: cityBuy, mercHire: mercHire, mercHireGo: mercHireGo, mercUse: mercUse, mercSkill: mercSkill, mercMode: mercMode, mercView: mercViewGo, mercLv99: mercLv99, mercPay: mercPay, mercAuto: mercAuto, mercSel: mercSelGo, mercTab: mercTabGo, talkNext: talkNext, smelt: smelt, gemCut: gemCut, artisan: artisan, digBtn: digBtn, atkBtn: atkBtn, ult: ultBtn, ultReset: ultReset, ultLearn: ultLearn, ultGift: ultGift, whirlSet: whirlSet, whirlLearn: whirlLearn, toggleMenu: toggleMenu, menuGo: menuGo, openEquip: openEquip, eqInfo: eqInfo, equipTab: equipTabGo, auraSet: auraSet, auraLearn: auraLearn, auraGift: auraGift, archiAct: archiAct, dig: dig, goVillage: goVillage, toggleMini: toggleMini, toggleLog: toggleLog, oreIcon: oreIcon, pickUpgrade: pickUpgrade, helmetUpgrade: helmetUpgrade, closeSmith: closeSmith, smithGo: smithGo, craft: craft, equipRing: equipRing, enhance: enhance, synthGalaxy: synthGalaxy, synth: synth, captain: captain,
           act: act, toggleAuto: toggleAuto, greet: greet, sail: sail, lookAct: lookAct, openWarp: openWarp, openWarpForm: openWarpForm, worldWarp: worldWarp, worldTab: worldTabGo, worldZoom: worldZoom, worldGo: worldGo, adminWarp: adminWarp, openWorld: openWorld, tryOn: tryOn, priestAct: priestAct, teleport: teleport, guideGo: guideGo, askYes: askYes, coatAct: coatAct, refine: refine, deliver: deliver, noticeOk: noticeOk, dayGo: dayGo, ORE_NAME: ORE_NAME, digTime: digTime };
})();
</script>
