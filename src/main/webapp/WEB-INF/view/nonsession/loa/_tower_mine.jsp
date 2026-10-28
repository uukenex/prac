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
  ORE_NAME[39] = '사막의 심장'; ORE_COL[39] = ['#e8a23a', '#fff0c0']; RARE_GEM[39] = 1; // [2026-10-28] 피라미드 보상 상자
  // [2026-10-28] 괴(40~44 = 구리~미스릴, 광석 전문가), 세공된 보석(45~53, 보석 세공사)
  ['동괴', '철괴', '은괴', '금괴', '미스릴괴'].forEach(function (n, i) { ORE_NAME[40 + i] = n; ORE_COL[40 + i] = ORE_COL[1 + i]; });
  for (var cgi = 0; cgi < 9; cgi++) { ORE_NAME[45 + cgi] = '세공된 ' + ORE_NAME[21 + cgi]; ORE_COL[45 + cgi] = ORE_COL[21 + cgi]; RARE_GEM[45 + cgi] = 1; }
  var TERR_NAME = { '.': '풀밭', ',': '흙길', ':': '바위 지대', 'V': '마을', 'H': '다리', '~': '물', 'T': '숲', '^': '바위산',
                    'c': '동굴 바닥', 'r': '자갈 바닥', '#': '동굴 벽', 'h': '초가집', 'j': '초가집', 'k': '초가집', 'l': '초가집', 'G': '지역 통로', 'I': '얼음 광맥', 'M': '동굴 입구', 'U': '나가는 길', 'S': '지하로 가는 문',
                    'd': '모래', 'R': '사암', 'C': '선인장', 'b': '모래사장', 'P': '부두', 'q': '배', 'B': '배에 오르는 판자', 'N': 'NPC',
                    's': '성 바닥', 'W': '성벽', 'n': '눈밭', 'Y': '눈 덮인 숲', 'K': '건물', 'Q': '성당', 'X': '텔레포트 게이트', 'u': '바다 모래', 'p': '용궁 길', 'e': '해초', 'O': '산호', 'L': '용궁 기둥', 'Z': '물' };
  var MINI_COL = { '.': [95, 174, 69], ',': [201, 164, 106], ':': [143, 133, 120], 'V': [232, 215, 168], 'H': [139, 90, 43], '~': [58, 120, 212],
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
    tiles.palaceWall = [1, 2].map(function (s) {
      return tile('#8a1e1e', [['#7a1818', 6]], 190 + s, function (g) {
        px(g, '#e8c25a', 0, 2, T, 1); px(g, '#e8c25a', 0, 13, T, 1); px(g, '#5a1010', 0, 14, T, 2);
        if (s === 1) { px(g, '#3a7ad8', 6, 5, 4, 6); px(g, '#e8c25a', 7, 6, 2, 1); } // 청기와 문양
      });
    });
    tiles.npc = { smith: npcSprite('smith'), captain: npcSprite('captain'), boutique: npcSprite('boutique'), priest: npcSprite('priest'), guide: npcSprite('guide'),
      blackmarket: blackSprite(), furrier: furrierSprite(), stall: stallSprite(), daykeeper: keeperSprite(), // [2026-10-26], [2026-10-28] 요일 동굴 문지기
      ninjaquest: npcSprite('ninjaquest'), boatwright: npcSprite('boatwright'), ferry: npcSprite('ferry'), chest: chestSprite(), antchest: chestSprite(), smelter: npcSprite('smelter'), gemcutter: npcSprite('gemcutter'), artisan: npcSprite('artisan') }; // [2026-10-28] 개미굴 보물 상자 // [2026-10-28] 피라미드 보상 상자 // [2026-10-28] 다도해 NPC(목록에 없으면 그림 없이 이름표만 나왔다)
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
  function npcSprite(kind) {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#f2c194';
    var smith = kind === 'smith', bq = kind === 'boutique', pr = kind === 'priest', gd = kind === 'guide'; // [2026-10-25] 이동 안내원(초록 여행복 + 깃발 지팡이)
     // [2026-10-24] 명품상점 주인(보라 연미복 + 실크햇 + 콧수염), 성당 신부(검은 사제복 + 흰 깃 + 금 십자가)
    var nq = kind === 'ninjaquest', bw = kind === 'boatwright', fr = kind === 'ferry';
    var so = kind === 'smelter', gc = kind === 'gemcutter', ar = kind === 'artisan'; // [2026-10-28] 광석 전문가(주황 앞치마), 보석 세공사(청록), 왕도 장인(검붉은) // [2026-10-28] 다도해 -- 신궁 수문장(붉은 갑옷), 배 목수(갈색 작업복), 뱃사공(파란 옷)
    var body = so ? '#c0682a' : gc ? '#2a8a8a' : ar ? '#7a2a2a' : smith ? '#7a4a1f' : bq ? '#7b3fa0' : pr ? '#24212b' : gd ? '#2f8a5a' : nq ? '#a8202a' : bw ? '#8a6a3a' : fr ? '#2a5aa8' : '#f4f4f0', bodyD = so ? '#8a4a1a' : gc ? '#1a5e5e' : ar ? '#4e1a1a' : smith ? '#5a3a1c' : bq ? '#5a2a78' : pr ? '#141218' : gd ? '#1f6342' : nq ? '#6a1018' : bw ? '#6a4a22' : fr ? '#1a3a78' : '#c9ccd6', pants = smith ? '#3a2b22' : bq ? '#2a1d3a' : pr ? '#24212b' : gd ? '#5a4630' : nq ? '#1a1a22' : bw ? '#3a2b22' : fr ? '#22305e' : '#22305e';
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
      case 'Z': return tiles.water[(wf + (x + y) % 2) % 2]; // [2026-10-24] 숨은 입구 -- 물과 똑같이 보인다
      case 'n': return tiles.snow[v % 2];
      case 'I': return tiles.vein[v % 2]; // [2026-10-28] 설산 동굴 얼음 광맥
      case 'Y': return tiles.pine[v % 2];
      case 'c': return tiles.cave[v % 2];
      case 'r': return tiles.gravel;
      case '#': return tiles.wall[v % 2];
      case 'h': case 'j': case 'k': case 'l': return tiles.house[c];
      case 'G': p = portalAt[x + ',' + y]; return tiles.gate[p && !p.locked ? 0 : 1];
      case 'M': return tiles.mouth;
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
      for (var s = 0; s < 3; s++) for (var k = 0; k < 3; k++) sprites[d + s + k] = charSprite(d, s, k, lk);
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
      if (grade >= 45 && grade <= 53) { px(g, '#ffffff', 1, 1); px(g, '#ffffff', 0, 2, 3, 1); px(g, '#ffffff', 1, 3); px(g, '#fff6b8', 13, 11); px(g, '#fff6b8', 12, 12, 3, 1); px(g, '#fff6b8', 13, 13); } // [2026-10-28] 세공된 보석 -- 반짝임 둘
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
      if (s.ox != null && t - s.mt < 450) { var q = Math.max(0, (t - s.mt) / 450); return { x: s.ox + (s.fx - s.ox) * q, y: s.oy + (s.fy - s.oy) * q, cx: s.fx, cy: s.fy }; } // [2026-10-25] 쫓아올 때 한 칸씩 걸어서
      return { x: s.fx, y: s.fy, cx: s.fx, cy: s.fy };
    }
    var SL = map.mobSlotMs || 3000, slot = Math.floor(t / SL);
    var b = mobCell(m, slot), a = mobCell(m, slot - 1);
    var bx = b % map.w, by = Math.floor(b / map.w), ax = a % map.w, ay = Math.floor(a / map.w), k = Math.min(1, (t - slot * SL) / 900);
    if (s && s.fu > 0 && t - s.fu < 900) { ax = s.fx; ay = s.fy; k = (t - s.fu) / 900; } // 멈춤이 풀린 직후
    return { x: ax + (bx - ax) * k, y: ay + (by - ay) * k, cx: k < 0.5 ? ax : bx, cy: k < 0.5 ? ay : by };
  }
  /** 몬스터 그림(종류별 16x16) -- kind: small 작은 짐승, quad 네발짐승, human 사람형, flyer 날짐승/유령, crab 게/전갈, blob 물컹이, snake 뱀. */
  function mobSprite(k, pose) {
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
    var dirs = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }, f = dirs[P.dir], m = f && mobOcc[(P.x + f[0]) + ',' + (P.y + f[1])];
    if (m) return { m: m, dir: P.dir };
    for (var key in dirs) { var v = dirs[key], mm = mobOcc[(P.x + v[0]) + ',' + (P.y + v[1])]; if (mm) return { m: mm, dir: key }; }
    // [2026-10-28] "공격 버튼은 내 캐릭터 주변 동서남북을 포함한 8방향을 타겟으로" -- 상하좌우에 없으면 대각선(바라보는 쪽은 좌우)
    var diag = [[-1, -1], [1, -1], [-1, 1], [1, 1]];
    for (var di = 0; di < diag.length; di++) { var dm = mobOcc[(P.x + diag[di][0]) + ',' + (P.y + diag[di][1])]; if (dm) return { m: dm, dir: diag[di][0] < 0 ? 'left' : 'right' }; }
    return null;
  }
  /** 서버 몬스터 상태 [id, hp, deadUntil, fx, fy, frozenUntil] -> 화면 상태. 멈춘(쫓아오는) 칸이 바뀌었으면 옛 칸에서 걸어오게. */
  function mobStOf(a, old) {
    var n = { hp: a[1], du: a[2], fx: a[3], fy: a[4], fu: a[5], sl: a[6] === 1 }, t = nowSrv(); // [2026-10-28] sl = 닌자 은신 중
    if (old && old.fu > t && n.fu > t && (old.fx !== n.fx || old.fy !== n.fy)) { n.ox = old.fx; n.oy = old.fy; n.mt = t; }
    else if (old && old.mt != null && t - old.mt < 450) { n.ox = old.ox; n.oy = old.oy; n.mt = old.mt; }
    return n;
  }
  function pollMobs() {
    if (!opened || !map || !map.mobs || !map.mobs.length) return;
    var mid = map.id;
    fetch(cfg.base + '/api/tower-mine-mobs?mapId=' + encodeURIComponent(mid)).then(function (r) { return r.json(); }).then(function (d) {
      if (!map || map.id !== mid || !d) return;
      if (d.now) srvOff = d.now - Date.now();
      var ns = {};
      (d.mobs || []).forEach(function (a) { ns[a[0]] = mobStOf(a, mobSt[a[0]]); });
      mobSt = ns;
    }).catch(function () { });
  }

  // ---------------------------------------------------------------- 화면
  function $(id) { return document.getElementById(id); }
  function bindDom() {
    if (bound) return;
    bound = true;
    el.ov = $('mineOverlay'); el.cv = $('mineCanvas'); el.coord = $('mineCoord'); el.terr = $('mineTerr');
    el.sta = $('mineSta'); el.regen = $('mineRegen'); el.mini = $('mineMini'); el.dig = $('mineDigBtn'); el.atk = $('mineAtkBtn'); el.menu = $('mineMenu'); el.menuBtn = $('mineMenuBtn'); // [2026-10-28] 공격 버튼 분리, ☰ 메뉴
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
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); act(); } // [2026-10-24] 옆에 몬스터가 있으면 공격, 없으면 캐기
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
  function msg(text, ms) {
    el.msg.textContent = text;
    el.msg.style.display = 'block';
    clearTimeout(msgTimer);
    msgTimer = setTimeout(function () { el.msg.style.display = 'none'; }, ms || 2600);
  }
  function cellAt(x, y) { return map && x >= 0 && y >= 0 && x < map.w && y < map.h ? map.rows[y].charAt(x) : '^'; }
  function walk(x, y) {
    var c = cellAt(x, y);
    if (st && st.look && st.look.shoesCode === 'SHOE_GM') return !!map && x >= 0 && y >= 0 && x < map.w && y < map.h && PORTALS.indexOf(c) < 0 && c !== 'N'; // [2026-10-28] 바람의 신(운영자) -- 장애물/몬스터 통과, NPC/포탈 칸에선 멈춘다(말 걸기/들어가기)
    if (mobOcc[x + ',' + y]) return false; // [2026-10-24] 몬스터가 있는 칸은 못 지나간다
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
    el.sta.textContent = '⛏️ ' + st.durability + ' / ' + st.durabilityMax;
    // [2026-10-24] 오른쪽 위 안내는 맵 리셋 시각만(대장장이 안내/보석 목록은 뺐다)
    el.regen.textContent = st.nextResetAt ? '🔄 맵 리셋 ' + resetText(st.nextResetAt) : '';
    el.dig.disabled = busy; // 내구도가 없어도 누를 수 있다(캐기는 dig()가 안내)
    if (el.atk) el.atk.disabled = busy;
  }
  // [2026-10-24] 광산 레벨/체력/경험치(체력은 서버가 준 시각부터 60초에 다 차게 화면도 같이 올린다)
  var combatT0 = 0, hpShownT = 0;
  function setCombat(c) {
    if (!c) return;
    st.combat = c; combatT0 = Date.now(); renderHp();
    // [2026-10-28] 환생 가능(99레벨 경험치 가득) -- 화면을 연 뒤 처음 한 번만 안내(잡는 순간엔 서버 처치 메시지에도 같은 안내가 붙는다)
    if (c.rebirthReady && !rbHintShown) { rbHintShown = true; msg('✨ 경험치가 가득 찼어요! 왕도 12시 방향 성당의 신부님께 가면 환생할 수 있어요.', 4200); }
  }
  var rbHintShown = false;
  /** [2026-10-28] "Lv.1(환생1)" -- 칸이 좁으면(체력 숫자가 길면) "Lv.1✦1"로 줄인다(마우스를 올리면 전체). */
  function lvLabel(c, compact) {
    var rb = c.rebirth | 0;
    if (!rb) return 'Lv.' + c.lv;
    return 'Lv.' + c.lv + '<span class="rb">' + (compact ? '✦' + rb : '(환생' + rb + ')') + '</span>';
  }
  function hpNow() {
    var c = st && st.combat;
    if (!c) return 0;
    // [2026-10-26] 서버와 같이 마지막 전투 뒤 대기(regenFrom, 서버 시각)가 지나야 차오른다. c.hp = 서버 시각 c.at 때의 체력.
    // [2026-10-28] 전투 중에도 1/4 속도로(대기 없이), 그늘/모닥불 등 안전지대에선 x1.5(서버 mineHpNow와 같은 식)
    var tSrv = c.at + (Date.now() - combatT0), from = c.fight ? c.at : Math.max(c.at, c.regenFrom || 0);
    var mul = (c.fight ? (c.regenFight != null ? c.regenFight : 0) : 1) * (inSafe() ? (c.regenSafe || 1) : 1);
    if (st && st.gloveCode === 'BERSERK_GLOVES') mul = 0; // [2026-10-28] 광전사의 장갑 -- 자연 회복 없음
    return Math.min(c.hpMax, Math.floor(c.hp + Math.max(0, tSrv - from) * c.hpMax * mul / (c.fullMs || 60000)));
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
    el.hp.title = (rb ? 'Lv.' + c.lv + '(환생' + rb + ') ' : '') + '체력 ' + h + ' / ' + c.hpMax + (lk ? ' (최대 체력 ' + lk + '% 잠김)' : '')
        + (ready ? ' -- 환생 가능! 왕도 12시 방향 성당 신부님께' : '');
    // [2026-10-28] 환생 꼬리표 -- 폭 112px 안에 들어가면 "Lv.1(환생1)", 넘치면 "Lv.1✦1", 그래도 넘치면(환생 뒤 체력이 7자리) 체력을 "1415k"로
    for (var fit = 0; fit < 3; fit++) {
      var hs = fit === 2 && h >= 100000 ? Math.floor(h / 1000) + 'k' : String(h);
      el.hp.innerHTML = '<span class="hp-l"><span>' + lvLabel(c, fit > 0) + '</span><span class="hp-n">' + (lk ? '🔒' : '❤') + hs + '</span></span>' // [2026-10-28] 짧게
          + '<span class="mine-bar hp"><i style="width:' + pct + '%"></i>' + (lk ? '<b style="width:' + lk + '%"></b>' : '') + '</span><span class="mine-bar ex' + (ready ? ' ready' : '') + '"><i style="width:' + ep + '%"></i></span>'
          + (ready ? '<span class="hp-rb">✨ 환생 가능!</span>' : '');
      var hl = el.hp.firstChild;
      if (!rb || !hl || hl.scrollWidth <= hl.clientWidth + 1) break;
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
  function minable(x, y) {
    var c = cellAt(x, y), k = x + ',' + y;
    return 'VTYPH~'.indexOf(c) < 0 && WALK.indexOf(c) >= 0 && !dug[k] && !noOre[k];
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
    return (st && st.helmet || 0) - (map.helmetNeed || 0);
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
    daykeeper: '#ffe14d', ninjaquest: '#ff4a4a', boatwright: '#c8964a', ferry: '#3a8aff', chest: '#ffd23f', smelter: '#ff8a3a', gemcutter: '#3ad8d8', artisan: '#c84a4a' };
  function drawMini() {
    var v = miniView(), ox = v.x0, oy = v.y0; // [2026-10-28] 긴 맵은 플레이어 주변 창만
    var k = Math.max(1, Math.round(v.w / 128)); // 큰 맵은 점을 크게
    miniCtx.clearRect(0, 0, v.w, v.h);
    if (v.x0 < 0 || v.y0 < 0) { miniCtx.fillStyle = '#0b0912'; miniCtx.fillRect(0, 0, v.w, v.h); } // 레터박스 띠
    if (map.noMap) { // [2026-10-28] 개미굴 -- 지도가 안 보이는 곳(내 위치도, 길도)
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
      if (p.kind === 'Z') return; // [2026-10-24] 숨은 입구(바닷길)는 미니맵에도 안 찍는다
      miniCtx.fillStyle = p.locked ? '#9a9a9a' : '#ffd23f';
      miniCtx.fillRect(p.x - ox - 2 * k, p.y - oy - 2 * k, 4 * k + 1, 4 * k + 1);
    });
    // [2026-10-28] "미니맵에 NPC 색 표시" -- NPC마다 색 점(테두리 흰색). 대장장이 주황, 상점 보라, 신부 흰색, 안내원 초록, 그 밖 하늘색
    (map.npcs || []).forEach(function (n) {
      var col = NPC_MINI_COL[n.kind] || '#5ad8ff';
      miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(n.x - ox - 2 * k - 1, n.y - oy - 2 * k - 1, 4 * k + 3, 4 * k + 3);
      miniCtx.fillStyle = col; miniCtx.fillRect(n.x - ox - 2 * k, n.y - oy - 2 * k, 4 * k + 1, 4 * k + 1);
    });
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
    var h = '<div class="mine-log-head">🎁 최근 획득·구매</div>';
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
    // [2026-10-24] 몬스터가 서 있는 칸(지나갈 수 없음, 공격 대상) + ⛏️/⚔️ 버튼 모양 + 체력 표시
    mobOcc = {};
    if (map.mobs) {
      var tS = nowSrv();
      for (var mi = 0; mi < map.mobs.length; mi++) {
        var mm = map.mobs[mi], mp = mobPos(mm, tS);
        if (mp) { mm._p = mp; mobOcc[mp.cx + ',' + mp.cy] = mm; } else mm._p = null;
      }
    }
    // [2026-10-28] "무기로 공격 버튼과 채광 버튼 분리" -- ⛏️는 캐기만, ⚔️는 바로 옆 몬스터가 있을 때 또렷해진다
    var near = !P.moving && adjMob();
    if (el.atk && el.atk.classList.contains('near') !== !!near) el.atk.classList.toggle('near', !!near);
    // [2026-10-28] "광석 캐기가 불가능한 지역에서는 캐기 버튼을 없애도" -- 캘 칸 없는 맵은 ⛏️와 자동채광 숨김(⚔️는 그대로)
    var digShow = map.noDig ? 'none' : '';
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
      tapDir = null;
      if (smithOpen) d = null; // 대장간 창이 열려 있는 동안은 움직이지 않는다
      if (d && frozenMe()) { // [2026-10-28] 얼음 여왕 빙결 -- 못 움직인다
        if (now - lastBumpT > 2500 || lastBump !== 'ice') msg('🧊 얼어붙어서 움직일 수 없어요! 동료가 곁(2칸)에 오면 풀려요.');
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
        if (walk(nx, ny)) {
          P.fx = P.x; P.fy = P.y; P.tx = nx; P.ty = ny; P.t0 = now; P.moving = true; P.step = P.step === 1 ? 2 : 1;
        } else if (PORTALS.indexOf(cellAt(nx, ny)) >= 0) {
          enterPortal(nx, ny, now);
        } else if (cellAt(nx, ny) === 'N') {
          talk(npcAt[nx + ',' + ny], now);
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
    if (n.kind === 'smith') openSmith(n, 'menu');
    else if (n.kind === 'captain') openSmith(n, 'captain'); // [2026-10-24] 선장 퀘스트 창
    else if (n.kind === 'boutique') openSmith(n, 'boutique'); // [2026-10-24] 명품상점
    else if (n.kind === 'priest') { priestConfirm = false; openSmith(n, 'priest'); } // [2026-10-24] 왕도 성당 신부 ([2026-10-28] 환생 확인 창은 닫힌 채로)
    else if (n.kind === 'guide') { setAuto(false); openSmith(n, 'guide'); } // [2026-10-25] 이동 안내원
    else if (n.kind === 'blackmarket' || n.kind === 'furrier') { setAuto(false); openCoat(n); } // [2026-10-26] 방한 외투
    else if (n.kind === 'daykeeper') { setAuto(false); openSmith(n, 'daygate'); } // [2026-10-28] 요일 동굴 문지기
    else if (n.kind === 'ninjaquest' || n.kind === 'boatwright' || n.kind === 'ferry') { setAuto(false); openSmith(n, 'archi'); } // [2026-10-28] 다도해
    else if (n.kind === 'chest' || n.kind === 'antchest') openChest(n); // [2026-10-28] 피라미드 보상 상자, 개미굴 보물 상자
    else if (n.kind === 'smelter' || n.kind === 'gemcutter' || n.kind === 'artisan') { setAuto(false); openSmith(n, n.kind); } // [2026-10-28] 광석 전문가 / 보석 세공사 / 왕도 장인
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
  var GEAR_PARTS = [['ring', '💍 반지', '하나만 낄 수 있어요'], ['necklace', '📿 목걸이', '가지고 있으면 바로 적용'], ['boots', '👢 장화', '가지고 있으면 바로 적용'], ['tool', '⛏️ 곡괭이', '하나만 낄 수 있어요'], ['gloves', '🧤 장갑', '왕도 대장장이 · 하나만 낄 수 있어요']]; // [2026-10-28] 재생/광전사의 장갑
  function gearCard(g) {
    var worn = g.kind === 'ring' || g.kind === 'gloves' ? g.worn && g.tier > 0 : g.kind === 'tool' ? !!g.on : g.tier > 0;
    var h = '<div class="cos-card gear-card' + (worn ? ' worn' : '') + '"><div class="gear-ico">' + (GEAR_ICON[g.kind] || '💍') + '</div>'
        + '<div class="cos-nm">' + g.name + '</div><small class="gear-tier">' + (g.tier > 0 ? g.tier + '단계' + (g.maxTier ? ' / ' + g.maxTier : '') : '없음') + '</small>';
    if (g.effect) h += '<small class="gear-eff">지금: ' + g.effect + '</small>';
    if (g.dim) h += '<small class="gear-eff" style="color:#9aa">🌑 빛을 잃음(맵 리셋 때 회복)</small>';
    if ((g.kind === 'ring' || g.kind === 'tool' || g.kind === 'gloves') && g.tier > 0)
      h += worn ? '<span class="mine-worn">' + (g.kind === 'tool' ? '사용 중' : '착용 중') + '</span>' + (g.kind === 'tool' || g.kind === 'gloves' ? '<button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + g.code + '\')">빼기</button>' : '')
          : '<button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + g.code + '\')">끼기</button>';
    if (!g.nextTier) return h + '<small class="gear-eff">최고 단계예요.</small></div>';
    var away = g.cityOnly && map.id !== 'city'; // [2026-10-25] 높은 단계는 왕도 대장장이만(곡괭이는 어디서나)
    h += '<small class="gear-next">' + (g.cityOnly ? '🏰 ' : '') + g.nextTier + '단계: ' + g.nextEffect + '</small>' + costHtml(g.costs);
    return h + (away ? '<span class="mine-worn" style="color:#ffd27a">왕도 대장장이</span>'
        : '<button type="button" ' + (g.can ? '' : 'disabled ') + 'onclick="TowerMine.craft(\'' + g.code + '\')">' + (g.tier > 0 ? '올리기' : '만들기') + '</button>') + '</div>';
  }
  function gearHtml() {
    var h = '';
    GEAR_PARTS.forEach(function (p) {
      var list = (st.gear || []).filter(function (g) { return g.kind === p[0]; });
      if (!list.length) return;
      h += '<div class="cos-slot"><div class="cos-slot-h">' + p[1] + ' <small>' + p[2] + '</small></div><div class="cos-car" data-slot="g-' + p[0] + '">'
          + list.map(gearCard).join('') + '</div></div>';
    });
    return h;
  }
  function synthHtml() {
    var stones = [];
    var dayNeed = st.sevenDayNeed || 30; // [2026-10-28] 요일석 종류마다 30개(서버 MINE_SEVEN_DAY_STONES)
    for (var g = 11; g <= 17; g++) stones.push({ grade: g, name: ORE_NAME[g], have: oreHave(g), need: dayNeed });
    stones.push({ grade: 6, name: ORE_NAME[6], have: oreHave(6), need: 1 }); // [2026-10-25] 칠요석 하나에 별빛석 1개
    var can = (st.sevenMax || 0) > 0 ? '' : 'disabled ';
    return '<div class="mine-smith-say">요일석 7종(월요석~일요석)을 ' + dayNeed + '개씩, 거기에 별빛석 하나를 더 가져오면 별빛으로 묶어 칠요석 하나로 합쳐 주지.</div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(18) + '" alt=""><span class="nm">칠요석<small>보유 ' + oreHave(18) + '개 · 지금 ' + (st.sevenMax || 0) + '개 만들 수 있어요</small>'
        + costHtml(stones) + '</span>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(1)">1개</button>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(9999)">최대</button></div>'
        + copperHtml();
  }
  // [2026-10-28] "구리 광석 사용처를 늘려줘" -- 구리 정련(구리 20 -> 철광석 1), 오늘의 구리 납품(하루 1번 구리 100 -> 오늘 요일석 3 + 곡괭이 내구도 +50)
  function copperHtml() {
    var cu = oreHave(1), canR = cu >= 20 ? '' : 'disabled ', canD = cu >= 100 && !st.deliverDone ? '' : 'disabled ';
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
          + '<div class="mine-smith-row"><span class="nm">🌊 용왕의 반지<small>가지고 있으면 물 위를 걸을 수 있어요</small>' + costHtml(d.costs) + '</span>'
          + '<button type="button" ' + (d.can ? '' : 'disabled ') + 'onclick="TowerMine.captain(\'craft\')">만들어 주세요</button></div>';
    }
    return h + talkBlock(n, captainSay || (n ? n.line : ''))
        + '<div class="mine-smith-row"><img src="' + oreIcon(4) + '" alt=""><span class="nm">금광석 ' + d.goldNeed + '개 주기<small>보유 ' + (d.gold || 0) + '개</small></span>'
        + '<button type="button" ' + ((d.gold || 0) >= d.goldNeed ? '' : 'disabled ') + 'onclick="TowerMine.captain(\'give\')">주기</button></div>';
  }
  // [2026-10-24] 배 -- 다른 항구를 고르면 그곳 부두로
  function shipHtml() {
    return npcHead('⛵ 배 타기', false) + '<div class="mine-smith-say">어디로 갈까요? (뱃삯 없음)</div><div class="mine-smith-menu">'
        + (map.shipTo || []).filter(function (s) { return !s.admin || (st && st.adminMaps); }) // [2026-10-28] 다도해는 운영자만
          .map(function (s) { return '<button type="button" onclick="TowerMine.sail(\'' + s.id + '\')">⛵ ' + s.name + (s.admin ? ' 🏯' : '') + '</button>'; }).join('') + '</div>';
  }
  function sail(dest) {
    var n = smithNpc;
    closeSmith();
    if (!n || busy) return;
    travel('/api/tower-mine-warp?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&px=' + n.px + '&py=' + n.py + '&dest=' + encodeURIComponent(dest) + verQ(), '배를 타지 못했어요.');
  }
  // [2026-10-24] 명품상점 -- 부위별 외형(능력치 없음) + 성별 전환
  var LOOK_SLOT_NAME = { hat: '🎩 모자', hair: '💇 머리', top: '👘 상의', bottom: '👖 하의', shoes: '👞 신발' };
  // [2026-10-24] "치장 아이템을 착용했을 때 모습을 이미지로" -- 목록마다 그 아이템을 입은 내 캐릭터, 누르면 위에 크게 입어보기(앞/옆/뒤)
  var lookImgCache = {}, tryLook = null, tryName = '';
  function lookImg(lk, dir) {
    var key = JSON.stringify(lk) + dir;
    if (!lookImgCache[key]) lookImgCache[key] = charSprite(dir, 0, 0, lk).toDataURL();
    return lookImgCache[key];
  }
  function lookWith(slot, look) {
    var lk = {}, cur = st.look || {};
    for (var k in cur) lk[k] = cur[k];
    lk[slot] = look;
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
    ['hat', 'hair', 'top', 'bottom', 'shoes'].forEach(function (slot) {
      var cur = lk[slot + 'Code'] || '', list = (st.cosShop || []).filter(function (c) { return c.slot === slot; });
      var owned = list.filter(function (c) { return c.owned; }).length;
      h += '<div class="cos-slot"><div class="cos-slot-h">' + LOOK_SLOT_NAME[slot] + ' <small>' + owned + '/' + list.length + ' 보유</small>'
          + (cur ? ' <button type="button" class="mine-mini-btn" onclick="TowerMine.lookAct(\'wear\', \'\', \'' + slot + '\')">기본으로</button>' : ' <span class="mine-worn">기본 착용 중</span>') + '</div>'
          + '<div class="cos-car" data-slot="' + slot + '">';
      list.forEach(function (c) {
        var btn;
        if (c.code === cur) btn = '<span class="mine-worn">착용 중</span>';
        else if (c.owned) btn = '<button type="button" onclick="TowerMine.lookAct(\'wear\', \'' + c.code + '\')">입기</button>';
        else btn = '<button type="button" ' + (c.can ? '' : 'disabled ') + 'onclick="TowerMine.lookAct(\'buy\', \'' + c.code + '\')">구매</button>';
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
    { id: 'snowcave', name: '설산 동굴 💠', x: 262, y: -124, fill: '#4e6a84', ink: '#e8f8ff' }, // [2026-10-28] 설산 마을 동문 // [2026-10-28] 마을은 전체 지도에 레벨 표시 안 함
    { id: 'snow3', name: '설산 3층', x: 262, y: -68, fill: '#c8d8ea', ink: '#1a2a3a' },
    { id: 'snow2', name: '설산 2층', x: 262, y: -12, fill: '#d6e2f0', ink: '#1a2a3a' },
    { id: 'snow1', name: '설산 1층', x: 262, y: 44, fill: '#e2ebf5', ink: '#1a2a3a' },
    { id: 'ruins', name: '성당 옛터 👿', x: 160, y: -110, fill: '#5a4a6a', ink: '#f0e0ff' }, // [2026-10-28] 왕도 가는 길 북쪽 샛길
    { id: 'royalroad', name: '왕도 북쪽 샛길', x: 160, y: -40, fill: '#b8a27a', ink: '#2a1d10' }, // [2026-10-27] 설산 마을 서문 -> 왕도 동문
    { id: 'city', name: '왕도 아스테리아', x: 160, y: 28, fill: '#c8a46a', ink: '#2a1d10', town: true }, // [2026-10-27] 일반 플레이어에게 열림, [2026-10-28] 레벨 표시 없음
    { id: 'steppe', name: '왕도 남쪽 초원', x: 160, y: 100, fill: '#8fbf5a', ink: '#10300a' },
    { id: 'cave3', name: '광산 동굴 3층', x: 52, y: 52, fill: '#2a2230', ink: '#e8d8ff' },
    { id: 'north', name: '북쪽 끝 설원', x: 262, y: 100, fill: '#eef3f8', ink: '#2a3440' },
    { id: 'desert', name: '북쪽 사막', x: 160, y: 172, fill: '#e3c47a', ink: '#3b2a10' },
    { id: 'antnest', name: '개미굴 🐜', x: -42, y: 172, fill: '#4a2a1a', ink: '#ffd8b0', w: 76 }, // [2026-10-28] 사막 서쪽에서 빠짐
    { id: 'pyramid', name: '피라미드 📦', x: -42, y: 242, fill: '#d8b060', ink: '#3b2a10', w: 76 }, // 개미굴을 빠져나오면
    { id: 'cave2', name: '광산 동굴 2층', x: 52, y: 122, fill: '#4a3d33', ink: '#fff' },
    { id: 'cave1', name: '광산 동굴 1층', x: 52, y: 192, fill: '#6b5b4b', ink: '#fff' },
    { id: 'dungeon', name: '요일 던전', x: 52, y: 252, fill: '#5b4d40', ink: '#fff' },
    { id: 'field', name: '중앙 평원 🏠', x: 160, y: 252, fill: '#5fae45', ink: '#10300a' },
    { id: 'castle', name: '동쪽 성곽', x: 258, y: 252, fill: '#8a857c', ink: '#fff' },
    { id: 'beach', name: '남쪽 바닷가', x: 160, y: 344, fill: '#ecd9a0', ink: '#3b2a10' },
    { id: 'island', name: '남쪽 섬', x: 60, y: 422, fill: '#3fa36b', ink: '#fff' },
    { id: 'archi', name: '다도해 🏯', x: 252, y: 422, fill: '#2f8a8a', ink: '#fff' }, // [2026-10-28] 다도해 -- 본섬은 모두에게(신궁/뱃길/일곱 섬은 아직 운영자만)
    // [2026-10-28] 다도해 안쪽(운영자만, 지도 아래로 늘려서) -- 관리자가 지역을 눌러 바로 이동할 수 있게
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
      nodes: [['@desert', 160, 30, 'desert', '🏜️ 사막·왕도 ▲'], ['cave3', 52, 40], ['cave2', 52, 100], ['cave1', 52, 160], ['field', 160, 180], ['castle', 268, 180],
        ['dungeon', 52, 240], ['beach', 160, 270], ['@sea', 92, 350, 'sea', '🏝️ 남쪽 바다 ⛵'], ['@snow', 240, 350, 'snow', '❄️ 설원 ⛵']],
      links: [['field', '@desert'], ['field', 'cave1'], ['cave1', 'cave2'], ['cave2', 'cave3'], ['field', 'dungeon'], ['field', 'castle'], ['field', 'beach'], ['beach', '@sea', 'sea'], ['beach', '@snow', 'sea']] },
    { id: 'desert', name: '🏜️ 사막·왕도', vh: 410,
      nodes: [['ruins', 160, 30], ['royalroad', 160, 100], ['@snowtown', 268, 100, 'snow', '❄️ 설산 마을 ▶'], ['city', 160, 170], ['steppe', 160, 240], ['desert', 160, 310],
        ['antnest', 44, 310], ['pyramid', 44, 380], ['@plain', 172, 380, 'plain', '🌾 평원 ▼']],
      links: [['royalroad', 'ruins'], ['royalroad', '@snowtown'], ['royalroad', 'city'], ['city', 'steppe'], ['steppe', 'desert'], ['desert', 'antnest', 'fall', '🕳️'], ['antnest', 'pyramid'], ['desert', '@plain']] },
    { id: 'snow', name: '❄️ 설원·설산', vh: 420,
      nodes: [['snowpeak', 160, 30], ['snowroad', 160, 90], ['snowtown', 160, 150], ['@royal', 46, 150, 'desert', '🏰 왕도 ◀'], ['snowcave', 272, 150], ['snow3', 160, 210], ['snow2', 160, 270], ['snow1', 160, 330],
        ['north', 160, 390], ['@beach2', 276, 390, 'plain', '⛵ 바닷가']],
      links: [['snowroad', 'snowpeak'], ['snowtown', 'snowroad'], ['snowtown', '@royal'], ['snowtown', 'snowcave'], ['snow3', 'snowtown'], ['snow2', 'snow3'], ['snow1', 'snow2'], ['north', 'snow1'], ['north', '@beach2', 'sea']] },
    { id: 'sea', name: '🏝️ 남쪽 바다', vh: 380,
      nodes: [['@beach', 160, 28, 'plain', '⛵ 남쪽 바닷가'], ['island', 60, 92], ['archi', 252, 92], ['shrine', 52, 160], ['shrine2', 160, 160], ['shrine3', 268, 160],
        ['shrineboss', 106, 220], ['archisea', 214, 220], ['isle1', 44, 295], ['isle2', 120, 295], ['isle3', 196, 295], ['isle4', 272, 295],
        ['isle5', 82, 345], ['isle6', 160, 345], ['isle7', 238, 345]],
      links: [['@beach', 'island', 'sea'], ['island', 'archi', 'sea', '⛵'], ['archi', 'shrine'], ['shrine', 'shrine2'], ['shrine2', 'shrine3'], ['archi', 'shrineboss'], ['archi', 'archisea', 'sea', '⛵']] }
  ];
  var worldTab = null;
  function regionOf(id) {
    for (var i = 0; i < WORLD_REGIONS.length; i++) if (WORLD_REGIONS[i].nodes.some(function (n) { return n[0] === id; })) return WORLD_REGIONS[i].id;
    return map && map.archi ? 'sea' : 'plain';
  }
  function worldTabGo(t) { worldTab = t; if (smithOpen) renderSmith(smithNpc); }
  function worldHtml() {
    if (!worldTab) worldTab = regionOf(map && map.id);
    var R = WORLD_REGIONS.filter(function (r) { return r.id === worldTab; })[0] || WORLD_REGIONS[0];
    var info = {}, W = 96, H = 32, lv = myLv(), adminW = !!(st && st.adminMaps);
    WORLD_NODES.forEach(function (n) { info[n.id] = n; });
    var N = {};
    R.nodes.forEach(function (e) {
      if (e[0].charAt(0) === '@') { N[e[0]] = { id: e[0], x: e[1], y: e[2], go: e[3], name: e[4], exit: true }; return; }
      var n = info[e[0]];
      if (!n || n.locked || n.dev) return;
      N[e[0]] = { id: n.id, x: e[1], y: e[2], name: n.name, fill: n.fill, ink: n.ink, w: n.w, town: n.town };
    });
    var STY = { walk: 'stroke="#f4f0e6" stroke-width="3"', sea: 'stroke="#6fc8f0" stroke-width="3" stroke-dasharray="7 5"', fall: 'stroke="#c8a070" stroke-width="3" stroke-dasharray="3 4"' };
    var tabs = '<div class="eq-tabs world-tabs">' + WORLD_REGIONS.map(function (r) {
      return '<button type="button" class="' + (r.id === R.id ? 'on' : '') + '" onclick="TowerMine.worldTab(\'' + r.id + '\')">' + r.name + '</button>';
    }).join('') + '</div>';
    var svg = '<svg viewBox="0 0 320 ' + R.vh + '" class="mine-world" role="img" aria-label="광산 전체 지도 -- ' + R.name + '">'
        + '<rect x="0" y="0" width="320" height="' + R.vh + '" fill="#1d1730"/>';
    R.links.forEach(function (l) {
      var p = N[l[0]], q = N[l[1]];
      if (!p || !q) return;
      svg += '<line x1="' + p.x + '" y1="' + p.y + '" x2="' + q.x + '" y2="' + q.y + '" ' + STY[l[2] || 'walk'] + '/>'
          + (l[3] ? '<text x="' + ((p.x + q.x) / 2 + 6) + '" y="' + ((p.y + q.y) / 2 + 4) + '" class="wl">' + l[3] + '</text>' : '');
    });
    // [2026-10-28] 보스 맵 -- 지금 있으면 "👑 출현 중", 이번 주기에 잡혔으면 다음 리셋 시각(보스는 맵 리셋 때마다 한 번)
    var bossOf = {};
    ((st && st.bosses) || []).forEach(function (b) { bossOf[b.map] = b; });
    var hhmm = function (ms) { var d = new Date(ms), p = function (v) { return (v < 10 ? '0' : '') + v; }; return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()); };
    Object.keys(N).forEach(function (k) {
      var n = N[k], nw = n.w || W;
      if (n.exit) { // 다른 구간으로 -- 누르면 그 탭
        svg += '<g style="cursor:pointer" onclick="TowerMine.worldTab(\'' + n.go + '\')"><rect x="' + (n.x - nw / 2) + '" y="' + (n.y - H / 2) + '" width="' + nw + '" height="' + H + '" fill="#2a2440" stroke="#b8b0c8" stroke-width="2" stroke-dasharray="5 4"/>'
            + '<text x="' + n.x + '" y="' + (n.y + 5) + '" text-anchor="middle" class="wn" fill="#e8e0ff">' + n.name + '</text></g>';
        return;
      }
      var here = map && (map.id === n.id || (n.id === 'archi' && map.archi && !N[map.id] && regionOf(map.id) === 'sea'));
      if (bossOf[n.id]) {
        var b = bossOf[n.id], by = n.y + H / 2 + 25, bx = Math.max(4, n.x - nw / 2 + 2);
        svg += b.alive ? '<text x="' + bx + '" y="' + by + '" class="wl" style="fill:#ff6a6a;font-weight:800">👑 보스 출현 중</text>'
            : '<text x="' + bx + '" y="' + by + '" class="wl" style="fill:#b8b0c8">💤 보스 ' + hhmm(b.nextAt) + '</text>';
      }
      // [2026-10-28] 관리자 -- 칸을 누르면 그 맵 시작점으로(서버도 관리자만)
      svg += (adminW ? '<g style="cursor:pointer" onclick="TowerMine.worldWarp(\'' + n.id + '\')">' : '')
          + '<rect x="' + (n.x - nw / 2) + '" y="' + (n.y - H / 2) + '" width="' + nw + '" height="' + H + '" fill="' + n.fill + '"'
          + ' stroke="' + (here ? '#ffe14d' : '#140d0a') + '" stroke-width="' + (here ? 4 : 2) + '"/>'
          + '<text x="' + n.x + '" y="' + (n.y + 5) + '" text-anchor="middle" class="wn" fill="' + n.ink + '">' + n.name + '</text>'
          + (adminW ? '</g>' : '')
          + (here ? '<text x="' + n.x + '" y="' + (n.y - H / 2 - 4) + '" text-anchor="middle" class="wh">📍 여기</text>' : '')
          + (!n.town && minLvOf(n.id) > 1 ? '<text x="' + n.x + '" y="' + (n.y + H / 2 + 12) + '" text-anchor="middle" class="wl"' + (minLvOf(n.id) > lv ? ' style="fill:#ff8a8a"' : '') + '>권장 Lv.' + minLvOf(n.id) + '</text>' : '');
    });
    svg += '</svg>';
    if (adminW) svg += '<div class="mine-smith-say">🛠️ 관리자: 지역을 누르면 그 맵 시작점으로 바로 이동해요. '
        + '<button type="button" class="mine-mini-btn" onclick="TowerMine.openWarpForm()">좌표 입력</button></div>';
    return npcHead('🗺️ 전체 지도', false) + tabs + svg;
  }
  /** [2026-10-28] 관리자 -- 전체 지도에서 누른 지역(맵) 시작점으로 이동(서버 mineAdminWarp가 관리자인지 다시 확인). */
  function worldWarp(id) {
    if (!st || !st.adminMaps || busy) return;
    var e = st.adminMaps.filter(function (m) { return m.id === id; })[0];
    if (!e) { msg('이동할 수 없는 곳이에요.'); return; }
    closeSmith();
    setAuto(false);
    travel('/api/tower-mine-admin-warp?mapId=' + encodeURIComponent(id) + '&x=' + (e.sx != null ? e.sx : 0) + '&y=' + (e.sy != null ? e.sy : 0), '이동하지 못했어요.');
  }
  // [2026-10-28] 🎒 장비 -- "반지류 하나, 옷류 하나, 장갑류 하나": 칸마다 가진 장비를 보여 주고 끼기/빼기(같은 칸의 다른 장비는 저절로 빠진다).
  var EQ_SLOTS = [
    { id: 'ring', name: '💍 반지', hint: '하나만 낄 수 있어요', none: '반지가 없어요. 대장장이에게서 풍요/심연의 반지를, 바닷가 늙은 선장에게서 용왕의 반지를 얻을 수 있어요.' },
    { id: 'coat', name: '🧥 옷', hint: '하나만 입을 수 있어요', none: '방한 외투가 없어요. 설산 마을 털옷 장인이나 설원 암상인에게서 살 수 있어요.' },
    { id: 'gloves', name: '🧤 장갑', hint: '', none: '장갑이 없어요. 설산 마을 털옷 장인의 의뢰(빙석)로 받을 수 있어요.' },
    { id: 'tool', name: '⛏️ 곡괭이', hint: '하나만 낄 수 있어요(빼면 기본 곡괭이)', none: '특수 곡괭이가 없어요. 대장장이에게서 광역/탐지/쌍날 곡괭이를 만들 수 있어요.' } // [2026-10-28] 곡괭이도 장비 창에서
  ];
  // [2026-10-28] "장비 버튼 -> 캐러셀, 장착 탭과 치장 탭으로 나눠서 둘 다 조정" -- 장착: 반지/옷/장갑 줄, 치장: 가진 치장만 부위별 줄(입기/기본으로)
  var equipTab = 'gear';
  function equipTabGo(t) { equipTab = t; if (smithOpen) renderSmith(smithNpc); }
  function equipHtml() {
    var h = npcHead('🎒 내 장비', false)
        + '<div class="eq-tabs"><button type="button" class="' + (equipTab === 'gear' ? 'on' : '') + '" onclick="TowerMine.equipTab(\'gear\')">⚔️ 장착</button>'
        + '<button type="button" class="' + (equipTab === 'look' ? 'on' : '') + '" onclick="TowerMine.equipTab(\'look\')">👗 치장</button></div>';
    if (equipTab === 'look') {
      var lk = st.look || {};
      h += '<div class="mine-look-top"><div class="mine-look-prevs">' + ['down', 'right', 'up'].map(function (d) { return '<img class="mine-look-prev" src="' + lookImg(lk, d) + '" alt="">'; }).join('') + '</div>'
          + '<div class="mine-smith-say">가지고 있는 치장만 보여요. 새 치장은 명품상점에서 살 수 있어요.</div></div>';
      ['hat', 'hair', 'top', 'bottom', 'shoes'].forEach(function (slot) {
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
    EQ_SLOTS.forEach(function (sl) {
      var rows = (st.equip || []).filter(function (e) { return e.slot === sl.id; });
      h += '<div class="cos-slot"><div class="cos-slot-h">' + sl.name + (sl.hint ? ' <small>' + sl.hint + '</small>' : '') + '</div>';
      if (!rows.length) { h += '<div class="mine-eq-empty">' + sl.none + '</div></div>'; return; }
      h += '<div class="cos-car" data-slot="e-' + sl.id + '">' + rows.map(function (e) {
        return '<div class="cos-card gear-card' + (e.worn ? ' worn' : '') + '" data-code="' + e.code + '" onclick="TowerMine.eqInfo(this, event)"><div class="gear-ico">' + sl.name.split(' ')[0] + '</div><div class="cos-nm">' + e.name + '</div><small class="gear-eff">' + e.effect + '</small>'
            + (e.worn ? '<span class="mine-worn">착용 중</span><button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + e.code + '\')">빼기</button>'
                : '<button type="button" onclick="TowerMine.equipRing(\'' + e.code + '\')">' + (sl.id === 'coat' ? '입기' : '끼기') + '</button>') + '</div>';
      }).join('') + '</div></div>';
    });
    return h + '<div class="mine-smith-say">장비를 누르면 설명이 나와요.</div>'; // [2026-10-28] 방한 장비 안내는 카드 설명으로
  }
  // [2026-10-28] "장비를 누르면 설명" -- 카드(버튼 말고)를 누르면 효과와 주의점을 안내
  var EQ_TIPS = { COAT1: '설산의 한기를 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    COAT2: '설산의 한기를 크게 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    GLOVES: '한기 방어 +1(외투와 함께 적용, 받는 피해는 그대로). 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    DRAGON_RING: '끼고 있는 동안 물 위를 땅처럼 걸을 수 있어요. 반지는 하나만 낄 수 있어요(물 위에서는 못 빼요).',
    REGEN_GLOVES: '몬스터에게 받은 피해의 80%를 4초에 걸쳐 되찾아요(전투 중에도). 장갑은 하나만 -- 끼면 방한 장갑은 벗어요.',
    BERSERK_GLOVES: '공격력이 50% 올라가지만 체력이 저절로 차지 않아요(성당 신부의 회복이나 쓰러진 뒤 깨어날 때만). 장갑은 하나만.' };
  function eqInfo(elm, ev) {
    if (ev && ev.target && ev.target.tagName === 'BUTTON') return; // 끼기/빼기 버튼은 그대로
    var code = elm.getAttribute('data-code'), e = null;
    (st.equip || []).forEach(function (q) { if (q.code === code) e = q; });
    if (!e) return;
    msg('ℹ️ ' + e.name + ' -- ' + (EQ_TIPS[code] || e.effect), 4200);
  }
  function openEquip() {
    if (!opened || !map) return;
    openSmith({ name: '내 장비', line: '', kind: 'equip' }, 'equip');
  }
  // [2026-10-28] 다도해 NPC -- 신궁 수문장(수색 임무: 닌자 100명 -> 비급서 + 달빛석), 배 목수(달빛석 -> 1인용 배), 뱃사공(배 타기 -> 뱃길)
  function archiHtml(n) {
    if (!st.archi) return npcHead(n.kind === 'ninjaquest' ? '🥷 신궁 수문장' : n.kind === 'boatwright' ? '🔨 배 목수' : '⛵ 뱃사공', false) + talkBlock(n, n.line)
        + '<div class="mine-smith-say">🚧 아직 준비 중이에요. 곧 열려요!</div>'; // [2026-10-28] 본섬만 열림 -- 신궁 임무/배/뱃길은 아직 운영자만
    var a = st.archi || {}, h = npcHead(n.kind === 'ninjaquest' ? '🥷 신궁 수문장' : n.kind === 'boatwright' ? '🔨 배 목수' : '⛵ 뱃사공', false) + talkBlock(n, n.line);
    var have = '<div class="mine-smith-say">🌙 달빛석 ' + (a.moon || 0) + '개 · 📜 비급서 ' + (a.books || 0) + '권 · ⛵ 1인용 배 ' + (a.boat ? '있음' : '없음') + (a.key ? ' · 🗝️ 신궁 열쇠' : '') + '</div>';
    if (n.kind === 'ninjaquest') { // [2026-10-28] 수색 임무 보상 = 닌자 두목의 방 열쇠(비급서/달빛석은 두목 첫 처치 보상)
      var boss = '<div class="mine-smith-say">🗝️ 신궁 열쇠가 있으면 동쪽 문(닌자 두목의 방)에 들어갈 수 있어요. 두목은 은신과 분신술을 써요 -- 다섯 중 <b>자세가 다른 하나</b>가 진짜!'
          + (a.bossReward ? ' (첫 처치 보상은 이미 받았어요)' : ' 처음 쓰러뜨리면 📜 비급서 + 🌙 달빛석 확정!') + '</div>';
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
          + '<button type="button" ' + (a.boatCan ? '' : 'disabled ') + 'onclick="TowerMine.archiAct(\'boat\')">만들기</button></div>';
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
    worldTab = regionOf(map.id); // [2026-10-28] 지금 있는 구간부터
    openSmith({ name: '전체 지도', line: '', kind: 'world' }, 'world');
  }
  // [2026-10-24] 왕도 -- 성당 신부(체력 회복 / 부활 지점), 텔레포트 게이트(금광석을 내고 다른 지역으로)
  // [2026-10-28] 환생 -- 99레벨 경험치 가득이면 신부 창의 "환생" 버튼이 켜지고, 누르면 효과 설명 + 확인(priestConfirm) 뒤에 서버로
  var priestConfirm = false, RB_MAX = 5;
  function rbMul(base, n) { var v = Math.pow(base, n); return v === Math.round(v) ? String(v) : String(Math.round(v * 100) / 100); }
  function priestHtml(n) {
    var city = !!(st && st.respawnCity), c = (st && st.combat) || {}, rb = c.rebirth | 0, ready = !!c.rebirthReady;
    var line = ready ? '때가 되었군요. 수많은 고비를 넘은 영혼이여, 원한다면 여신의 이름으로 새 날개를 달아 다시 태어나게 해 드리지요.' : (n ? n.line : '');
    var rbSub = rb >= RB_MAX ? '환생 ' + RB_MAX + '단계 완료' : ready ? '지금 환생할 수 있어요!' : 'Lv.99 + 경험치 가득일 때';
    var html = npcHead('⛪ ' + (n ? n.name : '성당 신부'), false)
        + talkBlock(n, line)
        + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.priestAct(\'heal\')">✨ 치료<small>체력 회복 (무료)</small></button>'
        + (city ? '<button type="button" onclick="TowerMine.priestAct(\'reset\')">🏠 부활 지점 해제<small>지금: 왕도 광장</small></button>'
                : '<button type="button" onclick="TowerMine.priestAct(\'save\')">🙏 기도<small>쓰러지면 왕도 광장에서 깨어나기</small></button>')
        + '<button type="button" ' + (ready ? 'class="rb-on" ' : 'disabled ') + 'onclick="TowerMine.priestAct(\'rebirthAsk\')">🕊️ 환생' + (rb ? ' (지금 환생' + rb + ')' : '') + '<small>' + rbSub + '</small></button></div>';
    // [2026-10-28] 환생 -- 1단계: 효과 설명, 2단계: 최종 확인(되돌릴 수 없음). "환생 시 멘트 조정" -- 좋아지는 것 / 어려워지는 것 / 그대로인 것으로 나눠서
    if (priestConfirm && ready) {
      var nrb = rb + 1;
      if (priestConfirm === 1) html += '<div class="mine-rb-box">🕊️ <b>환생 ' + nrb + '단계</b> -- 여신의 빛 속에서 지금까지의 수련을 내려놓고 새 날개로 다시 태어나요.<br>'
          + '<b>✨ 강해지는 것</b><br>'
          + '· 최대 체력 <b>x' + (1 + nrb) + '</b> · 공격력 <b>+' + (nrb * 50) + '%</b><br>'
          + '· 채굴·전투로 얻는 구리~미스릴 광석 <b>x' + (1 + nrb) + '</b><br>'
          + '· 한기·눈바람 피해 <b>-' + nrb + '%</b> · 다도해 햇빛 피해 <b>-' + Math.min(100, nrb * 20) + '%</b>' + (nrb >= 5 ? ' (햇빛을 완전히 막아요)' : '') + '<br>'
          + '· 등에 <b>환생 날개</b>가 돋아나요(단계가 오를수록 커져요, 최대 ' + RB_MAX + '단계)<br>'
          + '<b>⏳ 다시 쌓는 것</b><br>'
          + '· 레벨이 <b>Lv.1(환생' + nrb + ')</b>로 돌아가고 경험치는 0이 돼요<br>'
          + '· 레벨마다 필요한 경험치 <b>x' + rbMul(2, nrb) + '</b> (환생할 때마다 2배씩)<br>'
          + '<b>🎒 그대로인 것</b><br>'
          + '· 광석·보석·장비·치장은 모두 그대로예요.'
          + '<div class="mine-smith-menu"><button type="button" class="rb-on" onclick="TowerMine.priestAct(\'rebirthFinal\')">다음</button>'
          + '<button type="button" onclick="TowerMine.priestAct(\'rebirthNo\')">그만두기</button></div></div>';
      else html += '<div class="mine-rb-box">⚠️ <b>최종 확인</b><br>정말 <b>환생 ' + nrb + '단계</b>로 다시 태어날까요?<br>지금 <b>Lv.' + c.lv + '</b>가 <b>Lv.1</b>로 돌아가며, <b>되돌릴 수 없어요.</b>'
          + '<div class="mine-smith-menu"><button type="button" class="rb-on" onclick="TowerMine.priestAct(\'rebirth\')">🕊️ 최종 확인 -- 환생하기</button>'
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
      if (action === 'heal') flashT0 = performance.now();
      if (d.rebirthDone) { // [2026-10-28] 환생 -- 날개는 st.combat.rebirth로 그린다(외형도 같이 갱신)
        if (d.look) st.look = d.look;
        flashT0 = performance.now();
        banner = { text: '🕊️ 환생 ' + d.rebirth + '단계! Lv.1(환생' + d.rebirth + ')', t0: performance.now() };
      }
      msg(d.message || d.error || '완료', d.rebirthDone ? 5000 : 3000);
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function teleHtml() {
    var gold = 0;
    (st.ores || []).forEach(function (o) { if (o.grade === 4) gold = o.qty; });
    var need = map.teleGold != null ? map.teleGold : 5, can = gold >= need ? '' : 'disabled ';
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
  function coatHtml(n) {
    var bm = n && n.kind === 'blackmarket', d = coatData;
    var h = npcHead((bm ? '🕶️ ' : '🧥 ') + (n ? n.name : '털옷 장인'), false) + talkBlock(n, n ? n.line : '')
        + (bm ? '<div class="mine-smith-say" id="mineBlackLeft"></div>' : '');
    if (!d) return h + '<div class="mine-smith-say">물건을 꺼내는 중...</div>';
    var wear = d.coatDef === 1 ? '방한 외투' : d.coatDef === 2 ? '두꺼운 방한 외투' : '없음';
    h += '<div class="mine-smith-dur">🧥 입고 있는 외투 <b>' + wear + '</b></div>'; // [2026-10-28] 암상인 값이 더 비싼 건 비공개(문구 없음)
    return h + (d.coats || []).map(function (c) {
      return '<div class="mine-smith-row"><span class="nm">' + c.name + '<small>' + c.effect + (c.owned ? ' · 보유' : '') + '</small>' + (c.owned ? '' : costHtml(c.costs)) + '</span>'
          + (c.owned ? '<button type="button" onclick="TowerMine.coatAct(\'wear\',\'' + c.code + '\')">' + (c.worn ? '벗기' : '입기') + '</button>'
              : '<button type="button" ' + (c.can ? '' : 'disabled ') + 'onclick="TowerMine.coatAct(\'buy\',\'' + c.code + '\')">사기</button>') + '</div>';
    }).join('') + '<div class="mine-smith-say">한기 방어 1단계마다 한기가 1단계씩 약해져요(설원 1단계는 1단계 외투면 없음). 몸이 둔해져 몬스터에게 받는 피해는 2배, 눈바람은 막지 못해요.</div>'
      + (bm ? '' : gloveQuestHtml(d));
  }
  // [2026-10-28] 털옷 장인 의뢰 -- "빙석을 모아 오면 방한 장갑"(한기 방어 +1, 받는 피해 그대로)
  function gloveQuestHtml(d) {
    var q = d.gloveQuest || 0, h = '<div class="mine-smith-sub"><b>📜 의뢰: 손이 얼지 않는 장갑</b></div>';
    if (q >= 2 || d.hasGloves) return h + '<div class="mine-smith-say">🧤 방한 장갑을 받았어요. 한기 방어 +1 (외투와 함께 적용)</div>';
    if (q < 1) return h + '<div class="mine-smith-say">설산 얼음 속에 박힌 빙석을 모아 오면 장갑을 떠 주겠대요. (보상: 방한 장갑 -- 한기 방어 +1, 받는 피해는 그대로)</div>'
        + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.coatAct(\'quest\',\'\')">📜 의뢰 받기</button></div>';
    var ok = (d.iceHave || 0) >= (d.iceNeed || 30);
    return h + '<div class="mine-smith-row"><img src="' + oreIcon(19) + '" alt=""><span class="nm">빙석 ' + (d.iceHave || 0) + ' / ' + (d.iceNeed || 30)
        + '<small>설산 1~3층에서 캘 때, 설산 몬스터에게서</small></span>'
        + '<button type="button" ' + (ok ? '' : 'disabled ') + 'onclick="TowerMine.coatAct(\'gloves\',\'\')">건네기</button></div>';
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
    daykeeper: { skin: '#e8c09a', hair: '#e8e8e8', cloth: '#5a5560', clothD: '#46424c' } // [2026-10-28] 요일 동굴 문지기
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
  function talkBlock(n, text) {
    if (!n || !PORTRAIT[n.kind]) return '<div class="mine-smith-say">' + (text || '') + '</div>';
    return '<div class="mine-npc-talk"><img class="mine-portrait" id="minePortrait" src="' + portrait(n.kind, 0) + '" alt="' + escAttr(n.name) + '">'
        + '<div class="mine-npc-bubble"><b>' + escAttr(n.name) + '</b><span id="mineNpcLine" data-text="' + escAttr(text) + '"></span></div></div>';
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
    if (smithView === 'priest') {
      el.smith.innerHTML = priestHtml(n);
      return;
    }
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
          + '<button type="button" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',1)">1회</button>'
          + '<button type="button" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',9999)">최대</button></div>';
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
    return h + '<div class="mine-smith-row"><img src="' + oreIcon(hm.oreGrade) + '" alt=""><span class="nm">' + (hm.cityOnly ? '🏰 ' : '') + hm.oreName + ' ' + hm.need + '개 → ' + hm.nextTier + '단계'
        + '<small>보유 ' + hm.have + '개' + (hm.cityOnly ? ' · 동굴 3층까지 환하게' : '') + '</small>' + (hm.extra ? costHtml(hm.extra) : '') + '</span>'
        + (away ? '<span class="mine-worn" style="color:#ffd27a">왕도 대장장이</span>'
            : '<button type="button" ' + (ok ? '' : 'disabled ') + 'onclick="TowerMine.helmetUpgrade()">' + (hm.tier > 0 ? '업그레이드' : '만들기') + '</button>') + '</div>';
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
    ['gear', 'equip', 'gloveCode', 'archi', 'dragon', 'sevenMax', 'sevenDayNeed', 'deliverDone', 'ores', 'waterWalk', 'stepMs', 'look', 'cosShop', 'genderCosts', 'genderCan', 'durability', 'durabilityMax'].forEach(function (k) { if (d[k] != null) st[k] = d[k]; });
    if (d.durability != null) showStamina(); // [2026-10-28] 구리 납품 -- 곡괭이 내구도 표시
    STEP_MS = st.stepMs || 140;
    buildSprites(st.look); // [2026-10-24] 외형이 바뀌었으면 캐릭터 그림을 다시
  }
  // [2026-10-25] 전투 턴 -- "전투 중엔 체력 회복 없이, 안 때려도 몬스터가 일정 턴마다 공격, 5칸 이상 벌어지면 인식이 풀리고 체력 가득" 요청.
  //  몬스터에게 인식돼 있거나(st.combat.fight) 동굴 3층 보스 2칸 안이면 1초마다 서버에 묻는다(쫓아온 칸, 맞은 피해, 인식 해제).
  var lastBossT = 0, bossBusy = false, bossWarned = false;
  var bolts = [], BOLT_MS = 420, boltWarned = false; // [2026-10-28] 용암 거신 불덩이(원거리 공격)
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
      return ty && !ty.boss && !ty.minion && mb._p && (ty.first || 1 + 4 * (ty.tier - 1) - myLv() >= 20) && Math.abs(mb._p.cx - P.x) + Math.abs(mb._p.cy - P.y) <= 6;
    });
    if (!fight && !nearBoss && !nearStrong && !frozenMe() && !(healUntil > nowSrv())) return; // [2026-10-28] 재생의 장갑 회복 중에도 // [2026-10-28] 얼어 있으면 얼음 여왕이 쫓아오는 것도 받는다
    lastBossT = now; bossBusy = true;
    api('/api/tower-mine-combat-tick?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
      bossBusy = false;
      if (!d || !d.ok) return;
      // [2026-10-28] "몬스터가 인식했을 때 나오는 문구 없애줘, 벗어났을 때도 필요 없어" -- 보스 선공/레벨 차이 선공/인식 풀림 안내는 띄우지 않는다
      if (d.hpLockUp) floats.push({ x: P.x, y: P.y - 0.9, text: '🔒 최대 체력 -' + d.hpLockUp + '%', color: '#c9c3d6', t0: performance.now() + 300 });
      if (d.summoned) flashT0 = performance.now(); // [2026-10-28] "보스 스킬 쓸 때 가운데 멘트 없애줘, 특히 성당 보스" -- 번쩍임만
      (d.mobs || []).forEach(function (a) { mobSt[a[0]] = mobStOf(a, mobSt[a[0]]); });
      if (d.lava) lavaSet(d.lava);
      if (d.slowUntil) slowUntil = d.slowUntil; // [2026-10-28] 여왕개미 독산
      if (d.healUntil != null) healUntil = d.healUntil; // [2026-10-28] 재생의 장갑
      if (d.healed) floats.push({ x: P.x - 0.2, y: P.y - 0.5, text: '+' + d.healed + ' 💚', color: '#7cf08a', t0: performance.now() });
      if (d.veinBroken) veinBroke(); // [2026-10-28] 설산 동굴
      var t = performance.now();
      (d.hits || []).forEach(function (h, k) {
        if (h.poison) { floats.push({ x: P.x + 0.3, y: P.y - 0.1, text: '-' + h.dmg + '☠️', color: '#9ad84a', t0: t + k * 120 }); return; } // [2026-10-28] 여왕개미 독
        if (h.venom) { // [2026-10-28] 여왕개미 독산 -- 초록 덩이, 맞으면 느려지고 중독
          bolts.push({ fx: h.fx, fy: h.fy, tx: P.x, ty: P.y, t0: t, venom: true });
          floats.push({ x: P.x, y: P.y - 0.3, text: '🧪 -' + h.dmg + ' 느려짐·중독', color: '#9ad84a', big: true, t0: t + BOLT_MS });
          return;
        }
        if (h.ranged) { // [2026-10-28] 용암 거신 불덩이 -- 보스 칸에서 날아와 맞는다
          bolts.push({ fx: h.fx, fy: h.fy, tx: P.x, ty: P.y, t0: t });
          floats.push({ x: P.x, y: P.y - 0.3, text: '☄️ -' + h.dmg, color: '#ff8a2a', big: true, t0: t + BOLT_MS });
          return;
        }
        floats.push({ x: P.x, y: P.y - 0.3, text: (h.x2 ? '🗡️ 2배! ' : '') + '-' + h.dmg, color: h.x2 ? '#ff3a6a' : '#ff4a4a', big: true, t0: t + k * 160 });
      }); // [2026-10-28] 은신했던 닌자의 기습
      if (d.rewardMsg) { if (d.ores) st.ores = d.ores; flashT0 = t; msg(d.rewardMsg, 4200); } // [2026-10-28] 함께 싸운 닌자 두목 첫 처치 보상
      if (d.combat) setCombat(d.combat);
      else if (st.combat && d.fight === false) { st.combat.fight = false; renderHp(); }
      // (인식 풀림 안내는 띄우지 않는다 -- 위 [2026-10-28])
      if (d.died && d.state) fallDown(d);
    }).catch(function () { bossBusy = false; });
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
    if (!map || !(map.cold || map.raid || map.hot) || busy || coldBusy || fade || now - lastColdT < 1000) return; // [2026-10-28] 더운 곳(hot)도
    lastColdT = now; coldBusy = true;
    var mid = map.id;
    api('/api/tower-mine-cold-tick?mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
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
      if (d.lavaDmg) floats.push({ x: P.x - 0.3, y: P.y - 0.2, text: '-' + d.lavaDmg + '🌋', color: '#ff5a1a', t0: t });
      if (d.combat) setCombat(d.combat);
      renderCold();
      if (d.died && d.state) fallDown(d);
    }).catch(function () { coldBusy = false; });
  }
  var heatWarned = false;
  // [2026-10-28] 설산 봉우리 -- 얼음 여왕이 18초마다 한 명씩 얼린다(최대 15초). 언 동안 못 움직이고(서버도 막음), 여왕에게 맞으면 즉사.
  //  얼지 않은 동료가 2칸 안에 오면 풀린다(얼음땡). 서버가 환경 턴으로 알려 준다: iceAwake, nextFreezeAt, frozen {x, y, until}, thawed.
  var iceSt = null, iceWarned = 0;
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
  /** 얼음 덩어리(언 캐릭터 위) -- (x, y) = 캐릭터 그림 왼쪽 위. draw()의 TS는 지역 변수라 여기서 다시 구한다. */
  function drawIce(x, y, now) {
    var TS = T * scale, u = TS / 16, sh = 0.5 + 0.2 * Math.sin(now / 260);
    ctx.fillStyle = 'rgba(170,230,255,0.55)'; ctx.fillRect(x - u, y - 2 * u, TS + 2 * u, TS + 3 * u);
    ctx.strokeStyle = 'rgba(235,250,255,0.95)'; ctx.lineWidth = Math.max(1, u); ctx.strokeRect(x - u, y - 2 * u, TS + 2 * u, TS + 3 * u);
    ctx.fillStyle = 'rgba(255,255,255,' + sh + ')'; ctx.fillRect(x + u, y - u, 2 * u, 6 * u); ctx.fillRect(x + 3 * u, y - u, 4 * u, u);
    ctx.fillStyle = 'rgba(255,255,255,0.8)'; ctx.fillRect(x + TS - 3 * u, y + TS - 6 * u, u, 3 * u);
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
      var at = (inSafe() ? '🌴 그늘' : !day ? '🌙 밤' : hr > 0 ? '☀️ -' + rateTxt(hr) + '%/초' : '😇 햇빛 막음(환생)') + ' ' + archiLeftMin(tn) + '분'; // [2026-10-28] 환생 경감 반영
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
  function presTick() {
    if (!opened || !map || presBusy) return;
    presBusy = true;
    var mid = map.id;
    api('/api/tower-mine-presence?mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + '&d=' + P.dir).then(function (d) {
      presBusy = false;
      if (d) patchShow(d); // [2026-10-28] 패치 전 공지
      if (d && d.rewardMsg) { flashT0 = performance.now(); msg(d.rewardMsg, 4200); } // [2026-10-28] 닌자 두목 함께 처치 보상
      if (!map || map.id !== mid || !d) return;
      var now = performance.now(), seen = {};
      (d.others || []).forEach(function (o) {
        seen[o.id] = 1;
        var cur = others[o.id];
        if (!cur) others[o.id] = cur = { x: o.x, y: o.y, fx: o.x, fy: o.y, t0: now };
        else if (cur.x !== o.x || cur.y !== o.y) { var pp = otherPos(cur, now); cur.fx = pp.x; cur.fy = pp.y; cur.x = o.x; cur.y = o.y; cur.t0 = now; }
        cur.dir = o.d || 'down'; cur.look = o.look || {}; cur.bubble = o.bubble || ''; cur.frozen = !!o.frozen; // [2026-10-28] 얼음 여왕 빙결
      });
      for (var id in others) if (!seen[id]) delete others[id];
      drawMini(); // [2026-10-28] 미니맵에 다른 유저 위치
    }).catch(function () { presBusy = false; });
  }
  function otherPos(o, now) {
    var k = Math.min(1, (now - o.t0) / 950);
    return { x: o.fx + (o.x - o.fx) * k, y: o.fy + (o.y - o.fy) * k, moving: k < 1 };
  }
  function otherSprite(look, dir, step) {
    var key = JSON.stringify(look || {});
    if (!otherSprites[key]) otherSprites[key] = {};
    var c = otherSprites[key], sk = dir + step;
    if (!c[sk]) c[sk] = charSprite(dir, step, 0, look || {});
    return c[sk];
  }
  function nearOther() {
    for (var id in others) { var o = others[id]; if (Math.abs(o.x - P.x) + Math.abs(o.y - P.y) <= 4) return true; }
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
  function drawWings(x, y, rb, dir, now, seed) {
    if (!(rb > 0) || !window.MineChar || !MineChar.wings) return;
    var w = MineChar.wings(rb, dir || 'down'), up = ((now + (seed || 0) * 397) % 1600) < 250 ? scale : 0;
    ctx.drawImage(w.c, x - w.ext * scale, y - 2 * scale - up, w.c.width * scale, w.c.height * scale);
  }
  function drawBubble(text, px0, py0) {
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
      msg((d && (d.message || d.error)) || '인사하지 못했어요.', 3200);
    }).catch(function () { greetBusy = false; });
  }
  /** ⛏️/⚔️ 버튼·스페이스 -- 옆에 몬스터가 있으면 공격, 없으면 지금 칸 캐기. */
  // [2026-10-28] 버튼을 나눴다 -- ⛏️ = 캐기만, ⚔️ = 바로 옆 몬스터 공격만(스페이스/엔터는 예전처럼 act: 옆에 몬스터가 있으면 공격, 없으면 캐기)
  function digBtn() {
    if (frozenMe()) { msg('🧊 얼어붙어서 아무것도 할 수 없어요! 동료가 곁(2칸)에 오면 풀려요.'); return; }
    dig();
  }
  function atkBtn() {
    if (frozenMe()) { msg('🧊 얼어붙어서 아무것도 할 수 없어요! 동료가 곁(2칸)에 오면 풀려요.'); return; }
    var a = !P.moving && adjMob();
    if (a) attack(a); else msg('⚔️ 바로 옆(상하좌우)에 몬스터가 없어요.', 1600);
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
    else if (k === 'home') goVillage();
    else if (k === 'log') toggleLog();
    else if (k === 'exit') close();
  }
  function act() {
    if (frozenMe()) { msg('🧊 얼어붙어서 아무것도 할 수 없어요! 동료가 곁(2칸)에 오면 풀려요.'); return; } // [2026-10-28]
    var a = !P.moving && adjMob();
    if (a) attack(a); else dig();
  }
  // [2026-10-24] 공격(바람의나라식: 바로 옆 몬스터를 때리고, 살아남은 몬스터는 그 자리에 멈춰 반격)
  function attack(a) {
    if (!opened || !map || atkBusy || busy || fade) return;
    var now = performance.now();
    if (now - lastAtkT < 420) return;
    lastAtkT = now; atkBusy = true;
    P.dir = a.dir; P.digT0 = now;
    var m = a.m, mp = m._p;
    var mc = mp ? mp.cy * map.w + mp.cx : -1; // 화면이 보던 몬스터 칸 -- 서버가 그 칸에 멈춰 세운다(순간이동처럼 안 보이게)
    api('/api/tower-mine-attack?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&mob=' + m.id + '&mc=' + mc + verQ()).then(function (d) {
      atkBusy = false;
      if (!d || d.tooFast) return;
      if (d.reload) { reload(); return; }
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (d.mobDeadUntil != null) mobSt[m.id] = mobStOf([m.id, d.mobHp, d.mobDeadUntil, d.mobFx, d.mobFy, d.mobFrozenUntil, d.mobStealth ? 1 : 0], mobSt[m.id]);
      (d.mobRows || []).forEach(function (a) { mobSt[a[0]] = mobStOf(a, mobSt[a[0]]); }); // [2026-10-28] 분신술 -- 분신/두목 상태
      if (!d.ok) { if (d.message && !d.far) msg(d.message); return; }
      if (d.veinBroken) veinBroke(); // [2026-10-28] 설산 동굴 -- 반격에 맞음
      if (d.healUntil != null) healUntil = d.healUntil; // [2026-10-28] 재생의 장갑
      lastSaved = map.id + ':' + P.x + ',' + P.y;
      var t = performance.now(), fxp = mp ? mp.x : P.x, fyp = mp ? mp.y : P.y;
      // [2026-10-28] 닌자 -- 은신 회피("💨 은신!"), 분신("💨 펑!"), 은신했던 닌자의 2배 반격("2배!")
      if (d.evade) floats.push({ x: fxp, y: fyp, text: d.stealthLeft ? '💨 은신! (' + d.stealthLeft + ')' : '💨 은신!', color: '#c8c8ff', big: true, t0: t }); // [2026-10-28] 닌자 두목 -- 남은 공격 수
      else if (d.clone) floats.push({ x: fxp, y: fyp, text: '💨 펑! 분신', color: '#d8c8ff', big: true, t0: t });
      else floats.push({ x: fxp, y: fyp, text: (d.crit ? '💥' : '') + d.dmg, color: d.crit ? '#ffdd33' : '#ff5a4a', big: !!d.crit, t0: t });
      if (d.counter) floats.push({ x: P.x, y: P.y - 0.3, text: (d.stealthHit ? '🗡️ 2배! ' : '') + '-' + d.counter, color: d.stealthHit ? '#ff3a6a' : '#ff9a3a', big: !!d.stealthHit, t0: t + 180 });
      if (d.split) flashT0 = t; // [2026-10-28] 분신술 -- 가운데 멘트 없이 번쩍임만
      setCombat(d.combat);
      if (d.killed) {
        if (d.ores) st.ores = d.ores;
        (d.drops || []).forEach(function (dr, i) {
          var img = new Image(); img.src = oreIcon(dr.grade);
          floats.push({ x: fxp, y: fyp - 0.6 - i * 0.5, text: '+' + dr.qty + ' ' + dr.name, color: (ORE_COL[dr.grade] || ORE_COL[1])[1], icon: img, t0: t + 200 + i * 150 });
        });
        floats.push({ x: fxp, y: fyp + 0.4, text: '+' + d.expGain + ' EXP', color: '#9fe0ff', t0: t + 100 });
        msg(d.message || '', 2600);
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
      if (d.craftedRing || d.questStarted) flashT0 = performance.now();
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function craft(code) { gearAct('/api/tower-mine-craft?code=' + encodeURIComponent(code)); }
  // [2026-10-28] 광석 전문가(광석 100 -> 괴, 45%) / 보석 세공사(보석 5 -> 세공된 보석, 25%) / 왕도 장인(세공된 보석 -> 최대 내구도 +30, 1000 이상일 때)
  function smelt(grade, times) { gearAct('/api/tower-mine-smelt?grade=' + grade + '&times=' + times); }
  function gemCut(gem, times) { gearAct('/api/tower-mine-gemcut?gem=' + gem + '&times=' + times); }
  function artisan(gem) { gearAct('/api/tower-mine-artisan?gem=' + gem); }
  function craftNpcHtml(n) {
    var h = npcHead((n.kind === 'smelter' ? '🔥 ' : n.kind === 'gemcutter' ? '💎 ' : '🔨 ') + n.name, false) + talkBlock(n, n.line);
    var row = function (icon, name, sub, btns) { return '<div class="mine-smith-row"><img src="' + oreIcon(icon) + '" alt=""><span class="nm">' + name + '<small>' + sub + '</small></span>' + btns + '</div>'; };
    var btn = function (fn, a, t, ok) { return '<button type="button" ' + (ok ? '' : 'disabled ') + 'onclick="TowerMine.' + fn + '(' + a + ', ' + t + ')">' + t + '번</button>'; };
    if (n.kind === 'smelter') {
      h += '<div class="mine-smith-say">광석 100개 -> 괴 1개 (성공 45%, 실패해도 광석은 녹아요). 괴는 좋은 장비의 재료예요.</div>';
      for (var g = 1; g <= 5; g++) { var have = oreHave(g); h += row(39 + g, ORE_NAME[g] + ' 100 -> ' + ORE_NAME[39 + g], '보유 ' + have + ' · ' + ORE_NAME[39 + g] + ' ' + oreHave(39 + g) + '개', btn('smelt', g, 1, have >= 100) + btn('smelt', g, 10, have >= 1000)); }
      return h;
    }
    if (n.kind === 'gemcutter') {
      h += '<div class="mine-smith-say">보석 5개 -> 세공된 보석 1개 (성공 25%, 실패하면 보석이 깨져요). 세공된 보석은 고급 장비와 왕도 장인에게 쓰여요.</div>';
      for (var i = 0; i < 9; i++) { var hv = oreHave(21 + i); h += row(45 + i, ORE_NAME[21 + i] + ' 5 -> ' + ORE_NAME[45 + i], '보유 ' + hv + ' · 세공 ' + oreHave(45 + i) + '개', btn('gemCut', i, 1, hv >= 5) + btn('gemCut', i, 10, hv >= 50)); }
      return h;
    }
    var mx = st.durabilityMax || 0;
    h += '<div class="mine-smith-say">곡괭이 최대 내구도가 1000 이상이면, 세공된 보석 하나에 최대 내구도 +30 (지금 ' + (st.durability || 0) + ' / ' + mx + ')</div>';
    var any = false;
    for (var j = 0; j < 9; j++) {
      var c = oreHave(45 + j);
      if (!c) continue;
      any = true;
      h += row(45 + j, ORE_NAME[45 + j], '보유 ' + c + '개', '<button type="button" ' + (mx >= 1000 ? '' : 'disabled ') + 'onclick="TowerMine.artisan(' + j + ')">박아 넣기</button>');
    }
    if (!any) h += '<div class="mine-smith-say">세공된 보석이 없어요. 마을의 보석 세공사에게서 만들 수 있어요.</div>';
    return h;
  }
  function equipRing(code) { gearAct('/api/tower-mine-ring?code=' + encodeURIComponent(code)); }
  function synth(times) { gearAct('/api/tower-mine-synth?times=' + times); }
  function captain(action) { gearAct('/api/tower-mine-captain?action=' + action); }
  var smithNpc = null;
  function openSmith(n, view) {
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
  var ANT_FALL_X = 0.3, ANT_FALL_P = 0.012;
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
        if (dug[x + ',' + y]) ctx.drawImage(hole, sx, sy, TS, TS);
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
    // [2026-10-24] 몬스터(그림 + 맞은 몬스터 체력 막대 + 가까우면 이름)
    if (map.mobs) {
      // [2026-10-28] 닌자 두목 분신술 -- 분신은 두목과 똑같이(그림/크기/이름/체력 막대) 그리고, 분신이 나와 있는 동안 진짜만 인을 맺은 자세
      var bossM = map.mobs[map.mobs.length - 1], bossT = bossM && map.mobTypes[bossM.k], splitOn = false;
      if (bossT && bossT.boss) for (var si = 0; si < map.mobs.length; si++) { var stt = map.mobTypes[map.mobs[si].k]; if (stt && stt.clone && map.mobs[si]._p) { splitOn = true; break; } }
      for (var mi = 0; mi < map.mobs.length; mi++) {
        var mb = map.mobs[mi], mp = mb._p;
        if (!mp) continue;
        var mx = mp.x * TS - camX, my = mp.y * TS - camY;
        if (mx < -TS || my < -TS || mx > W || my > H) continue;
        var ty = map.mobTypes[mb.k] || {}, sk = mb.k, bid = mb.id, pose = false;
        if (ty.clone && bossT && bossT.boss) { ty = bossT; sk = bossM.k; bid = bossM.id; } else if (splitOn && mb === bossM) pose = true;
        var bob = ty.kind === 'flyer' ? Math.sin(now / 260 + bid) * 2 * scale : Math.abs(Math.sin(now / 420 + bid)) * scale;
        var ms = mobSt[mb.id];
        if (ms && ms.sl) ctx.globalAlpha = 0.3 + 0.15 * Math.sin(now / 90); // [2026-10-28] 은신한 닌자 -- 반투명하게 깜빡
        ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.fillRect(mx + 3 * scale, my + 13 * scale, 10 * scale, 2 * scale);
        if (ty.boss) { // [2026-10-24] 보스 -- 1.8배 크기, 붉은 기운
          ctx.fillStyle = 'rgba(255,40,40,' + (0.18 + 0.1 * Math.sin(now / 300)) + ')'; ctx.beginPath(); ctx.arc(mx + TS / 2, my + TS / 2, TS * 1.1, 0, Math.PI * 2); ctx.fill();
          ctx.drawImage(mobSprite(sk, pose), Math.round(mx - TS * 0.4), Math.round(my - TS * 0.8 - bob), TS * 1.8, TS * 1.8);
        } else ctx.drawImage(mobSprite(sk), Math.round(mx), Math.round(my - 2 * scale - bob), TS, TS);
        ctx.globalAlpha = 1;
        if (bid !== mb.id) ms = mobSt[bid]; // 분신 체력 막대 = 두목 체력
        if (ms && ms.hp > 0 && ms.hp < ty.hp) {
          ctx.fillStyle = '#140d0a'; ctx.fillRect(mx + 1 * scale, my - 4 * scale, 14 * scale, 3 * scale);
          ctx.fillStyle = '#e8433a'; ctx.fillRect(mx + 2 * scale, my - 3 * scale, Math.max(1, 12 * scale * ms.hp / ty.hp), scale);
        }
        if (Math.abs(mp.cx - P.x) + Math.abs(mp.cy - P.y) <= 4) {
          ctx.font = 'bold ' + Math.round(11 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
          var nm = (ty.boss ? '👑 보스 ' : '') + ty.name + ' Lv.' + (ty.lv || (1 + 4 * (ty.tier - 1))); // [2026-10-28] 얼음 여왕은 서버가 준 레벨(95)
          ctx.lineWidth = 3 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(nm, mx + TS / 2, my - (ty.boss ? 14 : 6) * scale); // [2026-10-28] 보스 이름이 두 줄로 보이던 문제 -- 테두리도 글자와 같은 높이
          ctx.fillStyle = ty.boss ? '#ff4a4a' : ty.tier >= 7 ? '#ff8a8a' : ty.tier >= 4 ? '#ffd27a' : '#ffffff'; ctx.fillText(nm, mx + TS / 2, my - (ty.boss ? 14 : 6) * scale);
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
        if (mix(ci + 1, cwin) % 3 === 0 && tc % 7000 < 3600) drawBubble(CIT_LINES[mix(ci + 7, cwin) % CIT_LINES.length], cxp + TS / 2, cyp - 6 * scale);
      }
    }
    // [2026-10-24] 다른 유저(이름 없이) -- 1초마다 받은 칸 사이를 걸어서, 인사하면 말풍선
    for (var oid in others) {
      var ou = others[oid], op = otherPos(ou, now), ox = Math.round(op.x * TS - camX), oy = Math.round(op.y * TS - camY);
      if (ox < -TS || oy < -TS || ox > W || oy > H) continue;
      ctx.fillStyle = "rgba(0,0,0,.22)"; ctx.fillRect(ox + 3 * scale, oy + 13 * scale, 10 * scale, 3 * scale);
      if (ou.look && ou.look.gm) { try { gmAura(ox, oy - 3 * scale, now); } catch (e) { } } // 치장 효과가 화면 그리기를 멈추지 않게
      if (map.boat) drawBoat(ox, oy - 3 * scale, now); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
      if (ou.look && ou.look.rebirth) { try { drawWings(ox, oy - 3 * scale, ou.look.rebirth, ou.dir || "down", now, +oid || 1); } catch (e) { } } // [2026-10-28] 환생 날개
      ctx.drawImage(otherSprite(ou.look, ou.dir || "down", op.moving ? 1 + Math.floor(now / 160) % 2 : 0), ox, oy - 3 * scale, TS, TS);
      if (ou.look && ou.look.gm) { try { gmSparkle(ox, oy - 3 * scale, now, +oid || 1); } catch (e) { } }
      if (ou.frozen) drawIce(ox, oy - 3 * scale, now); // [2026-10-28] 얼어붙은 동료
      if (ou.bubble) drawBubble(ou.bubble, ox + TS / 2, oy - 6 * scale);
    }
    // 캐릭터
    var cx = Math.round(pos.x * TS - camX), cy = Math.round(pos.y * TS - camY);
    ctx.fillStyle = 'rgba(0,0,0,.28)';
    ctx.fillRect(cx + 3 * scale, cy + 13 * scale, 10 * scale, 3 * scale);
    var digging = now - P.digT0 < 380, dk = digging ? ((now - P.digT0) < 190 ? 1 : 2) : 0;
    var meGm = !!(st && st.look && st.look.gm); // [2026-10-28] 운영자 장비 -- 반짝이 + "운영자" 표시
    if (meGm) { try { gmAura(cx, cy - 3 * scale, now); } catch (e) { } }
    if (map.boat) drawBoat(cx, cy - 3 * scale, now); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
    var myRb = st && st.combat ? st.combat.rebirth | 0 : 0; // [2026-10-28] 환생 날개(내 것은 전투 정보의 환생 횟수로)
    if (myRb) { try { drawWings(cx, cy - 3 * scale, myRb, P.dir, now, 0); } catch (e) { } }
    ctx.drawImage(sprites[P.dir + (P.moving ? P.step : 0) + dk], cx, cy - 3 * scale, TS, TS);
    if (meGm) { try { gmSparkle(cx, cy - 3 * scale, now, 0); } catch (e) { } }
    if (frozenMe()) drawIce(cx, cy - 3 * scale, now); // [2026-10-28] 얼음 여왕 빙결
    if (performance.now() - myBubbleT0 < 4000) drawBubble("안녕하세요!", cx + TS / 2, cy - 6 * scale); // [2026-10-24] 내가 한 인사
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
    if (map.archi && map.dayCycleMs && map.id.indexOf('shrine') !== 0) { // [2026-10-28] 신궁 1~3층/닌자 두목의 방은 실내
      ctx.fillStyle = archiDay(nowSrv()) ? 'rgba(255,220,120,0.06)' : 'rgba(10,20,70,0.38)';
      ctx.fillRect(0, 0, W, H);
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
      if (p.kind === 'Z' || Math.abs(p.x - P.x) + Math.abs(p.y - P.y) > 4) return; // [2026-10-24] 숨은 입구(Z)는 이름을 안 보인다
      var tx = p.x * TS - camX + TS / 2, ty = p.y * TS - camY - 4 * dpr;
      var label = (p.locked ? (p.label.indexOf('눈바람') >= 0 ? '🌨️ ' : '🔒 ') : '🚪 ') + p.label;
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(label, tx, ty);
      ctx.fillStyle = p.locked ? '#d0d0d0' : '#ffe14d'; ctx.fillText(label, tx, ty);
    });
    // [2026-10-28] 불덩이 -- 보스 칸에서 내 칸으로 BOLT_MS 동안 날아가며 꼬리를 남긴다
    bolts = bolts.filter(function (b) { return now - b.t0 < BOLT_MS + 150; });
    bolts.forEach(function (b) {
      var k = Math.min(1, (now - b.t0) / BOLT_MS);
      for (var tr = 3; tr >= 0; tr--) {
        var kk = Math.max(0, k - tr * 0.06), bx = (b.fx + (b.tx - b.fx) * kk) * TS - camX + TS / 2, by = (b.fy + (b.ty - b.fy) * kk) * TS - camY + TS / 2 - Math.sin(kk * Math.PI) * TS * 1.2;
        ctx.globalAlpha = k >= 1 ? Math.max(0, 1 - (now - b.t0 - BOLT_MS) / 150) : 1 - tr * 0.22;
        ctx.fillStyle = b.venom ? (tr ? '#6ab82a' : '#d8ff8a') : tr ? '#ff7a1a' : '#ffe08a'; ctx.beginPath(); ctx.arc(bx, by, TS * (0.22 - tr * 0.04), 0, Math.PI * 2); ctx.fill();
      }
      ctx.globalAlpha = 1;
    });
    // 캔 광석 글자
    floats = floats.filter(function (f) { return now - f.t0 < 1400; });
    floats.forEach(function (f) {
      var k = (now - f.t0) / 1400, fx = f.x * TS - camX + TS / 2, fy = f.y * TS - camY - k * 36 * dpr - 6 * dpr;
      ctx.globalAlpha = 1 - k * k;
      ctx.font = 'bold ' + Math.round((f.big ? 17 : 14) * dpr) + 'px monospace';
      ctx.textAlign = 'center';
      if (f.icon) ctx.drawImage(f.icon, fx - 9 * dpr - ctx.measureText(f.text).width / 2 - 8 * dpr, fy - 12 * dpr, 16 * dpr, 16 * dpr);
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(f.text, fx + 9 * dpr, fy);
      ctx.fillStyle = f.color; ctx.fillText(f.text, fx + 9 * dpr, fy);
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
        ctx.fillStyle = '#ffe9a8'; ctx.fillText(banner.text, W / 2, H * 0.32);
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
    if (!map) return;
    update(now);
    draw(now);
  }

  // ---------------------------------------------------------------- 서버
  function api(path) {
    return fetch(cfg.base + path + (path.indexOf('?') < 0 ? '?' : '&') + 'userName=' + encodeURIComponent(cfg.user)).then(function (r) { return r.json(); })
      .then(function (d) {
        // [2026-10-23] 2일 주기 맵 리셋 중 -- 안내 후 광산에서 나간다(리셋이 끝나면 다시 들어오면 새 맵)
        if (d && d.resetting && opened && map) { msg(d.message || d.error, 4000); setTimeout(close, 2500); }
        return d;
      });
  }
  /** 서버에 보내는 맵 version -- 리셋 등으로 맵이 바뀌었으면 서버가 reload로 답한다. */
  function verQ() { return map && map.version ? '&v=' + encodeURIComponent(map.version) : ''; }
  function ensureMap(id, ver) {
    var key = 'tw-mine-map-' + id;
    try {
      var c = JSON.parse(localStorage.getItem(key) || 'null');
      if (c && c.version === ver && c.rows) return Promise.resolve(c);
    } catch (e) { }
    return fetch(cfg.base + '/api/tower-mine-map?mapId=' + encodeURIComponent(id)).then(function (r) { return r.json(); }).then(function (m) {
      if (m.error || !m.rows) throw new Error(m.error || '맵을 불러오지 못했어요.');
      try { localStorage.removeItem('tw-mine-map'); localStorage.setItem(key, JSON.stringify(m)); } catch (e) { }
      return m;
    });
  }
  /** 서버 상태(맵/위치/캔 칸)로 화면을 다시 맞춘다 -- 처음 들어올 때, 포탈을 탄 뒤, 다른 곳에서 위치가 바뀌었을 때. */
  function enterState(s) {
    st = s;
    STEP_MS = s.stepMs || 140; // [2026-10-24] 신속의 장화
    buildSprites(s.look); setCombat(s.combat); mobSt = {}; noOre = {}; lavaCells = {}; scan = null; scanKey = ""; bossWarned = false; cold = null; lastBz = 0; raid = null; heatWarned = false; iceSt = null; /* [2026-10-26] 한기, [2026-10-28] 대주교, 더위, 얼음 여왕 */
    if (s.notice && !noticeShown) { noticeShown = true; noticeData = { id: s.noticeId, text: s.notice }; setTimeout(function () { openSmith({ name: '광산 소식', line: '', kind: 'notice' }, 'notice'); }, 700); } // [2026-10-28] 광산 소식(한 번)
    others = {}; myBubbleT0 = 0; // [2026-10-24] 외형, 광산 레벨, 몬스터 상태(새 맵), 자동채광 기록, 탐지, 보스 경고
    el.coord.classList.toggle('admin', !!s.adminMaps); // [2026-10-24] 관리자면 좌표를 눌러 이동
    return ensureMap(s.mapId, s.mapVersion).then(function (m) {
      map = m;
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
    if (exit) body.append('exit', '1');
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
    if (!(f && cellAt(P.x + f[0], P.y + f[1]) === 'I')) {
      f = null;
      for (var k in dirs) if (cellAt(P.x + dirs[k][0], P.y + dirs[k][1]) === 'I') { P.dir = k; f = dirs[k]; break; }
    }
    if (!f) { msg('💠 얼음 광맥 바로 앞에서 캐요. 이 동굴은 땅을 팔 수 없어요.', 1800); return; }
    var tx = P.x + f[0], ty = P.y + f[1];
    veinBusy = true; P.digT0 = performance.now();
    api('/api/tower-mine-vein?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&tx=' + tx + '&ty=' + ty + verQ()).then(function (d) {
      veinBusy = false;
      if (!d || d.tooFast) return;
      if (!d.ok) { if (d.message) msg(d.message, 1800); return; }
      veinCount = d.gain ? 0 : d.count;
      var t = performance.now();
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
  function dig() {
    if (map && map.vein) { veinDig(); return; } // [2026-10-28] 설산 동굴
    if (!opened || busy || !map) return;
    if (P.moving) return;
    var x = P.x, y = P.y, c = cellAt(x, y);
    if (c === 'V') { msg('마을에서는 캘 수 없어요. 마을 밖으로 나가 보세요.'); return; }
    if (c === 'T') { msg('나무 밑은 캘 수 없어요. 옆 칸에서 캐 보세요.'); return; }
    if (dug[x + ',' + y]) { msg('이미 캔 자리예요. 다른 곳을 찾아보세요.'); return; }
    if (st.durability < 1) { msg('⛏️ 곡괭이 내구도가 다 닳았어요. 중앙 평원 마을의 대장장이에게 광석을 주고 곡괭이를 강화하세요.'); return; }
    busy = true;
    el.dig.disabled = true;
    P.digT0 = performance.now();
    api('/api/tower-mine-dig?mapId=' + encodeURIComponent(map.id) + '&x=' + x + '&y=' + y + '&dir=' + P.dir + verQ()).then(function (d) { // [2026-10-25] dir = 쌍날 곡괭이 앞뒤
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
        if (d.detectDim) { // 탐지 곡괭이가 별빛석에 빛을 잃음 -- 맵 리셋 때 회복
          (st.gear || []).forEach(function (g) { if (g.code === 'DETECT_PICK') g.dim = true; });
          scan = null;
          msg('🌑 탐지 곡괭이가 별빛에 눌려 빛을 잃었어요... 맵 리셋 때 다시 빛나요.', 4200);
        }
        if (d.gemGrade) { // [2026-10-24] 보석이 함께 나옴
          (st.ores || []).forEach(function (o) { if (o.grade === d.gemGrade) o.qty = d.gemQty; });
          var gimg = new Image(); gimg.src = oreIcon(d.gemGrade);
          floats.push({ x: x, y: y - 0.6, text: '+1 ' + d.gemName, color: (ORE_COL[d.gemGrade] || ORE_COL[1])[1], big: !!RARE_GEM[d.gemGrade], icon: gimg, t0: performance.now() + 250 });
          if (RARE_GEM[d.gemGrade]) { flashT0 = performance.now(); msg('💎 ' + d.gemName + '! ' + map.name, 3600); } // [2026-10-28] 좌표 빼기 }
          else msg('💎 ' + d.gemName + '도 함께 나왔어요!', 2200);
        }
        showCoord(); drawMini(); renderLog();
      } else {
        if (d.reload) { reload(); return; }
        if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
        if (d.seaLimit) setAuto(false); // [2026-10-24] 바닷길 하루 보석 한도
        if (d.noOre || auto) noOre[x + ',' + y] = 1; // [2026-10-24] 자동채광이 같은 칸을 다시 고르지 않게
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
    api('/api/tower-mine?enter=1').then(function (s) { // enter=1: 서버가 입장 로그를 남긴다
      if (s.error) throw new Error(s.error);
      return enterState(s);
    }).then(function () {
      if (!opened) return;
      el.load.style.display = 'none';
      fadeIn('🗺️ ' + map.name);
      msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : '방향키·화면 끌기로 이동, ⛏️(스페이스)로 캐기, 몬스터 옆에서는 ⚔️로 공격. 🤖 자동채광을 켜면 알아서 캐요.', 3500);
      clearInterval(mobTimer); mobTimer = setInterval(pollMobs, 1500); // [2026-10-24] 몬스터 체력/쓰러짐 조회
      clearInterval(presTimer); presTimer = setInterval(presTick, 1000); // [2026-10-24] 다른 유저 위치 주고받기
      clearInterval(saveTimer);
      saveTimer = setInterval(function () {
        savePos();
      }, SAVE_MS);
    }).catch(function (e) {
      el.load.textContent = '광산을 불러오지 못했어요. (' + (e && e.message ? e.message : '오류') + ')';
    });
  }
  function close() {
    if (!opened) return;
    setMenu(false);
    if (map) saveBeacon(true);
    closeSmith();
    opened = false;
    cancelAnimationFrame(raf);
    clearInterval(saveTimer);
    clearInterval(mobTimer); setAuto(false); // [2026-10-24]
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

  return { open: open, close: close, smelt: smelt, gemCut: gemCut, artisan: artisan, digBtn: digBtn, atkBtn: atkBtn, toggleMenu: toggleMenu, menuGo: menuGo, openEquip: openEquip, eqInfo: eqInfo, equipTab: equipTabGo, archiAct: archiAct, dig: dig, goVillage: goVillage, toggleMini: toggleMini, toggleLog: toggleLog, oreIcon: oreIcon, pickUpgrade: pickUpgrade, helmetUpgrade: helmetUpgrade, closeSmith: closeSmith, smithGo: smithGo, craft: craft, equipRing: equipRing, synth: synth, captain: captain,
           act: act, toggleAuto: toggleAuto, greet: greet, sail: sail, lookAct: lookAct, openWarp: openWarp, openWarpForm: openWarpForm, worldWarp: worldWarp, worldTab: worldTabGo, adminWarp: adminWarp, openWorld: openWorld, tryOn: tryOn, priestAct: priestAct, teleport: teleport, guideGo: guideGo, askYes: askYes, coatAct: coatAct, refine: refine, deliver: deliver, noticeOk: noticeOk, dayGo: dayGo, ORE_NAME: ORE_NAME, digTime: digTime };
})();
</script>
